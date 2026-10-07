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
