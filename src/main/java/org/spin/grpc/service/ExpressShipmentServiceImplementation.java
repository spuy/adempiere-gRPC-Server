/************************************************************************************
 * Copyright (C) 2018-2023 E.R.P. Consultores y Asociados, C.A.                     *
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
import org.compiere.model.MRole;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.compiere.util.Util;
import org.spin.backend.grpc.common.Empty;
import org.spin.backend.grpc.form.express_shipment.BusinessPartner;
import org.spin.backend.grpc.form.express_shipment.CreateShipmentLineRequest;
import org.spin.backend.grpc.form.express_shipment.CreateShipmentRequest;
import org.spin.backend.grpc.form.express_shipment.DeleteShipmentLineRequest;
import org.spin.backend.grpc.form.express_shipment.DeleteShipmentRequest;
import org.spin.backend.grpc.form.express_shipment.ListBusinessPartnersRequest;
import org.spin.backend.grpc.form.express_shipment.ListBusinessPartnersResponse;
import org.spin.backend.grpc.form.express_shipment.ListProductsRequest;
import org.spin.backend.grpc.form.express_shipment.ListProductsResponse;
import org.spin.backend.grpc.form.express_shipment.ListSalesOrdersRequest;
import org.spin.backend.grpc.form.express_shipment.ListSalesOrdersResponse;
import org.spin.backend.grpc.form.express_shipment.ListShipmentLinesRequest;
import org.spin.backend.grpc.form.express_shipment.ListShipmentLinesResponse;
import org.spin.backend.grpc.form.express_shipment.ProcessShipmentRequest;
import org.spin.backend.grpc.form.express_shipment.Product;
import org.spin.backend.grpc.form.express_shipment.SalesOrder;
import org.spin.backend.grpc.form.express_shipment.Shipment;
import org.spin.backend.grpc.form.express_shipment.ShipmentLine;
import org.spin.backend.grpc.form.express_shipment.UpdateShipmentLineRequest;
import org.spin.backend.grpc.form.express_shipment.ExpressShipmentGrpc.ExpressShipmentImplBase;
import org.spin.base.db.LimitUtil;
import org.spin.base.util.DocumentUtil;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.SessionManager;
import org.spin.base.util.ValueUtil;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Edwin Betancourt, EdwinBetanc0urt@outlook.com, https://github.com/EdwinBetanc0urt
 * Service for backend of Express Shipment
 */
public class ExpressShipmentServiceImplementation extends ExpressShipmentImplBase {
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(ExpressShipmentServiceImplementation.class);
	
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
		final String whereClause = "IsCustomer='Y' ";
		Query query = new Query(
			Env.getCtx(),
			I_C_BPartner.Table_Name,
			whereClause,
			null
		)
			.setClient_ID()
			.setOnlyActiveRecords(true)
			.setApplyAccessFilter(MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO)
		;

