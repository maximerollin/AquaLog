# Privacy-safe product observability

Aquagenda treats product telemetry, technical failure reporting, and voluntary
feedback as three separate channels.

## Product telemetry

`ProductEvent` is the only application-facing API. `ProductAnalyticsSchema`
encodes its closed categories and validates the resulting payload again before
an adapter can send it. Unknown event names, property keys, value types, and
category values fail closed. The schema contains no stable user or account ID
and no field capable of carrying water values, Aquarium names, email addresses,
photos, notes, observations, or other free text.

The production application enables the PostHog Android adapter only when
`AQUALOG_POSTHOG_API_KEY` and a safe `AQUALOG_POSTHOG_HOST` are supplied through
Gradle properties or the environment. Otherwise it injects `DisabledProductTelemetry`.
The adapter disables autocaptured lifecycle/screen/replay data and every product
payload passes through `AllowlistedAnalyticsAdapter`. Keys are never repository secrets.

## Technical failures

`TechnicalFailure` accepts only a failure kind, a closed component, and an
optional closed synchronization category. `TechnicalFailureSchema` rejects any
additional context. Exception messages, URLs, identifiers, and user content are
not part of this contract.

Crashlytics initializes without a `google-services.json` only when all three
`AQUALOG_FIREBASE_APPLICATION_ID`, `AQUALOG_FIREBASE_API_KEY`, and
`AQUALOG_FIREBASE_PROJECT_ID` values are present. Missing configuration selects
`DisabledCrashReporter`. Manual reports contain only the closed failure kind,
component, and optional synchronization category; no user identifier is set.

## Voluntary feedback and reviews

Feedback uses `FeedbackGateway`, never `ProductTelemetry` or `CrashReporter`.
The Settings dialog explains exactly what is submitted and reminds the user not
to type personal details. `DisabledFeedbackGateway` makes the missing delivery
integration visible and does not claim the message was sent. When
`AQUALOG_FEEDBACK_URL` (or the configured Supabase Edge URL) exists, the
dedicated `submit-feedback` function accepts the message, its type, the app
version, the app language and an optional species id, with or without an
account. With a Supabase session the database RPC limits submissions to three
per account/hour. Without one, the anonymous path goes through a service-role
RPC that limits to three per install id/hour and 100/hour overall; the install
id is a random identifier used only for that limit.

Review eligibility is stored locally. On the fifth calm new Session (no value out
of range, not a Cycling Aquarium; editing a Session never counts), then at most
every 90 days, Aquagenda asks for a review. The first time, a one-time
« Votre avis compte » dialog explains it and offers « Laisser un avis » or
« Plus tard »; later requests call Google Play In-App Review directly. There is
no satisfaction pre-question. The request date is persisted before the native
request so recreation or retries cannot prompt repeatedly. Settings also offer
« Noter Aquagenda », which opens the store page. The review-flow LogSnag
event is unchanged.

## Operational notifications

LogSnag receives closed payment lifecycle categories from authenticated server webhooks and review-flow request events from release builds. Sandbox payments use a separate channel without push notifications. No account ID, email, store receipt, Aquarium content or free text is included. Review requests do not establish that a dialog was shown or an opinion published. The hosted privacy policy must disclose LogSnag before distribution. See [setup](revenuecat-setup.md).

PostHog events may include a closed `release_channel` category (local/internal/alpha/production). Debug and Firebase Test Lab disable both PostHog and Crashlytics adapters.
