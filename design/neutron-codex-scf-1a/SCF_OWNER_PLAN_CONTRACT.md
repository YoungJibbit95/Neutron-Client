# SCF-1a: Plan, Rotationswunsch und bestätigter Owner

Stand: 28.10.2026. Basis `88ee497d8fe2dca16e5eaae9e48548c7d46e7050`.
Maßgeblich: Codex-08-Masterprompt und Produktspezifikation v1.0 §§2.3/3.1/3.2/4/7.3/9/11.
Die 19 SHA-256-/Größenangaben des externen Quellenpakets wurden geprüft.
Dies implementiert **SCF-1a active hold**. Der 2–4-Tick-Disable-Auslauf bleibt SCF-1b.

## Drei getrennte Zustände

Ein Placementplan enthält Ziel, Frame und Sequenz. Ein Rotationswunsch enthält nur
die letzte in derselben Scene tatsächlich akzeptierte Scaffold-Pose. Bestätigte
Authority liefert weiterhin ausschließlich `RotationManager.canUseRotation("scaffold", pre)`.
Weder der gespeicherte Wunsch noch ein neuer Request erzeugt diese Authority.

| Übergang | Plan/Tower/Downwards/Timer | Wunsch | Bestätigte Source / Aktionen |
| --- | --- | --- | --- |
| Aktiver gültiger Plan, Rotation ON | Bestehende Planung und Cleanup bleiben | Bestehendes Profil für neues Ziel | PRE nur vorherige, POST nur aktuelle passende Acceptance |
| Plan endet / Support fehlt | Ziel, Frame, Sequenz, Tower und Downwards löschen; eigenen Timer freigeben | Letzte akzeptierte Pose derselben Scene behalten | Source nicht wegen Planende resetten; im tatsächlich betretenen PRE erneut anfordern |
| Erste Planlücke ohne akzeptierte Scaffold-Pose | Kein Plan | Kein erfundener Wunsch | Kein Source-Request, kein Placement |
| C03 Reject/Cancel/Delay oder abweichender Rewrite/Replay | Kein zusätzliches Placementbudget | Wunsch derselben Scene darf erneut angefordert werden | Bis passender neuer lokaler Acceptance keine Source-Rechte; Pending genügt nicht |
| GUI / Pause / Fokusverlust | Keine neue Planung/Platzierung/Input-/Timeranwendung; eigenen Plan sicher beenden | Derselbe Scene-Wunsch bleibt | Source-Anwendungsrecht entziehen; keine Requests während Suspension |
| Resume in derselben Scene | Frisch planen und Guards prüfen | Wunsch darf wieder angefordert werden | Neue C03-Acceptance erforderlich; PRE darf sie nicht vorwegnehmen |
| Rotation OFF | Plan abhängig von tatsächlich akzeptierter Kamerapose | Scaffold-Wunsch verwerfen | Source hart freigeben; numerischer Camera-/Vanilla-Pfad bleibt |
| Welt/Player/Connection/Pipeline/Epoch/Observergeneration ungültig, Death | Alte Pläne verwerfen; kein Slotrestore in Ersatzscene | Wunsch verwerfen | Zugehörige alte Source invalidieren; Ersatzmanager/-frame nicht freigeben |
| Disable | Eigene Pläne, Tower, Downwards und Timer aufräumen | Wunsch verwerfen | Sofortiger harter Source-Release, keine neue Place-/Inputaktion |

`src/main/java/me/youngjibbit/module/impl/world/Scaffold.java:134` unterscheidet lebende Objektidentitäten von aktiver Gameplaygültigkeit
einschließlich Pause. `:150/:163/:169` binden Player, World, Connection, PacketPipeline,
Epoch, RotationManager und, sobald verfügbar, den kanonischen `SceneContext`.
Vor Bootstrap gibt es keinen Ersatzobserver und keine Placement-Admission.
`RuntimeServices.observation()` bleibt unverändert nullable.

`Scaffold.java:187/:198` trennen Source-Ende vom Löschen des Wunsches;
`:203` übernimmt nur eine tatsächlich bestätigte, korrekt gebundene Scaffold-Pose.
`:212` suspendiert ohne neue Requests. `:218` beendet nur den Plan;
`:231` führt vollständigen Context-Cleanup aus. `onValueChange` besitzt im bestehenden
Modulevertrag keinen Value-Parameter; es beendet konservativ den Plan und revalidiert
Rotation-ON, Toggle und Scene, statt jede Settingänderung als Source-Ende auszulegen.

## PRE, C03 und POST

Unveränderte äußere Kette in
`src/main/java/net/minecraft/client/entity/EntityPlayerSP.java:219`:

```text
bereits berechnete Physik
  -> bindConnection(PacketPipeline), beginMotion(Player, World)
  -> Motion PRE
     -> Plan finalisieren
     -> ggf. tryPlace(true): ausschließlich vorheriger accepted Owner/Pose
     -> Request für aktuellen gültigen Plan ODER akzeptierten Scene-Wunsch
        auch nach erfolgreichem PRE-Placement vor dessen Return
  -> Sprint-/Sneak-C0B
  -> RotationManager.resolve
  -> C03 lokal accepted/rejected, tatsächliche beobachtete Winkel
  -> finishMotion
  -> Motion POST
     -> ggf. tryPlace(false): ausschließlich aktueller accepted Owner/Pose
     -> Planende lässt Source/Wunsch bestehen
```

