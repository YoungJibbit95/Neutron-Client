package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.TimerUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClickAnimationTest {
    private static final long NOW = 10000;
    private static ClickAnimation animation(Direction direction) {
        ClickAnimation result = new ClickAnimation(220, 1, direction);
        result.timerUtil = new TimerUtil() {
            { lastMS = NOW; }
            @Override public long getTime() { return NOW - lastMS; }
            @Override public boolean hasTimeElapsed(long time) { return getTime() > time; }
        };
        return result;
    }
    @Test public void reversingEitherDirectionPreservesTheVisiblePosition() {
        for(Direction direction:Direction.values())for(int elapsed:new int[]{30,80,140,190}) {
            ClickAnimation animation=animation(direction);
            animation.timerUtil.setTime(NOW-elapsed);
            double before=animation.getOutput();
            animation.setDirection(direction.opposite());
            assertEquals("Direction reversal jumped at "+elapsed,before,animation.getOutput(),.025);
        }
    }
    @Test public void completedTransitionsReverseFromTheEndpoint() {
        ClickAnimation animation=animation(Direction.FORWARDS);
        animation.timerUtil.setTime(NOW-1000);assertEquals(1,animation.getOutput(),0);
        animation.setDirection(Direction.BACKWARDS);assertEquals(1,animation.getOutput(),.02);
        animation.timerUtil.setTime(NOW-1000);assertEquals(0,animation.getOutput(),0);
        animation.setDirection(Direction.FORWARDS);assertEquals(0,animation.getOutput(),.02);
    }
}
