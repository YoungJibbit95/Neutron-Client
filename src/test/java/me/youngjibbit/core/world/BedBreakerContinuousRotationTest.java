package me.youngjibbit.core.world;

import net.minecraft.util.MathHelper;
import org.junit.Test;

import static org.junit.Assert.*;

/** Bounds and lifecycle requirements of the BedBreaker opt-in; source-parity tests cover the default engine. */
public class BedBreakerContinuousRotationTest {
    private static final class Clock {
        long millis = 1000, nanos = 1_000_000_000L;
        final DemiseRotationEngine engine = DemiseRotationEngine.continuous(() -> millis, () -> nanos, (a,b) -> a);
        DemiseRotationEngine.Aim frame(float cameraYaw, float cameraPitch, float sensitivity) {
            millis += 17; nanos += 17_000_000L;
            engine.mousePre();
            return engine.mouseUpdate(cameraYaw, cameraPitch, sensitivity);
        }
        void accept() {
            float[] pose = engine.rotation();
            engine.observeAcceptedPacket(true, pose[0], pose[1]);
        }
    }

    @Test public void maximumSettingsAcquireAndTrackWithoutOvershootOrWholeAngleSteps() {
        for (DemiseRotationEngine.Smoothing mode : DemiseRotationEngine.Smoothing.values())
            for (boolean acceleration : new boolean[]{false, true})
                for (float sensitivity : new float[]{0, .5f, 1}) {
                    Clock c = new Clock();
                    DemiseRotationSettings.Snapshot settings = settings(mode, acceleration, true, 1, 180, false);
                    c.engine.clear(170, -25); c.accept(); c.engine.updateSpeeds(settings);
                    float[] target = {-10, 70};
                    c.engine.setRotation(target, settings);
                    assertArrayEquals("creating the target cannot rotate", new float[]{170,-25}, c.engine.rotation(), 0);
                    double firstGap = distance(c.engine.rotation(), target);
                    for (int frame = 0; frame < 450; frame++) {
                        c.engine.setRotation(target, settings);
                        float[] before = c.engine.rotation();
                        c.frame(170,-25,sensitivity);
                        float[] after = c.engine.rotation();
                        assertTrue("every frame has a bounded turn", distance(before,after) <= 12.0001);
                        assertTrue("stable tracking cannot overshoot the target", distance(after,target) <= distance(before,target) + .0001);
                        assertTrue(Math.abs(after[1]) <= 90);
                        if (frame == 0) {
                            assertTrue(distance(before,after) > 0);
                            assertTrue("maximum settings still leave a visible acquisition curve", distance(after,target) > firstGap * .5);
                        }
                        if (frame % 3 == 0) c.accept();
                    }
                    assertTrue("integer mouse counts must not strand the final aim", distance(c.engine.rotation(),target) < .001);
                }
    }

    @Test public void returnBeginsImmediatelyAndFinishesTheOriginalDiagonalDeadZone() {
        for (DemiseRotationEngine.Smoothing mode : DemiseRotationEngine.Smoothing.values()) {
            Clock c = new Clock();
            DemiseRotationSettings.Snapshot settings = settings(mode, true, true, .01f, .01f, true);
            c.engine.clear(90,60); c.accept(); c.engine.updateSpeeds(settings);
            c.engine.setRotation(new float[]{150,80}, settings);
            c.engine.beginReturn();
            assertFalse(c.engine.enabled()); assertTrue(c.engine.returning());
            assertArrayEquals(new float[]{90,60}, c.engine.rotation(), 0);
            float[] camera = {90.9f,60.9f};
            int frames;
            for (frames = 0; frames < 400 && c.engine.shouldRotate(); frames++) {
                float[] before = c.engine.rotation();
                c.frame(camera[0],camera[1],1);
                assertTrue(distance(before,c.engine.rotation()) <= 12.0001);
                if (frames == 0) {
                    assertTrue("return moves on the next frame without the original expiry wait", distance(before,c.engine.rotation()) > 0);
                    assertTrue("the sub-degree gap is eased, not reset", distance(c.engine.rotation(),camera) > .1);
                }
                c.accept();
            }
            assertTrue("a minimum speed and short stop must not keep a disabled owner forever", frames < 400);
            assertFalse(c.engine.shouldRotate()); assertFalse(c.engine.returning());
            assertArrayEquals(camera,c.engine.rotation(),0);
        }
    }

