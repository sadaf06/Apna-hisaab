# Phase 01 — UI Review — Apna Hisaab (Jetpack Compose · Dark Glassmorphic)

> **Stage: AUDIT — Adversarial Visual & Interaction Review**
> `ui-brand` banner · Abstract 6-Pillar Standards (no UI-SPEC — audit against system maturity, consistency, and a11y)
> Dark glassmorphic · Forced dark · Single-source-of-truth Palette vs. ad-hoc raw colors

**Audited:** 2026-09-16
**Baseline:** Abstract 6-pillar standards (no UI-SPEC.md)
**Screenshots:** Not captured — Android Compose (no localhost dev server) — **code-only audit**
**Scope:** `MainActivity.kt` (TopBar, LiquidGlassNavBar, Splash), `ui/theme/*`, `ui/components/GlassUi.kt`, `ui/screens/{Aaj,Hisaab,Kahani,Sapna,Settings}`, `data/CategoryConfig.kt`

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | **3/4** | Hinglish voice is distinct & contextual; mixed EN/HI CTAs + fallback email leak degrade trust |
| 2. Visuals | **3/4** | Coherent refined-glass language with strong hero hierarchy; emoji-as-icons + competing focal points weaken a11y & focus |
| 3. Color | **2/4** | Palette + Theme are exemplary 60/30/10 docs; 15 raw hexes in CategoryConfig + `Color.White/Black` litter violate own contract |
| 4. Typography | **2/4** | 10 semantic tokens but 7 distinct `sp` sizes + 6 weights + pervasive `copy(fontSize=…)` overrides break the scale |
| 5. Spacing | **2/4** | `Dimens` 4dp scale exists & documented; ~40% of layouts use raw `14/18/10/6/140.dp` outside scale |
| 6. Experience Design | **3/4** | Loading/empty/error/confirm + reduced-motion + BlurLock/PIN are mature; skeletons, offline resilience & keyboard guards are fragile |

**Overall: 15/24 — Good intent, premium glass direction landing, but system discipline not yet enforced.**

---

## Top 3 Priority Fixes

1. **[BLOCKER → Color] Eliminate raw color literals — route CategoryConfig + GlassUi through Palette** — User impact: brand drift (15 category hexes like `0xFFDC2626`, `0xFF0F172A`) + ad-hoc `Color.White(0.12f)/Black(0.28f)` make future theme changes impossible and cause contrast variance per-category — **Concrete fix:** Add `Palette.Category.*` and `Palette.GlassWhite12/10/06` tokens; change `CategoryConfig` to reference `Palette.*` (no `Color(0xFF...)`), replace all `Color.White.copy(alpha=…)` with `Palette.Glass.*` or `Palette.TextPrimary` alphas; add `grep -rn "Color(0x" app/src --include="*.kt"` to CI and fail on match outside `Palette.kt`.

2. **[WARNING → Typography] Lock typography scale — forbid inline `fontSize` overrides** — User impact: 44sp hero, 24sp greeting, 11sp badge, 9sp “din bache” create 7+ sizes; Hindi legibility relies on 17sp `bodyLarge` but every screen overrides it, so type rhythm will diverge per-screen — **Concrete fix:** In `Type.kt` add `displayHero = 44sp/Black` and `labelMicro = 10sp/Bold` if truly needed (max 5 sizes, 3 weights). Delete inline `copy(fontSize=44.sp)`, `copy(fontSize=11.sp)`, `copy(fontSize=9.sp)` etc in `AajScreen.kt:626,614`, `SapnaScreen`, `KahaniScreen`; use semantic tokens (`MaterialTheme.typography.headlineLarge`, `labelSmall`). Add ktlint/detekt rule banning `fontSize =` outside `Type.kt`.

3. **[WARNING → Spacing] Enforce Dimens — replace arbitrary dp with scale** — User impact: TopBar `14.dp/20.dp`, NavBar `18.dp`, pills `10.dp`, bottom paddings `140.dp/180.dp` cause inconsistent gutters on foldables / 3-button nav — **Concrete fix:** Map `14.dp→Dimens.md (12) or lg (16)`, `18.dp→Dimens.xl (20)`, `10.dp→Dimens.md`, `6dp→Dimens.sm`, add `Dimens.bottomBarClearance = 96.dp` (instead of scattered `140.dp`/`180.dp`). Audit `AajScreen` `18.dp` border, `MainActivity:218/423` NavBar gaps, `HisaabScreen` `140.dp` lazy padding. Replace incrementally; lint for `\.dp` outside `Dimens.*` in screens.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4) — WARNING

