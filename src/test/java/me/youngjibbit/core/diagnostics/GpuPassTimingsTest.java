package me.youngjibbit.core.diagnostics;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/** Driver-free scheduling/lifecycle evidence; the native check measures actual GPU timestamps. */
public class GpuPassTimingsTest {
    @Test public void disabledHooksAndSnapshotsNeverTouchTheDriver() throws Exception {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        assertEquals(0, capture.begin(null));
        capture.end(0); capture.end(123); capture.poll(); capture.stop(); capture.abort("world"); capture.reset();
        StringWriter csv = new StringWriter(); capture.snapshot().writeCsv(csv);
        assertEquals(0, driver.calls);
        assertEquals(GpuPassTimings.State.IDLE, capture.snapshot().state);
        assertEquals(1, csv.toString().split("\n").length);
    }

    @Test public void unavailableContextsAndCounterBitsProduceExplicitEmptyEvidence() {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        Object original = driver.context;
        driver.context = null; capture.start(5);
        assertEquals(GpuPassTimings.State.NO_CONTEXT, capture.snapshot().state);
        assertFalse(capture.snapshot().supported);
        driver.context = original; driver.supported = false; capture.start(5);
        assertEquals(GpuPassTimings.State.UNSUPPORTED, capture.snapshot().state);
        driver.supported = true; driver.bits = 0; capture.start(5);
        assertEquals(GpuPassTimings.State.UNSUPPORTED, capture.snapshot().state);
        assertEquals(0, driver.created);
        int calls = driver.calls;
        capture.begin("WORLD"); capture.poll(); capture.end(99); capture.stop();
        assertEquals(calls, driver.calls);
        assertTrue(capture.snapshot().samples.isEmpty());
    }

    @Test public void neitherQueryResultIsReadUntilBothAreReady() {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        capture.start(4);
        long token = capture.begin("WORLD"); capture.end(token); capture.poll();
        assertEquals(0, driver.reads);
        driver.ready.add(2); // even inconsistent readiness cannot cause an unready start read
        capture.poll();
        assertEquals(0, driver.reads);
        assertEquals(1, capture.snapshot().pending);
        driver.ready.add(1); capture.poll();
        assertEquals(2, driver.reads);
        GpuPassTimings.Sample row = capture.snapshot().samples.get(0);
        assertEquals(10, row.elapsedNanos);
        assertEquals(0, row.issuedPoll);
        assertEquals(3, row.resolvedPoll);
        capture.stop();
        assertEquals(GpuPassTimings.State.STOPPED, capture.snapshot().state);
        assertEquals(driver.created, driver.deleted);
    }

    @Test public void nestedPassesAndStopInsideAnOpenPassDrainWithoutInventingAnEnd() {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        capture.start(8);
        long outer = capture.begin("WORLD"), inner = capture.begin("HUD");
        capture.end(inner); capture.end(inner); capture.stop();
        assertEquals(0, capture.begin("GUI"));
        driver.readyAll(); capture.poll();
        assertEquals(1, capture.snapshot().pending);
        assertEquals(1, capture.snapshot().samples.size());
        assertEquals(GpuPassTimings.State.DRAINING, capture.snapshot().state);
        capture.end(outer); driver.readyAll(); capture.poll();
        GpuPassTimings.Snapshot snapshot = capture.snapshot();
        assertEquals(GpuPassTimings.State.STOPPED, snapshot.state);
        assertEquals(30, snapshot.samples.get(0).elapsedNanos);
        assertEquals(10, snapshot.samples.get(1).elapsedNanos);
        assertEquals("WORLD", snapshot.samples.get(0).phase);
        assertEquals(4, driver.stamps);
        assertEquals(0, snapshot.queryObjectsRemaining);
    }

    @Test public void ringSaturationDoesNotOverwritePairsAndOldTokensCannotEndReusedSlots() {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        capture.start(8);
        long first = capture.begin("first"), second = capture.begin("second");
        int calls = driver.calls;
        assertEquals(0, capture.begin("dropped")); assertEquals(calls, driver.calls);
        assertEquals(1, capture.snapshot().dropped);
        capture.end(first); driver.readyAll(); capture.poll();
        long third = capture.begin("third"); assertNotEquals(first, third);
        calls = driver.calls; capture.end(first); assertEquals(calls, driver.calls);
        capture.end(third); capture.end(second); driver.readyAll(); capture.poll();
        assertEquals(3, capture.snapshot().samples.size()); assertEquals(4, driver.created);
        capture.abort("world switch"); assertEquals(driver.created, driver.deleted);
    }

