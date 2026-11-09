package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import org.junit.Test;
import static org.junit.Assert.*;

public class IslandScaffoldIndicatorTest {
    private static IslandStateMachine.Scaffold observed(ScaffoldActivitySnapshot.Status status){
        return IslandObservations.scaffold(new ScaffoldActivitySnapshot(7,100,1,3,47,
                "Adaptive","Arsenic",status),100);
    }
    @Test public void everyActualProducerStatusHasItsOwnGlyphAndRetainsSequence(){
        for(ScaffoldActivitySnapshot.Status status:ScaffoldActivitySnapshot.Status.values()){
            IslandScaffoldIndicator indicator=IslandScaffoldIndicator.capture(observed(status),0,true);
            assertNotNull(indicator);assertEquals(status.name(),indicator.status.name());
            assertEquals(7,indicator.sequence);assertFalse(indicator.label.isEmpty());
            assertTrue(indicator.segmentCount()>0&&indicator.segmentCount()<=16);
        }
        assertEquals(2,IslandScaffoldIndicator.capture(observed(ScaffoldActivitySnapshot.Status.READY),0,true).segmentCount());
        assertTrue(IslandScaffoldIndicator.capture(observed(ScaffoldActivitySnapshot.Status.EDGE_GUARD),0,true).warning());
        assertTrue(IslandScaffoldIndicator.capture(observed(ScaffoldActivitySnapshot.Status.RECOVERING),0,true).warning());
    }
    @Test public void absentLegacyAndUnknownStatusesNeverBecomeReady(){
        assertNull(IslandScaffoldIndicator.capture(null,0,true));
        assertNull(IslandScaffoldIndicator.capture(new IslandStateMachine.Scaffold(true,24,"Legacy"),0,true));
        assertNull(IslandScaffoldIndicator.capture(IslandStateMachine.Scaffold.observed(24,"Normal","Legacy",
                "Invented ready",1,0,7),0,true));
        IslandScaffoldIndicator searching=IslandScaffoldIndicator.capture(observed(ScaffoldActivitySnapshot.Status.SEARCHING),0,true);
        assertEquals(IslandScaffoldIndicator.Status.SEARCHING,searching.status);assertFalse(searching.warning());
    }
    @Test public void existingMotionAnimatesEveryStateAndReducedOrDisabledMotionIsStatic(){
        for(ScaffoldActivitySnapshot.Status status:ScaffoldActivitySnapshot.Status.values()){
            IslandStateMachine.Scaffold source=observed(status);
            IslandScaffoldIndicator first=IslandScaffoldIndicator.capture(source,0,true);
            assertFalse(same(first,IslandScaffoldIndicator.capture(source,.37f,true)));
            IslandScaffoldIndicator still=IslandScaffoldIndicator.capture(source,0,false);
            assertEquals(1,still.opacity,0);
            for(float time:new float[]{-10,.37f,10,100000,Float.NaN,Float.POSITIVE_INFINITY})
                assertTrue(same(still,IslandScaffoldIndicator.capture(source,time,false)));
        }
    }
    @Test public void allAnimatedSegmentsStayWithinTheReservedTwentyFourPixelGlyph(){
        for(ScaffoldActivitySnapshot.Status status:ScaffoldActivitySnapshot.Status.values())for(int i=0;i<240;i++){
            IslandScaffoldIndicator indicator=IslandScaffoldIndicator.capture(observed(status),i/60f,true);
            assertTrue(Float.isFinite(indicator.opacity)&&indicator.opacity>=0&&indicator.opacity<=1);
            for(int j=0;j<indicator.segmentCount();j++)for(float v:new float[]{indicator.x1(j),indicator.y1(j),indicator.x2(j),indicator.y2(j)})
                assertTrue(Float.isFinite(v)&&v>=1&&v<=23);
        }
    }
    @Test public void itemTextAndStatusFitTogetherDuringTinyAndLargeIslandMorphs(){
        for(boolean modern:new boolean[]{false,true})for(int[] size:new int[][]{{1,1},{20,1},{1,20},{40,20},{100,80},{640,360},{854,480},{1920,1080}}){
            IslandLayout.Frame frame=IslandLayout.forState(IslandStateMachine.State.SCAFFOLD,size[0],size[1],modern);
            for(float fraction:new float[]{.2f,.5f,1}){
                IslandLayout.Frame morph=frame.withGeometry(frame.width*fraction,frame.height*fraction,frame.radius,1);
                IslandScaffoldIndicator.Layout p=new IslandScaffoldIndicator.Layout(morph);
                assertTrue(p.scale>0&&p.scale<=1);assertTrue(p.x>=morph.x-.0001f&&p.y>=morph.y-.0001f);
                assertTrue(p.x+p.width<=morph.x+morph.width+.0001f&&p.y+p.height<=morph.y+morph.height+.0001f);
                assertTrue(IslandScaffoldIndicator.Layout.ITEM_X+IslandScaffoldIndicator.Layout.ITEM_SIZE<IslandScaffoldIndicator.Layout.TEXT_X);
                assertTrue(IslandScaffoldIndicator.Layout.TEXT_X+IslandScaffoldIndicator.Layout.TEXT_WIDTH<IslandScaffoldIndicator.Layout.STATUS_X);
                assertTrue(IslandScaffoldIndicator.Layout.STATUS_X+IslandScaffoldIndicator.Layout.STATUS_SIZE<IslandScaffoldIndicator.Layout.WIDTH);
            }
        }
    }
    @Test public void expiredProviderWithdrawsIndicatorAndReadsDoNotChangeCopiedData(){
        ScaffoldActivitySnapshot dto=new ScaffoldActivitySnapshot(9,100,1,3,47,"Adaptive","Arsenic",ScaffoldActivitySnapshot.Status.PLACING);
        IslandStateMachine.Scaffold source=IslandObservations.scaffold(dto,100);
        for(int i=0;i<100;i++)assertEquals(9,IslandScaffoldIndicator.capture(source,i/60f,true).sequence);
        assertEquals(47,source.blockCount);assertEquals(1,source.itemId);assertEquals(3,source.metadata);
        assertEquals("Adaptive",source.approach);assertEquals("Arsenic",source.profile);assertEquals("Placing",source.status);
        assertNull(IslandScaffoldIndicator.capture(IslandObservations.scaffold(dto,100+ScaffoldActivitySnapshot.TTL_NANOS+1),2,true));
    }
    private static boolean same(IslandScaffoldIndicator a,IslandScaffoldIndicator b){
        if(a.status!=b.status||a.opacity!=b.opacity||a.segmentCount()!=b.segmentCount())return false;
        for(int i=0;i<a.segmentCount();i++)if(a.x1(i)!=b.x1(i)||a.y1(i)!=b.y1(i)||a.x2(i)!=b.x2(i)||a.y2(i)!=b.y2(i))return false;
        return true;
    }
}
