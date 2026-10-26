package me.youngjibbit.core.combat;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Keep Unsafe static-final client fixtures out of the shared test JVM. */
public class SprintOrderingTest {
    @Test public void runRealSprintConsumersInChildJvm() throws Exception {
        Path output = Files.createTempFile(Paths.get(System.getProperty("user.dir")), "sprint-ordering-", ".log");
        Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Duser.home=" + System.getProperty("user.home"), "-cp", System.getProperty("java.class.path"),
                "org.junit.runner.JUnitCore", "me.youngjibbit.core.combat.SprintOrderingChecks")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) { child.destroy(); throw new AssertionError("Sprint checks timed out: " + output); }
        String result = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        System.out.print(result); assertEquals(result, 0, child.exitValue()); assertTrue(result, result.contains("OK (9 tests)"));
    }
}
