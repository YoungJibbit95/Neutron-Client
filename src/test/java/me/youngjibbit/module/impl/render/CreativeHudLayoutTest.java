package me.youngjibbit.module.impl.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class CreativeHudLayoutTest {
    @Test public void targetPortraitAndBodyAreSeparateAndBothInformationRailsFit(){
        for(float request:new float[]{1,196,220,244,999,Float.NaN}){
            CreativeHudLayout.Target g=new CreativeHudLayout.Target(request);
            assertEquals(142,g.width,0);assertEquals(34,g.height,0);
            assertTrue(g.portrait.right()<g.body.x);
            inside(g.portrait,g.width,g.height);inside(g.body,g.width,g.height);
            inside(g.health,g.width,g.height);inside(g.absorption,g.width,g.height);
            assertTrue(g.health.bottom()<g.absorption.y);
            assertEquals(g.health.width,g.absorption.width,0);
        }
    }
    @Test public void sessionHasTwoDisjointBlocksAndTwoActualCounterColumns(){
        CreativeHudLayout.Session g=new CreativeHudLayout.Session();
        assertTrue(g.time.right()<g.statistics.x);
        assertTrue(g.kills.right()<g.deaths.x);
        for(CreativeHudLayout.Rect r:new CreativeHudLayout.Rect[]{g.time,g.statistics,g.kills,g.deaths})
            inside(r,g.width,g.height);
        assertTrue(g.kills.x>=g.statistics.x&&g.deaths.right()<=g.statistics.right());
        assertEquals(154,g.width,0);assertEquals(40,g.height,0);assertTrue(g.width*g.height<250*100/3);
    }
    @Test public void mixedArmorRowsKeepInputMappingAndGroupEmptySlotsBelowOccupiedRows(){
        boolean[] occupied={true,false,true,false,true};float[] labels={24,18,27,20,31};
        CreativeHudLayout.Armor g=new CreativeHudLayout.Armor(false,occupied,labels);
        assertEquals(5,g.cells.length);
        assertEquals(0,g.cells[0].y,0);assertEquals(25,g.cells[2].y,0);assertEquals(50,g.cells[4].y,0);
        assertEquals(g.cells[1].y,g.cells[3].y,0);assertTrue(g.cells[1].y>g.cells[4].bottom());
        assertTrue(g.cells[1].right()<g.cells[3].x);
        for(CreativeHudLayout.Rect r:g.cells)inside(r,g.width,g.height);
    }
    @Test public void horizontalArmorNeverReordersSlotsAndHasNoGroupPadding(){
        CreativeHudLayout.Armor g=new CreativeHudLayout.Armor(true,
                new boolean[]{true,false,true,true,false},new float[]{21,18,500,Float.NaN,20});
        assertEquals(0,g.cells[0].x,0);assertEquals(34,g.height,0);
        for(int i=0;i<g.cells.length;i++){
            inside(g.cells[i],g.width,g.height);
            if(i>0)assertEquals(g.cells[i-1].right()+3,g.cells[i].x,0);
        }
        assertEquals(g.width,g.cells[4].right(),0);
    }
    @Test public void emptyAndMissingArmorStayCompactWithoutSyntheticEquipment(){
        CreativeHudLayout.Armor none=new CreativeHudLayout.Armor(false,new boolean[0],new float[0]);
        assertEquals(0,none.cells.length);assertEquals(0,none.width,0);assertEquals(0,none.height,0);
        for(boolean horizontal:new boolean[]{false,true}){
            CreativeHudLayout.Armor empty=new CreativeHudLayout.Armor(horizontal,
                    new boolean[]{false,false,false,false},new float[]{18,19,20,17});
            assertEquals(20,empty.height,0);
            for(CreativeHudLayout.Rect r:empty.cells)inside(r,empty.width,empty.height);
        }
    }
    @Test public void entireCompositionsFitUniformlyDownToOnePixel(){
        CreativeHudLayout.Target target=new CreativeHudLayout.Target(244);
        CreativeHudLayout.Session session=new CreativeHudLayout.Session();
        for(int w:new int[]{1,2,7,8,16,80,240,640})for(int h:new int[]{1,2,7,8,20,64,360}){
            CreativeHudLayout.Fit tf=target.fit(w,h),sf=session.fit(w,h);
            for(CreativeHudLayout.Rect r:new CreativeHudLayout.Rect[]{target.body,target.portrait,target.health,target.absorption})
                inside(tf.place(r),w,h);
            for(CreativeHudLayout.Rect r:new CreativeHudLayout.Rect[]{session.time,session.statistics,session.kills,session.deaths})
                inside(sf.place(r),w,h);
            assertTrue(tf.scale>0&&tf.scale<=1);assertTrue(sf.scale>0&&sf.scale<=1);
        }
    }
    private static void inside(CreativeHudLayout.Rect r,float width,float height){
        assertTrue(Float.isFinite(r.x)&&Float.isFinite(r.y)&&Float.isFinite(r.width)&&Float.isFinite(r.height));
        assertTrue(r.x>=-.0001f&&r.y>=-.0001f&&r.width>=0&&r.height>=0);
        assertTrue(r.right()<=width+.0001f&&r.bottom()<=height+.0001f);
    }
}
