package me.youngjibbit.account;

/** A private snapshot: screens and mutable registry entries cannot change an in-flight login. */
public final class LoginRequest {
    private final Account account;
    private final boolean remember, existing, browser;

    public LoginRequest(Account account, boolean remember, boolean existing) {
        this(account, remember, existing, account.getProvider()==Account.Provider.MICROSOFT);
    }
    private LoginRequest(Account account, boolean remember, boolean existing, boolean browser) {
        this.browser=browser;
        this.account = new Account();
        this.account.fromJson(account.toJson());
        this.remember = remember;
        this.existing = existing;
    }

    public Account getAccount() {
        Account copy = new Account();
        copy.fromJson(account.toJson());
        return copy;
    }
    public static LoginRequest microsoftBrowser() { return microsoftBrowser(true); }
    public static LoginRequest microsoftBrowser(boolean remember) {
        return new LoginRequest(new Account("Microsoft browser", null, "Microsoft browser", Account.Provider.MICROSOFT),remember,false,true);
    }
    public boolean isBrowserLogin() { return browser; }
    public Account.Provider getProvider() { return account.getProvider(); }
    public boolean shouldRemember() { return remember; }
    public boolean requiresExistingAccount() { return existing; }
}
