package me.youngjibbit.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.util.Session;

public final class AccountAuthenticator implements LoginCoordinator.Authenticator {
    public static final String MICROSOFT_SETUP = MicrosoftBrowserAuth.SETUP;
    private final MicrosoftBrowserAuth browser;
    private final OpenAuthBrowserAuth embedded;
    private final MicrosoftAuthConfig configuration;
    public AccountAuthenticator() { this(new MicrosoftBrowserAuth(),new OpenAuthBrowserAuth(),new MicrosoftAuthConfig()); }
    AccountAuthenticator(MicrosoftBrowserAuth browser) { this(browser,null,new MicrosoftAuthConfig()); }
    AccountAuthenticator(MicrosoftBrowserAuth browser,OpenAuthBrowserAuth embedded,MicrosoftAuthConfig configuration) {
        this.browser=browser;this.embedded=embedded;this.configuration=configuration;
    }
    @Override public void cancel() { browser.cancel();if(embedded!=null)embedded.cancel(); }
    @Override public Session authenticate(LoginRequest request, java.util.function.BooleanSupplier active) throws Exception {
        return request.getProvider()==Account.Provider.MICROSOFT ? microsoft(active) : authenticate(request);
    }
    private Session microsoft(java.util.function.BooleanSupplier active) throws Exception {
        // An explicitly configured own app keeps the PKCE system-browser path.
        // Unconfigured Windows builds use the requested OpenAuth compatibility window.
        if(embedded!=null) {
            String clientId;
            try { clientId=configuration.clientId(); }
            catch(java.io.IOException failure) {
                throw new LoginCoordinator.LoginUnavailableException("The Microsoft app configuration could not be read. Check Microsoft setup.",true);
            }
            if(clientId.isEmpty())return embedded.login(active);
        }
        return browser.login(active);
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
                return microsoft(() -> true);
            case THEALTENING:
                // The existing adapter uses HTTP and global endpoint mutation. Never send its token to Microsoft.
                throw new LoginCoordinator.LoginUnavailableException(ALTENING_SETUP);
            default: throw new IllegalArgumentException("Unsupported account provider.");
        }
    }
}
