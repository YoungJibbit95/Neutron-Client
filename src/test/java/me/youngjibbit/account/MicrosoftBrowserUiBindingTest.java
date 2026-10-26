package me.youngjibbit.account;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** The UI fixture's static coordinator/screen references are confined to a disposable child JVM. */
public class MicrosoftBrowserUiBindingTest {
    @Test public void browserActionsUseTheActualLoginWorkflowInIsolation() throws Exception {
        Path log=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"microsoft-browser-ui-",".log");
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Duser.home="+System.getProperty("user.home"),"-cp",System.getProperty("java.class.path"),
                "org.junit.runner.JUnitCore","me.youngjibbit.account.MicrosoftBrowserUiBindingChecks")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!child.waitFor(45,TimeUnit.SECONDS)){child.destroy();throw new AssertionError("Browser UI check timed out: "+log);}
        String output=new String(Files.readAllBytes(log),StandardCharsets.UTF_8);System.out.print(output);
        assertEquals(output,0,child.exitValue());assertTrue(output,output.contains("OK (1 test)"));
    }
}
