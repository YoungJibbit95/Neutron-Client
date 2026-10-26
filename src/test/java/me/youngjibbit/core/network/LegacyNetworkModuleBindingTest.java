package me.youngjibbit.core.network;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Separate stable-client process, preserving normal HotSpot treatment of Module's static-final client. */
public class LegacyNetworkModuleBindingTest {
    @Test public void executeAllLegacyNetworkModuleBindings() throws Exception {
        Path output=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"legacy-network-bindings-",".log");
        String java=Paths.get(System.getProperty("java.home"),"bin","java.exe").toString();
        Process child=new ProcessBuilder(java,"-Duser.home="+System.getProperty("user.home"),"-cp",System.getProperty("java.class.path"),"org.junit.runner.JUnitCore","me.youngjibbit.core.combat.LegacyNetworkModuleBindingChecks").redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)){child.destroy();throw new AssertionError("Legacy network checks timed out: "+output);}
        String result=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);System.out.print(result);assertEquals(result,0,child.exitValue());assertTrue(result,result.contains("OK (7 tests)"));
    }
}
