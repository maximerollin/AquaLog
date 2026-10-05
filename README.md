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
