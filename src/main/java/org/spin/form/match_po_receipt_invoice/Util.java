/************************************************************************************
 * Copyright (C) 2012-2023 E.R.P. Consultores y Asociados, C.A.                     *
 * Contributor(s): Edwin Betancourt, EdwinBetanc0urt@outlook.com                    *
 * This program is free software: you can redistribute it and/or modify             *
 * it under the terms of the GNU General Public License as published by             *
 * the Free Software Foundation, either version 2 of the License, or                *
 * (at your option) any later version.                                              *
 * This program is distributed in the hope that it will be useful,                  *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                   *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the                     *
 * GNU General Public License for more details.                                     *
 * You should have received a copy of the GNU General Public License                *
 * along with this program. If not, see <https://www.gnu.org/licenses/>.            *
 ************************************************************************************/

package org.spin.form.match_po_receipt_invoice;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

import org.adempiere.core.domains.models.I_C_Invoice;
import org.adempiere.core.domains.models.I_C_InvoiceLine;
import org.compiere.model.MBPartner;
import org.compiere.model.MClient;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MMatchInv;
import org.compiere.model.MMatchPO;
import org.compiere.model.MOrderLine;
import org.compiere.model.MPeriod;
import org.compiere.model.MRole;
import org.compiere.model.MStorage;
import org.compiere.model.MSysConfig;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.spin.backend.grpc.form.match_po_receipt_invoice.MatchType;
import org.spin.backend.grpc.form.match_po_receipt_invoice.Matched;
import org.spin.backend.grpc.form.match_po_receipt_invoice.Vendor;
import org.spin.base.util.ValueUtil;

public class Util {
	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(Util.class);


	public static Vendor.Builder convertVendor(int businessPartnerId) {
		if (businessPartnerId <= 0) {
			return Vendor.newBuilder();
		}
		MBPartner businessPartner = MBPartner.get(Env.getCtx(), businessPartnerId);
		return convertVendor(businessPartner);
	}

	public static Vendor.Builder convertVendor(MBPartner businessPartner) {
		Vendor.Builder builder = Vendor.newBuilder();
		if (businessPartner == null || businessPartner.getC_BPartner_ID() <= 0) {
			return builder;
		}

		builder.setId(businessPartner.getC_BPartner_ID())
			.setUuid(
				ValueUtil.validateNull(businessPartner.getUUID())
			)
			.setValue(
				ValueUtil.validateNull(businessPartner.getValue())
			)
			.setTaxId(
				ValueUtil.validateNull(businessPartner.getTaxID())
			)
			.setName(
				ValueUtil.validateNull(businessPartner.getName())
			)
			.setDescription(
				ValueUtil.validateNull(businessPartner.getDescription())
			)
		;

		return builder;
	}



	public static Matched.Builder convertMatched(ResultSet resultSet) throws SQLException {
		Matched.Builder builder = Matched.newBuilder();
		if (resultSet == null) {
			return builder;
		}

		builder.setId(
				resultSet.getInt("ID")
			)
			.setUuid(
				ValueUtil.validateNull(
					resultSet.getString(I_C_Invoice.COLUMNNAME_UUID)
				)
			)
			.setHeaderId(
				resultSet.getInt("Header_ID")
			)
			.setHeaderUuid(
				ValueUtil.validateNull(
					resultSet.getString("Header_UUID")
				)
			)
			.setDate(
				ValueUtil.getLongFromTimestamp(
					resultSet.getTimestamp("Date")
				)
			)
			.setDocumentNo(
				ValueUtil.validateNull(
					resultSet.getString(I_C_Invoice.COLUMNNAME_DocumentNo)
				)
			)
			.setLineNo(
				resultSet.getInt(I_C_InvoiceLine.COLUMNNAME_Line)
			)
			.setQuantity(
				ValueUtil.getDecimalFromBigDecimal(
					resultSet.getBigDecimal("Quantity")
				)
			)
			.setProductId(
				resultSet.getInt(I_C_InvoiceLine.COLUMNNAME_M_Product_ID)
			)
			.setProductName(
				ValueUtil.validateNull(
					resultSet.getString("M_Product_Name")
				)
			)
			.setVendorId(
				resultSet.getInt(I_C_Invoice.COLUMNNAME_C_BPartner_ID)
			)
			.setVendorName(
				ValueUtil.validateNull(
					resultSet.getString("C_BPartner_Name")
				)
			)
		;

		return builder;
	}


