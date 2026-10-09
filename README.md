# AquaLog

AquaLog is an Android-first, local-first aquarium log. The current vertical
slices let a new user complete the offline onboarding, configure an Aquarium
and its built-in Parameters, try a non-persisted mini-session, skip the
provisional paywall and return to the configured Aquarium after a restart.

## Development setup

- JDK 17
- Android SDK with API 37 installed
- Android Studio or the checked-in Gradle wrapper

Point Gradle at the Android SDK with `ANDROID_HOME`, or create an untracked
`local.properties` file containing:

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
```

Run the JVM contract test and build the development application:

```shell
./gradlew :shared:jvmTest
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
```

With an emulator or device connected, run the visible onboarding-to-persistence
seam:

```shell
./gradlew :androidApp:connectedDebugAndroidTest
```

The debug APK is generated under `androidApp/build/outputs/apk/debug/`.

## Local Task reminders

Task occurrences and their resolution history are stored in the local Room
database. Android reminders use inexact `AlarmManager` alarms, so AquaLog does
not request exact-alarm access. Android 13 and later require the user to grant
the notification permission before a reminder can be displayed. The Tasks
screen explains that declining the permission does not remove or block Tasks.

Android battery optimization can delay an inexact reminder. AquaLog detects
this state and explains it in the Tasks screen; it does not send the user to a
privileged settings screen. Reboot, clock, time-zone and app-replacement
broadcasts only reschedule the existing pending occurrence IDs. Receivers never
create an occurrence, resolve a Task or create a maintenance Action.

## Supabase account configuration

Account creation is deliberately disabled unless the build receives the public
Supabase project URL and anonymous client key. Supply them as Gradle properties
or environment variables; never commit values to the repository:

```shell
AQUALOG_SUPABASE_URL=https://PROJECT.supabase.co \
AQUALOG_SUPABASE_ANON_KEY=PUBLIC_ANON_KEY \
./gradlew :androidApp:assembleDebug
```

Configure `aqualog://auth/callback` as an allowed Auth redirect in Supabase and
enable Google plus email OTP providers. The application uses PKCE, correlates
the callback with an encrypted pending attempt, rejects implicit callback
tokens, and exchanges only the matching one-time authorization code. Access and
refresh tokens and the temporary PKCE verifier are encrypted with an Android
Keystore AES-GCM key; they are never stored in plain preferences.

The versioned backend lives under `supabase/`. Apply its migration and deploy the
authenticated `migrate-initial-copy` Edge Function before enabling account
creation in a release environment. The function uses the caller's bearer token
and public anonymous key; the atomic database RPC derives ownership from
`auth.uid()`, rejects a different `accountId`, and upserts every entity by its
existing local UUID. No service-role or other privileged key belongs in the
Android build. An expired access token is refreshed once before the migration is
retried; an invalid refresh token returns the user to authentication. Ongoing
multi-device synchronization is intentionally outside this slice.

The local backend contract is verified with:

```shell
supabase start
supabase test db
npx --yes deno test supabase/functions/migrate-initial-copy/service_test.ts
```
