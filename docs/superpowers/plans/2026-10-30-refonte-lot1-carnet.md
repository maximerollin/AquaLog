# Refonte lot 1 : style Carnet, densité et nouvel accueil — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every Aquagenda screen the approved « Carnet » style (Fraunces + DM Sans, warm paper, numbers on the page, no boxes in boxes), make Session entry, Chronologie, Tendances, Tâches and Paramètres dense, and replace the home with « mon bac, son état, ce qu'il y a à faire » (animated decor scene, latest value per parameter, today's Tâches, Mesurer button, multi-aquarium switching). No inhabitants, assistance rules, widget or photos: those are lots 2–5.

**Architecture:** The visual system lives in `ui/.../ui/theme` (colors, typography, shared Carnet primitives). Home data that is pure logic (which values to show, their age, the status line, today's Tâches, alerts for other Aquariums) is computed by pure functions in a new `HomeOverview.kt`, unit-tested on the JVM; `HomeViewModel` only collects one new repository Flow and calls them. Screens keep their existing ViewModel functions; this lot is mostly composable rewrites plus one navigation change (4 destinations).

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (material3 1.12.0-alpha03: `HorizontalPager`, `ModalBottomSheet`), compose resources (`Res.font`, `Res.string`), Room, kotlin.test on JVM, AndroidX Compose UI tests on the emulator.

**Spec:** `docs/superpowers/specs/2026-10-30-refonte-aquagenda-design.md` (§3, §4.1, §4.2, §5 lot 1, §7). Mockups: `docs/design/20-refonte-carnet/01-style-carnet.html` (target look), `03-accueil-scene.html` (scene motion), `06-densite-avant-apres.html` (what to remove).

## Global Constraints

- Palette unchanged except the light background: paper `#F3EFE3` (was `#F6F4EC`). Primary `#14675E`, surface `#FFFEF9`, onSurface `#1E3733`, outline `#7C9189`, outlineVariant `#CCD7CF`. Dark scheme unchanged unless a contrast test forces it.
- Attention (value outside its Plage indicative): dot/line `#C98A1B`, text `#8A5A00` (≥ 4.5:1 on paper). Lot 1 has only this one level; red comes with lot 2.
- Fonts: **Fraunces** (weights 500, 600) for screen titles, section titles and Chronologie dates; **DM Sans** (400, 500, 600, 700) for everything else. Static TTFs, OFL licence file committed next to them.
- Touch targets ≥ 48 dp; WCAG 4.5:1 normal text, 3:1 large/non-text; layouts hold at font scale 1.5 in German. Density comes from removing text and margins, never from smaller targets.
- No emoji anywhere in the UI; icons are 24 dp stroke vectors in `composeResources/drawable`, palette-coloured.
- Every visible string is a resource in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`); no `Text("…")` literals (`LocalizationContractTest`). Use the glossary terms of `docs/localization-glossary.md`.
- The animated scene stops when the system asks for reduced motion.
- Nothing in this lot changes the database schema, sync payloads or the export format.
- The top « Aquagenda » bar disappears on every screen; each screen shows its own Fraunces title and a context line.

## Review Focus

1. **Infinite animation vs tests and battery** — with system animations off (CI emulator, `animator_duration_scale 0`) the scene must be static, otherwise Compose tests never go idle. Pinned by Task 5 (`reducedMotion` path) and by running the connected suite in Task 13.
2. **Aquarium or parameter never measured, parameter without range** — home shows « — » and no attention colour, status line says « Pas encore de mesure ». Pinned in Task 3 tests.
3. **Values from different dates** — a value older than the latest Session shows its age (« il y a 12 j »), the others don't. Pinned in Task 3 tests.
4. **German at 150 % font** — value grid drops from 3 to 2 columns at font scale ≥ 1.3, the 4 navigation labels and the Mesurer button stay ≥ 48 dp and non-overlapping. Pinned by the updated `LocalizedAccessibilityFlowTest` in Task 4.
5. **Multi-aquarium switching from the home** — swiping the scene selects that Aquarium, so « Mesurer » records into the Aquarium shown; with one Aquarium there is no pager (tests assume a single scrollable node). Pinned in Task 6 (`MultiAquariumFlowTest` case).

---

### Task 1: Carnet theme — fonts, paper, typography, primitives

**Files:**
- Create: `ui/src/commonMain/composeResources/font/fraunces_medium.ttf`, `fraunces_semibold.ttf`, `dm_sans_regular.ttf`, `dm_sans_medium.ttf`, `dm_sans_semibold.ttf`, `dm_sans_bold.ttf`, `ui/src/commonMain/composeResources/font/OFL.txt`
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/CarnetTypography.kt`
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/CarnetComponents.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/AquaLogTheme.kt:14-56`
- Modify: `androidApp/src/main/res/values/themes.xml` (add `android:windowBackground` = paper colour, avoids a white flash)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/ui/theme/CarnetColorTest.kt`

**Interfaces:**
- Produces:
  - `val AttentionText: Color` (`0xFF8A5A00`), `val AttentionMark: Color` (`0xFFC98A1B`) in `AquaLogTheme.kt` (dark variants: implementer picks values that pass the test below on the dark background).
  - `@Composable fun attentionTextColor(): Color` / `attentionMarkColor(): Color` (theme-aware).
  - `@Composable fun carnetTypography(): Typography` — `displaySmall`/`headlineSmall`/`titleLarge`/`titleMedium` use Fraunces; body/label styles use DM Sans; `AquaLogTheme` passes it.
  - `CarnetComponents.kt`:
    - `@Composable fun CarnetScreenTitle(title: String, context: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null)` — Fraunces 21–24 sp title, muted context line.
    - `@Composable fun CarnetSectionTitle(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null)` — Fraunces section title in primary, optional trailing text action (≥ 48 dp target).
    - `@Composable fun ReadingValue(value: String, label: String, modifier: Modifier = Modifier, unit: String? = null, attention: Boolean = false, age: String? = null)` — large number (≈ 18 sp semibold), small unit, small label below, optional age line; attention colours the number.
    - `@Composable fun CarnetDivider(modifier: Modifier = Modifier)` — 1 dp `#E2DDCD`-like line from `outlineVariant`.

- [ ] **Step 1: Write the failing test** `CarnetColorTest`:
  - `light background is the warm paper`: `assertEquals(Color(0xFFF3EFE3), aquaLogColorScheme(false).background)`.
  - `text pairs reach 4_5 to 1 on paper in both schemes`: for `darkTheme in listOf(false, true)`, contrast(onBackground, background) ≥ 4.5, contrast(primary, background) ≥ 4.5, contrast(attention text for that scheme, background) ≥ 4.5. Contrast = WCAG ratio from `Color.luminance()`.
- [ ] **Step 2:** Run `./gradlew :ui:jvmTest --tests '*CarnetColorTest*'` — Expected: FAIL (background is `F6F4EC`, attention colours undefined).
- [ ] **Step 3:** Add the fonts (static TTF instances from the official Fraunces and DM Sans releases, OFL), set paper background, attention colours, `carnetTypography()`, the four primitives, and the Android `windowBackground`.
- [ ] **Step 4:** Run `./gradlew :ui:jvmTest` — Expected: PASS (all ui JVM tests, including `LocalizationContractTest`).
- [ ] **Step 5:** Run `./gradlew :ui:run`, check titles render in Fraunces and body in DM Sans on the existing screens; close the window.
- [ ] **Step 6: Commit** `feat(ui): add the Carnet theme (Fraunces, DM Sans, warm paper)`.

### Task 2: Latest reading per parameter (shared)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/RapidSession.kt:102-110`
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/AquariumRepository.kt` (interface + `RoomAquariumRepository`)
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/AquaLogDatabase.kt` (session DAO, next to `measurementsForAquarium` L384)
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/RapidSessionRepositoryContractTest.kt`

**Interfaces:**
- Produces:
  - `data class LatestReading(val parameterDefinitionId: String, val value: Double, val occurredAtEpochMillis: Long)` in `RapidSession.kt`.
  - `fun ParameterDefinition.isOutsideIndicativeRange(value: Double): Boolean` in `RapidSession.kt`; `evaluateMeasurement` uses it (same behaviour).
  - `AquariumRepository.observeLatestReadings(aquariumId: String): Flow<List<LatestReading>>` — one entry per parameter that has ever been measured in that Aquarium, the most recent by `occurredAtEpochMillis` then `createdAtEpochMillis`; re-emits when a Session is saved, edited or deleted.
  - DAO: a `Flow` query joining `measurements` and `sessions` returning `parameterDefinitionId`, `value`, `occurredAtEpochMillis` ordered like `measurementsForAquarium`; the repository keeps the first row per parameter.

- [ ] **Step 1: Write the failing tests** in `RapidSessionRepositoryContractTest`:
  - `latest readings keep the newest value of each parameter with its own date`: save Session A at `1_700_000_000_000L` with pH `7.0` and nitrate `10`, then Session B at `1_700_000_500_000L` with pH `7.4` only → readings contain pH `7.4` @ B's time and nitrate `10.0` @ A's time, and nothing for never-measured parameters.
  - `latest readings follow an edited Session date`: move Session B before A with the existing edit path → pH reading becomes `7.0`.
  - `outside indicative range uses the definition bounds`: definition with min `6.5`, max `7.5` → `6.4` true, `7.5` false, `7.6` true; definition with both bounds `null` → always false.
- [ ] **Step 2:** Run `./gradlew :shared:jvmTest --tests '*RapidSessionRepositoryContractTest*'` — Expected: FAIL (unresolved `observeLatestReadings`).
- [ ] **Step 3:** Implement the DAO query, `observeLatestReadings`, `LatestReading`, `isOutsideIndicativeRange`, and route `evaluateMeasurement` through it.
- [ ] **Step 4:** Run `./gradlew :shared:jvmTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(shared): observe the latest reading of each parameter`.

### Task 3: Home overview (pure logic)

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeOverview.kt`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/HomeOverviewTest.kt`

**Interfaces:**
- Consumes: `LatestReading`, `ParameterDefinition.isOutsideIndicativeRange` (Task 2); `AquariumSetup`, `RecordedSession`, `TaskOccurrence`, `TaskDate`, `Aquarium`.
- Produces:
  - `data class HomeValue(val definition: ParameterDefinition, val value: Double?, val occurredAtEpochMillis: Long?, val isOutsideRange: Boolean, val isOlderThanLatestSession: Boolean)`
  - `data class HomeOverview(val values: List<HomeValue>, val outsideRangeCount: Int, val latestSessionAtEpochMillis: Long?)`
  - `fun homeOverview(setup: AquariumSetup, readings: List<LatestReading>, latestSession: RecordedSession?): HomeOverview`
  - `data class TodayTasks(val due: List<TaskOccurrence>, val next: TaskOccurrence?)`
  - `fun todayTasks(pending: List<TaskOccurrence>, aquariumId: String, today: TaskDate): TodayTasks`
  - `data class AquariumAlert(val aquarium: Aquarium, val occurrence: TaskOccurrence, val isOverdue: Boolean)`
  - `fun otherAquariumAlerts(aquariums: List<Aquarium>, currentAquariumId: String, pending: List<TaskOccurrence>, today: TaskDate): List<AquariumAlert>`
  - `TaskDate` is not `Comparable`: compare with `compareValuesBy(a, b, TaskDate::year, TaskDate::month, TaskDate::day)` inside this file.

Rules the tests pin:
- `values`: active parameters ordered by `position`, at most **6**. Out-of-range values are always kept: if more than 6 active parameters, take the out-of-range ones first, fill with the others by position, then sort the kept 6 by position. Never-measured parameters appear with `value = null` (they sort last when trimming).
- `isOlderThanLatestSession` = reading time < latest Session `occurredAtEpochMillis`.
- `todayTasks.due` = this Aquarium's occurrences with `dueDate ≤ today`, in input order (already sorted by the repository), max 3; `next` = first occurrence of this Aquarium after today when `due` is empty, else null.
- `otherAquariumAlerts`: one entry per non-current, non-archived Aquarium that has an occurrence with `dueDate ≤ today` (its first one), `isOverdue = dueDate < today`, in `aquariums` order.

- [ ] **Step 1: Write the failing tests** (`HomeOverviewTest`), each with its exact data:
  - `values keep definition order and mark out of range`: pH (6.5–7.5) 7.2, nitrite (0–0) 0.25, nitrate (0–40) 20 → nitrite `isOutsideRange`, `outsideRangeCount == 1`.
  - `never measured parameter shows no value and no attention`: GH active without reading → `value == null`, `isOutsideRange == false`.
  - `parameter without range is never out of range`: TDS bounds null, value 900 → false.
  - `more than six active parameters keep the out of range ones`: 8 active, only the 8th out of range → it is in the 6, list sorted by position.
  - `older reading is flagged against the latest session`: KH read 12 days before the latest Session → `isOlderThanLatestSession`; pH read in that Session → not flagged.
  - `no session yet`: `latestSession == null` → `latestSessionAtEpochMillis == null`, no value flagged older.
  - `today tasks keep due and overdue of this aquarium only`, `today tasks offer the next one when nothing is due`.
  - `alerts list other aquariums with something due today or overdue`: current Aquarium excluded, archived excluded, `isOverdue` true for yesterday, false for today.
- [ ] **Step 2:** Run `./gradlew :ui:jvmTest --tests '*HomeOverviewTest*'` — Expected: FAIL (unresolved references).
- [ ] **Step 3:** Implement the three functions and data classes.
- [ ] **Step 4:** Run `./gradlew :ui:jvmTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): compute the home overview`.

### Task 4: Four destinations, no top bar

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeViewModel.kt` (`MainDestination` L135, `selectDestination` L1372, `selectHistorySection` L1396)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/AquaLogApp.kt:41-53` (remove `TopAppBar`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (`AquariumShell` L249-304; `ScreenHeading` L1462 → `CarnetScreenTitle`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TimelineUi.kt:72-93` (remove the Chronologie/Tendances chips)
- Create: `ui/src/commonMain/composeResources/drawable/ic_tank.xml`, `ic_trends.xml`, `ic_more.xml` (24 dp stroke, same style as `ic_history.xml`); delete `ic_home.xml`, `ic_settings.xml`, `ic_water.xml` if unused
- Modify: the 6 `strings.xml` catalogs
- Modify (tests): `androidApp/src/androidTest/.../*FlowTest.kt` and `LocalizedAccessibilityFlowTest.kt` navigation steps

