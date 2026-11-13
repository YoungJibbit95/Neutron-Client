package me.youngjibbit.utils.render;

import me.youngjibbit.core.world.BedBreakEstimate;
import me.youngjibbit.core.world.BedObservation;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.Test;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/** Pure screen-space geometry and labels: these checks require no GL context. */
public class BedEspLayoutTest {
    @Test public void projectionUsesActualViewportScaleAndColumnMajorTransformsWithoutConsumingBuffers() {
        FloatBuffer model = identity(), projection = identity();
        IntBuffer viewport = IntBuffer.wrap(new int[]{20, 10, 800, 600});
        model.position(7); projection.position(9); viewport.position(3);
        BedEspLayout.Point center = BedEspLayout.project(0, 0, 0, model, projection, viewport, 620, 2);
        assertNotNull(center); assertEquals(210, center.x, 0); assertEquals(155, center.y, 0); assertEquals(.5, center.depth, 0);
        model.put(12, .25f); model.put(13, -.5f);
        BedEspLayout.Point translated = BedEspLayout.project(0, 0, 0, model, projection, viewport, 620, 2);
        assertNotNull(translated); assertEquals(260, translated.x, 0); assertEquals(230, translated.y, 0);
        assertEquals(7, model.position()); assertEquals(9, projection.position()); assertEquals(3, viewport.position());
    }

    @Test public void projectionRejectsHiddenDepthBehindCameraAndInvalidNumbers() {
        FloatBuffer model = identity(), projection = identity(); IntBuffer viewport = IntBuffer.wrap(new int[]{0, 0, 800, 600});
        assertNull(BedEspLayout.project(0, 0, 1, model, projection, viewport, 600, 1));
        assertNull(BedEspLayout.project(0, 0, -1.01, model, projection, viewport, 600, 1));
        assertEquals(0, BedEspLayout.project(0, 0, -1, model, projection, viewport, 600, 1).depth, 0);
        projection.put(15, -1);
        assertNull(BedEspLayout.project(0, 0, 0, model, projection, viewport, 600, 1));
        projection.put(15, 1); projection.put(0, Float.NaN);
        assertNull(BedEspLayout.project(0, 0, 0, model, projection, viewport, 600, 1));
        projection.put(0, 1);
        assertNull(BedEspLayout.project(Double.NaN, 0, 0, model, projection, viewport, 600, 1));
        assertNull(BedEspLayout.project(0, 0, 0, model, projection, viewport, 600, 0));
        viewport.put(2, 0);
        assertNull(BedEspLayout.project(0, 0, 0, model, projection, viewport, 600, 1));
    }

    @Test public void crowdedLabelsStayInsideScreenAndStopAfterThreeNonoverlappingPositions() {
        List<BedEspLayout.Rect> placed = new ArrayList<>(); BedEspLayout.Point anchor = new BedEspLayout.Point(400, 550, .5f);
        for (int label = 0; label < 3; label++) {
            BedEspLayout.Rect next = BedEspLayout.place(anchor, 158, 62, 800, 600, placed);
            assertNotNull(next); assertTrue(next.x >= 4 && next.x + next.width <= 796);
            assertTrue(next.y >= 4 && next.y + next.height <= 596);
            for (BedEspLayout.Rect previous : placed) assertFalse(next.intersects(previous, 4));
            placed.add(next);
        }
        assertNull(BedEspLayout.place(anchor, 158, 62, 800, 600, placed));
        placed.clear();
        assertEquals(4, BedEspLayout.place(new BedEspLayout.Point(1, 100, .5f), 158, 62, 800, 600, placed).x, 0);
        assertEquals(638, BedEspLayout.place(new BedEspLayout.Point(799, 100, .5f), 158, 62, 800, 600, placed).x, 0);
        assertNull(BedEspLayout.place(new BedEspLayout.Point(-1, 100, .5f), 158, 62, 800, 600, placed));
        assertNull(BedEspLayout.place(anchor, 800, 62, 800, 600, placed));
        assertNull(BedEspLayout.place(new BedEspLayout.Point(Float.NaN, 100, .5f), 158, 62, 800, 600, placed));
    }

