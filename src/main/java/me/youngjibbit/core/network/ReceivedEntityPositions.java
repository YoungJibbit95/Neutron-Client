package me.youngjibbit.core.network;

import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** Receipt coordinates while movement applications are pending. Never derives a second delta from delayed serverPos. */
final class ReceivedEntityPositions {
    private final Map<Integer, Pending> pending = new HashMap<>();

    Runnable receive(World world, Packet<?> packet) {
        int id;
        if (packet instanceof S14PacketEntity) id = ((S14PacketEntity) packet).getEntityId();
        else if (packet instanceof S18PacketEntityTeleport) id = ((S18PacketEntityTeleport) packet).getEntityId();
        else return () -> { };
        Entity entity = world == null ? null : world.getEntityByID(id);
        if (entity == null) return () -> { };
        pending.values().removeIf(p -> p.world.getEntityByID(p.entity.getEntityId()) != p.entity);
        Pending value = pending.get(id);
        if (value == null || value.entity != entity || value.world != world) {
            // A real play handler completes immediate packets on this thread. Pending entries are bounded by the queue.
            if (pending.size() >= PacketDelays.MAX_PACKETS + 1) throw new IllegalStateException("Received position bound exceeded");
            value = new Pending(entity, world);
            pending.put(id, value);
        }
        value.position.receive(packet);
        value.applications++;
        Pending receipt = value;
        return () -> {
            if (--receipt.applications == 0 && pending.get(id) == receipt) pending.remove(id);
        };
    }

    Vec3 position(Entity entity) {
        if (entity == null || entity.worldObj == null || entity.worldObj.getEntityByID(entity.getEntityId()) != entity) return null;
        Pending value = pending.get(entity.getEntityId());
        return value != null && value.entity == entity && value.world == entity.worldObj ? value.position.position()
                : new Vec3(entity.serverPosX / 32.0, entity.serverPosY / 32.0, entity.serverPosZ / 32.0);
    }

    void clear() { pending.clear(); }

    private static final class Pending {
        final Entity entity;
        final World world;
        final ReceivedEntityPosition position = new ReceivedEntityPosition();
        int applications;
        Pending(Entity entity, World world) {
            this.entity = entity;
            this.world = world;
            position.bind(entity, world, this, entity.getEntityId(), entity.serverPosX, entity.serverPosY, entity.serverPosZ);
        }
    }
}
