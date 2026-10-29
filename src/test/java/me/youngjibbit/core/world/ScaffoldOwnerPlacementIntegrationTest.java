package me.youngjibbit.core.world;

import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Test;
import java.lang.reflect.*;
import static org.junit.Assert.*;

/** Real module admission/service/late guard with the existing deterministic geometry Access. */
public class ScaffoldOwnerPlacementIntegrationTest {
    private static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
        final ScaffoldPlacementTest.Fixture p=new ScaffoldPlacementTest.Fixture();
        final RuntimeObservation observer=f.runtime.getObservation();
        Run() throws Exception {
            ScaffoldOwnerContinuityTest.field(ClientRuntime.class,"scaffoldPlacementService").set(f.runtime,p.service);
            ScaffoldOwnerContinuityTest.field(ClientRuntime.class,"rotationManager").set(f.runtime,p.rotations);
            observer.beginOuterTick(); p.align();
            ScaffoldOwnerContinuityTest.field(Scaffold.class,"frameTarget").set(f.scaffold,p.plan);
        }
        void accept(String source,boolean pre,boolean accepted) {
            float yaw=p.rotations.getEffectiveYaw(),pitch=p.rotations.getEffectivePitch();
            p.rotations.beginMotion(0,0);
            if(source!=null) p.rotations.request(source,30,yaw,pitch);
            p.rotations.finishMotion(yaw,pitch,accepted);
            if(pre) p.rotations.beginMotion(0,0);
        }
        boolean attempt(boolean pre) {
            final boolean[] result={false};
            observer.observePlayerStep(f.player,()->{
                try {
                    ScaffoldOwnerContinuityTest.field(Scaffold.class,"frameSequence").setLong(f.scaffold,p.rotations.getMotionSequence());
                    Method method=Scaffold.class.getDeclaredMethod("tryPlace",boolean.class); method.setAccessible(true);
                    result[0]=(Boolean)method.invoke(f.scaffold,pre); return true;
                } catch(InvocationTargetException error) {
                    if(error.getCause() instanceof RuntimeException) throw (RuntimeException)error.getCause();
                    throw new AssertionError(error.getCause());
                } catch(Exception error) { throw new AssertionError(error); }
            },step->step.getAsBoolean());
            return result[0];
        }
        @Override public void close() throws Exception { observer.reset(); f.close(); }
    }
    @Test public void a06CameraAndAuraNumbersCannotAuthorizeOnBeforeSlotOrBudget() throws Exception {
        for(String source:new String[]{null,"aura"}) for(boolean pre:new boolean[]{false,true}) try(Run r=new Run()) {
            r.accept(source,pre,true); assertTrue(r.p.rotations.canUseRotation(pre));
            assertFalse("Rotation ON requires scaffold",r.attempt(pre));
            assertEquals(0,r.p.world.clicks); assertTrue(r.p.world.slots.isEmpty());
            assertEquals(ScaffoldPlacementService.Rejection.ROTATION,r.p.service.getLastRejection());
            r.accept("scaffold",false,true); assertTrue("denial consumed no tick budget",r.attempt(false));
        }
    }
    @Test public void a05PendingRejectedRewrittenAndWrongPhaseNeverAuthorizeOn() throws Exception {
        for(int mode=0;mode<4;mode++) try(Run r=new Run()) {
            if(mode==0) { r.p.rotations.reset(); r.p.rotations.beginMotion(0,0); r.p.rotations.request("scaffold",30,0,0); }
            if(mode==1) r.accept("scaffold",false,false);
            if(mode==2) r.p.rotations.observeOutboundRotation(-40,10);
            if(mode==3) r.accept("scaffold",false,true);
            assertFalse(r.attempt(mode==0 || mode==3)); assertEquals(0,r.p.world.clicks);
        }
    }
    @Test public void a06SourceIsRecheckedAfterSlotSynchronizationAndInsideControllerGuard() throws Exception {
        for(boolean duringClick:new boolean[]{false,true}) try(Run r=new Run()) {
            Runnable revoke=()->r.p.rotations.reset("scaffold");
            if(duringClick) r.p.world.onClick=revoke; else r.p.world.onSync=revoke;
            assertFalse(r.attempt(false)); assertEquals(0,r.p.world.clicks);
            assertEquals(0,r.p.world.selected); assertEquals(0,r.p.world.serverSlot);
            assertEquals(ScaffoldPlacementService.Rejection.LATE_CHANGE,r.p.service.getLastRejection());
        }
    }
    @Test public void a07OffRetainsActuallyAcceptedUnownedCameraForBothPhases() throws Exception {
        for(boolean pre:new boolean[]{false,true}) try(Run r=new Run()) {
            r.f.scaffold.getBooleanSetting("Rotations").setValue(false);
            ScaffoldOwnerContinuityTest.field(Scaffold.class,"frameTarget").set(r.f.scaffold,r.p.plan);
            r.accept(null,pre,true); assertNull(r.p.rotations.getConfirmedOwner());
            assertTrue(r.attempt(pre)); assertEquals(1,r.p.world.clicks);
        }
    }
    @Test public void a08SlotStackRangeAndRayChangesRetainTheirGuardsWithOnOwnership() throws Exception {
        for(int mode=0;mode<4;mode++) try(Run r=new Run()) {
            if(mode==0) r.p.world.selected=2;
            if(mode==1) r.p.world.inventory[3]=r.p.world.inventory[3].copy();
            if(mode==2) r.p.world.reach=.1;
            if(mode==3) r.p.world.wireEyes=new net.minecraft.util.Vec3(20,65.62,0);
            assertFalse(r.attempt(false)); assertEquals(0,r.p.world.clicks); assertTrue(r.p.world.slots.isEmpty());
        }
    }
    @Test public void a16ManualSlotAndExceptionCleanupStayIntact() throws Exception {
        try(Run r=new Run()) {
            r.p.world.onClick=()->r.p.world.selected=7;
            assertFalse(r.attempt(false)); assertEquals(7,r.p.world.selected); assertEquals(0,r.p.world.clicks);
        }
        try(Run r=new Run()) {
            r.p.world.throwClick=true;
            assertThrows(IllegalStateException.class,()->r.attempt(false));
            assertEquals(0,r.p.world.selected); assertEquals(0,r.p.world.serverSlot);
            assertNull(r.observer.getCurrentStep()); assertTrue(r.observer.getLastSteps().get(0).isInterrupted());
        }
    }
    @Test public void a16EpochChangeInCallbackCannotClickOrRestoreIntoReplacementScene() throws Exception {
        try(Run r=new Run()) {
            r.p.world.onSync=()->r.f.network.getClientPackets().worldChanged();
            assertFalse(r.attempt(false)); assertEquals(0,r.p.world.clicks);
            assertEquals("no cross-epoch slot restore",3,r.p.world.selected);
            assertEquals(1,r.p.world.slots.size());
        }
    }
}
