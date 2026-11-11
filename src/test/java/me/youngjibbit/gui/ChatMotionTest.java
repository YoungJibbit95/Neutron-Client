package me.youngjibbit.gui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatMotionTest {
    @Test public void openingReversesContinuously() {
        ChatMotion motion = new ChatMotion();
        motion.open(true);
        motion.advance(0, true, "Balanced");
        assertEquals(0, motion.focusAlpha(), 0);
        for (long t = 16_000_000; t <= 128_000_000; t += 16_000_000) {
            motion.advance(t, true, "Balanced");
            assertTrue(motion.inputSlide() >= -1.3f && motion.inputSlide() <= 16);
        }
        float before = motion.inputSlide();
        motion.open(false);
        motion.advance(128_000_000, true, "Balanced");
        assertEquals(before, motion.inputSlide(), .0001f);
        for (long t = 144_000_000; t <= 1_000_000_000; t += 16_000_000)
            motion.advance(t, true, "Balanced");
        assertEquals(0, motion.focusAlpha(), 0);
        motion.open(true);
        motion.advance(1_016_000_000, true, "Balanced");
        assertTrue(motion.focusAlpha() > 0);
    }

    @Test public void reducedMotionSettlesEveryPresentationChannel() {
        ChatMotion motion = new ChatMotion();
        motion.open(true);
        motion.edited(10);
        motion.advance(10, false, "Expressive");
        assertEquals(1, motion.focusAlpha(), 0);
        assertEquals(0, motion.inputSlide(), 0);
        assertEquals(0, motion.textSlideX(), 0);
        assertEquals(0, motion.textSlideY(), 0);
        ChatMotion.Line line = new ChatMotion.Line(10);
        assertEquals(1, line.appearance(10, false), 0);
        assertEquals(-53, line.y(3, .016f, false, "Expressive"), 0);
        motion.open(false);
        motion.advance(20, false, "Expressive");
        assertEquals(0, motion.focusAlpha(), 0);
    }

    @Test public void arrivalsBounceAndLifetimeNeverReappears() {
        ChatMotion.Line line = new ChatMotion.Line(0);
        float maximum = 0;
        for (long t = 0; t <= 1_000_000_000; t += 1_000_000) {
            float value = line.appearance(t, true);
            assertTrue(Float.isFinite(value));
            assertTrue(value >= -.0001f && value <= 1.08f);
            maximum = Math.max(maximum, value);
        }
        assertTrue(maximum > 1.01f);
        assertEquals(1, line.appearance(1_000_000_000, true), 0);
        float previous = 1;
        for (int tick = 0; tick <= 2200; tick++) {
            float alpha = ChatMotion.lifetimeAlpha(tick / 10f);
            assertTrue(alpha >= 0 && alpha <= previous + .00001f);
            previous = alpha;
        }
        assertEquals(1, ChatMotion.lifetimeAlpha(180), 0);
        assertEquals(0, ChatMotion.lifetimeAlpha(200), 0);
        assertTrue(ChatMotion.exitSlide(185) < 0);
        assertEquals(1, ChatMotion.exitSlide(200), .00001f);
    }

    @Test public void refreshedHistoryScrollAndResizeStayInsideViewport() {
        ChatMotion.Line line = new ChatMotion.Line(Long.MIN_VALUE);
        assertEquals(1, line.appearance(0, true), 0);
        line.y(0, .016f, true, "Balanced");
        assertEquals(-151, line.y(10, .016f, false, "Balanced"), 0);
        for (int viewport : new int[]{320, 640, 1280}) {
            for (float scale : new float[]{.25f, .5f, 1}) {
                int text = ChatMotion.textWidth(320, scale, viewport);
                assertTrue((text + ChatMotion.TEXT_INSET * 2) * scale <= viewport - 8 + .001f);
                assertTrue(text * scale <= 320);
            }
        }
        assertTrue(ChatMotion.textWidth(320, 0, 320) > 0);
    }
}
