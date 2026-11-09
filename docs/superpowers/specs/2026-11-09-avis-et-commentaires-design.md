# Avis Google Play et commentaires au développeur

Statut : conception validée en discussion avec Maxime le 09/11/2026, à relire.
Issues : #41 (avis Play dans l'app), #42 (écrire au développeur).

## 1. Intention

Deux besoins proches, traités ensemble parce qu'ils touchent tous deux les réglages :

- **Recueillir des notes sur Google Play** au moment où l'utilisateur est content. Les notes pèsent sur le classement face à AquaDiary (~1,8K avis). Le mécanisme est celui de l'app Yams de Maxime, déjà éprouvé.
- **Recevoir les remarques des utilisateurs** — bug, traduction fausse, erreur dans une fiche de l'encyclopédie — y compris depuis l'endroit où ils voient l'erreur, et y compris sans compte.

## 2. Objectifs, non-objectifs, anti-objectifs

**Objectifs**
- La fenêtre de notation Google s'ouvre, au plus une fois tous les 90 jours, après une Session qui s'est bien passée, à partir de la 5ᵉ.
- Un message « Votre avis compte » l'annonce la première fois, comme dans Yams.
- « Noter Aquagenda » reste disponible dans les réglages.
- « Envoyer des commentaires » marche **avec ou sans compte**, porte un **type**, et peut s'ouvrir depuis une fiche espèce, pré-rempli.
- Maxime voit dans la table Supabase si un message vient d'un utilisateur avec compte ou sans.

**Non-objectifs**
- Pas de mail, pas d'adresse de support dédiée : la fonction Supabase existante reste le seul canal (décision de Maxime).
- Pas de réponse à l'utilisateur depuis l'app ; Maxime lit les messages dans la table.
- Pas de notification à Maxime à chaque message (LogSnag ou autre) : pas demandé.
- Pas de nouvel événement de télémétrie.
- iOS : la cible reste Android et le harnais desktop.

