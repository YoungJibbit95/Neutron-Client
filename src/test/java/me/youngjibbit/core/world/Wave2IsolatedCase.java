package me.youngjibbit.core.world;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Existing child-JVM fixture pattern, once per outer JUnit case with ordinary Java 8 JIT. */
public final class Wave2IsolatedCase {
    private Wave2IsolatedCase() { }

    public static void run(Class<?> checks, String method) throws Exception {
        Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        if (!"target".equals(base.getFileName().toString())) base = base.resolve("target");
        Path outputDirectory = base.resolve("wave2-isolated-cases");
        Files.createDirectories(outputDirectory);
        Path output = Files.createTempFile(outputDirectory, method + "-", ".log");
        String java = Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString();
        Process child = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-Duser.home=" + System.getProperty("user.home"),
                "-cp", System.getProperty("java.class.path"), Wave2IsolatedCase.class.getName(), checks.getName(), method)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) {
            child.destroy();
            throw new AssertionError("Isolated case timed out: " + output);
        }
        String result = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        System.out.print(result);
        assertEquals("isolated assertion failed; output=" + output + "\n" + result, 0, child.exitValue());
        assertTrue("exactly one unchanged assertion case must execute: " + result, result.contains("OK (1 test)"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("checks class and method required");
        JUnitCore junit = new JUnitCore();
        junit.addListener(new TextListener(System.out));
        Result result = junit.run(Request.method(Class.forName(args[0]), args[1]));
        System.exit(result.wasSuccessful() && result.getRunCount() == 1 && result.getIgnoreCount() == 0 ? 0 : 1);
    }
}
