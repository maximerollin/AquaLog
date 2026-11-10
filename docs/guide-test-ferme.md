# Guide : du dépôt au test fermé Google Play

Ce guide te mène, étape par étape, de l'état actuel du dépôt à un **test fermé** ouvert sur Google Play (issue #19). Tu le suis seul, en 2 ou 3 sessions. Chaque phase peut s'arrêter proprement : coche les cases au fur et à mesure.

**À quoi sert le test fermé ?** Faire installer l'app depuis Google Play à quelques testeurs choisis, avec le vrai circuit : compte, synchronisation, achats avec carte de test, avis, crashs. C'est la seule façon de valider ce que les tests automatiques ne prouvent pas (voir [qualification](v1-closed-test-qualification.md)). Les tests locaux ne remplacent ni les vrais services, ni un humain avec un téléphone.

Guide détaillé de référence : [déploiement](deployment.md), [RevenueCat](revenuecat-setup.md), [transaction Play](google-play-test-transaction.md), [confidentialité](privacy.md) et [observabilité](privacy-observability.md).

> **Note sur les menus.** Les sources du dépôt donnent les noms des sections des consoles, pas des chemins de menu garantis. Les consoles changent : si un libellé diffère, cherche la section du même nom.

Légende : **[DÉCISION]** = à trancher avant. **[PORTE À SENS UNIQUE]** = impossible ou très pénible à changer après coup. **[À VÉRIFIER]** = les sources se taisent ou se contredisent.

## Avant de commencer

Comptes à avoir (tous gratuits ou déjà payés, sauf le compte développeur Play) :

- [ ] Compte **Google Play Console** (compte développeur Play).
- [ ] Compte **Supabase** et la CLI `supabase` installée (version 2.108.0 utilisée par la CI).
- [ ] Compte **RevenueCat**.
- [ ] Compte **GitHub** avec accès admin au dépôt (environments, secrets).
- [ ] Optionnel mais recommandé : **PostHog**, **Firebase** (Crashlytics), **LogSnag**.
- [ ] Un téléphone Android récent, avec un compte Google qui servira de **testeur de licence** (license tester).
- [ ] Une page web pour la **politique de confidentialité** et une adresse de **support** (URL). Les deux sont obligatoires pour publier : le workflow s'arrête sans elles.
- [ ] Un gestionnaire de mots de passe : tu vas créer une dizaine de secrets. Ne les mets jamais dans Git ni dans une discussion.

**Ordre à respecter** : Supabase avant RevenueCat (le webhook a besoin de la fonction déployée), Play avant RevenueCat (les produits viennent de Play), tout avant la publication CI, la CI avant d'inviter les testeurs.

---

## Phase 0 — Décisions (30 min)

- [ ] **0.1 [DÉCISION] Fixer les prix.**
  - **Où** : spec [§4.7 Gratuit et Pro](superpowers/specs/2026-10-30-refonte-aquagenda-design.md).
  - **Quoi** : deux produits. Annuel `aqualog_pro_annual` avec essai gratuit de 14 jours ; à vie `aqualog_pro_lifetime`. Prix de référence actuel : 14,99 €/an et 34,99 € à vie. La spec recommande 24,99 à 29,99 €/an et 69,99 à 79,99 € à vie quand l'assistance sortira. Tu n'as pas tranché : c'est à toi.
  - **Pourquoi maintenant** : tu saisis les prix à la phase 2. Les prix se modifient ensuite dans Play, mais les docs de test citent 14,99 € et 34,99 € ; mets-les à jour si tu changes.
  - **Vérifier** : tu as écrit les deux montants quelque part.
- [ ] **0.2 [DÉCISION] Confirmer le périmètre Gratuit / Pro.**
  - **Où** : spec §4.7.
  - **Quoi** : Gratuit = style, accueil, couleurs, pistes, rythme, série, habitants et encyclopédie, photo d'un bac. Pro = widget, bilan détaillé, multi-bac, historique au-delà de 90 jours. La fiche Play doit dire la même chose.
  - **Vérifier** : la description de la fiche (phase 2) correspond à cette liste.
- [ ] **0.3 [DÉCISION] Choisir ce que tu actives en observabilité.**
  - **Quoi** : PostHog, Crashlytics et LogSnag sont tous optionnels pour publier. Chaque service activé doit apparaître dans la politique de confidentialité et le formulaire « Sécurité des données ». Plus tu actives, plus tu déclares. Les groupes sont « tout ou rien » (voir phase 4).
  - **Vérifier** : tu as noté « PostHog : oui/non, Crashlytics : oui/non, LogSnag : oui/non ».
