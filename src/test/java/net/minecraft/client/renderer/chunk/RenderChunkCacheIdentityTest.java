package net.minecraft.client.renderer.chunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.junit.Test;
import static org.junit.Assert.*;

/** The reusable renderer's last column must not override a task's coordinates. */
public class RenderChunkCacheIdentityTest {
    @Test public void loadedColumnFromAnOlderLocationCannotSatisfyTheCurrentLocation() throws Exception {
        FixtureWorld world = bare(FixtureWorld.class);
        world.first = column(-3, 2);
        world.second = column(5, -7);
        RenderChunk renderer = bare(RenderChunk.class);
        set(RenderChunk.class, renderer, "world", world);
        set(RenderChunk.class, renderer, "chunk", world.first);
        set(RenderChunk.class, renderer, "position", new BlockPos(80, 48, -112));
        assertSame(world.second, renderer.getChunk());
        assertSame(world.second, renderer.getChunk());
        assertEquals(1, world.lookups);
    }

    @Test public void overlappingOldAndNewTaskLookupsAlwaysReturnTheirOwnColumn() throws Exception {
        final FixtureWorld world = bare(FixtureWorld.class);
        world.first = column(-3, 2);
        world.second = column(5, -7);
        final RenderChunk renderer = bare(RenderChunk.class);
        set(RenderChunk.class, renderer, "world", world);
        final Method lookup = RenderChunk.class.getDeclaredMethod("getChunk", BlockPos.class);
        lookup.setAccessible(true);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread oldTask = new Thread(() -> checkColumn(renderer, lookup, world.first, failure));
        Thread newTask = new Thread(() -> checkColumn(renderer, lookup, world.second, failure));
        oldTask.start(); newTask.start();
        oldTask.join(10000); newTask.join(10000);
        assertFalse(oldTask.isAlive()); assertFalse(newTask.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void checkColumn(RenderChunk renderer, Method lookup, Chunk expected,
            AtomicReference<Throwable> failure) {
        try {
            BlockPos position = new BlockPos(expected.xPosition << 4, 48, expected.zPosition << 4);
            for (int i = 0; i < 10000; i++) assertSame(expected, lookup.invoke(renderer, position));
        } catch (Throwable error) { failure.compareAndSet(null, error); }
    }

    public static class FixtureWorld extends WorldClient {
        Chunk first, second;
        int lookups;
        public FixtureWorld() { super(null, null, 0, null, null); }
        @Override public Chunk getChunkFromBlockCoords(BlockPos position) {
            lookups++;
            if (first.xPosition == position.getX() >> 4 && first.zPosition == position.getZ() >> 4) return first;
            if (second.xPosition == position.getX() >> 4 && second.zPosition == position.getZ() >> 4) return second;
            throw new AssertionError("Unexpected fixture coordinates " + position);
        }
    }

    private static Chunk column(int x, int z) throws Exception {
        Chunk chunk = bare(Chunk.class);
        set(Chunk.class, chunk, "xPosition", x);
        set(Chunk.class, chunk, "zPosition", z);
        set(Chunk.class, chunk, "isChunkLoaded", true);
        return chunk;
    }
    private static <T> T bare(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)field.get(null)).allocateInstance(type));
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
