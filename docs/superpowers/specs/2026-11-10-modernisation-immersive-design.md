# Modernisation d'Aquagenda : style immersif

Statut : direction choisie par Maxime le 10/11/2026 (variante C « Immersif »). Questions tranchées le même jour (§8). Prête pour le plan du lot 1.
Maquette de référence : `docs/design/21-modernisation/prototype-modernisation.html` sur la branche `prototype/21-modernisation`, qui ne sera jamais fusionnée. Pour l'ouvrir : `git show prototype/21-modernisation:docs/design/21-modernisation/prototype-modernisation.html > /tmp/proto.html`, ou passer sur la branche et servir le dépôt avec `python3 -m http.server`, puis ouvrir `?variant=C`.
Spec précédente, dont on garde tout le fond : [`2026-10-30-refonte-aquagenda-design.md`](2026-10-30-refonte-aquagenda-design.md).

## 1. Intention

Maxime aime la DA, le thème et les fonctionnalités, mais trouve que l'appli « fait un peu vieille ». Le 10/11/2026, on a comparé les captures de 22 applis récentes des stores avec 15 captures d'Aquagenda prises sur l'émulateur Pixel 9a.
- Applis de référence : Structured (Apple Design Award 2026), Gentler Streak, Bevel, Finch, Planta, Greg, Tiimo, Flighty, How We Feel, Crouton.
- Concurrents aquarium : TankSnap, Aquary, Aquarium Tracker, Tanknote, FinLog, Aquarily, Aquarium Manager, Carnet d'Aquarium.

Le constat : **la DA tient, c'est la structure des écrans qui date.**
- Du texte posé à plat, séparé par des filets.
- Des chiffres à peine plus gros que leur libellé.
- Des champs encadrés avec un libellé flottant (Material de 2018).
- Un onboarding fait uniquement de texte.
- Une scène du bac réduite à un bandeau.
- Des listes de réglages nues.
- Des graphiques pauvres.
- Aucune profondeur.

Trois variantes ont été maquettées : A (carnet net), B (tuiles) et C (immersif). Maxime a retenu **C**. La scène du bac, qui fait déjà la singularité de l'appli, devient l'écran. Le contenu monte dans une feuille de papier arrondie posée sur l'eau.

## 2. Objectifs, non-objectifs, anti-objectifs

**Objectifs**
- Une appli qui paraît de 2026 sur toutes ses surfaces, sans changer la palette, les polices ni la scène.
- Une seule grammaire visuelle, appliquée partout : bandeau d'eau, feuille, barre flottante.
- Accueil : on voit le bac, son état et ce qu'il y a à faire dès l'ouverture.
- Plus aucun champ encadré dans l'appli.

**Non-objectifs**
- Nouvelles fonctionnalités : aucune. Ce chantier ne change ni la base, ni la synchro, ni l'export.
- Changement de palette, de polices ou de dessin des espèces.
- iOS : la cible reste Android et le harnais desktop.

**Anti-objectifs (repris de la refonte, toujours valables)**
- **Un ton de diagnostic.** On écrit un fait, « 6 sur 7 dans vos plages », jamais un jugement comme « Eau stable » (voir §8).
- **Moins accessible qu'aujourd'hui.** `LocalizedAccessibilityFlowTest` doit rester vert, cibles de 48 dp, texte blanc sur l'eau à 4,5:1 au moins.
- **Un accueil qui change de bac tout seul.**
- **Du décor qui gêne la saisie.** Pas de poissons qui nagent derrière un titre ou un champ pendant une Session.

## 3. Grammaire visuelle

