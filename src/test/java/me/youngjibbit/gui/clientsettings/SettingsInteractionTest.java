package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.clickgui.utils.ClipRect;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.util.Collections;
import static org.junit.Assert.*;

public class SettingsInteractionTest {
    static class Screen extends SettingsMenu {
        boolean control;GuiScreen destination;
        Screen(GuiScreen parent){super(parent);}
        @Override protected boolean ctrl(){return control;}
        @Override protected boolean shift(){return false;}
        @Override protected void open(GuiScreen next){destination=next;}
        void size(int w,int h){width=w;height=h;initGui();}
        void key(int k)throws Exception{keyTyped(' ',k);}
        void type(char c)throws Exception{keyTyped(c,Keyboard.KEY_NONE);}
        void click(ClipRect r,int button)throws Exception{mouseClicked((int)(r.x+8),(int)(r.y+12),button);}
        ClipRect field(int i){return controlBounds(i);}
        void section(int i)throws Exception{click(categoryBounds(i),0);}
        void action(int i){activate(i);}
    }
    ModuleManager previous;ClientSettings settings;Screen screen;
    final GuiScreen parent=new GuiScreen(){};
    @Before public void setup()throws Exception{
        previous=Pulsar.INSTANCE.getMm();settings=new ClientSettings();settings.animations.setValue(false);
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);mm.set(Pulsar.INSTANCE,new ModuleManager(Collections.singletonList(settings)));
        screen=new Screen(parent);screen.size(800,500);
    }
    @After public void restore()throws Exception{
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);mm.set(Pulsar.INSTANCE,previous);
    }
    Object field(String name)throws Exception{Field f=SettingsMenu.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    @Test public void railOpensTypographyAndPopupCommitsOnlyTheChosenOption()throws Exception{
        screen.section(5);screen.click(screen.field(0),0);assertSame(settings.fontFamily,field("popupValue"));
        screen.key(Keyboard.KEY_DOWN);assertEquals("Original",settings.fontFamily.getValue());
        screen.key(Keyboard.KEY_RETURN);assertEquals("DM Sans",settings.fontFamily.getValue());assertNull(field("popupValue"));
        screen.click(screen.field(0),0);screen.key(Keyboard.KEY_DOWN);screen.key(Keyboard.KEY_ESCAPE);
        assertEquals("DM Sans",settings.fontFamily.getValue());assertNull(screen.destination);
    }
    @Test public void keyboardReachesEverySectionAtSmallSize()throws Exception{
        screen.size(334,234);for(int i=0;i<5;i++)screen.key(Keyboard.KEY_F6);
        assertEquals(5,field("group"));screen.key(Keyboard.KEY_RETURN);
        assertSame(settings.fontFamily,field("popupValue"));screen.key(Keyboard.KEY_ESCAPE);
        for(int i=0;i<5;i++)screen.key(Keyboard.KEY_DOWN);
        assertTrue((Float)field("targetScroll")>0);
    }
    @Test public void exactInputRejectsNonFiniteAndGroupChangeCannotRetargetDrag()throws Exception{
        screen.click(screen.field(0),1);screen.control=true;screen.key(Keyboard.KEY_A);screen.control=false;
        for(char c:"NaN".toCharArray())screen.type(c);screen.key(Keyboard.KEY_RETURN);
        assertEquals(5,settings.roundness.getValue(),0);assertEquals(0,field("editing"));
        screen.key(Keyboard.KEY_ESCAPE);assertNull(field("editValue"));
        screen.click(screen.field(0),0);assertSame(settings.roundness,field("dragValue"));
        screen.action(1);assertNull(field("dragValue"));assertEquals(-1,field("drag"));
    }
    @Test public void footerAndParentReturnStayReachable()throws Exception{
        screen.key(Keyboard.KEY_END);for(int i=0;i<6;i++)screen.key(Keyboard.KEY_TAB);screen.key(Keyboard.KEY_RETURN);
        assertSame(parent,screen.destination);
    }
}
