/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * This program is free software; you can redistribute it and/or modify it    		 *
 * under the terms version 2 or later of the GNU General Public License as published *
 * by the Free Software Foundation. This program is distributed in the hope   		 *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied 		 *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           		 *
 * See the GNU General Public License for more details.                       		 *
 * You should have received a copy of the GNU General Public License along    		 *
 * with this program; if not, write to the Free Software Foundation, Inc.,    		 *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     		 *
 * For the text or an alternative of this public license, you may reach us    		 *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, S.A. All Rights Reserved. *
 * Contributor(s): Yamel Senih www.erpya.com				  		                 *
 *************************************************************************************/
package org.spin.pos.service.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.adempiere.core.domains.models.I_C_InvoiceLine;
import org.adempiere.core.domains.models.I_C_Order;
import org.adempiere.core.domains.models.I_C_OrderLine;
import org.adempiere.core.domains.models.I_M_InOutLine;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MPOS;
import org.compiere.model.MPayment;
import org.compiere.model.MTable;
import org.compiere.model.MUOM;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;

/**
 * A util class for change values for documents
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 */
public class RMAUtil {
	
    /**
     * Copy all account dimensions
     * @param source
     * @param target
     */
    public static void copyAccountDimensions(PO source, PO target) {
		//	
		if(source.get_ValueAsInt("AD_OrgTrx_ID") != 0) {
			target.set_ValueOfColumn("AD_OrgTrx_ID", source.get_ValueAsInt("AD_OrgTrx_ID"));
		}
		if(source.get_ValueAsInt("C_Project_ID") != 0) {
			target.set_ValueOfColumn("C_Project_ID", source.get_ValueAsInt("C_Project_ID"));
		}
		if(source.get_ValueAsInt("C_Campaign_ID") != 0) {
			target.set_ValueOfColumn("C_Campaign_ID", source.get_ValueAsInt("C_Campaign_ID"));
		}
		if(source.get_ValueAsInt("C_Activity_ID") != 0) {
			target.set_ValueOfColumn("C_Activity_ID", source.get_ValueAsInt("C_Activity_ID"));
		}
		if(source.get_ValueAsInt("User1_ID") != 0) {
			target.set_ValueOfColumn("User1_ID", source.get_ValueAsInt("User1_ID"));
		}
		if(source.get_ValueAsInt("User2_ID") != 0) {
			target.set_ValueOfColumn("User2_ID", source.get_ValueAsInt("User2_ID"));
		}
		if(source.get_ValueAsInt("User3_ID") != 0) {
			target.set_ValueOfColumn("User3_ID", source.get_ValueAsInt("User3_ID"));
		}
		if(source.get_ValueAsInt("User4_ID") != 0) {
			target.set_ValueOfColumn("User4_ID", source.get_ValueAsInt("User4_ID"));
		}
    }
    
	/**
	 * Get Default document Type
	 * @param pointOfSales
	 * @param salesOrderDocumentTypeId
	 * @return
	 */
	public static int getReturnDocumentTypeId(int posId, int sessionPosId, int salesOrderDocumentTypeId) {
		MPOS pointOfSales = new MPOS(Env.getCtx(), posId, null);
		final String TABLE_NAME = "C_POSDocumentTypeAllocation";
		if(MTable.getTable_ID(TABLE_NAME) <= 0) {
			return pointOfSales.get_ValueAsInt("C_DocTypeRMA_ID");
		}
		//	Get from current sales order document type
		PO documentTypeAllocation = new Query(Env.getCtx(), TABLE_NAME, "C_POS_ID IN(?, ?) AND C_DocType_ID = ?", null)
			.setParameters(pointOfSales.getC_POS_ID(), sessionPosId, salesOrderDocumentTypeId)
			.setClient_ID()
			.setOnlyActiveRecords(true)
			.first();
		int returnDocumentTypeId = 0;
		if(documentTypeAllocation == null 
				|| documentTypeAllocation.get_ID() <= 0) {
			returnDocumentTypeId = pointOfSales.get_ValueAsInt("C_DocTypeRMA_ID");
		} else {
			returnDocumentTypeId = documentTypeAllocation.get_ValueAsInt("POSReturnDocumentType_ID");
		}
		if(returnDocumentTypeId <= 0) {
			throw new AdempiereException("@C_DocTypeRMA_ID@ @NotFound@");
		}
		//	
		return returnDocumentTypeId;
	}
	
	
    /**
     * Get Invoice Line Reference
     * @param sourceOrderLine
     * @param transactionName
     * @return
     */
    public static int getInvoiceLineReferenceId(MOrderLine sourceOrderLine, String transactionName) {
    	return new Query(sourceOrderLine.getCtx(), I_C_InvoiceLine.Table_Name, "C_OrderLine_ID = ?", transactionName)
    			.setParameters(sourceOrderLine.getC_OrderLine_ID())
    			.setClient_ID()
    			.firstId();
    }
    
