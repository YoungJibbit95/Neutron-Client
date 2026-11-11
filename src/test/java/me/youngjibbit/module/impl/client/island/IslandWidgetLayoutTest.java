package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import org.junit.Test;
import static org.junit.Assert.*;

public class IslandWidgetLayoutTest {
    @Test public void detachedCirclesAndCapsuleStayCenteredAndInsideEveryViewport() {
        for(int[] viewport:new int[][]{{1,1},{20,1},{1,20},{40,20},{100,80},{640,360},{854,480},{1920,1080}})
            for(int count=0;count<=3;count++) {
                IslandLayout.Frame desired=IslandLayout.forState(IslandStateMachine.State.IDLE,viewport[0],viewport[1],true);
                IslandWidgetLayout layout=IslandWidgetLayout.capture(desired,count,true);
                assertTrue(Float.isFinite(layout.diameter)&&layout.diameter>0);
                assertTrue(layout.frame.x>=0&&layout.frame.y>=0);
                assertTrue(layout.leftEdge>=-.0001f&&layout.rightEdge<=viewport[0]+.0001f);
                assertEquals(viewport[0]/2f,(layout.leftEdge+layout.rightEdge)/2,.001f);
                assertTrue(layout.y>=0&&layout.y+layout.diameter<=viewport[1]+.0001f);
                if(count>0)assertTrue(layout.x(0)+layout.width(0)<layout.frame.x);
                if(count>1)assertTrue(layout.x(1)>layout.frame.x+layout.frame.width);
                for(int i=0;i<count;i++){
                    assertTrue(layout.x(i)>=-.0001f);
                    assertTrue(layout.x(i)+layout.width(i)<=viewport[0]+.0001f);
                }
                if(viewport[0]>=640)assertEquals(34,layout.diameter,0);
            }
    }
    @Test public void oneGlobalPlacementChoiceAppliesToEveryWidgetWithoutChangingBaseBrandSpace() {
        for(int count=0;count<=3;count++){
            assertEquals(234,IslandWidgetLayout.idleWidth(true,count,true),0);
            assertEquals(234+count*34,IslandWidgetLayout.idleWidth(true,count,false),0);
        }
    }
    @Test public void scaffoldIsNarrowerAndExpandsDownwardWithoutMorphingOnLiveFacts() {
        IslandLayout.Frame idle=IslandLayout.forState(IslandStateMachine.State.IDLE,854,480,true);
        IslandLayout.Frame bridge=IslandLayout.forState(IslandStateMachine.State.SCAFFOLD,854,480,true);
        assertTrue(bridge.width<idle.width);assertEquals(idle.y,bridge.y,0);
        assertEquals(46,bridge.height,0);assertEquals(224,bridge.width,0);
        IslandStateMachine model=new IslandStateMachine();String key=null;
        for(ScaffoldActivitySnapshot.Status status:ScaffoldActivitySnapshot.Status.values()){
            IslandStateMachine.Scaffold view=IslandObservations.scaffold(new ScaffoldActivitySnapshot(
                    status.ordinal(),100,1,0,64-status.ordinal(),"Adaptive Bridge","Godbridge",status,status.ordinal()),100);
            String current=IslandContentKey.of(model.resolve(1,100,null,view,false,true));
            if(key==null)key=current;else assertEquals(key,current);
        }
        assertEquals("SCAFFOLD",key);
    }

    @Test public void allDockPermutationsRemainUniqueFiniteAndCollisionFreeWithExpandedIncidents(){
        for(int[] viewport:new int[][]{{1,1},{40,20},{100,80},{640,360},{1920,1080}})
            for(int a=0;a<6;a++)for(int b=0;b<6;b++)for(int c=0;c<6;c++){
                IslandLayout.Frame frame=IslandLayout.forState(IslandStateMachine.State.IDLE,viewport[0],viewport[1],true);
                IslandWidgetLayout layout=IslandWidgetLayout.capture(frame,3,true,new float[]{34,192,220},new int[]{a,b,c});
                int used=0;
                for(int i=0;i<3;i++){
                    assertEquals(0,used&(1<<layout.dock(i)));used|=1<<layout.dock(i);
                    assertTrue(Float.isFinite(layout.x(i))&&Float.isFinite(layout.width(i)));
                    assertTrue(layout.x(i)>=-.001f&&layout.x(i)+layout.width(i)<=viewport[0]+.001f);
                    assertTrue(layout.width(i)>=layout.diameter);
                    if(layout.left(i))assertTrue(layout.x(i)+layout.width(i)<=layout.frame.x+.001f);
                    else assertTrue(layout.x(i)>=layout.frame.x+layout.frame.width-.001f);
                    for(int j=0;j<i;j++)assertTrue(layout.x(i)+layout.width(i)<=layout.x(j)+.001f
                            ||layout.x(j)+layout.width(j)<=layout.x(i)+.001f);
                }
            }
    }

    @Test public void nearestSlotUsesCircleCentersAndDuplicateSettingsResolveWithoutCollisions(){
        IslandLayout.Frame frame=IslandLayout.forState(IslandStateMachine.State.IDLE,854,480,true);
        IslandWidgetLayout layout=IslandWidgetLayout.capture(frame,3,true,new float[]{34,192,220},new int[]{2,3,4});
        for(int slot=0;slot<6;slot++)
            assertEquals(slot,layout.nearestDock(layout.dockCircleX(slot)+layout.diameter/2));
        assertEquals(layout.x(0)+layout.width(0)-layout.diameter,layout.circleX(0),0);
        assertEquals(layout.x(1),layout.circleX(1),0);
        assertArrayEquals(new int[]{0,1,2},IslandWidgetLayout.resolveDocks(3,new int[]{0,0,0}));
        assertArrayEquals(new int[]{0,3,4},IslandWidgetLayout.resolveDocks(3,new int[]{-1,6,99}));
    }

    @Test public void allSixEditorTargetsStayDistinctAndReachableAtSmallGuiWidths(){
        for(int width:new int[]{320,427})for(int count=1;count<=3;count++)
            for(float[] expanded:new float[][]{{34,34,34},{34,192,220}})
                for(int[] requested:new int[][]{{0,3,4},{0,0,0},{5,5,5}}){
                    IslandLayout.Frame frame=IslandLayout.forState(IslandStateMachine.State.IDLE,width,240,true);
                    IslandWidgetLayout layout=IslandWidgetLayout.capture(frame,count,true,expanded,requested,true);
                    assertTrue(layout.leftEdge>=-.001f&&layout.rightEdge<=width+.001f);
                    for(int slot=0;slot<6;slot++){
                        float x=layout.dockCircleX(slot),center=x+layout.diameter/2;
                        assertTrue("slot remains on screen",x>=-.001f&&x+layout.diameter<=width+.001f);
                        assertEquals("center resolves to its own slot",slot,layout.nearestDock(center));
                        for(int other=0;other<slot;other++)
                            assertTrue("distinct circular drop targets",
                                    Math.abs(x-layout.dockCircleX(other))>=layout.diameter-.001f);
                    }
                    for(int i=0;i<count;i++)
                        assertEquals(layout.circleX(i),layout.dockCircleX(layout.dock(i)),.001f);
                }
        IslandLayout.Frame frame=IslandLayout.forState(IslandStateMachine.State.IDLE,427,240,true);
        assertEquals(34,IslandWidgetLayout.capture(frame,3,true,null,new int[]{0,3,4}).diameter,0);
        assertTrue(IslandWidgetLayout.capture(frame,3,true,null,new int[]{0,3,4},true).diameter<34);
    }
}
