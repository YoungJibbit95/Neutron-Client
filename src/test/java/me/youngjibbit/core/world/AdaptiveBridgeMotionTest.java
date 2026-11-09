package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.AdaptiveMotionController;
import org.junit.Test;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Pure Java tests: no Minecraft, Netty, render context or manager authority. */
public class AdaptiveBridgeMotionTest {
    private static final long STEP = 50_000_000L;
    private static AdaptiveBridgeMotion.Settings settings(AdaptiveBridgeMotion.Mode mode, double variation) {
        return new AdaptiveBridgeMotion.Settings(mode, AdaptiveBridgeMotion.Speed.BALANCED, variation, 35, 30);
    }
    private static AdaptiveBridgePlanner.Scope scope() {
        return new AdaptiveBridgePlanner.Scope(new Object(), new Object(), new Object(), new Object(), 0, 1);
    }
    private static AdaptiveBridgePlanner.Scope newer(AdaptiveBridgePlanner.Scope old, Object connection,
                                                      long epoch, long generation) {
        return new AdaptiveBridgePlanner.Scope(old.owner, old.world, old.player, connection, epoch, generation);
    }
    private static AdaptiveBridgeMotion.Point point(AdaptiveBridgeMotion c, AdaptiveBridgePlanner.Scope s,
            Object key, AdaptiveBridgeMotion.Settings p, long sequence, long nanos) {
        return c.point(s, key, p, sequence, nanos, AdaptiveBridgePlanner.Face.SOUTH,
                0, 63, -1, 1, 64, 0, .5, 63.5, 0, true);
    }
    private static AdaptiveMotionController.Result advance(AdaptiveBridgeMotion c, AdaptiveBridgePlanner.Scope s,
            Object key, AdaptiveBridgeMotion.Settings p, long sequence, long nanos,
            double yaw, double pitch, double targetYaw, double targetPitch) {
        return c.advance(s, key, p, sequence, nanos, yaw, pitch, targetYaw, targetPitch, true);
    }
    private static double wrap(double v) { v %= 360; if (v > 180) v -= 360; if (v <= -180) v += 360; return v; }

