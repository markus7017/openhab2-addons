/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.carnet.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link CarNetApiConstants} defines various Carnet API contants
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class CarNetApiConstants {

    public static final String CNAPI_BASE_URL_AUDI = "https://msg.audi.de/fs-car";
    public static final String CNAPI_BASE_URL_VW = "https://msg.volkswagen.de/fs-car";

    public static final String CNAPI_BRAND_AUDI = "Audi";
    public static final String CNAPI_BRAND_VW = "VW";
    public static final String CNAPI_BRAND_SKODA = "Skoda";
    public static final String CNAPI_BRAND_GO = "Go"; // WE Connect Go

    // HTTP header attributes
    public static final String CNAPI_HEADER_TYPE = "Accept: application/json";
    public static final String CNAPI_HEADER_APP = "X-App-Name";
    public static final String CNAPI_HEADER_APP_EREMOTE = "eRemote";
    public static final String CNAPI_HEADER_APP_MYAUDI = "myAudi";
    public static final String CNAPI_HEADER_VERS = "X-App-Version";
    public static final String CNAPI_HEADER_VERS_VALUE = "1.0.0";
    public static final String CNAPI_HEADER_USER_AGENT = "okhttp/2.3.0";
    public static final String CNAPI_AUTH_AUDI_VERS = "1";
    public static final String CNAPI_HEADER_AUTHORIZATION = "Authorization";
    public static final String CNAPI_HEADER_CLIENTID = "X-Client-Id";
    public static final String CNAPI_HEADER_HOST = "Host";

    public static final String CNAPI_CONTENTT_FORM_URLENC = "application/x-www-form-urlencoded";
    public static final String CNAPI_ACCEPTT_JSON = "application/json";

    public static int CNAPI_TIMEOUT_MS = 30 * 1000;

    // URIs: {0}=brand, {1} = VIN
    public static final String CNAPI_URI_GET_TOKEN = "core/auth/v1/{0}/{1}/token";
    public static final String CNAPI_URI_GET_USERINFO = "usermanagement/users/v1/{0}/{1}/vehicles/{2}/pairing";
    public static final String CNAPI_URI_VEHICLE_MANAGEMENT = "vehicleMgmt/vehicledata/v2/{0}/{1}/vehicles/{2}";
    public static final String CNAPI_URI_VEHICLE_LIST = "usermanagement/users/v1/{0}/{1}/vehicles";
    public static final String CNAPI_URI_VEHICLE_DETAILS = "promoter/portfolio/v1/{0}/{1}/vehicle//{2}/carportdata";
    public static final String CNAPI_URI_VEHICLE_DATA = "bs/vsr/v1/{0}/{1}/vehicles/{2}/requests";
    public static final String CNAPI_URI_VEHICLE_STATUS = "bs/vsr/v1/{0}/{1}/vehicles/{2}/status";
    public static final String CNAPI_URI_VEHICLE_POSITION = "bs/cf/v1/{0}/{1}/vehicles/{2}/position";
    public static final String CNAPI_URI_CLIMATER_STATUS = "bs/climatisation/v1/{0}/{1}/vehicles/{2}/climater";
    public static final String CNAPI_URI_CLIMATER_TIMER = "bs/departuretimer/v1/{0}/{1}/vehicles/{2}/timer";
    public static final String CNAPI_URI_CHARGER_STATUS = "bs/batterycharge/v1/{0}/{1}/vehicles/{2}/charger";
    public static final String CNAPI_URI_STORED_POS = "bs/cf/v1/{0}/{1}/vehicles/{2}/position";
    public static final String CNAPI_URI_DESTINATIONS = "destinationfeedservice/mydestinations/v1/{0}/{1}/vehicles/{2}/destinations";
    public static final String CNAPI_URI_HISTORY = "bs/dwap/v1/{0}/{1}/vehicles/{2}/history";
    public static final String CNAPI_URI_CMD_HONK = "bs/rhf/v1/{0}/{1}/vehicles/{2}/honkAndFlash";
    public static final String CNAPI_URI_GETTRIP = "bs/tripstatistics/v1/{0}/{1}/vehicles/{2}/tripdata/{3}?type={4}";

    private static final String VWPRE = "https://msg.volkswagen.de/fs-car/";
    public static final String CNAPI_VWURL_CLIMATE_STATUS = VWPRE + CNAPI_URI_CLIMATER_STATUS;
    public static final String CNAPI_VWURL_CHARGER_STATUS = VWPRE + CNAPI_URI_CHARGER_STATUS;
    public static final String CNAPI_VWURL_STORED_POS = VWPRE + CNAPI_URI_STORED_POS;
    public static final String CNAPI_VWURL_TIMER = VWPRE + CNAPI_URI_CLIMATER_TIMER;
    public static final String CNAPI_VWURL_TRIP_DATA = VWPRE + CNAPI_URI_GETTRIP;
    public static final String CNAPI_VWURL_CHARGER = VWPRE + CNAPI_URI_CHARGER_STATUS;

    public static final String CNAPI_URL_GET_SEC_TOKEN = "https://mbboauth-1d.prd.ece.vwg-connect.com/mbbcoauth/mobile/oauth2/v1/token";
    public static final String CNAPI_URL_GET_CHALLENGE = "https://mal-1a.prd.ece.vwg-connect.com/api/rolesrights/authorization/v2/vehicles/";
    public static final String CNAPI_URL_ACK_CHALLENGE = "https://mal-1a.prd.ece.vwg-connect.com/api/rolesrights/authorization/v2/security-pin-auth-completed";
    public static final String CNAPI_VWURL_HOMEREGION = "https://mal-1a.prd.ece.vwg-connect.com/api/cs/vds/v1/vehicles/{2}/homeRegion";
    public static final String CNAPI_VWURL_OPERATIONS = "https://mal-1a.prd.ece.vwg-connect.com/api/rolesrights/operationlist/v3/vehicles/{2}";

    public static final String CNAPI_OAUTH_BASE_URL = "https://identity.vwgroup.io";
    public static final String CNAPI_OAUTH_AUTHORIZE_URL = CNAPI_OAUTH_BASE_URL + "/oidc/v1/authorize";
    public static final String CNAPI_OAUTH_IDENTIFIER_URL = CNAPI_OAUTH_BASE_URL
            + "/signin-service/v1/09b6cbec-cd19-4589-82fd-363dfa8c24da@apps_vw-dilab_com/login/identifier";
    public static final String CNAPI_OAUTH_AUTHENTICATE_URL = CNAPI_OAUTH_BASE_URL
            + "/signin-service/v1/09b6cbec-cd19-4589-82fd-363dfa8c24da@apps_vw-dilab_com/login/authenticate";
    public static final String CNAPI_AUDI_TOKEN_URL = "https://app-api.my.audi.com/myaudiappidk/v1/token";
    public static final String CNAPI_VW_TOKEN_URL = "https://mbboauth-1d.prd.ece.vwg-connect.com/mbbcoauth/mobile/oauth2/v1/token";

    public static final String CNAPI_AUDIURL_OPERATIONS = "https://msg.audi.de/myaudi/vehicle-management/v2/vehicles";

    public static final String CNAPI_URL_AUDI_GET_TOKEN = "https://id.audi.com/v1/token";
    public static final String CNAPI_URL_GO_GET_TOKEN = "https://id.audi.com/v1/token";
    public static final String CNAPI_URL_DEF_GET_TOKEN = "https://tokenrefreshservice.apps.emea.vwapps.io/refreshTokens";

    public static final String CNAPI_SERVICE_VEHICLE_STATUS = "statusreport_v1";
    public static final String CNAPI_SERVICE_REMOTELOCK = "rlu_v1";
    public static final String CNAPI_SERVICE_CLIMATER = "rclima_v1";
    public static final String CNAPI_SERVICE_CHARGER = "rbatterycharge_v1";
    public static final String CNAPI_SERVICE_CARFINDER = "carfinder_v1";
    public static final String CNAPI_SERVICE_DESTINATIONS = "zieleinspeisung_v1";
    public static final String CNAPI_SERVICE_TRIPDATA = "trip_statistic_v1";

    public static final String CNAPI_URL_RLU_ACTIONS = "bs/rlu/v1/{0}/{1}/vehicles/{2}/actions";
    public static final String CNAPI_RLU_LOCK = "LOCK";
    public static final String CNAPI_RLU_UNLOCK = "UNLOCK";
    public static final String CNAPI_SERVICE_CLIMATISATION = "climatisation";
    public static final String CNAPI_SERVICE_PREHEATING = "rheating_v1";
    public static final String CNAPI_RHEATING_ACTION = "P_QSACT";

    public static final String CNAPI_SERVICE_TRIPSTATS = "tripstatistics";
    public static final String CNAPI_TRIP_SHORT_TERM = "shortTerm";
    public static final String CNAPI_TRIP_LONG_TERM = "longTerm";
}
