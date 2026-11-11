package net.minecraft.client.gui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.ChatMotion;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.RenderDiagnostics;
import me.youngjibbit.utils.render.UiEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/** Real chat consumers and framebuffer pixels; synthetic messages, no network or native window. */
public final class ChatPresentationNativeCheck {
    private static final File OUTPUT = new File("chat-preview");
    private ChatPresentationNativeCheck() { }

    public static void run(Minecraft mc, GuiChat chat) throws Exception {
        Field owner = field(Pulsar.class, "mm"), logOwner = field(GuiIngame.class, "persistantChatGUI");
        Object previousOwner = owner.get(Pulsar.INSTANCE);
        GuiNewChat previousLog = mc.ingameGUI.getChatGUI();
        GuiTextField previousInput = chat.inputField;
        GuiScreen previousScreen = mc.currentScreen;
        int displayWidth = mc.displayWidth, displayHeight = mc.displayHeight;
        int guiScale = mc.gameSettings.guiScale, screenWidth = chat.width, screenHeight = chat.height;
        float chatScale = mc.gameSettings.chatScale, chatWidth = mc.gameSettings.chatWidth;
        float focusedHeight = mc.gameSettings.chatHeightFocused, unfocusedHeight = mc.gameSettings.chatHeightUnfocused;
        float opacity = mc.gameSettings.chatOpacity;
        EntityPlayer.EnumChatVisibility visibility = mc.gameSettings.chatVisibility;
        boolean diagnostics = RenderDiagnostics.enabled;
        GuiNewChat log = new GuiNewChat(mc);
        try {
            Field unsafeField = field(sun.misc.Unsafe.class, "theUnsafe");
            ModuleManager modules = (ModuleManager) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(ModuleManager.class);
            ClientSettings settings = new ClientSettings();
            LinkedHashMap<Class<?>, Object> values = new LinkedHashMap<>();
            values.put(ClientSettings.class, settings);
            field(ModuleManager.class, "modulesByClass").set(modules, values);
            owner.set(Pulsar.INSTANCE, modules);
            logOwner.set(mc.ingameGUI, log);
            settings.surface.setValue("Liquid Glass");
            settings.gradientMotion.setValue("Static");
            settings.animations.setValue(false);
            settings.reducedMotion.setValue(false);
            UiEffects.invalidate();
            mc.gameSettings.chatWidth = 1;
            mc.gameSettings.chatHeightFocused = mc.gameSettings.chatHeightUnfocused = 1;
            mc.gameSettings.chatOpacity = 1;
            mc.gameSettings.chatVisibility = EntityPlayer.EnumChatVisibility.FULL;
            mc.currentScreen = chat;
            resize(mc, chat, 1280, 960, 2);
            OUTPUT.mkdirs();

            checkRenderedLinks(mc, chat, log);
            checkInputAndResize(mc, chat, log);
            checkReflowAndHistory(mc, chat, log);
            checkFormattedReflowAnchor(mc, chat, log);
            checkMotion(mc, chat, log, settings);
            checkWarmResources(mc, chat, log, settings);
            require(glGetError() == GL_NO_ERROR, "Chat presentation left a GL error");
            System.out.println("PASS: compact chat presentation: rendered link hits at .5/.8/1 scale on even/odd displays, input cursor and resize selection, viewport reflow and formatted text scroll anchor, opening/typing/closing/reopening, reduced motion, zero warm GPU allocations; chat-preview PNGs");
        } finally {
            RenderDiagnostics.enabled = diagnostics;
            log.clearChatMessages();
            logOwner.set(mc.ingameGUI, previousLog);
            owner.set(Pulsar.INSTANCE, previousOwner);
            UiEffects.invalidate();
            mc.gameSettings.guiScale = guiScale;
            mc.gameSettings.chatScale = chatScale;
            mc.gameSettings.chatWidth = chatWidth;
            mc.gameSettings.chatHeightFocused = focusedHeight;
            mc.gameSettings.chatHeightUnfocused = unfocusedHeight;
            mc.gameSettings.chatOpacity = opacity;
            mc.gameSettings.chatVisibility = visibility;
            mc.displayWidth = displayWidth; mc.displayHeight = displayHeight;
            mc.getFramebuffer().createBindFramebuffer(displayWidth, displayHeight);
            chat.width = screenWidth; chat.height = screenHeight; chat.inputField = previousInput;
            mc.currentScreen = previousScreen;
        }
    }

