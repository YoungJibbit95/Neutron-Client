package me.youngjibbit.core.diagnostics;

import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Effective configuration is a small GL-free value publication, including legacy budget callers. */
public class PerformanceOptionsTest {
    private PerformanceOptions.Snapshot previous;

    @Before public void defaults() {
        previous = PerformanceOptions.current();
        PerformanceOptions.defaults();
    }

    @After public void restore() {
        PerformanceOptions.configure(previous.fastParticleCleanup, previous.fastVisibilityGraph,
                previous.fastGaussian, previous.sceneBlurDivisor,
                previous.uploadLimits.nanoseconds / 1_000_000D, previous.uploadLimits.bytes / 1_048_576D);
    }

    @Test public void defaultsAreLegacyAndSnapshotsRemainImmutableAfterAChange() {
        PerformanceOptions.Snapshot original = PerformanceOptions.current();
        assertFalse(original.fastParticleCleanup);
        assertFalse(original.fastVisibilityGraph);
        assertFalse(original.fastGaussian);
        assertEquals(1, original.sceneBlurDivisor);
        assertSame(ChunkFrameBudget.DEFAULT, original.uploadLimits);

        assertTrue(PerformanceOptions.configure(true, true, true, 4, 2.5, 12));
        PerformanceOptions.Snapshot changed = PerformanceOptions.current();
        assertTrue(PerformanceOptions.isFastParticleCleanupEnabled());
        assertTrue(PerformanceOptions.isFastVisibilityGraphEnabled());
        assertTrue(PerformanceOptions.isFastGaussianEnabled());
        assertEquals(4, PerformanceOptions.getSceneBlurDivisor());
        assertSame(changed.uploadLimits, ChunkFrameBudget.current());
        assertEquals(2_500_000L, changed.uploadLimits.nanoseconds);
        assertEquals(12 * 1_048_576L, changed.uploadLimits.bytes);
        assertEquals(original.revision + 1, changed.revision);
        assertFalse(original.fastGaussian);
        assertEquals(1, original.sceneBlurDivisor);
        assertSame(ChunkFrameBudget.DEFAULT, original.uploadLimits);
    }

    @Test public void unchangedApplyPreservesIdentityButRoundTripHasANewerRevision() {
        PerformanceOptions.configure(true, false, true, 2, 1.5, 7);
        PerformanceOptions.Snapshot original = PerformanceOptions.current();
        assertFalse(PerformanceOptions.configure(true, false, true, 2, 1.5, 7));
        assertSame(original, PerformanceOptions.current());
        ChunkFrameBudget.configure(1.5, 7);
        assertSame(original, PerformanceOptions.current());

        PerformanceOptions.configure(false, false, true, 2);
        PerformanceOptions.configure(true, false, true, 2);
        assertEquals(original.revision + 2, PerformanceOptions.current().revision);
        assertNotSame(original, PerformanceOptions.current());
    }

    @Test public void legacyBudgetCallsPreserveStrategyChoicesAndAllOptionsResetIsSeparate() {
        PerformanceOptions.configure(true, true, true, 4);
        long before = PerformanceOptions.current().revision;
        ChunkFrameBudget.configure(.5, 8);
        assertEquals(before + 1, PerformanceOptions.current().revision);
        assertTrue(PerformanceOptions.isFastParticleCleanupEnabled());
        assertTrue(PerformanceOptions.isFastVisibilityGraphEnabled());
        assertTrue(PerformanceOptions.isFastGaussianEnabled());
        assertEquals(4, PerformanceOptions.getSceneBlurDivisor());
        ChunkFrameBudget.defaults();
        assertSame(ChunkFrameBudget.DEFAULT, ChunkFrameBudget.current());
        assertTrue(PerformanceOptions.isFastGaussianEnabled());
        assertEquals(4, PerformanceOptions.getSceneBlurDivisor());
        PerformanceOptions.defaults();
        assertFalse(PerformanceOptions.isFastParticleCleanupEnabled());
        assertFalse(PerformanceOptions.isFastVisibilityGraphEnabled());
        assertFalse(PerformanceOptions.isFastGaussianEnabled());
        assertEquals(1, PerformanceOptions.getSceneBlurDivisor());
        assertSame(ChunkFrameBudget.DEFAULT, ChunkFrameBudget.current());
    }

