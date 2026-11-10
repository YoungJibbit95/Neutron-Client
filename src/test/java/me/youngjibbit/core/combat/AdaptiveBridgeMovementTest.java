package me.youngjibbit.core.combat;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Isolates the legacy static-final Minecraft fixture from HotSpot inlining. */
public class AdaptiveBridgeMovementTest {
    @Test public void executeAdaptiveMovementConsumersInAnIsolatedJvm() throws Exception {
        Path directory = Paths.get(System.getProperty("user.dir"), "adaptive-bridge-checks");
        Files.createDirectories(directory);
        Path output = Files.createTempFile(directory, "movement-", ".log");
        Path executable = Paths.get(System.getProperty("java.home"), "bin", "java");
        if (!Files.exists(executable)) executable = Paths.get(System.getProperty("java.home"), "bin", "java.exe");
        Process child = new ProcessBuilder(executable.toString(), "-Duser.home=" + System.getProperty("user.home"),
                "-cp", System.getProperty("java.class.path"), "org.junit.runner.JUnitCore",
                "me.youngjibbit.core.combat.AdaptiveBridgeMovementChecks")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) {
            child.destroy(); throw new AssertionError("Adaptive Bridge movement checks timed out; output: " + output);
        }
        String result = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        System.out.print(result);
        assertEquals(result, 0, child.exitValue());
        assertTrue(result, result.contains("OK (3 tests)"));
    }
}
