package me.youngjibbit.account;

import fr.litarvan.openauth.microsoft.*;
import net.minecraft.util.Session;
import javax.swing.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** OpenAuth's JavaFX login, with attempt-local cancellation and validated OAuth return state. */
public final class OpenAuthBrowserAuth {
    interface Preparation { void run() throws Exception; }
    interface Window extends AutoCloseable {
        CompletableFuture<String> result();
        void close();
    }
    interface Windows { Window open(URI authorization) throws Exception; }
    interface Exchange { Session login(AuthTokens tokens, BooleanSupplier active) throws Exception; }
    private final Preparation preparation;
    private final Windows windows;
    private final Exchange exchange;
    private final long timeoutMillis;
    private final Set<Attempt> attempts = new HashSet<>();

    public OpenAuthBrowserAuth() {
        this(JavaFxRuntime::load, authorization -> new FxWindow(authorization), new MicrosoftBrowserAuth(), 180000);
    }

    OpenAuthBrowserAuth(Preparation preparation, Windows windows, MicrosoftBrowserAuth services, long timeoutMillis) {
        this(preparation, windows, (tokens, active) -> services.loginEmbedded(tokens.getAccessToken(), active), timeoutMillis);
    }

    OpenAuthBrowserAuth(Preparation preparation, Windows windows, Exchange exchange, long timeoutMillis) {
        this.preparation = preparation; this.windows = windows; this.exchange = exchange; this.timeoutMillis = timeoutMillis;
    }

    public synchronized void cancel() { for (Attempt attempt : new ArrayList<>(attempts)) attempt.close(); }

    public Session login(BooleanSupplier active) throws Exception {
        check(active);
        try { preparation.run(); }
        catch (Exception | LinkageError failure) {
            throw unavailable("Microsoft sign-in window could not load. Use a complete Windows x64 Java 8 build; your current session was retained.");
        }
        check(active);
        byte[] entropy = new byte[32]; new SecureRandom().nextBytes(entropy);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        URI authorization = authorization(state);
        Attempt attempt = new Attempt();
        synchronized (this) { attempts.add(attempt); }
        try {
            SwingUtilities.invokeLater(() -> {
                if (attempt.response.isDone() || !active.getAsBoolean()) return;
                try {
                    Window window = windows.open(authorization);
                    attempt.window.set(window);
                    if (attempt.response.isDone() || !active.getAsBoolean()) { window.close(); return; }
                    window.result().whenComplete((value, failure) -> {
                        if (failure != null) attempt.response.completeExceptionally(failure);
                        else attempt.response.complete(value);
                    });
                } catch (Exception | LinkageError failure) { attempt.response.completeExceptionally(failure); }
            });
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            String response;
            for (;;) {
                check(active);
                if (System.nanoTime() >= end) throw unavailable("Microsoft sign-in timed out. Try again; your current session was retained.");
                try { response = attempt.response.get(100, TimeUnit.MILLISECONDS); break; }
                catch (TimeoutException waiting) { }
                catch (ExecutionException | CancellationException failure) {
                    throw unavailable("Microsoft sign-in window closed or failed. Try again; your current session was retained.");
                }
            }
            check(active);
            AuthTokens tokens = tokens(response, state);
            Session session;
            try { session = exchange.login(tokens, active); }
            catch (LoginCoordinator.LoginUnavailableException failure) { throw failure; }
            catch (Exception failure) {
                throw unavailable("Microsoft/Xbox sign-in failed. Check account eligibility and try again; your current session was retained.");
            }
            check(active);
            if (session == null || session.getUsername() == null || !session.getUsername().matches("[A-Za-z0-9_]{1,16}")
                    || session.getPlayerID() == null || !session.getPlayerID().matches("[a-fA-F0-9]{32}")
                    || session.getToken() == null || session.getToken().isEmpty())
                throw unavailable("Minecraft returned an invalid profile; your current session was retained.");
            return session;
        } finally {
            attempt.close();
            synchronized (this) { attempts.remove(attempt); }
        }
    }

