package me.youngjibbit.core.world;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only remaining work at 20 ticks/second, recalculated from blocks and native mining progress. */
public final class BedBreakEstimate {
    public enum State { AVAILABLE, WAITING, ALIGNING, PAUSED, NO_BED_PATH, OUT_OF_RANGE, UNLOADED, UNBREAKABLE, INVALIDATED }
    public enum Basis { NONE, DIRECT, CONDITIONAL_COVER }
    public interface Access extends BedObservation.Access {
        MovingObjectPosition ray(Vec3 from, Vec3 to);
        boolean mayMine(BlockPos pos, IBlockState state);
        double relativeHardness(BlockPos pos, IBlockState state);
        boolean creative();
        double progress(BlockPos pos);
        boolean mining(BlockPos pos);
        int hitDelay();
    }
    public final State state;
    public final Basis basis;
    public final BlockPos bed, activeBlock;
    public final boolean activeIsBed;
    public final double accessSeconds, totalSeconds;
    public final float blockProgress, progress, accessProgress;
    public final int remainingCoverBlocks;
    public final List<BlockPos> path;
    private final BlockPos bedKey;
    private final Evidence evidence;

    private BedBreakEstimate(State state, Basis basis, BlockPos bed, BlockPos activeBlock, BlockPos bedKey,
                             boolean activeIsBed, double accessSeconds, double totalSeconds, float blockProgress,
                             float progress, float accessProgress, List<BlockPos> path, Evidence evidence) {
        this.state = state; this.basis = basis; this.bed = copy(bed); this.activeBlock = copy(activeBlock); this.bedKey = copy(bedKey);
        this.activeIsBed = activeIsBed; this.accessSeconds = accessSeconds; this.totalSeconds = totalSeconds;
        this.blockProgress = clamp(blockProgress); this.progress = clamp(progress); this.accessProgress = clamp(accessProgress);
        List<BlockPos> positions = new ArrayList<>(); for (BlockPos pos : path) positions.add(copy(pos));
        this.path = Collections.unmodifiableList(positions);
        this.evidence = evidence;
        remainingCoverBlocks = Math.max(0, path.size() - 1);
    }
    public boolean hasEstimate() { return state == State.AVAILABLE && Double.isFinite(accessSeconds) && Double.isFinite(totalSeconds); }
    public static BedBreakEstimate unavailable(State state, BlockPos bed, BlockPos active) {
        if (state == State.AVAILABLE) throw new IllegalArgumentException("available estimate needs measured work");
        return new BedBreakEstimate(state, Basis.NONE, bed, active, null, false, Double.NaN, Double.NaN, 0, 0, 0, Collections.emptyList(), null);
    }

    /** Bounded read-only cache check: cells, eye position and actual native remaining work must still agree. */
    public boolean isCurrent(Access access, Vec3 eyes, double reach, boolean direct) {
        return hasEstimate() && evidence != null && evidence.matches(access, eyes, reach, direct, this);
    }

