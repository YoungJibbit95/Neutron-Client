package me.youngjibbit.account;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.util.Session;

/** Network work is independent; all observable completion runs through the client executor. */
public final class LoginCoordinator {
    public interface Authenticator {
        Session authenticate(LoginRequest request) throws Exception;
        default Session authenticate(LoginRequest request, java.util.function.BooleanSupplier active) throws Exception { return authenticate(request); }
        default void cancel() { }
    }
    public interface SessionInstaller { void install(Session session, Account.Provider provider) throws Exception; }
    private final AccountManager manager;
    private final Authenticator authenticator;
    private final Consumer<Runnable> client;
    private final SessionInstaller installer;
    private final Consumer<Account> selection;
    private long generation;

    public LoginCoordinator(AccountManager manager, Authenticator authenticator, Consumer<Runnable> client,
                            SessionInstaller installer, Consumer<Account> selection) {
        this.manager = manager;
        this.authenticator = authenticator;
        this.client = client;
        this.installer = installer;
        this.selection = selection;
    }

    public synchronized Attempt begin(LoginRequest request, Runnable onSuccess) {
        long ticket=++generation;
        authenticator.cancel();
        return new Attempt(ticket, request, onSuccess);
    }
    public synchronized void invalidate() { generation++; authenticator.cancel(); }
    private synchronized boolean isCurrent(long ticket) { return ticket == generation; }

    public final class Attempt implements Runnable {
        private final long ticket;
        private final LoginRequest request;
        private final Runnable onSuccess;
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile String status = "&eWaiting for login...";
        private volatile boolean setupRequired;

        private Attempt(long ticket, LoginRequest request, Runnable onSuccess) {
            this.ticket = ticket;
            this.request = request;
            this.onSuccess = onSuccess;
        }

        public String getStatus() {
            return isCurrent(ticket) ? status : "&7Login superseded by a newer selection.";
        }

        public boolean isSetupRequired() { return isCurrent(ticket) && setupRequired; }

        public void cancel() { synchronized(LoginCoordinator.this) { if(isCurrent(ticket))invalidate(); } }

        @Override public void run() {
            if (!started.compareAndSet(false, true) || !isCurrent(ticket)) return;
            client.accept(() -> { if (isCurrent(ticket)) status = request.isBrowserLogin() ? "&6Preparing Microsoft sign-in. Complete the login window..." : "&6Logging in..."; });
            try {
                Session session = authenticator.authenticate(request, () -> isCurrent(ticket));
                if (session == null || session.getUsername() == null || !session.getUsername().matches("[a-zA-Z0-9_]{1,16}")
                        || session.getSessionType() == null || (request.getProvider() != Account.Provider.OFFLINE
                        && (session.getPlayerID() == null || session.getPlayerID().isEmpty() || session.getToken() == null || session.getToken().isEmpty()))) {
                    throw new IllegalStateException("Invalid login result.");
                }
                client.accept(() -> complete(session));
            } catch (Exception failure) {
                client.accept(() -> {
                    if (isCurrent(ticket)) {
                        setupRequired = failure instanceof LoginUnavailableException
                                && ((LoginUnavailableException) failure).isSetupRequired();
                        status = failure instanceof LoginUnavailableException
                            ? "&c" + ((LoginUnavailableException) failure).getUserMessage()
                            : "&cLogin failed. Check credentials or provider availability; the current session was retained.";
                    }
                });
            }
        }

        private void complete(Session session) {
            if (!isCurrent(ticket)) return;
            Account account = request.getAccount();
            Account existing = request.requiresExistingAccount() ? manager.getAccountById(account.getId()) : null;
            if (request.requiresExistingAccount() && existing == null) {
                status = "&cLogin cancelled because the account was removed or reloaded.";
                return;
            }
            if (request.isBrowserLogin()) {
                String expected = account.getMicrosoftProfileId();
                String liveProfile = existing == null ? null : existing.getMicrosoftProfileId();
                if ((expected != null && !sameProfile(expected, session.getPlayerID()))
                        || (liveProfile != null && !sameProfile(liveProfile, session.getPlayerID()))) {
                    status="&cA different Microsoft account was selected. The current session was retained.";
                    return;
                }
                account = liveProfile != null ? existing : new Account(session.getPlayerID(), null, session.getUsername(), Account.Provider.MICROSOFT);
            }
            if (request.shouldRemember()) {
                try { manager.resolveAccount(account); }
                catch (IllegalArgumentException conflict) {
                    status = "&cSaved account identity conflict. The current session was retained.";
                    return;
                }
            }
            try { installer.install(session, request.getProvider()); }
            catch (Exception failure) {
                status = "&cCould not switch the session provider. The current session was retained.";
                return;
            }
            boolean duplicate = false;
            if (request.shouldRemember()) {
                Account stored = account == existing ? existing : manager.addAccount(account);
                duplicate = stored != account && !request.requiresExistingAccount();
                account = stored;
            }
            if (request.isBrowserLogin()) {
                account.setMicrosoftProfileId(session.getPlayerID());
            }
            account.setName(session.getUsername());
            if (request.getProvider() == Account.Provider.MICROSOFT && !request.isBrowserLogin()) {
                account.setMicrosoftAccessToken(session.getToken());
                account.setMicrosoftProfileId(session.getPlayerID());
            }
            if (request.shouldRemember()) manager.setLastAlt(account);
            if (request.getProvider() == Account.Provider.THEALTENING) manager.setLastAlteningAlt(account.getEmail());
            selection.accept(account);
            status = request.shouldRemember() && !manager.save()
                    ? "&eLogged in for this session, but accounts could not be saved. See the account-file status."
                    : duplicate ? request.isBrowserLogin() ? "&aLogged in. Saved Microsoft profile selected."
                    : "&aLogged in. Existing saved credentials were retained." : "&aLogged in successfully.";
            if (onSuccess != null) onSuccess.run();
        }
    }

    private static boolean sameProfile(String stored, String received) {
        if (stored.equalsIgnoreCase(received)) return true;
        return stored.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                && stored.replace("-", "").equalsIgnoreCase(received);
    }

    /** Only fixed, locally authored messages may be presented as provider setup errors. */
    public static final class LoginUnavailableException extends Exception {
        private final String userMessage;
        private final boolean setupRequired;
        public LoginUnavailableException(String message) { this(message, false); }
        public LoginUnavailableException(String message, boolean setupRequired) {
            userMessage = message;
            this.setupRequired = setupRequired;
        }
        public String getUserMessage() { return userMessage; }
        public boolean isSetupRequired() { return setupRequired; }
    }
}
