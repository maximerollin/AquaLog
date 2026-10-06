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

The initial account attachment calls an authenticated Edge Function named
`migrate-initial-copy`. That function must authorize the bearer user, reject a
different `accountId`, and upsert every entity by its existing UUID in one
idempotent operation under Row Level Security. Deploying that server contract is
required before account creation is enabled in a release environment. No
service-role or other privileged key belongs in the Android build. Ongoing
multi-device synchronization is intentionally outside this slice.
