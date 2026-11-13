package me.youngjibbit.utils.render.shader.blur;

import org.junit.Test;
import static org.junit.Assert.*;

public class SceneBlurSizeTest {
    @Test public void oddDimensionsRetainTheLastRowAndNeverBecomeZero(){
        assertEquals(1,SceneBlurSize.dimension(1,4));
        assertEquals(214,SceneBlurSize.dimension(853,4));
        assertEquals(121,SceneBlurSize.dimension(481,4));
        assertEquals(427,SceneBlurSize.dimension(853,2));
        assertEquals(241,SceneBlurSize.dimension(481,2));
        assertEquals(Integer.MAX_VALUE,SceneBlurSize.dimension(Integer.MAX_VALUE,1));
        assertEquals(536870912,SceneBlurSize.dimension(Integer.MAX_VALUE,4));
    }

    @Test public void radiusUsesEachActualAxisRatio(){
        assertEquals(11f,SceneBlurSize.radius(11,853,853),0);
        assertEquals(11f*214/853,SceneBlurSize.radius(11,214,853),.000001);
        assertEquals(11f*121/481,SceneBlurSize.radius(11,121,481),.000001);
        assertNotEquals(SceneBlurSize.radius(11,214,853),SceneBlurSize.radius(11,121,481),.000001);
    }

    @Test public void invalidDimensionsAndUnsupportedScaleAreRejected(){
        for(int divisor:new int[]{0,3,8})try{SceneBlurSize.dimension(800,divisor);fail("divisor accepted");}catch(IllegalArgumentException expected){}
        try{SceneBlurSize.dimension(0,2);fail("zero size accepted");}catch(IllegalArgumentException expected){}
        try{SceneBlurSize.radius(10,801,800);fail("oversized target accepted");}catch(IllegalArgumentException expected){}
    }
}
