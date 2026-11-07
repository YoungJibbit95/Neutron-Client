package net.minecraft.client.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.local.LocalChannel;
import java.net.SocketAddress;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class IntegratedServerTransportTest {
    @Test(timeout = 10000)
    public void integratedServerAcceptsLocalClient() {
        NetworkSystem server = new NetworkSystem(null);
        try {
            SocketAddress address = server.addLocalEndpoint();
            Channel client = new Bootstrap().group(NetworkManager.CLIENT_LOCAL_EVENTLOOP.getValue())
                    .channel(LocalChannel.class).handler(new ChannelInboundHandlerAdapter())
                    .connect(address).syncUninterruptibly().channel();
            try {
                assertTrue(client.isActive());
            } finally {
                client.close().syncUninterruptibly();
            }
        } finally {
            server.terminateEndpoints();
        }
    }
}
