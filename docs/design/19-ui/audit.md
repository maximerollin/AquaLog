# Proposition visuelle #19 — 26 octobre 2026

Statut : **direction validée par le propriétaire le 26 octobre 2026** ; implémentation Android réalisée ; voir [le bilan de vérification](implementation.md).
Base inspectée : `d05caffed04f61956289d36c68181402d11c7573`.
Branche de proposition : `design/19-ui-proposal`.
Ouvrir `proposition.html` dans un navigateur : document autonome, sans dépendance, réseau ou stockage.

## État réel et sources

- Checkout principal propre sur `integration/aqualog-v1`, SHA distant vérifié identique. Un seul worktree de travail #19 créé ; aucune modification du code Android.
- AGENTS.md, RTK.md, tous les documents docs/agents lus. Aucun CONTEXT/CONTEXT-MAP ou ADR trouvé.
- Issues #1 et #19 lues via gh, commentaires compris. Bilan final : https://github.com/maximerollin/AquaLog/issues/19#issuecomment-6291233171 . #1 ne contient aucun commentaire au moment de la lecture.
- Documents lus intégralement : v1-closed-test-qualification, google-play-test-transaction, privacy-observability, accessibility-validation.
- RTK 0.44.2 signalé lors de la proposition initiale. Lors de la reprise dans Codex, exécutable introuvable dans le PATH et aux emplacements usuels ; commandes directes utilisées. `gh issue view --comments` échoue sur GraphQL projectCards déprécié ; lecture réussie avec `--json body,comments,labels`.
- Aucun émulateur actif selon processus et adb ; seul le daemon adb a été démarré par l'inventaire. Supabase #19 absent des conteneurs actifs, six conteneurs #10 actifs et intacts. Aucun service démarré, arrêté, configuré ou déployé.
- Skill Android/Material 3 utilisé. `implement-spec` trouvé dans le cache installé (skills/engineering/implement-spec/SKILL.md), non invoqué : cette livraison est une proposition visuelle, pas une implémentation de la spec. TDD et seams à confirmer lors de l'implémentation autorisée.

## Observations sur les captures AquaLog

Source : `/Users/maximerollin/.codex/validation-artifacts/aqualog-19-01a15859/captures/`.
Six captures effectivement regardées : `home-fr-light.png`, `rapid-session-fr-light.png`, `timeline-fr-light.png` et les trois variantes `fr-dark-large.png`.
Ce sont des états diagnostiques du 19 octobre, pas des références approuvées ni un inventaire exhaustif de l'application.

1. **Accueil** : grand espace entre le titre AquaLog et le contenu. Invitation au compte imposante avant l'Aquarium et les données récentes. Cartes presque uniformes, peu de contraste hiérarchique entre information secondaire et routine. L'action principale fixe est un acquis à conserver.
2. **Session rapide** : champs classiques espacés et répétition de la même aide. Les captures sont déjà défilées jusqu'au KH, clavier ouvert : elles ne prouvent pas un mauvais focus initial. Le titre générique AquaLog n'aide pas à identifier le parcours. Avec texte agrandi et clavier, la surface de travail devient très petite. Conserver le clavier système et Suivant ; réduire la décoration et la répétition autour des champs, pas les cibles.
3. **Historique** : quatre lignes de filtres prennent une grande part de la première vue. La Session est un résumé de comptages, sans valeurs immédiatement lisibles. Les puces débordent latéralement car les lignes défilent ; cela n'est pas assimilé à une perte de contenu.
4. **Finition commune** : navigation par lettres A/H/P ; manque d'identité aquatique. Les captures sombres montrent les icônes système très sombres et l'invitation au compte dont le libellé est coupé à grande taille : points à confirmer/corriger lors de la reprise Android, sans invalider les preuves fonctionnelles acquises. Le bandeau rose du clavier appartient au clavier système et n'est pas une surface AquaLog.

## Références réellement examinées

