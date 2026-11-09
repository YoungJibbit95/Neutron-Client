package me.youngjibbit.core.movement;

import org.junit.Test;

import java.util.Locale;

import static me.youngjibbit.core.movement.IntaveMomentumProfile.*;
import static org.junit.Assert.*;

/** Deterministic numeric tests only: no Minecraft, native physics, packets or server. */
public class IntaveMomentumProfileTest {
    private static final double EPS = 1.0E-7;

    private static IntaveMomentumProfile profile() {
        IntaveMomentumProfile profile = new IntaveMomentumProfile();
        assertTrue(profile.reset(1));
        return profile;
    }

    private static Input frame(long generation, long tick, boolean ground, boolean wall,
                               float strafe, float forward, float yaw, float friction,
                               double velocityX, double velocityZ) {
        return new Input(generation, tick, true, true, ground, wall,
                strafe, forward, yaw, friction, velocityX, velocityZ);
    }

    private static Input ground(long tick) {
        return frame(1, tick, true, false, 0, .98F, 0, .13F, 0, 0);
    }

    private static Input gates(Input source, boolean ordinary, boolean eligible) {
        return new Input(source.generation, source.outerTickId, ordinary, eligible,
                source.grounded, source.wall, source.strafe, source.forward,
                source.yaw, source.friction, source.velocityX, source.velocityZ);
    }

    private static void warm(IntaveMomentumProfile profile) {
        for (int tick = 0; tick < 3; tick++) assertTrue(profile.step(ground(tick)).hasAdjustment());
    }

    @Test public void requiresAnExplicitGenerationAndDoesNotAdoptAnInputScope() {
        IntaveMomentumProfile profile = new IntaveMomentumProfile();
        assertFalse(profile.step(ground(0)).hasAdjustment());
        assertFalse(profile.reset(-1));
        assertTrue(profile.reset(1));
        Input foreign = frame(99, 100, true, false, 0, 1, 0, .13F, 0, 0);
        assertEquals(Reason.INACTIVE_GENERATION, profile.step(foreign).reason);
        assertEquals(.004, profile.step(ground(0)).extraFriction, EPS);
        assertFalse(profile.reset(1));
        assertEquals(.008, profile.step(ground(1)).extraFriction, EPS);
    }

    @Test public void groundRampIsSmallBoundedAndReachesItsLimitInThreeActualSteps() {
        IntaveMomentumProfile profile = profile();
        float before = 0;
        for (int tick = 0; tick < 20; tick++) {
            Result result = profile.step(ground(tick));
            assertTrue(result.hasAdjustment());
            assertEquals(Phase.GROUND, result.phase);
            assertEquals(Math.min(.012, .004 * (tick + 1)), result.extraFriction, EPS);
            assertTrue(result.extraFriction <= GROUND_EXTRA);
            assertTrue(result.extraFriction - before <= GROUND_RISE);
            assertEquals(.13F + result.extraFriction, result.friction, 0);
            before = result.extraFriction;
        }
    }

    @Test public void duplicatesOldSamplesAndExtraBodiesDoNotAdvanceOrResetTheRamp() {
        IntaveMomentumProfile profile = profile();
        assertEquals(.004, profile.step(ground(10)).extraFriction, EPS);
        assertEquals(Reason.DUPLICATE_OR_OLD, profile.step(ground(10)).reason);
        assertEquals(Reason.DUPLICATE_OR_OLD,
                profile.step(gates(ground(9), true, false)).reason);
        assertEquals(Reason.NOT_ORDINARY, profile.step(gates(ground(11), false, true)).reason);
        assertEquals(.008, profile.step(ground(11)).extraFriction, EPS);
        assertEquals(.012, profile.step(ground(12)).extraFriction, EPS);
    }

    @Test public void resetCannotReopenTheConsumedTickOrAcceptAnOldGeneration() {
        IntaveMomentumProfile profile = profile();
        warm(profile);
        assertTrue(profile.reset(2));
        assertFalse(profile.reset(1));
        assertEquals(Reason.INACTIVE_GENERATION, profile.step(ground(100)).reason);
        Input sameTick = frame(2, 2, true, false, 0, .98F, 0, .13F, 0, 0);
        assertEquals(Reason.DUPLICATE_OR_OLD, profile.step(sameTick).reason);
        assertEquals(.004, profile.step(frame(2, 3, true, false, 0, .98F, 0, .13F, 0, 0)).extraFriction, EPS);
        assertEquals(Reason.INACTIVE_GENERATION, profile.step(gates(ground(999), true, false)).reason);
        assertEquals(.008, profile.step(frame(2, 4, true, false, 0, .98F, 0, .13F, 0, 0)).extraFriction, EPS);
    }

