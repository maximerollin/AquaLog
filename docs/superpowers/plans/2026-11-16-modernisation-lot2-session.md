# Modernisation lot 2 : la Session en tuiles — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Session screen in the immersive grammar of variant C:
- a compact water header (title, Aquarium, close, time chip);
- a paper sheet with a 2-column grid of measurement tiles that keeps the system numeric keyboard and the « Suivant » chain;
- maintenance chips with icons;
- dashed « Ajouter une observation » rows;
- a save button that counts the measurements.

**Architecture:**
- Pure tile logic (state wording, column count, measurement count) lives in a new `SessionTiles.kt` and is unit-tested on the JVM.
- Each tile *is* the measurement's `BasicTextField`: its decoration box draws the tile. A tap anywhere on the tile focuses the field, and the tile stays one accessible node with the existing `contentDescription` and test tag.
- The header reuses lot 1's pieces. `overlapNext` moves to `ImmersiveComponents.kt`, and the home's status-bar logic becomes a shared `StatusBarOverHeader`. The new `WaterHeader` is the compact header the spec lists in §6.
- `RapidSessionScreen` keeps its single `LazyColumn` (`rapid-session-list`). The header is item 0, laid out 30 dp shorter so the sheet overlaps it. The tiles come in rows of two (or one).

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (material3 1.12.0-alpha03), compose resources, kotlin.test + `runComposeUiTest` on JVM, AndroidX Compose UI tests on the Pixel 9a emulator.

**Spec:** `docs/superpowers/specs/2026-11-10-modernisation-immersive-design.md` (§3, §4.2, §5, §6, §7 lot 2). Mockup: `docs/design/21-modernisation/prototype-modernisation.html` on branch `prototype/21-modernisation`, variant C, screen « Session ». Run `git show prototype/21-modernisation:docs/design/21-modernisation/prototype-modernisation.html > /tmp/proto.html`, then read `C.session()`. Lot 1 (merged, `2ae6bf3`) provides `ImmersiveComponents.kt`, `HomeHero.kt`, `StatusBarOverWater` and the floating bar.

## Global Constraints

