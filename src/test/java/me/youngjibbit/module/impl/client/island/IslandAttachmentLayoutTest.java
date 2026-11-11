package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.gui.IslandMorphMotion;
import me.youngjibbit.utils.render.ArrayListSurface;
import org.junit.Test;
import static org.junit.Assert.*;

public class IslandAttachmentLayoutTest {
    @Test public void attachedSheetsKeepModernClockAndLogoInsideTheirHeader(){
        for(boolean modern:new boolean[]{false,true})for(int count=0;count<=3;count++)
            for(IslandStateMachine.State kind:new IslandStateMachine.State[]{IslandStateMachine.State.TARGET,
                    IslandStateMachine.State.NOTIFICATION,IslandStateMachine.State.POSSIBLE_SETBACK})
                for(float scale:new float[]{1,.75f,.5f})for(float opening:new float[]{.02f,.25f,.6f,1}){
                    float width=IslandWidgetLayout.idleWidth(modern,count,false);
                    IslandLayout.Frame desired=IslandLayout.forState(kind,854,480,modern);
                    IslandLayout.Frame frame=desired.withGeometry(width*scale,
                            (34+(desired.height-34)*opening)*scale,17*scale,1);
                    IslandAttachmentLayout layout=new IslandAttachmentLayout(frame,width,kind,scale);
                    assertEquals(layout.base.y+layout.base.height,layout.joinY,.0001f);
                    ArrayListSurface.Shape contour=layout.silhouette();
                    for(int sample=0;sample<64;sample++){
                        double angle=sample*Math.PI/32;
                        // Actual paintIslandIdle badge and clock canvases, one
                        // half-pixel inset so antialiasing is not the assertion.
                        assertTrue("logo "+kind,contour.contains(layout.base.x+(20+9.5f*(float)Math.cos(angle))*scale,
                                frame.y+(17+9.5f*(float)Math.sin(angle))*scale));
                        assertTrue("clock "+kind,contour.contains(layout.base.x+(width-23+12.5f*(float)Math.cos(angle))*scale,
                                frame.y+(17+12.5f*(float)Math.sin(angle))*scale));
                    }
                }
    }
    @Test public void baseAndLowerSheetShareOneContinuousSilhouette(){
        for(IslandStateMachine.State kind:new IslandStateMachine.State[]{IslandStateMachine.State.TARGET,
                IslandStateMachine.State.NOTIFICATION,IslandStateMachine.State.POSSIBLE_SETBACK}){
            IslandLayout.Frame frame=IslandLayout.forState(kind,854,480,true);
            IslandAttachmentLayout layout=new IslandAttachmentLayout(frame,234,kind);
            assertEquals(frame.y,layout.base.y,0);assertEquals(34,layout.base.height,0);
            assertEquals(layout.base.y+layout.base.height,layout.detail.y,0);
            assertEquals(1,layout.reveal,.0001f);
            assertTrue(layout.detail.width>=layout.base.width*.5f&&layout.detail.width<=layout.base.width*.75f);
            assertEquals(layout.base.x+layout.base.width/2,layout.detail.x+layout.detail.width/2,.0001f);
            ArrayListSurface.Shape contour=layout.silhouette();
            float center=frame.x+frame.width/2;
            for(float y=frame.y+.1f;y<frame.y+frame.height-.1f;y+=.2f)assertTrue(contour.contains(center,y));
            assertFalse(contour.contains(frame.x-1,layout.joinY));
        }
        assertFalse(IslandAttachmentLayout.attached(IslandStateMachine.State.MODULE_TOAST));
    }
    @Test public void downwardOpeningKeepsBaseAnchorAndReducedMotionIsImmediate(){
        IslandLayout.Frame idle=IslandLayout.forState(IslandStateMachine.State.IDLE,854,480,true);
        IslandLayout.Frame target=IslandLayout.forState(IslandStateMachine.State.TARGET,854,480,true);
        IslandMorphMotion motion=new IslandMorphMotion();motion.sample(idle,0,true,false,"Balanced");
        IslandAttachmentLayout first=new IslandAttachmentLayout(idle.withGeometry(234,35,17,1),234,IslandStateMachine.State.TARGET);
        assertEquals(234,first.base.width,0);assertEquals(34,first.base.height,0);
        for(int i=0;i<120;i++){
            IslandLayout.Frame frame=motion.sample(target,1f/60,true,false,"Balanced",IslandStateMachine.State.TARGET);
            IslandAttachmentLayout layout=new IslandAttachmentLayout(frame,234,IslandStateMachine.State.TARGET);
            assertEquals(idle.y,layout.base.y,0);assertTrue(layout.reveal>=0&&layout.reveal<=1);
            assertTrue(frame.y+frame.height<=480);
        }
        assertEquals(idle.height,motion.sample(idle,0,true,true,"Expressive",IslandStateMachine.State.IDLE).height,0);
    }
    @Test public void expandedWidgetsStayFiniteAndWithinViewport(){
        for(int[] size:new int[][]{{1,1},{40,20},{100,80},{640,360},{854,480}}){
            IslandLayout.Frame frame=IslandLayout.forState(IslandStateMachine.State.NOTIFICATION,size[0],size[1],true);
            IslandWidgetLayout layout=IslandWidgetLayout.capture(frame,3,true,new float[]{34,192,220});
            assertTrue(layout.frame.x>=0);assertTrue(layout.x(2)+layout.width(2)<=size[0]+.0001f);
            for(int i=0;i<3;i++){assertTrue(layout.width(i)>=layout.diameter);assertTrue(Float.isFinite(layout.x(i)));}
            IslandAttachmentLayout attachment=new IslandAttachmentLayout(layout.frame,234,IslandStateMachine.State.NOTIFICATION,layout.scale);
            assertTrue(attachment.silhouette().contains(layout.frame.x+layout.frame.width/2,
                    layout.frame.y+layout.frame.height/2));
        }
    }
    @Test public void restingWidthsEqualTheirDiameterEvenWhenViewportHeightConstrainsThem(){
        for(int[] size:new int[][]{{1,1},{20,1},{40,20},{640,360}}){
            IslandLayout.Frame frame=IslandLayout.forState(IslandStateMachine.State.IDLE,size[0],size[1],true);
            IslandWidgetLayout layout=IslandWidgetLayout.capture(frame,3,true,new float[]{34,34,34});
            for(int i=0;i<3;i++)assertEquals(layout.diameter,layout.width(i),0);
        }
    }
    @Test public void outerIncidentsRemainVisibleBesideHigherPriorityStates(){
        for(IslandStateMachine.State state:IslandStateMachine.State.values())
            assertTrue(IslandWidgetLayout.visible(state,true));
        assertTrue(IslandWidgetLayout.visible(IslandStateMachine.State.POSSIBLE_SETBACK,false));
        assertFalse(IslandWidgetLayout.visible(IslandStateMachine.State.MODULE_TOAST,false));
        assertTrue(IslandWidgetLayout.visible(IslandStateMachine.State.IDLE,false));
        assertTrue(IslandWidgetLayout.visible(IslandStateMachine.State.TARGET,false));
        assertTrue(IslandWidgetLayout.visible(IslandStateMachine.State.NOTIFICATION,false));
    }

    @Test public void extensionRatioStaysCompactWithInsideWidgetsAndBothBaseDesigns(){
        for(boolean modern:new boolean[]{false,true})for(int count=0;count<=3;count++)
            for(IslandStateMachine.State state:new IslandStateMachine.State[]{IslandStateMachine.State.TARGET,
                    IslandStateMachine.State.NOTIFICATION,IslandStateMachine.State.POSSIBLE_SETBACK}){
                float base=IslandWidgetLayout.idleWidth(modern,count,false);
                IslandLayout.Frame desired=IslandLayout.forState(state,854,480,modern);
                IslandAttachmentLayout layout=new IslandAttachmentLayout(
                        desired.withGeometry(base,desired.height,desired.radius,desired.opacity),base,state);
                assertTrue(layout.detail.width>=layout.base.width*.5f&&layout.detail.width<=layout.base.width*.75f);
                assertEquals(layout.base.x+layout.base.width/2,layout.detail.x+layout.detail.width/2,.0001f);
                assertEquals(34,layout.base.height,0);
            }
    }
}
