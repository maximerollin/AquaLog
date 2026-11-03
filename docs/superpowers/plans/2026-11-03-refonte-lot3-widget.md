# Refonte lot 3 — Widget Pro Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three home-screen widgets (Petit, Moyen, « Mes bacs ») that show an Aquarium's state and its next thing to do. They are Pro, built with Jetpack Glance, and fed by the lot 2 rhythm.

**Architecture:**
- **What a widget shows (`:ui` commonMain, `widget/`):** what an Aquarium shows on a widget is a pure function, `widgetAquarium`, tested on the JVM. It is turned into localized text by `widgetText`, which reuses the home's string keys.
- **Glance code (`:androidApp`, `widget/`):** each widget reads the repository in `provideGlance` and renders plain data with Glance composables, which are tested on the emulator.
- **Ticking a Tâche:** goes through a `TaskCompleter` extracted from `HomeViewModel`, so the home and the widget share one path.
- **Pro offline:** the Pro state is cached on the device by `ProPurchaseCoordinator`, so the widget knows Pro without network.

**Tech Stack:** Kotlin 2.4.20, Jetpack Glance `androidx.glance:glance-appwidget:1.2.0` + `glance-material3:1.2.0` (+ `glance-appwidget-testing:1.2.0` for androidTest), Room 3, Compose Multiplatform resources.

**Spec:** `docs/superpowers/specs/2026-10-30-refonte-aquagenda-design.md`: §4.4 Widget Pro, §4.3 (« Les tests dus s'affichent … dans le widget »), §4.7 (the widget is Pro), §7 (« Cocher une Tâche liée … depuis le widget enregistre l'action avec sa quantité habituelle »). Mockup: `docs/design/20-refonte-carnet/05-widget.html`.

## Global Constraints

- **No animation.** « Le widget n'est pas animé, et un tap ouvre l'app sur le bac concerné. »
- **Free users.**
  - A free user sees the widgets in the picker.
  - A placed widget invites them to Pro, and a tap opens the paywall.
- **Habitants and photos come later.**
  - The small widget's scene shows the decor only (water, sand, plants), as the home did in lot 1. Habitants arrive in lot 5.
  - « Mes bacs » has no photo. Photos arrive in lot 4.
