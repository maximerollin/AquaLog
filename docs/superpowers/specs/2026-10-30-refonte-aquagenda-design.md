# Refonte d'Aquagenda : style Carnet, assistance, habitants, photos

Statut : brouillon de conception, issu d'une séance de brainstorming avec Maxime le 30/10/2026.
Maquettes de référence : [`docs/design/20-refonte-carnet/`](../../design/20-refonte-carnet/) (pages HTML à ouvrir dans un navigateur).
Issues liées : #24 à #29.

## 1. Intention

Aquagenda a des fondations solides : Session rapide, Chronologie, Tendances, Tâches, synchronisation, export, cinq langues. Mais, comparé à AquaticLog, Coddykit Aquarium Manager et AquaDiary, il a deux défauts :

- **Les écrans sont trop gros.** Chaque paramètre a sa carte, un même texte d'aide est répété partout, les boutons sont en pleine largeur, et la barre « Aquagenda » ne sert à rien. On voit 5 paramètres par écran de saisie, une Session par tiers d'écran dans l'historique, et une seule courbe dans Tendances.
- **Il manque des fonctionnalités** que les concurrents ont : l'app ne dit pas quoi faire, ne sait pas ce qui vit dans le bac, n'a pas de photo, et n'aide pas à garder un rythme.

La refonte **garde l'identité visuelle** (vert canard #14675E, sauge, ivoire, formes arrondies). Elle donne aux écrans un style « Carnet », dense et calme, et ajoute trois familles de fonctionnalités : l'assistance, les habitants, les photos.

Le geste central, dit par Maxime : **« Je veux voir directement mon bassin, voir son état, s'il y a des choses à faire. »**

## 2. Objectifs, non-objectifs, anti-objectifs

**Objectifs**
- À l'ouverture, on voit le bac, son état et ce qu'il y a à faire, sans défiler.
- Tous les paramètres et l'entretien d'une saisie tiennent sur un écran.
- L'app propose des pistes quand une valeur sort de sa plage, et dit quand tester.
- On peut dire ce qui vit dans le bac et vérifier qu'une nouvelle espèce y ira bien.
- Le bac devient vivant et personnel : scène animée avec ses habitants, photo.
- On donne envie de revenir : série, bilan, widget.

