package net.minecraft.client.renderer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.src.Config;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runs the production admission loop without a GL context or background meshing. */
public class RenderGlobalSchedulingTest {
    @Test public void zeroBudgetAlternatesForcedAndNormalAcrossFrames() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (int i = 0; i < 3; i++) {
                fixture.forced.add(chunk("forced-" + i));
                fixture.normal.add(chunk("normal-" + i));
            }
            for (int i = 0; i < 6; i++) {
                fixture.renderer.updateChunks(0L);
                assertEquals(i + 1, fixture.dispatcher.accepted.size());
            }
            assertEquals(Arrays.asList("forced-0:true", "normal-0:false", "forced-1:true",
                    "normal-1:false", "forced-2:true", "normal-2:false"), fixture.dispatcher.accepted);
            assertEquals(0L, fixture.dispatcher.lastDeadline);
        }
    }

    @Test public void expiredBudgetStillAlternatesOneSuccessfulAdmissionPerFrame() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.forced.add(chunk("forced-0")); fixture.forced.add(chunk("forced-1"));
            fixture.normal.add(chunk("normal-0")); fixture.normal.add(chunk("normal-1"));
            for (int i = 0; i < 4; i++) {
                fixture.renderer.updateChunks(System.nanoTime() - 1L);
                assertEquals(i + 1, fixture.dispatcher.accepted.size());
                assertTrue("The expired budget must not acquire an extra 100 ms",
                        System.nanoTime() - fixture.dispatcher.lastDeadline >= 0L);
            }
            assertEquals(Arrays.asList("forced-0:true", "normal-0:false", "forced-1:true", "normal-1:false"),
                    fixture.dispatcher.accepted);
        }
    }

    @Test public void activeDirtyWorkIsKeptAndCleanEntriesAreRemovedWithoutBlockingOtherClass() throws Exception {
        try (Fixture fixture = new Fixture()) {
            FixtureChunk active = chunk("active"), clean = chunk("clean"), ready = chunk("ready");
            active.pending = true; clean.setNeedsUpdate(false);
            fixture.forced.add(active); fixture.forced.add(clean); fixture.normal.add(ready);
            fixture.renderer.updateChunks(0L);
            assertEquals(Arrays.asList("ready:false"), fixture.dispatcher.accepted);
            assertTrue(fixture.forced.contains(active)); assertTrue(active.isNeedsUpdate());
            assertFalse(fixture.forced.contains(clean));
            assertFalse(ready.isNeedsUpdate()); assertTrue(fixture.normal.isEmpty());
            active.pending = false;
            fixture.renderer.updateChunks(0L);
            assertEquals(Arrays.asList("ready:false", "active:true"), fixture.dispatcher.accepted);
        }
    }

    @Test public void rejectedAdmissionRetainsDirtyFlagsAndSetOrderForRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            FixtureChunk forced = chunk("forced"), normal = chunk("normal");
            fixture.forced.add(forced); fixture.normal.add(normal);
            fixture.dispatcher.reject = true;
            fixture.renderer.updateChunks(0L);
            assertTrue(fixture.dispatcher.accepted.isEmpty());
            assertTrue(forced.isNeedsUpdate()); assertTrue(normal.isNeedsUpdate());
            assertTrue(fixture.forced.contains(forced)); assertTrue(fixture.normal.contains(normal));
            fixture.dispatcher.reject = false;
            fixture.renderer.updateChunks(0L); fixture.renderer.updateChunks(0L);
            assertEquals(Arrays.asList("forced:true", "normal:false"), fixture.dispatcher.accepted);
            assertTrue(fixture.forced.isEmpty()); assertTrue(fixture.normal.isEmpty());
        }
    }

    @Test public void successfulRebuildRemovesDuplicateWaitersAndDefersADirtyFollowupUntilCompletion() throws Exception {
        try (Fixture fixture = new Fixture()) {
            FixtureChunk chunk = chunk("same");
            fixture.forced.add(chunk); fixture.normal.add(chunk); fixture.transparent.add(chunk);
            fixture.renderer.updateChunks(0L);
            assertEquals(Arrays.asList("same:true"), fixture.dispatcher.accepted);
            assertTrue(fixture.normal.isEmpty()); assertTrue(fixture.transparent.isEmpty());
            chunk.setNeedsUpdate(true); fixture.normal.add(chunk);
            fixture.renderer.updateChunks(0L);
            assertEquals(1, fixture.dispatcher.accepted.size());
            assertTrue(fixture.normal.contains(chunk)); assertTrue(chunk.isNeedsUpdate());
            chunk.pending = false;
            fixture.renderer.updateChunks(0L);
            assertEquals(Arrays.asList("same:true", "same:false"), fixture.dispatcher.accepted);
        }
    }

    @Test public void lifecycleStopClearsAllWaitersBeforeStoppingDispatcher() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.normal.add(chunk("normal")); fixture.forced.add(chunk("forced"));
            fixture.transparent.add(chunk("transparent"));
            set(RenderGlobal.class, fixture.renderer, "preferNormalChunkUpdate", true);
            fixture.renderer.stopChunkUpdates();
            assertTrue(fixture.normal.isEmpty()); assertTrue(fixture.forced.isEmpty());
            assertTrue(fixture.transparent.isEmpty());
            assertEquals(false, get(RenderGlobal.class, fixture.renderer, "preferNormalChunkUpdate"));
            assertEquals(1, fixture.dispatcher.stops);
        }
    }

    @Test public void activeTransparencyEntryDoesNotBlockAnotherReadyEntry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            FixtureChunk active = chunk("active"), ready = chunk("ready"), later = chunk("later");
            active.pending = true;
            fixture.transparent.add(active); fixture.transparent.add(ready); fixture.transparent.add(later);
            fixture.renderer.updateChunks(0L);
            assertEquals(Arrays.asList("ready"), fixture.dispatcher.sorts);
            assertTrue(fixture.transparent.contains(active)); assertTrue(fixture.transparent.contains(later));
            assertFalse(fixture.transparent.contains(ready));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final RenderGlobal renderer;
        final RecordingDispatcher dispatcher;
        final Set<RenderChunk> normal = new LinkedHashSet<>(), forced = new LinkedHashSet<>(), transparent = new LinkedHashSet<>();
        final Object previousSettings;
        Fixture() throws Exception {
            previousSettings = get(Config.class, null, "gameSettings");
            GameSettings settings = bare(GameSettings.class); settings.ofChunkUpdates = 1;
            set(Config.class, null, "gameSettings", settings);
            renderer = bare(RenderGlobal.class);
            dispatcher = bare(RecordingDispatcher.class);
            dispatcher.accepted = new ArrayList<>(); dispatcher.sorts = new ArrayList<>();
            set(RenderGlobal.class, renderer, "renderDispatcher", dispatcher);
            set(RenderGlobal.class, renderer, "chunksToUpdate", normal);
            set(RenderGlobal.class, renderer, "chunksToUpdateForced", forced);
            set(RenderGlobal.class, renderer, "chunksToResortTransparency", transparent);
        }
        @Override public void close() throws Exception { set(Config.class, null, "gameSettings", previousSettings); }
    }
    public static class RecordingDispatcher extends ChunkRenderDispatcher {
        boolean reject;
        int stops;
        long lastDeadline;
        List<String> accepted, sorts;
        public RecordingDispatcher() { super(1); }
        @Override public boolean runChunkUploads(long deadline, long bytes) { lastDeadline = deadline; return false; }
        @Override public boolean updateChunkLater(RenderChunk chunk, boolean urgent) {
            assertFalse(chunk.hasPendingCompileTask());
            if (reject) return false;
            FixtureChunk fixture = (FixtureChunk)chunk;
            accepted.add(fixture.id + ":" + urgent); fixture.pending = true; return true;
        }
        @Override public boolean updateTransparencyLater(RenderChunk chunk) {
            if (reject || chunk.hasPendingCompileTask()) return false;
            sorts.add(((FixtureChunk)chunk).id); return true;
        }
        @Override public void stopChunkUpdates() { stops++; }
    }
    public static class FixtureChunk extends RenderChunk {
        String id;
        boolean pending;
        public FixtureChunk() { super(null, null, BlockPos.ORIGIN, 0); }
        @Override public boolean hasPendingCompileTask() { return pending; }
        @Override public boolean isChunkRegionEmpty() { return true; }
    }
    private static FixtureChunk chunk(String id) throws Exception {
        FixtureChunk chunk = bare(FixtureChunk.class); chunk.id = id; chunk.setNeedsUpdate(true); return chunk;
    }
    private static <T> T bare(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    private static Object get(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
