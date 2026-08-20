package com.zkrwatch.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlin.math.ceil
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.zkrwatch.BuildConfig
import com.zkrwatch.R
import com.zkrwatch.data.update.UpdateInfo
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
    enabledSlots: List<ActionSlot>,
    onAction: (CommandKind) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onMoveSlot: (Int, Int) -> Unit = { _, _ -> },
    onRemoveSlot: (ActionSlot) -> Unit = {},
    update: UpdateInfo? = null,
    updating: Boolean = false,
    onInstallUpdate: () -> Unit = {},
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
            is VehicleUiState.Loading -> CenterMessage {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Connecting…", textAlign = TextAlign.Center)
                }
            }
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
                ReadyContent(
                    state, commands, enabledSlots, onAction, onOpenSettings, onRetry,
                    onMoveSlot, onRemoveSlot, update, updating, onInstallUpdate, listState,
                )
        }
    }
}

@Composable
private fun ReadyContent(
    state: VehicleUiState.Ready,
    commands: Map<CommandKind, CommandState>,
    enabledSlots: List<ActionSlot>,
    onAction: (CommandKind) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onMoveSlot: (Int, Int) -> Unit,
    onRemoveSlot: (ActionSlot) -> Unit,
    update: UpdateInfo?,
    updating: Boolean,
    onInstallUpdate: () -> Unit,
    listState: ScalingLazyListState,
) {
    val s = state.status
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    // Give the list focus so the rotating bezel / crown scrolls it.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Long-press any action button to rearrange them (wobble + drag + remove).
    var editMode by remember { mutableStateOf(false) }
    // Auto-leave edit mode once nothing is left to arrange.
    LaunchedEffect(enabledSlots.isEmpty()) { if (enabledSlots.isEmpty()) editMode = false }
    BackHandler(enabled = editMode) { editMode = false }

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
                updatedAt = state.updatedAt,
                onRefresh = onRefresh,
                timeToFullMinutes = s.timeToFullMinutes,
            )
        }
        item {
            ActionCluster(
                status = s,
                commands = commands,
                enabledSlots = enabledSlots,
                editMode = editMode,
                onAction = onAction,
                onEnterEdit = { editMode = true },
                onMove = onMoveSlot,
                onRemove = onRemoveSlot,
            )
        }
        item {
            // In edit mode this chip becomes "Done"; otherwise it opens settings.
            CompactChip(
                onClick = { if (editMode) editMode = false else onOpenSettings() },
                label = { Text(if (editMode) "Done" else "Buttons") },
                colors = if (editMode) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Only shown when a newer GitHub release exists — a single tap downloads and
        // installs it. No banner or prompt, so it's never in the way when up to date.
        if (update != null && !editMode) {
            item {
                Chip(
                    onClick = { if (!updating) onInstallUpdate() },
                    label = { Text(if (updating) "Downloading…" else "Update to ${update.versionName}") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        // Installed version, small and unobtrusive at the very bottom.
        item {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.caption2,
                color = ZkrGrey.copy(alpha = 0.6f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
    updatedAt: Long,
    onRefresh: () -> Unit,
    timeToFullMinutes: Int?,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Long-press the hero to force a status refresh (the poll is otherwise ~60s),
        // so you can confirm fresh state right before walking to the car.
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRefresh()
                },
            )
        },
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
                    timeToFullMinutes?.takeIf { it > 0 }?.let { append(" · full in ${formatEta(it)}") }
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
        RelativeUpdated(updatedAt)
    }
}

/** Tiny "updated Xm ago" freshness cue — remote data lags, so show how fresh it is. */
@Composable
private fun RelativeUpdated(updatedAt: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    // Re-tick so the label keeps counting up even if polling pauses.
    LaunchedEffect(updatedAt) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(20_000)
        }
    }
    Text(
        text = relativeUpdatedLabel((now - updatedAt).coerceAtLeast(0)),
        style = MaterialTheme.typography.caption2,
        color = ZkrGrey.copy(alpha = 0.7f),
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
    )
}

private fun relativeUpdatedLabel(deltaMs: Long): String {
    val mins = deltaMs / 60_000
    return when {
        deltaMs < 45_000 -> "Updated just now"
        mins < 60 -> "Updated ${mins.coerceAtLeast(1)}m ago"
        else -> "Updated ${mins / 60}h ago"
    }
}

private fun formatKw(kw: Double): String =
    if (kw >= 10) kw.roundToInt().toString() else String.format("%.1f", kw)

/** Minutes-to-full as a compact "45m" / "2h05" for the charging line. */
private fun formatEta(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h${String.format("%02d", minutes % 60)}"

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

/** Thin charge bar over a faint track — pulses green while charging, and turns
 *  amber/red at a low state of charge so a near-flat battery is obvious at a glance. */
@Composable
private fun BatteryBar(soc: Int?, charging: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "bar").animateFloat(
        initialValue = if (charging) 0.5f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "barAlpha",
    )
    // Charging always reads green; otherwise warn as the battery gets low.
    val fill = when {
        charging -> ZkrGreen
        soc != null && soc <= 10 -> ZkrRed
        soc != null && soc <= 20 -> ZkrAmber
        else -> ZkrGreen
    }
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
                .background(fill),
        )
    }
}