    /**
     * Get Shipment reference
     * @param sourceOrderLine
     * @param transactionName
     * @return
     */
    public static int getShipmentLineReferenceId(MOrderLine sourceOrderLine, String transactionName) {
    	return new Query(sourceOrderLine.getCtx(), I_M_InOutLine.Table_Name, "C_OrderLine_ID = ?", transactionName)
    			.setParameters(sourceOrderLine.getC_OrderLine_ID())
    			.setClient_ID()
    			.firstId();
    }
    
    
    /**
     * Create return order from source order
     * @param pos
     * @param sourceOrder
     * @param transactionName
     * @return
     */
    public static MOrder copyRMAFromOrder(MPOS pos, MOrder sourceOrder, String transactionName) {
    	MOrder returnOrder = new MOrder (sourceOrder.getCtx(), 0, transactionName);
		returnOrder.set_TrxName(transactionName);
		PO.copyValues(sourceOrder, returnOrder, false);
		//
		returnOrder.setDocStatus (DocAction.STATUS_Drafted);		//	Draft
		returnOrder.setDocAction(DocAction.ACTION_Complete);
		//
		returnOrder.setIsSelected (false);
		returnOrder.setDateOrdered(OrderUtil.getToday());
		returnOrder.setDateAcct(OrderUtil.getToday());
		returnOrder.setDatePromised(OrderUtil.getToday());	//	assumption
		returnOrder.setDatePrinted(null);
		returnOrder.setIsPrinted (false);
		//
		returnOrder.setIsApproved (false);
		returnOrder.setIsCreditApproved(false);
		returnOrder.setC_Payment_ID(0);
		returnOrder.setC_CashLine_ID(0);
		//	Amounts are updated  when adding lines
		returnOrder.setGrandTotal(Env.ZERO);
		returnOrder.setTotalLines(Env.ZERO);
		//
		returnOrder.setIsDelivered(false);
		returnOrder.setIsInvoiced(false);
		returnOrder.setIsSelfService(false);
		returnOrder.setIsTransferred (false);
		returnOrder.setPosted (false);
		returnOrder.setProcessed (false);
		returnOrder.setAD_Org_ID(sourceOrder.getAD_Org_ID());
		returnOrder.saveEx(sourceOrder.get_TrxName());
		int targetDocumentTypeId = RMAUtil.getReturnDocumentTypeId(sourceOrder.getC_POS_ID(), pos.getC_POS_ID(), sourceOrder.getC_DocTypeTarget_ID());
		//	Set Document base for return
		if(targetDocumentTypeId != 0) {
        	returnOrder.setC_DocTypeTarget_ID(targetDocumentTypeId);
        } else {
        	returnOrder.setC_DocTypeTarget_ID(MDocType.getDocTypeBaseOnSubType(sourceOrder.getAD_Org_ID(), 
            		MDocType.DOCBASETYPE_SalesOrder , MDocType.DOCSUBTYPESO_ReturnMaterial));
    	}
        //	Set references
		returnOrder.setC_BPartner_ID(sourceOrder.getC_BPartner_ID());
		returnOrder.setRef_Order_ID(sourceOrder.getC_Order_ID());
		returnOrder.setProcessed(false);
		//	
		RMAUtil.copyAccountDimensions(sourceOrder, returnOrder);
		returnOrder.setM_PriceList_ID(sourceOrder.getM_PriceList_ID());
		returnOrder.saveEx();
		return returnOrder;
    }
    
