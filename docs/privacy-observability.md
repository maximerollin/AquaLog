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

The production application currently injects `DisabledProductTelemetry`.
PostHog must remain disabled until a later integration supplies a reviewed
`AnalyticsPayloadSink`, wraps it in `AllowlistedAnalyticsAdapter`, separates
development/closed-test/production projects, and demonstrates the payload tests
against the real SDK adapter. API keys must be build/environment configuration,
never repository secrets.

## Technical failures

`TechnicalFailure` accepts only a failure kind, a closed component, and an
optional closed synchronization category. `TechnicalFailureSchema` rejects any
additional context. Exception messages, URLs, identifiers, and user content are
not part of this contract.

The production application currently injects `DisabledCrashReporter` because
Firebase Crashlytics is not configured. Enabling it requires separate Firebase
projects per environment, consent/release review, and an adapter test proving
that only `RawTechnicalFailure` allowlisted fields reach the SDK. Crash and ANR
collection must not be enabled merely by adding the dependency.

## Voluntary feedback and reviews

Feedback uses `FeedbackGateway`, never `ProductTelemetry` or `CrashReporter`.
The Settings dialog explains exactly what is submitted and reminds the user not
to type personal details. `DisabledFeedbackGateway` makes the missing delivery
integration visible and does not claim the message was sent. A real delivery
endpoint must be configured and reviewed separately before enabling this gate.

Review eligibility is stored locally. After three successful Sessions and at
least seven days since the first one, AquaLog calls Google Play In-App Review
directly. There is no satisfaction pre-question. The local flag is persisted
before the native request so recreation or retries cannot prompt repeatedly.
