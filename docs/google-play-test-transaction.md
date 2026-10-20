# Google Play test transaction

The instrumented `GooglePlayTestTransactionTest` is deliberately opt-in: it accepts only a real
Google Play purchase token and verifies it through the deployed Supabase function. It never creates
or substitutes a receipt locally.

Prerequisites:

1. Publish `com.maximerollin.aqualog.beta` to an internal test track at least once, then configure the
   `aqualog_pro_annual` annual base plan at €14.99 with a 14-day trial.
2. Add the device account as a license tester. Install the same debuggable beta build that the command below will
   exercise, with the package registered for the beta Play app. Keep the signing key and app data
   unchanged; do not prepare the account in a differently signed Play-delivered build.
3. Configure that build with `AQUALOG_BETA_SUPABASE_URL` and `AQUALOG_BETA_SUPABASE_ANON_KEY`, and configure
   the deployed `verify-pro-purchase` function with
   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `GOOGLE_PLAY_PACKAGE_NAME`,
   `GOOGLE_PLAY_ACCOUNT_HMAC_SECRET`, and Supabase service-role secrets. The HMAC secret must be a
   high-entropy server-only value shared by every deployed instance of the function; rotating it
   invalidates opaque account identifiers and prevents revalidation of purchase tokens encrypted
   with the previous value. Plan a controlled key migration before rotating it.
   Never put any of these values in the APK or repository.
4. Open that same beta build and sign in to AquaLog so encrypted Supabase tokens exist. Launch the
   purchase from AquaLog only after signing in, then confirm it explicitly in the Google Play sheet
   with a Play test card. This binds Google's receipt to the authenticated AquaLog account.
5. Keep the app data and signing key unchanged between authentication, purchase, and the command
   below; the test restores that real purchase and sends its receipt to the deployed verifier.

Set `GOOGLE_PLAY_PACKAGE_NAME=com.maximerollin.aqualog.beta` on the beta verifier;
the production verifier uses the production package and separate credentials.
This document is a future external procedure, not an executed qualification receipt.

Run only against that prepared device:

```sh
./gradlew :androidApp:connectedBetaAndroidTest -PaqualogTestBuildType=beta \
  -Pandroid.testInstrumentationRunnerArguments.runGooglePlayTestTransaction=true
```

The ordinary connected suite skips this test and continues to use deterministic boundary doubles.

At app startup and after an authentication change, AquaLog asks the server to revalidate the stored,
encrypted purchase token against `purchases.subscriptionsv2.get`. The server atomically updates the
entitlement to active or inactive from Google's current state; the Android clock is not the source of
truth, and expiry boundaries are evaluated against the server clock. This repository does not
provision RTDN infrastructure, so a revocation is observed on the
next startup or authentication refresh rather than pushed in real time.
