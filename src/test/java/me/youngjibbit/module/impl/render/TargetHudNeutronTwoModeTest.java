package me.youngjibbit.module.impl.render;

import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure appearance/viewport contracts; native shader, skin and GL live in the native fixture. */
public class TargetHudNeutronTwoModeTest {
    @Test public void exactlyFiveDistinctChoicesReplaceRetiredModes(){
        TargetHUD hud=new TargetHUD();ComboValue mode=hud.getModeSetting("Mode");
        String[] expected={"Rue","Tenacity","Basic","Modern","Creative"};
        assertArrayEquals(expected,mode.getOptions());assertEquals("Modern",mode.getValue());assertEquals(3,mode.getSelectedIndex());
        for(int i=0;i<expected.length;i++){mode.setValue(expected[i]);assertEquals(i,mode.getSelectedIndex());}
        assertEquals(5000d,hud.gradientSpeed.getValue(),0);
        hud.resetPosition();assertEquals(0d,hud.getNumberSetting("Position X").getValue(),0);assertEquals(0d,hud.getNumberSetting("Position Y").getValue(),0);
    }
    @Test public void paletteSelectionDoesNotRewriteTheSavedLayout(){
        TargetHUD hud=new TargetHUD();
        for(String name:hud.getModeSetting("Mode").getOptions()){
            hud.getModeSetting("Mode").setValue(name);
            assertEquals(name,TargetHUD.effectiveDesign(name,true));assertEquals(name,TargetHUD.effectiveDesign(name,false));
            assertEquals(name,hud.getModeSetting("Mode").getValue());
        }
        assertEquals("Modern",TargetHUD.migrateMode("Neutron White"));
        assertEquals("Modern",TargetHUD.migrateMode("Neutron Outline"));
        assertEquals("Rue",TargetHUD.migrateMode("Rise"));assertEquals("Basic",TargetHUD.migrateMode("Test"));
        assertEquals(20,Theme.values().length);assertEquals(ThemeMode.LEGACY,"Neutron Legacy");
    }
    @Test public void bothCompactDesignsStayAroundThreeQuartersOfTenacity(){
        for(int[] screen:new int[][]{{1,1},{40,20},{320,240},{640,360},{854,480},{1280,720}}){
            float w=TargetHUD.modernWidth(screen[0],900),h=TargetHUD.modernHeight(screen[1]);
            assertTrue(Float.isFinite(w)&&Float.isFinite(h));
            assertTrue(w>=1&&w<=Math.max(1,screen[0]-8));assertTrue(h>=1&&h<=Math.max(1,screen[1]-8));
            assertEquals(w,TargetHUD.creativeWidth(screen[0],Float.NaN),0);assertEquals(h,TargetHUD.creativeHeight(screen[1]),0);
        }
        assertEquals(142,TargetHUD.modernWidth(1280,900),0);assertEquals(142,TargetHUD.modernWidth(640,20),0);
        assertEquals(34,TargetHUD.modernHeight(720),0);
        assertEquals(.75f,142f/190,.025f);assertEquals(.75f,34f/44,.025f);
    }
    @Test public void palettesRemainIndependentAndUnchanged(){
        Theme modern=Theme.approachTwo();
        assertEquals(0xfff71ec0,modern.getPrimaryGradientRGB());assertEquals(0xffff922f,modern.getSecondaryGradientRGB());
        assertSame(Theme.Neutron,Theme.values()[19]);assertEquals(20,Theme.values().length);
    }
}
