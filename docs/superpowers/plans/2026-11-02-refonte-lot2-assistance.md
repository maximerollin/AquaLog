# Refonte lot 2 : assistance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Aquagenda say what to do: orange/red values at entry, a check of suspicious values before saving, rule-based « pistes » when a value is out of range, a per-parameter analysis rhythm with due tests and reminders, Tâches linked to a maintenance action, a weekly series and a Sunday summary.

**Architecture:** All judgement is pure, rule-based Kotlin in `:shared` (`assistance/` package: levels, checks, pistes, rhythm, series, summary), unit-tested on the JVM with exact values. Persistence changes are one Room migration (v14 → v15) with two synced columns and one local-only table; the UI calls the pure functions from `HomeViewModel` / composables like lot 1 did with `HomeOverview.kt`. Android alarms reuse the existing `AlarmManager` reminder pattern.

**Tech Stack:** Kotlin 2.4.20, Room 3 (`androidx.room3`, schema export), Compose Multiplatform 1.12.2, Supabase SQL migrations + pgTAP tests, AlarmManager receivers, kotlin.test, AndroidX Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-10-30-refonte-aquagenda-design.md` §4.3 (assistance), §4.7 (Free/Pro), §5 (lot 2), §7 (decisions). Lot 1 (merged, d30ebfa) provides `HomeOverview.kt`, `TankHome.kt`, `ReadingValue`, attention colours.

## Global Constraints

- Rules are written by us, **no AI**; deterministic and offline (spec §4.3).
- Tone: suggestion, never diagnosis. App copy uses **« vous »** in French (existing catalog). Products are **types, never brands**. Every pistes block ends with the disclaimer « Ce sont des pistes, pas un diagnostic : en cas de doute, demandez conseil en magasin spécialisé. »
- Verification never blocks saving (spec §4.3.2).
- Lot 2 levels: **ATTENTION** (outside the Plage indicative, existing orange `#B87A10` mark / `#8A5A00` text) and **ALERT** (beyond the per-parameter alert threshold, new red). One range per parameter stays; default ranges unchanged (spec §7).
- Rhythm defaults are suggestions, editable per Aquarium; one Mesure resets every due date it covers (spec §4.3.4).
- Series: one per account, in weeks, computed from history, never stored; a week where nothing was due never breaks it (spec §7).
- Sunday summary: one notification for all Aquariums; the home card shows the displayed Aquarium; detailed summary (value changes, next week) is **Pro**, the rest is free (spec §4.7).
- Tâches stay **local-only** (they are not synced today); new task fields go into Room and the portable export, not Supabase.
- `analysisIntervalDays` follows the account (synced + exported); old exports and old Supabase rows stay valid (defaults).
- Every visible string in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`) of `ui/src/commonMain/composeResources` (and `androidApp/src/main/res` for notification texts); no `Text("…")` literals; glossary of `docs/localization-glossary.md`.
- Touch targets ≥ 48 dp; WCAG 4.5:1 text / 3:1 non-text in light and dark; German at font scale 1.5; colour is never the only signal.
- No new product telemetry events (reuse `TaskUsed`).

## Rule tables (binding values)

| Parameter | Alert below | Alert above | Plausible min–max | Jump (abs, if previous ≤ 14 days) | Established interval (days) | Cycling interval | Planted/shrimp interval |
|---|---|---|---|---|---|---|---|
| TEMPERATURE | 18 | 31 | 0–45 | 4 | — | — | — |
| PH | 6.0 | 8.5 | 0–14 | 1.0 | 7 | 7 | 7 |
| AMMONIA | — | 0.5 | 0–10 | 1.0 | — (follow-up only) | 2 | — |
| NITRITE | — | 0.5 | 0–10 | 1.0 | 14 | 2 | 14 |
| NITRATE | — | 80 | 0–500 | 40 | 7 | 2 | 7 |
| GH | 2 | 20 | 0–50 | 6 | 30 | 30 | 7 |
| KH | 1 | 15 | 0–50 | 4 | 30 | 30 | 30 |
| TDS | — | 600 | 0–3000 | 200 | — | — | 7 |
| PHOSPHATE | — | 2 | 0–20 | 1.5 | — | — | — |
| SALINITY | — | — | 0–50 | 5 | — | — | — |

« — » = none. Follow-up after an out-of-range Session: active NITRITE and AMMONIA due at T+1 day and T+3 days (T = that Session's day). Reminder time 09:00 local; Sunday summary 19:00 local.

Pistes (French copy verbatim; en given; de/it/es written by the implementer with glossary terms):

| Case | Pistes (in order) | Retest in |
|---|---|---|
| NITRITE high | « Changez 30 % de l'eau. » · « Nourrissez moins pendant 2 jours. » · « Ajoutez des bactéries de démarrage. » | 1 day |
| AMMONIA high | « Changez 30 % de l'eau. » · « Nourrissez moins pendant 2 jours. » · « Ajoutez un conditionneur d'eau qui neutralise l'ammoniac. » | 1 day |
| NITRATE high | « Changez 30 à 50 % de l'eau. » · « Vérifiez la quantité de nourriture. » · « Ajoutez des plantes à croissance rapide. » | 3 days |
| PH low | « Vérifiez le KH : un KH bas fait chuter le pH. » · « Changez une partie de l'eau. » · « Ajoutez un tampon pour remonter le KH. » | 2 days |
| PH high | « Vérifiez l'eau du robinet et les décors calcaires. » · « Changez une partie de l'eau avec une eau plus douce. » | 2 days |
| TEMPERATURE high | « Vérifiez le réglage du chauffage. » · « Aérez la surface ou éteignez l'éclairage quelques heures. » | 1 day |
| TEMPERATURE low | « Vérifiez que le chauffage fonctionne. » · « Adaptez sa puissance au volume du bac. » | 1 day |
| KH low | « Ajoutez un tampon pour remonter le KH. » · « Changez une partie de l'eau. » | 3 days |
| GH low | « Ajoutez un reminéralisant. » · « Changez une partie de l'eau. » | 3 days |
| GH high | « Coupez l'eau avec de l'eau osmosée. » | 7 days |
| TDS high | « Changez une partie de l'eau avec une eau plus pure. » | 3 days |
| PHOSPHATE high | « Changez une partie de l'eau. » · « Réduisez la nourriture. » · « Ajoutez un anti-phosphates. » | 3 days |
| any other | « Refaites un test pour confirmer. » · « Changez une partie de l'eau. » | 2 days |

Context rules: a pending WATER_CHANGE linked Tâche overdue → first piste « Votre changement d'eau est en retard : commencez par là. »; profile CYCLING with AMMONIA/NITRITE high → replace the pistes with « Pendant le cyclage, de l'ammoniac et des nitrites sont normaux. Attendez qu'ils redescendent à 0 avant d'ajouter des poissons. » (retest 2 days).

## Review Focus

1. **Decimal slip vs real extreme** — « 72 » for pH must suggest 7.2; « 9 » for pH is plausible (no suggestion, just ATTENTION/ALERT). Pinned in Task 1.
2. **Nothing due ≠ broken series** — a week with no rhythm item and no Tâche due neither breaks nor counts; a reading one day after the week still counts. Pinned in Task 11.
3. **Old data** — v14 databases, old portable exports without the new fields, and Supabase rows without `analysis_interval_days` all load with defaults. Pinned in Task 4.
4. **Linked Tâche resolved twice** — ticking a linked Tâche on the home and saving the same action in a Session the same day must not complete the next occurrence. Pinned in Task 5.
5. **Notifications without permission / in the background** — reminders and the Sunday notification never crash or show Pro-only detail when POST_NOTIFICATIONS is denied or Pro is unknown. Pinned in Tasks 9 and 12.

---

### Task 1: Assistance rules — levels and checks (pure)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/assistance/AssistanceRules.kt`
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/RapidSession.kt` (`RapidSessionContext`, `loadRapidSessionContext` in `AquariumRepository.kt` ~L227)
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/assistance/AssistanceRulesTest.kt`