**Interfaces:**
- Produces: `enum class MainDestination { TANK, HISTORY, TRENDS, MORE }`. `selectDestination(TRENDS)` starts `observeTrends()` and records `ProductEvent.GraphViewed` (moved from `selectHistorySection`); `HistorySection` and `selectHistorySection` are removed. `MORE` renders the existing `PortableDataSettings` (restyled in Task 12).
- New strings (en / fr; de, it, es written by the implementer with glossary terms):
  - `navigation_tank`: Tank / Bac (de Becken, it Vasca, es Acuario)
  - `navigation_trends`: Trends / Tendances (same values as `trends_title`)
  - `navigation_more`: More / Plus (de Mehr, it Altro, es Más)
  - remove `navigation_home`, `navigation_settings` once unused.

- [ ] **Step 1: Update the failing UI tests first**: replace navigation clicks `"Home"` → `"Tank"`, `"Settings"` → `"More"`, the Trends chip → bottom item `"Trends"`; `LocalizedAccessibilityFlowTest` essential actions become `new_session` (kept until Task 6), `open_tasks`, `navigation_history`, `navigation_trends`, `navigation_more`.
- [ ] **Step 2:** Run `./gradlew :ui:jvmTest :androidApp:assembleDebugAndroidTest` — Expected: compile OK, `LocalizationContractTest` FAIL (new keys missing).
- [ ] **Step 3:** Implement the enum, routing, strings, icons, top bar removal (onboarding screens get their title from `CarnetScreenTitle`).
- [ ] **Step 4:** Run `./gradlew :ui:jvmTest :shared:jvmTest` — Expected: PASS. Then on an emulator with animations off (`adb shell settings put global animator_duration_scale 0`, same for `window_` and `transition_`): `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.TrendsFlowTest,com.maximerollin.aqualog.TimelineFlowTest,com.maximerollin.aqualog.LocalizedAccessibilityFlowTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): four-destination navigation without the top bar`.

