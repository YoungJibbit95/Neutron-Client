package me.youngjibbit.core.network;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.optifine.player.PlayerConfiguration;
import net.optifine.player.PlayerConfigurations;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** Real spawn/list handlers and packet handoff, with no renderer, HTTP downloads or server. */
public class PlayerSpawnProfileTest {
    private static final String NAME = "SpawnFixture";

    public static class SpawnWorld extends WorldClient {
        Map<Integer, Entity> entities;
        int additions;
        Runnable afterAdd;
        public SpawnWorld() { super(null, null, 0, null, null); }
        @Override public BlockPos getSpawnPoint() { return new BlockPos(0, 64, 0); }
        @Override public Entity getEntityByID(int id) { return entities.get(id); }
        @Override public void addEntityToWorld(int id, Entity entity) {
            entity.setEntityId(id);
            entities.put(id, entity);
            additions++;
            if (afterAdd != null) afterAdd.run();
        }
        @Override public Entity removeEntityFromWorld(int id) { return entities.remove(id); }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static SpawnWorld world() throws Exception {
        SpawnWorld world = AttackHookTest.bare(SpawnWorld.class);
        world.entities = new HashMap<>();
        field(World.class, "provider").set(world, new WorldProviderSurface());
        field(World.class, "isRemote").setBoolean(world, true);
        return world;
    }

    private static final class Fixture implements AutoCloseable {
        final PacketFlowTest.MainThread main = new PacketFlowTest.MainThread();
        final SessionConsumerIntegrationTest.Client client = AttackHookTest.bare(SessionConsumerIntegrationTest.Client.class);
        final NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel(network);
        final SpawnWorld world = world();
        final Object oldClient = field(Minecraft.class, "theMinecraft").get(null);
        final Object oldPlayerConfigurations = field(PlayerConfigurations.class, "mapConfigurations").get(null);
        final NetHandlerPlayClient play;

        Fixture() throws Exception {
            Bootstrap.register();
            client.main = main;
            client.theWorld = world;
            ThreadDownloadImageData cached = new ThreadDownloadImageData(null, null, null, null);
            cached.imageFound = false;
            field(Minecraft.class, "renderEngine").set(client, new TextureManager(null) {
                @Override public ITextureObject getTexture(ResourceLocation location) { return cached; }
            });
            field(Minecraft.class, "theMinecraft").set(null, client);
            Map<String, PlayerConfiguration> configurations = new HashMap<>();
            configurations.put(NAME, new PlayerConfiguration());
            field(PlayerConfigurations.class, "mapConfigurations").set(null, configurations);
            play = new NetHandlerPlayClient(client, null, network, null);
            play.clientWorldController = world;
            network.setNetHandler(play);
        }

        void incoming(Runnable operation) throws Exception {
            Thread netty = new Thread(operation);
            netty.start();
            netty.join();
            main.drain();
        }

        @Override public void close() throws Exception {
            channel.finish();
            main.drain();
            field(PlayerConfigurations.class, "mapConfigurations").set(null, oldPlayerConfigurations);
            field(Minecraft.class, "theMinecraft").set(null, oldClient);
        }
    }

    private static S0CPacketSpawnPlayer spawn(int id, UUID uuid, int x) throws Exception {
        PacketBuffer data = new PacketBuffer(Unpooled.buffer());
        try {
            data.writeVarIntToBuffer(id);
            data.writeUuid(uuid);
            data.writeInt(x);
            data.writeInt(64 * 32);
            data.writeInt(3 * 32);
            data.writeByte(64);
            data.writeByte(16);
            data.writeShort(0);
            data.writeByte(0); // One valid byte metadata entry, followed by the metadata terminator.
            data.writeByte(0);
            data.writeByte(127);
            S0CPacketSpawnPlayer packet = new S0CPacketSpawnPlayer();
            packet.readPacketData(data);
            return packet;
        } finally { data.release(); }
    }

    private static S38PacketPlayerListItem info(UUID uuid, S38PacketPlayerListItem.Action action) {
        S38PacketPlayerListItem packet = new S38PacketPlayerListItem(action, new EntityPlayerMP[0]);
        packet.func_179767_a().add(packet.new AddPlayerData(new GameProfile(uuid, NAME), 42,
                WorldSettings.GameType.SURVIVAL, null));
        return packet;
    }

    private static void addProfile(Fixture f, UUID uuid) {
        f.channel.writeInbound(info(uuid, S38PacketPlayerListItem.Action.ADD_PLAYER));
    }

