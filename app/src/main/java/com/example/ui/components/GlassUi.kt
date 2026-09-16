package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Dimens
import com.example.ui.theme.Palette
import com.example.ui.theme.Shapes

/**
 * The shared visual vocabulary for the "Refined Glass" redesign.
 *
 * - [AppBackground] : ambient deep base + two soft accent glows. Sits behind the
 *   whole app so every (translucent) screen gains depth.
 * - [GlassCard]     : the one card style. Subtle top-lit gradient fill, hairline
 *   border, low shadow. Replaces the dozens of bespoke per-screen cards.
 * - [GlassPill]     : compact rounded surface for chips / quick actions.
 * - [SectionHeader] : consistent section titles.
 */

/**
 * True when the OS "remove animations" / reduced-motion accessibility setting is on
 * (system animator duration scale == 0). Use to disable decorative motion.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/** Vertical gradient used as a card fill — slightly brighter at the top, like light catching glass. */
private val glassFill: Brush
    get() = Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.SurfaceLow))

/** A subtle purple→teal hairline gradient for accent borders. */
val accentBorderBrush: Brush
    get() = Brush.linearGradient(
        listOf(Palette.Purple.copy(alpha = 0.45f), Palette.Teal.copy(alpha = 0.40f))
    )

/** The brand gradient used on primary actions and highlights. */
val brandBrush: Brush
    get() = Brush.horizontalGradient(listOf(Palette.PurpleDeep, Palette.Teal))

/**
 * Ambient app backdrop: deep vertical base with two soft radial glows
 * (purple top-left, teal bottom-right). Place [content] on top.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbs_global")

    val orb1XOffset by infiniteTransition.animateFloat(
        initialValue = -70f,
        targetValue = 70f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1X"
    )
    val orb1YOffset by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1Y"
    )

    val orb2XOffset by infiniteTransition.animateFloat(
        initialValue = 50f,
        targetValue = -50f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2X"
    )
    val orb2YOffset by infiniteTransition.animateFloat(
        initialValue = -60f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2Y"
    )

    val orb3XOffset by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb3X"
    )
    val orb3YOffset by infiniteTransition.animateFloat(
        initialValue = 70f,
        targetValue = -70f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb3Y"
    )

    // Freeze decorative orb drift when the user has reduced motion enabled.
    val motion = if (rememberReducedMotion()) 0f else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BaseTop, Palette.BaseBottom)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Orb 1: mint #5EEAD4
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Palette.Teal.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(
                        size.width * 0.25f + (orb1XOffset * motion).dp.toPx(),
                        size.height * 0.20f + (orb1YOffset * motion).dp.toPx()
                    ),
                    radius = size.maxDimension * 0.38f
                )
            )
            // Orb 2: violet #A78BFA
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Palette.Purple.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(
                        size.width * 0.75f + (orb2XOffset * motion).dp.toPx(),
                        size.height * 0.45f + (orb2YOffset * motion).dp.toPx()
                    ),
                    radius = size.maxDimension * 0.42f
                )
            )
            // Orb 3: faint coral #FF8B8B
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Palette.Danger.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(
                        size.width * 0.30f + (orb3XOffset * motion).dp.toPx(),
                        size.height * 0.75f + (orb3YOffset * motion).dp.toPx()
                    ),
                    radius = size.maxDimension * 0.36f
                )
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * The single card style for the app.
 *
 * @param highlight when true, uses the purple→teal accent border for hero cards.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.md,
    contentPadding: PaddingValues = PaddingValues(Dimens.lg),
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .clip(shape)
        .background(glassFill, shape)

    val bordered = if (highlight) {
        base.border(BorderStroke(Dimens.border, accentBorderBrush), shape)
    } else {
        base.border(BorderStroke(Dimens.hairline, Palette.BorderSoft), shape)
    }

    Column(modifier = bordered.padding(contentPadding), content = content)
}

/**
 * Compact rounded surface for chips, quick-add buttons, badges.
 *
 * @param accent optional tint; when provided the pill takes a soft tinted fill + border.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color? = null,
    shape: Shape = Shapes.sm,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.md, vertical = Dimens.sm),
    content: @Composable () -> Unit
) {
    val tint = accent ?: Palette.Purple
    val fill = if (selected) tint.copy(alpha = 0.15f) else Palette.SurfaceInset
    val borderColor = if (selected) tint else Palette.BorderSoft
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(if (selected) Dimens.border else Dimens.hairline, borderColor, shape)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

/** Consistent section header: bold title with an optional trailing action slot. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Palette.TextPrimary,
            fontWeight = FontWeight.Bold,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(Modifier.width(Dimens.sm))
            trailing()
        }
    }
}

@Composable
fun BlurLockable(
    locked: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = locked) { onUnlock() }
    ) {
        Box(Modifier.blur(if (locked) 14.dp else 0.dp)) { content() }
        if (locked) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Glass.White12)
                        .border(1.dp, Palette.Glass.White20, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("🔒", fontSize = 11.sp)
                    Text(
                        "Tap to unlock",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, color = Palette.TextPrimary
                        )
                    )
                }
            }
        }
    }
}
