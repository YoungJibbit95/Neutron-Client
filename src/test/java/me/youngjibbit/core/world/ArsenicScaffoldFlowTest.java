package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ArsenicScaffoldFlowTest {
    public static class Scene extends ScaffoldClientFlowTest.Scene {
        Runnable beforeCollision;
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(Entity entity,AxisAlignedBB box) {
            if(beforeCollision!=null){Runnable callback=beforeCollision;beforeCollision=null;callback.run();}
            List<AxisAlignedBB> result=new ArrayList<>();
            for(Map.Entry<BlockPos,net.minecraft.block.state.IBlockState> cell:blocks.entrySet()) {
                AxisAlignedBB b=cell.getValue().getBlock().getCollisionBoundingBox(this,cell.getKey(),cell.getValue());
                if(b!=null && b.intersectsWith(box))result.add(b);
            }
            return result;
        }
    }
    static Scene prepare(RotationBehaviorHookTest.Fixture f) throws Exception {
        ScaffoldClientFlowTest.Scene old=ScaffoldClientFlowTest.prepare(f,.5,.48);
        Scene scene=RotationBehaviorHookTest.bare(Scene.class);scene.blocks=old.blocks;scene.border=old.border;scene.motionTime=old.motionTime;
        ScaffoldClientFlowTest.set(net.minecraft.world.World.class,scene,"loadedEntityList",new ArrayList<>());
        f.mc.theWorld=scene;f.player.worldObj=scene;
        f.player.movementInput=new MovementInput();f.player.movementInput.moveForward=0;f.player.movementInput.moveStrafe=0;
        f.player.getAttributeMap().registerAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
        f.scaffold.getModeSetting("Scaffold Approach").setValue("Arsenic");return scene;
    }
    static int step(RotationBehaviorHookTest.Fixture f,Scene scene) {
        scene.motionTime.addAndGet(50_000_000L);f.runtime.getScaffoldPlacementService().beginTick();ScaffoldClientFlowTest.ordinaryWalking(f);
        int count=0;Object sent;while((sent=f.channel.readOutbound())!=null)if(sent instanceof C08PacketPlayerBlockPlacement)count++;return count;
    }
    @Test public void optionalSoftStartDelaysPlacementUntilItsAcceptedRayReachesTheFace() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Scene scene = prepare(f);
            assertNotNull("Scaffold needs an opt-in soft start", f.scaffold.getBooleanSetting("Arsenic Soft Motion"));
            f.scaffold.getBooleanSetting("Arsenic Soft Motion").setValue(true);
            f.player.rotationYaw = 90;
            f.scaffold.onEnable();
            try {
                assertEquals(0, step(f, scene));
                float yaw = f.runtime.getRotationManager().getEffectiveYaw();
                float pitch = f.runtime.getRotationManager().getEffectivePitch();
                assertTrue(Math.abs(MathHelper.wrapAngleTo180_float(yaw - 90)) <= 2.5f);
                assertTrue(Math.abs(pitch - f.player.rotationPitch) <= 1.5f);
                int placed = 0;
                for (int i = 0; i < 70 && placed == 0; i++) placed += step(f, scene);
                assertEquals(1, placed);
                assertEquals(Blocks.stone, scene.getBlockState(new BlockPos(0,63,0)).getBlock());
            } finally { f.scaffold.onDisable(); }
        }
    }

    @Test public void predictiveCorePlacesThroughAcceptedWalkingAndNativeController() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Scene scene=prepare(f);f.scaffold.onEnable();
            try {
                int placed=0;for(int i=0;i<25 && placed==0;i++)placed+=step(f,scene);
                assertEquals(1,placed);assertEquals(Blocks.stone,scene.getBlockState(new BlockPos(0,63,0)).getBlock());
                assertEquals(63,f.player.inventory.mainInventory[3].stackSize);assertEquals(0,f.player.inventory.currentItem);
                assertEquals(0,f.player.rotationYaw,0);assertFalse(f.mc.gameSettings.keyBindSneak.isKeyDown());
            } finally { f.scaffold.onDisable(); }
            assertTrue(me.youngjibbit.module.impl.world.Scaffold.allowsSprint());
        }
    }
    @Test public void canceledReplacedAndChangedStackMovementCannotGrantPlacement() throws Exception {
        for(String mode:new String[]{"cancel","replace","stack","focus"}) {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Scene scene=prepare(f);
            Listener<EventPacket> cancel=new Listener<>(e->{if(e.getPacket() instanceof C03PacketPlayer && e.isPre()) {
                if("cancel".equals(mode))e.cancel();
                else if("replace".equals(mode))e.setPacket(new C03PacketPlayer.C05PacketPlayerLook(0,0,true));
                else if("stack".equals(mode))f.player.inventory.mainInventory[3]=f.player.inventory.mainInventory[3].copy();
                else f.mc.inGameHasFocus=false;
            }});
            Pulsar.BUS.subscribe(cancel);f.scaffold.onEnable();
            try { for(int i=0;i<12;i++)assertEquals(0,step(f,scene));assertEquals(64,f.player.inventory.mainInventory[3].stackSize); }
            finally { f.scaffold.onDisable();Pulsar.BUS.unsubscribe(cancel); }
        }
        }
    }
    @Test public void contextLossDuringPredictionCannotWriteInputIntoAnotherScene() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Scene scene=prepare(f);f.scaffold.onEnable();
            try {
                scene.beforeCollision=()->f.mc.inGameHasFocus=false;
                MovementInputEvent input=new MovementInputEvent(0,1,false,false);Pulsar.BUS.post(input);
                assertFalse(input.isSneak());assertEquals(1,input.getForward(),0);
            } finally { f.scaffold.onDisable(); }
        }
    }
    @Test public void disablingInsidePrePredictionCannotPublishANewScaffoldRotation() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Scene scene=prepare(f);f.scaffold.onEnable();
            try {
                scene.beforeCollision=()->f.scaffold.setToggled(false);
                assertEquals(0,step(f,scene));
                assertFalse(f.scaffold.isToggled());
                assertFalse("disabled module must not acquire rotation ownership after its callback returns",
                        f.runtime.getRotationManager().canUseRotation("scaffold",false));
            } finally { f.scaffold.onDisable(); }
        }
    }
    @SuppressWarnings("unchecked")
    @Test public void sceneSupplierReenableCannotPublishThePreviousGenerationPlan() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Scene scene=prepare(f);f.scaffold.onEnable();
            me.youngjibbit.core.observation.RuntimeObservation observation=f.runtime.getObservation();
            java.lang.reflect.Field source=ScaffoldOwnerContinuityTest.field(observation.getClass(),"source");
            java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample> original=
                    (java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)source.get(observation);
            java.util.concurrent.atomic.AtomicBoolean armed=new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean(true);
            scene.beforeCollision=()->armed.set(true);
            source.set(observation,(java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)()->{
                if(armed.get() && insideGuard() && once.compareAndSet(true,false)) {
                    f.scaffold.setToggled(false);f.scaffold.setToggled(true);
                }
                return original.get();
            });
            try {
                assertEquals(0,step(f,scene));assertFalse(once.get());assertTrue(f.scaffold.isToggled());
                assertFalse("previous generation must not publish into the reenabled module",f.runtime.getRotationManager().canUseRotation("scaffold",false));
                assertNull(ScaffoldOwnerContinuityTest.field(f.scaffold.getClass(),"frameTarget").get(f.scaffold));
            } finally { source.set(observation,original);f.scaffold.onDisable(); }
        }
    }
    @SuppressWarnings("unchecked")
    @Test public void staleSceneResultCannotCleanUpAReenabledGeneration() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            prepare(f);ScaffoldClientFlowTest.ordinaryWalking(f);f.scaffold.onEnable();
            me.youngjibbit.core.observation.RuntimeObservation observation=f.runtime.getObservation();
            java.lang.reflect.Field source=ScaffoldOwnerContinuityTest.field(observation.getClass(),"source");
            java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample> original=
                    (java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)source.get(observation);
            java.util.concurrent.atomic.AtomicBoolean once=new java.util.concurrent.atomic.AtomicBoolean(true);
            PlacementTarget replacement=f.runtime.getScaffoldPlacementService().planFace(new BlockPos(-1,63,0),EnumFacing.EAST,null);
            assertNotNull(replacement);
            java.lang.reflect.Field frameTarget=ScaffoldOwnerContinuityTest.field(f.scaffold.getClass(),"frameTarget");
            long generation=ScaffoldOwnerContinuityTest.field(f.scaffold.getClass(),"lifecycleGeneration").getLong(f.scaffold);
            source.set(observation,(java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)()->{
                if(insideGuard() && once.compareAndSet(true,false)) {
                    f.scaffold.setToggled(false);f.scaffold.setToggled(true);
                    try { frameTarget.set(f.scaffold,replacement); }
                    catch(IllegalAccessException error) { throw new AssertionError(error); }
                    f.mc.inGameHasFocus=false;
                }
                return original.get();
            });
            try {
                java.lang.reflect.Method guard=f.scaffold.getClass().getDeclaredMethod("continueMotion",long.class,long.class);guard.setAccessible(true);
                Object valid=guard.invoke(f.scaffold,f.runtime.getRotationManager().getMotionSequence(),generation);
                assertFalse("fixture must reach the scene-supplier boundary",once.get());assertEquals(false,valid);assertTrue(f.scaffold.isToggled());
                assertSame("old guard must not clear the replacement context",f.player,
                        ScaffoldOwnerContinuityTest.field(f.scaffold.getClass(),"contextPlayer").get(f.scaffold));
                assertSame("old guard must not release the replacement plan",replacement,frameTarget.get(f.scaffold));
            } finally { source.set(observation,original);f.mc.inGameHasFocus=true;f.scaffold.onDisable(); }
        }
    }
    private static boolean insideGuard() {
        for(StackTraceElement frame:Thread.currentThread().getStackTrace())
            if(me.youngjibbit.module.impl.world.Scaffold.class.getName().equals(frame.getClassName()) && "continueMotion".equals(frame.getMethodName()))return true;
        return false;
    }
    @Test public void eagleIsInputLocalPreservesPhysicalSneakAndClearsWhenDisabled() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            Scene scene=prepare(f);f.scaffold.onEnable();
            try {
                assertFalse(me.youngjibbit.module.impl.world.Scaffold.allowsSprint());
                MovementInputEvent input=new MovementInputEvent(0,1,false,false);Pulsar.BUS.post(input);
                assertTrue(input.isSneak());assertEquals(.3f,input.getForward(),1e-6);assertFalse(f.mc.gameSettings.keyBindSneak.isKeyDown());
                MovementInputEvent physical=new MovementInputEvent(0,.3f,false,true);Pulsar.BUS.post(physical);
                assertTrue(physical.isSneak());assertEquals(.3f,physical.getForward(),1e-6);
                f.player.onGround=false;
                MovementInputEvent airborne=new MovementInputEvent(0,1,false,false);Pulsar.BUS.post(airborne);assertFalse(airborne.isSneak());
            } finally { f.scaffold.onDisable(); }
            MovementInputEvent after=new MovementInputEvent(0,1,false,false);Pulsar.BUS.post(after);assertFalse(after.isSneak());assertEquals(1,after.getForward(),0);
        }
    }
    @Test public void acceptedPointRefinementCannotRetargetOrReplaceThePreparedStack() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            prepare(f);ScaffoldPlacementService service=f.runtime.getScaffoldPlacementService();
            PlacementTarget plan=service.planFace(new BlockPos(-1,63,0),EnumFacing.EAST,null);assertNotNull(plan);
            f.accept("scaffold",0);assertNull(service.alignAcceptedHit(plan,f.runtime.getRotationManager()));
            f.player.inventory.mainInventory[3]=f.player.inventory.mainInventory[3].copy();
            assertFalse(service.isValid(plan));assertNull(service.alignAcceptedHit(plan,f.runtime.getRotationManager()));
        }
    }
}
