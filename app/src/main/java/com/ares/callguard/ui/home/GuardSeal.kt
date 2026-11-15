package com.ares.callguard.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ares.callguard.domain.model.GuardStatus
import com.ares.callguard.ui.theme.DeepOlive
import com.ares.callguard.ui.theme.ForestGreen
import com.ares.callguard.ui.theme.MutedChampagne
import com.ares.callguard.ui.theme.SageGreen
import com.ares.callguard.ui.theme.Spacing
import com.ares.callguard.ui.theme.WarmIvory
import com.ares.callguard.ui.theme.rememberReduceMotion

/**
 * The master control, drawn as a wax seal. The state is carried three ways so it never
 * depends on colour: the word, the shield (solid with a tick, hollow, or marked "!") and the
 * ring (solid, thin, or broken).
 */
@Composable
fun GuardSeal(status: GuardStatus, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val look = SealLook.of(status)

    val disc by animateColorAsState(look.disc(), label = "disc")
    val ink by animateColorAsState(look.ink(), label = "ink")
    val ringColor by animateColorAsState(look.ring(), label = "ring")
    val halo = haloProgress(animate = status == GuardStatus.ACTIVE && !rememberReduceMotion())

    Box(
        modifier = modifier
            .size(264.dp)
            .clip(CircleShape)
            .toggleable(
                value = status != GuardStatus.INACTIVE,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            )
            .semantics {
                contentDescription = "ARES protection"
                stateDescription = look.word
            }
            .drawBehind {
                val outer = size.minDimension / 2
                if (status == GuardStatus.ACTIVE) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.35f * (1 - halo)),
                        radius = outer - 14.dp.toPx() + 12.dp.toPx() * halo,
                        style = Stroke(2.dp.toPx()),
                    )
                }
                drawCircle(
                    color = ringColor,
                    radius = outer - 14.dp.toPx(),
                    style = Stroke(
                        width = look.ringWidth.toPx(),
                        pathEffect = if (look.brokenRing) {
                            PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx()))
                        } else {
                            null
                        },
                    ),
                )
                drawCircle(color = disc, radius = outer - 26.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Canvas(Modifier.size(68.dp)) { drawShield(status, ink, disc) }
            Text(
                text = look.word,
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.12.em),
                color = ink,
            )
            Text(text = look.hint, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun haloProgress(animate: Boolean): Float {
    if (!animate) return 0f
    val transition = rememberInfiniteTransition(label = "halo")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600), RepeatMode.Restart),
        label = "halo",
    )
    return progress
}

private enum class SealLook(
    val word: String,
    val hint: String,
    val ringWidth: Dp,
    val brokenRing: Boolean,
) {
    ACTIVE("ACTIVE", "Tap to turn off", 4.dp, false),
    INACTIVE("INACTIVE", "Tap to turn on", 1.5.dp, false),
    NEEDS_ATTENTION("NEEDS ATTENTION", "Not protecting yet", 4.dp, true);

    @Composable
    fun disc(): Color = when (this) {
        ACTIVE -> ForestGreen
        INACTIVE -> MaterialTheme.colorScheme.surface
        NEEDS_ATTENTION -> DeepOlive
    }

    @Composable
    fun ink(): Color = when (this) {
        INACTIVE -> MaterialTheme.colorScheme.onSurface
        else -> WarmIvory
    }

    @Composable
    fun ring(): Color = when (this) {
        INACTIVE -> SageGreen
        else -> MutedChampagne
    }

    companion object {
        fun of(status: GuardStatus) = when (status) {
            GuardStatus.ACTIVE -> ACTIVE
            GuardStatus.INACTIVE -> INACTIVE
            GuardStatus.NEEDS_ATTENTION -> NEEDS_ATTENTION
        }
    }
}

/** Shield drawn on a 24-unit grid, scaled to the canvas. */
private fun DrawScope.drawShield(status: GuardStatus, ink: Color, disc: Color) {
    val unit = size.minDimension / 24f
    fun point(x: Float, y: Float) = Offset(x * unit, y * unit)

    val outline = Path().apply {
        moveTo(12 * unit, 2 * unit)
        lineTo(4 * unit, 5 * unit)
        lineTo(4 * unit, 11.1f * unit)
        cubicTo(4 * unit, 16.1f * unit, 7.4f * unit, 20.7f * unit, 12 * unit, 22 * unit)
        cubicTo(16.6f * unit, 20.7f * unit, 20 * unit, 16.1f * unit, 20 * unit, 11.1f * unit)
        lineTo(20 * unit, 5 * unit)
        close()
    }
    val line = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (status) {
        GuardStatus.ACTIVE -> {
            drawPath(outline, ink)
            val tick = Path().apply {
                moveTo(8f * unit, 12.2f * unit)
                lineTo(10.9f * unit, 15.1f * unit)
                lineTo(16.2f * unit, 9.4f * unit)
            }
            drawPath(tick, disc, style = line)
        }

        GuardStatus.INACTIVE -> drawPath(outline, ink, style = Stroke(1.4f * unit, join = StrokeJoin.Round))

        GuardStatus.NEEDS_ATTENTION -> {
            drawPath(outline, ink, style = Stroke(1.4f * unit, join = StrokeJoin.Round))
            drawLine(ink, point(12f, 7.8f), point(12f, 13.4f), strokeWidth = line.width, cap = StrokeCap.Round)
            drawCircle(ink, radius = 1.1f * unit, center = point(12f, 16.8f))
        }
    }
}
