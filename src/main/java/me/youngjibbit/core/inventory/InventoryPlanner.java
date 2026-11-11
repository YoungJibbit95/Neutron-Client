package me.youngjibbit.core.inventory;

import java.util.*;

/** Deterministic inventory decisions. No clicks, timers, mutable stacks or game state live here. */
public final class InventoryPlanner {
    public enum Role {
        NONE("Unassigned"), SWORD("Sword"), BOW("Bow"), PICKAXE("Pickaxe"), AXE("Axe"),
        BLOCKS("Blocks"), FOOD("Food"), PEARLS("Ender Pearls"), HEALING("Healing Potion"),
        ROD("Fishing Rod"), WATER("Water Bucket"), LAVA("Lava Bucket"), FLINT("Flint and Steel");
        public final String label;
        Role(String label) { this.label = label; }
        public static Role named(String name) {
            for (Role role : values()) if (role.label.equalsIgnoreCase(name)) return role;
            return NONE;
        }
        public static String[] labels() {
            String[] labels = new String[values().length];
            for (int i = 0; i < labels.length; i++) labels[i] = values()[i].label;
            return labels;
        }
    }
    public enum Vacant { RESERVE, UTILITY, LEAVE }
    public static Role[] organizedRoles() {
        return new Role[]{Role.SWORD, Role.BOW, Role.PICKAXE, Role.AXE, Role.BLOCKS,
                Role.BLOCKS, Role.HEALING, Role.FOOD, Role.PEARLS};
    }

    public static final class Stack {
        public final int slot, count, durability;
        public final Role role;
        public final double quality;
        /** Equal nonempty keys identify interchangeable non-consumable equipment. */
        public final String duplicateKey;
        public final boolean protectedItem, blacklisted;
        public final BlockStackPlanner.Kind blockKind;
        public Stack(int slot, int count, Role role, double quality, int durability, String duplicateKey,
                     boolean protectedItem, boolean blacklisted, BlockStackPlanner.Kind blockKind) {
            if (slot < 0 || slot >= 36 || count < 1 || role == null || !Double.isFinite(quality))
                throw new IllegalArgumentException("Invalid inventory snapshot");
            this.slot = slot; this.count = count; this.role = role; this.quality = quality;
            this.durability = durability; this.duplicateKey = duplicateKey == null ? "" : duplicateKey;
            this.protectedItem = protectedItem; this.blacklisted = blacklisted; this.blockKind = blockKind;
        }
    }
    public static final class Move {
        /** Source uses inventory indexes (0–35); an empty source evacuates a reserved hotbar slot. */
        public final int source, hotbar;
        public Move(int source, int hotbar) { this.source = source; this.hotbar = hotbar; }
        @Override public boolean equals(Object other) {
            return other instanceof Move && source == ((Move) other).source && hotbar == ((Move) other).hotbar;
        }
        @Override public int hashCode() { return source * 31 + hotbar; }
    }
    public static final class Plan {
        public final List<Integer> discard;
        private final int[] desired;
        public final Move nextMove;
        public final int unavailableRoles, blockedReservations;
        private Plan(List<Integer> discard, int[] desired, Move move, int missing, int blocked) {
            this.discard = Collections.unmodifiableList(new ArrayList<>(discard));
            this.desired = desired.clone(); this.nextMove = move;
            this.unavailableRoles = missing; this.blockedReservations = blocked;
        }
        public int desiredSource(int hotbar) { return desired[hotbar]; }
    }

    private static int comparePriority(Stack a, Stack b) {
        int cmp = Double.compare(b.quality, a.quality);
        if (cmp == 0) cmp = Integer.compare(b.durability, a.durability);
        if (cmp == 0) cmp = Integer.compare(b.count, a.count);
        return cmp;
    }
    private static final Comparator<Stack> BEST = (a, b) -> {
        int cmp = comparePriority(a, b); return cmp == 0 ? Integer.compare(a.slot, b.slot) : cmp;
    };
    private static Stack choose(List<Stack> kept, Role role, Set<Integer> assigned, Stack incumbent) {
        Stack best = null;
        for (Stack s : kept) if (s.role == role && !assigned.contains(s.slot)) { best = s; break; }
        // An equal stack already on its role must win the slot-index tie. Otherwise
        // swapping two identical stacks can repeat forever and starve later roles.
        if (best != null && incumbent != null && incumbent.role == role && !assigned.contains(incumbent.slot)
                && kept.contains(incumbent) && comparePriority(incumbent, best) == 0) return incumbent;
        return best;
    }

