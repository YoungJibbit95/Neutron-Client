package me.youngjibbit.core.combat;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Isolate the legacy static-final client fixture from later tests and their JIT-compiled code.
 * Restoring Module.mc/Eierlecken.mc with Unsafe cannot invalidate HotSpot's inlined references.
 */
public class SpeedMovementBasisTest {
    @Test public void executeAllSpeedConsumersInAnIsolatedJvm() throws Exception {
        Path output = Files.createTempFile(Paths.get(System.getProperty("user.dir")), "speed-movement-", ".log");
        String java = Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString();
        Process child = new ProcessBuilder(java, "-Duser.home=" + System.getProperty("user.home"),
                "-cp", System.getProperty("java.class.path"), "org.junit.runner.JUnitCore",
                "me.youngjibbit.core.combat.SpeedMovementBasisChecks")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) {
            child.destroy(); throw new AssertionError("Speed movement checks timed out; output: " + output);
        }
        String result = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        System.out.print(result);
        assertEquals(result, 0, child.exitValue());
        assertTrue(result, result.contains("OK (5 tests)"));
    }
}
