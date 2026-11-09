package me.youngjibbit.module.impl.render;

import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** Source-wired modern materials, truthful palette and unchanged functional equipment modes. */
public class Wave07ModernHudCardsTest {
    private final ThemeCatalog oldCatalog=ThemeCatalog.global();
    private final String oldTheme=ThemeMode.selected();
    @After public void restore(){
        ThemeCatalog.install(oldCatalog);ThemeMode.select(oldTheme);
    }

    @Test public void targetModernUsesActualSelectedThemeNotForcedPink()throws Exception {
        ThemeCatalog isolated=new ThemeCatalog(null);
        ThemeCatalog.install(isolated);
        Method theme=TargetHUD.class.getDeclaredMethod("targetTheme",String.class);
        theme.setAccessible(true);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        for(Theme t:Theme.values()){
            isolated.select(t.name());
            assertSame("Palette must be independent from Design: "+t.name(),t,
                    theme.invoke(null,"Neutron 2.0"));
        }
        assertEquals(20,Theme.values().length);
        assertEquals(0xfff71ec0,Theme.approachTwo().getPrimaryGradientRGB());
        isolated.select("Purple");
        assertSame(Theme.Purple,theme.invoke(null,"Neutron 2.0"));
    }

    @Test public void allEightSavedTargetDesignsAndDefaultRemainValid() {
        TargetHUD hud=new TargetHUD();
        String[] expected={"Neutron Default","Neutron White","Neutron Outline",
                "Rise","Tenacity","Basic","Test","Neutron 2.0","Modern","Creative"};
        assertArrayEquals(expected,hud.getModeSetting("Mode").getOptions());
        assertEquals("Neutron White",hud.getModeSetting("Mode").getValue());
        assertEquals(1,hud.getModeSetting("Mode").getSelectedIndex());
        for(int i=0;i<expected.length;i++){
            hud.getModeSetting("Mode").setValue(expected[i]);
            assertEquals(i,hud.getModeSetting("Mode").getSelectedIndex());
        }
        assertEquals("Neutron 2.0",TargetHUD.effectiveDesign("Neutron White",true));
        assertEquals("Neutron White",TargetHUD.effectiveDesign("Neutron White",false));
    }

    @Test public void armorLayoutAndDurabilityAreFunctionalNotCosmeticModes(){
        ArmorHUD hud=new ArmorHUD();
        assertArrayEquals(new String[]{"Vertical","Horizontal"},
                hud.getModeSetting("Layout").getOptions());
        assertArrayEquals(new String[]{"Percent","Value","Both","None"},
                hud.getModeSetting("Durability").getOptions());
        assertEquals("Vertical",hud.getModeSetting("Layout").getValue());
        assertEquals("Percent",hud.getModeSetting("Durability").getValue());
        for(String layout:new String[]{"Vertical","Horizontal"})
            hud.getModeSetting("Layout").setValue(layout);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertEquals("Horizontal",hud.getModeSetting("Layout").getValue());
        hud.getModeSetting("Durability").setValue("None");
        assertEquals("None",hud.getModeSetting("Durability").getValue());
        assertTrue(hud.getBooleanSetting("Held Item").getValue());
        assertTrue(hud.getBooleanSetting("Empty Slots").getValue());
        assertEquals(1,hud.getNumberSetting("Scale").getValue(),0);
    }

    @Test public void armorSemanticDurabilityStillDescribesActualRemainingRatio(){
        assertNotEquals(ArmorHUD.durabilityColor(Theme.Flare,0),
                ArmorHUD.durabilityColor(Theme.Flare,1));
        assertEquals(ArmorHUD.durabilityColor(Theme.SynthWave,0),
                ArmorHUD.durabilityColor(Theme.SynthWave,-1));
        assertEquals(ArmorHUD.durabilityColor(Theme.SynthWave,1),
                ArmorHUD.durabilityColor(Theme.SynthWave,2));
    }

    @Test public void keystrokesReusesSevenRealKeySpringsAndLegacyLabels()throws Exception {
        Keystrokes keys=new Keystrokes();
        Field f=Keystrokes.class.getDeclaredField("modernPresses");
        f.setAccessible(true);
        HudSpring[] springs=(HudSpring[])f.get(keys);
        assertEquals(7,springs.length);
        for(HudSpring spring:springs)assertNotNull(spring);
        assertEquals("LMB",Keystrokes.keyLabel(-100));
        assertEquals("RMB",Keystrokes.keyLabel(-99));
        assertTrue(keys.getBooleanSetting("Mouse Buttons").getValue());
        assertEquals(16,keys.getNumberSetting("Position X").getValue(),0);
        assertEquals(80,keys.getNumberSetting("Position Y").getValue(),0);
        float first=springs[0].snap(0);
        assertEquals(0,first,0);
        assertTrue(springs[0].step(1,.016f,true,false,"Expressive")>0);
        assertEquals(springs[0].value(),springs[0].step(0,0,true,false,"Expressive"),0);
    }
    @Test public void creativeKeystrokesNeverGrowBeyondTheOriginalCells() {
        for(float press:new float[]{-1,0,.5f,1,1.5f,10}) {
            float scale=Keystrokes.creativeScale(press);
            assertTrue("press animation remains inside each key",scale>0&&scale<=1);
        }
        assertEquals(1f,Keystrokes.creativeScale(0),0);
        assertTrue(Keystrokes.creativeScale(1)<1f);
    }

}
