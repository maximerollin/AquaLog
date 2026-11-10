# Modernisation lot 1 : fondations immersives et nouvel Accueil — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay down the immersive grammar of variant C (glass buttons, paper sheet, icon-only floating bar with the « + » Mesurer button, edge-to-edge water under the status bar) and rebuild the Accueil on it: a tall water hero with the tank's state, then a sheet with a horizontally scrolling value row (chevrons on both sides), « À faire » and the existing conditional blocks.

**Architecture:** Pure data for the hero (in-range ratio, every active value) extends `HomeOverview` and `StatusText`, unit-tested on the JVM. The visual building blocks live in a new `ui/theme/ImmersiveComponents.kt`, each with a JVM semantics test (`runComposeUiTest`). `AquariumShell` swaps its `Scaffold` + `NavigationBar` for a `Box` with the floating bar on top. `TankHome` keeps its single `LazyColumn` (`home-list`), whose first item is the hero, laid out 30 dp shorter than it draws so the sheet overlaps the water. History, Trends and More only get the new bar and insets in this lot; their redesign is lots 3 and 4. Of the shared components listed in spec §6, this lot builds only those the Accueil uses. `WaterHeader` (compact), `PeriodSegmented`, `SettingsGroup`, `FilledField` and `RangeSheet` arrive with the lot that first needs them.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (material3 1.12.0-alpha03, `HorizontalPager`, `LazyRow`), compose resources, kotlin.test + `runComposeUiTest` on JVM, AndroidX Compose UI tests on the Pixel 9a emulator.

**Spec:** `docs/superpowers/specs/2026-11-10-modernisation-immersive-design.md` (§3, §4.1, §5, §6, §7 lot 1, §8). Mockup: `docs/design/21-modernisation/prototype-modernisation.html` on branch `prototype/21-modernisation`, variant C, screen « Accueil » (`git show prototype/21-modernisation:docs/design/21-modernisation/prototype-modernisation.html > /tmp/proto.html`, serve the repo root, open `?variant=C`).

## Global Constraints

- Palette, fonts and `TankScene` drawing unchanged (`AquaLogTheme.kt`, `CarnetTypography.kt`, `home/TankScene.kt`). New tokens only for attention/alert containers: light `#FBF0D9` / `#F7E1DB`, dark `#3A3220` / `#3D2420`.
- Status wording is factual: « 6 sur 7 dans vos plages », never « Eau stable » (spec §8.1).
- When the Aquarium has a photo, the photo replaces the scene in the Accueil hero (spec §8.2). The scene stays everywhere else.
- Navigation bar: icons only, each with its `contentDescription` (Bac, Historique, Tendances, Plus, Mesurer), `Role.Tab` + `selected` on the tabs; targets ≥ 48 dp.
- White text on water ≥ 4.5:1: a dark gradient sits under every title and status line drawn on the hero.
- Touch targets ≥ 48 dp; layouts hold at font scale 1.5 in German; scene still when the system reduces motion.
- Every new visible string in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`), terms from `docs/localization-glossary.md`; no literals (`LocalizationContractTest`). No emoji.
- No change to Room schema, sync payloads, export format or existing string values.
- `home-list` stays the home's only vertical scrollable; the value row is a second, horizontal one.
- Desktop harness: verified by launching it (`./gradlew :ui:run`), it has no tests.

## Review Focus

1. **Never-measured Aquarium** — the hero shows no ring and « Pas encore de mesure », the value row shows « — » tiles, no chevron fires on empty space. Pinned in Task 1 (`statusRatio` is null) and Task 4 (hero test).
2. **Swipe between Aquariums mid-gesture** — « + » must not record into the wrong Aquarium: it is disabled exactly when `measureTargetsShownAquarium` is false. Pinned in Task 3 (shell wiring) and Task 2 (disabled semantics).
3. **Few parameters (≤ 3 active)** — the row does not overflow, so neither chevron is shown. Pinned in Task 2.
4. **Leaving the Accueil in light theme** — status-bar icons must go back to dark on History/Trends/More and every non-shell screen. Pinned in Task 3 (restore on dispose) and checked in Task 6 captures.
5. **German at 150 %** — the Aquarium name wraps on two lines without pushing the status card under the sheet; the bar keeps 48 dp targets. Pinned by the updated `LocalizedAccessibilityFlowTest` (Task 5) and Task 6 captures.

---

### Task 1: Hero data — every active value and the in-range ratio

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeOverview.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/StatusText.kt`
- Modify: `ui/src/commonMain/composeResources/values{,-en,-fr,-de,-it,-es}/strings.xml`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/HomeOverviewTest.kt`, `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/StatusTextTest.kt`

**Interfaces:**
- Produces: `HomeOverview.all: List<HomeValue>` (every active parameter, position order, untrimmed; `values` keeps its 6-value trim for the widget), `HomeOverview.measuredCount: Int`, `HomeOverview.inRangeCount: Int` (= `measuredCount - outsideRangeCount`); `fun statusRatio(overview: HomeOverview): TextRef?` (null when not measured).
- Produces strings: `home_status_in_range_ratio` (`%1$d` in range, `%2$d` measured), `home_to_do`, `home_values_previous`, `home_values_next`, plurals `home_inhabitants_count`.

- [ ] **Step 1: Write the failing tests**

In `HomeOverviewTest`, with the file's existing `definition`/`setup`/`reading` helpers:
```kotlin
@Test
fun `all keeps every active parameter in position order beyond six`() {
    // 8 active definitions, positions 0..7, none measured
    assertEquals(8, overview.all.size)
    assertEquals((0..7).toList(), overview.all.map { it.definition.position })
    assertEquals(6, overview.values.size)
}