- [Yams Score](https://play.google.com/store/apps/details?id=io.github.maximerollin.yams&hl=fr) : trois premières captures en pleine résolution utile, accueil initial, tableau de bord et préparation de partie. Fond crème, accent doré, cartes arrondies, statistiques regroupées, avatars et bouton principal persistant. Retenir chaleur, cohérence et hiérarchie ; ne pas reprendre les assets ni le contraste parfois faible de certains textes clairs.
- [Mölkky Champion](https://play.google.com/store/apps/details?id=io.github.vinceglb.molkky&hl=fr) : trois premières captures promotionnelles, illustration de quilles, sélection des joueurs, saisie des scores. Palette verte tonale cohérente, sélection explicitée par coche, grands chiffres, action immédiate. Distinguer l'habillage promotionnel de l'UI montrée dans le téléphone.

Images téléchargées uniquement pour inspection sous `/tmp/aqualog-19-visual/`. Aucun asset de ces produits dans la proposition. Illustration aquatique et pictogrammes dessinés en SVG original dans le HTML.

## Direction proposée

- Ivoire `#F6F4EC`, surfaces `#FFFEF9`, texte `#1E3733`, secondaire `#546A64`, turquoise `#14675E`, sauge `#DEEBE3`.
- Sombre : fond `#122622`, surface `#1D342E`, texte `#EDF3E8`, secondaire `#B7C9C0`, accent `#9BD8C8`.
- Cartes de 18 à 22 dp d'arrondi, séparation légère, chiffres lisibles, icônes de navigation reconnaissables avec libellés Accueil / Historique / Réglages.
- Illustration limitée à l'identité de l'Aquarium. Aucun changement de teinte, poisson, plante ou état décoratif en fonction des Mesures. Aucun badge « sain », jauge de santé ou prescription.
- Palette de marque proposée, non encore approuvée. Ne pas activer silencieusement une palette dynamique qui modifierait cette direction lors de la réalisation.

### Accueil

Aquarium et volume en tête, état de synchronisation discret mais textuel, dernières Mesures datées en cartes, Tâches puis invitation au compte secondaire. Bouton Nouvelle Session fixe. L'invitation après la première sauvegarde reste un parcours à préserver ; ce dessin représente l'accueil récurrent.

### Session rapide

Trois Paramètres épinglés fictifs ; labels et dernières valeurs à gauche, nouvelle saisie vide à droite. Les unités restent dans le label, jamais seulement dans un placeholder. À grande taille les champs passent sous le label. Une vue défilante, sans récapitulatif obligatoire. Changement d'eau habituel sélectionnable en un toucher, quantité modifiable séparément, autres Actions et Observation accessibles dessous.

Premier champ préfocusé et Suivant à préserver en Compose. Le bouton fixe reste au-dessus de l'IME et devient actif pour une Mesure, Action ou Observation. La maquette simule ces états sans persister de donnée. La vraie transaction atomique, prévention des doublons et conservation du brouillon ne doivent pas être réimplémentées dans la couche visuelle.

### Historique / Chronologie

Deux onglets conservés. Période et Aquarium visibles, filtre Paramètre/Action dans un panneau secondaire avec résumé des filtres actifs à prévoir en production. Sessions groupées par date avec valeurs exactes et entretien. La Tâche reportée est explicitement distinguée d'un entretien effectué. Tendances et détail de Session restent accessibles, mais non simulés ici.

## Vérifications de la proposition

- Rendu T3 HTML inspecté à 728 px, puis à 390 px avec texte agrandi et clavier illustratif : pas de débordement horizontal de page détecté.
- Simulation exercée : trois champs initialement vides, Enregistrer désactivé ; saisie → activation ; Action → coche explicite ; confirmation marquée simulation ; réinitialisation → vide et désactivé.
- Console sans erreur aux rendus observés.
- Contrastes calculés par luminance relative sRGB : blanc/turquoise **6,71:1** ; secondaire/ivoire **5,26:1** ; secondaire/sauge **4,72:1** ; texte/surface clair **12,61:1** ; texte/surface sombre **11,74:1** ; secondaire/sauge sombre **5,65:1** ; texte du bouton/accent sombre **9,16:1** ; contour de saisie/surface **3,32:1**. Ce contrôle concerne ces paires, pas chaque pixel ni l'application Android.
- Le bouton « Texte agrandi » illustre une réorganisation ; ce n'est pas une reproduction exacte du fontScale Android. Le clavier est schématique, pas une nouvelle proposition de clavier propriétaire.

## Périmètre autorisé après validation

Implémentation ciblée #19 dans ce même worktree, avec tests de comportement sur le seam Compose → repository local réel déjà établi. Préserver navigation, ordre des Paramètres, dernières valeurs non préremplies, focus/IME, brouillon, transaction atomique et anti-doublons. Vérifier les parcours affectés, thèmes, texte agrandi et FR/EN/DE/IT/ES, états vides/erreurs/hors connexion, cibles et contrastes. Contrôler les barres système sombres et les textes coupés observés.

Utiliser exclusivement Pixel_9a API 36 après contrôle de disponibilité et des autres sessions ; pas de wipe. Aucun redémarrage des validations acquises sans changement concerné. TalkBack réel et routine humaine strictement <30 s restent des gates distinctes.

#19 reste ouverte ; qualification locale héritée préservée, qualification finale Google Play non acquise. Cette proposition ne lève aucune gate SaaS, achat réel, signature/publication ou test humain.
