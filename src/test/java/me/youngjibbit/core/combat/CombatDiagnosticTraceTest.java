package me.youngjibbit.core.combat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CombatDiagnosticTraceTest {
    @Test public void boundedHistoryRetainsNewestEntriesInReverseChronologicalOrder() {
        CombatDiagnosticTrace trace = new CombatDiagnosticTrace(3);
        trace.append("motion");
        trace.append("attack attempt");
        trace.append("local attack accepted");
        trace.append("manual flag marker");
        assertEquals(3, trace.size());
        assertEquals("manual flag marker", trace.recent(3).get(0));
        assertEquals("local attack accepted", trace.recent(3).get(1));
        assertEquals("attack attempt", trace.recent(3).get(2));
        assertEquals(1, trace.recent(1).size());
    }

    @Test public void invalidAndEmptyEventsDoNotEvictAndSnapshotIsIndependent() {
        CombatDiagnosticTrace trace = new CombatDiagnosticTrace(2);
        trace.append("A");
        List<String> snapshot = trace.recent(10);
        trace.append(null);
        trace.append("");
        assertEquals(1, trace.size());
        trace.append("B");
        trace.append("C");
        assertEquals("A", snapshot.get(0));
        assertEquals("C", trace.recent(2).get(0));
        assertEquals("B", trace.recent(2).get(1));
        trace.clear();
        assertEquals(0, trace.size());
        assertTrue(trace.recent(10).isEmpty());
        assertTrue(trace.recent(0).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotAllocateZeroCapacity() {
        new CombatDiagnosticTrace(0);
    }
}
