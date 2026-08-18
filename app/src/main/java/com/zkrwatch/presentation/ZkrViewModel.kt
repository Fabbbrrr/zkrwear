package com.zkrwatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkrwatch.BuildConfig
import com.zkrwatch.data.ZkrClientFactory
import com.zkrwatch.data.cache.StatusCache
import com.zkrwatch.data.net.AuthException
import com.zkrwatch.data.net.ZkrKeys
import com.zkrwatch.data.repo.ZkrRepository
import com.zkrwatch.data.store.ConfigStore
import com.zkrwatch.data.store.UiPrefsStore
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the vehicle screen. Owns the [ZkrRepository], exposes [uiState] and
 * per-[CommandKind] [commandStates], and refreshes status on demand (the screen
 * calls [refresh] only while RESUMED — see VehicleScreen).
 */
class ZkrViewModel(
    private val repo: ZkrRepository?,
    private val statusCache: StatusCache? = null,
    private val uiPrefs: UiPrefsStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VehicleUiState>(
        if (repo == null) VehicleUiState.NotConfigured else VehicleUiState.Loading,
    )
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    /** Which action buttons the user has chosen to show. All enabled by default. */
    private val _enabledSlots = MutableStateFlow(
        uiPrefs?.enabledSlots() ?: ActionSlot.entries.toSet(),
    )
    val enabledSlots: StateFlow<Set<ActionSlot>> = _enabledSlots.asStateFlow()

    fun toggleSlot(slot: ActionSlot) {
        val next = _enabledSlots.value.toMutableSet().apply {
            if (!add(slot)) remove(slot)
        }
        _enabledSlots.value = next
        uiPrefs?.setEnabled(next)
    }

    private val _commandStates = MutableStateFlow(
        CommandKind.entries.associateWith { CommandState.Idle } as Map<CommandKind, CommandState>,
    )
    val commandStates: StateFlow<Map<CommandKind, CommandState>> = _commandStates.asStateFlow()

    private var vin: String? = null
    private var refreshJob: Job? = null

    fun refresh() {
        val repo = repo ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            try {
                if (_uiState.value !is VehicleUiState.Ready) _uiState.value = VehicleUiState.Loading
                repo.connect()
                val v = vin ?: repo.firstVin().also { vin = it }
                val status = repo.statusWithExtras(v)
                statusCache?.write(v, status)
                _uiState.value = VehicleUiState.Ready(v, status)
            } catch (e: Exception) {
                _uiState.value = VehicleUiState.Error(friendlyError(e))
            }
        }
    }

    /**
     * Execute a command. Unlock is confirmed by the slide-to-confirm control in
     * the UI before this is called, so there is no extra confirm step here.
     */
    fun onAction(kind: CommandKind) {
        val repo = repo ?: return
        val v = vin ?: return
        setCommand(kind, CommandState.Pending)
        viewModelScope.launch {
            try {
                val ok = when (kind) {
                    CommandKind.LOCK -> repo.lock(v)
                    CommandKind.UNLOCK -> repo.unlock(v)
                    CommandKind.TRUNK -> repo.openTrunk(v)
                    CommandKind.CLIMATE -> {
                        val active = (uiState.value as? VehicleUiState.Ready)?.status?.climateActive ?: false
                        repo.climate(v, on = !active)
                    }
                    CommandKind.SENTRY -> {
                        val active = (uiState.value as? VehicleUiState.Ready)?.status?.sentryActive ?: false
                        repo.setSentry(v, on = !active)
                    }
                }
                setCommand(kind, if (ok) CommandState.Success else CommandState.Failed("Car declined"))
                if (ok && kind == CommandKind.SENTRY) {
                    // The sentry state endpoint lags after a toggle, so an immediate refetch
                    // reads stale state (same reason HA writes optimistically). Flip locally;
                    // the next poll reconciles the real value.
                    (uiState.value as? VehicleUiState.Ready)?.let { ready ->
                        val flipped = ready.status.copy(sentryActive = !(ready.status.sentryActive ?: false))
                        statusCache?.write(v, flipped)
                        _uiState.value = VehicleUiState.Ready(v, flipped)
                    }
                } else {
                    // Reflect the car's new state.
                    repo.statusWithExtras(v).let {
                        statusCache?.write(v, it)
                        _uiState.value = VehicleUiState.Ready(v, it)
                    }
                }
            } catch (e: Exception) {
                setCommand(kind, CommandState.Failed(friendlyError(e)))
            } finally {
                viewModelScope.launch {
                    delay(RESULT_LINGER_MS)
                    if (_commandStates.value[kind] !is CommandState.Pending) setCommand(kind, CommandState.Idle)
                }
            }
        }
    }

    private fun setCommand(kind: CommandKind, state: CommandState) {
        _commandStates.update { it + (kind to state) }
    }

    /**
     * Turns a thrown error into a short, human message that distinguishes the cause:
     * a transport failure (no network / car's cloud unreachable) surfaces differently
     * from an expired session or a generic server error. A command the gateway
     * actively declined is reported as "Car declined" at the call site (no exception).
     */
    private fun friendlyError(e: Throwable): String = when {
        e is IOException -> "Car unreachable"
        e is AuthException -> "Sign-in expired"
        else -> e.message ?: "Failed"
    }

    companion object {
        private const val RESULT_LINGER_MS = 2_500L

        /**
         * Builds a repository from the runtime-imported [ConfigStore] (public,
         * keyless distribution — see ConfigActivity), falling back to baked
         * BuildConfig values for personal builds. Null when neither is set.
         */
        fun buildRepository(context: android.content.Context): ZkrRepository? {
            val store = ConfigStore(context)
            if (store.isConfigured()) {
                return ZkrClientFactory.createPersistent(
                    context, store.email()!!, store.password()!!, store.keys()!!, store.country(),
                )
            }
            // Fallback: keys baked at build time via keys.properties (personal builds).
            val keys = ZkrKeys(
                hmacAccessKey = BuildConfig.HMAC_ACCESS_KEY,
                hmacSecretKey = BuildConfig.HMAC_SECRET_KEY,
                passwordPublicKey = BuildConfig.PASSWORD_PUBLIC_KEY,
                prodSecret = BuildConfig.PROD_SECRET,
                vinKey = BuildConfig.VIN_KEY,
                vinIv = BuildConfig.VIN_IV,
            )
            val email = BuildConfig.ACCOUNT_EMAIL
            val password = BuildConfig.ACCOUNT_PASSWORD
            if (!keys.isComplete || email.isBlank() || password.isBlank()) return null
            return ZkrClientFactory.createPersistent(
                context, email, password, keys, BuildConfig.COUNTRY_CODE.ifBlank { "AU" },
            )
        }
    }
}
