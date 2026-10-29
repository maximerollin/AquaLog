# Icônes Aquagenda — exports PNG

Ces fichiers rasterisent fidèlement la scène B2 approuvée dans
[`scene-b2.svg`](../design/launcher-icon/scene-b2.svg), sans redessiner ni recadrer
la composition. Tous sont en sRGB, carrés, entièrement opaques, sans masque
d'arrondi ni ombre extérieure ajoutée.

| Fichier | Dimensions | Usage |
| --- | --- | --- |
| `aqualog-play-icon-512.png` | 512 × 512 | Icône de fiche Google Play, PNG RGBA 32 bits, moins de 1 Mo. |
| `aqualog-app-store-icon-1024.png` | 1024 × 1024 | Source d'icône App Store / catalogue Xcode, RGB sans canal alpha. N'intègre pas à elle seule une cible iOS. |
| `aqualog-revenuecat-icon-512.png` | 512 × 512 | Image personnalisée pour un paywall ou un formulaire RevenueCat acceptant un upload. Taille de commodité, pas une exigence universelle RevenueCat. |
| `aqualog-icon-256.png` | 256 × 256 | Petit format générique pour avatars et intégrations. |
| `aqualog-icon-master-2048.png` | 2048 × 2048 | Master haute définition pour de futurs exports. |

## Régénérer

Depuis la racine du dépôt, avec [uv](https://docs.astral.sh/uv/) :

```sh
uv run --script tools/design/generate_launcher_icon.py --export-png
```

Les dépendances de rendu sont épinglées dans le script. Chaque taille est rendue
directement depuis le SVG, sans agrandir un petit PNG. Le script contrôle
dimensions, mode couleur, opacité et poids maximal Google Play. Pour exporter
ailleurs, ajouter `--output-dir /chemin/vers/les/exports`.

## Périmètre et références

La même source SVG génère aussi l'icône installée dans l'application :

```sh
uv run --script tools/design/generate_launcher_icon.py androidApp/src/main/res build/icon
```

Cette commande régénère les PNG du tableau, les calques vectoriels adaptatifs
(eau/rayons, scène et silhouette monochrome), la silhouette de notification et
les mipmaps PNG Android 6–7, carrés arrondis et ronds, de mdpi à xxxhdpi.
La scène du launcher est recentrée à 72 % autour du centre de la grille 108 dp
pour garder les petits poissons et la plante rouge visibles avec un masque rond.
Les stores conservent la composition pleine image approuvée.

Vérification instrumentée des ressources installées, des masques rond, squircle
et carré arrondi, avec et sans silhouette monochrome :

```sh
./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.LauncherIconTest
```

Le test exporte `launcher-icon/masks.png` dans les fichiers de sortie AndroidX
Test Storage récupérés sous `androidApp/build/outputs/connected_android_test_additional_output/`.

- [Google Play : spécifications de l'icône](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
- [Apple : catalogue d'icônes et source 1024 px](https://developer.apple.com/documentation/xcode/configuring-your-app-icon).
- [RevenueCat : réponse du support sur les icônes du dashboard, 23 avril 2026](https://community.revenuecat.com/dashboard-tools-52/unable-to-find-app-icon-upload-option-in-revenuecat-dashboard-7617).
  Cette réponse indique qu'il n'y a pas d'upload manuel pour l'icône de projet/app
  du dashboard : iOS la récupère depuis l'App Store, Android n'est pas pris en
  charge. Ne pas confondre cette icône avec une image personnalisée de paywall.
