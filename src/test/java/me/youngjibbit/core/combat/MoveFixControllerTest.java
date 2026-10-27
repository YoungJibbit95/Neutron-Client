package me.youngjibbit.core.combat;

import me.youngjibbit.event.player.JumpFixEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import net.minecraft.util.MathHelper;
import org.junit.Test;
import static org.junit.Assert.*;

public class MoveFixControllerTest {
    private static final float[] ANGLES = {0, 17, 45, 90, -90, 179, -179, 360, -720};
    private static final float[][] KEYS = {{0,0},{0,1},{0,-1},{1,0},{-1,0},{1,1},{-1,1},{1,-1},{-1,-1}};
    private double norm(float s, float f) { return Math.hypot(s,f); }

    // Actual moveFlying math, including vanilla's single cap and its float trig table.
    private double[] acceleration(float s, float f, float yaw, float friction) {
        float length = s*s + f*f;
        if (length < 1.0e-4f) return new double[]{0,0};
        float scale = friction / Math.max(1, MathHelper.sqrt_float(length));
        s *= scale; f *= scale;
        float sin = MathHelper.sin(yaw * (float)Math.PI / 180), cos = MathHelper.cos(yaw * (float)Math.PI / 180);
        return new double[]{s*cos-f*sin, f*cos+s*sin};
    }

    @Test public void precisePreservesAllKeyDirectionsAndMagnitudesAcrossAnglesAndSlowdowns() {
        for (float camera : ANGLES) for (float target : ANGLES) for (float[] keys : KEYS)
            for (float factor : new float[]{1,.3f,.2f,.06f}) {
                float s=keys[0]*factor, f=keys[1]*factor;
                MoveFixController.Input result=MoveFixController.correct(s,f,camera,target,MoveFixController.Mode.PRECISE);
                assertEquals(norm(s,f),norm(result.strafe,result.forward),2e-7);
                double delta=Math.toRadians(camera);
                double[] expected={s*Math.cos(delta)-f*Math.sin(delta),s*Math.sin(delta)+f*Math.cos(delta)};
                double other=Math.toRadians(target);
                assertEquals(expected[0], result.strafe*Math.cos(other)-result.forward*Math.sin(other),3e-7);
                assertEquals(expected[1], result.strafe*Math.sin(other)+result.forward*Math.cos(other),3e-7);
                assertArrayEquals(acceleration(s,f,camera,.1f),acceleration(result.strafe,result.forward,target,.1f),2e-5);
            }
    }

    @Test public void simpleUsesNearestOfEightDirectionsWithoutIncreasingInputStrength() {
        for (float camera : ANGLES) for (float target : ANGLES) for (float[] keys : KEYS)
            for (float factor : new float[]{1,.3f,.06f}) {
                float s=keys[0]*factor, f=keys[1]*factor;
                MoveFixController.Input result=MoveFixController.correct(s,f,camera,target,MoveFixController.Mode.SIMPLE);
                assertEquals(norm(s,f),norm(result.strafe,result.forward),2e-7);
                assertTrue(result.strafe==0 || result.forward==0 || Math.abs(Math.abs(result.strafe)-Math.abs(result.forward))<1e-6);
                if (norm(s,f)==0) continue;
                double requested=camera-Math.toDegrees(Math.atan2(s,f));
                double chosen=target-Math.toDegrees(Math.atan2(result.strafe,result.forward));
                assertTrue(Math.abs(MoveFixController.wrap((float)(requested-chosen)))<=22.5001);
                double[] original=acceleration(s,f,camera,.13f), corrected=acceleration(result.strafe,result.forward,target,.13f);
                assertEquals(Math.hypot(original[0],original[1]),Math.hypot(corrected[0],corrected[1]),2e-6);
            }
    }

    @Test public void offLeavesTheOriginalInputAndYawUntouched() {
        for (float[] keys : KEYS) {
            MoveFixController.Input result=MoveFixController.correct(keys[0],keys[1],13,150,MoveFixController.Mode.OFF);
            assertEquals(keys[0],result.strafe,0); assertEquals(keys[1],result.forward,0);
        }
        PlayerMoveUpdateEvent event=new PlayerMoveUpdateEvent(.3f,-.3f,.02f,17,7);
        RotationCorrections.applyMovement(selection(MoveFixController.Mode.OFF,120),event);
        assertEquals(17,event.getYaw(),0); assertEquals(.3f,event.getStrafe(),0); assertEquals(-.3f,event.getForward(),0);
    }