    @Test public void targetSettingsAndReenableRetainTheCurrentTrajectory() {
        Clock c = new Clock();
        DemiseRotationSettings.Snapshot initial = settings(DemiseRotationEngine.Smoothing.Linear,false,true,.15f,180,false);
        c.engine.clear(0,0); c.accept(); c.engine.updateSpeeds(initial);
        for (int i=0;i<10;i++) {
            c.engine.setRotation(new float[]{140,65},initial); c.frame(0,0,.5f); c.accept();
        }
        float[] tracking = c.engine.rotation();
        DemiseRotationSettings.Snapshot changed = settings(DemiseRotationEngine.Smoothing.Relative,true,false,1,35,false);
        c.engine.updateSpeeds(changed); c.engine.setRotation(new float[]{-135,-55},changed);
        assertArrayEquals("configuration/target changes must not reset to the camera", tracking,c.engine.rotation(),0);
        c.frame(0,0,.5f); assertTrue(distance(tracking,c.engine.rotation()) <= 12.0001); c.accept();
        c.engine.beginReturn(); float[] beforeReturn = c.engine.rotation();
        c.frame(0,0,.5f); float[] returning = c.engine.rotation();
        assertTrue(c.engine.returning()); assertTrue(distance(beforeReturn,returning) <= 12.0001);
        c.engine.setRotation(new float[]{70,20},initial);
        assertFalse(c.engine.returning()); assertTrue(c.engine.enabled());
        assertArrayEquals("re-enable resumes the tail rather than restarting from camera/server",returning,c.engine.rotation(),0);
        c.frame(0,0,.5f); assertTrue(distance(returning,c.engine.rotation()) <= 12.0001);
    }

    @Test public void rejectedPacketsCannotAccumulateAnInstantCatchup() {
        Clock c = new Clock();
        DemiseRotationSettings.Snapshot settings = settings(DemiseRotationEngine.Smoothing.Linear,false,true,1,180,false);
        c.engine.clear(0,0); c.accept(); c.engine.updateSpeeds(settings);
        float[] target = {175,85};
        for (int i=0;i<240;i++) {
            c.engine.setRotation(target,settings); c.frame(0,0,1);
            assertTrue("unaccepted animation remains within the next C03 turn budget",distance(new float[]{0,0},c.engine.rotation()) <= 45.0001);
        }
        assertTrue(distance(new float[]{0,0},c.engine.rotation()) > 40);
        float[] accepted = c.engine.rotation(); c.accept();
        for (int i=0;i<240;i++) {
            c.engine.setRotation(target,settings); c.frame(0,0,1);
            assertTrue(distance(accepted,c.engine.rotation()) <= 45.0001);
            if (i%3==0) { accepted=c.engine.rotation(); c.accept(); }
        }
        assertTrue(distance(c.engine.rotation(),target) < .001);
    }

    @Test public void movingCameraReturnKeepsFrameAndTransportBoundsUntilItFinishes() {
        Clock c = new Clock();
        DemiseRotationSettings.Snapshot settings = settings(DemiseRotationEngine.Smoothing.Linear,false,true,1,180,false);
        c.engine.clear(155,75); c.accept(); c.engine.updateSpeeds(settings);
        c.engine.setRotation(new float[]{155,75},settings); c.engine.beginReturn();
        float[] accepted = c.engine.rotation();
        float[] camera = {-145,-70};
        for (int i=0;i<500 && c.engine.shouldRotate();i++) {
            if (i==6) camera = new float[]{35,40};
            float[] before=c.engine.rotation(); c.frame(camera[0],camera[1],.5f);
            assertTrue(distance(before,c.engine.rotation()) <= 12.0001);
            assertTrue(distance(accepted,c.engine.rotation()) <= 45.0001);
            if (i%3==0) { accepted=c.engine.rotation(); c.accept(); }
        }
        assertFalse(c.engine.shouldRotate()); assertArrayEquals(camera,c.engine.rotation(),0);
    }

    @Test public void ownerHandoffStartsAtFactualServerPoseAndPreservesTheDesiredTarget() {
        Clock c = new Clock();
        DemiseRotationSettings.Snapshot settings = settings(DemiseRotationEngine.Smoothing.Linear,false,true,1,180,false);
        c.engine.clear(0,0); c.engine.updateSpeeds(settings);
        float[] target={120,60}; c.engine.setRotation(target,settings); c.frame(0,0,.5f);
        c.engine.observeAcceptedPacket(true,-80,-20); c.engine.resumeFromServer();
        assertArrayEquals(new float[]{-80,-20},c.engine.rotation(),0);
        assertTrue(c.engine.enabled());
        double gap=distance(c.engine.rotation(),target); c.frame(0,0,.5f);
        assertTrue(distance(new float[]{-80,-20},c.engine.rotation()) <= 12.0001);
        assertTrue(distance(c.engine.rotation(),target)<gap);
        c.engine.beginReturn(); c.engine.observeAcceptedPacket(true,30,10); c.engine.resumeFromServer();
        assertTrue("a foreign C03 does not revive mining/tracking",c.engine.returning());
        c.frame(0,0,.5f); assertTrue(distance(new float[]{30,10},c.engine.rotation()) <= 12.0001);
    }

