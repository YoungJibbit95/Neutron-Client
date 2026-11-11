package me.youngjibbit.core.world;

import com.google.common.base.Predicate;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.render.TargetESP;
import me.youngjibbit.module.impl.world.NScaffold;
import me.youngjibbit.utils.render.TargetEspRenderer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Exercises the real module's observation path before GL; native geometry has its own render check. */
public class TargetEspLifecycleTest {
    @ModuleInfo(name="Target visual Aura fixture", category=Category.Combat)
    public static final class CallbackAura extends Aura {
        int reads;
        Runnable callback;
        EntityLivingBase observed;
        @Override public EntityLivingBase getActiveTarget() {
            reads++;
            if(callback!=null)callback.run();
            return observed;
        }
    }
    public static class Scene extends WorldClient {
        int blockReads, entityQueries;
        Runnable rayCallback, queryCallback;
        Entity target;
        Scene(){super(null,null,0,null,null);}
        @Override public Entity getEntityByID(int id){return target!=null&&target.getEntityId()==id?target:null;}
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 eyes,Vec3 end,boolean liquid,boolean empty,boolean last){
            blockReads++;
            if(rayCallback!=null)rayCallback.run();
            return new MovingObjectPosition(end,EnumFacing.NORTH,new BlockPos(end));
        }
        @Override public List<Entity> getEntitiesInAABBexcluding(Entity excluded,AxisAlignedBB area,Predicate<? super Entity> predicate){
            entityQueries++;
            if(queryCallback!=null)queryCallback.run();
            return target==null?Collections.emptyList():Collections.singletonList(target);
        }
    }
    public static class Candidate extends EntityOtherPlayerMP {
        Runnable collisionCallback;
        Candidate(){super(null,null);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean canBeCollidedWith(){if(collisionCallback!=null)collisionCallback.run();return true;}
    }
    private static Object field(Class<?> type,Object owner,String name)throws Exception {
        Field value=type.getDeclaredField(name);value.setAccessible(true);return value.get(owner);
    }
    private static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
        final Scene scene=RotationBehaviorHookTest.bare(Scene.class);
        final CallbackAura aura=new CallbackAura();
        final TargetESP visual=new TargetESP();
        final TargetEspRenderer.Frame frame;
        @SuppressWarnings("unchecked") Run()throws Exception {
            f.mc.theWorld=scene;f.player.worldObj=scene;
            f.player.observeAcceptedPosition(0,64,0);
            ScaffoldClientFlowTest.set(Module.class,aura,"toggled",true);
            Map<Class<? extends Module>,Module> modules=(Map<Class<? extends Module>,Module>)
                    field(ModuleManager.class,Pulsar.INSTANCE.getMm(),"modulesByClass");
            modules.put(Aura.class,aura);
            visual.getBooleanSetting("Target Halo").setValue(false);
            visual.setToggled(true);
            frame=(TargetEspRenderer.Frame)field(TargetESP.class,visual,"frame");
            // A missing view deliberately stops valid observations before native GL calls.
            assertNull(f.mc.getRenderManager());
        }
        void draw(){Pulsar.BUS.post(new Event3D(.5f));}
        void accept(String source,float yaw){f.accept(source,yaw);}
        void prime(String source){accept(source,0);draw();assertFalse(frame.marker);assertEquals(0,scene.blockReads);}
        RotationManager.DiagnosticSnapshot snapshot(){return f.runtime.getRotationManager().diagnosticSnapshot(f.player,f.mc.theWorld,f.network.getClientPackets());}
        Candidate candidate()throws Exception {
            Candidate target=RotationBehaviorHookTest.bare(Candidate.class);
            target.worldObj=scene;target.setEntityId(7);
            target.posY=target.lastTickPosY=64;target.posZ=target.lastTickPosZ=3;
            target.setEntityBoundingBox(new AxisAlignedBB(-.3,64,2.7,.3,65.8,3.3));
            aura.observed=target;scene.target=target;return target;
        }
        void assertDiscarded()throws Exception {
            assertFalse(frame.halo);assertFalse(frame.marker);assertFalse(frame.markerHit);
            Object renderer=field(TargetESP.class,visual,"renderer");
            assertNull("an aborted observation must not create/reopen its GL program",field(TargetEspRenderer.class,renderer,"shader"));
            assertNull("the visual observer sends no packet",f.channel.readOutbound());
        }
        @Override public void close()throws Exception {try{visual.setToggled(false);}finally{f.close();}}
    }

    @Test public void disableDuringBlockTraceCannotPublishAMarkerOrReopenTheRenderer()throws Exception {
        try(Run r=new Run()) {
            r.prime("scaffold");r.scene.rayCallback=()->r.visual.setToggled(false);
            r.accept("scaffold",0);r.draw();
            assertEquals(1,r.scene.blockReads);assertFalse(r.visual.isToggled());r.assertDiscarded();
        }
    }

    @Test public void worldReplacementDuringBlockTraceDropsTheOldResultWithOrWithoutAnUnloadEvent()throws Exception {
        for(boolean announce:new boolean[]{false,true})try(Run r=new Run()) {
            r.prime("scaffold");Scene replacement=RotationBehaviorHookTest.bare(Scene.class);
            r.scene.rayCallback=()->{
                if(announce)Pulsar.BUS.post(new WorldEvent.Unload(r.scene));
                r.f.mc.theWorld=replacement;r.f.player.worldObj=replacement;
            };
            r.accept("scaffold",0);r.draw();
            assertEquals(1,r.scene.blockReads);assertEquals(0,replacement.blockReads);r.assertDiscarded();
        }
    }

    @Test public void connectionEpochReplacementDuringBlockTraceDropsTheOldResult()throws Exception {
        try(Run r=new Run()) {
            r.prime("scaffold");r.scene.rayCallback=()->r.f.network.getClientPackets().worldChanged();
            r.accept("scaffold",0);r.draw();
            assertEquals(1,r.scene.blockReads);r.assertDiscarded();
        }
    }

    @Test public void reentrantDisableReenableAndRenderWaitForANewObservationGeneration()throws Exception {
        try(Run r=new Run()) {
            r.prime("scaffold");r.scene.rayCallback=()->{
                r.visual.setToggled(false);r.visual.setToggled(true);r.draw();
            };
            r.accept("scaffold",0);r.draw();
            assertTrue(r.visual.isToggled());assertEquals(1,r.scene.blockReads);r.assertDiscarded();
            r.scene.rayCallback=null;r.draw();
            assertFalse("re-enable cannot revive the previous sequence",r.frame.marker);
            assertEquals(1,r.scene.blockReads);
            r.accept("scaffold",0);r.draw();assertTrue(r.frame.marker);assertEquals(2,r.scene.blockReads);
        }
    }

    @Test public void auraGetterDisableIsObservedBeforeAnyRayOrEntityQuery()throws Exception {
        try(Run r=new Run()) {
            r.prime("scaffold");r.aura.callback=()->r.visual.setToggled(false);
            r.accept("scaffold",0);r.draw();
            assertEquals(2,r.aura.reads);assertEquals(0,r.scene.blockReads);assertEquals(0,r.scene.entityQueries);
            r.assertDiscarded();
        }
    }

    @Test public void entityQueryAndEntityCollisionCallbacksCannotReviveACancelledFrame()throws Exception {
        for(boolean query:new boolean[]{true,false})try(Run r=new Run()) {
            Candidate target=r.candidate();r.prime("aura");
            if(query)r.scene.queryCallback=()->r.visual.setToggled(false);
            else target.collisionCallback=()->r.visual.setToggled(false);
            r.accept("aura",0);r.draw();
            assertEquals(1,r.scene.blockReads);assertEquals(1,r.scene.entityQueries);r.assertDiscarded();
        }
    }

    @Test public void acceptedAnglesRemainReadOnlyAndEveryNewAngleUpdatesTheExactPoint()throws Exception {
        try(Run r=new Run()) {
            r.prime("scaffold");r.accept("scaffold",0);
            RotationManager.DiagnosticSnapshot before=r.snapshot();r.draw();
            assertTrue(r.frame.marker);assertEquals(0,r.frame.markerX,.0002);assertEquals(6,r.frame.markerZ,.0002);
            assertEquals(65.62,r.frame.markerY,.0002);sameSnapshot(before,r.snapshot());
            r.accept("scaffold",90);before=r.snapshot();r.draw();
            assertTrue(r.frame.marker);assertEquals(-6,r.frame.markerX,.0002);assertEquals(0,r.frame.markerZ,.0002);
            assertEquals(2,r.scene.blockReads);sameSnapshot(before,r.snapshot());
            assertNull(r.f.channel.readOutbound());
        }
    }

    @Test public void disablingBothVisualSwitchesSkipsSelectionAndWorldWork()throws Exception {
        try(Run r=new Run()) {
            r.visual.getBooleanSetting("Hitpoint").setValue(false);r.accept("scaffold",0);
            RotationManager.DiagnosticSnapshot before=r.snapshot();r.draw();r.draw();
            assertEquals(0,r.aura.reads);assertEquals(0,r.scene.blockReads);assertEquals(0,r.scene.entityQueries);
            r.assertDiscarded();sameSnapshot(before,r.snapshot());
        }
    }

    @Test @SuppressWarnings("unchecked") public void nscaffoldMarkerUsesItsOwnOwnerAndSourceFilterWithoutChangingRotation()throws Exception {
        try(Run r=new Run()) {
            NScaffold builder=new NScaffold();
            ScaffoldClientFlowTest.set(Module.class,builder,"toggled",true);
            Map<Class<? extends Module>,Module> modules=(Map<Class<? extends Module>,Module>)
                    field(ModuleManager.class,Pulsar.INSTANCE.getMm(),"modulesByClass");
            modules.put(NScaffold.class,builder);
            r.visual.getModeSetting("Hitpoint Source").setValue("NScaffold");
            r.prime("nscaffold");r.accept("nscaffold",90);
            RotationManager.DiagnosticSnapshot before=r.snapshot();r.draw();
            assertTrue(r.frame.marker);assertEquals(-6,r.frame.markerX,.0002);sameSnapshot(before,r.snapshot());
            ScaffoldClientFlowTest.set(Module.class,builder,"toggled",false);
            r.accept("nscaffold",90);r.draw();assertFalse(r.frame.marker);
            ScaffoldClientFlowTest.set(Module.class,builder,"toggled",true);
            r.visual.getModeSetting("Hitpoint Source").setValue("Scaffold");
            r.accept("nscaffold",90);r.draw();r.accept("nscaffold",90);r.draw();assertFalse(r.frame.marker);
            assertEquals(1,r.scene.blockReads);assertEquals(0,r.scene.entityQueries);
            assertNull(r.f.channel.readOutbound());
        }
    }

    private static void sameSnapshot(RotationManager.DiagnosticSnapshot before,RotationManager.DiagnosticSnapshot after) {
        assertEquals(before.sequence,after.sequence);assertEquals(before.collecting,after.collecting);
        assertEquals(before.accepted,after.accepted);assertEquals(before.hasEffective,after.hasEffective);
        assertEquals(before.owner,after.owner);assertEquals(before.requestedBy,after.requestedBy);
        assertEquals(before.priority,after.priority);assertEquals(before.contenders,after.contenders);
        assertEquals(before.wireYaw,after.wireYaw,0);assertEquals(before.wirePitch,after.wirePitch,0);
    }
}