    @Test public void pauseIsLatchedUntilAHostResetAndHasNoInventedTimeout() {
        IntaveMomentumProfile profile = profile();
        warm(profile);
        assertTrue(profile.pause(2));
        for (long tick : new long[]{3, 4, 100, 1_000_000}) {
            Result paused = profile.step(frame(2, tick, true, false, 0, .98F, 0, .13F, 0, 0));
            assertEquals(Reason.PAUSED, paused.reason);
            assertFalse(paused.hasAdjustment());
        }
        assertFalse(profile.reset(2));
        assertFalse(profile.pause(1));
        assertTrue(profile.reset(3));
        assertFalse(profile.step(frame(3, 1_000_000, true, false, 0, 1, 0, .13F, 0, 0)).hasAdjustment());
        assertEquals(.004, profile.step(frame(3, 1_000_001, true, false, 0, 1, 0, .13F, 0, 0)).extraFriction, EPS);
    }

    @Test public void invalidEligibilityConsumesItsOpportunityAndRestartsCold() {
        IntaveMomentumProfile profile = profile();
        warm(profile);
        assertEquals(Reason.INELIGIBLE, profile.step(gates(ground(3), true, false)).reason);
        assertEquals(Reason.DUPLICATE_OR_OLD, profile.step(ground(3)).reason);
        assertEquals(.004, profile.step(ground(4)).extraFriction, EPS);
    }

    @Test public void missingStepsDoNotAccumulateRampOrAirCredit() {
        IntaveMomentumProfile profile = profile();
        warm(profile);
        assertEquals(.004, profile.step(ground(1_000)).extraFriction, EPS);
        Input air = frame(1, 2_000, false, false, 0, 1, 0, .02F, 0, 0);
        assertEquals(Reason.AIR_EXHAUSTED, profile.step(air).reason);
        assertEquals(.004, profile.step(ground(2_001)).extraFriction, EPS);
    }

    @Test public void equivalentWorldCoursesAcrossMoveFixBasesKeepTheirRamp() {
        IntaveMomentumProfile profile = profile();
        assertEquals(.004, profile.step(ground(0)).extraFriction, EPS);
        assertEquals(.008, profile.step(frame(1, 1, true, false, .98F, 0, 90, .13F, 0, 0)).extraFriction, EPS);
        assertEquals(.012, profile.step(frame(1, 2, true, false, 0, -.98F, 180, .13F, 0, 0)).extraFriction, EPS);
        assertEquals(.012, profile.step(frame(1, 3, true, false, -.98F, 0, -90, .13F, 0, 0)).extraFriction, EPS);
    }

    @Test public void sharpTurnsDropOldBoostAndRampAgainOnTheNewCourse() {
        for (float yaw : new float[]{90, 180, -90}) {
            IntaveMomentumProfile profile = profile();
            warm(profile);
            Result turn = profile.step(frame(1, 3, true, false, 0, .98F, yaw, .13F, 0, 0));
            assertEquals(Phase.TURNING, turn.phase);
            assertEquals(Reason.DIRECTION_CHANGE, turn.reason);
            assertFalse(turn.hasAdjustment());
            assertEquals(.004, profile.step(frame(1, 4, true, false, 0, .98F, yaw, .13F, 0, 0)).extraFriction, EPS);
            assertEquals(.008, profile.step(frame(1, 5, true, false, 0, .98F, yaw, .13F, 0, 0)).extraFriction, EPS);
        }
    }

    @Test public void diagonalChangesAndYawWrapRemainCoherent() {
        IntaveMomentumProfile profile = profile();
        assertEquals(.004, profile.step(frame(1, 0, true, false, 0, 1, 359, .13F, 0, 0)).extraFriction, EPS);
        assertEquals(.008, profile.step(frame(1, 1, true, false, 0, 1, 1, .13F, 0, 0)).extraFriction, EPS);
        assertEquals(.012, profile.step(frame(1, 2, true, false, 1, 1, 1, .13F, 0, 0)).extraFriction, EPS);
    }

