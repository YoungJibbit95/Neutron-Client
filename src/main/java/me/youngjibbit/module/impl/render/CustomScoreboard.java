package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClickControlStyle;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.gui.HudPlacement;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.utils.render.HudDrag;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** A read-only sidebar with one font-aware layout for its surface, text and drag bounds. */
@ModuleInfo(id = "custom_scoreboard", name = "Custom Scoreboard",
        description = "Draggable scoreboard design; disable for the vanilla sidebar", category = Category.Render)
public final class CustomScoreboard extends Module {
    private static final float PADDING = 8;
    // Vanilla's normal armor/air row starts 49 GUI pixels above the bottom.
    // Keep a small gap above it and the actual custom hotbar rectangle.
    private static final float STATUS_HUD_HEIGHT = 49;
    private static final float BOTTOM_HUD_GAP = 6;
    private final CheckBoxValue showScores = new CheckBoxValue("Show Scores", this, true);
    private final CheckBoxValue background = new CheckBoxValue("Background", this, true);
    private final CheckBoxValue blur = new CheckBoxValue("Blur", this, true);
    private final CheckBoxValue glow = new CheckBoxValue("Glow", this, true);
    private final SliderValue scale = new SliderValue("Scale", this, 1.0, .65, 1.6);
    private final CheckBoxValue customPosition = new CheckBoxValue("Custom Position", this, false);
    private final SliderValue positionX = new SliderValue("Position X", this, 0.0, -16384, 16384);
    private final SliderValue positionY = new SliderValue("Position Y", this, 0.0, -16384, 16384);
    private final ArrayListSurface surface = new ArrayListSurface(this);
    private boolean dragging, previousButton;
    private float grabX, grabY;

    public CustomScoreboard() {
        positionX.setVisibility(module -> customPosition.getValue());
        positionY.setVisibility(module -> customPosition.getValue());
    }

    public static CustomScoreboard current() {
        return Pulsar.INSTANCE.getMm() == null ? null
                : (CustomScoreboard) Pulsar.INSTANCE.getMm().getModule(CustomScoreboard.class);
    }

    @Subscribe private final Listener<WorldEvent> worldChange = new Listener<>(event -> release());

    @Override public void onDisable() {
        release();
        super.onDisable();
    }

    private void release() {
        dragging = false;
        previousButton = Mouse.isCreated() && Mouse.isButtonDown(0);
        HudDrag.cancel(this);
        surface.close();
    }

    public void resetPosition() {
        customPosition.reset();
        positionX.reset();
        positionY.reset();
        dragging = false;
        HudDrag.cancel(this);
    }

    /** Null objectives have a small preview only in the existing chat/HUD editor. */
    public boolean render(ScoreObjective objective, ScaledResolution resolution) {
        if (!isToggled() || !HUD.visible() || Pulsar.INSTANCE.getFontManager() == null) {
            release();
            return false;
        }
        boolean editing = Diagnostics.editing(mc.currentScreen);
        if (objective == null && !editing) {
            release();
            return true;
        }
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = contentHeight(resolution.getScaledHeight());
        TTFFontRenderer font = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 20");
        TTFFontRenderer titleFont = Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 20");
        Frame frame = layout(objective, screenWidth, screenHeight, font, titleFont);
        boolean down = Mouse.isCreated() && Mouse.isButtonDown(0);
        float mouseX = Mouse.isCreated() ? Mouse.getX() / (float) resolution.getScaleFactor() : -1;
        float mouseY = Mouse.isCreated() ? (mc.displayHeight - Mouse.getY() - 1) / (float) resolution.getScaleFactor() : -1;
        interact(editing, down, mouseX, mouseY, frame, screenWidth, screenHeight);
        paint(frame);
        return true;
    }

    private static int contentHeight(int screenHeight) {
        return mc.currentScreen instanceof DiagnosticsEditor
                ? Math.max(1, screenHeight - DiagnosticsEditor.CONTROL_STRIP) : screenHeight;
    }

