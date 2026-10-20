# Privacy in AquaLog

AquaLog works with a local-only database by default. Portable exports contain Aquarium records,
not authentication tokens, billing information, or app secrets. When an account-enabled build is
configured, AquaLog sends account-owned Aquarium records to that configured backend and keeps
secure authentication tokens in Android's encrypted keystore-backed storage.

The app's Privacy action always presents this local summary. A hosted privacy URL is intentionally
not assumed by the repository and must be supplied explicitly through `AQUALOG_DEV_PRIVACY_URL` before publication.


Configuration names above refer to development. Use `AQUALOG_BETA_` and
`AQUALOG_PROD_` for the corresponding builds; global `AQUALOG_*` integration
values are not used. See the [environment matrix](v1-closed-test-qualification.md#environnements-android).
