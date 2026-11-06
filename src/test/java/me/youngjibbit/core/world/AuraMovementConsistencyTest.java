package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.*;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.network.play.client.*;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.*;
import net.minecraft.world.WorldSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real living input/sprint decisions, moveFlying/jump and Aura walking transport.
 * Stop before unrelated horse/base-world physics; integrate a free-space displacement explicitly. */
@RunWith(Parameterized.class)
public class AuraMovementConsistencyTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"simpleTurn"},{"preciseTurn"},{"boundTurn"},
                {"legacyRoundedTurn"},{"legacyStrictTurn"},{"firstAcquisitionSprint"},
                {"turningSprintJump"},{"survivalAttackReducesMotion"},{"cancelledAttackPreservesMotion"},
                {"preparedFrameIsConsumedOnce"},{"arsenicLazyTurns"},{"arsenicHeuristicsTurns"},{"rejectedPreparedMovementGrantsNoAttack"},{"preparedDisableReturnsFromSentPose"},
                {"neutronScaffoldMovement"},{"arsenicScaffoldMovement"},
                {"visibleAimAssistMovement"},{"arsenicAimAssistMovement"},{"scaffoldSprintDisabled"},
                {"wTapSprintMatchesPhysics"},{"scaffoldAutoJumpWithSprint"},{"scaffoldAutoJumpWithoutSprint"},
                {"velocityMovement"}});
    }
    private final String method;
    public AuraMovementConsistencyTest(String method) { this.method=method; }
    @Test public void checkInFreshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class,method); }
    static final class BeforePhysics extends RuntimeException { }
    public static class Player extends RotationBehaviorHookTest.Player {
        FoodStats food;
        @Override public FoodStats getFoodStats() { return food; }
        @Override protected boolean pushOutOfBlocks(double x,double y,double z) { return false; }
        @Override public boolean isRidingHorse() { throw new BeforePhysics(); }
    }
    public static class Remote extends EntityOtherPlayerMP {
        public Remote() { super(null,null); }
        @Override public Team getTeam() { return null; }
        @Override public boolean isInvisible() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isEntityAlive() { return !isDead; }
    }
    static final class Input extends MovementInput {
        float forward=1,strafe;
        @Override public void updatePlayerMoveState() {
            moveForward=forward*(sneak?.3f:1);moveStrafe=strafe*(sneak?.3f:1);
        }
    }
    static final class Run implements AutoCloseable {
        final Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single");
        final Player player;
        final Input input=new Input();
        PlayerMoveUpdateEvent move;
        double jumpX,jumpZ;
        double preparedJumpX,preparedJumpZ,preparedJumpY;
        boolean physicsSprint;
        Runnable beforeWalking=()->{};
        final Listener<PlayerMoveUpdateEvent> observeMove=new Listener<>(PlayerMoveUpdateEvent.class,e->move=e);
        Run(String mode) throws Exception {
            this(mode,Player.class);
        }
        Run(String mode,Class<? extends Player> playerType) throws Exception {
            player=RotationBehaviorHookTest.bare(playerType);
            for(Class<?> type=RotationBehaviorHookTest.Player.class;type!=Object.class;type=type.getSuperclass())
                for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);field.set(player,field.get(f.local));
                }
            player.inventory=new InventoryPlayer(player);player.capabilities=new PlayerCapabilities();
            player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            player.food=new FoodStats();player.movementInput=input;
            f.local=player;f.run.fixture.mc.thePlayer=player;
            ScaffoldOwnerContinuityTest.field(Minecraft.class,"renderViewEntity").set(f.run.fixture.mc,player);
            f.run.fixture.mc.gameSettings.keyBindSprint=new KeyBinding("synthetic.sprint",0,"synthetic");
            f.run.fixture.aura.getModeSetting("movefix").setValue(mode);
            f.run.fixture.aura.getModeSetting("rot_mode").setValue("Basic");
            f.run.fixture.aura.getBooleanSetting("Smooth Rotation Stop").setValue(false);
            target(.5,2);Pulsar.BUS.subscribe(observeMove);
        }
        void target(double x,double z) {
            f.first.posX=f.first.lastTickPosX=x;f.first.posZ=f.first.lastTickPosZ=z;
            f.first.setEntityBoundingBox(new AxisAlignedBB(x-.3,64,z-.3,x+.3,65.8,z+.3));
        }
        List<Object> physical(boolean jump) {
            f.scheduler.beginTick();f.clock.addAndGet(150);f.run.scene.motionTime.addAndGet(50_000_000L);
            me.youngjibbit.core.observation.RuntimeObservation observation=f.run.fixture.runtime.getObservation();
            observation.beginOuterTick();
            try { observation.observePlayerStep(player,()->{
                try { player.onLivingUpdate();fail("fixture must stop before unrelated world physics"); }
                catch(BeforePhysics expected) { }
                physicsSprint=player.isSprinting();
                preparedJumpX=player.motionX;preparedJumpZ=player.motionZ;preparedJumpY=player.motionY;
                player.motionX=player.motionZ=0;
                if(jump) { player.jump();jumpX=player.motionX;jumpZ=player.motionZ; }
                player.moveFlying(MovementInput.moveStrafe*.98f,MovementInput.moveForward*.98f,.1f);
                player.setPosition(player.posX+player.motionX,player.posY,player.posZ+player.motionZ);
                beforeWalking.run();player.onUpdateWalkingPlayer();return true;
            },step->step.getAsBoolean()); } finally { observation.endOuterTick(); }
            return f.run.drain();
        }
        float sentYaw(List<Object> packets) {
            C03PacketPlayer sent=null;
            for(Object packet:packets)if(packet instanceof C03PacketPlayer)sent=(C03PacketPlayer)packet;
            assertNotNull("ordinary movement reached real transport",sent);
            assertTrue("positive control: rotation actually changed",sent.getRotating());
            return sent.getYaw();
        }
        void remoteTarget() throws Exception {
            remoteTarget(Remote.class);
        }
        void remoteTarget(Class<? extends Remote> type) throws Exception {
            Remote target=RotationBehaviorHookTest.bare(type);target.worldObj=f.run.scene;target.setEntityId(19);
            target.inventory=new InventoryPlayer(target);target.posX=.5;target.posY=64;target.posZ=2;
            target.setEntityBoundingBox(new AxisAlignedBB(.2,64,1.7,.8,65.8,2.3));
            DataWatcher data=new DataWatcher(target);data.addObject(0,(byte)0);data.addObject(6,20F);
            ScaffoldOwnerContinuityTest.field(Entity.class,"dataWatcher").set(target,data);
            f.run.scene.loadedEntityList.clear();f.run.scene.loadedEntityList.add(target);
            player.getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(2);
            ScaffoldOwnerContinuityTest.field(PlayerControllerMP.class,"currentGameType").set(f.run.fixture.mc.playerController,WorldSettings.GameType.SURVIVAL);
        }
        @Override public void close() throws Exception { Pulsar.BUS.unsubscribe(observeMove);f.close(); }
    }
    public static class Checks {
        private void turn(String mode) throws Exception {
            try(Run run=new Run(mode)) {
                run.f.walk();assertEquals(0,run.f.run.fixture.runtime.getRotationManager().getEffectiveYaw(),.1);
                run.target(-.5,2);
                float sent=run.sentYaw(run.physical(false));
                assertTrue(Math.abs(sent)>5);assertNotNull(run.move);
                assertEquals("acceleration and the same step's C03 must share a yaw basis",0,
                        MathHelper.wrapAngleTo180_float(run.move.getYaw()-sent),.001f);
                for(int[] keys:new int[][]{{0,1},{1,1},{1,0},{1,-1},{0,-1},{-1,-1},{-1,0},{-1,1}}) {
                    run.input.strafe=keys[0];run.input.forward=keys[1];
                    run.player.setPosition(.5,64,.5);run.target(keys[0]>=0?-1:1.5,2);
                    run.physical(false);
                    RotationManager rotation=run.f.run.fixture.runtime.getRotationManager();
                    assertEquals("aura",rotation.getConfirmedOwner());
                    assertEquals("all eight keyboard directions share the sent basis",run.move.getYaw(),rotation.getEffectiveYaw(),.001);
                    assertTrue(Math.abs(run.move.getForward())<=.98001f && Math.abs(run.move.getStrafe())<=.98001f);
                }
            }
        }
        @Test public void simpleTurn() throws Exception { turn("Einfach"); }
        @Test public void preciseTurn() throws Exception { turn("Präzise"); }
        @Test public void boundTurn() throws Exception { turn("Rotationsgebunden"); }
        @Test public void legacyRoundedTurn() throws Exception { turn("Legacy Rounded"); }
        @Test public void legacyStrictTurn() throws Exception { turn("Legacy Strict"); }
        @Test public void firstAcquisitionSprint() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.target(-1.8,.5);run.f.run.fixture.mc.gameSettings.keyBindSprint.pressed=true;
                float sent=run.sentYaw(run.physical(false));
                float forward=MoveFixController.correct(0,1,0,sent,MoveFixController.Mode.SIMPLE).forward;
                assertEquals("sprint admission must use this step's corrected forward key",forward>=.8f,run.player.isSprinting());
            }
        }
        @Test public void turningSprintJump() throws Exception {
            try(Run run=new Run("Rotationsgebunden")) {
                run.f.walk();run.target(-.5,2);run.f.run.fixture.mc.gameSettings.keyBindSprint.pressed=true;
                float sent=run.sentYaw(run.physical(true));
                assertEquals(-Math.sin(Math.toRadians(sent))*.2,run.jumpX,3e-5);
                assertEquals(Math.cos(Math.toRadians(sent))*.2,run.jumpZ,3e-5);
            }
        }
        @SuppressWarnings("unchecked")
        @Test public void preparedFrameIsConsumedOnce() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.walk();run.target(-.5,2);
                me.youngjibbit.core.observation.RuntimeObservation observation=run.f.run.fixture.runtime.getObservation();
                Field field=ScaffoldOwnerContinuityTest.field(me.youngjibbit.core.observation.RuntimeObservation.class,"source");
                java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample> original=
                        (java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)field.get(observation);
                java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean();
                int[] reached={0};
                run.beforeWalking=()->{
                    try { field.set(observation,(java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)()->{
                        if(once.compareAndSet(false,true)) { reached[0]++;run.player.onUpdateWalkingPlayer(); }
                        return original.get();
                    }); } catch(Exception error) { throw new AssertionError(error); }
                };
                List<Object> packets;
                try { packets=run.physical(false); } finally { field.set(observation,original); }
                assertEquals(1,reached[0]);
                assertEquals("one physical step cannot consume its prepared frame twice",1,
                        packets.stream().filter(C03PacketPlayer.class::isInstance).count());
            }
        }
        @Test public void arsenicLazyTurns() throws Exception { arsenic("Arsenic Lazy"); }
        @Test public void arsenicHeuristicsTurns() throws Exception { arsenic("Arsenic Heuristics"); }
        private void arsenic(String mode) throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.getModeSetting("rot_mode").setValue(mode);
                run.f.walk();run.target(-.5,2);float largest=0;
                for(int tick=0;tick<6;tick++) {
                    List<Object> packets=run.physical(false);
                    for(Object packet:packets)if(packet instanceof C03PacketPlayer && ((C03PacketPlayer)packet).getRotating()) {
                        float yaw=((C03PacketPlayer)packet).getYaw();largest=Math.max(largest,Math.abs(yaw));
                        assertEquals(mode+" same-step movement basis",0,MathHelper.wrapAngleTo180_float(run.move.getYaw()-yaw),.001f);
                    }
                }
                assertTrue("positive control: controller turned",largest>.5f);
            }
        }
        @Test public void rejectedPreparedMovementGrantsNoAttack() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.walk();run.target(-.5,2);
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C03PacketPlayer)e.cancel();};
                Pulsar.BUS.subscribe(hooks);List<Object> packets;
                try { packets=run.physical(false); } finally { Pulsar.BUS.unsubscribe(hooks); }
                assertEquals(0,packets.stream().filter(C02PacketUseEntity.class::isInstance).count());
                RotationManager rotation=run.f.run.fixture.runtime.getRotationManager();
                assertFalse(rotation.isMotionAccepted());assertNull(rotation.getConfirmedOwner());
                assertNull(rotation.getPreparedMovement());
                run.physical(false);assertEquals("aura",rotation.getConfirmedOwner());
            }
        }
        @Test public void preparedDisableReturnsFromSentPose() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.getBooleanSetting("Smooth Rotation Stop").setValue(true);
                run.f.walk();run.target(-.5,2);
                Listener<me.youngjibbit.event.motion.EventMotion> disable=new Listener<>(me.youngjibbit.event.motion.EventMotion.class,
                        e->{if(e.isPre())run.f.run.fixture.aura.setToggled(false);},me.zero.alpine.event.EventPriority.LOWEST);
                Pulsar.BUS.subscribe(disable);List<Object> packets;
                try { packets=run.physical(false); } finally { Pulsar.BUS.unsubscribe(disable); }
                assertEquals(0,packets.stream().filter(C02PacketUseEntity.class::isInstance).count());
                RotationManager rotation=run.f.run.fixture.runtime.getRotationManager();
                assertEquals("a rotation-only return retains the pose already consumed by physics",
                        RotationManager.AURA_RELEASE_SOURCE,rotation.getConfirmedOwner());
                float first=rotation.getEffectiveYaw();assertTrue(first>5);
                run.physical(false);
                assertEquals(RotationManager.AURA_RELEASE_SOURCE,rotation.getConfirmedOwner());
                assertTrue(rotation.getEffectiveYaw()>0 && rotation.getEffectiveYaw()<first);
                assertEquals(run.move.getYaw(),rotation.getEffectiveYaw(),.001);
            }
        }
        @Test public void neutronScaffoldMovement() throws Exception { scaffold("Neutron"); }
        @Test public void arsenicScaffoldMovement() throws Exception { scaffold("Arsenic"); }
        @Test public void visibleAimAssistMovement() throws Exception { assist("Normal"); }
        @Test public void arsenicAimAssistMovement() throws Exception { assist("Arsenic Lazy"); }
        private void assist(String mode) throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);run.target(-.5,2);
                me.youngjibbit.module.impl.combat.AimAssist assist=new me.youngjibbit.module.impl.combat.AimAssist();
                assist.getModeSetting("Rotations").setValue(mode);
                assist.getModeSetting("Randomization Style").setValue("Aus");
                assist.getNumberSetting("Jitter").setValue(0D);assist.setToggled(true);
                try {
                    float sent=run.sentYaw(run.physical(false));
                    assertEquals("aimassist",run.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    assertEquals("visible assistance applies before acceleration",run.move.getYaw(),sent,.001);
                    assertEquals("walking must not apply visible assistance a second time",sent,run.player.rotationYaw,.001);
                } finally { assist.setToggled(false); }
            }
        }
        @Test public void scaffoldSprintDisabled() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);
                run.player.inventory.mainInventory[3]=new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.stone,64);
                me.youngjibbit.module.impl.world.Scaffold scaffold=run.f.run.fixture.scaffold;
                scaffold.setToggled(false);scaffold.getBooleanSetting("Sprint").setValue(false);scaffold.setToggled(true);
                run.f.run.fixture.mc.gameSettings.keyBindSprint.pressed=true;
                try {
                    run.physical(false);
                    assertEquals("scaffold",run.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    assertFalse("living sprint admission must respect Scaffold's disabled sprint",run.player.isSprinting());
                } finally { scaffold.setToggled(false); }
            }
        }
        @Test public void wTapSprintMatchesPhysics() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.first.hurtTime=2;run.f.run.fixture.mc.gameSettings.keyBindSprint.pressed=true;
                me.youngjibbit.module.impl.ghost.WTap2 tap=new me.youngjibbit.module.impl.ghost.WTap2();
                tap.getModeSetting("Mode").setValue("Spoof");tap.setToggled(true);
                try {
                    run.physical(false);
                    assertFalse("positive control: WTap suppresses sprint",run.player.isSprinting());
                    assertEquals("the sprint state sent with movement must be the state used by physics",
                            run.physicsSprint,run.player.isServerSprintState());
                } finally {
                    // Production disable restores physical keyboard state; this fixture has no input device.
                    Pulsar.BUS.unsubscribe(tap);
                    ScaffoldOwnerContinuityTest.field(me.youngjibbit.module.Module.class,"toggled").set(tap,false);
                }
            }
        }
        @Test public void scaffoldAutoJumpWithSprint() throws Exception { autoJump(true); }
        @Test public void scaffoldAutoJumpWithoutSprint() throws Exception { autoJump(false); }
        private void autoJump(boolean sprint) throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);
                run.player.inventory.mainInventory[3]=new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.stone,64);
                me.youngjibbit.module.impl.world.Scaffold scaffold=run.f.run.fixture.scaffold;
                scaffold.setToggled(false);scaffold.getBooleanSetting("Sprint").setValue(sprint);
                scaffold.getBooleanSetting("Auto Jump").setValue(true);scaffold.setToggled(true);
                run.f.run.fixture.mc.gameSettings.keyBindSprint.pressed=true;
                try {
                    float sent=run.sentYaw(run.physical(false));
                    assertEquals(.42,run.preparedJumpY,1e-6);
                    assertEquals(-Math.sin(Math.toRadians(sent))*(sprint?.2:0),run.preparedJumpX,3e-5);
                    assertEquals(Math.cos(Math.toRadians(sent))*(sprint?.2:0),run.preparedJumpZ,3e-5);
                    assertEquals(sprint,run.physicsSprint);
                } finally { scaffold.setToggled(false); }
            }
        }
        @SuppressWarnings("unchecked")
        @Test public void velocityMovement() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);run.target(-.5,2);
                me.youngjibbit.module.impl.combat.Velocity velocity=new me.youngjibbit.module.impl.combat.Velocity();
                Map<Class<? extends me.youngjibbit.module.Module>,me.youngjibbit.module.Module> modules=
                        (Map<Class<? extends me.youngjibbit.module.Module>,me.youngjibbit.module.Module>)ScaffoldOwnerContinuityTest
                                .field(me.youngjibbit.module.ModuleManager.class,"modulesByClass").get(Pulsar.INSTANCE.getMm());
                modules.put(me.youngjibbit.module.impl.combat.Velocity.class,velocity);velocity.setToggled(true);
                try {
                    me.youngjibbit.event.packet.EventPacket applied=new me.youngjibbit.event.packet.EventPacket(
                            new net.minecraft.network.play.server.S12PacketEntityVelocity(run.player.getEntityId(),.2,.2,0),
                            run.f.run.fixture.network.getClientPackets());
                    applied.setEventFlow(me.youngjibbit.event.EventFlow.INBOUND);applied.setEventPhase(me.zero.alpine.event.EventPhase.POST);
                    Pulsar.BUS.post(applied);
                    float sent=run.sentYaw(run.physical(false));
                    assertEquals("velocity",run.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    assertEquals(run.move.getYaw(),sent,.001);
                } finally { velocity.setToggled(false); }
            }
        }
        private void scaffold(String approach) throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);
                run.player.inventory.mainInventory[3] = new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.stone,64);
                me.youngjibbit.module.impl.world.Scaffold scaffold=run.f.run.fixture.scaffold;
                scaffold.setToggled(false);scaffold.getModeSetting("Scaffold Approach").setValue(approach);scaffold.setToggled(true);
                int accepted=0;
                try {
                    run.f.walk();
                    assertEquals("positive control: ordinary Scaffold accepts a real plan", "scaffold",
                            run.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    for(int i=0;i<5;i++) {
                        run.physical(false);RotationManager rotation=run.f.run.fixture.runtime.getRotationManager();
                        if("scaffold".equals(rotation.getConfirmedOwner())) {
                            accepted++;assertEquals(approach,run.move.getYaw(),rotation.getEffectiveYaw(),.001);
                        }
                    }
                    assertTrue("positive control: actual Scaffold source accepted",accepted>0);
                } finally { scaffold.setToggled(false); }
            }
        }
        @Test public void survivalAttackReducesMotion() throws Exception { attack(false); }
        @Test public void cancelledAttackPreservesMotion() throws Exception { attack(true); }
        private void attack(boolean cancelled) throws Exception {
            try(Run run=new Run("Einfach")) {
                run.remoteTarget();run.player.motionX=.4;run.player.motionZ=.2;run.player.sprint=true;
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{if(cancelled && e.isPre() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity)e.cancel();};
                Pulsar.BUS.subscribe(hooks);
                List<Object> packets;
                try { packets=run.f.walk(); } finally { Pulsar.BUS.unsubscribe(hooks); }
                assertEquals(cancelled?0:1,packets.stream().filter(C02PacketUseEntity.class::isInstance).count());
                assertEquals(.4*(cancelled?1:.6),run.player.motionX,1e-9);
                assertEquals(.2*(cancelled?1:.6),run.player.motionZ,1e-9);
                assertEquals(cancelled,run.player.isSprinting());
            }
        }
    }
}
