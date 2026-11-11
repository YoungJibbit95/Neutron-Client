package me.youngjibbit.core.diagnostics;

import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class CpuPhaseTimingsTest {
    @Test public void disabledDoesNotReadClockAndFullCapturePreservesEvidence() throws Exception {
        AtomicLong clock = new AtomicLong(10);
        CpuPhaseTimings capture = new CpuPhaseTimings(clock::getAndIncrement);
        assertEquals(Long.MIN_VALUE, capture.begin());
        assertEquals(10, clock.get());
        capture.start(1);
        long start = capture.begin();
        clock.set(60); capture.end(CpuPhaseTimings.Phase.WORLD, start, 21);
        capture.end(CpuPhaseTimings.Phase.HUD, capture.begin(), 21);
        capture.stop();
        CpuPhaseTimings.Snapshot snapshot = capture.snapshot();
        assertEquals(1, snapshot.count); assertEquals(1, snapshot.dropped);
        StringWriter writer = new StringWriter(); snapshot.writeCsv(writer);
        assertTrue(writer.toString().contains("0,21,WORLD,50\n"));
    }

    @Test public void wrapAndForeignThreadDoNotCorruptSamples() throws Exception {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 5);
        CpuPhaseTimings capture = new CpuPhaseTimings(clock::get);
        capture.start(2);
        long token = capture.begin(); clock.set(Long.MIN_VALUE + 4);
        capture.end(CpuPhaseTimings.Phase.GUI, token, 1);
        Thread foreign = new Thread(() -> capture.end(CpuPhaseTimings.Phase.WORLD, token, 2));
        foreign.start(); foreign.join();
        StringWriter writer = new StringWriter(); capture.snapshot().writeCsv(writer);
        assertTrue(writer.toString().contains("0,1,GUI,10\n"));
        assertEquals(1, capture.snapshot().count);
    }
}
