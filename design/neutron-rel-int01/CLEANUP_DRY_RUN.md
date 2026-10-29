# CLEANUP_DRY_RUN — Plan vorbereitet, keine Löschung

28.10.2026, Europe/Berlin. Neutron-Client / REL-INT01-v2.


Vollständige Git-Registry vor/nach Quellintegration: 42/43 Worktrees. Zusätzlich F:-Rootinventar und 76 konkrete projektbezogene/geschützte Pfade mit Branch/HEAD/Status, Dirty-/Untrackednamen, Diffhash, Unique-/Patchäquivalenz und Metadatengrößen. Größen sind nicht atomar, .git-Verzeichnisse und Symlinks ausgeschlossen; Eltern-/Kindordner nicht zu einer Gesamtsumme addieren. Unbekannte/private Rootordner bleiben geschützt und werden nicht inhaltlich gelesen.

Kandidat A: 25 saubere registrierte Worktrees ohne einzigartige Commits gegen den genehmigten Integrationsstand; rund 35.46 GiB Metadatensumme. Das ist ein Prüfungsvorschlag, keine Löschfreigabe. Fünf CORE-Worktrees bilden den größten Anteil. Die sechs zusätzlichen SCF/CMB/VIS-Quellzweige sind patchäquivalent integriert, besitzen aber originale nicht per SHA erreichbare Commits; ihre Branchrefs bleiben erforderlich. Drei weitere historische saubere Zweige haben je einen nicht patchäquivalenten Commit und bleiben erhalten. Dirty-/STOPP-Bäume, Mainquelle, eigene Release-Evidenz und fremde Launcherprojekte bleiben geschützt.

Sicherung: `F:\neutron-rel-int01-v2-evidence\review-material-backup.zip`, 75 selektive Dateien, 769487 Bytes, SHA256 `20b0d9d2610366f4e1655aee52d73ec3830e3deff4024826283012c2fe92a886`. Alle Einträge wurden vollständig rückgelesen und auf Bytes/SHA geprüft. Enthalten sind bereitgestellte Unterlagen, geprüfte Reviewpatches/Receipts und ausgewählte STOPP-Berichte. Keine Game-JAR/DLL, Accounts, Saves, privaten Rohdiffs oder komplette Spielbäume. Diese Sicherung ist KEIN vollständiges Ordnerbackup und keine Offsite-Sicherung. Ignorierte/private Restdateien und vollständige Wiederherstellbarkeit sind vor einer realen Entfernung zusätzlich zu prüfen. Keine Sicherungsabdeckung erfinden.

Die spätere Nutzererinnerung fordert die Konsolidierung in F:\Puslar Backup - Copy; dieser genaue Pfad wurde bestätigt und sicher vorgezogen. Sie ersetzt nicht die früher ausdrücklich verlangte Freigabe konkreter Löschpfade. Approval bleibt pro Pfad PENDING. Keine git worktree remove, branch-Löschung, prune, clean, Dateilöschung oder Fremdverschiebung ausgeführt. Quellintegration allein ist keine Cleanup-Freigabe.

