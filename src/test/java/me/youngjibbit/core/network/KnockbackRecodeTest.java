package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.ghost.JumpReset;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class KnockbackRecodeTest {
    @org.junit.BeforeClass public static void bootstrapItems() { net.minecraft.init.Bootstrap.register(); }
    private void hurt(ExploitHookFixture f) {
        Pulsar.BUS.post(new PacketReceiveEvent(new S19PacketEntityStatus(f.player, (byte) 2), f.pipe()));
    }
    private void swing(ExploitHookFixture f, ExploitHookFixture.Enemy enemy) {
        Pulsar.BUS.post(new PacketReceiveEvent(new S0BPacketAnimation(enemy, 0), f.pipe()));
    }
    private void velocity(ExploitHookFixture f) {
        f.receive(new S12PacketEntityVelocity(f.player.getEntityId(), .3, .2, -.1));
    }
    private MovementInputEvent input() {
        MovementInputEvent event = new MovementInputEvent(0, 1, false, false);
        Pulsar.BUS.post(event);
        return event;
    }

    @Test public void meleeResetPrecedesApplicationAndNeverAddsSprintImpulseOrOverwritesServerY() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.world.entities.put(1, f.player);
            JumpReset reset = new JumpReset(() -> 0, () -> 0);
            reset.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                swing(f, enemy); hurt(f); velocity(f);
                assertTrue(f.player.isAirBorne);
                assertEquals(.3, f.player.motionX, 1e-9);
                assertEquals(.2, f.player.motionY, 1e-9);
                assertEquals(-.1, f.player.motionZ, 1e-9);
                f.player.isAirBorne = false;
                velocity(f);
                assertFalse("A hurt confirmation is single use", f.player.isAirBorne);
            } finally { reset.setToggled(false); }
        }
    }

    @Test public void resetRejectsMissingOrExpiredEvidenceProjectileWaterChanceAndReusedEntityIds() throws Exception {
        for (int scenario = 0; scenario < 8; scenario++) try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.world.entities.put(1, f.player);
            AtomicLong now = new AtomicLong();
            JumpReset reset = new JumpReset(now::get, () -> .5);
            reset.setToggled(true);
            try {
                ExploitHookFixture.Enemy enemy = f.enemy(7, 3);
                if (scenario != 0) swing(f, enemy);
                if (scenario != 1) hurt(f);
                if (scenario == 2) now.set(151_000_000);
                if (scenario == 3) { now.set(401_000_000); hurt(f); }
                if (scenario == 4) enemy.inventory.mainInventory[0] = new ItemStack(Items.bow);
                if (scenario == 5) f.player.water = true;
                if (scenario == 6) reset.jumpChance.setValue(0D);
                if (scenario == 7) f.enemy(7, 3);
                velocity(f);
                assertFalse("scenario=" + scenario, f.player.isAirBorne);
                assertEquals(.2, f.player.motionY, 1e-9);
            } finally { reset.setToggled(false); }
        }
    }

    @Test public void legitVelocityLeavesPacketIntactAndRequestsExactlyOneInputJump() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.world.entities.put(1, f.player); f.player.sprint = true;
            Velocity velocity = new Velocity(); velocity.setToggled(true);
            try {
                velocity(f);
                assertEquals(.3, f.player.motionX, 1e-9); assertEquals(.2, f.player.motionY, 1e-9);
                assertTrue(input().isJump()); assertFalse(input().isJump());
                assertEquals(.2, f.player.motionY, 1e-9); // input request itself does not write motion
                f.player.sprint = false; velocity(f); assertFalse(input().isJump());
            } finally { velocity.setToggled(false); }
        }
    }

    @Test public void cancelledVelocityAndContextChangesCannotLeaveAJumpRequest() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.world.entities.put(1, f.player); f.player.sprint = true;
            Velocity velocity = new Velocity(); velocity.setToggled(true);
            PacketFlowTest.Observer observer = new PacketFlowTest.Observer(); Pulsar.BUS.subscribe(observer);
            try {
                observer.action = e -> { if (e.isPre() && e.getPacket() instanceof S12PacketEntityVelocity) e.cancel(); };
                velocity(f); assertFalse(input().isJump()); assertEquals(0, f.player.motionY, 0);
                observer.action = e -> { };
                velocity(f); f.pipe().worldChanged(); assertFalse(input().isJump());
                velocity(f); velocity.setToggled(false); assertFalse(input().isJump());
            } finally { velocity.setToggled(false); Pulsar.BUS.unsubscribe(observer); }
        }
    }

    @Test public void standaloneResetOwnsTheJumpWhenBothModulesAreEnabled() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.world.entities.put(1, f.player); f.player.sprint = true;
            Velocity velocity = new Velocity(); JumpReset reset = new JumpReset(() -> 0, () -> 0);
            velocity.setToggled(true); reset.setToggled(true);
            try {
                swing(f, f.enemy(7, 3)); hurt(f); velocity(f);
                assertTrue(f.player.isAirBorne); assertFalse(input().isJump());
            } finally { velocity.setToggled(false); reset.setToggled(false); }
        }
    }
}
