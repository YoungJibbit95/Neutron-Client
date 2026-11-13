package me.youngjibbit.core.world;

import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class BedObservationTest {
    @BeforeClass public static void blocks() { Bootstrap.register(); }
    @Test public void combinesHalvesAndCountsOnlyTheFirstTwoDefenseShells() {
        Cells cells = new Cells(); BlockPos foot = new BlockPos(0,64,0), head = foot.east(); cells.bed(foot);
        cells.states.put(foot.north(), Blocks.wool.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.RED));
        cells.states.put(foot.up(), Blocks.end_stone.getDefaultState());
        cells.states.put(foot.north(2), Blocks.obsidian.getDefaultState());
        cells.states.put(foot.down(), Blocks.obsidian.getDefaultState()); cells.states.put(head.down(), Blocks.obsidian.getDefaultState());
        BedObservation value = BedObservation.capture(cells, head, 42);
        assertNotNull(value); assertEquals(foot, value.foot); assertEquals(head, value.head); assertTrue(value.completeBed());
        assertEquals(2, value.bounds.maxX - value.bounds.minX, 0); assertEquals(.5625, value.bounds.maxY - value.bounds.minY, 0);
        assertEquals(2, value.inner.occupied); assertEquals(6, value.inner.empty); assertEquals(0, value.inner.unknown);
        assertEquals(1, value.outer.occupied); assertEquals(17, value.outer.empty); assertEquals(1, value.outer.materials.get(0).count);
        assertFalse(value.openSides.contains(EnumFacing.DOWN)); assertTrue(value.incompleteDefense()); assertEquals(42, value.observedTick);
    }
    @Test public void pairedHalfIsNotAnOpenAttackFaceAndKnownBottomIsChecked() {
        Cells cells = new Cells(); BlockPos foot = new BlockPos(-1,64,-1), head = foot.east(); cells.bed(foot);
        for (BlockPos pos : new BlockPos[]{foot,head}) for (EnumFacing side : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(side); if (!neighbor.equals(foot) && !neighbor.equals(head)) cells.states.put(neighbor, Blocks.stone.getDefaultState());
        }
        BedObservation closed = BedObservation.capture(cells, foot, 1);
        assertNotNull(closed); assertTrue(closed.openSides.isEmpty()); assertTrue(closed.inner.complete());
        cells.states.remove(head.down());
        BedObservation open = BedObservation.capture(cells, foot, 2);
        assertEquals(1, open.openSides.size()); assertTrue(open.openSides.contains(EnumFacing.DOWN));
    }
    @Test public void unloadedCoverageRemainsUnknownAndDoesNotBecomeAnOpening() {
        Cells cells = new Cells(); BlockPos foot = new BlockPos(0,64,0); cells.bed(foot);
        cells.unloaded.add(foot.west()); cells.unloaded.add(foot.east());
        BedObservation value = BedObservation.capture(cells, foot, 1);
        assertNotNull(value); assertFalse(value.fullyLoaded); assertFalse(value.headPresent); assertFalse(value.completeBed());
        assertEquals(1, value.inner.unknown); assertFalse(value.openSides.contains(EnumFacing.WEST));
    }
    @Test public void repeatedWoolColorIsOnlyAnExplicitUnverifiedHint() {
        Cells cells = new Cells(); BlockPos foot = new BlockPos(0,64,0); cells.bed(foot);
        IBlockState red = Blocks.wool.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.RED);
        cells.states.put(foot.north(), red); cells.states.put(foot.east().north(), red);
        BedObservation value = BedObservation.capture(cells, foot, 1);
        assertEquals(BedObservation.TeamHint.Kind.COLOR_HEURISTIC, value.team.kind);
        assertTrue(value.team.label.contains("?")); assertTrue(value.team.source.contains("unverified"));
        cells.states.put(foot.south(), Blocks.wool.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.BLUE));
        assertEquals(BedObservation.TeamHint.Kind.UNKNOWN, BedObservation.capture(cells, foot, 2).team.kind);
    }
    @Test public void immutableSnapshotsRejectLateWorldInvalidationAndBedRemoval() {
        Cells cells = new Cells(); BlockPos foot = new BlockPos(0,64,0); cells.bed(foot);
        BedObservation value = BedObservation.capture(cells, foot, 1);
        try { value.openSides.clear(); fail("mutable faces"); } catch (UnsupportedOperationException expected) { }
        try { value.inner.materials.clear(); fail("mutable materials"); } catch (UnsupportedOperationException expected) { }
        cells.states.remove(foot); assertFalse(value.stillPresent(cells));
        cells.valid = false; assertNull(BedObservation.capture(cells, foot.east(), 2));
    }
    @Test public void invalidationDuringPairReadStopsBeforeTheOtherHalf() {
        Cells cells = new Cells(); BlockPos foot = new BlockPos(0,64,0); cells.bed(foot); cells.stopAfter = 2;
        assertNull(BedObservation.capture(cells, foot, 1)); assertEquals(2,cells.reads);
    }
    static final class Cells implements BedObservation.Access {
        final Map<BlockPos,IBlockState> states = new HashMap<>(); final Set<BlockPos> unloaded = new HashSet<>(); boolean valid = true; int reads, stopAfter = Integer.MAX_VALUE;
        public boolean current() { return valid; }
        public IBlockState stateIfLoaded(BlockPos pos) { if (++reads == stopAfter) valid = false; return unloaded.contains(pos) ? null : states.getOrDefault(pos, Blocks.air.getDefaultState()); }
        void bed(BlockPos foot) {
            states.put(foot, Blocks.bed.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.EAST).withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT));
            states.put(foot.east(), Blocks.bed.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.EAST).withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD));
        }
    }
}
