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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.adempiere.core.domains.models.I_AD_Menu;
import org.adempiere.core.domains.models.I_AD_Role;
import org.adempiere.core.domains.models.I_M_Warehouse;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.MBrowse;
import org.compiere.model.MClient;
import org.compiere.model.MClientInfo;
import org.compiere.model.MCountry;
import org.compiere.model.MCurrency;
import org.compiere.model.MForm;
import org.compiere.model.MMenu;
import org.compiere.model.MProcess;
import org.compiere.model.MRole;
import org.compiere.model.MSession;
import org.compiere.model.MTree;
import org.compiere.model.MTreeNode;
import org.compiere.model.MUser;
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
import org.spin.backend.grpc.security.ContextValue;
import org.spin.backend.grpc.security.ListRolesRequest;
import org.spin.backend.grpc.security.ListRolesResponse;
import org.spin.backend.grpc.security.ListServicesRequest;
import org.spin.backend.grpc.security.ListServicesResponse;
import org.spin.backend.grpc.security.LoginOpenIDRequest;
import org.spin.backend.grpc.security.LoginRequest;
import org.spin.backend.grpc.security.LogoutRequest;
import org.spin.backend.grpc.security.Menu;
import org.spin.backend.grpc.security.MenuRequest;
import org.spin.backend.grpc.security.Role;
import org.spin.backend.grpc.security.SecurityGrpc.SecurityImplBase;
import org.spin.backend.grpc.security.Service;
import org.spin.backend.grpc.security.Session;
import org.spin.backend.grpc.security.SessionInfo;
import org.spin.backend.grpc.security.SessionInfoRequest;
import org.spin.backend.grpc.security.SetSessionAttributeRequest;
import org.spin.backend.grpc.security.UserInfo;
import org.spin.backend.grpc.security.UserInfoRequest;
import org.spin.backend.grpc.security.ValueType;
import org.spin.base.db.LimitUtil;
import org.spin.base.util.ContextManager;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.SessionManager;
import org.spin.base.util.ValueUtil;
import org.spin.model.MADAttachmentReference;
import org.spin.model.MADToken;
import org.spin.util.AttachmentUtil;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
/**
 * @author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 * Security service
 */
public class SecurityServiceImplementation extends SecurityImplBase {
	
	/**
	 * Load Validators
	 */
	public SecurityServiceImplementation() {
		super();
		DB.validateSupportedUUIDFromDB();
		MCountry.getCountries(Env.getCtx());
	}
	
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(SecurityServiceImplementation.class);
	/**	Menu */
	private static CCache<String, Menu.Builder> menuCache = new CCache<String, Menu.Builder>("Menu_for_User", 30, 0);


