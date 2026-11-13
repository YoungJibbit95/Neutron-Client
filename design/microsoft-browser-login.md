# Microsoft browser sign-in

Microsoft accounts use one authentication path: Authorization Code with S256 PKCE
in the operating system's default browser. The former OpenAuth/JavaFX window and
runtime bundle have been removed. Browser sessions, account selection and password
manager autofill work on Microsoft's own page; Neutron does not read browser
cookies, passwords or browser credential files.

The account worker completes Microsoft OAuth, Xbox Live, XSTS, Minecraft Services
and the Minecraft Java profile request. Only the client executor can apply the
result. A failure, cancellation, different saved profile or superseded attempt
keeps the current session. The browser callback confirms receipt of the OAuth
response; successful sign-in is reported in the client only after the complete
exchange and profile validation.

## Reported JavaFX failure

The reported `Microsoft returned an invalid sign-in response` message came from
the old implicit-flow callback parser, before any Xbox or Minecraft request. The
screen alone cannot identify which callback field failed validation. The new
flow removes that embedded-window/fragment parser and receives an authorization
code through the registered loopback callback instead.

An unauthenticated compatibility check on 2026-11-11 found that the old OpenAuth
client ID accepted its original Microsoft desktop redirect but Microsoft rejected
both `http://localhost:<port>/callback` and the bare localhost redirect. That
existing identity cannot simply be reused for the new callback. No personal
account, password, token or browser cookie was used in this check.

## Application registration is required

No Neutron application ID is included in this repository. A missing or malformed
configuration opens Microsoft setup before browser or network access. There is no
embedded-window fallback and no substituted third-party launcher identity.

1. Register an application that supports personal Microsoft accounts. The
   implementation uses the `consumers` authority.
2. Configure **Mobile and desktop applications** and register exactly
   `http://localhost/callback` as a native/public-client redirect. Enable
   public-client flows where required by the registration portal. Do not create
   or distribute a client secret for this desktop application.
3. Configure the resulting **Application (client) ID** using the setup screen or
   one of the configuration sources below. This UUID is public application
   configuration, not a Microsoft account credential.
4. Verify the application's access to Minecraft Java services through the
   application owner's official Minecraft support/review process. Entra
   registration alone is not proof of Minecraft Services acceptance.

The callback listener binds to IPv4 loopback `127.0.0.1` on an available port and,
when supported, IPv6 loopback `::1` on that same port. Its registered redirect form
is `http://localhost:<port>/callback`. Microsoft ignores the port when matching
localhost redirects; the path and native/public-client platform still matter.
The redirect contains no IPv6 literal. No listener is bound to a public interface.

The only requested scope is `xboxlive.signin`. This flow does not request or save
refresh/offline access. A later session signs in through the browser again, where
existing browser sign-in state may reduce the required interaction.
`prompt=select_account` lets the user select a remembered Microsoft account or
choose another account without forcing password entry on every attempt.

### Configuration shared by start paths

The application ID is read at login time in the following order; blank overrides
are ignored:

1. `-Dneutron.microsoft.clientId=...`, then legacy `-Dpulsar.microsoft.clientId=...`.
2. `NEUTRON_MICROSOFT_CLIENT_ID`, then legacy `PULSAR_MICROSOFT_CLIENT_ID`.
3. `${user.home}/.neutron/microsoft-auth.properties`, key `clientId`.
4. Bundled `/neutron-microsoft.properties`, key `clientId`, for a publisher-configured
   build.

The user file is independent of `test_run`, Minecraft's `--gameDir` and the launcher
working directory. Setup writes only the public application ID and retries the
original login after returning to its parent screen. Direct-login and saved-profile
behavior are preserved. If a JVM argument or environment variable supplies a
different ID, change or remove that override before saving a replacement.
Malformed explicit IDs do not silently select another configuration source.

The offline-only `Neutron-Dev-Start.cmd` remains unsuitable for live sign-in: its
network guard blocks outbound authentication and the loopback listener. Use a
properly configured online start path for the owner-operated acceptance check.

## Account and session behavior

- The Microsoft actions in the account manager, Add form and Direct sign-in form
  start the default browser. Saved Microsoft accounts use the same flow.
- A saved account with a known Minecraft profile ID must match the returned
  profile. Selecting a different Microsoft account cannot replace that session.
- Legacy email/password rows without a known profile remain saved. A successful
  browser login creates or selects a stable Minecraft-profile-ID entry, retaining
  the old row and its metadata.
