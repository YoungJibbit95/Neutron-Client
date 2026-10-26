package net.minecraft.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.youngjibbit.Pulsar;
import me.youngjibbit.account.Account;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.altmanager.thread.AccountLoginThread;
import me.youngjibbit.utils.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.IChatComponent;

public class GuiDisconnected extends GuiScreen
{
    private String reason;
    private IChatComponent message;
    private List<String> multilineMessage;
    private final GuiScreen parentScreen;
    private int field_175353_i;

    public static ServerData serverData;

    private String status = "&bWhat do you want to do?";
    private AccountLoginThread loginThread;

    public GuiDisconnected(GuiScreen screen, String reasonLocalizationKey, IChatComponent chatComp)
    {
        this.parentScreen = screen;
        this.reason = I18n.format(reasonLocalizationKey, new Object[0]);
        this.message = chatComp;
    }

    /**
     * Fired when a key is typed (except F11 which toggles full screen). This is the equivalent of
     * KeyListener.keyTyped(KeyEvent e). Args : character (character on the key), keyCode (lwjgl Keyboard key code)
     */
    protected void keyTyped(char typedChar, int keyCode) throws IOException
    {
    }

    /**
     * Adds the buttons (and other controls) to the screen in question. Called when the GUI is displayed and when the
     * window resizes, the buttonList is cleared beforehand.
     */
    public void initGui()
    {
        this.buttonList.clear();
        this.multilineMessage = this.fontRendererObj.listFormattedStringToWidth(this.message.getFormattedText(), this.width - 50);
        this.field_175353_i = this.multilineMessage.size() * this.fontRendererObj.FONT_HEIGHT;
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height / 2 + this.field_175353_i / 2 + this.fontRendererObj.FONT_HEIGHT, I18n.format("gui.toMenu", new Object[0])));
        this.buttonList.add(new GuiButton(1, this.width / 2 + 103, this.height / 2 + this.field_175353_i / 2 + this.fontRendererObj.FONT_HEIGHT, 90, 20, "Go to AltManager"));
        this.buttonList.add(new GuiButton(2, this.width / 2 + 103, this.height / 2 + this.field_175353_i / 2 + 33, 90, 20, "Set Banned"));
        this.buttonList.add(new GuiButton(4, this.width / 2 + 103, this.height / 2 + this.field_175353_i / 2 + 57, 90, 20, "Remove Alt"));
        this.buttonList.add(new GuiButton(5, this.width / 2 - 100, this.height / 2 + this.field_175353_i / 2 + 33, "Relog with new Alt (Normal, not banned)"));
        this.buttonList.add(new GuiButton(6, this.width / 2 - 100, this.height / 2 + this.field_175353_i / 2 + 57, "Relog with new Alt (Normal, banned)"));
        this.buttonList.add(new GuiButton(7, this.width / 2 - 100, this.height / 2 + this.field_175353_i / 2 + 81, "Relog with new Alt (The Altening)"));
        this.buttonList.add(new GuiButton(8, this.width / 2 + 103, this.height / 2 + this.field_175353_i / 2 + 81, 90, 20, "Relog"));
    }

    /**
     * Called by the controls from the buttonList when activated. (Mouse pressed for buttons)
     */
    protected void actionPerformed(GuiButton button) throws IOException
    {
        if (button.id == 0 || button.id == 1)
            Minecraft.getMinecraft().setServerData(null);

        if (button.id == 0)
        {
            this.mc.displayGuiScreen(this.parentScreen);
        }

        if (button.id == 1)
            this.mc.displayGuiScreen(GuiAltManager.INSTANCE);
        if (button.id == 2) {
            loginThread = null;
            if (GuiAltManager.INSTANCE.currentAccount == null) {
                status = "&cYou are not using any alt...";
                return;
            }
            GuiAltManager.INSTANCE.currentAccount.setBanned(true);
            status = Pulsar.INSTANCE.getAccountManager().save()
                    ? "&eThe account has been marked banned." : "&cBan flag could not be saved.";
        }
        if (button.id == 4) {
            loginThread = null;
            if (GuiAltManager.INSTANCE.currentAccount == null) {
                status = "&cYou are not using any alt...";
                return;
            }
            if (GuiAltManager.INSTANCE.loginThread != null)
                GuiAltManager.INSTANCE.loginThread = null;
            AccountLoginThread.cancelPending();
            Pulsar.INSTANCE.getAccountManager().remove(GuiAltManager.INSTANCE.currentAccount);
            boolean saved = Pulsar.INSTANCE.getAccountManager().save();
            Pulsar.INSTANCE.getAccountManager().setLastAlt(null);
            GuiAltManager.INSTANCE.currentAccount = null;

            status = saved ? "&eThe account has been removed." : "&cAccount removed in memory; saving failed.";
        }
        if (button.id == 5 || button.id == 6) {
            ArrayList<Account> registry = (button.id == 5 ? Pulsar.INSTANCE.getAccountManager().getNotBannedAccounts() : Pulsar.INSTANCE.getAccountManager().getAccounts());
            Random random = new Random();
            if (registry.size() == 0) {
                status = "&cYou don't have any account eligible for this.";
                return;
            }

            Account randomAlt = registry.get(random.nextInt(registry.size()));

            AccountLoginThread login = new AccountLoginThread(
                    new me.youngjibbit.account.LoginRequest(randomAlt, true, true), () -> {
                        if (mc.currentScreen == this && serverData != null)
                            mc.displayGuiScreen(new GuiConnecting(new GuiMainMenu(), mc, serverData));
                    });
            GuiAltManager.INSTANCE.loginThread = login;
            loginThread = login;
            login.start();
            status = "&eLogging in before reconnecting...";
        }
        if (button.id == 7) {
            loginThread = null;
            status = "&c" + me.youngjibbit.account.AccountAuthenticator.ALTENING_SETUP;
        }
        if (button.id == 8 && serverData != null && !(serverData.isConnected()))
            this.mc.displayGuiScreen((GuiScreen) new GuiConnecting(new GuiMainMenu(), this.mc, serverData));
    }

    /**
     * Draws the screen and all the components in it. Args : mouseX, mouseY, renderPartialTicks
     */
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        this.drawDefaultBackground();
        if (loginThread != null) status = loginThread.getStatus();
        this.drawCenteredString(this.fontRendererObj, Strings.translateColors(status), this.width / 2, 10, 0xffffff);
        this.drawCenteredString(this.fontRendererObj, this.reason, this.width / 2, this.height / 2 - this.field_175353_i / 2 - this.fontRendererObj.FONT_HEIGHT * 2, 11184810);
        int i = this.height / 2 - this.field_175353_i / 2;

        if (this.multilineMessage != null)
        {
            for (String s : this.multilineMessage)
            {
                this.drawCenteredString(this.fontRendererObj, s, this.width / 2, i, 16777215);
                i += this.fontRendererObj.FONT_HEIGHT;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
