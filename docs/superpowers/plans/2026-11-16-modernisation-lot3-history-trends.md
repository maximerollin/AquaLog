# Modernisation lot 3 : Historique et Tendances — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild History (« Chronologie ») and Trends in the immersive grammar of variant C. Both screens get a compact water header under the status bar, a paper sheet and a pill period selector.
- History: the timeline gets a node per event and Session cards whose readings show as pills.
- Trends: a hero card for the parameter to watch (curve, range band, water-change markers, « Repère, pas un diagnostic »), then the other parameters as rows that open on their curve, with the Pro analysis below.

**Architecture:**
- **Pure logic.** The hero parameter, its change over the period and a value-based range state are pure functions, unit-tested on the JVM. The range state is extracted from lot 2's `sessionTileState`, so tiles and the hero say the same words.
- **Shared components.** New ones go in `ImmersiveComponents.kt`: `PeriodSegmented`, `ValuePill`, `TimelineNode`. `WaterHeader` gains an action slot instead of a fixed close button, and the Session passes its close button through it.
- **Screens.** `TimelineScreen` and `TrendsScreen` keep one `LazyColumn` each (`timeline-list`, `trend-list`): item 0 is the header (`overlapNext(30.dp)`), then `PaperSheetTop`, then content. The shell stops padding History and Trends for the status bar, and both call `StatusBarOverHeader(listState)` as the Accueil and the Session do.
- **Out of scope.** More stays as it is until lot 4.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (material3 1.12.0-alpha03), compose resources, kotlin.test + `runComposeUiTest` on JVM, AndroidX Compose UI tests on the Pixel 9a emulator.

**Spec:** `docs/superpowers/specs/2026-11-10-modernisation-immersive-design.md` (§3, §4.3, §4.4, §5, §6, §7 lot 3).

**Mockup:** `docs/design/21-modernisation/prototype-modernisation.html` on branch `prototype/21-modernisation`, variant C, screens `history()` and `trends()`.

**Built in lots 1–2 (on `main`, `6949f61`):**
- `ImmersiveComponents.kt`: `WaterHeader`, `PaperSheetTop`, `GlassIconButton`, `LevelMark`, `overlapNext`, `levelContainerColor`, `FloatingNavBarClearance`;
- `StatusBarOverWater.kt`: `StatusBarOverHeader`;
- `SessionTiles.kt`: `sessionTileState`, `SessionTileState.text()`;
- `TrendSparkline.kt`: `TrendSparklineRow`;
- `MaintenanceActionType.icon()` (non-null) and the `FlaskConical` icon.

## Global Constraints