- **French copy:** French uses « vous ». Use the terms of `docs/localization-glossary.md`: Session, Mesure, Tâche, Aquarium.
- **Localization.**
  - Every new key exists in the 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`): the Compose resources under `ui/src/commonMain/composeResources/` and the Android resources under `androidApp/src/main/res/`.
  - `LocalizationContractTest` stays green, which means no `Text("…")` literals, Glance included.
- **Accessibility.**
  - Touch targets are ≥ 48 dp.
  - Text contrast follows WCAG: 4.5:1 for text, 3:1 for marks.
  - Every tappable element has a TalkBack label.
  - Nothing is cut mid-word in German at font scale 1.5: text uses `maxLines` with an ellipsis.
- **Colours** come from the Carnet palette, in light and dark (`AquaLogTheme.kt`). Status marks use `AttentionMark` / `AlertMark` and their dark variants. Glance uses the system font.
- **No new telemetry event or value.**
  - Ticking from the widget records the existing `ProductEvent.TaskUsed(TaskUsage.COMPLETED)`.
  - The widget's paywall records the existing `PaywallViewed(PaywallSource.PRO_FEATURE)`.
- **Battery.**
  - Widgets refresh when the data changes while the process is alive, and otherwise hourly (`updatePeriodMillis = 3600000`).
  - No alarm or worker is added for the widget.
- **CI must stay green:** `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest :androidApp:testReleaseUnitTest :androidApp:lintDebug :androidApp:lintRelease :androidApp:assembleRelease`.

## Decisions taken in this plan (Claude's calls, to check)

- **Which Aquarium a small or medium widget shows.**
  - By default it follows the Aquarium last viewed in the app.
  - Long-pressing the widget (reconfigure) pins a specific one, so that two widgets can show two Aquariums.
  - On API 31+ the configuration screen is optional (`configuration_optional|reconfigurable`). Below API 31 it opens at placement.
- **What « la prochaine chose à faire » is.** The first of these that applies:
  1. a value in ALERT;
  2. an overdue Tâche;
  3. due tests (« Tester NO₃ et pH », as on the home);
  4. a value in ATTENTION;
  5. a Tâche due today;
  6. « Rien à faire ».
- **The status dot** is the home's: red if a value is in ALERT, amber if a value is out of range, teal otherwise. An overdue Tâche shows in the text, not in the dot.
- **The medium widget leaves out the mockup's piste line.** The spec asks for « l'état en une phrase » + Tâches + « Mesurer ». The state sentence is the home's status line.
- **Pro offline.**
  - The last server answer is cached.
  - A network failure keeps the cache.
  - A rejection or « authentication required » clears it.
  - An annual entitlement past `validUntilEpochMillis` counts as not Pro.

## Review Focus

1. **« Mesurer » on a widget for Aquarium B while the app shows A** must open entry for B, never A. If an entry draft is already open, the widget does not replace it: the app just comes forward. Test: Task 4, `widgetMeasure_forAnotherAquarium_opensEntryForThatAquarium`.
2. **A double tap on a Tâche checkbox in the widget** records the linked action once and resolves the occurrence once. Test: Task 2, `completing an already resolved occurrence does nothing`.
3. **A pinned Aquarium that was archived or deleted** makes the widget fall back to the last viewed Aquarium, without a blank widget or a crash. Test: Task 1, `a missing pinned aquarium falls back to the current one`.
4. **Pro status edge cases.** A Pro user offline (verification `Failed`) keeps the widget. A signed-out user, or one whose annual entitlement expired, gets the invitation. Tests: Task 3, the four cache cases.
5. **A widget placed before any Aquarium exists** (fresh install) shows « Ouvrez Aquagenda pour créer votre bac » and opens the app. Test: Task 5, `noAquarium_showsTheInvitationToOpenTheApp`.

---

### Task 1: What a widget shows

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/widget/WidgetAquarium.kt`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/widget/WidgetAquariumTest.kt`

**Interfaces:**
- Consumes:
  - `homeOverview(setup, readings): HomeOverview`, `todayTasks(pending, aquariumId, today): TodayTasks`;
  - `dueTests(setup, samples, today): List<DueTest>`, `ReadingLevel`;
  - repository `observeAquariums()`, `observeCurrentAquarium()`, `loadSetup(id)`, `observeLatestReadings(id)`, `observeReadingSamples(id, zoneId)`, `observePendingTaskOccurrences()`.
- Produces:
  ```kotlin
  enum class WidgetLight { OK, ATTENTION, ALERT }
  sealed interface NextStep {
      data class Value(val definition: ParameterDefinition, val level: ReadingLevel) : NextStep
      data class Task(val occurrence: TaskOccurrence, val isOverdue: Boolean) : NextStep
      data class Tests(val tests: List<DueTest>) : NextStep
      data object Nothing : NextStep
  }
  data class WidgetAquarium(
      val aquarium: Aquarium,
      val overview: HomeOverview,
      val light: WidgetLight,
      val next: NextStep,
      val dueTasks: List<TaskOccurrence>, // todayTasks(...).due: due today or overdue, at most 3
  )
  fun widgetAquarium(setup: AquariumSetup, readings: List<LatestReading>, dueTests: List<DueTest>, pending: List<TaskOccurrence>, today: TaskDate): WidgetAquarium
  /** [pinnedId] when it is a non-archived Aquarium, else the current one, else null (no Aquarium yet). */
  suspend fun AquariumRepository.loadWidgetAquarium(pinnedId: String?, today: TaskDate, timeZoneId: String): WidgetAquarium?
  /** Every non-archived Aquarium, in the app's order. */
  suspend fun AquariumRepository.loadWidgetAquariums(today: TaskDate, timeZoneId: String): List<WidgetAquarium>
  ```

- [ ] **Step 1: Write the failing tests.** Build fixtures as `HomeOverviewTest` does. For the repository tests, use a real repository: `RoomAquariumRepository(createAquaLogDatabase(createJvmDatabaseBuilder(path)), …)`, as `TaskRepositoryContractTest` does.
  - `an alert value comes before an overdue task`: nitrite 0.5 (ALERT) + water-change Tâche due yesterday → `next == NextStep.Value(nitrite, ALERT)`, `light == ALERT`.
  - `an overdue task comes before due tests`: all values OK, a Tâche due yesterday, pH due → `next is NextStep.Task && isOverdue`.
  - `due tests come before an attention value`: pH 7.8 (ATTENTION) and nitrate due → `next == NextStep.Tests([nitrate])`, `light == ATTENTION`.
  - `an attention value comes before a task due today`.
  - `nothing to do`: all OK, nothing due → `NextStep.Nothing`, `light == OK`.
  - `a task of another aquarium is ignored`.
  - `due tasks are today's and overdue ones, at most 3`.
  - `a missing pinned aquarium falls back to the current one`: pin an id, archive that Aquarium → `loadWidgetAquarium(pinned, …)!!.aquarium.id == currentId`.
  - `no aquarium gives null`.

