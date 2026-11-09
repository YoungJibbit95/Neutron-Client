# Neutron 2.0 — AC-R01 Testprotokoll

Revision 1.0 · 28.10.2026. Geplante lokale/ausdrücklich autorisierte Serverversuche. In AC-R01 wurden keine Tests ausgeführt oder Server kontaktiert. IDs/Evidenz: [Register](AC_RESEARCH_REGISTER.md). Dieses Protokoll implementiert keine Instrumentierung.

## Aufnahmebedingungen

Vor einem Run Owner und Genehmigung des Testservers, Client-Commit und Dirty-Diff-Hash, AC-JAR-Version/Hash, Server-JAR/Java, Protokollversion, Proxy/ViaVersion/weitere Plugins mit Version, vollständige relevante Konfiguration und deren Hash, aktivierte Checks/Schwellen/Mitigation, Cloud-Status, Weltfixture/Seed, Modulsettings und Sensitivity sichern. Unbekannte Felder ausdrücklich unbekannt; keine ungeprüfte Version als aktuell einsetzen.

Intave, Grim und Polar auf getrennten Serverinstanzen oder getrennten, dokumentierten Runs testen. Mehrere aktive Anticheats nur in einem ausdrücklich eigenen späteren Interaktionstest. Bei Grim experimentelle Checks ausdrücklich an/aus notieren; bei Polar zuerst T-08; für NCP erst Commit/JAR und Config pinnen. Kein fremder Server ohne Genehmigung.

Zwei Kontrollen: unveränderter Vanilla-1.8.9-Client sowie Neutron mit abgeschalteten Modulen. So wird ein Core-/Hook-Unterschied nicht fälschlich einem Modul zugerechnet. Danach genau ein Modul, erst dann je eine Kombination: Aura+Scaffold, Aura+Backtrack, Aura+LagRange, Tickbase+Aura und Scaffold+Timer-Writer. Mit erfolgreicher Einzelprüfung beginnen; alle aktiven Module vollständig erfassen.

## Run-Datensatz und Ereignisse

| Gruppe | Pflichtfelder |
|---|---|
| Identität | runId, researchId, testId, Datum/Zeitzone, Client-/AC-/Server-Pins, Config-/Fixture-/Input-/Seed-Hash, Kontroll-/Versuchsvariante |
| Zeit | Client monotone Zeit, Server monotone Zeit sofern verfügbar, lokale Reihenfolgenummern, Uhrkorrelation samt Unsicherheit, tatsächliche RTT/Jitter/TPS/FPS |
| CORE B1b | outerTickId; currentStep oder endgültiger lastStep ausdrücklich unterscheiden; kind, stepIndex, interrupted, Withheld separat; Scene Availability, Generation, Packet-Epoch |
| Bewegung/Rotation | Raw/Applied-Input soweit verfügbar, Kamera/Request/Owner/effektive Winkel, lokale/geplante/angenommene Position, Ground/Sprint/Jump/Velocity/Item/Blockzustand |
| Netzwerk | Richtung, Packettyp, enqueue/hold/barrier/transport/discard mit Grund, FIFO-Sequenz und Hold-Owner, Zielpositionsalter; keine Token/Chat-/Accountinhalte |
| Aktionen | Klickgelegenheit, Swing C0A, Attack C02, Use/Release, Slot, Plan/Face/Hitpoint, lokale Controller-Rückgabe, serverseitig bestätigte Wirkung |
| Server | Check-/Violation-/Mitigation-Ereignis, Positivkontrollabdeckung, Setback, bestätigter Block/Schaden/Verbrauch, fehlende Kanäle und Logging-Drops |