- Palette, fonts and `TankScene` drawing unchanged.
- **Headers.** About 170 dp, `TankScene` of the shown Aquarium (`state.setup.aquarium`) with no fish, still (`animate = false`), never the photo (spec §3, §8.2). They run edge to edge under the status bar, with light icons while the header is under it and a paper band once scrolled.
- White text on water ≥ 4.5:1 (lot 2's `WaterHeaderShade` test covers the header).
- **Wording.** State is factual and never shown by colour alone. Out-of-range values show lot 1's `LevelMark` and words (« Au-dessus de la plage »).
- **Charts.** Their current descriptions and alt text are kept (spec §4.4): `trends_chart_description`, the sparkline row's spoken reading, the exact-values list, and the events list.
- **Size and access.** Targets ≥ 48 dp. Layouts hold at font scale 1.5 in German.
- **Strings.** Every new visible string goes in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`), with terms from `docs/localization-glossary.md`. No literals (`LocalizationContractTest`), no emoji. No existing string value changes, so the History title stays `timeline_title` (« Chronologie »).
- **Unchanged.** No change to Room schema, sync payloads, export format, `TimelineUiState` / `TrendsUiState` fields or `HomeViewModel` behaviour. The existing ViewModel calls are reused.
- **Scrolling.** `timeline-list` and `trend-list` are each their screen's only vertical scrollable.
- **Desktop harness.** It is checked by launching it (`./gradlew :ui:run`); it has no tests.

## Review Focus

1. **Empty and short data.**
   - A History with no event shows the existing empty text inside the sheet.
   - A Trends snapshot with no measurement shows no hero card, the rows « — » and `EmptyTrendCard`.
   - A hero with one point has no « change » line.

   Pinned in Task 1 (`heroSeries`, `periodChange`) and Task 4 (empty test).
2. **Which parameter is the hero.**
   - Rule: the first series, by position, whose latest value is not OK; else the first series with points; else none.
   - The hero is not repeated in the rows below.

   Pinned in Task 1 and Task 4.
3. **Filters and period after the move.**
   - The filter sheet no longer offers the period, which the segmented control on the sheet now owns.
   - The header subtitle names the active Aquarium, parameter and action filters.
   - Changing the period keeps the other filters.

   Pinned in Task 3.
4. **Custom period for a free user.** The header calendar button opens the Pro offer (`openProPaywall`), never an empty sheet. For Pro it opens the custom-period sheet with the existing date fields, apply button and « complete history ». Pinned in Task 4.
5. **German at 150 %.**
   - The 4-option period control wraps no label mid-word: options keep one line, and the control may scroll horizontally.
   - Session cards keep their pills inside the card.
   - The hero value and state pill do not overlap.

   Pinned in Task 2 (segmented test) and Task 4, and checked in Task 6.

---

### Task 1: Hero logic, range state and strings

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/SessionTiles.kt` (extract `valueRangeState`)
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TrendHero.kt` (logic only in this task)
- Modify: `ui/src/commonMain/composeResources/values{,-en,-fr,-de,-it,-es}/strings.xml`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/SessionTilesTest.kt`, `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/TrendHeroTest.kt`

**Interfaces:**
- Produces:
  - `internal fun valueRangeState(value: Double, definition: ParameterDefinition, profile: AquariumProfile): SessionTileState`
    - It returns IN_RANGE, ABOVE, BELOW, FAR_ABOVE, FAR_BELOW or NO_RANGE.
    - It uses exactly the rules `sessionTileState` applies to a valid value today, including the alert-threshold side for one-sided ranges.
    - `sessionTileState` delegates to it after its blank and invalid checks.
  - `internal fun heroSeries(snapshot: TrendSnapshot): ParameterTrendSeries?`
    - Among `snapshot.series` sorted by `definition.position`: the first whose last point's `valueRangeState(…, snapshot.aquarium.profile)` is not IN_RANGE or NO_RANGE.
    - Else the first with at least one point.
    - Else null.
  - `internal fun periodChange(series: ParameterTrendSeries): Double?`: last point's value minus first point's value. Null with fewer than 2 points.
  - `internal fun windowDays(snapshot: TrendSnapshot): Int`: `((windowEnd − windowStart) / 86_400_000)` rounded, at least 1.
  - New strings: `trends_custom_period`, `trends_hero_legend`, plurals `trends_hero_change_days`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// SessionTilesTest
@Test fun `value range state matches the tile rules`() // valueRangeState(7.8, ph 6.5–7.5) == ABOVE; (4.0, ph max-only 7.5) == FAR_BELOW; (7.0, ph no range) == NO_RANGE
// TrendHeroTest — build TrendSnapshot/ParameterTrendSeries/TrendPoint directly
@Test fun `hero is the first parameter to watch by position`()   // temp ok (pos 0), pH 7.8 (pos 1), nitrate 60 (pos 3) → pH
@Test fun `hero falls back to the first measured parameter`()     // all in range, temp has no points, pH has points → pH
@Test fun `no hero without any measurement`()                     // every series empty → null
@Test fun `change needs two points`()                             // [7.1, 7.8] → 0.7 (±1e-9); [7.8] → null
@Test fun `window days of a 30 day snapshot`()                    // window 30 × 86_400_000 → 30
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*TrendHeroTest*' --tests '*SessionTilesTest*'`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Add the strings**

| key | values / -en | -fr | -de | -it | -es |
|---|---|---|---|---|---|
| `trends_custom_period` | Custom period | Période personnalisée | Eigener Zeitraum | Periodo personalizzato | Periodo personalizado |
| `trends_hero_legend` | ▲ Water change · Reference, not a diagnosis | ▲ Changement d'eau · Repère, pas un diagnostic | ▲ Wasserwechsel · Orientierung, keine Diagnose | ▲ Cambio d'acqua · Riferimento, non una diagnosi | ▲ Cambio de agua · Referencia, no un diagnóstico |
| plurals `trends_hero_change_days` one / other | %1$s over %2$d day / %1$s over %2$d days | %1$s en %2$d jour / %1$s en %2$d jours | %1$s in %2$d Tag / %1$s in %2$d Tagen | %1$s in %2$d giorno / %1$s in %2$d giorni | %1$s en %2$d día / %1$s en %2$d días |

Check the water-change term against the glossary and the existing `action_water_change` value in each catalog; use that exact term.

- [ ] **Step 4: Implement the functions**

- [ ] **Step 5: Run the tests and the localization contract**

Run: `./gradlew :ui:jvmTest --tests '*TrendHeroTest*' --tests '*SessionTilesTest*' --tests '*LocalizationContractTest*'`
Expected: PASS.

- [ ] **Step 6: Commit** — `feat(trends): hero parameter, change over the period and shared range state`

---

### Task 2: Shared pieces — period control, value pill, timeline node, header action

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/ImmersiveComponents.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/RapidSessionUi.kt` (Session passes its close button through the new slot)
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/icon/Calendar.kt` (Lucide `calendar`, ISC, same builder pattern as `Funnel.kt`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/ImmersiveSemanticsTest.kt`

**Interfaces:**
- Consumes: `valueRangeState`, `SessionTileState.text()` (Task 1); `LevelMark`, `levelContainerColor`, `levelTextColor` (lots 1–2).
- Produces:
  - `WaterHeader(aquarium: Aquarium, title: String, subtitle: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}, below: @Composable () -> Unit = {})`
    - `closeLabel` / `onClose` are removed. `action` is drawn where the close button was.
    - `RapidSessionScreen` passes `action = { GlassIconButton(AquagendaIcons.X, cancel, closeRapidSession) }`.
  - `@Composable fun PeriodSegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`
    - A pill row: 48 dp tall, `surfaceVariant`-tinted track, the selected option in a `surface` pill with a shadow.
    - Each option is `Role.RadioButton` + `selected`, with `selectableGroup()` on the row.
    - Labels stay on one line. When they don't fit, the row scrolls horizontally rather than wrapping.
  - `@Composable fun ValuePill(label: String, value: String, unit: String?, state: SessionTileState, modifier: Modifier = Modifier)`
    - A 14 dp-corner pill, 6 × 10 dp padding.
    - Fill: `levelContainerColor` when out of range, else `surfaceVariant`.
    - Text: « label value unit » at 13 sp, the value semibold.
    - `LevelMark` before the label when out of range.
    - Decorative for semantics (`clearAndSetSemantics {}`), because the Session card speaks its readings itself.
  - `@Composable fun TimelineNode(icon: ImageVector, emphasized: Boolean, modifier: Modifier = Modifier)`
    - A 36 dp circle with an 18 dp icon: `primary` / `onPrimary` when emphasized, else `secondaryContainer` / `primary`.
    - Decorative.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `period options are radio buttons with one selected and 48 dp`()   // labels ["All time","7 days","30 days","90 days"], selected 2 → 4 nodes Role.RadioButton, only "30 days" selected, each ≥ 48 dp; click "7 days" → onSelect(1)
@Test fun `german period labels stay on one line at 150 percent`()            // de labels ["Gesamter Zeitraum","7 Tage","30 Tage","90 Tage"], fontScale 1.5, width 360 dp → each label's TextLayoutResult.lineCount == 1
@Test fun `water header shows its action`()                                    // action = GlassIconButton(Funnel, "Filters", {}) → node "Filters" exists, ≥ 48 dp; no "Cancel" node
@Test fun `an out of range value pill carries the mark`()                     // ValuePill("pH","7.8",null, ABOVE) contains LEVEL_MARK_TAG (unmerged); IN_RANGE pill does not
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*ImmersiveSemanticsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement**

Implement the components, the `WaterHeader` slot change with the Session call site, and the `Calendar` icon.

- [ ] **Step 4: Run the JVM suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS. The Session's tests are still green.

- [ ] **Step 5: Commit** — `feat(ui): period control, value pill, timeline node and a header action slot`

---

### Task 3: History

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TimelineUi.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (`AquariumShell`: HISTORY gets `Modifier`, not `tabModifier`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/TimelineSemanticsTest.kt`

**Interfaces:**
- Consumes:
  - `WaterHeader`, `PeriodSegmented`, `ValuePill`, `TimelineNode`, `PaperSheetTop`, `overlapNext`, `StatusBarOverHeader`, `FloatingNavBarClearance`;
  - `valueRangeState`;
  - the existing `viewModel.toggleTimelineFilters`, `selectTimelinePeriod`, `openTimelineSession` and `retryTimeline`.
- Produces:
  - `@Composable internal fun TimelineSessionCard(entry: TimelineSession, showAquarium: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - test tag `timeline-list`.

**List order in `timeline-list`:**
1. `WaterHeader` with `overlapNext(30.dp)`:
   - title `timeline_title`;
   - subtitle: the active filter context (Aquarium name or `timeline_all_aquariums`, then the parameter label, then the action label), joined with « · ». The period is no longer part of it;
   - `action = GlassIconButton(AquagendaIcons.Funnel, timeline_filters, toggleTimelineFilters)`.
2. `PaperSheetTop`. Every later item has `background(colorScheme.background)` and 16 dp side padding.
3. `PeriodSegmented`: the `TimelinePeriod.entries` labels (`timeline_period_*`), wired to `selectTimelinePeriod`.
4. Loading, error or empty states, as today, inside the sheet.
5. The timeline, grouped by day with the existing date heading.

   Each entry is a row: the `TimelineNode` on the left, with a 2 dp `outlineVariant` line joining the nodes of the same day, then its content.
   - **Session:** a `FlaskConical` node, emphasized, then a `TimelineSessionCard`. The card has a `surface` fill, 20 dp corners, a soft shadow and 12 × 14 dp padding, and contains:
     - the title `timeline_session` (plus the Aquarium name when `showAquarium`) and the time;
     - a `FlowRow` of `ValuePill`s, one per reading in position order, with `valueRangeState` for the state;
     - a `FlowRow` of non-interactive 28 dp action chips, each with `type.icon()` and today's `summary` text;
     - the event notes as today.

     The card keeps today's semantics: `clickable(role = Button)`, `contentDescription = open_session_details`, and `stateDescription` = today's spoken readings summary.
   - **Inhabitant:** a `Fish` node, not emphasized, with today's line and time.
   - **Task:** a `Check` node, not emphasized, with today's line.

The filter sheet (`TimelineFiltersSheet`) loses its period group. The Aquarium, parameter and action groups stay as today.

**Screen chrome:**
- `contentPadding` bottom = `FloatingNavBarClearance.dp`.
- `StatusBarOverHeader(listState)`.
- No status-bar padding: the shell passes `Modifier`.

- [ ] **Step 1: Write the failing semantics tests** (render `TimelineSessionCard` with a built `TimelineSession`, locale ENGLISH, `AquaLogTheme`)

```kotlin
@Test fun `a session card is one button that speaks its readings`()   // node with contentDescription "Open Session details", Role.Button, stateDescription contains "7.8" and the attention cue
@Test fun `readings show as pills with the out of range one marked`()  // LEVEL_MARK_TAG count (unmerged) == number of out-of-range readings (1 of 3)
@Test fun `the filter sheet no longer offers the period`()             // TimelineFiltersSheet rendered (internal) → no node "7 days"; node "All Aquariums" exists
```

Make `TimelineFiltersSheet` `internal` for this test.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*TimelineSemanticsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the screen, the card, the sheet change and the shell routing for HISTORY**

- [ ] **Step 4: Run the JVM suite and the Android build**

Run: `./gradlew :ui:jvmTest :androidApp:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit** — `feat(history): water header, period control, timeline nodes and Session cards with value pills`

---

### Task 4: Trends

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TrendsUi.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TrendHero.kt` (composables)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (TRENDS gets `Modifier`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/TrendHeroSemanticsTest.kt`

**Interfaces:**
- Consumes: Task 1 logic and strings; Task 2 components; the existing `TrendChart`, `TrendSparklineRow`, `trendDetails`, `TrendProRow`, `AdvancedAnalysis` and `EmptyTrendCard`, plus the ViewModel calls.
- Produces:
  - `@Composable internal fun TrendHeroCard(series: ParameterTrendSeries, snapshot: TrendSnapshot, expanded: Boolean, onToggleValues: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable internal fun CustomPeriodSheet(state: HomeUiState, viewModel: HomeViewModel)`

**List order in `trend-list`:**
1. `WaterHeader` with `overlapNext(30.dp)`:
   - title `trends_title`, subtitle the snapshot's Aquarium name (or the setup Aquarium's);
   - `action = GlassIconButton(AquagendaIcons.Calendar, trends_custom_period, onClick)`. `onClick` opens `CustomPeriodSheet` (local `showCustomPeriod` state) when `state.pro.purchaseStatus == ProPurchaseStatus.ACTIVE`, else `viewModel.openProPaywall()`.