- [ ] **Step 2: Run them to see them fail.**
  Run `./gradlew :ui:jvmTest --tests '*WidgetAquariumTest*'`. Expected: compilation failure (`widgetAquarium` is undefined).

- [ ] **Step 3: Implement `widgetAquarium`, `loadWidgetAquarium` and `loadWidgetAquariums`.**
  - Priority order as in « Decisions ».
  - `Value` picks the first parameter by `position` at that level.
  - `light` follows `overview.hasAlert` / `outsideRangeCount > 0`.
  - The loaders read each flow once with `.first()`, like `dueTestSummary` in `DueTests.kt`.

- [ ] **Step 4: Run the tests to see them pass.**
  Run the same command. Expected: PASS.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(widget): what a widget shows for an Aquarium"`

### Task 2: One path to tick a Tâche

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TaskCompleter.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeViewModel.kt` (`completeTask`, `completeLinkedTasks`, and the COMPLETED branch of `resolveTask` delegate to it)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/TaskCompleterTest.kt`

**Interfaces:**
- Consumes: `AquariumRepository.completeLinkedTasks(...)`, `resolveTaskOccurrence(...)`, `loadRapidSessionContext(...)`, `saveRapidSession(...)`; `defaultMaintenanceInput(type, last)`; `TaskReminderScheduler`.
- Produces:
  ```kotlin
  class TaskCompleter(
      private val repository: AquariumRepository,
      private val scheduler: TaskReminderScheduler,
      private val recordEvent: (ProductEvent) -> Unit,
      private val now: () -> Long,
      private val today: () -> TaskDate,
      private val newId: () -> String,
  ) {
      /** A linked Tâche records its action (usual quantity) in a Session and resolves; a free-text one resolves. Returns the recorded Session, or null. Throws on failure. */
      suspend fun complete(occurrence: TaskOccurrence): RecordedSession?
      /** Resolves the linked Tâches that [recorded]'s actions satisfy (called after a Session save). */
      suspend fun completeLinked(recorded: RecordedSession, alreadyRecorded: Set<MaintenanceActionType> = emptySet(), alsoOccurrenceId: String? = null)
  }
  ```

- [ ] **Step 1: Write the failing tests.** Use a real Room repository, as in Task 1, and a recording fake `TaskReminderScheduler`.
  - `a linked water change records the usual volume and resolves the occurrence`: the last water change was 30 L → the new Session has a 30 L water change. The occurrence is resolved, `scheduler.cancelled` contains its id, and `TaskUsed(COMPLETED)` is recorded once.
  - `a free-text task resolves without a Session`: returns null and the occurrence is resolved.
  - `completing an already resolved occurrence does nothing`: the second `complete` on the same occurrence records no Session and no event.

