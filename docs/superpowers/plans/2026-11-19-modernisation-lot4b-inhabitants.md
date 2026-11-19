# Modernisation lot 4b : Habitants, ajout d'habitant, fiche espèce, recadrage photo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the inhabitant screens and the photo crop the immersive grammar of variant C, and delete the dead code left by lot 4a.

**Architecture:**
- **Shared pieces.** New components go in `ImmersiveComponents.kt` and a new `habitants/SpeciesVisuals.kt`: species thumbnail, fit pill and fit mark, search pill, icon tile. They are reused by the four screens.
- **Habitants.** It stays a `ModalBottomSheet`, opened from the home water and from More. Its rows gain thumbnails, and the header shows a count.
- **Add inhabitant.** The screen and the species fiche get a stateless `AddInhabitantContent` + `AddInhabitantActions`, the same split as lot 4a. They draw under the status bar:
  - the search and the custom form use a 170 dp `WaterHeader`;
  - the fiche uses a 300 dp header with the species drawn large, and a sticky bottom bar.
- **Photo crop.** It keeps its `Dialog` and gestures, on dark water, with a thirds grid and corner marks.
- **Unchanged.** ViewModel, Room, sync and export. No new data is read.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (material3 1.12.0-alpha03), compose resources, kotlin.test + `runComposeUiTest` on JVM, AndroidX Compose UI tests on the Pixel 9a emulator.

**Spec:** `docs/superpowers/specs/2026-11-10-modernisation-immersive-design.md` (§3, §4.7, §5, §6, §7 lot 4).

**Mockup:** `docs/design/21-modernisation/prototype-modernisation.html` on branch `prototype/21-modernisation`, variant C, screens `habitants()`, `addInhabitant()`, `fiche()` and `crop()`.

**Decisions applied (to confirm when reviewing this plan):**
1. **Rule of 17/11/2026:** the new style everywhere, the current interactions everywhere. So:
   - « Retirer » still opens the counter dialog;
   - « Faire une analyse » still opens a Session over the fiche;
   - the custom form, « Détails » on a good fit and the species report link all stay.
2. **Badges and verdicts lose their ✓ ⚠ ✗ symbols** (spec §4.7). They become coloured pills and a coloured card. The meaning stays in words: « Compatible », the reason, « Avec précautions pour Salon ». Check rows show an icon instead of a character. Existing keys are not edited: new keys hold the same text without the symbol, and the old keys are deleted once unused.
3. **Fiche:** the counter and the add button sit in a bar stuck to the bottom (spec §4.7).
4. **Habitants** stays a bottom sheet. Opened from the home, it sits over the scene, as in the mockup. Opened from More, it sits over More.
5. **No Encyclopedia screen exists.** Species are browsed inside « Ajouter un habitant », so spec §4.7's « Encyclopédie » is covered by Task 3.
6. **Photo crop:** dark water, a glass ×, a thirds grid, corner marks, a hint line, then « Utiliser cette photo » in white and « Annuler » as text. Pinch and drag are unchanged.

**Built in lots 1–4a (on `main`, `3e8dd51`):**
- `ImmersiveComponents.kt`:
  - `WaterHeader(aquarium, title, subtitle, modifier, minHeight, photo, leading, action, below)`, `PaperSheetTop`, `GlassIconButton(icon, contentDescription, onClick, modifier)`;
  - `GroupLabel`, `SettingsGroup`, `SettingsRow(label, icon, …, value, supporting, onClick, enabled, labelColor, trailing, showChevron)`;
  - `FilledField(value, onValueChange, label, modifier, keyboardOptions, isError, errorMessage, singleLine, valueStyle, testTag)`;
  - `overlapNext`, `HeroTextBacking`, `FullScreenContentBacking`, `LargeFontScale = 1.3f`, `FloatingNavBarClearance`.
- `StatusBarOverWater.kt`: `StatusBarOverHeader(listState)`, `StatusBarOverWater(overWater)`.
- `home/HomeHero.kt`: `aquariumContextLine(aquarium, inhabitantCount)`. `home/SceneInhabitants.kt`: `inhabitantDrawing(inhabitant, species)`, `rememberEncyclopedia()`.
- The `AquaLogContent` routing in `OnboardingUi.kt`: `framed` draws under the status bar, `inset` starts below it. Add inhabitant gets `inset` today.
- `habitants/SpeciesFiche.kt` helpers: `fitColor(fit)`, `badgeText(assessment)`, `InhabitantCounter(count, onChange, max)`, `CyclingBanner()`, `BackButton(onClick)`, `Reason.label()`.

## Global Constraints

