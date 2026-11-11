package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.ClickGui;
import me.youngjibbit.clickgui.ClickLibraryScreen;
import me.youngjibbit.clickgui.component.impl.ClickModule;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;

/** Real screen layouts, handlers and config codec; no display or alternate settings store. */
public class ScreenStyleInteractionTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @ModuleInfo(name="Style A",category=Category.Combat)
    public static class A extends Module {
        final CheckBoxValue option=new CheckBoxValue("Option",this,false);
        final SliderValue amount=new SliderValue("Amount",this,5d,0,10);
        int enables;
        @Override public void onEnable(){enables++;}
    }
    @ModuleInfo(name="Style B",category=Category.Combat)
    public static class B extends Module {}
    @ModuleInfo(name="Style C",category=Category.Combat)
    public static class C extends Module {}
    private ModuleManager previous;
    private String oldTheme;
    private ClientSettings settings;
    private A first;
    private B second;
    private C third;
    @Before public void setup()throws Exception{
        previous=Pulsar.INSTANCE.getMm();oldTheme=ThemeMode.selected();
        settings=new ClientSettings();settings.animations.setValue(false);
        first=new A();second=new B();third=new C();
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,new ModuleManager(Arrays.asList(settings,first,second,third)));
        ThemeMode.select(ThemeMode.APPROACH_TWO);UiEffects.invalidate();
    }
    @After public void restore()throws Exception{
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,previous);ThemeMode.select(oldTheme);UiEffects.invalidate();
    }
    private static Object field(Object owner,Class<?> type,String name)throws Exception{
        Field value=type.getDeclaredField(name);value.setAccessible(true);return value.get(owner);
    }
    private static class Settings extends SettingsMenu {
        void size(int w,int h){width=w;height=h;initGui();}
        void key(int key)throws Exception{keyTyped('\0',key);}
        ClipRect row(int n){return rowBounds(n);}
        ClipRect control(int n){return controlBounds(n);}
    }
    private static class Menu extends MainMenu {
        int calls,last=-1;
        void size(int w,int h){width=w;height=h;initGui();}
        ClipRect action(int id){return actionBounds(id);}
        @Override protected void activate(int id){calls++;last=id;}
        void activateByMouse(int id)throws Exception{
            ClipRect r=actionBounds(id);int x=(int)(r.x+r.width/2),y=(int)(r.y+r.height/2);
            mouseClicked(x,y,0);mouseReleased(x,y,0);mouseReleased(x,y,0);
        }
    }
    private static class Panel extends ClickGui {
        void size(int w,int h)throws Exception{
            ((GuiScreen)this).width=w;((GuiScreen)this).height=h;initGui();layout();
        }
        void layout()throws Exception{
            Method layout=ClickGui.class.getDeclaredMethod("layoutModules",float.class);
            layout.setAccessible(true);layout.invoke(this,.1f);
        }
        void click(ClipRect r)throws Exception{mouseClicked((int)r.x+14,(int)r.y+12,0);mouseReleased(-1,-1,0);}
    }
    private static class Library extends ClickLibraryScreen {
        Library(ConfigManager configs,ThemeCatalog themes){super(null,true,configs,themes);}
        void size(){width=1024;height=768;initGui();}
        ClipRect item(int id){return itemBounds(id);}
        void select(int id)throws Exception{ClipRect r=itemBounds(id);mouseClicked((int)r.x+10,(int)r.y+10,0);}
    }

    @Test public void actualSettingsPopupSelectsCreativeAndKeepsLegacyRowsAndValues()throws Exception{
        assertEquals("Modern",settings.neutronScreenStyle.getValue());
        assertEquals(69,settings.getValues().size());
        assertSame(settings.lineTrail,settings.getValues().get(64));
        assertSame(settings.neutronScreenStyle,settings.getValues().get(65));
        assertEquals(Arrays.asList(settings.uiGlowMode,settings.flameMotion,settings.lineSpacing),
                settings.getValues().subList(66,69));
        Settings screen=new Settings();screen.size(1024,768);
        assertTrue(screen.row(1).y>screen.row(0).y);
        screen.key(Keyboard.KEY_END);screen.key(Keyboard.KEY_RETURN);
        assertSame(settings.neutronScreenStyle,field(screen,SettingsMenu.class,"popupValue"));
        screen.key(Keyboard.KEY_DOWN);screen.key(Keyboard.KEY_RETURN);
        assertEquals("Creative",settings.neutronScreenStyle.getValue());
        screen.size(1024,768);screen.key(Keyboard.KEY_HOME);
        assertEquals(screen.row(0).y,screen.row(1).y,0);
        assertTrue(screen.row(1).x>screen.row(0).x);
        assertTrue(screen.row(0).intersect(screen.row(1)).isEmpty());
        assertTrue(screen.row(0).contains(screen.control(0).x+1,screen.control(0).y+1));
        assertTrue(screen.row(0).contains(screen.control(0).x+screen.control(0).width-1,
                screen.control(0).y+screen.control(0).height-1));
        assertEquals(5,settings.roundness.getValue(),0);
        screen.size(334,234);screen.key(Keyboard.KEY_HOME);
        assertTrue(screen.row(1).y>screen.row(0).y);
        ThemeMode.select(ThemeMode.LEGACY);assertFalse(settings.neutronScreenStyle.isVisible());
    }

    @Test public void allNineMainMenuActionsRemainVisibleDisjointAndSingleShotInBothStyles()throws Exception{
        for(String style:new String[]{"Modern","Creative"}){
            settings.neutronScreenStyle.setValue(style);Menu menu=new Menu();
            for(int[] size:new int[][]{{1920,1080},{854,480},{367,245},{320,180}}){
                menu.size(size[0],size[1]);
                for(int id=0;id<9;id++){
                    ClipRect r=menu.action(id);
                    assertTrue(r.width>0&&r.height>0&&r.x>=0&&r.y>=0);
                    assertTrue(r.x+r.width<=size[0]&&r.y+r.height<=size[1]);
                    for(int other=0;other<id;other++)assertTrue(r.intersect(menu.action(other)).isEmpty());
                    int before=menu.calls;menu.activateByMouse(id);
                    assertEquals(before+1,menu.calls);assertEquals(id,menu.last);
                }
                if("Creative".equals(style))assertEquals(menu.action(0).y,menu.action(1).y,0);
                else assertTrue(menu.action(1).y>menu.action(0).y);
            }
        }
        ThemeMode.select(ThemeMode.LEGACY);Menu legacy=new Menu();legacy.size(854,480);
        assertTrue(legacy.action(1).y>legacy.action(0).y);
    }

    @Test public void creativeModuleGridUsesRealHeightsAndOriginalSingleToggleHandler()throws Exception{
        settings.neutronScreenStyle.setValue("Creative");Panel panel=new Panel();panel.size(854,480);
        @SuppressWarnings("unchecked") List<ClickModule> modules=(List<ClickModule>)field(panel,ClickGui.class,"modules");
        assertEquals(3,modules.size());
        ClipRect a=modules.get(0).presentationBounds(),b=modules.get(1).presentationBounds();
        assertEquals(a.y,b.y,0);assertTrue(a.intersect(b).isEmpty());assertTrue(a.width>=240);
        panel.click(a);assertEquals(1,first.enables);assertFalse(second.isToggled());assertFalse(third.isToggled());
        modules.get(0).setOpen(true);for(int i=0;i<30;i++)panel.layout();
        a=modules.get(0).presentationBounds();b=modules.get(1).presentationBounds();
        ClipRect next=modules.get(2).presentationBounds();
        assertTrue(next.y>=Math.max(a.y+a.height,b.y+b.height));
        assertFalse(first.option.getValue());assertEquals(5,first.amount.getValue(),0);
        panel.size(320,240);
        assertTrue(modules.get(1).presentationBounds().y>modules.get(0).presentationBounds().y);
    }

    @Test public void creativeThemeTilesChangeCompositionWithoutApplyingASelection()throws Exception{
        ConfigManager manager=new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(settings),()->false,s->{});
        ThemeCatalog catalog=new ThemeCatalog(null);String selected=catalog.selectedReference();
        try{
            Library modern=new Library(manager,catalog);modern.size();
            assertTrue(modern.item(3).y>modern.item(0).y);float modernHeight=modern.item(0).height;
            settings.neutronScreenStyle.setValue("Creative");Library creative=new Library(manager,catalog);creative.size();
            assertEquals(creative.item(0).y,creative.item(3).y,0);
            assertTrue(creative.item(0).height>modernHeight);
            creative.select(2);assertEquals(selected,catalog.selectedReference());
        }finally{manager.shutdown();}
    }

    @Test public void screenStylePersistsAndOldConfigsKeepTheModernMigrationDefault()throws Exception{
        ConfigManager manager=new ConfigManager(folder.newFolder().toPath(),Collections.singletonList(settings),()->false,s->{});
        try{
            settings.roundness.setValue(7.5);settings.neutronScreenStyle.setValue("Creative");
            assertTrue(manager.saveConfig("creative"));settings.neutronScreenStyle.setValue("Modern");
            assertTrue(manager.loadConfig("creative"));assertEquals("Creative",settings.neutronScreenStyle.getValue());
            com.google.gson.JsonObject json=new com.google.gson.JsonParser().parse(manager.serialize()).getAsJsonObject();
            json.getAsJsonObject("modules").entrySet().iterator().next().getValue().getAsJsonObject()
                    .getAsJsonObject("settings").remove(settings.neutronScreenStyle.getId());
            assertTrue(manager.saveConfig("old",json.toString()));assertTrue(manager.loadConfig("old"));
            assertEquals("Modern",settings.neutronScreenStyle.getValue());assertEquals(7.5,settings.roundness.getValue(),0);
        }finally{manager.shutdown();}
    }
}