    @Test public void airIsReducedDecaysAndCannotStartWithoutAnEligibleGroundSample() {
        IntaveMomentumProfile cold = profile();
        assertEquals(Reason.AIR_EXHAUSTED,
                cold.step(frame(1, 0, false, false, 0, 1, 0, .02F, 0, 0)).reason);
        IntaveMomentumProfile profile = profile();
        warm(profile);
        double[] expected = {.002, .0015, .001, .0005, 0, 0};
        for (int step = 0; step < expected.length; step++) {
            Input air = frame(1, step + 3, false, false, 0, 1, 0, .02F, 0, 0);
            Result result = profile.step(air);
            assertEquals(Phase.AIR, result.phase);
            assertEquals(expected[step], result.extraFriction, EPS);
            assertFalse("duplicate cannot use a second air credit", profile.step(air).hasAdjustment());
        }
    }

    @Test public void landingStartsWithOneGroundIncrementInsteadOfRestoringOldFullBoost() {
        IntaveMomentumProfile profile = profile();
        warm(profile);
        assertTrue(profile.step(frame(1, 3, false, false, 0, 1, 0, .02F, 0, 0)).hasAdjustment());
        Result landing = profile.step(ground(4));
        assertEquals(Phase.LANDING, landing.phase);
        assertEquals(.004, landing.extraFriction, EPS);
        assertEquals(.008, profile.step(ground(5)).extraFriction, EPS);
    }

    @Test public void anAirTurnOrWallInvalidatesRemainingAirCredit() {
        for (boolean wall : new boolean[]{false, true}) {
            IntaveMomentumProfile profile = profile();
            warm(profile);
            assertTrue(profile.step(frame(1, 3, false, false, 0, 1, 0, .02F, 0, 0)).hasAdjustment());
            float yaw = wall ? 0 : 90;
            assertFalse(profile.step(frame(1, 4, false, wall, 0, 1, yaw, .02F, 0, 0)).hasAdjustment());
            assertFalse(profile.step(frame(1, 5, false, false, 0, 1, yaw, .02F, 0, 0)).hasAdjustment());
            assertEquals(.004, profile.step(frame(1, 6, true, false, 0, 1, yaw, .13F, 0, 0)).extraFriction, EPS);
        }
    }

    @Test public void wallCapAndExitKeepOnlyBoundedAdditionalAcceleration() {
        IntaveMomentumProfile profile = profile();
        warm(profile);
        for (int tick = 3; tick <= 5; tick++) {
            Input wall = frame(1, tick, true, true, 0, 1, 0, .13F, .04, .03);
            Result result = profile.step(wall);
            assertEquals(Phase.WALL, result.phase);
            assertEquals(.006, result.extraFriction, EPS);
            assertTrue(result.extraFriction <= WALL_EXTRA);
            assertEquals(.04, wall.velocityX, 0);
            assertEquals(.03, wall.velocityZ, 0);
        }
        assertEquals(.010, profile.step(ground(6)).extraFriction, EPS);
        assertEquals(.012, profile.step(ground(7)).extraFriction, EPS);
    }

    @Test public void zeroInputNeverBrakesResidualVelocityAndResetsTheRamp() {
        IntaveMomentumProfile profile = profile();
        warm(profile);
        Input idle = frame(1, 3, true, false, 0, 0, 0, .13F, .31, -.16);
        Result result = profile.step(idle);
        assertEquals(Reason.NO_INPUT, result.reason);
        assertEquals(idle.friction, result.friction, 0);
        assertEquals(.31, idle.velocityX, 0);
        assertEquals(-.16, idle.velocityZ, 0);
        assertEquals(.004, profile.step(ground(4)).extraFriction, EPS);
    }

    @Test public void lowFrictionKeepsItsRelativeLimitInsteadOfReceivingAFlatBoost() {
        IntaveMomentumProfile profile = profile();
        for (int tick = 0; tick < 4; tick++) {
            Result result = profile.step(frame(1, tick, true, false, 0, 1, 0, .01F, 0, 0));
            assertTrue(result.extraFriction <= .01F * .12);
        }
        Result wall = profile.step(frame(1, 4, true, true, 0, 1, 0, .01F, 0, 0));
        assertTrue(wall.extraFriction <= .01F * .06);
        Result air = profile.step(frame(1, 5, false, false, 0, 1, 0, .01F, 0, 0));
        assertTrue(air.extraFriction <= .01F * .10);
    }

