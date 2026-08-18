package com.zkrwatch.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonColors
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.zkrwatch.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Main transactional screen: compact SOC hero + a row of large icon actions
 * (Lock/Unlock, Trunk, Climate). Unlock and Trunk require a slide-to-confirm.
 * Supports the rotating bezel / crown via [onRotaryScrollEvent].
 */
@Composable
fun VehicleScreen(
    state: VehicleUiState,
    commands: Map<CommandKind, CommandState>,
    enabledSlots: Set<ActionSlot>,
    onAction: (CommandKind) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Anchor from the first item (default is 1, which jams the SOC hero under the
    // TimeText clock); combined with autoCentering=null this top-aligns the content.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        when (state) {
            is VehicleUiState.Loading -> CenterMessage { CircularProgressIndicator() }
            is VehicleUiState.NotConfigured -> CenterMessage {
                Message(
                    "Not set up",
                    "On your PC, run setup (setup.ps1 / setup.sh) with your keys.txt, " +
                        "then reopen this app.\n\nClosing in 1 min…",
                )
            }
            is VehicleUiState.Error -> CenterMessage {
                Chip(
                    label = { Text("Retry") },
                    secondaryLabel = { Text(state.message, maxLines = 2) },
                    onClick = onRetry,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is VehicleUiState.Ready ->
                ReadyContent(state, commands, enabledSlots, onAction, onOpenSettings, listState)
        }
    }
}

@Composable
private fun ReadyContent(
    state: VehicleUiState.Ready,
    commands: Map<CommandKind, CommandState>,
    enabledSlots: Set<ActionSlot>,
    onAction: (CommandKind) -> Unit,
    onOpenSettings: () -> Unit,
    listState: ScalingLazyListState,
) {
    val s = state.status
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    // Give the list focus so the rotating bezel / crown scrolls it.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
        // Anchor from the top (no auto-centering) so contentPadding.top actually
        // applies — clears the curved TimeText clock so the SOC hero never collides
        // with it, while keeping the action row within easy reach.
        autoCentering = null,
        contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp, start = 8.dp, end = 8.dp),
    ) {
        item {
            SocHero(
                soc = s.socPercent,
                rangeKm = s.rangeKm,
                locked = s.locked,
                charging = s.charging == true,
                chargePowerKw = s.chargePowerKw,
            )
        }
        item {
            ActionCluster(
                status = s,
                commands = commands,
                enabledSlots = enabledSlots,
                onAction = onAction,
            )
        }
        item {
            CompactChip(
                onClick = onOpenSettings,
                label = { Text("Buttons") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SocHero(
    soc: Int?,
    rangeKm: Int?,
    locked: Boolean?,
    charging: Boolean,
    chargePowerKw: Double?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = soc?.toString() ?: "—",
                style = MaterialTheme.typography.display3.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                ),
            )
            Text(
                text = "%",
                style = MaterialTheme.typography.caption2,
                color = ZkrGrey,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
            )
            if (charging) {
                Spacer(Modifier.width(3.dp))
                PulsingBolt(Modifier.padding(bottom = 2.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        BatteryBar(soc, charging = charging)
        Spacer(Modifier.height(2.dp))
        if (charging) {
            // Clearly green while charging, with the live rate when available.
            Text(
                text = buildString {
                    append("Charging")
                    chargePowerKw?.let { append(" · ${formatKw(it)} kW") }
                    rangeKm?.let { append(" · $it km") }
                },
                style = MaterialTheme.typography.caption2,
                color = ZkrGreen,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = buildString {
                    append(rangeKm?.let { "$it km" } ?: "— km")
                    locked?.let { append(if (it) " · Locked" else " · Unlocked") }
                },
                style = MaterialTheme.typography.caption2,
                color = ZkrGrey,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatKw(kw: Double): String =
    if (kw >= 10) kw.roundToInt().toString() else String.format("%.1f", kw)

/** A green lightning bolt that gently pulses — the "charging" cue. */
@Composable
private fun PulsingBolt(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bolt")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "boltAlpha",
    )
    Icon(
        painter = painterResource(R.drawable.ic_bolt),
        contentDescription = "Charging",
        tint = ZkrGreen,
        modifier = modifier.size(16.dp).alpha(alpha),
    )
}

/** Thin green charge bar over a faint track — pulses gently while charging. */
@Composable
private fun BatteryBar(soc: Int?, charging: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "bar").animateFloat(
        initialValue = if (charging) 0.5f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "barAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .height(5.dp)
            .clip(CircleShape)
            .background(ZkrGrey.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(((soc ?: 0).coerceIn(0, 100)) / 100f)
                .height(5.dp)
                .clip(CircleShape)
                .alpha(if (charging) pulse else 1f)
                .background(ZkrGreen),
        )
    }
}

/**
 * The user's enabled icon actions, in [ActionSlot] order. Unlock and Trunk require
 * a slide-to-confirm (which replaces the cluster while active); Lock, Climate and
 * Sentry fire immediately. Up to three slots sit on one row; four wrap to a
 * balanced 2 + 2 so nothing overflows a small round screen.
 */
@Composable
private fun ActionCluster(
    status: com.zkrwatch.data.model.VehicleStatus,
    commands: Map<CommandKind, CommandState>,
    enabledSlots: Set<ActionSlot>,
    onAction: (CommandKind) -> Unit,
) {
    val locked = status.locked

    // When set, a slide-to-confirm is shown for this action instead of the cluster.
    var confirm by remember(locked) { mutableStateOf<CommandKind?>(null) }
    // Auto-dismiss the confirm slider if untouched.
    LaunchedEffect(confirm) {
        if (confirm != null) {
            kotlinx.coroutines.delay(6000)
            confirm = null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        if (confirm != null) {
            val label = if (confirm == CommandKind.TRUNK) "Slide to open trunk" else "Slide to unlock"
            SlideToConfirm(text = label) {
                val kind = confirm!!
                confirm = null
                onAction(kind)
            }
        } else {
            val slots = ActionSlot.entries.filter { it in enabledSlots }
            if (slots.isNotEmpty()) {
                // ≤3 slots stay on one row (unchanged layout); 4 wrap to 2 + 2.
                val twoByTwo = slots.size == 4
                val perRow = if (slots.size <= 3) slots.size else 2
                slots.chunked(perRow).forEachIndexed { index, rowSlots ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(
                        // The 2×2 grid clusters toward the centre (rather than spreading
                        // edge-to-edge) so the round screen's curve never clips the
                        // corner buttons; single rows of ≤3 keep the wide spread.
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = if (twoByTwo) 0.dp else 18.dp),
                        horizontalArrangement = when {
                            rowSlots.size == 1 -> Arrangement.Center
                            twoByTwo -> Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                            else -> Arrangement.SpaceBetween
                        },
                    ) {
                        rowSlots.forEach { slot ->
                            SlotButton(
                                slot = slot,
                                status = status,
                                onAction = onAction,
                                onConfirm = { confirm = it },
                            )
                        }
                    }
                }
            }
        }

        statusLine(commands)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.caption2, color = ZkrGrey, maxLines = 1)
        }
    }
}

/** Renders one [ActionSlot] as its icon button, wiring immediate vs slide-to-confirm behavior. */
@Composable
private fun SlotButton(
    slot: ActionSlot,
    status: com.zkrwatch.data.model.VehicleStatus,
    onAction: (CommandKind) -> Unit,
    onConfirm: (CommandKind) -> Unit,
) {
    when (slot) {
        // The icon shows current lock STATUS: an orange open padlock warns the car is
        // unlocked (tap to lock); a white closed padlock means locked (tap to unlock,
        // via slide-to-confirm).
        ActionSlot.LOCK ->
            if (status.locked == false) {
                IconAction(R.drawable.ic_lock_open, "Lock", ZkrOrange) { onAction(CommandKind.LOCK) }
            } else {
                IconAction(R.drawable.ic_lock, "Unlock") { onConfirm(CommandKind.UNLOCK) }
            }
        ActionSlot.TRUNK ->
            IconAction(R.drawable.ic_trunk, "Trunk") { onConfirm(CommandKind.TRUNK) }
        // Active state is signaled by the orange (primary) button fill; the icon stays
        // white so it's always legible (an orange tint on the orange fill would vanish).
        ActionSlot.CLIMATE -> {
            val on = status.climateActive == true
            IconAction(
                iconRes = R.drawable.ic_climate,
                label = "Climate",
                colors = if (on) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
            ) { onAction(CommandKind.CLIMATE) }
        }
        ActionSlot.SENTRY -> {
            val on = status.sentryActive == true
            IconAction(
                iconRes = R.drawable.ic_sentry,
                label = "Sentry",
                colors = if (on) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
            ) { onAction(CommandKind.SENTRY) }
        }
        // Momentary locate: flashes the blinkers. Fires immediately (harmless).
        ActionSlot.FLASH ->
            IconAction(R.drawable.ic_flash, "Flash") { onAction(CommandKind.FLASH) }
    }
}

/** A big circular icon button (label used as accessibility description). */
@Composable
private fun IconAction(
    iconRes: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colors.onSurface,
    colors: ButtonColors = ButtonDefaults.secondaryButtonColors(),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = colors,
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Most-recent command status as a short line, or null when all idle. */
private fun statusLine(commands: Map<CommandKind, CommandState>): String? {
    val entry = commands.entries.firstOrNull { it.value !is CommandState.Idle } ?: return null
    return when (val st = entry.value) {
        CommandState.Pending -> when (entry.key) {
            CommandKind.LOCK -> "Locking…"
            CommandKind.UNLOCK -> "Unlocking…"
            CommandKind.TRUNK -> "Opening trunk…"
            CommandKind.CLIMATE -> "Climate…"
            CommandKind.SENTRY -> "Sentry…"
            CommandKind.FLASH -> "Flashing…"
        }
        CommandState.Success -> "Done ✓"
        is CommandState.Failed -> st.message
        else -> null
    }
}

/** Drag the thumb to the end to confirm — prevents accidental taps from the wrist. */
@Composable
private fun SlideToConfirm(text: String, onConfirm: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thumb = 44.dp
    val offsetX = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colors.surface),
        contentAlignment = Alignment.CenterStart,
    ) {
        val endPx = with(density) { (maxWidth - thumb).toPx() }.coerceAtLeast(1f)
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption1,
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary)
                .pointerInput(endPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            scope.launch { offsetX.snapTo((offsetX.value + drag).coerceIn(0f, endPx)) }
                        },
                        onDragEnd = {
                            if (offsetX.value >= endPx * 0.9f) onConfirm()
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) { Text("›", style = MaterialTheme.typography.title2) }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Message(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
    }
}
