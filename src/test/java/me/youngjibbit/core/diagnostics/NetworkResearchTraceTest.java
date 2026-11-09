package me.youngjibbit.core.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Entry;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Kind;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Mode;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Origin;
import org.junit.Test;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import static org.junit.Assert.*;

public class NetworkResearchTraceTest {
    private NetworkResearchTrace trace(int capacity) {
        NetworkResearchTrace trace = new NetworkResearchTrace(capacity);
        trace.startRun(Mode.BACKTRACK, Origin.CLIENT_OBSERVATION); return trace;
    }

    @Test public void evictionIsExplicitAndHistoricalSnapshotsRemainImmutableAcrossRuns() {
        NetworkResearchTrace trace = trace(2);
        trace.packet(1, Kind.RECEIVED, "first"); trace.packet(2, Kind.APPLIED, "second");
        List<Entry> saved = trace.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> saved.clear());
        trace.packet(3, Kind.LOCAL_ACCEPTED, "third");
        assertEquals(2, trace.snapshot().size()); assertEquals(1, trace.getOverwritten());
        assertEquals("second", trace.snapshot().get(0).packetType);
        assertEquals(3, trace.snapshot().get(1).sequence);
        assertEquals("first", saved.get(0).packetType);
        long run = trace.getRun(); trace.entity(4, 1, Double.NaN, 0, 0, 0, 0, 0);
        trace.startRun(Mode.TICKBASE, Origin.SIMULATION);
        assertEquals(run + 1, trace.getRun()); assertTrue(trace.snapshot().isEmpty());
        assertEquals(0, trace.getOverwritten()); assertEquals(0, trace.getRejected());
        trace.tick(900, 1, false); Entry entry = trace.snapshot().get(0);
        assertEquals(1, entry.sequence); assertEquals(Mode.TICKBASE, entry.mode);
        assertEquals(Origin.SIMULATION, entry.origin); assertEquals(0, entry.intervalNanos);
        assertEquals(2, saved.size()); assertEquals(run, saved.get(0).run);
    }

    @Test public void tickGapsUseMonotonicDifferencesAndResetAfterRegression() {
        NetworkResearchTrace trace = trace(10);
        trace.tick(100_000_000, -1, false); trace.tick(170_000_000, -1, false);
        assertEquals(0, trace.snapshot().get(0).intervalNanos);
        assertTrue(trace.snapshot().get(0).detail.contains("baseline=true"));
        assertEquals(70_000_000, trace.snapshot().get(1).intervalNanos);
        assertEquals(20_000_000, trace.snapshot().get(1).deviationNanos);
        trace.tick(160_000_000, -1, false);
        assertEquals(1, trace.getRejected()); assertEquals(2, trace.snapshot().size());
        trace.tick(165_000_000, -1, false);
        assertEquals(0, trace.snapshot().get(2).intervalNanos);
        assertTrue(trace.snapshot().get(2).detail.contains("baseline=true"));
        trace.tick(215_000_000, 0, true);
        assertEquals(50_000_000, trace.snapshot().get(3).intervalNanos);
        assertEquals(0, trace.snapshot().get(3).deviationNanos);
        assertTrue(trace.snapshot().get(3).detail.contains("withheld=true"));
    }

    @Test public void signedNanoTimeWrapDoesNotBecomeAClockRegression() {
        NetworkResearchTrace trace = trace(4);
        long before = Long.MAX_VALUE - 25_000_000L;
        trace.tick(before, -1, false); trace.tick(before + 50_000_000L, -1, false);
        assertEquals(0, trace.getRejected()); assertEquals(2, trace.snapshot().size());
        assertEquals(50_000_000L, trace.snapshot().get(1).intervalNanos);
        assertEquals(0, trace.snapshot().get(1).deviationNanos);
    }

    @Test public void invalidSamplesAreRejectedWithoutPoisoningFiniteEvidence() {
        NetworkResearchTrace trace = trace(20);
        for (int coordinate = 0; coordinate < 6; coordinate++) {
            double[] values = {1, 2, 3, 4, 5, 6};
            values[coordinate] = coordinate % 2 == 0 ? Double.NaN : Double.POSITIVE_INFINITY;
            trace.entity(0, 7, values[0], values[1], values[2], values[3], values[4], values[5]);
        }
        trace.queue(0, -1, 0, 0); trace.queue(0, 0, -1, 0); trace.queue(0, 0, 0, -1);
        trace.tick(0, -2, false);
        assertEquals(10, trace.getRejected()); assertTrue(trace.snapshot().isEmpty());
        trace.entity(1, 7, 1, 2, 3, 4, 5, 6);
        assertEquals(7, trace.snapshot().get(0).entityId); assertEquals(6, trace.snapshot().get(0).renderedZ, 0);
        assertThrows(IllegalArgumentException.class, () -> trace.packet(0, Kind.SERVER_FLAG, "invented"));
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchTrace(0));
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchTrace(4097));
    }

    @Test public void jsonLinesAreBoundedEscapedAndDoNotCloseTheCallersWriter() throws Exception {
        NetworkResearchTrace trace = trace(4);
        trace.packet(1, Kind.RECEIVED, "quoted \"name\"\\path\nnext\rline\tend");
        char[] chars = new char[1000]; java.util.Arrays.fill(chars, 'x');
        trace.serverFlag(2, new String(chars), "check");
        StringWriter writer = new StringWriter(); trace.writeJsonLines(writer); writer.write("tail");
        String[] lines = writer.toString().split("\n"); assertEquals(3, lines.length);
        JsonObject first = JsonParser.parseString(lines[0]).getAsJsonObject();
        assertEquals("quoted \"name\"\\path next line\tend", first.get("packetType").getAsString());
        assertEquals("CLIENT_OBSERVATION", first.get("origin").getAsString());
        JsonObject second = JsonParser.parseString(lines[1]).getAsJsonObject();
        assertEquals(160, second.get("detail").getAsString().length()); assertEquals("tail", lines[2]);
        IOException failure = new IOException("destination unavailable");
        Writer broken = new Writer() {
            @Override public void write(char[] text, int offset, int length) throws IOException { throw failure; }
            @Override public void flush() { }
            @Override public void close() { fail("caller owns destination"); }
        };
        assertSame(failure, assertThrows(IOException.class, () -> trace.writeJsonLines(broken)));
        assertEquals(2, trace.snapshot().size());
    }

    @Test public void onlyExplicitEvidenceMarkersHaveServerProvenance() {
        NetworkResearchTrace trace = trace(10);
        trace.packet(1, Kind.CORRECTION, "S08PacketPlayerPosLook");
        assertTrue(trace.snapshot().stream().noneMatch(entry -> entry.kind == Kind.SERVER_FLAG));
        trace.serverFlag(2, "fixture/server.log#line=8", "ExampleCheck");
        trace.packet(3, Kind.APPLIED, "next");
        assertEquals(Origin.CLIENT_OBSERVATION, trace.snapshot().get(0).origin);
        assertEquals(Origin.SERVER_EVIDENCE, trace.snapshot().get(1).origin);
        assertEquals(Origin.CLIENT_OBSERVATION, trace.snapshot().get(2).origin);
        assertEquals("fixture/server.log#line=8", trace.snapshot().get(1).detail);
        assertThrows(IllegalArgumentException.class, () -> trace.serverFlag(4, " ", "ExampleCheck"));
        assertThrows(IllegalArgumentException.class, () -> trace.serverFlag(4, "log", null));
        assertThrows(IllegalArgumentException.class, () -> trace.startRun(Mode.BACKTRACK, Origin.SERVER_EVIDENCE));
        assertEquals(3, trace.snapshot().size());
    }
}
