package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandExactSilhouetteTest {
    @Test public void restingWidgetIsMathematicallyCircularAtEveryAngle(){
        for(float diameter:new float[]{1,12,34,64}){
            float radius=diameter/2;
            ArrayListSurface.Shape circle=ArrayListSurface.Shape.roundedRectangle(20,40,diameter,diameter,radius);
            for(int i=0;i<720;i++){
                double angle=i*Math.PI/360;
                assertTrue(circle.contains(20+radius+(float)Math.cos(angle)*radius*.999f,
                        40+radius+(float)Math.sin(angle)*radius*.999f));
                assertFalse(circle.contains(20+radius+(float)Math.cos(angle)*radius*1.001f,
                        40+radius+(float)Math.sin(angle)*radius*1.001f));
            }
        }
    }
    @Test public void expandedBarKeepsCircularEndsWithoutChangingLegacyShapes(){
        ArrayListSurface.Shape capsule=ArrayListSurface.Shape.roundedRectangle(10,20,190,34,17);
        assertTrue(capsule.contains(95,20.01f));assertTrue(capsule.contains(95,53.99f));
        assertFalse(capsule.contains(10.1f,20.1f));assertTrue(capsule.contains(10.01f,37));
        ArrayListSurface.Shape legacy=new ArrayListSurface.Shape(0,new float[]{0},new float[]{34},new float[]{34},17);
        for(int i=2;i<legacy.corners.length;i+=4)assertEquals(34*.45f,Math.abs(legacy.corners[i]),.0001f);
    }
}
