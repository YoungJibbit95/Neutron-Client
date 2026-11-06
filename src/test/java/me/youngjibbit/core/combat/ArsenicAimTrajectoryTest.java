package me.youngjibbit.core.combat;

import me.youngjibbit.core.arsenic.aim.AimCore;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArsenicAimTrajectoryTest {
    static AimCore.Input input() {
        AimCore.Input in=new AimCore.Input();in.eyeY=1.62;
        in.minX=2.7;in.maxX=3.3;in.minZ=-.3;in.maxZ=.3;in.maxY=1.8;
        in.targetId=7;return in;
    }
    static RotationProfile profile(RotationProfile.ArsenicMode mode) {
        RotationProfile p=new RotationProfile();p.arsenic=mode;p.yawSpeed=24;p.pitchSpeed=4;return p;
    }
    @Test public void bothControllersRespectAngularCapsAndConvergeAcrossWrap() {
        for(RotationProfile.ArsenicMode mode:new RotationProfile.ArsenicMode[]{RotationProfile.ArsenicMode.LAZY,RotationProfile.ArsenicMode.HEURISTICS}) {
            ArsenicAimTrajectory trajectory=new ArsenicAimTrajectory(871);Object target=new Object();
            AimCore.Input in=input();in.curYaw=179;in.curPitch=-60;
            RotationProfile p=profile(mode);p.missChance=0;
            for(int tick=0;tick<120;tick++) {
                float[] next=trajectory.advance(target,in,p,tick,tick*50_000_000L);
                assertTrue(Float.isFinite(next[0]));assertTrue(Float.isFinite(next[1]));
                assertTrue(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(next[0]-in.curYaw))<=24.001);
                assertTrue(Math.abs(next[1]-in.curPitch)<=4.001);assertTrue(next[1]>=-90 && next[1]<=90);
                in.curYaw=next[0];in.curPitch=next[1];
            }
            assertTrue(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(in.curYaw+90))<20);
            assertTrue(Math.abs(in.curPitch)<25);
        }
    }
    @Test public void repeatedQueriesDoNotAdvanceHistoryOrFlickTime() {
        ArsenicAimTrajectory a=new ArsenicAimTrajectory(42),b=new ArsenicAimTrajectory(42);
        Object target=new Object();AimCore.Input in=input();RotationProfile p=profile(RotationProfile.ArsenicMode.HEURISTICS);
        float[] first=a.advance(target,in,p,1,0);
        for(int i=0;i<50;i++)assertArrayEquals(first,a.advance(target,in,p,1,1000+i),0);
        b.advance(target,in,p,1,0);
        assertArrayEquals(a.advance(target,in,p,2,50_000_000),b.advance(target,in,p,2,50_000_000),0);
        assertArrayEquals(new float[]{0,0},a.advance(new Object(),in,p,2,50_000_001),0);
    }
    @Test public void identityReuseAndLongPausesStartFreshAcquisitions() {
        ArsenicAimTrajectory a=new ArsenicAimTrajectory(19),b=new ArsenicAimTrajectory(19);
        Object first=new Object(),second=new Object();AimCore.Input in=input();RotationProfile p=profile(RotationProfile.ArsenicMode.LAZY);
        for(int i=0;i<5;i++){a.advance(first,in,p,i,i*50_000_000L);b.advance(first,in,p,i,i*50_000_000L);}
        b.reset();
        assertArrayEquals(b.advance(second,in,p,5,250_000_000L),a.advance(second,in,p,5,250_000_000L),0);
        b.reset();
        assertArrayEquals(b.advance(second,in,p,6,900_000_000L),a.advance(second,in,p,6,900_000_000L),0);
    }
    @Test public void relativePredictionLeadsMovingTargetsAndCancelsSharedMotion() {
        AimCore core=new AimCore(AimCore.Tuning.best(),new java.util.Random(1));AimCore.Input in=input();
        in.targetDZ=.2;core.observe(in);assertArrayEquals(new double[]{0,.6},core.predictOffset(in,3),1e-8);
        in.selfDZ=.2;assertArrayEquals(new double[]{0,0},core.predictOffset(in,3),1e-8);
    }
}
