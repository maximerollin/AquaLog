# Transaction Google Play de test via RevenueCat

Le test instrumenté `GooglePlayTestTransactionTest` est opt-in. Il restaure un achat Google Play **réel déjà confirmé**, via le SDK RevenueCat, puis exige un Droit Pro vérifié par Supabase auprès de RevenueCat. Il ne fabrique aucun reçu ni CustomerInfo de qualification.

Prérequis :

1. Projet RevenueCat AquaLog commun, produits et droit `aqualog_pro`, restauration **Keep with original App User ID**, accès Play et notifications configurés selon [revenuecat-setup.md](revenuecat-setup.md).
2. App `com.maximerollin.aqualog` enregistrée dans Play, compte Google license tester, abonnement `aqualog_pro_annual` à 14,99 €/an avec essai 14 jours et achat non consommable `aqualog_pro_lifetime` à 34,99 €.
3. Build debug préparé pour l’instrumentation avec la configuration Supabase commune et `AQUALOG_REVENUECAT_PUBLIC_API_KEY`. Le backend dispose de ses secrets RevenueCat v1, identité HMAC, allowlist sandbox pour le compte de qualification et webhook.
4. Sur **cette même installation et signature**, s’authentifier dans Aquagenda puis confirmer explicitement l’achat annuel avec une carte de test dans la feuille native Play. Ne pas effacer les données ni changer de signature entre l’achat et le test.
5. Contrôler l’apparition du client et de son droit dans RevenueCat et Supabase, sans publier JWT, App User ID, token ou clé secrète.

```sh
./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.runGooglePlayTestTransaction=true
```

Ce test s’ajoute au parcours complet sur le candidat signé non débogable installé depuis la piste fermée. Une transaction Test Store RevenueCat et les tests déterministes locaux ne remplacent aucune de ces validations. Sans accès externes et achat réel préparé, le test est ignoré et #19 reste ouverte.
