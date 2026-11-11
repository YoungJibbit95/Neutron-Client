package net.minecraft.client.renderer.chunk;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import me.youngjibbit.core.diagnostics.ChunkPipelineMetrics;
import net.minecraft.util.EnumWorldBlockLayer;
import org.junit.Test;
import static org.junit.Assert.*;

/** Publication/draw ordering, including a resort before the first geometry submission. */
public class CompiledChunkTraceTest {
    @Test public void rebuildThenResortThenDrawPreservesDataAssociationAndClearsBothReferences() throws Exception {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(3);
        now.set(110);
        metrics.dataAvailable(2, 3, 1 << 4);
        try (ChunkUploadGroupTest.Fixture fixture = new ChunkUploadGroupTest.Fixture()) {
            ChunkCompileTaskGenerator rebuild = fixture.task(1);
            CompiledChunk geometry = rebuild.getCompiledChunk();
            geometry.setLayerUsed(EnumWorldBlockLayer.SOLID);
            ChunkPipelineMetrics.Trace buildTrace = metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
            setTrace(rebuild, buildTrace);
            fixture.enqueue(rebuild);
            fixture.dispatcher.runChunkUploads(Long.MAX_VALUE);

            RenderChunk chunk = rebuild.getRenderChunk();
            ChunkCompileTaskGenerator sort = chunk.makeCompileTaskTransparency();
            assertNotNull(sort);
            assertSame(geometry, sort.getCompiledChunk());
            ChunkPipelineMetrics.Trace sortTrace = metrics.beginTask(2, 4, 3, "RESORT_TRANSPARENCY", 0);
            setTrace(sort, sortTrace);
            sort.setRegionRenderCacheBuilder(rebuild.getRegionRenderCacheBuilder());
            sort.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);
            fixture.enqueue(sort);
            fixture.dispatcher.runChunkUploads(Long.MAX_VALUE);

            assertSame(geometry, chunk.getCompiledChunk());
            assertSame(buildTrace, geometry.getRenderTrace());
            assertSame(sortTrace, geometry.getSortTrace());
            now.set(140);
            chunk.onDrawSubmitted();
            assertNull(geometry.getRenderTrace());
            assertNull(geometry.getSortTrace());
            now.set(150);
            chunk.onDrawSubmitted();
            metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
            List<ChunkPipelineMetrics.TraceSnapshot> rows = metrics.snapshot().traces;
            assertEquals(ChunkPipelineMetrics.Outcome.DRAW_SUBMITTED, rows.get(0).outcome);
            assertEquals(ChunkPipelineMetrics.Outcome.DRAW_SUBMITTED, rows.get(1).outcome);
            assertEquals(40, rows.get(0).firstDrawSubmitNanos);
            assertEquals(40, rows.get(1).firstDrawSubmitNanos);
            assertEquals(10, rows.get(0).dataAvailableNanos);
            assertEquals(-1, rows.get(1).dataAvailableNanos);
            assertEquals("The original section mark was retired by the actual draw", -1,
                    rows.get(2).dataAvailableNanos);
        }
    }

    @Test public void drawReleasesRetiredCaptureReferencesWithoutReportingLateObservations() {
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(() -> 100L);
        metrics.start(2);
        CompiledChunk geometry = new CompiledChunk();
        geometry.setRenderTrace(metrics.beginTask(0, 0, 0, "REBUILD_CHUNK", 0));
        geometry.setSortTrace(metrics.beginTask(0, 0, 0, "RESORT_TRANSPARENCY", 0));
        metrics.stop();
        geometry.onDrawSubmitted();
        assertNull(geometry.getRenderTrace());
        assertNull(geometry.getSortTrace());
        for (ChunkPipelineMetrics.TraceSnapshot row : metrics.snapshot().traces) {
            assertEquals(ChunkPipelineMetrics.Outcome.QUEUED, row.outcome);
            assertEquals(-1, row.firstDrawSubmitNanos);
        }
    }

    @Test public void sharedDummyNeverRetainsOneRenderersTrace() {
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(() -> 100L);
        metrics.start(1);
        ChunkPipelineMetrics.Trace trace = metrics.beginTask(0, 0, 0, "RESORT_TRANSPARENCY", 0);
        CompiledChunk.DUMMY.setRenderTrace(trace);
        CompiledChunk.DUMMY.setSortTrace(trace);
        assertNull(CompiledChunk.DUMMY.getRenderTrace());
        assertNull(CompiledChunk.DUMMY.getSortTrace());
        CompiledChunk.DUMMY.onDrawSubmitted();
        assertEquals(ChunkPipelineMetrics.Outcome.QUEUED, metrics.snapshot().traces.get(0).outcome);
    }

    private static void setTrace(ChunkCompileTaskGenerator task, ChunkPipelineMetrics.Trace trace) throws Exception {
        Field field = ChunkCompileTaskGenerator.class.getDeclaredField("trace");
        field.setAccessible(true);
        field.set(task, trace);
    }
}
