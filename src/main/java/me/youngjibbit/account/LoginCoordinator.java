package me.youngjibbit.account;

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
        private volatile String status = "&eWaiting for login...";

        private Attempt(long ticket, LoginRequest request, Runnable onSuccess) {
            this.ticket = ticket;
            this.request = request;
            this.onSuccess = onSuccess;
        }

        public String getStatus() {
            return isCurrent(ticket) ? status : "&7Login superseded by a newer selection.";
        }

        public void cancel() { synchronized(LoginCoordinator.this) { if(isCurrent(ticket))invalidate(); } }

        @Override public void run() {
            if (!isCurrent(ticket)) return;
            client.accept(() -> { if (isCurrent(ticket)) status = request.isBrowserLogin() ? "&6Complete Microsoft sign-in in your browser..." : "&6Logging in..."; });
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
                    if (isCurrent(ticket)) status = failure instanceof LoginUnavailableException
                            ? "&c" + ((LoginUnavailableException) failure).getUserMessage()
                            : "&cLogin failed. Check credentials or provider availability; the current session was retained.";
                });
            }
        }

        private void complete(Session session) {
            if (!isCurrent(ticket)) return;
            Account account = request.getAccount();
            if (request.requiresExistingAccount() && manager.getAccountById(account.getId()) == null) {
                status = "&cLogin cancelled because the account was removed or reloaded.";
                return;
            }
            if (request.isBrowserLogin()) {
                if (account.getMicrosoftProfileId()!=null && !account.getMicrosoftProfileId().equalsIgnoreCase(session.getPlayerID())) {
                    status="&cA different Microsoft account was selected. The current session was retained.";
                    return;
                }
                account = new Account(session.getPlayerID(), null, session.getUsername(), Account.Provider.MICROSOFT);
                account.setMicrosoftProfileId(session.getPlayerID());
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
                Account stored = manager.addAccount(account);
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

    /** Only fixed, locally authored messages may be presented as provider setup errors. */
    public static final class LoginUnavailableException extends Exception {
        private final String userMessage;
        public LoginUnavailableException(String message) { userMessage = message; }
        public String getUserMessage() { return userMessage; }
    }
}
