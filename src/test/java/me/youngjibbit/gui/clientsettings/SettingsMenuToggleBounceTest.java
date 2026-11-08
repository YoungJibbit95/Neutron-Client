package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.Value;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class SettingsMenuToggleBounceTest {
    @Test public void switchedInkAndThumbFollowTheSameReversibleSpringWithoutChangingConfig()
            throws Exception {
        SettingsMenu menu=new SettingsMenu();
        ClientSettings owner=new ClientSettings();
        assertTrue(owner.animations.getValue());
        assertEquals(0,follow(menu,owner.animations,0,0),0);
        float moving=follow(menu,owner.animations,1,.03f);
        assertTrue(moving>0&&moving<1);
        assertEquals(moving,follow(menu,owner.animations,0,0),0);
        for(int i=0;i<30;i++)follow(menu,owner.animations,i%2==0?1:0,.01f);
        assertTrue(owner.animations.getValue()); // Rendered motion cannot mutate Value.
    }
    @Test public void sliderPreviewRemainsWithinLogicalTrackForAllInputs() throws Exception {
        SettingsMenu menu=new SettingsMenu();
        ClientSettings owner=new ClientSettings();
        double original=owner.roundness.getValue();
        for(int i=0;i<75;i++){
            float response=follow(menu,owner.roundness,i%2==0?1:0,.016f);
            assertTrue(Float.isFinite(response)&&response>=0&&response<=1);
        }
        assertEquals(original,owner.roundness.getValue(),0);
    }
    private static float follow(SettingsMenu menu,Value<?> value,float target,float dt) throws Exception {
        Method m=SettingsMenu.class.getDeclaredMethod("follow",Value.class,float.class,float.class);
        m.setAccessible(true);
        return (float)m.invoke(menu,value,target,dt);
    }
}
