package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Real consumers and ordinary walking; no synthetic request/finish authority. */
public class Wave05WalkingLifetimeChecks {
    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static void targetAt(CombatSequenceWave2Checks.Run run, double x) {
        run.target.posX = x;
        run.target.setEntityBoundingBox(new AxisAlignedBB(x - .3, 64, 1.7, x + .3, 65.8, 2.3));
    }

    private static AimAssist configure(CombatSequenceWave2Checks.Run run, String source) throws Exception {
        targetAt(run, -1);
        run.fixture.player.rotationYaw = 30;
        if ("aura".equals(source)) {
            run.fixture.aura.getNumberSetting("Yaw Speed").setValue(90D);
            run.fixture.aura.getNumberSetting("Pitch Speed").setValue(90D);
            return null;
        }
        run.fixture.aura.setToggled(false);
        if (!"aimassist".equals(source)) return null;
        AimAssist assist = new AimAssist();
        assist.getModeSetting("Rotations").setValue("Normal");
        assist.getModeSetting("Randomization Style").setValue("Aus");
        assist.getNumberSetting("Yaw Speed").setValue(90D);
        assist.getNumberSetting("Pitch Speed").setValue(90D);
        assist.getNumberSetting("Jitter").setValue(0D);
        ScaffoldOwnerContinuityTest.field(Module.class, "toggled").set(assist, true);
        assist.onEnable();
        return assist;
    }

    private static void nextPose(CombatSequenceWave2Checks.Run run) {
        targetAt(run, 1.5);
        run.fixture.player.rotationYaw = -40;
        run.scene.motionTime.addAndGet(50_000_000L);
    }

    private static void assertReported(CombatSequenceWave2Checks.Run run, float yaw) throws Exception {
        assertEquals(yaw, run.fixture.player.serverYaw, 0);
        assertEquals(yaw, ScaffoldOwnerContinuityTest.field(EntityPlayerSP.class, "lastReportedYaw").getFloat(run.fixture.player), 0);
        assertEquals(yaw, run.fixture.runtime.getRotationManager().getEffectiveYaw(), 0);
    }

