# Privacy-safe product observability

AquaLog treats product telemetry, technical failure reporting, and voluntary
feedback as three separate channels.

## Product telemetry

`ProductEvent` is the only application-facing API. `ProductAnalyticsSchema`
encodes its closed categories and validates the resulting payload again before
an adapter can send it. Unknown event names, property keys, value types, and
category values fail closed. The schema contains no stable user or account ID
and no field capable of carrying water values, Aquarium names, email addresses,
photos, notes, observations, or other free text.

The production application enables the PostHog Android adapter only when
`AQUALOG_DEV_POSTHOG_API_KEY` and a safe `AQUALOG_DEV_POSTHOG_HOST` are supplied through
Gradle properties or the environment. Otherwise it injects `DisabledProductTelemetry`.
The adapter disables autocaptured lifecycle/screen/replay data and every product
payload passes through `AllowlistedAnalyticsAdapter`. Keys are never repository secrets.

## Technical failures

`TechnicalFailure` accepts only a failure kind, a closed component, and an
optional closed synchronization category. `TechnicalFailureSchema` rejects any
additional context. Exception messages, URLs, identifiers, and user content are
not part of this contract.

Crashlytics initializes without a `google-services.json` only when all three
`AQUALOG_DEV_FIREBASE_APPLICATION_ID`, `AQUALOG_DEV_FIREBASE_API_KEY`, and
`AQUALOG_DEV_FIREBASE_PROJECT_ID` values are present. Missing configuration selects
`DisabledCrashReporter`. Manual reports contain only the closed failure kind,
component, and optional synchronization category; no user identifier is set.

## Voluntary feedback and reviews

Feedback uses `FeedbackGateway`, never `ProductTelemetry` or `CrashReporter`.
The Settings dialog explains exactly what is submitted and reminds the user not
to type personal details. `DisabledFeedbackGateway` makes the missing delivery
integration visible and does not claim the message was sent. When an authenticated
Supabase session and `AQUALOG_DEV_FEEDBACK_URL` (or the configured Supabase Edge URL)
exist, the dedicated `submit-feedback` function accepts only the voluntary message;
the database RPC enforces authentication and three submissions per account/hour.

Review eligibility is stored locally. After three successful Sessions and at
least seven days since the first one, AquaLog calls Google Play In-App Review
directly. There is no satisfaction pre-question. The local flag is persisted
before the native request so recreation or retries cannot prompt repeatedly.

Configuration names above refer to development. Use `AQUALOG_BETA_` and
`AQUALOG_PROD_` for the corresponding builds; global `AQUALOG_*` integration
values are not used. See the [environment matrix](v1-closed-test-qualification.md#environnements-android).
