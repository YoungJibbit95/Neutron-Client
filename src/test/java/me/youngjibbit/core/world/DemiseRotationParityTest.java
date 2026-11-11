package me.youngjibbit.core.world;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.*;
import static org.junit.Assert.*;

/** Differential checks against the pinned Demise method bodies, not the production implementation. */
public class DemiseRotationParityTest {
    @Test public void referenceResourcesMatchPinnedGitBlobs() throws Exception {
        assertEquals("29555b7cfca3b1e5cccad94e4d5ed06902784a31", gitBlob("RotationHandler.java.txt"));
        assertEquals("bd1c4a35710e21784f5150f15f6730b23cab874d", gitBlob("MoveUtil.java.txt"));
    }

    @Test public void frameSequencesMatchOriginalForEverySmoothingAccelerationAndCameraMode() {
        long[] intervals = {1, 6, 8, 16, 17, 50, 3, 7, 100, 150, 151};
        for (float sensitivity : new float[]{0, .13f, .5f, .87f, 1})
            for (DemiseRotationEngine.Smoothing smoothing : DemiseRotationEngine.Smoothing.values())
                for (boolean accel : new boolean[]{false, true}) for (boolean silent : new boolean[]{false, true}) {
                    DemiseOriginalRotation.Clock clock = new DemiseOriginalRotation.Clock();
                    DemiseOriginalRotation original = new DemiseOriginalRotation(clock);
                    DemiseRotationEngine actual = new DemiseRotationEngine(() -> clock.millis, () -> clock.nanos, (a,b) -> a);
                    DemiseRotationSettings.Snapshot settings = settings(smoothing, accel, silent, false, false, 17.3f, 143.7f);
                    actual.updateSpeeds(settings);
                    float cameraYaw = 173.75f, cameraPitch = -29.2f;
                    for (int frame = 0; frame < 360; frame++) {
                        long interval = intervals[frame % intervals.length];
                        clock.millis += interval; clock.nanos += interval * 1_000_000L;
                        original.mc.thePlayer.rotationYaw = cameraYaw;
                        original.mc.thePlayer.rotationPitch = cameraPitch;
                        original.mc.gameSettings.mouseSensitivity = sensitivity;
                        original.onMouseMove(new DemiseOriginalRotation.MouseMoveEvent(DemiseOriginalRotation.MouseMoveEvent.State.PRE));
                        actual.mousePre();
                        original.onMouseMove(new DemiseOriginalRotation.MouseMoveEvent(DemiseOriginalRotation.MouseMoveEvent.State.UPDATE));
                        DemiseRotationEngine.Aim aim = actual.mouseUpdate(cameraYaw, cameraPitch, sensitivity);
                        assertFloatBits(original.currentRotation[0], aim.yaw);
                        assertFloatBits(original.currentRotation[1], aim.pitch);
                        assertEquals(original.enabled, actual.enabled());
                        assertEquals(original.shouldRotate(), actual.shouldRotate());
                        if (!silent) {
                            assertFloatBits(original.mc.thePlayer.rotationYaw, aim.yaw);
                            assertFloatBits(original.mc.thePlayer.rotationPitch, aim.pitch);
                            cameraYaw = aim.yaw; cameraPitch = aim.pitch;
                        }
                        // AngleEvent supplies the next target after the current frame integration.
                        if (frame < 80 || frame >= 200 && frame < 270) {
                            float[] target = {(float) (sin(frame * .37) * 550), (float) (cos(frame * .21) * 89)};
                            original.previousRotation = original.shouldRotate() ? original.currentRotation : new float[]{cameraYaw, cameraPitch};
                            actual.observeMotion(cameraYaw, cameraPitch);
                            original.setRotation(target, DemiseOriginalRotation.MovementCorrectionMode.Silent,
                                    new float[]{17.3f, 143.7f}, accel, new float[]{.25f, .67f},
                                    DemiseOriginalRotation.SmoothMode.valueOf(smoothing.name()), silent, .15f);
                            actual.setRotation(target, settings);
                            assertFloatBits(original.lastDelta, actual.lastDelta());
                        }
                        cameraYaw += (float) sin(frame * .07) * 1.37f;
                        cameraPitch = Math.max(-89, Math.min(89, cameraPitch + (float) cos(frame * .13) * .47f));
                    }
                }
    }