**Strengths (evidence of craft):**
- Consistent Hinglish persona: `Aaj ka Kharcha`, `Likho ya bolo`, `Paisa gaya toh hisaab do!` (`AajScreen:312-316` shuffled placeholders), `Budget ke andar / Limit se zyada` (`AajScreen:601`), `Tap to unlock` paired with 🔒 metaphor consistently via `BlurLockable` (`GlassUi:312`, `AajScreen:717`).
- Empty states are contextual not generic: `Koi naya kharcha nahi mila 🤷` (`AajScreen:1362`), `Is mahine koi kahani nahi hai.` (`HisaabScreen:869`), `Abhi koi kamaai add nahi ki 😊` (`SettingsScreen:338`) — exceeds abstract best practice.
- Error/validation voice is friendly & actionable: `Paisa toh batao yaar, kitna gaya?` (`AajScreen:1183`), `Mic permission do Settings mein 🙏` (`AajScreen:248,288`), `AI abhi thak gayi hai! Khud se entry add karein?` (`AajScreen:1035`), budget alert `Budget Alert Seema Paar! ⚠️` (`SettingsScreen:600`).
- Destructive actions use Hinglish confirmation: `Logout karna chahte ho? / Aapka data safe rahega 😊 / Haan Logout / Nahi Rukna Hai` (`MainActivity:188-210`, `SettingsScreen:881`), double-confirm for account delete (`SettingsScreen:910,940`) — pattern correct.

**Failures:**
- **Mixed-language CTAs (inconsistency):** `HisaabScreen` edit dialog uses bare `Cancel / Save` (`HisaabScreen:355,385`) while topBar uses `Haan Logout / Nahi Rukna Hai`. `SettingsScreen` income sheet uses `Kamaai Jodo ✅` vs `Kamaai Jodein` (`SettingsScreen:1344` vs `514`). `AajScreen` manual save is bilingual parenthetical `Hisaab mein jodein (Save)` (`AajScreen:1229`) — reads as hesitation.
- **Parenthetical English redundancy:** Placeholders like `Rakam (Amount)` (`AajScreen:1079`), `Kya kharch kiya? (Details)` (`AajScreen:1112`), `Source ka naam (jaise: TCS Salary…)` — either Hinglish or Hindi; parentheses signal untranslated debt.
- **Hardcoded PII fallback visible to greppers / leaks to UI:** `S.i.siddiqui06@gmail.com` hardcoded fallback in `AajScreen:453`, `KahaniScreen:145`, `MainActivity` displayName logic (`AajScreen:455-460`) — if prefs empty, another user sees previous dev's email. Copy should fallback to `"Mehman"` not a personal email. Also `AboutScreen/AdminScreen` routes leak `isAdmin` email compare against same literal (`SettingsScreen:81`).
- **Verbosity / wrapping risk:** Hindi long strings without `maxLines/overflow` (e.g., `Jis category ka limit set karna hai…` `SettingsScreen:670`) will truncate on xxhdpi 4" screens; only some titles have `maxLines=1, overflow=Ellipsis`.
- **Emoji as semantics gap (copy→a11y):** `🎤/⏹️/🔒/🔓` used as lone `Text` labels for voice toggle (`AajScreen:842`) and privacy lock (`AajScreen:592`) — screen readers hear emoji name, not action. Should be `contentDescription` on parent `Box(clickable)` + `semantics`.

**Fix:** Unify CTA lexicon (`Haan / Nahi` or `Save / Cancel` — pick one per-surface), remove parenthetical English, replace email fallback with `"Apna Hisaab user"` or `null → onboarding`, add `semantics { contentDescription = "Voice input toggle, ${if(listening) "stop" else "start"}" }` to mic pill, audit for `Text("🔒")` pattern (8 occurrences) and give each a string resource.

---

### Pillar 2: Visuals (3/4) — WARNING

