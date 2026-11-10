package me.youngjibbit.core.combat;

import me.youngjibbit.core.network.PacketPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.world.World;
import java.util.function.BooleanSupplier;

/** Adaptive sword-use ownership. A request is not ownership until its guarded action succeeds. */
public final class AdaptiveAutoblockController {
    public enum Mode { FAKE, VANILLA, RELEASE, FORCE }
    public interface Use {
        boolean current();
        boolean using();
        boolean set(boolean block, BooleanSupplier eligible);
        default void discard() { }
    }
    public interface Access {
        boolean manualUse();
        boolean usingItem();
        long tick();
        Use capture();
    }

    private final Access access;
    private Use owned;
    private boolean visual, armed, transitioning;
    private long revision, reblockAt;

    public AdaptiveAutoblockController() { this(new ClientAccess()); }
    public AdaptiveAutoblockController(Access access) { this.access = java.util.Objects.requireNonNull(access); }

    private void refresh() {
        if (owned != null && !owned.current()) { owned.discard(); owned = null; }
        if (owned != null && !owned.using()) owned = null;
        // Physical use takes over; an automatic cleanup must never release the user's action.
        if (access.manualUse()) { owned = null; armed = false; visual = false; }
    }

    /** Called only when an admitted click is due, before final !isUsingItem/raycast validation. */
    public boolean prepareAttack(BooleanSupplier eligible) {
        refresh();
        if (transitioning || access.manualUse() || !eligible.getAsBoolean()) return false;
        if (owned != null && !release()) return false;
        armed = false;
        return !access.usingItem() && eligible.getAsBoolean();
    }

    /** Real client ticks define the delay. Force resumes immediately after an accepted attack. */
    public void update(boolean enabled, Mode mode, boolean attackAccepted, int delayTicks, BooleanSupplier eligible) {
        refresh();
        if (!enabled || !eligible.getAsBoolean()) { release(); return; }
        if (access.manualUse() || transitioning) return;
        if (mode == Mode.FAKE) {
            release();
            visual = eligible.getAsBoolean() && access.capture() != null;
            return;
        }
        visual = false;
        if (attackAccepted) {
            armed = true;
            reblockAt = access.tick() + (mode == Mode.FORCE ? 0 : Math.max(0, delayTicks));
        }
        if (owned != null || access.usingItem() || access.tick() < reblockAt
                || (mode != Mode.VANILLA && !armed)) return;
        final Use candidate = access.capture();
        if (candidate == null || !candidate.current()) return;
        final long attempt = revision;
        transitioning = true;
        try {
            boolean accepted = candidate.set(true, () -> revision == attempt && !access.manualUse()
                    && candidate.current() && eligible.getAsBoolean());
            if (accepted && revision == attempt && candidate.current() && candidate.using()
                    && !access.manualUse()) owned = candidate;
        } finally { transitioning = false; }
    }

    /** Cleanup is allowed after GUI/focus loss, but only in the original live item/connection context. */
    public boolean release() {
        final long attempt = ++revision;
        armed = false;
        visual = false;
        refresh();
        final Use previous = owned;
        if (previous == null) return true;
        if (transitioning) return false;
        transitioning = true;
        try {
            boolean accepted = previous.set(false, () -> revision == attempt && owned == previous
                    && previous.current() && !access.manualUse());
            if (accepted && owned == previous) owned = null;
            refresh();
            return owned == null;
        } finally { transitioning = false; }
    }

    /** Scene unload has no valid old transport on which to release. */
    public void reset() { revision++; owned = null; visual = armed = false; reblockAt = 0; }
    public boolean ownsUse() { refresh(); return owned != null; }
    public boolean visualBlocking() { refresh(); return visual; }

    private static final class ClientAccess implements Access {
        private Minecraft client() { return Minecraft.getMinecraft(); }
        public boolean manualUse() { Minecraft mc = client(); return mc != null && mc.gameSettings != null
                && mc.gameSettings.keyBindUseItem != null && mc.gameSettings.keyBindUseItem.isKeyDown(); }
        public boolean usingItem() { Minecraft mc = client(); return mc != null && mc.thePlayer != null && mc.thePlayer.isUsingItem(); }
        public long tick() { Minecraft mc = client(); return mc == null || mc.thePlayer == null ? 0 : mc.thePlayer.ticksExisted; }
        public Use capture() {
            final Minecraft mc = client();
            if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                    || mc.getNetHandler() == null) return null;
            final EntityPlayerSP player = mc.thePlayer;
            final World world = mc.theWorld;
            final PlayerControllerMP controller = mc.playerController;
            final ItemStack sword = player.getHeldItem();
            if (sword == null || !(sword.getItem() instanceof ItemSword)) return null;
            final PacketPipeline pipeline = mc.getNetHandler().getNetworkManager().getClientPackets();
            if (pipeline == null || !pipeline.isActive()) return null;
            final long epoch = pipeline.getContextVersion();
            return new Use() {
                public boolean current() { return client() == mc && mc.thePlayer == player && mc.theWorld == world
                        && mc.playerController == controller && !player.isDead && player.getHeldItem() == sword
                        && mc.getNetHandler() != null && pipeline == mc.getNetHandler().getNetworkManager().getClientPackets()
                        && pipeline.isActive() && pipeline.getContextVersion() == epoch; }
                public boolean using() { return current() && player.isUsingItem() && player.getItemInUse() == sword; }
                public boolean set(boolean block, BooleanSupplier eligible) {
                    return current() && controller.trySetSwordBlocking(player, block,
                            () -> current() && eligible.getAsBoolean());
                }
                public void discard() {
                    // A slot/context invalidation cannot send a release for a replacement item.
                    // Clear only the old automatic local sword use in the same player/world.
                    if (client() == mc && mc.thePlayer == player && mc.theWorld == world
                            && player.getItemInUse() == sword && !ClientAccess.this.manualUse()) player.stopUsingItem();
                }
            };
        }
    }
}