2. `PaperSheetTop`. Every later item has `background(colorScheme.background)` and 16 dp side padding.
3. `PeriodSegmented` over the `TrendPeriod.entries` labels, wired to `selectTrendPeriod`. It replaces `TrendPeriods`.
4. Loading and error states, as today, inside the sheet.
5. `heroSeries(snapshot)`: when non-null, the `TrendHeroCard`. The card has a `surface` fill, 24 dp corners, a shadow and 14 dp padding, and contains, top to bottom:
   - a row with the parameter name (15 sp semibold) and, at the end, a `ValuePill`-styled state chip with the words of `valueRangeState(last, …).text()`;
   - the last value at 40 sp semibold, with `levelTextColor` when out of range and the unit at half size;
   - then `trends_hero_change_days` with the signed `periodChange` (formatter, `+` sign when positive) and `windowDays`, muted, only when `periodChange` is non-null;
   - the existing `TrendChart(series, snapshot)` (range band, water-change markers, `trends_chart_description`);
   - `trends_hero_legend`, at 11 sp, muted;
   - a 48 dp text button `trends_exact_values` that toggles `selectTrendParameter(hero id)`.

   When the hero is selected, the existing exact values and events from `trendDetails` follow the card, without its heading and chart.
6. The other series, in position order and without the hero, as today's `TrendSparklineRow`s with their expand-on-tap `trendDetails`.
7. `trends_sparkline_legend`, as today.
8. `TrendProRow`, without the custom date fields and the « complete history » button, which move into `CustomPeriodSheet`. It keeps the free-limit text and the paywall link.
9. `AdvancedAnalysis`, as today.

