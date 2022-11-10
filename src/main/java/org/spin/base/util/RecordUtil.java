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
package org.spin.base.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pipo.IDFinder;
import org.adempiere.core.domains.models.I_AD_Element;
import org.compiere.model.MClientInfo;
import org.compiere.model.MColumn;
import org.compiere.model.MConversionRate;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;
import org.compiere.util.Util;
import org.spin.backend.grpc.common.Entity;
import org.spin.backend.grpc.common.ListEntitiesResponse;
import org.spin.backend.grpc.common.Value;
import org.spin.model.MADAttachmentReference;
import org.spin.util.AttachmentUtil;

/**
 * Class for handle records utils values
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 */
public class RecordUtil {
	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(RecordUtil.class);
	
	/**	Page Size	*/
	public static final int PAGE_SIZE = 15;
	
	/**
	 * Get Page Size from client, else from default
	 * @param pageSize
	 * @return
	 */
	public static int getPageSize(int pageSize) {
		return pageSize > 0? pageSize: PAGE_SIZE;
	}
	
	/**
	 * Get Page Number
	 * @param sessionUuid
	 * @param pageToken
	 * @return
	 */
	public static int getPageNumber(String sessionUuid, String pageToken) {
		int page = 1;
		String pagePrefix = getPagePrefix(sessionUuid);
		if(!Util.isEmpty(pageToken)) {
			if(pageToken.startsWith(pagePrefix)) {
				try {
					page = Integer.parseInt(pageToken.replace(pagePrefix, ""));
					if (page < 1) {
						page = 1;
					}
				} catch (Exception e) {
					//	
				}
			}
		}
		//	
		return page;
	}
	
	/**
	 * Get Page Prefix
	 * @param sessionUuid
	 * @return
	 */
	public static String getPagePrefix(String sessionUuid) {
		return sessionUuid + "-";
	}
	
	/**
	 * Validate if can have a next page token
	 * @param count
	 * @param offset
	 * @param limit
	 * @return
	 * @return boolean
	 */
	public static boolean isValidNextPageToken(int count, int offset, int limit) {
		return count > (offset + limit) && count > limit;
	}

	/**
	 * get Entity from Table ID and (Record UUID / Record ID)
	 * @param context
	 * @param tableId
	 * @param uuid
	 * @param recordId
	 * @return
	 */
	public static PO getEntity(Properties context, int tableId, String uuid, int recordId, String transactionName) {
		if (tableId <= 0) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}

