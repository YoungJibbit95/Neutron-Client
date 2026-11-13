package me.youngjibbit.core.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, observed bed geometry. No team ownership or server rules are inferred as facts. */
public final class BedObservation {
    public interface Access {
        /** Null means unavailable, never air. Implementations must not load a chunk. */
        IBlockState stateIfLoaded(BlockPos pos);
        boolean current();
    }
    public static final class MaterialCount {
        public final String id, label;
        public final int count;
        private MaterialCount(String id, String label, int count) { this.id = id; this.label = label; this.count = count; }
    }
    public static final class Layer {
        public final List<MaterialCount> materials;
        public final int occupied, empty, unknown;
        private Layer(Map<String, MaterialCount> materials, int occupied, int empty, int unknown) {
            List<MaterialCount> sorted = new ArrayList<>(materials.values());
            sorted.sort(Comparator.comparingInt((MaterialCount value) -> -value.count).thenComparing(value -> value.id));
            this.materials = Collections.unmodifiableList(sorted);
            this.occupied = occupied; this.empty = empty; this.unknown = unknown;
        }
        public boolean fullyKnown() { return unknown == 0; }
        public boolean complete() { return unknown == 0 && empty == 0; }
    }
    public static final class TeamHint {
        public enum Kind { UNKNOWN, COLOR_HEURISTIC }
        public final Kind kind;
        public final int colorRGB;
        public final String label, source;
        private TeamHint(Kind kind, int colorRGB, String label, String source) {
            this.kind = kind; this.colorRGB = colorRGB; this.label = label; this.source = source;
        }
        private static TeamHint unknown() { return new TeamHint(Kind.UNKNOWN, 0xB9C0CD, "Unknown team", "No bed-team metadata is available"); }
    }

    public final BlockPos foot, head;
    public final EnumFacing facing;
    public final boolean footPresent, headPresent, fullyLoaded;
    public final AxisAlignedBB bounds;
    public final Layer inner, outer;
    public final Set<EnumFacing> openSides;
    public final TeamHint team;
    public final long observedTick;
    private final IBlockState footState, headState;

