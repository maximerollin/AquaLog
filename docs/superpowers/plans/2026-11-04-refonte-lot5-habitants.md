# Refonte lot 5 — Habitants, encyclopédie, dessins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Say what lives in each Aquarium, check before adding a species that it will do well there, and see the inhabitants swim in the home scene.

**Architecture:**
- **The encyclopedia is a JSON file embedded in the app**, at `ui/src/commonMain/composeResources/files/encyclopedia/species.json`.
  - It is decoded into `Species` (`:shared`), with names in 5 languages.
  - It is loaded once, offline.
- **The verdict is a pure function in `:shared`**, `assessSpecies`. It compares one species with the Aquarium's volume, latest Measurements and present inhabitants.
- **Inhabitants are a new synced entity**, `Inhabitant` (Room table `inhabitants`, server table `public.inhabitants`), on the same pattern as the other entities.
  - Adding or removing them shows in the Chronologie.
  - Adding them triggers the ammonia/nitrite follow-up tests.
- **Drawings are Android vector XMLs in compose resources.**
  - There are 8 family silhouettes and 15 dedicated drawings.
  - The scene draws them as tinted painters, moved by one pure motion function per motion family.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (resources: `Res.readBytes`, `painterResource`), kotlinx.serialization, Room 3, Supabase (SQL + pgTAP), Glance 1.2.0.

