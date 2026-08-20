package com.zkrwatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkrwatch.BuildConfig
import com.zkrwatch.data.ZkrClientFactory
import com.zkrwatch.data.cache.StatusCache
import com.zkrwatch.data.model.VehicleStatus
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
        uiPrefs?.enabledSlots() ?: ActionSlot.entries.filter { it.defaultVisible }.toSet(),
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
                android.util.Log.w(TAG, "refresh failed: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = VehicleUiState.Error(friendlyError(e))
            }
        }
    }

    /**
     * Execute a command. Unlock is confirmed by the slide-to-confirm control in
     * the UI before this is called, so there is no extra confirm step here.
     *
     * The car's cloud state lags a command by several seconds, so a single
     * immediate refetch reads stale state. Instead, for commands whose result is
     * observable in [VehicleStatus] (lock, climate, sentry, charging) we hold the
     * button in [CommandState.Pending] — spinner still turning — and re-poll with a
     * growing backoff until the car actually reflects the change (or 1 min elapses).
     * The success buzz then fires only when the new state truly lands.
     */
    fun onAction(kind: CommandKind) {
        val repo = repo ?: return
        val v = vin ?: return
        // Snapshot the state at tap time: it defines what "changed" means, and what
        // on/off value each toggle should send.
        val before = (uiState.value as? VehicleUiState.Ready)?.status
        setCommand(kind, CommandState.Pending)
        viewModelScope.launch {
            try {
                val ok = when (kind) {
                    CommandKind.LOCK -> repo.lock(v)
                    CommandKind.UNLOCK -> repo.unlock(v)
                    CommandKind.TRUNK -> repo.openTrunk(v)
                    CommandKind.CLIMATE -> repo.climate(v, on = !(before?.climateActive ?: false))
                    CommandKind.SENTRY -> repo.setSentry(v, on = !(before?.sentryActive ?: false))
                    CommandKind.FLASH -> repo.flashLights(v)
                    CommandKind.CHARGING -> repo.setCharging(v, on = !(before?.charging ?: false))
                }
                if (!ok) {
                    setCommand(kind, CommandState.Failed("Car declined"))
                    return@launch
                }
                if (observable(kind)) {
                    // Keep the spinner up and poll until the car reflects the change,
                    // so the button confirms only once the new state is real.
                    pollForChange(kind, before, v)
                } else if (kind != CommandKind.FLASH) {
                    // Trunk has no persistent state to watch; refetch once for freshness.
                    refetchInto(v)
                }
                setCommand(kind, CommandState.Success)
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

    /**
     * Re-poll status with a growing backoff (see [REFRESH_BACKOFF_MS]) for up to
     * [REFRESH_MAX_MS], pushing each fresh snapshot to the UI, and return as soon as
     * the car reflects the [kind] command (compared against the pre-tap [before]).
     * A failed poll is skipped, not fatal — the next interval tries again.
     */
    private suspend fun pollForChange(kind: CommandKind, before: VehicleStatus?, v: String) {
        val repo = repo ?: return
        val deadline = System.currentTimeMillis() + REFRESH_MAX_MS
        for (wait in REFRESH_BACKOFF_MS) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            delay(minOf(wait, remaining))
            val fresh = runCatching { repo.statusWithExtras(v) }.getOrNull() ?: continue
            statusCache?.write(v, fresh)
            _uiState.value = VehicleUiState.Ready(v, fresh)
            if (landed(kind, before, fresh)) return
        }
    }

    private suspend fun refetchInto(v: String) {
        val repo = repo ?: return
        repo.statusWithExtras(v).let {
            statusCache?.write(v, it)
            _uiState.value = VehicleUiState.Ready(v, it)
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
        private const val TAG = "ZkrWatch"
        private const val RESULT_LINGER_MS = 2_500L

        /**
         * Growing backoff between confirmation polls after a command (ms). Front-loaded
         * so a quick change is caught almost immediately, then coarser; the total spans
         * [REFRESH_MAX_MS] (the deadline caps the final wait).
         */
        private val REFRESH_BACKOFF_MS = longArrayOf(1_000, 5_000, 10_000, 15_000, 15_000, 15_000)

        /** Stop waiting for the car to reflect a command after this long. */
        private const val REFRESH_MAX_MS = 60_000L

        /**
         * Commands whose outcome is observable in [VehicleStatus], so we can poll to
         * confirm. Trunk (no persistent open-state field) and Flash (momentary) are not.
         */
        internal fun observable(kind: CommandKind): Boolean = when (kind) {
            CommandKind.LOCK, CommandKind.UNLOCK, CommandKind.CLIMATE,
            CommandKind.SENTRY, CommandKind.CHARGING -> true
            CommandKind.TRUNK, CommandKind.FLASH -> false
        }

        /**
         * Has the car reflected [kind] yet? Lock/Unlock target an absolute state;
         * the toggles (climate/sentry/charging) target the opposite of the pre-tap
         * [before] value (null pre-state is treated as off, matching the toggle).
         */
        internal fun landed(kind: CommandKind, before: VehicleStatus?, now: VehicleStatus): Boolean = when (kind) {
            CommandKind.LOCK -> now.locked == true
            CommandKind.UNLOCK -> now.locked == false
            CommandKind.CLIMATE -> now.climateActive == !(before?.climateActive ?: false)
            CommandKind.SENTRY -> now.sentryActive == !(before?.sentryActive ?: false)
            CommandKind.CHARGING -> now.charging == !(before?.charging ?: false)
            CommandKind.TRUNK, CommandKind.FLASH -> true
        }

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