    /** Filter first, then limit: hidden entries must not displace visible sidebar rows. */
    static List<Score> visibleScores(ScoreObjective objective) {
        List<Score> rows = new ArrayList<>();
        if (objective == null) return rows;
        for (Score score : objective.getScoreboard().getSortedScores(objective)) {
            String name = score.getPlayerName();
            if (name != null && !name.startsWith("#")) rows.add(score);
        }
        return rows.size() > 15 ? new ArrayList<>(rows.subList(rows.size() - 15, rows.size())) : rows;
    }

    Frame layout(ScoreObjective objective, int screenWidth, int screenHeight,
                 TTFFontRenderer font, TTFFontRenderer titleFont) {
        List<String> names = new ArrayList<>(), scores = new ArrayList<>();
        String title = objective == null ? "Scoreboard" : singleLine(objective.getDisplayName());
        List<Score> rows = visibleScores(objective);
        for (int i = rows.size() - 1; i >= 0; i--) {
            Score score = rows.get(i);
            names.add(singleLine(ScorePlayerTeam.formatPlayerName(
                    objective.getScoreboard().getPlayersTeam(score.getPlayerName()), score.getPlayerName())));
            scores.add(showScores.getValue() ? Integer.toString(score.getScorePoints()) : "");
        }
        if (objective == null) {
            names.add("Drag to move");
            scores.add("");
            names.add("Server information");
            scores.add("");
        }
        float lineHeight = (float) Math.ceil(font.getLineHeight()) + 2;
        float headerHeight = (float) Math.ceil(titleFont.getLineHeight()) + 9;
        float height = PADDING * 2 + headerHeight + names.size() * lineHeight;
        boolean manual = customPosition.getValue();
        // The editor already removed its footer from screenHeight. A manually
        // placed sidebar retains the full existing screen/editor drag bounds.
        float availableHeight = manual || (mc != null && mc.currentScreen instanceof DiagnosticsEditor) ? screenHeight
                : Math.max(1, Math.min(CustomHotbar.layout(screenWidth, screenHeight).y,
                        screenHeight - STATUS_HUD_HEIGHT) - BOTTOM_HUD_GAP);
        float drawingScale = Math.min(scale.getValue().floatValue(), Math.max(1, availableHeight - 8) / height);
        float scoreWidth = 0;
        for (String score : scores) scoreWidth = Math.max(scoreWidth, font.getWidth(score));
        float gap = scoreWidth > 0 ? 8 : 0;
        float measuredWidth = titleFont.getWidth(title);
        for (String name : names) measuredWidth = Math.max(measuredWidth, font.getWidth(name) + gap + scoreWidth);
        float width = Math.min(Math.max(80, measuredWidth + PADDING * 2),
                Math.max(1, screenWidth - 8) / drawingScale);
        float contentWidth = Math.max(0, width - PADDING * 2);
        scoreWidth = Math.min(scoreWidth, contentWidth);
        gap = scoreWidth > 0 ? Math.min(8, Math.max(0, contentWidth - scoreWidth)) : 0;
        float nameWidth = Math.max(0, contentWidth - scoreWidth - gap);
        Frame frame = new Frame(font, titleFont, fit(titleFont, title, contentWidth), width, height,
                drawingScale, headerHeight, lineHeight, Theme.resolve(Theme.getDefaultTheme()));
        for (int i = 0; i < names.size(); i++) {
            frame.rows.add(new Row(fit(font, names.get(i), nameWidth), fit(font, scores.get(i), scoreWidth)));
        }
        float x = manual ? positionX.getValue().floatValue() : screenWidth - frame.screenWidth() - 6;
        float y = manual ? positionY.getValue().floatValue()
                : Math.min((screenHeight - frame.screenHeight()) / 2,
                        availableHeight - frame.screenHeight() - 4);
        HudPlacement.Point point = HudPlacement.clamp(x, y, screenWidth, screenHeight,
                frame.screenWidth(), frame.screenHeight(), HUD.bounded() || (mc != null && Diagnostics.editing(mc.currentScreen)));
        frame.x = point.x;
        frame.y = point.y;
        return frame;
    }

