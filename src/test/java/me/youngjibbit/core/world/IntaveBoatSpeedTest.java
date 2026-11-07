package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.movement.IntaveBoatSpeed;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import me.youngjibbit.module.impl.movement.Speed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Real moveFlying/event consumers. World storage/collision enumeration are synthetic; no server. */
@RunWith(Parameterized.class)
public class IntaveBoatSpeedTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"realHookAndExit"},{"spatialAndSurfaceGates"},
                {"ordinaryStepAndWarmup"},{"impulseAndContextReset"},{"rotationAndMotionPreservation"},{"reachableSprintPass"},{"postBusPrediction"},
                {"reentrantLifecycle"},{"appliedKnockbackPauses"}});
    }
    private final String method;
    public IntaveBoatSpeedTest(String method) { this.method=method; }
    @Test public void checkInFreshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class,method); }

    public static class Scene extends WorldClient {
        List<Entity> entities = new ArrayList<>();
        boolean floor=true, obstacle, slab;
        Runnable onCollision;
        public Scene() { super(null,null,0,null,null); }
        @Override public boolean isBlockLoaded(BlockPos p) { return true; }
        @Override public IBlockState getBlockState(BlockPos p) {
            return floor && p.getY()==63 ? (slab ? Blocks.stone_slab : Blocks.stone).getDefaultState() : Blocks.air.getDefaultState();
        }
        @Override public boolean isMaterialInBB(AxisAlignedBB box,Material material) { return false; }
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity,AxisAlignedBB box) {
            Runnable callback=onCollision;onCollision=null;if(callback!=null)callback.run();
            List<AxisAlignedBB> found=new ArrayList<>();
            if(obstacle) found.add(box);
            for(Entity e:entities) if(e!=entity && !e.isDead && e.getEntityBoundingBox().intersectsWith(box))
                found.add(e.getEntityBoundingBox());
            return found;
        }
        @Override public Entity getEntityByID(int id) {
            for(Entity e:entities)if(e.getEntityId()==id)return e;return null;
        }
        @Override public List<Entity> getEntitiesInAABBexcluding(Entity except,AxisAlignedBB box,
                com.google.common.base.Predicate<? super Entity> predicate) {
            List<Entity> found=new ArrayList<>();
            for(Entity e:entities)if(e!=except && e.getEntityBoundingBox().intersectsWith(box)
                    && (predicate==null || predicate.apply(e)))found.add(e);
            return found;
        }
    }
    static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
        final Scene world=RotationBehaviorHookTest.bare(Scene.class);
        final AtomicLong time=new AtomicLong();
        final IntaveBoatSpeed helper=new IntaveBoatSpeed(time::get);
        final EntityBoat boat=RotationBehaviorHookTest.bare(EntityBoat.class);
        Run() throws Exception {
            Bootstrap.register();world.entities=new ArrayList<>();world.floor=true;
            f.mc.theWorld=world;f.player.worldObj=world;
            f.player.capabilities=new PlayerCapabilities();f.player.movementInput=new MovementInput();
            f.player.width=.6f;f.player.height=1.8f;
            f.player.sprint=true;f.player.onGround=true;f.player.rotationYaw=0;
            f.player.setPosition(1.15,64,0);previous();
            f.mc.gameSettings=RotationBehaviorHookTest.bare(GameSettings.class);
            f.mc.gameSettings.keyBindJump=new KeyBinding("boat.jump",0,"test");
            f.mc.timer=new net.minecraft.util.Timer(20,()->0);
            boat.worldObj=world;boat.setEntityId(42);boat.setPosition(0,64,0);
            boat.prevPosX=boat.posX;boat.prevPosY=boat.posY;boat.prevPosZ=boat.posZ;
            boat.setEntityBoundingBox(new AxisAlignedBB(-.75,64,-.75,.75,64.6,.75));
            world.entities.add(boat);
        }
        void previous() { f.player.prevPosX=f.player.posX;f.player.prevPosY=f.player.posY;f.player.prevPosZ=f.player.posZ; }
        void tick(Runnable action) {
            f.player.ticksExisted++;time.addAndGet(50_000_000L);
            RuntimeObservation o=f.runtime.getObservation();o.beginOuterTick();
            try { o.observePlayerStep(f.player,()->{action.run();return true;},step->step.getAsBoolean()); }
            finally { o.endOuterTick(); }
        }
        PlayerMoveUpdateEvent event() { return new PlayerMoveUpdateEvent(0,.98f,.13f,0,0); }
        boolean apply() { return helper.apply(f.mc,event()); }
        void warm() { for(int i=0;i<6;i++)tick(()->apply()); }
        @Override public void close() throws Exception { f.close(); }
    }
    public static class Checks {
        @Test public void realHookAndExit() throws Exception {
            try(Run r=new Run()) {
                Speed speed=new Speed();speed.getModeSetting("Mode").setValue("Intave Boat");speed.onEnable();
                try {
                    for(int i=0;i<5;i++)r.tick(()->{
                        r.f.player.motionZ=0;r.f.player.moveFlying(0,.98f,.13f);
                        assertEquals(.98f*.13f,r.f.player.motionZ,1e-7);
                    });
                    r.f.player.motionX=r.f.player.motionZ=0;
                    r.tick(()->r.f.player.moveFlying(0,.98f,.13f));
                    double candidate=r.f.player.motionZ,baseline=.98f*.13f;
                    assertEquals(.98f*(.13f+.035f),candidate,1e-7);
                    assertTrue(candidate>baseline);
                    assertEquals(0,r.f.player.motionY,0);assertEquals(1,r.f.mc.timer.timerSpeed,0);
                    System.out.printf(Locale.ROOT,"Actual moveFlying: baseline=%.9f candidate=%.9f delta=%.9f blocks/step%n",baseline,candidate,candidate-baseline);
                    r.world.entities.clear();
                    r.tick(()->r.f.player.moveFlying(0,.98f,.13f));
                    assertEquals("exit retains incoming motion and adds only vanilla acceleration",candidate+baseline,r.f.player.motionZ,1e-7);
                    assertEquals(0,r.f.player.motionX,0);
                    speed.onDisable();double before=r.f.player.motionZ;
                    r.tick(()->r.f.player.moveFlying(0,.98f,.13f));
                    assertEquals(before+baseline,r.f.player.motionZ,1e-7);
                } finally { speed.onDisable(); }
            }
        }
        @Test public void spatialAndSurfaceGates() throws Exception {
            try(Run r=new Run()) {
                r.warm();r.tick(()->assertTrue(r.apply()));
                r.f.player.motionZ=.4;r.tick(()->assertFalse(r.apply()));
                assertEquals(.4,r.f.player.motionZ,0);r.f.player.motionZ=0;r.warm();
                r.f.player.prevPosX=2;r.tick(()->assertFalse(r.apply()));r.previous();r.warm();
                r.f.player.setPosition(1.15,64,.94);r.previous();
                r.tick(()->assertFalse("projected position leaves radius",r.apply()));
                r.f.player.setPosition(1.15,64,0);r.previous();r.warm();
                r.world.obstacle=true;r.tick(()->assertFalse(r.apply()));r.world.obstacle=false;r.warm();
                r.world.floor=false;r.tick(()->assertFalse(r.apply()));r.world.floor=true;r.warm();
                r.world.slab=true;r.tick(()->assertFalse(r.apply()));r.world.slab=false;r.warm();
                r.boat.prevPosX=-.1;r.tick(()->assertFalse(r.apply()));r.boat.prevPosX=0;r.warm();
                r.boat.isDead=true;r.tick(()->assertFalse(r.apply()));
                r.boat.isDead=false;r.warm();
                EntityBoat replacement=RotationBehaviorHookTest.bare(EntityBoat.class);
                replacement.worldObj=r.world;replacement.setEntityId(42);replacement.setPosition(0,64,0);
                replacement.prevPosY=64;replacement.setEntityBoundingBox(r.boat.getEntityBoundingBox());
                r.world.entities.clear();r.world.entities.add(replacement);
                r.tick(()->assertFalse("same network id must not inherit another boat's warmup",r.apply()));
            }
        }
        @Test public void ordinaryStepAndWarmup() throws Exception {
            try(Run r=new Run()) {
                assertFalse(r.apply());
                for(int i=0;i<5;i++)r.tick(()->assertFalse(r.apply()));
                r.tick(()->{
                    PlayerMoveUpdateEvent event=r.event();assertTrue(r.helper.apply(r.f.mc,event));
                    assertFalse(r.helper.apply(r.f.mc,r.event()));
                    assertEquals(.165f,event.getFriction(),1e-7);
                });
                r.warm();r.f.player.ticksExisted++;
                java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
                RuntimeObservation o=r.f.runtime.getObservation();o.beginOuterTick();
                try {
                    o.observePlayerStep(r.f.player,()->{
                        if(calls.incrementAndGet()==2)assertFalse("extra body cannot claim an unused outer-tick boost",r.apply());return true;
                    },step->{step.getAsBoolean();step.getAsBoolean();});
                } finally { o.endOuterTick(); }
                r.warm();
                r.f.player.ticksExisted+=5;r.tick(()->assertFalse(r.apply()));
            }
        }
        @Test public void impulseAndContextReset() throws Exception {
            try(Run r=new Run()) {
                r.warm();r.helper.pauseForImpulse();
                r.f.player.motionX=.21;r.f.player.motionY=.3;r.f.player.motionZ=-.17;
                for(int i=0;i<9;i++)r.tick(()->assertFalse(r.apply()));
                assertEquals(.21,r.f.player.motionX,0);assertEquals(.3,r.f.player.motionY,0);assertEquals(-.17,r.f.player.motionZ,0);
                r.f.player.motionX=r.f.player.motionY=r.f.player.motionZ=0;
                r.warm();r.f.network.getClientPackets().worldChanged();r.tick(()->assertFalse(r.apply()));
                r.warm();r.f.mc.inGameHasFocus=false;r.tick(()->assertFalse(r.apply()));r.f.mc.inGameHasFocus=true;r.warm();
                r.f.mc.currentScreen=new GuiScreen(){};r.tick(()->assertFalse(r.apply()));r.f.mc.currentScreen=null;r.warm();
                r.f.player.movementInput.jump=true;r.tick(()->assertFalse(r.apply()));r.f.player.movementInput.jump=false;r.warm();
                r.f.player.usingItem=true;r.tick(()->assertFalse(r.apply()));r.f.player.usingItem=false;r.warm();
                r.f.player.hurtTime=1;r.tick(()->assertFalse(r.apply()));
            }
        }
        @Test public void rotationAndMotionPreservation() throws Exception {
            try(Run r=new Run()) {
                r.f.player.setPosition(0,64,1.15);r.previous();
                r.f.player.rotationYaw=180;
                for(String owner:new String[]{"aura","scaffold"}) {
                    r.helper.reset();r.f.aura.getModeSetting("movefix").setValue("Rotationsgebunden");
                    r.f.scaffold.getModeSetting("movefix").setValue("Rotationsgebunden");
                    r.f.accept(owner,90);r.warm();
                    r.tick(()->{
                        PlayerMoveUpdateEvent event=r.event();event.setYaw(180);event.setStrafe(.98f);
                        me.youngjibbit.core.combat.RotationCorrections.movement(event);
                        assertTrue(r.helper.apply(r.f.mc,event));
                        float yaw=event.getYaw(),forward=event.getForward(),strafe=event.getStrafe();
                        me.youngjibbit.core.combat.RotationCorrections.movement(event);
                        assertEquals(yaw,event.getYaw(),0);assertEquals(forward,event.getForward(),0);assertEquals(strafe,event.getStrafe(),0);
                    });
                    assertEquals(180,r.f.player.rotationYaw,0);
                }
                r.helper.reset();r.f.player.motionX=Double.NaN;
                r.tick(()->assertFalse(r.apply()));assertTrue(Double.isNaN(r.f.player.motionX));
            }
        }
        @Test public void reachableSprintPass() throws Exception {
            double[] distances=new double[2];
            for(int variant=0;variant<2;variant++)try(Run r=new Run()) {
                Speed speed=new Speed();speed.getModeSetting("Mode").setValue("Intave Boat");
                if(variant==1)speed.onEnable();
                try {
                    r.f.player.setPosition(1.1,64,-.85);r.previous();r.f.player.motionZ=.153;
                    for(int i=0;i<6;i++)r.tick(()->{
                        r.previous();r.f.player.moveFlying(0,.98f,.13f);
                        assertTrue("real boat box must not intersect the swept player path",r.world.getCollidingBoundingBoxes(r.f.player,
                                r.f.player.getEntityBoundingBox().addCoord(r.f.player.motionX,0,r.f.player.motionZ)).isEmpty());
                        r.f.player.setPosition(r.f.player.posX+r.f.player.motionX,64,r.f.player.posZ+r.f.player.motionZ);
                        r.f.player.motionX*=.54600006;r.f.player.motionZ*=.54600006;
                    });
                    distances[variant]=r.f.player.posZ+.85;
                } finally { if(variant==1)speed.onDisable(); }
            }
            assertTrue("six consecutive moving sprint steps can activate the experiment",distances[1]>distances[0]+.03);
            System.out.printf(Locale.ROOT,"Six-step pass, real moveFlying + explicit free-space integration: baseline=%.9f candidate=%.9f delta=%.9f blocks%n",distances[0],distances[1],distances[1]-distances[0]);
        }
        @Test public void postBusPrediction() throws Exception {
            try(Run r=new Run()) {
                Speed speed=new Speed();speed.getModeSetting("Mode").setValue("Intave Boat");speed.onEnable();
                me.zero.alpine.listener.Listener<PlayerMoveUpdateEvent> last=new me.zero.alpine.listener.Listener<>(
                        PlayerMoveUpdateEvent.class,e->{e.setYaw(90);e.setFriction(.19f);},me.zero.alpine.event.EventPriority.LOWEST);
                try {
                    for(int i=0;i<6;i++)r.tick(()->{r.f.player.motionX=r.f.player.motionZ=0;r.f.player.moveFlying(0,.98f,.13f);});
                    Pulsar.BUS.subscribe(last);
                    r.tick(()->{r.f.player.motionX=r.f.player.motionZ=0;r.f.player.moveFlying(0,.98f,.13f);});
                    assertEquals("final listener turns the swept path into the boat: extra acceleration rejected",-.98f*.19f,r.f.player.motionX,1e-7);
                    assertEquals(0,r.f.player.motionZ,1e-7);
                } finally { Pulsar.BUS.unsubscribe(last);speed.onDisable(); }
            }
        }
        @Test public void reentrantLifecycle() throws Exception {
            try(Run r=new Run()) {
                Speed speed=new Speed();speed.getModeSetting("Mode").setValue("Intave Boat");speed.onEnable();
                try {
                    for(int i=0;i<6;i++)r.tick(()->{r.f.player.motionZ=0;r.f.player.moveFlying(0,.98f,.13f);});
                    r.world.onCollision=speed::onDisable;
                    r.tick(()->{r.f.player.motionZ=0;r.f.player.moveFlying(0,.98f,.13f);});
                    assertEquals("disable during world query cannot revive the old experiment",.98f*.13f,r.f.player.motionZ,1e-7);
                    assertEquals("",speed.info);
                } finally { speed.onDisable(); }
                r.f.player.motionZ=0;r.warm();r.world.onCollision=r.helper::pauseForImpulse;
                r.tick(()->assertFalse("an impulse during the support query wins over the pending boost",r.apply()));
                r.tick(()->assertFalse(r.apply()));
                assertEquals(0,r.f.player.motionZ,0);
            }
        }
        @Test public void appliedKnockbackPauses() throws Exception {
            try(Run r=new Run()) {
                Speed speed=new Speed();speed.getModeSetting("Mode").setValue("Intave Boat");speed.onEnable();
                me.zero.alpine.listener.Listener<me.youngjibbit.event.motion.KnockbackEvent> cancel=
                        new me.zero.alpine.listener.Listener<>(me.youngjibbit.event.motion.KnockbackEvent.class,e->{if(e.isPre())e.cancel();});
                try {
                    Field thread=net.minecraft.client.Minecraft.class.getDeclaredField("mcThread");thread.setAccessible(true);thread.set(r.f.mc,Thread.currentThread());
                    Field controller=net.minecraft.client.network.NetHandlerPlayClient.class.getDeclaredField("gameController");controller.setAccessible(true);controller.set(r.f.player.sendQueue,r.f.mc);
                    r.f.player.sendQueue.clientWorldController=r.world;r.f.player.setEntityId(7);r.world.entities.add(r.f.player);
                    for(int i=0;i<6;i++)r.tick(()->{r.f.player.motionZ=0;r.f.player.moveFlying(0,.98f,.13f);});
                    r.f.player.motionZ=0;Pulsar.BUS.subscribe(cancel);
                    r.f.player.sendQueue.handleEntityVelocity(new net.minecraft.network.play.server.S12PacketEntityVelocity(7,.2,.2,0));
                    Pulsar.BUS.unsubscribe(cancel);
                    r.tick(()->r.f.player.moveFlying(0,.98f,.13f));
                    assertEquals("cancelled S12 must not manufacture an applied impulse pause",.98f*(.13f+.035f),r.f.player.motionZ,1e-7);
                    r.f.player.sendQueue.handleEntityVelocity(new net.minecraft.network.play.server.S12PacketEntityVelocity(7,.2,.2,0));
                    assertEquals(.2,r.f.player.motionX,1e-4);assertEquals(.2,r.f.player.motionY,1e-4);
                    r.f.player.motionX=r.f.player.motionY=r.f.player.motionZ=0;
                    r.tick(()->r.f.player.moveFlying(0,.98f,.13f));
                    assertEquals("actual S12 handler POST pauses the production Speed hook",.98f*.13f,r.f.player.motionZ,1e-7);
                } finally { Pulsar.BUS.unsubscribe(cancel);speed.onDisable(); }
            }
        }
    }
}
