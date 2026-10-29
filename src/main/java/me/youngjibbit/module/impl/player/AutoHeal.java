package me.youngjibbit.module.impl.player;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.lwjgl.input.Keyboard;
import java.util.List;

@ModuleInfo(name = "AutoHeal", description = "Splash Instant Health II or server mushroom stew; optional inventory transfers wait for confirmation/resync", category = Category.Ghost)
public class AutoHeal extends Module {
    private final SliderValue minHealth = new SliderValue("Min Health", this, 6.0, 1.0f, 20.0f, true);
    private final ComboValue currentMode = new ComboValue("Mode", this, new String[]{"Potions", "Soup"}, 1);
    private final CheckBoxValue inventoryTransfers = new CheckBoxValue("Inventory Transfers", this, false);
    private final AutoHealInventoryTransfer transfer = new AutoHealInventoryTransfer(mc);
    private EntityPlayerSP contextPlayer;
    private WorldClient contextWorld;
    private net.minecraft.client.network.NetHandlerPlayClient contextConnection;
    private long nextUseNanos;
    private boolean using;
    private long generation;

    public AutoHeal() { super.setKey(Keyboard.KEY_0); }
    @Override public void onEnable() { reset(); super.onEnable(); }
    @Override public void onDisable() { reset(); super.onDisable(); }
    private void reset() { generation++; nextUseNanos = 0; contextPlayer = null; contextWorld = null; contextConnection = null; }
    private void bindContext() {
        transfer.bind();
        if (contextPlayer != mc.thePlayer || contextWorld != mc.theWorld || contextConnection != (mc.thePlayer == null ? null : mc.thePlayer.sendQueue)) {
            reset(); contextPlayer = mc.thePlayer; contextWorld = mc.theWorld; contextConnection = mc.thePlayer == null ? null : mc.thePlayer.sendQueue;
        }
    }
    @Subscribe private final Listener<WorldEvent> worldReset = new Listener<>(e -> { reset(); transfer.clear(); });
    @Subscribe private final Listener<TickEvent> onTick = new Listener<>(e -> {
        bindContext();
        if (transfer.blocked()) { info = transfer.status(); return; }
        info = "";
        if (!e.isPre() || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || using ||
                !mc.inGameHasFocus || !mc.thePlayer.isEntityAlive() || mc.thePlayer.isSpectator() || mc.thePlayer.isUsingItem() || mc.thePlayer.inventory.getItemStack() != null || mc.currentScreen != null ||
                mc.thePlayer.getHealth() > minHealth.getValue().floatValue() || System.nanoTime() < nextUseNanos) return;
        boolean potion = "Potions".equals(currentMode.getValue());
        if (potion ? shouldUsePotion() : shouldUseSoup()) {
            if (mayStartTickAction()) useHealingItem(potion);
        } else if (inventoryTransfers.getValue() && mayStartTickAction())
            transfer.begin(stack -> isHealingItem(stack, potion));
    });

    /** Start-only prerequisite; pending cleanup and later packet guards do not require this tick. */
    private static boolean mayStartTickAction() {
        RuntimeObservation observation = RuntimeServices.observation();
        if (observation == null) return false;
        long tick = observation.getActiveOuterTickId();
        // TickEvent precedes the player decision. Neither a live body nor its history grants entry.
        if (tick < 0 || observation.getCurrentStep() != null
                || !observation.getLastSteps().isEmpty() || observation.getLastWithheld() != null) return false;
        SceneContext before = observation.captureScene();
        SceneContext fresh = observation.captureScene();
        // Sampling can reenter lifecycle code: recheck canonical identity and lifetime afterwards.
        return RuntimeServices.observation() == observation && observation.getActiveOuterTickId() == tick
                && observation.getCurrentStep() == null && observation.getLastSteps().isEmpty()
                && observation.getLastWithheld() == null
                && before.isValid() && before.getAvailability() == SceneContext.Availability.ACTIVE
                && fresh.isValid() && fresh.getAvailability() == SceneContext.Availability.ACTIVE
                && before.sameSceneAs(fresh);
    }

