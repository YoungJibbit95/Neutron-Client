package me.youngjibbit.account;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import static org.junit.Assert.*;

public class JavaFxRuntimeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void realJava8WebViewLoadsLocalHtmlAndJavascriptWithoutOpeningAWindow() throws Exception {
        Path directory = temporary.newFolder("different-working-directory").toPath();
        Path log = directory.resolve("fx-probe.log");
        Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Xmx384m", "-Duser.home=" + directory, "-cp", System.getProperty("java.class.path"),
                JavaFxRuntimeProbe.class.getName()).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue("FX probe timed out", child.waitFor(60, TimeUnit.SECONDS));
            String output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            System.out.print(output);
            assertEquals(output, 0, child.exitValue());
            assertTrue(output, output.contains("JAVAFX8_WEBVIEW_OK"));
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
    }

    @Test public void corruptedCacheIsRepairedAndRepeatUnpackKeepsValidFiles() throws Exception {
        byte[] archive = archive(false, false);
        Path cache = temporary.newFolder().toPath();
        Path root = JavaFxRuntime.unpack(new ByteArrayInputStream(archive), cache);
        Path jar = root.resolve("lib/ext/jfxrt.jar");
        byte[] expected = Files.readAllBytes(jar);
        Files.write(jar, "corrupt".getBytes(StandardCharsets.UTF_8));
        JavaFxRuntime.unpack(new ByteArrayInputStream(archive), cache);
        assertArrayEquals(expected, Files.readAllBytes(jar));
        java.nio.file.attribute.FileTime modified = Files.getLastModifiedTime(jar);
        JavaFxRuntime.unpack(new ByteArrayInputStream(archive), cache);
        assertEquals(modified, Files.getLastModifiedTime(jar));
    }

    @Test public void pathTraversalAndIncompleteBundlesAreRejected() throws Exception {
        for (boolean traversal : new boolean[]{true, false}) {
            Path cache = temporary.newFolder().toPath();
            try { JavaFxRuntime.unpack(new ByteArrayInputStream(archive(traversal, !traversal)), cache); fail(); }
            catch (IOException expected) { }
            assertFalse(Files.exists(cache.resolve("escaped.dll")));
        }
    }

    private static byte[] archive(boolean traversal, boolean missing) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : Arrays.asList("lib/ext/jfxrt.jar", "bin/glass.dll", "bin/jfxwebkit.dll", "bin/prism_sw.dll")) {
                if (missing && name.endsWith("jfxwebkit.dll")) continue;
                zip.putNextEntry(new ZipEntry(JavaFxRuntime.ROOT + "/" + name));
                zip.write(("synthetic:" + name).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            if (traversal) { zip.putNextEntry(new ZipEntry(JavaFxRuntime.ROOT + "/../escaped.dll")); zip.write(1); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
}
