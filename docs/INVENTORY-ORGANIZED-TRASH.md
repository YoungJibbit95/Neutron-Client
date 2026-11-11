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

Organized und Custom berechnen ihre Auswahl deterministisch aus dem aktuellen Inventar. Pro Spielertick wird höchstens ein Inventarklick versucht, auch bei mehrfachen Update-Events, einem fehlgeschlagenen Controlleraufruf oder erneutem Einschalten. Vor dem nächsten Schritt werden Inhalt, Itemidentität und Kontext erneut geprüft. Das Modul arbeitet bei geöffnetem Spielerinventar; mit einem Item am Mauszeiger, während gedrückter linker oder rechter Maustaste, beim Tod oder bei einem fremden Container pausiert es. Organized und Custom prüfen zusätzlich, dass die angezeigte Inventaroberfläche genau zum aktuellen Spielercontainer gehört. Die beiden zusätzlichen Smart-Disable-Optionen können das Modul bei Tod beziehungsweise Weltwechsel vollständig ausschalten.

`Start Delay` beginnt beim Eintritt in einen tatsächlich zulässigen Inventarkontext, unabhängig davon, wie lange das Modul bereits existiert. Ein neues Inventarfenster, ein anderer Spieler oder ein Kontextwechsel beginnt diese Wartezeit erneut. Erfolgreiche Sortierschritte innerhalb desselben Kontextes setzen die Startwartezeit nicht zurück.

`Keep One Per Type` behält das beste vergleichbare Schwert, den besten Bogen und Werkzeuge sowie jeweils eine Angel, ein Feuerzeug und einen gefüllten Eimer pro Inhalt. Verzauberungen, verbleibende Haltbarkeit und stabile Slotreihenfolge entscheiden die Auswahl. Silk Touch und Fortune gelten als unterschiedliche Werkzeugzwecke. Verbrauchbare Heiltränke, Essen und stapelbare leere Eimer werden nicht als überzählige Ausrüstung behandelt.

Benannte Items und Gegenstände mit besonderen NBT-Daten werden nicht automatisch weggeworfen. Dazu gehören beispielsweise spezielle Serveritems, gespeicherte Inhalte und benutzerdefinierte Trankdaten. Gewöhnliche Verzauberungen und Reparaturkosten bleiben vergleichbar. Auch eine Trash-Regel hebt diesen Schutz nicht auf. Geschützte Gegenstände können deshalb dazu führen, dass mehr als ein Exemplar eines Typs erhalten bleibt. `Keep Armor` schützt Rüstung und unterdrückt keine unabhängigen Trash-Regeln mehr.

Lagerblöcke, Werkbänke und Blöcke mit Tile Entities wie Öfen, Endertruhen, Verzauberungstische oder Beacons zählen nicht als automatisch entsorgbare Baustacks. Diese Schutzkorrektur gilt auch für Legacy. Eine geschützte, zugleich geblacklistete Waffe verdrängt keine weiterhin zulässige gewöhnliche Waffe bei der Auswahl. Gleichwertige Stacks auf einem passenden Hotbarplatz bleiben dort, damit gleiche Blockstacks keine endlosen Tauschaktionen verursachen.

`Legacy` behält seine vorhandene Reihenfolge und die bisherigen Random-/Open-Inv-Einstellungen. Diese beiden Einstellungen sind für Organized und Custom ausgeblendet. Die bisherigen Delay-Einstellungen bleiben unverändert.

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

Die fokussierten JUnit-Klassen sind `InventoryPlannerTest`, `InventoryItemClassifierTest`, `TrashItemStoreTest`, `TrashItemCatalogTest` und `InventoryManagerOrganizedTest`. Sie ergänzen die bestehenden Ranking-, Consumer- und Sicherheitsprüfungen des Legacy-Modus. `TrashMenuCheck` verwendet die echten Registry-, Modell-, Shader- und Eingabepfade in einem isolierten Pbuffer und schreibt Ansichten nach `trash-preview/`. Diese automatisierten Prüfungen ersetzen keinen interaktiven Spieltest mit einem realen Server und dessen Inventarbestätigungen.