| Élément | Règle |
|---|---|
| **Bandeau d'eau** | `TankScene` existante (ou la photo du bac quand il en a une), de bord à bord sous la barre d'état. Hauteur : environ la moitié de l'écran sur l'Accueil, l'Objectif, l'Aquarium et l'Offre ; environ 170 dp sur les autres écrans. Un dégradé sombre en haut garantit le contraste de la barre d'état et du titre. |
| **Titre** | Fraunces semibold blanc, 28 à 38 sp, avec une ligne de contexte en DM Sans blanc à 85 %. |
| **Boutons « verre »** | Cercles de 40 dp, blanc à 20 %, bordure blanche fine, icône blanche. Compose n'a pas de flou d'arrière-plan natif, donc **pas de flou** : la translucidité suffit. |
| **Feuille** | Papier (`background`), coins supérieurs de 30 dp, qui chevauche le bandeau d'environ 30 dp, ombre douce vers le haut. Tout le contenu y vit. |
| **Barre flottante** | Pilule de 64 dp en `surface`, détachée des bords, **icônes seules** (choix de Maxime) : Bac, Historique, Tendances, Plus. Elle se termine par un bouton rond « + » (Mesurer) qui remplace le FAB actuel. L'onglet actif est dans une pastille teintée. |
| **Tuiles de valeur** | Coins de 20 dp ; libellé en petit, valeur en grand (24 à 28 sp), état par fond ambré + couleur du texte (jamais la couleur seule : la pastille d'état ou le libellé « Au-dessus de la plage » l'accompagnent). |
| **Groupes** | Lignes regroupées dans une carte `surface` de 22 dp, icône dans un carré teinté de 34 dp, valeur en gris, chevron. Remplace les listes de réglages nues. |
| **Sélecteur de période** | Contrôle segmenté en pilule (7 j / 30 j / 90 j, Tout). |
| **Champs** | Remplis, sans contour : libellé en petit, valeur en grand (Fraunces pour un nom, DM Sans tabulaire pour un nombre), anneau `primary` au focus. Les réglages fins (min/max d'une plage) passent par une feuille modale avec des boutons − et +. |
| **Mouvement** | Poissons et plantes animés uniquement dans les grands bandeaux, figés quand le système réduit les animations. Aucun poisson derrière un titre de saisie. |

## 4. Écran par écran

### 4.1 Accueil
- Bandeau d'environ 430 dp : scène ou photo, nom du bac, contexte (volume, profil, nombre d'habitants), bouton photo. **Le swipe entre bacs reste sur le bandeau** (pager actuel).
- Carte « verre » d'état : un anneau `n/total` (valeurs dans leur plage sur valeurs mesurées), le titre « 6 sur 7 dans vos plages » et une ligne factuelle (« pH à surveiller · 7,8 »). Toucher la carte ouvre les Pistes.
- Feuille :
  1. Rangée des dernières valeurs, **qui défile horizontalement**, avec un chevron à gauche et à droite (choix de Maxime). Chaque chevron est un bouton (« Valeurs précédentes » / « suivantes ») qui avance d'une page ; il disparaît au bout de la rangée.
  2. « À faire » : les tâches du jour et les tests dus.
  3. Les blocs conditionnels existants, dans cet ordre : reprendre la Session, proposition de cyclage terminé, alertes des autres bacs, bilan de la semaine, échec de synchro, invitation au compte.
- Les habitants restent dessinés dans la scène. « Ajouter vos habitants » devient un bouton « verre » sur le bandeau.

### 4.2 Session (« Relevé du jour »)
- Bandeau de 170 dp sans poissons, titre, bac et heure, bouton fermer.
- Feuille :
  1. Grille de tuiles sur 2 colonnes, une par paramètre : nom, plage, valeur en grand, état en clair (« Dans la plage », « Au-dessus de la plage ») ou « Toucher pour saisir ».
  2. Puces d'entretien avec icône.
  3. « Ajouter une observation ».
- **On garde le clavier numérique du système et l'enchaînement « Suivant »** d'une tuile à l'autre. La tuile active a l'anneau `primary`.
- Le bouton du bas compte les mesures : « Enregistrer · 4 mesures ». Le réglage de l'heure (−15 / +15 min) passe dans une puce sous le titre.

### 4.3 Historique
- Bandeau de 170 dp, titre, filtre du bac en contexte, bouton filtre. Les filtres par bac, paramètre et action passent dans une feuille modale, ce qui libère les quatre lignes de filtres d'aujourd'hui.
- Feuille :
  1. Sélecteur de période.
  2. Frise verticale : un nœud par événement (Session, habitant, action), regroupés par jour.
  3. Chaque Session est une carte avec ses valeurs en pastilles (lisibles d'un coup d'œil, plus seulement des comptages) et ses actions en puces.

