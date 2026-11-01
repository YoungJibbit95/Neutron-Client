package me.youngjibbit.gui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.ClickGui;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.ClickModule;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.gui.iosUi.component.iOSComponent;
import me.youngjibbit.gui.iosUi.component.impl.*;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.TimerUtil;
import me.youngjibbit.utils.animations.Animation;
import me.youngjibbit.utils.animations.Direction;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real component/screen handlers; frozen existing animation clocks, no renderer or account. */
public class Wave05UiInputTest {
    @ModuleInfo(name="Wave05 iOS", category=Category.Render)
    public static class IosDummy extends Module {
        public final ComboValue combo=new ComboValue("Mode",this,new String[]{"Alpha","Bravo","Charlie","Delta"},0);
        public final SliderValue slider=new SliderValue("Amount",this,5.0,0,10);
        public int changes;
        @Override public void onValueChange(){changes++;}
    }
    @ModuleInfo(name="Wave05 Panel", category=Category.Combat)
    public static class PanelDummy extends Module {
        public final CheckBoxValue check=new CheckBoxValue("Enabled",this,false);
        public final SliderValue slider=new SliderValue("Amount",this,5.0,0,10);
        public int changes;
        @Override public void onValueChange(){changes++;}
    }
    public static final class FixedTimer extends TimerUtil {
        final long elapsed;
        FixedTimer(long elapsed){this.elapsed=elapsed;}
        @Override public long getTime(){return elapsed;}
        @Override public boolean hasTimeElapsed(long duration){return elapsed>duration;}
        @Override public void setTime(long ignored){}
    }
    public static Object get(Object object,String name)throws Exception{return field(object.getClass(),name).get(object);}
    public static void set(Object object,String name,Object value)throws Exception{field(object.getClass(),name).set(object,value);}
    public static Field field(Class<?> type,String name)throws Exception {
        for(Class<?> t=type;t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    public static void freeze(Object object,String animationName,Direction direction,long elapsed)throws Exception {
        Animation animation=(Animation)get(object,animationName);
        animation.setDirection(direction);animation.timerUtil=new FixedTimer(elapsed);
    }
    public static class Panel extends ClickGui {
        public void resize(int width,int height)throws Exception{((GuiScreen)this).width=width;((GuiScreen)this).height=height;initGui();layoutNow();}
        public void layoutNow()throws Exception{Method m=ClickGui.class.getDeclaredMethod("layoutModules",float.class);m.setAccessible(true);m.invoke(this,0f);}
        public void click(int x,int y,int button)throws Exception{mouseClicked(x,y,button);}
        public void key(int code)throws Exception{keyTyped(' ',code);}
        public void release(){mouseReleased(-1,-1,0);}
        public ClickModule first()throws Exception{return ((List<ClickModule>)get(this,"modules")).get(0);}
    }
    public static class IosFixture {
        public final IosDummy value=new IosDummy();
        public final iOSModule row=new iOSModule(value,10,10,238,24);
        public final iOSCombo combo;
        public final iOSSlider slider;
        public IosFixture(long elapsed)throws Exception {
            set(row,"open",true);freeze(row,"openAnimation",Direction.FORWARDS,1000);
            List<iOSComponent.Sub<?>> children=(List<iOSComponent.Sub<?>>)get(row,"VALUES");
            combo=(iOSCombo)children.get(0);slider=(iOSSlider)children.get(1);
            set(combo,"open",true);freeze(combo,"openAnimation",Direction.FORWARDS,elapsed);
            combo.updateComponent(10,65);slider.updateComponent(10,65+combo.getHeight()+1);
            value.changes=0;
        }
        public float revealHeight()throws Exception{return 88*((Animation)get(combo,"openAnimation")).getOutput().floatValue();}
    }
    public static iOS shell()throws Exception {
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        return (iOS)unsafe.allocateInstance(iOS.class);
    }
    private Object previousManager,previousIos;
    private ClientSettings settings;
    private PanelDummy panelValue;
    @Before public void setup()throws Exception {
        previousManager=Pulsar.INSTANCE.getMm();previousIos=get(Pulsar.INSTANCE,"iOS");
        settings=new ClientSettings();settings.animations.setValue(true);settings.reducedMotion.setValue(false);
        panelValue=new PanelDummy();set(Pulsar.INSTANCE,"mm",new ModuleManager(Arrays.asList(settings,panelValue)));
        set(Pulsar.INSTANCE,"iOS",shell());UiEffects.invalidate();
    }
    @After public void restore()throws Exception{set(Pulsar.INSTANCE,"mm",previousManager);set(Pulsar.INSTANCE,"iOS",previousIos);UiEffects.invalidate();}

    @Test public void iosInvisibleOptionCannotChangeValueInsideOpenModule()throws Exception {
        for(float scroll:new float[]{0,-20}) {
            set(Pulsar.INSTANCE.getIOS(),"scrollOffset",scroll);
            IosFixture fixture=new IosFixture(66); // EaseInOutQuad: ~.301, not a wall-clock sleep.
            assertTrue(fixture.revealHeight()<33);
            fixture.row.mouseClicked(190,(int)(123+scroll),0);
            assertEquals("Invisible second option changed value","Alpha",fixture.value.combo.getValue());
            assertEquals(0,fixture.value.changes);
        }
    }
    @Test public void iosVisibleOptionDispatchesOnceAndReleaseDoesNotRepeat()throws Exception {
        IosFixture fixture=new IosFixture(66);fixture.value.combo.setValue("Bravo");fixture.value.changes=0;
        fixture.row.mouseClicked(190,100,0);fixture.row.mouseReleased();fixture.row.mouseReleased();
        assertEquals("Alpha",fixture.value.combo.getValue());assertEquals(1,fixture.value.changes);
    }
    @Test public void iosSharedOptionEdgeBelongsOnlyToFollowingRow()throws Exception {
        for(float scroll:new float[]{0,-20}) {
            set(Pulsar.INSTANCE.getIOS(),"scrollOffset",scroll);
            for(int[] sample:new int[][]{{111,0},{112,1},{177,3},{178,-1}}) {
                IosFixture fixture=new IosFixture(1000);
                fixture.value.combo.setValue("Charlie");fixture.value.changes=0;
                fixture.combo.mouseClicked(190,(int)(sample[0]+scroll),0);
                fixture.combo.mouseReleased();fixture.combo.mouseReleased();
                String expected=sample[1]<0?"Charlie":fixture.value.combo.getOptions()[sample[1]];
                assertEquals("row edge y="+sample[0]+" scroll="+scroll,expected,fixture.value.combo.getValue());
                assertEquals(sample[1]<0?0:1,fixture.value.changes);
            }
        }
    }
    @Test public void iosAllOptionsRemainReachableWithMotionOffAndReducedMotion()throws Exception {
        for(boolean reduced:new boolean[]{false,true}) {
            settings.animations.setValue(reduced);settings.reducedMotion.setValue(reduced);UiEffects.invalidate();
            for(int index=1;index<4;index++) {
                IosFixture fixture=new IosFixture(0);
                fixture.row.mouseClicked(190,90+22*index+10,0);fixture.row.mouseReleased();
                assertEquals(fixture.value.combo.getOptions()[index],fixture.value.combo.getValue());assertEquals(1,fixture.value.changes);
            }
        }
    }
    @Test public void iosClosingModuleCancelsCapturedSliderWithoutMouseUp()throws Exception {
        IosFixture fixture=new IosFixture(1000);
        fixture.row.mouseClicked(190,(int)fixture.slider.getY()+10,0);
        assertTrue((Boolean)get(fixture.slider,"dragging"));
        fixture.row.mouseClicked(30,20,1);
        assertFalse("Closing retained hidden slider capture",(Boolean)get(fixture.slider,"dragging"));
    }
    @Test public void panelZeroRevealDoesNotExposeKeyboardAction()throws Exception {
        Panel panel=new Panel();panel.resize(640,480);ClickModule row=panel.first();
        panel.click((int)row.getX()+12,(int)row.getY()+12,1);
        freeze(row,"openAnimation",Direction.FORWARDS,0);panel.layoutNow();
        assertEquals(24,row.getHeight(),.001);
        panel.key(Keyboard.KEY_TAB);panel.key(Keyboard.KEY_SPACE);panel.release();
        assertFalse("Invisible checkbox received keyboard activation",panelValue.check.getValue());assertEquals(0,panelValue.changes);
    }
    @Test public void panelFullyRevealedKeyboardActionStillDispatchesOnce()throws Exception {
        Panel panel=new Panel();panel.resize(640,480);ClickModule row=panel.first();
        panel.click((int)row.getX()+12,(int)row.getY()+12,1);
        freeze(row,"openAnimation",Direction.FORWARDS,1000);panel.layoutNow();
        panel.key(Keyboard.KEY_TAB);panel.key(Keyboard.KEY_SPACE);panel.release();
        assertTrue(panelValue.check.getValue());assertEquals(1,panelValue.changes);
    }
    @Test public void panelOffscreenFullyRevealedControlsRemainKeyboardReachable()throws Exception {
        settings.animations.setValue(false);UiEffects.invalidate();
        ClickModule row=new ClickModule(panelValue,10,300,238,24);row.setOpen(true);row.updateAnimation();
        row.layout(10,300,238,new ClipRect(0,0,320,200));
        assertEquals("Viewport clipping must not remove Tab destinations",2,row.focusableValues().size());
        for(Component.Sub<?> value:row.focusableValues())assertTrue(value.getClip().isEmpty());
    }
}
