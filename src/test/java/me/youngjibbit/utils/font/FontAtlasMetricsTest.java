package me.youngjibbit.utils.font;

import org.junit.Test;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class FontAtlasMetricsTest {
    @Test public void unicodeAndSupplementaryCharactersHaveVisibleAdvances(){
        try(TTFFontRenderer font=new TTFFontRenderer(new Font(Font.DIALOG,Font.PLAIN,20))){
            assertTrue(font.getWidth("Привет")>font.getWidth("P"));
            assertTrue(font.getWidth("中文")>0);
            assertTrue(font.getWidth("\ud83d\ude00")>0);
            assertEquals(0,font.getWidth(""),0);
        }
    }
    @Test public void formattingAndNewlinesUseOneMeasurementContract(){
        try(TTFFontRenderer font=new TTFFontRenderer(new Font(Font.DIALOG,Font.PLAIN,20))){
            assertEquals(font.getWidth("Neutron"),font.getWidth("§aNeutron§r"),.0001);
            assertEquals(font.getWidth("wide label"),font.getWidth("x\nwide label"),.0001);
            assertEquals(font.getLineHeight()*2,font.getHeight("x\ny"),.0001);
            assertEquals(font.getWidth("  ")*2,font.getWidth("\t"),.0001);
            assertTrue(font.getWidth("tail§")>font.getWidth("tail"));
        }
    }
    @Test public void metricWorkersDoNotNeedAContextAndClosedFontsRejectAccess()throws Exception{
        TTFFontRenderer font=new TTFFontRenderer(new Font(Font.DIALOG,Font.PLAIN,18));
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread worker=new Thread(()->{try{for(int i=0;i<100;i++)assertTrue(font.getWidth("§l§oAtlas Ω")>0);}catch(Throwable t){error.set(t);}});
        worker.start();worker.join();assertNull(error.get());font.close();
        try{font.getWidth("closed");fail();}catch(IllegalStateException expected){}
    }
}
