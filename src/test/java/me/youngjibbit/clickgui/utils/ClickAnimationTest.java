package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.animations.Direction;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClickAnimationTest {
    @Test public void reversingEitherDirectionPreservesTheVisiblePosition() {
        for(Direction direction:Direction.values())for(int elapsed:new int[]{30,80,140,190}) {
            ClickAnimation animation=new ClickAnimation(220,1,direction);
            animation.timerUtil.setTime(System.currentTimeMillis()-elapsed);
            double before=animation.getOutput();
            animation.setDirection(direction.opposite());
            assertEquals("Direction reversal jumped at "+elapsed,before,animation.getOutput(),.025);
        }
    }
    @Test public void completedTransitionsReverseFromTheEndpoint() {
        ClickAnimation animation=new ClickAnimation(220,1,Direction.FORWARDS);
        animation.timerUtil.setTime(System.currentTimeMillis()-1000);assertEquals(1,animation.getOutput(),0);
        animation.setDirection(Direction.BACKWARDS);assertEquals(1,animation.getOutput(),.02);
        animation.timerUtil.setTime(System.currentTimeMillis()-1000);assertEquals(0,animation.getOutput(),0);
        animation.setDirection(Direction.FORWARDS);assertEquals(0,animation.getOutput(),.02);
    }
}
