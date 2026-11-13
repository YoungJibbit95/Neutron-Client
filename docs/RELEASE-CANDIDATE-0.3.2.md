# Neutron 0.3.2 — Release-Kandidat

**Status:** Release-Vorbereitung, **noch nicht als Stable veröffentlicht**.  
**Minecraft:** 1.8.9 · **Java:** 8 x64 · **geplanter Tag:** `v0.3.2`  
**Basis:** `main` nach PR #195, Commit `d14c9f735f3538ee9aba3a58e33b11237398a73c`.  
**Vorheriger Release:** `v0.3.1` (`5eccfb7b28110731e9d522dfb6e20ca3270b6ec3`).

## Änderungen seit 0.3.1

- **Stabilität und Modulzustände:** Backtrack, Tickbase und LagRange brechen bei Schutzereignissen ihren aktuellen Vorgang kontrolliert ab, ohne den gewählten Aktivierungsstatus zu ändern. Paket-, Welt-, Verbindungs- und Zeitbudgetzustände werden bereinigt (PR #195).
- **Aura / Adaptive:** Die Demise-basierten Smooth Rotations stehen als eigene Rotationsengine bereit. Die kontinuierliche Neutron-Noise-Hitpoint-Bewegung innerhalb der Hitbox bleibt unabhängig wählbar; ältere Konfigurationen behalten ihren bisherigen Engine-Standard (PR #195).
- **Pre Aim:** Persistente Regler **Pre Aim Range** (1–9 Blöcke, Standard 3) und **Pre Aim Prediction** (0–3 Ticks, Standard 0). Die Pre-Aim-Zielaufnahme erweitert keine Angriffsreichweite (PR #195).
- **Konfigurations- und Release-Stabilität:** Toleranter Umgang mit älteren Konfigurationswerten, Diagnoseverbesserungen und die in PR #194 integrierten Release-Korrekturen.
- **Visuals und HUD:** GlowESP-Szenen, Scoreboard/Hotbar, TargetESP und UI-/Render-Stabilität wurden im Release-Vorbereitungslauf erweitert beziehungsweise korrigiert (PR #194).
- **BedBreaker, BedESP und FakeLag:** Integration und Aktualisierungen aus PR #193.
- **Zusätzliche Performance- und Renderprüfungen:** Kontrollierte Native-UI-, Pixel- und CPU-/Scene-Blur-Regressionsprüfungen gehören zum validierten Quellstand.

Ausführliche Änderungen: [Vergleich v0.3.1 bis zum geprüften Basiscommit](https://github.com/YoungJibbit95/Neutron-Client/compare/v0.3.1...d14c9f735f3538ee9aba3a58e33b11237398a73c).

## Automatische Prüfung, bereits für den Basisstand abgeschlossen

- [PR #195, Windows Java 8](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43428474402): **2.817 Tests**, **0 Failures**, **0 Errors**, 3 bestehende opt-in-Skips.
- [PR #195, vollständiges Bundle](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43428474316): Build, Java-8-Tests und **6 isolierte Prüfungen des gepackten JARs** erfolgreich.
- [Repository-/Dependency-Policy](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43428474295), [CodeQL](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43428474291), [Native UI](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43428474278) und [Native Rendering](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43428474257) erfolgreich.
- **Wichtig:** Diese Nachweise gelten für den geprüften Basisquellstand. Der Versionsänderungs-PR und das für 0.3.2 neu erstellte Release-Bundle müssen auf ihrer endgültigen Revision erneut die anwendbaren Checks bestehen.

## Noch ausstehende Freigabe für die Bezeichnung „Stable“

- [ ] Der finale Versionsänderungs-PR hat alle Pflichtchecks bestanden und wurde regulär nach `main` gemergt; endgültigen Quell-SHA dokumentieren.
- [ ] Ein Release-bundle-Workflow wurde **explizit auf `main` mit Version `v0.3.2` und `prepare_release=true`** ausgeführt. Der Workflow erzeugt zuerst einen **Draft** (keinen veröffentlichten Release).
- [ ] Version, SHA, Launcher-JSON, JAR, ZIP und SHA256SUMS im Draft gegen `main` geprüft. Die installierbare Client-Version muss **0.3.2** anzeigen.
- [ ] Vollständiges Paket im offiziellen Minecraft Launcher mit Java 8 x64 und getrenntem Spielverzeichnis gestartet; Assets, Natives, Grafik, GUI-Eingabe, Weltbeitritt/-wechsel, Trennung, Speichern/Laden sowie Wiederstart geprüft.
- [ ] Microsoft-/Account-Login und vorgesehene authentifizierte Sitzung geprüft; bestehende Konfigurationen einschließlich Alpha-/0.3.0-Import in einem realen Launcherlauf getestet.
- [ ] Änderungen aus PR #193–#195 in eigener kontrollierter Spiel-/Serverumgebung geprüft; insbesondere Schutzabbruch, Aura-Zielwechsel/Rotation, Noise, Pre Aim, BedBreaker/NScaffold und UI.
- [ ] Komponentenherkunft und Verteilungsrechte einschließlich der Demise-Referenz erneut freigegeben.
- [ ] Danach Draft prüfen, `v0.3.2` ohne Prerelease markieren, **Latest** setzen und ausdrücklich veröffentlichen. Keinen bestehenden veröffentlichten Release überschreiben.

**NOT_RUN:** Die genannten interaktiven Launcher-, Konto- und Server-Abnahmen können in dieser Arbeitsumgebung nicht durchgeführt werden. Sie dürfen nicht als bestanden bezeichnet werden.

## Installation nach Veröffentlichung

Neutron-Launcher: verfügbare Releases aktualisieren und **0.3.2** auswählen.

Offizieller Minecraft Launcher: `Neutron-0.3.2-bundle.zip` entpacken, den kompletten Ordner `Neutron-0.3.2` unter `.minecraft/versions` ablegen, passende Installation anlegen und **Java 8 x64** verwenden. Das Paket gehört nicht in `mods`.

Diese Anleitung beschreibt den geplanten Release; die Dateien sind vor Draft-Erzeugung und Veröffentlichung noch **nicht** als herunterladbarer Stable-Release verfügbar.