	public static String getDateColumn(int matchType) {
		if (matchType == MatchType.INVOICE_VALUE) {
			return "hdr.DateInvoiced";
		}
		else if (matchType == MatchType.PURCHASE_ORDER_VALUE) {
			return "hdr.DateOrdered";
		}
		// Receipt
		return "hdr.MovementDate";
	}

	public static String getQuantityColumn(int matchType) {
		if (matchType == MatchType.INVOICE_VALUE) {
			return "lin.QtyInvoiced";
		}
		else if (matchType == MatchType.PURCHASE_ORDER_VALUE) {
			return "lin.QtyOrdered";
		}
		// Receipt
		return "lin.MovementQty";
	}

	public static String getSQL(boolean isMatched, int matchTypeFrom, int matchTypeTo) {
		String sql = "";
		if (matchTypeFrom == MatchType.INVOICE_VALUE) {
			sql = "SELECT lin.C_InvoiceLine_ID AS ID, lin.UUID AS UUID, "
				+ " hdr.C_Invoice_ID AS Header_ID, hrd.Header_UUID, hdr.DateInvoiced AS Date,"
				+ " hdr.C_Invoice_ID, hdr.DocumentNo, hdr.DateInvoiced, "
				+ " bp.Name AS C_BPartner_Name, hdr.C_BPartner_ID, "
				+ " lin.Line, lin.C_InvoiceLine_ID, "
				+ " p.Name AS M_Product_Name, lin.M_Product_ID, "
				+ " lin.QtyInvoiced, SUM(COALESCE(mi.Qty, 0)), org.Name, hdr.AD_Org_ID "
				+ "FROM C_Invoice hdr"
				+ " INNER JOIN AD_Org org ON (hdr.AD_Org_ID = org.AD_Org_ID)"
				+ " INNER JOIN C_BPartner bp ON (hdr.C_BPartner_ID = bp.C_BPartner_ID)"
				+ " INNER JOIN C_InvoiceLine lin ON (hdr.C_Invoice_ID = lin.C_Invoice_ID)"
				+ " INNER JOIN M_Product p ON (lin.M_Product_ID = p.M_Product_ID)"
				+ " INNER JOIN C_DocType dt ON (hdr.C_DocType_ID = dt.C_DocType_ID AND dt.DocBaseType IN ('API', 'APC'))"
				+ " FULL JOIN M_MatchInv mi ON (lin.C_InvoiceLine_ID = mi.C_InvoiceLine_ID) "
				+ "WHERE hdr.DocStatus IN ('CO','CL')"
			;
		}
		else if (matchTypeFrom == MatchType.PURCHASE_ORDER_VALUE) {
			String lineType = matchTypeTo == MatchType.RECEIPT_VALUE ? "M_InOutLine_ID" : "C_InvoiceLine_ID";

			sql = "SELECT lin.C_OrderLine_ID AS ID, lin.UUID AS UUID, "
				+ " hdr.C_Order_ID AS Header_ID, hrd.Header_UUID, hdr.DateOrdered AS Date,"
				+ " hdr.C_Order_ID, hdr.DocumentNo, hdr.DateOrdered, "
				+ " bp.Name AS C_BPartner_Name, hdr.C_BPartner_ID, "
				+ " lin.Line, lin.C_OrderLine_ID, "
				+ " p.Name AS M_Product_Name, lin.M_Product_ID, "
				+ " lin.QtyOrdered, SUM(COALESCE(mo.Qty, 0)), org.Name, hdr.AD_Org_ID "
				+ "FROM C_Order hdr"
				+ " INNER JOIN AD_Org org ON (hdr.AD_Org_ID = org.AD_Org_ID)"
				+ " INNER JOIN C_BPartner bp ON (hdr.C_BPartner_ID = bp.C_BPartner_ID)"
				+ " INNER JOIN C_OrderLine lin ON (hdr.C_Order_ID = lin.C_Order_ID)"
				+ " INNER JOIN M_Product p ON (lin.M_Product_ID = p.M_Product_ID)"
				+ " INNER JOIN C_DocType dt ON (hdr.C_DocType_ID = dt.C_DocType_ID AND dt.DocBaseType = 'POO')"
				+ " FULL JOIN M_MatchPO mo ON (lin.C_OrderLine_ID = mo.C_OrderLine_ID) "
				+ " WHERE "
			;
			
			if (isMatched) {
				sql += " mo." + lineType + " IS NOT NULL ";
			} else {
 				sql += " ( mo." + lineType + " IS NULL OR "
					+ " (lin.QtyOrdered <> (SELECT sum(mo1.Qty) AS Qty"
					+ " FROM m_matchpo mo1 WHERE "
					+ " mo1.C_ORDERLINE_ID = lin.C_ORDERLINE_ID AND "
					+ " hdr.C_ORDER_ID = lin.C_ORDER_ID AND "
					+ " mo1." + lineType
					+ " IS NOT NULL group by mo1.C_ORDERLINE_ID))) "
				;
			}
			sql += " AND hdr.DocStatus IN ('CO', 'CL') ";
		}
		// Receipt
		else {
			sql = "SELECT lin.M_InOutLine_ID AS ID, lin.UUID AS UUID, "
				+ " hdr.M_InOut_ID AS Header_ID, hrd.Header_UUID, hdr.MovementDate AS Date,"
				+ " hdr.M_InOut_ID, hdr.DocumentNo, hdr.MovementDate, "
				+ " bp.Name AS C_BPartner_Name, hdr.C_BPartner_ID, "
				+ " lin.Line, lin.M_InOutLine_ID, "
				+ " p.Name AS M_Product_Name, lin.M_Product_ID, "
				+ " lin.MovementQty, SUM(COALESCE(m.Qty, 0)), org.Name, hdr.AD_Org_ID "
				+ "FROM M_InOut hdr"
				+ " INNER JOIN AD_Org org ON (hdr.AD_Org_ID = org.AD_Org_ID)"
				+ " INNER JOIN C_BPartner bp ON (hdr.C_BPartner_ID = bp.C_BPartner_ID)"
				+ " INNER JOIN M_InOutLine lin ON (hdr.M_InOut_ID = lin.M_InOut_ID)"
				+ " INNER JOIN M_Product p ON (lin.M_Product_ID = p.M_Product_ID)"
				+ " INNER JOIN C_DocType dt ON (hdr.C_DocType_ID = dt.C_DocType_ID AND dt.DocBaseType IN ('MMR', 'MMS'))"
				+ " FULL JOIN "
				+ (matchTypeFrom == MatchType.PURCHASE_ORDER_VALUE ? "M_MatchPO" : "M_MatchInv")
				+ " m ON (lin.M_InOutLine_ID = m.M_InOutLine_ID) "
				+ "WHERE hdr.DocStatus IN ('CO', 'CL') and dt.issotrx = 'N' "
			;
		}
		return sql;
	}