/**
 * The user's enabled icon actions, in [ActionSlot] order. Unlock and Trunk require
 * a slide-to-confirm (which replaces the cluster while active); Lock, Climate and
 * Sentry fire immediately (Sentry shows On/Off and toggles). Up to three slots
 * sit on one row; four or more wrap to centred rows of two so nothing overflows
 * a small round screen.
 */
@Composable
private fun ActionCluster(
    status: com.zkrwatch.data.model.VehicleStatus,
    commands: Map<CommandKind, CommandState>,
    enabledSlots: List<ActionSlot>,
    editMode: Boolean,
    onAction: (CommandKind) -> Unit,
    onEnterEdit: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (ActionSlot) -> Unit,
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
        if (editMode && enabledSlots.isNotEmpty()) {
            // Rearrange mode: wobbling, draggable buttons with a remove badge. Taps
            // do nothing here (the buttons are inert) so no action fires while editing.
            EditableActionGrid(
                slots = enabledSlots,
                status = status,
                onMove = onMove,
                onRemove = onRemove,
            )
        } else if (confirm != null) {
            val label = if (confirm == CommandKind.TRUNK) "Slide to open trunk" else "Slide to unlock"
            SlideToConfirm(text = label) {
                val kind = confirm!!
                confirm = null
                onAction(kind)
            }
        } else if (enabledSlots.isEmpty()) {
            // Every button is hidden — point the user at where to bring them back.
            Text(
                text = "All buttons hidden.\nTap Buttons to add controls.",
                style = MaterialTheme.typography.caption2,
                color = ZkrGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        } else {
            val slots = enabledSlots
            // ≤3 slots stay on one row (wide spread); 4+ wrap to rows of two.
            val wrap = slots.size > 3
            val perRow = if (wrap) 2 else slots.size
            slots.chunked(perRow).forEachIndexed { index, rowSlots ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(
                    // Wrapped rows (4+ slots) are centred with a small gap so the
                    // buttons grow outward from the middle instead of hugging the rim
                    // of the round screen; a lone trailing button centres too. Single
                    // rows of ≤3 keep the wide edge-to-edge spread.
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = if (wrap) 0.dp else 18.dp),
                    horizontalArrangement = when {
                        rowSlots.size == 1 -> Arrangement.Center
                        wrap -> Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        else -> Arrangement.SpaceBetween
                    },
                ) {
                    rowSlots.forEach { slot ->
                        SlotButton(
                            slot = slot,
                            status = status,
                            commands = commands,
                            onAction = onAction,
                            onConfirm = { confirm = it },
                            onLongPress = onEnterEdit,
                        )
                    }
                }
            }
        }

        if (editMode) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Drag to reorder · – to remove",
                style = MaterialTheme.typography.caption2,
                color = ZkrGrey,
                maxLines = 1,
            )
        } else {
            statusLine(commands, status)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.caption2, color = ZkrGrey, maxLines = 1)
            }
        }
    }
}

/**
 * Edit-mode grid: the enabled [slots] laid out in the same wrap pattern as normal,
 * but each button wobbles, carries a remove (–) badge, and can be dragged to a new
 * cell to reorder. A single pointer handler on the grid owns the drag so coordinates
 * stay in one space; the buttons themselves are inert, so a tap never fires an action.
 */