### Task 5: Animated tank scene (decor only)

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/TankScene.kt`
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ReducedMotion.kt` (`expect`), `ui/src/androidMain/kotlin/com/maximerollin/aqualog/ReducedMotion.android.kt`, `ui/src/jvmMain/kotlin/com/maximerollin/aqualog/ReducedMotion.jvm.kt`
- Delete: `AquariumArtwork` from `ui/src/commonMain/kotlin/com/maximerollin/aqualog/AquaticUi.kt` (replaced)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/ScenePhysicsTest.kt`

**Interfaces:**
- Produces:
  - `@Composable expect fun rememberReducedMotion(): Boolean` — Android: `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (also `TRANSITION_ANIMATION_SCALE`); JVM: `false`.
  - `@Composable fun TankScene(aquarium: Aquarium, modifier: Modifier = Modifier, animate: Boolean = !rememberReducedMotion())` — Canvas, 140 dp tall: water gradient `#3D9184 → #1F7468 → #0F4A43`, sand band `#D6C497 → #C3AE7E`, 2–3 stem plants (`#4F9D5D`/`#5FB36F`) swaying ±4° over 5 s, bubbles rising over 4 s, light rays; decorative only (`clearAndSetSemantics {}`); when `animate` is false it draws a single still frame.
  - `fun plantSway(progress: Float, amplitudeDegrees: Float = 4f): Float` and `fun bubbleRise(progress: Float, height: Float): Pair<Float, Float>` (y offset, alpha) — pure, reused by lot 5 for fish (`swimPose` comes with lot 5).