Beobachter ist in B1b vorhanden, Export/Raw-Applied-Input-Snapshot und feingranulare Transporttraces sind vorgeschlagen. Fehlende Felder als Lücke markieren; niemals durch neue Gameplay-Hooks oder einen parallelen Session-/Packet-/Config-Store im Research-Auftrag ergänzen. Keine nanosekundengenaue Hostkorrelation vortäuschen. Read-only-Diagnose kann Beobachtungserfassung vornehmen, darf aber keine Queue pumpen, Ownership ändern oder Aktionen reservieren.

Annahmestufen getrennt berichten: geplant → lokal validiert → an Transport übergeben → serverseitig empfangen → Wirkung beobachtet → Check-/Mitigationbewertung. `AttackResultEvent`, lokales Placement-true, `isMotionAccepted` oder Eligibility-true sind keine Serverbestätigung. Kausalität nur mit korrelierten Traces, nicht aus gleichzeitig auftretenden Meldungen ableiten.

## Versuchsdurchführung

1. Vorab Hypothese, Nutzenmetrik und Mindestverbesserung δ, maximal akzeptierte Steuerabweichung ε, Abbruchkriterium und Messfenster festlegen. δ/ε sind Messdesignwerte des einzelnen Versuchs, keine neuen Produktdefaults. Ohne diese Definition keine Promotion.
2. Kontroll-Logging mit bekannten, im Labor erlaubten Positivereignissen prüfen; fehlen erwartete Kontrollereignisse, Ergebnis als unbestimmt werten. AC-Historie/Trust/Cloud-Zustand soweit zugänglich erfassen.
3. Kurze deterministische Sequenz zuerst; dann mindestens drei unabhängige Vergleichsläufe pro Variante mit identischer Exposition. Als Pilot 10 Minuten pro Lauf und ein längerer 30-Minuten-Lauf; bei nicht erreichten Sample-/Aktivierungsbedingungen verlängern oder unbestimmt. Dies ist keine Garantie statistischer Aussagekraft.
4. Reihenfolge randomisieren, zwischen unabhängigen Runs kontrolliert neue Session/Serverfixture herstellen, Warm-up dokumentieren. Gleiche Inputspur/Seed zunächst, weitere Seeds und Szenarien als Holdout. Vergleich mit dokumentierten RTT-Profilen; symmetrische/asymmetrische künstliche Verzögerung und natürliche RTT getrennt behandeln.
5. Bei Welt-/Connection-Wechsel, Korrektur, Tod oder Trace-Ausfall keine spätere Aktion dem alten Kontext zuordnen. Für Lifecycle-Tests ist dieser Wechsel das erwartete Testereignis; sonst Run abbrechen oder gesonderten Abschnitt beginnen.
6. Rohtraces, Settings und Resultat verknüpfen; Beobachtung, Erklärung und Widerlegung getrennt schreiben. Fehlversuche ebenfalls behalten. Keine Aktivierungsbedingungen oder Statistikgrenzen nachträglich passend wählen.

## Begrenzte Testpakete

