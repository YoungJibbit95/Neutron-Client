package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.network.play.server.*;
import org.junit.Test;

import static org.junit.Assert.*;

/** Real NetHandlerPlayClient movement handlers, also before any Backtrack target has been selected. */
public class ReceivedPositionHookTest {
    static EntityOtherPlayerMP target(AttackHookTest.WorldStub world, int x) throws Exception {
        EntityOtherPlayerMP entity = AttackHookTest.bare(EntityOtherPlayerMP.class);
        entity.worldObj = world; entity.setEntityId(7); entity.serverPosX = x;
        world.target = entity;
        return entity;
    }
    static S14PacketEntity move(int x) {
        return new S14PacketEntity.S15PacketEntityRelMove(7, (byte) x, (byte) 0, (byte) 0, true);
    }
    @Test public void delayedDeltasBeforeTargetSelectionAccumulateAndSurvivePartialRelease() throws Exception {
        try (SessionConsumerIntegrationTest.Fixture f = new SessionConsumerIntegrationTest.Fixture()) {
            AttackHookTest.WorldStub world = AttackHookTest.bare(AttackHookTest.WorldStub.class);
            EntityOtherPlayerMP entity = target(world, 96); f.play.clientWorldController = world;
            PacketPipeline pipe = f.network.getClientPackets(); Object owner = new Object();
            pipe.delays().request(owner, 500, (p, flow) -> true);
            f.channel.writeInbound(move(16)); f.channel.writeInbound(move(16));
            PacketDelays.Snapshot snapshot = pipe.delays().snapshot(owner);
            assertEquals(2, snapshot.size); assertEquals(2, snapshot.heldByOwner); assertEquals(1, snapshot.activeOwners);
            assertEquals(96, entity.serverPosX); assertEquals(4, pipe.receivedPosition(entity).xCoord, 0);
            pipe.delays().release(owner);
            assertEquals(128, entity.serverPosX); assertEquals(4, pipe.receivedPosition(entity).xCoord, 0);
            f.channel.writeInbound(move(-16));
            assertEquals(3.5, pipe.receivedPosition(entity).xCoord, 0);
        }
    }
    @Test public void finalReplacementCancellationSilentTeleportAndIdReuseCannotCorruptReceiptCoordinates() throws Exception {
        try (SessionConsumerIntegrationTest.Fixture f = new SessionConsumerIntegrationTest.Fixture()) {
            AttackHookTest.WorldStub world = AttackHookTest.bare(AttackHookTest.WorldStub.class);
            EntityOtherPlayerMP entity = target(world, 96); f.play.clientWorldController = world;
            PacketPipeline pipe = f.network.getClientPackets(); Object owner = new Object();
            PacketFlowTest.Observer events = new PacketFlowTest.Observer(); Pulsar.BUS.subscribe(events);
            try {
                pipe.delays().request(owner, 500, (p, flow) -> true);
                events.action = e -> { if (e.isInbound() && e.isPre()) e.setPacket(move(16)); };
                f.channel.writeInbound(move(32)); assertEquals(3.5, pipe.receivedPosition(entity).xCoord, 0);
                events.action = e -> { if (e.isPre()) e.cancel(); };
                f.channel.writeInbound(move(32)); assertEquals(3.5, pipe.receivedPosition(entity).xCoord, 0);
                events.action = e -> { };
                f.network.receivePacket(move(16), true); // silent application also updates the receipt ledger
                assertEquals(128, entity.serverPosX); assertEquals(4, pipe.receivedPosition(entity).xCoord, 0);
                S18PacketEntityTeleport teleport = new S18PacketEntityTeleport(7, 224, 0, 0, (byte) 0, (byte) 0, true);
                f.channel.writeInbound(teleport); assertEquals(7, pipe.receivedPosition(entity).xCoord, 0);
                pipe.delays().request(owner, 500, (p, flow) -> true); f.channel.writeInbound(move(16));
                EntityOtherPlayerMP replacement = target(world, 32);
                assertNull(pipe.receivedPosition(entity)); assertEquals(1, pipe.receivedPosition(replacement).xCoord, 0);
                pipe.delays().release(owner); assertEquals(32, replacement.serverPosX);
                f.channel.writeInbound(move(16)); assertEquals(1.5, pipe.receivedPosition(replacement).xCoord, 0);
            } finally { Pulsar.BUS.unsubscribe(events); }
        }
    }
    @Test public void sceneAndConnectionLossDiscardPendingCoordinateHistory() throws Exception {
        try (SessionConsumerIntegrationTest.Fixture f = new SessionConsumerIntegrationTest.Fixture()) {
            AttackHookTest.WorldStub world = AttackHookTest.bare(AttackHookTest.WorldStub.class);
            EntityOtherPlayerMP entity = target(world, 96); f.play.clientWorldController = world;
            PacketPipeline pipe = f.network.getClientPackets();
            pipe.delays().request(this, 500, (p, flow) -> true); f.channel.writeInbound(move(16));
            assertEquals(3.5, pipe.receivedPosition(entity).xCoord, 0);
            pipe.worldChanged(); assertEquals(3, pipe.receivedPosition(entity).xCoord, 0);
            pipe.pump(); assertEquals(96, entity.serverPosX);
            pipe.close(); assertNull(pipe.receivedPosition(entity));
        }
    }
}
