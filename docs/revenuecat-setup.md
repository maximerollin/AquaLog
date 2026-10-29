# RevenueCat : configuration Aquagenda

Configuration actuelle : **une app Play `com.maximerollin.aqualog`, un projet RevenueCat AquaLog et un projet Supabase**. Les pistes Play internal, alpha (test fermé) et production utilisent la même clé publique Google Play `goog_…`. Aucun projet « Beta » n’est nécessaire.

## Produits et default offering

| Package dans l’offering | Produit Google Play | Prix de référence | Configuration |
| --- | --- | --- | --- |
| Yearly (`$rc_annual`) | `aqualog_pro_annual` | 14,99 €/an | Abonnement annuel, offre d’essai gratuit de 14 jours pour les utilisateurs éligibles |
| Lifetime (`$rc_lifetime`) | `aqualog_pro_lifetime` | 34,99 € | Achat unique non consommable |

Ne pas créer de package Monthly. Rattacher les deux produits au même entitlement **`aqualog_pro`** et à l’offering **`default`**, marqué current. Les prix affichés dans l’app viennent de Google Play ; configurer les prix locaux dans la console. Le code charge actuellement les produits par leurs identifiants canoniques : changer seulement l’offering ne change pas les produits disponibles dans l’app.

1. Ajouter l’application **Google Play** dans RevenueCat, avec `com.maximerollin.aqualog`.
2. Relier Google Play avec le compte de service et les autorisations demandées par RevenueCat ; activer les notifications serveur Play selon son assistant.
3. Régler la restauration sur **Keep with original App User ID** pour éviter de transférer un achat vers un autre compte Aquagenda. Créer/activer les produits dans Play, puis les importer dans RevenueCat. Pour l’annuel, activer le base plan annuel et son offre d’essai de 14 jours. Pour le lifetime, conserver un achat non consommable.
4. Donner à l’app la clé publique SDK via `AQUALOG_REVENUECAT_PUBLIC_API_KEY`. Ni clé `sk_…`, ni clé Test Store `test_…` dans le build.

## Supabase : secrets serveur

| Secret | Usage |
| --- | --- |
| `REVENUECAT_SECRET_API_KEY` | Clé secrète API **V1** `sk_…` utilisée pour lire les droits côté serveur |
| `REVENUECAT_APP_USER_ID_SECRET` | Secret aléatoire stable d’au moins 32 caractères pour dériver un identifiant opaque par compte ; ne pas changer après lancement |
| `REVENUECAT_WEBHOOK_AUTHORIZATION` | Valeur Authorization forte et identique dans le webhook RevenueCat et Supabase |
| `REVENUECAT_SANDBOX_APP_USER_IDS` | Liste séparée par espaces/virgules des identifiants opaques de comptes de test autorisés ; vide par défaut |

Les secrets Supabase existants et `AQUALOG_DELETION_SIGNING_SECRET` restent nécessaires. Aucune clé privilégiée dans l’APK ni dans Git. L’identifiant RevenueCat est dérivé côté serveur ; ne pas utiliser l’adresse email ni l’UUID Supabase dans l’allowlist.

Appliquer les migrations versionnées, puis déployer `migrate-initial-copy`, `verify-pro-purchase`, `revenuecat-webhook`, `delete-account` et `submit-feedback`. Ajouter `aqualog://auth/callback` aux redirections Auth et configurer Google et email OTP.

Créer le webhook RevenueCat vers `https://<project>.supabase.co/functions/v1/revenuecat-webhook`, pour les événements sandbox **et** production, avec l’Authorization partagée. Cette fonction vérifie son secret de webhook ; les endpoints de l’app vérifient la session Supabase. Une notification ne suffit jamais à accorder Pro : le serveur relit les droits RevenueCat, vérifie le produit, le store, l’expiration et l’accès sandbox avant de stocker le résultat.

## Tests et production dans le même projet

Utiliser des comptes Aquagenda de qualification distincts des comptes réels. Après leur connexion, relever leur identifiant opaque dans `revenuecat_customers` avec un accès administrateur. Ajouter les mêmes IDs :

- dans RevenueCat → Project settings → General → Sandbox Testing Access → **Allowed App User IDs only** ;
- dans le secret Supabase `REVENUECAT_SANDBOX_APP_USER_IDS`.

Les achats production valides sont acceptés pour tous les comptes ; les achats sandbox seulement pour ces comptes de qualification. Ne pas utiliser l’ancien `REVENUECAT_ENVIRONMENT`. Ne pas réutiliser un compte de test comme compte client.

Dans Play Console, ajouter le compte Google aux **license testers**, puis installer depuis la piste internal/alpha et sélectionner une **carte de test**. La piste de test seule ne garantit pas l’absence de débit. Tester achat annuel/essai, renouvellement, annulation, expiration, lifetime, restauration et changement de compte avec le [protocole réel](google-play-test-transaction.md). Une simulation ou un achat Test Store RevenueCat ne valide pas Google Play.

## LogSnag

Décision du 26 octobre 2026 : LogSnag est réintroduit pour les paiements et les demandes de flux d’avis, après son retrait du périmètre initial V1.

Créer le projet slug `aqualog`, avec les channels `payments`, `payments-test` et `reviews`.

- Supabase uniquement : `LOGSNAG_PROJECT=aqualog`, `LOGSNAG_PRIVATE_TOKEN` limité aux channels de paiement.
- Build Android : `AQUALOG_LOGSNAG_PROJECT=aqualog`, `AQUALOG_LOGSNAG_REVIEW_PUBLIC_TOKEN` de type **Public**, limité au projet et au channel `reviews`. Jamais le token privé serveur dans ces champs.

Les paiements proviennent des webhooks authentifiés après réconciliation des droits. Les essais ont un libellé distinct. Les événements sandbox vont dans `payments-test`, sans notification push. Les reçus SHA-256 privés limitent les doublons et permettent les reprises ; une panne entre acceptation HTTP par LogSnag et enregistrement du reçu peut encore produire un doublon. Une indisponibilité LogSnag retourne 503 au webhook pour reprise sans annuler le droit déjà réconcilié.

Les avis indiquent une **demande de flux d’avis**, sans affirmer l’affichage du dialogue ni la publication d’un avis. Aucun identifiant de compte, email, contenu d’aquarium ou token de transaction n’est envoyé à LogSnag. Les événements d’avis sont best effort et ne bloquent jamais le flux Google Play. Voir [déploiement](deployment.md) pour la CI.

Références : [sandbox RevenueCat](https://www.revenuecat.com/docs/projects/sandbox-access), [tests Play Billing](https://developer.android.com/google/play/billing/test), [tokens publics LogSnag](https://docs.logsnag.com/quick-start), [limites des avis Play](https://developer.android.com/guide/playcore/in-app-review/kotlin-java).
