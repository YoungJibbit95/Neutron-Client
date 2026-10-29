package me.youngjibbit.gui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.UiEffects;
import org.junit.*;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import static org.junit.Assert.*;

/** Actual GuiScreen handlers with routes intercepted; no world, accounts, display or GL. */
public class MainMenuInteractionTest {
    private Field managerField;
    private ModuleManager previous;
    private ClientSettings settings;
    private Screen screen;

    static class Screen extends MainMenu {
        int calls, last = -1;
        boolean shift;
        @Override protected boolean shiftDown() { return shift; }
        @Override protected void activate(int id) throws java.io.IOException {
            calls++; last = id;
            if (id >= 5) super.activate(id);
        }
        void down(int id, int button) throws Exception {
            ClipRect r = actionBounds(id);
            mouseClicked((int)(r.x + r.width/2), (int)(r.y + r.height/2), button);
        }
        void up(int id, int button) {
            ClipRect r = actionBounds(id);
            mouseReleased((int)(r.x + r.width/2), (int)(r.y + r.height/2), button);
        }
        void key(int code) throws Exception { keyTyped(' ', code); }
        void resize(int w, int h) { width = w; height = h; initGui(); }
    }

    @Before public void setup() throws Exception {
        managerField = Pulsar.class.getDeclaredField("mm"); managerField.setAccessible(true);
        previous = Pulsar.INSTANCE.getMm();
        Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        ModuleManager manager = (ModuleManager)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(ModuleManager.class);
        settings = new ClientSettings();
        Field index = ModuleManager.class.getDeclaredField("modulesByClass"); index.setAccessible(true);
        LinkedHashMap<Class<?>, Object> modules = new LinkedHashMap<>(); modules.put(ClientSettings.class, settings);
        index.set(manager, modules); managerField.set(Pulsar.INSTANCE, manager); UiEffects.invalidate();
        screen = new Screen(); screen.resize(480, 320);
    }
    @After public void restore() throws Exception { managerField.set(Pulsar.INSTANCE, previous); UiEffects.invalidate(); }

