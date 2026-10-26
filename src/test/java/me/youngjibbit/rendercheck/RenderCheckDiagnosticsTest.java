package me.youngjibbit.rendercheck;

import org.junit.Test;
import static org.junit.Assert.*;

public class RenderCheckDiagnosticsTest {
    @Test public void allowsNormalCoverageAndPassOutput() {
        RenderCheckDiagnostics.validate("COVERAGE: Pbuffer/FBO GUI composition\nPASS [Pbuffer/FBO only]: masks, blur, glow\n","");
    }
    @Test public void allowsOnlyTheThreeExactMissingSlf4jBindingMessages() {
        RenderCheckDiagnostics.validate("","SLF4J: Failed to load class \"org.slf4j.impl.StaticLoggerBinder\".\n"
                +"SLF4J: Defaulting to no-operation (NOP) logger implementation\n"
                +"SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.\n");
        rejects("","SLF4J: a different failure");
    }
    @Test public void rejectsBothHistoricalOptifineFailuresAndTheirWarning() {
        rejects("","java.lang.IllegalStateException: Display not created");
        rejects("","java.lang.NullPointerException");
        rejects("WARN: [OptiFine] Failed to save options","");
    }
    @Test public void rejectsUnexpectedCaughtErrorsAndWorkerStackTraces() {
        rejects("[ERROR] Shader failed to link","");
        rejects("java.io.IOException: missing shader","");
        rejects("","Exception in thread \"pool-1-thread-1\" java.lang.IllegalStateException");
        rejects("","\tat renderer.Draw.render(Draw.java:12)");
    }
    @Test public void liveGuardForwardsCaughtExceptionsFailsAndRestoresItsStreams() throws Exception {
        java.io.PrintStream originalOut=System.out,originalErr=System.err;
        java.io.ByteArrayOutputStream forwarded=new java.io.ByteArrayOutputStream();
        java.io.PrintStream sink=new java.io.PrintStream(forwarded,true,"UTF-8");
        System.setOut(sink);System.setErr(sink);
        try {
            RenderCheckDiagnostics guard=new RenderCheckDiagnostics();
            new IllegalStateException("synthetic caught renderer failure").printStackTrace();
            try {guard.close();fail("Caught renderer failure was accepted");}
            catch(AssertionError expected) {assertTrue(expected.getMessage().contains("synthetic caught renderer failure"));}
            assertSame(sink,System.out);assertSame(sink,System.err);
            assertTrue(new String(forwarded.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("synthetic caught renderer failure"));
        } finally {System.setOut(originalOut);System.setErr(originalErr);sink.close();}
    }
    private static void rejects(String out,String err) {
        try {RenderCheckDiagnostics.validate(out,err);fail("Diagnostic was accepted");}
        catch(AssertionError expected) {assertTrue(expected.getMessage().startsWith("Unexpected render-check"));}
    }
}
