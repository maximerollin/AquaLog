# Modernisation lot 5 : Onboarding + Offre — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the six onboarding screens and the Pro offer the immersive grammar of variant C. This includes the range sheet that replaces the four outlined fields per Parameter.

**Architecture:**
- **New package `onboarding`.** The screens are stateless `*Content` composables fed by an `OnboardingActions` class of lambdas, the same split as `AccountActions` (lot 4a). `AquaLogContent` builds the actions from `HomeViewModel`.
- **Frame.** Every step after the welcome is a `LazyColumn`:
  - a large `OnboardingHeader` (the tank scene, a progress bar, a title on the water);
  - `PaperSheetTop`, then the content;
  - a bottom bar on paper that holds the primary button.
- **Welcome.** It is full frame on the water, like the account screens.
- **Parameters.**
  - One card of rows. Each row has a drag handle, the name, the range pill and a switch. Tapping a row opens `RangeSheet`.
  - The drag calls the existing `moveParameter`. TalkBack gets « move up / down » as custom actions.
- **Offer.** One `PaywallContent` serves both the onboarding and More. The card selection is local UI state, and « Choisir Pro » calls the existing annual or lifetime purchase.
- **Unchanged.** ViewModel, Room, billing, sync and export. No new data is read or written.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 (material3 1.12.0-alpha03), compose resources, kotlin.test + `runComposeUiTest` on JVM, AndroidX Compose UI tests on the Pixel 9a emulator.

**Spec:** `docs/superpowers/specs/2026-11-10-modernisation-immersive-design.md` (§3, §4.6, §5, §6, §7 lot 5).

**Mockups:**
- `docs/design/21-modernisation/prototype-modernisation.html` on branch `prototype/21-modernisation`, variant C: screens `onboarding()`, `goal()`, `aquarium()`, `params()`, `paramEdit()` and `offer()`.
- The choices of 19/11 are on the page « Lot 5 · trois choix »: https://claude.ai/artifact/1pK4iC1hAnkTQMjmm28p33

**Decisions (Maxime, 19/11/2026):**
1. **Offer: variant A.** Two plan cards (Annuel, À vie) to select, then one « Choisir Pro » button.
   - Annuel is selected first, and its card carries a « Recommandé » badge. The badge is not « Le plus populaire », because no sales data supports that claim.
   - The full terms of the selected plan stay written under the cards (`pro_offer_summary` / `pro_lifetime_offer_summary`).
2. **Parameters: variant B.**
   - A drag handle reorders the rows. TalkBack keeps « Déplacer … vers le haut / bas » as custom actions on each row (existing strings).
   - Tapping a row opens the range sheet.
3. **Welcome: variant B.** It shows six decorative dots (the welcome plus 5 steps), the first one long, hidden from TalkBack.

**Rulings to confirm when reviewing this plan:**
4. **No back button on the steps.** The mockup puts a glass back button left of the progress bar. Onboarding has no back control today, and `backStep` returns null for it (rule of 17/11: current interactions), so the button is not added.
5. **Motion.** Plants sway on the Welcome, Objective and Offer water (large headers, spec §3). The Aquarium, Parameters and Practice water is still, because those are input screens (spec §2 anti-objectives).
   - No step shows inhabitants: there are none yet.
6. **The range sheet keeps typing.** Min and max are editable numbers between − and +, so what works today still works. Unit is a `FilledField`; decimals is a 0–3 stepper.
   - Changes apply live to the ViewModel, as the fields do today. « Valider » closes the sheet.
7. **Practice keeps its first three active Parameters** (`take(3)`, as today), shown as Session `MeasureTile`s with the « Suivant » chain.
8. **Offer opened from More.** It has no progress bar, and its bottom text button stays « Retour aux paramètres » (`back_to_settings`). System Back still closes it (`BackStep.ClosePaywall`).
9. **Pro benefits** are four lines: the three in today's `pro_plan_description` (several Aquariums, history beyond 90 days, before/after analysis), plus the home-screen tracking widgets (Maxime, 19/11; the widgets show « Pro requis » without Pro, `WidgetCommon.kt`). Nothing is promised that the app does not do.
10. **Volume unit labels.** The segmented control uses the existing « Litres » / « gallons américains », not the mockup's « L / gal US », so TalkBack reads words rather than abbreviations.

