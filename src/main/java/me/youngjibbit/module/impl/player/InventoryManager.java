package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.core.activity.InventoryActivitySnapshot;
import me.youngjibbit.core.activity.InventoryActivitySnapshot.Action;
import me.youngjibbit.event.Event;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.ghost.AutoBridger;
import me.youngjibbit.core.inventory.BlockStackPlanner;
import me.youngjibbit.core.inventory.InventoryPlanner;
import me.youngjibbit.core.inventory.InventoryItemClassifier;
import me.youngjibbit.core.inventory.TrashItemStore;
import me.youngjibbit.core.inventory.TrashItemCatalog;
import me.youngjibbit.utils.TimerUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.inventory.Container;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.util.DamageSource;


import java.util.*;
import java.util.function.BooleanSupplier;
import java.lang.ref.WeakReference;

@ModuleInfo(name = "InvManager", category = Category.Player)
public class InventoryManager extends Module
{


    private EntityPlayer player;
    private PlayerControllerMP playerController;

    private InventoryActivitySnapshot activitySnapshot;
    private ActivityInventoryContext activityContext;
    private final java.util.function.LongSupplier activityClock = System::nanoTime;
    private long activitySequence, activityRevision;
    private int activityProcessed;

    /** Cached display facts only; validates at most the two affected stacks, never reruns a scan. */
    public InventoryActivitySnapshot getActivitySnapshot() {
        InventoryActivitySnapshot cached = activitySnapshot;
        ActivityInventoryContext context = activityContext;
        long now = activityClock.getAsLong();
        if (cached == null || cached != activitySnapshot || mc == null || !isToggled()
                || !(mc.currentScreen instanceof GuiInventory) || !inventoryScreenMatches(mc.thePlayer) || !cached.isFresh(now)
                || context == null || !context.isCurrent(mc)) {
            if (cached != null && cached == activitySnapshot) clearActivity();
            return null;
        }
        return cached == activitySnapshot ? cached : null;
    }

    private void clearActivity() {
        activityRevision++;
        activitySnapshot = null;
        activityContext = null;
        activityProcessed = 0;
    }

    private void publishActivity(Action action, boolean actualAction, int... affectedSlots) {
        final long generation = inventoryGeneration, revision = activityRevision;
        long now = activityClock.getAsLong();
        if (mc == null || !isToggled() || !(mc.currentScreen instanceof GuiInventory) || !inventoryScreenMatches(mc.thePlayer)
                || generation != inventoryGeneration || revision != activityRevision) return;
        ActivityInventoryContext captured = ActivityInventoryContext.capture(mc, affectedSlots);
        if (captured == null || generation != inventoryGeneration || revision != activityRevision || !isToggled()) return;
        InventoryActivitySnapshot previous = activitySnapshot;
        if (actualAction || previous == null || !previous.hasSameFacts(action, activityProcessed, -1))
            activitySequence++;
        activityContext = captured;
        activitySnapshot = new InventoryActivitySnapshot(InventoryActivitySnapshot.Producer.INVENTORY_MANAGER,
                action, activityProcessed, -1, activitySequence, now);
    }

    private void observeClick(BooleanSupplier sameScene, long generation, long revision, Action action,
                              ItemStack expected, int sourceSlot, int... affectedSlots) {
        if (generation != inventoryGeneration || revision != activityRevision || !isToggled() || !sameScene.getAsBoolean()) return;
        if (mc.thePlayer.openContainer.inventorySlots == null || sourceSlot < 0
                || sourceSlot >= mc.thePlayer.openContainer.inventorySlots.size()) { clearActivity(); return; }
        ItemStack after = mc.thePlayer.openContainer.getSlot(sourceSlot).getStack();
        if (generation != inventoryGeneration || revision != activityRevision || !sameScene.getAsBoolean()) return;
        boolean changed = !ItemStack.areItemStacksEqual(expected, after);
        if (changed && activityProcessed < Integer.MAX_VALUE) activityProcessed++;
        publishActivity(changed ? action : Action.SCANNING, changed, affectedSlots);
    }

    @Subscribe private final Listener<me.youngjibbit.event.world.WorldEvent> activityWorld =
            new Listener<>(event -> invalidateInventoryPlan());
    @Subscribe private final Listener<me.youngjibbit.event.packet.EventPacket> activityPackets =
            new Listener<>(event -> {
                if (event.isPost() && event.isInbound()
                        && (event.getPacket() instanceof net.minecraft.network.play.server.S2FPacketSetSlot
                        || event.getPacket() instanceof net.minecraft.network.play.server.S30PacketWindowItems
                        || event.getPacket() instanceof net.minecraft.network.play.server.S2DPacketOpenWindow
                        || event.getPacket() instanceof net.minecraft.network.play.server.S2EPacketCloseWindow))
                    clearActivity();
            });

    private boolean changingSettings;
    private boolean invOpen;


