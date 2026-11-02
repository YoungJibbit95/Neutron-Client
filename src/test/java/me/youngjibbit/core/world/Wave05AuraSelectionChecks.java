package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.AttackScheduler;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.observation.RuntimeObservation;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Real Aura selector -> real Ordinary walking -> real swing/controller/EmbeddedChannel.
 * Target storage, health/team/invisibility and time are deterministic fixture inputs. */
public class Wave05AuraSelectionChecks {
    public static class Target extends RotationBehaviorHookTest.Player {
        boolean invisible;
        Team team;
        @Override public boolean isInvisible() { return invisible; }
        @Override public Team getTeam() { return team; }
    }

    public static class TeamPlayer extends RotationBehaviorHookTest.Player {
        Team team;
        @Override public Team getTeam() { return team; }
    }

    static final class SelectionRun implements AutoCloseable {
        final CombatSequenceWave2Checks.Run run = new CombatSequenceWave2Checks.Run(false);
        final AtomicLong clock = new AtomicLong(1000);
        final AttackScheduler scheduler = new AttackScheduler(clock::get, (min, max) -> 10);
        RotationBehaviorHookTest.Player local = run.fixture.player;
        final Target first = target(9, -.5, 10);
        final Target second = target(10, 1.5, 20);

        SelectionRun(String mode) throws Exception {
            ScaffoldOwnerContinuityTest.field(ClientRuntime.class, "attackScheduler").set(run.fixture.runtime, scheduler);
            run.scene.loadedEntityList.clear();
            run.scene.loadedEntityList.addAll(Arrays.asList(first, second));
            run.fixture.aura.getModeSetting("Mode").setValue(mode);
            run.fixture.aura.getModeSetting("Sort Mode").setValue("Health");
            run.fixture.aura.getNumberSetting("Multi Targets").setValue(2D);
            run.fixture.aura.getNumberSetting("Min CPS").setValue(10D);
            run.fixture.aura.getNumberSetting("Max CPS").setValue(10D);
            run.fixture.aura.getNumberSetting("Yaw Speed").setValue(90D);
            run.fixture.aura.getNumberSetting("Pitch Speed").setValue(90D);
            run.fixture.aura.getBooleanSetting("Best Aim Vec").setValue(false);
            // Run already selects Randomization Style=Aus and Jitter=0.
        }

        private Target target(int id, double x, float health) throws Exception {
            Target target = RotationBehaviorHookTest.bare(Target.class);
            target.setEntityId(id);
            target.worldObj = run.scene;
            target.posX = x; target.posY = 64; target.posZ = 2;
            target.setEntityBoundingBox(new AxisAlignedBB(x - .3, 64, 1.7, x + .3, 65.8, 2.3));
            ScaffoldOwnerContinuityTest.field(net.minecraft.entity.Entity.class, "dataWatcher")
                    .set(target, new net.minecraft.entity.DataWatcher(target));
            target.getDataWatcher().addObject(6, Float.valueOf(health));
            return target;
        }

        void useLocalTeam(Team team) throws Exception {
            // Existing Player fixture hardcodes getTeam()=null. Copy only its entity
            // instance state into a bare subtype before any observed motion; the real
            // selector, rotation, observer, walking and controller stay unchanged.
            TeamPlayer replacement = RotationBehaviorHookTest.bare(TeamPlayer.class);
            for (Class<?> type = RotationBehaviorHookTest.Player.class; type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    field.set(replacement, field.get(local));
                }
            }
            replacement.team = team;
            replacement.inventory = new InventoryPlayer(replacement);
            run.fixture.mc.thePlayer = replacement;
            ScaffoldOwnerContinuityTest.field(Minecraft.class, "renderViewEntity").set(run.fixture.mc, replacement);
            local = replacement;
            // No final Fixture.player field or static client reference is rewritten.
        }

        List<Object> walk() {
            // Production Minecraft advances the attack budget once per outer tick.
            scheduler.beginTick();
            clock.addAndGet(150);
            run.scene.motionTime.addAndGet(50_000_000L);
            RuntimeObservation observation = run.fixture.runtime.getObservation();
            observation.beginOuterTick();
            try { observation.observePlayerStep(local, () -> { local.onUpdateWalkingPlayer(); return true; }, step -> step.getAsBoolean()); }
            finally { observation.endOuterTick(); }
            return run.drain();
        }

