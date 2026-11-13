package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.combat.FakeLag;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** Real ownership/queue boundaries with isolated synthetic client state; no server is contacted. */
public class OperationAbortLifecycleTest {
    @Test public void abortLabelsKeepLegacyIdsAndOtherConsumersKeepTheirPolicy() throws Exception { run("abortLabelsKeepLegacyIdsAndOtherConsumersKeepTheirPolicy"); }
    @Test public void repeatedDamageAndNestedEventsCannotDisableOrRestartTheOperation() throws Exception { run("repeatedDamageAndNestedEventsCannotDisableOrRestartTheOperation"); }
    @Test public void timingConflictAbortsTheNewerOperationWithoutChangingActivationOrder() throws Exception { run("timingConflictAbortsTheNewerOperationWithoutChangingActivationOrder"); }
    @Test public void backtrackDamageDrainsItsQueueAndRequiresANewAcquisition() throws Exception { run("backtrackDamageDrainsItsQueueAndRequiresANewAcquisition"); }
    @Test public void tickbaseDamageDiscardsCreditAndRequiresAFreshFrame() throws Exception { run("tickbaseDamageDiscardsCreditAndRequiresAFreshFrame"); }
    @Test public void tickbasePacketAbortCannotReopenFromReleasedPacketCallbacks() throws Exception { run("tickbasePacketAbortCannotReopenFromReleasedPacketCallbacks"); }
    @Test public void targetChangeReleaseCannotRestoreWorldUnloadedByItsCallback() throws Exception { run("targetChangeReleaseCannotRestoreWorldUnloadedByItsCallback"); }
    @Test public void targetChangeReleaseCannotRestoreAnObsoleteConnectionEpoch() throws Exception { run("targetChangeReleaseCannotRestoreAnObsoleteConnectionEpoch"); }
    @Test public void lagRangeDamageReleasesMovementAndWaitsForRecovery() throws Exception { run("lagRangeDamageReleasesMovementAndWaitsForRecovery"); }
    @Test public void damageAbortKeepsUnpublishedLookBarrierUntilMovementCompletes() throws Exception { run("damageAbortKeepsUnpublishedLookBarrierUntilMovementCompletes"); }
    @Test public void inFlightLagRangeAbortCannotContinueOrPublishOldActivity() throws Exception { run("inFlightLagRangeAbortCannotContinueOrPublishOldActivity"); }
    @Test public void worldUnloadClearsReferencesAndBlocksTheStillVisibleOldScene() throws Exception { run("worldUnloadClearsReferencesAndBlocksTheStillVisibleOldScene"); }

    private static void run(String name) throws Exception { Wave2IsolatedCase.run(Checks.class, name); }

    @ModuleInfo(name = "Abort Safety Fixture", category = Category.Player)
    public static final class AbortGuarded extends Module {
        int aborts, disables, actions;
        Runnable duringAbort;
        AbortGuarded(Minecraft client) { enableActionSafety(() -> client, reason -> {
            aborts++;
            if (duringAbort != null) duringAbort.run();
        }); }
        boolean attempt() { if (!actionSafetyAllows()) return false; actions++; return true; }
        @Override public void onDisable() { disables++; super.onDisable(); }
    }

    public static class Checks {
        private static void health(ExploitHookFixture f, float amount) throws Exception {
            Bootstrap.register();
            DataWatcher data = f.player.getDataWatcher();
            if (data == null) {
                data = new DataWatcher(f.player); data.addObject(6, amount);
                Field field = Entity.class.getDeclaredField("dataWatcher"); field.setAccessible(true); field.set(f.player, data);
            } else data.updateObject(6, amount);
        }
        private static Object field(Object owner, String name) throws Exception {
            Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
        }
        private static Object guard(Module owner) throws Exception {
            Field field = Module.class.getDeclaredField("actionSafety"); field.setAccessible(true); return field.get(owner);
        }
        private static void tick() { Pulsar.BUS.post(new TickEvent()); }
        private static S14PacketEntity move() { return new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 1, (byte) 0, (byte) 0, true); }
        private static void damageEnabled(Module module) { module.getBooleanSetting("Abort on Damage").setValue(true); }
        private static Backtrack backtrack(ExploitHookFixture f, AtomicLong now) {
            Backtrack module = new Backtrack(now::get, f.client, (min, max) -> min);
            module.getBooleanSetting("Only Aura").setValue(false);
            module.getBooleanSetting("Diagnostics").setValue(true);
            module.getModeSetting("Backtrack Mode").setValue("Timed");
            module.getModeSetting("Target Source").setValue("Attack");
            module.getNumberSetting("Cooldown Ticks").setValue(0D);
            damageEnabled(module); module.setToggled(true); return module;
        }
        private static int body(ExploitHookFixture f) {
            AtomicInteger count = new AtomicInteger();
            Tickbase.runPlayerStep(f.player, () -> { count.incrementAndGet(); return true; });
            return count.get();
        }