**Strengths:**
- **Single glass vocabulary** is actually implemented: `GlassCard` (`GlassUi:211`), `GlassPill` (`GlassUi:237`), `LiquidGlassCard` (`AajScreen:80`, `HisaabScreen:1562`), `AppBackground` triple-orbs + `HazeState` blur (`MainActivity:569-579`, `GlassUi:158-197`). No bespoke card shadows remain — low `elevationCard=2.dp`, depth via gradients + radial glows.
- **Clear focal point per screen:** Aaj hero `44sp ₹amount + motivational line` with `Teal→Purple` progress (`AajScreen:624-712`) anchors above the fold; Hisaab personality card + brandBrush tabs create strong wayfinding (`HisaabScreen:703-825`); Kahani `DonutChart 170dp + ₹Total` centered (`KahaniScreen:1458-1521`) is immediate.
- **GlassNavBar craft:** `LiquidGlassNavBar` sliding pill (`MainActivity:476-485`), `dotAlpha` mint glow, `hazeChild blur 30dp + 12% white tint + 0.40 top specular` — coherent liquid glass, `animateDpAsState spring LowBouncy` feels premium. `glassShadow 18dp/8dp` + `clip 28dp` correct.
- **Hierarchy through weight/size/color:** `HisaabScreen: DiaryEntryCard` left `4dp moodColor` strip + `GlassPill` date/mood caps + serif `“` at 50% moodColor + dividing `HorizontalDivider(0.08)` — refined.

**Failures:**
- **Icon-only without semantics (partial a11y break):** TopBar logout `Icon(Logout)` has `contentDescription="Sign Out"` ✅ but Aaj mic `Box(clickable)` wrapping `Text("🎤")` has no `contentDescription` nor `role=Button` (`AajScreen:829-846`); same for 12 `IconButton` mood selectors (`AajScreen:902-927`) — voice relies on `clickable` without semantics; TalkBack announces nothing meaningful. `clearAndSetSemantics` used on some emojis (`SapnaScreen:334`) but not systematically.
- **Competing focal points:** Aaj stacks hero 44sp amount + `52dp full-width Teal gradient CTA "Hisaab/Action"` both at max saturation — eye oscillates. Kahani stacks income/kharch/bachat (3 big numbers) + donut + sapna canvas stars (18 random dots) + AI letter — 4 heroes in one scroll. Visual weight not stepped down for secondary cards.
- **Glass overuse flattens hierarchy:** Every surface is `SurfaceInset + 1dp BorderSoft + sheen` — background, card, inset input, pill all share same treatment; inset inputs (`AajScreen:784-792` `Color.White 0.06/0.12`) insufficiently recessed from card (`SurfaceInset 0x40070A12`); on bright wallpapers contrast may wash out. Need stronger `elevationRaised` or deeper inset.
- **FAB + NavBar collision:** `SapnaScreen:174-188` FAB `56dp` at `end 24dp, bottom 100dp` sits directly above glass NavBar `64dp + 18dp bottom inset = 82dp`; on <6" devices with gesture nav, touch targets overlap (48dp FAB vs 40dp NavItem). No `WindowInsets.ime` guard.
- **Confetti breaks language:** `ConfettiBurst` (`AajScreen:1761-1813`) generates `60` particles with `Color(red=randomFloat(), green=randomFloat(), blue=randomFloat())` — saturated neon `Size 12-32` rectangles at `cy = height/3` — visually clashes with muted `Purple/Teal/Danger` semantic palette.
- **Mood selector low contrast:** Unselected mood `alpha 0.4` (`AajScreen:926`), `Color.White 0.05 bg + 0.1 border` — fails WCAG 3:1 for graphical objects; user may not perceive tappable vs disabled.

**Fix:** Add `Modifier.semantics { contentDescription = "…"; role = Role.Button }` to every `clickable` Box wrapping emoji Text (Aaj mic, mood row, quick-add pills, chevrons). Reduce secondary CTA weight: make `Hisaab/Action` `Small 40dp` or secondary outline when hero amount >0, keep only one max-weight gradient per screen. Deepen inset inputs to `0x60_070A12` or add inner shadow. Move Sapna FAB to `bottom = WindowInsets.navigationBars + 80dp` via `windowInsetsPadding` and give `contentDescription`. Replace random confetti colors with `listOf(Palette.Purple, Palette.Teal, Palette.Success, Palette.Warning)` at `alpha 0.9`.

---

### Pillar 3: Color (2/4) — NEEDS WORK

