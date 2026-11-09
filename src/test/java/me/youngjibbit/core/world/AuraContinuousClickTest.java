package me.youngjibbit.core.world;

import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.value.impl.CheckBoxValue;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AuraContinuousClickTest {
    @Test public void offTargetSwingUsesTheRealAuraAndTransport() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "offTargetSwing");
    }

    public static class Checks {
        @Test public void offTargetSwing() throws Exception {
            try (Wave05AuraSelectionChecks.SelectionRun run = new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                Aura aura = run.run.fixture.aura;
                aura.getBooleanSetting("Rotations").setValue(false);
                aura.getBooleanSetting("Click Randomization Enabled").setValue(false);
                CheckBoxValue clicking = aura.getBooleanSetting("Click While Aiming");
                assertNotNull(clicking);
                assertFalse(clicking.getValue());
                clicking.setValue(true);
                assertTrue(run.run.scene.loadedEntityList.remove(run.second));
                run.first.posX = 2.5; run.first.posZ = 0;
                run.first.setEntityBoundingBox(new AxisAlignedBB(2.2, 64, -.3, 2.8, 65.8, .3));
                List<Object> packets = run.walk();
                assertEquals("The off-target control must really transport one movement", 1,
                        packets.stream().filter(C03PacketPlayer.class::isInstance).count());
                assertTrue("The control requires an accepted motion, not a rejected frame",
                        run.run.fixture.runtime.getRotationManager().isMotionAccepted());
                assertSame("The real selector must keep the in-range target", run.first, aura.getActiveTarget());
                assertTrue("The opt-in must survive the observed walking step", clicking.getValue());
                assertEquals("A due click while aiming must swing once", 1,
                        packets.stream().filter(C0APacketAnimation.class::isInstance).count());
                assertEquals("Off-target clicking must not attack", 0,
                        packets.stream().filter(C02PacketUseEntity.class::isInstance).count());
                // The fixture's camera/wire X is approximately .5, not zero. Center
                // the positive ray control on that camera instead of another miss.
                double cameraX = run.local.posX;
                run.first.posX = cameraX; run.first.posZ = 2.5;
                run.first.setEntityBoundingBox(new AxisAlignedBB(cameraX - .3, 64, 2.2, cameraX + .3, 65.8, 2.8));
                packets = run.walk();
                assertEquals("A valid attack has exactly one swing", 1, packets.stream().filter(C0APacketAnimation.class::isInstance).count());
                assertEquals(1, packets.stream().filter(C02PacketUseEntity.class::isInstance).count());
                assertTrue(run.run.scene.loadedEntityList.remove(run.first));
                packets = run.walk();
                assertEquals("Target loss stops both clicks and attacks", 0, packets.stream().filter(p -> p instanceof C0APacketAnimation || p instanceof C02PacketUseEntity).count());
            }
        }
    }
}
