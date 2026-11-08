package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.gui.HudReveal;
import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Test;

import static org.junit.Assert.*;

/** Pure presentation math; no GL context, input simulation or Minecraft launch. */
public class SettingsMenuMotionTest {
    @Test public void categoryRetargetKeepsPositionAndIsFrameRateIndependent() {
        ClientSettings settings = new ClientSettings();
        float reference = Float.NaN;
        for(int fps : new int[]{30,60,144,240}) {
            SettingsMenuMotion state = new SettingsMenuMotion();
            state.reset();state.category(35,0,settings);
            float result=0;
            for(int i=0;i<fps;i++)result=state.category(160,1f/fps,settings);
            if(Float.isFinite(reference))assertEquals(reference,result,.002f);
            reference=result;
        }
        assertEquals(160,reference,.01f);

        SettingsMenuMotion motion=new SettingsMenuMotion();
        motion.category(20,0,settings);
        float middle=motion.category(180,.038f,settings);
        assertTrue(middle>20&&middle<180);
        assertEquals(middle,motion.category(30,0,settings),0);
        float reversed=motion.category(30,.025f,settings);
        assertTrue(Float.isFinite(reversed));
        for(int i=0;i<30;i++) {
            reversed=motion.category(i%2==0?200:20,.008f,settings);
            assertTrue(Float.isFinite(reversed));
        }
    }

    @Test public void dropdownOpenCloseReversesWithoutSnappingOrCapturingInput() {
        ClientSettings settings=new ClientSettings();
        SettingsMenuMotion motion=new SettingsMenuMotion();
        motion.reset();motion.resetCursor(50);
        motion.popup(true);
        float opening=motion.popup(.035f,settings);
        assertTrue(opening>0&&opening<1);
        motion.popup(false);
        assertFalse(motion.popupOpen());
        assertEquals(opening,motion.popup(0,settings),0);
        float closing=motion.popup(.025f,settings);
        assertTrue(Float.isFinite(closing));
        motion.popup(true);
        assertEquals(closing,motion.popup(0,settings),0);
        motion.resetCursor(50);
        float cursor=motion.cursor(80,.02f,settings);
        assertTrue(cursor>50&&cursor<80);
        assertEquals(cursor,motion.cursor(18,0,settings),0);
        motion.resetPopup();
        assertFalse(motion.popupOpen());
        assertEquals(0,motion.popup(0,settings),0);
    }

    @Test public void openCloseScreenAccentIsReversibleAndAlwaysReadableWhenMotionOff() {
        ClientSettings settings=new ClientSettings();
        SettingsMenuMotion motion=new SettingsMenuMotion();
        motion.reset();
        HudReveal.Frame first=motion.screen(true,0,settings);
        assertEquals(0,first.opacity,0);
        float opening=motion.screen(true,.025f,settings).opacity;
        assertTrue(opening>0&&opening<1);
        assertEquals(opening,motion.screen(false,0,settings).opacity,0);
        assertTrue(Float.isFinite(motion.screen(true,.016f,settings).scale));
        settings.reducedMotion.setValue(true);
        assertEquals(0,motion.screen(false,0,settings).opacity,0);
        assertEquals(1,motion.screen(true,0,settings).opacity,0);
        settings.reducedMotion.setValue(false);
        settings.animations.setValue(false);
        assertEquals(0,motion.screen(false,0,settings).opacity,0);
        assertEquals(1,motion.screen(true,0,settings).opacity,0);
    }

    @Test public void animationPreferencesSnapAllPresentationWithoutWritingValues() {
        ClientSettings settings=new ClientSettings();
        settings.animations.setValue(false);
        SettingsMenuMotion motion=new SettingsMenuMotion();
        motion.reset();
        assertEquals(100,motion.category(100,0,settings),0);
        assertEquals(240,motion.category(240,0,settings),0);
        motion.popup(true);
        assertEquals(1,motion.popup(0,settings),0);
        motion.popup(false);
        assertEquals(0,motion.popup(0,settings),0);
        settings.animations.setValue(true);
        settings.reducedMotion.setValue(true);
        motion.popup(true);
        assertEquals(1,motion.popup(0,settings),0);
        assertEquals(105,motion.category(105,0,settings),0);
        assertTrue(settings.reducedMotion.getValue());
        assertEquals("Balanced",settings.motionIntensity.getValue());
    }

    @Test public void resetsDiscardTransientSpringAndPopupState() {
        SettingsMenuMotion motion=new SettingsMenuMotion();
        motion.reset();
        motion.category(8,0,null);
        motion.popup(true);
        motion.popup(.02f,null);
        motion.screen(true,.02f,null);
        motion.reset();
        assertFalse(motion.popupOpen());
        assertEquals(0,motion.popup(0,null),0);
        assertEquals(140,motion.category(140,0,null),0);
        assertEquals(0,motion.screen(true,0,null).opacity,0);
    }
}
