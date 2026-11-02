package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class BacktrackArsenicTest {
    @Test public void defaultRequiresAcceptedAttackAndReleasesWhenRealTargetComesCloser() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); Backtrack back = new Backtrack(now::get, f.client);
            back.getBooleanSetting("Diagnostics").setValue(true); back.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3); f.cursor(enemy); Pulsar.BUS.post(new TickEvent());
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 16, (byte) 0, (byte) 0, true));
                assertEquals(0, f.pipe().delays().size()); assertEquals(-1, back.getDiagnostics().targetId);
                assertTrue(f.controller.tryAttackEntity(f.player, enemy, () -> true));
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 16, (byte) 0, (byte) 0, true));
                assertEquals(1, f.pipe().delays().size());
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) -32, (byte) 0, (byte) 0, true));
                assertEquals(0, f.pipe().delays().size());
                assertEquals(96, enemy.serverPosX);
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7, (byte) 16, (byte) 0, (byte) 0, true));
                assertEquals("settling", back.getDiagnostics().reason);
                now.set(1_001_000_000L); Pulsar.BUS.post(new TickEvent());
                assertEquals(-1, back.getDiagnostics().targetId);
            } finally { back.setToggled(false); }
        }
    }

    @Test public void targetFeedbackSharesTheHoldAndLethalHealthDiscardsIt() throws Exception {
        PacketFlowTest.MainThread thread = new PacketFlowTest.MainThread();
        AtomicLong now = new AtomicLong(); java.util.List<Object> applied = new java.util.ArrayList<>();
        PacketPipeline pipe = new PacketPipeline(thread, () -> true, p -> { }, e -> { throw new AssertionError(e); }, new PacketDelays(now::get));
        try {
            pipe.delays().request(this, 100, (packet, flow) -> true);
            pipe.inbound(new S14PacketEntity(7), PacketFlowTest.handler(a -> applied.add(a[0])), false);
            pipe.inbound(new S19PacketEntityStatus(), PacketFlowTest.handler(a -> applied.add(a[0])), false);
            pipe.inbound(new S06PacketUpdateHealth(10, 20, 0), PacketFlowTest.handler(a -> applied.add(a[0])), false);
            pipe.inbound(new S29PacketSoundEffect("test", 0, 0, 0, 1, 1), PacketFlowTest.handler(a -> applied.add(a[0])), false);
            assertEquals(4, pipe.delays().size()); assertTrue(applied.isEmpty());
            pipe.inbound(new S06PacketUpdateHealth(0, 20, 0), PacketFlowTest.handler(a -> applied.add(a[0])), false);
            assertEquals(0, pipe.delays().size()); assertEquals(1, applied.size());
        } finally { pipe.close(); }
    }
}