    @Test public void moveFixMatchesOriginalAtWrapsHalfSectorsAndUnequalInputStrengths() {
        DemiseOriginalMoveFix original = new DemiseOriginalMoveFix();
        float[] angles = {-540, -180, -179.999f, -157.5f, -90, -67.5f, -22.5f, 0, 22.5f, 67.5f, 90, 157.5f, 179.999f, 180, 540};
        for (float camera : angles) for (float yaw : angles)
            for (float forward : new float[]{-1, -.3f, 0, .3f, 1}) for (float strafe : new float[]{-1, -.3f, 0, .3f, 1}) {
                DemiseOriginalMoveFix.MoveInputEvent event = new DemiseOriginalMoveFix.MoveInputEvent(forward, strafe);
                original.fixMovement(event, yaw, camera);
                DemiseMoveFix.Input actual = DemiseMoveFix.correct(strafe, forward, yaw, camera);
                assertFloatBits(event.forward, actual.forward); assertFloatBits(event.strafe, actual.strafe);
            }
    }

    @Test public void expiryIsStrictlyAfter150MillisAndReturnUsesTheSameOriginalCurve() {
        DemiseOriginalRotation.Clock clock = new DemiseOriginalRotation.Clock();
        DemiseRotationEngine engine = new DemiseRotationEngine(() -> clock.millis, () -> clock.nanos, (a,b) -> a);
        DemiseRotationSettings.Snapshot settings = DemiseRotationSettings.Snapshot.defaults(DemiseRotationEngine.Correction.Silent);
        engine.updateSpeeds(settings); engine.setRotation(new float[]{90, 0}, settings);
        clock.millis += 150; clock.nanos += 150_000_000L; engine.mousePre();
        DemiseRotationEngine.Aim active = engine.mouseUpdate(0, 0, .5f);
        assertTrue(engine.enabled()); assertTrue(active.yaw > 0); assertEquals(0,active.pitch,0);
        clock.millis++; clock.nanos += 1_000_000L; engine.mousePre();
        DemiseRotationEngine.Aim returning = engine.mouseUpdate(0, 0, .5f);
        assertFalse(engine.enabled()); assertTrue(engine.shouldRotate());
        assertTrue(returning.yaw < active.yaw); assertEquals(0,returning.pitch,0);
        for (int i = 0; i < 2000 && engine.shouldRotate(); i++) {
            clock.millis += 17; clock.nanos += 17_000_000L; engine.mousePre(); engine.mouseUpdate(0, 0, .5f);
        }
        assertFalse(engine.shouldRotate()); assertArrayEquals(new float[]{0,0}, engine.rotation(), 0);
    }

    @Test public void originalDiagonalReturnDeadZoneIsRetainedUntilCameraClosesTheGap() {
        DemiseOriginalRotation.Clock clock = new DemiseOriginalRotation.Clock();
        DemiseRotationEngine engine = new DemiseRotationEngine(() -> clock.millis, () -> clock.nanos, (a,b) -> a);
        DemiseRotationSettings.Snapshot settings = DemiseRotationSettings.Snapshot.defaults(DemiseRotationEngine.Correction.Silent);
        engine.updateSpeeds(settings); engine.setRotation(new float[]{90,60},settings);
        clock.millis+=17; clock.nanos+=17_000_000L; engine.mousePre(); engine.mouseUpdate(0,0,.5f);
        float[] current=engine.rotation();
        clock.millis+=134; clock.nanos+=134_000_000L; engine.mousePre();
        engine.mouseUpdate(current[0]+.9f,current[1]+.9f,.5f);
        assertFalse(engine.enabled());
        assertTrue("Both integer counts can be zero while the original total-distance reset threshold is not reached",engine.shouldRotate());
        assertArrayEquals(current,engine.rotation(),0);
        engine.mouseUpdate(current[0],current[1],.5f); assertFalse(engine.shouldRotate());
    }

    @Test public void packetBuildupIsSignedAndAStationaryPacketResetsIt() {
        DemiseRotationEngine engine = new DemiseRotationEngine();
        DemiseRotationSettings.Snapshot settings = DemiseRotationSettings.Snapshot.defaults(DemiseRotationEngine.Correction.Silent);
        engine.setRotation(new float[]{90,0}, settings);
        engine.observeAcceptedPacket(true, 170, 0); engine.observeAcceptedPacket(true, -170, 0);
        assertEquals(20, engine.rotationDiffBuildUp(), 0);
        engine.observeAcceptedPacket(true, 170, 0); assertEquals(0, engine.rotationDiffBuildUp(), 0);
        engine.observeAcceptedPacket(true, -90, 0); assertEquals(100, engine.rotationDiffBuildUp(), 0);
        engine.observeAcceptedPacket(false, 0, 0); assertEquals(0, engine.rotationDiffBuildUp(), 0);
        assertArrayEquals(new float[]{-90,0}, engine.serverRotation(), 0);
    }

