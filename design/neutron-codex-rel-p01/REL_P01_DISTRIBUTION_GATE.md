# REL-P01 — Distributionsgate

28.10.2026, Europe/Berlin. **LOCAL_ONLY / DISTRIBUTION_BLOCKED**.
Keine der drei JARs ist ein weitergebbares Mod-only-Artefakt.

Jede aktuelle Ausgabe enthält **2510 net/minecraft-Klassen**, **402 net/optifine-
Klassen** und **4240 Einträge unter assets/minecraft** (einschließlich Verzeichnissen).
Die Sourcekennung bleibt OptiFine_1.8.9_HD_U_L5. Keine Fonts, Shaderpacks,
Spielklassen oder Assets wurden für eine Weitergabe ausgesondert oder kopiert.
Alle Binärdateien verbleiben ausschließlich als lokale Build-/Diagnoseausgaben.

Die am 28.10.2026 erneut gelesene [Minecraft-EULA](https://www.minecraft.net/en-us/eula)
unterscheidet eigenständige Mods von kombinierten modifizierten Spielversionen und
gestattet deren Weitergabe nicht. Die [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
verlangen entsprechend die Beschränkung auf den Mod selbst.
Die [OptiFine-Copyrightbedingungen](https://optifine.net/copyright) enthalten
zusätzliche Reproduktions- und öffentliche Distributionsbeschränkungen; eine
Erlaubnis für den hier integrierten veränderten Bestand ist nicht belegt.
Die lokale GPLv3-Datei und die abweichende All-Rights-Reserved-POM-Beschreibung
erteilen keine Rechte an fremden Mojang-/OptiFine-Komponenten. Dies ist die
konservative Auftragsentscheidung, keine pauschale Lizenzfreigabe.

| Bestand | Bewertung |
|---|---|
| Minecraft/OptiFine und Originalassets | Distribution gesperrt |
| Fonts, Sounds, Shader-/Ressourcenherkunft | Keine vollständige Rechte-/Noticefreigabe |
| Mavenbibliotheken einschließlich integrierter Notices | Lokale Hash-/Klassenaudits; keine transitive Distributionsfreigabe |
| Eigene neue Prüfungen/Helfer/Berichte | Reviewumfang; kein lauffähiges Testerinstallationspaket |
| Accounts, Tokens, Profile, Welten | Nicht gelesen/kopiert/committed; kein Exportbestand |

Ein möglicher späterer Auftrag muss eine eigenständig lizenzierte Mod-/Loader-/
Transformationskomponente gegen die jeweils eigene legitime Installation definieren,
inklusive Mapping-/Runtimevertrag und geklärter OptiFine-/Ressourcenrechte.
Bloßes Wegfiltern von Klassen aus der MCP-JAR beweist keinen funktionsfähigen Mod.
Dieser Folgetrack wurde nicht begonnen. Kein Installer, Download für Kollegen,
Zweit-PC-Paket, Release-Tag oder Veröffentlichungszähler. Auch ein späterer lokaler
Fenstererfolg würde die Sperre nicht aufheben.

Beleg: [final-jar-audit.json](F:/neutron-rel-p01-evidence/final-jar-audit.json); lokale Dateien LICENSE und pom.xml unverändert
bis auf die dokumentierte Assembly-Main-Class-Konfiguration.
Der externe Review-Patch enthält nur die neun freigegebenen Quelldateien/Reports,
keine Binär-JAR, keine übernommenen proprietären Spielklassen und keine Assets.
**STOPP nach REL-P01.**
