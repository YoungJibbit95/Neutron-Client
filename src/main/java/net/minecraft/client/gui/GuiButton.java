package net.minecraft.client.gui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.UIUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;

public class GuiButton extends Gui
{
    protected static final ResourceLocation buttonTextures = new ResourceLocation("textures/gui/widgets.png");

    /** Button width in pixels */
    protected int width;
    private long lastTime = System.nanoTime(); // Initialisez avec le temps actuel
    private float deltaTime = 0f;
    private float hoverProgress = 0f;


    protected float smoothnessX = 1, offsetX = 0;

    /** Button height in pixels */
    protected int height;

    /** The x position of this control. */
    public int xPosition;

    /** The y position of this control. */
    public int yPosition;

    /** The string displayed on this control. */
    public String displayString;
    public int id;

    /** True if this control is enabled, false to disable. */
    public boolean enabled;

    /** Hides the button completely if false. */
    public boolean visible;
    protected boolean hovered;

    public GuiButton(int buttonId, int x, int y, String buttonText)
    {
        this(buttonId, x, y, 200, 20, buttonText);
    }

    public GuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText)
    {
        this.width = 200;
        this.height = 20;
        this.enabled = true;
        this.visible = true;
        this.id = buttonId;
        this.xPosition = x;
        this.yPosition = y;
        this.width = widthIn;
        this.height = heightIn;
        this.displayString = buttonText;
    }

    /**
     * Returns 0 if the button is disabled, 1 if the mouse is NOT hovering over this button and 2 if it IS hovering over
     * this button.
     */
    protected int getHoverState(boolean mouseOver)
    {
        int i = 1;

        if (!this.enabled)
        {
            i = 0;
        }
        else if (mouseOver)
        {
            i = 2;
        }

        return i;
    }

    /**
     * Draws this button to the screen.
     */
    private static int surfaceBatch;
    private static final java.util.Map<Class<?>,Boolean> styledClasses=new java.util.HashMap<>();
    boolean styledSurface(){return styledClasses.computeIfAbsent(getClass(),type->{try{return type.getMethod("drawButton",Minecraft.class,int.class,int.class).getDeclaringClass()==GuiButton.class;}catch(NoSuchMethodException ex){return false;}});}
    static void beginSurfaces(java.util.List<GuiButton> buttons){
        surfaceBatch++;
        if(surfaceBatch>1||!UiEffects.effectsSupported())return;
        boolean any=false;for(GuiButton button:buttons)any|=button.visible&&button.styledSurface();if(!any)return;
        // Snapshot and one mask union before any of these buttons' glow, fill or sharp contents.
        if(!UiEffects.material()&&(UiEffects.blurEnabled()||UiEffects.glass())){
            if(UiEffects.scene()!=null&&UiEffects.scene().clean>0&&UiEffects.scene().blurred){for(GuiButton b:buttons)if(b.visible&&b.styledSurface())UiEffects.drawBlurredRect(b.xPosition,b.yPosition,b.width,b.height,5,Color.WHITE,4,1);}
            else{
            me.youngjibbit.utils.render.Blur.startBlur();
            try{for(GuiButton b:buttons)if(b.visible&&b.styledSurface())UIUtils.drawRound(b.xPosition,b.yPosition,b.width,b.height,5,Color.WHITE);}
            finally{me.youngjibbit.utils.render.Blur.endBlur(4,1);}
            }
        }
        me.youngjibbit.utils.render.GuiShaderBloom.beginBatch();
        try{for(GuiButton b:buttons)if(b.visible&&b.enabled&&b.styledSurface())UiEffects.drawGlow(b.xPosition,b.yPosition,b.width,b.height,5,Theme.getDefaultTheme().getPrimaryGradientRGB(),.35f,2,2);}
        finally{me.youngjibbit.utils.render.GuiShaderBloom.flushBatch(2,2);}
    }
    static void endSurfaces(){surfaceBatch=Math.max(0,surfaceBatch-1);}
    public void drawButton(Minecraft mc, int mouseX, int mouseY)
    {
        if (this.visible)
        {
            mc.getTextureManager().bindTexture(buttonTextures);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.blendFunc(770, 771);


            //this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 46 + i * 20, this.width / 2, this.height);
            //this.drawTexturedModalRect(this.xPosition + this.width / 2, this.yPosition, 200 - this.width / 2, 46 + i * 20, this.width / 2, this.height);

            updateHoverProgress();

            Theme theme = Theme.resolve(Theme.getDefaultTheme());
            int pulse = theme.getPulseGradientRGB(System.currentTimeMillis(), 2400.0f, false);
            if(surfaceBatch==0){
                UiEffects.drawBlurredRect(this.xPosition,this.yPosition,this.width,this.height,5,new Color(0,0,0,135),4,1);
                if(enabled)UiEffects.drawGlow(this.xPosition,this.yPosition,this.width,this.height,5,pulse,.25f+hoverProgress*.55f,2,2);
            }

            int fillAlpha = (int) (105 + hoverProgress * 65);
            int outlineAlpha = (int) (75 + hoverProgress * 105);
            if(!UiEffects.glass()||UiEffects.material())UIUtils.drawRound(this.xPosition, this.yPosition, this.width, this.height, 5, true, new Color(0, 0, 0, fillAlpha));
            UIUtils.drawRoundOutline(this.xPosition, this.yPosition, this.width, this.height, 5, 0.8f,
                    new Color(0, 0, 0, 1),
                    UiEffects.accent(theme.getPrimaryGradientRGB(), outlineAlpha / 255.0f));

            this.mouseDragged(mc,mouseX,mouseY);
            TTFFontRenderer buttonFont = Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 20");
            float textX = this.xPosition + (this.width - buttonFont.getWidth(this.displayString)) / 2.0f;
            float textY = this.yPosition + (this.height - buttonFont.getHeight(this.displayString)) / 2.0f + 1.0f;
            int textColor = this.enabled
                    ? Theme.interpolateRGB(Color.WHITE.getRGB(), theme.getPrimaryGradientRGB(), hoverProgress * 0.35f)
                    : new Color(150, 150, 150, 190).getRGB();
            buttonFont.drawString(this.displayString, textX, textY, textColor);
            //this.drawCenteredString(fontrenderer, this.displayString, this.xPosition + this.width / 2, (this.yPosition + (this.height - 8) / 2) - 1, j);
        }
    }

    private void updateHoverProgress() {
        long now = System.nanoTime();
        deltaTime = Math.min(0.05f, (now - lastTime) / 1_000_000_000.0f);
        lastTime = now;

        float target = hovered && enabled ? 1.0f : 0.0f;
        float speed = 12.0f;
        hoverProgress += (target - hoverProgress) * UiEffects.response(deltaTime,speed);
        hoverProgress = MathHelper.clamp_float(hoverProgress, 0.0f, 1.0f);
    }

    /**
     * Fired when the mouse button is dragged. Equivalent of MouseListener.mouseDragged(MouseEvent e).
     */
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY)
    {
    }

    /**
     * Fired when the mouse button is released. Equivalent of MouseListener.mouseReleased(MouseEvent e).
     */
    public void mouseReleased(int mouseX, int mouseY)
    {
    }

    /**
     * Returns true if the mouse has been pressed on this control. Equivalent of MouseListener.mousePressed(MouseEvent
     * e).
     */
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY)
    {
        return this.enabled && this.visible && mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
    }

    /**
     * Whether the mouse cursor is currently over the button.
     */
    public boolean isMouseOver()
    {
        return this.hovered;
    }

    public void drawButtonForegroundLayer(int mouseX, int mouseY)
    {
    }

    public void playPressSound(SoundHandler soundHandlerIn)
    {
        soundHandlerIn.playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    public int getButtonWidth()
    {
        return this.width;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }
}