    @Test public void shortStopKeepsOriginalTimerAndThresholdAttemptQuirks() {
        DemiseOriginalRotation.Clock clock = new DemiseOriginalRotation.Clock();
        List<float[]> draws = new ArrayList<>();
        DemiseRotationEngine engine = new DemiseRotationEngine(() -> clock.millis, () -> clock.nanos,
                (a,b) -> { draws.add(new float[]{a,b}); return a; });
        DemiseRotationSettings.Snapshot settings = settings(DemiseRotationEngine.Smoothing.Linear, false, true, true, false, 180,180);
        engine.updateSpeeds(settings); draws.clear(); engine.setRotation(new float[]{90,0}, settings);
        assertEquals(2, draws.size()); assertArrayEquals(new float[]{0,.1f}, draws.get(0), 0);
        clock.millis += 51; draws.clear(); engine.setRotation(new float[]{90,0}, settings); assertTrue(draws.isEmpty());
        engine.observeAcceptedPacket(true, 0, 0); engine.observeAcceptedPacket(true, 180, 0);
        engine.setRotation(new float[]{90,0}, settings); assertTrue(draws.isEmpty());
        engine.setRotation(new float[]{90,0}, settings); assertEquals(2, draws.size());
        clock.millis += 51; draws.clear(); engine.setRotation(new float[]{90,0}, settings);
        assertEquals("The original threshold-attempt counter is not reset by a stop", 2, draws.size());
    }

    @Test public void forcedClutchSpeedsStillPassThroughOriginalCorrelationAndClamp() {
        DemiseOriginalRotation.Clock clock = new DemiseOriginalRotation.Clock();
        List<float[]> draws = new ArrayList<>();
        DemiseRotationEngine engine = new DemiseRotationEngine(() -> clock.millis, () -> clock.nanos,
                (a,b) -> { draws.add(new float[]{a,b}); return 1.8f; });
        DemiseRotationSettings.Snapshot settings = settings(DemiseRotationEngine.Smoothing.Linear, false, true, false, true, 1,1);
        engine.forceSpeeds(180,180); engine.setRotation(new float[]{90,60}, settings);
        assertEquals(2, draws.size()); assertArrayEquals(new float[]{.9f,1.1f}, draws.get(0), 0);
        DemiseOriginalRotation original = new DemiseOriginalRotation(clock);
        original.setRotation(new float[]{90,60}, DemiseOriginalRotation.MovementCorrectionMode.Silent,
                new float[]{180,180}, false, new float[]{.25f,.67f}, DemiseOriginalRotation.SmoothMode.Linear, true, .15f);
        clock.millis += 17; clock.nanos += 17_000_000L; original.mc.gameSettings.mouseSensitivity = .5f;
        original.onMouseMove(new DemiseOriginalRotation.MouseMoveEvent(DemiseOriginalRotation.MouseMoveEvent.State.PRE));
        original.onMouseMove(new DemiseOriginalRotation.MouseMoveEvent(DemiseOriginalRotation.MouseMoveEvent.State.UPDATE));
        engine.mousePre(); DemiseRotationEngine.Aim actual = engine.mouseUpdate(0,0,.5f);
        assertFloatBits(original.currentRotation[0],actual.yaw); assertFloatBits(original.currentRotation[1],actual.pitch);
    }

    private static DemiseRotationSettings.Snapshot settings(DemiseRotationEngine.Smoothing mode, boolean accel,
            boolean silent, boolean shortStop, boolean imperfect, float yaw, float pitch) {
        return new DemiseRotationSettings.Snapshot(silent,.15f,mode,accel,.25f,.67f,imperfect,yaw,yaw,pitch,pitch,
                DemiseRotationEngine.Correction.Silent,shortStop,50,180,1);
    }
    private static void assertFloatBits(float expected, float actual) {
        assertEquals("float bits for " + expected + " / " + actual, Float.floatToIntBits(expected), Float.floatToIntBits(actual));
    }
    private static String gitBlob(String name) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = DemiseRotationParityTest.class.getResourceAsStream("/demise-reference/" + name)) {
            assertNotNull(input); byte[] block = new byte[4096]; int read;
            while ((read = input.read(block)) != -1) bytes.write(block,0,read);
        }
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        sha.update(("blob " + bytes.size() + "\0").getBytes(StandardCharsets.US_ASCII));
        byte[] digest = sha.digest(bytes.toByteArray()); StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }
}