    /**
     * direct=true mirrors the current non-Legit BedBreaker: its action addresses the bed itself.
     * Legit estimates are conditional routes whose actual first block must be the owned mining block.
     * The estimator cannot select an action, send a packet, advance damage, or change rotation.
     */
    public static BedBreakEstimate calculate(Access access, BlockPos bed, BlockPos active, Vec3 eyes, double reach, boolean direct) {
        if (access == null || bed == null || active == null || !finite(eyes) || !Double.isFinite(reach) || reach <= 0 || reach > 5
                || !access.current()) return unavailable(State.INVALIDATED, bed, active);
        IBlockState bedState = access.stateIfLoaded(bed);
        if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
        IBlockState activeState = access.stateIfLoaded(active);
        if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
        if (bedState == null || activeState == null) return unavailable(State.UNLOADED, bed, active);
        if (!(bedState.getBlock() instanceof BlockBed)) return unavailable(State.NO_BED_PATH, bed, active);
        boolean activeBed = sameBed(bed, bedState, active, activeState);
        if (direct && !activeBed) return unavailable(State.NO_BED_PATH, bed, active);
        double measured = access.progress(active);
        if (!access.current() || !Double.isFinite(measured) || measured < 0 || measured > 1)
            return unavailable(State.INVALIDATED, bed, active);
        if (activeBed) {
            if (!inReach(eyes, closest(active, eyes), reach)) return unavailable(State.OUT_OF_RANGE, bed, active);
            Vec3 provenAim = null;
            if (!direct) {
                boolean exposed = false;
                State failure = State.NO_BED_PATH;
                for (Vec3 aim : points(active, eyes)) {
                    if (!inReach(eyes, aim, reach)) continue;
                    State loaded = loadedRay(access, eyes, aim);
                    if (loaded != null) { if (loaded == State.UNLOADED) failure = loaded; continue; }
                    MovingObjectPosition hit = access.ray(eyes, aim);
                    if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
                    if (DemiseBedBreakerGeometry.matches(hit, active, eyes, reach)) { exposed = true; provenAim = aim; break; }
                }
                if (!exposed) return unavailable(access.current() ? failure : State.INVALIDATED, bed, active);
            }
            double ticks = ticks(access, active, activeState, true);
            if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
            if (!Double.isFinite(ticks)) return unavailable(State.UNBREAKABLE, bed, active);
            int delay = access.hitDelay();
            if (!access.current() || delay < 0) return unavailable(State.INVALIDATED, bed, active);
            if (!bedState.equals(access.stateIfLoaded(bed)) || !access.current())
                return unavailable(State.INVALIDATED, bed, active);
            if (!activeState.equals(access.stateIfLoaded(active)) || !access.current()) return unavailable(State.INVALIDATED, bed, active);
            return available(access, Basis.DIRECT, bed, active, bedState, Collections.singletonList(active),
                    Collections.singletonList(activeState), eyes, provenAim, reach, direct);
        }
        // A Legit mouse-over block is related to the bed only when it is the first world hit on a complete route.
        List<BlockPos> halves = new ArrayList<>(); halves.add(bed);
        BlockPos partner = partner(bed, bedState);
        IBlockState partnerState = access.stateIfLoaded(partner);
        if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
        if (sameBed(bed, bedState, partner, partnerState)) halves.add(partner);
        int[] rays = {127};
        Route best = null;
        State failure = State.NO_BED_PATH;
        boolean reachable = false;
        for (BlockPos half : halves) for (Vec3 aim : points(half, eyes)) {
            if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
            if (!inReach(eyes, aim, reach)) continue;
            reachable = true;
            if (rays[0] <= 0) break;
            Route route = trace(access, half, active, eyes, aim, reach, rays);
            if (route.failure != null) {
                if (route.failure == State.UNLOADED || failure != State.UNLOADED && route.failure == State.UNBREAKABLE) failure = route.failure;
            } else if (best == null || route.totalTicks < best.totalTicks) best = route;
        }
        if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
        if (best == null) return unavailable(reachable ? failure : State.OUT_OF_RANGE, bed, active);
        State freshPath = loadedRay(access, eyes, best.aim);
        if (freshPath != null) return unavailable(freshPath, bed, active);
        MovingObjectPosition firstHit = access.ray(eyes, best.aim);
        if (!access.current() || !DemiseBedBreakerGeometry.matches(firstHit, active, eyes, reach))
            return unavailable(State.INVALIDATED, bed, active);
        if (!bedState.equals(access.stateIfLoaded(bed)) || !access.current())
            return unavailable(State.INVALIDATED, bed, active);
        if (!activeState.equals(access.stateIfLoaded(active)) || !access.current()) return unavailable(State.INVALIDATED, bed, active);
        return available(access, Basis.CONDITIONAL_COVER, bed, active, bedState, best.path, best.states, eyes, best.aim, reach, false);
    }