    @Test public void invalidNumbersConsumeOnlyTheirOwnOpportunityAndNeverProduceAnAdjustment() {
        Input[] invalid = {
                frame(1, 0, true, false, Float.NaN, 1, 0, .13F, 0, 0),
                frame(1, 0, true, false, 0, Float.POSITIVE_INFINITY, 0, .13F, 0, 0),
                frame(1, 0, true, false, 0, 1, Float.NaN, .13F, 0, 0),
                frame(1, 0, true, false, 0, 1, 0, Float.NaN, 0, 0),
                frame(1, 0, true, false, 0, 1, 0, Float.POSITIVE_INFINITY, 0, 0),
                frame(1, 0, true, false, 0, 1, 0, 0, 0, 0),
                frame(1, 0, true, false, 0, 1, 0, -.1F, 0, 0),
                frame(1, 0, true, false, 0, 1, 0, .21F, 0, 0),
                frame(1, 0, true, false, 0, 1, 0, .13F, Double.NaN, 0),
                frame(1, 0, true, false, 0, 1, 0, .13F, 0, Double.NEGATIVE_INFINITY),
                frame(1, 0, true, false, 3, 1, 0, .13F, 0, 0)
        };
        for (Input input : invalid) {
            IntaveMomentumProfile profile = profile();
            Result result = profile.step(input);
            assertEquals(Reason.INVALID_INPUT, result.reason);
            assertFalse(result.hasAdjustment());
            assertTrue(Float.isFinite(result.friction));
            assertEquals(0, result.extraFriction, 0);
            assertEquals(Reason.DUPLICATE_OR_OLD, profile.step(ground(0)).reason);
            assertEquals(.004, profile.step(ground(1)).extraFriction, EPS);
        }
        assertFalse(profile().step(null).hasAdjustment());
    }

    @Test public void speedLimitRejectsOnlyTheExtraAndDoesNotClampTheIncomingMotion() {
        IntaveMomentumProfile profile = profile();
        Input nearLimit = frame(1, 0, true, false, 0, 1, 0, .1F, 0, .297);
        Result rejected = profile.step(nearLimit);
        assertEquals(Reason.SPEED_BOUND, rejected.reason);
        assertFalse(rejected.hasAdjustment());
        assertEquals(.1F, rejected.friction, 0);
        assertEquals(.297, nearLimit.velocityZ, 0);
        Input impulse = frame(1, 1, true, false, 0, 1, 0, .13F, .8, -.5);
        assertFalse(profile.step(impulse).hasAdjustment());
        assertEquals(.8, impulse.velocityX, 0);
        assertEquals(-.5, impulse.velocityZ, 0);
        assertEquals(.004, profile.step(ground(2)).extraFriction, EPS);
    }

    @Test public void finalAxesAreNormalizedAndEveryProposedVectorRespectsTheNumericBound() {
        int applied = 0, rejected = 0;
        for (int yaw = -180; yaw < 180; yaw += 30)
            for (float strafe : new float[]{-1.38F, -.3F, 0, .3F, 1.38F})
                for (float forward : new float[]{-1, 0, 1})
                    for (float friction : new float[]{.02F, .1F, .13F, .2F})
                        for (double velocity : new double[]{-.3, 0, .3}) {
                            IntaveMomentumProfile profile = profile();
                            for (int tick = 0; tick < 4; tick++) {
                                Input input = frame(1, tick, true, false, strafe, forward,
                                        yaw, friction, velocity, velocity);
                                Result result = profile.step(input);
                                if (!result.hasAdjustment()) { rejected++; continue; }
                                applied++;
                                double norm = Math.max(1, Math.hypot(strafe, forward));
                                double angle = Math.toRadians(yaw);
                                double x = velocity + (strafe * Math.cos(angle) - forward * Math.sin(angle)) * result.friction / norm;
                                double z = velocity + (forward * Math.cos(angle) + strafe * Math.sin(angle)) * result.friction / norm;
                                assertTrue(Math.hypot(x, z) <= MAX_PREDICTED_HORIZONTAL + 1.0E-15);
                                assertTrue(result.extraFriction <= GROUND_EXTRA);
                                assertTrue(result.extraFriction <= friction * .12);
                                assertTrue(result.friction > friction);
                            }
                        }
        assertTrue(applied > 1_000);
        assertTrue(rejected > 1_000);
    }

