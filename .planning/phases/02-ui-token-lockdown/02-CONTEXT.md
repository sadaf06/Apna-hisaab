# Phase 02: UI Token Lockdown — Context

**Gathered:** 2026-09-16
**Status:** Ready for planning
**Source:** UI-REVIEW.md 15/24 + UAT 01

<domain>
## Phase Boundary

Lock design tokens to pass re-audit at 20+/24. Scope is token discipline, not taste change. No new features, no navigation change, no data model change. Fix 3 priority gaps: Color (raw hexes), Typography (inline overrides), Spacing (ad-hoc dp). Pages workflow untouched.

</domain>

<decisions>
## Implementation Decisions

### Color - Single Source of Truth
- Palette.kt is canonical. No Color(0xFF…) outside Palette.kt. Add Palette.Category.* for 15 categories, Palette.Glass.* for translucent whites, Palette.Brand.WhatsApp for share greens. CategoryConfig must reference Palette, not raw hex.

### Typography - Semantic Scale Only
- Type.kt defines max 5 sizes / 3 weights. Add displayHero 44sp and labelMicro 10sp if needed. All screens must use MaterialTheme.typography.* tokens, zero inline copy(fontSize=). Detekt rule bans fontSize outside Type.kt.

### Spacing - Dimens Scale Enforcement
- Dimens 4dp scale is canonical. Map 14→lg/xl, 18→xl, 10→md, 6→sm. Unify bottomBarClearance = 96dp + WindowInsets.navigationBars. All cards: primary xl 28, secondary md 16, pills pill 50. Lint raw .dp in screens.

### the agent's Discretion
- GlassCard/Pill/SurfaceInset keep existing glassFill + BorderSoft; only tokenize values, don't redesign visuals. Confetti/mood colors use Palette tokens, not random.
- Proguard/R8 Ktor keep already fixed (8d1b504), not in scope.

</decisions>

<canonical_refs>
## Canonical References

- `K:/apna-hisaab/app/src/main/java/com/example/ui/theme/Palette.kt` — single source of truth, 60/30/10 + Surface/Glass/Semantic/Mood
- `K:/apna-hisaab/app/src/main/java/com/example/ui/theme/Dimens.kt` — 4dp scale xs..xxxl, Shapes
- `K:/apna-hisaab/app/src/main/java/com/example/ui/theme/Type.kt` — BaseTypography 10 styles, SansSerif/Default split
- `K:/apna-hisaab/app/src/main/java/com/example/ui/components/GlassUi.kt` — AppBackground, GlassCard, GlassPill, BlurLockable
- `K:/apna-hisaab/UI-REVIEW.md` — 15/24 audit, 3 priority fixes + 14 minor
- `K:/apna-hisaab/app/src/main/java/com/example/data/CategoryConfig.kt` — 15 raw hex violations
</canonical_refs>

<specifics>
## Specific Ideas

- CI grep gate: `grep -R "Color(0x" --include="*.kt" app/src | grep -v "Palette.kt"` must be empty
- TopBar 20/14dp → Dimens.screenPadding/lg, NavBar 18dp → xl, pills 10dp → md, bottom 140/180dp → navBarClearance
- Hero amount 44sp -1.5ls → Typography.displayHero, badge 11sp → labelSmall, 9sp → labelMicro
- WhatsApp greens 0xFF128C7E/0xFF25D366 → Palette.Brand.WhatsAppDark/Green
</specifics>

<deferred>
## Deferred Ideas

- Skeleton/optimistic states, offline banner, PIN throttle, IME insets — Experience Design pillar, next phase
- Emoji semantics, FAB collision, confetti palette — Visuals pillar minor, next phase
</deferred>

---
*Phase: 02-ui-token-lockdown*
*Context gathered: 2026-09-16 via UI-REVIEW gap closure*
