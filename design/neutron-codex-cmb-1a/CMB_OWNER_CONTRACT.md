# CMB-1a — Bestätigter Rotation-Owner

Stand: 28.10.2026. Geprüfte lokale Basis: `codex/neutron-core-i01e`,
`3554f7a9545fbc03b91fbdf9af453ba851bf0f2c`. Maßgeblich sind der ausdrücklich
beauftragte CMB-1a-Prompt, v1.0 §§2.3/3.1/7.3/9.2 und der tatsächliche Java-Code.
Die ZIP-Berichte dienen als Kontext; ältere R01-Skizzen erweitern den Auftrag nicht.

## API und Invarianten

```java
public boolean canUseRotation(String source, boolean pre)
```

Die Methode bestätigt ausschließlich einen exakt passenden, nicht leeren Source-Namen
für die aktuell verfügbare Phase. `null`, leer und ausschließlich `trim()`-Whitespace
sind ungültig. Groß-/Kleinschreibung und führende Zeichen werden nicht normalisiert.
Es gibt keine feste Modulregistry: Eine andere tatsächlich akzeptierte Request-Source
kann ebenfalls Owner sein; ein unbekannter Name ohne passenden Owner bleibt false.

PRE (`pre=true`) ist zwischen `beginMotion` und `finishMotion` verfügbar und benötigt
`previousAccepted`, `hasEffective`, endliche effektive Winkel und denselben bestätigten
`owner`. Ein aktueller Request oder ein bereits aufgelöster `winner` genügt nicht.
POST (`pre=false`) benötigt nach `finishMotion` entsprechend `accepted` und den
aktuellen Owner. Der Getter `getConfirmedOwner()` delegiert an dieselbe Prüfung mit
`pre=collecting`; dadurch bestätigt er auch keinen leeren/Whitespace-Owner.
Seine bestehenden Player-/World-/Connection-Overloads behalten ihre Identitätsguards.

Die neue Prüfung verlangt zusätzlich `pre == collecting`. Grund: `finishMotion`
überschreibt effektive Winkel und Owner, lässt aber `previousAccepted` stehen.
Der illustrative Kandidat `source.equals(owner) && canUseRotation(pre)` könnte
danach den **aktuellen Aura-POST-Owner als vorherigen PRE-Owner** autorisieren.
C03 prüft diesen Gegenfall. PRE-Abfragen nach POST werden deshalb abgelehnt;
es wird kein historischer Owner-/Winkel-Snapshot eingeführt. POST während PRE ist
ebenfalls false. Für die jeweils aktuelle Phase gilt:

```text
canUseRotation(source, collecting)
    == (source ist gültig && source.equals(getConfirmedOwner()))
```

Die alte Methode `canUseRotation(boolean pre)` bleibt unverändert numerisch.
Ein unbesitzter akzeptierter Kamera-/Vanilla-Frame ist numerisch nutzbar, aber
berechtigt keine Source. Dasselbe gilt nach `reset("scaffold")`, obwohl Winkel,
`hasEffective` und Accepted-Flags erhalten sein können. `isOwner(String)` bleibt
die alte grobe Abfrage; sie allein ist kein phasenrichtiges Aktionsrecht.

## Tatsächliche Schreib- und Resetpunkte

Alle Angaben beziehen sich auf `src/main/java/me/youngjibbit/core/combat/RotationManager.java`.

