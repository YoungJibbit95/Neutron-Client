package me.youngjibbit.account;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Loads the bundled JavaFX 8 SDK without replacing Java or changing LWJGL's native paths. */
public final class JavaFxRuntime {
    public static final String RESOURCE = "/neutron/runtime/javafx8-win-x64.zip";
    static final String ROOT = "zulu8.96.0.205-ca-fx-jre8.0.504-win_x64";
    private static boolean loaded;
    private JavaFxRuntime() { }

    public static synchronized void load() throws Exception {
        if (loaded) return;
        if (!"1.8".equals(System.getProperty("java.specification.version"))
                || !System.getProperty("os.name", "").startsWith("Windows")
                || !"64".equals(System.getProperty("sun.arch.data.model")))
            throw new IOException("The bundled sign-in window requires Windows x64 and Java 8.");
        ClassLoader loader = JavaFxRuntime.class.getClassLoader();
        if (!(loader instanceof URLClassLoader)) throw new IOException("Unsupported Java 8 class loader.");
        Path cache = Paths.get(System.getProperty("user.home"), ".neutron", "runtime", "javafx8");
        Path runtime;
        try (InputStream resource = JavaFxRuntime.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("JavaFX is missing from this build. Rebuild with Maven before signing in.");
            runtime = unpack(resource, cache);
        }
        Method append = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        append.setAccessible(true);
        append.invoke(loader, runtime.resolve("lib/ext/jfxrt.jar").toUri().toURL());
        // Resolve before opening any window, so packaging failures remain ordinary login errors.
        Class.forName("javafx.embed.swing.JFXPanel", false, loader);
        Class.forName("javafx.scene.web.WebView", false, loader);
        loaded = true;
    }

    static Path unpack(InputStream source, Path cache) throws Exception {
        Files.createDirectories(cache);
        // Versioned directory plus per-entry verification supports independent client JVMs.
        Path runtime = cache.resolve(ROOT);
        Set<String> found = new HashSet<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith(ROOT + "/") || name.contains("\\") || !found.add(name))
                    throw new IOException("Invalid JavaFX runtime entry.");
                Path target = cache.resolve(name).normalize();
                if (!target.startsWith(runtime) || name.contains("/../")) throw new IOException("Invalid JavaFX runtime path.");
                // Refuse linked cache paths rather than writing through them.
                for (Path part = target; part != null && part.startsWith(cache); part = part.getParent())
                    if (Files.isSymbolicLink(part)) throw new IOException("Linked JavaFX cache paths are not supported.");
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), "fx-", ".tmp");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try {
                    try (OutputStream out = Files.newOutputStream(temporary)) {
                        byte[] bytes = new byte[8192]; int count;
                        while ((count = zip.read(bytes)) != -1) {
                            total += count;
                            if (total > 200L * 1024 * 1024) throw new IOException("JavaFX runtime is too large.");
                            digest.update(bytes, 0, count); out.write(bytes, 0, count);
                        }
                    }
                    byte[] expected = digest.digest();
                    if (!Files.isRegularFile(target) || !MessageDigest.isEqual(expected, hash(target))) {
                        try {
                            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
                        } catch (IOException concurrentInstall) {
                            // Another client can have installed and loaded this DLL in the meantime.
                            if (!Files.isRegularFile(target) || !MessageDigest.isEqual(expected, hash(target))) throw concurrentInstall;
                        }
                    }
                } finally { Files.deleteIfExists(temporary); }
            }
        }
        for (String required : Arrays.asList("lib/ext/jfxrt.jar", "bin/glass.dll", "bin/jfxwebkit.dll", "bin/prism_sw.dll"))
            if (!found.contains(ROOT + "/" + required)) throw new IOException("Incomplete JavaFX runtime.");
        return runtime;
    }

    private static byte[] hash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = in.read(bytes)) != -1) digest.update(bytes, 0, count);
        }
        return digest.digest();
    }
}
