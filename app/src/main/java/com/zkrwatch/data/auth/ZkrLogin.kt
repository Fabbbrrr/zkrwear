package com.zkrwatch.data.auth

import com.squareup.moshi.Moshi
import com.zkrwatch.data.crypto.PasswordRsa
import com.zkrwatch.data.net.AuthException
import com.zkrwatch.data.net.ZkrConst
import com.zkrwatch.data.net.ZkrException
import com.zkrwatch.data.net.ZkrHttp
import com.zkrwatch.data.net.ZkrSession
import com.zkrwatch.data.net.child
import com.zkrwatch.data.net.isSuccess
import com.zkrwatch.data.net.list
import com.zkrwatch.data.net.str

/**
 * Port of the login chain in `client.py` (`login()` and its 9 steps).
 *
 * All login-phase calls are HMAC-signed (customGet/customPost) except the final
 * bearer login, which is app-signature-signed. On success the session holds
 * [ZkrSession.authToken] (usercenter) and [ZkrSession.bearerToken] (app-auth),
 * and `loggedInHeaders["authorization"]` is set to the bearer token.
 */
class ZkrLogin(
    private val session: ZkrSession,
    private val http: ZkrHttp,
    private val moshi: Moshi,
) {
    /** Invoked after a fresh (re)login succeeds, e.g. to persist the session. */
    var onLogin: (() -> Unit)? = null

    init {
        // Token-expiry on any app-signed call re-runs the full login.
        http.reloginHandler = { login(relogin = true) }
    }

    @Synchronized
    fun login(relogin: Boolean = false) {
        if (session.loggedIn && !relogin) return
        if (relogin) session.reset()

        getUrls()
        checkUser()
        doLoginRequest()
        getUserInfo()
        getProtocol()
        checkInbox()
        val tspCode = getTspCode()
        updateLanguage()
        bearerLogin(tspCode)
        session.loggedIn = true
        onLogin?.invoke()
    }

    // 1) Region URL discovery -> per-region hosts + X-PROJECT-ID.
    private fun getUrls() {
        val urls = http.customGet("${ZkrConst.APP_SERVER_HOST}${ZkrConst.URL_URL}")
        if (!urls.isSuccess()) throw ZkrException("Unable to fetch URL data")

        var found = false
        for (block in urls.list("data").orEmpty()) {
            @Suppress("UNCHECKED_CAST")
            val b = block as? Map<String, Any?> ?: continue
            if (b.str("countryCode")?.lowercase() == session.countryCode.lowercase()) {
                val url = b.child("url")
                session.appServerHost = url?.str("appServerUrl").orEmpty()
                session.userCenterHost = url?.str("userCenterUrl").orEmpty()
                session.messageHost = url?.str("messageCoreUrl").orEmpty()
                session.regionCode = b.str("regionCode") ?: "SEA"
                found = true
                break
            }
        }

        if (!found) {
            // EU fallback: check the EU region list for this country.
            val eu = http.customGet("${ZkrConst.EU_APP_SERVER_HOST}${ZkrConst.URL_URL}")
            val euHasCountry = eu.isSuccess() && eu.list("data").orEmpty().any {
                @Suppress("UNCHECKED_CAST")
                (it as? Map<String, Any?>)?.str("countryCode")?.lowercase() == session.countryCode.lowercase()
            }
            if (euHasCountry) {
                session.appServerHost = ZkrConst.EU_APP_SERVER_HOST
                session.userCenterHost = ZkrConst.EU_USERCENTER_HOST
                session.messageHost = ZkrConst.EU_MESSAGE_HOST
                session.regionCode = "EU"
            } else {
                throw ZkrException("Country code not supported in region lookup: ${session.countryCode}")
            }
        }

        if (session.appServerHost.isBlank() || session.userCenterHost.isBlank() || session.messageHost.isBlank()) {
            throw ZkrException("One or more API URLs are blank after fetching.")
        }
        // Normalise trailing slash (LA region omits it).
        session.appServerHost = session.appServerHost.ensureSlash()
        session.userCenterHost = session.userCenterHost.ensureSlash()
        session.messageHost = session.messageHost.ensureSlash()

        session.regionLoginServer = ZkrConst.REGION_LOGIN_SERVERS[session.regionCode]
            ?: throw ZkrException("No login server for region: ${session.regionCode}")
        session.loggedInHeaders["X-PROJECT-ID"] = ZkrConst.projectIdFor(session.regionCode)
    }

    // 2) User existence check.
    private fun checkUser() {
        val body = linkedMapOf<String, Any?>("email" to session.username, "checkType" to "1")
        val resp = http.customPost("${session.userCenterHost}${ZkrConst.CHECKUSER_URL}", body)
        if (!resp.isSuccess()) throw AuthException("User check failed")
    }

    // 3) Encrypted-password login -> usercenter auth token.
    private fun doLoginRequest() {
        val encrypted = PasswordRsa.encrypt(session.password, session.keys.passwordPublicKey)
        val body = linkedMapOf<String, Any?>(
            "code" to "",
            "codeId" to "",
            "email" to session.username,
            "password" to encrypted,
        )
        val resp = http.customPost("${session.userCenterHost}${ZkrConst.LOGIN_URL}", body)
        if (!resp.isSuccess()) throw AuthException("Login failed: $resp")

        val data = resp.child("data")
        if (data?.str("tokenName") != "Authorization") {
            throw AuthException("Unknown login token type: $data")
        }
        session.authToken = data.str("tokenValue")
            ?: throw AuthException("No auth token supplied in login response")
    }

    // 4) User info (stored, best-effort).
    private fun getUserInfo() {
        val resp = http.customPost("${session.userCenterHost}${ZkrConst.USERINFO_URL}")
        if (resp.isSuccess()) session.userInfo = resp.child("data") ?: emptyMap()
    }

    // 5) Protocol agreement (best-effort).
    private fun getProtocol() {
        http.customPost(
            "${session.appServerHost}${ZkrConst.PROTOCOL_URL}",
            linkedMapOf("country" to session.countryCode),
        )
    }

    // 6) Inbox (best-effort).
    private fun checkInbox() {
        http.customGet("${session.appServerHost}${ZkrConst.INBOX_URL}")
    }

    // 7) TSP code (exchanged for the bearer token).
    private fun getTspCode(): String {
        val url = "${session.userCenterHost}${ZkrConst.TSPCODE_URL}?tspClientId=${ZkrConst.CLIENT_ID}"
        val resp = http.customGet(url)
        if (!resp.isSuccess()) throw ZkrException("Unable to fetch TSP Code: $resp")
        return resp.child("data")?.str("code")
            ?: throw ZkrException("No TSP code in response: $resp")
    }

    // 8) Language (best-effort).
    private fun updateLanguage(language: String = "en") {
        http.customGet("${session.userCenterHost}${ZkrConst.UPDATELANGUAGE_URL}?language=$language")
    }

    // 9) Bearer login (app-signed) -> long-lived access token.
    private fun bearerLogin(tspCode: String) {
        val body = linkedMapOf<String, Any?>(
            "identifier" to tspCode,
            "identityType" to 10,
            "loginDeviceId" to "google-sdk_gphone64_x86_64-36-16",
            "loginDeviceJgId" to "",
            "loginDeviceType" to 1,
            "loginPhoneBrand" to "google",
            "loginPhoneModel" to "sdk_gphone64_x86_64",
            "loginSystem" to "Android",
        )
        val json = moshi.adapter(Any::class.java).toJson(body)
        val server = session.regionLoginServer ?: throw AuthException("No region login server")
        val resp = http.appSignedPost("$server${ZkrConst.BEARERLOGIN_URL}", json)
        if (!resp.isSuccess()) throw AuthException("Bearer login failed: $resp")

        session.bearerToken = resp.child("data")?.str("accessToken")
            ?: throw AuthException("No bearer token in response: ${resp.child("data")}")
        session.loggedInHeaders["authorization"] = session.bearerToken!!
    }

    private fun String.ensureSlash(): String = if (endsWith("/")) this else "$this/"
}
