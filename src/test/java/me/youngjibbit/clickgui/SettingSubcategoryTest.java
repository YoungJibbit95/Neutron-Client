package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.SettingSubcategories;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.player.InventoryManager;
import me.youngjibbit.module.impl.player.Stealer;
import me.youngjibbit.module.impl.render.TargetESP;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;

/** Real module values and GUI dispatch: disclosure never becomes a saved gameplay setting. */
public class SettingSubcategoryTest {
    @ModuleInfo(name="Subcategory Fixture",category=Category.Combat)
    public static class Fixture extends Module {
        final ComboValue mode=new ComboValue("Mode",this,new String[]{"One","Two"},0);
        final CheckBoxValue enabled=new CheckBoxValue("Local",this,false);
        final SliderValue count=new SliderValue("Count",this,5d,0,10);
        final SliderValue range=new SliderValue("Range",this,4d,1,6);
        final SliderValue yaw=new SliderValue("Yaw Speed",this,20d,1,90);
        final CheckBoxValue noise=new CheckBoxValue("Aim Noise",this,false);
        final SliderValue delay=new SliderValue("Delay Ms",this,50d,0,100);
        int changes;
        @Override public void onValueChange(){changes++;}
    }
    @ModuleInfo(name="Aura",category=Category.Combat)
    public static class AuraFixture extends Module { }
    @ModuleInfo(name="Scaffold",category=Category.Movement)
    public static class ScaffoldFixture extends Module { }

    private ModuleManager previous;
    private String previousTheme;
    private ClientSettings settings;
    private Fixture fixture;
    @Before public void setUp()throws Exception{
        previous=Pulsar.INSTANCE.getMm();previousTheme=ThemeMode.selected();
        settings=new ClientSettings();fixture=new Fixture();
        field(Pulsar.INSTANCE,"mm",new ModuleManager(Arrays.asList(settings,fixture)));
        ThemeMode.select(ThemeMode.LEGACY);settings.animations.setValue(false);UiEffects.invalidate();
    }
    @After public void restore()throws Exception{
        field(Pulsar.INSTANCE,"mm",previous);ThemeMode.select(previousTheme);UiEffects.invalidate();
    }

    @Test public void groupingPreservesEveryLiveValueIdentityAndRegistrationOrder(){
        List<Value<?>> original=new ArrayList<>(fixture.getValues());
        List<SubcategoryValue> sections=SettingSubcategories.create(fixture,original);
        Set<Value<?>> seen=Collections.newSetFromMap(new IdentityHashMap<Value<?>,Boolean>());
        for(SubcategoryValue section:sections){
            assertNull(section.getParent());assertSame(fixture,section.owner());
            assertFalse(fixture.getValues().contains(section));
            for(Value<?> value:section.children())assertTrue("Value assigned exactly once",seen.add(value));
            section.setValue(true);section.setValue(false);
        }
        assertEquals(original,fixture.getValues());assertEquals(original.size(),seen.size());
        assertTrue(seen.containsAll(original));assertEquals(0,fixture.changes);
        assertEquals("General",sections.get(0).getName());
        assertTrue(SettingSubcategories.create(fixture,original.subList(0,5)).isEmpty());
    }

    @Test public void semanticGroupsUseStableIdsForDenseAuraAndBridgeControls(){
        Module aura=new AuraFixture();
        for(String name:Arrays.asList("Mode","Reach","Adaptive Min Aim Speed","Movefix","Attack Mode","Autoblock Mode","Debug"))
            new CheckBoxValue(name,aura,false);
        Map<String,String> auraGroups=assignments(aura);
        assertEquals("Targets",auraGroups.get("mode"));
        assertEquals("Aiming",auraGroups.get("adaptive_min_aim_speed"));
        assertEquals("Movement",auraGroups.get("movefix"));
        assertEquals("Attacks",auraGroups.get("attack_mode"));
        assertEquals("Blocking",auraGroups.get("autoblock_mode"));
        assertEquals("Advanced",auraGroups.get("debug"));
        Module scaffold=new ScaffoldFixture();
        for(String name:Arrays.asList("Scaffold Approach","Keep Y","Adaptive Min Aim Speed","Sprint Mode","Place Delay","Clutch","Adaptive Noise Profile"))
            new CheckBoxValue(name,scaffold,false);
        Map<String,String> bridge=assignments(scaffold);
        assertEquals("Bridge",bridge.get("scaffold_approach"));
        assertEquals("Movement",bridge.get("sprint_mode"));
        assertEquals("Placement",bridge.get("place_delay"));
        assertEquals("Safety",bridge.get("clutch"));
        assertEquals("Aiming",bridge.get("adaptive_noise_profile"));
    }