    @Test public void leftPressReleaseRoutesAllEightIdsOnce() throws Exception {
        for (int id = 0; id < 8; id++) {
            int before = screen.calls;
            screen.down(id, 1); screen.up(id, 1); assertEquals(before, screen.calls);
            screen.down(id, 0); assertEquals(before, screen.calls);
            screen.up(id, 0); screen.up(id, 0);
            assertEquals(before + 1, screen.calls); assertEquals(id, screen.last);
        }
    }
    @Test public void outsideAndDifferentActionReleaseCancel() throws Exception {
        screen.down(0, 0); screen.mouseReleased(-1, -1, 0); screen.up(0, 0);
        screen.down(0, 0); screen.up(1, 0); screen.up(0, 0); assertEquals(0, screen.calls);
    }
    @Test public void escapeCancelsCaptureBeforeRelease() throws Exception {
        screen.down(2, 0); screen.key(Keyboard.KEY_ESCAPE); screen.up(2, 0);
        assertEquals(0, screen.calls); assertEquals(0, screen.focusedAction());
    }
    @Test public void keyboardActionCancelsStaleMouseCapture() throws Exception {
        screen.down(0, 0); screen.key(Keyboard.KEY_DOWN); screen.key(Keyboard.KEY_SPACE); screen.up(0, 0);
        assertEquals(1, screen.calls); assertEquals(1, screen.last);
    }
    @Test public void resizeCloseAndReopenCancelCapture() throws Exception {
        screen.down(0, 0); screen.resize(320, 240); screen.up(0, 0); assertEquals(0, screen.calls);
        screen.down(0, 0); screen.onGuiClosed(); screen.up(0, 0); assertEquals(0, screen.calls);
        screen.resize(480, 320); screen.down(0, 0); screen.up(0, 0); assertEquals(1, screen.calls);
    }
    @Test public void keyboardWrapsAndActivatesWithoutMouse() throws Exception {
        screen.key(Keyboard.KEY_UP); assertEquals(7, screen.focusedAction());
        screen.key(Keyboard.KEY_RIGHT); assertEquals(0, screen.focusedAction());
        for(int id = 0; id < 8; id++) {
            assertEquals(id, screen.focusedAction()); screen.key(Keyboard.KEY_RETURN);
            assertEquals(id, screen.last); screen.key(Keyboard.KEY_TAB);
        }
        assertEquals(0, screen.focusedAction()); assertEquals(8, screen.calls);
    }
    @Test public void roundedCornersAndExclusiveEdgesDoNotActivate() throws Exception {
        ClipRect r = screen.actionBounds(0);
        screen.mouseClicked((int)Math.ceil(r.x), (int)Math.ceil(r.y), 0); screen.up(0, 0);
        screen.mouseClicked((int)Math.ceil(r.x+r.width), (int)(r.y+r.height/2), 0); screen.up(0, 0);
        assertEquals(0, screen.calls);
    }
    @Test public void boundsStayVisibleAndDisjointAtSmallOddSizesAndGuiScales() throws Exception {
        for(int[] size : new int[][]{{480,320},{320,240},{367,245},{320,180},{1920,1080}}) {
            screen.resize(size[0], size[1]);
            for(int id=0; id<8; id++) {
                ClipRect r=screen.actionBounds(id);
                assertTrue(r.width>0 && r.height>0 && r.x>=0 && r.y>=0);
                assertTrue(r.x+r.width<=screen.width && r.y+r.height<=screen.height);
                for(int other=0; other<id; other++) assertTrue(r.intersect(screen.actionBounds(other)).isEmpty());
                screen.down(id,0); screen.up(id,0); assertEquals(id,screen.last);
            }
        }
    }
    @Test public void utilityTogglesChangeOnlyTheirExistingValue() throws Exception {
        settings.reducedMotion.setValue(true);
        screen.down(5,0); screen.up(5,0);
        assertFalse(settings.animations.getValue()); assertTrue(settings.reducedMotion.getValue());
        assertTrue(settings.blur.getValue()); assertTrue(settings.glow.getValue());
        screen.down(6,0); screen.up(6,0);
        assertFalse(settings.blur.getValue()); assertTrue(settings.glow.getValue());
        screen.down(7,0); screen.up(7,0);
        assertFalse(settings.glow.getValue()); assertTrue(settings.reducedMotion.getValue());
    }
    @Test public void shiftTabWrapsBackwards() throws Exception {
        screen.shift=true;screen.key(Keyboard.KEY_TAB);assertEquals(7,screen.focusedAction());
        screen.key(Keyboard.KEY_TAB);assertEquals(6,screen.focusedAction());
    }
    @Test public void actualConsumerUsesPresetAndKeepsHitBoundsStableDuringMotion() throws Exception {
        ClipRect base=screen.actionBounds(1);float previousLift=0,previousScale=1,previousGlow=0;
        for(String name:new String[]{"Calm","Balanced","Expressive"}) {
            settings.motionIntensity.setValue(name);screen.resize(480,320);
            for(int frame=0;frame<60;frame++)screen.advanceMotion(1f/60,(int)(base.x+base.width/2),(int)(base.y+base.height/2));
            MenuMotion preset=MenuMotion.resolve(settings);float response=amounts()[1];
            assertTrue(preset.lift(response)>previousLift);assertTrue(preset.scale(response)>previousScale);assertTrue(preset.glow(response)>previousGlow);
            previousLift=preset.lift(response);previousScale=preset.scale(response);previousGlow=preset.glow(response);
            ClipRect animated=screen.actionBounds(1);assertEquals(base.x,animated.x,0);assertEquals(base.y,animated.y,0);
            assertEquals(base.width,animated.width,0);assertEquals(base.height,animated.height,0);
            screen.down(1,0);screen.up(1,0);assertEquals(1,screen.last);
        }
    }
    @Test public void consumerSeparatesLinearReducedFromLegacySnapAndResetsOnLifecycle() throws Exception {
        settings.reducedMotion.setValue(true);screen.advanceMotion(.03f,-1,-1);assertEquals(.55*.25,amounts()[0],.00001);
        screen.advanceMotion(.09f,-1,-1);assertEquals(.55,amounts()[0],.00001);
        settings.animations.setValue(false);screen.down(2,0);screen.advanceMotion(0,-1,-1);assertEquals(-.45,amounts()[2],.00001);
        screen.onGuiClosed();for(float value:amounts())assertEquals(0,value,0);
        screen.resize(320,240);for(float value:amounts())assertEquals(0,value,0);
        assertEquals(0,screen.focusedAction());screen.advanceMotion(0,-1,-1);assertEquals(.55,amounts()[0],.00001);
        managerField.set(Pulsar.INSTANCE,null);UiEffects.invalidate();screen.resize(480,320);
        screen.advanceMotion(.03f,-1,-1);
        assertEquals(new MenuSpring().step(.55f,.03f,true),amounts()[0],0);
        assertEquals(MenuMotion.BALANCED,MenuMotion.resolve(ClientSettings.current()));
    }
    private float[] amounts() throws Exception {
        Field field=MainMenu.class.getDeclaredField("hover");field.setAccessible(true);return (float[])field.get(screen);
    }
}