	public static String getGroupBy(boolean isMatched, int matchType) {
		if (matchType == MatchType.INVOICE_VALUE) {
			return " GROUP BY hdr.C_Invoice_ID,hdr.DocumentNo,hdr.DateInvoiced,bp.Name,hdr.C_BPartner_ID,"
				+ " lin.Line,lin.C_InvoiceLine_ID,p.Name,lin.M_Product_ID,lin.QtyInvoiced, org.Name, hdr.AD_Org_ID "
				+ "HAVING "
				+ (isMatched ? "0" : "lin.QtyInvoiced")
				+ "<>SUM(COALESCE(mi.Qty,0))"
			;
		}
		else if (matchType == MatchType.PURCHASE_ORDER_VALUE) {
			return " GROUP BY hdr.C_Order_ID,hdr.DocumentNo,hdr.DateOrdered,bp.Name,hdr.C_BPartner_ID,"
				+ " lin.Line,lin.C_OrderLine_ID,p.Name,lin.M_Product_ID,lin.QtyOrdered, org.Name, hdr.AD_Org_ID "
				+ "HAVING "
				+ (isMatched ? "0" : "lin.QtyOrdered")
				+ "<>SUM(COALESCE(mo.Qty,0))";
		}
		// Receipt
		return " GROUP BY hdr.M_InOut_ID,hdr.DocumentNo,hdr.MovementDate,bp.Name,hdr.C_BPartner_ID,"
			+ " lin.Line,lin.M_InOutLine_ID,p.Name,lin.M_Product_ID,lin.MovementQty, org.Name, hdr.AD_Org_ID "
			+ "HAVING "
			+ (isMatched ? "0" : "lin.MovementQty")
			+ "<>SUM(COALESCE(m.Qty,0))"
		;
	}



