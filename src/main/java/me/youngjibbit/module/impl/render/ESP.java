package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.RenderUtil;
import me.youngjibbit.utils.RenderUtils;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.Wrapper;
import me.youngjibbit.utils.render.Blur;
import me.youngjibbit.utils.render.RenderUiUtils;
import me.youngjibbit.utils.render.Roundedutil;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.shader.KawaseBloom;
import me.youngjibbit.utils.render.shader.ShaderUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.nio.IntBuffer;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import java.util.List;

import java.nio.FloatBuffer;
import java.util.Arrays;

@ModuleInfo(name="2D ESP",description="Projected panels inherit Client Settings. World-box geometry and team colors remain distinct.",category=Category.Render)
public class ESP extends Module
{
    private static final IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
    private static final FloatBuffer modelview = GLAllocation.createDirectFloatBuffer(16);
    private static final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer vector = GLAllocation.createDirectFloatBuffer(4);
    private static final Frustum frustrum = new Frustum();
    private final ComboValue theme = new ComboValue("Theme", this, Theme.themes, 1);
    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Static", "Morden", "None"}, 1);
    private final CheckBoxValue blur = new CheckBoxValue("Blur", this, false);
    public CheckBoxValue healthBar = new CheckBoxValue("Health Bar", this, true);
    private final me.youngjibbit.utils.render.ArrayListSurface surfaces=new me.youngjibbit.utils.render.ArrayListSurface(this);
    private long startTime = System.currentTimeMillis();
    private float gradientOffset = 30.0f;
    private static final float GRADIENT_SCROLL_SPEED = 0.5f;
    Theme currentTheme = applyTheme();
    int[] outlineColors = getStaticOutlineGradientColors(currentTheme);

    public ESP()
    {

    }

    private void updateGradientOffset()
    {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        startTime = currentTime;

        float delta = elapsedTime / 1000f;
        gradientOffset += delta * GRADIENT_SCROLL_SPEED;

        gradientOffset %= 1.0f;
    }

