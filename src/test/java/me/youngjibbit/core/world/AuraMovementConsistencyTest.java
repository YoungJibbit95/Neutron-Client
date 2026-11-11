package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.module.impl.player.NoSlow;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
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
                {"velocityMovement"},{"intaveReduceMotion"},{"legitJumpresetInput"},{"intaveBlockAndSwordPulse"},{"intaveWallAcceleration"},
                {"acquisitionMargin"},{"smartAcquisitionMargin"},{"marginConfigReset"},
                {"pendingBlockCompetingOwner"},{"pendingBlockLostTarget"},{"pendingBlockCancelledTransport"},
                {"pendingBlockReconfiguredDuringAcceptedAttack"},{"pendingBlockRespectsReachWithAimMargin"}});
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
    public static class SwordPlayer extends Player {
        @Override public boolean isUsingItem() { return getItemInUse() != null; }
        @Override public void setEating(boolean eating) { }
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
        private void acquisition(boolean smart) throws Exception {
            try (Run run = new Run("Einfach")) {
                run.f.run.scene.loadedEntityList.clear();run.f.run.scene.loadedEntityList.add(run.f.first);
                run.target(1.5,4.2);
                assertFalse(run.f.walk().stream().anyMatch(C02PacketUseEntity.class::isInstance));
                assertNull(run.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                run.f.run.fixture.aura.getBooleanSetting("Smart Aim").setValue(smart);
                run.f.run.fixture.aura.getNumberSetting("Aim Range Margin").setValue(2D);
                for (int i=0;i<2;i++) {
                    List<Object> packets=run.f.walk();
                    assertEquals("aura",run.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    assertFalse(packets.stream().anyMatch(C02PacketUseEntity.class::isInstance));
                    assertFalse(packets.stream().anyMatch(C08PacketPlayerBlockPlacement.class::isInstance));
                }
                run.target(.5,2);
                assertTrue(run.f.walk().stream().anyMatch(C02PacketUseEntity.class::isInstance));
                run.f.run.fixture.aura.getBooleanSetting("Rotations").setValue(false);
                run.target(1.5,4.2);
                assertFalse(run.f.walk().stream().anyMatch(C02PacketUseEntity.class::isInstance));
            }
        }
        @Test public void pendingBlockRespectsReachWithAimMargin() throws Exception {
            for (boolean outsideReach : new boolean[]{false, true}) {
                try (Run run = new Run("Einfach", SwordPlayer.class)) {
                    run.remoteTarget();
                    run.input.forward = 0;
                    ItemStack sword = new ItemStack(Items.iron_sword);
                    run.player.inventory.mainInventory[run.player.inventory.currentItem] = sword;
                    me.youngjibbit.module.impl.combat.Aura aura = run.f.run.fixture.aura;
                    aura.getNumberSetting("Reach").setValue(3D);
                    aura.getNumberSetting("Aim Range Margin").setValue(2D);
                    aura.getModeSetting("Autoblock Mode").setValue("Intave");
                    aura.getBooleanSetting("Autoblock").setValue(true);
                    run.f.run.fixture.mc.gameSettings.keyBindUseItem.pressed = false;
                    EntityLivingBase target = (EntityLivingBase) run.f.run.scene.getEntityByID(19);
                    List<Object> attack = run.f.walk();
                    assertEquals(1L, attack.stream().filter(C02PacketUseEntity.class::isInstance).count());
                    assertFalse(attack.stream().anyMatch(C08PacketPlayerBlockPlacement.class::isInstance));
                    assertFalse(run.player.isUsingItem());
                    assertNotNull(ScaffoldOwnerContinuityTest.field(
                            me.youngjibbit.module.impl.combat.Aura.class, "blockAfterHit").get(aura));
                    if (outsideReach) {
                        target.posZ = target.lastTickPosZ = 4.5;
                        target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, 4.2, .8, 65.8, 4.8));
                    }
                    TargetingService targeting = new TargetingService();
                    TargetingService.Options strict = new TargetingService.Options();
                    strict.range = 3;
                    TargetingService.Options acquisition = new TargetingService.Options();
                    acquisition.range = 5;
                    assertEquals(!outsideReach, targeting.isValidTarget(target, strict));
                    assertTrue(targeting.isValidTarget(target, acquisition));
                    run.player.ticksExisted++;
                    List<Object> followup = run.physical(false);
                    assertEquals("aura", run.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                    assertSame(target, ScaffoldOwnerContinuityTest.field(
                            me.youngjibbit.module.impl.combat.Aura.class, "target").get(aura));
                    assertFalse(followup.stream().anyMatch(C02PacketUseEntity.class::isInstance));
                    assertEquals(outsideReach ? 0L : 1L, followup.stream()
                            .filter(C08PacketPlayerBlockPlacement.class::isInstance).count());
                    assertEquals(!outsideReach, run.player.isUsingItem());
                    if (outsideReach) assertNull(run.player.getItemInUse());
                    else assertSame(sword, run.player.getItemInUse());
                }
            }
        }
        @Test public void acquisitionMargin() throws Exception { acquisition(false); }
        @Test public void smartAcquisitionMargin() throws Exception { acquisition(true); }
        @Test public void marginConfigReset() throws Exception {
            try (Run run=new Run("Einfach")) {
                assertEquals(0,run.f.run.fixture.aura.getNumberSetting("Aim Range Margin").getValue(),0);
                run.f.run.fixture.aura.getNumberSetting("Aim Range Margin").setValue(2D);
                run.f.run.fixture.aura.beginConfigUpdate();
                try { assertEquals(0,run.f.run.fixture.aura.getNumberSetting("Aim Range Margin").getValue(),0); }
                finally { run.f.run.fixture.aura.endConfigUpdate(); }
            }
        }
        public static class RotationVeto implements me.zero.alpine.listener.Subscriber {
            RotationManager manager;
            RotationVeto(RotationManager manager) { this.manager=manager; }
            @me.zero.alpine.listener.Subscribe final Listener<me.youngjibbit.event.motion.EventRotation> rotation =
                    new Listener<>(e -> manager.request("scaffold",100,e.motion().getYaw(),e.motion().getPitch()));
        }
        private void pendingBlock(int interruption) throws Exception {
            try (Run run=new Run("Einfach",SwordPlayer.class)) {
                run.remoteTarget();
                run.player.inventory.mainInventory[run.player.inventory.currentItem]=new ItemStack(Items.iron_sword);
                run.f.run.fixture.aura.getModeSetting("Autoblock Mode").setValue("Intave");
                run.f.run.fixture.aura.getBooleanSetting("Autoblock").setValue(true);
                assertTrue(run.f.walk().stream().anyMatch(C02PacketUseEntity.class::isInstance));
                run.player.ticksExisted++;
                RotationVeto veto=new RotationVeto(run.f.run.fixture.runtime.getRotationManager());
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C08PacketPlayerBlockPlacement)e.cancel();};
                if(interruption==0)Pulsar.BUS.subscribe(veto);
                if(interruption==1)run.f.run.scene.loadedEntityList.clear();
                if(interruption==2)Pulsar.BUS.subscribe(hooks);
                try {
                    List<Object> packets=run.physical(false);
                    assertFalse(packets.stream().anyMatch(C08PacketPlayerBlockPlacement.class::isInstance));
                    assertFalse(run.player.isUsingItem());
                } finally { Pulsar.BUS.unsubscribe(veto);Pulsar.BUS.unsubscribe(hooks); }
            }
        }
        @Test public void pendingBlockCompetingOwner() throws Exception { pendingBlock(0); }
        @Test public void pendingBlockLostTarget() throws Exception { pendingBlock(1); }
        @Test public void pendingBlockCancelledTransport() throws Exception { pendingBlock(2); }
        @Test public void pendingBlockReconfiguredDuringAcceptedAttack() throws Exception {
            try (Run run = new Run("Einfach", SwordPlayer.class)) {
                run.remoteTarget();
                run.player.inventory.mainInventory[run.player.inventory.currentItem] = new ItemStack(Items.iron_sword);
                run.f.run.fixture.aura.getModeSetting("Autoblock Mode").setValue("Intave");
                run.f.run.fixture.aura.getBooleanSetting("Autoblock").setValue(true);
                ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet = e -> {
                    if (e.isPost() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity)
                        run.f.run.fixture.aura.getNumberSetting("Yaw Speed").setValue(30D);
                };
                Pulsar.BUS.subscribe(hooks);
                try { assertTrue(run.f.walk().stream().anyMatch(C02PacketUseEntity.class::isInstance)); }
                finally { Pulsar.BUS.unsubscribe(hooks); }
                run.player.ticksExisted++;
                assertFalse(run.physical(false).stream().anyMatch(C08PacketPlayerBlockPlacement.class::isInstance));
                assertFalse(run.player.isUsingItem());
            }
        }
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
                run.player.inventory.mainInventory[3]=new ItemStack(net.minecraft.init.Blocks.stone,64);
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
                run.player.inventory.mainInventory[3]=new ItemStack(net.minecraft.init.Blocks.stone,64);
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
        private void impulse(Run run) {
            run.player.setVelocity(.4, .2, .2);
            KnockbackEvent applied =
                    new KnockbackEvent(.4, .2, .2);
            applied.setEventPhase(me.zero.alpine.event.EventPhase.POST);
            Pulsar.BUS.post(applied);
        }

        @Test public void intaveReduceMotion() throws Exception {
            try (Run run = new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);
                run.remoteTarget();
                Velocity velocity = new Velocity();
                velocity.mode.setValue("Intave Reduce");
                velocity.setToggled(true);
                try {
                    for (boolean sprint : new boolean[]{false, true}) {
                        run.player.sprint = sprint;
                        impulse(run);
                        List<Object> packets = run.f.walk();
                        assertEquals(sprint ? 1 : 0, packets.stream().filter(C02PacketUseEntity.class::isInstance).count());
                        assertEquals(sprint ? .24 : .4, run.player.motionX, 1e-9);
                        assertEquals(sprint ? .12 : .2, run.player.motionZ, 1e-9);
                        assertEquals(.2, run.player.motionY, 1e-9);
                    }
                } finally { velocity.setToggled(false); }
            }
        }

        @Test public void legitJumpresetInput() throws Exception {
            try (Run run = new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);
                run.remoteTarget();
                // This positive jump case needs real support, not just a synthetic onGround flag.
                AxisAlignedBB grounded = run.player.getEntityBoundingBox();
                run.f.run.scene.blocks.put(new BlockPos(run.player.posX, grounded.minY - 1, run.player.posZ),
                        Blocks.stone.getDefaultState());
                assertFalse(run.f.run.scene.getCollidingBoundingBoxes(run.player, grounded.offset(0, -.01, 0)).isEmpty());
                Velocity velocity = new Velocity();
                velocity.mode.setValue("Legit Jumpreset");
                velocity.setToggled(true);
                try {
                    run.player.sprint = run.player.onGround = true;
                    impulse(run);
                    for (boolean expected : new boolean[]{true, false}) {
                        MovementInputEvent sample =
                                new MovementInputEvent(0, 1, false, false);
                        Pulsar.BUS.post(sample);
                        assertEquals(expected, sample.isJump());
                    }
                    run.player.ticksExisted++;
                    run.player.onGround = false;
                    run.player.setEntityBoundingBox(grounded.offset(0, .42, 0));
                    impulse(run);
                    MovementInputEvent airborne = new MovementInputEvent(0, 1, false, false);
                    Pulsar.BUS.post(airborne);
                    assertFalse("airborne input never creates an automatic jump", airborne.isJump());
                    run.player.ticksExisted++;
                    run.player.onGround = true;
                    impulse(run);
                    MovementInputEvent staleGround = new MovementInputEvent(0, 1, false, false);
                    Pulsar.BUS.post(staleGround);
                    assertFalse("onGround without a support surface still cannot jump", staleGround.isJump());
                    run.player.setEntityBoundingBox(grounded);
                    assertFalse(run.f.walk().stream().anyMatch(C02PacketUseEntity.class::isInstance));
                } finally { velocity.setToggled(false); }
            }
        }

        @Test public void intaveBlockAndSwordPulse() throws Exception {
            try (Run run = new Run("Einfach", SwordPlayer.class)) {
                run.remoteTarget();
                ItemStack sword = new ItemStack(Items.iron_sword);
                run.player.inventory.mainInventory[run.player.inventory.currentItem] = sword;
                run.f.run.fixture.aura.getModeSetting("Autoblock Mode").setValue("Intave");
                run.f.run.fixture.aura.getBooleanSetting("Autoblock").setValue(true);
                assertTrue(run.f.walk().stream().anyMatch(C02PacketUseEntity.class::isInstance));
                run.player.ticksExisted++;
                List<Object> block = run.physical(false);
                assertTrue(block.stream().anyMatch(C08PacketPlayerBlockPlacement.class::isInstance));
                assertSame(sword, run.player.getItemInUse());
                assertFalse(block.stream().anyMatch(C02PacketUseEntity.class::isInstance));
                NoSlow noSlow = new NoSlow();
                noSlow.getModeSetting("Mode").setValue("Intave Sword Pulse");
                noSlow.setToggled(true);
                try {
                    run.f.run.fixture.mc.gameSettings.keyBindUseItem.pressed = true;
                    assertFalse(run.physical(false).stream().anyMatch(C07PacketPlayerDigging.class::isInstance));
                    run.player.clearItemInUse();
                    run.player.setItemInUse(sword, sword.getMaxItemUseDuration() - 2);
                    ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                    hooks.packet = e -> {
                        if (e.isPre() && e.isOutbound() && e.getPacket() instanceof C07PacketPlayerDigging) e.cancel();
                    };
                    Pulsar.BUS.subscribe(hooks);
                    try {
                        assertFalse(run.physical(false).stream().anyMatch(C07PacketPlayerDigging.class::isInstance));
                        assertTrue(run.player.isBlocking());
                    } finally { Pulsar.BUS.unsubscribe(hooks); }
                    assertTrue(run.physical(false).stream().anyMatch(C07PacketPlayerDigging.class::isInstance));
                    assertFalse(run.player.isUsingItem());
                } finally { noSlow.setToggled(false); }
            }
        }

        @Test public void intaveWallAcceleration() throws Exception {
            try (Run run = new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);
                Speed speed = new Speed();
                speed.getModeSetting("Mode").setValue("Intave Momentum");
                speed.setToggled(true);
                try {
                    run.player.onGround = true;
                    for (boolean wall : new boolean[]{false, true}) {
                        run.player.isCollidedHorizontally = wall;
                        run.player.motionX = run.player.motionZ = 0;
                        run.player.moveFlying(0, 1, .1F);
                        assertEquals(wall ? .112 : .1, Math.hypot(run.player.motionX, run.player.motionZ), 1e-5);
                    }
                } finally { speed.setToggled(false); }
            }
        }

        @Test public void velocityMovement() throws Exception {
            try(Run run=new Run("Einfach")) {
                run.f.run.fixture.aura.setToggled(false);run.target(-.5,2);
                Velocity velocity=new Velocity();
                Map<Class<? extends me.youngjibbit.module.Module>,me.youngjibbit.module.Module> modules=
                        (Map<Class<? extends me.youngjibbit.module.Module>,me.youngjibbit.module.Module>)ScaffoldOwnerContinuityTest
                                .field(me.youngjibbit.module.ModuleManager.class,"modulesByClass").get(Pulsar.INSTANCE.getMm());
                modules.put(Velocity.class,velocity);velocity.setToggled(true);
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
                run.player.inventory.mainInventory[3] = new ItemStack(net.minecraft.init.Blocks.stone,64);
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
