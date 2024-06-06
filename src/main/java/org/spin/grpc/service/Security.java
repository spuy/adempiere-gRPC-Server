/************************************************************************************
 * Copyright (C) 2012-present E.R.P. Consultores y Asociados, C.A.                  *
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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.adempiere.core.domains.models.I_AD_Browse;
import org.adempiere.core.domains.models.I_AD_Form;
import org.adempiere.core.domains.models.I_AD_Menu;
import org.adempiere.core.domains.models.I_AD_Org;
import org.adempiere.core.domains.models.I_AD_Process;
import org.adempiere.core.domains.models.I_AD_Role;
import org.adempiere.core.domains.models.I_AD_User_Authentication;
import org.adempiere.core.domains.models.I_AD_Window;
import org.adempiere.core.domains.models.I_AD_Workflow;
import org.adempiere.core.domains.models.I_M_Warehouse;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.MBrowse;
import org.compiere.model.MClient;
import org.compiere.model.MClientInfo;
import org.compiere.model.MCountry;
import org.compiere.model.MCurrency;
import org.compiere.model.MForm;
import org.compiere.model.MMenu;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPreference;
import org.compiere.model.MProcess;
import org.compiere.model.MRole;
import org.compiere.model.MSession;
import org.compiere.model.MTree;
import org.compiere.model.MTreeNode;
import org.compiere.model.MUser;
import org.compiere.model.MWarehouse;
import org.compiere.model.MWindow;
import org.compiere.model.Query;
import org.compiere.util.CCache;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Login;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.compiere.wf.MWorkflow;
import org.spin.authentication.services.OpenIDUtil;
import org.spin.backend.grpc.security.ChangeRoleRequest;
import org.spin.backend.grpc.security.Client;
import org.spin.backend.grpc.security.DictionaryEntity;
import org.spin.backend.grpc.security.DictionaryType;
import org.spin.backend.grpc.security.GetDictionaryAccessRequest;
import org.spin.backend.grpc.security.GetDictionaryAccessResponse;
import org.spin.backend.grpc.security.ListOrganizationsRequest;
import org.spin.backend.grpc.security.ListOrganizationsResponse;
import org.spin.backend.grpc.security.ListRolesRequest;
import org.spin.backend.grpc.security.ListRolesResponse;
import org.spin.backend.grpc.security.ListServicesRequest;
import org.spin.backend.grpc.security.ListServicesResponse;
import org.spin.backend.grpc.security.ListWarehousesRequest;
import org.spin.backend.grpc.security.ListWarehousesResponse;
import org.spin.backend.grpc.security.LoginOpenIDRequest;
import org.spin.backend.grpc.security.LoginRequest;
import org.spin.backend.grpc.security.LogoutRequest;
import org.spin.backend.grpc.security.Menu;
import org.spin.backend.grpc.security.MenuRequest;
import org.spin.backend.grpc.security.MenuResponse;
import org.spin.backend.grpc.security.Organization;
import org.spin.backend.grpc.security.Role;
import org.spin.backend.grpc.security.SecurityGrpc.SecurityImplBase;
import org.spin.backend.grpc.security.Service;
import org.spin.backend.grpc.security.Session;
import org.spin.backend.grpc.security.SessionInfo;
import org.spin.backend.grpc.security.SessionInfoRequest;
import org.spin.backend.grpc.security.SetSessionAttributeRequest;
import org.spin.backend.grpc.security.UserInfo;
import org.spin.backend.grpc.security.UserInfoRequest;
import org.spin.backend.grpc.security.Warehouse;
import org.spin.base.util.ContextManager;
import org.spin.base.util.PreferenceUtil;
import org.spin.model.MADAttachmentReference;
import org.spin.model.MADToken;
import org.spin.service.grpc.authentication.SessionManager;
import org.spin.service.grpc.util.db.LimitUtil;
import org.spin.service.grpc.util.value.BooleanManager;
import org.spin.service.grpc.util.value.NumberManager;
import org.spin.service.grpc.util.value.TimeManager;
import org.spin.service.grpc.util.value.ValueManager;
import org.spin.util.AttachmentUtil;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 * Security service
 */
public class Security extends SecurityImplBase {
	
	/**
	 * Load Validators
	 */
	public Security() {
		super();
		DB.validateSupportedUUIDFromDB();
		MCountry.getCountries(Env.getCtx());
	}
	
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(Security.class);
	/**	Menu */
	private static CCache<String, MenuResponse.Builder> menuCache = new CCache<String, MenuResponse.Builder>("Menu_for_User", 30, 0);



