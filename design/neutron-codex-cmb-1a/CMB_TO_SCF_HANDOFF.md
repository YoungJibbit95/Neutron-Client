# CMB-1a → SCF — Übergabe ohne Scaffold-Implementierung

Stand: 28.10.2026. Basis `3554f7a9545fbc03b91fbdf9af453ba851bf0f2c`.
CMB-1a ergänzt ausschließlich `RotationManager.canUseRotation(String, boolean)`
und die interne Getterdelegation. Kein bestehender Consumer wird migriert.
Details: `CMB_OWNER_CONTRACT.md`; Ausführungsnachweise: `RESULTS.md`.

## Verfügbare Accepted-/Owner-Prüfung

```java
// Im Motion PRE: vorheriger lokal akzeptierter Frame, vor aktuellem Resolve/C03.
boolean previousScaffold = rotation.canUseRotation("scaffold", true);
// Im Motion POST: aktueller lokal akzeptierter Frame, nach finishMotion.
boolean currentScaffold = rotation.canUseRotation("scaffold", false);
```

Das sind Beispiele für spätere Consumer, keine bereits eingefügten Call-Sites.
Die neue Methode gilt nur für die aktuell verfügbare Phase (`pre == collecting`).
Sie prüft Source, bestätigten Owner und numerische Accepted-Basis gemeinsam.
Ein Pending-Request, Wunschzustand, alter gültiger Winkel oder Paket-Hold ersetzt
keine Acceptance. PRE nach Finish hat keinen historischen Owner-Snapshot.
`getConfirmedOwner(player,world,connection)` behält seine bestehenden Bindungschecks;
die neue Methode führt keine eigene Scene-/Tickhistorie oder Reservation ein.

Die nachgeprüfte Reihenfolge in `EntityPlayerSP.java` L219/L222/L264/L304/L330 ist:

```text
Physik → beginMotion → Motion PRE → Scaffold-Plan/ggf. PRE-Placement
       → aktueller Request → resolve → C03 lokal angenommen/abgelehnt
       → finishMotion → Motion POST → ggf. POST-Placement
```

Scaffold PRE-Placement L177 liegt ausdrücklich vor `applyRotations` L179 und vor
dem aktuellen C03. Es darf keine erst später akzeptierte Rotation verwenden.

## Weiterhin offene konkrete Kopplungen

Alle folgenden Produktionsdateien bleiben unverändert:

| Nachgeprüfter Caller | Noch bestehendes Verhalten / spätere Arbeit |
| --- | --- |
| `module/impl/world/Scaffold.java` L129 `releasePlacement()` | Ruft `rotation.reset("scaffold")` und Correction-Reset auf; löscht Plan/Sequenz, Downwards/Tower und gibt den eigenen Timer frei. Planende und Ownerfreigabe müssen getrennt werden, ohne Tower-/Timer-/Slotcleanup zu verlieren. |
| Scaffold L156/L165/L173/L177/L184/L185/L198 | Fehlender/ungültiger Plan und Platzierungserfolg können freigeben. Der RotationManager kann ohne weitere gültige Scaffold-Requests diese Priorität nicht eigenständig verlängern. |
| Scaffold L218 `applyRotations` | Fordert nur mit aktivierten Rotationen und aktuellem Plan an; sonst Source-Reset. Für das Produktziel braucht es fortgeführte gültige Requests auch zwischen Plänen, ohne erfundene erste Acceptance. |
| Scaffold L111/L129/L147/L194 | Disable sowie ungültiger Kontext führen heute direkt zum Cleanup. GUI-Suspension und echte Scene-Invalidierung sind noch nicht fachlich getrennt. |
| `core/world/ScaffoldPlacementService.java` L200/L217/L226 | Prüft weiterhin numerisches `canUseRotation(pre)` sowie Sequenz, Identität, Slot/Stack, Geometrie, Raycast, Reichweite, Cadence und späten Controllerguard. Source-Migration noch offen. |
| `core/combat/RotationCorrections.java` L28/L43/L64/L75/L85 | Nutzt Confirmed-Owner mit Bindung und tatsächliche Effective-Winkel; spätere Migration muss am jeweiligen Bewegungs-/Sprung-/Renderconsumer Phase und Scene berücksichtigen. MoveFix-Mathematik bleibt erhalten. |
| `module/impl/combat/Aura.java` POST-Listener und `validHit` | Alte `isOwner`-Abfragen werden nicht pauschal als neue Authority interpretiert oder ersetzt. Admission, aktuelle Accepted-Pose und finaler Paket-/Rayguard bleiben zusätzlich nötig. |

## Anforderungen an die nächste separate SCF-Welle

1. **Planende ≠ Ownerfreigabe:** Scaffold hält im gültigen aktiven Kontext Vorrang
   vor konkurrierenden Aura-Requests bis Disable, auch ohne aktuelles Ziel.
   Die letzte gültig akzeptierte Basis darf geglättete neue Requests unterstützen;
   bloßes Behalten des Owner-Strings erneuert keine Acceptance.
