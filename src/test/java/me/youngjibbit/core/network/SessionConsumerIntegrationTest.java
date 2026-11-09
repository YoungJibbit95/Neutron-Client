package me.youngjibbit.core.network;

import static me.youngjibbit.core.network.NetworkTestSupport.*;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.event.network.*;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.world.WorldEvent;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Executes the real play-chat handler and its PacketThreadUtil/GUI boundary, without a renderer or server. */
public class SessionConsumerIntegrationTest {
    /** The replacement adapter subscribes once, independently of HUD enable/disable. */
    public static final class Consumer implements Subscriber {
        Object active;int starts,ends;final List<String> accepted=new ArrayList<>(),order=new ArrayList<>();
        NetworkTestSupport.MainThread main;
        Consumer(NetworkTestSupport.MainThread main) {this.main=main;}
        @Subscribe final Listener<PlaySessionEvent> sessions=new Listener<>(event->{
            assertTrue(this.main.isCallingFromMinecraftThread());
            if(event.getState()==PlaySessionEvent.State.STARTED) {active=event.getConnection();starts++;order.add("start");}
            else if(active==event.getConnection()) {active=null;ends++;order.add("end");}
        });
        @Subscribe final Listener<ClientChatDeliveredEvent> chat=new Listener<>(event->{
            assertTrue(this.main.isCallingFromMinecraftThread());
            if(active==event.getConnection() && event.getType()!=2) {accepted.add(event.getText());order.add("chat");}
        });
    }
    private static S02PacketChat chat(String text) {return new S02PacketChat(new ChatComponentText(text));}

