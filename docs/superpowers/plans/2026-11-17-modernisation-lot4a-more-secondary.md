# Modernisation lot 4a : Plus, Tâches, Rythme, Détails de Session, Compte — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the More tab and four secondary screens the immersive grammar of variant C:
- a water header under the status bar;
- a paper sheet;
- grouped cards with tinted icon squares;
- filled fields.

The screens are More, Tasks and reminders, Analysis rhythm, Session details, and the account steps.

**Architecture:**
- **Shared components.** New ones go in `ImmersiveComponents.kt`: `SettingsGroup`, `SettingsRow`, `GroupLabel`, `GlassChip`, `FilledField`, `PickerTile`. `WaterHeader` gains a leading slot, a minimum height and an optional photo. `WaterFullScreen` is the full-frame water layout used by the account steps.
- **Screens.** Each screen keeps one `LazyColumn`:
  - item 0 is the header (`overlapNext(30.dp)`);
  - then `PaperSheetTop`, then the content;
  - each calls `StatusBarOverHeader(listState)`.
  
  `AquaLogContent` and the shell stop padding these screens for the status bar.
- **New data.** The only new data read is the recurrence of each task, exposed as a map by the repository. There is no schema change.
- **Out of scope.** Lot 4b covers Habitants, adding an inhabitant, the species fiche and photo crop. Lot 5 covers onboarding and the offer. The Session's observation fields and the custom-period date fields stay as they are.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (material3 1.12.0-alpha03), Room, compose resources, kotlin.test + `runComposeUiTest` on JVM, AndroidX Compose UI tests on the Pixel 9a emulator.

**Spec:** `docs/superpowers/specs/2026-11-10-modernisation-immersive-design.md` (§3, §4.5, §4.7, §5, §6, §7 lot 4, §8.2).

**Mockup:** `docs/design/21-modernisation/prototype-modernisation.html` on branch `prototype/21-modernisation`, variant C, screens `more()`, `tasks()`, `taskNew()`, `rhythm()`, `sessionDetails()` and `account()`.

**Decisions of 17/11/2026 (Maxime):**
1. Lot 4 ships as two PRs: 4a (this plan) and 4b (Habitants, adding an inhabitant, species fiche, photo crop).
2. Each task row shows its recurrence. This needs a repository read, with no schema change.
3. **New style everywhere, current interactions everywhere, except the analysis rhythm:**
   - Rhythm rows expand in place to show their choices as chips, replacing the radio dialog.
   - The account keeps its separate steps (invitation, then method).
   - Session details keep « Modifier » and « Supprimer » as buttons at the bottom.

**Built in lots 1–3 (on `main`, `b14470b`):**
- `ImmersiveComponents.kt`: `WaterHeader(aquarium, title, subtitle, modifier, action, below)`, `PaperSheetTop`, `GlassIconButton`, `ValueTile`, `LevelMark`, `overlapNext`, `PeriodSegmented`, `HeroTextBacking`, `WaterHeaderShade`, `FloatingNavBarClearance`.
- `StatusBarOverWater.kt`: `StatusBarOverHeader(listState)`, `StatusBarOverWater(overWater)`.
- `SessionTiles.kt`: `MeasureTileRow(tiles, columns)`.
- `MaintenanceActionType.icon()`.
- `BackNavigation.kt`: `backStep` / `ScreenBackHandler`, which are unchanged by this lot.
- Test helpers in `AccessibilityTestSupport.kt`: `scrollAboveBar`, `scrollToText`, `hasLabel`, `awaitLabel`.

## Global Constraints

- Palette, fonts and `TankScene` drawing unchanged.
- **Headers.** About 170 dp; 190 dp on More (spec §4.5). They show the still `TankScene` of the shown Aquarium (`animate = false`, no inhabitants).
  - On More only, the Aquarium's photo replaces the scene when it has one (spec §8.2).
  - Headers run edge to edge under the status bar, with light icons over the water and a paper band once scrolled.
