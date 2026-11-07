package net.minecraft.client.network;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.NetworkSystem;
import net.minecraft.network.PingResponseHandler;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.EnumChatFormatting;
import org.junit.Test;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import static org.junit.Assert.*;

/** Real legacy handlers on in-memory channels. No server constructor, DNS, socket, world or account. */
public class LegacyPingCompatibilityTest {
    private static final String REPLY = "\u00a71\u0000127\u00001.8.9-fixture\u0000Gr\u00fc\u00dfe\u00002\u000020";

    private static final class ServerFixture extends IntegratedServer {
        private ServerFixture() { super((Minecraft) null); }
        @Override public String getMOTD() { return "Gr\u00fc\u00dfe"; }
        @Override public String getMinecraftVersion() { return "1.8.9-fixture"; }
        @Override public int getCurrentPlayerCount() { return 2; }
        @Override public int getMaxPlayers() { return 20; }
    }

    private static NetworkSystem network() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        ServerFixture server = (ServerFixture) ((Unsafe) field.get(null)).allocateInstance(ServerFixture.class);
        return new NetworkSystem(server);
    }

    private static final class RemoteChannel extends EmbeddedChannel {
        RemoteChannel(ChannelHandler... handlers) { super(handlers); }
        @Override protected SocketAddress remoteAddress0() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 25566);
        }
    }

    private static final class Fallback extends ChannelInboundHandlerAdapter {
        ByteBuf message;
        int calls, readerIndex;
        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
            calls++;
            message = (ByteBuf) msg;
            readerIndex = message.readerIndex();
        }
        void release() { if (message != null && message.refCnt() > 0) message.release(); }
    }

    private static ByteBuf request(boolean direct, String channelName, int protocol, int port) {
        String host = "localhost";
        ByteBuf bytes = direct ? Unpooled.directBuffer(256) : Unpooled.buffer(256);
        bytes.writeByte(254).writeByte(1).writeByte(250);
        bytes.writeShort(channelName.length()).writeBytes(channelName.getBytes(Charsets.UTF_16BE));
        bytes.writeShort(7 + 2 * host.length()).writeByte(protocol);
        bytes.writeShort(host.length()).writeBytes(host.getBytes(Charsets.UTF_16BE));
        bytes.writeInt(port);
        return bytes;
    }

    private static void assertReply(ByteBuf bytes) {
        assertNotNull("legacy ping must produce a reply", bytes);
        try {
            assertEquals("outbound queue owns one live reference", 1, bytes.refCnt());
            assertEquals(255, bytes.readUnsignedByte());
            int chars = bytes.readUnsignedShort();
            assertEquals(REPLY, bytes.readSlice(chars * 2).toString(Charsets.UTF_16BE));
            assertFalse(bytes.isReadable());
        } finally { if (bytes.refCnt() > 0) bytes.release(); }
    }

    @Test public void serverReadsLegacy16HeapAndDirectInputAndTransfersOneReplyReference() throws Exception {
        for (boolean direct : new boolean[]{false, true}) {
            for (boolean readOnly : new boolean[]{false, true}) {
                Fallback fallback = new Fallback();
                RemoteChannel channel = new RemoteChannel();
                channel.pipeline().addLast("legacy_query", new PingResponseHandler(network()));
                channel.pipeline().addLast("next_protocol", fallback);
                ByteBuf backing = request(direct, "MC|PingHost", 127, 25566);
                ByteBuf input = readOnly ? backing.asReadOnly() : backing;
                try {
                    channel.writeInbound(input);
                    assertEquals("valid legacy input must not fall through", 0, fallback.calls);
                    assertEquals("inbound request must be consumed once", 0, backing.refCnt());
                    assertReply(channel.readOutbound());
                    assertFalse(channel.isOpen());
                } finally {
                    channel.finishAndReleaseAll();
                    fallback.release();
                    if (backing.refCnt() > 0) backing.release();
                }
            }
        }
    }

    @Test public void serverPreservesFallbackMessageOwnershipAndOriginalReaderPosition() throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            Fallback fallback = new Fallback();
            RemoteChannel channel = new RemoteChannel();
            channel.pipeline().addLast("legacy_query", new PingResponseHandler(network()));
            channel.pipeline().addLast("next_protocol", fallback);
            ByteBuf input = kind == 0
                    ? Unpooled.directBuffer(32).writeBytes(new byte[]{9, 9, 0, 1, 2})
                    : request(true, kind == 1 ? "MC|BadHost" : "MC|PingHost", 127, kind == 2 ? 65536 : 25566);
            if (kind == 0) input.readerIndex(2);
            if (kind == 3) input.writerIndex(input.writerIndex() - 1);
            int initialIndex = input.readerIndex();
            try {
                channel.writeInbound(input);
                assertEquals(1, fallback.calls);
                assertSame(input, fallback.message);
                assertEquals(initialIndex, fallback.readerIndex);
                assertEquals("fallback owns the original message", 1, input.refCnt());
                assertNull(channel.pipeline().get("legacy_query"));
                assertNull(channel.readOutbound());
                assertTrue(channel.isOpen());
            } finally {
                channel.finishAndReleaseAll();
                fallback.release();
                if (input.refCnt() > 0) input.release();
            }
            assertEquals(0, input.refCnt());
        }
    }

    private static final class FailingChannel extends EmbeddedChannel {
        ByteBuf rejected;
        FailingChannel(ChannelHandler... handlers) { super(handlers); }
        @Override protected SocketAddress remoteAddress0() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 25566);
        }
        @Override protected void doWrite(ChannelOutboundBuffer outbound) {
            while (outbound.current() != null) {
                rejected = (ByteBuf) outbound.current();
                outbound.remove(new java.io.IOException("synthetic write failure"));
            }
        }
    }

    @Test public void failedClientQueryWriteReleasesBothReferences() {
        ServerData server = data();
        FailingChannel channel = new FailingChannel(OldServerPinger.compatibilityHandler(
                server, ServerAddress.func_78860_a(server.serverIP)));
        try {
            assertNotNull(channel.rejected);
            assertEquals(0, channel.rejected.refCnt());
            assertFalse(channel.isOpen());
            assertNull(channel.readOutbound());
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test public void failedServerReplyWriteReleasesResponseAndInput() throws Exception {
        FailingChannel channel = new FailingChannel();
        channel.pipeline().addLast("legacy_query", new PingResponseHandler(network()));
        ByteBuf input = request(true, "MC|PingHost", 127, 25566);
        try {
            channel.writeInbound(input);
            assertNotNull(channel.rejected);
            assertEquals(0, channel.rejected.refCnt());
            assertEquals(0, input.refCnt());
            assertFalse(channel.isOpen());
            assertNull(channel.readOutbound());
        } finally {
            channel.finishAndReleaseAll();
            if (input.refCnt() > 0) input.release();
        }
    }

    private static ServerData data() {
        ServerData server = new ServerData("Fixture", "127.0.0.1:25566", false);
        server.serverMOTD = "unchanged";
        server.populationInfo = "unchanged";
        return server;
    }

    private static EmbeddedChannel client(ServerData server) {
        // Add to an already active channel so decoder tests do not send a query.
        // Explicit non-default port avoids ServerAddress's SRV lookup.
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(OldServerPinger.compatibilityHandler(
                server, ServerAddress.func_78860_a(server.serverIP)));
        return channel;
    }

    private static void discardQuery(EmbeddedChannel channel) {
        ByteBuf query = channel.readOutbound();
        assertNotNull(query);
        try {
            assertEquals("outbound legacy query must remain owned by its queue", 1, query.refCnt());
            assertEquals(254, query.readUnsignedByte());
            assertEquals(1, query.readUnsignedByte());
            assertEquals(250, query.readUnsignedByte());
        } finally { if (query.refCnt() > 0) query.release(); }
    }

    @Test public void clientQueryTransfersExactlyOneLiveReference() {
        ServerData server = data();
        EmbeddedChannel channel = new EmbeddedChannel(OldServerPinger.compatibilityHandler(
                server, ServerAddress.func_78860_a(server.serverIP)));
        try { discardQuery(channel); }
        finally { channel.finishAndReleaseAll(); }
    }

    @Test public void clientReadsLegacy16DirectAndReadOnlyResponsesAndReleasesEachInput() {
        for (boolean readOnly : new boolean[]{false, true}) {
            ServerData server = data();
            EmbeddedChannel channel = client(server);
            ByteBuf backing = Unpooled.directBuffer(256);
            backing.writeByte(255).writeShort(REPLY.length()).writeBytes(REPLY.getBytes(Charsets.UTF_16BE));
            ByteBuf input = readOnly ? backing.asReadOnly() : backing;
            try {
                assertNull(channel.readOutbound());
                channel.writeInbound(input);
                assertEquals(-1, server.version);
                assertEquals("1.8.9-fixture", server.gameVersion);
                assertEquals("Gr\u00fc\u00dfe", server.serverMOTD);
                assertEquals(EnumChatFormatting.GRAY + "2" + EnumChatFormatting.DARK_GRAY
                        + "/" + EnumChatFormatting.GRAY + "20", server.populationInfo);
                assertEquals(0, backing.refCnt());
                assertFalse(channel.isOpen());
            } finally {
                channel.finishAndReleaseAll();
                if (backing.refCnt() > 0) backing.release();
            }
        }
    }

    @Test public void clientRejectsTruncatedDirectResponseWithoutChangingServerData() {
        ServerData server = data();
        EmbeddedChannel channel = client(server);
        ByteBuf bytes = Unpooled.directBuffer(128);
        bytes.writeByte(255).writeShort(12).writeBytes(new byte[]{0, 'x'});
        try {
            assertNull(channel.readOutbound());
            channel.writeInbound(bytes);
            assertEquals(47, server.version);
            assertEquals("1.8.9", server.gameVersion);
            assertEquals("unchanged", server.serverMOTD);
            assertEquals("unchanged", server.populationInfo);
            assertEquals(0, bytes.refCnt());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
            if (bytes.refCnt() > 0) bytes.release();
        }
    }
}