| Zustand | Tatsächliche Änderungen / Anker |
| --- | --- |
| `collecting` | `beginMotion(float,float)` L57 setzt true; `finishMotion` L102 setzt false, auch bei Reject. `reset()` setzt dieses Feld ausdrücklich nicht zurück. |
| `accepted` / `previousAccepted` | Begin L64 übernimmt accepted nach previousAccepted und setzt accepted false. Finish L104 setzt accepted nur bei lokaler Annahme, endlichen Winkeln und Pitch innerhalb [-90,90]. Invalides Observe L154 löscht beide; voller Reset L313 löscht beide. Source-Reset verändert sie nicht. |
| `hasEffective` | Anfang false; akzeptiertes Finish L106 und jedes Observe L163 setzen true. Ungültiges Observe behält seine tatsächlichen ungültigen Winkel; voller Reset setzt hasEffective false. |
| `effectiveYaw/Pitch` | Akzeptiertes Finish L107 übernimmt finalen Yaw und geklemmten Pitch; Observe L164 übernimmt tatsächlich beobachtete Werte. Reject und Source-Reset behalten Zahlen. Voller Reset löscht die Nutzbarkeit, nicht die Zahlen. |
| `baseYaw/Pitch` | Begin L59–62 nimmt nur endliche effektive Basis mit gültigem Pitch, sonst Kamerawerte. Keine Änderung an Smoothing/GCD/Noise. |
| `winner` | Begin löscht; `offer` L78 wählt nach bestehender Priorität und lexikalischem Tie-Break. Visible-Resolve kann verwerfen/aktualisieren. Source-Reset L310 löscht passenden Winner; voller Reset löscht jeden Winner. Finish lässt ihn als Diagnose stehen; er allein ist kein bestätigter Owner. |
| `owner` | Akzeptiertes Finish L109 bestätigt nur den gewählten Winner bei Yaw-Wrap- und Pitch-Abweichung jeweils <0.001°. Ohne passenden Winner wird owner null, auch bei identischen alten Kamerawinkeln. Reject kann den alten Feldwert behalten, die Accepted-Prüfung entzieht dennoch die Berechtigung. Observe löscht bei neuer abweichender oder ungültiger Pose. Beide Resetvarianten löschen passend bzw. vollständig. |
| `motionPlayer/World` | Schwache Identitätsreferenzen L45; gebundenes Begin L48 führt bei Wechsel zuerst vollen Reset aus und bindet neu. Voller Reset leert beide. Ungebundenes Begin sampelt keine Szene. |
| `connection` | Schwache Referenz L38. `bindConnection` L39 resettet nur beim Identitätswechsel. `invalidateConnection` L43 resettet/leert nur die passende Connection. Voller Reset allein leert nicht die Connection-Referenz. |
| `sequence` | Nur Begin inkrementiert L67; weder Getter noch Reset erzeugen einen Schritt. |

Die Methode liest nur vorhandenen Zustand und löst keine Aktion, Szeneabfrage,
Tickhistorie, Paketübertragung oder Zustandsänderung aus. Es entstehen keine neuen
Ownerfelder, Manager, Threads oder Locks. Der Client-Thread-Vertrag bleibt bestehen.
Der Aufrufer muss den vorhandenen Binding-/Invalidierungslebenszyklus einhalten;
der parameterlose Kontext wird hier nicht durch eine neue Live-Szeneprüfung ersetzt.

## Tatsächliche Phasen- und Transportkette

`EntityPlayerSP.onUpdateWalkingPlayer` bindet Connection/Player/World L219–220,
postet Motion PRE L222, resolved erst L264 und beendet die Bewegung L304 nach
lokaler Transportannahme mit den tatsächlich beobachteten Winkeln. Motion POST
folgt L330. PRE liegt nach Physik, vor dem aktuellen C03; Scaffold kann bereits
L177 vor seinem aktuellen `applyRotations` L179 platzieren.

`PacketPipeline.outboundSelected` L64 verweigert bei Cancel/Delay bzw. ungültigem
Kontext und meldet nur lokale Annahme. `NetworkManager.configureClientPackets`
L169 und `NetHandlerPlayClient.observeAcceptedOutbound` L870 beobachten auch
Replays/andere C03-Sendungen vor Paket-POST. Abweichende Replays können Ownership
sofort verlieren lassen; ein späteres Zurückkehren zu den alten Zahlen stellt sie
nicht wieder her. Die vorhandene Toleranz und ±180°-Äquivalenz bleiben unverändert.
Eine numerisch äquivalente Beobachtung allein beweist keinen extern erkennbaren
Ownerwechsel; es wird keine neue Paketidentitäts- oder Serverbestätigung behauptet.

## Regressionen C01–C12

Neue Klasse: `src/test/java/me/youngjibbit/core/combat/RotationOwnerPredicateTest.java`.
Die tatsächlichen Laufzahlen und XML-Nachweise stehen in `RESULTS.md`.

