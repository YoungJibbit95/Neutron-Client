package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class PerimeterOrbitTest {

    @Test public void perimeterLengthIncludesStraightEdgesAndCornerArcs(){
        float w=100,h=50,r=10;
        float p=PerimeterOrbit.perimeter(w,h,r);
        // 2*(w-2r) + 2*(h-2r) + 2*PI*r = 2*80 + 2*30 + 62.83 = 282.83
        float expected=2*(w-2*r)+2*(h-2*r)+(float)(2*Math.PI*r);
        assertEquals(expected,p,0.001f);
    }

    @Test public void perimeterClampsRadiusToHalfOfShortestSide(){
        float w=100,h=20,r=100;
        float p=PerimeterOrbit.perimeter(w,h,r);
        // radius clamped to min(w,h)/2 = 10
        // 2*(100-20) + 2*(20-20) + 2*PI*10 = 160 + 0 + 62.83 = 222.83
        float expected=2*(w-2*10)+2*(20-2*10)+(float)(2*Math.PI*10);
        assertEquals(expected,p,0.001f);
    }

    @Test public void positionStartsAtZeroForPhaseZero(){
        float t=PerimeterOrbit.position(100,50,10,0,PerimeterOrbit.Direction.CLOCKWISE);
        assertEquals(0f,t,0.001f);
    }

    @Test public void positionWrapsAtPhaseOne(){
        float t1=PerimeterOrbit.position(100,50,10,0.999f,PerimeterOrbit.Direction.CLOCKWISE);
        float t2=PerimeterOrbit.position(100,50,10,1.0f,PerimeterOrbit.Direction.CLOCKWISE);
        float t3=PerimeterOrbit.position(100,50,10,0.0f,PerimeterOrbit.Direction.CLOCKWISE);
        assertEquals(t3,t2,0.001f);
        assertTrue(t1>0.99f); // near end of contour
    }

    @Test public void clockwiseAndCounterclockwiseAreOpposite(){
        float w=100,h=50,r=10;
        float p=PerimeterOrbit.perimeter(w,h,r);
        float phase=0.3f;
        float tcw=PerimeterOrbit.position(w,h,r,phase,PerimeterOrbit.Direction.CLOCKWISE);
        float tccw=PerimeterOrbit.position(w,h,r,phase,PerimeterOrbit.Direction.COUNTERCLOCKWISE);
        // CCW at phase p should equal CW at phase (1-p)
        float expected=PerimeterOrbit.position(w,h,r,1f-phase,PerimeterOrbit.Direction.CLOCKWISE);
        assertEquals(expected,tccw,0.001f);
        assertNotEquals(tcw,tccw);
    }

    @Test public void positionIsContinuousAlongContour(){
        float w=100,h=50,r=10;
        float prev=-1;
        int changes=0;
        for(int i=0;i<=200;i++){
            float phase=i/200f;
            float t=PerimeterOrbit.position(w,h,r,phase,PerimeterOrbit.Direction.CLOCKWISE);
            assertTrue(t>=0&&t<=1);
            if(prev>=0 && Math.abs(t-prev)>0.001f) changes++;
            prev=t;
        }
        assertTrue(changes>100); // should change most of the time
    }

    @Test public void zeroRadiusDegeneratesToRectangle(){
        float w=100,h=50,r=0;
        float p=PerimeterOrbit.perimeter(w,h,r);
        assertEquals(2*(w+h),p,0.001f);
        // Phase 0.25 should be near the top-right corner area
        float t=PerimeterOrbit.position(w,h,r,0.25f,PerimeterOrbit.Direction.CLOCKWISE);
        assertTrue(t>=0&&t<=1);
    }

    @Test public void negativePhaseWrapsCorrectly(){
        float w=100,h=50,r=10;
        float t1=PerimeterOrbit.position(w,h,r,-0.1f,PerimeterOrbit.Direction.CLOCKWISE);
        float t2=PerimeterOrbit.position(w,h,r,0.9f,PerimeterOrbit.Direction.CLOCKWISE);
        assertEquals(t2,t1,0.001f);
    }
    @Test public void actualContourSamplesDistinguishEdgesAndRoundCorners() {
        float w=100,h=50,r=10,p=PerimeterOrbit.perimeter(w,h,r),a=(float)Math.PI*r/2;
        assertEquals(40/p,PerimeterOrbit.contour(50,0,w,h,r),.00001f);
        assertEquals((80+a+15)/p,PerimeterOrbit.contour(100,25,w,h,r),.00001f);
        assertEquals((80+30+2*a+40)/p,PerimeterOrbit.contour(50,50,w,h,r),.00001f);
        float c=(float)(r/Math.sqrt(2));
        assertEquals((80+a/2)/p,PerimeterOrbit.contour(90+c,10-c,w,h,r),.00001f);
        assertEquals(0,PerimeterOrbit.contour(10,0,w,h,r),.00001f);
    }
    @Test public void travelFollowsSpatialSamplesAndKeepsScaleAndDirection() {
        float phase=PerimeterOrbit.contour(100,25,100,50,10);
        assertEquals(1,PerimeterOrbit.sample(100,25,100,50,10,phase,PerimeterOrbit.Direction.CLOCKWISE),.00001f);
        assertEquals(1,PerimeterOrbit.sample(100,25,100,50,10,-phase,PerimeterOrbit.Direction.COUNTERCLOCKWISE),.00001f);
        assertNotEquals(PerimeterOrbit.sample(50,0,100,50,10,.17f,PerimeterOrbit.Direction.CLOCKWISE),
                PerimeterOrbit.sample(50,50,100,50,10,.17f,PerimeterOrbit.Direction.CLOCKWISE),.001f);
        assertEquals(PerimeterOrbit.sample(50,0,100,50,10,.17f,PerimeterOrbit.Direction.CLOCKWISE),
                PerimeterOrbit.sample(100,0,200,100,20,.17f,PerimeterOrbit.Direction.CLOCKWISE),.00001f);
    }
    @Test public void actualUiGradientConsumerUsesSpatialContourAndThePassedRadius() {
        UiEffects.Gradient gradient=new UiEffects.Gradient(0,5,.17f,.2f,1f);
        assertEquals(PerimeterOrbit.sample(90,0,100,50,10,.17f,PerimeterOrbit.Direction.CLOCKWISE),
                gradient.position(90,0,100,50,10),.00001f);
        assertNotEquals(gradient.position(10,0,100,50,10),gradient.position(90,0,100,50,10),.001f);
    }

}