    static URI authorization(String state) throws Exception {
        // OpenAuth's existing public-client compatibility flow, not a Neutron app registration.
        Map<String, String> parameters = MicrosoftBrowserAuth.params("client_id", MicrosoftAuthenticator.XBOX_LIVE_CLIENT_ID,
                "redirect_uri", MicrosoftAuthenticator.MICROSOFT_REDIRECTION_ENDPOINT,
                "scope", MicrosoftAuthenticator.XBOX_LIVE_SERVICE_SCOPE, "response_type", "token",
                "state", state, "prompt", "select_account");
        return URI.create(MicrosoftAuthenticator.MICROSOFT_AUTHORIZATION_ENDPOINT + "?" + MicrosoftBrowserAuth.form(parameters));
    }

    static AuthTokens tokens(String response, String state) throws Exception {
        if (response == null) throw unavailable("Microsoft sign-in cancelled; your current session was retained.");
        try {
            URI uri = URI.create(response);
            if (!"https".equals(uri.getScheme()) || !"login.live.com".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || !"/oauth20_desktop.srf".equals(uri.getPath())) throw new IllegalArgumentException();
            Map<String, String> values = MicrosoftBrowserAuth.query(uri.getRawFragment());
            if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                    values.getOrDefault("state", "").getBytes(StandardCharsets.UTF_8))
                    || values.containsKey("error") || !"bearer".equalsIgnoreCase(values.get("token_type"))
                    || values.getOrDefault("access_token", "").isEmpty()) throw new IllegalArgumentException();
            // Refresh credentials are deliberately discarded. Each new session uses interactive login.
            return new AuthTokens(values.get("access_token"), "");
        } catch (Exception invalid) {
            throw unavailable("Microsoft returned an invalid sign-in response; your current session was retained.");
        }
    }

    private static void check(BooleanSupplier active) throws LoginCoordinator.LoginUnavailableException {
        if (!active.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw unavailable("Microsoft sign-in cancelled; your current session was retained.");
    }
    private static LoginCoordinator.LoginUnavailableException unavailable(String text) { return new LoginCoordinator.LoginUnavailableException(text); }

    private static final class Attempt {
        final CompletableFuture<String> response = new CompletableFuture<>();
        final AtomicReference<Window> window = new AtomicReference<>();
        void close() {
            response.cancel(false);
            SwingUtilities.invokeLater(() -> { Window owned = window.getAndSet(null); if (owned != null) owned.close(); });
        }
    }

    /** Uses only OpenAuth's public Swing API at compile time; FX is loaded before this class is constructed. */
    private static final class FxWindow extends LoginFrame implements Window {
        private final CompletableFuture<String> result;
        private volatile boolean closed;
        FxWindow(URI authorization) throws Exception {
            super();
            Class.forName("javafx.application.Platform").getMethod("setImplicitExit", boolean.class).invoke(null, false);
            setTitle("Neutron - Microsoft sign-in");
            result = start(authorization.toASCIIString());
        }
        @Override protected void init(String url) {
            if (closed) return;
            super.init(url);
            if (closed) close();
        }
        @Override public CompletableFuture<String> result() { return result; }
        @Override public void close() {
            closed = true;
            if (result != null) result.cancel(false);
            // Stop WebKit navigation and detach its scene as well as disposing our own Swing window.
            try {
                Class.forName("javafx.application.Platform").getMethod("runLater", Runnable.class).invoke(null, (Runnable) () -> {
                    try {
                        Object panel = getContentPane();
                        Object scene = panel.getClass().getMethod("getScene").invoke(panel);
                        if (scene != null) {
                            Object root = scene.getClass().getMethod("getRoot").invoke(scene);
                            Object engine = root.getClass().getMethod("getEngine").invoke(root);
                            engine.getClass().getMethod("load", String.class).invoke(engine, new Object[]{null});
                        }
                        panel.getClass().getMethod("setScene", Class.forName("javafx.scene.Scene")).invoke(panel, new Object[]{null});
                    } catch (Exception ignored) { /* The frame may have closed before scene initialization. */ }
                });
            } catch (Exception ignored) { }
            SwingUtilities.invokeLater(this::dispose);
        }
    }
}
