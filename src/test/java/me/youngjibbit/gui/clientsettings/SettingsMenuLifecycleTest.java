package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.gui.UiSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.Value;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.*;

/** Headless close/reopen lifecycle: no GL context or Minecraft window is required. */
public class SettingsMenuLifecycleTest {
    private static final class Screen extends SettingsMenu {
        void openAt(int w, int h) {
            width = w;
            height = h;
            initGui();
        }
    }

    @Test public void closingClearsAllThreeMotionCachesAndTransientInput() throws Exception {
        ClientSettings values = new ClientSettings();
        Screen screen = new Screen();
        screen.openAt(854, 480);
        Map<Value<?>, UiSpring> rows = field(screen, "valueMotion");
        Map<String, UiSpring> categories = field(screen, "categoryMotion");
        Map<Value<?>, HudSpring> controls = field(screen, "controlSprings");

        rows.put(values.roundness, new UiSpring());
        categories.put("Appearance", new UiSpring());
        assertEquals(0f, follow(screen, values.roundness, 0f, 0f), 0f);
        follow(screen, values.roundness, 1f, .025f);
        assertEquals(1, controls.size());
        assertEquals(1, rows.size());
        assertEquals(1, categories.size());

        UiSpring[] actions = field(screen, "actionMotion");
        actions[0].step(.03f, true, false, false, true, null);
        assertTrue(actions[0].amount() > 0);
        set(screen, "pressed", 2);
        set(screen, "editing", 0);
        set(screen, "drag", 1);
        set(screen, "editValue", values.roundness);
        set(screen, "dragValue", values.roundness);
        set(screen, "popupValue", values.surface);
        double savedValue = values.roundness.getValue();

        screen.onGuiClosed();
        assertTrue(rows.isEmpty());
        assertTrue(categories.isEmpty());
        assertTrue(controls.isEmpty());
        for (UiSpring spring : actions) assertEquals(0f, spring.amount(), 0f);
        assertEquals(-1, (int) field(screen, "pressed"));
        assertEquals(-1, (int) field(screen, "editing"));
        assertEquals(-1, (int) field(screen, "drag"));
        assertNull(field(screen, "editValue"));
        assertNull(field(screen, "dragValue"));
        assertNull(field(screen, "popupValue"));
        assertEquals(savedValue, values.roundness.getValue(), 0);

        // A second close is safe and cannot reintroduce old animation data.
        screen.onGuiClosed();
        assertTrue(controls.isEmpty());
        assertTrue(rows.isEmpty());
        assertTrue(categories.isEmpty());
    }

    @Test public void reopenedScreenHasNoSpringTailOrStaleValueState() throws Exception {
        ClientSettings values = new ClientSettings();
        Screen screen = new Screen();
        screen.openAt(640, 360);
        follow(screen, values.roundness, 0, 0);
        follow(screen, values.roundness, 1, .03f);

        Map<Value<?>, HudSpring> controls = field(screen, "controlSprings");
        HudSpring oldSpring = controls.get(values.roundness);
        assertNotNull(oldSpring);
        values.roundness.setValue(9d);
        screen.onGuiClosed();

        screen.openAt(640, 360);
        assertTrue(controls.isEmpty());
        assertTrue(((Map<?, ?>) field(screen, "valueMotion")).isEmpty());
        assertTrue(((Map<?, ?>) field(screen, "categoryMotion")).isEmpty());
        // The first frame of a reopened screen is the actual setting value, not
        // the position or velocity left by the previous, closed screen.
        assertEquals(1f, follow(screen, values.roundness, 1, 0), 0);
        assertNotSame(oldSpring, controls.get(values.roundness));
        assertEquals(9d, values.roundness.getValue(), 0);
        screen.onGuiClosed();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(SettingsMenu screen, String name) throws Exception {
        Field f = SettingsMenu.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(screen);
    }

    private static void set(SettingsMenu screen, String name, Object value) throws Exception {
        Field f = SettingsMenu.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(screen, value);
    }

    private static float follow(SettingsMenu screen, Value<?> value, float target, float dt)
            throws Exception {
        Method m = SettingsMenu.class.getDeclaredMethod("follow",
                Value.class, float.class, float.class);
        m.setAccessible(true);
        return (Float) m.invoke(screen, value, target, dt);
    }
}
