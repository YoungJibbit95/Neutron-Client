package me.youngjibbit.core.network;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.zero.alpine.listener.*;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class PacketLifecycleTest {
    private PacketPipeline pipeline(PacketFlowTest.MainThread main,AtomicLong now,List<Packet<?>> accepted,List<Throwable> failures) {
        return new PacketPipeline(main,()->true,accepted::add,failures::add,new PacketDelays(now::get));
    }
    @Test public void nestedPostReplayCannotOvertakeAnOlderQueuedPacketOrCorruptTheLastRotation() {
        AtomicLong now=new AtomicLong();List<Packet<?>> accepted=new ArrayList<>();
        PacketPipeline pipe=pipeline(new PacketFlowTest.MainThread(),now,accepted,new ArrayList<>());
        C03PacketPlayer first=new C03PacketPlayer.C05PacketPlayerLook(10,0,true),second=new C03PacketPlayer.C05PacketPlayerLook(20,0,true),nested=new C03PacketPlayer.C05PacketPlayerLook(30,0,true);
        PacketFlowTest.Observer events=new PacketFlowTest.Observer();
        events.action=e->{if(e.isPost() && e.getPacket()==first)pipe.outbound(nested,true,p->{});};
        pipe.delays().request("lag",200,(p,f)->true);Pulsar.BUS.subscribe(events);
        try {pipe.outbound(first,false,p->{});pipe.outbound(second,false,p->{});now.set(200000000L);pipe.pump();
            assertEquals(Arrays.asList(first,second,nested),accepted);assertEquals(0,pipe.delays().size());
        }finally {Pulsar.BUS.unsubscribe(events);pipe.close();}
    }
    @Test public void theCapacityLimitIncludesBothDirections() {
        AtomicLong now=new AtomicLong();PacketDelays queue=new PacketDelays(now::get);AtomicInteger applied=new AtomicInteger();
        queue.request("both",500,(p,f)->true);
        for(int i=0;i<PacketDelays.MAX_PACKETS;i++)queue.defer(new S14PacketEntity(1),EventFlow.INBOUND,applied::incrementAndGet);
        queue.defer(new C03PacketPlayer(true),EventFlow.OUTBOUND,applied::incrementAndGet);
        assertEquals(PacketDelays.MAX_PACKETS,applied.get());assertEquals(1,queue.size());
    }
    @Test public void respawnSeparatesOldAndNewWorldPacketsAlreadyWaitingOnTheMainThread() throws Exception {
        AtomicLong now=new AtomicLong();PacketFlowTest.MainThread main=new PacketFlowTest.MainThread();List<Object> applied=new ArrayList<>();
        PacketPipeline pipe=pipeline(main,now,new ArrayList<>(),new ArrayList<>());
        INetHandler handler=PacketFlowTest.handler(a->{if(a[0] instanceof S07PacketRespawn)pipe.worldChanged();applied.add(a[0]);});
        S14PacketEntity old=new S14PacketEntity(1),fresh=new S14PacketEntity(2);S07PacketRespawn respawn=new S07PacketRespawn();
        pipe.delays().request("back",200,(p,f)->true);
        Thread netty=new Thread(()->{pipe.inbound(old,handler,false);pipe.inbound(respawn,handler,false);pipe.inbound(fresh,handler,false);});
        netty.start();netty.join();main.drain();
        assertEquals(Arrays.asList(respawn,fresh),applied);assertEquals(0,pipe.delays().size());
    }
    @Test public void replacementCorrectionDiscardsOldQueuesAndItsReplyIsNeverDelayed() {
        AtomicLong now=new AtomicLong();List<Packet<?>> accepted=new ArrayList<>();PacketPipeline pipe=pipeline(new PacketFlowTest.MainThread(),now,accepted,new ArrayList<>());
        PacketFlowTest.Observer replace=new PacketFlowTest.Observer();S08PacketPlayerPosLook correction=new S08PacketPlayerPosLook();
        replace.action=e->{if(e.isInbound()&&e.isPre())e.setPacket(correction);};
        class Receipt implements Subscriber {
            @Subscribe final Listener<PacketReceiveEvent> event=new Listener<>(e->pipe.delays().request("new",500,(p,f)->true));
        }
        Receipt receipt=new Receipt();Pulsar.BUS.subscribe(replace);Pulsar.BUS.subscribe(receipt);
        try {pipe.delays().request("old",500,(p,f)->true);C03PacketPlayer old=new C03PacketPlayer(true),reply=new C03PacketPlayer.C06PacketPlayerPosLook(0,0,0,40,5,true);
            pipe.outbound(old,false,p->{});
            pipe.inbound(new S14PacketEntity(1),PacketFlowTest.handler(a->pipe.outbound(reply,false,p->{})),false);
            assertEquals(Collections.singletonList(reply),accepted);assertEquals(0,pipe.delays().size());
        }finally {Pulsar.BUS.unsubscribe(replace);Pulsar.BUS.unsubscribe(receipt);pipe.close();}
    }
    @Test public void handoffAgeCapacityAndFailedHandlerAbortWithoutPostOrReplay() throws Exception {
        for(int mode=0;mode<3;mode++) {
            AtomicLong now=new AtomicLong();PacketFlowTest.MainThread main=new PacketFlowTest.MainThread();List<Throwable> failures=new ArrayList<>();
            PacketPipeline pipe=pipeline(main,now,new ArrayList<>(),failures);PacketFlowTest.Observer events=new PacketFlowTest.Observer();
            final int testMode=mode;INetHandler handler=PacketFlowTest.handler(a->{if(testMode==2)throw new IllegalStateException("handler failed");});
            Pulsar.BUS.subscribe(events);
            try {Thread netty=new Thread(()->{for(int i=0;i<(testMode==1?4097:1);i++)pipe.inbound(new S00PacketKeepAlive(i),handler,false);});
                netty.start();netty.join();if(mode==0)now.set(5000000001L);main.drain();
                assertFalse(pipe.isActive());assertEquals(1,failures.size());assertFalse(events.calls.contains("inPost"));assertEquals(0,pipe.delays().size());
            }finally {Pulsar.BUS.unsubscribe(events);pipe.close();}
        }
    }
    @Test public void actualDirectSendDrainsEarlierThreadHandoffAndObsoleteHandlerIsNeverApplied() throws Exception {
        NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);EmbeddedChannel channel=new EmbeddedChannel(network);PacketFlowTest.MainThread main=new PacketFlowTest.MainThread();network.configureClientPackets(main);
        C03PacketPlayer first=new C03PacketPlayer.C05PacketPlayerLook(10,0,true),last=new C03PacketPlayer.C05PacketPlayerLook(20,0,true);
        AtomicInteger oldHandler=new AtomicInteger(),newHandler=new AtomicInteger();network.setNetHandler(PacketFlowTest.handler(a->oldHandler.incrementAndGet()));
        try {
            Thread netty=new Thread(()->{network.sendPacket(first);network.receivePacket(new S00PacketKeepAlive(1),false);});netty.start();netty.join();
            network.setNetHandler(PacketFlowTest.handler(a->newHandler.incrementAndGet()));network.sendPacket(last);
            assertSame(first,channel.readOutbound());assertSame(last,channel.readOutbound());assertEquals(20,RuntimeServices.rotation().getEffectiveYaw(),0);
            main.drain();assertEquals(0,oldHandler.get());assertEquals(0,newHandler.get());
            network.receivePacket(new S00PacketKeepAlive(2),false);assertEquals(1,newHandler.get());
        }finally {channel.finish();RuntimeServices.rotation().reset();}
    }
    @Test public void worldChangeDuringPreAndWrongDirectionReplacementCannotBeAccepted() {
        AtomicLong now=new AtomicLong();List<Packet<?>> accepted=new ArrayList<>();PacketPipeline pipe=pipeline(new PacketFlowTest.MainThread(),now,accepted,new ArrayList<>());
        PacketFlowTest.Observer events=new PacketFlowTest.Observer();events.action=e->{if(e.isPre())pipe.worldChanged();};Pulsar.BUS.subscribe(events);
        try {assertNull(pipe.outbound(new C03PacketPlayer(true),false,p->{}));assertTrue(accepted.isEmpty());
            events.action=e->{if(e.isPre())e.setPacket(new S00PacketKeepAlive(1));};
            try {pipe.outbound(new C03PacketPlayer(true),false,p->{});fail("wrong direction accepted");}catch(IllegalArgumentException expected) { }
            assertTrue(accepted.isEmpty());assertFalse(events.calls.contains("outPost"));
        }finally {Pulsar.BUS.unsubscribe(events);pipe.close();}
    }
    @Test public void worldChangeInsideDependencyReleaseCannotFalselyAcceptTheFollowingAttack() {
        AtomicLong now=new AtomicLong();List<Packet<?>> accepted=new ArrayList<>();PacketPipeline pipe=pipeline(new PacketFlowTest.MainThread(),now,accepted,new ArrayList<>());
        C03PacketPlayer movement=new C03PacketPlayer(true);PacketFlowTest.Observer events=new PacketFlowTest.Observer();
        events.action=e->{if(e.isPost() && e.getPacket()==movement)pipe.worldChanged();};Pulsar.BUS.subscribe(events);
        try {pipe.delays().request("lag",200,(p,f)->true);pipe.outbound(movement,false,p->{});
            assertNull(pipe.outbound(new C02PacketUseEntity(),false,p->{}));
            assertEquals(Collections.singletonList(movement),accepted);
        }finally {Pulsar.BUS.unsubscribe(events);pipe.close();}
    }
    @Test public void deferredHandlerFailureDiscardsRemainingWorkAndAClosedQueueCannotBeRefilled() {
        AtomicLong now=new AtomicLong();List<Throwable> failures=new ArrayList<>();PacketPipeline pipe=pipeline(new PacketFlowTest.MainThread(),now,new ArrayList<>(),failures);
        INetHandler handler=PacketFlowTest.handler(a->{throw new IllegalStateException("deferred application failed");});
        pipe.delays().request("back",200,(p,f)->true);
        pipe.inbound(new S14PacketEntity(1),handler,false);pipe.inbound(new S14PacketEntity(1),handler,false);
        now.set(200000000L);pipe.pump();assertFalse(pipe.isActive());assertEquals(1,failures.size());assertEquals(0,pipe.delays().size());
        pipe.delays().request("late",500,(p,f)->true);
        assertFalse(pipe.delays().defer(new S14PacketEntity(1),EventFlow.INBOUND,()->fail("closed replay")));
        assertEquals(0,pipe.delays().size());
    }
    @Test public void aDirectPlayerSendCannotApplyAnIncomingRespawnReentrantly() throws Exception {
        AtomicLong now=new AtomicLong();PacketFlowTest.MainThread main=new PacketFlowTest.MainThread();List<Packet<?>> accepted=new ArrayList<>();
        PacketPipeline pipe=pipeline(main,now,accepted,new ArrayList<>());AtomicInteger respawns=new AtomicInteger();
        INetHandler handler=PacketFlowTest.handler(a->{pipe.worldChanged();respawns.incrementAndGet();});
        Thread netty=new Thread(()->pipe.inbound(new S07PacketRespawn(),handler,false));netty.start();netty.join();
        C03PacketPlayer movement=new C03PacketPlayer(true);assertSame(movement,pipe.outbound(movement,false,p->{}));
        assertEquals(0,respawns.get());assertEquals(Collections.singletonList(movement),accepted);
        main.drain();assertEquals(1,respawns.get());
    }
    @Test public void aDelayedDeltaCannotTouchANewEntityWithTheSameIdInTheRealPlayHandler() throws Exception {
        AtomicLong now=new AtomicLong();PacketPipeline pipe=pipeline(new PacketFlowTest.MainThread(),now,new ArrayList<>(),new ArrayList<>());
        net.minecraft.client.network.NetHandlerPlayClient play=new net.minecraft.client.network.NetHandlerPlayClient(null,null,new NetworkManager(EnumPacketDirection.CLIENTBOUND),null);
        AttackHookTest.WorldStub world=AttackHookTest.bare(AttackHookTest.WorldStub.class);
        world.target=AttackHookTest.bare(net.minecraft.client.entity.EntityOtherPlayerMP.class);world.target.setEntityId(7);
        java.lang.reflect.Field field=play.getClass().getDeclaredField("clientWorldController");field.setAccessible(true);field.set(play,world);
        pipe.delays().request("back",200,(p,f)->true);PacketFlowTest.Observer events=new PacketFlowTest.Observer();Pulsar.BUS.subscribe(events);
        try {pipe.inbound(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)32,(byte)0,(byte)0,true),play,false);
            world.target=AttackHookTest.bare(net.minecraft.client.entity.EntityOtherPlayerMP.class);world.target.setEntityId(7);
            now.set(200000000L);pipe.pump();assertEquals(0,world.target.serverPosX);assertTrue(pipe.isActive());
            assertEquals(0,pipe.delays().size());assertFalse(events.calls.contains("inPost"));
        }finally {Pulsar.BUS.unsubscribe(events);pipe.close();}
    }
}
