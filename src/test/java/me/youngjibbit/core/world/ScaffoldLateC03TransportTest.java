package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

/** Real walking, real Scaffold and selected-packet Apply; only geometry/input are fixture data. */
public class ScaffoldLateC03TransportTest {
    static final class Hooks implements Subscriber {
        Consumer<EventPacket> packet=e->{};
        Consumer<EventMotion> motion=e->{};
        @Subscribe final Listener<EventPacket> packets=new Listener<>(e->packet.accept(e));
        @Subscribe final Listener<EventMotion> motions=new Listener<>(e->motion.accept(e));
    }
    static boolean movementPre(EventPacket e) { return e.isPre() && e.isOutbound() && e.getPacket() instanceof C03PacketPlayer; }
    static void noMovementOrActions(List<Object> packets) {
        for(Object p:packets) {
            assertFalse("stale C03",p instanceof C03PacketPlayer);
            assertFalse("no C08",p instanceof C08PacketPlayerBlockPlacement);
            assertFalse("no C09",p instanceof C09PacketHeldItemChange);
            assertFalse("no C02",p instanceof C02PacketUseEntity);
            assertFalse("no C0E",p instanceof C0EPacketClickWindow);
        }
    }
    static void denied(ScaffoldOwnerContinuityTest.Run r,List<Object> packets) {
        noMovementOrActions(packets); assertFalse(r.rotation.isMotionAccepted());
        assertNull(r.rotation.getConfirmedOwner()); assertEquals(64,r.f.player.inventory.mainInventory[3].stackSize);
        assertEquals(0,r.f.player.inventory.currentItem);
    }
    static void withHook(Hooks hook,Runnable body) {
        Pulsar.BUS.subscribe(hook);try { body.run(); }finally { Pulsar.BUS.unsubscribe(hook); }
    }
    @Test public void t01OriginalGuiAndDisablePreRejectsNinePointNineDegreeLook() throws Exception {
        try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            r.f.scaffold.getNumberSetting("Yaw Speed").setValue(5D);r.f.scaffold.getNumberSetting("Pitch Speed").setValue(5D);
            r.walk(); assertEquals("scaffold",r.rotation.getConfirmedOwner());
            Hooks h=new Hooks();int[] reached={0};
            h.packet=e->{if(movementPre(e)) {
                C03PacketPlayer p=(C03PacketPlayer)e.getPacket();assertTrue(p.getRotating());
                assertEquals(9.900001f,p.getYaw(),.000001f);assertEquals(0,r.f.player.rotationYaw,0);
                r.f.mc.currentScreen=new GuiChat();r.f.scaffold.setToggled(false);reached[0]++;
            }};
            withHook(h,()->denied(r,r.walk()));assertEquals(1,reached[0]);
        }
    }
    @Test public void t02DisableAloneRejectsTheResolvedRequest() throws Exception {
        try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            Hooks h=new Hooks();h.packet=e->{if(movementPre(e))r.f.scaffold.setToggled(false);};
            withHook(h,()->denied(r,r.walk()));assertNull(r.f.mc.currentScreen);
        }
    }
    @Test public void t03EachLateSuspensionRejectsButUnchangedActiveFrameAccepts() throws Exception {
        for(int scenario=0;scenario<3;scenario++)try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            final int s=scenario;Hooks h=new Hooks();h.packet=e->{if(movementPre(e)) {
                if(s==0)r.f.mc.currentScreen=new GuiChat();
                if(s==1)r.f.mc.inGameHasFocus=false;
                if(s==2)try { ScaffoldOwnerContinuityTest.field(Minecraft.class,"isGamePaused").setBoolean(r.f.mc,true); } catch(Exception x) {throw new AssertionError(x);}
            }};
            withHook(h,()->denied(r,r.walk()));
        }
        try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            assertEquals(1,r.walk().stream().filter(p->p instanceof C03PacketPlayer).count());assertEquals("scaffold",r.rotation.getConfirmedOwner());
        }
    }
    @Test public void t04FirstRequestAtRealOuterIdZeroConfirmsOnlyAfterTransport() throws Exception {
        try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            Hooks h=new Hooks();int[] calls={0};h.packet=e->{if(movementPre(e)) {
                assertEquals(0,r.f.runtime.getObservation().getActiveOuterTickId());assertNotNull(r.f.runtime.getObservation().getCurrentStep());
                assertNull(r.rotation.getConfirmedOwner());assertFalse(r.rotation.isMotionAccepted());calls[0]++;
            }};
            withHook(h,()->assertEquals(1,r.walk().stream().filter(p->p instanceof C03PacketPlayer).count()));
            assertEquals(1,calls[0]);assertEquals("scaffold",r.rotation.getConfirmedOwner());
            assertEquals(-1,r.f.runtime.getObservation().getActiveOuterTickId());
        }
    }
    @Test public void t05OriginalMissingOuterAndHistoricalTickNeverGrantScaffoldTransport() throws Exception {
        for(boolean historical:new boolean[]{false,true})try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            RuntimeObservation o=r.f.runtime.getObservation();
            if(historical) {o.beginOuterTick();o.endOuterTick();o.beginOuterTick();o.endOuterTick();assertEquals(1,o.getOuterTickId());}
            assertEquals(-1,o.getActiveOuterTickId());r.f.player.onUpdateWalkingPlayer();denied(r,r.drain());
        }
    }
    @Test public void t05NullInvalidatedAndSuspendedObserverNeverGrantSyntheticTransport() throws Exception {
        for(int scenario=0;scenario<3;scenario++)try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);f.runtime.getObservation().beginOuterTick();
            if(scenario==0)ScaffoldOwnerContinuityTest.field(ClientRuntime.class,"observation").set(f.runtime,null);
            if(scenario==1)f.runtime.getObservation().reset();
            if(scenario==2)f.mc.currentScreen=new GuiChat();
            Hooks h=new Hooks();h.motion=e->{if(e.isPre())f.runtime.getRotationManager().request("scaffold",30,20,10);};
            withHook(h,f.player::onUpdateWalkingPlayer);
            assertNull(f.channel.readOutbound());assertFalse(f.runtime.getRotationManager().isMotionAccepted());
        }
    }
    @Test public void t06LivePlayerWorldConnectionPipelineAndEpochChangesRejectSamePose() throws Exception {
        for(int scenario=0;scenario<5;scenario++)try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            final int s=scenario;Hooks h=new Hooks();h.packet=e->{if(movementPre(e))try {
                if(s==0)r.f.mc.thePlayer=null;
                if(s==1)r.f.mc.theWorld=r.f.world;
                if(s==2)ScaffoldOwnerContinuityTest.field(EntityPlayerSP.class,"sendQueue").set(r.f.player,new NetHandlerPlayClient(null,null,r.f.network,null));
                if(s==3)r.f.network.configureClientPackets(new PacketFlowTest.MainThread());
                if(s==4)r.f.network.getClientPackets().worldChanged();
            }catch(Exception x){throw new AssertionError(x);}};
            withHook(h,()->noMovementOrActions(r.walk()));assertNull(r.rotation.getConfirmedOwner());
        }
    }
    @Test public void t07GenerationShutdownDeathAndSampledIdentityAbaRejectOldCapture() throws Exception {
        for(int scenario=0;scenario<4;scenario++)try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            final int s=scenario;Hooks h=new Hooks();h.packet=e->{if(movementPre(e)) {
                RuntimeObservation o=r.f.runtime.getObservation();
                if(s==0)o.invalidateScene();if(s==1)r.f.mc.shutdown();if(s==2)r.f.player.isDead=true;
                if(s==3) {r.f.mc.theWorld=r.f.world;o.captureScene();r.f.mc.theWorld=r.scene;o.captureScene();}
            }};
            withHook(h,()->denied(r,r.walk()));
        }
    }
    @Test public void t08OwnResetFullResetAndManagerReplacementRevokeButForeignResetDoesNot() throws Exception {
        for(int scenario=0;scenario<4;scenario++)try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            final int s=scenario;Hooks h=new Hooks();h.packet=e->{if(movementPre(e)) {
                if(s==0)r.rotation.reset("scaffold");if(s==1)r.rotation.reset();if(s==3)r.rotation.reset("aura");
                if(s==2)try {ScaffoldOwnerContinuityTest.field(ClientRuntime.class,"rotationManager").set(r.f.runtime,new RotationManager());}catch(Exception x){throw new AssertionError(x);}
            }};
            withHook(h,()->{List<Object> packets=r.walk();if(s==3){assertEquals(1,packets.size());assertEquals("scaffold",r.rotation.getConfirmedOwner());}else denied(r,packets);});
        }
    }
    @Test public void t09PacketCancelAndMotionCancelPreserveTheirDistinctPostContracts() throws Exception {
        for(boolean motion:new boolean[]{false,true})try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            Hooks h=new Hooks();int[] post={0};h.packet=e->{if(!motion && movementPre(e))e.cancel();};
            h.motion=e->{if(e.isPre() && motion)e.cancel();if(e.isPost())post[0]++;};
            withHook(h,()->denied(r,r.walk()));assertEquals(motion?0:1,post[0]);
        }
    }
    @Test public void t10DelayedApplyAndActionBarrierCannotReviveFinishedFrame() throws Exception {
        for(int scenario=0;scenario<3;scenario++)try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            r.f.network.getClientPackets().delays().request("ticket-test",200,(p,flow)->p instanceof C03PacketPlayer);
            denied(r,r.walk());assertEquals(1,r.f.network.getClientPackets().delays().size());
            if(scenario==0)r.f.runtime.getObservation().beginOuterTick();
            if(scenario==1)r.f.scaffold.setToggled(false);
            if(scenario==2)r.f.network.getClientPackets().worldChanged();
            // The existing dependency barrier is driven by an actual subsequent action.
            C0BPacketEntityAction action=new C0BPacketEntityAction(r.f.player,C0BPacketEntityAction.Action.START_SPRINTING);
            r.f.player.sendQueue.addToSendQueueTracked(action);
            List<Object> out=r.drain();assertEquals(Collections.singletonList(action),out);assertNull(r.rotation.getConfirmedOwner());
            r.f.runtime.getObservation().endOuterTick();
        }
    }
    @Test public void t11SelectedRewriteChecksPoseClassAndExistingAngularEquivalence() throws Exception {
        for(int scenario=0;scenario<5;scenario++)try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            final int s=scenario;Hooks h=new Hooks();h.packet=e->{if(movementPre(e)) {
                C03PacketPlayer p=(C03PacketPlayer)e.getPacket();
                if(s==0)e.setPacket(new C03PacketPlayer.C05PacketPlayerLook(p.getYaw()+2,p.getPitch(),p.isOnGround()));
                if(s==1)e.setPacket(new C03PacketPlayer.C05PacketPlayerLook(p.getYaw(),p.getPitch()+2,p.isOnGround()));
                if(s==2)e.setPacket(new C0BPacketEntityAction(r.f.player,C0BPacketEntityAction.Action.START_SPRINTING));
                if(s==3)e.setPacket(new C03PacketPlayer.C05PacketPlayerLook(p.getYaw()+360,p.getPitch(),p.isOnGround()));
                if(s==4)e.setPacket(new C03PacketPlayer.C05PacketPlayerLook(p.getYaw()+.0005f,p.getPitch(),p.isOnGround()));
            }};
            withHook(h,()->{List<Object> packets=r.walk();if(s<3){assertTrue(packets.isEmpty());assertFalse(r.rotation.isMotionAccepted());}else{assertEquals(1,packets.size());assertEquals("scaffold",r.rotation.getConfirmedOwner());}});
        }
    }
    @Test public void t10NormalDelayedReleaseCannotReplayAnEndedWalkingCapture() throws Exception {
        try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            r.f.network.getClientPackets().delays().request("normal-release",200,(p,flow)->p instanceof C03PacketPlayer);
            denied(r,r.walk());assertEquals(1,r.f.network.getClientPackets().delays().size());
            r.f.network.getClientPackets().delays().release("normal-release");
            assertTrue(r.drain().isEmpty());assertEquals(0,r.f.network.getClientPackets().delays().size());assertNull(r.rotation.getConfirmedOwner());
        }
    }
    @Test public void t12VanillaAndT14OtherOwnersRemainNeutralWithoutAnObserverTick() throws Exception {
        for(String source:new String[]{null,"aura","aimassist"})try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f,.5,.48);f.player.rotationYaw=25;
            Hooks h=new Hooks();h.motion=e->{if(e.isPre() && source!=null)f.runtime.getRotationManager().request(source,source.equals("aura")?20:10,30,10);};
            withHook(h,f.player::onUpdateWalkingPlayer);assertTrue(f.channel.readOutbound() instanceof C03PacketPlayer);
            assertNull(f.channel.readOutbound());assertTrue(f.runtime.getRotationManager().isMotionAccepted());assertEquals(source,f.runtime.getRotationManager().getConfirmedOwner());
        }
    }
    @Test public void t15IdenticalRequestAfterReenableOrNewFrameCannotReauthorizeOldPacket() throws Exception {
        for(boolean reenable:new boolean[]{false,true})try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            Hooks h=new Hooks();h.packet=e->{if(movementPre(e)) {
                C03PacketPlayer p=(C03PacketPlayer)e.getPacket();
                if(reenable){r.f.scaffold.setToggled(false);r.f.scaffold.setToggled(true);}
                r.rotation.beginMotion(r.f.player,r.scene,0,0);r.rotation.request("scaffold",30,p.getYaw(),p.getPitch());
                EventMotion newer=new EventMotion(0,64,0,0,0,true);r.rotation.resolve(newer);
            }};
            withHook(h,()->noMovementOrActions(r.walk()));assertNull(r.rotation.getConfirmedOwner());
        }
    }
    @Test public void t16NestedWalkingCannotSendOrFinishTheOuterStaleFrameTwice() throws Exception {
        try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            Hooks h=new Hooks();boolean[] nested={false};int[] post={0};
            h.packet=e->{if(movementPre(e) && !nested[0]) {nested[0]=true;r.f.player.onUpdateWalkingPlayer();}};
            h.motion=e->{if(e.isPost())post[0]++;};
            withHook(h,()->{List<Object> packets=r.walk();assertEquals(1,packets.stream().filter(p->p instanceof C03PacketPlayer).count());});
            assertEquals(1,post[0]);assertEquals("scaffold",r.rotation.getConfirmedOwner());
        }
    }
    @Test public void t16PacketPreThrowPreservesThrowableAndObserverFinallyWithoutTransport() throws Exception {
        try(ScaffoldOwnerContinuityTest.Run r=new ScaffoldOwnerContinuityTest.Run("Post")) {
            Hooks h=new Hooks();IllegalStateException failure=new IllegalStateException("packet callback");int[] post={0};
            h.packet=e->{if(movementPre(e))throw failure;};h.motion=e->{if(e.isPost())post[0]++;};
            withHook(h,()->assertSame(failure,assertThrows(IllegalStateException.class,r::walk)));
            assertTrue(r.drain().isEmpty());assertEquals(0,post[0]);assertFalse(r.rotation.isMotionAccepted());
            assertEquals(-1,r.f.runtime.getObservation().getActiveOuterTickId());assertNull(r.f.runtime.getObservation().getCurrentStep());
            assertTrue(r.f.runtime.getObservation().getLastSteps().get(0).isInterrupted());
        }
    }
}