    private static void checkRenderedLinks(Minecraft mc, GuiChat chat, GuiNewChat log) throws Exception {
        try {
            for (int[] display : new int[][]{{1280, 960}, {853, 721}}) {
                resize(mc, chat, display[0], display[1], 2);
                require(new ScaledResolution(mc).getScaleFactor() == 2, "Link fixture must exercise GUI scale 2");
                for (float scale : new float[]{.5f, .8f, 1}) {
                    log.clearChatMessages();
                    mc.gameSettings.chatScale = scale;
                    chat.initGui();
                    ChatComponentText message = new ChatComponentText("Fixture: ");
                    message.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
                    ChatComponentText link = new ChatComponentText("INSPECT LINK");
                    link.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)
                            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/fixture-inspect")));
                    message.appendSibling(link);
                    log.printChatMessage(message);
                    chat.inputField.setText("Synthetic draft — no message sent");
                    GuiChatInputNativeCheck.draw(mc, chat);
                    BufferedImage image = capture(mc);
                    String suffix = display[0] == 1280 ? "" : "-" + display[0] + "x" + display[1];
                    // Keep the failing frame available even when a glyph-edge assertion below fails.
                    ImageIO.write(image, "png", new File(OUTPUT, "links-scale-" + scale + suffix + ".png"));
                    requireLinkPixels(mc, log, image, "/fixture-inspect");
                    float x = number(log, "originX") + (ChatMotion.TEXT_INSET + 4) * scale;
                    float y = number(log, "originY") + (floats(log, "lineY")[0] + mc.fontRendererObj.FONT_HEIGHT + 1) * scale;
                    require(componentAt(mc, log, x, y) == null,
                            "Inter-line gap activates a chat component at " + display[0] + "x" + display[1] + ", scale " + scale);
                    require(componentAt(mc, log, number(log, "surfaceX") - 1, y) == null,
                            "A clipped outside pixel activates a chat component");
                }
            }
        } finally {
            resize(mc, chat, 1280, 960, 2);
        }
    }

    private static void checkInputAndResize(Minecraft mc, GuiChat chat, GuiNewChat log) throws Exception {
        chat.inputField.setText("abcd efgh ijkl");
        GuiChatInputNativeCheck.draw(mc, chat);
        clickCursor(mc, chat, log, 4);
        String draft = "Preserve the selected draft on resize";
        chat.inputField.setText(draft);
        chat.inputField.setCursorPosition(9);
        chat.inputField.setSelectionPos(18);
        resize(mc, chat, 960, 720, 2);
        require(draft.equals(chat.inputField.getText()), "Resize discarded the active draft");
        require(chat.inputField.getCursorPosition() == 9 && chat.inputField.getSelectionEnd() == 18,
                "Resize discarded cursor/selection positions");
        GuiChatInputNativeCheck.draw(mc, chat);
        snapshot(mc, "resize-selected-draft.png");
        resize(mc, chat, 1280, 960, 2);
    }

    private static void checkReflowAndHistory(Minecraft mc, GuiChat chat, GuiNewChat log) throws Exception {
        mc.gameSettings.chatScale = 1;
        log.clearChatMessages();
        log.addToSentMessages("keep this recalled draft");
        String wideLine = "Resize ";
        while (mc.fontRendererObj.getStringWidth(wideLine + "W END") <= 316) wideLine += "W";
        wideLine += " END";
        log.printChatMessageWithOptionalDeletion(new ChatComponentText(wideLine), 701);
        GuiChatInputNativeCheck.draw(mc, chat);
        require(lines(log, "field_146253_i").size() == 1, "Wide fixture must begin as one line");
        resize(mc, chat, 640, 480, 2);
        GuiChatInputNativeCheck.draw(mc, chat);
        List<ChatLine> wrapped = lines(log, "field_146253_i");
        require(wrapped.size() > 1, "Narrow viewport clipped the old line instead of reflowing its tail");
        int available = ChatMotion.textWidth(log.getChatWidth(), 1, new ScaledResolution(mc).getScaledWidth());
        StringBuilder reconstructed = new StringBuilder();
        for (int i = wrapped.size() - 1; i >= 0; i--) {
            require(mc.fontRendererObj.getStringWidth(wrapped.get(i).getChatComponent().getFormattedText()) <= available,
                    "Reflow still leaves text outside the visible surface");
            reconstructed.append(wrapped.get(i).getChatComponent().getUnformattedText());
        }
        require(plain(reconstructed.toString()).equals(plain(wideLine)), "Reflow lost or duplicated message characters");
        require(lines(log, "chatLines").size() == 1 && log.getSentMessages().contains("keep this recalled draft"),
                "Reflow changed retained or sent history");
        snapshot(mc, "narrow-wrapped-tail.png");
        resize(mc, chat, 1280, 960, 2);
        GuiChatInputNativeCheck.draw(mc, chat);
        require(lines(log, "field_146253_i").size() == 1, "Growing viewport retained stale narrow wrapping");

        log.clearChatMessages();
        for (int i = 0; i < 40; i++)
            log.printChatMessageWithOptionalDeletion(new ChatComponentText("History " + i + " " + wideLine), 1000 + i);
        log.scroll(17);
        GuiChatInputNativeCheck.draw(mc, chat);
        int anchor = ((ChatLine[]) field(GuiNewChat.class, "presentedLines").get(log))[0].getChatLineID();
        int retained = lines(log, "chatLines").size();
        resize(mc, chat, 640, 480, 2);
        GuiChatInputNativeCheck.draw(mc, chat);
        require(((ChatLine[]) field(GuiNewChat.class, "presentedLines").get(log))[0].getChatLineID() == anchor,
                "Viewport reflow jumped away from the scrolled history anchor");
        require(lines(log, "chatLines").size() == retained, "Reflow deleted source messages with nonzero IDs");
        require((Integer) field(GuiNewChat.class, "scrollPos").get(log) > 0, "Reflow reset the history scroll position");
        snapshot(mc, "narrow-scrolled-history.png");
        log.resetScroll();
        resize(mc, chat, 1280, 960, 2);
    }

    /** Compare displayed source text, independently of ChatLine's offset bookkeeping. */
    private static void checkFormattedReflowAnchor(Minecraft mc, GuiChat chat, GuiNewChat log) throws Exception {
        float previousWidth = mc.gameSettings.chatWidth, previousScale = mc.gameSettings.chatScale;
        boolean previousColours = mc.gameSettings.chatColours;
        try {
            log.clearChatMessages();
            mc.gameSettings.chatColours = true;
            mc.gameSettings.chatWidth = .25f;
            mc.gameSettings.chatScale = 1;
            chat.initGui();
            StringBuilder sourceText = new StringBuilder();
            for (int i = 0; i < 250; i++) sourceText.append(String.format(java.util.Locale.ROOT, "M%03d ", i));
            String original = sourceText.toString();
            ChatComponentText source = new ChatComponentText(original);
            source.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA).setBold(true));
            log.printChatMessageWithOptionalDeletion(source, 1701);
            log.scroll(17);
            GuiChatInputNativeCheck.draw(mc, chat);
            int narrowRows = lines(log, "field_146253_i").size();
            require(narrowRows > 40 && narrowRows < 100, "Formatted fixture must exercise many retained wraps without truncation");
            ChatLine before = ((ChatLine[]) field(GuiNewChat.class, "presentedLines").get(log))[0];
            String oldVisible = visibleText(before);
            int anchor = original.indexOf(oldVisible);
            require(!oldVisible.isEmpty() && anchor > 0 && original.indexOf(oldVisible, anchor + 1) < 0,
                    "The scrolled anchor must identify a unique visible source-text range");
            snapshot(mc, "formatted-history-narrow.png");

            // Both settings materially change the number of wraps and the artificial style prefixes.
            mc.gameSettings.chatWidth = 1;
            mc.gameSettings.chatScale = .8f;
            GuiChatInputNativeCheck.draw(mc, chat);
            ChatLine after = ((ChatLine[]) field(GuiNewChat.class, "presentedLines").get(log))[0];
            String newVisible = visibleText(after);
            int newStart = original.indexOf(newVisible);
            require(lines(log, "field_146253_i").size() < narrowRows / 2,
                    "Formatted reflow fixture did not substantially change wrapping");
            require(!newVisible.isEmpty() && newStart >= 0 && original.indexOf(newVisible, newStart + 1) < 0,
                    "Reflowed visible fragment is no longer an exact unique part of its source text");
            require(newStart <= anchor && anchor < newStart + newVisible.length(),
                    "Formatted reflow skipped the visible anchor: old source position " + anchor
                            + ", new displayed range " + newStart + ".." + (newStart + newVisible.length()));
            require(newVisible.substring(anchor - newStart).startsWith(
                            oldVisible.substring(0, Math.min(oldVisible.length(), newVisible.length() - (anchor - newStart)))),
                    "The old visible anchor's characters changed during formatted reflow");
            require(after.getChatLineID() == 1701 && lines(log, "chatLines").size() == 1,
                    "Formatted reflow replaced or duplicated the source message");
            snapshot(mc, "formatted-history-wide-scale.png");
        } finally {
            mc.gameSettings.chatWidth = previousWidth;
            mc.gameSettings.chatScale = previousScale;
            mc.gameSettings.chatColours = previousColours;
            log.resetScroll();
        }
    }

    private static String visibleText(ChatLine line) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(line.getChatComponent().getUnformattedText());
    }

    private static void checkMotion(Minecraft mc, GuiChat chat, GuiNewChat log, ClientSettings settings) throws Exception {
        log.clearChatMessages();
        settings.animations.setValue(true);
        settings.reducedMotion.setValue(false);
        chat.initGui();
        chat.inputField.setText("An animated draft");
        log.printChatMessage(new ChatComponentText("Synthetic arrival: the chat uses the typing-bar material."));
        // Only the elapsed timestamp is supplied; the real draw path advances every spring and paints every frame.
        advance(mc, chat, log, 16);
        ChatMotion motion = (ChatMotion) field(GuiNewChat.class, "motion").get(log);
        require(motion.focusAlpha() > 0 && motion.focusAlpha() <= 1, "Opening never presents the input");
        snapshot(mc, "opening.png");
        for (int i = 0; i < 8; i++) advance(mc, chat, log, 50);
        chat.keyTyped('!', 0);
        advance(mc, chat, log, 16);
        require(Math.abs(number(log, "inputOffsetX")) > .1f, "Typing does not animate the rendered text");
        clickCursor(mc, chat, log, 3);
        snapshot(mc, "typing.png");

        chat.onGuiClosed();
        mc.currentScreen = null;
        require(!chat.inputField.isFocused(), "Closing retained keyboard focus");
        advance(mc, chat, log, 32);
        require(motion.focusAlpha() > 0 && motion.focusAlpha() < 1, "Closing did not retain an animated exit");
        require(field(GuiNewChat.class, "closingInput").get(log) != null, "Closing dropped the visible input immediately");
        snapshot(mc, "closing.png");
        float priorSlide = motion.inputSlide();
        mc.currentScreen = chat;
        chat.initGui();
        require(Math.abs(motion.inputSlide() - priorSlide) < .001f, "Reopening resets the in-flight input position");
        advance(mc, chat, log, 16);
        require(chat.inputField.isFocused() && field(GuiNewChat.class, "closingInput").get(log) == null,
                "Reopening retained stale closing input");

        settings.reducedMotion.setValue(true);
        chat.keyTyped('R', 0);
        GuiChatInputNativeCheck.draw(mc, chat);
        require(motion.focusAlpha() == 1 && motion.inputSlide() == 0
                        && number(log, "inputOffsetX") == 0 && number(log, "inputOffsetY") == 0,
                "Reduced motion did not snap all input presentation channels");
        snapshot(mc, "reduced-motion.png");
        chat.onGuiClosed(); mc.currentScreen = null;
        GuiChatInputNativeCheck.draw(mc, chat);
        require(motion.focusAlpha() == 0 && field(GuiNewChat.class, "closingInput").get(log) == null,
                "Reduced motion keeps a closing input alive");
        GuiChatInputNativeCheck.draw(mc, chat, 201);
        require((Integer) field(GuiNewChat.class, "presentedCount").get(log) == 0,
                "Expired unfocused messages remain rendered after their lifetime");
        mc.currentScreen = chat; chat.initGui();
    }

    private static void checkWarmResources(Minecraft mc, GuiChat chat, GuiNewChat log, ClientSettings settings) throws Exception {
        settings.animations.setValue(false);
        settings.reducedMotion.setValue(false);
        log.clearChatMessages();
        log.printChatMessage(new ChatComponentText("Warm GPU resource check: one shared message surface."));
        chat.inputField.setText("Stable visible draft");
        for (int i = 0; i < 3; i++) GuiChatInputNativeCheck.draw(mc, chat);
        RenderDiagnostics.enabled = true; RenderDiagnostics.reset();
        for (int i = 0; i < 6; i++) GuiChatInputNativeCheck.draw(mc, chat);
        require(RenderDiagnostics.framebufferCreates == 0 && RenderDiagnostics.programCreates == 0
                        && RenderDiagnostics.shaderCompiles == 0 && RenderDiagnostics.glyphTexturesCreated == 0,
                "Warm chat frames create GPU resources: " + RenderDiagnostics.snapshot());
        RenderDiagnostics.enabled = false;
    }

    private static void clickCursor(Minecraft mc, GuiChat chat, GuiNewChat log, int position) throws Exception {
        int x = chat.inputField.xPosition + mc.fontRendererObj.getStringWidth(chat.inputField.getText().substring(0, position));
        int y = chat.inputField.yPosition + 4;
        chat.mouseClicked((int) Math.ceil(x + number(log, "inputOffsetX")),
                (int) Math.ceil(y + number(log, "inputOffsetY")), 0);
        require(chat.inputField.getCursorPosition() == position, "Rendered input click missed cursor index " + position);
    }

    private static void requireLinkPixels(Minecraft mc, GuiNewChat log, BufferedImage image, String action) throws Exception {
        int scale = new ScaledResolution(mc).getScaleFactor();
        float chatScale = mc.gameSettings.chatScale;
        float x = number(log, "originX") + floats(log, "lineX")[0] * chatScale;
        float y = number(log, "originY") + floats(log, "lineY")[0] * chatScale;
        int count = 0;
        int top = Math.max(0, (int) Math.ceil((y + 2 * chatScale) * scale));
        int bottom = Math.min(image.getHeight(), (int) Math.floor((y + 7 * chatScale) * scale));
        int right = Math.min(image.getWidth(), (int) ((number(log, "surfaceX") + number(log, "surfaceWidth") - 2) * scale));
        for (int py = top; py < bottom; py++) for (int px = Math.max(0, (int) (x * scale)); px < right; px++) {
            int color = image.getRGB(px, py), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
            if (g > 180 && b > 180 && r + 45 < g) {
                IChatComponent component = log.getChatComponent(px, mc.displayHeight - py - 1);
                if (component == null || component.getChatStyle().getChatClickEvent() == null
                        || !action.equals(component.getChatStyle().getChatClickEvent().getValue())) {
                    throw new AssertionError("Actual visible link pixel misses its component: "
                            + linkHitDiagnostic(mc, log, px, py, color, component));
                }
                count++;
            }
        }
        require(count > 2, "No readable link glyph pixels rendered at chat scale " + chatScale);
    }

    private static String linkHitDiagnostic(Minecraft mc, GuiNewChat log, int px, int py,
                                            int color, IChatComponent picked) throws Exception {
        int guiScale = new ScaledResolution(mc).getScaleFactor();
        float chatScale = mc.gameSettings.chatScale;
        float ox = number(log, "originX"), oy = number(log, "originY");
        float lx = floats(log, "lineX")[0], ly = floats(log, "lineY")[0];
        float cornerX = (px / (float) guiScale - ox) / chatScale - lx;
        float centerX = ((px + .5f) / guiScale - ox) / chatScale - lx;
        float cornerY = (py / (float) guiScale - oy) / chatScale - ly;
        float centerY = ((py + .5f) / guiScale - oy) / chatScale - ly;
        ChatLine shown = ((ChatLine[]) field(GuiNewChat.class, "presentedLines").get(log))[0];
        StringBuilder spans = new StringBuilder();
        int offset = 0;
        for (IChatComponent part : shown.getChatComponent()) if (part instanceof ChatComponentText) {
            String raw = ((ChatComponentText) part).getChatComponentText_TextValue();
            int width = mc.fontRendererObj.getStringWidth(GuiUtilRenderComponents.func_178909_a(raw, false));
            spans.append('[').append(offset).append(',').append(offset + width).append("] ")
                    .append(EnumChatFormatting.getTextWithoutFormattingCodes(raw)).append(" -> ")
                    .append(part.getChatStyle().getChatClickEvent()).append("; ");
            offset += width;
        }
        return "display=" + mc.displayWidth + "x" + mc.displayHeight + ", guiScale=" + guiScale
                + ", chatScale=" + chatScale + ", pixel=" + px + "," + py
                + ", rawMouse=" + px + "," + (mc.displayHeight - py - 1)
                + ", rgb=" + String.format(java.util.Locale.ROOT, "%06x", color & 0xffffff)
                + ", lineRelativeCorner=" + cornerX + "," + cornerY
                + ", lineRelativeCenter=" + centerX + "," + centerY
                + ", origin=" + ox + "," + oy + ", line=" + lx + "," + ly
                + ", clip=" + number(log, "surfaceX") + "," + number(log, "surfaceY")
                + "," + number(log, "surfaceWidth") + "," + number(log, "surfaceHeight")
                + ", picked=" + (picked == null ? "<null>" : EnumChatFormatting.getTextWithoutFormattingCodes(picked.getUnformattedText()))
                + ", componentSpans=" + spans;
    }

    private static IChatComponent componentAt(Minecraft mc, GuiNewChat log, float x, float y) {
        int scale = new ScaledResolution(mc).getScaleFactor();
        return log.getChatComponent((int) (x * scale), mc.displayHeight - 1 - (int) (y * scale));
    }
    private static void advance(Minecraft mc, GuiChat chat, GuiNewChat log, int milliseconds) throws Exception {
        Object motion = field(GuiNewChat.class, "motion").get(log);
        field(ChatMotion.class, "previous").setLong(motion, System.nanoTime() - milliseconds * 1_000_000L);
        GuiChatInputNativeCheck.draw(mc, chat);
    }
    private static void resize(Minecraft mc, GuiChat chat, int width, int height, int scale) {
        mc.displayWidth = width; mc.displayHeight = height; mc.gameSettings.guiScale = scale;
        mc.getFramebuffer().createBindFramebuffer(width, height);
        ScaledResolution resolution = new ScaledResolution(mc);
        chat.onResize(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
    }
    private static BufferedImage capture(Minecraft mc) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(mc.displayWidth * mc.displayHeight * 4);
        glReadPixels(0, 0, mc.displayWidth, mc.displayHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(mc.displayWidth, mc.displayHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < mc.displayHeight; y++) for (int x = 0; x < mc.displayWidth; x++) {
            int index = (y * mc.displayWidth + x) * 4;
            image.setRGB(x, mc.displayHeight - y - 1, 0xFF000000 | (pixels.get(index) & 255) << 16
                    | (pixels.get(index + 1) & 255) << 8 | pixels.get(index + 2) & 255);
        }
        return image;
    }
    private static void snapshot(Minecraft mc, String filename) throws Exception { ImageIO.write(capture(mc), "png", new File(OUTPUT, filename)); }
    private static String plain(String value) { return EnumChatFormatting.getTextWithoutFormattingCodes(value).replaceAll("\\s+", ""); }
    @SuppressWarnings("unchecked") private static List<ChatLine> lines(GuiNewChat log, String name) throws Exception { return (List<ChatLine>) field(GuiNewChat.class, name).get(log); }
    private static float[] floats(GuiNewChat log, String name) throws Exception { return (float[]) field(GuiNewChat.class, name).get(log); }
    private static float number(GuiNewChat log, String name) throws Exception { return field(GuiNewChat.class, name).getFloat(log); }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
