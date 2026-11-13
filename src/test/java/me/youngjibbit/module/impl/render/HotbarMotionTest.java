package me.youngjibbit.module.impl.render;

import org.junit.Test;

import static org.junit.Assert.*;

public class HotbarMotionTest {
    @Test public void cursorContinuesAcrossRapidChangesAndStaysInsideAllNineSlots() {
        HotbarMotion motion = new HotbarMotion();
        motion.step(0, 0, true, true, false, "Balanced");
        motion.step(8, 1 / 120f, true, true, false, "Balanced");
        assertTrue(motion.position() > 0 && motion.position() < 8);
        float before = motion.position();
        motion.step(2, 0, true, true, false, "Balanced");
        assertEquals(before, motion.position(), 0);
        for (int i = 0; i < 800; i++) {
            motion.step(i % 2 == 0 ? 0 : 8, 1 / 30f, true, true, false, "Expressive");
            assertTrue(Float.isFinite(motion.position()));
            assertTrue(motion.position() >= 0 && motion.position() <= 8);
            for (int slot = 0; slot < 9; slot++) assertTrue(motion.weight(slot) >= 0 && motion.weight(slot) <= 1);
        }
    }

    @Test public void selectionTimingDependsOnElapsedTimeRatherThanFrameCount() {
        HotbarMotion slow = new HotbarMotion(), fast = new HotbarMotion();
        slow.step(0, 0, true, true, false, "Balanced");
        fast.step(0, 0, true, true, false, "Balanced");
        for (int i = 0; i < 6; i++) slow.step(6, 1 / 30f, true, true, false, "Balanced");
        for (int i = 0; i < 24; i++) fast.step(6, 1 / 120f, true, true, false, "Balanced");
        assertEquals(slow.position(), fast.position(), .0001);
        assertEquals(slow.weight(6), fast.weight(6), .0001);
    }

    @Test public void reducedMotionAndDisabledAnimationSnapWithoutAResidualPulse() {
        HotbarMotion motion = new HotbarMotion();
        motion.step(0, 0, true, true, false, "Balanced");
        motion.step(8, .016f, true, true, true, "Balanced");
        assertEquals(8, motion.position(), 0);
        assertEquals(1, motion.weight(8), 0);
        motion.itemPulse(8, 1, 0, true, false, "Balanced");
        assertEquals(1, motion.itemPulse(8, 2, 0, true, false, "Balanced"), 0);
        assertEquals(0, motion.itemPulse(8, 3, .016f, true, true, "Balanced"), 0);
        motion.step(3, .016f, false, false, false, "Balanced");
        assertEquals(3, motion.position(), 0);
        motion.reset();
        motion.step(7, .016f, true, true, false, "Balanced");
        assertEquals(7, motion.position(), 0);
    }

    @Test public void hotbarAndSelectionCellsFitNarrowAndRegularViewports() {
        for (int[] size : new int[][]{{1, 1}, {100, 60}, {320, 240}, {550, 320}, {960, 540}}) {
            CustomHotbar.Layout layout = CustomHotbar.layout(size[0], size[1]);
            assertTrue(layout.x >= 0 && layout.y >= 0);
            assertTrue(layout.x + layout.width <= size[0] + .001);
            assertTrue(layout.y + layout.height <= size[1] + .001);
            float first = layout.x + 2 * layout.scale;
            float last = layout.x + 182 * layout.scale;
            assertTrue(first > layout.x && last < layout.x + layout.width);
        }
    }
}
