# Réalisation Android de la direction #19

Direction validée par le propriétaire le 26 octobre 2026. Branche `design/19-ui-proposal`, base `d05caff` ; proposition de référence : [proposition.html](proposition.html).

## Changements

- Palette ivoire/turquoise/sauge et sombre commune à toutes les versions Android ; pas de remplacement par les couleurs du fond d’écran. Cartes arrondies et surfaces tonales Material 3.
- Accueil : identité de l’Aquarium et volume, illustration Compose originale purement décorative, statut de synchronisation textuel, Mesures de la dernière Session avec sa date et ses unités, prochaines Tâches réelles de cet Aquarium, invitation au compte après les données. Les Mesures montrées sont celles de cette Session, pas un assemblage trompeur de dates différentes.
- Session rapide : labels et dernières valeurs séparés des champs vides ; disposition compacte ou verticale selon la largeur et la taille du texte, clavier système/focus/Suivant conservés. Actions sélectionnables avec coche explicite ; modification des quantités conservée. Le bouton Enregistrer peut grandir avec le texte.
- Historique : valeurs et Actions réelles dans les cartes de Session, regroupement par date locale ; filtres repliables et résumé des sélections. Leur état reste conservé lors du retour du détail d’une Session.
- Icônes de navigation et marque aquatique vectorielles, barres système lisibles en sombre ; consommation des marges système pour éviter leur double application entre les Scaffold.
- Les nouvelles ressources de texte existent dans les cinq langues. Les traductions et la qualité de restitution TalkBack restent soumises à la validation humaine habituelle.

Le repository, les transactions, les identifiants, le brouillon et les règles métier de sauvegarde restent ceux de la base qualifiée. Le seul ajout au ViewModel est l’état de présentation du panneau de filtres. Aucun service externe n’est modifié ; #19 reste ouverte.

## Vérification

Les tests de parcours existants utilisent le repository local réel. Le test Chronologie ouvre désormais le panneau de filtres et vérifie toujours la sélection après le retour du détail. Le test du rappel de compte défile jusqu’à son nouvel emplacement, en conservant ses assertions fonctionnelles. Le contrôle du titre d’accueil vérifie désormais le nom réel de l’Aquarium.

Première passe : 29 tests unitaires et lint réussis ; 11/13 tests instrumentés réussis. Les deux défauts observés (état d’ouverture du panneau et défilement du rappel dans le test) ont été corrigés avant la passe finale. L’inspection des captures a également conduit à supprimer les marges système appliquées en double.

Validation de la version finale :

- 29 tests unitaires Android debug : 0 échec, 0 skip.
- Assemblage APK debug et APK de tests réussi. Lint debug : 0 erreur, 54 avertissements (notamment dépendances, pluriels, ressources inutilisées et recommandations KTX ; aucun échec bloquant).
- Passe des 13 cas instrumentés : 11 réussites, deux échecs de défilement dans les tests hérités après les changements de disposition. Les cas concernent saisie/compte, Chronologie, focus/IME, brouillon, édition/suppression, synchronisation et accessibilité/localisation.
- Relance ciblée des cinq cas de saisie/fiabilité : quatre réussites, dont le rappel du compte ; un échec de sélection Fertilisation. L’inspection de l’arbre et de la capture a montré un élément LazyColumn préchargé mais non placé, dont les coordonnées par défaut conduisaient le test à toucher un champ de Mesure. Le test utilise maintenant le défilement de la liste vers l’élément, vérifie son affichage, puis le touche ; aucune assertion de contenu n’est retirée.
- Dernière relance du scénario édition/correction après redémarrage/suppression : réussite. Les **13 cas distincts sont donc validés sur le même code de production**, par la passe des 13 et ces relances ciblées ; il ne s’agit pas d’une dernière passe unique de 13 réussites.
- 131 captures diagnostiques de la version finale produites. Accueil FR clair/sombre agrandi, Session rapide FR et Chronologie FR effectivement inspectés ; contraste des rôles Material et parcours cinq langues validés par la suite existante.
- `git diff --check` sans erreur.

Appareil exclusif : Pixel_9a API 36, sans wipe, arrêté après usage. Journaux et preuves locales sous `/Users/maximerollin/.codex/validation-artifacts/aqualog-ui-20261026`.

Ces contrôles ne valent pas qualification Play, preuve humaine de routine <30 s ou validation manuelle TalkBack. Les preuves backend héritées ne sont pas réexécutées pour cette modification de présentation.
