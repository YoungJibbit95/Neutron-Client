package me.youngjibbit.core.diagnostics;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Entry;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Kind;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Mode;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Origin;

/** Immutable scalar observations for offline inspection. Reading never executes an entry. */
public final class NetworkResearchRecording {
    private static final String FORMAT = "neutron-network-research";
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_TEXT_LENGTH = 160;
    // Includes worst-case JSON escaping of every permitted text field in a full recording.
    static final int MAX_INPUT_CHARACTERS = 16 * 1024 * 1024;
    private static final String[] RECORDING_FIELDS = {"schemaVersion", "format", "run", "mode", "origin",
            "capacity", "appended", "overwritten", "rejected", "captureFailures", "scenario",
            "simulationRejected", "entries"};
    private static final String[] ENTRY_FIELDS = {"run", "sequence", "nanos", "mode", "origin", "kind",
            "packetType", "entityId", "x", "y", "z", "renderedX", "renderedY", "renderedZ",
            "intervalNanos", "deviationNanos", "detail"};

    public final int schemaVersion = 1;
    public final String format = FORMAT;
    public final long run;
    public final Mode mode;
    public final Origin origin;
    public final int capacity;
    public final long appended, overwritten, rejected, captureFailures;
    public final String scenario;
    public final long simulationRejected;
    public final List<Entry> entries;

    NetworkResearchRecording(long run, Mode mode, Origin origin, int capacity, long appended,
                             long overwritten, long rejected, long captureFailures, String scenario,
                             long simulationRejected, List<Entry> entries) {
        nonnegative(run, "run");
        required(mode, "mode");
        required(origin, "origin");
        require(origin != Origin.SERVER_EVIDENCE, "origin must describe the recording source");
        require(capacity >= 1 && capacity <= MAX_ENTRIES, "capacity must be 1..4096");
        nonnegative(appended, "appended");
        nonnegative(overwritten, "overwritten");
        nonnegative(rejected, "rejected");
        nonnegative(captureFailures, "captureFailures");
        nonnegative(simulationRejected, "simulationRejected");
        text(scenario, "scenario");
        if (origin == Origin.CLIENT_OBSERVATION) {
            require(scenario.isEmpty(), "scenario must be empty for CLIENT_OBSERVATION");
            require(simulationRejected == 0, "simulationRejected must be zero for CLIENT_OBSERVATION");
        } else {
            try { NetworkResearchSimulation.Scenario.valueOf(scenario); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("scenario is unknown: " + scenario, e); }
        }
        required(entries, "entries");
        require(entries.size() <= capacity, "entries exceed capacity");
        List<Entry> copy = new ArrayList<>(entries);
        require(copy.size() <= capacity, "entries exceed capacity");
        require(overwritten <= Long.MAX_VALUE - copy.size(), "overwritten + entries.size overflows appended");
        require(appended == overwritten + copy.size(), "appended must equal overwritten + entries.size");
        long expectedSequence = overwritten;
        for (int i = 0; i < copy.size(); i++) {
            Entry entry = copy.get(i);
            String field = "entries[" + i + "]";
            required(entry, field);
            require(entry.run == run, field + ".run does not match recording run");
            require(entry.sequence == ++expectedSequence, field + ".sequence is not contiguous after overwritten");
            required(entry.mode, field + ".mode");
            require(entry.mode == mode, field + ".mode does not match recording mode");
            required(entry.origin, field + ".origin");
            required(entry.kind, field + ".kind");
            text(entry.packetType, field + ".packetType");
            text(entry.detail, field + ".detail");
            if (entry.kind == Kind.SERVER_FLAG) {
                require(origin == Origin.CLIENT_OBSERVATION, field + ".kind cannot claim server evidence in SIMULATION");
                require(entry.origin == Origin.SERVER_EVIDENCE, field + ".origin must be SERVER_EVIDENCE for SERVER_FLAG");
                require(!entry.packetType.trim().isEmpty(), field + ".packetType needs a server check label");
                require(!entry.detail.trim().isEmpty(), field + ".detail needs a server evidence reference");
            } else {
                require(entry.origin == origin, field + ".origin does not match recording source");
            }
            finite(entry.x, field + ".x");
            finite(entry.y, field + ".y");
            finite(entry.z, field + ".z");
            finite(entry.renderedX, field + ".renderedX");
            finite(entry.renderedY, field + ".renderedY");
            finite(entry.renderedZ, field + ".renderedZ");
        }
        this.run = run;
        this.mode = mode;
        this.origin = origin;
        this.capacity = capacity;
        this.appended = appended;
        this.overwritten = overwritten;
        this.rejected = rejected;
        this.captureFailures = captureFailures;
        this.scenario = scenario;
        this.simulationRejected = simulationRejected;
        this.entries = Collections.unmodifiableList(copy);
    }