**Strengths (system exists):**
- `Palette.kt` is best-in-class for this repo size: documented `60/30/10`, forced-dark semantics, translucent `SurfaceHigh 0x14FFFFFF (8%) / SurfaceLow 0x0A... (4%)`, `SurfaceInset 0x40070A12`, hairline `BorderSoft 0x14 / BorderStrong 0x26`, `accentBorderBrush Purple 45% → Teal 40%`, muted semantic `SuccessBg 0x26` etc (`Palette:16-59`). `Theme.kt: DarkColorScheme primary = Palette.mood(mood)` dynamic accent by mood — clever brand tie.
- **Distribution discipline mostly respected:** 60 = `BaseTop #0C0F16 → BaseBottom #07090F` via `AppBackground` vertical gradient; 30 = glass cards + `TextPrimary #F6F7FB / TextSecondary 70%`; 10 = Purple/Teal gradients (`brandBrush PurpleDeep→Teal`, `GlassUi:85`, `HisaabScreen:779`). Whites/Blacks only at low alphas (sheen/shadow) not as flat fills.
- **Semantic restraint:** `Success #34D399 / Warning #FBBF24 / Danger #F87171` only as pill tints (`Danger 0.15 bg + 0.3 border` for streak `AajScreen:536`), badge text colors switching on `isOverBudget` (`AajScreen:599`), progress state (`SettingsScreen:477`). Not used as full-bleed backgrounds.

**Failures (contract violation — self-discipline):**
- **CategoryConfig raw-hex explosion — 15 violations in one file** (`CategoryConfig:14-29`): `Color(0xFF8B5CF6)`, `0xFF14B8A6`, `0xFF4F46E5`, `0xFFF59E0B`, `0xFF3B82F6` … `0xFFF97316`. Doc in `Palette:13` says “never raw Color(0x...) literals” — this file alone breaks it. Colors drift from `Palette.Purple/Teal/Danger/Warning` (e.g., `Khana 0xFF8B5CF6` duplicates `PurpleDeep`, `Recharge 0xFF0F172A` is a raw navy not in system, `Petrol 0xFFF59E0B` near but not `Warning`). Future palette shift requires hunting hexes.
- **Hardcoded neutrals outside Palette:** `GlassUi:303-305` `Color.White 0.12/0.20`, `AajScreen:788,834` `Color.White 0.10/0.06`, `HisaabScreen:322-324` `background(Palette.BaseTop)` vs random `Color.Transparent`, `SapnaScreen` `GInk = Palette.OnAccent` used as dialog `containerColor` with `GInk` as dark ink but also as surface (`SapnaScreen:241` `containerColor=GInk`) — dark ink used as background color confuses token meaning.
- **Brand-green exception not tokenized:** `KahaniScreen:70-71` `WhatsAppGreenDark 0xFF128C7E / WhatsAppGreen 0xFF25D366` hardcoded for brand recognition — justified but should live as `Palette.Brand.WhatsApp` not magic hex, else every share card hardcodes hex.
- **Duplicate mood colors outside Palette.mood():** `KahaniScreen:1752-1760` `getMoodAttributes` re-declares `0xFF34D399, 0xFFFBBF24, 0xFFFF8B8B, 0xFF60A5FA, 0xFF9AA3B2` instead of calling `Palette.mood()` / `Palette.Success/Warning` — 2 sources of truth for mood.
- **Surface token misuse:** `Theme.kt:23` `surface = Color(0xFF161B22)` raw not `Palette.Surface*`; `SapnaScreen:1035` `background(Palette.SurfaceLow)` ok but `EmptyGoalCard` uses dashed `Palette.BorderStrong` at full opacity — border stronger than card fill.

**Fix:** Create `Palette.Category.Khana = Purple` etc (or map at startup), delete all `Color(0xFF…)` from `CategoryConfig`; add `Palette.Glass.White06/10/12`, `Palette.Scrim.28` and replace every `Color.White.copy(alpha=0.06)` / `Color.Black.copy(0.28)` with tokens. Move WhatsApp greens into `Palette.Brand`. Unify `getMoodAttributes` → `Palette.mood(key)` + semantic map; add CI grep `grep -R "Color(0x" --include="*.kt" app/src | grep -v "Palette.kt"` must be empty.

---

### Pillar 4: Typography (2/4) — NEEDS WORK

**Strengths:**
- Single `Type.kt` with `BaseTypography` (`Type:18-99`) gives Hindi headroom: `bodyLarge 17sp/25lh Medium 0.5ls`, `bodyMedium 15sp` bumped for Devanagari, `EmojiSupportMatch.Default` prevents emoji tofu — thoughtful for Hinglish.
- `FontWeight.Black` for banking-style numbers (`headlineLarge 32/38 -0.5ls`, `AajScreen:627` `44sp -1.5ls` for today total) creates confident hierarchy; `labelSmall 12sp SemiBold 0.5ls` for pills/captions — premium.