    private void nestedPacket(String source, boolean pre) throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            AimAssist assist = configure(run, source);
            final boolean[] nested = {false};
            final int[] posts = {0};
            final float[] inner = {Float.NaN, Float.NaN};
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.motion = event -> { if (event.isPost()) posts[0]++; };
            hooks.packet = event -> {
                if (!nested[0] && event.isOutbound() && event.isPre() == pre
                        && event.getPacket() instanceof C03PacketPlayer) {
                    nested[0] = true;
                    nextPose(run);
                    run.fixture.player.onUpdateWalkingPlayer();
                    assertTrue("inner walking really accepted", run.fixture.runtime.getRotationManager().isMotionAccepted());
                    inner[0] = run.fixture.runtime.getRotationManager().getEffectiveYaw();
                    inner[1] = run.fixture.player.rotationYaw;
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> actual = run.walk();
                assertTrue(nested[0]);
                assertTrue(Float.isFinite(inner[0]));
                assertEquals("only inner frame publishes Motion POST", 1, posts[0]);
                assertEquals(pre ? "old packet was superseded before transport" : "old packet was already accepted before its POST callback",
                        pre ? 1 : 2, count(actual, C03PacketPlayer.class));
                assertReported(run, inner[0]);
                assertEquals(inner[1], run.fixture.player.rotationYaw, 0);
                assertTrue(run.fixture.runtime.getRotationManager().isMotionAccepted());
                assertEquals("aimassist".equals(source) ? "aimassist" : null,
                        run.fixture.runtime.getRotationManager().getConfirmedOwner());
                assertEquals(0, count(actual, C0APacketAnimation.class));
                assertEquals(0, count(actual, C02PacketUseEntity.class));
            } finally {
                Pulsar.BUS.unsubscribe(hooks);
                if (assist != null) assist.onDisable();
            }
        }
    }

    @Test public void vanillaPacketPreNestedWalkingKeepsOnlyInnerFrame() throws Exception { nestedPacket("vanilla", true); }
    @Test public void aimAssistPacketPreNestedWalkingKeepsOnlyInnerFrame() throws Exception { nestedPacket("aimassist", true); }
    @Test public void vanillaPacketPostNestedWalkingPreservesBothTransportFacts() throws Exception { nestedPacket("vanilla", false); }
    @Test public void aimAssistPacketPostNestedWalkingPreservesBothTransportFacts() throws Exception { nestedPacket("aimassist", false); }

    @Test public void cancelledOuterMotionPreCannotRejectAcceptedInnerFrame() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            configure(run, "vanilla");
            final boolean[] nested = {false};
            final int[] posts = {0};
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.motion = event -> {
                if (event.isPost()) posts[0]++;
                if (event.isPre() && !nested[0]) {
                    nested[0] = true;
                    nextPose(run);
                    run.fixture.player.onUpdateWalkingPlayer();
                    assertTrue(run.fixture.runtime.getRotationManager().isMotionAccepted());
                    event.cancel();
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> actual = run.walk();
                assertTrue(nested[0]);
                assertEquals(1, count(actual, C03PacketPlayer.class));
                assertEquals(1, posts[0]);
                assertTrue("cancel belongs to outer frame only", run.fixture.runtime.getRotationManager().isMotionAccepted());
                assertReported(run, -40);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    @Test public void throwingOuterPacketPreCannotRejectAcceptedInnerFrame() throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            configure(run, "vanilla");
            final boolean[] nested = {false};
            IllegalStateException failure = new IllegalStateException("outer packet callback");
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (!nested[0] && event.isPre() && event.isOutbound() && event.getPacket() instanceof C03PacketPlayer) {
                    nested[0] = true;
                    nextPose(run);
                    run.fixture.player.onUpdateWalkingPlayer();
                    throw failure;
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                assertSame(failure, assertThrows(IllegalStateException.class, run::walk));
                assertTrue(nested[0]);
                assertEquals(1, count(run.drain(), C03PacketPlayer.class));
                assertTrue(run.fixture.runtime.getRotationManager().isMotionAccepted());
                assertReported(run, -40);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    private void replacement(String source) throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            AimAssist assist = configure(run, source);
            C03PacketPlayer replacement = new C03PacketPlayer.C05PacketPlayerLook(73, 11, true);
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (event.isPre() && event.isOutbound() && event.getPacket() instanceof C03PacketPlayer)
                    event.setPacket(replacement);
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> actual = run.walk();
                assertEquals(1, count(actual, C03PacketPlayer.class));
                assertTrue("actual selected replacement reached EmbeddedChannel", actual.stream().anyMatch(packet -> packet == replacement));
                assertReported(run, 73);
                assertEquals(11, run.fixture.runtime.getRotationManager().getEffectivePitch(), 0);
                assertTrue(run.fixture.runtime.getRotationManager().isMotionAccepted());
                assertNull("rewritten numbers cannot grant requested source", run.fixture.runtime.getRotationManager().getConfirmedOwner());
            } finally {
                Pulsar.BUS.unsubscribe(hooks);
                if (assist != null) assist.onDisable();
            }
        }
    }
    @Test public void vanillaReplacementStillReachesTransport() throws Exception { replacement("vanilla"); }
    @Test public void aimAssistReplacementStillReachesTransport() throws Exception { replacement("aimassist"); }

    private void heldReplay(String source, boolean laterMotion) throws Exception {
        try (CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false)) {
            AimAssist assist = configure(run, source);
            List<C03PacketPlayer> selected = new ArrayList<>();
            final int[] posts = {0};
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.motion = event -> { if (event.isPost()) posts[0]++; };
            hooks.packet = event -> {
                if (event.isPre() && event.isOutbound() && event.getPacket() instanceof C03PacketPlayer)
                    selected.add((C03PacketPlayer) event.getPacket());
            };
            Object hold = new Object();
            run.fixture.network.getClientPackets().delays().request(hold, 500,
                    (packet, flow) -> flow == EventFlow.OUTBOUND && packet instanceof C03PacketPlayer);
            Pulsar.BUS.subscribe(hooks);
            try {
                assertEquals(0, count(run.walk(), C03PacketPlayer.class));
                long firstSequence = run.fixture.runtime.getRotationManager().getMotionSequence();
                if (laterMotion) {
                    nextPose(run);
                    assertEquals(0, count(run.walk(), C03PacketPlayer.class));
                    assertTrue(run.fixture.runtime.getRotationManager().getMotionSequence() > firstSequence);
                }
                int expected = laterMotion ? 2 : 1;
                assertEquals(expected, selected.size());
                assertFalse(run.fixture.runtime.getRotationManager().isMotionAccepted());
                assertNull(run.fixture.runtime.getRotationManager().getConfirmedOwner());
                int postsBeforeReplay = posts[0];
                run.fixture.network.getClientPackets().delays().release(hold);
                List<Object> replayed = run.drain();
                assertEquals("ordinary queued movement retains existing replay semantics", expected, count(replayed, C03PacketPlayer.class));
                for (C03PacketPlayer queued : selected) {
                    assertTrue("the selected queued object really reached transport", replayed.stream().anyMatch(packet -> packet == queued));
                    assertTrue("fixture makes both wire yaw observations explicit", queued.getRotating());
                }
                assertEquals(selected.get(selected.size() - 1).getYaw(), run.fixture.runtime.getRotationManager().getEffectiveYaw(), 0);
                assertEquals("packet replay is not a new Motion POST", postsBeforeReplay, posts[0]);
                assertFalse("replay does not grant current movement acceptance", run.fixture.runtime.getRotationManager().isMotionAccepted());
                assertNull("replay cannot resurrect a source", run.fixture.runtime.getRotationManager().getConfirmedOwner());
                assertEquals(0, count(replayed, C0APacketAnimation.class));
                assertEquals(0, count(replayed, C02PacketUseEntity.class));
            } finally {
                Pulsar.BUS.unsubscribe(hooks);
                run.fixture.network.getClientPackets().delays().release(hold);
                if (assist != null) assist.onDisable();
            }
        }
    }
    @Test public void vanillaHeldSameFrameReplaysWithoutAuthority() throws Exception { heldReplay("vanilla", false); }
    @Test public void vanillaHeldOlderFrameReplaysWithoutAuthority() throws Exception { heldReplay("vanilla", true); }
    @Test public void auraHeldSameFrameReplaysWithoutAuthority() throws Exception { heldReplay("aura", false); }
    @Test public void auraHeldOlderFrameReplaysWithoutAuthority() throws Exception { heldReplay("aura", true); }
    @Test public void aimAssistHeldSameFrameReplaysWithoutAuthority() throws Exception { heldReplay("aimassist", false); }
    @Test public void aimAssistHeldOlderFrameReplaysWithoutAuthority() throws Exception { heldReplay("aimassist", true); }
}
