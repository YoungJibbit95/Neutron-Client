package me.youngjibbit.core.activity;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

public class ActivitySnapshotTest {
    @Test public void snapshotsContainOnlyFinalScalarStringOrEnumFacts() {
        for (Class<?> type : new Class<?>[]{ScaffoldActivitySnapshot.class, InventoryActivitySnapshot.class}) {
            assertTrue(Modifier.isFinal(type.getModifiers()));
            for (Field field : type.getDeclaredFields()) {
                assertTrue(field.getName(), Modifier.isFinal(field.getModifiers()));
                assertTrue(field.getName(), field.getType().isPrimitive()
                        || field.getType() == String.class || field.getType().isEnum());
            }
        }
    }

    @Test public void freshnessHasAFiniteMonotonicBoundaryAndUnknownCountsStayUnknown() {
        ScaffoldActivitySnapshot scaffold = new ScaffoldActivitySnapshot(7, 100, 5, 2, 48,
                "Adaptive Bridge", "Adaptive", ScaffoldActivitySnapshot.Status.ALIGNING);
        assertTrue(scaffold.isFresh(100));
        assertTrue(scaffold.isFresh(100 + ScaffoldActivitySnapshot.TTL_NANOS));
        assertFalse(scaffold.isFresh(99));
        assertFalse(scaffold.isFresh(101 + ScaffoldActivitySnapshot.TTL_NANOS));
        assertEquals(5, scaffold.getItemId());
        assertEquals(2, scaffold.getMetadata());
        assertEquals(48, scaffold.getBlockCount());
        InventoryActivitySnapshot inventory = new InventoryActivitySnapshot(
                InventoryActivitySnapshot.Producer.INVENTORY_MANAGER,
                InventoryActivitySnapshot.Action.SORTING, 1, -1, 8, 100);
        assertEquals(-1, inventory.getTotal());
        assertEquals(1, inventory.getProcessed());
        assertTrue(inventory.isRunning());
        assertFalse(inventory.isComplete());
        assertFalse(inventory.isFresh(101 + InventoryActivitySnapshot.TTL_NANOS));
        assertThrows(IllegalArgumentException.class, () -> new InventoryActivitySnapshot(
                InventoryActivitySnapshot.Producer.STEALER, InventoryActivitySnapshot.Action.STEALING,
                3, 2, 9, 100));
    }
}
