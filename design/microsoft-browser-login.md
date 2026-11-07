# Microsoft browser sign-in

Windows x64 builds now support OpenAuth 1.1.6's Microsoft sign-in window with a bundled JavaFX 8 runtime. An empty app configuration selects this compatibility flow. A configured own application ID selects the existing authorization-code flow with S256 PKCE in the system browser. Both paths exchange Microsoft credentials for an Xbox/Minecraft session on the account worker; only the client executor can apply the result. Failure, cancellation, a different saved profile, or a superseded attempt keeps the current session.

## Default OpenAuth window

Maven includes JavaFX classes, WebKit/Glass/Prism Windows natives and vendor licenses in every client JAR. Temurin 8u482 stays the host runtime; no Java installation or LWJGL native path is modified. JavaFX is loaded only when this login path is selected. See [JavaFX runtime packaging](../docs/JAVAFX8-RUNTIME.md).

This path uses OpenAuth's existing public-client ID, scope and Microsoft desktop redirect. It is not Neutron's own app registration or evidence of Microsoft approval. A fresh OAuth `state` is added for each attempt; the returned HTTPS origin, exact desktop redirect, fragment parameters, token type and state are validated before token exchange. Refresh credentials are discarded. Provider errors never expose raw tokens or callback URLs.

Closing/navigating away from the account screen cancels the owned window and detaches its WebView. Closing the Microsoft window cancels the attempt. Reopening is supported without shutting down JavaFX between attempts. An already-running OpenAuth HTTP exchange can finish after cancellation (the dependency has bounded 30-second connect/60-second read timeouts), but its result cannot change the session. Live Microsoft acceptance, account eligibility and a multiplayer join still require manual verification; a working local WebView does not prove them.

## Registration required before a personal sign-in

For the optional **system-browser PKCE path**, no Neutron application ID is included. Register and control your own application for this path; do not substitute another application's ID into its configuration.

1. Create a Microsoft Entra application supporting personal Microsoft accounts. The implementation uses the `consumers` authority.
2. Configure the **Mobile and desktop applications** platform and register exactly `http://localhost/callback` as a native/public-client redirect. Enable public-client flows where required by the registration portal. Do not create or distribute a client secret.
3. Supply the resulting application/client UUID via the setup screen or one of the sources below. This identifier is public configuration, not an account credential.
4. Review the Minecraft Java Game Service API application process linked below and obtain any required approval for this app ID. Entra registration alone does not establish Minecraft Services access.

The listener binds only the IPv4 loopback `127.0.0.1` on an available ephemeral port, and IPv6 loopback `::1` on the same port when the OS supports it. The authorization request uses `http://localhost:<port>/callback`. Microsoft documents ignoring the port when matching **localhost** redirects; the path must still match. Do not register this as a Web or SPA redirect. The redirect itself never contains an IPv6 address literal; both local address families serve the same state-bound attempt.

The only requested scope is `xboxlive.signin`. This implementation deliberately does not request refresh/offline access. Users sign in through the browser again for a later session. No Microsoft password form or password authentication request is used.

### Configuration shared by all start paths

Lookup happens at login time, in this order (blank overrides are ignored):

1. `-Dneutron.microsoft.clientId=...`, then the legacy `-Dpulsar.microsoft.clientId=...`.
2. `NEUTRON_MICROSOFT_CLIENT_ID`, then the legacy `PULSAR_MICROSOFT_CLIENT_ID`.
3. `${user.home}/.neutron/microsoft-auth.properties`, key `clientId`.
4. Bundled `/neutron-microsoft.properties`, key `clientId`, for a publisher-configured build.

The user file is independent of `test_run`, Minecraft's `--gameDir` and the Neutron Launcher working directory. The setup screen writes only this public ID and retries the original request after returning to its parent, preserving direct-login and saved-profile behavior. A conflicting JVM/environment override must be removed or changed before saving a different ID. Malformed explicit IDs do not silently select another identity. Empty configuration selects OpenAuth; a nonempty invalid configuration opens setup.

## Account library behavior

- **More → Microsoft browser** starts a new online sign-in. **Sign in with Microsoft** is also available on the Add and Direct sign-in forms.
- Signing in a saved Microsoft entry uses the browser too. If its known Minecraft profile ID differs from the returned profile, the session stays unchanged.
- A legacy email/password row without a known profile remains saved. A successful browser sign-in creates or selects a separate stable Minecraft-profile-ID entry, retaining the old row and its metadata.
- New browser entries save only profile identity/name and ordinary library flags. Microsoft/Xbox/Minecraft tokens, OAuth codes, PKCE verifiers and refresh credentials are not saved. Legacy credentials and additional metadata remain intact, including when an existing stable-profile row is reused. No bulk migration deletes them.
- Closing the initiating screen, selecting another account, reloading/removing entries or starting another login invalidates the old attempt. A pending loopback listener is closed. An already-running HTTP request can take up to its bounded timeout to return, but its late result cannot apply a session.

The callback checks method, exact path, Host, state, duplicate parameters and code/error exclusivity. Invalid callbacks do not consume a valid attempt. Responses contain fixed text with `no-store`/`no-referrer`. Provider response bodies and callback parameters are not displayed or logged. Known Xbox profile/family restrictions receive fixed guidance; Minecraft rejection does not claim that registration approval has been verified.

OAuth failures use fixed recovery guidance: an expired or rejected code asks the user to start again; a rejected client registration or scope points to application setup; a temporary service failure asks the user to retry. Only `access_denied` is described as declined. Microsoft token and Xbox authorization error bodies are read up to 4 KiB and discarded; malformed, oversized and unknown responses use fixed fallback text. A nonempty invalid app ID stops the PKCE flow with a readable setup screen before browser/network access.

## Sources and approval limits

Primary Microsoft documentation checked on 2026-10-26; the authorization-code, redirect and Xbox references were rechecked on 2026-10-27:

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
$env:JAVA_HOME='C:/Users/Administrator/.jdks/temurin-1.8.0_482'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
mvn -q '-Dtest=JavaFxRuntimeTest,OpenAuthBrowserAuthTest,MicrosoftAuthConfigTest,MicrosoftBrowserAuthTest,MicrosoftBrowserUiBindingTest,LoginCoordinatorTest,AccountManagerTest,AccountUiTest' test
```

The tests use disposable local HTTP endpoints and a substitute browser opener. They exercise the real loopback server, HTTP bodies, PKCE verification, state rejection, token chain, provider failures, callback cleanup, cancellation during exchange, client-thread installation and token-free new persistence. They do not open Microsoft pages, authenticate a personal account, prove app approval or validate a live server join.

The follow-up check reproduced two misleading error paths before the fix: a temporary authorization failure appeared as user denial, and expired-code/registration failures had no recovery guidance. The regression tests also cover missing client configuration and malformed, oversized and unknown token errors. The browser callback page confirms receipt of the response; the client reports success only after the Xbox, Minecraft token and Java-profile requests complete.

After registration/approval, the owner can manually select Microsoft browser sign-in, consent in the system browser, return to the client and inspect the account name. Repeat with denial, closing the screen, and selecting a different saved profile. Keep the previous session until the entire chain succeeds. Live authentication remains a separate manual check.
