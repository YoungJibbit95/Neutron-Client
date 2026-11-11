package net.optifine.override;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.src.Config;
import net.minecraft.util.BlockPos;
import net.minecraft.world.ChunkCache;

/** CPU-only fixture: no world mutation, GL context or Minecraft bootstrap. */
final class ChunkCacheOFFixture implements AutoCloseable {
    private final Field settingsField;
    private final Object previousSettings;

    ChunkCacheOFFixture() throws Exception {
        settingsField = Config.class.getDeclaredField("gameSettings");
        settingsField.setAccessible(true);
        previousSettings = settingsField.get(null);
        GameSettings settings = bare(GameSettings.class);
        settings.ofDynamicLights = 3;
        settingsField.set(null, settings);
    }

    CountingCache source() throws Exception {
        CountingCache source = bare(CountingCache.class);
        source.states = new IBlockState[3];
        for (int i = 0; i < source.states.length; i++) {
            final int identity = i;
            source.states[i] = (IBlockState) Proxy.newProxyInstance(
                    IBlockState.class.getClassLoader(), new Class<?>[]{IBlockState.class},
                    (proxy, method, args) -> {
                        if ("toString".equals(method.getName())) return "fixture-state-" + identity;
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                        if ("equals".equals(method.getName())) return proxy == args[0];
                        throw new UnsupportedOperationException(method.toString());
                    });
        }
        return source;
    }

    static int value(ChunkCacheOF cache, String name) throws Exception {
        Field field = ChunkCacheOF.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(cache);
    }

    static Object field(ChunkCacheOF cache, String name) throws Exception {
        Field field = ChunkCacheOF.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(cache);
    }

    static <T> T bare(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    @Override public void close() throws Exception {
        settingsField.set(null, previousSettings);
    }

    static final class CountingCache extends ChunkCache {
        IBlockState[] states;
        int stateReads;
        int lightReads;
        int revision;

        private CountingCache() { super(null, BlockPos.ORIGIN, BlockPos.ORIGIN, 0); }

        IBlockState expectedState(BlockPos pos) {
            return states[Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() + revision, 3)];
        }

        int expectedLight(BlockPos pos, int minimum) {
            int blockLight = (pos.getX() * 31 + pos.getY() * 17 + pos.getZ() + revision) & 15;
            return 0x00300000 | Math.max(blockLight, minimum) << 4;
        }

        @Override public IBlockState getBlockState(BlockPos pos) {
            stateReads++;
            return expectedState(pos);
        }

        @Override public int getCombinedLight(BlockPos pos, int minimum) {
            lightReads++;
            return expectedLight(pos, minimum);
        }
    }
}
