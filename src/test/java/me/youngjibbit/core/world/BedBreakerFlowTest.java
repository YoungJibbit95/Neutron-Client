package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.youngjibbit.module.impl.world.NScaffold;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import org.junit.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Actual input hook, silent arbitration, native world ray and native controller transport. */
public class BedBreakerFlowTest {
    @Test public void nonLegitUsesSelectedBedAndOriginalMouseOverFace() throws Exception { run("nonLegitUsesSelectedBedAndOriginalMouseOverFace"); }
    @Test public void searchesAreThrottledAcrossRotationAndInputConsumers() throws Exception { run("searchesAreThrottledAcrossRotationAndInputConsumers"); }
    @Test public void sameTickDuplicatesAndToggleCyclesCannotMineTwice() throws Exception { run("sameTickDuplicatesAndToggleCyclesCannotMineTwice"); }
    @Test public void heldManualAttackTakesOverProgressAndDisableLeavesItAlone() throws Exception { run("heldManualAttackTakesOverProgressAndDisableLeavesItAlone"); }
    @Test public void releasedMissAndEntityClicksStillOwnTheirPlayerTick() throws Exception { run("releasedMissAndEntityClicksStillOwnTheirPlayerTick"); }
    @Test public void guiUseItemAndCompetingModulesWinBeforeRotationOrMining() throws Exception { run("guiUseItemAndCompetingModulesWinBeforeRotationOrMining"); }
    @Test public void originalDirectBedReachStaysFourAndAHalfInCreative() throws Exception { run("originalDirectBedReachStaysFourAndAHalfInCreative"); }
    @Test public void lateWallAndServerPoseChangesVetoStartAtTheTransportBoundary() throws Exception { run("lateWallAndServerPoseChangesVetoStartAtTheTransportBoundary"); }
    @Test public void lateDisableAndReenableCannotReviveTheOldAttempt() throws Exception { run("lateDisableAndReenableCannotReviveTheOldAttempt"); }
    @Test public void startPostDisableAbortsOnlyItsAcceptedMiningState() throws Exception { run("startPostDisableAbortsOnlyItsAcceptedMiningState"); }
    @Test public void sameIdentityUnloadDiscardsAndBlocksUntilLoad() throws Exception { run("sameIdentityUnloadDiscardsAndBlocksUntilLoad"); }
    @Test public void manualControllerRevisionWinsEvenAtTheSameBlock() throws Exception { run("manualControllerRevisionWinsEvenAtTheSameBlock"); }
    @Test public void heldStackReplacementCannotContinueOldStartOrStopWork() throws Exception { run("heldStackReplacementCannotContinueOldStartOrStopWork"); }
    @Test public void abortCallbacksCannotRecursivelyCreateAutomaticMining() throws Exception { run("abortCallbacksCannotRecursivelyCreateAutomaticMining"); }
    @Test public void instantBreakAndDestructionCallbacksDoNotLeakOrClearReplacementMining() throws Exception { run("instantBreakAndDestructionCallbacksDoNotLeakOrClearReplacementMining"); }
    @Test public void smartDisableOptionsRemainOptInAndUseNormalCleanup() throws Exception { run("smartDisableOptionsRemainOptInAndUseNormalCleanup"); }
    @Test public void animationCallbackManualTakeoverPreventsTheOldAutomaticSwing() throws Exception { run("animationCallbackManualTakeoverPreventsTheOldAutomaticSwing"); }
    @Test public void missingPicksWaitAndNonLegitRetainsTheMouseFace() throws Exception { run("missingPicksWaitAndNonLegitRetainsTheMouseFace"); }
    @Test public void toolChangeBetweenTicksAbortsOwnedWorkAndCanResumeNormally() throws Exception { run("toolChangeBetweenTicksAbortsOwnedWorkAndCanResumeNormally"); }
    @Test public void unloadDuringAbortClearsLocalProgressWithoutReenteringTheOldWorld() throws Exception { run("unloadDuringAbortClearsLocalProgressWithoutReenteringTheOldWorld"); }
    @Test public void failedActionCleanupKeepsItsOwnLifetimeThroughAbortCallbacks() throws Exception { run("failedActionCleanupKeepsItsOwnLifetimeThroughAbortCallbacks"); }
    private static void run(String name) throws Exception { Wave2IsolatedCase.run(Checks.class, name); }
    private static long count(List<C07PacketPlayerDigging> packets, C07PacketPlayerDigging.Action action) {
        return packets.stream().filter(packet -> packet.getStatus() == action).count();
    }
    public static class Checks {
        @Test public void failedActionCleanupKeepsItsOwnLifetimeThroughAbortCallbacks() throws Exception {
            for (boolean unload : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); AtomicBoolean aborted = new AtomicBoolean();
                // START is accepted, then the block callback invalidates action permission.
                // The scene remains valid until the optional nested ABORT-PRE unload.
                f.bed.clicked = () -> f.f.mc.inGameHasFocus = false;
                f.observer.action = event -> {
                    if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
                        aborted.set(true); if (unload) Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                    }
                };
                f.input(false); assertTrue(aborted.get()); assertSame(f.world, f.f.mc.theWorld);
                assertFalse(f.controller.func_181040_m()); assertEquals(0, f.progress(), 0); assertEquals(0, f.world.removals);
                assertEquals("old-world progress callback must be suppressed", unload ? 0 : 1, f.world.cracks);
                List<C07PacketPlayerDigging> packets = f.digs();
                assertEquals(1, count(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(unload ? 0 : 1, count(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
            }
        }
        @Test public void unloadDuringAbortClearsLocalProgressWithoutReenteringTheOldWorld() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.input(false); f.digs(); int callbacks = f.world.cracks; AtomicBoolean unloaded = new AtomicBoolean();
                f.observer.action = event -> {
                    if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
                        unloaded.set(true); Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                    }
                };
                f.module.setToggled(false); assertTrue(unloaded.get()); assertFalse(f.controller.func_181040_m());
                assertEquals(callbacks, f.world.cracks); assertTrue(f.digs().isEmpty());
            }
        }
        @Test public void toolChangeBetweenTicksAbortsOwnedWorkAndCanResumeNormally() throws Exception {
            for (boolean slot : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.f.player.inventory.mainInventory[0] = new ItemStack(Items.iron_axe); f.input(false); f.digs();
                f.next(); int selected = slot ? 1 : 0; f.f.player.inventory.mainInventory[selected] = new ItemStack(Items.iron_pickaxe);
                f.f.player.inventory.currentItem = selected; f.input(false);
                assertFalse(f.controller.func_181040_m()); assertEquals(1, count(f.digs(), C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
                f.input(false); assertEquals(1, f.controller.automaticCalls); f.next(); f.input(false);
                assertEquals(2, f.controller.automaticCalls); assertTrue(f.controller.isMiningBlock(f.pos));
            }
        }
        @Test public void missingPicksWaitAndNonLegitRetainsTheMouseFace() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.module.legit.setValue(false);
                f.aim();
                for (int kind = 0; kind < 3; kind++) {
                    f.f.mc.objectMouseOver = kind == 0 ? null : kind == 1
                            ? new MovingObjectPosition((Entity) f.f.player, f.f.player.getPositionVector())
                            : new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, new Vec3(0, 0, 0), EnumFacing.UP, BlockPos.ORIGIN);
                    f.input(false); assertEquals(kind == 2 ? 1 : 0, f.controller.automaticCalls);
                    assertEquals(kind == 2, f.controller.isMiningBlock(f.pos));
                    f.next();
                }
                assertEquals(1, count(f.digs(), C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
            }
        }
        @Test public void animationCallbackManualTakeoverPreventsTheOldAutomaticSwing() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim();
                f.observer.action = event -> {
                    if (event.isOutbound() && event.isPre() && event.getPacket() instanceof net.minecraft.network.play.client.C0APacketAnimation)
                        f.controller.onPlayerDamageBlock(f.pos, EnumFacing.NORTH);
                };
                f.input(false); assertTrue(f.progress() > 0);
                Object packet; int starts = 0;
                while ((packet = f.f.channel.readOutbound()) != null) {
                    assertFalse("old attempt cannot swing after manual takeover", packet instanceof net.minecraft.network.play.client.C0APacketAnimation);
                    if (packet instanceof C07PacketPlayerDigging) starts++;
                }
                assertEquals(1, starts); float progress = f.progress(); f.module.setToggled(false);
                assertTrue(f.controller.isMiningBlock(f.pos)); assertEquals(progress, f.progress(), 0); assertTrue(f.digs().isEmpty());
            }
        }
        @Test public void nonLegitUsesSelectedBedAndOriginalMouseOverFace() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.module.legit.setValue(false); f.aim();
                assertTrue(f.f.runtime.getRotationManager().getEffectivePitch() > 0); assertEquals(0, f.controller.automaticCalls);
                f.f.mc.objectMouseOver = new MovingObjectPosition(new Vec3(.5, 64, 2.5), EnumFacing.DOWN, f.pos);
                f.input(false); assertEquals(1, f.controller.automaticCalls);
                List<C07PacketPlayerDigging> packets = f.digs(); assertEquals(1, packets.size());
                assertEquals(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, packets.get(0).getStatus());
                assertEquals(f.pos, packets.get(0).getPosition()); assertEquals(EnumFacing.DOWN, packets.get(0).getFacing());
                assertTrue(f.controller.isMiningBlock(f.pos)); assertEquals(BedBreaker.Status.MINING, f.module.getStatus());
                assertFalse(f.f.mc.gameSettings.keyBindAttack.isKeyDown()); assertFalse(f.f.mc.gameSettings.keyBindUseItem.isKeyDown());
            }
        }
        @Test public void searchesAreThrottledAcrossRotationAndInputConsumers() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.world.blocks.clear(); f.update(); int first = f.world.loadedQueries; assertTrue(first > 0);
                f.input(false); f.input(false); assertEquals(first, f.world.loadedQueries);
                f.next(); f.rotation(); f.input(false); assertEquals(first, f.world.loadedQueries);
                f.next(); f.update(); assertEquals(first * 2, f.world.loadedQueries); assertEquals(0, f.controller.automaticCalls);
            }
        }
        @Test public void sameTickDuplicatesAndToggleCyclesCannotMineTwice() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.input(false); f.input(false); assertEquals(1, f.controller.automaticCalls);
                assertEquals(0, f.progress(), 0); f.module.setToggled(false); f.module.setToggled(true);
                f.input(false); assertEquals(1, f.controller.automaticCalls);
                // The next even Update can reuse the still-valid accepted bed pose.
                f.next(); f.update(); assertEquals(2, f.controller.automaticCalls);
                f.input(false); assertEquals(2, f.controller.automaticCalls);
                assertEquals(2, count(f.digs(), C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
            }
        }
        @Test public void heldManualAttackTakesOverProgressAndDisableLeavesItAlone() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.input(false); f.digs();
                f.f.mc.objectMouseOver = f.world.ray(f.f.player.getPositionEyes(1), new Vec3(.5, 64.3, 2.5));
                f.input(true); assertTrue(f.progress() > 0); float progress = f.progress(); f.module.setToggled(false);
                assertTrue(f.controller.isMiningBlock(f.pos)); assertEquals(progress, f.progress(), 0); assertTrue(f.digs().isEmpty());
            }
        }
        @Test public void releasedMissAndEntityClicksStillOwnTheirPlayerTick() throws Exception {
            for (boolean entity : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.f.mc.objectMouseOver = entity ? new MovingObjectPosition((Entity) f.f.player, f.f.player.getPositionVector())
                        : new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, new Vec3(0, 0, 0), EnumFacing.UP, BlockPos.ORIGIN);
                f.f.mc.clickMouse(); f.input(false); assertEquals(0, f.controller.automaticCalls); assertTrue(f.digs().isEmpty());
                assertEquals(entity ? 1 : 0, f.controller.manualAttacks);
                f.next(); f.update(); assertEquals(1, f.controller.automaticCalls);
                f.input(false); assertEquals(1, f.controller.automaticCalls);
            }
        }
        @Test public void guiUseItemAndCompetingModulesWinBeforeRotationOrMining() throws Exception {
            for (int choice = 0; choice < 6; choice++) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                if (choice == 0) f.f.mc.currentScreen = RotationBehaviorHookTest.bare(net.minecraft.client.gui.GuiChat.class);
                if (choice == 1) f.f.player.usingItem = true;
                if (choice == 2) f.f.mc.inGameHasFocus = false;
                if (choice == 3) BedBreakerTestSupport.field(Module.class, "toggled").setBoolean(f.f.scaffold, true);
                if (choice == 4) { NScaffold scaffold = new NScaffold(); BedBreakerTestSupport.field(Module.class, "toggled").setBoolean(scaffold, true); f.modules.put(NScaffold.class, scaffold); }
                if (choice == 5) {
                    TargetEspLifecycleTest.CallbackAura aura = new TargetEspLifecycleTest.CallbackAura();
                    aura.observed = RotationBehaviorHookTest.bare(TargetEspLifecycleTest.Candidate.class);
                    BedBreakerTestSupport.field(Module.class, "toggled").setBoolean(aura, true); f.modules.put(Aura.class, aura);
                }
                f.rotation(); f.input(false); assertEquals(0, f.controller.automaticCalls);
                assertEquals(0, f.f.player.rotationYaw, 0); assertEquals(0, f.f.player.rotationPitch, 0); assertTrue(f.digs().isEmpty());
            }
        }
        @Test public void originalDirectBedReachStaysFourAndAHalfInCreative() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.module.legit.setValue(false);
                f.world.blocks.put(f.pos, Blocks.stone.getDefaultState()); f.input(false); assertEquals(0, f.controller.automaticCalls);
                f.world.blocks.clear(); BlockPos far = new BlockPos(5, 65, 0); f.world.blocks.put(far, f.bed.getDefaultState());
                f.f.player.posY = 63.68; f.next(); f.next(); f.input(false); assertEquals(0, f.controller.automaticCalls);
                f.controller.setGameType(WorldSettings.GameType.CREATIVE); f.next(); f.next(); f.aim(); f.input(false);
                assertEquals(0, f.controller.automaticCalls); assertEquals(0, f.world.removals); assertFalse(f.controller.func_181040_m());
                assertTrue(f.world.blocks.containsKey(far));
            }
        }
        @Test public void lateWallAndServerPoseChangesVetoStartAtTheTransportBoundary() throws Exception {
            for (boolean wall : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); AtomicBoolean fired = new AtomicBoolean();
                f.observer.action = event -> {
                    if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
                            && fired.compareAndSet(false, true)) {
                        if (wall) f.world.blocks.put(new BlockPos(0, 65, 1), Blocks.stone.getDefaultState()); else f.f.runtime.getRotationManager().observeOutboundRotation(90, 0);
                    }
                };
                f.input(false); assertTrue(fired.get()); assertTrue(f.digs().isEmpty());
                assertEquals(0, f.world.removals); assertFalse(f.controller.func_181040_m());
            }
        }
        @Test public void lateDisableAndReenableCannotReviveTheOldAttempt() throws Exception {
            for (boolean reenable : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); AtomicBoolean fired = new AtomicBoolean();
                f.observer.action = event -> {
                    if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging && fired.compareAndSet(false, true)) {
                        f.module.setToggled(false); if (reenable) f.module.setToggled(true);
                    }
                };
                f.input(false); assertTrue(fired.get()); assertTrue(f.digs().isEmpty());
                f.input(false); assertTrue(f.digs().isEmpty()); assertEquals(1, f.controller.automaticCalls);
            }
        }
        @Test public void startPostDisableAbortsOnlyItsAcceptedMiningState() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.observer.action = event -> {
                    if (!event.isInbound() && !event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) f.module.setToggled(false);
                };
                f.input(false); List<C07PacketPlayerDigging> packets = f.digs();
                assertEquals(1, count(packets, C07PacketPlayerDigging.Action.START_DESTROY_BLOCK));
                assertEquals(1, count(packets, C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
                assertFalse(f.module.isToggled()); assertFalse(f.controller.func_181040_m()); assertEquals(0, f.progress(), 0);
            }
        }
        @Test public void sameIdentityUnloadDiscardsAndBlocksUntilLoad() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.world.afterRay = () -> Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                // The current mouse-over is already known. Unload occurs in the native
                // controller's validating ray, before its START may reach transport.
                f.input(false); assertSame(f.world, f.f.mc.theWorld); assertEquals(1, f.controller.automaticCalls);
                assertFalse(f.controller.func_181040_m()); assertTrue(f.digs().isEmpty());
                f.next(); f.next(); f.input(false); assertEquals(1, f.controller.automaticCalls);
                Pulsar.BUS.post(new WorldEvent.Load(f.world)); f.aim(); f.input(false); assertEquals(2, f.controller.automaticCalls);
                f.digs(); Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                assertFalse(f.controller.func_181040_m()); assertTrue("unload does not replay old mining packets", f.digs().isEmpty());
            }
        }
        @Test public void manualControllerRevisionWinsEvenAtTheSameBlock() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); AtomicBoolean fired = new AtomicBoolean();
                f.observer.action = event -> {
                    if (!event.isInbound() && !event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
                            && fired.compareAndSet(false, true)) f.controller.onPlayerDamageBlock(f.pos, EnumFacing.NORTH);
                };
                f.input(false); assertTrue(fired.get()); assertTrue(f.progress() > 0); float progress = f.progress(); f.digs();
                f.module.setToggled(false); assertTrue(f.controller.isMiningBlock(f.pos));
                assertEquals(progress, f.progress(), 0); assertTrue(f.digs().isEmpty());
            }
        }
        @Test public void heldStackReplacementCannotContinueOldStartOrStopWork() throws Exception {
            for (int phase = 0; phase < 3; phase++) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); ItemStack old = new ItemStack(Items.iron_axe), replacement = new ItemStack(Items.diamond_axe);
                f.f.player.inventory.mainInventory[0] = old; AtomicBoolean fired = new AtomicBoolean();
                final C07PacketPlayerDigging.Action at = phase == 1 ? C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
                        : C07PacketPlayerDigging.Action.START_DESTROY_BLOCK;
                if (phase == 2) f.bed.clicked = () -> { fired.set(true); f.f.player.inventory.mainInventory[0] = replacement; };
                else f.observer.action = event -> {
                    if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == at && fired.compareAndSet(false, true))
                        f.f.player.inventory.mainInventory[0] = replacement;
                };
                for (int tick = 0; tick < (phase == 1 ? 5 : 1); tick++) { f.input(false); f.next(); }
                assertTrue(fired.get()); assertSame(replacement, f.f.player.inventory.getCurrentItem());
                assertEquals(0, old.getItemDamage()); assertEquals(0, replacement.getItemDamage()); assertEquals(0, f.world.removals);
                List<C07PacketPlayerDigging> packets = f.digs(); assertEquals(0, count(packets, C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK));
                if (phase == 0) assertTrue(packets.isEmpty()); assertFalse(f.controller.func_181040_m());
            }
        }
        @Test public void abortCallbacksCannotRecursivelyCreateAutomaticMining() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.aim(); f.input(false); f.digs(); final long revision = f.controller.getMiningRevision(); final int[] calls = {0};
                f.observer.action = event -> {
                    if (!event.isInbound() && event.isPre() && event.getPacket() instanceof C07PacketPlayerDigging
                            && ((C07PacketPlayerDigging) event.getPacket()).getStatus() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
                        calls[0]++; assertFalse(f.controller.resetBlockRemoving(() -> true));
                        assertFalse(f.controller.onPlayerDamageBlock(f.pos, EnumFacing.NORTH, f.bed.getDefaultState(), () -> true, () -> true));
                    }
                };
                assertTrue(f.controller.resetBlockRemoving(f.pos, revision, () -> true));
                assertEquals(1, calls[0]); assertFalse(f.controller.func_181040_m());
                assertEquals(1, count(f.digs(), C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
            }
        }
        @Test public void instantBreakAndDestructionCallbacksDoNotLeakOrClearReplacementMining() throws Exception {
            for (boolean manual : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                f.bed.hardness = 1; f.aim(); BlockPos other = new BlockPos(2, 64, 2);
                BedBreakerTestSupport.Bed manualBed = new BedBreakerTestSupport.Bed(); f.world.blocks.put(other, manualBed.getDefaultState());
                if (manual) f.world.beforeRemoval = () -> f.controller.clickBlock(other, EnumFacing.NORTH);
                f.input(false); assertEquals(1, f.world.removals);
                assertFalse(f.controller.isMiningBlock(f.pos)); assertEquals(manual, f.controller.isMiningBlock(other));
                f.digs(); f.module.setToggled(false); assertEquals(manual, f.controller.isMiningBlock(other)); assertTrue(f.digs().isEmpty());
            }
        }
        @Test public void smartDisableOptionsRemainOptInAndUseNormalCleanup() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                assertFalse(f.module.getBooleanSetting("Smart Disable on Death").getValue());
                assertFalse(f.module.getBooleanSetting("Smart Disable on World Change").getValue());
                f.module.getBooleanSetting("Smart Disable on Death").setValue(true); f.aim(); f.input(false); f.digs();
                f.f.player.isDead = true; Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
                assertFalse(f.module.isToggled()); assertFalse(f.controller.func_181040_m());
                assertEquals(1, count(f.digs(), C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK));
            }
        }
    }
}
