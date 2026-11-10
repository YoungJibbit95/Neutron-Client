package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.junit.Test;
import static org.junit.Assert.*;

/** Production PRE/C03/POST, real fresh keys; synthetic world and stationary physical position. */
public class AdaptiveBridgeRefinementFlowChecks {
    private static Object field(AdaptiveBridgeInputSafetyFlowTest.Run run,String name) throws Exception {
        return ScaffoldOwnerContinuityTest.field(Scaffold.class,name).get(run.f.scaffold);
    }
    private static void visibleFutureFace(AdaptiveBridgeInputSafetyFlowTest.Run run,String profile) {
        run.adaptive("Off");
        run.f.scaffold.getModeSetting("Bridge Rotation Profile").setValue(profile);
        run.f.scaffold.getModeSetting("Sprint Mode").setValue("Off");
        run.scene.blocks.clear();
        run.scene.blocks.put(new BlockPos(0,63,0),Blocks.stone.getDefaultState());
        run.scene.blocks.put(new BlockPos(1,63,-1),Blocks.stone.getDefaultState());
        run.position(.8,.5);run.player.rotationYaw=-90;run.player.rotationPitch=0;
        run.player.motionX=.15;run.settings.keyBindForward.pressed=true;
    }
    private static void step(AdaptiveBridgeInputSafetyFlowTest.Run run) {
        run.step(true,()->{});
        for(Object packet:run.drain())
            assertFalse("a look-ahead must remain a preview while the foot cell is occupied",
                    packet instanceof C08PacketPlayerBlockPlacement);
    }

    @Test public void slowGodbridgeAcquisitionKeepsTheCanonicalTargetUntilItIsReached() throws Exception {
        try(AdaptiveBridgeInputSafetyFlowTest.Run run=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
            visibleFutureFace(run,"Godbridge");
            run.f.scaffold.getNumberSetting("Min Aim Speed").setValue(4D);
            run.f.scaffold.getNumberSetting("Max Aim Speed").setValue(4D);
            run.f.scaffold.onEnable();
            RotationManager rotations=run.f.runtime.getRotationManager();
            Vec3 firstHit=null;Object identity=null;float lastYaw=-90,lastPitch=0;int acquiredAt=-1;
            for(int tick=0;tick<60;tick++) {
                step(run);
                PlacementTarget target=(PlacementTarget)field(run,"bridgePreview");
                assertNotNull("each acquisition PRE keeps a checked face",target);
                if(firstHit==null) {
                    firstHit=target.getHitVec();
                    identity=run.f.runtime.getScaffoldPlacementService().rotationIdentity(target);
                }
                assertSame(identity,run.f.runtime.getScaffoldPlacementService().rotationIdentity(target));
                assertEquals("a partial turn must not become the next target",0,firstHit.distanceTo(target.getHitVec()),1e-10);
                float yaw=rotations.getEffectiveYaw(),pitch=rotations.getEffectivePitch();
                assertTrue(Math.abs(MathHelper.wrapAngleTo180_float(yaw-lastYaw))<=4.151);
                assertTrue(Math.abs(pitch-lastPitch)<=4.151);
                lastYaw=yaw;lastPitch=pitch;
                if((Boolean)field(run,"bridgeHasAim")) {
                    ScaffoldRotationPlan desired=ScaffoldRotationPlan.build("Back",run.player.getPositionEyes(1),firstHit);
                    assertEquals(0,MathHelper.wrapAngleTo180_float(yaw-desired.getYaw()),.151);
                    assertEquals(desired.getPitch(),pitch,.151);
                    acquiredAt=tick;break;
                }
            }
            assertTrue("the global speed cap deliberately requires several real motions",acquiredAt>10);
            assertTrue("a low cap must still converge to its canonical point",acquiredAt<60);
            for(int tick=0;tick<5;tick++) {
                step(run);
                assertEquals(lastYaw,rotations.getEffectiveYaw(),.151);
                assertEquals(lastPitch,rotations.getEffectivePitch(),.151);
            }
            assertEquals(64,run.player.inventory.mainInventory[3].stackSize);
        }
    }

    @Test public void diagonalKeyTransitionRetainsTheManoeuvreButAReversalRevokesIt() throws Exception {
        for(String profile:new String[]{"Adaptive","Godbridge"})
            try(AdaptiveBridgeInputSafetyFlowTest.Run run=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                visibleFutureFace(run,profile);run.f.scaffold.onEnable();
                for(int tick=0;tick<6;tick++)step(run);
                AdaptiveBridgePlanner.Scope before=(AdaptiveBridgePlanner.Scope)field(run,"bridgeScope");
                PlacementTarget target=(PlacementTarget)field(run,"bridgePreview");assertNotNull(target);
                Object identity=run.f.runtime.getScaffoldPlacementService().rotationIdentity(target);
                run.settings.keyBindRight.pressed=true;
                step(run);
                AdaptiveBridgePlanner.Scope diagonal=(AdaptiveBridgePlanner.Scope)field(run,"bridgeScope");
                assertEquals("45-degree changes keep the useful acquisition clock",before.acquisitionGeneration,
                        diagonal.acquisitionGeneration);
                PlacementTarget held=(PlacementTarget)field(run,"bridgePreview");assertNotNull(held);
                assertSame("same needed face keeps its immutable identity",identity,
                        run.f.runtime.getScaffoldPlacementService().rotationIdentity(held));
                run.settings.keyBindForward.pressed=false;run.settings.keyBindRight.pressed=false;
                run.settings.keyBindBack.pressed=true;
                step(run);
                AdaptiveBridgePlanner.Scope reverse=(AdaptiveBridgePlanner.Scope)field(run,"bridgeScope");
                assertTrue("a deliberate reversal starts a new task",reverse.acquisitionGeneration>diagonal.acquisitionGeneration);
                assertEquals(64,run.player.inventory.mainInventory[3].stackSize);
            }
    }

    @Test public void defaultGodbridgeStartsAtItsUsefulGeometryAndHoldsItWithoutNoiseDrift() throws Exception {
        try(AdaptiveBridgeInputSafetyFlowTest.Run run=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
            visibleFutureFace(run,"Godbridge");
            run.f.scaffold.getModeSetting("Aim Noise Profile").setValue("Lively");
            run.f.scaffold.getNumberSetting("Aim Noise Amount").setValue(1D);
            run.f.scaffold.onEnable();step(run);
            assertTrue((Boolean)field(run,"bridgeHasAim"));
            RotationManager rotations=run.f.runtime.getRotationManager();
            float yaw=rotations.getEffectiveYaw(),pitch=rotations.getEffectivePitch();
            PlacementTarget target=(PlacementTarget)field(run,"bridgePreview");assertNotNull(target);
            ScaffoldRotationPlan desired=ScaffoldRotationPlan.build("Back",run.player.getPositionEyes(1),target.getHitVec());
            assertEquals(0,MathHelper.wrapAngleTo180_float(yaw-desired.getYaw()),.151);
            assertEquals(desired.getPitch(),pitch,.151);
            for(int tick=0;tick<30;tick++) {
                step(run);assertEquals(yaw,rotations.getEffectiveYaw(),.151);
                assertEquals(pitch,rotations.getEffectivePitch(),.151);
            }
        }
    }
}
