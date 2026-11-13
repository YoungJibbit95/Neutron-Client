# Performance-Menü: zweiter Optimierungsschritt

Basis ist Main `5eccfb7b28110731e9d522dfb6e20ca3270b6ec3` vom 13. November 2026.
Vier neue Optionen stehen unter **Client → Performance**. Die drei neuen Schalter
sind standardmäßig aus; Scene Blur Quality steht auf **Full**. Upload-Budgets
behalten Namen, IDs, Standardwerte und Grenzen. Gameplay-, Netzwerk- und
Anticheatlogik gehören nicht zu diesem Schritt.

## Bedienung und Wirkung

Das Performance-Modul muss eingeschaltet sein. Ausschalten stellt die
Standardstrategien, Full und **1 ms / 4 MiB** Upload-Budget wieder her; gespeicherte
Menüwerte bleiben erhalten. Alte Performance-Profile ohne die neuen Felder setzen
diese auf die Standardwerte zurück. Komplett fehlende Module behalten die
vorhandene Partial-Import-Semantik des ConfigManagers.

| Einstellung | Standard | Wirkung und Grenze |
| --- | --- | --- |
| **Fast Particle Cleanup** | Aus | Wiederverwendbare Todesliste und bei größeren Mengen eine begrenzte Hashtabelle ohne Map-Knoten. Tickreihenfolge, Alphawechsel, Spawns, 4.000-Partikel-Grenze und bisherige 20-ms-Budgetregel bleiben erhalten. |
| **Fast Visibility Graph** | Aus | Primitive Floodfill-Queue und Flächen-Bitmasken für Chunksektionen. Die konservative Abkürzung bei weniger als 256 opaken Zellen bleibt erhalten. |
| **Fast Gaussian** | Aus | Benachbarte Gaussian-Gewichte durch einen linear gefilterten Texturzugriff zusammenfassen. Wirkt in geeigneten Scene-Blur-Pässen; Halo-Pässe behalten ihren Shader. |
| **Scene Blur Quality** | Full | Half/Quarter verkleinern die weiche Szenentextur und deren Scratch. Clean-Snapshot, Masken, Glow, Text und UI-Geometrie behalten volle Auflösung. Die weichgezeichnete Szene verliert ausdrücklich Details. |

Bei geraden Größen verarbeitet ein Blur-Pass mit Half ein Viertel, mit Quarter ein
Sechzehntel der Pixel. Ungerade Größen werden aufgerundet; Radius und
Kawase-Abstände verwenden die tatsächlichen Achsenverhältnisse. Das sind
**keine FPS- oder Gesamtzeitfaktoren**: Snapshot und Komposition bleiben bestehen.
Fast Gaussian ändert eine vorhandene Kawase-Methodenwahl nicht; Scene Blur Quality
wirkt auch auf den Kawase-Szenenpfad.

Bei Gaussian-Radius 11 sinken die Texturzugriffe je eindimensionalem Pass von 23
auf 13. Das diskrete Kernel bleibt erhalten, die endliche Filter-/Rechenpräzision
kann RGB geringfügig verändern. Der **vor den nativen Läufen** festgelegte Vertrag
lautet: Alpha exakt, maximal **2/255 RGB-Abweichung pro Kanal nach zwei Pässen**.
Die bisherigen exakten Default-RGBA-Vergleiche bleiben unverändert. Das zusätzliche
Glass-Endbild-Gate verwendet dieselbe feste Grenze in seinen zwei Szenen; es ist
ein empirischer End-to-End-Test, kein universeller mathematischer Fehlerbeweis.

Pairing setzt Originalquellen, gleich große Ein-/Ausgabe, lineare Filterung,
`CLAMP_TO_EDGE`, Basis-Mip-Level 0, keinen Texturrand und keine zusätzliche
anisotrope Filterung voraus. Andere Sampler und die erste Full→Half/Quarter-Stufe
verwenden den bisherigen Gaussian-Pfad; fremde Texturparameter werden nicht
geändert. Überschriebene Scene-Shader bewirken außerdem Full als Auflösungsfallback.
Die Zähler `pairedGaussianPasses`, `pairedGaussianFallbacks` und `sceneBlurPixels`
belegen die tatsächlich ausgeführte Arbeit.

## CPU-Messungen

