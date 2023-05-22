/************************************************************************************
 * Copyright (C) 2012-2023 E.R.P. Consultores y Asociados, C.A.                     *
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

import org.adempiere.exceptions.AdempiereException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.adempiere.core.domains.models.I_C_BPartner;
import org.adempiere.core.domains.models.I_C_Order;
import org.adempiere.core.domains.models.I_C_OrderLine;
import org.adempiere.core.domains.models.I_M_InOut;
import org.adempiere.core.domains.models.I_M_InOutLine;
import org.adempiere.core.domains.models.I_M_Product;
import org.adempiere.core.domains.models.X_M_InOut;
import org.compiere.model.MBPartner;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MSysConfig;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.compiere.util.Util;
import org.spin.backend.grpc.common.Empty;
import org.spin.backend.grpc.form.express_receipt.BusinessPartner;
import org.spin.backend.grpc.form.express_receipt.CreateReceiptLineRequest;
import org.spin.backend.grpc.form.express_receipt.CreateReceiptRequest;
import org.spin.backend.grpc.form.express_receipt.DeleteReceiptLineRequest;
import org.spin.backend.grpc.form.express_receipt.DeleteReceiptRequest;
import org.spin.backend.grpc.form.express_receipt.ListBusinessPartnersRequest;
import org.spin.backend.grpc.form.express_receipt.ListBusinessPartnersResponse;
import org.spin.backend.grpc.form.express_receipt.ListProductsRequest;
import org.spin.backend.grpc.form.express_receipt.ListProductsResponse;
import org.spin.backend.grpc.form.express_receipt.ListPurchaseOrdersRequest;
import org.spin.backend.grpc.form.express_receipt.ListPurchaseOrdersResponse;
import org.spin.backend.grpc.form.express_receipt.ListReceiptLinesRequest;
import org.spin.backend.grpc.form.express_receipt.ListReceiptLinesResponse;
import org.spin.backend.grpc.form.express_receipt.ProcessReceiptRequest;
import org.spin.backend.grpc.form.express_receipt.Product;
import org.spin.backend.grpc.form.express_receipt.PurchaseOrder;
import org.spin.backend.grpc.form.express_receipt.Receipt;
import org.spin.backend.grpc.form.express_receipt.ReceiptLine;
import org.spin.backend.grpc.form.express_receipt.UpdateReceiptLineRequest;
import org.spin.backend.grpc.form.express_receipt.ExpressReceiptGrpc.ExpressReceiptImplBase;
import org.spin.base.util.DocumentUtil;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.SessionManager;
import org.spin.base.util.ValueUtil;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Edwin Betancourt, EdwinBetanc0urt@outlook.com, https://github.com/EdwinBetanc0urt
 * Service for backend of Express Receipt
 */
public class ExpressReceiptServiceImplementation extends ExpressReceiptImplBase {
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(ExpressReceiptServiceImplementation.class);
	
	public String tableName = I_M_InOut.Table_Name;


