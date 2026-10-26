# Validation locale — configuration commune et déploiement

Date : 26 octobre 2026. Branche : `feature/19-revenuecat`, suite du commit RevenueCat `4764220`.

## Résultats

- JVM : 114 tests, aucun échec.
- Unités Android : 30 tests en debug et 30 en release, aucun échec.
- Lint : aucune erreur ; 54 avertissements debug et 52 release. Assemblage release réussi.
- Configuration Gradle : 7 tests Python, y compris rejet des anciens préfixes, clés secrètes/Test Store et groupes incomplets.
- Edge : 30 tests, aucun échec ; typage des cinq fonctions validé.
- SQL/RLS : 138 assertions sur 9 fichiers ; intégration HTTP authentifiée validée.
- Workflow : actionlint 1.7.12, aucune erreur.
- Pixel_9a API 36 : suite de 31 tests exécutée avec 2 ignorés opt-in et 1 échec de sélecteur sur les tâches. Après correction du sélecteur ambigu titre/bouton, les 3 tests TaskFlowTest ont été relancés avec succès. Les 29 cas exécutables ont ainsi passé au cours de ces exécutions ; aucune exécution complète unique après cette dernière correction n’est revendiquée.
- Les deux échecs UI d’une première tentative (attente de fermeture du formulaire Aquarium et défilement vers la confirmation d’export) ont été corrigés et ces cas ont passé dans la suite complète suivante.
- Qualification opt-in portable : 4 phases validées, création puis relecture après arrêt du processus, import dans une base neuve puis relecture après arrêt. Même package, deux bases dédiées ; aucune preuve de deux appareils physiques.

La transaction Google Play reste ignorée sans achat préparé avec un compte license tester ; aucune transaction RevenueCat/Google Play réelle, aucun webhook distant et aucune livraison LogSnag distante ne sont revendiqués. Les tests de notifications utilisent des dépendances contrôlées. GitHub Actions n’a pas été exécuté sur un runner hébergé ; seuls ses outils et contrats ont été vérifiés localement.

## Isolation et preuves

Backend local : `aqualog-ci-20261026-unified`, ports 593xx, migrations neuves sans reset du projet historique. La stack `aqualog-ticket-10` n’a pas été modifiée. Pixel_9a démarré sans wipe ni sauvegarde de snapshot ; les animations ont été temporairement désactivées pour correspondre à la CI puis restaurées. Les seules ressources arrêtées sont celles de cette validation.

Journaux et rapports conservés hors du dépôt : `/Users/maximerollin/.codex/validation-artifacts/aqualog-deployment-20261026`. Le journal de la suite complète conserve l’échec de sélecteur, et le journal ciblé documente sa correction.

Avant production : comptes externes et secrets à configurer, protections GitHub à activer, politique de confidentialité à publier, qualification TalkBack/routine et achats réels de test à réaliser. Voir [déploiement](deployment.md) et [configuration RevenueCat](revenuecat-setup.md).
