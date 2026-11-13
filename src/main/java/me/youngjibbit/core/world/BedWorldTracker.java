package me.youngjibbit.core.world;

import me.youngjibbit.core.network.PacketPipeline;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Blocks;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Incremental loaded-section observer. Rendering only reads its bounded immutable snapshots. */
public final class BedWorldTracker {
    public static final double MAX_RANGE = 128;
    public static final int MAX_BEDS = 64;
    public static final int MAX_CELLS_PER_TICK = 32768;
    public static final int MAX_SECTION_VISITS_PER_TICK = 64;
    public static final int MAX_DISCOVERIES_PER_TICK = 16;
    public static final int MAX_REFRESHES_PER_TICK = 8;

    public interface Access extends BedObservation.Access {
        Object worldIdentity();
        Object connectionIdentity();
        long connectionEpoch();
        long tick();
        Vec3 position();
        /** Null for an unloaded chunk or missing section; this method must never load a chunk. */
        ExtendedBlockStorage sectionIfLoaded(int chunkX, int sectionY, int chunkZ);
    }

    private Object world, connection;
    private long epoch, lastTick = Long.MIN_VALUE;
    private final Map<BlockPos, BedObservation> beds = new LinkedHashMap<>();
    private List<BedObservation> published = Collections.emptyList();
    private List<Column> columns = Collections.emptyList();
    private int minX, maxX, minZ, maxZ, columnCursor, sectionCursor, refreshCursor;
    private int[] sectionOrder = new int[16];
    private int sectionCenter = -1;
    private double range;
    private boolean gridReady;

    public void update(Minecraft client, double range) { update(clientAccess(client), range); }
    public List<BedObservation> snapshot(Minecraft client) { return snapshot(clientAccess(client)); }

    /** Call for WorldEvent.Unload/Load and module disable, including unload before client fields are replaced. */
    public void invalidate() {
        world = null; connection = null; epoch = 0; lastTick = Long.MIN_VALUE;
        beds.clear(); published = Collections.emptyList(); columns = Collections.emptyList();
        columnCursor = sectionCursor = refreshCursor = 0; sectionCenter = -1; gridReady = false;
    }

    public void update(Access access, double requestedRange) {
        if (access == null || !access.current() || !finite(access.position()) || !Double.isFinite(requestedRange) || requestedRange <= 0) {
            invalidate(); return;
        }
        if (!sameContext(access)) {
            invalidate(); world = access.worldIdentity(); connection = access.connectionIdentity(); epoch = access.connectionEpoch();
        }
        if (!access.current()) { invalidate(); return; }
        range = Math.min(MAX_RANGE, requestedRange);
        Vec3 position = access.position();
        prune(access, position);
        if (!access.current()) { invalidate(); return; }
        if (access.tick() <= lastTick) { publish(position); return; }
        lastTick = access.tick();
        prepareGrid(position);
        refresh(access);
        if (!access.current()) { invalidate(); return; }
        int cells = 0, visits = 0, discovered = 0;
        while (!columns.isEmpty() && visits++ < Math.min(MAX_SECTION_VISITS_PER_TICK, columns.size() * 16) && cells < MAX_CELLS_PER_TICK) {
            if (!access.current()) { invalidate(); return; }
            Column column = columns.get(columnCursor);
            int sectionY = sectionOrder[sectionCursor];
            advance();
            ExtendedBlockStorage section = access.sectionIfLoaded(column.x, sectionY, column.z);
            if (!access.current()) { invalidate(); return; }
            if (section == null || section.isEmpty()) continue;
            // A complete section fits the remaining budget (32768 is a multiple of 4096).
            for (int cell = 0; cell < 4096; cell++) {
                cells++;
                if (!access.current()) { invalidate(); return; }
                int x = cell & 15, y = cell >>> 8, z = cell >>> 4 & 15;
                IBlockState state = section.get(x, y, z);
                if (!(state.getBlock() instanceof BlockBed) || discovered >= MAX_DISCOVERIES_PER_TICK || beds.size() >= MAX_BEDS) continue;
                BlockPos pos = new BlockPos((column.x << 4) + x, (sectionY << 4) + y, (column.z << 4) + z);
                if (!within(pos.getX() + .5, pos.getY() + .3, pos.getZ() + .5, position, range)) continue;
                BlockPos foot = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD
                        ? pos.offset(state.getValue(BlockBed.FACING).getOpposite()) : pos;
                if (beds.containsKey(foot)) continue;
                discovered++;
                BedObservation observation = BedObservation.capture(access, pos, access.tick());
                if (!access.current()) { invalidate(); return; }
                if (observation != null) beds.put(observation.foot, observation);
            }
        }
        prune(access, position);
        if (!access.current()) { invalidate(); return; }
        publish(position);
    }

