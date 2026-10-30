package me.youngjibbit.core.combat;

import me.youngjibbit.module.impl.player.NoSlow;
import me.youngjibbit.module.impl.player.SlotHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import org.junit.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Real onLivingUpdate through input, SlowdownEvent and all sprint decisions; stop before
 * horse/flight/base-world physics. Real walking packets use a disposable embedded transport.
 */
public class NoSlowSprintChecks {
    private static RotationBehaviorHookTest.Fixture fixture;
    private static SprintPlayer player;
    private static NoSlow noSlow;
    private static Input input;
    private static final class ReachedPhysics extends RuntimeException { }
    public static class SprintPlayer extends RotationBehaviorHookTest.Player {
        FoodStats food;
        boolean blind;
        public SprintPlayer() { super(); }
        @Override public boolean isPotionActive(Potion potion) { return potion == Potion.blindness && blind; }
        @Override public FoodStats getFoodStats() { return food; }
        @Override protected boolean pushOutOfBlocks(double x, double y, double z) { return false; }
        @Override public boolean isRidingHorse() { throw new ReachedPhysics(); }
    }
    private static class Input extends MovementInput {
        float forward = 1, strafe = 1;
        @Override public void updatePlayerMoveState() {
            moveForward = forward * (sneak ? .3f : 1); moveStrafe = strafe * (sneak ? .3f : 1);
        }
    }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    @BeforeClass public static void createClient() throws Exception {
        fixture = new RotationBehaviorHookTest.Fixture(); Bootstrap.register();
        player = RotationBehaviorHookTest.bare(SprintPlayer.class); player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities(); player.food = new FoodStats();
        player.worldObj = fixture.world; player.setEntityBoundingBox(new AxisAlignedBB(0, 64, 0, .6, 65.8, .6));
        set(EntityPlayerSP.class, player, "mc", fixture.mc); set(EntityPlayerSP.class, player, "sendQueue", fixture.player.sendQueue);
        fixture.mc.thePlayer = player; set(Minecraft.class, fixture.mc, "renderViewEntity", player);
        fixture.mc.gameSettings = RotationBehaviorHookTest.bare(GameSettings.class);
        fixture.mc.gameSettings.keyBindSprint = RotationBehaviorHookTest.bare(KeyBinding.class);
        fixture.mc.gameSettings.keyBindSneak = RotationBehaviorHookTest.bare(KeyBinding.class);
        fixture.mc.gameSettings.keyBindJump = RotationBehaviorHookTest.bare(KeyBinding.class);
        noSlow = new NoSlow(); input = new Input(); player.movementInput = input;
    }
    @AfterClass public static void closeClient() throws Exception { noSlow.onDisable(); fixture.close(); }
    @Before public void resetPlayer() throws Exception {
        noSlow.onDisable(); noSlow.getModeSetting("Mode").setValue("Vanilla");
        noSlow.getNumberSetting("Reduce X").setValue(100D); noSlow.getNumberSetting("Reduce Z").setValue(100D);
        player.sprint = false; player.usingItem = true; player.blind = false; player.isCollidedHorizontally = false;
        player.onGround = true; player.rotationYaw = 180; player.sprintingTicksLeft = 0;
        player.food.setFoodLevel(20); player.capabilities.allowFlying = player.capabilities.isFlying = false;
        player.inventory.currentItem = 0; player.inventory.mainInventory[0] = new ItemStack(Items.diamond_sword);
        player.motionX = player.motionY = player.motionZ = 0;
        input.forward = input.strafe = 1; input.jump = input.sneak = false; MovementInput.moveForward = MovementInput.moveStrafe = 0;
        set(EntityPlayerSP.class, player, "sprintToggleTimer", 0); set(EntityPlayerSP.class, player, "serverSprintState", false);
        fixture.mc.gameSettings.keyBindSprint.pressed = true; SlotHandler.resetSlot(); actions(); noSlow.onEnable();
    }
    private static void living() {
        try { player.onLivingUpdate(); fail("Expected the boundary after the real sprint decisions"); }
        catch (ReachedPhysics expected) { }
    }
    private static List<C0BPacketEntityAction.Action> actions() {
        List<C0BPacketEntityAction.Action> result = new ArrayList<>(); Object packet;
        while ((packet = fixture.channel.readOutbound()) != null)
            if (packet instanceof C0BPacketEntityAction) result.add(((C0BPacketEntityAction) packet).getAction());
        return result;
    }
    @Test public void supportedItemsAndEveryExistingModeConsumeSprintPermission() {
        for (String mode : new String[]{"Vanilla", "Intave", "Vulcan", "Reduced", "Polar", "Item Switch"})
            for (Item item : new Item[]{Items.diamond_sword, Items.bow, Items.apple, Items.potionitem}) {
                noSlow.getModeSetting("Mode").setValue(mode); player.inventory.mainInventory[0] = new ItemStack(item);
                player.sprint = false; living(); assertTrue(mode + "/" + item, player.isSprinting());
                assertEquals(mode.equals("Vulcan") ? .92f : 1, MovementInput.moveForward, 1e-6);
            }
    }
    @Test public void reducedForwardThresholdAndStrafeStrengthRemainIndependent() {
        noSlow.getModeSetting("Mode").setValue("Reduced");
        for (int forward : new int[]{0, 30, 79, 80, 100}) for (int strafe : new int[]{0, 30, 100}) {
            noSlow.getNumberSetting("Reduce X").setValue((double)forward);
            noSlow.getNumberSetting("Reduce Z").setValue((double)strafe);
            player.sprint = false; living(); assertEquals(forward >= 80, player.isSprinting());
            assertEquals(forward / 100f, MovementInput.moveForward, 1e-6);
            assertEquals(strafe / 100f, MovementInput.moveStrafe, 1e-6);
        }
    }
    @Test public void disabledUnsupportedAndVanillaLimitsRemainEffective() {
        noSlow.onDisable(); living(); assertFalse(player.isSprinting()); assertEquals(.2f, MovementInput.moveForward, 0);
        noSlow.onEnable(); player.inventory.mainInventory[0] = new ItemStack(Items.stick); living(); assertFalse(player.isSprinting());
        player.inventory.mainInventory[0] = new ItemStack(Items.bow);
        player.food.setFoodLevel(6); living(); assertFalse(player.isSprinting());
        player.food.setFoodLevel(7); living(); assertTrue(player.isSprinting()); player.sprint = false;
        player.food.setFoodLevel(0); player.capabilities.allowFlying = true; living(); assertTrue(player.isSprinting()); player.sprint = false;
        player.capabilities.allowFlying = false; player.food.setFoodLevel(20); player.blind = true; living(); assertFalse(player.isSprinting());
        player.blind = false; player.isCollidedHorizontally = true; living(); assertFalse(player.isSprinting());
        player.isCollidedHorizontally = false; input.sneak = true; living(); assertFalse(player.isSprinting());
        input.sneak = false; input.forward = 0; living(); assertFalse(player.isSprinting());
        input.forward = -1; living(); assertFalse(player.isSprinting());
    }
    @Test public void roundedMoveFixSprintIsDecidedFromActualServerYawForwardKey() {
        // Execute the real onLivingUpdate sprint predicates; a running player
        // holding W must stop sprinting if rounded MoveFix makes that input
        // sideways in the confirmed module-facing yaw.
        RotationManager rotation = fixture.runtime.getRotationManager();
        try {
            player.usingItem = false;
            player.ticksExisted = 20;
            player.rotationYaw = 180;
            input.forward = 1;
            input.strafe = 0;
            fixture.aura.getModeSetting("movefix").setValue("Legacy Rounded");
            rotation.bindConnection(fixture.network.getClientPackets());
            rotation.beginMotion(player, fixture.world, 180, 0);
            rotation.request("aura", RotationManager.AURA_PRIORITY, 90, 0);
            rotation.finishMotion(90, 0, true);
            assertEquals("aura", rotation.getConfirmedOwner(
                    player, fixture.world, fixture.network.getClientPackets()));
            player.sprint = true;
            living();
            assertFalse("W becomes pure strafe at 90 degrees; no forward sprint", player.isSprinting());

            fixture.aura.getModeSetting("movefix").setValue("Legacy Strict");
            player.sprint = true;
            living();
            assertTrue("Strict preserves W relative to the module yaw", player.isSprinting());

            fixture.aura.getModeSetting("movefix").setValue("Legacy Rounded");
            rotation.reset("aura");
            player.sprint = true;
            living();
            assertTrue("Without a confirmed owner vanilla W sprint is unchanged", player.isSprinting());
        } finally {
            rotation.reset("aura");
            fixture.aura.getModeSetting("movefix").setValue("Rotationsgebunden");
        }
    }