**Interfaces:**
- Produces:
  - `enum class ReadingLevel { OK, ATTENTION, ALERT }`
  - `data class ParameterRule(val alertBelow: Double?, val alertAbove: Double?, val plausible: ClosedFloatingPointRange<Double>, val jump: Double)` and `fun ruleFor(parameter: BuiltInParameter): ParameterRule` (values from the Rule tables).
  - `fun readingLevel(definition: ParameterDefinition, value: Double): ReadingLevel` — ALERT if beyond the rule thresholds, else ATTENTION if `isOutsideIndicativeRange`, else OK.
  - `sealed interface MeasurementConcern { data class Implausible(val suggestion: Double?) ; data class BigJump(val previous: Double, val previousAtEpochMillis: Long) }`
  - `fun checkMeasurement(definition: ParameterDefinition, value: Double, previous: Double?, previousAtEpochMillis: Long?, nowEpochMillis: Long): MeasurementConcern?` — Implausible when outside `plausible`, with `suggestion` = first of value/10, value/100 that is plausible **and** inside the indicative range (else null); BigJump when `|value − previous| > jump` and the previous reading is ≤ 14 days old; Implausible wins.
  - `RapidSessionContext.lastMeasurementTimes: Map<String, Long>` (parameterDefinitionId → occurredAt of the newest reading), filled by `loadRapidSessionContext`.

