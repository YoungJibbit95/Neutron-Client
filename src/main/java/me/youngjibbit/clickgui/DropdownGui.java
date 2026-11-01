package me.youngjibbit.clickgui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.render.ClickGUI;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.TextEffects;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Floating category columns using the same values, controls and scene compositor as PanelGUI. */
public class DropdownGui extends GuiScreen {
    private static final float MARGIN = 10, GAP = 12, HEADER = 19, COMPACT_WIDTH = 108;
    private static final Category[] ORDER = {Category.Combat, Category.Movement, Category.Render,
            Category.Player, Category.Exploit, Category.World, Category.Ghost, Category.Client};
    private final List<CategoryPanel> panels = new ArrayList<>();
    private final ArrayListSurface scene = new ArrayListSurface(), toolbarSurface = new ArrayListSurface();
    private final TextEffects headingEffects = new TextEffects();
    private CategoryPanel dragging;
    private float grabX, grabY;
    private int layoutWidth, layoutHeight;
    private float compactWidth = COMPACT_WIDTH;
    private String search = "";
    private boolean searchFocus;
    private Component focus, pressed, hovered;
    private DropdownModule binding;
    private long hoverStarted, lastFrame = System.nanoTime();

    private static final class CategoryPanel {
        final Category category;
        final List<DropdownModule> all = new ArrayList<>(), rows = new ArrayList<>();
        final ArrayListSurface surface = new ArrayListSurface();
        float x, y, width, height, slotHeight, scroll, targetScroll, maxScroll;
        boolean open = true;
        ClipRect viewport = new ClipRect(0, 0, 0, 0);
        CategoryPanel(Category category) {
            this.category = category;
            for (Module module : Pulsar.INSTANCE.getMm().getModules(category)) all.add(new DropdownModule(module));
            rows.addAll(all);
        }
        ClipRect bounds() { return new ClipRect(x, y, width, height); }
        ClipRect header() { return new ClipRect(x, y, width, HEADER); }
    }

    public DropdownGui() {
        for (Category category : ORDER) panels.add(new CategoryPanel(category));
    }

    @Override public void initGui() {
        super.initGui();
        cancelInteraction();
        if (layoutWidth != width || layoutHeight != height) resetLayout();
        layout(0);
        lastFrame = System.nanoTime();
    }

    private float bottom() { return Math.max(HEADER + MARGIN, height - 48); }

    private void resetLayout() {
        releaseControls();
        dragging = null;
        layoutWidth = width;
        layoutHeight = height;
        int columns = Math.max(1, Math.min(ORDER.length, (int)((width - 2 * MARGIN + GAP) / (90 + GAP))));
        int bands = (ORDER.length + columns - 1) / columns;
        compactWidth = Math.min(COMPACT_WIDTH, (width - 2 * MARGIN - (columns - 1) * GAP) / columns);
        float panelWidth = compactWidth;
        float slot = Math.max(HEADER, (bottom() - MARGIN - (bands - 1) * GAP) / bands);
        for (CategoryPanel panel : panels) {
            int index = 0;
            while (ORDER[index] != panel.category) index++;
            panel.width = panelWidth;
            panel.x = MARGIN + (index % columns) * (panelWidth + GAP);
            panel.y = MARGIN + (index / columns) * (slot + GAP);
            panel.slotHeight = slot;
        }
    }