**Anti-objectifs (échec même si ça marche)**
- **Filtrer avant la notation** (« Vous aimez l'app ? » puis Play seulement pour les contents). Les règles Google l'interdisent ; l'app ne pose aucune question d'opinion avant la fenêtre Google et ne renvoie jamais les mécontents vers les commentaires à la place.
- **Demander une note dans un mauvais moment** : juste après une Session dont une valeur sort de la plage, ou dans un Aquarium en Cyclage.
- **Dire « merci pour votre note »** : l'app ne sait pas si l'utilisateur a noté, ni si Google a affiché la fenêtre.
- **Joindre des données personnelles** aux commentaires : valeurs d'eau, notes, noms d'Aquarium, photos, adresse e-mail, identifiants analytiques.
- **Ouvrir une porte au spam** en permettant l'envoi sans compte.

## 3. Contraintes

- Modules : `:shared`, `:ui` (Compose Multiplatform jvm + android), `:androidApp`, backend Supabase (migrations, pgTAP, fonctions Deno, tests d'intégration).
- **Langues** : FR, EN, DE, IT, ES ; chaque nouvelle clé dans les 6 catalogues ; glossaire `docs/localization-glossary.md` ; le français vouvoie.
- **Accessibilité** : cibles ≥ 48 dp, rien de coupé en DE sombre à 150 %.
- **Hors ligne d'abord** : sans réseau, l'app reste utilisable ; un commentaire non envoyé n'est pas perdu sans le dire.
- **Confidentialité** : `docs/privacy.md` et la politique publiée décrivent les nouvelles données (type, espèce, version, langue, identifiant d'installation). La suppression de compte efface aussi les commentaires liés au compte.
- Le harnais desktop compile et tourne : l'avis Play n'y fait rien.

## 4. Décisions

### 4.1 Avis Google Play (#41)

**Base existante** (trouvée à la relecture) : l'app demande déjà un avis, une seule fois dans sa vie, après 3 Sessions réussies et 7 jours depuis la première :
- `ReviewEligibilityPolicy`, `ReviewCoordinator`, `ReviewHistoryStore`, `InAppReviewGateway` dans `shared/.../PrivacyServices.kt` ;
- `AndroidReviewHistoryStore` et `GooglePlayInAppReviewGateway` dans `androidApp/.../PrivacyInfrastructure.kt` (dépendance `play-review` déjà là) ;
- l'appel `reviewCoordinator?.onSuccessfulSession()` dans `HomeViewModel` après l'enregistrement d'une nouvelle Session ;
- un événement LogSnag « review requested » envoyé par le gateway, décrit dans `docs/privacy.md`.

**On remplace ces règles, on ne construit rien en parallèle** (choix de Claude) : les types existants sont réécrits avec les règles ci-dessous ; leurs tests aussi ; `docs/privacy.md` et `docs/privacy-observability.md` suivent. L'événement LogSnag existant est **gardé** tel quel (ce n'est pas un nouvel événement) et part à chaque demande Google réelle.

**Moment** (Maxime) : la 5ᵉ Session enregistrée.
- Après l'enregistrement d'une Session, l'avis passe « en attente » si :
  - le nombre total de Sessions enregistrées (tous Aquariums) atteint au moins 5 ;
  - aucune valeur de cette Session ne sort de sa plage indicative ;
  - l'Aquarium n'est pas en Cyclage.
- Si la 5ᵉ Session ne remplit pas ces conditions, la suivante qui les remplit pose l'attente (choix de Claude).
- **Délai** (Maxime) : au moins **90 jours** entre deux demandes.
- La règle est une **fonction pure testée** (comme `shouldRequestInAppReview` dans Yams) : attente posée, nombre de Sessions, date de la dernière demande, maintenant.

**Déroulé** (sur le modèle de Yams, choix de Claude pour les détails) :
- Au retour sur l'accueil avec un avis en attente et éligible :
  - **la première fois** : un message « Votre avis compte » s'affiche sur l'accueil (Maxime : « je la veux »). Il dit qu'on peut noter maintenant ou plus tard depuis les réglages. Deux boutons : « Laisser un avis » ouvre la fenêtre Google ; « Plus tard » ferme. Dans les deux cas, le message ne reviendra plus, et la date de demande est enregistrée ;
  - **les fois suivantes** (après 90 jours) : l'app demande directement la fenêtre Google, sans message.
- La fenêtre Google passe par Play Core (`ReviewManager` : `requestReviewFlow` puis `launchReviewFlow`) ; les échecs sont ignorés ; sur desktop, rien.
- Google applique son propre quota : la fenêtre peut ne pas s'afficher. L'app ne commente pas le résultat.

**Réglages** : une entrée « Noter Aquagenda » dans la section Aide, à côté d'« Envoyer des commentaires ». Elle ouvre la fiche Play Store de l'app (`market://details?id=…`, repli sur l'URL https), et non la fenêtre Google (qui est soumise au quota). Sur desktop, l'entrée est masquée.

**Stockage** : l'attente, la date de dernière demande et « message déjà vu » sont des préférences locales de l'appareil, non synchronisées, dans le fichier de préférences existant (`aqualog-review-eligibility`).

**Comptage et cycle de vie** (choix de Claude) :
- Le compteur reste le compteur local existant : il augmente à chaque **nouvelle** Session enregistrée sur cet appareil. Les modifications, imports et Sessions reçues par synchronisation ne comptent pas. Un utilisateur déjà installé garde son compteur : s'il a déjà 5 Sessions, la prochaine Session réussie pose l'attente.
- L'attente reste posée jusqu'à la demande (app tuée ou pas de retour à l'accueil : elle attend le prochain passage).
- Le délai de 90 jours part de la dernière **demande** : message affiché (quel que soit le bouton choisi) ou fenêtre Google demandée.
- **Utilisateurs déjà sollicités** par l'ancienne règle (`requested = true`, sans date) : la date de leur demande est fixée au jour de la mise à jour ; ils seront éligibles 90 jours plus tard, et verront alors le message « Votre avis compte » une fois, comme tout le monde.
- Android sans Play Store : la fenêtre Google échoue sans bruit ; « Noter Aquagenda » ouvre l'URL https de la fiche Play.

### 4.2 Commentaires au développeur (#42)

**Base existante** : « Envoyer des commentaires » (réglages, section Aide) envoie un texte de 1 à 2 000 caractères à la fonction `submit-feedback`, qui appelle la RPC `submit_product_feedback` et écrit dans `public.product_feedback`. Elle exige aujourd'hui un compte connecté et limite à 3 messages par heure et par compte. Maxime garde ce canal comme **seul** chemin.

**Ce qui change :**
- **Sans compte** (Maxime : « oui ») : l'envoi marche aussi sans compte connecté.
  - La table enregistre si le message vient d'un compte ou non (Maxime) : `account_id` devient facultatif ; un message sans compte a `account_id` vide.
  - **Anti-spam** (choix de Claude) :
    - chaque installation porte un identifiant aléatoire local (UUID), créé au premier envoi sans compte, sans lien avec la télémétrie ; il n'est envoyé qu'avec les messages sans compte, et il est effacé par « supprimer mes données » ;
    - la limite reste 3 messages par heure par compte ; sans compte, 3 par heure par identifiant d'installation, **plus** une limite globale de 100 messages sans compte par heure ;
    - l'identifiant se contourne facilement : la vraie protection est la limite globale. Si elle est atteinte, un utilisateur sans compte voit « Trop de messages en ce moment, réessayez plus tard » (accepté : la table ne peut pas être inondée).
  - **Authentification** (choix de Claude, sécurité) :
    - la table et la RPC restent **fermées au rôle `anon`**. Une nouvelle RPC pour les messages sans compte n'est exécutable que par le rôle de service ;
    - la fonction `submit-feedback` passe en `verify_jwt = false` et vérifie elle-même : un jeton utilisateur valide → chemin compte (comme aujourd'hui) ; pas de jeton utilisateur → chemin sans compte, qu'elle exécute avec la clé de service, après validation ;
    - côté app, l'envoi sans compte n'a besoin que de l'URL de la fonction et de la clé anon publique déjà configurées.
  - **Session expirée** (choix de Claude) : si l'app croit avoir un compte mais que le serveur refuse le jeton, elle rafraîchit la session une fois ; si cela échoue, le message part **sans compte** plutôt que d'être perdu.
  - Un message envoyé sans compte n'est jamais rattaché après coup à un compte créé plus tard.
- **Type** : chaque message porte un type parmi : bug, erreur de traduction, erreur de fiche, idée, autre. Le formulaire le fait choisir ; « autre » par défaut depuis les réglages.
- **Depuis une fiche espèce** : un lien « Signaler une erreur » en bas de chaque fiche ouvre le même formulaire, type « erreur de fiche » et identifiant de l'espèce déjà remplis, avec le nom de l'espèce affiché.
- **Contexte joint automatiquement** : version de l'app et langue de l'app (et l'identifiant d'espèce pour une fiche).
- **Validation côté serveur** de ces champs, puisque la fonction devient publique (choix de Claude) : type parmi les 5 valeurs (sinon refus) ; identifiant d'espèce facultatif, au format de l'app (kebab-case, 64 caractères au plus), sans vérifier qu'il existe ; version de l'app 32 caractères au plus ; langue au format code (2 à 8 caractères) ; une ligne a soit un compte, soit un identifiant d'installation. Le texte d'explication du formulaire, qui promet aujourd'hui que « seul le message que vous tapez est soumis », dit désormais exactement ce qui part avec le message.
- **Ce qui ne change pas** (Maxime) : 1 à 2 000 caractères ; jamais de valeurs d'eau, notes, noms d'Aquarium, photos, adresse e-mail ni identifiants analytiques ; validation côté serveur ; pas de réponse possible depuis l'app.
- **Hors ligne** : sans réseau, l'envoi échoue avec un message clair et le texte reste dans le formulaire (choix de Claude : pas de file d'attente).
- **Suppression de compte** : les messages liés au compte sont effacés (comportement actuel, `on delete cascade`). Les messages envoyés sans compte ne sont liés à personne et restent (à dire dans `docs/privacy.md`).

## 5. Ce qui est laissé au constructeur

- L'emplacement exact du compteur de Sessions (requête existante ou compteur en préférence) et des préférences.
- La forme du message « Votre avis compte » (carte sur l'accueil, feuille ou dialogue), dans le style Carnet.
- Le schéma exact des nouvelles colonnes (type, espèce, version, langue, identifiant d'installation) et la migration ; les tests pgTAP et Deno qui vont avec.
- Le choix de la bibliothèque Play Core (`com.google.android.play:review-ktx`) et sa version.
- Les textes exacts dans les 5 langues, dans le ton des textes existants.
