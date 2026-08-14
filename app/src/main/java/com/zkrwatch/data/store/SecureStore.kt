package com.zkrwatch.data.store

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.StandardCharsets

/**
 * Small AEAD-encrypted key/value store. The Tink keyset is wrapped by a master
 * key in the Android Keystore (hardware-backed where available), so values are
 * encrypted at rest and the master key never leaves the secure element.
 *
 * Used to persist the logged-in session (bearer token etc.) — see [SessionStore].
 */
class SecureStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(VALUES_PREFS, Context.MODE_PRIVATE)

    private val aead: Aead by lazy {
        AeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        handle.getPrimitive(Aead::class.java)
    }

    fun putString(key: String, value: String) {
        val ct = aead.encrypt(value.toByteArray(StandardCharsets.UTF_8), key.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(ct, Base64.NO_WRAP)).apply()
    }

    fun getString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val pt = aead.decrypt(Base64.decode(stored, Base64.NO_WRAP), key.toByteArray(StandardCharsets.UTF_8))
            String(pt, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Corrupt/rotated key or tampered value -> treat as absent.
            null
        }
    }

    /** Store when non-null, otherwise clear any existing value. */
    fun putOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    private companion object {
        const val VALUES_PREFS = "zkr_secure_values"
        const val KEYSET_PREFS = "zkr_secure_keyset"
        const val KEYSET_NAME = "zkr_master_keyset"
        const val MASTER_KEY_URI = "android-keystore://zkr_master_key"
    }
}
