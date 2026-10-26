package me.youngjibbit.altmanager.thread;

import me.youngjibbit.Pulsar;
import me.youngjibbit.account.*;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.thealtening.AltService;
import net.minecraft.client.Minecraft;

public class AccountLoginThread extends Thread {
    private static LoginCoordinator coordinator;
    private final LoginRequest request;
    private final Runnable onSuccess;
    private LoginCoordinator.Attempt attempt;

    public AccountLoginThread(String email, String password) {
        this(new LoginRequest(new Account(email, password, email), true, false), null);
    }

    public AccountLoginThread(Account account) {
        this(new LoginRequest(account, true, true), null);
    }

    public AccountLoginThread(LoginRequest request, Runnable onSuccess) {
        super("Account login");
        this.request = request;
        this.onSuccess = onSuccess;
        setDaemon(true);
    }

    private static synchronized LoginCoordinator coordinator() {
        if (coordinator == null) {
            Minecraft mc = Minecraft.getMinecraft();
            coordinator = new LoginCoordinator(Pulsar.INSTANCE.getAccountManager(), new AccountAuthenticator(),
                    task -> mc.addScheduledTask(task),
                    (session, provider) -> {
                        // Microsoft sessions still use Mojang's Minecraft session service.
                        AltService.EnumAltService service = provider == Account.Provider.THEALTENING
                                ? AltService.EnumAltService.THEALTENING : AltService.EnumAltService.MOJANG;
                        Pulsar.INSTANCE.getAltService().switchService(service);
                        mc.setSession(session);
                    },
                    account -> { if (GuiAltManager.INSTANCE != null) GuiAltManager.INSTANCE.currentAccount = account; });
        }
        return coordinator;
    }

    @Override public synchronized void start() {
        if (getState() != State.NEW) throw new IllegalThreadStateException();
        attempt = coordinator().begin(request, onSuccess);
        super.start();
    }

    @Override public void run() { if (attempt != null) attempt.run(); }
    public String getStatus() { return attempt == null ? "&eWaiting for login..." : attempt.getStatus(); }
    public void cancelLogin() { if(attempt!=null)attempt.cancel(); }
    public static void cancelPending() { coordinator().invalidate(); }
}
