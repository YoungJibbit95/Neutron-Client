package me.youngjibbit.core.network;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.motion.EventMotion;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;

import static org.junit.Assert.*;

/** Actual resolved-send tickets used by BedBreaker/NScaffold must not regain lifetime at FakeLag release. */
public class FakeLagRotationContractTest {
    @Test public void bedBreakerTicketCannotAuthorizeAnExpiredMotionThroughPulse() throws Exception { Wave2IsolatedCase.run(Checks.class, "bedBreakerTicketCannotAuthorizeAnExpiredMotionThroughPulse"); }
    @Test public void scaffoldTicketCannotAuthorizeAnExpiredMotionThroughSpoof() throws Exception { Wave2IsolatedCase.run(Checks.class, "scaffoldTicketCannotAuthorizeAnExpiredMotionThroughSpoof"); }
    public static final class Checks {
        @Test public void bedBreakerTicketCannotAuthorizeAnExpiredMotionThroughPulse() throws Exception { check("bedbreaker", DemiseFakeLagQueue.Mode.PULSE); }
        @Test public void scaffoldTicketCannotAuthorizeAnExpiredMotionThroughSpoof() throws Exception { check("nscaffold", DemiseFakeLagQueue.Mode.SPOOF); }
        private void check(String source, DemiseFakeLagQueue.Mode mode) throws Exception {
            try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
                ScaffoldClientFlowTest.prepare(f, .5, .48);
                f.runtime.getObservation().beginOuterTick();
                RotationManager rotation = f.runtime.getRotationManager(); rotation.bindConnection(f.network.getClientPackets());
                rotation.beginMotion(f.player, f.mc.theWorld, 0, 0); rotation.request(source, 15, 90, 70, () -> true);
                EventMotion motion = new EventMotion(.5, 64, .48, 0, 0, true); rotation.resolve(motion);
                RotationManager.ResolvedC03SendTicket ticket = rotation.captureResolvedC03SendTicket(0, 0); assertNotNull(ticket);
                C03PacketPlayer packet = new C03PacketPlayer.C05PacketPlayerLook(motion.getYaw(), motion.getPitch(), true);
                assertTrue(ticket.guard(packet).test(packet));
                Object owner = new Object(); DemiseFakeLagQueue queue = f.network.getClientPackets().fakeLag();
                queue.request(owner, mode, 1000, () -> true, (held, before) -> fail("expired movement must not invoke a release hook"));
                if (mode == DemiseFakeLagQueue.Mode.SPOOF) queue.sourceUpdate(owner);
                assertNull(f.player.sendQueue.addToSendQueueTracked(packet, ticket.guard(packet)));
                assertEquals(1, queue.size()); assertNull(f.channel.readOutbound());
                rotation.finishMotion(motion.getYaw(), motion.getPitch(), false); assertFalse(ticket.isCurrent());
                queue.release(owner, true); assertNull(f.channel.readOutbound()); assertNull(rotation.getConfirmedOwner());
                assertFalse(rotation.canUseRotation(source, false));
                C03PacketPlayer ordinary = new C03PacketPlayer.C04PacketPlayerPosition(1, 64, 1, true);
                queue.request(owner, mode, 1000, () -> true, (held, before) -> { });
                if (mode == DemiseFakeLagQueue.Mode.SPOOF) queue.sourceUpdate(owner);
                assertNull(f.player.sendQueue.addToSendQueueTracked(ordinary)); queue.release(owner, true);
                assertSame(ordinary, f.channel.readOutbound()); assertNull(f.channel.readOutbound());
                assertNull(rotation.getConfirmedOwner());
                f.runtime.getObservation().endOuterTick();
            }
        }
    }
}