- [ ] **Step 1: Write the failing tests** in `AssistanceRulesTest`: `pH 7.2 is OK, 7.8 ATTENTION, 8.6 ALERT`; `nitrite 0.25 ATTENTION, 0.5 ALERT`; `temperature without alert threshold side stays ATTENTION` (e.g. 29 → ATTENTION); `TDS 900 with null range is ALERT`; `pH 72 is implausible with suggestion 7.2`; `pH 9 is plausible`; `nitrate 2000 is implausible with suggestion 20` (2000/10 = 200 is plausible but outside 0–40, so value/100); `temperature 250 suggests 25.0`; `nitrate 80 after 20 three days ago is a big jump`; `nitrate 80 after 20 twenty days ago is not`; `implausible beats big jump`. Add a `RapidSessionRepositoryContractTest` case: `context exposes the time of the newest reading per parameter`.
- [ ] **Step 2:** `./gradlew :shared:jvmTest --tests '*AssistanceRulesTest*'` — FAIL (unresolved).
- [ ] **Step 3:** Implement `AssistanceRules.kt` and `lastMeasurementTimes`.
- [ ] **Step 4:** `./gradlew :shared:jvmTest :ui:jvmTest` — PASS.
- [ ] **Step 5: Commit** `feat(shared): assistance levels and measurement checks`.

