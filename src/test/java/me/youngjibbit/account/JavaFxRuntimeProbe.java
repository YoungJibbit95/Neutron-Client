package me.youngjibbit.account;

import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real native toolkit/WebKit probe. Creates no JFrame, Stage, account or network request. */
public final class JavaFxRuntimeProbe {
    public static void main(String[] args) {
        try {
            String javaHome = System.getProperty("java.home"), natives = System.getProperty("java.library.path");
            JavaFxRuntime.load();
            if (!javaHome.equals(System.getProperty("java.home")) || !natives.equals(System.getProperty("java.library.path")))
                throw new AssertionError("Host runtime or native path changed");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try { Class.forName("javafx.embed.swing.JFXPanel").newInstance(); }
                catch (Throwable error) { failure.set(error); }
            });
            if (failure.get() != null) throw new AssertionError(failure.get());
            Class<?> platform = Class.forName("javafx.application.Platform");
            platform.getMethod("setImplicitExit", boolean.class).invoke(null, false);
            for (int i = 0; i < 3; i++) {
                AtomicReference<Object> engine = new AtomicReference<>();
                CountDownLatch created = new CountDownLatch(1);
                runLater(() -> {
                    try {
                        Object view = Class.forName("javafx.scene.web.WebView").newInstance();
                        Object web = view.getClass().getMethod("getEngine").invoke(view); engine.set(web);
                        web.getClass().getMethod("loadContent", String.class).invoke(web, "<html><body>Neutron local probe</body></html>");
                    } catch (Throwable error) { failure.set(error); }
                    finally { created.countDown(); }
                });
                if (!created.await(15, TimeUnit.SECONDS) || failure.get() != null) throw new AssertionError("WebView initialization", failure.get());
                boolean success = false;
                for (int poll = 0; poll < 100 && !success; poll++) {
                    CompletableFuture<Boolean> check = new CompletableFuture<>();
                    runLater(() -> {
                        try {
                            Object web = engine.get(); Object worker = web.getClass().getMethod("getLoadWorker").invoke(web);
                            Object state = Class.forName("javafx.concurrent.Worker").getMethod("getState").invoke(worker);
                            if (!"SUCCEEDED".equals(state.toString())) { check.complete(false); return; }
                            Object answer = web.getClass().getMethod("executeScript", String.class).invoke(web, "6*7");
                            Object body = web.getClass().getMethod("executeScript", String.class).invoke(web, "document.body.textContent");
                            if (!"42".equals(answer.toString()) || !"Neutron local probe".equals(body)) throw new AssertionError("WebKit result");
                            web.getClass().getMethod("load", String.class).invoke(web, new Object[]{null});
                            check.complete(true);
                        } catch (Throwable error) { check.completeExceptionally(error); }
                    });
                    success = check.get(5, TimeUnit.SECONDS);
                    if (!success) Thread.sleep(50);
                }
                if (!success) throw new AssertionError("Local HTML did not load");
            }
            JavaFxRuntime.load(); // Reusing the loaded SDK must remain safe.
            System.out.println("JAVAFX8_WEBVIEW_OK: three local HTML/JS loads; no visible windows, no account/network access");
            platform.getMethod("exit").invoke(null);
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    private static void runLater(Runnable action) throws Exception {
        Class.forName("javafx.application.Platform").getMethod("runLater", Runnable.class).invoke(null, action);
    }
}
