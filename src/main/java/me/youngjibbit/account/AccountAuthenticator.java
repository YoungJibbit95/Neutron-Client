package me.youngjibbit.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.util.Session;

public final class AccountAuthenticator implements LoginCoordinator.Authenticator {
    public static final String MICROSOFT_SETUP = MicrosoftBrowserAuth.SETUP;
    private final MicrosoftBrowserAuth browser;
    public AccountAuthenticator() { this(new MicrosoftBrowserAuth()); }
    AccountAuthenticator(MicrosoftBrowserAuth browser) { this.browser=browser; }
    @Override public void cancel() { browser.cancel(); }
    @Override public Session authenticate(LoginRequest request, java.util.function.BooleanSupplier active) throws Exception {
        return request.getProvider()==Account.Provider.MICROSOFT ? browser.login(active) : authenticate(request);
    }
    public static final String ALTENING_SETUP = "TheAltening token login needs a supported secure provider integration; Microsoft password login is not used.";

    @Override public Session authenticate(LoginRequest request) throws Exception {
        Account account = request.getAccount();
        switch (request.getProvider()) {
            case OFFLINE:
                if (!account.getEmail().matches("[a-zA-Z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid offline name.");
                String id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + account.getEmail()).getBytes(StandardCharsets.UTF_8)).toString();
                return new Session(account.getEmail(), id, "", "mojang");
            case MICROSOFT:
                return browser.login(() -> true);
            case THEALTENING:
                // The existing adapter uses HTTP and global endpoint mutation. Never send its token to Microsoft.
                throw new LoginCoordinator.LoginUnavailableException(ALTENING_SETUP);
            default: throw new IllegalArgumentException("Unsupported account provider.");
        }
    }
}
