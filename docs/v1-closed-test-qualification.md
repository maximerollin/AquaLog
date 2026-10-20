# Qualification V1 pour le test fermé — issue #19

## Verdict

**Non qualifiée pour une distribution Google Play.** Ce dossier prépare et vérifie
la partie locale de #19 ; une suite verte avec des doubles ne vaut pas validation
des services externes. L’issue doit rester ouverte tant que les gates ci-dessous
ne sont pas acquises. Aucun service externe n’a été créé, déployé ou configuré.

Références : [ticket #19](https://github.com/maximerollin/AquaLog/issues/19),
[spec #1](https://github.com/maximerollin/AquaLog/issues/1).
Base de qualification : `f0c67bb` sur `integration/aqualog-v1`.
Implémentation locale initiale : `6b97b7b`, correctifs de qualification : `ba82003` ;
verdict de distribution toujours bloqué.

## Critères et limites des preuves

| Critère #19 | Vérification locale réalisable | Gate avant qualification finale |
|---|---|---|
| Environnements séparés | Packages, callbacks et paramètres de build propres à dev/bêta/production ; rejet des collisions détectables | Projets Supabase, produits/comptes Play, PostHog et Firebase réellement distincts et audit de leur configuration |
| Onboarding → Aquarium → Session → compte → synchronisation | Interactions Compose, Room réelle, doubles aux frontières ; adaptateur réseau contre Supabase local | Google OAuth/lien magique et synchronisation avec le backend bêta hébergé |
| Trois Mesures + changement d’eau <30 s | Parcours automatisé et protocole humain ci-dessous | Chronométrage par un humain sur téléphone Android courant ; aucune durée automatisée ne le remplace |
| Fermeture forcée, réseau, reprise, migration, restauration | Contrats Room et outbox, reprise réseau, instrumentation et phases processus distinctes | Achat/restauration Google Play réels ; exécuter aussi la reprise sur le candidat distribué |
| Export/import et RLS | Export/import entre installations isolées sur le même AVD ; pgTAP et clients HTTP authentifiés contre la pile dédiée | Rejouer sur l’installation du candidat distribué ; ne pas appeler deux bases locales « deux téléphones » |
| Cinq langues et accessibilité | Catalogues, 10 configurations clair/sombre/texte agrandi, sémantiques et captures diagnostiques | Relecture linguistique/visuelle humaine et TalkBack réel, sur les écrans critiques |
| Confidentialité observabilité | Contrats fermés des payloads et adaptateurs | Inspection des événements effectivement reçus dans PostHog et des erreurs/ANR Crashlytics sur projets bêta |
| Confidentialité, support, suppression | Résumé local, dialogue support explicite, suppression via doubles et contrats serveur locaux | Politique et support publics joignables, suppression effective d’un compte bêta hébergé et de ses données |
| Scénario commun réalisable avec paiements test | Protocole ci-dessous et tests déterministes | Play Console, piste fermée, produits, testeurs et confirmations natives avec moyens de paiement test |
| Aucun défaut bloquant / réduction du périmètre | Suites et deux relectures indépendantes ; registre des limites | Toutes les gates non négociables doivent passer avant un verdict de qualification |

## Environnements Android

| Usage | Build | Package | Callback |
|---|---|---|---|
| Développement | `debug` | `com.maximerollin.aqualog.dev` | `aqualog-dev://auth/callback` |
| Bêta | `beta` | `com.maximerollin.aqualog.beta` | `aqualog-beta://auth/callback` |
| Production | `release` | `com.maximerollin.aqualog` | `aqualog://auth/callback` |

Les valeurs sont préfixées `AQUALOG_DEV_`, `AQUALOG_BETA_`, `AQUALOG_PROD_`,
puis le nom du champ (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `POSTHOG_HOST`,
`POSTHOG_API_KEY`, `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY`,
`FIREBASE_PROJECT_ID`, `FEEDBACK_URL`, `PRIVACY_URL`, `SUPPORT_URL`).
Les anciennes variables globales `AQUALOG_SUPABASE_URL`, etc. sont rejetées :
les remplacer par les variables propres à chaque environnement.
Les groupes Supabase, PostHog et Firebase doivent être complets lorsqu’ils sont
renseignés. Les valeurs Supabase URL/clé, clé PostHog et identifiants Firebase
partagés entre environnements sont refusés.
Une configuration absente laisse les intégrations désactivées. Cela permet les
tests locaux mais ne satisfait pas les gates de distribution.

Les identifiants de projet et endpoints sont contrôlables localement ; cela ne
permet pas de déduire la propriété d’une clé opaque ou la séparation effective des
comptes SaaS. Vérifier ces points dans les projets réels lors de la gate externe.
Ne jamais placer de service-role Supabase ni de credential Google serveur dans l’APK.
La signature de publication, les produits Play associés au **package bêta** et les
paramètres serveur de vérification restent à préparer hors de cette session.
Le profil `beta` actuel est débogable et signé avec la clé de développement pour
les contrôles locaux et l’inspection de sandbox. Ce n’est pas un artefact signé
prêt à téléverser sur Google Play : la préparation de la signature de publication
et du candidat distribué reste une gate.
Les comptes et données bêta ne migrent pas vers la production.

## Rejouer les contrôles locaux

Prérequis : JDK 17, SDK de compilation 37, AVD **Pixel_9a API 36**. Vérifier les
chats actifs, `adb devices -l`, les processus émulateur et les verrous existants
avant de réserver l’AVD. Ne jamais utiliser `-wipe-data`, supprimer l’AVD ou vider
les données d’une autre installation. Arrêter uniquement l’émulateur démarré pour
cette qualification après les tests.

```sh
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest \
  :androidApp:assembleDebug :androidApp:lintDebug
./gradlew :androidApp:testBetaUnitTest :androidApp:testReleaseUnitTest \
  :androidApp:assembleBeta :androidApp:assembleRelease
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest
npx --yes deno test --allow-env --allow-net --allow-read \
  supabase/functions/migrate-initial-copy/service_test.ts \
  supabase/functions/verify-pro-purchase/service_test.ts \
  supabase/functions/delete-account/service_test.ts \
  supabase/functions/submit-feedback/service_test.ts
```

L’instrumentation Play est opt-in et exige un reçu authentique ; voir
[le protocole Play](google-play-test-transaction.md). Son absence est un skip
environnemental, jamais une restauration validée. Les scénarios ordinaires
utilisent des doubles de facturation explicitement déterministes.

### Protocole entre installations

```sh
python3 tools/qualification/installations.py --serial emulator-5554 \
  --adb "$ANDROID_HOME/platform-tools/adb"
```

Le script exige Pixel_9a API 36, installe les APK dev/bêta sans effacer leurs
bases, puis exécute quatre phases instrumentées dans des processus distincts :
seed dev → force-stop → vérification dev → transfert du JSON par `run-as` →
import bêta → force-stop → vérification bêta. Les assertions passent par les
repositories publics et l’UI. La base `aqualog-qualification-19.db` est dédiée au
protocole et ne remplace pas la base utilisateur `aqualog.db`. Le seed et l’import
refusent une base de qualification déjà remplie : ce protocole est volontairement
sans nettoyage destructif automatique. Il n’est pas une preuve de transfert
physique entre téléphones ni de restauration Google Play.

### Supabase local isolé

Ne pas exécuter `supabase start`, `db reset` ou `stop` depuis la configuration
historique du dépôt : elle nomme `aqualog-ticket-10`. La pile existante appartient
à un autre ticket. Créer un dossier de validation séparé, sans autre worktree,
avec une copie de `supabase/config.toml`, et les migrations/tests/functions du
commit à qualifier. Avant le démarrage :

1. Choisir un `project_id` inédit et vérifier que les ports sont libres.
2. Modifier **tous** les ports concernés, y compris DB shadow, inspector,
   analytics et pooler ; désactiver les services inutiles.
3. Adapter `site_url` et les callbacks Auth à l’environnement testé.
4. Vérifier les noms de conteneurs avant toute commande destructive.

Cette qualification emploie `build/qualification-19/backend`,
`project_id = "aqualog-ticket-19"`, API `57321`, DB `57322`, shadow `57320`,
studio `57323`, SMTP `57324`, inspector `57327`, analytics désactivée `57328`,
pooler désactivé `57329`. Ce dossier de build non suivi n’est pas un livrable source :
le recréer en suivant les règles ci-dessus pour une nouvelle exécution.

```sh
supabase start --workdir build/qualification-19/backend
supabase test db --workdir build/qualification-19/backend
# Récupérer uniquement les clés LOCALES depuis status, sans les publier.
# Fournir SUPABASE_URL / SUPABASE_ANON_KEY / SUPABASE_SERVICE_ROLE_KEY au test HTTP :
npx --yes deno test --allow-env --allow-net supabase/tests/integration/entity_sync_test.ts
# Pour Android : URL http://10.0.2.2:57321 et clé anonyme locale via AQUALOG_DEV_*.
# Arrêter seulement la pile dédiée après la validation :
supabase stop --workdir build/qualification-19/backend
```

## Scénario commun du test fermé

Préconditions externes : candidat signé et installé depuis la piste fermée du
package bêta, deux comptes AquaLog bêta, compte Play license tester, catalogue
annuel (14,99 €, essai 14 jours) et achat à vie (34,99 €) tant qu’il est conservé,
backend et observabilité bêta distincts, politique et support publiés. Ne jamais
remplacer ces préconditions par des reçus fabriqués ou des événements de démonstration.

1. Sans compte et hors connexion, terminer l’onboarding, créer un Aquarium,
   choisir les Paramètres, essayer la mini-Session et ignorer le paywall.
2. Depuis Accueil, enregistrer trois Mesures (exemple : 24,5 °C, pH 7,2,
   nitrites 0) et un changement d’eau 20 %. Vérifier les valeurs et la Session
   unique dans la Chronologie. Ne pas interpréter ces exemples en diagnostic.
3. Répéter la routine chronométrée : téléphone/modèle/Android, version de l’app,
   langue, familiarisation et éventuelle aide consignés. Départ au toucher
   Nouvelle Session, fin à la confirmation de sauvegarde. Noter chaque temps
   brut et toute erreur ; seuil **strictement inférieur à 30 secondes**. Ne pas
   compter le temps d’un test automatisé comme preuve humaine.
4. Passer en arrière-plan avec un brouillon, revenir, tourner l’écran ; vérifier
   le brouillon. Après une sauvegarde, forcer l’arrêt puis relancer ; vérifier
   Session/Mesures/Actions. Couper le réseau, modifier et sauvegarder, relancer
   l’app avec des opérations en attente, rétablir le réseau et vérifier la reprise
   sans doublon ni perte. Vérifier aussi une mise à niveau de schéma avec données.
5. Après sauvegarde locale, créer le compte avec Google puis tester séparément
   le lien magique. Vérifier que les UUID/données restent conservés, puis que la
   seconde installation retrouve le même contenu après synchronisation.
6. Exporter puis importer sur une autre installation avec confirmation explicite,
   comparer les données et répéter l’import pour vérifier la stratégie anti-doublon.
   Avec le second compte, tenter lecture et écriture des données du premier :
   elles doivent être refusées/masquées par RLS.
7. Confirmer l’essai annuel dans la feuille native Google Play, vérifier le Droit
   Pro serveur, réinstaller et restaurer après authentification. Tester aussi
   achat à vie, annulation/échec et retour Free sans perte ; ne pas migrer le
   credential de test vers un autre environnement.
8. Parcourir les écrans critiques en FR/EN/DE/IT/ES, clair/sombre et texte agrandi ;
   contrôler TalkBack, focus, libellés, cibles et équivalents textuels des graphiques.
9. Consulter confidentialité/support, envoyer un feedback volontaire sans contenu
   personnel et inspecter les outils : aucune valeur d’eau, note, nom d’Aquarium,
   email, photo ou autre contenu personnel dans les canaux analytics/crashs.
   Vérifier les données techniques autorisées et le comportement ANR réel.
10. Supprimer le compte depuis l’app et vérifier la suppression serveur, la reprise
    après échec et l’impossibilité d’accès avec les anciens credentials.

Consigner pour chaque étape : candidat/commit, appareil, compte de test pseudonymisé,
date, résultat, lien vers preuve expurgée et défaut éventuel. Ne jamais joindre un
token d’achat, JWT, email ou secret au rapport public.

## Réduction de périmètre

Ordre imposé par #1 : LogSnag → graphiques corrélés avancés → photos cloud → achat
à vie → langues autres que FR/EN. LogSnag et photos cloud sont déjà hors V1 ; les
gates externes ne justifient pas une suppression silencieuse des fonctions déjà
livrées. Aucune réduction additionnelle n’est appliquée ici. Copie locale,
Session rapide, synchronisation, export, récupération d’erreur et sécurité des
données restent non négociables. Ne pas déclarer « aucun blocage » lorsque leur
preuve de qualification manque.

## Corrections issues de la qualification locale

- Callback d’authentification limité au schéma/hôte/port/chemin du build.
- Identité des Réglages rafraîchie après activation du premier compte ; le
  parcours Compose complet détectait une identité locale obsolète.
- Services du runner de test reconnectés au repository injecté ; le test
  Supabase lisait auparavant une base déjà fermée.
- Tests hérités des Réglages et de plusieurs Aquariums : défilements et attentes
  explicites avant les actions, sans retirer les assertions ni ajouter de sommeil.
- Restauration : hauteur minimale de 48 dp permettant un libellé allemand complet
  en texte agrandi ; RED observé par le contrôle public du débordement de texte,
  puis GREEN et inspection de la capture corrigée.

## Registre d’exécution du 20 octobre 2026

La première suite complète a été interrompue le matin : elle n’est pas comptée
comme réussite. La reprise a révélé et corrigé le retour non-void d’un nouveau test
JUnit. Les journaux de reprise se trouvent sous `build/qualification-19/`.

Les commandes sont exécutées sur le worktree #19 ; les artefacts volumineux et
logs locaux ne sont pas des reçus de validation externe.

| Contrôle | Résultat observé |
|---|---|
| Base `f0c67bb`, JVM + unitaires Android + assembleDebug + lintDebug | PASS, 80 tâches ; état hérité avant modification |
| Edge services Deno | PASS, 25 tests ; `deno check` des quatre points d’entrée PASS |
| Supabase dédiée, migrations + pgTAP | PASS, 7 fichiers / 114 tests |
| HTTP Supabase authentifié, deux clients et second compte isolé | PASS, 1 test |
| Migration depuis schémas Room archivés 3–13 | PASS, 11 cas vers la version 14 puis réouverture |
| Callback réservé à l’installation | RED observé : callback production accepté en dev ; GREEN après correction |
| APK dev/bêta/release : package et callback dans le manifest | PASS, inspection des trois APK |
| Première suite complète de reprise | 30 tests : 24 réussites, 4 échecs, 2 skips ; échecs conservés puis corrigés |
| Régression ciblée après corrections | PASS, 5/5 : suppression partielle, multi-Aquarium, premier compte, adaptateur Supabase et libellé allemand |
| Suite complète après corrections | PASS : 31 tests Android, 29 réussites, 2 skips expliqués, 0 échec ; 131 captures diagnostiques |
| JVM / unités Android | PASS : 111 JVM ; 29 unités sur chacun des profils debug/bêta/release |
| Assemblage et lint | PASS sur debug, bêta et release |
| Transfert entre installations et arrêt de processus | PASS, 4 phases réelles dev/bêta, repositories et UI ; base dédiée |
| Gardes de configuration Gradle | PASS, 5 tests (dont sous-cas d’observabilité) |
| Arrêt des ressources locales | Pixel_9a arrêté ; pile #19 arrêtée ; six conteneurs #10 toujours actifs, intacts |
| Gates humaines et externes ci-dessus | NON EXÉCUTÉES ; #19 reste ouverte |

Preuves héritées, à ne pas confondre avec les réexécutions présentes : #18 annonce
27 réussites/2 skips et 130 captures ; #14/#15 restent ouvertes pour Play,
#17 pour réception SaaS réelle. Les migrations Room héritées couvrent des fixtures
v1 et v2 et une v13 reconstruite. `HistoricalMigrationQualificationTest` ajoute
11 copies créées depuis les schémas archivés 3 à 13 : Aquarium, Paramètre, Session,
Mesure, Action et Événement, puis compte (≥5), Tâche/Occurrences (≥6) et opération
en attente (≥7), avec une seconde ouverture après migration. Il ne s’agit pas de
sauvegardes collectées sur des appareils historiques ; les états de suppression
en cours et chaque combinaison de métadonnées serveur ne sont pas inventoriés
dans ces fixtures.

Les deux skips de la suite complète sont la transaction Play réelle (gate externe)
et le protocole d’installations opt-in (exécuté séparément, quatre phases PASS).
Le compteur de progression Gradle affiche 33 en fin de run ; le rapport JUnit
agrégé fait foi : 31 cas, dont 2 skips. Aucune validation externe n’est déduite
d’un skip.
