package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Real Panel input/Value classes, no GL context or real Minecraft world. */
public class PanelControlMotionTest {
    @ModuleInfo(name="VIS03 Panel dummy",category=Category.Combat)
    public static class Dummy extends Module {
        final CheckBoxValue check=new CheckBoxValue("Check",this,false);
        final ComboValue combo=new ComboValue("Combo",this,new String[]{"One","Two","Three"},0);
        final SliderValue slider=new SliderValue("Slider",this,5d,0,10);
        int changes,enable,disable;
        @Override public void onValueChange(){changes++;}
        @Override public void onEnable(){enable++;}
        @Override public void onDisable(){disable++;}
    }
    private ModuleManager previous;
    private ClientSettings settings;
    private Dummy dummy;

    @Before public void setUp() throws Exception {
        previous=Pulsar.INSTANCE.getMm();
        settings=new ClientSettings();dummy=new Dummy();
        field(Pulsar.INSTANCE,"mm",new ModuleManager(Arrays.asList(settings,dummy)));
        UiEffects.invalidate();
    }
    @After public void restore() throws Exception {
        field(Pulsar.INSTANCE,"mm",previous);
        UiEffects.invalidate();
    }
    private static Object field(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void field(Object owner,String name,Object value) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
    }
    private static <T> T child(ClickModule module,Class<T> type) {
        for(Component.Sub<?> sub:module.presentationValues())if(type.isInstance(sub))return type.cast(sub);
        throw new AssertionError("Missing "+type);
    }

    @Test public void moduleOpensAndClosesWithReversalsButLogicalStateChangesImmediately(){
        ClickModule row=ClickModule.forPanel(dummy,238);
        row.updatePanelAnimation(0);
        assertFalse(row.isOpen());
        row.setOpen(true);
        float initial=row.getHeight();
        row.updatePanelAnimation(.028f);
        float opened=row.getHeight();
        assertTrue(opened>initial);
        row.setOpen(false);
        assertFalse(row.isOpen());
        assertEquals(opened,rowHeightAt(row,0),0);
        assertTrue(row.focusableValues().isEmpty());
        for(int i=0;i<30;i++){
            row.setOpen(i%2==0);
            row.updatePanelAnimation(.008f);
            assertTrue(Float.isFinite(row.getHeight()));
        }
        settings.animations.setValue(false);
        row.setOpen(true);row.updatePanelAnimation(0);
        assertFalse(row.focusableValues().isEmpty()==false && row.getHeight()<=24);
        row.setOpen(false);row.updatePanelAnimation(0);
        assertEquals(24,row.getHeight(),.001);
    }
    private static float rowHeightAt(ClickModule row,float dt){
        row.updatePanelAnimation(dt);return row.getHeight();
    }

    @Test public void comboDisclosureReversesAndHiddenOptionsCannotCommit(){
        ClickModule row=ClickModule.forPanel(dummy,238);
        row.setOpen(true);
        for(int i=0;i<90;i++)row.updatePanelAnimation(.016f);
        row.layout(10,10,238,new ClipRect(0,0,500,500));
        ClickCombo combo=child(row,ClickCombo.class);
        combo.keyTyped(' ',Keyboard.KEY_RETURN);
        assertTrue(combo.isOpen());
        row.updatePanelAnimation(.03f);
        float opening=combo.getHeight();
        assertTrue(opening>24 && opening<24+3*24+1);
        combo.close();
        assertFalse(combo.isOpen());
        row.updatePanelAnimation(0);
        assertEquals(opening,combo.getHeight(),.001);
        combo.mouseClicked(210,150,0); // Closing tail must never accept an option.
        assertEquals("One",dummy.combo.getValue());
        row.updatePanelAnimation(.02f);
        float closing=combo.getHeight();
        assertTrue(Float.isFinite(closing));
        combo.keyTyped(' ',Keyboard.KEY_RETURN);
        assertTrue(combo.isOpen());
        row.updatePanelAnimation(0);
        assertEquals(closing,combo.getHeight(),.001);
        combo.keyTyped(' ',Keyboard.KEY_RIGHT);
        assertEquals("Two",dummy.combo.getValue());
        for(int i=0;i<25;i++){combo.close();row.updatePanelAnimation(.008f);combo.keyTyped(' ',Keyboard.KEY_RETURN);row.updatePanelAnimation(.008f);}
        assertEquals("Two",dummy.combo.getValue());
        combo.close();
        settings.animations.setValue(false);
        row.updatePanelAnimation(0);
        assertEquals(24,combo.getHeight(),.001);
    }

