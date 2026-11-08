package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Actual SettingsMenu input handlers, not decorative animation simulation.
 * No GL context, Minecraft start or synthetic gameplay event is required.
 */
public class SettingsMenuVis03AInteractionTest {
    private static final class Screen extends SettingsMenu {
        void size(int w,int h){width=w;height=h;initGui();}
        void click(ClipRect rect,int button)throws Exception {
            mouseClicked((int)(rect.x+8),(int)(rect.y+12),button);
        }
        void key(int code)throws Exception{keyTyped(' ',code);}
        void dragTo(float x,float y){mouseClickMove((int)x,(int)y,0,20);}
        void release(){mouseReleased(0,0,0);}
        ClipRect field(int i){return controlBounds(i);}
        ClipRect category(int i){return categoryBounds(i);}
    }

    private ModuleManager prior;
    private ClientSettings settings;
    private Screen screen;
    @Before public void setup()throws Exception {
        prior=Pulsar.INSTANCE.getMm();
        settings=new ClientSettings();
        setManager(new ModuleManager(Collections.singletonList(settings)));
        screen=new Screen();screen.size(854,480);
    }
    @After public void tearDown()throws Exception {
        if(screen!=null)screen.onGuiClosed();
        setManager(prior);
    }
    private static void setManager(ModuleManager m)throws Exception{
        Field f=Pulsar.class.getDeclaredField("mm");f.setAccessible(true);f.set(Pulsar.INSTANCE,m);
    }
    @SuppressWarnings("unchecked")
    private <T> T field(String id)throws Exception {
        Field f=SettingsMenu.class.getDeclaredField(id);
        f.setAccessible(true);return (T)f.get(screen);
    }

    @Test public void switchingCategoriesRetargetsPaintOnlyAndDoesNotModifySettings()throws Exception{
        SettingsMenuMotion state=field("presentation");
        float y0=screen.category(0).y+4;
        assertEquals(y0,state.category(y0,0,settings),0);
        double opacity=settings.surfaceOpacity.getValue();

        screen.click(screen.category(3),0);
        assertEquals(3,(int)field("group"));
        float y1=screen.category(3).y+4;
        assertEquals(y0,state.category(y1,0,settings),0);
        float moving=state.category(y1,.03f,settings);
        assertTrue(moving>y0&&moving<y1);
        screen.click(screen.category(0),0);
        assertEquals(0,(int)field("group"));
        assertEquals(moving,state.category(y0,0,settings),0);
        assertEquals(opacity,settings.surfaceOpacity.getValue(),0);
    }

    @Test public void dropdownDirectionReversesButMouseAndKeyboardCommitImmediately()throws Exception{
        settings.surface.setValue("Standard");
        SettingsMenuMotion state=field("presentation");
        screen.click(screen.field(1),0);
        assertSame(settings.surface,field("popupValue"));
        float open=state.popup(.03f,settings);
        assertTrue(open>0&&open<1);

        screen.key(Keyboard.KEY_ESCAPE);
        assertNull(field("popupValue"));
        assertSame(settings.surface,field("closingPopupValue"));
        assertFalse(state.popupOpen());
        assertEquals(open,state.popup(0,settings),0);
        assertEquals("Standard",settings.surface.getValue());

        // Reopening the same combo in the middle of a close retains x/v.
        screen.click(screen.field(1),0);
        assertSame(settings.surface,field("popupValue"));
        assertNull(field("closingPopupValue"));
        assertEquals(open,state.popup(0,settings),0);
        screen.key(Keyboard.KEY_DOWN);
        assertEquals("Standard",settings.surface.getValue());
        screen.key(Keyboard.KEY_RETURN);
        assertNull(field("popupValue"));
        assertEquals("Clear",settings.surface.getValue());

        // Group changes discard the outgoing visual so it cannot repaint old rows.
        screen.click(screen.category(2),0);
        assertNull(field("closingPopupValue"));
        assertFalse(state.popupOpen());
        assertEquals(2,(int)field("group"));
    }

    @Test public void sliderWritesHappenOnlyInClickAndMouseDragHandlers()throws Exception {
        double initial=settings.roundness.getValue();
        ClipRect r=screen.field(0);
        screen.click(r,0);
        assertSame(settings.roundness,field("dragValue"));
        double afterDown=settings.roundness.getValue();
        assertNotEquals(initial,afterDown,0);

        screen.dragTo(r.x+r.width*.82f,r.y+12);
        double afterDrag=settings.roundness.getValue();
        assertNotEquals(afterDown,afterDrag,0);
        assertTrue(afterDrag>=settings.roundness.getMin()&&afterDrag<=settings.roundness.getMax());

        screen.release();
        screen.dragTo(r.x,r.y+12);
        assertEquals(afterDrag,settings.roundness.getValue(),0);
        assertNull(field("dragValue"));
        screen.size(854,480); // GUI reinitialization cannot mutate a stored Value.
        assertEquals(afterDrag,settings.roundness.getValue(),0);
    }

    @Test public void rapidPopupCloseAndReopenReleasesPresentationOnGuiClose()throws Exception {
        SettingsMenuMotion state=field("presentation");
        for(int i=0;i<30;i++) {
            screen.click(screen.field(1),0);
            assertSame(settings.surface,field("popupValue"));
            state.popup(.006f,settings);
            screen.key(Keyboard.KEY_ESCAPE);
            assertNull(field("popupValue"));
            state.popup(.006f,settings);
        }
        assertSame(settings.surface,field("closingPopupValue"));
        double stable=settings.roundness.getValue();
        screen.onGuiClosed();
        assertNull(field("closingPopupValue"));
        assertNull(field("closingPopupBounds"));
        assertFalse(state.popupOpen());
        assertEquals(0,state.popup(0,settings),0);
        assertEquals(stable,settings.roundness.getValue(),0);
        assertTrue(((Map<?,?>)field("controlSprings")).isEmpty());
        screen.size(854,480);
        assertNull(field("closingPopupValue"));
        assertEquals(stable,settings.roundness.getValue(),0);
    }

    @Test public void disabledAnimationsKeepPopupReadableAndSelectionsOperational()throws Exception {
        settings.animations.setValue(false);
        SettingsMenuMotion state=field("presentation");
        screen.click(screen.field(1),0);
        assertEquals(1,state.popup(0,settings),0);
        screen.key(Keyboard.KEY_ESCAPE);
        assertEquals(0,state.popup(0,settings),0);
        assertNull(field("popupValue"));
        settings.animations.setValue(true);
        settings.reducedMotion.setValue(true);
        screen.click(screen.field(1),0);
        assertEquals(1,state.popup(0,settings),0);
        assertTrue(settings.reducedMotion.getValue());
        screen.key(Keyboard.KEY_ESCAPE);
        assertEquals(0,state.popup(0,settings),0);
    }
}
