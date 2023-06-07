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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.core.domains.models.I_AD_Browse;
import org.adempiere.model.MBrowse;
import org.adempiere.model.MBrowseField;
import org.adempiere.model.MView;
import org.adempiere.model.MViewColumn;
import org.adempiere.core.domains.models.I_AD_Column;
import org.adempiere.core.domains.models.I_AD_Element;
import org.adempiere.core.domains.models.I_AD_Field;
import org.adempiere.core.domains.models.I_AD_FieldGroup;
import org.adempiere.core.domains.models.I_AD_Form;
import org.adempiere.core.domains.models.I_AD_Menu;
import org.adempiere.core.domains.models.I_AD_Message;
import org.adempiere.core.domains.models.I_AD_Process;
import org.adempiere.core.domains.models.I_AD_Reference;
import org.adempiere.core.domains.models.I_AD_Tab;
import org.adempiere.core.domains.models.I_AD_Val_Rule;
import org.adempiere.core.domains.models.I_AD_Window;
import org.adempiere.core.domains.models.I_AD_Workflow;
import org.compiere.model.MColumn;
import org.compiere.model.MField;
import org.compiere.model.MForm;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MLookupInfo;
import org.compiere.model.MMenu;
import org.compiere.model.MMessage;
import org.compiere.model.MProcess;
import org.compiere.model.MProcessPara;
import org.compiere.model.MRecentItem;
import org.compiere.model.MReportView;
import org.compiere.model.MTab;
import org.compiere.model.MTable;
import org.compiere.model.MValRule;
import org.compiere.model.MWindow;
import org.compiere.model.M_Element;
import org.compiere.model.Query;
import org.adempiere.core.domains.models.X_AD_FieldGroup;
import org.adempiere.core.domains.models.X_AD_Reference;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Language;
import org.compiere.util.Util;
import org.compiere.wf.MWorkflow;
import org.spin.base.dictionary.DictionaryConvertUtil;
import org.spin.base.util.DictionaryUtil;
import org.spin.base.util.RecordUtil;
import org.spin.base.util.ReferenceUtil;
import org.spin.base.util.ValueUtil;
import org.spin.backend.grpc.dictionary.Browser;
import org.spin.backend.grpc.dictionary.ContextInfo;
import org.spin.backend.grpc.dictionary.DependentField;
import org.spin.backend.grpc.dictionary.DictionaryGrpc.DictionaryImplBase;
import org.spin.backend.grpc.dictionary.EntityRequest;
import org.spin.backend.grpc.dictionary.Field;
import org.spin.backend.grpc.dictionary.FieldCondition;
import org.spin.backend.grpc.dictionary.FieldDefinition;
import org.spin.backend.grpc.dictionary.FieldGroup;
import org.spin.backend.grpc.dictionary.FieldRequest;
import org.spin.backend.grpc.dictionary.Form;
import org.spin.backend.grpc.dictionary.ListFieldsRequest;
import org.spin.backend.grpc.dictionary.ListFieldsResponse;
import org.spin.backend.grpc.dictionary.MessageText;
import org.spin.backend.grpc.dictionary.Process;
import org.spin.backend.grpc.dictionary.Reference;
import org.spin.backend.grpc.dictionary.ReferenceRequest;
import org.spin.backend.grpc.dictionary.ReportExportType;
import org.spin.backend.grpc.dictionary.Tab;
import org.spin.backend.grpc.dictionary.ValidationRule;
import org.spin.backend.grpc.dictionary.Window;
import org.spin.model.MADContextInfo;
import org.spin.model.MADFieldCondition;
import org.spin.model.MADFieldDefinition;
import org.spin.util.ASPUtil;
import org.spin.util.AbstractExportFormat;
import org.spin.util.ReportExportHandler;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * @author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 * Dictionary service
 * Get all dictionary meta-data
 */
public class DictionaryServiceImplementation extends DictionaryImplBase {
	/**	Logger			*/
	private CLogger log = CLogger.getCLogger(DictionaryServiceImplementation.class);
	
	@Override
	public void getWindow(EntityRequest request, StreamObserver<Window> responseObserver) {
		requestWindow(request, responseObserver, true);
	}
	
	@Override
	public void getTab(EntityRequest request, StreamObserver<Tab> responseObserver) {
		requestTab(request, responseObserver, true);
	}
	
	@Override
	public void getField(FieldRequest request, StreamObserver<Field> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getFieldUuid());
			Field.Builder fieldBuilder = getField(request);
			responseObserver.onNext(fieldBuilder.build());
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
	public void getReference(ReferenceRequest request, StreamObserver<Reference> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getReferenceUuid());
			Reference.Builder fieldBuilder = convertReference(Env.getCtx(), request);
			responseObserver.onNext(fieldBuilder.build());
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
	public void getValidationRule(EntityRequest request, StreamObserver<ValidationRule> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getUuid());
			ValidationRule.Builder fieldBuilder = convertValidationRule(Env.getCtx(), request);
			responseObserver.onNext(fieldBuilder.build());
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
	public void getProcess(EntityRequest request, StreamObserver<Process> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getUuid());
			Process.Builder processBuilder = convertProcess(Env.getCtx(), request.getUuid(), request.getId(), true);
			responseObserver.onNext(processBuilder.build());
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
	public void getBrowser(EntityRequest request, StreamObserver<Browser> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getUuid());
			Browser.Builder browserBuilder = convertBrowser(Env.getCtx(), request.getUuid(), true);
			responseObserver.onNext(browserBuilder.build());
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
	public void getForm(EntityRequest request, StreamObserver<Form> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getUuid());
			Form.Builder formBuilder = convertForm(Env.getCtx(), request.getUuid(), request.getId());
			responseObserver.onNext(formBuilder.build());
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
	 * Request with parameters
	 */
	public void requestWindow(EntityRequest request, StreamObserver<Window> responseObserver, boolean withTabs) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getUuid());
			Window.Builder windowBuilder = convertWindow(Env.getCtx(), request.getUuid(), request.getId(), withTabs);
			responseObserver.onNext(windowBuilder.build());
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
	 * Request with parameter
	 * @param request
	 * @param responseObserver
	 * @param withFields
	 */
	public void requestTab(EntityRequest request, StreamObserver<Tab> responseObserver, boolean withFields) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			log.fine("Menu Requested = " + request.getUuid());
			Tab.Builder tabBuilder = convertTab(Env.getCtx(), request.getUuid(), withFields);
			responseObserver.onNext(tabBuilder.build());
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
	 * Request Window: can be only window or child
	 * @param request
	 * @param uuid
	 * @param id
	 * @param withTabs
	 */
	private Window.Builder convertWindow(Properties context, String uuid, int id, boolean withTabs) {
		MWindow window = null;
		if(id > 0) {
			window = MWindow.get(context, id);
		} else if(!Util.isEmpty(uuid)) {
			window = new Query(context, I_AD_Window.Table_Name, I_AD_Window.COLUMNNAME_UUID + " = ?", null)
					.setParameters(uuid)
					.setOnlyActiveRecords(true)
					.first(); 
		}
		if(window == null) {
			return Window.newBuilder();
		}
		return convertWindow(context, window, withTabs);
	}
	
	/**
	 * Request Form from uuid
	 * @param context
	 * @param uuid
	 * @param id
	 */
	private Form.Builder convertForm(Properties context, String uuid, int id) {
		String whereClause = null;
		Object parameter = null;
		if(id > 0) {
			whereClause = I_AD_Form.COLUMNNAME_AD_Form_ID + " = ?";
			parameter = id;
		} else if(!Util.isEmpty(uuid)) {
			whereClause = I_AD_Form.COLUMNNAME_UUID + " = ?";
			parameter = uuid;
		}
		if(parameter == null) {
			return Form.newBuilder();
		}
		MForm form = new Query(context, I_AD_Form.Table_Name, whereClause, null)
				.setParameters(parameter)
				.setOnlyActiveRecords(true)
				.first();
		return convertForm(context, form);
	}
	
	/**
	 * Convert Window from Window Model
	 * @param form
	 * @return
	 */
	private Form.Builder convertForm(Properties context, MForm form) {
		Form.Builder builder = Form.newBuilder();
		if (form == null) {
			return builder;
		}
		//	
		builder
				.setId(form.getAD_Form_ID())
				.setUuid(ValueUtil.validateNull(form.getUUID()))
				.setName(ValueUtil.validateNull(ValueUtil.getTranslation(form, MForm.COLUMNNAME_Name)))
				.setDescription(ValueUtil.validateNull(ValueUtil.getTranslation(form, MForm.COLUMNNAME_Description)))
				.setHelp(ValueUtil.validateNull(ValueUtil.getTranslation(form, MForm.COLUMNNAME_Help)))
				.setIsActive(form.isActive());
		//	File Name
		String fileName = form.getClassname();
		if(!Util.isEmpty(fileName)) {
			int endIndex = fileName.lastIndexOf(".");
			int beginIndex = fileName.lastIndexOf("/");
			if(beginIndex == -1) {
				beginIndex = fileName.lastIndexOf(".");
				endIndex = -1;
			}
			if(beginIndex == -1) {
				beginIndex = 0;
			} else {
				beginIndex++;
			}
			if(endIndex == -1) {
				endIndex = fileName.length();
			}
			//	Set
			builder.setFileName(ValueUtil.validateNull(fileName.substring(beginIndex, endIndex)));
		}
		//	Add to recent Item
		addToRecentItem(MMenu.ACTION_Form, form.getAD_Form_ID());
		//	return
		return builder;
	}

	/**
	 * Convert Window from Window Model
	 * @param window
	 * @param withTabs
	 * @return
	 */
	private Window.Builder convertWindow(Properties context, MWindow window, boolean withTabs) {
		if (window == null) {
			return Window.newBuilder();
		}
		window = ASPUtil.getInstance(context).getWindow(window.getAD_Window_ID());
		Window.Builder builder = null;
		ContextInfo.Builder contextInfoBuilder = convertContextInfo(context, window.getAD_ContextInfo_ID());
		//	
		builder = Window.newBuilder()
				.setId(window.getAD_Window_ID())
				.setUuid(ValueUtil.validateNull(window.getUUID()))
				.setName(window.getName())
				.setDescription(ValueUtil.validateNull(window.getDescription()))
				.setHelp(ValueUtil.validateNull(window.getHelp()))
				.setWindowType(ValueUtil.validateNull(window.getWindowType()))
				.setIsSalesTransaction(window.isSOTrx())
				.setIsActive(window.isActive());
		if(contextInfoBuilder != null) {
			builder.setContextInfo(contextInfoBuilder.build());
		}
		//	With Tabs
		if(withTabs) {
//			List<Tab.Builder> tabListForGroup = new ArrayList<>();
			List<MTab> tabs = ASPUtil.getInstance(context).getWindowTabs(window.getAD_Window_ID());
			for(MTab tab : tabs) {
				if(!tab.isActive()) {
					continue;
				}
				Tab.Builder tabBuilder = convertTab(context, tab, tabs, withTabs);
				builder.addTabs(tabBuilder.build());
//				//	Get field group
//				int [] fieldGroupIdArray = getFieldGroupIdsFromTab(tab.getAD_Tab_ID());
//				if(fieldGroupIdArray != null) {
//					for(int fieldGroupId : fieldGroupIdArray) {
//						Tab.Builder tabFieldGroup = convertTab(context, tab, false);
//						FieldGroup.Builder fieldGroup = convertFieldGroup(context, fieldGroupId);
//						tabFieldGroup.setFieldGroup(fieldGroup);
//						tabFieldGroup.setName(fieldGroup.getName());
//						tabFieldGroup.setDescription("");
//						tabFieldGroup.setUuid(tabFieldGroup.getUuid() + "---");
//						//	Add to list
//						tabListForGroup.add(tabFieldGroup);
//					}
//				}
			}
			//	Add Field Group Tabs
//			for(Tab.Builder tabFieldGroup : tabListForGroup) {
//				builder.addTabs(tabFieldGroup.build());
//			}
		}
		//	Add to recent Item
		addToRecentItem(MMenu.ACTION_Window, window.getAD_Window_ID());
		//	return
		return builder;
	}
	
	/**
	 * Add element to recent item
	 * @param action
	 * @param optionId
	 */
	private void addToRecentItem(String action, int optionId) {
		if(Util.isEmpty(action)) {
			return;
		}
		String whereClause = null;
		if(action.equals(MMenu.ACTION_Window)) {
			whereClause = I_AD_Window.COLUMNNAME_AD_Window_ID + " = ?";
		} else if(action.equals(MMenu.ACTION_Form)) {
			whereClause = I_AD_Form.COLUMNNAME_AD_Form_ID + " = ?";
		} else if(action.equals(MMenu.ACTION_Process) || action.equals(MMenu.ACTION_Report)) {
			whereClause = I_AD_Process.COLUMNNAME_AD_Process_ID + " = ?";
		} else if(action.equals(MMenu.ACTION_WorkFlow)) {
			whereClause = I_AD_Workflow.COLUMNNAME_AD_Workflow_ID + " = ?";
		} else if(action.equals(MMenu.ACTION_SmartBrowse)) {
			whereClause = I_AD_Browse.COLUMNNAME_AD_Browse_ID + " = ?";
		}
		//	Get menu
		int menuId = new Query(Env.getCtx(), I_AD_Menu.Table_Name, whereClause, null)
			.setParameters(optionId)
			.firstId();
		MRecentItem.addMenuOption(Env.getCtx(), menuId, optionId);
	}
	