- New browser entries store profile identity/name and ordinary account flags.
  Microsoft/Xbox/Minecraft tokens, OAuth codes, PKCE verifiers and refresh
  credentials are not persisted by the new flow. Existing legacy credentials and
  additional metadata are preserved; this change performs no bulk deletion.
- Closing the initiating screen, choosing another account, reloading/removing
  entries or starting another login invalidates the previous attempt. Pending
  listeners are closed, and a late HTTP result cannot apply a session.
- Browser launch failure, denial, timeout and service failures have fixed user
  guidance. The client cannot reliably detect a closed browser tab; cancellation
  or the bounded login timeout ends that pending attempt.

The callback validates the method, exact path, Host, state, duplicate parameters
and code/error exclusivity. Invalid callbacks do not consume the active attempt.
Callback pages use fixed text and `no-store`/`no-referrer`; they never echo secrets.
One callback is accepted per attempt. The response is sent before the client
closes its listener and starts the token exchange. Browser sign-in allows five
minutes; HTTP connect/read timeouts remain ten seconds per operation.
HTTP connections have bounded timeouts and responses have size limits. Raw OAuth
callbacks, response bodies and tokens are not exposed in user-facing errors.
Known Xbox profile/family restrictions and missing Java profiles have distinct
messages. Minecraft's rejection of an app/account does not establish which
registration or eligibility condition failed.

## Build and automated verification

Use the project Java 8 runtime. A clean package is necessary after upgrading from
the old JavaFX build to remove generated SDK resources and deleted classes:

```text
mvn -B -ntp -Prelease-bundle clean package
```

The existing release workflow runs the Java 8 tests and the isolated
`PackagedRuntimeCheck` after packaging. Its browser-login contract checks class
linkage and rejects obsolete OpenAuth/JavaFX artifacts. It retains the other
manifest, dependency, resource and reflective-discovery checks. See
[JavaFX migration](../docs/JAVAFX8-RUNTIME.md).

For a focused account regression run:

```text
mvn -q -Dtest=Microsoft*Test,AccountAuthenticatorTest,LoginCoordinatorTest,AccountManagerTest,AccountUiTest test
```

The automated authentication tests use local HTTP fixtures and a substitute
browser opener. They cover the loopback callback, PKCE/state, token exchange,
provider failures, cleanup, cancellation, client-thread application and new-account
persistence. They do not authenticate a real account or establish Microsoft's
acceptance of a particular registration.

## Live acceptance remains a release requirement

There is no claim of an approved or live-tested Neutron application registration
in this change. Before calling the Microsoft sign-in release-ready, the application
owner must supply the actual registered ID and complete these checks on the target
desktop:

1. Open Microsoft sign-in from Add, Direct sign-in and a saved Microsoft entry.
   Confirm the normal browser opens and its account/password-manager behavior is
   available.
2. Complete Microsoft consent/sign-in with an eligible Minecraft Java account.
   Verify that the client receives the expected profile and that the intended
   authenticated multiplayer session works.
3. Repeat with denial, cancellation/closed account screen, timeout and a different
   saved profile. Verify that the previous client session remains intact.
4. Verify a fresh packaged build with the actual launcher/start path, including a
   retry after cancellation. Record the tested build and result without account
   secrets, tokens or complete callback URLs.

The code and fixture checks can be reviewed before that owner-operated test. A
missing registration, required service approval or unperformed live check remains
an open acceptance gate; it is not made successful by passing CI.

## Primary references

Microsoft documentation reviewed on 2026-11-11:

- [Desktop app registration](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-registration)
- [Authorization Code and PKCE](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)
- [Redirect URI and localhost rules](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url)
- [MSAL Java system-browser behavior](https://learn.microsoft.com/en-us/entra/msal/java/getting-started/acquiring-tokens-interactively)
- [Xbox services sign-in](https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-website-authentication)

The Xbox reference documents the personal-account authority, Xbox sign-in scope
and `d=` prefix for the Microsoft token in the Xbox `RpsTicket`. Its confidential
website/client-secret example is not the native desktop registration model; PKCE
and native redirect handling follow the identity-platform documentation.

The [Minecraft support article](https://help.minecraft.net/hc/en-us/articles/16254801392141)
returned only its page shell during this review. The
[official review link](https://aka.ms/mce-reviewappid) redirected to a Microsoft
form whose contents could not be inspected. Current eligibility criteria, review
steps and approval timing are therefore not asserted here. No registration was
created, submitted or approved during implementation.
