package me.youngjibbit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import me.youngjibbit.account.Account;
import me.youngjibbit.account.AccountManager;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.clickgui.ClickGui;
import me.youngjibbit.clickgui.utils.PanelUtils;
import me.youngjibbit.command.CommandManager;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.event.input.EventKey;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ArrayListHUDModule;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.module.impl.client.WatermarkHUDModule;
import me.youngjibbit.module.impl.movement.Sprint;
import me.youngjibbit.module.impl.player.FastPlace;
import me.youngjibbit.module.impl.render.FullBright;
import me.youngjibbit.thealtening.AltService;
import me.youngjibbit.utils.SlotSpoofHandler;
import me.youngjibbit.utils.Strings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.PingSpoofComponent;
import me.youngjibbit.utils.font.FontManager;
import me.zero.alpine.bus.EventBus;
import me.zero.alpine.bus.EventManager;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Session;
import org.lwjgl.opengl.Display;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Getter
public enum Pulsar implements Subscriber {
    INSTANCE;

    private final Minecraft mc = Minecraft.getMinecraft();
    public static final EventBus BUS = EventManager.builder()
            .setName("root/Pulsar")
            .setSuperListeners()
            .build();

    public final String name = "Neutron";
    public boolean devBuild = true;
    public final String version = devBuild ? "Dev 2.2" : "Private 1.1";
    private final String commandPrefix = ".";
    public final String author = "youngjibbit";
    public final String clientPrefix = "§l§b✎§d[§bN§de§bu§dt§br§do§bn§d] : §f";
    public final String debugPrefix = "&b✎&d[Neutron Debug] : ";
    private final String defaultUsername = "youngjibbit";
    private boolean destructed;

    private static final ScheduledExecutorService ex = Executors.newScheduledThreadPool(4);

    public FontManager fontManager;
    private ModuleManager mm;
    private ConfigManager configManager;
    private ClientRuntime runtime;
    private CommandManager cm;
    private ClickGui panel;
    private iOS iOS;
    private PanelUtils panelUtils;
    private SettingsMenu cs;
    private AccountManager accountManager;
    private AltService altService;
    public SlotSpoofHandler slotSpoofHandler;
    File accountsFolder = new File(System.getProperty("user.home"), "MinecraftAccounts");

    private int startTime;
    private long startTimeLong;

    public final void init() {
        System.out.println("Initializing Neutron...");
        BUS.subscribe(this);
        PingSpoofComponent.initialize();
        Display.setTitle(name + " | " + version + " by " + author + " for " + mc.getSession().getUsername());

        new GuiAltManager();

        if (!accountsFolder.exists()) {
            accountsFolder.mkdirs();
        }

        me.youngjibbit.theme.ThemeRuntime.initialize(mc.mcDataDir);

        String clientFolder = new File(".").getAbsolutePath();

        clientFolder = (clientFolder.contains("jars") ? new File(".").getAbsolutePath().substring(0, clientFolder.length() - 2) : new File(".").getAbsolutePath()) + Strings.getSplitter() + name;

        String accountManagerFolder = clientFolder + Strings.getSplitter() + "alts";

        mm = new ModuleManager();
        configManager = new ConfigManager();
        configManager.collectConfigs();
        runtime = new ClientRuntime(mm, configManager);
        me.youngjibbit.notifications.NotificationManager.releaseResources();
        if(fontManager!=null)fontManager.close();
        fontManager = new FontManager();
        initializeStartTime();
        cm = new CommandManager();
        panel = new ClickGui();
        iOS = new iOS();
        panelUtils = new PanelUtils();
        cs = new SettingsMenu();
        slotSpoofHandler = new SlotSpoofHandler();



        this.accountManager = new AccountManager(new File(accountManagerFolder));

        this.altService = new AltService();

        switchToMojang();

        if (mm != null) {
            System.out.println("ModuleManager initialized.");
        } else {
            System.err.println("ModuleManager initialization failed.");
        }

        if (cm != null) {
            System.out.println("CommandManager initialized.");
        } else {
            System.err.println("CommandManager initialization failed.");
        }

        // Defaults apply only on first use; a saved disabled state must win on restart.
        configManager.initialize(java.util.Arrays.asList("hud", "arraylist_hud", "watermark_hud", "fast_place", "fullbright", "sprint"));
    }