    /**
     * Copy a order line and create a new instance for RMA Line
     * @param rma
     * @param sourcerOrderLine
     * @param transactionName
     * @return
     */
    public static MOrderLine copyRMALineFromOrder(MOrder rma, MOrderLine sourcerOrderLine, String transactionName) {
    	MOrderLine returnOrderLine = new MOrderLine(rma);
		if (sourcerOrderLine.getM_Product_ID() > 0) {
			returnOrderLine.setM_Product_ID(sourcerOrderLine.getM_Product_ID());
			if(sourcerOrderLine.getM_AttributeSetInstance_ID() > 0) {
				returnOrderLine.setM_AttributeSetInstance_ID(sourcerOrderLine.getM_AttributeSetInstance_ID());
			}
		} else if(sourcerOrderLine.getC_Charge_ID() != 0) {
			returnOrderLine.setC_Charge_ID(sourcerOrderLine.getC_Charge_ID());
		}
		//	
		RMAUtil.copyAccountDimensions(sourcerOrderLine, returnOrderLine);
		//	Set quantity
		returnOrderLine.setQty(sourcerOrderLine.getQtyEntered());
		returnOrderLine.setQtyOrdered(sourcerOrderLine.getQtyOrdered());
		returnOrderLine.setC_UOM_ID(sourcerOrderLine.getC_UOM_ID());
		returnOrderLine.setPriceList(sourcerOrderLine.getPriceList());
		returnOrderLine.setPriceEntered(sourcerOrderLine.getPriceEntered());
		returnOrderLine.setPriceActual(sourcerOrderLine.getPriceActual());
		returnOrderLine.setC_Tax_ID(sourcerOrderLine.getC_Tax_ID());
		returnOrderLine.setRef_OrderLine_ID(sourcerOrderLine.getC_OrderLine_ID());
		//	Foreign references
		int invoioceLineId = RMAUtil.getInvoiceLineReferenceId(sourcerOrderLine, transactionName);
		if(invoioceLineId > 0) {
			returnOrderLine.setRef_InvoiceLine_ID(invoioceLineId);
		}
		int shipmentLineId = RMAUtil.getShipmentLineReferenceId(sourcerOrderLine, transactionName);
		if(shipmentLineId > 0) {
			returnOrderLine.setRef_InOutLine_ID(shipmentLineId);
		}
		return returnOrderLine;
    }
    
    /**
     * Reverse all payments
     * @param pos
     * @param sourceOrder
     * @param returnOrder
     * @param transactionName
     */
    public static void createReversedPayments(MPOS pos, MOrder sourceOrder, MOrder returnOrder, String transactionName) {
    	MPayment.getOfOrder(sourceOrder).forEach(sourcePayment -> {
        	MPayment returnPayment = new MPayment(sourceOrder.getCtx(), 0, transactionName);
            PO.copyValues(sourcePayment, returnPayment);
            returnPayment.setC_Invoice_ID(-1);
            returnPayment.setDateTrx(OrderUtil.getToday());
            returnPayment.setDateAcct(OrderUtil.getToday());
            returnPayment.addDescription(Msg.parseTranslation(sourceOrder.getCtx(), " @From@ " + sourcePayment.getDocumentNo() + " @of@ @C_Order_ID@ " + sourceOrder.getDocumentNo()));
            returnPayment.setIsReceipt(sourcePayment.isReceipt());
            returnPayment.setPayAmt(sourcePayment.getPayAmt().negate());
            returnPayment.setDocAction(DocAction.ACTION_Complete);
            returnPayment.setDocStatus(DocAction.STATUS_Drafted);
            returnPayment.setC_Order_ID(returnOrder.getC_Order_ID());
            returnPayment.setIsPrepayment(sourcePayment.isPrepayment());
            returnPayment.saveEx();
        });
    }
    
