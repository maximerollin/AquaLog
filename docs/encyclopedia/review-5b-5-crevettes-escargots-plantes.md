# Encyclopédie, lot 5b, lot 5 : crevettes, escargots et plantes, validées

Ces 37 fiches (4 crevettes, 7 escargots et 26 plantes d'aquarium) ont été **validées par Maxime le 2026-11-09**, valeurs « à vérifier en priorité » comprises. Les valeurs ont été écrites par nous, à partir des connaissances aquariophiles courantes, puis confrontées aux articles Wikipédia (fr, en, de, quand ils existent) liés dans chaque fiche. Aucune base de données ni fiche d'application concurrente n'a été consultée ou copiée.

**Ce qui est sourcé et ce qui ne l'est pas.** Les articles Wikipédia sur les plantes et les escargots ne donnent presque aucun chiffre d'élevage (pH, GH, KH, volume). Dans chaque tableau, la colonne « Note » rapporte ce que dit chaque article cité (« fr » = l'article français, etc.), puis, quand la valeur retenue n'est pas celle d'une source, le marque « choix de notre part ». Une valeur sans aucune source chiffrée porte « aucune source chiffrée » ; les plus fragiles sont marquées « à vérifier en priorité ». Les plages de pH et de température des plantes, les volumes des crevettes, les durées de vie et presque tous les GH et KH sont des choix de notre part. Les articles consultés sont exactement ceux liés dans la ligne « Sources » de chaque fiche (quand l'article d'une langue n'existe pas, il n'y a pas de lien).

**Plantes : champs techniques.** Une plante n'a ni volume, ni groupe, ni cohabitation : `minVolumeLiters` vide, `minGroup` 1, aucune étiquette, `motion` `SLOW`, `zone` `BOTTOM`. Le modèle exige pourtant d'autres champs (arbitrage du contrôleur) : `adultSizeCm` est la hauteur courante en aquarium, `diet` est `OMNIVORE` (jamais affiché), `lifespanYears` est 1 (la fiche masque la durée de vie des plantes). Les GH et KH des plantes sont laissés vides : les plantes d'aquarium sont tolérantes et un chiffre étroit donnerait de faux « déconseillé ». Les pH et températures sont larges, pour la même raison.

**Escargots et crevettes : volume.** Les escargots n'ont pas de volume minimum (`minVolumeLiters` vide, autorisé pour eux, aucune source n'en donne un). Les crevettes en ont un, choix de notre part (20 L pour les petites, 100 et 200 L pour le bambou et le vampire, qui sont grandes et filtrent l'eau).

**Comment les valeurs ont été choisies :** les plages (pH, température, GH, KH) sont celles qu'un bac d'amateur typique doit tenir, pas les extrêmes que l'espèce supporte dans la nature. Quand les sources divergent, on retient une plage prudente entre elles. Rappel des règles du verdict : une valeur hors plage d'un écart inférieur à 10 % de la largeur de la plage donne « avec précautions », au-delà « déconseillé ». Les plantes ne reçoivent ni verdict de volume ni verdict de groupe, ni avertissement de cyclage (réservé aux poissons).

**Étiquettes de comportement.** Seule la helena porte une étiquette de prédation : `EATS_SNAILS`, déjà utilisée par l'oscar, la loche-clown, la loche pakistanaise, la loche naine et le tétraodon nain (en base). Elle rend « déconseillé » tout escargot : les 7 escargots de ce lot (la helena comprise ; mais une helena à côté d'autres helenas va bien : la prédation ne s'applique pas à sa propre espèce) ; et, dans l'autre sens, la helena est « déconseillée » avec les cinq mangeurs d'escargots déjà en base. Les autres crevettes et escargots portent `PEACEFUL` (validé par Maxime (2026-11-09)) ou rien ; les plantes n'ont aucune étiquette (`EATS_PLANTS` ne vise que des poissons ou, éventuellement, des escargots : non retenu ici).

**Noms :** libellé Wikidata (fr/en/de/it/es) d'abord, sinon un alias Wikidata, sinon le nom courant en aquariophilie, marqué « nom ajouté par nous ». Quand le libellé Wikidata est le nom scientifique, il est considéré comme absent. Les libellés Wikidata en minuscule ont reçu une majuscule initiale. Quand l'article Wikipédia donne le nom courant, la ligne l'indique (« nom donné par l'article en »). Pour de nombreuses plantes, aucun nom courant n'a été trouvé : le nom du genre ou le nom scientifique est repris, tranché par Maxime (2026-11-09), choix actuel gardé. Les noms sont uniques dans chaque langue (le contrôle du validateur couvre aussi les 113 fiches déjà publiées). Le nom français de la fougère de Java (« Fougère de Java ») est un nom ajouté par nous, le libellé Wikidata fr étant le nom scientifique.

**Identifiants Wikidata :** pour chaque espèce, nous avons retenu l'élément dont la propriété « nom scientifique » (P225) est celle de la fiche (relu par une requête à l'API Wikidata). Les articles Wikipédia associés à l'élément ont été consultés pour les valeurs. Pour la fougère de Java, l'élément Wikidata n'a pas de lien Wikipédia ; l'article en est celui de « Leptochilus pteropus » (retrouvé via « Microsorum pteropus ») et l'article de est « Javafarn ». Les titres de certains articles en sont d'anciens noms scientifiques : « Vittina natalensis » pour la néritine, « Red-rimmed melania » pour le mélanoïde, « Hydrocharis laevigata » pour la grenouillette, « Elodea densa » pour l'égeria, « Palaemon paludosus » pour la crevette fantôme, « Marimo » pour l'algue. Aucun libellé n'a été repris d'une autre source que Wikidata ou les articles Wikipédia cités.