    private BedObservation(BlockPos foot, BlockPos head, EnumFacing facing, IBlockState footState, IBlockState headState,
                           Layer inner, Layer outer, Set<EnumFacing> openSides, boolean fullyLoaded, TeamHint team, long tick) {
        this.foot = new BlockPos(foot); this.head = new BlockPos(head); this.facing = facing;
        this.footState = footState; this.headState = headState;
        footPresent = footState != null; headPresent = headState != null;
        BlockPos a = footPresent ? foot : head, b = headPresent ? head : foot;
        bounds = new AxisAlignedBB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, a.getY() + .5625, Math.max(a.getZ(), b.getZ()) + 1);
        this.inner = inner; this.outer = outer;
        this.openSides = Collections.unmodifiableSet(EnumSet.copyOf(openSides));
        this.fullyLoaded = fullyLoaded; this.team = team; observedTick = tick;
    }

    public boolean contains(BlockPos pos) { return foot.equals(pos) || head.equals(pos); }
    public boolean completeBed() { return footPresent && headPresent; }
    public boolean incompleteDefense() { return !completeBed() || inner.empty > 0 || inner.unknown > 0 || !openSides.isEmpty(); }
    public BlockPos presentHalf() { return footPresent ? foot : head; }

    /** The old two-half marker must disappear immediately if its chunks or observed bed states disappear. */
    public boolean stillPresent(Access access) {
        if (access == null || !access.current()) return false;
        if (footPresent && !footState.equals(access.stateIfLoaded(foot))) return false;
        if (!access.current()) return false;
        if (headPresent && !headState.equals(access.stateIfLoaded(head))) return false;
        return access.current();
    }

    /** Two face-adjacent shells around the combined footprint, excluding its ground foundation. */
    public static BedObservation capture(Access access, BlockPos part, long tick) {
        if (access == null || part == null || part.getY() < 0 || part.getY() >= 256
                || Math.abs((long) part.getX()) > 30_000_000 || Math.abs((long) part.getZ()) > 30_000_000 || !access.current()) return null;
        IBlockState source = access.stateIfLoaded(part);
        if (!access.current() || source == null || !(source.getBlock() instanceof BlockBed)) return null;
        EnumFacing facing = source.getValue(BlockBed.FACING);
        BlockPos foot = source.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD ? part.offset(facing.getOpposite()) : part;
        BlockPos head = foot.offset(facing);
        IBlockState rawFoot = access.stateIfLoaded(foot);
        if (!access.current()) return null;
        IBlockState rawHead = access.stateIfLoaded(head);
        if (!access.current()) return null;
        IBlockState footState = matches(rawFoot, facing, BlockBed.EnumPartType.FOOT) ? rawFoot : null;
        IBlockState headState = matches(rawHead, facing, BlockBed.EnumPartType.HEAD) ? rawHead : null;
        if (footState == null && headState == null) return null;
        LayerBuilder first = new LayerBuilder(), second = new LayerBuilder();
        Map<EnumDyeColor, Integer> wool = new LinkedHashMap<>();
        boolean allLoaded = rawFoot != null && rawHead != null;
        for (int x = Math.min(foot.getX(), head.getX()) - 2; x <= Math.max(foot.getX(), head.getX()) + 2; x++) {
            for (int y = foot.getY(); y <= foot.getY() + 2; y++) {
                for (int z = Math.min(foot.getZ(), head.getZ()) - 2; z <= Math.max(foot.getZ(), head.getZ()) + 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    int distance = Math.min(distance(pos, foot), distance(pos, head));
                    if (distance < 1 || distance > 2) continue;
                    if (!access.current()) return null;
                    IBlockState state = access.stateIfLoaded(pos);
                    if (!access.current()) return null;
                    (distance == 1 ? first : second).add(state);
                    if (state == null) allLoaded = false;
                    else if (state.getBlock() == Blocks.wool) {
                        EnumDyeColor color = state.getValue(BlockColored.COLOR);
                        wool.put(color, wool.getOrDefault(color, 0) + 1);
                    }
                }
            }
        }
        Set<EnumFacing> open = EnumSet.noneOf(EnumFacing.class);
        for (EnumFacing side : EnumFacing.values()) for (BlockPos bed : new BlockPos[]{foot, head}) {
            if (bed.equals(foot) && footState == null || bed.equals(head) && headState == null) continue;
            BlockPos adjacent = bed.offset(side);
            if (adjacent.equals(foot) || adjacent.equals(head)) continue;
            if (!access.current()) return null;
            IBlockState state = access.stateIfLoaded(adjacent);
            if (!access.current()) return null;
            if (state == null) allLoaded = false;
            else if (open(state)) open.add(side);
        }
        TeamHint hint = TeamHint.unknown();
        // A single, repeated defense-wool color is only a visual cue; never claim player/team ownership.
        if (wool.size() == 1) {
            Map.Entry<EnumDyeColor, Integer> entry = wool.entrySet().iterator().next();
            if (entry.getValue() >= 2) hint = new TeamHint(TeamHint.Kind.COLOR_HEURISTIC,
                    entry.getKey().getMapColor().colorValue, title(entry.getKey().getName()) + "?",
                    "Defense wool color; team unverified");
        }
        BedObservation result = new BedObservation(foot, head, facing, footState, headState,
                first.build(), second.build(), open, allLoaded, hint, tick);
        return result.stillPresent(access) ? result : null;
    }

    private static boolean matches(IBlockState state, EnumFacing facing, BlockBed.EnumPartType part) {
        return state != null && state.getBlock() instanceof BlockBed && state.getValue(BlockBed.FACING) == facing
                && state.getValue(BlockBed.PART) == part;
    }
    private static int distance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }
    private static boolean open(IBlockState state) {
        return state.getBlock() == Blocks.air || !state.getBlock().canCollideCheck(state, false);
    }
    private static String title(String text) {
        String value = text.replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private static final class LayerBuilder {
        final Map<String, MaterialCount> materials = new LinkedHashMap<>();
        int occupied, empty, unknown;
        void add(IBlockState state) {
            if (state == null) { unknown++; return; }
            if (open(state)) { empty++; return; }
            occupied++;
            ResourceLocation location = Block.blockRegistry.getNameForObject(state.getBlock());
            String block = location == null ? "unknown" : location.toString();
            String id = block + ":" + state.getBlock().getMetaFromState(state);
            String label = title(location == null ? "unknown" : location.getResourcePath());
            if (state.getBlock() == Blocks.wool) label = title(state.getValue(BlockColored.COLOR).getName()) + " wool";
            MaterialCount previous = materials.get(id);
            materials.put(id, new MaterialCount(id, label, previous == null ? 1 : previous.count + 1));
        }
        Layer build() { return new Layer(materials, occupied, empty, unknown); }
    }
}
