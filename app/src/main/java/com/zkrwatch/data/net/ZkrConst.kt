package com.zkrwatch.data.net

import java.util.UUID

/**
 * Port of `zeekr_ev_api/const.py`. Hosts default to SEA (AU region); region URL
 * discovery at login may override the per-region hosts (see [com.zkrwatch.data.auth.ZkrLogin]).
 */
object ZkrConst {

    // Hosts (SEA default)
    const val APP_SERVER_HOST = "https://gateway-pub-hw-em-sg.zeekrlife.com/overseas-app/"
    const val USERCENTER_HOST = "https://gateway-pub-hw-em-sg.zeekrlife.com/zeekr-cuc-idaas-sea/"
    const val MESSAGE_HOST = "https://gateway-pub-hw-em-sg.zeekrlife.com/sea-message-core/"

    // EU hosts (fallback if country not found in SEA region list)
    const val EU_APP_SERVER_HOST = "https://gateway-pub-azure.zeekr.eu/overseas-app/"
    const val EU_USERCENTER_HOST = "https://gateway-pub-azure.zeekr.eu/zeekr-cuc-idaas/"
    const val EU_MESSAGE_HOST = "https://gateway-pub-azure.zeekr.eu/eu-message-core/"

    // Endpoint paths (appended to the relevant host)
    const val LOGIN_URL = "auth/loginByEmailEncrypt"
    const val PROTOCOL_URL = "protocol/service/getProtocol"
    const val URL_URL = "region/url"
    const val CHECKUSER_URL = "auth/checkUserV2"
    const val USERINFO_URL = "user/info"
    const val TSPCODE_URL = "user/tspCode"
    const val BEARERLOGIN_URL = "ms-user-auth/v1.0/auth/login"
    const val VEHLIST_URL = "ms-app-bff/api/v4.0/veh/vehicle-list"
    const val INBOX_URL = "member/inbox/home"
    const val UPDATELANGUAGE_URL = "user/updateLanguage"
    const val VEHICLESTATUS_URL = "ms-vehicle-status/api/v1.0/vehicle/status/latest"
    const val VEHICLECHARGINGSTATUS_URL = "ms-vehicle-status/api/v1.0/vehicle/status/qrvs"
    const val REMOTECONTROLSTATE_URL = "ms-app-bff/api/v1.0/remoteControl/getVehicleState"
    const val REMOTECONTROL_URL = "ms-remote-control/v1.0/remoteControl/control"
    const val CHARGE_CONTROL_URL = "ms-charge-manage/api/v1.0/charge/control"

    const val DEFAULT_COUNTRY_CODE = "AU"
    const val DEFAULT_REGION_CODE = "SEA"

    val REGION_LOGIN_SERVERS = mapOf(
        "SEA" to "https://sea-snc-tsp-api-gw.zeekrlife.com/",
        "UAE" to "https://me-snc-tsp-api-gw.zeekrlife.com/",
        "LA" to "https://la-snc-tsp-api-gw.zeekrlife.com/",
        "EU" to "https://eu-snc-tsp-api-gw.zeekrlife.com/",
    )

    /** X-PROJECT-ID per region; gateway validates against an enum. */
    fun projectIdFor(regionCode: String): String = when (regionCode) {
        "EU" -> "ZEEKR_EU"
        "LA" -> "ZEEKR_LA"
        else -> "ZEEKR_SEA"
    }

    /** Client-id used as the tspClientId query param on the TSP-code call. */
    const val CLIENT_ID = "1JwLroFkFFIpgFGdTRrm4_nzkkwDkfHj7RxJQb7J8tc"

    /** Pre-login headers for HMAC-signed calls (const.DEFAULT_HEADERS). */
    fun defaultHeaders(): LinkedHashMap<String, String> = linkedMapOf(
        "accept-encoding" to "gzip",
        "accept-language" to "en-AU",
        "app-authorization" to "1003",
        "app-code" to "32816dbd-ff17-47b7-e250-5dae7d9f8cd4",
        "appcode" to "eu-app",
        "appid" to "TSP",
        "appsecret" to "zeekr_tis",
        "appversion" to "1.4.1",
        "call-source" to "android",
        "client-id" to CLIENT_ID,
        "Content-Type" to "application/json; charset=UTF-8",
        "country" to DEFAULT_COUNTRY_CODE,
        "device-name" to "sdk_gphone64_x86_64",
        "device-type" to "app",
        "language" to "en",
        "msgappid" to "11002",
        "msgclientid" to "1003",
        "registcountry" to DEFAULT_COUNTRY_CODE,
        "tmp-tenant-code" to "3300743799505195008",
        "user-agent" to "Device/GoogleAppName/com.zeekr.globalAppVersion/1.4.1Platform/androidOSVersion/16Ditto/true",
    )

    /**
     * Post-login headers for app-signature calls (const.LOGGED_IN_HEADERS).
     * `authorization` is filled with the bearer token; `x-device-id` is a stable
     * per-install UUID (generated once); `X-PROJECT-ID` is set per region.
     */
    fun loggedInHeaders(deviceId: String): LinkedHashMap<String, String> = linkedMapOf(
        "Accept-Encoding" to "gzip",
        "ACCEPT-LANGUAGE" to "en-AU",
        "AppId" to "ONEX97FB91F061405",
        "authorization" to "",
        "Content-Type" to "application/json; charset=UTF-8",
        "user-agent" to "okhttp/4.12.0",
        "X-API-SIGNATURE-VERSION" to "2.0",
        "X-APP-ID" to "ZEEKRCNCH001M0001",
        "x-app-os-version" to "",
        "x-device-id" to deviceId,
        "x-p" to "Android",
        "X-PLATFORM" to "APP",
        "X-PROJECT-ID" to "ZEEKR_SEA",
    )

    fun newDeviceId(): String = UUID.randomUUID().toString()
}
