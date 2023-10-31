/************************************************************************************
 * Copyright (C) 2012-2023 E.R.P. Consultores y Asociados, C.A.                     *
 * Contributor(s): Yamel Senih ysenih@erpya.com                                     *
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
package org.spin.grpc.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.adempiere.core.domains.models.I_AD_PrintFormatItem;
import org.adempiere.core.domains.models.I_AD_Ref_List;
import org.adempiere.core.domains.models.I_AD_User;
import org.adempiere.core.domains.models.I_C_BP_BankAccount;
import org.adempiere.core.domains.models.I_C_BP_Group;
import org.adempiere.core.domains.models.I_C_BPartner;
import org.adempiere.core.domains.models.I_C_Bank;
import org.adempiere.core.domains.models.I_C_BankAccount;
import org.adempiere.core.domains.models.I_C_BankStatement;
import org.adempiere.core.domains.models.I_C_Campaign;
import org.adempiere.core.domains.models.I_C_Charge;
import org.adempiere.core.domains.models.I_C_City;
import org.adempiere.core.domains.models.I_C_ConversionType;
import org.adempiere.core.domains.models.I_C_Currency;
import org.adempiere.core.domains.models.I_C_Invoice;
import org.adempiere.core.domains.models.I_C_Order;
import org.adempiere.core.domains.models.I_C_OrderLine;
import org.adempiere.core.domains.models.I_C_POS;
import org.adempiere.core.domains.models.I_C_Payment;
import org.adempiere.core.domains.models.I_M_InOut;
import org.adempiere.core.domains.models.I_M_InOutLine;
import org.adempiere.core.domains.models.I_M_Product;
import org.adempiere.core.domains.models.I_M_Storage;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.GenericPO;
import org.adempiere.pos.process.ReverseTheSalesTransaction;
import org.adempiere.pos.services.CPOS;
import org.adempiere.pos.util.POSTicketHandler;
import org.compiere.model.MAllocationHdr;
import org.compiere.model.MAllocationLine;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MBPBankAccount;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MBankStatement;
import org.compiere.model.MCharge;
import org.compiere.model.MColumn;
import org.compiere.model.MConversionRate;
import org.compiere.model.MCurrency;
import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MLocation;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MPOS;
import org.compiere.model.MPayment;
import org.compiere.model.MPriceList;
import org.compiere.model.MPriceListVersion;
import org.compiere.model.MProduct;
import org.compiere.model.MProductPrice;
import org.compiere.model.MProductPricing;
import org.compiere.model.MResourceAssignment;
import org.compiere.model.MStorage;
import org.compiere.model.MTable;
import org.compiere.model.MTax;
import org.compiere.model.MUOM;
import org.compiere.model.MUOMConversion;
import org.compiere.model.MUser;
import org.compiere.model.MWarehouse;
import org.compiere.model.M_Element;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfo;
import org.compiere.util.CCache;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.MimeType;
import org.compiere.util.TimeUtil;
import org.compiere.util.Trx;
import org.compiere.util.Util;
import org.spin.backend.grpc.common.Currency;
import org.eevolution.services.dsl.ProcessBuilder;
import org.spin.backend.grpc.common.ProcessLog;
import org.spin.backend.grpc.common.ProductPrice;
import org.spin.backend.grpc.common.RunBusinessProcessRequest;
import org.spin.backend.grpc.pos.*;
import org.spin.backend.grpc.pos.StoreGrpc.StoreImplBase;
import org.spin.base.db.CountUtil;
import org.spin.base.db.LimitUtil;
import org.spin.base.util.ConvertUtil;
import org.spin.base.util.DocumentUtil;
import org.spin.base.util.FileUtil;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.convert.ConvertCommon;
import org.spin.pos.service.POSLogic;
import org.spin.pos.service.bank.BankManagement;
import org.spin.pos.service.cash.CashManagement;
import org.spin.pos.service.cash.CashUtil;
import org.spin.pos.service.cash.CollectingManagement;
import org.spin.pos.service.order.OrderManagement;
import org.spin.pos.service.order.OrderUtil;
import org.spin.pos.service.order.RMAUtil;
import org.spin.pos.service.order.ReturnSalesOrder;
import org.spin.pos.service.order.ReverseSalesTransaction;
import org.spin.pos.service.pos.POS;
import org.spin.pos.util.ColumnsAdded;
import org.spin.pos.util.POSConvertUtil;
import org.spin.pos.util.TicketHandler;
import org.spin.pos.util.TicketResult;
import org.spin.service.grpc.authentication.SessionManager;
import org.spin.service.grpc.util.ValueManager;
import org.spin.store.model.MCPaymentMethod;
import org.spin.store.util.VueStoreFrontUtil;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Value;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 * Service for backend of POS
 */
public class PointOfSalesForm extends StoreImplBase {
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(PointOfSalesForm.class);


	@Override
	public void createOrder(CreateOrderRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder order = createOrder(request);
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}


