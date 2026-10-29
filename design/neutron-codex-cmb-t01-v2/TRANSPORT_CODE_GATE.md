# CMB-T01-v2 — Gate A PASS

28.10.2026; Neutron-Client; BASE `ce7015cb8d8cc74ef4a0b17065167ff2bb98f4d4`.
Entscheidung nach lesender Inventur und externen Originalproben, vor jedem
Produkt-/Repository-Testedit. Gate B ist zu diesem Zeitpunkt noch nicht bestanden.
Aktueller v2-Master und direkte Nutzergrenzen gelten; historische Prompts sind
Kontext, keine konkurrierenden Aufträge. Keine Subagents.

## Reproduzierter Fixturebefund

Externe Evidenz: `F:\neutron-cmb-t01-v2-quellen-c03`.
31/31 Manifestdateien stimmen in SHA-256/Bytes. Separate Master-/Reviewdateien
sind bytegleich mit ihren Archivkopien. Manifest nicht selbst signiert.
Basis sauber, exakter Pin; CMB-Mergebase `88ee497d8fe2dca16e5eaae9e48548c7d46e7050`.
Origin historisch `https://github.com/YoungJibbit95/Pulsar-Client.git`, nur gelesen.

Die externe `FixtureProbeTest` führt jeweils die einzelne Originalmethode über
JUnit Request.method einschließlich unveränderter Lifecyclehooks/Assertions aus.
Ein ausschließlich lesender normaler Packetlistener protokolliert PRE und zählt
lokal transportierte rotierende C03 in POST. Kein Cancel/Rewrite/Tick/Drain.

| Originalmethode | Tatsächlicher PRE | Originalprüfung | Strikte T05-Nachprüfung |
| --- | --- | --- | --- |
| RotationModuleBindingChecks.invalidScaffoldSupportKeepsAcceptedMovementUntilDisable | active=-1, history=-1, INVALIDATED, yaw=0.90000004 | 1/0 Failures | 1 transportierte C03 statt 0: RED |
| LegacyScaffoldTest.BindingChecks.settingChangesInvalidateThePlanButOnlyRotationsOffReleasesOwnership | active=-1, history=-1, INVALIDATED, yaw=32.100002 | 1/0 Failures | 1 transportierte C03 statt 0: RED |

F02 ist damit eigenständig nachgewiesen, nicht nur aus dem alten Patch vermutet.
Die Baseline besteht mit 567/0/0/3 aus 93 frischen XMLs. Ein vorheriger
Surefire-Start scheiterte nach erfolgreicher Kompilierung an vollem C:-Temp;
0 ausgeführte Tests. Prozesslokales F:-Temp behebt das ohne POMänderung.

Die tatsächlich vorhandene Originalsonde
`F:\neutron-scf-1b-sources\probes\ScaffoldReleaseGateProbeTest.java`
hat 4.987 Bytes, SHA-256
`7f9b3b8ba0db0ea2e525f196b683a85f1becdbd56649c6f89e18a505318c055f`.
Ihre bytegleiche externe Kopie reproduziert ausschließlich B08/B20:
Yaw 9.900001, Kamera 0, activeTick=1, GUI+Disable in echtem C03-PRE;
staleSyntheticLooks=1, accepted=true, owner=null. 1 Assertionfailure.
Die bestehende Predicate-Kontrolle besteht separat. B02/B15-Lease nicht ausgeführt.

## Fixierter realer Callgraph

```text
Minecraft.runTick
  RuntimeServices.observation().beginOuterTick()  [erste ID 0]
  Tickbase/observePlayerStep -> echter Playerbody (Ordinary/Extra; kein Withheldbody)
    EntityPlayerSP.onUpdateWalkingPlayer()
      walkingContextValid (Player/World/Handler/Pipeline/Epoch)
      RotationManager.bindConnection(PacketPipeline)
      beginMotion(Player, World, cameraYaw, cameraPitch) -> sequence++
      BUS.post(Motion PRE) -> Scaffold.onMotionEvent -> request("scaffold",30,...)
      guarded C0B sprint/sneak; Kontext erneut prüfen
      Motion cancel -> finishMotion(camera,false), return ohne Motion POST
      RotationManager.resolve(EventMotion) -> tatsächlicher Winner ins Event
      [v2: konkreten Resolveframe festhalten; gebundenes Ticket erfassen]
      C03/C04/C05/C06 bauen
      NetHandlerPlayClient.addToSendQueueTracked(Packet, Predicate<Packet<?>>)
        NetworkManager.sendPacketGuarded
          PacketPipeline.outboundSelected
            drainOutbound -> Packet PRE -> final selected/cancel -> activity/scene
            ggf. bestehende PacketDelays-FIFO oder Actionbarriere
            apply: activity/scene && validAction.test(selected)
              transport -> observeAcceptedOutbound -> Packet POST
      finishMotion(beobachtete effektive Pose, accepted)
      Reportingfelder nur bei Acceptance -> Motion POST
  finally observation.endOuterTick()
```

