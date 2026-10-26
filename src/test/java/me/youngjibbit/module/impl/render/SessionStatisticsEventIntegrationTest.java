package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.SessionConsumerIntegrationTest.Fixture;
import me.youngjibbit.event.network.*;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.*;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** The actual SessionInfo adapter consumes the integrated Network lifecycle and GUI notifications. */
public class SessionStatisticsEventIntegrationTest {
    @Test public void fixedStatisticsConsumeConfirmedSessionAndRealChatApplicationWithoutRespawnResetOrReplayDuplicates() throws Exception {
        AtomicLong clock=new AtomicLong();SessionInfoLifecycle adapter=new SessionInfoLifecycle(()->"Tester",clock::get);adapter.install();
        try(Fixture first=new Fixture()) {
            first.join();long generation=adapter.statistics.generation();clock.set(2000000000L);
            first.channel.writeInbound(new S07PacketRespawn());assertEquals(generation,adapter.statistics.generation());
            first.netty(()->first.channel.writeInbound(new S02PacketChat(new ChatComponentText("Victim was slain by Tester"))));
            assertEquals(0,adapter.statistics.snapshot().kills);first.main.drain();assertEquals(1,adapter.statistics.snapshot().kills);
            class Cancel implements Subscriber {
                @Subscribe final Listener<EventPacket> packet=new Listener<>(e->{if(e.isPre() && e.getPacket() instanceof S02PacketChat)e.cancel();});
            }
            Cancel cancel=new Cancel();Pulsar.BUS.subscribe(cancel);
            S02PacketChat held=new S02PacketChat(new ChatComponentText("Tester was slain by Opponent"));
            try {first.channel.writeInbound(held);assertEquals(0,adapter.statistics.snapshot().deaths);
                first.network.receivePacketSilent(held);assertEquals(1,adapter.statistics.snapshot().deaths);
            }finally {Pulsar.BUS.unsubscribe(cancel);}
            first.channel.writeInbound(new S02PacketChat(new ChatComponentText("Victim was slain by Tester"),(byte)2));
            assertEquals(1,adapter.statistics.snapshot().kills);
            first.network.closeChannel(new ChatComponentText("end"));assertFalse(adapter.statistics.snapshot().active);
            assertEquals(2000,adapter.statistics.snapshot().milliseconds());clock.set(4000000000L);
            assertEquals(2000,adapter.statistics.snapshot().milliseconds());
            try(Fixture next=new Fixture()) {next.join();assertTrue(adapter.statistics.snapshot().active);assertEquals(0,adapter.statistics.snapshot().kills);
                Pulsar.BUS.post(new PlaySessionEvent(first.network,PlaySessionEvent.State.ENDED));assertTrue(adapter.statistics.snapshot().active);
            }
        }finally {Pulsar.BUS.unsubscribe(adapter);}
    }
}