    /**
     * Deliberately prescribed math model: add final event acceleration, integrate
     * free-space distance once, then multiply velocity by .54600006 on ground or
     * .91 in air. Ground/air and yaw are supplied, not simulated jump/collision
     * physics. Baseline is unchanged event friction, not native Intave Momentum.
     */
    @Test public void measuredPrescribedRecurrencesRemainDeterministicWithDuplicateCallbacks() {
        System.out.println("MODEL ONLY: prescribed ordinary steps, free-space recurrence; no native jump/collision or server.");
        for (String scenario : new String[]{"ground20", "stop20", "diagonal20", "turn90_20", "turn180_20", "air_landing20"}) {
            Model baseline = simulate(scenario, false, false);
            Model candidate = simulate(scenario, true, false);
            Model duplicates = simulate(scenario, true, true);
            assertEquals(candidate.distance, duplicates.distance, 0);
            assertEquals(candidate.lastDisplacement, duplicates.lastDisplacement, 0);
            assertEquals(candidate.adjustments, duplicates.adjustments);
            assertTrue(Double.isFinite(candidate.distance));
            assertEquals(20, candidate.steps);
            assertTrue(candidate.adjustments <= candidate.steps);
            if ("ground20".equals(scenario)) {
                assertTrue(candidate.distance > baseline.distance);
                assertTrue(candidate.firstQuarterBlockStep < baseline.firstQuarterBlockStep);
            }
            System.out.printf(Locale.ROOT,
                    "MODEL %s: baselineDistance=%.9f profileDistance=%.9f delta=%.9f baselineLastDisplacement=%.9f profileLastDisplacement=%.9f baselineFirst025=%d profileFirst025=%d adjustments=%d/20%n",
                    scenario, baseline.distance, candidate.distance, candidate.distance - baseline.distance,
                    baseline.lastDisplacement, candidate.lastDisplacement,
                    baseline.firstQuarterBlockStep, candidate.firstQuarterBlockStep, candidate.adjustments);
        }
    }

    private static final class Model {
        double distance, lastDisplacement;
        int firstQuarterBlockStep = -1, adjustments, steps;
    }

    private static Model simulate(String scenario, boolean enabled, boolean duplicates) {
        IntaveMomentumProfile profile = profile();
        Model result = new Model();
        double velocityX = 0, velocityZ = 0;
        for (int tick = 0; tick < 20; tick++) {
            boolean ground = !"air_landing20".equals(scenario) || tick < 6 || tick >= 12;
            float friction = ground ? .13F : .02F;
            float strafe = "diagonal20".equals(scenario) ? .98F : 0;
            float forward = "stop20".equals(scenario) && tick >= 8 && tick < 12 ? 0 : .98F;
            float yaw = tick < 10 ? 0 : "turn90_20".equals(scenario) ? 90 : "turn180_20".equals(scenario) ? 180 : 0;
            Input input = frame(1, tick, ground, false, strafe, forward, yaw, friction, velocityX, velocityZ);
            if (enabled) {
                Result adjustment = profile.step(input);
                if (adjustment.hasAdjustment()) {
                    friction = adjustment.friction;
                    result.adjustments++;
                }
                if (duplicates) {
                    assertFalse(profile.step(input).hasAdjustment());
                    assertFalse(profile.step(gates(input, true, false)).hasAdjustment());
                    assertFalse(profile.step(gates(input, false, true)).hasAdjustment());
                }
            }
            double length = Math.hypot(strafe, forward), yawRadians = Math.toRadians(yaw);
            if (length * length >= 1.0E-4) {
                double scale = friction / Math.max(1, length);
                velocityX += (strafe * Math.cos(yawRadians) - forward * Math.sin(yawRadians)) * scale;
                velocityZ += (forward * Math.cos(yawRadians) + strafe * Math.sin(yawRadians)) * scale;
            }
            result.lastDisplacement = Math.hypot(velocityX, velocityZ);
            result.distance += result.lastDisplacement;
            if (result.firstQuarterBlockStep < 0 && result.lastDisplacement >= .25)
                result.firstQuarterBlockStep = tick + 1;
            double drag = ground ? .54600006 : .91;
            velocityX *= drag;
            velocityZ *= drag;
            result.steps++;
        }
        return result;
    }
}