@Test
fun `in range count is measured minus out of range`() {
    // ph 7.8 out of 6.5–7.5, gh 8 in 4–12, kh never measured
    assertEquals(2, overview.measuredCount)
    assertEquals(1, overview.inRangeCount)
}
```
In `StatusTextTest`:
```kotlin
@Test
fun `ratio is null before the first measurement`() = assertNull(statusRatio(unmeasuredOverview))

@Test
fun `ratio reads in range over measured`() =
    assertEquals(TextRef.Str(Res.string.home_status_in_range_ratio, listOf(6, 7)), statusRatio(sixOfSeven))
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*HomeOverviewTest*' --tests '*StatusTextTest*'`
Expected: FAIL (unresolved `all`, `measuredCount`, `statusRatio`, `home_status_in_range_ratio`).

- [ ] **Step 3: Add the strings**

| key | values / -en | -fr | -de | -it | -es |
|---|---|---|---|---|---|
| `home_status_in_range_ratio` | %1$d of %2$d in range | %1$d sur %2$d dans vos plages | %1$d von %2$d im Richtbereich | %1$d su %2$d nell’intervallo indicativo | %1$d de %2$d en el rango indicativo |
| `home_to_do` | To do | À faire | Zu erledigen | Da fare | Por hacer |
| `home_values_previous` | Previous values | Valeurs précédentes | Vorherige Werte | Valori precedenti | Valores anteriores |
| `home_values_next` | Next values | Valeurs suivantes | Nächste Werte | Valori successivi | Valores siguientes |
| plurals `home_inhabitants_count` one / other | %1$d inhabitant / %1$d inhabitants | %1$d habitant / %1$d habitants | %1$d Bewohner / %1$d Bewohner | %1$d abitante / %1$d abitanti | %1$d habitante / %1$d habitantes |

- [ ] **Step 4: Implement `all`, `measuredCount`, `inRangeCount` in `HomeOverview` and `statusRatio` in `StatusText.kt`**

`all` is the list `homeOverview` already builds before trimming. The counts are derived properties. `statusRatio` returns `TextRef.Str(Res.string.home_status_in_range_ratio, listOf(inRangeCount, measuredCount))` when `isMeasured`.

- [ ] **Step 5: Run the tests and the localization contract**

Run: `./gradlew :ui:jvmTest --tests '*HomeOverviewTest*' --tests '*StatusTextTest*' --tests '*LocalizationContractTest*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeOverview.kt ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/StatusText.kt ui/src/commonMain/composeResources ui/src/jvmTest
git commit -m "feat(home): every active value and the in-range ratio for the immersive hero"
```

