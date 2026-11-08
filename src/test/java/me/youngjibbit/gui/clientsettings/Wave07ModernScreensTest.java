package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.ClickLibraryScreen;
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
    }
    private static class Library extends ClickLibraryScreen {
        Library(ConfigManager configs){super(null,false,configs,new ThemeCatalog(null));}
        void setup(int w,int h){width=w;height=h;initGui();}
        ClipRect visible(){return listBounds();}
        ClipRect search(){return searchBounds();}
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

    @Test public void actualLibraryUsesConsistentTwoPaneResponsiveLayoutWithoutConfigWrites()
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
        assertTrue(library.visible().width<modern.width);
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
