# CMD_LAUNCHER_GATE — Frontends geprüft, Start NOT_READY

28.10.2026, Europe/Berlin. Neutron-Client / REL-INT01-v2.


Implementiert: tools/neutron-private-playtest/Neutron-Check.cmd, Neutron-Start.cmd, check-private-playtest.ps1 und README.md. Der eng begrenzte read-only Helper ist durch Sonderzeichen-/Parameter-/Ausgabevertragsprüfungen begründet. Er nutzt exakt die vorhandenen Parameter Check, JavaHome, MinecraftHome, JarPath, ExpectedJarSha256, LauncherPath und IsolationRoot; keine erfundenen Runnerflags.

Check ist als Frontend funktionsgeprüft (CMD_CHECK_READY=PASS). Sein Runtimebefund bleibt CHECK_ONLY/BLOCKED und Exit2. Reale Bestandsprüfung: Java8, Assemblyhash, Main/EmbeddedClasses, 1.8.9-Metadaten, Vanilla-JAR, Assets und Bibliotheken PASS; Launcher-, Isolations-, vollständige Native-/Classpath- und Nutzer-/Sessiongates BLOCKED. Der vorhandene OfficialLauncher-Check ist eine historische Runnerbedingung; es wird keine Installation erzwungen und kein alternativer Laufzeitpfad als bewiesen behauptet.

Start ist ein tatsächlich implementierter, getesteter NOT_READY-Einstieg. Er führt ausschließlich den Check aus, gibt dessen nonzero Code weiter und liefert bei unerwartetem Check-Exit0 selbst Exit30. Er enthält keinen Game-Javaaufruf, keine Tokens, Authdateien oder Installationsfunktion. CMD_START_READY=BLOCKED; fail-closed Implementierungsabnahme PASS.

13 synthetische Windows-CMD-Fälle bestehen: normale Check-/Startdelegation, Leerzeichen/Unicode/& in Pfaden und Parametern, fehlender Helper/Runner/Check/PowerShell, ungültiger Hash, kaputtes Runnerformat, unerwartetes Exit0, Fehlerweitergabe und Start-Hartgrenze. Zusätzlich neun echte P01/P02-Negativfälle gegen die neue Assembly (fehlende Vanilla, falsches/fehlendes JDK, JSON-Fehler, Assembly-/Vanilla-Hash, fremder Profilpfad, fehlender Launcher/JAR) sowie Start-ohne-Java, echter lokaler Inventarcheck und realer NOT_READY-Start: alle erwarteten Exit2/BLOCKED. Fixturehashes unverändert, keine fehlerhaften Metadateninhalte oder Pfade geloggt. Quoting/DisableDelayedExpansion/setlocal/pushd und unmittelbare Exitcodeübernahme sind geprüft.

Die Betriebssystem-Modulauswahl gilt nur im eigenen Prozess. Keine setx-/Registry-/Firewall-/Profilwrites. Erwarteter SHA muss aus dem konkret geprüften lokalen Artefakt übernommen werden; kein Hash zählt als Auth-/Entitlementbeweis. Readme erklärt späteren Local-Developer-/Freunde-Vertrag und die gesperrte Fat-JAR-Weitergabe.

Belege: [`cmd-wrapper-tests.json`](F:/neutron-rel-int01-v2-evidence/cmd-wrapper-tests.json), [`cmd-wrapper-initial-failure.json`](F:/neutron-rel-int01-v2-evidence/cmd-wrapper-initial-failure.json), [`cmd-real-preflight-tests.json`](F:/neutron-rel-int01-v2-evidence/cmd-real-preflight-tests.json), [`real-cmd-real-local-inventory.log`](F:/neutron-rel-int01-v2-evidence/real-cmd-real-local-inventory.log). Installation/Spielstart und Zweitrechner bleiben offen.