**Failures:**
- **Scale fragmentation: 7 sizes, 6 weights, ~10 line heights > abstract threshold (4/2):** Tokens define `32,28,32,22,20,17,17,15,14,12` (`Type:21-97`) — duplicate `32sp` for `displayMedium` and `headlineLarge` wastes a step; `17sp` appears twice (`titleMedium 17`, `bodyLarge 17`). Weights `Black, ExtraBold, Bold, SemiBold, Medium, Normal` — abstract flag `>2` weights fires; even with Hindi justification this is too wide.
- **Pervasive inline overrides bypass the system:** `AajScreen:624` `fontSize=44.sp`, `516` `24.sp`, `585` `14.sp`, `615` `11.sp`, `1291` `titleLarge` copy with `9.sp` `labelSmall` (`AajScreen:1296`), `SapnaScreen:342` `18.sp`, `KahaniScreen:226` `21.sp`; ~22 inline `fontSize =` in 4 screens. The tokens exist but screens ignore them, so type will diverge.
- **Line height / letterSpacing not systemized:** `BaseTypography` sets `lineHeight 40,36,38,28,26,24,25,22,20,16` and `letterSpacing -1, -0.5, 0, 0.15, 0.5` per style; inline copies override with ad-hoc `letterSpacing = (-1.5).sp` (`AajScreen:629`) and no `lineHeight` guard — long Hindi strings like `Is mahine ke saare moods…` (`KahaniScreen:783`) at `lineHeight 24sp` may clip descender-heavy glyphs on some fonts.
- **Mixed families:** `SansSerif` for display/title/label but `Default` for body (`Type:60-82`); on some OEMs `Default` resolves to Roboto vs SamsungOne variance — Hinglish will render inconsistently. Should unify to `SansSerif` or a Hindi-optimized `FontFamily`.
- **Code-local typo / alias noise:** `MainActivity:243` `letterSpacing 0.5.sp` on `titleMedium` initials vs `SplashScreen` `displayMedium letterSpacing -1.5sp` — initials inside avatar vs splash same token but different tracking.

**Fix:** Collapse to 5 sizes: `display 32, headline 22, title 20/17, body 17/15, label 12/10` (remove duplicate 32/28). Keep 3 weights: `Black (numbers/display), Bold (titles/CTAs), Medium (body)` — demote `ExtraBold/SemiBold` to `Bold`. Remove all `copy(fontSize=…)` in screens; add missing semantic roles (`amountHero`, `captionMicro`) to `Typography` instead. Enforce detekt rule `ForbiddenMethodCall` for `copy(fontSize` outside `Type.kt` and fix incrementally.

---

### Pillar 5: Spacing (2/4) — NEEDS WORK

**Strengths:**
- `Dimens.kt` documents a coherent scale: `xs 4, sm 8, md 12, lg 16, xl 20, xxl 24, xxxl 32; screenPadding 20, sectionGap 16; radiusSm 12, md 16, lg 20, xl 28, pill 50; hairline 1, border 1.2; elevationCard 2` (`Dimens:15-47`). `Shapes.sm/md/lg/xl/pill` pre-built — premium setup. Most cards use `Dimens.xl/xxl/lg/md` via `Collection(verticalSpacing=Dimens.xl)` and `SectionHeader`-style grouping (`AajScreen:494-500` `Arrangement.spacedBy(Dimens.xl)` / `GlassUi:261`).