When the snapshot has no series with points, there is no hero: the rows show « — » and `EmptyTrendCard` follows the period control.

**`CustomPeriodSheet`:** a `ModalBottomSheet` with today's « complete history » button, the two date fields with their hints, the apply button and the invalid-period error, all wired to the existing ViewModel calls.

**Screen chrome:**
- `contentPadding` bottom = `FloatingNavBarClearance.dp`.
- `StatusBarOverHeader(listState)`.
- The shell passes `Modifier` for TRENDS.

- [ ] **Step 1: Write the failing semantics tests** (render `TrendHeroCard` with a built snapshot, ENGLISH)

```kotlin
@Test fun `hero says the state in words and the change over the period`() // pH points [7.1, 7.8], range 6.5–7.5, 30-day window → text "Above the range"; text "+0.7 over 30 days"; LEVEL_MARK_TAG present
@Test fun `one point gives no change line`()                                // [7.8] → no node containing "over"
@Test fun `the chart keeps its description`()                               // node with contentDescription == trends_chart_description for pH
@Test fun `exact values toggle is a 48 dp button`()                          // node "Exact values" (trends_exact_values en value) ≥ 48 dp, click → onToggleValues called
@Test fun `german at 150 percent keeps value and state apart`()             // de, fontScale 1.5, width 360 dp → state chip bounds and value bounds do not intersect
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*TrendHeroSemanticsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the card, the sheet, the new list body and the shell routing for TRENDS**

- [ ] **Step 4: Run the JVM suite and the Android build**

Run: `./gradlew :ui:jvmTest :androidApp:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Launch the desktop harness**

