package me.youngjibbit.module.impl.render;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Explicit native check, excluded from default *Test selection. All mutable client/GL state stays in the child. */
public class NametagsLabelNativeCheck {
    @Test public void executeLabelDecisionsInAnIsolatedNativeJvm() throws Exception {
        assertTrue("Run with -Dpulsar.nativeLabels=true and Windows LWJGL natives",Boolean.getBoolean("pulsar.nativeLabels"));
        Path output=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"nametag-labels-",".log");
        String java=Paths.get(System.getProperty("java.home"),"bin","java.exe").toString();
        Process child=new ProcessBuilder(java,"-Duser.home="+System.getProperty("user.home"),
                "-Dpulsar.nativeLabels="+System.getProperty("pulsar.nativeLabels"),"-cp",System.getProperty("java.class.path"),
                "org.junit.runner.JUnitCore","me.youngjibbit.rendercheck.NametagsLabelChecks")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)) { child.destroy();throw new AssertionError("Label checks timed out; output: "+output); }
        String result=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);System.out.print(result);
        assertEquals(result,0,child.exitValue());assertTrue(result,result.contains("OK (6 tests)"));
    }
}
