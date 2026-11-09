package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.Vis02TestContext;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual module layout methods and AWT font measurements; no native paint is claimed. */
public class CreativeHudCompositionTest {
    private FontManager previousFonts,fonts;
    private Vis02TestContext effects;
    private String previousTheme;
    @Before public void prepare()throws Exception {
        previousFonts=Pulsar.INSTANCE.fontManager;previousTheme=ThemeMode.selected();
        effects=new Vis02TestContext();effects.settings.reducedMotion.setValue(true);
        fonts=new FontManager();Pulsar.INSTANCE.fontManager=fonts;
        Bootstrap.register();ThemeMode.select(ThemeMode.APPROACH_TWO);
    }
    @After public void restore()throws Exception {
        Pulsar.INSTANCE.fontManager=previousFonts;
        if(fonts!=null)fonts.close();if(effects!=null)effects.close();
        if(previousTheme!=null)ThemeMode.select(previousTheme);
    }

    @Test public void actualArmorUsesCompactTabsAndCopiedEquipmentForBothFunctionalLayouts(){
        ArmorHUD hud=new ArmorHUD();
        ItemStack helmet=new ItemStack(Items.diamond_helmet),boots=new ItemStack(Items.diamond_boots);
        helmet.setItemDamage(73);boots.setItemDamage(90);
        ItemStack sword=new ItemStack(Items.diamond_sword);sword.setItemDamage(200);
        ItemStack[] equipment={boots,null,null,helmet};
        hud.getModeSetting("Mode").setValue("Creative");
        for(String orientation:new String[]{"Vertical","Horizontal"}){
            hud.getModeSetting("Layout").setValue(orientation);
            for(String format:new String[]{"Percent","Value","Both","None"}){
                hud.getModeSetting("Durability").setValue(format);
                ArmorHUD.Frame f=hud.layout(equipment,sword,640,360);
                assertEquals("Creative",f.design);assertEquals(5,f.cells.size());
                assertSame(Items.diamond_helmet,f.cells.get(0).item.getItem());
                assertNull(f.cells.get(1).item);assertNull(f.cells.get(2).item);
                assertSame(Items.diamond_boots,f.cells.get(3).item.getItem());
                assertSame(Items.diamond_sword,f.cells.get(4).item.getItem());
                assertNotSame(helmet,f.cells.get(0).item);assertFalse(f.glow);
                assertEquals((helmet.getMaxDamage()-73)/(float)helmet.getMaxDamage(),f.cells.get(0).durabilityRatio,.000001f);
                assertEquals("Horizontal".equals(orientation),f.horizontal);
                assertTrue(f.width*f.scale<=632&&f.height*f.scale<=352);
            }
        }
        assertEquals(73,helmet.getItemDamage());assertEquals(90,boots.getItemDamage());assertEquals(200,sword.getItemDamage());
        hud.getBooleanSetting("Empty Slots").setValue(false);hud.getBooleanSetting("Held Item").setValue(false);
        assertEquals(2,hud.layout(equipment,sword,640,360).cells.size());
        assertTrue(hud.layout(new ItemStack[4],null,640,360).cells.isEmpty());
    }

    @Test public void targetEditorUsesTheSameSmallerCreativeGeometryWithoutChangingSavedMode()throws Exception {
        TargetHUD hud=new TargetHUD();Method preview=TargetHUD.class.getDeclaredMethod("editorPreview",int.class,int.class);
        preview.setAccessible(true);hud.getModeSetting("Mode").setValue("Modern");
        TargetHUD.Frame modern=(TargetHUD.Frame)preview.invoke(hud,640,360);
        hud.getModeSetting("Mode").setValue("Creative");
        TargetHUD.Frame creative=(TargetHUD.Frame)preview.invoke(hud,640,360);
        assertEquals(46,creative.height,0);assertEquals(49,creative.textX,0);
        assertEquals("Preview",creative.kind);assertEquals("Creative",creative.design);
        assertTrue(creative.width*creative.height<modern.width*modern.height);
        assertEquals(9,hud.getModeSetting("Mode").getSelectedIndex());
        for(int viewport:new int[]{1,2,7,8,32,64,240}){
            TargetHUD.Frame f=(TargetHUD.Frame)preview.invoke(hud,viewport,viewport);
            assertTrue(f.width<=Math.max(1,viewport-8));assertTrue(f.height<=Math.max(1,viewport-8));
        }
    }

    @Test public void sessionCreativeUsesRealFrozenTimeAndCountsInSeparateColumns(){
        AtomicLong clock=new AtomicLong();SessionStatistics statistics=new SessionStatistics(clock::get);
        statistics.observe(new Object(),true);clock.set(74_000_000_000L);
        statistics.chat("Bob was slain by Tester","Tester",statistics.generation());
        statistics.chat("Tester was shot by Alice","Tester",statistics.generation());
        SessionStatistics.Snapshot snapshot=statistics.snapshot();SessionInfo hud=new SessionInfo(statistics);
        hud.getModeSetting("Mode").setValue("Modern");SessionInfo.Frame modern=hud.layout(snapshot,"Tester",640,360);
        hud.getModeSetting("Mode").setValue("Creative");SessionInfo.Frame creative=hud.layout(snapshot,"Tester",640,360);
        assertEquals(236,creative.width,0);assertEquals(64,creative.height,0);assertFalse(creative.ring);assertFalse(creative.glow);
        assertSame(snapshot,creative.snapshot);assertTrue(creative.width*creative.height<modern.width*modern.height);
        assertEquals("01:14",creative.text.get(1).text);assertEquals("Tester",creative.text.get(3).text);
        assertEquals("KILLS",creative.text.get(4).text);assertEquals("1",creative.text.get(5).text);
        assertEquals("DEATHS",creative.text.get(6).text);assertEquals("1",creative.text.get(7).text);
        assertTrue(creative.text.get(5).x<creative.text.get(7).x);
        assertEquals("Creative",hud.getModeSetting("Mode").getValue());
        assertEquals(50,hud.getNumberSetting("Position X").getValue(),0);assertEquals(40,hud.getNumberSetting("Position Y").getValue(),0);
    }

    @Test public void realArmorAndSessionLayoutStayInsideTheSmallestBoundedViewport(){
        ArmorHUD armor=new ArmorHUD();armor.getModeSetting("Mode").setValue("Creative");
        SessionInfo session=new SessionInfo(new SessionStatistics(()->0L));session.getModeSetting("Mode").setValue("Creative");
        SessionStatistics.Snapshot snapshot=new SessionStatistics.Snapshot(0,0,0,false);
        for(int size:new int[]{1,2,7,8,16,32,64,240}){
            ArmorHUD.Frame a=armor.layout(new ItemStack[4],null,size,size);
            assertTrue(a.x>=0&&a.y>=0&&a.x+a.width*a.scale<=size+.001f&&a.y+a.height*a.scale<=size+.001f);
            SessionInfo.Frame s=session.layout(snapshot,"A long player name",size,size);
            assertTrue(s.x>=0&&s.y>=0&&s.x+s.width<=size+.001f&&s.y+s.height<=size+.001f);
        }
    }
}