    public Theme applyTheme()
    {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    @Override public void onDisable(){surfaces.close();super.onDisable();}

    @Subscribe
    private final Listener<Event3D> on3D = new Listener<>(e ->
    {
        if(mc.theWorld==null||mc.thePlayer==null)return;
        // Always scoped, including the no-FBO and inherited-scene fallbacks.
        try (me.youngjibbit.utils.render.RenderState ignored = new me.youngjibbit.utils.render.RenderState()) {
        FloatBuffer worldModel = org.lwjgl.BufferUtils.createFloatBuffer(16);
        FloatBuffer worldProjection = org.lwjgl.BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, worldModel);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, worldProjection);
        final float guiScale = new ScaledResolution(mc).getScaleFactor();
        currentTheme=surfaces.theme(Theme.getDefaultTheme());
        surfaces.withSceneIf(mode.getValue().equals("Static")&&surfaces.usesSurface(blur.getValue()),currentTheme,()->{
        me.youngjibbit.utils.render.GuiShaderBloom.beginBatch();
        try{
        for (Object object : Minecraft.getMinecraft().theWorld.loadedEntityList)
        {
            EntityLivingBase ent;
            Entity entity = (Entity) object;
            if (!(entity instanceof EntityLivingBase) || entity.isInvisible() || (ent = (EntityLivingBase) entity) == ESP.mc.thePlayer || !this.isInViewFrustrum(ent))
                continue;
            double posX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) ESP.mc.timer.renderPartialTicks;
            double posY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) ESP.mc.timer.renderPartialTicks;
            double posZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) ESP.mc.timer.renderPartialTicks;
            double finalWidth = (double) entity.width / 1.4;
            double finalHeight = (double) entity.height + (entity.isSneaking() ? -0.3 : 0.2);
            AxisAlignedBB axisAlignedBB = new AxisAlignedBB(posX - finalWidth - 0.1, posY - 0.1, posZ - finalWidth - 0.1, posX + finalWidth + 0.1, posY + finalHeight + 0.1, posZ + finalWidth + 0.1);
            List<Vector3d> vectorList = Arrays.asList(new Vector3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ), new Vector3d(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vector3d(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ), new Vector3d(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ));
            // Reuse the actual eye/shader camera rather than rebuilding eye zero.
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadMatrix(worldProjection);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadMatrix(worldModel);
            // The projection state stays unchanged while the eight box corners are projected.
            // Refresh it per entity because overlay rendering below changes the GL matrices.
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            Vector4d posVec = null;
            for (Vector3d vector : vectorList)
            {
                FloatBuffer otherVec=this.vector;otherVec.clear();
                if (GLU.gluProject((float) (vector.x - ESP.mc.getRenderManager().viewerPosX), (float) ((double) ((float) vector.y) - ESP.mc.getRenderManager().viewerPosY), (float) ((double) ((float) vector.z) - ESP.mc.getRenderManager().viewerPosZ), modelview, projection, viewport, otherVec))
                {
                    vector = new Vector3d(otherVec.get(0) / (float) guiScale, ((float) mc.displayHeight - otherVec.get(1)) / (float) guiScale, otherVec.get(2));
                }
                if (!(vector.z >= 0.0) || !(vector.z < 1.0)) continue;
                if (posVec == null)
                {
                    posVec = new Vector4d(vector.x, vector.y, vector.z, 0.0);
                }
                posVec.x = Math.min(vector.x, posVec.x);
                posVec.y = Math.min(vector.y, posVec.y);
                posVec.z = Math.max(vector.x, posVec.z);
                posVec.w = Math.max(vector.y, posVec.w);
            }
            ESP.mc.entityRenderer.setupOverlayRendering();
            if (posVec == null) continue;
            GL11.glPushMatrix();
            RenderHelper.enableStandardItemLighting();
            GlStateManager.enablePolygonOffset();
            GlStateManager.doPolygonOffset(1.0f, -1500000.0f);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.enableBlend();
            float x = (float) posVec.x;
            float w = (float) posVec.z - x;
            float y = (float) posVec.y;
            float h = (float) posVec.w - y;

            float clampedWidth = Math.min(w, 80);  // Limit width to 100
            float clampedHeight = Math.min(h, 160);

            int[] outlineColors = getStaticOutlineGradientColors(currentTheme);
            int teamColor1 = Color.green.getRGB();
            int healthBarColor = this.getHealthColor(ent);
            int color01 = outlineColors[0];
            int color02 = outlineColors[1];
            long cT = (long)(me.youngjibbit.utils.render.UiEffects.time()*1000);
            float cycleDuration = 1500.0f;
            float o = (cT % 3000L) / 3000.0f;
            int interpolatedColor = interpolateTwoColorsSmoothly(color01, color02, cT, cycleDuration);
            if (ent instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer) ent; // Initialize the player variable properly
                AxisAlignedBB axisBB = new AxisAlignedBB(posX, posY, posZ, player.renderOffsetX, player.renderOffsetY, posZ);

                // Team Check
                if (this.healthBar.getValue())
                {
                    UIUtils.drawGradientRound(x - 7.0f, y, 2.0f, (float) ((int) ent.getHealth()) / 2.0f, 7, Color.cyan, Color.green, Color.cyan, Color.green);
                    this.drawBar(x - 7.0f, y, 3.0f, h, (float) ((int) ent.getMaxHealth()) / 2.0f, (float) ((int) ent.getHealth()) / 2.0f, healthBarColor, new Color(1, true).getRGB());
                }
                switch (this.mode.getValue())
                {
                    case "Static":
                    {
                        //this.drawBorderedRect(x, y, w, h, 0.6, getColor(), 0);
                        //this.drawBorderedRect(x, y, w, h, 1.0, Theme.valueOf(theme.getValue()).ordinal(), 0);
                        //UIUtils.drawRoundOutline(x,y, w, h, 5, 1, new Color(0,0,0,0), Color.cyan);
                        if (surfaces.usesSurface(blur.getValue()))
                        {
                            me.youngjibbit.utils.render.UiEffects.drawBlurredRect(x,y,w,h,2,new Color(0,0,0,90),8,2);
                        }
                        if (Wrapper.mc.thePlayer.getTeam() != null && Wrapper.mc.thePlayer.getTeam().isSameTeam(player.getTeam()))
                        {
                            //RenderUtils.drawBoxAroundEntity(player, 4, 1, 1, Color.red.getRGB(), false);
                            //mc.fontRendererObj.drawString(player.getCustomNameTag(), x, y, -1);
                            Roundedutil.drawRoundedGradientOutlineCorner(x, y, x + w, y + h, 2, 3, teamColor1, teamColor1, teamColor1, teamColor1); // Skip rendering if the player is on the same team
                        } else
                        {
                            //RenderUtils.drawBoxAroundEntity(player, 4, 1, 1, Color.red.getRGB(), true);
                            Roundedutil.drawRoundedGradientOutlineCorner(x, y, x + w, y + h, 2, 3, outlineColors[0], outlineColors[1], outlineColors[0], outlineColors[1]);
                            //int length = player.getCustomNameTag().length();
                            //UIUtils.drawRound(x - 2, y - 15, length, 10, 5, new Color(0,0,0,122));
                            //Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 14").drawStringWithShadow(player.getCommandSenderName(), x, y - 15, -1);
                        }
                        break;
                    }
                    case "Morden":
                    {

                        // Calculate the distance between the player and the entity
                        double distance = Minecraft.getMinecraft().thePlayer.getDistance(ent.posX, ent.posY, ent.posZ);

                        // Define a minimum and maximum scale factor
                        float minScale = 0.8f; // Prevent it from becoming too small
                        float maxScale = 1.7f; // Prevent it from becoming excessively large

                        // Adjust scale based on distance
                        float scale = (float) (1.0 / Math.sqrt(distance));
                        scale = Math.max(minScale, Math.min(scale, maxScale)); // Clamp the scale

                        // Push matrix for transformations
                        GlStateManager.pushMatrix();

                        // Translate to the entity's position
                        GlStateManager.translate(x + w / 2.0f, y + h / 2.0f, 0);

                        // Apply scaling
                        GlStateManager.scale(scale, scale, scale);

                        // Render the bloom shadow with clamped dimensions
                        boolean sameTeam=Wrapper.mc.thePlayer.getTeam()!=null&&Wrapper.mc.thePlayer.getTeam().isSameTeam(player.getTeam());
                        me.youngjibbit.utils.render.GuiShaderBloom.queueWorldRect(-clampedWidth/2,-clampedHeight/2,clampedWidth,clampedHeight,5,sameTeam?teamColor1:interpolatedColor,sameTeam||!surfaces.inheritsEffects());
                        // Pop matrix to restore state
                        GlStateManager.popMatrix();
                        break;
                    }
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.disablePolygonOffset();
            GlStateManager.doPolygonOffset(1.0f, 1500000.0f);
            GlStateManager.popMatrix();
        }
        }finally{me.youngjibbit.utils.render.GuiShaderBloom.flushBatch(2,2);}
        });
        }
    });

    private void renderModernGlowEffect(EntityLivingBase entity, AxisAlignedBB axisAlignedBB) {
        // Bind the main framebuffer
        mc.getFramebuffer().bindFramebuffer(false);

        // Render the entity to a temporary framebuffer
        Framebuffer entityFramebuffer = RenderUtil.createFrameBuffer(null);
        entityFramebuffer.bindFramebuffer(false);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        // Render the entity
        mc.getRenderManager().renderEntityStatic(entity, mc.timer.renderPartialTicks, false);

        // Apply the KawaseBloom shader to the entity framebuffer
        KawaseBloom.renderBlur(entityFramebuffer.framebufferTexture, 5, 2); // Adjust iterations and offset as needed

        // Bind the main framebuffer again
        mc.getFramebuffer().bindFramebuffer(false);

        // Render the blurred entity texture
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderUtil.bindTexture(entityFramebuffer.framebufferTexture);
        ShaderUtil.drawQuads();
        GlStateManager.disableBlend();

        // Clean up the temporary framebuffer
        entityFramebuffer.deleteFramebuffer();
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

    private int[] getStaticOutlineGradientColors(Theme theme)
    {
        Theme resolvedTheme = Theme.resolve(theme);
        int color1 = resolvedTheme.getPrimaryGradientRGB();
        int color2 = resolvedTheme.getSecondaryGradientRGB();

        return new int[]{
                color1,
                color2,
                color1,
                color2
        };
    }

    private int getHealthColor(EntityLivingBase player) {
        return Color.HSBtoRGB(Math.max(0.0f, Math.min(player.getHealth(), player.getMaxHealth()) / player.getMaxHealth()) / 3.0f, 0.56f, 1.0f) | 0xFF000000;
    }

    public boolean isInViewFrustrum(Entity entity) {
        return this.isInViewFrustrum(entity.getEntityBoundingBox()) || entity.ignoreFrustumCheck;
    }

    public boolean isInViewFrustrum(AxisAlignedBB bb) {
        Entity current = Minecraft.getMinecraft().getRenderViewEntity();
        frustrum.setPosition(current.posX, current.posY, current.posZ);
        return frustrum.isBoundingBoxInFrustum(bb);
    }

    public static int getColor() {
        return -1;
    }

    public void drawBar(float x, float y, float width, float height, float max, float value, int color, int color2) {
        float f = (float)(color >> 24 & 0xFF) / 255.0f;
        float f1 = (float)(color >> 16 & 0xFF) / 255.0f;
        float f2 = (float)(color >> 8 & 0xFF) / 255.0f;
        float f3 = (float)(color & 0xFF) / 255.0f;
        float inc = height / max;
        GL11.glColor4f(f1, f2, f3, f);
        float incY = y + height - inc;
        int i = 0;
        while ((float)i < value) {
            this.drawBorderedRect(x + 0.25f, incY, width - 2.0f, inc, 0.1, new Color(color2, true).getRGB(), color);
            incY -= inc;
            ++i;
        }
    }

    public void drawBorderedRect(double x, double y, double width, double height, double lineSize, int borderColor, int color) {
        Gui.drawRect(x, y, x + width, y + height, color);
        Gui.drawRect(x, y, x + width, y + lineSize, borderColor);
        Gui.drawRect(x, y, x + lineSize, y + height, borderColor);
        Gui.drawRect(x + width, y, x + width - lineSize, y + height, borderColor);
        Gui.drawRect(x, y + height, x + width, y + height - lineSize, borderColor);
    }

    public FloatBuffer getVector() {
        return this.vector;
    }

    public CheckBoxValue getHealthBar() {
        return this.healthBar;
    }
}
