package me.youngjibbit.account;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.Session;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Real HTTP/token/callback chain against disposable local services; never opens a real browser. */
public class MicrosoftBrowserAuthTest {
    static final String CLIENT="11111111-2222-3333-4444-555555555555", PROFILE="0123456789abcdef0123456789abcdef";
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    static final class Services implements AutoCloseable {
        final HttpServer server;
        final List<String> calls=new CopyOnWriteArrayList<>();
        final AtomicReference<Throwable> failure=new AtomicReference<>();
        final AtomicReference<URI> authorization=new AtomicReference<>();
        final AtomicReference<String> verifier=new AtomicReference<>();
        String failStage="", errorBody="{\"Message\":\"raw-secret-token\"}";
        int failStatus=401;
        CountDownLatch tokenEntered,releaseToken;
        Services() throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
            server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"Mock Microsoft HTTP");t.setDaemon(true);return t;}));
            server.createContext("/",exchange->{
                String path=exchange.getRequestURI().getPath();calls.add(path);
                int status=200;String response="{}";
                try {
                    String body=read(exchange.getRequestBody());
                    if(path.equals("/token")) {
                        if(tokenEntered!=null){tokenEntered.countDown();assertTrue(releaseToken.await(3,TimeUnit.SECONDS));}
                        Map<String,String> form=MicrosoftBrowserAuth.query(body), query=MicrosoftBrowserAuth.query(authorization.get().getRawQuery());
                        assertEquals("authorization_code",form.get("grant_type"));assertEquals("mock-code",form.get("code"));
                        assertEquals(CLIENT,form.get("client_id"));assertEquals(query.get("redirect_uri"),form.get("redirect_uri"));
                        assertFalse(form.containsKey("client_secret"));verifier.set(form.get("code_verifier"));
                        assertTrue(verifier.get().matches("[A-Za-z0-9_-]{43,128}"));
                        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                                .digest(verifier.get().getBytes(StandardCharsets.US_ASCII)));
                        assertEquals(challenge,query.get("code_challenge"));
                        response="{\"access_token\":\"ms-secret\",\"token_type\":\"Bearer\"}";
                    } else if(path.equals("/xbox")) {
                        JsonObject json=new JsonParser().parse(body).getAsJsonObject();
                        assertEquals("d=ms-secret",json.getAsJsonObject("Properties").get("RpsTicket").getAsString());
                        response=claims("xbox-secret");
                    } else if(path.equals("/xsts")) {
                        JsonObject json=new JsonParser().parse(body).getAsJsonObject();
                        assertEquals("rp://api.minecraftservices.com/",json.get("RelyingParty").getAsString());
                        assertEquals("xbox-secret",json.getAsJsonObject("Properties").getAsJsonArray("UserTokens").get(0).getAsString());
                        response=claims("xsts-secret");
                    } else if(path.equals("/minecraft")) {
                        assertEquals("XBL3.0 x=12345;xsts-secret",new JsonParser().parse(body).getAsJsonObject().get("identityToken").getAsString());
                        response="{\"access_token\":\"minecraft-secret\"}";
                    } else if(path.equals("/profile")) {
                        assertEquals("Bearer minecraft-secret",exchange.getRequestHeaders().getFirst("Authorization"));
                        response="{\"id\":\""+PROFILE+"\",\"name\":\"BrowserPlayer\"}";
                    } else fail("Unexpected request: "+path);
                    if(path.equals(failStage)){status=failStatus;response=errorBody;}
                } catch(Throwable invalid){failure.set(invalid);status=500;}
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
                try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
            });server.start();
        }
        static String claims(String token) {return "{\"Token\":\""+token+"\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"12345\"}]}}";}
        MicrosoftBrowserAuth auth(MicrosoftBrowserAuth.Browser browser,long timeout) {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            return new MicrosoftBrowserAuth(CLIENT,new MicrosoftBrowserAuth.Endpoints(base+"/authorize",base+"/token",base+"/xbox",base+"/xsts",base+"/minecraft",base+"/profile"),uri->{
                authorization.set(uri);Map<String,String> query=MicrosoftBrowserAuth.query(uri.getRawQuery());
                assertEquals("S256",query.get("code_challenge_method"));assertEquals("code",query.get("response_type"));
                assertEquals("xboxlive.signin",query.get("scope"));assertFalse(query.containsKey("client_secret"));
                browser.open(uri);
            },timeout);
        }
        MicrosoftBrowserAuth auth() {return auth(uri->callback(uri,"mock-code",null,null),2000);}
        void healthy(){if(failure.get()!=null)throw new AssertionError(failure.get());}
        @Override public void close(){server.stop(0);healthy();}
    }
    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int count;
        while((count=in.read(bytes))!=-1)out.write(bytes,0,count);return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
    static int callback(URI authorization,String code,String error,String stateOverride) throws Exception {
        Map<String,String> query=MicrosoftBrowserAuth.query(authorization.getRawQuery());
        Map<String,String> values=MicrosoftBrowserAuth.params("state",stateOverride==null?query.get("state"):stateOverride);
        if(code!=null)values.put("code",code);if(error!=null)values.put("error",error);
        return request(query.get("redirect_uri")+"?"+MicrosoftBrowserAuth.form(values));
    }
    static int request(String url) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(1000);connection.setReadTimeout(1000);
        try {int status=connection.getResponseCode();try(InputStream in=status<400?connection.getInputStream():connection.getErrorStream()){
            String page=read(in);assertFalse(page.contains("mock-code"));assertFalse(page.contains("raw-secret"));
        }return status;}finally{connection.disconnect();}
    }
    static void assertClosed(URI authorization) throws Exception {
        String redirect=MicrosoftBrowserAuth.query(authorization.getRawQuery()).get("redirect_uri");
        try{request(redirect+"?state=unused&code=unused");fail("Callback listener must be closed");}catch(IOException expected){}
    }
    @Test public void fullPkceHttpFlowProducesMinecraftSessionAndClosesCallback() throws Exception {
        try(Services services=new Services()) {
            MicrosoftBrowserAuth auth=services.auth();Session result=auth.login(()->true);
            assertEquals("BrowserPlayer",result.getUsername());assertEquals(PROFILE,result.getPlayerID());assertEquals("minecraft-secret",result.getToken());
            assertEquals(Arrays.asList("/token","/xbox","/xsts","/minecraft","/profile"),services.calls);
            assertClosed(services.authorization.get());
        }
    }
    @Test public void bothLocalhostAddressFamiliesCanDeliverOneAttemptWhenIpv6IsAvailable() throws Exception {
        boolean ipv6;
        try(java.net.ServerSocket probe=new java.net.ServerSocket()) {
            probe.bind(new InetSocketAddress(InetAddress.getByName("::1"),0));ipv6=true;
        } catch(IOException | UnsupportedOperationException unavailable) {ipv6=false;}
        final boolean supportsIpv6=ipv6;
        try(Services services=new Services()) {
            MicrosoftBrowserAuth auth=services.auth(uri->{
                Map<String,String> query=MicrosoftBrowserAuth.query(uri.getRawQuery());
                URI redirect=URI.create(query.get("redirect_uri"));
                String params="?"+MicrosoftBrowserAuth.form(MicrosoftBrowserAuth.params("state",query.get("state"),"code","mock-code"));
                assertEquals(200,request("http://127.0.0.1:"+redirect.getPort()+"/callback"+params));
                if(supportsIpv6)assertEquals(200,request("http://[::1]:"+redirect.getPort()+"/callback"+params));
            },2000);
            assertEquals("BrowserPlayer",auth.login(()->true).getUsername());
            assertEquals(5,services.calls.size());assertClosed(services.authorization.get());
        }
    }
    @Test public void wrongStateDuplicateParametersAndWrongPathDoNotConsumeValidCallback() throws Exception {
        try(Services services=new Services()) {
            MicrosoftBrowserAuth auth=services.auth(uri->{
                assertEquals(400,callback(uri,"mock-code",null,"wrong-state"));
                assertEquals(400,callback(uri,"mock-code","access_denied",null));
                Map<String,String> query=MicrosoftBrowserAuth.query(uri.getRawQuery());String redirect=query.get("redirect_uri");
                assertEquals(400,request(redirect+"/wrong?state="+query.get("state")+"&code=mock-code"));
                assertEquals(400,request(redirect+"?code=mock-code"));
                try(Socket raw=new Socket("127.0.0.1",URI.create(redirect).getPort())) {
                    raw.setSoTimeout(1000);
                    String request="GET /callback?state="+query.get("state")+"&code=mock-code HTTP/1.1\r\nHost: attacker.invalid\r\nConnection: close\r\n\r\n";
                    raw.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                    String status=new BufferedReader(new InputStreamReader(raw.getInputStream(),StandardCharsets.US_ASCII)).readLine();
                    assertTrue(status,status.contains("400"));
                }
                assertEquals(400,request(redirect+"?state="+query.get("state")+"&state="+query.get("state")+"&code=mock-code"));
                assertEquals(200,callback(uri,"mock-code",null,null));
            },2000);
            assertEquals("BrowserPlayer",auth.login(()->true).getUsername());
        }
    }
    @Test public void denialTimeoutAndBrowserFailureCloseListenerWithoutTokenExchange() throws Exception {
        for(int scenario=0;scenario<3;scenario++)try(Services services=new Services()) {
            final int mode=scenario;
            MicrosoftBrowserAuth auth=services.auth(uri->{
                if(mode==0)callback(uri,null,"access_denied",null);
                if(mode==2)throw new IOException("raw-secret-browser-error");
            },120);
            try{auth.login(()->true);fail();}catch(Exception expected){
                if(expected instanceof LoginCoordinator.LoginUnavailableException)
                    assertFalse(((LoginCoordinator.LoginUnavailableException)expected).getUserMessage().contains("raw-secret"));
            }
            assertTrue(services.calls.isEmpty());assertClosed(services.authorization.get());
        }
    }
    @Test public void providerFailuresAreRedactedAndChildAndProfileErrorsAreActionable() throws Exception {
        for(String stage:Arrays.asList("/token","/xbox","/xsts","/minecraft","/profile"))try(Services services=new Services()) {
            services.failStage=stage;
            if(stage.equals("/xsts"))services.errorBody="{\"XErr\":2148916238,\"Message\":\"raw-secret-token\"}";
            if(stage.equals("/minecraft"))services.failStatus=403;
            if(stage.equals("/profile"))services.failStatus=404;
            try{services.auth().login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){
                String message=expected.getUserMessage();assertFalse(message.contains("raw-secret"));
                if(stage.equals("/xsts"))assertTrue(message,message.contains("family"));
                if(stage.equals("/minecraft"))assertTrue(message.contains("approval"));
                if(stage.equals("/profile"))assertTrue(message.contains("Java profile"));
            }
            assertClosed(services.authorization.get());
        }
    }
    @Test public void expiredCodeAndRegistrationFailuresGiveFixedRecoveryGuidance() throws Exception {
        for(String error:Arrays.asList("invalid_grant","invalid_client","unauthorized_client","invalid_scope"))try(Services services=new Services()) {
            services.failStage="/token";services.failStatus=400;
            services.errorBody="{\"error\":\""+error+"\",\"error_description\":\"raw-secret-code-and-verifier\"}";
            try{services.auth().login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){
                String message=expected.getUserMessage();assertFalse(message.contains("raw-secret"));
                assertTrue(message,error.equals("invalid_grant")?message.contains("try again"):message.contains("registration"));
            }
            assertEquals(Collections.singletonList("/token"),services.calls);assertClosed(services.authorization.get());
        }
    }
    @Test public void malformedOversizedAndUnknownMicrosoftErrorsUseRedactedFallback() throws Exception {
        for(String body:Arrays.asList("raw-secret-not-json","{\"error\":\"raw-secret-unknown\"}",
                "{\"error\":\"invalid_client\",\"description\":\""+String.join("",Collections.nCopies(5000,"s"))+"\"}"))try(Services services=new Services()) {
            services.failStage="/token";services.failStatus=400;services.errorBody=body;
            try{services.auth().login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){
                assertEquals("Microsoft sign-in could not be completed. Please try again; the current session was retained.",expected.getUserMessage());
            }
            assertEquals(Collections.singletonList("/token"),services.calls);assertClosed(services.authorization.get());
        }
    }
    @Test public void temporaryAuthorizationFailureIsNotReportedAsUserDenial() throws Exception {
        try(Services services=new Services()) {
            MicrosoftBrowserAuth auth=services.auth(uri->callback(uri,null,"temporarily_unavailable",null),2000);
            try{auth.login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){
                assertTrue(expected.getUserMessage().contains("try again"));assertFalse(expected.getUserMessage().contains("declined"));
            }
            assertTrue(services.calls.isEmpty());assertClosed(services.authorization.get());
        }
    }
    @Test public void missingClientIdStopsBeforeBrowserOrNetworkWithSetupGuidance() throws Exception {
        try(Services services=new Services()) {
            AtomicBoolean opened=new AtomicBoolean();
            String base="http://127.0.0.1:"+services.server.getAddress().getPort();
            MicrosoftBrowserAuth auth=new MicrosoftBrowserAuth("",new MicrosoftBrowserAuth.Endpoints(base+"/authorize",base+"/token",base+"/xbox",base+"/xsts",base+"/minecraft",base+"/profile"),uri->opened.set(true),2000);
            try{auth.login(()->true);fail();}catch(LoginCoordinator.LoginUnavailableException expected){
                assertEquals(MicrosoftBrowserAuth.SETUP,expected.getUserMessage());
            }
            assertFalse(opened.get());assertTrue(services.calls.isEmpty());
        }
    }
    @Test public void coordinatorAppliesOnMainThreadAndPersistsOnlyProfileMetadataWhileKeepingLegacyRecords() throws Exception {
        try(Services services=new Services()) {
            File directory=temporary.newFolder();AccountManager manager=new AccountManager(directory);
            Account legacy=new Account("legacy@example.invalid","legacy-password","Legacy",Account.Provider.MICROSOFT);
            manager.addAccount(legacy);manager.save();
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();Thread owner=Thread.currentThread();
            Session previous=new Session("Previous","","","mojang");AtomicReference<Session> session=new AtomicReference<>(previous);
            LoginCoordinator coordinator=new LoginCoordinator(manager,new AccountAuthenticator(services.auth()),main::add,
                    (result,provider)->{assertSame(owner,Thread.currentThread());session.set(result);},account->assertSame(owner,Thread.currentThread()));
            LoginCoordinator.Attempt attempt=coordinator.begin(LoginRequest.microsoftBrowser(),null);
            Thread worker=new Thread(attempt);worker.start();worker.join(3000);assertFalse(worker.isAlive());assertSame(previous,session.get());
            Runnable task;while((task=main.poll())!=null)task.run();assertEquals("BrowserPlayer",session.get().getUsername());
            assertEquals(2,manager.getAccounts().size());assertSame(legacy,manager.getAccountById(legacy.getId()));assertEquals("legacy-password",legacy.getPassword());
            Account browser=manager.getLastAlt();assertEquals(PROFILE,browser.getEmail());assertNull(browser.getPassword());assertNull(browser.getMicrosoftAccessToken());
            String disk=new String(Files.readAllBytes(new File(directory,"alts.json").toPath()),StandardCharsets.UTF_8);
            for(String secret:Arrays.asList("minecraft-secret","ms-secret","xbox-secret","xsts-secret","mock-code",services.verifier.get()))assertFalse(disk.contains(secret));
        }
    }
    @Test public void cancellationClosesCallbackAndLateAttemptCannotReplaceNewerOfflineSession() throws Exception {
        try(Services services=new Services()) {
            CountDownLatch opened=new CountDownLatch(1);MicrosoftBrowserAuth auth=services.auth(uri->opened.countDown(),2000);
            AccountManager manager=new AccountManager(temporary.newFolder());Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicReference<Session> session=new AtomicReference<>();
            LoginCoordinator coordinator=new LoginCoordinator(manager,new AccountAuthenticator(auth),main::add,(result,provider)->session.set(result),account->{});
            LoginCoordinator.Attempt oldAttempt=coordinator.begin(LoginRequest.microsoftBrowser(),null);
            Thread old=new Thread(oldAttempt);old.start();assertTrue(opened.await(2,TimeUnit.SECONDS));
            LoginCoordinator.Attempt newer=coordinator.begin(new LoginRequest(new Account("Offline","","Offline"),false,false),null);
            Thread next=new Thread(newer);next.start();oldAttempt.cancel();next.join(2000);old.join(2000);assertFalse(old.isAlive());
            Runnable task;while((task=main.poll())!=null)task.run();assertEquals("Offline",session.get().getUsername());assertTrue(services.calls.isEmpty());assertClosed(services.authorization.get());
        }
    }
    @Test public void cancellationDuringTokenExchangeCannotApplyOrPersistItsLateResult() throws Exception {
        try(Services services=new Services()) {
            services.tokenEntered=new CountDownLatch(1);services.releaseToken=new CountDownLatch(1);
            AccountManager manager=new AccountManager(temporary.newFolder());Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicReference<Session> session=new AtomicReference<>();
            LoginCoordinator coordinator=new LoginCoordinator(manager,new AccountAuthenticator(services.auth()),main::add,(result,provider)->session.set(result),account->{});
            Thread old=new Thread(coordinator.begin(LoginRequest.microsoftBrowser(),null));old.start();assertTrue(services.tokenEntered.await(2,TimeUnit.SECONDS));
            coordinator.invalidate();services.releaseToken.countDown();old.join(3000);assertFalse(old.isAlive());
            Runnable task;while((task=main.poll())!=null)task.run();assertNull(session.get());assertTrue(manager.getAccounts().isEmpty());assertEquals(Collections.singletonList("/token"),services.calls);
        }
    }
    @Test public void unexpectedBrowserFailureIsRedactedBeforeItReachesTheUi() throws Exception {
        try(Services services=new Services()) {
            AccountManager manager=new AccountManager(temporary.newFolder());Queue<Runnable> main=new ConcurrentLinkedQueue<>();
            Session previous=new Session("Previous","","","mojang");AtomicReference<Session> current=new AtomicReference<>(previous);
            MicrosoftBrowserAuth auth=services.auth(uri->{throw new IOException("raw-secret-browser-url?code=private");},2000);
            LoginCoordinator coordinator=new LoginCoordinator(manager,new AccountAuthenticator(auth),main::add,(result,provider)->current.set(result),account->{});
            LoginCoordinator.Attempt attempt=coordinator.begin(LoginRequest.microsoftBrowser(),null);
            Thread worker=new Thread(attempt);worker.start();worker.join(2000);assertFalse(worker.isAlive());
            Runnable task;while((task=main.poll())!=null)task.run();
            assertSame(previous,current.get());assertTrue(manager.getAccounts().isEmpty());
            assertFalse(attempt.getStatus().contains("raw-secret"));assertFalse(attempt.getStatus().contains("code="));
            assertTrue(attempt.getStatus().contains("retained"));assertClosed(services.authorization.get());
        }
    }
}