    private void layout(float dt) {
        for (CategoryPanel panel : panels) {
            float content = 0;
            boolean expanded = false;
            for (DropdownModule row : panel.rows) {
                row.updateAnimation();
                content += row.getHeight();
                // Keep width during the closing animation to avoid squeezing live controls.
                expanded |= row.isOpen() || row.getHeight() > DropdownModule.ROW_HEIGHT + .1f;
            }
            panel.width = Math.min(expanded ? 220 : compactWidth, width - 2 * MARGIN);
            panel.x = Math.max(MARGIN, Math.min(panel.x, width - panel.width - MARGIN));
            panel.y = Math.max(MARGIN, Math.min(panel.y, bottom() - HEADER - 2));
            boolean visible = panel.open || !search.trim().isEmpty();
            float body = visible ? Math.min(Math.max(DropdownModule.ROW_HEIGHT, content),
                    Math.max(0, Math.min(panel.slotHeight - HEADER - 2, bottom() - panel.y - HEADER - 2))) : 0;
            panel.height = HEADER + (visible ? body + 2 : 0);
            panel.viewport = new ClipRect(panel.x + 1, panel.y + HEADER, panel.width - 2, body);
            panel.maxScroll = Math.max(0, content - body);
            panel.targetScroll = Math.max(-panel.maxScroll, Math.min(0, panel.targetScroll));
            panel.scroll += (panel.targetScroll - panel.scroll) * UiEffects.response(dt, 22);
            panel.scroll = Math.max(-panel.maxScroll, Math.min(0, panel.scroll));
            float rowY = panel.viewport.y + panel.scroll;
            for (DropdownModule row : panel.rows) {
                row.layout(panel.viewport.x, rowY, panel.viewport.width, visible ? panel.viewport : new ClipRect(0, 0, 0, 0));
                row.setGradientPosition((rowY - panel.viewport.y) / Math.max(1, body));
                rowY += row.getHeight();
            }
        }
        if (focus != null && !focusables().contains(focus)) setFocus(null);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = Math.max(0, Math.min(.05f, (now - lastFrame) / 1e9f));
        lastFrame = now;
        if (dragging != null) { dragging.x = mouseX - grabX; dragging.y = mouseY - grabY; }
        layout(dt);
        CategoryPanel top = panelAt(mouseX, mouseY);
        for (CategoryPanel panel : panels) for (DropdownModule row : panel.all) {
            boolean shown = panel.rows.contains(row) && (panel.open || !search.trim().isEmpty());
            boolean available = shown && dragging == null && binding == null;
            row.advanceMotion(dt, panel == top ? mouseX : -1, panel == top ? mouseY : -1, focus == row, pressed == row,
                    available && !panel.viewport.intersect(row.presentationBounds()).isEmpty());
            for (Component.Sub<?> value : row.presentationValues()) {
                boolean visible = available && row.focusableValues().contains(value) && value.getClip() != null
                        && !value.getClip().intersect(value.presentationBounds()).isEmpty();
                value.advanceMotion(dt, panel == top ? mouseX : -1, panel == top ? mouseY : -1,
                        focus == value, pressed == value, visible);
            }
        }
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        // Capture/blur the world once. Each disconnected panel borrows this clean scene;
        // overlapping columns never sample already-rendered UI or allocate a scene per module.
        scene.withScene(theme, () -> {
            for (CategoryPanel panel : panels) drawPanel(panel, theme,
                    panel == top && dragging == null && binding == null ? mouseX : -1,
                    panel == top && dragging == null && binding == null ? mouseY : -1);
            drawToolbar(theme);
            if (binding != null) drawBinding();
            else drawTooltip(mouseX, mouseY);
        });
    }