    private boolean isHealingItem(ItemStack stack, boolean potion) {
        if (stack == null || stack.stackSize <= 0) return false;
        if (!potion) return stack.getItem() == Items.mushroom_stew;
        if (stack.getItem() != Items.potionitem || !ItemPotion.isSplash(stack.getMetadata())) return false;
        List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
        if (effects != null) for (PotionEffect effect : effects)
            if (effect != null && effect.getPotionID() == Potion.heal.getId() && effect.getAmplifier() >= 1) return true;
        return false;
    }
    private int findSlot(boolean potion) {
        if (mc.thePlayer != null) for (int slot = 0; slot < 9; slot++)
            if (isHealingItem(mc.thePlayer.inventory.getStackInSlot(slot), potion)) return slot;
        return -1;
    }
    // Retain one shared search contract for both the eligibility and execution paths.
    private int getPotionSlot() { return findSlot(true); }
    private int getSoupSlot() { return findSlot(false); }
    private boolean shouldUsePotion() { return mc.thePlayer != null && mc.thePlayer.getHealth() <= minHealth.getValue().floatValue() && getPotionSlot() >= 0; }
    private boolean shouldUseSoup() { return mc.thePlayer != null && mc.thePlayer.getHealth() <= minHealth.getValue().floatValue() && getSoupSlot() >= 0; }

    private void useHealingItem(boolean potion) {
        bindContext();
        final EntityPlayerSP player = mc.thePlayer;
        final WorldClient world = mc.theWorld;
        final net.minecraft.client.multiplayer.PlayerControllerMP controller = mc.playerController;
        if (transfer.blocked() || using || player == null || world == null || controller == null || player.sendQueue == null || System.nanoTime() < nextUseNanos) return;
        final int slot = potion ? getPotionSlot() : getSoupSlot();
        final int previous = player.inventory.currentItem;
        if (slot < 0 || previous < 0 || previous >= 9) return;
        final long actionGeneration = generation;
        final net.minecraft.client.network.NetHandlerPlayClient connection = player.sendQueue;
        final ItemStack stack = player.inventory.getStackInSlot(slot), snapshot = stack.copy();
        using = true;
        try {
            player.inventory.currentItem = slot;
            if (!controller.syncCurrentPlayItemChecked() || !valid(player, world, controller, actionGeneration, slot, stack, snapshot, potion)) return;
            final C08PacketPlayerBlockPlacement use = new C08PacketPlayerBlockPlacement(stack);
            Packet<?> sent = player.sendQueue.addToSendQueueGuarded(use,
                    () -> valid(player, world, controller, actionGeneration, slot, stack, snapshot, potion) && controller.syncCurrentPlayItemChecked());
            // A packet accepted locally is a use attempt, not proof of server healing.
            if (sent == use && generation == actionGeneration && mc.thePlayer == player && mc.theWorld == world)
                nextUseNanos = System.nanoTime() + 1_000_000_000L;
        } finally {
            if (mc.thePlayer == player && mc.theWorld == world && player.sendQueue == connection &&
                    mc.playerController == controller && player.inventory.currentItem == slot && previous != slot) {
                player.inventory.currentItem = previous;
                controller.syncCurrentPlayItemChecked();
            }
            using = false;
        }
    }
    private boolean valid(EntityPlayerSP player, WorldClient world, net.minecraft.client.multiplayer.PlayerControllerMP controller,
                          long actionGeneration, int slot, ItemStack stack, ItemStack snapshot, boolean potion) {
        return mc.inGameHasFocus && player.isEntityAlive() && !player.isSpectator() && !player.isUsingItem() && player.inventory.getItemStack() == null && mc.currentScreen == null &&
                player.sendQueue == contextConnection && generation == actionGeneration && mc.thePlayer == player && mc.theWorld == world && mc.playerController == controller &&
                player.inventory.currentItem == slot && player.inventory.getStackInSlot(slot) == stack &&
                ItemStack.areItemStacksEqual(snapshot, stack) && isHealingItem(stack, potion);
    }
}