    public static Plan plan(List<Stack> inventory, Role[] roles, int blockLimit, boolean keepOne, Vacant vacant) {
        if (roles == null || roles.length != 9 || blockLimit < 0 || vacant == null)
            throw new IllegalArgumentException("Invalid inventory policy");
        Stack[] bySlot = new Stack[36];
        List<Stack> ordered = new ArrayList<>(inventory);
        ordered.sort(Comparator.comparingInt(s -> s.slot));
        for (Stack s : ordered) {
            if (bySlot[s.slot] != null) throw new IllegalArgumentException("Duplicate inventory slot");
            bySlot[s.slot] = s;
        }
        LinkedHashSet<Integer> drop = new LinkedHashSet<>();
        for (Stack s : ordered) if (s.blacklisted && !s.protectedItem) drop.add(s.slot);
        if (keepOne) {
            Map<String, Stack> best = new HashMap<>();
            for (Stack s : ordered) if (!drop.contains(s.slot) && !s.blacklisted && !s.duplicateKey.isEmpty()) {
                Stack previous = best.get(s.duplicateKey);
                if (previous == null || BEST.compare(s, previous) < 0) best.put(s.duplicateKey, s);
            }
            for (Stack s : ordered) if (!s.protectedItem && !drop.contains(s.slot) &&
                    !s.duplicateKey.isEmpty() && best.get(s.duplicateKey) != s) drop.add(s.slot);
        }
        List<BlockStackPlanner.Snapshot> blocks = new ArrayList<>();
        int protectedBlocks = 0;
        for (Stack s : ordered) if (!drop.contains(s.slot) && s.blockKind != null) {
            if (s.protectedItem) protectedBlocks++;
            else blocks.add(new BlockStackPlanner.Snapshot(s.slot, s.count, s.blockKind));
        }
        for (BlockStackPlanner.Snapshot overflow : BlockStackPlanner.plan(blocks,
                Math.max(0, blockLimit - protectedBlocks)).trashBlockStacks) drop.add(overflow.slot);

        List<Stack> kept = new ArrayList<>();
        for (Stack s : ordered) if (!drop.contains(s.slot) && !s.blacklisted) kept.add(s);
        kept.sort(BEST);
        int[] desired = new int[9]; Arrays.fill(desired, -1);
        Set<Integer> assigned = new HashSet<>();
        int missing = 0;
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            Role role = roles[hotbar];
            if (role == null) throw new IllegalArgumentException("Missing hotbar role");
            if (role == Role.NONE) continue;
            Stack chosen = choose(kept, role, assigned, bySlot[hotbar]);
            if (chosen != null) { desired[hotbar] = chosen.slot; assigned.add(chosen.slot); }
            if (desired[hotbar] < 0) missing++;
        }
        if (vacant == Vacant.UTILITY) {
            Role[] utility = {Role.ROD, Role.WATER, Role.LAVA, Role.FLINT};
            for (int hotbar = 0; hotbar < 9; hotbar++) if (roles[hotbar] != Role.NONE && desired[hotbar] < 0) {
                for (Role role : utility) {
                    Stack chosen = choose(kept, role, assigned, bySlot[hotbar]);
                    if (chosen != null) { desired[hotbar] = chosen.slot; assigned.add(chosen.slot); break; }
                }
            }
        }
        Move next = null;
        for (int hotbar = 0; hotbar < 9; hotbar++) if (desired[hotbar] >= 0 && desired[hotbar] != hotbar) {
            next = new Move(desired[hotbar], hotbar); break;
        }
        int blocked = 0;
        if (vacant != Vacant.LEAVE) for (int hotbar = 0; hotbar < 9; hotbar++) {
            if (roles[hotbar] == Role.NONE || desired[hotbar] >= 0 || bySlot[hotbar] == null ||
                    drop.contains(hotbar) || bySlot[hotbar].protectedItem) continue;
            int empty = -1;
            for (int slot = 9; slot < 36; slot++) if (bySlot[slot] == null) { empty = slot; break; }
            if (empty < 0) blocked++;
            else if (next == null) next = new Move(empty, hotbar);
        }
        return new Plan(new ArrayList<>(drop), desired, next, missing, blocked);
    }
    private InventoryPlanner() { }
}