- [ ] **Step 2: Run them to see them fail.**
  Run `./gradlew :ui:jvmTest --tests '*TaskCompleterTest*'`. Expected: compilation failure.

- [ ] **Step 3: Implement `TaskCompleter`.**
  - Move the bodies out of `HomeViewModel` unchanged.
  - The only new behaviour: `complete` first checks that the occurrence is still in `observePendingTaskOccurrences().first()`, and returns null otherwise.
  - `HomeViewModel` builds one `TaskCompleter` from its own dependencies.
  - `HomeViewModel` keeps its UI-state update (`latestSession` only for the displayed Aquarium) and its `onFailed` callback.

- [ ] **Step 4: Run the tests to see them pass, the new ones and the existing UI tests.**
  Run `./gradlew :ui:jvmTest`. Expected: PASS.

- [ ] **Step 5: Commit.**
  `git commit -m "refactor: one TaskCompleter for the home and the widget"`

### Task 3: Pro known offline

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/ProPurchase.kt`
- Modify: `androidApp/src/main/kotlin/com/maximerollin/aqualog/ProBillingInfrastructure.kt` (Android cache), `AquaLogApplication.kt` (wiring)
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/ProPurchaseCoordinatorTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  interface ProEntitlementCache {
      fun read(): ProEntitlement?
      fun write(entitlement: ProEntitlement?)
  }
  fun ProEntitlement?.isActiveAt(nowEpochMillis: Long): Boolean // non-null and (validUntil == null || validUntil > now)
  class ProPurchaseCoordinator(billingGateway, purchaseVerifier, private val entitlementCache: ProEntitlementCache? = null)
  class SharedPreferencesProEntitlementCache(context: Context, private val onChanged: () -> Unit) : ProEntitlementCache // androidApp; prefs file "pro-entitlement"
  ```
  `AquaLogApplication` exposes `val proEntitlementCache: ProEntitlementCache`. Its `onChanged` updates all widgets once Task 7 exists. Until then, pass `{}`.

- [ ] **Step 1: Write the failing tests** in `ProPurchaseCoordinatorTest`, with an in-memory cache.
  - `a verified entitlement is cached`.
  - `a network failure keeps the cached entitlement`: after `Verified`, a `Failed` keeps it.
  - `not entitled and authentication required clear the cache`.
  - `an expired annual entitlement is not active`: `ProEntitlement(ANNUAL_PRO_PRODUCT_ID, 1_000).isActiveAt(1_000) == false`, `isActiveAt(999) == true`, and a lifetime entitlement (`null`) is always active.

- [ ] **Step 2: Run them to see them fail.**
  Run `./gradlew :shared:jvmTest --tests '*ProPurchaseCoordinatorTest*'`. Expected: compilation failure.

- [ ] **Step 3: Implement the cache.**
  - In `applyVerification`:
    - `Verified` → `write(entitlement)`;
    - `Rejected` when `!rejectionIsFailure` → `write(null)`;
    - `AuthenticationRequired` → `write(null)`;
    - `Rejected` as a failure, and `Failed` → no write.
  - `restorePurchase`'s `AuthenticationRequired` branch also writes null.
  - Android side:
    - stores `productId` and `validUntil`, with `-1` meaning lifetime;
    - calls `onChanged` only when the stored value changes.

- [ ] **Step 4: Run the tests to see them pass.**
  Run `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(pro): remember the Pro entitlement on the device"`

### Task 4: Open the app from a widget

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeViewModel.kt`
- Modify: `androidApp/src/main/kotlin/com/maximerollin/aqualog/MainActivity.kt`
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/WidgetLaunchFlowTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class WidgetTarget { HOME, MEASURE, PRO }
  /** Brings the app to [aquariumId] (when non-null and not archived), then opens entry for MEASURE or the paywall for PRO. */
  fun HomeViewModel.openFromWidget(aquariumId: String?, target: WidgetTarget)
  // MainActivity companion
  const val EXTRA_WIDGET_AQUARIUM_ID = "widget-aquarium-id"
  const val EXTRA_WIDGET_TARGET = "widget-target" // WidgetTarget.name
  ```