        @Override public void close() throws Exception { run.close(); }
    }

    private static int index(List<Object> packets, Class<?> type) {
        for (int i = 0; i < packets.size(); i++) if (type.isInstance(packets.get(i))) return i;
        return -1;
    }
    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }
    private static void assertAttack(SelectionRun fixture, List<Object> packets, Target expected) {
        assertEquals(1, count(packets, C03PacketPlayer.class));
        assertEquals(1, count(packets, C0APacketAnimation.class));
        assertEquals(1, count(packets, C02PacketUseEntity.class));
        assertTrue(index(packets, C03PacketPlayer.class) < index(packets, C0APacketAnimation.class));
        assertTrue(index(packets, C0APacketAnimation.class) < index(packets, C02PacketUseEntity.class));
        C02PacketUseEntity attack = (C02PacketUseEntity) packets.get(index(packets, C02PacketUseEntity.class));
        assertEquals(C02PacketUseEntity.Action.ATTACK, attack.getAction());
        assertSame("actual transported target identity", expected, attack.getEntityFromWorld(fixture.run.scene));
        assertEquals("aura", fixture.run.fixture.runtime.getRotationManager().getConfirmedOwner());
        assertEquals("silent module restores the camera", 0, fixture.local.rotationYaw, 0);
    }

    @Test public void multiCyclesTwoRealTargetsOnlyAfterAcceptedControllerAttacks() throws Exception {
        try (SelectionRun fixture = new SelectionRun("Multi")) {
            assertAttack(fixture, fixture.walk(), fixture.first);
            assertAttack(fixture, fixture.walk(), fixture.second);
            assertAttack(fixture, fixture.walk(), fixture.first);
        }
    }

    @Test public void rejectedMultiAttackRetriesSameTargetBeforeAdvancing() throws Exception {
        try (SelectionRun fixture = new SelectionRun("Multi")) {
            final boolean[] rejected = {false};
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (!rejected[0] && event.isPre() && event.isOutbound() && event.getPacket() instanceof C02PacketUseEntity) {
                    assertSame(fixture.first, ((C02PacketUseEntity) event.getPacket()).getEntityFromWorld(fixture.run.scene));
                    rejected[0] = true;
                    event.cancel();
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> rejectedFrame = fixture.walk();
                assertTrue(rejected[0]);
                assertEquals(1, count(rejectedFrame, C03PacketPlayer.class));
                assertEquals(1, count(rejectedFrame, C0APacketAnimation.class));
                assertEquals(0, count(rejectedFrame, C02PacketUseEntity.class));
                assertAttack(fixture, fixture.walk(), fixture.first);
                assertAttack(fixture, fixture.walk(), fixture.second);
            } finally { Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    @Test public void singleRetainsTheFirstSortedTargetAcrossAcceptedAttacks() throws Exception {
        try (SelectionRun fixture = new SelectionRun("Single")) {
            assertAttack(fixture, fixture.walk(), fixture.first);
            assertAttack(fixture, fixture.walk(), fixture.first);
        }
    }

    @Test public void invisibleFilterSelectsVisibleTargetUntilExplicitlyAllowed() throws Exception {
        try (SelectionRun fixture = new SelectionRun("Single")) {
            fixture.first.invisible = true;
            fixture.run.fixture.aura.getBooleanSetting("Invisibles").setValue(false);
            assertAttack(fixture, fixture.walk(), fixture.second);
            fixture.run.fixture.aura.getBooleanSetting("Invisibles").setValue(true);
            assertAttack(fixture, fixture.walk(), fixture.first);
        }
    }

    @Test public void playersFilterPreventsAllPlayerAttacksUntilExplicitlyAllowed() throws Exception {
        try (SelectionRun fixture = new SelectionRun("Single")) {
            fixture.run.fixture.aura.getBooleanSetting("Players").setValue(false);
            List<Object> denied = fixture.walk();
            assertEquals(1, count(denied, C03PacketPlayer.class));
            assertEquals(0, count(denied, C0APacketAnimation.class));
            assertEquals(0, count(denied, C02PacketUseEntity.class));
            assertNull(fixture.run.fixture.runtime.getRotationManager().getConfirmedOwner());
            fixture.run.fixture.aura.getBooleanSetting("Players").setValue(true);
            assertAttack(fixture, fixture.walk(), fixture.first);
        }
    }

    @Test public void teamFilterSelectsEnemyUntilSameTeamIsExplicitlyAllowed() throws Exception {
        try (SelectionRun fixture = new SelectionRun("Single")) {
            Scoreboard scoreboard = new Scoreboard();
            Team friendly = scoreboard.createTeam("fixture-friendly");
            Team enemy = scoreboard.createTeam("fixture-enemy");
            fixture.useLocalTeam(friendly);
            fixture.first.team = friendly;
            fixture.second.team = enemy;
            fixture.run.fixture.aura.getBooleanSetting("Teams").setValue(false);
            assertAttack(fixture, fixture.walk(), fixture.second);
            fixture.run.fixture.aura.getBooleanSetting("Teams").setValue(true);
            assertAttack(fixture, fixture.walk(), fixture.first);
        }
    }
}
