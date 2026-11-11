# InvManager: Organized, Custom und Trash

## Layout und Hotbar

Neue Modulinstanzen beginnen mit `Layout = Organized`. Vorhandene Konfigurationen ohne Layout-Eintrag werden als `Legacy` geladen; ihre bisherigen Einstellungsschlüssel und Slotregeln bleiben erhalten. `Custom` erlaubt eine eigene Rolle für jeden der neun Hotbarplätze. `Unassigned` lässt den jeweiligen Platz in Ruhe.

| Platz | Organized-Rolle |
| --- | --- |
| 1 | Schwert |
| 2 | Bogen |
| 3 | Spitzhacke |
| 4 | Axt |
| 5 | Erster Blockstack |
| 6 | Zweiter Blockstack |
| 7 | Heiltrank, Wurftrank bevorzugt |
| 8 | Goldener Apfel oder bestes verfügbares geeignetes Essen |
| 9 | Enderperlen |

Die gewünschte Ausstattung mit zusätzlicher Angel benötigt insgesamt zehn Plätze. Deshalb behält Organized die Angel im Hauptinventar. Mit `Vacant Slots = Utility Fallback` kann sie einen Rollenplatz übernehmen, dessen eigentliches Item fehlt. Danach folgen Wasser, Lava und Feuerzeug als Ersatz. Sobald das eigentliche Item vorhanden ist, erhält es wieder seinen vorgesehenen Platz; das Ersatzitem wird beim Tausch erhalten. Eine feste Angelposition lässt sich in `Custom` wählen.

`Reserve` räumt einen unbelegten Rollenplatz in einen freien Hauptinventarplatz. Bei vollem Inventar bleibt der bisherige Inhalt erhalten. `Leave` lässt solche Plätze unverändert. Keine dieser Optionen entsorgt ein Item nur, um Platz zu schaffen. `Maximum Block Stacks` begrenzt die insgesamt behaltenen geeigneten Blockstacks; zusätzliche Blockrollen in `Custom` benötigen einen entsprechend höheren Grenzwert.

## Auswahl und Schutz

Organized und Custom berechnen ihre Auswahl deterministisch aus dem aktuellen Inventar. Pro Spielertick wird höchstens ein Inventarklick versucht, auch bei mehrfachen Update-Events, einem fehlgeschlagenen Controlleraufruf oder erneutem Einschalten. Vor dem nächsten Schritt werden Inhalt, Itemidentität und Kontext erneut geprüft. Das Modul arbeitet bei geöffnetem Spielerinventar; mit einem Item am Mauszeiger, während echter Maus- oder Tastatureingabe sowie nativer Touch-/Drag-Vorgänge, beim Tod oder bei einem fremden Container pausiert es. Organized und Custom prüfen zusätzlich, dass die angezeigte Inventaroberfläche genau zum aktuellen Spielercontainer gehört. Die beiden zusätzlichen Smart-Disable-Optionen können das Modul bei Tod beziehungsweise Weltwechsel vollständig ausschalten.

`Start Delay` beginnt beim Eintritt in einen tatsächlich zulässigen Inventarkontext, unabhängig davon, wie lange das Modul bereits existiert. Ein neues Inventarfenster, ein anderer Spieler oder ein Kontextwechsel beginnt diese Wartezeit erneut. Erfolgreiche Sortierschritte innerhalb desselben Kontextes setzen die Startwartezeit nicht zurück.

`Keep One Per Type` behält das beste vergleichbare Schwert, den besten Bogen und Werkzeuge sowie jeweils eine Angel, ein Feuerzeug und einen gefüllten Eimer pro Inhalt. Verzauberungen, verbleibende Haltbarkeit und stabile Slotreihenfolge entscheiden die Auswahl. Silk Touch und Fortune gelten als unterschiedliche Werkzeugzwecke. Verbrauchbare Heiltränke, Essen und stapelbare leere Eimer werden nicht als überzählige Ausrüstung behandelt.

