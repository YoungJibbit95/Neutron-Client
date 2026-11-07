package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public final class HaloCachePolicyTest {
    @Test public void budgetUsesActualRgbaBytesAndNeverExceedsEitherLimit() {
        assertEquals(32,HaloCache.capacity(640,360));
        assertEquals(16,HaloCache.capacity(1920,1080));
        assertEquals(9,HaloCache.capacity(2560,1440));
        assertEquals(4,HaloCache.capacity(3840,2160));
        assertEquals(0,HaloCache.capacity(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertEquals(0,HaloCache.capacity(0,1080));
        for(int width:new int[]{1,641,1920,2560,3840,8192}) {
            int count=HaloCache.capacity(width,1440);
            assertTrue(count<=32 && (long)count*width*1440*4<=HaloCache.MAX_BYTES);
        }
    }

    @Test public void thirteenPanelsKeepNineHotInsteadOfRoundRobinThrashing() {
        HaloCache.Retention<Integer> policy=new HaloCache.Retention<>();
        int hits=0,misses=0,bypasses=0;
        for(int frame=0;frame<200;frame++)for(int panel=0;panel<13;panel++) {
            HaloCache.Selection choice=policy.select(panel,frame*16_000_000L,9);
            if(choice==null){bypasses++;continue;}
            if(choice.hit)hits++;else {misses++;assertFalse(choice.evicted);policy.complete(choice);}
        }
        assertEquals(9,misses);assertEquals(199*9,hits);assertEquals(200*4,bypasses);
    }

    @Test public void idleReplacementAndFailedPopulationReuseTheSameSlot() {
        HaloCache.Retention<String> policy=new HaloCache.Retention<>();
        HaloCache.Selection failed=policy.select("failed",0,32);
        // A renderer/allocation failure does not publish this selection.
        HaloCache.Selection recovered=policy.select("recovered",1,32);
        assertEquals(failed.index,recovered.index);assertFalse(recovered.hit);
        policy.complete(recovered);
        assertNull(policy.select("busy",HaloCache.IDLE_NANOS+1,1));
        HaloCache.Selection replacement=policy.select("idle",HaloCache.IDLE_NANOS+2,1);
        assertEquals(recovered.index,replacement.index);assertTrue(replacement.evicted);
        assertFalse(replacement.hit);policy.complete(replacement);
        assertTrue(policy.select("idle",HaloCache.IDLE_NANOS+3,1).hit);
    }

    @Test public void keyCopiesGeometryAndKeepsFractionalBoundsAndRasterStateExact() {
        float[] corners={1,2,3,0};
        HaloCache.Key first=key(corners,0,false,7);
        corners[0]=99;
        assertEquals(first,key(new float[]{1,2,3,0},0,false,7));
        assertNotEquals(first,key(new float[]{1,2,3,0},-0f,false,7));
        assertNotEquals(first,key(new float[]{1,2,3,0},Math.ulp(1f),false,7));
        assertNotEquals(first,key(new float[]{1,2,3,0},0,true,7));
        assertNotEquals(first,key(new float[]{1,2,3,0},0,false,8));
    }
    private static HaloCache.Key key(float[] corners,float left,boolean dither,int radius) {
        return new HaloCache.Key(640,360,2,radius,dither,corners,left,2,30,40);
    }
}
