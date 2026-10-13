# Google Play test transaction

The instrumented `GooglePlayTestTransactionTest` is deliberately opt-in: it accepts only a real
Google Play purchase token and verifies it through the deployed Supabase function. It never creates
or substitutes a receipt locally.

Prerequisites:

1. Publish an app build containing the `aqualog_pro_annual` subscription to an internal test track.
2. Configure its annual base plan at €14.99 with a 14-day trial, then add the device account as a
   license tester and install the Play-delivered build.
3. Complete the test purchase explicitly in the Google Play purchase sheet with a Play test card.
4. Sign in to AquaLog on that device so encrypted Supabase tokens exist, and configure the build
   with `AQUALOG_SUPABASE_URL` and `AQUALOG_SUPABASE_ANON_KEY`.
5. Configure the deployed `verify-pro-purchase` function with
   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `GOOGLE_PLAY_PACKAGE_NAME`,
   `GOOGLE_PLAY_ACCOUNT_HMAC_SECRET`, and Supabase service-role secrets. The HMAC secret must be a
   high-entropy server-only value shared by every deployed instance of the function; rotating it
   invalidates in-flight purchases that were launched with the previous opaque account identifier.
   Never put any of these values in the APK or repository.

Run only against that prepared device:

```sh
./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.runGooglePlayTestTransaction=true
```

The ordinary connected suite skips this test and continues to use deterministic boundary doubles.
