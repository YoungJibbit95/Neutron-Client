package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.FoodStats;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.world.World;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Real fresh-key input, module events and subsequent Downwards consumer.
 * World storage and physical keys are synthetic; no native window or server.
 * The living hook stops only after the production input/sprint/rotation hooks.
 */
public class AdaptiveBridgeInputSafetyFlowTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final class ReachedInputBoundary extends RuntimeException { }

    public static class InputPlayer extends RotationBehaviorHookTest.Player {
        FoodStats food;
        @Override public boolean isSneaking() {
            return movementInput != null && movementInput.sneak;
        }
        @Override public FoodStats getFoodStats() { return food; }
        @Override protected boolean pushOutOfBlocks(double x, double y, double z) { return false; }
        @Override public boolean isRidingHorse() { throw new ReachedInputBoundary(); }
        @Override public boolean isInWater() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean isWet() { return false; }
        @Override protected boolean canTriggerWalking() { return false; }
        @Override protected void doBlockCollisions() { }
        @Override protected void updateFallState(double y, boolean grounded, Block block, BlockPos pos) { }
    }

    public static class Scene extends ArsenicScaffoldFlowTest.Scene {
        @Override public boolean isFlammableWithin(AxisAlignedBB box) { return false; }
    }

    static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
        final InputPlayer player = RotationBehaviorHookTest.bare(InputPlayer.class);
        final Scene scene = RotationBehaviorHookTest.bare(Scene.class);
        final GameSettings settings;

        Run() throws Exception {
            ScaffoldClientFlowTest.Scene original = ScaffoldClientFlowTest.prepare(f, .05, .05);
            scene.blocks = original.blocks;
            scene.border = original.border;
            scene.motionTime = original.motionTime;
            ScaffoldClientFlowTest.set(World.class, scene, "loadedEntityList", new java.util.ArrayList<>());
            ScaffoldClientFlowTest.set(World.class, scene, "theProfiler", new Profiler());
            for (Class<?> type = RotationBehaviorHookTest.Player.class;
                 type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    field.set(player, field.get(f.player));
                }
            }
            player.worldObj = scene;
            player.width = .6f;
            player.height = 1.8f;
            player.inventory = new InventoryPlayer(player);
            player.inventory.mainInventory[3] = f.player.inventory.mainInventory[3];
            player.capabilities = new PlayerCapabilities();
            player.food = new FoodStats();
            player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            f.mc.thePlayer = player;
            f.mc.theWorld = scene;
            ScaffoldClientFlowTest.set(Minecraft.class, f.mc, "renderViewEntity", player);
            settings = f.mc.gameSettings;
            settings.keyBindForward = new KeyBinding("bridge.forward", 17, "bridge");
            settings.keyBindBack = new KeyBinding("bridge.back", 31, "bridge");
            settings.keyBindLeft = new KeyBinding("bridge.left", 30, "bridge");
            settings.keyBindRight = new KeyBinding("bridge.right", 32, "bridge");
            settings.keyBindJump = new KeyBinding("bridge.jump", 57, "bridge");
            settings.keyBindSneak = new KeyBinding("bridge.sneak", 42, "bridge");
            settings.keyBindSprint = new KeyBinding("bridge.sprint", 29, "bridge");
            player.movementInput = new MovementInputFromOptions(settings);
            MovementInput.moveForward = MovementInput.moveStrafe = 0;
            f.scaffold.getModeSetting("Place Timing").setValue("Post");
            f.scaffold.getModeSetting("movefix").setValue("Aus");
        }

        void input() {
            scene.motionTime.addAndGet(50_000_000L);
            f.runtime.getScaffoldPlacementService().beginTick();
            RuntimeObservation observation = f.runtime.getObservation();
            observation.beginOuterTick();
            try {
                observation.observePlayerStep(player, () -> {
                    try {
                        player.onLivingUpdate();
                        fail("fixture must reach the boundary after the real input consumer");
                    } catch (ReachedInputBoundary expected) { }
                    return true;
                }, step -> step.getAsBoolean());
            } finally {
                observation.endOuterTick();
            }
        }

        @Override public void close() throws Exception {
            try {
                f.scaffold.onDisable();
                for (KeyBinding key : new KeyBinding[]{settings.keyBindForward, settings.keyBindBack,
                        settings.keyBindLeft, settings.keyBindRight, settings.keyBindJump,
                        settings.keyBindSneak, settings.keyBindSprint}) key.pressed = false;
                MovementInput.moveForward = MovementInput.moveStrafe = 0;
            } finally {
                f.close();
            }
        }
    }

    private void legacyConfig(Run r, String approach) throws Exception {
        Path directory = temp.newFolder().toPath();
        String saved = "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":"
                + "{\"scaffold_approach\":\"" + approach + "\",\"downwards\":true}}}}";
        Files.write(directory.resolve("OldBridge.json"), saved.getBytes(StandardCharsets.UTF_8));
        ConfigManager configs = new ConfigManager(directory,
                Collections.<Module>singletonList(r.f.scaffold), () -> false, message -> { });
        assertTrue(configs.getLastError(), configs.loadConfig("OldBridge"));
        assertEquals(approach, r.f.scaffold.getModeSetting("Scaffold Approach").getValue());
        assertTrue(r.f.scaffold.getBooleanSetting("Downwards").getValue());
        assertEquals("loading must not rewrite the old settings file", saved,
                new String(Files.readAllBytes(directory.resolve("OldBridge.json")), StandardCharsets.UTF_8));
        // This regression isolates the old Downwards consumer from new edge policy.
        if (r.f.scaffold.getModeSetting("Bridge Edge Safety") != null)
            r.f.scaffold.getModeSetting("Bridge Edge Safety").setValue("Off");
    }

    @Test public void savedAdaptiveDownwardsCannotUndoPhysicalSneakAfterTheInputEvent() throws Exception {
        try (Run r = new Run()) {
            legacyConfig(r, "Adaptive Bridge");
            assertFalse("legacy Downwards is hidden in Adaptive Bridge",
                    r.f.scaffold.getBooleanSetting("Downwards").isVisible());
            r.settings.keyBindForward.pressed = r.settings.keyBindSneak.pressed = true;
            AtomicReference<float[]> eventInput = new AtomicReference<>();
            Listener<MovementInputEvent> observe = new Listener<>(event -> eventInput.set(
                    new float[]{event.getForward(), event.getStrafe(), event.isSneak() ? 1 : 0}));
            Pulsar.BUS.subscribe(observe);
            r.f.scaffold.onEnable();
            try {
                r.input();
                assertNotNull("the real fresh input event must run", eventInput.get());
                assertEquals(.3f, eventInput.get()[0], 1e-6f);
                assertEquals(1, eventInput.get()[2], 0);
                PlacementTarget target = (PlacementTarget) ScaffoldOwnerContinuityTest.field(
                        Scaffold.class, "currentTarget").get(r.f.scaffold);
                assertNotNull("a real validated target must exercise the former suppression condition", target);
                assertTrue(r.f.runtime.getScaffoldPlacementService().isValid(target));
                assertTrue("the later Downwards consumer must preserve physical sneak", r.player.movementInput.sneak);
                assertEquals("sneak slowdown must not be undone or applied twice", .3f,
                        r.player.movementInput.moveForward, 1e-6f);
                assertTrue(r.settings.keyBindSneak.isKeyDown());
                r.f.scaffold.onDisable();
                r.input();
                assertTrue(r.player.movementInput.sneak);
                assertEquals(.3f, r.player.movementInput.moveForward, 1e-6f);
                assertTrue(r.settings.keyBindSneak.isKeyDown());
            } finally {
                Pulsar.BUS.unsubscribe(observe);
            }
        }
    }

    @Test public void savedNeutronDownwardsRetainsItsExistingSuppressionAndScaling() throws Exception {
        try (Run r = new Run()) {
            legacyConfig(r, "Neutron");
            r.scene.blocks.put(new BlockPos(-1, 62, 0), Blocks.stone.getDefaultState());
            r.settings.keyBindForward.pressed = r.settings.keyBindSneak.pressed = true;
            r.f.scaffold.onEnable();
            r.input();
            PlacementTarget target = (PlacementTarget) ScaffoldOwnerContinuityTest.field(
                    Scaffold.class, "currentTarget").get(r.f.scaffold);
            assertNotNull("legacy downwards has its own valid lower placement target", target);
            assertEquals(62, target.getDestination().getY());
            assertTrue(r.f.runtime.getScaffoldPlacementService().isValid(target));
            assertFalse("Neutron retains its explicit downwards input behavior", r.player.movementInput.sneak);
            assertEquals(1, r.player.movementInput.moveForward, 1e-6f);
            assertTrue("only the fresh sample changes, never the physical key", r.settings.keyBindSneak.isKeyDown());
        }
    }
}
