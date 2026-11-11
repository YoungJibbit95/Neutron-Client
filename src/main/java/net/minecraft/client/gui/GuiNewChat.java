package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import java.awt.Color;
import java.nio.IntBuffer;
import me.youngjibbit.gui.ChatMotion;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuiNewChat extends Gui
{
    private static final Logger logger = LogManager.getLogger();
    private final Minecraft mc;
    private final List<String> sentMessages = Lists.<String>newArrayList();
    private final List<ChatLine> chatLines = Lists.<ChatLine>newArrayList();
    private final List<ChatLine> field_146253_i = Lists.<ChatLine>newArrayList();
    private int scrollPos;
    private boolean isScrolled;
    private final ChatMotion motion = new ChatMotion();
    private final HudSpring panelWidth = new HudSpring(), panelHeight = new HudSpring();
    private final ArrayListSurface messageSurface = new ArrayListSurface(), inputSurface = new ArrayListSurface();
    // Reused frame snapshot: painting and link picking use exactly the same transforms.
    private final ChatLine[] presentedLines = new ChatLine[100];
    private final String[] presentedText = new String[100];
    private final float[] lineX = new float[100], lineY = new float[100], lineAlpha = new float[100];
    private final IntBuffer parentScissor = BufferUtils.createIntBuffer(16);
    private final Runnable messageContents = this::drawMessageContents, inputContents = this::drawInputContents;
    private int presentedCount, viewportHeight, viewportPixelHeight, viewportScale, wrappedWidth = -1;
    private float originX, originY, renderScale, surfaceX, surfaceY, surfaceWidth, surfaceHeight;
    private boolean snapLinePositions = true;
    private GuiTextField presentedInput, closingInput;
    private int inputWidth, inputHeight;
    private String lastInputText = "";
    private float inputOffsetX, inputOffsetY, inputAlpha;

    public GuiNewChat(Minecraft mcIn)
    {
        this.mc = mcIn;
    }

    public void drawChat(int updateCounter)
    {
        long now = System.nanoTime();
        boolean animate = UiEffects.motion();
        String intensity = motionIntensity();
        motion.open(getChatOpen());
        motion.advance(now, animate, intensity);
        presentedCount = 0;
        if (this.mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) return;
        ScaledResolution resolution = new ScaledResolution(this.mc);
        int effectiveWidth = ChatMotion.textWidth(getChatWidth(), getChatScale(), resolution.getScaledWidth());
        if (wrappedWidth != effectiveWidth) refreshChat();
        viewportHeight = resolution.getScaledHeight();
        viewportPixelHeight = this.mc.displayHeight;
        viewportScale = resolution.getScaleFactor();
        originX = 4;
        originY = viewportHeight - 30 + motion.chatSlide();
        renderScale = Math.max(.01f, getChatScale());
        float opacity = this.mc.gameSettings.chatOpacity * .9f + .1f;
        float widest = 42, maximumAlpha = 0;
        int visibleRows = 0, limit = Math.min(getLineCount(), presentedLines.length);
        for (int row = 0; row < limit && row + scrollPos < field_146253_i.size(); row++) {
            ChatLine line = field_146253_i.get(row + scrollPos);
            float age = Math.max(0, updateCounter - line.getUpdatedCounter() + this.mc.timer.renderPartialTicks);
            float appearance = line.presentation().appearance(now, animate);
            float alpha = Math.max(ChatMotion.lifetimeAlpha(age), motion.focusAlpha())
                    * Math.max(0, Math.min(1, appearance)) * opacity;
            float y = line.presentation().y(row, motion.seconds(), animate && !snapLinePositions, intensity);
            if (alpha <= .003f) continue;
            float exit = animate ? ChatMotion.exitSlide(age) * (1 - motion.focusAlpha()) : 0;
            int index = presentedCount++;
            presentedLines[index] = line;
            presentedText[index] = line.getChatComponent().getFormattedText();
            lineX[index] = ChatMotion.TEXT_INSET + (appearance - 1) * 8 - exit * 8;
            lineY[index] = y + (1 - appearance) * 3 + exit * 2;
            lineAlpha[index] = alpha;
            widest = Math.max(widest, this.mc.fontRendererObj.getStringWidth(presentedText[index]));
            maximumAlpha = Math.max(maximumAlpha, alpha);
            visibleRows = row + 1;
        }
        snapLinePositions = false;
        if (presentedCount == 0) {
            panelWidth.reset();
            panelHeight.reset();
            return;
        }
        float width = panelWidth.step(Math.min(effectiveWidth, widest) + ChatMotion.TEXT_INSET * 2,
                motion.seconds(), animate, false, intensity);
        float height = panelHeight.step(visibleRows * ChatMotion.LINE_HEIGHT + 6,
                motion.seconds(), animate, false, intensity);
        surfaceX = originX;
        surfaceY = Math.max(2, originY - (height - 3) * renderScale);
        surfaceWidth = Math.min(resolution.getScaledWidth() - 8, Math.max(24, width * renderScale));
        surfaceHeight = Math.max(1, originY + 3 * renderScale - surfaceY);
        messageSurface.inheritedPanel(surfaceX, surfaceY, surfaceWidth, surfaceHeight, 6,
                Theme.getDefaultTheme(), maximumAlpha, messageContents);
    }

    private void drawMessageContents()
    {
        clip(surfaceX + 2, surfaceY + 2, surfaceWidth - 4, surfaceHeight - 4);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(originX, originY, 0);
            GlStateManager.scale(renderScale, renderScale, 1);
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha();
            for (int i = presentedCount - 1; i >= 0; i--) {
                int alpha = Math.max(4, Math.min(255, Math.round(lineAlpha[i] * 255)));
                this.mc.fontRendererObj.drawStringWithShadow(presentedText[i], lineX[i], lineY[i],
                        0x00ffffff | alpha << 24);
            }
        } finally {
            GlStateManager.popMatrix();
        }
        if (getChatOpen() && field_146253_i.size() > getLineCount()) {
            float track = Math.max(1, surfaceHeight - 10);
            float thumb = Math.max(10, track * getLineCount() / field_146253_i.size());
            float fraction = scrollPos / (float) Math.max(1, field_146253_i.size() - getLineCount());
            UIUtils.drawDecorationRound(surfaceX + surfaceWidth - 3,
                    surfaceY + 5 + (track - thumb) * (1 - fraction), 1.5f, thumb, .75f,
                    new Color(UiEffects.alpha(Theme.getDefaultTheme().getAccentColor().getRGB(), .8f), true));
        }
    }

    public void openInput()
    {
        closingInput = null;
        lastInputText = "";
        motion.open(true);
    }

    public void closeInput(GuiTextField field, int width, int height)
    {
        closingInput = field;
        if (field != null) field.setFocused(false);
        inputWidth = width;
        inputHeight = height;
        motion.open(false);
    }

    public void drawInput(GuiTextField field, int width, int height)
    {
        long now = System.nanoTime();
        if (!lastInputText.equals(field.getText())) {
            lastInputText = field.getText();
            motion.edited(now);
        }
        motion.open(true);
        motion.advance(now, UiEffects.motion(), motionIntensity());
        presentedInput = field;
        inputWidth = width;
        inputHeight = height;
        renderInput();
    }

    /** The HUD owns the short closing tail after GuiChat has left currentScreen. */
    public void drawClosingInput()
    {
        if (getChatOpen() || closingInput == null) return;
        if (this.mc.currentScreen != null || motion.focusAlpha() <= .003f) {
            closingInput = presentedInput = null;
            return;
        }
        presentedInput = closingInput;
        renderInput();
    }

    private void renderInput()
    {
        inputAlpha = motion.focusAlpha();
        inputOffsetX = motion.textSlideX();
        inputOffsetY = motion.inputSlide() + motion.textSlideY();
        if (inputAlpha <= .003f) return;
        inputSurface.inheritedPanel(4, inputHeight - 22 + motion.inputSlide(), inputWidth - 8, 18, 7,
                Theme.getDefaultTheme(), inputAlpha, inputContents);
    }

    private void drawInputContents()
    {
        float top = inputHeight - 22 + motion.inputSlide();
        UIUtils.drawDecorationRound(10, top + 5, 2, 8, 1,
                new Color(UiEffects.alpha(Theme.getDefaultTheme().getAccentColor().getRGB(), inputAlpha), true));
        clip(16, top + 2, inputWidth - 24, 14);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(inputOffsetX, inputOffsetY, 0);
            presentedInput.setTextColor(Math.max(4, Math.round(inputAlpha * 255)) << 24 | 0x00f1f4fb);
            presentedInput.drawTextBox();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    public int inputMouseX(int mouseX) { return (int) Math.floor(mouseX - inputOffsetX); }
    public int inputMouseY(int mouseY) { return (int) Math.floor(mouseY - inputOffsetY); }

    private static String motionIntensity()
    {
        ClientSettings settings = ClientSettings.current();
        return settings == null ? "Balanced" : settings.motionIntensity.getValue();
    }

    /** Intersect with the caller's scissor; ArrayListSurface restores it after the callback. */
    private void clip(float x, float y, float width, float height)
    {
        ScaledResolution resolution = new ScaledResolution(this.mc);
        int scale = resolution.getScaleFactor();
        int left = Math.max(0, (int) Math.ceil(x * scale));
        int bottom = Math.max(0, (int) Math.ceil(this.mc.displayHeight - (y + height) * scale));
        int right = Math.min(this.mc.displayWidth, (int) Math.floor((x + width) * scale));
        int top = Math.min(this.mc.displayHeight, (int) Math.floor(this.mc.displayHeight - y * scale));
        if (glIsEnabled(GL_SCISSOR_TEST)) {
            parentScissor.clear();
            glGetInteger(GL_SCISSOR_BOX, parentScissor);
            left = Math.max(left, parentScissor.get(0));
            bottom = Math.max(bottom, parentScissor.get(1));
            right = Math.min(right, parentScissor.get(0) + parentScissor.get(2));
            top = Math.min(top, parentScissor.get(1) + parentScissor.get(3));
        }
        glEnable(GL_SCISSOR_TEST);
        glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }

    /**
     * Clears the chat.
     */
    public void clearChatMessages()
    {
        this.field_146253_i.clear();
        this.chatLines.clear();
        this.sentMessages.clear();
        java.util.Arrays.fill(presentedLines, null);
        java.util.Arrays.fill(presentedText, null);
        presentedCount = 0;
        closingInput = presentedInput = null;
        motion.reset();
        panelWidth.reset();
        panelHeight.reset();
        messageSurface.close();
        inputSurface.close();
        snapLinePositions = true;
    }

    public void printChatMessage(IChatComponent p_146227_1_)
    {
        this.printChatMessageWithOptionalDeletion(p_146227_1_, 0);
    }

    /**
     * prints the ChatComponent to Chat. If the ID is not 0, deletes an existing Chat Line of that ID from the GUI
     */
    public void printChatMessageWithOptionalDeletion(IChatComponent p_146234_1_, int p_146234_2_)
    {
        this.setChatLine(p_146234_1_, p_146234_2_, this.mc.ingameGUI.getUpdateCounter(), false);
        logger.info("[CHAT] " + p_146234_1_.getUnformattedText());
    }

    private void setChatLine(IChatComponent p_146237_1_, int p_146237_2_, int p_146237_3_, boolean p_146237_4_)
    {
        if (p_146237_2_ != 0 && !p_146237_4_)
        {
            this.deleteChatLine(p_146237_2_);
        }

        int i = ChatMotion.textWidth(getChatWidth(), getChatScale(), new ScaledResolution(this.mc).getScaledWidth());
        if (field_146253_i.isEmpty() && chatLines.isEmpty()) wrappedWidth = i;
        List<IChatComponent> list = GuiUtilRenderComponents.func_178908_a(p_146237_1_, i, this.mc.fontRendererObj, false, false);
        boolean flag = this.getChatOpen();
        int sourceOffset = 0;

        for (IChatComponent ichatcomponent : list)
        {
            if (flag && this.scrollPos > 0)
            {
                this.isScrolled = true;
                this.scroll(1);
            }

            this.field_146253_i.add(0, new ChatLine(p_146237_3_, ichatcomponent, p_146237_2_,
                    !p_146237_4_, p_146237_1_, sourceOffset));
            sourceOffset += net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(ichatcomponent.getUnformattedText()).length();
        }

        while (this.field_146253_i.size() > 100)
        {
            this.field_146253_i.remove(this.field_146253_i.size() - 1);
        }

        if (!p_146237_4_)
        {
            this.chatLines.add(0, new ChatLine(p_146237_3_, p_146237_1_, p_146237_2_, false));

            while (this.chatLines.size() > 100)
            {
                this.chatLines.remove(this.chatLines.size() - 1);
            }
        }
    }

    public void refreshChat()
    {
        ChatLine anchor = scrollPos > 0 && scrollPos < field_146253_i.size() ? field_146253_i.get(scrollPos) : null;
        wrappedWidth = ChatMotion.textWidth(getChatWidth(), getChatScale(), new ScaledResolution(this.mc).getScaledWidth());
        this.field_146253_i.clear();
        this.resetScroll();

        for (int i = this.chatLines.size() - 1; i >= 0; --i)
        {
            ChatLine chatline = (ChatLine)this.chatLines.get(i);
            this.setChatLine(chatline.getChatComponent(), chatline.getChatLineID(), chatline.getUpdatedCounter(), true);
        }
        if (anchor != null) {
            int best = -1, distance = Integer.MAX_VALUE, fallback = -1, fallbackDistance = Integer.MAX_VALUE;
            for (int i = 0; i < field_146253_i.size(); i++) {
                ChatLine line = field_146253_i.get(i);
                if (line.source() != anchor.source()) continue;
                int candidate = anchor.sourceOffset() - line.sourceOffset();
                int absolute = Math.abs(candidate);
                if (absolute < fallbackDistance) { fallback = i; fallbackDistance = absolute; }
                // Keep the old character inside its new row, never skip forward to the next row.
                if (candidate >= 0 && candidate < distance) { best = i; distance = candidate; }
            }
            if (best < 0) best = fallback; // The oldest source fragments may have hit the 100-line cap.
            if (best > 0) {
                scroll(best);
                isScrolled = scrollPos > 0;
            }
        }
    }

    public List<String> getSentMessages()
    {
        return this.sentMessages;
    }

    /**
     * Adds this string to the list of sent messages, for recall using the up/down arrow keys
     */
    public void addToSentMessages(String p_146239_1_)
    {
        if (this.sentMessages.isEmpty() || !((String)this.sentMessages.get(this.sentMessages.size() - 1)).equals(p_146239_1_))
        {
            this.sentMessages.add(p_146239_1_);
        }
    }

    /**
     * Resets the chat scroll (executed when the GUI is closed, among others)
     */
    public void resetScroll()
    {
        this.scrollPos = 0;
        this.isScrolled = false;
        snapLinePositions = true;
        presentedCount = 0;
    }

    /**
     * Scrolls the chat by the given number of lines.
     */
    public void scroll(int p_146229_1_)
    {
        snapLinePositions = true;
        presentedCount = 0;
        this.scrollPos += p_146229_1_;
        int i = this.field_146253_i.size();

        if (this.scrollPos > i - this.getLineCount())
        {
            this.scrollPos = i - this.getLineCount();
        }

        if (this.scrollPos <= 0)
        {
            this.scrollPos = 0;
            this.isScrolled = false;
        }
    }

    /**
     * Gets the chat component under the mouse
     */
    public IChatComponent getChatComponent(int mouseX, int mouseY)
    {
        if (!getChatOpen() || presentedCount == 0 || viewportScale <= 0) return null;
        // Mouse indices address framebuffer pixels; use their centers, matching rasterized glyph coverage.
        float screenX = (mouseX + .5f) / viewportScale;
        float screenY = (viewportPixelHeight - mouseY - .5f) / viewportScale;
        if (screenX < surfaceX + 2 || screenX >= surfaceX + surfaceWidth - 2
                || screenY < surfaceY + 2 || screenY >= surfaceY + surfaceHeight - 2) return null;
        float x = (screenX - originX) / renderScale, y = (screenY - originY) / renderScale;
        for (int i = 0; i < presentedCount; i++) {
            if (lineAlpha[i] < .08f || y < lineY[i] || y >= lineY[i] + this.mc.fontRendererObj.FONT_HEIGHT
                    || x < lineX[i]) continue;
            int width = 0;
            for (IChatComponent component : presentedLines[i].getChatComponent()) {
                if (component instanceof ChatComponentText) {
                    width += this.mc.fontRendererObj.getStringWidth(GuiUtilRenderComponents.func_178909_a(
                            ((ChatComponentText) component).getChatComponentText_TextValue(), false));
                    if (width > x - lineX[i]) return component;
                }
            }
        }
        return null;
    }

    /**
     * Returns true if the chat GUI is open
     */
    public boolean getChatOpen()
    {
        return this.mc.currentScreen instanceof GuiChat;
    }

    /**
     * finds and deletes a Chat line by ID
     */
    public void deleteChatLine(int p_146242_1_)
    {
        presentedCount = 0;
        Iterator<ChatLine> iterator = this.field_146253_i.iterator();

        while (iterator.hasNext())
        {
            ChatLine chatline = (ChatLine)iterator.next();

            if (chatline.getChatLineID() == p_146242_1_)
            {
                iterator.remove();
            }
        }

        iterator = this.chatLines.iterator();

        while (iterator.hasNext())
        {
            ChatLine chatline1 = (ChatLine)iterator.next();

            if (chatline1.getChatLineID() == p_146242_1_)
            {
                iterator.remove();
                break;
            }
        }
    }

    public int getChatWidth()
    {
        return calculateChatboxWidth(this.mc.gameSettings.chatWidth);
    }

    public int getChatHeight()
    {
        return calculateChatboxHeight(this.getChatOpen() ? this.mc.gameSettings.chatHeightFocused : this.mc.gameSettings.chatHeightUnfocused);
    }

    /**
     * Returns the chatscale from mc.gameSettings.chatScale
     */
    public float getChatScale()
    {
        return this.mc.gameSettings.chatScale;
    }

    public static int calculateChatboxWidth(float p_146233_0_)
    {
        int i = 320;
        int j = 40;
        return MathHelper.floor_float(p_146233_0_ * (float)(i - j) + (float)j);
    }

    public static int calculateChatboxHeight(float p_146243_0_)
    {
        int i = 180;
        int j = 20;
        return MathHelper.floor_float(p_146243_0_ * (float)(i - j) + (float)j);
    }

    public int getLineCount()
    {
        return Math.max(1, this.getChatHeight() / ChatMotion.LINE_HEIGHT);
    }
}