    @Test public void rawCapacityIsReservedBeforeIssueAndRemainsBoundedWhileDraining() {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        capture.start(3);
        long first = capture.begin("a"), second = capture.begin("b");
        capture.end(second); driver.readyAll(); capture.poll();
        long third = capture.begin("c");
        assertEquals(GpuPassTimings.State.CAPACITY_REACHED, capture.snapshot().state);
        int calls = driver.calls;
        for (int i = 0; i < 20; i++) assertEquals(0, capture.begin("over capacity"));
        assertEquals(calls, driver.calls);
        capture.end(first); capture.end(third); driver.readyAll(); capture.poll();
        assertEquals(3, capture.snapshot().samples.size()); assertEquals(0, capture.snapshot().pending);
        assertEquals(0, capture.snapshot().queryObjectsRemaining); assertEquals(3, capture.snapshot().issued);
        assertEquals(4, driver.created);
    }

    @Test public void abortPreservesResolvedEvidenceAndDiscardsPendingWithoutReadingIt() {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        capture.start(6);
        long first = capture.begin("a"); capture.end(first); driver.readyAll(); capture.poll();
        GpuPassTimings.Snapshot old = capture.snapshot();
        long pending = capture.begin("pending"); capture.end(pending);
        capture.abort("resource reload"); capture.abort("later event");
        assertEquals(GpuPassTimings.State.ABORTED, capture.snapshot().state);
        assertEquals("resource reload", capture.snapshot().reason);
        assertEquals(1, capture.snapshot().abandoned); assertEquals(2, driver.reads); assertEquals(4, driver.deleted);
        assertEquals(1, old.samples.size());
        try { old.samples.clear(); fail("Snapshot must own an immutable list"); }
        catch (UnsupportedOperationException expected) { }
        capture.start(2);
        int calls = driver.calls; capture.end(pending); assertEquals(calls, driver.calls);
        assertTrue(capture.snapshot().samples.isEmpty());
        capture.reset(); assertEquals(driver.created, driver.deleted);
        assertEquals(GpuPassTimings.State.IDLE, capture.snapshot().state);
    }

    @Test public void contextChangeNeverDeletesOrReadsForeignIdsAndDefersNewAllocation() {
        Driver driver = new Driver(); Object original = driver.context;
        GpuPassTimings capture = new GpuPassTimings(driver, 2);
        capture.start(8); long token = capture.begin("WORLD"); capture.end(token);
        driver.context = new Object();
        capture.poll(); capture.abort("world"); capture.reset(); capture.start(10);
        assertEquals(GpuPassTimings.State.CONTEXT_LOST, capture.snapshot().state);
        assertEquals(1, capture.snapshot().abandoned); assertEquals(4, capture.snapshot().queryObjectsRemaining);
        assertEquals(4, driver.created); assertEquals(0, driver.deleted); assertEquals(0, driver.reads);
        driver.context = original; capture.poll();
        assertEquals(4, driver.deleted); assertEquals(0, capture.snapshot().queryObjectsRemaining);
        capture.start(2); assertTrue(capture.isRecording());
        capture.reset(); assertEquals(driver.created, driver.deleted);
    }

    @Test public void partialAllocationFailureDeletesExactlyTheNamesActuallyAllocated() {
        Driver driver = new Driver(); driver.failAllocationAt = 3;
        GpuPassTimings capture = new GpuPassTimings(driver, 2); capture.start(8);
        assertEquals(GpuPassTimings.State.ERROR, capture.snapshot().state);
        assertEquals(2, driver.created); assertEquals(2, driver.deleted);
        assertEquals(0, capture.snapshot().queryObjectsRemaining); assertEquals(0, capture.begin("WORLD"));
    }