    @Test public void invalidFullConfigurationsNeverPublishPartialChanges() {
        PerformanceOptions.configure(true, false, false, 2, .75, 2);
        PerformanceOptions.Snapshot original = PerformanceOptions.current();
        for (int invalid : new int[]{0, -1, 3, 8}) {
            try { PerformanceOptions.configure(false, true, true, invalid, 4, 16); fail(); }
            catch (IllegalArgumentException expected) { }
            assertSame(original, PerformanceOptions.current());
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -.1, 100}) {
            try { PerformanceOptions.configure(false, true, true, 4, invalid, 16); fail(); }
            catch (IllegalArgumentException expected) { }
            assertSame(original, PerformanceOptions.current());
            try { PerformanceOptions.configure(false, true, true, 4, 4, invalid); fail(); }
            catch (IllegalArgumentException expected) { }
            assertSame(original, PerformanceOptions.current());
        }
    }

    @Test public void workerReadersNeverSeeAMixedStrategyAndBudgetPublication() throws Exception {
        PerformanceOptions.configure(false, true, false, 1, .25, .25);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                long lastRevision = -1;
                for (int i = 0; i < 100_000; i++) {
                    PerformanceOptions.Snapshot snapshot = PerformanceOptions.current();
                    assertTrue(snapshot.revision >= lastRevision);
                    lastRevision = snapshot.revision;
                    if (snapshot.fastParticleCleanup) {
                        assertFalse(snapshot.fastVisibilityGraph);
                        assertTrue(snapshot.fastGaussian);
                        assertEquals(4, snapshot.sceneBlurDivisor);
                        assertEquals(8_000_000L, snapshot.uploadLimits.nanoseconds);
                        assertEquals(32 * 1_048_576L, snapshot.uploadLimits.bytes);
                    } else {
                        assertTrue(snapshot.fastVisibilityGraph);
                        assertFalse(snapshot.fastGaussian);
                        assertEquals(1, snapshot.sceneBlurDivisor);
                        assertEquals(250_000L, snapshot.uploadLimits.nanoseconds);
                        assertEquals(262_144L, snapshot.uploadLimits.bytes);
                    }
                }
            } catch (Throwable error) { failure.set(error); }
        }, "performance-options-reader");
        reader.start();
        for (int i = 0; i < 10_000; i++) {
            PerformanceOptions.configure(true, false, true, 4, 8, 32);
            PerformanceOptions.configure(false, true, false, 1, .25, .25);
        }
        reader.join(5_000);
        assertFalse("Reader did not finish", reader.isAlive());
        if (failure.get() != null) throw new AssertionError("Inconsistent worker snapshot", failure.get());
    }

    @Test public void metadataRecordsTheExactEffectiveSnapshotEvenWithoutClientSettings() {
        PerformanceOptions.configure(true, false, true, 2, 2, 6);
        PerformanceOptions.Snapshot snapshot = PerformanceOptions.current();
        JsonObject metadata = PerformanceCaptureMetadata.configuration(null).getAsJsonObject("effectivePerformance");
        assertEquals(snapshot.revision, metadata.get("revision").getAsLong());
        assertTrue(metadata.get("fastParticleCleanup").getAsBoolean());
        assertFalse(metadata.get("fastVisibilityGraph").getAsBoolean());
        assertTrue(metadata.get("fastGaussian").getAsBoolean());
        assertEquals(2, metadata.get("sceneBlurDivisor").getAsInt());
        assertEquals(2_000_000L, metadata.get("uploadBudgetNanos").getAsLong());
        assertEquals(6 * 1_048_576L, metadata.get("uploadBudgetBytes").getAsLong());
    }
}
