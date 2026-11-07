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
