package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandTargetCompositionTest {
    private static IslandPresentation view(IslandStateMachine.Selection selection){
        return IslandPresentation.from(selection,"Neutron","test","12:00",true,true,true);
    }
    private static IslandTargetComposition target(float hp,float max,float absorption,float time,boolean motion){
        return IslandTargetComposition.capture(view(new IslandStateMachine().resolve(7,100,
                new IslandStateMachine.Target(4,"Enemy",hp,max,absorption),null,true,false)),time,motion);
    }

    @Test public void frozenNameHealthAndAbsorptionOwnTheActualComposition(){
        IslandPresentation source=view(new IslandStateMachine().resolve(7,100,
                new IslandStateMachine.Target(4,"\u00a7cEnemy",15,40,12),null,true,false));
        IslandTargetComposition captured=IslandTargetComposition.capture(source,.3f,true);
        assertNotNull(captured);assertEquals("Enemy",captured.name);
        assertEquals("15.0 / 40.0 HP +12.0",captured.healthText);
        assertEquals(source.healthFraction,captured.healthFraction,0);
        assertEquals(source.absorptionFraction,captured.absorptionFraction,0);
        assertEquals(116*.375f,captured.healthWidth,0);assertEquals(116*.3f,captured.absorptionWidth,0);
        assertEquals(.1f,target(2,20,0,9,true).healthFraction,0);
        assertEquals(.375f,captured.healthFraction,0);assertEquals(.3f,captured.absorptionFraction,0);
        assertEquals("15.0 / 40.0 HP +12.0",source.detail);
    }

    @Test public void zeroFullAndQuarterHealthUseExactSweepsAndSeparateAbsorption(){
        IslandTargetComposition empty=target(0,20,0,0,false);
        assertEquals(0,empty.health.segments);assertEquals(0,empty.absorption.segments);
        assertEquals(0,empty.healthWidth,0);assertEquals(0,empty.absorptionWidth,0);
        IslandTargetComposition quarter=target(5,20,0,0,false);
        assertEquals(12,quarter.health.segments);assertEquals((float)Math.PI/2,quarter.health.sweep,.000001f);
        assertEquals(24,quarter.health.x(0),.00001f);assertEquals(5,quarter.health.y(0),.00001f);
        assertEquals(41,quarter.health.x(12),.00001f);assertEquals(22,quarter.health.y(12),.00001f);
        IslandTargetComposition full=target(20,20,40,0,false);
        assertEquals(48,full.health.segments);assertEquals(48,full.absorption.segments);
        assertEquals(full.health.x(0),full.health.x(48),.00001f);
        assertEquals(full.health.y(0),full.health.y(48),.00001f);
        assertEquals(116,full.healthWidth,0);assertEquals(116,full.absorptionWidth,0);
        assertEquals("20.0 / 20.0 HP +40.0",full.healthText);
        assertNotEquals(full.health.radius,full.absorption.radius,0);
    }

    @Test public void missingHiddenOrHigherPrioritySourcesNeverRetainTarget(){
        assertNull(IslandTargetComposition.capture(null,0,true));
        IslandStateMachine machine=new IslandStateMachine();
        IslandStateMachine.Target observed=new IslandStateMachine.Target(4,"Enemy",15,20,0);
        assertNotNull(IslandTargetComposition.capture(view(machine.resolve(7,100,observed,null,true,false)),0,true));
        assertNull(IslandTargetComposition.capture(view(machine.resolve(7,101,null,null,true,false)),0,true));
        assertNull(IslandTargetComposition.capture(view(machine.resolve(7,102,observed,null,false,false)),0,true));
        assertTrue(machine.offer(IslandStateMachine.State.POSSIBLE_SETBACK,"warning","Observed",7,103));
        IslandStateMachine.Selection warning=machine.resolve(7,104,observed,null,true,false);
        assertEquals(IslandStateMachine.State.POSSIBLE_SETBACK,warning.state);
        assertNull(IslandTargetComposition.capture(view(warning),0,true));
        machine.reset();
        assertNull(IslandTargetComposition.capture(view(machine.resolve(8,105,null,null,true,false)),0,true));
    }

    @Test public void motionOnlyChangesReticleAndDisabledOrReducedIsStatic(){
        IslandTargetComposition still=target(15,40,12,0,false);
        assertNotEquals(target(15,40,12,0,true).reticleAlpha,target(15,40,12,.37f,true).reticleAlpha,0);
        for(float time:new float[]{-20,0,.37f,10,100000,Float.NaN,Float.POSITIVE_INFINITY}){
            IslandTargetComposition frozen=target(15,40,12,time,false);
            assertEquals(1,frozen.reticleAlpha,0);
            for(int i=0;i<still.reticleSegments();i++){
                assertEquals(still.x1(i),frozen.x1(i),0);assertEquals(still.y1(i),frozen.y1(i),0);
                assertEquals(still.x2(i),frozen.x2(i),0);assertEquals(still.y2(i),frozen.y2(i),0);
            }
        }
        for(int frame=0;frame<240;frame++){
            IslandTargetComposition current=target(15,40,12,frame/60f,true);
            assertEquals(still.health.sweep,current.health.sweep,0);assertEquals(still.absorption.sweep,current.absorption.sweep,0);
            assertEquals(still.healthWidth,current.healthWidth,0);assertEquals(still.absorptionWidth,current.absorptionWidth,0);
            assertTrue(current.reticleAlpha>=.8f&&current.reticleAlpha<=1);
            for(int i=0;i<current.reticleSegments();i++){
                assertTrue(current.x1(i)>=4&&current.x1(i)<=44);assertTrue(current.x2(i)>=4&&current.x2(i)<=44);
                assertTrue(current.y1(i)>=2&&current.y1(i)<=42);assertTrue(current.y2(i)>=2&&current.y2(i)<=42);
            }
        }
    }

    @Test public void arcsTextAndBothRailsFitActualClipAtOnePixelAndDuringMorph(){
        IslandTargetComposition target=target(17,20,6,.37f,true);
        for(boolean modern:new boolean[]{false,true})for(int[] viewport:new int[][]{
                {1,1},{1,20},{20,1},{40,20},{100,80},{640,360},{854,480},{1920,1080}})
            for(IslandStateMachine.State state:new IslandStateMachine.State[]{
                    IslandStateMachine.State.IDLE,IslandStateMachine.State.TARGET,IslandStateMachine.State.CLOCK_REMINDER}){
                IslandLayout.Frame frame=IslandLayout.forState(state,viewport[0],viewport[1],modern);
                for(float fraction:new float[]{.2f,.5f,1}){
                    IslandLayout.Frame morph=frame.withGeometry(frame.width*fraction,frame.height*fraction,frame.radius,1);
                    IslandTargetComposition.Layout layout=new IslandTargetComposition.Layout(morph);
                    assertTrue(Float.isFinite(layout.scale)&&layout.scale>0&&layout.scale<=1);
                    inside(layout,morph,0,0);inside(layout,morph,172,44);
                    for(IslandTargetComposition.Arc arc:new IslandTargetComposition.Arc[]{target.health,target.absorption})
                        for(int point=0;point<=arc.segments;point++)inside(layout,morph,arc.x(point),arc.y(point));
                    for(int i=0;i<target.reticleSegments();i++){
                        inside(layout,morph,target.x1(i),target.y1(i));inside(layout,morph,target.x2(i),target.y2(i));
                    }
                    inside(layout,morph,48+116,6+11);inside(layout,morph,48+116,19+9);
                    inside(layout,morph,48+target.healthWidth,33+3);inside(layout,morph,48+target.absorptionWidth,39+2);
                }
            }
        assertTrue(44<IslandTargetComposition.Layout.TEXT_X);
        assertTrue(IslandTargetComposition.Layout.NAME_Y+IslandTargetComposition.Layout.NAME_HEIGHT<IslandTargetComposition.Layout.DETAIL_Y);
        assertTrue(IslandTargetComposition.Layout.DETAIL_Y+IslandTargetComposition.Layout.DETAIL_HEIGHT<IslandTargetComposition.Layout.HEALTH_Y);
        assertTrue(IslandTargetComposition.Layout.HEALTH_Y+IslandTargetComposition.Layout.HEALTH_HEIGHT<IslandTargetComposition.Layout.ABSORPTION_Y);
    }
    private static void inside(IslandTargetComposition.Layout layout,IslandLayout.Frame frame,float x,float y){
        float sx=layout.screenX(x),sy=layout.screenY(y);
        assertTrue(Float.isFinite(sx)&&Float.isFinite(sy));
        assertTrue(sx>=frame.x+layout.inset-.0001f&&sx<=frame.x+frame.width-layout.inset+.0001f);
        assertTrue(sy>=frame.y+layout.inset-.0001f&&sy<=frame.y+frame.height-layout.inset+.0001f);
    }
}

