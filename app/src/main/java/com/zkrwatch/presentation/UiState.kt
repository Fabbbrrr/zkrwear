package com.zkrwatch.presentation

import com.zkrwatch.data.model.VehicleStatus

/** Top-level screen state. */
sealed interface VehicleUiState {
    data object Loading : VehicleUiState
    data object NotConfigured : VehicleUiState
    data class Error(val message: String) : VehicleUiState
    data class Ready(
        val vin: String,
        val status: VehicleStatus,
        val updatedAt: Long = System.currentTimeMillis(),
    ) : VehicleUiState
}

/** The transactional actions on the main screen. Lock/Unlock share one toggle. */
enum class CommandKind { LOCK, UNLOCK, TRUNK, CLIMATE, SENTRY }

/**
 * A personalizable button on the main screen — the unit users show/hide. Distinct
 * from [CommandKind]: the LOCK slot renders either Lock or Unlock depending on the
 * car's lock state. Order here is the on-screen order. See [com.zkrwatch.data.store.UiPrefsStore].
 */
enum class ActionSlot { LOCK, TRUNK, CLIMATE, SENTRY }

/** Per-command progress, used to drive control visuals. */
sealed interface CommandState {
    data object Idle : CommandState
    data object Pending : CommandState
    data object Success : CommandState
    data class Failed(val message: String) : CommandState
}
