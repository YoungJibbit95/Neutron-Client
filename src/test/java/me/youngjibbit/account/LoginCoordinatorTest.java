package me.youngjibbit.account;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.util.Session;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LoginCoordinatorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final Queue<Runnable> clientQueue = new ConcurrentLinkedQueue<>();
    private final Session previous = session("Previous");
    private final AtomicReference<Session> current = new AtomicReference<>(previous);
    private final AtomicReference<Account> selected = new AtomicReference<>();
    private AccountManager manager;
    private Thread clientThread;

    private static Session session(String name) { return new Session(name, "0123456789abcdef0123456789abcdef", "synthetic-token", "mojang"); }
    private Account account(String name) { return new Account(name, "", name); }
    private LoginRequest request(String name) { return new LoginRequest(account(name), true, false); }
    private void drain() { Runnable task; while ((task = clientQueue.poll()) != null) task.run(); }

    @Before public void setup() throws Exception {
        manager = new AccountManager(temporary.newFolder()); clientThread = Thread.currentThread();
    }

    private LoginCoordinator coordinator(LoginCoordinator.Authenticator auth) {
        return new LoginCoordinator(manager, auth, clientQueue::add, (value, provider) -> {
            assertSame(clientThread, Thread.currentThread()); current.set(value);
        }, value -> { assertSame(clientThread, Thread.currentThread()); selected.set(value); });
    }
    private void worker(Runnable task) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> { try { task.run(); } catch (Throwable error) { failure.set(error); } });
        thread.start(); thread.join(5000); assertFalse(thread.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test public void successInstallsAndRemembersOnlyOnClientThread() throws Exception {
        LoginCoordinator coordinator = coordinator(request -> { assertNotSame(clientThread, Thread.currentThread()); return session("Success"); });
        LoginCoordinator.Attempt login = coordinator.begin(request("New"), null);
        worker(login);
        assertSame(previous, current.get()); assertTrue(manager.getAccounts().isEmpty()); assertNull(selected.get());
        drain(); assertEquals("Success", current.get().getUsername()); assertEquals(1, manager.getAccounts().size());
        assertSame(manager.getAccounts().get(0), manager.getLastAlt()); assertSame(manager.getLastAlt(), selected.get());
        assertTrue(manager.load()); assertEquals(1, manager.getAccounts().size());
        worker(coordinator.begin(request("New"), null)); drain(); assertEquals(1, manager.getAccounts().size());
    }

    @Test public void failedLoginRetainsSessionAndNeverExposesProviderException() throws Exception {
        LoginCoordinator coordinator = coordinator(request -> { throw new Exception("password token raw-response"); });
        LoginCoordinator.Attempt login = coordinator.begin(request("New"), null);
        worker(login); drain(); assertSame(previous, current.get()); assertTrue(manager.getAccounts().isEmpty());
        assertFalse(login.getStatus().contains("raw-response")); assertFalse(login.getStatus().contains("password token"));
    }

    @Test public void oldSlowSuccessCannotOverwriteNewerSuccess() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        LoginCoordinator coordinator = coordinator(request -> {
            if (request.getAccount().getEmail().equals("Old")) {
                entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new TimeoutException();
            }
            return session(request.getAccount().getEmail());
        });
        LoginCoordinator.Attempt old = coordinator.begin(request("Old"), null);
        Thread slow = new Thread(old); slow.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            worker(coordinator.begin(request("New"), null)); drain(); assertEquals("New", current.get().getUsername());
        } finally { release.countDown(); slow.join(5000); }
        drain(); assertEquals("New", current.get().getUsername()); assertEquals(1, manager.getAccounts().size());
        assertTrue(old.getStatus().contains("superseded"));
    }

    @Test public void alreadyQueuedOldSuccessIsDiscardedEvenWhenNewLoginFails() throws Exception {
        LoginCoordinator coordinator = coordinator(request -> {
            if (request.getAccount().getEmail().equals("New")) throw new Exception("failure");
            return session("Old");
        });
        worker(coordinator.begin(request("Old"), null));
        LoginCoordinator.Attempt latest = coordinator.begin(request("New"), null); worker(latest); drain();
        assertSame(previous, current.get()); assertTrue(latest.getStatus().contains("Login failed"));
        assertTrue(manager.getAccounts().isEmpty());
    }

    @Test public void selectionInvalidatesQueuedResultAndCallback() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LoginCoordinator coordinator = coordinator(request -> session("Old"));
        worker(coordinator.begin(request("Old"), calls::incrementAndGet)); coordinator.invalidate(); drain();
        assertSame(previous, current.get()); assertEquals(0, calls.get()); assertTrue(manager.getAccounts().isEmpty());
    }

    @Test public void removedAccountDoesNotReturnThroughLateLogin() throws Exception {
        Account account = manager.addAccount(account("Saved"));
        LoginCoordinator coordinator = coordinator(request -> session("Saved"));
        LoginCoordinator.Attempt login = coordinator.begin(new LoginRequest(account, true, true), null);
        worker(login); manager.remove(account); drain();
        assertSame(previous, current.get()); assertTrue(manager.getAccounts().isEmpty()); assertTrue(login.getStatus().contains("removed"));
    }

    @Test public void requestSnapshotsProviderIdentityAndCredentials() throws Exception {
        Account account = new Account("synthetic@example.invalid", "synthetic password", "Before", Account.Provider.MICROSOFT);
        LoginRequest request = new LoginRequest(account, true, false);
        account.setName("After"); account.setMicrosoftAccessToken("later token");
        Account copy = request.getAccount(); copy.setName("Other screen");
        assertEquals("Before", request.getAccount().getName()); assertNull(request.getAccount().getMicrosoftAccessToken());
        AtomicReference<Account.Provider> provider = new AtomicReference<>();
        LoginCoordinator coordinator = coordinator(snapshot -> { provider.set(snapshot.getProvider()); return session("Authenticated"); });
        worker(coordinator.begin(request, null)); drain();
        assertEquals(Account.Provider.MICROSOFT, provider.get());
        assertNull(manager.getAccounts().get(0).getPassword());
        assertNull(manager.getAccounts().get(0).getMicrosoftAccessToken());
        assertEquals("0123456789abcdef0123456789abcdef", manager.getAccounts().get(0).getMicrosoftProfileId());
    }

    @Test public void browserReusePreservesLegacyCredentialsAndMetadataOnAnExistingStableIdentity() throws Exception {
        Account legacy=new Account("0123456789abcdef0123456789abcdef","legacy-password","Legacy",Account.Provider.MICROSOFT);
        legacy.setMicrosoftAccessToken("legacy-token");legacy.setBanned(true);
        com.google.gson.JsonObject json=legacy.toJson();json.addProperty("futureMetadata","keep");legacy.fromJson(json);
        manager.addAccount(legacy);
        LoginCoordinator coordinator=coordinator(request->session("BrowserName"));
        worker(coordinator.begin(LoginRequest.microsoftBrowser(),null));drain();
        assertSame(legacy,manager.getAccounts().get(0));assertEquals(1,manager.getAccounts().size());
        assertEquals("legacy-password",legacy.getPassword());assertEquals("legacy-token",legacy.getMicrosoftAccessToken());
        assertTrue(legacy.isBanned());assertEquals("keep",legacy.toJson().get("futureMetadata").getAsString());
        assertEquals("synthetic-token",current.get().getToken());
    }

    @Test public void directMicrosoftLoginDoesNotMutateTheSavedRegistryOrLastUsedEntry() throws Exception {
        Account saved=manager.addAccount(account("Saved"));manager.setLastAlt(saved);manager.save();
        String before=manager.toJson().toString();
        LoginCoordinator coordinator=coordinator(request->session("DirectName"));
        worker(coordinator.begin(LoginRequest.microsoftBrowser(false),null));drain();
        assertEquals("DirectName",current.get().getUsername());assertEquals(before,manager.toJson().toString());
        assertSame(saved,manager.getLastAlt());
    }

    @Test public void browserCannotReplaceAKnownSavedProfileWithADifferentAccount() throws Exception {
        Account saved=new Account("saved@example.invalid", "legacy-password", "Saved", Account.Provider.MICROSOFT);
        saved.setMicrosoftProfileId("ffffffffffffffffffffffffffffffff");manager.addAccount(saved);
        LoginCoordinator coordinator=coordinator(request->session("Different"));
        LoginCoordinator.Attempt login=coordinator.begin(new LoginRequest(saved,true,true),null);
        worker(login);drain();
        assertSame(previous,current.get());assertSame(saved,manager.getAccounts().get(0));
        assertEquals("legacy-password",saved.getPassword());assertTrue(login.getStatus().contains("different Microsoft account"));
    }

    @Test public void providerInstallFailureRetainsPreviousSessionAndRegistry() throws Exception {
        LoginCoordinator coordinator = new LoginCoordinator(manager, request -> session("New"), clientQueue::add,
                (session, provider) -> { throw new Exception("raw provider response"); }, selected::set);
        LoginCoordinator.Attempt login = coordinator.begin(request("New"), null); worker(login); drain();
        assertSame(previous, current.get()); assertTrue(manager.getAccounts().isEmpty()); assertNull(selected.get());
        assertFalse(login.getStatus().contains("raw provider response"));
    }

    @Test public void saveFailureIsVisibleWithoutClaimingTheLoginWasPersisted() throws Exception {
        AccountManager failing = new AccountManager(temporary.newFolder()) {
            @Override protected void replaceFile(java.nio.file.Path source, java.nio.file.Path destination) throws java.io.IOException {
                throw new java.io.IOException("synthetic secret");
            }
        };
        AtomicInteger callbacks = new AtomicInteger();
        LoginCoordinator coordinator = new LoginCoordinator(failing, request -> session("New"), clientQueue::add,
                (value, provider) -> current.set(value), selected::set);
        LoginCoordinator.Attempt login = coordinator.begin(request("New"), callbacks::incrementAndGet);
        worker(login); drain();
        assertEquals("New", current.get().getUsername()); assertEquals(1, failing.getAccounts().size());
        assertTrue(login.getStatus().contains("could not be saved")); assertEquals(1, callbacks.get());
        assertFalse(login.getStatus().contains("synthetic secret"));
    }

    @Test public void thirdPartyEndpointsCannotBeInstalledByLegacyGlobalSwitch() throws Exception {
        me.youngjibbit.thealtening.AltService service = new me.youngjibbit.thealtening.AltService();
        for (me.youngjibbit.thealtening.AltService.EnumAltService provider : Arrays.asList(
                me.youngjibbit.thealtening.AltService.EnumAltService.THEALTENING,
                me.youngjibbit.thealtening.AltService.EnumAltService.NEWALT)) {
            try { service.switchService(provider); fail(); } catch (IllegalAccessException expected) { }
            assertEquals(me.youngjibbit.thealtening.AltService.EnumAltService.MOJANG, service.getCurrentService());
        }
        service.switchService(me.youngjibbit.thealtening.AltService.EnumAltService.MICROSOFT);
        assertEquals(me.youngjibbit.thealtening.AltService.EnumAltService.MICROSOFT, service.getCurrentService());
    }

    @Test public void invalidResultsCannotInstallSession() throws Exception {
        for (Session result : Arrays.asList(null, new Session("", "", "", "mojang"), new Session("Name", "id", "token", "microsoft"))) {
            LoginCoordinator coordinator = coordinator(request -> result);
            worker(coordinator.begin(request("New"), null)); drain(); assertSame(previous, current.get());
        }
    }

    @Test public void identityConflictIsDetectedBeforeChangingSession() throws Exception {
        Account incoming = account("Incoming");
        com.google.gson.JsonObject json = account("Existing").toJson(); json.addProperty("id", incoming.getId());
        Account conflicting = new Account(); conflicting.fromJson(json); manager.addAccount(conflicting);
        LoginCoordinator coordinator = coordinator(request -> session("Incoming"));
        LoginCoordinator.Attempt login = coordinator.begin(new LoginRequest(incoming, true, false), null);
        worker(login); drain();
        assertSame(previous, current.get()); assertTrue(login.getStatus().contains("identity conflict"));
        assertEquals(1, manager.getAccounts().size());
    }

    @Test public void realBackendOfflineAndUnavailableProviderPathsNeverNeedNetwork() throws Exception {
        String unused="http://127.0.0.1:1";
        AccountAuthenticator backend = new AccountAuthenticator(new MicrosoftBrowserAuth("",
                new MicrosoftBrowserAuth.Endpoints(unused,unused,unused,unused,unused,unused),
                uri -> { throw new AssertionError("Unconfigured login must not open a browser"); },1));
        Session offline = backend.authenticate(request("Offline"));
        assertEquals("Offline", offline.getUsername()); assertNotNull(offline.getSessionType());
        assertEquals("", offline.getToken()); assertEquals(offline.getPlayerID(), backend.authenticate(request("Offline")).getPlayerID());
        for (Account account : Arrays.asList(new Account("synthetic@alt.com", "unused", "name", Account.Provider.THEALTENING),
                new Account("Synthetic", null, "name", Account.Provider.MICROSOFT))) {
            try { backend.authenticate(new LoginRequest(account, false, false)); fail(); }
            catch (LoginCoordinator.LoginUnavailableException expected) { assertFalse(expected.getUserMessage().contains("synthetic@alt.com")); }
        }
    }
}
