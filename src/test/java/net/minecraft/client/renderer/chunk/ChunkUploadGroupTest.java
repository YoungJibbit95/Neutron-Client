package net.minecraft.client.renderer.chunk;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real group/budget/publication code; upload calls are recorded instead of invoking OpenGL. */
public class ChunkUploadGroupTest {
    @Test public void uploadBytesMatchTheActualQuadToTriangleBufferExpansion() throws Exception {
        WorldRenderer buffer = new WorldRenderer(32);
        set(WorldRenderer.class, buffer, "vertexFormat", DefaultVertexFormats.field_181705_e);
        buffer.drawMode = 7;
        buffer.vertexCount = 4;
        buffer.getByteBuffer().limit(4 * DefaultVertexFormats.field_181705_e.getNextOffset());
        long original = buffer.getByteBuffer().remaining();
        long expected = ChunkRenderDispatcher.getUploadSize(buffer, true);
        assertEquals(original, ChunkRenderDispatcher.getUploadSize(buffer, false));
        buffer.quadsToTriangles();
        assertEquals(expected, buffer.getByteBuffer().remaining());
        assertEquals("Already converted buffers must not be expanded in accounting again",
                expected, ChunkRenderDispatcher.getUploadSize(buffer, true));
    }

    @Test public void byteBudgetNeverSplitsAChunkRevision() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ChunkCompileTaskGenerator first = fixture.task(1), second = fixture.task(2);
            ListenableFuture<Object> a = fixture.enqueue(first), b = fixture.enqueue(second);
            assertTrue(fixture.dispatcher.runChunkUploads(Long.MAX_VALUE, 1L));
            assertEquals(2, fixture.dispatcher.uploads.size());
            assertTrue(((FixtureChunk)first.getRenderChunk()).published);
            assertFalse(((FixtureChunk)second.getRenderChunk()).published);
            assertTrue(a.isDone()); assertFalse(b.isDone());
            assertEquals(1, fixture.dispatcher.getPendingUploadCount());
            assertTrue(fixture.dispatcher.runChunkUploads(Long.MAX_VALUE, 1L));
            assertEquals(4, fixture.dispatcher.uploads.size());
            assertTrue(((FixtureChunk)second.getRenderChunk()).published);
            assertTrue(b.isDone());
            assertEquals(0, fixture.dispatcher.getPendingUploadCount());
        }
    }

    @Test public void expiredDeadlineStillMakesOneWholeGroupOfProgress() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.enqueue(fixture.task(1)); fixture.enqueue(fixture.task(2));
            assertTrue(fixture.dispatcher.runChunkUploads(System.nanoTime() - 1L, Long.MAX_VALUE));
            assertEquals(2, fixture.dispatcher.uploads.size());
            assertEquals(1, fixture.dispatcher.getPendingUploadCount());
        }
    }

    @Test public void cancelledAndReplacedTasksNeverTouchBuffersOrPublish() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ChunkCompileTaskGenerator cancelled = fixture.task(1), replaced = fixture.task(2);
            ListenableFuture<Object> a = fixture.enqueue(cancelled);
            fixture.enqueue(replaced);
            a.cancel(false);
            set(RenderChunk.class, replaced.getRenderChunk(), "compileTask",
                    new ChunkCompileTaskGenerator(replaced.getRenderChunk(), ChunkCompileTaskGenerator.Type.REBUILD_CHUNK));
            fixture.dispatcher.runChunkUploads(Long.MAX_VALUE);
            assertTrue(fixture.dispatcher.uploads.isEmpty());
            assertFalse(((FixtureChunk)cancelled.getRenderChunk()).published);
            assertFalse(((FixtureChunk)replaced.getRenderChunk()).published);
        }
    }

    @Test public void stopCompileTaskInvalidatesQueuedGroupBeforeAnyUpload() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ChunkCompileTaskGenerator task = fixture.task(1);
            fixture.enqueue(task);
            task.getRenderChunk().stopCompileTask();
            fixture.dispatcher.runChunkUploads(Long.MAX_VALUE);
            assertTrue(fixture.dispatcher.uploads.isEmpty());
            assertFalse(((FixtureChunk)task.getRenderChunk()).published);
            assertEquals(ChunkCompileTaskGenerator.Status.DONE, task.getStatus());
        }
    }

    @Test public void failedLayerUploadDoesNotPublishACompleteRevision() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ChunkCompileTaskGenerator task = fixture.task(1);
            fixture.dispatcher.failLayer = EnumWorldBlockLayer.TRANSLUCENT;
            ListenableFuture<Object> future = fixture.enqueue(task);
            fixture.dispatcher.runChunkUploads(Long.MAX_VALUE);
            try { future.get(); fail("upload failure must be retained by future"); }
            catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
            assertFalse(((FixtureChunk)task.getRenderChunk()).published);
        }
    }

    static final class Fixture implements AutoCloseable {
        final Minecraft previous;
        final RecordingDispatcher dispatcher;
        Fixture() throws Exception {
            previous = Minecraft.getMinecraft();
            Minecraft client = bare(Minecraft.class);
            set(Minecraft.class, client, "mcThread", Thread.currentThread());
            set(Minecraft.class, null, "theMinecraft", client);
            dispatcher = bare(RecordingDispatcher.class);
            dispatcher.uploads = new ArrayList<>();
            set(ChunkRenderDispatcher.class, dispatcher, "queueChunkUploads", new ArrayDeque<>());
        }
        ChunkCompileTaskGenerator task(int id) throws Exception {
            FixtureChunk chunk = bare(FixtureChunk.class);
            chunk.id = id;
            set(RenderChunk.class, chunk, "lockCompileTask", new ReentrantLock());
            set(RenderChunk.class, chunk, "lockCompiledChunk", new ReentrantLock());
            set(RenderChunk.class, chunk, "field_181056_j", new HashSet<>());
            RegionRenderCacheBuilder builder = bare(RegionRenderCacheBuilder.class);
            WorldRenderer[] layers = new WorldRenderer[4];
            for (int i = 0; i < layers.length; i++) layers[i] = new WorldRenderer(8);
            set(RegionRenderCacheBuilder.class, builder, "worldRenderers", layers);
            CompiledChunk compiled = new CompiledChunk();
            compiled.setLayerStarted(EnumWorldBlockLayer.SOLID);
            compiled.setLayerStarted(EnumWorldBlockLayer.TRANSLUCENT);
            ChunkCompileTaskGenerator task = new ChunkCompileTaskGenerator(chunk,
                    ChunkCompileTaskGenerator.Type.REBUILD_CHUNK);
            task.setRegionRenderCacheBuilder(builder);
            task.setCompiledChunk(compiled);
            task.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);
            set(RenderChunk.class, chunk, "compileTask", task);
            return task;
        }
        ListenableFuture<Object> enqueue(ChunkCompileTaskGenerator task) throws Exception {
            AtomicReference<ListenableFuture<Object>> future = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try { future.set(dispatcher.uploadChunk(task)); }
                catch (Throwable error) { failure.set(error); }
            });
            worker.start(); worker.join(10000);
            assertFalse(worker.isAlive());
            if (failure.get() != null) throw new AssertionError(failure.get());
            return future.get();
        }
        @Override public void close() throws Exception { set(Minecraft.class, null, "theMinecraft", previous); }
    }

    public static class RecordingDispatcher extends ChunkRenderDispatcher {
        List<String> uploads;
        EnumWorldBlockLayer failLayer;
        public RecordingDispatcher() { super(1); }
        @Override public ListenableFuture<Object> uploadChunk(EnumWorldBlockLayer layer,
                WorldRenderer buffer, RenderChunk chunk, CompiledChunk compiled) {
            assertTrue(Minecraft.getMinecraft().isCallingFromMinecraftThread());
            assertFalse("revision publishes after every layer", ((FixtureChunk)chunk).published);
            if (layer == failLayer) throw new IllegalStateException("fixture upload failure");
            uploads.add(((FixtureChunk)chunk).id + ":" + layer.name());
            return Futures.immediateFuture(null);
        }
    }
    public static class FixtureChunk extends RenderChunk {
        int id;
        boolean published;
        public FixtureChunk() { super(null, null, BlockPos.ORIGIN, 0); }
        @Override public void setNeedsUpdate(boolean value) { }
        @Override public void setCompiledChunk(CompiledChunk compiled) { super.setCompiledChunk(compiled); published = true; }
    }
    private static <T> T bare(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
