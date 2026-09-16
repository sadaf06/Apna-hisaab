package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Refined Glass" palette — the single source of truth for the redesign.
 *
 * The app runs in forced dark mode, so this is tuned as a deep charcoal-navy
 * glass system: a near-black base, translucent elevated surfaces, hairline
 * borders, and two restrained accents (purple + teal) used sparingly. Semantic
 * colours (success / warning / danger) are muted so nothing screams.
 *
 * Rule of thumb: screens should reference Palette / MaterialTheme.colorScheme,
 * never raw Color(0x...) literals. That consistency is the premium feel.
 */
object Palette {
    // ---- Base / background ----
    val BaseTop = Color(0xFF0C0F16)      // deep charcoal-navy (top of ambient gradient)
    val BaseBottom = Color(0xFF07090F)   // near-black (bottom)
    val Scrim = Color(0xFF05070B)

    // ---- Accent glows (used at low alpha behind content for depth) ----
    val GlowPurple = Color(0xFF8B5CF6)
    val GlowTeal = Color(0xFF2DD4BF)

    // ---- Brand accents ----
    val Purple = Color(0xFFA78BFA)
    val PurpleDeep = Color(0xFF8B5CF6)
    val Teal = Color(0xFF2DD4BF)
    val TealDeep = Color(0xFF0D9488)     // gradient end for mint CTAs

    // Dark ink for text/icons placed ON bright accent fills (mint/gold buttons).
    val OnAccent = Color(0xFF14171F)

    // ---- Glass surfaces (translucent, layered over the ambient base) ----
    // Cards: a subtle top-lit gradient from SurfaceHigh -> SurfaceLow.
    val SurfaceHigh = Color(0x14FFFFFF)  // ~8% white
    val SurfaceLow = Color(0x0AFFFFFF)   // ~4% white
    // Inset elements inside a card (inputs, chips): darker than the card.
    val SurfaceInset = Color(0x40070A12) // translucent near-black

    // ---- Borders ----
    val BorderSoft = Color(0x14FFFFFF)   // ~8% white hairline
    val BorderStrong = Color(0x26FFFFFF) // ~15% white
    val BorderAccent = Color(0x40A78BFA) // purple @ 25%

    // ---- Text ----
    val TextPrimary = Color(0xFFF6F7FB)
    val TextSecondary = Color(0xB3FFFFFF) // 70%
    val TextTertiary = Color(0x80FFFFFF)  // 50%
    val TextFaint = Color(0x59FFFFFF)     // 35%

    // ---- Semantic ----
    val Success = Color(0xFF34D399)
    val SuccessBg = Color(0x2634D399)
    val Warning = Color(0xFFFBBF24)
    val WarningBg = Color(0x26FBBF24)
    val Danger = Color(0xFFF87171)
    val DangerBg = Color(0x26F87171)

    // ---- Mood accents (kept aligned with existing mood feature) ----
    fun mood(key: String): Color = when (key.lowercase().trim()) {
        "khush" -> Purple
        "normal" -> Success
        "thaka" -> Category.Padhai
        "stressed" -> Category.Masti
        "sad" -> Category.Sad
        else -> Purple
    }

    // ---- Category colors (single source; no Color(0x...) outside this file) ----
    object Category {
        val Khana = Color(0xFF8B5CF6)
        val GharKharch = Color(0xFF14B8A6)
        val Rent = Color(0xFF4F46E5)
        val EMI = Color(0xFF6366F1)
        val Petrol = Color(0xFFF59E0B)
        val Safar = Color(0xFF3B82F6)
        val Masti = Color(0xFFEC4899)
        val Shopping = Color(0xFF22C55E)
        val Health = Color(0xFFDC2626)
        val Padhai = Color(0xFF60A5FA)
        val Personal = Color(0xFF10B981)
        val Gift = Color(0xFFF43F5E)
        val Savings = Color(0xFF22C55E)
        val Pooja = Color(0xFFEAB308)
        val Recharge = Color(0xFF0F172A)
        val Other = Color(0xFFF97316)
        val Sad = Color(0xFF94A3B8)
    }

    // ---- Glass translucents (use instead of Color.White/Black copy) ----
    object Glass {
        val White06 = Color(0x0FFFFFFF).copy(alpha = 0.06f) // ~6% white (approx 0x0F)
        val White10 = Color.White.copy(alpha = 0.10f)
        val White12 = Color.White.copy(alpha = 0.12f)
        val White15 = Color.White.copy(alpha = 0.15f)
        val White20 = Color.White.copy(alpha = 0.20f)
        val White25 = Color.White.copy(alpha = 0.25f)
        val White40 = Color.White.copy(alpha = 0.40f)
        val Black28 = Color.Black.copy(alpha = 0.28f)
        val Black30 = Color.Black.copy(alpha = 0.30f)
        val Black40 = Color.Black.copy(alpha = 0.40f)
    }

    // ---- Brand greens (WhatsApp) ----
    object Brand {
        val WhatsApp = Color(0xFF25D366)
        val WhatsAppDark = Color(0xFF128C7E)
    }
}
