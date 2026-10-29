package me.youngjibbit.gui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.*;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import java.lang.reflect.*;
import static org.junit.Assert.*;

/** Actual handlers and layouts, synthetic Values and temporary ThemeStore. No world/auth routes. */
public class Qa02ScreenMotionTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    ClientSettings c;ModuleManager previous;ThemeCatalog oldCatalog;Dummy dummy;
    @ModuleInfo(name="QA02 Dummy",category=Category.Combat)
    public static class Dummy extends Module {
        public final CheckBoxValue check=new CheckBoxValue("Check",this,false);
        public final ComboValue combo=new ComboValue("Combo",this,new String[]{"A","B","C"},0);
        public final SliderValue slider=new SliderValue("Slider",this,5.0,0,10);
        public int enable,disable,changes;@Override public void onEnable(){enable++;}@Override public void onDisable(){disable++;}@Override public void onValueChange(){changes++;}
    }
    @Before public void setup()throws Exception{previous=Pulsar.INSTANCE.getMm();oldCatalog=ThemeCatalog.global();c=new ClientSettings();dummy=new Dummy();set(Pulsar.INSTANCE,"mm",new ModuleManager(Arrays.asList(c,dummy)));ThemeCatalog.install(new ThemeCatalog(null));UiEffects.invalidate();}
    @After public void restore()throws Exception{set(Pulsar.INSTANCE,"mm",previous);ThemeCatalog.install(oldCatalog);UiEffects.invalidate();}
    static Object get(Object o,String name)throws Exception{for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void set(Object o,String name,Object value)throws Exception{for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void same(ClipRect a,ClipRect b){assertEquals(a.x,b.x,0);assertEquals(a.y,b.y,0);assertEquals(a.width,b.width,0);assertEquals(a.height,b.height,0);}
    static int midX(ClipRect b){return (int)(b.x+b.width/2);}static int midY(ClipRect b){return (int)(b.y+b.height/2);}
    static class Settings extends SettingsMenu {
        int calls,last=-1;@Override protected void activate(int id){calls++;last=id;}
        ClipRect box(int id){return actionBounds(id);}void resize(int w,int h){width=w;height=h;initGui();}
        void step(float dt,int x,int y){advanceMotion(dt,x,y);}void click(int x,int y,int b)throws Exception{mouseClicked(x,y,b);}void up(){mouseReleased(-1,-1,0);}void key(int k)throws Exception{keyTyped(' ',k);}
    }
    static class Panel extends ClickGui {
        void dispose()throws Exception{Method reset=ClickGui.class.getDeclaredMethod("resetMotion");reset.setAccessible(true);reset.invoke(this);}
        int themes;@Override protected void openThemeEditor(){themes++;}
        void resize(int w,int h)throws Exception{((GuiScreen)this).width=w;((GuiScreen)this).height=h;initGui();layout();}
        void layout()throws Exception{Method m=ClickGui.class.getDeclaredMethod("layoutModules",float.class);m.setAccessible(true);m.invoke(this,0f);}
        void step(float dt,int x,int y){advanceMotion(dt,x,y);}void click(int x,int y,int b)throws Exception{mouseClicked(x,y,b);}void up(){mouseReleased(-1,-1,0);}void key(int k)throws Exception{keyTyped(' ',k);}
        ClipRect theme(){return themeBounds();}
    }
    static class Editor extends ThemeEditorScreen {
        int exits;Editor(ThemeCatalog catalog){super(null,catalog,s->catalog.select(s));}@Override protected void leave(){exits++;}
        void dispose()throws Exception{Method reset=ThemeEditorScreen.class.getDeclaredMethod("resetMotion");reset.setAccessible(true);reset.invoke(this);((ThemeDraft)get(this,"draft")).close();}
        ClipRect box(int id){return actionBounds(id);}void resize(int w,int h){width=w;height=h;initGui();}
        void step(float dt,int x,int y){advanceMotion(dt,x,y);}void click(int id)throws Exception{ClipRect b=box(id);mouseClicked(midX(b),midY(b),0);}void up(){mouseReleased(-1,-1,0);}void key(int k)throws Exception{keyTyped(' ',k);}
    }
    static class IOS extends iOS {
        void click(int x,int y)throws Exception{mouseClicked(x,y,0);}void up(){mouseReleased(-1,-1,0);}
        void step(float dt,int localX,int localY){advanceMotion(dt,localX,localY);}
    }
    @Test public void sixSettingsButtonsKeepMouseDownDispatchAndNoReleaseDoubleAction()throws Exception{
        Settings s=new Settings();s.resize(480,320);for(int i=0;i<6;i++){ClipRect b=s.box(i);int before=s.calls;s.click(midX(b),midY(b),1);assertEquals(before,s.calls);s.click(midX(b),midY(b),0);assertEquals(before+1,s.calls);s.step(.02f,midX(b),midY(b));assertTrue(((UiSpring[])get(s,"actionMotion"))[i].pressed());s.up();s.up();assertEquals(before+1,s.calls);assertEquals(i,s.last);}s.onGuiClosed();
    }
    @Test public void settingsPresetsMoveVisualsWithStableBoundsAndNoActivation()throws Exception{
        Settings s=new Settings();s.resize(480,320);ClipRect b=s.box(2);float lift=0;for(String preset:c.motionIntensity.getOptions()){c.motionIntensity.setValue(preset);s.resize(480,320);for(int n=0;n<60;n++)s.step(1f/60,midX(b),midY(b));UiSpring visual=((UiSpring[])get(s,"actionMotion"))[2];assertTrue(visual.lift()>lift);lift=visual.lift();same(b,s.box(2));}assertEquals(0,s.calls);s.onGuiClosed();
    }
    @Test public void settingsKeyboardFocusAndResizeCloseCancelVisualPress()throws Exception{
        Settings s=new Settings();s.resize(480,320);ClipRect b=s.box(0);s.click(midX(b),midY(b),0);s.resize(367,245);assertEquals(-1,get(s,"pressed"));
        for(int i=0;i<c.groups().get("Appearance").size();i++)s.key(Keyboard.KEY_TAB);s.step(0,-1,-1);assertTrue(((UiSpring[])get(s,"actionMotion"))[0].focused());int calls=s.calls;s.key(Keyboard.KEY_RETURN);assertEquals(calls+1,s.calls);s.up();assertEquals(calls+1,s.calls);s.onGuiClosed();for(UiSpring state:(UiSpring[])get(s,"actionMotion")){assertEquals(0,state.amount(),0);assertFalse(state.pressed());}
    }
    @Test public void settingsOutsideAndExclusiveEdgesRemainInactive()throws Exception{
        Settings s=new Settings();s.resize(480,320);for(int i=0;i<6;i++){ClipRect b=s.box(i);s.step(.2f,midX(b),midY(b));s.click((int)Math.ceil(b.x+b.width),midY(b),0);}assertEquals(0,s.calls);s.onGuiClosed();
    }
    @Test public void panelModuleTogglesExactlyOnceAndMotionNeverDispatches()throws Exception{
        Panel p=new Panel();p.resize(640,480);ClickModule row=((List<ClickModule>)get(p,"modules")).get(0);ClipRect b=row.presentationBounds();
        for(int i=0;i<90;i++)p.step(1f/60,midX(b),midY(b));assertEquals(0,dummy.enable);p.click((int)b.x+10,midY(b),0);assertEquals(1,dummy.enable);p.up();assertEquals(1,dummy.enable);same(b,row.presentationBounds());
        p.key(Keyboard.KEY_SPACE);assertEquals(1,dummy.disable);p.up();assertEquals(1,dummy.disable);p.dispose();
    }
    @Test public void panelChromePressKeyboardCancelAndResizeKeepRouteBounds()throws Exception{
        Panel p=new Panel();p.resize(640,480);ClipRect b=p.theme();p.step(.1f,midX(b),midY(b));same(b,p.theme());assertEquals(0,p.themes);p.click(midX(b),midY(b),0);assertEquals(1,p.themes);p.up();assertEquals(1,p.themes);p.key(Keyboard.KEY_TAB);p.resize(367,245);assertNull(get(p,"visualControl"));assertEquals(-1,get(p,"visualChrome"));p.dispose();
    }
    @Test public void panelCheckboxAndSliderActualHitAndKeyboardKeepValuesAndCallbacks()throws Exception{
        ClickCheckBox check=new ClickCheckBox(dummy.check,10,10,240,24);check.setClip(new ClipRect(10,10,240,24));ClipRect b=check.presentationBounds();
        for(int i=0;i<60;i++)check.advanceMotion(.016f,midX(b),midY(b),true,false,true);assertFalse(dummy.check.getValue());assertEquals(0,dummy.changes);
        check.mouseClicked(midX(b),midY(b),0);assertTrue(dummy.check.getValue());assertEquals(1,dummy.changes);check.keyTyped(' ',Keyboard.KEY_SPACE);assertFalse(dummy.check.getValue());assertEquals(2,dummy.changes);
        ClickSlider slider=new ClickSlider(dummy.slider,10,40,240,24);slider.setClip(new ClipRect(10,40,240,24));double value=dummy.slider.getValue();slider.advanceMotion(.1f,180,50,true,false,true);assertEquals(value,dummy.slider.getValue(),0);slider.keyTyped(' ',Keyboard.KEY_RIGHT);assertEquals(value+.1,dummy.slider.getValue(),.00001);slider.mouseReleased();
    }
    @Test public void panelClippedClosedAndPartlyRevealedComboChildrenStayUnclickable()throws Exception{
        c.animations.setValue(false);ClickModule row=new ClickModule(dummy,10,10,240,24);ClipRect clip=new ClipRect(10,10,240,120);row.updateAnimation();row.layout(10,10,240,clip);row.mouseClicked(230,66,0);assertFalse(dummy.check.getValue());
        row.setOpen(true);row.updateAnimation();row.layout(10,10,240,clip);List<Component.Sub<?>> values=row.focusableValues();ClickCombo combo=null;for(Component.Sub<?> v:values)if(v instanceof ClickCombo)combo=(ClickCombo)v;assertNotNull(combo);
        combo.keyTyped(' ',Keyboard.KEY_RETURN);combo.updateAnimation();row.updateAnimation();row.layout(10,10,240,clip);combo.mouseClicked(200,180,0);assertEquals("A",dummy.combo.getValue());
        row.setOpen(false);row.updateAnimation();row.layout(10,10,240,clip);assertTrue(row.focusableValues().isEmpty());row.mouseClicked(230,80,0);assertFalse(dummy.check.getValue());
    }
    @Test public void editorReadOnlyAndDisabledIgnoreMouseAndHaveNoHoverResponse()throws Exception{
        ThemeCatalog catalog=new ThemeCatalog(null);Editor e=new Editor(catalog);e.resize(480,320);ClipRect b=e.box(6);e.step(.2f,midX(b),midY(b));UiSpring state=((UiSpring[])get(e,"controlMotion"))[6];assertFalse(state.enabled());assertEquals(0,state.amount(),0);e.click(6);assertEquals(0,e.exits);assertFalse(catalog.current().isCustom());e.dispose();
    }
    @Test public void editorHoverFocusDoNotPersistAndSaveCancelResetDeleteKeepActualOwnership()throws Exception{
        java.nio.file.Path file=folder.newFolder().toPath().resolve("themes.json");ThemeCatalog catalog=new ThemeCatalog(new ThemeStore(file));Editor e=new Editor(catalog);e.resize(640,480);e.click(2);e.up();ClipRect save=e.box(3);for(int i=0;i<60;i++)e.step(.016f,midX(save),midY(save));assertFalse(java.nio.file.Files.exists(file));
        e.click(5);e.up();assertFalse(java.nio.file.Files.exists(file));e.click(3);e.up();assertTrue(java.nio.file.Files.exists(file));assertEquals(1,e.exits);String id=catalog.selectedReference();e.dispose();
        e=new Editor(catalog);e.resize(640,480);e.click(4);assertEquals(id,catalog.selectedReference());e.dispose();
        e=new Editor(catalog);e.resize(640,480);e.click(6);assertNull(catalog.find(id));assertEquals(1,e.exits);e.dispose();
    }
    @Test public void editorFocusWithoutBorderGlowAndVisualCaptureResetDoNotChangeBounds()throws Exception{
        c.border.setValue(false);c.glow.setValue(false);Editor e=new Editor(new ThemeCatalog(null));e.resize(480,320);e.key(Keyboard.KEY_TAB);e.step(0,-1,-1);assertTrue(((UiSpring[])get(e,"controlMotion"))[1].focused());ClipRect b=e.box(4);e.step(.1f,midX(b),midY(b));same(b,e.box(4));e.click(0);e.resize(367,245);assertEquals(-1,get(e,"visualPressed"));e.dispose();for(UiSpring state:(UiSpring[])get(e,"controlMotion"))assertEquals(0,state.amount(),0);
    }
    @Test public void iosCategoryPresentationUsesUnchangedInverseScaleHandlerAndNeverToggles()throws Exception{
        c.animations.setValue(false);IOS ios=new IOS();((GuiScreen)ios).width=320;((GuiScreen)ios).height=224;ios.initGui();
        float x=(float)get(ios,"x"),y=(float)get(ios,"y"),w=(float)get(ios,"width"),h=(float)get(ios,"height");
        for(float scale:new float[]{1,.75f,.5f}){set(ios,"renderedScale",scale);set(ios,"categoryReady",true);int index=Category.Client.ordinal();float lx=x+40,ly=y+25+index*17+8;
            ios.step(.1f,(int)lx,(int)ly);assertTrue(((UiSpring[])get(ios,"categoryMotion"))[index].amount()>0);
            int rawX=Math.round(x+w/2+(lx-x-w/2)*scale),rawY=Math.round(y+h/2+(ly-y-h/2)*scale);ios.click(rawX,rawY);assertEquals(Category.Client,get(ios,"selectedCategory"));ios.up();assertEquals(-1,get(ios,"visualCategory"));assertEquals(0,dummy.enable);
        }ios.onGuiClosed();
    }
    @Test public void iosOpeningAndContentClipDoNotGrantCategoryOrModuleActions()throws Exception{
        IOS ios=new IOS();((GuiScreen)ios).width=640;((GuiScreen)ios).height=480;ios.initGui();set(ios,"categoryReady",false);float x=(float)get(ios,"x"),y=(float)get(ios,"y");
        ios.step(.1f,(int)x+30,(int)y+35);for(UiSpring state:(UiSpring[])get(ios,"categoryMotion"))assertEquals(0,state.amount(),0);
        ios.click((int)x+30,(int)y+100);assertEquals(Category.Combat,get(ios,"selectedCategory"));assertEquals(0,dummy.enable);
        c.animations.setValue(false);ios.click(-100,-100);assertEquals(0,dummy.enable);ios.initGui();assertEquals(-1,get(ios,"visualCategory"));ios.onGuiClosed();
    }
}
