package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.network.PacketDelays;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.utils.Eierlecken;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

/**
 * Original LagRange earns/spends credit; actual EntityPlayerSP.onUpdate enters
 * observation and the real moveFlying/Speed V2 consumer.
 * An EventUpdate listener calls moveFlying and then cancels before full world physics.
 * World storage and monotonic time are fixture seams; no credit/step kind is injected.
 */
public class LagRangeObservationFlowTest {
    @Test public void checkInFreshJvm() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "earnedBodyCannotBecomeOrdinaryOrConsumeTheSpeedRamp");
    }

    public static class Checks {
        @Test public void earnedBodyCannotBecomeOrdinaryOrConsumeTheSpeedRamp() throws Exception {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            Field utilityClient = Eierlecken.class.getDeclaredField("mc");
            utilityClient.setAccessible(true);
            Object oldUtilityClient = utilityClient.get(null);
            try (IntaveBoatSpeedTest.Run run = new IntaveBoatSpeedTest.Run()) {
                unsafe.putObject(unsafe.staticFieldBase(utilityClient),
                        unsafe.staticFieldOffset(utilityClient), run.f.mc);
                run.world.entities.clear();
                run.world.entities.add(run.f.player);
                ScaffoldClientFlowTest.set(World.class, run.world, "loadedEntityList", run.world.entities);
                run.f.player.setEntityId(1);
                run.f.player.setPosition(0, 64, 0);
                run.f.player.rotationYaw = -90;
                run.f.player.ticksExisted = 120;
                run.f.player.movementInput.moveForward = .98F;
                run.f.mc.gameSettings.keyBindSneak = new KeyBinding("lagrange.sneak", 42, "test");
                run.f.mc.gameSettings.keyBindSprint = new KeyBinding("lagrange.sprint", 29, "test");
                ScaffoldClientFlowTest.set(Minecraft.class, run.f.mc, "mcThread", Thread.currentThread());
                ScaffoldClientFlowTest.set(EntityPlayerSP.class, run.f.player, "mc", run.f.mc);
                ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, run.f.player.sendQueue,
                        "gameController", run.f.mc);
                ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, run.f.player.sendQueue,
                        "doneLoadingTerrain", true);
                run.f.player.sendQueue.clientWorldController = run.world;
                // The coordinator reads this pipeline's delays.now(), sharing the fixture clock.
                ScaffoldClientFlowTest.set(PacketPipeline.class, run.f.network.getClientPackets(),
                        "delays", new PacketDelays(run.time::get));

                RotationBehaviorHookTest.Player enemy =
                        RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
                enemy.worldObj = run.world;
                enemy.setEntityId(7);
                enemy.setPosition(3, 64, 0);
                enemy.setEntityBoundingBox(new AxisAlignedBB(2.7, 64, -.3, 3.3, 65.8, .3));
                enemy.hurtTime = 5;
                run.world.entities.add(enemy);

                Speed speed = new Speed();
                ScaffoldClientFlowTest.set(Speed.class, speed, "momentumClock", (LongSupplier) run.time::get);
                speed.getModeSetting("Mode").setValue("Intave Momentum V2");
                LagRange lag = new LagRange(run.time::get, (min, max) -> min);
                lag.getBooleanSetting("Only Aura").setValue(false);
                lag.getBooleanSetting("Dodge").setValue(false);

                RuntimeObservation observation = run.f.runtime.getObservation();
                List<ExecutedStep.Kind> kinds = new ArrayList<>();
                List<Boolean> mayPlace = new ArrayList<>();
                List<Float> friction = new ArrayList<>();
                PlayerMoveUpdateEvent[] movement = {null};
                Listener<PlayerMoveUpdateEvent> capture =
                        new Listener<>(PlayerMoveUpdateEvent.class, event -> movement[0] = event);
                Listener<EventUpdate> body = new Listener<>(EventUpdate.class, event -> {
                    assertNotNull("actual onUpdate entered an observed body", observation.getCurrentStep());
                    kinds.add(observation.getCurrentStep().getKind());
                    // Prerequisite only, not placement success or a controller reservation.
                    mayPlace.add(AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT));
                    run.f.player.motionX = run.f.player.motionZ = 0;
                    movement[0] = null;
                    run.f.player.moveFlying(0, .98F, .1F);
                    assertNotNull("actual native movement event reached final Speed hook", movement[0]);
                    friction.add(movement[0].getFriction());
                    event.cancel();
                });
                speed.setToggled(true);
                lag.setToggled(true);
                Pulsar.BUS.subscribe(capture);
                Pulsar.BUS.subscribe(body);
                try {
                    Pulsar.BUS.post(new TickEvent()); // Select the real nearby hurt target.
                    for (int tick = 0; tick < 3; tick++) {
                        run.time.set(tick * 50_000_000L);
                        LagRange.beginFrame(run.f.mc, false, 1, 50);
                        observation.beginOuterTick();
                        try {
                            run.f.player.onUpdate();
                            assertNotNull("credit is earned by an actually withheld native-entry body",
                                    observation.getLastWithheld());
                            assertTrue(kinds.isEmpty());
                        } finally { observation.endOuterTick(); }
                    }

                    enemy.hurtTime = 0;
                    enemy.setPosition(3.5, 64, 0);
                    enemy.setEntityBoundingBox(new AxisAlignedBB(3.2, 64, -.3, 3.8, 65.8, .3));
                    run.f.player.lastTickPosX = -.25;
                    run.time.set(150_000_000L);
                    LagRange.beginFrame(run.f.mc, false, 1, 50);
                    observation.beginOuterTick();
                    try {
                        // Actual TickEvent -> runExtraSteps -> p.onUpdate; no syntheticBody override.
                        Pulsar.BUS.post(new TickEvent());
                        assertEquals("one genuinely earned extra was entered", 1, kinds.size());
                        assertTrue("the original budget spent one of three earned credits",
                                lag.getStatus().contains("steps 3/1"));
                        run.f.player.onUpdate(); // Ordinary world body later in this same outer tick.
                        assertEquals(2, kinds.size());
                        System.out.println("LagRange earned 3/spent 1: kinds=" + kinds
                                + ", mayPlace=" + mayPlace + ", friction=" + friction);
                        assertEquals(Arrays.asList(ExecutedStep.Kind.EARNED_EXTRA, ExecutedStep.Kind.ORDINARY),
                                kinds);
                        assertEquals(Arrays.asList(false, true), mayPlace);
                        assertEquals("extra body earns no V2 ramp", .1F, friction.get(0), 1e-7);
                        assertEquals("the real ordinary body still owns its first ramp step",
                                .104F, friction.get(1), 1e-7);
                        assertEquals(1F, run.f.mc.timer.timerSpeed, 0);
                    } finally { observation.endOuterTick(); }
                } finally {
                    Pulsar.BUS.unsubscribe(body);
                    Pulsar.BUS.unsubscribe(capture);
                    lag.setToggled(false);
                    speed.setToggled(false);
                }
            } finally {
                unsafe.putObject(unsafe.staticFieldBase(utilityClient),
                        unsafe.staticFieldOffset(utilityClient), oldUtilityClient);
            }
        }
    }
}