	boolean isSessionContext(String contextKey) {
		return contextKey.startsWith("#") || contextKey.startsWith("$");
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
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
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
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void listServices(ListServicesRequest request, StreamObserver<ListServicesResponse> responseObserver) {
		try {
			log.fine("List Services");
			ListServicesResponse.Builder serviceBuilder = ListServicesResponse.newBuilder();
			Hashtable<Integer, Map<String, String>> services = OpenIDUtil.getAuthenticationServices();
			services.entrySet().forEach(service -> {
				Service.Builder availableService = Service.newBuilder();
				availableService.setId(service.getKey())
					.setDisplayName(ValueUtil.validateNull(service.getValue().get(OpenIDUtil.DISPLAYNAME)))
					.setAuthorizationUri(ValueUtil.validateNull(service.getValue().get(OpenIDUtil.ENDPOINT_Authorization_URI)))
				;
				serviceBuilder.addServices(availableService);
			});
			responseObserver.onNext(serviceBuilder.build());
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
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
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
			responseObserver.onError(Status.INTERNAL
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
			responseObserver.onError(Status.INTERNAL
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
		int warehouseId = Env.getContextAsInt(context, "#M_Warehouse_ID");
		if (!Util.isEmpty(request.getLanguage())) {
			language = ContextManager.getDefaultLanguage(request.getLanguage());
		}
		//	Warehouse
		if (!Util.isEmpty(request.getWarehouseUuid(), true)) {
			int warehouseReferenceId = RecordUtil.getIdFromUuid(I_M_Warehouse.Table_Name, request.getWarehouseUuid(), null);
			if(warehouseReferenceId >= 0) {
				warehouseId = warehouseReferenceId;
			}
		}
		MSession currentSession = MSession.get(context, false);
		// Session values
		Session.Builder builder = Session.newBuilder();
		final String bearerToken = SessionManager.createSession(currentSession.getWebSession(), language, currentSession.getAD_Role_ID(), currentSession.getCreatedBy(), currentSession.getAD_Org_ID(), warehouseId);
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
			responseObserver.onError(Status.INTERNAL
					.withDescription(e.getLocalizedMessage())
					.withCause(e)
					.asRuntimeException());
		}
	}
	
	@Override
	public void getMenu(MenuRequest request, StreamObserver<Menu> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			Menu.Builder menuBuilder = convertMenu();
			responseObserver.onNext(menuBuilder.build());
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
			responseObserver.onError(Status.INTERNAL
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
		//	Set next page
		builder.setNextPageToken(ValueUtil.validateNull(nexPageToken));
		//	Return
		return builder;
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

			// TODO: Validate values
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
		return createValidSession(isDefaultRole, request.getClientVersion(), request.getLanguage(), roleId, userId, organizationId, warehouseId);
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
	private Session.Builder createValidSession(boolean isDefaultRole, String clientVersion, String language, int roleId, int userId, int organizationId, int warehouseId) {
		Session.Builder builder = Session.newBuilder();
			if(isDefaultRole
					|| roleId <= 0) {
				roleId = SessionManager.getDefaultRoleId(userId);
			}
			//	Organization
			if(organizationId < 0) {
				organizationId = SessionManager.getDefaultOrganizationId(roleId, userId);
			}
			if(warehouseId < 0) {
				warehouseId = SessionManager.getDefaultWarehouseId(organizationId);
			}
			if(warehouseId < 0) {
				warehouseId = 0;
			}
			//	Get Values from role
			if(roleId < 0) {
				throw new AdempiereException("@AD_User_ID@ / @AD_Role_ID@ / @AD_Org_ID@ @NotFound@");
			}
			if(organizationId < 0) {
				throw new AdempiereException("@AD_User_ID@: @AD_Org_ID@ @NotFound@");
			}
			//	Session values
			final String bearerToken = SessionManager.createSession(clientVersion, language, roleId, userId, organizationId, warehouseId);
			builder.setToken(bearerToken);
			//	Return session
			return builder;
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
		return createValidSession(true, request.getClientVersion(), request.getLanguage(), -1, validUser.getAD_User_ID(), -1, -1); 
	}


	/**
	 * Convert Values from Context
	 * @param value
	 * @return
	 */
	private ContextValue.Builder convertObjectFromContext(String value) {
		ContextValue.Builder builder = ContextValue.newBuilder();
		if (Util.isEmpty(value)) {
			return builder;
		}
		if (ValueUtil.isNumeric(value)) {
			builder.setValueType(ValueType.INTEGER);
			builder.setIntValue(ValueUtil.getIntegerFromString(value));
		} else if (ValueUtil.isBoolean(value)) {
			builder.setValueType(ValueType.BOOLEAN);
			boolean booleanValue = ValueUtil.stringToBoolean(value.trim());
			builder.setBooleanValue(booleanValue);
		} else if (ValueUtil.isDate(value)) {
			builder.setValueType(ValueType.DATE);
			builder.setLongValue(ValueUtil.getDateFromString(value).getTime());
		} else {
			builder.setValueType(ValueType.STRING);
			builder.setStringValue(ValueUtil.validateNull(value));
		}
		//	
		return builder;
	}


	/**
	 * Get Object from ContextValue
	 * @param contextValue
	 * @return
	 */
	public Object getObjectFromContextValue(ContextValue contextValue) {
		ValueType valueType = contextValue.getValueType();
		if (valueType.equals(ValueType.BOOLEAN)) {
			return contextValue.getBooleanValue();
		} else if (valueType.equals(ValueType.DOUBLE)) {
			return contextValue.getDoubleValue();
		} else if (valueType.equals(ValueType.INTEGER)) {
			return contextValue.getIntValue();
		} else if (valueType.equals(ValueType.STRING)) {
			return contextValue.getStringValue();
		} else if (valueType.equals(ValueType.LONG) || valueType.equals(ValueType.DATE)) {
			return ValueUtil.getTimestampFromLong(contextValue.getLongValue());
		}
		return null;
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
			responseObserver.onError(Status.INTERNAL
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
		final String bearerToken = SessionManager.createSession(currentSession.getWebSession(), language, roleId, userId, organizationId, warehouseId);
		builder.setToken(bearerToken);
		// Logout
		logoutSession(LogoutRequest.newBuilder().build());

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
		session.setCountryCode(ValueUtil.validateNull(country.getCountryCode()));
		session.setCountryName(ValueUtil.validateNull(country.getName()));
		session.setDisplaySequence(ValueUtil.validateNull(country.getDisplaySequence()));
		session.setCurrencyIsoCode(ValueUtil.validateNull(currency.getISO_Code()));
		session.setCurrencyName(ValueUtil.validateNull(currency.getDescription()));
		session.setCurrencySymbol(ValueUtil.validateNull(currency.getCurSymbol()));
		session.setStandardPrecision(currency.getStdPrecision());
		session.setCostingPrecision(currency.getCostingPrecision());
		session.setLanguage(ValueUtil.validateNull(ContextManager.getDefaultLanguage(Env.getAD_Language(Env.getCtx()))));
		//	Set default context
		Env.getCtx().entrySet().stream()
			.filter(keyValue -> isSessionContext(String.valueOf(keyValue.getKey())))
			.forEach(contextKeyValue -> {
				session.putDefaultContext(contextKeyValue.getKey().toString(), convertObjectFromContext((String)contextKeyValue.getValue()).build());
			});
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
		builder.setId(session.getAD_Session_ID());
		builder.setUuid(ValueUtil.validateNull(session.getUUID()));
		builder.setName(ValueUtil.validateNull(session.getDescription()));
		builder.setUserInfo(convertUserInfo(MUser.get(context, session.getCreatedBy())).build());
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
		UserInfo.Builder userInfo = UserInfo.newBuilder();
		userInfo.setId(user.getAD_User_ID());
		userInfo.setUuid(ValueUtil.validateNull(user.getUUID()));
		userInfo.setName(ValueUtil.validateNull(user.getName()));
		userInfo.setDescription(ValueUtil.validateNull(user.getDescription()));
		userInfo.setComments(ValueUtil.validateNull(user.getComments()));
		if(user.getLogo_ID() > 0 && AttachmentUtil.getInstance().isValidForClient(user.getAD_Client_ID())) {
			MClientInfo clientInfo = MClientInfo.get(Env.getCtx(), user.getAD_Client_ID());
			MADAttachmentReference attachmentReference = MADAttachmentReference.getByImageId(user.getCtx(), clientInfo.getFileHandler_ID(), user.getLogo_ID(), null);
			if(attachmentReference != null
					&& attachmentReference.getAD_AttachmentReference_ID() > 0) {
				userInfo.setImage(ValueUtil.validateNull(attachmentReference.getValidFileName()));
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
		builder.setId(client.getAD_Client_ID())
			.setUuid(
				ValueUtil.validateNull(client.getUUID())
			)
			.setName(
				ValueUtil.validateNull(client.getName())
			)
			.setDescription(
				ValueUtil.validateNull(client.getDescription())
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
						ValueUtil.validateNull(
							attachmentReference.getValidFileName()
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
						ValueUtil.validateNull(
							attachmentReference.getValidFileName()
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
						ValueUtil.validateNull(
							attachmentReference.getValidFileName()
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
			.setId(role.getAD_Role_ID())
			.setUuid(ValueUtil.validateNull(role.getUUID()))
			.setName(ValueUtil.validateNull(role.getName()))
			.setDescription(ValueUtil.validateNull(role.getDescription()))
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
		;

		//	return
		return builder;
	}


	/**
	 * Convert Menu
	 * @return
	 */
	private Menu.Builder convertMenu() {
		int roleId = Env.getAD_Role_ID(Env.getCtx());
		int userId = Env.getAD_User_ID(Env.getCtx());
		String menuKey = roleId + "|" + userId + "|" + Env.getAD_Language(Env.getCtx());
		Menu.Builder builder = menuCache.get(menuKey);
		if(builder != null) {
			return builder;
		}
		builder = Menu.newBuilder();
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
		if(treeId != 0) {
			MTree tree = new MTree(Env.getCtx(), treeId, false, false, null, null);
			//	
			builder = convertMenu(Env.getCtx(), menu, 0, Env.getAD_Language(Env.getCtx()));
			//	Get main node
			MTreeNode rootNode = tree.getRoot();
			Enumeration<?> childrens = rootNode.children();
			while (childrens.hasMoreElements()) {
				MTreeNode child = (MTreeNode)childrens.nextElement();
				Menu.Builder childBuilder = convertMenu(Env.getCtx(), MMenu.getFromId(Env.getCtx(), child.getNode_ID()), child.getParent_ID(), Env.getAD_Language(Env.getCtx()));
				//	Explode child
				addChildren(Env.getCtx(), childBuilder, child, Env.getAD_Language(Env.getCtx()));
				builder.addChilds(childBuilder.build());
			}
		}
		//	Set from DB
		menuCache.put(menuKey, builder);
		return builder;
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
		if(Util.isEmpty(name)) {
			name = menu.getName();
		}
		if(Util.isEmpty(description)) {
			description = menu.getDescription();
		}
		String parentUuid = null;
		if(parentId > 0) {
			parentUuid = MMenu.getFromId(context, parentId).getUUID();
		}
		Menu.Builder builder = Menu.newBuilder()
				.setId(menu.getAD_Menu_ID())
				.setUuid(ValueUtil.validateNull(menu.getUUID()))
				.setName(ValueUtil.validateNull(name))
				.setDescription(ValueUtil.validateNull(description))
				.setAction(ValueUtil.validateNull(menu.getAction()))
				.setIsSOTrx(menu.isSOTrx())
				.setIsSummary(menu.isSummary())
				.setIsReadOnly(menu.isReadOnly())
				.setIsActive(menu.isActive())
				.setParentUuid(ValueUtil.validateNull(parentUuid));
		//	Supported actions
		if(!Util.isEmpty(menu.getAction())) {
			int referenceId = 0;
			String referenceUuid = null;
			if(menu.getAction().equals(MMenu.ACTION_Form)) {
				if(menu.getAD_Form_ID() > 0) {
					MForm form = new MForm(context, menu.getAD_Form_ID(), null);
					referenceId = menu.getAD_Form_ID();
					referenceUuid = form.getUUID();
				}
			} else if(menu.getAction().equals(MMenu.ACTION_Window)) {
				if(menu.getAD_Window_ID() > 0) {
					MWindow window = new MWindow(context, menu.getAD_Window_ID(), null);
					referenceId = menu.getAD_Window_ID();
					referenceUuid = window.getUUID();
				}
			} else if(menu.getAction().equals(MMenu.ACTION_Process)
				|| menu.getAction().equals(MMenu.ACTION_Report)) {
				if(menu.getAD_Process_ID() > 0) {
					MProcess process = MProcess.get(context, menu.getAD_Process_ID());
					referenceId = menu.getAD_Process_ID();
					referenceUuid = process.getUUID();
				}
			} else if(menu.getAction().equals(MMenu.ACTION_SmartBrowse)) {
				if(menu.getAD_Browse_ID() > 0) {
					MBrowse smartBrowser = MBrowse.get(context, menu.getAD_Browse_ID());
					referenceId = menu.getAD_Browse_ID();
					referenceUuid = smartBrowser.getUUID();
				}
			} else if(menu.getAction().equals(MMenu.ACTION_WorkFlow)) {
				if(menu.getAD_Workflow_ID() > 0) {
					MWorkflow workflow = MWorkflow.get(context, menu.getAD_Workflow_ID());
					referenceId = menu.getAD_Workflow_ID();
					referenceUuid = workflow.getUUID();
				}
			}
			builder.setReferenceId(referenceId)
				.setReferenceUuid(
					ValueUtil.validateNull(referenceUuid)
				)
			;
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
			MTreeNode child = (MTreeNode)childrens.nextElement();
			Menu.Builder childBuilder = convertMenu(context, MMenu.getFromId(context, child.getNode_ID()), child.getParent_ID(), language);
			addChildren(context, childBuilder, child, language);
			builder.addChilds(childBuilder.build());
		}
	}
}
