package me.youngjibbit.rendercheck;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Observe, forward and reject diagnostics that production catch blocks would otherwise turn into a passing check. */
public final class RenderCheckDiagnostics implements AutoCloseable {
    private static final Pattern FAILURE = Pattern.compile("(?i)(\\bWARN(?:ING)?\\b|\\bERROR\\b|\\bFATAL\\b|\\b[\\w.$]*(?:Exception|Error)\\b|\\bFailed to\\b)");
    private final PrintStream previousOut=System.out, previousErr=System.err;
    private final ByteArrayOutputStream out=new ByteArrayOutputStream(), err=new ByteArrayOutputStream();
    private final PrintStream observedOut, observedErr;

    public RenderCheckDiagnostics() throws Exception {
        observedOut=new PrintStream(tee(previousOut,out),true,"UTF-8");
        observedErr=new PrintStream(tee(previousErr,err),true,"UTF-8");
        System.setOut(observedOut);System.setErr(observedErr);
    }
    private static OutputStream tee(PrintStream original,ByteArrayOutputStream captured) {
        return new OutputStream() {
            @Override public synchronized void write(int value) {original.write(value);captured.write(value);}
            @Override public synchronized void write(byte[] data,int offset,int length) {
                original.write(data,offset,length);captured.write(data,offset,length);
            }
            @Override public void flush() {original.flush();}
        };
    }
    static void validate(String stdout,String stderr) {
        for(String line:stderr.split("\\R")) {
            if(line.isEmpty() || knownMissingSlf4jBinding(line))continue;
            throw new AssertionError("Unexpected render-check stderr: "+line);
        }
        for(String line:stdout.split("\\R")) {
            if(FAILURE.matcher(line).find())throw new AssertionError("Unexpected render-check diagnostic: "+line);
        }
    }
    private static boolean knownMissingSlf4jBinding(String line) {
        // This optional logging facade is unrelated to Minecraft/OptiFine's Log4j renderer diagnostics.
        return line.equals("SLF4J: Failed to load class \"org.slf4j.impl.StaticLoggerBinder\".")
                || line.equals("SLF4J: Defaulting to no-operation (NOP) logger implementation")
                || line.equals("SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.");
    }
    public void check() {
        observedOut.flush();observedErr.flush();
        validate(new String(out.toByteArray(),StandardCharsets.UTF_8),new String(err.toByteArray(),StandardCharsets.UTF_8));
    }
    @Override public void close() {
        System.setOut(previousOut);System.setErr(previousErr);
        check();
    }
}