- Palette, fonts and `TankScene` drawing unchanged. Reuse lot 1 tokens: `levelContainerColor`, `HeroTextBacking`, `GlassIconButton`, `PaperSheetTop`.
- Header: about 170 dp, `TankScene` with **no fish**, and never the photo (spec §3, §8.2). It runs edge to edge under the status bar, with light status-bar icons while it is under them.
- White text on water ≥ 4.5:1, pinned by a JVM contrast test as in lot 1 (`ScenePhysicsTest`).
- Keep the system numeric keyboard (`KeyboardType.Decimal`) and the `ImeAction.Next` chain from tile to tile; the last tile is `ImeAction.Done`. The active tile has a 2 dp `primary` ring.
- Tile state is never shown by colour alone. The state line says it in words, and out-of-range tiles also carry lot 1's `LevelMark`.
- German at 150 %: the grid drops to 1 column (spec §5). Targets ≥ 48 dp. The scene is still when the system reduces motion.
- Every new visible string goes in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`), with terms from `docs/localization-glossary.md` (Measurement = Mesure / Messung / Misurazione / Medición). No literals (`LocalizationContractTest`), no emoji. No existing string value changes, so the title stays `rapid_session_title` / `edit_session`.
- No change to Room schema, sync payloads, export format, `HomeViewModel` behaviour, `SaveCheckDialog`, or `RapidSessionUiState`.
- `rapid-session-list` stays the screen's only vertical scrollable. Field test tags `rapid-measurement-<storageValue>` and descriptions « Measurement for X » are kept.
- Desktop harness: check it by launching `./gradlew :ui:run`. It has no tests.

## Review Focus

1. **Keyboard chain across two columns.** « Suivant » goes left → right, then to the next row. The focused tile scrolls into view above the keyboard and the save button. Pinned in Task 3 (focus-order test) and checked in Task 6 (capture with the keyboard open).
2. **Editing an existing Session.** The title is « Modifier la session », the button reads « Enregistrer les modifications » with no count, and the prefilled tiles show their state. Pinned in Task 4 (button label rule test) and checked in Task 6.
3. **Parameter without a range.** A valid value shows no state line, and never « Dans la plage ». Pinned in Task 1.
4. **Invalid input (« 7,8,1 »).** The tile shows the invalid-number text in the error colour, and the count excludes it. Pinned in Task 1 (count) and Task 3 (text).
5. **German at 150 %.** One column, long state lines wrap instead of clipping, and the close button and time chip keep 48 dp targets. Pinned in Task 1 (`sessionColumns`) and Task 3 (layout test), and checked in Task 6.

---

### Task 1: Tile logic and strings

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/SessionTiles.kt` (logic only in this task)
- Modify: `ui/src/commonMain/composeResources/values{,-en,-fr,-de,-it,-es}/strings.xml`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/SessionTilesTest.kt`

**Interfaces:**
- Produces:
  - `enum class SessionTileState { EMPTY, INVALID, IN_RANGE, ABOVE, BELOW, FAR_ABOVE, FAR_BELOW, NO_RANGE }`
  - `internal fun sessionTileState(input: String, definition: ParameterDefinition, profile: AquariumProfile): SessionTileState`
    - Blank input gives EMPTY.
    - Input that `evaluateMeasurement` rejects gives INVALID.
    - A definition with neither indicative bound gives NO_RANGE.
    - Otherwise the side comes from `indicativeMinimum` / `indicativeMaximum`, and the FAR_ variants apply when `readingLevel(...) == ReadingLevel.ALERT`.
  - `internal fun sessionColumns(fontScale: Float, maxWidth: Dp): Int`: 1 when `fontScale >= 1.3f` or `maxWidth < 340.dp`, else 2.
  - `internal fun validMeasurementCount(inputs: Map<String, String>, definitions: List<ParameterDefinition>): Int`: non-blank, valid inputs of the given definitions.
  - `@Composable internal fun SessionTileState.text(): String?`: null for NO_RANGE, else the strings below (INVALID → existing `invalid_measurement`).
  - Strings:
    - `session_tile_tap_to_enter`, `session_tile_in_range`, `session_tile_above`, `session_tile_below`, `session_tile_far_above`, `session_tile_far_below`;
    - plurals `save_session_count`.

- [ ] **Step 1: Write the failing tests** (build `ParameterDefinition`s the way the existing `HomeOverviewTest` does)

```kotlin
@Test fun `blank is empty and garbage is invalid`() // "" → EMPTY; "7,8,1" → INVALID
@Test fun `ph 7_8 over 6_5–7_5 is above, 6_0 is below, 7_0 in range`()
@Test fun `nitrite far outside is far above`()   // value giving ReadingLevel.ALERT on ESTABLISHED → FAR_ABOVE
@Test fun `no indicative range never reads in range`() // both bounds null, "12" → NO_RANGE
@Test fun `one column at 150 percent or under 340 dp`() // (1.5f, 412.dp) → 1; (1.0f, 330.dp) → 1; (1.0f, 412.dp) → 2
@Test fun `count skips blank, invalid and inactive inputs`() // {ph:"7,8", kh:"", gh:"x", other-id:"5"} with [ph, kh, gh] → 1
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*SessionTilesTest*'`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Add the strings**

| key | values / -en | -fr | -de | -it | -es |
|---|---|---|---|---|---|
| `session_tile_tap_to_enter` | Tap to enter | Toucher pour saisir | Zum Eingeben tippen | Tocca per inserire | Toca para introducir |
| `session_tile_in_range` | In range | Dans la plage | Im Richtbereich | Nell’intervallo | En el rango |
| `session_tile_above` | Above the range | Au-dessus de la plage | Über dem Richtbereich | Sopra l’intervallo | Por encima del rango |
| `session_tile_below` | Below the range | En dessous de la plage | Unter dem Richtbereich | Sotto l’intervallo | Por debajo del rango |
| `session_tile_far_above` | Far above the range | Très au-dessus de la plage | Weit über dem Richtbereich | Molto sopra l’intervallo | Muy por encima del rango |
| `session_tile_far_below` | Far below the range | Très en dessous de la plage | Weit unter dem Richtbereich | Molto sotto l’intervallo | Muy por debajo del rango |
| plurals `save_session_count` one / other | Save · %1$d measurement / Save · %1$d measurements | Enregistrer · %1$d mesure / Enregistrer · %1$d mesures | Speichern · %1$d Messung / Speichern · %1$d Messungen | Salva · %1$d misurazione / Salva · %1$d misurazioni | Guardar · %1$d medición / Guardar · %1$d mediciones |

- [ ] **Step 4: Implement the four functions in `SessionTiles.kt`**

- [ ] **Step 5: Run the tests and the localization contract**

Run: `./gradlew :ui:jvmTest --tests '*SessionTilesTest*' --tests '*LocalizationContractTest*'`
Expected: PASS.

- [ ] **Step 6: Commit** — `feat(session): tile states, column rule and measurement count`

---

