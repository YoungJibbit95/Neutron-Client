package me.youngjibbit.utils.render;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import org.junit.Test;
import sun.misc.Unsafe;
import static org.junit.Assert.*;

public class GuiResolutionTest {
    private static final class Client extends Minecraft {
        boolean unicode;
        private Client() { super(null); }
        @Override public boolean isUnicode() { return unicode; }
    }

    private static Client client() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe)field.get(null);
        Client client = (Client)unsafe.allocateInstance(Client.class);
        client.gameSettings = (GameSettings)unsafe.allocateInstance(GameSettings.class);
        return client;
    }

    @Test public void reusesOnlyAnUnchangedClientConfiguration() throws Exception {
        Client client = client();
        client.displayWidth = 1920; client.displayHeight = 1080; client.gameSettings.guiScale = 3;
        ScaledResolution first = GuiResolution.current(client);
        assertSame(first, GuiResolution.current(client));
        client.unicode = true;
        ScaledResolution unicode = GuiResolution.current(client);
        assertNotSame(first, unicode);
        assertEquals(2, unicode.getScaleFactor());
        client.gameSettings.guiScale = 1;
        ScaledResolution scale = GuiResolution.current(client);
        assertNotSame(unicode, scale);
        assertEquals(1, scale.getScaleFactor());
        client.displayWidth--;
        ScaledResolution width = GuiResolution.current(client);
        assertNotSame(scale, width);
        client.displayHeight--;
        assertNotSame(width, GuiResolution.current(client));
        Client replacement = client();
        replacement.displayWidth = client.displayWidth; replacement.displayHeight = client.displayHeight;
        replacement.unicode = true; replacement.gameSettings.guiScale = 1;
        assertNotSame(GuiResolution.current(client), GuiResolution.current(replacement));
    }

    @Test public void matchesMinecraftForAutoScaleOddSizesAndUnicode() throws Exception {
        Client client = client();
        for (int[] size : new int[][]{{320,240},{640,480},{1001,701},{1920,1080},{7680,4320}}) {
            for (int scale : new int[]{0,1,2,3,4,8}) for (boolean unicode : new boolean[]{false,true}) {
                client.displayWidth = size[0]; client.displayHeight = size[1];
                client.gameSettings.guiScale = scale; client.unicode = unicode;
                ScaledResolution expected = new ScaledResolution(client), actual = GuiResolution.current(client);
                assertEquals(expected.getScaleFactor(), actual.getScaleFactor());
                assertEquals(expected.getScaledWidth(), actual.getScaledWidth());
                assertEquals(expected.getScaledHeight(), actual.getScaledHeight());
                assertEquals(expected.getScaledWidth_double(), actual.getScaledWidth_double(), 0);
                assertEquals(expected.getScaledHeight_double(), actual.getScaledHeight_double(), 0);
                assertSame(actual, GuiResolution.current(client));
            }
        }
    }
}