- **Contrast.** White text on water must reach 4.5:1 or more, including over a white photo and on the account screens.
- **Wording.** State is factual and never shown by colour alone.
- **Size and access.** Targets ≥ 48 dp. Layouts hold at font scale 1.5 in German. Every glass button and chip has a `contentDescription` or visible label.
- **Strings.** Every new visible string goes in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`), with terms from `docs/localization-glossary.md`. No literals (`LocalizationContractTest`), no emoji. No existing string value changes.
- **Unchanged.**
  - No change to Room schema, sync payloads or export format.
  - No change to `HomeViewModel` behaviour, except the new recurrence map in Task 3.
  - Existing ViewModel calls are reused; navigation and Back behave as on `main`.
- **No outlined fields** on the screens of this lot: `FilledField` replaces `OutlinedTextField` for the task title, the magic-link e-mail and the Aquarium editor.
- **Scrolling.** Each screen of this lot has a single vertical scrollable. Its test tag stays as on `main`: `settings-list`, `task-list`, and the new `rhythm-list` and `session-details-list`.
- **Desktop harness.** It is checked by launching it (`./gradlew :ui:run`); it has no tests.

## Review Focus

1. **Free and Pro on More.**
   - The Pro banner shows in Free only and opens `openProPaywall`.
   - Pro shows a « Pro actif » row instead.
   - Archive stays enabled only for Pro with more than one Aquarium; Free keeps its `free_one_aquarium_limit` line.

   Pinned in Task 2.
2. **Switching and adding Aquariums from More.**
   - « Changer d'aquarium » opens `AquariumPickerSheet` with the current one selected. Picking another calls `selectAquarium` and the header follows.
   - « Ajouter » calls `openCreateAquarium`, so the Pro gate stays where it is.
   - The pencil opens the editor on the current Aquarium.

   Pinned in Task 2 (JVM semantics) and `MultiAquariumFlowTest`.
3. **More's conditional blocks survive the move:** pending account deletion (both variants), portable data messages, working spinner, account error detail, sign out and delete only when signed in. Pinned in Task 2.
4. **Overdue and undated tasks.**
   - An occurrence due before today sits under « Aujourd'hui », first, with « En retard » in the alert colour plus its due date in words.
   - An occurrence with no time reads « Pas d'heure ».
   - A task whose recurrence is unknown omits it.
   - No pending task shows `no_pending_tasks`.

   Pinned in Task 3.
5. **German at 150 %.**
   - The More header chips wrap below the title.
   - The task date and time tiles stack.
   - Session detail tiles drop to 2 columns, and rhythm chips wrap.

   Pinned in Tasks 2, 3, 4 and 5, and checked in Task 7.

---

### Task 1: Shared pieces — groups, filled field, glass chip, header slots, icons, strings

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/ImmersiveComponents.kt`
- Create in `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/icon/`: `ArrowLeft.kt`, `Pencil.kt`, `Bell.kt`, `Cloud.kt`, `Download.kt`, `Upload.kt`, `Star.kt`, `CircleHelp.kt`, `MessageSquare.kt`, `Shield.kt`, `Archive.kt`, `LogOut.kt`, `Trash.kt`, `ChevronDown.kt`, `Sparkles.kt`.
  - These are Lucide icons (ISC), built with the same builder pattern as `Calendar.kt`.
  - Each is registered in `AquagendaIcons.kt`.
