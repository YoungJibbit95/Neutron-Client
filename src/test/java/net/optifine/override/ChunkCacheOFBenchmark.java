package net.optifine.override;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import net.minecraft.util.BlockPos;

/** Isolated production-cache CPU work. Does not measure client frames or chunk visibility. */
public final class ChunkCacheOFBenchmark {
    private static volatile int blackhole;
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("JSON output path required");
        try (ChunkCacheOFFixture fixture = new ChunkCacheOFFixture();
             PrintWriter output = new PrintWriter(args[0], "UTF-8")) {
            ChunkCacheOFFixture.CountingCache source = fixture.source();
            ChunkCacheOF cache = new ChunkCacheOF(source, new BlockPos(-1, -1, -1), new BlockPos(16, 16, 16), 1);
            BlockPos[] positions = new BlockPos[4096];
            for (int i = 0; i < positions.length; i++) positions[i] = new BlockPos(i & 15, i >> 4 & 15, i >> 8 & 15);
            cache.renderStart();
            int lightEntries = ((int[]) ChunkCacheOFFixture.field(cache, "combinedLights")).length;
            int stateEntries = ((Object[]) ChunkCacheOFFixture.field(cache, "blockStates")).length;
            cache.renderFinish();
            output.println("{");
            output.println("  \"scope\": \"isolated production ChunkCacheOF CPU methods, not FPS or packet-to-visible latency\",");
            output.println("  \"java\": \"" + System.getProperty("java.runtime.version") + "\",");
            output.println("  \"processors\": " + Runtime.getRuntime().availableProcessors() + ",");
            output.println("  \"max_heap_bytes\": " + Runtime.getRuntime().maxMemory() + ",");
            output.println("  \"light_entries\": " + lightEntries + ",");
            output.println("  \"state_entries\": " + stateEntries + ",");
            output.println("  \"warmup_iterations\": 2000,");
            output.println("  \"samples\": [");
            for (int scene = 0; scene < 2; scene++) {
                boolean query = scene == 1;
                for (int i = 0; i < 2000; i++) run(cache, positions, query);
                for (int sample = 0; sample < 40; sample++) {
                    long begin = System.nanoTime();
                    for (int iteration = 0; iteration < 100; iteration++) run(cache, positions, query);
                    long elapsed = System.nanoTime() - begin;
                    output.printf(Locale.ROOT,
                            "    {\"scene\":\"%s\",\"sample\":%d,\"iterations\":100,\"ns_per_build\":%.3f}%s%n",
                            query ? "reset_and_4096_queries" : "reset_only", sample, elapsed / 100.0,
                            scene == 1 && sample == 39 ? "" : ",");
                }
            }
            output.println("  ]\n}");
        }
    }

    private static void run(ChunkCacheOF cache, BlockPos[] positions, boolean query) {
        cache.renderStart();
        if (query) {
            int sum = 0;
            for (BlockPos position : positions) {
                sum += cache.getCombinedLight(position, 0);
                if (cache.getBlockState(position) != null) sum++;
            }
            blackhole = sum;
        }
        cache.renderFinish();
    }
}
