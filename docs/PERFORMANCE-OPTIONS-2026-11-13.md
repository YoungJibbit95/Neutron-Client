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
Der vollständige Java-Lauf und die PR-CI werden nach Abschluss hier ergänzt.
Lokal steht kein Display-/GL-Kontext bereit; exakte Baseline-RGBA-Vergleiche und
neue native Verträge laufen deshalb in CI. Ein Hardware-GPU-, Livewelt- oder
allgemeiner FPS-Nachweis wird daraus nicht abgeleitet.

Einstiegspunkte: [CPU-Runner](../.github/scripts/check_core_performance.py),
[Scene-Blur-Runner](../.github/scripts/check_scene_blur_performance.py),
[native UI-Suiten](../.github/scripts/run-ui-render-checks.sh) und der unveränderte
[exakte Baseline-Runner](../.github/scripts/check_render_performance.py).

Technische Primärquellen zur Samplepaarung:
[OpenGL 2.1, Abschnitt 3.8](https://registry.khronos.org/OpenGL/specs/gl/glspec21.pdf)
und [GPU Gems 3, Kapitel 28](https://developer.nvidia.com/gpugems/gpugems3/part-iv-image-effects/chapter-28-practical-post-process-depth-field).
Die Implementierung verwendet die bestehenden Projektgewichte und eigene
Hilfsklassen; es wurde kein fremder Renderer übernommen.
