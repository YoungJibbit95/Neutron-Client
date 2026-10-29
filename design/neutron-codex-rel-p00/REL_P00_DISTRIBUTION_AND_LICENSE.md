# REL-P00 — Distribution und Rechte

Stand: 28.10.2026. **LOCAL_ONLY / DISTRIBUTION_BLOCKED.**
Kein installierbares Testerpaket, keine Spiel-JAR und keine OptiFine-JAR werden
zur Weitergabe bereitgestellt. Die erzeugten JARs bleiben lokale Buildausgaben.

## Entscheidung aus tatsächlichem Inhalt

Alle drei JARs enthalten jeweils **2510 `net/minecraft`-Klassen**, **402
`net/optifine`-Klassen** und **4240 Einträge unter `assets/minecraft/`**.
Der Quellstand nennt ausdrücklich `OptiFine_1.8.9_HD_U_L5`.
Einige Asseteinträge sind projektspezifisch; ihre gemeinsame Ablage ist kein
Nachweis freier Rechte für sämtliche Texturen, Schriften und sonstigen Dateien.
Die Ausgaben sind keine nachgewiesen eigenständigen Mod-Komponenten.

Die am 28.10.2026 abgerufene offizielle Minecraft-EULA unterscheidet originale
Mods von kombinierten modifizierten Spielversionen. Sie gestattet nicht die
Weitergabe einer vollständigen modifizierten Spielversion. Eine private
Testgruppe oder ein anderer Dateiname beseitigt diese Grenze nicht.
Quelle: [Minecraft EULA, insbesondere „USING mods“](https://www.minecraft.net/en-us/eula).

Die offizielle OptiFine-Copyrightseite erklärt die Rechte des Autors und
beschränkt Reproduktion bzw. öffentliche Distribution; eine Erlaubnis für
diesen veränderten integrierten Bestand wurde nicht vorgelegt.
Damit besteht hier keine belastbare Freigabe für seine Weitergabe.
Quelle: [OptiFine Copyright](https://optifine.net/copyright).

## Rechte- und Herkunftsmatrix

| Bestandteil | Befund | Distributionsentscheidung |
|---|---|---|
| Projektspezifischer Java-/VIS-Code | Hauptsächlich `me.youngjibbit`; Root-LICENSE enthält GPLv3, POM zugleich „All Rights Reserved“ | Herkunft/Rechte der konkreten Mod-Komponente klären; kein pauschaler Freibrief |
| Minecraft-Klassen/Originalassets | Im Build enthalten; direkte Sourceintegration | Vollständige modifizierte Spielausgaben nicht weitergeben |
| OptiFine L5 | Im Source und allen JARs integriert | Keine gesonderte Erlaubnis belegt; Distribution blockiert |
| Maven-Drittbibliotheken | 45 direkte Deklarationen inkl. JUnit-Testscope, 63 aufgelöste Runtimeartefakte | Lokaler Bestand geprüft; keine vollständige transitive Lizenz-/Notice-Freigabe |
| Fonts/sonstige mitgeführte Ressourcen | 4149 Maven-Quellressourcen; differenzierte Rechte nicht vollständig nachgewiesen | Kein gemeinsames Assetpaket exportieren |
| Fremde Shaderpacks | Kein neues Shaderpack bezogen oder gebündelt | Keine Weitergabe |
| Konten/Profile/Welten | Nicht aus fremden Worktrees kopiert; keine Account-/Launcherprofildateien gelesen | Kein Teil eines Exports |
| REL-Berichte und Review-Patch | Eigene Dokumentation plus ausschließlich bestätigter VIS-Diff gegen CMB-Basis | Technische Reviewübergabe, kein installierbarer Spielclient |

Die lokale POM-Inventur findet bei 18 von 45 direkten Deklarationen unmittelbar
genannte Lizenzen, unter anderem Apache-2.0, MIT, BSD, JNA-Duallizenz und
Vecmath GPLv2 mit Classpath-Ausnahme. Fehlende unmittelbare Lizenzfelder sind
kein Beweis fehlender Rechte: Eltern-POMs und vollständige transitive
Pflichten wurden nicht abschließend geklärt. Zusammengeführte LICENSE-/NOTICE-
Einträge im JAR und Shade-Überlappungen ersetzen diesen Nachweis nicht.
Die Root-GPL kann keine Rechte an Mojang-/OptiFine-Bestandteilen erteilen.

## Zulässiger nächster Ansatz, noch nicht geliefert

Benötigt wird eine eigenständig lizenzierte Loader-/Transformationskomponente,
die ausschließlich gegen die jeweils eigene rechtmäßige 1.8.9-Installation
arbeitet. Direkte Änderungen an Vanilla-/OptiFine-Quellen lassen sich nicht
durch bloßes Wegfiltern ihrer Klassen in ein funktionierendes Mod-JAR verwandeln.
Mapping-/Loadervertrag, Transformationsherkunft, OptiFine-Rechte und Ressourcen-
herkunft müssen zuerst geklärt sein. Kein ungeprüfter Quellen-/Binärpatch mit
proprietären Spielbestandteilen wird als Alternative verteilt.

Erst nach dieser Trennung und realen Client-/Launcher-/Servergates können
idempotente Check/Install/Uninstall-Schritte, Backup, eigene Versionsmetadaten,
DE/EN-Anleitung und Zweitrechnerprüfung qualifiziert werden. Keine Mirrors,
Authumgehung, kopierten Tokens oder stillen `launcher_profiles.json`-Writes.
REL-P00 endet mit der Blockerübergabe; es gibt keinen funktionsbehauptenden Installer.

Belege: [JAR-Inhalt und Notices](F:/neutron-rel-p00-evidence-20261028/jar-audit.json),
[direkte Lizenzdeklarationen](F:/neutron-rel-p00-evidence-20261028/direct-dependency-license-inventory.json),
[Runtimeabhängigkeiten](F:/neutron-rel-p00-evidence-20261028/runtime-dependency-tree.txt),
[LICENSE](../../LICENSE), [Runtime-Gates](REL_P00_LOCAL_LAUNCHER_GATE.md).