---

### Task 2: Immersive building blocks

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/ImmersiveComponents.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/AquaLogTheme.kt` (container colours)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/ImmersiveSemanticsTest.kt`

**Interfaces:**
- Consumes: `HomeValue`, `ReadingLevel`, `LocalizedFormatters`, `MainDestination`, `AquagendaIcons`.
- Produces:
  - `@Composable fun levelContainerColor(level: ReadingLevel): Color?` in `AquaLogTheme.kt` (null for OK; the four tokens of Global Constraints).
  - `@Composable fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier)`: 48 dp target, 40 dp circle, white 20 % fill, 1 dp white 30 % border, white icon.
  - `@Composable fun PaperSheetTop(modifier: Modifier = Modifier)`: the 30 dp rounded top edge in `colorScheme.background`, with a 5 dp × 40 dp handle and a soft upward shadow. It is a LazyColumn item, not a container.
  - `@Composable fun FloatingNavBar(selected: MainDestination, onSelect: (MainDestination) -> Unit, onMeasure: () -> Unit, measureEnabled: Boolean, modifier: Modifier = Modifier)`: 64 dp pill on `surface`, `outlineVariant` border, 8 dp shadow. Four icon tabs (selected one in a `secondaryContainer` pill), then a 52 dp `primary` circle « + ». The tabs use the labels `navigation_tank/history/trends/more` as `contentDescription` with `Role.Tab` + `selected`. « + » uses `measure_action` and, when disabled, alpha .38 + `disabled()`, exactly like today's FAB.
  - `const val FloatingNavBarClearance = 96` (dp of bottom padding a list needs to scroll clear of the bar).
  - `@Composable fun ValueTile(value: HomeValue, formatter: LocalizedFormatters, modifier: Modifier = Modifier)`:
    - 96 dp wide, 20 dp corners.
    - Label 12 sp, value 24 sp semibold tabular, unit at half size.
    - Container from `levelContainerColor`, else `surface` + `outlineVariant` border. Text colour from `levelTextColor`.
    - « — » when unmeasured.
    - One merged semantics node reading label, value, unit and `levelDescription(level)`.
  - `@Composable fun ValueRow(values: List<HomeValue>, formatter: LocalizedFormatters, modifier: Modifier = Modifier)`:
    - A `LazyRow` with 10 dp spacing and 20 dp start/end padding.
    - Chevrons: 48 dp target, 30 dp `surface` circle with shadow, over a 56 dp fade from `background`.
    - The left chevron shows only when `canScrollBackward`, the right only when `canScrollForward`. Their `contentDescription` is `home_values_previous` / `home_values_next`.
    - On click, `animateScrollBy(±viewport width − 40 dp)`.

- [ ] **Step 1: Write the failing semantics tests**

```kotlin
@Test fun `tabs are icon-only, named and selectable`()       // 4 nodes with Role.Tab; contentDescriptions = the 4 labels; only TANK selected; each ≥ 48 dp
@Test fun `measure is disabled while the shown aquarium is not the current one`() // measureEnabled=false → node "Measure" has SemanticsProperties.Disabled
@Test fun `no chevron when three values fit`()               // ValueRow(3 values) at width 360 dp → no node "Previous values" / "Next values"
@Test fun `only the next chevron at the start of a long row`() // 7 values → "Next values" exists, "Previous values" does not
@Test fun `next chevron scrolls and reveals the previous one`() // performClick on "Next values", waitForIdle → "Previous values" exists
@Test fun `a value tile reads as one node`()                  // ph 7.8 attention → one node whose text contains "pH", "7.8" and the attention description
@Test fun `glass button keeps a 48 dp target`()
```
Locale ENGLISH as in `FieldAndPagerSemanticsTest`; wrap content in `AquaLogTheme`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*ImmersiveSemanticsTest*'`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement the components above in `ImmersiveComponents.kt` and `levelContainerColor` in `AquaLogTheme.kt`**

