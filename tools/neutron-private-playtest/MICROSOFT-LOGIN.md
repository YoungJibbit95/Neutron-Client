# Microsoft-Browserlogin: Voraussetzungen und Abnahme

Microsoft-Anmeldungen verwenden ausschließlich den Standardbrowser.
`me.youngjibbit.account.MicrosoftBrowserAuth` führt Authorization Code mit
S256-PKCE, zufälligem State, einem kurzlebigen Loopback-Callback und anschließend
Xbox Live, XSTS, Minecraft Services und die Java-Profilprüfung aus. Browser-Cookies
und Passwortmanager bleiben im Browser; der Client liest sie nicht aus.
Der frühere OpenAuth-/JavaFX-Anmeldeweg wurde entfernt.

Für eine echte Microsoft-/Minecraft-Anmeldung ist eine eigene registrierte
Anwendung erforderlich. Dieses Repository enthält keine freigegebene Neutron-
App-ID. Bei fehlender oder ungültiger Konfiguration öffnet sich Microsoft setup.
Lokale HTTP-Fixtures und Paketprüfungen beweisen keine Live-Freigabe durch
Microsoft oder Minecraft Services.

## Anwendung konfigurieren

1. Im [Microsoft Entra Admin Center](https://entra.microsoft.com/) eine Anwendung
   für persönliche Microsoft-Konten registrieren.
2. Plattform **Mobile und Desktopanwendungen** mit dem Redirect
   `http://localhost/callback` konfigurieren. Der Port ist beim localhost-Redirect
   dynamisch; der Callback-Pfad muss übereinstimmen. Kein Client-Secret im Client.
3. Die öffentliche **Application (client) ID** in Microsoft setup eintragen.
   Microsoft-/Minecraft-Dienstzulassung separat durch den App-Inhaber prüfen;
   die Entra-Registrierung allein beweist sie nicht.

Die Konfiguration gilt unabhängig vom Arbeitsverzeichnis. Vorrang haben
`-Dneutron.microsoft.clientId`, danach die ältere Property
`-Dpulsar.microsoft.clientId`, dann `NEUTRON_MICROSOFT_CLIENT_ID` beziehungsweise
`PULSAR_MICROSOFT_CLIENT_ID`. Ohne Override wird
`${user.home}/.neutron/microsoft-auth.properties` mit dem Schlüssel `clientId`
gelesen, danach eine gegebenenfalls eingebettete `neutron-microsoft.properties`.
Microsoft setup speichert ausschließlich die öffentliche App-ID. Ein abweichender
JVM-/Umgebungs-Override muss zuerst korrigiert oder entfernt werden.

`Neutron-Dev-Start.cmd` bleibt ausdrücklich ungeeignet: Sein Offline-Guard blockiert
Netzwerk und Callback-Listener. Für den Live-Test ist der tatsächliche, konfigurierte
Online-Startpfad erforderlich. Die Umstellung verändert diesen Guard nicht.

## Erforderlicher Live-Test

Der App-Inhaber meldet sich über Microsoft im Standardbrowser an, prüft den
zurückgegebenen Minecraft-Namen und die tatsächlich authentifizierte Online-
Sitzung. Auch Abbruch, erneuter Versuch und die Wahl eines anderen gespeicherten
Profils sind zu prüfen. Bei einem Fehler bleibt die vorherige Sitzung erhalten.

Vor dieser Abnahme darf der Browser-Login nicht als live verifiziert oder
releasefertig bezeichnet werden. Weder eine App-Registrierung noch eine Zulassung
oder echte Nutzeranmeldung wurden durch lokale Fixture-Tests durchgeführt.
Passwörter, Tokens und vollständige Callback-URLs gehören nicht in Testnachweise.

Build nach der Migration mit `mvn -B -ntp -Prelease-bundle clean package`, damit
keine alten generierten JavaFX-Dateien in `target` in das Paket gelangen. Der
bestehende Release-Paketcheck prüft die Browser-Implementierung und weist solche
veralteten Runtime-Artefakte zurück.

Details: [Browser-Login-Architektur und Quellen](../../design/microsoft-browser-login.md)
und [JavaFX-Migration](../../docs/JAVAFX8-RUNTIME.md).

Die volle Assembly enthält weiterhin Minecraft-/OptiFine-Klassen und Assets.
Ein lokaler Build ist allein keine freigegebene Weitergabe. Persönliche Konten,
Tokens, Laufzeitdaten und Welten werden nicht in ein Testpaket übernommen.