    @Test public void continuedSprintAndStartStopPacketsFollowFinalInput() {
        living(); player.onUpdateWalkingPlayer(); assertTrue(player.isSprinting());
        assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions());
        living(); player.onUpdateWalkingPlayer(); assertTrue(player.isSprinting()); assertTrue(actions().isEmpty());
        noSlow.getModeSetting("Mode").setValue("Reduced"); noSlow.getNumberSetting("Reduce X").setValue(79D);
        living(); player.onUpdateWalkingPlayer(); assertFalse(player.isSprinting());
        assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.STOP_SPRINTING), actions());
        noSlow.getNumberSetting("Reduce X").setValue(80D); living(); player.onUpdateWalkingPlayer(); assertTrue(player.isSprinting());
        assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions());
        noSlow.onDisable(); living(); player.onUpdateWalkingPlayer(); assertFalse(player.isSprinting());
        assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.STOP_SPRINTING), actions());
        player.usingItem = false; living(); player.onUpdateWalkingPlayer(); assertTrue(player.isSprinting());
        assertEquals(Collections.singletonList(C0BPacketEntityAction.Action.START_SPRINTING), actions());
        assertEquals(180, player.rotationYaw, 0);
    }
    @Test public void actualVanillaSprintDecisionUsesRoundedServerFacingForwardInput() {
        noSlow.onDisable();
        player.usingItem = false;
        player.ticksExisted = 20;
        player.rotationYaw = 180;
        input.forward = 1;
        input.strafe = 0;
        input.sneak = false;
        RotationManager rotations = fixture.runtime.getRotationManager();
        String previousMode = fixture.aura.getModeSetting("movefix").getValue();
        try {
            rotations.bindConnection(fixture.network.getClientPackets());
            rotations.beginMotion(player, fixture.world, 180, 0);
            rotations.request("aura", RotationManager.AURA_PRIORITY, 90, 0);
            rotations.finishMotion(90, 0, true);

            fixture.aura.getModeSetting("movefix").setValue("Legacy Rounded");
            player.sprint = true;
            living();
            assertFalse("W maps to strafe-only at a 90 degree camera/server yaw difference",
                    player.isSprinting());

            fixture.aura.getModeSetting("movefix").setValue("Legacy Strict");
            player.sprint = false;
            living();
            assertTrue("Strict keeps the original forward key", player.isSprinting());

            rotations.beginMotion(player, fixture.world, 180, 0);
            rotations.request("aura", RotationManager.AURA_PRIORITY, 145, 0);
            rotations.finishMotion(145, 0, true);
            fixture.aura.getModeSetting("movefix").setValue("Legacy Rounded");
            player.sprint = false;
            living();
            assertTrue("Rounded W still has positive forward at 35 degrees",
                    player.isSprinting());
        } finally {
            fixture.aura.getModeSetting("movefix").setValue(previousMode);
            rotations.reset();
        }
    }

    @Test public void doubleTapUsesTheSamePermissionAndVanillaWorksWithoutItemUse() throws Exception {
        fixture.mc.gameSettings.keyBindSprint.pressed = false;
        living(); assertFalse(player.isSprinting()); // first press arms the double-tap window
        input.forward = 0; living(); input.forward = 1; living(); assertTrue(player.isSprinting());
        noSlow.onDisable(); player.usingItem = false; player.sprint = false;
        set(EntityPlayerSP.class, player, "sprintToggleTimer", 0); MovementInput.moveForward = 0;
        living(); assertFalse(player.isSprinting()); input.forward = 0; living(); input.forward = 1;
        living(); assertTrue(player.isSprinting());
    }
}
