# Privacy in Aquagenda

Aquagenda works with a local-only database by default. Portable exports contain Aquarium records,
not authentication tokens, billing information, or app secrets. When an account-enabled build is
configured, Aquagenda sends account-owned Aquarium records to that configured backend and keeps
secure authentication tokens in Android's encrypted keystore-backed storage.

The app's Privacy action always presents this local summary. A hosted privacy URL is intentionally
not assumed by the repository and must be supplied explicitly through `AQUALOG_PRIVACY_URL` before publication.


## Purchase processing with RevenueCat

When purchases are configured, RevenueCat processes store transaction information and an opaque
account identifier to manage Pro access. Aquagenda does not set a customer email,
Aquarium name, water values, notes or advertising identifier in RevenueCat; automatic device identifier
collection and SDK diagnostics are disabled. Purchase data and SDK technical data remain a separate
processing purpose from the allowlisted product analytics channel. The hosted privacy policy must
identify RevenueCat and the applicable store before distribution.

Account deletion requests remove the RevenueCat customer before completing Supabase deletion, with
retry on failure. Deleting account data does not cancel an active Google Play subscription; that is
managed through Google Play. See [RevenueCat setup](revenuecat-setup.md) for the external validation protocol.

## Operational notifications

LogSnag receives closed payment lifecycle categories from authenticated server webhooks and review-flow request events from release builds. Sandbox payments use a separate channel without push notifications. No account ID, email, store receipt, Aquarium content or free text is included. Review requests do not establish that a dialog was shown or an opinion published. The hosted privacy policy must disclose LogSnag before distribution. See [setup](revenuecat-setup.md).