**Built in lots 1–4b (on `main`, `a8eb49c`):**
- `ui/theme/ImmersiveComponents.kt`:
  - `WaterHeader`, `PaperSheetTop`, `GlassIconButton`, `overlapNext`, `HeroTextBacking`, `HeroText`;
  - `FilledField(value, onValueChange, label, modifier, keyboardOptions, isError, errorMessage, singleLine, valueStyle, testTag)`;
  - `PeriodSegmented(labels, selectedIndex, onSelect, modifier)`, `IconTile`, `WaterPrimaryButtonText` (#14675E);
  - `FullScreenContentBacking`, `Modifier.fullScreenBacking(fade)`, `WaterFullScreen`, `LargeFontScale = 1.3f`.
- `StatusBarOverWater.kt`: `StatusBarOverHeader(listState)`, `StatusBarOverWater(overWater)`.
- `home/TankScene.kt`: `TankScene(aquarium, inhabitants, modifier, decorPlants, animate)`. It reads no field of `aquarium`, so a preview Aquarium is enough.
- `SessionTiles.kt`:
  - `MeasureTile(definition, input, profile, formatter, imeAction, onValueChange, onNext, onDone, modifier)`, `MeasureTileRow(tiles, columns, modifier)`;
  - `sessionColumns(fontScale, maxWidth)`, `sessionImeAction(index, lastIndex)`, `ParameterDefinition.rangeText(formatter)`.
- `HomeViewModel.kt`:
  - `ParameterEditorState(parameter, isActive, unit, precision, indicativeMinimum, indicativeMaximum)` with `toDraft(position)`;
  - `displayInput(decimalSeparator)`, `regionalDecimalSeparator()`;
  - every onboarding call listed in Task 2's `OnboardingActions`.
- Shared: `parseDecimalInput(input)` in `RapidSession.kt`, `ParameterDefinition`, `AquariumProfile`, `VolumeUnit`, `AnnualProOffer(productId, formattedPrice, trialDays, billingDateEpochMillis)`, `LifetimeProOffer(productId, formattedPrice)`.
- `OnboardingUi.kt`:
  - the `AquaLogContent` routing (`framed` draws under the status bar, `inset` starts below it);
  - `PaywallScreen`, and the old step screens this lot replaces;
  - `ProMessage.resourceId()`, `Aquarium.formattedVolume()`, `BuiltInParameter.label()`, `AnnualProOffer.localizedBillingDate(formatter)`.

## Global Constraints

- Palette, fonts and `TankScene` are unchanged.
- **Headers.** Each draws the `TankScene` of a preview Aquarium (no inhabitants) edge to edge under the status bar, with light status-bar icons (`StatusBarOverHeader`), and a dark gradient at the top. Heights:

  | Screen | Header |
  |---|---|
  | Welcome | full screen |
  | Objective, Aquarium | 300 dp |
  | Parameters | 200 dp |
  | Practice | 170 dp |
  | Offer | 330 dp |

  Each header grows with large fonts (`heightIn(min = …)`).
- **Progress.** Five white segments on the water (`OnboardingProgress`), filled up to the step: Objective 1, Aquarium 2, Parameters 3, Practice 4, Offer 5. It is one node for TalkBack: « Étape n sur 5 ».
- **Contrast.** White text on the water reaches 4.5:1 or more, through the gradient or the `FullScreenContentBacking` behind the welcome text (ScenePhysicsTest).
- **Size and access.**
  - Targets ≥ 48 dp. Every icon-only control has a `contentDescription`.
  - Layouts hold at font scale 1.5 in German: titles wrap and never cut, and rows stack.
- **Fields.** No outlined field and no `RadioButton` remains on these screens. Fields are `FilledField`; choices are selectable cards or `PeriodSegmented`.
- **Strings.**
  - Every new visible string goes in all 6 catalogs (`values`, `-en`, `-fr`, `-de`, `-it`, `-es`), with the terms of `docs/localization-glossary.md`: Aquarium, Session, Plage indicative / Richtbereich / Intervallo indicativo / Rango indicativo.
  - No literals (`LocalizationContractTest`), and no emoji.
  - No existing string value changes. Keys left unused are deleted from all 6 catalogs in Task 7.
- **Unchanged.**
  - No change to Room, sync payloads, export format, billing or `HomeViewModel` (beyond reading it).
  - Back behaves as on `main`.
- **Test tags.**
  - Existing tags stay where the device tests use them.
  - New: `onboarding-list`, `onboarding-primary`, `onboarding-progress`, `welcome-dots`, `parameter-row-<storageValue>`, `parameter-handle-<storageValue>`, `range-sheet`, `range-min`, `range-max`, `practice-<storageValue>`, `plan-annual`, `plan-lifetime`, `choose-pro`.
- **Desktop harness.** It is checked by launching it (`./gradlew :ui:run`); it has no tests.
- **Test literals.** The French literals in the test sketches show intent. Assert each catalog's real value, and never change a catalog to match a sketch.

## Review Focus

1. **The offer is partial or missing.**
   - With only the annual offer, only the annual card shows, selected. With only lifetime, the same for lifetime.
   - With neither, the error and « Réessayer » show, there are no cards and no « Choisir Pro », and « Continuer gratuitement » stays.
   - While loading, a spinner sits where the cards go.

   Pinned in Task 6.
2. **A purchase in flight.**
   - « Choisir Pro » is disabled in AWAITING_PLAY_CONFIRMATION, PENDING, VERIFYING and ACTIVE, as both buttons are today.
   - The `ProMessage` line shows under the cards.
   - « Continuer avec Pro » replaces « Continuer gratuitement » once ACTIVE in onboarding.

   Pinned in Task 6.
3. **A range left invalid in the sheet** (min above max, a blank unit, an unparsable number).
   - The sheet shows the error under the fields.
   - The row's pill reads « Plage à revoir ».
   - « Essayer une mini-Session » still shows `invalid_parameters` and does not advance, as today.

   Pinned in Task 4.
4. **The last active Parameter switched off.** The switch still works, as today. « Essayer une mini-Session » shows `invalid_parameters` and does not advance. Pinned in Task 4.
5. **German at 150 % on the Aquarium step with the keyboard open.**
   - The name and volume fields stay above the keyboard.
   - The bottom button rides above it (`imePadding`).
   - The live name on the water wraps on two lines and is ellipsized after that.

   Pinned in Task 3.

---

### Task 1: Shared onboarding pieces — header, progress, dots, preview data, icons, strings

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/onboarding/OnboardingFrame.kt`
- Create in `ui/src/commonMain/kotlin/com/maximerollin/aqualog/ui/icon/`: `GripVertical.kt`, `Lock.kt`, `Leaf.kt`. They are Lucide icons (ISC), built like `Check.kt`.
- Modify: `ui/src/commonMain/composeResources/values{,-en,-fr,-de,-it,-es}/strings.xml`
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/OnboardingFrameSemanticsTest.kt` (new), `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/onboarding/OnboardingPreviewTest.kt` (new)

**Interfaces:**
- Produces (all in `OnboardingFrame.kt`, package `com.maximerollin.aqualog.onboarding`):
  - **`fun onboardingPreviewAquarium(name: String, volumeInput: String, unit: VolumeUnit, profile: AquariumProfile): Aquarium`**
    - `id = "onboarding-preview"` and `createdAtEpochMillis = 0`.
    - `volume = parseDecimalInput(volumeInput)` when it is finite and > 0, else 0.0.
  - **`fun ParameterEditorState.toPreviewDefinition(position: Int): ParameterDefinition?`** is `toDraft(position)` mapped to a `ParameterDefinition` with `id = parameter.storageValue` and `aquariumId = "onboarding-preview"`. It is null when the draft is invalid.
  - **`@Composable fun OnboardingHeader(aquarium: Aquarium, minHeight: Dp, animate: Boolean, modifier: Modifier = Modifier, step: Int? = null, content: @Composable ColumnScope.() -> Unit)`**
    - Layers: `TankScene(aquarium, emptyList(), animate = animate && !rememberReducedMotion())`, then `WaterHeaderShade`.
    - On top, a column: the status-bar spacer, `OnboardingProgress(step)` when `step != null` (20 dp sides, 12 dp top), then `content` aligned to the bottom, with 20 dp sides.
    - It reserves the 30 + 16 dp bottom space as `WaterHeader` does, and is used with `overlapNext(30.dp)`.
  - **`@Composable fun OnboardingProgress(step: Int, total: Int = 5, modifier: Modifier = Modifier)`**
    - A row of `total` segments, 5 dp tall, 3 dp corners, 4 dp gaps: white for those ≤ `step`, white at 35 % for the rest.
    - Tag `onboarding-progress`.
    - Semantics: cleared and set to `contentDescription = onboarding_step_of(step, total)` and `progressBarRangeInfo(step / total)`.
  - **`@Composable fun WelcomeDots(modifier: Modifier = Modifier)`** draws 6 dots: the first 22 × 6 dp white, the others 6 × 6 dp white at 45 %, 6 dp gaps. Tag `welcome-dots`, `clearAndSetSemantics {}`.
  - **`@Composable fun OnboardingTitle(title: String, subtitle: String?)`**: Fraunces SemiBold 30 sp / 34 sp in `HeroText`, as a heading; the subtitle is 14 sp `HeroText`. Neither has `maxLines`.
  - **`@Composable fun OnboardingScaffold(listState: LazyListState, primaryLabel: String, onPrimary: () -> Unit, modifier: Modifier = Modifier, primaryEnabled: Boolean = true, secondary: (@Composable () -> Unit)? = null, content: LazyListScope.() -> Unit)`**
    - A `Column`: a `LazyColumn` (weight 1, tag `onboarding-list`, `StatusBarOverHeader(listState)`) with `content`, then the bottom bar.
    - Bottom bar: `background` colour, 16 dp sides, 12 dp top, 16 dp bottom, `imePadding()`. It holds a 56 dp full-width `Button` (tag `onboarding-primary`), and `secondary` under it when given.
- Icons: `AquagendaIcons.GripVertical`, `AquagendaIcons.Lock`, `AquagendaIcons.Leaf`.
- New strings (the full table; later tasks only use them):

| key | values / -en | -fr | -de | -it | -es |
|---|---|---|---|---|---|
| `onboarding_step_of` | Step %1$d of %2$d | Étape %1$d sur %2$d | Schritt %1$d von %2$d | Passo %1$d di %2$d | Paso %1$d de %2$d |
| `parameters_hint` | Drag to reorder, tap a row to set its range | Glissez pour réordonner, touchez une ligne pour régler sa plage | Zum Umsortieren ziehen, auf eine Zeile tippen, um ihren Richtbereich einzustellen | Trascina per riordinare, tocca una riga per regolarne l'intervallo | Arrastra para reordenar, toca una fila para ajustar su rango |
| `parameters_count_active` (plurals) | %1$d tracked | %1$d suivi / %1$d suivis | %1$d erfasst | %1$d monitorato / %1$d monitorati | %1$d seguido / %1$d seguidos |
| `parameter_range_none` | No range | Aucune plage | Kein Bereich | Nessun intervallo | Sin rango |
| `parameter_range_invalid` | Range to check | Plage à revoir | Bereich prüfen | Intervallo da rivedere | Rango por revisar |
| `range_sheet_hint` | Indicative range: a personal reference point, not a norm. | Plage indicative : un repère personnel, pas une norme. | Richtbereich: ein persönlicher Anhaltspunkt, keine Norm. | Intervallo indicativo: un riferimento personale, non una norma. | Rango indicativo: una referencia personal, no una norma. |
| `range_sheet_invalid` | Check the unit and the range: the minimum cannot be above the maximum. | Vérifiez l'unité et la plage : le minimum ne peut pas dépasser le maximum. | Prüfen Sie Einheit und Bereich: Das Minimum darf das Maximum nicht überschreiten. | Controlla unità e intervallo: il minimo non può superare il massimo. | Revisa la unidad y el rango: el mínimo no puede superar el máximo. |
| `range_sheet_decrease` | Decrease %1$s | Diminuer %1$s | %1$s verringern | Diminuisci %1$s | Disminuir %1$s |
| `range_sheet_increase` | Increase %1$s | Augmenter %1$s | %1$s erhöhen | Aumenta %1$s | Aumentar %1$s |
| `range_sheet_done` | Done | Valider | Fertig | Fatto | Listo |
| `paywall_pro_overline` | Aquagenda Pro | Aquagenda Pro | Aquagenda Pro | Aquagenda Pro | Aquagenda Pro |
| `paywall_headline` | All your tanks, their whole story. | Tous vos bacs, toute leur histoire. | Alle Ihre Becken, ihre ganze Geschichte. | Tutte le tue vasche, tutta la loro storia. | Todos tus acuarios, toda su historia. |
| `paywall_benefit_aquariums` | Several Aquariums | Plusieurs aquariums | Mehrere Aquarien | Più acquari | Varios acuarios |
| `paywall_benefit_history` | History beyond 90 days | Historique au-delà de 90 jours | Verlauf über 90 Tage hinaus | Cronologia oltre 90 giorni | Historial de más de 90 días |
| `paywall_benefit_analysis` | Before / after analysis of an intervention | Analyse avant / après une intervention | Vorher-nachher-Analyse eines Eingriffs | Analisi prima / dopo un intervento | Análisis antes / después de una intervención |
| `paywall_plan_annual` | Annual | Annuel | Jährlich | Annuale | Anual |
| `paywall_plan_lifetime` | Lifetime | À vie | Lebenslang | A vita | De por vida |
| `paywall_annual_terms` | per year · %1$d-day trial | par an · essai de %1$d jours | pro Jahr · %1$d Tage Testzeitraum | all'anno · prova di %1$d giorni | al año · prueba de %1$d días |
| `paywall_lifetime_terms` | once, no trial | une fois, sans essai | einmalig, ohne Testzeitraum | una volta, senza prova | una vez, sin prueba |
| `paywall_recommended` | Recommended | Recommandé | Empfohlen | Consigliato | Recomendado |
| `paywall_choose_pro` | Choose Pro | Choisir Pro | Pro wählen | Scegli Pro | Elegir Pro |

- [ ] **Step 1: Write the failing tests**

```kotlin
// OnboardingPreviewTest
@Test fun `preview aquarium parses a comma volume`()          // ("Salon", "120,5", LITERS, ESTABLISHED).volume == 120.5
@Test fun `preview aquarium keeps zero for a bad volume`()    // "", "abc", "-3", "0" → volume == 0.0
@Test fun `preview definition is null for an invalid editor`()// min "8", max "6" → null; unit " " → null
@Test fun `preview definition carries the editor values`()    // pH, "6,5", "7,5", precision "1" → min 6.5, max 7.5, precision 1, id "ph"

// OnboardingFrameSemanticsTest (French locale, as MoreSemanticsTest does)
@Test fun `progress is one node saying the step`()            // OnboardingProgress(2) → tag node contentDescription == catalog onboarding_step_of(2, 5); no child nodes in the merged tree
@Test fun `welcome dots are silent`()                         // WelcomeDots() → onNodeWithTag("welcome-dots") has no contentDescription and no text, useUnmergedTree too
@Test fun `scaffold primary button is a 56 dp button`()       // OnboardingScaffold(…) → onNodeWithTag("onboarding-primary") hasClickAction, height ≥ 56.dp
@Test fun `header title is a heading`()                       // OnboardingHeader { OnboardingTitle("Que suivez-vous ?", null) } → onNode(isHeading() and hasText(…)) exists
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :ui:jvmTest --tests '*OnboardingPreviewTest' --tests '*OnboardingFrameSemanticsTest'`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement the interfaces above, the three icons and the strings.**

- [ ] **Step 4: Run them, plus the contract tests**

Run: `./gradlew :ui:jvmTest --tests '*OnboardingPreviewTest' --tests '*OnboardingFrameSemanticsTest' --tests '*LocalizationContractTest' --tests '*StringResourcesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(onboarding): shared header, progress, welcome dots and lot 5 strings"
```

---

### Task 2: Welcome and Objective, and the onboarding actions

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/onboarding/OnboardingSteps.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (routing; delete `WelcomeScreen`, `ProfileScreen`, `ProfileOption`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/OnboardingStepsSemanticsTest.kt` (new)

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - **`class OnboardingActions`**. Its lambdas mirror the ViewModel calls:
    - `startOnboarding`, `requestImport`, `selectProfile(AquariumProfile)`, `continueToAquarium`;
    - `updateAquariumName(String)`, `updateVolume(String)`, `selectVolumeUnit(VolumeUnit)`, `continueToParameters`;
    - `setParameterActive(BuiltInParameter, Boolean)`, `moveParameter(BuiltInParameter, Int)`;
    - `updateParameterUnit|Precision|Minimum|Maximum(BuiltInParameter, String)`;
    - `continueToPractice`, `updatePracticeValue(BuiltInParameter, String)`, `continueToPaywall`.

    Add `fun HomeViewModel.onboardingActions(onRequestImport: () -> Unit = ::requestPortableImport): OnboardingActions` next to it.
  - **`@Composable fun WelcomeContent(portableData: PortableDataUiState, actions: OnboardingActions, modifier: Modifier = Modifier)`**
    - Water: `TankScene` of `onboardingPreviewAquarium("", "", LITERS, ESTABLISHED)`, animated (ruling 5), full frame, with `StatusBarOverWater(true)`.
    - At the bottom, on `fullScreenBacking()` with `navigationBarsPadding()`, 24 dp sides, top to bottom:
      - « Aquagenda » (`app_name`, 13 sp SemiBold, upper case, 6 % letter spacing);
      - `welcome_title` in Fraunces 38 sp / 40 sp white, as a heading;
      - `welcome_description`, 15 sp white;
      - the existing `PortableDataMessage(portableData)`;
      - `WelcomeDots` (22 dp above, 18 dp below);
      - « Commencer » as a 56 dp white button with `WaterPrimaryButtonText`;
      - « Restaurer depuis l'exportation » as a white text button, disabled while `portableData.isWorking`.
    - It scrolls when taller than the screen, as `WaterFullScreen` does.
  - **`@Composable fun ProfileContent(selected: AquariumProfile, actions: OnboardingActions, modifier: Modifier = Modifier)`**
    - `OnboardingScaffold` with primary `continue_to_aquarium`.
    - Header: 300 dp, `step = 1`, animated. It shows `OnboardingTitle(profile_title, profile_description)`.
    - Below the sheet top, a `selectableGroup` column (16 dp sides, 10 dp gaps) of three `ProfileCard`s:
      - Établi → `Fish`, Cyclage → `FlaskConical`, Planté / crevette → `Leaf`;
      - their titles and descriptions are the existing `profile_*` strings.
  - **`@Composable private fun ProfileCard(title: String, description: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit)`**
    - 22 dp corners and 14 dp padding.
    - Selected: a `surface` fill with a 2 dp `primary` border and a soft shadow; the 44 dp icon square (14 dp corners) is `primary` with an `onPrimary` icon.
    - Unselected: no fill, a 1 dp `outlineVariant` border, and the square is `secondaryContainer` with a `primary` icon.
    - `selectable(selected, role = Role.RadioButton)`, merged semantics; the icon has `contentDescription = null`.
- Routing in `AquaLogContent`:
  - `WELCOME` → `WelcomeContent(…, modifier)`: the bare modifier, full frame like the account screens.
  - `PROFILE` → `ProfileContent(…, framed)`.
  - The import confirmation dialog is unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `welcome start calls startOnboarding`()            // click catalog get_started → calls == ["startOnboarding"]
@Test fun `welcome restore is disabled while working`()      // portableData.isWorking = true → restore_from_export node assertIsNotEnabled
@Test fun `welcome title is a heading over the water`()      // onNode(isHeading() and hasText(welcome_title)) exists; welcome-dots exists and is silent
@Test fun `profile cards are a radio group`()                // three nodes with Role.RadioButton; the ESTABLISHED one is selected
@Test fun `tapping a profile card selects it`()              // click profile_cycling title → calls contains "selectProfile(CYCLING)"
@Test fun `profile shows step 1 of 5`()                      // onboarding-progress contentDescription == onboarding_step_of(1, 5)
@Test fun `profile at German 150 percent keeps the button`() // Density(fontScale = 1.5f), German locale → onboarding-primary is displayed and ≥ 48 dp
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :ui:jvmTest --tests '*OnboardingStepsSemanticsTest'`
Expected: FAIL.

- [ ] **Step 3: Implement, route, and delete the replaced screens.**

- [ ] **Step 4: Run them, then the JVM suite**

Run: `./gradlew :ui:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(onboarding): immersive welcome and objective"
```

---

### Task 3: Aquarium step — live name and volume on the water

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/onboarding/OnboardingSteps.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (routing; delete `AquariumDetailsScreen`, `VolumeUnitOption`)
- Test: `OnboardingStepsSemanticsTest.kt`

**Interfaces:**
- Produces: **`@Composable fun AquariumStepContent(name: String, volumeInput: String, unit: VolumeUnit, profile: AquariumProfile, showError: Boolean, actions: OnboardingActions, modifier: Modifier = Modifier)`**
  - `OnboardingScaffold` with primary `continue_to_parameters`.
  - Header: 300 dp, `step = 2`, still water, of `onboardingPreviewAquarium(name, volumeInput, unit, profile)`. At its bottom:
    - `aquarium_details_title` in 13 sp `HeroText`;
    - the typed `name` in Fraunces 36 sp / 40 sp, `maxLines = 2`, ellipsized. When `name` is blank, it is `aquarium_name_label` at 60 % alpha;
    - under it, the preview's `formattedVolume()` when the volume is > 0.
    - These three are one `liveRegion = Polite` node, so TalkBack does not reread the name on every key.
  - Sheet content (16 dp sides, 10 dp gaps):
    1. `FilledField(name, aquarium_name_label)`, Fraunces 26 sp, tag `aquarium-name`.
    2. `FilledField(volumeInput, aquarium_volume_label)` with the decimal keyboard, tag `aquarium-volume`.
    3. `PeriodSegmented([unit_liters, unit_us_gallons], selected = unit)`.
    4. A row with the `Lock` icon (16 dp, `onSurfaceVariant`) and `aquarium_details_description` (13 sp, `onSurfaceVariant`).
    5. When `showError`: the `invalid_aquarium` text in `error`. Both fields are `isError` with that message.
  - IME: name → Next, volume → Done.
- Routing: `AQUARIUM` → `AquariumStepContent(…, framed)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `typed name shows on the water`()                  // name "Salon" → a node with text "Salon" outside the field (the header) exists
@Test fun `blank name shows the label on the water`()        // name "" → header shows aquarium_name_label
@Test fun `volume line appears once valid`()                 // volume "120" LITERS → header text == formattedVolume of 120 L; "0" → no volume line
@Test fun `unit segmented calls selectVolumeUnit`()          // click unit_us_gallons → calls contains "selectVolumeUnit(US_GALLONS)"
@Test fun `error marks both fields and shows the message`()  // showError → invalid_aquarium text displayed; aquarium-name has SemanticsProperties.Error
@Test fun `German 150 percent name wraps on two lines`()     // long German name, fontScale 1.5 → header name node is displayed, primary button displayed
```

- [ ] **Step 2: Run them to make sure they fail.**

Run: `./gradlew :ui:jvmTest --tests '*OnboardingStepsSemanticsTest'`
Expected: FAIL.

- [ ] **Step 3: Implement and route.**

- [ ] **Step 4: Run them.**

Run: `./gradlew :ui:jvmTest --tests '*OnboardingStepsSemanticsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(onboarding): aquarium step with live name and volume on the water"
```

---

### Task 4: Parameters — one card of rows, drag to reorder, range sheet

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/onboarding/ParametersStep.kt`
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/onboarding/RangeSheet.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (routing; delete `ParametersScreen`, `ParameterEditor`, `ParameterTextField`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/onboarding/RangeStepTest.kt` (new), `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/ParametersStepSemanticsTest.kt` (new)

**Interfaces:**
- Produces:
  - **`fun stepRangeInput(input: String, precision: Int, direction: Int, decimalSeparator: Char): String?`** (`RangeSheet.kt`)
    - The step is 10^−precision. A blank input counts as 0; an unparsable one returns null, and the button does nothing.
    - The result is clamped at ≥ 0, rounded to `precision`, and written with exactly `precision` decimals and `decimalSeparator`; precision 0 gives an integer.
  - **`fun stepPrecision(input: String, direction: Int): String`**: within 0..3; an unparsable input becomes "1".
  - **`fun reorderOffset(dragY: Float, previousHeight: Float?, nextHeight: Float?): Int`** (`ParametersStep.kt`)
    - It returns −1 when `dragY < -previousHeight / 2`, +1 when `dragY > nextHeight / 2`, else 0.
    - A null neighbour never wins.
  - **`@Composable fun rangePillText(state: ParameterEditorState): String`**
    - `toPreviewDefinition(0)?.rangeText(currentLocalizedFormatters())` followed by « unit »;
    - `parameter_range_none` when the range is empty;
    - `parameter_range_invalid` when the editor is invalid.
  - **`@Composable fun ParametersContent(parameters: List<ParameterEditorState>, profile: AquariumProfile, showError: Boolean, actions: OnboardingActions, modifier: Modifier = Modifier)`**
    - `OnboardingScaffold` with primary `try_mini_session`.
    - Header: 200 dp, `step = 3`, still water. `OnboardingTitle(parameters_title, "<profile label> · <parameters_count_active>")`.
    - Sheet:
      - `parameters_hint` (13 sp `onSurfaceVariant`, 20 dp sides);
      - one `surface` card of 22 dp corners holding every row in a plain `Column`, with a 1 dp `outlineVariant` line between rows;
      - `invalid_parameters` in `error` when `showError`.
    - The sheet opened is local state: `rememberSaveable { mutableStateOf<String?>(null) }` holding a `storageValue`.
  - **`@Composable private fun ParameterRow(state: ParameterEditorState, canMoveUp: Boolean, canMoveDown: Boolean, onOpen: () -> Unit, onMove: (Int) -> Unit, onActive: (Boolean) -> Unit, …)`**, tag `parameter-row-<storageValue>`, min height 56 dp, left to right:
    1. **Handle.** `GripVertical`, 20 dp `outline` colour, in a 48 dp box tagged `parameter-handle-<storageValue>`.
       - `pointerInput` with `detectDragGestures`. It sums the drag; whenever `reorderOffset(sum, previous row height, next row height)` is ±1, it calls `onMove(±1)` and takes that neighbour's height off the sum.
       - While dragging, the row draws translated by the leftover sum, raised (4 dp shadow, `zIndex` 1).
       - The box has no semantics of its own.
    2. **Name and pill.** The name (15 sp Medium) and the range pill (`rangePillText`, 12 sp, 1 dp border, 14 dp corners; `error` colour when invalid). At `fontScale >= LargeFontScale` the pill goes under the name; otherwise it sits at the end.
    3. **Switch.** `Switch(state.isActive)`, with `contentDescription = enabled_for_parameter(label)`.

    Semantics:
    - The name and pill area is `clickable(role = Role.Button, onClick = onOpen)`, merged.
    - The row has `customActions` `move_parameter_up(label)` / `move_parameter_down(label)` only when it can move that way.
    - An inactive row dims its name and pill to 55 %.
  - **`@Composable fun RangeSheet(state: ParameterEditorState, actions: OnboardingActions, onDismiss: () -> Unit)`**
    - A `ModalBottomSheet(skipPartiallyExpanded = true)`, tag `range-sheet`, 20 dp sides. Top to bottom:
      1. the label in Fraunces 24 sp, as a heading, with the same switch at the end;
      2. `range_sheet_hint`;
      3. two `RangeStepperRow`s, `parameter_minimum` (tag `range-min`) and `parameter_maximum` (tag `range-max`);
      4. `FilledField(unit, parameter_unit)`;
      5. a decimals stepper row (`parameter_precision`, value shown, − / +);
      6. `range_sheet_invalid` in `error` while `state.toDraft(0) == null`;
      7. a 56 dp `range_sheet_done` button that calls `onDismiss`.
    - Every change calls the matching `actions.updateParameter*` at once (ruling 6).
  - **`@Composable private fun RangeStepperRow(label: String, value: String, precision: Int, onValueChange: (String) -> Unit, tag: String)`**
    - The label on the left. On the right: a 40 dp round − button in a 48 dp target, a 72 dp-wide editable number (`BasicTextField`, decimal keyboard, DM Sans 26 sp SemiBold tabular, centred, tag `tag`), then a + button.
    - The buttons call `stepRangeInput(value, precision, ∓1, regionalDecimalSeparator())` and ignore null.
    - Button descriptions: `range_sheet_decrease(label)` / `range_sheet_increase(label)`.
- Routing: `PARAMETERS` → `ParametersContent(state.parameters, state.profile, state.showParameterValidationError, actions, framed)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// RangeStepTest
@Test fun `plus adds one step at the precision`()        // ("6,5", 1, +1, ',') == "6,6"; ("0,1", 2, +1, ',') == "0,11"
@Test fun `minus clamps at zero`()                       // ("0", 1, -1, ',') == "0,0"; ("0,05", 1, -1, ',') == "0,0"
@Test fun `blank starts from zero`()                      // ("", 0, +1, ',') == "1"
@Test fun `unparsable input does nothing`()               // ("abc", 1, +1, ',') == null
@Test fun `no floating error`()                           // ("0,7", 1, +1, '.') == "0.8"; ten +1 steps from "0" at precision 1 == "1.0"
@Test fun `precision stays within 0 and 3`()              // stepPrecision("3", +1) == "3"; ("0", -1) == "0"; ("x", +1) == "1"
@Test fun `reorder waits for half a neighbour`()          // reorderOffset(-29f, 60f, 60f) == 0; (-31f, 60f, 60f) == -1; (31f, 60f, null) == 0; (31f, null, 60f) == 1

// ParametersStepSemanticsTest (French locale)
@Test fun `row exposes move actions by position`()        // first row: customActions == [move_parameter_down(label)]; middle row: up and down; last: up only
@Test fun `move action calls moveParameter`()             // performCustomAction(move_parameter_up("pH")) → calls contains "moveParameter(PH, -1)"
@Test fun `dragging the handle down moves the row`()      // performTouchInput on parameter-handle-ph: down, moveBy(0, rowHeight * 0.6), up → calls contains "moveParameter(PH, 1)"
@Test fun `tapping a row opens the range sheet`()         // click parameter-row-ph → range-sheet displayed with heading "pH"
@Test fun `range sheet plus updates the minimum`()        // in sheet, click range_sheet_increase(parameter_minimum) → calls contains "updateParameterMinimum(PH, 6,6)"
@Test fun `typing in the sheet still works`()             // performTextReplacement on range-max "8" → calls contains "updateParameterMaximum(PH, 8)"
@Test fun `invalid range shows in sheet and pill`()       // min "8", max "6" → range_sheet_invalid displayed; row pill text == parameter_range_invalid
@Test fun `no range pill reads Aucune plage`()            // min "", max "" → pill == parameter_range_none
@Test fun `error shows under the card`()                  // showError → invalid_parameters displayed
@Test fun `switch is named for its parameter`()           // node with contentDescription enabled_for_parameter("pH") is toggleable
@Test fun `German 150 percent puts the pill under the name`() // fontScale 1.5 → pill top > name bottom for the pH row
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :ui:jvmTest --tests '*RangeStepTest' --tests '*ParametersStepSemanticsTest'`
Expected: FAIL.

- [ ] **Step 3: Implement and route; delete the replaced composables.**

- [ ] **Step 4: Run them, then the JVM suite.**

Run: `./gradlew :ui:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(onboarding): parameters card with drag reorder and range sheet"
```

---

### Task 5: Practice — the Session's tiles, marked training only

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/onboarding/OnboardingSteps.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (routing; delete `PracticeScreen`)
- Test: `OnboardingStepsSemanticsTest.kt`

**Interfaces:**
- Produces: **`@Composable fun PracticeContent(parameters: List<ParameterEditorState>, values: Map<BuiltInParameter, String>, aquarium: Aquarium, actions: OnboardingActions, modifier: Modifier = Modifier)`**
  - `aquarium` is the preview Aquarium.
  - `OnboardingScaffold` with primary `see_free_and_pro`, which calls `continueToPaywall`.
  - Header: 170 dp, `step = 4`, still water. `OnboardingTitle(practice_title, aquarium.name)`.
  - Sheet:
    1. `practice_description` (14 sp `onSurfaceVariant`).
    2. A `secondaryContainer` note with 14 dp corners, holding `practice_not_saved` (13 sp SemiBold `onSecondaryContainer`).
    3. The tiles: the first three active Parameters with a valid `toPreviewDefinition(index)`. They are laid out as the Session lays them out (`sessionColumns`, `MeasureTileRow`, `MeasureTile` with `profile = aquarium.profile`).
       - `imeAction = sessionImeAction`, « Suivant » moves focus, the last one clears it.
       - Each tile is tagged `practice-<storageValue>`.
  - Nothing is saved: the values only go to `updatePracticeValue`, as today.
- Routing: `PRACTICE` → `PracticeContent(…, onboardingPreviewAquarium(state.aquariumName, state.volumeInput, state.volumeUnit, state.profile), …, framed)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `practice shows three tiles of active parameters`()   // 5 active editors → practice-<x> exists for the first 3 active only
@Test fun `practice says training only`()                        // practice_not_saved displayed
@Test fun `typing in a tile calls updatePracticeValue`()          // performTextInput "7,2" on practice-ph → calls contains "updatePracticeValue(PH, 7,2)"
@Test fun `tile shows its range state`()                          // values[PH] = "8,5" with range 6,5–7,5 → the tile's state description is session_tile_above
@Test fun `practice primary goes to the offer`()                  // click see_free_and_pro → calls contains "continueToPaywall"
```

- [ ] **Step 2: Run them to make sure they fail.**

Run: `./gradlew :ui:jvmTest --tests '*OnboardingStepsSemanticsTest'`
Expected: FAIL.

- [ ] **Step 3: Implement and route.**

- [ ] **Step 4: Run them.**

Run: `./gradlew :ui:jvmTest --tests '*OnboardingStepsSemanticsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(onboarding): practice in session tiles"
```

---

### Task 6: Offer — plan cards and « Choisir Pro », in onboarding and from More

**Files:**
- Create: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/onboarding/PaywallContent.kt`
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (both paywall routes; delete `PaywallScreen`, `PlanCard`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/a11y/PaywallSemanticsTest.kt` (new), `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/onboarding/ProPlanTest.kt` (new)

**Interfaces:**
- Produces:
  - **`enum class ProPlan { ANNUAL, LIFETIME }`**.
  - **`fun availablePlans(pro: ProUiState): List<ProPlan>`** lists ANNUAL when `pro.offer != null`, then LIFETIME when `pro.lifetimeOffer != null`.
  - **`fun initialPlan(pro: ProUiState): ProPlan?`** is the first of `availablePlans`.
  - **`fun ProUiState.purchaseBusy(): Boolean`** is true when `purchaseStatus` is AWAITING_PLAY_CONFIRMATION, PENDING, VERIFYING or ACTIVE.
  - **`class PaywallActions(onStartAnnual, onStartLifetime, onRestore, onRetryOffer, onContinue)`** holds lambdas `() -> Unit`.
  - **`@Composable fun PaywallContent(pro: ProUiState, isSaving: Boolean, onboarding: Boolean, aquarium: Aquarium, actions: PaywallActions, modifier: Modifier = Modifier)`**
    - **Header.** 330 dp, animated; `step = 5` only when `onboarding`. It shows `paywall_pro_overline` (12 sp SemiBold, upper case), `paywall_headline` (Fraunces 30 sp, heading) and `paywall_description` (13 sp).
    - **Benefits.** Four rows, each a 24 dp `secondaryContainer` circle with a 14 dp `Check` in `primary`, then 15 sp text: `paywall_benefit_aquariums`, `paywall_benefit_history`, `paywall_benefit_analysis`, `paywall_benefit_widgets`.
    - **New string** (this task adds it to the 6 catalogs):

      | key | values / -en | -fr | -de | -it | -es |
      |---|---|---|---|---|---|
      | `paywall_benefit_widgets` | Tracking widgets on your home screen | Widgets de suivi sur l'écran d'accueil | Übersichts-Widgets auf dem Startbildschirm | Widget di monitoraggio nella schermata Home | Widgets de seguimiento en la pantalla de inicio |
    - **Plans.** `isLoadingOffer` → spinner + `loading_google_play_offer`. No plan → `google_play_offer_unavailable` in `error` + a `try_again` text button. Otherwise, a `selectableGroup` row (a column at `fontScale >= LargeFontScale`) of `PlanCard`s:
      - annual: `paywall_plan_annual`, `formattedPrice` (19 sp SemiBold tabular), `paywall_annual_terms(trialDays)`, badge `paywall_recommended`, tag `plan-annual`;
      - lifetime: `paywall_plan_lifetime`, `formattedPrice`, `paywall_lifetime_terms`, tag `plan-lifetime`.
    - The selection is `rememberSaveable` of the plan name, starting at `initialPlan(pro)`. If the selected plan leaves `availablePlans`, it falls back to `initialPlan`.
    - **Under the cards**, the selected plan's full terms: `pro_offer_summary(price, trialDays, localizedBillingDate)` or `pro_lifetime_offer_summary(price)`, 12.5 sp `onSurfaceVariant`. Then the `ProMessage` line, unchanged, then `restore_purchase` as a text button, disabled while VERIFYING.
    - **Bottom bar** (`OnboardingScaffold`'s):
      - primary `paywall_choose_pro`, tag `choose-pro`, shown only when a plan is selected; disabled while `purchaseBusy()`. It calls `onStartAnnual` or `onStartLifetime`;
      - secondary, a text button. Its label is `back_to_settings` when `!onboarding`, `continue_with_pro` when ACTIVE, else `continue_for_free`. It calls `onContinue` and shows a spinner while `isSaving`.
      - With no plan, the continue button is alone and becomes the 56 dp primary.
  - **`@Composable private fun PlanCard(title: String, price: String, terms: String, badge: String?, selected: Boolean, onClick: () -> Unit, tag: String)`**
    - 20 dp corners, 12 × 14 dp padding, `surface` fill; selected has a 2 dp `primary` border, unselected 1 dp `outlineVariant`.
    - It has a 20 dp check circle at the top end.
    - The badge is a 22 dp `primary` pill with `onPrimary` 11 sp Bold upper-case text, drawn over the top edge (offset −11 dp, 12 dp from the start).
    - `selectable(role = Role.RadioButton)`, merged: title, price, terms and badge are all read.
- Routing: both paywall branches → `PaywallContent(…, framed)`.
  - Onboarding passes `onContinue = finishForFree` and the preview Aquarium.
  - More passes `onContinue = closeProPaywall` and `state.setup.aquarium`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ProPlanTest
@Test fun `annual comes first when both exist`()              // both offers → availablePlans == [ANNUAL, LIFETIME]; initialPlan == ANNUAL
@Test fun `only lifetime selects lifetime`()                  // offer null → initialPlan == LIFETIME
@Test fun `no offer has no plan`()                            // both null → initialPlan == null
@Test fun `busy statuses`()                                   // AWAITING_PLAY_CONFIRMATION, PENDING, VERIFYING, ACTIVE → true; IDLE, CANCELLED, ERROR → false

// PaywallSemanticsTest (French locale; offers "14,99 €" annual 14-day trial, "34,99 €" lifetime)
@Test fun `annual card is selected and recommended`()         // plan-annual is selected, merged text contains paywall_recommended; plan-lifetime not selected
@Test fun `choose pro starts the selected plan`()             // click choose-pro → ["startAnnual"]; click plan-lifetime then choose-pro → [..., "startLifetime"]
@Test fun `terms follow the selected card`()                  // annual selected → pro_offer_summary text displayed; after plan-lifetime click → pro_lifetime_offer_summary displayed
@Test fun `busy purchase disables choose pro`()               // purchaseStatus = PENDING → choose-pro assertIsNotEnabled
@Test fun `no offer shows the error and retry, no cards`()    // both null → google_play_offer_unavailable and try_again displayed; plan-annual and choose-pro do not exist; continue_for_free exists
@Test fun `loading shows the spinner text`()                  // isLoadingOffer → loading_google_play_offer displayed; no plan-annual
@Test fun `from More the continue reads back to settings`()   // onboarding = false → back_to_settings exists; onboarding-progress does not
@Test fun `active onboarding continues with pro`()            // onboarding, ACTIVE → continue_with_pro exists
@Test fun `German 150 percent stacks the cards`()             // fontScale 1.5 → plan-lifetime top ≥ plan-annual bottom; choose-pro displayed
```

- [ ] **Step 2: Run them to make sure they fail.**

Run: `./gradlew :ui:jvmTest --tests '*ProPlanTest' --tests '*PaywallSemanticsTest'`
Expected: FAIL.

- [ ] **Step 3: Implement and route; delete `PaywallScreen` and `PlanCard` (the old one).**

- [ ] **Step 4: Update `LocalizedAccessibilityFlowTest` (Pro from More).**
  - It awaits `paywall_headline` instead of `paywall_title`.
  - It asserts `plan-annual`, `plan-lifetime`, `choose-pro`, `restore_purchase` and `back_to_settings` as essential actions instead of the two old purchase buttons.

- [ ] **Step 5: Run the JVM suite, then that device test**

Run: `./gradlew :ui:jvmTest`, then `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.LocalizedAccessibilityFlowTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(paywall): plan cards and Choisir Pro, in onboarding and from More"
```

---

### Task 7: Cleanup, the onboarding device flow, captures

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (delete `StandardScreen`, `ScreenHeading`, `PrimaryButton`, `ErrorText` and imports, if unused)
- Modify: the 6 `strings.xml` (delete keys left unused)
- Create: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/OnboardingFlowTest.kt`
- Modify: `LocalizedAccessibilityFlowTest.kt` (onboarding captures)
- Create: `docs/design/21-modernisation/lot5-captures/` (PNG)

- [ ] **Step 1: Delete dead code and keys.**
  - Delete every composable of `OnboardingUi.kt` that no caller uses any more.
  - For each candidate key, grep `ui/`, `androidApp/` and the tests. Delete the ones with no use left, from all 6 catalogs.
  - Candidates: `paywall_title`, `pro_plan`, `pro_plan_description`, `buy_lifetime_in_google_play`, `start_trial_in_google_play`, `move_up_symbol`, `move_down_symbol`, `unit_for_parameter`, `precision_for_parameter`, `minimum_for_parameter`, `maximum_for_parameter`, `practice_value_for_parameter`, `parameter_with_unit`, `volume_unit_label`.
  - `free_plan` stays (MoreUi uses it).

- [ ] **Step 2: Write `OnboardingFlowTest`**, English locale, fresh repository. It walks through the onboarding:
  1. welcome → `get_started`;
  2. profile → select `profile_planted_shrimp` → `continue_to_aquarium`;
  3. type `Salon` and `120`; the header shows `Salon` → `continue_to_parameters`;
  4. open `parameter-row-ph`, press + on the minimum, close with `range_sheet_done`. The pH pill changed;
  5. `performCustomAction(move_parameter_down("pH"))`;
  6. `try_mini_session`;
  7. type `7.2` in `practice-<first>` → `see_free_and_pro`;
  8. offer: `plan-annual` is selected and `choose-pro` is displayed → `continue_for_free`;
  9. the home shows « Salon ». The saved pH minimum and order match steps 4 and 5 (read from `aquariumRepository`).

- [ ] **Step 3: Extend `criticalOnboardingScreens_haveLocalizedCaptures_andAccessiblePrimaryActions`.** After the profile, capture the aquarium, parameters (and the range sheet), practice and offer steps in each language and mode, asserting each primary action with `assertEssentialActions`.

- [ ] **Step 4: Run the full suites**

Run: `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:lintDebug`, then `./gradlew :androidApp:connectedDebugAndroidTest`
Expected: all green.
- Lint warnings stay at 44 or fewer.
- Uninstall afterwards if an fr-FR per-app locale was set.

- [ ] **Step 5: Captures.**
  - Shoot the 6 screens and the range sheet, in FR light, FR dark and DE at 150 %.
  - Take « before » from `main` (`a8eb49c`, in a worktree) and « after » from this branch, on the Pixel 9a emulator.
  - Save them as `docs/design/21-modernisation/lot5-captures/<screen>-<fr-light|fr-dark|de-150>-<before|after>.png`.

- [ ] **Step 6: Launch the desktop harness** (`./gradlew :ui:run`) and walk the onboarding to the offer once.

- [ ] **Step 7: Commit**

```bash
git commit -m "chore(onboarding): delete replaced screens and keys, onboarding device flow, lot 5 captures"
```
