package net.minecraft.client.renderer.chunk;

import java.lang.reflect.Field;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises real admission without starting workers or creating GL buffers. */
public class ChunkRenderDispatcherTest {
    @Test public void activeTasksKeepDirtyInvalidationsWithoutBeingReplaced() throws Exception {
        for (ChunkCompileTaskGenerator.Status status : new ChunkCompileTaskGenerator.Status[]{
                ChunkCompileTaskGenerator.Status.PENDING, ChunkCompileTaskGenerator.Status.COMPILING,
                ChunkCompileTaskGenerator.Status.UPLOADING}) {
            ChunkRenderDispatcher dispatcher = bare(ChunkRenderDispatcher.class);
            BlockingQueue<ChunkCompileTaskGenerator> queue = new ArrayBlockingQueue<>(1);
            set(ChunkRenderDispatcher.class, dispatcher, "queueChunkUpdates", queue);
            RenderChunk chunk = bare(RenderChunk.class);
            set(RenderChunk.class, chunk, "lockCompileTask", new ReentrantLock());
            ChunkCompileTaskGenerator active = new ChunkCompileTaskGenerator(chunk,
                    ChunkCompileTaskGenerator.Type.REBUILD_CHUNK);
            active.setStatus(status);
            set(RenderChunk.class, chunk, "compileTask", active);
            chunk.setNeedsUpdate(true);
            assertTrue(chunk.hasPendingCompileTask());
            assertFalse(dispatcher.updateChunkLater(chunk, true));
            assertFalse(dispatcher.updateTransparencyLater(chunk));
            assertSame(active, get(RenderChunk.class, chunk, "compileTask"));
            assertFalse(active.isFinished());
            assertTrue("later dirty revision remains pending", chunk.isNeedsUpdate());
            assertTrue(queue.isEmpty());
        }
    }

    @Test public void cancellationCallbacksRunOnceIncludingLateSubscribers() {
        ChunkCompileTaskGenerator task = new ChunkCompileTaskGenerator(null,
                ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        task.addFinishRunnable(calls::incrementAndGet);
        task.finish(); task.finish();
        assertEquals(1, calls.get());
        task.addFinishRunnable(calls::incrementAndGet);
        task.finish();
        assertEquals(2, calls.get());
    }

    @Test public void rejectedTransparencyTaskCanBeRetriedAfterCapacityReturns() throws Exception {
        ChunkRenderDispatcher dispatcher = bare(ChunkRenderDispatcher.class);
        BlockingQueue<ChunkCompileTaskGenerator> queue = new ArrayBlockingQueue<>(1);
        set(ChunkRenderDispatcher.class, dispatcher, "queueChunkUpdates", queue);
        RenderChunk chunk = bare(RenderChunk.class);
        set(RenderChunk.class, chunk, "lockCompileTask", new ReentrantLock());
        set(RenderChunk.class, chunk, "compiledChunk", CompiledChunk.DUMMY);
        ChunkCompileTaskGenerator occupying = new ChunkCompileTaskGenerator(chunk,
                ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY);
        queue.add(occupying);
        assertFalse(dispatcher.updateTransparencyLater(chunk));
        ChunkCompileTaskGenerator rejected = (ChunkCompileTaskGenerator) get(RenderChunk.class, chunk, "compileTask");
        assertTrue("queue rejection must finish the off-queue task", rejected.isFinished());
        assertEquals(ChunkCompileTaskGenerator.Status.DONE, rejected.getStatus());
        assertSame(occupying, queue.remove());
        assertTrue(dispatcher.updateTransparencyLater(chunk));
        ChunkCompileTaskGenerator retried = queue.poll();
        assertNotNull("retry must enqueue, not report unqueued pending work as accepted", retried);
        assertNotSame(rejected, retried);
        assertEquals(ChunkCompileTaskGenerator.Status.PENDING, retried.getStatus());
        retried.finish();
    }

    @Test public void cancelledTransparencyTaskRemovesOnlyItsOwnQueueEntry() throws Exception {
        ChunkRenderDispatcher dispatcher = bare(ChunkRenderDispatcher.class);
        BlockingQueue<ChunkCompileTaskGenerator> queue = new ArrayBlockingQueue<>(2);
        set(ChunkRenderDispatcher.class, dispatcher, "queueChunkUpdates", queue);
        RenderChunk chunk = bare(RenderChunk.class);
        set(RenderChunk.class, chunk, "lockCompileTask", new ReentrantLock());
        set(RenderChunk.class, chunk, "compiledChunk", CompiledChunk.DUMMY);
        ChunkCompileTaskGenerator unrelated = new ChunkCompileTaskGenerator(chunk,
                ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY);
        queue.add(unrelated);
        assertTrue(dispatcher.updateTransparencyLater(chunk));
        ChunkCompileTaskGenerator accepted = (ChunkCompileTaskGenerator) get(RenderChunk.class, chunk, "compileTask");
        accepted.finish();
        assertEquals(1, queue.size());
        assertSame(unrelated, queue.poll());
    }

    private static <T> T bare(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    private static Object get(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
