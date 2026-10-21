// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.settings.HudCorner
import io.github.mircodhd.mvplayer.settings.HudSettings
import kotlin.math.roundToInt

private val HudText = Color(0xFFF2F2F2)
private val HudDim = Color(0xFFB0B0B0)
private val Good = Color(0xFF9CCC65)
private val Warn = Color(0xFFFFCA28)
private val Bad = Color(0xFFFF6E6E)

private fun HudLevel?.color(): Color = when (this) {
    HudLevel.GOOD -> Good
    HudLevel.WARN -> Warn
    HudLevel.BAD -> Bad
    null -> HudText
}

/**
 * The HUD in its corner of the safe area, above the game and below menus. It draws only
 * when a new sample arrives and never takes touches, so controls underneath keep working.
 */
@Composable
fun HudOverlay(
    snapshot: HudSnapshot,
    settings: HudSettings,
    onTap: (() -> Unit)? = null,
    /** The player dragged the HUD; the new offset from its corner in dp. */
    onMove: ((Float, Float) -> Unit)? = null,
    onDrawNanos: (Long) -> Unit = {},
) {
    val density = LocalDensity.current
    var drag by remember { mutableStateOf(Offset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(8.dp),
    ) {
        val saved = with(density) { Offset(settings.offsetX.dp.toPx(), settings.offsetY.dp.toPx()) }
        val free = Offset(
            (constraints.maxWidth - panelSize.width).coerceAtLeast(0).toFloat(),
            (constraints.maxHeight - panelSize.height).coerceAtLeast(0).toFloat(),
        )
        val position = keepOnScreen(saved + drag, settings.corner, free)
        HudPanel(
            snapshot, settings,
            Modifier
                .align(settings.corner.alignment)
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                // The compact line wraps at the chosen width, which also leaves room to move sideways.
                .widthIn(max = maxWidth * (settings.widthPercent / 100f))
                .onSizeChanged { panelSize = it }
                .then(
                    if (onMove == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(settings.corner) {
                            detectDragGestures(
                                onDragEnd = {
                                    val end = keepOnScreen(saved + drag, settings.corner, free)
                                    drag = Offset.Zero
                                    with(density) { onMove(end.x.toDp().value, end.y.toDp().value) }
                                },
                                onDragCancel = { drag = Offset.Zero },
                            ) { change, amount ->
                                change.consume()
                                drag += amount
                            }
                        }
                    },
                ),
            onTap, onDrawNanos,
        )
    }
}

/** Limits an offset from [corner] so the panel stays inside the area ([free] = room left over). */
private fun keepOnScreen(offset: Offset, corner: HudCorner, free: Offset): Offset {
    val x = if (corner == HudCorner.TOP_START || corner == HudCorner.BOTTOM_START) offset.x.coerceIn(0f, free.x) else offset.x.coerceIn(-free.x, 0f)
    val y = if (corner == HudCorner.TOP_START || corner == HudCorner.TOP_END) offset.y.coerceIn(0f, free.y) else offset.y.coerceIn(-free.y, 0f)
    return Offset(x, y)
}

private val HudCorner.alignment: Alignment
    get() = when (this) {
        HudCorner.TOP_START -> Alignment.TopStart
        HudCorner.TOP_END -> Alignment.TopEnd
        HudCorner.BOTTOM_START -> Alignment.BottomStart
        HudCorner.BOTTOM_END -> Alignment.BottomEnd
    }

@Composable
fun HudPanel(
    snapshot: HudSnapshot,
    settings: HudSettings,
    modifier: Modifier = Modifier,
    /** Tapping the panel (in game: switch between the compact and the detailed layout). */
    onTap: (() -> Unit)? = null,
    onDrawNanos: (Long) -> Unit = {},
) {
    val res = LocalResources.current
    val scale = settings.size.scale
    val style = TextStyle(color = HudText, fontFamily = FontFamily.Monospace, fontSize = (11 * scale).sp, lineHeight = (14 * scale).sp)
    val modules = HudRegistry.resolve(settings.modules)
    val paused = snapshot.js?.paused == true

    Column(
        modifier
            .alpha(settings.opacity)
            .clip(RoundedCornerShape((8 * scale).dp))
            .then(if (onTap != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onTap) else Modifier)
            .background(Color.Black.copy(alpha = BACKGROUND_ALPHA))
            .drawWithContent {
                val start = System.nanoTime()
                drawContent()
                onDrawNanos(System.nanoTime() - start)
            }
            .padding(horizontal = (6 * scale).dp, vertical = (4 * scale).dp),
    ) {
        if (paused) Text(stringResource(R.string.hud_paused), style = style.copy(color = HudDim))
        val graphHeight = (26 * scale).dp
        val graphModifier = Modifier.padding(vertical = 2.dp).size((150 * scale).dp, graphHeight)
        if (settings.detailed) {
            // Two columns, so values line up whatever the longest label is in this language.
            // The graph module's row is as tall as the graph; its label sits at the top.
            val graphRow = graphHeight + 4.dp
            val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
            Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                Column {
                    for (module in modules) {
                        Text(module.label(res, settings.shortLabels), style = style.copy(color = HudDim))
                        if (module.graph) Spacer(Modifier.height((graphRow - lineHeight).coerceAtLeast(0.dp)))
                    }
                }
                Column {
                    for (module in modules) {
                        if (module.graph) {
                            FrameGraph(snapshot.frameHistory, snapshot.js?.frameIntervalMs ?: 0.0, graphModifier)
                        } else {
                            Text(module.value(snapshot, res) ?: "…", style = style.copy(color = module.level(snapshot).color()))
                        }
                    }
                }
            }
        } else {
            // No breaks inside a module (non-breaking spaces, word joiner after '/'), so lines
            // only break between modules.
            val parts = modules.mapNotNull { module ->
                module.compact(snapshot, res, settings.shortLabels)?.let { text ->
                    text.replace(" ", "\u00A0").replace("/", "/\u2060") to module.level(snapshot)
                }
            }
            val line = buildAnnotatedString {
                parts.forEachIndexed { i, (text, level) ->
                    if (i > 0) append("  ")
                    if (level == null) append(text) else withStyle(SpanStyle(color = level.color())) { append(text) }
                }
            }
            val showGraph = modules.any { it.graph }
            if (parts.isNotEmpty() || !showGraph) Text(if (parts.isEmpty()) AnnotatedString("…") else line, style = style)
            if (showGraph) FrameGraph(snapshot.frameHistory, snapshot.js?.frameIntervalMs ?: 0.0, graphModifier)
        }
    }
}