**Espèces écartées :** aucune. Les 37 espèces ont été comparées à la liste de l'Union des espèces exotiques envahissantes préoccupantes (règlement (UE) 1143/2014, liste de 114 espèces à jour de mars 2026 selon l'article Wikipédia en « List of invasive alien species of Union concern », code wiki relu pour ce lot, le 2026-11-09). Les plantes listées dans l'article sont *Alternanthera philoxeroides*, *Cabomba caroliniana*, *Crassula helmsii*, *Eichhornia crassipes*, *Elodea nuttallii*, *Gymnocoronis spilanthoides*, *Hydrocotyle ranunculoides*, *Lagarosiphon major*, *Ludwigia grandiflora*, *Ludwigia peploides*, *Myriophyllum aquaticum*, *Myriophyllum heterophyllum*, *Pistia stratiotes* et *Salvinia molesta* (plus des plantes de jardin sans rapport). Les mollusques d'eau douce listés sont *Cipangopaludina chinensis* et *Marisa cornuarietis* ; aucune crevette n'y figure (les crustacés listés sont des écrevisses et des crabes). **Aucune des 37 espèces n'y figure**, ni *Egeria densa*, ni *Limnobium laevigatum*, ni *Hydrocotyle tripartita*, ni *Ludwigia repens* : seules des espèces voisines sont listées (*Elodea nuttallii* pour l'égeria, *Hydrocotyle ranunculoides*, *Ludwigia grandiflora* et *Ludwigia peploides*). Aucun *Pomacea* n'est en base (importation interdite par la décision 2012/697/UE). Cette liste n'a pas été relue sur EUR-Lex. Plusieurs espèces de ce lot sont toutefois envahissantes hors de l'Union (voir les points tranché par Maxime (2026-11-09), choix actuel gardé) ; des listes nationales peuvent aussi les interdire.

Les dessins (`drawing`) sont ajoutés plus tard (tâche 7). La couleur sert à teinter la silhouette.

## Crevette crystal red (*Caridina cantonensis*)

- [x] validée par Maxime (2026-11-09)

Identifiant `crevette-crystal-red`, Wikidata [Q20686014](https://www.wikidata.org/wiki/Q20686014). Tempérament : Crevette d'eau très douce et acide, plus délicate que la red cherry ; le crystal red est une sélection de cette espèce (l'espèce sauvage s'appelle aussi crevette abeille). Elle vit en colonie.

| Langue | Nom | Origine |
|---|---|---|
| Français | Crevette crystal red | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Bee shrimp | Wikidata, alias |
| Allemand | Bienengarnele | « nom ajouté par nous » (le libellé de est le nom scientifique ; nom courant d'aquariophilie) |
| Italien | Gamberetto crystal red | « nom ajouté par nous » (le libellé it est le nom scientifique ; nom courant d'aquariophilie) |
| Espagnol | Gamba crystal red | « nom ajouté par nous » (le libellé es est le nom scientifique ; nom courant d'aquariophilie) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7 | [en](https://en.wikipedia.org/wiki/Caridina_cantonensis) | en : vit dans une eau douce dont le pH doit correspondre à celui de ses cours d'eau d'origine, sans chiffre. choix de notre part : 6–7, à vérifier en priorité |
| Température | 21 – 26 °C | [en](https://en.wikipedia.org/wiki/Caridina_cantonensis) | en : 21–26 °C ; couleurs les plus fortes à 24–25 °C ; une eau chaude de façon prolongée augmente la mortalité. 21–26 retenu |
| GH | 3 – 6 °d | _aucune_ | choix de notre part : 3–6 (eau très douce), à vérifier en priorité |
| KH | 0 – 2 °d | _aucune_ | choix de notre part : 0–2, à vérifier en priorité |
| Volume minimum | 20 L | _aucune_ | choix de notre part : 20 L, comme la red cherry |
| Taille adulte | 3 cm | _aucune_ | choix de notre part : 3 |
| Groupe minimum | 10 | _aucune_ | choix de notre part : 10, comme la red cherry |
| Durée de vie | 2 ans | _aucune_ | choix de notre part : 2, comme la red cherry |
| Comportement | _aucune_ | [en](https://en.wikipedia.org/wiki/Caridina_cantonensis) | en : charognarde, mange des morceaux de végétaux en décomposition et des algues. Aucune source sur le caractère : `PEACEFUL` retenu, validé par Maxime (2026-11-09). Aucune étiquette de prédation (`EATS_*`) : crevette de petite taille |
| Régime | `OMNIVORE` | [en](https://en.wikipedia.org/wiki/Caridina_cantonensis) | en : mange végétaux en décomposition et algues ; `OMNIVORE` : choix de notre part |
| Dessin, nage, zone | `SHRIMP`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#C62828` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Caridina_cantonensis)

## Crevette bambou (*Atyopsis moluccensis*)

- [x] validée par Maxime (2026-11-09)

Identifiant `crevette-bambou`, Wikidata [Q1815616](https://www.wikidata.org/wiki/Q1815616). Tempérament : Crevette qui filtre l'eau du courant avec ses pattes en éventail ; paisible, elle se tient en hauteur sur le décor et a besoin d'un bac mûr, avec un peu de courant.

| Langue | Nom | Origine |
|---|---|---|
| Français | Crevette bambou | Wikidata, libellé |
| Anglais | Bamboo shrimp | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom courant d'aquariophilie) |
| Allemand | Molukken-Fächergarnele | Wikidata, libellé |
| Italien | Atyopsis moluccensis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Atyopsis moluccensis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–7,5, à vérifier en priorité |
| Température | 22 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 22–28, à vérifier en priorité |
| GH | 4 – 15 °d | _aucune_ | choix de notre part : 4–15 |
| KH | 2 – 10 °d | _aucune_ | choix de notre part : 2–10 |
| Volume minimum | 100 L | _aucune_ | choix de notre part : 100 L (crevette de grande taille qui filtre l'eau), à vérifier en priorité |
| Taille adulte | 7 cm | [fr](https://fr.wikipedia.org/wiki/Crevette_bambou), [de](https://de.wikipedia.org/wiki/Molukken-F%C3%A4chergarnele) | fr : 5 à 7,5 cm ; de : jusqu'à 8,6 cm. choix de notre part : 7 |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1, elle peut vivre seule |
| Durée de vie | 2 ans | _aucune_ | choix de notre part : 2, à vérifier en priorité |
| Comportement | `PEACEFUL` | [fr](https://fr.wikipedia.org/wiki/Crevette_bambou) | fr : se nourrit en filtrant les particules du courant avec ses pattes antérieures, elle ne chasse pas. `PEACEFUL` : validé par Maxime (2026-11-09) (aucune source ne parle de son caractère) |
| Régime | `OMNIVORE` | [fr](https://fr.wikipedia.org/wiki/Crevette_bambou) | fr : microphage suspensivore (filtreur) ; `OMNIVORE` : choix de notre part |
| Dessin, nage, zone | `SHRIMP`, `CRAWL`, `MIDDLE` | _aucune_ | choix de notre part (affichage) ; se tient en hauteur sur le décor pour filtrer |
| Couleur | `#8D6E63` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Crevette_bambou) · [de](https://de.wikipedia.org/wiki/Molukken-F%C3%A4chergarnele)

## Crevette fantôme (*Palaemonetes paludosus*)

- [x] validée par Maxime (2026-11-09)

Identifiant `crevette-fantome`, Wikidata [Q3933834](https://www.wikidata.org/wiki/Q3933834). Tempérament : Crevette transparente de 3 cm environ, nocturne, qui nettoie le fond ; elle vit en groupe. Elle est vendue pour nourrir des poissons autant que pour décorer.

| Langue | Nom | Origine |
|---|---|---|
| Français | Crevette fantôme | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Ghost shrimp | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Palaemonetes paludosus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Palaemonetes paludosus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Palaemonetes paludosus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8, à vérifier en priorité |
| Température | 20 – 26 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–26, à vérifier en priorité |
| GH | 5 – 15 °d | _aucune_ | aucune source chiffrée. choix de notre part : 5–15 |
| KH | 3 – 10 °d | _aucune_ | aucune source chiffrée. choix de notre part : 3–10 |
| Volume minimum | 20 L | [en](https://en.wikipedia.org/wiki/Palaemon_paludosus) | en : 20 individus au plus dans un bac de 5 gallons (environ 19 L). choix de notre part : 20 L |
| Taille adulte | 3 cm | [en](https://en.wikipedia.org/wiki/Palaemon_paludosus) | en : jusqu'à 2,5 cm. choix de notre part : 3 (arrondi), à vérifier en priorité |
| Groupe minimum | 6 | [en](https://en.wikipedia.org/wiki/Palaemon_paludosus) | en : il est recommandé d'en garder un groupe. choix de notre part : 6 |
| Durée de vie | 1 an | _aucune_ | choix de notre part : 1, à vérifier en priorité |
| Comportement | `PEACEFUL` | [en](https://en.wikipedia.org/wiki/Palaemon_paludosus) | en : nocturne, se cache dans la végétation le jour ; mange des algues et ce que mangent les autres animaux du bac. `PEACEFUL` : validé par Maxime (2026-11-09) (aucune source ne parle de son caractère) |
| Régime | `OMNIVORE` | [en](https://en.wikipedia.org/wiki/Palaemon_paludosus) | en : plancton la nuit, algues et restes de nourriture ; `OMNIVORE` : choix de notre part |
| Dessin, nage, zone | `SHRIMP`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#B0BEC5` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Palaemon_paludosus)

## Crevette vampire (*Atya gabonensis*)

- [x] validée par Maxime (2026-11-09)

Identifiant `crevette-vampire`, Wikidata [Q882521](https://www.wikidata.org/wiki/Q882521). Tempérament : Grande crevette (jusqu'à 14 cm) sans pinces, qui filtre l'eau avec ses pattes en éventail ; paisible mais exigeante : il lui faut un grand bac mûr. Elle ne se reproduit pas en eau douce.

| Langue | Nom | Origine |
|---|---|---|
| Français | Crevette vampire | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Vampire shrimp | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom courant d'aquariophilie) |
| Allemand | Blaue Monsterfächergarnele | Wikidata, libellé |
| Italien | Atya gabonensis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Atya gabonensis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 7,8 | [en](https://en.wikipedia.org/wiki/Atya_gabonensis) | en : un pH de 6,5–7,8 convient |
| Température | 23 – 29 °C | [en](https://en.wikipedia.org/wiki/Atya_gabonensis) | en : 23–29 °C convient |
| GH | 6 – 15 °d | [en](https://en.wikipedia.org/wiki/Atya_gabonensis) | en : une eau plus dure donne la forme bleue, sans chiffre. choix de notre part : 6–15, à vérifier en priorité |
| KH | 3 – 10 °d | _aucune_ | aucune source chiffrée. choix de notre part : 3–10 |
| Volume minimum | 200 L | _aucune_ | aucune source chiffrée. choix de notre part : 200 L, crevette de 14 cm, à vérifier en priorité |
| Taille adulte | 14 cm | [de](https://de.wikipedia.org/wiki/Blaue_Monsterf%C3%A4chergarnele) | de : jusqu'à environ 14 cm |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1 |
| Durée de vie | 5 ans | _aucune_ | aucune source chiffrée. choix de notre part : 5, à vérifier en priorité |
| Comportement | `PEACEFUL` | [fr](https://fr.wikipedia.org/wiki/Atya_gabonensis) | fr : sans pinces, elle filtre l'eau pour se nourrir ; « très pacifique et très sociable ». `PEACEFUL` : validé par Maxime (2026-11-09) |
| Régime | `OMNIVORE` | [en](https://en.wikipedia.org/wiki/Atya_gabonensis) | en : filtreur omnivore ; `OMNIVORE` |
| Dessin, nage, zone | `SHRIMP`, `CRAWL`, `MIDDLE` | _aucune_ | choix de notre part (affichage) ; se tient en hauteur sur le décor pour filtrer |
| Couleur | `#5D7A8C` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Atya_gabonensis) · [en](https://en.wikipedia.org/wiki/Atya_gabonensis) · [de](https://de.wikipedia.org/wiki/Blaue_Monsterf%C3%A4chergarnele)

## Néritine zébrée (*Neritina natalensis*)

- [x] validée par Maxime (2026-11-09)

Identifiant `neritine`, Wikidata [Q139450](https://www.wikidata.org/wiki/Q139450). Tempérament : Petit escargot très efficace contre les algues. Il ne se reproduit pas en eau douce (larves marines) : sa population ne déborde donc pas. Il a besoin d'une eau plutôt dure pour sa coquille.

| Langue | Nom | Origine |
|---|---|---|
| Français | Néritine zébrée | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Zebra nerite | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Zebrarennschnecke | « nom ajouté par nous » (le libellé de est le nom scientifique ; nom donné par l'article de) |
| Italien | Neritina natalensis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Neritina natalensis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 7 – 8,5 | _aucune_ | aucune source chiffrée. choix de notre part : 7–8,5 (coquille calcaire), à vérifier en priorité |
| Température | 22 – 26 °C | [en](https://en.wikipedia.org/wiki/Vittina_natalensis) | en : préfère 22 à 26 °C |
| GH | 6 – 15 °d | _aucune_ | aucune source chiffrée. choix de notre part : 6–15, à vérifier en priorité |
| KH | 4 – 12 °d | _aucune_ | aucune source chiffrée. choix de notre part : 4–12 |
| Volume minimum | _aucun_ | _aucune_ | aucune source chiffrée ; champ laissé vide (permis pour un escargot) |
| Taille adulte | 2,5 cm | [en](https://en.wikipedia.org/wiki/Vittina_natalensis), [de](https://de.wikipedia.org/wiki/Neritina_natalensis) | en : coquille d'environ 2,5 cm de diamètre en aquarium ; de : 2,5 cm. choix de notre part : 2,5 |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1 |
| Durée de vie | 2 ans | _aucune_ | aucune source chiffrée. choix de notre part : 2, à vérifier en priorité |
| Comportement | `PEACEFUL` | [en](https://en.wikipedia.org/wiki/Vittina_natalensis), [de](https://de.wikipedia.org/wiki/Neritina_natalensis) | en et de : mange les algues ; ne se reproduit qu'en eau saumâtre ou salée. `PEACEFUL` : validé par Maxime (2026-11-09) (aucune source ne parle de son caractère) |
| Régime | `ALGAE` | [en](https://en.wikipedia.org/wiki/Vittina_natalensis), [de](https://de.wikipedia.org/wiki/Neritina_natalensis) | en et de : mange les algues ; `ALGAE` |
| Dessin, nage, zone | `SNAIL`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#8D6E3F` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Vittina_natalensis) · [de](https://de.wikipedia.org/wiki/Neritina_natalensis)

## Nérite couronnée (*Clithon corona*)

- [x] validée par Maxime (2026-11-09)

Identifiant `nerite-couronnee`, Wikidata [Q10959794](https://www.wikidata.org/wiki/Q10959794). Tempérament : Petit escargot mangeur d'algues, à coquille cornue. L'article en n'en dit presque rien et l'espèce vit aussi en eau saumâtre : à vérifier en priorité avant publication.

| Langue | Nom | Origine |
|---|---|---|
| Français | Nérite couronnée | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Horned nerite | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom courant d'aquariophilie) |
| Allemand | Clithon corona | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Clithon corona | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Clithon corona | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 7 – 8,5 | _aucune_ | aucune source chiffrée. choix de notre part : 7–8,5, à vérifier en priorité |
| Température | 22 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 22–28, à vérifier en priorité |
| GH | 6 – 15 °d | _aucune_ | aucune source chiffrée. choix de notre part : 6–15, à vérifier en priorité |
| KH | 4 – 12 °d | _aucune_ | aucune source chiffrée. choix de notre part : 4–12, à vérifier en priorité |
| Volume minimum | _aucun_ | _aucune_ | aucune source chiffrée ; champ laissé vide (permis pour un escargot) |
| Taille adulte | 2 cm | _aucune_ | aucune source chiffrée. choix de notre part : 2, à vérifier en priorité |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1 |
| Durée de vie | 2 ans | _aucune_ | aucune source chiffrée. choix de notre part : 2, à vérifier en priorité |
| Comportement | `PEACEFUL` | [en](https://en.wikipedia.org/wiki/Clithon_corona) | en : vit en eau saumâtre et en eau douce, sans autre précision. Aucune source sur le comportement : `PEACEFUL` retenu, validé par Maxime (2026-11-09) |
| Régime | `ALGAE` | _aucune_ | aucune source chiffrée. `ALGAE` : choix de notre part (comme les autres néritidés), à vérifier en priorité |
| Dessin, nage, zone | `SNAIL`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#6B5B45` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Clithon_corona)

## Planorbe rouge (*Planorbella duryi*)

- [x] validée par Maxime (2026-11-09)

Identifiant `planorbe-rouge`, Wikidata [Q3075371](https://www.wikidata.org/wiki/Q3075371). Tempérament : Escargot plat en spirale, très courant en aquarium (variante rouge sélectionnée) ; il mange les algues, les plantes mortes et les restes, et se reproduit facilement.

| Langue | Nom | Origine |
|---|---|---|
| Français | Planorbe rouge | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Seminole rams-horn | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Abgeplattete Posthornschnecke | Wikidata, libellé |
| Italien | Planorbella duryi | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Planorbella duryi | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 7 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 7–8, à vérifier en priorité |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28, à vérifier en priorité |
| GH | 6 – 20 °d | _aucune_ | aucune source chiffrée. choix de notre part : 6–20 |
| KH | 3 – 12 °d | _aucune_ | aucune source chiffrée. choix de notre part : 3–12 |
| Volume minimum | _aucun_ | _aucune_ | aucune source chiffrée ; champ laissé vide (permis pour un escargot) |
| Taille adulte | 2,5 cm | _aucune_ | aucune source chiffrée. choix de notre part : 2,5, à vérifier en priorité |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1 |
| Durée de vie | 1 an | _aucune_ | aucune source chiffrée. choix de notre part : 1, à vérifier en priorité |
| Comportement | `PEACEFUL` | [en](https://en.wikipedia.org/wiki/Planorbella_duryi), [fr](https://fr.wikipedia.org/wiki/Planorbella_duryi) | en et fr : mange plantes ou poissons morts ou en décomposition, et des algues ; `EATS_PLANTS` non retenu (plantes mortes seulement). `PEACEFUL` : validé par Maxime (2026-11-09). de : échappée d'aquariums, établie presque partout dans le monde. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | [en](https://en.wikipedia.org/wiki/Planorbella_duryi), [fr](https://fr.wikipedia.org/wiki/Planorbella_duryi) | en et fr : plantes et poissons morts, algues ; `OMNIVORE` |
| Dessin, nage, zone | `SNAIL`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#C0392B` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Planorbella_duryi) · [en](https://en.wikipedia.org/wiki/Planorbella_duryi) · [de](https://de.wikipedia.org/wiki/Abgeplattete_Posthornschnecke)

## Planorbe cornée (*Planorbarius corneus*)

- [x] validée par Maxime (2026-11-09)

Identifiant `planorbe-corne`, Wikidata [Q598612](https://www.wikidata.org/wiki/Q598612). Tempérament : Plus grand escargot de la famille des planorbes en Europe, rustique et capable de vivre en eau froide ; il nettoie le bac des restes et des algues.

| Langue | Nom | Origine |
|---|---|---|
| Français | Planorbe cornée | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Great ramshorn | Wikidata, alias |
| Allemand | Posthornschnecke | Wikidata, libellé |
| Italien | Planorbarius corneus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Caracol cuerno de carnero | Wikidata, alias |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8, à vérifier en priorité |
| Température | 15 – 26 °C | [de](https://de.wikipedia.org/wiki/Posthornschnecke) | de : la température de l'eau doit atteindre au moins 12 °C. choix de notre part : 15–26 (espèce européenne, tolère le frais) |
| GH | 5 – 20 °d | _aucune_ | aucune source chiffrée. choix de notre part : 5–20 |
| KH | 3 – 12 °d | _aucune_ | aucune source chiffrée. choix de notre part : 3–12 |
| Volume minimum | _aucun_ | _aucune_ | aucune source chiffrée ; champ laissé vide (permis pour un escargot) |
| Taille adulte | 3,5 cm | [en](https://en.wikipedia.org/wiki/Planorbarius_corneus) | en : coquille de 35 mm de diamètre en général, adulte |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1 (hermaphrodite, de) |
| Durée de vie | 3 ans | [en](https://en.wikipedia.org/wiki/Planorbarius_corneus) | en : jusqu'à 6 ans en captivité. choix de notre part : 3, à vérifier en priorité |
| Comportement | `PEACEFUL` | [en](https://en.wikipedia.org/wiki/Planorbarius_corneus), [fr](https://fr.wikipedia.org/wiki/Planorbarius_corneus) | en : généraliste, mange selon ce qui est disponible ; fr : végétaux morts, algues et débris. `PEACEFUL` : validé par Maxime (2026-11-09) |
| Régime | `OMNIVORE` | [en](https://en.wikipedia.org/wiki/Planorbarius_corneus), [fr](https://fr.wikipedia.org/wiki/Planorbarius_corneus) | en : généraliste ; fr : végétaux morts, algues, débris ; `OMNIVORE` |
| Dessin, nage, zone | `SNAIL`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#8B5A2B` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Planorbarius_corneus) · [en](https://en.wikipedia.org/wiki/Planorbarius_corneus) · [de](https://de.wikipedia.org/wiki/Posthornschnecke)

## Mélanoïde (*Melanoides tuberculata*)

- [x] validée par Maxime (2026-11-09)

Identifiant `melanoide`, Wikidata [Q1931948](https://www.wikidata.org/wiki/Q1931948). Tempérament : Escargot en tourelle qui s'enfouit dans le sable le jour et l'aère ; il se multiplie très vite (femelles qui portent des jeunes) et peut envahir un bac bien nourri.

| Langue | Nom | Origine |
|---|---|---|
| Français | Mélanoïde | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Malaysian Trumpet Snail | Wikidata, libellé |
| Allemand | Nadel-Kronenschnecke | Wikidata, libellé |
| Italien | Melanoides tuberculata | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Melanoides tuberculata | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8,5, à vérifier en priorité |
| Température | 20 – 28 °C | [en](https://en.wikipedia.org/wiki/Red-rimmed_melania) | en : semble préférer 18 à 25 °C, ou 18 à 32 °C selon les études. choix de notre part : 20–28 |
| GH | 5 – 20 °d | _aucune_ | aucune source chiffrée. choix de notre part : 5–20 |
| KH | 3 – 15 °d | _aucune_ | aucune source chiffrée. choix de notre part : 3–15 |
| Volume minimum | _aucun_ | _aucune_ | aucune source chiffrée ; champ laissé vide (permis pour un escargot) |
| Taille adulte | 3 cm | [de](https://de.wikipedia.org/wiki/Nadel-Kronenschnecke) | de : coquille jusqu'à environ 3 cm de haut |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1 |
| Durée de vie | 2 ans | _aucune_ | aucune source chiffrée. choix de notre part : 2, à vérifier en priorité |
| Comportement | `PEACEFUL` | [en](https://en.wikipedia.org/wiki/Red-rimmed_melania), [de](https://de.wikipedia.org/wiki/Nadel-Kronenschnecke) | en : mange micro-algues et détritus ; de : nocturne, mange restes de nourriture et de plantes, ameublit le sol. `PEACEFUL` : validé par Maxime (2026-11-09). en et de : établie hors de son aire par les aquariophiles, supplante des escargots locaux. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | [en](https://en.wikipedia.org/wiki/Red-rimmed_melania), [de](https://de.wikipedia.org/wiki/Nadel-Kronenschnecke) | en : algues et détritus ; de : restes de nourriture et de plantes ; `OMNIVORE` |
| Dessin, nage, zone | `SNAIL`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#7A5C3A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Melanoides_tuberculata) · [en](https://en.wikipedia.org/wiki/Red-rimmed_melania) · [de](https://de.wikipedia.org/wiki/Nadel-Kronenschnecke)

## Physe (*Physella acuta*)

- [x] validée par Maxime (2026-11-09)

Identifiant `physe`, Wikidata [Q1694040](https://www.wikidata.org/wiki/Q1694040). Tempérament : Petit escargot à coquille pointue, rustique et très prolifique ; il arrive souvent avec les plantes et peut devenir envahissant dans un bac trop nourri.

| Langue | Nom | Origine |
|---|---|---|
| Français | Physe | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Acute bladder snail | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Spitze Blasenschnecke | Wikidata, libellé |
| Italien | Physella acuta | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Physella acuta | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8,5, à vérifier en priorité |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28, à vérifier en priorité |
| GH | 5 – 20 °d | _aucune_ | aucune source chiffrée. choix de notre part : 5–20 |
| KH | 3 – 15 °d | _aucune_ | aucune source chiffrée. choix de notre part : 3–15 |
| Volume minimum | _aucun_ | _aucune_ | aucune source chiffrée ; champ laissé vide (permis pour un escargot) |
| Taille adulte | 1,6 cm | [en](https://en.wikipedia.org/wiki/Physella_acuta), [fr](https://fr.wikipedia.org/wiki/Physella_acuta) | en : coquille jusqu'à 16 mm ; fr : de 8 à 16 mm de haut. choix de notre part : 1,6 |
| Groupe minimum | 1 | _aucune_ | choix de notre part : 1 |
| Durée de vie | 1 an | _aucune_ | aucune source chiffrée. choix de notre part : 1, à vérifier en priorité |
| Comportement | `PEACEFUL` | [en](https://en.wikipedia.org/wiki/Physella_acuta), [fr](https://fr.wikipedia.org/wiki/Physella_acuta) | en : une population contrôlée aide à nettoyer les restes et à limiter les algues ; fr : tolère la pollution. `PEACEFUL` : validé par Maxime (2026-11-09). en : supplante les escargots locaux. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | [en](https://en.wikipedia.org/wiki/Physella_acuta) | en : restes organiques et algues ; `OMNIVORE` |
| Dessin, nage, zone | `SNAIL`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#A1887F` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Physella_acuta) · [en](https://en.wikipedia.org/wiki/Physella_acuta) · [de](https://de.wikipedia.org/wiki/Spitze_Blasenschnecke)

## Escargot assassin (*Anentome helena*)

- [x] validée par Maxime (2026-11-09)

Identifiant `helena`, Wikidata [Q106170753](https://www.wikidata.org/wiki/Q106170753). Tempérament : Escargot carnivore rayé de jaune et de brun, qui s'enfouit dans le sable et chasse les autres escargots (physes, planorbes, mélanoïdes) ; il se reproduit lentement.

| Langue | Nom | Origine |
|---|---|---|
| Français | Escargot assassin | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Assassin snail | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Raubturmdeckelschnecke | « nom ajouté par nous » (le libellé de est le nom scientifique ; nom donné par l'article de) |
| Italien | Anentome helena | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Anentome helena | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 7 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 7–8, à vérifier en priorité |
| Température | 23 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 23–28, à vérifier en priorité |
| GH | 6 – 15 °d | _aucune_ | aucune source chiffrée. choix de notre part : 6–15 |
| KH | 3 – 10 °d | _aucune_ | aucune source chiffrée. choix de notre part : 3–10 |
| Volume minimum | _aucun_ | _aucune_ | aucune source chiffrée ; champ laissé vide (permis pour un escargot) |
| Taille adulte | 2,5 cm | [en](https://en.wikipedia.org/wiki/Anentome_helena), [de](https://de.wikipedia.org/wiki/Raubturmdeckelschnecke) | en : 18–32 mm, souvent moins en aquarium ; de : 18 à 28 mm de haut. choix de notre part : 2,5 |
| Groupe minimum | 1 | [fr](https://fr.wikipedia.org/wiki/Anentome_helena) | fr : sexes séparés sans dimorphisme, il faut plusieurs individus pour espérer une ponte. choix de notre part : 1 |
| Durée de vie | 2 ans | _aucune_ | aucune source chiffrée. choix de notre part : 2, à vérifier en priorité |
| Comportement | `EATS_SNAILS` | [en](https://en.wikipedia.org/wiki/Anentome_helena), [fr](https://fr.wikipedia.org/wiki/Anentome_helena), [de](https://de.wikipedia.org/wiki/Raubturmdeckelschnecke) | en : carnivore, mange vers et escargots, utilisé pour limiter les petits escargots indésirables ; fr : mange physes, planorbes, mélanoïdes ; de : vit surtout de charogne, mais mange aussi d'autres escargots et leurs pontes. `EATS_SNAILS` retenu : tranché par Maxime (2026-11-09), choix actuel gardé (tout autre escargot « déconseillé » : les 6 autres escargots de ce lot, et la helena « déconseillée » avec l'oscar, la loche-clown, la loche pakistanaise, la loche naine et le tétraodon nain) |
| Régime | `CARNIVORE` | [en](https://en.wikipedia.org/wiki/Anentome_helena), [fr](https://fr.wikipedia.org/wiki/Anentome_helena), [de](https://de.wikipedia.org/wiki/Raubturmdeckelschnecke) | en, fr et de : carnivore ; `CARNIVORE` |
| Dessin, nage, zone | `SNAIL`, `CRAWL`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#C9A227` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Anentome_helena) · [en](https://en.wikipedia.org/wiki/Anentome_helena) · [de](https://de.wikipedia.org/wiki/Raubturmdeckelschnecke)

## Anubias (*Anubias barteri*)

- [x] validée par Maxime (2026-11-09)

Identifiant `anubias`, Wikidata [Q112520](https://www.wikidata.org/wiki/Q112520). Tempérament : Plante robuste à feuilles coriaces, à fixer sur une pierre ou une racine plutôt qu'à enterrer ; elle pousse lentement et supporte peu de lumière.

| Langue | Nom | Origine |
|---|---|---|
| Français | Anubias | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom du genre faute de nom courant sourcé) |
| Anglais | Anubias | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom du genre faute de nom courant sourcé) |
| Allemand | Anubias | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom du genre faute de nom courant sourcé) |
| Italien | Anubias | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom du genre faute de nom courant sourcé) |
| Espagnol | Anubias | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom du genre faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | [en](https://en.wikipedia.org/wiki/Anubias_barteri), [fr](https://fr.wikipedia.org/wiki/Anubias_barteri) | en : préfère 22–28 °C ; fr : 22 à 28 °C. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 20 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Anubias_barteri), [fr](https://fr.wikipedia.org/wiki/Anubias_barteri) | en : feuilles jusqu'à 300 mm ; fr : jusqu'à 30 cm. choix de notre part : 20 (hauteur courante en aquarium) |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#2E7D32` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Anubias_barteri) · [en](https://en.wikipedia.org/wiki/Anubias_barteri)

## Fougère de Java (*Leptochilus pteropus*)

- [x] validée par Maxime (2026-11-09)

Identifiant `fougere-de-java`, Wikidata [Q15314697](https://www.wikidata.org/wiki/Q15314697). Tempérament : Fougère rustique qu'on attache à une racine ou à une pierre sans l'enterrer ; très facile.

| Langue | Nom | Origine |
|---|---|---|
| Français | Fougère de Java | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Java fern | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Javafarn | « nom ajouté par nous » (le libellé de est le nom scientifique ; nom donné par l'article de) |
| Italien | Leptochilus pteropus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Leptochilus pteropus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28. Abaissé à 18 °C (avant : 20) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 20 cm (hauteur) | [de](https://de.wikipedia.org/wiki/Javafarn) | de : feuilles jusqu'à 21 cm de long, jusqu'à 40 cm pour la variante à larges feuilles. choix de notre part : 20 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#3F7F3A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Leptochilus_pteropus) · [de](https://de.wikipedia.org/wiki/Javafarn)

## Mousse de Java (*Taxiphyllum barbieri*)

- [x] validée par Maxime (2026-11-09)

Identifiant `mousse-de-java`, Wikidata [Q630569](https://www.wikidata.org/wiki/Q630569). Tempérament : Mousse très rustique qui forme des tapis denses sur les racines et les pierres ; abri apprécié des alevins et des crevettes.

| Langue | Nom | Origine |
|---|---|---|
| Français | Mousse de Java | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom donné par l'article fr) |
| Anglais | Java moss | Wikidata, alias |
| Allemand | Javamoos | Wikidata, alias |
| Italien | Taxiphyllum barbieri | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Taxiphyllum barbieri | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6–8 |
| Température | 20 – 28 °C | [en](https://en.wikipedia.org/wiki/Taxiphyllum_barbieri), [fr](https://fr.wikipedia.org/wiki/Taxiphyllum_barbieri) | en : pousse mieux entre 20 et 30 °C ; fr : optimum 21–24 °C, supporte 29–32 °C. choix de notre part : 20–28. Abaissé à 20 °C (avant : 21) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 5 cm (hauteur) | _aucune_ | aucune source chiffrée. choix de notre part : 5 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#558B2F` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Taxiphyllum_barbieri) · [en](https://en.wikipedia.org/wiki/Taxiphyllum_barbieri) · [de](https://de.wikipedia.org/wiki/Javamoos)

## Cryptocoryne wendtii (*Cryptocoryne wendtii*)

- [x] validée par Maxime (2026-11-09)

Identifiant `cryptocoryne-wendtii`, Wikidata [Q4241189](https://www.wikidata.org/wiki/Q4241189). Tempérament : Une des cryptocorynes les plus robustes ; touffe basse à planter dans un sol nourricier. Elle peut perdre ses feuilles au changement de bac (« fonte »), puis repartir.

| Langue | Nom | Origine |
|---|---|---|
| Français | Cryptocoryne wendtii | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Wendt's water trumpet | Wikidata, alias |
| Allemand | Cryptocoryne wendtii | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Cryptocoryne wendtii | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Cryptocoryne wendtii | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 15 cm (hauteur) | _aucune_ | aucune source chiffrée. choix de notre part : 15, à vérifier en priorité |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#7B5E3B` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Cryptocoryne_wendtii)

## Cryptocoryne parva (*Cryptocoryne parva*)

- [x] validée par Maxime (2026-11-09)

Identifiant `cryptocoryne-parva`, Wikidata [Q15339070](https://www.wikidata.org/wiki/Q15339070). Tempérament : Plus petite cryptocoryne, en rosette basse, pour le premier plan ; croissance très lente.

| Langue | Nom | Origine |
|---|---|---|
| Français | Cryptocoryne parva | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Cryptocoryne parva | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Cryptocoryne parva | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Cryptocoryne parva | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Cryptocoryne parva | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 6 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Cryptocoryne_parva) | en : rosette de 5 à 8 cm. choix de notre part : 6 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#6D8B3A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Cryptocoryne_parva)

## Échinodorus (*Echinodorus grisebachii*)

- [x] validée par Maxime (2026-11-09)

Identifiant `echinodorus`, Wikidata [Q476284](https://www.wikidata.org/wiki/Q476284). Tempérament : Grande plante en rosette (épée amazone), à placer seule au centre d'un bac assez grand ; elle apprécie un sol nourricier.

| Langue | Nom | Origine |
|---|---|---|
| Français | Échinodorus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom du genre faute de nom courant sourcé) |
| Anglais | Amazon sword plant | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Grisebachs Schwertpflanze | Wikidata, libellé |
| Italien | Echinodorus grisebachii | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Echinodorus grisebachii | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–7,5 |
| Température | 20 – 28 °C | [de](https://de.wikipedia.org/wiki/Grisebachs_Schwertpflanze) | de : pousse au mieux entre 22 et 28 °C. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 40 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Echinodorus_grisebachii), [fr](https://fr.wikipedia.org/wiki/Echinodorus_grisebachii) | en : feuilles de 40 à 60 cm ; fr : feuilles jusqu'à 60 cm. choix de notre part : 40 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#388E3C` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Echinodorus_grisebachii) · [en](https://en.wikipedia.org/wiki/Echinodorus_grisebachii) · [de](https://de.wikipedia.org/wiki/Grisebachs_Schwertpflanze)

## Vallisnérie (*Vallisneria spiralis*)

- [x] validée par Maxime (2026-11-09)

Identifiant `vallisneria`, Wikidata [Q159160](https://www.wikidata.org/wiki/Q159160). Tempérament : Plante en rubans pour l'arrière-plan, qui se multiplie par stolons et forme vite un rideau ; elle aime une bonne lumière et un sol riche.

| Langue | Nom | Origine |
|---|---|---|
| Français | Vallisnérie | Wikidata, alias |
| Anglais | Straight vallisneria | Wikidata, alias |
| Allemand | Gewöhnliche Wasserschraube | Wikidata, alias |
| Italien | Vallisneria spiralis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Vallisneria spiralis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 50 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Vallisneria_spiralis), [fr](https://fr.wikipedia.org/wiki/Vallisneria_spiralis), [de](https://de.wikipedia.org/wiki/Gew%C3%B6hnliche_Wasserschraube) | en : feuilles jusqu'à 1 m ; fr : jusqu'à 1 m ; de : 50 à 80 cm. choix de notre part : 50 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette. en : décrite comme envahissante (section « Invasive species »). Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#66BB6A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Vallisneria_spiralis) · [en](https://en.wikipedia.org/wiki/Vallisneria_spiralis) · [de](https://de.wikipedia.org/wiki/Gew%C3%B6hnliche_Wasserschraube)

## Vallisnérie d'Amérique (*Vallisneria americana*)

- [x] validée par Maxime (2026-11-09)

Identifiant `vallisneria-geante`, Wikidata [Q2561951](https://www.wikidata.org/wiki/Q2561951). Tempérament : Grande vallisnérie en rubans, pour l'arrière-plan d'un bac haut ; tolère un peu de sel. Les feuilles peuvent dépasser 80 cm.

| Langue | Nom | Origine |
|---|---|---|
| Français | Vallisnérie d'Amérique | Wikidata, libellé |
| Anglais | Wild celery | Wikidata, alias |
| Allemand | Vallisneria americana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Vallisneria americana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Vallisneria americana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 80 cm (hauteur) | [fr](https://fr.wikipedia.org/wiki/Vallisn%C3%A9rie_d'Am%C3%A9rique), [en](https://en.wikipedia.org/wiki/Vallisneria_americana) | fr : feuilles de plus de 80 cm, jusqu'à 1,5 m ; en : plante vendue comme plante d'arrière-plan. choix de notre part : 80 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#4CAF50` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Vallisn%C3%A9rie_d'Am%C3%A9rique) · [en](https://en.wikipedia.org/wiki/Vallisneria_americana) · [de](https://de.wikipedia.org/wiki/Vallisneria_americana)

## Élodée dense (*Egeria densa*)

- [x] validée par Maxime (2026-11-09)

Identifiant `egeria`, Wikidata [Q163649](https://www.wikidata.org/wiki/Q163649). Tempérament : Plante à tiges très vigoureuses, qui oxygène l'eau et absorbe beaucoup de nutriments. Elle n'est pas sur la liste de l'Union des espèces exotiques envahissantes, mais est envahissante hors d'Europe : ne jamais la jeter dans la nature.

| Langue | Nom | Origine |
|---|---|---|
| Français | Élodée dense | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom donné par l'article fr) |
| Anglais | Brazilian waterweed | Wikidata, alias |
| Allemand | Dichtblättrige Wasserpest | Wikidata, libellé |
| Italien | Egeria densa | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Egeria densa | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 8 | [de](https://de.wikipedia.org/wiki/Dichtbl%C3%A4ttrige_Wasserpest) | de : en milieu naturel, presque toujours dans des eaux de pH supérieur à 5,5. choix de notre part : 6–8 |
| Température | 16 – 28 °C | [en](https://en.wikipedia.org/wiki/Elodea_densa), [fr](https://fr.wikipedia.org/wiki/Egeria_densa), [de](https://de.wikipedia.org/wiki/Dichtbl%C3%A4ttrige_Wasserpest) | en : croissance stable de 16 à 28 °C, limite haute 32 °C ; fr : 16 à 28 °C ; de : 16 à 28 °C, jusqu'à 35 °C après adaptation. 16–28 retenu |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 50 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Elodea_densa), [fr](https://fr.wikipedia.org/wiki/Egeria_densa) | en et fr : tiges de 2 m ou plus. choix de notre part : 50 (hauteur courante en aquarium) |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette. en et fr : envahissante là où l'hiver ne gèle pas. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#2E9E4F` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Egeria_densa) · [en](https://en.wikipedia.org/wiki/Elodea_densa) · [de](https://de.wikipedia.org/wiki/Dichtbl%C3%A4ttrige_Wasserpest)

## Hygrophila polysperma (*Hygrophila polysperma*)

- [x] validée par Maxime (2026-11-09)

Identifiant `hygrophila-polysperma`, Wikidata [Q1661596](https://www.wikidata.org/wiki/Q1661596). Tempérament : Plante à tiges, très facile et à croissance rapide, qui garnit vite l'arrière-plan. Elle est envahissante dans d'autres régions chaudes : ne jamais la jeter dans la nature.

| Langue | Nom | Origine |
|---|---|---|
| Français | Hygrophila polysperma | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Indian waterweed | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Indischer Wasserfreund | Wikidata, libellé |
| Italien | Hygrophila polysperma | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Hygrophila polysperma | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | [fr](https://fr.wikipedia.org/wiki/Hygrophila_polysperma) | fr : pH de 6,5 à 7,2. choix de notre part : 6–7,5 |
| Température | 18 – 28 °C | [fr](https://fr.wikipedia.org/wiki/Hygrophila_polysperma) | fr : de 15 à 30 °C, elle supporte même l'eau froide. choix de notre part : 18–28 |
| GH | _aucun_ | [fr](https://fr.wikipedia.org/wiki/Hygrophila_polysperma) | fr : th de 4 à 20 °f (environ 2 à 11 °d). aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 50 cm (hauteur) | [fr](https://fr.wikipedia.org/wiki/Hygrophila_polysperma) | fr : jusqu'à 50 cm de haut. choix de notre part : 50 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette. fr : envahissante dans d'autres régions chaudes. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#7CB342` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Hygrophila_polysperma) · [en](https://en.wikipedia.org/wiki/Hygrophila_polysperma) · [de](https://de.wikipedia.org/wiki/Indischer_Wasserfreund)

## Hygrophila difformis (*Hygrophila difformis*)

- [x] validée par Maxime (2026-11-09)

Identifiant `hygrophila-difformis`, Wikidata [Q1431832](https://www.wikidata.org/wiki/Q1431832). Tempérament : Plante à tiges aux feuilles découpées, facile ; de 20 à 50 cm de haut selon la lumière.

| Langue | Nom | Origine |
|---|---|---|
| Français | Hygrophila difformis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Water wisteria | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Indischer Wasserstern | Wikidata, libellé |
| Italien | Hygrophila difformis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Hygrophila difformis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | [de](https://de.wikipedia.org/wiki/Indischer_Wasserstern) | de : grande tolérance au pH. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 35 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Hygrophila_difformis) | en : 20 à 50 cm de haut sur 15 à 25 cm de large. choix de notre part : 35 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#81C784` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Hygrophila_difformis) · [de](https://de.wikipedia.org/wiki/Indischer_Wasserstern)

## Limnophila sessiliflora (*Limnophila sessiliflora*)

- [x] validée par Maxime (2026-11-09)

Identifiant `limnophila`, Wikidata [Q1768430](https://www.wikidata.org/wiki/Q1768430). Tempérament : Plante à tiges aux feuilles fines en verticilles, de croissance rapide ; elle est envahissante aux États-Unis : ne jamais la jeter dans la nature.

| Langue | Nom | Origine |
|---|---|---|
| Français | Limnophila sessiliflora | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Limnophila sessiliflora | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Limnophila sessiliflora | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Limnophila sessiliflora | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Limnophila sessiliflora | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 30 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Limnophila_sessiliflora) | en : peut dépasser 40 cm, souvent émergée. choix de notre part : 30 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette. en : envahissante aux États-Unis. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#9CCC65` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Limnophila_sessiliflora)

## Ludwigia repens (*Ludwigia repens*)

- [x] validée par Maxime (2026-11-09)

Identifiant `ludwigia-repens`, Wikidata [Q4406281](https://www.wikidata.org/wiki/Q4406281). Tempérament : Plante à tiges aux feuilles rougissant à la lumière. Attention : d'autres ludwigias (Ludwigia grandiflora et peploides) sont interdites dans l'Union ; celle-ci ne l'est pas.

| Langue | Nom | Origine |
|---|---|---|
| Français | Ludwigia repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Creeping primrose-willow | Wikidata, alias |
| Allemand | Ludwigia repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Ludwigia repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Ludwigia repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 40 cm (hauteur) | _aucune_ | aucune source chiffrée. choix de notre part : 40, à vérifier en priorité |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#B5483C` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Ludwigia_repens) · [en](https://en.wikipedia.org/wiki/Ludwigia_repens)

## Rotala rotundifolia (*Rotala rotundifolia*)

- [x] validée par Maxime (2026-11-09)

Identifiant `rotala`, Wikidata [Q2569339](https://www.wikidata.org/wiki/Q2569339). Tempérament : Plante à tiges fines, qui a besoin de beaucoup de lumière (elle perd ses feuilles du bas sinon) ; rosée ou rouge sous un fort éclairage.

| Langue | Nom | Origine |
|---|---|---|
| Français | Rotala rotundifolia | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Rotala rotundifolia | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Rotala rotundifolia | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Rotala rotundifolia | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Rotala rotundifolia | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28. Abaissé à 18 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 30 cm (hauteur) | _aucune_ | aucune source chiffrée. choix de notre part : 30, à vérifier en priorité |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#E57373` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Rotala_rotundifolia)

## Bacopa caroliniana (*Bacopa caroliniana*)

- [x] validée par Maxime (2026-11-09)

Identifiant `bacopa`, Wikidata [Q1767137](https://www.wikidata.org/wiki/Q1767137). Tempérament : Plante à tiges facile, qui pousse même avec peu de lumière ; les feuilles tournent au bronze sous un fort éclairage.

| Langue | Nom | Origine |
|---|---|---|
| Français | Bacopa caroliniana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Bacopa caroliniana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Bacopa caroliniana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Bacopa caroliniana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Bacopa caroliniana | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28. Abaissé à 18 °C (avant : 20) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 50 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Bacopa_caroliniana) | en : 50 à 100 cm (en poussant hors de l'eau). choix de notre part : 50 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#6A9F3F` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Bacopa_caroliniana)

## Cornifle immergé (*Ceratophyllum demersum*)

- [x] validée par Maxime (2026-11-09)

Identifiant `cornifle`, Wikidata [Q157385](https://www.wikidata.org/wiki/Q157385). Tempérament : Plante flottante ou ancrée sans racines, très facile ; bonne pour les premières semaines d'un bac. Elle peut se casser en morceaux et se multiplier vite.

| Langue | Nom | Origine |
|---|---|---|
| Français | Cornifle immergé | Wikidata, libellé |
| Anglais | Coontail | Wikidata, alias |
| Allemand | Raues Hornblatt | Wikidata, libellé |
| Italien | Ceratofillo | Wikidata, alias |
| Espagnol | Cola de zorro | Wikidata, alias |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 8 | [de](https://de.wikipedia.org/wiki/Raues_Hornblatt), [fr](https://fr.wikipedia.org/wiki/Ceratophyllum_demersum) | de : préfère un pH de 6,0 à 7,5 ; fr : pH de 6 à 9. choix de notre part : 6–8 |
| Température | 18 – 28 °C | [de](https://de.wikipedia.org/wiki/Raues_Hornblatt), [en](https://en.wikipedia.org/wiki/Ceratophyllum_demersum), [fr](https://fr.wikipedia.org/wiki/Ceratophyllum_demersum) | de : 18–28 °C de préférence, 15–30 °C possibles ; en : 15–30 °C ; fr : 10 à 30 °C. choix de notre part : 18–28 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | [de](https://de.wikipedia.org/wiki/Raues_Hornblatt) | de : préfère un KH de 5 à 15. aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 50 cm (hauteur) | [fr](https://fr.wikipedia.org/wiki/Ceratophyllum_demersum) | fr : hauteur 50 cm. choix de notre part : 50 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#4E8F3A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Ceratophyllum_demersum) · [en](https://en.wikipedia.org/wiki/Ceratophyllum_demersum) · [de](https://de.wikipedia.org/wiki/Raues_Hornblatt)

## Petite lentille d'eau (*Lemna minor*)

- [x] validée par Maxime (2026-11-09)

Identifiant `lentille-d-eau`, Wikidata [Q26875](https://www.wikidata.org/wiki/Q26875). Tempérament : Minuscule plante flottante qui couvre vite la surface ; elle consomme les nitrates mais peut priver d'oxygène et de lumière. À contenir.

| Langue | Nom | Origine |
|---|---|---|
| Français | Petite lentille d'eau | Wikidata, alias |
| Anglais | Common duckweed | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom courant d'aquariophilie) |
| Allemand | Kleine Wasserlinse | Wikidata, libellé |
| Italien | Lenticchia d'acqua | Wikidata, alias |
| Espagnol | Lemna minor | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8 | [en](https://en.wikipedia.org/wiki/Lemna_minor), [fr](https://fr.wikipedia.org/wiki/Lemna_minor) | en : croissance optimale à pH 6,5–8 ; fr : optimal à pH 6,5–7,5, supporte 5 à 9. choix de notre part : 6,5–8 |
| Température | 15 – 30 °C | [en](https://en.wikipedia.org/wiki/Lemna_minor) | en : peut pousser de 6 à 33 °C. choix de notre part : 15–30 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 1 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Lemna_minor), [fr](https://fr.wikipedia.org/wiki/Lemna_minor) | en et fr : feuilles de 1 à 8 mm, racine de 1 à 2 cm. choix de notre part : 1 (flottante) |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#7CB342` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Lemna_minor) · [en](https://en.wikipedia.org/wiki/Lemna_minor) · [de](https://de.wikipedia.org/wiki/Kleine_Wasserlinse)

## Grenouillette d'Amazonie (*Limnobium laevigatum*)

- [x] validée par Maxime (2026-11-09)

Identifiant `grenouillette`, Wikidata [Q6549756](https://www.wikidata.org/wiki/Q6549756). Tempérament : Plante flottante à petites feuilles rondes et racines pendantes, utile pour absorber les nutriments ; il faut garder le dessus des feuilles sec. Elle est envahissante aux États-Unis et au Chili : ne jamais la jeter dans la nature. Elle n'est pas sur la liste de l'Union.

| Langue | Nom | Origine |
|---|---|---|
| Français | Grenouillette d'Amazonie | « nom ajouté par nous » (le libellé fr est le nom scientifique ; nom courant d'aquariophilie) |
| Anglais | Amazon frogbit | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Südamerikanischer Froschbiss | Wikidata, libellé |
| Italien | Limnobium laevigatum | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Limnobium laevigatum | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6–8 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 10 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Hydrocharis_laevigata) | en : plantes mûres jusqu'à 50 cm de haut dans la nature, feuilles émergées. choix de notre part : 10 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette. en et de : envahissante en Amérique du Nord et au Chili. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter) |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#6FAF4A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Hydrocharis_laevigata) · [de](https://de.wikipedia.org/wiki/S%C3%BCdamerikanischer_Froschbiss)

## Marimo (*Aegagropila linnaei*)

- [x] validée par Maxime (2026-11-09)

Identifiant `marimo`, Wikidata [Q149096](https://www.wikidata.org/wiki/Q149096). Tempérament : Boule d'algue verte (forme de croissance d'une algue filamenteuse), pas une vraie plante ; très lente, elle aime l'eau fraîche. Elle est classée comme plante pour l'affichage.

| Langue | Nom | Origine |
|---|---|---|
| Français | Marimo | Wikidata, alias |
| Anglais | Marimo | Wikidata, libellé |
| Allemand | Mooskugel | Wikidata, alias |
| Italien | Alga a Palla | Wikidata, alias |
| Espagnol | Aegagropila linnaei | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8, à vérifier en priorité |
| Température | 15 – 25 °C | _aucune_ | aucune source chiffrée. choix de notre part : 15–25 (eau fraîche), à vérifier en priorité |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 5 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Marimo), [de](https://de.wikipedia.org/wiki/Aegagropila_linnaei) | en : croît d'environ 5 mm par an, jusqu'à 20–30 cm au lac Akan ; de : colonies de boules de 12 cm. choix de notre part : 5, à vérifier en priorité |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) ; le marimo est une algue, classée « plante » pour l'affichage : tranché par Maxime (2026-11-09), choix actuel gardé (classé « plante ») |
| Couleur | `#2F6B3A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Aegagropila_linnaei) · [en](https://en.wikipedia.org/wiki/Marimo) · [de](https://de.wikipedia.org/wiki/Aegagropila_linnaei)

## Hydrocotyle tripartita (*Hydrocotyle tripartita*)

- [x] validée par Maxime (2026-11-09)

Identifiant `hydrocotyle-tripartita`, Wikidata [Q15552976](https://www.wikidata.org/wiki/Q15552976). Tempérament : Petite plante rampante aux feuilles en trèfle, souvent utilisée en tapis de premier plan. L'article en est très court : tout est à vérifier en priorité. À ne pas confondre avec Hydrocotyle ranunculoides, interdite dans l'Union.

| Langue | Nom | Origine |
|---|---|---|
| Français | Hydrocotyle tripartita | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Hydrocotyle tripartita | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Hydrocotyle tripartita | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Hydrocotyle tripartita | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Hydrocotyle tripartita | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5, à vérifier en priorité |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28, à vérifier en priorité |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 10 cm (hauteur) | _aucune_ | aucune source chiffrée. choix de notre part : 10, à vérifier en priorité |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#8BC34A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Hydrocotyle_tripartita)

## Staurogyne repens (*Staurogyne repens*)

- [x] validée par Maxime (2026-11-09)

Identifiant `staurogyne`, Wikidata [Q15368979](https://www.wikidata.org/wiki/Q15368979). Tempérament : Plante rampante qui forme un tapis vert vif au premier plan ; elle aime la lumière.

| Langue | Nom | Origine |
|---|---|---|
| Français | Staurogyne repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Staurogyne repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Staurogyne repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Italien | Staurogyne repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Staurogyne repens | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 10 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Staurogyne_repens) | en : forme un tapis vert vif au fond. choix de notre part : 10 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#5FAE3C` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Staurogyne_repens)

## Aponogeton crispus (*Aponogeton crispus*)

- [x] validée par Maxime (2026-11-09)

Identifiant `aponogeton`, Wikidata [Q1786857](https://www.wikidata.org/wiki/Q1786857). Tempérament : Plante à bulbe et à feuilles ondulées, la plus facile des aponogétons ; elle apprécie un bac mûr et nourri, et parfois une période de repos.

| Langue | Nom | Origine |
|---|---|---|
| Français | Aponogeton crispus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Anglais | Aponogeton crispus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Krause Wasserähre | Wikidata, libellé |
| Italien | Aponogeton crispus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Aponogeton crispus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 20 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 20–28. Abaissé à 20 °C (avant : 22) : tolérance des plantes robustes en bac domestique, pour ne pas déconseiller un bac non chauffé à 19–20 °C — choix de notre part |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 40 cm (hauteur) | [de](https://de.wikipedia.org/wiki/Krause_Wasser%C3%A4hre), [en](https://en.wikipedia.org/wiki/Aponogeton_crispus) | de : 30 à 50 cm de haut, bac d'au moins 150 L ; en : feuilles de 20 à 35 cm. choix de notre part : 40 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#558B2F` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Aponogeton_crispus) · [de](https://de.wikipedia.org/wiki/Krause_Wasser%C3%A4hre)

## Sagittaire (*Sagittaria subulata*)

- [x] validée par Maxime (2026-11-09)

Identifiant `sagittaire`, Wikidata [Q1434390](https://www.wikidata.org/wiki/Q1434390). Tempérament : Plante à rubans pour le premier ou le deuxième plan, qui forme un gazon dense (forme naine, environ 5 cm) ; elle se multiplie vite.

| Langue | Nom | Origine |
|---|---|---|
| Français | Sagittaire | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom du genre faute de nom courant sourcé) |
| Anglais | Sagittaria subulata | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Flutendes Pfeilkraut | Wikidata, libellé |
| Italien | Sagittaria subulata | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Sagittaria subulata | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6,5 – 8 | _aucune_ | aucune source chiffrée. choix de notre part : 6,5–8 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28 |
| GH | _aucun_ | [de](https://de.wikipedia.org/wiki/Flutendes_Pfeilkraut) | de : GH de 10 à 15. aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | [de](https://de.wikipedia.org/wiki/Flutendes_Pfeilkraut) | de : KH de 4 à 8. aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 10 cm (hauteur) | [de](https://de.wikipedia.org/wiki/Flutendes_Pfeilkraut) | de : la forme naine forme un gazon d'environ 5 cm de haut. choix de notre part : 10 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#43A047` | _aucune_ | choix de notre part : teinte dominante |

Sources : [en](https://en.wikipedia.org/wiki/Sagittaria_subulata) · [de](https://de.wikipedia.org/wiki/Flutendes_Pfeilkraut)

## Scirpe épingle (*Eleocharis acicularis*)

- [x] validée par Maxime (2026-11-09)

Identifiant `eleocharis`, Wikidata [Q159148](https://www.wikidata.org/wiki/Q159148). Tempérament : Plante en fines tiges qui forme un gazon, pour le premier plan ; elle demande beaucoup de lumière, et du CO2 pour bien pousser.

| Langue | Nom | Origine |
|---|---|---|
| Français | Scirpe épingle | Wikidata, alias |
| Anglais | Eleocharis acicularis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Allemand | Nadelsimse | Wikidata, libellé |
| Italien | Eleocharis acicularis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Eleocharis acicularis | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 10 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Eleocharis_acicularis), [de](https://de.wikipedia.org/wiki/Nadel-Sumpfbinse), [fr](https://fr.wikipedia.org/wiki/Eleocharis_acicularis) | en : tiges jusqu'à 15 cm ; de : 2 à 15 cm de haut ; fr : jusqu'à 50 cm quand elles sont immergées. choix de notre part : 10 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#7FB43A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Eleocharis_acicularis) · [en](https://en.wikipedia.org/wiki/Eleocharis_acicularis) · [de](https://de.wikipedia.org/wiki/Nadel-Sumpfbinse)

## Riccie des flots (*Riccia fluitans*)

- [x] validée par Maxime (2026-11-09)

Identifiant `riccia`, Wikidata [Q133697](https://www.wikidata.org/wiki/Q133697). Tempérament : Hépatique flottante en fins rubans, qui forme un matelas ; refuge des alevins. Elle peut aussi se fixer au fond.

| Langue | Nom | Origine |
|---|---|---|
| Français | Riccie des flots | Wikidata, alias |
| Anglais | Floating crystalwort | « nom ajouté par nous » (le libellé en est le nom scientifique ; nom donné par l'article en) |
| Allemand | Untergetauchtes Sternlebermoos | Wikidata, libellé |
| Italien | Riccia fluitans | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Riccia fluitans | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 18 – 28 °C | _aucune_ | aucune source chiffrée. choix de notre part : 18–28 |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 2 cm (hauteur) | [de](https://de.wikipedia.org/wiki/Untergetauchtes_Sternlebermoos) | de : thalles jusqu'à 4 cm de long. choix de notre part : 2 |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#66A34A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Riccia_fluitans) · [en](https://en.wikipedia.org/wiki/Riccia_fluitans) · [de](https://de.wikipedia.org/wiki/Untergetauchtes_Sternlebermoos)

## Lotus tigré (*Nymphaea lotus*)

- [x] validée par Maxime (2026-11-09)

Identifiant `lotus-tigre`, Wikidata [Q161111](https://www.wikidata.org/wiki/Q161111). Tempérament : Nymphéa d'aquarium à feuilles tachetées et à bulbe ; il a besoin d'une forte lumière et de place, et ses feuilles finissent par flotter à la surface.

| Langue | Nom | Origine |
|---|---|---|
| Français | Lotus tigré | Wikidata, alias |
| Anglais | Tiger lotus | Wikidata, alias |
| Allemand | Tigerlotus | Wikidata, libellé |
| Italien | Nymphaea lotus | « nom ajouté par nous », tranché par Maxime (2026-11-09), choix actuel gardé (nom scientifique faute de nom courant sourcé) |
| Espagnol | Loto tigre | Wikidata, alias |

| Valeur | Fiche | Sources | Note |
|---|---|---|---|
| pH | 6 – 7,5 | _aucune_ | aucune source chiffrée. choix de notre part : 6–7,5 |
| Température | 22 – 28 °C | [en](https://en.wikipedia.org/wiki/Nymphaea_lotus) | en : cultivé sous forte lumière en eau chaude de 22 à 28 °C. Plancher gardé à 22 °C : la plante tropicale cesse de pousser nettement en dessous de 21 °C ; tranché par Maxime (2026-11-09), choix actuel gardé (voir fin de fiche) |
| GH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| KH | _aucun_ | _aucune_ | aucune valeur retenue : plante tolérante, laissée vide |
| Volume minimum | _aucun_ | _aucune_ | plante : champ laissé vide (pas de verdict de volume) |
| Taille adulte | 30 cm (hauteur) | [en](https://en.wikipedia.org/wiki/Nymphaea_lotus) | en : feuilles flottantes de 10 à 50 cm de large. choix de notre part : 30 (hauteur de tige courante en aquarium) |
| Groupe minimum | 1 | _aucune_ | plante : valeur par défaut (pas de verdict de groupe) |
| Durée de vie | 1 an | _aucune_ | plante : valeur technique, non affichée (voir l'introduction) |
| Comportement | _aucune_ | _aucune_ | plante : aucune étiquette |
| Régime | `OMNIVORE` | _aucune_ | valeur technique, non affichée (voir l'introduction) |
| Dessin, nage, zone | `PLANT`, `SLOW`, `BOTTOM` | _aucune_ | choix de notre part (affichage) |
| Couleur | `#6D8F3A` | _aucune_ | choix de notre part : teinte dominante |

Sources : [fr](https://fr.wikipedia.org/wiki/Nymphaea_lotus) · [en](https://en.wikipedia.org/wiki/Nymphaea_lotus) · [de](https://de.wikipedia.org/wiki/Tigerlotus)

## Points tranchés (Maxime, 2026-11-09 : tous les choix actuels sont gardés)

La seule étiquette qui pèse sur les verdicts de ce lot est `EATS_SNAILS` (helena). Les autres points sont des noms, des classements d'affichage et des espèces envahissantes hors de l'Union.

- **Helena** : `EATS_SNAILS` retenu : tranché par Maxime (2026-11-09), choix actuel gardé (tout autre escargot « déconseillé » : les 6 autres escargots de ce lot, et la helena « déconseillée » avec l'oscar, la loche-clown, la loche pakistanaise, la loche naine et le tétraodon nain). Sources : en (carnivore, mange vers et escargots, sert à limiter les petits escargots), fr (mange physes, planorbes, mélanoïdes) et de (carnivore, charogne, autres escargots et leurs pontes). Sans cette étiquette, aucune cohabitation d'escargots ne serait avertie.
- **Espèces envahissantes hors de l'Union** : Planorbe rouge (de : échappée d'aquariums, établie presque partout dans le monde), Mélanoïde (en et de : établie hors de son aire par les aquariophiles, supplante des escargots locaux), Physe (en : supplante les escargots locaux), Vallisnérie (en : décrite comme envahissante (section « Invasive species »)), Élodée dense (en et fr : envahissante là où l'hiver ne gèle pas), Hygrophila polysperma (fr : envahissante dans d'autres régions chaudes), Limnophila sessiliflora (en : envahissante aux États-Unis), Grenouillette d'Amazonie (en et de : envahissante en Amérique du Nord et au Chili). Aucune n'est sur la liste de l'Union (règlement 1143/2014), mais elles gênent ailleurs. Envahissante hors de l'Union : tranché par Maxime (2026-11-09), choix actuel gardé (garder la fiche ou l'écarter). L'égeria (*Egeria densa*) est le cas le plus net : l'article en la dit parfois retirée de la vente.
- **Noms scientifiques faute de nom courant** : le nom scientifique est repris, aucun nom courant sourcé n'ayant été trouvé, pour *Atyopsis moluccensis* (it/es), *Palaemonetes paludosus* (de/it/es), *Atya gabonensis* (it/es), *Neritina natalensis* (it/es), *Clithon corona* (de/it/es), *Planorbella duryi* (it/es), *Planorbarius corneus* (it), *Melanoides tuberculata* (it/es), *Physella acuta* (it/es), *Anentome helena* (it/es), *Leptochilus pteropus* (it/es), *Taxiphyllum barbieri* (it/es), *Cryptocoryne wendtii* (fr/de/it/es), *Cryptocoryne parva* (les cinq langues), *Echinodorus grisebachii* (it/es), *Vallisneria spiralis* (it/es), *Vallisneria americana* (de/it/es), *Egeria densa* (it/es), *Hygrophila polysperma* (fr/it/es), *Hygrophila difformis* (fr/it/es), *Limnophila sessiliflora* (les cinq langues), *Ludwigia repens* (fr/de/it/es), *Rotala rotundifolia* (les cinq langues), *Bacopa caroliniana* (les cinq langues), *Lemna minor* (es), *Limnobium laevigatum* (it/es), *Aegagropila linnaei* (es), *Hydrocotyle tripartita* (les cinq langues), *Staurogyne repens* (les cinq langues), *Aponogeton crispus* (fr/en/it/es), *Sagittaria subulata* (en/it/es), *Eleocharis acicularis* (en/it/es), *Riccia fluitans* (it/es), *Nymphaea lotus* (it). Tranché par Maxime (2026-11-09) : choix actuel gardé.
- **Noms de genre faute de nom courant** : « Anubias » (cinq langues), « Échinodorus » (fr) et « Sagittaire » (fr) reprennent le nom du genre. Tranché par Maxime (2026-11-09) : choix actuel gardé.
- **Noms ajoutés par nous** (fr) : « Crevette crystal red », « Crevette fantôme », « Crevette vampire », « Néritine zébrée », « Nérite couronnée », « Planorbe rouge », « Planorbe cornée », « Mélanoïde », « Physe », « Escargot assassin », « Fougère de Java », « Grenouillette d'Amazonie », « Mousse de Java », « Élodée dense » ; (en) : « Bamboo shrimp », « Vampire shrimp », « Horned nerite », « Common duckweed », « Ghost shrimp », « Zebra nerite », « Seminole rams-horn », « Acute bladder snail », « Assassin snail », « Java fern », « Amazon sword plant », « Indian waterweed », « Water wisteria », « Amazon frogbit », « Floating crystalwort » ; (de) : « Bienengarnele », « Zebrarennschnecke », « Raubturmdeckelschnecke », « Javafarn ». Tranché par Maxime (2026-11-09) : choix actuel gardé.
- **Marimo** : le marimo est une algue, classée « plante » pour l'affichage : tranché par Maxime (2026-11-09), choix actuel gardé (classé « plante »).
- **Plantes, plancher de température** : les planchers de 12 plantes ont été abaissés (mousse de Java, anubias, Cryptocoryne wendtii et parva, échinodorus, Hygrophila difformis, Limnophila, staurogyne, aponogeton à 20 °C ; fougère de Java, bacopa et rotala à 18 °C), pour qu'un bac non chauffé à 19–20 °C ne les affiche pas « Trop froid » : choix de notre part, validé par Maxime (2026-11-09). Effet : à 20 °C elles ressortent toutes « Vont bien » au lieu de « Déconseillé » ; à 19 °C, seules la fougère de Java, la bacopa et la rotala (plancher 18 °C) le sont, les neuf autres restent « Déconseillé ». Les sources citées pour l'anubias (en, fr : 22–28 °C) et l'échinodorus (de : au mieux 22–28 °C) donnent un optimum, pas une tolérance.
- **Lotus tigré, plancher de température** : plancher gardé à 22 °C (source en : eau chaude de 22 à 28 °C ; plante tropicale qui cesse de pousser nettement sous 21 °C). Effet : dans un bac non chauffé à 19–20 °C, elle ressort « Déconseillé » (« Trop froid »). Tranché par Maxime (2026-11-09) : choix actuel gardé.
- **Plantes : GH, KH, volume** laissés vides, pH et températures larges : voir l'introduction. Choix de notre part, validé par Maxime (2026-11-09) : les plages de GH et de KH demanderaient une source (aucun article Wikipédia consulté n'en donne une sûre).
- **Crevette crystal red** : le crystal red est une sélection de *Caridina cantonensis* (même espèce sauvage, nommée crevette abeille en anglais). Sa fiche suit l'espèce, validé par Maxime (2026-11-09) ; pH, GH et KH (eau très douce) sont des choix de notre part, à vérifier en priorité.
- **Nérite couronnée (*Clithon corona*)** : l'article en ne donne aucune valeur et dit qu'elle vit aussi en eau saumâtre ; toutes ses valeurs sont à vérifier en priorité.
- **Hydrocotyle tripartita** : l'article en ne contient presque rien ; toutes ses valeurs sont à vérifier en priorité.