- [ ] **0.4 Choisir les testeurs** (une dizaine de comptes Google, dont toi) et une adresse e-mail de groupe ou une liste. Prévois deux comptes **Aquagenda** de qualification, séparés de tes vrais comptes.

---

## Phase 1 — Supabase de production (1 h)

Contexte : un seul Supabase sert à tous les canaux (interne, test fermé, production). Les données de test fermé sont donc de vraies données de production.

- [ ] **1.1 Créer le projet.**
  - **Où** : tableau de bord Supabase → nouveau projet.
  - **Quoi** : un nom, une région proche des utilisateurs, un mot de passe de base (garde-le au coffre).
  - **Vérifier** : tu vois l'URL `https://<projet>.supabase.co` et la clé « anon » (publique). Note l'URL et la clé anon : ce sont `AQUALOG_SUPABASE_URL` et `AQUALOG_SUPABASE_ANON_KEY` (phase 5). La clé `service_role` ne sort jamais de Supabase.
- [ ] **1.2 Relier la CLI au projet.**
  - **Où** : terminal, à la racine du dépôt. **Ne lance jamais `supabase start`, `db reset` ou `stop` ici** : la config historique (`aqualog-ticket-10`) appartient à la pile locale d'un autre ticket.
  - **Quoi** : `supabase link --project-ref <référence du projet>` puis connecte-toi si la CLI le demande.
  - **Vérifier** : la commande répond sans erreur.
  - **[À VÉRIFIER]** : les sources ne montrent que `db push` et `functions deploy`, pas `link`. C'est la méthode standard de la CLI.
- [ ] **1.3 Appliquer toutes les migrations.**
  - **Où** : `supabase db push`.
  - **Quoi** : les 20 fichiers de `supabase/migrations/`, dans l'ordre :
    `20261006090000_initial_account_copy`, `20261007090000_pro_entitlements`, `20261007110000_entity_sync_rls`, `20261010100000_session_child_sync_delete_rls`, `20261012120000_recoverable_entity_sync`, `20261013090000_secure_pro_purchase_claims`, `20261013100000_server_ordered_lww`, `20261014090000_revalidate_pro_entitlements`, `20261014100000_pro_entitlement_expiry_guard`, `20261014105000_account_deletion`, `20261014110000_scope_deletion_receipts`, `20261015100000_lifetime_pro_multi_aquarium`, `20261016090000_product_feedback`, `20261026120000_revenuecat_pro_entitlements`, `20261026130000_logsnag_delivery_receipts`, `20261102100000_parameter_analysis_interval`, `20261104100000_aquarium_photos`, `20261105100000_inhabitants`, `20261105110000_inhabitants_checks`, `20261110100000_product_feedback_context`.
  - **Vérifier** : dans le tableau de bord → Table editor, la table `inhabitants` existe (sinon chaque synchronisation échoue). La table `product_feedback` existe aussi. Le bucket de photos `aquarium-photos` est créé par la migration `20261104100000` : tu n'as rien à créer à la main ; contrôle-le dans Storage.
- [ ] **1.4 Déployer les cinq fonctions.**
  - **Où** : terminal.
  - **Quoi** :
    ```bash
    supabase functions deploy migrate-initial-copy
    supabase functions deploy verify-pro-purchase
    supabase functions deploy revenuecat-webhook
    supabase functions deploy delete-account
    supabase functions deploy submit-feedback --no-verify-jwt
    ```
    `--no-verify-jwt` sur `submit-feedback` est **obligatoire** : la fonction accepte les retours anonymes (sans compte) et vérifie elle-même le jeton quand il y en a un. Ne te repose pas sur `config.toml` seul.
  - **Vérifier** : les cinq fonctions sont listées dans le tableau de bord → Edge Functions.
  - **[À VÉRIFIER]** : `config.toml` met aussi `verify_jwt = false` sur `delete-account` et `revenuecat-webhook`, mais les docs ne demandent `--no-verify-jwt` explicite que pour `submit-feedback`. Après déploiement, regarde dans le tableau de bord que « Verify JWT » est bien désactivé pour ces deux-là ; sinon redéploie avec `--no-verify-jwt`. Le webhook se protège par son propre secret (1.6).
