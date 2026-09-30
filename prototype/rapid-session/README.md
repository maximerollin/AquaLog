# Prototype jetable — session rapide Android

> Question : un utilisateur peut-il enregistrer une session courante — mesures, entretien et observation — en moins de 30 secondes, sans perdre les informations utiles ?

Trois variantes du parcours de session rapide sont disponibles sur la route unique `/`, via `?variant=A`, `?variant=B` et `?variant=C`.

Ce prototype est volontairement jetable. Il simule une interface Android/Material 3 dans le navigateur, conserve tout en mémoire et n'est pas une base pour l'application Jetpack Compose.

## Lancer

Depuis la racine du dépôt :

```bash
npm run prototype
```

Puis ouvrir <http://localhost:4173/?variant=A>.

## Variantes

- **A — Formulaire continu** : mesures, actions et observation sur un seul écran ; saisie au clavier de haut en bas.
- **B — Assistant guidé** : une étape par groupe d'information, puis récapitulatif avant validation.
- **C — Checklist compacte** : liste dense de la routine habituelle, avec édition dans une feuille basse.

La barre flottante ou les touches `←` et `→` changent de variante. Les flèches clavier restent disponibles dans les champs numériques pour leur comportement natif.

## Scénarios à tester

- **Routine** : température 24,5 °C, nitrates 12 mg/L, pH 7,2 et changement d'eau 25 %.
- **Session complète** : la même saisie, plus entretien du filtre et observation « Poissons actifs ».

Pour chaque essai, utiliser **Recommencer**, réaliser la mission sans chercher à optimiser le premier passage, puis enregistrer. Le panneau **État & mesure** expose les données en mémoire, le temps, les actions significatives et les changements d'écran. Une action significative correspond à la saisie d'une information, à l'activation d'un contrôle ou à une validation ; les frappes individuelles au clavier ne sont pas comptées.

## Hypothèses à arbitrer

1. Une vue continue est-elle assez lisible tout en restant sous 30 secondes ?
2. Le récapitulatif de l'assistant évite-t-il des erreurs qui justifient ses écrans supplémentaires ?
3. La checklist compacte rend-elle les omissions et les valeurs par défaut plus claires que le formulaire ?

Les valeurs de mesure précédentes sont montrées comme contexte, jamais préremplies. Seuls l'ordre des paramètres et les détails d'une action d'entretien habituelle sont repris.