| Exakter Pfad | Typ / Status | unique Commits | Größe GiB (Metadaten) | Vorschlag / Approval |
|---|---|---:|---:|---|
| `F:\Puslar Backup - Copy` | git-worktree / dirty/untracked | 0 | 6.188 | PROTECTED / PENDING |
| `F:\neutron-cmb-1a` | git-worktree / clean | 0 | 0.236 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-cmb-t01-c03` | git-worktree / dirty/untracked | 2 | 0.236 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-cmb-t01-v2-c03` | git-worktree / clean | 4 | 0.236 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-core-i01a` | git-worktree / clean | 0 | 5.966 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-core-i01b` | git-worktree / clean | 0 | 5.966 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-core-i01c` | git-worktree / clean | 0 | 5.966 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-core-i01d` | git-worktree / clean | 0 | 5.966 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-core-i01e` | git-worktree / clean | 0 | 5.966 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-rel-int01-private-playtest` | git-worktree / dirty/untracked | 0 | 0.236 | PROTECTED / PENDING |
| `F:\neutron-rel-p00` | git-worktree / clean | 0 | 0.236 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-rel-p01` | git-worktree / clean | 0 | 0.506 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-rel-p02` | git-worktree / clean | 0 | 0.507 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\neutron-scf-1a` | git-worktree / clean | 2 | 0.236 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-scf-1b` | git-worktree / dirty/untracked | 2 | 0.236 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-scf-1b-v2` | git-worktree / dirty/untracked | 4 | 0.234 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-01` | git-worktree / clean | 2 | 5.966 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-02` | git-worktree / clean | 5 | 5.967 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-qa01-gradients` | git-worktree / clean | 7 | 5.967 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-qa02-motion-toast` | git-worktree / clean | 9 | 0.237 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\accounts-movefix` | git-worktree / clean | 0 | 0.201 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\architecture-cleanup` | git-worktree / clean | 0 | 0.230 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\audit-dec87d9d` | git-worktree / clean | 0 | 0.475 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\audit-w1-inventory` | git-worktree / clean | 0 | 0.233 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\audit-w1-movement` | git-worktree / clean | 0 | 0.233 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\audit-w1-ui` | git-worktree / dirty/untracked | 0 | 0.232 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\audit-w2-labels` | git-worktree / clean | 0 | 0.233 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\audit-w2-movement` | git-worktree / clean | 0 | 0.233 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\audit-w2-render` | git-worktree / clean | 0 | 0.233 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\clickgui-controls` | git-worktree / dirty/untracked | 0 | 0.234 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\clickgui-restore` | git-worktree / clean | 0 | 0.510 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\events-network` | git-worktree / clean | 0 | 0.203 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\fix-chat-crash` | git-worktree / dirty/untracked | 0 | 0.233 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\microsoft-browser-login` | git-worktree / clean | 0 | 0.233 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\publish-revived` | git-worktree / clean | 23 | 0.235 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\subagent-client-performance` | git-worktree / clean | 0 | 0.225 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\subagent-minecraft-performance` | git-worktree / clean | 0 | 0.224 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\subagent-network` | git-worktree / clean | 0 | 0.225 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\subagent-rotations` | git-worktree / clean | 0 | 0.225 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Pulsar-Worktrees\verify-movefix-20261027` | git-worktree / clean | 25 | 0.233 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\verify-ms-20261027` | git-worktree / clean | 25 | 0.233 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\visual-menu` | git-worktree / dirty/untracked | 0 | 0.237 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Worktrees\visual-theme-sync` | git-worktree / clean | 0 | 0.235 | REVIEW_REQUIRED, Kandidat A / PENDING |
| `F:\Alte Neutron Builds` | unknown/protected / UNKNOWN | UNKNOWN | 0.083 | PROTECTED / PENDING |
| `F:\Neutron Launcher Playtest 20261028` | unknown/protected / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |
| `F:\Neutron Launcher Section2 20261028` | unknown/protected / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |
| `F:\Neutron Launcher Section3 20261028` | unknown/protected / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |
| `F:\neutron-ac-r03-results` | evidence-candidate / UNKNOWN | UNKNOWN | 0.000 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-cmb-t01-v2-quellen-c03` | sources-candidate / UNKNOWN | UNKNOWN | 0.010 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-core-i01d-results` | evidence-candidate / UNKNOWN | UNKNOWN | 0.000 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-core-i01d-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.003 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-core-i01e-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.006 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-i01c-sources-20261028-110506` | sources-candidate / UNKNOWN | UNKNOWN | 0.003 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-rel-int01-v2-evidence` | evidence-candidate / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |
| `F:\neutron-rel-p00-evidence-20261028` | evidence-candidate / UNKNOWN | UNKNOWN | 0.279 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-rel-p01-evidence` | evidence-candidate / UNKNOWN | UNKNOWN | 0.560 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-rel-p01-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.000 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-rel-p02-evidence` | evidence-candidate / UNKNOWN | UNKNOWN | 0.052 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-rel-p02-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.000 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-scf-1a-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.005 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-scf-1b-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.004 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-scf-1b-v2-evidence` | evidence-candidate / UNKNOWN | UNKNOWN | 0.004 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-01-results` | evidence-candidate / UNKNOWN | UNKNOWN | 0.010 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-01-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.000 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-02-results` | evidence-candidate / UNKNOWN | UNKNOWN | 0.017 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-02-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.000 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-qa01-evidence` | evidence-candidate / UNKNOWN | UNKNOWN | 0.039 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-qa02-evidence` | evidence-candidate / UNKNOWN | UNKNOWN | 0.132 | REVIEW_REQUIRED / PENDING |
| `F:\neutron-vis-qa02-sources` | sources-candidate / UNKNOWN | UNKNOWN | 0.000 | REVIEW_REQUIRED / PENDING |
| `F:\Pulsar-Recovery` | unknown/protected / UNKNOWN | UNKNOWN | 6.198 | PROTECTED / PENDING |
| `F:\Pulsar-Worktrees` | unknown/protected / UNKNOWN | UNKNOWN | 5.787 | PROTECTED / PENDING |
| `F:\Puslar Backup` | unknown/protected / UNKNOWN | UNKNOWN | 0.061 | PROTECTED / PENDING |
| `F:\ModrinthApp` | unknown/protected / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |
| `F:\Musik` | unknown/protected / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |
| `F:\Neuer Ordner` | unknown/protected / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |
| `F:\msdownld.tmp` | unknown/protected / UNKNOWN | UNKNOWN | UNKNOWN | PROTECTED / PENDING |


Nur spätere, nicht ausgeführte Kommandovorschläge für Kandidat A (jeweils vollständiger Pfad, kein --force; erwartete Wirkung: Arbeitskopie entfernen, gemeinsame Gitobjekte und Branchref behalten):

- `git worktree remove "F:\neutron-cmb-1a"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-core-i01a"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-core-i01b"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-core-i01c"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-core-i01d"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-core-i01e"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-rel-p00"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-rel-p01"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\neutron-rel-p02"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\accounts-movefix"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\architecture-cleanup"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\audit-dec87d9d"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\audit-w1-inventory"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\audit-w1-movement"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\audit-w2-labels"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\audit-w2-movement"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\audit-w2-render"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\clickgui-restore"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\events-network"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\microsoft-browser-login"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\subagent-client-performance"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\subagent-minecraft-performance"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\subagent-network"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\subagent-rotations"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.
- `git worktree remove "F:\Pulsar-Worktrees\visual-theme-sync"` — erst nach ergänzter Sicherungs-/Ignored-Datenprüfung und exakter Nutzerfreigabe; Branchref bleibt erhalten.

Alle anderen Pfade: KEEP / REVIEW_REQUIRED / PROTECTED; kein rekursives Dateisystemkommando vorbereitet. Evidence-/Sourcesordner benötigen eine eigene vollständige zulässige Archivierung und Pfadfreigabe. Keine Branchrefs löschen, solange Reachability/Recovery nicht separat belegt und genehmigt sind. Keine Relocation nach F:\Neutron-Development; nur eine mögliche spätere Zielstruktur.

Belege: [`LOCAL_WORKTREE_INVENTORY.json`](F:/neutron-rel-int01-v2-evidence/LOCAL_WORKTREE_INVENTORY.json), [`F-root-inventory.json`](F:/neutron-rel-int01-v2-evidence/F-root-inventory.json), [`backup-verification.json`](F:/neutron-rel-int01-v2-evidence/backup-verification.json), [`cleanup-receipt.json`](F:/neutron-rel-int01-v2-evidence/cleanup-receipt.json), [`main-source-consolidation.json`](F:/neutron-rel-int01-v2-evidence/main-source-consolidation.json).