    public void updateCurrentTheme(Theme newTheme) {
        if(newTheme!=null)me.youngjibbit.theme.ThemeRuntime.select(newTheme.name());
    }
    public Theme getTheme(){return Theme.getDefaultTheme();}

    @Getter
    private Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();


    public Theme getCurrentTheme() {
        return Theme.getDefaultTheme();
    }

    public Theme getDefaultTheme() {
        return Theme.getDefaultTheme();
    }

    public GuiScreen getMainMenu() {
        return (GuiScreen)(this.destructed ? new GuiMainMenu() : new MainMenu());
    }

    public final void shutdown() {
        if (configManager != null) configManager.shutdown();
        me.youngjibbit.notifications.NotificationManager.releaseResources();
        if(fontManager!=null)fontManager.close();
        me.youngjibbit.utils.render.ArrayListSurface.releaseResources();
        me.youngjibbit.utils.render.TextEffects.releaseResources();
        me.youngjibbit.utils.render.Blur.releaseResources();
        me.youngjibbit.utils.render.UIUtils.releaseResources();
        me.youngjibbit.utils.render.GuiShaderBloom.releaseResources();
        me.youngjibbit.utils.render.shader.blur.GaussianBlur.releaseResources();
        me.youngjibbit.gui.MenuShader.releaseResources();
        me.youngjibbit.gui.MainMenu.releaseResources();
        me.youngjibbit.module.impl.render.SessionInfo.releaseRenderResources();
        System.out.println("Shutting down Neutron(Pulsar forever, fuck Geuxy, Pulsar is my Name bruh)...");
        PingSpoofComponent.shutdown();
        BUS.unsubscribe(this);
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public AltService getAltService() {
        return altService;
    }

    public void switchToMojang() {
        try {
            this.altService.switchService(AltService.EnumAltService.MOJANG);
        } catch (NoSuchFieldException e) {
            System.out.println("Couldn't switch to modank altservice");
        } catch (IllegalAccessException e) {
            System.out.println("Couldn't switch to modank altservice -2");
        }
    }

    public void switchToTheAltening() {
        try {
            this.altService.switchService(AltService.EnumAltService.THEALTENING);
        } catch (NoSuchFieldException e) {
            System.out.println("Couldn't switch to altening altservice");
        } catch (IllegalAccessException e) {
            System.out.println("Couldn't switch to altening altservice -2");
        }
    }

    public void loginWithMicrosoft(String email, String password) {
        mc.addScheduledTask(() -> {
            Account account = new Account(email, password, email, Account.Provider.MICROSOFT);
            new me.youngjibbit.altmanager.thread.AccountLoginThread(
                    new me.youngjibbit.account.LoginRequest(account, true, false), null).start();
        });
    }

    private void initializeStartTime() {
        startTime = (int) System.currentTimeMillis();
        startTimeLong = System.currentTimeMillis();
        System.out.println("Start time initialized: {} ms" + startTime);
    }

    // Set Minecraft session after login
    public void setSession(Session session) {
        mc.addScheduledTask(() -> {
            me.youngjibbit.altmanager.thread.AccountLoginThread.cancelPending();
            mc.setSession(session);
        });
    }

    public static ScheduledExecutorService getExecutor() {
        return ex;
    }

    @Subscribe
    private final Listener<TickEvent> configTick = new Listener<>(e -> {
        if (configManager != null) configManager.tick();
    });

    @Subscribe
    private final Listener<EventKey> listener = new Listener<>(e -> {
        if (this.mm != null) {
            this.mm.toggleByKey(e.getKey());
        }
    });

    public boolean isGhost() {
        return false;
    }
    public void setGhost(boolean g){

    }

    public void debugMessage(String message) {
        mc.thePlayer.addChatComponentMessage(new net.minecraft.util.ChatComponentText( clientPrefix + message));
    }

    public boolean isDestructed() {
        return this.destructed;
    }

    public void setDestructed(boolean destructed) {
        this.destructed = destructed;
    }

}
