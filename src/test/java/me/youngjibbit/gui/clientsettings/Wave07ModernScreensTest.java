package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.ClickLibraryScreen;
import me.youngjibbit.clickgui.ClickGui;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.clickgui.component.impl.ClickModule;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.value.impl.CheckBoxValue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Actual screen geometry, original module handler and config storage, headless only. */
public class Wave07ModernScreensTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @ModuleInfo(name="Wave07 UI fixture",category=Category.Render)
    public static class Fixture extends Module {
        final CheckBoxValue checked=new CheckBoxValue("Enabled",this,false);
        int changes;
        @Override public void onValueChange(){changes++;}
    }
    private ModuleManager previous;
    private String oldMode;
    private ClientSettings settings;
    private Fixture fixture;
    @Before public void before()throws Exception {
        previous=Pulsar.INSTANCE.getMm();oldMode=ThemeMode.selected();
        settings=new ClientSettings();fixture=new Fixture();
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,new ModuleManager(Arrays.asList(settings,fixture)));
    }
    @After public void after()throws Exception {
        Field mm=Pulsar.class.getDeclaredField("mm");mm.setAccessible(true);
        mm.set(Pulsar.INSTANCE,previous);ThemeMode.select(oldMode);
    }
    private static class Settings extends SettingsMenu {
        void setup(int w,int h){width=w;height=h;initGui();}
        ClipRect category(int n){return categoryBounds(n);}
        ClipRect visible(){return view();}
        ClipRect row(int n){return rowBounds(n);}
        ClipRect action(int n){return actionBounds(n);}
        ClipRect control(int n){return controlBounds(n);}
    }
    private static class Library extends ClickLibraryScreen {
        Library(ConfigManager configs){super(null,false,configs,new ThemeCatalog(null));}
        void setup(int w,int h){width=w;height=h;initGui();}
        ClipRect visible(){return listBounds();}
        ClipRect search(){return searchBounds();}
        ClipRect item(int id){return itemBounds(id);}
        ClipRect action(int id){return actionBounds(id);}
        ClipRect name(){return nameBounds();}
    }

    private static class Main extends MainMenu {
        void setup(int w,int h){width=w;height=h;initGui();}
        ClipRect action(int n){return actionBounds(n);}
    }
    private static float dimension(Object screen,Class<?> owner,String field)throws Exception{
        Field value=owner.getDeclaredField(field);value.setAccessible(true);return value.getFloat(screen);
    }
    @Test public void actualModernScreensKeepCompactDefaultFramesAndReadableControls()throws Exception{
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        Main menu=new Main();menu.setup(1920,1080);
        assertTrue(dimension(menu,MainMenu.class,"panelWidth")<=336);
        assertTrue(dimension(menu,MainMenu.class,"panelHeight")<=360);
        assertTrue(menu.action(0).height>=30);
        Settings preferences=new Settings();preferences.setup(1920,1080);
        assertTrue(dimension(preferences,SettingsMenu.class,"w")<=740);
        assertTrue(dimension(preferences,SettingsMenu.class,"h")<=448);
        assertEquals(30,preferences.control(0).height,0);
        ClickGui panel=new ClickGui();
        ((net.minecraft.client.gui.GuiScreen)panel).width=1920;
        ((net.minecraft.client.gui.GuiScreen)panel).height=1080;panel.initGui();
        assertTrue(dimension(panel,ClickGui.class,"width")<=760);
        assertTrue(dimension(panel,ClickGui.class,"height")<=448);
        ConfigManager manager=new ConfigManager(folder.newFolder().toPath(),
                Arrays.asList(settings,fixture),()->false,ignored->{});
        manager.initialize(Collections.emptyList());
        try{
            Library library=new Library(manager);library.setup(1920,1080);
            assertTrue(dimension(library,ClickLibraryScreen.class,"w")<=780);
            assertTrue(dimension(library,ClickLibraryScreen.class,"h")<=460);
            assertTrue(library.visible().width>360 && library.visible().height>300);
        }finally{manager.shutdown();}
        assertEquals(0,fixture.changes);
    }

    @Test public void modernSettingsHaveRealSeparateNavigationAndContentCoordinates(){
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        Settings s=new Settings();s.setup(854,480);
        ClipRect category=s.category(0),content=s.visible(),row=s.row(0);
        assertEquals(162,category.width,0);
        assertTrue(category.x+category.width<content.x);
        assertTrue(content.contains(row.x+5,row.y+5));
        assertTrue(s.action(5).y>row.y);
        assertEquals(5,settings.roundness.getValue(),0);
        ThemeMode.select(ThemeMode.LEGACY);
        s.setup(854,480);
        assertEquals(126,s.category(0).width,0);
        assertTrue(s.visible().x<content.x);
        assertEquals("Blur",settings.surface.getValue());
    }

    @Test public void actualLibraryKeepsApproachTwoPaneAndUsesLegacyGridWithoutConfigWrites()
            throws Exception {
        ConfigManager manager=new ConfigManager(folder.newFolder().toPath(),
                Arrays.asList(settings,fixture),()->false,ignored->{});
        manager.initialize(Collections.emptyList());
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        Library library=new Library(manager);library.setup(1024,768);
        ClipRect modern=library.visible();
        Field outer=ClickLibraryScreen.class.getDeclaredField("w");
        outer.setAccessible(true);
        assertEquals(780f,outer.getFloat(library),0);
        assertTrue(modern.width>100 && modern.height>100);
        assertTrue(library.search().y<modern.y);
        assertEquals(0,fixture.changes);
        ThemeMode.select(ThemeMode.LEGACY);
        library.setup(1024,768);
        assertEquals(720f,outer.getFloat(library),0);
        assertTrue("Legacy grid uses the content width",library.visible().width>modern.width);
        assertEquals(library.item(0).y,library.item(1).y,0);
        assertTrue(library.item(1).x>library.item(0).x+library.item(0).width);
        assertTrue("Legacy toolbar belongs below the name field",library.action(0).y>library.name().y+library.name().height);
        assertFalse(fixture.checked.getValue());
        assertEquals(0,fixture.changes);
        manager.shutdown();
    }

    @Test public void cardFactoryChangesOnlyPresentationAndNeverStoredModuleValues(){
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        ClickModule modern=ClickModule.forPanel(fixture,360,true);
        ClickModule legacy=ClickModule.forPanel(fixture,360);
        assertEquals(44f,modern.getHeight(),.001f);
        assertEquals(24f,legacy.getHeight(),.001f);
        for(int i=0;i<90;i++)modern.updatePanelAnimation(.016f);
        assertEquals(0,fixture.changes);
        assertFalse(fixture.checked.getValue());
        modern.setOpen(true);
        for(int i=0;i<30;i++)modern.updatePanelAnimation(.016f);
        assertFalse(fixture.checked.getValue());
        assertEquals(0,fixture.changes);
        modern.cancelInputs();
    }
}
