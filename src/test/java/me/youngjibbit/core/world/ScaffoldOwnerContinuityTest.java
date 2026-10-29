package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Actual walking/Scaffold/native controller and embedded transport; synthetic world/input only. */
public class ScaffoldOwnerContinuityTest {
    static Field field(Class<?> type, String name) throws Exception {
        Field f=type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
        final ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.48);
        final RotationManager rotation=f.runtime.getRotationManager();
        Run(String timing) throws Exception {
            f.scaffold.getModeSetting("Randomization Style").setValue("Aus");
            f.scaffold.getModeSetting("Place Timing").setValue(timing);
            f.scaffold.onEnable();
        }
        List<Object> drain() {
            List<Object> packets=new ArrayList<>(); Object p;
            while((p=f.channel.readOutbound())!=null) packets.add(p);
            return packets;
        }
        List<Object> walk() {
            scene.motionTime.addAndGet(50_000_000L);
            f.runtime.getScaffoldPlacementService().beginTick();
            ScaffoldClientFlowTest.ordinaryWalking(f);
            return drain();
        }
        List<Object> complete(BlockPos destination) {
            List<Object> packets=Collections.emptyList();
            for(int i=0;i<20 && scene.getBlockState(destination).getBlock()!=Blocks.stone;i++) packets=walk();
            assertEquals("native placement completes",Blocks.stone,scene.getBlockState(destination).getBlock());
            return packets;
        }
        void noSupport() { scene.blocks.clear(); }
        @Override public void close() throws Exception { f.scaffold.onDisable(); f.close(); }
    }
    static final class Hooks implements Subscriber {
        java.util.function.Consumer<EventMotion> motion=e->{};
        java.util.function.Consumer<EventPacket> packet=e->{};
        @Subscribe final Listener<EventMotion> motions=new Listener<>(e->motion.accept(e));
        @Subscribe final Listener<EventPacket> packets=new Listener<>(e->packet.accept(e));
    }
    static void noActions(List<Object> packets) {
        for(Object p:packets) {
            assertFalse("no automatic C08",p instanceof C08PacketPlayerBlockPlacement);
            assertFalse("no automatic C09",p instanceof C09PacketHeldItemChange);
        }
    }
    @Test public void a01TwoNativePlanCyclesAndIdleKeepAcceptedOwnerForBothTimings() throws Exception {
        for(String timing:new String[]{"Pre","Post"}) try(Run r=new Run(timing)) {
            for(int cycle=0;cycle<2;cycle++) {
                if(cycle==1) {
                    r.f.player.posX=1.5;
                    r.f.player.setEntityBoundingBox(new AxisAlignedBB(1.2,64,.2,1.8,65.8,.8));
                }
                r.complete(new BlockPos(cycle,63,0));
                assertNull(field(Scaffold.class,"currentTarget").get(r.f.scaffold));
                assertNull(field(Scaffold.class,"frameTarget").get(r.f.scaffold));
                assertEquals("accepted owner after "+timing+" success","scaffold",r.rotation.getConfirmedOwner());
                float yaw=r.rotation.getEffectiveYaw(),pitch=r.rotation.getEffectivePitch();
                for(int idle=0;idle<3;idle++) {
                    noActions(r.walk()); assertTrue(r.rotation.canUseRotation("scaffold",false));
                    assertEquals(yaw,r.rotation.getEffectiveYaw(),0); assertEquals(pitch,r.rotation.getEffectivePitch(),0);
                }
            }
            assertEquals(62,r.f.player.inventory.mainInventory[3].stackSize);
        }
    }
    @Test public void a02HoldBeatsAuraInBothListenerOrdersOnlyAfterAcceptance() throws Exception {
        for(boolean first:new boolean[]{false,true}) try(Run r=new Run("Post")) {
            r.walk(); r.noSupport();
            Hooks hooks=new Hooks(); hooks.motion=e->{
                if(e.isPre()) {
                    assertTrue(r.rotation.canUseRotation("scaffold",true));
                    assertFalse(r.rotation.canUseRotation("aura",true));
                    r.rotation.request("aura",RotationManager.AURA_PRIORITY,-40,10);
                }
            };
            if(first) { Pulsar.BUS.unsubscribe(r.f.scaffold); Pulsar.BUS.subscribe(hooks); Pulsar.BUS.subscribe(r.f.scaffold); }
            else Pulsar.BUS.subscribe(hooks);
            try { noActions(r.walk()); assertEquals("scaffold",r.rotation.getConfirmedOwner()); }
            finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }
    @Test public void a03FirstPlanlessFrameNeverInventsAnOwner() throws Exception {
        try(Run r=new Run("Pre")) { r.noSupport(); noActions(r.walk()); assertNull(r.rotation.getConfirmedOwner()); }
    }
    @Test public void a04PreSuccessStillRenewsRequestForTheFollowingC03() throws Exception {
        try(Run r=new Run("Pre")) {
            List<Object> packets=r.complete(new BlockPos(0,63,0));
            int place=-1,movement=-1;
            for(int i=0;i<packets.size();i++) {
                if(packets.get(i) instanceof C08PacketPlayerBlockPlacement) place=i;
                if(packets.get(i) instanceof C03PacketPlayer) movement=i;
            }
            assertTrue("PRE placement precedes this frame's C03",place>=0 && movement>place);
            assertTrue("early return must renew scaffold",r.rotation.canUseRotation("scaffold",false));
        }
    }
    @Test public void a09PlanlessHoldInExtraAndNoStepCannotEmitPlacementOrSlotPackets() throws Exception {
        try(Run r=new Run("Post")) {
            r.complete(new BlockPos(0,63,0)); r.drain();
            me.youngjibbit.core.observation.RuntimeObservation o=r.f.runtime.getObservation();
            o.beginOuterTick();
            try {
                o.observePlayerStep(r.f.player,()->{
                    if(o.getCurrentStep().getKind()==ExecutedStep.Kind.EARNED_EXTRA) {
                        r.f.player.onUpdateWalkingPlayer(); noActions(r.drain());
                        assertTrue(r.rotation.canUseRotation("scaffold",false));
                    }
                    return true;
                },step->{step.getAsBoolean();step.getAsBoolean();});
                o.observePlayerStep(r.f.player,()->{fail("withheld has no body");return true;},step->{});
                assertNotNull(o.getLastWithheld());
                r.f.player.onUpdateWalkingPlayer(); noActions(r.drain());
            } finally { o.endOuterTick(); }
            assertEquals(63,r.f.player.inventory.mainInventory[3].stackSize);
        }
    }
    @Test public void a10GuiPauseAndFocusSuspendWithoutRequestsAndResumeRevalidates() throws Exception {
        for(int mode=0;mode<3;mode++) try(Run r=new Run("Post")) {
            r.walk(); assertTrue(r.rotation.canUseRotation("scaffold",false));
            if(mode==0) r.f.mc.currentScreen=new GuiChat();
            if(mode==1) r.f.mc.inGameHasFocus=false;
            if(mode==2) field(Minecraft.class,"isGamePaused").setBoolean(r.f.mc,true);
            noActions(r.walk()); assertTrue(r.f.scaffold.isToggled());
            assertNull("suspended frames make no scaffold request",r.rotation.getConfirmedOwner());
            assertEquals(1,r.f.mc.timer.timerSpeed,0);
            r.f.mc.currentScreen=null; r.f.mc.inGameHasFocus=true;
            field(Minecraft.class,"isGamePaused").setBoolean(r.f.mc,false);
            r.complete(new BlockPos(0,63,0)); assertTrue(r.rotation.canUseRotation("scaffold",false));
        }
    }
    @Test public void a11EpochInvalidatesOldHoldWithoutRestoringAForeignTimer() throws Exception {
        try(Run r=new Run("Post")) {
            r.f.scaffold.getNumberSetting("Timer").setValue(1.6D); r.walk();
            assertEquals(1.6,r.f.mc.timer.timerSpeed,.0001);
            r.f.mc.timer.timerSpeed=2.2f; r.f.network.getClientPackets().worldChanged(); r.noSupport();
            Pulsar.BUS.post(new TickEvent());
            assertNull(r.rotation.getConfirmedOwner()); assertEquals(2.2,r.f.mc.timer.timerSpeed,.0001);
            noActions(r.walk()); assertNull(r.rotation.getConfirmedOwner());
        }
    }
    @Test public void a13EpochCleanupReleasesOnlyTheSameOwnedTimerObject() throws Exception {
        for(boolean replaced:new boolean[]{false,true}) try(Run r=new Run("Post")) {
            r.f.scaffold.getNumberSetting("Timer").setValue(1.6D); r.walk();
            assertEquals(1.6,r.f.mc.timer.timerSpeed,.0001);
            if(replaced) { r.f.mc.timer=new net.minecraft.util.Timer(20,()->0); r.f.mc.timer.timerSpeed=2.2f; }
            r.f.network.getClientPackets().worldChanged(); Pulsar.BUS.post(new TickEvent());
            assertEquals(replaced?2.2:1,r.f.mc.timer.timerSpeed,.0001);
            assertNull(r.rotation.getConfirmedOwner());
        }
    }
    @Test public void a11PlayerWorldConnectionPipelineAndDeathInvalidateOldBasis() throws Exception {
        for(int mode=0;mode<5;mode++) try(Run r=new Run("Post")) {
            r.walk(); assertTrue(r.rotation.canUseRotation("scaffold",false));
            if(mode==0) r.f.mc.thePlayer=null;
            if(mode==1) r.f.mc.theWorld=r.f.world;
            if(mode==2) field(net.minecraft.client.entity.EntityPlayerSP.class,"sendQueue").set(r.f.player,null);
            if(mode==3) r.f.network.configureClientPackets(new me.youngjibbit.core.network.PacketFlowTest.MainThread());
            if(mode==4) r.f.player.isDead=true;
            Pulsar.BUS.post(new TickEvent());
            assertNull("boundary "+mode,r.rotation.getConfirmedOwner());
            assertNull(field(Scaffold.class,"currentTarget").get(r.f.scaffold));
            r.f.player.motionX=r.f.player.motionZ=0; r.f.player.moveFlying(0,1,.1f);
            assertEquals(0,r.f.player.motionX,3e-5); assertEquals(.1,r.f.player.motionZ,3e-5);
            noActions(r.drain());
        }
    }
    @Test public void a12DisableReleasesImmediatelyAndCannotPlaceAgain() throws Exception {
        try(Run r=new Run("Post")) {
            r.walk(); r.f.scaffold.setToggled(false);
            assertNull(r.rotation.getConfirmedOwner()); noActions(r.walk());
            assertEquals(64,r.f.player.inventory.mainInventory[3].stackSize);
        }
    }
    @Test public void a11ObserverGenerationBoundaryCannotReviveAnOldPlanlessOwner() throws Exception {
        try(Run r=new Run("Post")) {
            r.walk(); r.noSupport(); r.f.runtime.getObservation().invalidateScene();
            Pulsar.BUS.post(new TickEvent()); assertNull(r.rotation.getConfirmedOwner());
            noActions(r.walk()); assertNull(r.rotation.getConfirmedOwner());
        }
    }
    @Test public void a10SuspensionKeepsOnlyTheWishUntilFreshPlanlessAcceptance() throws Exception {
        try(Run r=new Run("Pre")) {
            r.complete(new BlockPos(0,63,0)); float yaw=r.rotation.getEffectiveYaw();
            r.f.mc.inGameHasFocus=false; Pulsar.BUS.post(new TickEvent());
            assertNull("suspension revokes application rights",r.rotation.getConfirmedOwner());
            noActions(r.walk()); r.f.mc.inGameHasFocus=true;
            Hooks hooks=new Hooks(); hooks.motion=e->{ if(e.isPre()) assertFalse(r.rotation.canUseRotation("scaffold",true)); };
            Pulsar.BUS.subscribe(hooks);
            try { noActions(r.walk()); } finally { Pulsar.BUS.unsubscribe(hooks); }
            assertEquals("fresh C03 accepted the retained wish","scaffold",r.rotation.getConfirmedOwner());
            assertEquals(yaw,r.rotation.getEffectiveYaw(),0);
        }
    }
    @Test public void a15RejectedHoldRequiresFreshAcceptanceButKeepsItsSceneBoundWish() throws Exception {
        try(Run r=new Run("Pre")) {
            r.complete(new BlockPos(0,63,0));
            Hooks hooks=new Hooks(); hooks.packet=e->{if(e.isPre() && e.getPacket() instanceof C03PacketPlayer) e.cancel();};
            Pulsar.BUS.subscribe(hooks);
            try { noActions(r.walk()); } finally { Pulsar.BUS.unsubscribe(hooks); }
            assertNull(r.rotation.getConfirmedOwner());
            hooks.motion=e->{if(e.isPre()) assertFalse("pending hold is no PRE authority",r.rotation.canUseRotation("scaffold",true));};
            hooks.packet=e->{}; Pulsar.BUS.subscribe(hooks);
            try { noActions(r.walk()); } finally { Pulsar.BUS.unsubscribe(hooks); }
            assertTrue(r.rotation.canUseRotation("scaffold",false));
        }
    }
    @Test public void a05NativePostGuardRechecksSourceAfterC09AndC08Listeners() throws Exception {
        for(boolean slot:new boolean[]{false,true}) try(Run r=new Run("Post")) {
            Hooks hooks=new Hooks(); final int[] revoked={0};
            hooks.packet=e->{
                if(e.isPre() && (slot ? e.getPacket() instanceof C09PacketHeldItemChange
                        : e.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
                    revoked[0]++; r.rotation.reset("scaffold");
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                for(int i=0;i<8;i++) {
                    List<Object> packets=r.walk();
                    for(Object packet:packets) assertFalse(packet instanceof C08PacketPlayerBlockPlacement);
                }
                assertTrue("real native guard was reached",revoked[0]>0);
                assertEquals(64,r.f.player.inventory.mainInventory[3].stackSize);
                assertEquals(0,r.f.player.inventory.currentItem);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
            r.complete(new BlockPos(0,63,0));
        }
    }
    @Test public void a16SettingsCallbackEndsThePlanEvenWhileItsAcceptedOwnerSurvives() throws Exception {
        try(Run r=new Run("Post")) {
            Hooks hooks=new Hooks(); final boolean[] changed={false};
            hooks.packet=e->{if(e.isPre() && e.getPacket() instanceof C09PacketHeldItemChange && !changed[0]) {
                changed[0]=true; r.f.scaffold.getBooleanSetting("Swing").setValue(true);
                assertEquals("unrelated plan setting keeps the source","scaffold",r.rotation.getConfirmedOwner());
            }};
            Pulsar.BUS.subscribe(hooks);
            try {
                for(int i=0;i<8 && !changed[0];i++) {
                    for(Object p:r.walk()) assertFalse("ended plan cannot click",p instanceof C08PacketPlayerBlockPlacement);
                }
                assertTrue(changed[0]); assertEquals(64,r.f.player.inventory.mainInventory[3].stackSize);
                assertEquals(0,r.f.player.inventory.currentItem);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }
    @Test public void a13PlanAndUnrelatedSettingCleanupKeepSourceButOffReleasesIt() throws Exception {
        try(Run r=new Run("Post")) {
            r.f.scaffold.getNumberSetting("Timer").setValue(1.6D); r.walk();
            assertEquals(1.6,r.f.mc.timer.timerSpeed,.0001); r.noSupport();
            Pulsar.BUS.post(new TickEvent());
            assertTrue(r.rotation.canUseRotation("scaffold",false));
            assertNull(field(Scaffold.class,"currentTarget").get(r.f.scaffold));
            assertEquals(-1,field(Scaffold.class,"frameSequence").getLong(r.f.scaffold));
            assertEquals(1,r.f.mc.timer.timerSpeed,0); assertFalse(Scaffold.isDownwards());
            Object tower=field(Scaffold.class,"towerController").get(r.f.scaffold);
            assertEquals(0,field(TowerController.class,"ticks").getInt(tower));
            r.f.scaffold.getBooleanSetting("Swing").setValue(true);
            assertTrue(r.rotation.canUseRotation("scaffold",false));
            r.f.scaffold.getBooleanSetting("Rotations").setValue(false);
            assertNull(r.rotation.getConfirmedOwner()); noActions(r.walk());
        }
    }
    @Test public void a14ExistingMovementAndJumpConsumersUseTheHeldAcceptedYaw() throws Exception {
        try(Run r=new Run("Post")) {
            r.walk(); float yaw=r.rotation.getEffectiveYaw(); r.noSupport();
            Pulsar.BUS.post(new TickEvent()); noActions(r.walk());
            for(float s:new float[]{-1,0,1}) for(float forward:new float[]{-1,0,1}) {
                r.f.player.motionX=r.f.player.motionZ=0; r.f.player.moveFlying(s,forward,.1f);
                double cap=Math.max(1,Math.hypot(s,forward)),a=Math.toRadians(yaw);
                assertEquals(.1*(s*Math.cos(a)-forward*Math.sin(a))/cap,r.f.player.motionX,3e-5);
                assertEquals(.1*(forward*Math.cos(a)+s*Math.sin(a))/cap,r.f.player.motionZ,3e-5);
            }
            r.f.player.sprint=true; r.f.player.motionX=r.f.player.motionZ=0; r.f.player.jump();
            assertEquals(-.2*Math.sin(Math.toRadians(yaw)),r.f.player.motionX,3e-5);
            assertEquals(.2*Math.cos(Math.toRadians(yaw)),r.f.player.motionZ,3e-5);
        }
    }
    @Test public void a15CancelledDelayedRewrittenAndReplayedMovementCannotSustainStaleHold() throws Exception {
        for(int mode=0;mode<4;mode++) try(Run r=new Run("Post")) {
            r.walk(); r.noSupport(); final int scenario=mode;
            Hooks hooks=new Hooks(); hooks.packet=e->{
                if(e.isPre() && e.getPacket() instanceof C03PacketPlayer) {
                    if(scenario==0) e.cancel();
                    if(scenario==2) e.setPacket(new C03PacketPlayer.C05PacketPlayerLook(-40,10,true));
                }
            };
            if(mode==1) r.f.network.getClientPackets().delays().request("scf-test",200,(p,flow)->p instanceof C03PacketPlayer);
            Pulsar.BUS.subscribe(hooks);
            try { noActions(r.walk()); }
            finally { Pulsar.BUS.unsubscribe(hooks); }
            if(mode==1) { r.f.network.getClientPackets().delays().release("scf-test"); r.drain(); }
            if(mode==3) { r.f.network.sendPacketSilent(new C03PacketPlayer.C05PacketPlayerLook(-40,10,true)); r.drain(); }
            assertNull("transport case "+mode,r.rotation.getConfirmedOwner());
            hooks.packet=e->{};
            hooks.motion=e->{if(e.isPre()) assertFalse(r.rotation.canUseRotation("scaffold",true));};
            Pulsar.BUS.subscribe(hooks);
            try { noActions(r.walk()); } finally { Pulsar.BUS.unsubscribe(hooks); }
            if(!r.rotation.canUseRotation("scaffold",false)) {
                // An external replay does not update EntityPlayerSP.lastReportedYaw. The first
                // non-rotating C03 preserves the replay pose; a subsequent actual look can reacquire.
                assertEquals("only the replay case needs a second wire look",3,mode);
                assertEquals(-40,r.rotation.getEffectiveYaw(),0); noActions(r.walk());
            }
            assertTrue("only a matching newly accepted C03 restores the source: "+mode,r.rotation.canUseRotation("scaffold",false));
        }
    }
}
