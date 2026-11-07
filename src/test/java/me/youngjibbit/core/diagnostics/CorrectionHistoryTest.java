package me.youngjibbit.core.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;

public class CorrectionHistoryTest {
    @Test public void initialSyncIsNeverPresentedAsAFlagAndNoiseHasASeparateState() {
        CorrectionHistory h=new CorrectionHistory();
        assertFalse(h.record(1,200,90,true,"spawn","","server logs").possibleSetback);
        assertFalse(h.record(2,.01,1,false,"","","").possibleSetback);
        assertTrue(h.record(3,.5,0,false,"aura enabled","range=3","A/B test").possibleSetback);
        assertEquals("Possible setback",h.latest().kind());assertEquals("aura enabled",h.latest().context);
        assertNull(h.record(4,Double.NaN,0,false,"","",""));assertEquals(3,h.snapshot().size());
    }
    @Test public void keepsOnlyTwentyFourFrozenEventsAndClearStartsANewRun() {
        CorrectionHistory h=new CorrectionHistory();for(int i=0;i<100;i++)h.record(i,i,0,false,"ctx"+i,"values"+i,"advice");
        assertEquals(24,h.snapshot().size());assertEquals(100,h.latest().id);assertEquals("values99",h.latest().values);
        assertEquals(77,h.snapshot().get(23).id);h.clear();assertNull(h.latest());assertEquals(1,h.record(1,1,0,false,"","","").id);
    }
}