| ID / Register | Konkrete Sequenz | Widerlegungsfall / Akzeptanz |
|---|---|---|
| T-01 / AC-CORE-DIAG-001 | echter Outer-Tick, Ordinary; Withhold ohne Body; Release mit Ordinary+Extra; EventUpdate-Cancel; GUI/Resume; Epoch-/Worldwechsel; Body-Exception | Zählungen oder Ereignisfolge ändern sich durch Diagnose: fail. Withheld nie ExecutedStep; currentStep vorläufig, finales interrupted korrekt; keine stale Zuordnung |
| T-02 / AC-INT-ROT-001, AC-GRI-ROT-001 | bewegtes Ziel und gültiger Attackkontext; Sensitivity niedrig/mittel/hoch, Wrap/Pitchgrenze, Zielverlust; Ist-Quantisierung vs. erst separat beauftragter Kandidat | kein Vorteil oder schlechtere Zielkonvergenz widerlegt Kandidat. Direkter Quantisierungs-/Pakettest nötig; Check-Abdeckung belegen. Grim experimental an/aus getrennt |
| T-03 / AC-INT-SCF-001, AC-GRI-SCF-001 | feste Brücke gerade/diagonal, Teilblock/Kante, zwei Pläne und Planlücke; PRE/POST; konkurrierende Aura; Slot-/C03-Rewrite und Resume | falsche Face/Pose/Scene oder unerlaubte Extra-Aktion: fail. Nutzen ≥δ bestätigte Blöcke bei Steuerfehler ≤ε, keine zusätzlichen Stürze/Ghostblocks; Checkdaten getrennt je AC |
| T-04 / AC-INT-CMB-001 | Ziel gültig→Swing→Zielverlust/Queue-Flush vor Controller; dazu physischer Klick, Itemwechsel und ungültiger Outer-Tick | falsche Sequenz oder manuelle Regression: fail. Klickgelegenheiten/C0A/C02/Schaden vollständig zugeordnet; tatsächliche CPS-Verteilung und Hitnutzen statt Settingwert |
| T-05 / AC-INT-CMB-002 | Schwert, Essen, Trank, Bogen einzeln; Use→Release→Attack→Resume; echter Slotwechsel und GUI; NoSlow+Autoblock | kein Verbrauch/Bogen-/Blockeffekt oder kein Bewegungsnutzen widerlegt NoSlow-Erfolg; keine stale Use/Slots, physischer Use erhalten; tatsächliche Serverwirkung nötig |
| T-06 / AC-INT-MOV-001 | acht Bewegungsrichtungen und Stillstand, alle bestehenden MoveFix-Modi; Sprint/Jump, Potion/Slowdown, kontrollierter Knockback, Slab/Treppe/Kollision | gleicher Physics-Befund oder schlechtere Steuerbarkeit widerlegt Verbesserung. Betrag/Richtung/Impuls getrennt prüfen; bestätigte Nettostrecke ≥δ ohne zusätzliche Korrekturen |
| T-07 / AC-INT-NET-001 | off/inbound/outbound/beide; zwei Hold-Owner, Lease-Ablauf und C0A/C02/C08-Barriere; Withhold/Extra, Zielwechsel/Correction | FIFO-Bruch/stale Replay/unerlaubte Automation: fail. Server-Hits/Nettostrecke ≥δ, Stillstandskosten mitrechnen; Timer-/Folgemitigation vollständig, keine lokale Reach-Garantie |
| T-08 / AC-POL-DIAG-001, AC-POL-BEH-001 | Polar/PolarLogs-Pins, Permissions, Storage-Kontext und Mitigation-Speicherung kontrollieren; bekannte Kontrollereignisse exportieren; danach T-02–T-07 getrennt | fehlende Kontrolle macht Abwesenheit von Logs unbestimmt. Nur belegte Kanalabdeckung und Polar-Szenarioreaktion berichten; proprietärer Algorithmus bleibt unbekannt |

## Resultatformat und Freigabe

Je Research-ID: Versionen, Run-IDs, Hypothese, beobachtete Werte samt Streuung/Exposition, aktivierte/zugängliche Check-Kanäle, Fehl-/Abbruchläufe, Widerlegung, alternative Ursache, Entscheidung `gestützt`, `widerlegt` oder `unbestimmt`, verbleibende Grenze. Nulldetektionen nur mit Dauer, Anzahl relevanter Aktionen und Logging-Abdeckung berichten. Ein bestandenes endliches Szenario rechtfertigt keine Aussage „unflaggbar“.

Entwicklungsfreigabe für ein begrenztes Paket erst mit lokalem Vertrags-/Regressionstest, passendem Serververgleich, geklärten API-/Lizenz-/Produktentscheidungen und Review des zuständigen Fachbereichs. Manuelle Vanilla-Aktionen bleiben außerhalb AutomationEligibility; zusätzliche Steps dürfen keine neue automatische Aktionsberechtigung erhalten. Keine neue globale Aktionssperre aus diesem Protokoll ableiten.
