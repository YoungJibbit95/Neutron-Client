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
import java.util.function.BooleanSupplier;

/** Public-client authorization code flow. Secrets remain in memory for this attempt only. */
public final class MicrosoftBrowserAuth {
    public static final String SETUP = "Set your own registered desktop app ID (pulsar.microsoft.clientId); register http://localhost/callback and check the Minecraft API review requirements.";
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
    private final Set<Callback> callbacks = new HashSet<>();

    public MicrosoftBrowserAuth() {
        this(null, LIVE, uri -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw unavailable("No system browser is available. Configure a desktop browser and try again.");
            Desktop.getDesktop().browse(uri);
        }, 180000);
    }
    MicrosoftBrowserAuth(String clientId, Endpoints endpoints, Browser browser, long timeoutMillis) {
        this.clientId=clientId; this.endpoints=endpoints; this.browser=browser; this.timeoutMillis=timeoutMillis;
    }
    public synchronized void cancel() {
        for (Callback callback : new ArrayList<>(callbacks)) callback.close();
    }

    public Session login(BooleanSupplier active) throws Exception {
        String id = clientId != null ? clientId : System.getProperty("pulsar.microsoft.clientId",
                System.getenv("PULSAR_MICROSOFT_CLIENT_ID") == null ? "" : System.getenv("PULSAR_MICROSOFT_CLIENT_ID"));
        if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw unavailable(SETUP);
        check(active);
        String verifier=random(64), state=random(32);
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        try (Callback callback = new Callback(state)) {
            synchronized (this) { callbacks.add(callback); }
            try {
                check(active);
                String redirect = callback.redirect();
                Map<String,String> query = params("client_id",id,"response_type","code","redirect_uri",redirect,
                        "response_mode","query","scope","xboxlive.signin","state",state,
                        "code_challenge",challenge,"code_challenge_method","S256","prompt","select_account");
                browser.open(URI.create(endpoints.authorize + "?" + form(query)));
                String code=callback.await(active, timeoutMillis);
                check(active);
                JsonObject tokens=request(endpoints.token,"POST","application/x-www-form-urlencoded",
                        form(params("client_id",id,"grant_type","authorization_code","code",code,
                                "redirect_uri",redirect,"code_verifier",verifier,"scope","xboxlive.signin")),null,"Microsoft");
                String access=string(tokens,"access_token");
                check(active);
                JsonObject properties=new JsonObject();
                properties.addProperty("AuthMethod","RPS"); properties.addProperty("SiteName","user.auth.xboxlive.com");
                properties.addProperty("RpsTicket","d="+access);
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
            } finally { synchronized (this) { callbacks.remove(callback); } }
        }
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
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
        connection.setRequestMethod(method); connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("x-xbl-contract-version","1");
        if (bearer!=null) connection.setRequestProperty("Authorization","Bearer "+bearer);
        try {
            if (body!=null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type",contentType);
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                try(OutputStream out=connection.getOutputStream()) { out.write(bytes); }
            }
            int status=connection.getResponseCode();
            if (status<200 || status>=300) {
                if (stage.equals("Xbox authorization")) {
                    long error=0;
                    try(InputStream in=connection.getErrorStream()) {
                        if(in!=null) {
                            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int count;
                            while((count=in.read(bytes))!=-1) {
                                if(out.size()+count>4096)throw new IOException("Error response too large");
                                out.write(bytes,0,count);
                            }
                            if(out.size()>0)error=new JsonParser().parse(new String(out.toByteArray(),StandardCharsets.UTF_8))
                                    .getAsJsonObject().get("XErr").getAsLong();
                        }
                    } catch(IOException | RuntimeException ignored) { }
                    if(error==2148916238L || error==2148916229L)
                        throw unavailable("Xbox family or child-account restrictions prevented sign-in. A parent or organizer must review the Microsoft family and Xbox privacy settings; the current session was retained.");
                    if(error==2148916233L)
                        throw unavailable("Create or finish this account's Xbox profile at xbox.com, then try again. The current session was retained.");
                }
                if (stage.equals("Minecraft Services") && status==403)
                    throw unavailable("Minecraft Services rejected this app or account. Verify the app's Minecraft API approval and account eligibility; the current session was retained.");
                if (stage.equals("Minecraft profile") && status==404)
                    throw unavailable("This account has no available Minecraft Java profile. The current session was retained.");
                throw unavailable(stage+" sign-in failed. The current session was retained.");
            }
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=in.read(buffer))!=-1) {
                    if(out.size()+count>1048576) throw new IOException("Response too large");
                    out.write(buffer,0,count);
                }
                return new JsonParser().parse(new String(out.toByteArray(),StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } finally { connection.disconnect(); }
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
        Callback(String state) throws IOException {
            server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);
            HttpServer additional=null;
            try {
                additional=HttpServer.create(new InetSocketAddress(InetAddress.getByName("::1"),server.getAddress().getPort()),8);
            } catch(IOException | UnsupportedOperationException ipv6Unavailable) {
                // IPv4 remains available on systems with IPv6 disabled.
            }
            ipv6=additional;
            server.createContext("/callback",exchange -> receive(exchange,state));
            if(ipv6!=null)ipv6.createContext("/callback",exchange -> receive(exchange,state));
            server.start();if(ipv6!=null)ipv6.start();
        }
        String redirect() { return "http://localhost:"+server.getAddress().getPort()+"/callback"; }
        void receive(HttpExchange exchange,String state) throws IOException {
            int status=400; String message="Invalid sign-in callback. Return to Pulsar.";
            try {
                if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals("/callback")
                        || exchange.getRequestURI().isAbsolute()) throw new IOException("Invalid callback");
                String host=exchange.getRequestHeaders().getFirst("Host");
                if (!("localhost:"+server.getAddress().getPort()).equals(host)
                        && !("127.0.0.1:"+server.getAddress().getPort()).equals(host)
                        && !("[::1]:"+server.getAddress().getPort()).equals(host)) throw new IOException("Invalid host");
                Map<String,String> values=query(exchange.getRequestURI().getRawQuery());
                if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
                        values.getOrDefault("state","").getBytes(StandardCharsets.US_ASCII))) throw new IOException("Invalid state");
                if (values.containsKey("code")==values.containsKey("error"))throw new IOException("Invalid callback");
                if (values.containsKey("error")) code.completeExceptionally(unavailable("Microsoft sign-in was declined. The current session was retained."));
                else if(values.get("code").isEmpty())throw new IOException("Missing code");
                else code.complete(values.get("code"));
                status=200; message="Sign-in response received. You can close this tab and return to Pulsar.";
            } catch (RuntimeException | IOException invalid) { /* Never echo provider/query values. */ }
            byte[] bytes=message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control","no-store");
            exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");
            exchange.sendResponseHeaders(status,bytes.length);
            try(OutputStream out=exchange.getResponseBody()) { out.write(bytes); }
            finally { exchange.close(); }
        }
        String await(BooleanSupplier active,long timeoutMillis) throws Exception {
            long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            for(;;) {
                check(active);
                if(System.nanoTime()>=end)throw unavailable("Microsoft sign-in timed out. Please try again; the current session was retained.");
                try { return code.get(100,TimeUnit.MILLISECONDS); }
                catch(TimeoutException ignored) { }
                catch(ExecutionException failure) {
                    if(failure.getCause() instanceof LoginCoordinator.LoginUnavailableException)
                        throw (LoginCoordinator.LoginUnavailableException)failure.getCause();
                    throw unavailable("Microsoft sign-in cancelled. The current session was retained.");
                }
            }
        }
        @Override public void close() { server.stop(0);if(ipv6!=null)ipv6.stop(0);code.cancel(false); }
    }
}