    /** No scans here: at most 64 cached bed pairs are checked for world/session/chunk/bed invalidation. */
    public List<BedObservation> snapshot(Access access) {
        if (access == null || !access.current() || !sameContext(access) || !finite(access.position())) {
            invalidate(); return Collections.emptyList();
        }
        int before = beds.size();
        prune(access, access.position());
        if (!access.current()) { invalidate(); return Collections.emptyList(); }
        if (before != beds.size()) publish(access.position());
        return published;
    }

    private boolean sameContext(Access access) {
        return world != null && world == access.worldIdentity() && connection == access.connectionIdentity()
                && epoch == access.connectionEpoch();
    }
    private void prune(Access access, Vec3 position) {
        Iterator<BedObservation> iterator = beds.values().iterator();
        while (iterator.hasNext()) {
            BedObservation bed = iterator.next();
            if (!access.current() || !within((bed.bounds.minX + bed.bounds.maxX) / 2, bed.bounds.minY + .3,
                    (bed.bounds.minZ + bed.bounds.maxZ) / 2, position, range) || !bed.stillPresent(access)) iterator.remove();
        }
    }
    private void refresh(Access access) {
        if (beds.isEmpty()) { refreshCursor = 0; return; }
        List<BlockPos> keys = new ArrayList<>(beds.keySet());
        int count = Math.min(MAX_REFRESHES_PER_TICK, keys.size());
        for (int i = 0; i < count; i++) {
            BlockPos key = keys.get((refreshCursor + i) % keys.size());
            BedObservation previous = beds.get(key);
            if (previous == null) continue;
            BedObservation next = BedObservation.capture(access, previous.presentHalf(), access.tick());
            if (!access.current()) return;
            if (next == null) beds.remove(key); else beds.put(key, next);
        }
        refreshCursor = (refreshCursor + count) % keys.size();
    }
    private void publish(Vec3 position) {
        List<BedObservation> result = new ArrayList<>(beds.values());
        result.sort(Comparator.comparingDouble(value -> value.foot.distanceSq(position.xCoord, position.yCoord, position.zCoord)));
        published = Collections.unmodifiableList(result);
    }
    private void prepareGrid(Vec3 position) {
        int lowX = MathHelper.floor_double(position.xCoord - range) >> 4, highX = MathHelper.floor_double(position.xCoord + range) >> 4;
        int lowZ = MathHelper.floor_double(position.zCoord - range) >> 4, highZ = MathHelper.floor_double(position.zCoord + range) >> 4;
        if (!gridReady || lowX != minX || highX != maxX || lowZ != minZ || highZ != maxZ) {
            minX = lowX; maxX = highX; minZ = lowZ; maxZ = highZ; gridReady = true;
            List<Column> next = new ArrayList<>();
            for (int x = lowX; x <= highX; x++) for (int z = lowZ; z <= highZ; z++) next.add(new Column(x, z));
            next.sort(Comparator.comparingDouble((Column column) -> square(column.x * 16D + 8 - position.xCoord)
                    + square(column.z * 16D + 8 - position.zCoord)).thenComparingInt(column -> column.x).thenComparingInt(column -> column.z));
            columns = next; columnCursor = sectionCursor = 0;
        }
        int center = Math.max(0, Math.min(15, MathHelper.floor_double(position.yCoord) >> 4));
        if (center != sectionCenter) {
            sectionCenter = center;
            List<Integer> order = new ArrayList<>(); for (int y = 0; y < 16; y++) order.add(y);
            order.sort(Comparator.comparingInt((Integer value) -> Math.abs(value - center)).thenComparingInt(value -> value));
            for (int index = 0; index < 16; index++) sectionOrder[index] = order.get(index);
            sectionCursor = 0;
        }
    }
    private void advance() {
        if (++sectionCursor == 16) { sectionCursor = 0; columnCursor = (columnCursor + 1) % columns.size(); }
    }
    private static boolean finite(Vec3 point) {
        return DemiseBedBreakerGeometry.finite(point) && Math.abs(point.xCoord) <= 30_000_000 && Math.abs(point.zCoord) <= 30_000_000;
    }
    private static double square(double value) { return value * value; }
    private static boolean within(double x, double y, double z, Vec3 point, double range) {
        return square(x - point.xCoord) + square(y - point.yCoord) + square(z - point.zCoord) <= range * range;
    }
    private static final class Column {
        final int x, z;
        Column(int x, int z) { this.x = x; this.z = z; }
    }

