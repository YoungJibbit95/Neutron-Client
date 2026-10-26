package me.youngjibbit.core.network;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Actual transport/server send paths, no personal world or running integrated server. */
public class NetworkShutdownTest {
    @Test public void integratedServerLogoutDoesNotQueueThousandsOfLateChunkUnloads() throws Exception {
        NetworkManager network = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network);
        NetHandlerPlayServer handler = AttackHookTest.bare(NetHandlerPlayServer.class);
        Field transport = NetHandlerPlayServer.class.getDeclaredField("netManager");
        transport.setAccessible(true);
        transport.set(handler, network);
        try {
            channel.close(); // PlayerInstance removal still sends chunk unload packets afterwards.
            for (int i = 0; i < 2048; i++) handler.sendPacket(new S21PacketChunkData());
            assertTrue(network.outboundPacketsQueue.isEmpty());
            assertNull(channel.readOutbound());
        } finally { channel.finish(); }
    }

    @Test public void closedTransportDoesNotReportLatePacketsAsAccepted() {
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network);
        try {
            network.closeChannel(new ChatComponentText("Leaving world"));
            assertNull(network.sendPacketTracked(new C00PacketKeepAlive(1), false));
            network.sendPacketSilent(new C00PacketKeepAlive(2));
            assertTrue(network.outboundPacketsQueue.isEmpty());
        } finally { channel.finish(); }
    }

    @Test public void cancelledConnectionCannotBeReopenedByLateChannelActivation() {
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        network.sendPacket(new C00PacketKeepAlive(1));
        ChatComponentText reason = new ChatComponentText("Cancelled before connect");
        network.closeChannel(reason);
        assertNull(network.sendPacketTracked(new C00PacketKeepAlive(2), false));
        assertTrue(network.outboundPacketsQueue.isEmpty());
        EmbeddedChannel channel = new EmbeddedChannel(network);
        try {
            assertFalse(network.isChannelOpen());
            assertSame(reason, network.getExitMessage());
            assertNull(channel.readOutbound());
        } finally { channel.finish(); }
    }

    @Test public void initialConnectionStillFlushesPendingPacketsInOrder() {
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        Packet<?> first = new C00PacketKeepAlive(1), second = new C00PacketKeepAlive(2);
        assertSame(first, network.sendPacketTracked(first, false));
        assertEquals(1, network.outboundPacketsQueue.size());
        EmbeddedChannel channel = new EmbeddedChannel(network);
        try {
            assertSame(second, network.sendPacketTracked(second, false));
            assertSame(first, channel.readOutbound());
            assertSame(second, channel.readOutbound());
            assertTrue(network.outboundPacketsQueue.isEmpty());
        } finally { channel.finish(); }
    }

    @Test public void unopenedConnectionQueueRemainsBounded() {
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        try {
            for (int i = 0; i < 1024; i++) network.sendPacket(new C00PacketKeepAlive(i));
            assertThrows(IllegalStateException.class, () -> network.sendPacket(new C00PacketKeepAlive(1024)));
            assertEquals(1024, network.outboundPacketsQueue.size());
        } finally { network.closeChannel(new ChatComponentText("Test complete")); }
    }

    @Test public void closeDuringPendingFlushStopsTheRestWithoutRefillingOrFalseAcceptance() {
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        C00PacketKeepAlive first = new C00PacketKeepAlive(1);
        network.sendPacket(first, (ChannelFutureListener) future ->
                network.closeChannel(new ChatComponentText("Closed by send completion")));
        network.sendPacket(new C00PacketKeepAlive(2));
        EmbeddedChannel channel = new EmbeddedChannel(network);
        try {
            assertNull(network.sendPacketTracked(new C00PacketKeepAlive(3), false));
            assertSame(first, channel.readOutbound());
            assertNull(channel.readOutbound());
            assertTrue(network.outboundPacketsQueue.isEmpty());
            assertFalse(network.isChannelOpen());
        } finally { channel.finish(); }
    }

    @Test public void closedPlayPipelineDoesNotEmitAcceptanceOrRefillTheQueue() {
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network);
        AtomicInteger accepted = new AtomicInteger();
        network.configureClientPackets(new PacketFlowTest.MainThread(), ignored -> accepted.incrementAndGet());
        try {
            network.closeChannel(new ChatComponentText("Leaving world"));
            assertNull(network.sendPacketTracked(new C00PacketKeepAlive(1), false));
            assertEquals(0, accepted.get());
            assertTrue(network.outboundPacketsQueue.isEmpty());
            assertFalse(network.getClientPackets().isActive());
        } finally { channel.finish(); }
    }
}
