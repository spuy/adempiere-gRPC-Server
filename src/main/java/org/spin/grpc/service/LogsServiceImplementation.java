/************************************************************************************
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, C.A.                     *
 * Contributor(s): Yamel Senih ysenih@erpya.com                                     *
 * This program is free software: you can redistribute it and/or modify             *
 * it under the terms of the GNU General Public License as published by             *
 * the Free Software Foundation, either version 2 of the License, or                *
 * (at your option) any later version.                                              *
 * This program is distributed in the hope that it will be useful,                  *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                   *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the                     *
 * GNU General Public License for more details.                                     *
 * You should have received a copy of the GNU General Public License                *
 * along with this program.	If not, see <https://www.gnu.org/licenses/>.            *
 ************************************************************************************/
package org.spin.grpc.service;

import java.util.ArrayList;
import java.util.List;

import org.adempiere.core.domains.models.I_AD_ChangeLog;
import org.adempiere.core.domains.models.I_AD_PInstance;
import org.adempiere.core.domains.models.I_AD_WF_Process;
import org.adempiere.core.domains.models.I_CM_Chat;
import org.adempiere.core.domains.models.I_CM_ChatEntry;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.MBrowse;
import org.compiere.model.MChangeLog;
import org.compiere.model.MChat;
import org.compiere.model.MChatEntry;
import org.compiere.model.MChatType;
import org.compiere.model.MForm;
import org.compiere.model.MMenu;
import org.compiere.model.MPInstance;
import org.compiere.model.MProcess;
import org.compiere.model.MRecentItem;
import org.compiere.model.MTab;
import org.compiere.model.MTable;
import org.compiere.model.MUser;
import org.compiere.model.MWindow;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.compiere.wf.MWFProcess;
import org.spin.backend.grpc.common.ChatEntry;
import org.spin.backend.grpc.common.ProcessLog;
import org.spin.backend.grpc.logs.EntityChat;
import org.spin.backend.grpc.logs.EntityChat.ConfidentialType;
import org.spin.backend.grpc.logs.EntityChat.ModerationType;
import org.spin.backend.grpc.logs.ExistsChatEntriesRequest;
import org.spin.backend.grpc.logs.ExistsChatEntriesResponse;
import org.spin.backend.grpc.logs.ListChatEntriesRequest;
import org.spin.backend.grpc.logs.ListChatEntriesResponse;
import org.spin.backend.grpc.logs.ListEntityChatsRequest;
import org.spin.backend.grpc.logs.ListEntityChatsResponse;
import org.spin.backend.grpc.logs.ListEntityLogsRequest;
import org.spin.backend.grpc.logs.ListEntityLogsResponse;
import org.spin.backend.grpc.logs.ListProcessLogsRequest;
import org.spin.backend.grpc.logs.ListProcessLogsResponse;
import org.spin.backend.grpc.logs.ListRecentItemsRequest;
import org.spin.backend.grpc.logs.ListRecentItemsResponse;
import org.spin.backend.grpc.logs.ListUserActivitesRequest;
import org.spin.backend.grpc.logs.ListUserActivitesResponse;
import org.spin.backend.grpc.logs.ListWorkflowLogsRequest;
import org.spin.backend.grpc.logs.ListWorkflowLogsResponse;
import org.spin.backend.grpc.logs.LogsGrpc.LogsImplBase;
import org.spin.backend.grpc.logs.RecentItem;
import org.spin.backend.grpc.wf.WorkflowProcess;
import org.spin.base.db.LimitUtil;
import org.spin.base.util.ConvertUtil;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.SessionManager;
import org.spin.base.util.ValueUtil;
import org.spin.base.util.WorkflowUtil;
import org.spin.log.LogsConvertUtil;
import org.spin.log.LogsServiceLogic;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Edwin Betancourt, EdwinBetanc0urt@outlook.com, https://github.com/EdwinBetanc0urt
 * Service for backend of Logs
 */
public class LogsServiceImplementation extends LogsImplBase {
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(LogsServiceImplementation.class);
	
	@Override
	public void listProcessLogs(ListProcessLogsRequest request, StreamObserver<ListProcessLogsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Process Activity Requested is Null");
			}
			log.fine("Object List Requested = " + request);
			ListProcessLogsResponse.Builder entityValueList = listProcessLogs(request);
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
	
