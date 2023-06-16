/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * This program is free software; you can redistribute it and/or modify it           *
 * under the terms version 2 or later of the GNU General Public License as published *
 * by the Free Software Foundation. This program is distributed in the hope          *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied        *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                  *
 * See the GNU General Public License for more details.                              *
 * You should have received a copy of the GNU General Public License along           *
 * with this program; if not, write to the Free Software Foundation, Inc.,           *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                            *
 * For the text or an alternative of this public license, you may reach us           *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, S.A. All Rights Reserved. *
 * Contributor(s): Yamel Senih www.erpya.com                                         *
 *************************************************************************************/
package org.spin.base.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pipo.IDFinder;
import org.adempiere.core.domains.models.I_AD_Element;
import org.adempiere.core.domains.models.X_AD_Table;
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
	public static final int PAGE_SIZE = 50;

	/** Table Allows Records with Zero Identifier */
	public static List<String> ALLOW_ZERO_ID = Arrays.asList(
		X_AD_Table.ACCESSLEVEL_All,
		X_AD_Table.ACCESSLEVEL_SystemPlusClient,
		X_AD_Table.ACCESSLEVEL_ClientPlusOrganization
	);

	/**
	 * Get Page Size from client, else from default
	 * @param pageSize
	 * @return
	 */
	public static int getPageSize(int pageSize) {
		return pageSize > 0 ? pageSize : PAGE_SIZE;
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
			} else {
				try {
					page = Integer.parseInt(pageToken);
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
		if (Util.isEmpty(tableName, true)) {
			throw new AdempiereException("@FillMandatory@ @AD_Table_ID@");
		}
		MTable table = MTable.get(context, tableName);
		if (table == null || table.getAD_Table_ID() <= 0) {
			throw new AdempiereException("@AD_Table_ID@ @NotFound@");
		}
		//	Validate ID
		if (Util.isEmpty(uuid, true) && !isValidId(recordId, table.getAccessLevel())) {
			throw new AdempiereException("@FillMandatory@ @Record_ID@ / @UUID@");
		}

		StringBuffer whereClause = new StringBuffer();
		List<Object> params = new ArrayList<>();
		if (!Util.isEmpty(uuid, true)) {
			whereClause.append(I_AD_Element.COLUMNNAME_UUID + " = ?");
			params.add(uuid);
		} else if (isValidId(recordId, table.getAccessLevel())) {
			whereClause.append(tableName + "_ID = ?");
			params.add(recordId);
		} else {
			throw new AdempiereException("@Record_ID@ / @UUID@ @NotFound@");
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
		if (Util.isEmpty(tableName, true) || Util.isEmpty(uuid, true)) {
			return -1;
		}
		//	Get
		return IDFinder.getIdFromUUID(Env.getCtx(), tableName, uuid, Env.getAD_Client_ID(Env.getCtx()), transactionName);
	}


	/**
	 * Evaluate if is valid identifier
	 * @param id
	 * @param accesLevel
	 * @return
	 */
	public static boolean isValidId(int id, String accesLevel) {
		if (id < 0) {
			return false;
		}

		if (id == 0 && !ALLOW_ZERO_ID.contains(accesLevel)) {
			return false;
		}

		return true;
	}


	/**
	 * Get UUID from record id
	 * @param tableName
	 * @param id
	 * @return
	 */
	public static String getUuidFromId(String tableName, int id) {
		//	Get
		return getUuidFromId(tableName, id, null);
	}

	/**
	 * Get UUID from record id
	 * @param tableName
	 * @param id
	 * @param transactionName
	 * @return
	 */
	public static String getUuidFromId(String tableName, int id, String transactionName) {
		if (Util.isEmpty(tableName, true)) {
			return null;
		}
		MTable table = MTable.get(Env.getCtx(), tableName);
		if (table == null || table.getAD_Table_ID() <= 0) {
			return null;
		}

		//	Validate ID
		if (!isValidId(id, table.getAccessLevel())) {
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
		return addSearchValueAndGet(sql, tableName, null, searchValue, true, parameters);
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
		return addSearchValueAndGet(sql, tableName, tableAlias, searchValue, true, parameters);
	}

	/**
	 * Add where clause with search value and return the new complete SQL
	 * @param sql
	 * @param tableName
	 * @param searchValue
	 * @param isTranslated
	 * @param parameters
	 * @return
	 */
	public static String addSearchValueAndGet(String sql, String tableName, String searchValue, boolean isTranslated, List<Object> parameters) {
		return addSearchValueAndGet(sql, tableName, null, searchValue, isTranslated, parameters);
	}

	/**
	 * Add where clause with search value and return the new complete SQL
	 * @param sql
	 * @param tableName
	 * @param tableAlias
	 * @param searchValue
	 * @param isTranslated
	 * @param parameters
	 * @return
	 */
	public static String addSearchValueAndGet(String sql, String table_name, String table_alias, String searchValue, boolean isTranslated, List<Object> parameters) {
		if(Util.isEmpty(searchValue, true)) {
			return sql;
		}
		MTable table = MTable.get(Env.getCtx(), table_name);
		if(table == null || table.getAD_Table_ID() <= 0) {
			return sql;
		}

		String lang = Env.getAD_Language(Env.getCtx());
		// search on trl table
		final MTable tableTranslation = org.compiere.util.Language.isBaseLanguage(lang) || !isTranslated ?
			null : MTable.get(Env.getCtx(), table_name + DictionaryUtil.TRANSLATION_SUFFIX);
		final String tableAlias = Util.isEmpty(table_alias, true) ? table.getTableName() : table_alias;

		StringBuffer where = new StringBuffer();
		List<MColumn> selectionColums = table.getColumnsAsList().stream()
			.filter(column -> {
				return (column.isIdentifier() || column.isSelectionColumn())
					&& Util.isEmpty(column.getColumnSQL(), true) && DisplayType.isText(column.getAD_Reference_ID());
			})
			.collect(Collectors.toList());

		selectionColums.stream()
			.filter(column -> {
				// "Value" is not translated for example
				return (tableTranslation == null || (tableTranslation != null && !column.isTranslated()));
			})
			.forEach(column -> {
				if(where.length() > 0) {
				    where.append(" OR ");
				}
				where.append("UPPER(")
					.append(tableAlias).append(".")
					.append(column.getColumnName())
					.append(")")
					.append(" LIKE ")
					.append("'%'|| UPPER(?) || '%'");
				parameters.add(searchValue);
			});

		// Add language clause
		StringBuffer whereClause = where;
		String joinTranslation = "";
		if (tableTranslation != null) {
			StringBuffer whereTranslation = new StringBuffer();
			
			selectionColums.stream()
				.filter(column -> {
					// Name is translated for example
					return tableTranslation != null && column.isTranslated();
				})
				.forEach(column -> {
					if(whereTranslation.length() > 0) {
						whereTranslation.append(" OR ");
					}
					whereTranslation.append("UPPER(")
						.append("NVL(")
						.append(tableTranslation.getTableName()).append(".")
						.append(column.getColumnName())
						.append(", ")
						.append(tableAlias).append(".")
						.append(column.getColumnName())
						.append("))")
						.append(" LIKE ")
						.append("'%'|| UPPER(?) || '%'");
					parameters.add(searchValue);
				});

			if (!Util.isEmpty(whereTranslation.toString(), true)) {
				if (!Util.isEmpty(where.toString(), true)) {
					whereClause.append(" OR ");
				}
				whereClause
					.append("((")
					.append(whereTranslation)
					.append(") AND ")
					.append(tableTranslation.getTableName() + ".AD_Language = ? ")
					// .append(" AND ")
					// .append(tableTranslation.getTableName() + ".IsTranslated = 'Y' ")
					.append(")")
				;
				parameters.add(lang);
	
				String keyColumn = table.getTableName() + "_ID";
				String translationTableName = tableTranslation.getTableName();
				// only table, tableName tableName, tableName AS tableName
				String tableWithAliases = getPatternTableName(translationTableName, null);

				String joinPattern = "JOIN(\\s+)" 
					+ "(" + tableWithAliases + ")"
					+ "(\\s+ON(\\s*\\(){0,1})\\s*"
					// base_table.table_id = translation_table.table_id
					+ "((\\w+." + keyColumn + "\\s*=\\s*" + translationTableName + "." + keyColumn + ")"
					// translation_table.table_id = base_table.table_id
					+ "|(" + translationTableName + "." + keyColumn + "\\s*=\\s*\\w+." + keyColumn +"))"
					;
				Matcher matcherJoinTranslated = Pattern.compile(
					joinPattern,
					Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE
				).matcher(sql);
				// Add tranlated join
				if (!matcherJoinTranslated.find()) {
					joinTranslation = " LEFT JOIN " + translationTableName + " AS " + translationTableName
						+ " ON " + translationTableName + "." + keyColumn + " = " + tableAlias + "." + keyColumn + " "
					;
				}
			}
		}
		
		//	Validate and return
		if(whereClause.length() > 0) {
			//	Order by
			Matcher matcherOrderBy = Pattern.compile(
				"\\s+(ORDER BY)\\s+",
				Pattern.CASE_INSENSITIVE | Pattern.DOTALL
			).matcher(sql);
			int positionOrderBy = -1;
			String orderByClause = "";
			String queryWithoutOrderBy = sql;
			if (matcherOrderBy.find()) {
				positionOrderBy = matcherOrderBy.start();
				orderByClause = sql.substring(positionOrderBy);
				// remove order By
				queryWithoutOrderBy = sql.substring(0, positionOrderBy);
			}

			// tableName tableName, tableName AS tableName
			String patternAlias = getPatternTableName(table.getTableName(), tableAlias);

			String fromWherePattern = "\\s+(FROM)\\s+(" + patternAlias + ")" + "\\s+(WHERE)";
			Matcher matcher = Pattern.compile(
				fromWherePattern,
				Pattern.CASE_INSENSITIVE | Pattern.DOTALL
			)
			.matcher(queryWithoutOrderBy);
			Matcher matcherJoin = Pattern.compile(
				"JOIN(\\w|\\s)*(\\((\\w|\\.|\\s|=|\\')*\\))(\\s*)(WHERE)",
				Pattern.CASE_INSENSITIVE | Pattern.DOTALL
			).matcher(queryWithoutOrderBy);

			String conditional = " WHERE ";
			List<MatchResult> fromWhereParts = matcher.results()
					.collect(Collectors.toList());
			List<MatchResult> joinWhereParts = matcherJoin.results()
					.collect(Collectors.toList());
			
			String onlyQuery = queryWithoutOrderBy;
			if (joinWhereParts != null && joinWhereParts.size() > 0) {
				MatchResult lastJoinWhere = joinWhereParts.get(joinWhereParts.size() - 1);
				onlyQuery = queryWithoutOrderBy.substring(0, lastJoinWhere.end());

				int lastWhere = onlyQuery.lastIndexOf("WHERE");
				onlyQuery = onlyQuery.substring(0, lastWhere);

				conditional = queryWithoutOrderBy.substring(lastWhere, queryWithoutOrderBy.length()) + " AND ";
			} else if (fromWhereParts != null && fromWhereParts.size() > 0) {
				MatchResult lastJoinWhere = fromWhereParts.get(fromWhereParts.size() - 1);
				onlyQuery = queryWithoutOrderBy.substring(0, lastJoinWhere.end());

				int lastWhere = onlyQuery.lastIndexOf("WHERE");
				onlyQuery = onlyQuery.substring(0, lastWhere);
				
				conditional = queryWithoutOrderBy.substring(lastWhere, queryWithoutOrderBy.length()) + " AND ";
			}

			sql = onlyQuery + joinTranslation + conditional + "(" + whereClause + ")" + orderByClause;
		}
		return sql;
	}


	/**
	 * Get regex pattern to match with table name and/or table alias
	 * @param tableName
	 * @param tableNameAlias
	 * @return
	 */
	public static String getPatternTableName(String tableName, String tableNameAlias) {
		// tableName tableName, tableName AS tableName
		String patternTableWithAliases = tableName + "\\s+" + tableName + "|" + tableName + "\\s+AS\\s+" + tableName;
		if (!Util.isEmpty(tableNameAlias, true) && !tableName.equals(tableNameAlias)) {
			// tableName tableAlias, tableName AS tableAlias
			patternTableWithAliases += "|" + tableName + "\\s+" + tableNameAlias + "|" + tableName + "\\s+AS\\s+" + tableNameAlias;
		}
		// only table on last position
		patternTableWithAliases += "|" + tableName;

		return patternTableWithAliases;
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
		// tableName tableName, tableName AS tableName
		String tableWithAliases = getPatternTableName(tableName, tableNameAlias);

		Matcher matcherFrom = Pattern.compile(
			"\\s+(FROM)\\s+(" + tableWithAliases + ")\\s+(WHERE|(LEFT|INNER|RIGHT)\\s+JOIN)",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL
		)
		.matcher(sql);

		List<MatchResult> fromWhereParts = matcherFrom.results()
			.collect(Collectors.toList());
		int positionFrom = -1;
		if (fromWhereParts != null && fromWhereParts.size() > 0) {
			// MatchResult lastFrom = fromWhereParts.get(fromWhereParts.size() - 1);
			MatchResult lastFrom = fromWhereParts.get(0);
			positionFrom = lastFrom.start();
		} else {
			return 0;
		}

		String queryCount = "SELECT COUNT(*) " + sql.substring(positionFrom, sql.length());

		// remove order by clause
		Matcher matcherOrderBy = Pattern.compile(
			"\\s+(ORDER BY)\\s+",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL
		).matcher(queryCount);
		if(matcherOrderBy.find()) {
			int positionOrderBy = matcherOrderBy.start();
			queryCount = queryCount.substring(0, positionOrderBy);
		}

		if (parameters == null) {
			parameters = new ArrayList<Object>();
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
