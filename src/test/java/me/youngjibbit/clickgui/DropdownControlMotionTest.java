package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Actual handlers without a native screen; only opt-in 2.0 presentation changes. */
public class DropdownControlMotionTest {
    @ModuleInfo(name="VIS03 B2 dummy",category=Category.Combat)
    public static class Dummy extends Module {
        final CheckBoxValue check=new CheckBoxValue("Check",this,false);
        final ComboValue combo=new ComboValue("Combo",this,new String[]{"A","B","C"},0);
        final SliderValue slider=new SliderValue("Slider",this,5d,0,10);
        int changes,enable,disable;
        @Override public void onValueChange(){changes++;}
        @Override public void onEnable(){enable++;}
        @Override public void onDisable(){disable++;}
    }

    private ModuleManager previous;
    private ClientSettings settings;
    private Dummy dummy;
    private String previousTheme;
    @Before public void prepare() throws Exception {
        previous=Pulsar.INSTANCE.getMm();
        previousTheme=ThemeMode.selected();
        settings=new ClientSettings();dummy=new Dummy();
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,new ModuleManager(Arrays.asList(settings,dummy)));
        ThemeMode.select(ThemeMode.APPROACH_TWO);UiEffects.invalidate();
    }
    @After public void restore() throws Exception {
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,previous);
        ThemeMode.select(previousTheme);UiEffects.invalidate();
    }

    static Object reflect(Object owner,String name) throws Exception {
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass()){
            try {
                Field field=type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            }catch(NoSuchFieldException ignored){}
        }
        throw new NoSuchFieldException(name);
    }
    private static <T> T child(DropdownModule module,Class<T> kind){
        for(Component.Sub<?> value:module.presentationValues())
            if(kind.isInstance(value))return kind.cast(value);
        throw new AssertionError(kind.toString());
    }
    static class Screen extends DropdownGui {
        void size(int w,int h){
            ((GuiScreen)this).width=w;((GuiScreen)this).height=h;initGui();
        }
        void step(float dt){layout(dt);}
        void click(int x,int y,int button)throws Exception{mouseClicked(x,y,button);}
        void up(){mouseReleased(-1,-1,0);}
        void key(int code)throws Exception{keyTyped(' ',code);}
        void wheel(int x,int y,int steps){scrollAt(x,y,steps);}
        /** Calls the exact production cleanup path without touching native GL resources. */
        void closeInputHeadless(){cancelInteraction();}
    }
    private static Object category(Screen screen,Category wanted)throws Exception{
        @SuppressWarnings("unchecked") List<Object> list=(List<Object>)reflect(screen,"panels");
        for(Object item:list)if(reflect(item,"category")==wanted)return item;
        throw new AssertionError(wanted);
    }

    @Test public void columnReversalKeepsOriginalHitGeometryAndLogicalClosure() throws Exception {
        Screen screen=new Screen();screen.size(640,480);
        Object combat=category(screen,Category.Combat);
        float x=(Float)reflect(combat,"x"),y=(Float)reflect(combat,"y");
        float full=(Float)reflect(combat,"height");
        assertTrue((Boolean)reflect(combat,"open"));
        screen.click((int)x+9,(int)y+7,1);
        assertFalse((Boolean)reflect(combat,"open"));
        assertEquals(full,(Float)reflect(combat,"height"),.0001f); // dt0 retains paint pose.
        screen.step(.030f);
        float closing=(Float)reflect(combat,"height");
        assertTrue(closing<full);
        screen.click((int)x+9,(int)y+7,1);
        assertTrue((Boolean)reflect(combat,"open"));
        assertEquals(closing,(Float)reflect(combat,"height"),.0001f);
        for(int i=0;i<30;i++){
            screen.click((int)x+9,(int)y+7,1);
            screen.step(.006f);
            float value=(Float)reflect(combat,"height");
            assertTrue(Float.isFinite(value)&&value>=17);
        }
        screen.closeInputHeadless();
        assertNull(reflect(screen,"dragging"));
        assertNull(reflect(screen,"scrolling"));
        assertNull(reflect(screen,"focus"));
        assertNull(reflect(screen,"pressed"));
        assertNull(reflect(screen,"binding"));
    }

    @Test public void logicalClosedColumnCannotToggleItsChildInVisibleSpringTail() throws Exception {
        Screen screen=new Screen();screen.size(640,480);
        Object combat=category(screen,Category.Combat);
        float x=(Float)reflect(combat,"x"),y=(Float)reflect(combat,"y");
        screen.click((int)x+9,(int)y+7,1);
        assertFalse((Boolean)reflect(combat,"open"));
        screen.click((int)x+15,(int)y+25,0);
        assertFalse(dummy.isToggled());assertEquals(0,dummy.enable);
        assertTrue((Boolean)reflect(combat,"open")==false);
        screen.closeInputHeadless();
        assertNull(reflect(screen,"dragging"));
        assertNull(reflect(screen,"scrolling"));
        assertNull(reflect(screen,"focus"));
        assertNull(reflect(screen,"pressed"));
        assertNull(reflect(screen,"binding"));
    }

    @Test public void scrollAndDragCaptureUseTheRealCategoryHandlers() throws Exception {
        Screen screen=new Screen();screen.size(640,480);
        Object combat=category(screen,Category.Combat);
        float x=(Float)reflect(combat,"x"),y=(Float)reflect(combat,"y");
        // Supply a deterministic scrollable content range without starting GL drawing.
        Field max=combat.getClass().getDeclaredField("maxScroll");max.setAccessible(true);
        max.setFloat(combat,96f);
        screen.wheel((int)x+12,(int)y+23,-120);
        assertEquals(-32f,(Float)reflect(combat,"targetScroll"),.0001f);
        screen.click((int)x+9,(int)y+7,0);
        assertSame(combat,reflect(screen,"dragging"));
        screen.wheel((int)x+12,(int)y+23,-120);
        assertEquals("Captured drag blocks scroll", -32f,
                (Float)reflect(combat,"targetScroll"),.0001f);
        screen.up();
        assertNull(reflect(screen,"dragging"));
        screen.closeInputHeadless();
        assertNull(reflect(screen,"scrolling"));
        assertNull(reflect(screen,"pressed"));
        assertNull(reflect(screen,"focus"));
    }

    @Test public void keyboardFocusTogglesModuleWithoutPresentationCallbacks() throws Exception {
        Screen screen=new Screen();screen.size(640,480);
        screen.key(Keyboard.KEY_TAB);
        assertTrue(reflect(screen,"focus") instanceof DropdownModule);
        assertFalse(dummy.isToggled());
        screen.key(Keyboard.KEY_SPACE);
        assertTrue(dummy.isToggled());
        assertEquals(1,dummy.enable);
        screen.up();screen.up();
        assertEquals(1,dummy.enable);
        screen.closeInputHeadless();
        assertNull(reflect(screen,"focus"));
    }

    @Test public void compactComboMouseOptionCommitsImmediatelyAfterReveal() throws Exception {
        DropdownModule row=new DropdownModule(dummy);
        row.setOpen(true);
        for(int i=0;i<70;i++)row.updateCompactMotion(.016f);
        row.layout(10,10,104,new ClipRect(0,0,500,500));
        ClickCombo combo=child(row,ClickCombo.class);
        combo.keyTyped(' ',Keyboard.KEY_RETURN);
        for(int i=0;i<70;i++)row.updateCompactMotion(.016f);
        row.layout(10,10,104,new ClipRect(0,0,500,500));
        assertTrue(combo.isOpen());
        // Compact field height is 36, visible option height 19; choose the second option.
        combo.mouseClicked((int)combo.getX()+17,(int)combo.getY()+36+19+9,0);
        assertEquals("B",dummy.combo.getValue());
        assertFalse(combo.isOpen());
        assertEquals(1,dummy.changes);
    }

    @Test public void compactComboReversesWithoutCommittingDuringClose() {
        DropdownModule row=new DropdownModule(dummy);
        row.setOpen(true);
        for(int i=0;i<80;i++)row.updateCompactMotion(.016f);
        row.layout(10,10,104,new ClipRect(0,0,500,500));
        ClickCombo combo=child(row,ClickCombo.class);
        combo.keyTyped(' ',Keyboard.KEY_RETURN);
        row.updateCompactMotion(.028f);
        float opening=combo.getHeight();
        assertTrue(opening>36);
        combo.close();
        row.updateCompactMotion(0);
        assertEquals(opening,combo.getHeight(),0);
        combo.mouseClicked(30,170,0);
        assertEquals("A",dummy.combo.getValue());
        row.updateCompactMotion(.028f);
        float closing=combo.getHeight();
        combo.keyTyped(' ',Keyboard.KEY_RETURN);
        row.updateCompactMotion(0);
        assertEquals(closing,combo.getHeight(),.001f);
        combo.keyTyped(' ',Keyboard.KEY_RIGHT);
        assertEquals("B",dummy.combo.getValue());
        for(int i=0;i<30;i++){
            combo.close();row.updateCompactMotion(.008f);
            combo.keyTyped(' ',Keyboard.KEY_RETURN);row.updateCompactMotion(.008f);
        }
        assertEquals("B",dummy.combo.getValue());
        combo.close();settings.animations.setValue(false);row.updateCompactMotion(0);
        assertEquals(36,combo.getHeight(),.001f);
    }

    @Test public void checkboxAndSliderUseRealCallbacksNotDrawFrameWrites() throws Exception {
        ClickCheckBox box=new ClickCheckBox(dummy.check,10,10,104,21);
        ClickSlider slider=new ClickSlider(dummy.slider,10,40,104,36);
        box.setClip(new ClipRect(0,0,400,400));
        slider.setClip(new ClipRect(0,0,400,400));
        int before=dummy.changes;
        for(int i=0;i<80;i++){
            box.advanceMotion(.016f,20,16,true,false,true);
            slider.advanceMotion(.016f,40,66,true,false,true);
        }
        assertEquals(before,dummy.changes);
        box.mouseClicked(90,18,0);
        assertTrue(dummy.check.getValue());
        assertEquals(before+1,dummy.changes);
        box.keyTyped(' ',Keyboard.KEY_SPACE);
        assertFalse(dummy.check.getValue());
        slider.keyTyped(' ',Keyboard.KEY_RIGHT);
        assertEquals(5.1,dummy.slider.getValue(),.0001);
        slider.advanceMotion(.016f,-1,-1,false,false,true);
        float old=(Float)reflect(slider,"displayedFraction");
        assertTrue(old>.5f&&old<.51f);
        slider.mouseClicked(25,66,0);
        double committed=dummy.slider.getValue();
        slider.mouseDragged(85,66);
        assertTrue(dummy.slider.getValue()>committed);
        int changes=dummy.changes;
        slider.advanceMotion(.016f,85,66,true,true,true);
        assertEquals(changes,dummy.changes);
        slider.mouseReleased();
        settings.reducedMotion.setValue(true);
        slider.keyTyped(' ',Keyboard.KEY_LEFT);
        slider.advanceMotion(0,-1,-1,true,false,true);
        assertEquals((float)(dummy.slider.getValue()/10),
                (Float)reflect(slider,"displayedFraction"),.0001f);
    }

    @Test public void legacyAnimationsStillUseOldClickAnimationFields() throws Exception {
        ThemeMode.select(ThemeMode.LEGACY);
        DropdownModule row=new DropdownModule(dummy);
        assertNotNull(reflect(row,"openAnimation"));
        assertNotNull(reflect(row,"toggleAnimation"));
        ClickCombo combo=child(row,ClickCombo.class);
        assertNotNull(reflect(combo,"animation"));
        ClickCheckBox check=child(row,ClickCheckBox.class);
        assertNotNull(reflect(check,"animation"));
        row.setOpen(true);
        row.updateCompactMotion(0);
        assertEquals("A",dummy.combo.getValue());
        assertFalse(dummy.check.getValue());
        assertEquals(5,dummy.slider.getValue(),0);
    }
}
