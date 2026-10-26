# Configuration RevenueCat pour AquaLog

Choix du propriétaire : RevenueCat, le 26 octobre 2026. Le code Android utilise le SDK RevenueCat ; Google Play reste le store qui présente et traite le paiement. Supabase garde Auth, les Aquariums et le contrôle serveur du Droit Pro. Aucun projet externe n’a été créé ou déployé par cette migration.

## À créer maintenant

1. Dans RevenueCat, créer **AquaLog Beta**, un projet dédié. Le futur projet de production sera distinct, avec ses propres clés et un autre secret d’identité.
2. Ajouter une application **Google Play** avec le package **`com.maximerollin.aqualog.beta`**. Le package production est `com.maximerollin.aqualog` ; le build debug utilise `.dev` et ne doit pas emprunter les identifiants bêta.
3. Dans Play Console, préparer l’application bêta, son candidat signé, les testeurs de licence et la piste fermée. Connecter à RevenueCat le compte de service Google demandé par son assistant de configuration et lui accorder les permissions Play requises. Cette clé JSON reste côté RevenueCat, jamais dans l’APK ou Git.
4. Créer/importer les produits Play et les rattacher au droit RevenueCat **`aqualog_pro`** :
   - **`aqualog_pro_annual`** : abonnement annuel, 14,99 € en France, avec offre d’essai gratuit de 14 jours ; phase récurrente annuelle après l’essai.
   - **`aqualog_pro_lifetime`** : achat intégré non consommable à 34,99 € en France.
   Les prix effectivement affichés viennent du catalogue Play localisé. L’app conserve son paywall Compose et récupère directement les produits ; un Offering RevenueCat n’est pas nécessaire pour cette version. Elle sélectionne explicitement l’option annuelle avec essai et ne la remplace pas silencieusement par un abonnement immédiatement payant.
5. Dans les réglages de restauration RevenueCat, choisir **Keep with original App User ID**. Chaque achat reste attaché au compte AquaLog d’origine ; changer de compte Play/AquaLog ne doit pas transférer Pro à un autre compte.
6. Configurer les **Google Real-time Developer Notifications** vers RevenueCat selon son assistant, puis vérifier la réception d’un événement réel de test. Le raccordement des notifications du store reste nécessaire pour les remboursements et changements survenant lorsque l’app est fermée.

## Clés et environnements

La clé publique Android RevenueCat (`goog_…`) se renseigne via la propriété Gradle ou variable d’environnement **`AQUALOG_BETA_REVENUECAT_PUBLIC_API_KEY`**. Les préfixes `AQUALOG_DEV_` et `AQUALOG_PROD_` correspondent aux deux autres builds. Une clé identique entre environnements, une clé secrète `sk_…` ou une clé de Test Store `test_…` provoque un refus du build. Sans clé, la copie locale et le mode Free restent utilisables ; les achats sont indisponibles.

Secrets à renseigner uniquement dans le projet Supabase bêta :

| Nom | Valeur attendue |
|---|---|
| `REVENUECAT_SECRET_API_KEY` | Clé **secrète API v1** du projet RevenueCat bêta, autorisée à lire les clients et à les supprimer. |
| `REVENUECAT_APP_USER_ID_SECRET` | Secret aléatoire d’au moins 32 caractères, unique à cet environnement. Il produit un identifiant HMAC opaque pour chaque compte Supabase. |
| `REVENUECAT_ENVIRONMENT` | `sandbox` pour les transactions de test bêta ; `production` dans le backend de production. |
| `REVENUECAT_WEBHOOK_AUTHORIZATION` | Valeur d’en-tête complète, par exemple `Bearer <secret-aléatoire>`, identique à celle du webhook RevenueCat. |

Les secrets Supabase existants et `AQUALOG_DELETION_SIGNING_SECRET` restent requis. Ne transmettre aucune clé secrète dans une conversation ou un commit. La clé publique SDK est la seule clé RevenueCat embarquée.