    @Test public void observedMaterialsAndUnknownCoverageRemainVisibleWithoutInventingATeam() {
        BlocksMap access = new BlocksMap();
        IBlockState red = Blocks.wool.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.RED);
        access.blocks.put(access.foot.up(), red); access.blocks.put(access.head.up(), red);
        access.blocks.put(access.foot.north(), Blocks.stone.getDefaultState());
        access.blocks.put(access.foot.south(), Blocks.planks.getDefaultState()); access.unknown.add(access.foot.west());
        BedObservation bed = BedObservation.capture(access, access.foot, 7);
        assertNotNull(bed); assertEquals("Red wool x2 / Planks x1 +1 ?", BedEspLayout.layer(bed.inner));
        assertEquals("Air", BedEspLayout.layer(bed.outer)); assertTrue(BedEspLayout.exposure(bed).startsWith("Open:"));
        BedHudRenderer.Widget all = BedHudRenderer.Widget.from(bed, 12.6, true, true, true);
        assertEquals("Red?", all.team); assertEquals("13m", all.distance); assertTrue(all.open); assertEquals(62, all.height(), 0);
        assertEquals(25, BedHudRenderer.Widget.from(bed, 0, false, false, false).height(), 0);
        assertEquals(37, BedHudRenderer.Widget.from(bed, 0, true, false, true).height(), 0);
        assertEquals(50, BedHudRenderer.Widget.from(bed, 0, true, true, false).height(), 0);
        access.blocks.put(access.head.up(), Blocks.wool.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.BLUE));
        assertEquals("Unknown team", BedObservation.capture(access, access.foot, 8).team.label);
        access.blocks.remove(access.head);
        assertEquals("Partial bed", BedEspLayout.exposure(BedObservation.capture(access, access.foot, 9)));
    }

    @Test public void unknownTimesNeverRenderAsZeroOrAsAnAvailableCountdown() {
        assertEquals("--", BedEspLayout.time(Double.NaN)); assertEquals("--", BedEspLayout.time(Double.POSITIVE_INFINITY));
        assertEquals("--", BedEspLayout.time(-.1)); assertEquals("~2.4 s", BedEspLayout.time(2.4));
        assertEquals("~1:02", BedEspLayout.time(61.1)); assertEquals("~10m+", BedEspLayout.time(600));
        BlockPos bed = new BlockPos(0, 64, 0);
        for (BedBreakEstimate.State state : BedBreakEstimate.State.values()) {
            if (state == BedBreakEstimate.State.AVAILABLE || state == BedBreakEstimate.State.INVALIDATED) continue;
            BedHudRenderer.Progress frame = BedBreakerIndicator.frame(BedBreakEstimate.unavailable(state, bed, bed));
            assertNotNull(state.toString(), frame); assertEquals("--", frame.time); assertFalse(frame.estimated); assertEquals(0, frame.progress, 0);
        }
        assertNull(BedBreakerIndicator.frame(BedBreakEstimate.unavailable(BedBreakEstimate.State.INVALIDATED, bed, bed)));
        assertNull(BedBreakerIndicator.frame(BedBreakEstimate.unavailable(BedBreakEstimate.State.WAITING, null, null)));
    }

    @Test public void hudTextCannotInjectFormattingAndProgressIsAlwaysFiniteAndBounded() {
        BedHudRenderer.Progress frame = new BedHudRenderer.Progress("\u00a7cBED", "Line\nnext\t", null, Float.NaN, false);
        assertEquals("BED", frame.phase); assertEquals("Line next ", frame.detail); assertEquals("", frame.time); assertEquals(0, frame.progress, 0);
        assertEquals(0, new BedHudRenderer.Progress("", "", "", -.1f, true).progress, 0);
        assertEquals(1, new BedHudRenderer.Progress("", "", "", 1.1f, true).progress, 0);
    }

    private static FloatBuffer identity() {
        FloatBuffer result = FloatBuffer.allocate(16); for (int i = 0; i < 4; i++) result.put(i * 5, 1); return result;
    }
    private static final class BlocksMap implements BedObservation.Access {
        final Map<BlockPos, IBlockState> blocks = new HashMap<>(); final Set<BlockPos> unknown = new HashSet<>();
        final BlockPos foot = new BlockPos(0, 64, 0), head = foot.east();
        BlocksMap() {
            Bootstrap.register(); IBlockState state = Blocks.bed.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.EAST);
            blocks.put(foot, state.withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT));
            blocks.put(head, state.withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD));
        }
        public boolean current() { return true; }
        public IBlockState stateIfLoaded(BlockPos pos) { return unknown.contains(pos) ? null : blocks.getOrDefault(pos, Blocks.air.getDefaultState()); }
    }
}
