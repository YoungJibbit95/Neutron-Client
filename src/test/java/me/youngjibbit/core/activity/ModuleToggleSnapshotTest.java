package me.youngjibbit.core.activity;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

public class ModuleToggleSnapshotTest {
    @Test public void snapshotContainsOnlyFinalScalarsAndOriginalIdentity() {
        ModuleToggleSnapshot snapshot = new ModuleToggleSnapshot(7, 31, "original_id", "Original Name", true);
        assertTrue(Modifier.isFinal(ModuleToggleSnapshot.class.getModifiers()));
        for (Field field : ModuleToggleSnapshot.class.getDeclaredFields()) {
            assertTrue(field.getName(), Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getName(), field.getType().isPrimitive() || field.getType() == String.class);
        }
        assertEquals(7, snapshot.getSequence());
        assertEquals(31, snapshot.getObservedAtNanos());
        assertEquals("original_id", snapshot.getModuleId());
        assertEquals("Original Name", snapshot.getModuleName());
        assertTrue(snapshot.isEnabled());
    }

    @Test public void freshnessIsBoundedAndRejectsFutureOrExpiredEvidence() {
        long start = 9_000_000_000L;
        ModuleToggleSnapshot snapshot = new ModuleToggleSnapshot(1, start, "id", "Name", false);
        assertFalse(snapshot.isEnabled());
        assertFalse(snapshot.isFresh(start - 1));
        assertTrue(snapshot.isFresh(start));
        assertTrue(snapshot.isFresh(start + ModuleToggleSnapshot.MAX_AGE_NANOS));
        assertFalse(snapshot.isFresh(start + ModuleToggleSnapshot.MAX_AGE_NANOS + 1));
        ModuleToggleSnapshot wrap = new ModuleToggleSnapshot(2, Long.MAX_VALUE - 10, "id", "Name", true);
        assertTrue(wrap.isFresh(Long.MIN_VALUE + 10));
    }

    @Test public void invalidSequencesOrMissingIdentitiesCannotCreateEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleToggleSnapshot(0, 1, "id", "Name", true));
        assertThrows(IllegalArgumentException.class, () -> new ModuleToggleSnapshot(-1, 1, "id", "Name", true));
        assertThrows(IllegalArgumentException.class, () -> new ModuleToggleSnapshot(1, 1, null, "Name", true));
        assertThrows(IllegalArgumentException.class, () -> new ModuleToggleSnapshot(1, 1, "id", null, true));
    }
}
