package me.youngjibbit.module.impl.render;

import org.junit.Test;
import static org.junit.Assert.*;

/** No fake ping, graph or setback points and no altered telemetry history. */
public class Wave07DiagnosticsCardTest {
    @Test public void graphAcceptsOnlyActualFiniteNonnegativeMeasurements() {
        assertTrue(Diagnostics.graphSampleValid(0));
        assertTrue(Diagnostics.graphSampleValid(16.7));
        assertTrue(Diagnostics.graphSampleValid(200));
        for(double bad:new double[]{-1,-.001,Double.NaN,
                Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})
            assertFalse(Diagnostics.graphSampleValid(bad));
    }
    @Test public void easingDoesNotModifyOriginalMeasurementHistory() {
        double[] original={12.1,13.2,-1,14.3,Double.NaN,17.4};
        double[] copy=original.clone();
        double[] display=DiagnosticsPlots.easedTail(original,16);
        assertArrayEquals(copy,original,0);
        assertEquals(12.1,display[0],0);
        assertEquals(-1,display[2],0);
        assertTrue(Double.isNaN(display[4]));
        assertEquals(16,display[5],0);
    }
    @Test public void legacyDisplayModeIsNotReplacedByCosmeticTheme(){
        Diagnostics module=new Diagnostics();
        assertArrayEquals(new String[]{"Text","Simple"},module.getModeSetting("Display Mode").getOptions());
        assertEquals("Text",module.getModeSetting("Display Mode").getValue());
        module.getModeSetting("Display Mode").setValue("Simple");
        assertEquals("Simple",module.getModeSetting("Display Mode").getValue());
    }
}