- [ ] **Step 1: Write the failing tests** (`ScenePhysicsTest`): `plantSway(0f) == 0f`, `plantSway(0.5f) == 4f`, `plantSway(1f) == 0f` (ease in-out); `bubbleRise(0f, 140f) == (0f to 1f)`, `bubbleRise(1f, 140f) == (-140f to 0f)`.
- [ ] **Step 2:** Run `./gradlew :ui:jvmTest --tests '*ScenePhysicsTest*'` — Expected: FAIL.
- [ ] **Step 3:** Implement the helpers, `rememberReducedMotion`, and `TankScene` with `rememberInfiniteTransition` only when `animate`.
- [ ] **Step 4:** Run `./gradlew :ui:jvmTest` — Expected: PASS. Run `./gradlew :ui:run` and look at the scene on the home (temporarily placed above the current content if Task 6 is not done) — plants sway, bubbles rise, nothing stutters.
- [ ] **Step 5: Commit** `feat(ui): add the animated tank scene`.

### Task 6: New home screen

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/TankHome.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeViewModel.kt` (new state + collector next to `observeLatestSession` L466; new `completeTask`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (delete `AquariumHome` L1282-1440 and its helpers; `AquariumShell` TANK → `TankHome`)
- Delete: `HomeMeasurements` from `AquaticUi.kt` (file removed if empty)
- Modify: 6 `strings.xml`
- Modify (tests): `RapidSessionFlowTest`, `SessionReliabilityFlowTest`, `SyncStatusFlowTest`, `PrivacyFlowTest`, `TaskFlowTest`, `LocalizedAccessibilityFlowTest`, `MultiAquariumFlowTest`

**Interfaces:**
- Consumes: `observeLatestReadings` (Task 2), `homeOverview`, `todayTasks`, `otherAquariumAlerts` (Task 3), `TankScene` (Task 5), `ReadingValue`, `CarnetSectionTitle` (Task 1).
- Produces:
  - `HomeUiState.latestReadings: List<LatestReading>` (collected per current Aquarium, `collectLatest` like `latestSession`).
  - `fun completeTask(occurrence: TaskOccurrence)` = `resolveTask(occurrence, TaskResolution.COMPLETED)`.
  - `@Composable fun TankHome(state: HomeUiState, viewModel: HomeViewModel, modifier: Modifier = Modifier)`.
- Layout (top to bottom, one `LazyColumn` tagged `home-list`, FAB outside it):
  1. `TankScene` with the Aquarium name (Fraunces, white) and « 120 L · 8 mois » (volume + age from `createdAtEpochMillis`).
  2. Status line: attention dot + `home_status_outside_range` (plural) or `home_status_all_in_range` or `home_no_measurement_yet`, then « · » + `home_measured_today` / `home_measured_yesterday` / `home_measured_days_ago` (plural). The whole line is clickable when a Session exists → `openSessionDetails`, `contentDescription` = existing `view_session_details`.
  3. `CarnetSectionTitle(latest_values_title, actionLabel = navigation_trends → selectDestination(TRENDS))`, then a grid of `ReadingValue` (3 columns; 2 when `fontScale ≥ 1.3`); value « — » when null; `age` = `reading_age_days` (plural) only when `isOlderThanLatestSession`.
  4. `CarnetSectionTitle(home_today, actionLabel = home_see_all → openTasks)`; one row per `todayTasks.due`: checkbox (≥ 48 dp, `contentDescription` = existing `task_complete` + title) → `completeTask`, title, `task_due_value`/`home_task_overdue`; when empty: muted line `home_nothing_due` with `next` title and date, or `no_pending_tasks`.
  5. Account invitation and sync-failure cards are kept, restyled with Carnet primitives, at their current positions in the list.
  6. Extended FAB `measure_action` → `openRapidSession` (≥ 48 dp, bottom end, above the navigation bar).
- New strings (en / fr): `measure_action` Measure / Mesurer; `latest_values_title` Latest values / Dernières valeurs; `home_today` Today / Aujourd'hui; `home_see_all` See all / Tout voir; `home_task_overdue` overdue / en retard; `home_nothing_due` Nothing due · next: %1$s on %2$s / Rien à faire · prochaine : %1$s le %2$s; `home_no_measurement_yet` No measurement yet / Pas encore de mesure; `home_status_all_in_range` All in range / Tout est dans la plage; plurals `home_status_outside_range` %1$d value to watch / %1$d valeur à surveiller (other: values / valeurs); `home_measured_today` measured today / mesuré aujourd'hui; `home_measured_yesterday` measured yesterday / mesuré hier; plurals `home_measured_days_ago` measured %1$d days ago / mesuré il y a %1$d j; plurals `reading_age_days` %1$d d ago / il y a %1$d j. Remove keys left unused (`session_saved`, `no_session_yet`, `continue_after_session` only if no longer referenced).

- [ ] **Step 1: Update the failing UI tests**: home assertions on « Session saved » / « No Session yet » become the status line (`home_measured_today`, `home_no_measurement_yet`); « New Session » clicks become « Measure »; `LocalizedAccessibilityFlowTest` essential home actions = `measure_action`, `home_see_all`, `navigation_history`, `navigation_trends`, `navigation_more`. Add to `TaskFlowTest`: `a due task can be completed from the home` (create a task due today, tick it on the home, it leaves the home and appears in Chronologie).
- [ ] **Step 2:** Run `./gradlew :androidApp:assembleDebugAndroidTest :ui:jvmTest` — Expected: `LocalizationContractTest` FAIL (keys missing).
- [ ] **Step 3:** Implement state, collector, `completeTask`, `TankHome`, strings; delete the old home code.
- [ ] **Step 4:** Run `./gradlew :ui:jvmTest :shared:jvmTest` — PASS. Emulator (animations off): `connectedDebugAndroidTest` for the 7 modified test classes — PASS. `./gradlew :ui:run`: home shows scene, status, 6 values, today, FAB without scrolling at 412×915.
- [ ] **Step 5: Commit** `feat(ui): new home — scene, status, latest values, today`.

### Task 7: Multi-aquarium on the home

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/TankHome.kt`
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/AquariumPickerSheet.kt`
- Modify: 6 `strings.xml`
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/MultiAquariumFlowTest.kt`