Benannte Items und Gegenstände mit besonderen NBT-Daten werden nicht automatisch weggeworfen. Dazu gehören beispielsweise spezielle Serveritems, gespeicherte Inhalte und benutzerdefinierte Trankdaten. Gewöhnliche Verzauberungen und Reparaturkosten bleiben vergleichbar. Auch eine Trash-Regel hebt diesen Schutz nicht auf. Geschützte Gegenstände können deshalb dazu führen, dass mehr als ein Exemplar eines Typs erhalten bleibt. `Keep Armor` schützt Rüstung und unterdrückt keine unabhängigen Trash-Regeln mehr.

Lagerblöcke, Werkbänke und Blöcke mit Tile Entities wie Öfen, Endertruhen, Verzauberungstische oder Beacons zählen nicht als automatisch entsorgbare Baustacks. Diese Schutzkorrektur gilt auch für Legacy. Eine geschützte, zugleich geblacklistete Waffe verdrängt keine weiterhin zulässige gewöhnliche Waffe bei der Auswahl. Gleichwertige Stacks auf einem passenden Hotbarplatz bleiben dort, damit gleiche Blockstacks keine endlosen Tauschaktionen verursachen.

`Legacy` behält seine vorhandenen Rollen und die bisherigen Random-/Open-Inv-Einstellungen. Diese beiden Einstellungen sind für Organized und Custom ausgeblendet. Die bisherigen Delay-Schlüssel und Einheiten bleiben erhalten; die Wartezeit wird jetzt einmal pro Aktionsintervall bestimmt und nicht bei jeder Abfrage neu gezogen.

## Tempo und Auswahlprofile (Stealer und InvManager)

Alle neuen Werte werden hinter den vorhandenen Phase-1-Werten einschließlich Smart Disable angehängt. Neue Modulinstanzen verwenden zunächst `Interaction Mode = Legacy` und `Speed Profile = Legacy`. Beim Laden älterer Profile ohne diese Schlüssel werden beide Modes ausdrücklich auf Legacy zurückgesetzt, damit Einstellungen aus einer vorher geladenen Konfiguration nicht versehentlich aktiv bleiben. Explizit gespeicherte neue Profile bleiben erhalten.

| Speed Profile | Ablauf |
| --- | --- |
| Legacy | Verwendet den bisherigen `Delay` des Stealers beziehungsweise `Min Delay`/`Max Delay` des InvManagers. |
| Uniform | Unabhängiger, einmal gezogener Wert zwischen den beiden Millisekunden-Grenzen. |
| Smooth | Zusammenhängender Verlauf über mehrere Aktionen mit vorhandenem FastNoiseLite. |
| Waves | Ruhiger Wellenverlauf mit zusätzlicher begrenzter Variation. |

`Min Action Delay MS` und `Max Action Delay MS` begrenzen die geplanten Aktionsintervalle auf 0–1500 ms. Bei vertauschten Grenzen gilt automatisch der kleinere Wert als Minimum. `Variation Rate` verändert bei Smooth und Waves, wie schnell der Verlauf zwischen aufeinanderfolgenden Aktionen fortschreitet. Der Stealer beginnt für neue Profile mit 80–120 ms, der InvManager mit 100–200 ms. Diese Werte werden erst mit einem der neuen Speed-Profile aktiv.

Beim InvManager bleiben die alten Werte in ihren historischen Einheiten erhalten: `Min Delay`, `Max Delay` und `Start Delay` entsprechen jeweils dem angezeigten Wert mal 10 ms. Der Stealer verwendete bereits Millisekunden. Open-/Close-/Start-Wartezeiten bleiben eigene Einstellungen. Start Delay erhält keinen ständig neu berechneten Zufallsaufschlag mehr.

Die nächste Wartezeit wird einmal bestimmt und gegen eine monotone Uhr geprüft. Zusätzliche Update-Aufrufe und die Renderfrequenz ziehen keine neuen Zufallswerte. Nach einem Versuch beginnt genau ein neues Intervall; es gibt keine angesammelten Nachholaktionen. Der vorhandene Grenzwert von höchstens einem Versuch pro Spielertick gilt auch bei 0 ms. Vorschauzeit, Serverantworten und echte Eingaben können den tatsächlich beobachteten Abstand zusätzlich verlängern.

### Stealer: stabile Pick-Reihenfolge

`Randomness = Off` verwendet eine fortlaufende Slotreihenfolge. Mit Randomness sind drei Pick-Profile verfügbar:

