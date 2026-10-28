# Microsoft-Browserlogin: echte Voraussetzung

Der vorhandene Browserflow liegt in
`me.youngjibbit.account.MicrosoftBrowserAuth`. Er verwendet Authorization Code
mit PKCE, zufälliges State, einen kurzlebigen Loopback-Callback und anschließend
Xbox Live, XSTS, Minecraft Services und die Prüfung des zurückgegebenen Java-
Profils. Tokens bleiben im Speicher; der Flow verlangt kein Client-Secret.

Ohne eine eigene registrierte App ist eine echte Microsoft-/Minecraft-Anmeldung
über diesen Flow noch nicht ausführbar.
Die vorhandenen Tests verwenden lokale HTTP-Fixtures und beweisen keine
Live-Freigabe durch Microsoft oder Minecraft Services.

Für den vorhandenen Flow wird eine eigene App im
[Microsoft Entra Admin Center](https://entra.microsoft.com/) benötigt:

1. App für persönliche Microsoft-Konten registrieren.
2. Plattform **Mobile und Desktopanwendungen** mit dem Redirect
   `http://localhost/callback` konfigurieren. Der Port ist beim localhost-
   Redirect dynamisch; der Callback-Pfad muss übereinstimmen.
3. Die öffentliche **Application (client) ID** übernehmen. Keine Passwörter,
   Tokens oder Client-Secrets weitergeben. Minecraft-/Xbox-API-Zulassung ist
   gesondert zu prüfen; eine Entra-Registrierung allein beweist sie nicht.

Diese Registrierung und die tatsächliche Benutzeranmeldung erfolgen durch den
Nutzer. Es wurde keine App registriert, kein fremder Launcher-Client verwendet
und kein bestehender Account-/Tokenbestand gelesen.

Der Code nimmt die öffentliche ID als JVM-Property
`pulsar.microsoft.clientId` oder als prozesslokale Umgebungsvariable
`PULSAR_MICROSOFT_CLIENT_ID` entgegen. Im Accountmanager heißt die Aktion
Microsoft-Browseranmeldung. `Neutron-Dev-Start.cmd` ist dafür ausdrücklich
ungeeignet: Sein geprüfter Offline-Guard blockiert Netzwerk und Callback-
Listener. Er wird nicht stillschweigend zu einem Online-Launcher umgestellt.

Vor einem Online-Start sind die neue eigene App-ID, eine kontrollierte
Session-/Netz-/Loaderkette und ein Live-Test mit Nutzeranmeldung erforderlich.
Es existiert derzeit kein qualifiziertes Ein-Klick-Multiplayerpaket für den
zweiten Rechner.

Primärquellen, am 28.10.2026 gelesen:

- [Microsoft: Desktop-App registrieren](https://learn.microsoft.com/en-us/azure/active-directory/develop/scenario-desktop-app-registration)
- [Microsoft: Redirect-URIs und localhost-Portausnahme](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url)

Die volle Assembly enthält weiterhin Minecraft-/OptiFine-Klassen und Assets.
Eine lokale Builddatei ist damit noch keine freigegebene Weitergabe. Keine
Kollegen-ZIP mit fingierter Sitzung und kein öffentlicher Vollquell-Push.
