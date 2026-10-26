# Validation locale de la migration RevenueCat — 26 octobre 2026

Base : `integration/aqualog-v1` au commit `9fbe8da`. Réalisation sur `feature/19-revenuecat`, dans le worktree de travail #19 existant. Le propriétaire a choisi RevenueCat et indiqué qu’aucun projet n’existe encore.

## Résultats

| Contrôle | Résultat |
|---|---|
| Cœur JVM, dont trois nouveaux cas au contrat public du coordinateur Pro | 114 tests, 0 échec |
| Unités Android debug, dont contrats de traduction et vérificateur Supabase authentifié | 29 tests, 0 échec |
| Assemblages Android debug/tests, bêta et release | Réussite ; builds locaux sans clé RevenueCat ni candidat Play signé distribuable |
| Lint debug | 0 erreur, 54 avertissements non bloquants |
| Fonctions Edge (migration initiale, RevenueCat, suppression, feedback) | 25 tests Deno, 0 échec |
| Vérification TypeScript des fonctions achat, webhook et suppression | Réussite |
| Base dédiée : migrations et pgTAP, dont 16 nouveaux contrôles RevenueCat | 8 fichiers, 130 tests, 0 échec |
| Contrat public de configuration Gradle | 7 tests Python, 0 échec ; refus des clés secrètes/Test Store et du partage de clé entre environnements |
| Parcours Android sur Pixel_9a API 36 | 7 cas distincts validés après relance ciblée ; 1 cas de paiement réel ignoré faute de prérequis |
| `git diff --check` | Réussite |

Les tests du coordinateur vérifient qu’un callback RevenueCat ne débloque rien sans relecture serveur, qu’une panne de vérification ne donne pas Pro et qu’une restauration exige l’identité authentifiée. Les tests HTTP/Deno exercent l’authentification, le rejet de claims fournis par le client, les expirations/remboursements/grâces, les environnements et les webhooks. Les tests SQL couvrent l’isolation A/B, les permissions, les snapshots désordonnés et le blocage d’une identité en suppression. Aucun reçu de qualification externe n’est fabriqué.

La première compilation a révélé l’usage d’un helper interne du SDK, remplacé par ses propriétés publiques ; le SDK Maven utilisé est épinglé à `10.29.0`. La première passe d’instrumentation n’a pas démarré car l’émulateur s’était arrêté. Après redémarrage du même AVD, la passe de huit cas a donné six réussites, un échec et un skip. L’échec concernait l’affichage de « Local-only » après déconnexion : le statut existait, mais le test devait défiler jusqu’à lui. L’assertion d’affichage et les contrôles fonctionnels sont conservés. La relance AccountControl/Privacy/transaction opt-in a donné trois réussites et le même skip. Les quatre autres réussites sont celles de MultiAquarium et RapidSession de la première passe ; il ne s’agit pas d’une dernière passe unique de huit cas.

Les textes de confidentialité et de suppression sont mis à jour dans FR/EN/DE/IT/ES ; leurs boîtes de dialogue peuvent défiler avec le texte système. La relecture linguistique et TalkBack restent des validations humaines.

## Ressources et limites

Pile locale dédiée : `aqualog-19-revenuecat-20261026`, API `59321`, DB `59322`, shadow `59320`, inspector `59327`, services inutiles désactivés et ports réservés distincts. Seule cette pile a été initialisée/réinitialisée pour vérifier les migrations. La pile historique `aqualog-ticket-10` est restée intacte. Pixel_9a API 36 utilisé sans wipe. Les ressources démarrées pour cette validation sont arrêtées après usage.

Preuves et journaux conservés dans `/Users/maximerollin/.codex/validation-artifacts/aqualog-revenuecat-20261026`.

Ces résultats ne valident ni l’adaptateur SDK contre un compte RevenueCat réel, ni un achat/restauration Google Play réel, ni la réception SaaS, ni la suppression chez RevenueCat déployé. Le test instrumenté de transaction réelle reste opt-in et ignoré ici. Aucun service externe n’a été créé, configuré ou déployé ; aucune issue n’est fermée. Suivre [le guide de raccordement](revenuecat-setup.md) puis exécuter le scénario externe avant toute qualification Play.