**Interfaces:**
- Consumes: `otherAquariumAlerts` (Task 3), `selectAquarium(id)` (existing, persists the last consulted Aquarium), `state.aquariums`.
- Produces:
  - When `state.aquariums.size > 1`: the scene is a `HorizontalPager` over `state.aquariums` starting at the current one; settling on a page calls `selectAquarium`; page dots under the scene. With one Aquarium: no pager, no dots.
  - Tapping the Aquarium name opens `AquariumPickerSheet(aquariums, currentId, onSelect, onAdd)` (`ModalBottomSheet`, one row per Aquarium, « Ajouter un aquarium » row → existing `openCreateAquarium`).
  - Alert band under the status line, one row per `AquariumAlert`: « {name} · {task title} · en retard/aujourd'hui › » → `selectAquarium(alert.aquarium.id)`.
- New strings (en / fr): `home_change_aquarium` Change Aquarium / Changer d'aquarium (content description of the name); `home_alert_due_today` today / aujourd'hui (reuse `home_task_overdue` for overdue).

- [ ] **Step 1: Write the failing test** `MultiAquariumFlowTest.swipingTheSceneMeasuresIntoTheShownAquarium`: with Pro and two Aquariums « Aqua 120 » and « Nano 30 », current « Aqua 120 », swipe the scene left, tap « Measure », save one pH value, open History → the Session is listed under « Nano 30 ». Add `alertBandOpensTheOtherAquarium`: task due yesterday on « Nano 30 », home on « Aqua 120 » shows the band, tapping it shows « Nano 30 » on the home.
- [ ] **Step 2:** Run the class on the emulator — Expected: FAIL.
- [ ] **Step 3:** Implement pager, dots, picker sheet, alert band.
- [ ] **Step 4:** Run `MultiAquariumFlowTest` and `LocalizedAccessibilityFlowTest` — PASS.
- [ ] **Step 5: Commit** `feat(ui): switch Aquariums from the home`.

