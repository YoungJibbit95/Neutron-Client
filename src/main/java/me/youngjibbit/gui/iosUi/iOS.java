package me.youngjibbit.gui.iosUi;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.GuiClip;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.clickgui.utils.PanelUtils;
import me.youngjibbit.gui.MenuShader;
import me.youngjibbit.gui.iosUi.component.impl.iOSModule;
import me.youngjibbit.gui.star.StarParticle;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.render.ClickGUI;
import me.youngjibbit.utils.RenderUtil;
import me.youngjibbit.utils.RenderUtils;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.input.MouseUtil;
import me.youngjibbit.utils.render.AnimationEngine;
import me.youngjibbit.utils.render.Blur;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.UIUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class iOS extends GuiScreen
{
    private static final Category[] CATEGORIES = Category.values();
    private static final float SIDEBAR_WIDTH = 94.0f;
    private static final float HEADER_HEIGHT = 20.0f;
    private final AnimationEngine animationEngine = new AnimationEngine(0f, 1f, 220);

    public float rectHeight = 255, rectWidth = 370;
    private float x, y, lastX, lastY, width, height;
    private boolean dragging;
    private Category selectedCategory;
    private final List<iOSModule> MODULES = new ArrayList<>();
    public float scrollOffset = 0;
    private float maxScroll;
    private float selectedCategoryY;
    private final me.youngjibbit.utils.render.ArrayListSurface surface=new me.youngjibbit.utils.render.ArrayListSurface();
    private final List<StarParticle> particles;
    Theme currentTheme = applyTheme();
    int[] outlineColors = getStaticOutlineGradientColors(currentTheme);
    private boolean closing = false;
    private float renderedScale=1;

    private final PanelUtils panelUtils = new PanelUtils();

    public iOS()
    {
        this.particles = new ArrayList<>();
        generateParticles(70);
        this.width = 350;
        this.height = 200;
        this.x = 250;
        this.y = 20;
        this.selectedCategory = Category.Combat;
        this.setupModules();
    }

    /** Ends interrupted gestures without changing selected settings or categories. */
    private void cancelInputs() {
        dragging = false;
        lastX = lastY = 0;
        MODULES.forEach(iOSModule::cancelInputs);
    }

    @Override public void onGuiClosed(){cancelInputs();surface.close();super.onGuiClosed();}

    @Override
    public void initGui() {
        cancelInputs();
        this.x=(super.width-width)/2;this.y=Math.max(8,(super.height-height)/2);
        animationEngine.setIsDrawAnimation(true);
        animationEngine.reset();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        super.drawScreen(mouseX, mouseY, partialTicks);
        GuiShaderBloom.beginBatch();
        MenuShader.mm2(Pulsar.INSTANCE.getStartTimeLong());
        currentTheme = Theme.resolve(Theme.getDefaultTheme());
        outlineColors = getStaticOutlineGradientColors(currentTheme);

        int[] outlineColors = getStaticOutlineGradientColors(currentTheme);
        int color01 = outlineColors[0];
        int color02 = outlineColors[1];
        long cT = (long)(UiEffects.time()*1000);
        float cycleDuration = 3000.0f;
        int interpolatedColor1 = interpolateTwoColorsSmoothly(color01, color02, cT, cycleDuration);
        float animProgress = UiEffects.motion()?animationEngine.getAnimationValue():1; // 0 to 1
        float fit=Math.min(1,Math.min((super.width-16)/width,(super.height-16)/height));
        renderedScale=Math.max(0.001f,animProgress*fit);
        x=Math.max(8+width*fit/2,Math.min(x+width/2,super.width-8-width*fit/2))-width/2;
        y=Math.max(8+height*fit/2,Math.min(y+height/2,super.height-8-height*fit/2))-height/2;
        this.smoothDrag(mouseX,mouseY);
        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        int localMouseX=(int)(centerX+(mouseX-centerX)/renderedScale),localMouseY=(int)(centerY+(mouseY-centerY)/renderedScale);


        for (StarParticle particle : particles)
        {
            particle.update((int) this.width + 300, (int) this.height);
            particle.render(mouseX, mouseY, particles);
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0);
        GL11.glScalef(renderedScale, renderedScale, 1f);
        GL11.glTranslatef(-centerX, -centerY, 0);
        GuiShaderBloom.flushBatch(2,3);
        float[] bounds=me.youngjibbit.utils.render.ScreenRect.project(x,y,width,height-1);
        surface.panel(bounds[0],bounds[1],bounds[2],bounds[3],8*renderedScale,currentTheme,true,true,true,5,.72f,.68f,()->{
        GL11.glPushMatrix();GL11.glTranslatef(centerX,centerY,0);GL11.glScalef(renderedScale,renderedScale,1);GL11.glTranslatef(-centerX,-centerY,0);
        UIUtils.drawRound(x + SIDEBAR_WIDTH, y, width - SIDEBAR_WIDTH, height - 1, 8, true, new Color(255, 255, 255, 16));
        UIUtils.drawRound(x + SIDEBAR_WIDTH + 1.0f, y + HEADER_HEIGHT, width - SIDEBAR_WIDTH - 4.0f, height - HEADER_HEIGHT - 4.0f, 8, true, new Color(0, 0, 0, 46));


        if (animationEngine.isAnimationDone()) {
            animationEngine.setIsDrawAnimation(false);
        }

        RenderUtil.drawCircle((int) (x + 10), (int) (y + 10), 2.8f, Color.red.getRGB());
        RenderUtil.drawCircle((int) (x + 20), (int) (y + 10), 2.8f, Color.yellow.getRGB());
        RenderUtil.drawCircle((int) (x + 30), (int) (y + 10), 2.8f, Color.green.getRGB());

        UIUtils.drawRoundOutline(x, y, this.width, this.height - 1, 8, 0.65f, new Color(0,0,0,1), UiEffects.accent(interpolatedColor1, 0.68f));
        Category cat = selectedCategory;
        Pulsar.INSTANCE.getFontManager()
                .getFont("SFProText-Bold 20")
                .drawString(
                        cat.name(),
                        (int) (x + 120),
                        y+ 4, Color.white.getRGB());

        Pulsar.INSTANCE.getFontManager()
                .getFont("icon1 19")
                .drawString(
                        "l",
                        (int) (x + 105),
                        y+ 6, UiEffects.alpha(interpolatedColor1, 0.95f));

        drawGlowingDot();

        icon1("c", (int) (x + 5), (int) (y + 27), Theme.Rainbow);
        /*MOVEMENT*/icon1("f", (int) (x + 5), (int) (y + 44), Theme.Rainbow);
        /*PLAYER*/icon2("d", (int) (x + 5), (int) (y + 62), Theme.Rainbow);
        /*RENDER*/icon1("d", (int) (x + 5), (int) (y + 79), Theme.Rainbow);
        /*WORLD*/icon2("e", (int) (x + 5), (int) (y + 97), Theme.Rainbow);
        /*CLIENT*/icon1("t", (int) (x + 5), (int) (y + 113), Theme.Rainbow);
        /*GHOST*/icon2("f", (int) (x + 5), (int) (y + 130), Theme.Rainbow);
        /*EXPLOIT*/icon1("a", (int) (x + 5), (int) (y + 147),Theme.Rainbow);
        /*TESTMODULES*/icon1("n", (int) (x + 5), (int) (y + 163), Theme.Rainbow);

        //Category Rendering
        int i = 0;
        for (Category category : CATEGORIES)
        {
            float categoryY = y + 20 + i;

            Pulsar.INSTANCE.getFontManager()
                    .getFont("KhmerOureang 19")
                    .drawString(
                            category.name(),
                            (int) (x + 21),
                            (int) (y + 18 + (float) mc.fontRendererObj.FONT_HEIGHT + i),
                            isSelectedCategory(category) ? Color.white.getRGB() : new Color(210, 215, 230, 180).getRGB());
            i += 17;
            if (isSelectedCategory(category))
            {
                selectedCategoryY = categoryY;
            }
        }

        float contentHeight = 0;
        for (iOSModule module : MODULES) contentHeight += module.getHeight() + 4;
        maxScroll = Math.max(0, contentHeight - (height - 28));
        scrollOffset = Math.max(-maxScroll, Math.min(0, scrollOffset));
        GL11.glPushMatrix();
        try(GuiClip clip=GuiClip.pushTransformed(new ClipRect(x+102,y+20,width-104,height-22))) {
        GL11.glTranslatef(0, scrollOffset, 0);

        i = 0;
        for (iOSModule module : MODULES) {
            module.updateComponent(x + 106, y + 24 + i);
            module.drawScreen(localMouseX, localMouseY);
            i += module.getHeight() + 4;
        }

        }
        GL11.glPopMatrix();
        GL11.glPopMatrix();
        });
        GL11.glPopMatrix();
    }



    public void generateParticles(int count)
    {
        Random random = new Random();
        int width = (int) this.width;
        int height = (int) this.height;

        for (int i = 0; i < count; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float size = 10.0f + random.nextFloat();
            float alphaChangeRate = 0.01f + random.nextFloat() * 0.03f;
            particles.add(new StarParticle(x, y, size, alphaChangeRate));
        }
    }

    private static int interpolateTwoColorsSmoothly(int color1, int color2, long currentTime, float cycleDuration) {

        float progress = (currentTime % (long) cycleDuration) / cycleDuration;
        float factor = (float) (Math.sin(progress * 2 * Math.PI) * 0.5 + 0.5);

        int red1 = (color1 >> 16) & 0xFF;
        int green1 = (color1 >> 8) & 0xFF;
        int blue1 = color1 & 0xFF;

        int red2 = (color2 >> 16) & 0xFF;
        int green2 = (color2 >> 8) & 0xFF;
        int blue2 = color2 & 0xFF;

        int red = (int) (red1 + (red2 - red1) * factor);
        int green = (int) (green1 + (green2 - green1) * factor);
        int blue = (int) (blue1 + (blue2 - blue1) * factor);

        return (0xFF << 24) | (red << 16) | (green << 8) | blue;
    }

    private int[] getStaticOutlineGradientColors(Theme currentTheme)
    {
        Theme resolvedTheme = Theme.resolve(currentTheme);
        int color1 = resolvedTheme.getPrimaryGradientRGB();
        int color2 = resolvedTheme.getSecondaryGradientRGB();

        return new int[]
                {
                        color1,
                        color2,
                        color1,
                        color2
                };
    }

    public Theme applyTheme() {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    public void drawGlowingDot()
    {
        int accent = UiEffects.gradientAt(Theme.resolve(currentTheme),UiEffects.Channel.BORDER,x,y);

        UIUtils.drawRound(this.x + 4.0f, selectedCategoryY + 7.0f, 86.0f, 13.0f, 4, true, new Color(255, 255, 255, 24));
    }

    @Override
    public void handleMouseInput()
            throws IOException
    {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();

        if (scroll != 0) {
            if (scroll > 0) {
                scrollOffset += 20; //up
            } else if (scroll < 0) {
                scrollOffset -= 20; //down
            }

            if (scrollOffset > 0) {
                scrollOffset = 0;
            }
            if (scrollOffset < -maxScroll) {
                scrollOffset = -maxScroll;
            }
            scrollOffset = Math.max(Math.min(scrollOffset, 0), -maxScroll);
        }
    }

    private void setupModules()
    {
        for (Module module : Pulsar.INSTANCE.getMm().getModules(selectedCategory))
        {
            MODULES.add(new iOSModule(module, 0, 0, width - 112, 24));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton)
            throws IOException
    {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if(UiEffects.motion()&&animationEngine.getAnimationValue()<0.98f)return;
        int rawX=mouseX,rawY=mouseY;
        mouseX=(int)(x+width/2+(mouseX-x-width/2)/renderedScale);
        mouseY=(int)(y+height/2+(mouseY-y-height/2)/renderedScale);
        if (isOverDraggableArea(mouseX, mouseY) && mouseButton == 0) {
            this.dragging = true;
            this.lastX = rawX - x;
            this.lastY = rawY - y;
        } else {
            this.dragging = false;
        }

        int i = 0;
        for (Category category : CATEGORIES) {
            if (mouseButton==0 && MouseUtil.isMouseOver(mouseX, mouseY, x + 4, y + 25 + i, 86, 17)) {
                this.selectedCategory = category;
                MODULES.clear();
                this.setupModules();
                this.scrollOffset = 0.0f;
                return;
            }
            i += 17;
        }

        if(new ClipRect(x+102,y+20,width-104,height-22).contains(mouseX,mouseY))
            for(iOSModule module:MODULES)module.mouseClicked(mouseX,mouseY,mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state)
    {
        super.mouseReleased(mouseX, mouseY, state);
        this.dragging = false;
        MODULES.forEach(iOSModule::mouseReleased);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode)
            throws IOException
    {
        if (keyCode == Keyboard.KEY_ESCAPE)
        {
            Pulsar.INSTANCE.getMm()
                    .getModule(ClickGUI.class)
                    .setToggled(false);
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void smoothDrag(int mouseX, int mouseY)
    {
        //if (!openingAnimation.isDone() || closing) return; // Lock drag while animating
        if (dragging) {
            this.x = mouseX - lastX;
            this.y = mouseY - lastY;
        }
    }

    private boolean isOverDraggableArea(int mouseX, int mouseY)
    {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 20;
    }

    private boolean isSelectedCategory(Category category)
    {
        return selectedCategory.equals(category);
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    private void icon1(String text, int x, int y, Theme theme)
    {
        int currentX = x;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);
            double charOffset = (double) i / (double) text.length();

            int color = me.youngjibbit.utils.render.UiEffects.gradientColor(Theme.resolve(theme),me.youngjibbit.utils.render.UiEffects.Channel.TEXT,me.youngjibbit.utils.render.ScreenRect.project(currentX,y,1,1)[0],me.youngjibbit.utils.render.ScreenRect.project(currentX,y,1,1)[1]);


            String tmpStr = String.valueOf(currentChar);
            Pulsar.INSTANCE.getFontManager().getFont("icon 23").drawString(tmpStr, currentX, y, color);
            currentX += Pulsar.INSTANCE.getFontManager().getFont("icon 23").getWidth(tmpStr) * 0.78;
        }
    }

    private void icon2(String text, int x, int y, Theme theme)
    {
        int currentX = x;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);
            double charOffset = (double) i / (double) text.length();

            int color = me.youngjibbit.utils.render.UiEffects.gradientColor(Theme.resolve(theme),me.youngjibbit.utils.render.UiEffects.Channel.TEXT,me.youngjibbit.utils.render.ScreenRect.project(currentX,y,1,1)[0],me.youngjibbit.utils.render.ScreenRect.project(currentX,y,1,1)[1]);


            String tmpStr = String.valueOf(currentChar);
            Pulsar.INSTANCE.getFontManager().getFont("icon1 23").drawString(tmpStr, currentX, y, color);
            currentX += Pulsar.INSTANCE.getFontManager().getFont("icon1 23").getWidth(tmpStr) * 0.78;
        }
    }

    private int getColorFromTheme(Theme theme, float offset)
    {
        List<Color> colors = theme.getGradientColors();
        if (colors == null || colors.isEmpty())
        {
            return Color.WHITE.getRGB();
        }

        int size = colors.size();
        int index1 = (int) (offset * size) % size;
        int index2 = (index1 + 1) % size;

        float blend = offset * size - index1;

        Color color = Theme.convert(colors.get(index1), colors.get(index2), blend);

        return color.getRGB();
    }

    public float getScrollOffset() {
        return scrollOffset;
    }
}

