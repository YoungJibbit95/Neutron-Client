package me.neutron.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

public class ClientBootstrapTest {
    private String lwjglPath;
    private String jinputPath;

    @Before public void saveNativeProperties() {
        lwjglPath = System.getProperty("org.lwjgl.librarypath");
        jinputPath = System.getProperty("net.java.games.input.librarypath");
    }

    @After public void restoreNativeProperties() {
        restore("org.lwjgl.librarypath", lwjglPath);
        restore("net.java.games.input.librarypath", jinputPath);
    }

    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }

    private String[] fixture(String... extra) throws Exception {
        Path root = Files.createTempDirectory("neutron-bootstrap-");
        Path assets = Files.createDirectories(root.resolve("assets/indexes"));
        Files.write(assets.resolve("1.8.json"), "{\"objects\":{}}".getBytes("UTF-8"));
        Path natives = Files.createDirectory(root.resolve("natives"));
        for (String name : Arrays.asList("lwjgl64.dll", "OpenAL64.dll", "jinput-raw_64.dll",
                "liblwjgl64.so", "libopenal64.so", "libjinput-linux64.so",
                "liblwjgl.dylib", "openal.dylib", "libjinput-osx.jnilib")) {
            Files.createFile(natives.resolve(name));
        }
        List<String> args = new ArrayList<String>(Arrays.asList("--gameDir",
                Paths.get("").toAbsolutePath().toString(), "--assetsDir", assets.getParent().toString(),
                "--nativesDir", natives.toString(), "--assetIndex", "1.8", "--version", "Development-test"));
        args.addAll(Arrays.asList(extra));
        return args.toArray(new String[args.size()]);
    }

    private void rejects(String[] args, String message) {
        try {
            ClientBootstrap.validate(args);
            fail("Expected rejection: " + message);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    @Test public void productionArgumentsContainNoTestPathsOrLauncherAuthentication() throws Exception {
        List<String> args = ClientBootstrap.validate(fixture("--username", "LauncherSmoke", "--validateOnly"));
        assertFalse(args.contains("--nativesDir"));
        assertFalse(args.contains("--validateOnly"));
        assertFalse(args.toString().contains("test_natives"));
        assertEquals("0", args.get(args.indexOf("--accessToken") + 1));
        assertTrue(Paths.get(System.getProperty("org.lwjgl.librarypath")).isAbsolute());
    }

    @Test public void rejectsDuplicatePathsAndEqualsSyntaxBypass() throws Exception {
        rejects(fixture("--assetsDir", "elsewhere"), "duplicate");
        rejects(fixture("--gameDir=elsewhere"), "Unknown launcher option");
    }

    @Test public void rejectsWrongWorkingDirectory() throws Exception {
        String[] args = fixture();
        args[1] = Files.createTempDirectory("neutron-wrong-cwd").toString();
        rejects(args, "working directory");
    }

    @Test public void rejectsMissingNativesAndIndex() throws Exception {
        String[] args = fixture();
        args[5] = Files.createTempDirectory("neutron-no-natives").toString();
        rejects(args, "native library missing");
        args = fixture();
        args[3] = Files.createTempDirectory("neutron-no-assets").toString();
        rejects(args, "Asset index");
    }

    @Test public void rejectsInvalidDimensionsAndInjectedCredentials() throws Exception {
        rejects(fixture("--width", "0"), "Window size");
        rejects(fixture("--height", "NaN"), "integer");
        rejects(fixture("--accessToken", "synthetic-secret"), "inside the client");
    }

    @Test public void rejectsJava21AndNativeArmProcess() throws Exception {
        String version = System.getProperty("java.specification.version");
        String arch = System.getProperty("os.arch");
        try {
            System.setProperty("java.specification.version", "21");
            rejects(fixture(), "Java 8");
            System.setProperty("java.specification.version", version);
            System.setProperty("os.arch", "aarch64");
            rejects(fixture(), "x64 Java process");
        } finally {
            System.setProperty("java.specification.version", version);
            System.setProperty("os.arch", arch);
        }
    }
}
