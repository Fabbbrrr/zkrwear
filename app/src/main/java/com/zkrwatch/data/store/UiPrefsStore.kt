package com.zkrwatch.data.store

import android.content.Context
import com.zkrwatch.presentation.ActionSlot

/**
 * Non-sensitive UI preferences, persisted in plain SharedPreferences (no Keystore
 * needed — this only records which action buttons the user wants and in what order).
 * Modeled on [com.zkrwatch.data.cache.StatusCache].
 *
 * Stored as an *ordered* list of enabled slot names — the arrangement the user set
 * by dragging in edit mode. When nothing is stored yet (fresh install / existing
 * users upgrading), the default set is shown in enum order so the screen looks
 * exactly as before until the user rearranges or hides something.
 */
class UiPrefsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The enabled slots, in the user's chosen on-screen order. */
    fun enabledOrder(): List<ActionSlot> {
        prefs.getString(KEY_ORDER, null)?.let { csv ->
            val order = csv.split(",")
                .mapNotNull { name -> runCatching { ActionSlot.valueOf(name) }.getOrNull() }
            if (order.isNotEmpty()) return order
        }
        // Migrate from the older unordered set, preserving enum order.
        prefs.getStringSet(KEY_ENABLED, null)?.let { set ->
            return ActionSlot.entries.filter { it.name in set }
        }
        return defaultEnabled()
    }

    /** Slots shown on a fresh install (opt-in extras start hidden), in enum order. */
    fun defaultEnabled(): List<ActionSlot> = ActionSlot.entries.filter { it.defaultVisible }

    /** Persist the enabled slots in their on-screen order. */
    fun setOrder(slots: List<ActionSlot>) {
        prefs.edit()
            .putString(KEY_ORDER, slots.joinToString(",") { it.name })
            .remove(KEY_ENABLED) // superseded by the ordered list
            .apply()
    }

    private companion object {
        const val PREFS = "zkr_ui_prefs"
        const val KEY_ENABLED = "enabled_slots" // legacy unordered set, read once for migration
        const val KEY_ORDER = "enabled_order"
    }
}
