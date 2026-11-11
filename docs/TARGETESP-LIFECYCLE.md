# TargetESP und Modul-Lifecycle

## TargetESP einschalten

`Render → TargetESP` enthält zwei unabhängig schaltbare Anzeigen: einen Halo am aktuellen Aura-Target und einen Punkt für die aktuell angewendete Aura-/Scaffold-Rotation. Das Modul ist standardmäßig ausgeschaltet. Nach dem Einschalten sind beide Anzeigen zunächst ausgewählt.

Die Einstellungen liegen in den drei Dropdown-Unterkategorien **Halo**, **Hitpoint** und **Appearance**. Die zugehörigen Detailregler werden ausgeblendet, wenn ihre Anzeige ausgeschaltet ist; der Schalter selbst bleibt erreichbar.

### Halo

| Einstellung | Wirkung |
|---|---|
| Target Halo | Zeigt den Halo am aktuell gültigen Aura-Target. |
| Halo Radius Padding | Zusätzlicher Abstand zur seitlichen Target-Hitbox. |
| Halo Width | Breite des leuchtenden Bandes. |
| Halo Speed | Geschwindigkeit der Höhenbewegung und des umlaufenden Lichtkopfes. |
| Halo Trail | Stärke der nachlaufenden, ausblendenden Bänder. |
| Halo Motion | Stärke der federnden Neigung bei Target-Bewegung. |

Der Halo übernimmt das aktive Target der Aura. Er sucht selbst keine Gegner. Ein Target-Wechsel oder eine ungültige Szene verwirft die vorherige Bewegung, damit kein Schweif an einem alten Target hängen bleibt.

### Hitpoint

| Einstellung | Wirkung |
|---|---|
| Hitpoint | Schaltet den Rotationspunkt ein oder aus. |
| Hitpoint Source | `Active` zeigt den aktuell akzeptierten Besitzer der Rotation; `Aura` und `Scaffold` beschränken die Anzeige auf dieses Modul. |
| Hitpoint Size | Größe des geschichteten Punktes und seines Glows. |
| Hitpoint Ray Length | Länge des **Visualisierungsstrahls**, standardmäßig sechs Blöcke. Verändert keine Angriffs- oder Platzierungsreichweite. |
| Show Ray Misses | Zeigt bei fehlendem Treffer einen schwächeren Punkt am Strahlende. Ausgeschaltet erscheinen nur tatsächliche lokale Schnittpunkte. |

Der Punkt beobachtet ausschließlich die **lokal akzeptierten effektiven Rotationswinkel**. Der Strahl beginnt an der zuletzt lokal gemeldeten Spielerposition zuzüglich Augenhöhe. Bei Aura wird der nächste Schnittpunkt mit einer kollidierbaren Entität oder einem Block angezeigt; bei Scaffold werden Blöcke berücksichtigt.

Das ist **keine Serverbestätigung** eines Treffers, Angriffs oder platzierten Blocks. Der Punkt zeigt auch keinen gewünschten zukünftigen Aim-Punkt. Die Anzeige fordert keine Rotation an und löst keine Spielaktion aus.

Die Punktposition erhält bewusst **keine Bewegungsglättung**: Eine geänderte angewendete Rotation soll sofort sichtbar werden. Glow und Schichtung verbessern die Lesbarkeit, ohne den Punkt hinter seiner tatsächlichen Position herziehen zu lassen.

Nach dem Einschalten oder einem Kontextwechsel wartet die Anzeige auf frische Bewegungsdaten. Fehlt der gültige Besitzer oder schreitet dessen Bewegungssequenz länger als 250 ms nicht fort, wird kein alter Punkt weiter angezeigt. Menüs, Pause, Fokusverlust und ungültige Spieler-/Weltszenen können die Darstellung ebenfalls aussetzen.

### Appearance

| Einstellung | Wirkung |
|---|---|
| Opacity | Deckkraft beider Anzeigen. |
| Glow Strength | Stärke des weichen Shader-Glows. |
| Through Walls | Aus: Die Szene verdeckt die Anzeigen durch ihren Tiefentest. Ein: Die Overlays bleiben durch verdeckende Geometrie sichtbar. Der Strahl berücksichtigt weiterhin seine Blockkollisionen. |
| Color Source | `Theme` übernimmt die aktuellen Theme-/Gradient-Einstellungen; `Custom` verwendet die eigene Farbe. |
| Custom Color | Eigene Farbe, sichtbar bei `Color Source = Custom`. |

Die globalen Optionen **Animations**, **Reduced Motion** und **Ambient Motion** werden berücksichtigt. Bei reduzierter oder deaktivierter Bewegung entfallen dekoratives Umlaufen, Höhenbewegung und federnde Neigung. Die aktuelle Position des Targets und der genaue Rotationspunkt bleiben weiterhin beobachtete Spielwerte.

## Smart Disable

**Aura, Scaffold, Stealer und InvManager** erhalten unter **Safety** zwei unabhängige Checkboxen:

| Einstellung | Verhalten |
|---|---|
| Smart Disable on Death | Deaktiviert das Modul, wenn der lokale Spieler tot beziehungsweise nicht mehr lebendig ist. |
| Smart Disable on World Change | Deaktiviert das Modul beim Verlust oder Wechsel des beobachteten Welt-, Spieler- oder Verbindungskontexts. Dazu zählen etwa Weltentladen, ein neuer Spieler beim Respawn oder das Ende der beobachteten Verbindung. |

Beide Optionen sind standardmäßig **OFF** und müssen ausdrücklich aktiviert werden. Fehlende Werte in älteren Konfigurationen bleiben ebenfalls OFF. Eine automatische Deaktivierung führt den normalen Modul-Abschaltpfad aus; das Modul schaltet sich danach nicht von selbst wieder ein.

Die Erkennung beruht auf tatsächlichen Welt-/Spieler-/Verbindungswechseln. Ein Teleport in eine Lobby innerhalb derselben Welt mit demselben Spieler und derselben Verbindung wird **nicht anhand von Positions-, Scoreboard- oder Lobby-Heuristiken erraten**. Wird ein Modul im Menü eingeschaltet, darf der erste Welteintritt zunächst seinen Beobachtungskontext herstellen.

## Konservativer Ground Guard

Automatische Sprunganforderungen von JumpReset und Velocity werden zusätzlich durch denselben Ground Guard begrenzt. Ein gesetztes `onGround` allein reicht nicht aus: Der Spieler muss lebendig sein, zur aktuellen Welt gehören, darf nicht fallen beziehungsweise `noClip` verwenden, und direkt unter den Füßen muss gültige Kollisionsunterstützung vorhanden sein. Ändert sich die Szene während dieser Prüfung, wird die Anforderung verworfen.

Pro Spieler und Spielertick kann nur eine dieser automatischen Sprunganforderungen den gemeinsamen Guard beanspruchen. Bei fehlender oder unklarer Unterstützung bleibt der automatische Sprung aus. Manuelle Sprungeingaben erhalten dadurch keine neue Automatisierung; vorhandene modulspezifische Bedingungen gelten weiterhin.
