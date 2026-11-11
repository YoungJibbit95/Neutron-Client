package me.youngjibbit.core.diagnostics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Entry;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Kind;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Mode;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Origin;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkResearchRecordingTest {
    private static Entry sample(long sequence) {
        return entry(7, sequence, -100, Mode.LAGRANGE, Origin.CLIENT_OBSERVATION, Kind.ENTITY, "", "");
    }

    private static Entry entry(long run, long sequence, long nanos, Mode mode, Origin origin,
                               Kind kind, String packetType, String detail) {
        return new Entry(run, sequence, nanos, mode, origin, kind, packetType, -17,
                1.25, -2.5, Math.PI, Double.MIN_VALUE, Double.MAX_VALUE, -0.0,
                Long.MAX_VALUE, Long.MIN_VALUE, detail);
    }

    private static NetworkResearchRecording recording(List<Entry> entries) {
        return new NetworkResearchRecording(7, Mode.LAGRANGE, Origin.CLIENT_OBSERVATION,
                4, entries.size(), 0, 0, 0, "", 0, entries);
    }

    private static String encode(NetworkResearchRecording recording) throws IOException {
        StringWriter writer = new StringWriter();
        recording.write(writer);
        return writer.toString();
    }

    private static JsonObject document() throws IOException {
        return JsonParser.parseString(encode(recording(Collections.singletonList(sample(1))))).getAsJsonObject();
    }

    private static JsonObject first(JsonObject document) {
        return document.getAsJsonArray("entries").get(0).getAsJsonObject();
    }

    private static void rejects(String json, String field) {
        IOException error = assertThrows(IOException.class,
                () -> NetworkResearchRecording.read(new StringReader(json)));
        assertTrue("Expected context '" + field + "' in: " + error.getMessage(), error.getMessage().contains(field));
    }

    private static void rejects(JsonObject json, String field) { rejects(json.toString(), field); }

    @Test public void roundTripPreservesEveryScalarAndRecordingMetadata() throws Exception {
        List<Entry> entries = Arrays.asList(sample(4), entry(7, 5, Long.MIN_VALUE, Mode.LAGRANGE,
                Origin.SERVER_EVIDENCE, Kind.SERVER_FLAG, "check \"name\"\\path", "log\nrecord\t42"));
        NetworkResearchRecording original = new NetworkResearchRecording(7, Mode.LAGRANGE,
                Origin.CLIENT_OBSERVATION, 2, 5, 3, 9, 6, "", 0, entries);
        String json = encode(original);
        NetworkResearchRecording decoded = NetworkResearchRecording.read(new StringReader(json));
        assertEquals(json, encode(decoded));
        assertEquals(1, decoded.schemaVersion);
        assertEquals("neutron-network-research", decoded.format);
        assertEquals(7, decoded.run);
        assertEquals(Mode.LAGRANGE, decoded.mode);
        assertEquals(Origin.CLIENT_OBSERVATION, decoded.origin);
        assertEquals(2, decoded.capacity);
        assertEquals(5, decoded.appended);
        assertEquals(3, decoded.overwritten);
        assertEquals(9, decoded.rejected);
        assertEquals(6, decoded.captureFailures);
        assertEquals("", decoded.scenario);
        assertEquals(0, decoded.simulationRejected);
        Entry value = decoded.entries.get(0);
        assertEquals(4, value.sequence);
        assertEquals(-100, value.nanos);
        assertEquals(Kind.ENTITY, value.kind);
        assertEquals(-17, value.entityId);
        assertEquals(1.25, value.x, 0);
        assertEquals(-2.5, value.y, 0);
        assertEquals(Math.PI, value.z, 0);
        assertEquals(Double.MIN_VALUE, value.renderedX, 0);
        assertEquals(Double.MAX_VALUE, value.renderedY, 0);
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(value.renderedZ));
        assertEquals(Long.MAX_VALUE, value.intervalNanos);
        assertEquals(Long.MIN_VALUE, value.deviationNanos);
        assertEquals("check \"name\"\\path", decoded.entries.get(1).packetType);
        assertEquals("log\nrecord\t42", decoded.entries.get(1).detail);
    }

    @Test public void allSimulationScenariosAndModesRoundTrip() throws Exception {
        for (NetworkResearchSimulation.Scenario scenario : NetworkResearchSimulation.Scenario.values()) {
            for (Mode mode : Mode.values()) {
                Entry entry = entry(1, 1, 0, mode, Origin.SIMULATION, Kind.TICK, "", "baseline=true");
                NetworkResearchRecording original = new NetworkResearchRecording(1, mode, Origin.SIMULATION,
                        1, 1, 0, 2, 0, scenario.name(), 3, Collections.singletonList(entry));
                NetworkResearchRecording decoded = NetworkResearchRecording.read(new StringReader(encode(original)));
                assertEquals(scenario.name(), decoded.scenario);
                assertEquals(3, decoded.simulationRejected);
                assertEquals(mode, decoded.entries.get(0).mode);
                assertEquals(Origin.SIMULATION, decoded.entries.get(0).origin);
            }
        }
    }

    @Test public void emptyRecordingAndArbitraryFieldOrderAreSupported() throws Exception {
        NetworkResearchRecording empty = new NetworkResearchRecording(0, Mode.BACKTRACK,
                Origin.CLIENT_OBSERVATION, 1, 0, 0, 0, 0, "", 0, Collections.emptyList());
        JsonObject reordered = JsonParser.parseString(encode(empty)).getAsJsonObject();
        reordered.add("schemaVersion", reordered.remove("schemaVersion"));
        reordered.add("run", reordered.remove("run"));
        NetworkResearchRecording decoded = NetworkResearchRecording.read(new StringReader(" \n" + reordered + "\t "));
        assertEquals(0, decoded.run);
        assertTrue(decoded.entries.isEmpty());
    }

    @Test public void snapshotsDefensivelyCopyEntriesAndExposeUnmodifiableLists() throws Exception {
        List<Entry> source = new ArrayList<>();
        Entry retained = sample(1);
        source.add(retained);
        NetworkResearchRecording original = recording(source);
        source.clear();
        source.add(sample(2));
        assertSame(retained, original.entries.get(0));
        assertThrows(UnsupportedOperationException.class, () -> original.entries.clear());
        NetworkResearchRecording decoded = NetworkResearchRecording.read(new StringReader(encode(original)));
        assertThrows(UnsupportedOperationException.class, () -> decoded.entries.add(sample(2)));
    }

    @Test public void everyHeaderAndEntryFieldIsRequired() throws Exception {
        JsonObject valid = document();
        for (String field : new ArrayList<>(valid.keySet())) {
            JsonObject incomplete = document();
            incomplete.remove(field);
            rejects(incomplete, field);
        }
        for (String field : new ArrayList<>(first(valid).keySet())) {
            JsonObject incomplete = document();
            first(incomplete).remove(field);
            rejects(incomplete, "entries[0]." + field);
        }
    }

    @Test public void duplicateAndUnknownFieldsAreRejectedInBothObjects() throws Exception {
        String valid = encode(recording(Collections.singletonList(sample(1))));
        rejects(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"), "schemaVersion");
        rejects(valid.replace("\"sequence\":1", "\"sequence\":1,\"sequence\":1"), "entries[0].sequence");
        rejects(valid.replace("\"run\":7", "\"run\":7,\"\\u0072un\":7"), "run");
        JsonObject unknownHeader = document();
        unknownHeader.addProperty("extra", false);
        rejects(unknownHeader, "unknown field: extra");
        JsonObject unknownEntry = document();
        first(unknownEntry).addProperty("extra", false);
        rejects(unknownEntry, "entries[0] has unknown field: extra");
    }

    @Test public void unsupportedVersionsFormatsAndEnumsAreRejected() throws Exception {
        JsonObject version = document(); version.addProperty("schemaVersion", 2); rejects(version, "schemaVersion");
        JsonObject format = document(); format.addProperty("format", "other-format"); rejects(format, "format");
        for (String field : Arrays.asList("mode", "origin")) {
            JsonObject unknown = document(); unknown.addProperty(field, "UNKNOWN"); rejects(unknown, field);
        }
        for (String field : Arrays.asList("mode", "origin", "kind")) {
            JsonObject unknown = document(); first(unknown).addProperty(field, "UNKNOWN");
            rejects(unknown, "entries[0]." + field);
        }
    }

    @Test public void integerFieldsNeverCoerceStringsDecimalsExponentsOrOtherTypes() throws Exception {
        List<String> literals = Arrays.asList("\"1\"", "1.0", "1e0", "true", "null", "[]", "{}",
                "9223372036854775808", "-9223372036854775809");
        for (String field : Arrays.asList("schemaVersion", "run", "capacity", "appended", "overwritten",
                "rejected", "captureFailures", "simulationRejected")) {
            for (String literal : literals) {
                JsonObject invalid = document(); invalid.add(field, JsonParser.parseString(literal));
                rejects(invalid, field);
            }
        }
        for (String field : Arrays.asList("run", "sequence", "nanos", "entityId", "intervalNanos", "deviationNanos")) {
            for (String literal : literals) {
                JsonObject invalid = document(); first(invalid).add(field, JsonParser.parseString(literal));
                rejects(invalid, "entries[0]." + field);
            }
        }
        JsonObject intOverflow = document(); first(intOverflow).addProperty("entityId", 2147483648L);
        rejects(intOverflow, "entityId");
    }

    @Test public void doubleFieldsRejectNonfiniteValuesAndStringCoercion() throws Exception {
        for (String field : Arrays.asList("x", "y", "z", "renderedX", "renderedY", "renderedZ")) {
            for (String literal : Arrays.asList("1e9999", "-1e9999", "\"1.25\"", "\"NaN\"", "null", "true")) {
                JsonObject invalid = document(); first(invalid).add(field, JsonParser.parseString(literal));
                rejects(invalid, "entries[0]." + field);
            }
        }
        String valid = document().toString();
        rejects(valid.replace("\"x\":1.25", "\"x\":NaN"), "x");
        rejects(valid.replace("\"x\":1.25", "\"x\":Infinity"), "x");
    }

    @Test public void textAndContainerTypesAreExactAndTextIsBounded() throws Exception {
        for (String field : Arrays.asList("format", "mode", "origin", "scenario")) {
            JsonObject invalid = document(); invalid.addProperty(field, 1); rejects(invalid, field);
            JsonObject nullValue = document(); nullValue.add(field, null); rejects(nullValue, field);
        }
        String tooLong = repeated('x', 161);
        for (String field : Arrays.asList("packetType", "detail")) {
            JsonObject invalid = document(); first(invalid).addProperty(field, tooLong);
            rejects(invalid, "entries[0]." + field);
            JsonObject nullValue = document(); first(nullValue).add(field, null);
            rejects(nullValue, "entries[0]." + field);
        }
        JsonObject scenario = document(); scenario.addProperty("scenario", tooLong); rejects(scenario, "scenario");
        JsonObject entriesNull = document(); entriesNull.add("entries", null); rejects(entriesNull, "entries");
        JsonObject entryNull = document(); entryNull.getAsJsonArray("entries").set(0, null); rejects(entryNull, "entries[0]");
        JsonObject hugeName = document(); hugeName.addProperty(tooLong, true); rejects(hugeName, "field name");
        rejects("[]", "recording");
    }

    @Test public void countersCapacityAndSequenceMustDescribeOneConsistentRun() throws Exception {
        for (String field : Arrays.asList("run", "appended", "overwritten", "rejected", "captureFailures", "simulationRejected")) {
            JsonObject negative = document(); negative.addProperty(field, -1); rejects(negative, field);
        }
        for (int capacity : new int[]{0, -1, 4097}) {
            JsonObject invalid = document(); invalid.addProperty("capacity", capacity); rejects(invalid, "capacity");
        }
        JsonObject mismatch = document(); mismatch.addProperty("appended", 2); rejects(mismatch, "appended");
        JsonObject overflow = document(); overflow.addProperty("overwritten", Long.MAX_VALUE);
        rejects(overflow, "overflows");
        JsonObject firstSequence = document(); first(firstSequence).addProperty("sequence", 2);
        rejects(firstSequence, "sequence");
        JsonObject differentRun = document(); first(differentRun).addProperty("run", 8);
        rejects(differentRun, "entries[0].run");
        JsonObject differentMode = document(); first(differentMode).addProperty("mode", "TICKBASE");
        rejects(differentMode, "entries[0].mode");
        assertThrows(IllegalArgumentException.class, () -> recording(Arrays.asList(sample(1), sample(3))));
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchRecording(7, Mode.LAGRANGE,
                Origin.CLIENT_OBSERVATION, 1, 2, 0, 0, 0, "", 0, Arrays.asList(sample(1), sample(2))));
    }

    @Test public void maximumSequenceDoesNotOverflowAndClockRegressionsRemainInspectable() throws Exception {
        NetworkResearchRecording last = new NetworkResearchRecording(7, Mode.LAGRANGE,
                Origin.CLIENT_OBSERVATION, 1, Long.MAX_VALUE, Long.MAX_VALUE - 1,
                Long.MAX_VALUE, Long.MAX_VALUE, "", 0, Collections.singletonList(sample(Long.MAX_VALUE)));
        assertEquals(Long.MAX_VALUE,
                NetworkResearchRecording.read(new StringReader(encode(last))).entries.get(0).sequence);
        Entry before = entry(7, 1, 100, Mode.LAGRANGE, Origin.CLIENT_OBSERVATION, Kind.RECEIVED, "sample", "");
        Entry after = entry(7, 2, -100, Mode.LAGRANGE, Origin.CLIENT_OBSERVATION, Kind.APPLIED, "sample", "");
        NetworkResearchRecording regression = NetworkResearchRecording.read(new StringReader(encode(recording(Arrays.asList(before, after)))));
        assertEquals(100, regression.entries.get(0).nanos);
        assertEquals(-100, regression.entries.get(1).nanos);
    }

    @Test public void scenarioAndSimulationCountersBelongToTheRecordingSource() throws Exception {
        JsonObject clientScenario = document(); clientScenario.addProperty("scenario", "BASELINE");
        rejects(clientScenario, "scenario");
        JsonObject clientCounter = document(); clientCounter.addProperty("simulationRejected", 1);
        rejects(clientCounter, "simulationRejected");
        JsonObject serverSource = document(); serverSource.addProperty("origin", "SERVER_EVIDENCE");
        rejects(serverSource, "origin");
        for (String scenario : Arrays.asList("", "baseline", "UNKNOWN")) {
            JsonObject simulation = document(); simulation.addProperty("origin", "SIMULATION");
            simulation.addProperty("scenario", scenario); first(simulation).addProperty("origin", "SIMULATION");
            rejects(simulation, "scenario");
        }
    }

    @Test public void serverEvidenceRequiresAnExplicitMarkerWithReferenceAndCheck() throws Exception {
        Entry flag = entry(7, 1, 0, Mode.LAGRANGE, Origin.SERVER_EVIDENCE, Kind.SERVER_FLAG, "check", "server.log#42");
        NetworkResearchRecording decoded = NetworkResearchRecording.read(new StringReader(encode(recording(Collections.singletonList(flag)))));
        assertEquals(Origin.SERVER_EVIDENCE, decoded.entries.get(0).origin);
        for (Origin wrong : Arrays.asList(Origin.CLIENT_OBSERVATION, Origin.SIMULATION)) {
            Entry fake = entry(7, 1, 0, Mode.LAGRANGE, wrong, Kind.SERVER_FLAG, "check", "server.log#42");
            assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(fake)));
        }
        for (String blank : Arrays.asList("", " \t\r\n")) {
            Entry noCheck = entry(7, 1, 0, Mode.LAGRANGE, Origin.SERVER_EVIDENCE, Kind.SERVER_FLAG, blank, "reference");
            Entry noReference = entry(7, 1, 0, Mode.LAGRANGE, Origin.SERVER_EVIDENCE, Kind.SERVER_FLAG, "check", blank);
            assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(noCheck)));
            assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(noReference)));
        }
        Entry implicit = entry(7, 1, 0, Mode.LAGRANGE, Origin.SERVER_EVIDENCE, Kind.CORRECTION, "check", "reference");
        assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(implicit)));
        Entry foreign = entry(7, 1, 0, Mode.LAGRANGE, Origin.SIMULATION, Kind.ENTITY, "", "");
        assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(foreign)));
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchRecording(7, Mode.LAGRANGE,
                Origin.SIMULATION, 1, 1, 0, 0, 0, "BASELINE", 0, Collections.singletonList(flag)));
    }

    @Test public void directConstructionRejectsNullsAndNonfiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchRecording(7, Mode.LAGRANGE,
                Origin.CLIENT_OBSERVATION, 1, 0, 0, 0, 0, "", 0, null));
        assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchRecording(7, null,
                Origin.CLIENT_OBSERVATION, 1, 0, 0, 0, 0, "", 0, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchRecording(7, Mode.LAGRANGE,
                null, 1, 0, 0, 0, 0, "", 0, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new NetworkResearchRecording(7, Mode.LAGRANGE,
                Origin.CLIENT_OBSERVATION, 1, 0, 0, 0, 0, null, 0, Collections.emptyList()));
        for (int coordinate = 0; coordinate < 6; coordinate++) {
            double[] values = {1, 2, 3, 4, 5, 6};
            values[coordinate] = coordinate % 2 == 0 ? Double.NaN : Double.NEGATIVE_INFINITY;
            Entry invalid = new Entry(7, 1, 0, Mode.LAGRANGE, Origin.CLIENT_OBSERVATION, Kind.ENTITY,
                    "", 1, values[0], values[1], values[2], values[3], values[4], values[5], 0, 0, "");
            assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(invalid)));
        }
        Entry nullKind = entry(7, 1, 0, Mode.LAGRANGE, Origin.CLIENT_OBSERVATION, null, "", "");
        assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(nullKind)));
        Entry nullText = entry(7, 1, 0, Mode.LAGRANGE, Origin.CLIENT_OBSERVATION, Kind.ENTITY, null, "");
        assertThrows(IllegalArgumentException.class, () -> recording(Collections.singletonList(nullText)));
    }

    @Test public void fullRecordingWithMaximallyEscapedTextFitsTheInputBudget() throws Exception {
        List<Entry> entries = new ArrayList<>();
        String text = repeated('\u0001', 160);
        for (int i = 1; i <= 4096; i++) {
            entries.add(entry(7, i, Long.MIN_VALUE, Mode.LAGRANGE,
                    Origin.CLIENT_OBSERVATION, Kind.RECEIVED, text, text));
        }
        NetworkResearchRecording full = new NetworkResearchRecording(7, Mode.LAGRANGE,
                Origin.CLIENT_OBSERVATION, 4096, 4096, 0, 0, 0, "", 0, entries);
        String json = encode(full);
        assertTrue(json.length() <= NetworkResearchRecording.MAX_INPUT_CHARACTERS);
        assertEquals(4096, NetworkResearchRecording.read(new StringReader(json)).entries.size());
    }

    @Test public void moreThan4096EntriesIsRejectedBeforeUnboundedAccumulation() throws Exception {
        JsonObject excessive = document();
        excessive.addProperty("capacity", 4096);
        excessive.addProperty("appended", 4097);
        JsonObject entry = first(excessive);
        JsonArray entries = new JsonArray();
        for (int i = 0; i < 4097; i++) entries.add(entry);
        excessive.add("entries", entries);
        rejects(excessive, "entries exceed maximum count");
    }

    @Test public void totalInputIncludingTrailingWhitespaceIsBounded() throws Exception {
        String prefix = encode(recording(Collections.emptyList()));
        int remaining = NetworkResearchRecording.MAX_INPUT_CHARACTERS - prefix.length();
        PaddingReader exact = new PaddingReader(prefix, remaining);
        assertTrue(NetworkResearchRecording.read(exact).entries.isEmpty());
        assertFalse(exact.closed);
        PaddingReader excessive = new PaddingReader(prefix, (long) remaining + 1);
        IOException error = assertThrows(IOException.class, () -> NetworkResearchRecording.read(excessive));
        assertTrue(error.getMessage().contains("input exceeds maximum"));
        assertFalse(excessive.closed);
    }

    @Test public void malformedJsonAndTrailingDocumentsAreRejectedStrictly() throws Exception {
        String valid = document().toString();
        for (String invalid : Arrays.asList("", valid.substring(0, valid.length() - 1),
                valid + "{}", valid + " false", "//comment\n" + valid, "/*comment*/" + valid,
                valid.replace("\"schemaVersion\"", "schemaVersion"),
                valid.replace("\"format\"", "'format'"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":01"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":+1"),
                valid.replace("\"packetType\":\"\"", "\"packetType\":\"raw\nnewline\""),
                valid.substring(0, valid.length() - 1) + ",}")) {
            rejects(invalid, "recording");
        }
    }

    @Test public void readerAndWriterRemainOwnedByTheCaller() throws Exception {
        final boolean[] closed = {false};
        StringReader source = new StringReader(encode(recording(Collections.emptyList()))) {
            @Override public void close() { closed[0] = true; super.close(); }
        };
        NetworkResearchRecording decoded = NetworkResearchRecording.read(source);
        assertFalse(closed[0]);
        StringWriter destination = new StringWriter() {
            @Override public void close() { fail("caller owns writer"); }
            @Override public void flush() { fail("caller controls flushing"); }
        };
        decoded.write(destination);
        destination.write("tail");
        assertTrue(destination.toString().endsWith("tail"));
    }

    @Test public void writerFailuresPropagateWithoutClosingTheDestination() {
        IOException failure = new IOException("destination unavailable");
        Writer broken = new Writer() {
            @Override public void write(char[] text, int offset, int length) throws IOException { throw failure; }
            @Override public void flush() { fail("caller controls flushing"); }
            @Override public void close() { fail("caller owns writer"); }
        };
        assertSame(failure, assertThrows(IOException.class, () -> recording(Collections.emptyList()).write(broken)));
    }

    private static String repeated(char value, int length) {
        char[] chars = new char[length];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static final class PaddingReader extends Reader {
        private final StringReader prefix;
        private long padding;
        private boolean prefixEnded, closed;

        PaddingReader(String prefix, long padding) { this.prefix = new StringReader(prefix); this.padding = padding; }

        @Override public int read(char[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (!prefixEnded) {
                int count = prefix.read(buffer, offset, length);
                if (count >= 0) return count;
                prefixEnded = true;
            }
            if (padding == 0) return -1;
            int count = (int) Math.min(length, padding);
            Arrays.fill(buffer, offset, offset + count, ' ');
            padding -= count;
            return count;
        }

        @Override public void close() { closed = true; }
    }
}