    private static String singleLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
    }

    private static String fit(TTFFontRenderer font, String text, float width) {
        String fitted = ClickControlStyle.fit(font, text, width);
        // Do not leave a formatting introducer directly in front of an ellipsis.
        return fitted.endsWith("\u00a7...") ? fitted.substring(0, fitted.length() - 4) + "..." : fitted;
    }

    void interact(boolean editing, boolean down, float mouseX, float mouseY,
                  Frame frame, int screenWidth, int screenHeight) {
        boolean wasDragging = dragging;
        dragging = HudDrag.capture(this, editing, down, !previousButton,
                HudDrag.roundedContains(mouseX, mouseY, frame.x, frame.y,
                        frame.screenWidth(), frame.screenHeight(),
                        UiEffects.radius(8 * frame.scale, frame.screenWidth(), frame.screenHeight())));
        if (dragging && !wasDragging) {
            grabX = mouseX - frame.x;
            grabY = mouseY - frame.y;
        }
        if (dragging) {
            HudPlacement.Point point = HudPlacement.clamp(HUD.snap(mouseX - grabX), HUD.snap(mouseY - grabY),
                    screenWidth, screenHeight, frame.screenWidth(), frame.screenHeight(),
                    HUD.bounded() || mc.currentScreen instanceof DiagnosticsEditor);
            frame.x = point.x;
            frame.y = point.y;
            positionX.setValue((double) frame.x);
            positionY.setValue((double) frame.y);
            customPosition.setValue(true);
        }
        previousButton = down;
    }

    void paint(Frame frame) {
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            surface.panel(frame.x, frame.y, frame.screenWidth(), frame.screenHeight(), 8 * frame.scale,
                    frame.theme, background.getValue(), blur.getValue(), glow.getValue(), 8,
                    150 / 255f, .65f, () -> {
                        try (GuiClip clip = GuiClip.push(new ClipRect(frame.x, frame.y,
                                frame.screenWidth(), frame.screenHeight()))) {
                            GlStateManager.pushMatrix();
                            try {
                                GlStateManager.translate(frame.x, frame.y, 0);
                                GlStateManager.scale(frame.scale, frame.scale, 1);
                                frame.titleFont.drawCenteredString(frame.title, frame.width / 2, PADDING, 0xffeaf4ff);
                                UIUtils.drawDecorationRound(PADDING, PADDING + frame.headerHeight - 4,
                                        Math.max(0, frame.width - PADDING * 2), 1, 0,
                                        new Color(255, 255, 255, 28));
                                for (int i = 0; i < frame.rows.size(); i++) {
                                    Row row = frame.rows.get(i);
                                    float y = PADDING + frame.headerHeight + i * frame.lineHeight;
                                    frame.font.drawStringWithShadow(row.name, PADDING, y, 0xffedf2fb);
                                    frame.font.drawStringWithShadow(row.score,
                                            frame.width - PADDING - frame.font.getWidth(row.score), y, 0xffb4c3d8);
                                }
                            } finally {
                                GlStateManager.popMatrix();
                            }
                        }
                    });
        }
    }

    static final class Row {
        final String name, score;
        Row(String name, String score) { this.name = name; this.score = score; }
    }

    static final class Frame {
        final TTFFontRenderer font, titleFont;
        final String title;
        final float width, height, scale, headerHeight, lineHeight;
        final Theme theme;
        final List<Row> rows = new ArrayList<>();
        float x, y;
        Frame(TTFFontRenderer font, TTFFontRenderer titleFont, String title, float width, float height,
              float scale, float headerHeight, float lineHeight, Theme theme) {
            this.font = font;
            this.titleFont = titleFont;
            this.title = title;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.headerHeight = headerHeight;
            this.lineHeight = lineHeight;
            this.theme = theme;
        }
        float screenWidth() { return width * scale; }
        float screenHeight() { return height * scale; }
    }
}