    private void drawPanel(CategoryPanel panel, Theme theme, int mouseX, int mouseY) {
        panel.surface.panel(panel.x, panel.y, panel.width, panel.height, 3, theme, true, true, true, 8, .88f, 1, () -> {
            try (GuiClip ignored = GuiClip.push(panel.bounds())) {
                // Secondary -> primary yields the reference's pink-to-cyan ramp for Cotton_candy.
                UIUtils.drawDecorationGradientVertical(panel.x, panel.y, panel.width, HEADER, 3,
                        UiEffects.color(theme.getSecondaryGradientRGB(), .88f),
                        UiEffects.color(theme.getSecondaryGradientRGB(), .60f));
                TTFFontRenderer heading = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 18");
                ClickGUI settings = (ClickGUI)Pulsar.INSTANCE.getMm().getModule(ClickGUI.class);
                boolean glow = settings != null && settings.getBooleanSetting("Heading Glow").getValue();
                int headingInk = ink(theme);
                float headingX = panel.x + (panel.width - heading.getWidth(panel.category.name())) / 2;
                float headingY = panel.y + (HEADER - heading.getHeight(panel.category.name())) / 2;
                if (glow && UiEffects.glowEnabled()) {
                    headingEffects.draw(heading, panel.category.name(), headingX, headingY,
                            headingInk, headingInk, 1, false, theme.getAnimationSpeed(), true,
                            theme.isCustom() ? theme.getGlowStrength() : .4f, theme.isCustom() ? theme.getGlowRadius() : 3, false);
                } else heading.drawString(panel.category.name(), headingX, headingY, headingInk);
                float markX = panel.x + panel.width - 10, markY = panel.y + HEADER / 2;
                UIUtils.drawDecorationRound(markX, markY, 5, 1, 0, new Color(ink(theme), true));
                if (!panel.open && search.trim().isEmpty())
                    UIUtils.drawDecorationRound(markX + 2, markY - 2, 1, 5, 0, new Color(ink(theme), true));
                try (GuiClip content = GuiClip.push(panel.viewport)) {
                    if (panel.rows.isEmpty()) font().drawCenteredString(search.isEmpty() ? "No modules" : "No results",
                            panel.x + panel.width / 2, panel.viewport.y + 3, ink(theme));
                    if (panel.open || !search.trim().isEmpty()) for (DropdownModule row : panel.rows)
                        if (!panel.viewport.intersect(new ClipRect(row.getX(), row.getY(), row.getWidth(), row.getHeight())).isEmpty())
                            row.drawScreen(mouseX, mouseY);
                    if (panel.maxScroll > 0 && panel.viewport.height > 0) {
                        float thumb = Math.max(10, panel.viewport.height * panel.viewport.height / (panel.viewport.height + panel.maxScroll));
                        float sy = panel.viewport.y - panel.scroll / panel.maxScroll * (panel.viewport.height - thumb);
                        UIUtils.drawDecorationRound(panel.x + panel.width - 2, sy, 1, thumb, 0, new Color(ink(theme), true));
                    }
                }
            }
        });
    }

    private TTFFontRenderer font() { return ClickControlStyle.font(); }
    private static int ink(Theme theme) { return theme.isCustom() ? theme.getTextColor().getRGB() : 0xfff4f0fa; }
    protected ClipRect toolbarBounds() { return new ClipRect((width - Math.min(310, width - 20)) / 2f, height - 32, Math.min(310, width - 20), 22); }
    protected ClipRect searchBounds() { ClipRect r = toolbarBounds(); return new ClipRect(r.x + 4, r.y + 2, r.width - 118, 18); }
    private ClipRect themeBounds() { ClipRect r = toolbarBounds(); return new ClipRect(r.x + r.width - 108, r.y + 2, 62, 18); }
    private ClipRect resetBounds() { ClipRect r = toolbarBounds(); return new ClipRect(r.x + r.width - 42, r.y + 2, 38, 18); }

    private void drawToolbar(Theme theme) {
        ClipRect r = toolbarBounds();
        toolbarSurface.panel(r.x, r.y, r.width, r.height, 4, theme, true, true, true, 8, .88f, .8f, () -> {
            ClipRect s = searchBounds();
            GuiStyle.panel(s.x, s.y, s.width, s.height, 3, searchFocus ? 1 : 0, new Color(22, 22, 26));
            try (GuiClip ignored = GuiClip.push(new ClipRect(s.x + 5, s.y, s.width - 18, s.height))) {
                String text = search.isEmpty() && !searchFocus ? "/ Search modules" : search + (searchFocus ? "_" : "");
                font().drawString(ClickControlStyle.fit(font(), text, s.width - 22), s.x + 5, s.y + 4, ink(theme));
            }
            if (!search.isEmpty()) font().drawString("x", s.x + s.width - 11, s.y + 4, ink(theme));
            button(themeBounds(), "Themes", theme);
            button(resetBounds(), "Reset", theme);
        });
        String hint = "LMB Toggle   RMB Settings   MMB Bind";
        font().drawCenteredString(hint, width / 2f, height - 45, ink(theme));
    }

