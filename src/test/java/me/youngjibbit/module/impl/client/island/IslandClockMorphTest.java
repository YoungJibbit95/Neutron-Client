package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.gui.IslandMorphMotion;
import org.junit.Test;
import static org.junit.Assert.*;

public class IslandClockMorphTest {
    private static void bounded(IslandLayout.Frame frame){
        assertTrue(Float.isFinite(frame.width)&&Float.isFinite(frame.height));
        assertTrue(frame.width>0&&frame.height>0&&frame.x>=0&&frame.y>=0);
        assertTrue(frame.x+frame.width<=frame.viewportWidth+.0001f);
        assertTrue(frame.y+frame.height<=frame.viewportHeight+.0001f);
        assertTrue(frame.radius>=0&&frame.radius<=Math.min(frame.width,frame.height)/2);
    }
    @Test public void entireClockFrameIsCircularEvenInTheSmallestViewport(){
        for(boolean modern:new boolean[]{false,true})
            for(int[] viewport:new int[][]{{1,1},{1,20},{20,1},{40,20},{100,80},{640,360},{854,480},{1280,720},{1920,1080}}){
                IslandLayout.Frame clock=IslandLayout.forState(IslandStateMachine.State.CLOCK_REMINDER,viewport[0],viewport[1],modern);
                bounded(clock);assertEquals(clock.width,clock.height,0);assertEquals(clock.width/2,clock.radius,0);
                assertEquals((clock.viewportWidth-clock.width)/2,clock.x,.0001f);
                if(viewport[0]>=100&&viewport[1]>=80)assertEquals(64,clock.width,0);
            }
    }
    @Test public void existingMotionMorphsTheWholeIslandToClockAndBackAtEveryFrameRate(){
        for(int fps:new int[]{30,60,144,240}){
            IslandMorphMotion motion=new IslandMorphMotion();
            IslandLayout.Frame idle=IslandLayout.forState(IslandStateMachine.State.IDLE,854,480,true);
            IslandLayout.Frame clock=IslandLayout.forState(IslandStateMachine.State.CLOCK_REMINDER,854,480,true);
            motion.sample(idle,0,true,false,"Balanced");motion.contentChanged();
            IslandLayout.Frame frame=idle;
            for(int i=0;i<fps*2;i++){frame=motion.sample(clock,1f/fps,true,false,"Balanced");bounded(frame);}
            assertEquals(64,frame.width,.001f);assertEquals(64,frame.height,.001f);assertEquals(32,frame.radius,.001f);
            motion.contentChanged();
            for(int i=0;i<fps*2;i++){frame=motion.sample(idle,1f/fps,true,false,"Balanced");bounded(frame);}
            assertEquals(idle.width,frame.width,.001f);assertEquals(idle.height,frame.height,.001f);
        }
    }
    @Test public void reducedMotionAndAnimationsOffSnapToTheCompleteCircle(){
        for(boolean reduced:new boolean[]{false,true}){
            IslandMorphMotion motion=new IslandMorphMotion();
            motion.sample(IslandLayout.forState(IslandStateMachine.State.IDLE,640,360,true),0,true,false,"Expressive");
            motion.contentChanged();
            IslandLayout.Frame frame=motion.sample(IslandLayout.forState(IslandStateMachine.State.CLOCK_REMINDER,640,360,true),
                    0,reduced,reduced,"Expressive");
            bounded(frame);assertEquals(64,frame.width,0);assertEquals(64,frame.height,0);
            assertEquals(32,frame.radius,0);assertEquals(1,motion.contentAlpha(),0);
        }
    }
    @Test public void repeatedResizesAndClockReversalsNeverEscapeTheSafeArea(){
        IslandMorphMotion motion=new IslandMorphMotion();
        int[][] sizes={{1,1},{40,20},{854,480},{20,1},{1280,720}};
        for(int i=0;i<100;i++){
            int[] size=sizes[i%sizes.length];
            IslandStateMachine.State state=i%2==0?IslandStateMachine.State.CLOCK_REMINDER:IslandStateMachine.State.IDLE;
            bounded(motion.sample(IslandLayout.forState(state,size[0],size[1],true),.016f,true,false,"Expressive"));
        }
    }
}