Run `./gradlew :ui:run` in the background. Check that it starts with no exception in the log, then stop it.

- [ ] **Step 6: Commit** — `feat(trends): water header, hero card for the parameter to watch, rows below and a custom period sheet`

---

### Task 5: Device tests follow History and Trends

**Files:**
- Modify: the androidTests that reach these screens: `TrendsFlowTest`, `TimelineFlowTest`, `PistesFlowTest`, `MultiAquariumFlowTest`, `RapidSessionFlowTest`, `TaskFlowTest`, `LocalizedAccessibilityFlowTest` and `SupabaseTwoDeviceSyncTest`. Also any other that compiling or running shows to be affected.

**Interfaces:**
- Consumes: tags `timeline-list` and `trend-list`; the filter button by label `Filters` (`hasLabel`); the period control options by text; `TrendHeroCard`'s « Exact values » button; and, for Pro, the custom period behind the `Custom period` header button.

- [ ] **Step 1: Update the lookups**

Make these changes, keeping each test's intent:
- The period is chosen on the screen's segmented control, no longer in the filter sheet.
- The Pro custom-period fields and « complete history » are reached through the header's « Custom period » button.
- The hero parameter's exact values are reached through its « Exact values » button. The other parameters still open by tapping their row.
- Scrolling goes through `onNodeWithTag("timeline-list")` / `onNodeWithTag("trend-list")`, never `hasScrollAction()`.
- A tap on an item near the bottom of these lists first clears the floating bar: generalise `scrollHomeAboveBar` to `scrollAboveBar(listTag: String, matcher: SemanticsMatcher)` and keep `scrollHomeAboveBar` as a call to it.