    @Test public void profilesAndSettingsHaveOnlyTheSpecifiedChoicesAndFiniteLimits() {
        assertArrayEquals(new AdaptiveBridgeMotion.Mode[]{AdaptiveBridgeMotion.Mode.ADAPTIVE,
                AdaptiveBridgeMotion.Mode.GODBRIDGE}, AdaptiveBridgeMotion.Mode.values());
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values()) {
            double last = 0;
            for (AdaptiveBridgeMotion.Speed speed : AdaptiveBridgeMotion.Speed.values()) {
                AdaptiveBridgeMotion.Settings p = new AdaptiveBridgeMotion.Settings(mode, speed, 1, 35, 30);
                assertTrue(p.maxYawSpeed > last); last = p.maxYawSpeed;
                assertTrue(p.maxPitchSpeed > 0 && p.maxAcceleration > 0 && p.maxJerk > 0);
                assertTrue(p.same(new AdaptiveBridgeMotion.Settings(mode, speed, 1, 35, 30)));
            }
        }
        for (double invalid : new double[]{-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, invalid); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        for (double invalid : new double[]{0, -1, 91, Double.NaN}) {
            try { new AdaptiveBridgeMotion.Settings(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                    AdaptiveBridgeMotion.Speed.CALM, .25, invalid, 30); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void allFacesKeepTheirPlaneAndBoundTheTotalTangentialDisplacement() {
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values()) {
            AdaptiveBridgeMotion.Settings p = settings(mode, 1);
            double radius = mode == AdaptiveBridgeMotion.Mode.ADAPTIVE ? .08 : .035;
            for (AdaptiveBridgePlanner.Face face : AdaptiveBridgePlanner.Face.values()) {
                AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(83);
                AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
                double x = .5 + face.x * .5, y = 63.5 + face.y * .5, z = -.5 + face.z * .5;
                boolean varied = false;
                for (int tick = 0; tick <= 400; tick++) {
                    AdaptiveBridgeMotion.Point q = c.point(s, key, p, tick, tick * STEP, face,
                            0, 63, -1, 1, 64, 0, x, y, z, true);
                    assertNotNull(q);
                    assertTrue(Math.sqrt((q.x-x)*(q.x-x)+(q.y-y)*(q.y-y)+(q.z-z)*(q.z-z)) <= radius + 1e-12);
                    if (face.x != 0) assertEquals(x, q.x, 0);
                    if (face.y != 0) assertEquals(y, q.y, 0);
                    if (face.z != 0) assertEquals(z, q.z, 0);
                    if (tick == 0) { assertEquals(x, q.x, 0); assertEquals(y, q.y, 0); assertEquals(z, q.z, 0); }
                    varied |= Math.abs(q.x-x)+Math.abs(q.y-y)+Math.abs(q.z-z) > 1e-4;
                }
                assertTrue("time-correlated variation must be observable", varied);
            }
        }
    }

    @Test public void zeroVariationAndValidatedEdgeAnchorsNeverJumpInward() {
        AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(44);
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, 0);
        for (int tick = 0; tick < 100; tick++) {
            AdaptiveBridgeMotion.Point q = point(c, s, key, p, tick, tick * STEP);
            assertEquals(.5, q.x, 0); assertEquals(63.5, q.y, 0); assertEquals(0, q.z, 0);
        }
        c = new AdaptiveBridgeMotion(44); p = settings(AdaptiveBridgeMotion.Mode.GODBRIDGE, 1);
        for (int tick = 0; tick < 100; tick++) {
            AdaptiveBridgeMotion.Point q = c.point(s, key, p, tick, tick * STEP, AdaptiveBridgePlanner.Face.SOUTH,
                    0, 63, -1, 1, 64, 0, .001, 63.999, 0, true);
            assertEquals(.001, q.x, 0); assertEquals(63.999, q.y, 0); assertEquals(0, q.z, 0);
        }
    }

    @Test public void renderDuplicatesCannotChangeNoisePoseOrTheNextMotionsTime() {
        for (int fps : new int[]{20, 60, 144, 240}) {
            AdaptiveBridgeMotion rendered = new AdaptiveBridgeMotion(451), reference = new AdaptiveBridgeMotion(451);
            AdaptiveBridgePlanner.Scope a = scope(), b = scope(); Object keyA = new Object(), keyB = new Object();
            AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .6);
            double yaw = -35, pitch = -10;
            for (int motion = 0; motion < 100; motion++) {
                long now = motion * STEP;
                AdaptiveBridgeMotion.Point q = point(rendered, a, keyA, p, motion, now);
                AdaptiveBridgeMotion.Point expectedPoint = point(reference, b, keyB, p, motion, now);
                AdaptiveMotionController.Result r = advance(rendered, a, keyA, p, motion, now, yaw, pitch, 100, 50);
                AdaptiveMotionController.Result expected = advance(reference, b, keyB, p, motion, now, yaw, pitch, 100, 50);
                assertEquals(expectedPoint.x, q.x, 0); assertEquals(expectedPoint.y, q.y, 0);
                assertEquals(expected.yaw, r.yaw, 0); assertEquals(expected.pitch, r.pitch, 0);
                for (int frame = 0; frame < fps / 20 + 2; frame++) {
                    assertSame(q, point(rendered, a, keyA, p, motion, now + frame * 1_000_000_000L));
                    assertSame(r, advance(rendered, a, keyA, p, motion, Long.MIN_VALUE, yaw, pitch, -150, -70));
                }
                yaw = r.yaw; pitch = r.pitch;
            }
        }
    }

