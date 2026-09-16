package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Centralised spacing, radius, elevation and stroke scale.
 *
 * Every screen used to hardcode its own paddings (16.dp here, 20.dp there) and
 * corner radii (12 / 14 / 16 / 20). Routing everything through one scale is what
 * makes the redesign read as a single, premium product instead of many screens.
 *
 * Scale is loosely 4dp based: xs=4, sm=8, md=12, lg=16, xl=20, xxl=24, xxxl=32.
 */
object Dimens {
    // Spacing
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    // Screen-level gutters
    val screenPadding = 20.dp
    val sectionGap = 16.dp

    // Corner radii
    val radiusSm = 12.dp
    val radiusMd = 16.dp
    val radiusLg = 20.dp
    val radiusXl = 28.dp
    val radiusPill = 50.dp

    // Borders
    val hairline = 1.dp
    val border = 1.2.dp

    // Elevation (kept low; depth comes from gradients + glow, not hard shadows)
    val elevationCard = 2.dp
    val elevationRaised = 6.dp

    // Common touch targets
    val iconButton = 40.dp
    val avatar = 44.dp

    // Nav bar clearance (64dp bar + 18dp inset + 14dp breathing)
    val navBarClearance = 96.dp
    val micro = 2.dp
}

// Convenience pre-built shapes so callers don't re-allocate RoundedCornerShape everywhere.
object Shapes {
    val sm = RoundedCornerShape(Dimens.radiusSm)
    val md = RoundedCornerShape(Dimens.radiusMd)
    val lg = RoundedCornerShape(Dimens.radiusLg)
    val xl = RoundedCornerShape(Dimens.radiusXl)
    val pill = RoundedCornerShape(Dimens.radiusPill)
}
