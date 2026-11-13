package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.combat.Aura;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.util.MovingObjectPosition;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AdaptiveAuraActionsTest {
    @Test public void executionModesKeepOneGuardedAttackAndDocumentLocalEffects() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "executionModesKeepOneGuardedAttackAndDocumentLocalEffects");
    }
    @Test public void nativeClickRejectsWrongPickedEntityAndClickCooldown() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "nativeClickRejectsWrongPickedEntityAndClickCooldown");
    }
    @Test public void deniedReleasePreventsAttackAndReconfigurationReleasesOwnUse() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "deniedReleasePreventsAttackAndReconfigurationReleasesOwnUse");
    }
    @Test public void fakeIsVisualAndStopsOnHandOrLifecycleChange() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "fakeIsVisualAndStopsOnHandOrLifecycleChange");
    }
    @Test public void everyExecutionModeRespectsCanceledAttackPackets() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "everyExecutionModeRespectsCanceledAttackPackets");
    }
    @Test public void acceptedUseCallbacksCannotLeaveStaleLocalBlocking() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "acceptedUseCallbacksCannotLeaveStaleLocalBlocking");
    }
    @Test public void throwingAcceptedStartClosesOnlyItsOwnSwordRequest() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "throwingAcceptedStartClosesOnlyItsOwnSwordRequest");
    }
    @Test public void failSwingIsAnExplicitVisualOnlyOptionForEveryExecutionMode() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "failSwingIsAnExplicitVisualOnlyOptionForEveryExecutionMode");
    }
    @Test public void visibleSwitchAndBlockChoicesAlsoWorkOutsideAdaptiveRotations() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "visibleSwitchAndBlockChoicesAlsoWorkOutsideAdaptiveRotations");
    }
    @Test public void rejectedStartDoesNotEmitAReleaseWithoutTransportEvidence() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "rejectedStartDoesNotEmitAReleaseWithoutTransportEvidence");
    }
    @Test public void acceptedReleaseClearsOldUseBeforeManualPostTakeover() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "acceptedReleaseClearsOldUseBeforeManualPostTakeover");
    }

    public static class Checks {
        private Aura configure(AuraMovementConsistencyTest.Run r, String attack) throws Exception {
            r.remoteTarget(); r.input.forward = 0;
            Aura aura = r.f.run.fixture.aura;
            aura.getModeSetting("Rot Mode").setValue("Adaptive");
            aura.getModeSetting("Adaptive Rotation Engine").setValue("Neutron");
            aura.getModeSetting("Attack Mode").setValue(attack);
            r.f.run.fixture.mc.objectMouseOver = new MovingObjectPosition(r.f.run.scene.getEntityByID(19));
            r.player.inventory.mainInventory[0] = new ItemStack(Items.iron_sword);
            return aura;
        }
        private long count(List<Object> packets, Class<?> type) { return packets.stream().filter(type::isInstance).count(); }

        @Test public void executionModesKeepOneGuardedAttackAndDocumentLocalEffects() throws Exception {
            for (String mode : new String[]{"PlayerController", "Legit", "Packet"}) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    configure(r, mode); r.player.motionX = .4; r.player.motionZ = .2; r.player.sprint = true;
                    List<Object> packets = r.f.walk();
                    assertEquals(mode, 1, count(packets, C02PacketUseEntity.class));
                    assertEquals(mode, 1, count(packets, C0APacketAnimation.class));
                    assertEquals(mode, "Packet".equals(mode) ? .4 : .24, r.player.motionX, 1e-8);
                }
            }
        }

        @Test public void nativeClickRejectsWrongPickedEntityAndClickCooldown() throws Exception {
            for (int reason = 0; reason < 3; reason++) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    configure(r, "Legit");
                    if (reason == 0) r.f.run.fixture.mc.objectMouseOver = null;
                    if (reason == 1) r.f.run.fixture.mc.objectMouseOver = new MovingObjectPosition(r.f.first);
                    if (reason == 2) r.f.run.fixture.mc.leftClickCounter = 5;
                    List<Object> packets = r.f.walk();
                    assertEquals(0, count(packets, C02PacketUseEntity.class));
                    assertEquals("Fail Swing is off", 0, count(packets, C0APacketAnimation.class));
                    assertEquals(0, count(packets, C07PacketPlayerDigging.class));
                }
            }
        }

        @Test public void deniedReleasePreventsAttackAndReconfigurationReleasesOwnUse() throws Exception {
            try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                Aura aura = configure(r, "PlayerController");
                aura.getBooleanSetting("Autoblock").setValue(true); aura.getModeSetting("Autoblock Mode").setValue("Force");
                assertEquals(1, count(r.f.walk(), C02PacketUseEntity.class)); assertNotNull(r.player.getItemInUse());
                assertTrue(aura.keepsAdaptiveSwordUse());
                ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet = e -> { if (e.isPre() && e.isOutbound() && e.getPacket() instanceof C07PacketPlayerDigging) e.cancel(); };
                Pulsar.BUS.subscribe(hooks);
                try { assertEquals(0, count(r.f.walk(), C02PacketUseEntity.class)); assertNotNull(r.player.getItemInUse()); }
                finally { Pulsar.BUS.unsubscribe(hooks); }
                aura.getModeSetting("Autoblock Mode").setValue("Fake");
                assertNull(r.player.getItemInUse());
            }
        }

        @Test public void fakeIsVisualAndStopsOnHandOrLifecycleChange() throws Exception {
            try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                Aura aura = configure(r, "PlayerController");
                aura.getBooleanSetting("Autoblock").setValue(true); aura.getModeSetting("Autoblock Mode").setValue("Fake");
                ItemStack sword = r.player.getHeldItem(); List<Object> packets = r.f.walk();
                assertEquals(0, count(packets, C08PacketPlayerBlockPlacement.class)); assertNull(r.player.getItemInUse());
                assertTrue(aura.shouldRenderAdaptiveBlock(sword));
                r.player.inventory.currentItem = 1; assertFalse(aura.shouldRenderAdaptiveBlock(sword));
                r.player.inventory.currentItem = 0; aura.onDisable(); assertFalse(aura.shouldRenderAdaptiveBlock(sword));
            }
        }

        @Test public void everyExecutionModeRespectsCanceledAttackPackets() throws Exception {
            for (String mode : new String[]{"PlayerController", "Legit", "Packet"}) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    Aura aura = configure(r, mode); aura.getBooleanSetting("Autoblock").setValue(true);
                    aura.getModeSetting("Autoblock Mode").setValue("Force");
                    ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                    hooks.packet = e -> { if (e.isPre() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity) e.cancel(); };
                    Pulsar.BUS.subscribe(hooks);
                    try {
                        List<Object> packets = r.f.walk();
                        assertEquals(mode, 0, count(packets, C02PacketUseEntity.class));
                        assertEquals(mode, 0, count(packets, C08PacketPlayerBlockPlacement.class));
                        assertNull(r.player.getItemInUse());
                    } finally { Pulsar.BUS.unsubscribe(hooks); }
                }
            }
        }

        @Test public void acceptedUseCallbacksCannotLeaveStaleLocalBlocking() throws Exception {
            for (int boundary = 0; boundary < 3; boundary++) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    Aura aura = configure(r, "PlayerController"); aura.getBooleanSetting("Autoblock").setValue(true);
                    aura.getModeSetting("Autoblock Mode").setValue("Force");
                    if (boundary != 0) { r.f.walk(); assertNotNull(r.player.getItemInUse()); }
                    final int selectedBoundary = boundary; final boolean[] changed = {false};
                    ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                    hooks.packet = e -> {
                        if (changed[0] || !e.isPost() || !e.isOutbound()) return;
                        if (selectedBoundary == 0 && e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                            changed[0] = true; aura.getBooleanSetting("Autoblock").setValue(false);
                        } else if (selectedBoundary != 0 && e.getPacket() instanceof C07PacketPlayerDigging) {
                            changed[0] = true;
                            if (selectedBoundary == 1) aura.getModeSetting("Autoblock Mode").setValue("Fake");
                            else aura.onDisable();
                        }
                    };
                    Pulsar.BUS.subscribe(hooks);
                    try {
                        List<Object> packets = r.f.walk(); assertTrue(changed[0]); assertNull(r.player.getItemInUse());
                        assertEquals(1, count(packets, C07PacketPlayerDigging.class));
                        assertEquals(selectedBoundary == 0 ? 1 : 0, count(packets, C02PacketUseEntity.class));
                        assertFalse(aura.keepsAdaptiveSwordUse());
                    } finally { Pulsar.BUS.unsubscribe(hooks); }
                }
            }
        }

        @Test public void throwingAcceptedStartClosesOnlyItsOwnSwordRequest() throws Exception {
            for (boolean releasing : new boolean[]{false,true}) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    Aura aura = configure(r, "PlayerController"); aura.getBooleanSetting("Autoblock").setValue(true);
                    aura.getModeSetting("Autoblock Mode").setValue("Force"); final boolean[] threw = {false};
                    if (releasing) { r.f.walk(); assertNotNull(r.player.getItemInUse()); }
                    ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                    hooks.packet = e -> {
                        if (!threw[0] && e.isPost() && e.isOutbound() && (releasing
                                ? e.getPacket() instanceof C07PacketPlayerDigging : e.getPacket() instanceof C08PacketPlayerBlockPlacement)) {
                            threw[0] = true; throw new IllegalStateException("synthetic accepted-use failure");
                        }
                    };
                    Pulsar.BUS.subscribe(hooks);
                    try {
                        try { r.f.walk(); fail("callback must throw"); } catch (IllegalStateException expected) { }
                        assertTrue(threw[0]); assertNull(r.player.getItemInUse());
                        assertEquals(1, count(r.f.run.drain(), C07PacketPlayerDigging.class));
                    } finally { Pulsar.BUS.unsubscribe(hooks); }
                }
            }
        }

        @Test public void failSwingIsAnExplicitVisualOnlyOptionForEveryExecutionMode() throws Exception {
            for (String mode : new String[]{"PlayerController", "Legit", "Packet"}) for (boolean swing : new boolean[]{false,true}) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    Aura aura = configure(r, mode); aura.getBooleanSetting("Fail Swing").setValue(swing);
                    aura.getNumberSetting("Aim Range Margin").setValue(2D);
                    aura.getBooleanSetting("Autoblock").setValue(true); aura.getModeSetting("Autoblock Mode").setValue("Force");
                    Entity target = r.f.run.scene.getEntityByID(19); target.posZ = target.lastTickPosZ = 4;
                    target.setEntityBoundingBox(new net.minecraft.util.AxisAlignedBB(.2,64,3.7,.8,65.8,4.3));
                    List<Object> packets = r.f.walk();
                    assertEquals(mode, swing ? 1 : 0, count(packets, C0APacketAnimation.class));
                    assertEquals(mode, 0, count(packets, C02PacketUseEntity.class));
                    assertEquals(mode, 0, count(packets, C07PacketPlayerDigging.class));
                    assertEquals(mode, 0, count(packets, C08PacketPlayerBlockPlacement.class));
                }
            }
        }

        @Test public void visibleSwitchAndBlockChoicesAlsoWorkOutsideAdaptiveRotations() throws Exception {
            try (Wave05AuraSelectionChecks.SelectionRun r = new Wave05AuraSelectionChecks.SelectionRun("Switch")) {
                r.run.fixture.aura.getNumberSetting("Switch Delay MS").setValue(0D);
                r.walk(); assertSame(r.first, r.run.fixture.aura.getActiveTarget());
                r.walk(); assertSame(r.second, r.run.fixture.aura.getActiveTarget());
            }
            for (String block : new String[]{"Vanilla", "Release", "Force"}) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    Aura aura = configure(r, "PlayerController"); aura.getModeSetting("Rot Mode").setValue("Basic");
                    aura.getBooleanSetting("Autoblock").setValue(true); aura.getModeSetting("Autoblock Mode").setValue(block);
                    aura.getNumberSetting("Reblock Delay").setValue(0D);
                    assertEquals(block, 1, count(r.f.walk(), C08PacketPlayerBlockPlacement.class));
                    assertNotNull(r.player.getItemInUse());
                }
            }
        }

        @Test public void rejectedStartDoesNotEmitAReleaseWithoutTransportEvidence() throws Exception {
            for (boolean throwing : new boolean[]{false,true}) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    Aura aura = configure(r, "PlayerController"); aura.getBooleanSetting("Autoblock").setValue(true);
                    aura.getModeSetting("Autoblock Mode").setValue("Force");
                    ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                    hooks.packet = e -> {
                        if (e.isPre() && e.isOutbound() && e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                            if (throwing) throw new IllegalStateException("synthetic PRE failure"); else e.cancel();
                        }
                    };
                    Pulsar.BUS.subscribe(hooks);
                    try {
                        List<Object> packets;
                        try { packets = r.f.walk(); assertFalse(throwing); }
                        catch (IllegalStateException expected) { assertTrue(throwing); packets = r.f.run.drain(); }
                        assertNull(r.player.getItemInUse());
                        assertEquals(0, count(packets, C08PacketPlayerBlockPlacement.class));
                        assertEquals(0, count(packets, C07PacketPlayerDigging.class));
                    } finally { Pulsar.BUS.unsubscribe(hooks); }
                }
            }
        }

        @Test public void acceptedReleaseClearsOldUseBeforeManualPostTakeover() throws Exception {
            for (boolean beginManualUse : new boolean[]{false,true}) {
                try (AuraMovementConsistencyTest.Run r = new AuraMovementConsistencyTest.Run("Einfach", AuraMovementConsistencyTest.SwordPlayer.class)) {
                    Aura aura = configure(r, "PlayerController"); aura.getBooleanSetting("Autoblock").setValue(true);
                    aura.getModeSetting("Autoblock Mode").setValue("Force");
                    r.f.walk(); ItemStack sword = r.player.getHeldItem(); assertSame(sword, r.player.getItemInUse());
                    final boolean[] changed = {false};
                    ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                    hooks.packet = e -> {
                        if (!changed[0] && e.isPost() && e.isOutbound() && e.getPacket() instanceof C07PacketPlayerDigging) {
                            changed[0] = true;
                            assertNull("the accepted old release is visible before POST", r.player.getItemInUse());
                            r.f.run.fixture.mc.gameSettings.keyBindUseItem.pressed = true;
                            if (beginManualUse) r.f.run.fixture.mc.playerController.sendUseItem(r.player, r.f.run.scene, sword);
                        }
                    };
                    Pulsar.BUS.subscribe(hooks);
                    try {
                        List<Object> packets = r.f.walk(); assertTrue(changed[0]);
                        assertEquals(1, count(packets, C07PacketPlayerDigging.class));
                        assertEquals(0, count(packets, C02PacketUseEntity.class));
                        assertEquals(beginManualUse ? 1 : 0, count(packets, C08PacketPlayerBlockPlacement.class));
                        if (beginManualUse) assertSame("a fresh manual use survives the old release completion", sword, r.player.getItemInUse());
                        else assertNull("held input can now start a fresh native use", r.player.getItemInUse());
                        assertTrue(r.f.run.fixture.mc.gameSettings.keyBindUseItem.isKeyDown());
                        assertFalse(aura.keepsAdaptiveSwordUse());
                    } finally {
                        Pulsar.BUS.unsubscribe(hooks);
                        r.f.run.fixture.mc.gameSettings.keyBindUseItem.pressed = false;
                    }
                }
            }
        }
    }
}
