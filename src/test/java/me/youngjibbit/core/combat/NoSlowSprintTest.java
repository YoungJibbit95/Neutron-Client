package me.youngjibbit.core.combat;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** A separate JVM owns the synthetic legacy client for the complete check class. */
public class NoSlowSprintTest {
    @Test public void runActualSlowdownAndSprintConsumers() throws Exception {
        Path output = Files.createTempFile(Paths.get(System.getProperty("user.dir")), "noslow-sprint-", ".log");
        Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Duser.home=" + System.getProperty("user.home"), "-cp", System.getProperty("java.class.path"),
                "org.junit.runner.JUnitCore", "me.youngjibbit.core.combat.NoSlowSprintChecks")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) { child.destroy(); throw new AssertionError("NoSlow checks timed out: " + output); }
        String result = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        System.out.print(result); assertEquals(result, 0, child.exitValue()); assertTrue(result, result.contains("OK (6 tests)"));
    }
}
