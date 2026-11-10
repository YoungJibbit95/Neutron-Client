package me.youngjibbit.core.combat;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class AdaptiveTargetPolicyTest {
    @Test public void singleKeepsAValidIdentityDespitePriorityReordering() {
        Object a = new Object(), b = new Object(); AdaptiveTargetPolicy p = new AdaptiveTargetPolicy();
        assertSame(a, p.select(Arrays.asList(a,b), null, false, 500, 0));
        assertSame(a, p.select(Arrays.asList(b,a), a, false, 500, 1000));
        assertSame(b, p.select(Collections.singletonList(b), a, false, 500, 1001));
    }
    @Test public void switchUsesMonotonicElapsedTimeAndInvalidTargetNeverWaits() {
        Object a = new Object(), b = new Object(); AdaptiveTargetPolicy p = new AdaptiveTargetPolicy();
        assertSame(a, p.select(Arrays.asList(a,b), null, true, 500, 1000));
        assertSame(a, p.select(Arrays.asList(a,b), a, true, 500, 1499));
        assertSame(b, p.select(Arrays.asList(a,b), a, true, 500, 1500));
        assertSame(a, p.select(Collections.singletonList(a), b, true, 500, 1501));
        assertNull(p.select(Collections.emptyList(), a, true, 500, 1502));
    }
    @Test public void equalityCannotReuseTheStateOfADifferentEntityWithTheSameId() {
        class SameId { public boolean equals(Object value) { return value instanceof SameId; } public int hashCode() { return 1; } }
        SameId a = new SameId(), replacement = new SameId(); AdaptiveTargetPolicy p = new AdaptiveTargetPolicy();
        assertSame(a, p.select(Collections.singletonList(a), null, true, 500, 0));
        assertSame(replacement, p.select(Collections.singletonList(replacement), a, true, 500, 1));
    }
}