`ValueRow` derives chevron visibility from `LazyListState.canScrollBackward/Forward`. The fade is a horizontal `Brush.horizontalGradient` from `colorScheme.background` (35 %) to transparent.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :ui:jvmTest --tests '*ImmersiveSemanticsTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/ImmersiveSemanticsTest.kt
git commit -m "feat(ui): immersive building blocks — glass button, sheet edge, floating bar, value row"
```

---

### Task 3: Shell — floating bar, edge to edge, status bar over water

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/AquaLogApp.kt` (Scaffold insets)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (`AquaLogContent`, `AquariumShell`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/TankHome.kt` (signature, FAB removed)
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/StatusBarOverWater.kt` (`expect`), `ui/src/androidMain/kotlin/com/maximerollin/aqualog/StatusBarOverWater.android.kt`, `ui/src/jvmMain/kotlin/com/maximerollin/aqualog/StatusBarOverWater.jvm.kt`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/MeasureGuardTest.kt`

**Interfaces:**
- Consumes: `FloatingNavBar`, `FloatingNavBarClearance` (Task 2); `measureTargetsShownAquarium` (existing).
- Produces:
  - `TankHome(state, viewModel, modifier, rateApp, onMeasureAvailabilityChange: (Boolean) -> Unit = {})`. `TankHome` calls it from a `LaunchedEffect(canMeasure)`.
  - `@Composable expect fun StatusBarOverWater()`: while in composition, the status-bar icons are light. On dispose they return to `!isSystemInDarkTheme()`. Same pattern as `ReducedMotion` expect/actual; no-op on JVM.

- [ ] **Step 1: Write the failing test**

In `MeasureGuardTest`, pin the shell's rule for « + »:
```kotlin
@Test fun `plus is live on other tabs and follows the home guard on the Bac tab`() {
    assertTrue(plusEnabled(MainDestination.HISTORY, tankCanMeasure = false))
    assertFalse(plusEnabled(MainDestination.TANK, tankCanMeasure = false))
    assertTrue(plusEnabled(MainDestination.TANK, tankCanMeasure = true))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :ui:jvmTest --tests '*MeasureGuardTest*'`
Expected: FAIL (unresolved `plusEnabled`).

- [ ] **Step 3: Implement**

- `internal fun plusEnabled(destination: MainDestination, tankCanMeasure: Boolean): Boolean` goes next to `measureTargetsShownAquarium` in `TankHome.kt`.
- `AquaLogApp`: `Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))`.
- `AquaLogContent`: every branch except `AquariumShell` gets `modifier.windowInsetsPadding(WindowInsets.statusBars)`.
- `AquariumShell`:
  - `Box(modifier.fillMaxSize())` holds the destination, then `FloatingNavBar` aligned bottom-center with 16 dp side and bottom margins.
  - TANK draws from the top edge and calls `StatusBarOverWater()`.
  - HISTORY / TRENDS / MORE get `windowInsetsPadding(WindowInsets.statusBars)` and `padding(bottom = FloatingNavBarClearance.dp)`.
  - « + » calls `viewModel.openRapidSession()` when `plusEnabled(state.destination, tankCanMeasure)`.
- `TankHome`: the `ExtendedFloatingActionButton` is removed. `contentPadding` bottom becomes `FloatingNavBarClearance.dp`, and the `SnackbarHost` sits above the bar (`padding(bottom = FloatingNavBarClearance.dp)`).

- [ ] **Step 4: Run the JVM suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS.

- [ ] **Step 5: Launch the desktop harness and check by eye**

Run: `./gradlew :ui:run`
Expected: the floating bar is on every tab, « + » opens the Session, and History, Trends and More are not hidden under the bar.

- [ ] **Step 6: Commit**

```bash
git add ui/src
git commit -m "feat(ui): floating icon bar with Measure, edge-to-edge home, light status bar over water"
```

---

### Task 4: Accueil — hero and sheet

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/HomeHero.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/TankHome.kt` (list body; `SceneHeader`, `StatusLine` and the values grid removed)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/HomeHeroSemanticsTest.kt`

**Interfaces:**
- Consumes: `statusRatio`, `statusText`, `HomeOverview.all`/`inRangeCount`/`measuredCount`, plurals `home_inhabitants_count` (Task 1); `GlassIconButton`, `PaperSheetTop`, `ValueRow` (Task 2); `TankScene`, `AquariumThumbnail`'s `loadPhoto`/`cachedPhoto` lambdas, `PagerDots`, `PistesSheet`, `HabitantsSheet` (existing).
- Produces:
  - `@Composable fun HomeHero(aquarium: Aquarium, overview: HomeOverview?, today: LocalDate, inhabitantCount: Int, scene: MiniScene, still: Boolean, photo: ImageBitmap?, onChangeAquarium: () -> Unit, onOpenPhoto: (() -> Unit)?, onOpenHabitants: (() -> Unit)?, onAddHabitants: (() -> Unit)?, onOpenStatus: (() -> Unit)?, modifier: Modifier = Modifier)`.
  - `fun Modifier.overlapNext(overlap: Dp): Modifier`: measures the content at full height, then reports `height − overlap` to its parent. The next LazyColumn item, drawn later, covers the bottom of the hero.

**Hero layout (430 dp, top to bottom):**
- Background: `photo` with `ContentScale.Crop` when non-null, else `TankScene(… Modifier.fillMaxSize(), animate = !still && !rememberReducedMotion())`. Gradient: black 40 % → transparent over the top 35 %, and transparent → black 30 % over the bottom 30 %.
- Status-bar inset, then the name block. The name is Fraunces 34 sp white, wrapping to 2 lines max. The context line is `volume · profile · inhabitants` (inhabitants omitted at 0). The whole block is clickable → `onChangeAquarium` with `home_change_aquarium` as click label. A `GlassIconButton(Camera)` at top end → `onOpenPhoto` with the existing `photo_open`/`photo_add` label.
- Status card: 16 dp under the name block, white 16 % fill, 1 dp white 25 % border, 22 dp corners.
  - A 46 dp ring: in-range fraction stroke 5 dp, `n/total` in the centre.
  - Title: `statusRatio`.
  - Second line: `statusText` summary + « · » + since.
  - When `statusRatio` is null: no ring, title = `home_no_measurement_yet`.
  - When `overview` is null (a pager page that is not the current Aquarium, seen only mid-swipe), there is no status card at all.
  - Click → `onOpenStatus` (null = not clickable).
- When there are no inhabitants and `onAddHabitants != null`: a glass chip `habitants_add_yours` at bottom end. A tap on the water elsewhere → `onOpenHabitants` (existing click label `habitants_open`).
- With 2+ Aquariums, `PagerDots` in white sit 40 dp above the hero's bottom. `PagerDots` gains `onWater: Boolean = false`.

**Sheet order in `home-list`:**
1. The hero (inside the existing `HorizontalPager` when 2+ Aquariums), with `overlapNext(30.dp)`.
2. `PaperSheetTop`.
3. `ValueRow(overview.all)`.
4. The maintenance series line (existing text).
5. `CarnetSectionTitle(home_to_do, action home_see_all → openTasks)`, then due tests, then today's tasks or the « next » line (existing items).
6. Continue after session, cycled proposal, `AlertRow`s, `WeeklySummaryCard`, `SyncFailure`, account reminder (existing items, this order).

Every sheet item has `Modifier.background(colorScheme.background)`, so the water never shows between items.

**Status card action:** when pistes exist, show `PistesSheet(pistes, today, onDismiss)` through a local `showPistes` state. Otherwise, when a Session exists, `viewModel.openSessionDetails()`. Otherwise not clickable. The inline `PistesCard` item is removed.

- [ ] **Step 1: Write the failing semantics tests**

```kotlin
@Test fun `unmeasured aquarium shows no ring and the no-measurement title`() // overview with no readings → no node with text matching "\\d+/\\d+"; node "No measurement yet" exists
@Test fun `ratio card reads six of seven and is a button when pistes exist`() // "6 of 7 in range" exists, has click action
@Test fun `name block changes aquarium and camera opens the photo`()        // click label "Change Aquarium" on the name node; glass node "Add a photo of Salon" invokes onOpenPhoto
@Test fun `inhabitants context is omitted at zero`()                       // inhabitantCount = 0 → context text has no "inhabitant"
@Test fun `a page that is not the current aquarium has no status card`()    // overview = null → no "in range" / "No measurement yet" node
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*HomeHeroSemanticsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement `HomeHero`, `overlapNext` and the new `TankHome` list body as specified above**

