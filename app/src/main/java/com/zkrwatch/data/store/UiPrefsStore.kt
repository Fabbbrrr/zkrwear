package com.zkrwatch.data.store

import android.content.Context
import com.zkrwatch.presentation.ActionSlot

/**
 * Non-sensitive UI preferences, persisted in plain SharedPreferences (no Keystore
 * needed — this only records which action buttons the user wants visible). Modeled
 * on [com.zkrwatch.data.cache.StatusCache].
 *
 * Stored as the set of *enabled* slot names. When nothing is stored yet (fresh
 * install / existing users upgrading), every slot is enabled so the screen looks
 * exactly as before until the user opts to hide something.
 */
class UiPrefsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabledSlots(): Set<ActionSlot> {
        val stored = prefs.getStringSet(KEY_ENABLED, null) ?: return defaultEnabled()
        val slots = stored.mapNotNull { name -> runCatching { ActionSlot.valueOf(name) }.getOrNull() }
        return slots.toSet()
    }

    /** Slots shown on a fresh install (opt-in extras start hidden). */
    fun defaultEnabled(): Set<ActionSlot> = ActionSlot.entries.filter { it.defaultVisible }.toSet()

    fun setEnabled(slots: Set<ActionSlot>) {
        prefs.edit()
            .putStringSet(KEY_ENABLED, slots.map { it.name }.toSet())
            .apply()
    }

    private companion object {
        const val PREFS = "zkr_ui_prefs"
        const val KEY_ENABLED = "enabled_slots"
    }
}
