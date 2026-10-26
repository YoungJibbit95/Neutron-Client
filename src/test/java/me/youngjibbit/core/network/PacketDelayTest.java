package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.utils.PingSpoofComponent;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class PacketDelayTest {
    private PacketPipeline pipeline(AtomicLong time, List<Packet<?>> accepted) {
        return new PacketPipeline(new PacketFlowTest.MainThread(),()->true,accepted::add,
                error->{throw new AssertionError(error);},new PacketDelays(time::get));
    }
    @Test public void twoOwnersReleaseOnlyTheirOwnHoldsAndReplayHasOnePreOnePost() {
        AtomicLong now=new AtomicLong();List<Packet<?>> accepted=new ArrayList<>();
        PacketPipeline pipe=pipeline(now,accepted);PacketFlowTest.Observer events=new PacketFlowTest.Observer();
        PingSpoofComponent.Owner back=new PingSpoofComponent.Owner(),lag=new PingSpoofComponent.Owner();
        back.request(pipe,150,(p,f)->true);lag.request(pipe,200,(p,f)->true);
        C03PacketPlayer first=new C03PacketPlayer(true),second=new C03PacketPlayer(false);
        Pulsar.BUS.subscribe(events);
        try {
            assertNull(pipe.outbound(first,false,p->{}));assertNull(pipe.outbound(second,false,p->{}));
            back.release();assertTrue(accepted.isEmpty());assertTrue(lag.isActive());
            now.set(200000000L);pipe.pump();
            assertEquals(Arrays.asList(first,second),accepted);assertEquals(0,pipe.delays().size());
            assertEquals(Arrays.asList("outPre","outPre","outPost","outPost"),events.calls);
            pipe.outbound(first,false,p->{});assertEquals(1,pipe.delays().size()); // reused object gets a new attempt
            lag.release();assertEquals(3,accepted.size());
        } finally {Pulsar.BUS.unsubscribe(events);pipe.close();}
    }
    @Test public void actionsFlushEarlierMovementAndSilentReplayCannotOvertakeIt() {
        AtomicLong now=new AtomicLong();List<Packet<?>> accepted=new ArrayList<>();PacketPipeline pipe=pipeline(now,accepted);
        pipe.delays().request("lag",500,(p,f)->true);
        C03PacketPlayer movement=new C03PacketPlayer(true);C02PacketUseEntity attack=new C02PacketUseEntity();
        pipe.outbound(movement,false,p->{});pipe.outbound(attack,false,p->{});
        assertEquals(Arrays.asList(movement,attack),accepted);
        pipe.outbound(movement,false,p->{});pipe.outbound(movement,true,p->{});
        assertEquals(4,accepted.size());assertEquals(0,pipe.delays().size());
    }
    @Test public void inboundDeltasStayOrderedAndTeleportAppliesAfterThemButCorrectionDiscardsThem() {
        AtomicLong now=new AtomicLong();List<Packet<?>> received=new ArrayList<>();PacketPipeline pipe=pipeline(now,new ArrayList<>());
        INetHandler handler=PacketFlowTest.handler(a->received.add((Packet<?>)a[0]));
        pipe.delays().request("back",200,(p,f)->f==EventFlow.INBOUND);
        S14PacketEntity first=new S14PacketEntity(4),second=new S14PacketEntity(4);
        pipe.inbound(first,handler,false);pipe.inbound(second,handler,false);assertTrue(received.isEmpty());
        S18PacketEntityTeleport teleport=new S18PacketEntityTeleport();pipe.inbound(teleport,handler,false);
        assertEquals(Arrays.asList(first,second,teleport),received);
        pipe.inbound(first,handler,false);pipe.worldChanged();now.set(500000000L);pipe.pump();
        assertEquals(3,received.size());assertEquals(0,pipe.delays().size());
    }
    @Test public void capacityAgeLeaseAndDisconnectAreBoundedWithMonotoneDeadlines() {
        AtomicLong now=new AtomicLong();PacketDelays queue=new PacketDelays(now::get);AtomicInteger applied=new AtomicInteger();
        queue.request("owner",500,(p,f)->true);
        for(int i=0;i<PacketDelays.MAX_PACKETS+1;i++)queue.defer(new C03PacketPlayer(true),EventFlow.OUTBOUND,applied::incrementAndGet);
        assertEquals(PacketDelays.MAX_PACKETS,applied.get());assertEquals(1,queue.size());
        now.set(250000001L);queue.pump();assertEquals(PacketDelays.MAX_PACKETS+1,applied.get()); // expired lease
        queue.request("owner",Integer.MAX_VALUE,(p,f)->true);
        queue.defer(new C03PacketPlayer(true),EventFlow.OUTBOUND,applied::incrementAndGet);
        queue.discard();now.set(Long.MAX_VALUE/2);queue.pump();assertEquals(PacketDelays.MAX_PACKETS+1,applied.get());
        List<Packet<?>> accepted=new ArrayList<>();PacketPipeline pipe=pipeline(now,accepted);
        pipe.delays().request("owner",500,(p,f)->true);pipe.outbound(new C03PacketPlayer(true),false,p->{});
        pipe.close();pipe.delays().barrier(EventFlow.OUTBOUND);assertTrue(accepted.isEmpty());assertEquals(0,pipe.delays().size());
    }
    @Test public void worldChangeCancelsAlreadyScheduledTransportAndFreshSceneStillWorks() throws Exception {
        AtomicLong now=new AtomicLong();PacketFlowTest.MainThread main=new PacketFlowTest.MainThread();List<Packet<?>> accepted=new ArrayList<>();
        PacketPipeline pipe=new PacketPipeline(main,()->true,accepted::add,e->{throw new AssertionError(e);},new PacketDelays(now::get));
        AtomicInteger applied=new AtomicInteger();INetHandler handler=PacketFlowTest.handler(a->applied.incrementAndGet());
        Thread netty=new Thread(()->{pipe.inbound(new S00PacketKeepAlive(1),handler,false);pipe.outbound(new C03PacketPlayer(true),false,p->{});});
        netty.start();netty.join();pipe.worldChanged();main.drain();assertEquals(0,applied.get());assertTrue(accepted.isEmpty());
        pipe.inbound(new S00PacketKeepAlive(2),handler,false);assertEquals(1,applied.get());
    }
}