[Alle 16 JVM-Berichte und Rohdaten](evidence/performance-options-2026-11-13/core-local.json)
sind einschließlich Quellhashes, Umgebung und Baseline-Build-Provenienz archiviert.
Temurin 8u482, 9 verfügbare Prozessoren, identische Heapoptionen. Beide Strategien
verwenden denselben aktuellen Build in getrennten JVMs, einschließlich des
gemeinsamen Sichtbarkeits-Bitfixes. Das ist kein Vergleich vollständiger
historischer Clients und kein FPS-Nachweis.

Je Benchmark vier Paare **Legacy→Fast / Fast→Legacy / Legacy→Fast / Fast→Legacy**,
1.000 Warmup-Operationen und 40 Batches pro Szene. Partikel-Batches enthalten 10,
Graph-Batches 20 Operationen. Konstruktion, Belegung, Eingabe-Reset,
Ergebnisprüfung und I/O liegen außerhalb der Messung. Partikelprobe: Sammlung,
Membership, Entfernen und Scratch-Freigabe, ohne Ticks/Rendering. Graphprobe:
`computeVisibility`, ohne Chunkaufbau/Meshing/Uploads.

Zeiten sind der Median der vier JVM-p50-Werte in **µs pro Operation**. Das Δ ist
der Median der vier gepaarten Fast/Legacy-Verhältnisse, nicht der Quotient der
Tabellenmediane. p95/p99 in der Rohdatei beschreiben **Batchmittel**, keine
einzelnen Tick- oder Frame-Latenzen. Es werden keine Ausreißer entfernt.

| Szene | Legacy µs | Fast µs | Gepaarter Zeit-Δ-Median | Langsamere Fast-Paare | Heap-Bytes/Operation Legacy → Fast |
| --- | ---: | ---: | ---: | ---: | ---: |
| 4.000 Partikel, alle leben | 1,114 | 0,052 | −95,38 % | 0/4 | 24 → 0 |
| 4.000 Partikel, 8 abgelaufen | 17,443 | 11,709 | −32,87 % | 0/4 | 80 → 0 |
| 4.000 Partikel, Hälfte abgelaufen | 2.046,587 | 10,471 | −99,48 % | 0/4 | 33.568 → 32 |
| 4.000 Partikel, alle abgelaufen | 2.477,963 | 15,069 | −99,39 % | 0/4 | 50.240 → 32 |
| Sichtbarkeit: leer | 0,035 | 0,033 | −1,63 % | 2/4 | 24 → 24 |
| Sichtbarkeit: vollständig opak | 0,028 | 0,028 | −3,48 % | 1/4 | 24 → 24 |
| Sichtbarkeit: Boden | 48,755 | 18,197 | −62,20 % | 0/4 | 2.144 → 24 |
| Sichtbarkeit: halb opak | 26,148 | 9,701 | −62,25 % | 0/4 | 2.144 → 24 |
| Sichtbarkeit: geschlossener Raum | 1,048 | 1,038 | −0,96 % | 0/4 | 24 → 24 |
| Sichtbarkeit: Ecktunnel | 1,808 | 1,427 | −21,29 % | 0/4 | 2.144 → 24 |
| Sichtbarkeit: Schachbrett | 109,550 | 5,601 | −94,88 % | 0/4 | 1.433.144 → 24 |
| Sichtbarkeit: deterministisch 35 % opak | 32,740 | 15,335 | −54,66 % | 0/4 | 8.504 → 24 |

Die großen Gewinne passen zur entfernten quadratischen Mitgliedschaftssuche und
zu Floodfills mit vielen temporären Queues/Flächensets. Triviale Graphpfade bleiben
praktisch unverändert. Das Schachbrett ist eine Stressszene, keine typische Welt.
`IntegerCache` deckt bereits alle 4.096 Zellindizes ab; es wird keine vermeintliche
neue Integer-Allokation pro Zelle als Ersparnis gezählt.

Wiederverwendung hält Speicher: gemessen **49.344 Byte Array-Payload pro
Partikel-Owner** bei komprimierten Referenzen, ohne Objekt-/Arrayheader; alle
Partikelreferenzen werden nach jedem Durchlauf gelöscht. Die Graphqueue hält
lazy **16.384 Byte int-Payload pro nutzendem Thread**, ohne Header, nicht pro Chunk.