    @Test public void confirmedStartAndEndUseConnectionIdentityAcrossRespawnAndDimensionWorldEvents() throws Exception {
        try(Fixture f=new Fixture()) {
            Consumer consumer=new Consumer(f.main);Pulsar.BUS.subscribe(consumer);
            try {
                f.netty(()->f.channel.writeInbound(new S01PacketJoinGame()));assertEquals(0,consumer.starts);
                f.main.drain();assertEquals(1,consumer.starts);assertEquals(1,f.play.joins);assertSame(f.network,consumer.active);
                f.channel.writeInbound(new S07PacketRespawn());f.channel.writeInbound(new S07PacketRespawn());
                Pulsar.BUS.post(new WorldEvent.Unload(null));Pulsar.BUS.post(new WorldEvent.Load(null));
                assertEquals(1,consumer.starts);assertEquals(0,consumer.ends);assertEquals(2,f.play.respawns);
                f.netty(()->f.network.closeChannel(new ChatComponentText("end")));assertEquals(0,consumer.ends);
                f.main.drain();assertEquals(Arrays.asList("start","end"),consumer.order);
                f.network.closeChannel(new ChatComponentText("again"));assertEquals(1,consumer.ends);
                try(Fixture next=new Fixture()) {next.join();assertSame(next.network,consumer.active);assertNotSame(f.network,next.network);
                    Pulsar.BUS.post(new PlaySessionEvent(f.network,PlaySessionEvent.State.ENDED));assertSame(next.network,consumer.active);
                }
            }finally {Pulsar.BUS.unsubscribe(consumer);}
        }
    }
    @Test public void cancelledFailedOrDisconnectedJoinNeverConfirmsAStartedSession() throws Exception {
        for(int mode=0;mode<3;mode++)try(Fixture f=new Fixture()) {
            Consumer consumer=new Consumer(f.main);NetworkTestSupport.Observer packets=new NetworkTestSupport.Observer();
            if(mode==0)packets.action=e->{if(e.isPre())e.cancel();};if(mode==1)f.play.failJoin=true;
            Pulsar.BUS.subscribe(consumer);Pulsar.BUS.subscribe(packets);
            try {f.netty(()->f.channel.writeInbound(new S01PacketJoinGame()));
                if(mode==2)f.network.closeChannel(new ChatComponentText("before application"));
                f.main.drain();assertEquals(0,consumer.starts);assertEquals(0,consumer.ends);assertFalse(f.network.getPlaySession().isActive());
            }finally {Pulsar.BUS.unsubscribe(consumer);Pulsar.BUS.unsubscribe(packets);}
        }
    }
    @Test public void onlyTheFinalSuccessfullyDisplayedChatIsDeliveredIncludingSilentFirstApplication() throws Exception {
        try(Fixture f=new Fixture()) {
            Consumer consumer=new Consumer(f.main);NetworkTestSupport.Observer packets=new NetworkTestSupport.Observer();Pulsar.BUS.subscribe(consumer);Pulsar.BUS.subscribe(packets);
            try {f.join();S02PacketChat replacement=chat("Victim was slain by Tester");
                packets.action=e->{if(e.isPre() && e.getPacket() instanceof S02PacketChat)e.setPacket(replacement);};
                f.netty(()->f.channel.writeInbound(chat("original")));assertTrue(consumer.accepted.isEmpty());f.main.drain();
                assertEquals(Collections.singletonList("Victim was slain by Tester"),f.chat.displayed);assertEquals(f.chat.displayed,consumer.accepted);
                packets.action=e->{if(e.isPre() && e.getPacket() instanceof S02PacketChat)e.cancel();};
                S02PacketChat delayed=chat("Tester was slain by Opponent");f.channel.writeInbound(delayed);
                assertEquals(1,f.chat.displayed.size());assertEquals(1,consumer.accepted.size());
                int pre=packets.calls.size();f.network.receivePacketSilent(delayed);
                assertEquals(pre,packets.calls.size());assertEquals(2,f.chat.displayed.size());assertEquals(f.chat.displayed,consumer.accepted);
                packets.action=e->{};f.channel.writeInbound(new S02PacketChat(new ChatComponentText("bar"),(byte)2));
                assertEquals(3,f.chat.displayed.size());assertEquals(2,consumer.accepted.size());
            }finally {Pulsar.BUS.unsubscribe(consumer);Pulsar.BUS.unsubscribe(packets);}
        }
    }
    @Test public void quickExitAndRepeatedContinuationProduceOneGuiApplicationAndOneDelivery() throws Exception {
        try(Fixture f=new Fixture()) {
            Consumer consumer=new Consumer(f.main);Pulsar.BUS.subscribe(consumer);
            try {f.join();AtomicInteger completed=new AtomicInteger();S02PacketChat packet=chat("Victim was slain by Tester");
                f.netty(()->PacketApplication.process(packet,f.play,f.network::isChannelOpen,completed::incrementAndGet));
                assertTrue(f.chat.displayed.isEmpty());assertTrue(consumer.accepted.isEmpty());assertEquals(0,completed.get());
                Runnable continuation=f.main.tasks.poll();assertNotNull(continuation);continuation.run();continuation.run();
                assertEquals(1,f.chat.displayed.size());assertEquals(1,consumer.accepted.size());assertEquals(1,completed.get());
                f.chat.fail=true;f.netty(()->f.channel.writeInbound(chat("failed")));f.main.drain();
                assertEquals(1,consumer.accepted.size());assertEquals(1,consumer.ends);
            }finally {Pulsar.BUS.unsubscribe(consumer);}
        }
    }
    @Test public void chatActuallyAppliedDuringCloseIsDeliveredBeforeSessionEndEvenWithoutPacketPost() throws Exception {
        try(Fixture f=new Fixture()) {
            Consumer consumer=new Consumer(f.main);NetworkTestSupport.Observer packets=new NetworkTestSupport.Observer();Pulsar.BUS.subscribe(consumer);Pulsar.BUS.subscribe(packets);
            try {f.join();packets.calls.clear();f.chat.after=()->f.network.closeChannel(new ChatComponentText("during GUI application"));
                f.channel.writeInbound(chat("Victim was slain by Tester"));
                assertEquals(Arrays.asList("start","chat","end"),consumer.order);assertEquals(1,consumer.accepted.size());
                assertFalse(packets.calls.contains("inPost"));assertFalse(f.network.getPlaySession().isActive());
            }finally {Pulsar.BUS.unsubscribe(consumer);Pulsar.BUS.unsubscribe(packets);}
        }
    }
}