    /**
     * Generate Credit Memo from Return Order
     * @param returnOrder
     * @param transactionName
     */
    public static void generateCreditMemoFromRMA(MOrder returnOrder, String transactionName) {
    	MInvoice invoice = new MInvoice (returnOrder, 0, OrderUtil.getToday());
		invoice.saveEx();
    	//	Convert Lines
		new Query(returnOrder.getCtx(), I_C_OrderLine.Table_Name, "C_Order_ID = ?", transactionName)
			.setParameters(returnOrder.getC_Order_ID())
			.setClient_ID()
			.getIDsAsList()
			.forEach(returnOrderLineId -> {
				MOrderLine returnOrderLine = new MOrderLine(returnOrder.getCtx(), returnOrderLineId, transactionName);
				MOrderLine sourcerOrderLine = new MOrderLine(returnOrder.getCtx(), returnOrderLine.getRef_OrderLine_ID(), transactionName);
				MInvoiceLine line = new MInvoiceLine (invoice);
				line.setOrderLine(returnOrderLine);
				BigDecimal toReturn = sourcerOrderLine.getQtyInvoiced();
				line.setQtyInvoiced(toReturn);
				line.setQtyEntered(toReturn);
				line.setQtyEntered(toReturn
						.multiply(returnOrderLine.getQtyEntered())
						.divide(returnOrderLine.getQtyOrdered(), MUOM.getPrecision(returnOrder.getCtx(), returnOrderLine.getC_UOM_ID()), RoundingMode.HALF_UP));	
				line.setLine(returnOrderLine.getLine());
				line.saveEx();
		});
		if(!invoice.processIt(MOrder.DOCACTION_Complete)) {
        	throw new AdempiereException(invoice.getProcessMsg());
        }
		returnOrder.setIsInvoiced(true);
		returnOrder.set_ValueOfColumn("AssignedSalesRep_ID", null);
		invoice.saveEx();
    }
    
    /**
     * Generate Return from Return Order
     * @param returnOrder
     * @param transactionName
     */
    public static void generateReturnFromRMA(MOrder returnOrder, String transactionName) {
    	MInOut shipment = new MInOut (returnOrder, 0, OrderUtil.getToday());
		shipment.setM_Warehouse_ID(returnOrder.getM_Warehouse_ID());	//	sets Org too
		shipment.saveEx();
		//	Convert Lines
		new Query(returnOrder.getCtx(), I_C_OrderLine.Table_Name, "C_Order_ID = ?", transactionName)
			.setParameters(returnOrder.getC_Order_ID())
			.setClient_ID()
			.getIDsAsList()
			.forEach(returnOrderLineId -> {
				MOrderLine returnOrderLine = new MOrderLine(returnOrder.getCtx(), returnOrderLineId, transactionName);
				MOrderLine sourcerOrderLine = new MOrderLine(returnOrder.getCtx(), returnOrderLine.getRef_OrderLine_ID(), transactionName);
				MInOutLine line = new MInOutLine (shipment);
				BigDecimal toReturn = sourcerOrderLine.getQtyDelivered();
				line.setOrderLine(returnOrderLine, 0, Env.ZERO);
				line.setQty(toReturn);
				line.setQtyEntered(toReturn
						.multiply(returnOrderLine.getQtyEntered())
						.divide(returnOrderLine.getQtyOrdered(), MUOM.getPrecision(returnOrder.getCtx(), returnOrderLine.getC_UOM_ID()), RoundingMode.HALF_UP));	
				line.setLine(returnOrderLine.getLine());
				line.saveEx();
		});
		if(!shipment.processIt(MOrder.DOCACTION_Complete)) {
        	throw new AdempiereException(shipment.getProcessMsg());
        }
		returnOrder.setIsDelivered(true);
		shipment.saveEx();
    }
    
