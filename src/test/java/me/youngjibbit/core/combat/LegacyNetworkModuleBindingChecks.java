package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.*;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.*;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.exploit.*;
import me.youngjibbit.utils.PingSpoofComponent;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.*;
import org.junit.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Actual module listeners and queue/budget consumers, one stable client, controlled clock, no external transport. */
public class LegacyNetworkModuleBindingChecks {
    public static class Animal extends net.minecraft.entity.passive.EntityCow {
        public Animal(net.minecraft.world.World world){super(world);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean isInvisible(){return false;}
    }
    public static class Mob extends net.minecraft.entity.monster.EntityZombie {
        public Mob(net.minecraft.world.World world){super(world);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean isInvisible(){return false;}
    }
    private static RotationBehaviorHookTest.Fixture shared;
    @BeforeClass public static void createClient() throws Exception {shared=new RotationBehaviorHookTest.Fixture();}
    @AfterClass public static void closeClient() throws Exception {shared.close();}
    static final class Setup implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f=shared;
        final AtomicLong clock=new AtomicLong();
        final RotationBehaviorHookTest.Player target=RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
        final List<Packet<?>> applied=new ArrayList<>(),sent=new ArrayList<>();
        final PacketPipeline pipe;
        final List<Module> enabled=new ArrayList<>();
        Setup() throws Exception {
            CombatTargetTracker.clear();ScaffoldClientFlowTest.prepare(f,.5,.48);f.player.onGround=true;f.player.isDead=false;f.player.hurtTime=0;f.mc.objectMouseOver=null;
            target.worldObj=f.mc.theWorld;target.setEntityId(7);target.posX=3;target.posY=64;target.posZ=.5;target.serverPosX=96;target.serverPosY=2048;target.serverPosZ=16;target.hurtTime=1;
            target.setEntityBoundingBox(new AxisAlignedBB(2.7,64,.2,3.3,65.8,.8));
            RotationBehaviorHookTest.field(net.minecraft.world.World.class,"loadedEntityList").set(f.mc.theWorld,new ArrayList<>(Collections.singletonList(target)));
            RotationBehaviorHookTest.field(net.minecraft.client.Minecraft.class,"mcThread").set(f.mc,Thread.currentThread());
            f.player.sendQueue.clientWorldController=f.mc.theWorld;
            f.network.getClientPackets().close();
            pipe=new PacketPipeline(new PacketFlowTest.MainThread(),f.network::isChannelOpen,f.player.sendQueue::observeAcceptedOutbound,error->{throw new AssertionError(error);},new PacketDelays(clock::get),applied::add);
            RotationBehaviorHookTest.field(NetworkManager.class,"clientPackets").set(f.network,pipe);f.runtime.getRotationManager().bindConnection(pipe);
        }
        <T extends Module> T enable(T module){module.setToggled(true);enabled.add(module);return module;}
        void tick(long millis){tickTarget(target,millis);}
        void tickTarget(net.minecraft.entity.EntityLivingBase selected,long millis){clock.set(millis*1000000);CombatTargetTracker.update(selected,"legacy-fixture");Pulsar.BUS.post(new TickEvent());pipe.pump();}
        void receive(Packet<?> packet){pipe.inbound(packet,packet instanceof S14PacketEntity || packet instanceof S18PacketEntityTeleport
                ? f.player.sendQueue : PacketFlowTest.handler(args->{}),false);}
        void send(Packet<?> packet){pipe.outbound(packet,false,sent::add);}
        void pump(long millis){clock.set(millis*1000000);pipe.pump();}
        public void close(){for(Module module:enabled)module.setToggled(false);pipe.close();CombatTargetTracker.clear();f.mc.objectMouseOver=null;while(f.channel.readOutbound()!=null){}}
    }
    private boolean active(Module module) throws Exception {java.lang.reflect.Field field=module.getClass().getDeclaredField("delayOwner");field.setAccessible(true);return ((PingSpoofComponent.Owner)field.get(module)).isActive();}
    private Backtrack back(Setup s){Backtrack back=new Backtrack(s.clock::get,(min,max)->max);back.getModeSetting("Target Source").setValue("Combat / Nearest");back.getModeSetting("Delay Mode").setValue("Fixed");back.getNumberSetting("Delay").setValue(125D);back.getNumberSetting("Min Range").setValue(3D);return back;}