- [ ] **Step 1: Write the failing tests** in `WidgetLaunchFlowTest`. Launch `MainActivity` with the extras, using the `ActivityScenario` + `createEmptyComposeRule` pattern of `MultiAquariumFlowTest`.
  - `widgetMeasure_forAnotherAquarium_opensEntryForThatAquarium`: two Aquariums, A shown. Intent `(B, MEASURE)` → the entry screen's title names B, and saving puts the Session in B.
  - `widgetMeasure_withADraftOpen_keepsTheDraft`: a draft is open with a typed pH value. A new intent `(A, MEASURE)` via `onNewIntent` → the typed value is still there.
  - `widgetPro_opensThePaywall`.
  - `widgetHome_forAnArchivedAquarium_staysOnTheCurrentOne`.

- [ ] **Step 2: Run them to see them fail.**
  Run on the emulator: `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.WidgetLaunchFlowTest`. Expected: FAIL.

- [ ] **Step 3: Implement `openFromWidget` and the intent handling.**
  - **Before onboarding is complete:** does nothing.
  - **When an entry draft is open:** returns without changing anything.
  - **Otherwise:**
    - selects the Aquarium if it is another one;
    - waits until `uiState.setup?.aquarium?.id == aquariumId` (first matching state, with a 5 s timeout) before `openRapidSession()`;
    - `PRO` records `PaywallViewed(PaywallSource.PRO_FEATURE)` and shows the paywall the way `openProPaywall` does.
  - **`MainActivity`:**
    - reads the extras in `onCreate` and `onNewIntent`, next to `handleAuthenticationIntent`;
    - then clears them with `setIntent(Intent(this, MainActivity::class.java))`, so a recreation does not replay them.

- [ ] **Step 4: Run the tests to see them pass.**
  Run the same command. Expected: 4 passed.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(widget): open the app on an Aquarium, its entry or the paywall"`

### Task 5: Glance foundation and the small widget

**Files:**
- Modify: `gradle/libs.versions.toml` (`glance = "1.2.0"`, plus `glance-appwidget`, `glance-material3` and `glance-appwidget-testing`); `androidApp/build.gradle.kts`.
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/AquaLogTheme.kt`: rename `LightColors` / `DarkColors` to public `CarnetLightColors` / `CarnetDarkColors`, so the widgets share the palette.
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/widget/WidgetText.kt`
- Create in `androidApp/src/main/kotlin/com/maximerollin/aqualog/widget/`:
  - `WidgetCommon.kt`: theme, status dot, Pro invitation, no-Aquarium invitation, launch actions;
  - `SmallTankWidget.kt`: `GlanceAppWidget` + receiver.
