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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.            *
 ************************************************************************************/
package org.spin.base.util;

/**
 * Services
 * @author Yamel Senih
 */
public enum Services {
    ACCESS("access"),
    ENROLLMENT("enrollment"),
    DICTIONARY("dictionary"),
    BUSINESS("business"),
    CORE("core"),
	MATERIAL_MANAGEMENT("material_management"),
    UI("ui"),
    DASHBOARDING("dashboarding"),
	GENERAL_LEDGER("general_ledger"),
    LOG("log"),
    STORE("store"),
    POS("pos"),
    UPDATER("updater"),
    EXTENSION("extension"),
    BUSINESS_PARTNER("business_partner"),
    IN_OUT("in_out"),
    INVOICE("invoice"),
    ORDER("order"),
    PAYMENT("payment"),
    PAYROLL_ACTION_NOTICE("payroll_action_notice"),
	PRODUCT("product"),
	TIME_CONTROL("time_control"),
	WORKFLOW("workflow");

	/**	Service Name	*/
    private final String serviceName;
    
    Services(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}