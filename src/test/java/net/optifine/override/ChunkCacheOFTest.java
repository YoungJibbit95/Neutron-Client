package net.optifine.override;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkCacheOFTest {
    @Test public void chunkMeshingCacheIsBoundedToRequestedHalo() throws Exception {
        try (ChunkCacheOFFixture fixture = new ChunkCacheOFFixture()) {
            for (BlockPos origin : new BlockPos[]{BlockPos.ORIGIN,
                    new BlockPos(-16, -32, -48), new BlockPos(16, 240, 32)}) {
                ChunkCacheOFFixture.CountingCache source = fixture.source();
                ChunkCacheOF cache = new ChunkCacheOF(source, origin.add(-1, -1, -1), origin.add(16, 16, 16), 1);
                assertEquals("20 cubed entries cover the complete meshing halo", 8000,
                        ChunkCacheOFFixture.value(cache, "arraySize"));
                verifyRegion(cache, source, origin.add(-2, -2, -2), origin.add(17, 17, 17));
            }
        }
    }

    @Test public void rectangularRegionAndZeroMarginHaveUniqueCacheIndices() throws Exception {
        try (ChunkCacheOFFixture fixture = new ChunkCacheOFFixture()) {
            for (int margin : new int[]{0, 2}) {
                BlockPos from = new BlockPos(-12, 5, 31);
                BlockPos to = new BlockPos(-7, 8, 39);
                ChunkCacheOFFixture.CountingCache source = fixture.source();
                ChunkCacheOF cache = new ChunkCacheOF(source, from, to, margin);
                verifyRegion(cache, source, from.add(-margin, -margin, -margin), to.add(margin, margin, margin));
            }
        }
    }

    @Test public void releasedAndReusedCachesDoNotReturnPreviousBuildValues() throws Exception {
        try (ChunkCacheOFFixture fixture = new ChunkCacheOFFixture()) {
            ChunkCacheOFFixture.CountingCache source = fixture.source();
            ChunkCacheOF cache = new ChunkCacheOF(source, new BlockPos(-1, -1, -1), new BlockPos(16, 16, 16), 1);
            BlockPos pos = new BlockPos(5, 7, 9);
            cache.renderStart();
            IBlockState oldState = cache.getBlockState(pos);
            int oldLight = cache.getCombinedLight(pos, 0);
            source.revision++;
            assertSame(oldState, cache.getBlockState(pos));
            assertEquals(oldLight, cache.getCombinedLight(pos, 0));
            cache.renderFinish();
            assertNull(ChunkCacheOFFixture.field(cache, "blockStates"));
            assertNull(ChunkCacheOFFixture.field(cache, "combinedLights"));
            assertSame(source.expectedState(pos), cache.getBlockState(pos));
            assertEquals(source.expectedLight(pos, 0), cache.getCombinedLight(pos, 0));
            cache.renderStart();
            assertSame(source.expectedState(pos), cache.getBlockState(pos));
            assertEquals(source.expectedLight(pos, 0), cache.getCombinedLight(pos, 0));
            cache.renderFinish();
            cache.renderFinish();
        }
    }

    @Test public void concurrentBuildersKeepIndependentCacheStorage() throws Exception {
        try (ChunkCacheOFFixture fixture = new ChunkCacheOFFixture()) {
            final CountDownLatch start = new CountDownLatch(1);
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread[] workers = new Thread[4];
            for (int i = 0; i < workers.length; i++) {
                final ChunkCacheOFFixture.CountingCache source = fixture.source();
                source.revision = i;
                final ChunkCacheOF cache = new ChunkCacheOF(source, new BlockPos(-1, -1, -1), new BlockPos(16, 16, 16), 1);
                workers[i] = new Thread(() -> {
                    try {
                        start.await();
                        BlockPos pos = new BlockPos(8, 9, 10);
                        for (int build = 0; build < 100; build++) {
                            cache.renderStart();
                            try {
                                for (int query = 0; query < 100; query++) {
                                    assertSame(source.expectedState(pos), cache.getBlockState(pos));
                                    assertEquals(source.expectedLight(pos, 0), cache.getCombinedLight(pos, 0));
                                }
                            } finally { cache.renderFinish(); }
                            source.revision++;
                        }
                    } catch (Throwable error) { failure.compareAndSet(null, error); }
                }, "chunk-cache-test-" + i);
                workers[i].start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join(10000);
                assertFalse("cache workers must finish", worker.isAlive());
            }
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }

    private static void verifyRegion(ChunkCacheOF cache, ChunkCacheOFFixture.CountingCache source,
                                     BlockPos from, BlockPos to) throws Exception {
        int volume = (to.getX() - from.getX() + 1) * (to.getY() - from.getY() + 1) * (to.getZ() - from.getZ() + 1);
        cache.renderStart();
        try {
            for (int pass = 0; pass < 2; pass++) {
                for (int x = from.getX(); x <= to.getX(); x++)
                    for (int y = from.getY(); y <= to.getY(); y++)
                        for (int z = from.getZ(); z <= to.getZ(); z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            assertSame(source.expectedState(pos), cache.getBlockState(pos));
                            assertEquals(source.expectedLight(pos, 0), cache.getCombinedLight(pos, 0));
                        }
                assertEquals("one source read per cached coordinate", volume, source.stateReads);
                assertEquals(volume, source.lightReads);
            }
            BlockPos[] outside = {from.add(-1, 0, 0), from.add(0, -1, 0), from.add(0, 0, -1),
                    to.add(1, 0, 0), to.add(0, 1, 0), to.add(0, 0, 1)};
            for (BlockPos pos : outside) for (int repeat = 0; repeat < 2; repeat++) {
                assertSame(source.expectedState(pos), cache.getBlockState(pos));
                assertEquals(source.expectedLight(pos, 0), cache.getCombinedLight(pos, 0));
            }
            assertEquals("out-of-halo queries use the unchanged fallback", volume + 12, source.stateReads);
            assertEquals(volume + 12, source.lightReads);
        } finally { cache.renderFinish(); }
    }
}