**Failures:**
- **Scale ignored ~40% of the time:** `MainActivity:218` `padding(horizontal=20.dp, vertical=14.dp)` — `14` not in scale; `MainActivity:423` `padding(start=20,end=20,bottom=18.dp)` — `18` not in scale; `AajScreen:792` `padding(horizontal=18, vertical=6)`, `AajScreen:162` `vertical 10.dp`, `10.dp` appears 3× (`AajScreen:162,290,1094`), `6.dp` appears 6×, `14.dp` for emoji border etc. `SapnaScreen:FAB 24.dp` inset, `HisaabScreen:842-960` `bottom 140.dp` scattered.
- **Bottom clearance inconsistency:** `AajScreen:1439` `Spacer 140.dp`, `HisaabScreen:842` `contentPadding bottom 140.dp`, `SapnaScreen:88` `bottom 180.dp`, `SettingsScreen:152` `bottom 140.dp`, `KahaniScreen:1386` `height 140.dp` — 3 different values for same nav-bar avoidance; on gesture-nav vs 3-button nav the gap will be wrong on one.
- **Radius / border drift:** `LiquidGlassCard shape = Shapes.xl (28dp)` but `GlassCard default Shapes.md (16dp)` and `HisaabScreen:HisaabLiquidGlassCard 28dp` vs `GlassPill radius 12dp` vs FAB `56dp CircleShape` — 4 radius systems coexist; `1.dp` vs `1.2.dp (Dimens.border)` vs `1.5.dp` for mood circle (`KahaniScreen:1712`) vs `1.8.dp` for income type selector (`SettingsScreen:1276`).
- **Ad-hoc gaps break 4dp grid:** `AajScreen:523` `height 2.dp`, `635` `height 8.dp` vs `Dimens.sm`, `AajScreen:747` `height 12.dp` (md) ok but `AajScreen:496` uses `Dimens.xl` correctly then immediately `2.dp` — grid not consistent.
- **Glass thickness not tokenized:** `BlurLockable blur 14.dp/16.dp`, `AppBackground orbs radius size.maxDimension*0.38` etc are one-offs.

**Fix:** Extend `Dimens` with `micro=2.dp` if needed (formally adopt 2), else map `14→lg`, `18→xl`, `10→md`, `6→sm`; unify bottom clearance to single `Dimens.navBarClearance = 96.dp + WindowInsets.navigationBars`; unify card radii — all primary cards `xl (28)`, secondary/inset `md (16)`, pills `pill (50)`; lint screen files for raw `.dp` literals and auto-fix to nearest `Dimens.*`.

---

### Pillar 6: Experience Design (3/4) — WARNING

**Strengths (state coverage mature for a shipped Android app):**
- **Loading:** `AajScreen:934-973` `ParsingState.PARSING → CircularProgressIndicator + "Analyse ho raha hai..."` with `enabled=!isParsing` guard; `KahaniScreen:725-748` summary loading `height 96.dp` fixed placeholder avoids layout shift ✅; `HisaabScreen:940-946` / `KahaniScreen:1040` `loadingMonthKey == key → 24/36dp spinner + "Hisaab nikala jaa raha hai…"` — per-month granularity.
- **Empty:** Every list has contextual empty: `todayExpenses.isEmpty → 🤷 Koi naya kharcha nahi` (`AajScreen:1351`), `currentMonthEntries.isEmpty → Is mahine koi kahani nahi` with `GlassCard` (`HisaabScreen:868`), `Sapna empty dashed EmptyGoalCard` (`SapnaScreen:1029` 140dp `dashPathEffect`).
- **Error:** `aiParsingError → PremiumToast longDuration` (`AajScreen:444`), `summaryError → DangerBg + Retry TextButton` (`KahaniScreen:813-843`), `speechError` inline `Danger` `labelSmall` (`AajScreen:858-864`), `aiErrorMessage` / `manualErrorMessage` inline (`AajScreen:849,1171`). `income` / `staff` raw error states missing but core flows covered.
- **Destructive confirms:** Single delete → `AlertDialog "Entry Delete Karein? 🗑️ / Ye entry … delete ho jaayegi"` (`HisaabScreen:148-183`) with slide-left exit animation before actual `deleteDiaryEntry` (`HisaabScreen:185-199`); Account delete double-dialog (`SettingsScreen:909/938`) with re-auth error surfacing. Income/Goal deletes each have dedicated confirm.
- **Disabled & validation:** `AddGoalBottomSheet` validates `name.isNotEmpty && target>0` before `onSave` (`SapnaScreen:884`), `EditGoalBottomSheet withdraw validates >0 && ≤savedAmount` (`SapnaScreen:697-708`), feedback `Submit enabled = isNotBlank && !isSubmitting` (`SettingsScreen:1066`), `Amount keyboardType=Number` + `isDigit()` filter.
- **Motion & privacy a11y:** `rememberReducedMotion()` checks `ANIMATOR_DURATION_SCALE==0` and gates orb drift (`GlassUi:156`), `pulseScale` (`AajScreen:818`), `shimmerOffset` (`KahaniScreen:452`, `SapnaScreen:301`) — correct. `BlurLockable locked → blur 14dp + 🔒 Tap to unlock` + PIN sheet (`AajScreen:1524-1737` 4-dot indicators, default `1234`, glass keyboard) + `IncomeVisibilityManager` global hide-on-background (`MainActivity:91`) is defensible privacy pattern.
- **Nav & keyboard:** `BackHandler(enabled=isKeyboardVisible) { hide() }` (`AajScreen:339`), `LiquidGlassNavBar` `animateDpAsState spring LowBouncy` pill, `ModalBottomSheet confirmValueChange != Hidden` guards against swipe-dismiss loss — thoughtful.