### Task 2: Red level in the UI

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/AquaLogTheme.kt` (L46-55), `ui/.../ui/theme/CarnetComponents.kt` (`ReadingValue` L103-163)
- Modify: `ui/.../HomeOverview.kt` (`HomeValue.isOutsideRange` → `level`; `outsideRangeCount` counts level ≠ OK), `ui/.../home/TankHome.kt` (status dot), `ui/.../RapidSessionUi.kt` (row colours L202-297), `ui/.../TimelineUi.kt`, `ui/.../TrendSparkline.kt`, `ui/.../SessionDetailsUi.kt` (callers of `ReadingValue`)
- Modify: 6 catalogs
- Test: `ui/src/jvmTest/.../ui/theme/CarnetColorTest.kt`, `ui/src/jvmTest/.../HomeOverviewTest.kt`

**Interfaces:**
- Consumes: `ReadingLevel`, `readingLevel` (Task 1).
- Produces: `AlertText`, `AlertMark` (+ dark variants) and `@Composable fun levelTextColor(level: ReadingLevel): Color?` / `levelMarkColor(level: ReadingLevel): Color?` (null for OK); `ReadingValue(..., level: ReadingLevel = ReadingLevel.OK, ...)` replacing `attention: Boolean`; `HomeValue.level: ReadingLevel`; status dot red when any ALERT.
- New string `reading_alert_description` (en « far outside your indicative range », fr « très en dehors de votre plage indicative »).

- [ ] **Step 1: Write the failing tests:** `CarnetColorTest` — alert text ≥ 4.5:1 and alert mark ≥ 3:1 on the background in both schemes; `HomeOverviewTest` — `nitrite 0.6 is ALERT and counts as out of range`.
- [ ] **Step 2:** `./gradlew :ui:jvmTest` — FAIL.
- [ ] **Step 3:** Implement colours (pick reds from the palette family that pass the test), `ReadingValue(level)`, callers, entry row colours (underline + value use `levelMarkColor`/`levelTextColor`; the existing hint stays; ALERT adds `reading_alert_description` to `stateDescription`).
- [ ] **Step 4:** `./gradlew :ui:jvmTest :shared:jvmTest` PASS; emulator (animations off, see lot-1 context): `RapidSessionFlowTest`, `TimelineFlowTest`, `TrendsFlowTest`, `LocalizedAccessibilityFlowTest` PASS.
- [ ] **Step 5: Commit** `feat(ui): red alert level for readings`.

### Task 3: Check suspicious values before saving

**Files:**
- Modify: `ui/.../RapidSessionUi.kt`, `ui/.../HomeViewModel.kt` (`RapidSessionUiState` L369-395, `saveRapidSession` L1570-1630)
- Create: `ui/.../SaveCheckDialog.kt`
- Modify: 6 catalogs
- Test: `androidApp/src/androidTest/.../RapidSessionFlowTest.kt`

**Interfaces:**
- Consumes: `checkMeasurement`, `lastMeasurementTimes` (Task 1).
- Produces: `RapidSessionUiState.pendingConcerns: Map<String, MeasurementConcern>`; `fun saveRapidSession(confirmed: Boolean = false)` — when `confirmed` is false and any concern exists, it fills `pendingConcerns` and does not save; `fun applySuggestion(parameterDefinitionId: String)`; `fun dismissConcerns()`.
- Dialog: one line per concern — Implausible: « pH 72 ? Vous vouliez dire 7,2 ? » with a « Corriger » button when there is a suggestion; BigJump: « Nitrates à 80 alors que vous étiez à 20 il y a 3 jours, c'est bien ça ? »; buttons « Enregistrer quand même » (saves with `confirmed = true`) and « Revenir » (dismiss). New strings: `check_title`, `check_implausible`, `check_implausible_suggestion`, `check_big_jump`, `check_fix`, `check_save_anyway`, `check_back` (en/fr as above; de/it/es translated).

- [ ] **Step 1: Write the failing tests** in `RapidSessionFlowTest`: `implausiblePh_offersTheDecimalFix` (type 72 → Save → dialog → « Corriger » → field shows 7.2, nothing saved yet); `bigJump_canBeSavedAnyway` (previous nitrate 20, type 80 → Save → dialog → « Enregistrer quand même » → Session saved with 80).
- [ ] **Step 2:** Run the class on the emulator — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `RapidSessionFlowTest`, `SessionReliabilityFlowTest`, `LocalizedAccessibilityFlowTest` PASS; `:ui:jvmTest` PASS.
- [ ] **Step 5: Commit** `feat(ui): check suspicious values before saving`.

### Task 4: Schema v15 — rhythm column, linked task columns, dismissals

**Files:**
- Modify: `shared/.../AquaLogDatabase.kt` (entities L42-53, L118-145; version L1055; `MIGRATION_14_15`; `addMigrations` L1079-1093; `removeAccountAndLocalCopy` L638-649; `PortableDataDao.replaceAll` L755-777)
- Create: `shared/schemas/com.maximerollin.aqualog.shared.AquaLogDatabase/15.json` (generated)
- Modify: `shared/.../Onboarding.kt` (`ParameterDefinition` + `analysisIntervalDays: Int? = null`), `shared/.../Tasks.kt` (`AquariumTask`, `TaskOccurrence`, `TaskInput` + `maintenanceActionType: MaintenanceActionType? = null`), mappers in `AquariumRepository.kt` (L782-825)
- Modify: `shared/.../PortableData.kt` (portable parameter definition, task, occurrence fields with defaults; validation L350-374)
- Modify: sync payload for parameter definitions: `androidApp/.../SyncInfrastructure.kt` (`toRemoteJson` L331-356, `pullVersioned` L138-216 with `optInt`/nullable read), `applyRemoteSnapshot` mapping
- Create: `supabase/migrations/20261102100000_parameter_analysis_interval.sql`, update `supabase/tests/database/sync_rls.test.sql` (or a new `analysis_interval.test.sql`)
- Test: `HistoricalMigrationQualificationTest.kt` (`versions()` → `3..14`, fixture rows), `PortableDataServiceTest.kt`, `SyncCoordinatorTest.kt` if payload assertions exist

**Interfaces:**
- Produces:
  - Room: `parameter_definitions.analysisIntervalDays INTEGER NULL`; `tasks.maintenanceActionType TEXT NULL`; `task_occurrences.maintenanceActionType TEXT NULL`; local-only table `assistant_dismissals(aquariumId TEXT, kind TEXT, sessionId TEXT, PRIMARY KEY(aquariumId, kind))` + `AssistantDao` with `dismiss(aquariumId, kind, sessionId)` / `observeDismissals(aquariumId): Flow<Map<String, String>>`.
  - `AquariumRepository.updateAnalysisInterval(parameterDefinitionId: String, days: Int?)` (syncs as a parameter_definition upsert).
  - Supabase: `parameter_definitions.analysis_interval_days integer null`, applied on `parameter_definition` upserts by wrapping the 6-argument `apply_sync_operation` exactly like `20261015100000_lifetime_pro_multi_aquarium.sql` L6-45 did for `is_archived`.
  - Initial copy unchanged (precedent: `is_archived`); note it in the migration comment.

- [ ] **Step 1: Write the failing tests:** migration test fixtures for v14 → v15 (rows survive, new columns null, dismissals table exists); `PortableDataServiceTest` — `v1 file without the new fields still imports`, round trip keeps `analysisIntervalDays` and `maintenanceActionType`, invalid `maintenanceActionType` rejected; repository test `updateAnalysisInterval queues a parameter_definition sync operation`.
- [ ] **Step 2:** `./gradlew :shared:jvmTest` — FAIL.
- [ ] **Step 3:** Implement entities, migration, schema export, mappers, export, sync JSON, SQL migration + pgTAP assertion (column present; upsert with/without the field).
- [ ] **Step 4:** `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest` PASS; if Docker is available `python3 tools/ci/backend.py prepare && python3 tools/ci/backend.py verify` PASS (otherwise say so — CI runs it).
- [ ] **Step 5: Commit** `feat(shared): schema v15 for rhythm, linked tasks and dismissals` (commit `15.json`).

### Task 5: Linked Tâches

**Files:**
- Modify: `shared/.../AquariumRepository.kt` (`createTask` L500-525, `resolveTaskOccurrence` L527-557, new method), `shared/.../AquaLogDatabase.kt` (`TaskDao`)
- Modify: `ui/.../HomeViewModel.kt` (`createTask`, `completeTask` L1299, `saveRapidSession` success hook L1602-1625, `toggleMaintenanceAction` defaults L1520-1544), `ui/.../TaskUi.kt` (creation sheet), `ui/.../home/TankHome.kt` (`TaskRow` L415-444)
- Modify: 6 catalogs
- Test: `TaskRepositoryContractTest.kt`, `androidApp/src/androidTest/.../TaskFlowTest.kt`

**Interfaces:**
- Consumes: `maintenanceActionType` fields (Task 4).
- Produces:
  - `suspend fun completeLinkedTasks(aquariumId: String, actionTypes: Set<MaintenanceActionType>, today: TaskDate, resolvedAtEpochMillis: Long): List<TaskResolutionResult>` — resolves as COMPLETED every pending occurrence of that Aquarium with `dueDate ≤ today` and a matching type, once (occurrences already resolved are skipped).
  - `fun defaultMaintenanceInput(type: MaintenanceActionType, last: MaintenanceAction?): MaintenanceActionInput` (extracted from `toggleMaintenanceAction`, reused).
  - VM `completeTask(occurrence)`: for a linked occurrence, saves a Rapid Session with only that action (default quantity) and lets `completeLinkedTasks` resolve it; unlinked behaviour unchanged.
  - Creation sheet: optional chips « Lier à une action » (none + the 6 types); empty title is filled with the action label.
  - Home row shows the action stroke icon (ic_drop for WATER_CHANGE, existing icons otherwise or a generic one).
  - `fun overdueLinkedAction(pending: List<TaskOccurrence>, aquariumId: String, type: MaintenanceActionType, today: TaskDate): Boolean` in `ui/.../HomeOverview.kt` (used by Task 7).

- [ ] **Step 1: Write the failing tests:** contract — `saving a water change completes the due linked water-change task once`; `a future linked occurrence is untouched`; `ticking then saving the same action the same day does not complete the next occurrence` (Review Focus 4); connected `TaskFlowTest.linkedWaterChange_tickedOnHome_recordsTheAction` (History shows « Changement d'eau » with the default quantity).
- [ ] **Step 2:** Run — FAIL.
- [ ] **Step 3:** Implement; reminders: cancel resolved, schedule next (as `resolveTask` does L1284-1285).
- [ ] **Step 4:** `:shared:jvmTest :ui:jvmTest` PASS; `TaskFlowTest`, `RapidSessionFlowTest` PASS.
- [ ] **Step 5: Commit** `feat: Tâches linked to a maintenance action`.

### Task 6: Pistes rules (pure)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/assistance/Pistes.kt`
- Test: `shared/src/jvmTest/.../assistance/PistesTest.kt`