    @Test public void ownerThreadGuardsRejectGlWorkAndInvalidStartLeavesCaptureIntact() throws Exception {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2); capture.start(8);
        try { capture.start(0); fail("Capacity must be validated"); }
        catch (IllegalArgumentException expected) { }
        assertTrue(capture.isRecording()); int calls = driver.calls;
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            try { capture.begin("foreign"); } catch (Throwable failure) { error.set(failure); }
        });
        foreign.start(); foreign.join();
        assertTrue(error.get() instanceof IllegalStateException); assertEquals(calls, driver.calls);
        capture.abort("done");
    }

    @Test public void invalidTimestampRangesAreExportedWithoutAnInventedDuration() throws Exception {
        Driver driver = new Driver(); driver.bits = 32;
        GpuPassTimings capture = new GpuPassTimings(driver, 2); capture.start(2);
        long token = capture.begin("wrap"); capture.end(token);
        driver.values.put(1, 0xfffffff0L); driver.values.put(2, 4L); driver.readyAll(); capture.poll();
        assertEquals(1, capture.snapshot().invalidResults); assertFalse(capture.snapshot().samples.get(0).valid);
        StringWriter out = new StringWriter(); capture.snapshot().writeCsv(out);
        assertTrue(out.toString().contains("4294967280,4,,COUNTER_WRAP_OR_RESET\n"));
        capture.abort("done");
    }

    @Test public void unsignedTimestampExportEscapesPhasesAndPropagatesIoFailure() throws Exception {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 1); capture.start(1);
        long token = capture.begin("A,\"B\"\nC"); capture.end(token);
        driver.values.put(1, -20L); driver.values.put(2, -10L); driver.readyAll(); capture.poll();
        StringWriter out = new StringWriter(); capture.snapshot().writeCsv(out); out.write("open");
        assertTrue(out.toString().contains("\"A,\"\"B\"\"\nC\""));
        assertTrue(out.toString().contains("18446744073709551596,18446744073709551606,10,VALID\n"));
        try {
            capture.snapshot().writeCsv(new Writer() {
                public void write(char[] data, int start, int count) throws IOException { throw new IOException("full"); }
                public void flush() { }
                public void close() { fail("Exporter does not own writer"); }
            });
            fail("Export must report failure");
        } catch (IOException expected) { assertEquals("full", expected.getMessage()); }
        assertEquals(1, capture.snapshot().samples.size());
    }

    @Test public void explicitGameFrameMappingSurvivesExtraPollsAndNestedPhases() throws Exception {
        Driver driver = new Driver();
        GpuPassTimings capture = new GpuPassTimings(driver, 2); capture.start(4);
        capture.poll(); capture.poll();
        long outer = capture.begin("WORLD", 1234), nested = capture.begin("HUD", 1234);
        capture.end(nested); capture.end(outer); driver.readyAll(); capture.poll();
        assertEquals(1234, capture.snapshot().samples.get(0).frameSequence);
        assertEquals(1234, capture.snapshot().samples.get(1).frameSequence);
        capture.poll();
        long last = capture.begin("GUI", 1236); capture.end(last); driver.readyAll(); capture.poll();
        StringWriter csv = new StringWriter(); capture.snapshot().writeCsv(csv);
        assertTrue(csv.toString().contains("1,3,1236,\"GUI\""));
        assertEquals(1236, capture.snapshot().samples.get(2).frameSequence);
        capture.abort("done");
    }

    private static final class Driver implements GpuPassTimings.QueryBackend {
        Object context = new Object();
        boolean supported = true;
        int bits = 64, calls, created, deleted, stamps, reads, nextId, failAllocationAt = -1;
        long time;
        final Map<Integer, Object> contexts = new HashMap<>();
        final Map<Integer, Long> values = new HashMap<>();
        final Set<Integer> ready = new HashSet<>();
        public Object currentContext() { calls++; return context; }
        public String supportedApi(Object ignored) { calls++; return supported ? "TEST_DRIVER" : null; }
        public int counterBits() { calls++; return bits; }
        public int createQuery() {
            calls++;
            if (nextId + 1 == failAllocationAt) throw new IllegalStateException("allocation failed");
            int id = ++nextId; created++; contexts.put(id, context); return id;
        }
        public void deleteQuery(int id) {
            calls++; assertSame("Delete must use creating context", context, contexts.remove(id));
            assertTrue(id != 0); deleted++; values.remove(id); ready.remove(id);
        }
        public void timestamp(int id) {
            calls++; assertSame(context, contexts.get(id));
            values.put(id, time += 10); ready.remove(id); stamps++;
        }
        public boolean isAvailable(int id) { calls++; assertSame(context, contexts.get(id)); return ready.contains(id); }
        public long result(int id) {
            calls++; assertTrue("Must never read an unready result", ready.contains(id));
            assertSame(context, contexts.get(id)); reads++; return values.get(id);
        }
        void readyAll() { ready.addAll(values.keySet()); }
    }
}
