package me.youngjibbit.core.contract;

import org.junit.Test;

import static me.youngjibbit.core.contract.SceneContext.Availability.*;
import static org.junit.Assert.*;

public class SceneContextTest {
    private final Object connection = new EqualIdentity();
    private final Object player = new EqualIdentity();
    private final Object world = new EqualIdentity();

    private SceneContext scene(SceneContext.Availability availability) {
        return new SceneContext(connection, player, world, 7, 12, availability);
    }

    @Test public void independentSnapshotsMatchTheSameLiveIdentitiesAndVersions() {
        SceneContext snapshot = scene(ACTIVE);
        assertTrue(snapshot.isValid());
        assertTrue(snapshot.sameSceneAs(scene(ACTIVE)));
        assertEquals(7, snapshot.getConnectionGeneration());
        assertEquals(12, snapshot.getPacketContextEpoch());
    }

    @Test public void everyReferenceChangeRejectsEvenObjectsThatCompareEqual() {
        SceneContext snapshot = scene(ACTIVE);
        assertEquals(connection, new EqualIdentity());
        assertFalse(snapshot.sameSceneAs(new SceneContext(new EqualIdentity(), player, world, 7, 12, ACTIVE)));
        assertFalse(snapshot.sameSceneAs(new SceneContext(connection, new EqualIdentity(), world, 7, 12, ACTIVE)));
        assertFalse(snapshot.sameSceneAs(new SceneContext(connection, player, new EqualIdentity(), 7, 12, ACTIVE)));
    }

    @Test public void connectionGenerationAndPacketEpochEachInvalidateOldMatches() {
        SceneContext snapshot = scene(ACTIVE);
        assertFalse(snapshot.sameSceneAs(new SceneContext(connection, player, world, 8, 12, ACTIVE)));
        assertFalse(snapshot.sameSceneAs(new SceneContext(connection, player, world, 7, 13, ACTIVE)));
    }

    @Test public void suspendPreservesIdentityAndValidityButInvalidationRemovesValidity() {
        SceneContext active = scene(ACTIVE), suspended = scene(SUSPENDED), invalid = scene(INVALIDATED);
        assertTrue(active.sameSceneAs(suspended));
        assertTrue(suspended.isValid());
        assertTrue(suspended.isSuspended());
        assertFalse(suspended.isInvalidated());
        assertTrue(invalid.isInvalidated());
        assertFalse(invalid.isValid());
        assertFalse(active.isSuspended());
        assertFalse(active.isInvalidated());
        assertTrue(active.isValid()); // Creating later observations does not mutate a historical value.
    }

    @Test public void missingIdentitiesAndUnknownVersionsAreInvalidAndNeverMatch() {
        SceneContext[] invalid = {
                new SceneContext(null, player, world, 7, 12, ACTIVE),
                new SceneContext(connection, null, world, 7, 12, ACTIVE),
                new SceneContext(connection, player, null, 7, 12, ACTIVE),
                new SceneContext(connection, player, world, -1, 12, ACTIVE),
                new SceneContext(connection, player, world, 7, -1, ACTIVE)
        };
        for (SceneContext snapshot : invalid) {
            assertFalse(snapshot.isValid());
            assertFalse(snapshot.sameSceneAs(scene(ACTIVE)));
            assertFalse(snapshot.sameSceneAs(snapshot));
        }
        assertFalse(scene(ACTIVE).sameSceneAs(null));
    }

    @Test public void versionZeroAndLargeMonotoneVersionsAreRepresentable() {
        assertTrue(new SceneContext(connection, player, world, 0, 0, ACTIVE).isValid());
        assertTrue(new SceneContext(connection, player, world, Long.MAX_VALUE, Long.MAX_VALUE, ACTIVE).isValid());
    }

    @Test(expected = NullPointerException.class)
    public void availabilityMustBeExplicit() {
        new SceneContext(connection, player, world, 7, 12, null);
    }

    private static final class EqualIdentity {
        @Override public boolean equals(Object other) { return other instanceof EqualIdentity; }
        @Override public int hashCode() { return 1; }
    }
}
