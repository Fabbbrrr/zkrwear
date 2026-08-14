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
enum class CommandKind { LOCK, UNLOCK, TRUNK, CLIMATE }

/** Per-command progress, used to drive control visuals. */
sealed interface CommandState {
    data object Idle : CommandState
    data object Pending : CommandState
    data object Success : CommandState
    data class Failed(val message: String) : CommandState
}
