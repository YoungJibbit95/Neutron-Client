package me.youngjibbit.core.world;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/**
 * Runs the full movement sequences without warming another suite's reflected
 * static-final Minecraft fixture into HotSpot-compiled production consumers.
 * Every actual placement/physics assertion remains in the child checks.
 */
public class AdaptiveBridgeStabilityFlowTest {
    @Test public void executePhysicalBridgeSequencesInAFreshJvm() throws Exception {
        Path directory=Paths.get(System.getProperty("user.dir"),"adaptive-bridge-checks");
        Files.createDirectories(directory);
        Path output=Files.createTempFile(directory,"stability-",".log");
        Path executable=Paths.get(System.getProperty("java.home"),"bin","java");
        if(!Files.exists(executable))executable=Paths.get(System.getProperty("java.home"),"bin","java.exe");
        Process child=new ProcessBuilder(executable.toString(),
                "-Duser.home="+System.getProperty("user.home"),
                "-Djava.library.path="+System.getProperty("java.library.path"),
                "-cp",System.getProperty("java.class.path"),"org.junit.runner.JUnitCore",
                "me.youngjibbit.core.world.AdaptiveBridgeStabilityFlowChecks")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)) {
            child.destroy();throw new AssertionError("Adaptive Bridge physics checks timed out; output: "+output);
        }
        String result=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);
        System.out.print(result);
        assertEquals(result,0,child.exitValue());
        assertTrue(result,result.contains("OK (4 tests)"));
    }
}
