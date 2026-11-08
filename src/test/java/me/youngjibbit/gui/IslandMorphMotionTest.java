package me.youngjibbit.gui;

import me.youngjibbit.module.impl.client.island.IslandLayout;
import me.youngjibbit.module.impl.client.island.IslandStateMachine;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class IslandMorphMotionTest {
    private static IslandLayout.Frame idle() {
        return IslandLayout.forState(IslandStateMachine.State.IDLE, 854, 480);
    }
    private static IslandLayout.Frame target() {
        return IslandLayout.forState(IslandStateMachine.State.TARGET, 854, 480);
    }

    @Test public void retargetPreservesPositionAndSpringVelocity() throws Exception {
        IslandMorphMotion motion = new IslandMorphMotion();
        motion.sample(idle(), 0, true, false, "Balanced");
        IslandLayout.Frame opening = motion.sample(target(), .037f, true, false, "Balanced");
        MenuSpring spring = (MenuSpring) field(motion, "width");
        double velocity = (double) field(spring, "velocity");
        IslandLayout.Frame reversed = motion.sample(idle(), 0, true, false, "Balanced");
        assertEquals(opening.width, reversed.width, 0);
        assertEquals(velocity, (double) field(spring, "velocity"), 0);
        assertTrue(reversed.width > idle().width);
    }

    @Test public void allSupportedFrameratesConvergeToSameGeometry() {
        float prior = Float.NaN;
        for (int fps : new int[]{30,60,144,240}) {
            IslandMorphMotion motion = new IslandMorphMotion();
            motion.sample(idle(), 0, true, false, "Balanced");
            IslandLayout.Frame result = idle();
            for (int i = 0; i < fps; i++)
                result = motion.sample(target(), 1f / fps, true, false, "Balanced");
            if (Float.isFinite(prior)) assertEquals(prior, result.width, .002f);
            prior = result.width;
        }
        assertEquals(target().width, prior, .005f);
    }

    @Test public void thirtyQuickReversalsStayWithinSafeViewport() {
        IslandMorphMotion motion = new IslandMorphMotion();
        motion.sample(idle(), 0, true, false, "Expressive");
        for (int i = 0; i < 30; i++) {
            IslandLayout.Frame result = motion.sample(i % 2 == 0 ? target() : idle(),
                    .012f, true, false, "Expressive");
            assertTrue(Float.isFinite(result.width) && Float.isFinite(result.radius));
            assertTrue(result.x >= 0 && result.x + result.width <= 854);
            assertTrue(result.y >= 0 && result.y + result.height <= 480);
        }
    }

    @Test public void animationsOffAndReducedMotionSnapToReadableState() {
        IslandMorphMotion motion = new IslandMorphMotion();
        motion.sample(idle(), 0, true, false, "Balanced");
        assertEquals(target().width, motion.sample(target(), 0, false, false, "Balanced").width, 0);
        assertEquals(idle().width, motion.sample(idle(), 0, true, true, "Expressive").width, 0);
        motion.contentChanged();
        assertEquals(1, motion.sample(target(), 0, true, true, "Calm").opacity, 0);
        assertEquals(1, motion.contentAlpha(), 0);
    }

    @Test public void contentCrossfadeIsBoundedAndShapeNeverResetsOnTextChange() {
        IslandMorphMotion motion = new IslandMorphMotion();
        motion.sample(idle(), 0, true, false, "Balanced");
        IslandLayout.Frame moving = motion.sample(target(), .028f, true, false, "Balanced");
        motion.contentChanged();
        assertEquals(0, motion.contentAlpha(), 0);
        IslandLayout.Frame same = motion.sample(target(), 0, true, false, "Balanced");
        assertEquals(moving.width, same.width, 0);
        assertEquals(moving.height, same.height, 0);
        assertEquals(0, motion.contentAlpha(), 0);
        for (int i = 0; i < 60; i++)
            motion.sample(target(), .016f, true, false, "Balanced");
        assertTrue(motion.contentAlpha() > .99f && motion.contentAlpha() <= 1);
    }

    @Test public void invalidTimeAndLargeFocusPauseAreBounded() {
        IslandMorphMotion a = new IslandMorphMotion(), b = new IslandMorphMotion();
        a.sample(idle(), 0, true, false, "Balanced");
        b.sample(idle(), 0, true, false, "Balanced");
        IslandLayout.Frame before = a.sample(target(), Float.NaN, true, false, "Balanced");
        assertEquals(idle().width, before.width, 0);
        assertEquals(b.sample(target(), .05f, true, false, "Balanced").width,
                a.sample(target(), 900, true, false, "Balanced").width, .00001f);
        a.reset();
        assertEquals(target().width, a.sample(target(), 0, true, false, "Balanced").width, 0);
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }
}
