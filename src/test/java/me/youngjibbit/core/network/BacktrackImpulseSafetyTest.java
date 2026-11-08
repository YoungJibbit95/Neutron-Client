package me.youngjibbit.core.network;

import me.youngjibbit.module.impl.exploit.Backtrack;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class BacktrackImpulseSafetyTest {
    private Backtrack enable(ExploitHookFixture f, AtomicLong now, String mode) throws Exception {
        BacktrackLagRangeControlTest.clock(f, now);
        f.world.entities.put(1, f.player);
        Backtrack back = new Backtrack(now::get, f.client, (min, max) -> min);
        back.getBooleanSetting("Only Aura").setValue(false);
        back.getModeSetting("Backtrack Mode").setValue(mode);
        back.getModeSetting("Delay Mode").setValue("Range");
        back.getNumberSetting("Min MS").setValue(21D);
        back.getNumberSetting("Max MS").setValue(91D);
        back.getNumberSetting("Acquire Range").setValue(3.5D);
        back.setToggled(true);
        return back;
    }
    private void move(ExploitHookFixture f) {
        f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte)4, (byte)0, (byte)0, true));
    }
    @Test public void allModesReleaseOnRealKnockbackAndSettingsCannotBypassRecovery() throws Exception {
        for (String mode : new String[]{"Distance", "Timed", "Pulse", "Intave"})
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                AtomicLong now = new AtomicLong(); Backtrack back = enable(f, now, mode);
                try {
                    ExploitHookFixture.Enemy enemy = f.enemy(7, 2.8);
                    // Safety recovery remains mandatory even for a legacy false value.
                    back.getBooleanSetting("Release On Velocity").setValue(false);
                    assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                    move(f); assertEquals(1, f.pipe().delays().size());
                    f.receive(new S12PacketEntityVelocity(1, .3, .2, 0));
                    assertEquals(mode, 0, f.pipe().delays().size());
                    back.setToggled(false); back.setToggled(true);
                    assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                    move(f); assertEquals(0, f.pipe().delays().size());
                    now.set(600_000_000L); f.player.onGround = false; f.player.motionY = -.1;
                    assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                    move(f); assertEquals(0, f.pipe().delays().size());
                } finally { back.setToggled(false); }
            }
    }
    @Test public void allModesDrainTargetMovementBeforeLocalHealthFeedback() throws Exception {
        for (String mode : new String[]{"Distance", "Timed", "Pulse", "Intave"})
            try (ExploitHookFixture f = new ExploitHookFixture()) {
                Backtrack back = enable(f, new AtomicLong(), mode);
                try {
                    ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                    assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                    move(f); assertEquals(96, enemy.serverPosX);
                    java.util.List<Object> applied = new java.util.ArrayList<>();
                    f.pipe().inbound(new S06PacketUpdateHealth(10, 20, 0), PacketFlowTest.handler(args -> {
                        assertEquals("movement must precede health feedback", 100, enemy.serverPosX);
                        applied.add(args[0]);
                    }), false);
                    assertEquals(mode, 1, applied.size());
                    assertEquals(0, f.pipe().delays().size());
                } finally { back.setToggled(false); }
            }
    }
}