    private static int bestSwordSlot, bestPickaxeSlot, bestBowSlot, bestBlockSlot, bestGapSlot;
    private static int[] bestArmorDamageReducment, bestArmorSlot;

    //Lists
    private final List<Integer> allSwords = new ArrayList<>();
    private final List<Integer> allBows = new ArrayList<>();
    private final List<Integer> allPickaxes = new ArrayList<>();
    private final List<Integer>[] allArmors = new List[4];
    private final List<Integer> allBlocks = new ArrayList<>();
    private final List<Integer> trash = new ArrayList<>();
    private final Map<Integer, ItemStack> trashSnapshots = new HashMap<>();
    private final Map<Integer, ItemStack> trashIdentities = new HashMap<>();
    private EntityPlayer trashOwner;
    private Object trashWorld;
    private Object trashScreen;
    private Container trashContainer;
    private PlayerControllerMP trashController;
    private int trashWindowId;
    private boolean inventoryClickInProgress;
    private int lastAttemptTick = Integer.MIN_VALUE;
    private WeakReference<EntityPlayer> lastAttemptPlayer = new WeakReference<>(null);
    private WeakReference<Object> lastAttemptWorld = new WeakReference<>(null);
    private ActivityInventoryContext startDelayContext;
    private long inventoryGeneration;
    private long trashGeneration = -1;
    private final TimerUtil timer = new TimerUtil();
    private Integer delay = null;
    private boolean movedItem;
    private boolean inventoryOpen;
    private int ticksSinceChest;


    // settings

    public SliderValue minDelay = new SliderValue("Min Delay", this,10.0, 0, 100, true);
    public SliderValue maxDelay = new SliderValue("Max Delay", this,20.0, 0, 100, true);
    public SliderValue startDelay = new SliderValue("Start Delay", this,20.0, 0, 500, true);
    public SliderValue maxBlocks = new SliderValue("Maximum Block Stacks", this,2.0, 0, 8, true);
    public CheckBoxValue sort = new CheckBoxValue("Sort", this, true);
    public CheckBoxValue autoArmor = new CheckBoxValue("Auto Armor", this, true);
    public CheckBoxValue random = new CheckBoxValue("Random", this, true);
    public CheckBoxValue openInv = new CheckBoxValue("Open Inv", this, true);
    public CheckBoxValue keepArmor = new CheckBoxValue("Keep Armor", this,false);
    public CheckBoxValue onlyStill = new CheckBoxValue("Only When Standing Still", this,false);
    // Appended: legacy setting indexes remain stable.
    public final ComboValue layout = new ComboValue("Layout", this, "Organized", new String[]{"Organized", "Custom", "Legacy"});
    public final CheckBoxValue keepOnePerType = new CheckBoxValue("Keep One Per Type", this, true);
    public final ComboValue vacantSlots = new ComboValue("Vacant Slots", this, "Utility Fallback", new String[]{"Utility Fallback", "Reserve", "Leave"});
    public final ComboValue[] hotbarRoles = new ComboValue[9];
    private TrashItemStore trashStore;
    //Timers
    private final TimerUtil delayTimer = new TimerUtil();
    private final TimerUtil startDelayTimer = new TimerUtil();

    public InventoryManager()
    {
        this(null);
    }

    public InventoryManager(TrashItemStore store) {
        trashStore = store;
        super.setKey(Keyboard.KEY_V);
        InventoryPlanner.Role[] roles = InventoryPlanner.organizedRoles();
        for (int i = 0; i < hotbarRoles.length; i++) {
            hotbarRoles[i] = new ComboValue("Slot " + (i + 1) + " Role", this, roles[i].label, InventoryPlanner.Role.labels());
            hotbarRoles[i].setVisibility(m -> "Custom".equals(layout.getValue()));
        }
        keepOnePerType.setVisibility(m -> !legacyLayout());
        vacantSlots.setVisibility(m -> !legacyLayout());
        random.setVisibility(m -> legacyLayout());
        openInv.setVisibility(m -> legacyLayout());
        enableSmartDisable();
    }

