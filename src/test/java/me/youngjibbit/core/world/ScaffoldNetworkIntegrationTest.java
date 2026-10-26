package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.*;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

public class ScaffoldNetworkIntegrationTest {
    static final class Access extends ScaffoldPlacementTest.WorldAccess {
        PacketPipeline pipe;
        final List<Packet<?>> sent=new ArrayList<>();
        @Override public boolean click(PlacementTarget target,Vec3 hit,BooleanSupplier valid) {
            Packet<?> packet=new C08PacketPlayerBlockPlacement(target.getBlockPos(),target.getFacing().getIndex(),inventory[selected],0,.5f,.5f);
            Packet<?> accepted=pipe.outbound(packet,false,sent::add,outgoing->outgoing==packet && valid.getAsBoolean());
            return accepted!=null && super.click(target,hit,valid);
        }
    }
    static final class Replace implements Subscriber {
        Packet<?> replacement;boolean cancel;
        @Subscribe final Listener<EventPacket> event=new Listener<>(e->{if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C03PacketPlayer){if(cancel)e.cancel();else if(replacement!=null)e.setPacket(replacement);}});
    }
    PacketPipeline pipeline(ScaffoldPlacementTest.Fixture f,Access a,AtomicLong clock) {
        PacketPipeline p=new PacketPipeline(new PacketFlowTest.MainThread(),()->a.ready,packet->{
            if(packet instanceof C03PacketPlayer){C03PacketPlayer m=(C03PacketPlayer)packet;
                if(m.isMoving())a.wireEyes=new Vec3(m.getPositionX(),m.getPositionY()+1.62,m.getPositionZ());
                if(m.getRotating())f.rotations.observeOutboundRotation(m.getYaw(),m.getPitch());}
        },error->{throw new AssertionError(error);},new PacketDelays(clock::get));a.pipe=p;return p;
    }
    C03PacketPlayer move(float yaw,float pitch,double x) {return new C03PacketPlayer.C06PacketPlayerPosLook(x,64,.5,yaw,pitch,true);}
    @Test public void delayedMovementCannotAuthorizePostAndReplayChangesThePreActionPose() {
        Access a=new Access();ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture(a);AtomicLong time=new AtomicLong();PacketPipeline p=pipeline(f,a,time);
        try {f.align();float yaw=f.rotations.getEffectiveYaw(),pitch=f.rotations.getEffectivePitch();
            p.delays().request("lag",200,(packet,flow)->flow==EventFlow.OUTBOUND);
            f.rotations.beginMotion(0,0);assertNull(p.outbound(move(yaw,pitch,.5),false,a.sent::add));f.rotations.finishMotion(yaw,pitch,false);
            assertFalse(f.place(false,0,true));assertEquals(0,a.clicks);
            p.delays().release("lag");assertEquals(.5,a.wireEyes.xCoord,0);
            f.rotations.finishMotion(yaw,pitch,true);f.rotations.beginMotion(0,0);
            assertTrue(f.place(true,0,true));assertEquals(1,a.clicks);
        } finally {p.close();}
    }
    @Test public void queueBarrierRevalidatesPositionAndRotationAfterCombinedDelayOwners() {
        Access a=new Access();ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture(a);PacketPipeline p=pipeline(f,a,new AtomicLong());
        try {f.align();p.delays().request("backtrack",200,(packet,flow)->true);p.delays().request("lagrange",200,(packet,flow)->true);
            assertNull(p.outbound(move(0,0,10),false,a.sent::add));
            p.delays().release("backtrack");assertTrue(a.sent.isEmpty());
            assertFalse(f.place(false,0,true));assertEquals(0,a.clicks);assertEquals(0,a.selected);
            assertEquals(10,a.wireEyes.xCoord,0);assertEquals(0,f.rotations.getEffectiveYaw(),0);
            assertTrue(a.sent.stream().noneMatch(packet->packet instanceof C08PacketPlayerBlockPlacement));
        } finally {p.close();}
    }
    @Test public void cancelledAndReplacedMotionDoNotReuseThePlannedRotation() {
        Access a=new Access();ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture(a);PacketPipeline p=pipeline(f,a,new AtomicLong());Replace replace=new Replace();Pulsar.BUS.subscribe(replace);
        try {f.align();replace.cancel=true;f.rotations.beginMotion(0,0);
            assertNull(p.outbound(move(90,80,.5),false,a.sent::add));f.rotations.finishMotion(90,80,false);assertFalse(f.place(false,0,true));
            replace.cancel=false;replace.replacement=move(0,0,10);f.rotations.beginMotion(0,0);
            assertSame(replace.replacement,p.outbound(move(90,80,.5),false,a.sent::add));f.rotations.finishMotion(0,0,true);
            assertFalse(f.place(false,0,true));assertEquals(0,a.clicks);
        } finally {Pulsar.BUS.unsubscribe(replace);p.close();}
    }
    @Test public void tickbaseExtraSimulationCannotCreateASecondPlacementBudget() {
        Access a=new Access();a.success=false;ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture(a);PacketPipeline p=pipeline(f,a,new AtomicLong());
        TickbaseBudget budget=new TickbaseBudget(()->0);budget.configure(1,1,2);budget.bind(a.player,a.world);budget.frame(true,1,50,1);
        final int[] steps={0};
        BooleanSupplier simulate=()->{steps[0]++;f.rotations.beginMotion(0,0);ScaffoldRotationPlan aim=ScaffoldRotationPlan.build("Back",a.eyes(),f.plan.getHitVec());
            f.rotations.finishMotion(aim.getYaw(),aim.getPitch(),true);f.place(false,0,true);return true;};
        try {budget.step(true,()->true,simulate);assertEquals(0,steps[0]);assertTrue(budget.release());budget.step(false,()->true,simulate);
            assertEquals(2,steps[0]);assertEquals(1,a.clicks);
        } finally {p.close();}
    }
}