	@Override
	public void createOrderLine(CreateOrderLineRequest request, StreamObserver<OrderLine> responseObserver) {
		try {
			OrderLine.Builder orderLine = createAndConvertOrderLine(request);
			responseObserver.onNext(orderLine.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void deleteOrderLine(DeleteOrderLineRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder orderLine = deleteOrderLine(request);
			responseObserver.onNext(orderLine.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}


	@Override
	public void updateOrderLine(UpdateOrderLineRequest request, StreamObserver<OrderLine> responseObserver) {
		try {
			OrderLine.Builder orderLine = updateAndConvertOrderLine(request);
			responseObserver.onNext(orderLine.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listProductPrice(ListProductPriceRequest request, StreamObserver<ListProductPriceResponse> responseObserver) {
		try {
			ListProductPriceResponse.Builder productPriceList = getProductPriceList(request);
			responseObserver.onNext(productPriceList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void getOrder(GetOrderRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder order = ConvertUtil.convertOrder(getOrder(request.getId(), null));
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void createPayment(CreatePaymentRequest request, StreamObserver<Payment> responseObserver) {
		try {
			Payment.Builder payment = ConvertUtil.convertPayment(createPayment(request));
			responseObserver.onNext(payment.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void updatePayment(UpdatePaymentRequest request, StreamObserver<Payment> responseObserver) {
		try {
			Payment.Builder payment = ConvertUtil.convertPayment(updatePayment(request));
			responseObserver.onNext(payment.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void deletePayment(DeletePaymentRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder empty = deletePayment(request);
			responseObserver.onNext(empty.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listPayments(ListPaymentsRequest request, StreamObserver<ListPaymentsResponse> responseObserver) {
		try {
			ListPaymentsResponse.Builder paymentList = listPayments(request);
			responseObserver.onNext(paymentList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listOrders(ListOrdersRequest request, StreamObserver<ListOrdersResponse> responseObserver) {
		try {
			ListOrdersResponse.Builder ordersList = listOrders(request);
			responseObserver.onNext(ordersList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listOrderLines(ListOrderLinesRequest request, StreamObserver<ListOrderLinesResponse> responseObserver) {
		try {
			ListOrderLinesResponse.Builder orderLinesList = listOrderLines(request);
			responseObserver.onNext(orderLinesList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void getKeyLayout(GetKeyLayoutRequest request, StreamObserver<KeyLayout> responseObserver) {
		try {
			KeyLayout.Builder keyLayout = ConvertUtil.convertKeyLayout(request.getKeyLayoutId());
			responseObserver.onNext(keyLayout.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void updateOrder(UpdateOrderRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder order = ConvertUtil.convertOrder(updateOrder(request));
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void releaseOrder(ReleaseOrderRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder order = ConvertUtil.convertOrder(changeOrderAssigned(request.getId()));
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void holdOrder(HoldOrderRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder order = ConvertUtil.convertOrder(changeOrderAssigned(request.getId(), request.getSalesRepresentativeId()));
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void getAvailableRefund(GetAvailableRefundRequest request, StreamObserver<AvailableRefund> responseObserver) {
		try {
			AvailableRefund.Builder availableRefund = getAvailableRefund(request);
			responseObserver.onNext(availableRefund.build());
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
	 * Calculate Available refund from daily operations
	 * @param request
	 * @return
	 * @return AvailableRefund.Builder
	 */
	private AvailableRefund.Builder getAvailableRefund(GetAvailableRefundRequest request) {
		return AvailableRefund.newBuilder();
	}
	
	@Override
	public void processOrder(ProcessOrderRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder order = ConvertUtil.convertOrder(
				processOrder(request)
			);
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void validatePIN(ValidatePINRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder empty = validatePIN(request);
			responseObserver.onNext(empty.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listAvailableWarehouses(ListAvailableWarehousesRequest request,
			StreamObserver<ListAvailableWarehousesResponse> responseObserver) {
		try {
			ListAvailableWarehousesResponse.Builder warehouses = listWarehouses(request);
			responseObserver.onNext(warehouses.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listAvailablePriceList(ListAvailablePriceListRequest request,
			StreamObserver<ListAvailablePriceListResponse> responseObserver) {
		try {
			ListAvailablePriceListResponse.Builder priceList = listPriceList(request);
			responseObserver.onNext(priceList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listAvailablePaymentMethods(ListAvailablePaymentMethodsRequest request,
			StreamObserver<ListAvailablePaymentMethodsResponse> responseObserver) {
		try {
			ListAvailablePaymentMethodsResponse.Builder tenderTypes = listPaymentMethods(request);
			responseObserver.onNext(tenderTypes.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listAvailableDocumentTypes(ListAvailableDocumentTypesRequest request,
			StreamObserver<ListAvailableDocumentTypesResponse> responseObserver) {
		try {
			ListAvailableDocumentTypesResponse.Builder documentTypes = listDocumentTypes(request);
			responseObserver.onNext(documentTypes.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listAvailableCurrencies(ListAvailableCurrenciesRequest request,
			StreamObserver<ListAvailableCurrenciesResponse> responseObserver) {
		try {
			ListAvailableCurrenciesResponse.Builder currencies = listCurrencies(request);
			responseObserver.onNext(currencies.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void createCustomer(CreateCustomerRequest request, StreamObserver<Customer> responseObserver) {
		try {
			Customer.Builder customer = createCustomer(Env.getCtx(), request);
			responseObserver.onNext(customer.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void updateCustomer(UpdateCustomerRequest request, StreamObserver<Customer> responseObserver) {
		try {
			Customer.Builder customer = updateCustomer(request);
			responseObserver.onNext(customer.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	@Override
	public void listCustomers(ListCustomersRequest request, StreamObserver<ListCustomersResponse> responseObserver) {
		try {
			ListCustomersResponse.Builder customer = POSLogic.listCustomers(request);
			responseObserver.onNext(customer.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	@Override
	public void getCustomer(GetCustomerRequest request, StreamObserver<Customer> responseObserver) {
		try {
			Customer.Builder customer = POSLogic.getCustomer(request);
			responseObserver.onNext(customer.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}


	@Override
	public void printTicket(PrintTicketRequest request, StreamObserver<PrintTicketResponse> responseObserver) {
		try {
			if(request.getOrderId() <= 0) {
				log.warning("Sales Order Not Found");
				return;
			}
			log.fine("Print Ticket = " + request);
			//	Print based on handler
			TicketResult ticketResult = TicketHandler.getInstance()
					.withPosId(request.getPosId())
					.withTableName(I_C_Order.Table_Name)
					.withRecordId(request.getOrderId())
					.printTicket();
			//	Process response
			PrintTicketResponse.Builder builder = PrintTicketResponse.newBuilder();
			if(ticketResult != null) {
				builder
					.setIsError(ticketResult.isError())
					.setSummary(
						ValueManager.validateNull(
							ticketResult.getSummary()
						)
					)
				;
				File fileReport = ticketResult.getReportFile();
				if(fileReport != null
						&& fileReport.exists()) {
					String validFileName = FileUtil.getValidFileName(fileReport.getName());
					String fileType = FileUtil.getExtension(validFileName);
					if(Util.isEmpty(fileType)) {
						fileType = fileType.replaceAll(".", "");
					}
					ByteString resultFile = ByteString.empty();
					try {
						resultFile = ByteString.readFrom(new FileInputStream(fileReport));
					} catch (IOException e) {
						log.warning(e.getLocalizedMessage());
						builder
							.setSummary(
								ValueManager.validateNull(
									e.getLocalizedMessage()
								)
							)
							.setIsError(true);
					}
					builder
						.setFileName(
							ValueManager.validateNull(validFileName)
						)
						.setMimeType(
							ValueManager.validateNull(
								MimeType.getMimeType(validFileName)
							)
						)
						.setResultType(
							ValueManager.validateNull(fileType)
						)
						.setOutputStream(resultFile)
					;
				}
				//	Write map
				if(ticketResult.getResultValues() != null) {
					Map<String, Object> resultValues = ticketResult.getResultValues();
					builder.setResultValues(
						ValueManager.convertObjectMapToStruct(resultValues)
					);
				}
			}
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void printPreview(PrintPreviewRequest request, StreamObserver<PrintPreviewResponse> responseObserver) {
		try {
			if(request.getOrderId() <= 0) {
				log.warning("Sales Order Not Found");
				return;
			}
			log.fine("Print Ticket = " + request);
			MPOS pos = getPOSFromId(request.getPosId(), true);
			int userId = Env.getAD_User_ID(pos.getCtx());
			if (!getBooleanValueFromPOS(pos, userId, "IsAllowsPreviewDocument")) {
				throw new AdempiereException("@POS.PreviewDocumentNotAllowed@");
			}

			int orderId = request.getOrderId();
			MOrder order = new MOrder(Env.getCtx(), orderId, null);
			PrintPreviewResponse.Builder ticket = PrintPreviewResponse.newBuilder()
				.setResult("Ok");

			// run process request
			// Rpt C_Order
			int processId = 110;
			int recordId = order.getC_Order_ID();
			String tableName = I_C_Order.Table_Name;
			int invoiceId = order.getC_Invoice_ID();
			
			if (invoiceId > 0) {
				// Rpt C_Invoice
				processId = 116;
				tableName = I_C_Invoice.Table_Name;
				recordId = invoiceId;
			}
			String reportType = "pdf";
			if (!Util.isEmpty(request.getReportType(), true)) {
				reportType = request.getReportType();
			}
			RunBusinessProcessRequest.Builder processRequest = RunBusinessProcessRequest.newBuilder()
				.setId(processId)
				.setTableName(tableName)
				.setRecordId(recordId)
				.setReportType(reportType)
			;

			ProcessLog.Builder processLog = BusinessData.runBusinessProcess(processRequest.build());
			
			// preview document
			ticket.setProcessLog(processLog.build());

			responseObserver.onNext(ticket.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}
	
	@Override
	public void printShipmentPreview(PrintShipmentPreviewRequest request, StreamObserver<PrintShipmentPreviewResponse> responseObserver) {
		try {
			if(request.getShipmentId() <= 0) {
				log.warning("Shipment Not Found");
				return;
			}
			log.fine("Print Ticket = " + request);
			MPOS pos = getPOSFromId(request.getPosId(), true);
			int userId = Env.getAD_User_ID(pos.getCtx());
			if (!getBooleanValueFromPOS(pos, userId, "IsAllowsPreviewDocument")) {
				throw new AdempiereException("@POS.PreviewDocumentNotAllowed@");
			}

			int shipmentId = request.getShipmentId();
			
			PrintShipmentPreviewResponse.Builder ticket = PrintShipmentPreviewResponse.newBuilder()
				.setResult("Ok");
			
			// Rpt M_InOut
			int processId = 117;
			String reportType = "pdf";
			if (!Util.isEmpty(request.getReportType(), true)) {
				reportType = request.getReportType();
			}
			RunBusinessProcessRequest.Builder processRequest = RunBusinessProcessRequest.newBuilder()
				.setId(processId)
				.setTableName(I_M_InOut.Table_Name)
				.setRecordId(shipmentId)
				.setReportType(reportType)
			;

			ProcessLog.Builder processLog = BusinessData.runBusinessProcess(processRequest.build());
			
			// preview document
			ticket.setProcessLog(processLog.build());

			responseObserver.onNext(ticket.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	@Override
	public void createCustomerBankAccount(CreateCustomerBankAccountRequest request, StreamObserver<CustomerBankAccount> responseObserver) {
		try {
			if(request.getCustomerId() <= 0) {
				throw new AdempiereException("@C_BPartner_ID@ @IsMandatory@");
			}
			if(Util.isEmpty(request.getAccountNo())) {
				throw new AdempiereException("@AccountNo@ @IsMandatory@");
			}
			MBPartner businessPartner = MBPartner.get(Env.getCtx(), request.getCustomerId());
			int bankId = request.getBankId();
			if(bankId <= 0 && request.getIsAch()) {
				throw new AdempiereException("@C_Bank_ID@ @IsMandatory@");
			}
			String accountName = request.getName();
			if(Util.isEmpty(accountName)) {
				accountName = businessPartner.getName() + "_" + request.getAccountNo();
				if (bankId >= 0) {
					MBank bank = MBank.get(Env.getCtx(), bankId);
					if (bank != null) {
						accountName += " (" + bank.getRoutingNo() + ")";
					}
				}
			}
			int customerBankAccountId = getCustomerBankAccountFromAccount(Env.getCtx(), businessPartner.getC_BPartner_ID(), bankId, request.getAccountNo(), request.getSocialSecurityNumber());
			if(customerBankAccountId < 0) {
				customerBankAccountId = 0;
			}
			//	For data
			MBPBankAccount businessPartnerBankAccount = new MBPBankAccount(Env.getCtx(), customerBankAccountId, null);
			businessPartnerBankAccount.setC_BPartner_ID(businessPartner.getC_BPartner_ID());
			businessPartnerBankAccount.setIsACH(request.getIsAch());
			//	Validate all data
			Optional.ofNullable(request.getCity()).ifPresent(value -> businessPartnerBankAccount.setA_City(value));
			Optional.ofNullable(request.getCountry()).ifPresent(value -> businessPartnerBankAccount.setA_Country(value));
			Optional.ofNullable(request.getEmail()).ifPresent(value -> businessPartnerBankAccount.setA_EMail(value));
			Optional.ofNullable(request.getDriverLicense()).ifPresent(value -> businessPartnerBankAccount.setA_Ident_DL(value));
			Optional.ofNullable(request.getSocialSecurityNumber()).ifPresent(value -> businessPartnerBankAccount.setA_Ident_SSN(value));
			Optional.ofNullable(accountName).ifPresent(value -> businessPartnerBankAccount.setA_Name(value));
			Optional.ofNullable(request.getState()).ifPresent(value -> businessPartnerBankAccount.setA_State(value));
			Optional.ofNullable(request.getStreet()).ifPresent(value -> businessPartnerBankAccount.setA_Street(value));
			Optional.ofNullable(request.getZip()).ifPresent(value -> businessPartnerBankAccount.setA_Zip(value));
			Optional.ofNullable(request.getAccountNo()).ifPresent(value -> businessPartnerBankAccount.setAccountNo(value));
			if(bankId > 0) {
				businessPartnerBankAccount.setC_Bank_ID(bankId);
			}
			Optional.ofNullable(request.getAddressVerified()).ifPresent(value -> businessPartnerBankAccount.setR_AvsAddr(value));
			Optional.ofNullable(request.getZipVerified()).ifPresent(value -> businessPartnerBankAccount.setR_AvsZip(value));
			Optional.ofNullable(request.getRoutingNo()).ifPresent(value -> businessPartnerBankAccount.setRoutingNo(value));
			Optional.ofNullable(request.getIban()).ifPresent(value -> businessPartnerBankAccount.setIBAN(value));
			//	Bank Account Type
			if(Util.isEmpty(request.getBankAccountType())) {
				businessPartnerBankAccount.setBankAccountType(MBPBankAccount.BANKACCOUNTTYPE_Savings);
			} else {
				businessPartnerBankAccount.setBankAccountType(request.getBankAccountType());
			}
			businessPartnerBankAccount.saveEx();
			responseObserver.onNext(ConvertUtil.convertCustomerBankAccount(businessPartnerBankAccount).build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	private int getCustomerBankAccountFromAccount(Properties context, int customerId, int bankId, String accountNo, String businessPartnerCode) {
		return new Query(context, I_C_BP_BankAccount.Table_Name, "C_BPArtner_ID = ? AND C_Bank_ID = ? AND AccountNo = ? AND A_Ident_SSN = ?", null)
				.setParameters(customerId, bankId, accountNo, businessPartnerCode)
				.setOnlyActiveRecords(true)
				.firstId();
	}
	
	@Override
	public void updateCustomerBankAccount(UpdateCustomerBankAccountRequest request, StreamObserver<CustomerBankAccount> responseObserver) {
		try {
			if(request.getCustomerBankAccountId() <= 0) {
				throw new AdempiereException("@C_BPBankAccount_ID@ @IsMandatory@");
			}
			//	For data
			MBPBankAccount businessPartnerBankAccount = new MBPBankAccount(Env.getCtx(), request.getCustomerBankAccountId(), null);
			businessPartnerBankAccount.setIsACH(request.getIsAch());
			//	Validate all data
			Optional.ofNullable(request.getCity()).ifPresent(value -> businessPartnerBankAccount.setA_City(value));
			Optional.ofNullable(request.getCountry()).ifPresent(value -> businessPartnerBankAccount.setA_Country(value));
			Optional.ofNullable(request.getEmail()).ifPresent(value -> businessPartnerBankAccount.setA_EMail(value));
			Optional.ofNullable(request.getDriverLicense()).ifPresent(value -> businessPartnerBankAccount.setA_Ident_DL(value));
			Optional.ofNullable(request.getSocialSecurityNumber()).ifPresent(value -> businessPartnerBankAccount.setA_Ident_SSN(value));
			Optional.ofNullable(request.getName()).ifPresent(value -> businessPartnerBankAccount.setA_Name(value));
			Optional.ofNullable(request.getState()).ifPresent(value -> businessPartnerBankAccount.setA_State(value));
			Optional.ofNullable(request.getStreet()).ifPresent(value -> businessPartnerBankAccount.setA_Street(value));
			Optional.ofNullable(request.getZip()).ifPresent(value -> businessPartnerBankAccount.setA_Zip(value));
			Optional.ofNullable(request.getAccountNo()).ifPresent(value -> businessPartnerBankAccount.setAccountNo(value));
			if(request.getBankId() > 0) {
				businessPartnerBankAccount.setC_Bank_ID(request.getBankId());
			}
			Optional.ofNullable(request.getAddressVerified()).ifPresent(value -> businessPartnerBankAccount.setR_AvsAddr(value));
			Optional.ofNullable(request.getZipVerified()).ifPresent(value -> businessPartnerBankAccount.setR_AvsZip(value));
			Optional.ofNullable(request.getRoutingNo()).ifPresent(value -> businessPartnerBankAccount.setRoutingNo(value));
			Optional.ofNullable(request.getIban()).ifPresent(value -> businessPartnerBankAccount.setIBAN(value));
			//	Bank Account Type
			if(Util.isEmpty(request.getBankAccountType())) {
				businessPartnerBankAccount.setBankAccountType(MBPBankAccount.BANKACCOUNTTYPE_Savings);
			} else {
				businessPartnerBankAccount.setBankAccountType(request.getBankAccountType());
			}
			businessPartnerBankAccount.saveEx();
			responseObserver.onNext(ConvertUtil.convertCustomerBankAccount(businessPartnerBankAccount).build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void getCustomerBankAccount(GetCustomerBankAccountRequest request, StreamObserver<CustomerBankAccount> responseObserver) {
		try {
			if(request.getId() <= 0) {
				throw new AdempiereException("@C_BP_BankAccount_ID@ @IsMandatory@");
			}
			//	For data
			MBPBankAccount businessPartnerBankAccount = new MBPBankAccount(Env.getCtx(), request.getId(), null);
			responseObserver.onNext(ConvertUtil.convertCustomerBankAccount(businessPartnerBankAccount).build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void deleteCustomerBankAccount(DeleteCustomerBankAccountRequest request, StreamObserver<Empty> responseObserver) {
		try {
			if(request.getCustomerBankAccountId() <= 0) {
				throw new AdempiereException("@C_BP_BankAccount_ID@ @IsMandatory@");
			}
			//	For data
			MBPBankAccount businessPartnerBankAccount = new MBPBankAccount(Env.getCtx(), request.getCustomerBankAccountId(), null);
			businessPartnerBankAccount.deleteEx(true);
			responseObserver.onNext(Empty.newBuilder().build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}


	@Override
	public void listBanks(ListBanksRequest request, StreamObserver<ListBanksResponse> responseObserver) {
		try {
			ListBanksResponse.Builder builder = BankManagement.listBanks(request);
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException()
			);
		}
	}


	@Override
	public void listBankAccounts(ListBankAccountsRequest request, StreamObserver<ListBankAccountsResponse> responseObserver) {
		try {
			ListBankAccountsResponse.Builder bankAccountsBuilderList = BankManagement.listBankAccounts(request);
			responseObserver.onNext(bankAccountsBuilderList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException()
			);
		}
	}


	@Override
	public void listCustomerBankAccounts(ListCustomerBankAccountsRequest request, StreamObserver<ListCustomerBankAccountsResponse> responseObserver) {
		try {
			if(request.getCustomerId() <= 0) {
				throw new AdempiereException("@C_BPartner_ID@ @IsMandatory@");
			}
			//	For data
			ListCustomerBankAccountsResponse.Builder builder = ListCustomerBankAccountsResponse.newBuilder();
			int customerId = request.getCustomerId();
			String nexPageToken = null;
			int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
			int limit = LimitUtil.getPageSize(request.getPageSize());
			int offset = (pageNumber - 1) * limit;

			//	Dynamic where clause
			//	Get Product list
			Query query = new Query(Env.getCtx(), I_C_BP_BankAccount.Table_Name, I_C_BP_BankAccount.COLUMNNAME_C_BPartner_ID + " = ?", null)
					.setParameters(customerId)
					.setClient_ID()
					.setOnlyActiveRecords(true);
			int count = query.count();
			query
			.setLimit(limit, offset)
			.<MBPBankAccount>list()
			.forEach(customerBankAccount -> {
				builder.addCustomerBankAccounts(ConvertUtil.convertCustomerBankAccount(customerBankAccount));
			});
			//	
			builder.setRecordCount(count);
			//	Set page token
			if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
				nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
			}
			builder.setNextPageToken(
				ValueManager.validateNull(nexPageToken)
			);
			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void createShipment(CreateShipmentRequest request, StreamObserver<Shipment> responseObserver) {
		try {
			Shipment.Builder shipment = createShipment(request);
			responseObserver.onNext(shipment.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void createShipmentLine(CreateShipmentLineRequest request, StreamObserver<ShipmentLine> responseObserver) {
		try {
			ShipmentLine.Builder shipmentLine = createAndConvertShipmentLine(request);
			responseObserver.onNext(shipmentLine.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}
	
	@Override
	public void deleteShipmentLine(DeleteShipmentLineRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder nothing = deleteShipmentLine(request);
			responseObserver.onNext(nothing.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listShipmentLines(ListShipmentLinesRequest request, StreamObserver<ListShipmentLinesResponse> responseObserver) {
		try {
			ListShipmentLinesResponse.Builder shipmentLinesList = listShipmentLines(request);
			responseObserver.onNext(shipmentLinesList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void processShipment(ProcessShipmentRequest request, StreamObserver<Shipment> responseObserver) {
		try {
			Shipment.Builder shipment = processShipment(request);
			responseObserver.onNext(shipment.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void reverseSales(ReverseSalesRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder order = reverseSalesTransaction(request);
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}
	
	@Override
	public void processCashOpening(CashOpeningRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder empty = cashOpening(request);
			responseObserver.onNext(empty.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void processCashWithdrawal(CashWithdrawalRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder empty = cashWithdrawal(request);
			responseObserver.onNext(empty.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void processCashClosing(CashClosingRequest request, StreamObserver<CashClosing> responseObserver) {
		try {
			CashClosing.Builder closing = cashClosing(request);
			responseObserver.onNext(closing.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listCashMovements(ListCashMovementsRequest request, StreamObserver<ListCashMovementsResponse> responseObserver) {
		try {
			ListCashMovementsResponse.Builder response = listCashMovements(request);
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listCashSummaryMovements(ListCashSummaryMovementsRequest request, StreamObserver<ListCashSummaryMovementsResponse> responseObserver) {
		try {
			ListCashSummaryMovementsResponse.Builder response = listCashSummaryMovements(request);
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}
	
	@Override
	public void allocateSeller(AllocateSellerRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder empty = allocateSeller(request);
			responseObserver.onNext(empty.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void createPaymentReference(CreatePaymentReferenceRequest request, StreamObserver<PaymentReference> responseObserver) {
		try {
			PaymentReference.Builder refund = createPaymentReference(request);
			responseObserver.onNext(refund.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void deletePaymentReference(DeletePaymentReferenceRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder orderLine = deletePaymentReference(request);
			responseObserver.onNext(orderLine.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listPaymentReferences(ListPaymentReferencesRequest request, StreamObserver<ListPaymentReferencesResponse> responseObserver) {
		try {
			ListPaymentReferencesResponse.Builder refundReferenceList = listPaymentReferencesLines(request);
			responseObserver.onNext(refundReferenceList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void deallocateSeller(DeallocateSellerRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder empty = deallocateSeller(request);
			responseObserver.onNext(empty.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listAvailableSellers(ListAvailableSellersRequest request, StreamObserver<ListAvailableSellersResponse> responseObserver) {
		try {
			ListAvailableSellersResponse.Builder response = listAvailableSellers(request);
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void createRMA(CreateRMARequest request, StreamObserver<RMA> responseObserver) {
		try {
			MOrder rma = ReturnSalesOrder.createRMAFromOrder(request.getPosId(), request.getSourceOrderId(), request.getSalesRepresentativeId(), request.getIsCreateLinesFromOrder(), null);
			RMA.Builder returnOrder = ConvertUtil.convertRMA(rma);
			responseObserver.onNext(returnOrder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void createRMALine(CreateRMALineRequest request, StreamObserver<RMALine> responseObserver) {
		try {
			MOrderLine rmaLine = ReturnSalesOrder.createRMALineFromOrder(
				request.getRmaId(),
				request.getSourceOrderLineId(),
				ValueManager.getBigDecimalFromValue(
					request.getQuantity()
				),
				request.getDescription()
			);
			RMALine.Builder returnLine = ConvertUtil.convertRMALine(rmaLine);
			responseObserver.onNext(returnLine.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}
	
	@Override
	public void updateRMALine(UpdateRMALineRequest request, StreamObserver<RMALine> responseObserver) {
		try {
			MOrderLine rmaLine = ReturnSalesOrder.updateRMALine(
				request.getId(),
				ValueManager.getBigDecimalFromValue(
					request.getQuantity()
				),
				request.getDescription()
			);
			RMALine.Builder returnLine = ConvertUtil.convertRMALine(rmaLine);
			responseObserver.onNext(returnLine.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}
	
	@Override
	public void deleteRMALine(DeleteRMALineRequest request, StreamObserver<Empty> responseObserver) {
		try {
			RMAUtil.deleteRMALine(request.getId());
			responseObserver.onNext(Empty.newBuilder().build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	@Override
	public void deleteRMA(DeleteRMARequest request, StreamObserver<Empty> responseObserver) {
		try {
			RMAUtil.deleteRMA(request.getId());
			responseObserver.onNext(Empty.newBuilder().build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listRMALines(ListRMALinesRequest request, StreamObserver<ListRMALinesResponse> responseObserver) {
		try {
			ListRMALinesResponse.Builder rmaLinesList = listRMALines(request);
			responseObserver.onNext(rmaLinesList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	@Override
	public void processRMA(ProcessRMARequest request, StreamObserver<RMA> responseObserver) {
		try {
			RMA.Builder rma = ConvertUtil.convertRMA(ReturnSalesOrder.processRMA(request.getRmaId(), request.getPosId(), request.getDocumentAction(), request.getDescription()));
			responseObserver.onNext(rma.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	@Override
	public void copyOrder(CopyOrderRequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder salesOrder = ConvertUtil.convertOrder(
				OrderManagement.createOrderFromOther(
					request.getPosId(),
					request.getSalesRepresentativeId(),
					request.getSourceOrderId()
				)
			);
			responseObserver.onNext(salesOrder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	@Override
	public void createOrderFromRMA(CreateOrderFromRMARequest request, StreamObserver<Order> responseObserver) {
		try {
			Order.Builder salesOrder = ConvertUtil.convertOrder(OrderManagement.createOrderFromRMA(request.getPosId(), request.getSalesRepresentativeId(), request.getSourceRmaId()));
			responseObserver.onNext(salesOrder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	@Override
	public void listCustomerCredits(ListCustomerCreditsRequest request, StreamObserver<ListCustomerCreditsResponse> responseObserver) {
		try {
			ListCustomerCreditsResponse.Builder response = listCustomerCredits(request);
			responseObserver.onNext(response.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	/**
	 * List shipment Lines from Order UUID
	 * @param request
	 * @return
	 */
	private ListCustomerCreditsResponse.Builder listCustomerCredits(ListCustomerCreditsRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		if(request.getCustomerId() <= 0) {
			throw new AdempiereException("@C_BPartner_ID@ @NotFound@");
		}
		ListCustomerCreditsResponse.Builder builder = ListCustomerCreditsResponse.newBuilder();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//
		MPOS pos = new MPOS(Env.getCtx(), request.getPosId(), null);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		int count = 0;
		try {
			//	Get Bank statement
			StringBuffer sql = new StringBuffer("SELECT i.C_Invoice_ID, i.DocumentNo, i.Description, i.DateInvoiced, i.C_Currency_ID, i.GrandTotal, (InvoiceOpen(i.C_Invoice_ID, null) * -1) AS OpenAmount ")
					.append("FROM C_Invoice i ")
					.append("WHERE i.IsSOTrx = 'Y' ")
					.append("AND i.DocStatus IN('CO', 'CL') ")
					.append("AND i.IsPaid = 'N' ")
					.append("AND EXISTS(SELECT 1 FROM C_DocType dt WHERE dt.C_DocType_ID = i.C_DocType_ID AND dt.DocBaseType = 'ARC') ")
					.append("AND i.C_BPartner_ID = ? ")
					.append("AND i.AD_Org_ID = ? ")
					.append("AND InvoiceOpen(i.C_Invoice_ID, null) <> 0");
			StringBuffer whereClause = new StringBuffer();
			List<Object> parameters = new ArrayList<Object>();
			//	Count records
			parameters.add(request.getCustomerId());
			parameters.add(pos.getAD_Org_ID());
			if(!Util.isEmpty(request.getSearchValue())) {
				whereClause.append(" AND UPPER(i.DocumentNo) LIKE '%' || UPPER(?) || '%'");
				//	Add parameters
				parameters.add(request.getSearchValue());
			}
			if(request.getDocumentTypeId() > 0) {
				sql.append(" AND i.C_DocType_ID = ?");
				parameters.add(request.getDocumentTypeId());
			}
			sql.append(whereClause);
			sql.append(" ORDER BY i.DateInvoiced DESC");
			count = CountUtil.countRecords(sql.toString(), "C_Invoice i", parameters);
			pstmt = DB.prepareStatement(sql.toString(), null);
			int index = 1;
			pstmt.setInt(index++, request.getCustomerId());
			pstmt.setInt(index++, pos.getAD_Org_ID());
			if(whereClause.length() > 0) {
				pstmt.setString(index++, request.getSearchValue());
			}
			if(request.getDocumentTypeId() > 0) {
				pstmt.setInt(index++, request.getDocumentTypeId());
			}
			//	Get from Query
			rs = pstmt.executeQuery();
			while(rs.next()) {
				CreditMemo.Builder creditMemo = CreditMemo.newBuilder()
					.setId(rs.getInt("C_Invoice_ID"))
					.setDocumentNo(
						ValueManager.validateNull(
							rs.getString("DocumentNo")
						)
					)
					.setDescription(
						ValueManager.validateNull(
							rs.getString("Description")
						)
					)
					.setDocumentDate(
						ValueManager.getTimestampFromDate(
							rs.getTimestamp("DateInvoiced")
						)
					)
					.setCurrency(
						ConvertCommon.convertCurrency(
							rs.getInt("C_Currency_ID")
						)
					)
					.setAmount(
						ValueManager.getValueFromBigDecimal(
							rs.getBigDecimal("GrandTotal")
						)
					)
					.setOpenAmount(
						ValueManager.getValueFromBigDecimal(
							rs.getBigDecimal("OpenAmount")
						)
					)
				;
				//	
				builder.addRecords(creditMemo.build());
				count++;
			}
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			throw new AdempiereException(e);
		} finally {
			DB.close(rs, pstmt);
		}
		//
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}

	/**
	 * List shipment Lines from Order UUID
	 * @param request
	 * @return
	 */
	private ListAvailableSellersResponse.Builder listAvailableSellers(ListAvailableSellersRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		ListAvailableSellersResponse.Builder builder = ListAvailableSellersResponse.newBuilder();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		int posId = request.getPosId();
		//	
		StringBuffer whereClause = new StringBuffer();
		List<Object> parameters = new ArrayList<Object>();
		parameters.add(posId);
		if(request.getIsOnlyAllocated()) {
			whereClause.append("EXISTS(SELECT 1 FROM C_POSSellerAllocation s WHERE s.C_POS_ID = ? AND s.SalesRep_ID = AD_User.AD_User_ID AND s.IsActive = 'Y')");
		} else {
			whereClause.append("EXISTS(SELECT 1 FROM C_POSSellerAllocation s WHERE s.C_POS_ID <> ? AND s.SalesRep_ID = AD_User.AD_User_ID OR s.IsActive = 'N')");
		}
		Query query = new Query(Env.getCtx(), I_AD_User.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.<MUser>list()
		.forEach(seller -> {
			builder.addSellers(ConvertUtil.convertSeller(seller));
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * Allocate a seller to point of sales
	 * @param request
	 * @return
	 */
	private Empty.Builder deallocateSeller(DeallocateSellerRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @IsMandatory@");
		}
		if(request.getSalesRepresentativeId() <= 0) {
			throw new AdempiereException("@SalesRep_ID@ @IsMandatory@");
		}
		int posId = request.getPosId();
		int salesRepresentativeId = request.getSalesRepresentativeId();
		MPOS pointOfSales = new MPOS(Env.getCtx(), posId, null);
		if(!pointOfSales.get_ValueAsBoolean("IsAllowsAllocateSeller")) {
			throw new AdempiereException("@POS.AllocateSellerNotAllowed@");
		}
		Trx.run(transactionName -> {
			PO seller = new Query(Env.getCtx(), "C_POSSellerAllocation", "C_POS_ID = ? AND SalesRep_ID = ?", transactionName).setParameters(posId, salesRepresentativeId).first();
			if(seller == null
					|| seller.get_ID() <= 0) {
				throw new AdempiereException("@SalesRep_ID@ @NotFound@");
			}
			seller.set_ValueOfColumn("IsActive", false);
			seller.saveEx(transactionName);
		});
		//	Return
		return Empty.newBuilder();
	}
	
	/**
	 * Allocate a seller to point of sales
	 * @param request
	 * @return
	 */
	private Empty.Builder allocateSeller(AllocateSellerRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @IsMandatory@");
		}
		if(request.getSalesRepresentativeId() <= 0) {
			throw new AdempiereException("@SalesRep_ID@ @IsMandatory@");
		}
		int posId = request.getPosId();
		int salesRepresentativeId = request.getSalesRepresentativeId();
		MPOS pointOfSales = new MPOS(Env.getCtx(), posId, null);
		if(!pointOfSales.get_ValueAsBoolean("IsAllowsAllocateSeller")) {
			throw new AdempiereException("@POS.AllocateSellerNotAllowed@");
		}
		Trx.run(transactionName -> {
			List<Integer> allocatedSellersIds = new Query(Env.getCtx(), "C_POSSellerAllocation", "C_POS_ID = ?", transactionName).setParameters(posId).getIDsAsList();
			if(!pointOfSales.get_ValueAsBoolean("IsAllowsConcurrentUse")) {
				allocatedSellersIds
				.forEach(allocatedSellerId -> {
					PO allocatedSeller = new GenericPO("C_POSSellerAllocation", Env.getCtx(), allocatedSellerId, transactionName);
					if(allocatedSeller.get_ValueAsInt("SalesRep_ID") != salesRepresentativeId) {
						allocatedSeller.set_ValueOfColumn("IsActive", false);
						allocatedSeller.saveEx(transactionName);
					}
				});
			}
			//	For add seller
			PO seller = new Query(Env.getCtx(), "C_POSSellerAllocation", "C_POS_ID = ? AND SalesRep_ID = ?", transactionName).setParameters(posId, salesRepresentativeId).first();
			if(seller == null
					|| seller.get_ID() <= 0) {
				seller = new GenericPO("C_POSSellerAllocation", Env.getCtx(), 0, transactionName);
				seller.set_ValueOfColumn("C_POS_ID", posId);
				seller.set_ValueOfColumn("SalesRep_ID", salesRepresentativeId);
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsModifyQuantity, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsModifyQuantity));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsReturnOrder, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsReturnOrder));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsCollectOrder, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsCollectOrder));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsCreateOrder, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsCreateOrder));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsDisplayTaxAmount, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsDisplayTaxAmount));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsDisplayDiscount, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsDisplayDiscount));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsConfirmShipment, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsConfirmShipment));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsConfirmCompleteShipment, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsConfirmCompleteShipment));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsAllocateSeller, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsAllocateSeller));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsConcurrentUse, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsConcurrentUse));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsCashOpening, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsCashOpening));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsCashClosing, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsCashClosing));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsCashWithdrawal, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsCashWithdrawal));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsApplyDiscount, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsApplyDiscount));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_MaximumRefundAllowed, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_MaximumRefundAllowed));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_MaximumDailyRefundAllowed, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_MaximumDailyRefundAllowed));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_MaximumDiscountAllowed, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_MaximumDiscountAllowed));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_WriteOffAmtTolerance, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_WriteOffAmtTolerance));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsCreateCustomer, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsCreateCustomer));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsPrintDocument, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsPrintDocument));
				seller.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_IsAllowsPreviewDocument, pointOfSales.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsAllowsPreviewDocument));
			}
			seller.set_ValueOfColumn("IsActive", true);
			seller.saveEx(transactionName);
		});
		//	Return
		return Empty.newBuilder();
	}
	
	/**
	 * List all movements from cash
	 * @return
	 */
	private ListCashMovementsResponse.Builder listCashMovements(ListCashMovementsRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @IsMandatory@");
		}
		MPOS pos = getPOSFromId(request.getPosId(), true);
		MBankStatement cashClosing = CashManagement.getOpenCashClosing(pos, RecordUtil.getDate(), true, null);
		if(cashClosing == null
				|| cashClosing.getC_BankStatement_ID() <= 0) {
			throw new AdempiereException("@C_BankStatement_ID@ @NotFound@");
		}

		ListCashMovementsResponse.Builder builder = ListCashMovementsResponse.newBuilder()
			.setId(cashClosing.getC_BankStatement_ID())
		;

		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		List<Object> parameters = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer("DocStatus IN('CO', 'CL') "
				+ "AND C_POS_ID = ? "
				+ "AND EXISTS(SELECT 1 FROM C_BankStatementLine bsl WHERE bsl.C_Payment_ID = C_Payment.C_Payment_ID AND bsl.C_BankStatement_ID = ?)");
		parameters.add(pos.getC_POS_ID());
		parameters.add(cashClosing.getC_BankStatement_ID());
		//	Optional
		if(request.getBusinessPartnerId() > 0) {
			parameters.add(request.getBusinessPartnerId());
			whereClause.append(" AND C_BPartner_ID = ?");
		} else if(request.getSalesRepresentativeId() > 0) {
			parameters.add(request.getSalesRepresentativeId());
			whereClause.append(" AND CollectingAgent_ID = ?");
		}
		//	Get Refund Reference list
		Query query = new Query(Env.getCtx(), I_C_Payment.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.getIDsAsList()
		.forEach(paymentId -> {
			MPayment payment = new MPayment(pos.getCtx(), paymentId, null);
			builder.addCashMovements(ConvertUtil.convertPayment(payment));
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		//	Return
		return builder;
	}
	
	/**
	 * List all movements from cash
	 * @return
	 */
	private ListCashSummaryMovementsResponse.Builder listCashSummaryMovements(ListCashSummaryMovementsRequest request) {
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		ListCashSummaryMovementsResponse.Builder builder = ListCashSummaryMovementsResponse.newBuilder();
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @IsMandatory@");
		}
		MPOS pos = getPOSFromId(request.getPosId(), true);
		MBankStatement cashClosing = CashManagement.getOpenCashClosing(pos, RecordUtil.getDate(), true, null);
		if(cashClosing == null
				|| cashClosing.getC_BankStatement_ID() <= 0) {
			throw new AdempiereException("@C_BankStatement_ID@ @NotFound@");
		}
		builder
			.setId(cashClosing.getC_BankStatement_ID());
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		int count = 0;
		try {
			//	Get Bank statement
			String sql = "SELECT pm.C_PaymentMethod_ID, pm.Name AS PaymentMethodName, pm.TenderType AS TenderTypeCode, p.C_Currency_ID, p.IsReceipt, (SUM(p.PayAmt) * CASE WHEN p.IsReceipt = 'Y' THEN 1 ELSE -1 END) AS PaymentAmount "
					+ "FROM C_Payment p "
					+ "INNER JOIN C_PaymentMethod pm ON(pm.C_PaymentMethod_ID = p.C_PaymentMethod_ID) "
					+ "WHERE p.DocStatus IN('CO', 'CL') "
					+ "AND p.C_POS_ID = ? "
					+ "AND EXISTS(SELECT 1 FROM C_BankStatementLine bsl WHERE bsl.C_Payment_ID = p.C_Payment_ID AND bsl.C_BankStatement_ID = ?) "
					+ "GROUP BY pm.UUID, pm.Name, pm.TenderType, p.C_Currency_ID, p.IsReceipt";
			//	Count records
			List<Object> parameters = new ArrayList<Object>();
			parameters.add(pos.getC_POS_ID());
			parameters.add(cashClosing.getC_BankStatement_ID());
			count = CountUtil.countRecords(sql, "C_Payment p", parameters);
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, pos.getC_POS_ID());
			pstmt.setInt(2, cashClosing.getC_BankStatement_ID());
			//	Get from Query
			rs = pstmt.executeQuery();
			while(rs.next()) {
				PaymentSummary.Builder paymentSummary = PaymentSummary.newBuilder()
					.setPaymentMethodId(rs.getInt("C_PaymentMethod_ID"))
					.setPaymentMethodName(
						ValueManager.validateNull(
							rs.getString("PaymentMethodName")
						)
					)
					.setTenderTypeCode(
						ValueManager.validateNull(
							rs.getString("TenderTypeCode")
						)
					)
					.setCurrency(
						ConvertCommon.convertCurrency(
							rs.getInt("C_Currency_ID")
						)
					)
					.setIsRefund(rs.getString("IsReceipt").equals("Y")? false: true)
					.setAmount(
						ValueManager.getValueFromBigDecimal(
							rs.getBigDecimal("PaymentAmount")
						)
					)
				;
				//	
				builder.addCashMovements(paymentSummary.build());
				count++;
			}
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			throw new AdempiereException(e);
		} finally {
			DB.close(rs, pstmt);
		}
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		//	Return
		return builder;
	}
	
	/**
	 * Create Refund Reference
	 * @param request
	 * @return
	 */
	private PaymentReference.Builder createPaymentReference(CreatePaymentReferenceRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @IsMandatory@");
		}
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_Order_ID@ @IsMandatory@");
		}
		if(request.getSalesRepresentativeId() <= 0) {
			throw new AdempiereException("@SalesRep_ID@ @IsMandatory@");
		}
		if(request.getCurrencyId() <= 0) {
			throw new AdempiereException("@C_Currency_ID@ @IsMandatory@");
		}
		if(request.getCustomerId() <= 0) {
			throw new AdempiereException("@C_BPartner_ID@ @IsMandatory@");
		}
		if(request.getAmount() == null) {
			throw new AdempiereException("@Amount@ @IsMandatory@");
		}
		AtomicReference<PO> refundReference = new AtomicReference<PO>();
		Trx.run(transactionName -> {
			GenericPO refundReferenceToCreate = new GenericPO("C_POSPaymentReference", Env.getCtx(), 0, transactionName);
			refundReferenceToCreate.set_ValueOfColumn(
				"Amount",
				ValueManager.getBigDecimalFromValue(
					request.getAmount()
				)
			);
			refundReferenceToCreate.set_ValueOfColumn(
				"AmtSource",
				ValueManager.getBigDecimalFromValue(
					request.getSourceAmount()
				)
			);
			if(request.getCustomerBankAccountId() > 0) {
				refundReferenceToCreate.set_ValueOfColumn("C_BP_BankAccount_ID", request.getCustomerBankAccountId());
			}
			refundReferenceToCreate.set_ValueOfColumn("C_BPartner_ID", request.getCustomerId());
			int currencyId = request.getCurrencyId();
			if(currencyId > 0) {
				refundReferenceToCreate.set_ValueOfColumn("C_Currency_ID", currencyId);
			}
			MPOS pos = getPOSFromId(request.getPosId(), true);
			if(pos.get_ValueAsInt(I_C_ConversionType.COLUMNNAME_C_ConversionType_ID) > 0) {
				refundReferenceToCreate.set_ValueOfColumn("C_ConversionType_ID", pos.get_ValueAsInt(I_C_ConversionType.COLUMNNAME_C_ConversionType_ID));
			}
			MOrder salesOrder = getOrder(request.getOrderId(), transactionName);
			//	Throw if not exist conversion
			ConvertUtil.validateConversion(salesOrder, currencyId, pos.get_ValueAsInt(I_C_ConversionType.COLUMNNAME_C_ConversionType_ID), RecordUtil.getDate());
			refundReferenceToCreate.set_ValueOfColumn("C_Order_ID", salesOrder.getC_Order_ID());
			int id = request.getPaymentMethodId();
			if(id > 0) {
				refundReferenceToCreate.set_ValueOfColumn("C_PaymentMethod_ID", id);
			}
			if(pos.getC_POS_ID() > 0) {
				refundReferenceToCreate.set_ValueOfColumn("C_POS_ID", pos.getC_POS_ID());
			}
			id = request.getSalesRepresentativeId();
			if(id > 0) {
				refundReferenceToCreate.set_ValueOfColumn("SalesRep_ID", id);
			}
			refundReferenceToCreate.set_ValueOfColumn("IsReceipt", request.getIsReceipt());
			refundReferenceToCreate.set_ValueOfColumn("TenderType", request.getTenderTypeCode());
			refundReferenceToCreate.set_ValueOfColumn("Description", request.getDescription());
			refundReferenceToCreate.setAD_Org_ID(salesOrder.getAD_Org_ID());
			refundReferenceToCreate.saveEx(transactionName);
			refundReference.set(refundReferenceToCreate);
		});
		return convertPaymentReference(refundReference.get());
	}
	
	/**
	 * Cash Opening
	 * @param request
	 * @return
	 */
	private Empty.Builder cashOpening(CashOpeningRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		if(request.getCollectingAgentId() <= 0) {
			throw new AdempiereException("@CollectingAgent_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			MPOS pos = getPOSFromId(request.getPosId(), true);
			if(request.getPaymentsList().size() == 0) {
				throw new AdempiereException("@C_Payment_ID@ @NotFound@");
			}
			//	
			if(pos.getC_BankAccount_ID() <= 0) {
				throw new AdempiereException("@C_BankAccount_ID@ @NotFound@");
			}
			MBankAccount cashAccount = MBankAccount.get(Env.getCtx(), pos.getC_BankAccount_ID());
			if(cashAccount.get_ValueAsInt("DefaultOpeningCharge_ID") <= 0) {
				throw new AdempiereException("@DefaultOpeningCharge_ID@ @NotFound@");	
			}
			request.getPaymentsList().forEach(paymentRequest -> {
				MPayment payment = CashManagement.createPaymentFromCharge(cashAccount.get_ValueAsInt("DefaultOpeningCharge_ID"), paymentRequest, pos, transactionName);
				//	Add bank statement
				CashManagement.createCashClosing(pos, payment);
				//	
				CashManagement.processPayment(pos, payment, transactionName);
			});
		});
		return Empty.newBuilder();
	}
	
	/**
	 * Closing
	 * @param request
	 * @return
	 */
	private CashClosing.Builder cashClosing(CashClosingRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		CashClosing.Builder cashClosing = CashClosing.newBuilder();
		Trx.run(transactionName -> {
			int bankStatementId = request.getId();
			if(bankStatementId <= 0
					&& request.getId() <= 0) {
				throw new AdempiereException("@C_BankStatement_ID@ @NotFound@");
			}
			MBankStatement bankStatement = new MBankStatement(Env.getCtx(), bankStatementId, transactionName);
			if(bankStatement.isProcessed()) {
				throw new AdempiereException("@C_BankStatement_ID@ @Processed@");
			}
			if(!Util.isEmpty(request.getDescription())) {
				bankStatement.addDescription(request.getDescription());
			}
			bankStatement.setDocStatus(MBankStatement.DOCSTATUS_Drafted);
			bankStatement.setDocAction(MBankStatement.ACTION_Complete);
			bankStatement.saveEx(transactionName);
			if(!bankStatement.processIt(DocAction.ACTION_Complete)) {
				throw new AdempiereException(bankStatement.getProcessMsg());
			}
			bankStatement.saveEx(transactionName);
	        //	Set
	        cashClosing
				.setId(bankStatement.getC_BankStatement_ID())
				.setDocumentNo(
					ValueManager.validateNull(
						bankStatement.getDocumentNo()
					)
				)
				.setDescription(
					ValueManager.validateNull(
						bankStatement.getDescription()
					)
				)
				.setDocumentStatus(
					ConvertUtil.convertDocumentStatus(
						bankStatement.getDocStatus(),
						bankStatement.getDocStatus(),
						bankStatement.getDocStatus()
					)
				)
				.setDocumentType(
					ConvertUtil.convertDocumentType(
						MDocType.get(Env.getCtx(), bankStatement.getC_DocType_ID())
					)
				)
			;
		});
		return cashClosing;
	}
	
	/**
	 * Withdrawal
	 * @param request
	 * @return
	 */
	private Empty.Builder cashWithdrawal(CashWithdrawalRequest request) {
		MPOS pos = getPOSFromId(request.getPosId(), true);
		if(request.getCollectingAgentId() <= 0) {
			throw new AdempiereException("@CollectingAgent_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			if(request.getPaymentsList().size() == 0) {
				throw new AdempiereException("@C_Payment_ID@ @NotFound@");
			}
			//	
			if(pos.getC_BankAccount_ID() <= 0) {
				throw new AdempiereException("@C_BankAccount_ID@ @NotFound@");
			}
			MBankAccount cashAccount = MBankAccount.get(Env.getCtx(), pos.getC_BankAccount_ID());
			if(cashAccount.get_ValueAsInt("DefaultWithdrawalCharge_ID") <= 0) {
				throw new AdempiereException("@DefaultWithdrawalCharge_ID@ @NotFound@");	
			}
			request.getPaymentsList().forEach(paymentRequest -> {
				MPayment payment = CashManagement.createPaymentFromCharge(cashAccount.get_ValueAsInt("DefaultWithdrawalCharge_ID"), paymentRequest, pos, transactionName);
				CashManagement.processPayment(pos, payment, transactionName);
			});
		});
		return Empty.newBuilder();
	}

	/**
	 * Reverse Sales Transaction
	 * @param request ReverseSalesRequest
	 * @return Order.Builder
	 */
	private Order.Builder reverseSalesTransaction(ReverseSalesRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@FillMandatory@ @C_POS_ID@");
		}
		MPOS pos = getPOSFromId(request.getPosId(), true);
		if (pos == null || pos.getC_POS_ID() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		int orderId = request.getId();
		if (orderId <= 0) {
			throw new AdempiereException("@FillMandatory@ @C_Order_ID@");
		}
		MOrder returnOrder = ReverseSalesTransaction.returnCompleteOrder(pos, orderId, request.getDescription());
		//	Default
		return ConvertUtil.convertOrder(returnOrder);
	}

	/**
	 * Process a Shipment
	 * @param request
	 * @return
	 * @return Shipment.Builder
	 */
	private Shipment.Builder processShipment(ProcessShipmentRequest request) {
		if(request.getId() <= 0) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		if(Util.isEmpty(request.getDocumentAction())) {
			throw new AdempiereException("@DocStatus@ @IsMandatory@");
		}
		if(!request.getDocumentAction().equals(MInOut.ACTION_Complete)
				&& !request.getDocumentAction().equals(MInOut.ACTION_Reverse_Accrual)
				&& !request.getDocumentAction().equals(MInOut.ACTION_Reverse_Correct)) {
			throw new AdempiereException("@DocStatus@ @Invalid@");
		}
		AtomicReference<MInOut> shipmentReference = new AtomicReference<MInOut>();
		Trx.run(transactionName -> {
			int shipmentId = request.getId();
			MInOut shipment = new MInOut(Env.getCtx(), shipmentId, transactionName);
			if(shipment.isProcessed()) {
				throw new AdempiereException("@M_InOut_ID@ @Processed@");
			}
			if (!shipment.processIt(request.getDocumentAction())) {
				log.warning("@ProcessFailed@ :" + shipment.getProcessMsg());
				throw new AdempiereException("@ProcessFailed@ :" + shipment.getProcessMsg());
			}
			shipment.saveEx(transactionName);
			shipmentReference.set(shipment);
		});
		//	Default
		return ConvertUtil.convertShipment(shipmentReference.get());
	}
	
	/**
	 * List RMA Lines from RMA ID
	 * @param request
	 * @return
	 */
	private ListRMALinesResponse.Builder listRMALines(ListRMALinesRequest request) {
		if(request.getRmaId() <= 0) {
			throw new AdempiereException("@M_RMA_ID@ @NotFound@");
		}
		MOrder rma = new MOrder(Env.getCtx(), request.getRmaId(), null);
		MPOS pos = new MPOS(Env.getCtx(), rma.getC_POS_ID(), null);
		ListRMALinesResponse.Builder builder = ListRMALinesResponse.newBuilder();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		StringBuffer whereClause = new StringBuffer(I_C_OrderLine.COLUMNNAME_C_Order_ID + " = ?");
		List<Object> parameters = new ArrayList<>();
		parameters.add(request.getRmaId());
		if(pos.get_ValueAsInt("DefaultDiscountCharge_ID") > 0) {
			parameters.add(pos.get_ValueAsInt("DefaultDiscountCharge_ID"));
			whereClause.append(" AND (C_Charge_ID IS NULL OR C_Charge_ID <> ?)");
		}
		//	Get Product list
		Query query = new Query(Env.getCtx(), I_C_OrderLine.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.setOrderBy(I_C_OrderLine.COLUMNNAME_Line)
		.<MOrderLine>list()
		.forEach(rmaLine -> {
			builder.addRmaLines(ConvertUtil.convertRMALine(rmaLine));
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}

	/**
	 * List shipment Lines from Order UUID
	 * @param request
	 * @return
	 */
	private ListShipmentLinesResponse.Builder listShipmentLines(ListShipmentLinesRequest request) {
		if(request.getShipmentId() <= 0) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		ListShipmentLinesResponse.Builder builder = ListShipmentLinesResponse.newBuilder();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		int shipmentId = request.getShipmentId();
		//	Get Product list
		Query query = new Query(Env.getCtx(), I_M_InOutLine.Table_Name, I_M_InOutLine.COLUMNNAME_M_InOut_ID + " = ?", null)
				.setParameters(shipmentId)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.<MInOutLine>list()
		.forEach(line -> {
			ShipmentLine.Builder shipmenLinetBuilder = POSConvertUtil.convertShipmentLine(line);
			builder.addShipmentLines(shipmenLinetBuilder);
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * List refund references from Order UUID
	 * @param request
	 * @return
	 */
	private ListPaymentReferencesResponse.Builder listPaymentReferencesLines(ListPaymentReferencesRequest request) {
		if(request.getCustomerId() <= 0
				&& request.getPosId() <= 0) {
			throw new AdempiereException("@C_BPartner_ID@ / @C_Order_ID@ @IsMandatory@");
		}
		ListPaymentReferencesResponse.Builder builder = ListPaymentReferencesResponse.newBuilder();
		if(MTable.get(Env.getCtx(), "C_POSPaymentReference") == null) {
			return builder;
		}
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		List<Object> parameters = new ArrayList<Object>();
		StringBuffer whereClause = new StringBuffer("(IsPaid = 'N' AND Processed = 'N' OR IsKeepReferenceAfterProcess = 'Y')");
		if(request.getPosId() <= 0) {
			parameters.add(request.getOrderId());
			whereClause.append(" AND C_Order_ID = ?");
		} else if(request.getCustomerId() <= 0) {
			parameters.add(request.getCustomerId());
			whereClause.append(" AND EXISTS(SELECT 1 FROM C_BP_BankAccount ba WHERE ba.C_BP_BankAccount_ID = C_POSPaymentReference.C_BP_BankAccount_ID AND ba.C_BPartner_ID = ?)");
		} else if(request.getPosId() <= 0) {
			parameters.add(request.getPosId());
			whereClause.append(" AND C_POS_ID = ?");
		}
		//	Get Refund Reference list
		Query query = new Query(Env.getCtx(), "C_POSPaymentReference", whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.list()
		.forEach(refundReference -> {
			builder.addPaymentReferences(convertPaymentReference(refundReference));
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * Conver Refund Reference to gRPC stub object
	 * @param paymentReference
	 * @return
	 * @return RefundReference.Builder
	 */
	private PaymentReference.Builder convertPaymentReference(PO paymentReference) {
		PaymentReference.Builder builder = PaymentReference.newBuilder();
		if(paymentReference != null
				&& paymentReference.get_ID() > 0) {
			MCPaymentMethod paymentMethod = MCPaymentMethod.getById(Env.getCtx(), paymentReference.get_ValueAsInt("C_PaymentMethod_ID"), null);
			PaymentMethod.Builder paymentMethodBuilder = ConvertUtil.convertPaymentMethod(paymentMethod);

			int presicion = MCurrency.getStdPrecision(paymentReference.getCtx(), paymentReference.get_ValueAsInt("C_Currency_ID"));

			BigDecimal amount = (BigDecimal) paymentReference.get_Value("Amount");
			amount.setScale(presicion, RoundingMode.HALF_UP);

			MOrder order = new MOrder(Env.getCtx(), paymentReference.get_ValueAsInt("C_Order_ID"), null);
			BigDecimal convertedAmount = ConvertUtil.getConvetedAmount(order, paymentReference, amount)
				.setScale(presicion, RoundingMode.HALF_UP);

			builder.setAmount(
					ValueManager.getValueFromBigDecimal(amount)
				)
				.setDescription(
					ValueManager.validateNull(
						paymentReference.get_ValueAsString("Description")
					)
				)
				.setIsPaid(
					paymentReference.get_ValueAsBoolean("IsPaid")
				)
				.setTenderTypeCode(
					ValueManager.validateNull(
						paymentReference.get_ValueAsString("TenderType")
					)
				)
				.setCurrency(
					ConvertCommon.convertCurrency(
						paymentReference.get_ValueAsInt("C_Currency_ID")
					)
				)
				.setCustomerBankAccountId(paymentReference.get_ValueAsInt("C_BP_BankAccount_ID"))
				.setOrderId(paymentReference.get_ValueAsInt("C_Order_ID"))
				.setPosId(paymentReference.get_ValueAsInt("C_POS_ID"))
				.setSalesRepresentative(
					ConvertUtil.convertSalesRepresentative(
						MUser.get(Env.getCtx(), paymentReference.get_ValueAsInt("SalesRep_ID"))
					)
				)
				.setId(paymentReference.get_ID())
				.setPaymentMethod(paymentMethodBuilder)
				.setPaymentDate(
					ValueManager.getTimestampFromDate(
						(Timestamp) paymentReference.get_Value("PayDate")
					)
				)
				.setIsAutomatic(
					paymentReference.get_ValueAsBoolean("IsAutoCreatedReference")
				)
				.setIsProcessed(
					paymentReference.get_ValueAsBoolean("Processed")
				)
				.setConvertedAmount(
					ValueManager.getValueFromBigDecimal(convertedAmount)
				)
			;
		}
		//	
		return builder;
	}
	
	/**
	 * Delete shipment line from uuid
	 * @param request
	 * @return
	 */
	private Empty.Builder deleteShipmentLine(DeleteShipmentLineRequest request) {
		if(request.getId() <= 0) {
			throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			MInOutLine shipmentLine = new Query(Env.getCtx(), I_M_InOutLine.Table_Name, I_M_InOutLine.COLUMNNAME_M_InOutLine_ID + " = ?", transactionName)
					.setParameters(request.getId())
					.setClient_ID()
					.first();
			if(shipmentLine != null
					&& shipmentLine.getM_InOutLine_ID() != 0) {
				//	Validate processed Order
				if(shipmentLine.isProcessed()) {
					throw new AdempiereException("@M_InOutLine_ID@ @Processed@");
				}
				if(shipmentLine != null
						&& shipmentLine.getM_InOutLine_ID() >= 0) {
					shipmentLine.deleteEx(true);
				}
			}
		});
		//	Return
		return Empty.newBuilder();
	}
	
	/**
	 * Create shipment line and return this
	 * @param request
	 * @return
	 */
	private ShipmentLine.Builder createAndConvertShipmentLine(CreateShipmentLineRequest request) {
		//	Validate Order
		if(request.getShipmentId() <= 0) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		//	Validate Product and charge
		if(request.getOrderLineId() <= 0) {
			throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
		}
		int shipmentId = request.getShipmentId();
		int salesOrderLineId = request.getOrderLineId();
		if(shipmentId <= 0) {
			return ShipmentLine.newBuilder();
		}
		if(salesOrderLineId <= 0) {
			throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
		}
		MInOut shipmentHeader = new MInOut(Env.getCtx(), shipmentId, null);
		if(!DocumentUtil.isDrafted(shipmentHeader)) {
			throw new AdempiereException("@M_InOut_ID@ @Processed@");
		}
		//	Quantity
		MOrderLine salesOrderLine = new MOrderLine(Env.getCtx(), salesOrderLineId, null);
		Optional<MInOutLine> maybeOrderLine = Arrays.asList(shipmentHeader.getLines(true))
				.stream()
				.filter(shipmentLineTofind -> shipmentLineTofind.getC_OrderLine_ID() == salesOrderLine.getC_OrderLine_ID())
				.findFirst();
		AtomicReference<MInOutLine> shipmentLineReference = new AtomicReference<MInOutLine>();
		BigDecimal quantity = ValueManager.getBigDecimalFromValue(
			request.getQuantity()
		);
		//	Validate available
		if(salesOrderLine.getQtyOrdered().subtract(salesOrderLine.getQtyDelivered()).compareTo(Optional.ofNullable(quantity).orElse(Env.ONE)) < 0) {
			throw new AdempiereException("@QtyInsufficient@");
		}
		if(maybeOrderLine.isPresent()) {
			MInOutLine shipmentLine = maybeOrderLine.get();
			//	Set Quantity
			BigDecimal quantityToOrder = quantity;
			if(quantity == null) {
				quantityToOrder = shipmentLine.getMovementQty();
				quantityToOrder = quantityToOrder.add(Env.ONE);
			}
			//	Validate available
			if(salesOrderLine.getQtyOrdered().subtract(salesOrderLine.getQtyDelivered()).compareTo(quantityToOrder) < 0) {
				throw new AdempiereException("@QtyInsufficient@");
			}
			//	Update movement quantity
			updateUomAndQuantityForShipment(shipmentLine, salesOrderLine.getC_UOM_ID(), quantityToOrder);
			shipmentLine.saveEx();
			shipmentLineReference.set(shipmentLine);
		} else {
			MInOutLine shipmentLine = new MInOutLine(shipmentHeader);
			BigDecimal quantityToOrder = quantity;
			if(quantity == null) {
				quantityToOrder = Env.ONE;
			}
	        //create new line
			shipmentLine.setOrderLine(salesOrderLine, 0, quantityToOrder);
			Optional.ofNullable(request.getDescription()).ifPresent(description -> shipmentLine.setDescription(description));
			//	Update movement quantity
			updateUomAndQuantityForShipment(shipmentLine, salesOrderLine.getC_UOM_ID(), quantityToOrder);
			//	Save Line
			shipmentLine.saveEx();
			shipmentLineReference.set(shipmentLine);
		}
		//	Convert Line
		return POSConvertUtil.convertShipmentLine(
			shipmentLineReference.get()
		);
	}
	
	/**
	 * Create  from request
	 * @param context
	 * @param request
	 * @return
	 */
	private Shipment.Builder createShipment(CreateShipmentRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_Order_ID@ @NotFound@");
		}
		AtomicReference<MInOut> maybeShipment = new AtomicReference<MInOut>();
		Trx.run(transactionName -> {
			int orderId = request.getOrderId();
			int salesRepresentativeId = request.getSalesRepresentativeId();
			if(orderId <= 0) {
				throw new AdempiereException("@C_Order_ID@ @NotFound@");
			}
			if(salesRepresentativeId <= 0) {
				throw new AdempiereException("@SalesRep_ID@ @NotFound@");
			}
			MOrder salesOrder = new MOrder(Env.getCtx(), orderId, transactionName);
			if(!OrderUtil.isValidOrder(salesOrder)) {
				throw new AdempiereException("@ActionNotAllowedHere@");
			}
			if(salesOrder.isDelivered()) {
				throw new AdempiereException("@C_Order_ID@ @IsDelivered@");
			}
			//	Valid if has a Order
			if(!DocumentUtil.isCompleted(salesOrder)) {
				throw new AdempiereException("@Invalid@ @C_Order_ID@ " + salesOrder.getDocumentNo());
			}
			//	
			MInOut shipment = new Query(Env.getCtx(), I_M_InOut.Table_Name, 
					"DocStatus = 'DR' "
					+ "AND C_Order_ID = ? "
					+ "AND SalesRep_ID = ?", transactionName)
					.setParameters(salesOrder.getC_Order_ID(), salesRepresentativeId)
					.first();
			//	Validate
			if(shipment == null) {
				shipment = new MInOut(salesOrder, 0, RecordUtil.getDate());
				shipment.setMovementType(MInOut.MOVEMENTTYPE_CustomerShipment);
			} else {
				shipment.setDateOrdered(RecordUtil.getDate());
				shipment.setDateAcct(RecordUtil.getDate());
				shipment.setDateReceived(RecordUtil.getDate());
				shipment.setMovementDate(RecordUtil.getDate());
			}
			//	Default values
			shipment.setC_Order_ID(orderId);
			shipment.setSalesRep_ID(salesRepresentativeId);
			shipment.setC_POS_ID(salesOrder.getC_POS_ID());
			shipment.saveEx(transactionName);
			maybeShipment.set(shipment);
			if (request.getIsCreateLinesFromOrder()) {
				createShipmentLines(shipment, transactionName);
			}
		});
		//	Convert order
		return ConvertUtil.convertShipment(maybeShipment.get());
	}
	
	private void createShipmentLines(MInOut shipmentHeader, String transactionName) {
		MOrder order = new MOrder(Env.getCtx(), shipmentHeader.getC_Order_ID(), transactionName);
		List<MOrderLine> orderLines = Arrays.asList(order.getLines());

		orderLines.stream().forEach(salesOrderLine -> {
			if(!DocumentUtil.isDrafted(shipmentHeader)) {
				throw new AdempiereException("@M_InOut_ID@ @Processed@");
			}
			//	Quantity
			Optional<MInOutLine> maybeOrderLine = Arrays.asList(shipmentHeader.getLines(true))
				.stream()
				.filter(shipmentLineTofind -> shipmentLineTofind.getC_OrderLine_ID() == salesOrderLine.getC_OrderLine_ID())
				.findFirst();

			AtomicReference<MInOutLine> shipmentLineReference = new AtomicReference<MInOutLine>();
			BigDecimal quantity = salesOrderLine.getQtyEntered();
			//	Validate available
			if(salesOrderLine.getQtyOrdered().subtract(salesOrderLine.getQtyDelivered()).compareTo(Optional.ofNullable(quantity).orElse(Env.ONE)) < 0) {
				throw new AdempiereException("@QtyInsufficient@");
			}
			if(maybeOrderLine.isPresent()) {
				MInOutLine shipmentLine = maybeOrderLine.get();
				//	Set Quantity
				BigDecimal quantityToOrder = quantity;
				if(quantity == null) {
					quantityToOrder = shipmentLine.getMovementQty();
					quantityToOrder = quantityToOrder.add(Env.ONE);
				}
				//	Validate available
				if(salesOrderLine.getQtyOrdered().subtract(salesOrderLine.getQtyDelivered()).compareTo(quantityToOrder) < 0) {
					throw new AdempiereException("@QtyInsufficient@");
				}
				//	Update movement quantity
				updateUomAndQuantityForShipment(shipmentLine, salesOrderLine.getC_UOM_ID(), quantityToOrder);
				shipmentLine.saveEx();
				shipmentLineReference.set(shipmentLine);
			} else {
				MInOutLine shipmentLine = new MInOutLine(shipmentHeader);
				BigDecimal quantityToOrder = quantity;
				if(quantity == null) {
					quantityToOrder = Env.ONE;
				}
				//create new line
				shipmentLine.setOrderLine(salesOrderLine, 0, quantityToOrder);
				Optional.ofNullable(salesOrderLine.getDescription()).ifPresent(description -> shipmentLine.setDescription(description));
				//	Update movement quantity
				updateUomAndQuantityForShipment(shipmentLine, salesOrderLine.getC_UOM_ID(), quantityToOrder);
				//	Save Line
				shipmentLine.saveEx();
				shipmentLineReference.set(shipmentLine);
			}
		});
	}


	/**
	 * Create Customer
	 * @param request
	 * @return
	 */
	private Customer.Builder createCustomer(Properties context, CreateCustomerRequest request) {
		//	Validate name
		if(Util.isEmpty(request.getName())) {
			throw new AdempiereException("@Name@ @IsMandatory@");
		}
		//	POS Uuid
		MPOS pos = getPOSFromId(request.getPosId(), true);
		MBPartner businessPartner = MBPartner.getTemplate(context, Env.getAD_Client_ID(Env.getCtx()), pos.getC_POS_ID());
		//	Validate Template
		if(pos.getC_BPartnerCashTrx_ID() <= 0) {
			throw new AdempiereException("@C_BPartnerCashTrx_ID@ @NotFound@");
		}
		MBPartner template = MBPartner.get(context, pos.getC_BPartnerCashTrx_ID());
		Optional<MBPartnerLocation> maybeTemplateLocation = Arrays.asList(template.getLocations(false)).stream().findFirst();
		if(!maybeTemplateLocation.isPresent()) {
			throw new AdempiereException("@C_BPartnerCashTrx_ID@ @C_BPartner_Location_ID@ @NotFound@");
		}
		//	Get location from template
		MLocation templateLocation = maybeTemplateLocation.get().getLocation(false);
		if(templateLocation == null
				|| templateLocation.getC_Location_ID() <= 0) {
			throw new AdempiereException("@C_Location_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			//	Create it
			businessPartner.setAD_Org_ID(0);
			businessPartner.setIsCustomer (true);
			businessPartner.setIsVendor (false);
			businessPartner.set_TrxName(transactionName);
			//	Set Value
			String code = request.getValue();
			if(Util.isEmpty(code)) {
				code = DB.getDocumentNo(Env.getAD_Client_ID(Env.getCtx()), "C_BPartner", transactionName, businessPartner);
			}
			//	
			businessPartner.setValue(code);
			//	Set Value
			Optional.ofNullable(request.getValue()).ifPresent(value -> businessPartner.setValue(value));			
			//	Tax Id
			Optional.ofNullable(request.getTaxId()).ifPresent(value -> businessPartner.setTaxID(value));
			//	Duns
			Optional.ofNullable(request.getDuns()).ifPresent(value -> businessPartner.setDUNS(value));
			//	Naics
			Optional.ofNullable(request.getNaics()).ifPresent(value -> businessPartner.setNAICS(value));
			//	Name
			Optional.ofNullable(request.getName()).ifPresent(value -> businessPartner.setName(value));
			//	Last name
			Optional.ofNullable(request.getLastName()).ifPresent(value -> businessPartner.setName2(value));
			//	Description
			Optional.ofNullable(request.getDescription()).ifPresent(value -> businessPartner.setDescription(value));
			//	Business partner group
			if(request.getBusinessPartnerGroupId() > 0) {
				int businessPartnerGroupId = request.getBusinessPartnerGroupId();
				if(businessPartnerGroupId != 0) {
					businessPartner.setC_BP_Group_ID(businessPartnerGroupId);
				}
			}
			//	Additional attributes
			setAdditionalAttributes(businessPartner, request.getAdditionalAttributes().getFieldsMap());
			//	Save it
			businessPartner.saveEx(transactionName);
			
			// clear price list from business partner group
			if (businessPartner.getM_PriceList_ID() > 0) {
				businessPartner.setM_PriceList_ID(0);
				businessPartner.saveEx(transactionName);
			}
			
			//	Location
			request.getAddressesList().forEach(address -> {
				createCustomerAddress(businessPartner, address, templateLocation, transactionName);
			});
		});
		//	Default return
		return POSConvertUtil.convertCustomer(
			businessPartner
		);
	}
	
	/**
	 * Set additional attributes
	 * @param entity
	 * @param attributes
	 * @return void
	 */
	private void setAdditionalAttributes(PO entity, Map<String, Value> attributes) {
		if(attributes != null) {
			attributes.keySet().forEach(key -> {
				Value attribute = attributes.get(key);
				int referenceId = getReferenceId(entity.get_Table_ID(), key);
				Object value = null;
				if(referenceId > 0) {
					value = ValueManager.getObjectFromReference(attribute, referenceId);
				}
				if(value == null) {
					value = ValueManager.getObjectFromValue(attribute);
				}
				entity.set_ValueOfColumn(key, value);
			});
		}
	}
	
	/**
	 * Get reference from column name and table
	 * @param tableId
	 * @param columnName
	 * @return
	 */
	private int getReferenceId(int tableId, String columnName) {
		MColumn column = MTable.get(Env.getCtx(), tableId).getColumn(columnName);
		if(column == null) {
			return -1;
		}
		return column.getAD_Reference_ID();
	}
	
	/**
	 * Create Address from customer and address request
	 * @param customer
	 * @param address
	 * @param templateLocation
	 * @param transactionName
	 * @return void
	 */
	private void createCustomerAddress(MBPartner customer, AddressRequest address, MLocation templateLocation, String transactionName) {
		int countryId = address.getCountryId();
		//	Instance it
		MLocation location = new MLocation(Env.getCtx(), 0, transactionName);
		if(countryId > 0) {
			int regionId = address.getRegionId();
			int cityId = address.getCityId();
			String cityName = null;
			//	City Name
			if(!Util.isEmpty(address.getCityName())) {
				cityName = address.getCityName();
			}
			location.setC_Country_ID(countryId);
			location.setC_Region_ID(regionId);
			location.setCity(cityName);
			if(cityId > 0) {
				location.setC_City_ID(cityId);
			}
		} else {
			//	Copy
			PO.copyValues(templateLocation, location);
		}
		//	Postal Code
		if(!Util.isEmpty(address.getPostalCode())) {
			location.setPostal(address.getPostalCode());
		}
		//	Address
		Optional.ofNullable(address.getAddress1()).ifPresent(addressValue -> location.setAddress1(addressValue));
		Optional.ofNullable(address.getAddress2()).ifPresent(addressValue -> location.setAddress2(addressValue));
		Optional.ofNullable(address.getAddress3()).ifPresent(addressValue -> location.setAddress3(addressValue));
		Optional.ofNullable(address.getAddress4()).ifPresent(addressValue -> location.setAddress4(addressValue));
		Optional.ofNullable(address.getPostalCode()).ifPresent(postalCode -> location.setPostal(postalCode));
		//	
		location.saveEx(transactionName);
		//	Create BP location
		MBPartnerLocation businessPartnerLocation = new MBPartnerLocation(customer);
		businessPartnerLocation.setC_Location_ID(location.getC_Location_ID());
		//	Default
		businessPartnerLocation.setIsBillTo(address.getIsDefaultBilling());
		businessPartnerLocation.set_ValueOfColumn(VueStoreFrontUtil.COLUMNNAME_IsDefaultBilling, address.getIsDefaultBilling());
		businessPartnerLocation.setIsShipTo(address.getIsDefaultShipping());
		businessPartnerLocation.set_ValueOfColumn(VueStoreFrontUtil.COLUMNNAME_IsDefaultShipping, address.getIsDefaultShipping());
		Optional.ofNullable(address.getContactName()).ifPresent(contact -> businessPartnerLocation.setContactPerson(contact));
		Optional.ofNullable(address.getFirstName()).ifPresent(firstName -> businessPartnerLocation.setName(firstName));
		Optional.ofNullable(address.getEmail()).ifPresent(email -> businessPartnerLocation.setEMail(email));
		Optional.ofNullable(address.getPhone()).ifPresent(phome -> businessPartnerLocation.setPhone(phome));
		Optional.ofNullable(address.getDescription()).ifPresent(description -> businessPartnerLocation.set_ValueOfColumn("Description", description));
		if(Util.isEmpty(businessPartnerLocation.getName())) {
			businessPartnerLocation.setName(".");
		}
		//	Additional attributes
		setAdditionalAttributes(businessPartnerLocation, address.getAdditionalAttributes().getFieldsMap());
		businessPartnerLocation.saveEx(transactionName);
		//	Contact
		if(!Util.isEmpty(address.getContactName()) || !Util.isEmpty(address.getEmail()) || !Util.isEmpty(address.getPhone())) {
			MUser contact = new MUser(customer);
			Optional.ofNullable(address.getEmail()).ifPresent(email -> contact.setEMail(email));
			Optional.ofNullable(address.getPhone()).ifPresent(phome -> contact.setPhone(phome));
			Optional.ofNullable(address.getDescription()).ifPresent(description -> contact.setDescription(description));
			String contactName = address.getContactName();
			if(Util.isEmpty(contactName)) {
				contactName = address.getEmail();
			}
			if(Util.isEmpty(contactName)) {
				contactName = address.getPhone();
			}
			contact.setName(contactName);
			//	Save
			contact.setC_BPartner_Location_ID(businessPartnerLocation.getC_BPartner_Location_ID());
			contact.saveEx(transactionName);
 		}
	}
	
	/**
	 * update Customer
	 * @param request
	 * @return
	 */
	private Customer.Builder updateCustomer(UpdateCustomerRequest request) {
		MPOS pos = getPOSFromId(request.getPosId(), true);
		if(!getBooleanValueFromPOS(pos, Env.getAD_User_ID(Env.getCtx()), "IsAllowsModifyCustomer")) {
			throw new AdempiereException("@POS.ModifyCustomerNotAllowed@");
		}
		//	Customer Uuid
		if(request.getId() <= 0) {
			throw new AdempiereException("@C_BPartner_ID@ @IsMandatory@");
		}
		//	
		AtomicReference<MBPartner> customer = new AtomicReference<MBPartner>();
		Trx.run(transactionName -> {
			//	Create it
			MBPartner businessPartner = MBPartner.get(Env.getCtx(), request.getId());
			if(businessPartner == null) {
				throw new AdempiereException("@C_BPartner_ID@ @NotFound@");
			}
			if(businessPartner.getC_BPartner_ID() == pos.getC_BPartnerCashTrx_ID()) {
				throw new AdempiereException("@POS.ModifyTemplateCustomerNotAllowed@");
			}
			businessPartner.set_TrxName(transactionName);
			//	Set Value
			Optional.ofNullable(request.getValue()).ifPresent(value -> businessPartner.setValue(value));			
			//	Tax Id
			Optional.ofNullable(request.getTaxId()).ifPresent(value -> businessPartner.setTaxID(value));
			//	Duns
			Optional.ofNullable(request.getDuns()).ifPresent(value -> businessPartner.setDUNS(value));
			//	Naics
			Optional.ofNullable(request.getNaics()).ifPresent(value -> businessPartner.setNAICS(value));
			//	Name
			Optional.ofNullable(request.getName()).ifPresent(value -> businessPartner.setName(value));
			//	Last name
			Optional.ofNullable(request.getLastName()).ifPresent(value -> businessPartner.setName2(value));
			//	Description
			Optional.ofNullable(request.getDescription()).ifPresent(value -> businessPartner.setDescription(value));
			//	Additional attributes
			setAdditionalAttributes(businessPartner, request.getAdditionalAttributes().getFieldsMap());
			//	Save it
			businessPartner.saveEx(transactionName);
			//	Location
			request.getAddressesList().forEach(address -> {
				int countryId = address.getCountryId();
				//	
				int regionId = address.getRegionId();
				String cityName = null;
				int cityId = address.getCityId();
				//	City Name
				if(!Util.isEmpty(address.getCityName())) {
					cityName = address.getCityName();
				}
				//	Validate it
				if(countryId > 0
						|| regionId > 0
						|| cityId > 0
						|| !Util.isEmpty(cityName)) {
					//	Find it
					Optional<MBPartnerLocation> maybeCustomerLocation = Arrays.asList(businessPartner.getLocations(true)).stream().filter(customerLocation -> customerLocation.getC_BPartner_Location_ID() == address.getId()).findFirst();
					if(maybeCustomerLocation.isPresent()) {
						MBPartnerLocation businessPartnerLocation = maybeCustomerLocation.get();
						MLocation location = businessPartnerLocation.getLocation(true);
						location.set_TrxName(transactionName);
						if(countryId > 0) {
							location.setC_Country_ID(countryId);
						}
						if(regionId > 0) {
							location.setC_Region_ID(regionId);
						}
						if(cityId > 0) {
							location.setC_City_ID(cityId);
						}
						Optional.ofNullable(cityName).ifPresent(city -> location.setCity(city));
						//	Address
						Optional.ofNullable(address.getAddress1()).ifPresent(addressValue -> location.setAddress1(addressValue));
						Optional.ofNullable(address.getAddress2()).ifPresent(addressValue -> location.setAddress2(addressValue));
						Optional.ofNullable(address.getAddress3()).ifPresent(addressValue -> location.setAddress3(addressValue));
						Optional.ofNullable(address.getAddress4()).ifPresent(addressValue -> location.setAddress4(addressValue));
						Optional.ofNullable(address.getPostalCode()).ifPresent(postalCode -> location.setPostal(postalCode));
						//	Save
						location.saveEx(transactionName);
						//	Update business partner location
						businessPartnerLocation.setIsBillTo(address.getIsDefaultBilling());
						businessPartnerLocation.set_ValueOfColumn(VueStoreFrontUtil.COLUMNNAME_IsDefaultBilling, address.getIsDefaultBilling());
						businessPartnerLocation.setIsShipTo(address.getIsDefaultShipping());
						businessPartnerLocation.set_ValueOfColumn(VueStoreFrontUtil.COLUMNNAME_IsDefaultShipping, address.getIsDefaultShipping());
						Optional.ofNullable(address.getContactName()).ifPresent(contactName -> businessPartnerLocation.set_ValueOfColumn("ContactName", contactName));
						Optional.ofNullable(address.getContactName()).ifPresent(contact -> businessPartnerLocation.setContactPerson(contact));
						Optional.ofNullable(address.getFirstName()).ifPresent(firstName -> businessPartnerLocation.setName(firstName));
						Optional.ofNullable(address.getLastName()).ifPresent(lastName -> businessPartnerLocation.set_ValueOfColumn("Name2", lastName));
						Optional.ofNullable(address.getEmail()).ifPresent(email -> businessPartnerLocation.setEMail(email));
						Optional.ofNullable(address.getPhone()).ifPresent(phome -> businessPartnerLocation.setPhone(phome));
						Optional.ofNullable(address.getDescription()).ifPresent(description -> businessPartnerLocation.set_ValueOfColumn("Description", description));
						//	Additional attributes
						setAdditionalAttributes(businessPartnerLocation, address.getAdditionalAttributes().getFieldsMap());
						businessPartnerLocation.saveEx(transactionName);
						//	Contact
						AtomicReference<MUser> contactReference = new AtomicReference<MUser>(getOfBusinessPartnerLocation(businessPartnerLocation, transactionName));
						if(contactReference.get() == null
								|| contactReference.get().getAD_User_ID() <= 0) {
							contactReference.set(new MUser(businessPartner));
						}
						if(!Util.isEmpty(address.getContactName()) || !Util.isEmpty(address.getEmail()) || !Util.isEmpty(address.getPhone())) {
							MUser contact = contactReference.get();
							Optional.ofNullable(address.getEmail()).ifPresent(email -> contact.setEMail(email));
							Optional.ofNullable(address.getPhone()).ifPresent(phome -> contact.setPhone(phome));
							Optional.ofNullable(address.getDescription()).ifPresent(description -> contact.setDescription(description));
							String contactName = address.getContactName();
							if(Util.isEmpty(contactName)) {
								contactName = address.getEmail();
							}
							if(Util.isEmpty(contactName)) {
								contactName = address.getPhone();
							}
							contact.setName(contactName);
							//	Save
							contact.setC_BPartner_Location_ID(businessPartnerLocation.getC_BPartner_Location_ID());
							contact.saveEx(transactionName);
				 		}
					} else {	//	Create new
						Optional<MBPartnerLocation> maybeTemplateLocation = Arrays.asList(businessPartner.getLocations(false)).stream().findFirst();
						if(!maybeTemplateLocation.isPresent()) {
							throw new AdempiereException("@C_BPartnerCashTrx_ID@ @C_BPartner_Location_ID@ @NotFound@");
						}
						//	Get location from template
						MLocation templateLocation = maybeTemplateLocation.get().getLocation(false);
						if(templateLocation == null
								|| templateLocation.getC_Location_ID() <= 0) {
							throw new AdempiereException("@C_Location_ID@ @NotFound@");
						}
						createCustomerAddress(businessPartner, address, templateLocation, transactionName);
					}
					customer.set(businessPartner);
				}
			});
		});
		//	Default return
		return POSConvertUtil.convertCustomer(
			customer.get()
		);
	}
	
	/**
	 * 
	 * @param businessPartnerLocation
	 * @param transactionName
	 * @return
	 * @return MUser
	 */
	private MUser getOfBusinessPartnerLocation(MBPartnerLocation businessPartnerLocation, String transactionName) {
		return new Query(businessPartnerLocation.getCtx(), MUser.Table_Name, "C_BPartner_Location_ID = ?", transactionName)
				.setParameters(businessPartnerLocation.getC_BPartner_Location_ID())
				.first();
	}
	
	/**
	 * List Warehouses from POS UUID
	 * @param request
	 * @return
	 */
	private ListAvailableWarehousesResponse.Builder listWarehouses(ListAvailableWarehousesRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		ListAvailableWarehousesResponse.Builder builder = ListAvailableWarehousesResponse.newBuilder();
		final String TABLE_NAME = "C_POSWarehouseAllocation";
		if(MTable.getTable_ID(TABLE_NAME) <= 0) {
			return builder;
		}
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Aisle Seller
		int posId = request.getPosId();
		//	Get Product list
		Query query = new Query(Env.getCtx(), TABLE_NAME, "C_POS_ID = ?", null)
				.setParameters(posId)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setOrderBy(I_AD_PrintFormatItem.COLUMNNAME_SeqNo);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.list()
		.forEach(availableWarehouse -> {
			MWarehouse warehouse = MWarehouse.get(Env.getCtx(), availableWarehouse.get_ValueAsInt("M_Warehouse_ID"));
			builder.addWarehouses(
				AvailableWarehouse.newBuilder()
					.setId(warehouse.getM_Warehouse_ID())
					.setKey(
						ValueManager.validateNull(
							warehouse.getValue()
						)
					)
					.setName(
						ValueManager.validateNull(
							warehouse.getName()
						)
					)
					.setIsPosRequiredPin(
						availableWarehouse.get_ValueAsBoolean(I_C_POS.COLUMNNAME_IsPOSRequiredPIN)
					)
			);
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * List Price List from POS UUID
	 * @param request
	 * @return
	 */
	private ListAvailablePriceListResponse.Builder listPriceList(ListAvailablePriceListRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		ListAvailablePriceListResponse.Builder builder = ListAvailablePriceListResponse.newBuilder();
		final String TABLE_NAME = "C_POSPriceListAllocation";
		if(MTable.getTable_ID(TABLE_NAME) <= 0) {
			return builder;
		}
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Dynamic where clause
		//	Aisle Seller
		int posId = request.getPosId();
		//	Get Product list
		Query query = new Query(Env.getCtx(), TABLE_NAME, "C_POS_ID = ?", null)
				.setParameters(posId)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setOrderBy(I_AD_PrintFormatItem.COLUMNNAME_SeqNo);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.list()
		.forEach(availablePriceList -> {
			MPriceList priceList = MPriceList.get(Env.getCtx(), availablePriceList.get_ValueAsInt("M_PriceList_ID"), null);
			builder.addPriceList(
				AvailablePriceList.newBuilder()
					.setId(priceList.getM_PriceList_ID())
					.setKey(
						ValueManager.validateNull(
							priceList.getName()
						)
					)
					.setName(
						ValueManager.validateNull(
							priceList.getName()
						)
					)
					.setIsPosRequiredPin(
						availablePriceList.get_ValueAsBoolean(I_C_POS.COLUMNNAME_IsPOSRequiredPIN)
					)
			);
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * List tender Types from POS UUID
	 * @param request
	 * @return
	 */
	private ListAvailablePaymentMethodsResponse.Builder listPaymentMethods(ListAvailablePaymentMethodsRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		ListAvailablePaymentMethodsResponse.Builder builder = ListAvailablePaymentMethodsResponse.newBuilder();
		final String TABLE_NAME = "C_POSPaymentTypeAllocation";
		if(MTable.getTable_ID(TABLE_NAME) <= 0) {
			return builder;
		}
		final String PAYMENT_METHOD_TABLE_NAME = "C_PaymentMethod";
		MTable paymentTypeTable = MTable.get(Env.getCtx(), PAYMENT_METHOD_TABLE_NAME);
		if(paymentTypeTable == null
				|| paymentTypeTable.getAD_Table_ID() <= 0) {
			return builder;
		}
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Dynamic where clause
		//	Aisle Seller
		int posId = request.getPosId();
		//	Get Product list
		Query query = new Query(
				Env.getCtx(),
				TABLE_NAME,
				" C_POS_ID = ? AND IsDisplayedFromCollection = 'Y' ",
				null
			)
				.setParameters(posId)
				.setOnlyActiveRecords(true)
				.setOrderBy(I_AD_PrintFormatItem.COLUMNNAME_SeqNo);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.list()
		.forEach(availablePaymentMethod -> {
			MCPaymentMethod paymentMethod = (MCPaymentMethod) paymentTypeTable.getPO(availablePaymentMethod.get_ValueAsInt("C_PaymentMethod_ID"), null);
			PaymentMethod.Builder paymentMethodBuilder = ConvertUtil.convertPaymentMethod(paymentMethod);

			AvailablePaymentMethod.Builder tenderTypeValue = AvailablePaymentMethod.newBuilder()
				.setId(availablePaymentMethod.get_ID())
				.setName(
					Util.isEmpty(availablePaymentMethod.get_ValueAsString(I_AD_Ref_List.COLUMNNAME_Name)) ?
						ValueManager.validateNull(paymentMethod.getName()) :
						ValueManager.validateNull(availablePaymentMethod.get_ValueAsString(I_AD_Ref_List.COLUMNNAME_Name))
				)
				.setPosId(request.getPosId())
				.setIsPosRequiredPin(
					availablePaymentMethod.get_ValueAsBoolean(I_C_POS.COLUMNNAME_IsPOSRequiredPIN)
				)
				.setIsAllowedToRefund(
					availablePaymentMethod.get_ValueAsBoolean("IsAllowedToRefund")
				)
				.setIsAllowedToRefundOpen(
					availablePaymentMethod.get_ValueAsBoolean("IsAllowedToRefundOpen")
				)
				.setMaximumRefundAllowed(
					ValueManager.getValueFromBigDecimal(
						(BigDecimal) availablePaymentMethod.get_Value("MaximumRefundAllowed")
					)
				)
				.setMaximumDailyRefundAllowed(
					ValueManager.getValueFromBigDecimal(
						(BigDecimal) availablePaymentMethod.get_Value("MaximumDailyRefundAllowed")
					)
				)
				.setIsPaymentReference(
					availablePaymentMethod.get_ValueAsBoolean("IsPaymentReference")
				)
				.setDocumentTypeId(
					availablePaymentMethod.get_ValueAsInt("C_DocTypeCreditMemo_ID")
				)
					.setDocumentTypeId(availablePaymentMethod.get_ValueAsInt("C_DocTypeCreditMemo_ID"))
					.setPaymentMethod(paymentMethodBuilder)
			;
			if(availablePaymentMethod.get_ValueAsInt("RefundReferenceCurrency_ID") > 0) {
				tenderTypeValue.setRefundReferenceCurrency(
					ConvertCommon.convertCurrency(
						availablePaymentMethod.get_ValueAsInt("RefundReferenceCurrency_ID")
					)
				);
			}
			if(availablePaymentMethod.get_ValueAsInt("ReferenceCurrency_ID") > 0) {
				tenderTypeValue.setReferenceCurrency(
					ConvertCommon.convertCurrency(
						availablePaymentMethod.get_ValueAsInt("ReferenceCurrency_ID")
					)
				);
			}
			builder.addPaymentMethods(tenderTypeValue);
			//	
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * List Document Types from POS UUID
	 * @param request
	 * @return
	 */
	private ListAvailableDocumentTypesResponse.Builder listDocumentTypes(ListAvailableDocumentTypesRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		ListAvailableDocumentTypesResponse.Builder builder = ListAvailableDocumentTypesResponse.newBuilder();
		final String TABLE_NAME = "C_POSDocumentTypeAllocation";
		if(MTable.getTable_ID(TABLE_NAME) <= 0) {
			return builder;
		}
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Dynamic where clause
		//	Aisle Seller
		int posId = request.getPosId();
		//	Get Product list
		Query query = new Query(Env.getCtx(), TABLE_NAME, "C_POS_ID = ?", null)
				.setParameters(posId)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setOrderBy(I_AD_PrintFormatItem.COLUMNNAME_SeqNo);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.list()
		.forEach(availableDocumentType -> {
			MDocType documentType = MDocType.get(Env.getCtx(), availableDocumentType.get_ValueAsInt("C_DocType_ID"));
			builder.addDocumentTypes(AvailableDocumentType.newBuilder()
				.setId(documentType.getC_DocType_ID())
				.setKey(
					ValueManager.validateNull(
						documentType.getName()
					)
				)
				.setName(
					ValueManager.validateNull(
						documentType.getPrintName()
					)
				)
				.setIsPosRequiredPin(availableDocumentType.get_ValueAsBoolean(I_C_POS.COLUMNNAME_IsPOSRequiredPIN)));
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * List Currencies from POS UUID
	 * @param request
	 * @return
	 */
	private ListAvailableCurrenciesResponse.Builder listCurrencies(ListAvailableCurrenciesRequest request) {
		MPOS pos = getPOSFromId(request.getPosId(), true);
		ListAvailableCurrenciesResponse.Builder builder = ListAvailableCurrenciesResponse.newBuilder();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Dynamic where clause
		String whereClause = "EXISTS(SELECT 1 FROM C_Conversion_Rate cr "
				+ "WHERE (cr.C_Currency_ID = C_Currency.C_Currency_ID  OR cr.C_Currency_ID_To = C_Currency.C_Currency_ID) "
				+ "AND cr.C_ConversionType_ID = ? AND ? >= cr.ValidFrom AND ? <= cr.ValidTo)";
		//	Aisle Seller
		Timestamp now = TimeUtil.getDay(System.currentTimeMillis());
		//	Get Product list
		Query query = new Query(Env.getCtx(), I_C_Currency.Table_Name, whereClause.toString(), null)
				.setParameters(pos.get_ValueAsInt(I_C_ConversionType.COLUMNNAME_C_ConversionType_ID), now, now)
				.setOnlyActiveRecords(true);
		int count = query.count();
		query.setLimit(limit, offset)
			.getIDsAsList()
			.forEach(currencyId -> {
				Currency.Builder currencyBuilder = ConvertCommon.convertCurrency(currencyId);
				builder.addCurrencies(currencyBuilder);
			});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * Process Order from Point of Sales
	 * @param request
	 * @return
	 */
	private MOrder processOrder(ProcessOrderRequest request) {
		MPOS pos = getPOSFromId(request.getPosId(), true);
		return OrderManagement.processOrder(
			pos,
			request.getId(),
			request.getIsOpenRefund()
		);
	}
	
	/**
	 * Get Boolean value from POS
	 * @param pos
	 * @param userId
	 * @param columnName
	 * @return
	 */
	private boolean getBooleanValueFromPOS(MPOS pos, int userId, String columnName) {
		if (pos.get_ValueAsBoolean("IsAllowsAllocateSeller")) {
			PO userAllocated = getUserAllowed(Env.getCtx(), pos.getC_POS_ID(), userId, null);
			if(userAllocated != null) {
				// if column exists in C_POSSellerAllocation_ID
				if(userAllocated.get_ColumnIndex(columnName) >= 0) {
					return userAllocated.get_ValueAsBoolean(columnName);
				}
			}
		}
		// if column exists in C_POS
		if (pos.get_ColumnIndex(columnName) >= 0) {
			return pos.get_ValueAsBoolean(columnName);
		}
		return false;
	}

	/**
	 * Get Decimal value from pos
	 * @param pos
	 * @param userId
	 * @param columnName
	 * @return
	 */
	private BigDecimal getBigDecimalValueFromPOS(MPOS pos, int userId, String columnName) {
		PO userAllocated = getUserAllowed(Env.getCtx(), pos.getC_POS_ID(), userId, null);
		if (userAllocated != null) {
			if (userAllocated.get_ColumnIndex(columnName) >= 0) {
				return Optional.ofNullable((BigDecimal) userAllocated.get_Value(columnName)).orElse(BigDecimal.ZERO);
			}
		}
		if (pos.get_ColumnIndex(columnName) >= 0) {
			return Optional.ofNullable((BigDecimal) pos.get_Value(columnName)).orElse(BigDecimal.ZERO);
		}
		return BigDecimal.ZERO;
	}
	
	/**
	 * Get Integer value from pos
	 * @param pos
	 * @param userId
	 * @param columnName
	 * @return
	 */
	private int getIntegerValueFromPOS(MPOS pos, int userId, String columnName) {
		PO userAllocated = getUserAllowed(Env.getCtx(), pos.getC_POS_ID(), userId, null);
		if (userAllocated != null) {
			if (userAllocated.get_ColumnIndex(columnName) >= 0) {
				return userAllocated.get_ValueAsInt(columnName);
			}
		}
		if (pos.get_ColumnIndex(columnName) >= 0) {
			return pos.get_ValueAsInt(columnName);
		}
		return -1;
	}
	
	/**
	 * Get write off amount tolerance
	 * @param pos
	 * @return
	 */
	private BigDecimal getWriteOffAmtTolerance(MPOS pos) {
		BigDecimal writeOffAmtTolerance = getBigDecimalValueFromPOS(pos, Env.getAD_User_ID(Env.getCtx()), "WriteOffAmtTolerance");
		int currencyId = getIntegerValueFromPOS(pos, Env.getAD_User_ID(Env.getCtx()), "WriteOffAmtCurrency_ID");
		if(currencyId > 0) {
			writeOffAmtTolerance = OrderUtil.getConvertedAmount(pos, currencyId, writeOffAmtTolerance);
		}
		return writeOffAmtTolerance;
	}

	/**
	 * Process payment of Order
	 * @param salesOrder
	 * @param pos
	 * @param isOpenRefund
	 * @param transactionName
	 * @return void
	 */
	private void processPayments(MOrder salesOrder, MPOS pos, boolean isOpenRefund, String transactionName) {
		//	Get invoice if exists
		int invoiceId = salesOrder.getC_Invoice_ID();
		AtomicReference<BigDecimal> openAmount = new AtomicReference<BigDecimal>(salesOrder.getGrandTotal());
		AtomicReference<BigDecimal> currentAmount = new AtomicReference<BigDecimal>(salesOrder.getGrandTotal());
		List<Integer> paymentsIds = new ArrayList<Integer>();
		//	Complete Payments
		List<MPayment> payments = MPayment.getOfOrder(salesOrder);
		payments.stream().sorted(Comparator.comparing(MPayment::getCreated)).forEach(payment -> {
			BigDecimal convertedAmount = getConvetedAmount(salesOrder, payment, payment.getPayAmt());
			//	Get current open amount
			AtomicReference<BigDecimal> multiplier = new AtomicReference<BigDecimal>(Env.ONE);
			if(!payment.isReceipt()) {
				multiplier.set(Env.ONE.negate());
			}
			openAmount.updateAndGet(amount -> amount.subtract(convertedAmount.multiply(multiplier.get())));
			if(payment.isAllocated()) {
				currentAmount.updateAndGet(amount -> amount.subtract(convertedAmount.multiply(multiplier.get())));
			}
			if(DocumentUtil.isDrafted(payment)) {
				payment.setIsPrepayment(true);
				payment.setOverUnderAmt(Env.ZERO);
				if(payment.getTenderType().equals(MPayment.TENDERTYPE_CreditMemo)) {
					createCreditMemo(salesOrder, payment, transactionName);
				}
				payment.setDocAction(MPayment.DOCACTION_Complete);
				setCurrentDate(payment);
				payment.saveEx(transactionName);
				if (!payment.processIt(MPayment.DOCACTION_Complete)) {
					log.warning("@ProcessFailed@ :" + payment.getProcessMsg());
					throw new AdempiereException("@ProcessFailed@ :" + payment.getProcessMsg());
				}
				payment.saveEx(transactionName);
				paymentsIds.add(payment.getC_Payment_ID());
				salesOrder.saveEx(transactionName);
			}
			CashManagement.addPaymentToCash(pos, payment);
		});
		//	Validate Write Off Amount
		if(!isOpenRefund) {
			BigDecimal writeOffAmtTolerance = getBigDecimalValueFromPOS(pos, Env.getAD_User_ID(Env.getCtx()), "WriteOffAmtTolerance");
			int currencyId = getIntegerValueFromPOS(pos, Env.getAD_User_ID(Env.getCtx()), "WriteOffAmtCurrency_ID");
			if(currencyId > 0) {
				writeOffAmtTolerance = getConvetedAmount(salesOrder, currencyId, writeOffAmtTolerance);
			}
			if(writeOffAmtTolerance.compareTo(Env.ZERO) > 0 && openAmount.get().abs().compareTo(writeOffAmtTolerance) > 0) {
				throw new AdempiereException("@POS.WriteOffAmtToleranceExceeded@");
			}
		}
		//	Allocate all payments
		if(paymentsIds.size() > 0) {
			String description = Msg.parseTranslation(Env.getCtx(), "@C_POS_ID@: " + pos.getName() + " - " + salesOrder.getDocumentNo());
			//
			MAllocationHdr paymentAllocation = new MAllocationHdr (Env.getCtx(), true, RecordUtil.getDate(), salesOrder.getC_Currency_ID(), description, transactionName);
			paymentAllocation.setAD_Org_ID(salesOrder.getAD_Org_ID());
			//	Set Description
			paymentAllocation.saveEx();
			AtomicBoolean isAllocationLineCreated = new AtomicBoolean(false);
			//	Add lines
			paymentsIds.stream().map(paymentId -> new MPayment(Env.getCtx(), paymentId, transactionName))
			.filter(payment -> payment.getC_POS_ID() != 0 && !payment.getTenderType().equals(MPayment.TENDERTYPE_CreditMemo))
			.forEach(payment -> {
				BigDecimal multiplier = Env.ONE;
				if(!payment.isReceipt()) {
					multiplier = Env.ONE.negate();
				}
				BigDecimal paymentAmount = getConvetedAmount(salesOrder, payment, payment.getPayAmt());
				BigDecimal discountAmount = getConvetedAmount(salesOrder, payment, payment.getDiscountAmt());
				BigDecimal overUnderAmount = getConvetedAmount(salesOrder, payment, payment.getOverUnderAmt());
				BigDecimal writeOffAmount = getConvetedAmount(salesOrder, payment, payment.getWriteOffAmt());
				if (overUnderAmount.signum() < 0 && paymentAmount.signum() > 0) {
					paymentAmount = paymentAmount.add(overUnderAmount);
				}
				MAllocationLine paymentAllocationLine = new MAllocationLine (paymentAllocation, paymentAmount.multiply(multiplier), discountAmount.multiply(multiplier), writeOffAmount.multiply(multiplier), overUnderAmount.multiply(multiplier));
				paymentAllocationLine.setDocInfo(salesOrder.getC_BPartner_ID(), salesOrder.getC_Order_ID(), invoiceId);
				paymentAllocationLine.setPaymentInfo(payment.getC_Payment_ID(), 0);
				paymentAllocationLine.saveEx();
				isAllocationLineCreated.set(true);
			});
			//	Allocate all Credit Memo
			paymentsIds.stream().map(paymentId -> new MPayment(Env.getCtx(), paymentId, transactionName))
			.filter(payment -> payment.getC_POS_ID() != 0 && payment.getTenderType().equals(MPayment.TENDERTYPE_CreditMemo))
			.forEach(payment -> {
				//	Create new Line
				if(!isAllocationLineCreated.get()) {
					BigDecimal discountAmount = Env.ZERO;
					BigDecimal overUnderAmount = Env.ZERO;
					BigDecimal writeOffAmount = Env.ZERO;
					MAllocationLine paymentAllocationLine = new MAllocationLine (paymentAllocation, currentAmount.get(), discountAmount, writeOffAmount, overUnderAmount);
					paymentAllocationLine.setDocInfo(salesOrder.getC_BPartner_ID(), salesOrder.getC_Order_ID(), invoiceId);
					paymentAllocationLine.saveEx();
				}
				BigDecimal multiplier = Env.ONE.negate();
				MInvoice creditMemo = new Query(payment.getCtx(), MInvoice.Table_Name, "C_Payment_ID = ?", payment.get_TrxName()).setParameters(payment.getC_Payment_ID()).first();
				BigDecimal paymentAmount = getConvetedAmount(salesOrder, payment, creditMemo.getGrandTotal());
				BigDecimal discountAmount = Env.ZERO;
				BigDecimal overUnderAmount = Env.ZERO;
				BigDecimal writeOffAmount = Env.ZERO;
				if (overUnderAmount.signum() < 0 && paymentAmount.signum() > 0) {
					paymentAmount = paymentAmount.add(overUnderAmount);
				}
				MAllocationLine paymentAllocationLine = new MAllocationLine (paymentAllocation, paymentAmount.multiply(multiplier), discountAmount.multiply(multiplier), writeOffAmount.multiply(multiplier), overUnderAmount.multiply(multiplier));
				paymentAllocationLine.setDocInfo(salesOrder.getC_BPartner_ID(), salesOrder.getC_Order_ID(), creditMemo.getC_Invoice_ID());
				paymentAllocationLine.saveEx();
			});
			//	Add write off
			if(!isOpenRefund) {
				if(openAmount.get().compareTo(Env.ZERO) != 0) {
					MAllocationLine paymentAllocationLine = new MAllocationLine (paymentAllocation, Env.ZERO, Env.ZERO, openAmount.get(), Env.ZERO);
					paymentAllocationLine.setDocInfo(salesOrder.getC_BPartner_ID(), salesOrder.getC_Order_ID(), invoiceId);
					paymentAllocationLine.saveEx();
				}
			}
			//	Complete
			if (!paymentAllocation.processIt(MAllocationHdr.DOCACTION_Complete)) {
				log.warning("@ProcessFailed@ :" + paymentAllocation.getProcessMsg());
				throw new AdempiereException("@ProcessFailed@ :" + paymentAllocation.getProcessMsg());
			}
			paymentAllocation.saveEx();
			//	Test allocation
			paymentsIds.stream().map(paymentId -> new MPayment(Env.getCtx(), paymentId, transactionName)).forEach(payment -> {
				payment.setIsAllocated(true);
				payment.saveEx();
			});
		}
	}

	/**
	 * Get Payment Method allocation from payment
	 * @param payment
	 * @return
	 * @return PO
	 */
	private PO getPaymentMethodAllocation(int paymentMethodId, int posId, String transactionName) {
		if(MTable.get(Env.getCtx(), "C_POSPaymentTypeAllocation") == null) {
			return null;
		}
		return new Query(Env.getCtx(), "C_POSPaymentTypeAllocation", "C_POS_ID = ? AND C_PaymentMethod_ID = ?", transactionName)
				.setParameters(posId, paymentMethodId)
				.setOnlyActiveRecords(true)
				.first();
	}

	/**
	 * Create Credit Memo from payment
	 * @param salesOrder
	 * @param payment
	 * @param transactionName
	 * @return void
	 */
	private void createCreditMemo(MOrder salesOrder, MPayment payment, String transactionName) {
		MInvoice creditMemo = new MInvoice(salesOrder.getCtx(), 0, transactionName);
		creditMemo.setBPartner((MBPartner) payment.getC_BPartner());
		creditMemo.setDescription(payment.getDescription());
		creditMemo.setIsSOTrx(true);
		creditMemo.setSalesRep_ID(payment.get_ValueAsInt("CollectingAgent_ID"));
		creditMemo.setAD_Org_ID(payment.getAD_Org_ID());
		creditMemo.setC_POS_ID(payment.getC_POS_ID());
		if(creditMemo.getM_PriceList_ID() <= 0
				|| creditMemo.getC_Currency_ID() != payment.getC_Currency_ID()) {
			String isoCode = MCurrency.getISO_Code(salesOrder.getCtx(), payment.getC_Currency_ID());
			MPriceList priceList = MPriceList.getDefault(salesOrder.getCtx(), true, isoCode);
			if(priceList == null) {
				throw new AdempiereException("@M_PriceList_ID@ @NotFound@ (@C_Currency_ID@ " + isoCode + ")");
			}
			creditMemo.setM_PriceList_ID(priceList.getM_PriceList_ID());
		}
		PO paymentTypeAllocation = getPaymentMethodAllocation(payment.get_ValueAsInt("C_PaymentMethod_ID"), payment.getC_POS_ID(), payment.get_TrxName());
		int chargeId = 0;
		if(paymentTypeAllocation != null) {
			chargeId = paymentTypeAllocation.get_ValueAsInt("C_Charge_ID");
			if(paymentTypeAllocation.get_ValueAsInt("C_DocTypeCreditMemo_ID") > 0) {
				creditMemo.setC_DocTypeTarget_ID(paymentTypeAllocation.get_ValueAsInt("C_DocTypeCreditMemo_ID"));
			}
		}
		//	Set if not exist
		if(creditMemo.getC_DocTypeTarget_ID() <= 0) {
			creditMemo.setC_DocTypeTarget_ID(MDocType.DOCBASETYPE_ARCreditMemo);
		}
		creditMemo.setDocumentNo(payment.getDocumentNo());
		creditMemo.setC_ConversionType_ID(payment.getC_ConversionType_ID());
		creditMemo.setC_Order_ID(salesOrder.getC_Order_ID());
		creditMemo.setC_Payment_ID(payment.getC_Payment_ID());
		creditMemo.saveEx(transactionName);
		//	Add line
		MInvoiceLine creditMemoLine = new MInvoiceLine(creditMemo);
		if(chargeId <= 0) {
			chargeId = new Query(salesOrder.getCtx(), MCharge.Table_Name, null, transactionName).setClient_ID().firstId();
		}
		MCharge charge = MCharge.get(payment.getCtx(), chargeId);
		Optional<MTax> optionalTax = Arrays.asList(MTax.getAll(Env.getCtx()))
				.stream()
				.filter(tax -> tax.getC_TaxCategory_ID() == charge.getC_TaxCategory_ID()
									&& (tax.isSalesTax()
											|| (!Util.isEmpty(tax.getSOPOType())
													&& (tax.getSOPOType().equals(MTax.SOPOTYPE_Both)
															|| tax.getSOPOType().equals(MTax.SOPOTYPE_SalesTax)))))
				.findFirst();
		creditMemoLine.setC_Charge_ID(chargeId);
		creditMemoLine.setC_Tax_ID(optionalTax.get().getC_Tax_ID());
		creditMemoLine.setQty(Env.ONE);
		creditMemoLine.setPrice(payment.getPayAmt());
		creditMemoLine.setDescription(payment.getDescription());
		creditMemoLine.saveEx(transactionName);
		//	change payment
		payment.setC_Invoice_ID(-1);
		payment.setPayAmt(Env.ZERO);
		payment.saveEx(transactionName);
		//	Process credit Memo
		if (!creditMemo.processIt(MInvoice.DOCACTION_Complete)) {
			log.warning("@ProcessFailed@ :" + creditMemo.getProcessMsg());
			throw new AdempiereException("@ProcessFailed@ :" + creditMemo.getProcessMsg());
		}
		creditMemo.setDocumentNo(payment.getDocumentNo());
		creditMemo.saveEx(transactionName);
	}

	/**
	 * Get Refund references from order
	 * @param order
	 * @return
	 * @return List<PO>
	 */
	private static List<PO> getPaymentReferences(MOrder order) {
		if(MTable.get(Env.getCtx(), "C_POSPaymentReference") == null) {
			return new ArrayList<PO>();
		}
		//
		return new Query(order.getCtx(), "C_POSPaymentReference", "C_Order_ID = ? AND IsPaid = 'N' AND Processed = 'N'", order.get_TrxName()).setParameters(order.getC_Order_ID()).list();
	}

	/**
	 * Get Refund Reference Amount
	 * @param order
	 * @return
	 * @return BigDecimal
	 */
	private static BigDecimal getPaymentReferenceAmount(MOrder order, List<PO> paymentReferences) {
		Optional<BigDecimal> paymentReferenceAmount = paymentReferences.stream().map(refundReference -> {
			return getConvetedAmount(order, refundReference, ((BigDecimal) refundReference.get_Value("Amount")));
		}).collect(Collectors.reducing(BigDecimal::add));
		if(paymentReferenceAmount.isPresent()) {
			return paymentReferenceAmount.get();
		}
		return Env.ZERO;
	}

	/**
	 * Get Converted Amount based on Order currency and optional currency id
	 * @param order
	 * @param payment
	 * @return
	 * @return BigDecimal
	 */
	public static BigDecimal getConvetedAmount(MOrder order, int fromCurrencyId, BigDecimal amount) {
		if(fromCurrencyId == order.getC_Currency_ID()
				|| amount == null
				|| amount.compareTo(Env.ZERO) == 0) {
			return amount;
		}
		BigDecimal convertedAmount = MConversionRate.convert(order.getCtx(), amount, fromCurrencyId, order.getC_Currency_ID(), order.getDateAcct(), order.get_ValueAsInt("C_ConversionType_ID"), order.getAD_Client_ID(), order.getAD_Org_ID());
		//
		return Optional.ofNullable(convertedAmount).orElse(Env.ZERO);
	}

	/**
	 * Get Converted Amount based on Order currency
	 * @param order
	 * @param payment
	 * @return
	 * @return BigDecimal
	 */
	public static BigDecimal getConvetedAmount(MOrder order, PO payment, BigDecimal amount) {
		if(payment.get_ValueAsInt("C_Currency_ID") == order.getC_Currency_ID()
				|| amount == null
				|| amount.compareTo(Env.ZERO) == 0) {
			return amount;
		}
		BigDecimal convertedAmount = MConversionRate.convert(payment.getCtx(), amount, payment.get_ValueAsInt("C_Currency_ID"), order.getC_Currency_ID(), order.getDateAcct(), payment.get_ValueAsInt("C_ConversionType_ID"), payment.getAD_Client_ID(), payment.getAD_Org_ID());
		//
		return Optional.ofNullable(convertedAmount).orElse(Env.ZERO);
	}

	/**
	 * Get Converted Amount based on Order currency
	 * @param order
	 * @param payment
	 * @return
	 * @return BigDecimal
	 */
	private BigDecimal getConvetedAmount(MOrder order, MPayment payment, BigDecimal amount) {
		if(payment.getC_Currency_ID() == order.getC_Currency_ID()
				|| amount == null
				|| amount.compareTo(Env.ZERO) == 0) {
			return amount;
		}
		BigDecimal convertedAmount = MConversionRate.convert(payment.getCtx(), amount, payment.getC_Currency_ID(), order.getC_Currency_ID(), payment.getDateAcct(), payment.getC_ConversionType_ID(), payment.getAD_Client_ID(), payment.getAD_Org_ID());
		if(convertedAmount == null
				|| convertedAmount.compareTo(Env.ZERO) == 0) {
			throw new AdempiereException(MConversionRate.getErrorMessage(payment.getCtx(), "ErrorConvertingDocumentCurrencyToBaseCurrency", payment.getC_Currency_ID(), order.getC_Currency_ID(), payment.getC_ConversionType_ID(), payment.getDateAcct(), payment.get_TrxName()));
		}
		//
		return convertedAmount;
	}

	/**
	 * List Orders from POS UUID
	 * @param request
	 * @return
	 */
	private ListOrdersResponse.Builder listOrders(ListOrdersRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @IsMandatory@");
		}
		//	Sales Representative
		if(request.getSalesRepresentativeId() <= 0) {
			throw new AdempiereException("@SalesRep_ID@ @IsMandatory@");
		}

		//	Dynamic where clause
		MPOS pos = getPOSFromId(request.getPosId(), true);
		int posId = pos.getC_POS_ID();
		int salesRepresentativeId = request.getSalesRepresentativeId();
		int orgId = pos.getAD_Org_ID();
		MUser salesRepresentative = MUser.get(Env.getCtx(), salesRepresentativeId);

		StringBuffer whereClause = new StringBuffer();
		List<Object> parameters = new ArrayList<Object>();
		whereClause.append("(C_Order.AD_Org_ID = ? OR C_Order.C_POS_ID = ?)");
		parameters.add(orgId);
		parameters.add(posId);

		final String whereClauseWithoutProposal = " AND NOT EXISTS(SELECT 1 FROM C_DocType dt "
			+ "WHERE dt.C_DocType_ID = C_Order.C_DocTypeTarget_ID "
			+ "AND dt.DocSubTypeSO IN('ON', 'OB', 'PR'))"
		;
		if(!salesRepresentative.get_ValueAsBoolean("IsPOSManager")) {
			if(pos.get_ValueAsBoolean("IsConfidentialInfo")) {
				whereClause.append(" AND ((C_Order.SalesRep_ID = ? OR C_Order.AssignedSalesRep_ID = ?) AND C_Order.C_POS_ID = ?)");
				parameters.add(salesRepresentativeId);
				parameters.add(salesRepresentativeId);
				parameters.add(posId);
			} else {
				if(request.getIsOnlyAisleSeller()) {
					whereClause
						.append(" AND DocStatus NOT IN('VO', 'CL')")
						.append(" AND ((C_Order.SalesRep_ID = ? OR COALESCE(C_Order.AssignedSalesRep_ID, ?) = ?)")
						.append(" AND EXISTS(SELECT 1 FROM C_POS p WHERE p.C_POS_ID = C_Order.C_POS_ID AND p.IsAisleSeller = 'Y'))")
						.append(whereClauseWithoutProposal)
					;
					parameters.add(salesRepresentativeId);
					parameters.add(salesRepresentativeId);
					parameters.add(salesRepresentativeId);
				} else {
					whereClause.append(" AND ((C_Order.SalesRep_ID = ? OR COALESCE(C_Order.AssignedSalesRep_ID, ?) = ?) AND EXISTS(SELECT 1 FROM C_POS p WHERE p.C_POS_ID = C_Order.C_POS_ID AND p.IsSharedPOS = 'Y'))");
					parameters.add(salesRepresentativeId);
					parameters.add(salesRepresentativeId);
					parameters.add(salesRepresentativeId);
				}
			}
		}
		//	Document No
		if(!Util.isEmpty(request.getDocumentNo())) {
			whereClause.append(" AND (UPPER(DocumentNo) LIKE '%' || UPPER(?) || '%'");
			whereClause.append(" OR EXISTS(");
			whereClause.append("SELECT 1 ");
			whereClause.append("FROM C_Invoice i ");
			whereClause.append("JOIN C_InvoiceLine il ON i.C_Invoice_ID=il.C_Invoice_ID ");
			whereClause.append("JOIN C_OrderLine ol ON il.C_OrderLine_ID=ol.C_OrderLine_ID ");
			whereClause.append("WHERE ol.C_Order_ID=C_Order.C_Order_ID ");
			whereClause.append("AND UPPER(i.DocumentNo) = UPPER(?)");
			whereClause.append("))");
			parameters.add(request.getDocumentNo());
			parameters.add(request.getDocumentNo());
		}
		//	Business Partner
		if(request.getBusinessPartnerId() > 0) {
			int businessPartnerId = request.getBusinessPartnerId();
			whereClause.append(" AND C_BPartner_ID = ?");
			parameters.add(businessPartnerId);
		}
		//	Grand Total
		BigDecimal grandTotal = ValueManager.getBigDecimalFromValue(
			request.getGrandTotal()
		);
		if(grandTotal != null
				&& !grandTotal.equals(Env.ZERO)) {
			whereClause.append(" AND GrandTotal = ?");
			parameters.add(grandTotal);
		}
		//	Support Open Amount
		BigDecimal openAmount = ValueManager.getBigDecimalFromValue(
			request.getOpenAmount()
		);
		if(openAmount != null
				&& !openAmount.equals(Env.ZERO)) {
			whereClause.append(" (EXISTS(SELECT 1 FROM C_Invoice i WHERE i.C_Order_ID = C_Order.C_Order_ID GROUP BY i.C_Order_ID HAVING(SUM(invoiceopen(i.C_Invoice_ID, 0)) = ?))"
					+ " OR EXISTS(SELECT 1 FROM C_Payment p WHERE C_Order_ID = C_Order.C_Order_ID GROUP BY p.C_Order_ID HAVING(SUM(p.PayAmt) = ?)"
					+ ")");
			parameters.add(openAmount);
			parameters.add(openAmount);
		}
		if(request.getIsOnlyProcessed()) {
			whereClause.append(" AND DocStatus IN('CO')");
		}
		//	Is Invoiced
		if(request.getIsWaitingForInvoice()) {
			whereClause.append(" AND DocStatus NOT IN('VO', 'CL')")
				.append(" AND NOT EXISTS(SELECT 1 FROM C_Invoice i WHERE i.C_Order_ID = C_Order.C_Order_ID AND i.DocStatus IN('CO', 'CL'))")
				.append(whereClauseWithoutProposal)
			;
		}
		//	for payment (credit)
		if(request.getIsWaitingForPay()) {
			whereClause.append(" AND DocStatus IN('CO')")
				.append(" AND (EXISTS(SELECT 1 FROM C_Invoice i WHERE i.C_Order_ID = C_Order.C_Order_ID AND i.DocStatus IN('CO', 'CL') AND i.IsPaid = 'N')")
				.append(" OR IsInvoiced = 'N')")
				.append(whereClauseWithoutProposal)
			;
		}
		if(request.getIsBindingOffer()) {
			whereClause.append(" AND DocStatus IN('DR', 'IP') ")
				.append("AND EXISTS(SELECT 1 FROM C_DocType dt ")
				.append("WHERE dt.C_DocType_ID = C_Order.C_DocTypeTarget_ID ")
				.append("AND dt.DocSubTypeSO IN('ON', 'OB', 'PR')) ")
			;
		}
		if(request.getIsWaitingForShipment()) {
			whereClause.append(" AND DocStatus IN('CO') AND NOT EXISTS(SELECT 1 FROM M_InOut io WHERE io.C_Order_ID = C_Order.C_Order_ID AND io.DocStatus IN('CO', 'CL'))")
			.append(whereClauseWithoutProposal)
			;
		}
		if(request.getIsClosed()) {
			whereClause.append(" AND DocStatus IN('CL') ");
		}
		if(request.getIsNullified()) {
			whereClause.append(" AND DocStatus IN('VO') ");
		}
		//	Is RMA
		if(request.getIsOnlyRma()) {
			whereClause.append(" AND DocStatus IN('CO', 'CL')")
				.append(" AND EXISTS(SELECT 1 FROM C_DocType dt WHERE dt.C_DocType_ID = C_Order.C_DocTypeTarget_ID AND dt.DocSubTypeSO IN('RM') AND dt.IsSOTrx = 'Y') ")
				.append(whereClauseWithoutProposal)
			;
		}
		//	Date Order From
		if(ValueManager.getDateFromTimestampDate(request.getDateOrderedFrom()) != null) {
			whereClause.append(" AND DateOrdered >= ?");
			parameters.add(
				TimeUtil.getDay(
					ValueManager.getDateFromTimestampDate(
						request.getDateOrderedFrom()
					)
				)
			);
		}
		//	Date Order To
		if(ValueManager.getDateFromTimestampDate(request.getDateOrderedTo()) != null) {
			whereClause.append(" AND DateOrdered <= ?");
			parameters.add(
				TimeUtil.getDay(
					ValueManager.getDateFromTimestampDate(
						request.getDateOrderedTo()
					)
				)
			);
		}

		//	Get Product list
		Query query = new Query(
			Env.getCtx(),
			I_C_Order.Table_Name,
			whereClause.toString(),
			null
		)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setOrderBy(
					I_C_Order.COLUMNNAME_DateOrdered + " DESC, "
					+ I_C_Order.COLUMNNAME_Updated + " DESC"
				);

		int count = query.count();
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		String nexPageToken = null;
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListOrdersResponse.Builder builder = ListOrdersResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(
				ValueManager.validateNull(nexPageToken)
			)
		;

		query.setLimit(limit, offset)
			.getIDsAsList()
			.forEach(orderId -> {
				Order.Builder orderBuilder = POSConvertUtil.convertOder(orderId);
				builder.addOrders(orderBuilder);
			});

		return builder;
	}


	/**
	 * List Payments from POS UUID
	 * @param request
	 * @return
	 */
	private ListPaymentsResponse.Builder listPayments(ListPaymentsRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		ListPaymentsResponse.Builder builder = ListPaymentsResponse.newBuilder();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Dynamic where clause
		StringBuffer whereClause = new StringBuffer();
		//	Parameters
		List<Object> parameters = new ArrayList<Object>();
		//	Aisle Seller
		int posId = request.getPosId();
		int orderId = request.getOrderId();
		//	For order
		if(orderId > 0) {
			whereClause.append("C_Payment.C_Order_ID = ?");
			parameters.add(orderId);
		} else {
			whereClause.append("C_Payment.C_POS_ID = ?");
			parameters.add(posId);
			whereClause.append(" AND C_Payment.C_Charge_ID IS NOT NULL AND C_Payment.Processed = 'N'");
		}
		if(request.getIsOnlyRefund()) {
			if(whereClause.length() > 0) {
				whereClause.append(" AND ");
			}
			whereClause.append("C_Payment.IsReceipt = 'N'");
		}
		if(request.getIsOnlyReceipt()) {
			if(whereClause.length() > 0) {
				whereClause.append(" AND ");
			}
			whereClause.append("C_Payment.IsReceipt = 'Y'");
		}
		//	Get Product list
		Query query = new Query(Env.getCtx(), I_C_Payment.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.<MPayment>list()
		.forEach(payment -> {
			builder.addPayments(ConvertUtil.convertPayment(payment));
		});
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * List Orders Lines from Order UUID
	 * @param request
	 * @return
	 */
	private ListOrderLinesResponse.Builder listOrderLines(ListOrderLinesRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_Order_ID@ @NotFound@");
		}
		int orderId = request.getOrderId();
		MOrder order = new MOrder(Env.getCtx(), orderId, null);
		MPOS pos = new MPOS(Env.getCtx(), order.getC_POS_ID(), order.get_TrxName());
		ListOrderLinesResponse.Builder builder = ListOrderLinesResponse.newBuilder();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		StringBuffer whereClause = new StringBuffer(I_C_OrderLine.COLUMNNAME_C_Order_ID + " = ?");
		List<Object> parameters = new ArrayList<>();
		parameters.add(orderId);
		if(pos.get_ValueAsInt("DefaultDiscountCharge_ID") > 0) {
			parameters.add(pos.get_ValueAsInt("DefaultDiscountCharge_ID"));
			whereClause.append(" AND (C_Charge_ID IS NULL OR C_Charge_ID <> ?)");
		}
		//	Get Product list
		Query query = new Query(Env.getCtx(), I_C_OrderLine.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.setOrderBy(I_C_OrderLine.COLUMNNAME_Line)
		.<MOrderLine>list()
		.forEach(orderLine -> {
			builder.addOrderLines(ConvertUtil.convertOrderLine(orderLine));
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * Get Order from UUID
	 * @param uuid
	 * @return
	 */
	private MOrder getOrder(int id, String transactionName) {
		return (MOrder) RecordUtil.getEntity(Env.getCtx(), I_C_Order.Table_Name, id, transactionName);
	}

	private MOrder changeOrderAssigned(int orderId) {
		return changeOrderAssigned(orderId, 0);
	}
	
	/**
	 * Update Order from UUID
	 * @param request
	 * @return
	 */
	private MOrder changeOrderAssigned(int orderId, int salesRepresentativeId) {
		AtomicReference<MOrder> orderReference = new AtomicReference<MOrder>();
		if(orderId > 0) {
			Trx.run(transactionName -> {
				MOrder salesOrder = getOrder(orderId, transactionName);
				if(salesOrder == null) {
					throw new AdempiereException("@C_Order_ID@ @NotFound@");
				}
				if(!DocumentUtil.isDrafted(salesOrder)) {
					throw new AdempiereException("@C_Order_ID@ @Processed@");
				}
				if(salesRepresentativeId > 0) {
					if(salesOrder.get_ValueAsInt("AssignedSalesRep_ID") > 0 && salesRepresentativeId != salesOrder.get_ValueAsInt(ColumnsAdded.COLUMNNAME_AssignedSalesRep_ID)) {
						throw new AdempiereException("@POS.SalesRepAssigned@");
					}
					salesOrder.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_AssignedSalesRep_ID, salesRepresentativeId);
				} else {
					salesOrder.set_ValueOfColumn(ColumnsAdded.COLUMNNAME_AssignedSalesRep_ID, null);
				}
				//	Save
				salesOrder.saveEx(transactionName);
				orderReference.set(salesOrder);
			});
		}
		//	Return order
		return orderReference.get();
	}
	
	/**
	 * Update Order from ID
	 * @param request
	 * @return
	 */
	private MOrder updateOrder(UpdateOrderRequest request) {
		if (request.getId() <= 0) {
			throw new AdempiereException("@FillMandatory@ @C_Order_ID@");
		}

		AtomicReference<MOrder> orderReference = new AtomicReference<MOrder>();
		Trx.run(transactionName -> {
			MOrder salesOrder = getOrder(request.getId(), transactionName);
			if(salesOrder == null) {
				throw new AdempiereException("@C_Order_ID@ @NotFound@");
			}
			if(!DocumentUtil.isDrafted(salesOrder)) {
				throw new AdempiereException("@C_Order_ID@ @Processed@");
			}
			OrderManagement.validateOrderReleased(salesOrder);
			//	Update Date Ordered
			Timestamp now = TimeUtil.getDay(System.currentTimeMillis());
			salesOrder.setDateOrdered(now);
			salesOrder.setDateAcct(now);
			salesOrder.setDatePromised(now);
			//	POS
			if(request.getPosId() > 0 && salesOrder.getC_POS_ID() <= 0) {
				salesOrder.setC_POS_ID(request.getPosId());
			}
			//	Document Type
			if(request.getDocumentTypeId() > 0) {
				int documentTypeId = request.getDocumentTypeId();
				if(documentTypeId > 0 && documentTypeId != salesOrder.getC_DocTypeTarget_ID()) {
					salesOrder.setC_DocTypeTarget_ID(documentTypeId);
					salesOrder.setC_DocType_ID(documentTypeId);
					//	Set Sequenced No
					String value = DB.getDocumentNo(documentTypeId, transactionName, false, salesOrder);
					if (value != null) {
						salesOrder.setDocumentNo(value);
					}
				}
			}
			//	Business partner
			if(request.getCustomerId() > 0 && salesOrder.getC_POS_ID() > 0) {
				configureBPartner(salesOrder, request.getCustomerId(), transactionName);
			}
			//	Description
			if(!Util.isEmpty(request.getDescription())) {
				salesOrder.setDescription(request.getDescription());
			}
			//	Campaign
			int campaignId = request.getCampaignId();
			if(campaignId > 0 && campaignId != salesOrder.getC_Campaign_ID()) {
				salesOrder.setC_Campaign_ID(campaignId);
				// update campaign on lines
				for (MOrderLine orderLine: salesOrder.getLines()) {
					orderLine.setC_Campaign_ID(campaignId);
					orderLine.saveEx(transactionName);
				}
			}
			//	Price List
			int priceListId = request.getPriceListId();
			if(priceListId > 0 && priceListId != salesOrder.getM_PriceList_ID()) {
				salesOrder.setM_PriceList_ID(priceListId);
				salesOrder.saveEx(transactionName);
				configurePriceList(salesOrder);
			}
			//	Warehouse
			int warehouseId = request.getWarehouseId();
			if(warehouseId > 0) {
				salesOrder.setM_Warehouse_ID(warehouseId);
				salesOrder.saveEx(transactionName);
				configureWarehouse(salesOrder);
			}
			//	Discount Amount
			BigDecimal discountRate = ValueManager.getBigDecimalFromValue(
				request.getDiscountRate()
			);
			if(discountRate != null) {
				configureDiscount(salesOrder, discountRate, transactionName);
			}
			//	Discount Off
			BigDecimal discountRateOff = ValueManager.getBigDecimalFromValue(
				request.getDiscountRateOff()
			);
			BigDecimal discountAmountOff = ValueManager.getBigDecimalFromValue(
				request.getDiscountAmountOff()
			);
			if(discountRateOff != null) {
				configureDiscountRateOff(salesOrder, discountRateOff, transactionName);
			} else if(discountAmountOff != null) {
				configureDiscountAmountOff(salesOrder, discountAmountOff, transactionName);
			}

			// Sales Representative
			if (request.getSalesRepresentativeId() > 0) {
				salesOrder.setSalesRep_ID(request.getSalesRepresentativeId());
			}

			//	Save
			salesOrder.saveEx(transactionName);
			orderReference.set(salesOrder);
		});
		//	Return order
		return orderReference.get();
	}

	/**
	 * 	Set BPartner, update price list and locations
	 *  Configuration of Business Partner has priority over POS configuration
	 *	@param order
	 *	@param businessPartnerId id
	 */
	public void configureBPartner(MOrder order, int businessPartnerId, String transactionName) {
		//	Valid if has a Order
		if(DocumentUtil.isCompleted(order)
				|| DocumentUtil.isVoided(order))
			return;
		log.fine( "CPOS.setC_BPartner_ID=" + businessPartnerId);
		boolean isSamePOSPartner = false;
		// TODO: Validate order.getC_POS_ID > 0 and pos != null
		MPOS pos = new MPOS(Env.getCtx(), order.getC_POS_ID(), null);
		//	Validate BPartner
		if (businessPartnerId == 0) {
			isSamePOSPartner = true;
			businessPartnerId = pos.getC_BPartnerCashTrx_ID();
		}
		//	Get BPartner
		MBPartner partner = MBPartner.get(Env.getCtx(), businessPartnerId);
		partner.set_TrxName(null);
		if (partner == null || partner.get_ID() == 0) {
			throw new AdempiereException("POS.NoBPartnerForOrder");
		} else {
			log.info("CPOS.SetC_BPartner_ID -" + partner);
			order.setBPartner(partner);
			AtomicBoolean priceListIsChanged = new AtomicBoolean(false);
			if(partner.getM_PriceList_ID() > 0 && pos.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_IsKeepPriceFromCustomer)) {
				MPriceList businesPartnerPriceList = MPriceList.get(Env.getCtx(), partner.getM_PriceList_ID(), null);
				MPriceList currentPriceList = MPriceList.get(Env.getCtx(), pos.getM_PriceList_ID(), null);
				if(currentPriceList.getC_Currency_ID() != businesPartnerPriceList.getC_Currency_ID()) {
					order.setM_PriceList_ID(currentPriceList.getM_PriceList_ID());
				} else if(currentPriceList.getM_PriceList_ID() != partner.getM_PriceList_ID()) {
					priceListIsChanged.set(true);
				}
			} else {
				order.setM_PriceList_ID(pos.getM_PriceList_ID());
			}
			//	
			MBPartnerLocation [] partnerLocations = partner.getLocations(true);
			if(partnerLocations.length > 0) {
				for(MBPartnerLocation partnerLocation : partnerLocations) {
					if(partnerLocation.isBillTo())
						order.setBill_Location_ID(partnerLocation.getC_BPartner_Location_ID());
					if(partnerLocation.isShipTo())
						order.setShip_Location_ID(partnerLocation.getC_BPartner_Location_ID());
				}				
			}
			//	Validate Same BPartner
			if(isSamePOSPartner) {
				if(order.getPaymentRule() == null)
					order.setPaymentRule(MOrder.PAYMENTRULE_Cash);
			}
			//	Set Sales Representative
			if (order.getC_BPartner().getSalesRep_ID() != 0) {
				order.setSalesRep_ID(order.getC_BPartner().getSalesRep_ID());
			} else {
				order.setSalesRep_ID(Env.getAD_User_ID(Env.getCtx()));
			}
			//	Save Header
			order.saveEx(transactionName);
			//	Load Price List Version
			Arrays.asList(order.getLines(true, "Line"))
			.forEach(orderLine -> {
				orderLine.setC_BPartner_ID(partner.getC_BPartner_ID());
				orderLine.setC_BPartner_Location_ID(order.getC_BPartner_Location_ID());
				orderLine.setPrice();
				orderLine.setTax();
				orderLine.saveEx(transactionName);
				if(Optional.ofNullable(orderLine.getPriceActual()).orElse(Env.ZERO).signum() == 0
						&& priceListIsChanged.get()) {
					orderLine.saveEx(transactionName);
				}
			});
		}
		//	Change for payments
		MPayment.getOfOrder(order).forEach(payment -> {
			if(DocumentUtil.isCompleted(payment)
					|| DocumentUtil.isVoided(payment)) {
				throw new AdempiereException("@C_Payment_ID@ @Processed@ " + payment.getDocumentNo());
			}
			//	Change Business Partner
			payment.setC_BPartner_ID(order.getC_BPartner_ID());
			payment.saveEx(transactionName);
		});
	}
	
	/**
	 * Configure Warehouse after change
	 * @param order
	 */
	private void configureWarehouse(MOrder order) {
		Arrays.asList(order.getLines())
		.forEach(orderLine -> {
			orderLine.setM_Warehouse_ID(order.getM_Warehouse_ID());
			orderLine.saveEx();
		});
	}
	
	/**
	 * Configure Discount for all lines
	 * @param order
	 */
	private void configureDiscount(MOrder order, BigDecimal discountRate, String transactionName) {
		Arrays.asList(order.getLines())
		.forEach(orderLine -> {
			BigDecimal discountAmount = orderLine.getPriceList().multiply(Optional.ofNullable(discountRate).orElse(Env.ZERO).divide(Env.ONEHUNDRED));
			BigDecimal price = orderLine.getPriceList().subtract(discountAmount);
			//	Set values
			orderLine.setPrice(price); 
			//	sets List/limit
			orderLine.setTax();
			orderLine.saveEx(transactionName);
		});
	}
	
	/**
	 * Configure Discount Off for order
	 * @param order
	 */
	private void configureDiscountRateOff(MOrder order, BigDecimal discountRateOff, String transactionName) {
		if(discountRateOff == null) {
			return;
		}
		MPOS pos = new MPOS(order.getCtx(), order.getC_POS_ID(), order.get_TrxName());
		if(pos.get_ValueAsInt("DefaultDiscountCharge_ID") <= 0) {
			throw new AdempiereException("@DefaultDiscountCharge_ID@ @NotFound@");
		}
		//	Validate Discount
		Optional<BigDecimal> baseAmount = Arrays.asList(order.getLines()).stream()
				.filter(ordeLine -> ordeLine.getC_Charge_ID() != pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultDiscountCharge_ID))
				.map(ordeLine -> ordeLine.getLineNetAmt())
		.collect(Collectors.reducing(BigDecimal::add));
		//	Get Base amount
		if(baseAmount.isPresent()
				&& baseAmount.get().compareTo(Env.ZERO) > 0) {
			int precision = MCurrency.getStdPrecision(order.getCtx(), order.getC_Currency_ID());
			BigDecimal finalPrice = getFinalPrice(baseAmount.get(), discountRateOff, precision);
			createDiscountLine(pos, order, baseAmount.get().subtract(finalPrice), transactionName);
			//	Set Discount Rate
			order.set_ValueOfColumn("FlatDiscount", discountRateOff);
			order.saveEx(transactionName);
		} else {
			deleteDiscountLine(pos, order, transactionName);
		}
	}
	
	/**
	 * Get final price based on base price and discount applied
	 * @param basePrice
	 * @param discount
	 * @return
	 */
	public static BigDecimal getFinalPrice(BigDecimal basePrice, BigDecimal discount, int precision) {
		basePrice = Optional.ofNullable(basePrice).orElse(Env.ZERO);
		discount = Optional.ofNullable(discount).orElse(Env.ZERO);
		//	A = 100 - discount
		BigDecimal multiplier = Env.ONE.subtract(discount.divide(Env.ONEHUNDRED, MathContext.DECIMAL128));
		//	B = A / 100
		BigDecimal finalPrice = basePrice.multiply(multiplier);
		finalPrice = finalPrice.setScale(precision, RoundingMode.HALF_UP);
		return finalPrice;
	}
	
	/**
	 * Get discount based on base price and price list
	 * @param finalPrice
	 * @param basePrice
	 * @param precision
	 * @return
	 */
	public static BigDecimal getDiscount(BigDecimal basePrice, BigDecimal finalPrice, int precision) {
		finalPrice = Optional.ofNullable(finalPrice).orElse(Env.ZERO);
		basePrice = Optional.ofNullable(basePrice).orElse(Env.ZERO);
		BigDecimal discount = Env.ZERO;
		if (basePrice.compareTo(Env.ZERO) != 0) {
			discount = basePrice.subtract(finalPrice);
			discount = discount.divide(basePrice, MathContext.DECIMAL128);
			discount = discount.multiply(Env.ONEHUNDRED);
		}
		if (discount.scale() > precision) {
			discount = discount.setScale(precision, RoundingMode.HALF_UP);
		}
		return discount;
	}
	
	/**
	 * Configure Discount Off for order
	 * @param order
	 */
	private void configureDiscountAmountOff(MOrder order, BigDecimal discountAmountOff, String transactionName) {
		if(discountAmountOff == null) {
			return;
		}
		MPOS pos = new MPOS(order.getCtx(), order.getC_POS_ID(), order.get_TrxName());
		if(pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultDiscountCharge_ID) <= 0) {
			throw new AdempiereException("@DefaultDiscountCharge_ID@ @NotFound@");
		}
		int defaultDiscountChargeId = pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultDiscountCharge_ID);
		boolean isAllowsApplyDiscount = getBooleanValueFromPOS(pos, Env.getAD_User_ID(Env.getCtx()), ColumnsAdded.COLUMNNAME_IsAllowsApplyDiscount);
		if(!isAllowsApplyDiscount) {
			throw new AdempiereException("@POS.ApplyDiscountNotAllowed@");
		}
		BigDecimal maximumDiscountAllowed = getBigDecimalValueFromPOS(pos, Env.getAD_User_ID(Env.getCtx()), ColumnsAdded.COLUMNNAME_MaximumDiscountAllowed);
		BigDecimal baseAmount = Optional.ofNullable(Arrays.asList(order.getLines()).stream()
				.filter(orderLine -> orderLine.getC_Charge_ID() != defaultDiscountChargeId || defaultDiscountChargeId == 0)
				.map(ordeLine -> ordeLine.getLineNetAmt())
				.reduce(BigDecimal.ZERO, BigDecimal::add)).orElse(Env.ZERO);
		if(baseAmount.compareTo(Env.ZERO) <= 0) {
			deleteDiscountLine(pos, order, transactionName);
			return;
		}
		//	
		int precision = MCurrency.getStdPrecision(order.getCtx(), order.getC_Currency_ID());
		BigDecimal discountRateOff = getDiscount(baseAmount, baseAmount.add(discountAmountOff), precision).negate();
		if(maximumDiscountAllowed.compareTo(Env.ZERO) > 0 && discountRateOff.compareTo(maximumDiscountAllowed) > 0) {
			throw new AdempiereException("@POS.MaximumDiscountAllowedExceeded@");
		}
		//	Create Discount line
		createDiscountLine(pos, order, discountAmountOff, transactionName);
		//	Set Discount Rate
		order.set_ValueOfColumn("FlatDiscount", discountRateOff);
		order.saveEx(transactionName);
	}
	
	/**
	 * Delete Discount Line
	 * @param pos
	 * @param order
	 * @param transactionName
	 */
	private void deleteDiscountLine(MPOS pos, MOrder order, String transactionName) {
		Optional<MOrderLine> maybeOrderLine = Arrays.asList(order.getLines()).stream()
				.filter(ordeLine -> ordeLine.getC_Charge_ID() == pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultDiscountCharge_ID))
				.findFirst();
		maybeOrderLine.ifPresent(discountLine -> discountLine.deleteEx(true,transactionName));
	}
	
	/**
	 * Create Discount Line
	 * @param pos
	 * @param order
	 * @param amount
	 * @param transactionName
	 */
	private void createDiscountLine(MPOS pos, MOrder order, BigDecimal amount, String transactionName) {
		Optional<MOrderLine> maybeOrderLine = Arrays.asList(order.getLines()).stream()
				.filter(ordeLine -> ordeLine.getC_Charge_ID() == pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultDiscountCharge_ID))
				.findFirst();
		MOrderLine discountOrderLine = null;
		if(maybeOrderLine.isPresent()) {
			discountOrderLine = maybeOrderLine.get();
			discountOrderLine.setQty(Env.ONE);
			discountOrderLine.setPrice(amount.negate());
		} else {
			discountOrderLine = new MOrderLine(order);
			discountOrderLine.setQty(Env.ONE);
			discountOrderLine.setC_Charge_ID(pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultDiscountCharge_ID));
			discountOrderLine.setPrice(amount.negate());
		}
		//	
		discountOrderLine.saveEx(transactionName);
	}
	
	/**
	 * Configure Price List after change
	 * @param order
	 */
	private void configurePriceList(MOrder order) {
		Arrays.asList(order.getLines())
		.forEach(orderLine -> {
			orderLine.setPrice();
			orderLine.setTax();
			orderLine.saveEx();
			if(Optional.ofNullable(orderLine.getPriceActual()).orElse(Env.ZERO).signum() == 0) {
				orderLine.deleteEx(true);
			}
		});
	}
	
	/**
	 * Validate User PIN
	 * @param userPin
     */
	private Empty.Builder validatePIN(ValidatePINRequest request) {
		MPOS pos = POS.validateAndGetPOS(request.getPosId(), false);

		if(Util.isEmpty(request.getPin())) {
			throw new AdempiereException("@UserPIN@ @IsMandatory@");
		}
		PO supervisorAccess = getSupervisorAccessFromPIN(pos.getC_POS_ID(), Env.getAD_User_ID(Env.getCtx()), request.getPin(), request.getRequestedAccess());
		if(supervisorAccess == null) {
			throw new AdempiereException("@POS.SupervisorNotFound@");
		}
		//	Validate special access for PIN (Amount and other types)
		if(request.getRequestedAccess().equals(ColumnsAdded.COLUMNNAME_IsAllowsWriteOffAmount)) {
			if(request.getOrderId() > 0) {
				MOrder order = new MOrder(Env.getCtx(), request.getOrderId(), null);
				MPriceList priceList = MPriceList.get(Env.getCtx(), order.getM_PriceList_ID(), null);
				int standardPrecision = priceList.getStandardPrecision();
			    BigDecimal totalOpenAmount = OrderUtil.getTotalOpenAmount(order);
			    BigDecimal totalPaymentAmount = OrderUtil.getTotalPaymentAmount(order);
			    BigDecimal writeOffAmount = Optional.ofNullable(totalOpenAmount).orElse(Env.ZERO).subtract(Optional.ofNullable(totalPaymentAmount).orElse(Env.ZERO)).abs();
			    BigDecimal writeOffPercent = OrderUtil.getWriteOffPercent(totalOpenAmount, totalPaymentAmount, standardPrecision);
				//	For Write off
			    if(supervisorAccess.get_ValueAsBoolean(ColumnsAdded.COLUMNNAME_ECA14_WriteOffByPercent)) {
					BigDecimal allowedPercent = Optional.ofNullable((BigDecimal) supervisorAccess.get_Value(ColumnsAdded.COLUMNNAME_WriteOffPercentageTolerance)).orElse(Env.ZERO);
					//	Validate Here
					if(allowedPercent.compareTo(Env.ZERO) == 0
							|| allowedPercent.compareTo(writeOffPercent) >= 0) {
						return Empty.newBuilder();
					} else {
						throw new AdempiereException("@POS.WriteOffNotAllowedByAmount@");
					}
				} else {
					BigDecimal allowedAmount = Optional.ofNullable((BigDecimal) supervisorAccess.get_Value(ColumnsAdded.COLUMNNAME_WriteOffAmtTolerance)).orElse(Env.ZERO);
					int allowedCurrencyId = supervisorAccess.get_ValueAsInt(ColumnsAdded.COLUMNNAME_WriteOffAmtCurrency_ID);
					if(allowedCurrencyId <= 0) {
						MPriceList posPriceList = MPriceList.get(Env.getCtx(), pos.getM_PriceList_ID(), null);
						allowedCurrencyId = posPriceList.getC_Currency_ID();
					}
					allowedAmount = OrderUtil.getConvertedAmountFrom(
						order,
						allowedCurrencyId,
						allowedAmount
					);
					//	Validate Here
					if(allowedAmount.compareTo(Env.ZERO) == 0
							|| allowedAmount.compareTo(writeOffAmount) >= 0) {
						return Empty.newBuilder();
					} else {
						throw new AdempiereException("@POS.WriteOffNotAllowedByAmount@");
					}
				}
			} else {
				throw new AdempiereException("@C_Order_ID@ @NotFound@");
			}
		}
		//	Default
		return Empty.newBuilder();
	}
	
	/**
	 * Just get supervisor access for evaluate based on POS ID, PIN and Requested Access
	 * @param posId
	 * @param userId
	 * @param pin
	 * @param requestedAccess
	 * @return
	 */
	private PO getSupervisorAccessFromPIN(int posId, int userId, String pin, String requestedAccess) {
		if (Util.isEmpty(requestedAccess)) {
			return null;
		}
		MPOS pos = POS.validateAndGetPOS(posId, false);

		StringBuffer whereClause = new StringBuffer();
		List<Object> parameters = new ArrayList<>();
		parameters.add(pos.getC_POS_ID());
		MTable table = MTable.get(Env.getCtx(), "C_POSSellerAllocation");
		if (table == null) {
			throw new AdempiereException("@TableName@ @NotFound@ C_POSSellerAllocation");
		}
		if(table.getColumn(requestedAccess) != null) {
			whereClause.append(" AND ").append(requestedAccess).append("= 'Y'");
		}
		//	Add PIN
		parameters.add(pin);
		//	Get if exists
		return new Query(Env.getCtx(), table.getTableName(), "C_POS_ID = ? "
				+ "AND IsAllowsPOSManager = 'Y' "
				+ "AND EXISTS(SELECT 1 FROM AD_User u "
				+ "WHERE u.AD_User_ID = C_POSSellerAllocation.SalesRep_ID "
				+ "AND u.IsActive = 'Y' "
				+ "AND u.UserPIN = ?)",
				null
			)
			.setOnlyActiveRecords(true)
			.setParameters(parameters)
			.first()
		;
	}

	/**
	 * Load Price List Version from Price List
	 * @param priceListId
	 * @param validFrom
	 * @param transactionName
	 * @return
	 * @return MPriceListVersion
	 */
	public MPriceListVersion loadPriceListVersion(int priceListId, Timestamp validFrom, String transactionName) {
		MPriceList priceList = MPriceList.get(Env.getCtx(), priceListId, transactionName);
		//
		return priceList.getPriceListVersion(validFrom);
	}
	
	/**
	 * Delete order line from uuid
	 * @param request
	 * @return
	 */
	private Empty.Builder deleteOrderLine(DeleteOrderLineRequest request) {
		if(request.getId() <= 0) {
			throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			MOrderLine orderLine = new Query(Env.getCtx(), I_C_OrderLine.Table_Name, I_C_OrderLine.COLUMNNAME_C_OrderLine_ID + " = ?", transactionName)
					.setParameters(request.getId())
					.setClient_ID()
					.first();
			if(orderLine != null
					&& orderLine.getC_OrderLine_ID() != 0) {
				//	Validate processed Order
				if(orderLine.isProcessed()) {
					throw new AdempiereException("@C_OrderLine_ID@ @Processed@");
				}
				if(orderLine != null
						&& orderLine.getC_Order_ID() >= 0) {
					MOrder order = orderLine.getParent();
					OrderManagement.validateOrderReleased(order);
					orderLine.deleteEx(true);
					//	Apply Discount from order
					configureDiscountRateOff(order, (BigDecimal) order.get_Value("FlatDiscount"), transactionName);
				}
			}
		});
		//	Return
		return Empty.newBuilder();
	}
	
	/**
	 * Delete order line from uuid
	 * @param request
	 * @return
	 */
	private Empty.Builder deletePaymentReference(DeletePaymentReferenceRequest request) {
		if(request.getId() <= 0) {
			throw new AdempiereException("@C_POSPaymentReference_ID@ @IsMandatory@");
		}
		if(MTable.get(Env.getCtx(), "C_POSPaymentReference") == null) {
			return Empty.newBuilder();
		}
		Trx.run(transactionName -> {
			PO refundReference = RecordUtil.getEntity(Env.getCtx(), "C_POSPaymentReference", request.getId(), transactionName);
			if(refundReference != null
					&& refundReference.get_ID() != 0) {
				//	Validate processed Order
				refundReference.deleteEx(true);
			} else {
				throw new AdempiereException("@C_POSPaymentReference_ID@ @NotFound@");
			}
		});
		//	Return
		return Empty.newBuilder();
	}



	@Override
	public void deleteOrder(DeleteOrderRequest request, StreamObserver<Empty> responseObserver) {
		try {
			Empty.Builder order = deleteOrder(request);
			responseObserver.onNext(order.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	/**
	 * Delete order from id
	 * @param request
	 * @return
	 */
	private Empty.Builder deleteOrder(DeleteOrderRequest request) {
		int orderId = request.getId();
		if(orderId <= 0) {
			throw new AdempiereException("@FillMandatory@ @C_Order_ID@");
		}
		MOrder order = new MOrder(Env.getCtx(), orderId, null);
		if(order == null || order.getC_Order_ID() <= 0) {
			throw new AdempiereException("@C_Order_ID@ @NotFound@");
		}
		//	Validate drafted
		if(!DocumentUtil.isDrafted(order)) {
			throw new AdempiereException("@C_Order_ID@ @Processed@");
		}
		//	Validate processed Order
		if(order.isProcessed()) {
			throw new AdempiereException("@C_Order_ID@ @Processed@");
		}
		//
		OrderManagement.validateOrderReleased(order);
		order.deleteEx(true);

		//	Return
		return Empty.newBuilder();
	}
	
	/**
	 * Delete payment from uuid
	 * @param request
	 * @return
	 */
	private Empty.Builder deletePayment(DeletePaymentRequest request) {
		int paymentId = request.getId();
		if(paymentId <= 0) {
			throw new AdempiereException("@C_Payment_ID@ @NotFound@");
		}
		MPayment payment = new Query(Env.getCtx(), I_C_Payment.Table_Name, I_C_Payment.COLUMNNAME_C_Payment_ID + " = ?", null)
				.setParameters(paymentId)
				.setClient_ID()
				.first();
		if(payment == null
				|| payment.getC_Payment_ID() == 0) {
			return Empty.newBuilder();
		}
		//	Validate drafted
		if(!DocumentUtil.isDrafted(payment)) {
			throw new AdempiereException("@C_Payment_ID@ @Processed@");
		}
		//	Validate processed Order
		if(payment.isProcessed()) {
			throw new AdempiereException("@C_Payment_ID@ @Processed@");
		}
		//	
		if(payment != null
				&& payment.getC_Payment_ID() >= 0) {
			if(payment.getC_Order_ID() > 0) {
				MOrder salesOrder = new MOrder(Env.getCtx(), payment.getC_Order_ID(), null);
				OrderManagement.validateOrderReleased(salesOrder);
			}
			payment.deleteEx(true);
		}
		//	Return
		return Empty.newBuilder();
	}
	
	/**
	 * Create order line and return this
	 * @param request
	 * @return
	 */
	private OrderLine.Builder updateAndConvertOrderLine(UpdateOrderLineRequest request) {
		//	Validate Order
		int orderLineId = request.getId();
		if(orderLineId <= 0) {
			throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
		}
		MPOS pos = getPOSFromId(request.getPosId(), true);
		//	Quantity
		return ConvertUtil.convertOrderLine(
			updateOrderLine(
				pos,
				orderLineId,
				ValueManager.getBigDecimalFromValue(
					request.getQuantity()
				),
				ValueManager.getBigDecimalFromValue(
					request.getPrice()
				),
				ValueManager.getBigDecimalFromValue(
					request.getDiscountRate()
				),
				request.getIsAddQuantity(),
				request.getWarehouseId(),
				request.getUomId()
			)
		);
	}
	
	/**
	 * Create order line and return this
	 * @param request
	 * @return
	 */
	private OrderLine.Builder createAndConvertOrderLine(CreateOrderLineRequest request) {
		//	Validate Order
		int orderId = request.getOrderId();
		if(orderId <= 0) {
			throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
		}
		//	Validate Product and charge
		if(request.getProductId() <= 0
				&& request.getChargeId() <= 0
				&& request.getResourceAssignmentId() <= 0) {
			throw new AdempiereException("@M_Product_ID@ / @C_Charge_ID@ / @S_ResourceAssignment_ID@ @NotFound@");
		}
		MOrderLine orderLine = null;
		if (request.getResourceAssignmentId() > 0) {
			orderLine = addOrderLineFromResourceAssigment(
				orderId,
				request.getResourceAssignmentId(),
				request.getWarehouseId()
			);
		} else {
			orderLine = addOrderLine(
				orderId,
				request.getProductId(),
				request.getChargeId(),
				request.getWarehouseId(),
				ValueManager.getBigDecimalFromValue(
					request.getQuantity()
				)
			);
		}
		//	Quantity
		return ConvertUtil.convertOrderLine(orderLine);
	}
	
	private MOrderLine addOrderLineFromResourceAssigment(int orderId, int resourceAssignmentId, int warehouseId) {
		if(orderId <= 0) {
			return null;
		}
		//	
		AtomicReference<MOrderLine> orderLineReference = new AtomicReference<MOrderLine>();
		Trx.run(transactionName -> {
			MOrder order = new MOrder(Env.getCtx(), orderId, transactionName);
			//	Valid Complete
			if (!DocumentUtil.isDrafted(order)) {
				throw new AdempiereException("@C_Order_ID@ @Processed@");
			}
			OrderUtil.setCurrentDate(order);
			// catch Exceptions at order.getLines()
			Optional<MOrderLine> maybeOrderLine = Arrays.asList(order.getLines(true, "Line"))
				.stream()
				.filter(orderLine -> {
					return resourceAssignmentId != 0 &&
						resourceAssignmentId == orderLine.getS_ResourceAssignment_ID();
				})
				.findFirst();

			MResourceAssignment resourceAssigment = new MResourceAssignment(Env.getCtx(), resourceAssignmentId, transactionName);
			if (!resourceAssigment.isConfirmed()) {
				resourceAssigment = TimeControl.confirmResourceAssignment(resourceAssignmentId);
			}
			BigDecimal quantity = resourceAssigment.getQty();

			if(maybeOrderLine.isPresent()) {
				MOrderLine orderLine = maybeOrderLine.get();
				//	Set Quantity
				BigDecimal quantityToOrder = quantity;
				if(quantity == null) {
					quantityToOrder = orderLine.getQtyEntered();
					quantityToOrder = quantityToOrder.add(Env.ONE);
				}
				orderLine.setS_ResourceAssignment_ID(resourceAssignmentId);

				OrderUtil.updateUomAndQuantity(orderLine, orderLine.getC_UOM_ID(), quantityToOrder);
				orderLineReference.set(orderLine);
			} else {
				BigDecimal quantityToOrder = quantity;
				if(quantity == null) {
					quantityToOrder = Env.ONE;
				}
				//create new line
				MOrderLine orderLine = new MOrderLine(order);

				MProduct product = new Query(
					order.getCtx(),
					MProduct.Table_Name,
					" S_Resource_ID = ? ",
					null
				)
					.setParameters(resourceAssigment.getS_Resource_ID())
					.first();
				if (product != null && product.getM_Product_ID() > 0) {
					orderLine.setProduct(product);
					orderLine.setC_UOM_ID(product.getC_UOM_ID());
				}
				orderLine.setS_ResourceAssignment_ID(resourceAssignmentId);

				orderLine.setQty(quantityToOrder);
				orderLine.setPrice();
				orderLine.setTax();
				StringBuffer description = new StringBuffer(
					ValueManager.validateNull(
						resourceAssigment.getName()
					)
				);
				if (!Util.isEmpty(resourceAssigment.getDescription())) {
					description.append(" (" + resourceAssigment.getDescription() + ")");
				}
				description.append(": ").append(DisplayType.getDateFormat(DisplayType.DateTime).format(resourceAssigment.getAssignDateFrom()));
				description.append(" ~ ").append(DisplayType.getDateFormat(DisplayType.DateTime).format(resourceAssigment.getAssignDateTo()));
				orderLine.setDescription(description.toString());

				//	Save Line
				orderLine.saveEx(transactionName);
				//	Apply Discount from order
				configureDiscountRateOff(order, (BigDecimal) order.get_Value("FlatDiscount"), transactionName);
				orderLineReference.set(orderLine);
			}
		});
		return orderLineReference.get();
	}
	
	/***
	 * Add order line
	 * @param orderId
	 * @param productId
	 * @param chargeId
	 * @param warehouseId
	 * @param quantity
	 * @return
	 */
	private MOrderLine addOrderLine(int orderId, int productId, int chargeId, int warehouseId, BigDecimal quantity) {
		if(orderId <= 0) {
			return null;
		}
		//	
		AtomicReference<MOrderLine> orderLineReference = new AtomicReference<MOrderLine>();
		Trx.run(transactionName -> {
			MOrder order = new MOrder(Env.getCtx(), orderId, transactionName);
			//	Valid Complete
			if (!DocumentUtil.isDrafted(order)) {
				throw new AdempiereException("@C_Order_ID@ @Processed@");
			}
			OrderUtil.setCurrentDate(order);
			// catch Exceptions at order.getLines()
			Optional<MOrderLine> maybeOrderLine = Arrays.asList(order.getLines(true, "Line"))
					.stream()
					.filter(orderLine -> (productId != 0 && productId == orderLine.getM_Product_ID()) 
							|| (chargeId != 0 && chargeId == orderLine.getC_Charge_ID()))
					.findFirst();
			if(maybeOrderLine.isPresent()) {
				MOrderLine orderLine = maybeOrderLine.get();
				//	Set Quantity
				BigDecimal quantityToOrder = quantity;
				if(quantity == null) {
					quantityToOrder = orderLine.getQtyEntered();
					quantityToOrder = quantityToOrder.add(Env.ONE);
				}
				OrderUtil.updateUomAndQuantity(orderLine, orderLine.getC_UOM_ID(), quantityToOrder);
				orderLineReference.set(orderLine);
			} else {
				BigDecimal quantityToOrder = quantity;
				if(quantity == null) {
					quantityToOrder = Env.ONE;
				}
				//create new line
				MOrderLine orderLine = new MOrderLine(order);
				orderLine.setC_Campaign_ID(order.getC_Campaign_ID());
				if(chargeId > 0) {
					orderLine.setC_Charge_ID(chargeId);
				} else if(productId > 0) {
					orderLine.setProduct(MProduct.get(order.getCtx(), productId));
				}
				orderLine.setQty(quantityToOrder);
				orderLine.setPrice();
				orderLine.setTax();
				//	Save Line
				orderLine.saveEx(transactionName);
				//	Apply Discount from order
				configureDiscountRateOff(order, (BigDecimal) order.get_Value("FlatDiscount"), transactionName);
				orderLineReference.set(orderLine);
			}
		});
		return orderLineReference.get();
			
	} //	addOrUpdateLine

	/**
	 * Validate if a order is released
	 * @param salesOrder
	 * @return void
	 */
	private void validateOrderReleased(MOrder salesOrder) {
		if(salesOrder.get_ValueAsInt("AssignedSalesRep_ID") > 0
				&& salesOrder.get_ValueAsInt("AssignedSalesRep_ID") != Env.getAD_User_ID(Env.getCtx())) {
			throw new AdempiereException("@POS.SalesRepAssigned@");
		}
	}

	/***
	 * Update order line
	 * @param orderLineId
	 * @param quantity
	 * @param price
	 * @param discountRate
	 * @param isAddQuantity
	 * @param warehouseId
	 * @return
	 */
	private MOrderLine updateOrderLine(MPOS pos, int orderLineId, BigDecimal quantity, BigDecimal price, BigDecimal discountRate, boolean isAddQuantity, int warehouseId, int unitOfMeasureId) {
		if(orderLineId <= 0) {
			return null;
		}
		AtomicReference<MOrderLine> maybeOrderLine = new AtomicReference<MOrderLine>();
		Trx.run(transactionName -> {
			MOrderLine orderLine = new MOrderLine(Env.getCtx(), orderLineId, transactionName);
			MOrder order = orderLine.getParent();
			order.set_TrxName(transactionName);
			OrderManagement.validateOrderReleased(order);
			OrderUtil.setCurrentDate(order);
			orderLine.setHeaderInfo(order);
			//	Valid Complete
			if (!DocumentUtil.isDrafted(order)) {
				throw new AdempiereException("@C_Order_ID@ @Processed@");
			}
			int precision = MPriceList.getPricePrecision(Env.getCtx(), order.getM_PriceList_ID());
			BigDecimal quantityToChange = quantity;
			//	Get if is null
			if(quantity != null) {
				if(isAddQuantity) {
					BigDecimal currentQuantity = orderLine.getQtyEntered();
					if(currentQuantity == null) {
						currentQuantity = Env.ZERO;
					}
					quantityToChange = currentQuantity.add(quantityToChange);
				}
				if(orderLine.get_ValueAsInt(ColumnsAdded.COLUMNNAME_ECA14_Source_RMALine_ID) > 0) {
//					if(warehouseId != orderLine.get_ValueAsInt("Ref_WarehouseSource_ID")
//							|| unitOfMeasureId != orderLine.getC_UOM_ID()) {
//						throw new AdempiereException("@ActionNotAllowedHere@");
//					}
					MOrderLine sourceRmaLine = new MOrderLine(Env.getCtx(), orderLine.get_ValueAsInt(ColumnsAdded.COLUMNNAME_ECA14_Source_RMALine_ID), transactionName);
					BigDecimal availableQuantity = OrderUtil.getAvailableQuantityForSell(orderLine.get_ValueAsInt(ColumnsAdded.COLUMNNAME_ECA14_Source_RMALine_ID), orderLineId, sourceRmaLine.getQtyEntered(), quantityToChange);
					if(availableQuantity.compareTo(Env.ZERO) > 0) {
						//	Update order quantity
						quantityToChange = availableQuantity;
					} else {
						throw new AdempiereException("@QtyInsufficient@");
					}
				}
				orderLine.setQty(quantityToChange);
			}
			//	Calculate discount from final price
			if(price != null
					|| discountRate != null) {
				if(orderLine.get_ValueAsInt(ColumnsAdded.COLUMNNAME_ECA14_Source_RMALine_ID) > 0) {
					throw new AdempiereException("@ActionNotAllowedHere@");
				}
				// TODO: Verify with Price Entered/Actual
				BigDecimal priceToOrder = orderLine.getPriceActual();
				BigDecimal discountRateToOrder = Env.ZERO;
				if (price != null) {
					priceToOrder = price;
					discountRateToOrder = getDiscount(orderLine.getPriceList(), price, precision);
				}
				//	Calculate final price from discount
				if (discountRate != null) {
					discountRateToOrder = discountRate;
					priceToOrder = getFinalPrice(orderLine.getPriceList(), discountRate, precision);
					priceToOrder = MUOMConversion.convertProductFrom(orderLine.getCtx(), orderLine.getM_Product_ID(), orderLine.getC_UOM_ID(), priceToOrder);
				}
				orderLine.setDiscount(discountRateToOrder);
				orderLine.setPrice(priceToOrder);
			}
			//	
			if(warehouseId > 0) {
//				orderLine.setM_Warehouse_ID(warehouseId);
				orderLine.set_ValueOfColumn("Ref_WarehouseSource_ID", warehouseId);
			}
			//	Validate UOM
			OrderUtil.updateUomAndQuantity(orderLine, unitOfMeasureId, quantityToChange);
			//	Set values
			orderLine.setTax();
			orderLine.saveEx(transactionName);
			//	Apply Discount from order
			configureDiscountRateOff(order, (BigDecimal) order.get_Value("FlatDiscount"), transactionName);
			maybeOrderLine.set(orderLine);
		});
		return maybeOrderLine.get();
	} //	UpdateLine
	
	/**
	 * Set UOM and Quantity based on unit of measure
	 * @param orderLine
	 * @param unitOfMeasureId
	 * @param quantity
	 */
	private void updateUomAndQuantity(MOrderLine orderLine, int unitOfMeasureId, BigDecimal quantity) {
		if(quantity != null) {
			orderLine.setQty(quantity);
		}
		if(unitOfMeasureId > 0) {
			orderLine.setC_UOM_ID(unitOfMeasureId);
		}
		BigDecimal quantityEntered = orderLine.getQtyEntered();
		BigDecimal priceEntered = orderLine.getPriceEntered();
		BigDecimal convertedQuantity = MUOMConversion.convertProductFrom(orderLine.getCtx(), orderLine.getM_Product_ID(), orderLine.getC_UOM_ID(), quantityEntered);
		BigDecimal convertedPrice = MUOMConversion.convertProductTo(orderLine.getCtx(), orderLine.getM_Product_ID(), orderLine.getC_UOM_ID(), priceEntered);
		orderLine.setQtyOrdered(convertedQuantity);
		orderLine.setPriceEntered(convertedPrice);
		orderLine.setLineNetAmt();
		orderLine.saveEx();
	}

	/**
	 * Set UOM and Quantity based on unit of measure
	 * @param inOutLine
	 * @param unitOfMeasureId
	 * @param quantity
	 */
	private void updateUomAndQuantityForShipment(MInOutLine inOutLine, int unitOfMeasureId, BigDecimal quantity) {
		if(quantity != null) {
			inOutLine.setQty(quantity);
		}
		if(unitOfMeasureId > 0) {
			inOutLine.setC_UOM_ID(unitOfMeasureId);
		}
		BigDecimal quantityEntered = inOutLine.getQtyEntered();
		BigDecimal convertedQuantity = MUOMConversion.convertProductFrom(inOutLine.getCtx(), inOutLine.getM_Product_ID(), inOutLine.getC_UOM_ID(), quantityEntered);
		inOutLine.setMovementQty(convertedQuantity);
	}



	@Override
	public void listPointOfSales(ListPointOfSalesRequest request, StreamObserver<ListPointOfSalesResponse> responseObserver) {
		try {
			ListPointOfSalesResponse.Builder posList = listPointOfSales(request);
			responseObserver.onNext(posList.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	/**
	 * Get list from user
	 * @param request
	 * @return
	 */
	private ListPointOfSalesResponse.Builder listPointOfSales(ListPointOfSalesRequest request) {
		int salesRepresentativeId = Env.getAD_User_ID(Env.getCtx());

		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Get POS List
		boolean isAppliedNewFeaturesPOS = M_Element.get(Env.getCtx(), "IsSharedPOS") != null && M_Element.get(Env.getCtx(), "IsAllowsAllocateSeller") != null;
		StringBuffer whereClause = new StringBuffer("SalesRep_ID = ? ");
		List<Object> parameters = new ArrayList<>();
		parameters.add(salesRepresentativeId);
		//	applies for Shared pos
		if(isAppliedNewFeaturesPOS) {
			//	Shared POS
			whereClause.append(" OR (AD_Org_ID = ? AND IsSharedPOS = 'Y' AND IsAllowsAllocateSeller = 'N')");
			//	Allocation by Seller Allocation table
			whereClause.append(" OR (IsAllowsAllocateSeller = 'Y' AND EXISTS(SELECT 1 FROM C_POSSellerAllocation sa WHERE sa.C_POS_ID = C_POS.C_POS_ID AND sa.SalesRep_ID = ? AND sa.IsActive = 'Y'))");
			parameters.add(Env.getAD_Org_ID(Env.getCtx()));
			parameters.add(salesRepresentativeId);
		}
		Query query = new Query(Env.getCtx() , I_C_POS.Table_Name , whereClause.toString(), null)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setParameters(parameters)
				.setOrderBy(I_C_POS.COLUMNNAME_Name);
		int count = query.count();
		ListPointOfSalesResponse.Builder builder = ListPointOfSalesResponse.newBuilder()
			.setRecordCount(count)
		;

		query
			.setLimit(limit, offset)
			.<MPOS>list()
			.forEach(pos -> {
				PointOfSales.Builder posBuilder = convertPointOfSales(pos);
				builder.addSellingPoints(posBuilder);
			});
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}



	@Override
	public void getPointOfSales(PointOfSalesRequest request, StreamObserver<PointOfSales> responseObserver) {
		try {
			PointOfSales.Builder pos = getPosBuilder(request);
			responseObserver.onNext(pos.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	/**
	 * Get POS builder
	 * @param context
	 * @param request
	 * @return
	 */
	private PointOfSales.Builder getPosBuilder(PointOfSalesRequest request) {
		return convertPointOfSales(
			getPOSFromId(request.getId(), true)
		);
	}

	/**
	 * Get POS from UUID
	 * @param uuid
	 * @return
	 */
	private MPOS getPOSFromId(int posId, boolean requery) {
		if(posId <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		if(requery) {
			return new MPOS(Env.getCtx(), posId, null);
		}
		return MPOS.get(Env.getCtx(), posId);
	}
	
	/**
	 * Convert POS
	 * @param pos
	 * @return
	 */
	private PointOfSales.Builder convertPointOfSales(MPOS pos) {
		PointOfSales.Builder builder = PointOfSales.newBuilder()
			.setId(pos.getC_POS_ID())
			.setName(
				ValueManager.validateNull(pos.getName())
			)
			.setDescription(
				ValueManager.validateNull(pos.getDescription())
			)
			.setHelp(
				ValueManager.validateNull(pos.getHelp())
			)
			.setIsModifyPrice(pos.isModifyPrice())
			.setIsPosRequiredPin(pos.isPOSRequiredPIN())
			.setSalesRepresentative(ConvertUtil.convertSalesRepresentative(MUser.get(pos.getCtx(), pos.getSalesRep_ID())))
			.setTemplateCustomer(
				POSConvertUtil.convertCustomer(
					pos.getBPartner()
				)
			)
			.setKeyLayoutId(pos.getC_POSKeyLayout_ID())
			.setIsAisleSeller(pos.get_ValueAsBoolean("IsAisleSeller"))
			.setIsSharedPos(pos.get_ValueAsBoolean("IsSharedPOS"))
			.setConversionTypeId(pos.get_ValueAsInt(I_C_ConversionType.COLUMNNAME_C_ConversionType_ID))
		;

		int userId = Env.getAD_User_ID(pos.getCtx());
		//	Special values
		builder
			.setMaximumRefundAllowed(
				ValueManager.getValueFromBigDecimal(
					getBigDecimalValueFromPOS(
						pos,
						userId,
						ColumnsAdded.COLUMNNAME_MaximumRefundAllowed
					)
				)
			)
			.setMaximumDailyRefundAllowed(
				ValueManager.getValueFromBigDecimal(
					getBigDecimalValueFromPOS(
						pos,
						userId,
						ColumnsAdded.COLUMNNAME_MaximumDailyRefundAllowed
					)
				)
			)
			.setMaximumDiscountAllowed(
				ValueManager.getValueFromBigDecimal(
					getBigDecimalValueFromPOS(
						pos,
						userId,
						ColumnsAdded.COLUMNNAME_MaximumDiscountAllowed
					)
				)
			)
			.setMaximumLineDiscountAllowed(
				ValueManager.getValueFromBigDecimal(
					getBigDecimalValueFromPOS(
						pos,
						userId,
						ColumnsAdded.COLUMNNAME_MaximumLineDiscountAllowed
					)
				)
			)
			.setWriteOffAmountTolerance(
				ValueManager.getValueFromBigDecimal(
					getWriteOffAmtTolerance(pos)
				)
			)
			.setWriteOffPercentageTolerance(
				ValueManager.getValueFromBigDecimal(
					getBigDecimalValueFromPOS(
						pos,
						userId,
						ColumnsAdded.COLUMNNAME_MaximumLineDiscountAllowed
					)
				)
			)
			.setIsAllowsModifyQuantity(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsModifyQuantity))
			.setIsAllowsReturnOrder(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsReturnOrder))
			.setIsAllowsCollectOrder(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsCollectOrder))
			.setIsAllowsCreateOrder(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsCreateOrder))
			.setIsDisplayTaxAmount(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsDisplayTaxAmount))
			.setIsDisplayDiscount(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsDisplayDiscount))
			.setIsAllowsConfirmShipment(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsConfirmShipment))
			.setIsConfirmCompleteShipment(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsConfirmCompleteShipment))
			.setIsAllowsAllocateSeller(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsAllocateSeller))
			.setIsAllowsConcurrentUse(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsConcurrentUse))
			.setIsAllowsCashOpening(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsCashOpening))
			.setIsAllowsCashClosing(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsCashClosing))
			.setIsAllowsCashWithdrawal(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsCashWithdrawal))
			.setIsAllowsApplyDiscount(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsApplyDiscount))
			.setIsAllowsCreateCustomer(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsCreateCustomer))
			.setIsAllowsModifyCustomer(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsModifyCustomer))
			.setIsAllowsPrintDocument(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsPrintDocument))
			.setIsAllowsPreviewDocument(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsPreviewDocument))
			.setIsAllowsModifyDiscount(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsModifyDiscount))
			.setIsKeepPriceFromCustomer(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsKeepPriceFromCustomer))
			.setIsModifyPrice(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsModifyPrice))
			.setIsAllowsDetailCashClosing(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_IsAllowsDetailCashClosing))
			.setIsWriteOffByPercent(getBooleanValueFromPOS(pos, userId, ColumnsAdded.COLUMNNAME_ECA14_WriteOffByPercent))
		;

		if(pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_RefundReferenceCurrency_ID) > 0) {
			builder.setRefundReferenceCurrency(
				ConvertCommon.convertCurrency(
					pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_RefundReferenceCurrency_ID)
				)
			);
		}
		//	Set Price List and currency
		if(pos.getM_PriceList_ID() != 0) {
			MPriceList priceList = MPriceList.get(Env.getCtx(), pos.getM_PriceList_ID(), null);
			builder.setPriceList(ConvertUtil.convertPriceList(priceList));
		}
		//	Bank Account
		if(pos.getC_BankAccount_ID() != 0) {
			MBankAccount cashAccount = MBankAccount.get(Env.getCtx(), pos.getC_BankAccount_ID());
			builder.setDefaultOpeningChargeId(cashAccount.get_ValueAsInt("DefaultOpeningCharge_ID"))
				.setDefaultWithdrawalChargeId(cashAccount.get_ValueAsInt("DefaultWithdrawalCharge_ID"))
				.setCashBankAccount(
					ConvertCommon.convertBankAccount(cashAccount)
				);
		}
		//	Bank Account to transfer
		if(pos.getCashTransferBankAccount_ID() != 0) {
			builder.setCashBankAccount(
				ConvertCommon.convertBankAccount(
					pos.getCashTransferBankAccount_ID()
				)
			);
		}
		//	Warehouse
		if(pos.getM_Warehouse_ID() > 0) {
			MWarehouse warehouse = MWarehouse.get(Env.getCtx(), pos.getM_Warehouse_ID());
			builder.setWarehouse(ConvertUtil.convertWarehouse(warehouse));
		}
		//	Price List
		if(pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DisplayCurrency_ID) > 0) {
			builder.setDisplayCurrency(
				ConvertCommon.convertCurrency(
					pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DisplayCurrency_ID)
				)
			);
		}
		//	Document Type
		if(pos.getC_DocType_ID() > 0) {
			builder.setDocumentType(ConvertUtil.convertDocumentType(MDocType.get(Env.getCtx(), pos.getC_DocType_ID())));
		}
		//	Return Document Type
		if(pos.get_ValueAsInt("C_DocTypeRMA_ID") > 0) {
			builder.setReturnDocumentType(ConvertUtil.convertDocumentType(MDocType.get(Env.getCtx(), pos.get_ValueAsInt("C_DocTypeRMA_ID"))));
		}
		// Campaign
		if (pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultCampaign_ID) > 0) {
			builder.setDefaultCampaign(
				POSConvertUtil.convertCampaign(
					pos.get_ValueAsInt(ColumnsAdded.COLUMNNAME_DefaultCampaign_ID)
				)
			);
		}
		
		return builder;
	}
	
	/**
	 * Validate if is allowed user
	 * @param context
	 * @param userId
	 * @param transactionName
	 * @return
	 */
	public static PO getUserAllowed(Properties context, int posId, int userId, String transactionName) {
		return new Query(context, "C_POSSellerAllocation", "C_POS_ID = ?  AND SalesRep_ID = ?", transactionName)
			.setParameters(posId, userId)
			.setOnlyActiveRecords(true)
			.setClient_ID()
			.first();
	}
	
	/**
	 * Create Order from request
	 * @param context
	 * @param request
	 * @return
	 */
	private Order.Builder createOrder(CreateOrderRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @IsMandatory@");
		}
		if(request.getSalesRepresentativeId() <= 0) {
			throw new AdempiereException("@SalesRep_ID@ @IsMandatory@");
		}
		AtomicReference<MOrder> maybeOrder = new AtomicReference<MOrder>();
		Trx.run(transactionName -> {
			MPOS pos = getPOSFromId(request.getPosId(), true);
			StringBuffer whereClause = new StringBuffer("DocStatus = 'DR' "
					+ "AND C_POS_ID = ? "
					+ "AND NOT EXISTS(SELECT 1 "
					+ "					FROM C_OrderLine ol "
					+ "					WHERE ol.C_Order_ID = C_Order.C_Order_ID)");
			//	
			List<Object> parameters = new ArrayList<Object>();
			parameters.add(pos.getC_POS_ID());
			if(pos.get_ValueAsBoolean("IsSharedPOS")) {
				whereClause.append(" AND SalesRep_ID = ?");
				parameters.add(request.getSalesRepresentativeId());
			}
			//	Allocation by Seller Allocation table
			MOrder salesOrder = new Query(Env.getCtx(), I_C_Order.Table_Name, 
					whereClause.toString(), transactionName)
					.setParameters(parameters)
					.first();
			//	Validate
			if(salesOrder == null) {
				salesOrder = new MOrder(Env.getCtx(), 0, transactionName);
			} else {
				salesOrder.setDateOrdered(RecordUtil.getDate());
				salesOrder.setDateAcct(RecordUtil.getDate());
				salesOrder.setDatePromised(RecordUtil.getDate());
			}
			//	Set campaign
			//	Default values
			salesOrder.setIsSOTrx(true);
			salesOrder.setAD_Org_ID(pos.getAD_Org_ID());
			salesOrder.setC_POS_ID(pos.getC_POS_ID());
			//	Warehouse
			int warehouseId = pos.getM_Warehouse_ID();
			if(request.getWarehouseId() > 0) {
				warehouseId = request.getWarehouseId();
			}
			//	From POS
			if(warehouseId < 0) {
				warehouseId = pos.getM_Warehouse_ID();
			}
			//	Price List
			int priceListId = pos.getM_PriceList_ID();
			if(request.getPriceListId() > 0) {
				priceListId = request.getPriceListId();
			}
			//	Price List From POS
			if(priceListId < 0) {
				priceListId = pos.getM_PriceList_ID();
			}
			salesOrder.setM_PriceList_ID(priceListId);
			salesOrder.setM_Warehouse_ID(warehouseId);
			//	Document Type
			int documentTypeId = 0;
			if(request.getDocumentTypeId() > 0) {
				documentTypeId = request.getDocumentTypeId();
			}
			//	Validate
			if(documentTypeId <= 0
					&& pos.getC_DocType_ID() != 0) {
				documentTypeId = pos.getC_DocType_ID();
			}
			//	Validate
			if(documentTypeId > 0) {
				salesOrder.setC_DocTypeTarget_ID(documentTypeId);
			} else {
				salesOrder.setC_DocTypeTarget_ID(MOrder.DocSubTypeSO_POS);
			}
			//	Delivery Rules
			if (pos.getDeliveryRule() != null) {
				salesOrder.setDeliveryRule(pos.getDeliveryRule());
			}
			//	Invoice Rule
			if (pos.getInvoiceRule() != null) {
				salesOrder.setInvoiceRule(pos.getInvoiceRule());
			}
			//	Conversion Type
			if(pos.get_ValueAsInt(MOrder.COLUMNNAME_C_ConversionType_ID) > 0) {
				salesOrder.setC_ConversionType_ID(pos.get_ValueAsInt(MOrder.COLUMNNAME_C_ConversionType_ID));
			}
			int campaignId = request.getCampaignId();
			if(campaignId > 0 && campaignId != salesOrder.getC_Campaign_ID()) {
				salesOrder.setC_Campaign_ID(campaignId);
			}
			//	Set business partner
			setBPartner(pos, salesOrder, request.getCustomerId(), request.getSalesRepresentativeId(), transactionName);
			maybeOrder.set(salesOrder);
		});
		//	Convert order
		return ConvertUtil.convertOrder(maybeOrder.get());
	}
	
	/**
	 * Set current date to order
	 * @param salesOrder
	 * @return void
	 */
	private void setCurrentDate(MOrder salesOrder) {
		//	Ignore if the document is processed
		if(salesOrder.isProcessed() || salesOrder.isProcessing()) {
			return;
		}
		if(!salesOrder.getDateOrdered().equals(RecordUtil.getDate())
				|| !salesOrder.getDateAcct().equals(RecordUtil.getDate())) {
			salesOrder.setDateOrdered(RecordUtil.getDate());
			salesOrder.setDateAcct(RecordUtil.getDate());
			salesOrder.saveEx();
		}
	}

	/**
	 * Set current date to order
	 * @param payment
	 * @return void
	 */
	private void setCurrentDate(MPayment payment) {
		//	Ignore if the document is processed
		if(payment.isProcessed() || payment.isProcessing()) {
			return;
		}
		if(!payment.getDateTrx().equals(RecordUtil.getDate())) {
			payment.setDateTrx(RecordUtil.getDate());
			payment.saveEx();
		}
	}

	/**
	 * Set business partner from uuid
	 * @param pos
	 * @param salesOrder
	 * @param businessPartnerUuid
	 * @param salesRepresentativeUuid
	 * @param transactionName
	 */
	private void setBPartner(MPOS pos, MOrder salesOrder, int businessPartnerId, int salesRepresentativeId, String transactionName) {
		//	Valid if has a Order
		if(DocumentUtil.isCompleted(salesOrder)
				|| DocumentUtil.isVoided(salesOrder)) {
			return;
		}
		//	Get BP
		MBPartner businessPartner = null;
		if(businessPartnerId > 0) {
			businessPartner = MBPartner.get(Env.getCtx(), businessPartnerId);
		}
		boolean isSamePOSPartner = false;
		if(businessPartner == null) {
			businessPartner = pos.getBPartner();
			isSamePOSPartner = true;
		}
		//	Validate business partner
		if(businessPartner == null) {
			throw new AdempiereException("@C_BPartner_ID@ @NotFound@");
		}
		log.fine( "CPOS.setC_BPartner_ID=" + businessPartner.getC_BPartner_ID());
		businessPartner.set_TrxName(transactionName);
		salesOrder.setBPartner(businessPartner);
		boolean isKeepPriceListCustomer = getBooleanValueFromPOS(pos, businessPartnerId, ColumnsAdded.COLUMNNAME_IsKeepPriceFromCustomer);
		if(!isKeepPriceListCustomer && businessPartner.getM_PriceList_ID() > 0) {
			MPriceList businesPartnerPriceList = MPriceList.get(salesOrder.getCtx(), businessPartner.getM_PriceList_ID(), transactionName);
			MPriceList currentPriceList = MPriceList.get(salesOrder.getCtx(), pos.getM_PriceList_ID(), transactionName);
			if(currentPriceList.getC_Currency_ID() != businesPartnerPriceList.getC_Currency_ID()) {
				salesOrder.setM_PriceList_ID(currentPriceList.getM_PriceList_ID());
			}
		}
		//	
		MBPartnerLocation [] partnerLocations = businessPartner.getLocations(true);
		if(partnerLocations.length > 0) {
			for(MBPartnerLocation partnerLocation : partnerLocations) {
				if(partnerLocation.isBillTo())
					salesOrder.setBill_Location_ID(partnerLocation.getC_BPartner_Location_ID());
				if(partnerLocation.isShipTo())
					salesOrder.setShip_Location_ID(partnerLocation.getC_BPartner_Location_ID());
			}				
		}
		//	Validate Same BPartner
		if(isSamePOSPartner) {
			if(salesOrder.getPaymentRule() == null) {
				salesOrder.setPaymentRule(MOrder.PAYMENTRULE_Cash);
			}
		}
		//	Set Sales Representative
		if(salesRepresentativeId <= 0) {
			MUser currentUser = MUser.get(salesOrder.getCtx());
			PO sellerSupervisor = new Query(
				Env.getCtx(),
				"C_POSSellerAllocation",
				"C_POS_ID = ? AND SalesRep_ID = ? AND IsAllowsPOSManager='Y'",
				transactionName
			).setParameters(pos.getC_POS_ID(), currentUser.getAD_User_ID())
			.first();

			if (pos.get_ValueAsBoolean("IsSharedPOS")) {
				salesRepresentativeId = currentUser.getAD_User_ID();
			} else if (sellerSupervisor != null) {
				salesRepresentativeId = sellerSupervisor.get_ValueAsInt("SalesRep_ID");
			} else if (businessPartner.getSalesRep_ID() != 0) {
				salesRepresentativeId = salesOrder.getC_BPartner().getSalesRep_ID();
			} else {
				salesRepresentativeId = pos.getSalesRep_ID();
			}
		}
		//	Set
		if(salesRepresentativeId > 0) {
			salesOrder.setSalesRep_ID(salesRepresentativeId);
			if(salesOrder.get_ValueAsInt("AssignedSalesRep_ID") <= 0) {
				salesOrder.set_ValueOfColumn("AssignedSalesRep_ID", salesRepresentativeId);
			}
		}
		OrderUtil.setCurrentDate(salesOrder);
		//	Save Header
		salesOrder.saveEx();
		//	Load Price List Version
		MPriceList priceList = MPriceList.get(Env.getCtx(), salesOrder.getM_PriceList_ID(), transactionName);
		//
		MPriceListVersion priceListVersion = priceList.getPriceListVersion (RecordUtil.getDate());
		List<MProductPrice> productPrices = Arrays.asList(priceListVersion.getProductPrice(" AND EXISTS("
				+ "SELECT 1 "
				+ "FROM C_OrderLine ol "
				+ "WHERE ol.C_Order_ID = " + salesOrder.getC_Order_ID() + " "
				+ "AND ol.M_Product_ID = M_ProductPrice.M_Product_ID)"));
		//	Update Lines
		Arrays.asList(salesOrder.getLines())
			.forEach(orderLine -> {
				//	Verify if exist
				if(productPrices
					.stream()
					.filter(productPrice -> productPrice.getM_Product_ID() == orderLine.getM_Product_ID())
					.findFirst()
					.isPresent()) {
					orderLine.setC_BPartner_ID(businessPartnerId);
					orderLine.setC_BPartner_Location_ID(salesOrder.getC_BPartner_Location_ID());
					orderLine.setPrice();
					orderLine.setTax();
					orderLine.saveEx();
				} else {
					orderLine.deleteEx(true);
				}
			});
	}

	/**
	 * Update payment if is required
	 * @param request
	 * @return
	 */
	private MPayment updatePayment(UpdatePaymentRequest request) {
		AtomicReference<MPayment> maybePayment = new AtomicReference<MPayment>();
		Trx.run(transactionName -> {
			String tenderType = request.getTenderTypeCode();
			int paymentId = request.getId();
			if(paymentId <= 0) {
				throw new AdempiereException("@C_Payment_ID@ @NotFound@");
			}
			MPayment payment = new MPayment(Env.getCtx(), paymentId, transactionName);
			if(!DocumentUtil.isDrafted(payment)) {
				throw new AdempiereException("@C_Payment_ID@ @Processed@");
			}
			if(payment.getC_Order_ID() > 0) {
				MOrder salesOrder = new MOrder(Env.getCtx(), payment.getC_Order_ID(), transactionName);
				OrderManagement.validateOrderReleased(salesOrder);
			}
			if(!Util.isEmpty(tenderType)) {
				payment.setTenderType(tenderType);
			}
			if(ValueManager.getDateFromTimestampDate(request.getPaymentDate()) != null) {
				Timestamp date = ValueManager.getDateFromTimestampDate(
					request.getPaymentDate()
				);
	        	if(date != null) {
	        		payment.setDateTrx(date);
	        	}
	        }
			if(ValueManager.getDateFromTimestampDate(request.getPaymentAccountDate()) != null) {
				Timestamp date = ValueManager.getDateFromTimestampDate(
					request.getPaymentAccountDate()
				);
	        	if(date != null) {
	        		payment.setDateAcct(date);
	        	}
	        }
			//	Set Bank Id
			if(request.getBankId() > 0) {
				payment.set_ValueOfColumn(MBank.COLUMNNAME_C_Bank_ID, request.getBankId());
			}
			//	Validate reference
			if(!Util.isEmpty(request.getReferenceNo())) {
				payment.addDescription(request.getReferenceNo());
			}
			//	Set Description
			if(!Util.isEmpty(request.getDescription())) {
				payment.addDescription(request.getDescription());
			}
			//	Amount
			BigDecimal paymentAmount = ValueManager.getBigDecimalFromValue(
				request.getAmount()
			);
	        payment.setPayAmt(paymentAmount);
	        payment.setOverUnderAmt(Env.ZERO);
	        CashUtil.setCurrentDate(payment);
			payment.saveEx(transactionName);
			maybePayment.set(payment);
		});
		//	Return payment
		return maybePayment.get();
	}

	/**
	 * Create Related Payment from payment
	 * @param pointOfSalesDefinition
	 * @param sourcePayment
	 * @param transactionName
	 * @return
	 */
	private MPayment createRelatedPayment(MPOS pointOfSalesDefinition, MPayment sourcePayment, String transactionName) {
		if(sourcePayment.get_ValueAsInt("POSReferenceBankAccount_ID") <= 0) {
			return null;
		}
		MPayment relatedPayment = new MPayment(Env.getCtx(), 0, transactionName);
		PO.copyValues(sourcePayment, relatedPayment);
		//
		relatedPayment.setAD_Org_ID(pointOfSalesDefinition.getAD_Org_ID());
		relatedPayment.set_ValueOfColumn("POSReferenceBankAccount_ID", null);
		relatedPayment.setC_BankAccount_ID(sourcePayment.get_ValueAsInt("POSReferenceBankAccount_ID"));
		relatedPayment.setRelatedPayment_ID(sourcePayment.getC_Payment_ID());
		int documentTypeId;
		if(!sourcePayment.isReceipt()) {
			documentTypeId = pointOfSalesDefinition.get_ValueAsInt("POSDepositDocumentType_ID");
		} else {
			documentTypeId = pointOfSalesDefinition.get_ValueAsInt("POSWithdrawalDocumentType_ID");
		}
		if(documentTypeId > 0) {
			relatedPayment.setC_DocType_ID(documentTypeId);
		} else {
			relatedPayment.setC_DocType_ID(!sourcePayment.isReceipt());
		}
		relatedPayment.saveEx();
		sourcePayment.setRelatedPayment_ID(relatedPayment.getC_Payment_ID());
		sourcePayment.saveEx();
		return relatedPayment;
	}

	/**
	 * create Payment
	 * @param request
	 * @return
	 */
	private MPayment createPayment(CreatePaymentRequest request) {
		AtomicReference<MPayment> maybePayment = new AtomicReference<MPayment>();
		Trx.run(transactionName -> {
			MPOS pos = getPOSFromId(request.getPosId(), true);
			if(request.getOrderId() > 0) {
				MOrder salesOrder = getOrder(request.getOrderId(), transactionName);
				maybePayment.set(CollectingManagement.createPaymentFromOrder(salesOrder, request, pos, transactionName));
			} else if(request.getChargeId() > 0) {
				maybePayment.set(CashManagement.createPaymentFromCharge(request.getChargeId(), request, pos, transactionName));
			} else {
				throw new AdempiereException("@C_Charge_ID@ / @C_Order_ID@ @NotFound@");
			}
		});
		//	Return payment
		return maybePayment.get();
	}
	
	/**
	 * Get Product Price Method
	 * @param context
	 * @param request
	 * @return
	 */
	private ListProductPriceResponse.Builder getProductPriceList(ListProductPriceRequest request) {
		ListProductPriceResponse.Builder builder = ListProductPriceResponse.newBuilder();
		MPOS pos = getPOSFromId(request.getPosId(), true);
		//	Validate Price List
		int priceListId = pos.getM_PriceList_ID();
		if(request.getPriceListId() > 0) {
			priceListId = request.getPriceListId();
		}
		MPriceList priceList = MPriceList.get(Env.getCtx(), priceListId, null);
		//	Get Valid From
		AtomicReference<Timestamp> validFrom = new AtomicReference<>();
		if(!Util.isEmpty(request.getValidFrom())) {
			validFrom.set(
				ValueManager.getTimestampFromString(
					request.getValidFrom()
				)
			);
		} else {
			validFrom.set(TimeUtil.getDay(System.currentTimeMillis()));
		}
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Dynamic where clause
		StringBuffer whereClause = new StringBuffer();
		//	Parameters
		List<Object> parameters = new ArrayList<Object>();
		//	For search value
		if(!Util.isEmpty(request.getSearchValue())) {
			whereClause.append("IsSold = 'Y' "
				+ "AND ("
				+ "UPPER(Value) LIKE '%' || UPPER(?) || '%'"
				+ "OR UPPER(Name) LIKE '%' || UPPER(?) || '%'"
				+ "OR UPPER(UPC) = UPPER(?)"
				+ "OR UPPER(SKU) = UPPER(?)"
				+ ")");
			//	Add parameters
			parameters.add(request.getSearchValue());
			parameters.add(request.getSearchValue());
			parameters.add(request.getSearchValue());
			parameters.add(request.getSearchValue());
		} 
		//	for price list
		if(whereClause.length() > 0) {
			whereClause.append(" AND ");
		}
		//	Add Price List
		whereClause.append("(EXISTS(SELECT 1 FROM M_PriceList_Version plv "
				+ "INNER JOIN M_ProductPrice pp ON(pp.M_PriceList_Version_ID = plv.M_PriceList_Version_ID) "
				+ "WHERE plv.M_PriceList_ID = ? "
				+ "AND plv.ValidFrom <= ? "
				+ "AND plv.IsActive = 'Y' "
				+ "AND pp.PriceList IS NOT NULL AND pp.PriceList > 0 "
				+ "AND pp.PriceStd IS NOT NULL AND pp.PriceStd > 0 "
				+ "AND pp.M_Product_ID = M_Product.M_Product_ID))");
		//	Add parameters
		parameters.add(priceList.getM_PriceList_ID());
		parameters.add(TimeUtil.getDay(validFrom.get()));
		AtomicInteger warehouseId = new AtomicInteger(pos.getM_Warehouse_ID());
		if(request.getWarehouseId() > 0) {
			warehouseId.set(request.getWarehouseId());
		}
		int businessPartnerId = request.getBusinessPartnerId();
		int displayCurrencyId = pos.get_ValueAsInt("DisplayCurrency_ID");
		int conversionTypeId = pos.get_ValueAsInt("C_ConversionType_ID");
		//	Get Product list
		Query query = new Query(Env.getCtx(), I_M_Product.Table_Name, 
				whereClause.toString(), null)
				.setParameters(parameters)
				.setClient_ID()
				.setOnlyActiveRecords(true);
		int count = query.count();
		query
		.setLimit(limit, offset)
		.<MProduct>list()
		.forEach(product -> {
			ProductPrice.Builder productPrice = convertProductPrice(
					product, 
					businessPartnerId, 
					priceList, 
					warehouseId.get(), 
					validFrom.get(),
					displayCurrencyId,
					conversionTypeId,
					null);
			if(productPrice.hasPriceList()
					&& productPrice.hasPriceStandard()
					&& productPrice.hasPriceLimit()) {
				builder.addProductPrices(productPrice);
			}
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}
	
	/**
	 * Get 
	 * @param product
	 * @param businessPartnerId
	 * @param priceList
	 * @param warehouseId
	 * @param validFrom
	 * @param quantity
	 * @return
	 */
	private ProductPrice.Builder convertProductPrice(MProduct product, int businessPartnerId, MPriceList priceList, int warehouseId, Timestamp validFrom, int displayCurrencyId, int conversionTypeId, BigDecimal priceQuantity) {
		ProductPrice.Builder builder = ProductPrice.newBuilder();
		//	Get Price
		MProductPricing productPricing = new MProductPricing(product.getM_Product_ID(), businessPartnerId, priceQuantity, true, null);
		productPricing.setM_PriceList_ID(priceList.getM_PriceList_ID());
		productPricing.setPriceDate(validFrom);
		builder.setProduct(ConvertUtil.convertProduct(product));
		BigDecimal priceStd = productPricing.getPriceStd();
		int taxCategoryId = product.getC_TaxCategory_ID();
		Optional<MTax> optionalTax = Arrays.asList(MTax.getAll(Env.getCtx()))
		.stream()
		.filter(tax -> tax.getC_TaxCategory_ID() == taxCategoryId 
							&& (tax.isSalesTax() 
									|| (!Util.isEmpty(tax.getSOPOType()) 
											&& (tax.getSOPOType().equals(MTax.SOPOTYPE_Both) 
													|| tax.getSOPOType().equals(MTax.SOPOTYPE_SalesTax)))))
		.findFirst();
		//	Validate
		if(optionalTax.isPresent()) {
			builder.setTaxRate(ConvertUtil.convertTaxRate(optionalTax.get()));
		}
		//	Set currency
		builder.setCurrency(
			ConvertCommon.convertCurrency(
				priceList.getC_Currency_ID()
			)
		);
		//	Price List Attributes
		builder.setIsTaxIncluded(priceList.isTaxIncluded())
			.setValidFrom(
				ValueManager.validateNull(
					ValueManager.convertDateToString(
						productPricing.getPriceDate()
					)
				)
			)
			.setPriceListName(
				ValueManager.validateNull(
					priceList.getName()
				)
			)
		;
		//	Pricing
		builder.setPricePrecision(productPricing.getPrecision());
		//	Prices
		if(Optional.ofNullable(productPricing.getPriceStd()).orElse(Env.ZERO).signum() > 0) {
			builder.setPriceList(
					ValueManager.getValueFromBigDecimal(
						Optional.ofNullable(productPricing.getPriceList()).orElse(Env.ZERO)
					)
				)
				.setPriceStandard(
					ValueManager.getValueFromBigDecimal(
						Optional.ofNullable(priceStd).orElse(Env.ZERO)
					)
				)
				.setPriceLimit(
					ValueManager.getValueFromBigDecimal(
						Optional.ofNullable(
							productPricing.getPriceLimit()
						).orElse(Env.ZERO)
					)
				)
			;
			//	Get from schema
			if(displayCurrencyId > 0) {
				builder.setDisplayCurrency(
					ConvertCommon.convertCurrency(
						displayCurrencyId
					)
				);
				//	Get
				int conversionRateId = MConversionRate.getConversionRateId(priceList.getC_Currency_ID(), displayCurrencyId, RecordUtil.getDate(), conversionTypeId, Env.getAD_Client_ID(Env.getCtx()), Env.getAD_Org_ID(Env.getCtx()));
				if(conversionRateId > 0) {
					//	TODO: cache or re-query should be resolved
					MConversionRate conversionRate = MConversionRate.get(Env.getCtx(), conversionRateId);
					if(conversionRate != null) {
						BigDecimal multiplyRate = conversionRate.getMultiplyRate();
						builder.setDisplayPriceList(
								ValueManager.getValueFromBigDecimal(
									Optional.ofNullable(
										productPricing.getPriceList()
									)
										.orElse(Env.ZERO)
										.multiply(multiplyRate, MathContext.DECIMAL128)
								)
							)
							.setDisplayPriceStandard(
								ValueManager.getValueFromBigDecimal(
									Optional.ofNullable(
										productPricing.getPriceStd()
									)
										.orElse(Env.ZERO)
										.multiply(multiplyRate, MathContext.DECIMAL128)
								)
							)
							.setDisplayPriceLimit(
								ValueManager.getValueFromBigDecimal(
									Optional.ofNullable(
										productPricing.getPriceLimit()
									)
										.orElse(Env.ZERO)
										.multiply(multiplyRate, MathContext.DECIMAL128)
								)
							)
							.setConversionRate(
								ConvertUtil.convertConversionRate(conversionRate)
							)
						;
					}
				}
			}
		}
		//	Get Storage
		if(warehouseId > 0) {
			AtomicReference<BigDecimal> quantityOnHand = new AtomicReference<BigDecimal>(Env.ZERO);
			AtomicReference<BigDecimal> quantityReserved = new AtomicReference<BigDecimal>(Env.ZERO);
			AtomicReference<BigDecimal> quantityOrdered = new AtomicReference<BigDecimal>(Env.ZERO);
			AtomicReference<BigDecimal> quantityAvailable = new AtomicReference<BigDecimal>(Env.ZERO);
			//	
			Arrays.asList(MStorage.getOfProduct(Env.getCtx(), product.getM_Product_ID(), null))
				.stream()
				.filter(storage -> storage.getM_Warehouse_ID() == warehouseId)
				.forEach(storage -> {
					quantityOnHand.updateAndGet(quantity -> quantity.add(storage.getQtyOnHand()));
					quantityReserved.updateAndGet(quantity -> quantity.add(storage.getQtyReserved()));
					quantityOrdered.updateAndGet(quantity -> quantity.add(storage.getQtyOrdered()));
					quantityAvailable.updateAndGet(quantity -> quantity.add(storage.getQtyOnHand().subtract(storage.getQtyReserved())));
				});
			builder.setQuantityOnHand(
					ValueManager.getValueFromBigDecimal(
						quantityOnHand.get()
					)
				)
				.setQuantityReserved(
					ValueManager.getValueFromBigDecimal(
						quantityReserved.get()
					)
				)
				.setQuantityOrdered(
					ValueManager.getValueFromBigDecimal(
						quantityOrdered.get()
					)
				)
				.setQuantityAvailable(
					ValueManager.getValueFromBigDecimal(
						quantityAvailable.get()
					)
				)
			;
		}
		return builder;
	}



	@Override
	public void getProductPrice(GetProductPriceRequest request, StreamObserver<ProductPrice> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Object Requested = " + request.getSearchValue());
			ProductPrice.Builder productPrice = getProductPrice(request);
			responseObserver.onNext(productPrice.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(
				Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException()
			);
		}
	}

	/**
	 * Get Product Price Method
	 * @param request
	 * @return
	 */
	private ProductPrice.Builder getProductPrice(GetProductPriceRequest request) {
		//	Get Product
		MProduct product = null;
		if(!Util.isEmpty(request.getSearchValue(), true)) {
			product = new Query(
				Env.getCtx(),
				I_M_Product.Table_Name,
				"("
				+ "UPPER(Value) = UPPER(?)"
				+ "OR UPPER(Name) = UPPER(?)"
				+ "OR UPPER(UPC) = UPPER(?)"
				+ "OR UPPER(SKU) = UPPER(?)"
				+ ")",
				null
			)
				.setParameters(request.getSearchValue(), request.getSearchValue(), request.getSearchValue(), request.getSearchValue())
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.first();
		} else if(!Util.isEmpty(request.getUpc(), true)) {
			if(product == null) {
				Optional<MProduct> optionalProduct = MProduct.getByUPC(Env.getCtx(), request.getUpc(), null).stream().findAny();
				if(optionalProduct.isPresent()) {
					product = optionalProduct.get();
				}
			}
		} else if(!Util.isEmpty(request.getSku(), true)) {
			if(product == null) {
				product = new Query(
					Env.getCtx(),
					I_M_Product.Table_Name,
					"UPPER(SKU) = UPPER(?)",
					null
				)
					.setParameters(request.getSku())
					.setClient_ID()
					.setOnlyActiveRecords(true)
					.first();
			}
		} else if(!Util.isEmpty(request.getValue(), true)) {
			if(product == null) {
				product = new Query(
					Env.getCtx(),
					I_M_Product.Table_Name,
					"UPPER(Value) = UPPER(?)",
					null
				)
					.setParameters(request.getValue())
					.setClient_ID()
					.setOnlyActiveRecords(true)
					.first();
			}
		} else if(!Util.isEmpty(request.getName(), true)) {
			if(product == null) {
				product = new Query(
					Env.getCtx(),
					I_M_Product.Table_Name,
					"UPPER(Name) LIKE UPPER(?)",
					null
				)
					.setParameters(request.getName())
					.setClient_ID()
					.setOnlyActiveRecords(true)
					.first();
			}
		}
		//	Validate product
		if(product == null) {
			throw new AdempiereException("@M_Product_ID@ @NotFound@");
		}
		MPOS pos = getPOSFromId(request.getPosId(), true);
		//	Validate Price List
		int priceListId = pos.getM_PriceList_ID();
		if(request.getPriceListId() > 0) {
			priceListId = request.getPriceListId();
		}
		MPriceList priceList = MPriceList.get(Env.getCtx(), priceListId, null);
		AtomicInteger warehouseId = new AtomicInteger(pos.getM_Warehouse_ID());
		if(request.getWarehouseId() > 0) {
			warehouseId.set(request.getWarehouseId());
		}
		//	Get Valid From
		AtomicReference<Timestamp> validFrom = new AtomicReference<>();
		if(!Util.isEmpty(request.getValidFrom())) {
			validFrom.set(
				ValueManager.getTimestampFromString(
					request.getValidFrom()
				)
			);
		} else {
			validFrom.set(TimeUtil.getDay(System.currentTimeMillis()));
		}
		int businessPartnerId = request.getBusinessPartnerId();
		int displayCurrencyId = pos.get_ValueAsInt("DisplayCurrency_ID");
		int conversionTypeId = pos.get_ValueAsInt("C_ConversionType_ID");
		return convertProductPrice(
			product,
			businessPartnerId,
			priceList,
			warehouseId.get(),
			validFrom.get(),
			displayCurrencyId,
			conversionTypeId,
			Env.ONE
		);
	}

	@Override
	public void listStocks(ListStocksRequest request, StreamObserver<ListStocksResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Object Requested = " + SessionManager.getSessionUuid());
			ListStocksResponse.Builder stocks = listStocks(request);
			responseObserver.onNext(stocks.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException()
			);
		}
	}
	
	/**
	 * List Stocks
	 * @param request
	 * @return
	 */
	private ListStocksResponse.Builder listStocks(ListStocksRequest request) {
		ListStocksResponse.Builder builder = ListStocksResponse.newBuilder();
		Trx.run(transactionName -> {
			MPOS pos = getPOSFromId(request.getPosId(), true);
			String sku = request.getSku();
			if(request.getSku().contains(":")) {
				sku = request.getSku().split(":")[0];
			}
			MProduct product = null;
			if (!Util.isEmpty(sku, true)) {
				product = getProductFromSku(sku);
			} else if (!Util.isEmpty(request.getValue(), true)) {
				product = getProductFromValue(request.getValue());
			}
			if(product == null) {
				throw new AdempiereException("@M_Product_ID@ @NotFound@");
			}

			String nexPageToken = null;
			int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
			int limit = LimitUtil.PAGE_SIZE;
			int offset = pageNumber * LimitUtil.PAGE_SIZE;

			final String whereClause = I_M_Storage.COLUMNNAME_M_Product_ID + " = ? "
				+ " AND EXISTS (SELECT 1 FROM C_POSWarehouseAllocation "
				+ " JOIN C_POS ON C_POS.C_POS_ID = C_POSWarehouseAllocation.C_POS_ID "
				+ " JOIN M_Locator ON M_Locator.M_Locator_ID = M_Storage.M_Locator_ID "
				+ " AND C_POSWarehouseAllocation.M_Warehouse_ID = M_Locator.M_Warehouse_ID "
				+ " WHERE C_POSWarehouseAllocation.C_POS_ID = ? "
				+ ")"
			;

			/*
			boolean IsMultiStoreStock = false;
			int warehouseId = store.getM_Warehouse_ID();
			if(!IsMultiStoreStock) {
				whereClause = "AND M_Locator_ID IN (SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=" + warehouseId + ")";
			}
			*/
			
			Query query = new Query(
					Env.getCtx(),
					I_M_Storage.Table_Name, 
					whereClause,
					null
				)
				.setParameters(product.getM_Product_ID(), pos.getC_POS_ID())
				.setClient_ID()
				.setOnlyActiveRecords(true);
			int count = query.count();
			query.<MStorage>list().forEach(storage -> builder.addStocks(
				(convertStock(storage)).build())
			);
			//	
			builder.setRecordCount(count);
			//	Set page token
			if(count > offset && count > limit) {
				nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
			}
			builder.setNextPageToken(
				ValueManager.validateNull(nexPageToken)
			);
		});
		
		//	
		return builder;
	}

	/**
	 * Get product from SKU
	 * @param sku
	 * @return
	 */
	private MProduct getProductFromSku(String sku) {
		//	SKU
		if(Util.isEmpty(sku)) {
			throw new AdempiereException("@SKU@ @IsMandatory@");
		}
		//	
		MProduct product = new Query(
				Env.getCtx(),
				I_M_Product.Table_Name, 
				"(UPPER(SKU) = UPPER(?))",
				null
			)
			.setParameters(sku.trim())
			.setClient_ID()
			.setOnlyActiveRecords(true)
			.first();
		//	Validate product
		if(product == null) {
			throw new AdempiereException("@M_Product_ID@ @NotFound@");
		}
		//	Default
		return product;
	}

	/**
	 * Get product from Value
	 * @param sku
	 * @return
	 */
	private MProduct getProductFromValue(String value) {
		if(Util.isEmpty(value)) {
			throw new AdempiereException("@Value@ @IsMandatory@");
		}
		//	
		MProduct product = new Query(
				Env.getCtx(),
				I_M_Product.Table_Name, 
				"(UPPER(Value) = UPPER(?))",
				null
			)
			.setParameters(value.trim())
			.setClient_ID()
			.setOnlyActiveRecords(true)
			.first();
		//	Validate product
		if(product == null) {
			throw new AdempiereException("@M_Product_ID@ @NotFound@");
		}
		//	Default
		return product;
	}

	/**
	 * Convert stock
	 * @param storage
	 * @return
	 */
	private Stock.Builder convertStock(MStorage storage) {
		Stock.Builder builder = Stock.newBuilder();
		if(storage == null) {
			return builder;
		}
		BigDecimal quantityOnHand = Optional.ofNullable(storage.getQtyOnHand()).orElse(Env.ZERO);
		BigDecimal quantityReserved = Optional.ofNullable(storage.getQtyReserved()).orElse(Env.ZERO);
		BigDecimal quantityAvailable = quantityOnHand.subtract(quantityReserved);
		builder.setIsInStock(quantityAvailable.signum() > 0);
		builder.setQuantity(quantityAvailable.doubleValue());
		//	
		MProduct product = MProduct.get(Env.getCtx(), storage.getM_Product_ID());
		MUOM unitOfMeasure = MUOM.get(Env.getCtx(), product.getC_UOM_ID());
		Trx.run(transactionName -> {
			MAttributeSetInstance attribute = new MAttributeSetInstance(Env.getCtx(), storage.getM_AttributeSetInstance_ID(), transactionName);
			builder.setAttributeName(
				ValueManager.validateNull(
					attribute.getDescription()
				)
			);
		});
		
		builder.setIsDecimalQuantity(unitOfMeasure.getStdPrecision() != 0);
		//	References
		builder.setProductId(storage.getM_Product_ID());

		builder.setIsManageStock(product.isStocked());
		//	Warehouse
		MWarehouse warehouse = MWarehouse.get(Env.getCtx(), storage.getM_Warehouse_ID());
		builder
			.setWarehouseId(warehouse.getM_Warehouse_ID())
			.setWarehouseName(Optional.ofNullable(warehouse.getName()).orElse("")
		);
		//	
		return builder;
	}

	@Override
	public void listAvailableCash(ListAvailableCashRequest request, StreamObserver<ListAvailableCashResponse> responseObserver) {
		try {
			ListAvailableCashResponse.Builder cashListBuilder = listCash(Env.getCtx(), request);
			responseObserver.onNext(cashListBuilder.build());
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
	 * List Cash from POS UUID
	 * @param request
	 * @return
	 */
	private ListAvailableCashResponse.Builder listCash(Properties context, ListAvailableCashRequest request) {
		if(request.getPosId() <= 0) {
			throw new AdempiereException("@C_POS_ID@ @NotFound@");
		}
		ListAvailableCashResponse.Builder builder = ListAvailableCashResponse.newBuilder();
		final String TABLE_NAME = "C_POSCashAllocation";
		if(MTable.getTable_ID(TABLE_NAME) <= 0) {
			return builder;
		}
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		//	Aisle Seller
		int posId = request.getPosId();
		//	Get Product list
		Query query = new Query(
				Env.getCtx(),
				TABLE_NAME,
				"C_POS_ID = ?",
				null
			)
			.setParameters(posId)
			.setClient_ID()
			.setOnlyActiveRecords(true)
			.setOrderBy(I_AD_PrintFormatItem.COLUMNNAME_SeqNo);
		int count = query.count();
		query
			.setLimit(limit, offset)
			.list()
			.forEach(availableCash -> {
				MBankAccount bankAccount = MBankAccount.get(context, availableCash.get_ValueAsInt("C_BankAccount_ID"));
				AvailableCash.Builder availableCashBuilder = AvailableCash.newBuilder()
					.setId(bankAccount.getC_BankAccount_ID())
					.setName(
						ValueManager.validateNull(
							bankAccount.getName()
						)
					)
					.setKey(
						ValueManager.validateNull(
							bankAccount.getAccountNo()
						)
					)
					.setIsPosRequiredPin(availableCash.get_ValueAsBoolean(I_C_POS.COLUMNNAME_IsPOSRequiredPIN))
					.setBankAccount(
						ConvertCommon.convertBankAccount(
							bankAccount
						)
					)
				;

				builder.addCash(availableCashBuilder);
		});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		return builder;
	}


	@Override
	public void saveCommandShortcut(SaveCommandShortcutRequest request, StreamObserver<CommandShortcut> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			CommandShortcut.Builder cashListBuilder = saveCommandShortcut(request);
			responseObserver.onNext(cashListBuilder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	private CommandShortcut.Builder saveCommandShortcut(SaveCommandShortcutRequest request) {
		MPOS pos = getPOSFromId(request.getPosId(), true);

		if (Util.isEmpty(request.getCommand(), true)) {
			throw new AdempiereException("@ECA14_Command@ @NotFound@");
		}
		if (Util.isEmpty(request.getShortcut(), true)) {
			throw new AdempiereException("@ECA14_Shortcut@ @NotFound@");
		}

		final String sqlShorcut = "SELECT ECA14_Command FROM C_POSCommandShortcut WHERE C_POS_ID = ? AND ECA14_Shortcut = ?";
		String commandUsed = DB.getSQLValueString(null, sqlShorcut, pos.getC_POS_ID(), request.getShortcut());
		if (!Util.isEmpty(commandUsed, true)) {
			throw new AdempiereException("@ECA14_Shortcut@ @Used@ " + commandUsed);
		}

		final String whereClause = "C_POS_ID = ? AND ECA14_Command = ?";
		PO shorcutCommand = new Query(
			Env.getCtx(),
			"C_POSCommandShortcut",
			whereClause,
			null
		)
			.setClient_ID()
			.setParameters(pos.getC_POS_ID(), request.getCommand())
			.first()
		;
		if (shorcutCommand == null) {
			MTable table = MTable.get(Env.getCtx(), "C_POSCommandShortcut");
			shorcutCommand = table.getPO(0, null);
			shorcutCommand.set_ValueOfColumn("C_POS_ID", pos.getC_POS_ID());
		}

		shorcutCommand.set_ValueOfColumn("ECA14_Command", request.getCommand());
		shorcutCommand.set_ValueOfColumn("ECA14_Shortcut", request.getShortcut());
		shorcutCommand.saveEx();

		CommandShortcut.Builder builder = POSConvertUtil.convertCommandShorcut(shorcutCommand);

		return builder;
	}


	@Override
	public void listCommandShortcuts(ListCommandShortcutsRequest request, StreamObserver<ListCommandShortcutsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListCommandShortcutsResponse.Builder cashListBuilder = listCommandShortcuts(request);
			responseObserver.onNext(cashListBuilder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	private ListCommandShortcutsResponse.Builder listCommandShortcuts(ListCommandShortcutsRequest request) {
		MPOS pos = getPOSFromId(request.getPosId(), true);

		Query query = new Query(
			Env.getCtx(),
			"C_POSCommandShortcut",
			I_C_POS.COLUMNNAME_C_POS_ID + " = ?",
			null
		)
			.setParameters(pos.getC_POS_ID())
			.setClient_ID()
			.setOnlyActiveRecords(true)
		;

		int count = query.count();
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListCommandShortcutsResponse.Builder builderList = ListCommandShortcutsResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(
				ValueManager.validateNull(nexPageToken)
			)
		;

		query.setLimit(limit, offset)
			.list()
			.forEach(commandShorcut -> {
				CommandShortcut.Builder builder = POSConvertUtil.convertCommandShorcut(commandShorcut);
				builderList.addRecords(builder);
			});
		;

		return builderList;
	}


	@Override
	public void deleteCommandShortcut(DeleteCommandShortcutRequest request, StreamObserver<Empty> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			Empty.Builder cashListBuilder = deleteCommandShortcut(request);
			responseObserver.onNext(cashListBuilder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}

	private Empty.Builder deleteCommandShortcut(DeleteCommandShortcutRequest request) {
		if (request.getId() <= 0) {
			throw new AdempiereException("@C_POSCommandShortcut_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			PO commandShorcut = RecordUtil.getEntity(Env.getCtx(), "C_POSCommandShortcut", request.getId(), transactionName);
			if (commandShorcut == null || commandShorcut.get_ID() <= 0) {
				throw new AdempiereException("@C_POSCommandShortcut_ID@ @NotFound@");
			}

			commandShorcut.deleteEx(true);
		});

		Empty.Builder builder = Empty.newBuilder();

		return builder;
	}



	@Override
	public void listCampaigns(ListCampaignsRequest request, StreamObserver<ListCampaignsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListCampaignsResponse.Builder cashListBuilder = POS.listCampaigns(request);
			responseObserver.onNext(cashListBuilder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException()
			);
		}
	}

}
