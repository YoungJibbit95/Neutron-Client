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
            checkMotion(mc, chat, log, settings);
            checkWarmResources(mc, chat, log, settings);
            require(glGetError() == GL_NO_ERROR, "Chat presentation left a GL error");
            System.out.println("PASS: compact chat presentation: rendered link hits at .5/.8/1 scale, input cursor and resize selection, viewport reflow and scroll anchor, opening/typing/closing/reopening, reduced motion, zero warm GPU allocations; chat-preview PNGs");
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
            requireLinkPixels(mc, log, image, "/fixture-inspect");
            float x = number(log, "originX") + (ChatMotion.TEXT_INSET + 4) * scale;
            float y = number(log, "originY") + (floats(log, "lineY")[0] + mc.fontRendererObj.FONT_HEIGHT + 1) * scale;
            require(componentAt(mc, log, x, y) == null, "Inter-line gap activates a chat component at scale " + scale);
            require(componentAt(mc, log, number(log, "surfaceX") - 1, y) == null,
                    "A clipped outside pixel activates a chat component");
            ImageIO.write(image, "png", new File(OUTPUT, "links-scale-" + scale + ".png"));
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
                require(component != null && component.getChatStyle().getChatClickEvent() != null
                                && action.equals(component.getChatStyle().getChatClickEvent().getValue()),
                        "Actual visible link pixel misses its component at chat scale " + chatScale + ": " + px + "," + py);
                count++;
            }
        }
        require(count > 2, "No readable link glyph pixels rendered at chat scale " + chatScale);
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
