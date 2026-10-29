package me.youngjibbit.core.network;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NettyCompressionDecoder;
import net.minecraft.network.NettyCompressionEncoder;
import net.minecraft.network.NetworkManager;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * NET-001: a later positive threshold must update both installed compression handlers
 * and a negative threshold must remove both. The decoder/encoder stubs stand in for
 * the vanilla splitter/decoder/prepender/encoder chain; no socket, server or account is used.
 */
public class CompressionPipelineTest {

    @Test
    public void recompressionUpdatesBothHandlersAndNegativeThresholdRemovesBoth() throws Exception {
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network);
        try {
            channel.pipeline().addFirst("encoder", new ChannelOutboundHandlerAdapter());
            channel.pipeline().addFirst("decoder", new ChannelInboundHandlerAdapter());

            network.setCompressionTreshold(256);
            assertTrue(channel.pipeline().get("decompress") instanceof NettyCompressionDecoder);
            assertTrue(channel.pipeline().get("compress") instanceof NettyCompressionEncoder);
            assertEquals(256, threshold(channel.pipeline().get("decompress")));
            assertEquals(256, threshold(channel.pipeline().get("compress")));

            network.setCompressionTreshold(64);
            assertTrue(channel.pipeline().get("decompress") instanceof NettyCompressionDecoder);
            assertTrue(channel.pipeline().get("compress") instanceof NettyCompressionEncoder);
            assertEquals(64, threshold(channel.pipeline().get("decompress")));
            assertEquals(64, threshold(channel.pipeline().get("compress")));

            network.setCompressionTreshold(-1);
            assertNull(channel.pipeline().get("decompress"));
            assertNull(channel.pipeline().get("compress"));
        } finally {
            channel.finish();
        }
    }

    private static int threshold(Object handler) throws Exception {
        Field field = handler.getClass().getDeclaredField("treshold");
        field.setAccessible(true);
        return field.getInt(handler);
    }
}
