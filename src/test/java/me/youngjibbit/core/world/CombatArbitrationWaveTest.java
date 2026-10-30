package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.AttackScheduler;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.TargetingService;
import me.zero.alpine.event.EventPhase;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.module.impl.combat.Aura;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;

/** Real Ordinary Aura POST consumer with seeded local acceptance; no server-hit claim. */
public class CombatArbitrationWaveTest {
    @Test public void equalAngleForeignScaffoldOwnerCannotAuthorizeAuraInEitherRequestOrder() throws Exception {
        for (boolean scaffoldFirst : new boolean[]{false, true}) {
            try (RotationBehaviorHookTest.Fixture fixture = new RotationBehaviorHookTest.Fixture()) {
                ScaffoldClientFlowTest.prepare(fixture, .5, .48);
                fixture.mc.gameSettings.keyBindUseItem = new KeyBinding("synthetic.use", 0, "synthetic");
                fixture.player.sendQueue.clientWorldController = fixture.mc.theWorld;
                RotationBehaviorHookTest.Player target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
                target.worldObj = fixture.mc.theWorld;
                target.setEntityId(9);
                target.posX = .5; target.posY = 64; target.posZ = 2;
                target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, 1.7, .8, 65.8, 2.3));
                ScaffoldOwnerContinuityTest.field(World.class, "loadedEntityList").set(fixture.mc.theWorld,
                        new ArrayList<>(Collections.singletonList(target)));
                ScaffoldOwnerContinuityTest.field(PlayerControllerMP.class, "currentGameType").set(fixture.mc.playerController,
                        WorldSettings.GameType.SPECTATOR);
                fixture.aura.onEnable();
                try {
                    ScaffoldOwnerContinuityTest.field(Aura.class, "target").set(fixture.aura, target);
                    ScaffoldOwnerContinuityTest.field(Aura.class, "worldContext").set(fixture.aura, fixture.mc.theWorld);
                    ScaffoldOwnerContinuityTest.field(Aura.class, "playerContext").set(fixture.aura, fixture.player);
                    assertTrue(fixture.runtime.getTargetingService().isValidTarget(target, new TargetingService.Options()));
                    assertTrue("the equal numerical pose really hits the target",
                            fixture.runtime.getRaycastService().canHit(target, 0, 0, 3));
                    fixture.runtime.getObservation().beginOuterTick();
                    try {
                        fixture.runtime.getObservation().observePlayerStep(fixture.player, () -> {
                            RotationManager manager = fixture.runtime.getRotationManager();
                            manager.bindConnection(fixture.network.getClientPackets());
                            manager.beginMotion(fixture.player, fixture.mc.theWorld, 0, 0);
                            if (scaffoldFirst) manager.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 0, 0);
                            manager.request("aura", RotationManager.AURA_PRIORITY, 0, 0);
                            if (!scaffoldFirst) manager.request("scaffold", RotationManager.SCAFFOLD_PRIORITY, 0, 0);
                            EventMotion motion = new EventMotion(.5, 64, .5, 0, 0, true);
                            manager.resolve(motion);
                            manager.finishMotion(0, 0, true);
                            assertTrue(manager.matchesEffective(0, 0));
                            assertTrue(manager.canUseRotation("scaffold", false));
                            assertFalse(manager.canUseRotation("aura", false));
                            motion.setEventPhase(EventPhase.POST);
                            motion.setAccepted(true);
                            Pulsar.BUS.post(motion);
                            try {
                                assertEquals("Aura's real POST listener entered the scheduler",
                                        manager.getMotionSequence(), ScaffoldOwnerContinuityTest.field(AttackScheduler.class,
                                                "attemptedMotion").getLong(fixture.runtime.getAttackScheduler()));
                            } catch (Exception error) { throw new AssertionError(error); }
                            assertFalse(fixture.runtime.getAttackScheduler().hasAttackThisTick());
                            return true;
                        }, step -> step.getAsBoolean());
                    } finally { fixture.runtime.getObservation().endOuterTick(); }
                    Object packet;
                    while ((packet = fixture.channel.readOutbound()) != null)
                        assertFalse("foreign numerical equality cannot emit C02", packet instanceof C02PacketUseEntity);
                } finally { fixture.aura.onDisable(); }
            }
        }
    }
}
