package me.youngjibbit.core.inventory;

import org.junit.Test;
import java.util.*;
import me.youngjibbit.core.inventory.InventoryPlanner.Stack;
import static org.junit.Assert.*;
import static me.youngjibbit.core.inventory.InventoryPlanner.*;

public class InventoryPlannerTest {
    private Stack stack(int slot, Role role, double quality, String group, boolean protect) {
        return new Stack(slot, 1, role, quality, 100, group, protect, false, null);
    }
    private Plan plan(List<Stack> items, Role[] roles, Vacant vacant) {
        return InventoryPlanner.plan(items, roles, 2, true, vacant);
    }
    private Role[] roles(Role... prefix) {
        Role[] result = new Role[9]; Arrays.fill(result, Role.NONE);
        System.arraycopy(prefix, 0, result, 0, prefix.length); return result;
    }
    @Test public void bestEquipmentIsStableAcrossInputOrderAndProtectedCopiesSurvive() {
        List<Stack> items = Arrays.asList(stack(20, Role.SWORD, 9, "sword", false),
                stack(9, Role.SWORD, 8, "sword", false), stack(12, Role.SWORD, 7, "sword", true));
        for (int i = 0; i < 3; i++) {
            Plan p = plan(items, roles(Role.SWORD), Vacant.LEAVE);
            assertEquals(Collections.singletonList(9), p.discard);
            assertEquals(20, p.desiredSource(0));
            Collections.rotate(items, 1);
        }
        assertTrue(InventoryPlanner.plan(items, roles(Role.SWORD), 2, false, Vacant.LEAVE).discard.isEmpty());
    }
    @Test public void nineSlotPresetKeepsTwoLargestBlockStacksAndLeavesRodAvailable() {
        List<Stack> items = new ArrayList<>();
        Role[] preset = organizedRoles(); int nextSlot = 9;
        for (Role role : preset) if (role != Role.BLOCKS) items.add(stack(nextSlot++, role, 1, role.name(), false));
        items.add(new Stack(20, 64, Role.BLOCKS, 129, 0, "", false, false, BlockStackPlanner.Kind.BUILDABLE));
        items.add(new Stack(21, 48, Role.BLOCKS, 97, 0, "", false, false, BlockStackPlanner.Kind.BUILDABLE));
        items.add(new Stack(22, 4, Role.BLOCKS, 8, 0, "", false, false, BlockStackPlanner.Kind.SPECIAL));
        items.add(stack(23, Role.ROD, 0, "rod", false));
        Plan p = plan(items, preset, Vacant.UTILITY);
        assertEquals(Collections.singletonList(22), p.discard);
        assertEquals(20, p.desiredSource(4)); assertEquals(21, p.desiredSource(5));
        for (int hotbar = 0; hotbar < 9; hotbar++) assertNotEquals(23, p.desiredSource(hotbar));
        assertEquals(0, p.unavailableRoles);
    }
    @Test public void utilityFallbackYieldsToPrimaryRoleWithoutDiscardingUtility() {
        List<Stack> items = new ArrayList<>(Collections.singletonList(stack(12, Role.ROD, 0, "rod", false)));
        assertEquals(12, plan(items, roles(Role.HEALING), Vacant.UTILITY).desiredSource(0));
        items.add(stack(14, Role.HEALING, 1000, "", false));
        Plan p = plan(items, roles(Role.HEALING), Vacant.UTILITY);
        assertEquals(14, p.desiredSource(0)); assertTrue(p.discard.isEmpty());
    }
    @Test public void swapCycleConvergesAndReservationNeverDropsAnUnrelatedItem() {
        List<Stack> items = Arrays.asList(stack(0, Role.BOW, 0, "bow", false), stack(1, Role.SWORD, 1, "sword", false));
        Role[] policy = roles(Role.SWORD, Role.BOW);
        assertEquals(new Move(1, 0), plan(items, policy, Vacant.RESERVE).nextMove);
        items = Arrays.asList(stack(1, Role.BOW, 0, "bow", false), stack(0, Role.SWORD, 1, "sword", false));
        assertNull(plan(items, policy, Vacant.RESERVE).nextMove);
        Plan reserve = plan(Collections.singletonList(stack(0, Role.NONE, 0, "", false)), roles(Role.SWORD), Vacant.RESERVE);
        assertEquals(new Move(9, 0), reserve.nextMove); assertTrue(reserve.discard.isEmpty());
        assertNull(plan(Collections.singletonList(stack(0, Role.NONE, 0, "", true)), roles(Role.SWORD), Vacant.RESERVE).nextMove);
    }
    @Test public void fullInventoryReportsBlockedReservationWithoutInventingSpace() {
        List<Stack> items = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) items.add(stack(slot, Role.NONE, 0, "", false));
        Plan p = plan(items, roles(Role.SWORD), Vacant.RESERVE);
        assertNull(p.nextMove); assertEquals(1, p.blockedReservations); assertTrue(p.discard.isEmpty());
    }
    @Test public void blacklistIsExplicitAndProtectedBlocksDoNotGetDiscardedAtZeroLimit() {
        List<Stack> items = Arrays.asList(new Stack(2, 64, Role.BLOCKS, 128, 0, "", true, true, BlockStackPlanner.Kind.BUILDABLE),
                new Stack(10, 1, Role.NONE, 0, 0, "", false, true, null));
        Plan p = InventoryPlanner.plan(items, organizedRoles(), 0, true, Vacant.RESERVE);
        assertEquals(Collections.singletonList(10), p.discard);
        assertEquals(-1, p.desiredSource(4)); assertNull(p.nextMove);
    }
    @Test public void protectedBlacklistedEquipmentCannotDisplaceUsableOrdinaryEquipment() {
        List<Stack> items = Arrays.asList(new Stack(10, 1, Role.SWORD, 10, 100, "sword", true, true, null),
                stack(11, Role.SWORD, 6, "sword", false));
        Plan p = plan(items, roles(Role.SWORD), Vacant.LEAVE);
        assertTrue(p.discard.isEmpty()); assertEquals(11, p.desiredSource(0));
    }
    @Test public void equalStacksBelowTheirTargetSlotsConvergeInsteadOfSwappingForever() {
        Role[] policy = roles(Role.NONE, Role.NONE, Role.NONE, Role.NONE, Role.BLOCKS, Role.BLOCKS);
        List<Stack> items = Arrays.asList(new Stack(0, 64, Role.BLOCKS, 129, 0, "", false, false, BlockStackPlanner.Kind.BUILDABLE),
                new Stack(4, 64, Role.BLOCKS, 129, 0, "", false, false, BlockStackPlanner.Kind.BUILDABLE));
        assertEquals(new Move(0, 5), plan(items, policy, Vacant.LEAVE).nextMove);
        items = Arrays.asList(new Stack(5, 64, Role.BLOCKS, 129, 0, "", false, false, BlockStackPlanner.Kind.BUILDABLE), items.get(1));
        assertNull(plan(items, policy, Vacant.LEAVE).nextMove);
        assertEquals(4, plan(items, policy, Vacant.LEAVE).desiredSource(4));
        assertEquals(5, plan(items, policy, Vacant.LEAVE).desiredSource(5));
    }
    @Test public void repeatedUtilityFallbackRolesAlsoPreferEqualIncumbents() {
        Role[] policy = roles(Role.NONE, Role.NONE, Role.NONE, Role.NONE, Role.HEALING, Role.PEARLS);
        List<Stack> items = Arrays.asList(stack(0, Role.ROD, 0, "rod", true), stack(4, Role.ROD, 0, "rod", true));
        assertEquals(new Move(0, 5), plan(items, policy, Vacant.UTILITY).nextMove);
    }
}
