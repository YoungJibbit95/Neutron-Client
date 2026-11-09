package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class AuraDependentNetworkTest {
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class AuraSelection implements AutoCloseable {
        final ExploitHookFixture fixture;
        final Aura aura = new Aura();
        final Object oldModules;

        AuraSelection(ExploitHookFixture fixture) throws Exception {
            this.fixture = fixture;
            oldModules = field(Pulsar.class, "mm").get(Pulsar.INSTANCE);
            field(Pulsar.class, "mm").set(Pulsar.INSTANCE, new ModuleManager(Collections.singletonList(aura)));
            fixture.player.ticksExisted = 120;
            me.youngjibbit.core.bootstrap.RuntimeServices.attackScheduler().beginTick();
            aura.setToggled(true);
        }

        void select(ExploitHookFixture.Enemy target) throws Exception {
            field(Aura.class, "target").set(aura, target);
            field(Aura.class, "worldContext").set(aura, fixture.world);
            field(Aura.class, "playerContext").set(aura, fixture.player);
        }

        void lose(String cause, ExploitHookFixture.Enemy target) throws Exception {
            if ("disabled".equals(cause)) aura.setToggled(false);
            else if ("no target".equals(cause)) select(null);
            else if ("dead".equals(cause)) target.isDead = true;
            else if ("removed".equals(cause)) fixture.world.entities.remove(target.getEntityId());
            else {
                target.posX = 20;
                target.setEntityBoundingBox(new net.minecraft.util.AxisAlignedBB(19.7, 0, -.3, 20.3, 1.8, .3));
            }
        }

        @Override public void close() throws Exception {
            try { aura.setToggled(false); }
            finally { field(Pulsar.class, "mm").set(Pulsar.INSTANCE, oldModules); }
        }
    }

    @Test public void backtrackDefaultsToOrdinaryPacketsWithoutAura() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong();
            NetworkTestSupport.clock(f, now);
            Backtrack back = new Backtrack(now::get, f.client);
            back.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 16, (byte) 0, (byte) 0, true));
                assertEquals("Aura is disabled, so movement must not be held", 0, f.pipe().delays().size());
                assertEquals(112, enemy.serverPosX);
            } finally { back.setToggled(false); }
        }
    }

    @Test public void lagRangeDefaultsToOrdinaryPacketsWithoutAura() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong();
            NetworkTestSupport.clock(f, now);
            LagRange lag = new LagRange(now::get, (min, max) -> max);
            lag.setToggled(true);
            try {
                f.enemy(7, 6);
                Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer movement = new C03PacketPlayer(true);
                f.connection.network.sendPacket(movement);
                assertEquals("A nearby player alone must not activate delay", 0, f.pipe().delays().size());
                assertSame(movement, f.connection.channel.readOutbound());
            } finally { lag.setToggled(false); }
        }
    }

    @Test public void tickbaseDefaultsToOrdinaryStepsWithoutAura() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Tickbase tick = new Tickbase(() -> 0, f.client);
            tick.getBooleanSetting("Debug").setValue(true);
            tick.setToggled(true);
            try {
                f.cursor(f.enemy(7, 3));
                Tickbase.beginFrame(f.client, false, 1, 50);
                AtomicInteger steps = new AtomicInteger();
                Tickbase.runPlayerStep(f.player, () -> { steps.incrementAndGet(); return true; });
                assertEquals("Cursor target alone must not withhold a player step", 1, steps.get());
                assertEquals(0, tick.getDiagnostics().credit);
            } finally { tick.setToggled(false); }
        }
    }

    @Test public void backtrackReleasesOnAuraLossAndIgnoresOtherTargetSources() throws Exception {
        for (String cause : new String[]{"disabled", "no target", "dead", "removed", "range"}) {
            try (ExploitHookFixture f = new ExploitHookFixture(); AuraSelection selection = new AuraSelection(f)) {
                AtomicLong now = new AtomicLong();
                NetworkTestSupport.clock(f, now);
                Backtrack back = new Backtrack(now::get, f.client);
                back.getModeSetting("Backtrack Mode").setValue("Timed");
                back.setToggled(true);
                try {
                    ExploitHookFixture.Enemy target = f.enemy(7, 3);
                    selection.select(target);
                    CombatTargetTracker.update(f.enemy(8, 2.5), "aimassist");
                    f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 0, (byte) 0, (byte) 0, true));
                    assertEquals("A valid Aura selection activates Backtrack", 1, f.pipe().delays().size());
                    selection.lose(cause, target);
                    f.receive(new S14PacketEntity.S15PacketEntityRelMove(8, (byte) 0, (byte) 0, (byte) 0, true));
                    assertEquals(cause, 0, f.pipe().delays().size());
                } finally { back.setToggled(false); }
            }
        }
    }

    @Test public void lagRangeReleasesBeforeNextOutboundPacketOnAuraLoss() throws Exception {
        for (String cause : new String[]{"disabled", "no target", "dead", "removed", "range"}) {
            try (ExploitHookFixture f = new ExploitHookFixture(); AuraSelection selection = new AuraSelection(f)) {
                AtomicLong now = new AtomicLong();
                NetworkTestSupport.clock(f, now);
                LagRange lag = new LagRange(now::get, (min, max) -> max);
                lag.getNumberSetting("Start Distance").setValue(0D);
                lag.setToggled(true);
                try {
                    ExploitHookFixture.Enemy target = f.enemy(7, 3);
                    selection.select(target);
                    Pulsar.BUS.post(new TickEvent());
                    C03PacketPlayer held = new C03PacketPlayer(true);
                    f.connection.network.sendPacket(held);
                    assertEquals(1, f.pipe().delays().size());
                    selection.lose(cause, target);
                    C03PacketPlayer next = new C03PacketPlayer(false);
                    f.connection.network.sendPacket(next);
                    assertEquals(cause, 0, f.pipe().delays().size());
                    assertSame(held, f.connection.channel.readOutbound());
                    assertSame(next, f.connection.channel.readOutbound());
                } finally { lag.setToggled(false); }
            }
        }
    }

    @Test public void tickbaseDropsEarnedCreditWhenAuraDisappearsBetweenSteps() throws Exception {
        for (String cause : new String[]{"disabled", "no target", "dead", "removed", "range"}) {
            try (ExploitHookFixture f = new ExploitHookFixture(); AuraSelection selection = new AuraSelection(f)) {
                Tickbase tick = new Tickbase(() -> 0, f.client);
                tick.getBooleanSetting("Debug").setValue(true);
                tick.getBooleanSetting("Require Target").setValue(false);
                tick.getNumberSetting("Min Charge").setValue(1D);
                tick.getNumberSetting("Release Speed").setValue(2D);
                tick.setToggled(true);
                try {
                    ExploitHookFixture.Enemy target = f.enemy(7, 3);
                    selection.select(target);
                    Tickbase.beginFrame(f.client, false, 1, 50);
                    Tickbase.runPlayerStep(f.player, () -> { fail("Valid Aura target should allow charging"); return true; });
                    assertEquals(1, tick.getDiagnostics().credit);
                    assertTrue(f.controller.tryAttackEntity(f.player, target, () -> true));
                    assertTrue(tick.getDiagnostics().releasing);
                    AtomicInteger steps = new AtomicInteger();
                    Tickbase.runPlayerStep(f.player, () -> {
                        steps.incrementAndGet();
                        try { selection.lose(cause, target); }
                        catch (Exception error) { throw new AssertionError(error); }
                        return true;
                    });
                    assertEquals(cause, 1, steps.get());
                    assertEquals(cause, 0, tick.getDiagnostics().credit);
                    assertFalse(tick.getDiagnostics().releasing);
                } finally { tick.setToggled(false); }
            }
        }
    }

    @Test public void enabledAuraWithoutSelectionDoesNotAdoptCursorOrNearestTarget() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture(); AuraSelection selection = new AuraSelection(f)) {
            ExploitHookFixture.Enemy target = f.enemy(7, 3);
            f.cursor(target);
            CombatTargetTracker.update(target, "aimassist");
            Backtrack back = new Backtrack(() -> 0, f.client);
            LagRange lag = new LagRange(() -> 0, (min, max) -> max);
            Tickbase tick = new Tickbase(() -> 0, f.client);
            lag.getNumberSetting("Start Distance").setValue(0D);
            back.setToggled(true); lag.setToggled(true); tick.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 16, (byte) 0, (byte) 0, true));
                f.connection.network.sendPacket(new C03PacketPlayer(true));
                Tickbase.beginFrame(f.client, false, 1, 50);
                AtomicInteger steps = new AtomicInteger();
                Tickbase.runPlayerStep(f.player, () -> { steps.incrementAndGet(); return true; });
                assertEquals(1, steps.get());
                assertEquals(0, f.pipe().delays().size());
            } finally { tick.setToggled(false); lag.setToggled(false); back.setToggled(false); }
        }
    }
}
