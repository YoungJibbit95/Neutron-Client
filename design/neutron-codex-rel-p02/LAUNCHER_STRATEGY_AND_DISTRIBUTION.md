# REL-P02 — Launcherstrategie und Distribution

28.10.2026, Europe/Berlin. **LOCAL_ONLY / DISTRIBUTION_BLOCKED.**
Der offizielle Minecraft Launcher wird auf Nutzerwunsch nicht installiert.
Ein alternativer Launcher kann Anmeldung und Instanzen verwalten; der konkrete
Neutron-Loadervertrag bleibt ein eigenständiges offenes Projekt.

## Prism als Standardpfad

[Prism](https://prismlauncher.org/) verwaltet getrennte Instanzen, Konten und
Mods. Seine [Instanzdokumentation](https://prismlauncher.org/wiki/getting-started/create-instance/)
beschreibt die Auswahl von Minecraftversion und Modloader; die
[Javadokumentation](https://prismlauncher.org/wiki/getting-started/installing-java/)
ordnet älteren Minecraftversionen Java 8 zu und beschreibt die Auswahl pro
Instanz. Die [API-Einstellungen](https://prismlauncher.org/wiki/help-pages/apis/)
dokumentieren Microsoft-Authentifizierung. Diese Primärquellen wurden am
28.10.2026 gelesen. Prism wurde weder installiert noch gestartet; keine
Prism-Profile oder Accounts wurden gelesen.

Ein sinnvoller späterer Abnahmepfad ist: Nutzer richtet eine eigene rechtmäßige
1.8.9-Instanz mit Microsoft-Konto und Java8 x64 ein und bestätigt zunächst einen
Vanilla-Fensterstart. Danach wird ein gesondert geprüfter Neutron-Loadervertrag
in einer separaten Instanz getestet. Das ist ein Plan, kein Kompatibilitäts-PASS.
Die jetzige Assembly darf nicht einfach in den Mods-Ordner gelegt werden.

## Abgleich mit dem wirklichen Bestand

| Vertrag | Vorhanden | Noch erforderlich |
|---|---|---|
| Einstieg | Assembly ruft `net.minecraft.client.main.Main` direkt auf | Vereinbarter Launcher-/Loaderadapter, keine doppelte Main-/Spielklassendefinition |
| Klassenbestand | Direkt modifizierte MCP-1.8.9-Quellen, OptiFine L5, Forge-Dummys | Mappings, Transformationen und Loader-Lifecycle pro tatsächlicher Änderung |
| Laufzeit | Unminimierte Assembly enthält Mavenbibliotheken; Vanilla-Classpath weicht ab | Eindeutige Classloader-/Prioritätsregeln, keine zweite Vanilla-/LWJGL-/Netty-Liste anhängen |
| Natives | Windows64-JNI-Linkprobe; DLL-Inventar auch mit 32-bit-Dateien | Java-/Prozessarchitektur und gepinnte Nativeauswahl pro Instanz, Display-/Audioabnahme |
| Assets/Pfade | Main unterstützt GameDir und Assetpfade; Pulsar verwendet zusätzlich CWD/Home | Drei getrennte Laufwurzeln, legitime Assets, keine persönlichen Accountkopien |
| Auth | Main akzeptiert Sessionargumente; `Start` hat nur einen Testwert | Tatsächliche reguläre Launcher-Sitzung und Entitlement; keine Tokenextraktion |
| Modmetadaten | Kein nachgewiesener eigenständiger Forge-/Fabric-Mod | Konkreter unterstützter Loader und überprüfter Start-/Transformationsvertrag |

Callgraph: Launcher/Loader liefert vereinbarten Classpath, Java8, Natives,
Arbeitswurzeln und berechtigte Sitzung an den vorhandenen Main;
`Main -> GameConfiguration -> Minecraft.run/startGame -> Pulsar.init`.
Direkte Änderungen an `Minecraft` und OptiFine werden dabei nicht automatisch
zu Loaderhooks. `TRSRTransformation` ist im Bestand ausdrücklich ein Forge-Dummy;
seine Existenz beweist keine Forge-Integration. Das vorhandene Assembly ist
auch kein gewöhnlicher Fabric-Mod. Das sind Schlussfolgerungen aus POM,
Source-Entrypoint und Paketbestand, keine ausprobierte Prism-Integration.

## Eigenes Desktop-Launchersystem: nur Architektur

| Entscheidung | Künftiger Vertrag |
|---|---|
| Runtime | Gepinnter Java8-x64-Prozess, eindeutiger Classpath, natives/Assets und getrennte GameDir/CWD/Home |
| Anmeldung | Systembrowser und rechtmäßig registrierte öffentliche Desktop-App; kein eingebetteter Passwortdialog |
| Protokoll | Authorization Code mit PKCE, zufälliges State, validierter Redirect und Abbruch-/Timeoutgrenzen |
| Berechtigung | Xbox-/Minecraft-API-Zugang sowie Entitlement-/Profilprüfung separat klären und abnehmen |
| Credentials | Kein Clientsecret in Desktopdateien; minimale Tokens im Speicher, sichere spätere Ablage nur nach eigenem Konzept |
| Lifecycle/Privacy | Abmelden/Widerruf/Expiry, minimale Logs ohne Tokens, Konten, Chat, Serveradressen oder fremde Cacheimporte |
| UX | Anmeldung und Berechtigungsfehler verständlich anzeigen; kein Silent-Fallback auf fingierte Multiplayerdaten |

Microsoft beschreibt [Authorization Code mit PKCE für Desktopanwendungen](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)
und untersagt Clientsecrets für öffentliche Clients. Das ersetzt keine
Minecraft-/Xbox-API-Zulassung. Registration, Berechtigungen, Credential-Lifecycle
und Benutzerzustimmung sind spätere eigene Gates. In REL-P02 wurden keine
OAuth-App, Client-ID, Tokenablage, Accountmanager oder Authrequests erstellt.

## Rechte und Mod-only-Arbeit

Die [Minecraft-EULA](https://www.minecraft.net/en-us/eula) trennt eigenständige
Mods von kombinierten modifizierten Spielversionen und erlaubt deren Weitergabe
nicht. Die [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
geben keine Freigabe für das hier zusammengesetzte Spielpaket. Nach den
[OptiFine-Copyrightbedingungen](https://optifine.net/copyright) ist eine Erlaubnis
für den integrierten veränderten Bestand ebenfalls nicht belegt. Die Entscheidung
dieses Auftrags bleibt deshalb konservativ gesperrt; sie ist keine allgemeine
Lizenzberatung oder Rechtefreigabe.

Die neue JAR-Inventur steht in `final-jar-audit.json`: alle drei Ausgaben enthalten
je **2510 Minecraft-Klassen, 402 OptiFine-Klassen und 4240 assets/minecraft-
Einträge**. Die Root-GPLv3-Datei und die All-Rights-Reserved-POM-Beschreibung
klären diese Fremdrechte nicht. Fonts, Sounds, Ressourcenherkunft sowie transitive
Lizenz-/Noticepflichten bleiben zusätzlich offen.

Ein späteres Mod-only-Projekt muss ausschließlich eigenständig lizenzierte
Komponenten liefern, ihre Loader-/Transformations-/Mappingverträge gegen die
jeweilige eigene rechtmäßige Installation prüfen und OptiFine/Ressourcenrechte
separat klären. Herausfiltern von Spielklassen aus der Fat-JAR beweist weder einen
funktionsfähigen Mod noch eine zulässige Transformation. Es wurden kein solcher
Mod, keine Installer-/Versions-JSON und kein Tester-ZIP erzeugt. Die drei
Maven-JARs bleiben lokale `target`-Buildausgaben.

**STOPP nach REL-P02.** Kein anschließender Launcher-/Auth-/Distributionsauftrag
wurde begonnen.
