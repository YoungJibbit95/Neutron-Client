package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Guarded silent poses use the shared movement/transport lifetime without moving the camera. */
public class GuardedSilentRotationTest {
    private static EventMotion motion() {
        return new EventMotion(.5,64,.5,12,3,true);
    }

    private static final class Requester implements Subscriber {
        final RotationManager rotations;
        final BooleanSupplier valid;
        @Subscribe final Listener<EventRotation> listener=new Listener<>(event->offer());
        Requester(RotationManager rotations,BooleanSupplier valid) {
            this.rotations=rotations;this.valid=valid;
        }
        private void offer(){rotations.request("nscaffold",15,90,70,valid);}
    }

    private static RotationManager begin(RotationBehaviorHookTest.Fixture fixture,BooleanSupplier valid) {
        fixture.player.rotationYaw=12;fixture.player.rotationPitch=3;
        RotationManager rotations=fixture.runtime.getRotationManager();
        rotations.bindConnection(fixture.network.getClientPackets());
        rotations.beginMotion(fixture.player,fixture.mc.theWorld,12,3);
        rotations.request("nscaffold",15,90,70,valid);
        return rotations;
    }

    private static void unchangedCamera(RotationBehaviorHookTest.Fixture fixture) {
        assertEquals(12,fixture.player.rotationYaw,0);
        assertEquals(3,fixture.player.rotationPitch,0);
    }