    private static BedBreakEstimate available(Access access, Basis basis, BlockPos bed, BlockPos active, IBlockState bedState,
                                              List<BlockPos> path, List<IBlockState> states, Vec3 eyes, Vec3 aim, double reach, boolean direct) {
        if (path.isEmpty() || !sameBed(bed, bedState, path.get(path.size() - 1), states.get(states.size() - 1)))
            return unavailable(State.NO_BED_PATH, bed, active);
        Map<BlockPos, IBlockState> cells = new LinkedHashMap<>(); cells.put(copy(bed), bedState);
        for (int i = 0; i < path.size(); i++) cells.put(copy(path.get(i)), states.get(i));
        if (!direct) {
            State loaded = loadedRay(access, eyes, aim, cells);
            if (loaded != null) return unavailable(loaded, bed, active);
        }
        Evidence evidence = new Evidence(eyes, reach, direct, path, states, cells);
        if (!evidence.statesCurrent(access)) return unavailable(State.INVALIDATED, bed, active);
        Work work = work(access, path, states);
        if (!access.current()) return unavailable(State.INVALIDATED, bed, active);
        if (work == null) return unavailable(State.UNBREAKABLE, bed, active);
        if (!evidence.statesCurrent(access)) return unavailable(State.INVALIDATED, bed, active);
        BlockPos key = bedState.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD ? partner(bed, bedState) : bed;
        int covers = Math.max(0, path.size() - 1);
        return new BedBreakEstimate(State.AVAILABLE, basis, bed, active, key, covers == 0,
                work.accessTicks / 20D, work.totalTicks / 20D, work.progress, work.progress / path.size(),
                covers == 0 ? 1 : work.progress / covers, path, evidence);
    }
    private static Route trace(Access access, BlockPos bed, BlockPos active, Vec3 eyes, Vec3 aim, double reach, int[] rays) {
        State loaded = loadedRay(access, eyes, aim);
        if (loaded != null) return Route.failure(loaded);
        List<BlockPos> path = new ArrayList<>(); List<IBlockState> states = new ArrayList<>();
        Vec3 cursor = eyes;
        double total = access.hitDelay(), previousDelay = 0;
        if (!access.current() || total < 0) return Route.failure(State.INVALIDATED);
        for (int step = 0; step < 16 && rays[0]-- > 0; step++) {
            BlockPos pos;
            Vec3 hitPoint;
            if (step > 0 && new BlockPos(cursor).equals(bed)) {
                // The virtual skip can start inside the final bed box, where vanilla has no entry
                // plane to hit. The aim lies inside that box: after entering its cell the final
                // segment necessarily reaches the bed, whose current state is checked below.
                pos = bed; hitPoint = aim;
            } else {
                MovingObjectPosition hit = access.ray(cursor, aim);
                if (!access.current()) return Route.failure(State.INVALIDATED);
                if (hit == null || !DemiseBedBreakerGeometry.matches(hit, hit.getBlockPos(), eyes, reach)) return Route.failure(State.NO_BED_PATH);
                pos = hit.getBlockPos(); hitPoint = hit.hitVec;
            }
            if (step == 0 && !active.equals(pos) || path.contains(pos)) return Route.failure(State.NO_BED_PATH);
            IBlockState state = access.stateIfLoaded(pos);
            if (!access.current()) return Route.failure(State.INVALIDATED);
            if (state == null) return Route.failure(State.UNLOADED);
            if (state.getBlock() instanceof BlockBed && !bed.equals(pos)) return Route.failure(State.NO_BED_PATH);
            double work = ticks(access, pos, state, step == 0);
            if (!access.current()) return Route.failure(State.INVALIDATED);
            if (!Double.isFinite(work)) return Route.failure(State.UNBREAKABLE);
            total += previousDelay;
            double accessTicks = total;
            total += work;
            if (!Double.isFinite(total)) return Route.failure(State.UNBREAKABLE);
            path.add(pos); states.add(state);
            if (bed.equals(pos)) {
                for (int i = 0; i < path.size(); i++) {
                    if (!states.get(i).equals(access.stateIfLoaded(path.get(i))) || !access.current()) return Route.failure(State.INVALIDATED);
                }
                return new Route(path, states, accessTicks, total, null, aim);
            }
            double hardness = access.relativeHardness(pos, state);
            if (!access.current()) return Route.failure(State.INVALIDATED);
            previousDelay = !access.creative() && hardness >= 1 ? 0 : 5;
            if (!access.current()) return Route.failure(State.INVALIDATED);
            cursor = afterCell(eyes, aim, pos, hitPoint);
            if (cursor == null) return Route.failure(State.NO_BED_PATH);
        }
        return Route.failure(State.NO_BED_PATH);
    }
    private static double ticks(Access access, BlockPos pos, IBlockState state, boolean active) {
        if (!access.current() || !access.mayMine(pos, state) || !access.current()) return Double.POSITIVE_INFINITY;
        if (access.creative()) return access.current() ? 1 : Double.POSITIVE_INFINITY;
        if (!access.current()) return Double.POSITIVE_INFINITY;
        double hardness = access.relativeHardness(pos, state);
        if (!access.current()) return Double.POSITIVE_INFINITY;
        if (hardness >= 1) return 1;
        if (!Double.isFinite(hardness) || hardness <= 0) return Double.POSITIVE_INFINITY;
        double progress = active ? access.progress(pos) : 0;
        if (!access.current()) return Double.POSITIVE_INFINITY;
        boolean started = active && access.mining(pos);
        if (!access.current() || !Double.isFinite(progress) || progress < 0 || progress > 1) return Double.POSITIVE_INFINITY;
        return (started ? 0 : 1) + Math.max(1, Math.ceil((1 - progress) / hardness));
    }