- [ ] **1.5 Contrôle de `submit-feedback` (3 requêtes).**
  - **Où** : terminal, avec `URL=https://<projet>.supabase.co/functions/v1/submit-feedback`. Les trois commandes `curl` sont dans [deployment.md](deployment.md), section « Avis et commentaires ».
  - **Vérifier** : tu obtiens `400` (corps vide), `204` (retour anonyme valide), `401` (jeton invalide). Ensuite, **supprime** la ligne créée par le 2e appel : `delete from product_feedback where message = 'smoke test';` (éditeur SQL du tableau de bord).
- [ ] **1.6 Définir les secrets des fonctions.**
  - **Où** : tableau de bord → Edge Functions → secrets (ou la CLI).
  - **Quoi** (valeurs aléatoires fortes, tirées au coffre ; détail dans [revenuecat-setup.md](revenuecat-setup.md)) :
    - `REVENUECAT_SECRET_API_KEY` : clé secrète API **V1** `sk_…` de RevenueCat (tu l'obtiens en phase 3 : reviens ici ensuite).
    - `REVENUECAT_APP_USER_ID_SECRET` : aléatoire, au moins 32 caractères. **[PORTE À SENS UNIQUE]** : ne plus jamais le changer après le lancement.
    - `REVENUECAT_WEBHOOK_AUTHORIZATION` : valeur forte, **identique** à celle du webhook RevenueCat (phase 3).
    - `REVENUECAT_SANDBOX_APP_USER_IDS` : vide pour l'instant (tu la rempliras en phase 6).
    - `AQUALOG_DELETION_SIGNING_SECRET` : aléatoire, pour la suppression de compte.
    - Si LogSnag : `LOGSNAG_PROJECT=aqualog` et `LOGSNAG_PRIVATE_TOKEN` (phase 4).
    - Ne définis **pas** `AQUALOG_ACCOUNT_STORAGE_BUCKETS` (le défaut vide déjà `aquarium-photos`).
  - **Vérifier** : les noms apparaissent dans la liste des secrets (les valeurs restent masquées).
  - **[À VÉRIFIER]** : les sources donnent les noms, pas la commande exacte pour les poser (la CLI propose `supabase secrets set`).
- [ ] **1.7 Configurer l'authentification.**
  - **Où** : tableau de bord → Authentication.
  - **Quoi** : ajouter `aqualog://auth/callback` aux URL de redirection autorisées ; activer le fournisseur Google et le code e-mail (OTP).
  - **Vérifier** : la redirection est listée. La connexion réelle se teste en phase 6.
  - **[À VÉRIFIER]** : les sources ne décrivent pas la création du client OAuth Google (console Google Cloud). Prévois ce temps. Elles ne précisent pas non plus si la confirmation d'e-mail est active en production (`enable_confirmations = false` n'est écrit que pour la pile locale).

---

## Phase 2 — Google Play Console (2 à 3 h, hors délais de validation)

- [ ] **2.1 [PORTE À SENS UNIQUE] Créer l'application.**
  - **Où** : Play Console → Créer une application.
  - **Quoi** : nom « Aquagenda », langue par défaut français, application (pas jeu), gratuite. Le **nom de package** `com.maximerollin.aqualog` se fixe au premier envoi d'un bundle ; il ne pourra jamais être changé ni réutilisé. Il est déjà dans le workflow.
  - **Vérifier** : l'app apparaît dans la liste, tableau de bord de configuration visible.
- [ ] **2.2 Fiche Play (présentation principale).**
  - **Où** : Croissance → Présence sur le Store → Fiche principale (selon la console).
  - **Quoi** : titre, description courte et longue (reprends la liste Gratuit / Pro de 0.2), **icône 512 × 512** : `docs/store/aqualog-play-icon-512.png`. Captures d'écran : à faire sur ton téléphone. Une image de présentation (feature graphic) est aussi demandée en général.
  - **Vérifier** : la console n'affiche plus d'erreur de format sur l'icône.
  - **[À VÉRIFIER]** : `docs/store/` ne contient que des icônes. Aucune capture, ni image de présentation, ni texte de fiche n'existent dans le dépôt.