| ID | Konkrete Testmethoden / Grenze |
| --- | --- |
| C01 | `c01FreshManagerHasNoOwnerForEitherPhaseOrInvalidSource`: keine erste Acceptance, null/leer/fremd, beide Phasen. |
| C02 | `c02OnlyTheAcceptedSourceOwnsPost`: begin/request/resolve/finish, nur Scaffold, Confirmed-/numerische APIs stimmen überein. |
| C03 | `c03PreKeepsPreviousScaffoldDespitePendingAuraAndPostChangesOwner`: alter Scaffold-PRE, Pending Aura, danach Aura-POST; zusätzliche Ablehnung der falschen PRE-Abfrage nach POST. |
| C04 | `c04FirstPendingAndResolvedRequestCannotAuthorizePre`: Pending und Resolve ohne erste Acceptance gewähren nichts. |
| C05 | `c05RejectionDeniesPostAndTheFollowingPreDespiteOldEffectiveAngles`; zusätzlich `actualPacketCancellationAndDelayCannotGrantPostOrSubsequentPreRights`: echter Cancel/Hold/Replay über bestehende Pipeline. |
| C06 | `c06MismatchedAcceptedYawOrPitchCannotInheritOldOwnership`, `c06MatchingAnglesBelongOnlyToTheActuallySelectedWinner`; zusätzlich tatsächlicher Paket-Rewrite. |
| C07 | `c07SourceResetRemovesRightsButPreservesLegacyAcceptedNumbers`: PRE/POST, fremder Reset schont Scaffold, eigener Reset entzieht Source trotz numerischer Nutzbarkeit. |
| C08 | `c08ChangedOutboundAnglesLoseOwnerInPreAndPostAndCannotRestoreIt`, `c08InvalidOutboundAnglesRevokeBothAcceptedPhases`; zusätzlich `actualPacketRewriteAndExternalReplayRevokeTheAcceptedOwner`. |
| C09 | `c09ConnectionReplacementInvalidationAndResetNeverRestoreSourceRights`, `c09PlayerAndWorldBindingsInvalidatePreIncludingReturningToOldScene`: Identitätswechsel, passende/fremde Invalidation, A→B→A und vorhandene Getterguards. |
| C10 | `c10ExistingPrioritiesChooseScaffoldInEitherOrderButRequireRenewedRequests`: bestehende Konstanten, beide Reihenfolgen, keine implizite Erneuerung bei Kameraframe ohne Request. |
| C11 | `c11WrapClampAndExistingMatchingToleranceStayIntact`, `c11InvalidPacketAnglesAndInvalidRequestsCannotCreateOwners`: ±180°, Clamp auf ±90°, innerhalb/außerhalb 0.001°, NaN/Infinity/ungültiger Pitch. |
| C12 | `c12RepeatedQueriesLeaveEveryManagerFieldAndTransportUntouched`: je 100 Wiederholungen in POST/PRE/Reject, Reflection-Snapshots aller Managerfelder einschließlich Winnerfelder/Map-Inhalte/Weak-Referenten; kein Outboundpaket. |

Zusätzliche eigenständige Fälle: `unownedAcceptedCameraFrameRemainsNumericallyUsable`,
`emptyOrWhitespaceWinnerIsNeverAConfirmedSource` und
`sourcesAreExactRequestIdentifiersRatherThanAHardcodedModuleRegistry`.
Die Testfixture für echten Transport stammt unverändert aus
`NetworkMoveFixIntegrationTest.Fixture`; alle neuen Tests bleiben im freigegebenen
Combat-Testpfad. Bestehende Assertions werden nicht verändert.

## Verbleibende Grenzen

Die API allein hält Scaffold nicht bis Disable. Planfreigabe, Suspend/Resume,
Auslauf, Placement- und MoveFix-Migration bleiben im separaten SCF-Handoff offen.
I01c-Admission und I01e-Observer/AutoHeal bleiben unverändert. Die synthetischen
JUnit-/EmbeddedChannel-Tests liefern keine Spiel-, Server-, Anticheat-, native
Input-, GL-, Shader-, Performance- oder Releaseabnahme. Numerische Produktgates
und Fremdcodeübernahme werden durch CMB-1a nicht freigegeben.
