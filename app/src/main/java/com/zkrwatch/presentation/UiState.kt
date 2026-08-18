package com.zkrwatch.presentation

import com.zkrwatch.data.model.VehicleStatus

/** Top-level screen state. */
sealed interface VehicleUiState {
    data object Loading : VehicleUiState
    data object NotConfigured : VehicleUiState
    data class Error(val message: String) : VehicleUiState
    data class Ready(val vin: String, val status: VehicleStatus) : VehicleUiState
}

/** The transactional actions on the main screen. Lock/Unlock share one toggle. */
enum class CommandKind { LOCK, UNLOCK, TRUNK, CLIMATE, SENTRY, FLASH }

/**
 * A personalizable button on the main screen — the unit users show/hide. Distinct
 * from [CommandKind]: the LOCK slot renders either Lock or Unlock depending on the
 * car's lock state. Order here is the on-screen order. See [com.zkrwatch.data.store.UiPrefsStore].
 *
 * [defaultVisible] is the out-of-the-box state for a fresh install; extra actions
 * (e.g. Flash) ship hidden so the main screen stays uncluttered until opted in.
 */
enum class ActionSlot(val defaultVisible: Boolean) {
    LOCK(true), TRUNK(true), CLIMATE(true), SENTRY(true), FLASH(false)
}

/** Per-command progress, used to drive control visuals. */
sealed interface CommandState {
    data object Idle : CommandState
    data object Pending : CommandState
    data object Success : CommandState
    data class Failed(val message: String) : CommandState
}
