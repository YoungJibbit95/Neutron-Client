# Neutron 2.0 — AC-R01 Offene Fragen

Revision 1.0 · 28.10.2026. Offene Punkte begrenzen die jeweils benannte Aussage; sie blockieren keinen unabhängigen CORE-Auftrag. Research-IDs und historische Korrekturen: [Register](AC_RESEARCH_REGISTER.md).

| ID / Priorität | Frage / fehlende Evidenz | Benötigter Beleg / Zuständigkeit |
|---|---|---|
| O-01 / P1 | Wo ist das originale v1.0-/RV-Quellenpaket mit aktueller Prioritätsrangfolge? | CORE-Projektowner: konkrete Dokumentversion und Ort. I01a/I01b-Vertragsdokumente liegen vor und wurden geprüft; Originalpaket hier nicht unabhängig gelesen |
| O-02 / P1 | Welcher Commit wird Fachbasis nach dem laufenden I01c? | CORE: veröffentlichter lokaler Commit, Consumer-Scope und Testbericht; aktueller uncommitteter Stand ist keine Abnahme |
| O-03 / P1 | Welche Intave-/Grim-/Polar-JARs, Configs und autorisierten Labore sind verfügbar? | Testserver-Owner: Version/Hash, Genehmigung, Plugins/Proxy/Via, Checks/Cloud/Trust/Logging. Bisher kein Live-Run |
| O-04 / P1 | Welcher begrenzte Trace-Export wird gesondert beauftragt? | CORE/NET: Schema/Retention/Overhead, Drop-Erfassung, Scene-/Tick-Korrelation. Observer existiert, enthält keine allgemeine Paket-/Inputhistorie |
| O-05 / P1 | Wie erfolgt die aktuelle automatische Admission inklusive Phasengültigkeit? | CORE-I01c: API und endgültige Tests; B1b-Eligibility allein ist weder Abschluss-, Phasen- noch Budgetnachweis. Manuelle Aktionen separat erhalten |
| O-06 / P2 | Bleibt die Quantisierungs-Restbehandlung fachlich gewünscht? | CMB: direkter Quantisierungs-/Transporttest und T-02. Alter Verweis auf RotationTrajectory-Test beweist diese Ausnahme nicht |
| O-07 / P2 | Welche konkreten MoveFix-/Sprint-/Jump-Kombinationen erzeugen Serverabweichungen? | MOV/CMB: T-06; diskrete Inputrekonstruktion ist Quellenbefund, Flag-Hypothese ungetestet. Keine automatische Defaultänderung |
| O-08 / P2 | Wie lang hält Scaffold fachlich Rotation bei Planlücke, Suspend/Resume und Aura-Konkurrenz? | SCF/CMB/CORE: Produktprinzip Halten bis Disable plus frische Kontext-/Ownerprüfung; kein blinder stale Winkel. Numerische Profile separat |
| O-09 / P2 | Welche tatsächlichen Swing-/Attack-/Use-Serien sieht der Server? | CMB/NET/MOD: T-04/05 mit sichtbaren Gates und Barrieren. Kein sicherer CPS-/Autoblock-/NoSlow-Modus belegt |
| O-10 / P2 | Sind Backtrack-/Tickbase-/LagRange-Vorteile nach Delay-/Stillstandskosten und Timer-Folgemitigation real? | NET: T-07 plus Server-Hit/Positionsnachweis; lokale Animation/Positionsalter reichen nicht |
| O-11 / P2 | Sind Grim-Experimental-Checks und Polar-Mitigation-Logs aktiviert und vollständig? | jeweiliger Serverowner: exakte Config, Positivkontrollen, T-08. Keine Projektion von Intave auf Polar |
| O-12 / P3 | Welche Rechte wären bei einer konkreten Übernahme fremden Codes nötig? | Lizenzverantwortlicher: datei-/versionsbezogene Klärung vor Übernahme; AC-R01 enthält nur selbst formulierte Analyse und Quellenlinks |
| O-13 / P3 | Welcher NCP-Stand soll untersucht werden? | Research-/Serverowner: Commit/JAR/Config; bisher keine versionierte NCP-Checkanalyse, daher keine NCP-spezifischen Anforderungen |

## Nicht belegte Annahmen

- Ein kleiner Sensitivity-Rest löst Intave unmittelbar aus: U; die gelesene Heuristik hat zusätzliche Bedingungen und Historie.
- Noise oder empirische Klickintervalle seien menschlich/unauffällig: U; keine garantierte Übertragbarkeit auf lokale oder Cloud-Heuristiken.
- Acht-Richtungs-MoveFix sei automatisch vom Server akzeptiert: U; Stärke, Sprint, Impuls, Phasenbasis und andere Kandidaten wirken mit.
- Verzögerte S14 veränderten Intaves Feedback-Sicht identisch: U; Server und Client führen eigene Zustände.
- Langfristig ausgeglichene Tickzahl genüge gegen Timer/MicroBlink: U; Ankunftszeit und Aktionskorrelation sind getrennte Quellenbefunde.
- Keine sichtbare Polar-Meldung bedeute keine Erkennung: U; Abdeckung und proprietäre Auswertung unbekannt.
- Full Disabler oder allgemeines Fly folge aus Zustandsmehrdeutigkeit: U; kein reproduzierbarer Nachweis, kein Implementierungsauftrag.

## Nächster begrenzter Forschungszyklus

Zuerst H0/T-01 und O-02/O-03 klären; danach maximal H1 und H2 als einzeln messbare Fachuntersuchungen priorisieren. Neue Ideen werden erst aufgenommen, wenn sie eine vorhandene ID präzisieren oder nach einem abgeschlossenen Test eine konkrete neue Frage begründen. Wenn Server-/Quellenzugang fehlt, Ergebnis unbestimmt dokumentieren und unabhängige Code-/Testlektüre fortsetzen. Keine unbegrenzte Ideensammlung und keine stillschweigende Freigabe offener Produktentscheidungen.
