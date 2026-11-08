package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure silhouettes: no GL context, fake telemetry or Minecraft world. */
public class Wave07IslandVisualTest {
    @Test public void legacyGeometryIsExactlyPreservedByTheNewOverload(){
        for(int[] size:new int[][]{{1,1},{40,20},{100,80},{640,360},{854,480},{1280,720},{1920,1080}}){
            for(IslandStateMachine.State state:IslandStateMachine.State.values()){
                IslandLayout.Frame old=IslandLayout.forState(state,size[0],size[1]);
                IslandLayout.Frame legacy=IslandLayout.forState(state,size[0],size[1],false);
                assertEquals(old.x,legacy.x,0);assertEquals(old.y,legacy.y,0);
                assertEquals(old.width,legacy.width,0);assertEquals(old.height,legacy.height,0);
                assertEquals(old.radius,legacy.radius,0);assertEquals(old.opacity,legacy.opacity,0);
            }
        }
    }

    @Test public void modernCapsuleFitsAllViewportsIncludingTinyScreens(){
        for(int[] size:new int[][]{{1,1},{40,20},{100,80},{640,360},{854,480},{1280,720},{1920,1080}}){
            for(IslandStateMachine.State state:IslandStateMachine.State.values()){
                IslandLayout.Frame frame=IslandLayout.forState(state,size[0],size[1],true);
                assertTrue(Float.isFinite(frame.x)&&Float.isFinite(frame.y));
                assertTrue(frame.x>=0&&frame.y>=0);
                assertTrue(frame.x+frame.width<=size[0]+.0001f);
                assertTrue(frame.y+frame.height<=size[1]+.0001f);
                assertTrue(frame.width<=size[0]*.85f+.0001f);
                assertTrue(frame.radius<=frame.height/2f);
            }
        }
    }
    @Test public void idleAndVerifiedTargetHaveDistinctModernCompositions(){
        IslandLayout.Frame idle=IslandLayout.forState(IslandStateMachine.State.IDLE,854,480,true);
        IslandLayout.Frame target=IslandLayout.forState(IslandStateMachine.State.TARGET,854,480,true);
        assertEquals(234,idle.width,0);assertEquals(34,idle.height,0);
        assertEquals(266,target.width,0);assertEquals(52,target.height,0);
        assertEquals((854-idle.width)/2,idle.x,0);
        assertTrue(target.width>idle.width&&target.height>idle.height);
        assertEquals(17,idle.radius,0);
    }
    @Test public void unknownStateIsNeverAssumedToBeAnAnticheatWarning(){
        IslandLayout.Frame unknown=IslandLayout.forState(null,640,360,true);
        IslandLayout.Frame idle=IslandLayout.forState(IslandStateMachine.State.IDLE,640,360,true);
        assertEquals(idle.width,unknown.width,0);
        assertEquals(idle.height,unknown.height,0);
    }
}
