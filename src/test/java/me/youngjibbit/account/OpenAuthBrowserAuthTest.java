package me.youngjibbit.account;

import net.minecraft.util.Session;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import javax.swing.SwingUtilities;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class OpenAuthBrowserAuthTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void emptyConfigurationSelectsOpenAuthAndOwnAppSelectsPkce() throws Exception {
        MicrosoftAuthConfig config=new MicrosoftAuthConfig(new java.util.Properties(),java.util.Collections.emptyMap(),temporary.newFolder().toPath().resolve("microsoft.properties"));
        Window window=new Window();AtomicInteger embeddedCalls=new AtomicInteger();
        OpenAuthBrowserAuth embedded=new OpenAuthBrowserAuth(()->{},uri->{window.response.complete(response(uri));return window;},(tokens,attemptActive)->{embeddedCalls.incrementAndGet();return session();},2000);
        try(MicrosoftBrowserAuthTest.Services services=new MicrosoftBrowserAuthTest.Services()) {
            AccountAuthenticator auth=new AccountAuthenticator(services.auth(),embedded,config);
            assertEquals("WebPlayer",auth.authenticate(LoginRequest.microsoftBrowser(),()->true).getUsername());drain();
            assertTrue(services.calls.isEmpty());assertEquals(1,embeddedCalls.get());
            config.save(MicrosoftBrowserAuthTest.CLIENT);
            assertEquals("BrowserPlayer",auth.authenticate(LoginRequest.microsoftBrowser(),()->true).getUsername());
            assertEquals(5,services.calls.size());assertEquals(1,embeddedCalls.get());
        }
    }
    @Test public void unreadableConfigurationRequestsSetupBeforeAnyLoginWindow() throws Exception {
        java.nio.file.Path file=temporary.newFolder().toPath().resolve("microsoft.properties");
        java.nio.file.Files.write(file,"clientId=\\uNOTHEX".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MicrosoftAuthConfig config=new MicrosoftAuthConfig(new java.util.Properties(),java.util.Collections.emptyMap(),file);
        OpenAuthBrowserAuth embedded=new OpenAuthBrowserAuth(()->{fail("No runtime or window before configuration recovery");},uri->null,(tokens,attemptActive)->session(),100);
        AccountAuthenticator auth=new AccountAuthenticator(new MicrosoftBrowserAuth("",null,uri->{fail();},100),embedded,config);
        try{auth.authenticate(LoginRequest.microsoftBrowser(),()->true);fail();}
        catch(LoginCoordinator.LoginUnavailableException expected){assertTrue(expected.isSetupRequired());}
    }
    @Test public void productionAdapterSupportsOfflineWithoutInitializingJavaFx() throws Exception {
        AccountAuthenticator authenticator=new AccountAuthenticator();
        Session offline=authenticator.authenticate(new LoginRequest(new Account("Offline","","Offline",Account.Provider.OFFLINE),false,false));
        assertEquals("Offline",offline.getUsername());assertEquals("",offline.getToken());
        authenticator.cancel();
    }
    static class Window implements OpenAuthBrowserAuth.Window {
        final CompletableFuture<String> response = new CompletableFuture<>();
        final AtomicInteger closed = new AtomicInteger();
        public CompletableFuture<String> result() { return response; }
        public void close() { assertTrue(SwingUtilities.isEventDispatchThread());closed.incrementAndGet();response.cancel(false); }
    }
    private static String response(URI uri) throws Exception {
        String state=MicrosoftBrowserAuth.query(uri.getRawQuery()).get("state");
        return "https://login.live.com/oauth20_desktop.srf#"+MicrosoftBrowserAuth.form(MicrosoftBrowserAuth.params(
                "state",state,"access_token","synthetic-token","refresh_token","discard-me","token_type","Bearer"));
    }
    private static Session session() { return new Session("WebPlayer",MicrosoftBrowserAuthTest.PROFILE,"minecraft-token","mojang"); }
    private static void drain() throws Exception { SwingUtilities.invokeAndWait(()->{}); }

    @Test public void compatibilityFlowUsesLibraryParametersAndDiscardsRefreshCredential() throws Exception {
        Window window=new Window();AtomicReference<URI> uri=new AtomicReference<>();
        OpenAuthBrowserAuth auth=new OpenAuthBrowserAuth(()->{},authorization->{
            assertTrue(SwingUtilities.isEventDispatchThread());uri.set(authorization);window.response.complete(response(authorization));return window;
        },(tokens,attemptActive)->{assertEquals("synthetic-token",tokens.getAccessToken());assertEquals("",tokens.getRefreshToken());return session();},2000);
        assertEquals("WebPlayer",auth.login(()->true).getUsername());drain();assertEquals(1,window.closed.get());
        assertEquals(fr.litarvan.openauth.microsoft.MicrosoftAuthenticator.XBOX_LIVE_CLIENT_ID,
                MicrosoftBrowserAuth.query(uri.get().getRawQuery()).get("client_id"));
    }

    @Test public void documentedDesktopLocaleCallbackReachesMinecraftExchange() throws Exception {
        for (String locale : new String[]{"1033", "1031"}) {
            Window window = new Window(); AtomicInteger exchanges = new AtomicInteger();
            OpenAuthBrowserAuth auth = new OpenAuthBrowserAuth(() -> {}, authorization -> {
                window.response.complete(response(authorization).replace(".srf#", ".srf?lc=" + locale + "#"));
                return window;
            }, (tokens, active) -> {
                exchanges.incrementAndGet();
                assertEquals("synthetic-token", tokens.getAccessToken());
                assertEquals("", tokens.getRefreshToken());
                return session();
            }, 2000);
            assertEquals("WebPlayer", auth.login(() -> true).getUsername());
            drain(); assertEquals(1, exchanges.get()); assertEquals(1, window.closed.get());
        }
    }

    @Test public void embeddedCallbackBecomesARememberedSessionOnlyOnTheClientThread() throws Exception {
        try (MicrosoftBrowserAuthTest.Services services = new MicrosoftBrowserAuthTest.Services()) {
            services.expectedTicket = "synthetic-token";
            Window window = new Window();
            OpenAuthBrowserAuth embedded = new OpenAuthBrowserAuth(() -> {}, authorization -> {
                window.response.complete(response(authorization).replace(".srf#", ".srf?lc=1031#"));
                return window;
            }, services.auth(), 2000);
            MicrosoftAuthConfig configuration = new MicrosoftAuthConfig(new java.util.Properties(),
                    java.util.Collections.emptyMap(), temporary.newFolder().toPath().resolve("microsoft.properties"));
            AccountAuthenticator authenticator = new AccountAuthenticator(services.auth(), embedded, configuration);
            AccountManager manager = new AccountManager(temporary.newFolder());
            java.util.Queue<Runnable> client = new ConcurrentLinkedQueue<>();
            Thread clientThread = Thread.currentThread();
            Session previous = new Session("Previous", MicrosoftBrowserAuthTest.PROFILE, "previous-token", "mojang");
            AtomicReference<Session> current = new AtomicReference<>(previous);
            AtomicReference<Account> selected = new AtomicReference<>();
            AtomicInteger successes = new AtomicInteger();
            LoginCoordinator coordinator = new LoginCoordinator(manager, authenticator, client::add, (result, provider) -> {
                assertSame(clientThread, Thread.currentThread());
                assertEquals(Account.Provider.MICROSOFT, provider);
                current.set(result);
            }, account -> { assertSame(clientThread, Thread.currentThread()); selected.set(account); });
            LoginCoordinator.Attempt attempt = coordinator.begin(LoginRequest.microsoftBrowser(), successes::incrementAndGet);
            runAttempt(attempt);
            assertSame(previous, current.get()); assertTrue(manager.getAccounts().isEmpty()); assertNull(selected.get());
            drainClient(client);
            assertEquals("BrowserPlayer", current.get().getUsername());
            assertEquals("minecraft-secret", current.get().getToken());
            assertEquals(MicrosoftBrowserAuthTest.PROFILE, current.get().getPlayerID());
            assertEquals(1, manager.getAccounts().size()); assertSame(manager.getLastAlt(), selected.get());
            assertEquals(MicrosoftBrowserAuthTest.PROFILE, selected.get().getMicrosoftProfileId());
            assertNull(selected.get().getPassword()); assertNull(selected.get().getMicrosoftAccessToken());
            assertEquals(1, successes.get()); assertTrue(attempt.getStatus().contains("successfully"));
            assertTrue(manager.load()); assertEquals(1, manager.getAccounts().size());
            drain(); assertEquals(1, window.closed.get());
        }
    }

    @Test public void declinedEmbeddedCallbackRetainsTheSessionAndSameAuthenticatorCanRetry() throws Exception {
        try (MicrosoftBrowserAuthTest.Services services = new MicrosoftBrowserAuthTest.Services()) {
            services.expectedTicket = "synthetic-token";
            AtomicInteger opened = new AtomicInteger(); java.util.List<Window> windows = new java.util.ArrayList<>();
            OpenAuthBrowserAuth embedded = new OpenAuthBrowserAuth(() -> {}, authorization -> {
                Window window = new Window(); windows.add(window);
                if (opened.incrementAndGet() == 1) {
                    String state = MicrosoftBrowserAuth.query(authorization.getRawQuery()).get("state");
                    assertTrue(OpenAuthBrowserAuth.completeDesktopRedirect(window.response, "https://login.live.com/oauth20_desktop.srf?lc=1033#"
                            + MicrosoftBrowserAuth.form(MicrosoftBrowserAuth.params("state", state,
                            "error", "access_denied", "error_description", "private provider description"))));
                } else window.response.complete(response(authorization));
                return window;
            }, services.auth(), 2000);
            MicrosoftAuthConfig configuration = new MicrosoftAuthConfig(new java.util.Properties(),
                    java.util.Collections.emptyMap(), temporary.newFolder().toPath().resolve("microsoft.properties"));
            AccountAuthenticator authenticator = new AccountAuthenticator(services.auth(), embedded, configuration);
            AccountManager manager = new AccountManager(temporary.newFolder());
            java.util.Queue<Runnable> client = new ConcurrentLinkedQueue<>();
            Session previous = new Session("Previous", MicrosoftBrowserAuthTest.PROFILE, "previous-token", "mojang");
            AtomicReference<Session> current = new AtomicReference<>(previous);
            LoginCoordinator coordinator = new LoginCoordinator(manager, authenticator, client::add,
                    (result, provider) -> current.set(result), account -> {});
            LoginCoordinator.Attempt first = coordinator.begin(LoginRequest.microsoftBrowser(), null);
            runAttempt(first); drainClient(client); drain();
            assertSame(previous, current.get()); assertTrue(manager.getAccounts().isEmpty());
            assertTrue(services.calls.isEmpty()); assertTrue(first.getStatus().contains("cancelled or declined"));
            assertFalse(first.getStatus().contains("private provider description"));
            LoginCoordinator.Attempt retry = coordinator.begin(LoginRequest.microsoftBrowser(), null);
            runAttempt(retry); drainClient(client); drain();
            assertEquals("BrowserPlayer", current.get().getUsername()); assertEquals(1, manager.getAccounts().size());
            assertEquals(2, opened.get()); assertEquals(1, windows.get(0).closed.get()); assertEquals(1, windows.get(1).closed.get());
        }
    }

    @Test public void desktopRedirectRoutingWaitsForTheCallbackAndLeavesValidationToTheExchange() throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        for (String location : new String[]{null, "about:blank", "https://login.live.com/oauth20_authorize.srf?access_token=intermediate",
                "https://login.live.com/oauth20_desktop.srf?lc=1033", "https://other.invalid/oauth20_desktop.srf#error=access_denied",
                "http://login.live.com/oauth20_desktop.srf#error=access_denied", "https://login.live.com@other.invalid/oauth20_desktop.srf#error=access_denied"}) {
            assertFalse(OpenAuthBrowserAuth.completeDesktopRedirect(result, location));
            assertFalse(result.isDone());
        }
        String denial = "https://login.live.com/oauth20_desktop.srf?lc=1033#state=wrong&error=access_denied";
        assertTrue(OpenAuthBrowserAuth.completeDesktopRedirect(result, denial));
        assertEquals(denial, result.get());
        assertInvalidCallback(result.get());
        assertFalse(OpenAuthBrowserAuth.completeDesktopRedirect(result, "https://login.live.com/oauth20_desktop.srf#access_token=late"));
    }

    private static void runAttempt(LoginCoordinator.Attempt attempt) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { attempt.run(); } catch (Throwable error) { failure.set(error); } });
        worker.start(); worker.join(5000); assertFalse(worker.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void drainClient(java.util.Queue<Runnable> client) {
        Runnable next; while ((next = client.poll()) != null) next.run();
    }

    @Test public void localeMetadataDoesNotPermitQueryCredentialsOrRelaxFragmentValidation() throws Exception {
        URI authorization = OpenAuthBrowserAuth.authorization("synthetic-state");
        String valid = response(authorization);
        for (String query : new String[]{"", "lc=", "lc=de-DE", "lc=-1033", "lc=1033&lc=1031",
                "lc=1033&access_token=other", "access_token=other", "lc=1033&state=synthetic-state",
                "lc=1033&token_type=Bearer", "lc=1033&redirect_uri=https%3A%2F%2Fother.invalid"}) {
            assertInvalidCallback(valid.replace(".srf#", ".srf?" + query + "#"));
        }
        String localized = valid.replace(".srf#", ".srf?lc=1033#");
        assertInvalidCallback(localized.replace("login.live.com", "other.invalid"));
        assertInvalidCallback(localized.replace("https:", "http:"));
        assertInvalidCallback(localized.replace("state=", "state=wrong"));
        assertInvalidCallback(localized.replace("token_type=Bearer", "token_type=Other"));
        assertInvalidCallback(localized + "&access_token=duplicate");
        assertInvalidCallback(localized + "&state=synthetic-state");
        assertInvalidCallback(localized + "&token_type=Bearer");
    }

    private static void assertInvalidCallback(String response) throws Exception {
        try { OpenAuthBrowserAuth.tokens(response, "synthetic-state"); fail("Callback must be rejected"); }
        catch (LoginCoordinator.LoginUnavailableException expected) {
            assertFalse(expected.getUserMessage().contains("synthetic-token"));
        }
    }

    @Test public void embeddedLoginConsumesMinecraftTokenEntitlementAndProfileWithoutLegacyUsername() throws Exception {
        try (MicrosoftBrowserAuthTest.Services services=new MicrosoftBrowserAuthTest.Services()) {
            services.expectedTicket="synthetic-token";
            OpenAuthBrowserAuth auth=new OpenAuthBrowserAuth(()->{},uri->{
                Window window=new Window();
                window.response.complete(response(uri));
                return window;
            },services.auth(),2000);
            Session result=auth.login(()->true);
            assertEquals("BrowserPlayer",result.getUsername());
            assertEquals(MicrosoftBrowserAuthTest.PROFILE,result.getPlayerID());
            assertEquals("minecraft-secret",result.getToken());
            assertEquals(java.util.Arrays.asList("/xbox","/xsts","/minecraft","/store","/profile"),services.calls);

            services.failStage="/store";
            services.failStatus=200;
            services.errorBody="{\"items\":[]}";
            services.calls.clear();
            try {
                auth.login(()->true);
                fail();
            } catch(LoginCoordinator.LoginUnavailableException expected) {
                assertTrue(expected.getUserMessage(),expected.getUserMessage().contains("entitlement"));
                assertFalse(expected.getUserMessage().contains("synthetic-token"));
            }
            assertFalse(services.calls.contains("/profile"));
        }
    }

    @Test public void wrongOriginWrongStateAndDuplicateTokensNeverReachExchange() throws Exception {
        for(int mode=0;mode<4;mode++) {
            final int selected=mode;Window window=new Window();AtomicInteger exchanges=new AtomicInteger();
            OpenAuthBrowserAuth auth=new OpenAuthBrowserAuth(()->{},uri->{
                String result=response(uri);
                if(selected==0)result=result.replace("login.live.com","attacker.invalid");
                if(selected==1)result=result.replace("state=","state=wrong");
                if(selected==2)result+="&access_token=duplicate";
                if(selected==3)result=result.replace("https:","http:");
                window.response.complete(result);return window;
            },(tokens,attemptActive)->{exchanges.incrementAndGet();return session();},2000);
            try{auth.login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){assertFalse(expected.getUserMessage().contains("synthetic-token"));}
            drain();assertEquals(0,exchanges.get());assertEquals(1,window.closed.get());
        }
    }

    @Test public void cancelClosesOnlyOwnedWindowAndAllowsRetry() throws Exception {
        Window window=new Window();CountDownLatch opened=new CountDownLatch(1);AtomicInteger exchanges=new AtomicInteger();
        OpenAuthBrowserAuth auth=new OpenAuthBrowserAuth(()->{},uri->{opened.countDown();return window;},(tokens,attemptActive)->{exchanges.incrementAndGet();return session();},2000);
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread worker=new Thread(()->{try{auth.login(()->true);fail();}catch(Throwable expected){error.set(expected);}});
        worker.start();assertTrue(opened.await(2,TimeUnit.SECONDS));auth.cancel();worker.join(3000);drain();
        assertFalse(worker.isAlive());assertTrue(error.get() instanceof LoginCoordinator.LoginUnavailableException);
        assertEquals(0,exchanges.get());assertEquals(1,window.closed.get());
        compatibilityFlowUsesLibraryParametersAndDiscardsRefreshCredential();
    }

    @Test public void timeoutLoadFailureAndCancelledLateExchangeKeepSessionUnavailable() throws Exception {
        Window window=new Window();
        OpenAuthBrowserAuth timeout=new OpenAuthBrowserAuth(()->{},uri->window,(tokens,attemptActive)->{fail();return null;},120);
        try{timeout.login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){assertTrue(expected.getUserMessage().contains("timed out"));}
        drain();assertEquals(1,window.closed.get());
        OpenAuthBrowserAuth broken=new OpenAuthBrowserAuth(()->{throw new UnsatisfiedLinkError("secret-path");},uri->{fail();return null;},(tokens,attemptActive)->session(),100);
        try{broken.login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){assertFalse(expected.getUserMessage().contains("secret-path"));}
        AtomicBoolean active=new AtomicBoolean(true);Window late=new Window();
        OpenAuthBrowserAuth cancelled=new OpenAuthBrowserAuth(()->{},uri->{late.response.complete(response(uri));return late;},(tokens,attemptActive)->{active.set(false);return session();},1000);
        try{cancelled.login(active::get);fail();}catch(LoginCoordinator.LoginUnavailableException expected){assertTrue(expected.getUserMessage().contains("cancelled"));}
        drain();assertEquals(1,late.closed.get());
    }
}
