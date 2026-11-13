package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.packet.LivingEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.FakeLag;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import net.minecraft.world.WorldSettings;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class FakeLagBehaviorHookTest {
    @Test public void onlyOrdinaryUpdatesAdvancePulseAndDuplicateBodiesCannotSpendAnotherWindow() throws Exception { run("onlyOrdinaryUpdatesAdvancePulseAndDuplicateBodiesCannotSpendAnotherWindow"); }
    @Test public void spoofActivatesOnTheFollowingUpdateAndGroundLossPublishesTheCurrentConnection() throws Exception { run("spoofActivatesOnTheFollowingUpdateAndGroundLossPublishesTheCurrentConnection"); }
    @Test public void selectionUsesOriginalLastTieAndAlwaysSpoofWhileRejectingRemovedEntities() throws Exception { run("selectionUsesOriginalLastTieAndAlwaysSpoofWhileRejectingRemovedEntities"); }
    @Test public void forcedFirstHitUsesTheNativeControllerOnceAndNeverLeavesADelayedAutomaticAttack() throws Exception { run("forcedFirstHitUsesTheNativeControllerOnceAndNeverLeavesADelayedAutomaticAttack"); }
    @Test public void registeredHitProducesOneWTapAndTimerLeaseYieldsToOtherOwners() throws Exception { run("registeredHitProducesOneWTapAndTimerLeaseYieldsToOtherOwners"); }
    @Test public void worldUnloadCannotReacquireTheOldWorldBeforeMinecraftReplacesIt() throws Exception { run("worldUnloadCannotReacquireTheOldWorldBeforeMinecraftReplacesIt"); }
    @Test public void timerRestoresAcrossPlayerAndWorldReplacementWhenTheSameTimerSurvives() throws Exception { run("timerRestoresAcrossPlayerAndWorldReplacementWhenTheSameTimerSurvives"); }
    @Test public void focusLossAndDisableReleaseOnlyCurrentSceneWhileLookPacketsNeverInventAnOrigin() throws Exception { run("focusLossAndDisableReleaseOnlyCurrentSceneWhileLookPacketsNeverInventAnOrigin"); }
    @Test public void releasedLookPacketsEvaluateDynamicCriteriaEvenWithEarlyReleaseDisabled() throws Exception { run("releasedLookPacketsEvaluateDynamicCriteriaEvenWithEarlyReleaseDisabled"); }
    @Test public void aRegisteredHitRemainsVisibleUntilPulseDispatchCompletes() throws Exception { run("aRegisteredHitRemainsVisibleUntilPulseDispatchCompletes"); }
    @Test public void sourceGroundLossStillEvaluatesReleaseAndKeepsItsRangeTimerUntilExpiry() throws Exception { run("sourceGroundLossStillEvaluatesReleaseAndKeepsItsRangeTimerUntilExpiry"); }
    @Test public void firstHitAndItsAttackMarkerSurviveTheSourceAuraOnlyEarlyReturn() throws Exception { run("firstHitAndItsAttackMarkerSurviveTheSourceAuraOnlyEarlyReturn"); }
    @Test public void spoofSamplesResetPositionAfterDispatchCallbacks() throws Exception { run("spoofSamplesResetPositionAfterDispatchCallbacks"); }
    private static void run(String method) throws Exception { Wave2IsolatedCase.run(Checks.class, method); }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }

    public static class World extends WorldClient {
        Map<Integer, Entity> entities;
        public World() { super(null, null, 0, null, null); }
        @Override public Entity getEntityByID(int id) { return entities.get(id); }
    }
    public static class Enemy extends EntityOtherPlayerMP {
        public Enemy() { super(null, null); }
        @Override public boolean isEntityAlive() { return !isDead; }
        @Override public boolean canEntityBeSeen(Entity entity) { return true; }
    }
    private static final class Fixture implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
        final World world = RotationBehaviorHookTest.bare(World.class);
        final AtomicLong millis = new AtomicLong();
        final FakeLag module = new FakeLag(millis::get, f.mc, (a, b) -> a);
        final PacketPipeline pipe = f.network.getClientPackets();
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            world.entities = new HashMap<>();
            field(net.minecraft.world.World.class, "playerEntities").set(world, new ArrayList<EntityPlayer>());
            field(net.minecraft.world.World.class, "loadedEntityList").set(world, new ArrayList<Entity>());
            f.mc.theWorld = world; f.player.worldObj = world; f.player.sendQueue.clientWorldController = world;
            field(Module.class, "toggled").setBoolean(f.aura, false); field(Module.class, "toggled").setBoolean(f.scaffold, false);
            Map<Class<? extends Module>, Module> modules = (Map<Class<? extends Module>, Module>) field(ModuleManager.class, "modulesByClass").get(Pulsar.INSTANCE.getMm());
            modules.put(FakeLag.class, module);
            f.mc.gameSettings = RotationBehaviorHookTest.bare(GameSettings.class); f.mc.timer = new Timer(20, () -> 0);
            f.player.onGround = true; f.player.capabilities = new PlayerCapabilities();
            f.player.setEntityBoundingBox(new AxisAlignedBB(-.3, 0, -.3, .3, 1.8, .3));
            field(net.minecraft.client.network.NetHandlerPlayClient.class, "doneLoadingTerrain").setBoolean(f.player.sendQueue, true);
            field(PlayerControllerMP.class, "currentGameType").set(f.mc.playerController, WorldSettings.GameType.SPECTATOR);
            field(PacketPipeline.class, "delays").set(pipe, new PacketDelays(() -> millis.get() * 1_000_000L));
            module.getBooleanSetting("Smart").setValue(false);
        }
        Enemy enemy(int id, double x) throws Exception {
            Enemy e = RotationBehaviorHookTest.bare(Enemy.class); e.worldObj = world; e.setEntityId(id); e.setPosition(x, 0, 0);
            e.setEntityBoundingBox(new AxisAlignedBB(x - .3, 0, -.3, x + .3, 1.8, .3));
            world.entities.put(id, e); world.playerEntities.add(e); world.loadedEntityList.add(e); return e;
        }
        void enable() { module.setToggled(true); }
        void update(long time, boolean nextTick) {
            millis.set(time); if (nextTick) f.player.ticksExisted++;
            RuntimeObservation observation = f.runtime.getObservation(); observation.beginOuterTick();
            f.runtime.getAttackScheduler().beginTick();
            try { observation.observePlayerStep(f.player, () -> { Pulsar.BUS.post(new LivingEvent()); return true; }, body -> body.getAsBoolean()); }
            finally { observation.endOuterTick(); }
        }
        List<Packet<?>> drain() { List<Packet<?>> result = new ArrayList<>(); Object packet; while ((packet = f.channel.readOutbound()) != null) result.add((Packet<?>) packet); return result; }
        void timerDown(Enemy enemy) {
            module.getBooleanSetting("Smart").setValue(true); module.getModeSetting("Keep range mode").setValue("Timer down");
            long start = millis.get(); update(start + 101, true); Pulsar.BUS.post(new AttackEvent(enemy)); update(start + 102, true);
            assertEquals(.75f, f.mc.timer.timerSpeed, 0);
        }
        @Override public void close() throws Exception { module.setToggled(false); f.close(); }
    }

    public static final class Checks {
        @Test public void aRegisteredHitRemainsVisibleUntilPulseDispatchCompletes() throws Exception {
            try (Fixture f = new Fixture()) {
                f.f.player.setPosition(10, 0, 0); Enemy enemy = f.enemy(7, 14.5);
                f.module.getBooleanSetting("Smart").setValue(true); f.module.getBooleanSetting("Early release").setValue(true);
                f.module.getNumberSetting("Delay (min ms)").setValue(1000D); f.module.getNumberSetting("Delay (max ms)").setValue(1000D);
                f.module.getNumberSetting("Recoil time (ms)").setValue(0D); f.enable(); f.update(101, false);
                Packet<?> movement = new C03PacketPlayer.C04PacketPlayerPosition(12, 0, 0, true); f.f.network.sendPacket(movement);
                f.f.player.setPosition(12.5, 0, 0); Pulsar.BUS.post(new AttackEvent(enemy)); f.update(102, true);
                assertEquals(Collections.singletonList(movement), f.drain());
                assertEquals("the source attack marker vetoes early position rollback during dispatch", 12.5, f.f.player.posX, 0);
                assertEquals(12, f.module.getServerPosition().xCoord, 0);
                f.update(103, true); f.f.network.sendPacket(new C0APacketAnimation());
                assertEquals("the marker is cleared after dispatch, so the following Update can lag again", 1, f.module.queuedPackets());
            }
        }
        @Test public void sourceGroundLossStillEvaluatesReleaseAndKeepsItsRangeTimerUntilExpiry() throws Exception {
            try (Fixture f = new Fixture()) {
                f.f.player.setPosition(10, 0, 0); Enemy enemy = f.enemy(7, 14.5);
                f.module.getBooleanSetting("Smart").setValue(true); f.module.getBooleanSetting("Only onGround").setValue(true);
                f.module.getModeSetting("Keep range mode").setValue("Timer down"); f.module.getNumberSetting("Timer ticks").setValue(3D);
                f.module.getNumberSetting("Delay (min ms)").setValue(1000D); f.module.getNumberSetting("Delay (max ms)").setValue(1000D);
                f.module.getNumberSetting("Recoil time (ms)").setValue(0D); f.enable(); f.update(101, false);
                f.f.network.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(12.2, 0, 0, true));
                f.f.player.setPosition(11.35, 0, 0); f.f.player.onGround = false;
                Pulsar.BUS.post(new AttackEvent(enemy)); f.update(102, true);
                assertEquals(1, f.drain().size()); assertFalse(f.module.isBlinking());
                assertEquals("release criteria use the valid target despite the outer ground return", .75f, f.f.mc.timer.timerSpeed, 0);
                f.update(127, true); assertEquals(.75f, f.f.mc.timer.timerSpeed, 0);
                f.update(252, true); assertEquals("strict 150 ms timer endpoint", .75f, f.f.mc.timer.timerSpeed, 0);
                f.update(253, true); assertEquals(1, f.f.mc.timer.timerSpeed, 0);
            }
        }
        @Test public void firstHitAndItsAttackMarkerSurviveTheSourceAuraOnlyEarlyReturn() throws Exception {
            try (Fixture f = new Fixture()) {
                Enemy enemy = f.enemy(7, 3.15); f.module.getBooleanSetting("Smart").setValue(true);
                f.module.getBooleanSetting("Only on killAura").setValue(true); f.module.getBooleanSetting("Force first hit").setValue(true);
                f.module.getModeSetting("Keep range mode").setValue("WTap");
                f.f.mc.objectMouseOver = new MovingObjectPosition(enemy, enemy.getPositionVector()); f.enable(); f.update(101, false);
                assertEquals(1, f.drain().stream().filter(packet -> packet instanceof C02PacketUseEntity).count());
                assertFalse(f.module.isBlinking()); f.update(102, true); assertTrue(f.drain().isEmpty());
                field(Module.class, "toggled").setBoolean(f.f.aura, true); f.update(103, true);
                MovementInputEvent input = new MovementInputEvent(0, 1, false, false); Pulsar.BUS.post(input);
                assertEquals("native first-hit AttackEvent survives both post-dispatch and ineligible boundaries", 0, input.getForward(), 0);
            }
        }
        @Test public void spoofSamplesResetPositionAfterDispatchCallbacks() throws Exception {
            try (Fixture f = new Fixture()) {
                f.enemy(7, 5); f.module.getModeSetting("Mode").setValue("Spoof"); f.enable(); f.update(51, false); f.update(52, true);
                Packet<?> held = new C00PacketKeepAlive(1); f.f.network.sendPacket(held); assertEquals(1, f.module.queuedPackets());
                NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer();
                observer.action = event -> { if (event.isPost() && event.getPacket() == held) f.f.player.setPosition(3, 0, 0); };
                Pulsar.BUS.subscribe(observer);
                try {
                    f.f.player.setPosition(2, 0, 0); f.update(53, true);
                    assertEquals(Collections.singletonList(held), f.drain()); assertEquals(3, f.module.getServerPosition().xCoord, 0);
                    assertFalse(f.module.isBlinking());
                } finally { Pulsar.BUS.unsubscribe(observer); }
            }
        }
        @Test public void releasedLookPacketsEvaluateDynamicCriteriaEvenWithEarlyReleaseDisabled() throws Exception {
            try (Fixture f = new Fixture()) {
                f.f.player.setPosition(10, 0, 0); f.enemy(7, 14.5);
                f.module.getBooleanSetting("Smart").setValue(true);
                f.module.getBooleanSetting("Experimental dynamic hurt time").setValue(true);
                f.module.getNumberSetting("Delay (min ms)").setValue(1000D);
                f.module.getNumberSetting("Delay (max ms)").setValue(1000D);
                f.module.getNumberSetting("Recoil time (ms)").setValue(0D);
                assertFalse(f.module.getBooleanSetting("Early release").getValue());
                f.enable(); f.update(101, false); assertTrue(f.module.isBlinking());
                f.f.player.hurtTime = 9;
                f.f.network.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(0, 0, true));
                f.f.network.sendPacket(new C03PacketPlayer.C05PacketPlayerLook(0, 0, true));
                assertEquals(2, f.module.queuedPackets());
                f.update(102, true); assertEquals(2, f.drain().size());
                assertEquals(10, f.module.getServerPosition().xCoord, 0); assertEquals(10, f.f.player.posX, 0);
                // Update counted once at hurtTime 9; both release callbacks
                // count again, so the next Update enters the source override.
                f.update(103, true); f.f.network.sendPacket(new C0APacketAnimation());
                assertEquals(1, f.module.queuedPackets()); assertTrue(f.drain().isEmpty());
            }
        }
        @Test public void onlyOrdinaryUpdatesAdvancePulseAndDuplicateBodiesCannotSpendAnotherWindow() throws Exception {
            try (Fixture f = new Fixture()) {
                f.enemy(7, 5); f.enable(); f.millis.set(51); Pulsar.BUS.post(new LivingEvent());
                assertFalse(f.module.isBlinking()); f.update(51, false); assertTrue(f.module.isBlinking());
                Packet<?> packet = new C0APacketAnimation(); f.f.network.sendPacket(packet); assertEquals(1, f.module.queuedPackets());
                f.update(101, false); assertEquals(1, f.module.queuedPackets()); assertTrue(f.drain().isEmpty());
                f.update(101, true); assertEquals(Collections.singletonList(packet), f.drain()); assertFalse(f.module.isBlinking());
            }
        }
        @Test public void spoofActivatesOnTheFollowingUpdateAndGroundLossPublishesTheCurrentConnection() throws Exception {
            try (Fixture f = new Fixture()) {
                f.enemy(7, 5); f.module.getModeSetting("Mode").setValue("Spoof"); f.module.getBooleanSetting("Only onGround").setValue(true); f.enable();
                f.update(51, false); Packet<?> startup = new C03PacketPlayer(true); f.f.network.sendPacket(startup);
                assertEquals(Collections.singletonList(startup), f.drain());
                f.update(52, true); Packet<?> held = new C00PacketKeepAlive(9); f.f.network.sendPacket(held);
                assertEquals(1, f.module.queuedPackets()); assertTrue(f.drain().isEmpty());
                f.f.player.onGround = false; f.update(53, true); assertEquals(Collections.singletonList(held), f.drain());
                assertFalse(f.module.isBlinking()); assertEquals(0, f.module.queuedPackets());
            }
        }
        @Test public void selectionUsesOriginalLastTieAndAlwaysSpoofWhileRejectingRemovedEntities() throws Exception {
            try (Fixture f = new Fixture()) {
                Enemy first = f.enemy(7, 5), last = f.enemy(8, 5); f.enable(); f.update(51, false); assertSame(last, f.module.getTarget());
                f.world.entities.remove(8); f.update(52, true); assertSame(first, f.module.getTarget());
                first.setPosition(100, 0, 0); first.setEntityBoundingBox(new AxisAlignedBB(99.7, 0, -.3, 100.3, 1.8, .3));
                f.update(53, true); assertNull(f.module.getTarget());
                f.module.getBooleanSetting("Always spoof").setValue(true); f.update(110, true); assertSame(first, f.module.getTarget());
                f.module.getBooleanSetting("Only on killAura").setValue(true); f.update(170, true); assertFalse(f.module.isBlinking());
            }
        }
        @Test public void forcedFirstHitUsesTheNativeControllerOnceAndNeverLeavesADelayedAutomaticAttack() throws Exception {
            try (Fixture f = new Fixture()) {
                Enemy enemy = f.enemy(7, 2.8); f.module.getNumberSetting("Attack range").setValue(0D);
                f.module.getNumberSetting("Recoil time (ms)").setValue(0D); f.module.getNumberSetting("Delay (min ms)").setValue(1000D);
                f.module.getNumberSetting("Delay (max ms)").setValue(1000D); f.module.getBooleanSetting("Force first hit").setValue(true);
                f.f.mc.objectMouseOver = new MovingObjectPosition(enemy, enemy.getPositionVector()); f.enable();
                f.millis.set(1); Pulsar.BUS.post(new LivingEvent()); assertTrue(f.drain().isEmpty());
                f.update(1, false); List<Packet<?>> sent = f.drain();
                assertEquals(1, sent.stream().filter(p -> p instanceof C02PacketUseEntity).count());
                assertEquals(1, sent.stream().filter(p -> p instanceof C0APacketAnimation).count());
                assertEquals(0, f.module.queuedPackets());
                f.update(2, true); f.module.setToggled(false);
                assertEquals(0, f.drain().stream().filter(p -> p instanceof C02PacketUseEntity).count());
            }
        }
        @Test public void registeredHitProducesOneWTapAndTimerLeaseYieldsToOtherOwners() throws Exception {
            try (Fixture f = new Fixture()) {
                Enemy enemy = f.enemy(7, 3.15); f.enable(); f.module.getBooleanSetting("Smart").setValue(true);
                f.module.getModeSetting("Keep range mode").setValue("WTap"); f.update(101, true);
                Pulsar.BUS.post(new AttackEvent(enemy)); f.update(102, true);
                MovementInputEvent first = new MovementInputEvent(1, 1, false, false); Pulsar.BUS.post(first); assertEquals(0, first.getForward(), 0);
                MovementInputEvent next = new MovementInputEvent(1, 1, false, false); Pulsar.BUS.post(next); assertEquals(1, next.getForward(), 0);
                f.timerDown(enemy); f.f.mc.timer.timerSpeed = .5f; f.module.setToggled(false); assertEquals(.5f, f.f.mc.timer.timerSpeed, 0);
            }
        }
        @Test public void worldUnloadCannotReacquireTheOldWorldBeforeMinecraftReplacesIt() throws Exception {
            try (Fixture f = new Fixture()) {
                f.enemy(7, 5); f.enable(); f.update(51, false); f.f.network.sendPacket(new C03PacketPlayer(true)); assertEquals(1, f.module.queuedPackets());
                Pulsar.BUS.post(new WorldEvent.Unload(f.world)); f.update(60, true);
                assertFalse(f.module.isBlinking()); assertNull(f.module.getTarget()); assertEquals(0, f.pipe.fakeLag().size()); assertTrue(f.drain().isEmpty());
                f.module.setToggled(false); f.module.setToggled(true); f.update(150, true); assertNull(f.module.getTarget());
                Pulsar.BUS.post(new WorldEvent.Load(f.world)); f.update(201, true); assertNotNull(f.module.getTarget());
            }
        }
        @Test public void timerRestoresAcrossPlayerAndWorldReplacementWhenTheSameTimerSurvives() throws Exception {
            try (Fixture f = new Fixture()) {
                Enemy enemy = f.enemy(7, 3.15); f.enable(); f.timerDown(enemy);
                f.f.mc.theWorld = RotationBehaviorHookTest.bare(WorldClient.class);
                f.f.mc.thePlayer = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
                Pulsar.BUS.post(new TickEvent()); assertEquals(1, f.f.mc.timer.timerSpeed, 0);
                assertFalse(f.module.isBlinking()); assertEquals(0, f.pipe.fakeLag().size());
            }
        }
        @Test public void focusLossAndDisableReleaseOnlyCurrentSceneWhileLookPacketsNeverInventAnOrigin() throws Exception {
            try (Fixture f = new Fixture()) {
                f.f.player.setPosition(100, 64, 100); Enemy enemy = f.enemy(7, 105); enemy.setPosition(105, 64, 100);
                enemy.setEntityBoundingBox(new AxisAlignedBB(104.7, 64, 99.7, 105.3, 65.8, 100.3));
                f.enable(); f.update(51, false);
                Packet<?> look = new C03PacketPlayer.C05PacketPlayerLook(40, 20, true); f.f.network.sendPacket(look);
                f.update(101, true); assertEquals(Collections.singletonList(look), f.drain());
                assertEquals(100, f.module.getServerPosition().xCoord, 0); assertEquals(64, f.module.getServerPosition().yCoord, 0);
                f.update(152, true); Packet<?> action = new C0APacketAnimation(); f.f.network.sendPacket(action);
                f.f.mc.inGameHasFocus = false; Pulsar.BUS.post(new TickEvent());
                assertEquals(Collections.singletonList(action), f.drain()); assertFalse(f.module.isBlinking());
            }
        }
    }
}