Der zusätzliche CI-Lauf bestätigt die großen Gewinne bei vielen abgelaufenen
Partikeln und nichttrivialen Graphen. Er enthält auch kleine Nachteile: der
vollständig opake Graph hat **+0,98 %**, der geschlossene Raum **+0,15 %** gepaarten
p50-Zeitmedian. Bei den Batch-Tails liegen der leere Graph p99 bei **+2,64 %**, der
geschlossene Raum p95 bei **+8,50 %** und der Ecktunnel p95 bei **+6,85 %**. Diese
Fälle und langsameren Einzelpaare bleiben in der CI-Evidenz erhalten.

## Native Render-Messungen

Der [korrigierte Performance-Lauf](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43398942063)
prüft den Code-Stand `cc92dc10` über den GitHub-Merge `bc3fd69f`; beide haben
denselben Tree `5066ecdb`. [Die vollständige CI-Evidenz](evidence/performance-options-2026-11-13/ci-verified.json)
enthält Default-, CPU- und Scene-Berichte samt Rohserien, Provenienz und Artefakthashes.
Auch die zusätzlichen 16 CPU-Mess-JVMs bestanden; ihre Daten ergänzen den oben
ausgewiesenen lokalen Lauf und ersetzen ihn nicht.

Die Scene-Probe verwendet echte `ArrayListSurface`-/Glass-Verbraucher mit
verschachtelten Panels, UI-Radius 11 und GUI-Skalierung 2. Je Option werden vier
frische JVM-Paare in wechselnder Reihenfolge ausgeführt: insgesamt **24 JVMs**,
30 Warmup- und 40 Messframes pro Szene. Half/Quarter werden jeweils mit
ausgeschaltetem Fast Gaussian gegen Full verglichen. Der Test läuft auf
**Mesa 25.2.8 / llvmpipe LLVM 20.1.2**, Java 8u482, mit zwei Software-Renderthreads.

Die folgende Zeit umfasst die Szenen-/Materialarbeit bis einschließlich `glFinish`.
Die Hintergrundvorbereitung und Bildprüfung liegen außerhalb des Zeitfensters.
Absolute Zeiten sind Mediane der vier JVM-p50-Werte; Δ ist wieder der Median der
gepaarten Verhältnisse. Submission, p95/p99, Kaltstart und Heapwerte bleiben in den
Rohdaten getrennt erhalten. Das sind Software-OpenGL-Messungen dieser beiden
Szenen; daraus folgt kein Hardware-GPU- oder Spiel-FPS-Faktor.

| Option | Szene | Full ms | Option ms | Gepaarter Zeit-Δ-Median | Langsamere Optionspaare |
| --- | --- | ---: | ---: | ---: | ---: |
| Fast Gaussian | 960×640 | 151,656 | 80,178 | −47,11 % | 0/4 |
| Fast Gaussian | 853×481 | 103,429 | 55,585 | −46,26 % | 0/4 |
| Half | 960×640 | 151,167 | 26,151 | −82,75 % | 0/4 |
| Half | 853×481 | 103,442 | 19,237 | −81,41 % | 0/4 |
| Quarter | 960×640 | 151,136 | 9,202 | −93,91 % | 0/4 |
| Quarter | 853×481 | 103,385 | 7,976 | −92,28 % | 0/4 |

Alle 48 Scene-Berichte haben im gemessenen warmen Abschnitt **null neue
Framebuffers und null Shaderkompilierungen**. Die p50-Heap-Allokation bleibt bei
1.760 Byte pro gemessenem Aufruf in allen Modi. Pass-/Pixelzähler belegen die
ausgeführten Varianten. Die gespeicherten Full-/Fast-Gaussian-Endbilder dieser
zwei Messszenen sind exakt gleich; die breitere native Vertragssuite behält ihre
feste RGB≤2-/Alpha-exakt-Grenze. Half/Quarter reduzieren die Auflösung der weichen
Szene und können Details verändern. Ihre Zeitgewinne dürfen nicht mit gleicher
Bildqualität gleichgesetzt oder zu den Fast-Gaussian-Gewinnen addiert werden.

### Standardpfade und negative Ergebnisse

Der separate Default-Vergleich gegen Main `5eccfb7b` bestand vier frische JVM-Paare,
60 Warmup- und 90 Messframes je Szene sowie **64 exakte RGBA-Vergleiche**. Seine
gemischten Gaussian-Zeitreihen wechseln zwischen Radius 7 und 11. Der erste Lauf
und der korrigierte Lauf zeigen folgende gepaarte Completed-p50-Mediane:

| Default-Gaussian-Szene | Erster Lauf | Langsamere Paare | Korrigierter Lauf | Langsamere Paare |
| --- | ---: | ---: | ---: | ---: |
| 960×640, gemischte Radien | +9,07 % | 3/4 | +5,21 % | 4/4 |
| 854×480, gemischte Radien | +7,86 % | 4/4 | −1,66 % | 1/4 |

Der Nachteil bei 960×640 tritt erneut auf und bleibt als negativer Messbefund
erhalten. Bei getrennt ausgewerteten Radiusphasen sind die aktuellen Mediane
kleiner: 960×640 Radius 7 **+0,22 %** (2/4 langsamer), Radius 11 **+0,34 %** (3/4);
854×480 Radius 7 **+0,25 %** (4/4), Radius 11 **+0,13 %** (3/4). Die getrennten
Tails bei 960×640 bleiben jedoch ungünstiger: Radius 7 p95 **+2,63 %**, p99
**+5,21 %**, Radius 11 p95 **+2,69 %**, jeweils 4/4 Paare langsamer. Der gemischte
p50 entspricht hier dem Radius-7-p99. Die Phasentrennung entkräftet den
Tail-Nachteil somit nicht. Ein kostenfreier Default-Pfad ist nicht nachgewiesen;
die Ursache des Nachteils ist durch diese Messungen nicht geklärt. Alle acht
Default-Szenen, sämtliche Einzelpaare und die anfänglichen Nachteile stehen in
den beiden CI-Rohdateien.

## Korrektheit und Aufnahme

`SetVisibility` verschob bisher einen 32-Bit-Integer vor der Umwandlung in `long`.
Dadurch waren die oberen Einträge der 6×6-Matrix nicht unabhängig. `1L << index`
stellt alle 36 Paare korrekt dar. Die Korrektur gilt immer, wird mit einem
unabhängigen `boolean[6][6]`-Orakel geprüft und ist kein Timinggewinn.

CPU-Tests prüfen alle Paare/Flächenmasken, 140 deterministische Zufallsszenen,
255/256-Grenze und mehrere Worker. Buffer- und Renderer-Tests prüfen finale mutierte
Entity-IDs, Kollisionen/Duplikate, Reihenfolge, Alphawechsel, Spawns, Reentranz,
Ausnahmefreigabe und die bisherige Budget-Eviction.

Native Grafikprüfungen decken ungerade Größen, Radien 1/2/7/11/31/127,
Sampler-Fallback, Materialpfad, Kawase, Masken, Ressourcenüberschreibungen,
Resize, Freigabe und Zustandsrestore ab. Während eines aktiven eigenen oder
geborgten Soft-Scene-Scopes wird ein Qualitätswechsel aufgeschoben; anschließend
werden abhängige Captures vor dem Löschen invalidiert. Der vorhandene Sonderfall
einer rekursiven Neuberechnung desselben Owners mit anderem Radius behält seine
bisherige Komposition; es gibt keine allgemeine Reentranz-Neugestaltung.

Optionen und Budgets werden atomar als unveränderlicher Snapshot veröffentlicht.
Tatsächliche Änderungen erhöhen eine monotone Revision, identisches Apply nicht.
`.perf` exportiert die effektive Konfiguration und erkennt auch Ändern und
Zurückstellen zwischen zwei Frames. Settings-Callbacks bleiben GL-frei; die
Aufnahme reagiert auf ihrem Renderthread. Offene GPU-Paare über einen Wechsel
werden verworfen, auch bei spätem `endGpu()` nach `stop()`. Vollständige Frame-/CPU-
Daten samt Stop-Snapshot bleiben erhalten; der GPU-Rest hat einen eigenen
Abbruchgrund und `abandoned`-Zähler.

Für einen Spielvergleich Welt, Position, Blickrichtung, Auflösung, Renderdistanz,
Effekte und Framelimit festhalten. Nach Warmup beispielsweise `.perf start 30`,
nach Ende `.perf status` und `.perf export legacy` ausführen. Eine Option ändern,
wieder aufwärmen und als `fast` exportieren. Bei gleicher Bildqualität bleibt
Scene Blur Quality auf Full; Half/Quarter sind ein eigener Qualitätsvergleich.

## Prüfstand

