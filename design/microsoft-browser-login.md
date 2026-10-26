# Microsoft browser sign-in

The client supports authorization code sign-in with S256 PKCE in the system browser, followed by Xbox Live, XSTS, Minecraft Services and the Java profile request. Network requests run on the account worker. The existing client executor applies a successful session. Failure, cancellation, a different saved profile, or a superseded attempt keeps the current session.

## Registration required before a personal sign-in

No Microsoft application ID is included. The project owner must register and control its own application. Do not copy an official launcher or another project's client ID.

1. Create a Microsoft Entra application supporting personal Microsoft accounts. The implementation uses the `consumers` authority.
2. Configure the **Mobile and desktop applications** platform and register exactly `http://localhost/callback` as a native/public-client redirect. Enable public-client flows where required by the registration portal. Do not create or distribute a client secret.
3. Supply the resulting application/client UUID through the environment variable `PULSAR_MICROSOFT_CLIENT_ID`, or the JVM argument `-Dpulsar.microsoft.clientId=<your-owned-client-uuid>`. This identifier is public configuration, not an account credential. The JVM property takes precedence.
4. Review the Minecraft Java Game Service API application process linked below and obtain any required approval for this app ID. Entra registration alone does not establish Minecraft Services access.

The listener binds only the IPv4 loopback `127.0.0.1` on an available ephemeral port, and IPv6 loopback `::1` on the same port when the OS supports it. The authorization request uses `http://localhost:<port>/callback`. Microsoft documents ignoring the port when matching **localhost** redirects; the path must still match. Do not register this as a Web or SPA redirect. The redirect itself never contains an IPv6 address literal; both local address families serve the same state-bound attempt.

The only requested scope is `xboxlive.signin`. This implementation deliberately does not request refresh/offline access. Users sign in through the browser again for a later session. No Microsoft password form or password authentication request is used.

## Account library behavior

- **More → Microsoft browser** starts a new online sign-in. **Sign in with Microsoft** is also available on the Add and Direct sign-in forms.
- Signing in a saved Microsoft entry uses the browser too. If its known Minecraft profile ID differs from the returned profile, the session stays unchanged.
- A legacy email/password row without a known profile remains saved. A successful browser sign-in creates or selects a separate stable Minecraft-profile-ID entry, retaining the old row and its metadata.
- New browser entries save only profile identity/name and ordinary library flags. Microsoft/Xbox/Minecraft tokens, OAuth codes, PKCE verifiers and refresh credentials are not saved. Legacy credentials and additional metadata remain intact, including when an existing stable-profile row is reused. No bulk migration deletes them.
- Closing the initiating screen, selecting another account, reloading/removing entries or starting another login invalidates the old attempt. A pending loopback listener is closed. An already-running HTTP request can take up to its bounded timeout to return, but its late result cannot apply a session.

The callback checks method, exact path, Host, state, duplicate parameters and code/error exclusivity. Invalid callbacks do not consume a valid attempt. Responses contain fixed text with `no-store`/`no-referrer`. Provider response bodies and callback parameters are not displayed or logged. Known Xbox profile/family restrictions receive fixed guidance; Minecraft rejection does not claim that registration approval has been verified.

## Sources and approval limits

Primary Microsoft documentation checked on 2026-10-26:

- [Authorization code flow and PKCE](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow): authorization parameters, state, verifier redemption and public-client flow.
- [Desktop app configuration](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration): desktop platform and system-browser/public-client registration. Its workforce example is not a requirement to use a workforce-only audience; this client uses personal accounts.
- [Redirect URI rules](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url): localhost port matching, path matching, platform distinctions and loopback restrictions.
- [Xbox services sign-in](https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-website-authentication): personal-account authority, Xbox scope and Xbox user-token request format. The article's server-side client-secret example does not apply to this desktop public client.
- [Minecraft Java Game Service API review/application page](https://help.minecraft.net/hc/en-us/articles/16254801392141) and [official Java support index](https://help.minecraft.net/hc/en-us/sections/44460521913485).

The GDK Integration section explicitly pairs `consumers/oauth2/v2.0/authorize` with `xboxlive.signin` and `consumers/oauth2/v2.0/token`; its Registration section specifies personal Microsoft accounts. Public-client PKCE is taken from the identity-platform desktop/auth-code documentation, because the GDK example itself is a confidential website using a secret. This combination is implemented and mock-tested; live acceptance for a newly registered app and this scope remains unverified.

The Minecraft help article returned a JavaScript shell during this check. The official support index lists the review/application process, but its current form, eligibility criteria and approval duration could not be verified. Check the official page as the application owner. Community answers on Microsoft Q&A give conflicting application routes; this document does not treat them as authoritative approval instructions. No registration was created, submitted or approved during implementation.

## Verification

Use Java 8 and the existing Maven dependencies:

```powershell
$env:JAVA_HOME='C:/Users/Administrator/scoop/apps/temurin8-jdk/current'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
mvn -o -q '-Dtest=MicrosoftBrowserAuthTest,MicrosoftBrowserUiBindingTest,LoginCoordinatorTest,AccountManagerTest,AccountUiTest' test
```

The tests use disposable local HTTP endpoints and a substitute browser opener. They exercise the real loopback server, HTTP bodies, PKCE verification, state rejection, token chain, provider failures, callback cleanup, cancellation during exchange, client-thread installation and token-free new persistence. They do not open Microsoft pages, authenticate a personal account, prove app approval or validate a live server join.

After registration/approval, the owner can manually select Microsoft browser sign-in, consent in the system browser, return to the client and inspect the account name. Repeat with denial, closing the screen, and selecting a different saved profile. Keep the previous session until the entire chain succeeds. Live authentication remains a separate manual check.
