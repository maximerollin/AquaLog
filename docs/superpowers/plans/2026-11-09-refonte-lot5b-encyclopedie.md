# Refonte lot 5b — Encyclopédie complète Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Grow the encyclopedia from 15 to 150 common freshwater species, and from 15 to 35 dedicated drawings, each batch ready for Maxime to validate.

**Architecture:**
- **Content, not code.** Lot 5 built everything that reads the encyclopedia:
  - the strict loader and validator (`decodeSpecies`, `speciesProblems`);
  - the verdict (`assessSpecies`);
  - the search and the fiche;
  - the scene, which looks drawings up by name (`Res.allDrawableResources`).
- This lot adds entries to `species.json`, vector files, and one review file per batch.
- **One small code change:** the tag `EATS_SNAILS`. Several new species (botias, Helena snail, pea puffer) eat snails, and without the tag the verdict would call them compatible with snails.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2 resources, kotlinx.serialization. Wikidata `Special:EntityData/<QID>.json` for names.

**Spec:** `docs/superpowers/specs/2026-10-30-refonte-aquagenda-design.md`:
- §4.5: about 150 species; fiche content; 30 to 40 dedicated drawings; family silhouettes for the others;
- §2: the anti-goal of copied fiches;
- §7: « Contenu de l'encyclopédie et dessins ».

**Earlier plan:** `docs/superpowers/plans/2026-11-04-refonte-lot5-habitants.md`. Its "Decisions" section holds:
- the model;
- the verdict rules;
- the 9 drawing families and 5 motion families.

## Global Constraints

- **Data rules (§2 anti-goal, EU database right).**
  - No FishBase, no scraping, no copied fiches from AquaticLog, Coddykit, AquaDiary or any other app or database.
  - Facts are written by us from general aquarium knowledge, then cross-checked against public sources (Wikipedia in two languages, or a public-domain care sheet).
  - Names come from Wikidata labels (CC0), fr/en/de/it/es. Each species records its QID.
- **Wikidata requests.**
  - Send a generic User-Agent: `AquagendaEncyclopedia/1.0 (+https://github.com/maximerollin/AquaLog)`.
  - **Never** put a personal email address or any other personal data in a request.
  - Keep at most 1 request per second.
- **Legal in the EU.** Leave out every species on the EU list of invasive alien species (Regulation (EU) 1143/2014, current implementing list). Also leave out *Pomacea* (import banned by Decision 2012/697/EU).
  - Examples already left out of the lists below: *Cabomba caroliniana*, *Myriophyllum aquaticum*, *Hydrocotyle ranunculoides*, *Ludwigia grandiflora*, *Procambarus*, *Pomacea*.
  - Check every plant against the current list before writing it. If one is listed, drop it and note it in the batch's review file.
- **Freshwater only** (§2 non-goal: no saltwater).
- **Existing ids never change.** `Inhabitant.speciesId` is synced and stores these ids.
  - New ids: the French common name, kebab-case, without accents (like `danio-zebre`).
  - Drawing names: `species_` + the id with `_` (like `species_corydoras_panda`).
- **The validator is the gate.**
  - `./gradlew :shared:jvmTest --tests '*SpeciesValidationTest*' :ui:jvmTest --tests '*EncyclopediaFileTest*'` must pass after every batch.
  - Unknown keys and enum names fail CI on purpose.
- **Review before publication (§7).** Every fiche is a draft until Maxime validates it.
  - Each batch has its own review file, in the format of `docs/encyclopedia/review.md`: the intro, then one section per species with a `- [ ] à valider` checkbox and one table of values, sources and notes.
  - It marks « choix de notre part », « aucune source chiffrée », « à vérifier en priorité » and « à trancher » the same way.
- **Plants** use reduced criteria:
  - kind `PLANT`, family `PLANT`;
  - `minVolumeLiters: null`, `minGroup: 1`, no cohabitation tags;
  - pH, temperature, GH and KH ranges when known;
  - `motion: SLOW`, `zone: BOTTOM` (they add a plant to the decor, never a swimmer).