**Spec:** `docs/superpowers/specs/2026-10-30-refonte-aquagenda-design.md`:
- §4.5 Habitants et encyclopédie;
- §4.2 (« les habitants réels du bac qui nagent, chacun selon sa façon de bouger »);
- §4.3 (follow-up tests after an inhabitant is added);
- §4.4 (the small widget's mini-scene « avec les habitants dessinés »);
- §4.6 (« Sans photo : … mini-scène dessinée avec les habitants »);
- §4.7 (habitants and encyclopedia are free);
- §3 (offline, sync, export, deletion, reduced motion);
- §7 (« Habitants hors encyclopédie », « Contenu de l'encyclopédie et dessins »).

Mockup: `docs/design/20-refonte-carnet/04-ajout-habitant.html`.

## Global Constraints

- **Free.** Inhabitants and the encyclopedia need no Pro. Only the widget stays Pro (lot 3).
- **The fiche never forbids.** « Ajouter quand même » is always there.
- **Bac sans Mesure:** no water verdict.
  - Each water criterion shows « pas encore mesuré ».
  - A « Faire une analyse » button opens the entry for this Aquarium.
  - The volume is checked anyway.
- **Bac en Cyclage:** adding a fish (not a plant, shrimp or snail) shows « Attendez que les nitrites soient à 0 avant d'ajouter des poissons. »
- **Species outside the encyclopedia:** a typed name plus a chosen family silhouette.
  - There is no verdict.
  - It appears in the scene and in the Chronologie.
- **Data rules.**
  - No FishBase, no scraping, no copied fiches (§2 anti-goal, EU database right).
  - Multilingual common names come from Wikidata (CC0). Each species records its Wikidata QID.
- **Content review.** The facts of every fiche are a draft until Maxime validates them (§7).
  - `docs/encyclopedia/review.md` lists each species with its sources, for that review.
- **Offline first.**
  - Everything works without network.
  - Sync, export and account deletion cover inhabitants.
- **Reduced motion:** the fish stand still when the system asks to reduce animations.
- **Battery:** no more than the current scene's frame pacing (`FrameDelayMillis`). The scene draws at most 12 sprites.
- **French copy:** French uses « vous ». Use the glossary terms of `docs/localization-glossary.md`: Aquarium, Mesure, Session, Chronologie, Cyclage.
- **Localization.**
  - Every new key exists in the 6 catalogs under `ui/src/commonMain/composeResources/`.
  - `LocalizationContractTest` stays green.
- **Accessibility.**
  - Touch targets are ≥ 48 dp.
  - Verdict badges carry text, not colour alone.
  - The scene keeps `clearAndSetSemantics {}`. The inhabitants are listed in the habitants sheet.
  - DE dark at font scale 1.5: nothing is clipped mid-word.
- **No new telemetry.**
- **CI must stay green:**
  - `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest :androidApp:testReleaseUnitTest :androidApp:lintDebug :androidApp:lintRelease :androidApp:assembleRelease`;
  - `python3 tools/ci/backend.py prepare && python3 tools/ci/backend.py verify`;
  - `deno check` of the integration tests, with the CI's Deno version.

## Decisions taken in this plan (Claude's calls, to check)

- **This lot ships 15 species, not 150.**
  - These are the 15 with a dedicated drawing: néon, cardinalis, guppy, platy, molly, betta, corydoras (panda), ancistrus, scalaire, gourami (nain), danio (zébré), rasbora (arlequin), red cherry, amano, otocinclus.
  - The other ≈ 135 fiches, and the remaining 15–25 dedicated drawings, are a separate content plan (« lot 5b »). They only add entries to `species.json` and vector files, with no code change.
  - This keeps the code reviewable and lets Maxime validate the facts in batches.
- **Family silhouettes (8):** `TETRA`, `LIVEBEARER`, `CICHLID`, `LABYRINTH`, `CYPRINID`, `CATFISH`, `SHRIMP`, `SNAIL`. Plants use a 9th family, `PLANT`. It isn't drawn as a swimmer; it adds a plant to the decor.
- **Motion families (5, from §4.5):** `SCHOOL` (en banc), `BOTTOM` (au fond), `GLIDE` (en glissant), `CRAWL` (en rampant), `SLOW` (très lent).
- **Verdict rules.**
  - **Volume:** the Aquarium's volume, converted from US gallons when needed, is below the species' `minVolumeLiters` → ✗ problem.
  - **Water** (pH, température, GH, KH), for each with a latest Measurement:
    - inside the species range → ✓;
    - outside, but by less than 10 % of the range width → ⚠ caution;
    - further out → ✗ problem;
    - no Measurement → « pas encore mesuré », no verdict.
  - **Cohabitation**, checked both ways between the new species and each present group, from tags:
    - `EATS_SMALL_FISH` (prey adult size ≤ 4 cm) → ✗;
    - `EATS_SHRIMP` against shrimp → ✗;
    - `FIN_NIPPER` against `LONG_FINS` → ✗;
    - `TERRITORIAL` with the same species already present → ⚠;
    - `EATS_PLANTS` against present plants → ⚠.
  - **Group:** the counter starts at `minGroup`. A chosen count below it → ⚠.
  - **Overall:** any ✗ → « Déconseillés »; else any ⚠ → « Avec précautions »; else « Vont bien dans ce bac ».
  - **Badge:**
    - with one ✗, its two-word reason;
    - with 2 or more ✗, « N problèmes »;
    - with a ⚠, its two-word reason;
    - otherwise « Compatible ».
- **Inhabitant model.**
  - One row per group added at one time: `Inhabitant(id, aquariumId, speciesId?, customName?, family, count, addedAtEpochMillis, removedAtEpochMillis?)`.
  - Removing n of a group of m:
    - the row's count becomes m − n;
    - a new row is created, with count n, the same `addedAt` and `removedAt` = now.
  - Removing the whole group sets `removedAt` on the row.
  - The Chronologie shows one line per add (`addedAt`) and per removal (`removedAt`).
- **The follow-up trigger** is the add day of a fish, shrimp or snail (not a plant). It joins the existing trigger days, and keeps the same exception: none while in Cycling.
- **Where inhabitants are managed:** tapping the scene opens a « Habitants » sheet.
  - It lists the groups, with « Retirer… » (a count chooser) and « Ajouter un habitant ».
  - An empty scene shows a small « Ajouter vos habitants » chip (the lot 1 « no invitation until lot 5 » ends here).
- **Widget:** the small widget draws up to 3 present groups as family silhouettes, using Android drawables (`widget_family_*.xml`, recoloured). Glance can't draw compose-resource vectors.
- **Initial account copy:** at account migration, every inhabitant gets an upsert operation, as photos do (lot 4 I4). Nothing is lost at sign-in.

## Review Focus

1. **An Aquarium in US gallons is judged in liters.**
   - Expected: a 30 gal (113 L) Aquarium passes a 100 L minimum.
   - Test: in Task 2.
2. **A species with no reading for a criterion.**
   - Expected: « pas encore mesuré », never a ✗ from a missing value; the volume is still judged.
   - Test: in Task 2.
3. **Removing part of a group twice, then re-adding.**
   - Expected: the counts in the scene and the sheet always equal the present total.
   - Expected: the Chronologie shows each step once.
   - Test: in Task 3.
4. **A malformed or partial `species.json` entry** (a missing language, a min above its max, an unknown family).
   - Expected: CI fails at the validator, never at runtime.
   - Test: in Task 1.
5. **Many inhabitants** (200 néons and 10 groups).
   - Expected: the scene stays ≤ 12 sprites, and the frame pacing doesn't change.
   - Test: in Task 8.

---

### Task 1: Species model, encyclopedia format and loader

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/encyclopedia/Species.kt`, `ui/src/commonMain/kotlin/com/maximerollin/aqualog/encyclopedia/Encyclopedia.kt`, `ui/src/commonMain/composeResources/files/encyclopedia/species.json` (3 fixture species: néon, corydoras panda, red cherry; Task 6 replaces them with the full 15)
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/encyclopedia/SpeciesValidationTest.kt`, `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/encyclopedia/EncyclopediaFileTest.kt`

**Interfaces:**
- Produces, in `Species.kt`:
  - `@Serializable data class Species`, with fields:
    - `id: String` (kebab, e.g. `"neon"`);
    - `wikidata: String` (`"Q…"`);
    - `scientificName: String`;
    - `names: Map<String, String>` (keys `fr`, `en`, `de`, `it`, `es`);
    - `kind: SpeciesKind`;
    - `family: DrawingFamily`;
    - `motion: MotionFamily`;
    - `ph: ValueRange?`, `temperatureC: ValueRange?`, `gh: ValueRange?`, `kh: ValueRange?`;
    - `minVolumeLiters: Int?`;
    - `adultSizeCm: Double`;
    - `minGroup: Int`;
    - `tags: Set<SpeciesTag>`;
    - `diet: Diet`;
    - `lifespanYears: Int`;
    - `zone: SwimZone`;
    - `color: Long` (ARGB, used to recolour the silhouette);
    - `drawing: String?` (dedicated vector name, e.g. `"species_neon"`).
  - `ValueRange(min: Double, max: Double)`.
  - `enum SpeciesKind { FISH, SHRIMP, SNAIL, PLANT }`.
  - `enum DrawingFamily { TETRA, LIVEBEARER, CICHLID, LABYRINTH, CYPRINID, CATFISH, SHRIMP, SNAIL, PLANT }`.
  - `enum MotionFamily { SCHOOL, BOTTOM, GLIDE, CRAWL, SLOW }`.
  - `enum SpeciesTag { EATS_SMALL_FISH, EATS_SHRIMP, FIN_NIPPER, LONG_FINS, TERRITORIAL, EATS_PLANTS, PEACEFUL }`.
  - `enum Diet { OMNIVORE, HERBIVORE, CARNIVORE, ALGAE }`.
  - `enum SwimZone { BOTTOM, MIDDLE, SURFACE }`.
  - `fun decodeSpecies(json: String): List<Species>`.
  - `fun speciesProblems(species: List<Species>): List<String>`: human-readable problems, empty when valid.
- Produces, in `Encyclopedia.kt`:
  - `suspend fun loadEncyclopedia(): List<Species>`, via `Res.readBytes("files/encyclopedia/species.json")`, cached;
  - `fun Species.localName(languageTag: String): String`, which falls back to `fr`, then to `scientificName`.

- [ ] **Step 1: Write the failing tests.**
  - `SpeciesValidationTest`, on hand-written JSON strings:
    - a valid entry gives no problems;
    - each of these gives one problem naming the id:
      - a missing language key;
      - `min > max`;
      - an unknown family;
      - a duplicate `id`;
      - `minGroup < 1`;
      - a `wikidata` not matching `^Q\d+$`;
      - a fish without `minVolumeLiters`.
  - `EncyclopediaFileTest`: the shipped `species.json` decodes, `speciesProblems(...)` is empty, and every non-null `drawing` names an existing file under `ui/src/commonMain/composeResources/drawable/`.
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*SpeciesValidationTest*' :ui:jvmTest --tests '*EncyclopediaFileTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - The JSON uses enum names as written above, with `ignoreUnknownKeys = false`, so a typo fails CI.
  - The 3 fixtures have real values. Their names come from Wikidata labels: fetch `https://www.wikidata.org/wiki/Special:EntityData/<QID>.json` and keep the QID.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(habitants): species model and embedded encyclopedia`.

### Task 2: The verdict — `assessSpecies`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/encyclopedia/Compatibility.kt`
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/encyclopedia/CompatibilityTest.kt`

**Interfaces:**
- Consumes: Task 1, `AquariumSetup`, `LatestReading` (as the home's « Dernières valeurs » use it), and `Parameter`, the existing enum: map PH, TEMPERATURE, GH and KH to the species ranges.
- Produces:
  - `enum CriterionStatus { OK, CAUTION, PROBLEM, NOT_MEASURED }`.
  - `enum CriterionKind { VOLUME, PH, TEMPERATURE, GH, KH, COHABITATION, GROUP }`.
  - `data class CriterionCheck(kind: CriterionKind, status: CriterionStatus, reason: Reason?, aquariumValue: Double?, otherSpeciesId: String?)`.
  - `enum Reason`, which drives the two-word badges:
    - `TANK_TOO_SMALL` « Bac petit »;
    - `WATER_TOO_HARD` « Eau dure »;
    - `WATER_TOO_SOFT` « Eau douce »;
    - `PH_TOO_HIGH` « pH haut »;
    - `PH_TOO_LOW` « pH bas »;
    - `TOO_WARM` « Trop chaud »;
    - `TOO_COLD` « Trop froid »;
    - `EATS_SMALL_FISH` « Chasse petits »;
    - `EATS_SHRIMP` « Mange crevettes »;
    - `FIN_NIPPING` « Mordille nageoires »;
    - `TERRITORIAL` « Territorial »;
    - `EATS_PLANTS` « Mange plantes »;
    - `GROUP_TOO_SMALL` « Groupe petit ».
  - `enum Fit { GOOD, CAUTION, BAD }`.
  - `data class Assessment(species: Species, fit: Fit, checks: List<CriterionCheck>, problems: Int, badgeReason: Reason?, cyclingWarning: Boolean)`.
  - `data class PresentGroup(species: Species?, family: DrawingFamily, count: Int)`.
  - `fun assessSpecies(species: Species, setup: AquariumSetup, readings: List<LatestReading>, present: List<PresentGroup>, count: Int = species.minGroup): Assessment`.

- [ ] **Step 1: Write the failing tests** in `CompatibilityTest`, with a 120 L Established setup:
  - `a small peaceful fish in a matching tank is GOOD`:
    - néon with pH 7.0, 25 °C, GH 8 → `Fit.GOOD`;
    - every water check is OK;
    - `badgeReason == null`.
  - `the mockup's scalaire is BAD with 2 problems`:
    - scalaire (min 200 L, `EATS_SMALL_FISH`) with 10 néons present;
    - VOLUME is PROBLEM `TANK_TOO_SMALL`, and COHABITATION is PROBLEM `EATS_SMALL_FISH` with `otherSpeciesId == "neon"`;
    - `problems == 2`.
  - `a predator already present makes the small new species BAD`: the cohabitation check runs both ways.
  - `just outside a range is CAUTION, far outside is PROBLEM`, with a guppy at GH 6–20 (width 14):
    - GH 5 (−1 < 1.4) → CAUTION `WATER_TOO_SOFT`;
    - GH 3 → PROBLEM.
  - `no reading gives NOT_MEASURED and no water verdict`:
    - readings are empty;
    - the 4 water checks are NOT_MEASURED;
    - the fit only reflects volume, cohabitation and group.
  - `US gallons are converted`: 30 `US_GALLONS` passes `minVolumeLiters = 100`.
  - `a group below the minimum is CAUTION`: `count = minGroup − 1` gives `GROUP_TOO_SMALL`.
  - `cycling warns for fish only`: the profile CYCLING gives `cyclingWarning` true for néon and false for red cherry.
  - `territorial with the same species present is CAUTION`.
  - `plants skip volume and group`.
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*CompatibilityTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the rules exactly as listed in "Decisions" (the 10 % tolerance; prey ≤ 4 cm adult size; checked both ways). The badge picks the first PROBLEM, else the first CAUTION, in `CriterionKind` order.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat(habitants): judge a species against an Aquarium`.

### Task 3: Inhabitants storage, Chronologie lines and export (Room v17)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/Inhabitants.kt`
- Modify:
  - `AquaLogDatabase.kt` (`InhabitantEntity`, DAO, `MIGRATION_16_17`, version 17);
  - `AquariumRepository.kt`;
  - `PortableData.kt`;
  - `Timeline.kt`;
  - `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TimelineUi.kt`;
  - the 6 string catalogs.
- Create: `shared/schemas/…/17.json`
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/InhabitantRepositoryTest.kt`, `HistoricalMigrationQualificationTest.kt`, `PortableDataServiceTest.kt`

**Interfaces:**
- Produces:
  - `data class Inhabitant(id: String, aquariumId: String, speciesId: String?, customName: String?, family: DrawingFamily, count: Int, addedAtEpochMillis: Long, removedAtEpochMillis: Long? = null)`.
  - `AquariumRepository` members, each with a default (`flowOf(emptyList())`, or an error for writes):
    - `fun observeInhabitants(aquariumId: String): Flow<List<Inhabitant>>`: present groups only, `removedAt == null`;
    - `suspend fun addInhabitant(aquariumId: String, speciesId: String?, customName: String?, family: DrawingFamily, count: Int): Inhabitant`;
    - `suspend fun removeInhabitants(inhabitantId: String, count: Int)`: splits when `count` is less than the group's count;
    - `fun observeInhabitantHistory(aquariumId: String?): Flow<List<Inhabitant>>`: all rows, for the Chronologie.
  - `TimelineInhabitantChange(aquarium: Aquarium, inhabitant: Inhabitant, removed: Boolean, atEpochMillis: Long)`.
  - `PortableInhabitant`, plus `PortableDataDocument.inhabitants: List<PortableInhabitant> = emptyList()`. `formatVersion` stays 1.

- [ ] **Step 1: Write the failing tests** in `InhabitantRepositoryTest` (JVM Room plus a temp DB, as in `TaskCompleterTest`):
  - `adding a group shows it present`.
  - `removing part of a group splits it`:
    - 10 néons, remove 2 → present count 8;
    - history: the 8-row stays present, and a 2-row has `removedAt` set with the same `addedAt`.
  - `removing twice then re-adding keeps totals`:
    - 10, then −2, then −3, then +4;
    - the present total is 9, and the history has 1 add (10), 2 removals and 1 add (4).
  - `removing more than present is rejected`: `IllegalArgumentException`, and nothing changes.
  - `a custom species needs a name`: `speciesId == null && customName.isNullOrBlank()` throws.
  - `adding or removing enqueues a sync operation`: the INHABITANT entity type from Task 4. Until Task 4, assert only that the row exists; Task 4 adds the sync assertion.
  - Migration test: 16→17 creates the empty table.
  - Export test: export, then import, restores the inhabitants with their history; a file without the key imports with none.
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*InhabitantRepositoryTest*' --tests '*PortableDataServiceTest*' --tests '*HistoricalMigration*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Table `inhabitants`:
    - columns `id` PK, `aquariumId` (FK to aquariums), `speciesId`, `customName`, `family`, `count`, `addedAtEpochMillis`, `removedAtEpochMillis`;
    - index on `aquariumId`.
  - **Chronologie:**
    - `TimelineUi` gets `TimelineListItem.InhabitantItem` lines, merged by time with the sessions and Tâches. Read the history flow next to `observeTimeline`, with the same Aquarium and since filters.
    - The line copy is « 6 Otocinclus ajoutés » or « 2 Néons retirés ». Use plurals (`plurals` resources) in 6 catalogs.
    - For a custom species, the line shows its typed name.
- [ ] **Step 4: Run** the tests, then `./gradlew :ui:jvmTest`. Expected: PASS. Commit `17.json`.
- [ ] **Step 5: Commit** `feat(habitants): inhabitants stored, in the Chronologie and the export`.

### Task 4: Inhabitants sync (client + server)

**Files:**
- Modify: `shared/…/Sync.kt` (`SyncEntityType.INHABITANT("inhabitant")`, `PendingSyncBatch.inhabitants`, `InitialAccountCopy`/snapshot), `AquariumRepository.kt`, `AquaLogDatabase.kt` (remote apply, tombstones), `androidApp/…/SyncInfrastructure.kt`
- Create: `supabase/migrations/20261105100000_inhabitants.sql`, `supabase/tests/database/inhabitants.test.sql`
- Modify: `supabase/tests/integration/entity_sync_test.ts` (or a new `inhabitant_sync_test.ts`, added to `tools/ci/backend.py verify`)
- Test: `SyncCoordinatorTest`/`SyncRecoveryTest`, `androidApp/src/androidTest/…/SupabaseTwoDeviceSyncTest.kt`

**Interfaces:**
- Consumes: Task 3.
- Produces:
  - `public.inhabitants`:
    - columns `id uuid`, `account_id uuid` (FK to `auth.users`, `on delete cascade`), `aquarium_id uuid`, `species_id text`, `custom_name text`, `family text`, `count int > 0`, `added_at_epoch_millis bigint`, `removed_at_epoch_millis bigint`, plus the same version and tombstone columns as the other entity tables;
    - FK `(account_id, aquarium_id)` to aquariums, `on delete cascade`;
    - RLS: own rows only.
  - `apply_sync_operation` handles `inhabitant` upserts and deletes with the same LWW and receipts as the other entities. Read `20261012120000_recoverable_entity_sync.sql` and `20261013100000_server_ordered_lww.sql` first, and extend the dispatch where the existing entity types are dispatched. Do not wrap it from outside.

- [ ] **Step 1: Write the failing tests.**
  - **pgTAP:**
    - an upsert stores the row and an update with a newer version wins;
    - another account can't select or insert;
    - deleting the auth user removes the rows.
  - **Coordinator/Room:**
    - an added inhabitant is in `pendingSyncBatch().inhabitants`;
    - a remote snapshot with an inhabitant adds it locally;
    - a remote tombstone deletes it;
    - at account migration (`completeAccountMigration`), every local inhabitant gets an upsert operation.
  - **Instrumented:** `productionAdapter_syncsInhabitantsToASecondDevice`. Add 6 néons on device 1 and sync; device 2 syncs and sees them. Then remove 2 on device 1; device 2 sees 4 present and 1 removed row.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Mirror how `MAINTENANCE_ACTION` flows end to end: entity type, batch, payload JSON in `toRemoteJson`, pull mapping, `toVersions`, remote apply, tombstones.
  - `migrate-initial-copy` does not carry inhabitants: the upserts enqueued at migration send them.
- [ ] **Step 4: Run** the full CI command, the backend `prepare` and `verify`, and the instrumented test against the local stack. Expected: PASS.
- [ ] **Step 5: Commit** `feat(habitants): inhabitants follow the account`.

### Task 5: Follow-up tests after an inhabitant is added

**Files:**
- Modify: `shared/…/assistance/Rhythm.kt` (`dueTests`), `ui/…/DueTests.kt` (`dueTestSummary`, `nextDueDate`), every `dueTests(` caller (grep)
- Test: `shared/src/jvmTest/…/assistance/RhythmTest.kt`, `ui/src/jvmTest/…/DueTestReminderTest.kt`

**Interfaces:**
- Produces: `fun dueTests(setup: AquariumSetup, samples: List<ReadingSample>, today: TaskDate, inhabitantAddedDays: List<TaskDate> = emptyList()): List<DueTest>`. The days of fish, shrimp or snail additions join `triggerDays`, except in CYCLING.

- [ ] **Step 1: Write the failing tests** in `RhythmTest`:
  - `adding an inhabitant schedules ammonia and nitrite at D+1 and D+3`, on an Established Aquarium;
  - `no follow-up while cycling`;
  - `a measurement after D+1 clears that follow-up`, as the existing rule does.
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*RhythmTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** it and pass the add days from the repository. Plants are excluded: use `Species.kind`, or `DrawingFamily.PLANT` for custom ones.
- [ ] **Step 4: Run** `./gradlew :shared:jvmTest :ui:jvmTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(habitants): adding an inhabitant schedules follow-up tests`.

### Task 6: The 15 starter fiches and the review sheet

**Files:**
- Modify: `ui/src/commonMain/composeResources/files/encyclopedia/species.json` (15 species)
- Create: `docs/encyclopedia/review.md`

**Interfaces:**
- Consumes: Task 1's format and validator.

- [ ] **Step 1: Write the 15 fiches** listed in "Decisions".
  - **Names:** from Wikidata labels in fr/en/de/it/es (fetch `Special:EntityData/<QID>.json`). When a label is missing in a language, use the scientific name and list the gap in `review.md`.
  - **Facts** (ranges, minimum volume, adult size, group, temperament tags, diet, lifespan, zone): write them from general aquarium knowledge, cross-checked against at least two public sources per species (for example Wikipedia in two languages, or a public-domain care sheet). Never FishBase, never AquaticLog or competitor apps.
  - **`review.md`:** one section per species, with each value, its sources (URLs) and a « à valider » checkbox for Maxime.
- [ ] **Step 2: Run** `./gradlew :ui:jvmTest --tests '*EncyclopediaFileTest*'`. Expected: PASS: the validator and every `drawing` exist. Keep `drawing: null` here; Task 8 adds the vectors and sets each `drawing`.
- [ ] **Step 3: Commit** `content(habitants): first 15 species fiches, to review`.

### Task 7: « Ajouter un habitant » — search and fiche

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/habitants/AddInhabitantScreen.kt`, `habitants/SpeciesFiche.kt`, `habitants/HabitantsViewModel.kt` (or a state slice in `HomeViewModel` if that matches the rhythm-settings pattern better: follow the existing pattern)
- Modify: the 6 string catalogs
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/habitants/AddInhabitantStateTest.kt`, an instrumented `androidApp/src/androidTest/…/AddInhabitantFlowTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3 and the 15 fiches of Task 6.
- Produces:
  - `fun searchSpecies(all: List<Species>, query: String, languageTag: String, kind: SpeciesKind?): List<Species>`. It matches the localized and scientific names, case- and accent-insensitive.
  - `fun groupedForAquarium(assessments: List<Assessment>): Map<Fit, List<Assessment>>`, in the order GOOD, CAUTION, BAD; inside a group, alphabetical by localized name.

- [ ] **Step 1: Write the failing state tests.**
  - `search finds "neon" for "néon", "Neon" and "Paracheirodon"`.
  - `the kind chips filter`.
  - `grouping keeps the three sections in order`.
  - `adding uses the counter value and returns to the home`.
  - `"Ajouter quand même" on a BAD species still adds`.
  - `a custom species needs a name and a family`.
- [ ] **Step 2: Run** `./gradlew :ui:jvmTest --tests '*AddInhabitantStateTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement the screens from the mockup.**
  - **Search screen:** title « Ajouter · <Aquarium> ».
    - Field « Rechercher une espèce… ».
    - Chips « Poissons », « Crevettes », « Escargots », « Plantes ».
    - Sections « Vont bien dans ce bac », « Avec précautions », « Déconseillés ».
    - Each row shows the localized name, the italic scientific name, and the badge (« ✓ Compatible » / « ⚠ <raison> » / « ✗ <raison> » or « ✗ N problèmes »).
    - Last row: « Espèce absente ? L'ajouter à la main » opens the custom form (name field and family chips).
  - **Fiche:**
    - The verdict line: « ✓ Va bien dans <Aquarium> » / « ⚠ Avec précautions pour <Aquarium> » / « ✗ Déconseillé pour <Aquarium> ».
    - A one-sentence explanation, built from the first two reasons. Its strings are per reason; there's no free-text field in the JSON.
    - « Comparé à votre bac »: one row per criterion, with the species range and the Aquarium's value.
    - The rows are folded behind « Détail › » when the fit is GOOD.
    - Size, lifespan and group facts.
    - Counter « Combien ? », starting at `minGroup`; minus and plus are 48 dp each.
    - Main button « Ajouter N <nom> »; on a BAD fit it reads « Ajouter quand même ».
  - **No Measurement:** water rows say « pas encore mesuré », with a « Faire une analyse » button that opens the rapid Session for this Aquarium.
  - **Cycling:** a banner with the copy from the Global Constraints.
- [ ] **Step 4: Write an instrumented flow test** `AddInhabitantFlowTest` on a seeded « Salon » 120 L with pH 7.2:
  - search « oto »;
  - Otocinclus is under « Vont bien dans ce bac »;
  - open it, and the counter shows 6;
  - « Ajouter 6 Otocinclus » brings the home back;
  - the habitants sheet lists « 6 Otocinclus ».
- [ ] **Step 5: Run** the JVM tests, the instrumented test on the emulator, and the full CI. Expected: PASS.
- [ ] **Step 6: Commit** `feat(habitants): add an inhabitant from the encyclopedia`.

### Task 8: Drawings and motion in the scene

**Files:**
- Create:
  - `ui/src/commonMain/composeResources/drawable/family_*.xml` (9 silhouettes, single-path, white fill, tinted at draw time);
  - `species_*.xml` (15 dedicated drawings, a few KB each);
  - `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/SceneInhabitants.kt`
- Modify: `home/TankScene.kt` (`TankScene(aquarium, inhabitants: List<SceneSprite> = emptyList(), …)`), `home/TankHome.kt` (habitants sheet, empty-scene chip), `species.json` (set `drawing`), the 6 string catalogs
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/ScenePhysicsTest.kt` (it exists; extend it)

**Interfaces:**
- Consumes: Tasks 1, 3, 6 and 7 (the add flow, opened from the sheet).
- Produces:
  - `data class SceneSprite(drawing: DrawableResource, tint: Color?, motion: MotionFamily, zone: SwimZone, seed: Int)`.
  - `fun sceneSprites(present: List<Inhabitant>, species: Map<String, Species>, max: Int = 12): List<SceneSprite>`. The count is shared between groups proportionally, with at least one sprite per group while groups ≤ max. Plants add none.
  - `fun spritePosition(motion: MotionFamily, zone: SwimZone, seed: Int, elapsedMillis: Long, width: Float, height: Float): Offset`, which is pure:
    - SCHOOL moves together in a loose cluster;
    - BOTTOM stays in the lowest 20 %;
    - GLIDE moves slowly and horizontally;
    - CRAWL moves along the sand or glass;
    - SLOW is almost still.

- [ ] **Step 1: Write the failing tests** in `ScenePhysicsTest`:
  - `200 neons and 10 groups give at most 12 sprites, one per group`.
  - `plants give no sprite`.
  - `BOTTOM stays in the lowest 20 percent for any time`: sample 1 000 `elapsedMillis`.
  - `every motion stays inside the scene`.
  - `elapsed 0 is a stable still frame`: the same position for the same seed, as reduced motion relies on it.
- [ ] **Step 2: Run** `./gradlew :ui:jvmTest --tests '*ScenePhysicsTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **Drawing:** in `drawWithCache`, draw painters with `translate` and `ColorFilter.tint` (the tint applies to silhouettes only). Drawings face right and are mirrored when moving left.
  - **Animation:** the existing elapsed-time loop drives it, with no new loop. Reduced motion leaves `elapsed = 0`.
  - **Habitants sheet:**
    - tapping the scene's background (not the vignette or the name) opens it;
    - a 48 dp row per present group: drawing, name and count, with « Retirer… » opening a count chooser;
    - « Ajouter un habitant » at the bottom.
  - **Empty scene:** the chip « Ajouter vos habitants ».
  - The vignette's mini-scene (lot 4) passes the same sprites, with `animate = false`.
- [ ] **Step 4: Take screenshots** on the emulator (FR light, DE dark at 1.5) of the home with 3 groups and of the sheet, saved under the scratchpad.
- [ ] **Step 5: Run** the tests and the full CI. Expected: PASS.
- [ ] **Step 6: Commit** `feat(habitants): inhabitants swim in the scene`.

### Task 9: Inhabitants in the small widget

**Files:**
- Create: `androidApp/src/main/res/drawable/widget_family_*.xml` (copies of the 9 silhouettes as Android drawables)
- Modify: `androidApp/…/widget/SmallTankWidget.kt`, `WidgetCommon.kt`, `ui/…/widget/WidgetAquarium.kt` (the present groups)
- Test: `androidApp/src/androidTest/…/widget/SmallTankWidgetTest.kt`, plus a JVM test that each `family_*.xml` has a `widget_family_*.xml` twin with the same `pathData`

**Interfaces:**
- Produces: `WidgetAquarium.groups: List<PresentGroup>` (Task 2's type), with the 3 largest groups.

- [ ] **Step 1: Write the failing tests.**
  - `SmallTankWidgetTest`: with 2 groups present, the scene shows 2 `Image` nodes, found by test tag `WidgetFamilyTag(family)`; with none, it shows none.
  - The twin test fails if any `pathData` differs.
- [ ] **Step 2: Implement.** Draw `Image(ImageProvider(R.drawable.widget_family_x), colorFilter = ColorFilter.tint(ColorProvider(speciesColor)))` over `widget_scene`, at fixed slots by zone (bottom, middle, surface), with 24 dp silhouettes and `contentDescription = null`. The widget's TalkBack label is unchanged.
- [ ] **Step 3: Run** the widget test on the emulator and the full CI. Expected: PASS.
- [ ] **Step 4: Commit** `feat(habitants): inhabitants in the small widget`.

---

## Manual pass (controller, after Task 9)

On the emulator, debug build, French, local backend:
1. **Search and add.** The search groups match the mockup. Add 6 otocinclus; they swim at the bottom, and the Chronologie shows « 6 Otocinclus ajoutés ».
2. **A BAD species.** Open the scalaire with néons present: ✗ with 2 reasons. « Ajouter quand même » works.
3. **Fresh Aquarium, no Mesure.** The water rows say « pas encore mesuré ». « Faire une analyse » opens the entry.
4. **Partial removal.** Remove 2 of 6; the sheet, the scene and the Chronologie agree.
5. **Custom species.** « Espèce absente ? » with a typed name and the « Cichlidé » family: it swims, and has no verdict.
6. **Display.** Reduced motion (`settings put global animator_duration_scale 0`): the scene is still. DE dark at font scale 1.5: the fiche and sheet aren't clipped.
7. **Pro.** The small widget (Pro faked through the cache) shows the silhouettes.