**Non-objectifs (pour cette refonte)**
- Social : partage, communauté, profils publics. Refusé par Maxime.
- Inventaire / stock de réactifs (#28) : « peut-être intéressant », plus tard.
- Journal photo (une galerie qui montre l'évolution du bac) : pas demandé, une seule photo par bac.
- Eau de mer : l'encyclopédie couvre l'eau douce.
- iOS : la cible reste Android et le harnais desktop.

**Anti-objectifs (ce qui serait un échec, même si ça marche)**
- **Un ton de diagnostic.** L'app donne des pistes, jamais une vérité : « rien ne vaut un professionnel ». Pas de « il faut », pas de marque de produit.
- **Un formulaire d'admin.** Densifier ne doit pas produire des boîtes dans des boîtes, des emojis et des textes tous de la même taille. C'est exactement ce que Maxime a rejeté dans la variante A de `02-variantes-style.html`.
- **Un accueil qui change de bac tout seul.** On risquerait d'enregistrer une Mesure dans le mauvais Aquarium.
- **Des fiches espèces copiées.** Pas de scraping d'AquaticLog ni d'autres bases (droit sui generis des bases de données en UE), même reformulé.
- **Moins accessible qu'aujourd'hui.** La suite `LocalizedAccessibilityFlowTest` doit rester verte.

## 3. Contraintes

- **Modules existants** : `:shared` (domaine, Room, sync), `:ui` (Compose Multiplatform, cibles jvm + android), `:androidApp`. Le harnais desktop (`:ui:run`) sert à itérer vite sur l'UI.
- **Accessibilité** (voir `docs/accessibility-validation.md`) :
  - cibles tactiles d'au moins 48 dp ;
  - contraste WCAG (4,5:1 pour le texte normal, 3:1 pour les grands éléments) ;
  - police à 150 % en mode sombre ;
  - cinq langues ;
  - alternative textuelle des graphiques.

  La densité vient du retrait de texte et de marges, **jamais** de cibles plus petites.
- **Langues** : FR, EN, DE, IT, ES. Respecter `docs/localization-glossary.md` (Session, Mesure, Action d'entretien, Événement, Tâche, Chronologie, Cyclage, Plage indicative).
- **Mode sombre** : la palette sombre existe (`AquaLogTheme.kt`). Le style Carnet doit avoir son équivalent sombre.
- **Hors ligne d'abord** : tout fonctionne sans réseau. La synchronisation et l'export existants doivent couvrir les nouvelles données (habitants, rythme, photo).
- **Confidentialité** : la suppression de compte efface aussi les nouvelles données distantes, y compris les photos dans le stockage. La télémétrie reste limitée à la liste blanche existante.
- **Fichiers et photos** : bibliothèque **FileKit** (choix de Maxime).
- **Mouvement réduit** : les animations s'arrêtent quand le système demande de réduire les animations.

## 4. Décisions

### 4.1 Style « Carnet » et densité
Maquettes : `01-style-carnet.html` (validé) et `06-densite-avant-apres.html` (les problèmes actuels).

- **Typographie**
  - **Fraunces**, serif douce, pour les titres d'écran, les intitulés de section et les dates de la Chronologie.
  - **DM Sans** pour tout le reste.
  - Les deux polices sont sous licence OFL et embarquées dans les ressources de l'app.
- **Fond « papier »** : un peu plus chaud qu'aujourd'hui (#F3EFE3 dans les maquettes, contre #F6F4EC). Le reste de la palette ne change pas.
- **Chiffres** : les valeurs sont posées en grand sur le papier, avec un libellé en petit dessous, sans tuile ni étiquette autour. Seule une valeur hors plage prend une couleur : orange pour « à surveiller », rouge pour « hors plage nette ».
- **Saisie** : une ligne par paramètre, avec le nom, l'unité en petit, la plage cible et la valeur. Sous la valeur, un trait au lieu d'une boîte.
- **Aide** : en italique, et seulement quand il y a quelque chose à dire. Fini le « Pas encore mesuré · laisser vide pour sauter » répété sur chaque ligne.
- **Composants** :
  - les pastilles sont réservées à ce qu'on peut toucher (actions d'entretien, filtres) ;
  - les icônes sont au trait, aux couleurs de la palette ;
  - **aucun emoji dans l'UI**.
- **Barre du haut** : la barre « Aquagenda » disparaît. Chaque écran a un titre en Fraunces et une ligne de contexte (« Bac du salon · 30 derniers jours »).
- **Navigation basse** : 4 entrées, Bac / Historique / Tendances / Plus. Aujourd'hui il y en a 3, Accueil / Historique / Paramètres, et les Tendances sont un onglet de l'Historique. **Plus** regroupe les Paramètres, les Tâches, le compte et l'offre Pro.
- **Écrans à densifier** :
  - **Saisie de Session** : tous les paramètres et l'entretien en pastilles sur un écran.
  - **Historique** : une Session fait 3 ou 4 lignes, avec un fil chronologique à gauche. Les Tâches résolues, les Événements et les ajouts d'habitants tiennent sur une ligne chacun.
  - **Tendances** : une mini-courbe par paramètre, sur la bande de la plage cible, avec les changements d'eau marqués. Un tap ouvre le graphique complet.
  - **Tâches** : la liste d'abord, la création dans une feuille. Les demandes de permission tiennent dans une seule bande.
  - **Paramètres** : des lignes de liste, plus de gros boutons pleins.
  - **Onboarding et paywall** : même style.

### 4.2 Accueil
Maquette : `03-accueil-scene.html`, avec le style de `01-style-carnet.html`.

De haut en bas :
1. **Scène animée du bac**, environ 130 à 150 dp de haut :
   - un fond d'eau, du sable et des plantes qui ondulent ;
   - les **habitants réels** du bac qui nagent, chacun selon sa façon de bouger (§4.5) ;
   - le nom du bac en Fraunces, avec le volume, le type et l'âge ;
   - en haut à droite, une vignette avec la photo du bac (§4.6) qui l'ouvre en plein écran.
2. **Ligne d'état** : une pastille et une phrase, par exemple « 1 valeur à surveiller · mesuré il y a 2 j » ou « Tout est dans la plage · mesuré hier ».
3. **Dernières valeurs** : 6 valeurs en grand, avec une flèche de tendance, et un lien vers Tendances.
4. **Aujourd'hui** : les Tâches et les tests dus (§4.3.4), à cocher depuis l'accueil.
5. **Bouton « Mesurer »** flottant, toujours visible. Il ouvre la saisie pour le bac affiché.
6. **Carte « Bilan de la semaine »**, le dimanche (§4.3.5).

**Plusieurs bacs (Pro)**
- L'accueil s'ouvre sur **le dernier bac consulté**. Il ne change jamais de bac de lui-même.
- On change de bac en faisant glisser la scène à gauche ou à droite (des points ●○○ indiquent la position), ou en touchant le nom, qui ouvre la liste des bacs avec leurs photos.
- Quand un **autre** bac a quelque chose à faire, une bande apparaît sous la ligne d'état, par exemple « Nano crevettes · changement d'eau en retard › ». Un tap y mène.
- Avec un seul bac, il n'y a ni points ni bande.

### 4.3 Assistance
Maxime : « il nous le faut ». Les règles sont écrites par nous, **sans IA** : le comportement est prévisible, vérifiable et fonctionne hors ligne.

1. **Couleurs à la saisie** (#24). Chaque valeur prend sa couleur dès qu'on la tape, par rapport à la plage indicative du paramètre.
2. **Vérification à la saisie.** Une valeur impossible ou très éloignée de la précédente déclenche une question de confirmation, sans bloquer l'enregistrement. Exemples : « pH 72 ? Tu voulais dire 7,2 ? », ou « Nitrates à 80 alors que tu étais à 20 il y a 3 jours, c'est bien ça ? ».
3. **Pistes d'amélioration** (#29). Quand une valeur sort de sa plage, l'app propose 2 ou 3 pistes, de la plus simple à la plus engageante, puis une date pour refaire le test. Elles apparaissent après l'enregistrement, puis sont rappelées sur l'accueil.
   - Les pistes parlent de **types de produits** (« un conditionneur d'eau », « des bactéries de démarrage », « un tampon KH »), **jamais de marques**.
   - Le ton est celui de la suggestion (« tu peux essayer »). Chaque bloc de pistes se termine par une phrase qui renvoie vers un professionnel, par exemple « Ce sont des pistes, pas un diagnostic : en cas de doute, demande conseil en magasin spécialisé ».
   - Les pistes tiennent compte du contexte. Un changement d'eau déjà en retard passe en premier. En Cyclage, des nitrites élevés sont normaux, et le message le dit au lieu d'alarmer.
4. **Rythme d'analyse.** Chaque paramètre a une échéance, calculée à partir de sa dernière Mesure. Valeurs par défaut :

   | Situation | Paramètres | Fréquence |
   |---|---|---|
   | Cyclage | Ammoniac, nitrites, nitrates | tous les 2 jours |
   | Bac établi | Nitrates, pH | chaque semaine |
   | Bac établi | Nitrites | toutes les 2 semaines |
   | Bac établi | KH, GH | chaque mois |
   | Bac planté / crevettes | en plus : GH, TDS | chaque semaine |
   | Après un événement (ajout d'habitant, valeur hors plage, traitement) | Nitrites, ammoniac | le lendemain, puis à J+3 |

   - Ces valeurs sont **des points de départ, modifiables par bac**. Elles se présentent comme une suggestion, parce qu'il n'existe pas de norme officielle : les sources sont des guides de magasins et des forums.
   - Une seule saisie remet à zéro toutes les échéances qu'elle couvre.
   - En Cyclage, quand ammoniac et nitrites sont à 0, l'app propose : « Ton bac a l'air cyclé, passer au rythme d'un bac établi ? ». Accepter fait passer le profil de l'Aquarium de CYCLING à ESTABLISHED.
   - Les tests dus s'affichent dans « Aujourd'hui » et dans le widget, par exemple « Tester NO₃ et pH ».
5. **Série et bilan** (#27). Ils servent la gamification : « c'est toujours bien d'avoir un streak à garder ».
   - **Série en semaines**, pas en jours. Une semaine compte quand le rythme a été suivi (tests dus faits, Tâches cochées). Un jour de retard ne casse pas la série ; sauter la semaine la casse. Exemple d'affichage : « 8 semaines d'entretien régulier ».
   - **Bilan du dimanche soir**, en notification puis en carte sur l'accueil :
     - les tests faits et les Tâches cochées ;
     - les valeurs qui ont bougé (« NO₃ 20 → 35, en hausse ») ;
     - la série ;
     - ce qui attend la semaine suivante.

### 4.4 Widget Pro
Maquette : `05-widget.html`. Maxime : « J'adore les widgets ! », et le widget est dans Pro.

- **Petit (un bac)** : une mini-scène fixe avec les habitants dessinés, la pastille d'état et **la** prochaine chose à faire.
- **Moyen (un bac)** : l'état en une phrase, les Tâches du jour à cocher depuis le widget, et un bouton « Mesurer » qui ouvre la saisie de ce bac.
- **« Mes bacs »** : une ligne par bac, avec sa pastille d'état et sa prochaine chose à faire.
- Le widget n'est pas animé, et un tap ouvre l'app sur le bac concerné.
- Un utilisateur gratuit voit le widget dans la liste des widgets, mais celui-ci l'invite à passer à Pro.

### 4.5 Habitants et encyclopédie
Maquette : `04-ajout-habitant.html`.

**But** (Maxime) : « l'ajouter à mon bassin et être sûr que ça cohabite bien, avec le bon volume, le bon pH, etc. »

- **Encyclopédie** : environ **150 espèces courantes d'eau douce** (poissons, crevettes, escargots, et des plantes avec des critères réduits).
  - Les fiches sont **rédigées par nous**. Les faits sont recoupés avec des sources publiques. Les noms communs multilingues viennent de Wikidata (CC0).
  - FishBase est exclu (usage non commercial seulement), tout comme le scraping.
- **Contenu d'une fiche** :
  - nom commun (5 langues) et nom scientifique ;
  - famille de dessin et façon de bouger ;
  - plages de pH, température, GH et KH ;
  - volume minimum et taille adulte ;
  - taille de groupe minimale ;
  - tempérament et règles de cohabitation, par exemple « chasse les petits poissons » ou « territorial avec ses congénères » ;
  - régime et durée de vie ;
  - zone de nage (fond, milieu, surface).
- **Ajouter un habitant** :
  - La recherche classe les espèces **pour le bac courant**, en trois groupes : « Vont bien dans ce bac », « Avec précautions », « Déconseillés ». Chaque ligne affiche un badge qui donne la raison en deux mots.
  - La fiche commence par un verdict en une phrase. Elle compare ensuite chaque critère à la valeur réelle du bac : volume de l'Aquarium, dernières Mesures, habitants présents.
  - Un compteur démarre au minimum de groupe de l'espèce.
  - « Ajouter quand même » reste toujours possible : la fiche n'interdit rien.
- **Bac sans Mesure** : pas de verdict sur l'eau. La fiche affiche « pas encore mesuré » et un bouton « Faire une analyse » qui ouvre la saisie. Le volume, lui, est vérifié tout de suite.
- **Bac en Cyclage** : un avertissement rappelle d'attendre que les nitrites soient à 0 avant d'ajouter des poissons.
- **Chronologie** : l'ajout ou le retrait d'un habitant y apparaît comme un événement daté, et déclenche le test de suivi (§4.3.4).
- **Dessins**
  - Les **30 à 40 espèces les plus courantes** ont un dessin dédié : néon, cardinalis, guppy, platy, molly, betta, corydoras, ancistrus, scalaire, gourami, danio, rasbora, red cherry, amano, otocinclus…
  - Les autres utilisent la **silhouette de leur famille, recolorée**, et sont remplacées par de vrais dessins au fil des versions.
  - Les dessins sont vectoriels et pèsent quelques Ko chacun.
  - Le **mouvement est partagé par famille** : en banc, au fond, en glissant, en rampant, très lent.

### 4.6 Photos
- **Une photo par bac.** Elle sert à reconnaître le bac au premier coup d'œil et à se faire plaisir.
- **Où elle apparaît** : vignette de l'accueil (en plein écran au tap), liste des bacs, Paramètres, widget « Mes bacs ».
- **Ajout** : depuis la vignette. On prend une photo ou on choisit dans la galerie, via FileKit. Ensuite, recadrage carré, puis on peut la changer ou la retirer.
- **Taille** : la photo est réduite à environ 1 200 px et compressée, soit environ 200 Ko.
- **Sans photo** : la vignette montre une mini-scène dessinée avec les habitants du bac.
- **Elle suit le compte partout.** Elle est envoyée sur Supabase Storage, synchronisée sur les autres appareils, incluse dans l'export portable, et supprimée avec le compte.

### 4.7 Gratuit et Pro
- **Gratuit** :
  - tout le style ;
  - l'accueil avec la scène ;
  - les couleurs et la vérification ;
  - les pistes ;
  - le rythme d'analyse ;
  - la série ;
  - les habitants et l'encyclopédie ;
  - la photo d'un bac.
- **Pro** :
  - le widget ;
  - le bilan détaillé (les variations de valeurs, la semaine suivante) ;
  - le multi-bac ;
  - l'historique au-delà de 90 jours et les tendances avancées.

Les deux derniers points Pro (multi-bac, historique long et tendances avancées) existent déjà.

Je recommande de relever les prix quand l'assistance sortira : environ 24,99 à 29,99 € par an et 69,99 à 79,99 € à vie. **Maxime n'a pas encore tranché.**

## 5. Ordre de livraison

L'ordre a été approuvé par Maxime. Chaque lot est livrable seul et passe par son propre plan.

1. **Style Carnet + densité + nouvel accueil**, sans les habitants. La scène ne montre que le décor (eau, plantes, sable, bulles), sans invitation, jusqu'au lot 5.
2. **Assistance** : couleurs, vérification, pistes, rythme, série, bilan.
3. **Widget Pro**. Il dépend du rythme du lot 2.
4. **Photos**.
5. **Habitants + encyclopédie + dessins**. Le travail sur les données (rédaction des fiches, dessins) peut commencer en parallèle dès maintenant.
6. Plus tard : l'inventaire.

## 6. Laissé au constructeur

- L'architecture : comment l'état de l'accueil, le moteur de règles d'assistance et le calcul des échéances se découpent entre `:shared` et `:ui`.
- Le format de stockage de l'encyclopédie (fichier embarqué ou table), tant qu'elle fonctionne hors ligne et en 5 langues.
- Le format de la photo dans l'export portable (archive ou autre), tant qu'un export restauré rend la photo.
- La technique d'animation de la scène (Canvas Compose, etc.), avec une contrainte : pas de consommation de batterie notable quand l'accueil reste ouvert.
- La technique du widget (Jetpack Glance, etc.).
- La déclinaison sombre du style Carnet, dans le respect des contrastes.
- Les seuils exacts de la vérification à la saisie (« valeur impossible », « variation forte »).
- Le texte exact des pistes, valeur par valeur, à relire par Maxime.

## 7. Précisions issues de la relecture

Un relecteur a lu ce document comme s'il devait le construire, et a posé des questions.

**Décidé par Maxime :**
- **Tâches liées à une action d'entretien.**
  - Une Tâche peut être liée à un type d'action (changement d'eau, engrais…). C'est ce qui permet de dire « changement d'eau en retard », et aux pistes de le savoir.
  - Cocher une Tâche liée depuis l'accueil (ou depuis le widget) enregistre l'action avec sa quantité habituelle.
  - Enregistrer cette action dans une Session résout la Tâche due.
  - Les Tâches en texte libre continuent d'exister.
  - Ce changement touche le schéma des Tâches, Room, la synchronisation et l'export.
- **Habitants hors encyclopédie.**
  - On peut ajouter une espèce absente de l'encyclopédie en tapant son nom et en choisissant une silhouette de famille pour la scène.
  - Elle n'a pas de verdict de compatibilité, mais elle apparaît dans la scène et dans la Chronologie.
  - Le lien vers une fiche de l'encyclopédie est donc optionnel.

Les réponses ci-dessous sont des **choix de Claude**, à vérifier par Maxime.

- **Contenu de l'encyclopédie et dessins.**
  - Claude produit un premier jet des fiches et des dessins, comme Maxime l'a demandé pendant le brainstorming. C'est une piste de travail séparée du code, qui peut démarrer tout de suite.
  - Maxime relit et valide les faits avant publication.
  - Le lot 5 consiste à charger ce contenu et à calculer les verdicts.
  - Le décor de la scène du lot 1 (eau, sable, plantes) se dessine dans le code et ne dépend pas de ce contenu.
- **Dernières valeurs.**
  - L'accueil montre la **dernière Mesure de chaque paramètre**, même quand elles viennent de Sessions différentes. Avec le rythme par paramètre, c'est le cas normal.
  - Pour ne pas tromper, une valeur plus ancienne que la dernière Session affiche son âge en petit, par exemple « KH 5 · il y a 12 j ».
  - La ligne d'état « mesuré il y a 2 j » parle de la dernière Session.
  - Cela revient volontairement sur le choix de #19 (« pas un assemblage de dates différentes ») : afficher l'âge règle le problème que #19 voulait éviter.
- **Test dû.**
  - Il est **calculé à la volée** (dernière Mesure du paramètre + fréquence du rythme), sans être stocké.
  - « Cocher » un test dû depuis l'accueil ouvre la saisie avec ces paramètres en premier. Seule une Mesure remet l'échéance à zéro.
  - Un rappel est envoyé quand des tests deviennent dus, en réutilisant l'infrastructure des rappels de Tâches.
  - Le réglage du rythme par bac est, lui, stocké et synchronisé.
- **Orange et rouge.**
  - On garde une seule Plage indicative par paramètre, sans nouvelle bande à saisir.
  - Orange : la valeur sort de la plage. Rouge : elle s'en écarte nettement, selon un seuil défini par paramètre dans la table de règles de l'assistance (par exemple, nitrites ≥ 0,5 mg/L).
  - Les plages par défaut ne changent pas, et il n'y a pas de migration. Les valeurs des maquettes sont illustratives.
- **Valeurs courantes en un tap (#24)** : abandonnées pour cette refonte. Elles iraient contre l'objectif « tout sur un écran ».
- **Profils.**
  - `AquariumProfile` reste exclusif. Le rythme « planté / crevettes » s'applique au profil PLANTED_SHRIMP.
  - Passer de CYCLING à ESTABLISHED change le rythme, pas les paramètres actifs.
  - Les tests de suivi après un événement ne portent que sur les paramètres actifs. Par exemple, l'ammoniac est ignoré s'il est désactivé.
- **Traitement** : retiré des déclencheurs de tests de suivi. Rien dans l'app ne représente un traitement aujourd'hui. Les déclencheurs sont l'ajout d'un habitant et une valeur hors plage.
- **Série et bilan.**
  - **Une série pour tout le compte**, tous bacs confondus.
  - Une semaine compte si tout ce qui était dû a été fait dans la semaine. Une semaine où rien n'était dû ne casse pas la série.
  - La série est recalculée depuis l'historique, sans être stockée. Elle existe donc dès la mise à jour.
  - Le dimanche, une seule notification couvre tous les bacs, et la carte de l'accueil montre le bac affiché.
- **Synchronisation pour les gratuits** : le compte et la synchronisation sont ouverts aux comptes gratuits. La photo qui « suit le compte » fonctionne donc en gratuit.
