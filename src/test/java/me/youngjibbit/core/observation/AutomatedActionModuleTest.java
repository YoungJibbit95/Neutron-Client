package me.youngjibbit.core.observation;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.ghost.AutoClicker2;
import me.youngjibbit.module.impl.ghost.HitDelayFix;
import me.youngjibbit.utils.TimerUtil;
import me.youngjibbit.utils.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import me.zero.alpine.event.EventPhase;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Real listeners, observer body scopes, walking/controller and embedded packet boundaries.
 * World storage and physical key sampling are synthetic; no game, GL or server starts. */
public class AutomatedActionModuleTest {
    private static RotationBehaviorHookTest.Fixture f;
    private static Minecraft previousWrapper;
    private RuntimeObservation observer;
    private RotationBehaviorHookTest.Player target;
    private final List<Module> subscribed = new ArrayList<>();

    private static Field field(Class<?> type, String name) throws Exception {
        Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
    }
    @BeforeClass public static void open() throws Exception {
        f = new RotationBehaviorHookTest.Fixture(); previousWrapper = Wrapper.mc; Wrapper.mc = f.mc;
    }
    @AfterClass public static void close() throws Exception { Wrapper.mc = previousWrapper; f.close(); }
    @Before public void prepare() throws Exception {
        f.mc.currentScreen = null; f.mc.inGameHasFocus = true; f.player.isDead = false;
        field(Minecraft.class, "isGamePaused").setBoolean(f.mc, false);
        ScaffoldClientFlowTest.prepare(f, .5, .48);
        for (me.youngjibbit.value.Value<?> value : f.aura.getValues()) value.reset();
        for (me.youngjibbit.value.Value<?> value : f.scaffold.getValues()) value.reset();
        f.scaffold.getBooleanSetting("Swing").setValue(false);
        field(Aura.class, "autoblockController").set(f.aura, new AutoblockController());
        field(Module.class, "toggled").setBoolean(f.aura, false);
        f.player.sendQueue.clientWorldController = f.mc.theWorld;
        f.player.usingItem = false; f.player.isSwingInProgress = false;
        f.player.rotationYaw = f.player.rotationPitch = 0;
        f.mc.gameSettings.keyBindAttack = RotationBehaviorHookTest.bare(KeyBinding.class);
        f.mc.gameSettings.keyBindAttack.pressed = true;
        f.mc.gameSettings.keyBindUseItem = RotationBehaviorHookTest.bare(KeyBinding.class);
        target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
        target.worldObj = f.mc.theWorld; target.setEntityId(9);
        target.posX = .5; target.posY = 64; target.posZ = 2;
        target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, 1.7, .8, 65.8, 2.3));
        field(World.class, "loadedEntityList").set(f.mc.theWorld, new ArrayList<>(Collections.singletonList(target)));
        f.mc.objectMouseOver = new MovingObjectPosition(target);
        f.mc.playerController = new PlayerControllerMP(f.mc, f.player.sendQueue);
        field(PlayerControllerMP.class, "currentGameType").set(f.mc.playerController, WorldSettings.GameType.SPECTATOR);
        field(ClientRuntime.class, "attackScheduler").set(f.runtime, new AttackScheduler(() -> 1000, (min, max) -> 10));
        observer = f.runtime.getObservation(); observer.reset(); observer.beginOuterTick();
        drain();
    }
    @After public void cleanup() {
        for (Module module : subscribed) module.onDisable();
        subscribed.clear(); observer.reset(); drain();
    }
    private void subscribe(Module module) {
        if (module.isToggled()) module.onEnable(); else module.setToggled(true);
        subscribed.add(module);
    }
    private void ordinary(BooleanSupplier body) { observer.observePlayerStep(f.player, body, step -> step.getAsBoolean()); }
    private void pair(BooleanSupplier body) {
        observer.observePlayerStep(f.player, body, step -> { step.getAsBoolean(); step.getAsBoolean(); });
    }
    private boolean extra() { return observer.getCurrentStep().getKind() == ExecutedStep.Kind.EARNED_EXTRA; }
    private int drain() {
        int attacks = 0; Object packet;
        while ((packet = f.channel.readOutbound()) != null) if (packet instanceof C02PacketUseEntity) attacks++;
        return attacks;
    }
    private long attemptedMotion() {
        try { return field(AttackScheduler.class, "attemptedMotion").getLong(f.runtime.getAttackScheduler()); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private AutoClicker2 clicker() throws Exception {
        AutoClicker2 module = new AutoClicker2();
        module.getBooleanSetting("Enable Randomization").setValue(false);
        module.getModeSetting("Randomization Mode").setValue("Basic");
        return module;
    }
    private void ready(AutoClicker2 module) {
        try { ((TimerUtil) field(AutoClicker2.class, "clickTimer").get(module)).lastMS = 0; }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void update(AutoClicker2 module) { if (module != null) ready(module); Pulsar.BUS.post(new EventUpdate()); }

    @Test public void hitDelayFixOrdinaryClicksButExtraAfterOrdinaryDoesNot() {
        subscribe(new HitDelayFix());
        pair(() -> { update(null); assertEquals(extra() ? 0 : 1, drain()); return true; });
    }
    @Test public void clickerOrdinaryClicksButExtraAfterOrdinaryDoesNot() throws Exception {
        AutoClicker2 module = clicker(); subscribe(module);
        pair(() -> { update(module); assertEquals(extra() ? 0 : 1, drain()); return true; });
    }
    @Test public void deniedClickerDoesNotChangeArtificialUseInputOrConsumeItsTimer() throws Exception {
        AutoClicker2 module = clicker(); module.getBooleanSetting("AutoBlock").setValue(true); subscribe(module);
        pair(() -> {
            if (extra()) {
                f.mc.gameSettings.keyBindUseItem.pressed = true;
                f.mc.gameSettings.keyBindAttack.pressed = false;
                update(module); assertTrue(f.mc.gameSettings.keyBindUseItem.pressed);
                f.mc.gameSettings.keyBindAttack.pressed = true;
                update(module); assertTrue(f.mc.gameSettings.keyBindUseItem.pressed);
                try { assertEquals(0, ((TimerUtil) field(AutoClicker2.class, "clickTimer").get(module)).lastMS); }
                catch (Exception error) { throw new AssertionError(error); }
                assertEquals(0, drain());
            }
            return true;
        });
    }
    @Test public void withheldAndFinishedHistoryNeverAuthorizeSyntheticClicks() throws Exception {
        AutoClicker2 module = clicker(); subscribe(module); subscribe(new HitDelayFix());
        observer.observePlayerStep(f.player, () -> { fail("withheld has no body"); return true; }, step -> {});
        assertNotNull(observer.getLastWithheld()); update(module); assertEquals(0, drain());
        ordinary(() -> true); assertFalse(observer.getLastSteps().isEmpty());
        update(module); assertEquals(0, drain());
    }
    @Test public void packetEpochChangesRejectBothClickListenersEvenAfterResume() throws Exception {
        AutoClicker2 module = clicker(); subscribe(module); subscribe(new HitDelayFix());
        ordinary(() -> {
            f.network.getClientPackets().worldChanged(); update(module); assertEquals(0, drain());
            f.mc.inGameHasFocus = false; update(module); f.mc.inGameHasFocus = true;
            update(module); assertEquals(0, drain()); return true;
        });
        observer.beginOuterTick(); ordinary(() -> { update(module); assertTrue(drain() > 0); return true; });
    }
    @Test public void pauseGuiFocusInvalidIdentityAndShutdownRejectClicks() throws Exception {
        AutoClicker2 module = clicker(); subscribe(module); subscribe(new HitDelayFix());
        for (int mode = 0; mode < 5; mode++) {
            final int scenario = mode;
            observer.beginOuterTick();
            ordinary(() -> {
                try {
                    if (scenario == 0) f.mc.currentScreen = new GuiChat();
                    if (scenario == 1) f.mc.inGameHasFocus = false;
                    if (scenario == 2) field(Minecraft.class, "isGamePaused").setBoolean(f.mc, true);
                    if (scenario == 3) f.player.worldObj = null;
                    if (scenario == 4) f.mc.shutdown();
                    update(module); assertEquals("scenario " + scenario, 0, drain());
                    return true;
                } catch (Exception error) { throw new AssertionError(error); }
            });
            f.mc.currentScreen = null; f.mc.inGameHasFocus = true; f.player.worldObj = f.mc.theWorld;
            field(Minecraft.class, "isGamePaused").setBoolean(f.mc, false);
        }
    }
    @Test public void wrongOuterIdRejectsSyntheticClicks() throws Exception {
        AutoClicker2 module = clicker(); subscribe(module); subscribe(new HitDelayFix());
        ordinary(() -> {
            try { field(RuntimeObservation.class, "outerTickId").setLong(observer, observer.getOuterTickId() + 1); }
            catch (Exception error) { throw new AssertionError(error); }
            update(module); assertEquals(0, drain()); return true;
        });
    }
    @Test public void auraExtraDoesNotEvenEnterSchedulerAndKeepsAcceptedForeignOwner() {
        subscribe(f.aura);
        pair(() -> {
            f.accept("scaffold", 30);
            long before = attemptedMotion();
            EventMotion event = new EventMotion(0, 0, 0, 0, 0, true); event.setEventPhase(EventPhase.POST);
            Pulsar.BUS.post(event);
            if (extra()) assertEquals(before, attemptedMotion());
            else assertEquals(f.runtime.getRotationManager().getMotionSequence(), attemptedMotion());
            assertTrue(f.runtime.getRotationManager().isOwner("scaffold")); assertEquals(0, drain());
            return true;
        });
    }
    @Test public void auraExtraPreStillTargetsAndCalculatesRotationWithoutAutoblockEffects() throws Exception {
        subscribe(f.aura); int[] blockWrites = {0};
        AutoblockController block = new AutoblockController(() -> true, () -> false, () -> 4, value -> blockWrites[0]++);
        field(Aura.class, "autoblockController").set(f.aura, block);
        // Bind the module context before seeding owned block state.
        ordinary(() -> { f.player.onUpdateWalkingPlayer(); return true; }); drain();
        block.update(true, AutoblockController.Mode.VERUS, false, true); int before = blockWrites[0];
        pair(() -> {
            if (extra()) {
                f.player.usingItem = true; f.player.onUpdateWalkingPlayer();
                assertEquals(before, blockWrites[0]);
                assertNotNull(readAuraTarget()); assertTrue(f.runtime.getRotationManager().isOwner("aura"));
                f.player.motionX = f.player.motionZ = 0; f.player.moveFlying(0, 1, .1f);
                assertTrue(Math.hypot(f.player.motionX, f.player.motionZ) > .09);
                assertEquals(0, drain());
            }
            return true;
        });
    }
    private Object readAuraTarget() {
        try { return field(Aura.class, "target").get(f.aura); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    @Test public void auraOrdinaryReachesSchedulerButRejectedMotionStillPreventsAttack() {
        subscribe(f.aura);
        ordinary(() -> {
            f.player.usingItem = true; f.player.onUpdateWalkingPlayer();
            assertEquals(f.runtime.getRotationManager().getMotionSequence(), attemptedMotion());
            assertFalse(f.runtime.getAttackScheduler().hasAttackThisTick()); assertEquals(0, drain()); return true;
        });
    }
    @Test public void auraOrdinaryCanSendAcceptedAttackAndExtraCannotRepeatAttempt() throws Exception {
        f.aura.getBooleanSetting("Rotations").setValue(false); subscribe(f.aura);
        pair(() -> {
            long before = attemptedMotion(); f.player.onUpdateWalkingPlayer();
            if (extra()) { assertEquals(before, attemptedMotion()); assertEquals(0, drain()); }
            else { assertTrue(f.runtime.getAttackScheduler().hasAttackThisTick()); assertEquals(1, drain()); }
            return true;
        });
    }
    @Test public void deniedScaffoldExtraStillComputesRotationsButNeverPlacesOrSwitchesSlot() throws Exception {
        field(PlayerControllerMP.class, "currentGameType").set(f.mc.playerController, WorldSettings.GameType.SURVIVAL);
        subscribe(f.scaffold);
        for (int tick = 0; tick < 16; tick++) {
            ((ScaffoldClientFlowTest.Scene) f.mc.theWorld).motionTime.addAndGet(50_000_000L);
            observer.beginOuterTick(); f.runtime.getScaffoldPlacementService().beginTick();
            pair(() -> {
                if (extra()) {
                    f.player.onUpdateWalkingPlayer();
                    Object packet; while ((packet = f.channel.readOutbound()) != null) {
                        assertFalse(packet instanceof C08PacketPlayerBlockPlacement);
                        assertFalse(packet instanceof C09PacketHeldItemChange);
                    }
                    assertTrue(f.runtime.getRotationManager().isOwner("scaffold"));
                    assertEquals(0, f.player.inventory.currentItem); assertEquals(64, f.player.inventory.mainInventory[3].stackSize);
                }
                return true;
            });
        }
    }
    @Test public void physicalClickAndManualWindowClickWithoutStepStillReachNativeController() throws Exception {
        assertNull(observer.getCurrentStep()); f.mc.clickMouse(); assertEquals(1, drain());
        Container container = new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return true; }
            @Override public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) { return null; }
        };
        f.player.openContainer = container;
        f.mc.playerController.windowClick(0, 0, 0, 0, f.player);
        assertTrue(f.channel.readOutbound() instanceof C0EPacketClickWindow); assertNull(f.channel.readOutbound());
    }
}
