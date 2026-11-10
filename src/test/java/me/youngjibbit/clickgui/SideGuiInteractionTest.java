package me.youngjibbit.clickgui;

import me.youngjibbit.clickgui.utils.ClipRect;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;
import java.util.*;
import static org.junit.Assert.*;

/** The Library is a navigation hub; editing and binding modules stays in the ClickGUI. */
public class SideGuiInteractionTest {
    static class Screen extends SideGuiScreen {
        GuiScreen routed;boolean shifted;final List<Integer> destinations=new ArrayList<>();
        Screen(GuiScreen parent){super(parent);}
        @Override protected boolean shift(){return shifted;}
        @Override protected void open(GuiScreen next){routed=next;}
        @Override protected void activate(int id){destinations.add(id);}
        void key(int key)throws Exception{keyTyped('\0',key);}
        void resize(int width,int height){this.width=width;this.height=height;initGui();}
        void click(int id,int button)throws Exception{
            ClipRect r=tabBounds(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),button);
            mouseReleased(0,0,button);
        }
    }
    private final GuiScreen parent=new GuiScreen(){};
    private Screen screen;
    @Before public void setup(){screen=new Screen(parent);screen.resize(480,320);}
    @After public void close(){screen.onGuiClosed();}

    @Test public void leftClickOpensEachDestinationAndRightClickIsInert()throws Exception{
        for(int id=1;id<=5;id++)screen.click(id,1);
        assertTrue(screen.destinations.isEmpty());
        for(int id=1;id<=5;id++)screen.click(id,0);
        assertEquals(Arrays.asList(1,2,3,4,5),screen.destinations);
        assertTrue("Removed module browser must not retain an invisible hit target",screen.tabBounds(0).isEmpty());
    }
    @Test public void tabAndShiftTabReachAllDestinationsInBothDirections()throws Exception{
        for(int i=0;i<5;i++){screen.key(Keyboard.KEY_RETURN);screen.key(Keyboard.KEY_TAB);}
        assertEquals(Arrays.asList(1,2,3,4,5),screen.destinations);
        screen.destinations.clear();screen.shifted=true;
        for(int i=0;i<5;i++){screen.key(Keyboard.KEY_TAB);screen.key(Keyboard.KEY_NUMPADENTER);}
        assertEquals(Arrays.asList(5,4,3,2,1),screen.destinations);
    }
    @Test public void arrowNavigationFollowsTheResponsiveCardGrid()throws Exception{
        screen.key(Keyboard.KEY_DOWN);screen.key(Keyboard.KEY_SPACE);
        screen.key(Keyboard.KEY_RIGHT);screen.key(Keyboard.KEY_RETURN);
        screen.key(Keyboard.KEY_UP);screen.key(Keyboard.KEY_RETURN);
        assertEquals(Arrays.asList(3,4,2),screen.destinations);
        screen.resize(280,320);screen.destinations.clear();
        screen.key(Keyboard.KEY_DOWN);screen.key(Keyboard.KEY_RETURN);
        assertEquals(Collections.singletonList(3),screen.destinations);
    }
    @Test public void escapeReturnsImmediatelyToItsParent()throws Exception{
        screen.key(Keyboard.KEY_K);screen.key(Keyboard.KEY_DELETE);screen.key(Keyboard.KEY_A);
        assertTrue("Typing must not bind or edit a module from the Library",screen.destinations.isEmpty());
        screen.key(Keyboard.KEY_ESCAPE);assertSame(parent,screen.routed);
    }
    @Test public void cardsAndBackRemainSeparateInsideCompactAndWideWindows(){
        for(int[] size:new int[][]{{320,180},{280,320},{480,320},{800,500}}){
            screen.resize(size[0],size[1]);ClipRect window=screen.window();
            for(int id=1;id<=5;id++){
                ClipRect a=screen.tabBounds(id);
                assertTrue("Destination has a usable hit area",a.width>=36&&a.height>=18);
                assertEquals(a.width,a.intersect(window).width,.01f);
                assertEquals(a.height,a.intersect(window).height,.01f);
                for(int next=id+1;next<=5;next++)assertTrue(a.intersect(screen.tabBounds(next)).isEmpty());
            }
        }
    }
}