    @Test public void expiryAlsoUsesTheContinuousTailAndSceneClearAloneDropsIt() {
        Clock c = new Clock();
        DemiseRotationSettings.Snapshot settings = DemiseRotationSettings.Snapshot.defaults(DemiseRotationEngine.Correction.Strict);
        c.engine.clear(70,40); c.accept(); c.engine.updateSpeeds(settings);
        c.engine.setRotation(new float[]{100,70},settings);
        c.millis+=151; c.nanos+=151_000_000L; c.engine.mousePre(); c.engine.mouseUpdate(0,0,.5f);
        assertTrue(c.engine.returning()); assertEquals(DemiseRotationEngine.Correction.Strict,c.engine.correction());
        assertTrue(distance(new float[]{70,40},c.engine.rotation()) <= 12.0001);
        c.engine.clear(-12,8);
        assertFalse(c.engine.shouldRotate()); assertFalse(c.engine.hasServerRotation());
        assertArrayEquals(new float[]{-12,8},c.engine.rotation(),0);
    }

    @Test public void silentToVisibleToggleEasesTheCameraWithoutRewindingServerRotation() {
        Clock c=new Clock();
        DemiseRotationSettings.Snapshot silent=settings(DemiseRotationEngine.Smoothing.Linear,false,true,1,180,false);
        c.engine.clear(140,70); c.accept(); c.engine.updateSpeeds(silent);
        c.engine.setRotation(new float[]{160,80},silent);
        DemiseRotationSettings.Snapshot visible=settings(DemiseRotationEngine.Smoothing.Linear,false,false,1,180,false);
        c.engine.setRotation(new float[]{160,80},visible);
        float[] serverBefore=c.engine.rotation(); c.frame(0,0,.5f);
        DemiseRotationEngine.Aim camera=c.engine.cameraAim(0,0);
        assertTrue(distance(new float[]{0,0},camera.array())<=12.0001);
        assertTrue(distance(camera.array(),c.engine.rotation())>100);
        assertTrue("a visible toggle cannot rewind server/model to the free camera",distance(serverBefore,c.engine.rotation())<=12.0001);
    }

    @Test public void independentSpeedSlidersMayCrossWithoutBreakingTheContinuousOwner() {
        long[] time={1000};
        DemiseRotationEngine engine=DemiseRotationEngine.continuous(() -> time[0],() -> time[0]*1_000_000L,
                DemiseRotationEngine::randomizeFloat);
        DemiseRotationSettings.Snapshot crossed=new DemiseRotationSettings.Snapshot(true,1,DemiseRotationEngine.Smoothing.Linear,
                false,.25f,.25f,false,180,1,150,.01f,DemiseRotationEngine.Correction.Silent,false,50,180,1);
        engine.clear(0,0); engine.observeAcceptedPacket(true,0,0);
        for(int i=0;i<50;i++) {
            engine.updateSpeeds(crossed); engine.setRotation(new float[]{130,65},crossed);
            float[] before=engine.rotation(); time[0]+=17; engine.mousePre(); engine.mouseUpdate(0,0,.5f);
            assertTrue(Float.isFinite(engine.rotation()[0])); assertTrue(Float.isFinite(engine.rotation()[1]));
            assertTrue(distance(before,engine.rotation())<=12.0001);
            engine.observeAcceptedPacket(true,engine.rotation()[0],engine.rotation()[1]);
        }
    }

    private static double distance(float[] a,float[] b) {
        return Math.hypot(MathHelper.wrapAngleTo180_float(a[0]-b[0]),a[1]-b[1]);
    }
    private static DemiseRotationSettings.Snapshot settings(DemiseRotationEngine.Smoothing mode, boolean acceleration,
            boolean silent, float smoothing, float speed, boolean shortStop) {
        return new DemiseRotationSettings.Snapshot(silent,smoothing,mode,acceleration,.25f,.25f,false,
                speed,speed,speed,speed,DemiseRotationEngine.Correction.Silent,shortStop,1000,180,1);
    }
}
