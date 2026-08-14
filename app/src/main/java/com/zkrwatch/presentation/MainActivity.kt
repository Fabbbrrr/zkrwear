package com.zkrwatch.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zkrwatch.data.cache.StatusCache
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    val appContext = LocalContext.current.applicationContext
    val vm: ZkrViewModel = viewModel(factory = ZkrViewModelFactory(appContext))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val commands by vm.commandStates.collectAsStateWithLifecycle()

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

    ZkrTheme {
        VehicleScreen(
            state = state,
            commands = commands,
            onAction = vm::onAction,
            onRetry = vm::refresh,
        )
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
        ZkrViewModel(ZkrViewModel.buildRepository(context), StatusCache(context)) as T
}
