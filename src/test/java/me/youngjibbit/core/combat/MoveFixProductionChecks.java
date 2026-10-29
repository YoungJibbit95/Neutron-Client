package me.youngjibbit.core.combat;

import me.youngjibbit.module.Module;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runs in its own JVM so the static-final legacy client fixture remains stable under JIT. */
public class MoveFixProductionChecks {
    @Test public void everyModeReachesRealAccelerationWithVanillaSlowdownsAndSilentCamera() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            for(String owner:new String[]{"aura","scaffold"}) for(MoveFixController.Mode mode:MoveFixController.Mode.values()) {
                Module module=owner.equals("aura")?f.aura:f.scaffold;
                module.getModeSetting("movefix").setValue(mode.label);
                for(float camera:new float[]{-179,-90,0,17,90,179,360}) for(float accepted:new float[]{-179,-90,0,71,90,179})
                    // Vanilla damps keys by .98 after applying sneak (.3) and item (.2) strength.
                    for(float strength:new float[]{.98f,.294f,.196f,.0588f})
                        for(float s:new float[]{-1,0,1}) for(float forward:new float[]{-1,0,1}) {
                            f.player.rotationYaw=camera;f.accept(owner,accepted);f.player.motionX=f.player.motionZ=0;
                            f.player.moveFlying(s*strength,forward*strength,.1f);
                            double magnitude=Math.hypot(s*strength,forward*strength);
                            double acceleration=.1*Math.min(1,magnitude);
                            if(mode==MoveFixController.Mode.LEGACY_ROUNDED) {
                                // Rounding creates a new vanilla key combination. It can have a
                                // different length from the old SIMPLE/PRECISE movement contract.
                                MoveFixController.Input snapped=MoveFixController.correct(
                                        s*strength,forward*strength,camera,accepted,mode);
                                double snapMagnitude=Math.hypot(snapped.strafe,snapped.forward);
                                double snapAcceleration=.1*Math.min(1,snapMagnitude);
                                assertEquals(snapAcceleration,Math.hypot(f.player.motionX,f.player.motionZ),3e-5);
                                if(snapMagnitude>0) {
                                    double heading=accepted-Math.toDegrees(Math.atan2(snapped.strafe,snapped.forward));
                                    assertEquals(-Math.sin(Math.toRadians(heading))*snapAcceleration,f.player.motionX,3e-5);
                                    assertEquals(Math.cos(Math.toRadians(heading))*snapAcceleration,f.player.motionZ,3e-5);
                                }
                            } else {
                                assertEquals(acceleration,Math.hypot(f.player.motionX,f.player.motionZ),3e-5);
                                if(magnitude>0) {
                                    // +forward at yaw 0 points south (+Z); +strafe east (+X).
                                    double basis=mode==MoveFixController.Mode.ROTATION_BOUND
                                            || mode==MoveFixController.Mode.LEGACY_STRICT?accepted:camera;
                                    double heading=basis-Math.toDegrees(Math.atan2(s,forward));
                                    if(mode==MoveFixController.Mode.SIMPLE) {
                                        double actual=Math.toDegrees(Math.atan2(-f.player.motionX,f.player.motionZ));
                                        double difference=Math.IEEEremainder(actual-heading,360);
                                        assertTrue("Simple correction exceeds half an octant",Math.abs(difference)<22.53);
                                    } else {
                                        assertEquals(-Math.sin(Math.toRadians(heading))*acceleration,f.player.motionX,3e-5);
                                        assertEquals(Math.cos(Math.toRadians(heading))*acceleration,f.player.motionZ,3e-5);
                                    }
                                }
                            }
                            assertEquals(camera,f.player.rotationYaw,0);
                            assertEquals(accepted,f.runtime.getRotationManager().getEffectiveYaw(),0);
                            assertEquals(owner,f.runtime.getRotationManager().getConfirmedOwner());
                            if(s==0 && forward==1 && strength==.98f) {
                                f.player.motionX=f.player.motionZ=0;f.player.sprint=true;f.player.jump();f.player.sprint=false;
                                assertEquals(.2,Math.hypot(f.player.motionX,f.player.motionZ),3e-5);
                                double basis=mode==MoveFixController.Mode.ROTATION_BOUND
                                        || mode==MoveFixController.Mode.LEGACY_STRICT
                                        || mode==MoveFixController.Mode.LEGACY_ROUNDED?accepted:camera;
                                if(mode==MoveFixController.Mode.SIMPLE) {
                                    double actual=Math.toDegrees(Math.atan2(-f.player.motionX,f.player.motionZ));
                                    assertTrue(Math.abs(Math.IEEEremainder(actual-basis,360))<22.53);
                                } else {
                                    assertEquals(-Math.sin(Math.toRadians(basis))*.2,f.player.motionX,3e-5);
                                    assertEquals(Math.cos(Math.toRadians(basis))*.2,f.player.motionZ,3e-5);
                                }
                                assertEquals(camera,f.player.rotationYaw,0);
                            }
                            assertNull(f.channel.readOutbound());
                        }
            }
        }
    }
}