**Failures:**
- **No skeleton / optimistic states:** Only spinners; on slow Firestore + Gemini, lists show blank then pop. No `Skeleton` shimmer card for `Hisaab` `LazyColumn` while `allDiaryEntries` initial `collectAsState` empty — first paint shows empty-state incorrectly before data loads (flash of “Koi kahani nahi”).
- **Offline story breaks:** `syncStatus 🟢/🔄/🔴` is 11sp colored dot + “Online/Syncing/Offline” (`MainActivity:263-279`) — tiny, low contrast (`onSurfaceVariant 0xFF94A3B8`), no banner or disabled AI action. AI parsing (`viewModel.startParsingEntry`) will fail offline with generic toast but CTA stays enabled.
- **Keyboard guard fragility:** 4 screens duplicate `ViewTreeObserver.OnGlobalLayoutListener keypadHeight > screenHeight*0.15` (`AajScreen:325-336`, `HisaabScreen:216-228`, etc). This heuristic fails on foldables/multi-window and leaks listener if `viewTreeObserver` dead. Should use `WindowInsets.isImeVisible` (as partially in `SapnaScreen:913`).
- **Sheet dismiss confusion:** `EditGoalBottomSheet` / `AddGoalBottomSheet` `onDismissRequest { if(isAnyFieldFocused) hideKeyboard else /* do nothing */ }` + `shouldDismissOnBackPress=false` means Back never closes sheet when field not focused — user must find `X` (`SapnaScreen:568-573`, `SettingsScreen:1094-1103`). `X` is 24dp touch target vs 48dp spec.
- **PIN fallback weak:** Default `1234` disclosed as tip (`AajScreen:1729`) and hardcoded verify fallback (`AajScreen:1673`) — trivial brute force; no attempt limiting, no biometric, no `isPinSet()` prompt on first lock.
- **Focus & semantics gaps:** Several `OutlinedTextField` lack `imeAction/done` + `onDone` submit; no `testTag` on AI input field (voice/text is primary flow) but income submit has `testTag`. `CircularProgressIndicator` instances lack `contentDescription` for TalkBack (“Loading”).

**Fix:** Add initial `isLoadingEntries` guard (`viewModel.isDataLoaded` or `entries==null` vs empty) + skeleton `GlassCard` placeholder for first 800ms. Promote offline to banner: when `syncStatus != 🟢`, show `GlassCard DangerBg "Offline — AI features paused"` and `enabled=false` on `Hisaab/Action` CTA. Replace `ViewTreeObserver` heuristic with `WindowInsets.isImeVisible` everywhere; make sheets dismiss on Back when keyboard hidden (`shouldDismissOnBackPress=true` + `BackHandler`). Add PIN attempt throttle (3 tries → 30s cooldown) and prompt to set custom PIN on first lock. Add `imeAction=Done` + `keyboardActions = KeyboardActions(onDone={ startParsing })` to story input.

---

## Files Audited