2. **GUI/Pause/Fokus-Suspend:** Logischen Wunsch erhalten, aber keine automatische
   Platzierung oder synthetische Inputwirkung. Sceneverlust durch Player/World/
   Connection/Packet-Epoch invalidiert sofort. Kein Restore in eine neue Scene.
3. **Retry/Resume:** Player, World, Connection, Epoch/Generation, Slot/Stack, Plan,
   Geometrie und passend akzeptierte Rotation frisch prüfen. Ein alter Wunsch oder
   numerisch gleicher Winkel erteilt kein Placementrecht.
4. **Disable:** Keine neuen Place-/Inputaktionen; sanfter Rotation-Auslauf innerhalb
   der beschlossenen 2–4 Ticks, anschließend harte Owner-Endgrenze. Bei invalidem
   Kontext sofort abbrechen. Zählbasis bei Withheld, fehlendem Body und Pause
   ausdrücklich festlegen; CMB-1a wählt dafür keinen Default.
5. **Rotation On:** Späteres Placement verlangt bestätigte Source `"scaffold"` für
   die richtige Phase, zusätzlich zu allen bestehenden Fach-/Transportguards.
   **Rotation Off/Camera-View:** Gesondert gültig akzeptierte Kamera-/Vanilla-Pose
   erhalten. Den alten numerischen Aufruf bis zur bewussten Migration beibehalten;
   ein globales Source-Gate würde den gewollten unbesitzten Kamerapfad ändern.
6. **MoveFix:** Sourceprüfung und tatsächlich akzeptierte Basis am realen Consumer
   zusammenführen. `MoveFixController.correct/jumpYaw`, physische Eingaben,
   Idempotenz und Vanilla-Stärkebegrenzung unverändert bewahren. Keine frühere
   `isOwner`-Abfrage ohne Phase-/Sceneprüfung als alleinige Authority verwenden.
7. **Automationsgrenzen:** I01c-Admission schützt Ordinary/Extra/NO_STEP und
   manuelle Controllerpfade weiterhin. I01e schützt den aktiven Observer-Tick und
   AutoHeal-Start. Weiterlaufende Rotation darf keine zusätzliche Placement-/Attack-
   oder Inventaraktion pro Extra-Step erlauben. Gültige Owner-Pose allein ist
   ebenfalls kein Aktionsbudget und kein Nachweis eines Servererfolgs.

## Tests, die bewusst noch nicht das neue SCF-Ziel beweisen

`ScaffoldPlacementTest.rotationsOffStillRequiresActualAcceptedView` bleibt unverändert
und schützt den Kamera-Fallback. Der ebenfalls unveränderte Test
`scaffoldPriorityIsReleasedToAuraAfterTargetLossOrDisable` schreibt das heutige
Release-Verhalten fest. Er darf erst in der separaten SCF-Welle zusammen mit dem
ausdrücklichen Verhaltenswechsel migriert werden, nicht gelöscht/abgeschwächt werden.
Auch `RotationModuleBindingChecks` prüft heute Freigabe bei verlorenem Support.

`AutomatedPlacementAdmissionTest.preUsesPreviousAcceptedAngleAndPostUsesCurrentAcceptedAngle`,
`pendingFirstRotationIsNotAcceptanceAndCancelledPostCannotPlace`,
`extraDenialConsumesNoSlotBudgetOrSuccessAndOrdinaryCanStillPlace` sowie
`AutomatedActionModuleTest.deniedScaffoldExtraStillComputesRotationsButNeverPlacesOrSwitchesSlot`
bleiben konkrete vorhandene Schutzanker. Sie beweisen noch keinen Owner-Hold.

Die AC-R02/H1-Fälle bleiben **Testvorlagen**, keine sieben hier bestandenen SCF-Tests:

| Vorlage | Noch separat abzunehmen |
| --- | --- |
| H1-01 | Planlücke mit fortbestehender Scaffold-Priorität ohne neue Platzierung |
| H1-02 | Aura-Konkurrenz bei Aktivierung und Planlücke |
| H1-03 | PRE/POST mit passenden Ownern und Drop/Rewrite/Delay im migrierten Placementpfad |
| H1-04 | Slot/Raycast/Admission und unveränderte Ordinary-/Extra-Budgets |
| H1-05 | Suspend/Resume ohne automatische Place-/Inputwirkung während Pause |
| H1-06 | Disable-Auslauf 2–4 Ticks mit garantiertem Ende, auch bei Withheld/Pause |
| H1-07 | Harte Death-/World-/Connection-/Epochgrenze und Exceptioncleanup |

CMB-1a liefert vorgelagerte Owner-/Accepted-Regressionen für Teile von H1-02/03/07,
aber keine vollständige SCF-Integration. H2-Swing-/Trace-Arbeit bleibt außerhalb des
Auftrags. Keine Grad/s-, Winkelprofil-, CPS-, Noise-, GCD-, Config- oder Timeränderung;
kein Fremd-Anticheat-Code und keine universelle Flagfreiheitsbehauptung.
**STOPP nach CMB-1a.**
