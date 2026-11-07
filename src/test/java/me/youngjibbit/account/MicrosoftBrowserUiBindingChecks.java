package me.youngjibbit.account;

import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.altmanager.thread.AccountLoginThread;
import me.youngjibbit.altmanager.ui.AccountFormScreen;
import me.youngjibbit.altmanager.ui.MicrosoftSetupScreen;
import me.youngjibbit.altmanager.ui.AccountTextInput;
import net.minecraft.client.gui.GuiScreen;
import me.youngjibbit.clickgui.utils.ClipRect;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual account actions -> login thread -> loopback/HTTP -> queued session application. Runs in its own JVM. */
public class MicrosoftBrowserUiBindingChecks {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    static class Form extends AccountFormScreen {
        GuiScreen opened;
        Form(Mode mode){super(null,mode);}
        @Override protected void openScreen(GuiScreen screen){opened=screen;}
        void clickMicrosoft() throws Exception {
            width=640;height=480;
            Method layout=AccountFormScreen.class.getDeclaredMethod("layout");layout.setAccessible(true);layout.invoke(this);
            ClipRect button=password.bounds();mouseClicked((int)button.x+2,(int)button.y+2,0);
        }
        AccountLoginThread worker(){return login;}
    }
    static class Library extends GuiAltManager {
        GuiScreen opened;
        Library(AccountManager accounts){super(accounts);}
        @Override protected void openScreen(GuiScreen screen){opened=screen;}
    }
    static class Setup extends MicrosoftSetupScreen {
        GuiScreen opened;
        Setup(GuiScreen parent,Runnable retry,MicrosoftAuthConfig config){super(parent,retry,config);}
        @Override protected void openScreen(GuiScreen screen){opened=screen;}
        void save(){saveAndRetry();}
    }
    @Test public void missingConfigurationOpensSetupForMenuFormsAndSavedRowsWithoutChangingSession() throws Exception {
        Field coordinatorField=AccountLoginThread.class.getDeclaredField("coordinator");coordinatorField.setAccessible(true);
        Object previousCoordinator=coordinatorField.get(null);GuiAltManager previousScreen=GuiAltManager.INSTANCE;
        try {
            AccountManager accounts=new AccountManager(temporary.newFolder("setup-ui"));
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger installed=new AtomicInteger();
            MicrosoftBrowserAuth unavailable=new MicrosoftBrowserAuth("",null,uri->{fail("No browser before registration");},100);
            coordinatorField.set(null,new LoginCoordinator(accounts,new AccountAuthenticator(unavailable),main::add,
                    (session,provider)->installed.incrementAndGet(),account->{}));
            for(AccountFormScreen.Mode mode:new AccountFormScreen.Mode[]{AccountFormScreen.Mode.DIRECT,AccountFormScreen.Mode.ADD}) {
                Form form=new Form(mode);form.clickMicrosoft();failed(form.worker(),main);
                form.updateScreen();assertTrue(form.opened instanceof MicrosoftSetupScreen);assertNull(form.worker());
            }
            Library library=new Library(accounts);
            Method menu=GuiAltManager.class.getDeclaredMethod("activateMenu",int.class);menu.setAccessible(true);menu.invoke(library,4);
            failed(library.loginThread,main);library.updateScreen();assertTrue(library.opened instanceof MicrosoftSetupScreen);
            Account saved=accounts.addAccount(new Account("KnownProfile",null,"KnownProfile",Account.Provider.MICROSOFT));
            saved.setMicrosoftProfileId(MicrosoftBrowserAuthTest.PROFILE);
            library.opened=null;library.login(saved);failed(library.loginThread,main);library.updateScreen();
            assertTrue(library.opened instanceof MicrosoftSetupScreen);assertEquals(0,installed.get());
            assertEquals(1,accounts.getAccounts().size());assertSame(saved,accounts.getAccountById(saved.getId()));
        } finally {coordinatorField.set(null,previousCoordinator);GuiAltManager.INSTANCE=previousScreen;}
    }
    private static void failed(AccountLoginThread worker,Queue<Runnable> main)throws Exception {
        worker.join(3000);assertFalse(worker.isAlive());Runnable task;while((task=main.poll())!=null)task.run();
        assertTrue(worker.getStatus(),worker.isSetupRequired());
    }
    @Test public void setupValidatesBeforeSavingAndNavigatesBeforeRetry() throws Exception {
        java.nio.file.Path file=temporary.newFolder("setup-save").toPath().resolve("microsoft-auth.properties");
        MicrosoftAuthConfig config=new MicrosoftAuthConfig(new java.util.Properties(),java.util.Collections.emptyMap(),file);
        GuiScreen parent=new GuiScreen() {};AtomicInteger retries=new AtomicInteger();
        Setup[] screen=new Setup[1];screen[0]=new Setup(parent,()->{assertSame(parent,screen[0].opened);retries.incrementAndGet();},config);
        Field field=MicrosoftSetupScreen.class.getDeclaredField("clientId");field.setAccessible(true);
        AccountTextInput input=(AccountTextInput)field.get(screen[0]);input.value("invalid");screen[0].save();
        assertEquals(0,retries.get());assertFalse(Files.exists(file));
        input.value(MicrosoftBrowserAuthTest.CLIENT);screen[0].save();
        assertEquals(1,retries.get());assertEquals(MicrosoftBrowserAuthTest.CLIENT,config.clientId());
    }
    static void finish(AccountLoginThread worker,Queue<Runnable> main,AtomicInteger installed,int expected) throws Exception {
        assertNotNull(worker);worker.join(3000);assertFalse(worker.isAlive());
        assertEquals(expected-1,installed.get());Runnable task;while((task=main.poll())!=null)task.run();
        assertEquals(worker.getStatus(),expected,installed.get());
    }
    @Test public void menuBothFormsAndSavedMicrosoftRowReachTheCompleteBrowserFlow() throws Exception {
        File directory=temporary.newFolder("browser-ui");
        Field coordinatorField=AccountLoginThread.class.getDeclaredField("coordinator");coordinatorField.setAccessible(true);
        Object previousCoordinator=coordinatorField.get(null);GuiAltManager previousScreen=GuiAltManager.INSTANCE;
        Thread mainThread=Thread.currentThread();AtomicInteger installed=new AtomicInteger();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(MicrosoftBrowserAuthTest.Services services=new MicrosoftBrowserAuthTest.Services()) {
            AtomicInteger saves=new AtomicInteger();
            AccountManager accounts=new AccountManager(directory) {
                @Override public synchronized boolean save(){saves.incrementAndGet();return super.save();}
            };
            LoginCoordinator coordinator=new LoginCoordinator(accounts,new AccountAuthenticator(services.auth()),main::add,
                    (session,provider)->{assertSame(mainThread,Thread.currentThread());assertEquals("BrowserPlayer",session.getUsername());installed.incrementAndGet();},account->{});
            coordinatorField.set(null,coordinator);GuiAltManager library=new GuiAltManager(accounts);
            Form direct=new Form(AccountFormScreen.Mode.DIRECT);direct.clickMicrosoft();finish(direct.worker(),main,installed,1);
            assertTrue(accounts.getAccounts().isEmpty());assertEquals(0,saves.get());
            assertFalse(new File(directory,"alts.json").exists());assertNull(accounts.getLastAlt());
            Form add=new Form(AccountFormScreen.Mode.ADD);add.clickMicrosoft();finish(add.worker(),main,installed,2);
            assertEquals(1,accounts.getAccounts().size());assertEquals(1,saves.get());assertTrue(new File(directory,"alts.json").isFile());
            Method menu=GuiAltManager.class.getDeclaredMethod("activateMenu",int.class);menu.setAccessible(true);menu.invoke(library,4);
            finish(library.loginThread,main,installed,3);
            Method login=GuiAltManager.class.getDeclaredMethod("login",Account.class);login.setAccessible(true);
            login.invoke(library,accounts.getLastAlt());finish(library.loginThread,main,installed,4);
            assertEquals(1,accounts.getAccounts().size());assertNull(accounts.getLastAlt().getPassword());assertNull(accounts.getLastAlt().getMicrosoftAccessToken());
            menu.invoke(library,4);AccountLoginThread cancelled=library.loginThread;
            cancelled.join(3000);assertFalse(cancelled.isAlive());
            library.showStatus("Navigation cancelled the pending account action");
            Runnable task;while((task=main.poll())!=null)task.run();assertEquals(4,installed.get());
            assertEquals(3,saves.get());assertEquals(25,services.calls.size());
        } finally {coordinatorField.set(null,previousCoordinator);GuiAltManager.INSTANCE=previousScreen;}
    }
}
