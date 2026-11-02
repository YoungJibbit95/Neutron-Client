package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.LagRange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class LagRangePlayerStepTest {
    private static final class DigController extends net.minecraft.client.multiplayer.PlayerControllerMP {
        int resets;
        DigController(ExploitHookFixture fixture) { super(fixture.client, fixture.connection.play); }
        @Override public void resetBlockRemoving() { resets++; }
    }

    @Test public void withholdingRejectsUnpublishedDigLookButStillReleasesExistingDig() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.player.ticksExisted = 120; f.enemy(7, 3);
            LagRange lag = new LagRange(() -> 0, (min, max) -> min); lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent()); LagRange.beginFrame(f.client, false, 1, 50);
                LagRange.runPlayerStep(f.player, () -> { fail("hurt target should allow an earned withheld body"); return true; });
                f.client.objectMouseOver = new net.minecraft.util.MovingObjectPosition(new net.minecraft.util.Vec3(1, 1, 0),
                        net.minecraft.util.EnumFacing.WEST, new net.minecraft.util.BlockPos(1, 1, 0));
                assertFalse(LagRange.allowBlockAttack());
                DigController controller = new DigController(f); f.client.playerController = controller;
                java.lang.reflect.Method click = net.minecraft.client.Minecraft.class.getDeclaredMethod("sendClickBlockToController", boolean.class);
                click.setAccessible(true);
                click.invoke(f.client, true); assertEquals(1, controller.resets);
                click.invoke(f.client, false); assertEquals(2, controller.resets);
            } finally { lag.setToggled(false); }
        }
    }

    @Test public void onlyEarnedBodiesRunOnApproachAndExpiredFrameCannotDropAnotherStep() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            AtomicLong now = new AtomicLong(); AtomicInteger bodies = new AtomicInteger();
            f.player.ticksExisted = 120; f.player.movementInput = new MovementInput();
            f.player.movementInput.moveForward = 1; f.player.rotationYaw = -90;
            ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
            LagRange lag = new LagRange(now::get, (min, max) -> min); lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());
                for (int tick = 0; tick < 3; tick++) {
                    now.set(tick * 50_000_000L); LagRange.beginFrame(f.client, false, 1, 50);
                    LagRange.runPlayerStep(f.player, () -> { bodies.incrementAndGet(); return true; });
                }
                assertEquals(0, bodies.get());
                now.set(150_000_000L); LagRange.beginFrame(f.client, false, 1, 50);
                LagRange.runPlayerStep(f.player, () -> { bodies.incrementAndGet(); return true; });
                assertEquals(1, bodies.get()); // the fourth consecutive body must run
                enemy.posX = 3.5; enemy.hurtTime = 0;
                enemy.setEntityBoundingBox(new AxisAlignedBB(3.2, 0, -.3, 3.8, 1.8, .3));
                f.player.lastTickPosX = -.25;
                f.player.syntheticBody = () -> { bodies.incrementAndGet(); f.player.posX += .25; };
                now.set(200_000_000L); LagRange.beginFrame(f.client, false, 1, 50);
                Pulsar.BUS.post(new TickEvent());
                assertEquals(2, bodies.get()); assertEquals(.25, f.player.posX, 0);
                assertEquals(121, f.player.ticksExisted);
                enemy.hurtTime = 5;
                now.set(400_000_000L); // more than 150 ms since the last healthy frame
                LagRange.runPlayerStep(f.player, () -> { bodies.incrementAndGet(); return true; });
                assertEquals(3, bodies.get());
            } finally { lag.setToggled(false); }
        }
    }

    @Test public void acceptedActionRequiresAnOrdinaryMovementBodyInsteadOfDroppingIt() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.player.ticksExisted = 120; f.enemy(7, 3);
            AtomicInteger bodies = new AtomicInteger();
            LagRange lag = new LagRange(() -> 0, (min, max) -> min); lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent()); LagRange.beginFrame(f.client, false, 1, 50);
                f.connection.network.sendPacket(new C0APacketAnimation());
                LagRange.runPlayerStep(f.player, () -> { bodies.incrementAndGet(); return true; });
                assertEquals(1, bodies.get());
                assertEquals(1, f.client.timer.timerSpeed, 0);
            } finally { lag.setToggled(false); }
        }
    }
}