- **Snails and shrimp** may have `minVolumeLiters: null` only when no source gives one; the review file says so.
- **French copy** uses « vous » and `docs/localization-glossary.md`. Every new string exists in the 6 catalogs.
- **CI must stay green:** `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleRelease`.

## Decisions taken in this plan (Claude's calls, to check)

- **The species list** is below, in 5 batches, 135 species. Added to the 15 of lot 5, that makes 150.
  - Batches follow drawing families, so that Maxime reviews similar fish together.
  - Swapping a species is cheap until its batch is written.
- **The 20 new dedicated drawings** (35 in all, inside the spec's 30 to 40):
  - fish: poisson-rouge, xipho, endler, barbus-de-sumatra, barbus-cerise, rasbora-galaxy, vairon-de-chine, neon-noir, tetra-nez-rouge, tetra-ember, ramirezi, apistogramma-cacatuoides, discus, kribensis, gourami-perle, corydoras-bronze, botia-clown, kuhli;
  - invertebrates: crevette-crystal-red, neritine.
  - The others keep their recoloured family silhouette.
- **`EATS_SNAILS`** works like `EATS_SHRIMP`: it applies against every `SNAIL` and gives ✗.
  - Shrimp-eating stays unchanged, as Maxime decided for the betta (« Pas de tag », 2026-11-06).
- **Two new validator rules:**
  - no two species share a name in the same language, so that two search rows never read the same;
  - no name starts with a lowercase letter.
- **One review file per batch**: `docs/encyclopedia/review-5b-1.md` to `review-5b-5.md`. `review.md` stays the record of the first 15.

### The 135 species

Each line gives the id, then the scientific name. The executor finds the QID on Wikidata from the scientific name. When Wikidata uses a newer name, keep Wikidata's and note the old one in the review file.

**Batch 1 — Characins (23), family `TETRA`:**
- neon-noir *Hyphessobrycon herbertaxelrodi*
- tetra-nez-rouge *Hemigrammus bleheri*
- tetra-citron *Hyphessobrycon pulchripinnis*
- tetra-serpae *Hyphessobrycon eques*
- tetra-ember *Hyphessobrycon amandae*
- tetra-empereur *Nematobrycon palmeri*
- tetra-diamant *Moenkhausia pittieri*
- tetra-congo *Phenacogrammus interruptus*
- tetra-veuve-noire *Gymnocorymbus ternetzi*
- tetra-cuivre *Hasemania nana*
- pristella *Pristella maxillaris*
- tetra-buenos-aires *Hyphessobrycon anisitsi*
- tetra-lumiere *Hemigrammus erythrozonus*
- tetra-fantome-noir *Hyphessobrycon megalopterus*
- poisson-crayon *Nannostomus beckfordi*
- nannostomus-marginatus *Nannostomus marginatus*
- poisson-hachette *Carnegiella strigata*
- neon-vert *Paracheirodon simulans*
- tetra-pingouin *Thayeria boehlkei*
- tetra-feu-de-position *Hemigrammus ocellifer*
- tetra-flamme *Hyphessobrycon flammeus*
- tetra-nageoires-rouges *Aphyocharax anisitsi*
- tetra-yeux-rouges *Moenkhausia sanctaefilomenae*

**Batch 2 — Livebearers and cyprinids (24):**
- Family `LIVEBEARER`:
  - xipho *Xiphophorus hellerii*
  - endler *Poecilia wingei*
  - platy-variatus *Xiphophorus variatus*
  - molly-voile *Poecilia latipinna*
  - heterandria *Heterandria formosa*
- Family `CYPRINID`:
  - poisson-rouge *Carassius auratus*
  - barbus-de-sumatra *Puntigrus tetrazona*
  - barbus-cerise *Puntius titteya*
  - barbus-odessa *Pethia padamya*
  - barbus-rose *Pethia conchonius*
  - barbus-denisonii *Sahyadria denisonii*
  - rasbora-galaxy *Danio margaritatus*
  - danio-choprae *Danio choprae*
  - rasbora-moustique *Boraras brigittae*
  - rasbora-espei *Trigonostigma espei*
  - rasbora-nain *Boraras maculatus*
  - vairon-de-chine *Tanichthys albonubes*
  - labeo-frenatus *Epalzeorhynchos frenatum*
  - labeo-bicolore *Epalzeorhynchos bicolor*
  - mangeur-algues-siamois *Crossocheilus oblongus*
  - barbus-requin *Balantiocheilos melanopterus*
  - rasbora-ciseaux *Rasbora trilineata*
  - barbus-schwanenfeldi *Barbonymus schwanefeldii*
  - danio-vert *Microdevario kubotai*

**Batch 3 — Cichlids and labyrinth fish (24):**
- Family `CICHLID`:
  - ramirezi *Mikrogeophagus ramirezi*
  - apistogramma-cacatuoides *Apistogramma cacatuoides*
  - apistogramma-agassizii *Apistogramma agassizii*
  - discus *Symphysodon aequifasciatus*
  - kribensis *Pelvicachromis pulcher*
  - oscar *Astronotus ocellatus*
  - labidochromis-jaune *Labidochromis caeruleus*
  - neolamprologus-multifasciatus *Neolamprologus multifasciatus*
  - bolivien *Mikrogeophagus altispinosus*
  - acara-bleu *Andinoacara pulcher*
  - cichlide-gorge-de-feu *Thorichthys meeki*
  - cichlide-zebre *Amatitlania nigrofasciata*
  - nannacara *Nannacara anomala*
  - aulonocara-jacobfreibergi *Aulonocara jacobfreibergi*
  - pseudotropheus-saulosi *Chindongo saulosi*
  - badis *Badis badis*
- Family `LABYRINTH`:
  - gourami-perle *Trichopodus leerii*
  - gourami-bleu *Trichopodus trichopterus*
  - gourami-miel *Trichogaster chuna*
  - gourami-grogneur *Trichopsis pumila*
  - macropode *Macropodus opercularis*
  - gourami-embrasseur *Helostoma temminckii*
  - gourami-chocolat *Sphaerichthys osphromenoides*
  - gourami-lune *Trichopodus microlepis*

**Batch 4 — Catfish, loaches and others (27):**
- Family `CATFISH`:
  - corydoras-bronze *Corydoras aeneus*
  - corydoras-paleatus *Corydoras paleatus*
  - corydoras-sterbai *Corydoras sterbai*
  - corydoras-pygmee *Corydoras pygmaeus*
  - corydoras-trilineatus *Corydoras trilineatus*
  - corydoras-habrosus *Corydoras habrosus*
  - botia-clown *Chromobotia macracanthus*
  - kuhli *Pangio kuhlii*
  - botia-yoyo *Botia almorhae*
  - botia-nain *Ambastaia sidthimunki*
  - plecostomus *Pterygoplichthys pardalis*
  - loricaire *Rineloricaria parva*
  - synodontis-petricola *Synodontis petricola*
  - poisson-chat-de-verre *Kryptopterus vitreolus*
  - sewellia *Sewellia lineolata*
- Family `CYPRINID` (closest silhouette):
  - arc-en-ciel-boesemani *Melanotaenia boesemani*
  - arc-en-ciel-praecox *Melanotaenia praecox*
  - arc-en-ciel-rouge *Glossolepis incisus*
  - arc-en-ciel-filament *Iriatherina werneri*
  - yeux-bleus *Pseudomugil furcatus*
  - medaka *Oryzias latipes*
- Family `LIVEBEARER` (killis):
  - killi-cap-lopez *Aphyosemion australe*
  - killi-raye *Epiplatys annulatus*
- Family `CICHLID`:
  - tetraodon-nain *Carinotetraodon travancoricus*
  - gobie-paon *Tateurndina ocellicauda*
- Family `CATFISH`:
  - bichir *Polypterus senegalus*
  - poisson-elephant *Gnathonemus petersii*

**Batch 5 — Shrimp, snails and plants (37):**
- Family `SHRIMP`:
  - crevette-crystal-red *Caridina cantonensis*
  - crevette-bambou *Atyopsis moluccensis*
  - crevette-fantome *Palaemonetes paludosus*
  - crevette-vampire *Atya gabonensis*
- Family `SNAIL`:
  - neritine *Neritina natalensis*
  - nerite-couronnee *Clithon corona*
  - planorbe-rouge *Planorbella duryi*
  - planorbe-corne *Planorbarius corneus*
  - melanoide *Melanoides tuberculata*
  - physe *Physella acuta*
  - helena *Anentome helena*
- Family `PLANT`:
  - anubias *Anubias barteri*
  - fougere-de-java *Leptochilus pteropus*
  - mousse-de-java *Taxiphyllum barbieri*
  - cryptocoryne-wendtii *Cryptocoryne wendtii*
  - cryptocoryne-parva *Cryptocoryne parva*
  - echinodorus *Echinodorus grisebachii*
  - vallisneria *Vallisneria spiralis*
  - vallisneria-geante *Vallisneria americana*
  - egeria *Egeria densa*
  - hygrophila-polysperma *Hygrophila polysperma*
  - hygrophila-difformis *Hygrophila difformis*
  - limnophila *Limnophila sessiliflora*
  - ludwigia-repens *Ludwigia repens*
  - rotala *Rotala rotundifolia*
  - bacopa *Bacopa caroliniana*
  - cornifle *Ceratophyllum demersum*
  - lentille-d-eau *Lemna minor*
  - grenouillette *Limnobium laevigatum*
  - marimo *Aegagropila linnaei*
  - hydrocotyle-tripartita *Hydrocotyle tripartita*
  - staurogyne *Staurogyne repens*
  - aponogeton *Aponogeton crispus*
  - sagittaire *Sagittaria subulata*
  - eleocharis *Eleocharis acicularis*
  - riccia *Riccia fluitans*
  - lotus-tigre *Nymphaea lotus*

## Review Focus

1. **A snail-eater added to a tank with snails, or a snail added next to a snail-eater.**
   - Expected: ✗ « Mange escargots » both ways.
   - Test: in Task 1.
2. **Two species with the same common name in one language.** Wikidata often labels close species alike, for example two « Néon ».
   - Expected: CI fails at the validator, so that two search rows never read the same.
   - Test: in Task 1.
3. **A coldwater species in a tropical tank,** for example a poisson rouge at 26 °C.
   - Expected: ✗ on temperature, from data alone.
   - Test: in Task 3, an `assessSpecies` check on the shipped `poisson-rouge` entry.
4. **A plant in a Cycling tank or a small tank.**
   - Expected: no fish warning, no volume or group verdict.
   - Test: in Task 6, an `assessSpecies` check on the shipped `anubias` entry.
5. **Search across 150 species.**
   - Expected: a query by French name, by scientific name, or without accents finds the species.
   - Expected: each verdict group stays sorted by name.
   - Test: in Task 6, `searchSpecies` checks on the shipped file.

---

### Task 1: `EATS_SNAILS` and two validator rules

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/encyclopedia/Species.kt` (tag, validator), `Compatibility.kt` (`Reason.EATS_SNAILS`, the rule in `conflicts`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/habitants/SpeciesFiche.kt` (the two `Reason` mappings)
- Modify: the 6 catalogs `ui/src/commonMain/composeResources/values*/strings.xml`
- Test: `shared/src/jvmTest/kotlin/com/maximerollin/aqualog/shared/encyclopedia/CompatibilityTest.kt`, `SpeciesValidationTest.kt`

**Interfaces:**
- Produces:
  - `SpeciesTag.EATS_SNAILS` and `Reason.EATS_SNAILS`;
  - string keys `habitants_reason_eats_snails` and `habitants_explain_eats_snails`.

- [ ] **Step 1: Write the failing tests.**
  - `CompatibilityTest`, in the style of the existing test `eats plants, shrimp and fin nipping are detected`:
    - `eats snails is detected both ways`: a fish with `EATS_SNAILS` against a present `SNAIL` gives `Reason.EATS_SNAILS` and `Fit.BAD`. The reverse, a snail assessed against a present snail-eater, gives the same.
    - `eats snails ignores shrimp`: the same fish against a present `SHRIMP` gives no cohabitation problem.
  - `SpeciesValidationTest`:
    - `sameNameInOneLanguageIsAProblem`: two species with `names["fr"] = "Néon"` give a problem containing `duplicate fr name 'Néon'`.
    - `lowercaseNameIsAProblem`: `names["de"] = "neon"` gives a problem containing `de name starts lowercase`.
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*encyclopedia*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Add the tag and the reason.
  - Add one line to `conflicts`, next to `EATS_SHRIMP`, with `victim.kind == SpeciesKind.SNAIL`.
  - Add the two validator rules to `speciesProblems`.
  - Map the reason in `SpeciesFiche.kt`.
  - Add the strings:

    | | `reason` (2 words, badge) | `explain` (sentence part) |
    |---|---|---|
    | values, en | Eats snails | snails risk being eaten |
    | fr | Mange escargots | les escargots risquent d'être mangés |
    | de | Frisst Schnecken | Schnecken könnten gefressen werden |
    | it | Mangia lumache | le lumache rischiano di essere mangiate |
    | es | Come caracoles | los caracoles pueden ser comidos |

- [ ] **Step 4: Run** `./gradlew :shared:jvmTest :ui:jvmTest`. Expected: PASS. This includes `LocalizationContractTest` and `EncyclopediaFileTest`: the 15 shipped species pass the new rules.
- [ ] **Step 5: Commit** `feat(habitants): snail-eaters and unique species names`.

### Tasks 2 to 6: one batch of fiches each

Each task is the same. Task 2 writes batch 1, Task 3 batch 2, Task 4 batch 3, Task 5 batch 4, Task 6 batch 5.

**Files:**
- Modify: `ui/src/commonMain/composeResources/files/encyclopedia/species.json` (append the batch, keep the existing order)
- Create: `docs/encyclopedia/review-5b-<batch>.md`
- Test (Tasks 3 and 6 only): `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/encyclopedia/EncyclopediaFileTest.kt`

**Interfaces:**
- Consumes: Task 1's `EATS_SNAILS`. The model and enums are in `Species.kt`.

- [ ] **Step 1: Write the batch's fiches,** following the Global Constraints.
  - **Names:** the Wikidata labels.
    - When a label is missing, use an alias.
    - When there is no alias either, use the usual aquarium name and mark it « nom ajouté par nous ».
    - Capitalize the first letter.
    - When two species would share a name in one language (Task 1's rule fails), give the newer one a Wikidata alias that tells them apart, or add a qualifier (« Néon vert »), and note it.
  - **Facts:**
    - Write the ranges for a typical home tank, not the species' natural extremes.
    - Cross-check each fact with at least two sources where they exist. Otherwise mark it « choix de notre part » or « aucune source chiffrée ».
  - **Tags:** `EATS_SMALL_FISH`, `EATS_SHRIMP`, `EATS_SNAILS`, `FIN_NIPPER`, `LONG_FINS`, `TERRITORIAL`, `EATS_PLANTS`, `PEACEFUL`, from the sources.
    - When a tag rests on our judgement alone, write « à valider ».
    - When including or leaving out a tag would change the verdict a lot, write « à trancher », and give the effect: which species turn « Déconseillés ».
  - **Display fields** (`family`, `motion`, `zone`, `color`): our choice, noted « choix de notre part (affichage) ».
    - `color` is the dominant hue, opaque ARGB.
    - `drawing` stays `null`; Task 7 sets it.
- [ ] **Step 2: Write `docs/encyclopedia/review-5b-<batch>.md`.**
  - It has the same intro and table format as `review.md`, titled « Encyclopédie, lot 5b, lot n : <families>, à valider ».
  - It ends with a short list of the « à trancher » points, one line each, so that Maxime sees them first.
- [ ] **Step 3 (Task 3 only): Add the test** `poissonRougeIsTooColdForATropicalTank` to `EncyclopediaFileTest`.
  - It runs `assessSpecies` for the shipped `poisson-rouge`, with a 200 L Aquarium and a latest temperature of 26 °C.
  - It asserts the `CriterionKind.TEMPERATURE` check is `CriterionStatus.PROBLEM`, with `Reason.TOO_WARM`.
- [ ] **Step 3 (Task 6 only): Add two tests to `EncyclopediaFileTest`.**
  - `aPlantGetsNoFishVerdict`: the shipped `anubias`, in a Cycling 20 L Aquarium with no Measurement, has `cyclingWarning == false`, and no check of kind `CriterionKind.VOLUME` or `CriterionKind.GROUP`.
  - `searchFindsTheShippedSpecies`:
    - `searchSpecies(species, "neon", "fr", null)` contains `neon` and `neon-noir`;
    - `"Carassius"` finds `poisson-rouge`;
    - `"fougère"` and `"fougere"` both find `fougere-de-java`.
- [ ] **Step 4: Run** `./gradlew :shared:jvmTest --tests '*SpeciesValidationTest*' :ui:jvmTest --tests '*EncyclopediaFileTest*'`. Expected: PASS, with no validator problem.
- [ ] **Step 5: Commit** `content(habitants): lot 5b batch <n> — <families>, to review`.

### Task 7: The 20 dedicated drawings

**Files:**
- Create: `ui/src/commonMain/composeResources/drawable/species_<id_with_underscores>.xml` (20 files, the list in "Decisions")
- Modify: `species.json` (set `drawing` on those 20 species)

**Interfaces:**
- Consumes: the ids from Tasks 2 to 6.

- [ ] **Step 1: Draw the 20 vectors** in the style of the 15 existing `species_*.xml`.
  - They face right, viewport 40 × 16, with 3 to 6 filled paths and an eye.
  - They use the species' real colours, under 2 KB each, with no gradient and no stroke.
  - Shrimp and snails use the proportions of `species_red_cherry.xml` and `family_snail.xml`.
- [ ] **Step 2: Set `drawing`** on the 20 species.
- [ ] **Step 3: Run** `./gradlew :ui:jvmTest --tests '*EncyclopediaFileTest*'`. Expected: PASS: every drawing exists.
- [ ] **Step 4: Make a contact sheet for Maxime.**
  - A throwaway script in the scratchpad turns each vector's `pathData` into an SVG `<path>` (same syntax).
  - It writes one HTML page showing all 35 dedicated drawings at 4× scale, each with its name.
  - Nothing is committed.
- [ ] **Step 5: Commit** `content(habitants): 20 more species drawings`.

## Manual pass (controller, after Task 7)

- [ ] In the desktop harness (`./gradlew :ui:run`) or on the emulator:
  - open « Ajouter un habitant »;
  - filter by each kind (fish, shrimp, snail, plant);
  - search « neon », « corydoras », « anubias »;
  - add 3 groups with new drawings, and check that they swim in the scene.
- [ ] Take screenshots (FR light; DE dark at font scale 1.5 of the search list), saved under the scratchpad. Check that nothing is clipped mid-word in the longer German names.
- [ ] Show Maxime the contact sheet and the 5 review files' « à trancher » lists.
