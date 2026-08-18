package com.zkrwatch.presentation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.launch

/**
 * Personalization screen: a toggle per [ActionSlot] deciding which buttons appear
 * on the main screen. Reached from the "Buttons" chip; dismissed with the back
 * gesture or the Done chip.
 */
@Composable
fun SettingsScreen(
    enabledSlots: Set<ActionSlot>,
    onToggle: (ActionSlot) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    scope.launch { listState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, start = 10.dp, end = 10.dp),
        ) {
            item {
                Text(
                    text = "Buttons",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            items(ActionSlot.entries) { slot ->
                val checked = slot in enabledSlots
                ToggleChip(
                    checked = checked,
                    onCheckedChange = { onToggle(slot) },
                    label = { Text(slotLabel(slot)) },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(checked),
                            contentDescription = if (checked) "Shown" else "Hidden",
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
            item {
                Chip(
                    label = { Text("Done") },
                    onClick = onClose,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

private fun slotLabel(slot: ActionSlot): String = when (slot) {
    ActionSlot.LOCK -> "Lock / Unlock"
    ActionSlot.TRUNK -> "Trunk"
    ActionSlot.CLIMATE -> "Climate"
    ActionSlot.SENTRY -> "Sentry"
    ActionSlot.FLASH -> "Flash lights"
}
