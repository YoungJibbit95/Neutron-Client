package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.impl.ClickModule;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** The actual 2.0 screen reuses the production Panel input and focus engine. */
public class NeutronTwoGuiTest {
    @ModuleInfo(name="VIS03 Modern",category=Category.Combat)
    public static class Dummy extends Module {
        final CheckBoxValue enabled=new CheckBoxValue("Switch",this,false);
        final SliderValue amount=new SliderValue("Amount",this,5d,0,10);
        int enabledCount,disabledCount,valueChanges;
        @Override public void onEnable(){enabledCount++;}
        @Override public void onDisable(){disabledCount++;}
        @Override public void onValueChange(){valueChanges++;}
    }
    private ModuleManager old;
    private String previousTheme;
    private Dummy dummy;
    @Before public void prepare()throws Exception {
        old=Pulsar.INSTANCE.getMm();previousTheme=ThemeMode.selected();
        dummy=new Dummy();
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,new ModuleManager(Arrays.asList(new ClientSettings(),dummy)));
        ThemeMode.select(ThemeMode.APPROACH_TWO);
    }
    @After public void cleanup()throws Exception {
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,old);
        ThemeMode.select(previousTheme);
    }
    private static Object field(Object screen,String name)throws Exception {
        for(Class<?> c=screen.getClass();c!=null;c=c.getSuperclass()){
            try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
            catch(NoSuchFieldException ignored){}
        }
        throw new NoSuchFieldException(name);
    }
    private static class Screen extends NeutronTwoGui {
        void setup(int w,int h){((GuiScreen)this).width=w;((GuiScreen)this).height=h;initGui();}
        void click(int mx,int my,int button)throws Exception{mouseClicked(mx,my,button);}
        void release(){mouseReleased(-1,-1,0);}
        void key(int code)throws Exception{keyTyped(' ',code);}
        void frame(float dt,int mx,int my){advanceMotion(dt,mx,my);}
    }
    @Test public void modernScreenHasDistinctLayoutWithoutChangingLegacyPanelDimensions()throws Exception {
        Screen modern=new Screen();modern.setup(854,480);
        assertTrue(modern.modernPresentation());
        assertEquals(640f,(Float)field(modern,"width"),.01f);
        assertEquals(420f,(Float)field(modern,"height"),.01f);
        float left=(Float)field(modern,"x");
        ClipRect viewport=(ClipRect)field(modern,"viewport");
        assertEquals(left+156,viewport.x,.01f);
        ClickGui legacy=new ClickGui();
        assertFalse(legacy.modernPresentation());
        modern.setup(320,240);
        assertTrue((Float)field(modern,"width")<=304);
        assertTrue((Float)field(modern,"height")<=224);
    }

    @Test public void moduleClickAndKeyboardCategoryChangeAreImmediateWithoutMotionWrites()
            throws Exception {
        Screen screen=new Screen();screen.setup(854,480);
        @SuppressWarnings("unchecked") List<ClickModule> list=(List<ClickModule>)field(screen,"modules");
        assertEquals(1,list.size());
        ClickModule row=list.get(0);
        ClipRect hit=row.presentationBounds();
        int mx=(int)hit.x+14,my=(int)hit.y+12;
        for(int i=0;i<60;i++)screen.frame(1f/60,mx,my);
        assertFalse(dummy.isToggled());
        assertEquals(0,dummy.valueChanges);
        screen.click(mx,my,0);
        assertEquals(1,dummy.enabledCount);
        screen.release();screen.release();assertEquals(1,dummy.enabledCount);
        float x=(Float)field(screen,"x"),y=(Float)field(screen,"y");
        screen.click((int)x+15,(int)y+44+4+32+12,0);
        assertEquals(Category.Movement,field(screen,"selectedCategory"));
        screen.key(Keyboard.KEY_PRIOR);
        assertEquals(Category.Combat,field(screen,"selectedCategory"));
        assertEquals(0,dummy.valueChanges);
        assertEquals(5d,dummy.amount.getValue(),0);
    }

    @Test public void reopeningKeepsLogicalCategoryButDiscardsPointerCapture()throws Exception {
        Screen screen=new Screen();screen.setup(854,480);
        float x=(Float)field(screen,"x"),y=(Float)field(screen,"y");
        screen.click((int)x+15,(int)y+44+4+32+12,0);
        screen.release();
        assertEquals(Category.Movement,field(screen,"selectedCategory"));
        screen.click((int)x+38,(int)y+14,0);
        assertTrue((Boolean)field(screen,"dragging"));
        screen.setup(640,360);
        assertFalse((Boolean)field(screen,"dragging"));
        assertFalse((Boolean)field(screen,"resizing"));
        assertEquals(Category.Movement,field(screen,"selectedCategory"));
        assertEquals(0,dummy.valueChanges);
    }
}
