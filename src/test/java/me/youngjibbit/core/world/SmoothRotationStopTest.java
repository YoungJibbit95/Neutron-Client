package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.event.packet.TickEvent;
import net.minecraft.network.play.client.*;
import net.minecraft.util.MathHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.*;
import static org.junit.Assert.*;

/** Real selection, ordinary walking, accepted transport and module lifecycles. */
@RunWith(Parameterized.class)
public class SmoothRotationStopTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"targetDeathReturnsToTheLiveCameraWithoutActions"},
                {"disableAndRotationToggleReturnWithoutRetainingAttackAuthority"},
                {"scaffoldUsesConfiguredDurationAndReturnsItsRenderedPose"},
                {"reenablePreemptsTheOldReturn"},{"rejectedReturnCannotGrantMovementOrActionAuthority"},
                {"packetCallbackReenableInvalidatesTheOldReturnTicket"},
                {"sceneChangeAndReservedSourceCannotReviveReturn"},{"disabledSmoothingRestoresImmediateStop"},
                {"ownRequestThenDisableInTheSamePreFrameStillReturns"},
                {"scaffoldRotationToggleCannotBorrowItsPendingReturnForPlacement"},
                {"reenableInsideAuraReleaseCapturePreservesTheNewGeneration"},
                {"rotationToggleInPostCannotAttackWithPendingReturn"}});
    }
    private final String method;
    public SmoothRotationStopTest(String method) { this.method=method; }
    @Test public void checkInFreshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class,method); }

    public static final class Checks {
        @Test public void rotationToggleInPostCannotAttackWithPendingReturn() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f);int[] reached={0};
                me.zero.alpine.listener.Listener<me.youngjibbit.event.motion.EventMotion> beforePost=new me.zero.alpine.listener.Listener<>(
                        me.youngjibbit.event.motion.EventMotion.class,e->{
                    if(e.isPost() && reached[0]++==0) {
                        assertTrue(rotation(f).canUseRotation("aura",false));
                        assertTrue("positive control: accepted ray hits live target",f.run.fixture.runtime.getRaycastService().canHit(
                                f.first,rotation(f).getEffectiveYaw(),rotation(f).getEffectivePitch(),3));
                        f.run.fixture.aura.getBooleanSetting("Rotations").setValue(false);
                        assertTrue(rotation(f).hasRotationRelease("aura"));
                    }
                },me.zero.alpine.event.EventPriority.HIGHEST);
                Pulsar.BUS.subscribe(beforePost);
                try { noActions(f.walk()); }
                finally { Pulsar.BUS.unsubscribe(beforePost); }
                assertEquals(1,reached[0]);
            }
        }
        @Test public void ownRequestThenDisableInTheSamePreFrameStillReturns() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f); float original=rotation(f).getEffectiveYaw(); int[] reached={0};
                me.zero.alpine.listener.Listener<me.youngjibbit.event.motion.EventMotion> late=new me.zero.alpine.listener.Listener<>(
                        me.youngjibbit.event.motion.EventMotion.class,e->{
                    if(e.isPre() && reached[0]++==0)f.run.fixture.aura.setToggled(false);
                },me.zero.alpine.event.EventPriority.LOWEST);
                Pulsar.BUS.subscribe(late);
                try { noActions(f.walk()); }
                finally { Pulsar.BUS.unsubscribe(late); }
                assertEquals(1,reached[0]);
                assertEquals("same-frame own offer must not force a camera snap",RotationManager.AURA_RELEASE_SOURCE,rotation(f).getConfirmedOwner());
                assertEquals(original,rotation(f).getEffectiveYaw(),.08);
            }
        }
        @Test public void scaffoldRotationToggleCannotBorrowItsPendingReturnForPlacement() throws Exception {
            try(ScaffoldOwnerContinuityTest.Run f=new ScaffoldOwnerContinuityTest.Run("Post")) {
                f.f.scaffold.getNumberSetting("Return Ticks").setValue(6D);int[] reached={0};
                me.zero.alpine.listener.Listener<me.youngjibbit.event.motion.EventMotion> beforePost=new me.zero.alpine.listener.Listener<>(
                        me.youngjibbit.event.motion.EventMotion.class,e->{
                    if(!e.isPost() || reached[0]!=0)return;
                    try {
                        PlacementTarget target=(PlacementTarget)ScaffoldOwnerContinuityTest.field(me.youngjibbit.module.impl.world.Scaffold.class,"frameTarget").get(f.f.scaffold);
                        ScaffoldPlacementService service=f.f.runtime.getScaffoldPlacementService();
                        target=service.alignAcceptedHit(target,f.rotation);
                        if(target==null)return;
                        assertTrue("positive control: valid real placement ray/stack",service.isValid(target));
                        reached[0]++;
                        f.f.scaffold.getBooleanSetting("Rotations").setValue(false);
                        assertTrue(f.rotation.hasRotationRelease("scaffold"));
                        assertFalse("pending return is not a camera placement right",service.place(target,f.rotation,false,
                                f.rotation.getMotionSequence(),0,true,true,false));
                        assertEquals(ScaffoldPlacementService.Rejection.ROTATION,service.getLastRejection());
                    } catch(Exception error) { throw new AssertionError(error); }
                },me.zero.alpine.event.EventPriority.HIGHEST);
                Pulsar.BUS.subscribe(beforePost);
                try { for(int i=0;i<20 && reached[0]==0;i++)noActions(f.walk()); }
                finally { Pulsar.BUS.unsubscribe(beforePost); }
                assertEquals(1,reached[0]);assertEquals(64,f.f.player.inventory.mainInventory[3].stackSize);
                for(int i=0;i<5;i++)noActions(f.walk());
            }
        }
        @SuppressWarnings("unchecked")
        @Test public void reenableInsideAuraReleaseCapturePreservesTheNewGeneration() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f);
                me.youngjibbit.core.observation.RuntimeObservation observation=f.run.fixture.runtime.getObservation();
                java.lang.reflect.Field field=ScaffoldOwnerContinuityTest.field(me.youngjibbit.core.observation.RuntimeObservation.class,"source");
                java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample> original=
                        (java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)field.get(observation);
                java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean();int[] reached={0};
                field.set(observation,(java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)()->{
                    if(once.compareAndSet(false,true)) {
                        f.run.fixture.aura.setToggled(true);f.walk();reached[0]++;
                        assertEquals("nested fresh Aura acceptance", "aura",rotation(f).getConfirmedOwner());
                    }
                    return original.get();
                });
                try {
                    f.run.fixture.aura.setToggled(false);assertEquals(1,reached[0]);
                    assertTrue(f.run.fixture.aura.isToggled());assertEquals("aura",rotation(f).getConfirmedOwner());
                    f.walk();assertEquals("new listener survives the old disable tail","aura",rotation(f).getConfirmedOwner());
                } finally { field.set(observation,original); }
            }
        }
        private static void noActions(List<Object> packets) {
            for(Object packet:packets) {
                assertFalse(packet instanceof C02PacketUseEntity);
                assertFalse(packet instanceof C08PacketPlayerBlockPlacement);
                assertFalse(packet instanceof C09PacketHeldItemChange);
                assertFalse(packet instanceof C0APacketAnimation);
            }
        }
        private static RotationManager rotation(Wave05AuraSelectionChecks.SelectionRun f) {
            return f.run.fixture.runtime.getRotationManager();
        }
        private static void acceptedAura(Wave05AuraSelectionChecks.SelectionRun f) {
            f.walk(); assertEquals("aura",rotation(f).getConfirmedOwner());
            assertTrue(Math.abs(rotation(f).getEffectiveYaw())>5);
        }
        private static void finishReturn(Wave05AuraSelectionChecks.SelectionRun f) {
            RotationManager r=rotation(f);
            for(int i=1;i<=6;i++) {
                noActions(f.walk());
                assertEquals(i<6?RotationManager.AURA_RELEASE_SOURCE:null,r.getConfirmedOwner());
                assertFalse(r.canUseRotation("aura",false));
            }
            assertEquals(f.local.rotationYaw,r.getEffectiveYaw(),0);
            assertEquals(f.local.rotationPitch,r.getEffectivePitch(),0);
        }
        @Test public void targetDeathReturnsToTheLiveCameraWithoutActions() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f); RotationManager r=rotation(f); float start=r.getEffectiveYaw();
                f.first.isDead=f.second.isDead=true; Pulsar.BUS.post(new TickEvent());
                assertTrue(r.hasRotationRelease("aura")); assertNull(r.getConfirmedOwner());
                for(int tick=1;tick<6;tick++) {
                    if(tick==2)f.local.rotationYaw=5; // Manual camera input remains live throughout the return.
                    noActions(f.walk()); assertEquals(RotationManager.AURA_RELEASE_SOURCE,r.getConfirmedOwner());
                    float expected=RotationReturnCurve.angle(start,f.local.rotationYaw,RotationReturnCurve.progress(tick/6f));
                    assertEquals(expected,r.getEffectiveYaw(),.08f);
                    assertFalse(r.canUseRotation("aura",false));
                }
                noActions(f.walk()); assertNull(r.getConfirmedOwner()); assertEquals(5,r.getEffectiveYaw(),0);
            }
        }
        @Test public void disableAndRotationToggleReturnWithoutRetainingAttackAuthority() throws Exception {
            for(boolean disable:new boolean[]{true,false})try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f);
                if(disable)f.run.fixture.aura.setToggled(false);
                else f.run.fixture.aura.getBooleanSetting("Rotations").setValue(false);
                finishReturn(f);
            }
        }
        @Test public void scaffoldUsesConfiguredDurationAndReturnsItsRenderedPose() throws Exception {
            try(ScaffoldOwnerContinuityTest.Run f=new ScaffoldOwnerContinuityTest.Run("Post")) {
                f.f.scaffold.getNumberSetting("Return Ticks").setValue(6D);
                f.f.scaffold.getModeSetting("Body Rotation").setValue("Direkt");
                f.walk(); float yaw=f.rotation.getEffectiveYaw();
                BodyRotationController.Pose before=RotationCorrections.renderPose(f.f.player,0);
                assertNotNull(before);
                f.f.scaffold.setToggled(false);
                BodyRotationController.Pose pending=RotationCorrections.renderPose(f.f.player,0);
                assertNotNull(pending); assertEquals(before.headYaw,pending.headYaw,0);
                assertEquals(before.bodyYaw,pending.bodyYaw,0);
                for(int tick=1;tick<6;tick++) {
                    noActions(f.walk()); assertEquals(RotationManager.SCAFFOLD_RELEASE_SOURCE,f.rotation.getConfirmedOwner());
                    assertEquals(RotationReturnCurve.angle(yaw,0,RotationReturnCurve.progress(tick/6f)),f.rotation.getEffectiveYaw(),.08);
                }
                noActions(f.walk()); assertNull(f.rotation.getConfirmedOwner()); assertNull(RotationCorrections.renderPose(f.f.player,0));
            }
        }
        @Test public void reenablePreemptsTheOldReturn() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f); f.run.fixture.aura.setToggled(false); noActions(f.walk());
                assertEquals(RotationManager.AURA_RELEASE_SOURCE,rotation(f).getConfirmedOwner());
                f.run.fixture.aura.setToggled(true); assertFalse(rotation(f).hasRotationRelease("aura"));
                f.walk(); assertEquals("aura",rotation(f).getConfirmedOwner());
            }
        }
        @Test public void rejectedReturnCannotGrantMovementOrActionAuthority() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f); f.run.fixture.aura.setToggled(false);
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C03PacketPlayer)e.cancel();};
                Pulsar.BUS.subscribe(hooks);
                try { noActions(f.walk()); assertNull(rotation(f).getConfirmedOwner()); assertFalse(rotation(f).canUseRotation(false)); }
                finally { Pulsar.BUS.unsubscribe(hooks); }
                noActions(f.walk()); assertEquals(RotationManager.AURA_RELEASE_SOURCE,rotation(f).getConfirmedOwner());
            }
        }
        @Test public void packetCallbackReenableInvalidatesTheOldReturnTicket() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f); f.run.fixture.aura.setToggled(false);
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                int[] reached={0}; hooks.packet=e->{if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C03PacketPlayer && reached[0]++==0)f.run.fixture.aura.setToggled(true);};
                Pulsar.BUS.subscribe(hooks);
                try { List<Object> packets=f.walk(); noActions(packets); assertEquals(0,packets.stream().filter(C03PacketPlayer.class::isInstance).count()); }
                finally { Pulsar.BUS.unsubscribe(hooks); }
                assertEquals(1,reached[0]); assertNull(rotation(f).getConfirmedOwner());
                f.walk(); assertEquals("aura",rotation(f).getConfirmedOwner());
            }
        }
        @Test public void sceneChangeAndReservedSourceCannotReviveReturn() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                acceptedAura(f); f.run.fixture.aura.setToggled(false); noActions(f.walk());
                RotationManager r=rotation(f); f.run.fixture.runtime.getObservation().invalidateScene();
                assertFalse(r.hasRotationRelease("aura")); assertNull(r.getConfirmedOwner());
                r.beginMotion(0,0);r.request(RotationManager.AURA_RELEASE_SOURCE,100,45,15);
                me.youngjibbit.event.motion.EventMotion event=new me.youngjibbit.event.motion.EventMotion(0,64,0,0,0,true);
                r.resolve(event);r.finishMotion(event.getYaw(),event.getPitch(),true);
                assertNull(r.getConfirmedOwner());assertEquals(0,event.getYaw(),0);
            }
        }
        @Test public void disabledSmoothingRestoresImmediateStop() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single")) {
                f.run.fixture.aura.getBooleanSetting("Smooth Rotation Stop").setValue(false);
                acceptedAura(f);f.run.fixture.aura.setToggled(false);noActions(f.walk());
                assertNull(rotation(f).getConfirmedOwner());assertEquals(0,rotation(f).getEffectiveYaw(),0);
            }
        }
    }
}