	@Override
	public void listRecentItems(ListRecentItemsRequest request, StreamObserver<ListRecentItemsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Process Activity Requested is Null");
			}
			log.fine("Recent Items Requested = " + request);
			ListRecentItemsResponse.Builder entityValueList = convertRecentItems(request);
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
	
	@Override
	public void listEntityLogs(ListEntityLogsRequest request, StreamObserver<ListEntityLogsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Record Logs Requested is Null");
			}
			ListEntityLogsResponse.Builder entityValueList = listEntityLogs(request);
			responseObserver.onNext(entityValueList.build());
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
	public void listWorkflowLogs(ListWorkflowLogsRequest request,
			StreamObserver<ListWorkflowLogsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Workflow Logs Requested is Null");
			}
			log.fine("Object List Requested = " + request);
			ListWorkflowLogsResponse.Builder entityValueList = convertWorkflowLogs(request);
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
	
	@Override
	public void listEntityChats(ListEntityChatsRequest request,
			StreamObserver<ListEntityChatsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Record Chats Requested is Null");
			}
			ListEntityChatsResponse.Builder entityValueList = convertEntityChats(request);
			responseObserver.onNext(entityValueList.build());
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
	public void listChatEntries(ListChatEntriesRequest request,
			StreamObserver<ListChatEntriesResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Chat Entries Requested is Null");
			}
			log.fine("Object List Requested = " + request);
			ListChatEntriesResponse.Builder entityValueList = convertChatEntries(request);
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
	 * Convert request for process log to builder
	 * @param request
	 * @return
	 */
	private ListProcessLogsResponse.Builder listProcessLogs(ListProcessLogsRequest request) {
		String sql = null;
		List<Object> parameters = new ArrayList<>();
		if(!Util.isEmpty(request.getTableName())) {
			MTable table = MTable.get(Env.getCtx(), request.getTableName());
			if(table == null
					|| table.getAD_Table_ID() == 0) {
				throw new AdempiereException("@AD_Table_ID@ @Invalid@");
			}
			int id = request.getId();
			if(id <= 0) {
				id = RecordUtil.getIdFromUuid(table.getTableName(), request.getUuid(), null);
			}
			parameters.add(id);
			parameters.add(table.getAD_Table_ID());
			sql = "Record_ID = ? AND EXISTS(SELECT 1 FROM AD_Process WHERE AD_Table_ID = ? AND AD_Process_ID = AD_PInstance.AD_Process_ID)";
		} if(!Util.isEmpty(request.getUserUuid())) {
			parameters.add(request.getUserUuid());
			sql = "EXISTS(SELECT 1 FROM AD_User WHERE UUID = ? AND AD_User_ID = AD_PInstance.AD_User_ID)";
		} else if(!Util.isEmpty(request.getInstanceUuid())) {
			parameters.add(request.getInstanceUuid());
			sql = "UUID = ?";
		} else {
			parameters.add(SessionManager.getSessionUuid());
			sql = "EXISTS(SELECT 1 FROM AD_Session WHERE UUID = ? AND AD_Session_ID = AD_PInstance.AD_Session_ID)";
		}
		List<MPInstance> processInstanceList = new Query(Env.getCtx(), I_AD_PInstance.Table_Name, 
				sql, null)
				.setParameters(parameters)
				.setOrderBy(I_AD_PInstance.COLUMNNAME_Created + " DESC")
				.<MPInstance>list();
		//	
		ListProcessLogsResponse.Builder builder = ListProcessLogsResponse.newBuilder();
		//	Convert Process Instance
		for(MPInstance processInstance : processInstanceList) {
			ProcessLog.Builder valueObject = LogsConvertUtil.convertProcessLog(processInstance);
			builder.addProcessLogs(valueObject.build());
		}
		//	Return
		return builder;
	}
	