The photo bitmap: `remember(aquarium.photoId) { cachedPhoto(id) }`, then a `LaunchedEffect` with `loadPhoto(id)` if it is null (same lambdas `AquariumScene` already passes).

- [ ] **Step 4: Run the JVM suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS.

- [ ] **Step 5: Desktop harness by eye, light and dark**

Run: `./gradlew :ui:run`
Expected: it matches the mockup's Accueil. Check that the swipe between two Aquariums still works on the hero, that the photo replaces the scene once one is set, and that the chevrons appear and disappear at the row's ends.

- [ ] **Step 6: Commit**

```bash
git add ui/src
git commit -m "feat(home): immersive Accueil — water hero with in-range ratio, sheet with scrolling values"
```

---

### Task 5: Device tests follow the icon-only bar

**Files:**
- Modify: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/AccessibilityTestSupport.kt`
- Modify: every androidTest that clicks the bar or Measure by text. These are `SyncStatusFlowTest`, `TrendsFlowTest`, `AccountControlSettingsTest`, `PistesFlowTest`, `MultiAquariumFlowTest`, `TimelineFlowTest`, `RapidSessionFlowTest`, `PortableDataFlowTest`, `TaskFlowTest`, `SessionReliabilityFlowTest`, `PrivacyFlowTest`, `WidgetLaunchFlowTest`, `TankHomeDueTestsFlowTest`, `AquariumPersistenceTest`, `SupabaseTwoDeviceSyncTest` and `LocalizedAccessibilityFlowTest`.

**Interfaces:**
- Produces: `internal fun hasLabel(label: String): SemanticsMatcher = hasText(label) or hasContentDescription(label)`.

- [ ] **Step 1: Replace the lookups**

Every `onNodeWithText(X)` / `hasText(X)` where X is « Tank », « History », « Trends », « More », « Measure », or their `Res.string.navigation_*` / `measure_action` resolution, becomes `onNode(hasLabel(X))` / `hasLabel(X)`. In `LocalizedAccessibilityFlowTest`, `awaitText`, `assertAction` and `assertEssentialActions` match with `hasLabel`. The home Trends link is gone, so `navigation_trends` now matches the tab only. Home scrolls through `onNodeWithTag("home-list")`, never `onNode(hasScrollAction())`.

- [ ] **Step 2: Run the connected suite on the Pixel 9a emulator**

Run: `./gradlew :androidApp:connectedDebugAndroidTest`
Expected: PASS. The two Supabase tests are skipped without their local backend, as today.

- [ ] **Step 3: Commit**

```bash
git add androidApp/src/androidTest
git commit -m "test: reach the icon-only bar and Measure by accessible label"
```

---

### Task 6: Verification and captures

**Files:**
- Create: `docs/design/21-modernisation/lot1-captures/` (PNG, before/after)

- [ ] **Step 1: Full suites**

Run: `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:assembleDebug :androidApp:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Captures on the emulator**

Take the Accueil in five setups: FR light, FR dark, DE at font scale 1.5, an Aquarium with a photo, and a never-measured Aquarium. Also take History in FR light. Use `adb exec-out screencap -p` and the same seed as the 10 Nov captures (Salon, 120 L, Planté / crevette, one Session: 25,5 / 7,8 / 8 / 5 / 220 / 35 / 0,5).
Expected:
- The status-bar icons are light on the Accueil and dark on History in light theme.
- The text on the water is readable over the photo.
- In German, the name wraps without overlap.
- The value row shows its chevrons.

- [ ] **Step 3: TalkBack pass**

Swipe through the bar: each tab is announced with its name and « selected » state, and « + » as Mesurer. Swipe through the value row: every tile is reached, including off-screen ones, and each chevron is announced.

- [ ] **Step 4: Commit the captures**

```bash
git add docs/design/21-modernisation/lot1-captures
git commit -m "docs: lot 1 before/after captures"
```