    /**
     * Create all lines from order
     * @param sourceOrder
     * @param returnOrder
     * @param transactionName
     */
    public static void createReturnOrderLines(MOrder sourceOrder, MOrder returnOrder, String transactionName) {
    	new Query(sourceOrder.getCtx(), I_C_OrderLine.Table_Name, "C_Order_ID = ?", transactionName)
    		.setParameters(sourceOrder.getC_Order_ID())
    		.setClient_ID()
    		.getIDsAsList()
    		.forEach(sourceOrderLineId -> {
    			MOrderLine sourcerOrderLine = new MOrderLine(sourceOrder.getCtx(), sourceOrderLineId, transactionName);
				//	Create new Invoice Line
				MOrderLine returnOrderLine = new MOrderLine(returnOrder);
				if (sourcerOrderLine.getM_Product_ID() > 0) {
					returnOrderLine.setM_Product_ID(sourcerOrderLine.getM_Product_ID());
					if(sourcerOrderLine.getM_AttributeSetInstance_ID() > 0) {
						returnOrderLine.setM_AttributeSetInstance_ID(sourcerOrderLine.getM_AttributeSetInstance_ID());
					}
				} else if(sourcerOrderLine.getC_Charge_ID() != 0) {
					returnOrderLine.setC_Charge_ID(sourcerOrderLine.getC_Charge_ID());
				}
				//	
				RMAUtil.copyAccountDimensions(sourcerOrderLine, returnOrderLine);
				//	Set quantity
				returnOrderLine.setQty(sourcerOrderLine.getQtyEntered());
				returnOrderLine.setQtyOrdered(sourcerOrderLine.getQtyOrdered());
				returnOrderLine.setC_UOM_ID(sourcerOrderLine.getC_UOM_ID());
				returnOrderLine.setPriceList(sourcerOrderLine.getPriceList());
				returnOrderLine.setPriceEntered(sourcerOrderLine.getPriceEntered());
				returnOrderLine.setPriceActual(sourcerOrderLine.getPriceActual());
				returnOrderLine.setC_Tax_ID(sourcerOrderLine.getC_Tax_ID());
				returnOrderLine.setRef_OrderLine_ID(sourceOrderLineId);
				//	Foreign references
				int invoioceLineId = RMAUtil.getInvoiceLineReferenceId(sourcerOrderLine, transactionName);
				if(invoioceLineId > 0) {
					returnOrderLine.setRef_InvoiceLine_ID(invoioceLineId);
				}
				int shipmentLineId = RMAUtil.getShipmentLineReferenceId(sourcerOrderLine, transactionName);
				if(shipmentLineId > 0) {
					returnOrderLine.setRef_InOutLine_ID(shipmentLineId);
				}
				//	Save
				returnOrderLine.saveEx(transactionName);
    		});
    }
    
    /**
     * Create all lines from order
     * @param sourceOrder
     * @param returnOrder
     * @param transactionName
     */
    public static void copyRMALinesFromOrder(MOrder sourceOrder, MOrder returnOrder, String transactionName) {
    	new Query(sourceOrder.getCtx(), I_C_OrderLine.Table_Name, "C_Order_ID = ?", transactionName)
    		.setParameters(sourceOrder.getC_Order_ID())
    		.setClient_ID()
    		.getIDsAsList()
    		.forEach(sourceOrderLineId -> {
    			MOrderLine sourcerOrderLine = new MOrderLine(sourceOrder.getCtx(), sourceOrderLineId, transactionName);
				//	Create new Invoice Line
				MOrderLine returnOrderLine = RMAUtil.copyRMALineFromOrder(returnOrder, sourcerOrderLine, transactionName);
				//	Save
				returnOrderLine.saveEx(transactionName);
    		});
    }
    
    /**
     * Delete RMA Line
     * @param rmaLineId
     */
    public static void deleteRMALine(int rmaLineId) {
    	if(rmaLineId <= 0) {
			throw new AdempiereException("@M_RMALine_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			MOrderLine rmaLine = new Query(Env.getCtx(), I_C_OrderLine.Table_Name, I_C_OrderLine.COLUMNNAME_C_OrderLine_ID + " = ?", transactionName)
					.setParameters(rmaLineId)
					.setClient_ID()
					.first();
			if(rmaLine != null
					&& rmaLine.getC_OrderLine_ID() > 0) {
				//	Validate processed Order
				if(rmaLine.isProcessed()) {
					throw new AdempiereException("@M_RMALine_ID@ @Processed@");
				}
				rmaLine.deleteEx(true);
			}
		});
    }
    
    /**
     * Delete RMA
     * @param rmaId
     */
    public static void deleteRMA(int rmaId) {
    	if(rmaId <= 0) {
			throw new AdempiereException("@M_RMA_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			MOrder rmaLine = new Query(Env.getCtx(), I_C_Order.Table_Name, I_C_Order.COLUMNNAME_C_Order_ID + " = ?", transactionName)
					.setParameters(rmaId)
					.setClient_ID()
					.first();
			if(rmaLine != null
					&& rmaLine.getC_Order_ID() > 0) {
				//	Validate processed Order
				if(rmaLine.isProcessed()) {
					throw new AdempiereException("@M_RMA_ID@ @Processed@");
				}
				rmaLine.deleteEx(true);
			}
		});
    }
}