- [ ] **2.3 Politique de confidentialité hébergée.**
  - **Où** : à héberger sur une page publique de ton choix, puis URL saisie dans Play (Contenu de l'application → Politique de confidentialité) et dans le secret `AQUALOG_PRIVACY_URL` (phase 5).
  - **Quoi** : elle doit mentionner : comptes et synchronisation (Supabase), photos des bacs, habitants, retours des utilisateurs **avec ou sans compte** (identifiant d'installation aléatoire utilisé seulement pour limiter les envois ; les retours anonymes ne sont pas supprimés avec le compte), demande d'avis Google Play (5e Session calme, puis au plus tous les 30 jours), **RevenueCat** et Google Play pour les achats, **LogSnag** si activé, **PostHog** et **Crashlytics** si activés. Source : [privacy.md](privacy.md) et [privacy-observability.md](privacy-observability.md) (en anglais : à traduire et adapter).
  - **Vérifier** : l'URL s'ouvre sans connexion, sur téléphone.
  - **[À VÉRIFIER]** : les sources ne disent pas où l'héberger.
- [ ] **2.4 Adresse de support.**
  - **Quoi** : une URL publique ou une page de contact ; ce sera `AQUALOG_SUPPORT_URL`. Le dépôt n'en fournit aucune ([support.md](support.md)).
  - **Vérifier** : l'URL s'ouvre sans connexion.
- [ ] **2.5 Formulaire « Sécurité des données ».**
  - **Où** : Contenu de l'application → Sécurité des données.
  - **Quoi** : déclare, selon [privacy.md](privacy.md) et [deployment.md](deployment.md) (liste de contrôle) : compte (e-mail via Supabase) ; contenu généré par l'utilisateur (aquariums, mesures, photos, habitants, **retours envoyés au développeur**) ; **identifiant aléatoire de l'appareil** pour les retours sans compte ; informations d'achat (RevenueCat) ; plantages/diagnostics si Crashlytics ; analyses si PostHog. Données chiffrées en transit. Suppression : possible depuis l'app.
  - **Vérifier** : le formulaire est « Terminé » et son résumé reflète la politique (2.3).
  - **[À VÉRIFIER]** : les sources n'indiquent pas le nom exact des catégories Play. Elles ne disent pas non plus si Play exige un lien web de suppression de compte en plus de la suppression dans l'app.
- [ ] **2.6 Autres déclarations de contenu.**
  - **Où** : Contenu de l'application → Classification du contenu, Public cible, Publicités, etc.
  - **Quoi** : questionnaire de classification (app de suivi d'aquarium, sans contenu sensible) ; public cible adulte/tout public selon ton choix ; l'app ne collecte pas l'identifiant publicitaire ([privacy.md](privacy.md)).
  - **Vérifier** : toutes les tâches du tableau de bord de configuration sont cochées.
  - **[À VÉRIFIER]** : les sources ne parlent ni de public cible, ni de classification, ni de la déclaration « contient des publicités ». À toi de répondre honnêtement.
- [ ] **2.7 [PORTE À SENS UNIQUE] Signature de l'app : clé d'upload.**
  - **Où** : ta machine, puis Play Console → Configuration → Intégrité de l'application → Signature d'application (Play App Signing).
  - **Quoi** : crée une clé d'upload (un fichier keystore) avec son alias et deux mots de passe. Garde-la **en double sauvegarde** : la CI doit toujours utiliser **la même** clé ([deployment.md](deployment.md)). Tu en auras besoin en phase 5 sous la forme `KEYSTORE` (le fichier encodé en base64), `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`.
  - **Vérifier** : tu peux rouvrir le keystore avec l'alias et le mot de passe notés.
  - **[À VÉRIFIER]** : les sources ne donnent ni la commande de création du keystore, ni celle de l'encodage base64.
- [ ] **2.8 [PORTE À SENS UNIQUE] Créer les produits.**
  - **Où** : Play Console → Monétiser avec Play → Produits (abonnements, produits uniques).
  - **Quoi** :
    - Abonnement `aqualog_pro_annual` : plan de base annuel au prix de 0.1, **offre d'essai gratuit de 14 jours**.
    - Produit unique non consommable `aqualog_pro_lifetime` au prix de 0.1.
    - Active-les. Les identifiants de produit ne peuvent plus être modifiés ni réutilisés ; copie-les exactement. Les prix locaux se règlent dans Play : l'app affiche ceux de Google.
  - **Vérifier** : les deux produits sont « Actifs ».
  - **[À VÉRIFIER]** : Play n'autorise peut-être la création des produits qu'après un premier bundle envoyé. Si c'est le cas, fais d'abord la phase 5 puis reviens ici.
- [ ] **2.9 Compte de service de publication.**
  - **Où** : Google Cloud (compte de service) puis Play Console → Utilisateurs et autorisations (inviter ce compte de service avec le droit de publier sur cette app).
  - **Quoi** : un compte de service **dédié à la publication**, différent de celui de RevenueCat. Télécharge sa clé JSON : ce sera `PLAY_STORE_SERVICE_ACCOUNT_JSON`.
  - **Vérifier** : le compte de service apparaît dans les utilisateurs de la console.
  - **[À VÉRIFIER]** : les sources disent qu'un « premier téléversement manuel et des formalités Play Console peuvent être nécessaires avant l'accès API ». Si la CI échoue en 5.4, c'est probablement ça.
- [ ] **2.10 Testeurs de licence.**
  - **Où** : Play Console → Paramètres → Test de licence.
  - **Quoi** : ajoute le compte Google du téléphone de test.
  - **Vérifier** : le compte est listé.

---

## Phase 3 — RevenueCat (1 h)

Voir [revenuecat-setup.md](revenuecat-setup.md). Un seul projet RevenueCat, une seule app Play. Pas de projet « Beta ».

- [ ] **3.1 Créer le projet et l'app Google Play.**
  - **Où** : tableau de bord RevenueCat → Project → Apps → ajouter Google Play.
  - **Quoi** : package `com.maximerollin.aqualog`. Relie Google Play avec un compte de service et les autorisations demandées par l'assistant RevenueCat (un compte de service **distinct** de celui de 2.9) ; active les notifications serveur Play.
  - **Vérifier** : l'app est connectée, sans avertissement.
- [ ] **3.2 Régler la restauration.**
  - **Quoi** : **Keep with original App User ID**. Cela évite de transférer un achat à un autre compte Aquagenda.
  - **Vérifier** : le réglage est enregistré.
- [ ] **3.3 Importer les produits.**
  - **Quoi** : `aqualog_pro_annual` et `aqualog_pro_lifetime`, depuis Play (ils doivent être actifs, voir 2.8).
  - **Vérifier** : les deux produits sont listés.
- [ ] **3.4 Entitlement.**
  - **Quoi** : créer **`aqualog_pro`** et lui rattacher les deux produits.
  - **Vérifier** : l'entitlement affiche deux produits.
- [ ] **3.5 Offering.**
  - **Quoi** : offering **`default`**, marqué « current », avec deux packages : Yearly (`$rc_annual`) → `aqualog_pro_annual` ; Lifetime (`$rc_lifetime`) → `aqualog_pro_lifetime`. **Pas de package Monthly.**
  - **Vérifier** : `default` est « current ».
- [ ] **3.6 Clés.**
  - **Quoi** : relève la clé publique SDK Google Play `goog_…` (ce sera `AQUALOG_REVENUECAT_PUBLIC_API_KEY`) ; crée une clé secrète API **V1** `sk_…` et saisis-la dans Supabase (`REVENUECAT_SECRET_API_KEY`, étape 1.6). Jamais de clé `sk_…` ni de clé Test Store `test_…` dans l'app.
  - **Vérifier** : la clé commence par `goog_`.
- [ ] **3.7 Webhook.**
  - **Où** : RevenueCat → Integrations → Webhooks.
  - **Quoi** : URL `https://<projet>.supabase.co/functions/v1/revenuecat-webhook` ; événements **sandbox et production** ; en-tête Authorization = la même valeur que `REVENUECAT_WEBHOOK_AUTHORIZATION` côté Supabase.
  - **Vérifier** : envoie l'événement de test proposé par RevenueCat ; la livraison est notée réussie (ou regarde les journaux de la fonction dans Supabase).
  - **[À VÉRIFIER]** : les sources ne disent pas ce que renvoie la fonction à un événement de test factice.

---

## Phase 4 — Observabilité (optionnelle, 45 min)

Tout est optionnel pour publier. **Règle** : Supabase, PostHog, Firebase et LogSnag forment chacun un groupe qui doit être **complet ou entièrement absent** ([deployment.md](deployment.md)).

- [ ] **4.1 PostHog (optionnel).**
  - **Quoi** : un projet ; relève `AQUALOG_POSTHOG_API_KEY` et `AQUALOG_POSTHOG_HOST`. Les événements sont filtrés par une liste fermée, sans identifiant utilisateur.
  - **Vérifier** (phase 6) : des événements arrivent, avec `release_channel` = `alpha`.
- [ ] **4.2 Firebase Crashlytics (optionnel).**
  - **Quoi** : un projet Firebase avec une app Android `com.maximerollin.aqualog` ; relève `AQUALOG_FIREBASE_APPLICATION_ID`, `AQUALOG_FIREBASE_API_KEY`, `AQUALOG_FIREBASE_PROJECT_ID`. Pas de fichier `google-services.json` nécessaire : les trois valeurs suffisent.
  - **Vérifier** (phase 6) : les rapports de crash n'ont ni nom, ni e-mail, ni valeur d'eau.
- [ ] **4.3 LogSnag (optionnel).**
  - **Quoi** : projet de slug `aqualog` avec les channels `payments`, `payments-test`, `reviews`. Côté Supabase : `LOGSNAG_PROJECT=aqualog` et `LOGSNAG_PRIVATE_TOKEN` (limité aux paiements). Côté app : `AQUALOG_LOGSNAG_PROJECT=aqualog` et `AQUALOG_LOGSNAG_REVIEW_PUBLIC_TOKEN`, un jeton de type **Public** limité au channel `reviews`. **Jamais** le jeton privé dans l'app.
  - **Vérifier** (phase 6) : un achat de test apparaît dans `payments-test`, sans notification push.
  - **[À VÉRIFIER]** : [v1-closed-test-qualification.md](v1-closed-test-qualification.md) (note historique) dit LogSnag « hors V1 » ; [revenuecat-setup.md](revenuecat-setup.md) le réintroduit (décision du 26 octobre 2026). Ce guide suit le second.

---

## Phase 5 — GitHub et publication (1 h)

- [ ] **5.1 Créer les deux environments.**
  - **Où** : GitHub → dépôt → Settings → Environments.
  - **Quoi** : `google-play-test` (branches de déploiement limitées à `main` et `integration/aqualog-v1`) et `google-play-production` (branche `main` seule, relecteur obligatoire, auto-approbation interdite). Protège aussi `main` et `integration/aqualog-v1` (PR et check « JVM, Android, backend and configuration checks » obligatoires, pas de force-push). Le fichier workflow ne crée rien de tout cela.
  - **Vérifier** : les deux environments existent. Pour un test fermé, seul `google-play-test` sert.
- [ ] **5.2 Poser les secrets (secrets d'environment, pas de dépôt).**
  - **Où** : Settings → Environments → `google-play-test` → secrets (et la même chose dans `google-play-production` pour plus tard).
  - **Quoi** :
    - Signature : `KEYSTORE`, `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `PLAY_STORE_SERVICE_ACCOUNT_JSON`.
    - **Obligatoires** : `AQUALOG_SUPABASE_URL`, `AQUALOG_SUPABASE_ANON_KEY`, `AQUALOG_REVENUECAT_PUBLIC_API_KEY`, `AQUALOG_PRIVACY_URL`, `AQUALOG_SUPPORT_URL`.
    - Si activés : `AQUALOG_POSTHOG_API_KEY` + `AQUALOG_POSTHOG_HOST` ; `AQUALOG_FIREBASE_APPLICATION_ID` + `AQUALOG_FIREBASE_API_KEY` + `AQUALOG_FIREBASE_PROJECT_ID` ; `AQUALOG_LOGSNAG_PROJECT` + `AQUALOG_LOGSNAG_REVIEW_PUBLIC_TOKEN`.
  - **Vérifier** : la liste des secrets de l'environment contient tous les noms attendus. Les groupes sont complets.
  - **[À VÉRIFIER]** : le workflow ne lit aucune *variable* GitHub, seulement des secrets, même pour les valeurs publiques.
- [ ] **5.3 Vérifier l'ordre de déploiement.**
  - **Quoi** : phase 1 entièrement terminée (migrations **et** `submit-feedback` déployée) **avant** de publier. Sinon chaque synchronisation échoue.
  - **Vérifier** : la table `inhabitants` existe, et les trois codes `400 / 204 / 401` sont obtenus.
- [ ] **5.4 Lancer la publication vers le test fermé.**
  - **Où** : GitHub → Actions → « Verify and publish » → Run workflow, branche `main`.
  - **Quoi** : `track` = `alpha` (c'est la piste « test fermé » du workflow). Laisse `confirm_production` décoché. Le job de vérification (environ 1 h) passe d'abord, puis le job « Signed bundle to Google Play » téléverse. `versionCode` = numéro d'exécution, `versionName` = `0.1.<run>`.
  - **Vérifier** : les deux jobs sont verts ; dans Play Console → Tests → Test fermé, une version apparaît.
  - **Si ça échoue** à l'envoi à cause de l'accès API (voir 2.9) : télécharge l'artefact `signed-app-bundle` de l'exécution, envoie-le à la main dans Play Console (nouvelle version du test fermé), puis relance la CI pour les versions suivantes.
  - **[À VÉRIFIER]** : le workflow appelle la piste `alpha`. Dans la console, un test fermé créé à la main peut avoir un autre nom. Utilise la piste « Alpha » existante, ou vérifie que la CI atteint bien celle que tu as créée.
- [ ] **5.5 Terminer la mise en ligne de la piste.**
  - **Où** : Play Console → Tests → Test fermé.
  - **Quoi** : choisis les pays, ajoute les testeurs (phase 6), enregistre, puis envoie la version en examen.
  - **Vérifier** : la version passe à « Disponible pour les testeurs » après l'examen de Google (délai variable).
  - **[À VÉRIFIER]** : Google peut imposer des conditions selon le type de compte développeur (nombre de testeurs, durée du test) avant la production. Les sources n'en parlent pas : lis ce que la console affiche.

---

## Phase 6 — Testeurs et qualification (2 sessions)

- [ ] **6.1 Liste de testeurs et lien d'inscription.**
  - **Où** : Play Console → Tests → Test fermé → Testeurs.
  - **Quoi** : crée une liste d'e-mails (ou un groupe Google), ajoute-la à la piste. Copie le **lien d'inscription** (opt-in), envoie-le aux testeurs.
  - **Vérifier** : toi, sur ton téléphone, tu ouvres le lien, acceptes, et installes l'app depuis Play.
- [ ] **6.2 Comptes de qualification et liste blanche sandbox.**
  - **Quoi** : crée deux comptes Aquagenda de qualification (pas tes vrais comptes). Après leur connexion, relève leur identifiant opaque dans la table `revenuecat_customers` (éditeur SQL, accès admin). Ajoute **ces identifiants** à deux endroits : RevenueCat → Project settings → General → Sandbox Testing Access → **Allowed App User IDs only**, et le secret Supabase `REVENUECAT_SANDBOX_APP_USER_IDS`. N'utilise ni e-mail ni UUID Supabase.
  - **Vérifier** : les deux listes contiennent les mêmes identifiants.
- [ ] **6.3 Test humain de 30 secondes (porte non négociable).**
  - **Quoi** : sur ton téléphone, sans compte et hors ligne : onboarding, un aquarium, puis trois mesures (exemple : 24,5 °C, pH 7,2, nitrites 0) et un changement d'eau de 20 %. Chronomètre de « Nouvelle Session » à la confirmation de sauvegarde. Répète. Note l'appareil, la version, la langue, chaque temps. Détail : [qualification, scénario étapes 1 à 3](v1-closed-test-qualification.md).
  - **Vérifier** : chaque temps est **strictement inférieur à 30 secondes**, avec une seule Session dans la Chronologie.
- [ ] **6.4 Scénario complet.**
  - **Quoi** : suis les étapes 4 à 10 du [scénario commun](v1-closed-test-qualification.md) : brouillon et arrêt forcé, réseau coupé puis rétabli, création de compte (Google puis lien magique), seconde installation, export/import, isolation entre deux comptes (RLS), 5 langues, clair/sombre, retour sans contenu personnel, suppression de compte. Consigne pour chaque étape : appareil, date, résultat, défaut éventuel (jamais de token, JWT, e-mail).
  - **Vérifier** : chaque étape a un résultat noté.
  - **[À VÉRIFIER]** : ce document de qualification est historique et parle encore d'un package bêta `.beta` et de variables `AQUALOG_DEV_/BETA_/PROD_`. Les docs actuels (déploiement, workflow) n'ont qu'un package `com.maximerollin.aqualog` et des variables `AQUALOG_*`. Suis les docs actuels.
- [ ] **6.5 Achats de test avec le testeur de licence.**
  - **Quoi** : depuis l'app installée via la piste de test, connecte-toi avec un compte de qualification, ouvre le paywall, achète l'annuel (essai 14 jours) en choisissant une **carte de test** dans la feuille Play. Puis : droit Pro visible, renouvellement, annulation, expiration, achat à vie, restauration après réinstallation, changement de compte. Protocole : [google-play-test-transaction.md](google-play-test-transaction.md). Le test instrumenté `GooglePlayTestTransactionTest` est facultatif.
  - **Vérifier** : le client et son droit `aqualog_pro` apparaissent dans RevenueCat et dans Supabase ; l'événement arrive dans `payments-test` (si LogSnag). Sans carte de test, l'achat peut être réellement débité : vérifie la feuille avant de confirmer.
  - **[À VÉRIFIER]** : le test instrumenté exige un build **debug** avec le même package, alors que [deployment.md](deployment.md) dit qu'un debug (autre signature) ne cohabite pas avec l'app du store. Fais-le avant d'installer depuis Play, ou sur un autre appareil.
- [ ] **6.6 Contrôle TalkBack de 5 minutes.**
  - **Où** : téléphone, Réglages → Accessibilité → TalkBack activé.
  - **Quoi** : trois contrôles ciblés. (1) Champ de commentaire : TalkBack annonce « Votre message ». (2) Recherche d'espèce dans l'encyclopédie : le champ est annoncé et les résultats se lisent un par un. (3) Le pager (pagination) annonce « 1 / 2 » et la page suivante.
  - **Vérifier** : les trois annonces sont claires, le focus ne se perd pas.
  - **[À VÉRIFIER]** : « Votre message » est bien le libellé du champ dans le code (`feedback_message_label`). Je n'ai pas retrouvé « 1 / 2 » dans les sources ; vérifie sur l'écran réel. [accessibility-validation.md](accessibility-validation.md) rappelle qu'une passe TalkBack manuelle reste nécessaire.
- [ ] **6.7 Contrôle des services (confidentialité).**
  - **Quoi** : dans PostHog et Crashlytics, vérifie qu'aucun événement ne contient de valeur d'eau, note, nom de bac, e-mail ou photo ; dans LogSnag, qu'aucun identifiant de compte n'apparaît.
  - **Vérifier** : rien de personnel n'est visible.
- [ ] **6.8 Nettoyer.**
  - **Quoi** : supprime les lignes de test `product_feedback` (`smoke test`) ; garde la liste blanche sandbox à jour ; ne réutilise jamais un compte de qualification comme compte client.
  - **Vérifier** : requête `select * from product_feedback` sans ligne de test.
- [ ] **6.9 Clore.**
  - **Quoi** : quand toutes les étapes sont notées, mets à jour [v1-closed-test-qualification.md](v1-closed-test-qualification.md) et ferme l'issue #19 sur GitHub. Tant qu'une gate manque, elle reste ouverte.

---

## Phase 7 — Après le lancement : quoi surveiller

- [ ] **7.1 Retours anonymes.** Dans l'éditeur SQL : `select * from product_feedback order by created_at desc;`. Les retours anonymes ont un identifiant d'installation ; les envois sont limités à 3 par installation et par heure, et 100 par heure au total. Un pic de `401` ou de refus = signal à regarder.
- [ ] **7.2 Plantages.** Crashlytics (si activé) et Play Console → Qualité → Plantages et ANR. Les fichiers `mapping-file` de la CI (conservés 90 jours) servent à lire les traces.
- [ ] **7.3 Avis.** Play Console → Avis. Rappel : l'app demande un avis à la 5e Session calme, puis au plus tous les 30 jours ; elle ne sait pas si un avis a été écrit. LogSnag `reviews` compte les **demandes**, pas les avis.
- [ ] **7.4 Paiements.** RevenueCat → Customers, canal LogSnag `payments`. Les achats production valides sont acceptés pour tous ; les achats sandbox seulement pour la liste blanche.
- [ ] **7.5 Rappels.** Chaque nouvelle version : migration d'abord, fonction ensuite, app en dernier. Le `versionCode` doit toujours augmenter (la CI s'en charge). Production plus tard : environment `google-play-production`, approbation humaine, et la CI ne prépare qu'un **brouillon**.

---

## Récapitulatif des portes à sens unique

- Nom de package `com.maximerollin.aqualog` (2.1).
- Clé d'upload : la garder identique et sauvegardée (2.7).
- Identifiants de produit `aqualog_pro_annual` et `aqualog_pro_lifetime` (2.8).
- Secret `REVENUECAT_APP_USER_ID_SECRET` : ne plus changer (1.6).
- Prix : modifiables après, mais décide-les avant (0.1).
