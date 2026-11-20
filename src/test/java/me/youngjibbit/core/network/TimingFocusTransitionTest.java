package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.util.MouseHelper;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class TimingFocusTransitionTest {
    static void loseFocus(Minecraft client) {
        client.mouseHelper = new MouseHelper() {
            @Override public void ungrabMouseCursor() { /* No native window in this fixture. */ }
        };
        client.setIngameNotInFocus();
        assertFalse(client.inGameHasFocus);
    }

    @Test public void lagRangeFlushesItsMovementInOrderBeforeAnotherTick() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong();
            NetworkTestSupport.clock(f, now);
            LagRange module = new LagRange(now::get, (min, max) -> max);
            module.getBooleanSetting("Only Aura").setValue(false);
            module.getNumberSetting("Start Distance").setValue(0D);
            module.setToggled(true);
            try {
                CombatTargetTracker.update(f.enemy(7, 3), "test");
                Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer first = new C03PacketPlayer(true), second = new C03PacketPlayer(false);
                f.connection.network.sendPacket(first);
                f.connection.network.sendPacket(second);
                assertEquals(2, f.pipe().delays().size());

                loseFocus(f.client);
                assertEquals(0, f.pipe().delays().size());
                assertSame(first, f.connection.channel.readOutbound());
                assertSame(second, f.connection.channel.readOutbound());
                assertNull(f.connection.channel.readOutbound());
                assertTrue(module.isToggled());
            } finally { module.setToggled(false); }
        }
    }

    @Test public void backtrackAppliesHeldMovementAndForgetsTheAttackWhenFocusReturns() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong();
            NetworkTestSupport.clock(f, now);
            Backtrack module = new Backtrack(now::get, f.client);
            module.getBooleanSetting("Only Aura").setValue(false);
            module.getModeSetting("Backtrack Mode").setValue("Timed");
            module.getModeSetting("Target Source").setValue("Attack");
            module.setToggled(true);
            try {
                ExploitHookFixture.Enemy target = f.enemy(7, 3);
                assertTrue(f.controller.tryAttackEntity(f.player, target, () -> true));
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 4, (byte) 0, (byte) 0, true));
                assertEquals(1, f.pipe().delays().size());
                loseFocus(f.client);
                assertEquals(0, f.pipe().delays().size());
                assertEquals(100, target.serverPosX);

                f.client.inGameHasFocus = true;
                Pulsar.BUS.post(new TickEvent());
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 4, (byte) 0, (byte) 0, true));
                assertEquals(104, target.serverPosX);
                assertEquals(0, f.pipe().delays().size());
                assertTrue(module.isToggled());
            } finally { module.setToggled(false); }
        }
    }

    @Test public void tickbaseDropsCreditBeforeAnImmediateRefocus() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong();
            NetworkTestSupport.clock(f, now);
            Tickbase module = new Tickbase(now::get, f.client);
            module.getBooleanSetting("Only Aura").setValue(false);
            module.getBooleanSetting("Diagnostics").setValue(true);
            module.setToggled(true);
            try {
                f.cursor(f.enemy(7, 3));
                Tickbase.beginFrame(f.client, false, 1, 50);
                AtomicInteger steps = new AtomicInteger();
                Tickbase.runPlayerStep(f.player, () -> steps.incrementAndGet() > 0);
                assertEquals(1, module.getDiagnostics().credit);
                assertEquals(0, steps.get());

                loseFocus(f.client);
                assertEquals(0, module.getDiagnostics().credit);
                f.client.inGameHasFocus = true;
                Tickbase.runPlayerStep(f.player, () -> steps.incrementAndGet() > 0);
                assertEquals(1, steps.get());
                assertFalse(module.getDiagnostics().releasing);
                assertTrue(module.isToggled());
            } finally { module.setToggled(false); }
        }
    }
}