    @Test public void normalSpawnUsesAnnouncedProfileAndWireState() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID uuid = UUID.randomUUID();
            addProfile(f, uuid);
            f.channel.writeInbound(spawn(17, uuid, 80));
            EntityOtherPlayerMP player = (EntityOtherPlayerMP) f.world.getEntityByID(17);
            assertNotNull(player);
            assertSame(f.play.getPlayerInfo(uuid).getGameProfile(), player.getGameProfile());
            assertEquals(2.5, player.posX, 0);
            assertEquals(64, player.posY, 0);
            assertEquals(90, player.rotationYaw, 0);
            assertEquals(1, f.world.additions);
            assertTrue(f.network.getClientPackets().isActive());
        }
    }

    @Test public void spawnBeforePlayerInfoDoesNotDisconnectAndIsRecovered() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID uuid = UUID.randomUUID();
            S0CPacketSpawnPlayer packet = spawn(17, uuid, 80);
            f.incoming(() -> f.channel.writeInbound(packet));
            assertTrue("A missing tablist profile must not disconnect the client", f.channel.isOpen());
            assertNull(f.world.getEntityByID(17));
            f.incoming(() -> addProfile(f, uuid));
            EntityOtherPlayerMP player = (EntityOtherPlayerMP) f.world.getEntityByID(17);
            assertNotNull(player);
            assertEquals(uuid, player.getUniqueID());
            assertEquals(NAME, player.getName());
            assertEquals(80, player.serverPosX);
            addProfile(f, uuid);
            assertEquals("A repeated profile update must not replay the spawn twice", 1, f.world.additions);
        }
    }

    @Test public void latestPendingSpawnWinsAndDestroyCancelsIt() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID uuid = UUID.randomUUID();
            f.play.handleSpawnPlayer(spawn(17, uuid, 80));
            f.play.handleSpawnPlayer(spawn(17, uuid, 160));
            addProfile(f, uuid);
            assertEquals(5.0, f.world.getEntityByID(17).posX, 0);
            UUID removed = UUID.randomUUID();
            f.play.handleSpawnPlayer(spawn(18, removed, 80));
            f.play.handleDestroyEntities(new S13PacketDestroyEntities(18));
            addProfile(f, removed);
            assertNull(f.world.getEntityByID(18));
        }
    }

    @Test public void playerListRemovalCancelsAnUnresolvedSpawn() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID uuid = UUID.randomUUID();
            f.play.handleSpawnPlayer(spawn(17, uuid, 80));
            f.play.handlePlayerListItem(info(uuid, S38PacketPlayerListItem.Action.REMOVE_PLAYER));
            addProfile(f, uuid);
            assertNull(f.world.getEntityByID(17));
        }
    }

    @Test public void worldEpochCleanupAndPipelineBoundariesCannotReplayOldSpawns() throws Exception {
        for (int boundary = 0; boundary < 4; boundary++) {
            try (Fixture f = new Fixture()) {
                UUID uuid = UUID.randomUUID();
                f.play.handleSpawnPlayer(spawn(17, uuid, 80));
                SpawnWorld fresh = f.world;
                if (boundary == 0) {
                    fresh = world();
                    f.client.theWorld = fresh;
                    f.play.clientWorldController = fresh;
                } else if (boundary == 1) f.network.getClientPackets().worldChanged();
                else if (boundary == 2) {
                    f.play.cleanup();
                    f.play.clientWorldController = f.world;
                } else f.network.configureClientPackets(f.client, f.play::observeAcceptedOutbound);
                addProfile(f, uuid);
                assertNull(fresh.getEntityByID(17));
                assertEquals(0, f.world.additions);
            }
        }
    }

    @Test public void aWorldChangeDuringRecoveryStopsTheRemainingSpawns() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID uuid = UUID.randomUUID();
            f.play.handleSpawnPlayer(spawn(17, uuid, 80));
            f.play.handleSpawnPlayer(spawn(18, uuid, 160));
            SpawnWorld replacement = world();
            f.world.afterAdd = () -> {
                f.client.theWorld = replacement;
                f.play.clientWorldController = replacement;
            };
            addProfile(f, uuid);
            assertEquals(1, f.world.additions);
            assertEquals(0, replacement.additions);
            assertNull(replacement.getEntityByID(18));
        }
    }

    @Test public void delayedProfileCannotOverwriteAReusedEntityId() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID uuid = UUID.randomUUID();
            f.play.handleSpawnPlayer(spawn(17, uuid, 80));
            Entity replacement = AttackHookTest.bare(EntityOtherPlayerMP.class);
            f.world.addEntityToWorld(17, replacement);
            addProfile(f, uuid);
            assertSame(replacement, f.world.getEntityByID(17));
            assertEquals(1, f.world.additions);
        }
    }

    @Test public void unknownProfilesCannotCreateAnUnboundedSpawnQueue() throws Exception {
        try (Fixture f = new Fixture()) {
            int limit = field(NetHandlerPlayClient.class, "MAX_PENDING_PLAYER_SPAWNS").getInt(null);
            UUID oldest = UUID.randomUUID();
            f.play.handleSpawnPlayer(spawn(1, oldest, 80));
            for (int id = 2; id <= limit + 1; id++) f.play.handleSpawnPlayer(spawn(id, UUID.randomUUID(), 80));
            Map<?, ?> pending = (Map<?, ?>) field(NetHandlerPlayClient.class, "pendingPlayerSpawns").get(f.play);
            assertEquals(limit, pending.size());
            addProfile(f, oldest);
            assertNull(f.world.getEntityByID(1));
            assertTrue(f.channel.isOpen());
        }
    }
}