        @Test public void abortLabelsKeepLegacyIdsAndOtherConsumersKeepTheirPolicy() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                for (Module module : Arrays.asList(new Backtrack(() -> 0, f.client), new Tickbase(() -> 0, f.client), new LagRange(() -> 0, null))) {
                    assertSame(module.getBooleanSetting("Stop on Damage"), module.getBooleanSetting("Abort on Damage"));
                    assertEquals("stop_on_damage", module.getBooleanSetting("Abort on Damage").getId());
                    assertEquals("Abort on Damage", module.getBooleanSetting("Stop on Damage").getName());
                    damageEnabled(module); module.beginConfigUpdate(); module.endConfigUpdate();
                    assertFalse(module.getBooleanSetting("Abort on Damage").getValue());
                }
                Backtrack backtrack = new Backtrack(() -> 0, f.client);
                assertSame(backtrack.getBooleanSetting("Release On Velocity"), backtrack.getBooleanSetting("Abort on Knockback"));
                assertSame(backtrack.getBooleanSetting("Release on local knockback"), backtrack.getBooleanSetting("release_on_velocity"));
                FakeLag fakeLag = new FakeLag();
                assertEquals("Stop on Damage", fakeLag.getBooleanSetting("Stop on Damage").getName());
            }
        }

        @Test public void repeatedDamageAndNestedEventsCannotDisableOrRestartTheOperation() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                health(f, 20); AbortGuarded module = new AbortGuarded(f.client); damageEnabled(module); module.setToggled(true);
                AtomicInteger toggles = new AtomicInteger(); module.setPersistenceListener(changed -> { if (changed) toggles.incrementAndGet(); });
                try {
                    assertTrue(module.attempt());
                    module.duringAbort = () -> { tick(); Pulsar.BUS.post(new EventUpdate()); assertFalse(module.attempt()); };
                    health(f, 15); assertFalse(module.attempt());
                    assertFalse("health-only edge cannot reopen during the current tick", module.attempt());
                    f.player.hurtTime = 2; tick(); tick(); assertFalse(module.attempt());
                    assertEquals(1, module.aborts); assertEquals(0, module.disables); assertEquals(0, toggles.get()); assertTrue(module.isToggled());
                    f.player.hurtTime = 0; tick(); assertTrue(module.attempt());
                    health(f, 14); assertFalse(module.attempt()); assertEquals(2, module.aborts);
                    module.setToggled(false); assertEquals(1, module.disables); assertEquals(1, toggles.get());
                    module.setToggled(true); assertTrue(module.attempt());
                } finally { module.setToggled(false); }
            }
        }

        @Test public void timingConflictAbortsTheNewerOperationWithoutChangingActivationOrder() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                AbortGuarded older = new AbortGuarded(f.client), newer = new AbortGuarded(f.client);
                newer.getBooleanSetting("Exclusive Timing").setValue(true); older.setToggled(true); newer.setToggled(true);
                try {
                    assertFalse(newer.attempt()); tick(); assertFalse(newer.attempt());
                    assertEquals(1, newer.aborts); assertTrue(newer.isToggled()); assertTrue(older.attempt());
                    assertEquals(0, newer.disables); assertEquals(0, older.aborts);
                    older.setToggled(false); tick(); assertTrue(newer.attempt());
                    older.setToggled(true); assertFalse(older.attempt());
                    assertEquals("waiting did not silently re-enable/reorder the newer module", 0, newer.disables);
                    assertTrue(newer.attempt());
                } finally { newer.setToggled(false); older.setToggled(false); }
            }
        }

        @Test public void backtrackDamageDrainsItsQueueAndRequiresANewAcquisition() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                AtomicLong now = new AtomicLong(); NetworkTestSupport.clock(f, now); health(f, 20);
                Backtrack module = backtrack(f, now); NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer();
                AtomicBoolean callback = new AtomicBoolean(); Pulsar.BUS.subscribe(observer);
                try {
                    ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                    assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true)); f.receive(move());
                    assertEquals(1, f.pipe().delays().size());
                    observer.action = event -> { if (event.isInbound() && event.isPost() && event.getPacket() instanceof S14PacketEntity
                            && callback.compareAndSet(false, true)) tick(); };
                    health(f, 15); Pulsar.BUS.post(new EventUpdate());
                    assertTrue(callback.get()); assertTrue(module.isToggled()); assertEquals(0, f.pipe().delays().size());
                    assertEquals(97, enemy.serverPosX); assertEquals(-1, module.getDiagnostics().targetId);
                    assertEquals(0, f.pipe().delays().snapshot(this).activeOwners);
                    f.receive(move()); tick(); f.receive(move());
                    assertEquals("a retained old attack must not re-acquire the target", 0, f.pipe().delays().size());
                    assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true)); f.receive(move());
                    assertEquals("normal fresh acquisition works without retoggling", 1, f.pipe().delays().size());
                } finally { Pulsar.BUS.unsubscribe(observer); module.setToggled(false); }
            }
        }

        @Test public void tickbaseDamageDiscardsCreditAndRequiresAFreshFrame() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                health(f, 20); Tickbase module = new Tickbase(() -> 0, f.client);
                module.getBooleanSetting("Only Aura").setValue(false); module.getBooleanSetting("Debug").setValue(true);
                module.getNumberSetting("Min Charge").setValue(1D); damageEnabled(module); module.setToggled(true);
                try {
                    ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy);
                    Tickbase.beginFrame(f.client, false, 1, 50); assertEquals(0, body(f));
                    assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true)); assertTrue(module.getDiagnostics().releasing);
                    health(f, 15); Pulsar.BUS.post(new EventUpdate());
                    assertTrue(module.isToggled()); assertEquals(0, module.getDiagnostics().credit); assertFalse(module.getDiagnostics().releasing);
                    TickbaseBudget budget = (TickbaseBudget) field(module, "budget");
                    assertNull(field(budget, "player")); assertNull(field(budget, "world")); assertNull(field(budget, "connection"));
                    assertEquals(1, body(f)); tick(); assertEquals("next tick alone is not an earned frame", 1, body(f));
                    Tickbase.beginFrame(f.client, false, 1, 50); assertEquals(0, body(f));
                    assertEquals(1, module.getDiagnostics().credit);
                } finally { module.setToggled(false); }
            }
        }

        @Test public void tickbasePacketAbortCannotReopenFromReleasedPacketCallbacks() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                health(f, 20); AtomicLong now = new AtomicLong(); NetworkTestSupport.clock(f, now);
                Tickbase module = new Tickbase(now::get, f.client); module.getBooleanSetting("Only Aura").setValue(false);
                module.getModeSetting("Mode").setValue("Demise Pulse"); damageEnabled(module); module.setToggled(true);
                NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer(); AtomicBoolean callback = new AtomicBoolean();
                Pulsar.BUS.subscribe(observer);
                try {
                    f.cursor(f.enemy(7, 5)); Tickbase.beginFrame(f.client, false, 1, 50);
                    C03PacketPlayer first = new C03PacketPlayer(true); f.connection.network.sendPacket(first);
                    assertEquals(1, f.pipe().delays().size());
                    observer.action = event -> { if (event.isPost() && event.getPacket() == first && callback.compareAndSet(false, true)) {
                        module.setToggled(false); module.setToggled(true);
                        tick(); Tickbase.beginFrame(f.client, false, 1, 50);
                    } };
                    health(f, 15); Pulsar.BUS.post(new EventUpdate());
                    assertTrue(callback.get()); assertSame(first, f.connection.channel.readOutbound()); assertTrue(module.isToggled());
                    assertEquals(0, f.pipe().delays().snapshot(this).activeOwners); assertEquals(0, f.pipe().delays().size());
                    assertNull(field(module, "sampledTarget")); assertNull(field(module, "connection"));
                    Tickbase.beginFrame(f.client, false, 1, 50);
                    assertNull("reactivation inside cleanup still requires a later fresh tick", field(module, "connection"));
                    tick(); Tickbase.beginFrame(f.client, false, 1, 50);
                    C03PacketPlayer second = new C03PacketPlayer(true); f.connection.network.sendPacket(second);
                    assertEquals("new current frame may establish a separate window", 1, f.pipe().delays().size());
                } finally { Pulsar.BUS.unsubscribe(observer); module.setToggled(false); }
            }
        }

        @Test public void lagRangeDamageReleasesMovementAndWaitsForRecovery() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                health(f, 20); AtomicLong now = new AtomicLong(); NetworkTestSupport.clock(f, now);
                LagRange module = new LagRange(now::get, (min, max) -> min); module.getBooleanSetting("Only Aura").setValue(false);
                module.getNumberSetting("Release Cooldown MS").setValue(0D); damageEnabled(module); module.setToggled(true);
                try {
                    f.enemy(7, 6); tick(); C03PacketPlayer first = new C03PacketPlayer(true); f.connection.network.sendPacket(first);
                    assertEquals(1, f.pipe().delays().size());
                    f.player.hurtTime = 2; Pulsar.BUS.post(new EventUpdate());
                    assertTrue(module.isToggled()); assertSame(first, f.connection.channel.readOutbound());
                    assertEquals(0, f.pipe().delays().size()); assertNull(field(module, "windowTarget"));
                    tick(); C03PacketPlayer during = new C03PacketPlayer(true); f.connection.network.sendPacket(during);
                    assertSame(during, f.connection.channel.readOutbound()); assertNull(module.getActivitySnapshot());
                    f.player.hurtTime = 0; tick(); C03PacketPlayer after = new C03PacketPlayer(true); f.connection.network.sendPacket(after);
                    assertNull(f.connection.channel.readOutbound()); assertEquals(1, f.pipe().delays().size());
                } finally { module.setToggled(false); }
            }
        }

        @Test public void targetChangeReleaseCannotRestoreWorldUnloadedByItsCallback() throws Exception {
            targetChangeReleasePreservesInvalidation(true);
        }

        @Test public void targetChangeReleaseCannotRestoreAnObsoleteConnectionEpoch() throws Exception {
            targetChangeReleasePreservesInvalidation(false);
        }

        private static void targetChangeReleasePreservesInvalidation(boolean unload) throws Exception {
            for (boolean lagRange : new boolean[]{false, true}) {
                try (ExploitHookFixture f = new ExploitHookFixture()) {
                    health(f, 20); AtomicLong now = new AtomicLong(); NetworkTestSupport.clock(f, now);
                    Module module = lagRange ? new LagRange(now::get, (min, max) -> min) : new Tickbase(now::get, f.client);
                    module.getBooleanSetting("Only Aura").setValue(false);
                    if (lagRange) module.getNumberSetting("Release Cooldown MS").setValue(0D);
                    else module.getModeSetting("Mode").setValue("Demise Pulse");
                    module.setToggled(true);
                    NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer(); AtomicBoolean callback = new AtomicBoolean();
                    Pulsar.BUS.subscribe(observer);
                    try {
                        ExploitHookFixture.Enemy firstTarget = f.enemy(7, 6); f.cursor(firstTarget);
                        if (lagRange) tick(); else Tickbase.beginFrame(f.client, false, 1, 50);
                        String targetField = lagRange ? "windowTarget" : "sampledTarget";
                        assertSame(firstTarget, field(module, targetField));
                        C03PacketPlayer movement = new C03PacketPlayer(true); f.connection.network.sendPacket(movement);
                        assertEquals("positive control: real movement is queued", 1, f.pipe().delays().size());
                        long epoch = f.pipe().getContextVersion();
                        observer.action = event -> {
                            if (event.isPost() && event.isOutbound() && event.getPacket() == movement && callback.compareAndSet(false, true)) {
                                f.pipe().worldChanged();
                                if (unload) Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                            }
                        };
                        f.cursor(f.enemy(8, 5.7));
                        if (lagRange) tick(); else Tickbase.beginFrame(f.client, false, 1, 50);
                        assertTrue("target change released the queued C03", callback.get());
                        assertTrue(f.pipe().getContextVersion() > epoch); assertSame(movement, f.connection.channel.readOutbound());
                        assertTrue(module.isToggled()); assertNull(field(module, "connection")); assertNull(field(module, targetField));
                        assertNull(field(module, lagRange ? "player" : "boundPlayer"));
                        assertNull(field(module, lagRange ? "world" : "boundWorld"));
                        assertFalse(f.pipe().combatTiming().owns(module));
                        assertEquals(0, f.pipe().delays().size()); assertEquals(0, f.pipe().delays().snapshot(module).activeOwners);
                        if (unload) {
                            tick(); Tickbase.beginFrame(f.client, false, 1, 50); LagRange.beginFrame(f.client, false, 1, 50);
                            assertNull("old client pointers cannot rebind while unloaded", field(module, "connection"));
                            assertNull(field(module, targetField));
                        }
                    } finally { Pulsar.BUS.unsubscribe(observer); module.setToggled(false); }
                }
            }
        }

        @Test public void damageAbortKeepsUnpublishedLookBarrierUntilMovementCompletes() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                health(f, 20); f.player.ticksExisted = 120; f.enemy(7, 3);
                LagRange module = new LagRange(() -> 0, (min, max) -> min);
                module.getBooleanSetting("Only Aura").setValue(false); damageEnabled(module); module.setToggled(true);
                try {
                    tick(); LagRange.beginFrame(f.client, false, 1, 50);
                    LagRange.runPlayerStep(f.player, () -> { fail("positive control: a player body was withheld"); return true; });
                    f.client.objectMouseOver = new net.minecraft.util.MovingObjectPosition(new net.minecraft.util.Vec3(1, 1, 0),
                            net.minecraft.util.EnumFacing.WEST, new net.minecraft.util.BlockPos(1, 1, 0));
                    health(f, 15); Pulsar.BUS.post(new EventUpdate());
                    assertTrue(module.isToggled()); assertFalse("abort has not published the withheld look", LagRange.allowBlockAttack());
                    f.connection.network.sendPacket(new C03PacketPlayer(true));
                    assertTrue("completed current-scene movement is observed while suspended", LagRange.allowBlockAttack());
                } finally { module.setToggled(false); }
            }
        }

        @Test public void inFlightLagRangeAbortCannotContinueOrPublishOldActivity() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                health(f, 20); AtomicLong now = new AtomicLong(); NetworkTestSupport.clock(f, now); AtomicInteger extras = new AtomicInteger();
                f.player.ticksExisted = 120; f.player.movementInput = new net.minecraft.util.MovementInput();
                f.player.movementInput.moveForward = 1; f.player.rotationYaw = -90;
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                LagRange module = new LagRange(now::get, (min, max) -> min);
                module.getBooleanSetting("Only Aura").setValue(false); damageEnabled(module); module.setToggled(true);
                try {
                    tick();
                    for (int i = 0; i < 3; i++) {
                        now.set(i * 50_000_000L); LagRange.beginFrame(f.client, false, 1, 50);
                        LagRange.runPlayerStep(f.player, () -> { fail("positive control: earn withheld credit"); return true; });
                    }
                    enemy.posX = 3.7; enemy.hurtTime = 0;
                    enemy.setEntityBoundingBox(new net.minecraft.util.AxisAlignedBB(3.4, 0, -.3, 4, 1.8, .3));
                    f.player.lastTickPosX = -.25;
                    f.player.syntheticBody = () -> { extras.incrementAndGet(); f.player.hurtTime = 2; Pulsar.BUS.post(new EventUpdate()); };
                    now.set(150_000_000L); LagRange.beginFrame(f.client, false, 1, 50); tick();
                    assertEquals("only the already-running body can finish", 1, extras.get()); assertTrue(module.isToggled());
                    assertNull("old completion must not repopulate cleared activity", module.getActivitySnapshot());
                    assertEquals(0, ((LagRangeBudget) field(module, "budget")).liveCredit(now.get()));
                } finally { module.setToggled(false); }
            }
        }

        @Test public void worldUnloadClearsReferencesAndBlocksTheStillVisibleOldScene() throws Exception {
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                health(f, 20); ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy);
                for (Module module : Arrays.asList(new Backtrack(() -> 0, f.client), new Tickbase(() -> 0, f.client), new LagRange(() -> 0, null))) {
                    module.getBooleanSetting("Only Aura").setValue(false); damageEnabled(module); module.setToggled(true);
                    try {
                        tick(); Tickbase.beginFrame(f.client, false, 1, 50); LagRange.beginFrame(f.client, false, 1, 50);
                        assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                        f.pipe().worldChanged(); Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                        assertTrue(module.isToggled());
                        String worldField = module instanceof Backtrack ? "targetWorld" : module instanceof Tickbase ? "boundWorld" : "world";
                        String playerField = module instanceof Backtrack ? "localPlayer" : module instanceof Tickbase ? "boundPlayer" : "player";
                        assertNull(field(module, worldField)); assertNull(field(module, playerField));
                        assertNull(field(guard(module), "world")); assertNull(field(guard(module), "player")); assertNull(field(guard(module), "pipeline"));
                        tick(); Tickbase.beginFrame(f.client, false, 1, 50); LagRange.beginFrame(f.client, false, 1, 50);
                        assertNull("Unload still exposes old Minecraft pointers; they cannot be rebound", field(module, worldField));
                        if (module instanceof Tickbase) {
                            Object budget = field(module, "budget"); assertNull(field(budget, "world")); assertNull(field(budget, "player"));
                        }
                        Pulsar.BUS.post(new WorldEvent.Load(f.world)); tick(); assertTrue(module.isToggled());
                    } finally { module.setToggled(false); }
                }
            }
        }
    }
}