- **Shuffle:** eine gemischte vollständige Reihenfolge pro geöffneter Sitzung;
- **Rows:** unterschiedliche Reihenfolge der Reihen, innerhalb einer Reihe zusammenhängend;
- **Nearby:** nach dem Start bevorzugt nahe Slots.

Während Wartezeiten und Vorschau bleibt diese Reihenfolge unverändert. Erst nach einem tatsächlich versuchten Transfer rückt ein Cursor in derselben Reihenfolge weiter. Dadurch kann ein ständig nachgefüllter Slot andere noch unbehandelte Slots nicht dauerhaft verdrängen. Nicht empfangbare Items werden übersprungen, ohne gegen ein volles Inventar wiederholt Klicks auszugeben. Ein neues Fenster oder eine geänderte Einstellung beginnt eine neue Sitzung.

### InvManager: Drop-Reihenfolge

`Drop Order` bietet Priority, Shuffle, Rows und Nearby. Diese Einstellung ordnet ausschließlich das bereits abgeschlossene, sichere Trash-Set. Sie verändert weder die Wahl des besten Werkzeugs noch Schutzregeln, Itemvarianten oder Hotbar-Rollen. Bei Legacy und aktivem altem `Random` wird die bisher gewünschte zufällige Reihenfolge auch bei `Drop Order = Priority` beibehalten; sie wird dabei ebenfalls stabil statt bei jeder Abfrage neu gemischt.

Ausrüsten und Sortieren behalten ihre fachliche Reihenfolge. Geschützte Items werden durch kein Zufallsprofil entsorgbar.

## Sichtbarer Click-Modus

`Interaction Mode = Click` arbeitet ausschließlich in einer tatsächlich geöffneten, passend gebundenen Truhe beziehungsweise im Spielerinventar. Eine Theme-farbene Markierung zeigt den Quellslot und bei einem Tausch zusätzlich das Ziel. Eine kleine Statusfläche erläutert die Aktion; bei wenig Platz wird sie zu einer kompakten Anzeige im GUI-Kopfbereich.

`Preview Time MS` bestimmt eine sichtbare Vorschau von 50–500 ms, standardmäßig 100 ms. Die Zeit beginnt erst, nachdem die Markierung wirklich gerendert wurde. Ein Update alleine kann keine Vorschau bestätigen. Danach erfolgen die bestehenden Auswahl-, Stack-, Szenen- und Tickbudget-Prüfungen erneut, bevor genau eine passende native Shift-/Hotbar-/Drop-Aktion über den vorhandenen Controller ausgeführt wird.

Echte Eingaben gewinnen jederzeit. Mausbewegung, Rad, Maustasten und Tastatur widerrufen eine begonnene Vorschau. Cursorstacks, Touchscreen-Vorgänge und Drag-Splitting pausieren die Assistenz ebenfalls. `Resume Delay MS` bestimmt die anschließende Ruhezeit (250–1000 ms, Standard 300 ms); die Mindestzeit berücksichtigt das native Doppelklickfenster. Eine neue Vorschau ist danach wieder erforderlich. Die physische Mausposition, native Hover-Slots und Tasten-/Drag-Zustände werden von der Assistenz nicht gesetzt oder überschrieben.

Nach einer Click-Aktion bleibt höchstens eine Operation offen. Eine lokale Vorhersage ist keine Serverbestätigung. Der vorhandene normale Transport wird passiv auf die passende Fenster-/Transaktionsbestätigung beobachtet. Eine Ablehnung, unerwartete Inhaltsänderung oder ausbleibende Antwort nach fünf Sekunden pausiert weitere assistierte Aktionen bis zu einer passenden vollständigen Synchronisierung beziehungsweise einem neuen Fenster. Die Oberfläche zeigt diesen Zustand. Es werden keine künstlichen Resync-Pakete verschickt und keine fremden Inventaränderungen rückgängig gemacht. Eine offene Operation wird auch während eines Modul-Toggles weiter rein passiv abgewickelt; der Beobachter entfernt sich nach Abschluss oder Kontextende.

Der Click-Modus verschickt keine zusätzlichen Legacy-Open-/Close-Pakete. Er simuliert weder OS-Eingaben noch einen versteckten Mauszeiger. Im Legacy-Interaktionsmodus bleibt der bisherige Controllerweg bestehen; die neuen Timing- und Auswahlprofile sind unabhängig davon nutzbar.