Scaffold-PRE und Early-Return: `Scaffold.java:246/:289`; POST: `:301`.
Request-Erneuerung: `:337/:351`. Callbackfortsetzung prüft zusätzlich dieselbe
Motionsequenz und Scene (`:360`); ein inzwischen deaktiviertes Modul oder ein
reentrant ersetzter Frame darf den alten Listenerpfad nicht fortsetzen.

`src/main/java/me/youngjibbit/core/combat/RotationManager.java:138` bleibt unverändert: die Source-Predicate verlangt
`pre == collecting`. PRE nach Finish und POST während Collecting sind ungültig.
`finishMotion` bestätigt nur den tatsächlich gewählten Winner bei passender finaler Pose.
Die bestehende Priorität `SCAFFOLD_PRIORITY` und alle Profil-/GCD-/Noise-/Winkelwerte bleiben gleich.

Ein abweichender externer C03-Replay verändert Effective-Winkel, aber nicht unmittelbar
`EntityPlayerSP.lastReportedYaw`. Im getesteten Fall bleibt der erste nachfolgende
nicht rotierende C03 unbesessen; erst der folgende tatsächlich passende Look bestätigt
den erneuerten Scaffold-Wunsch. Es wird keine frühere Acceptance behauptet oder ein
zusätzliches Paket erzwungen. Die Transport-/FIFO-/Barrierenlogik bleibt unverändert.

## Placement-API und späte Guards

Datei: `src/main/java/me/youngjibbit/core/world/ScaffoldPlacementService.java`.

```java
// Unverändert aufrufbarer numerischer Legacy-/Camera-Pfad:
boolean place(PlacementTarget target, RotationManager rotations, boolean pre,
              long sequence, long delay, boolean silentSwitch,
              boolean swing, boolean silentSwing);

// Additiver Produktionspfad:
boolean placeWithAuthority(PlacementTarget target, RotationManager rotations,
              boolean pre, long sequence, long delay, boolean silentSwitch,
              boolean swing, boolean silentSwing, String requiredRotationSource,
              BooleanSupplier actionValid, BooleanSupplier sceneValid);
```

Der alte Aufruf delegiert mit `requiredRotationSource=null` und neutralen Callbacks
(`:208/:211`). Seine numerischen Guards und deren bisherige Reihenfolge bleiben erhalten.
`Scaffold.tryPlace` (`Scaffold.java:374/:394`) prüft zuerst die vorhandene
`AutomatedActionAdmission`, dann bindet es einen unveränderlichen lokalen Plan-/Sequenzbezug,
Observer-Scene und Manager. Rotation ON übergibt `"scaffold"`, OFF `null`.

Der neue Source-Check steht vor Slotwechsel und Budgetverbrauch (`Service.java:217`).
Der späte Guard (`:236`) prüft vor Controller/Packetanwendung erneut:

- aktuelle Admission, aktive Gameplaygültigkeit und Toggle;
- dieselbe Observer-Scene, denselben Manager und unveränderte Rotation-ON/OFF-Option;
- denselben Modulplan, dieselbe Framesequenz und Motionsequenz;
- passende Source-Acceptance bei ON bzw. numerische Acceptance bei OFF;
- Slot-/Stackidentität und Snapshot, Supportzustand, freie Destination, Face,
  Range und tatsächlichen Raycast aus übertragener Augenposition.

Cadence/Delay/Versuchsbudget bleiben im vorhandenen Service. `finally` (`:250`)
restauriert nur bei unveränderter Scene und Ziel-Player/World und überschreibt keine
manuelle Slotwahl. Bei Contextwechsel im Callback wird auch kein abschließender
Slot-Sync in eine Ersatzscene geschickt. Ein Settingscallback, der nur den Plan
beendet und den Owner behält, invalidiert trotzdem den laufenden Klick.
`place()==true` bleibt ausschließlich ein lokales Controllerresultat.

## Timer und MoveFix

TimerControl bleibt wertbasiert. Bei Epoch-/Generationinvalidierung vor dem realen
World-Unload werden die noch identischen alten Player-/World-/Connection-/Pipelineobjekte
und das exakt erfasste Timerobjekt geprüft; nur der eigene unverändert geschriebene
Wert wird freigegeben. Nach Objektersatz wird die Buchhaltung ohne Write verworfen.
Ein fremder abweichender Wert und ein Ersatz-Timerobjekt bleiben erhalten.
Gleichwertige Fremdwrites sind mit der bestehenden Wertpolitik nicht unterscheidbar;
SCF-1a führt keinen TimerArbiter ein.

`Scaffold.correctionEnabled` (`:411`) berücksichtigt aktive Gameplaygültigkeit und
die bestehende Scenebindung. `RotationCorrections`, `MoveFixController`, Body-Controller
und RotationManager bleiben unverändert. Die vorhandenen Bewegungs-/Jump-/Renderconsumer
lesen weiterhin bestätigten Owner und tatsächliche Effective-Winkel; die Tests prüfen
W/A/S/D/Diagonalen und Jump nach Hold zusätzlich zu den unveränderten Mathematik-/Idempotenztests.
Weitere Consumer-Migration und Disable-Smoothing sind gesonderte Folgearbeit.

Testnamen und Grenzen: [SCF_1B_HANDOFF.md](SCF_1B_HANDOFF.md).
Neu ausgeführte Zahlen, REDs und Gitnachweise: [RESULTS.md](RESULTS.md).
