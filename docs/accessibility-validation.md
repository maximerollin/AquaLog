# Validation automatisée de l’accessibilité Android

La suite `LocalizedAccessibilityFlowTest` exerce les écrans critiques dans les cinq langues prises en charge, en mode clair à taille normale et en mode sombre avec une police à 150 %.

Elle vérifie automatiquement :

- les noms accessibles exposés par les sémantiques Compose pour les actions essentielles inventoriées ;
- des cibles tactiles d’au moins 48 dp, entièrement contenues dans la fenêtre et sans superposition entre actions essentielles visibles simultanément ;
- l’affichage et les bornes des textes critiques, ainsi que la présence de leur valeur textuelle complète dans l’arbre sémantique ;
- l’ordre de focus et les actions IME du parcours Session rapide ;
- l’alternative textuelle du graphique ;
- les rapports de contraste WCAG des paires de rôles Material réellement fournies au thème actif, avec la luminance relative sRGB (4,5:1 pour le texte normal, 3:1 pour les éléments grands ou non textuels inventoriés) ;
- la production de captures diagnostiques par le service de stockage de tests Android. Après la suite connectée, Gradle copie automatiquement ces sorties sous `androidApp/build/reports/localized-accessibility`.

La qualification #19 ajoute un contrôle du `TextLayoutResult` public pour le
libellé de restauration en allemand à 150 %. Il détecte un texte visuellement
coupé même lorsque sa valeur sémantique complète reste présente. Les boutons de
restauration peuvent grandir au-delà de 48 dp pour conserver les deux lignes.
Ce contrôle ciblé ne garantit pas l’absence de débordement de tous les textes.

Ces contrôles ne remplacent pas une revue humaine. Ils ne prouvent ni la qualité linguistique à eux seuls, ni la restitution vocale, l’ordre de lecture réel ou les gestes avec TalkBack, ni le contraste de chaque pixel d’une capture. Les captures sont des artefacts de diagnostic, pas des références visuelles (« goldens »). Une passe manuelle TalkBack et une inspection visuelle restent nécessaires avant une publication.
