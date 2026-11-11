package me.youngjibbit.account;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.Session;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** System-browser authorization code flow with PKCE. Credentials remain in memory for this attempt only. */
public final class MicrosoftBrowserAuth {
    public static final String SETUP = "Microsoft sign-in is not configured. Add Neutron's registered application ID in Microsoft setup.";
    interface Browser { void open(URI uri) throws Exception; }
    static final class Endpoints {
        final URI authorize, token, xbox, xsts, minecraft, profile;
        Endpoints(String authorize, String token, String xbox, String xsts, String minecraft, String profile) {
            this.authorize=URI.create(authorize); this.token=URI.create(token); this.xbox=URI.create(xbox);
            this.xsts=URI.create(xsts); this.minecraft=URI.create(minecraft); this.profile=URI.create(profile);
        }
    }
    private static final Endpoints LIVE = new Endpoints(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            "https://user.auth.xboxlive.com/user/authenticate", "https://xsts.auth.xboxlive.com/xsts/authorize",
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            "https://api.minecraftservices.com/minecraft/profile");
    private final Endpoints endpoints;
    private final Browser browser;
    private final String clientId;
    private final long timeoutMillis;
    private final MicrosoftAuthConfig configuration;
    private final Set<Callback> callbacks = new HashSet<>();

    public MicrosoftBrowserAuth() {
        this(null, LIVE, uri -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw unavailable("No system browser is available. Configure a desktop browser and try again.");
            Desktop.getDesktop().browse(uri);
        }, TimeUnit.MINUTES.toMillis(5));
    }
    MicrosoftBrowserAuth(String clientId, Endpoints endpoints, Browser browser, long timeoutMillis) {
        this(clientId, endpoints, browser, timeoutMillis, new MicrosoftAuthConfig());
    }
    MicrosoftBrowserAuth(String clientId, Endpoints endpoints, Browser browser, long timeoutMillis, MicrosoftAuthConfig configuration) {
        this.clientId=clientId; this.endpoints=endpoints; this.browser=browser; this.timeoutMillis=timeoutMillis;
        this.configuration=configuration;
    }
    public synchronized void cancel() {
        for (Callback callback : new ArrayList<>(callbacks)) callback.close();
    }

    public Session login(BooleanSupplier active) throws Exception {
        check(active);
        String id;
        try { id = clientId != null ? clientId.trim() : configuration.clientId(); }
        catch (IOException | SecurityException failure) {
            throw new LoginCoordinator.LoginUnavailableException("The Microsoft app configuration could not be read. Check Microsoft setup.", true);
        }
        if (!MicrosoftAuthConfig.valid(id))
            throw new LoginCoordinator.LoginUnavailableException(SETUP, true);
        check(active);
        String verifier=random(64), state=random(32);
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        Callback listener;
        try { listener = new Callback(state); }
        catch (IOException | SecurityException failure) {
            throw unavailable("Could not start the local sign-in callback. Check local firewall settings and try again; the current session was retained.");
        }
        try (Callback callback = listener) {
            synchronized (this) { callbacks.add(callback); }
            try {
                check(active);
                String redirect = callback.redirect();
                Map<String,String> query = params("client_id",id,"response_type","code","redirect_uri",redirect,
                        "response_mode","query","scope","xboxlive.signin","state",state,
                        "code_challenge",challenge,"code_challenge_method","S256","prompt","select_account");
                try { browser.open(URI.create(endpoints.authorize + "?" + form(query))); }
                catch (Exception | LinkageError failure) {
                    throw unavailable("Could not open your default browser. Check the system browser settings and retry; the current session was retained.");
                }
                String code=callback.await(active, timeoutMillis);
                callback.close(); // The browser response has been sent; no listener is needed during token exchange.
                check(active);
                JsonObject tokens=request(endpoints.token,"POST","application/x-www-form-urlencoded",
                        form(params("client_id",id,"grant_type","authorization_code","code",code,
                                "redirect_uri",redirect,"code_verifier",verifier,"scope","xboxlive.signin")),null,"Microsoft");
                if (!"Bearer".equalsIgnoreCase(string(tokens,"token_type")))
                    throw unavailable("Microsoft returned an unsupported token response. Please try again; the current session was retained.");
                return exchangeMicrosoftToken(string(tokens,"access_token"),active);
            } finally { synchronized (this) { callbacks.remove(callback); } }
        }
    }

    private Session exchangeMicrosoftToken(String accessToken, BooleanSupplier active) throws Exception {
        check(active);
        JsonObject properties=new JsonObject();
        properties.addProperty("AuthMethod","RPS"); properties.addProperty("SiteName","user.auth.xboxlive.com");
        properties.addProperty("RpsTicket","d="+accessToken);
        JsonObject xbox=request(endpoints.xbox,"POST","application/json",
                envelope(properties,"http://auth.xboxlive.com").toString(),null,"Xbox Live");
        String userHash=hash(xbox);
        check(active);
        properties=new JsonObject(); properties.addProperty("SandboxId","RETAIL");
        JsonArray userTokens=new JsonArray(); userTokens.add(string(xbox,"Token")); properties.add("UserTokens",userTokens);
        JsonObject xsts=request(endpoints.xsts,"POST","application/json",
                envelope(properties,"rp://api.minecraftservices.com/").toString(),null,"Xbox authorization");
        if (!userHash.equals(hash(xsts))) throw unavailable("Xbox account claims did not match. The current session was retained.");
        check(active);
        JsonObject login=new JsonObject(); login.addProperty("identityToken","XBL3.0 x="+userHash+";"+string(xsts,"Token"));
        JsonObject minecraft=request(endpoints.minecraft,"POST","application/json",login.toString(),null,"Minecraft Services");
        String minecraftToken=string(minecraft,"access_token");
        check(active);
        JsonObject profile=request(endpoints.profile,"GET",null,null,minecraftToken,"Minecraft profile");
        String name=string(profile,"name"), profileId=string(profile,"id");
        if (!name.matches("[a-zA-Z0-9_]{1,16}") || !profileId.matches("[0-9a-fA-F]{32}"))
            throw unavailable("Minecraft returned an invalid Java profile. The current session was retained.");
        check(active);
        return new Session(name,profileId.toLowerCase(Locale.ROOT),minecraftToken,"mojang");
    }

    private static JsonObject envelope(JsonObject properties,String relyingParty) {
        JsonObject json=new JsonObject(); json.add("Properties",properties);
        json.addProperty("RelyingParty",relyingParty); json.addProperty("TokenType","JWT"); return json;
    }
    private static String hash(JsonObject json) throws Exception {
        try { return string(json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(),"uhs"); }
        catch (RuntimeException invalid) { throw unavailable("Xbox returned incomplete account claims. The current session was retained."); }
    }
    private static String string(JsonObject object,String name) throws Exception {
        JsonElement value=object.get(name);
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isEmpty() || value.getAsString().length()>65536)
            throw unavailable("The sign-in service returned an incomplete response. The current session was retained.");
        return value.getAsString();
    }
    private static JsonObject request(URI uri,String method,String contentType,String body,String bearer,String stage) throws Exception {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            connection.setRequestMethod(method); connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("x-xbl-contract-version","1");
            if (bearer!=null) connection.setRequestProperty("Authorization","Bearer "+bearer);
            if (body!=null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type",contentType);
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                // Keep the small request buffered: Java 8 streaming mode can hide a 401 error body.
                try(OutputStream out=connection.getOutputStream()) { out.write(bytes); }
            }
            int status=connection.getResponseCode();
            if (status<200 || status>=300) {
                if (stage.equals("Microsoft")) {
                    JsonElement error=errorResponse(connection).get("error");
                    throw oauthFailure(error!=null && error.isJsonPrimitive() && error.getAsJsonPrimitive().isString()
                            ? error.getAsString() : "");
                }
                if (stage.equals("Xbox authorization")) {
                    long error=0;
                    try { error=errorResponse(connection).get("XErr").getAsLong(); }
                    catch(RuntimeException ignored) { }
                    if(error==2148916238L || error==2148916229L)
                        throw unavailable("Xbox family or child-account restrictions prevented sign-in. A parent or organizer must review the Microsoft family and Xbox privacy settings; the current session was retained.");
                    if(error==2148916233L)
                        throw unavailable("Create or finish this account's Xbox profile at xbox.com, then try again. The current session was retained.");
                }
                if (stage.equals("Minecraft Services") && status==403)
                    throw unavailable("Minecraft Services rejected this app or account. Verify the app's Minecraft API approval and account eligibility; the current session was retained.");
                if (stage.equals("Minecraft profile") && status==404)
                    throw unavailable("This account has no available Minecraft Java profile. The current session was retained.");
                if (status==429 || status>=500)
                    throw unavailable(stage+" is temporarily unavailable. Please try again later; the current session was retained.");
                throw unavailable(stage+" sign-in failed. The current session was retained.");
            }
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=in.read(buffer))!=-1) {
                    if(out.size()+count>1048576) throw new IOException("Response too large");
                    out.write(buffer,0,count);
                }
                JsonElement result=new JsonParser().parse(new String(out.toByteArray(),StandardCharsets.UTF_8));
                if (!result.isJsonObject())
                    throw unavailable(stage+" returned an incomplete response. Please try again; the current session was retained.");
                return result.getAsJsonObject();
            }
        } catch (SocketTimeoutException timeout) {
            throw unavailable(stage+" connection timed out. Please try again; the current session was retained.");
        } catch (IOException connectionFailure) {
            throw unavailable("Could not connect to "+stage+". Check your connection and try again; the current session was retained.");
        } catch (RuntimeException malformedResponse) {
            throw unavailable(stage+" returned an invalid response. Please try again; the current session was retained.");
        } finally { if(connection!=null)connection.disconnect(); }
    }
    private static JsonObject errorResponse(HttpURLConnection connection) {
        try(InputStream in=connection.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null)return new JsonObject();
            byte[] bytes=new byte[1024];int count;
            while((count=in.read(bytes))!=-1) {
                if(out.size()+count>4096)return new JsonObject();
                out.write(bytes,0,count);
            }
            return new JsonParser().parse(new String(out.toByteArray(),StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(IOException | RuntimeException ignored) { return new JsonObject(); }
    }
    private static LoginCoordinator.LoginUnavailableException oauthFailure(String error) {
        if(error.equals("access_denied"))
            return unavailable("Microsoft sign-in was declined. The current session was retained.");
        if(error.equals("invalid_grant"))
            return unavailable("Microsoft sign-in code expired or was rejected. Please try again; the current session was retained.");
        if(error.equals("invalid_client") || error.equals("unauthorized_client") || error.equals("invalid_scope"))
            return new LoginCoordinator.LoginUnavailableException("Microsoft app registration rejected sign-in. Check the client ID, desktop redirect and Xbox scope; the current session was retained.",true);
        return unavailable("Microsoft sign-in could not be completed. Please try again; the current session was retained.");
    }
    static Map<String,String> params(String... values) {
        Map<String,String> map=new LinkedHashMap<>(); for(int i=0;i<values.length;i+=2)map.put(values[i],values[i+1]); return map;
    }
    static String form(Map<String,String> values) throws UnsupportedEncodingException {
        StringBuilder result=new StringBuilder();
        for(Map.Entry<String,String> entry:values.entrySet()) {
            if(result.length()>0)result.append('&');
            result.append(URLEncoder.encode(entry.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(),"UTF-8"));
        }
        return result.toString();
    }
    static Map<String,String> query(String raw) throws IOException {
        Map<String,String> values=new HashMap<>();
        if(raw==null || raw.length()>8192) throw new IOException("Invalid callback");
        for(String part:raw.split("&")) {
            String[] pair=part.split("=",2);
            if(pair.length!=2)throw new IOException("Invalid callback");
            String name=URLDecoder.decode(pair[0],"UTF-8"), value=URLDecoder.decode(pair[1],"UTF-8");
            if(values.put(name,value)!=null)throw new IOException("Duplicate callback parameter");
        }
        return values;
    }
    private static String random(int length) {
        byte[] bytes=new byte[length];new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static void check(BooleanSupplier active) throws Exception {
        if(!active.getAsBoolean() || Thread.currentThread().isInterrupted())throw unavailable("Microsoft sign-in cancelled. The current session was retained.");
    }
    private static LoginCoordinator.LoginUnavailableException unavailable(String message) {
        return new LoginCoordinator.LoginUnavailableException(message);
    }

    private static final class Callback implements AutoCloseable {
        final HttpServer server, ipv6;
        final CompletableFuture<String> code=new CompletableFuture<>();
        final AtomicBoolean accepted=new AtomicBoolean(), closed=new AtomicBoolean();
        Callback(String state) throws IOException {
            server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);
            HttpServer additional=null;
            try {
                additional=HttpServer.create(new InetSocketAddress(InetAddress.getByName("::1"),server.getAddress().getPort()),8);
            } catch(IOException | UnsupportedOperationException | SecurityException ipv6Unavailable) {
                // IPv4 remains available on systems with IPv6 disabled.
            }
            ipv6=additional;
            try {
                server.createContext("/callback",exchange -> receive(exchange,state));
                if(ipv6!=null)ipv6.createContext("/callback",exchange -> receive(exchange,state));
                server.start();if(ipv6!=null)ipv6.start();
            } catch (RuntimeException failure) {
                close(); throw new IOException("Could not start callback listener");
            }
        }
        String redirect() { return "http://localhost:"+server.getAddress().getPort()+"/callback"; }
        void receive(HttpExchange exchange,String state) throws IOException {
            int status=400; String message="Invalid sign-in callback. Return to Neutron and try again.";
            String result=null;
            LoginCoordinator.LoginUnavailableException failure=null;
            boolean valid=false;
            try {
                if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getRawPath().equals("/callback")
                        || exchange.getRequestURI().isAbsolute() || !exchange.getRemoteAddress().getAddress().isLoopbackAddress())
                    throw new IOException("Invalid callback");
                List<String> hosts=exchange.getRequestHeaders().get("Host");
                if (hosts==null || hosts.size()!=1) throw new IOException("Invalid host");
                String host=exchange.getRequestHeaders().getFirst("Host");
                if (!("localhost:"+server.getAddress().getPort()).equalsIgnoreCase(host)
                        && !("127.0.0.1:"+server.getAddress().getPort()).equals(host)
                        && !("[::1]:"+server.getAddress().getPort()).equals(host)) throw new IOException("Invalid host");
                Map<String,String> values=query(exchange.getRequestURI().getRawQuery());
                if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
                        values.getOrDefault("state","").getBytes(StandardCharsets.US_ASCII))) throw new IOException("Invalid state");
                if (values.containsKey("code")==values.containsKey("error"))throw new IOException("Invalid callback");
                if (values.containsKey("error")) {
                    if (values.get("error").isEmpty()) throw new IOException("Missing error");
                    failure=oauthFailure(values.get("error"));
                } else {
                    result=values.get("code");
                    if(result.isEmpty())throw new IOException("Missing code");
                }
                if(closed.get() || !accepted.compareAndSet(false,true)) {
                    status=409; message="This sign-in response was already received or cancelled. Return to Neutron.";
                } else {
                    valid=true; status=200;
                    message=failure==null
                            ? "Sign-in response received. Return to Neutron to finish signing in. You can close this tab."
                            : "Microsoft sign-in was not completed. Return to Neutron for details. You can close this tab.";
                }
            } catch (RuntimeException | IOException invalid) { /* Never echo provider/query values. */ }
            byte[] bytes=message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");
            exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            try {
                exchange.sendResponseHeaders(status,bytes.length);
                try(OutputStream out=exchange.getResponseBody()) { out.write(bytes); }
            } finally {
                exchange.close();
                // Do not let a fast exchange close the listener before the browser receives its response.
                if(valid) {
                    if(failure==null)code.complete(result);
                    else code.completeExceptionally(failure);
                }
            }
        }
        String await(BooleanSupplier active,long timeoutMillis) throws Exception {
            long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            for(;;) {
                check(active);
                if(System.nanoTime()>=end)throw unavailable("Microsoft sign-in timed out. Please try again; the current session was retained.");
                try { return code.get(100,TimeUnit.MILLISECONDS); }
                catch(TimeoutException ignored) { }
                catch(CancellationException cancelled) {
                    throw unavailable("Microsoft sign-in cancelled. The current session was retained.");
                }
                catch(ExecutionException failure) {
                    if(failure.getCause() instanceof LoginCoordinator.LoginUnavailableException)
                        throw (LoginCoordinator.LoginUnavailableException)failure.getCause();
                    throw unavailable("Microsoft sign-in cancelled. The current session was retained.");
                }
            }
        }
        @Override public void close() {
            if(closed.compareAndSet(false,true)) {
                code.cancel(false);server.stop(0);if(ipv6!=null)ipv6.stop(0);
            }
        }
    }
}
