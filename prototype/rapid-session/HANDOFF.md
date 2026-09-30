# Handoff — prototype de session rapide

**Branche :** `prototype/rapid-session`  
**Prototype :** `npm run prototype`, puis <http://localhost:4173/?variant=A>  
**Question :** un utilisateur peut-il enregistrer une session courante en moins de 30 secondes, sans perdre les informations utiles ?

## Ce qui a été prototypé

Trois parcours Android/Material 3 jetables, sans backend ni persistance :

- **A — Formulaire continu** : saisie verticale, clavier conservé entre les mesures, actions en un toucher et observation repliée.
- **B — Assistant guidé** : une mesure par écran, puis entretien, observation et récapitulatif.
- **C — Checklist compacte** : vue globale de la routine, chaque mesure étant saisie dans une feuille basse.

Deux missions sont intégrées : routine (3 mesures + changement d'eau) et session complète (mêmes données + entretien du filtre + observation). Le prototype mesure le temps, les actions significatives, les écrans et la complétude de la mission.

## Résultat structurel

| Variante | Routine | Session complète | Écrans, confirmation comprise |
| --- | ---: | ---: | ---: |
| A — Formulaire continu | 5 actions significatives | 8 actions significatives | 2 |
| B — Assistant guidé | 10 actions significatives | 12 actions significatives | 7 |
| C — Checklist compacte | 11 actions significatives | 14 actions significatives | 2 |

Les temps de l'automatisation (moins de 3 secondes) prouvent seulement que les parcours fonctionnent ; ils ne constituent pas une mesure d'utilisabilité. Le seuil humain de 30 secondes reste à vérifier avec le protocole intégré au prototype.

## Verdict provisoire

**Conserver la variante A comme base du futur parcours Compose.** Elle réalise la routine standard sans changement d'écran, rend les mesures omises évidentes par leur champ vide et garde les actions d'entretien dans le même contexte. L'assistant B ajoute un coût de navigation disproportionné. La checklist C donne une bonne vision de complétude, mais les ouvertures et validations répétées de feuilles basses ralentissent chaque mesure.

Éléments utiles à reprendre des autres variantes :

- de B : le récapitulatif lisible, mais uniquement après l'enregistrement dans la carte de chronologie, pas comme étape obligatoire ;
- de C : l'indication visuelle des éléments renseignés, à intégrer discrètement dans la vue continue sans feuille basse par mesure.

## Recommandations à réinjecter dans la spécification technique

1. Une session rapide tient sur une vue défilante unique ; aucune étape de vérification obligatoire ne précède l'enregistrement.
2. Le premier champ numérique reçoit le focus à l'ouverture et l'action clavier **Suivant** déplace le focus sans fermer le clavier.
3. La session précédente prépare l'ordre des paramètres et les détails des actions d'entretien, mais **ne préremplit jamais une nouvelle mesure**. La dernière valeur reste un contexte visuel discret.
4. Un champ de mesure vide signifie explicitement « mesure non réalisée » et n'empêche pas l'enregistrement.
5. Une action habituelle reprend sa dernière quantité en un toucher ; une action secondaire ouvre la modification détaillée.
6. Observation et incident restent repliés et facultatifs.
7. Une valeur inhabituelle est signalée immédiatement sans bloquer la sauvegarde.
8. Le bouton fixe **Enregistrer la session** est désactivé uniquement tant que la session est vide. Une seule activation produit une session locale atomique et affiche immédiatement sa carte dans la chronologie.
9. Le test d'acceptation doit mesurer séparément la routine (3 mesures + changement d'eau) et la session complète. Le critère des 30 secondes ne sera considéré validé qu'après des essais humains sur téléphone Android courant.

## Suite

Faire tester d'abord `?variant=A&scenario=routine`, puis `?variant=A&scenario=complete`, sans démonstration préalable. Noter le temps, les hésitations et les corrections. Utiliser B et C uniquement comme contrepoints si un testeur réclame davantage de guidage ou une meilleure perception de la complétude.

Quand un ticket d'implémentation existera, y ajouter un lien vers cette branche comme source primaire, puis réécrire le parcours retenu proprement en Jetpack Compose plutôt que de reprendre le code du prototype.
