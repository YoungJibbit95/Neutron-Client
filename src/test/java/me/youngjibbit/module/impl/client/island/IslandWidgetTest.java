package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.core.activity.ExploitActivitySnapshot;
import org.junit.Test;
import static org.junit.Assert.*;

public class IslandWidgetTest {
    @Test public void onlyFreshObservedActivityCanLightTheExploitWidget() {
        ExploitActivitySnapshot held=ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,100,1,2,34);
        IslandWidget active=IslandWidget.exploit(new ExploitActivitySnapshot[]{held},101,0,true);
        assertTrue(active.active);assertEquals("BT",active.label);assertEquals("34ms",active.value);assertEquals("Hold",active.detail);
        assertFalse(IslandWidget.exploit(new ExploitActivitySnapshot[]{held},99,0,true).active);
        assertFalse(IslandWidget.exploit(new ExploitActivitySnapshot[]{held},100+ExploitActivitySnapshot.TTL_NANOS+1,0,true).active);
        assertEquals("Idle",IslandWidget.exploit(null,100,0,true).detail);
    }
    @Test public void observedReleaseTakesPriorityOverHeldOrChargedWork() {
        ExploitActivitySnapshot held=ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,100,1,2,34);
        ExploitActivitySnapshot charged=ExploitActivitySnapshot.steps(ExploitActivitySnapshot.Producer.LAGRANGE,100,1,3,0,false);
        ExploitActivitySnapshot firing=ExploitActivitySnapshot.steps(ExploitActivitySnapshot.Producer.TICKBASE,100,1,2,1,true);
        IslandWidget selected=IslandWidget.exploit(new ExploitActivitySnapshot[]{held,charged,firing},100,0,true);
        assertEquals("TB",selected.label);assertEquals("Fire",selected.detail);assertEquals("+1t",selected.value);
    }
    @Test public void reducedMotionFreezesDecorationWhileKeepingTruthfulValues() {
        IslandWidget still=new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,0,false);
        assertEquals(1,still.pulse,0);assertEquals(0,still.phase,0);
        for(float time:new float[]{.2f,2,Float.NaN,Float.POSITIVE_INFINITY})
            assertEquals(still.pulse,new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,time,false).pulse,0);
        assertNotEquals(new IslandWidget(IslandWidget.Kind.CPS,"CPS","1·2","L / R",true,0,true).pulse,
                new IslandWidget(IslandWidget.Kind.CPS,"CPS","1·2","L / R",true,.25f,true).pulse,0);
    }
    @Test public void producerSignaturesAreDistinctAndFitTheirReservedSixPixelIcon(){
        int[] segments={4,6,3};int index=0;
        for(String label:new String[]{"BT","LR","TB"}){
            IslandExploitGlyph glyph=IslandExploitGlyph.from(new IslandWidget(IslandWidget.Kind.EXPLOIT,label,"1t","Ready",true,0,false));
            assertNotNull(glyph);assertEquals(segments[index++],glyph.segments());
            assertSame(glyph,IslandExploitGlyph.from(new IslandWidget(IslandWidget.Kind.EXPLOIT,label,"9t","Fire",true,4,true)));
            for(int i=0;i<glyph.segments();i++)for(float value:new float[]{glyph.x1(i),glyph.y1(i),glyph.x2(i),glyph.y2(i)})
                assertTrue(value>=-3&&value<=3);
        }
        assertNull(IslandExploitGlyph.from(new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,0,false)));
    }

    @Test public void orbitIsFiniteAndReducedMotionFreezesEveryDecorativeChannel(){
        for(float time:new float[]{-.3f,0,.37f,100000,Float.NaN,Float.POSITIVE_INFINITY}){
            IslandWidget moving=new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,time,true);
            assertTrue(Float.isFinite(moving.phase)&&moving.phase>=0&&moving.phase<1);
            IslandWidget reduced=new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,time,false);
            assertEquals(0,reduced.phase,0);assertEquals(1,reduced.pulse,0);
            assertEquals("144",reduced.value);
        }
        assertNotEquals(new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,0,true).phase,
                new IslandWidget(IslandWidget.Kind.FPS,"FPS","144","live",true,.37f,true).phase,0);
    }
}
