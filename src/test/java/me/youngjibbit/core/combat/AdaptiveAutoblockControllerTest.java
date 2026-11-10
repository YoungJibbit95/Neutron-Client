package me.youngjibbit.core.combat;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

public class AdaptiveAutoblockControllerTest {
    private static final class Access implements AdaptiveAutoblockController.Access {
        boolean manual, using, sword = true, denyStart, denyRelease;
        long ticks, epoch;
        Runnable before = () -> { };
        final List<Boolean> actions = new ArrayList<>();
        public boolean manualUse() { return manual; }
        public boolean usingItem() { return using; }
        public long tick() { return ticks; }
        public AdaptiveAutoblockController.Use capture() {
            if (!sword) return null;
            final long token = epoch;
            return new AdaptiveAutoblockController.Use() {
                public boolean current() { return sword && token == epoch; }
                public boolean using() { return current() && using; }
                public void discard() { if (!manual) using = false; }
                public boolean set(boolean block, BooleanSupplier eligible) {
                    before.run();
                    if (!current() || !eligible.getAsBoolean() || (block ? denyStart : denyRelease)) return false;
                    actions.add(block); using = block; return true;
                }
            };
        }
    }

    @Test public void deniedStartNeverClaimsOwnershipAndDeniedReleaseCannotAuthorizeAttack() {
        Access a = new Access(); AdaptiveAutoblockController c = new AdaptiveAutoblockController(a);
        a.denyStart = true;
        c.update(true, AdaptiveAutoblockController.Mode.VANILLA, false, 1, () -> true);
        assertFalse(c.ownsUse()); assertFalse(a.using); assertTrue(a.actions.isEmpty());
        a.denyStart = false; c.update(true, AdaptiveAutoblockController.Mode.VANILLA, false, 1, () -> true);
        assertTrue(c.ownsUse());
        a.denyRelease = true; assertFalse(c.prepareAttack(() -> true)); assertTrue(c.ownsUse());
        a.denyRelease = false; assertTrue(c.prepareAttack(() -> true)); assertFalse(c.ownsUse());
    }

    @Test public void releaseWaitsRealTicksAndForceResumesInTheAcceptedTick() {
        Access a = new Access(); AdaptiveAutoblockController c = new AdaptiveAutoblockController(a);
        c.update(true, AdaptiveAutoblockController.Mode.RELEASE, false, 3, () -> true);
        assertTrue(a.actions.isEmpty());
        c.update(true, AdaptiveAutoblockController.Mode.RELEASE, true, 3, () -> true);
        a.ticks = 2; c.update(true, AdaptiveAutoblockController.Mode.RELEASE, false, 3, () -> true);
        assertTrue(a.actions.isEmpty());
        a.ticks = 3; c.update(true, AdaptiveAutoblockController.Mode.RELEASE, false, 3, () -> true);
        assertTrue(c.ownsUse()); assertTrue(c.prepareAttack(() -> true));
        c.update(true, AdaptiveAutoblockController.Mode.RELEASE, false, 3, () -> true);
        assertFalse("a rejected attack cannot arm a hit-based reblock", c.ownsUse());
        c.update(true, AdaptiveAutoblockController.Mode.FORCE, true, 3, () -> true);
        assertTrue(c.ownsUse());
    }

    @Test public void manualUseTakesOverWithoutAnAutomaticRelease() {
        Access a = new Access(); AdaptiveAutoblockController c = new AdaptiveAutoblockController(a);
        c.update(true, AdaptiveAutoblockController.Mode.VANILLA, false, 0, () -> true);
        a.manual = true; c.release();
        assertTrue(a.using); assertFalse(c.ownsUse()); assertEquals(1, a.actions.size());
        assertFalse(c.prepareAttack(() -> true));
    }

    @Test public void fakeUsesOnlyVisualStateAndInvalidationClearsIt() {
        Access a = new Access(); AdaptiveAutoblockController c = new AdaptiveAutoblockController(a);
        c.update(true, AdaptiveAutoblockController.Mode.FAKE, true, 0, () -> true);
        assertTrue(c.visualBlocking()); assertFalse(c.ownsUse()); assertFalse(a.using); assertTrue(a.actions.isEmpty());
        c.update(true, AdaptiveAutoblockController.Mode.FAKE, false, 0, () -> false);
        assertFalse(c.visualBlocking()); assertTrue(a.actions.isEmpty());
    }

    @Test public void invalidatedSwordOwnershipDoesNotReleaseAReplacementItem() {
        Access a = new Access(); AdaptiveAutoblockController c = new AdaptiveAutoblockController(a);
        c.update(true, AdaptiveAutoblockController.Mode.VANILLA, false, 0, () -> true);
        a.epoch++; a.sword = false;
        assertFalse(c.ownsUse()); assertFalse(a.using); assertEquals(1, a.actions.size());
        c.release(); assertEquals(1, a.actions.size());
    }

    @Test public void reentrantReconfigurationAndExceptionsNeverLeaveAnOwnedStart() {
        Access a = new Access(); AdaptiveAutoblockController c = new AdaptiveAutoblockController(a);
        a.before = c::release;
        c.update(true, AdaptiveAutoblockController.Mode.VANILLA, false, 0, () -> true);
        assertFalse(c.ownsUse()); assertTrue(a.actions.isEmpty());
        a.before = () -> { throw new IllegalStateException("synthetic"); };
        try { c.update(true, AdaptiveAutoblockController.Mode.VANILLA, false, 0, () -> true); fail(); }
        catch (IllegalStateException expected) { assertFalse(c.ownsUse()); }
        a.before = () -> { };
        c.update(true, AdaptiveAutoblockController.Mode.VANILLA, false, 0, () -> true);
        assertTrue("transition flag is cleared after failure", c.ownsUse());
    }
}