    @Test public void targetEspUsesThreeCompleteSemanticSectionsInPresentationOrder(){
        TargetESP target=new TargetESP();
        List<Value<?>> original=new ArrayList<>(target.getValues());
        List<SubcategoryValue> sections=SettingSubcategories.create(target,target.getValues());
        List<String> names=new ArrayList<>();
        for(SubcategoryValue section:sections)names.add(section.getName());
        assertEquals(Arrays.asList("Halo","Hitpoint","Appearance"),names);
        Map<String,String> groups=assignments(target);
        assertEquals(original.size(),groups.size());
        for(String id:Arrays.asList("target_halo","halo_radius_padding","halo_width","halo_speed","halo_trail","halo_motion"))
            assertEquals(id,"Halo",groups.get(id));
        for(String id:Arrays.asList("hitpoint","hitpoint_source","hitpoint_size","hitpoint_ray_length","show_ray_misses"))
            assertEquals(id,"Hitpoint",groups.get(id));
        for(String id:Arrays.asList("opacity","glow_strength","through_walls","color_source","custom_color"))
            assertEquals(id,"Appearance",groups.get(id));
        assertEquals("Presentation must preserve live value registration",original,target.getValues());
    }

    @Test public void targetEspSectionsRetainTheirSwitchesWhenTheirOptionalChildrenAreHidden(){
        TargetESP target=new TargetESP();
        List<SubcategoryValue> sections=SettingSubcategories.create(target,target.getValues());
        assertEquals(6,sections.get(0).visibleCount());
        assertEquals(5,sections.get(1).visibleCount());
        assertEquals(4,sections.get(2).visibleCount());
        target.getBooleanSetting("Target Halo").setValue(false);
        target.getBooleanSetting("Hitpoint").setValue(false);
        assertEquals(1,sections.get(0).visibleCount());assertTrue(sections.get(0).isVisible());
        assertEquals(1,sections.get(1).visibleCount());assertTrue(sections.get(1).isVisible());
        target.getModeSetting("Color Source").setValue("Custom");
        assertEquals(5,sections.get(2).visibleCount());
        assertEquals("Hitpoint",assignments(target).get("hitpoint_ray_length"));
    }

    @Test public void allFourSmartDisableModulesKeepOptInLifecycleValuesInSafety(){
        for(Module owner:Arrays.asList(new Aura(),new Scaffold(),new Stealer(),new InventoryManager())){
            Map<String,String> groups=assignments(owner);
            for(String name:Arrays.asList("Smart Disable on Death","Smart Disable on World Change")){
                CheckBoxValue option=owner.getBooleanSetting(name);
                assertNotNull(owner.getName()+": "+name,option);
                assertFalse("Lifecycle stops stay opt-in for "+owner.getName(),option.getValue());
                assertEquals(owner.getName()+": "+name,"Safety",groups.get(option.getId()));
            }
        }
    }
    private static Map<String,String> assignments(Module module){
        Map<String,String> result=new HashMap<>();
        for(SubcategoryValue section:SettingSubcategories.create(module,module.getValues()))
            for(Value<?> child:section.children())result.put(child.getId(),section.getName());
        return result;
    }

    private ClickModule openRow(){
        ClickModule row=ClickModule.forPanel(fixture,260);row.setOpen(true);layout(row);return row;
    }
    private void layout(ClickModule row){row.updatePanelAnimation(0);row.layout(10,10,260,new ClipRect(0,0,500,500));}
    private static <T> T widget(ClickModule row,Value<?> value,Class<T> type){
        for(Component.Sub<?> child:row.presentationValues())if(child.getValue()==value)return type.cast(child);
        throw new AssertionError("Missing original value "+value.getId());
    }

    @Test public void collapseImmediatelyRevokesEditingCaptureAndChildFocus(){
        ClickModule row=openRow();ClickSubcategory section=row.subcategories().get(0);
        assertTrue("Main settings start open",section.isOpen());
        ClickSlider slider=widget(row,fixture.count,ClickSlider.class);
        ClickCombo combo=widget(row,fixture.mode,ClickCombo.class);
        assertTrue(row.focusableValues().contains(slider));
        slider.keyTyped(' ',Keyboard.KEY_RETURN);assertTrue(slider.isEditing());
        combo.keyTyped(' ',Keyboard.KEY_RETURN);assertTrue(combo.isOpen());
        section.setOpen(false);
        assertFalse(slider.isEditing());assertFalse(combo.isOpen());
        assertFalse(row.focusableValues().contains(slider));assertSame(section,row.focusParent(slider));
        double before=fixture.count.getValue();
        // Old geometry exists during the closing animation, but cannot receive pointer input.
        row.mouseClicked((int)slider.getX()+40,(int)slider.getY()+26,0);
        row.mouseDragged((int)slider.getX()+240,(int)slider.getY()+26);
        assertEquals(before,fixture.count.getValue(),0);
        layout(row);assertTrue(slider.getClip().isEmpty());assertEquals(0,fixture.changes);
        section.keyTyped(' ',Keyboard.KEY_RIGHT);layout(row);
        slider.keyTyped(' ',Keyboard.KEY_RETURN);
        slider.keyTyped('7',Keyboard.KEY_NONE);slider.keyTyped('.',Keyboard.KEY_NONE);slider.keyTyped('2',Keyboard.KEY_NONE);slider.keyTyped('5',Keyboard.KEY_NONE);
        slider.keyTyped(' ',Keyboard.KEY_RETURN);assertEquals(7.25,fixture.count.getValue(),0);
        assertEquals(1,fixture.changes);
    }