	public static Matched.Builder getMatchedSelectedFrom(int matchFromSelectedId, boolean isMatched, int matchTypeFrom, int matchTypeTo) {
		Matched.Builder builder = Matched.newBuilder();
		if (matchFromSelectedId <= 0) {
			return builder;
		}

		// Receipt
		String whereClause = " AND lin.M_InOutLine_ID = ";
		if (matchTypeFrom == MatchType.INVOICE_VALUE) {
			whereClause = " AND lin.C_InvoiceLine_ID = ";
		}
		else if (matchTypeFrom == MatchType.PURCHASE_ORDER_VALUE) {
			whereClause = " AND lin.C_OrderLine_ID = ";
		}
		whereClause += matchFromSelectedId;

		final String sql = getSQL(isMatched, matchTypeFrom, matchTypeTo);
		final String groupBy = getGroupBy(isMatched, matchTypeFrom);

		final String sqlWithAccess = MRole.getDefault().addAccessSQL(
			sql + whereClause,
			"hdr",
			MRole.SQL_FULLYQUALIFIED,
			MRole.SQL_RO
		) + groupBy;

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(sqlWithAccess, null);
			rs = pstmt.executeQuery();

			if (rs.next()) {
				builder = convertMatched(rs);
			}
		} catch (Exception e) {
			// TODO: handle exception
		}