/**
 * Frame times as a line, newest on the right. Dashes mark the target frame time of the
 * current cap (16.7 ms when uncapped) and twice that. Each segment takes the colour of its
 * newer frame: green up to 1.25 target frames, yellow up to two, red above (see HudLevels).
 */
@Composable
private fun FrameGraph(history: List<Float>, targetMs: Double, modifier: Modifier) {
    val target = HudLevels.targetMs(targetMs).toFloat()
    Canvas(modifier) {
        if (history.isEmpty()) return@Canvas
        val top = maxOf(3 * target, history.max())
        fun y(ms: Float) = size.height * (1 - ms / top)
        for (ref in listOf(target, 2 * target)) {
            drawLine(HudDim.copy(alpha = 0.35f), Offset(0f, y(ref)), Offset(size.width, y(ref)), strokeWidth = 1f)
        }
        val step = size.width / (HISTORY_POINTS - 1)
        val points = history.takeLast(HISTORY_POINTS)
        val offset = (HISTORY_POINTS - points.size).coerceAtLeast(0) * step
        val width = 1.5.dp.toPx()
        for (i in 1 until points.size) {
            val color = HudLevels.frameTime(points[i].toDouble(), target.toDouble()).color()
            drawLine(
                color,
                Offset(offset + (i - 1) * step, y(points[i - 1])),
                Offset(offset + i * step, y(points[i])),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val HISTORY_POINTS = 180

/** Background darkness under the text; the HUD's opacity setting scales everything. */
private const val BACKGROUND_ALPHA = 0.6f
