package me.youngjibbit.core.combat;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Run all binding checks with a stable client in a fresh, normally JIT-compiled JVM.
 * Replacing Module's static-final client hundreds of times via Unsafe would invalidate
 * the fixture assumptions: HotSpot is allowed to inline that reference. */
public class RotationModuleBindingTest {
    @Test public void executeAllProductiveBindingChecksWithAStableClient() throws Exception {
        Path output=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"rotation-bindings-",".log");
        String java=Paths.get(System.getProperty("java.home"),"bin","java.exe").toString();
        Process child=new ProcessBuilder(java,"-Duser.home="+System.getProperty("user.home"),"-cp",System.getProperty("java.class.path"),
                "org.junit.runner.JUnitCore","me.youngjibbit.core.combat.RotationModuleBindingChecks")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)) { child.destroy();throw new AssertionError("Binding checks timed out; output: "+output); }
        String result=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);
        System.out.print(result);
        assertEquals(result,0,child.exitValue());
        assertTrue(result,result.contains("OK (19 tests)"));
    }
}
