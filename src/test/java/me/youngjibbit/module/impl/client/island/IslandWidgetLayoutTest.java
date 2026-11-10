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
                float right=count==0?layout.frame.x+layout.frame.width:layout.x(count-1)+layout.diameter;
                assertTrue(right<=viewport[0]+.0001f);
                assertEquals(viewport[0]/2f,(layout.frame.x+right)/2,.001f);
                assertTrue(layout.y>=0&&layout.y+layout.diameter<=viewport[1]+.0001f);
                if(count>0)assertTrue(layout.x(0)>layout.frame.x+layout.frame.width);
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
}
