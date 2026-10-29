package me.youngjibbit.core.combat;

import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.event.motion.EventMotion;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import static org.junit.Assert.*;

/** Exercises the immutable frame API with the canonical runtime and real selected-packet transport. */
public class ResolvedC03SendTicketTest {
    static Object ticket(RotationManager r) throws Exception {
        return RotationManager.class.getMethod("captureResolvedC03SendTicket",float.class,float.class).invoke(r,0f,0f);
    }
    @SuppressWarnings("unchecked") static Predicate<Packet<?>> guard(Object ticket,C03PacketPlayer packet) throws Exception {
        return (Predicate<Packet<?>>)ticket.getClass().getMethod("guard",C03PacketPlayer.class).invoke(ticket,packet);
    }
    static void set(Class<?> type,Object target,String name,Object value) throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    static RotationManager resolve(RotationBehaviorHookTest.Fixture f,float yaw,float pitch) {
        RotationManager r=f.runtime.getRotationManager();r.bindConnection(f.network.getClientPackets());
        r.beginMotion(f.player,f.mc.theWorld,0,0);r.request("scaffold",30,yaw,pitch);
        r.resolve(new EventMotion(0,64,0,0,0,true));return r;
    }
    static List<Object> state(Object object) throws Exception {
        List<Object> fields=new ArrayList<>();
        for(Field f:object.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())) {
            f.setAccessible(true);Object value=f.get(object);
            fields.add(f.getName());fields.add(value instanceof WeakReference?((WeakReference<?>)value).get():value instanceof Map?new HashMap<>((Map<?,?>)value):value);
        }
        return fields;
    }
    @Test public void t04PendingIsNotResolvedAndCaptureCannotResolveOrCreateAcceptance() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);f.runtime.getObservation().beginOuterTick();
            RotationManager r=f.runtime.getRotationManager();r.beginMotion(f.player,f.mc.theWorld,0,0);r.request("scaffold",30,30,10);
            assertNull(ticket(r));assertNull(r.getConfirmedOwner());assertFalse(r.isMotionAccepted());
            r.resolve(new EventMotion(0,64,0,0,0,true));assertNotNull(ticket(r));assertNull(r.getConfirmedOwner());assertNull(f.channel.readOutbound());
        }
    }
    @Test public void t05NullObserverCannotBecomeNeutralAfterARealResolve() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);RotationManager r=resolve(f,30,10);
            set(ClientRuntime.class,f.runtime,"observation",null);
            Object captured=ticket(r);assertNotNull("sourced invalid frame is a denial ticket, never neutral",captured);
            C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(30,10,true);
            assertNull(f.player.sendQueue.addToSendQueueTracked(p,guard(captured,p)));assertNull(f.channel.readOutbound());
        }
    }
    @Test public void t05LateCaptureCannotRebindAResolveToANewOuterTick() throws Exception {
        for(boolean initiallyActive:new boolean[]{false,true})try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);RuntimeObservation o=f.runtime.getObservation();
            if(initiallyActive)o.beginOuterTick();
            RotationManager r=resolve(f,30,10);o.endOuterTick();o.beginOuterTick();
            C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(30,10,true);
            assertFalse("capture must retain the original resolve lifetime",guard(ticket(r),p).test(p));assertNull(f.channel.readOutbound());
        }
    }
    @Test public void t07ObserverReplacementAndGenerationAbaCannotRestoreOldTicket() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);RuntimeObservation o=f.runtime.getObservation();o.beginOuterTick();
            RotationManager r=resolve(f,30,10);C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(30,10,true);Predicate<Packet<?>> g=guard(ticket(r),p);
            assertTrue(g.test(p));o.invalidateScene();assertFalse(g.test(p));o.captureScene();assertFalse(g.test(p));
            RuntimeObservation replacement=new RuntimeObservation(()->me.youngjibbit.core.observation.MinecraftSceneSource.capture(f.mc));replacement.beginOuterTick();
            set(ClientRuntime.class,f.runtime,"observation",replacement);assertFalse(g.test(p));
            set(ClientRuntime.class,f.runtime,"observation",o);assertFalse(g.test(p));assertNull(f.channel.readOutbound());
        }
    }
    @Test public void t10EvenSameTickReplayAfterFinishHasNoLateFramePermission() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);f.runtime.getObservation().beginOuterTick();
            RotationManager r=resolve(f,30,10);C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(30,10,true);Predicate<Packet<?>> g=guard(ticket(r),p);
            assertTrue(g.test(p));r.finishMotion(30,10,false);assertFalse(g.test(p));assertNull(f.player.sendQueue.addToSendQueueTracked(p,g));assertNull(f.channel.readOutbound());
        }
    }
    @Test public void t11WrappedHalfTurnToleranceAndNonRotatingWirePoseAreExplicit() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);f.runtime.getObservation().beginOuterTick();
            RotationManager r=resolve(f,180,10);C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(180,10,true);Predicate<Packet<?>> g=guard(ticket(r),p);
            assertTrue(g.test(new C03PacketPlayer.C05PacketPlayerLook(-180,10,true)));
            assertTrue(g.test(new C03PacketPlayer.C05PacketPlayerLook(180,10.0005f,true)));
            assertFalse(g.test(new C03PacketPlayer.C05PacketPlayerLook(180,10.0011f,true)));
            assertFalse(g.test(new C03PacketPlayer.C06PacketPlayerPosLook(0,64,0,180,10,true)));
            assertFalse(g.test(new C03PacketPlayer.C05PacketPlayerLook(Float.NaN,10,true)));
            r=resolve(f,0,0);p=new C03PacketPlayer(true);g=guard(ticket(r),p);assertTrue(g.test(new C03PacketPlayer(true)));
            r=resolve(f,30,10);g=guard(ticket(r),p);assertFalse(g.test(p));
        }
    }
    @Test public void t15OwnResetAndSameNumbersInANewResolveCannotReviveOldGuard() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);f.runtime.getObservation().beginOuterTick();
            RotationManager r=resolve(f,30,10);C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(30,10,true);Predicate<Packet<?>> old=guard(ticket(r),p);
            r.reset("aura");assertTrue(old.test(p));r.reset("scaffold");r.request("scaffold",30,30,10);r.resolve(new EventMotion(0,64,0,0,0,true));
            assertFalse(old.test(p));assertTrue(guard(ticket(r),p).test(p));
            r.resolve(new EventMotion(0,64,0,0,0,true));assertFalse(old.test(p));
        }
    }
    @Test public void t16ValidationThrowPropagatesBeforeTransportAndReentryIsRechecked() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);RuntimeObservation o=f.runtime.getObservation();o.beginOuterTick();
            RotationManager r=resolve(f,30,10);C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(30,10,true);Predicate<Packet<?>> g=guard(ticket(r),p);
            IllegalStateException failure=new IllegalStateException("scene validation");
            set(RuntimeObservation.class,o,"source",(Supplier<RuntimeObservation.SceneSample>)()->{throw failure;});
            assertSame(failure,assertThrows(IllegalStateException.class,()->f.player.sendQueue.addToSendQueueTracked(p,g)));assertNull(f.channel.readOutbound());
            set(RuntimeObservation.class,o,"source",(Supplier<RuntimeObservation.SceneSample>)()->{r.beginMotion(f.player,f.mc.theWorld,0,0);return me.youngjibbit.core.observation.MinecraftSceneSource.capture(f.mc);});
            assertFalse(g.test(p));assertNull(f.channel.readOutbound());
        }
    }
    @Test public void t17HundredPermissionReadsPreserveOwnerRequestTickStepQuotaAndTransport() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);RuntimeObservation o=f.runtime.getObservation();o.beginOuterTick();
            o.observePlayerStep(f.player,()->{
                try {
                    RotationManager r=resolve(f,30,10);C03PacketPlayer p=new C03PacketPlayer.C05PacketPlayerLook(30,10,true);Object t=ticket(r);Predicate<Packet<?>> g=guard(t,p);
                    List<Object> before=state(r);Object step=o.getCurrentStep(),steps=o.getLastSteps(),withheld=o.getLastWithheld();long tick=o.getActiveOuterTickId();
                    for(int i=0;i<100;i++){assertTrue(g.test(p));assertTrue(guard(ticket(r),p).test(p));assertEquals(before,state(r));assertEquals(tick,o.getActiveOuterTickId());assertSame(step,o.getCurrentStep());assertSame(steps,o.getLastSteps());assertSame(withheld,o.getLastWithheld());assertEquals(0,f.network.getClientPackets().delays().size());assertNull(r.getConfirmedOwner());assertNull(f.channel.readOutbound());}
                    return true;
                }catch(Exception e){throw new AssertionError(e);}
            },body->body.getAsBoolean());
            assertEquals(1,o.getLastSteps().size());o.endOuterTick();assertEquals(-1,o.getActiveOuterTickId());
        }
    }
}
