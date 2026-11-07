package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.render.ClickGUI;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.input.Keyboard;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Real screen handlers and config storage, with only the window/focus boundary replaced. */
public class NavigationLifecycleTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    public static class Client extends Minecraft {
        public Client(){super(null);}
        @Override public void displayGuiScreen(GuiScreen next){
            if(currentScreen!=null)currentScreen.onGuiClosed();
            currentScreen=next;
            if(next!=null)next.setWorldAndResolution(this,800,500);
        }
    }
    static class Panel extends ClickGui {
        void escape()throws Exception{keyTyped(' ',Keyboard.KEY_ESCAPE);}
        // GPU resource disposal is covered by the separate Pbuffer fixture.
        @Override public void onGuiClosed(){}
    }
    static class Library extends SideGuiScreen {
        Library(GuiScreen parent){super(parent);}
        void go(GuiScreen next){open(next);}
        void escape()throws Exception{keyTyped(' ',Keyboard.KEY_ESCAPE);}
        float left(){return window().x;}
    }
    static class Configs extends ClickLibraryScreen {
        Configs(GuiScreen parent,ConfigManager configs,ThemeCatalog themes){super(parent,false,configs,themes);}
        void escape()throws Exception{keyTyped(' ',Keyboard.KEY_ESCAPE);}
        void click(me.youngjibbit.clickgui.utils.ClipRect bounds) throws Exception {
            mouseClicked((int) bounds.x + 2, (int) bounds.y + 2, 0);
            mouseReleased(0, 0, 0);
        }
        void type(String text) throws Exception {
            for (char c : text.toCharArray()) keyTyped(c, Keyboard.KEY_NONE);
        }
    }
    static class QueuedScreen extends GuiScreen {
        int mouseEvents,keyboardEvents,mouseCalls,keyboardCalls;
        GuiScreen next;
        @Override protected boolean nextMouseEvent(){if(mouseEvents==0)return false;mouseEvents--;return true;}
        @Override protected boolean nextKeyboardEvent(){if(keyboardEvents==0)return false;keyboardEvents--;return true;}
        @Override public void handleMouseInput(){mouseCalls++;mc.displayGuiScreen(next);}
        @Override public void handleKeyboardInput(){keyboardCalls++;mc.displayGuiScreen(next);}
    }
    private Client client; private ModuleManager previous; private ClickGUI owner;
    @Before public void setup()throws Exception{
        Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        client=(Client)((Unsafe)f.get(null)).allocateInstance(Client.class);
        previous=Pulsar.INSTANCE.getMm();owner=new ClickGUI();
        ClientSettings settings=new ClientSettings();settings.animations.setValue(true);settings.reducedMotion.setValue(false);
        setManager(new ModuleManager(Arrays.asList(owner,settings)));
    }
    @After public void restore()throws Exception{if(client.currentScreen!=null)client.currentScreen.onGuiClosed();setManager(previous);}
    private void setManager(ModuleManager manager)throws Exception{Field f=Pulsar.class.getDeclaredField("mm");f.setAccessible(true);f.set(Pulsar.INSTANCE,manager);}

    @Test public void panelEscapeAfterConfigRoundTripClosesWhenOwnerAlreadyDisabled()throws Exception{
        ConfigManager configs=new ConfigManager(temporary.newFolder().toPath(),Arrays.asList(owner),()->true,message->{});
        assertTrue(configs.saveConfig("DisabledGui"));
        ThemeCatalog themes=new ThemeCatalog(new ThemeStore(temporary.newFolder().toPath().resolve("themes.json")));
        Panel panel=new Panel();Library library=new Library(panel);Configs configScreen=new Configs(library,configs,themes);
        client.displayGuiScreen(panel);client.displayGuiScreen(library);client.displayGuiScreen(configScreen);
        assertTrue(configs.loadConfig("DisabledGui"));assertFalse(owner.isToggled());
        configScreen.escape();assertSame(library,client.currentScreen);
        library.escape();assertSame(panel,client.currentScreen);panel.escape();
        assertNull("ESC must close the visible panel even when the loaded module flag is false",client.currentScreen);
        assertFalse("Closing must not reactivate the module saved by the config",owner.isToggled());
    }
    @Test public void emptySearchCannotLoadOrDeleteThePreviouslySelectedProfile() throws Exception {
        java.nio.file.Path folder = temporary.newFolder().toPath();
        ConfigManager storage = new ConfigManager(folder, Arrays.asList(owner), () -> false, message -> {});
        owner.setKey(31);
        assertTrue(storage.saveConfig("Practice"));
        owner.setKey(45);
        Configs screen = new Configs(null, storage, new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
        client.displayGuiScreen(screen);
        screen.click(screen.itemBounds(0));
        screen.click(screen.searchBounds());
        screen.type("missing");
        screen.keyTyped('\0', Keyboard.KEY_DOWN);
        screen.keyTyped('\0', Keyboard.KEY_RETURN);
        assertEquals("Enter in empty search results loaded a hidden config", 45, owner.getKey());
        screen.click(screen.actionBounds(1));
        assertEquals("Load must require a visible selection", 45, owner.getKey());
        screen.click(screen.actionBounds(2));
        screen.keyTyped('\0', Keyboard.KEY_RETURN);
        assertTrue("A filtered-out config must survive Delete/Enter", java.nio.file.Files.exists(folder.resolve("Practice.json")));
    }

    @Test public void downArrowInNameFieldDoesNotWriteAProfile() throws Exception {
        java.nio.file.Path folder = temporary.newFolder().toPath();
        ConfigManager storage = new ConfigManager(folder, Arrays.asList(owner), () -> false, message -> {});
        Configs screen = new Configs(null, storage, new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
        client.displayGuiScreen(screen);
        screen.click(screen.nameBounds());
        screen.type("NewProfile");
        screen.keyTyped('\0', Keyboard.KEY_DOWN);
        assertFalse("An arrow key wrote a new config", java.nio.file.Files.exists(folder.resolve("NewProfile.json")));
        screen.keyTyped('\0', Keyboard.KEY_RETURN);
        assertTrue(java.nio.file.Files.exists(folder.resolve("NewProfile.json")));
    }

    @Test public void deleteRequiresSelectionEvenWhenAnotherProfileMatchesSearch() throws Exception {
        java.nio.file.Path folder = temporary.newFolder().toPath();
        ConfigManager storage = new ConfigManager(folder, Arrays.asList(owner), () -> false, message -> {});
        assertTrue(storage.saveConfig("Alpha"));
        owner.setKey(56);
        assertTrue(storage.saveConfig("Beta"));
        owner.setKey(45);
        Configs screen = new Configs(null, storage, new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
        client.displayGuiScreen(screen);
        screen.type("Al");
        screen.keyTyped('\0', Keyboard.KEY_DOWN);
        screen.click(screen.searchBounds());
        screen.keyTyped('\0', Keyboard.KEY_BACK);
        screen.keyTyped('\0', Keyboard.KEY_BACK);
        screen.type("beTA");
        screen.click(screen.actionBounds(2));
        screen.keyTyped('\0', Keyboard.KEY_RETURN);
        assertTrue(java.nio.file.Files.exists(folder.resolve("Alpha.json")));
        assertTrue(java.nio.file.Files.exists(folder.resolve("Beta.json")));
        screen.click(screen.itemBounds(0));
        screen.keyTyped('\0', Keyboard.KEY_RETURN);
        assertEquals("Selecting the matching row enables Load", 56, owner.getKey());
    }

    @Test public void libraryRoutesImmediatelyInsteadOfWaitingUntilItHasDisappeared(){
        Library library=new Library(new GuiScreen(){});GuiScreen destination=new GuiScreen(){};
        client.displayGuiScreen(library);library.go(destination);
        assertSame("The shared compositor must own the whole transition",destination,client.currentScreen);
    }
    @Test public void numpadEnterLoadsTheFocusedConfigRow() throws Exception {
        java.nio.file.Path folder=temporary.newFolder().toPath();
        ConfigManager storage=new ConfigManager(folder,Arrays.asList(owner),()->false,message->{});
        owner.setKey(31);assertTrue(storage.saveConfig("Practice"));owner.setKey(45);
        Configs screen=new Configs(null,storage,new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
        client.displayGuiScreen(screen);screen.click(screen.itemBounds(0));
        screen.keyTyped('\0',Keyboard.KEY_NUMPADENTER);
        assertEquals("Numpad Enter must activate the focused row",31,owner.getKey());
    }
    @Test public void numpadEnterLoadsTheFocusedActionButton() throws Exception {
        java.nio.file.Path folder=temporary.newFolder().toPath();
        ConfigManager storage=new ConfigManager(folder,Arrays.asList(owner),()->false,message->{});
        owner.setKey(31);assertTrue(storage.saveConfig("Practice"));owner.setKey(45);
        Configs screen=new Configs(null,storage,new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
        client.displayGuiScreen(screen);screen.click(screen.itemBounds(0));
        screen.keyTyped('\0',Keyboard.KEY_TAB);screen.keyTyped('\0',Keyboard.KEY_NUMPADENTER);
        assertEquals("Numpad Enter must activate the focused Load button",31,owner.getKey());
    }
    @Test public void emptyResultsKeepSearchEditableAfterEnterOrDown() throws Exception {
        for(int key:new int[]{Keyboard.KEY_DOWN,Keyboard.KEY_RETURN,Keyboard.KEY_NUMPADENTER}) {
            java.nio.file.Path folder=temporary.newFolder().toPath();
            ConfigManager storage=new ConfigManager(folder,Arrays.asList(owner),()->false,message->{});
            owner.setKey(31);assertTrue(storage.saveConfig("Practice"));owner.setKey(45);
            Configs screen=new Configs(null,storage,new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
            client.displayGuiScreen(screen);screen.type("missing");screen.keyTyped('\0',key);
            for(int i=0;i<7;i++)screen.keyTyped('\0',Keyboard.KEY_BACK);
            screen.type("Practice");screen.keyTyped('\0',Keyboard.KEY_RETURN);screen.keyTyped('\0',Keyboard.KEY_RETURN);
            assertEquals("Search lost focus on empty results, key="+key,31,owner.getKey());
        }
    }
    @Test public void searchSelectionRevealsTheFirstRowAfterScrolling() throws Exception {
        java.nio.file.Path folder=temporary.newFolder().toPath();
        ConfigManager storage=new ConfigManager(folder,Arrays.asList(owner),()->false,message->{});
        for(int i=0;i<24;i++)assertTrue(storage.saveConfig(String.format("Profile%02d",i)));
        Configs screen=new Configs(null,storage,new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
        client.displayGuiScreen(screen);screen.keyTyped('\0',Keyboard.KEY_DOWN);
        for(int i=0;i<23;i++)screen.keyTyped('\0',Keyboard.KEY_DOWN);
        assertTrue("control: first row has scrolled out",screen.itemBounds(0).y<screen.listBounds().y);
        screen.click(screen.searchBounds());screen.keyTyped('\0',Keyboard.KEY_DOWN);
        assertTrue("keyboard selection must be visible",screen.itemBounds(0).y>=screen.listBounds().y);
        assertTrue(screen.itemBounds(0).y+screen.itemBounds(0).height<=screen.listBounds().y+screen.listBounds().height);
    }
    @Test public void deleteEscapeRetainsButtonFocusAndRequiresFreshConfirmation() throws Exception {
        java.nio.file.Path folder=temporary.newFolder().toPath();
        ConfigManager storage=new ConfigManager(folder,Arrays.asList(owner),()->false,message->{});
        assertTrue(storage.saveConfig("Practice"));
        Configs screen=new Configs(null,storage,new ThemeCatalog(new ThemeStore(folder.resolve("themes.json"))));
        client.displayGuiScreen(screen);screen.click(screen.itemBounds(0));screen.click(screen.actionBounds(2));
        screen.keyTyped('\0',Keyboard.KEY_ESCAPE);assertSame(screen,client.currentScreen);
        assertTrue(java.nio.file.Files.exists(folder.resolve("Practice.json")));
        screen.keyTyped('\0',Keyboard.KEY_NUMPADENTER);
        assertTrue("first activation must only reopen confirmation",java.nio.file.Files.exists(folder.resolve("Practice.json")));
        screen.keyTyped('\0',Keyboard.KEY_NUMPADENTER);
        assertFalse(java.nio.file.Files.exists(folder.resolve("Practice.json")));
    }
    @Test public void libraryReentryStartsAtItsFinalGeometryAndEscapeRemainsUsable()throws Exception{
        GuiScreen parent=new GuiScreen(){};Library library=new Library(parent);
        client.displayGuiScreen(library);
        assertEquals("No second local entry slide underneath the compositor",125,library.left(),.01);
        client.displayGuiScreen(new GuiScreen(){});client.displayGuiScreen(library);
        assertEquals(125,library.left(),.01);library.escape();assertSame(parent,client.currentScreen);
    }
    @Test public void pointerHidesKeyboardCueAndTabRestoresIt() throws Exception {
        class FocusScreen extends QueuedScreen {
            void tab() throws Exception { dispatchKeyTyped(' ', Keyboard.KEY_TAB); }
            @Override protected void keyTyped(char ch, int key) {}
            @Override public void handleMouseInput() {}
        }
        FocusScreen screen = new FocusScreen();
        client.displayGuiScreen(screen);
        boolean initial = GuiScreen.showsKeyboardFocus();
        me.youngjibbit.gui.UiSpring state = new me.youngjibbit.gui.UiSpring();
        try {
            screen.tab();
            state.step(0, false, true, false, true, null);
            assertTrue(state.keyboardFocused());
            screen.mouseEvents = 1;
            screen.handleInput();
            assertTrue(state.focused());
            assertFalse(state.keyboardFocused());
            screen.tab();
            assertTrue(state.keyboardFocused());
        } finally {
            if (!initial) { screen.mouseEvents = 1; screen.handleInput(); }
        }
    }

    @Test public void queuedClicksCannotDispatchIntoAClosedScreen()throws Exception{
        QueuedScreen screen=new QueuedScreen();screen.mouseEvents=2;screen.keyboardEvents=1;screen.next=new GuiScreen(){};
        client.displayGuiScreen(screen);screen.handleInput();
        assertSame(screen.next,client.currentScreen);assertEquals(1,screen.mouseCalls);assertEquals(0,screen.keyboardCalls);
        assertEquals("Keep remaining events for the active screen's next pump",1,screen.mouseEvents);assertEquals(1,screen.keyboardEvents);
    }
    @Test public void queuedKeysCannotDispatchAfterEscapeChangesScreen()throws Exception{
        QueuedScreen screen=new QueuedScreen();screen.keyboardEvents=2;screen.next=new GuiScreen(){};
        client.displayGuiScreen(screen);screen.handleInput();
        assertSame(screen.next,client.currentScreen);assertEquals(1,screen.keyboardCalls);assertEquals(1,screen.keyboardEvents);
    }
}
