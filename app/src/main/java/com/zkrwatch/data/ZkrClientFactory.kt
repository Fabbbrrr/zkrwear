package com.zkrwatch.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.zkrwatch.BuildConfig
import com.zkrwatch.data.auth.ZkrLogin
import com.zkrwatch.data.net.ZkrConst
import com.zkrwatch.data.net.ZkrHttp
import com.zkrwatch.data.net.ZkrKeys
import com.zkrwatch.data.net.ZkrSession
import com.zkrwatch.data.repo.ZkrRepository
import com.zkrwatch.data.store.SessionStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Wires the session, HTTP transports, login chain, and repository together. */
object ZkrClientFactory {

    /** Region-scoped keys baked in at build time (see keys.properties). */
    fun buildConfigKeys(): ZkrKeys = ZkrKeys(
        hmacAccessKey = BuildConfig.HMAC_ACCESS_KEY,
        hmacSecretKey = BuildConfig.HMAC_SECRET_KEY,
        passwordPublicKey = BuildConfig.PASSWORD_PUBLIC_KEY,
        prodSecret = BuildConfig.PROD_SECRET,
        vinKey = BuildConfig.VIN_KEY,
        vinIv = BuildConfig.VIN_IV,
    )

    fun create(
        username: String,
        password: String,
        keys: ZkrKeys,
        countryCode: String = BuildConfig.COUNTRY_CODE.ifBlank { ZkrConst.DEFAULT_COUNTRY_CODE },
        deviceId: String = ZkrConst.newDeviceId(),
    ): ZkrRepository {
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val session = ZkrSession(username, password, countryCode, keys, deviceId)
        val http = ZkrHttp(session, client, moshi)
        val login = ZkrLogin(session, http, moshi)
        return ZkrRepository(session, http, login, moshi)
    }

    /**
     * Like [create], but restores any persisted session for [username] up front
     * (reusing its device id + bearer token to skip a full re-login) and saves
     * the session after each successful login. Uses Keystore-backed encryption.
     */
    fun createPersistent(
        context: Context,
        username: String,
        password: String,
        keys: ZkrKeys,
        countryCode: String = BuildConfig.COUNTRY_CODE.ifBlank { ZkrConst.DEFAULT_COUNTRY_CODE },
    ): ZkrRepository {
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val store = SessionStore(context)
        val stored = runCatching { store.load(username) }.getOrNull()
        val deviceId = stored?.deviceId ?: ZkrConst.newDeviceId()

        val session = ZkrSession(username, password, countryCode, keys, deviceId)
        stored?.let { runCatching { session.load(it) } }

        val http = ZkrHttp(session, client, moshi)
        val login = ZkrLogin(session, http, moshi)
        login.onLogin = { runCatching { store.save(session.export()) } }
        return ZkrRepository(session, http, login, moshi)
    }
}
