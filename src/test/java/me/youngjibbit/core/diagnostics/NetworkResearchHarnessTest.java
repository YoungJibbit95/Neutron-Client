package me.youngjibbit.core.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Kind;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Mode;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Origin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class NetworkResearchHarnessTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final class Result {
        final int status;
        final String output, error;

        Result(int status, String output, String error) {
            this.status = status; this.output = output; this.error = error;
        }
    }

    private static Result run(String input, String... args) throws IOException {
        StringWriter output = new StringWriter(), error = new StringWriter();
        int status = NetworkResearchHarness.run(args, new StringReader(input), output, error);
        return new Result(status, output.toString(), error.toString());
    }

    private static String baseline() throws IOException {
        Result result = run("", "simulate", "baseline", "backtrack");
        assertEquals(0, result.status);
        assertEquals("", result.error);
        return result.output;
    }

    @Test public void simulationOutputIsDeterministicAcrossScenariosAndProfiles() throws Exception {
        for (String scenario : new String[]{"baseline", "jitter", "burst", "pause"}) {
            for (String profile : new String[]{"backtrack", "lagrange", "tickbase"}) {
                Result first = run("unused input", "simulate", scenario, profile);
                Result second = run("different unused input", "simulate", scenario, profile);
                assertEquals(0, first.status); assertEquals(0, second.status);
                assertEquals("", first.error); assertEquals("", second.error);
                assertArrayEquals(first.output.getBytes(StandardCharsets.UTF_8), second.output.getBytes(StandardCharsets.UTF_8));
                NetworkResearchRecording recording = NetworkResearchRecording.read(new StringReader(first.output));
                assertEquals(61, recording.entries.size());
                assertEquals(Origin.SIMULATION, recording.origin);
                assertEquals(scenario.toUpperCase(java.util.Locale.ROOT), recording.scenario);
                assertEquals(profile.toUpperCase(java.util.Locale.ROOT), recording.mode.name());
                assertTrue(first.output.endsWith("\n"));
            }
        }
    }

    @Test public void stdinAndLocalFileRoundTripsProduceTheSameAnalysis() throws Exception {
        String recording = baseline();
        Result stdin = run(recording, "analyze", "-");
        File file = temporary.newFile("recording.json");
        Files.write(file.toPath(), recording.getBytes(StandardCharsets.UTF_8));
        Result local = run("unused input", "analyze", file.getAbsolutePath());
        StringWriter expected = new StringWriter();
        NetworkResearchAnalysis.analyze(NetworkResearchRecording.read(new StringReader(recording))).write(expected);
        assertEquals(0, stdin.status); assertEquals(0, local.status);
        assertEquals("", stdin.error); assertEquals("", local.error);
        assertEquals(expected.toString(), stdin.output);
        assertEquals(stdin.output, local.output);
        Files.delete(file.toPath());
    }

    @Test public void borrowedStreamsStayOpenAndUnflushedForSuccessAndInvalidInput() throws Exception {
        for (String input : new String[]{baseline(), "{}"}) {
            OwnedReader source = new OwnedReader(input);
            OwnedWriter output = new OwnedWriter(), error = new OwnedWriter();
            int status = NetworkResearchHarness.run(new String[]{"analyze", "-"}, source, output, error);
            assertEquals(input.equals("{}") ? 2 : 0, status);
            assertFalse(source.closed);
            assertFalse(output.closed); assertFalse(error.closed);
            assertFalse(output.flushed); assertFalse(error.flushed);
            output.write("caller can continue");
            error.write("caller can continue");
            assertTrue(output.toString().endsWith("caller can continue"));
        }
    }

    @Test public void verifyDistinguishesTruncationFromSuccessfulAnalysis() throws Exception {
        Result simulated = run("", "simulate", "baseline", "backtrack", "4");
        assertEquals(0, simulated.status);
        NetworkResearchRecording recording = NetworkResearchRecording.read(new StringReader(simulated.output));
        assertEquals(61, recording.appended);
        assertEquals(57, recording.overwritten);
        assertEquals(4, recording.entries.size());
        Result verification = run(simulated.output, "verify", "-");
        Result analysis = run(simulated.output, "analyze", "-");
        assertEquals(3, verification.status);
        assertEquals(0, analysis.status);
        assertEquals("", verification.error); assertEquals("", analysis.error);
        assertEquals(analysis.output, verification.output);
        JsonObject report = JsonParser.parseString(verification.output).getAsJsonObject();
        assertEquals(57, report.get("overwritten").getAsLong());
        assertTrue(report.getAsJsonArray("issues").toString().contains("OVERWRITTEN_PREFIX"));
        Result complete = run(baseline(), "verify", "-");
        assertEquals(0, complete.status);
        assertEquals("", complete.error);
        assertEquals(0, JsonParser.parseString(complete.output).getAsJsonObject().getAsJsonArray("issues").size());
    }

    @Test public void invalidArgumentsMalformedInputAndReadFailuresReturnTwoWithoutOutput() throws Exception {
        String[][] invalidArguments = {
                {}, {"unknown"}, {"simulate"}, {"simulate", "unknown", "backtrack"},
                {"simulate", "baseline", "unknown"}, {"simulate", "baseline", "backtrack", "0"},
                {"simulate", "baseline", "backtrack", "4097"}, {"simulate", "baseline", "backtrack", "1.0"},
                {"simulate", "baseline", "backtrack", "4", "extra"}, {"analyze"}, {"verify", "-", "extra"},
                {"analyze", new File(temporary.getRoot(), "missing-recording.json").getAbsolutePath()}
        };
        for (String[] args : invalidArguments) {
            Result result = run("{}", args);
            assertEquals(java.util.Arrays.toString(args), 2, result.status);
            assertEquals("", result.output);
            assertTrue(result.error.startsWith("Research harness: "));
        }
        for (String invalid : new String[]{"", "{}", "[]", "{", "null",
                baseline().replace("\"schemaVersion\":1", "\"schemaVersion\":2")}) {
            Result result = run(invalid, "analyze", "-");
            assertEquals(2, result.status);
            assertEquals("", result.output);
            assertFalse(result.error.isEmpty());
        }
        Reader broken = new Reader() {
            @Override public int read(char[] buffer, int offset, int length) throws IOException { throw new IOException("source unavailable"); }
            @Override public void close() { fail("caller owns input"); }
        };
        StringWriter output = new StringWriter(), error = new StringWriter();
        assertEquals(2, NetworkResearchHarness.run(new String[]{"verify", "-"}, broken, output, error));
        assertEquals("", output.toString());
        assertTrue(error.toString().contains("source unavailable"));
    }

    @Test public void legacyJsonLinesCannotStandInForVersionedRecordingMetadata() throws Exception {
        NetworkResearchTrace trace = new NetworkResearchTrace(4);
        trace.startRun(Mode.BACKTRACK, Origin.CLIENT_OBSERVATION);
        trace.packet(0, Kind.RECEIVED, "sample"); trace.packet(1, Kind.APPLIED, "sample");
        StringWriter legacy = new StringWriter(); trace.writeJsonLines(legacy);
        Result raw = run(legacy.toString(), "analyze", "-");
        assertEquals(2, raw.status);
        assertEquals("", raw.output);
        assertTrue(raw.error.contains("recording"));
        String firstLine = legacy.toString().split("\n")[0];
        Result missingMetadata = run("{\"entries\":[" + firstLine + "]}", "verify", "-");
        assertEquals(2, missingMetadata.status);
        assertEquals("", missingMetadata.output);
        assertTrue(missingMetadata.error.contains("schemaVersion"));
        assertTrue(missingMetadata.error.contains("missing"));
    }

    @Test public void mainAcceptsValidUtf8FromProcessStdin() throws Exception {
        byte[] input = processRecording().replace("UTF8_MARKER", "Gr\u00fc\u00dfe \ud83d\udcbb")
                .getBytes(StandardCharsets.UTF_8);
        for (String command : new String[]{"analyze", "verify"}) {
            Result result = runProcess(input, false, command, "-");
            assertEquals(0, result.status);
            assertEquals("", result.error);
            JsonObject report = JsonParser.parseString(result.output).getAsJsonObject();
            assertEquals(3, report.get("retainedSamples").getAsInt());
            assertEquals(1, report.getAsJsonObject("tickIntervals").get("samples").getAsInt());
            assertEquals(0, report.getAsJsonArray("issues").size());
        }
    }

    @Test public void mainRejectsMalformedUtf8OnStdinWithoutAReport() throws Exception {
        String recording = processRecording();
        byte[] input = recording.getBytes(StandardCharsets.UTF_8);
        int marker = recording.indexOf("UTF8_MARKER");
        assertTrue(marker >= 0);
        input[recording.substring(0, marker).getBytes(StandardCharsets.UTF_8).length] = (byte) 0xff;
        for (String command : new String[]{"analyze", "verify"}) {
            Result result = runProcess(input, false, command, "-");
            assertEquals(2, result.status);
            assertEquals("", result.output);
            assertTrue(result.error, result.error.startsWith("Research harness: recording"));
        }
    }

    @Test public void mainReportsAClosedStdoutPipeAsAnIoFailure() throws Exception {
        byte[] input = processRecording().getBytes(StandardCharsets.UTF_8);
        for (String command : new String[]{"analyze", "verify"}) {
            Result result = runProcess(input, true, command, "-");
            assertEquals(2, result.status);
            assertEquals("", result.output);
            assertTrue(result.error, result.error.contains("failed to write standard output"));
        }
    }

    private static String processRecording() throws IOException {
        NetworkResearchTrace trace = new NetworkResearchTrace(4);
        trace.startRun(Mode.BACKTRACK, Origin.CLIENT_OBSERVATION);
        trace.packet(0, Kind.RECEIVED, "UTF8_MARKER");
        trace.tick(0, 1, false);
        trace.tick(50_000_000L, 1, false);
        StringWriter writer = new StringWriter();
        trace.recording(0, "", 0).write(writer);
        return writer.toString();
    }

    private static Result runProcess(byte[] input, boolean closeStdout, String... args) throws Exception {
        // The fixture and resulting report fit below even a small pipe buffer; waitFor is always bounded.
        assertTrue("Process input fixture must stay small", input.length < 2048);
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? "java.exe" : "java";
        File java = new File(new File(System.getProperty("java.home"), "bin"), executable);
        List<String> command = new ArrayList<>(Arrays.asList(java.getAbsolutePath(), "-cp",
                System.getProperty("java.class.path"), NetworkResearchHarness.class.getName()));
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).start();
        try (InputStream output = process.getInputStream(); InputStream error = process.getErrorStream()) {
            // The child cannot produce its report until stdin is complete. Close stdout first to avoid a race.
            if (closeStdout) output.close();
            try (OutputStream stdin = process.getOutputStream()) { stdin.write(input); }
            assertTrue("Harness process timed out", process.waitFor(10, TimeUnit.SECONDS));
            return new Result(process.exitValue(), closeStdout ? "" : readUtf8(output), readUtf8(error));
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    assertTrue("Harness process did not terminate", process.waitFor(5, TimeUnit.SECONDS));
                }
            }
        }
    }

    private static String readUtf8(InputStream stream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int count; (count = stream.read(buffer)) != -1; ) bytes.write(buffer, 0, count);
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class OwnedReader extends StringReader {
        boolean closed;
        OwnedReader(String text) { super(text); }
        @Override public void close() { closed = true; super.close(); }
    }

    private static final class OwnedWriter extends StringWriter {
        boolean closed, flushed;
        @Override public void close() { closed = true; }
        @Override public void flush() { flushed = true; }
    }
}