@Composable
private fun EditableActionGrid(
    slots: List<ActionSlot>,
    status: com.zkrwatch.data.model.VehicleStatus,
    onMove: (Int, Int) -> Unit,
    onRemove: (ActionSlot) -> Unit,
) {
    val cols = if (slots.size <= 3) slots.size.coerceAtLeast(1) else 2
    val rows = ceil(slots.size / cols.toFloat()).toInt().coerceAtLeast(1)
    val button = 56.dp
    val gap = 16.dp
    val density = LocalDensity.current
    val stepPx = with(density) { (button + gap).toPx() }
    val halfPx = with(density) { (button / 2).toPx() }
    val gridW = button * cols + gap * (cols - 1)
    val gridH = button * rows + gap * (rows - 1)

    fun baseOffset(i: Int) = Offset((i % cols) * stepPx, (i / cols) * stepPx)
    fun indexAt(p: Offset): Int? {
        val c = (p.x / stepPx).toInt()
        val r = (p.y / stepPx).toInt()
        if (c < 0 || c >= cols || r < 0 || r >= rows) return null
        return (r * cols + c).takeIf { it in slots.indices }
    }

    var dragSlot by remember { mutableStateOf<ActionSlot?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    // The gesture handler isn't restarted on reorder (only on count change), so read
    // the live order through this rather than the captured `slots`.
    val liveSlots = rememberUpdatedState(slots)

    Box(
        modifier = Modifier
            .size(gridW, gridH)
            .pointerInput(slots.size) {
                detectDragGestures(
                    onDragStart = { pos ->
                        indexAt(pos)?.let { idx ->
                            dragSlot = liveSlots.value[idx]
                            dragPos = pos
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPos = change.position
                        val from = dragSlot?.let { liveSlots.value.indexOf(it) }
                        val to = indexAt(dragPos)
                        if (from != null && from >= 0 && to != null && to != from) onMove(from, to)
                    },
                    onDragEnd = { dragSlot = null },
                    onDragCancel = { dragSlot = null },
                )
            },
    ) {
        slots.forEachIndexed { index, slot ->
            key(slot) {
                val dragging = slot == dragSlot
                val target = if (dragging) dragPos - Offset(halfPx, halfPx) else baseOffset(index)
                // Non-dragged buttons slide to their new cell; the dragged one tracks the finger.
                val animated by animateOffsetAsState(target, label = "slotOffset")
                val pos = if (dragging) target else animated
                val wobble = rememberWobble(index)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { rotationZ = if (dragging) 0f else wobble },
                ) {
                    SlotButton(
                        slot = slot,
                        status = status,
                        commands = emptyMap(),
                        interactive = false,
                        onAction = {},
                        onConfirm = {},
                    )
                    RemoveBadge(onClick = { onRemove(slot) })
                }
            }
        }
    }
}

/** A gentle continuous tilt for edit mode; the per-item [seed] desyncs the phase. */
@Composable
private fun rememberWobble(seed: Int): Float {
    val transition = rememberInfiniteTransition(label = "wobble")
    val angle by transition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(160 + (seed % 3) * 30, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobbleAngle",
    )
    return angle
}

/** White circle with a minus sign, top-left of a button in edit mode; tap to remove. */
@Composable
private fun BoxScope.RemoveBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .size(20.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 10.dp, height = 2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(androidx.compose.ui.graphics.Color.Black),
        )
    }
}

/**
 * Renders one [ActionSlot] as its icon button, wiring immediate vs slide-to-confirm
 * behaviour. A long press starts edit mode via [onLongPress]. When [interactive] is
 * false (edit mode) the button is inert — taps and drags never fire the action.
 */
@Composable
private fun SlotButton(
    slot: ActionSlot,
    status: com.zkrwatch.data.model.VehicleStatus,
    commands: Map<CommandKind, CommandState>,
    onAction: (CommandKind) -> Unit,
    onConfirm: (CommandKind) -> Unit,
    interactive: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    fun pending(vararg kinds: CommandKind) = kinds.any { commands[it] is CommandState.Pending }
    val onLong = if (interactive) onLongPress else null
    // Null onClick => inert (edit mode): no action on tap, and drag reaches the grid.
    fun click(block: () -> Unit): (() -> Unit)? = if (interactive) block else null
    when (slot) {
        // The icon shows current lock STATUS: an orange open padlock warns the car is
        // unlocked (tap to lock); a white closed padlock means locked (tap to unlock,
        // via slide-to-confirm).
        ActionSlot.LOCK ->
            if (status.locked == false) {
                IconAction(
                    R.drawable.ic_lock_open, "Lock", tint = ZkrOrange,
                    pending = pending(CommandKind.LOCK, CommandKind.UNLOCK),
                    onClick = click { onAction(CommandKind.LOCK) },
                    onLongClick = onLong,
                )
            } else {
                IconAction(
                    R.drawable.ic_lock, "Unlock",
                    pending = pending(CommandKind.LOCK, CommandKind.UNLOCK),
                    onClick = click { onConfirm(CommandKind.UNLOCK) },
                    onLongClick = onLong,
                )
            }
        ActionSlot.TRUNK ->
            IconAction(
                R.drawable.ic_trunk, "Trunk",
                pending = pending(CommandKind.TRUNK),
                onClick = click { onConfirm(CommandKind.TRUNK) },
                onLongClick = onLong,
            )
        // Active state is signaled by the orange (primary) button fill; the icon stays
        // white so it's always legible (an orange tint on the orange fill would vanish).
        // Shows the cabin temperature under the icon when known, for climate context.
        ActionSlot.CLIMATE ->
            ClimateButton(
                on = status.climateActive == true,
                interiorTempC = status.interiorTempC,
                pending = pending(CommandKind.CLIMATE),
                onClick = click { onAction(CommandKind.CLIMATE) },
                onLongClick = onLong,
            )
        ActionSlot.SENTRY ->
            SentryButton(
                on = status.sentryActive,
                pending = pending(CommandKind.SENTRY),
                onClick = click { onAction(CommandKind.SENTRY) },
                onLongClick = onLong,
            )
        // Momentary locate: flashes the blinkers. Fires immediately (harmless).
        ActionSlot.FLASH ->
            IconAction(
                R.drawable.ic_flash, "Flash",
                onClick = click { onAction(CommandKind.FLASH) },
                onLongClick = onLong,
            )
        // Toggles charging; orange while the car is actively charging.
        ActionSlot.CHARGING ->
            IconAction(
                iconRes = R.drawable.ic_charge,
                label = "Charge",
                filled = status.charging == true,
                onClick = click { onAction(CommandKind.CHARGING) },
                onLongClick = onLong,
            )
    }
}

