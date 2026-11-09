package me.neutron.testserver;

import net.minecraft.server.v1_8_R3.EnumProtocolDirection;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.NetworkManager;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PlayerConnection;

/**
 * A PlayerConnection with no socket.  EntityPlayer uses the connection for
 * inventory/equipment/health packets; a fake player must never write those to
 * a real channel, so every outbound packet is discarded here.
 */
public final class FakePlayerConnection extends PlayerConnection {

    public FakePlayerConnection(MinecraftServer server, net.minecraft.server.v1_8_R3.EntityPlayer player) {
        super(server, new NetworkManager(EnumProtocolDirection.SERVERBOUND), player);
    }

    @Override
    public void sendPacket(Packet packet) {
        // Intentionally empty: fake players have no client.
    }

    @Override
    public void disconnect(String reason) {
        // Intentionally empty: fake players are removed by the bot manager.
    }

    @Override
    public boolean isDisconnected() {
        return false;
    }
}
