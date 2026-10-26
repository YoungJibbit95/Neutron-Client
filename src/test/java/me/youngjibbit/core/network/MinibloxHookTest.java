package me.youngjibbit.core.network;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.exploit.MinibloxFix;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C0CPacketInput;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class MinibloxHookTest {
    @Test public void actualOutboundPathSanitizesOnePacketWithoutRecursiveResendsOrExtraMovement() {
        NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);EmbeddedChannel channel=new EmbeddedChannel(network);
        network.configureClientPackets(new PacketFlowTest.MainThread());MinibloxFix module=new MinibloxFix();PacketFlowTest.Observer events=new PacketFlowTest.Observer();
        module.setToggled(true);Pulsar.BUS.subscribe(events);
        try {
            C0CPacketInput original=new C0CPacketInput(Float.NaN,2.5f,true,false);
            network.sendPacket(original);C0CPacketInput accepted=(C0CPacketInput)channel.readOutbound();assertNotSame(original,accepted);
            assertEquals(0,accepted.getStrafeSpeed(),0);assertEquals(1,accepted.getForwardSpeed(),0);
            assertTrue(accepted.isJumping());assertFalse(accepted.isSneaking());assertNull(channel.readOutbound());
            assertEquals(Arrays.asList("outPre","outPost"),events.calls);
            module.setToggled(false);network.sendPacket(original);assertSame(original,channel.readOutbound());
        }finally {module.setToggled(false);Pulsar.BUS.unsubscribe(events);channel.finish();}
    }
}
