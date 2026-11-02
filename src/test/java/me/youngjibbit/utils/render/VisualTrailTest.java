package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class VisualTrailTest {
    @Test public void capacityEvictsOldestInChronologicalOrder(){
        VisualTrail trail=new VisualTrail(3);Object world=new Object();
        for(int i=0;i<8;i++)trail.add(world,i,2,3,i*10);
        assertEquals(3,trail.size());assertEquals(5,trail.x(0),0);assertEquals(7,trail.x(2),0);
        trail.expire(85,20);assertEquals(1,trail.size());assertEquals(70,trail.time(0));
    }
    @Test public void worldsAndTeleportsNeverConnect(){
        VisualTrail trail=new VisualTrail(8);Object a=new Object(),b=new Object();
        trail.add(a,0,0,0,1);trail.add(a,1,0,0,2);trail.world(b);assertEquals(0,trail.size());
        trail.add(b,0,0,0,3);trail.add(b,100,0,0,4);assertEquals(1,trail.size());assertEquals(100,trail.x(0),0);
        trail.world(null);assertEquals(0,trail.size());
    }
    @Test public void invalidSamplesAndClockResetAreSafe(){
        VisualTrail trail=new VisualTrail(4);Object w=new Object();
        trail.add(w,1,2,3,100);trail.add(w,Double.NaN,0,0,101);trail.add(w,0,Double.POSITIVE_INFINITY,0,102);
        assertEquals(1,trail.size());trail.expire(50,1000);assertEquals(0,trail.size());
        trail.add(null,0,0,0,0);assertEquals(0,trail.size());
    }
    @Test(expected=IllegalArgumentException.class) public void zeroCapacityRejected(){new VisualTrail(0);}
}
