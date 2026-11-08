package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandLayoutTest {
    @Test public void allStatesStayFiniteAndClippedAcrossRequiredViewports() {
        for (int[] size : new int[][]{{640,360},{854,480},{1280,720},{1,1},{100,20}}) {
            for (IslandStateMachine.State state : IslandStateMachine.State.values()) {
                IslandLayout.Frame frame = IslandLayout.forState(state, size[0], size[1]);
                assertTrue(Float.isFinite(frame.x) && Float.isFinite(frame.y));
                assertTrue(Float.isFinite(frame.width) && Float.isFinite(frame.height));
                assertTrue(Float.isFinite(frame.radius) && Float.isFinite(frame.opacity));
                assertTrue(frame.x >= 0 && frame.y >= 0);
                assertTrue(frame.x + frame.width <= size[0] + .0001f);
                assertTrue(frame.y + frame.height <= size[1] + .0001f);
                assertTrue(frame.width <= size[0] * .85f + .0001f);
                assertTrue(frame.radius <= frame.height / 2);
            }
        }
    }

    @Test public void unknownStateIsIdleAndCenterAligned() {
        IslandLayout.Frame a = IslandLayout.forState(null, 640, 360);
        IslandLayout.Frame b = IslandLayout.forState(IslandStateMachine.State.IDLE, 640, 360);
        assertEquals(b.width, a.width, 0);
        assertEquals((640f - a.width) / 2, a.x, .00001f);
        assertEquals(8, a.y, 0);
    }

    @Test public void morphCanNeverEscapeViewportOrProduceNaN() {
        IslandLayout.Frame frame = IslandLayout.forState(IslandStateMachine.State.TARGET, 640, 360);
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -9999, 999999}) {
            IslandLayout.Frame changed = frame.withGeometry(value, value, value, value);
            assertTrue(Float.isFinite(changed.width));
            assertTrue(Float.isFinite(changed.opacity));
            assertTrue(changed.x >= 0 && changed.x + changed.width <= 640);
            assertTrue(changed.y >= 0 && changed.y + changed.height <= 360);
        }
    }

    @Test public void unicodeAndNamesAreBoundedByCodePointNotUtf16Unit() {
        String raw = "😀😃😄😁👋 Grüß Gott — مرحبا";
        String limited = IslandLayout.limitText(raw, 6);
        assertEquals(6, limited.codePointCount(0, limited.length()));
        assertFalse(limited.endsWith("\uD83D"));
        assertEquals("A B", IslandLayout.limitText("A\nB", 10));
        assertEquals("", IslandLayout.limitText(null, 10));
        assertEquals("", IslandLayout.limitText(raw, 0));
        assertEquals("ABC...", IslandLayout.limitText("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 6));
    }
}
