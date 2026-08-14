package com.zkrwatch.data.store

import android.content.Context
import com.zkrwatch.data.net.ZkrKeys

/**
 * Encrypted store for the user's region-scoped keys + account credentials,
 * imported at runtime (see com.zkrwatch.setup.ConfigActivity) rather than baked
 * into the APK. This is what lets a single, secret-free APK be published publicly
 * and configured per-user. Backed by [SecureStore] (Keystore/Tink AEAD).
 *
 * Field names match keys.txt / keys.properties so the same content works whether
 * baked at build time or pushed at runtime.
 */
class ConfigStore(context: Context) {

    private val secure = SecureStore(context)

    fun save(values: Map<String, String>) {
        FIELDS.forEach { field -> values[field]?.let { secure.putString(field, it.trim()) } }
    }

    fun keys(): ZkrKeys? {
        val k = ZkrKeys(
            hmacAccessKey = get("HMAC_ACCESS_KEY"),
            hmacSecretKey = get("HMAC_SECRET_KEY"),
            passwordPublicKey = get("PASSWORD_PUBLIC_KEY"),
            prodSecret = get("PROD_SECRET"),
            vinKey = get("VIN_KEY"),
            vinIv = get("VIN_IV"),
        )
        return if (k.isComplete) k else null
    }

    fun email(): String? = get("ACCOUNT_EMAIL").ifBlank { null }
    fun password(): String? = get("ACCOUNT_PASSWORD").ifBlank { null }
    fun country(): String = get("COUNTRY_CODE").ifBlank { "AU" }

    fun isConfigured(): Boolean = keys() != null && email() != null && password() != null

    fun clear() = FIELDS.forEach { secure.remove(it) }

    private fun get(key: String): String = secure.getString(key).orEmpty()

    companion object {
        val FIELDS = listOf(
            "HMAC_ACCESS_KEY", "HMAC_SECRET_KEY", "PASSWORD_PUBLIC_KEY", "PROD_SECRET",
            "VIN_KEY", "VIN_IV", "COUNTRY_CODE", "ACCOUNT_EMAIL", "ACCOUNT_PASSWORD",
        )
    }
}