## Trash-Menü

Das Menü ist über `.trash` und den Trash-Eintrag der Library erreichbar. Die Suche durchsucht die im Spiel registrierten Items einschließlich ihrer Varianten nach Anzeigename, Registry-ID und Metadaten. Die Liste zeigt echte Itemicons, Namen, IDs und die betroffene Variante. Ein bestätigter Klick oder die Leertaste/Eingabetaste auf einem ausgewählten Eintrag schaltet dessen Regel um.

Regeln gelten für die genaue Vanilla-Variante. Beispielsweise betrifft eine Regel für rote Wolle keine weiße Wolle; normale und geworfene Tränke bleiben getrennt. Bei beschädigbarer Ausrüstung gilt die Regel für alle Haltbarkeitswerte desselben Items. Das Menü selbst verschiebt oder entsorgt keine Inventarinhalte. Neue Regeln werden vom aktiven InvManager erst bei einem anschließend zulässigen Inventarschritt berücksichtigt.

| Eingabe | Aktion |
| --- | --- |
| `Ctrl+F` | Suche fokussieren und auswählen |
| `Ctrl+L` | Alle Items / nur geblacklistete Items |
| `Ctrl+Z` | Letzte Regeländerung rückgängig machen |
| `F5` | Gespeicherte Regeln neu laden |
| `Tab` | Zwischen Suche und Itemliste wechseln |
| Pfeile, `Home`, `End`, `Page Up`, `Page Down` | Itemliste navigieren |
| Leertaste / Eingabetaste in der Liste | Ausgewählte Regel umschalten |
| `Escape` | Suche leeren, danach zum vorherigen Menü zurückkehren |

Die Regeln werden clientweit in `<mcDataDir>/pulsar/inventory-trash.json` gespeichert und wechseln nicht mit einer Modulkonfiguration. Das Format ist versioniert, auf 512 KiB und 4096 Regeln begrenzt und wird über eine temporäre Datei ersetzt; ein atomarer Austausch wird genutzt, wenn das Dateisystem ihn unterstützt. Schreibfehler verändern den aktiven Regelsatz nicht. Ungültige oder nicht unterstützte Dateien werden sichtbar gemeldet und nicht überschrieben. `Reload` ermöglicht das erneute Laden nach einer Korrektur. Eine begonnene Mausaktion verfällt bei Filterwechsel, Tastatureingabe oder Größenänderung, damit kein anderer Eintrag versehentlich geändert wird.

## Prüfumfang

Die fokussierten JUnit-Klassen sind `InventoryPlannerTest`, `InventoryItemClassifierTest`, `TrashItemStoreTest`, `TrashItemCatalogTest` und `InventoryManagerOrganizedTest`. Sie ergänzen die bestehenden Ranking-, Consumer- und Sicherheitsprüfungen des Legacy-Modus. `TrashMenuCheck` verwendet die echten Registry-, Modell-, Shader- und Eingabepfade in einem isolierten Pbuffer und schreibt Ansichten nach `trash-preview/`. `InventoryPacingTest`, `InventorySlotOrderTest`, `InventoryAssistSessionTest` und `InventoryProfilesFlowTest` prüfen zusätzlich gespeicherte Deadlines, stabile faire Reihenfolgen, Eingabevorrang, echte native Aktionen sowie ACK-/Resync-/Lifecycle-Grenzen. `InventoryLayoutConfigTest` prüft auch unveränderte Phase-1-Indizes und Migration/Roundtrip der neuen Profile.

`InventoryAssistRenderCheck` rendert echte 27-/54-Slot-Truhen und das Spielerinventar samt Itemmodellen und Produktions-Overlay in einer eigenen Pbuffer-JVM; nur das Spielerportrait wird in dieser Fixture ausgelassen. Ausgaben liegen unter `inventory-assist-preview/`. Der Test prüft sichtbare Renderbestätigung, Resize-Widerruf, kompakte Darstellung, GL-Zustandswiederherstellung und ausbleibende Inventaraktionen beim Zeichnen. Diese automatisierten Prüfungen ersetzen keinen interaktiven Spieltest mit einem realen Server und dessen Inventarbestätigungen.