**Interfaces:**
- Consumes: `ReadingLevel` (Task 1), `AquariumProfile`.
- Produces: `enum class PisteKey` (one entry per sentence in the Pistes table, plus `WATER_CHANGE_OVERDUE`, `CYCLING_NORMAL`); `enum class Direction { LOW, HIGH }`; `data class PisteAdvice(val parameter: BuiltInParameter, val direction: Direction, val pistes: List<PisteKey>, val retestInDays: Int)`; `fun direction(definition: ParameterDefinition, value: Double): Direction?` (null when in range); `fun pistesFor(parameter: BuiltInParameter, direction: Direction, profile: AquariumProfile, waterChangeOverdue: Boolean): PisteAdvice` — table order, max 3 pistes, overdue first (then the first 2 of the table), cycling replacement for AMMONIA/NITRITE HIGH.

- [ ] **Step 1: Write the failing tests:** `nitrite high gives the three pistes and retest 1`; `nitrate high retest 3`; `ph low starts with KH`; `overdue water change comes first and keeps 3 pistes`; `cycling nitrite high gives the cycling note, retest 2`; `unknown combination gives the generic pistes`.
- [ ] **Step 2:** `./gradlew :shared:jvmTest --tests '*PistesTest*'` — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `:shared:jvmTest` PASS.
- [ ] **Step 5: Commit** `feat(shared): rule-based pistes`.