- [ ] **Step 2: Run the connected classes, then the whole suite**

Run the classes listed above:

```bash
./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.TrendsFlowTest,com.maximerollin.aqualog.TimelineFlowTest,com.maximerollin.aqualog.PistesFlowTest,com.maximerollin.aqualog.MultiAquariumFlowTest,com.maximerollin.aqualog.RapidSessionFlowTest,com.maximerollin.aqualog.TaskFlowTest,com.maximerollin.aqualog.LocalizedAccessibilityFlowTest
```

Then run `./gradlew :androidApp:connectedDebugAndroidTest`.

Expected: PASS. The env-gated Supabase, Play and installation tests are skipped as today. Report any other failure, with a run on `main` to show whether it already exists there.

- [ ] **Step 3: Commit** — `test: reach the new History and Trends`

---

### Task 6: Verification and captures

**Files:**
- Create: `docs/design/21-modernisation/lot3-captures/` (PNG)

- [ ] **Step 1: Full suites**

Run: `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: BUILD SUCCESSFUL. Lint warnings equal `main`'s.

- [ ] **Step 2: Captures on the Pixel 9a emulator**

Use `adb exec-out screencap -p`, then `sips -Z 1200`. Seed « Salon », 120 L, Planté / crevette, with three Sessions on different days:
- pH 7,1, 7,4 and 7,8;
- one water change;
- one inhabitant added.

Captures:
- **Before** (from `main` at `6949f61`, built in a separate worktree): History FR light, Trends FR light.
- **After:**
  - History: FR light, FR light with the filter sheet open, FR dark, DE at 150 %.
  - Trends: FR light (hero pH « Au-dessus de la plage », « +0,7 en N jours »), FR light with the hero's exact values open, FR dark, DE at 150 %, and free user with the calendar button opening the Pro offer.

Expected:
- light status-bar icons over the headers and the paper band once scrolled;
- pills inside the cards;
- nothing hidden under the floating bar;
- no clipped text.

- [ ] **Step 3: Accessibility dump** (`adb shell uiautomator dump`)

Expected:
- the period options as selectable items, with one selected;
- each Session card as one button labelled with the existing `open_session_details` (« Détails de la session ouverte »);
- the filter button « Filtres » and the calendar button « Période personnalisée »;
- the hero chart's content description present.

- [ ] **Step 4: Commit the captures** — `docs: lot 3 before/after captures`