		int count = query.count();
		String nexPageToken = "";
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
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
	public void listSalesOrders(ListSalesOrdersRequest request, StreamObserver<ListSalesOrdersResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ListSalesOrdersResponse.Builder builderList = listSalesOrders(request);
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
	private ListSalesOrdersResponse.Builder listSalesOrders(ListSalesOrdersRequest request) {
		Properties context = Env.getCtx();

		int businessPartnerId = request.getBusinessPartnerId();
		if (businessPartnerId <= 0 && !Util.isEmpty(request.getBusinessPartnerUuid(), true)) {
			businessPartnerId = RecordUtil.getIdFromUuid(I_C_BPartner.Table_Name, request.getBusinessPartnerUuid(), null);
		}
		if (businessPartnerId <= 0) {
			throw new AdempiereException("@C_BPartner_ID@ @NotFound@");
		}

		final String whereClause = "IsSOTrx='Y' "
			+ "AND C_BPartner_ID = ? "
			+ "AND DocStatus IN ('CL','CO')"
			+ "AND EXISTS(SELECT 1 FROM C_OrderLine ol "
			+ "WHERE ol.C_Order_ID = C_Order.C_Order_ID "
			+ "AND COALESCE(ol.QtyOrdered, 0) - COALESCE(ol.QtyDelivered, 0) > 0)"
		;
		Query query = new Query(
			context,
			I_C_Order.Table_Name,
			whereClause,
			null
		)
			.setParameters(businessPartnerId)
			.setClient_ID()
			.setApplyAccessFilter(MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO)
		;

		int count = query.count();
		String nexPageToken = "";
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListSalesOrdersResponse.Builder builderList = ListSalesOrdersResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(nexPageToken)
		;

		query.setLimit(limit, offset)
			.list(MOrder.class)
			.forEach(salesOrder -> {
				SalesOrder.Builder builder = convertSalesOrder(salesOrder);
				builderList.addRecords(builder);
			});
		;

		return builderList;
	}

	SalesOrder.Builder convertSalesOrder(MOrder salesOrder) {
		SalesOrder.Builder builder = SalesOrder.newBuilder();
		if (salesOrder == null) {
			return builder;
		}

		builder.setId(salesOrder.getC_Order_ID())
			.setUuid(
				ValueUtil.validateNull(salesOrder.getUUID())
			)
			.setDateOrdered(
				ValueUtil.getLongFromTimestamp(salesOrder.getDateOrdered())
			)
			.setDocumentNo(
				ValueUtil.validateNull(salesOrder.getDocumentNo())
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
		String whereClause = "IsSold = 'Y' AND IsSummary = 'N' "
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
			.setParameters(parameters)
			.setApplyAccessFilter(MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO)
		;

		int count = query.count();
		String nexPageToken = "";
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		//	Set page token
		if (LimitUtil.isValidNextPageToken(count, offset, limit)) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
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


	Shipment.Builder convertShipment(MInOut shipment) {
		Shipment.Builder builder = Shipment.newBuilder();
		if (shipment == null) {
			return builder;
		}

		builder.setId(shipment.getM_InOut_ID())
			.setUuid(
				ValueUtil.validateNull(shipment.getUUID())
			)
			.setDocumentNo(
				ValueUtil.validateNull(shipment.getDocumentNo())
			)
			.setDateOrdered(
				ValueUtil.getLongFromTimestamp(shipment.getDateOrdered())
			)
			.setMovementDate(
				ValueUtil.getLongFromTimestamp(shipment.getMovementDate())
			)
			.setIsCompleted(
				DocumentUtil.isCompleted(shipment)
			)
		;
		MOrderLine salesOrderLine = new MOrderLine(Env.getCtx(), shipment.getC_Order_ID(), null);
		if (salesOrderLine != null && salesOrderLine.getC_OrderLine_ID() > 0) {
			builder.setOrderId(salesOrderLine.getC_OrderLine_ID())
				.setOrderUuid(salesOrderLine.getUUID())
			;
		}

		return builder;
	}


	@Override
	public void createShipment(CreateShipmentRequest request, StreamObserver<Shipment> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Shipment.Builder builder = createShipment(request);
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

	private Shipment.Builder createShipment(CreateShipmentRequest request) {
		if (request.getOrderId() <= 0 && Util.isEmpty(request.getOrderUuid(), true)) {
			throw new AdempiereException("@C_Order_ID@ @NotFound@");
		}

		AtomicReference<MInOut> maybeShipment = new AtomicReference<MInOut>();
		Trx.run(transactionName -> {
			int orderId = request.getOrderId();
			if (orderId <= 0 && !Util.isEmpty(request.getOrderUuid(), true)) {
				orderId = RecordUtil.getIdFromUuid(this.tableName, request.getOrderUuid(), null);
			}
			if (orderId <= 0) {
				throw new AdempiereException("@C_Order_ID@ @NotFound@");
			}
			MOrder salesOrder = new MOrder(Env.getCtx(), orderId, transactionName);
			if (salesOrder == null || salesOrder.getC_Order_ID() <= 0) {
				throw new AdempiereException("@C_Order_ID@ @NotFound@");
			}
			// if (salesOrder.isDelivered()) {
			// 	throw new AdempiereException("@C_Order_ID@ @IsDelivered@");
			// }
			// Valid if has a Order
			if (!DocumentUtil.isCompleted(salesOrder)) {
				throw new AdempiereException("@Invalid@ @C_Order_ID@ " + salesOrder.getDocumentNo());
			}

			final String whereClause = "IsSOTrx='Y' "
				+ "AND MovementType IN ('V-') "
				+ "AND DocStatus = 'DR' "
				+ "AND C_Order_ID = ? "
			;

			MInOut shipment = new Query(
				Env.getCtx(),
				I_M_InOut.Table_Name,
				whereClause,
				transactionName
			)
				.setParameters(salesOrder.getC_Order_ID())
				.setClient_ID()
				.first();

			// Validate
			if (shipment == null) {
				shipment = new MInOut(salesOrder, 0, RecordUtil.getDate());
			} else {
				if(!DocumentUtil.isDrafted(shipment)) {
					throw new AdempiereException("@M_InOut_ID@ @Processed@");
				}
				shipment.setDateOrdered(RecordUtil.getDate());
				shipment.setDateAcct(RecordUtil.getDate());
				shipment.setDateReceived(RecordUtil.getDate());
				shipment.setMovementDate(RecordUtil.getDate());
			}
			shipment.setC_Order_ID(orderId);
			shipment.saveEx(transactionName);

			maybeShipment.set(shipment);

			if (request.getIsCreateLinesFromOrder()) {
				List<MOrderLine> orderLines = Arrays.asList(salesOrder.getLines());
				orderLines.stream().forEach(salesOrderLine -> {
					Optional<MInOutLine> maybeShipmentLine = Arrays.asList(maybeShipment.get().getLines(true))
						.stream()
						.filter(shipmentLineTofind -> {
							return shipmentLineTofind.getC_OrderLine_ID() == salesOrderLine.getC_OrderLine_ID();
						})
						.findFirst();

					if (maybeShipmentLine.isPresent()) {
						MInOutLine shipmentLine = maybeShipmentLine.get();

						BigDecimal quantity = salesOrderLine.getQtyEntered();
						if (quantity == null) {
							quantity = shipmentLine.getMovementQty().add(Env.ONE);
						}

						// Validate available
						BigDecimal orderQuantityDelivered = salesOrderLine.getQtyOrdered().subtract(salesOrderLine.getQtyDelivered());
						if (orderQuantityDelivered.compareTo(quantity) < 0) {
							throw new AdempiereException("@QtyInsufficient@");
						}
						shipmentLine.setQty(quantity);
						shipmentLine.saveEx();
					} else {
						MInOutLine shipmentLine = new MInOutLine(maybeShipment.get());

						BigDecimal quantity = salesOrderLine.getQtyReserved();
						shipmentLine.setOrderLine(salesOrderLine, 0, quantity);
						shipmentLine.saveEx(transactionName);
					}
				});
			}
		});

		Shipment.Builder builder = convertShipment(maybeShipment.get());

		return builder;
	}



	@Override
	public void deleteShipment(DeleteShipmentRequest request, StreamObserver<Empty> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Empty.Builder builder = deleteShipment(request);
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

	private Empty.Builder deleteShipment(DeleteShipmentRequest request) {
		if (request.getId() <= 0 && Util.isEmpty(request.getUuid(), true)) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		Trx.run(transactionName -> {
			int shipmentId = request.getId();
			if (shipmentId <= 0 && !Util.isEmpty(request.getUuid(), true)) {
				shipmentId = RecordUtil.getIdFromUuid(this.tableName, request.getUuid(), transactionName);
			}
			if (shipmentId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}

			MInOut shipment = new MInOut(Env.getCtx(), shipmentId, transactionName);
			if (shipment == null || shipment.getM_InOut_ID() <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
			if (!DocumentUtil.isDrafted(shipment)) {
				throw new AdempiereException("@Invalid@ @M_InOut_ID@ " + shipment.getDocumentNo());
			}
			shipment.deleteEx(true);
		});

		return Empty.newBuilder();
	}



	@Override
	public void processShipment(ProcessShipmentRequest request, StreamObserver<Shipment> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Shipment.Builder builder = processShipment(request);
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

	private Shipment.Builder processShipment(ProcessShipmentRequest request) {
		AtomicReference<MInOut> shipmentReference = new AtomicReference<MInOut>();

		Trx.run(transactionName -> {
			int shipmentId = request.getId();
			if (shipmentId <= 0 && !Util.isEmpty(request.getUuid(), true)) {
				shipmentId = RecordUtil.getIdFromUuid(this.tableName, request.getUuid(), transactionName);
			}
			if (shipmentId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}

			MInOut shipment = new MInOut(Env.getCtx(), shipmentId, transactionName);
			if (shipment == null || shipmentId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
			if (shipment.isProcessed()) {
				throw new AdempiereException("@M_InOut_ID@ @Processed@");
			}
			if (!shipment.processIt(X_M_InOut.DOCACTION_Complete)) {
				log.warning("@ProcessFailed@ :" + shipment.getProcessMsg());
				throw new AdempiereException("@ProcessFailed@ :" + shipment.getProcessMsg());
			}
			shipment.saveEx(transactionName);
			shipmentReference.set(shipment);
		});

		Shipment.Builder builder = convertShipment(shipmentReference.get());

		return builder;
	}



	@Override
	public void createShipmentLine(CreateShipmentLineRequest request, StreamObserver<ShipmentLine> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ShipmentLine.Builder builder = createShipmentLine(request);
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

	private ShipmentLine.Builder createShipmentLine(CreateShipmentLineRequest request) {
		if (request.getShipmentId() <= 0 && Util.isEmpty(request.getShipmentUuid(), true)) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		if (request.getProductId() <= 0 && Util.isEmpty(request.getProductUuid(), true)) {
			throw new AdempiereException("@M_Product_ID@ @NotFound@");
		}

		AtomicReference<MInOutLine> shipmentLineReference = new AtomicReference<MInOutLine>();

		Trx.run(transactionName -> {
			int shipmentId = request.getShipmentId();
			if (shipmentId <= 0) {
				shipmentId = RecordUtil.getIdFromUuid(this.tableName, request.getShipmentUuid(), transactionName);
				if (shipmentId <= 0) {
					throw new AdempiereException("@M_InOut_ID@ @NotFound@");
				}
			}
			MInOut shipment = new MInOut(Env.getCtx(), shipmentId, transactionName);
			if (shipment == null || shipment.getM_InOut_ID() <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
			if (shipment.isProcessed()) {
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
			MOrderLine salesOrderLine = new Query(
				Env.getCtx(),
				I_C_OrderLine.Table_Name,
				whereClause,
				transactionName
			)
				.setParameters(productId, shipmentId)
				.setClient_ID()
				.first();
			if (salesOrderLine == null || salesOrderLine.getC_OrderLine_ID() <= 0) {
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
				quantity = salesOrderLine.getQtyReserved();
			}
			// Validate available
			BigDecimal orderQuantityDelivered = salesOrderLine.getQtyOrdered().subtract(salesOrderLine.getQtyDelivered());
			if (orderQuantityDelivered.compareTo(quantity) < 0) {
				throw new AdempiereException("@QtyInsufficient@");
			}

			Optional<MInOutLine> maybeShipmentLine = Arrays.asList(shipment.getLines(true))
				.stream()
				.filter(shipmentLineTofind -> {
					return shipmentLineTofind.getC_OrderLine_ID() == salesOrderLine.getC_OrderLine_ID();
				})
				.findFirst();


			MInOutLine shipmentLine = null;
			if (maybeShipmentLine.isPresent()) {
				shipmentLine = maybeShipmentLine.get();
				BigDecimal orderQuantity = shipmentLine.getQtyEntered();
				orderQuantity = orderQuantity.add(quantity);
				shipmentLine.setQty(orderQuantity);
			} else {
				shipmentLine = new MInOutLine(shipment);
				shipmentLine.setOrderLine(salesOrderLine, 0, quantity);
				shipmentLine.setQty(quantity);
			}
			shipmentLine.saveEx(transactionName);

			shipmentLineReference.set(shipmentLine);
		});

		ShipmentLine.Builder builder = convertShipmentLine(
			shipmentLineReference.get()
		);

		return builder;
	}



	@Override
	public void deleteShipmentLine(DeleteShipmentLineRequest request, StreamObserver<Empty> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			Empty.Builder builder = deleteShipmentLine(request);
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

	private Empty.Builder deleteShipmentLine(DeleteShipmentLineRequest request) {
		if (request.getId() <= 0 && Util.isEmpty(request.getUuid(), true)) {
			throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
		}

		Trx.run(transactionName -> {
			int shipmentLineId = request.getId();
			if (shipmentLineId <= 0 && !Util.isEmpty(request.getUuid(), true)) {
				shipmentLineId = RecordUtil.getIdFromUuid(I_M_InOutLine.Table_Name, request.getUuid(), transactionName);
			}
			if (shipmentLineId <= 0) {
				throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
			}

			MInOutLine shipmentLine = new MInOutLine(Env.getCtx(), shipmentLineId, transactionName);
			if (shipmentLine == null || shipmentLine.getM_InOutLine_ID() <= 0) {
				throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
			}
			// Validate processed
			if (shipmentLine.isProcessed()) {
				throw new AdempiereException("@M_InOutLine_ID@ @Processed@");
			}
			shipmentLine.deleteEx(true);
		});

		return Empty.newBuilder();
	}



	@Override
	public void updateShipmentLine(UpdateShipmentLineRequest request, StreamObserver<ShipmentLine> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ShipmentLine.Builder builder = updateShipmentLine(request);
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

	private ShipmentLine.Builder updateShipmentLine(UpdateShipmentLineRequest request) {
		if (request.getId() <= 0 && Util.isEmpty(request.getUuid(), true)) {
			throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
		}

		AtomicReference<MInOutLine> maybeShipmentLine = new AtomicReference<MInOutLine>();

		Trx.run(transactionName -> {
			int shipmentLineId = request.getId();
			if (shipmentLineId <= 0) {
				shipmentLineId = RecordUtil.getIdFromUuid(I_M_InOutLine.Table_Name, request.getUuid(), transactionName);
				if (shipmentLineId <= 0) {
					throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
				}
			}

			MInOutLine shipmentLine = new MInOutLine(Env.getCtx(), shipmentLineId, transactionName);
			if (shipmentLine == null || shipmentLine.getM_InOutLine_ID() <= 0) {
				throw new AdempiereException("@M_InOutLine_ID@ @NotFound@");
			}
			// Validate processed
			if (shipmentLine.isProcessed()) {
				throw new AdempiereException("@M_InOutLine_ID@ @Processed@");
			}

			MOrderLine salesOrderLine = new MOrderLine(Env.getCtx(), shipmentLine.getC_OrderLine_ID(), transactionName);
			if (salesOrderLine == null || salesOrderLine.getC_OrderLine_ID() <= 0) {
				throw new AdempiereException("@C_OrderLine_ID@ @NotFound@");
			}

			BigDecimal quantity = BigDecimal.ONE;
			if (request.getQuantity() != null) {
				quantity = ValueUtil.getBigDecimalFromDecimal(request.getQuantity());
				if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
					quantity = BigDecimal.ONE;
				}
			}
			// Validate available
			BigDecimal orderQuantityDelivered = salesOrderLine.getQtyOrdered().subtract(salesOrderLine.getQtyDelivered());
			if (orderQuantityDelivered.compareTo(quantity) < 0) {
				throw new AdempiereException("@QtyInsufficient@");
			}

			shipmentLine.setQty(quantity);
			shipmentLine.setDescription(
				ValueUtil.validateNull(request.getDescription())
			);
			shipmentLine.saveEx(transactionName);

			maybeShipmentLine.set(shipmentLine);
		});

		ShipmentLine.Builder builder = convertShipmentLine(
			maybeShipmentLine.get()
		);

		return builder;
	}



	@Override
	public void listShipmentLines(ListShipmentLinesRequest request, StreamObserver<ListShipmentLinesResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}

			ListShipmentLinesResponse.Builder builderList = listShipmentLines(request);
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

	ShipmentLine.Builder convertShipmentLine(MInOutLine shipmentLine) {
		ShipmentLine.Builder builder = ShipmentLine.newBuilder();
		if (shipmentLine == null || shipmentLine.getM_InOutLine_ID() <= 0) {
			return builder;
		}

		builder.setId(shipmentLine.getM_InOutLine_ID())
			.setUuid(
				ValueUtil.validateNull(shipmentLine.getUUID())
			)
			.setProduct(
				convertProduct(shipmentLine.getM_Product_ID())
			)
			.setDescription(
				ValueUtil.validateNull(shipmentLine.getDescription())
			)
			.setQuantity(
				ValueUtil.getDecimalFromBigDecimal(shipmentLine.getQtyEntered())
			)
			.setLine(shipmentLine.getLine())
		;
		MOrderLine orderLine = new MOrderLine(Env.getCtx(), shipmentLine.getC_OrderLine_ID(), null);
		if (orderLine != null && orderLine.getC_OrderLine_ID() > 0) {
			builder.setOrderLineId(orderLine.getC_OrderLine_ID())
				.setOrderLineUuid(
					ValueUtil.validateNull(orderLine.getUUID())
				)
			;
		}

		return builder;
	}

	private ListShipmentLinesResponse.Builder listShipmentLines(ListShipmentLinesRequest request) {
		if (request.getShipmentId() <= 0 && Util.isEmpty(request.getShipmentUuid(), true)) {
			throw new AdempiereException("@M_InOut_ID@ @NotFound@");
		}
		int shipmentId = request.getShipmentId();
		if (shipmentId <= 0) {
			shipmentId = RecordUtil.getIdFromUuid(I_M_InOut.Table_Name, request.getShipmentUuid(), null);
			if (shipmentId <= 0) {
				throw new AdempiereException("@M_InOut_ID@ @NotFound@");
			}
		}

		Query query = new Query(
			Env.getCtx(),
			I_M_InOutLine.Table_Name,
			I_M_InOutLine.COLUMNNAME_M_InOut_ID + " = ?",
			null
		)
			.setParameters(shipmentId)
			.setClient_ID()
			.setOnlyActiveRecords(true)
			.setApplyAccessFilter(MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO)
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

		ListShipmentLinesResponse.Builder builderList = ListShipmentLinesResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(
				ValueUtil.validateNull(nexPageToken)
			)
		;

		query.setLimit(limit, offset)
			.list(MInOutLine.class)
			.forEach(shipmentLine -> {
				ShipmentLine.Builder builder = convertShipmentLine(shipmentLine);
				builderList.addRecords(builder);
			});
		;

		return builderList;
	}

}
