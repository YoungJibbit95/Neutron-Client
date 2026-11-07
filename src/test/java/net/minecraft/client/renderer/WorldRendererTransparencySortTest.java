package net.minecraft.client.renderer;

import com.google.common.primitives.Floats;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.junit.Test;

import static org.junit.Assert.*;

public class WorldRendererTransparencySortTest {
    @Test public void primitiveOrderMatchesStableFloatComparator() {
        assertOrder(new float[0]);
        assertOrder(new float[]{-0.0f});
        float[] values = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                0.0f, -0.0f, 1.0f, 1.0f, -1.0f};
        assertOrder(values);
        Random random = new Random(0x4e455554L);
        for (int run = 0; run < 100; run++) {
            float[] sample = new float[256];
            for (int i = 0; i < sample.length; i++) {
                sample[i] = i % 7 == 0 ? values[i % values.length] :
                        Float.intBitsToFloat(random.nextInt());
            }
            assertOrder(sample);
        }
    }

    @Test public void reordersVertexWordsAndSpritesTogether() throws Exception {
        VertexFormat format = DefaultVertexFormats.field_181706_f;
        int stride = format.func_181719_f();
        float[] x = {1, -1, 3, 2, Float.NaN, 0, -0.0f};
        int[] original = new int[x.length * 4 * stride];
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[x.length];
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        for (int q = 0; q < x.length; q++) {
            // Identity is all the sorter needs; a real sprite constructor requires Minecraft settings.
            sprites[q] = (TextureAtlasSprite) unsafe.allocateInstance(TextureAtlasSprite.class);
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = (q * 4 + vertex) * stride;
                original[offset] = Float.floatToRawIntBits(x[q]);
                original[offset + 1] = Float.floatToRawIntBits(0.0f);
                original[offset + 2] = Float.floatToRawIntBits(0.0f);
                original[offset + 3] = 0x12340000 | q;
            }
        }
        float[] distance = new float[x.length];
        for (int q = 0; q < x.length; q++) {
            float dx = x[q] - 1.25f;
            distance[q] = dx * dx + 2.5f * 2.5f + 0.75f * 0.75f;
        }
        Integer[] reference = referenceOrder(distance);
        int[] expected = new int[original.length];
        for (int target = 0; target < reference.length; target++) {
            System.arraycopy(original, reference[target] * 4 * stride,
                    expected, target * 4 * stride, 4 * stride);
        }

        WorldRenderer renderer = new WorldRenderer(original.length + 64);
        renderer.setVertexState(renderer.new State(original, format, sprites));
        renderer.func_181674_a(1.25f, -2.5f, 0.75f);
        assertArrayEquals(expected, renderer.func_181672_a().getRawBuffer());
        Field field = WorldRenderer.class.getDeclaredField("quadSprites");
        field.setAccessible(true);
        TextureAtlasSprite[] reordered = (TextureAtlasSprite[]) field.get(renderer);
        for (int target = 0; target < reference.length; target++) {
            assertSame(sprites[reference[target]], reordered[target]);
        }
    }

    @Test public void alreadyOrderedQuadsPreserveBufferAndSprites() throws Exception {
        VertexFormat format = DefaultVertexFormats.field_181706_f;
        int stride = format.func_181719_f();
        Field spriteField = WorldRenderer.class.getDeclaredField("quadSprites");
        spriteField.setAccessible(true);
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        for (int count : new int[]{0, 1, 4}) for (boolean withSprites : new boolean[]{false, true}) {
            int[] raw = new int[count * 4 * stride];
            TextureAtlasSprite[] sprites = withSprites ? new TextureAtlasSprite[count] : null;
            for (int q = 0; q < count; q++) {
                if (withSprites) sprites[q] = (TextureAtlasSprite) unsafe.allocateInstance(TextureAtlasSprite.class);
                float x = q < 2 ? 4.0f - q : 2.0f; // final two quads have a stable distance tie
                for (int v = 0; v < 4; v++) {
                    int offset = (q * 4 + v) * stride;
                    raw[offset] = Float.floatToRawIntBits(x);
                    raw[offset + 1] = 0;
                    raw[offset + 2] = 0;
                    raw[offset + 3] = 0x12340000 | q;
                }
            }
            WorldRenderer renderer = new WorldRenderer(raw.length + 64);
            renderer.setVertexState(renderer.new State(raw, format, sprites));
            renderer.func_181674_a(0, 0, 0);
            assertEquals(raw.length, renderer.rawIntBuffer.position());
            assertEquals(renderer.rawIntBuffer.capacity(), renderer.rawIntBuffer.limit());
            assertArrayEquals(raw, renderer.func_181672_a().getRawBuffer());
            TextureAtlasSprite[] actual = (TextureAtlasSprite[]) spriteField.get(renderer);
            if (!withSprites) assertNull(actual);
            else for (int q = 0; q < count; q++) assertSame(sprites[q], actual[q]);
        }
    }

    private static void assertOrder(float[] values) {
        Integer[] expected = referenceOrder(values);
        long[] actual = new long[values.length];
        for (int i = 0; i < values.length; i++)
            actual[i] = WorldRenderer.quadSortKey(values[i], i);
        Arrays.sort(actual);
        for (int i = 0; i < values.length; i++)
            assertEquals("index " + i, expected[i].intValue(), (int) actual[i]);
    }

    private static Integer[] referenceOrder(final float[] values) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                return Floats.compare(values[b], values[a]);
            }
        });
        return order;
    }
}
