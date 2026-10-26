package me.youngjibbit.core.world;

import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

/** Immutable intent and the world/inventory identities against which it was prepared. */
public final class PlacementTarget {
    private final BlockPos destination, blockPos;
    private final EnumFacing facing;
    private final Vec3 hitVec, baseHitVec;
    final Object trajectoryKey;
    final boolean pointChosen;
    private final int slot, selectedSlot;
    final Object world, player, supportState;
    final ItemStack stack, stackSnapshot;

    PlacementTarget(BlockPos destination, BlockPos support, EnumFacing facing, Vec3 hitVec,
                    int slot, int selectedSlot, Object world, Object player, Object supportState, ItemStack stack) {
        this.destination = destination;
        this.blockPos = support;
        this.facing = facing;
        this.hitVec = this.baseHitVec = hitVec;
        this.trajectoryKey = new Object();
        this.pointChosen = false;
        this.slot = slot;
        this.selectedSlot = selectedSlot;
        this.world = world;
        this.player = player;
        this.supportState = supportState;
        this.stack = stack;
        this.stackSnapshot = stack.copy();
    }

    private PlacementTarget(PlacementTarget original,Vec3 hit) {
        destination=original.destination;blockPos=original.blockPos;facing=original.facing;
        hitVec=hit;baseHitVec=original.baseHitVec;trajectoryKey=original.trajectoryKey;pointChosen=true;
        slot=original.slot;selectedSlot=original.selectedSlot;world=original.world;player=original.player;
        supportState=original.supportState;stack=original.stack;stackSnapshot=original.stackSnapshot;
    }
    PlacementTarget withHit(Vec3 hit) { return new PlacementTarget(this,hit); }
    Vec3 baseHit() { return baseHitVec; }

    public BlockPos getDestination() { return destination; }
    public BlockPos getBlockPos() { return blockPos; }
    public EnumFacing getFacing() { return facing; }
    public Vec3 getHitVec() { return hitVec; }
    public int getSlot() { return slot; }
    public int getSelectedSlot() { return selectedSlot; }
}
