package me.youngjibbit.account;

import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.altmanager.thread.AccountLoginThread;
import me.youngjibbit.altmanager.ui.AccountFormScreen;
import me.youngjibbit.clickgui.utils.ClipRect;
import org.junit.Test;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual account actions -> login thread -> loopback/HTTP -> queued session application. Runs in its own JVM. */
public class MicrosoftBrowserUiBindingChecks {
    static class Form extends AccountFormScreen {
        Form(Mode mode){super(null,mode);}
        void clickMicrosoft() throws Exception {
            width=640;height=480;
            Method layout=AccountFormScreen.class.getDeclaredMethod("layout");layout.setAccessible(true);layout.invoke(this);
            ClipRect button=password.bounds();mouseClicked((int)button.x+2,(int)button.y+2,0);
        }
        AccountLoginThread worker(){return login;}
    }
    static void finish(AccountLoginThread worker,Queue<Runnable> main,AtomicInteger installed,int expected) throws Exception {
        assertNotNull(worker);worker.join(3000);assertFalse(worker.isAlive());
        assertEquals(expected-1,installed.get());Runnable task;while((task=main.poll())!=null)task.run();
        assertEquals(worker.getStatus(),expected,installed.get());
    }
    @Test public void menuBothFormsAndSavedMicrosoftRowReachTheCompleteBrowserFlow() throws Exception {
        File directory=Files.createTempDirectory("pulsar-browser-ui-").toFile();
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
