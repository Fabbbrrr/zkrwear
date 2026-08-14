package com.zkrwatch.data.cache

import android.content.Context
import com.zkrwatch.data.model.VehicleStatus

/**
 * Last-known vehicle status, persisted in SharedPreferences. Written by the app
 * whenever it fetches fresh status; read by the Tile and the watch-face
 * complication so those surfaces render instantly without doing network on their
 * UI path (they request their own refresh separately).
 */
class StatusCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun write(vin: String, status: VehicleStatus) {
        prefs.edit()
            .putString(KEY_VIN, vin)
            .putInt(KEY_SOC, status.socPercent ?: -1)
            .putInt(KEY_RANGE, status.rangeKm ?: -1)
            .putInt(KEY_LOCKED, status.locked?.let { if (it) 1 else 0 } ?: -1)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun read(): CachedStatus = CachedStatus(
        vin = prefs.getString(KEY_VIN, null),
        socPercent = prefs.getInt(KEY_SOC, -1).takeIf { it >= 0 },
        rangeKm = prefs.getInt(KEY_RANGE, -1).takeIf { it >= 0 },
        locked = when (prefs.getInt(KEY_LOCKED, -1)) {
            1 -> true
            0 -> false
            else -> null
        },
        updatedAt = prefs.getLong(KEY_UPDATED, 0L).takeIf { it > 0 },
    )

    private companion object {
        const val PREFS = "zkr_status_cache"
        const val KEY_VIN = "vin"
        const val KEY_SOC = "soc"
        const val KEY_RANGE = "range"
        const val KEY_LOCKED = "locked"
        const val KEY_UPDATED = "updated"
    }
}

data class CachedStatus(
    val vin: String?,
    val socPercent: Int?,
    val rangeKm: Int?,
    val locked: Boolean?,
    val updatedAt: Long?,
)