    @Test public void guardedSilentPoseReachesRealTransportAndConfirmedOwnerWithoutMovingCamera()throws Exception {
        try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(fixture,.5,.48);
            fixture.runtime.getObservation().beginOuterTick();
            RotationManager rotations=begin(fixture,()->true);
            EventMotion event=motion();rotations.resolve(event);
            assertEquals(90,event.getYaw(),0);assertEquals(70,event.getPitch(),0);
            assertNull(rotations.getConfirmedOwner());unchangedCamera(fixture);
            RotationManager.ResolvedC03SendTicket ticket=rotations.captureResolvedC03SendTicket(12,3);
            assertNotNull(ticket);
            C03PacketPlayer packet=new C03PacketPlayer.C05PacketPlayerLook(event.getYaw(),event.getPitch(),true);
            assertSame(packet,fixture.player.sendQueue.addToSendQueueTracked(packet,ticket.guard(packet)));
            assertSame(packet,fixture.channel.readOutbound());assertNull(fixture.channel.readOutbound());
            assertTrue(ticket.isCurrent());rotations.finishMotion(event.getYaw(),event.getPitch(),true);
            assertEquals("nscaffold",rotations.getConfirmedOwner());assertTrue(rotations.canUseRotation("nscaffold",false));
            unchangedCamera(fixture);
        }
    }

    @Test public void invalidOrMissingGuardCannotResolveOrGainMatchingNumberAuthority() {
        for(BooleanSupplier guard:new BooleanSupplier[]{()->false,null}) {
            RotationManager rotations=new RotationManager();rotations.beginMotion(12,3);
            rotations.request("nscaffold",15,90,70,guard);
            EventMotion event=motion();rotations.resolve(event);
            assertEquals(12,event.getYaw(),0);assertEquals(3,event.getPitch(),0);
            assertNull(rotations.captureResolvedC03SendTicket(12,3));
            rotations.finishMotion(90,70,true);
            assertNull(rotations.getConfirmedOwner());assertFalse(rotations.canUseRotation("nscaffold",false));
        }
    }

    @Test public void invalidationAfterResolveRevokesTicketAndCannotBeUndoneInTheSameFrame()throws Exception {
        try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(fixture,.5,.48);fixture.runtime.getObservation().beginOuterTick();
            AtomicBoolean valid=new AtomicBoolean(true);RotationManager rotations=begin(fixture,valid::get);
            rotations.resolve(motion());RotationManager.ResolvedC03SendTicket ticket=rotations.captureResolvedC03SendTicket(12,3);
            C03PacketPlayer packet=new C03PacketPlayer.C05PacketPlayerLook(90,70,true);
            assertTrue(ticket.guard(packet).test(packet));valid.set(false);
            assertNull(fixture.player.sendQueue.addToSendQueueTracked(packet,ticket.guard(packet)));
            valid.set(true);assertFalse(ticket.isCurrent());
            assertNull(fixture.player.sendQueue.addToSendQueueTracked(packet,ticket.guard(packet)));
            rotations.finishMotion(90,70,true);assertNull(rotations.getConfirmedOwner());
            assertNull(fixture.channel.readOutbound());unchangedCamera(fixture);
        }
    }

    @Test public void invalidationAfterTransportPreservesObservedNumbersButCannotGrantSourceOwnership()throws Exception {
        try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(fixture,.5,.48);fixture.runtime.getObservation().beginOuterTick();
            AtomicBoolean valid=new AtomicBoolean(true);RotationManager rotations=begin(fixture,valid::get);
            rotations.resolve(motion());RotationManager.ResolvedC03SendTicket ticket=rotations.captureResolvedC03SendTicket(12,3);
            C03PacketPlayer packet=new C03PacketPlayer.C05PacketPlayerLook(90,70,true);
            assertSame(packet,fixture.player.sendQueue.addToSendQueueTracked(packet,ticket.guard(packet)));
            assertSame(packet,fixture.channel.readOutbound());valid.set(false);
            rotations.finishMotion(90,70,true);
            assertTrue(rotations.isMotionAccepted());assertEquals(90,rotations.getEffectiveYaw(),0);
            assertNull(rotations.getConfirmedOwner());assertFalse(rotations.canUseRotation("nscaffold",false));
            unchangedCamera(fixture);
        }
    }

    @Test public void validationStartingANewFrameCannotWriteTheOldPoseOrOverwriteTheNewWinner() {
        RotationManager rotations=new RotationManager();rotations.beginMotion(12,3);
        rotations.request("nscaffold",15,90,70,()->{
            rotations.beginMotion(12,3);rotations.request("aura",20,-40,10);return true;
        });
        EventMotion old=motion();rotations.resolve(old);
        assertEquals(12,old.getYaw(),0);assertEquals(3,old.getPitch(),0);
        EventMotion current=motion();rotations.resolve(current);assertEquals(-40,current.getYaw(),0);
        rotations.finishMotion(current.getYaw(),current.getPitch(),true);
        assertEquals("aura",rotations.getConfirmedOwner());
    }

    @Test public void sameFrameResetAndReplacementCannotPublishTheDiscardedRequest() {
        RotationManager rotations=new RotationManager();rotations.beginMotion(12,3);
        rotations.request("nscaffold",15,90,70,()->{
            rotations.reset("nscaffold");rotations.request("nscaffold",15,-40,10,()->true);return true;
        });
        EventMotion old=motion();rotations.resolve(old);assertEquals(12,old.getYaw(),0);
        EventMotion current=motion();rotations.resolve(current);assertEquals(-40,current.getYaw(),0);
        rotations.finishMotion(current.getYaw(),current.getPitch(),true);
        assertEquals("nscaffold",rotations.getConfirmedOwner());assertEquals(-40,rotations.getEffectiveYaw(),0);
    }

    @Test public void sameFrameWithdrawalAtFinishRecordsActualTransportWithoutGivingReplacementAuthority() {
        for(boolean replacement:new boolean[]{false,true}) {
            RotationManager rotations=new RotationManager();AtomicBoolean finishing=new AtomicBoolean();
            rotations.beginMotion(12,3);
            rotations.request("nscaffold",15,90,70,()->{
                if(finishing.get()) {
                    rotations.reset("nscaffold");
                    if(replacement)rotations.request("aura",20,90,70);
                }
                return true;
            });
            rotations.resolve(motion());finishing.set(true);rotations.finishMotion(90,70,true);
            assertTrue(rotations.isMotionAccepted());assertTrue(rotations.hasEffectiveRotation());
            assertEquals(90,rotations.getEffectiveYaw(),0);assertEquals(70,rotations.getEffectivePitch(),0);
            assertNull(rotations.getConfirmedOwner());assertFalse(rotations.diagnosticSnapshot().collecting);
            assertNull(rotations.getPreparedMovement());
            assertFalse(rotations.canUseRotation("nscaffold",false));assertFalse(rotations.canUseRotation("aura",false));
        }
    }

    @Test public void newFrameFromFinishGuardRetainsItsCollectionAndWinner() {
        RotationManager rotations=new RotationManager();AtomicBoolean finishing=new AtomicBoolean();
        rotations.beginMotion(12,3);
        rotations.request("nscaffold",15,90,70,()->{
            if(finishing.get()) {
                rotations.beginMotion(12,3);rotations.request("aura",20,-40,10);
            }
            return true;
        });
        rotations.resolve(motion());finishing.set(true);rotations.finishMotion(90,70,true);
        assertTrue(rotations.diagnosticSnapshot().collecting);assertFalse(rotations.isMotionAccepted());
        EventMotion current=motion();rotations.resolve(current);assertEquals(-40,current.getYaw(),0);
        rotations.finishMotion(current.getYaw(),current.getPitch(),true);
        assertEquals("aura",rotations.getConfirmedOwner());assertEquals(-40,rotations.getEffectiveYaw(),0);
    }

    @Test public void recursiveResolutionCannotReuseTheGuardWhileItIsBeingEvaluated() {
        RotationManager rotations=new RotationManager();AtomicInteger calls=new AtomicInteger();rotations.beginMotion(12,3);
        rotations.request("nscaffold",15,90,70,()->{
            calls.incrementAndGet();EventMotion nested=motion();rotations.resolve(nested);
            assertEquals(12,nested.getYaw(),0);return true;
        });
        EventMotion event=motion();rotations.resolve(event);
        assertEquals(1,calls.get());assertEquals(12,event.getYaw(),0);
        rotations.finishMotion(90,70,true);assertNull(rotations.getConfirmedOwner());
    }

    @Test public void callbackFailurePropagatesAndRejectedCleanupDoesNotRunItAgain() {
        RotationManager rotations=new RotationManager();rotations.beginMotion(12,3);AtomicInteger calls=new AtomicInteger();
        IllegalStateException failure=new IllegalStateException("stale guarded pose");
        rotations.request("nscaffold",15,90,70,()->{calls.incrementAndGet();throw failure;});
        assertSame(failure,assertThrows(IllegalStateException.class,()->rotations.resolve(motion())));
        rotations.finishMotion(12,3,false);
        assertEquals(1,calls.get());assertNull(rotations.getConfirmedOwner());
        rotations.beginMotion(12,3);rotations.request("aura",20,30,10);rotations.finishMotion(30,10,true);
        assertEquals("aura",rotations.getConfirmedOwner());
    }

    @Test public void withdrawingAfterPreparedPhysicsKeepsItsPoseWithoutSourceAuthority()throws Exception {
        try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(fixture,.5,.48);fixture.player.rotationYaw=12;fixture.player.rotationPitch=3;
            RotationManager rotations=fixture.runtime.getRotationManager();AtomicBoolean valid=new AtomicBoolean(true);
            Requester requester=new Requester(rotations,valid::get);Pulsar.BUS.subscribe(requester);
            RuntimeObservation observation=fixture.runtime.getObservation();observation.beginOuterTick();
            try {
                observation.observePlayerStep(fixture.player,()->{
                    rotations.prepareMovement(fixture.player);
                    RotationManager.MovementRotation prepared=rotations.getPreparedMovement();
                    assertNotNull(prepared);assertEquals("nscaffold",prepared.source);
                    assertEquals(90,prepared.yaw,0);assertEquals(70,prepared.pitch,0);unchangedCamera(fixture);
                    rotations.beforeMovementPhysics();valid.set(false);
                    assertTrue(rotations.resumePreparedMovement(fixture.player,fixture.mc.theWorld));
                    EventMotion event=motion();rotations.resolve(event);
                    assertEquals(90,event.getYaw(),0);assertEquals(70,event.getPitch(),0);
                    rotations.finishMotion(event.getYaw(),event.getPitch(),true);
                    assertNull(rotations.getConfirmedOwner());unchangedCamera(fixture);return true;
                },body->body.getAsBoolean());
            } finally {observation.endOuterTick();Pulsar.BUS.unsubscribe(requester);}
        }
    }

    @Test public void guardResetDuringTicketValidationCannotSendOrRestoreAnOlderFrame()throws Exception {
        try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(fixture,.5,.48);fixture.runtime.getObservation().beginOuterTick();
            AtomicBoolean replace=new AtomicBoolean();RotationManager rotations=fixture.runtime.getRotationManager();
            begin(fixture,()->{
                if(replace.get()) {rotations.beginMotion(fixture.player,fixture.mc.theWorld,12,3);rotations.request("aura",20,-40,10);}
                return true;
            });
            rotations.resolve(motion());RotationManager.ResolvedC03SendTicket old=rotations.captureResolvedC03SendTicket(12,3);
            replace.set(true);C03PacketPlayer packet=new C03PacketPlayer.C05PacketPlayerLook(90,70,true);
            assertNull(fixture.player.sendQueue.addToSendQueueTracked(packet,old.guard(packet)));
            EventMotion current=motion();rotations.resolve(current);assertEquals(-40,current.getYaw(),0);
            rotations.finishMotion(current.getYaw(),current.getPitch(),true);
            assertEquals("aura",rotations.getConfirmedOwner());assertNull(fixture.channel.readOutbound());unchangedCamera(fixture);
        }
    }

    @Test public void sceneCallbackDuringTicketCreationCannotOverwriteANewerResolvedFrame()throws Exception {
        try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(fixture,.5,.48);RuntimeObservation observation=fixture.runtime.getObservation();
            observation.beginOuterTick();RotationManager rotations=begin(fixture,()->true);AtomicBoolean once=new AtomicBoolean(true);
            ResolvedC03SendTicketTest.set(RuntimeObservation.class,observation,"source",(Supplier<RuntimeObservation.SceneSample>)()->{
                if(once.getAndSet(false)) {
                    rotations.beginMotion(fixture.player,fixture.mc.theWorld,12,3);
                    rotations.request("scaffold",30,-40,10);rotations.resolve(motion());
                }
                return me.youngjibbit.core.observation.MinecraftSceneSource.capture(fixture.mc);
            });
            rotations.resolve(motion());RotationManager.ResolvedC03SendTicket current=rotations.captureResolvedC03SendTicket(12,3);
            assertNotNull(current);C03PacketPlayer packet=new C03PacketPlayer.C05PacketPlayerLook(-40,10,true);
            assertTrue(current.guard(packet).test(packet));rotations.finishMotion(-40,10,true);
            assertEquals("scaffold",rotations.getConfirmedOwner());unchangedCamera(fixture);
        }
    }
}
