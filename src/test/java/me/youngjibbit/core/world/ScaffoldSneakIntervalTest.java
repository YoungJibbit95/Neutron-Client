package me.youngjibbit.core.world;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldSneakIntervalTest {
    @Test public void exactSuccessQuotaAndTwoDistinctInputs() {
        ScaffoldSneakInterval state = new ScaffoldSneakInterval();
        state.placed(3); state.placed(3);
        assertFalse(state.input(1));
        state.placed(3);
        assertTrue(state.input(2)); assertTrue(state.input(2));
        assertTrue(state.input(3)); assertFalse(state.input(4));
        state.placed(3); state.placed(3);
        assertFalse(state.input(5));
        state.placed(3); assertTrue(state.input(6));
    }
    @Test public void disabledAndResetDiscardQuotaAndHold() {
        ScaffoldSneakInterval state = new ScaffoldSneakInterval();
        state.placed(0); assertFalse(state.input(1));
        state.placed(1); assertTrue(state.input(2));
        state.reset(); assertFalse(state.input(3));
        state.placed(2); state.reset(); state.placed(2);
        assertFalse(state.input(4));
    }
}
