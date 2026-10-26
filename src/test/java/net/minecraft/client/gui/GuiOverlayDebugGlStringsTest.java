package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import static org.junit.Assert.*;

public class GuiOverlayDebugGlStringsTest
{
    @Test
    public void realOffscreenContextRecreationRefreshesDriverStrings() throws Exception
    {
        org.junit.Assume.assumeTrue("Opt-in native offscreen check", Boolean.getBoolean("pulsar.test.glContext"));
        assertFalse(org.lwjgl.opengl.Display.isCreated());
        GuiOverlayDebug.GlStringsCache cache = new GuiOverlayDebug.GlStringsCache();
        Object previousCapabilities = null;
        int[] calls = {0};
        for (int generation = 0; generation < 2; generation++)
        {
            org.lwjgl.opengl.Pbuffer buffer = new org.lwjgl.opengl.Pbuffer(16, 16,
                    new org.lwjgl.opengl.PixelFormat(), null, null);
            try
            {
                buffer.makeCurrent();
                Object capabilities = org.lwjgl.opengl.GLContext.getCapabilities();
                assertNotSame(previousCapabilities, capabilities);
                IntFunction<String> query = name -> { calls[0]++; return GL11.glGetString(name); };
                String[] cached = cache.get(capabilities, query);
                assertArrayEquals(new String[] {GL11.glGetString(GL11.GL_VENDOR),
                        GL11.glGetString(GL11.GL_RENDERER), GL11.glGetString(GL11.GL_VERSION)}, cached);
                assertSame(cached, cache.get(capabilities, query));
                assertEquals((generation + 1) * 3, calls[0]);
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
                previousCapabilities = capabilities;
            }
            finally { buffer.destroy(); }
        }
        assertFalse(org.lwjgl.opengl.Display.isCreated());
    }

    @Test
    public void stableContextQueriesEachDriverStringOnceAcrossFrames()
    {
        GuiOverlayDebug.GlStringsCache cache = new GuiOverlayDebug.GlStringsCache();
        Object context = new Object();
        List<Integer> queries = new ArrayList<>();
        IntFunction<String> driver = name -> { queries.add(name); return "driver-" + name; };
        String[] first = cache.get(context, driver);
        for (int frame = 0; frame < 1000; frame++)
        {
            assertSame(first, cache.get(context, driver));
            assertArrayEquals(new String[] {"driver-" + GL11.GL_VENDOR,
                    "driver-" + GL11.GL_RENDERER, "driver-" + GL11.GL_VERSION}, first);
        }
        assertEquals(Arrays.asList(GL11.GL_VENDOR, GL11.GL_RENDERER, GL11.GL_VERSION), queries);
    }

    @Test
    public void contextIdentityChangesRefreshAllStringsIncludingReturnToEarlierContext()
    {
        GuiOverlayDebug.GlStringsCache cache = new GuiOverlayDebug.GlStringsCache();
        Object first = new String("equal");
        Object second = new String("equal");
        assertEquals(first, second);
        assertArrayEquals(new String[] {"A", "A", "A"}, cache.get(first, name -> "A"));
        assertArrayEquals(new String[] {"B", "B", "B"}, cache.get(second, name -> "B"));
        assertArrayEquals(new String[] {"C", "C", "C"}, cache.get(first, name -> "C"));
    }

    @Test
    public void nullDriverResultsRemainNullAndAreRetried()
    {
        GuiOverlayDebug.GlStringsCache cache = new GuiOverlayDebug.GlStringsCache();
        Object context = new Object();
        String[] strings = cache.get(context, name -> null);
        assertArrayEquals(new String[] {null, null, null}, strings);
        assertArrayEquals(new String[] {"recovered", "recovered", "recovered"},
                cache.get(context, name -> "recovered"));
    }

    @Test
    public void failedRefreshDoesNotPublishPartialDataOrReplacePreviousContext()
    {
        GuiOverlayDebug.GlStringsCache cache = new GuiOverlayDebug.GlStringsCache();
        Object first = new Object();
        Object second = new Object();
        String[] previous = cache.get(first, name -> "old");
        try
        {
            cache.get(second, name -> {
                if (name == GL11.GL_RENDERER) throw new IllegalStateException("query failed");
                return "partial";
            });
            fail("Query failure must propagate");
        }
        catch (IllegalStateException expected) { }
        assertSame(previous, cache.get(first, name -> { throw new AssertionError("Previous cache lost"); }));
        assertArrayEquals(new String[] {"new", "new", "new"}, cache.get(second, name -> "new"));
    }
}