- Create: `androidApp/src/main/res/xml/widget_small_info.xml`, `androidApp/src/main/res/drawable/widget_scene.xml` (static vector: water `#3D9184`→`#14675E`, sand band `#D6C497`, two plants).
- Modify: `AndroidManifest.xml` (receiver), the 6 Android `strings.xml` files, and the 6 Compose `strings.xml` files.
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/widget/SmallTankWidgetTest.kt`

**Interfaces:**
- Consumes: Task 1 `WidgetAquarium`, `loadWidgetAquarium`; Task 3 `proEntitlementCache.read().isActiveAt(now)`; Task 4 `EXTRA_WIDGET_*`, `WidgetTarget`; `dueTestsLabels(tests)`.
- Produces:
  ```kotlin
  // :ui
  data class WidgetTaskRow(val occurrenceId: String, val title: String, val isOverdue: Boolean)
  data class WidgetTankText(
      val aquariumId: String, val name: String, val light: WidgetLight,
      val lightDescription: String, // TalkBack: « Tout est dans la plage » / attention / alert description
      val next: String, val status: String, val tasks: List<WidgetTaskRow>,
  )
  suspend fun widgetText(state: WidgetAquarium, today: LocalDate): WidgetTankText
  // androidApp
  sealed interface WidgetContent { data object NoAquarium; data object NeedsPro; data class Tank(val text: WidgetTankText); data class Tanks(val rows: List<WidgetTankText>) }
  val PINNED_AQUARIUM_ID = stringPreferencesKey("aquarium-id") // Glance state of small/medium widgets
  @Composable fun SmallTankContent(content: WidgetContent)
  fun openAppAction(aquariumId: String?, target: WidgetTarget): Action // actionStartActivity<MainActivity> with the extras
  ```

- **New copy** (FR shown; EN/DE/IT/ES written in the same register, following the glossary):
  - Compose catalogs:
    - `widget_value_attention` « %1$s à surveiller »;
    - `widget_value_alert` « %1$s très hors plage »;
    - `widget_task_overdue` « %1$s en retard »;
    - `widget_task_today` « %1$s aujourd’hui »;
    - `widget_nothing_to_do` « Rien à faire ».
  - Android catalogs:
    - `widget_small_description` « L’état de votre bac et la prochaine chose à faire »;
    - `widget_medium_description` « L’état, les tâches du jour et Mesurer »;
    - `widget_tanks_description` « Tous vos bacs, une ligne chacun »;
    - `widget_needs_pro` « Le widget fait partie d’Aquagenda Pro »;
    - `widget_needs_pro_action` « Découvrir Pro »;
    - `widget_no_aquarium` « Ouvrez Aquagenda pour créer votre bac »;
    - `widget_configure_title` « Choisir le bac ».
  - Reused keys:
    - `status` is built exactly as `StatusLine` in `TankHome.kt` does, with the same keys (`home_status_*`, `home_measured_*`, `home_no_measurement_yet`), joined with « · »;
    - `Tests` → `due_tests_row` + `dueTestsLabels`;
    - `measure_action` for the button.

- [ ] **Step 1: Write the failing tests** with `runGlanceAppWidgetUnitTest { provideComposable { SmallTankContent(...) } }`.
  - `alertTank_showsNameNextStepAndAlertDescription`: name « Salon », next « Nitrite très hors plage ». The status dot's content description equals the ALERT description.
  - `needsPro_showsTheInvitation_andOpensThePaywall`: the text « Le widget fait partie d’Aquagenda Pro » is shown, and the root click action targets `MainActivity` with `EXTRA_WIDGET_TARGET == "PRO"`.
  - `noAquarium_showsTheInvitationToOpenTheApp`.
  - `tank_tapOpensTheAppOnThatAquarium`: the extras are `(aquariumId, HOME)`.
  - `germanLargeFont_nextStepIsEllipsizedNotCut`: `next` has `maxLines == 2`. Assert it on the Text node's style.

- [ ] **Step 2: Run them to see them fail.**
  Run on the emulator: `-Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.widget.SmallTankWidgetTest`. Expected: FAIL.

- [ ] **Step 3: Implement the foundation and the small widget.**
  - **`provideGlance`:**
    - reads the application (`context.applicationContext as AquaLogApplication`), its repository, clock and zone;
    - decides `NoAquarium` / `NeedsPro` / `Tank`;
    - computes `widgetText` before `provideContent`;
    - wraps the content in `GlanceTheme(colors = ColorProviders(light = CarnetLightColors, dark = CarnetDarkColors))`.
  - **Layout:**
    - scene image 42 dp high;
    - name (serif, bold, 1 line);
    - dot + next (2 lines);
    - the whole widget is one tappable area with a content description of « name, next ».
  - **`widget_small_info.xml`:**
    - `targetCellWidth=2`, `targetCellHeight=2`, `minWidth=110dp`, `minHeight=110dp`, `updatePeriodMillis=3600000`, `previewImage=@drawable/widget_scene`;
    - `widgetFeatures="reconfigurable|configuration_optional"`, with `configure` added in Task 8;
    - `description=@string/widget_small_description`.

- [ ] **Step 4: Run the tests to see them pass, plus `:ui:jvmTest`** (`LocalizationContractTest`).
  Expected: PASS.

- [ ] **Step 5: Place the widget by hand on the emulator** in FR light and DE dark at font scale 1.5, as a Pro test user and as a free user. Save a capture of each.

- [ ] **Step 6: Commit.**
  `git commit -m "feat(widget): Glance foundation and the small widget"`

### Task 6: Medium widget with Tâches to tick

**Files:**
- Create: `androidApp/src/main/kotlin/com/maximerollin/aqualog/widget/MediumTankWidget.kt` (widget, receiver, `CompleteTaskAction`)
- Create: `androidApp/src/main/res/xml/widget_medium_info.xml` (`targetCellWidth=4`, `targetCellHeight=2`, `minWidth=250dp`, `minHeight=110dp`, other attributes as in Task 5)
- Modify: `AndroidManifest.xml`
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/widget/MediumTankWidgetTest.kt`