- Modify: `ui/src/commonMain/composeResources/values{,-en,-fr,-de,-it,-es}/strings.xml`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/ImmersiveSemanticsTest.kt`, `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/ScenePhysicsTest.kt`

**Interfaces:**
- Produces (all in `ImmersiveComponents.kt`):
  - **`WaterHeader(aquarium, title, subtitle, modifier, minHeight: Dp = 170.dp, photo: ImageBitmap? = null, leading: (@Composable () -> Unit)? = null, action, below)`**
    - `leading` sits left of the title, vertically aligned with it (back buttons).
    - With `photo`, the header draws it with `ContentScale.Crop` in place of the scene, under `WaterHeaderPhotoShade`.
    - Existing callers compile unchanged.
  - **`internal val WaterHeaderPhotoShade = Color.Black.copy(alpha = .6f)`**: a uniform shade.
  - **`GroupLabel(text: String, modifier: Modifier = Modifier)`**
    - 13 sp, SemiBold, upper-case, `onSurfaceVariant`, horizontal padding 20 dp.
    - It is a heading in semantics.
  - **`SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`**
    - A `surface` card with 22 dp corners, a soft shadow, horizontal margin 16 dp and inner horizontal padding 14 dp.
    - Children are separated by a 1 dp `outlineVariant` line. The caller does not draw dividers: the group inserts them between rows.
  - **`SettingsRow(label: String, icon: ImageVector?, modifier: Modifier = Modifier, value: String? = null, supporting: String? = null, onClick: (() -> Unit)? = null, enabled: Boolean = true, labelColor: Color = Color.Unspecified, trailing: (@Composable () -> Unit)? = null, showChevron: Boolean = onClick != null)`**
    - Minimum height 56 dp, one merged accessible unit with `Role.Button` when clickable.
    - The icon sits in a 34 dp `secondaryContainer` square with 11 dp corners and an 18 dp `primary` icon.
    - The value is in `onSurfaceVariant`, followed by a `ChevronRight` when `showChevron`.
    - At font scale > 1.3, the value moves under the label.
    - When disabled, it is at 50 % alpha and exposes `disabled()`.
  - **`GlassChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null)`**
    - Visual: 36 dp tall, `HeroTextBacking` fill, 1 dp white rim at 30 % alpha, white 13 sp SemiBold text.
    - Touch target: 48 dp tall.
    - Semantics: `Role.Button`.
  - **`FilledField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, isError: Boolean = false, singleLine: Boolean = true, valueStyle: TextStyle = <DM Sans 18 sp SemiBold>, testTag: String? = null)`**
    - A `surface` box with 18 dp corners and a 1 dp `outlineVariant` border.
    - When focused, the border is a 2 dp `primary` ring and the label turns `primary`. On error, the ring and label use `error`.
    - The label is a 12 sp SemiBold line above a `BasicTextField`.
    - In semantics, the label is the field's name, so TalkBack reads it when the field is empty.
    - Minimum height 56 dp.
  - **`PickerTile(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier)`**
    - Same look as `FilledField`, not editable, `Role.Button`.
    - Spoken as « label, value ».
  - **`WaterFullScreen(aquarium: Aquarium?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`**
    - Full-bleed still `TankScene`, or `HeroTextBacking` when `aquarium` is null.
    - The content column is anchored to the bottom and scrolls when taller than the screen.
    - It is drawn over `FullScreenContentBacking`, a vertical gradient from transparent to `Color(0xFF0A2824).copy(alpha = .88f)` over its first 48 dp, then solid. Text never sits in the fade: the column has 48 dp top padding.
    - It applies `navigationBarsPadding()` and `imePadding()`, and calls `StatusBarOverWater(true)`.
- New strings (Task 1 adds them all; later tasks use them):

| key | values / -en | -fr | -de | -it | -es |
|---|---|---|---|---|---|
| `more_pro_banner_title` | Several aquariums, all your history | Plusieurs aquariums, tout l'historique | Mehrere Aquarien, der ganze Verlauf | Più acquari, tutta la cronologia | Varios acuarios, todo el historial |
| `more_pro_banner_action` | Discover Pro | Découvrir Pro | Pro entdecken | Scopri Pro | Descubrir Pro |
| `more_section_data_account` | Data and account | Données et compte | Daten und Konto | Dati e account | Datos y cuenta |
| `task_day_tomorrow` | Tomorrow | Demain | Morgen | Domani | Mañana |
| `task_overdue` | Overdue | En retard | Überfällig | In ritardo | Atrasada |
| `task_row_detail` | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s |
| `rhythm_description` | When Aquagenda suggests testing each parameter. “Auto” follows your Aquarium's profile. | Quand Aquagenda vous propose de tester chaque paramètre. « Auto » suit le profil de votre Aquarium. | Wann Aquagenda vorschlägt, jeden Parameter zu testen. „Auto“ folgt dem Profil Ihres Aquariums. | Quando Aquagenda ti propone di testare ogni parametro. «Auto» segue il profilo del tuo Acquario. | Cuándo Aquagenda te propone analizar cada parámetro. «Auto» sigue el perfil de tu Acuario. |
| `session_details_context` | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s | %1$s · %2$s |
| `session_notes_heading` | Notes | Notes | Notizen | Note | Notas |

  - **Glossary.** Check « Aquarium », « Profil » and the register (vous / Sie / tu / tú) against the glossary and the neighbouring `rhythm_*` and `home_task_overdue` values in each catalog. Where they differ, use the catalog's term.
  - **`task_overdue`.** It is the capitalised form of `home_task_overdue` in each catalog.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ImmersiveSemanticsTest
@Test fun `settings row is one button with label and value`()      // onNode(hasText("Rythme d’analyse") and hasClickAction()) has Role.Button, its merged text contains "Chaque semaine"; one node only
@Test fun `disabled settings row is not clickable`()                 // enabled=false → assertIsNotEnabled()
@Test fun `settings group draws a line between rows only`()          // two rows → exactly one node tagged SETTINGS_DIVIDER_TAG
@Test fun `filled field is named by its label when empty`()          // value "" → onNode(hasSetTextAction()) has contentDescription or text "Titre de la tâche"; performTextInput("x") calls onValueChange("x")
@Test fun `picker tile speaks label and value`()                     // merged text "Première date d'échéance", "12 nov. 2026"; Role.Button
@Test fun `glass chip is a 48 dp button`()                           // assertHeightIsAtLeast(48.dp), Role.Button
@Test fun `water header leading slot comes before the title`()      // leading GlassIconButton "Retour" and title node: leading.left < title.left
@Test fun `water header honours its minimum height`()               // minHeight = 190.dp → root height ≥ 190.dp
// ScenePhysicsTest
@Test fun `white text keeps 4·5 to 1 over a white photo under the header shade`()   // contrast(White, Black(.6) over White) ≥ 4.5
@Test fun `white text keeps 4·5 to 1 on the full-screen backing`()                    // over the lightest TankScene colour, Color(0xFF0A2824) at .88 → ≥ 4.5
```

`SETTINGS_DIVIDER_TAG` is a new `internal const val` in `ImmersiveComponents.kt`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*ImmersiveSemanticsTest*' --tests '*ScenePhysicsTest*'`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Add the icons, the strings and the components**

- [ ] **Step 4: Run the tests, the localization contract and the full UI suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS (including `LocalizationContractTest`).

- [ ] **Step 5: Commit** — `feat(ui): settings groups, filled field, glass chip and header slots for lot 4`

---

### Task 2: More in groups under the water

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/MoreUi.kt`
  - Move `PortableDataSettings` there as `MoreScreen`.
  - Move `MoreRow`, `AccountSettingsDialog`, `InformationDialog`, `PortableDataMessage` and `AquariumEditorDialog` there as well.
  - `MoreRow` stays `internal`, because lot 4b replaces its last callers.
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (the shell passes `Modifier` to More, as it does for History)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/HomeHero.kt` (extract the context line)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/MoreSemanticsTest.kt` (new)
- Test: update in `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/` only the selectors that the new layout breaks: `MultiAquariumFlowTest`, `LocalizedAccessibilityFlowTest`, `PortableDataFlowTest`, `AccountControlSettingsTest`, `SyncStatusFlowTest`, `PrivacyFlowTest`. Assertions are never weakened.

**Interfaces:**
- Consumes (Task 1):
  - `WaterHeader(minHeight = 190.dp, photo, action, below)`;
  - `GlassChip`, `GlassIconButton`;
  - `SettingsGroup`, `SettingsRow`, `GroupLabel`;
  - `FilledField`;
  - the new icons and strings.
- Produces:
  - `@Composable internal fun aquariumContextLine(aquarium: Aquarium, inhabitantCount: Int): String`, in `HomeHero.kt`. It returns « 120 L · Planté / crevette · 16 habitants », exactly what `NameBlock` shows today, and `NameBlock` uses it.
  - `MoreScreen(state: HomeUiState, viewModel: HomeViewModel, onExportData: () -> Unit, externalLinks: ExternalLinks, rateApp: (() -> Unit)?, modifier: Modifier = Modifier)`.

**Layout** (mockup `more()`), in one `LazyColumn` tagged `settings-list`, with `StatusBarOverHeader(listState)` and bottom padding `FloatingNavBarClearance`:

0. **Header** (`overlapNext(30.dp)`):
   - `WaterHeader(aquarium, title = aquarium.name, subtitle = aquariumContextLine(…), minHeight = 190.dp, photo = <the Aquarium's photo>)`.
   - The photo is loaded as `TankHome` does: `viewModel::cachedAquariumPhoto` first, then `viewModel::aquariumPhoto` in a `LaunchedEffect(photoId)`.
   - `action`: `GlassIconButton(Pencil, stringResource(edit_aquarium), viewModel::openEditAquarium)`.
   - `below`: a `FlowRow`, start padding 20 dp, 8 dp gaps, with two glass chips:
     - `GlassChip(home_change_aquarium)`, which opens `AquariumPickerSheet` with the same arguments as `TankHome`;
     - `GlassChip(add_aquarium, icon = Plus)` → `viewModel.openCreateAquarium()`.
1. `PaperSheetTop`.
2. **Aquarium group**, without a label:
   - Habitants (Fish): the value is the sum of inhabitant counts, or none when 0; the row opens `HabitantsSheet` exactly as today.
   - Rhythm (Calendar) → `openRhythmSettings`.
   - Tasks (Bell): the value is the count of pending occurrences of this Aquarium, or none when 0; the row → `openTasks`.
3. **Pro banner**, Free only (`purchaseStatus != ACTIVE`):
   - A 112 dp card with 22 dp corners and horizontal margin 16 dp.
   - Background: a still `TankScene` with a horizontal gradient from `Color(0xFF0F4A43)` at .9 alpha to the same colour at .2.
   - Text in white: `free_plan` (12 sp, upper-case), then `more_pro_banner_title` (Fraunces 19 sp), then `more_pro_banner_action` with a `ChevronRight`.
   - It is one `Role.Button` → `openProPaywall`.
   - Pro users get no banner. Instead the « Données et compte » group starts with `SettingsRow(pro_active, Sparkles, supporting = subscription_settings_description, onClick = openProPaywall)`.
4. **`GroupLabel(more_section_data_account)`**, then a group:
   - account row (Cloud): label is the identity e-mail or `account_local_only`, value is the sync status words of today;
   - the pending-deletion rows when `pendingDeletion != null`, with the same rows and conditions as today;
   - the `portable_data_replace_explanation` text, then `PortableDataMessage` when there is a message or problem;
   - « Exporter » (Download) and « Restaurer depuis l'exportation » (Upload), both disabled while working.
5. **`GroupLabel(more_section_help)`**, then a group: feedback (MessageSquare), rate (Star, only when `rateApp != null`), privacy (Shield), support (CircleHelp).
6. **`GroupLabel(account_actions)`**, then a group:
   - signed in without pending deletion: sign out (LogOut) and delete account (Trash, `error` colour);
   - otherwise the `account_local_only_description` line;
   - then « Archiver » (Archive), enabled only for Pro with more than one Aquarium, otherwise with `free_one_aquarium_limit` as supporting text.
7. **Footer:** spinner while working, and the `localizedSafeDetail()` error, as today.

The **Aquarium editor** keeps its `AlertDialog`, but its name and volume become `FilledField`s. The two unit buttons become a two-option `PeriodSegmented` (`unit_liters`, `unit_us_gallons`), with the selected one following `editor.unit`.

- [ ] **Step 1: Write the failing JVM tests** (`MoreSemanticsTest`: render `MoreScreen` with a fake `HomeUiState`)

  Follow the existing fake-state pattern used by `TimelineSemanticsTest`. If `MoreScreen` needs a `HomeViewModel`, split a stateless `MoreContent(state, callbacks…)` out of it and test that.

```kotlin
@Test fun `free shows the Pro banner and opens the offer`()          // click hasText("Découvrir Pro") → onOpenPaywall called; no node "Pro actif"
@Test fun `pro shows Pro actif instead of the banner`()             // no node "Découvrir Pro"; "Pro actif" present
@Test fun `archive is disabled in free with its reason`()           // "Archiver l'aquarium" assertIsNotEnabled; "free_one_aquarium_limit" text present
@Test fun `change aquarium chip opens the picker`()                 // click "Changer d’aquarium" → node with Role.RadioButton selected = current aquarium name
@Test fun `pending deletion shows its retry rows`()                 // pendingDeletion set, showDeletionReauthenticationAction=false → "retry_account_deletion" row present
@Test fun `signed out shows the local only line, no sign out`()     // identity null → no "Se déconnecter"
@Test fun `german at 150 percent keeps the header chips whole`()    // de locale, fontScale 1.5 → "Aquarium wechseln" text node's line count == 1 or it is fully displayed (assertIsDisplayed after scroll)
```

  Use each catalog's real values. Read them from `values-fr` / `values-de`; do not invent them.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*MoreSemanticsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement `aquariumContextLine`, `MoreScreen` and the editor fields, and wire the shell**

  The shell passes `Modifier`, not `tabModifier`, to More. Delete `tabModifier` if nothing else uses it.

- [ ] **Step 4: Run the JVM suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS.

- [ ] **Step 5: Run the device tests that touch More, fixing only broken selectors**

Run: `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.MultiAquariumFlowTest,com.maximerollin.aqualog.LocalizedAccessibilityFlowTest,com.maximerollin.aqualog.PortableDataFlowTest,com.maximerollin.aqualog.AccountControlSettingsTest,com.maximerollin.aqualog.SyncStatusFlowTest,com.maximerollin.aqualog.PrivacyFlowTest,com.maximerollin.aqualog.BackNavigationFlowTest`
Expected: PASS.
- A test that picked an Aquarium from More's inline list now opens « Changer d'aquarium » first.
- A row hidden under the floating bar is reached with `scrollAboveBar("settings-list", …)`.

- [ ] **Step 6: Commit** — `feat(more): the More tab in groups under the Aquarium's water`

---

### Task 3: Tasks by day, with their recurrence

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/AquaLogDatabase.kt` (a `TaskDao` query)
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/AquariumRepository.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeViewModel.kt` (`TaskUiState` field and its collector)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TaskUi.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt`. The `TaskScreen` call passes `aquarium` and the unpadded `modifier`, because the header draws under the status bar.
- Test: the shared repository test that already covers tasks (find it with `grep -rl createTask shared/src/*Test`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/TaskDaysTest.kt` (new), `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/TaskSemanticsTest.kt` (new)
- Test: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/TaskFlowTest.kt` (selectors only)

**Interfaces:**
- Produces:
  - **DAO:** `@Query("SELECT * FROM tasks") abstract fun observeTasks(): Flow<List<TaskEntity>>` in `TaskDao`.
  - **Repository:** `fun observeTaskRecurrences(): Flow<Map<String, TaskRecurrence>> = flowOf(emptyMap())` in `AquariumRepository`, keyed by task id.
    - It has a default body, so fakes compile.
    - `RoomAquariumRepository` overrides it from `observeTasks()`.
  - **UI state:** `TaskUiState.recurrenceByTaskId: Map<String, TaskRecurrence> = emptyMap()`, collected in `HomeViewModel` next to `observePendingTaskOccurrences()`.
  - **Day groups:** `internal data class TaskDayGroup(val date: TaskDate, val occurrences: List<TaskOccurrence>)` and `internal fun taskDayGroups(occurrences: List<TaskOccurrence>, today: TaskDate): List<TaskDayGroup>`, in `TaskUi.kt`.
    - The group key is `max(dueDate, today)`, so an overdue occurrence joins today's group.
    - Groups are in ascending date order.
    - Within a group, overdue occurrences come first, by due date, then by `minuteOfDay` with nulls last. The input order breaks ties.
  - **Screen:** `TaskScreen(state: TaskUiState, viewModel: HomeViewModel, aquarium: Aquarium, modifier: Modifier = Modifier)`, replacing the `aquariumName` parameter.

**Layout** (mockup `tasks()` / `taskNew()`), in one `LazyColumn` tagged `task-list`, with `StatusBarOverHeader`:

0. **Header:** `WaterHeader(aquarium, tasks_title, aquarium.name, …)`.
   - `leading`: `GlassIconButton(ArrowLeft, stringResource(back), viewModel::closeTasks, Modifier.testTag("tasks-back"))`.
   - `action`: `GlassIconButton(Plus, stringResource(new_task)) { creating = true }`.
   - The section's « Nouvelle tâche » text action goes away; the header « + » replaces it.
1. `PaperSheetTop`.
2. **Reminder banner**, with the same conditions and texts as today.
   - A 20 dp-corner card in `levelContainerColor(ReadingLevel.WATCH)` (or the attention container lot 1 uses).
   - It has a `Bell` icon. The action is a `TextButton`, at least 48 dp.
3. **Days.** For each `TaskDayGroup`:
   - a `GroupLabel`: `home_today` for today, `task_day_tomorrow` for tomorrow, otherwise `formatter.date(y, m, d)`;
   - a `SettingsGroup` of `TaskRow`s.

   With no occurrence, the screen shows `no_pending_tasks`.

A **`TaskRow`** reads from left to right:
- **Round check.** A 26 dp circle in a 48 dp target. It is `toggleable` with `Role.Checkbox`, and keeps today's `contentDescription` and double-tap guard.
- **Icon square.** It shows `maintenanceActionType?.icon() ?: Bell`.
- **Title and detail line.** The title keeps `LinkedTaskTitle`. The detail line is nested `task_row_detail`:
  - overdue: `task_overdue` in `colorScheme.error`, then the due date, then the recurrence;
  - otherwise: the time or `task_no_time`, then the recurrence.
  
  When the recurrence is unknown, it is omitted. The recurrence label is today's `TaskRecurrence.label()`.
- **Menu.** The ⋮ menu is unchanged: postpone and ignore.

**New-task sheet:** still a `ModalBottomSheet`, with the same fields, order and behaviour.
- The title is a Fraunces 24 sp heading (`new_task`) with a close `TextButton`.
- The task title is a `FilledField`.
- Date and time are two `PickerTile`s side by side. Below font scale 1.3 they share the width; above it, they stack. They open today's date and time pickers.
- `clear_task_time` stays.
- Recurrence chips wrap in a `FlowRow`.
- Linked-action chips use `leadingIcon = type.icon()`.
- The validation error and the save button are unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
// shared repository test
@Test fun `task recurrences are observed by task id`()   // createTask(weekly) → observeTaskRecurrences().first()[task.id] == WEEKLY
// TaskDaysTest
@Test fun `overdue joins today first`()                  // today 2026-11-17; due 11-15 09:00, 11-17 18:00, 11-17 null → one group 11-17: [11-15, 11-17 18:00, 11-17 null]
@Test fun `later days keep their own group in order`()   // due 11-18, 11-20, 11-18 → groups [11-18 (2), 11-20 (1)]
@Test fun `no occurrence no group`()                     // emptyList → emptyList
// TaskSemanticsTest (stateless content or fake state, as in Task 2)
@Test fun `overdue row says so with its date and recurrence`()  // merged text contains "En retard", the formatted 15 nov. date, "Hebdomadaire"
@Test fun `unknown recurrence is omitted`()                     // empty map → detail is time only, no dangling " · "
@Test fun `check is a 48 dp checkbox named after the task`()     // Role.Checkbox, contentDescription "Terminer, Changement d'eau" (real catalog value), size ≥ 48 dp
@Test fun `header plus opens the new task sheet`()               // click contentDescription "Nouvelle tâche" → node "Enregistrer la tâche" exists
@Test fun `date and time tiles stack at 150 percent`()            // fontScale 1.5 → time tile top ≥ date tile bottom
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:jvmTest :ui:jvmTest --tests '*TaskDaysTest*' --tests '*TaskSemanticsTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement the query, the repository flow, the state field, `taskDayGroups` and the screen**

- [ ] **Step 4: Run the suites**

Run: `./gradlew :shared:jvmTest :ui:jvmTest`
Expected: PASS.

- [ ] **Step 5: Run the task device tests, fixing only broken selectors**

Run: `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.TaskFlowTest,com.maximerollin.aqualog.TankHomeDueTestsFlowTest`
Expected: PASS. A test that tapped the « Nouvelle tâche » section action now taps the header « + » by its content description.

- [ ] **Step 6: Commit** — `feat(tasks): tasks grouped by day with their recurrence, under the water`

---

### Task 4: Analysis rhythm rows that expand in place

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/RhythmSettingsScreen.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (the unpadded `modifier` for Rhythm)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/RhythmSemanticsTest.kt` (new)

**Interfaces:**
- Consumes: `WaterHeader(leading)`, `SettingsGroup`, `GlassIconButton`, `ChevronDown` / `ChevronRight`, `rhythm_description`, `trends_row_expanded`, `trends_row_collapsed`.
- Produces: `RhythmSettingsScreen(setup, viewModel, modifier)`. The signature is unchanged; it gets a `LazyColumn` tagged `rhythm-list`.

**Layout** (mockup `rhythm()`):
- **Header and intro.**
  - Header: `WaterHeader(aquarium, rhythm_title, aquarium.name, leading = back glass button → closeRhythmSettings)`.
  - Then `PaperSheetTop` and `rhythm_description` in `onSurfaceVariant`.
  - Then one `SettingsGroup` with one row per active parameter, in position order.
- **A row** (minimum 56 dp):
  - the parameter label;
  - a chip showing the current `rhythmLabel`, `primary` filled when expanded;
  - `ChevronRight` when collapsed, `ChevronDown` when expanded.
  
  The whole row is one `Role.Button` with `stateDescription` = `trends_row_expanded` / `trends_row_collapsed`.
- **Expansion.** At most one row is expanded (`rememberSaveable` id). Tapping the expanded row collapses it.
- **Expanded choices.** The row shows a `FlowRow` of the 8 `RHYTHM_CHOICES` inside a `selectableGroup()`:
  - each choice is a chip at least 48 dp tall, `selectable(selected, role = Role.RadioButton)`;
  - choosing calls `viewModel.updateAnalysisInterval(definition.id, days)`, then collapses the row.
- The `AlertDialog` goes away.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `a row expands to its choices and collapses after a choice`()   // click "pH" row → stateDescription "Développé", 8 RadioButton nodes; click "3 jours" → update called with 3, row "Réduit", no RadioButton nodes
@Test fun `only one row is expanded`()                                     // expand pH then GH → only GH's choices exist
@Test fun `the current choice is selected`()                               // analysisIntervalDays = 7 → "7 jours" assertIsSelected
@Test fun `choices wrap at 150 percent in German`()                        // de, 1.5 → every chip assertIsDisplayed within the root width (no horizontal scroll)
```

  The update is observed through a fake `HomeViewModel`/callback as in the other semantics tests. If the screen needs the real ViewModel, split a stateless `RhythmContent(setup, onUpdate, onClose)`.

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :ui:jvmTest --tests '*RhythmSemanticsTest*'`, Expected: FAIL.

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run `./gradlew :ui:jvmTest`** — Expected: PASS.

- [ ] **Step 5: Commit** — `feat(rhythm): rhythm rows expand in place to their choices`

---

### Task 5: Session details in tiles and cards

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/SessionDetailsUi.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (the unpadded `modifier` for Session details)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/SessionDetailsSemanticsTest.kt` (new)
- Test: device tests that open details (`TimelineFlowTest`, `RapidSessionFlowTest`, `SessionReliabilityFlowTest`, `BackNavigationFlowTest`): selectors only.

**Interfaces:**
- Consumes: `WaterHeader(leading)`, `ValueTile`, `MeasureTileRow`, `SettingsGroup`, `SettingsRow`, `GroupLabel`, `session_details_context`, `session_notes_heading`, the existing `maintenance_heading`.
- Produces: `SessionDetailsScreen(...)`. The signature is unchanged; it gets a `LazyColumn` tagged `session-details-list`.

**Layout** (mockup `sessionDetails()`, with the buttons kept at the bottom):
- **Header.**
  - `WaterHeader(setup.aquarium, title = formatter.shortDateTime(occurredAt), subtitle = session_details_context(session_details, aquarium.name))`.
  - `leading`: a back glass button. Its `contentDescription` is `back_to_timeline` when `returnToTimeline`, else `back`, and it calls `closeSessionDetails`.
- **Readings.** A grid of `ValueTile`s, each built as `HomeValue(definition, measurement.value, occurredAt, readingLevel(definition, value, profile), isOlderThanLatestReading = false)`.
  - The grid is laid out with `MeasureTileRow`: 3 columns, or 2 when `fontScale > 1.3`.
  - It sits in 16 dp margins.
- **Maintenance.**
  - `GroupLabel(maintenance_heading)`, then a group.
  - One `SettingsRow` per action: `type.icon()`, label = `type.label()`, value = the detail of today (quantity, unit, product). The row is not clickable.
- **Notes.**
  - `GroupLabel(session_notes_heading)`, then a group with one row per event: `NotepadText` icon, a small `observation` / `incident` label above the note text, not clickable.
- **Buttons.** « Modifier la Session » (`Button`, primary, full width, at least 48 dp) and « Supprimer la Session » (`OutlinedButton`, content and border in `colorScheme.error`).
  - The deletion `AlertDialog` is unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `readings are tiles spoken with their state`()          // pH 7.8 above range → a node whose text contains "pH", "7,8", "Au-dessus de la plage"; LEVEL_MARK_TAG present
@Test fun `three columns, two at 150 percent`()                    // 6 readings: at 1.0 the 4th tile's top > 1st tile's bottom and 3 tiles share row 1; at 1.5 two per row
@Test fun `back says where it returns`()                           // returnToTimeline = true → contentDescription "Retour à la chronologie" (real value)
@Test fun `edit and delete stay as buttons`()                      // "Modifier la Session" and "Supprimer la Session" have click actions; delete click → requestSessionDeletion
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :ui:jvmTest --tests '*SessionDetailsSemanticsTest*'`, Expected: FAIL.

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run `./gradlew :ui:jvmTest` and the device tests listed above** — Expected: PASS.

- [ ] **Step 5: Commit** — `feat(session): Session details in tiles and cards under the water`

---

### Task 6: Account steps on the water

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt`:
  - `AccountScreen` and `AccountDeletionCleanupScreen`;
  - the calls in `AquaLogContent` pass `state.setup?.aquarium` and the unpadded `modifier`.
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/AccountScreenSemanticsTest.kt` (new)
- Test: `AccountControlSettingsTest`, `BackNavigationFlowTest`, `LocalizedAccessibilityFlowTest`: selectors only.

**Interfaces:**
- Consumes: `WaterFullScreen`, `FilledField`, `Cloud`.
- Produces:
  - `AccountScreen(state: AccountUiState, aquarium: Aquarium?, viewModel: HomeViewModel, modifier: Modifier)`;
  - `AccountDeletionCleanupScreen(state, aquarium: Aquarium?, viewModel, modifier)`.

**Layout** (mockup `account()`, keeping every step):
- **Shared frame.** Every step uses `WaterFullScreen(aquarium)`. The bottom column holds:
  - a 56 dp glass square with the `Cloud` icon (decorative, no semantics);
  - the step's title in Fraunces 30 sp white, a heading;
  - its description in white at 85 %;
  - then its buttons.
  
  Titles, descriptions, buttons and their actions are exactly today's, step by step. The wording is unchanged.
- **Buttons.**
  - Primary actions are white filled `Button`s with `Color(0xFF14675E)` text.
  - Secondary actions (« Continuer avec l'e-mail ») are glass `OutlinedButton`s: white text, white rim at 35 %.
  - Text buttons (« Pas maintenant », « Annuler », « Retour », « Retour à l'aquarium ») are white.
  - All are at least 48 dp tall and full width.
- **Steps with special content.**
  - `MAGIC_EMAIL`: a `FilledField(email_address, keyboardType = Email)`.
  - `WORKING`: a white `CircularProgressIndicator` centred in the column.
  - `ERROR`: the error message in white, never red on water.
- **Cleanup screen.** `AccountDeletionCleanupScreen` uses the same frame, with its error detail in white.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `invitation keeps its two actions`()                   // INVITATION → "Créer un compte" button and "Pas maintenant"; click create → openAccountMethods (callback/fake)
@Test fun `methods keep google, email and cancel`()              // METHODS → three click actions with today's labels
@Test fun `magic email field is named and typed`()               // MAGIC_EMAIL → set-text node named "Adresse e-mail" (real value); input → updateAccountEmail
@Test fun `no aquarium still draws the frame`()                   // aquarium = null, INVITATION → title displayed
@Test fun `content scrolls at 150 percent in German`()            // de, 1.5, METHODS → "Abbrechen" (real value) reachable with performScrollTo and displayed
```

  If `AccountScreen` needs the real ViewModel, split a stateless `AccountContent(state, aquarium, callbacks…)`.

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :ui:jvmTest --tests '*AccountScreenSemanticsTest*'`, Expected: FAIL.

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run `./gradlew :ui:jvmTest` and `AccountControlSettingsTest`, `BackNavigationFlowTest` on the device** — Expected: PASS.

- [ ] **Step 5: Commit** — `feat(account): account steps full frame on the water`

---

### Task 7: Whole-branch checks and captures

**Files:**
- Create: `docs/design/21-modernisation/lot4a-captures/` (PNG)

- [ ] **Step 1: Full suites**

  Run:
  - `./gradlew :shared:jvmTest :ui:jvmTest`;
  - `./gradlew :androidApp:connectedDebugAndroidTest`, the whole device suite on emulator-5554.

  Expected: PASS. Any failure is fixed in the task's own code, never by weakening an assertion.

- [ ] **Step 2: Desktop harness**

  Run `./gradlew :ui:run`, open More, Tasks, Rhythm and the details of a Session, then close it.
  Expected: no crash.

- [ ] **Step 3: Captures**

  On the Pixel 9a emulator, take captures of `main` (`b14470b`) as « before » and this branch as « after ».
  - **Screens:** More (Free, then Pro), Tasks with an overdue weekly task, the new-task sheet, Rhythm with pH expanded, Session details with one out-of-range value, the account invitation, the account methods.
  - **Before captures:** FR light.
  - **After captures:** FR light and FR dark for every screen, plus DE at 150 % for More, Tasks, Rhythm and Session details.
  - **File names:** `<screen>-<before|after>-<fr-light|fr-dark|de-150>.png`.
  - **Check every after capture:**
    - white text on water is readable, including More over a photo;
    - no label is cut mid-word;
    - nothing hides under the floating bar on More.

- [ ] **Step 4: Commit** — `docs: lot 4a before/after captures`
