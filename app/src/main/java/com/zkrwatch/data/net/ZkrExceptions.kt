package com.zkrwatch.data.net

/** Base for all Zkr API errors. */
open class ZkrException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Authentication / token failures (login, bearer, token-refresh). */
class AuthException(message: String, cause: Throwable? = null) : ZkrException(message, cause)