    @Test public void purePointSamplingCannotSpendAFirstRotationStep() {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(11);
        AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25);
        for (int tick = 0; tick < 20; tick++) assertNotNull(point(c, s, key, p, tick, tick * STEP));
        AdaptiveMotionController.Result first = advance(c, s, key, p, 20, 20 * STEP, 0, 0, 90, 70);
        assertNotNull(first); assertFalse(first.candidate); assertEquals(0, first.yaw, 0);
        assertTrue(advance(c, s, key, p, 21, 21 * STEP, 0, 0, 90, 70).candidate);
    }

    @Test public void lowerEpochOnANewConnectionNeedsANewerHostGenerationAndThenWorks() {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(59);
        AdaptiveBridgePlanner.Scope old = scope(); old = newer(old, old.connection, 40, 4);
        Object key = new Object(); AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25);
        advance(c, old, key, p, 8, 0, 0, 0, 100, 50);
        assertTrue(advance(c, old, key, p, 9, STEP, 0, 0, 100, 50).candidate);
        AdaptiveBridgePlanner.Scope next = newer(old, new Object(), 0, 5);
        assertFalse(advance(c, next, key, p, 0, STEP, 0, 0, 100, 50).candidate);
        assertFalse(c.reset(old));
        assertNull(c.advance(old, key, p, 999, 1_000 * STEP, 0, 0, 100, 50, false));
        assertTrue(advance(c, next, key, p, 1, 2 * STEP, 0, 0, 100, 50).candidate);
        AdaptiveBridgePlanner.Scope foreign = new AdaptiveBridgePlanner.Scope(new Object(), next.world,
                next.player, next.connection, 100, 100);
        assertNull(advance(c, foreign, key, p, 50, 50 * STEP, 0, 0, 100, 50));
        assertTrue(advance(c, next, key, p, 2, 3 * STEP, 0, 0, 100, 50).candidate);
        AdaptiveBridgePlanner.Scope advancedEpoch = newer(next, next.connection, 5, 6);
        assertFalse(advance(c, advancedEpoch, key, p, 3, 4 * STEP, 0, 0, 100, 50).candidate);
        AdaptiveBridgePlanner.Scope backwards = newer(advancedEpoch, next.connection, 4, 7);
        assertNull(advance(c, backwards, key, p, 4, 5 * STEP, 0, 0, 100, 50));
        assertTrue(advance(c, advancedEpoch, key, p, 4, 5 * STEP, 0, 0, 100, 50).candidate);
    }

    @Test public void rejectedMotionAndExplicitLifecycleResetHaveDifferentReacquisitionBoundaries() {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(7);
        AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25);
        advance(c, s, key, p, 0, 0, 0, 0, 90, 50); c.rejectMotion(0);
        assertNull(advance(c, s, key, p, 0, STEP, 0, 0, 90, 50));
        assertFalse(advance(c, s, key, p, 1, STEP, 0, 0, 90, 50).candidate);
        assertTrue(c.reset(s));
        assertNull(advance(c, s, key, p, 2, 2 * STEP, 0, 0, 90, 50));
        AdaptiveBridgePlanner.Scope next = newer(s, s.connection, 0, 2);
        assertFalse(advance(c, next, key, p, 2, 2 * STEP, 0, 0, 90, 50).candidate);
        assertFalse(c.reset(s)); c.rejectMotion(1);
        assertTrue(advance(c, next, key, p, 3, 3 * STEP, 0, 0, 90, 50).candidate);
    }

    @Test public void duplicateTargetOrSettingsChangesCannotBuyAnotherDynamicsBudget() {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(1);
        AdaptiveBridgePlanner.Scope s = scope(); Object first = new Object(), second = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25);
        advance(c, s, first, p, 0, 0, 0, 0, 90, 40);
        assertNull(advance(c, s, second, p, 0, STEP, 0, 0, 90, 40));
        assertNull(advance(c, s, first, p, 0, STEP, 0, 0, 90, 40));
        assertFalse(advance(c, s, second, p, 1, STEP, 0, 0, 90, 40).candidate);
        AdaptiveBridgeMotion.Settings changed = settings(AdaptiveBridgeMotion.Mode.GODBRIDGE, .25);
        assertNull(advance(c, s, second, changed, 1, 2 * STEP, 0, 0, 90, 40));
        assertFalse(advance(c, s, second, changed, 2, 2 * STEP, 0, 0, 90, 40).candidate);
        assertTrue(advance(c, s, second, settings(AdaptiveBridgeMotion.Mode.GODBRIDGE, .25),
                3, 3 * STEP, 0, 0, 90, 40).candidate);
    }

    @Test public void changedHeldGeometryRetiresTheHostAcquisitionIncludingDuplicates() {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(1);
        AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, 1);
        assertNotNull(point(c, s, key, p, 0, 0));
        assertNull(c.point(s, key, p, 0, 0, AdaptiveBridgePlanner.Face.SOUTH,
                0, 63, -1, 1, 64, 0, .51, 63.5, 0, true));
        assertNull(point(c, s, key, p, 1, STEP));
        AdaptiveBridgePlanner.Scope next = newer(s, s.connection, 0, 2);
        assertNotNull(point(c, next, key, p, 1, STEP));
        assertNull(c.point(s, key, p, 99, 99 * STEP, AdaptiveBridgePlanner.Face.SOUTH,
                0, 63, -1, Double.NaN, 64, 0, .5, 63.5, 0, true));
        assertNotNull(point(c, next, key, p, 2, 2 * STEP));
    }

    @Test public void staleScopedRejectionCannotEraseANewerAcquisitionInTheSameMotion() {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(42);
        AdaptiveBridgePlanner.Scope old = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25);
        advance(c, old, key, p, 9, 0, 0, 0, 90, 50);
        AdaptiveBridgePlanner.Scope next = newer(old, old.connection, old.connectionEpoch, 2);
        Object nextKey = new Object();
        AdaptiveMotionController.Result replacement = advance(c, next, nextKey, p, 9, 0, 0, 0, 90, 50);
        c.rejectMotion(old, key, 9);
        c.rejectMotion(next, key, 9);
        assertSame(replacement, advance(c, next, nextKey, p, 9, STEP, 0, 0, 90, 50));
        assertTrue(advance(c, next, nextKey, p, 10, STEP, 0, 0, 90, 50).candidate);
    }

    @Test public void longPauseAndClockRollbackCannotAccumulateMotionOrReviveTheSameFrame() {
        for (boolean rollback : new boolean[]{false, true}) {
            AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(6);
            AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
            AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25);
            long origin = -1_000_000_000L;
            advance(c, s, key, p, 0, origin, 0, 0, 100, 50);
            assertTrue(advance(c, s, key, p, 1, origin + STEP, 0, 0, 100, 50).candidate);
            long invalid = rollback ? origin + STEP - 1 : origin + STEP + 250_000_000L;
            assertNull(advance(c, s, key, p, 2, invalid, 0, 0, 100, 50));
            assertNull(advance(c, s, key, p, 2, origin + 9 * STEP, 0, 0, 100, 50));
            assertFalse(advance(c, s, key, p, 3, origin + 10 * STEP, 0, 0, 100, 50).candidate);
            assertTrue(advance(c, s, key, p, 4, origin + 11 * STEP, 0, 0, 100, 50).candidate);
        }
    }

    @Test public void staleInvalidCallsCannotDestroyCurrentMotionAndForeignThreadsCannotRebind() throws Exception {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(26);
        AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25);
        advance(c, s, key, p, 3, 0, 0, 0, 90, 30);
        assertTrue(advance(c, s, key, p, 4, STEP, 0, 0, 90, 30).candidate);
        assertNull(c.advance(s, key, p, 3, 0, Double.NaN, 0, 90, 30, false));
        AtomicReference<Object> result = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            result.set(advance(c, newer(s, new Object(), 0, 99), key, p, 99, 99 * STEP, 0, 0, 90, 30));
            c.reset(); c.reset(s); c.rejectMotion(4);
        });
        foreign.start(); foreign.join(); assertNull(result.get());
        assertTrue(advance(c, s, key, p, 5, 2 * STEP, 0, 0, 90, 30).candidate);
    }

    @Test public void reusedDynamicsConvergeWithinSpeedAccelerationAndJerkBudgets() {
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values())
            for (AdaptiveBridgeMotion.Speed speed : AdaptiveBridgeMotion.Speed.values()) {
                AdaptiveBridgeMotion.Settings p = new AdaptiveBridgeMotion.Settings(mode, speed, .25, 35, 30);
                AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(49);
                AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
                double yaw = 179, pitch = -50, priorYawA = 0, priorPitchA = 0;
                for (int tick = 0; tick < 200; tick++) {
                    AdaptiveMotionController.Result r = advance(c, s, key, p, tick, tick * STEP, yaw, pitch, -120, 70);
                    assertNotNull(r);
                    assertTrue(Math.abs(wrap(r.yaw-yaw)) <= p.maxYawSpeed * .05 + 1e-9);
                    assertTrue(Math.abs(r.pitch-pitch) <= p.maxPitchSpeed * .05 + 1e-9);
                    assertTrue(Math.abs(r.yawAcceleration) <= p.maxAcceleration + 1e-9);
                    assertTrue(Math.abs(r.pitchAcceleration) <= p.maxAcceleration + 1e-9);
                    assertTrue(Math.abs(r.yawAcceleration-priorYawA) <= p.maxJerk * .05 + 1e-8);
                    assertTrue(Math.abs(r.pitchAcceleration-priorPitchA) <= p.maxJerk * .05 + 1e-8);
                    priorYawA = r.yawAcceleration; priorPitchA = r.pitchAcceleration;
                    yaw = r.yaw; pitch = r.pitch;
                }
                assertEquals(0, wrap(-120-yaw), 1e-6); assertEquals(70, pitch, 1e-6);
            }
    }

    @Test public void resetKeepsOnlyWeakScopeReferences() throws Exception {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(7);
        AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
        point(c, s, key, settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, .25), 0, 0);
        assertTrue(c.reset(s));
        Set<Object> forbidden = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(forbidden, s, s.owner, s.world, s.player, s.connection, key);
        checkReachable(c, forbidden, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static void checkReachable(Object value, Set<Object> forbidden, Set<Object> seen) throws Exception {
        if (value == null || !seen.add(value) || value instanceof Reference) return;
        assertFalse("reset retained scene or target", forbidden.contains(value));
        if (!value.getClass().getName().startsWith(AdaptiveBridgeMotion.class.getName())) return;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            field.setAccessible(true); checkReachable(field.get(value), forbidden, seen);
        }
    }

    /** Presets split initial/keeping caps; the flat W08 constructor stays uniform. */
    @Test public void splitPresetsStrengthenOnlyTheInitialPhaseAndStayMonotone() {
        AdaptiveBridgeMotion.Speed[] presets = {AdaptiveBridgeMotion.Speed.CALM,
                AdaptiveBridgeMotion.Speed.BALANCED, AdaptiveBridgeMotion.Speed.SWIFT};
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values()) {
            double scale = mode == AdaptiveBridgeMotion.Mode.GODBRIDGE ? .8 : 1;
            double lastInitial = 0, lastKeeping = 0;
            for (AdaptiveBridgeMotion.Speed speed : presets) {
                AdaptiveBridgeMotion.Settings p = new AdaptiveBridgeMotion.Settings(mode,
                        speed, 1, 35, 30, 35, 30);
                assertEquals(35 * 20 * speedToInitial(speed) * scale, p.maxYawSpeed, 1e-9);
                assertEquals(35 * 20 * speedToKeep(speed) * scale, p.keepingYawSpeed, 1e-9);
                assertEquals(30 * 20 * speedToKeep(speed) * scale, p.keepingPitchSpeed, 1e-9);
                assertTrue(p.keepingYawSpeed <= p.maxYawSpeed);
                assertTrue(p.keepingPitchSpeed <= p.maxPitchSpeed);
                assertTrue("finite physical budget", p.maxAcceleration > 0 && p.maxJerk > 0);
                // The settled W08 pace is unchanged: keeping caps equal the flat preset caps.
                AdaptiveBridgeMotion.Settings flat = new AdaptiveBridgeMotion.Settings(mode,
                        speed, 1, 35, 30);
                assertEquals(flat.maxYawSpeed, p.keepingYawSpeed, 0);
                assertEquals(flat.maxPitchSpeed, p.keepingPitchSpeed, 0);
                lastInitial = p.maxYawSpeed; lastKeeping = p.keepingYawSpeed;
            }
            // Monotone preset ladder on both phases.
            for (int i = 1; i < presets.length; i++) {
                AdaptiveBridgeMotion.Settings a = new AdaptiveBridgeMotion.Settings(mode,
                        presets[i - 1], 1, 35, 30, 35, 30);
                AdaptiveBridgeMotion.Settings b = new AdaptiveBridgeMotion.Settings(mode,
                        presets[i], 1, 35, 30, 35, 30);
                assertTrue(b.maxYawSpeed > a.maxYawSpeed);
                assertTrue(b.keepingYawSpeed > a.keepingYawSpeed);
            }
        }
        // Raw custom sliders bypass preset multipliers entirely (only leaving the W08 clamp bounds).
        AdaptiveBridgeMotion.Settings custom = new AdaptiveBridgeMotion.Settings(
                AdaptiveBridgeMotion.Mode.ADAPTIVE, AdaptiveBridgeMotion.Speed.CUSTOM, 1, 12, 34, 28, 30);
        assertEquals(240, custom.maxYawSpeed, 0);
        assertEquals(680, custom.maxPitchSpeed, 0);
        assertEquals(560, custom.keepingYawSpeed, 0);
        assertEquals(600, custom.keepingPitchSpeed, 0);
        assertTrue(custom.same(new AdaptiveBridgeMotion.Settings(
                AdaptiveBridgeMotion.Mode.ADAPTIVE, AdaptiveBridgeMotion.Speed.CUSTOM, 1, 12, 34, 28, 30)));
        assertFalse(custom.same(new AdaptiveBridgeMotion.Settings(
                AdaptiveBridgeMotion.Mode.ADAPTIVE, AdaptiveBridgeMotion.Speed.CUSTOM, 1, 12, 34, 28, 29)));
        assertFalse(custom.same(new AdaptiveBridgeMotion.Settings(
                AdaptiveBridgeMotion.Mode.ADAPTIVE, AdaptiveBridgeMotion.Speed.SWIFT, 1, 12, 34, 28, 30)));
        // Sliders above the shared 720 degrees/second bound saturate exactly like the W08 caps.
        AdaptiveBridgeMotion.Settings saturated = new AdaptiveBridgeMotion.Settings(
                AdaptiveBridgeMotion.Mode.ADAPTIVE, AdaptiveBridgeMotion.Speed.CUSTOM, 1, 90, 90, 90, 90);
        assertEquals(720, saturated.maxYawSpeed, 0);
        assertEquals(720, saturated.keepingPitchSpeed, 0);
    }

    private static double speedToInitial(AdaptiveBridgeMotion.Speed speed) {
        switch (speed) {
            case CALM: return .5;
            case BALANCED: return .75;
            case SWIFT: case CUSTOM: return 1;
            default: throw new AssertionError();
        }
    }
    private static double speedToKeep(AdaptiveBridgeMotion.Speed speed) {
        switch (speed) {
            case CALM: return .38;
            case BALANCED: return .55;
            case SWIFT: case CUSTOM: return .8;
            default: throw new AssertionError();
        }
    }

    /** The keeping caps must exist as real motion caps on the shared engine, not only as numbers. */
    @Test public void keepingPhaseUsesTheLowerCapsOnRealMotions() {
        AdaptiveBridgeMotion c = new AdaptiveBridgeMotion(53);
        AdaptiveBridgePlanner.Scope s = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = new AdaptiveBridgeMotion.Settings(
                AdaptiveBridgeMotion.Mode.ADAPTIVE, AdaptiveBridgeMotion.Speed.SWIFT, 1, 35, 30, 35, 30);
        double yaw = 0, pitch = 0, progress = 0;
        boolean sawCalmKeeping = false;
        double initialDistance = Math.hypot(150, 75);
        for (int tick = 0; tick < 260; tick++) {
            AdaptiveMotionController.Result r = advance(c, s, key, p, tick, tick * STEP, yaw, pitch, 150, 75);
            assertNotNull(r);
            if (progress >= AdaptiveBridgeMotion.Settings.KEEPING_PROGRESS && r.candidate) {
                assertTrue("bridge keep yaw cap: yawVelocity=" + r.yawVelocity + " tick=" + tick,
                        Math.abs(r.yawVelocity) <= p.keepingYawSpeed + 1e-6);
                assertTrue("bridge keep pitch cap: pitchVelocity=" + r.pitchVelocity + " tick=" + tick,
                        Math.abs(r.pitchVelocity) <= p.keepingPitchSpeed + 1e-6);
                sawCalmKeeping = true;
            }
            yaw = r.yaw; pitch = r.pitch;
            progress = 1 - Math.hypot(wrap(150 - yaw), 75 - pitch) / initialDistance;
        }
        assertTrue(sawCalmKeeping);
        assertEquals(0, wrap(150 - yaw), 1e-6);
        assertEquals(75, pitch, 1e-6);
    }

    @Test public void splitSettingsRejectInvalidKeepingTicks() {
        for (double invalid : new double[]{0, -1, 91, Double.NaN}) {
            try { new AdaptiveBridgeMotion.Settings(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                    AdaptiveBridgeMotion.Speed.SWIFT, .25, 35, 30, invalid, 30); fail(); }
            catch (IllegalArgumentException expected) { }
            try { new AdaptiveBridgeMotion.Settings(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                    AdaptiveBridgeMotion.Speed.SWIFT, .25, 35, 30, 35, invalid); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