**Interfaces:**
- Consumes: Task 2 `TaskCompleter.complete`; Task 5 `WidgetContent`, `WidgetTankText`, `openAppAction`.
- Produces:
  - `@Composable fun MediumTankContent(content: WidgetContent)`;
  - `class CompleteTaskAction : ActionCallback`, with parameter key `OCCURRENCE_ID = ActionParameters.Key<String>("occurrence-id")`.

- [ ] **Step 1: Write the failing tests.**
  - `showsStatusTasksAndMeasure`: status « 1 valeur à surveiller · mesuré il y a 2 j ». Two tasks are shown, the overdue one with « en retard », and the « Mesurer » button targets `(id, MEASURE)`.
  - `eachTaskCheckbox_runsCompleteTaskWithItsOccurrence`: the checkbox's action is `CompleteTaskAction` with that occurrence id. Its TalkBack label is the task title.
  - `noTasks_showsNextStepInstead`: shows `next` in place of the list.
  - `completeTaskAction_completesALinkedTaskOnce`: run `CompleteTaskAction().onAction(context, glanceId, params)` twice on the test application's repository → one Session with the water change, and the occurrence is resolved.

- [ ] **Step 2: Run them to see them fail.**
  Run on the emulator, class `MediumTankWidgetTest`. Expected: FAIL.