Die Predicateauswertung erfolgt bei Apply nach PRE, bei Delay erst beim späteren
Apply, nicht garantiert zusätzlich vor dem Enqueue. Delay liefert synchron null.
Ein fertiger Frame soll nie durch spätes Replay wieder akzeptiert werden.
Keine neue Pipeline, Clock, Tick-, Budget- oder Inputinstanz.

## APIbefunde und Entscheidungen 1–9

1. `request/offer(Request)` arbitriert 30/20/10 und lexikalische Gleichstände.
   `resolve(EventMotion)` setzt Winnerpose; `getConfirmedOwner` bezeichnet dagegen
   PRE-Vorbesitz bzw. POST-Acceptance. Ticket bindet den konkreten gewählten Request
   und einen eindeutigen Resolveframe, niemals den alten Owner. Erste Acceptance
   ohne Vorbesitzer bei ID 0 bleibt möglich; Pending ohne Resolve erzeugt kein Ticket.
2. Nur exakt `scaffold` im aktuellen Resolve aktiviert den Guard. Kein Winner,
   Vanilla, Kamera, Aura und AimAssist bleiben neutral. C0B, C02, C08, C0E,
   manuelle Click-/Inputpfade werden nicht global gegatet. Alle sourced Frames
   ohne gültigen Kontext erhalten DENY, keinen neutralen Ersatz.
3. `beginMotion` invalidiert den vorherigen Resolveframe; `resolve` zeichnet erst
   nach tatsächlicher Eventübernahme einen eindeutigen Frame auf. Der Caller
   erfasst unmittelbar danach das immutable Ticket, vor C03-Bau. Dessen Getter
   führt keinen zweiten Resolve und keine Rotation aus. Der konkrete Paketenvelope
   wird vor Sendung unveränderlich im Predicate gebunden.
4. Bindung an Managerinstanz, Requestidentität, Resolveorigin, Motionsequenz,
   collecting-Phase, Player/World/Handler/NetworkManager/Pipeline, kanonischen
   Observer, aktive Outer-ID >=0, gültige ACTIVE Scene, Generation und Epoch.
   Full-/Source-Reset, Begin/Finish, Observerreset/-wechsel, Death, Shutdown und
   echte Objekt-/Epoch-/Generationswechsel entziehen alte Rechte. Identitäts-ABA
   wird durch bestehende Lifecycleinvalidierung bzw. beobachtete Generation
   erkannt; keine unbeobachtbaren beliebigen Feldwrites als neue Garantie.
5. Später Guard wird ausschließlich an vorhandenen tracked-Predicate-Caller
   übergeben. GUI/Pause/Fokus/Disable nach Resolve werden beim Apply geprüft.
   Kein Ersatz-C03, keine Camerarettung oder zusätzliche Events.
6. Final selected ist maßgeblich. Andere Paketklasse, Bewegungs-/Rotationsflags,
   Position oder Groundstatus dürfen den alten Envelope nicht erben. Identische
   C03-Klasse mit äquivalenter Pose darf ersetzen. Bestehende Wrapped-Yaw- und
   Pitchgrenze jeweils strikt <0.001 sowie +/-180-Äquivalenz bleiben erhalten.
   Nichtrotierende C03 benötigt die zum Capture gebundene berichtete Wirepose.
   Numerisch äquivalenter Rewrite ist nach bestehendem Vertrag kein beweisbarer
   fremder Sourcewechsel. Keine Serverannahme aus lokalem Transport.
