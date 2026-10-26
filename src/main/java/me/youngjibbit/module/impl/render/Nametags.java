package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.Wrapper;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ModuleInfo(name="Nametags",description="Projected panels follow Client Settings; team text keeps its semantic color.",category=Category.Render)
public class Nametags extends Module {
    private final me.youngjibbit.utils.render.ArrayListSurface surfaces=new me.youngjibbit.utils.render.ArrayListSurface(this);

    private static final IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
    private static final FloatBuffer modelview = GLAllocation.createDirectFloatBuffer(16);
    private static final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer vector = GLAllocation.createDirectFloatBuffer(4);
    private static Frustum frustrum;
    Theme currentTheme = applyTheme();

    public Theme applyTheme()
    {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    public Nametags() {
    }

    @Override
    public void onEnable()
    {
        super.onEnable();
    }

    @Override
    public void onDisable()
    {
        surfaces.close();super.onDisable();
    }

    @Subscribe
    private final Listener<Event2D> on2D = new Listener<>(e->
    {
        if(mc.theWorld==null||mc.thePlayer==null)return;
        java.util.List<EntityPlayer> players=new java.util.ArrayList<>();for(Entity candidate:mc.theWorld.loadedEntityList)if(replacementCandidate(candidate,mc.getRenderManager()))players.add((EntityPlayer)candidate);if(players.isEmpty())return;
        currentTheme=Theme.getDefaultTheme();
        me.youngjibbit.utils.render.GuiShaderBloom.beginBatch();try{surfaces.withScene(currentTheme,()->{
        for (Object object : players)
        {
            EntityLivingBase ent;
            Entity entity = (Entity) object;
            if (!replacementCandidate(entity,mc.getRenderManager()))
                continue;
            ent = (EntityLivingBase) entity;
            ESP.mc.entityRenderer.setupCameraTransform(ESP.mc.timer.renderPartialTicks, 0);
            Vector4d posVec = projectLabel(entity);
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
            float x = (float) posVec.x - 8;
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
            long cT = (long)(UiEffects.time()*1000);
            float cycleDuration = 1500.0f;
            float o = (cT % 3000L) / 3000.0f;
            int interpolatedColor = interpolateTwoColorsSmoothly(color01, color02, cT, cycleDuration);
            if (ent instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer) ent;
                String health = wrappedHealth(player) + "";
                String hp = String.valueOf(player.getHealth());

                int length = (int) Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Medium 15").getWidth(player.getCommandSenderName() + " | " + health) + 6;
                int tagColor = mc.thePlayer.isOnSameTeam(player) ? Color.green.getRGB() : interpolatedColor;
                UiEffects.drawGlow(x - 8, y - 17, length + 2, 11, 3, tagColor, 0.65f, 2, 1);
                UiEffects.drawPanelFill(x-7,y-16,length+2,11,3,new Color(0,0,0,92),5);
                UIUtils.drawRoundOutline(x - 7, y - 16, length + 2, 11, 3, 0.45f, new Color(0, 0, 0, 1), UiEffects.color(tagColor, 0.62f));

                if(Wrapper.mc.thePlayer.getTeam() != null && player.getTeam() != null && Wrapper.mc.thePlayer.getTeam().isSameTeam(player.getTeam()))
                {
                    Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Medium 15").drawStringWithShadow(player.getCommandSenderName() + " | " + health, x - 5, y - 14, Color.green.getRGB());
                } else {
                    Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Medium 15").drawStringWithShadow(player.getCommandSenderName() + " | " + health, x - 5, y - 14, -1);
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.disablePolygonOffset();
            GlStateManager.doPolygonOffset(1.0f, 1500000.0f);
            GlStateManager.popMatrix();
        }
        });}finally{me.youngjibbit.utils.render.GuiShaderBloom.flushBatch(2,2);}
    });

    /** Reads current world-camera matrices after the entity model matrix has been popped. */
    protected Vector4d projectLabel(Entity entity) {
        double partialTicks = ESP.mc.timer.renderPartialTicks;
        double posX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double posY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double posZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        double finalWidth = (double) entity.width / 1.4;
        double finalHeight = (double) entity.height + (entity.isSneaking() ? -0.3 : 0.2);
        AxisAlignedBB box = new AxisAlignedBB(
                posX - finalWidth - 0.1, posY - 0.1, posZ - finalWidth - 0.1,
                posX + finalWidth + 0.1, posY + finalHeight + 0.1, posZ + finalWidth + 0.1);
        List<Vector3d> corners = Arrays.asList(
                new Vector3d(box.minX, box.minY, box.minZ),
                new Vector3d(box.minX, box.maxY, box.minZ),
                new Vector3d(box.maxX, box.minY, box.minZ),
                new Vector3d(box.maxX, box.maxY, box.minZ),
                new Vector3d(box.minX, box.minY, box.maxZ),
                new Vector3d(box.minX, box.maxY, box.maxZ),
                new Vector3d(box.maxX, box.minY, box.maxZ),
                new Vector3d(box.maxX, box.maxY, box.maxZ));
        Vector4d bounds = null;
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int scale = new ScaledResolution(mc).getScaleFactor();
        RenderManager manager = ESP.mc.getRenderManager();
        for (Vector3d corner : corners) {
            vector.clear();
            boolean projected = GLU.gluProject(
                    (float) (corner.x - manager.viewerPosX),
                    (float) ((double) ((float) corner.y) - manager.viewerPosY),
                    (float) ((double) ((float) corner.z) - manager.viewerPosZ),
                    modelview, projection, viewport, vector);
            if (!projected) continue;
            double depth = vector.get(2);
            if (!(depth >= 0.0) || !(depth < 1.0)) continue;
            double x = vector.get(0) / (float) scale;
            double y = ((float) mc.displayHeight - vector.get(1)) / (float) scale;
            bounds = includeProjected(bounds, x, y);
        }
        return bounds;
    }

    static Vector4d includeProjected(Vector4d bounds, double x, double y) {
        if (bounds == null) return new Vector4d(x, y, x, y);
        bounds.x = Math.min(x, bounds.x);
        bounds.y = Math.min(y, bounds.y);
        bounds.z = Math.max(x, bounds.z);
        bounds.w = Math.max(y, bounds.w);
        return bounds;
    }

    /** Same entity/context contract for the overlay and vanilla fallback. */
    public boolean replacementCandidate(Entity entity, RenderManager manager) {
        if (!isToggled() || mc == null || mc.theWorld == null || mc.thePlayer == null
                || mc.gameSettings == null || mc.gameSettings.hideGUI
                || mc.entityRenderer == null || mc.timer == null
                || manager == null || manager.livingPlayer == null || mc.getRenderViewEntity() == null
                || !(entity instanceof EntityPlayer) || entity == mc.thePlayer || entity == manager.livingPlayer
                || entity.worldObj != mc.theWorld || entity.isInvisible() || entity.riddenByEntity != null) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        return RendererLivingEntity.canRenderVanillaName(living, mc.thePlayer, manager.livingPlayer, true)
                && isInViewFrustrum(entity);
    }

    /** Call from label methods while the world-camera matrices are active; reads GL state only. */
    public static boolean replacesVanillaLabel(Entity entity, RenderManager manager) {
        if (Pulsar.INSTANCE.getMm() == null) return false;
        Nametags module = (Nametags) Pulsar.INSTANCE.getMm().getModule(Nametags.class);
        return module != null && module.replacementCandidate(entity, manager)
                && module.projectLabel(entity) != null;
    }

    public static boolean replacesVanillaLabel(Entity entity, String text, RenderManager manager) {
        // The below-name scoreboard is a distinct label, not replaced by the name/health panel.
        return entity instanceof EntityPlayer
                && (entity.getDisplayName().getFormattedText().equals(text)
                || entity.getDisplayName().getUnformattedText().equals(text))
                && replacesVanillaLabel(entity, manager);
    }

    public static float wrappedHealth(EntityLivingBase entity){
        return Math.round(entity.getHealth() * 100.0f) / 100.0f;
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
        if (frustrum == null) frustrum = new Frustum();
        frustrum.setPosition(current.posX, current.posY, current.posZ);
        return frustrum.isBoundingBoxInFrustum(bb);
    }
}