- Palette, fonts, `TankScene` and the species drawings are unchanged.
- **Headers.**
  - Search and custom form: 170 dp. Fiche: 300 dp.
  - Each shows the still `TankScene` of the Aquarium (`animate = false`, no inhabitants), edge to edge under the status bar.
  - Status bar icons are light over the water, and a paper band shows once scrolled (`StatusBarOverHeader`).
- **Contrast.** White text on water reaches 4.5:1 or more: on the fiche title, on the crop backdrop, and on the hint line.
- **Wording.** State is never shown by colour alone: every pill, card and mark carries its words or its `contentDescription`.
- **Size and access.** Targets ≥ 48 dp. Layouts hold at font scale 1.5 in German. Every glass button has a `contentDescription`.
- **Strings.**
  - Every new visible string goes in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`), with terms from `docs/localization-glossary.md`.
  - No literals (`LocalizationContractTest`), no emoji. No existing string value changes.
  - Deleted keys go from all 6 catalogs in the same commit.
- **Unchanged.**
  - No change to Room schema, sync payloads, export format, `HomeViewModel`, `AddInhabitantUiState` or `PhotoPipeline.kt`.
  - Existing ViewModel calls are reused; Back behaves as on `main`.
- **No outlined fields** on these screens: the search becomes `SearchPill`, the custom name becomes `FilledField`.
- **Test tags.** `species-search` stays on the search field. New tags: `add-inhabitant-list`, `species-fiche-list`, `fiche-bottom-bar` and `crop-grid`.
- **Desktop harness.** It is checked by launching it (`./gradlew :ui:run`); it has no tests.
- **Test literals.** The French and German literals in the test sketches below show intent. Assert each catalog's real value, and never change a catalog to match a sketch.

## Review Focus

1. **Inhabitants without a species** (added by hand, or the species was removed from the encyclopedia).
   - The Habitants row shows the family silhouette, tinted, and no scientific-name line.
   - « Retirer » still works.
   
   Pinned in Task 2.
2. **The encyclopedia loading or failing to load.** The search shows its spinner, or `habitants_unavailable`, under the header. The « Espèce absente ? » row stays reachable. Pinned in Task 3.
3. **A fish chosen while the Aquarium is cycling.** `CyclingBanner` still shows on the fiche and in the custom form. Pinned in Tasks 3 and 4.
4. **The sticky bar hides nothing.**
   - The fiche's last item (the report link) scrolls clear above the bar.
   - The bar sits above the gesture bar.
   - At German 150 % the bar's button wraps rather than clips.
   
   Pinned in Task 4.
5. **Two or more problems, and German at 150 %.** The pill reads `habitants_pill_problems` (« 2 problèmes ») and stacks under the name at `fontScale >= LargeFontScale`. Pinned in Task 3.

---

### Task 1: Shared pieces — thumbnails, fit pill and mark, search pill, icon tile, icons, strings

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/habitants/SpeciesVisuals.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/ImmersiveComponents.kt` (add `SearchPill` and `IconTile`; `SettingsRow` uses `IconTile`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/TaskUi.kt:505` and `SessionDetailsUi.kt:267`: replace the inline 34 dp square with `IconTile`.
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/SceneInhabitants.kt` (add `speciesDrawing`)
- Create in `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/icon/`: `Search.kt`, `CircleCheck.kt`, `CircleAlert.kt`, `CircleX.kt`, `CircleMinus.kt`, `X.kt`, `Minus.kt`.
  - Skip any that already exist (check `AquagendaIcons.kt` first).
  - They are Lucide icons (ISC), built like `Calendar.kt`, and registered in `AquagendaIcons.kt`.
- Modify: `ui/src/commonMain/composeResources/values{,-en,-fr,-de,-it,-es}/strings.xml`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/SpeciesVisualsSemanticsTest.kt` (new), `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/home/ScenePhysicsTest.kt`

**Interfaces:**
- Produces:
  - **`fun speciesDrawing(species: Species): Pair<DrawableResource, Color?>`** (`SceneInhabitants.kt`). It follows the same rule as `inhabitantDrawing`: the species' own drawing, else its family silhouette tinted with `species.color`. `inhabitantDrawing` delegates to it when the species is known.
  - **`SpeciesThumb(drawing: DrawableResource, tint: Color?, modifier: Modifier = Modifier)`**
    - A 56 × 44 dp `secondaryContainer` box with 14 dp corners.
    - The drawing is 40 dp wide, centred, fit inside, with a `ColorFilter.tint` when `tint` is set.
    - `contentDescription = null`: the row speaks the name.
  - **`FitPill(text: String, fit: Fit, modifier: Modifier = Modifier)`**
    - A 28 dp min-height pill, 14 dp corners, 13 sp SemiBold text.
    - Colours: the text is `fitColor(fit)`. The fill is `colorScheme.secondaryContainer` for GOOD, `levelContainerColor(ReadingLevel.ATTENTION)` for CAUTION and `levelContainerColor(ReadingLevel.ALERT)` for BAD, falling back to `surfaceVariant` when null. These are the containers the Session tiles use.
    - It has no symbol, and its text is its semantics.
  - **`FitMark(status: CriterionStatus, modifier: Modifier = Modifier)`**
    - A 24 dp icon: `CircleCheck` in `primary` for OK, `CircleAlert` in the caution colour for CAUTION, `CircleX` in the alert colour for PROBLEM, `CircleMinus` in `onSurfaceVariant` for NOT_MEASURED and UNIT_NOT_COMPARED.
    - It has `contentDescription = null`, because the row's cleared semantics already says it. `CriterionStatus` is `OK, CAUTION, PROBLEM, NOT_MEASURED, UNIT_NOT_COMPARED` (shared `Compatibility.kt`).
  - **`fitVerdict(fit: Fit, aquariumName: String): String`** (`@Composable`) returns `habitants_fit_good|caution|bad`.
  - **`fitPillText(assessment: SpeciesAssessment): String`** (`@Composable`) replaces `badgeText`. The rules are unchanged (problems ≥ 2 → plural; BAD/CAUTION with a reason → `Reason.label()`; otherwise good), but use `habitants_pill_good` and `habitants_pill_problems`. For BAD and CAUTION the pill text is the reason label itself.
  - **`SearchPill(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, testTag: String? = null)`** (`ImmersiveComponents.kt`)
    - Visual: 48 dp tall, 24 dp corners, `surface` fill, 1 dp `outlineVariant` border (2 dp `primary` when focused), and a leading 20 dp `Search` icon.
    - It is a single-line `BasicTextField` with `ImeAction.Search`.
    - Semantics: the placeholder is drawn inside the `decorationBox` while the query is empty, with no `contentDescription`. This is the #45 pattern, so the editable node has the placeholder as its text and TalkBack reads it.
  - **`IconTile(icon: ImageVector, modifier: Modifier = Modifier)`** (`ImmersiveComponents.kt`): the 34 dp `secondaryContainer` square with 11 dp corners and an 18 dp `primary` icon, `contentDescription = null`. `SettingsRow`, `TaskRow` and `NoteRow` use it instead of their inline copies.
  - **`internal val CropBackdrop = Color(0xFF0E2F2A)`** (`ImmersiveComponents.kt`).
- New strings:

| key | values / -en | -fr | -de | -it | -es |
|---|---|---|---|---|---|
| `habitants_pill_good` | the catalog's `habitants_badge_good` without its leading symbol and space | ← same rule | ← | ← | ← |
| `habitants_pill_problems` (plurals) | the catalog's `habitants_badge_problems` items without `✗ ` | ← | ← | ← | ← |
| `habitants_fit_good` | the catalog's `habitants_verdict_good` without its leading symbol and space | ← | ← | ← | ← |
| `habitants_fit_caution` | `habitants_verdict_caution` without symbol | ← | ← | ← | ← |
| `habitants_fit_bad` | `habitants_verdict_bad` without symbol | ← | ← | ← | ← |
| `habitants_count_in_aquarium` | %1$d in %2$s | %1$d dans %2$s | %1$d in %2$s | %1$d in %2$s | %1$d en %2$s |
| `photo_crop_hint` | Pinch to zoom, drag to frame. This square is exactly what will be saved. | Pincez pour zoomer, glissez pour cadrer. Ce carré est exactement ce qui sera enregistré. | Zum Zoomen auseinanderziehen, zum Ausrichten verschieben. Genau dieses Quadrat wird gespeichert. | Pizzica per ingrandire, trascina per inquadrare. Questo quadrato è esattamente ciò che verrà salvato. | Pellizca para ampliar, arrastra para encuadrar. Este cuadrado es exactamente lo que se guardará. |

  The old `habitants_badge_*` and `habitants_verdict_*` keys stay until Task 6.

- [ ] **Step 1: Write the failing tests**

```kotlin
// SpeciesVisualsSemanticsTest (French locale, as in MoreSemanticsTest)
@Test fun `fit pill says its words without a symbol`()        // FitPill("Compatible", Fit.GOOD) → onNodeWithText("Compatible") exists; no node text contains "✓", "⚠" or "✗"
@Test fun `pill text for two problems is the plural`()        // assessment with 2 problems → fitPillText == "2 problèmes"
@Test fun `pill text for a caution is its reason`()           // CAUTION + one reason → fitPillText == that reason's label
@Test fun `verdict names the aquarium without a symbol`()     // fitVerdict(Fit.CAUTION, "Salon") == "Avec précautions pour Salon"
@Test fun `search pill is named by its placeholder when empty`() // SearchPill("", …, "Rechercher une espèce…", testTag = "species-search") → onNode(hasSetTextAction() and hasText("Rechercher une espèce…")) exists; performTextInput("oto") calls onQueryChange("oto"); node tagged species-search has height ≥ 48.dp
@Test fun `species thumb is silent`()                         // SpeciesThumb(...) inside a row → no contentDescription on the image node
// ScenePhysicsTest
@Test fun `white text keeps 4·5 to 1 on the crop backdrop`()           // contrast(White, CropBackdrop) ≥ 4.5
@Test fun `hint text keeps 4·5 to 1 on the crop backdrop`()            // contrast(White.copy(alpha = .85f) over CropBackdrop, CropBackdrop) ≥ 4.5
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :ui:jvmTest --tests '*SpeciesVisualsSemanticsTest*' --tests '*ScenePhysicsTest*'`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Add the icons, the strings and the components.** Switch `SettingsRow`, `TaskRow` and `NoteRow` to `IconTile`.

- [ ] **Step 4: Run the full UI suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS, including `LocalizationContractTest`, `ImmersiveSemanticsTest`, `TaskSemanticsTest` and `SessionDetailsSemanticsTest`.

- [ ] **Step 5: Commit** — `feat(ui): species thumbnail, fit pill and mark, search pill and icon tile for lot 4b`

---

### Task 2: Habitants sheet with thumbnails

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/home/HabitantsSheet.kt`
- Modify the callers: `home/TankHome.kt:331-343` and `MoreUi.kt:342-354` (pass the Aquarium name).
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/HabitantsSheetSemanticsTest.kt` (new)

**Interfaces:**
- Consumes: `SpeciesThumb`, `inhabitantDrawing`, `habitants_count_in_aquarium` (Task 1).
- Produces: **`HabitantsSheet(groups: List<Inhabitant>, encyclopedia: List<Species>, language: String, aquariumName: String, onRemove: (inhabitantId: String, count: Int, onFailed: () -> Unit) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit)`**. `aquariumName` is new. Extract the sheet's body as **`internal fun HabitantsSheetBody(...)`**, with the same parameters minus `onDismiss`, so a JVM test can render it without a `ModalBottomSheet`.

Layout, top to bottom, inside the existing `ModalBottomSheet`:
1. **Header row:** the title `habitants_title` (Fraunces 24 sp, a heading) and, at the right, `habitants_count_in_aquarium(total, aquariumName)` in `onSurfaceVariant`. `total` is the sum of the group counts. At `fontScale >= LargeFontScale` the count goes under the title.
2. **One row per group**, at least 64 dp tall, separated by 1 dp `outlineVariant` lines:
   - a `SpeciesThumb`;
   - a column with « name × n » as `habitants_present_group` (15 sp SemiBold) and the italic scientific name (12 sp, `onSurfaceVariant`). The scientific line is shown only when the species is known.
   - the « Retirer » `TextButton`: text `habitants_remove`, `contentDescription` `habitants_remove_named`, at least 48 dp.
3. The `habitants_remove_failed` text, unchanged.
4. **Add button:** a full-width filled `Button`, 48 dp, with a leading `Plus` icon and the text `habitants_add_one`.

The remove `AlertDialog` with `InhabitantCounter` is unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
// HabitantsSheetSemanticsTest (French)
@Test fun `header counts the inhabitants in the aquarium`()   // groups cherry×10, oto×4 → text "14 dans Salon"
@Test fun `row speaks name and count and scientific name`()    // text "Crevette red cherry × 10" and "Neocaridina davidi" displayed
@Test fun `inhabitant without species has no scientific line`() // custom group (species null) → its name × n shown; no node with italic scientific text for it (assert node count of scientific names == known species count)
@Test fun `remove is a named 48 dp button per row`()           // onNodeWithContentDescription("Retirer des Crevette red cherry…") (habitants_remove_named) height ≥ 48.dp; click → dialog with "Retirer 1" (habitants_remove_confirm) shown
@Test fun `add button opens add`()                             // click "Ajouter un habitant" → onAdd called
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :ui:jvmTest --tests '*HabitantsSheetSemanticsTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement the layout above.**

- [ ] **Step 4: Run** `./gradlew :ui:jvmTest`. Expected: PASS.

- [ ] **Step 5: Commit** — `feat(habitants): the Habitants sheet with thumbnails and a count`

---

### Task 3: Add inhabitant — search and custom form under the water

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/habitants/AddInhabitantScreen.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt`: the `addInhabitant` branch gets `framed` instead of `inset`, and the routing comment lists it with the screens drawn under the status bar.
- Modify: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/FieldAndPagerSemanticsTest.kt` (`SpeciesSearchField` now wraps `SearchPill`; keep the tag assertion).
- Modify: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/AddInhabitantFlowTest.kt`, selectors only: badge text `habitants_pill_good`.
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/AddInhabitantSemanticsTest.kt` (new)

**Interfaces:**
- Consumes: Task 1's pieces, `WaterHeader`, `GlassIconButton`, `GroupLabel`, `SettingsGroup`, `SettingsRow`, `FilledField`, `aquariumContextLine`, `StatusBarOverHeader`.
- Produces:
  - **`class AddInhabitantActions(val onBack: () -> Unit, val onQueryChange: (String) -> Unit, val onSelectKind: (SpeciesKind?) -> Unit, val onOpenFiche: (String) -> Unit, val onOpenCustom: () -> Unit, val onCountChange: (Int) -> Unit, val onCustomNameChange: (String) -> Unit, val onSelectFamily: (DrawingFamily) -> Unit, val onConfirm: () -> Unit, val onMeasure: () -> Unit, val onReportSpecies: (speciesId: String, name: String) -> Unit)`**
    - Each lambda maps one to one to the `HomeViewModel` call used today.
    - Match each lambda's parameter type to the ViewModel function it wraps (e.g. `selectSpeciesKind`).
  - **`internal fun AddInhabitantContent(add: AddInhabitantUiState, setup: AquariumSetup, readings: List<LatestReading>, inhabitants: List<Inhabitant>, present: List<PresentGroup>, encyclopedia: List<Species>, language: String, actions: AddInhabitantActions, modifier: Modifier = Modifier)`**
    - It dispatches to the fiche, the custom form or the search, as `AddInhabitantScreen` does today.
    - `AddInhabitantScreen(add, state, viewModel, modifier)` keeps its signature. It builds the data and the actions, then calls it.

**Search layout:** one `LazyColumn` tagged `add-inhabitant-list`, with `StatusBarOverHeader(listState)`.
1. **Header:** `WaterHeader(aquarium, title = habitants_add_one, subtitle = aquariumContextLine(aquarium, inhabitant total), leading = GlassIconButton(ArrowLeft, back → onBack))` with `overlapNext(30.dp)`.
2. `PaperSheetTop`.
3. `SearchPill(query, …, placeholder = habitants_search_hint, testTag = "species-search")`, with 16 dp side margins. `SpeciesSearchField` becomes a thin wrapper on it.
4. **Kind chips:** a `FlowRow` of the existing `FilterChip`s per `SpeciesKind`. The selected chip is filled `primary`.
5. **Loading:** `habitants_unavailable` or the spinner, unchanged.
6. **Groups:** per non-empty group, `GroupLabel(habitants_group_*)` then a `SettingsGroup` of species rows. Each row:
   - is at least 62 dp tall, one merged `Role.Button` that opens the fiche;
   - shows a `SpeciesThumb(speciesDrawing(species))`, the name (15 sp SemiBold), the italic scientific name, and a `FitPill(fitPillText(assessment), fit)`;
   - at `fontScale >= LargeFontScale`, stacks the pill under the names.
7. **Missing species:** a `SettingsGroup` with one `SettingsRow(habitants_missing, icon = Plus, onClick = onOpenCustom)`. This replaces the last `MoreRow` call.
8. A bottom spacer of 24 dp plus the navigation bar.

**Custom form layout:** a scrolling `Column`.
- The header is `WaterHeader(title = habitants_custom_title, subtitle = aquarium.name, leading = back)`, then `PaperSheetTop`.
- The name is a `FilledField(label = habitants_custom_name, isError = showCustomError, errorMessage = habitants_custom_error)`.
- The family `FilterChip`s, `CyclingBanner` (same condition as today), the counter (`habitants_count_title` as a `GroupLabel` + `InhabitantCounter`) and the full-width `Button(habitants_custom_add)` follow, then the failure text.

- [ ] **Step 1: Write the failing tests**

```kotlin
// AddInhabitantSemanticsTest (French; Salon, 120 L, Planté / crevette; species fixtures for a GOOD, a CAUTION and a BAD fit)
@Test fun `header names the screen and the aquarium context`()      // heading "Ajouter un habitant"; text starting "120 L"
@Test fun `back is a glass button`()                                 // onNodeWithContentDescription("Retour") click → onBack called
@Test fun `groups are labelled and rows open the fiche`()            // headings "Vont bien dans ce bac", "Avec précautions", "Déconseillés"; click row "Rasbora galaxy" → onOpenFiche("…galaxy id…")
@Test fun `row speaks name scientific name and pill as one button`() // one node with Role.Button whose merged text contains "Rasbora galaxy", "Danio margaritatus", "Compatible"
@Test fun `two problems read as a plural pill`()                     // BAD fixture with 2 problems → row text contains "2 problèmes"
@Test fun `pill stacks under the name at large fonts`()              // Density(fontScale = 1.5f), German locale: pill node top ≥ name node bottom
@Test fun `missing species row opens the custom form`()              // performScrollToNode on tag add-inhabitant-list to "Espèce absente ? L'ajouter à la main", click → onOpenCustom
@Test fun `encyclopedia failure keeps the custom row`()              // add.loadFailed = true → habitants_unavailable text shown, and the missing-species row exists
@Test fun `custom name is a filled field with its error`()           // add.custom = true, showCustomError = true → editable node named "Nom de l'espèce" (habitants_custom_name) and error text habitants_custom_error shown
@Test fun `cycling banner shows for a fish in a cycling aquarium`()  // custom form, profile CYCLING, family TETRA → habitants_cycling text shown
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :ui:jvmTest --tests '*AddInhabitantSemanticsTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement the split, the two layouts and the routing change.**

- [ ] **Step 4: Run** `./gradlew :ui:jvmTest`. Expected: PASS, including `FieldAndPagerSemanticsTest`, `BackNavigationTest` and `LocalizationContractTest`.

- [ ] **Step 5: Device check** — `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.AddInhabitantFlowTest`. Expected: PASS. It may still fail on the fiche's add button until Task 4; if so, note it in the report and leave it for Task 4.

- [ ] **Step 6: Commit** — `feat(habitants): add an inhabitant under the water, with thumbnails and fit pills`

---

### Task 4: Species fiche — large drawing on the water, verdict card, fact tiles, sticky bar

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/habitants/SpeciesFiche.kt`
- Modify: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/AddInhabitantFlowTest.kt`, selectors only: the add button is clicked directly, with no `scrollToNode`, because it sits in the bar.
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/SpeciesFicheSemanticsTest.kt` (new)

**Interfaces:**
- Consumes: `AddInhabitantActions` (Task 3); `speciesDrawing`, `FitMark`, `fitVerdict` (Task 1).
- Produces: **`SpeciesFiche(species, add, setup, readings, inhabitants, present, encyclopedia, language, actions: AddInhabitantActions, modifier)`**. Only `viewModel` is replaced by `actions`; the other parameters are unchanged.

**Layout:** a `Box`. The content is a `LazyColumn` tagged `species-fiche-list`, with `StatusBarOverHeader`, and a bottom bar tagged `fiche-bottom-bar` aligned to the bottom.

1. **Header (300 dp, `overlapNext(30.dp)`):**
   - the still `TankScene` (no inhabitants), with the species drawing 200 dp wide, centred, fit inside;
   - a `GlassIconButton(ArrowLeft, back)` under the status bar;
   - at the bottom, the name (Fraunces 32 sp SemiBold white, a heading) and the italic scientific name (14 sp, white at 85 %).
   
   The name block sits on a backing drawn like `WaterFullScreen`'s: a 48 dp fade from transparent to `FullScreenContentBacking`, then solid. Its padding keeps text out of the fade. The drawing's `contentDescription` is null.
2. `PaperSheetTop`.
3. **Verdict card:** one merged node. 20 dp corners. Its fill and text colour are `FitPill`'s for the same fit. Contents:
   - a 24 dp `FitMark`-style icon for the fit, decorative;
   - `fitVerdict(fit, aquarium.name)` in 15 sp SemiBold;
   - the existing explanation sentence.
4. `CyclingBanner`, under the same condition as today.
5. **Facts:** three tiles in one row — size (`habitants_fact_size`), lifespan (`habitants_fact_lifespan`) and group (`habitants_fact_group`). Each tile is 16 dp corners, `surface` with a 1 dp `outlineVariant` border, showing the existing fact string.
   - For a plant, only the size tile shows, as today.
   - At `fontScale >= LargeFontScale` the tiles stack in one column.
6. **Comparison:** `GroupLabel(habitants_compared)`, then the existing « Détails » toggle for a GOOD fit. Then a `SettingsGroup` of `CheckRow`s, each:
   - at least 56 dp tall;
   - a `FitMark(status)`, the criterion label with its range under it (`onSurfaceVariant`), and the value at the right;
   - the same cleared `contentDescription` as today.
7. The measure button (`habitants_measure`, when anything is not measured), unchanged.
8. The report link (`feedback_report_species`), unchanged. It is followed by a spacer as tall as the bottom bar, so the last item scrolls clear of it.

**Bottom bar:** `surfaceContainer`, a 1 dp top `outlineVariant` line, padding 12/16 dp, `navigationBarsPadding()`.
- **Top line:** `habitants_count_minimum` when `minGroup > 1`, 12 sp `onSurfaceVariant`.
- **Main row:** `InhabitantCounter(add.count, actions.onCountChange)`, then the add `Button` with weight 1 and min 48 dp. Its text is `habitants_add_anyway` when BAD, else `habitants_add_button(count, name)`, with `TextAlign.Center` and wrapping allowed.
- **Failure:** `habitants_add_failed` under the row when `saveFailed`.
- The `habitants_count_title` heading is dropped: the counter's buttons are already labelled.

- [ ] **Step 1: Write the failing tests**

```kotlin
// SpeciesFicheSemanticsTest (French; Néon bleu over Salon at pH 7.8 → CAUTION)
@Test fun `title is a heading on the water with back`()              // heading "Néon bleu"; "Paracheirodon innesi" shown; contentDescription "Retour" click → onBack
@Test fun `verdict card says its fit in words`()                     // one node whose merged text contains "Avec précautions pour Salon" and the explanation; no text contains "⚠"
@Test fun `check row keeps its spoken summary`()                     // node with contentDescription containing "pH" and "7,8" exists (same cleared semantics as main)
@Test fun `add bar stays on screen while the list scrolls`()         // performScrollToIndex(last) on species-fiche-list → node tagged fiche-bottom-bar still displayed; button "Ajouter 10 Néon bleu" displayed and enabled; click → onConfirm
@Test fun `report link scrolls clear of the bar`()                   // scroll to "Signaler une erreur" (feedback_report_species) → its bounds.bottom ≤ bar's bounds.top
@Test fun `bad fit says add anyway`()                                // BAD fixture → button text habitants_add_anyway
@Test fun `bar button wraps at large fonts`()                        // German, fontScale 1.5 → button text node is displayed and not clipped (its height ≥ one line, its width ≤ bar width)
@Test fun `fact tiles stack at large fonts`()                        // fontScale 1.5 → second tile's top ≥ first tile's bottom
@Test fun `cycling banner shows on a fish fiche while cycling`()     // profile CYCLING, fish species → habitants_cycling shown
```

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :ui:jvmTest --tests '*SpeciesFicheSemanticsTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement the layout above.**

- [ ] **Step 4: Run** `./gradlew :ui:jvmTest`. Expected: PASS.

- [ ] **Step 5: Device check** — run `AddInhabitantFlowTest`, `BackNavigationFlowTest` and `LocalizedAccessibilityFlowTest` with `-Pandroid.testInstrumentationRunnerArguments.class=…,…`. Expected: PASS.

- [ ] **Step 6: Commit** — `feat(habitants): species fiche with its drawing on the water and a sticky add bar`

---

### Task 5: Photo crop on dark water

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/photo/PhotoCropScreen.kt`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/PhotoCropSemanticsTest.kt` (new)

**Interfaces:**
- Consumes: `CropBackdrop`, `GlassIconButton`, the `X` icon, `photo_crop_hint` (Task 1).
- Produces: `PhotoCropScreen(image, onConfirm, onCancel)`, with an unchanged signature. The gestures and `applyGesture` / `cropFor` are unchanged.

**Layout:** inside the existing full-width `Dialog`, a `Box` filled with `CropBackdrop`, then `StatusBarOverWater(true)` and `safeDrawingPadding()`. In a column:
1. **Top row:** `GlassIconButton(X, contentDescription = cancel, onClick = onCancel)` and the title `photo_crop_title` (Fraunces 22 sp white, a heading).
2. **Viewport:** the square viewport (weight 1, centred, as today), with the existing `Canvas` and its `photo_crop_preview` description. Over it, a `crop-grid` overlay that draws:
   - a 2 dp white border;
   - two vertical and two horizontal 1 dp lines in white at 40 %, at one and two thirds;
   - four 26 dp corner marks, 5 dp thick, in white.
   
   The overlay has no semantics and does not consume gestures.
3. **Hint:** `photo_crop_hint`, 14 sp, white at 85 %, centred.
4. **Confirm:** a white `Button` with `primary` text `photo_crop_confirm`, full width, 52 dp. It calls `onConfirm` as today.
5. **Cancel:** a `TextButton` with white text `cancel`, 48 dp. It calls `onCancel`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// PhotoCropSemanticsTest (French; a 400×300 ImageBitmap)
@Test fun `confirm sends the centred crop when untouched`()     // click "Utiliser cette photo" → onConfirm(centredCrop(400, 300)) (the same value main sends)
@Test fun `cancel and the glass close both cancel`()            // click "Annuler" → onCancel; click contentDescription "Annuler" (glass ×) → onCancel again (two calls)
@Test fun `grid overlays the preview without semantics`()       // node tagged crop-grid exists; its bounds equal the preview node's bounds; it has no contentDescription
@Test fun `hint and title are shown`()                          // heading "Recadrer la photo"; text starting "Pincez pour zoomer"
```

Use `onAllNodesWithText("Annuler")` and `onAllNodesWithContentDescription("Annuler")` to tell the text button from the glass button.

- [ ] **Step 2: Run them to verify they fail** — `./gradlew :ui:jvmTest --tests '*PhotoCropSemanticsTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement the layout above.**

- [ ] **Step 4: Run** `./gradlew :ui:jvmTest`. Expected: PASS, including `HomeViewModelPhotoTest` and `PhotoPipelineTest`.

- [ ] **Step 5: Commit** — `feat(photo): photo crop on dark water with a thirds grid`

---

### Task 6: Dead code from lots 4a and 4b

**Files:**
- Modify: `ui/src/commonMain/composeResources/values{,-en,-fr,-de,-it,-es}/strings.xml`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/theme/CarnetComponents.kt` (delete `ReadingValue`, lines 116-174; keep `levelDescription` and `trendIcon`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/MoreUi.kt` (delete `MoreRow`, lines 428-473)
- Modify: `habitants/SpeciesFiche.kt` (delete `badgeText`, and `BackButton` if it has no caller left)

**Interfaces:** none produced.

Delete from all 6 catalogs:
- the 9 keys left by lot 4a: `account_title`, `more_section_plan`, `more_section_tasks`, `pending_tasks`, `portable_data_title`, `session_action_detail`, `session_date`, `session_event_detail`, `view_free_and_pro`;
- the keys this lot made unused: `habitants_badge_good`, `habitants_badge_caution`, `habitants_badge_bad`, the plurals `habitants_badge_problems`, `habitants_verdict_good`, `habitants_verdict_caution`, `habitants_verdict_bad`, `habitants_add_title`, and `habitants_count_title` if no caller is left.

Delete only keys that `rg 'Res\.(string|plurals)\.<key>\b' ui androidApp` finds no reference for.

- [ ] **Step 1: Check each key and composable has no reference left.** Run `rg` for every name above. Expected: matches only in the catalogs, or in the definition itself.

- [ ] **Step 2: Delete them.**

- [ ] **Step 3: Run** `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:assembleDebug :androidApp:lintDebug`. Expected: BUILD SUCCESSFUL, and lint warnings ≤ main's 44.

- [ ] **Step 4: Commit** — `chore: remove strings and composables left unused by lots 4a and 4b`

---

### Task 7: Whole-branch checks and captures

**Files:**
- Create: `docs/design/21-modernisation/lot4b-captures/` (PNG)

- [ ] **Step 1: Full suites**

  Run:
  - `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:assembleDebug :androidApp:lintDebug`;
  - `./gradlew :androidApp:connectedDebugAndroidTest`, the whole device suite on emulator-5554.

  Expected: PASS. A failure is fixed in the task's own code, never by weakening an assertion.

- [ ] **Step 2: Desktop harness**

  Run `./gradlew :ui:run`. Open Habitants, add an inhabitant, a species fiche and, if a file picker is available, the photo crop. Then close it.
  Expected: no crash.

- [ ] **Step 3: Captures**

  On the Pixel 9a emulator, capture `main` (`3e8dd51`) as « before » and this branch as « after ».
  - **Seed:** Salon, 120 L, Planté / crevette, with pH 7,8 measured, plus 10 Crevette red cherry, 4 Otocinclus and one inhabitant added by hand.
  - **Screens** (slugs):
    - `habitants`: the sheet from the home water;
    - `ajout`: the search with all three groups;
    - `ajout-main`: the custom form;
    - `fiche`: Néon bleu, a caution fit;
    - `fiche-bas`: Néon bleu scrolled to the end;
    - `recadrage`: the crop, with any picture pushed to the emulator.
  - **Variants:** « before » in FR light; « after » in FR light and FR dark, plus DE at 150 % for `ajout`, `fiche` and `habitants`.
  - **File names:** `<slug>-<before|after>-<fr-light|fr-dark|de-150>.png`, downscaled with `sips -Z 1200`.
  - **Check every after capture:**
    - white text on water is readable;
    - no label is cut mid-word;
    - no ✓ ⚠ ✗ remains;
    - the bar hides nothing on `fiche-bas`;
    - the grid and corners show on `recadrage`.

- [ ] **Step 4: Commit** — `docs: lot 4b before/after captures`