- [ ] **Step 3: Implement the medium widget.**
  - **Layout:** name + dot, status (1 line), up to 3 checkbox rows (48 dp high), and a « Mesurer » button aligned to the end.
  - **`CompleteTaskAction`:**
    - builds a `TaskCompleter` from the application's repository, scheduler, telemetry and clock;
    - calls `complete`, catching and ignoring failures (the widget just re-renders);
    - then updates the widgets (`updateAll` on the three widget classes, through Task 7's `updateAllWidgets` once it exists, directly until then).

- [ ] **Step 4: Run the tests to see them pass.**
  Expected: PASS.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(widget): medium widget with Tâches and Mesurer"`

### Task 7: « Mes bacs » and keeping widgets fresh

**Files:**
- Create in `androidApp/src/main/kotlin/com/maximerollin/aqualog/widget/`:
  - `TanksWidget.kt`;
  - `WidgetUpdates.kt`: `suspend fun updateAllWidgets(context: Context)` and its use in the application.
- Create: `androidApp/src/main/res/xml/widget_tanks_info.xml` (`targetCellWidth=4`, `targetCellHeight=2`, `resizeMode="horizontal|vertical"`, no configure)
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/AquariumRepository.kt`: `fun observeChanges(): Flow<Unit>` (interface default `emptyFlow()`). The Room implementation uses `database.invalidationTracker.createFlow(…)` over the tables of Aquariums, parameter definitions, Sessions, Measurements and task occurrences.
- Modify: `AquaLogApplication.kt`, `AndroidManifest.xml`
- Test:
  - `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/widget/TanksWidgetTest.kt`;
  - `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/AquariumRepositoryContractTest.kt`: `observeChanges emits after a Session is saved`.

**Interfaces:**
- Consumes: Task 1 `loadWidgetAquariums`; Task 5 `widgetText`, `openAppAction`.
- Produces: `@Composable fun TanksContent(content: WidgetContent)`; `suspend fun updateAllWidgets(context: Context)`.

- [ ] **Step 1: Write the failing tests.**
  - `oneRowPerAquarium_withDotAndNextStep`: three tanks, the rows read « Bac du salon · Changement d’eau en retard », etc.
  - `eachRowOpensItsAquarium`: tapping a row opens `(id, HOME)`.
  - The repository test above.

- [ ] **Step 2: Run them to see them fail.**
  Expected: FAIL.

- [ ] **Step 3: Implement « Mes bacs » and the refresh.**
  - **« Mes bacs »:**
    - a Glance `LazyColumn` of rows, each 48 dp: dot, name (1 line), next (1 line);
    - free users → `NeedsPro`.
  - **Refresh:**
    - in `AquaLogApplication.onCreate`, an application scope collects `aquariumRepository.observeChanges().debounce(1_000)` → `updateAllWidgets(this)`;
    - `proEntitlementCache`'s `onChanged` → `updateAllWidgets`;
    - `CompleteTaskAction` now calls `updateAllWidgets`.

- [ ] **Step 4: Run the tests to see them pass**, plus `:shared:jvmTest`.
  Expected: PASS.

- [ ] **Step 5: Commit.**
  `git commit -m "feat(widget): Mes bacs, and widgets follow the data"`

### Task 8: Choose the Aquarium of a small or medium widget

**Files:**
- Create: `androidApp/src/main/kotlin/com/maximerollin/aqualog/widget/WidgetConfigureActivity.kt`
- Modify: `widget_small_info.xml`, `widget_medium_info.xml` (`android:configure`), `AndroidManifest.xml` (activity with `APPWIDGET_CONFIGURE`, `exported=true`)
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/widget/WidgetConfigureFlowTest.kt`

**Interfaces:**
- Consumes: Task 5 `PINNED_AQUARIUM_ID`; `updateAllWidgets`; `observeAquariums()`.

- [ ] **Step 1: Write the failing tests.**
  - `pickingAnAquarium_pinsItAndReturnsOk`: launch with `EXTRA_APPWIDGET_ID` = a widget id from `GlanceAppWidgetManager`. Tap « Nano » → `RESULT_OK` with that id, and the Glance state holds Nano's id.
  - `backWithoutPicking_returnsCanceled`.
  - `rows_areAtLeast48dpAndNamed`.

- [ ] **Step 2: Run them to see them fail.**
  Expected: FAIL.

- [ ] **Step 3: Implement the activity.**
  - Starts with `setResult(RESULT_CANCELED)`.
  - Shows a Compose screen in `AquaLogTheme`: title `widget_configure_title`, then one Carnet list row per non-archived Aquarium.
  - On pick: `updateAppWidgetState(context, glanceId) { it[PINNED_AQUARIUM_ID] = id }`, then update that widget, then `setResult(RESULT_OK, …)` and finish.

- [ ] **Step 4: Run the tests to see them pass.**
  Expected: PASS.

- [ ] **Step 5: Do a final manual pass on the emulator.**
  1. Place all three widgets.
  2. Save a Session in the app, and check that the widgets change within a few seconds.
  3. Tick a Tâche on the medium widget, and check that the home's « Aujourd’hui » updates.
  4. Pin the medium widget to the second Aquarium, then tap « Mesurer » and check that entry opens for that Aquarium.
  5. Repeat in DE dark at font scale 1.5.

- [ ] **Step 6: Run the full CI command from Global Constraints.**
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit.**
  `git commit -m "feat(widget): choose the Aquarium of a widget"`
