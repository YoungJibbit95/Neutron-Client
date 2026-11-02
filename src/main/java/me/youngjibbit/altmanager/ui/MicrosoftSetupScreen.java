package me.youngjibbit.altmanager.ui;

import me.youngjibbit.account.MicrosoftAuthConfig;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import static me.youngjibbit.altmanager.ui.AccountUi.*;

/** Recoverable setup for an unconfigured build. Never asks for account credentials. */
public class MicrosoftSetupScreen extends GuiScreen {
    private static final String GUIDE = "https://github.com/YoungJibbit95/Neutron-Client/blob/main/design/microsoft-browser-login.md";
    private final GuiScreen parent;
    private final Runnable retry;
    private final MicrosoftAuthConfig config;
    private final AccountUi ui = new AccountUi();
    private final AccountTextInput clientId = new AccountTextInput(false);
    private ClipRect panel, body, save, guide, back;
    private String error = "";
    private float scroll, contentHeight;
    private int focus;

    public MicrosoftSetupScreen(GuiScreen parent, Runnable retry) {
        this(parent, retry, new MicrosoftAuthConfig());
    }

    public MicrosoftSetupScreen(GuiScreen parent, Runnable retry, MicrosoftAuthConfig config) {
        this.parent = parent;
        this.retry = retry;
        this.config = config;
        try { clientId.value(config.clientId()); }
        catch (IOException failure) { error = "Could not read the saved app configuration. Check file permissions before saving."; }
    }

    @Override public void initGui() {
        buttonList.clear();
        float w = Math.min(510, width - 24), h = Math.min(350, height - 20);
        panel = new ClipRect((width - w) / 2, (height - h) / 2, w, h);
        body = new ClipRect(panel.x + 18, panel.y + 52, w - 36, h - 162);
        clientId.bounds(new ClipRect(body.x, panel.y + h - 88, body.width, 28));
        guide = new ClipRect(body.x, panel.y + h - 48, (body.width - 10) / 2, 28);
        save = new ClipRect(guide.x + guide.width + 10, guide.y, guide.width, 28);
        back = new ClipRect(panel.x + w - 68, panel.y + 14, 50, 25);
        clientId.focused = focus == 0;
        if (Keyboard.isCreated()) Keyboard.enableRepeatEvents(true);
    }

    @Override public void drawScreen(int mx, int my, float partialTicks) {
        ui.window(panel, () -> {
            text("Microsoft setup", body.x, panel.y + 20, 24, TEXT);
            control(back, "Back", mx, my, 3, false, true);
            try (GuiClip ignored = GuiClip.push(body)) {
                float y = body.y - scroll;
                if (!error.isEmpty()) y = paragraph(error, y, RED) + 10;
                y = paragraph("This build has no usable Microsoft application ID. Your current Minecraft session stays active.", y, TEXT) + 10;
                y = paragraph("The Neutron project owner must register a desktop app for personal Microsoft accounts and arrange Minecraft Services access. Players can ask for a configured build.", y, MUTED) + 10;
                y = paragraph("Already registered? Paste its public Application (client) ID below. Desktop redirect: http://localhost/callback", y, MUTED) + 10;
                y = paragraph("Saved once for this Windows user, shared by IntelliJ, Minecraft Launcher and Neutron Launcher. No password or client secret is needed.", y, MUTED);
                contentHeight = y - body.y + scroll;
            }
            if (contentHeight > body.height) {
                float thumb = Math.max(12, body.height * body.height / contentHeight);
                round(new ClipRect(body.x + body.width - 2, body.y + scroll / Math.max(1, contentHeight - body.height) * (body.height - thumb), 2, thumb), 1, MUTED);
            }
            text("APPLICATION (CLIENT) ID", body.x, clientId.bounds().y - 13, 12, MUTED);
            clientId.draw("Paste the registered application ID");
            control(guide, "Setup guide", mx, my, 1, false, true);
            control(save, "Save & retry", mx, my, 2, true, MicrosoftAuthConfig.valid(clientId.value()));
        });
    }

    private float paragraph(String message, float y, int color) {
        String line = "";
        for (String word : message.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font(15).getWidth(next) > body.width - 12) {
                text(line, body.x, y, 15, color); y += 14; line = word;
            } else line = next;
        }
        if (!line.isEmpty()) { text(line, body.x, y, 15, color); y += 14; }
        return y;
    }

    private void control(ClipRect rect, String label, int mx, int my, int index, boolean primary, boolean enabled) {
        button(rect, label, focus == index ? (int) rect.x + 2 : mx,
                focus == index ? (int) rect.y + 2 : my, primary, enabled, false);
    }

    protected void openScreen(GuiScreen screen) { mc.displayGuiScreen(screen); }

    protected void saveAndRetry() {
        try { config.save(clientId.value()); }
        catch (IllegalArgumentException invalid) { error = invalid.getMessage(); scroll = 0; return; }
        catch (IOException failure) { error = "Could not save the app ID. Check write access to your user folder and try again."; scroll = 0; return; }
        openScreen(parent);
        retry.run(); // Start only after closing setup; navigation must not cancel the new attempt.
    }

    private void openGuide() {
        Thread worker = new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IOException();
                Desktop.getDesktop().browse(URI.create(GUIDE));
            } catch (Exception failure) {
                mc.addScheduledTask(() -> { error = "Could not open the browser. Read design/microsoft-browser-login.md in the Neutron repository."; scroll = 0; });
            }
        }, "Microsoft setup guide");
        worker.setDaemon(true); worker.start();
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (button != 0) return;
        if (back.contains(x, y)) { openScreen(parent); return; }
        if (save.contains(x, y)) { focus = 2; clientId.focused = false; if (MicrosoftAuthConfig.valid(clientId.value())) saveAndRetry(); return; }
        if (guide.contains(x, y)) { focus = 1; clientId.focused = false; openGuide(); return; }
        clientId.click(x, y); if (clientId.focused) focus = 0;
    }

    @Override protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { openScreen(parent); return; }
        if (key == Keyboard.KEY_TAB) { focus = (focus + (isShiftKeyDown() ? 3 : 1)) % 4; clientId.focused = focus == 0; return; }
        if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) { scroll(key == Keyboard.KEY_PRIOR ? -48 : 48); return; }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (focus == 3) openScreen(parent);
            else if (focus == 1) openGuide();
            else saveAndRetry();
            return;
        }
        clientId.key(c, key, isCtrlKeyDown(), isShiftKeyDown());
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll(wheel > 0 ? -28 : 28);
    }

    private void scroll(float delta) { scroll = Math.max(0, Math.min(Math.max(0, contentHeight - body.height), scroll + delta)); }

    @Override public void onGuiClosed() {
        ui.close();
        if (Keyboard.isCreated()) Keyboard.enableRepeatEvents(false);
    }
}