### 4.4 Tendances
- Bandeau de 170 dp, titre, bac, bouton de période personnalisée.
- Feuille :
  1. Sélecteur de période.
  2. Carte héros pour le paramètre à surveiller (ou le premier) : valeur en grand, variation sur la période, courbe avec la plage en fond, repères de changement d'eau, mention « Repère, pas un diagnostic ».
  3. Les autres paramètres en lignes (valeur + petite courbe) qui s'ouvrent sur leur courbe.
  4. L'analyse Pro avant/après reste en dessous.
- Les descriptions et le texte alternatif actuels des graphiques sont conservés.

### 4.5 Plus
- Bandeau de 190 dp au nom du bac courant, avec « Changer de bac », « + Ajouter » et un bouton modifier.
- Feuille, en groupes :
  1. Groupe du bac : habitants, rythme d'analyse, tâches et rappels.
  2. Bannière Pro sur fond de scène (seulement en Gratuit).
  3. « Données et compte ».
  4. « Aide ».
  5. Actions du compte, archivage.

### 4.6 Onboarding (6 écrans, progression sur les 5 étapes après la bienvenue)
1. **Bienvenue** : scène plein écran, titre sur l'eau, indicateur de pages, « Commencer » en bouton blanc, « Restaurer depuis l'exportation ».
2. **Objectif** : bandeau de 300 dp, titre en bas du bandeau, trois cartes à choisir avec icône.
3. **Aquarium** : le nom et le volume s'affichent en direct sur la scène pendant la saisie. Champs remplis, unité en segmenté L / gal US, mention « Reste sur votre téléphone ».
4. **Paramètres** : une seule carte de lignes (poignée de réordonnancement, nom, plage en puce, interrupteur). Toucher une plage ouvre une feuille modale (min/max avec − et +, unité, décimales). Elle remplace le formulaire actuel à 4 champs encadrés par paramètre.
5. **Essai** : même grammaire que la Session (§4.2), avec la mention « Entraînement uniquement ».
6. **Offre** : bandeau de 330 dp, avantages Pro cochés, deux cartes de formule (annuel, à vie) avec le prix localisé de Google Play, « Choisir Pro », « Continuer gratuitement ». Le paywall ouvert depuis Plus utilise le même écran.

### 4.7 Écrans secondaires
Tâches et rappels, Rythme d'analyse, Habitants, Encyclopédie, fiche espèce, ajout d'habitant, recadrage photo, compte, détails de Session : ils prennent la grammaire des écrans secondaires (bandeau de 170 dp, feuille, groupes, champs remplis). Chacun a sa maquette dans la rangée « Écrans secondaires » du prototype.

Les maquettes changent aussi un peu les interactions. Sur chaque point, le comportement est à confirmer au moment du lot 4 :
- **Tâches** : les tâches sont regroupées par jour en cartes, avec l'icône de l'action liée. Chaque ligne affiche la récurrence, ce que l'écran actuel ne fait pas. Dans la feuille « Nouvelle tâche », la date et l'heure deviennent deux tuiles qui ouvrent les sélecteurs.
- **Rythme d'analyse** : la ligne se déplie et montre ses choix en puces, à la place de la boîte de dialogue avec des boutons radio.
- **Habitants** : la feuille s'ouvre par-dessus la scène, où l'on voit les vrais dessins des habitants. Chaque ligne a sa vignette d'espèce, et le bouton « Retirer » ouvre toujours le compteur.
- **Ajout d'habitant** : un champ de recherche en pilule, des puces de type, les trois groupes en cartes avec vignettes, et des badges sous forme de pastilles colorées sans symbole ✓ ⚠ ✗.
- **Fiche espèce** : le dessin en grand sur l'eau, le verdict dans une carte colorée, trois tuiles de faits et les comparaisons en lignes. Le compteur et le bouton d'ajout restent collés en bas.
- **Recadrage photo** : plein écran sur fond d'eau sombre, avec la grille des tiers et des coins marqués.
- **Compte** : l'invitation et le choix de la méthode tiennent sur un seul écran plein cadre (Google, e-mail, « Pas maintenant »). Les étapes suivantes (lien magique, attente, erreur) gardent cette mise en page.
- **Détails de Session** : les valeurs en tuiles sur 3 colonnes, l'entretien et les notes en cartes, « Modifier » en bouton de bandeau et « Supprimer la Session » en lien rouge.

## 5. Accessibilité