/**
 * Climate button — like [IconAction] but stacks the cabin temperature under the
 * icon when it's known (e.g. "22°"), giving climate control real context at a glance.
 * Shows the same in-flight progress ring as [IconAction] while [pending].
 */
@Composable
private fun ClimateButton(
    on: Boolean,
    interiorTempC: Double?,
    pending: Boolean = false,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
) {
    Box(contentAlignment = Alignment.Center) {
        ActionButton(filled = on, onClick = if (pending) null else onClick, onLongClick = onLongClick) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_climate),
                    contentDescription = "Climate",
                    tint = MaterialTheme.colors.onSurface,
                    modifier = Modifier.size(if (interiorTempC != null) 24.dp else 30.dp),
                )
                if (interiorTempC != null) {
                    Text(
                        text = "${interiorTempC.roundToInt()}°",
                        style = MaterialTheme.typography.caption2,
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
            }
        }
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                strokeWidth = 3.dp,
                indicatorColor = ZkrOrange,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }
}

/**
 * Sentry toggle — like [ClimateButton], but stacks On/Off under the shield so the
 * live surveillance state is readable at a glance. Orange fill means armed; tap
 * flips the car (unknown/`null` is treated as off, so the first tap arms it).
 */
@Composable
private fun SentryButton(
    on: Boolean?,
    pending: Boolean = false,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
) {
    val armed = on == true
    val stateLabel = when (on) {
        true -> "On"
        false -> "Off"
        null -> "—"
    }
    Box(contentAlignment = Alignment.Center) {
        ActionButton(filled = armed, onClick = if (pending) null else onClick, onLongClick = onLongClick) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sentry),
                    contentDescription = "Sentry $stateLabel",
                    tint = MaterialTheme.colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.caption2,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onSurface,
                )
            }
        }
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                strokeWidth = 3.dp,
                indicatorColor = ZkrOrange,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }
}

/**
 * A big circular icon button (label used as accessibility description). While the
 * command is in flight, an indeterminate ring is drawn around it and taps are
 * ignored — a glanceable in-place progress cue that also blocks double-sends.
 */
@Composable
private fun IconAction(
    iconRes: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colors.onSurface,
    filled: Boolean = false,
    pending: Boolean = false,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
) {
    Box(contentAlignment = Alignment.Center) {
        ActionButton(filled = filled, onClick = if (pending) null else onClick, onLongClick = onLongClick) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(30.dp),
            )
        }
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                strokeWidth = 3.dp,
                indicatorColor = ZkrOrange,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        }
    }
}

/**
 * Circular action surface matching the Wear button look: orange [filled] fill when
 * active, else the dark secondary surface. A null [onClick] renders it inert (edit
 * mode) so a tap fires nothing and a drag passes through to the reorder grid;
 * [onLongClick] enters edit mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    filled: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val bg = if (filled) MaterialTheme.colors.primary else MaterialTheme.colors.surface
    val base = Modifier.size(56.dp).clip(CircleShape).background(bg)
    val clickable = if (onClick != null) {
        base.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        base
    }
    Box(modifier = clickable, contentAlignment = Alignment.Center, content = content)
}

/** Most-recent command status as a short line, or null when all idle. */
private fun statusLine(
    commands: Map<CommandKind, CommandState>,
    status: com.zkrwatch.data.model.VehicleStatus,
): String? {
    val entry = commands.entries.firstOrNull { it.value !is CommandState.Idle } ?: return null
    return when (val st = entry.value) {
        CommandState.Pending -> when (entry.key) {
            CommandKind.LOCK -> "Locking…"
            CommandKind.UNLOCK -> "Unlocking…"
            CommandKind.TRUNK -> "Opening trunk…"
            CommandKind.CLIMATE -> "Climate…"
            CommandKind.SENTRY -> if (status.sentryActive == true) "Disarming…" else "Arming…"
            CommandKind.FLASH -> "Flashing…"
            CommandKind.CHARGING -> "Charge…"
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