Java-8-Kompilation und 48 gezielte JUnit-Tests bestanden ohne Fehler/Skips.
Alle 16 CPU-Mess-JVMs bestanden Signatur-, Vollständigkeits- und Umgebungsprüfungen.
33 Tests der Performance-Auswertung bestanden ebenfalls. Nach Wiederherstellung
des Kawase-Referenzeinstiegs bestanden neun weitere fokussierte Java-Tests.

Der erste [native UI-Lauf](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43397468000)
auf Head `5c36b1fc` (CI-Merge `c66be95b`) bestand alle 17 Suiten und alle 38 neuen
Scene-Blur-Records. Die rohe gepaarte Gaussian-Ausgabe wich dabei maximal 1/255
pro RGB-Kanal ab, Alpha blieb exakt. Der separate
[erste Performance-Lauf](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43397467966)
bestand alle 64 exakten Default-RGBA-Vergleiche, scheiterte anschließend aber am
fehlenden reflektiven Dreiparameter-Kawase-Helper. Der symmetrische Adapter wurde
in `cc92dc10` wiederhergestellt; keine Pixel- oder Zustandsassertion wurde geändert.
[Die initiale Evidenz](evidence/performance-options-2026-11-13/ci-initial.json)
behält den Fehlerstatus, sämtliche abgeschlossenen Messwerte und Nachteile.

Auf dem korrigierten Code-Stand `cc92dc10` bestanden der
[native UI-Lauf](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43398941795)
erneut **17 Suiten und alle 38 Scene-Blur-Records** und der oben verlinkte
Performance-Lauf sämtliche Default-, CPU- und Scene-Gates. Die vollständige
[Windows-Regression](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43398941680)
bestand **424 Suiten mit 2.617 Tests, null Failures, null Errors und drei
vorgesehenen nativen Skips**, einschließlich sämtlicher LagRange-Suiten.
Der [Paketlauf desselben Stands](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43398942123)
bestand ebenfalls die vollständigen 2.617 Java-Tests mit genau diesen drei Skips,
**89 Tooling-Tests** und **sechs Prüfungen der gebauten JAR in einem isolierten
Java-8-Prozess**. CodeQL und die Repository-Prüfungen waren ebenfalls grün.

Die lokale Linux-Vollsuite lief vollständig: 2.617 Tests, acht Failures,
20 Errors, drei native Skips. Alle 20 Startfehler betreffen bestehende
Windows-`java.exe`-Aufrufe. Der Schriftfehler tritt auch mit der unveränderten
Baseline-JAR auf. Sieben LagRange-Assertions scheiterten nur in der Gesamtreihenfolge;
separate Baseline-/Current-Kontrollen bestehen jeweils 12/12, alle acht neuen
Testklassen unmittelbar vor diesen Klassen bestehen 53/53. Die Ursache dieser
sieben lokalen Fehler ist ungeklärt. Der vollständige Windows-CI-Lauf und die
Paketprüfung bilden die maßgebliche Gesamtabnahme; ein grüner Windows-Lauf erklärt
die lokale Reihenfolgeabhängigkeit nicht nachträglich.

Lokal steht kein Display-/GL-Kontext bereit; die nativen Daten stammen aus
Software-OpenGL unter Mesa/llvmpipe. Ein Hardware-GPU-, Livewelt- oder allgemeiner
FPS-Nachweis wird daraus nicht abgeleitet. Die Checks des abschließenden
Dokumentations-/Evidenz-Commits und der Merge-Stand werden im zugehörigen
[PR 191](https://github.com/YoungJibbit95/Neutron-Client/pull/191) ausgewiesen.

Einstiegspunkte: [CPU-Runner](../.github/scripts/check_core_performance.py),
[Scene-Blur-Runner](../.github/scripts/check_scene_blur_performance.py),
[native UI-Suiten](../.github/scripts/run-ui-render-checks.sh) und der unveränderte
[exakte Baseline-Runner](../.github/scripts/check_render_performance.py).

Technische Primärquellen zur Samplepaarung:
[OpenGL 2.1, Abschnitt 3.8](https://registry.khronos.org/OpenGL/specs/gl/glspec21.pdf)
und [GPU Gems 3, Kapitel 28](https://developer.nvidia.com/gpugems/gpugems3/part-iv-image-effects/chapter-28-practical-post-process-depth-field).
Die Implementierung verwendet die bestehenden Projektgewichte und eigene
Hilfsklassen; es wurde kein fremder Renderer übernommen.