    @Test public void checkboxAndSliderKeepImmediateCallbacksAndDoNotWriteFromPaint(){
        ClickModule row=ClickModule.forPanel(dummy,238);
        ClickCheckBox checkbox=child(row,ClickCheckBox.class);
        ClickSlider slider=child(row,ClickSlider.class);
        checkbox.updateComponent(10,10);
        slider.updateComponent(10,40);
        checkbox.setClip(new ClipRect(0,0,400,400));
        slider.setClip(new ClipRect(0,0,400,400));
        checkbox.advanceMotion(0,-1,-1,false,false,true);
        slider.advanceMotion(0,-1,-1,false,false,true);
        int changes=dummy.changes;
        for(int i=0;i<90;i++){
            checkbox.advanceMotion(.016f,15,15,true,false,true);
            slider.advanceMotion(.016f,15,50,true,false,true);
        }
        assertEquals(changes,dummy.changes);
        assertFalse(dummy.check.getValue());
        assertEquals(5.0,dummy.slider.getValue(),0);
        checkbox.mouseClicked(35,15,0);
        assertTrue(dummy.check.getValue());
        assertEquals(changes+1,dummy.changes);
        checkbox.advanceMotion(.016f,35,15,true,true,true);
        checkbox.keyTyped(' ',Keyboard.KEY_SPACE);
        assertFalse(dummy.check.getValue());
        assertEquals(changes+2,dummy.changes);
        slider.keyTyped(' ',Keyboard.KEY_RIGHT);
        assertEquals(5.1,dummy.slider.getValue(),.0001);
        slider.advanceMotion(.016f,15,50,true,false,true);
        float shown=(Float)unchecked(slider,"displayedFraction");
        assertTrue(shown>.5f && shown<.51f);
        // One captured mouse movement commits one value; a render step commits none.
        slider.mouseClicked(75,66,0);
        double afterClick=dummy.slider.getValue();
        slider.mouseDragged(190,66);
        assertTrue(dummy.slider.getValue()>afterClick);
        int recorded=dummy.changes;
        slider.advanceMotion(.016f,190,66,true,true,true);
        assertEquals(recorded,dummy.changes);
        slider.mouseReleased();
        settings.animations.setValue(false);
        slider.keyTyped(' ',Keyboard.KEY_LEFT);
        slider.advanceMotion(0,-1,-1,true,false,true);
        assertEquals((float)(dummy.slider.getValue()/10),(Float)unchecked(slider,"displayedFraction"),.0001f);
    }
    private static Object unchecked(Object obj,String field) {
        try{return field(obj,field);}catch(Exception e){throw new AssertionError(e);}
    }

    private static class Panel extends ClickGui {
        void size(int w,int h)throws Exception {
            ((GuiScreen)this).width=w;((GuiScreen)this).height=h;initGui();
            Method m=ClickGui.class.getDeclaredMethod("layoutModules",float.class);
            m.setAccessible(true);m.invoke(this,0);
        }
        void key(int code)throws Exception {keyTyped(' ',code);}
        void press(int x,int y,int button)throws Exception {mouseClicked(x,y,button);}
        void up(){mouseReleased(-1,-1,0);}
        void step(float dt,int x,int y){advanceMotion(dt,x,y);}
    }

    @Test public void categoryAndPanelCaptureUseStableLogicalBounds() throws Exception {
        Panel screen=new Panel();screen.size(640,480);
        assertEquals(Category.Combat,field(screen,"selectedCategory"));
        @SuppressWarnings("unchecked") List<ClickModule> rows=(List<ClickModule>)field(screen,"modules");
        assertFalse(rows.isEmpty());
        ClickModule row=rows.get(0);
        ClipRect b=row.presentationBounds();
        screen.step(.032f,(int)b.x+12,(int)b.y+12);
        assertEquals(0,dummy.enable);
        screen.press((int)b.x+12,(int)b.y+12,0);
        assertEquals(1,dummy.enable);
        screen.up();screen.up();assertEquals(1,dummy.enable);
        assertEquals(b.x,row.presentationBounds().x,0);
        assertEquals(b.y,row.presentationBounds().y,0);
        screen.key(Keyboard.KEY_NEXT);
        assertEquals(Category.Movement,field(screen,"selectedCategory"));
        assertEquals(0f,(Float)field(screen,"scrollOffset"),0);
        screen.key(Keyboard.KEY_PRIOR);
        assertEquals(Category.Combat,field(screen,"selectedCategory"));
    }

    @Test public void disabledMotionAndReopenKeepStoredValueDefaults(){
        ClickModule row=ClickModule.forPanel(dummy,238);
        settings.animations.setValue(false);
        row.setOpen(true);row.updatePanelAnimation(0);
        ClickCombo combo=child(row,ClickCombo.class);
        combo.keyTyped(' ',Keyboard.KEY_RETURN);row.updatePanelAnimation(0);
        assertEquals(24+3*24,combo.getHeight(),.001);
        row.resetPanelMotion();
        assertEquals("One",dummy.combo.getValue());
        assertEquals(5d,dummy.slider.getValue(),0);
        assertEquals(0,dummy.changes);
        assertEquals("Neutron Legacy",me.youngjibbit.theme.ThemeMode.LEGACY);
    }
}