	@Override
	public void listServices(ListServicesRequest request, StreamObserver<ListServicesResponse> responseObserver) {
		try {
			log.fine("List Services");
			ListServicesResponse.Builder serviceBuilder = ListServicesResponse.newBuilder();
			Hashtable<Integer, Map<String, String>> services = OpenIDUtil.getAuthenticationServices();
			services.entrySet().parallelStream().forEach(service -> {
				Service.Builder availableService = Service.newBuilder();
				availableService.setId(service.getKey())
					.setDisplayName(
						ValueManager.validateNull(
							service.getValue().get(OpenIDUtil.DISPLAYNAME)
						)
					)
					.setAuthorizationUri(
						ValueManager.validateNull(
							service.getValue().get(OpenIDUtil.ENDPOINT_Authorization_URI)
						)
					)
				;
				serviceBuilder.addServices(availableService);
			});
			responseObserver.onNext(serviceBuilder.build());
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
	public void runLogout(LogoutRequest request, StreamObserver<Session> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			Session.Builder sessionBuilder = logoutSession(request);
			responseObserver.onNext(sessionBuilder.build());
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
	public void getSessionInfo(SessionInfoRequest request, StreamObserver<SessionInfo> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			SessionInfo.Builder sessionBuilder = getSessionInfo(request);
			responseObserver.onNext(sessionBuilder.build());
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
	public void setSessionAttribute(SetSessionAttributeRequest request, StreamObserver<Session> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}
			Session.Builder sessionBuilder = setSessionAttribute(request);
			responseObserver.onNext(sessionBuilder.build());
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

	private Session.Builder setSessionAttribute(SetSessionAttributeRequest request) {
		Properties context = Env.getCtx();
		//	Language
		String language = Env.getAD_Language(context);
		if (!Util.isEmpty(request.getLanguage(), true)) {
			language = ContextManager.getDefaultLanguage(request.getLanguage());
		}

		// warehouse
		int warehouseId = Env.getContextAsInt(context, "#M_Warehouse_ID");
		if (request.getWarehouseId() > 0) {
			warehouseId = request.getWarehouseId();
		}

		MSession currentSession = MSession.get(context, false);

		int userId = currentSession.getCreatedBy();
		MRole role = MRole.get(context, currentSession.getAD_Role_ID());

		// Session values
		boolean isOpenID = false;
		if (currentSession.get_ColumnIndex(I_AD_User_Authentication.COLUMNNAME_AD_User_Authentication_ID) >= 0) {
			isOpenID = currentSession.get_ValueAsInt(I_AD_User_Authentication.COLUMNNAME_AD_User_Authentication_ID) > 0;
		}

		// Session values
		Session.Builder builder = Session.newBuilder();
		final String bearerToken = SessionManager.createSessionAndGetToken(
			currentSession.getWebSession(),
			language,
			role.getAD_Role_ID(),
			userId,
			currentSession.getAD_Org_ID(),
			warehouseId,
			isOpenID
		);

		// Update session preferences
		PreferenceUtil.saveSessionPreferences(
			userId, language, role.getAD_Role_ID(), role.getAD_Client_ID(), currentSession.getAD_Org_ID(), warehouseId
		);

		builder.setToken(bearerToken);
		return builder;
	}


	@Override
	public void getUserInfo(UserInfoRequest request, StreamObserver<UserInfo> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			UserInfo.Builder UserInfo = getUserInfo(request);
			responseObserver.onNext(UserInfo.build());
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
	public void listRoles(ListRolesRequest request, StreamObserver<ListRolesResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListRolesResponse.Builder rolesList = listRoles(request);
			responseObserver.onNext(rolesList.build());
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
	 * Convert languages to gRPC
	 * @param context
	 * @param request
	 * @return
	 */
	private ListRolesResponse.Builder listRoles(ListRolesRequest request) {
		int userId = Env.getAD_User_ID(Env.getCtx());

		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		final String whereClause = "EXISTS("
				+ "SELECT 1 FROM AD_User_Roles ur "
				+ "WHERE ur.AD_Role_ID = AD_Role.AD_Role_ID AND ur.AD_User_ID = ?"
			+ ")"
			+ "AND ("
				+ "(IsAccessAllOrgs = 'Y' AND EXISTS(SELECT 1 FROM AD_Org AS o "
				+ "WHERE (o.AD_Client_ID = AD_Client_ID OR o.AD_Org_ID = 0) "
				+ "AND o.IsActive = 'Y' AND o.IsSummary = 'N'))"
			+ "OR ("
				+ "IsUseUserOrgAccess = 'N' AND EXISTS(SELECT 1 FROM AD_Role_OrgAccess AS ro "
				+ "INNER JOIN AD_Org AS o ON o.AD_Org_ID = ro.AD_Org_ID AND o.IsSummary = 'N' "
				+ "WHERE ro.AD_Role_ID = AD_Role.AD_Role_ID AND ro.IsActive = 'Y')) "
			+ "OR ("
				+ "IsUseUserOrgAccess = 'Y' AND EXISTS(SELECT 1 FROM AD_User_OrgAccess AS uo "
				+ "INNER JOIN AD_Org AS o ON o.AD_Org_ID = uo.AD_Org_ID AND o.IsSummary = 'N' "
				+ "WHERE uo.AD_User_ID = ? AND uo.IsActive = 'Y')) "
			+ ")"
		;
		Query query = new Query(
			Env.getCtx(),
			I_AD_Role.Table_Name,
			whereClause,
			null
		)
			.setParameters(userId, userId)
			.setOnlyActiveRecords(true)
		;
		int count = query.count();

		ListRolesResponse.Builder builder = ListRolesResponse.newBuilder();
		query.setLimit(limit, offset)
			.<MRole>list()
			.forEach(role -> {
				builder.addRoles(
					convertRole(role)
				);
			});
		//	
		builder.setRecordCount(count);
		//	Set page token
		if(count > offset && count > limit) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}
		builder.setNextPageToken(
			ValueManager.validateNull(nexPageToken)
		);
		//	Return
		return builder;
	}



	@Override
	public void listOrganizations(ListOrganizationsRequest request,
			StreamObserver<ListOrganizationsResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListOrganizationsResponse.Builder organizationsList = listOrganizations(request);
			responseObserver.onNext(organizationsList.build());
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
	 * Convert Organization to list
	 * @param request
	 * @return
	 */
	private ListOrganizationsResponse.Builder listOrganizations(ListOrganizationsRequest request) {
		final int roleId = request.getRoleId();
		if(roleId < 0) {
			throw new AdempiereException("@AD_Role_ID@ @NotFound@");
		}
		MRole role = MRole.get(Env.getCtx(), roleId);

		//	get from role access
		if (role == null || !role.isActive()) {
			throw new AdempiereException("@AD_Role_ID@ @NotFound@");
		}

		List<Object> parameters = new ArrayList<Object>();
		String whereClause = "1 = 2";
		//	get from role access
		if (role.isAccessAllOrgs()) {
			whereClause = "(EXISTS(SELECT 1 FROM AD_Role r "
				+ "WHERE r.AD_Client_ID = AD_Org.AD_Client_ID "
				+ "AND r.AD_Role_ID = ? "
				+ "AND r.IsActive = 'Y') "
				+ "OR AD_Org_ID = 0) "
			;
			parameters.add(role.getAD_Role_ID());
		} else {
			if(role.isUseUserOrgAccess()) {
				whereClause = "EXISTS(SELECT 1 FROM AD_User_OrgAccess ua "
					+ "WHERE ua.AD_Org_ID = AD_Org.AD_Org_ID "
					+ "AND ua.AD_User_ID = ? "
					+ "AND ua.IsActive = 'Y')";
				parameters.add(Env.getAD_User_ID(Env.getCtx()));
			} else {
				whereClause = "EXISTS(SELECT 1 FROM AD_Role_OrgAccess ra "
					+ "WHERE ra.AD_Org_ID = AD_Org.AD_Org_ID "
					+ "AND ra.AD_Role_ID = ? "
					+ "AND ra.IsActive = 'Y')";
				parameters.add(role.getAD_Role_ID());
			}
		}
		whereClause += " AND IsSummary = ? ";
		parameters.add(false);

		Query query = new Query(
			Env.getCtx(),
			I_AD_Org.Table_Name,
			whereClause,
			null
		)
			.setParameters(parameters)
			.setOnlyActiveRecords(true)
			.setApplyAccessFilter(MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO)
		;

		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		int count = query.count();
		//	Set page token
		if(count > limit) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListOrganizationsResponse.Builder builder = ListOrganizationsResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(
				ValueManager.validateNull(nexPageToken)
			)
		;
		//	Get List
		query.setLimit(limit, offset)
			// .getIDsAsList() // do not use the list of identifiers because it cannot be instantiated zero (0)
			.<MOrg>list()
			.forEach(organization -> {
				// MOrg.get static method not instance the organization in 0=* (asterisk)
				// MOrg organization = MOrg.get(Env.getCtx(), organizationId);
				Organization.Builder organizationBuilder = convertOrganization(organization);
				builder.addOrganizations(organizationBuilder);
			});
		//	
		return builder;
	}

	/**
	 * Convert organization
	 * @param organization
	 * @return
	 */
	public static Organization.Builder convertOrganization(MOrg organization) {
		Organization.Builder organizationBuilder = Organization.newBuilder();
		if (organization == null) {
			return organizationBuilder;
		}
		MOrgInfo organizationInfo = MOrgInfo.get(Env.getCtx(), organization.getAD_Org_ID(), null);

		if(organizationInfo.getCorporateBrandingImage_ID() > 0 && AttachmentUtil.getInstance().isValidForClient(organizationInfo.getAD_Client_ID())) {
			MClientInfo clientInfo = MClientInfo.get(Env.getCtx(), organizationInfo.getAD_Client_ID());
			MADAttachmentReference attachmentReference = MADAttachmentReference.getByImageId(Env.getCtx(), clientInfo.getFileHandler_ID(), organizationInfo.getCorporateBrandingImage_ID(), null);
			if(attachmentReference != null
					&& attachmentReference.getAD_AttachmentReference_ID() > 0) {
					organizationBuilder.setCorporateBrandingImage(
					ValueManager.validateNull(
						attachmentReference.getFileName()
					)
				);
			}
		}
		
		organizationBuilder.setId(
				organization.getAD_Org_ID()
			)
			.setUuid(
				ValueManager.validateNull(
					organization.getUUID()
				)
			)
			.setName(
				ValueManager.validateNull(
					organization.getName()
				)
			)
			.setDescription(
				ValueManager.validateNull(
					organization.getDescription()
				)
			)
			.setDuns(
				ValueManager.validateNull(
					organizationInfo.getDUNS()
				)
			)
			.setTaxId(
				ValueManager.validateNull(
					organizationInfo.getTaxID()
				)
			)
			.setPhone(
				ValueManager.validateNull(
					organizationInfo.getPhone()
				)
			)
			.setPhone2(
				ValueManager.validateNull(
					organizationInfo.getPhone2()
				)
			)
			.setFax(
				ValueManager.validateNull(
					organizationInfo.getFax()
				)
			)
			.setIsReadOnly(false)
		;
		return organizationBuilder;
	}



	@Override
	public void listWarehouses(ListWarehousesRequest request, StreamObserver<ListWarehousesResponse> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListWarehousesResponse.Builder organizationsList = listWarehouses(request);
			responseObserver.onNext(organizationsList.build());
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
	 * Convert warehouses list
	 * @param request
	 * @return
	 */
	private ListWarehousesResponse.Builder listWarehouses(ListWarehousesRequest request) {
		int organizationId = request.getOrganizationId();
		final String whereClause = "AD_Org_ID = ? AND IsInTransit = ? ";
		Query query = new Query(
			Env.getCtx(),
			I_M_Warehouse.Table_Name,
			whereClause,
			null
		)
			.setParameters(organizationId, false)
			.setOnlyActiveRecords(true)
			.setApplyAccessFilter(MRole.SQL_FULLYQUALIFIED, MRole.SQL_RO)
		;

		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		// int offset = (pageNumber - 1) * limit;
		int count = query.count();
		//	Set page token
		if(count > limit) {
			nexPageToken = LimitUtil.getPagePrefix(SessionManager.getSessionUuid()) + (pageNumber + 1);
		}

		ListWarehousesResponse.Builder builder = ListWarehousesResponse.newBuilder()
			.setRecordCount(count)
			.setNextPageToken(
				ValueManager.validateNull(
					nexPageToken
				)
			)
		;

		//	Get List
		// TODO: Fix .setLimit combined with .setApplyAccessFilter and with access record (ROWNUM error)
		query //.setLimit(limit, offset)
			// .getIDsAsList() // do not use the list of identifiers because it cannot be instantiated zero (0)
			.<MWarehouse>list()
			.forEach(warehouse -> {
				// MWarehouse.get static method not instance the warehouse in 0=* (asterisk)
				// MWarehouse warehouse = MWarehouse.get(Env.getCtx(), warehouseId);
				Warehouse.Builder warehouseBuilder = convertWarehouse(warehouse);
				builder.addWarehouses(
					warehouseBuilder
				);
			});
		//	
		return builder;
	}

	/**
	 * Convert warehouse
	 * @param warehouse
	 * @return
	 */
	public static Warehouse.Builder convertWarehouse(MWarehouse warehouse) {
		Warehouse.Builder warehouseBuilder = Warehouse.newBuilder();
		if (warehouse == null) {
			return warehouseBuilder;
		}
		warehouseBuilder.setId(
				warehouse.getM_Warehouse_ID()
			)
			.setUuid(
				ValueManager.validateNull(
					warehouse.getUUID()
				)
			)
			.setValue(
				ValueManager.validateNull(
					warehouse.getValue()
				)
			)
			.setName(
				ValueManager.validateNull(
					warehouse.getName()
				)
			)
			.setDescription(
				ValueManager.validateNull(
					warehouse.getDescription()
				)
			)
		;
		return warehouseBuilder;
	}



	/**
	 * Get User ID
	 * @param userName
	 * @param userPass
	 * @return
	 */
	private int getUserId(String userName, String userPass) {
		Login login = new Login(Env.getCtx());
		return login.getAuthenticatedUserId(userName, userPass);
	}



	@Override
	public void runLogin(LoginRequest request, StreamObserver<Session> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Session Requested = " + request.getUserName());
			Session.Builder sessionBuilder = runLogin(request, true);
			responseObserver.onNext(sessionBuilder.build());
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
	 * Get and convert session
	 * @param request
	 * @return
	 */
	private Session.Builder runLogin(LoginRequest request, boolean isDefaultRole) {
		//	Validate if is token based
		int userId = -1;
		int roleId = -1;
		int organizationId = -1;
		int warehouseId = -1;
		if(!Util.isEmpty(request.getToken())) {
			MADToken token = SessionManager.createSessionFromToken(request.getToken());
			if(Optional.ofNullable(token).isPresent()) {
				userId = token.getAD_User_ID();
				roleId = token.getAD_Role_ID();
				organizationId = token.getAD_Org_ID();
			}
		} else {
			userId = getUserId(request.getUserName(), request.getUserPass());
			//	Get Values from role
			if(userId < 0) {
				throw new AdempiereException("@AD_User_ID@ / @AD_Role_ID@ / @AD_Org_ID@ @NotFound@");
			}
			MUser user = MUser.get(Env.getCtx(), userId);
			if (user == null) {
				throw new AdempiereException("@AD_User_ID@ / @AD_Role_ID@ / @AD_Org_ID@ @NotFound@");
			}

			List<MPreference> preferencesList = new ArrayList<MPreference>();
			if (isDefaultRole) {
				preferencesList = PreferenceUtil.getSessionPreferences(userId);
				for (MPreference preference: preferencesList) {
					String attibuteName = preference.getAttribute();
					String attributeValue = preference.getValue();
					if (!Util.isEmpty(attributeValue, true)) {
						if (attibuteName.equals(PreferenceUtil.P_ROLE)) {
							roleId = NumberManager.getIntFromString(attributeValue);
						} else if (attibuteName.equals(PreferenceUtil.P_CLIENT)) {
							// clientId = NumberManager.getIntFromString(attributeValue);
						} else if (attibuteName.equals(PreferenceUtil.P_ORG)) {
							organizationId = NumberManager.getIntFromString(attributeValue);
						} else if (attibuteName.equals(PreferenceUtil.P_WAREHOUSE)) {
							warehouseId = NumberManager.getIntFromString(attributeValue);
						} else if (attibuteName.equals(PreferenceUtil.P_LANGUAGE)) {
							// language = attributeValue;
						}
					}
				}
			}

			// TODO: Validate values
			if ((preferencesList == null || preferencesList.isEmpty()) || (request.getRoleId() > 0 && request.getOrganizationId() > 0)) {
				if (request.getRoleId() >= 0) {
					roleId = request.getRoleId();
				}
				if(request.getOrganizationId() >= 0) {
					organizationId = request.getOrganizationId();
				}
				if(request.getWarehouseId() >= 0) {
					warehouseId = request.getWarehouseId();
				}
			}
		}
		return createValidSession(
			isDefaultRole,
			request.getClientVersion(),
			request.getLanguage(),
			roleId,
			userId,
			organizationId,
			warehouseId,
			false
		);
	}

	/**
	 * Create Valid Session After Login
	 * @param isDefaultRole
	 * @param clientVersion
	 * @param language
	 * @param roleId
	 * @param userId
	 * @param organizationId
	 * @param warehouseId
	 * @return
	 */
	private Session.Builder createValidSession(boolean isDefaultRole, String clientVersion, String language, int roleId, int userId, int organizationId, int warehouseId, boolean isOpenID) {
		Session.Builder builder = Session.newBuilder();
			if(isDefaultRole && roleId <= 0) {
				roleId = SessionManager.getDefaultRoleId(userId);
			}
			//	Get Values from role
			if(roleId < 0) {
				throw new AdempiereException("@AD_User_ID@ / @AD_Role_ID@ / @AD_Org_ID@ @NotFound@");
			}

			//	Organization
			if(organizationId <= 0) {
				organizationId = SessionManager.getDefaultOrganizationId(roleId, userId);
			}
			if(organizationId < 0) {
				throw new AdempiereException("@AD_User_ID@: @AD_Org_ID@ @NotFound@");
			}

			if (organizationId == 0) {
				warehouseId = 0;
			} else if (warehouseId <= 0) {
				warehouseId = SessionManager.getDefaultWarehouseId(organizationId);
			}

			//	Session values
			final String bearerToken = SessionManager.createSessionAndGetToken(
				clientVersion,
				language,
				roleId,
				userId,
				organizationId,
				warehouseId,
				isOpenID
			);
			builder.setToken(bearerToken);
			//	Return session
			return builder;
	}



	@Override
	public void runLoginOpenID(LoginOpenIDRequest request, StreamObserver<Session> responseObserver) {
		try {
			log.fine("Run Login Open ID");
			Session.Builder sessionBuilder = createSessionFromOpenID(request);
			responseObserver.onNext(sessionBuilder.build());
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
	 * Get and convert session
	 * @param request
	 * @return
	 */
	private Session.Builder createSessionFromOpenID(LoginOpenIDRequest request) {
		MUser validUser = OpenIDUtil.getUserAuthenticated(request.getCodeParameter(), request.getStateParameter());
		if(validUser == null) {
			throw new AdempiereException("@AD_User_ID@ / @AD_Role_ID@ / @AD_Org_ID@ @NotFound@");
		}
		return createValidSession(
			true,
			request.getClientVersion(),
			request.getLanguage(),
			-1,
			validUser.getAD_User_ID(),
			-1,
			-1,
			true
		);
	}


	@Override
	public void runChangeRole(ChangeRoleRequest request, StreamObserver<Session> responseObserver) {
		try {
			if (request == null) {
				throw new AdempiereException("Object Request Null");
			}
			Session.Builder sessionBuilder = runChangeRole(request);
			responseObserver.onNext(sessionBuilder.build());
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
	 * Change current role
	 * @param request
	 * @return
	 */
	private Session.Builder runChangeRole(ChangeRoleRequest request) {
		DB.validateSupportedUUIDFromDB();
		Properties context = Env.getCtx();
		//	Get / Validate Session
		MSession currentSession = MSession.get(context, false, false);
		int userId = currentSession.getCreatedBy();
		//	Get Values from role
		int roleId = request.getRoleId();
		if (roleId < 0) {
			throw new AdempiereException("@AD_User_ID@ / @AD_Role_ID@ / @AD_Org_ID@ @NotFound@");
		}
		MRole role = MRole.get(context, roleId);

		// Get organization
		int organizationId = -1;
		if (request.getOrganizationId() >= 0) {
			organizationId = request.getOrganizationId();
			if (!role.isOrgAccess(organizationId, true)) {
				// invlaid organization from role
				organizationId = -1;
			}
		}
		if (organizationId < 0) {
			organizationId = SessionManager.getDefaultOrganizationId(roleId, userId);
			if (organizationId < 0) {
				// TODO: Verify it access
				organizationId = 0;
			}
		}

		// Get warehouse
		int warehouseId = -1;
		if (request.getWarehouseId() >= 0) {
			warehouseId = request.getWarehouseId();
		}
		if (warehouseId < 0) {
			warehouseId = 0;
		}

		// Default preference values
		String language = request.getLanguage();
		if (Util.isEmpty(language, true)) {
			// set language with current session
			language = Env.getContext(currentSession.getCtx(), Env.LANGUAGE);
		}

		// Session values
		Session.Builder builder = Session.newBuilder();
		boolean isOpenID = false;
		if (currentSession.get_ColumnIndex(I_AD_User_Authentication.COLUMNNAME_AD_User_Authentication_ID) >= 0) {
			isOpenID = currentSession.get_ValueAsInt(I_AD_User_Authentication.COLUMNNAME_AD_User_Authentication_ID) > 0;
		}
		final String bearerToken = SessionManager.createSessionAndGetToken(
			currentSession.getWebSession(),
			language,
			roleId,
			userId,
			organizationId,
			warehouseId,
			isOpenID
		);
		builder.setToken(bearerToken);
		// Logout
		logoutSession(LogoutRequest.newBuilder().build());

		// Update session preferences
		PreferenceUtil.saveSessionPreferences(
			userId, language, roleId, role.getAD_Client_ID(), organizationId, warehouseId
		);

		// Return session
		return builder;
	}


	/**
	 * Populate default values and preferences for session
	 * @param session
	 */
	private void populateDefaultPreferences(SessionInfo.Builder session) {
		MCountry country = MCountry.get(Env.getCtx(), Env.getContextAsInt(Env.getCtx(), "#C_Country_ID"));
		MCurrency currency = MCurrency.get(Env.getCtx(), country.getC_Currency_ID());
		//	Set values for currency
		session.setCountryId(country.getC_Country_ID());
		session.setCountryCode(
			ValueManager.validateNull(
				country.getCountryCode()
			)
		);
		session.setCountryName(
			ValueManager.validateNull(
				country.getName()
			)
		);
		session.setDisplaySequence(
			ValueManager.validateNull(
				country.getDisplaySequence()
			)
		);
		session.setCurrencyIsoCode(
			ValueManager.validateNull(currency.getISO_Code()));
		session.setCurrencyName(
			ValueManager.validateNull(currency.getDescription()));
		session.setCurrencySymbol(
			ValueManager.validateNull(currency.getCurSymbol()));
		session.setStandardPrecision(currency.getStdPrecision());
		session.setCostingPrecision(currency.getCostingPrecision());
		session.setLanguage(
			ValueManager.validateNull(ContextManager.getDefaultLanguage(Env.getAD_Language(Env.getCtx()))));
		//	Set default context
		Struct.Builder contextValues = Struct.newBuilder();
		Env.getCtx().entrySet()
			.stream()
			.filter(keyValue -> {
				return ContextManager.isSessionContext(
					String.valueOf(
						keyValue.getKey()
					)
				);
			})
			.forEach(contextKeyValue -> {
				contextValues.putFields(
					contextKeyValue.getKey().toString(),
					convertObjectFromContext(
						(String) contextKeyValue.getValue()
					).build()
				);
			});
		session.setDefaultContext(contextValues);
	}

	/**
	 * Convert Values from Context
	 * @param value
	 * @return
	 */
	private Value.Builder convertObjectFromContext(String value) {
		Value.Builder builder = Value.newBuilder();
		if (Util.isEmpty(value)) {
			return builder;
		}
		if (NumberManager.isNumeric(value)) {
			builder.setNumberValue(NumberManager.getIntFromString(value));
		} else if (BooleanManager.isBoolean(value)) {
			boolean booleanValue = BooleanManager.getBooleanFromString(value.trim());
			builder.setBoolValue(booleanValue);
		} else if(TimeManager.isDate(value)) {
			return ValueManager.getValueFromTimestamp(
				TimeManager.getTimestampFromString(value)
			);
		} else {
			builder.setStringValue(
				ValueManager.validateNull(value)
			);
		}
		//	
		return builder;
	}


	/**
	 * Logout session
	 * @param request
	 * @return
	 */
	private Session.Builder logoutSession(LogoutRequest request) {
		MSession session = MSession.get(Env.getCtx(), false);
		//	Logout
		session.logout();
		//	Session values
		Session.Builder builder = Session.newBuilder();
		//	Return session
		return builder;
	}

	/**
	 * Logout session
	 * @param request
	 * @return
	 */
	private SessionInfo.Builder getSessionInfo(SessionInfoRequest request) {
		Properties context = Env.getCtx();
		MSession session = MSession.get(context, false);
		//	Load default preference values
		SessionManager.loadDefaultSessionValues(context, Env.getAD_Language(context));
		//	Session values
		SessionInfo.Builder builder = SessionInfo.newBuilder();
		builder.setId(
				session.getAD_Session_ID()
			)
			.setUuid(
				session.getUUID()
			)
			.setName(
				ValueManager.validateNull(
					session.getDescription()
				)
			)
			.setUserInfo(
				convertUserInfo(
					MUser.get(context, session.getCreatedBy())
				).build()
			)
		;
		//	Set role
		Role.Builder roleBuilder = convertRole(
			MRole.get(context, session.getAD_Role_ID())
		);
		builder.setRole(roleBuilder.build());
		//	Set default context
		populateDefaultPreferences(builder);
		//	Return session
		return builder;
	}
	
	/**
	 * Convert User entity
	 * @param user
	 * @return
	 */
	private UserInfo.Builder convertUserInfo(MUser user) {
		UserInfo.Builder userInfo = UserInfo.newBuilder()
			.setId(
				user.getAD_User_ID()
			)
			.setUuid(
				ValueManager.validateNull(
					user.getUUID()
				)
			)
			.setValue(
				ValueManager.validateNull(
					user.getValue()
				)
			)
			.setName(
				ValueManager.validateNull(
					user.getName()
				)
			)
			.setDescription(
				ValueManager.validateNull(
					user.getDescription()
				)
			)
			.setComments(
				ValueManager.validateNull(
					user.getComments()
				)
			)
		;
		int clientId = Env.getAD_Client_ID(Env.getCtx());
		if(user.getLogo_ID() > 0 && AttachmentUtil.getInstance().isValidForClient(clientId)) {
			MClientInfo clientInfo = MClientInfo.get(Env.getCtx(), clientId);
			MADAttachmentReference attachmentReference = MADAttachmentReference.getByImageId(
				Env.getCtx(),
				clientInfo.getFileHandler_ID(),
				user.getLogo_ID(),
				null
			);
			if(attachmentReference != null
					&& attachmentReference.getAD_AttachmentReference_ID() > 0) {
				userInfo.setImage(
					ValueManager.validateNull(
						attachmentReference.getFileName()
					)
				);
			}
		}
		userInfo.setConnectionTimeout(SessionManager.getSessionTimeout(user));
		return userInfo;
	}
	
	/**
	 * Get User Info
	 * @param request
	 * @return
	 */
	private UserInfo.Builder getUserInfo(UserInfoRequest request) {
		MSession session = MSession.get(Env.getCtx(), false);
		List<MRole> roleList = new Query(Env.getCtx(), I_AD_Role.Table_Name, 
				"EXISTS(SELECT 1 FROM AD_User_Roles ur "
				+ "WHERE ur.AD_Role_ID = AD_Role.AD_Role_ID "
				+ "AND ur.AD_User_ID = ?)", null)
				.setParameters(session.getCreatedBy())
				.setOnlyActiveRecords(true)
				.<MRole>list();
		//	Validate
		if(roleList == null
				|| roleList.size() == 0) {
			return null;
		}
		//	Get it
		MUser user = MUser.get(Env.getCtx(), session.getCreatedBy());
		if(user == null
				|| user.getAD_User_ID() <= 0) {
			throw new AdempiereException("@AD_User_ID@ @NotFound@");
		}
		//	Return
		return convertUserInfo(user);
	}


	private Client.Builder convertClient(int clientId) {
		Client.Builder builder = Client.newBuilder();
		if (clientId < 0) {
			return builder;
		}
		MClient client = MClient.get(Env.getCtx(), clientId);
		if (client == null) {
			return builder;
		}
		builder.setId(
				client.getAD_Client_ID()
			)
			.setUuid(
				ValueManager.validateNull(
					client.getUUID()
				)
			)
			.setName(
				ValueManager.validateNull(
					client.getName()
				)
			)
			.setDescription(
				ValueManager.validateNull(
					client.getDescription()
				)
			)
		;

		// Add client logo
		if (AttachmentUtil.getInstance().isValidForClient(client.getAD_Client_ID())) {
			MClientInfo clientInfo = MClientInfo.get(Env.getCtx(), client.getAD_Client_ID());
			if (clientInfo.getLogo_ID() > 0) {
				MADAttachmentReference attachmentReference = MADAttachmentReference.getByImageId(
					Env.getCtx(),
					clientInfo.getFileHandler_ID(),
					clientInfo.getLogo_ID(),
					null
				);
				if (attachmentReference != null && attachmentReference.getAD_AttachmentReference_ID() > 0) {
					builder.setLogo(
						ValueManager.validateNull(
							attachmentReference.getFileName()
						)
					);
				}
			}
			if (clientInfo.getLogoReport_ID() > 0) {
				MADAttachmentReference attachmentReference = MADAttachmentReference.getByImageId(
					Env.getCtx(),
					clientInfo.getFileHandler_ID(),
					clientInfo.getLogoReport_ID(),
					null
				);
				if (attachmentReference != null && attachmentReference.getAD_AttachmentReference_ID() > 0) {
					builder.setLogoReport(
						ValueManager.validateNull(
							attachmentReference.getFileName()
						)
					);
				}
			}
			if (clientInfo.getLogoWeb_ID() > 0) {
				MADAttachmentReference attachmentReference = MADAttachmentReference.getByImageId(
					Env.getCtx(),
					clientInfo.getFileHandler_ID(),
					clientInfo.getLogoWeb_ID(),
					null
				);
				if (attachmentReference != null && attachmentReference.getAD_AttachmentReference_ID() > 0) {
					builder.setLogoWeb(
						ValueManager.validateNull(
							attachmentReference.getFileName()
						)
					);
				}
			}
		}

		return builder;
	}


	/**
	 * Convert role from model class
	 * @param role
	 * @param withAccess
	 * @return
	 */
	private Role.Builder convertRole(MRole role) {
		Role.Builder builder = Role.newBuilder();
		//	Validate
		if(role == null) {
			return builder;
		}
		Client.Builder clientBuilder = convertClient(role.getAD_Client_ID());
		builder = Role.newBuilder()
			.setId(
				role.getAD_Role_ID()
			)
			.setUuid(
				ValueManager.validateNull(
					role.getUUID()
				)
			)
			.setName(
				ValueManager.validateNull(
					role.getName()
				)
			)
			.setDescription(
				ValueManager.validateNull(
					role.getDescription()
				)
			)
			.setClient(
				clientBuilder
			)
			.setIsCanExport(role.isCanExport())
			.setIsCanReport(role.isCanReport())
			.setIsPersonalAccess(role.isPersonalAccess())
			.setIsPersonalLock(role.isPersonalLock())
			.setIsAllowHtmlView(role.isAllow_HTML_View())
			.setIsAllowInfoAccount(role.isAllow_Info_Account())
			.setIsAllowInfoAsset(role.isAllow_Info_Asset())
			.setIsAllowInfoBusinessPartner(role.isAllow_Info_BPartner())
			.setIsAllowInfoCashJournal(role.isAllow_Info_CashJournal())
			.setIsAllowInfoCrp(role.isAllow_Info_CRP())
			.setIsAllowInfoInOut(role.isAllow_Info_InOut())
			.setIsAllowInfoInvoice(role.isAllow_Info_Invoice())
			.setIsAllowInfoMrp(role.isAllow_Info_MRP())
			.setIsAllowInfoOrder(role.isAllow_Info_Order())
			.setIsAllowInfoPayment(role.isAllow_Info_Payment())
			.setIsAllowInfoProduct(role.isAllow_Info_Product())
			.setIsAllowInfoResource(role.isAllow_Info_Resource())
			.setIsAllowInfoSchedule(role.isAllow_Info_Schedule())
			.setIsAllowXlsView(role.isAllow_XLS_View())
			.setIsShowAccounting(
				role.isShowAcct()
			)
		;

		//	return
		return builder;
	}



	@Override
	public void getMenu(MenuRequest request, StreamObserver<MenuResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Menu Request Null");
			}
			MenuResponse.Builder menuBuilder = convertMenu();
			responseObserver.onNext(menuBuilder.build());
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
	 * Convert Menu
	 * @return
	 */
	private MenuResponse.Builder convertMenu() {
		int roleId = Env.getAD_Role_ID(Env.getCtx());
		int userId = Env.getAD_User_ID(Env.getCtx());
		String menuKey = roleId + "|" + userId + "|" + Env.getAD_Language(Env.getCtx());
		MenuResponse.Builder builderList = menuCache.get(menuKey);
		if(builderList != null) {
			return builderList;
		}

		MMenu menu = new MMenu(Env.getCtx(), 0, null);
		menu.setName(Msg.getMsg(Env.getCtx(), "Menu"));
		//	Get Reference
		int treeId = DB.getSQLValue(null,
			"SELECT COALESCE(r.AD_Tree_Menu_ID, ci.AD_Tree_Menu_ID)" 
			+ "FROM AD_ClientInfo ci" 
			+ " INNER JOIN AD_Role r ON (ci.AD_Client_ID=r.AD_Client_ID) "
			+ "WHERE AD_Role_ID=?", roleId);
		if (treeId <= 0) {
			treeId = MTree.getDefaultTreeIdFromTableId(menu.getAD_Client_ID(), I_AD_Menu.Table_ID);
		}

		if(treeId > 0) {
			builderList = MenuResponse.newBuilder();
			MTree tree = new MTree(Env.getCtx(), treeId, false, false, null, null);
			//	
			// Menu.Builder builder = convertMenu(Env.getCtx(), menu, 0, Env.getAD_Language(Env.getCtx()));
			//	Get main node
			MTreeNode rootNode = tree.getRoot();
			Enumeration<?> childrens = rootNode.children();
			while (childrens.hasMoreElements()) {
				MTreeNode child = (MTreeNode)childrens.nextElement();
				Menu.Builder childBuilder = convertMenu(
					Env.getCtx(),
					MMenu.getFromId(Env.getCtx(), child.getNode_ID()),
					child.getParent_ID(),
					Env.getAD_Language(Env.getCtx())
				);
				//	Explode child
				addChildren(Env.getCtx(), childBuilder, child, Env.getAD_Language(Env.getCtx()));
				// builder.addChildren(childBuilder.build());
				builderList.addMenus(childBuilder);
			}
		}

		//	Set from DB
		menuCache.put(menuKey, builderList);
		return builderList;
	}

	/**
	 * Convert Menu to builder
	 * @param context
	 * @param menu
	 * @param parentId
	 * @param language
	 * @param withChild
	 * @return
	 */
	private Menu.Builder convertMenu(Properties context, MMenu menu, int parentId, String language) {
		String name = null;
		String description = null;
		if(!Util.isEmpty(language)) {
			name = menu.get_Translation(I_AD_Menu.COLUMNNAME_Name, language);
			description = menu.get_Translation(I_AD_Menu.COLUMNNAME_Description, language);
		}
		//	Validate for default
		if(Util.isEmpty(name, true)) {
			name = menu.getName();
		}
		if(Util.isEmpty(description, true)) {
			description = menu.getDescription();
		}
		Menu.Builder builder = Menu.newBuilder()
			.setId(menu.getAD_Menu_ID())
			.setUuid(
				ValueManager.validateNull(
					menu.getUUID()
				)
			)
			.setParentId(parentId)
			// .setSequence(
			// 	menu.getSeqNo()
			// )
			.setName(
				ValueManager.validateNull(name)
			)
			.setDescription(
				ValueManager.validateNull(description))
			.setAction(
				ValueManager.validateNull(
					menu.getAction()
				)
			)
			.setIsSalesTransaction(
				menu.isSOTrx()
			)
			.setIsSummary(menu.isSummary())
			.setIsReadOnly(menu.isReadOnly())
		;
		//	Supported actions
		if(!Util.isEmpty(menu.getAction(), true)) {
			DictionaryEntity.Builder actionReference = DictionaryEntity.newBuilder();
			if(menu.getAction().equals(MMenu.ACTION_Form) && menu.getAD_Form_ID() > 0) {
				MForm form = new MForm(context, menu.getAD_Form_ID(), null);
				actionReference.setId(
						form.getAD_Form_ID()
					)
					.setUuid(
						ValueManager.validateNull(
							form.getUUID()
						)
					)
					.setName(
						ValueManager.validateNull(
							form.get_Translation(I_AD_Form.COLUMNNAME_Name)
						)
					)
					.setDescription(
						ValueManager.validateNull(
							form.get_Translation(I_AD_Form.COLUMNNAME_Description)
						)
					)
					.setHelp(
						ValueManager.validateNull(
							form.get_Translation(I_AD_Form.COLUMNNAME_Help)
						)
					)
				;
				builder.setActionId(form.getAD_Form_ID())
					.setActionUuid(
						ValueManager.validateNull(
							form.getUUID()
						)
					)
					.setForm(actionReference)
				;
			} else if (menu.getAction().equals(MMenu.ACTION_Window) && menu.getAD_Window_ID() > 0) {
				MWindow window = new MWindow(context, menu.getAD_Window_ID(), null);
				actionReference.setId(
						window.getAD_Window_ID()
					)
					.setUuid(
						ValueManager.validateNull(
							window.getUUID()
						)
					)
					.setName(
						ValueManager.validateNull(
							window.get_Translation(I_AD_Window.COLUMNNAME_Name)
						)
					)
					.setDescription(
						ValueManager.validateNull(
							window.get_Translation(I_AD_Window.COLUMNNAME_Description)
						)
					)
					.setHelp(
						ValueManager.validateNull(
							window.get_Translation(I_AD_Window.COLUMNNAME_Help)
						)
					)
				;
				builder.setActionId(window.getAD_Window_ID())
					.setActionUuid(
						ValueManager.validateNull(
							window.getUUID()
						)
					)
					.setWindow(actionReference)
				;
				
			} else if ((menu.getAction().equals(MMenu.ACTION_Process) || menu.getAction().equals(MMenu.ACTION_Report))
					&& menu.getAD_Process_ID() > 0) {
				MProcess process = MProcess.get(context, menu.getAD_Process_ID());
				actionReference.setId(
						process.getAD_Process_ID()
					)
					.setUuid(
						ValueManager.validateNull(
							process.getUUID()
						)
					)
					.setName(
						ValueManager.validateNull(
							process.get_Translation(I_AD_Process.COLUMNNAME_Name)
						)
					)
					.setDescription(
						ValueManager.validateNull(
							process.get_Translation(I_AD_Process.COLUMNNAME_Description)
						)
					)
					.setHelp(
						ValueManager.validateNull(
							process.get_Translation(I_AD_Process.COLUMNNAME_Help)
						)
					)
				;
				builder.setActionId(process.getAD_Process_ID())
					.setActionUuid(
						ValueManager.validateNull(
							process.getUUID()
						)
					)
					.setProcess(actionReference)
				;
			} else if (menu.getAction().equals(MMenu.ACTION_SmartBrowse) && menu.getAD_Browse_ID() > 0) {
				MBrowse smartBrowser = MBrowse.get(context, menu.getAD_Browse_ID());
				actionReference.setId(
						smartBrowser.getAD_Browse_ID()
					)
					.setUuid(
						ValueManager.validateNull(
							smartBrowser.getUUID()
						)
					)
					.setName(
						ValueManager.validateNull(
							smartBrowser.get_Translation(I_AD_Browse.COLUMNNAME_Name)
						)
					)
					.setDescription(
						ValueManager.validateNull(
							smartBrowser.get_Translation(I_AD_Browse.COLUMNNAME_Description)
						)
					)
					.setHelp(
						ValueManager.validateNull(
							smartBrowser.get_Translation(I_AD_Browse.COLUMNNAME_Help)
						)
					)
				;
				builder.setActionId(smartBrowser.getAD_Browse_ID())
					.setActionUuid(
						ValueManager.validateNull(
							smartBrowser.getUUID()
						)
					)
					.setBrowser(actionReference)
				;
			} else if (menu.getAction().equals(MMenu.ACTION_WorkFlow) && menu.getAD_Workflow_ID() > 0) {
				MWorkflow workflow = MWorkflow.get(context, menu.getAD_Workflow_ID());
				actionReference.setId(
						workflow.getAD_Workflow_ID()
					)
					.setUuid(
						ValueManager.validateNull(
							workflow.getUUID()
						)
					)
					.setName(
						ValueManager.validateNull(
							workflow.get_Translation(I_AD_Workflow.COLUMNNAME_Name)
						)
					)
					.setDescription(
						ValueManager.validateNull(
							workflow.get_Translation(I_AD_Workflow.COLUMNNAME_Description)
						)
					)
					.setHelp(
						ValueManager.validateNull(
							workflow.get_Translation(I_AD_Workflow.COLUMNNAME_Help)
						)
					)
				;
				builder.setActionId(workflow.getAD_Workflow_ID())
					.setActionUuid(
						ValueManager.validateNull(
							workflow.getUUID()
						)
					)
					.setWorkflow(actionReference)
				;
			}
		}
		return builder;
	}

	/**
	 * Add children to menu
	 * @param context
	 * @param builder
	 * @param node
	 * @param language
	 */
	private void addChildren(Properties context, Menu.Builder builder, MTreeNode node, String language) {
		Enumeration<?> childrens = node.children();
		while (childrens.hasMoreElements()) {
			MTreeNode child = (MTreeNode) childrens.nextElement();
			MMenu menuNone = MMenu.getFromId(context, child.getNode_ID());
			Menu.Builder childBuilder = convertMenu(
				context,
				menuNone,
				child.getParent_ID(),
				language
			);
			childBuilder.setSequence(
				child.getSeqNo()
			);
			addChildren(context, childBuilder, child, language);
			builder.addChildren(
				childBuilder.build()
			);
		}
	}



	@Override
	public void getDictionaryAccess(GetDictionaryAccessRequest request, StreamObserver<GetDictionaryAccessResponse> responseObserver) {
		try {
			log.fine("Get Dictionary Access");
			GetDictionaryAccessResponse.Builder builder = getDictionaryAccess(request);
			responseObserver.onNext(builder.build());
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

	GetDictionaryAccessResponse.Builder getDictionaryAccess(GetDictionaryAccessRequest request) {
		GetDictionaryAccessResponse.Builder builder = GetDictionaryAccessResponse.newBuilder();
		if (request.getDictionaryType() == DictionaryType.UNKNOW) {
			throw new AdempiereException("DictionaryType @Mandatory@");
		}
		int dictionaryId = request.getId();
		if (dictionaryId <= 0) {
			throw new AdempiereException("@Record_ID@ @Mandatory@");
		}

		MRole role = MRole.getDefault();

		boolean isWithAccess = false;
		String message = "";
		if (request.getDictionaryTypeValue() == DictionaryType.MENU_VALUE) {
			isWithAccess = true;
		} else if (request.getDictionaryTypeValue() == DictionaryType.WINDOW_VALUE) {
			isWithAccess = true;
			Boolean isRoleAccess = role.getWindowAccess(dictionaryId);
			if (isRoleAccess == null || !isRoleAccess.booleanValue()) {
				message += "@AD_Window_ID@ without role access.";
				isWithAccess = false;
			}
			boolean isRecordAccess = role.isRecordAccess(
				I_AD_Window.Table_ID,
				dictionaryId,
				MRole.SQL_RO
			);
			if (!isRecordAccess) {
				if (!Util.isEmpty(message, true)) {
					message += " | ";
				}
				message += "@AD_Window_ID@ without record access.";
				isWithAccess = false;
			}
		} else if (request.getDictionaryTypeValue() == DictionaryType.PROCESS_VALUE) {
			isWithAccess = true;
			Boolean isRoleAccess = role.getProcessAccess(dictionaryId);
			if (isRoleAccess == null || !isRoleAccess.booleanValue()) {
				message += "@AD_Process_ID@ without role access.";
				isWithAccess = false;
			}
			boolean isRecordAccess = role.isRecordAccess(
				I_AD_Process.Table_ID,
				dictionaryId,
				MRole.SQL_RO
			);
			if (!isRecordAccess) {
				if (!Util.isEmpty(message, true)) {
					message += " | ";
				}
				message += "@AD_Process_ID@ without record access.";
				isWithAccess = false;
			}
		} else if (request.getDictionaryTypeValue() == DictionaryType.BROWSER_VALUE) {
			isWithAccess = true;
			Boolean isRoleAccess = role.getBrowseAccess(dictionaryId);
			if (isRoleAccess == null || !isRoleAccess.booleanValue()) {
				message += "@AD_Browse_ID@ without role access.";
				isWithAccess = false;
			}
			boolean isRecordAccess = role.isRecordAccess(
				I_AD_Browse.Table_ID,
				dictionaryId,
				MRole.SQL_RO
			);
			if (!isRecordAccess) {
				if (!Util.isEmpty(message, true)) {
					message += " | ";
				}
				message += "@AD_Browse_ID@ without record access.";
				isWithAccess = false;
			}
		} else if (request.getDictionaryTypeValue() == DictionaryType.FORM_VALUE) {
			isWithAccess = true;
			Boolean isRoleAccess = role.getFormAccess(dictionaryId);
			if (isRoleAccess == null || !isRoleAccess.booleanValue()) {
				message += "@AD_Form_ID@ without role access.";
				isWithAccess = false;
			}
			boolean isRecordAccess = role.isRecordAccess(
				I_AD_Form.Table_ID,
				dictionaryId,
				MRole.SQL_RO
			);
			if (!isRecordAccess) {
				if (!Util.isEmpty(message, true)) {
					message += " | ";
				}
				message += "@AD_Form_ID@ without record access.";
				isWithAccess = false;
			}
		}

		builder.setIsAccess(isWithAccess)
			.setMessage(
				ValueManager.validateNull(
					message
				)
			)
		;

		return builder;
	}

}
