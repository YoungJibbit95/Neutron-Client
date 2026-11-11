package me.youngjibbit.module.impl.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticsGraphMotionTest {
    @Test public void plotAxisHasAPositiveFiniteRangeForEmptyZeroAndInvalidSeries() {
        assertEquals(1,DiagnosticsPlots.seriesMaximum(null,0),0);
        assertEquals(1,DiagnosticsPlots.seriesMaximum(new double[]{0,0,0},0),0);
        assertEquals(50,DiagnosticsPlots.seriesMaximum(new double[]{0,0},50),0);
        assertEquals(85,DiagnosticsPlots.seriesMaximum(new double[]{10,85,20},50),0);
        assertEquals(1,DiagnosticsPlots.seriesMaximum(new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY},Double.NaN),0);
        assertEquals(1,DiagnosticsPlots.seriesMaximum(new double[0],Double.NEGATIVE_INFINITY),0);
        assertEquals(7,DiagnosticsPlots.seriesMaximum(new double[]{0,7,Double.NaN},-20),0);
    }
    @Test public void rawHistoryRemainsUnchangedWhileNewestPointEases(){
        double[] raw={10,15,20,85};
        double[] view=DiagnosticsPlots.easedTail(raw,39);
        assertArrayEquals(new double[]{10,15,20,85},raw,0);
        assertArrayEquals(new double[]{10,15,20,39},view,0);
        assertNotSame(raw,view);
    }
    @Test public void missingPingNeverBecomesInventedZeroLatency(){
        double[] raw={30,48,-1};
        assertArrayEquals(new double[]{30,48,-1},DiagnosticsPlots.easedTail(raw,37),0);
        assertArrayEquals(raw,DiagnosticsPlots.easedTail(raw,Float.NaN),0);
        assertEquals(0,DiagnosticsPlots.easedTail(null,20).length);
    }
    @Test public void invalidOrEmptySamplesRemainInspectablyMissing(){
        double[] raw={15,Double.NaN};
        double[] smooth=DiagnosticsPlots.easedTail(raw,12);
        assertEquals(15,smooth[0],0);
        assertTrue(Double.isNaN(smooth[1]));
        assertEquals(0,DiagnosticsPlots.easedTail(new double[0],10).length);
        assertArrayEquals(new double[]{30},DiagnosticsPlots.easedTail(new double[]{30},-1),0);
    }
}
