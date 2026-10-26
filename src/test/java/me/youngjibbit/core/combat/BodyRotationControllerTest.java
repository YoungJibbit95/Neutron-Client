package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

public class BodyRotationControllerTest {
    private final Object player=new Object(),world=new Object();
    private BodyRotationController.Pose sample(BodyRotationController controller,String owner,BodyRotationController.Mode mode,float yaw,float body,long nanos) {
        return controller.sample(player,world,owner,mode,yaw,33,body,nanos);
    }

    @Test public void vanillaReturnsNoOverrideAndDirectFollowsAcceptedHeadAndBody() {
        BodyRotationController controller=new BodyRotationController();
        assertNull(sample(controller,"aura",BodyRotationController.Mode.VANILLA,100,0,0));
        BodyRotationController.Pose direct=sample(controller,"aura",BodyRotationController.Mode.DIRECT,179,-40,0);
        assertEquals(179,direct.headYaw,0);assertEquals(179,direct.bodyYaw,0);assertEquals(33,direct.headPitch,0);
    }

    @Test public void naturalBoundsHeadBodyAngleThenSmoothsMonotonically() {
        BodyRotationController controller=new BodyRotationController();
        BodyRotationController.Pose first=sample(controller,"aura",BodyRotationController.Mode.NATURAL,160,0,0);
        assertEquals(75,MoveFixController.wrap(first.headYaw-first.bodyYaw),0);
        float before=first.bodyYaw;
        for(int i=1;i<=100;i++) {
            BodyRotationController.Pose pose=sample(controller,"aura",BodyRotationController.Mode.NATURAL,160,0,i*10_000_000L);
            assertEquals(160,pose.headYaw,0);assertEquals(33,pose.headPitch,0);
            assertTrue(pose.bodyYaw>=before);assertTrue(pose.bodyYaw<=160);
            assertTrue(Math.abs(MoveFixController.wrap(pose.headYaw-pose.bodyYaw))<=75);
            before=pose.bodyYaw;
        }
        assertTrue(160-before<.3);
    }

    @Test public void bodyTakesShortestPathAcrossBothWrapBoundaries() {
        for(float sign:new float[]{1,-1}) {
            BodyRotationController controller=new BodyRotationController();
            sample(controller,"aura",BodyRotationController.Mode.NATURAL,-179*sign,179*sign,0);
            BodyRotationController.Pose pose=sample(controller,"aura",BodyRotationController.Mode.NATURAL,-179*sign,179*sign,180_000_000L);
            assertEquals(-2*sign/Math.E,MoveFixController.wrap(pose.bodyYaw-pose.headYaw),2e-5);
        }
    }

    @Test public void exponentialSmoothingIsIndependentOfFrameSubdivision() {
        float reference=0;
        for(int frames:new int[]{1,2,10,30,60,144}) {
            BodyRotationController controller=new BodyRotationController();
            sample(controller,"aura",BodyRotationController.Mode.NATURAL,160,0,0);
            BodyRotationController.Pose pose=null;
            for(int i=1;i<=frames;i++) pose=sample(controller,"aura",BodyRotationController.Mode.NATURAL,160,0,1_000_000_000L*i/frames);
            if(frames==1) reference=pose.bodyYaw;
            assertEquals(reference,pose.bodyYaw,1e-4);
        }
    }

    @Test public void ownerWorldPlayerAndModeChangesReseedFromVanillaInsteadOfOldBody() {
        BodyRotationController controller=new BodyRotationController();
        sample(controller,"aura",BodyRotationController.Mode.NATURAL,100,10,0);
        sample(controller,"aura",BodyRotationController.Mode.NATURAL,100,10,500_000_000L);
        BodyRotationController.Pose scaffold=sample(controller,"scaffold",BodyRotationController.Mode.NATURAL,-30,-40,600_000_000L);
        assertEquals(-40,scaffold.bodyYaw,0);
        BodyRotationController.Pose changedWorld=controller.sample(player,new Object(),"scaffold",BodyRotationController.Mode.NATURAL,40,0,15,700_000_000L);
        assertEquals(15,changedWorld.bodyYaw,0);
        BodyRotationController.Pose changedPlayer=controller.sample(new Object(),world,"scaffold",BodyRotationController.Mode.NATURAL,40,0,25,800_000_000L);
        assertEquals(25,changedPlayer.bodyYaw,0);
        assertNull(sample(controller,null,BodyRotationController.Mode.NATURAL,40,0,900_000_000L));
        assertEquals(12,sample(controller,"aura",BodyRotationController.Mode.NATURAL,40,12,1_000_000_000L).bodyYaw,0);
        assertNull(sample(controller,"aura",BodyRotationController.Mode.VANILLA,40,20,1_100_000_000L));
        assertEquals(20,sample(controller,"aura",BodyRotationController.Mode.NATURAL,40,20,1_200_000_000L).bodyYaw,0);
    }

    @Test public void sourceResetDoesNotDisturbOtherOwnerAndDeathResetsAll() {
        BodyRotationController controller=new BodyRotationController();
        sample(controller,"scaffold",BodyRotationController.Mode.NATURAL,40,10,0);controller.reset("aura");
        assertTrue(sample(controller,"scaffold",BodyRotationController.Mode.NATURAL,40,-20,100_000_000L).bodyYaw>10);
        controller.reset("scaffold");
        assertEquals(-20,sample(controller,"scaffold",BodyRotationController.Mode.NATURAL,40,-20,200_000_000L).bodyYaw,0);
        assertNull(controller.sample(null,world,"scaffold",BodyRotationController.Mode.NATURAL,40,0,10,300_000_000L));
        assertEquals(10,sample(controller,"scaffold",BodyRotationController.Mode.NATURAL,40,10,400_000_000L).bodyYaw,0);
    }

    @Test public void nonfiniteDataIsRejectedAndPitchIsBoundedWithoutChangingTargetInput() {
        BodyRotationController controller=new BodyRotationController();
        assertNull(sample(controller,"aura",BodyRotationController.Mode.NATURAL,Float.NaN,0,0));
        BodyRotationController.Pose pose=controller.sample(player,world,"aura",BodyRotationController.Mode.DIRECT,30,130,0,0);
        assertEquals(90,pose.headPitch,0);assertEquals(30,pose.headYaw,0);
    }
}
