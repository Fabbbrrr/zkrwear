package com.zkrwatch.data.net

import com.squareup.moshi.Moshi
import com.zkrwatch.data.crypto.ZkrAppSig
import com.zkrwatch.data.crypto.ZkrHmac
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Port of `zeekr_ev_api/network.py`: the four signed transports.
 *
 * - [customGet]/[customPost] — HMAC-signed, pre-login DEFAULT_HEADERS.
 * - [appSignedGet]/[appSignedPost] — app-signature-signed, post-login LOGGED_IN_HEADERS.
 *
 * Two faithful-port subtleties, straight from the reference:
 *  1. **HMAC body digest is over an empty string.** The reference builds a
 *     `requests.Request(json=body)` and signs it *before* `prepare_request`
 *     serializes the JSON, so `request.data` is empty and the digest is
 *     `HMAC(secret, "")`. The JSON body is still sent. We replicate exactly.
 *  2. **Token refresh triggers on `msg == "Token expired"` in the JSON body**,
 *     not on HTTP 401. On hit we re-login once via [reloginHandler] and retry.
 *
 * Response bodies are parsed to `Map<String, Any?>` (Moshi's built-in Any adapter:
 * objects -> LinkedHashMap, arrays -> List, numbers -> Double).
 */
class ZkrHttp(
    private val session: ZkrSession,
    private val client: OkHttpClient,
    moshi: Moshi,
) {
    /** Set by the login layer; re-runs login(relogin=true) on token expiry. */
    var reloginHandler: (() -> Unit)? = null

    @Suppress("UNCHECKED_CAST")
    private val anyAdapter = moshi.adapter(Any::class.java)

    // ---- HMAC transports (DEFAULT_HEADERS) ----

    fun customGet(url: String): Map<String, Any?> {
        val builder = Request.Builder().url(url).get()
        applyHmacHeaders(builder, method = "GET", url = url)
        return execute(builder.build())
    }

    fun customPost(url: String, body: Map<String, Any?>? = null): Map<String, Any?> {
        val json = if (body == null) "" else anyAdapter.toJson(body)
        // POST always carries a JSON body (empty object when none); Content-Type
        // is supplied via DEFAULT_HEADERS, so the RequestBody has no media type.
        val reqBody = (if (body == null) "{}" else json).toRequestBody(null)
        val builder = Request.Builder().url(url).post(reqBody)
        applyHmacHeaders(builder, method = "POST", url = url)
        return execute(builder.build())
    }

    /** Adds DEFAULT_HEADERS + (post-login) authorization, then the X-HMAC-* headers. */
    private fun applyHmacHeaders(builder: Request.Builder, method: String, url: String) {
        val headers = ZkrConst.defaultHeaders()
        session.authToken?.let { headers["authorization"] = it }
        putHeaders(builder, headers)
        // Digest is over "" to match the reference (see class doc, subtlety #1).
        val signed = ZkrHmac.sign(method, url, "", session.keys.hmacAccessKey, session.keys.hmacSecretKey)
        for ((k, v) in signed) builder.header(k, v)
    }

    // ---- App-signature transports (LOGGED_IN_HEADERS) ----

    fun appSignedGet(
        url: String,
        extraHeaders: Map<String, String>? = null,
        allowRetry: Boolean = true,
    ): Map<String, Any?> {
        val bearer = session.bearerToken ?: throw ZkrException("Client is not logged in.")
        val headers = buildAppSigHeaders(bearer, extraHeaders)
        val signature = ZkrAppSig.calculateSig("GET", url, headers, null, session.keys.prodSecret)
        headers["X-SIGNATURE"] = signature

        val builder = Request.Builder().url(url).get()
        putHeaders(builder, headers)
        val result = execute(builder.build())
        return handleTokenExpiry(result, allowRetry) { appSignedGet(url, extraHeaders, allowRetry = false) }
    }

    fun appSignedPost(
        url: String,
        body: String,
        extraHeaders: Map<String, String>? = null,
        allowRetry: Boolean = true,
    ): Map<String, Any?> {
        // No bearer requirement here: the bearer-login call is itself an
        // appSignedPost made before any token exists (matches network.py, where
        // only appSignedGet checks for a token). authorization stays "" until set.
        val headers = buildAppSigHeaders(session.bearerToken, extraHeaders)
        val signature = ZkrAppSig.calculateSig("POST", url, headers, body, session.keys.prodSecret)
        headers["X-SIGNATURE"] = signature

        val builder = Request.Builder().url(url).post(body.toRequestBody(null))
        putHeaders(builder, headers)
        val result = execute(builder.build())
        return handleTokenExpiry(result, allowRetry) { appSignedPost(url, body, extraHeaders, allowRetry = false) }
    }

    /** LOGGED_IN_HEADERS copy + authorization + fresh nonce/timestamp + extras. */
    private fun buildAppSigHeaders(
        bearer: String?,
        extraHeaders: Map<String, String>?,
    ): LinkedHashMap<String, String> {
        val headers = LinkedHashMap(session.loggedInHeaders)
        // Keep the existing (empty) authorization when no bearer yet — the app-sig
        // signer excludes an empty authorization header, matching the reference.
        if (bearer != null) headers["authorization"] = bearer
        extraHeaders?.let { headers.putAll(it) }
        headers["X-API-SIGNATURE-NONCE"] = UUID.randomUUID().toString()
        headers["X-TIMESTAMP"] = System.currentTimeMillis().toString()
        return headers
    }

    private inline fun handleTokenExpiry(
        result: Map<String, Any?>,
        allowRetry: Boolean,
        retry: () -> Map<String, Any?>,
    ): Map<String, Any?> {
        if (isSessionExpired(result)) {
            if (!allowRetry) throw AuthException("Session expired (retry failed): $result")
            val handler = reloginHandler ?: throw AuthException("Session expired; no relogin handler")
            handler()
            return retry()
        }
        return result
    }

    /**
     * True when a failed response looks like a stale session/token — covers the
     * exact "Token expired" message and the broader family (expiry-code 079021,
     * or messages mentioning token/expired/login) seen when a persisted bearer
     * token is reused after it lapsed.
     */
    private fun isSessionExpired(result: Map<String, Any?>): Boolean {
        if (result["msg"] == "Token expired") return true
        if (result["success"] == true) return false
        val msg = (result["msg"] as? String)?.lowercase().orEmpty()
        val code = result["code"]?.toString().orEmpty()
        return code == "079021" ||
            msg.contains("token") || msg.contains("expired") ||
            msg.contains("not logged") || msg.contains("re-login") || msg.contains("relogin")
    }

    // ---- shared ----

    /**
     * Applies headers, dropping any accept-encoding so OkHttp manages gzip
     * transparently (it will not auto-decompress a response when the request
     * sets Accept-Encoding itself). Signatures never cover accept-encoding.
     */
    private fun putHeaders(builder: Request.Builder, headers: Map<String, String>) {
        for ((k, v) in headers) {
            if (k.equals("accept-encoding", ignoreCase = true)) continue
            builder.header(k, v)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun execute(request: Request): Map<String, Any?> {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isEmpty()) {
                throw ZkrException("Empty response from server")
            }
            return try {
                anyAdapter.fromJson(text) as? Map<String, Any?>
                    ?: throw ZkrException("Non-object JSON response")
            } catch (e: Exception) {
                throw ZkrException("Invalid JSON response: ${e.message}")
            }
        }
    }
}
