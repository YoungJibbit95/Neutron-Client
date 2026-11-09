package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandStateMachineTest {
    private static IslandStateMachine.Target target(String name) {
        return new IslandStateMachine.Target(17, name, 12, 20, 2);
    }

    @Test public void idleAndUnknownStatesAreSafe() {
        IslandStateMachine model = new IslandStateMachine();
        assertEquals(IslandStateMachine.State.IDLE, IslandStateMachine.State.fromName("unregistered"));
        assertEquals(IslandStateMachine.State.IDLE, IslandStateMachine.State.fromName(null));
        assertEquals(IslandStateMachine.State.TARGET, IslandStateMachine.State.fromName("target"));
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(1, 0, null, null, true, true).state);
    }

    @Test public void priorityWarningToastTargetScaffoldIdle() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Target live = target("Neutron victim");
        IslandStateMachine.Scaffold scaffold = new IslandStateMachine.Scaffold(true, 32, "Normal");
        assertEquals(IslandStateMachine.State.TARGET, model.resolve(1, 0, live, scaffold, true, true).state);
        assertEquals(IslandStateMachine.State.SCAFFOLD, model.resolve(1, 0, live, scaffold, false, true).state);
        assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST, "toggle1", "Module enabled", 1, 10));
        assertEquals(IslandStateMachine.State.MODULE_TOAST, model.resolve(1, 20, live, scaffold, true, true).state);
        assertTrue(model.offer(IslandStateMachine.State.POSSIBLE_SETBACK, "local1", "correction", 1, 100));
        assertEquals(IslandStateMachine.State.POSSIBLE_SETBACK, model.resolve(1, 120, live, scaffold, true, true).state);
        assertFalse(model.offer(IslandStateMachine.State.MODULE_TOAST, "toggle2", "enabled", 1, 200));
        assertEquals(IslandStateMachine.State.TARGET,
                model.resolve(1, 2_400_000_200L, live, scaffold, true, true).state);
    }

    @Test public void expiryReturnsToFreshSnapshotNotCachedName() {
        IslandStateMachine model = new IslandStateMachine();
        assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST, "t1", "Test", 4, 200));
        assertEquals(IslandStateMachine.State.MODULE_TOAST,
                model.resolve(4, 200, target("Old"), null, true, true).state);
        assertEquals("New", model.resolve(4, 1_900_000_201L, target("New"), null, true, true).target.name);
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(4, 1_900_000_202L, null, null, true, true).state);
    }

    @Test public void worldEpochRejectsOldEventsAndDoesNotCacheTarget() {
        IslandStateMachine model = new IslandStateMachine();
        assertEquals(IslandStateMachine.State.TARGET, model.resolve(8, 4, target("Before"), null, true, false).state);
        assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST, "epoch8", "Old", 8, 10));
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(9, 0, null, null, true, true).state);
        assertEquals(0, model.pendingEventCount());
        assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST, "epoch8", "New scene", 9, 20));
        assertEquals("New scene", model.resolve(9, 20, null, null, true, true).label);
    }

    @Test public void thirtyRapidTogglesKeepOnlyTheMostRecentEvent() {
        IslandStateMachine model = new IslandStateMachine();
        for (int i = 0; i < 30; i++) {
            assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST,
                    "t" + i, "Toggle " + i, 1, i * 1_000_000L));
            assertEquals(1, model.pendingEventCount());
            assertEquals("Toggle " + i, model.resolve(1, i * 1_000_000L, null, null, true, true).label);
            assertFalse(model.offer(IslandStateMachine.State.MODULE_TOAST,
                    "t" + i, "Replay", 1, i * 1_000_000L));
        }
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(1, 2_000_000_000L, null, null, true, true).state);
        assertEquals(0, model.pendingEventCount());
    }

    @Test public void warningIsOnlyAnUnconfirmedLocalHintWithCooldown() {
        IslandStateMachine model = new IslandStateMachine();
        assertTrue(model.offer(IslandStateMachine.State.POSSIBLE_SETBACK, "a", "Confirmed flag!", 1, 1));
        assertEquals("Possible setback", model.resolve(1, 10, null, null, true, true).label);
        assertFalse(model.offer(IslandStateMachine.State.POSSIBLE_SETBACK, "b", "Again", 1, 2_500_000_000L));
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(1, 2_500_000_000L, null, null, true, true).state);
        assertTrue(model.offer(IslandStateMachine.State.POSSIBLE_SETBACK, "c", "Local", 1, 4_100_000_000L));
    }

    @Test public void invalidProvidersNeverCreateGameplayClaims() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Scaffold absent = new IslandStateMachine.Scaffold(false, 99, "Spoofed");
        IslandStateMachine.Scaffold unknown = new IslandStateMachine.Scaffold(true, -1, "Unknown");
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(1, 0, null, absent, true, true).state);
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(1, 0, null, unknown, true, true).state);
        assertFalse(model.offer(IslandStateMachine.State.TARGET, "t", "no", 1, 3));
        assertFalse(model.offer(IslandStateMachine.State.IDLE, "i", "no", 1, 3));
        assertEquals(IslandStateMachine.State.SCAFFOLD,
                model.resolve(1, 0, null, new IslandStateMachine.Scaffold(true, 0, "Safe fixture"), true, true).state);
    }

    @Test public void healthAndAbsorptionAreFiniteAndClamped() {
        IslandStateMachine.Target invalid = new IslandStateMachine.Target(1, "Ä😀", Float.NaN,
                Float.NaN, Float.POSITIVE_INFINITY);
        assertTrue(invalid.valid());
        assertEquals(20f, invalid.maxHealth, 0);
        assertEquals(0f, invalid.health, 0);
        assertEquals(0f, invalid.absorption, 0);
        assertEquals(0f, invalid.fraction(), 0);
        IslandStateMachine.Target other = new IslandStateMachine.Target(2, "x", 999, 30, 9000);
        assertEquals(30f, other.health, 0);
        assertEquals(2048f, other.absorption, 0);
        assertEquals(1f, other.fraction(), 0);
        assertFalse(new IslandStateMachine.Target(-1, "x", 1, 20, 0).valid());
        assertFalse(new IslandStateMachine.Target(3, "", 1, 20, 0).valid());
    }

    @Test public void backwardAndInvalidEpochTimesCannotResurrectEvents() {
        IslandStateMachine model = new IslandStateMachine();
        assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST, "one", "A", 5, 100));
        model.resolve(5, 1_900_000_101L, null, null, true, true);
        assertEquals(0, model.pendingEventCount());
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(5, 1, null, null, true, true).state);
        model.reset();
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(5, 1, null, null, true, true).state);
    }
    @Test public void idempotencyWindowSuppressesInterleavedDuplicatesWithoutGrowing() {
        IslandStateMachine model = new IslandStateMachine();
        for (int i = 0; i < 30; i++)
            assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST,
                    "id-" + i, "Toggle " + i, 1, i * 1_000_000L));
        assertEquals(30, model.rememberedEventCount());
        assertEquals(1, model.pendingEventCount());
        assertFalse(model.offer(IslandStateMachine.State.MODULE_TOAST,
                "id-0", "Duplicate old toggle", 1, 30_000_000L));
        assertEquals(30, model.rememberedEventCount());
        for (int i = 30; i < 90; i++) {
            assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST,
                    "id-" + i, "Toggle " + i, 1, i * 1_000_000L));
            assertTrue(model.rememberedEventCount() <= 32);
            assertEquals(1, model.pendingEventCount());
        }
        assertEquals(32, model.rememberedEventCount());
        // After the bounded replay horizon, IDs can be legitimately reused.
        assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST,
                "id-0", "Fresh later", 1, 90_000_000L));
        assertEquals(32, model.rememberedEventCount());
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(2, 0, null, null, true, true).state);
        assertEquals(0, model.rememberedEventCount());
        assertEquals(0, model.pendingEventCount());
    }

    @Test public void clockReminderIsBelowTargetAndScaffoldButAboveIdle() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Target live = target("Neutron victim");
        IslandStateMachine.ClockReminder noon = new IslandStateMachine.ClockReminder("12:00", false);
        assertEquals(IslandStateMachine.State.TARGET,
                model.resolve(1, 0, live, null, noon, null, true, true, true, true).state);
        IslandStateMachine.Scaffold scaf = new IslandStateMachine.Scaffold(true, 5, "Normal");
        assertEquals(IslandStateMachine.State.SCAFFOLD,
                model.resolve(1, 0, null, scaf, noon, null, true, true, true, true).state);
        assertEquals(IslandStateMachine.State.CLOCK_REMINDER,
                model.resolve(1, 0, null, null, noon, null, true, true, true, true).state);
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(1, 0, null, null, noon, null, true, true, false, true).state);
    }

    @Test public void clockReminderDeduplicatesPerTenMinuteSlot() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.ClockReminder noon = new IslandStateMachine.ClockReminder("12:00", false);
        long noonNanos = 12L * 60_000_000_000L;
        assertTrue(model.offer(IslandStateMachine.State.CLOCK_REMINDER, "clock:12:00", "12:00", 1, noonNanos));
        assertFalse(model.offer(IslandStateMachine.State.CLOCK_REMINDER, "clock:12:00", "12:00", 1, noonNanos + 5_000_000_000L));
        IslandStateMachine.ClockReminder twelveTen = new IslandStateMachine.ClockReminder("12:10", false);
        assertTrue(model.offer(IslandStateMachine.State.CLOCK_REMINDER, "clock:12:10", "12:10", 1, noonNanos + 10_000_000_000L));
        assertEquals(IslandStateMachine.State.CLOCK_REMINDER,
                model.resolve(1, noonNanos + 10_000_000_000L, null, null, twelveTen, null, true, true, true, true).state);
    }

    @Test public void inventoryActivityIsBelowClockButAboveIdle() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.InventoryActivity sort = new IslandStateMachine.InventoryActivity("Sorting", 10, 64, false);
        IslandStateMachine.ClockReminder noon = new IslandStateMachine.ClockReminder("12:00", false);
        assertEquals(IslandStateMachine.State.CLOCK_REMINDER,
                model.resolve(1, 0, null, null, noon, sort, true, true, true, true).state);
        assertEquals(IslandStateMachine.State.INVENTORY_ACTIVITY,
                model.resolve(1, 0, null, null, null, sort, true, true, true, true).state);
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(1, 0, null, null, null, sort, true, true, true, false).state);
    }

    @Test public void sceneEpochClearsClockAndInventoryState() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.ClockReminder noon = new IslandStateMachine.ClockReminder("12:00", false);
        long noonNanos = 12L * 60_000_000_000L;
        assertTrue(model.offer(IslandStateMachine.State.CLOCK_REMINDER, "clock:12:00", "12:00", 7, noonNanos));
        assertEquals(IslandStateMachine.State.CLOCK_REMINDER,
                model.resolve(7, noonNanos, null, null, noon, null, true, true, true, true).state);
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(8, noonNanos, null, null, noon, null, true, true, true, true).state);
        assertEquals(0, model.pendingEventCount());
    }

    @Test public void inventoryActivityExpiresAfterTtl() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.InventoryActivity sort = new IslandStateMachine.InventoryActivity("Sorting", 10, 64, false);
        long now = 100_000_000_000L;
        assertTrue(model.offer(IslandStateMachine.State.INVENTORY_ACTIVITY, "inv:sort", "Sorting", 1, now));
        assertEquals(IslandStateMachine.State.INVENTORY_ACTIVITY,
                model.resolve(1, now, null, null, null, sort, true, true, true, true).state);
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(1, now + 5_000_000_001L, null, null, null, sort, true, true, true, true).state);
        assertEquals(0, model.pendingEventCount());
    }

    @Test public void legacySixParameterResolveStillWorks() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Target live = target("Neutron victim");
        IslandStateMachine.Scaffold scaf = new IslandStateMachine.Scaffold(true, 3, "Arsenic");
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(1, 0, null, null, true, true).state);
        assertEquals(IslandStateMachine.State.TARGET, model.resolve(1, 0, live, scaf, true, true).state);
        assertEquals(IslandStateMachine.State.SCAFFOLD, model.resolve(1, 0, live, scaf, false, true).state);
        assertEquals(IslandStateMachine.State.IDLE, model.resolve(1, 0, null, null, false, false).state);
    }

    @Test public void invalidClockAndInventoryAreNeverShown() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.ClockReminder emptyClock = new IslandStateMachine.ClockReminder("", false);
        IslandStateMachine.InventoryActivity emptyInv = new IslandStateMachine.InventoryActivity("", 0, 0, false);
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(1, 0, null, null, emptyClock, null, true, true, true, true).state);
        assertEquals(IslandStateMachine.State.IDLE,
                model.resolve(1, 0, null, null, null, emptyInv, true, true, true, true).state);
    }

}