### Task 8: Dense Session entry

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/RapidSessionUi.kt` (whole screen; `MaintenanceActionEditor` L297, `ActionDetails` L337)
- Modify (tests if needed): `RapidSessionFlowTest`, `SessionReliabilityFlowTest`, `LocalizedAccessibilityFlowTest` focus order

**Interfaces:**
- Consumes: all existing `HomeViewModel` Rapid Session functions (unchanged); `isOutsideIndicativeRange` (Task 2) through `evaluateMeasurement`.
- Keeps (tests rely on them): `testTag("rapid-session-list")`, `testTag("rapid-measurement-${storageValue}")`, `contentDescription = measurement_for_parameter`, IME Next/Done order, save button texts `save_session`/`save_changes`.
- Layout:
  - `CarnetScreenTitle(rapid_session_title or edit_session, context = « {Aquarium} · {date time} »)`, ±15 min controls as two icon buttons next to the time.
  - One row per active parameter (min height 48 dp): name, small unit, small range « 6,5–7,5 », value field on the right with an underline instead of a box; underline and value take the attention colour when out of range; the hint line (`unusual_measurement` / `invalid_measurement`) appears in italics under the row **only** when it applies. Drop the per-row « last measurement / not measured yet » text.
  - Maintenance: `FlowRow` of `FilterChip`s, one per `MaintenanceActionType`; selected chip shows its quantity (« Changement d'eau · 30 L »); tapping a selected chip opens the existing quantity/unit/product editor inline; long-press or a trailing close icon deselects.
  - Observation / incident: one muted text row each that expands into its field.
  - At font scale ≥ 1.3 or width < 280 dp the row stacks (existing `BoxWithConstraints` rule).

- [ ] **Step 1:** Run `RapidSessionFlowTest`, `SessionReliabilityFlowTest` on the emulator before changing anything — Expected: PASS (baseline).
- [ ] **Step 2:** Rewrite the screen.
- [ ] **Step 3:** Re-run both classes plus `LocalizedAccessibilityFlowTest` — Expected: PASS; fix only selectors that depended on removed text (« Last … »).
- [ ] **Step 4:** `./gradlew :ui:run`: 7 parameters and the maintenance chips visible without scrolling at 412×915.
- [ ] **Step 5: Commit** `feat(ui): dense Session entry`.

### Task 9: Chronologie in Carnet style

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TimelineUi.kt` (`TimelineList` L128, `TimelineTaskCard` L181, `TimelineFilters` L206, `TimelineSessionCard` L307; delete dead `TimelineDetail` L343 and `DetailCard`; `timelineLabel()` replaced by `label()` from `RapidSessionUi.kt`)
- Modify: `HomeViewModel.kt` (remove `TimelineUiState.selectedSessionId`, `openTimelineSession`/`closeTimelineSession` only if `openTimelineSession` stays the entry to `sessionDetail`)
- Create: `ui/src/commonMain/composeResources/drawable/ic_check.xml`, `ic_note.xml`, `ic_drop.xml`

