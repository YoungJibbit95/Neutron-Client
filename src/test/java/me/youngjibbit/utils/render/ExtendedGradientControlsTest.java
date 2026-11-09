package me.youngjibbit.utils.render;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.Value;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExtendedGradientControlsTest {
    @Test public void fractionalEffectSpeedsDoNotRoundToZero() {
        ClientSettings settings=new ClientSettings();
        assertEquals(.18,settings.orbitSpeed.getValue().doubleValue(),.000001);
        assertEquals(.18,settings.flameSpeed.getValue().doubleValue(),.000001);
        settings.orbitSpeed.setValue(.37);settings.flameSpeed.setValue(.29);
        assertEquals(.37,settings.orbitSpeed.getValue().doubleValue(),.000001);
        assertEquals(.29,settings.flameSpeed.getValue().doubleValue(),.000001);
    }
    @Test public void allGradientMethodsKeepTheirExactOrderAndLegacyDefault() {
        ClientSettings settings=new ClientSettings();
        String[] methods={"Flow","Ping-Pong","Pulse","Perimeter Orbit","Flame","Neon","Lines"};
        assertArrayEquals(methods,settings.gradientMethod.getOptions());
        assertEquals("Flow",settings.gradientMethod.getValue());
        assertEquals(0,settings.gradientMethod.getSelectedIndex());
        for(int i=0;i<methods.length;i++){
            settings.gradientMethod.setValue(methods[i]);
            assertEquals(i,settings.gradientMethod.getSelectedIndex());
        }
    }
    @Test public void perModuleWorldControlsAreAvailableWithTheOrdinaryUiGradient() {
        ClientSettings settings=new ClientSettings();
        settings.gradientMethod.setValue("Flow");
        for(Value<?> value:new Value<?>[]{settings.flameSpeed,settings.flameHeight,settings.neonCore,
                settings.neonGlow,settings.lineSegments,settings.lineTrail}){
            assertTrue(settings.groups().get("World effects").contains(value));
            assertTrue(value.getVisibility()==null||value.getVisibility().test(settings));
        }
    }
}