    /** Writes one complete recording without closing or flushing the caller's destination. */
    public void write(Writer destination) throws IOException {
        JsonWriter json = new JsonWriter(destination);
        json.setStrictness(Strictness.STRICT);
        json.beginObject();
        json.name("schemaVersion").value(schemaVersion);
        json.name("format").value(format);
        json.name("run").value(run);
        json.name("mode").value(mode.name());
        json.name("origin").value(origin.name());
        json.name("capacity").value(capacity);
        json.name("appended").value(appended);
        json.name("overwritten").value(overwritten);
        json.name("rejected").value(rejected);
        json.name("captureFailures").value(captureFailures);
        json.name("scenario").value(scenario);
        json.name("simulationRejected").value(simulationRejected);
        json.name("entries").beginArray();
        for (Entry entry : entries) {
            json.beginObject();
            json.name("run").value(entry.run);
            json.name("sequence").value(entry.sequence);
            json.name("nanos").value(entry.nanos);
            json.name("mode").value(entry.mode.name());
            json.name("origin").value(entry.origin.name());
            json.name("kind").value(entry.kind.name());
            json.name("packetType").value(entry.packetType);
            json.name("entityId").value(entry.entityId);
            json.name("x").value(entry.x);
            json.name("y").value(entry.y);
            json.name("z").value(entry.z);
            json.name("renderedX").value(entry.renderedX);
            json.name("renderedY").value(entry.renderedY);
            json.name("renderedZ").value(entry.renderedZ);
            json.name("intervalNanos").value(entry.intervalNanos);
            json.name("deviationNanos").value(entry.deviationNanos);
            json.name("detail").value(entry.detail);
            json.endObject();
        }
        json.endArray();
        json.endObject();
    }

