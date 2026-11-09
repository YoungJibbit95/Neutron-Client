package me.youngjibbit.module.impl.render;

import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure appearance selection/viewport contracts; native shader, skin and GL are NOT_RUN. */
public class TargetHudNeutronTwoModeTest {
    @Test public void newSkinIsAppendedAndNeverMovesSevenOriginalSelections() {
        TargetHUD hud=new TargetHUD();
        ComboValue mode=hud.getModeSetting("Mode");
        String[] legacy={"Neutron Default","Neutron White","Neutron Outline",
                "Rise","Tenacity","Basic","Test"};
        assertArrayEquals(new String[]{"Neutron Default","Neutron White","Neutron Outline",
                "Rise","Tenacity","Basic","Test","Neutron 2.0","Modern","Creative"},mode.getOptions());
        for(int i=0;i<legacy.length;i++){
            assertEquals(legacy[i],mode.getOptions()[i]);
            mode.setValue(legacy[i]);
            assertEquals(i,mode.getSelectedIndex());
        }
        mode.setValue("Neutron 2.0");
        assertEquals(7,mode.getSelectedIndex());
        mode.setValue("Modern");assertEquals(8,mode.getSelectedIndex());
        mode.setValue("Creative");assertEquals(9,mode.getSelectedIndex());
        assertEquals(5000d,hud.gradientSpeed.getValue(),0);
        hud.resetPosition();
        assertEquals(0d,hud.getNumberSetting("Position X").getValue(),0);
        assertEquals(0d,hud.getNumberSetting("Position Y").getValue(),0);
    }

    @Test public void optionalGlobalStyleNeverRewritesStoredDefaultOrOtherDesigns() {
        TargetHUD hud=new TargetHUD();
        String persisted=hud.getModeSetting("Mode").getDefaultValue();
        assertEquals("Neutron White",persisted);
        assertEquals("Neutron White",TargetHUD.effectiveDesign(persisted,false));
        assertEquals("Neutron 2.0",TargetHUD.effectiveDesign(persisted,true));
        for(String name:new String[]{"Neutron Default","Neutron Outline","Rise","Tenacity","Basic","Test"}){
            assertEquals(name,TargetHUD.effectiveDesign(name,true));
        }
        assertEquals("Neutron White",hud.getModeSetting("Mode").getValue());
        assertEquals(1,hud.getModeSetting("Mode").getSelectedIndex());
        assertEquals(20,Theme.values().length);
        assertEquals(ThemeMode.LEGACY,"Neutron Legacy");
    }

    @Test public void modernViewportGeometryIsBoundedAcrossSmallAndWideScreens(){
        for(int[] screen:new int[][]{{1,1},{40,20},{320,240},{640,360},{854,480},{1280,720}}){
            float w=TargetHUD.modernWidth(screen[0],270),h=TargetHUD.modernHeight(screen[1]);
            assertTrue(Float.isFinite(w)&&Float.isFinite(h));
            assertTrue(w>=1&&w<=Math.max(1,screen[0]-8));
            assertTrue(h>=1&&h<=Math.max(1,screen[1]-8));
        }
        assertEquals(270f,TargetHUD.modernWidth(1280,270),0);
        assertEquals(64f,TargetHUD.modernHeight(720),0);
        assertEquals(240f,TargetHUD.modernWidth(640,100),0);
        assertEquals(310f,TargetHUD.modernWidth(1280,900),0);
    }

    @Test public void approachTwoPaletteIsStableAndNoLegacyPresetIsMutated(){
        Theme modern=Theme.approachTwo();
        assertEquals(0xfff71ec0,modern.getPrimaryGradientRGB());
        assertEquals(0xffff922f,modern.getSecondaryGradientRGB());
        assertSame(Theme.Neutron,Theme.values()[19]);
        assertEquals(20,Theme.values().length);
    }
}
