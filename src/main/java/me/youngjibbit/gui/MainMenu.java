package me.youngjibbit.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import me.youngjibbit.utils.ColorUtils;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.io.IOException;

public class MainMenu extends GuiScreen

{

    private static final int BACKGROUND_WIDTH = 1920;
    private static final int BACKGROUND_HEIGHT = 1080;

    // Replace with your actual logo dimensions
    private static final int LOGO_WIDTH = 210;
    private static final int LOGO_HEIGHT = 100;

    public MainMenu()

    {

    }

    @Override
    public void initGui()

    {

    }

    final ResourceLocation logoLocation = new ResourceLocation("pulsar/MenuLogo.png");

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)

    {
        // Draw Background
        mc.getTextureManager().bindTexture(new ResourceLocation("pulsar/MainMenuBackground.jpg"));
        this.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, this.width, this.height, this.width, this.height);

        // Draw the "Pulsar" logo image
        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2 - LOGO_WIDTH / 2, height / 22.5f + 80, 0); // Center above buttons
        GlStateManager.scale(1.0, 1.0, 1.0); // Scale if necessary
        this.mc.getTextureManager().bindTexture(logoLocation);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
        GlStateManager.popMatrix();

        // Draw buttons with increased font size and gradient border with filled edges
        String[] buttons =

                {
                        " SinglePlayer", " MultiPlayer", " AltManager", " Settings", " Language", " Quit"
                };

        int yBase = height / 3 + 60; // Adjust Y position for buttons

        for (int i = 0; i < buttons.length; i++)

        {
            String name = buttons[i];
            int x = (int) ((width - mc.fontRendererObj.getStringWidth(name) * 1.5f) / 2); // Adjust for increased font size
            int y = yBase + i * 28; // Vertical spacing for each button
            int buttonWidth = (int) (mc.fontRendererObj.getStringWidth(name) * 1.5f);
            int buttonHeight = mc.fontRendererObj.FONT_HEIGHT + 4; // Padding

            // Draw black background rectangle with rounded corners and filled edges
            drawRoundedRect(x - 2, y - 2, x + buttonWidth + 2, y + buttonHeight, 5, Color.BLACK.getRGB(), Color.WHITE.getRGB(), 1);

            // Draw button text with gradient
            drawGradientString(name, x, y, i * 5000000L); // Adjust offset for wider color coverage
        }
    }

    private static final int GRADIENT_OFFSET = 5; // Adjust as needed for gradient offset

    private void drawRoundedRect(int left, int top, int right, int bottom, int radius, int i, int j, int k) {
        Color outlineColor = ColorUtils.gradientEffect(GRADIENT_OFFSET, 1.0f).brighter().brighter().brighter();

        int adjustedRadius = radius + 3; // Adjust the radius to make the corners farther apart
        int additionalSpace = 14; // Additional space in the middle

        // Adjust the left and right positions to include the additional space
        right += additionalSpace / 4;
        left -= additionalSpace / 8;

        drawArc(left + adjustedRadius, top + adjustedRadius, adjustedRadius, 180, 270, outlineColor); // Top-left corner
        drawArc(right - adjustedRadius, top + adjustedRadius, adjustedRadius, 270, 360, outlineColor); // Top-right corner
        drawArc(left + adjustedRadius, bottom - adjustedRadius, adjustedRadius, 90, 180, outlineColor); // Bottom-left corner
        drawArc(right - adjustedRadius, bottom - adjustedRadius, adjustedRadius, 0, 90, outlineColor); // Bottom-right corner

        // Draw top and bottom edges with half the previous thickness
        drawFilledRect(left + adjustedRadius - 2, top - 1, right - adjustedRadius + 1, top, outlineColor); // Top edge
        drawFilledRect(left + adjustedRadius - 2, bottom - 1, right - adjustedRadius + 1, bottom, outlineColor); // Bottom edge

        // Draw left and right edges with half the previous thickness
        drawFilledRect(left, top + adjustedRadius, left + 1, bottom - adjustedRadius, outlineColor); // Left edge
        drawFilledRect(right, top + adjustedRadius, right + 1, bottom - adjustedRadius, outlineColor); // Right edge
    }


    private void drawFilledRect(int left, int top, int right, int bottom, Color outlineColor)

    {

        for (int i = top; i <= bottom; i++)

        {

            float lerp = (float) (i - top) / (bottom - top);
            Color gradientColor = ColorUtils.gradientEffect(GRADIENT_OFFSET, lerp);
            int rgb = gradientColor.getRGB();
            Gui.drawRect(left, i, right, i + 2, rgb);

        }
    }

    private void drawArc(int x, int y, int radius, int startAngle, int endAngle, Color outlineColor)

    {

        double pi = Math.PI;
        int thickness = 3; // Set the thickness of the arc

        for (int i = startAngle; i <= endAngle; i++)

        {

            double angle = i * pi / 180;

            // Draw multiple points to increase thickness
            for (int t = 0; t < thickness; t++)

            {

                int xPos = x + (int) (Math.cos(angle) * (radius + t));
                int yPos = y + (int) (Math.sin(angle) * (radius + t));
                float lerp = (float) (i - startAngle) / (endAngle - startAngle);
                Color gradientColor = ColorUtils.gradientEffect(GRADIENT_OFFSET, lerp);
                int rgb = gradientColor.getRGB();
                Gui.drawRect(xPos, yPos, xPos - 1, yPos + 2, rgb);

            }
        }
    }


    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException

    {

        super.mouseClicked(mouseX, mouseY, mouseButton);

        String[] buttons =
                {
                        "SinglePlayer", "MultiPlayer", "AltManager", "Settings", "Language", "Quit"
                };

        int yBase = height / 3 + 60; // Adjust Y position for buttons

        for (int i = 0; i < buttons.length; i++)

        {

            String name = buttons[i];
            int buttonWidth = (int) (mc.fontRendererObj.getStringWidth(name) * 1.5f);
            int buttonHeight = mc.fontRendererObj.FONT_HEIGHT + 4; // Padding
            int x = (width - buttonWidth) / 2; // Centered x position
            int y = yBase + i * 28; // Vertical spacing for each button

            // Check if the mouse is over the button
            if (mouseX >= x && mouseY >= y && mouseX < x + buttonWidth && mouseY < y + buttonHeight)

            {

                switch (name)
                {
                    case "SinglePlayer":
                        mc.displayGuiScreen(new GuiSelectWorld(this));
                        return;
                    case "MultiPlayer":
                        mc.displayGuiScreen(new GuiMultiplayer(this));
                        return;
                    case "AltManager":
                        // Implement AltManager action
                        return;
                    case "Settings":
                        mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                        return;
                    case "Language":
                        mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
                        return;
                    case "Quit":
                        mc.shutdown();
                        return;
                }
            }
        }
    }

    private void drawGradientString(String text, float x, float y, long offset)

    {

        for (int i = 0; i < text.length(); i++)

        {
            char c = text.charAt(i);
            Color color = ColorUtils.gradientEffect(offset + (i * 50000000L), 1.0f); // Adjust offset for smoother transition
            String s = String.valueOf(c);
            mc.fontRendererObj.drawStringWithShadow(s, x, y, color.getRGB());
            x += mc.fontRendererObj.getCharWidth(c) * 1.5f; // Adjust for increased font size
        }
    }
}