    @Test public void hiddenValuesCancelInputAndRespectBothSectionAndViewportClips(){
        ClickModule row=openRow();ClickSlider slider=widget(row,fixture.count,ClickSlider.class);
        slider.keyTyped(' ',Keyboard.KEY_RETURN);fixture.count.setVisibility(module->false);layout(row);
        assertFalse(slider.isEditing());assertTrue(slider.getClip().isEmpty());assertFalse(row.focusableValues().contains(slider));
        assertEquals(2,row.subcategories().get(0).getValue().visibleCount());
        fixture.count.setVisibility(module->true);layout(row);
        ClipRect viewport=new ClipRect(10,10,260,70);
        row.layout(10,10,260,viewport);
        double before=fixture.count.getValue();
        row.mouseClicked((int)slider.getX()+40,(int)slider.getY()+26,0);
        assertEquals(before,fixture.count.getValue(),0);
        assertTrue("Offscreen settings remain keyboard destinations",row.focusableValues().contains(slider));
        for(Component.Sub<?> child:row.presentationValues())if(child.getClip()!=null&&!child.getClip().isEmpty()){
            assertTrue(child.getClip().y>=viewport.y);
            assertTrue(child.getClip().y+child.getClip().height<=viewport.y+viewport.height+.001f);
        }
    }

    @Test public void sectionSpringIsBoundedAndReducedMotionSnapsWithoutValueWrites(){
        settings.animations.setValue(true);ClickModule row=openRow();ClickSubcategory section=row.subcategories().get(0);
        for(int i=0;i<90;i++){
            section.setOpen(i%7<4);row.updatePanelAnimation(.016f);
            assertTrue(Float.isFinite(row.getHeight()));assertTrue(section.progress()>=0&&section.progress()<=1);
        }
        settings.reducedMotion.setValue(true);section.setOpen(false);row.updatePanelAnimation(0);assertEquals(0,section.progress(),0);
        section.setOpen(true);row.updatePanelAnimation(0);assertEquals(1,section.progress(),0);
        assertEquals(0,fixture.changes);
    }

    @Test public void approachTwoRemainsFlatAndUsesOriginalValues(){
        ThemeMode.select(ThemeMode.APPROACH_TWO);ClickModule row=ClickModule.forPanel(fixture,260,true);
        assertEquals(fixture.getValues().size(),row.presentationValues().size());
        for(Component.Sub<?> child:row.presentationValues())assertFalse(child instanceof ClickSubcategory);
        assertEquals(0,fixture.changes);
    }

    @Test public void panelRepairsFocusToDisclosureWhenItsChildIsCollapsed()throws Exception{
        ClickGui screen=new ClickGui();((GuiScreen)screen).width=640;((GuiScreen)screen).height=480;screen.initGui();
        @SuppressWarnings("unchecked") List<ClickModule> rows=(List<ClickModule>)field(screen,"modules");
        ClickModule row=rows.get(0);row.setOpen(true);layoutScreen(screen);
        ClickSlider slider=widget(row,fixture.count,ClickSlider.class);field(screen,"focus",slider);
        slider.keyTyped(' ',Keyboard.KEY_RETURN);row.subcategories().get(0).setOpen(false);layoutScreen(screen);
        assertSame(row.subcategories().get(0),field(screen,"focus"));assertFalse(slider.isEditing());
        row.setOpen(false);layoutScreen(screen);assertSame(row,field(screen,"focus"));
    }
    private static void layoutScreen(ClickGui screen)throws Exception{
        Method method=ClickGui.class.getDeclaredMethod("layoutModules",float.class);method.setAccessible(true);method.invoke(screen,0f);
    }
    private static Object field(Object owner,String name)throws Exception{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{
            Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    private static void field(Object owner,String name,Object value)throws Exception{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{
            Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);return;
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
}