### Task 2: Shared header pieces

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/ImmersiveComponents.kt` (receives `overlapNext`; adds `WaterHeader`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/HomeHero.kt` (drops its `overlapNext`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/StatusBarOverWater.kt` (adds `StatusBarOverHeader`, `heroUnderStatusBar` moves here)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/TankHome.kt` (uses `StatusBarOverHeader`; `StatusBarOverHome` removed)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/ImmersiveSemanticsTest.kt`, `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/ScenePhysicsTest.kt`, `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/HeroStatusBarTest.kt` (import path only)

**Interfaces:**
- Consumes:
  - `TankScene`, `GlassIconButton`, `HeroText`, `HeroTextBacking` (lot 1);
  - `heroUnderStatusBar(...)` and the body of `StatusBarOverHome` (TankHome.kt, lot 1).
- Produces:
  - `fun Modifier.overlapNext(overlap: Dp): Modifier`, now in `ImmersiveComponents.kt`, same behaviour.
  - `@Composable fun StatusBarOverHeader(listState: LazyListState)`: lot 1's `StatusBarOverHome`, unchanged, renamed and shared. It gives light icons while item 0 is under the status bar, then dark icons and a paper band.
  - `@Composable fun WaterHeader(aquarium: Aquarium, title: String, subtitle: String, closeLabel: String, onClose: () -> Unit, modifier: Modifier = Modifier, below: @Composable () -> Unit = {})`. From top to bottom:
    - `TankScene(aquarium, inhabitants = emptyList(), modifier = Modifier.matchParentSize(), decorPlants = 0, animate = !rememberReducedMotion())`;
    - the `WaterHeaderShade`;
    - the status-bar inset;
    - a row with the title (Fraunces 28 sp, `HeroText`, `heading()`, up to 2 lines) and the subtitle (14 sp, `HeroText`), with a `GlassIconButton(AquagendaIcons.X, closeLabel, onClose)` at the end;
    - `below()`;
    - a bottom spacer of 30 dp + 16 dp, for the sheet's overlap.

    The box is `heightIn(min = 170.dp)`.
  - `internal val WaterHeaderShade: Brush`: a vertical gradient from black .45 to black .25. It is drawn over the whole header, so the text keeps its contrast at any height.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ImmersiveSemanticsTest
@Test fun `water header title is a heading and close is a 48 dp glass button`() // node "New Session" has Heading; node "Cancel" ≥ 48 dp, click invokes onClose
// ScenePhysicsTest
@Test fun `white header text stays readable over the brightest water`() // HeroText over WaterHeaderShade's lightest stop (.25) composited over the scene's top water #3D9184 lit by a ray (white 14 %) ≥ 4.5
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*ImmersiveSemanticsTest*' --tests '*ScenePhysicsTest*'`
Expected: FAIL (unresolved `WaterHeader`, `WaterHeaderShade`).

- [ ] **Step 3: Move `overlapNext` and the status-bar helper, then implement `WaterHeader`**

`TankHome` calls `StatusBarOverHeader(listState)` where it called `StatusBarOverHome`. `HeroStatusBarTest` keeps its cases with the new import.

- [ ] **Step 4: Run the JVM suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit** — `refactor(ui): shared water header, sheet overlap and status-bar helper`

---

### Task 3: The measurement tile and its grid

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/SessionTiles.kt` (composables)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/SessionTilesSemanticsTest.kt`

**Interfaces:**
- Consumes:
  - `sessionTileState`, `SessionTileState.text()`, `sessionColumns` (Task 1);
  - lot 1's `levelContainerColor`, `levelTextColor` and `levelDescription`;
  - `LevelMark(level, color, modifier)` in `ImmersiveComponents.kt`, which becomes `internal` (it is `private` today);
  - the `rangeText` extension, which moves from `RapidSessionUi.kt` to `SessionTiles.kt` as `internal`.
- Produces:
  - `@Composable fun MeasureTile(definition: ParameterDefinition, input: String, profile: AquariumProfile, formatter: LocalizedFormatters, imeAction: ImeAction, onValueChange: (String) -> Unit, onNext: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier)`
    - It is a `BasicTextField` (Decimal keyboard, single line) whose `decorationBox` draws the tile.
    - The tile has 20 dp corners and 12 × 14 dp padding.
    - Its fill and border:
      - fill: `levelContainerColor(level)`, or `surface` when the level gives none;
      - border: 1 dp `outlineVariant`, which becomes 2 dp `primary` while the field is focused, or 2 dp `error` when the input is invalid.
    - Its lines, from top to bottom:
      - a label line: name in 13 sp medium, then « · range » in muted text, with the `LevelMark` before the name when out of range;
      - the value: the inner field at 28 sp semibold, coloured with `levelTextColor` or `error`, the unit at half size after it, and « — » placeholder in `outline` when empty;
      - the state line at 12 sp, wrapping. It is muted for EMPTY, `error` for INVALID, the level text colour when out of range, and `primary` for IN_RANGE.
    - Semantics stay on the field:
      - `contentDescription` is `measurement_for_parameter`;
      - `stateDescription` is unit, state text and `levelDescription`, comma-joined and blank-free;
      - `error(invalid_measurement)` when the input is invalid.
  - `@Composable fun MeasureTileRow(tiles: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier)`: a `Row` with 10 dp spacing, where each tile gets `Modifier.weight(1f)`. A short last row keeps an empty weighted slot, so a lone tile stays half width.

- [ ] **Step 1: Write the failing semantics tests** (locale ENGLISH, `AquaLogTheme`, as in `ImmersiveSemanticsTest`)

```kotlin
@Test fun `a tile is one editable node named after its parameter`()       // node "Measurement for pH" has SetText; tile text "Tap to enter" when empty
@Test fun `above the range is said in words and marked`()                   // input "7,8" (range 6,5–7,5) → stateDescription contains "Above the range"; LEVEL_MARK_TAG present
@Test fun `invalid input reads as an error`()                               // "7,8,1" → SemanticsProperties.Error defined; text "Enter a decimal number…" shown
@Test fun `next moves right then down, last is done`()                      // 3 tiles in rows of 2: focus pH, performImeAction → GH focused; again → KH (row 2) focused; KH has ImeAction.Done
@Test fun `german at 150 percent stacks the tiles`()                        // fontScale 1.5, width 360 dp, de: columns = sessionColumns(...) = 1 → tile 2 top ≥ tile 1 bottom; state text not clipped (no TextLayout overflow)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*SessionTilesSemanticsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement `MeasureTile` and `MeasureTileRow`**

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :ui:jvmTest --tests '*SessionTilesSemanticsTest*'`
Expected: PASS.

- [ ] **Step 5: Commit** — `feat(session): measurement tile with the keyboard chain`

---

### Task 4: The Session screen

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/RapidSessionUi.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (the RapidSession branch gets `modifier`, not `inset`)
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/icon/{FlaskConical,BrushCleaning,SprayCan,Scissors}.kt`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/SessionTilesTest.kt` (button label rule)

**Interfaces:**
- Consumes: Tasks 1–3, plus `PaperSheetTop`, `CarnetSectionTitle` and `HomeViewModel`'s existing methods.
- Produces:
  - `@Composable internal fun saveButtonLabel(editing: Boolean, count: Int): String`: `save_changes` when editing, `save_session` when the count is 0, `save_session_count` otherwise.
  - Test tag `save-session` on the save button.
  - `MaintenanceActionType.icon(): ImageVector`, now non-null:
    - WATER_CHANGE → Droplet, FILTER_MAINTENANCE → Funnel (as today);
    - FERTILIZATION → FlaskConical, VACUUM → BrushCleaning, GLASS_CLEANING → SprayCan, PRUNING → Scissors.

    Lucide paths (ISC), in the same `ImageVector.Builder` pattern as `Funnel.kt`.

**List order in `rapid-session-list`:**
- **Item 0**, `WaterHeader` with `Modifier.overlapNext(30.dp)`:
  - title `rapid_session_title` or `edit_session`, subtitle `aquarium.name`;
  - close label `cancel` → `closeRapidSession`;
  - `below` is the time chip: a glass pill with a − button (`earlier_time`, 48 dp), `formatter.shortDateTime(occurredAt)` and a + button (`later_time`, 48 dp), which call `shiftSessionTimeByMinutes(∓15)`.
- **Item 1**, `PaperSheetTop`. Every later item has `background(colorScheme.background)` and 16 dp side padding.
- **Item 2**, `CarnetSectionTitle(measurements_heading)`.
- **Measurement rows**: `context.activeParameters.chunked(columns)`, each row keyed by its first definition's id. Then `FIRST_PARAMETER_ITEM_INDEX = 3`, and the auto-focus effect keeps working unchanged.
- **Maintenance**: `CarnetSectionTitle(maintenance_heading)`, then the `FlowRow` of chips. A chip's leading icon is `Check` when selected, else `type.icon()`. Chip behaviour (edit, remove action) is unchanged, and the `ActionDetails` editor follows as today.
- **Note rows**: observation, then incident, as 48 dp rows with a 1 dp dashed `outlineVariant` rounded (18 dp) border, a `NotepadText` icon and the existing labels. Their text fields open below as today.
- **Save error text**, as today.

**Screen chrome:**
- The screen draws under the status bar and calls `StatusBarOverHeader(listState)`.
- The save button stays in the `Scaffold` bottom bar, with `navigationBarsPadding().imePadding()`. Its label is `saveButtonLabel(state.editingSessionId != null, validMeasurementCount(state.measurementInputs, context.activeParameters))`, with the progress indicator while saving as today.
- The `Scaffold` uses `contentWindowInsets = WindowInsets(0)`, so item 0 reaches the top edge.
- The old header (`CarnetScreenTitle`, `TextButton` cancel, chevron time controls), the per-row hint texts and `UnderlinedValueField` are removed. `alert_measurement` / `unusual_measurement` stay in the catalogs, unused.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun `save label counts measurements, not when editing`() // runComposeUiTest, ENGLISH: (false,0) "Save Session"; (false,4) "Save · 4 measurements"; (false,1) "Save · 1 measurement"; (true,4) "Save changes"
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :ui:jvmTest --tests '*SessionTilesTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the icons, `saveButtonLabel`, the new list body and the routing change**

- [ ] **Step 4: Run the JVM suite and the Android build**

Run: `./gradlew :ui:jvmTest :androidApp:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Launch the desktop harness**

Run: `./gradlew :ui:run` in the background. Open a Session, check that the window starts with no exception in the log, then stop it.

- [ ] **Step 6: Commit** — `feat(session): immersive Session — water header, tile grid, maintenance chips with icons`

---

### Task 5: Device tests follow the new Session

**Files:**
- Modify: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/{RapidSessionFlowTest,PistesFlowTest,MultiAquariumFlowTest,SessionReliabilityFlowTest,WidgetLaunchFlowTest,TankHomeDueTestsFlowTest,LocalizedAccessibilityFlowTest}.kt` and any other androidTest the compile or run shows clicking the old Session controls
- Modify: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/AccessibilityTestSupport.kt` (only if a shared helper is needed)

**Interfaces:**
- Consumes: test tag `save-session` (Task 4), close by accessible label `Cancel` / `Annuler` through `hasLabel` (lot 1), and the state texts from Task 1.

- [ ] **Step 1: Replace the lookups**

| Old lookup | New lookup |
|---|---|
| Save button by text (« Save Session », « Enregistrer la session », « Save changes ») | `onNodeWithTag("save-session")` |
| `onNodeWithText("Cancel")` on the Session | `onNode(hasLabel("Cancel"))` |
| « Far outside your indicative range » / « Outside your indicative range » | « Far above the range » / « Above the range » |

Keep each test's intent.

- [ ] **Step 2: Run the connected classes, then the whole suite**

Run: `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.RapidSessionFlowTest,com.maximerollin.aqualog.PistesFlowTest,com.maximerollin.aqualog.MultiAquariumFlowTest,com.maximerollin.aqualog.SessionReliabilityFlowTest,com.maximerollin.aqualog.WidgetLaunchFlowTest,com.maximerollin.aqualog.TankHomeDueTestsFlowTest,com.maximerollin.aqualog.LocalizedAccessibilityFlowTest`, then `./gradlew :androidApp:connectedDebugAndroidTest`
Expected: PASS. The two Supabase tests are skipped without their backend, as today.

- [ ] **Step 3: Commit** — `test: reach the new Session by tag and accessible label`

---

### Task 6: Verification and captures

**Files:**
- Create: `docs/design/21-modernisation/lot2-captures/` (PNG)

- [ ] **Step 1: Full suites**

Run: `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Captures on the Pixel 9a emulator** (`adb exec-out screencap -p`, `sips -Z 1200`)

Use the lot 1 seed: Salon, 120 L, Planté / crevette.
- **Before**, from `main` at `2ae6bf3`: the Session in FR light.
- **After**, all in FR unless noted:
  - FR light, empty;
  - FR light with pH 7,8 entered and the keyboard open on KH (active ring, « Au-dessus de la plage »);
  - FR dark;
  - DE at 150 % (one column);
  - editing an existing Session;
  - an invalid value.

Expected:
- light status-bar icons over the header;
- the active tile has the ring and stays above the keyboard;
- no clipped text;
- the button reads « Enregistrer · N mesures ».

- [ ] **Step 3: Accessibility dump** (`adb shell uiautomator dump`)

Expected:
- Every tile is one editable node « Mesure pour X » whose state reads the unit and the state line.
- The close button is « Annuler », and the − and + buttons are « −15 minutes » / « +15 minutes ».
- Every chip keeps its « Retirer » action when selected.

- [ ] **Step 4: Commit the captures** — `docs: lot 2 before/after captures`
