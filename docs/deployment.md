# Déploiement AquaLog

Une application Play `com.maximerollin.aqualog`, un projet RevenueCat, un Supabase, un PostHog et un LogSnag. Ajouter un projet Firebase/Crashlytics si les rapports techniques doivent être activés. Les pistes internal, alpha et production partagent ces ressources ; `release_channel` permet de filtrer les analyses. Debug et Firebase Test Lab désactivent PostHog et Crashlytics. Debug désactive aussi les notifications d’avis LogSnag.

## Contrôles avant déploiement

`.github/workflows/verify-and-publish.yml` lance sur PR, main et integration : tests JVM, unités Android debug/release, lint, assemblage release, garde-fous de configuration, tests Edge, migrations et tests SQL/RLS, intégration HTTP authentifiée et instrumentation sur Pixel_9a API 36. Le backend CI a un projet et des ports dédiés ; il ne réinitialise pas la stack historique #10.

La publication est manuelle (`workflow_dispatch`) et dépend de la réussite de ces contrôles. Pistes disponibles : internal, alpha, production. Production exige main et la confirmation explicite, et prépare un **brouillon** Play. Configurer dans GitHub les environments `google-play-test` et `google-play-production`, avec approbation humaine obligatoire sur le second. Ces protections doivent être réglées dans GitHub ; le fichier workflow ne les crée pas. `workflow_dispatch` n’est proposé qu’une fois ce workflow présent sur la branche par défaut `main`.

Protections attendues :

- `google-play-test` : branches de déploiement limitées à `main` et `integration/aqualog-v1` ;
- `google-play-production` : branche `main` seule, relecteur obligatoire, auto-approbation interdite ;
- `main` et `integration/aqualog-v1` : PR et check « JVM, Android, backend and configuration checks » obligatoires, force-push et suppression interdits.

Les actions du workflow sont épinglées par SHA de commit, car le job de publication manipule la clé d’upload et le compte de service Play.

## Configuration GitHub

Tous les secrets ci-dessous sont des secrets d’**environment** (présents dans les deux environments), jamais des secrets de dépôt : seul le job de publication, après les contrôles et sur une branche autorisée, peut les lire.

Secrets de signature/distribution : `KEYSTORE` (base64 de la clé d’upload), `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `PLAY_STORE_SERVICE_ACCOUNT_JSON` (compte de service dédié à la publication, distinct de celui de RevenueCat).

Configuration publique de l’app transmise comme secrets GitHub :

- `AQUALOG_SUPABASE_URL`, `AQUALOG_SUPABASE_ANON_KEY` ;
- `AQUALOG_REVENUECAT_PUBLIC_API_KEY` (`goog_…`) ;
- `AQUALOG_POSTHOG_API_KEY`, `AQUALOG_POSTHOG_HOST` ;
- `AQUALOG_FIREBASE_APPLICATION_ID`, `AQUALOG_FIREBASE_API_KEY`, `AQUALOG_FIREBASE_PROJECT_ID` ;
- `AQUALOG_PRIVACY_URL`, `AQUALOG_SUPPORT_URL` ;
- `AQUALOG_LOGSNAG_PROJECT`, `AQUALOG_LOGSNAG_REVIEW_PUBLIC_TOKEN` (Public, limité à reviews).

Les groupes Supabase/PostHog/Firebase/LogSnag doivent être complets ou entièrement absents ; la publication exige Supabase, RevenueCat et les URLs de confidentialité/support. Le workflow choisit `AQUALOG_DISTRIBUTION_TRACK`, `versionCode=GITHUB_RUN_NUMBER`, `versionName=0.1.<run>`. Maintenir un versionCode supérieur à toute version déjà téléversée et conserver la même clé d’upload. Un premier téléversement manuel et les formalités Play Console peuvent être nécessaires avant l’accès API. Les secrets serveur RevenueCat/LogSnag et service_role restent exclusivement dans Supabase : voir [configuration des achats](revenuecat-setup.md).

Le debug porte le même package, mais sa signature diffère normalement de Play : il ne peut pas cohabiter avec l’app du store. Préserver les données via export avant un changement d’installation nécessitant une désinstallation ; aucune suppression automatique n’est prévue. Les anciens packages `.dev`/`.beta` ne sont pas migrés automatiquement.

## Qualification locale

`GITHUB_RUN_ID=<id-unique> python3 tools/ci/backend.py prepare`, puis `verify`, puis `stop` avec le même identifiant. Ne jamais lancer/reset/stop directement le projet Supabase historique du dépôt. L’outil refuse de reprendre un répertoire existant. Seules les valeurs publiques du backend sont exportées vers GITHUB_ENV.

`tools/qualification/installations.py --help` qualifie l’export/import entre deux bases dédiées dans le même package, avec reprise après arrêt de processus. Ce protocole ne prouve pas une synchronisation entre deux appareils physiques.

Avant production, restent nécessaires : configuration des comptes externes, politique de confidentialité publiée incluant les prestataires, parcours TalkBack manuel, routine réelle et transactions Play de test. Les tests locaux ne constituent pas une preuve de paiement réel ni d’exécution réussie de GitHub Actions.