### Task 7: Pistes in the app

**Files:**
- Create: `ui/.../PistesUi.kt` (`PistesCard(advices, disclaimer)`, `piste label` mapping)
- Modify: `ui/.../HomeViewModel.kt` (after-save advices), `ui/.../RapidSessionUi.kt` or the post-save surface, `ui/.../home/TankHome.kt` (card under the status line when current readings are out of range)
- Modify: 6 catalogs (one key per `PisteKey`, `pistes_title` « Pistes pour %1$s », `pistes_retest` « Refaites un test %1$s. » with « demain » / « dans %d jours », `pistes_disclaimer`)
- Test: `androidApp/src/androidTest/.../RapidSessionFlowTest.kt` or new `PistesFlowTest.kt`

**Interfaces:**
- Consumes: `pistesFor`, `direction` (Task 6), `overdueLinkedAction` (Task 5), latest readings (lot 1).
- Produces: after a save with at least one new out-of-range value, a bottom sheet lists one `PisteAdvice` per such parameter + disclaimer; the home shows a collapsible « Pistes » card for current out-of-range readings (same content). Copy from the Pistes table.

- [ ] **Step 1: Write the failing test** `PistesFlowTest.outOfRangeNitrite_showsPistesAfterSaving_andOnTheHome` (nitrite 0.25 → sheet with « Changez 30 % de l'eau. » and the disclaimer; close → home card shows the same first piste).
- [ ] **Step 2:** Run — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `PistesFlowTest`, `LocalizedAccessibilityFlowTest` PASS; `:ui:jvmTest` PASS.
- [ ] **Step 5: Commit** `feat(ui): pistes after saving and on the home`.

### Task 8: Analysis rhythm (pure)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/assistance/Rhythm.kt`
- Modify: `shared/.../Tasks.kt` (make `TaskDate.plusDays` and `daysInMonth` public; add `TaskDate.toEpochDay()`/`fromEpochDay`)
- Test: `shared/src/jvmTest/.../assistance/RhythmTest.kt`

**Interfaces:**
- Consumes: `ReadingLevel`/`readingLevel` (Task 1), `analysisIntervalDays` (Task 4).
- Produces: `fun defaultIntervalDays(profile: AquariumProfile, parameter: BuiltInParameter): Int?` (Rule tables); `fun effectiveIntervalDays(definition: ParameterDefinition, profile: AquariumProfile): Int?` (`analysisIntervalDays` wins; `0` means « jamais »); `data class ReadingSample(val parameterDefinitionId: String, val value: Double, val day: TaskDate)`; `data class DueTest(val definition: ParameterDefinition, val dueDate: TaskDate, val reason: DueReason)` with `enum DueReason { RHYTHM, FOLLOW_UP }`; `fun dueTests(setup: AquariumSetup, samples: List<ReadingSample>, today: TaskDate): List<DueTest>` — only active parameters; RHYTHM due = last reading day + interval (never measured → due today); FOLLOW_UP: every day T holding a sample with `readingLevel ≠ OK` triggers active NITRITE/AMMONIA at T+1 and T+3, due when no reading of it on/after that date; returns items with `dueDate ≤ today`, earliest first, one per parameter; `fun looksCycled(setup: AquariumSetup, latest: List<LatestReading>): Boolean` — profile CYCLING and latest AMMONIA and NITRITE (when active) are both ≤ 0.

- [ ] **Step 1: Write the failing tests:** `established nitrate measured 8 days ago is due`; `nitrite 13 days ago is not due`; `never measured active pH is due today`; `custom interval 3 wins`; `interval 0 never due`; `an out-of-range sample yesterday makes nitrite and ammonia follow-ups due today`; `a nitrite reading after the follow-up date clears it`; `cycling looks cycled when ammonia and nitrite are 0`.
- [ ] **Step 2:** `./gradlew :shared:jvmTest --tests '*RhythmTest*'` — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `:shared:jvmTest` PASS.
- [ ] **Step 5: Commit** `feat(shared): analysis rhythm and due tests`.

### Task 9: Due tests in the app, rhythm settings, reminders

**Files:**
- Modify: `shared/.../AquariumRepository.kt` (+ `observeReadingSamples(aquariumId: String, timeZoneId: String): Flow<List<ReadingSample>>` built on `observeReadingsForAquarium` L390)
- Modify: `ui/.../HomeViewModel.kt` (state `dueTests`, `openRapidSession(firstParameterIds: List<String> = emptyList())` L1172-1194), `ui/.../home/TankHome.kt` (« Aujourd'hui »: one row « Tester NO₃ et pH » → `openRapidSession(those ids)`), `ui/.../RapidSessionUi.kt` (requested parameters first)
- Create: `ui/.../RhythmSettingsScreen.kt` (from More → Aquariums → « Rythme d'analyse »: one row per active parameter, choices Auto (profil) / 1 / 2 / 3 / 7 / 14 / 30 jours / Jamais → `updateAnalysisInterval`)
- Modify: `ui/.../TaskReminderScheduler.kt` (+ `scheduleDueTests(at: TaskDate?)`), `androidApp/.../TaskReminders.kt` (alarm at 09:00 of the earliest due date; receiver recomputes due tests via `AquaLogApplication.aquariumRepository` and posts « Tests à faire : %1$s » or nothing; reschedule on boot/time change), `androidApp/src/main/res/values*/strings.xml`
- Modify: 6 catalogs (ui) + 6 catalogs (androidApp)
- Test: `ui/src/jvmTest/.../DueTestReminderTest.kt` (pure `nextDueTestReminder(dueDates: List<TaskDate>, zone: TimeZone, now: Long): Long?`), connected `TankHomeDueTestsFlowTest.kt` (or in `TaskFlowTest`)

**Interfaces:**
- Consumes: `dueTests`, `effectiveIntervalDays` (Task 8), `updateAnalysisInterval` (Task 4).
- Produces: `HomeUiState.dueTests: List<DueTest>`; `fun nextDueTestReminder(...)` (09:00 local of the earliest due date, or tomorrow 09:00 if that moment has passed today).

- [ ] **Step 1: Write the failing tests:** `DueTestReminderTest` — due today before 9h → today 9h; after 9h → tomorrow 9h; none → null; connected — `dueNitrate_showsATestRow_thatOpensEntryWithNitrateFirst`; `rhythmSetting_never_removesTheRow`.
- [ ] **Step 2:** Run — FAIL.
- [ ] **Step 3:** Implement; the receiver returns early without POST_NOTIFICATIONS (Review Focus 5).
- [ ] **Step 4:** `:ui:jvmTest :shared:jvmTest :androidApp:testDebugUnitTest` PASS; connected classes + `TaskFlowTest`, `LocalizedAccessibilityFlowTest` PASS.
- [ ] **Step 5: Commit** `feat: due tests, rhythm settings and reminders`.

### Task 10: « Votre bac a l'air cyclé »

**Files:**
- Modify: `ui/.../HomeViewModel.kt` (observe dismissals, `switchToEstablished()`, `dismissCycledProposal()`), `ui/.../home/TankHome.kt` (card), 6 catalogs
- Test: connected `TankHomeDueTestsFlowTest.kt` (or a new class)

**Interfaces:**
- Consumes: `looksCycled` (Task 8), `AssistantDao` (Task 4, kind `"cycled"`), `updateAquarium` (existing).
- Produces: card « Votre bac a l'air cyclé. Passer au rythme d'un bac établi ? » with « Passer en établi » (profile → ESTABLISHED; parameters unchanged) and « Plus tard » (dismiss until a newer Session: stored `sessionId` = latest Session id; card shows again only when the latest Session id differs and it still looks cycled).

- [ ] **Step 1: Write the failing test** `cycledProposal_switchesProfile_andLaterHidesItUntilTheNextSession`.
- [ ] **Step 2:** Run — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Class + `LocalizedAccessibilityFlowTest` PASS.
- [ ] **Step 5: Commit** `feat(ui): propose switching a cycled tank to established`.

### Task 11: Weekly series (pure + home)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/assistance/Series.kt`
- Modify: `ui/.../HomeViewModel.kt`, `ui/.../home/TankHome.kt` (small line under the status: « 8 semaines d'entretien régulier » when ≥ 1), 6 catalogs (plural `series_weeks`)
- Test: `shared/src/jvmTest/.../assistance/SeriesTest.kt`

**Interfaces:**
- Consumes: `effectiveIntervalDays`, `ReadingSample` (Task 8).
- Produces: `data class AquariumHistory(val setup: AquariumSetup, val samples: List<ReadingSample>, val occurrences: List<TaskOccurrence>)`; `fun weeklySeries(histories: List<AquariumHistory>, today: TaskDate): Int`.
- Rules: weeks are Monday–Sunday. For each Aquarium, a week **requires** a reading of every active parameter whose effective interval ≤ 7 days, and every Tâche occurrence due that week resolved COMPLETED; each requirement may be met up to **1 day after** the week ends. A week with no requirement in any Aquarium is **neutral** (skipped, neither counts nor breaks). A week counts when every Aquarium with requirements meets them. The series = consecutive counted weeks going back from the last finished week; the current week adds 1 if already met, never breaks.

- [ ] **Step 1: Write the failing tests:** `three kept weeks give 3`; `a missed week breaks`; `a neutral week is skipped`; `a reading on Monday after the week still counts`; `an ignored task breaks the week`; `two aquariums: one missing breaks`; `current week met adds one, unmet does not break`.
- [ ] **Step 2:** `./gradlew :shared:jvmTest --tests '*SeriesTest*'` — FAIL.
- [ ] **Step 3:** Implement; VM builds histories from repository flows already available (readings per Aquarium, resolved + pending occurrences).
- [ ] **Step 4:** `:shared:jvmTest :ui:jvmTest` PASS.
- [ ] **Step 5: Commit** `feat: weekly maintenance series`.

### Task 12: Sunday summary — card and notification

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/assistance/WeeklySummary.kt`
- Create: `ui/.../home/WeeklySummaryCard.kt`; Modify `ui/.../home/TankHome.kt`, `ui/.../HomeViewModel.kt`
- Modify: `androidApp/.../TaskReminders.kt` (or new `WeeklySummaryReminder.kt`: weekly alarm Sunday 19:00 local, reschedule after firing and on boot/time change), `AndroidManifest.xml` if a new receiver, `androidApp/src/main/res/values*/strings.xml`
- Modify: 6 ui catalogs
- Test: `shared/src/jvmTest/.../assistance/WeeklySummaryTest.kt`, `ui/src/jvmTest/.../WeeklySummaryReminderTest.kt` (pure next-Sunday-19h), connected `WeeklySummaryFlowTest.kt`

**Interfaces:**
- Consumes: `AquariumHistory`, `weeklySeries` (Task 11), `dueTests`, `ReadingSample` (Task 8).
- Produces: `data class WeeklySummary(val testsDone: Int, val tasksCompleted: Int, val series: Int, val changes: List<ValueChange>, val nextWeek: List<DueTest>)`, `data class ValueChange(val definition: ParameterDefinition, val from: Double, val to: Double)`; `fun weeklySummary(history: AquariumHistory, weekStart: TaskDate, series: Int, today: TaskDate): WeeklySummary` (`changes` = parameters whose last value in the week differs from the last value before it by ≥ 10^-precision); `fun nextSundaySummary(now: Long, zone: TimeZone): Long`.
- Card « Bilan de la semaine » on the home from Sunday 00:00 to Monday 23:59 for the displayed Aquarium: free part = tests faits, tâches cochées, série; **Pro** part = changes (« NO₃ 20 → 35, en hausse ») and next week's due tests; non-Pro sees a one-line Pro invitation (`openProPaywall`).
- Notification (all Aquariums, free text only, Pro never assumed in the background): « Votre bilan de la semaine est prêt · %1$d semaines d'entretien régulier » (or without the series when 0); skipped without POST_NOTIFICATIONS.

- [ ] **Step 1: Write the failing tests:** `WeeklySummaryTest` — counts, changes with precision, nextWeek; reminder — Sunday 18h → today 19h, Sunday 20h → next Sunday 19h, Wednesday → coming Sunday; connected — `summaryCard_onSunday_showsFreePart_andProPartForPro` (needs a clock: add `currentTimeMillis` / `currentTimeZoneId` wiring to `HomeViewModel.factory` L2107-2133 and an override in `TestAquaLogApplication`).
- [ ] **Step 2:** Run — FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `:shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest` PASS; `WeeklySummaryFlowTest`, `LocalizedAccessibilityFlowTest` PASS.
- [ ] **Step 5: Commit** `feat: Sunday summary card and notification`.

### Task 13: Full verification

**Files:** none new (fixes only, separate commits).

- [ ] **Step 1:** `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest :androidApp:testReleaseUnitTest :androidApp:lintDebug :androidApp:lintRelease :androidApp:assembleRelease --console=plain` — BUILD SUCCESSFUL.
- [ ] **Step 2:** If Docker is available: `python3 tools/ci/backend.py prepare && python3 tools/ci/backend.py verify && python3 tools/ci/backend.py stop` — PASS (else state it; CI runs it).
- [ ] **Step 3:** Full `./gradlew :androidApp:connectedDebugAndroidTest --console=plain` on the headless Pixel_9a with animation scales 0 — 0 failed (report skips).
- [ ] **Step 4:** Manual pass FR light + DE dark 1.5: entry colours orange/red, check dialog, pistes sheet and card, due-test row, rhythm screen, cycled card, series line, Sunday card (Pro and free); grant then deny notifications and confirm no crash.
- [ ] **Step 5: Commit** any fixes separately (`fix(...)`).
