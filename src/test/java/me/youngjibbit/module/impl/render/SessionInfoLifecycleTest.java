package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.*;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;
import net.minecraft.util.*;
import org.junit.Test;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

public class SessionInfoLifecycleTest {
    static final class Cancel implements Subscriber {
        boolean cancel;
        @Subscribe final Listener<EventPacket> event=new Listener<>(e->{if(cancel && e.isInbound() && e.isPre() && e.getPacket() instanceof S02PacketChat)e.cancel();});
    }
    static final class Fixture implements AutoCloseable {
        final SessionConsumerIntegrationTest.Fixture transport=new SessionConsumerIntegrationTest.Fixture();
        final PacketFlowTest.MainThread main=transport.main;
        final SessionInfoLifecycle tracker=new SessionInfoLifecycle(()->"Tester");
        final PacketPipeline pipe=transport.network.getClientPackets();
        final INetHandlerPlayClient handler=transport.play;
        final Cancel cancel=new Cancel();
        Fixture() throws Exception {tracker.install();Pulsar.BUS.subscribe(cancel);transport.join();}
        int delivered(){return transport.chat.displayed.size();}
        S02PacketChat chat(){return new S02PacketChat(new ChatComponentText("Victim was slain by Tester"));}
        public void close(){transport.close();Pulsar.BUS.unsubscribe(tracker);Pulsar.BUS.unsubscribe(cancel);}
    }
    @Test public void onlyAppliedChatCountsOnClientThreadAndReplayIsIdempotent() throws Exception {
        try(Fixture f=new Fixture()) {
            S02PacketChat chat=f.chat();Thread netty=new Thread(()->f.pipe.inbound(chat,f.handler,false));netty.start();netty.join();
            assertEquals(0,f.delivered());assertEquals(0,f.tracker.statistics.snapshot().kills);
            f.main.drain();assertEquals(1,f.delivered());assertEquals(1,f.tracker.statistics.snapshot().kills);
            f.pipe.inbound(chat,f.handler,true);assertEquals(1,f.delivered());assertEquals(1,f.tracker.statistics.snapshot().kills);
            f.cancel.cancel=true;f.pipe.inbound(f.chat(),f.handler,false);assertEquals(1,f.delivered());assertEquals(1,f.tracker.statistics.snapshot().kills);
            f.cancel.cancel=false;f.pipe.inbound(new S02PacketChat(new ChatComponentText("Victim was slain by Tester"),(byte)2),f.handler,false);
            assertEquals(1,f.tracker.statistics.snapshot().kills);
        }
    }
    @Test public void respawnPreservesSessionDisconnectFreezesAndReconnectResets() throws Exception {
        try(Fixture f=new Fixture()) {
            f.pipe.inbound(f.chat(),f.handler,false);long generation=f.tracker.statistics.generation();
            f.pipe.inbound(new S07PacketRespawn(),f.handler,false);
            assertEquals(generation,f.tracker.statistics.generation());assertEquals(1,f.tracker.statistics.snapshot().kills);
            Thread netty=new Thread(()->f.transport.network.closeChannel(new ChatComponentText("end")));netty.start();netty.join();assertTrue(f.tracker.statistics.snapshot().active);
            f.main.drain();assertFalse(f.tracker.statistics.snapshot().active);
            try(SessionConsumerIntegrationTest.Fixture next=new SessionConsumerIntegrationTest.Fixture()) {
                next.join();assertTrue(f.tracker.statistics.snapshot().active);assertEquals(0,f.tracker.statistics.snapshot().kills);
                Pulsar.BUS.post(new me.youngjibbit.event.network.PlaySessionEvent(f.transport.network,
                        me.youngjibbit.event.network.PlaySessionEvent.State.ENDED));
                assertTrue(f.tracker.statistics.snapshot().active);
            }
        }
    }
    @Test public void failedApplicationAndStaleQueuedChatNeverCount() throws Exception {
        try(Fixture f=new Fixture()) {
            INetHandlerPlayClient broken=(INetHandlerPlayClient)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{INetHandlerPlayClient.class},(p,m,a)->{throw new IllegalStateException("synthetic failure");});
            try {f.pipe.inbound(f.chat(),broken,false);fail();}catch(IllegalStateException expected){}
            assertEquals(0,f.tracker.statistics.snapshot().kills);
            Thread netty=new Thread(()->f.pipe.inbound(f.chat(),f.handler,false));netty.start();netty.join();f.pipe.worldChanged();f.main.drain();
            assertEquals(0,f.delivered());assertEquals(0,f.tracker.statistics.snapshot().kills);
        }
    }
    @Test public void guiFailureDoesNotCountAndChatAppliedDuringCloseCountsBeforeEnding() throws Exception {
        try(Fixture f=new Fixture()) {
            f.transport.chat.fail=true;
            f.transport.netty(()->f.transport.channel.writeInbound(f.chat()));f.main.drain();
            assertEquals(0,f.delivered());assertEquals(0,f.tracker.statistics.snapshot().kills);
            assertFalse(f.tracker.statistics.snapshot().active);
        }
        try(Fixture f=new Fixture()) {
            f.transport.chat.after=()->f.transport.network.closeChannel(new ChatComponentText("during chat"));
            f.transport.channel.writeInbound(f.chat());
            assertEquals(1,f.delivered());assertEquals(1,f.tracker.statistics.snapshot().kills);
            assertFalse(f.tracker.statistics.snapshot().active);
        }
    }
    @Test public void displayedChatRemainsDeliveredWhenItsGuiCallbackChangesTheWorldGeneration() throws Exception {
        try(Fixture f=new Fixture()) {
            S02PacketChat packet=f.chat();f.transport.chat.after=f.pipe::worldChanged;
            f.transport.channel.writeInbound(packet);
            assertEquals(1,f.delivered());assertEquals(1,f.tracker.statistics.snapshot().kills);
            f.transport.chat.after=()->{};
            f.transport.network.receivePacketSilent(packet);
            assertEquals(1,f.delivered());assertEquals(1,f.tracker.statistics.snapshot().kills);
        }
    }
    @Test public void replayFromAConfirmedChatListenerCannotApplyOrCountTheSamePacketTwice() throws Exception {
        try(Fixture f=new Fixture()) {
            S02PacketChat packet=f.chat();
            class Replay implements Subscriber {
                boolean replayed;
                @Subscribe final Listener<me.youngjibbit.event.network.ClientChatDeliveredEvent> chat=new Listener<>(e->{
                    if(!replayed && e.getConnection()==f.transport.network) {
                        replayed=true;f.transport.network.receivePacketSilent(packet);
                    }
                });
            }
            Replay replay=new Replay();Pulsar.BUS.subscribe(replay);
            try {
                f.transport.channel.writeInbound(packet);
                assertEquals(1,f.delivered());assertEquals(1,f.tracker.statistics.snapshot().kills);
            }finally {Pulsar.BUS.unsubscribe(replay);}
        }
    }
}
