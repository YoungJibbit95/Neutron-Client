package me.youngjibbit.module.impl.render;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisualModeCompatibilityTest {
    private static void ids(Module owner,String... expected){
        assertArrayEquals(expected,owner.getValues().stream().map(Value::getId).toArray(String[]::new));
    }
    @Test public void cosmeticModesAppendAfterEveryExistingValueRegistration() {
        ids(new ArmorHUD(),"layout","durability","durability_bar","held_item","empty_slots",
                "background","blur","glow","scale","effects_source","local_rectangle_substance",
                "position_x","position_y","mode");
        ids(new Keystrokes(),"position_x","position_y","mouse_buttons","effects_source",
                "local_rectangle_substance","mode");
        ids(new Crosshair(),"length","gap","thickness","dynamic","center_dot","mode");
        ids(new GlowESP(),"kawase_glow","color_mode","players","animals","mobs","chests",
                "radius","iterations","offset","intensity","glow_mode");
    }
    @Test public void completeCosmeticListsRetainDefaultsAndEveryOptionIndex() {
        for(Module owner:new Module[]{new ArmorHUD(),new Keystrokes(),new Crosshair()})
            modes(owner.getModeSetting("Mode"),new String[]{"Basic","Modern","Creative"});
        modes(new GlowESP().getModeSetting("Glow Mode"),new String[]{"Standard","Flame","Neon","Lines"});
        modes(new Nametags().getModeSetting("Mode"),new String[]{"Static","Flame","Neon","Lines"});
    }
    private static void modes(ComboValue mode,String[] expected){
        assertArrayEquals(expected,mode.getOptions());assertEquals(expected[0],mode.getValue());
        assertEquals(expected[0],mode.getDefaultValue());assertEquals(0,mode.getSelectedIndex());
        for(int i=0;i<expected.length;i++){mode.setValue(expected[i]);assertEquals(i,mode.getSelectedIndex());}
    }
    @Test public void creativeCrosshairDoesNotPulseWhenMotionIsDisabledOrReduced() {
        for(float seconds:new float[]{0,.1f,1,10,1000})assertEquals(1,Crosshair.creativePulse(seconds,false),0);
        assertNotEquals(Crosshair.creativePulse(0,true),Crosshair.creativePulse(.5f,true),.001f);
    }
}
