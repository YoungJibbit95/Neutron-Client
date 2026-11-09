package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

/** Real contour/domain and world-palette consumers; no OpenGL context is required. */
public class PerimeterConsumerTest {
    private static ArrayListSurface.Shape panel() {
        return new ArrayListSurface.Shape(40,new float[]{60},new float[]{100},new float[]{160},30);
    }
    private static UiEffects.Gradient orbit(float phase,int axis) {
        return new UiEffects.Gradient(axis,5,phase,.2f,1);
    }

    @Test public void renderedPanelBoundsAndClampedRadiusOwnTheOrbitSample() {
        ArrayListSurface.Shape shape=panel();
        UiEffects.Gradient gradient=orbit(.17f,0);
        assertEquals(18,Math.abs(shape.corners[2]),0);
        try(UiEffects.GradientSpace parent=UiEffects.gradientSpace(10,20,400,300)) {
            float before=UiEffects.gradientPosition(gradient,152,62,1920,1080,5);
            try(UiEffects.GradientSpace component=shape.gradientSpace()) {
                float actual=UiEffects.gradientPosition(gradient,152,62,1920,1080,5);
                assertEquals(gradient.position(112,2,120,40,18),actual,.000001f);
                assertNotEquals("the global roundness must not replace the rendered contour radius",
                        gradient.position(112,2,120,40,5),actual,.001f);
                assertNotEquals("the screen/category is not this panel's contour",before,actual,.001f);
            }
            assertEquals(before,UiEffects.gradientPosition(gradient,152,62,1920,1080,5),0);
        }
    }

    @Test public void translatedPanelKeepsTheSameLocalContour() {
        ArrayListSurface.Shape shape=panel();
        UiEffects.Gradient gradient=orbit(.31f,-1);
        float before;
        try(UiEffects.GradientSpace component=shape.gradientSpace()) {
            before=UiEffects.gradientPosition(gradient,152,62,1920,1080,5);
        }
        try(UiEffects.GradientSpace moved=shape.translated(350,-20).gradientSpace()) {
            assertEquals(before,UiEffects.gradientPosition(gradient,502,42,1920,1080,5),0);
        }
    }

    @Test public void nestedContourRestoresItsParentAfterAContentFailure() {
        UiEffects.Gradient gradient=orbit(.17f,0);
        ArrayListSurface.Shape nested=new ArrayListSurface.Shape(60,new float[]{70},
                new float[]{90},new float[]{120},4);
        RuntimeException marker=new RuntimeException("synthetic content failure");
        try(UiEffects.GradientSpace outer=panel().gradientSpace()) {
            float before=UiEffects.gradientPosition(gradient,100,70,1920,1080,5);
            try(UiEffects.GradientSpace inner=nested.gradientSpace()) {
                assertEquals(gradient.position(40,0,60,20,4),
                        UiEffects.gradientPosition(gradient,100,70,1920,1080,5),.000001f);
                throw marker;
            } catch(RuntimeException failure) {
                assertSame(marker,failure);
            }
            assertEquals(before,UiEffects.gradientPosition(gradient,100,70,1920,1080,5),0);
        }
        assertEquals(gradient.position(100,70,1920,1080,5),
                UiEffects.gradientPosition(gradient,100,70,1920,1080,5),0);
    }

    @Test public void nonOrbitChannelsKeepTheirCategoryDomainInsideAComponent() {
        try(UiEffects.GradientSpace category=UiEffects.gradientSpace(10,20,400,300)) {
            for(int mode:new int[]{0,1,2,3,4,6,7,8})for(int axis:new int[]{0,1,-1,2,-2}) {
                UiEffects.Gradient gradient=new UiEffects.Gradient(axis,mode,.23f,.8f,.6f);
                float before=UiEffects.gradientPosition(gradient,152,62,1920,1080,5);
                try(UiEffects.GradientSpace component=panel().gradientSpace()) {
                    assertEquals("mode "+mode+" axis "+axis,before,
                            UiEffects.gradientPosition(gradient,152,62,1920,1080,5),0);
                }
            }
        }
    }

    @Test public void worldOrbitTraversesEveryAngleWithDirectionAndAClosedSeam() {
        for(int axis:new int[]{0,-1})for(float phase:new float[]{0,.125f,.35f}) {
            UiEffects.Gradient gradient=orbit(phase,axis);
            for(int i=0;i<=WorldVisuals.SEGMENTS;i++) {
                double contour=.25+i/(double)WorldVisuals.SEGMENTS;
                double travel=axis<0?-phase:phase;
                float expected=.5f+.5f*(float)Math.cos(2*Math.PI*(contour-travel));
                assertEquals("ring angle "+i,expected,WorldVisuals.ringGradientPosition(gradient,i),.000002f);
            }
            assertEquals(WorldVisuals.ringGradientPosition(gradient,0),
                    WorldVisuals.ringGradientPosition(gradient,WorldVisuals.SEGMENTS),0);
        }
        UiEffects.Gradient gradient=orbit(.125f,0);
        assertNotEquals("opposite travel halves must not mirror the same input",
                WorldVisuals.ringGradientPosition(gradient,8),
                WorldVisuals.ringGradientPosition(gradient,56),.01f);
    }

    @Test public void everyOtherWorldModeRetainsItsExactMirroredSamples() {
        for(int mode:new int[]{0,1,2,3,4,6,7,8})for(int axis:new int[]{0,1,-1,2,-2})
            for(float phase:new float[]{0,.23f,.75f})for(int i=0;i<=WorldVisuals.SEGMENTS;i++) {
                UiEffects.Gradient gradient=new UiEffects.Gradient(axis,mode,phase,.8f,.6f);
                float t=2*Math.min(i,WorldVisuals.SEGMENTS-i)/(float)WorldVisuals.SEGMENTS;
                assertEquals(gradient.position(t,t,1,1,.5f),
                        WorldVisuals.ringGradientPosition(gradient,i),0);
            }
    }
}