    /** Reads only this versioned schema, with bounded input and no reflective deserialization. */
    public static NetworkResearchRecording read(Reader source) throws IOException {
        JsonReader json = new JsonReader(new BoundedReader(source));
        json.setStrictness(Strictness.STRICT);
        try {
            NetworkResearchRecording recording = readRecording(json);
            expect(json, JsonToken.END_DOCUMENT, "recording end (trailing content is not allowed)");
            return recording;
        } catch (IOException e) {
            throw new IOException("recording at " + json.getPath() + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException("recording at " + json.getPath() + ": " + e.getMessage(), e);
        }
    }

    private static NetworkResearchRecording readRecording(JsonReader json) throws IOException {
        expect(json, JsonToken.BEGIN_OBJECT, "recording");
        json.beginObject();
        Set<String> seen = new HashSet<>();
        long run = 0, appended = 0, overwritten = 0, rejected = 0, captureFailures = 0, simulationRejected = 0;
        int capacity = 0;
        Mode mode = null;
        Origin origin = null;
        String scenario = null;
        List<Entry> entries = null;
        while (json.hasNext()) {
            String field = nextField(json, seen, "recording");
            switch (field) {
                case "schemaVersion":
                    if (readInt(json, field) != 1) throw new IOException("schemaVersion must be 1");
                    break;
                case "format":
                    if (!FORMAT.equals(readText(json, field))) throw new IOException("format must be " + FORMAT);
                    break;
                case "run": run = readLong(json, field); break;
                case "mode": mode = readEnum(json, Mode.class, field); break;
                case "origin": origin = readEnum(json, Origin.class, field); break;
                case "capacity": capacity = readInt(json, field); break;
                case "appended": appended = readLong(json, field); break;
                case "overwritten": overwritten = readLong(json, field); break;
                case "rejected": rejected = readLong(json, field); break;
                case "captureFailures": captureFailures = readLong(json, field); break;
                case "scenario": scenario = readText(json, field); break;
                case "simulationRejected": simulationRejected = readLong(json, field); break;
                case "entries": entries = readEntries(json); break;
                default: throw new IOException("recording has unknown field: " + field);
            }
        }
        json.endObject();
        requireFields(seen, RECORDING_FIELDS, "recording");
        return new NetworkResearchRecording(run, mode, origin, capacity, appended, overwritten,
                rejected, captureFailures, scenario, simulationRejected, entries);
    }

    private static List<Entry> readEntries(JsonReader json) throws IOException {
        expect(json, JsonToken.BEGIN_ARRAY, "entries");
        json.beginArray();
        List<Entry> entries = new ArrayList<>();
        while (json.hasNext()) {
            if (entries.size() == MAX_ENTRIES) throw new IOException("entries exceed maximum count of " + MAX_ENTRIES);
            entries.add(readEntry(json, "entries[" + entries.size() + "]"));
        }
        json.endArray();
        return entries;
    }

    private static Entry readEntry(JsonReader json, String path) throws IOException {
        expect(json, JsonToken.BEGIN_OBJECT, path);
        json.beginObject();
        Set<String> seen = new HashSet<>();
        long run = 0, sequence = 0, nanos = 0, interval = 0, deviation = 0;
        Mode mode = null;
        Origin origin = null;
        Kind kind = null;
        String packetType = null, detail = null;
        int entityId = 0;
        double x = 0, y = 0, z = 0, renderedX = 0, renderedY = 0, renderedZ = 0;
        while (json.hasNext()) {
            String name = nextField(json, seen, path);
            String field = path + "." + name;
            switch (name) {
                case "run": run = readLong(json, field); break;
                case "sequence": sequence = readLong(json, field); break;
                case "nanos": nanos = readLong(json, field); break;
                case "mode": mode = readEnum(json, Mode.class, field); break;
                case "origin": origin = readEnum(json, Origin.class, field); break;
                case "kind": kind = readEnum(json, Kind.class, field); break;
                case "packetType": packetType = readText(json, field); break;
                case "entityId": entityId = readInt(json, field); break;
                case "x": x = readDouble(json, field); break;
                case "y": y = readDouble(json, field); break;
                case "z": z = readDouble(json, field); break;
                case "renderedX": renderedX = readDouble(json, field); break;
                case "renderedY": renderedY = readDouble(json, field); break;
                case "renderedZ": renderedZ = readDouble(json, field); break;
                case "intervalNanos": interval = readLong(json, field); break;
                case "deviationNanos": deviation = readLong(json, field); break;
                case "detail": detail = readText(json, field); break;
                default: throw new IOException(path + " has unknown field: " + name);
            }
        }
        json.endObject();
        requireFields(seen, ENTRY_FIELDS, path);
        return new Entry(run, sequence, nanos, mode, origin, kind, packetType, entityId,
                x, y, z, renderedX, renderedY, renderedZ, interval, deviation, detail);
    }

    private static String nextField(JsonReader json, Set<String> seen, String path) throws IOException {
        String name = json.nextName();
        if (name.length() > MAX_TEXT_LENGTH) throw new IOException(path + " field name exceeds 160 characters");
        if (!seen.add(name)) throw new IOException(path + "." + name + " is duplicated");
        return name;
    }

    private static void requireFields(Set<String> seen, String[] fields, String path) throws IOException {
        for (String field : fields) {
            if (!seen.contains(field)) throw new IOException(path + "." + field + " is missing");
        }
    }

    private static void expect(JsonReader json, JsonToken token, String field) throws IOException {
        if (json.peek() != token) throw new IOException(field + " must be " + token);
    }

    private static long readLong(JsonReader json, String field) throws IOException {
        expect(json, JsonToken.NUMBER, field);
        String value = json.nextString();
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { throw new IOException(field + " must be an exact 64-bit integer", e); }
    }

    private static int readInt(JsonReader json, String field) throws IOException {
        long value = readLong(json, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IOException(field + " must be an exact 32-bit integer");
        return (int) value;
    }

    private static double readDouble(JsonReader json, String field) throws IOException {
        expect(json, JsonToken.NUMBER, field);
        String number = json.nextString();
        if (number.length() > 128) throw new IOException(field + " numeric representation exceeds 128 characters");
        double value;
        try { value = Double.parseDouble(number); }
        catch (NumberFormatException e) { throw new IOException(field + " must be a finite number", e); }
        if (!Double.isFinite(value)) throw new IOException(field + " must be a finite number");
        return value;
    }

    private static String readText(JsonReader json, String field) throws IOException {
        expect(json, JsonToken.STRING, field);
        String value = json.nextString();
        if (value.length() > MAX_TEXT_LENGTH) throw new IOException(field + " exceeds 160 characters");
        return value;
    }

    private static <E extends Enum<E>> E readEnum(JsonReader json, Class<E> type, String field) throws IOException {
        String value = readText(json, field);
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException e) { throw new IOException(field + " has unknown value: " + value, e); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static void required(Object value, String field) { require(value != null, field + " must not be null"); }
    private static void nonnegative(long value, String field) { require(value >= 0, field + " must not be negative"); }
    private static void finite(double value, String field) { require(Double.isFinite(value), field + " must be finite"); }
    private static void text(String value, String field) {
        required(value, field);
        require(value.length() <= MAX_TEXT_LENGTH, field + " exceeds 160 characters");
    }

    private static final class BoundedReader extends Reader {
        private final Reader source;
        private int charactersRead;

        BoundedReader(Reader source) {
            if (source == null) throw new NullPointerException("source");
            this.source = source;
        }

        @Override public int read(char[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int count = source.read(buffer, offset, Math.min(length, MAX_INPUT_CHARACTERS - charactersRead + 1));
            if (count > 0) charactersRead += count;
            if (charactersRead > MAX_INPUT_CHARACTERS)
                throw new IOException("input exceeds maximum of " + MAX_INPUT_CHARACTERS + " characters");
            return count;
        }

        @Override public void close() { /* The caller retains ownership of source. */ }
    }
}