Ne pas changer le secret d’identité à la légère : la base refuse le remplacement de l’App User ID d’un compte déjà enregistré. Une rotation exige une migration des identités RevenueCat, pas seulement une nouvelle variable.

## Raccordement Supabase

Après identification explicite du projet bêta cible :

1. Appliquer les migrations du dépôt, dont `20261026120000_revenuecat_pro_entitlements.sql`.
2. Déployer `verify-pro-purchase`, `revenuecat-webhook` et la version actualisée de `delete-account`.
3. Dans RevenueCat, créer un webhook vers **`https://<projet-beta>.supabase.co/functions/v1/revenuecat-webhook`**, avec l’en-tête Authorization ci-dessus et les événements sandbox du projet bêta. La fonction ne vérifie pas un JWT Supabase : elle vérifie son secret de webhook. Les fonctions appelées par l’app continuent à authentifier le compte AquaLog.
4. Envoyer l’événement de test du dashboard et contrôler la réponse. Ce test de câblage ne remplace pas une transaction Google Play de test.

L’API interne `verify-pro-purchase` garde son URL mais n’accepte plus de token Play fourni par le client. Elle expose uniquement `purchaseIdentity` et `loadEntitlement`. Le serveur déduit l’identité du JWT Supabase, interroge RevenueCat et conserve le résultat dans `pro_entitlements`. Un callback SDK déclenche une relecture serveur ; son contenu ne débloque jamais Pro à lui seul.

Le webhook réinterroge lui aussi RevenueCat, au lieu d’appliquer directement les dates du payload. La base rejette les snapshots plus anciens, bloque les identités en cours de suppression et ne permet ni la lecture des identités de facturation ni l’écriture des Droits Pro par un client.

Les anciens secrets `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `GOOGLE_PLAY_PACKAGE_NAME` et `GOOGLE_PLAY_ACCOUNT_HMAC_SECRET` ne sont plus utilisés par les fonctions de cette version. Les tables/migrations Play historiques sont conservées pour l’audit et les données existantes. Cette migration vise l’état actuel, sans clientèle Play payante qualifiée ; si des ventes réelles existent déjà, importer leurs reçus dans RevenueCat avant ce basculement et qualifier leur conservation.

## Validation externe avant distribution

Sur le candidat bêta signé installé depuis Play, avec un compte de licence :

- S’authentifier dans AquaLog, acheter l’annuel, confirmer la feuille Play et contrôler `aqualog_pro` dans RevenueCat puis le Droit Pro Supabase.
- Vérifier l’essai explicite, annulation du paiement, paiement en attente, renouvellement, annulation de l’abonnement avec accès jusqu’à expiration, grâce de paiement, expiration et remboursement/révocation.
- Tester l’achat à vie et sa restauration après réinstallation ; tenter une restauration sous un autre compte AquaLog et vérifier son refus selon Keep with original App User ID.
- Contrôler les deux chaînes de notifications : Google → RevenueCat et RevenueCat → Supabase, y compris un changement lorsque l’app est fermée, la répétition d’un webhook et une panne/reprise du backend.
- Tester la suppression d’un compte : suppression du client RevenueCat avant les données/Auth Supabase, reprise après échec, plus de mapping après réussite. Cette suppression de données ne constitue pas l’annulation d’un abonnement Google Play ; l’utilisateur doit gérer celui-ci dans Play.
- Vérifier les projets séparés et le refus d’une transaction sandbox en production. Le Test Store RevenueCat ne constitue pas une preuve Google Play.

L’opt-in `GooglePlayTestTransactionTest` restaure via RevenueCat puis exige la confirmation du backend. Voir [le protocole](google-play-test-transaction.md).

Références : [SDK Android](https://www.revenuecat.com/docs/getting-started/installation/android), [identités](https://www.revenuecat.com/docs/customers/identifying-customers), [notifications du store](https://www.revenuecat.com/docs/platform-resources/server-notifications), [modèle Customer Info API v1](https://www.revenuecat.com/docs/api-v1/customer-info-model).

Les vérifications locales et leurs limites sont consignées dans [le bilan de migration](revenuecat-validation.md).
