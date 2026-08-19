package com.zkrwatch.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.ReduceMotion
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zkrwatch.data.cache.StatusCache
import com.zkrwatch.data.store.UiPrefsStore
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Wear OS 5 returns to the watch face after a few seconds of no touch
        // (ambient, then auto-resume). Keep this activity interactive while it
        // is in the foreground so login/status can finish and the user can tap.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { WearApp() }
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun WearApp() {
    val appContext = LocalContext.current.applicationContext
    val vm: ZkrViewModel = viewModel(factory = ZkrViewModelFactory(appContext))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val commands by vm.commandStates.collectAsStateWithLifecycle()
    val enabledSlots by vm.enabledSlots.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    // Confirmation haptics: a light tick when a command is sent, and a distinct
    // success/error buzz when the car responds — you often act without looking.
    val view = LocalView.current
    CommandHaptics(commands)

    // Poll status only while the screen is RESUMED; never in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // Foolproof setup: if the app isn't configured, show the instructions for a
    // minute then fully close, so a stale "Not set up" screen never lingers after
    // the setup script runs — the next launch reads the freshly imported keys.
    val context = LocalContext.current
    if (state is VehicleUiState.NotConfigured) {
        LaunchedEffect(Unit) {
            delay(NOT_CONFIGURED_TIMEOUT_MS)
            context.findActivity()?.finishAndRemoveTask()
        }
    }

    // Wear Compose 1.3.1 reads Settings.Global.reduce_motion inside ScalingLazyColumn.
    // On Wear OS 5.1 (API 35) that hidden key is unreadable by targetSdk-35 apps and
    // throws SecurityException, which looked like "the app opened then instantly
    // vanished." Read it defensively: honor the user's accessibility preference where
    // the read succeeds (Wear OS 4 and earlier), and fall back to "motion on" only
    // when the platform blocks the read.
    val reduceMotion = remember(context) {
        val enabled = try {
            Settings.Global.getFloat(context.contentResolver, "reduce_motion", 0f) == 1f
        } catch (_: SecurityException) {
            false
        }
        ReduceMotion { enabled }
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        ZkrTheme {
            if (showSettings) {
                BackHandler { showSettings = false }
                // Standard Wear edge-swipe-to-dismiss: drag the settings screen off to
                // go back, with the native follow-the-finger animation.
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissValue.Dismissed) {
                        showSettings = false
                        dismissState.snapTo(SwipeToDismissValue.Default)
                    }
                }
                SwipeToDismissBox(state = dismissState) { isBackground ->
                    if (isBackground) {
                        Box(Modifier.fillMaxSize())
                    } else {
                        SettingsScreen(
                            enabledSlots = enabledSlots,
                            onToggle = vm::toggleSlot,
                            onClose = { showSettings = false },
                        )
                    }
                }
            } else {
                VehicleScreen(
                    state = state,
                    commands = commands,
                    enabledSlots = enabledSlots,
                    onAction = { kind ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        vm.onAction(kind)
                    },
                    onRetry = vm::refresh,
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
}

/**
 * Fires a system confirm/reject haptic when a command settles — CONFIRM on success,
 * REJECT on failure — by watching each [CommandKind]'s state for a transition. Uses
 * the OS haptic constants so it respects the user's system haptic setting.
 */
@Composable
private fun CommandHaptics(commands: Map<CommandKind, CommandState>) {
    val view = LocalView.current
    val previous = remember { mutableStateMapOf<CommandKind, CommandState>() }
    LaunchedEffect(commands) {
        commands.forEach { (kind, state) ->
            if (previous[kind] != state) {
                when (state) {
                    is CommandState.Success -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    is CommandState.Failed -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    else -> {}
                }
                previous[kind] = state
            }
        }
    }
}

private const val POLL_INTERVAL_MS = 60_000L
private const val NOT_CONFIGURED_TIMEOUT_MS = 60_000L

/** Walk up the ContextWrapper chain to the hosting Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Builds [ZkrViewModel] with a repository from baked-in config (or null). */
class ZkrViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ZkrViewModel(ZkrViewModel.buildRepository(context), StatusCache(context), UiPrefsStore(context)) as T
}