- **Barre en icônes seules** : chaque icône a son `contentDescription` (Bac, Historique, Tendances, Plus, Mesurer) et l'état sélectionné est exposé (`Role.Tab`, `selected`). Cibles de 48 dp au moins.
- **Texte sur l'eau** : le dégradé du bandeau assure 4,5:1 au moins pour le texte blanc à toutes les hauteurs de titre. À vérifier par capture, en clair et en sombre.
- **Rangée de valeurs** : les chevrons sont des boutons étiquetés ; TalkBack parcourt toutes les tuiles même hors écran.
- **Allemand à 150 %** : les titres du bandeau passent à la ligne sans couper, la grille de la Session passe à 1 colonne si besoin, la barre flottante garde des cibles de 48 dp.
- **Animations réduites** : scène figée (règle déjà en place).

## 6. Technique

- **Bord à bord** : `MainActivity` gère déjà les insets via `WindowCompat`. Les bandeaux dessinent sous la barre d'état, avec des icônes de barre d'état claires sur l'eau. Les écrans sans bandeau (feuilles modales plein écran) repassent en icônes sombres.
- **Navigation** : le `Scaffold` + `NavigationBar` d'`AquariumShell` est remplacé par un `Box`, avec la barre flottante en surimpression et un padding bas équivalent pour les listes. Le FAB « Mesurer » de `TankHome` disparaît au profit du « + ».
- **Accueil** : la scène reste le premier item de la `LazyColumn` (le pager garde ses gestes). L'item suivant porte la feuille (coins arrondis, décalage de −30 dp). La rangée de valeurs ajoute un **deuxième élément défilant** : les tests qui cherchent `hasScrollAction()` sur l'accueil passent par le tag `home-list`.
- **Composants partagés** dans `ui/theme/CarnetComponents.kt` (ou un nouveau `ImmersiveComponents.kt`) : `WaterHeader`, `PaperSheet`, `GlassIconButton`, `FloatingNavBar`, `ValueTile`, `ScrollChevrons`, `PeriodSegmented`, `SettingsGroup`, `FilledField`, `RangeSheet`.
- **Aucun changement** de schéma Room, de charge utile de synchro, de format d'export ni de chaîne existante. Les nouvelles chaînes vont dans les 6 catalogues.
- Harnais desktop : vérification au lancement (pas de tests), comme d'habitude.

## 7. Découpage en lots

| Lot | Contenu | Pourquoi dans cet ordre |
|---|---|---|
| **1 · Fondations + Accueil** | Composants partagés, bord à bord, barre flottante + bouton Mesurer, Accueil complet | Pose la grammaire et la navigation dont tout le reste dépend ; l'Accueil est l'écran le plus vu |
| **2 · Session** | Relevé du jour en tuiles, chaîne de saisie, puces d'entretien, heure | Geste central ; dépend de `ValueTile` |
| **3 · Historique + Tendances** | Frise, filtres en feuille, carte héros, lignes dépliables | Deux écrans de lecture, mêmes composants (sélecteur de période) |
| **4 · Plus + écrans secondaires** | Groupes, bannière Pro, et §4.7 | Beaucoup de petits écrans, même grammaire |
| **5 · Onboarding + Offre** | Les 6 écrans de §4.6, feuille de réglage d'une plage | Touche le premier contact et le paywall ; à faire une fois la grammaire stabilisée |

Chaque lot est une PR, avec un plan dans `docs/superpowers/plans/` et des captures avant/après prises sur l'émulateur Pixel 9a, en clair et en sombre.

Suite naturelle, hors chantier : refaire les captures de la fiche Google Play une fois les lots 1, 2 et 5 livrés.

## 8. Décisions du 10/11/2026

1. **Formulation de l'état** : factuelle, « 6 sur 7 dans vos plages », jamais « Eau stable ».
2. **Photo du bac** : quand le bac en a une, elle remplace la scène sur l'Accueil et Plus, et seulement là.
3. **Écrans de §4.7** : ils ont chacun leur maquette, dans le même prototype.

## 9. Validation

- Suites existantes vertes : `:shared:jvmTest`, `:ui:jvmTest`, `connectedDebugAndroidTest` (dont `LocalizedAccessibilityFlowTest`, `RapidSessionFlowTest`, `TrendsFlowTest`).
- Captures sur l'émulateur pour chaque écran touché : FR clair, FR sombre, DE à 150 %.
- Passe TalkBack sur la barre flottante et la rangée de valeurs.
