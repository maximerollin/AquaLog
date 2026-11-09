# Avis Google Play et commentaires — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- Ask for a Google Play review at the right moment: from the 5th calm Session, at most every 90 days, announced once by « Votre avis compte ».
- Let anyone send typed feedback, with or without an account, including from a species fiche.

**Architecture:**
- **Both features already exist; this plan changes them, it builds nothing parallel.**
- **Review** is `ReviewEligibilityPolicy` / `ReviewCoordinator` / `ReviewHistoryStore` in `shared/.../PrivacyServices.kt`, wired in `androidApp/.../PrivacyInfrastructure.kt` and called from `HomeViewModel` after a new Session. Its rules are rewritten, and a discovery dialog and a settings entry are added.
- **Feedback** is `VoluntaryFeedback` → `FeedbackGateway` → the edge function `submit-feedback` → the RPC `submit_product_feedback` → `public.product_feedback`. It gains a type, context fields, an anonymous path (service-role RPC, rate-limited) and a fiche entry point.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2, Room 3; Play Core `play-review` (already in the catalog); Supabase Postgres + pgTAP, Deno edge functions.

**Spec:** `docs/superpowers/specs/2026-11-09-avis-et-commentaires-design.md` (issues #41, #42).

## Global Constraints

- **No question of opinion before the Google review window**, and the unhappy are never sent to feedback instead (Google policy).
- **No « merci pour votre note »**: the app never knows whether the window showed.
- **No review request in a bad moment:** never right after a Session with a value out of its indicative range, nor in a Cycling Aquarium.
- **Feedback never carries** water values, notes, Aquarium names, photos, an email address or analytics identifiers. The only automatic additions are the app version, the app language, and the species id (from a fiche).
- **Free:** both features need no Pro.
- **No new telemetry event.** The existing `ProductEvent.ReviewRequested` and the LogSnag review event stay, fired on each real Google request.
- **Localization:** every new key in the 6 catalogs under `ui/src/commonMain/composeResources/values*/`; French uses « vous »; `LocalizationContractTest` stays green.
- **Accessibility:** touch targets ≥ 48 dp; nothing clipped mid-word in DE dark at font scale 1.5.
- **Desktop harness** compiles and runs; the review does nothing there and « Noter Aquagenda » is hidden.
- **Security:** `public.product_feedback` and its RPCs stay closed to the `anon` role; the anonymous RPC is executable by `service_role` only.
- **CI stays green:**
  - `./gradlew :shared:jvmTest :ui:jvmTest :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleRelease`;
  - `python3 tools/ci/backend.py prepare && python3 tools/ci/backend.py verify` (pgTAP + Deno);
  - `deno check` with the CI's Deno version.

## Decisions taken in this plan (Claude's calls, to check)

- **"Calm" Session** = a new Session whose `pistesAdvices(...)` is empty (no reading out of range — the same test that decides the pistes sheet), saved in an Aquarium whose profile is not `CYCLING`.
- **Where the review shows:** `HomeViewModel.maybeAskForReview()` runs after a new Session is saved and again when the account invitation or the pistes sheet closes. It acts only when no other prompt (account invitation, pistes, feedback) is open.
- **Feedback types** on the wire: `bug`, `translation`, `species`, `idea`, `other`. Old app versions that send only `{message}` get `other`.
- **Gateway outcome** replaces the `Boolean`: `FeedbackOutcome { SENT, RATE_LIMITED, FAILED, UNAVAILABLE }`. `FAILED` covers network errors (« Pas de connexion ? Votre message est gardé, réessayez plus tard. »); `UNAVAILABLE` stays « not configured in this version ».
- **Install id:** a random UUID in SharedPreferences `aqualog-feedback`, key `install-id`, created on the first anonymous send, sent only with anonymous messages.
  - The app has no "delete my local data" flow, so the id is cleared when the user deletes their account (the closest privacy action).
  - `docs/privacy.md` says this.
- **Rate the app** opens `market://details?id=<applicationId>`, falling back to `https://play.google.com/store/apps/details?id=<applicationId>`. It is passed into the UI as a nullable callback `rateApp: (() -> Unit)?` (null on desktop, which hides the entry).

## Review Focus

1. **An old app version** posts only `{"message": "…"}` with a user JWT after the server is deployed.
   - Expected: accepted as `kind = other`.
   - Test: in Task 2.
2. **Anonymous spam:** a 4th anonymous message within an hour from one install id, and the 101st anonymous message overall within an hour.
   - Expected: both rejected with 429 → the app shows « Trop de messages en ce moment, réessayez plus tard ».
   - Test: in Task 1 (pgTAP) and Task 3 (outcome mapping).
3. **A signed-in user whose token expired.**
   - Expected: one refresh, then the message goes anonymously; it is never lost silently.
   - Test: in Task 3.
4. **The 5th Session is out of range, the 6th is calm; or the Aquarium is Cycling.**
   - Expected: pending only at the 6th; never while Cycling.
   - Test: in Task 5.
5. **A user already asked by the old rule** (`review-requested = true`, no date).
   - Expected: no prompt until 90 days after the update; then the discovery dialog shows once.
   - Test: in Task 5.

---

### Task 1: Feedback table and RPCs

**Files:**
- Create: `supabase/migrations/20261110100000_product_feedback_context.sql`
- Modify: `supabase/tests/database/product_feedback.test.sql`

**Interfaces:**
- Produces:
  - **Table** `public.product_feedback`:
    - `account_id uuid null`;
    - new columns `kind text not null default 'other'`, `species_id text null`, `app_version text null`, `language text null`, `install_id uuid null`.
  - **Constraints:**
    - `kind in ('bug','translation','species','idea','other')`;
    - `species_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(species_id) <= 64`;
    - `char_length(app_version) <= 32`;
    - `language ~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$' and char_length(language) between 2 and 16`;
    - `num_nonnulls(account_id, install_id) = 1`.
  - **RPC** `public.submit_product_feedback(feedback_message text, feedback_kind text default 'other', feedback_species_id text default null, feedback_app_version text default null, feedback_language text default null)`, `authenticated` only:
    - the old one-argument signature is dropped;
    - it keeps 3/hour per account.
  - **RPC** `public.submit_anonymous_feedback(feedback_install_id uuid, feedback_message text, feedback_kind text default 'other', feedback_species_id text default null, feedback_app_version text default null, feedback_language text default null)`, `security definer`, granted to `service_role` only:
    - it limits to 3/hour per `install_id` and 100/hour for all anonymous rows together (`account_id is null`);
    - it raises `P0001` with message `feedback rate limit reached` on either limit, `22023` on invalid input;
    - an advisory lock on the install id, plus one global lock key for the global count.

- [ ] **Step 1: Write the failing pgTAP tests** in `product_feedback.test.sql`, keeping the existing ones:
  - `anon` cannot execute either RPC; `authenticated` cannot execute `submit_anonymous_feedback`;
  - an authenticated call with only `feedback_message` stores `kind = 'other'`;
  - an authenticated call with `'species'`, `'neon'`, `'1.4.0'`, `'fr'` stores those values;
  - a service-role anonymous call stores `account_id is null` and the install id;
  - a 4th anonymous call within the hour for the same install id → `P0001`;
  - with 100 anonymous rows in the last hour from other install ids, the next → `P0001`;
  - an unknown kind, a species id `'Neon!'`, or a language `'français'` → `22023`, or a check violation;
  - deleting the account still deletes its rows; anonymous rows remain.
- [ ] **Step 2: Run** `python3 tools/ci/backend.py prepare && python3 tools/ci/backend.py verify`. Expected: FAIL on the new tests.
- [ ] **Step 3: Write the migration.** Validate inside the RPCs (blank-trimmed message 1–2000, kind in the list) before inserting, so callers get `22023`, not a raw check error.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(feedback): typed and anonymous feedback in the database`.

### Task 2: The `submit-feedback` function

**Files:**
- Modify: `supabase/functions/submit-feedback/index.ts`, `supabase/functions/submit-feedback/service.ts`, `supabase/functions/submit-feedback/service_test.ts`
- Modify: `supabase/config.toml` (`[functions.submit-feedback] verify_jwt = false`)
- Modify: `docs/deployment.md` (deploy the migration and the function before the app), `docs/privacy.md` (anonymous feedback, install id, kept after account deletion, cleared on account deletion on the device)

**Interfaces:**
- Consumes: Task 1's two RPCs.
- Produces:
  - **Request body:** `{ message: string, kind?: string, speciesId?: string, appVersion?: string, language?: string, installId?: string }`.
  - **Responses:**
    - `204` sent;
    - `400` invalid body;
    - `401` a Bearer token was sent but is not a valid user token (the client refreshes or falls back);
    - `429` rate limited;
    - `500` otherwise.
  - **Pure function** in `service.ts`: `parseFeedbackRequest(body: unknown): FeedbackRequest | null`. It trims nothing but the checks; it maps a missing `kind` to `"other"`, and rejects any field that breaks Task 1's formats.

- [ ] **Step 1: Write the failing tests** in `service_test.ts`:
  - `parseFeedbackRequest({ message: "x" })` → `{ message: "x", kind: "other" }` (Review Focus 1);
  - a full valid body round-trips;
  - each invalid field (`kind: "spam"`, `speciesId: "Neon!"`, `appVersion` of 33 chars, `language: "français"`, `installId: "not-a-uuid"`, blank or 2001-char message) → `null`;
  - `feedbackRpcStatus("P0001")` → 429, `"22023"` → 400, `"42501"` → 401.
- [ ] **Step 2: Run** `deno test supabase/functions/submit-feedback/`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **With an `Authorization: Bearer` header:** check the user with `client.auth.getUser(token)`.
    - Invalid → `401`.
    - Valid → call `submit_product_feedback` with the user's header, as today.
  - **Without it:** require a valid `installId`, then call `submit_anonymous_feedback` with a service-role client (`SUPABASE_SERVICE_ROLE_KEY`).
  - Never log the message.
- [ ] **Step 4: Run** `deno test supabase/functions/submit-feedback/` and `deno check supabase/functions/submit-feedback/index.ts`, then `python3 tools/ci/backend.py verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(feedback): accept feedback without an account`.

### Task 3: Feedback model and Android gateway

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/PrivacyServices.kt` (feedback part), `shared/src/commonTest/kotlin/com/maximerollin/aqualog/shared/PrivacyServicesTest.kt`
- Modify: `androidApp/src/main/kotlin/com/maximerollin/aqualog/ObservabilityInfrastructure.kt`, `androidApp/src/test/kotlin/com/maximerollin/aqualog/ObservabilityInfrastructureTest.kt`
- Create: `androidApp/src/main/kotlin/com/maximerollin/aqualog/FeedbackInstallId.kt`
- Modify: the account-deletion completion path in `androidApp` (where the account's local state is cleared): also clear the install id.

**Interfaces:**
- Consumes: Task 2's request and responses.
- Produces (in `shared`):
  - `enum class FeedbackKind(val wireValue: String) { BUG("bug"), TRANSLATION("translation"), SPECIES("species"), IDEA("idea"), OTHER("other") }`.
  - `data class VoluntaryFeedback(val message: String, val kind: FeedbackKind = FeedbackKind.OTHER, val speciesId: String? = null, val language: String? = null)`. It keeps its two `require`s.
  - `enum class FeedbackOutcome { SENT, RATE_LIMITED, FAILED, UNAVAILABLE }`.
  - `fun interface FeedbackGateway { suspend fun submit(feedback: VoluntaryFeedback): FeedbackOutcome }`. `FeedbackCoordinator.submit` returns `FeedbackOutcome`; `DisabledFeedbackGateway` returns `UNAVAILABLE`.
- Produces (in `androidApp`):
  - `internal interface FeedbackInstallIdStore { fun getOrCreate(): String; fun clear() }`, with `AndroidFeedbackInstallIdStore(context)` (prefs `aqualog-feedback`, key `install-id`).
  - `internal class SupabaseFeedbackGateway(endpoint, sessionProvider: SyncSessionProvider, installIds: FeedbackInstallIdStore, appVersion: String, transport: FeedbackTransport)`. It replaces `AuthenticatedFeedbackGateway`.
  - `FeedbackTransport.post(endpoint: String, accessToken: String?, body: JSONObject): Int`. It returns the HTTP code, or `-1` on an IO error.

- [ ] **Step 1: Write the failing tests** in `ObservabilityInfrastructureTest`, with a fake transport recording calls:
  - with a session → one post with the token, no `installId` in the body;
  - without a session → one post without a token, with the install id;
  - token refused (`401`) → `refreshSession` once → retry with the new token; if still `401` or no refreshed session → anonymous post (Review Focus 3);
  - `429` → `RATE_LIMITED`, `-1` or `500` → `FAILED`, `204` → `SENT`;
  - the body carries `kind`, `speciesId` when set, `appVersion`, `language`, and never anything else.

  Plus in `PrivacyServicesTest`: `DisabledFeedbackGateway` → `UNAVAILABLE`.
- [ ] **Step 2: Run** `./gradlew :androidApp:testDebugUnitTest --tests '*ObservabilityInfrastructureTest*' :shared:jvmTest --tests '*PrivacyServicesTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - `createFeedbackGateway` builds `SupabaseFeedbackGateway` when `config.hasFeedback`.
  - `appVersion` = `BuildConfig.VERSION_NAME`.
  - Update `HomeViewModel.submitFeedback` to the new return type: keep the message unless `SENT`.
- [ ] **Step 4: Run** the same command, then `./gradlew :ui:jvmTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(feedback): send typed feedback with or without an account`.

### Task 4: Feedback form and the fiche link

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeViewModel.kt` (`FeedbackUiState`, `openFeedback`, `submitFeedback`), `ui/src/commonMain/kotlin/com/maximerollin/aqualog/OnboardingUi.kt` (the feedback dialog, ~line 1262), `ui/src/commonMain/kotlin/com/maximerollin/aqualog/habitants/SpeciesFiche.kt`
- Modify: the 6 string catalogs
- Modify: `androidApp/src/androidTest/kotlin/com/maximerollin/aqualog/PrivacyFlowTest.kt` (`voluntaryFeedback_explainsItsPayload_andNeverEntersProductTelemetry`)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/` (a `FeedbackStateTest` next to the existing view-model tests)

**Interfaces:**
- Consumes: Task 3's `FeedbackKind`, `VoluntaryFeedback`, `FeedbackOutcome`.
- Produces:
  - `data class FeedbackUiState(isOpen, message, kind: FeedbackKind = OTHER, speciesId: String? = null, speciesName: String? = null, outcome: FeedbackOutcome? = null)`. The old `wasSubmitted` / `isUnavailable` become `outcome`.
  - `fun openFeedback(kind: FeedbackKind = FeedbackKind.OTHER, speciesId: String? = null, speciesName: String? = null)`.
  - `fun selectFeedbackKind(kind: FeedbackKind)`.
  - `fun submitFeedback(language: String)`.

- [ ] **Step 1: Write the failing tests** (`FeedbackStateTest`, with a fake gateway):
  - `openFeedback(SPECIES, "neon", "Néon bleu")` sets kind, id and name;
  - submit sends `VoluntaryFeedback(message, SPECIES, "neon", "fr")`;
  - `SENT` clears the message and keeps the dialog open with the sent notice;
  - `RATE_LIMITED` and `FAILED` keep the message.
- [ ] **Step 2: Run** `./gradlew :ui:jvmTest --tests '*FeedbackStateTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **Dialog:** a 48 dp row of 5 type choices (single choice), labelled « Bug », « Erreur de traduction », « Erreur de fiche », « Idée », « Autre ».
    - With a species, a line « Espèce : Néon bleu ».
    - The message field.
    - The explanation text, replaced to state exactly what is sent: « Votre message, son type, la version de l'app et sa langue (et l'espèce pour une fiche) sont envoyés. Sans compte, un identifiant aléatoire de l'appareil limite les envois. Aquagenda ne joint jamais vos valeurs d'eau, notes, noms d'aquarium, photos, adresse e-mail ni identifiants analytiques. »
    - One notice per outcome:
      - SENT « Message envoyé. Merci. »;
      - RATE_LIMITED « Trop de messages en ce moment, réessayez plus tard. »;
      - FAILED « Le message n'est pas parti. Il est gardé : réessayez plus tard. »;
      - UNAVAILABLE keeps the existing text.
  - **Language:** the UI passes `Locale.current.toLanguageTag()` to `submitFeedback`.
  - **Fiche:** a 48 dp row « Signaler une erreur » at the bottom of `SpeciesFiche`, calling `openFeedback(SPECIES, species.id, localName)`.
    - The feedback dialog must render wherever it is opened. If it is only composed inside the settings screen today, move its call to the app level, next to the other app-wide dialogs.
  - **Catalogs:** add all new strings to the 6 catalogs.
  - **`PrivacyFlowTest`:** update it to the new explanation.
- [ ] **Step 4: Run** `./gradlew :ui:jvmTest :androidApp:assembleDebugAndroidTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(feedback): typed feedback form and « Signaler une erreur » on fiches`.

### Task 5: Review rules

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/maximerollin/aqualog/shared/PrivacyServices.kt` (review part), `shared/src/commonTest/kotlin/com/maximerollin/aqualog/shared/PrivacyServicesTest.kt`
- Modify: `androidApp/src/main/kotlin/com/maximerollin/aqualog/PrivacyInfrastructure.kt` (`AndroidReviewHistoryStore`)

**Interfaces:**
- Produces:
  - `data class ReviewHistory(val newSessionCount: Int, val pending: Boolean, val lastRequestedAtEpochMillis: Long?, val discoverySeen: Boolean)`.
  - `fun storedReviewHistory(newSessionCount: Int, pending: Boolean, lastRequestedAtEpochMillis: Long?, discoverySeen: Boolean, legacyRequested: Boolean, nowEpochMillis: Long): ReviewHistory`. When `legacyRequested && lastRequestedAtEpochMillis == null`, it sets `lastRequestedAtEpochMillis = nowEpochMillis`.
  - `class ReviewEligibilityPolicy(minimumNewSessions: Int = 5, cooldownMillis: Long = 90 * DAY_MILLIS)`:
    - `fun shouldMarkPending(history: ReviewHistory, calm: Boolean, nowEpochMillis: Long): Boolean`;
    - `fun prompt(history: ReviewHistory, nowEpochMillis: Long): ReviewPrompt`.
  - `enum class ReviewPrompt { NONE, DISCOVERY, DIRECT }`.
  - `class ReviewCoordinator(policy, store, gateway, currentTimeMillis)`:
    - `fun onNewSessionSaved(calm: Boolean)`;
    - `fun pendingPrompt(): ReviewPrompt`;
    - `fun onDiscoveryAnswered(leaveReview: Boolean)` (records the request date, sets `discoverySeen`, clears `pending`, calls the gateway only when `leaveReview`);
    - `fun requestDirect()` (gateway, records date, clears `pending`).
  - **Store keys:** `new-session-count` (reads the old `successful-session-count` when absent), `review-pending`, `review-last-requested-at`, `review-discovery-seen`, legacy `review-requested`.

- [ ] **Step 1: Write the failing tests** in `PrivacyServicesTest` (replace the old review tests):
  - 4 calm Sessions → not pending; the 5th calm → pending, `prompt` = `DISCOVERY`;
  - 5th not calm, 6th calm → pending only after the 6th (Review Focus 4);
  - Cycling is passed as `calm = false` → never pending;
  - after `onDiscoveryAnswered(false)`: no gateway call, `prompt` = `NONE`; 89 days later, a calm Session → not pending; 90 days later → pending with `prompt` = `DIRECT`;
  - `onDiscoveryAnswered(true)` and `requestDirect()` call the gateway once each;
  - `storedReviewHistory(legacyRequested = true, lastRequestedAt = null, now)` → `lastRequestedAt = now`; then nothing for 90 days, then `DISCOVERY`, since `discoverySeen` is false (Review Focus 5).
- [ ] **Step 2: Run** `./gradlew :shared:jvmTest --tests '*PrivacyServicesTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement**, with `AndroidReviewHistoryStore` loading through `storedReviewHistory`. Keep `GooglePlayInAppReviewGateway` as is (telemetry + LogSnag).
- [ ] **Step 4: Run** `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(review): ask from the 5th calm Session, every 90 days`.

### Task 6: Review on the home and « Noter Aquagenda »

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/HomeViewModel.kt` (the save path ~line 2105, `continueAfterSessionConfirmation`, `dismissPistes`; new `reviewPrompt` state), the home screen that renders app-level dialogs, `OnboardingUi.kt` (Aide section, next to `send_feedback`)
- Modify: `ui/src/commonMain/kotlin/com/maximerollin/aqualog/AquaLogApp.kt` (parameter `rateApp: (() -> Unit)? = null`), `androidApp/src/main/kotlin/com/maximerollin/aqualog/MainActivity.kt` (passes it: `market://` intent, falling back to https on `ActivityNotFoundException`)
- Modify: the 6 string catalogs; `docs/privacy.md` and `docs/privacy-observability.md` (the new rule replaces « three successful Sessions and at least seven days »)
- Test: `ui/src/jvmTest/kotlin/com/maximerollin/aqualog/` (a `ReviewPromptStateTest`)

**Interfaces:**
- Consumes: Task 5's `ReviewCoordinator`, `ReviewPrompt`.
- Produces:
  - `HomeUiState.reviewDiscoveryVisible: Boolean`;
  - `fun maybeAskForReview()`;
  - `fun answerReviewDiscovery(leaveReview: Boolean)`.

- [ ] **Step 1: Write the failing tests** (`ReviewPromptStateTest`, fake coordinator pieces):
  - after a new calm Session that makes the prompt `DISCOVERY`, with no other prompt open → `reviewDiscoveryVisible`;
  - with the account invitation open → not visible; after `continueAfterSessionConfirmation()` → visible;
  - `DIRECT` → the gateway is called and nothing is shown;
  - editing a Session never counts.
- [ ] **Step 2: Run** `./gradlew :ui:jvmTest --tests '*ReviewPromptStateTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - **Saving a Session:** compute `calm` from the advices computed in `showPistesAfterSave` (empty) and `setup.aquarium.profile != AquariumProfile.CYCLING`; call `onNewSessionSaved(calm)`, then `maybeAskForReview()`.
  - **Dialog:** title « Votre avis compte », text « Si Aquagenda vous aide, laissez un avis sur Google Play : cela aide d'autres aquariophiles à le trouver. Vous pourrez aussi le faire plus tard depuis les réglages. », buttons « Laisser un avis » and « Plus tard ». No question of opinion.
  - **Settings:** « Noter Aquagenda » row in the Aide section when `rateApp != null`.
  - Add the 6 catalogs; update the two privacy docs.
- [ ] **Step 4: Run** the full CI command of the Global Constraints. Expected: PASS.
- [ ] **Step 5: Take screenshots** on the emulator: the discovery dialog (FR light, DE dark 1.5), the feedback dialog opened from the néon fiche. Save them in the scratchpad.
- [ ] **Step 6: Commit** `feat(review): « Votre avis compte » and « Noter Aquagenda »`.