    /** Loaded-only block read shared with the read-only mining estimate. Never uses chunkExists (always true on this client). */
    public static IBlockState stateIfLoaded(WorldClient world, BlockPos pos) {
        if (world == null || pos == null || !(world.getChunkProvider() instanceof ChunkProviderClient)) return null;
        if (pos.getY() < 0 || pos.getY() >= 256) return Blocks.air.getDefaultState();
        ChunkProviderClient provider = (ChunkProviderClient) world.getChunkProvider();
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        Chunk chunk = provider.provideChunk(x, z);
        if (chunk == null || !chunk.isLoaded() || chunk.xPosition != x || chunk.zPosition != z) return null;
        ExtendedBlockStorage section = chunk.getBlockStorageArray()[pos.getY() >> 4];
        return section == null ? Blocks.air.getDefaultState() : section.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    private static Access clientAccess(Minecraft client) {
        if (client == null || !client.isCallingFromMinecraftThread() || client.theWorld == null || client.thePlayer == null
                || client.thePlayer.sendQueue == null || !(client.theWorld.getChunkProvider() instanceof ChunkProviderClient)) return null;
        final WorldClient world = client.theWorld;
        final EntityPlayerSP player = client.thePlayer;
        final NetHandlerPlayClient handler = player.sendQueue;
        final NetworkManager network = handler.getNetworkManager();
        if (network == null || !network.isChannelOpen()) return null;
        final PacketPipeline packets = network.getClientPackets();
        if (packets == null || !packets.isActive()) return null;
        final long epoch = packets.getContextVersion();
        final ChunkProviderClient provider = (ChunkProviderClient) world.getChunkProvider();
        return new Access() {
            public Object worldIdentity() { return world; }
            public Object connectionIdentity() { return network; }
            public long connectionEpoch() { return epoch; }
            public long tick() { return player.ticksExisted; }
            public Vec3 position() { return new Vec3(player.posX, player.posY, player.posZ); }
            public boolean current() {
                return client.theWorld == world && client.thePlayer == player && player.worldObj == world && player.sendQueue == handler
                        && handler.getNetworkManager() == network && network.isChannelOpen() && network.getClientPackets() == packets
                        && packets.isActive() && packets.getContextVersion() == epoch && world.getChunkProvider() == provider;
            }
            private Chunk loaded(int x, int z) {
                // ChunkProviderClient.provideChunk is a map lookup returning its unloaded blank sentinel; it never generates/loads.
                Chunk chunk = provider.provideChunk(x, z);
                return chunk != null && chunk.isLoaded() && chunk.xPosition == x && chunk.zPosition == z ? chunk : null;
            }
            public ExtendedBlockStorage sectionIfLoaded(int x, int y, int z) {
                Chunk chunk = loaded(x, z);
                return chunk == null || y < 0 || y >= 16 ? null : chunk.getBlockStorageArray()[y];
            }
            public IBlockState stateIfLoaded(BlockPos pos) {
                if (!current()) return null;
                if (pos.getY() < 0 || pos.getY() >= 256) return Blocks.air.getDefaultState();
                Chunk chunk = loaded(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null) return null;
                ExtendedBlockStorage section = chunk.getBlockStorageArray()[pos.getY() >> 4];
                return section == null ? Blocks.air.getDefaultState() : section.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
            }
        };
    }
}
