package me.youngjibbit.core.combat.ai;

import org.junit.Test;
import static org.junit.Assert.*;

/** Version-1 feature math fixtures: strictly physical state, no personal data. */
public class NeutronAiFeaturesTest {
    static NeutronAiFeatures.Observation visibleTarget() {
        NeutronAiFeatures.Observation o = new NeutronAiFeatures.Observation();
        o.eyeX = 0; o.eyeY = 1.6; o.eyeZ = 0;
        o.targetX = -3; o.targetY = 1.4; o.targetZ = 0;
        o.yaw = 0; o.pitch = 0;
        o.visible = true; o.targetAlive = true;
        o.playerGrounded = true; o.targetGrounded = false;
        return o;
    }

    @Test public void targetGeometryCreatesCorrectWrappedYawAndPitch() {
        NeutronAiFeatures.Observation o = visibleTarget();
        assertTrue(NeutronAiFeatures.valid(o));
        assertEquals(90, NeutronAiFeatures.targetYaw(o), 1e-10);
        assertEquals(-180, NeutronAiFeatures.wrap(180), 0);
        assertEquals(179, NeutronAiFeatures.wrap(-181), 0);
        assertEquals(90, NeutronAiFeatures.wrap(NeutronAiFeatures.targetYaw(o) - o.yaw), 0);
        assertTrue(NeutronAiFeatures.targetPitch(o) > 0);
        o.yaw = 179;
        assertEquals(-89, NeutronAiFeatures.wrap(NeutronAiFeatures.targetYaw(o) - o.yaw), 0);
    }

    @Test public void allFortyFeaturesAreBoundedFiniteAndDeterministic() {
        NeutronAiFeatures.Observation o = visibleTarget();
        o.playerVx = .16; o.targetVx = -.1; o.targetVy = .02; o.targetVz = .3;
        float[] first = new float[NeutronAiFeatures.WIDTH];
        float[] second = new float[NeutronAiFeatures.WIDTH];
        assertTrue(NeutronAiFeatures.populate(o, 120_000_000L, first));
        assertTrue(NeutronAiFeatures.populate(o, 120_000_000L, second));
        assertArrayEquals(first, second, 0);
        assertEquals(.5f, first[0], 1e-6f);
        assertEquals(1f, first[36], 0);
        assertEquals(1f, first[37], 0);
        assertEquals(0f, first[38], 0);
        for (float f : first) {
            assertTrue(Float.isFinite(f));
            assertTrue(f >= -1 && f <= 1);
        }
    }

    @Test public void invalidVisibilityTargetStateAndGeometryNeverProduceFeatures() {
        NeutronAiFeatures.Observation o = visibleTarget();
        o.visible = false;
        assertFalse(NeutronAiFeatures.valid(o));
        o.visible = true; o.targetAlive = false;
        assertFalse(NeutronAiFeatures.valid(o));
        o.targetAlive = true; o.targetWidth = 0;
        assertFalse(NeutronAiFeatures.valid(o));
        o.targetWidth = .6; o.reach = 0;
        assertFalse(NeutronAiFeatures.valid(o));
        o.reach = 3; o.pitch = 120;
        assertFalse(NeutronAiFeatures.valid(o));
        o.pitch = 0; o.eyeX = Double.NaN;
        assertFalse(NeutronAiFeatures.valid(o));
        o.eyeX = 0; o.targetVx = 100;
        assertFalse(NeutronAiFeatures.valid(o));
        o.targetVx = 0;
        assertFalse(NeutronAiFeatures.populate(o, -1, new float[40]));
        assertFalse(NeutronAiFeatures.populate(o, 1, new float[39]));
    }

    @Test public void worldCoordinateAndTeleportVelocityLimitsReject() {
        NeutronAiFeatures.Observation o = visibleTarget();
        o.targetZ = Double.POSITIVE_INFINITY;
        assertFalse(NeutronAiFeatures.valid(o));
        o.targetZ = 0; o.eyeX = 40_000_000;
        assertFalse(NeutronAiFeatures.valid(o));
        o.eyeX = 0; o.playerVy = -5;
        assertFalse(NeutronAiFeatures.valid(o));
        o.playerVy = 0; o.targetHeight = Double.NaN;
        assertFalse(NeutronAiFeatures.valid(o));
        o.targetHeight = 1.8; o.targetX = 0; o.targetY = 1.6;
        assertFalse("overlapping eye and target center must HOLD", NeutronAiFeatures.valid(o));
    }

    @Test public void observationChangesAreTimeBasedNotRenderCallBased() {
        NeutronAiFeatures.Observation o = visibleTarget();
        float[] first = new float[40], repeat = new float[40], next = new float[40];
        assertTrue(NeutronAiFeatures.populate(o, 20_000_000L, first));
        for (int fps : new int[]{20, 60, 144, 240}) {
            for (int frame = 0; frame < fps / 20 + 1; frame++) {
                assertTrue(NeutronAiFeatures.populate(o, 20_000_000L, repeat));
                assertArrayEquals(first, repeat, 0);
            }
        }
        assertTrue(NeutronAiFeatures.populate(o, 50_000_000L, next));
        assertTrue(next[35] > first[35]);
    }
}
