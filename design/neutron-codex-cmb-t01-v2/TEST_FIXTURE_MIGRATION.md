# CMB-T01-v2 — eng begrenzte Fixturemigration

28.10.2026. Gate-A-Freigabe und externe Original-REDs stehen im Gatebericht.
Nur die zwei nachgewiesenen direkten Walking-Aufrufe wurden ersetzt. Keine
weitere Zeile der beiden Bestandsdateien wurde fachlich geändert.

## F01

`RotationModuleBindingChecks.invalidScaffoldSupportKeepsAcceptedMovementUntilDisable`:

```diff
-            f.player.onUpdateWalkingPlayer();
+            ScaffoldClientFlowTest.ordinaryWalking(f);
```

Die vollständige Datei vor der Migration ist extern als
`RotationModuleBindingChecks.before.java` gesichert, SHA-256
`457cda030cb3c8b87426b9e262c31e5e60736c128c716777828333ad2e758fcb`.
Alle 109 erfassten Assertionausdrücke und 19 Testmethoden der Datei sind
textidentisch; Owner, Planverlust-Hold, heldYaw-basierte MoveFixwerte,
Disable-Release, Kamerafallback und Stack=64 einschließlich aller Toleranzen
bleiben unverändert. Auch der Prozesswrapper bleibt unverändert.

## F02

`LegacyScaffoldTest.BindingChecks.settingChangesInvalidateThePlanButOnlyRotationsOffReleasesOwnership`:

```diff
- f.runtime.getScaffoldPlacementService().beginTick();f.player.onUpdateWalkingPlayer();assertNotNull(current(f));
+ f.runtime.getScaffoldPlacementService().beginTick();ScaffoldClientFlowTest.ordinaryWalking(f);assertNotNull(current(f));
```

Vorherdatei `LegacyScaffoldTest.before.java`, SHA-256
`295f1c3839300bf5ce6e5655e2ef4ad6f28333edb0c5bd99bbafdd32e2da3e57`.
Alle 22 erfassten Assertionausdrücke und 5 Testmethoden (einschließlich innerer
BindingChecks) sind textidentisch. Plan vorhanden, Settinginvalidierung,
bestätigter Scaffoldowner, Rotation-OFF-Release und kein platzierter Zielblock
bleiben unverändert. Der vorhandene Service-beginTick wird nicht verdoppelt.

`verify_fixtures.py` prüft strenger als nur einzelne Assertions: Der gesamte
normalisierte Dateiinhalt muss exakt dem Vorherstand mit genau dieser einen
Walking-Substitution entsprechen. Receipt mit vollständigen Assertions,
Methodennamen und Vorher-/Nachherhashes: `fixture-assertions-verification.json`.

## Echter Lifecycle statt Testausnahme

Der bereits öffentliche `ScaffoldClientFlowTest.ordinaryWalking(Fixture)`:

```text
f.runtime.getObservation().beginOuterTick()
try
  observePlayerStep(f.player,
    simulation = original onUpdateWalkingPlayer exactly once,
    decision = execute original simulation once)
finally endOuterTick()
```

Er verwendet dieselben aktuellen Player-/World-/Connection-/Pipelineobjekte
wie der Walkingbody. Er erfindet keine Produktionstaktung, keine Ersatzscene,
keinen Fakeowner und kein Acceptedflag. Erste echte ID 0 ist gültig. Genau ein
Step; kein TickEvent, Clockadvance, Servicecadencewechsel oder zusätzlicher
Paketdrain. Das Observer-Sampling stellt lediglich den gültigen Testkontext her.
Der Helper brauchte keine Sichtbarkeitsänderung; seine Datei bleibt bytegleich
zur Basis. POM, RuntimeObservation und Minecraft bleiben unverändert.

Vor jedem Produktedit bestehen beide migrierten Legacyklassen:
`fixtures-before-production`: 4 Surefirefälle, 0 Failures/Errors/Skips, 2 XMLs.
Der Rotationwrapper führt separat seine 19 inneren Checks aus, der Legacywrapper
seine 2 Bindingchecks; diese werden nicht zu den Surefirezahlen addiert.
Der unveränderte Produktcode ist dabei noch für T01/T05 fehlerhaft.

## F03 / F04 und Abnahmegrenze

Die ursprünglichen externen F01-/F02-Proben beweisen jeweils: active=-1,
INVALIDATED, unveränderte Originalassertions bestanden, eine synthetische C03
transportiert; die zusätzliche strikte T05-Assertion scheitert real.
Die neue dauerhafte negative T05-Prüfung ist davon getrennt und verlangt unter
v2 weiterhin Denial. `t04FirstRequestAtRealOuterIdZeroConfirmsOnlyAfterTransport`
prüft den gültigen ersten Frame einschließlich echtem Playerstep und erst
anschließendem bestätigtem Owner. Ein grüner migrierter Legacytest allein ist
kein Nachweis einer T05-Sperre.

F04: genau zwei bestehende Testdateien mit je einer Aufrufsubstitution. Keine
Assertions/Zahlen/Toleranzen geändert, keine neuen Assertionen in Bestandsdateien,
keine Tests gelöscht, übersprungen oder umbenannt. Alle weiteren neuen Prüfungen
stehen ausschließlich in den beiden ausdrücklich erlaubten neuen Testdateien.
T01–T18-Nachweise und endgültige XMLzahlen stehen in Vertrag und RESULTS.
