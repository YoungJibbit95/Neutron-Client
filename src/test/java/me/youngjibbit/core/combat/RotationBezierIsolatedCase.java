package me.youngjibbit.core.combat;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Fresh stable client per case; propagates only the explicit legacy-capture switch. */
public final class RotationBezierIsolatedCase {
    private RotationBezierIsolatedCase() { }
    public static void run(String method) throws Exception {
        Path base=Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        if(!"target".equals(base.getFileName().toString()))base=base.resolve("target");
        Path directory=base.resolve("bezier-cases");Files.createDirectories(directory);
        Path output=Files.createTempFile(directory,method+"-",".log");
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Dfile.encoding=UTF-8","-Duser.home="+System.getProperty("user.home"),
                "-Dneutron.bezier.captureLegacy="+Boolean.getBoolean("neutron.bezier.captureLegacy"),
                "-cp",System.getProperty("java.class.path"),RotationBezierIsolatedCase.class.getName(),method)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)){child.destroy();throw new AssertionError("Case timed out: "+output);}
        String result=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);System.out.print(result);
        assertEquals("Child output: "+output+"\n"+result,0,child.exitValue());
        assertTrue(result,result.contains("OK (1 test)"));
    }
    public static void main(String[] args) {
        JUnitCore junit=new JUnitCore();junit.addListener(new TextListener(System.out));
        Result result=junit.run(Request.method(RotationBezierConsumerChecks.class,args[0]));
        System.exit(result.wasSuccessful()&&result.getRunCount()==1&&result.getIgnoreCount()==0?0:1);
    }
}
