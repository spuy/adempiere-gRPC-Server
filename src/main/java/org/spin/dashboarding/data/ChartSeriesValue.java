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
 * Copyright (C) 2012-2023 E.R.P. Consultores y Asociados, S.A. All Rights Reserved. *
 * Contributor(s): Yamel Senih www.erpya.com                                         *
 *************************************************************************************/
package org.spin.dashboarding.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub for chart series
 * @author Yamel Senih at www.erpya.com
 *
 */
public final class ChartSeriesValue {
	
	private String name;
	private List<ChartDataValue> dataSet;
	
	public ChartSeriesValue(String name) {
		this.name = name;
		this.dataSet = new ArrayList<ChartDataValue>();
	}
	
	public ChartSeriesValue addData(ChartDataValue data) {
		if(data != null) {
			dataSet.add(data);
		}
		return this;
	}
	
	public ChartSeriesValue setName(String name) {
		this.name = name;
		return this;
	}
	
	public String getName() {
		return name;
	}
	
	public List<ChartDataValue> getDataSet() {
		return dataSet;
	}
}