    @Test public void fixedBacktrackDelayAndMinimumDistanceReachTheActualReceiptQueue() throws Exception {
        try(Setup s=new Setup()) {
            Backtrack back=s.enable(back(s));s.tick(0);S14PacketEntity move=new S14PacketEntity.S15PacketEntityRelMove(7,(byte)32,(byte)0,(byte)0,true);s.receive(move);
            assertTrue(active(back));assertTrue(s.applied.isEmpty());s.tick(124);assertTrue(s.applied.isEmpty());s.pump(125);assertEquals(Collections.singletonList(move),s.applied);
            back.getNumberSetting("Min Range").setValue(5D);s.tick(126);s.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)32,(byte)0,(byte)0,true));assertEquals(2,s.applied.size());assertFalse(active(back));
        }
    }
    @Test public void cursorFallbackKeepsOnlyAValidBoundedTargetAndNeverPublishesACombatOwner() throws Exception {
        try(Setup s=new Setup()) {
            Backtrack back=back(s);back.getModeSetting("Target Source").setValue("Combat / Cursor");back.getNumberSetting("Target Hold MS").setValue(500D);s.enable(back);s.f.mc.objectMouseOver=new MovingObjectPosition(s.target);
            Pulsar.BUS.post(new TickEvent());s.f.mc.objectMouseOver=null;s.clock.set(400000000);Pulsar.BUS.post(new TickEvent());
            java.lang.reflect.Field current=back.getClass().getDeclaredField("currentTarget");current.setAccessible(true);assertTrue(current.get(back)==s.target);assertNull(CombatTargetTracker.getPrimaryTarget());
            s.clock.set(501000000);Pulsar.BUS.post(new TickEvent());assertTrue(current.get(back)==null);assertFalse(active(back));
        }
    }
    @Test public void lagTickWindowsReleaseEveryMovementInOrderAndHonorSwingAndRangeControls() throws Exception {
        try(Setup s=new Setup()) {
            LagRange lag=new LagRange(s.clock::get,(min,max)->max);lag.getModeSetting("Delay Mode").setValue("Ticks");lag.getNumberSetting("Window Ticks").setValue(2D);lag.getNumberSetting("Max Target Range").setValue(1D);lag.getNumberSetting("Preactivation").setValue(2D);lag.getBooleanSetting("Only Swinging").setValue(true);s.f.player.isSwingInProgress=true;s.enable(lag);s.tick(0);
            List<Packet<?>> packets=Arrays.<Packet<?>>asList(new C03PacketPlayer.C04PacketPlayerPosition(1,64,.5,true),new C03PacketPlayer.C04PacketPlayerPosition(2,64,.5,true),new C03PacketPlayer.C04PacketPlayerPosition(3,64,.5,true));for(Packet<?> packet:packets)s.send(packet);
            assertTrue(s.sent.isEmpty());s.tick(99);assertTrue(s.sent.isEmpty());s.tick(100);assertEquals(packets,s.sent);assertEquals(3,s.f.player.getLastReportedPosition().xCoord,0);
            lag.getNumberSetting("Preactivation").setValue(0D);s.tick(150);assertFalse(active(lag));lag.getNumberSetting("Preactivation").setValue(2D);s.f.player.isSwingInProgress=false;s.tick(200);assertFalse(active(lag));
            s.f.player.isSwingInProgress=true;s.tick(250);Packet<?> held=new C03PacketPlayer(true);s.send(held);assertEquals(3,s.sent.size());
            AttackEvent cancelled=new AttackEvent(s.target);cancelled.cancel();Pulsar.BUS.post(cancelled);assertTrue(active(lag));assertEquals(3,s.sent.size());
            Pulsar.BUS.post(new AttackResultEvent(s.target));assertFalse(active(lag));assertEquals(held,s.sent.get(3));
        }
    }
    @Test public void overlappingWindowsReleaseOnlyTheirOwnHoldsAndDiscardTheOldWorld() throws Exception {
        try(Setup s=new Setup()) {
            Backtrack back=back(s);back.getNumberSetting("Delay").setValue(200D);s.enable(back);
            LagRange lag=new LagRange(s.clock::get,(min,max)->min);lag.getModeSetting("Delay Mode").setValue("Ticks");s.enable(lag);s.tick(0);
            Packet<?> inbound=new S14PacketEntity.S15PacketEntityRelMove(7,(byte)32,(byte)0,(byte)0,true);s.receive(inbound);Packet<?> outbound=new C03PacketPlayer(true);s.send(outbound);
            assertTrue(s.applied.isEmpty());assertTrue(s.sent.isEmpty());s.tick(100);assertEquals(Collections.singletonList(outbound),s.sent);assertTrue(s.applied.isEmpty());assertTrue(active(back));
            s.pump(200);assertEquals(Collections.singletonList(inbound),s.applied);s.tick(250);s.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)1,(byte)0,(byte)0,true));s.send(new C03PacketPlayer(true));
            s.pipe.worldChanged();net.minecraft.client.multiplayer.WorldClient previous=s.f.mc.theWorld;s.f.mc.theWorld=null;Pulsar.BUS.post(new WorldEvent.Unload(previous));s.pump(1000);
            assertEquals(1,s.sent.size());assertEquals(1,s.applied.size());assertFalse(active(back));assertFalse(active(lag));
        }
    }
    @Test public void tickbaseCooldownSpendsOnlyEarnedStepsWithoutRewindingPoseOrChangingTheTimer() throws Exception {
        try(Setup s=new Setup()) {
            Tickbase tick=new Tickbase(s.clock::get);tick.getNumberSetting("Charge Ticks").setValue(3D);tick.getNumberSetting("Min Charge").setValue(3D);tick.getNumberSetting("Release Speed").setValue(2D);tick.getNumberSetting("Delay").setValue(300D);tick.getBooleanSetting("Require Target").setValue(false);s.enable(tick);
            java.lang.reflect.Field field=Tickbase.class.getDeclaredField("budget");field.setAccessible(true);TickbaseBudget budget=(TickbaseBudget)field.get(tick);AtomicInteger simulation=new AtomicInteger();
            Vec3 before=s.f.player.getPositionVector();float yaw=s.f.player.rotationYaw;
            for(int i=0;i<5;i++){s.clock.set(i*50000000L);Tickbase.beginFrame(s.f.mc,false,1,50);Tickbase.runPlayerStep(s.f.player,()->{simulation.incrementAndGet();return true;});}
            assertEquals(3,budget.getCredit());assertEquals(2,simulation.get());s.clock.set(250000000L);Pulsar.BUS.post(new AttackResultEvent(s.target));assertTrue(budget.isReleasing());assertEquals(3,budget.getCredit());
            s.clock.set(300000000L);assertTrue(budget.isReleasing());for(int i=0;i<3;i++)Tickbase.runPlayerStep(s.f.player,()->{simulation.incrementAndGet();return true;});
            assertEquals(0,budget.getCredit());assertEquals(8,simulation.get());assertEquals(before.xCoord,s.f.player.posX,0);assertEquals(before.yCoord,s.f.player.posY,0);assertEquals(before.zCoord,s.f.player.posZ,0);assertEquals(yaw,s.f.player.rotationYaw,0);assertEquals(1,s.f.mc.timer.timerSpeed,0);
            Tickbase.beginFrame(s.f.mc,false,1,50);Tickbase.runPlayerStep(s.f.player,()->{simulation.incrementAndGet();return true;});assertEquals(0,budget.getCredit());
            s.clock.set(550000000L);Tickbase.beginFrame(s.f.mc,false,1,50);Tickbase.runPlayerStep(s.f.player,()->{simulation.incrementAndGet();return true;});assertEquals(1,budget.getCredit());
            Pulsar.BUS.post(new WorldEvent.Unload(s.f.mc.theWorld));Tickbase.runPlayerStep(s.f.player,()->{simulation.incrementAndGet();return true;});assertEquals(0,budget.getCredit());Tickbase.beginFrame(s.f.mc,false,1,50);Tickbase.runPlayerStep(s.f.player,()->true);assertEquals(1,budget.getCredit());
        }
    }
    @Test public void restoredLivingTargetFiltersReachBacktrackAndLagRangeRatherThanOnlyTheirConfig() throws Exception {
        for(Class<? extends net.minecraft.entity.EntityLivingBase> type:Arrays.asList(Animal.class,Mob.class))try(Setup s=new Setup()) {
            net.minecraft.entity.EntityLivingBase target=RotationBehaviorHookTest.bare(type);target.worldObj=s.f.mc.theWorld;target.setEntityId(8);target.posX=3;target.posY=64;target.posZ=.5;target.serverPosX=96;target.serverPosY=2048;target.serverPosZ=16;target.hurtTime=1;target.setEntityBoundingBox(new AxisAlignedBB(2.7,64,.2,3.3,65.8,.8));
            s.f.mc.theWorld.loadedEntityList.clear();s.f.mc.theWorld.loadedEntityList.add(target);
            Backtrack back=back(s);s.enable(back);s.tickTarget(target,0);s.receive(new S14PacketEntity.S15PacketEntityRelMove(8,(byte)32,(byte)0,(byte)0,true));assertFalse(active(back));
            back.getBooleanSetting(type==Animal.class?"Animals":"Mobs").setValue(true);s.tickTarget(target,1);s.receive(new S14PacketEntity.S15PacketEntityRelMove(8,(byte)32,(byte)0,(byte)0,true));assertTrue(active(back));back.setToggled(false);
            LagRange lag=new LagRange(s.clock::get,(min,max)->max);lag.getBooleanSetting("Animals").setValue(false);s.enable(lag);s.tickTarget(target,2);assertEquals(type==Mob.class,active(lag));
            if(type==Animal.class){lag.getBooleanSetting("Animals").setValue(true);s.tickTarget(target,3);assertTrue(active(lag));}
        }
    }
    @Test public void keepaliveBypassesVelocityFlushesAndCorrectionDiscardsObsoleteMovement() throws Exception {
        try(Setup s=new Setup()) {
            Backtrack back=s.enable(back(s));s.tick(0);Packet<?> move=new S14PacketEntity.S15PacketEntityRelMove(7,(byte)32,(byte)0,(byte)0,true),ping=new S00PacketKeepAlive(7);
            s.receive(move);assertTrue(s.applied.isEmpty());s.receive(ping);assertEquals(Collections.singletonList(ping),s.applied);assertTrue(active(back));assertEquals(1,s.pipe.delays().size());
            Packet<?> next=new S14PacketEntity.S15PacketEntityRelMove(7,(byte)1,(byte)0,(byte)0,true),velocity=new S12PacketEntityVelocity(s.f.player.getEntityId(),.1,.2,.3);
            s.receive(next);assertEquals(1,s.applied.size());s.receive(velocity);assertEquals(Arrays.asList(ping,move,next,velocity),s.applied);assertFalse(active(back));
            s.tick(200);Packet<?> last=new S14PacketEntity.S15PacketEntityRelMove(7,(byte)1,(byte)0,(byte)0,true),correction=new S08PacketPlayerPosLook();s.receive(last);assertEquals(4,s.applied.size());s.receive(correction);assertEquals(Arrays.asList(ping,move,next,velocity,correction),s.applied);assertFalse(s.applied.contains(last));assertEquals(0,s.pipe.delays().size());assertFalse(active(back));
        }
    }
}
