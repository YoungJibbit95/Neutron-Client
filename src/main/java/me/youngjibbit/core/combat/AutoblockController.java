package me.youngjibbit.core.combat;

import me.youngjibbit.utils.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import java.util.function.*;

public final class AutoblockController {
    public enum Mode { WATCHDOG, VERUS, FAKE }
    private final BooleanSupplier playerReady, manualUse;
    private final IntSupplier tick;
    private final Consumer<Boolean> send;
    private boolean owned;

    public AutoblockController() {
        this(() -> Minecraft.getMinecraft().thePlayer != null,
                () -> Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown(),
                () -> Minecraft.getMinecraft().thePlayer.ticksExisted,
                block -> {
                    if (block) PacketUtils.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(Minecraft.getMinecraft().thePlayer.getHeldItem()));
                    else PacketUtils.sendPacketNoEvent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                });
    }

    public AutoblockController(BooleanSupplier playerReady, BooleanSupplier manualUse, IntSupplier tick, Consumer<Boolean> send) {
        this.playerReady=playerReady; this.manualUse=manualUse; this.tick=tick; this.send=send;
    }

    public void update(boolean enabled, Mode mode, boolean pre, boolean post) {
        if (!playerReady.getAsBoolean()) { reset(); return; }
        if (!enabled || manualUse.getAsBoolean()) { release(); return; }
        if (mode == Mode.FAKE) { release(); return; }
        if (mode == Mode.WATCHDOG && pre && owned && tick.getAsInt()%4 == 0) release();
        if (mode == Mode.VERUS && pre) release();
        // Blocking is resumed after the attack, never immediately before the controller action.
        if (post && !owned) { send.accept(true); owned=true; }
    }

    public void beforeAttack() { release(); }

    public void release() {
        if (owned && playerReady.getAsBoolean() && !manualUse.getAsBoolean()) send.accept(false);
        owned=false;
    }

    /** World unload has no valid old connection to release on. */
    public void reset() { owned=false; }
}