//	/**
//	 * Get Field group from Tab
//	 * @param tabId
//	 * @return
//	 */
//	private int[] getFieldGroupIdsFromTab(int tabId) {
//		return DB.getIDsEx(null, "SELECT f.AD_FieldGroup_ID "
//				+ "FROM AD_Field f "
//				+ "INNER JOIN AD_FieldGroup fg ON(fg.AD_FieldGroup_ID = f.AD_FieldGroup_ID) "
//				+ "WHERE f.AD_Tab_ID = ? "
//				+ "AND fg.FieldGroupType = ? "
//				+ "GROUP BY f.AD_FieldGroup_ID", tabId, X_AD_FieldGroup.FIELDGROUPTYPE_Tab);
//	}
	
	/**
	 * Convert Tabs from UUID
	 * @param uuid
	 * @param withFields
	 * @return
	 */
	private Tab.Builder convertTab(Properties context, String uuid, boolean withFields) {
		MTab tab = MTab.get(context, RecordUtil.getIdFromUuid(I_AD_Tab.Table_Name, uuid, null));
		//	Convert
		return convertTab(context, tab, withFields);
	}
	
	/**
	 * Convert Process from UUID
	 * @param uuid
	 * @param id
	 * @param withParameters
	 * @return
	 */
	private Process.Builder convertProcess(Properties context, String uuid, int id, boolean withParameters) {
		MProcess process = null;
		if(id > 0) {
			process = MProcess.get(context, id);
		} else if(!Util.isEmpty(uuid)) {
			process = MProcess.get(context, RecordUtil.getIdFromUuid(I_AD_Process.Table_Name, uuid, null));
		}
		if(process == null) {
			return Process.newBuilder();
		}
		//	Convert
		return convertProcess(context, process, withParameters);
	}
	
	/**
	 * Convert Browser from UUID
	 * @param uuid
	 * @param withFields
	 * @return
	 */
	private Browser.Builder convertBrowser(Properties context, String uuid, boolean withFields) {
		MBrowse browser = ASPUtil.getInstance(context).getBrowse(RecordUtil.getIdFromUuid(I_AD_Browse.Table_Name, uuid, null));
		//	Convert
		return convertBrowser(context, browser, withFields);
	}
	
	/**
	 * Convert Model tab to builder tab
	 * @param tab
	 * @return
	 */
	private Tab.Builder convertTab(Properties context, MTab tab, boolean withFields) {
		return convertTab(context, tab, null, withFields);
	}
	
	/**
	 * Convert Model tab to builder tab
	 * @param tab
	 * @return
	 */
	private Tab.Builder convertTab(Properties context, MTab tab, List<MTab> tabs, boolean withFields) {
		if (tab == null) {
			return Tab.newBuilder();
		}

		int tabId = tab.getAD_Tab_ID();
		tab = ASPUtil.getInstance(context).getWindowTab(tab.getAD_Window_ID(), tabId);

		String parentTabUuid = null;
		// root tab has no parent
		if (tab.getTabLevel() > 0) {
			int parentTabId = DictionaryUtil.getDirectParentTabId(tab.getAD_Window_ID(), tabId);
			if (parentTabId > 0) {
				MTable table = MTable.get(context, tab.getAD_Table_ID());
				parentTabUuid = RecordUtil.getUuidFromId(table.getTableName(), parentTabId, null);
			}
		}

		//	Get table attributes
		MTable table = MTable.get(context, tab.getAD_Table_ID());
		boolean isReadOnly = tab.isReadOnly() || table.isView();
		int contextInfoId = tab.getAD_ContextInfo_ID();
		if(contextInfoId <= 0) {
			contextInfoId = table.getAD_ContextInfo_ID();
		}

		// get where clause including link column and parent column
		String whereClause = DictionaryUtil.getSQLWhereClauseFromTab(context, tab, tabs);

		//	create build
		Tab.Builder builder = Tab.newBuilder()
				.setId(tab.getAD_Tab_ID())
				.setUuid(ValueUtil.validateNull(tab.getUUID()))
				.setName(ValueUtil.validateNull(tab.getName()))
				.setDescription(ValueUtil.validateNull(tab.getDescription()))
				.setHelp(ValueUtil.validateNull(tab.getHelp()))
				.setAccessLevel(Integer.parseInt(table.getAccessLevel()))
				.setCommitWarning(ValueUtil.validateNull(tab.getCommitWarning()))
				.setSequence(tab.getSeqNo())
				.setDisplayLogic(ValueUtil.validateNull(tab.getDisplayLogic()))
				.setReadOnlyLogic(ValueUtil.validateNull(tab.getReadOnlyLogic()))
				.setIsAdvancedTab(tab.isAdvancedTab())
				.setIsDeleteable(table.isDeleteable())
				.setIsDocument(table.isDocument())
				.setIsHasTree(tab.isHasTree())
				.setIsInfoTab(tab.isInfoTab())
				.setIsInsertRecord(!isReadOnly && tab.isInsertRecord())
				.setIsReadOnly(isReadOnly)
				.setIsSingleRow(tab.isSingleRow())
				.setIsSortTab(tab.isSortTab())
				.setIsTranslationTab(tab.isTranslationTab())
				.setIsView(table.isView())
				.setTabLevel(tab.getTabLevel())
				.setTableName(ValueUtil.validateNull(table.getTableName()))
			.setParentTabUuid(
				ValueUtil.validateNull(parentTabUuid)
			)
				.setIsChangeLog(table.isChangeLog())
				.setIsActive(tab.isActive())
				.addAllContextColumnNames(
					DictionaryUtil.getContextColumnNames(
						Optional.ofNullable(whereClause).orElse("")
						+ Optional.ofNullable(tab.getOrderByClause()).orElse("")
					)
				);

		//	For link
		if(contextInfoId > 0) {
			ContextInfo.Builder contextInfoBuilder = convertContextInfo(context, contextInfoId);
			builder.setContextInfo(contextInfoBuilder.build());
		}
		//	Parent Link Column Name
		if(tab.getParent_Column_ID() > 0) {
			MColumn column = MColumn.get(context, tab.getParent_Column_ID());
			builder.setParentColumnName(column.getColumnName());
		}
		//	Link Column Name
		if(tab.getAD_Column_ID() > 0) {
			MColumn column = MColumn.get(context, tab.getAD_Column_ID());
			builder.setLinkColumnName(column.getColumnName());
		}
		if(tab.isSortTab()) {
			//	Sort Column
			if(tab.getAD_ColumnSortOrder_ID() > 0) {
				MColumn column = MColumn.get(context, tab.getAD_ColumnSortOrder_ID());
				builder.setSortOrderColumnName(column.getColumnName());
			}
			//	Sort Yes / No
			if(tab.getAD_ColumnSortYesNo_ID() > 0) {
				MColumn column = MColumn.get(context, tab.getAD_ColumnSortYesNo_ID());
				builder.setSortYesNoColumnName(column.getColumnName());
			}
		}
		//	Process
		List<MProcess> processList = getProcessActionFromTab(context, tab);
		if(processList != null
				&& processList.size() > 0) {
			for(MProcess process : processList) {
				// get process associated without parameters
				Process.Builder processBuilder = convertProcess(context, process, false);
				builder.addProcesses(processBuilder.build());
			}
		}
		if(withFields) {
			for(MField field : ASPUtil.getInstance(context).getWindowFields(tab.getAD_Tab_ID())) {
				Field.Builder fieldBuilder = convertField(context, field, false);
				builder.addFields(fieldBuilder.build());
			}
		}
		//	
		return builder;
	}
	
	/**
	 * Get Parent column name from tab
	 * @param tab
	 * @return
	 */
	public static String getParentColumnNameFromTab(MTab tab) {
		String parentColumnName = null;
		if(tab.getParent_Column_ID() != 0) {
			parentColumnName = MColumn.getColumnName(tab.getCtx(), tab.getParent_Column_ID());
		}
		return parentColumnName;
	}
	
	/**
	 * Get Link column name from tab
	 * @param tab
	 * @return
	 */
	public static String getLinkColumnNameFromTab(MTab tab) {
		String parentColumnName = null;
		if(tab.getAD_Column_ID() != 0) {
			parentColumnName = MColumn.getColumnName(tab.getCtx(), tab.getAD_Column_ID());
		}
		return parentColumnName;
	}
	
	/**
	 * Convert Context Info to builder
	 * @param contextInfoId
	 * @return
	 */
	private ContextInfo.Builder convertContextInfo(Properties context, int contextInfoId) {
		ContextInfo.Builder builder = ContextInfo.newBuilder();
		if(contextInfoId > 0) {
			MADContextInfo contextInfoValue = MADContextInfo.getById(context, contextInfoId);
			MMessage message = MMessage.get(context, contextInfoValue.getAD_Message_ID());
			//	Get translation
			String msgText = null;
			String msgTip = null;
			String language = Env.getAD_Language(context);
			if(!Util.isEmpty(language)) {
				msgText = message.get_Translation(I_AD_Message.COLUMNNAME_MsgText, language);
				msgTip = message.get_Translation(I_AD_Message.COLUMNNAME_MsgTip, language);
			}
			//	Validate for default
			if(Util.isEmpty(msgText)) {
				msgText = message.getMsgText();
			}
			if(Util.isEmpty(msgTip)) {
				msgTip = message.getMsgTip();
			}
			//	Add message text
			MessageText.Builder messageText = MessageText.newBuilder();
			if (message != null) {
				messageText
					.setId(message.getAD_Message_ID())
					.setUuid(ValueUtil.validateNull(message.getUUID()))
					.setValue(ValueUtil.validateNull(message.getValue()))
					.setMessageText(ValueUtil.validateNull(msgText))
					.setMessageTip(ValueUtil.validateNull(msgTip))
				;
			}
			builder = ContextInfo.newBuilder()
					.setId(contextInfoValue.getAD_ContextInfo_ID())
					.setUuid(ValueUtil.validateNull(contextInfoValue.getUUID()))
					.setName(ValueUtil.validateNull(contextInfoValue.getName()))
					.setDescription(ValueUtil.validateNull(contextInfoValue.getDescription()))
					.setMessageText(messageText.build())
					.setSqlStatement(ValueUtil.validateNull(contextInfoValue.getSQLStatement()));
		}
		return builder;
	}
	
	/**
	 * Convert process to builder
	 * @param process
	 * @return
	 */
	private Process.Builder convertProcess(Properties context, MProcess process, boolean withParams) {
		if (process == null) {
			return Process.newBuilder();
		}
		process = ASPUtil.getInstance(context).getProcess(process.getAD_Process_ID());
		Process.Builder builder = Process.newBuilder()
				.setId(process.getAD_Process_ID())
				.setUuid(ValueUtil.validateNull(process.getUUID()))
				.setValue(ValueUtil.validateNull(process.getValue()))
				.setName(ValueUtil.validateNull(process.getName()))
				.setDescription(ValueUtil.validateNull(process.getDescription()))
				.setHelp(ValueUtil.validateNull(process.getHelp()))
				.setAccessLevel(Integer.parseInt(process.getAccessLevel()))
				.setIsDirectPrint(process.isDirectPrint())
				.setIsReport(process.isReport())
				.setIsActive(process.isActive());

		if (process.getAD_Browse_ID() > 0) {
			MBrowse browse = ASPUtil.getInstance(context).getBrowse(process.getAD_Browse_ID());
			builder.setBrowserUuid(ValueUtil.validateNull(browse.getUUID()));
		}
		if (process.getAD_Form_ID() > 0) {
			builder.setFormUuid(ValueUtil.validateNull(process.getAD_Form().getUUID()));
		}
		if (process.getAD_Workflow_ID() > 0) {
			MWorkflow workflow = MWorkflow.get(Env.getCtx(), process.getAD_Workflow_ID());
			builder.setWorkflowUuid(ValueUtil.validateNull(workflow.getUUID()));
		}
		//	Report Types
		if(process.isReport()) {
			MReportView reportView = null;
			if(process.getAD_ReportView_ID() > 0) {
				reportView = MReportView.get(context, process.getAD_ReportView_ID());
			}
			ReportExportHandler exportHandler = new ReportExportHandler(Env.getCtx(), reportView);
			for(AbstractExportFormat reportType : exportHandler.getExportFormatList()) {
				ReportExportType.Builder reportExportType = ReportExportType.newBuilder();
				reportExportType.setName(ValueUtil.validateNull(reportType.getName()));
				reportExportType.setDescription(ValueUtil.validateNull(reportType.getName()));
				reportExportType.setType(ValueUtil.validateNull(reportType.getExtension()));
				builder.addReportExportTypes(reportExportType.build());
			}
		}
		//	For parameters
		if(withParams) {
			String language = context.getProperty(Env.LANGUAGE);
			for(MProcessPara parameter : ASPUtil.getInstance(context).getProcessParameters(process.getAD_Process_ID())) {
				// TODO: Remove conditional with fix the issue https://github.com/solop-develop/backend/issues/28
				if(!Language.isBaseLanguage(language)) {
					//	Name
					String value = parameter.get_Translation(I_AD_Tab.COLUMNNAME_Name, language);
					if(!Util.isEmpty(value)) {
						parameter.set_ValueOfColumn(I_AD_Tab.COLUMNNAME_Name, value);
					}
					//	Description
					value = parameter.get_Translation(I_AD_Tab.COLUMNNAME_Description, language);
					if(!Util.isEmpty(value)) {
						parameter.set_ValueOfColumn(I_AD_Tab.COLUMNNAME_Description, value);
					}
					//	Help
					value = parameter.get_Translation(I_AD_Tab.COLUMNNAME_Help, language);
					if(!Util.isEmpty(value)) {
						parameter.set_ValueOfColumn(I_AD_Tab.COLUMNNAME_Help, value);
					}
				}
				
				Field.Builder fieldBuilder = convertProcessParameter(context, parameter);
				builder.addParameters(fieldBuilder.build());
			}
		}
		return builder;
	}
	
	/**
	 * Convert process to builder
	 * @param browser
	 * @param withFields
	 * @return
	 */
	private Browser.Builder convertBrowser(Properties context, MBrowse browser, boolean withFields) {
		if (browser == null) {
			return Browser.newBuilder();
		}
		String query = DictionaryUtil.addQueryReferencesFromBrowser(browser);
		String orderByClause = DictionaryUtil.getSQLOrderBy(browser);
		Browser.Builder builder = Browser.newBuilder()
				.setId(browser.getAD_Browse_ID())
				.setUuid(ValueUtil.validateNull(browser.getUUID()))
				.setValue(ValueUtil.validateNull(browser.getValue()))
				.setName(browser.getName())
				.setDescription(ValueUtil.validateNull(browser.getDescription()))
				.setHelp(ValueUtil.validateNull(browser.getHelp()))
				.setAccessLevel(Integer.parseInt(browser.getAccessLevel()))
				.setIsActive(browser.isActive())
				.setIsCollapsibleByDefault(browser.isCollapsibleByDefault())
				.setIsDeleteable(browser.isDeleteable())
				.setIsExecutedQueryByDefault(browser.isExecutedQueryByDefault())
				.setIsSelectedByDefault(browser.isSelectedByDefault())
				.setIsShowTotal(browser.isShowTotal())
				.setIsUpdateable(browser.isUpdateable())
				.addAllContextColumnNames(
					DictionaryUtil.getContextColumnNames(
						Optional.ofNullable(query).orElse("")
						+ Optional.ofNullable(browser.getWhereClause()).orElse("")
						+ Optional.ofNullable(orderByClause).orElse("")
					)
				);
		//	Set View UUID
		if(browser.getAD_View_ID() > 0) {
			MView view = new MView(Env.getCtx(), browser.getAD_View_ID());
			builder.setViewUuid(ValueUtil.validateNull(view.getUUID()));
		}
		// set table name
		if (browser.getAD_Table_ID() > 0) {
			MTable table = MTable.get(Env.getCtx(), browser.getAD_Table_ID());
			builder.setTableName(ValueUtil.validateNull(table.getTableName()));	
		}
		//	Window Reference
		if(browser.getAD_Window_ID() > 0) {
			MWindow window = ASPUtil.getInstance(context).getWindow(browser.getAD_Window_ID());
			Window.Builder windowBuilder = convertWindow(context, window, false);
			builder.setWindow(windowBuilder.build());
		}
		//	Process Reference
		if(browser.getAD_Process_ID() > 0) {
			Process.Builder processBuilder = convertProcess(context, MProcess.get(context, browser.getAD_Process_ID()), false);
			builder.setProcess(processBuilder.build());
		}
		//	For parameters
		if(withFields) {
			for(MBrowseField field : ASPUtil.getInstance(context).getBrowseFields(browser.getAD_Browse_ID())) {
				Field.Builder fieldBuilder = convertBrowseField(context, field);
				builder.addFields(fieldBuilder.build());
			}
		}
		//	Add to recent Item
		addToRecentItem(MMenu.ACTION_SmartBrowse, browser.getAD_Window_ID());
		return builder;
	}
	
	/**
	 * Get process action from tab
	 * @param tab
	 * @return
	 */
	private List<MProcess> getProcessActionFromTab(Properties context, MTab tab) {
		// to prevent duplicity of associated processes in different locations (table, column and tab).
		HashMap<Integer, MProcess> processList = new HashMap<>();

		//	First Process Tab
		if(tab.getAD_Process_ID() > 0) {
			processList.put(tab.getAD_Process_ID(), MProcess.get(context, tab.getAD_Process_ID()));
		}

		//	Process from tab
		List<MProcess> processFromTabList = new Query(tab.getCtx(), I_AD_Process.Table_Name, "EXISTS(SELECT 1 FROM AD_Field f "
				+ "INNER JOIN AD_Column c ON(c.AD_Column_ID = f.AD_Column_ID) "
				+ "WHERE c.AD_Process_ID = AD_Process.AD_Process_ID "
				+ "AND f.AD_Tab_ID = ? "
				+ "AND f.IsActive = 'Y')", null)
				.setParameters(tab.getAD_Tab_ID())
				.setOnlyActiveRecords(true)
				.<MProcess>list();
		for(MProcess process : processFromTabList) {
			processList.put(process.getAD_Process_ID(), process);
		}

		//	Process from table
		List<MProcess> processFromTableList = new Query(tab.getCtx(), I_AD_Process.Table_Name, 
				"EXISTS(SELECT 1 FROM AD_Table_Process WHERE AD_Process_ID = AD_Process.AD_Process_ID AND AD_Table_ID = ?)", null)
				.setParameters(tab.getAD_Table_ID())
				.setOnlyActiveRecords(true)
				.<MProcess>list();
		for(MProcess process : processFromTableList) {
			processList.put(process.getAD_Process_ID(), process);
		}

		return new ArrayList<MProcess>(processList.values());
	}
	
	/**
	 * Convert Process Parameter
	 * @param processParameter
	 * @return
	 */
	private Field.Builder convertProcessParameter(Properties context, MProcessPara processParameter) {
		if (processParameter == null) {
			return Field.newBuilder();
		}
		//	Convert
		Field.Builder builder = Field.newBuilder()
				.setId(processParameter.getAD_Process_Para_ID())
				.setUuid(ValueUtil.validateNull(processParameter.getUUID()))
				.setName(ValueUtil.validateNull(processParameter.getName()))
				.setDescription(ValueUtil.validateNull(processParameter.getDescription()))
				.setHelp(ValueUtil.validateNull(processParameter.getHelp()))
				.setColumnName(ValueUtil.validateNull(processParameter.getColumnName()))
				.setElementName(ValueUtil.validateNull(processParameter.getColumnName()))
				.setDefaultValue(ValueUtil.validateNull(processParameter.getDefaultValue()))
				.setDefaultValueTo(ValueUtil.validateNull(processParameter.getDefaultValue2()))
				.setDisplayLogic(ValueUtil.validateNull(processParameter.getDisplayLogic()))
				.setDisplayType(processParameter.getAD_Reference_ID())
				.setIsDisplayed(true)
				.setIsInfoOnly(processParameter.isInfoOnly())
				.setIsMandatory(processParameter.isMandatory())
				.setIsRange(processParameter.isRange())
				.setReadOnlyLogic(ValueUtil.validateNull(processParameter.getReadOnlyLogic()))
				.setSequence(processParameter.getSeqNo())
				.setValueMax(ValueUtil.validateNull(processParameter.getValueMax()))
				.setValueMin(ValueUtil.validateNull(processParameter.getValueMin()))
				.setVFormat(ValueUtil.validateNull(processParameter.getVFormat()))
				.setFieldLength(processParameter.getFieldLength())
				.setIsActive(processParameter.isActive())
				.addAllContextColumnNames(
						DictionaryUtil.getContextColumnNames(Optional.ofNullable(processParameter.getDefaultValue()).orElse("") + Optional.ofNullable(processParameter.getDefaultValue2()).orElse(""))
				);
		//	
		int displayTypeId = processParameter.getAD_Reference_ID();
		if (ReferenceUtil.validateReference(displayTypeId)) {
			//	Reference Value
			int referenceValueId = processParameter.getAD_Reference_Value_ID();
			//	Validation Code
			int validationRuleId = processParameter.getAD_Val_Rule_ID();

			String columnName = processParameter.getColumnName();
			if (processParameter.getAD_Element_ID() > 0) {
				columnName = processParameter.getAD_Element().getColumnName();
			}

			MLookupInfo info = ReferenceUtil.getReferenceLookupInfo(
				displayTypeId, referenceValueId, columnName, validationRuleId
			);
			if (info != null) {
				Reference.Builder referenceBuilder = DictionaryConvertUtil.convertReference(context, info);
				builder.setReference(referenceBuilder.build());
			}
		}

		List<DependentField> dependentProcessParameters = generateDependentProcessParameters(processParameter);
		builder.addAllDependentFields(dependentProcessParameters);

		return builder;
	}

	private List<DependentField> generateDependentProcessParameters(MProcessPara processParameter) {
		List<DependentField> depenentFieldsList = new ArrayList<>();

		String parentColumnName = processParameter.getColumnName();

		MProcess process = ASPUtil.getInstance().getProcess(processParameter.getAD_Process_ID());
		List<MProcessPara> parametersList = ASPUtil.getInstance().getProcessParameters(processParameter.getAD_Process_ID());

		parametersList.stream()
			.filter(currentParameter -> {
				if (!currentParameter.isActive()) {
					return false;
				}
				// Display Logic
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentParameter.getDisplayLogic())) {
					return true;
				}
				// Default Value of Column
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentParameter.getDefaultValue())) {
					return true;
				}
				// ReadOnly Logic
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentParameter.getReadOnlyLogic())) {
					return true;
				}
				// Dynamic Validation
				if (currentParameter.getAD_Val_Rule_ID() > 0) {
					MValRule validationRule = MValRule.get(Env.getCtx(), currentParameter.getAD_Val_Rule_ID());
					if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, validationRule.getCode())) {
						return true;
					}
				}
				return false;
			})
			.forEach(currentParameter -> {
				DependentField.Builder builder = DependentField.newBuilder();
				builder.setContainerId(process.getAD_Process_ID());
				builder.setContainerUuid(process.getUUID());
				builder.setContainerName(process.getName());

				builder.setId(currentParameter.getAD_Process_Para_ID());
				builder.setUuid(currentParameter.getUUID());
				builder.setColumnName(currentParameter.getColumnName());

				depenentFieldsList.add(builder.build());
			});

		return depenentFieldsList;
	}

	/**
	 * Convert Browse Field
	 * @param browseField
	 * @return
	 */
	private Field.Builder convertBrowseField(Properties context, MBrowseField browseField) {
		if (browseField == null) {
			return Field.newBuilder();
		}
		//	Convert
		Field.Builder builder = Field.newBuilder()
				.setId(browseField.getAD_Browse_Field_ID())
				.setUuid(ValueUtil.validateNull(browseField.getUUID()))
				.setName(ValueUtil.validateNull(browseField.getName()))
				.setDescription(ValueUtil.validateNull(browseField.getDescription()))
				.setHelp(ValueUtil.validateNull(browseField.getHelp()))
				.setDefaultValue(ValueUtil.validateNull(browseField.getDefaultValue()))
				.setDefaultValueTo(ValueUtil.validateNull(browseField.getDefaultValue2()))
				.setDisplayLogic(ValueUtil.validateNull(browseField.getDisplayLogic()))
				.setDisplayType(browseField.getAD_Reference_ID())
				.setIsDisplayed(browseField.isDisplayed())
				.setIsQueryCriteria(browseField.isQueryCriteria())
				.setIsOrderBy(browseField.isOrderBy())
				.setIsInfoOnly(browseField.isInfoOnly())
				.setIsMandatory(browseField.isMandatory())
				.setIsRange(browseField.isRange())
				.setIsReadOnly(browseField.isReadOnly())
				.setReadOnlyLogic(ValueUtil.validateNull(browseField.getReadOnlyLogic()))
				.setIsKey(browseField.isKey())
				.setIsIdentifier(browseField.isIdentifier())
				.setSeqNoGrid(browseField.getSeqNoGrid())
				.setSequence(browseField.getSeqNo())
				.setValueMax(ValueUtil.validateNull(browseField.getValueMax()))
				.setValueMin(ValueUtil.validateNull(browseField.getValueMin()))
				.setVFormat(ValueUtil.validateNull(browseField.getVFormat()))
				.setIsActive(browseField.isActive())
				.setCallout(ValueUtil.validateNull(browseField.getCallout()))
				.setFieldLength(browseField.getFieldLength())
				.addAllContextColumnNames(
						DictionaryUtil.getContextColumnNames(Optional.ofNullable(browseField.getDefaultValue()).orElse("") + Optional.ofNullable(browseField.getDefaultValue2()).orElse(""))
				);
		
		String elementName = null;
		MViewColumn viewColumn = MViewColumn.getById(context, browseField.getAD_View_Column_ID(), null);
		builder.setColumnName(ValueUtil.validateNull(viewColumn.getColumnName()));
		if(viewColumn.getAD_Column_ID() != 0) {
			MColumn column = MColumn.get(context, viewColumn.getAD_Column_ID());
			elementName = column.getColumnName();
			builder.setColumnId(column.getAD_Column_ID());
			builder.setColumnUuid(ValueUtil.validateNull(column.getUUID()));
		}

		//	Default element
		if(Util.isEmpty(elementName)) {
			elementName = browseField.getAD_Element().getColumnName();
		}
		builder.setElementName(ValueUtil.validateNull(elementName));
		builder.setElementId(browseField.getAD_Element_ID());
		builder.setElementUuid(ValueUtil.validateNull(browseField.getAD_Element().getUUID()));

		//	
		int displayTypeId = browseField.getAD_Reference_ID();
		if (ReferenceUtil.validateReference(displayTypeId)) {
			//	Reference Value
			int referenceValueId = browseField.getAD_Reference_Value_ID();
			//	Validation Code
			int validationRuleId = browseField.getAD_Val_Rule_ID();

			// TODO: Verify this conditional with "elementName" variable
			String columnName = browseField.getAD_Element().getColumnName();
			if (viewColumn.getAD_Column_ID() > 0) {
				MColumn column = MColumn.get(context, viewColumn.getAD_Column_ID());
				columnName = column.getColumnName();
			}

			MLookupInfo info = ReferenceUtil.getReferenceLookupInfo(
				displayTypeId, referenceValueId, columnName, validationRuleId
			);
			if (info != null) {
				Reference.Builder referenceBuilder = DictionaryConvertUtil.convertReference(context, info);
				builder.setReference(referenceBuilder.build());
			} else {
				builder.setDisplayType(DisplayType.String);
			}
		}

		List<DependentField> dependentBrowseFieldsList = generateDependentBrowseFields(browseField);
		builder.addAllDependentFields(dependentBrowseFieldsList);

		return builder;
	}

	private List<DependentField> generateDependentBrowseFields(MBrowseField browseField) {
		List<DependentField> depenentFieldsList = new ArrayList<>();

		MViewColumn viewColumn = MViewColumn.getById(Env.getCtx(), browseField.getAD_View_Column_ID(), null);
		String parentColumnName = viewColumn.getColumnName();

		String elementName = null;
		if(viewColumn.getAD_Column_ID() != 0) {
			MColumn column = MColumn.get(Env.getCtx(), viewColumn.getAD_Column_ID());
			elementName = column.getColumnName();
		}
		if(Util.isEmpty(elementName, true)) {
			elementName = browseField.getAD_Element().getColumnName();
		}
		String parentElementName = elementName;

		MBrowse browse = ASPUtil.getInstance().getBrowse(browseField.getAD_Browse_ID());
		List<MBrowseField> browseFieldsList = ASPUtil.getInstance().getBrowseFields(browseField.getAD_Browse_ID());

		browseFieldsList.stream()
			.filter(currentBrowseField -> {
				if(!currentBrowseField.isActive()) {
					return false;
				}
				// Display Logic
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentBrowseField.getDisplayLogic())
					|| DictionaryUtil.isUseParentColumnOnContext(parentElementName, currentBrowseField.getDisplayLogic())) {
					return true;
				}
				// Default Value
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentBrowseField.getDefaultValue())
					|| DictionaryUtil.isUseParentColumnOnContext(parentElementName, currentBrowseField.getDefaultValue())) {
					return true;
				}
				// Default Value 2
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentBrowseField.getDefaultValue2())
					|| DictionaryUtil.isUseParentColumnOnContext(parentElementName, currentBrowseField.getDefaultValue2())) {
					return true;
				}
				// ReadOnly Logic
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentBrowseField.getReadOnlyLogic())
					|| DictionaryUtil.isUseParentColumnOnContext(parentElementName, currentBrowseField.getReadOnlyLogic())) {
					return true;
				}
				// Dynamic Validation
				if (currentBrowseField.getAD_Val_Rule_ID() > 0) {
					MValRule validationRule = MValRule.get(Env.getCtx(), currentBrowseField.getAD_Val_Rule_ID());
					if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, validationRule.getCode())
						|| DictionaryUtil.isUseParentColumnOnContext(parentElementName, validationRule.getCode())) {
						return true;
					}
				}
				return false;
			})
			.forEach(currentBrowseField -> {
				DependentField.Builder builder = DependentField.newBuilder();

				builder.setContainerId(browse.getAD_Browse_ID());
				builder.setContainerUuid(browse.getUUID());
				builder.setContainerName(browse.getName());
				builder.setId(currentBrowseField.getAD_Browse_Field_ID());
				builder.setUuid(currentBrowseField.getUUID());

				MViewColumn currentViewColumn = MViewColumn.getById(Env.getCtx(), currentBrowseField.getAD_View_Column_ID(), null);
				builder.setColumnName(currentViewColumn.getColumnName());

				depenentFieldsList.add(builder.build());
			});

		return depenentFieldsList;
	}

	/**
	 * Convert field from request
	 * @param context
	 * @param request
	 * @return
	 */
	private Field.Builder getField(FieldRequest request) {
		Field.Builder builder = Field.newBuilder();
		//	For UUID
		if(!Util.isEmpty(request.getFieldUuid())) {
			builder = convertField(Env.getCtx(), request.getFieldUuid());
		} else if(!Util.isEmpty(request.getColumnUuid())) {
			MColumn column = new Query(Env.getCtx(), I_AD_Column.Table_Name, I_AD_Column.COLUMNNAME_UUID + " = ?", null)
					.setParameters(request.getColumnUuid())
					.setOnlyActiveRecords(true)
					.first();
			builder = convertField(Env.getCtx(), column);
		} else if(!Util.isEmpty(request.getElementUuid())) {
			M_Element element = new Query(Env.getCtx(), I_AD_Element.Table_Name, I_AD_Element.COLUMNNAME_UUID + " = ?", null)
					.setParameters(request.getElementUuid())
					.setOnlyActiveRecords(true)
					.first();
			builder = convertField(Env.getCtx(), element);
		} else if(!Util.isEmpty(request.getElementColumnName())) {
			M_Element element = new Query(Env.getCtx(), I_AD_Element.Table_Name, I_AD_Element.COLUMNNAME_ColumnName+ " = ?", null)
					.setParameters(request.getElementColumnName())
					.setOnlyActiveRecords(true)
					.first();
			builder = convertField(Env.getCtx(), element);
		} else if(!Util.isEmpty(request.getTableName()) 
				&& !Util.isEmpty(request.getColumnName())) {
			MTable table = MTable.get(Env.getCtx(), request.getTableName());
			if(table != null) {
				MColumn column = table.getColumn(request.getColumnName());
				builder = convertField(Env.getCtx(), column);
			}
		}
		return builder;
	}
	
	/**
	 * Convert Field from UUID
	 * @param uuid
	 * @return
	 */
	private Field.Builder convertField(Properties context, String uuid) {
		MField field = new Query(context, I_AD_Field.Table_Name, I_AD_Field.COLUMNNAME_AD_Field_ID + " = ?", null)
				.setParameters(RecordUtil.getIdFromUuid(I_AD_Field.Table_Name, uuid, null))
				.setOnlyActiveRecords(true)
				.first();
		int fieldId = field.getAD_Field_ID();
		List<MField> customFields = ASPUtil.getInstance(context).getWindowFields(field.getAD_Tab_ID());
		if(customFields != null) {
			Optional<MField> maybeField = customFields.stream().filter(customField -> customField.getAD_Field_ID() == fieldId).findFirst();
			if(maybeField.isPresent()) {
				field = maybeField.get();

				// TODO: Remove conditional with fix the issue https://github.com/solop-develop/backend/issues/28
				String language = context.getProperty(Env.LANGUAGE);
				if(!Language.isBaseLanguage(language)) {
					//	Name
					String value = field.get_Translation(I_AD_Field.COLUMNNAME_Name, language);
					if (!Util.isEmpty(value, true)) {
						field.set_ValueOfColumn(I_AD_Field.COLUMNNAME_Name, value);
					}
					//	Description
					value = field.get_Translation(I_AD_Field.COLUMNNAME_Description, language);
					if (!Util.isEmpty(value, true)) {
						field.set_ValueOfColumn(I_AD_Field.COLUMNNAME_Description, value);
					}
					//	Help
					value = field.get_Translation(I_AD_Tab.COLUMNNAME_Help, language);
					if (!Util.isEmpty(value, true)) {
						field.set_ValueOfColumn(I_AD_Field.COLUMNNAME_Help, value);
					}
				}
			}
		}
		//	Convert
		return convertField(context, field, true);
	}
	
	/**
	 * Convert field to builder
	 * @param column
	 * @param language
	 * @return
	 */
	private Field.Builder convertField(Properties context, MColumn column) {
		if (column == null) {
			return Field.newBuilder();
		}
		String defaultValue = column.getDefaultValue();
		if(Util.isEmpty(defaultValue)) {
			defaultValue = column.getDefaultValue();
		}
		//	Display Type
		int displayTypeId = column.getAD_Reference_ID();
		// element
		M_Element element = new M_Element(context, column.getAD_Element_ID(), null);
		//	Convert
		Field.Builder builder = Field.newBuilder()
				.setId(column.getAD_Column_ID())
				.setUuid(ValueUtil.validateNull(column.getUUID()))
				.setName(ValueUtil.validateNull(column.getName()))
				.setDescription(ValueUtil.validateNull(column.getDescription()))
				.setHelp(ValueUtil.validateNull(column.getHelp()))
				.setCallout(ValueUtil.validateNull(column.getCallout()))
				.setColumnId(column.getAD_Column_ID())
				.setColumnUuid(ValueUtil.validateNull(column.getUUID()))
				.setColumnName(ValueUtil.validateNull(column.getColumnName()))
				.setElementId(element.getAD_Element_ID())
				.setElementUuid(ValueUtil.validateNull(element.getUUID()))
				.setElementName(ValueUtil.validateNull(element.getColumnName()))
				.setColumnSql(ValueUtil.validateNull(column.getColumnSQL()))
				.setDefaultValue(ValueUtil.validateNull(defaultValue))
				.setDisplayType(displayTypeId)
				.setFormatPattern(ValueUtil.validateNull(column.getFormatPattern()))
				.setIdentifierSequence(column.getSeqNo())
				.setIsAllowCopy(column.isAllowCopy())
				.setIsAllowLogging(column.isAllowLogging())
				.setIsAlwaysUpdateable(column.isAlwaysUpdateable())
				.setIsEncrypted(column.isEncrypted())
				.setIsIdentifier(column.isIdentifier())
				.setIsKey(column.isKey())
				.setIsMandatory(column.isMandatory())
				.setIsParent(column.isParent())
				.setIsRange(column.isRange())
				.setIsSelectionColumn(column.isSelectionColumn())
				.setIsTranslated(column.isTranslated())
				.setIsUpdateable(column.isUpdateable())
				.setMandatoryLogic(ValueUtil.validateNull(column.getMandatoryLogic()))
				.setReadOnlyLogic(ValueUtil.validateNull(column.getReadOnlyLogic()))
				.setSequence(column.getSeqNo())
				.setValueMax(ValueUtil.validateNull(column.getValueMax()))
				.setValueMin(ValueUtil.validateNull(column.getValueMin()))
				.setFieldLength(column.getFieldLength())
				.setIsActive(column.isActive())
				.addAllContextColumnNames(
						DictionaryUtil.getContextColumnNames(Optional.ofNullable(column.getDefaultValue()).orElse(""))
				);
		//	Process
		if(column.getAD_Process_ID() > 0) {
			MProcess process = MProcess.get(context, column.getAD_Process_ID());
			Process.Builder processBuilder = convertProcess(context, process, false);
			builder.setProcess(processBuilder.build());
		}
		//	
		if (ReferenceUtil.validateReference(displayTypeId)) {
			//	Reference Value
			int referenceValueId = column.getAD_Reference_Value_ID();

			//	Validation Code
			int validationRuleId = column.getAD_Val_Rule_ID();

			MLookupInfo info = ReferenceUtil.getReferenceLookupInfo(
				displayTypeId, referenceValueId, column.getColumnName(), validationRuleId
			);
			if (info != null) {
				Reference.Builder referenceBuilder = DictionaryConvertUtil.convertReference(context, info);
				builder.setReference(referenceBuilder.build());
			} else {
				builder.setDisplayType(DisplayType.String);
			}
		}

		List<DependentField> depenentFieldsList = generateDependentColumns(column);
		builder.addAllDependentFields(depenentFieldsList);

		return builder;
	}

	private List<DependentField> generateDependentColumns(MColumn column) {
		List<DependentField> depenentFieldsList = new ArrayList<>();
		if (column == null) {
			return depenentFieldsList;
		}

		String parentColumnName = column.getColumnName();

		MTable table = MTable.get(Env.getCtx(), column.getAD_Table_ID());
		List<MColumn> columnsList = table.getColumnsAsList(false);

		columnsList.stream()
			.filter(currentColumn -> {
				if(!currentColumn.isActive()) {
					return false;
				}
				// Default Value
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentColumn.getDefaultValue())) {
					return true;
				}
				// ReadOnly Logic
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentColumn.getReadOnlyLogic())) {
					return true;
				}
				// Mandatory Logic
				if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentColumn.getMandatoryLogic())) {
					return true;
				}
				// Dynamic Validation
				if (currentColumn.getAD_Val_Rule_ID() > 0) {
					MValRule validationRule = MValRule.get(Env.getCtx(), currentColumn.getAD_Val_Rule_ID());
					if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, validationRule.getCode())) {
						return true;
					}
				}
				return false;
			})
			.forEach(currentColumn -> {
				DependentField.Builder builder = DependentField.newBuilder();

				builder.setContainerId(table.getAD_Table_ID());
				builder.setContainerUuid(table.getUUID());
				builder.setContainerName(table.getTableName());
	
				builder.setId(currentColumn.getAD_Column_ID());
				builder.setUuid(currentColumn.getUUID());
	
				builder.setColumnName(currentColumn.getColumnName());

				depenentFieldsList.add(builder.build());
			});

		return depenentFieldsList;
	}

	/**
	 * Convert field to builder
	 * @param element
	 * @return
	 */
	private Field.Builder convertField(Properties context, M_Element element) {
		if (element == null) {
			return Field.newBuilder();
		}
		//	Display Type
		int displayTypeId = element.getAD_Reference_ID();
		if(element.getAD_Reference_ID() > 0) {
			displayTypeId = element.getAD_Reference_ID();
		}
		//	Convert
		Field.Builder builder = Field.newBuilder()
				.setId(element.getAD_Element_ID())
				.setUuid(ValueUtil.validateNull(element.getUUID()))
				.setName(ValueUtil.validateNull(ValueUtil.getTranslation(element, M_Element.COLUMNNAME_Name)))
				.setDescription(ValueUtil.validateNull(ValueUtil.getTranslation(element, M_Element.COLUMNNAME_Description)))
				.setHelp(ValueUtil.validateNull(ValueUtil.getTranslation(element, M_Element.COLUMNNAME_Help)))
				.setColumnName(ValueUtil.validateNull(element.getColumnName()))
				.setElementName(ValueUtil.validateNull(element.getColumnName()))
				.setDisplayType(displayTypeId)
				.setFieldLength(element.getFieldLength())
				.setIsActive(element.isActive());
		//	
		if (ReferenceUtil.validateReference(displayTypeId)) {
			//	Reference Value
			int referenceValueId = element.getAD_Reference_Value_ID();
			if(element.getAD_Reference_Value_ID() > 0) {
				referenceValueId = element.getAD_Reference_Value_ID();
			}

			MLookupInfo info = ReferenceUtil.getReferenceLookupInfo(
				displayTypeId, referenceValueId, element.getColumnName(), 0
			);
			if (info != null) {
				Reference.Builder referenceBuilder = DictionaryConvertUtil.convertReference(context, info);
				builder.setReference(referenceBuilder.build());
			} else {
				builder.setDisplayType(DisplayType.String);
			}
		}
		return builder;
	}
	
	/**
	 * Convert field to builder
	 * @param field
	 * @param translate
	 * @return
	 */
	private Field.Builder convertField(Properties context, MField field, boolean translate) {
		if (field == null) {
			return Field.newBuilder();
		}
		// Column reference
		MColumn column = MColumn.get(context, field.getAD_Column_ID());
		M_Element element = new M_Element(context, column.getAD_Element_ID(), null);
		String defaultValue = field.getDefaultValue();
		if(Util.isEmpty(defaultValue)) {
			defaultValue = column.getDefaultValue();
		}
		//	Display Type
		int displayTypeId = column.getAD_Reference_ID();
		if(field.getAD_Reference_ID() > 0) {
			displayTypeId = field.getAD_Reference_ID();
		}
		//	Mandatory Property
		boolean isMandatory = column.isMandatory();
		if(!Util.isEmpty(field.getIsMandatory())) {
			isMandatory = !Util.isEmpty(field.getIsMandatory()) && field.getIsMandatory().equals("Y");
		}
		//	Convert
		Field.Builder builder = Field.newBuilder()
				.setId(field.getAD_Field_ID())
				.setUuid(ValueUtil.validateNull(field.getUUID()))
				.setName(ValueUtil.validateNull(field.getName()))
				.setDescription(ValueUtil.validateNull(field.getDescription()))
				.setHelp(ValueUtil.validateNull(field.getHelp()))
				.setCallout(ValueUtil.validateNull(column.getCallout()))
				.setColumnId(column.getAD_Column_ID())
				.setColumnUuid(column.getUUID())
				.setColumnName(ValueUtil.validateNull(column.getColumnName()))
				.setElementId(element.getAD_Element_ID())
				.setElementUuid(ValueUtil.validateNull(element.getUUID()))
				.setElementName(ValueUtil.validateNull(element.getColumnName()))
				.setColumnSql(ValueUtil.validateNull(column.getColumnSQL()))
				.setDefaultValue(ValueUtil.validateNull(defaultValue))
				.setDisplayLogic(ValueUtil.validateNull(field.getDisplayLogic()))
				.setDisplayType(displayTypeId)
				.setFormatPattern(ValueUtil.validateNull(column.getFormatPattern()))
				.setIdentifierSequence(column.getSeqNo())
				.setIsAllowCopy(field.isAllowCopy())
				.setIsAllowLogging(column.isAllowLogging())
				.setIsDisplayed(field.isDisplayed())
				.setIsAlwaysUpdateable(column.isAlwaysUpdateable())
				.setIsDisplayedGrid(field.isDisplayedGrid())
				.setIsEncrypted(field.isEncrypted() || column.isEncrypted())
				.setIsFieldOnly(field.isFieldOnly())
				.setIsHeading(field.isHeading())
				.setIsIdentifier(column.isIdentifier())
				.setIsKey(column.isKey())
				.setIsMandatory(isMandatory)
				.setIsParent(column.isParent())
				.setIsQuickEntry(field.isQuickEntry())
				.setIsRange(column.isRange())
				.setIsReadOnly(field.isReadOnly())
				.setIsSameLine(field.isSameLine())
				.setIsSelectionColumn(column.isSelectionColumn())
				.setIsTranslated(column.isTranslated())
				.setIsUpdateable(column.isUpdateable())
				.setMandatoryLogic(ValueUtil.validateNull(column.getMandatoryLogic()))
				.setReadOnlyLogic(ValueUtil.validateNull(column.getReadOnlyLogic()))
				.setSequence(field.getSeqNo())
				.setValueMax(ValueUtil.validateNull(column.getValueMax()))
				.setValueMin(ValueUtil.validateNull(column.getValueMin()))
				.setFieldLength(column.getFieldLength())
				.setIsActive(field.isActive())
				.addAllContextColumnNames(
						DictionaryUtil.getContextColumnNames(Optional.ofNullable(field.getDefaultValue()).orElse(Optional.ofNullable(column.getDefaultValue()).orElse("")))
				);
		//	Context Info
		if(field.getAD_ContextInfo_ID() > 0) {
			ContextInfo.Builder contextInfoBuilder = convertContextInfo(context, field.getAD_ContextInfo_ID());
			builder.setContextInfo(contextInfoBuilder.build());
		}
		//	Process
		if(column.getAD_Process_ID() > 0) {
			MProcess process = MProcess.get(context, column.getAD_Process_ID());
			Process.Builder processBuilder = convertProcess(context, process, false);
			builder.setProcess(processBuilder.build());
		}
		//
		if (ReferenceUtil.validateReference(displayTypeId)) {
			//	Reference Value
			int referenceValueId = column.getAD_Reference_Value_ID();
			if(field.getAD_Reference_Value_ID() > 0) {
				referenceValueId = field.getAD_Reference_Value_ID();
			}
			//	Validation Code
			int validationRuleId = column.getAD_Val_Rule_ID();
			if(field.getAD_Val_Rule_ID() > 0) {
				validationRuleId = field.getAD_Val_Rule_ID();
			}

			MLookupInfo info = ReferenceUtil.getReferenceLookupInfo(
				displayTypeId, referenceValueId, column.getColumnName(), validationRuleId
			);
			if (info != null) {
				Reference.Builder referenceBuilder = DictionaryConvertUtil.convertReference(context, info);
				builder.setReference(referenceBuilder.build());
			} else {
				builder.setDisplayType(DisplayType.String);
			}
		}
		
		//	Field Definition
		if(field.getAD_FieldDefinition_ID() > 0) {
			FieldDefinition.Builder fieldDefinitionBuilder = convertFieldDefinition(context, field.getAD_FieldDefinition_ID());
			builder.setFieldDefinition(fieldDefinitionBuilder);
		}
		//	Field Group
		if(field.getAD_FieldGroup_ID() > 0) {
			FieldGroup.Builder fieldGroup = convertFieldGroup(context, field.getAD_FieldGroup_ID());
			builder.setFieldGroup(fieldGroup.build());
		}

		List<DependentField> depenentFieldsList = generateDependentFields(field);
		builder.addAllDependentFields(depenentFieldsList);

		return builder;
	}

	private List<DependentField> generateDependentFields(MField field) {
		List<DependentField> depenentFieldsList = new ArrayList<>();
		if (field == null) {
			return depenentFieldsList;
		}

		int columnId = field.getAD_Column_ID();
		String parentColumnName = MColumn.getColumnName(Env.getCtx(), columnId);

		MTab parentTab = MTab.get(Env.getCtx(), field.getAD_Tab_ID());
		List<MTab> tabsList = ASPUtil.getInstance(Env.getCtx()).getWindowTabs(parentTab.getAD_Window_ID());
		if (tabsList == null) {
			return depenentFieldsList;
		}
		tabsList.stream()
			.filter(currentTab -> {
				// transaltion tab is not rendering on client
				return currentTab.isActive() && !currentTab.isTranslationTab();
			})
			.forEach(tab -> {
				List<MField> fieldsList = ASPUtil.getInstance().getWindowFields(tab.getAD_Tab_ID());
				if (fieldsList == null) {
					return;
				}

				fieldsList.stream()
					.filter(currentField -> {
						if (!currentField.isActive()) {
							return false;
						}
						// Display Logic
						if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentField.getDisplayLogic())) {
							return true;
						}
						// Default Value of Field
						if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentField.getDisplayLogic())) {
							return true;
						}
						// Dynamic Validation
						if (currentField.getAD_Val_Rule_ID() > 0) {
							MValRule validationRule = MValRule.get(Env.getCtx(), currentField.getAD_Val_Rule_ID());
							if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, validationRule.getCode())) {
								return true;
							}
						}

						MColumn currentColumn = MColumn.get(Env.getCtx(), currentField.getAD_Column_ID());
						// Default Value of Column
						if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentColumn.getDefaultValue())) {
							return true;
						}
						// ReadOnly Logic
						if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentColumn.getReadOnlyLogic())) {
							return true;
						}
						// Mandatory Logic
						if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, currentColumn.getMandatoryLogic())) {
							return true;
						}
						// Dynamic Validation
						if (currentColumn.getAD_Val_Rule_ID() > 0) {
							MValRule validationRule = MValRule.get(Env.getCtx(), currentColumn.getAD_Val_Rule_ID());
							if (DictionaryUtil.isUseParentColumnOnContext(parentColumnName, validationRule.getCode())) {
								return true;
							}
						}
						return false;
					})
					.forEach(currentField -> {
						DependentField.Builder builder = DependentField.newBuilder();
						builder.setContainerId(tab.getAD_Tab_ID());
						builder.setContainerUuid(tab.getUUID());
						builder.setContainerName(tab.getName());

						builder.setId(currentField.getAD_Field_ID());
						builder.setUuid(currentField.getUUID());

						String currentColumnName = MColumn.getColumnName(Env.getCtx(), currentField.getAD_Column_ID());
						builder.setColumnName(currentColumnName);

						depenentFieldsList.add(builder.build());
					});
			});

		return depenentFieldsList;
	}


	/**
	 * Convert Field Definition to builder
	 * @param fieldDefinitionId
	 * @return
	 */
	private FieldDefinition.Builder convertFieldDefinition(Properties context, int fieldDefinitionId) {
		FieldDefinition.Builder builder = null;
		if(fieldDefinitionId > 0) {
			MADFieldDefinition fieldDefinition  = new MADFieldDefinition(context, fieldDefinitionId, null);
			//	Reference
			builder = FieldDefinition.newBuilder()
					.setId(fieldDefinition.getAD_FieldDefinition_ID())
					.setUuid(ValueUtil.validateNull(fieldDefinition.getUUID()))
					.setValue(ValueUtil.validateNull(fieldDefinition.getValue()))
					.setName(ValueUtil.validateNull(fieldDefinition.getName()));
			//	Get conditions
			for(MADFieldCondition condition : fieldDefinition.getConditions()) {
				if(!condition.isActive()) {
					continue;
				}
				FieldCondition.Builder fieldConditionBuilder = FieldCondition.newBuilder()
						.setId(fieldDefinition.getAD_FieldDefinition_ID())
						.setUuid(ValueUtil.validateNull(condition.getUUID()))
						.setCondition(ValueUtil.validateNull(condition.getCondition()))
						.setStylesheet(ValueUtil.validateNull(condition.getStylesheet()))
						.setIsActive(fieldDefinition.isActive());
				//	Add to parent
				builder.addConditions(fieldConditionBuilder);
			}
		}
		return builder;
	}
	
	/**
	 * Convert Field Group to builder
	 * @param fieldGroupId
	 * @return
	 */
	private FieldGroup.Builder convertFieldGroup(Properties context, int fieldGroupId) {
		FieldGroup.Builder builder = FieldGroup.newBuilder();
		if(fieldGroupId > 0) {
			X_AD_FieldGroup fieldGroup  = new X_AD_FieldGroup(context, fieldGroupId, null);
			//	Get translation
			String name = null;
			String language = Env.getAD_Language(context);
			if(!Util.isEmpty(language)) {
				name = fieldGroup.get_Translation(I_AD_FieldGroup.COLUMNNAME_Name, language);
			}
			//	Validate for default
			if(Util.isEmpty(name)) {
				name = fieldGroup.getName();
			}
			//	Field Group
			builder = FieldGroup.newBuilder()
					.setId(fieldGroup.getAD_FieldGroup_ID())
					.setUuid(ValueUtil.validateNull(fieldGroup.getUUID()))
					.setName(ValueUtil.validateNull(name))
					.setFieldGroupType(fieldGroup.getFieldGroupType())
					.setIsActive(fieldGroup.isActive());
		}
		return builder;
	}
	
	/**
	 * Convert reference from a request
	 * @param context
	 * @param request
	 * @return
	 */
	private Reference.Builder convertReference(Properties context, ReferenceRequest request) {
		Reference.Builder builder = Reference.newBuilder();
		MLookupInfo info = null;
		if(!Util.isEmpty(request.getReferenceUuid())) {
			X_AD_Reference reference = new Query(context, I_AD_Reference.Table_Name, I_AD_Reference.COLUMNNAME_UUID + " = ?", null)
					.setParameters(request.getReferenceUuid())
					.first();
			if(reference.getValidationType().equals(X_AD_Reference.VALIDATIONTYPE_TableValidation)) {
				info = MLookupFactory.getLookupInfo(context, 0, 0, DisplayType.Search, Language.getLanguage(Env.getAD_Language(context)), null, reference.getAD_Reference_ID(), false, null, false);
			} else if(reference.getValidationType().equals(X_AD_Reference.VALIDATIONTYPE_ListValidation)) {
				info = MLookupFactory.getLookup_List(Language.getLanguage(Env.getAD_Language(context)), reference.getAD_Reference_ID());
			}
		} else if(!Util.isEmpty(request.getColumnName())) {
			info = MLookupFactory.getLookupInfo(context, 0, 0, DisplayType.TableDir, Language.getLanguage(Env.getAD_Language(context)), request.getColumnName(), 0, false, null, false);
		}

		if (info != null) {
			builder = DictionaryConvertUtil.convertReference(context, info);
		}

		return builder;
	}
	
	/**
	 * Convert Validation rule
	 * @param context
	 * @param request
	 * @return
	 */
	private ValidationRule.Builder convertValidationRule(Properties context, EntityRequest request) {
		MValRule validationRule = null;
		if(request.getId() > 0) {
			validationRule = MValRule.get(context, request.getId());
		} else {
			validationRule = new Query(context, I_AD_Val_Rule.Table_Name, I_AD_Val_Rule.COLUMNNAME_UUID + " = ?", null)
					.setParameters(request.getUuid())
					.first();
		}
		if (validationRule == null) {
			return ValidationRule.newBuilder();
		}
		//	
		return ValidationRule.newBuilder()
				.setId(validationRule.getAD_Val_Rule_ID())
				.setUuid(ValueUtil.validateNull(validationRule.getUUID()))
				.setName(ValueUtil.validateNull(validationRule.getName()))
				.setDescription(ValueUtil.validateNull(validationRule.getDescription()))
				.setValidationCode(ValueUtil.validateNull(validationRule.getCode()))
				.setType(ValueUtil.validateNull(validationRule.getType()))
				;
	}

	/**
	 * Get reference from column name and table
	 * @param tableId
	 * @param columnName
	 * @return
	 */
	public static int getReferenceId(int tableId, String columnName) {
		MColumn column = MTable.get(Env.getCtx(), tableId).getColumn(columnName);
		if(column == null) {
			return -1;
		}
		return column.getAD_Reference_ID();
	}



	@Override
	public void listIdentifiersFields(ListFieldsRequest request, StreamObserver<ListFieldsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListFieldsResponse.Builder fielsListBuilder = getIdentifierFields(Env.getCtx(), request);
			responseObserver.onNext(fielsListBuilder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException());
		}
	}
	
	private ListFieldsResponse.Builder getIdentifierFields(Properties context, ListFieldsRequest request) {
		MTable table = null;
		int tableId = request.getTableId();
		if (tableId > 0) {
			table = MTable.get(context, tableId);
		} else if(!Util.isEmpty(request.getTableUuid(), true)) {
			table = new Query(context, MTable.Table_Name, MTable.COLUMNNAME_UUID + " = ?", null)
				.setParameters(request.getTableUuid())
				.setOnlyActiveRecords(true)
				.first();
		} else if (!Util.isEmpty(request.getTableName(), true)) {
			table = MTable.get(context, request.getTableName());
		} 
		if (table == null) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}
		
		ListFieldsResponse.Builder fieldsListBuilder = ListFieldsResponse.newBuilder();
		
		final String sql = "SELECT c.AD_Column_ID"
			// + ", c.ColumnName, t.AD_Table_ID, t.TableName, c.ColumnSql "
			+ " FROM AD_Table AS t "
			+ "	INNER JOIN AD_Column c ON (t.AD_Table_ID=c.AD_Table_ID) "
			+ "	WHERE c.AD_Reference_ID = 10 "
			+ " AND t.AD_Table_ID = ? "
			//	Displayed in Window
			+ "	AND EXISTS (SELECT * FROM AD_Field AS f "
			+ "	WHERE f.AD_Column_ID=c.AD_Column_ID "
			+ " AND f.IsDisplayed='Y' AND f.IsEncrypted='N' AND f.ObscureType IS NULL) "
			+ "	ORDER BY c.IsIdentifier DESC, c.SeqNo ";
		/*
		DB.runResultSet(null, sql, List.of(table.getAD_Table_ID()), resultSet -> {
			while(resultSet.next()) {
				MColumn column = MColumn.get(context, resultSet.getInt(MColumn.COLUMNNAME_AD_Column_ID));
				if (column != null) {
					Field.Builder fieldBuilder = convertField(context, column);
					fieldsListBuilder.addFields(fieldBuilder.build());
				}
			}
		}).onFailure(throwable -> {
			log.log(Level.SEVERE, "loadPreferences", throwable);
		});
		*/

		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		try {
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, table.getAD_Table_ID());
			resultSet = pstmt.executeQuery();

			while (resultSet.next()) {
				//	Only 4 Query Columns
				if (fieldsListBuilder.getFieldsList().size() >= 4) {
					break;
				}
				MColumn column = MColumn.get(context, resultSet.getInt(MColumn.COLUMNNAME_AD_Column_ID));
				if (column != null) {
					Field.Builder fieldBuilder = convertField(context, column);
					fieldsListBuilder.addFields(fieldBuilder.build());
				}
			}
			resultSet.close();
			pstmt.close();
		}
		catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}
		finally {
			DB.close(resultSet, pstmt);
			resultSet = null;
			pstmt = null;
		}

		//	empty general info
		// if (fieldsListBuilder.getFieldsList().size() == 0) {
		// }
		
		return fieldsListBuilder;
	}

	@Override
	public void listTableSearchFields(ListFieldsRequest request, StreamObserver<ListFieldsResponse> responseObserver) {
		try {
			if(request == null) {
				throw new AdempiereException("Object Request Null");
			}
			ListFieldsResponse.Builder fielsListBuilder = getTableSearchFields(Env.getCtx(), request);
			responseObserver.onNext(fielsListBuilder.build());
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			responseObserver.onError(Status.INTERNAL
				.withDescription(e.getLocalizedMessage())
				.withCause(e)
				.asRuntimeException());
		}
	}
	
	private ListFieldsResponse.Builder getTableSearchFields(Properties context, ListFieldsRequest request) {
		MTable table = null;
		int tableId = request.getTableId();
		if (tableId > 0) {
			table = MTable.get(context, tableId);
		} else if(!Util.isEmpty(request.getTableUuid(), true)) {
			table = new Query(context, MTable.Table_Name, MTable.COLUMNNAME_UUID + " = ?", null)
				.setParameters(request.getTableUuid())
				.setOnlyActiveRecords(true)
				.first();
		} else if (!Util.isEmpty(request.getTableName(), true)) {
			table = MTable.get(context, request.getTableName());
		} 
		if (table == null || table.getAD_Table_ID() < 1) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}
		
		ListFieldsResponse.Builder fieldsListBuilder = ListFieldsResponse.newBuilder();
		
		final String sql = "SELECT f.AD_Field_ID "
			// + ", c.ColumnName, c.AD_Reference_ID, c.IsKey, f.IsDisplayed, c.AD_Reference_Value_ID, c.ColumnSql "
			+ " FROM AD_Column c "
			+ " INNER JOIN AD_Table t ON (c.AD_Table_ID=t.AD_Table_ID)"
			+ " INNER JOIN AD_Tab tab ON (t.AD_Window_ID=tab.AD_Window_ID)"
			+ " INNER JOIN AD_Field f ON (tab.AD_Tab_ID=f.AD_Tab_ID AND f.AD_Column_ID=c.AD_Column_ID) "
			+ " WHERE t.AD_Table_ID=? "
			+ " AND (c.IsKey='Y' OR "
				// + " (f.IsDisplayed='Y' AND f.IsEncrypted='N' AND f.ObscureType IS NULL)) "
				+ " (f.IsEncrypted='N' AND f.ObscureType IS NULL)) "
			+ "ORDER BY c.IsKey DESC, f.SeqNo";
		/*
		DB.runResultSet(null, sql, List.of(table.getAD_Table_ID()), resultSet -> {
			while(resultSet.next()) {
				MField field = new MField(context, resultSet.getInt(MField.COLUMNNAME_AD_Field_ID), null);
				if (field != null) {
					Field.Builder fieldBuilder = convertField(context, field, true);
					fieldsListBuilder.addFields(fieldBuilder.build());
				}
			}
		}).onFailure(throwable -> {
			log.log(Level.SEVERE, "loadPreferences", throwable);
		});
		*/

		PreparedStatement pstmt = null;
		ResultSet resultSet = null;
		try {
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, table.getAD_Table_ID());
			resultSet = pstmt.executeQuery();
			int recordCount = 0;
			while (resultSet.next()) {
				MField field = new MField(context, resultSet.getInt(MField.COLUMNNAME_AD_Field_ID), null);
				if (field != null) {
					Field.Builder fieldBuilder = convertField(context, field, true);
					fieldsListBuilder.addFields(fieldBuilder.build());
				}
				recordCount++;
			}
			fieldsListBuilder.setRecordCount(recordCount);
			resultSet.close();
			pstmt.close();
		}
		catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}
		finally {
			DB.close(resultSet, pstmt);
			resultSet = null;
			pstmt = null;
		}
		
		return fieldsListBuilder;
	}
	
}