**Interfaces:**
- Keeps: `contentDescription = open_session_details` on each Session, filter behaviour, `timeline_offline_status` moved into the filter sheet header (no longer a permanent line).
- Layout: `CarnetScreenTitle(timeline_title, context = « {Aquarium or all} · {period} »)` with a filter icon action (opens the existing filters in a `ModalBottomSheet`); date headers in Fraunces primary; each entry has a left rail (dot + line); a Session shows « Session » + time, then a row of `ReadingValue` (number + small label, attention colour when out of range, no box), then one line per maintenance action with `ic_drop`; resolved Tâches and Événements are single lines with `ic_check` / `ic_note`.

- [ ] **Step 1:** Run `TimelineFlowTest`, `TaskFlowTest` — baseline PASS.
- [ ] **Step 2:** Rewrite as above.
- [ ] **Step 3:** Re-run both + `LocalizedAccessibilityFlowTest` — PASS (update « Filters » click to the filter icon's content description `timeline_filters` if the text button is gone).
- [ ] **Step 4:** `./gradlew :ui:run` with ≥ 4 Sessions: about 4 Sessions per screen.
- [ ] **Step 5: Commit** `feat(ui): Carnet Chronologie`.

### Task 10: Tendances as small multiples

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TrendsUi.kt` (`TrendContent` L68, `TrendPeriods` L452, `TrendParameters` L479, `TrendChart` L523)
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TrendSparkline.kt`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/TrendSparklineTest.kt`

**Interfaces:**
- Consumes: `TrendSnapshot.series`, `TrendSnapshot.events`, existing `selectTrendParameter`, `selectTrendPeriod`.
- Produces:
  - `fun sparklinePoints(series: ParameterTrendSeries, windowStart: Long, windowEnd: Long, width: Float, height: Float): List<Offset>` — x by time in window, y scaled to `min(points ∪ range)…max(points ∪ range)`, top = max.
  - `@Composable fun TrendSparklineRow(series: ParameterTrendSeries, snapshot: TrendSnapshot, selected: Boolean, onClick: () -> Unit)` — latest value large (attention colour when out of range), label, sparkline over the range band (`primaryContainer`), water-change markers; `contentDescription` = existing `trends_chart_description` content for that series.
- Layout: `CarnetScreenTitle(trends_title, context = « {Aquarium} · {period} »)`; period as a 3-segment row; one `TrendSparklineRow` per series; tapping a row expands it in place into the existing `TrendChart` + exact values (keeps `trends_exact_values`, `trend-list`, `trend-intervention-*`); the Pro entry (complete history, advanced analysis) becomes the last row.

- [ ] **Step 1: Write the failing tests** (`TrendSparklineTest`): two points at window start (value 6.5) and end (7.5) with range 6.5–7.5, size 100×30 → `[(0, 30), (100, 0)]`; single point in the middle of the window → x = 50; a point outside the range widens the scale (value 8.0 → y = 0, range max below it).
- [ ] **Step 2:** Run `./gradlew :ui:jvmTest --tests '*TrendSparklineTest*'` — FAIL.
- [ ] **Step 3:** Implement `sparklinePoints`, `TrendSparklineRow`, the new layout.
- [ ] **Step 4:** `./gradlew :ui:jvmTest` PASS; emulator `TrendsFlowTest` + `LocalizedAccessibilityFlowTest` PASS (selectors that tapped parameter chips now tap the row).
- [ ] **Step 5: Commit** `feat(ui): Tendances as small multiples`.

### Task 11: Tâches list first

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TaskUi.kt` (`TaskScreen` L50, `ReminderInformationCard` L224, `TaskOccurrenceCard` L234)
- Modify (tests): `TaskFlowTest`, `LocalizedAccessibilityFlowTest`

**Interfaces:**
- Keeps: `task-list`, `tasks-back`, `task-date-picker`, `task-time-picker` tags; all task ViewModel functions.
- Layout: `CarnetScreenTitle(tasks_title)` + back; **one** banner merging notification permission and battery optimisation (only when one applies, with its action); pending occurrences as rows (checkbox = complete, title, due, overflow menu with `task_postpone` / `task_ignore`); `new_task` button opens a `ModalBottomSheet` holding the existing form (title, date, time, recurrence, `save_task`); the sheet closes after a successful save.

- [ ] **Step 1: Update `TaskFlowTest`** to open the sheet with « New Task » before typing, and to postpone/ignore through the overflow menu.
- [ ] **Step 2:** Run it on the emulator — Expected: FAIL.
- [ ] **Step 3:** Rewrite the screen.
- [ ] **Step 4:** `TaskFlowTest` + `LocalizedAccessibilityFlowTest` PASS.
- [ ] **Step 5: Commit** `feat(ui): Tâches list first, creation in a sheet`.

### Task 12: « Plus » screen as list rows

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (`PortableDataSettings` L344-566)
- Modify (tests): `AccountControlSettingsTest`, `PortableDataFlowTest`, `MultiAquariumFlowTest`, `PrivacyFlowTest` selectors if needed

**Interfaces:**
- Keeps: `settings-list` tag, every existing action and dialog.
- Layout: `CarnetScreenTitle(navigation_more)`; sections with `CarnetSectionTitle`: **Tâches** (row `open_tasks` → `openTasks`), **Aquariums** (one row per Aquarium with a selected mark, rows `add_aquarium`, `edit_aquarium`, `archive_aquarium`), **Offre** (`free_plan`/`pro_active`, row `view_free_and_pro`), **Compte**, **Données** (`export_data`, `restore_from_export`), **Aide** (`send_feedback`, privacy, support), **Compte (actions)** (`sign_out`, `delete_account` in error colour). Rows are 48–56 dp `ListItem`s separated by `CarnetDivider`; no filled full-width buttons.

- [ ] **Step 1:** Run the four classes — baseline PASS.
- [ ] **Step 2:** Rewrite the screen.
- [ ] **Step 3:** Re-run the four classes + `LocalizedAccessibilityFlowTest` — PASS.
- [ ] **Step 4: Commit** `feat(ui): Plus screen as list rows`.

### Task 13: Onboarding, paywall, details — and full verification

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (`WelcomeScreen` L318, `ProfileScreen`, `AquariumDetailsScreen` L803, `ParametersScreen` L862-980, `PracticeScreen` L981, `PaywallScreen` L1015, `AccountScreen` L173)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/SessionDetailsUi.kt`
- Modify: `docs/design/19-ui/implementation.md` (one line: the home now shows the latest value per parameter with its age, see the lot-1 spec §7)

**Interfaces:** none new; screens adopt `CarnetScreenTitle`, `CarnetSectionTitle`, `ReadingValue`, `CarnetDivider`; `ParametersScreen` editor cards become rows.

- [ ] **Step 1:** Restyle the listed screens.
- [ ] **Step 2:** Run `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest :androidApp:testReleaseUnitTest :androidApp:lintDebug :androidApp:lintRelease :androidApp:assembleRelease --console=plain` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** Emulator with animations off: `./gradlew :androidApp:connectedDebugAndroidTest --console=plain` — Expected: all tests PASS.
- [ ] **Step 4:** Manual pass with animations on, `./gradlew :androidApp:installDebug`: French light, German dark at 150 % font; check the scene moves, stops with « Supprimer les animations », and no screen shows an emoji or the old top bar.
- [ ] **Step 5: Commit** `feat(ui): Carnet onboarding, paywall and Session details`.
