/************************************************************************************
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, C.A.                     *
 * Contributor(s): Edwin Betancourt, EdwinBetanc0urt@outlook.com                    *
 * This program is free software: you can redistribute it and/or modify             *
 * it under the terms of the GNU General Public License as published by             *
 * the Free Software Foundation, either version 2 of the License, or                *
 * (at your option) any later version.                                              *
 * This program is distributed in the hope that it will be useful,                  *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                   *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the                     *
 * GNU General Public License for more details.                                     *
 * You should have received a copy of the GNU General Public License                *
 * along with this program. If not, see <https://www.gnu.org/licenses/>.            *
 ************************************************************************************/
package org.spin.grpc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.I_C_InvoiceLine;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.I_M_Requisition;
import org.compiere.model.I_M_RequisitionLine;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrderLine;
import org.compiere.model.MRequisitionLine;
import org.compiere.model.MRole;
import org.compiere.model.MTable;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.spin.base.util.ContextManager;
import org.spin.base.util.DictionaryUtil;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.ValueUtil;

import org.spin.backend.grpc.common.ListEntitiesResponse;
import org.spin.backend.grpc.material_management.ListProductStorageRequest;
import org.spin.backend.grpc.material_management.MaterialManagementGrpc.MaterialManagementImplBase;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Edwin Betancourt, EdwinBetanc0urt@outlook.com, https://github.com/EdwinBetanc0urt
 * Service for Material Management
 */
