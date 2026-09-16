---
status: testing
phase: 01-apna-hisaab
source: README.md, AajScreen.kt, HisaabScreen.kt, KahaniScreen.kt, SapnaScreen.kt, SettingsScreen.kt
started: 2026-09-16T13:45:00Z
updated: 2026-09-16T13:45:00Z
---

## Current Test

number: 1
name: Cold Start Smoke Test
expected: |
  Kill app, clear temp state, launch Apna Hisaab. Splash shows "AH" medallion + "Apna Hisaab" + "Ek line likho, baaki hum samjhe" for 2s, then onboarding (if first install) or Aaj screen with greeting "Aaj ka hisaab dekhein!" / personalized greeting, Today's header shows date. No crash.
awaiting: user response

## Tests

### 1. Cold Start Smoke Test
expected: Kill app, clear temp state, launch Apna Hisaab. Splash shows "AH" medallion + "Apna Hisaab" + "Ek line likho, baaki hum samjhe" for 2s, then onboarding (if first install) or Aaj screen with greeting "Aaj ka hisaab dekhein!" / personalized greeting, Today's header shows date. No crash.
result: [pending]

### 2. Aaj - Single Expense via Hinglish
expected: On Aaj screen, type "200 ki chai piya" with mood Khush, tap "Hisaab/Action". AI parses amount 200, category Khana/Masti, entry appears in Today's list with ₹200, mood captured, toast "₹200 kharch" shows.
result: [pending]

### 3. Aaj - Multi-Intent (Expense + Saving)
expected: Type "100 ki chai pi aur 500 bachaye" and submit. Two actions logged: ₹100 expense + ₹500 saving. Hisaab shows ₹100 entry, Sapna/income reflects ₹500 saving (if goal). Single toast combined "₹100 kharch aur ₹500 bachaye!"
result: [pending]

### 4. Aaj - Mood Selector + Voice Input
expected: Mood row shows 5 emojis (😊 Khush etc), selected mood has teal/purple border + 1.08 scale + alpha 1.0, others 0.4. Tap mic 🎤 toggles listening, asks RECORD_AUDIO permission if needed, speech fills input. Blur lock "🔒 Tap to unlock" works.
result: [pending]

### 5. Aaj - Hero Card & Budget Progress
expected: Top hero GlassCard shows "Aaj ka Kharcha" + large ₹ amount (44sp Black) + motivational line (Teal), budget progress bar + "Budget: X% / Mahina: ₹Y". Privacy blur + lock toggles correctly. Quick-add pills (Chai/Khana/Safar/Masti) fill input on tap.
result: [pending]

### 6. Hisaab - List, Running Total & Edit
expected: Hisaab screen lists diary entries with date/mood caps, category emoji, amount, running total correct. Tap entry expands to show AI insight + edit/delete. Delete shows "Entry Delete Karein? 🗑️" confirm, slides out left before deletion.
result: [pending]

### 7. Kahani - Monthly Story & Insights
expected: Kahani shows income/kharch/bachat cards (blur-lockable), DonutChart 170dp centered + legend, CategoryBarRow 8dp tracks, MoodStream LazyRow. Tap "Kahani Generate" gives 3-4 sentence Hinglish paragraph starting "Yaar, is mahine...". Empty month shows "Is mahine koi kahani nahi".
result: [pending]

### 8. Sapna - Goals Create & Complete
expected: Sapna shows GoalCards with 8dp progress + shimmer, dashed EmptyGoalCard if none, FAB 56dp bottom-right above glass nav. Create goal via bottom sheet (validates name + target>0), progress updates on saving, complete shows celebration, withdraw validates ≤ savedAmount.
result: [pending]

### 9. Settings - Profile, Income, Budget & Logout
expected: Settings shows 72dp gradient avatar, name/email, streak. Edit name via dialog, add income (source/type/amount) via sheet, category limits expand. Logout shows "Logout karna chahte ho? / Haan Logout / Nahi Rukna Hai" and returns to Login. Delete account double-confirm works.
result: [pending]

### 10. Navigation, Glass NavBar & System
expected: LiquidGlassNavBar 64dp with 5 tabs (Aaj/Hisaab/Kahani/Sapna/Settings), sliding pill spring LowBouncy, haze blur 30dp, top specular. Back handling: Back on non-Aaj goes to Aaj, Back on Aaj does not exit if keyboard visible (hides keyboard). Splash 2s, TopBar initials + sync dot 🟢/🔄/🔴 correct. Reduced-motion gates orbs/pulse.
result: [pending]

## Summary

total: 10
passed: 0
issues: 0
pending: 10
skipped: 0
blocked: 0

## Gaps

