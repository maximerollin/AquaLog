# Delete account function

This function performs its own request authentication, so gateway JWT verification is disabled for
this function only. A live Supabase session is required to obtain the first deletion credential.
Subsequent retries use an HMAC credential bound to the account ID, operation key, and issuance
expiry; an expired credential can only finish an operation whose account-scoped database receipt
already exists.

Configure `AQUALOG_DELETION_SIGNING_SECRET` with a dedicated high-entropy server secret. It must
never be shipped in the Android build. `AQUALOG_ACCOUNT_STORAGE_BUCKETS` is an optional comma-
separated allowlist of buckets whose objects live below the account-ID prefix. Production must set
`AQUALOG_ACCOUNT_STORAGE_BUCKETS=aquarium-photos` (the private bucket of Aquarium photos, objects
`<accountId>/<aquariumId>.jpg`); otherwise account deletion leaves the photos behind.

Deployment checklist, secrets of this function: `AQUALOG_DELETION_SIGNING_SECRET`,
`AQUALOG_ACCOUNT_STORAGE_BUCKETS=aquarium-photos`, and `REVENUECAT_SECRET_API_KEY` for RevenueCat-enabled accounts.


For RevenueCat-enabled accounts, configure `REVENUECAT_SECRET_API_KEY` (v1 read/delete customer
permissions). Billing cleanup freezes the account mapping, deletes the RevenueCat customer, and
retries before database/Auth deletion can complete. A customer lookup returning 404 is already
clean. Deleting account data does not cancel a Google Play subscription.
