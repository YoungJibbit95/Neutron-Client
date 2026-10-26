package me.youngjibbit.altmanager.components;

import me.youngjibbit.Pulsar;
import me.youngjibbit.account.Account;
import me.youngjibbit.altmanager.GuiAltManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class GuiAccountList extends GuiSlot {

    public int selected = -1;

    private GuiAltManager parent;

    public GuiAccountList(GuiAltManager parent) {
        super(Minecraft.getMinecraft(), parent.width, parent.height, 56, parent.height - 80, 40);
        this.parent = parent;
    }

    public int getSize() {
        return Pulsar.INSTANCE.getAccountManager().getAccounts().size();
    }

    public void elementClicked(int i, boolean b, int i1, int i2) {
        Account account = getAccount(i);
        if (account == null) return;
        if (!account.getId().equals(parent.selectedAccountId)) {
            me.youngjibbit.altmanager.thread.AccountLoginThread.cancelPending();
        }
        parent.selectedAccountId = account.getId();
        this.selected = i;
        if (b)
            this.parent.login(account);
    }

    protected boolean isSelected(int i) {
        Account account = getAccount(i);
        return account != null && account.getId().equals(parent.selectedAccountId);
    }

    protected void drawBackground() {
    }

    protected void drawSlot(int i, int i1, int i2, int i3, int i4, int i5) {
        Account account = getAccount(i);
        if (account == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        FontRenderer fontRenderer = minecraft.fontRendererObj;
        int x = i1 + 2;
        int y = i2;
        if (y >= scaledResolution.getScaledHeight() || y < 0)
            return;
        GL11.glTranslated(x, y, 0.0D);
        String name = account.getProvider() == Account.Provider.THEALTENING ? "TheAltening account" : account.getName();
        if (account.getProvider() != Account.Provider.THEALTENING) drawFace(name, 0, 6, 24, 24);
        fontRenderer.drawStringWithShadow(name, 30.0F, 6.0F, -1);
        fontRenderer.drawStringWithShadow(account.getProvider().name() + (account.isBanned() ? " | Banned" : ""), 30.0F, (6 + fontRenderer.FONT_HEIGHT + 2), -1);
        GL11.glTranslated(-x, -y, 0.0D);
    }

    public Account getAccount(int i) {
        java.util.List<Account> accounts = Pulsar.INSTANCE.getAccountManager().getAccounts();
        return i >= 0 && i < accounts.size() ? accounts.get(i) : null;
    }

    private void drawFace(String name, int x, int y, int w, int h) {
        try {
            AbstractClientPlayer.getDownloadImageSkin(AbstractClientPlayer.getLocationSkin(name), name).loadTexture(Minecraft.getMinecraft().getResourceManager());
            Minecraft.getMinecraft().getTextureManager().bindTexture(AbstractClientPlayer.getLocationSkin(name));
            GL11.glEnable(3042);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 24.0F, 24.0F, w, h, 192.0F, 192.0F);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 120.0F, 24.0F, w, h, 192.0F, 192.0F);
            GL11.glDisable(3042);
        } catch (Exception exception) {
        }
    }

    public void removeSelected() {
        Account selected = getSelectedAccount();
        if (selected == null) return;
        Pulsar.INSTANCE.getAccountManager().remove(selected);
        if (parent.currentAccount != null && parent.currentAccount.getId().equals(selected.getId())) parent.currentAccount = null;
        parent.selectedAccountId = null;
        this.selected = -1;
        Pulsar.INSTANCE.getAccountManager().save();
    }

    public Account getSelectedAccount() {
        return Pulsar.INSTANCE.getAccountManager().getAccountById(parent.selectedAccountId);
    }

}
