package me.youngjibbit.gui.inventory;

import me.youngjibbit.core.inventory.InventoryAssistSession.Preview;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.UIUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

/** A real visible proposal, never a substitute OS cursor. Rendering cannot issue inventory actions. */
public final class InventoryAssistOverlay {
    private InventoryAssistOverlay() { }
    public static void draw(GuiContainer screen, Preview preview, FontRenderer font) {
        if (preview == null || font == null || !screen.inventoryAssistSlotVisible(preview.slot)) return;
        if (!preview.waiting && !screen.canInventoryAssist(preview.slot, 250_000_000L)) return;
        long now = System.nanoTime();
        long shown = screen.inventoryAssistPresentedAt(preview.token);
        float progress = preview.waiting ? 1 : shown == Long.MIN_VALUE ? 0
                : (float)Math.max(0, Math.min(1, (now - shown) / (double)Math.max(1, preview.durationNanos)));
        Theme theme = Theme.getDefaultTheme();
        int accent = preview.warning ? 0xFFFFB46C : theme.getPrimaryGradientRGB();
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            marker(screen, preview.slot, accent, true, progress);
            if (preview.companion >= 0 && preview.companion != preview.slot && screen.inventoryAssistSlotVisible(preview.companion))
                marker(screen, preview.companion, theme.getSecondaryGradientRGB(), false, progress);
            int top = screen.inventoryAssistTop(), bottom = top + screen.inventoryAssistHeight();
            boolean compact = screen.height - bottom < 32 && top < 32;
            int w = compact ? 68 : Math.min(screen.width - 12, 250), h = compact ? 13 : 28;
            int x = compact ? screen.inventoryAssistLeft() + screen.inventoryAssistWidth() - w - 6 : (screen.width - w) / 2;
            int y = compact ? top + 3 : screen.height - bottom >= 32 ? bottom + 3 : top - 31;
            x = Math.max(1, Math.min(screen.width - w - 1, x)); y = Math.max(1, Math.min(screen.height - h - 1, y));
            UIUtils.drawDecorationRound(x, y, w, h, 4, new java.awt.Color(0xEE151924, true));
            String title = compact ? preview.warning ? "Resync" : preview.waiting ? "Waiting" : "Auto " + preview.label
                    : preview.owner + " · " + preview.label;
            font.drawString(font.trimStringToWidth(title, w - 12), x + 6, y + (compact ? 2 : 4), 0xFFF0F3FF);
            if (!compact) font.drawString(font.trimStringToWidth(preview.waiting ? "Manual input remains available"
                    : "Mouse or keyboard input pauses assistance", w - 12), x + 6, y + 15, 0xFFADB4C8);
            Gui.drawRect(x + 3, y + h - 1, x + 3 + Math.round((w - 6) * progress), y + h, accent | 0xFF000000);
        }
        screen.inventoryAssistWasRendered(preview.token, now);
    }
    private static void marker(GuiContainer screen, int index, int color, boolean primary, float progress) {
        Slot slot = screen.inventorySlots.getSlot(index);
        int x = screen.inventoryAssistLeft() + slot.xDisplayPosition, y = screen.inventoryAssistTop() + slot.yDisplayPosition;
        int edge = color | 0xFF000000;
        Gui.drawRect(x - 2, y - 2, x + 18, y + 18, (primary ? 0x26000000 : 0x14000000) | color & 0xFFFFFF);
        for (int i = 0; i < 2; i++) {
            int dx = i == 0 ? -2 : 13;
            Gui.drawRect(x + dx, y - 2, x + dx + 5, y - 1, edge);
            Gui.drawRect(x + dx, y + 17, x + dx + 5, y + 18, edge);
        }
        for (int i = 0; i < 2; i++) {
            int dy = i == 0 ? -2 : 13;
            Gui.drawRect(x - 2, y + dy, x - 1, y + dy + 5, edge);
            Gui.drawRect(x + 17, y + dy, x + 18, y + dy + 5, edge);
        }
        if (primary) Gui.drawRect(x, y + 16, x + Math.round(progress * 16), y + 17, edge);
    }
}