	@Override
	public void listBusinessPartners(ListBusinessPartnersRequest request, StreamObserver<ListBusinessPartnersResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ListBusinessPartnersResponse.Builder builderList = listBusinessPartners(request);
			responseObserver.onNext(builderList.build());
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

	ListBusinessPartnersResponse.Builder listBusinessPartners(ListBusinessPartnersRequest request) {
		final String whereClause = "IsVendor='Y' ";
		Query query = new Query(
			Env.getCtx(),
			I_C_BPartner.Table_Name,
			whereClause,
			null
		).setClient_ID();

		int count = query.count();
		String nexPageToken = "";
		int pageNumber = RecordUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = RecordUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (RecordUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = RecordUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListBusinessPartnersResponse.Builder builderList = ListBusinessPartnersResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(nexPageToken)
		;

		query.setLimit(limit, offset)
			.list(MBPartner.class)
			.forEach(businessPartner -> {
				BusinessPartner.Builder builder = convertBusinessPartner(businessPartner);
				builderList.addRecords(builder);
			})
		;

		return builderList;
	}

	BusinessPartner.Builder convertBusinessPartner(MBPartner businessPartner) {
		BusinessPartner.Builder builder = BusinessPartner.newBuilder();
		if (builder == null) {
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


	@Override
	public void listPurchaseOrders(ListPurchaseOrdersRequest request, StreamObserver<ListPurchaseOrdersResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ListPurchaseOrdersResponse.Builder builderList = listPurchaseOrders(request);
			responseObserver.onNext(builderList.build());
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
	 * Get default value base on field, process parameter, browse field or column
	 * @param request
	 * @return
	 */
	private ListPurchaseOrdersResponse.Builder listPurchaseOrders(ListPurchaseOrdersRequest request) {
		Properties context = Env.getCtx();

		int businessPartnerId = request.getBusinessPartnerId();
		if (businessPartnerId <= 0 && !Util.isEmpty(request.getBusinessPartnerUuid(), true)) {
			businessPartnerId = RecordUtil.getIdFromUuid(I_C_BPartner.Table_Name, request.getBusinessPartnerUuid(), null);
		}
		if (businessPartnerId <= 0) {
			throw new AdempiereException("@C_BPartner_ID@ @NotFound@");
		}

		// See dynamic validation 52464
		final String whereClause = "IsSOTrx='N' "
			+ "AND C_BPartner_ID = ? "
			+ "AND DocStatus IN ('CL','CO') "
			+ "AND EXISTS(SELECT 1 FROM C_OrderLine ol "
			+ "WHERE ol.C_Order_ID = C_Order.C_Order_ID "
			+ "AND ol.QtyOrdered - ol.QtyDelivered > 0)"
		;
		Query query = new Query(
			context,
			I_C_Order.Table_Name,
			whereClause,
			null
		)
			.setParameters(businessPartnerId)
			.setClient_ID()
		;

		int count = query.count();
		String nexPageToken = "";
		int pageNumber = RecordUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = RecordUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (RecordUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = RecordUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListPurchaseOrdersResponse.Builder builderList = ListPurchaseOrdersResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(nexPageToken)
		;

		query.setLimit(limit, offset)
			.list(MOrder.class)
			.forEach(purchase -> {
				PurchaseOrder.Builder builder = convertPurchaseOrder(purchase);
				builderList.addRecords(builder);
			})
		;

		return builderList;
	}

	PurchaseOrder.Builder convertPurchaseOrder(MOrder purchaseOrder) {
		PurchaseOrder.Builder builder = PurchaseOrder.newBuilder();
		if (purchaseOrder == null) {
			return builder;
		}

		builder.setId(purchaseOrder.getC_Order_ID())
			.setUuid(
				ValueUtil.validateNull(purchaseOrder.getUUID())
			)
			.setDateOrdered(
				ValueUtil.getLongFromTimestamp(purchaseOrder.getDateOrdered())
			)
			.setDocumentNo(
				ValueUtil.validateNull(purchaseOrder.getDocumentNo())
			)
		;

		return builder;
	}



	@Override
	public void listProducts(ListProductsRequest request, StreamObserver<ListProductsResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ListProductsResponse.Builder builder = listProducts(request);
			responseObserver.onNext(builder.build());
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

	private ListProductsResponse.Builder listProducts(ListProductsRequest request) {
		int orderId = request.getOrderId();
		if (orderId <= 0 && !Util.isEmpty(request.getOrderUuid(), true)) {
			orderId = RecordUtil.getIdFromUuid(this.tableName, request.getOrderUuid(), null);
		}
		if (orderId <= 0) {
			throw new AdempiereException("@C_Order_ID@ @NotFound@");
		}

		//	Dynamic where clause
		String whereClause = "IsPurchased = 'Y' AND IsSummary = 'N' "
			+ "AND EXISTS(SELECT 1 FROM C_OrderLine AS OL "
			+ "WHERE OL.C_Order_ID = ? AND OL.M_Product_ID = M_Product.M_Product_ID) "
		;
		//	Parameters
		List<Object> parameters = new ArrayList<Object>();
		parameters.add(orderId);

		//	For search value
		if (!Util.isEmpty(request.getSearchValue(), true)) {
			whereClause += " AND ("
				+ "UPPER(Value) LIKE '%' || UPPER(?) || '%' "
				+ "OR UPPER(Name) LIKE '%' || UPPER(?) || '%' "
				+ "OR UPPER(UPC) = UPPER(?) "
				+ "OR UPPER(SKU) = UPPER(?) "
				+ ")"
			;
			//	Add parameters
			parameters.add(request.getSearchValue());
			parameters.add(request.getSearchValue());
			parameters.add(request.getSearchValue());
			parameters.add(request.getSearchValue());
		}

		Query query = new Query(
			Env.getCtx(),
			I_M_Product.Table_Name,
			whereClause.toString(),
			null
		)
			.setClient_ID()
			.setParameters(parameters);

		int count = query.count();
		String nexPageToken = "";
		int pageNumber = RecordUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = RecordUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (RecordUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = RecordUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListProductsResponse.Builder builderList = ListProductsResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(
				ValueUtil.validateNull(nexPageToken)
			)
		;

		query.setLimit(limit, offset)
			.list(MProduct.class)
			.forEach(product -> {
				Product.Builder builder = convertProduct(product);
				builderList.addRecords(builder);
			});
		;

		return builderList;
	}

	Product.Builder convertProduct(int productId) {
		Product.Builder builder = Product.newBuilder();
		if (productId <= 0) {
			return builder;
		}
		MProduct product = MProduct.get(Env.getCtx(), productId);
		return convertProduct(product);
	}
	Product.Builder convertProduct(MProduct product) {
		Product.Builder builder = Product.newBuilder();
		if (product == null) {
			return builder;
		}
		builder.setId(product.getM_Product_ID())
			.setUuid(
				ValueUtil.validateNull(product.getUUID())
			)
			.setUpc(
				ValueUtil.validateNull(product.getUPC())
			)
			.setSku(
				ValueUtil.validateNull(product.getSKU())
			)
			.setValue(
				ValueUtil.validateNull(product.getValue())
			)
			.setName(
				ValueUtil.validateNull(product.getName())
			)
		;

		return builder;
	}


	Receipt.Builder convertReceipt(MInOut receipt) {
		Receipt.Builder builder = Receipt.newBuilder();
		if (receipt == null) {
			return builder;
		}

		builder.setId(receipt.getM_InOut_ID())
			.setUuid(
				ValueUtil.validateNull(receipt.getUUID())
			)
			.setDocumentNo(
				ValueUtil.validateNull(receipt.getDocumentNo())
			)
			.setDateOrdered(
				ValueUtil.getLongFromTimestamp(receipt.getDateOrdered())
			)
			.setMovementDate(
				ValueUtil.getLongFromTimestamp(receipt.getMovementDate())
			)
			.setIsCompleted(
				DocumentUtil.isCompleted(receipt)
			)
		;
		MOrderLine purchaseOrderLine = new MOrderLine(Env.getCtx(), receipt.getC_Order_ID(), null);
		if (purchaseOrderLine != null && purchaseOrderLine.getC_OrderLine_ID() > 0) {
			builder.setOrderId(purchaseOrderLine.getC_OrderLine_ID())
				.setOrderUuid(purchaseOrderLine.getUUID())
			;
		}

		return builder;
	}


	@Override
	public void createReceipt(CreateReceiptRequest request, StreamObserver<Receipt> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Receipt.Builder builder = createReceipt(request);
			responseObserver.onNext(builder.build());
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

	private Receipt.Builder createReceipt(CreateReceiptRequest request) {
		if (request.getOrderId() <= 0 && Util.isEmpty(request.getOrderUuid(), true)) {
			throw new AdempiereException("@C_Order_ID@ @NotFound@");
		}

		AtomicReference<MInOut> maybeReceipt = new AtomicReference<MInOut>();
		Trx.run(transactionName -> {
			int orderId = request.getOrderId();
			if (orderId <= 0 && !Util.isEmpty(request.getOrderUuid(), true)) {
				orderId = RecordUtil.getIdFromUuid(this.tableName, request.getOrderUuid(), null);
			}
			if (orderId <= 0) {
				throw new AdempiereException("@C_Order_ID@ @NotFound@");
			}
			MOrder purchaseOrder = new MOrder(Env.getCtx(), orderId, transactionName);
			if (purchaseOrder == null || purchaseOrder.getC_Order_ID() <= 0) {
				throw new AdempiereException("@C_Order_ID@ @NotFound@");
			}
			// if (purchaseOrder.isDelivered()) {
			// 	throw new AdempiereException("@C_Order_ID@ @IsDelivered@");
			// }
			// Valid if has a Order
			if (!DocumentUtil.isCompleted(purchaseOrder)) {
				throw new AdempiereException("@Invalid@ @C_Order_ID@ " + purchaseOrder.getDocumentNo());
			}

			final String whereClause = "IsSOTrx='N' "
				+ "AND MovementType IN ('V+') "
				+ "AND DocStatus = 'DR' "
				+ "AND C_Order_ID = ? "
			;
			MInOut receipt = new Query(
				Env.getCtx(),
				I_M_InOut.Table_Name,
				whereClause,
				transactionName
			)
				.setParameters(purchaseOrder.getC_Order_ID())
				.setClient_ID()
				.first();

			// Validate
			if (receipt == null) {
				receipt = new MInOut(purchaseOrder, 0, RecordUtil.getDate());
			} else {
				if(!DocumentUtil.isDrafted(receipt)) {
					throw new AdempiereException("@M_InOut_ID@ @Processed@");
				}
				receipt.setDateOrdered(RecordUtil.getDate());
				receipt.setDateAcct(RecordUtil.getDate());
				receipt.setDateReceived(RecordUtil.getDate());
				receipt.setMovementDate(RecordUtil.getDate());
			}
			receipt.setC_Order_ID(orderId);
			receipt.saveEx(transactionName);

			maybeReceipt.set(receipt);

			if (request.getIsCreateLinesFromOrder()) {
				boolean validateOrderedQty = MSysConfig.getBooleanValue("VALIDATE_MATCHING_TO_ORDERED_QTY", true, Env.getAD_Client_ID(Env.getCtx()));

				List<MOrderLine> orderLines = Arrays.asList(purchaseOrder.getLines());
				orderLines.stream().forEach(purchaseOrderLine -> {
					Optional<MInOutLine> maybeReceiptLine = Arrays.asList(maybeReceipt.get().getLines(true))
						.stream()
						.filter(shipmentLineTofind -> {
							return shipmentLineTofind.getC_OrderLine_ID() == purchaseOrderLine.getC_OrderLine_ID();
						})
						.findFirst();

					if (maybeReceiptLine.isPresent()) {
						MInOutLine receiptLine = maybeReceiptLine.get();

						BigDecimal quantity = purchaseOrderLine.getQtyEntered();
						if (quantity == null) {
							quantity = receiptLine.getMovementQty().add(Env.ONE);
						}

						// Validate available
						if (validateOrderedQty) {
							BigDecimal orderQuantityDelivered = purchaseOrderLine.getQtyOrdered().subtract(purchaseOrderLine.getQtyDelivered());
							if (orderQuantityDelivered.compareTo(quantity) < 0) {
								throw new AdempiereException("@QtyInsufficient@");
							}
						}
						receiptLine.setQty(quantity);
						receiptLine.saveEx();
					} else {
						MInOutLine receiptLine = new MInOutLine(maybeReceipt.get());

						BigDecimal quantity = purchaseOrderLine.getQtyReserved();
						receiptLine.setOrderLine(purchaseOrderLine, 0, quantity);
						receiptLine.saveEx(transactionName);
					}
				});
			}
		});

		Receipt.Builder builder = convertReceipt(maybeReceipt.get());

		return builder;
	}



	@Override
	public void deleteReceipt(DeleteReceiptRequest request, StreamObserver<Empty> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Empty.Builder builder = deleteReceipt(request);
			responseObserver.onNext(builder.build());
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

	private Empty.Builder deleteReceipt(DeleteReceiptRequest request) {
		if (request.getId() <= 0 && Util.isEmpty(request.getUuid(), true)) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			int receiptId = request.getId();
			if (receiptId <= 0 && !Util.isEmpty(request.getUuid(), true)) {
				receiptId = RecordUtil.getIdFromUuid(this.tableName, request.getUuid(), transactionName);
			}
			if (receiptId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}

			MInOut receipt = new MInOut(Env.getCtx(), receiptId, transactionName);
			if (receipt == null || receipt.getM_InOut_ID() <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
			if (!DocumentUtil.isDrafted(receipt)) {
				throw new AdempiereException("@Invalid@ @M_InOut_ID@ " + receipt.getDocumentNo());
			}
			receipt.deleteEx(true);
		});

		return Empty.newBuilder();
	}



	@Override
	public void processReceipt(ProcessReceiptRequest request, StreamObserver<Receipt> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Receipt.Builder builder = processReceipt(request);
			responseObserver.onNext(builder.build());
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

	private Receipt.Builder processReceipt(ProcessReceiptRequest request) {
		AtomicReference<MInOut> receiptReference = new AtomicReference<MInOut>();

		Trx.run(transactionName -> {
			int receiptId = request.getId();
			if (receiptId <= 0 && !Util.isEmpty(request.getUuid(), true)) {
				receiptId = RecordUtil.getIdFromUuid(this.tableName, request.getUuid(), transactionName);
			}
			if (receiptId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}

			MInOut receipt = new MInOut(Env.getCtx(), receiptId, transactionName);
			if (receipt == null || receiptId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
			if (receipt.isProcessed()) {
				throw new AdempiereException("@M_InOut_ID@ @Processed@");
			}
			if (!receipt.processIt(X_M_InOut.DOCACTION_Complete)) {
				log.warning("@ProcessFailed@ :" + receipt.getProcessMsg());
				throw new AdempiereException("@ProcessFailed@ :" + receipt.getProcessMsg());
			}
			receipt.saveEx(transactionName);
			receiptReference.set(receipt);
		});

		Receipt.Builder builder = convertReceipt(receiptReference.get());

		return builder;
	}



	@Override
	public void createReceiptLine(CreateReceiptLineRequest request, StreamObserver<ReceiptLine> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ReceiptLine.Builder builder = createReceiptLine(request);
			responseObserver.onNext(builder.build());
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

	private ReceiptLine.Builder createReceiptLine(CreateReceiptLineRequest request) {
		if (request.getReceiptId() <= 0 && Util.isEmpty(request.getReceiptUuid(), true)) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		if (request.getProductId() <= 0 && Util.isEmpty(request.getProductUuid(), true)) {
			throw new AdempiereException("@M_Product_ID@ @NotFound@");
		}

		AtomicReference<MInOutLine> receiptLineReference = new AtomicReference<MInOutLine>();

		Trx.run(transactionName -> {
			int receiptId = request.getReceiptId();
			if (receiptId <= 0) {
				receiptId = RecordUtil.getIdFromUuid(this.tableName, request.getReceiptUuid(), transactionName);
				if (receiptId <= 0) {
					throw new AdempiereException("@M_InOut_ID@ @NotFound@");
				}
			}
			MInOut receipt = new MInOut(Env.getCtx(), receiptId, transactionName);
			if (receipt == null || receipt.getM_InOut_ID() <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
			if (receipt.isProcessed()) {
				throw new AdempiereException("@M_InOut_ID@ @Processed@");
			}

			int productId = request.getProductId();
			if (productId <= 0) {
				productId = RecordUtil.getIdFromUuid(I_M_Product.Table_Name, request.getProductUuid(), transactionName);
				if (productId <= 0) {
					throw new AdempiereException("@M_Product_ID@ @NotFound@");
				}
			}

			final String whereClause = "M_Product_ID = ? "
				+ "AND EXISTS(SELECT 1 FROM M_InOut "
				+ "WHERE M_InOut.C_Order_ID = C_OrderLine.C_Order_ID "
				+ "AND M_InOut.M_InOut_ID = ?)"
			;
			MOrderLine purchaseOrderLine = new Query(
				Env.getCtx(),
				I_C_OrderLine.Table_Name,
				whereClause,
				transactionName
			)
				.setParameters(productId, receiptId)
				.setClient_ID()
				.first();
			if (purchaseOrderLine == null || purchaseOrderLine.getC_OrderLine_ID() <= 0) {
				throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
			}

			BigDecimal quantity = BigDecimal.ONE;
			if (request.getQuantity() != null) {
				quantity = ValueUtil.getBigDecimalFromDecimal(request.getQuantity());
				if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
					quantity = BigDecimal.ONE;
				}
			}
			if (request.getIsQuantityFromOrderLine()) {
				quantity = purchaseOrderLine.getQtyReserved();
			}

			Optional<MInOutLine> maybeReceiptLine = Arrays.asList(receipt.getLines(true))
				.stream()
				.filter(receiptLineTofind -> {
					return receiptLineTofind.getC_OrderLine_ID() == purchaseOrderLine.getC_OrderLine_ID();
				})
				.findFirst();


			MInOutLine receiptLine = null;
			if (maybeReceiptLine.isPresent()) {
				receiptLine = maybeReceiptLine.get();
				BigDecimal orderQuantity = receiptLine.getQtyEntered();
				orderQuantity = orderQuantity.add(quantity);
				receiptLine.setQty(orderQuantity);
			} else {
				receiptLine = new MInOutLine(receipt);
				receiptLine.setOrderLine(purchaseOrderLine, 0, quantity);
				receiptLine.setQty(quantity);
			}
			receiptLine.saveEx(transactionName);

			receiptLineReference.set(receiptLine);
		});

		ReceiptLine.Builder builder = convertReceiptLine(
			receiptLineReference.get()
		);

		return builder;
	}



	@Override
	public void deleteReceiptLine(DeleteReceiptLineRequest request, StreamObserver<Empty> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Empty.Builder builder = deleteReceiptLine(request);
			responseObserver.onNext(builder.build());
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

	private Empty.Builder deleteReceiptLine(DeleteReceiptLineRequest request) {
		if (request.getId() <= 0 && Util.isEmpty(request.getUuid(), true)) {
			throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
		}

		Trx.run(transactionName -> {
			int receiptLineId = request.getId();
			if (receiptLineId <= 0 && !Util.isEmpty(request.getUuid(), true)) {
				receiptLineId = RecordUtil.getIdFromUuid(I_M_InOutLine.Table_Name, request.getUuid(), transactionName);
			}
			if (receiptLineId <= 0) {
				throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
			}

			MInOutLine receiptLine = new MInOutLine(Env.getCtx(), receiptLineId, transactionName);
			if (receiptLine == null || receiptLine.getM_InOutLine_ID() <= 0) {
				throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
			}
			// Validate processed
			if (receiptLine.isProcessed()) {
				throw new AdempiereException("@M_InOutLine_ID@ @Processed@");
			}
			receiptLine.deleteEx(true);
		});

		return Empty.newBuilder();
	}



	@Override
	public void updateReceiptLine(UpdateReceiptLineRequest request, StreamObserver<ReceiptLine> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ReceiptLine.Builder builder = updateReceiptLine(request);
			responseObserver.onNext(builder.build());
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

	private ReceiptLine.Builder updateReceiptLine(UpdateReceiptLineRequest request) {
		if (request.getId() <= 0 && Util.isEmpty(request.getUuid(), true)) {
			throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
		}

		AtomicReference<MInOutLine> maybeReceiptLine = new AtomicReference<MInOutLine>();

		Trx.run(transactionName -> {
			int receiptLineId = request.getId();
			if (receiptLineId <= 0) {
				receiptLineId = RecordUtil.getIdFromUuid(I_M_InOutLine.Table_Name, request.getUuid(), transactionName);
				if (receiptLineId <= 0) {
					throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
				}
			}

			MInOutLine receiptLine = new MInOutLine(Env.getCtx(), receiptLineId, transactionName);
			if (receiptLine == null || receiptLine.getM_InOutLine_ID() <= 0) {
				throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
			}
			// Validate processed
			if (receiptLine.isProcessed()) {
				throw new AdempiereException("@M_InOutLine_ID@ @Processed@");
			}

			MOrderLine purchaseOrderLine = new MOrderLine(Env.getCtx(), receiptLine.getC_OrderLine_ID(), transactionName);
			if (purchaseOrderLine == null || purchaseOrderLine.getC_OrderLine_ID() <= 0) {
				throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
			}

			BigDecimal quantity = BigDecimal.ONE;
			if (request.getQuantity() != null) {
				quantity = ValueUtil.getBigDecimalFromDecimal(request.getQuantity());
				if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
					quantity = BigDecimal.ONE;
				}
			}

			boolean validateOrderedQty = MSysConfig.getBooleanValue("VALIDATE_MATCHING_TO_ORDERED_QTY", true, Env.getAD_Client_ID(Env.getCtx()));
			// Validate available
			if (validateOrderedQty) {
				BigDecimal orderQuantityDelivered = purchaseOrderLine.getQtyOrdered().subtract(purchaseOrderLine.getQtyDelivered());
				if (orderQuantityDelivered.compareTo(quantity) < 0) {
					throw new AdempiereException("@QtyInsufficient@");
				}
			}

			receiptLine.setQty(quantity);
			receiptLine.setDescription(
				ValueUtil.validateNull(request.getDescription())
			);
			receiptLine.saveEx(transactionName);

			maybeReceiptLine.set(receiptLine);
		});

		ReceiptLine.Builder builder = convertReceiptLine(
			maybeReceiptLine.get()
		);

		return builder;
	}



	@Override
	public void listReceiptLines(ListReceiptLinesRequest request, StreamObserver<ListReceiptLinesResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ListReceiptLinesResponse.Builder builderList = listReceiptLines(request);
			responseObserver.onNext(builderList.build());
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

	ReceiptLine.Builder convertReceiptLine(MInOutLine receiptLine) {
		ReceiptLine.Builder builder = ReceiptLine.newBuilder();
		if (receiptLine == null || receiptLine.getM_InOutLine_ID() <= 0) {
			return builder;
		}

		builder.setId(receiptLine.getM_InOutLine_ID())
			.setUuid(
				ValueUtil.validateNull(receiptLine.getUUID())
			)
			.setProduct(
				convertProduct(receiptLine.getM_Product_ID())
			)
			.setDescription(
				ValueUtil.validateNull(receiptLine.getDescription())
			)
			.setQuantity(
				ValueUtil.getDecimalFromBigDecimal(receiptLine.getQtyEntered())
			)
			.setLine(receiptLine.getLine())
		;
		MOrderLine orderLine = new MOrderLine(Env.getCtx(), receiptLine.getC_OrderLine_ID(), null);
		if (orderLine != null && orderLine.getC_OrderLine_ID() > 0) {
			builder.setOrderLineId(orderLine.getC_OrderLine_ID())
				.setOrderLineUuid(
					ValueUtil.validateNull(orderLine.getUUID())
				)
			;
		}

		return builder;
	}

	private ListReceiptLinesResponse.Builder listReceiptLines(ListReceiptLinesRequest request) {
		if (request.getReceiptId() <= 0 && Util.isEmpty(request.getReceiptUuid(), true)) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		int receiptId = request.getReceiptId();
		if (receiptId <= 0) {
			receiptId = RecordUtil.getIdFromUuid(I_M_InOut.Table_Name, request.getReceiptUuid(), null);
			if (receiptId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
		}

		Query query = new Query(
			Env.getCtx(),
			I_M_InOutLine.Table_Name,
			I_M_InOutLine.COLUMNNAME_M_InOut_ID + " = ?",
			null
		)
			.setParameters(receiptId)
			.setClient_ID()
			.setOnlyActiveRecords(true)
		;

		int count = query.count();
		String nexPageToken = null;
		int pageNumber = RecordUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = RecordUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (RecordUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = RecordUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListReceiptLinesResponse.Builder builderList = ListReceiptLinesResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(
				ValueUtil.validateNull(nexPageToken)
			)
		;

		query.setLimit(limit, offset)
			.list(MInOutLine.class)
			.forEach(receiptLine -> {
				ReceiptLine.Builder builder = convertReceiptLine(receiptLine);
				builderList.addRecords(builder);
			});
		;

		return builderList;
	}

}
