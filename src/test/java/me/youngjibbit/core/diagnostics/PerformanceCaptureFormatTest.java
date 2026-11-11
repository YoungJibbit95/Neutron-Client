package me.youngjibbit.core.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PerformanceCaptureFormatTest {
    @Test public void emptyStatisticsProduceJsonNullRatherThanFabricatedNumbers() {
        JsonObject json = PerformanceCaptureSession.frameStatistics(FrameTimeStatistics.fromNanos(new long[0], 50_000_000));
        assertTrue(json.get("averageFps").isJsonNull());
        assertTrue(json.get("p99Milliseconds").isJsonNull());
        assertEquals(0, json.get("frames").getAsInt());
        assertFalse(new Gson().toJson(json).contains("NaN"));
    }
    @Test public void exportLabelCannotEscapeCaptureDirectory() {
        PerformanceCaptureSession.validateLabel("pair-01_baseline");
        for (String invalid : new String[]{"../outside", "/absolute", "a/b", "a\\b", "", ".", "label with spaces"}) {
            try { PerformanceCaptureSession.validateLabel(invalid); fail(invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
