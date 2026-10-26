package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.LagRange;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.junit.Test;

import static org.junit.Assert.*;

public class LagRangeLifecycleTest {
    @Test public void deadLocalPlayerReleasesItsWindowAndCannotAcquireAnotherHold() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            LagRange lag = new LagRange(() -> 0, (min, max) -> 500);
            lag.getNumberSetting("Min MS").setValue(500.0);
            lag.getNumberSetting("Max MS").setValue(500.0);
            lag.setToggled(true);
            try {
                CombatTargetTracker.update(f.enemy(7, 3), "synthetic");
                Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer first = new C03PacketPlayer(true);
                f.connection.network.sendPacket(first);
                assertEquals(1, f.pipe().delays().size());
                f.player.isDead = true;
                Pulsar.BUS.post(new TickEvent());
                assertEquals(0, f.pipe().delays().size());
                assertSame(first, f.connection.channel.readOutbound());
                Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer next = new C03PacketPlayer(true);
                f.connection.network.sendPacket(next);
                assertSame(next, f.connection.channel.readOutbound());
                assertEquals(0, f.pipe().delays().snapshot(this).activeOwners);
            } finally { lag.setToggled(false); }
        }
    }
}