    public TrashItemStore getTrashStore() {
        if (trashStore == null) trashStore = mc == null || mc.mcDataDir == null ? TrashItemStore.memory()
                : new TrashItemStore(mc.mcDataDir.toPath().resolve("pulsar").resolve("inventory-trash.json"));
        return trashStore;
    }
    public TrashItemCatalog getTrashCatalog() { return new TrashItemCatalog(); }
    public void openTrashMenu(net.minecraft.client.gui.GuiScreen parent) {
        mc.displayGuiScreen(new me.youngjibbit.gui.inventory.TrashMenuScreen(parent, this));
    }
    public void trashRulesChanged() { invalidateInventoryPlan(); }
    private boolean legacyLayout() { return "Legacy".equals(layout.getValue()); }
    private boolean cursorFree(EntityPlayer owner) {
        return owner != null && owner.inventory != null && owner.inventory.getItemStack() == null &&
                !owner.isDead && owner.isEntityAlive() && !mouseBusy();
    }
    private static boolean mouseBusy() {
        return Mouse.isCreated() && (Mouse.isButtonDown(0) || Mouse.isButtonDown(1));
    }
    private boolean attemptedThisTick() {
        return mc != null && mc.thePlayer != null && lastAttemptPlayer.get() == mc.thePlayer &&
                lastAttemptWorld.get() == mc.theWorld && lastAttemptTick == mc.thePlayer.ticksExisted;
    }
    /** Shared click budget: rejection or controller failure cannot authorize a second attempt this tick. */
    private boolean claimInventoryAttempt(EntityPlayer owner, Object world) {
        if (mc.thePlayer != owner || mc.theWorld != world || attemptedThisTick() || !cursorFree(owner)) return false;
        lastAttemptTick = owner.ticksExisted;
        lastAttemptPlayer = new WeakReference<>(owner);
        lastAttemptWorld = new WeakReference<>(world);
        delayTimer.reset();
        return true;
    }
    private boolean inventoryScreenMatches(EntityPlayer owner) {
        return legacyLayout() || owner != null && mc.currentScreen instanceof GuiInventory &&
                ((GuiInventory)mc.currentScreen).inventorySlots == owner.inventoryContainer;
    }

