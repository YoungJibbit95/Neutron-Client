package me.youngjibbit.release;

import org.junit.Test;
import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import me.youngjibbit.module.Module;
import static org.junit.Assert.*;

/** Explicit post-package JUnit runner, intentionally outside Surefire's *Test pattern.
 * Run with only test-classes, the Assembly JAR, JUnit and Hamcrest on the classpath.
 * Never invokes Main, constructs a Module or reads player data. */
public class PackagedRuntimeCheck {
    private Path jar() { return Paths.get(Objects.requireNonNull(System.getProperty("neutron.release.jar"), "set neutron.release.jar")); }
    private Path root() { return Paths.get(Objects.requireNonNull(System.getProperty("neutron.release.root"), "set neutron.release.root")); }

    @Test public void manifestResolvesToJava8Main() throws Exception {
        try (JarFile jar = new JarFile(jar().toFile())) {
            assertEquals("net.minecraft.client.main.Main", jar.getManifest().getMainAttributes().getValue("Main-Class"));
            Class<?> main = Class.forName("net.minecraft.client.main.Main", false, getClass().getClassLoader());
            assertTrue(Modifier.isStatic(main.getMethod("main", String[].class).getModifiers()));
            assertEquals(void.class, main.getMethod("main", String[].class).getReturnType());
            for (JarEntry e : Collections.list(jar.entries())) {
                if ((e.getName().startsWith("net/minecraft/") || e.getName().startsWith("net/optifine/")) && e.getName().endsWith(".class")) {
                    try (DataInputStream in = new DataInputStream(jar.getInputStream(e))) {
                        assertEquals(0xCAFEBABE, in.readInt()); in.readUnsignedShort();
                        assertTrue(e.getName(), in.readUnsignedShort() <= 52);
                    }
                }
            }
        }
    }

    @Test public void runtimeDependenciesArePresentOnceAndLinkWithoutInitialization() throws Exception {
        String[] names = {"com.google.gson.Gson", "com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService", "com.sun.jna.Native", "com.sun.jna.platform.win32.Kernel32", "oshi.SystemInfo", "io.netty.bootstrap.Bootstrap", "org.apache.logging.log4j.core.LoggerContext", "org.lwjgl.Sys", "org.lwjgl.opengl.Display", "org.lwjgl.opengl.Pbuffer", "org.lwjgl.openal.AL", "net.java.games.input.ControllerEnvironment", "tv.twitch.Core", "paulscode.sound.codecs.CodecJOrbis", "paulscode.sound.codecs.CodecWav", "paulscode.sound.libraries.LibraryLWJGLOpenAL"};
        for (String name : names) {
            assertEquals(name, 1, Collections.list(getClass().getClassLoader().getResources(name.replace('.', '/') + ".class")).size());
            Class.forName(name, false, getClass().getClassLoader());
        }
    }

    @Test public void serviceProvidersAndLog4jIndexSurvivePackaging() throws Exception {
        try (JarFile jar = new JarFile(jar().toFile())) {
            assertNotNull(jar.getJarEntry("META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"));
            int services = 0;
            for (JarEntry e : Collections.list(jar.entries())) {
                if (!e.getName().startsWith("META-INF/services/") || e.isDirectory()) continue;
                services++;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(jar.getInputStream(e), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String provider = line.split("#", 2)[0].trim();
                        if (!provider.isEmpty()) {
                            String path = provider.replace('.', '/');
                            assertTrue(e.getName() + " -> " + provider, jar.getJarEntry(path + ".class") != null || jar.getJarEntry(path + ".SCL.lombok") != null);
                        }
                    }
                }
            }
            assertTrue("SPI descriptors missing", services > 0);
        }
    }

    @Test public void everyProjectResourceIsBytePreserved() throws Exception {
        Path resources = root().resolve("src/main/resources");
        try (JarFile jar = new JarFile(jar().toFile()); Stream<Path> paths = Files.walk(resources)) {
            for (Iterator<Path> it = paths.filter(Files::isRegularFile).iterator(); it.hasNext();) {
                Path p = it.next(); String name = resources.relativize(p).toString().replace('\\', '/');
                // Maven's default resource excludes omit Finder metadata, not game resources.
                if (p.getFileName().toString().equals(".DS_Store")) continue;
                JarEntry entry = jar.getJarEntry(name); assertNotNull(name, entry);
                try (InputStream in = jar.getInputStream(entry); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
                    assertArrayEquals(name, Files.readAllBytes(p), bytes.toByteArray());
                }
            }
        }
    }

    @Test public void reflectiveModuleDiscoveryFindsAllSourceModules() throws Exception {
        Reflections reflections = new Reflections(new ConfigurationBuilder().setUrls(jar().toUri().toURL())
                .filterInputsBy(new FilterBuilder().includePackage("me.youngjibbit.module.impl"))
                .setScanners(Scanners.SubTypes));
        Set<String> actual = new HashSet<>();
        for (Class<? extends Module> type : reflections.getSubTypesOf(Module.class)) actual.add(type.getName());
        Path modules = root().resolve("src/main/java/me/youngjibbit/module/impl");
        try (Stream<Path> paths = Files.walk(modules)) {
            for (Iterator<Path> it = paths.filter(p -> p.toString().endsWith(".java")).iterator(); it.hasNext();) {
                String name = "me.youngjibbit.module.impl." + modules.relativize(it.next()).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.java$", "");
                // Helpers such as DelayedPacketThread live here but are not Modules.
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                if (Module.class.isAssignableFrom(type)) assertTrue("reflection lost " + name, actual.contains(name));
            }
        }
        assertFalse(actual.isEmpty());
    }
}
