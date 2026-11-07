package me.youngjibbit.account;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class MicrosoftAuthConfigTest {
    private static final String ID = MicrosoftBrowserAuthTest.CLIENT;
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void savedPublicIdSurvivesNewInstancesWithoutSavingCredentials() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("home/.neutron/microsoft-auth.properties");
        MicrosoftAuthConfig config = config(new Properties(), new HashMap<>(), file);
        assertEquals("", config.clientId());
        config.save("  " + ID + "  ");
        assertEquals(ID, config(new Properties(), new HashMap<>(), file).clientId());
        Properties saved = new Properties();
        try (InputStream in = Files.newInputStream(file)) { saved.load(in); }
        assertEquals(Collections.singleton("clientId"), saved.stringPropertyNames());
        try (java.util.stream.Stream<Path> files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    @Test public void explicitOverridesTakePrecedenceAndBlankOverridesFallThrough() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("config.properties");
        Properties props = new Properties(); Map<String, String> env = new HashMap<>();
        MicrosoftAuthConfig config = config(props, env, file); config.save(ID);
        env.put("PULSAR_MICROSOFT_CLIENT_ID", " legacy-env "); assertEquals("legacy-env", config.clientId());
        env.put("NEUTRON_MICROSOFT_CLIENT_ID", "new-env"); assertEquals("new-env", config.clientId());
        props.setProperty("pulsar.microsoft.clientId", "legacy-property"); assertEquals("legacy-property", config.clientId());
        props.setProperty("neutron.microsoft.clientId", "new-property"); assertEquals("new-property", config.clientId());
        try { config.save(ID); fail("Do not pretend a saved ID replaces an explicit override"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("override")); }
        props.setProperty("neutron.microsoft.clientId", " "); props.setProperty("pulsar.microsoft.clientId", "");
        env.clear(); assertEquals(ID, config.clientId());
    }

    @Test public void invalidIdsAndUnreadableConfigurationDoNotSilentlyFallback() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("config.properties");
        MicrosoftAuthConfig config = config(new Properties(), new HashMap<>(), file);
        config.save(ID); byte[] before = Files.readAllBytes(file);
        for (String value : Arrays.asList("", "not-an-app-id", "000000004C12AE6F", "00000000-0000-0000-0000-000000000000")) {
            assertFalse(MicrosoftAuthConfig.valid(value));
            try { config.save(value); fail(); } catch (IllegalArgumentException expected) { }
            assertArrayEquals(before, Files.readAllBytes(file));
        }
        Files.write(file, "clientId=\\uNOTHEX".getBytes(StandardCharsets.UTF_8));
        try { config.clientId(); fail(); } catch (IOException expected) { }
    }

    @Test public void sameUserConfigurationLoadsInSeparateJvmsWithAllThreeWorkingDirectories() throws Exception {
        Path home = temporary.newFolder("home").toPath();
        config(new Properties(), new HashMap<>(), home.resolve(".neutron/microsoft-auth.properties")).save(ID);
        // Separate JVMs prove lookup is independent of the IDE/launcher process cwd.
        for (String start : Arrays.asList("intellij-test_run", "minecraft-game", "neutron-launcher-game")) {
            Path cwd = temporary.newFolder(start).toPath(), log = cwd.resolve("probe.log");
            String classpath = absoluteClasspath();
            ProcessBuilder builder = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-Duser.home=" + home, "-cp", classpath, Probe.class.getName());
            builder.environment().remove("NEUTRON_MICROSOFT_CLIENT_ID");
            builder.environment().remove("PULSAR_MICROSOFT_CLIENT_ID");
            Process child = builder.directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(15, TimeUnit.SECONDS));
                String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
                assertEquals(output, 0, child.exitValue()); assertTrue(output, output.contains("CONFIGURED"));
            } finally { if (child.isAlive()) child.destroyForcibly(); }
        }
    }

    private static String absoluteClasspath() {
        List<String> entries = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            entries.add(Paths.get(entry).toAbsolutePath().toString());
        return String.join(File.pathSeparator, entries);
    }
    private static MicrosoftAuthConfig config(Properties props, Map<String, String> env, Path file) {
        return new MicrosoftAuthConfig(props, env, file);
    }
    public static class Probe {
        public static void main(String[] args) throws Exception {
            assertEquals(ID, new MicrosoftAuthConfig().clientId());
            System.out.println("CONFIGURED");
        }
    }
}
