package net.minecraft.client.renderer.chunk;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual worker transitions with CPU-only fixture mesh and builder ownership counters. */
public class ChunkRenderWorkerLifecycleTest {
    @Test public void failedUploadFutureReturnsLeaseOnceAndRetainsTheRenderingFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.dispatcher.uploadFailure = new IllegalStateException("fixture GL upload failure");
            fixture.worker.processTask(fixture.task);
            assertEquals(1, fixture.dispatcher.returned);
            assertTrue(fixture.task.isFinished());
            assertNull(fixture.task.getRegionRenderCacheBuilder());
            assertEquals(true, get(Minecraft.class, fixture.client, "hasCrashed"));
            fixture.task.finish();
            assertEquals("A later cancellation must not return the lease again", 1, fixture.dispatcher.returned);
        }
    }

    @Test public void cancellationDuringBuildReturnsLeaseWithoutEnqueuingUploads() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.chunk.cancelDuringBuild = true;
            fixture.worker.processTask(fixture.task);
            assertEquals(1, fixture.dispatcher.returned);
            assertEquals(0, fixture.dispatcher.getPendingUploadCount());
            assertTrue(fixture.task.isFinished());
            assertNull(fixture.chunk.published);
        }
    }

    @Test public void interruptedBuilderWaitDoesNotReturnAnUnacquiredLease() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.dispatcher.interruptAllocation = true;
            try { fixture.worker.processTask(fixture.task); fail("interrupt must propagate"); }
            catch (InterruptedException expected) { }
            assertEquals(0, fixture.dispatcher.returned);
            assertTrue(fixture.task.isFinished());
            assertNull(fixture.task.getRegionRenderCacheBuilder());
        }
    }

    @Test public void compilerFailureReturnsBuilderOnceAndFinishesTask() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.chunk.failure = new IllegalStateException("fixture compiler failure");
            try {
                fixture.worker.processTask(fixture.task);
                fail("compiler failure must propagate");
            } catch (IllegalStateException expected) {
                assertSame(fixture.chunk.failure, expected);
            }
            assertEquals(1, fixture.dispatcher.returned);
            assertTrue(fixture.task.isFinished());
            assertEquals(ChunkCompileTaskGenerator.Status.DONE, fixture.task.getStatus());
            assertNull("released task must not retain pooled builder", fixture.task.getRegionRenderCacheBuilder());
        }
    }

    @Test public void emptyMeshPublishesOnlyWhenClientThreadConsumesCompletion() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.compileOnWorker();
            assertNull("worker must not publish an empty mesh directly", fixture.chunk.published);
            fixture.runCompletions();
            assertSame(fixture.chunk.result, fixture.chunk.published);
            assertSame(Thread.currentThread(), fixture.chunk.publishThread);
            assertEquals(1, fixture.dispatcher.returned);
        }
    }

    @Test public void cancelledEmptyMeshDoesNotPublishAfterOwnerThreadHandoff() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.compileOnWorker();
            fixture.task.finish();
            fixture.runCompletions();
            assertNull(fixture.chunk.published);
            assertEquals(1, fixture.dispatcher.returned);
        }
    }

    @Test public void replacedTaskCannotPublishItsCompletion() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.compileOnWorker();
            ChunkCompileTaskGenerator replacement = new ChunkCompileTaskGenerator(fixture.chunk,
                    ChunkCompileTaskGenerator.Type.REBUILD_CHUNK);
            set(RenderChunk.class, fixture.chunk, "compileTask", replacement);
            fixture.runCompletions();
            assertNull("completion must still belong to the current RenderChunk task", fixture.chunk.published);
            assertSame(replacement, get(RenderChunk.class, fixture.chunk, "compileTask"));
            assertEquals(1, fixture.dispatcher.returned);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Minecraft previousClient;
        final Minecraft client;
        final FixtureDispatcher dispatcher;
        final FixtureChunk chunk;
        final ChunkCompileTaskGenerator task;
        final ChunkRenderWorker worker;

        Fixture() throws Exception {
            previousClient = (Minecraft) get(Minecraft.class, null, "theMinecraft");
            client = bare(Minecraft.class);
            set(Minecraft.class, client, "mcThread", Thread.currentThread());
            set(Minecraft.class, client, "renderViewEntity", bare(Camera.class));
            set(Minecraft.class, null, "theMinecraft", client);
            dispatcher = bare(FixtureDispatcher.class);
            dispatcher.builder = bare(RegionRenderCacheBuilder.class);
            set(ChunkRenderDispatcher.class, dispatcher, "queueChunkUploads", new ArrayDeque<>());
            chunk = bare(FixtureChunk.class);
            chunk.result = new CompiledChunk();
            set(RenderChunk.class, chunk, "lockCompileTask", new ReentrantLock());
            set(RenderChunk.class, chunk, "lockCompiledChunk", new ReentrantLock());
            set(RenderChunk.class, chunk, "field_181056_j", new java.util.HashSet<>());
            task = new ChunkCompileTaskGenerator(chunk, ChunkCompileTaskGenerator.Type.REBUILD_CHUNK);
            set(RenderChunk.class, chunk, "compileTask", task);
            worker = new ChunkRenderWorker(dispatcher);
        }

        void compileOnWorker() throws Exception {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try { worker.processTask(task); }
                catch (Throwable error) { failure.set(error); }
            }, "fixture-mesh-worker");
            thread.start();
            thread.join(10000);
            assertFalse("fixture mesh must finish", thread.isAlive());
            if (failure.get() != null) throw new AssertionError(failure.get());
        }

        void runCompletions() {
            dispatcher.runChunkUploads(Long.MAX_VALUE);
        }

        @Override public void close() throws Exception {
            set(Minecraft.class, null, "theMinecraft", previousClient);
        }
    }

    public static class FixtureDispatcher extends ChunkRenderDispatcher {
        RegionRenderCacheBuilder builder;
        int returned;
        boolean interruptAllocation;
        RuntimeException uploadFailure;
        public FixtureDispatcher() { super(1); }
        @Override public RegionRenderCacheBuilder allocateRenderBuilder() throws InterruptedException {
            if (interruptAllocation) throw new InterruptedException("fixture builder wait interrupted");
            return builder;
        }
        @Override public void freeRenderBuilder(RegionRenderCacheBuilder value) {
            assertSame(builder, value);
            returned++;
            assertEquals("builder must be returned exactly once", 1, returned);
        }
        @Override public ListenableFuture<Object> uploadChunk(ChunkCompileTaskGenerator task) {
            return uploadFailure == null ? super.uploadChunk(task) : Futures.immediateFailedFuture(uploadFailure);
        }
    }

    public static class FixtureChunk extends RenderChunk {
        RuntimeException failure;
        boolean cancelDuringBuild;
        CompiledChunk result;
        CompiledChunk published;
        Thread publishThread;
        public FixtureChunk() { super(null, null, BlockPos.ORIGIN, 0); }
        @Override public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGenerator task) {
            if (failure != null) throw failure;
            task.setCompiledChunk(result);
            if (cancelDuringBuild) task.finish();
        }
        @Override public void setNeedsUpdate(boolean value) { }
        @Override public void setCompiledChunk(CompiledChunk value) {
            published = value;
            publishThread = Thread.currentThread();
        }
    }

    public static class Camera extends Entity {
        public Camera() { super(null); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
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
