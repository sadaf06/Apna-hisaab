package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Define a premium typographic hierarchy
// Bumping standard sizes slightly for absolute Hindi clarity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.EmojiSupportMatch

private val emojiPlatformStyle = PlatformTextStyle(
    emojiSupportMatch = EmojiSupportMatch.Default
)

val BaseTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1).sp,
        platformStyle = emojiPlatformStyle
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        platformStyle = emojiPlatformStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black, // ultra-bold banking app numbers style
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
        platformStyle = emojiPlatformStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        platformStyle = emojiPlatformStyle
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        platformStyle = emojiPlatformStyle
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        platformStyle = emojiPlatformStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium, // bumped slightly from Normal for readability
        fontSize = 17.sp,               // larger text for prominent Hindi rendering
        lineHeight = 25.sp,
        letterSpacing = 0.5.sp,
        platformStyle = emojiPlatformStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp, // slightly larger
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp,
        platformStyle = emojiPlatformStyle
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        platformStyle = emojiPlatformStyle
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        platformStyle = emojiPlatformStyle
    )
)

val Typography = BaseTypography

// Extended tokens for hero amount and micro badges — use instead of inline copy(fontSize=)
val displayHero = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontSize = 44.sp,
    lineHeight = 52.sp,
    letterSpacing = (-1.5).sp,
    platformStyle = emojiPlatformStyle
)
val labelMicro = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.5.sp,
    platformStyle = emojiPlatformStyle
)
