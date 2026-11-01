package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Actual module, Ordinary walking, controller and EmbeddedChannel; world storage is synthetic. */
public class Wave05CombatContractChecks {
    @Test public void auraNestedWalkingCannotTransportTheSupersededC03() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            run.fixture.aura.getNumberSetting("Yaw Speed").setValue(90D);
            run.fixture.aura.getNumberSetting("Pitch Speed").setValue(90D);
            run.target.posX = -1;
            run.target.setEntityBoundingBox(new AxisAlignedBB(-1.3, 64, 1.7, -.7, 65.8, 2.3));
            boolean[] nested = {false};
            int[] posts = {0};
            float[] innerYaw = {Float.NaN};
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.motion = event -> { if (event.isPost()) posts[0]++; };
            hooks.packet = event -> {
                if (!nested[0] && event.isOutbound() && event.isPre()
                        && event.getPacket() instanceof C03PacketPlayer) {
                    nested[0] = true;
                    run.target.posX = 1.5;
                    run.target.setEntityBoundingBox(new AxisAlignedBB(1.2, 64, 1.7, 1.8, 65.8, 2.3));
                    run.scene.motionTime.addAndGet(50_000_000L);
                    run.fixture.player.onUpdateWalkingPlayer();
                    assertEquals("aura", run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    innerYaw[0] = run.fixture.runtime.getRotationManager().getEffectiveYaw();
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = run.walk();
                assertTrue(nested[0]);
                assertTrue(Float.isFinite(innerYaw[0]));
                assertEquals(1, posts[0]);
                assertEquals("only the newer frame may transport movement", 1, count(packets, C03PacketPlayer.class));
                assertEquals("aura", run.fixture.runtime.getRotationManager().getConfirmedOwner());
                assertEquals(innerYaw[0], run.fixture.runtime.getRotationManager().getEffectiveYaw(), 0);
                assertEquals(innerYaw[0], run.fixture.player.serverYaw, 0);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    @Test public void targetLossAfterAcceptedSwingCannotStartAutoblock() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            run.fixture.player.inventory.mainInventory[run.fixture.player.inventory.currentItem] =
                    new ItemStack(Items.diamond_sword);
            run.fixture.aura.getBooleanSetting("Autoblock").setValue(true);
            final boolean[] lostAfterSwing = {false};
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() instanceof C0APacketAnimation) {
                    assertEquals("aura", run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    run.target.isDead = true;
                    lostAfterSwing[0] = true;
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = run.walk();
                assertEquals("real walking transport establishes this frame", 1, count(packets, C03PacketPlayer.class));
                assertTrue("loss occurs after an actually accepted swing", lostAfterSwing[0]);
                assertEquals(1, count(packets, C0APacketAnimation.class));
                assertEquals("final controller guard already rejects the dead target", 0, count(packets, C02PacketUseEntity.class));
                assertEquals("a target invalidated by the swing callback cannot authorize new blocking", 0,
                        count(packets, C08PacketPlayerBlockPlacement.class));
            } finally {
                Pulsar.BUS.unsubscribe(hooks);
            }
        }
    }

    @Test public void nestedManualAttackAndAuraShareOneControllerBudget() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            final boolean[] enteredManual = {false}, enteredNested = {false};
            Listener<AttackEvent> reenter = new Listener<>(AttackEvent.class, event -> {
                if (enteredNested[0]) return;
                enteredNested[0] = true;
                run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true);
            });
            Listener<EventMotion> manualBeforeAura = new Listener<>(EventMotion.class, event -> {
                if (!event.isPre() || enteredManual[0]) return;
                enteredManual[0] = true;
                // A separate manual controller action shares the same real outer tick with Aura.
                run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true);
            }, EventPriority.HIGHEST);
            Pulsar.BUS.subscribe(reenter);
            Pulsar.BUS.subscribe(manualBeforeAura);
            try {
                List<Object> packets = run.walk();
                assertTrue(enteredManual[0]);
                assertTrue("AttackEvent really reenters the production controller", enteredNested[0]);
                assertEquals(1, count(packets, C03PacketPlayer.class));
                assertEquals("aura", run.fixture.runtime.getRotationManager().getConfirmedOwner());
                assertEquals("manual controller use consumes the shared automatic budget", 0,
                        count(packets, C0APacketAnimation.class));
                assertEquals("nested manual callbacks cannot send a second C02 in the shared tick", 1,
                        count(packets, C02PacketUseEntity.class));
            } finally {
                Pulsar.BUS.unsubscribe(manualBeforeAura);
                Pulsar.BUS.unsubscribe(reenter);
            }
        }
    }

    @Test public void packetPreReentryCannotDuplicateManualAttack() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            final boolean[] entered = {false};
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (!entered[0] && event.isOutbound() && event.isPre() && event.getPacket() instanceof C02PacketUseEntity) {
                    entered[0] = true;
                    assertFalse("packet callback is still inside the admitted controller scope",
                            run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true));
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                assertTrue(run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true));
                assertTrue(entered[0]);
                assertEquals(1, count(run.drain(), C02PacketUseEntity.class));
            } finally {
                Pulsar.BUS.unsubscribe(hooks);
            }
        }
    }

    @Test public void cancelledAndThrowingCallbacksReleaseManualControllerScope() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            final int[] scenario = {-1};
            final RuntimeException failure = new IllegalStateException("synthetic callback failure");
            Listener<AttackEvent> attack = new Listener<>(AttackEvent.class, event -> {
                if (scenario[0] == 0) event.cancel();
                if (scenario[0] == 1) throw failure;
            });
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (!event.isOutbound() || !event.isPre() || !(event.getPacket() instanceof C02PacketUseEntity)) return;
                if (scenario[0] == 2) event.cancel();
                if (scenario[0] == 3) throw failure;
            };
            Pulsar.BUS.subscribe(attack);
            Pulsar.BUS.subscribe(hooks);
            try {
                for (int kind = 0; kind < 4; kind++) {
                    run.fixture.runtime.getAttackScheduler().beginTick();
                    scenario[0] = kind;
                    try {
                        assertFalse(run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true));
                        assertTrue("throwing callback must preserve its exception", kind == 0 || kind == 2);
                    } catch (RuntimeException actual) {
                        assertSame(failure, actual);
                        assertTrue(kind == 1 || kind == 3);
                    }
                    assertEquals("failed admission sent no attack", 0, count(run.drain(), C02PacketUseEntity.class));
                    scenario[0] = -1;
                    assertTrue("independent retry after cancellation/error remains possible",
                            run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true));
                    assertEquals(1, count(run.drain(), C02PacketUseEntity.class));
                    assertFalse("successful retry still consumes Aura's shared tick budget",
                            run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true));
                    assertEquals(0, count(run.drain(), C02PacketUseEntity.class));
                }
            } finally {
                Pulsar.BUS.unsubscribe(hooks);
                Pulsar.BUS.unsubscribe(attack);
            }
        }
    }

    @Test public void liveTargetStillStartsAutoblockAfterAttack() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            run.fixture.player.inventory.mainInventory[run.fixture.player.inventory.currentItem] =
                    new ItemStack(Items.diamond_sword);
            run.fixture.aura.getBooleanSetting("Autoblock").setValue(true);
            List<Object> packets = run.walk();
            assertEquals(1, count(packets, C03PacketPlayer.class));
            assertEquals(1, count(packets, C0APacketAnimation.class));
            assertEquals(1, count(packets, C02PacketUseEntity.class));
            assertEquals("valid post-attack blocking remains available", 1, count(packets, C08PacketPlayerBlockPlacement.class));
        }
    }

    @Test public void visibleAimAssistLeavesClicksManualAndMovementOnCameraBasis() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            run.fixture.aura.setToggled(false);
            AimAssist assist = new AimAssist();
            assist.getModeSetting("Rotations").setValue("Normal");
            assist.getModeSetting("Randomization Style").setValue("Aus");
            assist.getNumberSetting("Yaw Speed").setValue(90D);
            assist.getNumberSetting("Pitch Speed").setValue(90D);
            assist.getNumberSetting("Jitter").setValue(0D);
            run.target.posX = -1;
            run.target.setEntityBoundingBox(new AxisAlignedBB(-1.3, 64, 1.7, -.7, 65.8, 2.3));
            ScaffoldOwnerContinuityTest.field(Module.class, "toggled").set(assist, true);
            assist.onEnable();
            try {
                List<Object> walking = run.walk();
                assertEquals(1, count(walking, C03PacketPlayer.class));
                assertEquals("aimassist", run.fixture.runtime.getRotationManager().getConfirmedOwner());
                assertSame(run.target, assist.getTarget());
                float yaw = run.fixture.player.rotationYaw, pitch = run.fixture.player.rotationPitch;
                assertTrue("real visible request turns the camera toward the oblique target", yaw > 1);
                assertEquals(yaw, run.fixture.runtime.getRotationManager().getEffectiveYaw(), 0);
                assertEquals(pitch, run.fixture.runtime.getRotationManager().getEffectivePitch(), 0);
                assertSame("camera ray reaches the actual target", run.target, RuntimeServices.raycast().raycast(yaw, pitch, 4));
                assertEquals("AimAssist never swings automatically", 0, count(walking, C0APacketAnimation.class));
                assertEquals("AimAssist never attacks automatically", 0, count(walking, C02PacketUseEntity.class));
                run.fixture.player.motionX = run.fixture.player.motionZ = 0;
                run.fixture.player.moveFlying(0, 1, .1f);
                assertEquals(-.1 * Math.sin(Math.toRadians(yaw)), run.fixture.player.motionX, 3e-5);
                assertEquals(.1 * Math.cos(Math.toRadians(yaw)), run.fixture.player.motionZ, 3e-5);
                for (int click = 0; click < 2; click++) {
                    run.fixture.player.swingItem();
                    assertTrue("independent manual clicks without Aura keep their normal controller behavior",
                            run.fixture.mc.playerController.tryAttackEntity(run.fixture.player, run.target, () -> true));
                }
                List<Object> manual = run.drain();
                assertEquals(2, count(manual, C0APacketAnimation.class));
                assertEquals(2, count(manual, C02PacketUseEntity.class));
                assertEquals(yaw, run.fixture.player.rotationYaw, 0);
            } finally {
                assist.onDisable();
            }
        }
    }
}