		String tableName = MTable.getTableName(context, tableId);
		return getEntity(context, tableName, uuid, recordId, transactionName);
	}

	/**
	 * get Entity from Table and (UUID / Record ID)
	 * @param context
	 * @param tableName
	 * @param uuid
	 * @param recordId
	 * @return
	 */
	public static PO getEntity(Properties context, String tableName, String uuid, int recordId, String transactionName) {
		//	Validate ID
		if(recordId == 0
				&& Util.isEmpty(uuid)) {
			throw new AdempiereException("@Record_ID@ @NotFound@");
		}
		
		if(Util.isEmpty(tableName)) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}
		StringBuffer whereClause = new StringBuffer();
		List<Object> params = new ArrayList<>();
		if(!Util.isEmpty(uuid)) {
			whereClause.append(I_AD_Element.COLUMNNAME_UUID + " = ?");
			params.add(uuid);
		} else if(recordId > 0) {
			whereClause.append(tableName + "_ID = ?");
			params.add(recordId);
		} else {
			throw new AdempiereException("@Record_ID@ @NotFound@");
		}
		//	Default
		return new Query(context, tableName, whereClause.toString(), transactionName)
				.setParameters(params)
				.first();
	}
	
	/**
	 * get Entity from Table and where clause
	 * @param context
	 * @param tableName
	 * @param whereClause
	 * @param parameters
	 * @return
	 */
	public static PO getEntity(Properties context, String tableName, String whereClause, List<Object> parameters, String transactionName) {
		//	Validate ID
		if(Util.isEmpty(whereClause)) {
			throw new AdempiereException("@WhereClause@ @NotFound@");
		}
		
		if(Util.isEmpty(tableName)) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}
		//	Default
		return new Query(context, tableName, whereClause, transactionName)
				.setParameters(parameters)
				.first();
	}
	
	/**
	 * Get ID for record from table name and uuid
	 * @param tableName
	 * @param uuid
	 * @return
	 */
	public static int getIdFromUuid(String tableName, String uuid, String transactionName) {
		if(Util.isEmpty(tableName) || Util.isEmpty(uuid)) {
			return -1;
		}
		//	Get
		return IDFinder.getIdFromUUID(Env.getCtx(), tableName, uuid, Env.getAD_Client_ID(Env.getCtx()), transactionName);
	}
	
	/**
	 * Get UUID from record id
	 * @param tableName
	 * @param id
	 * @return
	 */
	public static String getUuidFromId(String tableName, int id) {
		if(Util.isEmpty(tableName) || id <= 0) {
			return null;
		}
		//	Get
		return IDFinder.getUUIDFromId(tableName, id, Env.getAD_Client_ID(Env.getCtx()), null);
	}
	
	/**
	 * Get UUID from record id
	 * @param tableName
	 * @param id
	 * @return
	 */
	public static String getUuidFromId(String tableName, int id, String transactionName) {
		if(Util.isEmpty(tableName) || id <= 0) {
			return null;
		}
		//	Get
		return IDFinder.getUUIDFromId(tableName, id, Env.getAD_Client_ID(Env.getCtx()), transactionName);
	}
	
	/**
	 * Get resource UUID from image id
	 * @param imageId
	 * @return
	 */
	public static String getResourceUuidFromImageId(int imageId) {
		MADAttachmentReference reference = getResourceFromImageId(imageId);
		if(reference == null) {
			return null;
		}
		//	Return uuid
		return reference.getUUID();
	}
	
	/**
	 * Get Attachment reference from image ID
	 * @param imageId
	 * @return
	 */
	public static MADAttachmentReference getResourceFromImageId(int imageId) {
		if(!AttachmentUtil.getInstance().isValidForClient(Env.getAD_Client_ID(Env.getCtx()))) {
			return null;
		}
		//	
		return MADAttachmentReference.getByImageId(Env.getCtx(), MClientInfo.get(Env.getCtx(), Env.getAD_Client_ID(Env.getCtx())).getFileHandler_ID(), imageId, null);
	}
	
	/**
	 * Get conversion Rate from ValidFrom, Currency From, Currency To and Conversion Type
	 * @param request
	 * @return
	 */
	public static MConversionRate getConversionRate(int organizationId, int conversionTypeId, int currencyFromId, int currencyToId, Timestamp conversionDate) {
		if(conversionTypeId <= 0
				|| currencyFromId <= 0
				|| currencyToId <= 0) {
			return null;
		}
		//	Get values
		conversionDate = TimeUtil.getDay(Optional.ofNullable(conversionDate).orElse(new Timestamp(System.currentTimeMillis())));
		if(organizationId < 0) {
			organizationId = 0;
		}
		int conversionRateId = MConversionRate.getConversionRateId(currencyFromId, 
				currencyToId, 
				conversionDate, 
				conversionTypeId, 
				Env.getAD_Client_ID(Env.getCtx()), 
				organizationId);
		if(conversionRateId > 0) {
			return MConversionRate.get(Env.getCtx(), conversionRateId);
		}
		//	
		return null;
	}

	/**
	 * Add where clause with search value and return the new complete SQL
	 * @param sql
	 * @param tableName
	 * @param searchValue
	 * @param parameters
	 * @return
	 */
	public static String addSearchValueAndGet(String sql, String tableName, String searchValue, List<Object> parameters) {
		return addSearchValueAndGet(sql, tableName, null, searchValue, parameters);
	}
	
	/**
	 * Add where clause with search value and return the new complete SQL
	 * @param sql
	 * @param tableName
	 * @param searchValue
	 * @param parameters
	 * @return
	 */
	public static String addSearchValueAndGet(String sql, String tableName, String tableAlias, String searchValue, List<Object> parameters) {
		if(Util.isEmpty(searchValue)) {
			return sql;
		}
		MTable table = MTable.get(Env.getCtx(), tableName);
		if(table == null) {
			return sql;
		}
		StringBuffer whereClause = new StringBuffer();
		table.getColumnsAsList().stream()
			.filter(column -> {
				return (column.isIdentifier() || column.isSelectionColumn())
					&& Util.isEmpty(column.getColumnSQL()) && DisplayType.isText(column.getAD_Reference_ID());
			})
			.forEach(column -> {
				if(whereClause.length() > 0) {
					whereClause.append(" OR ");
				}
				whereClause.append("UPPER(")
					.append(tableName).append(".")
					.append(column.getColumnName())
					.append(")")
					.append(" LIKE ")
					.append("'%'|| UPPER(?) || '%'");
				parameters.add(searchValue);
			});
		//	Order by
		//	Validate and return
		if(whereClause.length() > 0) {
			String patternAlias = "";
			if (!Util.isEmpty(tableAlias, true)) {
				patternAlias = "\\s+(" + tableAlias + "){0,1}";
			}
			Matcher matcher = Pattern.compile(
					"\\s+(FROM)\\s+(" + tableName + ")" + patternAlias + "\\s+(WHERE)",
					Pattern.CASE_INSENSITIVE | Pattern.DOTALL
				)
				.matcher(sql);
			Matcher matcherJoin = Pattern.compile("JOIN(\\w|\\s)*(\\((\\w|\\.|\\s|=|\\')*\\))(\\s*)(WHERE)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
			Matcher matcherOrderBy = Pattern.compile("\\s+(ORDER BY)\\s+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
			int positionFrom = -1;
			if(matcherOrderBy.find()) {
				positionFrom = matcherOrderBy.start();
			}
			String conditional = " WHERE ";
			if (matcher.find() || matcherJoin.find()) {
				conditional = " AND ";
			}
			if(positionFrom > 0) {
				sql = sql.substring(0, positionFrom) + conditional + "(" + whereClause + ")" + sql.substring(positionFrom);
			} else {
				sql = sql + conditional + "(" + whereClause + ")";
			}
		}
		return sql;
	}

	/**
	 * Count records
	 * @param sql
	 * @param tableName
	 * @param parameters
	 * @return
	 */
	public static int countRecords(String sql, String tableName, List<Object> parameters) {
		return countRecords(sql, tableName, null, parameters);
	}
	
	/**
	 * Count records
	 * @param sql
	 * @param tableName
	 * @param tableNameAlias
	 * @param parameters
	 * @return
	 */
	public static int countRecords(String sql, String tableName, String tableNameAlias, List<Object> parameters) {
		String tableWithAliases = tableName;
		if (!Util.isEmpty(tableNameAlias)) {
			// tableName tableAlias | tableName AS tableAlias | tableName
			tableWithAliases = tableName + " " + tableNameAlias + "|" + tableName + " AS " + tableNameAlias; // + "|" + tableName;
		}
		Matcher matcher = Pattern.compile(
				"\\s+(FROM)\\s+(" + tableWithAliases + ")(\\s+)",
				Pattern.CASE_INSENSITIVE | Pattern.DOTALL
			)
			.matcher(sql);
		int positionFrom = -1;
		if(matcher.find()) {
			positionFrom = matcher.start();
		} else {
			return 0;
		}
		String queryCount = "SELECT COUNT(*) " + sql.substring(positionFrom, sql.length());
		matcher = Pattern.compile("\\s+(ORDER BY)\\s+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(queryCount);
		if(matcher.find()) {
			positionFrom = matcher.start();
			queryCount = queryCount.substring(0, positionFrom);
		}
		if(parameters == null
				|| parameters.size() == 0) {
			return DB.getSQLValueEx(null, queryCount);
		}
		return DB.getSQLValueEx(null, queryCount, parameters);
	}
	
	/**
	 * Get Query with limit
	 * @param query
	 * @param limit
	 * @param offset
	 * @return
	 */
	public static String getQueryWithLimit(String query, int limit, int offset) {
		Matcher matcher = Pattern.compile("\\s+(ORDER BY)\\s+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(query);
		int positionFrom = -1;
		if(matcher.find()) {
			positionFrom = matcher.start();
			query = query.substring(0, positionFrom) + " AND ROWNUM >= " + offset + " AND ROWNUM <= " + limit + " " + query.substring(positionFrom);
		} else {
			query = query + " AND ROWNUM >= " + offset + " AND ROWNUM <= " + limit;
		}
		return query;
	}
	
	/**
	 * Get Date
	 * @return
	 */
	public static Timestamp getDate() {
		return TimeUtil.getDay(System.currentTimeMillis());
	}

	/**
	 * Convert Entities List
	 * @param table
	 * @param sql
	 * @param params
	 * @return
	 */
	public static ListEntitiesResponse.Builder convertListEntitiesResult(MTable table, String sql, List<Object> params) {
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		ListEntitiesResponse.Builder builder = ListEntitiesResponse.newBuilder();
		long recordCount = 0;
		try {
			LinkedHashMap<String, MColumn> columnsMap = new LinkedHashMap<>();
			//	Add field to map
			for(MColumn column: table.getColumnsAsList()) {
				columnsMap.put(column.getColumnName().toUpperCase(), column);
			}
			//	SELECT Key, Value, Name FROM ...
			pstmt = DB.prepareStatement(sql, null);
			AtomicInteger parameterIndex = new AtomicInteger(1);
			for(Object value : params) {
				ValueUtil.setParameterFromObject(pstmt, value, parameterIndex.getAndIncrement());
			} 
			//	Get from Query
			rs = pstmt.executeQuery();
			while(rs.next()) {
				Entity.Builder valueObjectBuilder = Entity.newBuilder();
				valueObjectBuilder.setTableName(table.getTableName());
				ResultSetMetaData metaData = rs.getMetaData();
				for (int index = 1; index <= metaData.getColumnCount(); index++) {
					try {
						String columnName = metaData.getColumnName (index);
						if (columnName.toUpperCase().equals("UUID")) {
							valueObjectBuilder.setUuid(rs.getString(index));
						}
						MColumn field = columnsMap.get(columnName.toUpperCase());
						Value.Builder valueBuilder = Value.newBuilder();
						//	Display Columns
						if(field == null) {
							String value = rs.getString(index);
							if(!Util.isEmpty(value)) {
								valueBuilder = ValueUtil.getValueFromString(value);
							}
							valueObjectBuilder.putValues(columnName, valueBuilder.build());
							continue;
						}
						if (field.isKey()) {
							valueObjectBuilder.setId(rs.getInt(index));
						}
						//	From field
						String fieldColumnName = field.getColumnName();
						valueBuilder = ValueUtil.getValueFromReference(rs.getObject(index), field.getAD_Reference_ID());
						if(!valueBuilder.getValueType().equals(Value.ValueType.UNRECOGNIZED)) {
							valueObjectBuilder.putValues(fieldColumnName, valueBuilder.build());
						}
					} catch (Exception e) {
						log.severe(e.getLocalizedMessage());
					}
				}
				//	
				builder.addRecords(valueObjectBuilder.build());
				recordCount++;
			}
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
		} finally {
			DB.close(rs, pstmt);
		}
		//	Set record counts
		if (builder.getRecordCount() <= 0) {
			builder.setRecordCount(recordCount);
		}
		//	Return
		return builder;
	}
}
