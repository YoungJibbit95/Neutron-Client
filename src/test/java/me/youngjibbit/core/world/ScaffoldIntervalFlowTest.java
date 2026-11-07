package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual module/native controller success, followed by its ordinary fresh-input hook. */
public class ScaffoldIntervalFlowTest {
    private static MovementInput fresh(float forward, boolean sneak) {
        MovementInput input = new MovementInput(); input.moveForward = forward; input.sneak = sneak;
        Scaffold.applyDownwardsInput(input); return input;
    }
    @Test public void successSchedulesIndependentInputPulseInBothApproaches() throws Exception {
        for (String approach : new String[]{"Neutron", "Arsenic"}) {
            try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
                ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
                f.scaffold.getModeSetting("Scaffold Approach").setValue(approach);
                f.scaffold.getBooleanSetting("Eagle").setValue(false);
                f.scaffold.getNumberSetting("Sneak After Blocks").setValue(1D);
                f.scaffold.onEnable();
                try {
                    int placed = 0;
                    for (int i = 0; i < 30 && placed == 0; i++) placed += ArsenicScaffoldFlowTest.step(f, scene);
                    assertEquals(approach, 1, placed);
                    assertTrue(scene.blocks.containsKey(new BlockPos(0, 63, 0)));
                    MovementInput first = fresh(1, false);
                    assertTrue(first.sneak); assertEquals(.3f, first.moveForward, 0);
                    // Existing manual/Eagle slowdown is not applied twice.
                    MovementInput second = fresh(.3f, true);
                    assertTrue(second.sneak); assertEquals(.3f, second.moveForward, 0);
                    assertFalse(fresh(1, false).sneak);
                    assertFalse(f.mc.gameSettings.keyBindSneak.isKeyDown());
                } finally { f.scaffold.onDisable(); }
            }
        }
    }
    @Test public void failedPlacementDoesNotCountAndDisablePreservesManualInput() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            f.scaffold.getNumberSetting("Sneak After Blocks").setValue(1D);
            f.player.inventory.mainInventory[3] = null;
            f.scaffold.onEnable();
            try {
                for (int i = 0; i < 4; i++) assertEquals(0, ArsenicScaffoldFlowTest.step(f, scene));
                assertFalse(fresh(1, false).sneak);
            } finally { f.scaffold.onDisable(); }
            MovementInput manual = fresh(.3f, true);
            assertTrue(manual.sneak); assertEquals(.3f, manual.moveForward, 0);
        }
    }
    @Test public void settingIsVisibleWithoutEagleAndConfigChangeResetsPendingPulse() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            me.youngjibbit.value.impl.SliderValue value = f.scaffold.getNumberSetting("Sneak After Blocks");
            assertEquals(0D, value.getValue(), 0);
            f.scaffold.getBooleanSetting("Eagle").setValue(false);
            assertTrue(value.getVisibility() == null || value.getVisibility().test(f.scaffold));
            value.setValue(1D); f.scaffold.onEnable();
            try {
                int placed = 0;
                for (int i = 0; i < 30 && placed == 0; i++) placed += ArsenicScaffoldFlowTest.step(f, scene);
                assertEquals(1, placed);
                value.setValue(2D);
                assertFalse(fresh(1, false).sneak);
                f.scaffold.getModeSetting("Scaffold Approach").setValue("Neutron");
                assertTrue(value.getVisibility() == null || value.getVisibility().test(f.scaffold));
            } finally { f.scaffold.onDisable(); }
        }
    }
    @Test public void arsenicHoldsAcceptedLookDuringNoTargetGap() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            f.scaffold.onEnable();
            try {
                int placed = 0;
                for (int i = 0; i < 30 && placed == 0; i++) placed += ArsenicScaffoldFlowTest.step(f, scene);
                assertEquals(1, placed);
                float yaw = f.runtime.getRotationManager().getEffectiveYaw();
                float pitch = f.runtime.getRotationManager().getEffectivePitch();
                f.player.rotationYaw = 90;
                for (int i = 0; i < 3; i++) {
                    assertEquals(0, ArsenicScaffoldFlowTest.step(f, scene));
                    assertEquals(yaw, f.runtime.getRotationManager().getEffectiveYaw(), 0);
                    assertEquals(pitch, f.runtime.getRotationManager().getEffectivePitch(), 0);
                }
            } finally { f.scaffold.onDisable(); }
        }
    }

    @Test public void cancelledPlacementCannotArmPulse() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            f.scaffold.getNumberSetting("Sneak After Blocks").setValue(1D);
            me.zero.alpine.listener.Listener<me.youngjibbit.event.packet.EventPacket> listener =
                    new me.zero.alpine.listener.Listener<>(event -> {
                        if (event.isPre() && event.getPacket() instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement)
                            event.cancel();
                    });
            me.youngjibbit.Pulsar.BUS.subscribe(listener); f.scaffold.onEnable();
            try {
                for (int i = 0; i < 12; i++) assertEquals(0, ArsenicScaffoldFlowTest.step(f, scene));
                assertFalse(fresh(1, false).sneak);
                assertEquals(64, f.player.inventory.mainInventory[3].stackSize);
            } finally { f.scaffold.onDisable(); me.youngjibbit.Pulsar.BUS.unsubscribe(listener); }
        }
    }
    @Test public void focusLossDiscardsPendingPulseAndOldConfigResetsSetting() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            f.scaffold.getNumberSetting("Sneak After Blocks").setValue(1D); f.scaffold.onEnable();
            try {
                int placed = 0;
                for (int i = 0; i < 30 && placed == 0; i++) placed += ArsenicScaffoldFlowTest.step(f, scene);
                assertEquals(1, placed);
                f.mc.inGameHasFocus = false;
                assertFalse(fresh(1, false).sneak);
                f.mc.inGameHasFocus = true;
                assertFalse(fresh(1, false).sneak);
                f.scaffold.beginConfigUpdate();
                try { assertEquals(0D, f.scaffold.getNumberSetting("Sneak After Blocks").getValue(), 0); }
                finally { f.scaffold.endConfigUpdate(); }
            } finally { f.scaffold.onDisable(); }
        }
    }
    @Test public void noTargetCannotAcquireFirstScaffoldRotation() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            scene.blocks.put(new BlockPos(0, 63, 0), net.minecraft.init.Blocks.stone.getDefaultState());
            f.player.rotationYaw = 37; f.scaffold.onEnable();
            try {
                assertEquals(0, ArsenicScaffoldFlowTest.step(f, scene));
                assertFalse(f.runtime.getRotationManager().canUseRotation("scaffold", false));
            } finally { f.scaffold.onDisable(); }
        }
    }

    @Test public void reenabledGenerationCannotReceiveOldPlacementPulse() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            f.scaffold.getNumberSetting("Sneak After Blocks").setValue(1D);
            boolean[] invoked = {false};
            me.zero.alpine.listener.Listener<me.youngjibbit.event.packet.EventPacket> listener =
                    new me.zero.alpine.listener.Listener<>(event -> {
                        if (!invoked[0] && event.isPre() && event.getPacket() instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement) {
                            invoked[0] = true; f.scaffold.onDisable(); f.scaffold.onEnable();
                        }
                    });
            me.youngjibbit.Pulsar.BUS.subscribe(listener); f.scaffold.onEnable();
            try {
                for (int i = 0; i < 30 && !invoked[0]; i++) ArsenicScaffoldFlowTest.step(f, scene);
                assertTrue(invoked[0]); assertFalse(fresh(1, false).sneak);
            } finally { f.scaffold.onDisable(); me.youngjibbit.Pulsar.BUS.unsubscribe(listener); }
        }
    }
    @Test public void intervalIsAppliedAfterDownwardsSuppression() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.prepare(f, .5, .48);
            f.scaffold.getBooleanSetting("Downwards").setValue(true);
            f.scaffold.getNumberSetting("Sneak After Blocks").setValue(1D);
            f.scaffold.onEnable();
            try {
                PlacementTarget target = f.runtime.getScaffoldPlacementService().findTarget(new PlacementContext(false, false, 63));
                assertNotNull(target);
                ScaffoldClientFlowTest.set(Scaffold.class, f.scaffold, "currentTarget", target);
                java.lang.reflect.Field field = Scaffold.class.getDeclaredField("sneakInterval"); field.setAccessible(true);
                ((ScaffoldSneakInterval) field.get(f.scaffold)).placed(1);
                f.mc.gameSettings.keyBindSneak.pressed = true;
                MovementInput input = fresh(.3f, true);
                assertTrue(input.sneak); assertEquals(.3f, input.moveForward, .00001f);
                assertTrue(f.mc.gameSettings.keyBindSneak.isKeyDown());
            } finally { f.mc.gameSettings.keyBindSneak.pressed = false; f.scaffold.onDisable(); }
        }
    }

    @Test public void cancelledInputDoesNotApplyOrConsumePendingPulse() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ArsenicScaffoldFlowTest.Scene scene = ArsenicScaffoldFlowTest.prepare(f);
            f.scaffold.getNumberSetting("Sneak After Blocks").setValue(1D);
            f.scaffold.onEnable();
            try {
                int placed = 0;
                for (int i = 0; i < 30 && placed == 0; i++) placed += ArsenicScaffoldFlowTest.step(f, scene);
                assertEquals(1, placed);
                MovementInput cancelled = new MovementInput(); cancelled.moveForward = 1;
                Scaffold.applyDownwardsInput(cancelled, false);
                assertFalse(cancelled.sneak); assertEquals(1, cancelled.moveForward, 0);
                assertTrue(fresh(1, false).sneak);
                assertTrue(fresh(1, false).sneak);
                assertFalse(fresh(1, false).sneak);
            } finally { f.scaffold.onDisable(); }
        }
    }

}