    private void button(ClipRect r, String text, Theme theme) {
        GuiStyle.panel(r.x, r.y, r.width, r.height, 3, .25f, new Color(22, 22, 26));
        font().drawCenteredString(text, r.x + r.width / 2, r.y + 4, ink(theme));
    }

    private CategoryPanel panelAt(int x, int y) {
        if (toolbarBounds().contains(x, y)) return null;
        for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).bounds().contains(x, y)) return panels.get(i);
        return null;
    }

    private Component componentAt(CategoryPanel panel, int x, int y) {
        if (panel == null || !panel.viewport.contains(x, y) || (!panel.open && search.trim().isEmpty())) return null;
        for (DropdownModule row : panel.rows) {
            if (row.isMouseOver(x, y)) return row;
            for (Component.Sub<?> value : row.focusableValues())
                if (value.getClip().contains(x, y) && new ClipRect(value.getX(), value.getY(), value.getWidth(), value.getHeight()).contains(x, y)) return value;
        }
        return null;
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (binding != null) return;
        pressed = null;
        if (button == 0 && searchBounds().contains(x, y)) {
            setFocus(null);
            searchFocus = true;
            if (x >= searchBounds().x + searchBounds().width - 16) { search = ""; updateSearch(); }
            return;
        }
        if (button == 0 && themeBounds().contains(x, y)) { openThemeEditor(); return; }
        if (button == 0 && resetBounds().contains(x, y)) { cancelInteraction(); resetLayout(); layout(0); return; }
        searchFocus = false;
        CategoryPanel panel = panelAt(x, y);
        if (panel == null) { setFocus(null); return; }
        panels.remove(panel);
        panels.add(panel);
        if (panel.header().contains(x, y)) {
            releaseControls();
            setFocus(null);
            if (button == 1) {
                panel.open = !panel.open;
                for (DropdownModule row : panel.all) row.cancelInputs();
                layout(0);
            } else if (button == 0) { dragging = panel; grabX = x - panel.x; grabY = y - panel.y; }
            return;
        }
        Component target = componentAt(panel, x, y);
        setFocus(target);
        if (button == 2 && target instanceof DropdownModule) {
            binding = (DropdownModule)target;
            releaseControls();
            return;
        }
        pressed = button == 0 ? target : null;
        // Dispatch only to the topmost hit component. Background columns cannot consume input.
        if (target != null) target.mouseClicked(x, y, button);
        layout(0);
    }

    @Override protected void mouseReleased(int x, int y, int button) {
        dragging = null;
        pressed = null;
        releaseControls();
        super.mouseReleased(x, y, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        scrollAt(x, y, Mouse.getEventDWheel());
    }

    protected void scrollAt(int x, int y, int wheel) {
        if (wheel == 0 || dragging != null || binding != null) return;
        CategoryPanel panel = panelAt(x, y);
        if (panel == null || !panel.viewport.contains(x, y)) return;
        releaseControls();
        if (focus instanceof ClickSlider) ((ClickSlider)focus).cancelInput();
        panel.targetScroll = Math.max(-panel.maxScroll, Math.min(0, panel.targetScroll + (wheel > 0 ? 32 : -32)));
    }

    private void updateSearch() {
        releaseControls();
        setFocus(null);
        searchFocus = true;
        String query = search.trim().toLowerCase(Locale.ROOT);
        for (CategoryPanel panel : panels) {
            panel.rows.clear();
            panel.scroll = panel.targetScroll = 0;
            for (DropdownModule row : panel.all) {
                row.cancelInputs();
                Module module = row.getModule();
                if (query.isEmpty() || (module.getName() + " " + module.getDescription()).toLowerCase(Locale.ROOT).contains(query)) panel.rows.add(row);
            }
        }
        layout(0);
    }

    @Override protected void keyTyped(char typed, int key) throws IOException {
        pressed = null;
        if (binding != null) {
            if (key == Keyboard.KEY_ESCAPE) { binding = null; return; }
            if (key == Keyboard.KEY_NONE) return;
            binding.getModule().setKey(key == Keyboard.KEY_DELETE || key == Keyboard.KEY_BACK ? 0 : key);
            binding = null;
            return;
        }
        if (focus instanceof ClickSlider && ((ClickSlider)focus).isEditing()) {
            if (key == Keyboard.KEY_TAB) { ((ClickSlider)focus).cancelInput(); navigate(shiftDown() ? -1 : 1); }
            else focus.keyTyped(typed, key);
            return;
        }
        if (ctrlDown() && key == Keyboard.KEY_T) { openThemeEditor(); return; }
        if ((typed == '/' && !searchFocus) || (ctrlDown() && key == Keyboard.KEY_F)) { setFocus(null); searchFocus = true; return; }
        if (key == Keyboard.KEY_TAB) { navigate(shiftDown() ? -1 : 1); return; }
        if (searchFocus) {
            if (key == Keyboard.KEY_ESCAPE) { if (!search.isEmpty()) { search = ""; updateSearch(); } else searchFocus = false; return; }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_DOWN) { navigate(1); return; }
            if (ctrlDown() && key == Keyboard.KEY_A) { search = ""; updateSearch(); return; }
            if (key == Keyboard.KEY_BACK) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); updateSearch(); return; }
            if (typed >= 32 && typed != 127 && search.length() < 96) { search += typed; updateSearch(); }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE && focus instanceof ClickCombo && ((ClickCombo)focus).isOpen()) { ((ClickCombo)focus).close(); return; }
        if (key == Keyboard.KEY_ESCAPE) {
            Module owner = Pulsar.INSTANCE.getMm().getModule(ClickGUI.class);
            if (owner != null && owner.isToggled()) owner.setToggled(false);
            else mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_HOME) { resetLayout(); layout(0); return; }
        if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) { navigate(key == Keyboard.KEY_UP ? -1 : 1); return; }
        if (focus instanceof DropdownModule && key == Keyboard.KEY_K) { binding = (DropdownModule)focus; releaseControls(); return; }
        if (focus != null) { focus.keyTyped(typed, key); layout(0); reveal(focus); }
    }

    protected boolean shiftDown() { return Keyboard.isCreated() && isShiftKeyDown(); }
    protected boolean ctrlDown() { return Keyboard.isCreated() && isCtrlKeyDown(); }
    protected void openThemeEditor() {
        mc.displayGuiScreen(new ThemeEditorScreen(this, me.youngjibbit.theme.ThemeCatalog.global(), me.youngjibbit.theme.ThemeRuntime::select));
    }

    private List<Component> focusables() {
        List<Component> result = new ArrayList<>();
        // Stable category order, independent of the pointer's stacking order.
        for (Category category : ORDER) for (CategoryPanel panel : panels) if (panel.category == category && (panel.open || !search.trim().isEmpty()))
            for (DropdownModule row : panel.rows) { result.add(row); result.addAll(row.focusableValues()); }
        return result;
    }

    private void navigate(int direction) {
        List<Component> list = focusables();
        int current = searchFocus || focus == null ? 0 : list.indexOf(focus) + 1;
        int next = (current + direction + list.size() + 1) % (list.size() + 1);
        setFocus(next == 0 ? null : list.get(next - 1));
        searchFocus = next == 0;
        if (focus != null) reveal(focus);
    }

    private void reveal(Component component) {
        CategoryPanel owner = null;
        for (CategoryPanel panel : panels) for (DropdownModule row : panel.rows)
            if (row == component || row.focusableValues().contains(component)) {
                owner = panel;
                break;
            }
        if (owner == null) return;
        panels.remove(owner);
        panels.add(owner);
        float top = component.getY(), bottom = top + (component instanceof DropdownModule ? DropdownModule.ROW_HEIGHT : 24);
        float correction = top < owner.viewport.y ? owner.viewport.y - top
                : bottom > owner.viewport.y + owner.viewport.height ? owner.viewport.y + owner.viewport.height - bottom : 0;
        owner.targetScroll = Math.max(-owner.maxScroll, Math.min(0, owner.targetScroll + correction));
        owner.scroll = owner.targetScroll;
        layout(0);
    }

    private void setFocus(Component next) {
        if (focus != next && focus instanceof ClickSlider) ((ClickSlider)focus).cancelInput();
        focus = next;
        searchFocus = false;
    }

    private void releaseControls() { for (CategoryPanel panel : panels) for (DropdownModule row : panel.all) row.mouseReleased(); }
    private void cancelInteraction() {
        dragging = null;
        focus = pressed = hovered = null;
        binding = null;
        searchFocus = false;
        for (CategoryPanel panel : panels) for (DropdownModule row : panel.all) {
            row.cancelInputs();
            row.getPresentation().reset();
            for (Component.Sub<?> value : row.presentationValues()) value.getPresentation().reset();
        }
    }

    private void drawBinding() {
        float w = Math.min(290, width - 20), h = 89, x = (width - w) / 2, y = (height - h) / 2;
        GuiStyle.panel(x, y, w, h, 5, 1, new Color(13, 13, 18));
        Theme theme = Theme.getDefaultTheme();
        font().drawString("Bind " + ClickControlStyle.fit(font(), binding.getModule().getName(), w - 50), x + 10, y + 10, ink(theme));
        font().drawString("Press key / Esc: cancel", x + 10, y + 27, ink(theme));
        font().drawString("Delete / Backspace: remove", x + 10, y + 42, ink(theme));
        int key = binding.getModule().getKey(), conflicts = 0;
        for (Module other : Pulsar.INSTANCE.getMm().getAllModules()) if (other != binding.getModule() && key != 0 && other.getKey() == key) conflicts++;
        font().drawString("Current: " + (key == 0 ? "None" : Keyboard.getKeyName(key)), x + 10, y + 57, ink(theme));
        if (conflicts > 0) font().drawString("Shared with " + conflicts + " other module(s)", x + 10, y + 71, 0xffffc969);
    }

    private void drawTooltip(int x, int y) {
        Component target = dragging == null ? componentAt(panelAt(x, y), x, y) : null;
        if (target != hovered) { hovered = target; hoverStarted = System.nanoTime(); return; }
        if (!(target instanceof DropdownModule) || System.nanoTime() - hoverStarted < 500_000_000L) return;
        Module module = ((DropdownModule)target).getModule();
        float w = Math.min(265, width - 20);
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : (module.getName() + ": " + module.getDescription()).split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font().getWidth(next) > w - 16 && !line.isEmpty()) { lines.add(line); line = word; } else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        float h = 12 + lines.size() * 12;
        float tx = Math.max(5, Math.min(x + 10, width - w - 5)), ty = Math.max(5, Math.min(y + 14, height - h - 38));
        try (GuiClip ignored = GuiClip.push(new ClipRect(tx, ty, w, h))) {
            GuiStyle.panel(tx, ty, w, h, 4, .5f, new Color(13, 13, 18));
            for (int i = 0; i < lines.size(); i++) font().drawString(lines.get(i), tx + 8, ty + 6 + i * 12, ink(Theme.getDefaultTheme()));
        }
    }

    @Override public void onGuiClosed() {
        cancelInteraction();
        scene.close();
        headingEffects.close();
        toolbarSurface.close();
        for (CategoryPanel panel : panels) panel.surface.close();
        super.onGuiClosed();
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
