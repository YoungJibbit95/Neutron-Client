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
    @Test public void softTurnAcceleratesBrakesAndRestartsOnTargetSwitch() {
        ArsenicMotionCurve curve = new ArsenicMotionCurve(new java.util.Random(81));
        float remaining = 100, first = 0, peak = 0, last = 0;
        for (int i = 0; i < 40 && remaining > 0; i++) {
            float step = curve.advance(remaining, 0, 180, 8)[0];
            if (i == 0) first = step;
            assertTrue(step > 0 && step <= remaining);
            peak = Math.max(peak, step);
            last = step;
            remaining -= step;
        }
        assertEquals(0, remaining, .001);
        assertTrue(first <= 2.3 && peak > first * 2 && last < peak);
        for (RotationProfile.ArsenicMode mode : new RotationProfile.ArsenicMode[]{
                RotationProfile.ArsenicMode.HEURISTICS, RotationProfile.ArsenicMode.LAZY}) {
            RotationProfile p = profile(mode);
            p.softArsenicMotion = true;
            AimCore.Input in = input();
            Object oldTarget = new Object();
            ArsenicAimTrajectory path = new ArsenicAimTrajectory(123);
            for (int i = 0; i < 6; i++) {
                float[] next = path.advance(oldTarget, in, p, i, i * 50_000_000L);
                assertArrayEquals(next, path.advance(oldTarget, in, p, i, i * 50_000_000L + 8), 0);
                if (i == 0) assertTrue(Math.abs(next[0] - in.curYaw) <= 2.3);
                in.curYaw = next[0]; in.curPitch = next[1];
            }
            float[] next = path.advance(new Object(), in, p, 6, 300_000_000L);
            assertTrue(Math.abs(next[0] - in.curYaw) <= 2.3);
        }
    }

    @Test public void hitpointNoiseStaysInsideMovingBoxesAndSpeedControlsItsClock() {
        for (RotationProfile.HitpointNoise style : RotationProfile.HitpointNoise.values()) {
            ArsenicHitpoint slow = new ArsenicHitpoint(17), fast = new ArsenicHitpoint(17);
            AimCore.Input a = input(), b = input();
            RotationProfile p = profile(RotationProfile.ArsenicMode.LAZY);
            p.hitpointStrength = 1; p.hitpointNoise = style;
            for (int i = 1; i < 100; i++) {
                a.minX = b.minX = i - .3; a.maxX = b.maxX = i + .3;
                p.hitpointSpeed = 1; slow.sample(a, p, .05);
                p.hitpointSpeed = 2; fast.sample(b, p, .025);
                assertEquals(a.hitX, b.hitX, 1e-9);
                assertEquals(a.hitY, b.hitY, 1e-9);
                assertTrue(a.hitX > a.minX && a.hitX < a.maxX);
                assertTrue(a.hitY > a.minY && a.hitY < a.maxY);
                assertTrue(a.hitZ > a.minZ && a.hitZ < a.maxZ);
            }
        }
    }

    @Test public void hitpointStrengthChangesBothLiveArsenicPathsWithIdenticalRandomSeeds() {
        for (RotationProfile.ArsenicMode mode : new RotationProfile.ArsenicMode[]{
                RotationProfile.ArsenicMode.HEURISTICS, RotationProfile.ArsenicMode.LAZY}) {
            ArsenicAimTrajectory on = new ArsenicAimTrajectory(56), off = new ArsenicAimTrajectory(56);
            RotationProfile p = profile(mode);
            Object target = new Object();
            AimCore.Input a = input(), b = input();
            boolean differs = false;
            a.curYaw = b.curYaw = -90;
            for (int i = 0; i < 50; i++) {
                p.hitpointStrength = 1;
                float[] changed = on.advance(target, a, p, i, i * 50_000_000L);
                p.hitpointStrength = 0;
                float[] legacy = off.advance(target, b, p, i, i * 50_000_000L);
                differs |= Math.abs(changed[0] - legacy[0]) > .01 || Math.abs(changed[1] - legacy[1]) > .01;
            }
            assertTrue(mode.name(), differs);
        }
    }

    @Test public void relativePredictionLeadsMovingTargetsAndCancelsSharedMotion() {
        AimCore core=new AimCore(AimCore.Tuning.best(),new java.util.Random(1));AimCore.Input in=input();
        in.targetDZ=.2;core.observe(in);assertArrayEquals(new double[]{0,.6},core.predictOffset(in,3),1e-8);
        in.selfDZ=.2;assertArrayEquals(new double[]{0,0},core.predictOffset(in,3),1e-8);
    }
}