public class MaterialManagementServiceImplementation extends MaterialManagementImplBase {
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(MaterialManagementServiceImplementation.class);

	
	@Override
	public void listProductStorage(ListProductStorageRequest request, StreamObserver<ListEntitiesResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListEntitiesResponse.Builder entitiesList = convertListAccountingCombinations(request);
			responseObserver.onNext(entitiesList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException());
		}
	}

	private ListEntitiesResponse.Builder convertListAccountingCombinations(ListProductStorageRequest request) {
		//
		String tableName = "RV_Storage";
		Properties context = ContextManager.getContext(request.getClientRequest());

		MTable table = MTable.get(context, tableName);
		StringBuilder sql = new StringBuilder(DictionaryUtil.getQueryWithReferencesFromColumns(table));
		StringBuffer whereClause = new StringBuffer(" WHERE 1=1 ");

		//	For dynamic condition
		List<Object> parametersList = new ArrayList<>(); // includes on filters criteria
		if (!Util.isEmpty(request.getTableName(), true)) {
			int recordId = request.getRecordId();
			if (recordId <= 0) {
				recordId = RecordUtil.getIdFromUuid(request.getTableName(), request.getRecordUuid(), null);
			}
			if (recordId <= 0) {
				throw new AdempiereException("@Record_ID@ @NotFound@");
			}
			String where = getWhereClause(request.getTableName(), recordId, parametersList);
			whereClause.append(where);
		}
		sql.append(whereClause); 

		// add where with access restriction
		String parsedSQL = MRole.getDefault(context, false)
			.addAccessSQL(sql.toString(),
				null,
				MRole.SQL_FULLYQUALIFIED,
				MRole.SQL_RO
			);

		//	Get page and count
		int pageNumber = RecordUtil.getPageNumber(request.getClientRequest().getSessionUuid(), request.getPageToken());
		int limit = RecordUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * RecordUtil.getPageSize(request.getPageSize());
		int count = 0;
		ListEntitiesResponse.Builder builder = ListEntitiesResponse.newBuilder();

		//	Count records
		count = RecordUtil.countRecords(parsedSQL, tableName, parametersList);
		//	Add Row Number
		parsedSQL = RecordUtil.getQueryWithLimit(parsedSQL, limit, offset);
		builder = RecordUtil.convertListEntitiesResult(MTable.get(context, tableName), parsedSQL, parametersList);
		//	
		builder.setRecordCount(count);
		//	Set page token
		String nexPageToken = null;
		if(RecordUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = RecordUtil.getPagePrefix(request.getClientRequest().getSessionUuid()) + (pageNumber + 1);
		}
		//	Set next page
		builder.setNextPageToken(ValueUtil.validateNull(nexPageToken));

		return builder;
	}

	public static String getWhereClause(String tableName, int recordId, List<Object> parametersList) {
		String where = "";

		switch (tableName) {
			case I_M_Requisition.Table_Name:
			case I_M_RequisitionLine.Table_Name:
				where = getWhereClasuseByRequisition(tableName, recordId, parametersList);
				break;
			case I_C_Order.Table_Name:
			case I_C_OrderLine.Table_Name:
				where = getWhereClasuseByOrder(tableName, recordId, parametersList);
				break;
			case I_C_Invoice.Table_Name:
			case I_C_InvoiceLine.Table_Name:
			    where = getWhereClasuseByInvoice(tableName, recordId, parametersList);
				break;
			default:
			    where = " AND "
					+ " EXISTS(SELECT 1 FROM " + tableName
					+ " WHERE RV_Storage.M_Product_ID = "
					+ tableName + ".M_Product_ID"
					+ " AND "
					+ tableName + "." + tableName + "_ID = ?)"
				;
				parametersList.add(recordId);
				break;
		}

		return where;
	}


	public static String getWhereClasuseByRequisition(String tableName, int recordId, List<Object> parametersList) {
		int requisitionId = recordId;
		StringBuffer whereClause = new StringBuffer();
		if (tableName.equals(I_M_Requisition.Table_Name)) {
		} else if (tableName.equals(I_M_RequisitionLine.Table_Name)) {
			MRequisitionLine requisitionLine = new MRequisitionLine(Env.getCtx(), recordId, null);
			requisitionId = requisitionLine.getM_Requisition_ID();
		}
		whereClause.append(" AND ")
			.append("EXISTS(SELECT 1 FROM M_RequisitionLine WHERE ")
			.append("RV_Storage.M_Product_ID = M_RequisitionLine.M_Product_ID ")
			.append("AND M_RequisitionLine.M_Requisition_ID = ?) ")
		;
		parametersList.add(requisitionId);

		return whereClause.toString();
	}

	public static String getWhereClasuseByOrder(String tableName, int recordId, List<Object> parametersList) {
		int orderId = recordId;
		StringBuffer whereClause = new StringBuffer();
		if (tableName.equals(I_C_Order.Table_Name)) {
		} else if (tableName.equals(I_C_OrderLine.Table_Name)) {
			MOrderLine orderLine = new MOrderLine(Env.getCtx(), recordId, null);
			orderId = orderLine.getC_Order_ID();
		}
		whereClause.append(" AND ")
			.append("EXISTS(SELECT 1 FROM C_OrderLine WHERE ")
			.append("RV_Storage.M_Product_ID = C_OrderLine.M_Product_ID ")
			.append("AND C_OrderLine.C_Order_ID = ?) ")
		;
		parametersList.add(orderId);

		return whereClause.toString();
	}

	public static String getWhereClasuseByInvoice(String tableName, int recordId, List<Object> parametersList) {
		int invoiceId = recordId;
		StringBuffer whereClause = new StringBuffer();
		if (tableName.equals(I_C_Invoice.Table_Name)) {
		} else if (tableName.equals(I_C_InvoiceLine.Table_Name)) {
			MInvoiceLine invoiceLine = new MInvoiceLine(Env.getCtx(), recordId, null);
			invoiceId = invoiceLine.getC_Invoice_ID();
		}
		whereClause.append(" AND ")
			.append("EXISTS(SELECT 1 FROM C_InvoiceLine WHERE ")
			.append("RV_Storage.M_Product_ID = C_InvoiceLine.M_Product_ID ")
			.append("AND C_InvoiceLine.C_Invoice_ID = ?) ")
		;
		parametersList.add(invoiceId);

		return whereClause.toString();
	}

}
