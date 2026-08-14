package com.zkrwatch.data.net

import com.zkrwatch.data.crypto.VinCipher

/**
 * Holds the mutable session + credentials for one Zkr account, mirroring the
 * stateful fields of the Python `ZkrClient`. Not thread-safe by itself;
 * [com.zkrwatch.data.repo.ZkrRepository] serializes access.
 *
 * @property keys region-scoped app secrets (from BuildConfig at runtime).
 */
class ZkrSession(
    val username: String,
    val password: String,
    val countryCode: String,
    val keys: ZkrKeys,
    val deviceId: String = ZkrConst.newDeviceId(),
) {
    // Region-derived hosts (defaults = SEA; may be overwritten by _get_urls).
    var appServerHost: String = ZkrConst.APP_SERVER_HOST
    var userCenterHost: String = ZkrConst.USERCENTER_HOST
    var messageHost: String = ZkrConst.MESSAGE_HOST
    var regionCode: String = ZkrConst.DEFAULT_REGION_CODE
    var regionLoginServer: String? = ZkrConst.REGION_LOGIN_SERVERS[ZkrConst.DEFAULT_REGION_CODE]

    // Tokens
    var authToken: String? = null      // usercenter session token (HMAC-phase auth header)
    var bearerToken: String? = null    // long-lived app-auth token (app-sig authorization)
    var loggedIn: Boolean = false

    var userInfo: Map<String, Any?> = emptyMap()

    /** Mutable copy of LOGGED_IN_HEADERS; authorization + X-PROJECT-ID updated during login. */
    val loggedInHeaders: LinkedHashMap<String, String> = ZkrConst.loggedInHeaders(deviceId)

    private val vinCache = HashMap<String, String>()

    /** AES-encrypted VIN for the X-VIN header, cached like the reference client. */
    fun encryptedVin(vin: String): String =
        vinCache.getOrPut(vin) { VinCipher.encrypt(vin, keys.vinKey, keys.vinIv) }

    fun reset() {
        authToken = null
        bearerToken = null
        loggedIn = false
        loggedInHeaders["authorization"] = ""
    }

    /** Snapshot for encrypted persistence. */
    fun export(): SessionData = SessionData(
        username = username,
        deviceId = deviceId,
        authToken = authToken,
        bearerToken = bearerToken,
        appServerHost = appServerHost,
        userCenterHost = userCenterHost,
        messageHost = messageHost,
        regionCode = regionCode,
        regionLoginServer = regionLoginServer,
    )

    /** Restore a previously persisted session (skips full re-login while valid). */
    fun load(data: SessionData) {
        authToken = data.authToken
        bearerToken = data.bearerToken
        appServerHost = data.appServerHost
        userCenterHost = data.userCenterHost
        messageHost = data.messageHost
        regionCode = data.regionCode
        regionLoginServer = data.regionLoginServer
        loggedInHeaders["X-PROJECT-ID"] = ZkrConst.projectIdFor(regionCode)
        if (bearerToken != null) {
            loggedIn = true
            loggedInHeaders["authorization"] = bearerToken!!
        }
    }
}

/** Region-scoped app secrets. See keys.properties / BuildConfig. */
data class ZkrKeys(
    val hmacAccessKey: String,
    val hmacSecretKey: String,
    val passwordPublicKey: String,
    val prodSecret: String,
    val vinKey: String,
    val vinIv: String,
) {
    val isComplete: Boolean
        get() = listOf(hmacAccessKey, hmacSecretKey, passwordPublicKey, prodSecret, vinKey, vinIv)
            .all { it.isNotBlank() }
}
