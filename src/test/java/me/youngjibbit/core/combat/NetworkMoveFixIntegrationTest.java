package me.youngjibbit.core.combat;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.network.*;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.player.*;
import net.minecraft.network.*;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Shared correction services plus real play pipeline/transport and earned player-step budgets. */
public class NetworkMoveFixIntegrationTest {
    static final class Fixture implements AutoCloseable {
        final Object player=new Object(),world=new Object();
        final RotationManager rotation=RuntimeServices.rotation();
        final NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        final EmbeddedChannel channel=new EmbeddedChannel(network);
        final PacketFlowTest.MainThread thread=new PacketFlowTest.MainThread();
        final INetHandlerPlayClient handler=(INetHandlerPlayClient)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{INetHandlerPlayClient.class},(p,m,a)->null);
        Fixture(){network.configureClientPackets(thread);network.setNetHandler(handler);}
        PacketPipeline pipe(){return network.getClientPackets();}
        void accept(String source,float yaw) {
            rotation.beginMotion(player,world,0,0);rotation.request(source,source.equals("scaffold")?30:20,yaw,15);
            Packet<?> accepted=network.sendPacketTracked(new C03PacketPlayer.C05PacketPlayerLook(yaw,15,true),false);
            rotation.finishMotion(rotation.getEffectiveYaw(),rotation.getEffectivePitch(),accepted!=null);
            while(channel.readOutbound()!=null){}
        }
        RotationCorrections.Selection select(String owner,MoveFixController.Mode mode) {
            if(rotation.getConfirmedOwner(player,world,pipe())==null)return null;
            return RotationCorrections.select(rotation,owner,true,mode,BodyRotationController.Mode.NATURAL);
        }
        public void close(){channel.finish();rotation.reset();}
    }
    @Test public void withheldAndReplayedMovementCannotBorrowPendingOrOldOwnerProfiles() {
        try(Fixture f=new Fixture()) {
            f.accept("aura",40);assertNotNull(f.select("aura",MoveFixController.Mode.PRECISE));
            f.pipe().delays().request("lagrange",200,(p,flow)->flow==EventFlow.OUTBOUND);
            f.rotation.beginMotion(f.player,f.world,0,0);f.rotation.request("aura",20,50,15);f.rotation.request("scaffold",30,80,15);
            assertNotNull(f.select("aura",MoveFixController.Mode.PRECISE));assertNull(f.select("scaffold",MoveFixController.Mode.PRECISE));
            assertNull(f.network.sendPacketTracked(new C03PacketPlayer.C05PacketPlayerLook(80,15,true),false));
            f.rotation.finishMotion(80,15,false);assertNull(f.select("aura",MoveFixController.Mode.PRECISE));assertNull(f.select("scaffold",MoveFixController.Mode.PRECISE));
            f.pipe().delays().release("lagrange");assertEquals(80,f.rotation.getEffectiveYaw(),0);assertNull(f.select("scaffold",MoveFixController.Mode.PRECISE));
            f.accept("scaffold",80);assertNotNull(f.select("scaffold",MoveFixController.Mode.PRECISE));assertNull(f.select("aura",MoveFixController.Mode.PRECISE));
            f.rotation.reset("scaffold");assertNull(f.select("scaffold",MoveFixController.Mode.PRECISE));
        }
    }
    @Test public void connectionWorldRespawnAndDeathRejectStaleCorrectionState() {
        try(Fixture f=new Fixture()) {
            f.accept("aura",40);assertNull(f.rotation.getConfirmedOwner(f.player,f.world,new Object()));
            f.rotation.beginMotion(f.player,new Object(),0,0);assertNull(f.select("aura",MoveFixController.Mode.SIMPLE));
            f.accept("aura",40);f.pipe().inbound(new S07PacketRespawn(),f.handler,false);assertNull(f.select("aura",MoveFixController.Mode.SIMPLE));
            f.accept("scaffold",80);f.pipe().inbound(new S06PacketUpdateHealth(0,20,0),f.handler,false);assertNull(f.select("scaffold",MoveFixController.Mode.SIMPLE));
            f.accept("aura",40);f.network.closeChannel(new net.minecraft.util.ChatComponentText("synthetic end"));assertNull(f.select("aura",MoveFixController.Mode.SIMPLE));
        }
    }
    @Test public void actionBarrierReleasesMovementBeforeCorrectionAndFinalActionGuards() {
        try(Fixture f=new Fixture()) {
            f.accept("scaffold",80);f.pipe().delays().request("lagrange",200,(p,flow)->flow==EventFlow.OUTBOUND);
            assertNull(f.network.sendPacketTracked(new C03PacketPlayer.C05PacketPlayerLook(-30,15,true),false));
            C08PacketPlayerBlockPlacement placement=new C08PacketPlayerBlockPlacement();
            assertNull(f.network.sendPacketGuarded(placement,p->f.rotation.matchesEffective(80,15) && f.select("scaffold",MoveFixController.Mode.PRECISE)!=null));
            assertTrue(f.channel.readOutbound() instanceof C03PacketPlayer);assertNull(f.channel.readOutbound());
            assertEquals(-30,f.rotation.getEffectiveYaw(),0);assertNull(f.select("scaffold",MoveFixController.Mode.PRECISE));
        }
    }
    @Test public void withheldStepsDoNotCorrectAndExtraStepsCorrectEachFreshInputOnlyOnce() {
        try(Fixture f=new Fixture()) {
            f.accept("aura",90);RotationCorrections.Selection selected=f.select("aura",MoveFixController.Mode.PRECISE);
            TickbaseBudget budget=new TickbaseBudget(()->0);budget.configure(1,1,2);budget.bind(f.player,f.world);budget.frame(true,1,50,1);
            AtomicInteger steps=new AtomicInteger();
            BooleanSupplier simulate=()->{steps.incrementAndGet();PlayerMoveUpdateEvent input=new PlayerMoveUpdateEvent(0,.3f,.02f,0,0);
                RotationCorrections.applyMovement(selected,input);float s=input.getStrafe(),forward=input.getForward(),yaw=input.getYaw();
                RotationCorrections.applyMovement(selected,input);assertEquals(s,input.getStrafe(),0);assertEquals(forward,input.getForward(),0);assertEquals(yaw,input.getYaw(),0);
                assertEquals(.3,Math.hypot(s,forward),1e-7);
                double radians=Math.toRadians(yaw);assertEquals(0,s*Math.cos(radians)-forward*Math.sin(radians),1e-7);assertEquals(.3,forward*Math.cos(radians)+s*Math.sin(radians),1e-7);return true;};
            budget.step(true,()->true,simulate);assertEquals(0,steps.get());assertTrue(budget.release());budget.step(false,()->true,simulate);assertEquals(2,steps.get());
        }
    }
    @Test public void jumpCorrectionIsIdempotentAndHonorsCancellation() {
        try(Fixture f=new Fixture()) {
            f.accept("aura",71);RotationCorrections.Selection selected=f.select("aura",MoveFixController.Mode.SIMPLE);
            JumpFixEvent jump=new JumpFixEvent(0);RotationCorrections.applyJump(selected,jump);float once=jump.getYaw();
            RotationCorrections.applyJump(selected,jump);assertEquals(once,jump.getYaw(),0);assertTrue(Math.abs(once)<=22.5001);
            JumpFixEvent cancelled=new JumpFixEvent(0);cancelled.cancel();RotationCorrections.applyJump(selected,cancelled);assertEquals(0,cancelled.getYaw(),0);
        }
    }
    @Test public void repeatedRenderPassesSharePoseAndCannotChangeActionOrRotationState() {
        try(Fixture f=new Fixture()) {
            f.accept("scaffold",100);long sequence=f.rotation.getMotionSequence();BodyRotationController body=new BodyRotationController();
            BodyRotationController.Pose first=body.sampleFrame(1,f.player,f.world,"scaffold",BodyRotationController.Mode.NATURAL,100,15,0,0);
            for(int i=1;i<20;i++) {
                assertSame(first,body.sampleFrame(1,f.player,f.world,"scaffold",BodyRotationController.Mode.NATURAL,100,15,20,i*1000000L));
                assertTrue(f.rotation.matchesEffective(100,15));assertEquals(sequence,f.rotation.getMotionSequence());assertEquals("scaffold",f.rotation.getConfirmedOwner());
            }
            BodyRotationController.Pose next=body.sampleFrame(2,f.player,f.world,"scaffold",BodyRotationController.Mode.NATURAL,100,15,0,20000000L);assertTrue(next.bodyYaw>first.bodyYaw);
            body.reset("scaffold");assertNull(body.sampleFrame(2,f.player,f.world,null,BodyRotationController.Mode.NATURAL,100,15,0,21000000L));
            assertEquals(12,body.sampleFrame(3,f.player,new Object(),"aura",BodyRotationController.Mode.NATURAL,40,15,12,30000000L).bodyYaw,0);
        }
    }
}
