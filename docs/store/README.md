# Aquagenda — icônes et captures de la fiche

Ces fichiers rasterisent fidèlement la scène B2 approuvée dans
[`scene-b2.svg`](../../tools/design/scene-b2.svg), sans redessiner ni recadrer
la composition. Tous sont en sRGB, carrés, entièrement opaques, sans masque
d'arrondi ni ombre extérieure ajoutée.

| Fichier | Dimensions | Usage |
| --- | --- | --- |
| `aqualog-play-icon-512.png` | 512 × 512 | Icône de fiche Google Play, PNG RGBA 32 bits, moins de 1 Mo. |
| `aqualog-app-store-icon-1024.png` | 1024 × 1024 | Source d'icône App Store / catalogue Xcode, RGB sans canal alpha. N'intègre pas à elle seule une cible iOS. |
| `aqualog-revenuecat-icon-512.png` | 512 × 512 | Image personnalisée pour un paywall ou un formulaire RevenueCat acceptant un upload. Taille de commodité, pas une exigence universelle RevenueCat. |
| `aqualog-icon-256.png` | 256 × 256 | Petit format générique pour avatars et intégrations. |
| `aqualog-icon-master-2048.png` | 2048 × 2048 | Master haute définition pour de futurs exports. |

## Captures de la fiche Google Play

`screenshots/<langue Play>/` contient six captures téléphone par fiche (`fr-FR`, `en-US`,
`de-DE`, `it-IT`, `es-ES`), en PNG 24 bits sans alpha, 1080 × 1920 (9:16, le format que
Google Play demande pour la mise en avant) :

| Fichier | Écran |
| --- | --- |
| `01-home.png` | Accueil, le bac et ses valeurs |
| `02-session.png` | Nouvelle Session en tuiles |
| `03-history.png` | Chronologie |
| `04-trends.png` | Tendances, nitrate à surveiller |
| `05-onboarding.png` | Premier écran de l'onboarding |
| `06-offer.png` | Offre Pro et ses deux formules |

Elles sortent du test instrumenté `PlayStoreScreenshotsTest`, qui remplit un bac planté de
120 L avec huit semaines de Sessions, des habitants et une tâche, dans chaque langue. Il est
ignoré dans la suite normale. Sur l'émulateur Pixel_9a, depuis la racine du dépôt :

```sh
adb shell wm size 1080x1920
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.maximerollin.aqualog.PlayStoreScreenshotsTest \
  -Pandroid.testInstrumentationRunnerArguments.playStoreScreenshots=true
```

`-Pandroid.testInstrumentationRunnerArguments.playStoreLanguages=fr,en` limite les langues.
Les PNG arrivent sous
`androidApp/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/*/play-store/<langue>/`.
Les copier dans `screenshots/` (`fr` → `fr-FR`, `en` → `en-US`, `de` → `de-DE`,
`it` → `it-IT`, `es` → `es-ES`), puis remettre l'émulateur en état :

```sh
adb shell am broadcast -a com.android.systemui.demo -e command exit
adb shell settings put global sysui_demo_allowed 0
adb shell wm size reset
adb shell settings put system font_scale 1.0
adb shell cmd uimode night no
```

Le test rétablit la langue par appli, le thème et l'échelle de police à la fin, et Gradle
désinstalle l'appli après le passage.

## Régénérer les icônes

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
