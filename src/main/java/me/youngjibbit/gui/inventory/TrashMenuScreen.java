package me.youngjibbit.gui.inventory;

import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.core.inventory.TrashItemCatalog;
import me.youngjibbit.core.inventory.TrashItemStore;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.module.impl.player.InventoryManager;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Explicit persistent trash rules. This screen never issues an inventory click or drops an item. */
public class TrashMenuScreen extends GuiScreen {
    private final GuiScreen parent;
    private final InventoryManager owner;
    private final TrashItemStore store;
    private final ArrayListSurface surface = new ArrayListSurface();
    private TrashItemCatalog catalog;
    private List<TrashItemCatalog.Entry> visible = Collections.emptyList();
    private String query = "", cachedQuery, message = "", undoKey, pressedKey;
    private boolean onlyListed, cachedOnlyListed, searchFocus = true, selectAll, undoPrevious;
    private boolean repeatBefore, repeatOwned;
    private long cachedRevision = -1, lastFrame;
    private int selected = -1, pressed = -1;
    private float x, y, w, h, scroll, targetScroll;

    public TrashMenuScreen(GuiScreen parent, InventoryManager owner) {
        if (owner == null) throw new IllegalArgumentException("Inventory manager is required");
        this.parent = parent; this.owner = owner; this.store = owner.getTrashStore();
    }
    @Override public void initGui() {
        if (catalog == null) catalog = owner.getTrashCatalog();
        if (Keyboard.isCreated()) {
            if (!repeatOwned) { repeatBefore = Keyboard.areRepeatEventsEnabled(); repeatOwned = true; }
            Keyboard.enableRepeatEvents(true);
        }
        lastFrame = System.nanoTime(); pressed = -1; refresh(); layout();
    }
    private void refresh() {
        if (query.equals(cachedQuery) && onlyListed == cachedOnlyListed && cachedRevision == store.revision()) return;
        cancelPress();
        String previous = selected >= 0 && selected < visible.size() ? visible.get(selected).key : null;
        visible = catalog.find(query, onlyListed, store.keys());
        cachedQuery = query; cachedOnlyListed = onlyListed; cachedRevision = store.revision();
        selected = -1;
        if (previous != null) for (int i = 0; i < visible.size(); i++)
            if (previous.equals(visible.get(i).key)) { selected = i; break; }
        if (selected < 0 && !visible.isEmpty()) selected = 0;
    }
    private void layout() {
        w = Math.min(800, Math.max(1, width - 20)); h = Math.min(490, Math.max(1, height - 20));
        x = (width - w) / 2; y = (height - h) / 2;
        targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll));
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }
    private void cancelPress() { pressed = -1; pressedKey = null; }
    private boolean compact() { return h < 260; }
    private int columns() { return Math.max(1, Math.min(4, (int)((w - 24) / 180))); }
    private float rowHeight() { return compact() ? 48 : 58; }
    public ClipRect searchBounds() { return new ClipRect(x + 14, y + (compact() ? 34 : 50), w - 28, 24); }
    public ClipRect listBounds() { float top = y + (compact() ? 68 : 84); return new ClipRect(x + 14, top, w - 28, Math.max(0, y + h - 60 - top)); }
    public ClipRect entryBounds(int index) {
        ClipRect list = listBounds(); float cw = (list.width - (columns() - 1) * 7) / columns();
        return new ClipRect(list.x + (index % columns()) * (cw + 7), list.y + (index / columns()) * rowHeight() - scroll, cw, rowHeight() - 6);
    }
    public ClipRect actionBounds(int action) {
        float bottom = y + h - 47;
        if (action == 0) return new ClipRect(x + w - 78, bottom, 64, 23);
        if (action == 1) return new ClipRect(x + 14, bottom, Math.min(126, Math.max(62, w * .24f)), 23);
        if (action == 2) return new ClipRect(x + 20 + Math.min(126, Math.max(62, w * .24f)), bottom, 54, 23);
        return new ClipRect(x + w - 138, bottom, 54, 23);
    }
    private float maxScroll() { return Math.max(0, ((visible.size() + columns() - 1) / columns()) * rowHeight() - listBounds().height); }
    private TTFFontRenderer font() { return ClickControlStyle.font(); }
    private void text(String text, float x, float y, float width, int color) {
        font().drawString(ClickControlStyle.fit(font(), text, width), x, y, color);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refresh(); layout(); long now = System.nanoTime();
        float dt = Math.min(.05f, Math.max(0, (now - lastFrame) / 1e9f)); lastFrame = now;
        scroll += (targetScroll - scroll) * UiEffects.response(dt, 24);
        Theme theme = Theme.getDefaultTheme();
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            if (mc.theWorld == null) me.youngjibbit.gui.MainMenu.drawBackdrop(theme);
            surface.panel(x, y, w, h, 9, theme, true, true, true, 8, .95f, .65f, () -> {
                try (GuiClip panel = GuiClip.push(new ClipRect(x + 2, y + 2, w - 4, h - 4))) {
                    LegacyUiStyle.icon("delete", x + 15, y + 12, 14, theme.getPrimaryGradientRGB());
                    text("Trash", x + 37, y + 11, w - 150, LegacyUiStyle.ink(theme));
                    int ruleCount = store.keys().size();
                    text(ruleCount + (ruleCount == 1 ? " rule" : " rules"), x + w - 98, y + 12, 82, LegacyUiStyle.MUTED);
                    if (!compact()) text("Choose exact variants · Ctrl+F search · Ctrl+L filter · Ctrl+Z undo", x + 15, y + 30, w - 30, LegacyUiStyle.MUTED);
                    ClipRect search = searchBounds();
                    MenuSurface.card(search.x, search.y, search.width, search.height, theme, searchFocus, search.contains(mouseX, mouseY));
                    LegacyUiStyle.icon("search", search.x + 7, search.y + 6, 12, LegacyUiStyle.MUTED);
                    String shown = query.isEmpty() ? "Search name, item ID or variant..." : query;
                    if (selectAll && searchFocus && !query.isEmpty()) UIUtils.drawDecorationRound(search.x + 25, search.y + 5,
                            Math.min(search.width - 49, font().getWidth(query) + 2), 13, 2, LegacyUiStyle.accent(theme, .2f));
                    text(shown, search.x + 25, search.y + 6, search.width - 51, query.isEmpty() ? LegacyUiStyle.FAINT : LegacyUiStyle.ink(theme));
                    if (!query.isEmpty()) LegacyUiStyle.icon("close", search.x + search.width - 19, search.y + 7, 10, LegacyUiStyle.MUTED);
                    ClipRect list = listBounds();
                    try (GuiClip clip = GuiClip.push(list)) {
                        int start = Math.max(0, (int)(scroll / rowHeight()) * columns());
                        int end = Math.min(visible.size(), ((int)((scroll + list.height) / rowHeight()) + 2) * columns());
                        for (int i = start; i < end; i++) drawEntry(i, mouseX, mouseY, theme);
                        if (visible.isEmpty()) text("No matching items", list.x + 8, list.y + 14, list.width - 16, LegacyUiStyle.MUTED);
                    }
                    if (maxScroll() > 0 && list.height > 0) {
                        float thumb = Math.max(15, list.height * list.height / (maxScroll() + list.height));
                        UIUtils.drawDecorationRound(list.x + list.width - 2, list.y + (list.height - thumb) * scroll / maxScroll(),
                                2, thumb, 1, LegacyUiStyle.accent(theme, .6f));
                    }
                    button(0, "Done", mouseX, mouseY, theme, true);
                    button(1, onlyListed ? "Blacklisted" : "All items", mouseX, mouseY, theme, true);
                    if (w >= 390) button(2, "Undo", mouseX, mouseY, theme, undoKey != null);
                    if (w >= 460) button(3, "Reload", mouseX, mouseY, theme, true);
                    String status = !store.lastError().isEmpty() ? store.lastError() : !message.isEmpty() ? message :
                            "Saved automatically · Named and custom items stay protected";
                    text(status, x + 14, y + h - 17, w - 28, !store.lastError().isEmpty() ? 0xffffacaa : LegacyUiStyle.MUTED);
                }
            });
        }
    }
    private void drawEntry(int index, int mx, int my, Theme theme) {
        TrashItemCatalog.Entry entry = visible.get(index); ClipRect r = entryBounds(index);
        boolean listed = store.contains(entry.key), hover = r.intersect(listBounds()).contains(mx, my);
        MenuSurface.card(r.x, r.y, r.width, r.height, theme, listed, hover || !searchFocus && selected == index);
        if (mc.getRenderItem() != null) try (RenderState state = new RenderState()) {
            float oldZ = mc.getRenderItem().zLevel;
            try {
                GlStateManager.enableDepth(); GlStateManager.depthFunc(GL11.GL_LEQUAL); GlStateManager.depthMask(true);
                GlStateManager.enableAlpha(); GlStateManager.enableTexture2D();
                RenderHelper.enableGUIStandardItemLighting(); mc.getRenderItem().zLevel = 100;
                mc.getRenderItem().renderItemAndEffectIntoGUI(entry.icon(), Math.round(r.x + 9), Math.round(r.y + 10));
            } finally { mc.getRenderItem().zLevel = oldZ; RenderHelper.disableStandardItemLighting(); }
        }
        text(entry.name, r.x + 34, r.y + 9, r.width - 53, LegacyUiStyle.ink(theme));
        text(entry.registryId.replace("minecraft:", ""), r.x + 34, r.y + 24, r.width - 43, LegacyUiStyle.MUTED);
        if (!compact()) text(entry.variant, r.x + 9, r.y + 39, r.width - 20, LegacyUiStyle.FAINT);
        LegacyUiStyle.icon(listed ? "check" : "plus", r.x + r.width - 18, r.y + 9, 10,
                listed ? theme.getPrimaryGradientRGB() : LegacyUiStyle.FAINT);
    }
    private void button(int id, String label, int mx, int my, Theme theme, boolean enabled) {
        ClipRect b = actionBounds(id); MenuSurface.card(b.x, b.y, b.width, b.height, theme,
                id == 1 && onlyListed, enabled && b.contains(mx, my));
        text(label, b.x + 9, b.y + 7, b.width - 18, enabled ? LegacyUiStyle.ink(theme) : LegacyUiStyle.FAINT);
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button != 0) return;
        refresh(); layout(); cancelPress();
        if (searchBounds().contains(mx, my)) {
            searchFocus = true; selectAll = false;
            if (mx >= searchBounds().x + searchBounds().width - 25) setQuery("");
            return;
        }
        searchFocus = false; selectAll = false;
        if (listBounds().contains(mx, my)) for (int i = 0; i < visible.size(); i++)
            if (entryBounds(i).intersect(listBounds()).contains(mx, my)) { selected = i; pressed = i; pressedKey = visible.get(i).key; return; }
        for (int i = 0; i < 4; i++) if ((i != 2 || w >= 390) && (i != 3 || w >= 460) && actionBounds(i).contains(mx, my)) {
            pressed = -10 - i; return;
        }
    }
    @Override protected void mouseReleased(int mx, int my, int state) {
        refresh();
        int hit = pressed; String key = pressedKey; cancelPress();
        if (state != 0) return;
        if (hit >= 0 && hit < visible.size() && visible.get(hit).key.equals(key) && entryBounds(hit).intersect(listBounds()).contains(mx, my)) toggle(visible.get(hit));
        if (hit <= -10) { int action = -10 - hit; if (actionBounds(action).contains(mx, my)) action(action); }
    }
    private void action(int id) {
        if (id == 0) open(parent);
        if (id == 1) { onlyListed = !onlyListed; scroll = targetScroll = 0; refresh(); }
        if (id == 2 && undoKey != null) {
            try { store.setBlacklisted(undoKey, undoPrevious); undoKey = null; message = "Last rule change undone"; owner.trashRulesChanged(); refresh(); }
            catch (IOException ex) { message = "Could not undo: " + ex.getMessage(); }
        }
        if (id == 3) { store.reload(); owner.trashRulesChanged(); message = store.lastError().isEmpty() ? "Trash list reloaded" : ""; refresh(); }
    }
    private void toggle(TrashItemCatalog.Entry entry) {
        boolean old = store.contains(entry.key);
        try {
            store.setBlacklisted(entry.key, !old); undoKey = entry.key; undoPrevious = old;
            message = (old ? "Kept: " : "Blacklisted: ") + entry.name;
            owner.trashRulesChanged(); refresh();
        } catch (IOException ex) { message = "Not saved; rule unchanged: " + ex.getMessage(); }
    }
    private void setQuery(String value) { query = value; selectAll = false; scroll = targetScroll = 0; refresh(); }
    protected boolean ctrl() { return isCtrlKeyDown(); }
    protected void open(GuiScreen screen) { mc.displayGuiScreen(screen); }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        cancelPress(); refresh();
        if (key == Keyboard.KEY_ESCAPE) { if (!query.isEmpty()) setQuery(""); else open(parent); return; }
        if (ctrl() && key == Keyboard.KEY_F) { searchFocus = true; selectAll = true; return; }
        if (ctrl() && key == Keyboard.KEY_L) { action(1); return; }
        if (ctrl() && key == Keyboard.KEY_Z) { action(2); return; }
        if (key == Keyboard.KEY_F5) { action(3); return; }
        if (key == Keyboard.KEY_TAB) { searchFocus = !searchFocus; selectAll = searchFocus; return; }
        if (searchFocus) {
            if (ctrl() && key == Keyboard.KEY_A) { selectAll = true; return; }
            if (ctrl() && key == Keyboard.KEY_C) { if (selectAll) setClipboardString(query); return; }
            if (ctrl() && key == Keyboard.KEY_V) { append(getClipboardString()); return; }
            if (key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE) {
                if (selectAll) setQuery("");
                else if (key == Keyboard.KEY_BACK && !query.isEmpty()) setQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
                return;
            }
            if (key == Keyboard.KEY_DOWN || key == Keyboard.KEY_RETURN) { searchFocus = false; return; }
            if (ChatAllowedCharacters.isAllowedCharacter(typed)) append(String.valueOf(typed));
        } else {
            if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_SPACE) && selected >= 0 && selected < visible.size()) toggle(visible.get(selected));
            int next = selected;
            if (key == Keyboard.KEY_LEFT) next--; if (key == Keyboard.KEY_RIGHT) next++;
            if (key == Keyboard.KEY_UP) next -= columns(); if (key == Keyboard.KEY_DOWN) next += columns();
            if (key == Keyboard.KEY_HOME) next = 0; if (key == Keyboard.KEY_END) next = visible.size() - 1;
            if (key == Keyboard.KEY_PRIOR) next -= columns() * Math.max(1, (int)(listBounds().height / rowHeight()));
            if (key == Keyboard.KEY_NEXT) next += columns() * Math.max(1, (int)(listBounds().height / rowHeight()));
            selected = visible.isEmpty() ? -1 : Math.max(0, Math.min(visible.size() - 1, next));
            if (selected >= 0) {
                float top = (selected / columns()) * rowHeight();
                if (top < targetScroll) targetScroll = top;
                if (top + rowHeight() > targetScroll + listBounds().height) targetScroll = top + rowHeight() - listBounds().height;
            }
        }
    }
    private void append(String value) {
        String next = (selectAll ? "" : query) + ChatAllowedCharacters.filterAllowedCharacters(value);
        if (next.length() > 96) next = next.substring(0, Character.isHighSurrogate(next.charAt(95)) ? 95 : 96);
        setQuery(next);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && listBounds().contains(mx, my)) { cancelPress(); targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll - Math.signum(wheel) * rowHeight() * 1.5f)); }
    }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() {
        surface.close(); cancelPress();
        if (repeatOwned && Keyboard.isCreated()) Keyboard.enableRepeatEvents(repeatBefore);
        repeatOwned = false;
    }
}
