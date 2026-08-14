package com.zkrwatch.data.store

import android.content.Context
import com.zkrwatch.data.net.SessionData

/**
 * Persists a [SessionData] snapshot as individual AEAD-encrypted fields via
 * [SecureStore] (avoids pulling in a Kotlin JSON adapter for one small type).
 * Only returns a stored session for the currently-configured account, so
 * switching accounts never reuses a stale token.
 */
class SessionStore(context: Context) {

    private val secure = SecureStore(context)

    fun save(data: SessionData) {
        secure.putString(K_USERNAME, data.username)
        secure.putString(K_DEVICE_ID, data.deviceId)
        secure.putOrRemove(K_AUTH, data.authToken)
        secure.putOrRemove(K_BEARER, data.bearerToken)
        secure.putString(K_APP_HOST, data.appServerHost)
        secure.putString(K_UC_HOST, data.userCenterHost)
        secure.putString(K_MSG_HOST, data.messageHost)
        secure.putString(K_REGION, data.regionCode)
        secure.putOrRemove(K_LOGIN_SERVER, data.regionLoginServer)
    }

    fun load(forUsername: String): SessionData? {
        val username = secure.getString(K_USERNAME) ?: return null
        if (username != forUsername) return null
        val deviceId = secure.getString(K_DEVICE_ID) ?: return null
        return SessionData(
            username = username,
            deviceId = deviceId,
            authToken = secure.getString(K_AUTH),
            bearerToken = secure.getString(K_BEARER),
            appServerHost = secure.getString(K_APP_HOST).orEmpty(),
            userCenterHost = secure.getString(K_UC_HOST).orEmpty(),
            messageHost = secure.getString(K_MSG_HOST).orEmpty(),
            regionCode = secure.getString(K_REGION).orEmpty(),
            regionLoginServer = secure.getString(K_LOGIN_SERVER),
        )
    }

    private companion object {
        const val K_USERNAME = "s_username"
        const val K_DEVICE_ID = "s_device_id"
        const val K_AUTH = "s_auth_token"
        const val K_BEARER = "s_bearer_token"
        const val K_APP_HOST = "s_app_host"
        const val K_UC_HOST = "s_uc_host"
        const val K_MSG_HOST = "s_msg_host"
        const val K_REGION = "s_region"
        const val K_LOGIN_SERVER = "s_login_server"
    }
}