    private InventoryPlanner.Plan organizedPlan() {
        List<InventoryPlanner.Stack> snapshots = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            InventoryPlanner.Stack snapshot = InventoryItemClassifier.describe(slot, stack,
                    getTrashStore().contains(TrashItemStore.keyFor(stack)));
            if (snapshot != null) snapshots.add(snapshot);
        }
        InventoryPlanner.Role[] roles = InventoryPlanner.organizedRoles();
        if ("Custom".equals(layout.getValue())) for (int i = 0; i < roles.length; i++) roles[i] = InventoryPlanner.Role.named(hotbarRoles[i].getValue());
        InventoryPlanner.Vacant vacant = "Reserve".equals(vacantSlots.getValue()) ? InventoryPlanner.Vacant.RESERVE
                : "Leave".equals(vacantSlots.getValue()) ? InventoryPlanner.Vacant.LEAVE : InventoryPlanner.Vacant.UTILITY;
        return InventoryPlanner.plan(snapshots, roles, (int)Math.round(maxBlocks.getValue()), keepOnePerType.getValue(), vacant);
    }

    @Override
    public void onEnable()
    {
        invalidateInventoryPlan();
        super.onEnable();
        if (mc.currentScreen instanceof GuiChest)
        {
            onUpdate();
        } else {

        }
    }

    @Override
    public void onDisable() {
        invalidateInventoryPlan();
        super.onDisable();
    }

    @Override
    public void onValueChange() {
        invalidateInventoryPlan();
        super.onValueChange();
    }

    private void invalidateInventoryPlan() {
        clearActivity();
        startDelayContext = null;
        inventoryGeneration++;
        trashGeneration = -1;
        trash.clear();
        trashSnapshots.clear();
        trashIdentities.clear();
        trashOwner = null;
        trashWorld = null;
        trashScreen = null;
        trashContainer = null;
        trashController = null;
        trashWindowId = -1;
        invOpen = false;
        // A lifecycle callback must not unlock an action still on the call stack.
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e->
    {
        if (inventoryClickInProgress) return;
        if (activityContext != null && !activityContext.isCurrent(mc)) clearActivity();
        if (!isToggled() || !(mc.currentScreen instanceof GuiInventory)) { clearActivity(); startDelayContext = null; }
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || !cursorFree(mc.thePlayer)) {
            invalidateInventoryPlan();
            return;
        }
        if (trashOwner != null && !hasTrashAuthority()) invalidateInventoryPlan();
            if (this.isToggled() && mc.currentScreen instanceof GuiInventory)
        {
            final EntityPlayer updateOwner = mc.thePlayer;
            final Object updateWorld = mc.theWorld;
            final Object updateScreen = mc.currentScreen;
            final PlayerControllerMP updateController = mc.playerController;
            final Container updateContainer = updateOwner.inventoryContainer;
            final long updateGeneration = inventoryGeneration;
            closeInvServerSide();
            if (!isToggled() || inventoryGeneration != updateGeneration || mc.thePlayer != updateOwner ||
                    mc.theWorld != updateWorld || mc.playerController != updateController ||
                    mc.currentScreen != updateScreen || updateContainer == null ||
                    updateOwner.inventoryContainer != updateContainer || updateOwner.openContainer != updateContainer || !inventoryScreenMatches(updateOwner)) {
                invalidateInventoryPlan();
                return;
            }

            Module autoBridger = Pulsar.INSTANCE.getMm().getModule(AutoBridger.class);
            if ((autoBridger != null && autoBridger.isToggled()) || mc.currentScreen instanceof GuiChest) {
                clearActivity(); return;
            }

            if (onlyStill.getValue() && (mc.gameSettings.keyBindJump.isKeyDown() || mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindBack.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown())) {
                clearActivity(); return;
            }
            if (startDelayContext == null || !startDelayContext.sameScene(mc)) {
                startDelayContext = ActivityInventoryContext.capture(mc);
                startDelayTimer.reset();
                if (startDelayContext == null) return;
            }
            if (attemptedThisTick()) return;

            searchForItems();
            searchForBestArmor();
            searchForTrash();

            // One attempted action per client tick. Every later action gets a fresh scan next tick,
            // including after a rejected action or an inventory-open lifecycle callback.
            if (!trash.isEmpty()) {
                publishActivity(Action.SCANNING, false, wireSlot(trash.get(0)));
                if (hasNoDelay()) return;
                try { dropTrashSlot(trash.get(0)); }
                catch (IndexOutOfBoundsException ignored) { }
                delayTimer.reset();
                return;
            }

            if (autoArmor.getValue()) {
                for (int i = 0; i < 4; i++) {
                    final int armorIndex = i;
                    final int bestSlot = bestArmorSlot[i];
                    if (bestSlot < 0) continue;
                    final ItemStack oldArmor = mc.thePlayer.inventory.armorItemInSlot(i);
                    // Shift-click cannot replace occupied armor while keeping that armor.
                    if (oldArmor != null && (keepArmor.getValue() || InventoryItemClassifier.protectedItem(oldArmor))) continue;
                    publishActivity(Action.SCANNING, false, wireSlot(bestSlot), 8 - i);
                    if (hasNoDelay()) return;
                    final BooleanSupplier stillBestArmor = () -> {
                        searchForBestArmor();
                        return bestArmorSlot[armorIndex] == bestSlot;
                    };
                    if (oldArmor != null) {
                        clickInventorySlot(8 - i, 0, 4, wireSlot(bestSlot), stillBestArmor);
                    } else {
                        clickInventorySlot(wireSlot(bestSlot), 0, 1, 8 - i, stillBestArmor);
                    }
                    delayTimer.reset();
                    return;
                }
            }

            if (sort.getValue()) {
                if (!legacyLayout()) {
                    final InventoryPlanner.Move move = organizedPlan().nextMove;
                    if (move != null) {
                        publishActivity(Action.SCANNING, false, wireSlot(move.source), wireSlot(move.hotbar));
                        if (hasNoDelay()) return;
                        clickInventorySlot(wireSlot(move.source), move.hotbar, 2, wireSlot(move.hotbar),
                                () -> move.equals(organizedPlan().nextMove));
                        delayTimer.reset();
                        return;
                    }
                    publishActivity(Action.COMPLETE, false);
                    return;
                }
                if (bestSwordSlot >= 0 && bestSwordSlot != 0) {
                    publishActivity(Action.SCANNING, false, wireSlot(bestSwordSlot), 36);
                    if (hasNoDelay()) return;
                    final int source = bestSwordSlot;
                    clickInventorySlot(wireSlot(source), 0, 2, 36, () -> {
                        searchForItems(); return bestSwordSlot == source;
                    });
                    delayTimer.reset();
                    return;
                }
                if (bestBowSlot >= 0 && bestBowSlot != 1) {
                    publishActivity(Action.SCANNING, false, wireSlot(bestBowSlot), 37);
                    if (hasNoDelay()) return;
                    final int source = bestBowSlot;
                    clickInventorySlot(wireSlot(source), 1, 2, 37, () -> {
                        searchForItems(); return bestBowSlot == source;
                    });
                    delayTimer.reset();
                    return;
                }
                if (bestPickaxeSlot >= 0 && bestPickaxeSlot != 2) {
                    publishActivity(Action.SCANNING, false, wireSlot(bestPickaxeSlot), 38);
                    if (hasNoDelay()) return;
                    final int source = bestPickaxeSlot;
                    clickInventorySlot(wireSlot(source), 2, 2, 38, () -> {
                        searchForItems(); return bestPickaxeSlot == source;
                    });
                    delayTimer.reset();
                    return;
                }
                if (bestBlockSlot >= 0 && bestBlockSlot != 8) {
                    publishActivity(Action.SCANNING, false, wireSlot(bestBlockSlot), 44);
                    if (hasNoDelay()) return;
                    moveBestBlockStack(8);
                    delayTimer.reset();
                    return;
                }
                if (bestGapSlot >= 0 && bestGapSlot != 7) {
                    publishActivity(Action.SCANNING, false, wireSlot(bestGapSlot), 43);
                    if (hasNoDelay()) return;
                    final int source = bestGapSlot;
                    clickInventorySlot(wireSlot(source), 7, 2, 43, () -> {
                        searchForItems(); return bestGapSlot == source;
                    });
                    delayTimer.reset();
                    return;
                }
            }
            publishActivity(Action.COMPLETE, false);
        }
    });




    private static int wireSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    /** All legacy armor/tool swaps keep their ranking, with the same final click authority. */
    private boolean clickInventorySlot(int slot, int button, int mode, int companionSlot,
                                       BooleanSupplier stillEligible) {
        if (inventoryClickInProgress || attemptedThisTick() || mc.thePlayer == null || mc.theWorld == null ||
                mc.playerController == null || !cursorFree(mc.thePlayer) || !inventoryScreenMatches(mc.thePlayer)) return false;
        final EntityPlayer owner = mc.thePlayer;
        final Object world = mc.theWorld;
        final Object screen = mc.currentScreen;
        final PlayerControllerMP controller = mc.playerController;
        final Container container = owner.inventoryContainer;
        final long generation = inventoryGeneration;
        if (container == null || owner.openContainer != container || slot < 0 || companionSlot < 0 ||
                slot >= container.inventorySlots.size() || companionSlot >= container.inventorySlots.size()) return false;
        final int windowId = container.windowId;
        final ItemStack sourceIdentity = container.getSlot(slot).getStack();
        final ItemStack companionIdentity = container.getSlot(companionSlot).getStack();
        if (sourceIdentity == null && (mode != 2 || companionIdentity == null)) return false;
        final ItemStack sourceExpected = sourceIdentity == null ? null : sourceIdentity.copy();
        final ItemStack companionExpected = companionIdentity == null ? null : companionIdentity.copy();
        final BooleanSupplier current = () -> generation == inventoryGeneration && mc.thePlayer == owner &&
                mc.theWorld == world && mc.currentScreen == screen && mc.playerController == controller && owner.inventoryContainer == container &&
                owner.openContainer == container && container.windowId == windowId && cursorFree(owner) && inventoryScreenMatches(owner) &&
                container.getSlot(slot).getStack() == sourceIdentity &&
                container.getSlot(companionSlot).getStack() == companionIdentity &&
                ItemStack.areItemStacksEqual(sourceExpected, sourceIdentity) &&
                ItemStack.areItemStacksEqual(companionExpected, companionIdentity);
        inventoryClickInProgress = true;
        try {
            openInvServerSide();
            if (!current.getAsBoolean() || !stillEligible.getAsBoolean() || !current.getAsBoolean()) {
                invalidateInventoryPlan();
                return false;
            }
            final long activityBefore = activityRevision;
            if (!claimInventoryAttempt(owner, world)) return false;
            controller.windowClick(windowId, slot, button, mode, owner);
            observeClick(() -> mc.thePlayer == owner && mc.theWorld == world && mc.currentScreen == screen
                            && mc.playerController == controller && owner.inventoryContainer == container
                            && owner.openContainer == container && container.windowId == windowId,
                    generation, activityBefore, mode == 4 ? Action.DROPPING : mode == 1 ? Action.EQUIPPING : Action.SORTING,
                    sourceExpected, slot, slot, companionSlot);
            return true;
        } finally {
            inventoryClickInProgress = false;
        }
    }

    private void openInvServerSide() {
        if (!legacyLayout()) return;
        if (!invOpen && !openInv.getValue()) {
            if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
                final net.minecraft.client.entity.EntityPlayerSP owner = mc.thePlayer;
                final Object world = mc.theWorld;
                final long generation = inventoryGeneration;
                owner.sendQueue.addToSendQueue(new C0BPacketEntityAction(owner, C0BPacketEntityAction.Action.OPEN_INVENTORY));
                if (generation == inventoryGeneration && mc.thePlayer == owner && mc.theWorld == world)
                    invOpen = true;
            }
        }
    }

    private void closeInvServerSide() {
        if (!legacyLayout()) return;
        if (invOpen && !openInv.getValue()) {
            invOpen = false;
            if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
                mc.thePlayer.sendQueue.addToSendQueue(new C0DPacketCloseWindow(0));
            }
        }
    }

    private boolean hasNoDelay() {
        return !startDelayTimer.hasTimeElapsed((double) (startDelay.getValue() * 10 + Math.random() * 2), false) || !delayTimer.hasTimeElapsed(Double.valueOf((double) (Math.random() * (maxDelay.getValue() * 10 - minDelay.getValue() * 10 + 1) + minDelay.getValue() * 10)), false) || (!(mc.currentScreen instanceof GuiInventory)) && openInv.getValue();
    }

    private void searchForTrash() {
        // Bind destructive actions to the exact scan context and stack identities.
        trash.clear();
        trashSnapshots.clear();
        trashIdentities.clear();
        trashGeneration = inventoryGeneration;
        trashOwner = mc.thePlayer;
        trashWorld = mc.theWorld;
        trashScreen = mc.currentScreen;
        trashContainer = trashOwner == null ? null : trashOwner.inventoryContainer;
        trashController = mc.playerController;
        trashWindowId = trashContainer == null ? -1 : trashContainer.windowId;
        if (trashOwner == null) return;

        // Keep Armor controls armor retention, not every unrelated trash rule.
        for (int i = 0; !keepArmor.getValue() && i < allArmors.length; i++) {
            final List<Integer> armorItem = allArmors[i];
            if (armorItem != null) {
                final int finalI = i;
                armorItem.stream().filter(slot -> slot != bestArmorSlot[finalI]).forEach(trash::add);
            }
        }

        if (legacyLayout()) {
            // Preserve old ranking and weakest-first block overflow for imported configs.
            allBows.stream().filter(slot -> slot != bestBowSlot).forEach(trash::add);
            allSwords.stream().filter(slot -> slot != bestSwordSlot).forEach(trash::add);
            allPickaxes.stream().filter(slot -> slot != bestPickaxeSlot).forEach(trash::add);
            Collections.sort(trash);
            for (final BlockStackPlanner.Snapshot overflow :
                    BlockStackPlanner.plan(blockSnapshots(), (int)Math.round(maxBlocks.getValue())).trashBlockStacks)
                trash.add(overflow.slot);
        } else {
            trash.addAll(organizedPlan().discard);
        }

        // Explicit variant rules also work with Legacy. Named/custom stacks are
        // protected even when their ordinary vanilla item variant is blacklisted.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = trashOwner.inventory.getStackInSlot(slot);
            if (stack != null && getTrashStore().contains(TrashItemStore.keyFor(stack))) trash.add(slot);
        }
        LinkedHashSet<Integer> safe = new LinkedHashSet<>();
        for (int slot : trash) {
            ItemStack stack = trashOwner.inventory.getStackInSlot(slot);
            if (stack != null && !InventoryItemClassifier.protectedItem(stack)) safe.add(slot);
        }
        trash.clear();
        trash.addAll(safe);

        // Randomness may only reorder the already finalized safe candidate set.
        if (legacyLayout() && random.getValue()) Collections.shuffle(trash);

        for (int slot : trash) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null) {
                trashSnapshots.put(slot, stack.copy());
                trashIdentities.put(slot, stack);
            }
        }
    }

    private boolean hasTrashAuthority() {
        return trashGeneration == inventoryGeneration && trashOwner != null &&
                trashOwner == mc.thePlayer && trashWorld != null &&
                trashWorld == mc.theWorld && trashScreen == mc.currentScreen && trashController != null && trashController == mc.playerController &&
                trashContainer != null && trashOwner.inventoryContainer == trashContainer &&
                trashOwner.openContainer == trashContainer && trashContainer.windowId == trashWindowId && cursorFree(trashOwner) && inventoryScreenMatches(trashOwner);
    }

    /** Recheck after inventory-open packet listeners, immediately before the destructive click. */
    private boolean dropTrashSlot(int slot) {
        if (inventoryClickInProgress || attemptedThisTick() || slot < 0 || slot >= 36 || !hasTrashAuthority()) return false;
        final long generation = inventoryGeneration;
        final EntityPlayer owner = trashOwner;
        final Object world = trashWorld;
        final Object screen = trashScreen;
        final Container container = trashContainer;
        final PlayerControllerMP controller = trashController;
        final int windowId = trashWindowId;
        final ItemStack expected = trashSnapshots.get(slot);
        final ItemStack identity = trashIdentities.get(slot);
        if (expected == null || identity == null || owner.inventory.getStackInSlot(slot) != identity ||
                !ItemStack.areItemStacksEqual(expected, identity)) return false;
        inventoryClickInProgress = true;
        try {
            openInvServerSide();
            if (generation != inventoryGeneration || !hasTrashAuthority() || mc.thePlayer != owner || mc.theWorld != world || mc.currentScreen != screen ||
                    mc.playerController != controller || owner.openContainer != container ||
                    container.windowId != windowId || owner.inventory.getStackInSlot(slot) != identity ||
                    !ItemStack.areItemStacksEqual(expected, identity)) return false;
            // A competitor can disappear or improve while the planned stack stays unchanged.
            searchForItems();
            searchForBestArmor();
            searchForTrash();
            if (generation != inventoryGeneration || !hasTrashAuthority() || trashOwner != owner || trashWorld != world || trashScreen != screen ||
                    trashContainer != container || trashController != controller || trashWindowId != windowId ||
                    !trash.contains(slot) || owner.inventory.getStackInSlot(slot) != identity ||
                    !ItemStack.areItemStacksEqual(expected, identity)) return false;
            final long activityBefore = activityRevision;
            if (!claimInventoryAttempt(owner, world)) return false;
            controller.windowClick(windowId, slot < 9 ? slot + 36 : slot, 1, 4, owner);
            observeClick(() -> mc.thePlayer == owner && mc.theWorld == world && mc.currentScreen == screen
                            && mc.playerController == controller && owner.inventoryContainer == container
                            && owner.openContainer == container && container.windowId == windowId,
                    generation, activityBefore, Action.DROPPING, expected, wireSlot(slot), wireSlot(slot));
            return true;
        } finally {
            inventoryClickInProgress = false;
        }
    }

    private boolean moveBestBlockStack(int targetSlot) {
        if (inventoryClickInProgress || attemptedThisTick() || targetSlot < 0 || targetSlot >= 9 || mc.thePlayer == null ||
                mc.theWorld == null || mc.playerController == null || !cursorFree(mc.thePlayer)) return false;
        final long generation = inventoryGeneration;
        final EntityPlayer owner = mc.thePlayer;
        final Object world = mc.theWorld;
        final Object screen = mc.currentScreen;
        final Container container = owner.inventoryContainer;
        final PlayerControllerMP controller = mc.playerController;
        if (container == null || owner.openContainer != container) return false;
        final int windowId = container.windowId;
        searchForItems();
        final int sourceSlot = bestBlockSlot;
        if (sourceSlot < 0 || sourceSlot == targetSlot) return false;
        final ItemStack identity = owner.inventory.getStackInSlot(sourceSlot);
        final ItemStack expected = identity.copy();
        final ItemStack targetIdentity = owner.inventory.getStackInSlot(targetSlot);
        final ItemStack targetExpected = targetIdentity == null ? null : targetIdentity.copy();
        inventoryClickInProgress = true;
        try {
            openInvServerSide();
            if (generation != inventoryGeneration || mc.thePlayer != owner || mc.theWorld != world || mc.currentScreen != screen || mc.playerController != controller ||
                    owner.inventoryContainer != container || owner.openContainer != container ||
                    container.windowId != windowId || !cursorFree(owner) || owner.inventory.getStackInSlot(sourceSlot) != identity ||
                    owner.inventory.getStackInSlot(targetSlot) != targetIdentity ||
                    !ItemStack.areItemStacksEqual(expected, identity) ||
                    !ItemStack.areItemStacksEqual(targetExpected, targetIdentity)) return false;
            searchForItems();
            if (generation != inventoryGeneration || bestBlockSlot != sourceSlot) return false;
            final long activityBefore = activityRevision;
            if (!claimInventoryAttempt(owner, world)) return false;
            controller.windowClick(windowId, sourceSlot < 9 ? sourceSlot + 36 : sourceSlot, targetSlot, 2, owner);
            observeClick(() -> mc.thePlayer == owner && mc.theWorld == world && mc.currentScreen == screen
                            && mc.playerController == controller && owner.inventoryContainer == container
                            && owner.openContainer == container && container.windowId == windowId,
                    generation, activityBefore, Action.SORTING, expected, wireSlot(sourceSlot),
                    wireSlot(sourceSlot), wireSlot(targetSlot));
            return true;
        } finally {
            inventoryClickInProgress = false;
        }
    }

    private void searchForBestArmor() {
        //Reset all Stats and Slots
        bestArmorDamageReducment = new int[4];
        bestArmorSlot = new int[4];

        //Fill Arrays
        Arrays.fill(bestArmorDamageReducment, -1);
        Arrays.fill(bestArmorSlot, -1);

        //Fill Array with current Armor
        for (int i = 0; i < bestArmorSlot.length; i++) {
            final ItemStack itemStack = mc.thePlayer.inventory.armorItemInSlot(i);
            allArmors[i] = new ArrayList<>();

            if (itemStack != null && itemStack.getItem() != null) {
                if (itemStack.getItem() instanceof ItemArmor) {
                    final ItemArmor armor = (ItemArmor) itemStack.getItem();
                    final int currentProtection = armor.damageReduceAmount + EnchantmentHelper.getEnchantmentModifierDamage(new ItemStack[]{itemStack}, DamageSource.generic); //Get the Reduce amount of the ArmorType and Protection Level
                    bestArmorDamageReducment[i] = currentProtection;
                }
            }
        }

        //Search for better Armor in Inventory
        for (int i = 0; i < 9 * 4; i++) {
            final ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack == null || itemStack.getItem() == null) continue;

            if (itemStack.getItem() instanceof ItemArmor) {
                final ItemArmor armor = (ItemArmor) itemStack.getItem();

                final int armorType = 3 - armor.armorType; //Slots reversed for some reason
                allArmors[armorType].add(i);
                if (getTrashStore().contains(TrashItemStore.keyFor(itemStack))) continue;
                final int slotProtectionLevel = armor.damageReduceAmount + EnchantmentHelper.getEnchantmentModifierDamage(new ItemStack[]{itemStack}, DamageSource.generic); //Get the Reduce amount of the ArmorType and Protection Level

                if (bestArmorDamageReducment[armorType] < slotProtectionLevel) {
                    bestArmorDamageReducment[armorType] = slotProtectionLevel;
                    bestArmorSlot[armorType] = i;
                }

            }
        }
    }


    private void searchForItems() {
        //Reset all Slots
        bestSwordSlot = -1;
        bestBowSlot = -1;
        bestPickaxeSlot = -1;
        bestBlockSlot = -1;
        bestGapSlot = -1;

        allSwords.clear();
        allBows.clear();
        allPickaxes.clear();
        allBlocks.clear();

        //Other Stats
        float bestSwordDamage = -1, bestSwordDurability = -1, bestPickaxeEfficiency = -1, bestPickaxeDurability = -1, bestBowDurability = -1;
        int bestBowDamage = -1;
        int gapStackSize = -1;

        for (int i = 0; i < 9 * 4; i++) {
            final ItemStack itemStack = mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack == null || itemStack.getItem() == null) continue;

            //Search for best Sword
            if (getTrashStore().contains(TrashItemStore.keyFor(itemStack))) continue;
            if (itemStack.getItem() instanceof ItemSword) {
                final ItemSword sword = (ItemSword) itemStack.getItem();
                final int level = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack);
                final float damageLevel = (float) (sword.getDamageVsEntity() + level * 1.25); //Enchantment Multiplier

                allSwords.add(i);

                if (bestSwordDamage < damageLevel) {
                    bestSwordDamage = damageLevel;
                    bestSwordDurability = sword.getDamageVsEntity();
                    bestSwordSlot = i;
                }

                if ((damageLevel == bestSwordDamage) && (sword.getDamageVsEntity() < bestSwordDurability)) {
                    bestSwordDurability = sword.getDamageVsEntity();
                    bestSwordSlot = i;
                }
            }

            //Search for best Pickaxe
            if (itemStack.getItem() instanceof ItemPickaxe) {
                final ItemPickaxe pickaxe = (ItemPickaxe) itemStack.getItem();
                allPickaxes.add(i);

                final float efficiencyLevel = getPickaxeEfficiency(itemStack);

                final int remaining = itemStack.getMaxDamage() - itemStack.getItemDamage();
                if (bestPickaxeEfficiency < efficiencyLevel ||
                        (efficiencyLevel == bestPickaxeEfficiency && remaining > bestPickaxeDurability)) {
                    bestPickaxeEfficiency = efficiencyLevel;
                    bestPickaxeDurability = remaining;
                    bestPickaxeSlot = i;
                }

            }

            //Search for best Bow
            if (itemStack.getItem() instanceof ItemBow) {
                final ItemBow bow = (ItemBow) itemStack.getItem();
                final int level = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemStack);
                allBows.add(i);

                if (bestBowDamage < level || (bestBowDamage == -1 && level == 0)) {
                    bestBowDamage = level;
                    bestBowDurability = bow.getMaxDamage();
                    bestBowSlot = i;
                }

                if ((level == bestBowDamage) && bow.getMaxDamage() < bestBowDurability) {
                    bestBowDurability = bow.getMaxDamage();
                    bestBowSlot = i;
                }
            }

            // Keep the existing block eligibility; the shared planner owns ranking.
            if (blockKind(itemStack) != null) allBlocks.add(i);

            // Search for Gaps.
            if (itemStack.getItem() instanceof ItemAppleGold) {
                if (gapStackSize < itemStack.stackSize) {
                    gapStackSize = itemStack.stackSize;
                    bestGapSlot = i;
                }
            }
        }
        final BlockStackPlanner.Snapshot best = BlockStackPlanner.plan(blockSnapshots(),
                (int) Math.round(maxBlocks.getValue())).bestBuildStack;
        bestBlockSlot = best == null ? -1 : best.slot;
    }

    private List<BlockStackPlanner.Snapshot> blockSnapshots() {
        final List<BlockStackPlanner.Snapshot> stacks = new ArrayList<>();
        for (int slot : allBlocks) {
            final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            final BlockStackPlanner.Kind kind = blockKind(stack);
            if (kind != null) stacks.add(new BlockStackPlanner.Snapshot(slot, stack.stackSize, kind));
        }
        return stacks;
    }

    private static BlockStackPlanner.Kind blockKind(ItemStack stack) {
        return InventoryItemClassifier.blockKind(stack);
    }

    private float getPickaxeEfficiency(final ItemStack stack) {
        ItemPickaxe pickaxe = (ItemPickaxe) stack.getItem();
        int level = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);

        //Percentage of Efficiency per Level
        switch (level) {
            case 1:
                level = 30;
                break;
            case 2:
                level = 69;
                break;
            case 3:
                level = 120;
                break;
            case 4:
                level = 186;
                break;
            case 5:
                level = 271;
                break;

            default:
                level = 0;
                break;
        }

        return pickaxe.getToolMaterial().getEfficiencyOnProperMaterial() + level;
    }

}
