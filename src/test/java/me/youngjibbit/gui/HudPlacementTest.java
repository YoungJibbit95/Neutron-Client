package me.youngjibbit.gui;

import org.junit.Test;
import static org.junit.Assert.*;

public class HudPlacementTest {
    @Test public void untouchedTargetHudMatchesOldAutoAnchor() {
        for(int[] screen:new int[][]{{640,360},{854,480},{1280,720}}){
            HudPlacement.Point point=HudPlacement.location(0,0,false,screen[0],screen[1],180,44,true);
            assertEquals(screen[0]/2f+25,point.x,0);
            assertEquals(screen[1]/2f-35,point.y,0);
        }
    }
    @Test public void draggingUsesGlobalGridAndClampsBothAxes() {
        HudPlacement.Point p=HudPlacement.drag(157,187,5,7,8,640,360,180,44,true);
        assertEquals(152,p.x,0);assertEquals(184,p.y,0);
        HudPlacement.Point left=HudPlacement.drag(-100,-100,5,7,8,640,360,180,44,true);
        assertEquals(4,left.x,0);assertEquals(4,left.y,0);
        HudPlacement.Point right=HudPlacement.drag(20000,20000,5,7,8,640,360,180,44,true);
        assertEquals(456,right.x,0);assertEquals(312,right.y,0);
    }
    @Test public void smallViewportsStayInsideAndBadInputsAreFinite() {
        for(int[] screen:new int[][]{{1,1},{40,20},{640,360}}){
            float width=Math.max(1,screen[0]-8),height=Math.max(1,screen[1]-8);
            HudPlacement.Point p=HudPlacement.location(Float.NaN,Float.POSITIVE_INFINITY,true,
                    screen[0],screen[1],width,height,true);
            assertTrue(Float.isFinite(p.x)&&Float.isFinite(p.y));
            assertTrue(p.x>=0&&p.y>=0);
            assertTrue(p.x+width<=screen[0]+.0001f);
            assertTrue(p.y+height<=screen[1]+.0001f);
        }
    }
    @Test public void gridOffKeepsSubpixelInputAndUnboundedModeDoesNotClamp() {
        HudPlacement.Point p=HudPlacement.drag(54.5f,88.25f,4.25f,8.5f,0,640,360,150,44,true);
        assertEquals(50.25f,p.x,.00001f);assertEquals(79.75f,p.y,.00001f);
        HudPlacement.Point off=HudPlacement.drag(-100,-80,0,0,8,640,360,150,44,false);
        assertEquals(-104,off.x,0);assertEquals(-80,off.y,0);
    }
}