	/**
	 * Convert request for workflow log to builder
	 * @param request
	 * @return
	 */
	private ListWorkflowLogsResponse.Builder convertWorkflowLogs(ListWorkflowLogsRequest request) {
		StringBuffer whereClause = new StringBuffer();
		List<Object> parameters = new ArrayList<>();
		if(Util.isEmpty(request.getTableName())) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}
		//	
		MTable table = MTable.get(Env.getCtx(), request.getTableName());
		if(table == null
				|| table.getAD_Table_ID() == 0) {
			throw new AdempiereException("@AD_Table_ID@ @Invalid@");
		}
		whereClause
			.append(I_AD_WF_Process.COLUMNNAME_AD_Table_ID).append(" = ?")
			.append(" AND ")
			.append(I_AD_WF_Process.COLUMNNAME_Record_ID).append(" = ?");
		//	Set parameters
		int id = request.getId();
		if(id <= 0) {
			id = RecordUtil.getIdFromUuid(table.getTableName(), request.getUuid(), null);
		}
		parameters.add(table.getAD_Table_ID());
		parameters.add(id);
		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		Query query = new Query(Env.getCtx(), I_AD_WF_Process.Table_Name, whereClause.toString(), null)
				.setParameters(parameters);
		int count = query.count();
		List<MWFProcess> workflowProcessLogList = query
				.setLimit(limit, offset)
				.setOrderBy(I_AD_WF_Process.COLUMNNAME_Updated + " DESC")
				.<MWFProcess>list();
		//	
		ListWorkflowLogsResponse.Builder builder = ListWorkflowLogsResponse.newBuilder();
		//	Convert Record Log
		for(MWFProcess workflowProcessLog : workflowProcessLogList) {
			WorkflowProcess.Builder valueObject = WorkflowUtil.convertWorkflowProcess(workflowProcessLog);
			builder.addWorkflowLogs(valueObject.build());
		}
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
	 * Convert request for record log to builder
	 * @param request
	 * @return
	 */
	private ListEntityLogsResponse.Builder listEntityLogs(ListEntityLogsRequest request) {
		StringBuffer whereClause = new StringBuffer();
		List<Object> parameters = new ArrayList<>();
		if(!Util.isEmpty(request.getTableName())) {
			MTable table = MTable.get(Env.getCtx(), request.getTableName());
			if(table == null
					|| table.getAD_Table_ID() == 0) {
				throw new AdempiereException("@AD_Table_ID@ @Invalid@");
			}
			whereClause
				.append(I_AD_ChangeLog.COLUMNNAME_AD_Table_ID).append(" = ?")
				.append(" AND ")
				.append(I_AD_ChangeLog.COLUMNNAME_Record_ID).append(" = ?");
			//	Set parameters
			int id = request.getId();
			if(id <= 0) {
				id = RecordUtil.getIdFromUuid(table.getTableName(), request.getUuid(), null);
			}
			parameters.add(table.getAD_Table_ID());
			parameters.add(id);
		} else {
			whereClause.append("EXISTS(SELECT 1 FROM AD_Session WHERE UUID = ? AND AD_Session_ID = AD_ChangeLog.AD_Session_ID)");
			parameters.add(SessionManager.getSessionUuid());
		}
		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;
		Query query = new Query(Env.getCtx(), I_AD_ChangeLog.Table_Name, whereClause.toString(), null)
				.setParameters(parameters);
		int count = query.count();
		List<MChangeLog> recordLogList = query
				.setLimit(limit, offset)
				.<MChangeLog>list();
		//	Convert Record Log
		ListEntityLogsResponse.Builder builder = LogsConvertUtil.convertRecordLog(recordLogList);
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
	 * Convert Recent Items
	 * @param request
	 * @return
	 */
	private ListRecentItemsResponse.Builder convertRecentItems(ListRecentItemsRequest request) {
		ListRecentItemsResponse.Builder builder = ListRecentItemsResponse.newBuilder();
		List<MRecentItem> recentItemsList = MRecentItem.getFromUserAndRole(Env.getCtx());
		if(recentItemsList != null) {
			for(MRecentItem recentItem : recentItemsList) {
				try {
					RecentItem.Builder recentItemBuilder = RecentItem.newBuilder()
							.setDisplayName(ValueUtil.validateNull(recentItem.getLabel()));
					String menuName = "";
					String menuDescription = "";
					String referenceUuid = null;
					if(recentItem.getAD_Tab_ID() > 0) {
						MTab tab = MTab.get(Env.getCtx(), recentItem.getAD_Tab_ID());
						recentItemBuilder.setTabUuid(ValueUtil.validateNull(tab.getUUID()));
						menuName = tab.getName();
						menuDescription = tab.getDescription();
						if(!Env.isBaseLanguage(Env.getCtx(), "")) {
							menuName = tab.get_Translation("Name");
							menuDescription = tab.get_Translation("Description");
						}
						//	Add Action
						recentItemBuilder.setAction(ValueUtil.validateNull(MMenu.ACTION_Window));
					}
					if(recentItem.getAD_Window_ID() > 0) {
						MWindow window = MWindow.get(Env.getCtx(), recentItem.getAD_Window_ID());
						recentItemBuilder.setWindowUuid(ValueUtil.validateNull(window.getUUID()));
						menuName = window.getName();
						menuDescription = window.getDescription();
						referenceUuid = window.getUUID();
						if(!Env.isBaseLanguage(Env.getCtx(), "")) {
							menuName = window.get_Translation("Name");
							menuDescription = window.get_Translation("Description");
						}
						//	Add Action
						recentItemBuilder.setAction(ValueUtil.validateNull(MMenu.ACTION_Window));
					}
					if(recentItem.getAD_Menu_ID() > 0) {
						MMenu menu = MMenu.getFromId(Env.getCtx(), recentItem.getAD_Menu_ID());
						recentItemBuilder.setMenuUuid(ValueUtil.validateNull(menu.getUUID()));
						menuName = menu.getName();
						menuDescription = menu.getDescription();
						if(!Env.isBaseLanguage(Env.getCtx(), "")) {
							menuName = menu.get_Translation("Name");
							menuDescription = menu.get_Translation("Description");
						}
						//	Add Action
						recentItemBuilder.setAction(ValueUtil.validateNull(menu.getAction()));
						//	Supported actions
						if(!Util.isEmpty(menu.getAction())) {
							if(menu.getAction().equals(MMenu.ACTION_Form)) {
								if(menu.getAD_Form_ID() > 0) {
									MForm form = new MForm(Env.getCtx(), menu.getAD_Form_ID(), null);
									referenceUuid = form.getUUID();
								}
							} else if(menu.getAction().equals(MMenu.ACTION_Window)) {
								if(menu.getAD_Window_ID() > 0) {
									MWindow window = new MWindow(Env.getCtx(), menu.getAD_Window_ID(), null);
									referenceUuid = window.getUUID();
								}
							} else if(menu.getAction().equals(MMenu.ACTION_Process)
								|| menu.getAction().equals(MMenu.ACTION_Report)) {
								if(menu.getAD_Process_ID() > 0) {
									MProcess process = MProcess.get(Env.getCtx(), menu.getAD_Process_ID());
									referenceUuid = process.getUUID();
								}
							} else if(menu.getAction().equals(MMenu.ACTION_SmartBrowse)) {
								if(menu.getAD_Browse_ID() > 0) {
									MBrowse smartBrowser = MBrowse.get(Env.getCtx(), menu.getAD_Browse_ID());
									referenceUuid = smartBrowser.getUUID();
								}
							}
						}
					}
					//	Add time
					recentItemBuilder.setMenuName(ValueUtil.validateNull(menuName));
					recentItemBuilder.setMenuDescription(ValueUtil.validateNull(menuDescription));
					recentItemBuilder.setUpdated(recentItem.getUpdated().getTime());
					recentItemBuilder.setReferenceUuid(ValueUtil.validateNull(referenceUuid));
					//	For uuid
					if(recentItem.getAD_Table_ID() != 0
							&& recentItem.getRecord_ID() != 0) {
						MTable table = MTable.get(Env.getCtx(), recentItem.getAD_Table_ID());
						if(table != null
								&& table.getAD_Table_ID() != 0) {
							recentItemBuilder.setId(recentItem.getRecord_ID())
								.setUuid(ValueUtil.validateNull(RecordUtil.getUuidFromId(table.getTableName(), recentItem.getRecord_ID())))
								.setTableName(ValueUtil.validateNull(table.getTableName()))
								.setTableId(recentItem.getAD_Table_ID());
						}
					}
					//	
					builder.addRecentItems(recentItemBuilder.build());	
				} catch (Exception e) {
					log.severe(e.getLocalizedMessage());
				}
			}
		}
		//	Return
		return builder;
	}


	/**
	 * Convert request for record chats to builder
	 * @param request
	 * @return
	 */
	private ListChatEntriesResponse.Builder convertChatEntries(ListChatEntriesRequest request) {
		if(request.getId() <= 0
				&& Util.isEmpty(request.getUuid())) {
			throw new AdempiereException("@CM_Chat_ID@ @NotFound@");
		}
		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		int id = request.getId();
		if(id <= 0) {
			id = RecordUtil.getIdFromUuid(I_CM_Chat.Table_Name, request.getUuid(), null);
		}
		Query query = new Query(Env.getCtx(), I_CM_ChatEntry.Table_Name, I_CM_ChatEntry.COLUMNNAME_CM_Chat_ID + " = ?", null)
				.setParameters(id);
		int count = query.count();
		List<MChatEntry> chatEntryList = query
				.setLimit(limit, offset)
				.<MChatEntry>list();
		//	
		ListChatEntriesResponse.Builder builder = ListChatEntriesResponse.newBuilder();
		//	Convert Record Log
		for(MChatEntry chatEntry : chatEntryList) {
			ChatEntry.Builder valueObject = ConvertUtil.convertChatEntry(chatEntry);
			builder.addChatEntries(valueObject.build());
		}
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
	 * Convert request for record chats to builder
	 * @param request
	 * @return
	 */
	private ListEntityChatsResponse.Builder convertEntityChats(ListEntityChatsRequest request) {
		StringBuffer whereClause = new StringBuffer();
		List<Object> parameters = new ArrayList<>();
		if(Util.isEmpty(request.getTableName())) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}
		//	
		MTable table = MTable.get(Env.getCtx(), request.getTableName());
		if(table == null
				|| table.getAD_Table_ID() == 0) {
			throw new AdempiereException("@AD_Table_ID@ @Invalid@");
		}
		whereClause
			.append(I_CM_Chat.COLUMNNAME_AD_Table_ID).append(" = ?")
			.append(" AND ")
			.append(I_CM_Chat.COLUMNNAME_Record_ID).append(" = ?");
		//	Set parameters
		int id = request.getId();
		if(id <= 0) {
			id = RecordUtil.getIdFromUuid(table.getTableName(), request.getUuid(), null);
		}
		parameters.add(table.getAD_Table_ID());
		parameters.add(id);
		//	Get page and count
		String nexPageToken = null;
		int pageNumber = LimitUtil.getPageNumber(SessionManager.getSessionUuid(), request.getPageToken());
		int limit = LimitUtil.getPageSize(request.getPageSize());
		int offset = (pageNumber - 1) * limit;

		Query query = new Query(Env.getCtx(), I_CM_Chat.Table_Name, whereClause.toString(), null)
				.setParameters(parameters);
		int count = query.count();
		List<MChat> chatList = query
				.setLimit(limit, offset)
				.<MChat>list();
		//	
		ListEntityChatsResponse.Builder builder = ListEntityChatsResponse.newBuilder();
		//	Convert Record Log
		for(MChat chat : chatList) {
			EntityChat.Builder valueObject = convertRecordChat(chat);
			builder.addEntityChats(valueObject.build());
		}
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
	 * Convert PO class from Record Chat process to builder
	 * @param recordChat
	 * @return
	 */
	private EntityChat.Builder convertRecordChat(MChat recordChat) {
		MTable table = MTable.get(recordChat.getCtx(), recordChat.getAD_Table_ID());
		EntityChat.Builder builder = EntityChat.newBuilder();
		builder.setChatUuid(ValueUtil.validateNull(recordChat.getUUID()));
		builder.setTableName(ValueUtil.validateNull(table.getTableName()));
		if(recordChat.getCM_ChatType_ID() != 0) {
			MChatType chatType = MChatType.get(recordChat.getCtx(), recordChat.getCM_Chat_ID());
			builder.setChatTypeUuid(ValueUtil.validateNull(chatType.getUUID()));
		}
		builder.setId(recordChat.getRecord_ID());
		builder.setUuid(ValueUtil.validateNull(RecordUtil.getUuidFromId(table.getTableName(), recordChat.getRecord_ID())));
		builder.setDescription(ValueUtil.validateNull(recordChat.getDescription()));
		builder.setLogDate(recordChat.getCreated().getTime());

		if (recordChat.getCreatedBy() > 0) {
			MUser user = MUser.get(recordChat.getCtx(), recordChat.getCreatedBy());
			builder.setUserId(recordChat.getCreatedBy());
			builder.setUserUuid(ValueUtil.validateNull(user.getUUID()));
			builder.setUserName(ValueUtil.validateNull(user.getName()));
		}

		//	Confidential Type
		if(!Util.isEmpty(recordChat.getConfidentialType())) {
			if(recordChat.getConfidentialType().equals(MChat.CONFIDENTIALTYPE_PublicInformation)) {
				builder.setConfidentialType(ConfidentialType.PUBLIC);
			} else if(recordChat.getConfidentialType().equals(MChat.CONFIDENTIALTYPE_PartnerConfidential)) {
				builder.setConfidentialType(ConfidentialType.PARTER);
			} else if(recordChat.getConfidentialType().equals(MChat.CONFIDENTIALTYPE_Internal)) {
				builder.setConfidentialType(ConfidentialType.INTERNAL);
			}
		}
		//	Moderation Type
		if(!Util.isEmpty(recordChat.getModerationType())) {
			if(recordChat.getModerationType().equals(MChat.MODERATIONTYPE_NotModerated)) {
				builder.setModerationType(ModerationType.NOT_MODERATED);
			} else if(recordChat.getModerationType().equals(MChat.MODERATIONTYPE_BeforePublishing)) {
				builder.setModerationType(ModerationType.BEFORE_PUBLISHING);
			} else if(recordChat.getModerationType().equals(MChat.MODERATIONTYPE_AfterPublishing)) {
				builder.setModerationType(ModerationType.AFTER_PUBLISHING);
			}
		}
		return builder;
	}


	@Override
	public void existsChatEntries(ExistsChatEntriesRequest request, StreamObserver<ExistsChatEntriesResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Exists Chat Entries Requested is Null");
			}
			ExistsChatEntriesResponse.Builder builder = existsChatEntries(request);
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
	
	private ExistsChatEntriesResponse.Builder existsChatEntries(ExistsChatEntriesRequest request) {
		if (Util.isEmpty(request.getTableName(), true)) {
			throw new AdempiereException("@FillMandatory@ @AD_Table_ID@");
		}

		MTable table = MTable.get(Env.getCtx(), request.getTableName());
		if (table == null || table.getAD_Table_ID() <= 0) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}

		// validate record
		int recordId = request.getRecordId();
		if (recordId <= 0 && !Util.isEmpty(request.getRecordUuid(), true)) {
			recordId = RecordUtil.getIdFromUuid(table.getTableName(), request.getRecordUuid(), null);
			if (recordId < 0) {
				throw new AdempiereException("@Record_ID@ / @UUID@ @NotFound@");
			}
		}

		final String whereClause = "EXISTS("
			+ "SELECT 1 FROM CM_Chat "
			+ "WHERE CM_Chat.CM_Chat_Id = CM_ChatEntry.CM_Chat_Id "
			+ "AND CM_Chat.Record_ID = ? "
			+ "AND CM_Chat.AD_Table_ID = ? "
			+ ")"
		;
		int recordCount = new Query(
				Env.getCtx(),
			I_CM_ChatEntry.Table_Name,
			whereClause,
			null
		)
			.setParameters(recordId, table.getAD_Table_ID())
			.count()
		;
		
		ExistsChatEntriesResponse.Builder builder = ExistsChatEntriesResponse.newBuilder()
			.setRecordCount(recordCount);

		return builder;
	}


	@Override
	public void listUserActivites(ListUserActivitesRequest request, StreamObserver<ListUserActivitesResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("List User Activities Requested is Null");
			}
			ListUserActivitesResponse.Builder builder = LogsServiceLogic.listUserActivites(request);
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

}