- `app/src/main/java/com/example/MainActivity.kt` — TopBar (44dp avatar, logout ring, sync dot), LiquidGlassNavBar (hazeChild 30dp blur, sliding pill spring), SplashScreen (pulse 1.03×, 3-dot stagger, AH medallion), ApnaHisaabApp NavHost with slide/fade 350ms, permission & reminder scheduling
- `app/src/main/java/com/example/ui/theme/Palette.kt` — Refined Glass single source, Base/Glow/Glass/Semantic/Mood tokens
- `app/src/main/java/com/example/ui/theme/Theme.kt` — forced-dark `darkColorScheme primary = Palette.mood(mood)`, `LocalThemeIsDark=true`
- `app/src/main/java/com/example/ui/theme/Type.kt` — `BaseTypography` 10 styles, EmojiSupportMatch, SansSerif/Default split
- `app/src/main/java/com/example/ui/theme/Dimens.kt` + `Shapes` — 4dp scale, screenPadding 20, radius/border/elevation tokens
- `app/src/main/java/com/example/ui/components/GlassUi.kt` — AppBackground (3 orbs, reducedMotion), GlassCard/Pill, SectionHeader, BlurLockable (14dp blur)
- `app/src/main/java/com/example/ui/screens/AajScreen.kt` — hero card (44sp amount, progress, BlurLock 16dp), input pill (BasicTextField + 🎤 pulse), mood 5×, quick-add pills, manual fallback LazyRow categories, today transactions coral −₹, PIN sheet, ConfettiBurst, AnimatedAmountText
- `app/src/main/java/com/example/ui/screens/HisaabScreen.kt` — personality card, brandBrush tabs, DiaryEntryCard (mood strip, 4dp indicator, expand/collapse, AI insight), ExpenseRow, month-detail fetch, HisaabLiquidGlassCard
- `app/src/main/java/com/example/ui/screens/KahaniScreen.kt` — income/kharch/bachat card (BlurLock), DonutChart 170dp (12dp legend, gap 2.5°), CategoryBarRow (8dp tracks), MoodStream LazyRow, AI letter (96dp loader, Regenerate mint gradient), Purane Mahine FlowRow expand, WhatsApp share bitmap gen 600×700
- `app/src/main/java/com/example/ui/screens/SapnaScreen.kt` — GoalCard (8dp progress + shimmer), CompletedGoalCard strikethrough, Add/Edit/Savings bottom sheets (shouldDismissOnBackPress=false), dashed EmptyGoalCard, 56dp mint FAB, withdraw/reactivate logic
- `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` — profile 72dp gradient avatar, GlassActionButton pill trio, Kamaai card + Budget & Kamaai merged card with animated progress (budget*0.8 alert), Category Limits expand, Suggest Feature & Income Management sheets, double-confirm delete
- `app/src/main/java/com/example/data/CategoryConfig.kt` — 15 categories with raw hexes + emoji + description, `getCategoryByName` fallback
- `app/src/main/java/com/example/ui/theme/Palette.kt:62-69` + `ui/screens/KahaniScreen:1752-1760` — dual mood color sources

---

## Registry Safety

Skipped — Android Compose project (no `components.json` / shadcn registries). No third-party UI registry audit applicable.

---

## Appendix — Raw grep evidence (code-only signals)

- `grep "Color(0x" app/src --include="*.kt"` → 18 hits: 15 in `CategoryConfig.kt`, 2 in `KahaniScreen:getMoodAttributes` (`0xFF...`), 1 in `Theme.kt: surface 0xFF161B22` — all outside `Palette.kt`.
- `grep "fontSize =" app/src --include="*.kt"` → 22 inline overrides (Aaj 8, Kahani 6, Sapna 4, Settings 3, Hisaab 1).
- `grep "\.dp" app/src/main/java/com/example/ui/screens/AajScreen.kt` → values outside `Dimens`: `14, 10, 6, 18, 44, 40, 52, 140, 120`.
- `grep "isLoading|isSummaryLoading|loadingMonthKey|ParsingState"` → loading states present (5 distinct), but no `Skeleton` / `isDataLoaded` guard.
- `grep "contentDescription"` → 12 Icon usages have it; 9 `Box(clickable)` wrapping `Text(emoji)` do not.
- `grep "ANIMATOR_DURATION_SCALE|rememberReducedMotion"` → 6 gates ✅.
- `grep "BlurLockable|IncomeVisibilityManager"` → 9 usages across 4 screens — privacy pattern consistently applied.

---

## Reviewer Notes

The glass system is not a moodboard — it shipped. `Palette` + `Dimens` + `GlassCard/Pill` + `HazeState` + `AppBackground` are disciplined, and the Hinglish voice (with emoji warmth + self-aware humor like `AI abhi thak gayi`) is a genuine differentiator. The downgrades are discipline, not taste: raw hexes/categories, inline type overrides, and ad-hoc spacing will compound as more screens are added. Lock the tokens with a grep gate now, before the next milestone widens the gap.

**Recommendation:** Fix Color P0 + Typography P1 + Spacing P2 in one “token lockdown” pass (est. 1–2 days, no design change) — afterwards re-audit to 20+/24.

