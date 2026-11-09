package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandNoticeFlowTest {
    private static IslandStateMachine.Selection selection(IslandStateMachine model,long now){
        return model.resolve(1,now,null,null,null,null,false,false,false,false);
    }
    @Test public void observedModuleChangeHasStatusCompositionAndIsNotAFixture(){
        IslandStateMachine model=new IslandStateMachine();IslandNoticeFlow flow=new IslandNoticeFlow();flow.reset(0,0);
        IslandNotice change=IslandNotice.module("speed","§cSpeed",false,1,100);
        flow.update(model,change,null,true,true,1,101);
        IslandPresentation view=IslandPresentation.from(selection(model,101),"Neutron","Dev","12:00",true,true,true);
        assertEquals(IslandStateMachine.State.MODULE_TOAST,view.state);assertEquals("Speed",view.title);
        assertEquals("Disabled",view.detail);assertFalse(view.fixtureOnly);assertSame(change,view.notice);
        assertFalse(view.notice.enabled);assertEquals("",view.brand);assertNull(view.clockFace);
    }
    @Test public void localCorrectionWinsSimultaneousToggleAndNeverClaimsACause(){
        IslandStateMachine model=new IslandStateMachine();IslandNoticeFlow flow=new IslandNoticeFlow();flow.reset(0,0);
        IslandNotice warning=IslandNotice.correction(5,1,100,.5,5);
        flow.update(model,IslandNotice.module("speed","Speed",true,1,100),warning,true,true,1,101);
        IslandPresentation view=IslandPresentation.from(selection(model,101),"Neutron","Dev","",true,true,false);
        assertEquals(IslandStateMachine.State.POSSIBLE_SETBACK,view.state);assertFalse(view.fixtureOnly);
        assertEquals("Possible setback",view.title);assertEquals("Local correction · cause unknown",view.detail);
        assertEquals("0.50 blocks",view.notice.distanceText);assertEquals("5.0°",view.notice.angleText);
        assertEquals(1,model.pendingEventCount());assertEquals(1,model.rememberedEventCount());
    }
    @Test public void repeatedFramesCannotExtendAProducerNoticeOrQueueAnotherOne(){
        IslandStateMachine model=new IslandStateMachine();IslandNoticeFlow flow=new IslandNoticeFlow();flow.reset(0,0);
        IslandNotice change=IslandNotice.module("speed","Speed",true,1,100);
        for(long now=100;now<1_900_000_100L;now+=20_000_000L)flow.update(model,change,null,true,true,1,now);
        assertEquals(IslandStateMachine.State.IDLE,selection(model,1_900_000_100L).state);
        flow.update(model,change,null,true,true,1,1_900_000_101L);
        assertEquals(0,model.pendingEventCount());assertEquals(1,model.rememberedEventCount());
    }
    @Test public void unavailableOrHiddenProducerWithdrawsNoticeWithoutReplayingIt(){
        IslandStateMachine model=new IslandStateMachine();IslandNoticeFlow flow=new IslandNoticeFlow();flow.reset(0,0);
        IslandNotice change=IslandNotice.module("speed","Speed",true,1,100);
        flow.update(model,change,null,true,true,1,101);flow.update(model,null,null,true,true,1,102);
        assertEquals(IslandStateMachine.State.IDLE,selection(model,102).state);
        flow.update(model,change,null,true,true,1,103);assertEquals(IslandStateMachine.State.IDLE,selection(model,103).state);
        IslandNotice hidden=IslandNotice.module("speed","Speed",false,2,104);
        flow.update(model,hidden,null,false,true,1,105);flow.update(model,hidden,null,true,true,1,106);
        assertEquals(IslandStateMachine.State.IDLE,selection(model,106).state);
    }
    @Test public void sceneResetRejectsOldEventsAndNewDiagnosticRunsHaveDistinctIdentity(){
        IslandStateMachine model=new IslandStateMachine();IslandNoticeFlow flow=new IslandNoticeFlow();flow.reset(200,9);
        flow.update(model,IslandNotice.module("speed","Speed",true,9,100),IslandNotice.correction(1,1,100,.5,0),true,true,1,201);
        assertEquals(IslandStateMachine.State.IDLE,selection(model,201).state);
        flow.update(model,null,IslandNotice.correction(2,1,202,.8,0),true,true,1,203);
        assertEquals(IslandStateMachine.State.POSSIBLE_SETBACK,selection(model,203).state);
        flow.update(model,null,null,true,true,1,204);
        assertEquals(IslandStateMachine.State.IDLE,selection(model,204).state);
    }
    @Test public void eachContentCanvasFitsTheActualMorphFrameEvenAtOnePixel(){
        for(IslandStateMachine.State state:new IslandStateMachine.State[]{IslandStateMachine.State.MODULE_TOAST,
                IslandStateMachine.State.POSSIBLE_SETBACK,IslandStateMachine.State.INVENTORY_ACTIVITY})
            for(int[] size:new int[][]{{1,1},{20,1},{40,20},{640,360},{854,480},{1280,720}}){
                IslandLayout.Frame frame=IslandLayout.forState(state,size[0],size[1],true);
                IslandContentLayout canvas=IslandContentLayout.forFrame(frame,state);
                assertTrue(Float.isFinite(canvas.scale)&&canvas.scale>0&&canvas.scale<=1);
                assertTrue(canvas.x>=frame.x-.0001f&&canvas.y>=frame.y-.0001f);
                assertTrue(canvas.x+canvas.width*canvas.scale<=frame.x+frame.width+.0001f);
                assertTrue(canvas.y+canvas.height*canvas.scale<=frame.y+frame.height+.0001f);
            }
        assertEquals(206,IslandContentLayout.width(IslandStateMachine.State.MODULE_TOAST),0);
        assertEquals(72,IslandContentLayout.height(IslandStateMachine.State.POSSIBLE_SETBACK),0);
        assertEquals(42,IslandContentLayout.height(IslandStateMachine.State.INVENTORY_ACTIVITY),0);
    }
}
