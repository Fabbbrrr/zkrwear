package com.zkrwatch.data.net

/**
 * Serializable snapshot of a logged-in session, mirroring the Python client's
 * export_session/load_session. Persisted encrypted so the app can reuse a valid
 * bearer token across launches instead of re-running the 9-step login every time
 * (which also reduces the single-session-logout churn on the account).
 */
data class SessionData(
    val username: String,
    val deviceId: String,
    val authToken: String?,
    val bearerToken: String?,
    val appServerHost: String,
    val userCenterHost: String,
    val messageHost: String,
    val regionCode: String,
    val regionLoginServer: String?,
)
