package me.youngjibbit.module.impl.render;

import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class TargetHudMotionContractTest {
    @Test public void allSevenDesignsAndExistingDefaultArePreserved() {
        TargetHUD hud=new TargetHUD();
        ComboValue mode=hud.getModeSetting("Mode");
        assertArrayEquals(new String[]{"Neutron Default","Neutron White","Neutron Outline",
                "Rise","Tenacity","Basic","Test"},
                java.util.Arrays.copyOf(mode.getOptions(),7));
        assertEquals("Neutron 2.0",mode.getOptions()[7]);
        assertEquals("Neutron White",mode.getValue());
        assertEquals(1,mode.getSelectedIndex());
        assertEquals(5000d,hud.gradientSpeed.getValue(),0);
    }
    @Test public void twoPositionValuesAreAppendedAndResettable() throws Exception {
        TargetHUD hud=new TargetHUD();
        SliderValue x=position(hud,"positionX"),y=position(hud,"positionY");
        assertEquals(0,x.getValue(),0);assertEquals(0,y.getValue(),0);
        x.setValue(172d);y.setValue(208d);
        assertEquals(172,x.getValue(),0);assertEquals(208,y.getValue(),0);
        hud.resetPosition();
        assertEquals(0,x.getValue(),0);assertEquals(0,y.getValue(),0);
        // HUD exposes a persistent grid size and resetPositions action shared by other HUDs.
        HUD settings=new HUD();
        assertEquals(0,settings.snap.getValue(),0);
    }
    @Test public void targetHudDoesNotRequireASecondHudModuleOrGameplayHook() {
        TargetHUD hud=new TargetHUD();
        assertNotNull(hud.getModeSetting("Mode"));
        assertNotNull(hud.getValueByName("Position X"));
        assertNotNull(hud.getValueByName("Position Y"));
    }
    private static SliderValue position(TargetHUD hud,String field)throws Exception {
        Field f=TargetHUD.class.getDeclaredField(field);
        f.setAccessible(true);
        return (SliderValue)f.get(hud);
    }
}
