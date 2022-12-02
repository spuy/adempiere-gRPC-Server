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
import java.util.concurrent.ThreadLocalRandom;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.core.domains.models.I_M_InOut;
import org.compiere.model.MLookupInfo;
import org.compiere.model.MRole;
import org.compiere.model.MTable;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.spin.base.util.ContextManager;
import org.spin.base.util.DictionaryUtil;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.ReferenceInfo;
import org.spin.base.util.ValueUtil;
import org.spin.backend.grpc.common.ListEntitiesResponse;
import org.spin.backend.grpc.inout.InOutGrpc.InOutImplBase;
import org.spin.backend.grpc.inout.ListInOutInfoRequest;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Edwin Betancourt, EdwinBetanc0urt@outlook.com, https://github.com/EdwinBetanc0urt
 * Service for backend of Update Center
 */
public class InOutServiceImplementation extends InOutImplBase {
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(InOutServiceImplementation.class);
	
	public String tableName = I_M_InOut.Table_Name;

	@Override
	public void listInOutInfo(ListInOutInfoRequest request, StreamObserver<ListEntitiesResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ListEntitiesResponse.Builder entityValueList = listInOutInfo(request);
			responseObserver.onNext(entityValueList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException());
		}
	}
	
	/**
	 * Get default value base on field, process parameter, browse field or column
	 * @param request
	 * @return
	 */
	private ListEntitiesResponse.Builder listInOutInfo(ListInOutInfoRequest request) {
		MLookupInfo reference = ReferenceInfo.getInfoFromRequest(
			request.getReferenceUuid(),
			request.getFieldUuid(),
			request.getProcessParameterUuid(),
			request.getBrowseFieldUuid(),
			request.getColumnUuid(),
			request.getColumnName(),
			this.tableName
		);

		Properties context = ContextManager.getContext(request.getClientRequest());
		int windowNo = ThreadLocalRandom.current().nextInt(1, 8996 + 1);
		context = ContextManager.setContextWithAttributes(windowNo, context, request.getContextAttributesList());

		//
		MTable table = MTable.get(context, this.tableName);
		StringBuilder sql = new StringBuilder(DictionaryUtil.getQueryWithReferencesFromColumns(table));

		// add where with access restriction
		String sqlWithRoleAccess = MRole.getDefault(context, false)
			.addAccessSQL(
				sql.toString(),
				null,
				MRole.SQL_FULLYQUALIFIED,
				MRole.SQL_RO
			);

		StringBuffer whereClause = new StringBuffer();

		// validation code of field
		String validationCode = DictionaryUtil.getValidationCodeWithAlias(tableName, reference.ValidationCode);
		String parsedValidationCode = Env.parseContext(context, windowNo, validationCode, false);
		if (!Util.isEmpty(reference.ValidationCode, true)) {
			if (Util.isEmpty(parsedValidationCode, true)) {
				throw new AdempiereException("@WhereClause@ @Unparseable@");
			}
			whereClause.append(" AND ").append(parsedValidationCode);
		}

		//	For dynamic condition
		List<Object> params = new ArrayList<>(); // includes on filters criteria
		String dynamicWhere = ValueUtil.getWhereClauseFromCriteria(request.getFilters(), this.tableName, params);
		if (!Util.isEmpty(dynamicWhere, true)) {
			//	Add includes first AND
			whereClause.append(" AND ")
				.append("(")
				.append(dynamicWhere)
				.append(")");
		}

		sqlWithRoleAccess += whereClause;
		String parsedSQL = RecordUtil.addSearchValueAndGet(sqlWithRoleAccess, this.tableName, request.getSearchValue(), params);

		//	Get page and count
		int pageNumber = RecordUtil.getPageNumber(request.getClientRequest().getSessionUuid(), request.getPageToken());
		int limit = RecordUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * RecordUtil.getPageSize(request.getPageSize());
		int count = 0;
		ListEntitiesResponse.Builder builder = ListEntitiesResponse.newBuilder();
		
		//	Count records
		count = RecordUtil.countRecords(parsedSQL, this.tableName, params);
		//	Add Row Number
		parsedSQL = RecordUtil.getQueryWithLimit(parsedSQL, limit, offset);
		builder = RecordUtil.convertListEntitiesResult(MTable.get(context, this.tableName), parsedSQL, params);
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

}
