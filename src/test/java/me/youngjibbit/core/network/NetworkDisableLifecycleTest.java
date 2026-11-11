package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** Shutdown contracts only: no server connection and no changes to enabled behavior. */
public class NetworkDisableLifecycleTest {
    @Test public void backtrackDisableCannotReacquireAHoldFromAReleaseCallback() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); NetworkTestSupport.clock(f, now);
            Backtrack module = new Backtrack(now::get, f.client);
            module.getBooleanSetting("Only Aura").setValue(false);
            module.getBooleanSetting("Diagnostics").setValue(true);
            module.getModeSetting("Target Source").setValue("Attack / Cursor");
            module.getModeSetting("Backtrack Mode").setValue("Timed");
            module.getNumberSetting("Cooldown Ticks").setValue(0D);
            module.setToggled(true);
            NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer();
            AtomicBoolean callback = new AtomicBoolean();
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy);
                Pulsar.BUS.post(new TickEvent());
                f.receive(move(7));
                assertEquals(1, f.pipe().delays().size());
                observer.action = event -> {
                    if (event.isPost() && event.isInbound() && callback.compareAndSet(false, true))
                        Pulsar.BUS.post(new TickEvent());
                };
                Pulsar.BUS.subscribe(observer);
                module.setToggled(false);
                assertTrue("release callback must actually run", callback.get());
                assertEquals("disabled module must not renew its lease", 0, f.pipe().delays().snapshot(this).activeOwners);
                assertEquals(0, f.pipe().delays().size());
                assertEquals(-1, module.getDiagnostics().targetId);
                assertEquals("disabled", module.getDiagnostics().reason);
                int applied = enemy.serverPosX;
                f.receive(move(7));
                assertEquals(applied + 1, enemy.serverPosX);
                assertEquals(0, f.pipe().delays().size());
            } finally { Pulsar.BUS.unsubscribe(observer); module.setToggled(false); }
        }
    }

    @Test public void lagRangeDisableCannotRenewItsQueueDuringReleasedPacketCallbacks() throws Exception {
        Wave2IsolatedCase.run(LagRangeChecks.class,"lagRangeDisableCannotRenewItsQueueDuringReleasedPacketCallbacks");
    }

    /** LagRange reads static-final Module.mc; isolate the fixture's Unsafe rebind under normal JIT. */
    public static class LagRangeChecks {
    @Test public void lagRangeDisableCannotRenewItsQueueDuringReleasedPacketCallbacks() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); NetworkTestSupport.clock(f, now);
            LagRange module = new LagRange(now::get, (min, max) -> 500);
            module.getBooleanSetting("Only Aura").setValue(false);
            module.setToggled(true);
            NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer();
            AtomicBoolean callback = new AtomicBoolean();
            try {
                f.enemy(7, 6); Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer first = new C03PacketPlayer(true);
                f.connection.network.sendPacket(first);
                assertEquals(1, f.pipe().delays().size());
                observer.action = event -> {
                    if (event.isPost() && event.getPacket() == first && callback.compareAndSet(false, true)) {
                        now.set(1_000_000_000L);
                        Pulsar.BUS.post(new TickEvent());
                    }
                };
                Pulsar.BUS.subscribe(observer);
                module.setToggled(false);
                assertTrue(callback.get());
                assertSame(first, f.connection.channel.readOutbound());
                assertEquals("disabled module must not renew its lease", 0, f.pipe().delays().snapshot(this).activeOwners);
                C03PacketPlayer next = new C03PacketPlayer(false);
                f.connection.network.sendPacket(next);
                assertSame(next, f.connection.channel.readOutbound());
                assertEquals(0, f.pipe().delays().size());
                assertTrue(LagRange.allowBlockAttack());
                AtomicInteger steps = new AtomicInteger();
                LagRange.runPlayerStep(f.player, () -> { steps.incrementAndGet(); return true; });
                assertEquals(1, steps.get());
            } finally { Pulsar.BUS.unsubscribe(observer); module.setToggled(false); }
        }
    }
    }

    @Test public void tickbaseDisableClearsCompletedStepMetadataAndRequiresAFreshEnableSample() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Tickbase module = new Tickbase(() -> 0, f.client);
            module.getBooleanSetting("Debug").setValue(true);
            module.setToggled(true);
            try {
                TickbaseBudget budget = budget(module);
                budget.configure(1, 1, 2); budget.bind(f.player, f.world);
                budget.frame(true, 1, 50, 1);
                budget.step(true, () -> true, () -> true);
                assertTrue(budget.release());
                budget.step(false, () -> true, () -> true);
                assertEquals(1, budget.getExtraSteps());
                module.setToggled(false);
                assertEquals(0, module.getDiagnostics().extraSteps);
                assertEquals(0, module.getDiagnostics().credit);
                assertFalse(module.getDiagnostics().releasing);
                assertEquals("disabled", module.getDiagnostics().reason);
                AtomicInteger steps = new AtomicInteger();
                Tickbase.runPlayerStep(f.player, () -> { steps.incrementAndGet(); return true; });
                assertEquals(1, steps.get());
                module.setToggled(true);
                assertFalse(budget.hasFreshFrame());
                assertFalse(budget.release());
                assertEquals(0, budget.getExtraSteps());
            } finally { module.setToggled(false); }
        }
    }

    private static TickbaseBudget budget(Tickbase module) throws Exception {
        Field field = Tickbase.class.getDeclaredField("budget"); field.setAccessible(true);
        return (TickbaseBudget) field.get(module);
    }

    private static S14PacketEntity move(int id) {
        return new S14PacketEntity.S15PacketEntityRelMove(id, (byte) 1, (byte) 0, (byte) 0, true);
    }
}