7. Denial -> finishMotion false, kein POST-Aktionsrecht. Reentrantes Begin/Walking
   darf den älteren Caller nicht einen neueren Frame finishen/reporten lassen.
   Delay/Hold gewährt synchron nichts und kann nach Finish nicht reaktivieren.
8. Reine Ticketzustands-/Winkel-/IDprüfungen schreiben keine Spiel-/Quota-/Manager-
   Daten. `captureScene/isCurrent` sind ausdrücklich Scene-Sampling mit interner
   Weakreferenz-/Generationsbuchhaltung, keine reinen Observergetter. Sampling
   wird separat dokumentiert; nach einem potentiell reentranten Sample werden
   Frame/Observer/ID erneut geprüft. Keine catch-and-allow-Ausnahme. Synchroner
   PRE-/Validierungsthrow propagiert vor Transport; vorhandenes Observerfinally
   schließt Step/Tick. Kein künstliches Motion POST nach Throw. Pump/Drain haben
   vorhandene Failure-/Close-finally-Pfade; diese bleiben unverändert.
9. Keine Release-Lease, 3-Tick-Zählung, 200-ms-Deadline, Disableglättung oder
   Aura-/MoveFix-/Scaffold-/NET-Erweiterung. H1-06 bleibt offen.

`Scaffold.setToggled(false)` setzt Toggle vor onDisable; clearState/endRotation
resetten die passende Source und geben Ressourcen frei, dann Unsubscribe.
`ScaffoldPlacementService.placeWithAuthority` prüft Source vor Budget/Slot und
erneut in seinem späten Actionguard; Scene-/Slotcleanup bleibt im finally.
Diese Stellen und RuntimeObservation/SceneContext werden nicht verändert.

## Vollständige Inventur der direkten Testaufrufer

Externe `direct-walking-inventory.txt` enthält alle Fundstellen des Basisstands.

| Aufruferfamilie | Source/Observer/Assertion und Entscheidung |
| --- | --- |
| RotationModuleBindingChecks.Setup.tick | Aura oder sichtbares AimAssist, keine neue Scaffoldpolicy; unverändert |
| RotationModuleBindingChecks F01 | sourced, kein Outer, verlangt Scaffoldowner/MoveFix; exakt ein Aufruf migrieren |
| LegacyScaffoldTest.BindingChecks F02 | sourced, Before/BeforeClass eröffnen keinen Outer, verlangt Scaffoldowner; exakt ein Aufruf migrieren |
| ScaffoldClientFlowTest.ordinaryWalking | public static, echter begin/observePlayerStep/finally-end; bereits gültig |
| ScaffoldClientFlowTest.Diagnostics | keine Blöcke, kein Scaffoldrequest, nur Diagnosezahlen; unverändert |
| ScaffoldOwnerContinuityTest.a09 | Outer bereits aktiv, Extra bzw. no-Step; Rotation braucht kein Placementbudget; unverändert |
| RuntimeObservationHookTest | echter Beobachtungs-/Vanillapfad, Walking innerhalb der geprüften Bodys; unverändert |
| NoSlowSprintChecks | keine aktive Scaffoldrequestquelle; Item-/Sprintregression; unverändert |
| SprintOrderingChecks | prepare setzt Scaffold-Rotations=false; C0B/neutral, Sonderfälle Cancel/Kontext; unverändert |
| AutomatedActionModuleTest | AutoHeal/Sprint/NoSlow/Actionbodys, keine anzunehmende Scaffold-C03; unverändert |

F01/F02 verwenden unmittelbar `ScaffoldClientFlowTest.ordinaryWalking(f)` statt
des direkten Aufrufs. Derselbe Player, dieselbe World/Connection/Pipeline und
der kanonische Runtimeobserver; erste ID 0 zulässig. Genau ein Walkingbody,
keine TickEvents, kein zusätzlicher Service-beginTick, kein Clockadvance und
keine Packetdrainage im Helper. Keine Produktionsänderung an Minecraft/Observer.
Der Helper ist bereits public; ScaffoldClientFlowTest bleibt unverändert.
Originalassertions, Toleranzen, Tests/Wrapper und Paketfolge bleiben erhalten.
Neue negative T05-Proben bleiben getrennt von dieser positiven Fixturemigration.

Alle erforderlichen Schreiborte passen in die explizite v2-Allowlist.
Gate A PASS; erst jetzt sind Fixture-first und Test-first Gate B freigegeben.