    /** Reprices only the already observed path; this cannot choose or start another action. */
    private static Work work(Access access, List<BlockPos> path, List<IBlockState> states) {
        if (!access.current() || path.isEmpty() || path.size() != states.size()) return null;
        int delay = access.hitDelay();
        if (!access.current() || delay < 0) return null;
        double progress = access.progress(path.get(0));
        if (!access.current() || !Double.isFinite(progress) || progress < 0 || progress > 1) return null;
        double total = delay, accessTicks = 0;
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                double hardness = access.relativeHardness(path.get(i - 1), states.get(i - 1));
                if (!access.current()) return null;
                boolean creative = access.creative();
                if (!access.current()) return null;
                total += !creative && hardness >= 1 ? 0 : 5;
            }
            if (i == path.size() - 1 && i > 0) accessTicks = total;
            total += ticks(access, path.get(i), states.get(i), i == 0);
            if (!access.current() || !Double.isFinite(total)) return null;
        }
        return new Work(accessTicks, total, (float) progress);
    }

    private static final class Work {
        final double accessTicks, totalTicks;
        final float progress;
        Work(double accessTicks, double totalTicks, float progress) {
            this.accessTicks = accessTicks; this.totalTicks = totalTicks; this.progress = progress;
        }
    }
    private static final class Evidence {
        final Vec3 eyes;
        final double reach;
        final boolean direct;
        final List<BlockPos> path;
        final List<IBlockState> states;
        final Map<BlockPos, IBlockState> cells;
        Evidence(Vec3 eyes, double reach, boolean direct, List<BlockPos> path, List<IBlockState> states,
                 Map<BlockPos, IBlockState> cells) {
            this.eyes = new Vec3(eyes.xCoord, eyes.yCoord, eyes.zCoord); this.reach = reach; this.direct = direct;
            this.path = new ArrayList<>(); for (BlockPos pos : path) this.path.add(copy(pos));
            this.states = new ArrayList<>(states); this.cells = new LinkedHashMap<>(cells);
        }
        boolean statesCurrent(Access access) {
            if (access == null || !access.current()) return false;
            for (Map.Entry<BlockPos, IBlockState> cell : cells.entrySet()) {
                if (!cell.getValue().equals(access.stateIfLoaded(cell.getKey())) || !access.current()) return false;
            }
            return access.current();
        }
        boolean matches(Access access, Vec3 eyes, double reach, boolean direct, BedBreakEstimate estimate) {
            if (!finite(eyes) || this.direct != direct || Double.compare(this.reach, reach) != 0
                    || Double.compare(this.eyes.xCoord, eyes.xCoord) != 0 || Double.compare(this.eyes.yCoord, eyes.yCoord) != 0
                    || Double.compare(this.eyes.zCoord, eyes.zCoord) != 0 || !statesCurrent(access)) return false;
            Work measured = work(access, path, states);
            return measured != null && access.current() && measured.progress == estimate.blockProgress
                    && Double.compare(measured.accessTicks / 20D, estimate.accessSeconds) == 0
                    && Double.compare(measured.totalTicks / 20D, estimate.totalSeconds) == 0 && statesCurrent(access);
        }
    }

    /** Accumulates only confirmed removed prefix blocks, never elapsed wall time. A changed route resets the baseline. */
    public static final class ProgressTracker {
        private BlockPos bed;
        private List<BlockPos> original = Collections.emptyList();
        public void clear() { bed = null; original = Collections.emptyList(); }
        public BedBreakEstimate apply(BedBreakEstimate estimate, Access access) {
            if (!estimate.hasEstimate() || !access.current()) { clear(); return estimate; }
            int prefix = -1;
            if (estimate.bedKey.equals(bed)) {
                for (int index = 0; index < original.size(); index++) {
                    if (original.subList(index, original.size()).equals(estimate.path)) { prefix = index; break; }
                }
                for (int index = 0; index < prefix; index++) {
                    IBlockState state = access.stateIfLoaded(original.get(index));
                    if (state == null || state.getBlock() != Blocks.air || !access.current()) { prefix = -1; break; }
                }
            }
            if (prefix < 0) { bed = estimate.bedKey; original = estimate.path; prefix = 0; }
            if (!access.current()) { clear(); return unavailable(State.INVALIDATED, estimate.bed, estimate.activeBlock); }
            int covers = Math.max(0, original.size() - 1);
            float totalProgress = (prefix + estimate.blockProgress) / original.size();
            float accessProgress = covers == 0 || estimate.activeIsBed ? 1 : (prefix + estimate.blockProgress) / covers;
            return new BedBreakEstimate(estimate.state, estimate.basis, estimate.bed, estimate.activeBlock, estimate.bedKey,
                    estimate.activeIsBed, estimate.accessSeconds, estimate.totalSeconds, estimate.blockProgress,
                    totalProgress, accessProgress, estimate.path, estimate.evidence);
        }
    }

    private static boolean sameBed(BlockPos bed, IBlockState bedState, BlockPos other, IBlockState state) {
        return state != null && state.getBlock() instanceof BlockBed && (bed.equals(other)
                || partner(bed, bedState).equals(other) && state.getValue(BlockBed.FACING) == bedState.getValue(BlockBed.FACING)
                && state.getValue(BlockBed.PART) != bedState.getValue(BlockBed.PART));
    }
    private static BlockPos partner(BlockPos pos, IBlockState state) {
        return pos.offset(state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD
                ? state.getValue(BlockBed.FACING).getOpposite() : state.getValue(BlockBed.FACING));
    }
    private static Vec3 closest(BlockPos pos, Vec3 eyes) {
        return new Vec3(inset(eyes.xCoord, pos.getX(), 1), inset(eyes.yCoord, pos.getY(), .5625), inset(eyes.zCoord, pos.getZ(), 1));
    }
    private static List<Vec3> points(BlockPos pos, Vec3 eyes) {
        List<Vec3> result = new ArrayList<>(); result.add(closest(pos, eyes));
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        result.add(new Vec3(x + .5, y + .3, z + .5));
        for (double dx : new double[]{.04, .96}) for (double dy : new double[]{.04, .5225}) for (double dz : new double[]{.04, .96})
            result.add(new Vec3(x + dx, y + dy, z + dz));
        return result;
    }
    private static double inset(double value, int min, double height) { return Math.max(min + .04, Math.min(min + height - .04, value)); }
    private static boolean inReach(Vec3 from, Vec3 to, double reach) { return from.squareDistanceTo(to) <= reach * reach + 1e-7; }
    private static BlockPos copy(BlockPos pos) { return pos == null ? null : new BlockPos(pos); }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    private static boolean finite(Vec3 value) { return DemiseBedBreakerGeometry.finite(value) && Math.abs(value.xCoord) <= 30_000_000 && Math.abs(value.zCoord) <= 30_000_000; }
    private static double boundary(double from, int cell, double direction) {
        return direction > 0 ? (cell + 1D - from) / direction : direction < 0 ? (cell - from) / direction : Double.POSITIVE_INFINITY;
    }
    private static Vec3 afterCell(Vec3 from, Vec3 to, BlockPos pos, Vec3 hit) {
        double dx = to.xCoord - from.xCoord, dy = to.yCoord - from.yCoord, dz = to.zCoord - from.zCoord;
        double length = dx * dx + dy * dy + dz * dz;
        if (length < 1e-10) return null;
        double at = ((hit.xCoord - from.xCoord) * dx + (hit.yCoord - from.yCoord) * dy + (hit.zCoord - from.zCoord) * dz) / length;
        double exit = Math.min(boundary(from.xCoord, pos.getX(), dx), Math.min(boundary(from.yCoord, pos.getY(), dy), boundary(from.zCoord, pos.getZ(), dz)));
        double next = exit + 1e-6 / Math.sqrt(length);
        return !Double.isFinite(next) || exit < at - 1e-7 || next >= 1 || next <= at ? null
                : new Vec3(from.xCoord + dx * next, from.yCoord + dy * next, from.zCoord + dz * next);
    }
    private static State loadedRay(Access access, Vec3 from, Vec3 to) {
        return loadedRay(access, from, to, null);
    }
    private static State loadedRay(Access access, Vec3 from, Vec3 to, Map<BlockPos, IBlockState> observed) {
        if (!finite(from) || !finite(to) || !access.current()) return State.INVALIDATED;
        int x = MathHelper.floor_double(from.xCoord), y = MathHelper.floor_double(from.yCoord), z = MathHelper.floor_double(from.zCoord);
        int endX = MathHelper.floor_double(to.xCoord), endY = MathHelper.floor_double(to.yCoord), endZ = MathHelper.floor_double(to.zCoord);
        double dx = to.xCoord - from.xCoord, dy = to.yCoord - from.yCoord, dz = to.zCoord - from.zCoord;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tx = boundary(from.xCoord, x, dx), ty = boundary(from.yCoord, y, dy), tz = boundary(from.zCoord, z, dz);
        for (int count = 0; count < 24; count++) {
            BlockPos pos = new BlockPos(x, y, z);
            IBlockState state = access.stateIfLoaded(pos);
            if (!access.current()) return State.INVALIDATED;
            if (state == null) return State.UNLOADED;
            if (!remember(observed, pos, state)) return State.INVALIDATED;
            if (x == endX && y == endY && z == endZ) return null;
            double next = Math.min(tx, Math.min(ty, tz));
            if (!Double.isFinite(next) || next > 1 + 1e-7) return State.NO_BED_PATH;
            int cross = (tx <= next + 1e-7 ? 1 : 0) | (ty <= next + 1e-7 ? 2 : 0) | (tz <= next + 1e-7 ? 4 : 0);
            for (int mask = 1; mask <= 7; mask++) if ((mask & cross) == mask) {
                pos = new BlockPos(x + ((mask & 1) != 0 ? sx : 0), y + ((mask & 2) != 0 ? sy : 0), z + ((mask & 4) != 0 ? sz : 0));
                state = access.stateIfLoaded(pos);
                if (!access.current()) return State.INVALIDATED;
                if (state == null) return State.UNLOADED;
                if (!remember(observed, pos, state)) return State.INVALIDATED;
            }
            if ((cross & 1) != 0) { x += sx; tx += 1 / Math.abs(dx); }
            if ((cross & 2) != 0) { y += sy; ty += 1 / Math.abs(dy); }
            if ((cross & 4) != 0) { z += sz; tz += 1 / Math.abs(dz); }
        }
        return State.NO_BED_PATH;
    }
    private static boolean remember(Map<BlockPos, IBlockState> cells, BlockPos pos, IBlockState state) {
        if (cells == null) return true;
        IBlockState previous = cells.get(pos);
        if (previous != null && !previous.equals(state)) return false;
        cells.put(copy(pos), state); return true;
    }
    private static final class Route {
        final List<BlockPos> path;
        final List<IBlockState> states;
        final double accessTicks, totalTicks;
        final State failure;
        final Vec3 aim;
        Route(List<BlockPos> path, List<IBlockState> states, double accessTicks, double totalTicks, State failure, Vec3 aim) {
            this.path = path; this.states = states; this.accessTicks = accessTicks; this.totalTicks = totalTicks; this.failure = failure; this.aim = aim;
        }
        static Route failure(State failure) { return new Route(Collections.emptyList(), Collections.emptyList(), Double.NaN, Double.NaN, failure, null); }
    }
}
