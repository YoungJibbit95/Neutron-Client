package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.ShaderUtil;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.EffectMotion;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.gui.LiquidGlass;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;

@ModuleInfo(name="Nametags",description="Projected panels follow Client Settings; team text keeps its semantic color.",category=Category.Render)
public class Nametags extends Module {
    private final me.youngjibbit.utils.render.ArrayListSurface surfaces=new me.youngjibbit.utils.render.ArrayListSurface(this);

    private static final IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
    private static final FloatBuffer modelview = GLAllocation.createDirectFloatBuffer(16);
    private static final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
    private final FloatBuffer vector = GLAllocation.createDirectFloatBuffer(4);
    private static Frustum frustrum;
    Theme currentTheme = applyTheme();

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Static", "Flame", "Neon", "Lines"}, 0);
    // Append optional presentation values; existing config identifiers keep their order.
    private final CheckBoxValue playerHead = new CheckBoxValue("Player Head", this, false);
    private final CheckBoxValue distance = new CheckBoxValue("Distance", this, false);
    private ShaderUtil nametagFlameShader, nametagNeonShader, nametagLinesShader;
    private final java.util.Map<Entity, EffectMotion> flameMotions = new java.util.IdentityHashMap<>();
    private final java.util.Set<Entity> flameMotionSeen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Entity, Boolean>());
    private final java.util.Map<Entity, HudSpring> healthMotions = new java.util.IdentityHashMap<>();
    private final java.util.Set<Entity> healthMotionSeen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Entity, Boolean>());
    private long lastHealthFrame;
    private Object motionWorld;

    public Theme applyTheme()
    {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    public Nametags() {
    }

    @Override
    public void onEnable()
    {
        resetMotion();
        super.onEnable();
    }

    @Override
    public void onDisable()
    {
        resetMotion();
        try(me.youngjibbit.utils.render.RenderState ignored = new me.youngjibbit.utils.render.RenderState()){
            for(ShaderUtil s:new ShaderUtil[]{nametagFlameShader,nametagNeonShader,nametagLinesShader})if(s!=null)s.close();
        }
        nametagFlameShader=nametagNeonShader=nametagLinesShader=null;
        surfaces.close();super.onDisable();
    }

    @Subscribe
    private final Listener<WorldEvent> onWorldEvent = new Listener<>(event -> {resetMotion();surfaces.close();});

    private void clearFlameMotion() {
        flameMotions.clear(); flameMotionSeen.clear();
    }
    private void clearHealthMotion() {
        healthMotions.clear(); healthMotionSeen.clear(); lastHealthFrame=0;
    }
    private void resetMotion() {
        clearFlameMotion(); clearHealthMotion(); motionWorld = null;
    }

    @Subscribe
    private final Listener<Event2D> on2D = new Listener<>(e->
    {
        if(mc.theWorld==null||mc.thePlayer==null){resetMotion();surfaces.close();return;}
        if(motionWorld!=mc.theWorld){resetMotion();surfaces.close();motionWorld=mc.theWorld;}
        if(!"Flame".equals(mode.getValue()))clearFlameMotion();
        flameMotionSeen.clear();healthMotionSeen.clear();
        ClientSettings motion=ClientSettings.current();
        boolean animated=motion==null||motion.animations.getValue();
        boolean reduced=motion!=null&&motion.reducedMotion.getValue();
        String intensity=motion==null?"Balanced":motion.motionIntensity.getValue();
        long healthNow=System.nanoTime();
        float healthDt=lastHealthFrame==0||healthNow<=lastHealthFrame?0:
                Math.min(.05f,(healthNow-lastHealthFrame)/1_000_000_000f);
        lastHealthFrame=healthNow;
        try(me.youngjibbit.core.diagnostics.RenderTimings.Scope timing=me.youngjibbit.core.diagnostics.RenderTimings.INSTANCE.scope("Nametags / labels")) {
        java.util.List<EntityPlayer> players=new java.util.ArrayList<>();for(Entity candidate:mc.theWorld.loadedEntityList)if(replacementCandidate(candidate,mc.getRenderManager()))players.add((EntityPlayer)candidate);if(players.isEmpty())return;
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()) {
        currentTheme=surfaces.theme(Theme.getDefaultTheme());
        me.youngjibbit.utils.render.GuiShaderBloom.beginBatch();try{surfaces.withScene(currentTheme,true,true,5,policy->{
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
            try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            int healthBarColor = this.getHealthColor(ent);
            long cT = (long)(UiEffects.time()*1000);
            int interpolatedColor = interpolateTwoColorsSmoothly(currentTheme.getPrimaryGradientRGB(),
                    currentTheme.getSecondaryGradientRGB(), cT, 1500.0f);
            if (ent instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer) ent;
                String health = wrappedHealth(player) + "";
                float healthTarget = healthRatio(player.getHealth(), player.getMaxHealth());
                HudSpring spring = healthMotions.get(player);
                if (spring == null) {
                    spring = new HudSpring();
                    spring.snap(healthTarget);
                    healthMotions.put(player, spring);
                }
                healthMotionSeen.add(player);
                float shownHealth = Math.max(0, Math.min(1,
                        spring.step(healthTarget, healthDt, animated, reduced, intensity)));

                me.youngjibbit.utils.font.TTFFontRenderer font = Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Medium 15");
                String name = player.getCommandSenderName() + " | ";
                float nameWidth = font.getWidth(name);
                // Fixed health/distance columns keep damage and movement from shifting the label.
                float healthWidth = Math.max(font.getWidth("00.00"), font.getWidth(health));
                float headWidth = playerHead.getValue() && player instanceof net.minecraft.client.entity.AbstractClientPlayer ? 12 : 0;
                float distanceWidth = distance.getValue() ? font.getWidth("999 m") + 5 : 0;
                float tagWidth = 6 + headWidth + nameWidth + healthWidth + distanceWidth;
                float tagHeight = 15;
                float tagX = (float)((posVec.x + posVec.z) * .5) - tagWidth * .5f;
                float tagY = (float)posVec.y - tagHeight - 3;
                float tagRadius = UiEffects.radius(LiquidGlass.radius(3, tagWidth, tagHeight),tagWidth,tagHeight);
                int tagColor = mc.thePlayer.isOnSameTeam(player) ? Color.green.getRGB() : interpolatedColor;
                String nm = mode.getValue();
                if (nm.equals("Flame") || nm.equals("Neon") || nm.equals("Lines")) {
                    renderShaderNametag(entity, tagX, tagY, tagWidth, tagHeight, currentTheme, tagColor,policy.opacity);
                } else {
                    UiEffects.drawGlow(tagX, tagY, tagWidth, tagHeight, Math.round(tagRadius), tagColor, 0.65f, 2, 1);
                    UiEffects.drawPanelFill(tagX, tagY, tagWidth, tagHeight, tagRadius,
                            LiquidGlass.fill(currentTheme, Math.round(150*policy.opacity)), 5);
                    LiquidGlass.highlight(tagX, tagY, tagWidth, tagHeight, tagRadius,policy.opacity);
                    LiquidGlass.border(tagX,tagY,tagWidth,tagHeight,tagRadius,currentTheme,false,policy.opacity);
                }
                // A slim semantic rail makes health changes readable without relying on color alone.
                float railX = tagX + 3, railWidth = tagWidth - 6, railY = tagY + tagHeight - 3;
                UIUtils.drawDecorationRound(railX, railY, railWidth, 1.4f, .7f,
                        new Color(255, 255, 255, 28));
                if (railWidth * shownHealth > .05f)
                    UIUtils.drawDecorationRound(railX, railY, railWidth * shownHealth, 1.4f,
                            Math.min(.7f,railWidth*shownHealth*.5f),
                            UiEffects.color(healthBarColor, .92f));
                if (headWidth > 0) {
                    net.minecraft.client.entity.AbstractClientPlayer clientPlayer = (net.minecraft.client.entity.AbstractClientPlayer)player;
                    CompactHudVisuals.skin(clientPlayer.getLocationSkin(),tagX+3,tagY+1,10,3,1);
                }
                float textX = tagX + 3 + headWidth;
                int nameColor = mc.thePlayer.isOnSameTeam(player) ? Color.green.getRGB() : -1;
                font.drawStringWithShadow(name, textX, tagY+2, nameColor);
                font.drawStringWithShadow(health, textX+nameWidth, tagY+2, nameColor);
                if (distance.getValue()) {
                    float meters=mc.thePlayer.getDistanceToEntity(player);
                    String range=Float.isFinite(meters)&&meters>=0?Math.min(999,Math.round(meters))+" m":"-- m";
                    font.drawStringWithShadow(range,textX+nameWidth+healthWidth+5,tagY+2,0xffb9c8d9);
                }
            }
            } finally { GlStateManager.popMatrix(); }
        }
        });}finally{me.youngjibbit.utils.render.GuiShaderBloom.flushBatch(2,2);}
        }
        } finally { flameMotions.keySet().retainAll(flameMotionSeen);
            healthMotions.keySet().retainAll(healthMotionSeen); }
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
        float health=entity.getHealth();
        return Float.isFinite(health)?Math.round(Math.max(0,health) * 100.0f) / 100.0f:0;
    }

    static float healthRatio(float health,float maximum) {
        return Float.isFinite(health)&&Float.isFinite(maximum)&&maximum>0
                ?Math.max(0,Math.min(1,health/maximum)):0;
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

    private int getHealthColor(EntityLivingBase player) {
        return Color.HSBtoRGB(healthRatio(player.getHealth(),player.getMaxHealth()) / 3.0f, 0.56f, 1.0f) | 0xFF000000;
    }

    private ShaderUtil getNametagShader(String mode) {
        if (mode.equals("Flame")) {
            if (nametagFlameShader == null) nametagFlameShader = new ShaderUtil("pulsar/shader/esp/flame.frag");
            return nametagFlameShader;
        } else if (mode.equals("Neon")) {
            if (nametagNeonShader == null) nametagNeonShader = new ShaderUtil("pulsar/shader/esp/neon.frag");
            return nametagNeonShader;
        } else if (mode.equals("Lines")) {
            if (nametagLinesShader == null) nametagLinesShader = new ShaderUtil("pulsar/shader/esp/lines.frag");
            return nametagLinesShader;
        }
        return null;
    }

    private void renderShaderNametag(Entity entity, float x, float y, float w, float h, Theme theme, int tagColor,float opacity) {
        ShaderUtil shader=getNametagShader(mode.getValue());
        if(shader==null)return;
        UiEffects.drawPanelFill(x,y,w,h,3,LiquidGlass.fill(theme,Math.round(150*opacity)),5);
        boolean teammate=entity instanceof EntityPlayer && mc.thePlayer.isOnSameTeam((EntityPlayer)entity);
        int primary=teammate?tagColor:theme.getPrimaryGradientRGB();
        int secondary=teammate?tagColor:theme.getSecondaryGradientRGB();
        float flowX=0,flowY=0,energy=0;
        if("Flame".equals(mode.getValue())){
            EffectMotion motion=flameMotions.get(entity);
            if(motion==null){motion=new EffectMotion();flameMotions.put(entity,motion);}
            flameMotionSeen.add(entity);
            float scale=me.youngjibbit.utils.render.GuiResolution.current().getScaleFactor();
            motion.sample(UiEffects.time(),(x+w*.5f)*scale,(y+h*.5f)*scale,w*scale,h*scale,
                    mc.displayWidth,mc.displayHeight,UiEffects.motion()&&UiEffects.options().ambient);
            flowX=motion.flowX();flowY=motion.flowY();energy=motion.energy();
        }
        me.youngjibbit.utils.render.EspEffectStyle.drawRectangle(shader,theme,mode.getValue(),x,y,w,h,primary,secondary,flowX,flowY,energy);
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

