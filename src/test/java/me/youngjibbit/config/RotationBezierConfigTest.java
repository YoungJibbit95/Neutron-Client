package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class RotationBezierConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private void available(Module module){assertNotNull("Missing optional Speed Curve",module.getModeSetting("Speed Curve"));}
    @Test public void optionalSettingsDefaultOffHideTheirParametersAndStayOutOfPlacement() {
        for(Module m:Arrays.<Module>asList(new Aura(),new AimAssist())) {
            available(m);assertEquals("Off",m.getModeSetting("Speed Curve").getValue());
            assertTrue(m.getModeSetting("Speed Curve").isVisible());
            assertFalse(m.getNumberSetting("Bezier Strength").isVisible());assertFalse(m.getNumberSetting("Bezier Duration").isVisible());
            m.getModeSetting("Speed Curve").setValue("Bezier");
            assertTrue(m.getNumberSetting("Bezier Strength").isVisible());assertTrue(m.getNumberSetting("Bezier Duration").isVisible());
        }
        assertNull(new Scaffold().getValueByName("Speed Curve"));
    }
    @Test public void warmLegacyAndVersionTwoLoadsDefaultOnlyTheMissingNewModeOff() throws Exception {
        for(Module m:Arrays.<Module>asList(new Aura(),new AimAssist())) {
            available(m);Path dir=temporary.newFolder().toPath();ConfigManager configs=new ConfigManager(dir,Collections.singletonList(m),()->false,message->{});
            String id=m.getId(),display=m instanceof Aura?"Aura":"AimAssist";
            String[] legacy={"{\""+display+"\":{}}","{\"version\":2,\"modules\":{\""+id+"\":{\"rotationVersion\":1,\"settings\":{}}}}",
                    "{\"version\":2,\"modules\":{\""+id+"\":{\"rotationVersion\":2,\"settings\":{}}}}"};
            for(String json:legacy) {
                m.getModeSetting("Speed Curve").setValue("Bezier");m.getNumberSetting("Bezier Duration").setValue(1700D);
                Path file=dir.resolve("Old.json");Files.write(file,json.getBytes(StandardCharsets.UTF_8));assertTrue(configs.getLastError(),configs.loadConfig("Old"));
                assertEquals("omitted feature must not inherit warm session activation","Off",m.getModeSetting("Speed Curve").getValue());
                assertEquals("inactive explicit tuning remains local",1700,m.getNumberSetting("Bezier Duration").getValue(),0);
                assertEquals("migration does not rewrite imported files",json,new String(Files.readAllBytes(file),StandardCharsets.UTF_8));
            }
        }
    }
    @Test public void explicitIndependentProfilesRoundTripAndInvalidLoadRemainsAtomic() throws Exception {
        Aura aura=new Aura();AimAssist assist=new AimAssist();available(aura);available(assist);
        Path dir=temporary.newFolder().toPath();ConfigManager configs=new ConfigManager(dir,Arrays.<Module>asList(aura,assist),()->false,message->{});
        aura.getModeSetting("Speed Curve").setValue("Bezier");aura.getNumberSetting("Bezier Strength").setValue(.8);aura.getNumberSetting("Bezier Duration").setValue(1200D);
        assist.getModeSetting("Speed Curve").setValue("Off");assist.getNumberSetting("Bezier Strength").setValue(.2);
        assertTrue(configs.saveConfig("Both"));String expected=configs.serialize();
        aura.getModeSetting("Speed Curve").setValue("Off");assist.getModeSetting("Speed Curve").setValue("Bezier");
        assertTrue(configs.getLastError(),configs.loadConfig("Both"));assertEquals(expected,configs.serialize());
        String invalid="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{\"speed_curve\":\"imaginary\"}},\"aimassist\":{\"settings\":{}}}}";
        Files.write(dir.resolve("Bad.json"),invalid.getBytes(StandardCharsets.UTF_8));assertFalse(configs.loadConfig("Bad"));assertEquals(expected,configs.serialize());
        assertEquals(invalid,new String(Files.readAllBytes(dir.resolve("Bad.json")),StandardCharsets.UTF_8));
    }
}