		return builder;
	}



	/**
	 * Create Matching Record
	 * @param invoice true if matching invoice false if matching PO
	 * @param M_InOutLine_ID shipment line
	 * @param Line_ID C_InvoiceLine_ID or C_OrderLine_ID
	 * @param qty quantity
	 * @param trxName 
	 * @return true if created
	 */
	public static boolean createMatchRecord(
		boolean invoice, int M_InOutLine_ID, int Line_ID,
		BigDecimal qty, String trxName
	) {
		if (qty.compareTo(Env.ZERO) == 0) {
			return true;
		}
		log.fine("IsInvoice=" + invoice
			+ ", M_InOutLine_ID=" + M_InOutLine_ID + ", Line_ID=" + Line_ID
			+ ", Qty=" + qty);
		//
		boolean success = false;
		MInOutLine sLine = new MInOutLine (Env.getCtx(), M_InOutLine_ID, trxName);
		//	Shipment - Invoice
		if (invoice) {
			//	Update Invoice Line
			MInvoiceLine iLine = new MInvoiceLine (Env.getCtx(), Line_ID, trxName);
			iLine.setM_InOutLine_ID(M_InOutLine_ID);
			if (sLine.getC_OrderLine_ID() != 0) {
				iLine.setC_OrderLine_ID(sLine.getC_OrderLine_ID());
			}
			iLine.saveEx();
			//	Create Shipment - Invoice Link
			if (iLine.getM_Product_ID() != 0) {
				Boolean useReceiptDateAcct = MSysConfig.getBooleanValue(
					"MATCHINV_USE_DATEACCT_FROM_RECEIPT",
					false,
					iLine.getAD_Client_ID()
				);
				MMatchInv match = null;
				Boolean isreceiptPeriodOpen = MPeriod.isOpen(
					Env.getCtx(),
					sLine.getParent().getDateAcct(),
					sLine.getParent().getC_DocType().getDocBaseType(),
					sLine.getParent().getAD_Org_ID(),
					trxName
				);
				Boolean isInvoicePeriodOpen = MPeriod.isOpen(
					Env.getCtx(),
					iLine.getParent().getDateAcct(),
					iLine.getParent().getC_DocType().getDocBaseType(),
					iLine.getParent().getAD_Org_ID(),
					trxName
				);

				if (useReceiptDateAcct & isreceiptPeriodOpen) {
					match= new MMatchInv(iLine,sLine.getParent().getDateAcct() , qty);
				}
				else if (isInvoicePeriodOpen){
					match = new MMatchInv(iLine, iLine.getParent().getDateAcct(), qty);
				}
				else {
					match = new MMatchInv(iLine, null, qty);
				}
				match.setM_InOutLine_ID(M_InOutLine_ID);
				match.saveEx();
				if (match.save()) {
					success = true;
				}
				else {
					log.log(Level.SEVERE, "Inv Match not created: " + match);
				}
				if (match.getC_InvoiceLine().getC_Invoice().getDocStatus().equals("VO") ||
						match.getC_InvoiceLine().getC_Invoice().getDocStatus().equals("RE") ||
						match.getM_InOutLine().getM_InOut().getDocStatus().equals("VO") ||
						match.getM_InOutLine().getM_InOut().getDocStatus().equals("RE")) {
					match.reverseIt(match.getDateAcct());
				}
			}
			else {
				success = true;
			}
			//	Create PO - Invoice Link = corrects PO
			if (iLine.getC_OrderLine_ID() != 0 && iLine.getM_Product_ID() != 0) {
				MMatchPO matchPO = new MMatchPO(iLine, iLine.getParent().getDateAcct() , qty);
				matchPO.setC_InvoiceLine_ID(iLine);
				if (!matchPO.save()) {
					log.log(Level.SEVERE, "PO(Inv) Match not created: " + matchPO);
				}
				if (MClient.isClientAccountingImmediate()) {
					String ignoreError = DocumentEngine.postImmediate(matchPO.getCtx(), matchPO.getAD_Client_ID(), matchPO.get_Table_ID(), matchPO.get_ID(), true, matchPO.get_TrxName());						
				}
			}
		}
		//	Shipment - Order
		else {
			//	Update Shipment Line
			sLine.setC_OrderLine_ID(Line_ID);
			sLine.saveEx();
			//	Update Order Line
			MOrderLine oLine = new MOrderLine(Env.getCtx(), Line_ID, trxName);
			//	other in MInOut.completeIt
			if (oLine.get_ID() != 0) {
				oLine.setQtyReserved(oLine.getQtyReserved().subtract(qty));
				if (!oLine.save()) {
					log.severe("QtyReserved not updated - C_OrderLine_ID=" + Line_ID);
				}
			}

			//	Create PO - Shipment Link
			if (sLine.getM_Product_ID() != 0) {
				MMatchPO match = new MMatchPO (sLine, null, qty);
				if (!match.save()) {
					log.log(Level.SEVERE, "PO Match not created: " + match);
				}
				else {
					success = true;
					//	Correct Ordered Qty for Stocked Products (see MOrder.reserveStock / MInOut.processIt)
					if (sLine.getProduct() != null && sLine.getProduct().isStocked()) {
						success = MStorage.add(
							Env.getCtx(), sLine.getM_Warehouse_ID(),
							sLine.getM_Locator_ID(),
							sLine.getM_Product_ID(),
							sLine.getM_AttributeSetInstance_ID(),
							oLine.getM_AttributeSetInstance_ID(),
							null,
							null,
							qty.negate(),
							trxName
						);
					}
				}
			}
			else {
				success = true;
			}
		}
		return success;
	}   //  createMatchRecord
}