    private RotationCorrections.Selection selection(MoveFixController.Mode mode,float yaw) {
        RotationManager manager=new RotationManager();manager.beginMotion(17,7);
        manager.request("aura",RotationManager.AURA_PRIORITY,yaw,9);manager.finishMotion(yaw,9,true);
        return RotationCorrections.select(manager,"aura",true,mode,BodyRotationController.Mode.NATURAL);
    }

    @Test public void movementAdapterPreservesFrictionPitchAndSlowInputAndHonorsCancellation() {
        PlayerMoveUpdateEvent event=new PlayerMoveUpdateEvent(.3f,.3f,.013f,17,7);
        RotationCorrections.applyMovement(selection(MoveFixController.Mode.PRECISE,129),event);
        assertEquals(.013f,event.getFriction(),0);assertEquals(7,event.getPitch(),0);assertEquals(129,event.getYaw(),0);
        assertEquals(Math.sqrt(.18),norm(event.getStrafe(),event.getForward()),1e-7);
        PlayerMoveUpdateEvent cancelled=new PlayerMoveUpdateEvent(0,1,.1f,17,7);cancelled.setCancelled(true);
        RotationCorrections.applyMovement(selection(MoveFixController.Mode.PRECISE,129),cancelled);
        assertEquals(17,cancelled.getYaw(),0);assertEquals(1,cancelled.getForward(),0);
        RotationCorrections.applyMovement(null,cancelled); assertTrue(cancelled.isCancelled());
    }

    @Test public void sprintJumpRetainsVanillaImpulseStrengthAndUsesTheSelectedMovementBasis() {
        for(float camera:ANGLES) for(float target:ANGLES) for(MoveFixController.Mode mode:MoveFixController.Mode.values()) {
            JumpFixEvent event=new JumpFixEvent(camera);
            RotationCorrections.applyJump(selection(mode,target),event);
            if(mode==MoveFixController.Mode.ROTATION_BOUND) assertEquals(target,event.getYaw(),0);
            else if(mode!=MoveFixController.Mode.SIMPLE) assertEquals(camera,event.getYaw(),0);
            else {
                MoveFixController.Input forward=MoveFixController.correct(0,1,camera,target,mode);
                double heading=target-Math.toDegrees(Math.atan2(forward.strafe,forward.forward));
                assertEquals(0,MoveFixController.wrap((float)heading-event.getYaw()),1e-4);
                assertTrue(Math.abs(MoveFixController.wrap(camera-event.getYaw()))<=22.5001);
            }
            float yaw=event.getYaw()*.017453292F;
            double x=-MathHelper.sin(yaw)*.2F,z=MathHelper.cos(yaw)*.2F;
            // Each vanilla lookup can differ by one 65536-entry table step, plus float rounding.
            double lookupBound=.2*Math.sqrt(2)*(2*Math.PI/65536)+1e-7;
            assertEquals(.2,Math.hypot(x,z),lookupBound);
            if(mode!=MoveFixController.Mode.SIMPLE) {
                float basis = mode==MoveFixController.Mode.ROTATION_BOUND ? target : camera;
                assertEquals(-MathHelper.sin(basis*.017453292F)*.2F,x,0);
                assertEquals(MathHelper.cos(basis*.017453292F)*.2F,z,0);
            }
        }
    }

    @Test public void continuousModeAcceptsAnalogInputsWithoutSnappingAndWrapsTheShortWay() {
        MoveFixController.Input result=MoveFixController.correct(.12f,.47f,179,-179,MoveFixController.Mode.PRECISE);
        double radians=Math.toRadians(-2);
        assertEquals(.12*Math.cos(radians)-.47*Math.sin(radians),result.strafe,2e-8);
        assertEquals(.12*Math.sin(radians)+.47*Math.cos(radians),result.forward,2e-8);
    }

    @Test public void finiteYawSubtractionCannotOverflowBeforeWrapping() {
        for (MoveFixController.Mode mode : new MoveFixController.Mode[]{MoveFixController.Mode.PRECISE,MoveFixController.Mode.SIMPLE}) {
            MoveFixController.Input result=MoveFixController.correct(.3f,.6f,Float.MAX_VALUE,-Float.MAX_VALUE,mode);
            assertTrue(Float.isFinite(result.strafe));assertTrue(Float.isFinite(result.forward));
            assertEquals(Math.hypot(.3f,.6f),Math.hypot(result.strafe,result.forward),1e-7);
        }
    }
}
