package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.clickgui.utils.ClipRect;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

public class SideGuiInteractionTest {
    @ModuleInfo(name="Alpha",description="First searchable module",category=Category.Render)
    public static class Alpha extends Module { final CheckBoxValue setting=new CheckBoxValue("Unique setting",this,false); }
    @ModuleInfo(name="Beta",description="Second searchable module",category=Category.Render)
    public static class Beta extends Module {}
    @ModuleInfo(name="Gamma",description="Third searchable module",category=Category.Render)
    public static class Gamma extends Module {}
    static class Screen extends SideGuiScreen {
        GuiScreen routed; boolean control;
        Screen(GuiScreen parent){super(parent);}
        @Override protected boolean ctrl(){return control;}
        @Override protected boolean shift(){return false;}
        @Override protected void open(GuiScreen next){routed=next;}
        void key(int key)throws Exception{keyTyped(' ',key);}
        void type(char c)throws Exception{keyTyped(c,Keyboard.KEY_NONE);}
        void search(String text){query(text);}
        void resize(int w,int h){width=w;height=h;initGui();}
        void clickResult(int index,int button)throws Exception{ClipRect b=resultBounds(index);mouseClicked((int)(b.x+8),(int)(b.y+10),button);}
        void wheel(int amount){ClipRect b=listBounds();scrollAt((int)b.x+4,(int)b.y+4,amount);}
    }
    private ModuleManager previous;
    private Screen screen;
    private Alpha alpha;private Beta beta;private Gamma gamma;
    private GuiScreen parent=new GuiScreen(){};
    @Before public void setup()throws Exception{
        previous=Pulsar.INSTANCE.getMm();alpha=new Alpha();beta=new Beta();gamma=new Gamma();
        ClientSettings c=new ClientSettings();c.animations.setValue(false);
        Field f=Pulsar.class.getDeclaredField("mm");f.setAccessible(true);f.set(Pulsar.INSTANCE,new ModuleManager(Arrays.asList(alpha,beta,gamma,c)));
        screen=new Screen(parent);screen.resize(480,320);
    }
    @After public void restore()throws Exception{
        screen.onGuiClosed();for(Module m:Arrays.asList(alpha,beta,gamma))if(m.isToggled())m.setToggled(false);
        Field f=Pulsar.class.getDeclaredField("mm");f.setAccessible(true);f.set(Pulsar.INSTANCE,previous);
    }
    private Object field(String name)throws Exception{Field f=SideGuiScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    @Test public void searchesValueNamesAndHandlesEmptyResults()throws Exception{
        screen.search("unique setting");assertEquals(Collections.singletonList(alpha),field("results"));
        screen.search("no such module");assertTrue(((List<?>)field("results")).isEmpty());assertNull(field("editor"));
        screen.key(Keyboard.KEY_DOWN);screen.key(Keyboard.KEY_RETURN);screen.key(Keyboard.KEY_SPACE);
    }
    @Test public void arrowsSelectResultsWithoutTogglingAndEnterOpensEditor()throws Exception{
        screen.search("render");screen.key(Keyboard.KEY_DOWN);screen.key(Keyboard.KEY_DOWN);assertSame(beta,field("selected"));
        assertFalse(alpha.isToggled());assertFalse(beta.isToggled());screen.key(Keyboard.KEY_RETURN);
        assertSame(field("editor"),field("focus"));screen.key(Keyboard.KEY_SPACE);assertTrue(beta.isToggled());
    }
    @Test public void tabVisitsSearchResultsBeforeSettings()throws Exception{
        screen.key(Keyboard.KEY_TAB);assertEquals(true,field("listFocus"));screen.key(Keyboard.KEY_TAB);assertSame(field("editor"),field("focus"));
    }
    @Test public void mouseSelectAndBindingRespectCancelDeleteAndSearchFocus()throws Exception{
        screen.search("render");screen.clickResult(1,0);assertSame(beta,field("selected"));assertFalse(beta.isToggled());
        screen.key(Keyboard.KEY_K);screen.key(Keyboard.KEY_G);assertEquals(Keyboard.KEY_G,beta.getKey());
        screen.key(Keyboard.KEY_K);screen.key(Keyboard.KEY_ESCAPE);assertEquals(Keyboard.KEY_G,beta.getKey());
        screen.key(Keyboard.KEY_K);screen.key(Keyboard.KEY_DELETE);assertEquals(0,beta.getKey());
        screen.control=true;screen.key(Keyboard.KEY_F);screen.control=false;screen.type('x');assertEquals("renderx",field("query"));
    }
    @Test public void escapeClearsSearchBeforeReturningToParent()throws Exception{
        screen.search("query");screen.key(Keyboard.KEY_ESCAPE);assertEquals("",field("query"));assertNull(screen.routed);
        screen.key(Keyboard.KEY_ESCAPE);assertSame(parent,screen.routed);
    }
    @Test public void listScrollClampsAndKeyboardRevealsSelectedRowAtSmallSize()throws Exception{
        screen.resize(320,180);screen.search("render");screen.wheel(-120);screen.wheel(-120);
        float max=3*30-screen.listBounds().height;assertEquals(max,(Float)field("targetScroll"),.01f);
        screen.key(Keyboard.KEY_DOWN);ClipRect first=screen.resultBounds(0);assertTrue(first.y>=screen.listBounds().y);
        screen.key(Keyboard.KEY_DOWN);screen.key(Keyboard.KEY_DOWN);ClipRect third=screen.resultBounds(2);
        assertTrue(third.y+third.height<=screen.listBounds().y+screen.listBounds().height+.01f);
    }
}
