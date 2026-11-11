package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.value.Value;
import org.junit.*;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Protects the compact legacy layout and input access when the HUD section is present. */
public class LegacySettingsLayoutTest {
    private ModuleManager priorManager;
    private String priorMode;
    private ClientSettings settings;
    private Screen screen;
    private static final class Screen extends SettingsMenu {
        void size(int w,int h){width=w;height=h;initGui();}
        ClipRect viewport(){return view();}
        ClipRect category(int i){return categoryBounds(i);}
        ClipRect row(int i){return rowBounds(i);}
        ClipRect field(int i){return controlBounds(i);}
        ClipRect action(int i){return actionBounds(i);}
        ClipRect popup(){return popupBounds();}
        ClipRect track(int row){return sliderTrack(row);}
        void key(int key)throws Exception{keyTyped(' ',key);}
        void click(ClipRect r)throws Exception{mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
        void clickAt(float x,float y)throws Exception{mouseClicked(Math.round(x),Math.round(y),0);mouseReleased(0,0,0);}
        @Override protected boolean ctrl(){return false;}
        @Override protected boolean shift(){return false;}
    }
    @Before public void before()throws Exception{
        priorManager=Pulsar.INSTANCE.getMm();priorMode=ThemeMode.selected();settings=new ClientSettings();
        setManager(new ModuleManager(Arrays.asList(settings,new HUD())));ThemeMode.select(ThemeMode.LEGACY);screen=new Screen();
    }
    @After public void after()throws Exception{screen.onGuiClosed();setManager(priorManager);ThemeMode.select(priorMode);}
    private static void setManager(ModuleManager manager)throws Exception{Field f=Pulsar.class.getDeclaredField("mm");f.setAccessible(true);f.set(Pulsar.INSTANCE,manager);}
    private Object state(String name)throws Exception{Field f=SettingsMenu.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private static void contained(ClipRect outer,ClipRect inner){
        assertTrue(inner.x>=outer.x-.01f&&inner.y>=outer.y-.01f);
        assertTrue(inner.x+inner.width<=outer.x+outer.width+.01f&&inner.y+inner.height<=outer.y+outer.height+.01f);
        assertTrue(inner.width>0&&inner.height>0);
    }

    @Test public void shortRailKeepsEverySectionAboveTheFooterAndSeparateFromTheFields()throws Exception{
        for(int[] size:new int[][]{{580,342},{640,360},{854,480}}){
            screen.size(size[0],size[1]);ClipRect all=new ClipRect(0,0,size[0],size[1]);
            for(int i=0;i<8;i++){
                ClipRect category=screen.category(i);contained(all,category);
                assertTrue(category.x+category.width<screen.viewport().x);
                assertTrue(category.y+category.height<=screen.action(2).y-6);
                screen.click(category);assertEquals(i,state("group"));
            }
        }
    }

    @Test public void smallControlsAndFontPopupFitAndRelayoutCannotChangeLiveValues()throws Exception{
        double initial=settings.roundness.getValue();String font=settings.fontFamily.getValue();
        for(int[] size:new int[][]{{334,234},{320,240},{854,480}}){
            screen.size(size[0],size[1]);ClipRect all=new ClipRect(0,0,size[0],size[1]);
            contained(screen.row(0),screen.field(0));contained(screen.viewport(),screen.row(0));
            assertTrue(screen.viewport().y+screen.viewport().height<=screen.action(2).y-6);
            for(int i=0;i<6;i++)contained(all,screen.action(i));
            while(!Integer.valueOf(5).equals(state("group")))screen.key(Keyboard.KEY_F6);
            screen.key(Keyboard.KEY_RETURN);assertSame(settings.fontFamily,state("popupValue"));contained(all,screen.popup());
            screen.key(Keyboard.KEY_END);screen.key(Keyboard.KEY_ESCAPE);assertEquals(font,settings.fontFamily.getValue());
            while(!Integer.valueOf(0).equals(state("group")))screen.key(Keyboard.KEY_F6);
            assertEquals(initial,settings.roundness.getValue(),0);
        }
    }

    @Test public void resizeWithALowerComboOpenRevealsItsValueAndKeepsThePendingChoiceVisible()throws Exception{
        screen.size(854,480);screen.click(screen.category(3));
        List<Value<?>> visible=new ArrayList<>();
        for(Value<?> value:settings.groups().get("Gradients / Motion"))if(value.isVisible())visible.add(value);
        int row=visible.indexOf(settings.gradientMethod);assertTrue(row>=4);
        screen.key(Keyboard.KEY_HOME);for(int i=0;i<row;i++)screen.key(Keyboard.KEY_DOWN);
        screen.key(Keyboard.KEY_RETURN);assertSame(settings.gradientMethod,state("popupValue"));
        screen.key(Keyboard.KEY_END);int choice=settings.gradientMethod.getOptions().length-1;
        String saved=settings.gradientMethod.getValue();
        for(int[] size:new int[][]{{320,240},{854,480},{334,234}}){
            screen.size(size[0],size[1]);
            assertSame(settings.gradientMethod,state("popupValue"));assertEquals(row,state("focus"));
            contained(screen.viewport(),screen.row(row));contained(screen.viewport(),screen.field(row));
            ClipRect popup=screen.popup();contained(new ClipRect(0,0,size[0],size[1]),popup);
            assertTrue(popup.y+popup.height<=screen.action(2).y-6+.01f);
            int offset=(Integer)state("popupOffset"),shown=(int)((popup.height-8)/22);
            assertEquals(choice,state("popupChoice"));assertTrue(shown>0&&offset>=0);
            assertTrue(choice>=offset&&choice<offset+shown);
            assertTrue(offset<=settings.gradientMethod.getOptions().length-shown);
            assertEquals("Resizing must not commit the highlighted option",saved,settings.gradientMethod.getValue());
        }
        screen.key(Keyboard.KEY_RETURN);
        assertEquals(settings.gradientMethod.getOptions()[choice],settings.gradientMethod.getValue());assertNull(state("popupValue"));
    }

    @Test public void clickingTheVisibleSliderEndsSetsExactMinAndMaxInBothLayouts()throws Exception{
        for(String style:new String[]{ThemeMode.LEGACY,ThemeMode.APPROACH_TWO}){
            ThemeMode.select(style);
            for(int[] size:new int[][]{{334,234},{854,480}}){
                screen.size(size[0],size[1]);ClipRect track=screen.track(0);
                screen.clickAt(track.x,track.y+1);assertEquals(settings.roundness.getMin(),settings.roundness.getValue(),0);
                screen.clickAt(track.x+track.width,track.y+1);assertEquals(settings.roundness.getMax(),settings.roundness.getValue(),0);
            }
        }
    }

    @Test public void modernRailAndCompactControlsStayReachableInBothPresentations()throws Exception {
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        for(String style:new String[]{"Modern","Creative"}){
            settings.neutronScreenStyle.setValue(style);
            for(int[] size:new int[][]{{580,342},{640,360},{854,480},{1280,720}}){
                screen.size(size[0],size[1]);
                ClipRect all=new ClipRect(0,0,size[0],size[1]);
                for(int i=0;i<8;i++){
                    ClipRect category=screen.category(i);contained(all,category);
                    assertTrue(category.x+category.width<screen.viewport().x);
                    assertTrue(category.y+category.height<=screen.action(2).y-6);
                    screen.click(category);assertEquals(i,state("group"));
                }
                screen.click(screen.category(0));
                contained(screen.row(0),screen.field(0));
                contained(screen.viewport(),screen.row(0));
                for(int action=0;action<6;action++)contained(all,screen.action(action));
            }
        }
    }
}
