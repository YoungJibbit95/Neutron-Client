package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.render.RenderChestEvent;
import me.youngjibbit.event.render.RenderModelEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.MathUtils;
import me.youngjibbit.utils.RenderUtil;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.animations.Animation;
import me.youngjibbit.utils.animations.impl.DecelerateAnimation;
import me.youngjibbit.utils.render.ColorUtil;
import me.youngjibbit.utils.render.ESPUtil;
import me.youngjibbit.utils.render.GLUtils;
import me.youngjibbit.utils.render.RenderUiUtils;
import me.youngjibbit.utils.render.ShaderUtil;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.shader.KawasePass;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.awt.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glUniform1;

@ModuleInfo(name = "GlowESP", category = Category.Render)
public class GlowESP extends Module
{

    private final CheckBoxValue kawaseGlow = new CheckBoxValue("Kawase Glow", this, false);
    private final ComboValue colorMode = new ComboValue("Color Mode", this, new String[]{"Sync", "Random", "Custom"}, 0);
    private final ComboValue glowMode = new ComboValue("Glow Mode", this, new String[]{"Standard", "Flame", "Neon", "Lines"}, 0);

    private final CheckBoxValue players = new CheckBoxValue("Players", this, true);
    private final CheckBoxValue animals = new CheckBoxValue("Animals", this, true);
    private final CheckBoxValue mobs = new CheckBoxValue("Mobs", this, true);
    private final CheckBoxValue chests = new CheckBoxValue("Chests", this, true);
    private final SliderValue radius = new SliderValue("Radius", this, 5.0, 2, 20, true);
    private final SliderValue iterationsSetting = new SliderValue("Iterations", this, 4.0, 2, 6, true);
    private final SliderValue offsetSetting = new SliderValue("Offset", this, 4.0, 2, 10, true);
    private final SliderValue exposure = new SliderValue("Intensity", this,  3.0, 0.5, 3.5, false);
    private ShaderUtil chamsShader,outlineShader,glowShader,kawaseGlowShader,kawaseGlowShader2,glowFlameShader,glowNeonShader,glowLinesShader;

    public Framebuffer framebuffer;
    public Framebuffer outlineFrameBuffer;

    public Framebuffer glowFrameBuffer;
    public static boolean renderGlint = false;

    private final java.util.Set<Entity> entities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Entity,Boolean>());
    private final Map<Object, Color> entityColorMap = new HashMap<>();

    public static Animation fadeIn;

    private int currentIterations;
    private int lastPrunedTick=Integer.MIN_VALUE;
    private final java.util.Set<Object> liveObjects=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());

    private final List<Framebuffer> framebufferList = new ArrayList<>();
    // Keep all 256 uploaded floats (including zero padding) identical to the previous path.
    private final FloatBuffer gaussianWeights = BufferUtils.createFloatBuffer(256);
    private float cachedGaussianRadius = Float.NaN;

    Theme currentTheme = applyTheme();
    int[] outlineColors = getStaticOutlineGradientColors(currentTheme);

    public Theme applyTheme() {
        return Theme.resolve(Pulsar.INSTANCE.getDefaultTheme());
    }

    public GlowESP() {

    }
    private void ensureShaders(){
        if(chamsShader==null)chamsShader=new ShaderUtil("chams");
        if(outlineShader==null)outlineShader=new ShaderUtil("Flauxy/Shaders/outline.frag");
        if(kawaseGlow.getValue()){
            if(kawaseGlowShader==null)kawaseGlowShader=new ShaderUtil("kawaseDownBloom");
            if(kawaseGlowShader2==null)kawaseGlowShader2=new ShaderUtil("kawaseUpGlow");
        }else if(glowShader==null)glowShader=new ShaderUtil("glow");
        String gm=glowMode.getValue();
        if(gm.equals("Flame")&&glowFlameShader==null)glowFlameShader=new ShaderUtil("pulsar/shader/esp/flame.frag");
        else if(gm.equals("Neon")&&glowNeonShader==null)glowNeonShader=new ShaderUtil("pulsar/shader/esp/neon.frag");
        else if(gm.equals("Lines")&&glowLinesShader==null)glowLinesShader=new ShaderUtil("pulsar/shader/esp/lines.frag");
    }
    private float fade(){return me.youngjibbit.utils.render.UiEffects.motion()&&fadeIn!=null?fadeIn.getOutput().floatValue():1;}

    @Subscribe
    public final Listener<WorldEvent> onWorldEvent = new Listener<>(e ->{
        entityColorMap.clear();entities.clear();
    });

    @Override
    public void onEnable() {
        super.onEnable();
        entityColorMap.clear();
        fadeIn = new DecelerateAnimation(250, 1);
    }

    @Override public void onDisable(){
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){
            java.util.Set<Framebuffer> owned=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Framebuffer,Boolean>());
            releaseGlowFramebuffers();
            if(framebuffer!=null)owned.add(framebuffer);if(outlineFrameBuffer!=null)owned.add(outlineFrameBuffer);
            for(Framebuffer buffer:owned)buffer.deleteFramebuffer();framebufferList.clear();framebuffer=outlineFrameBuffer=glowFrameBuffer=null;entities.clear();
            for(ShaderUtil shader:new ShaderUtil[]{chamsShader,outlineShader,glowShader,kawaseGlowShader,kawaseGlowShader2,glowFlameShader,glowNeonShader,glowLinesShader})if(shader!=null)shader.close();
            chamsShader=outlineShader=glowShader=kawaseGlowShader=kawaseGlowShader2=glowFlameShader=glowNeonShader=glowLinesShader=null;entityColorMap.clear();liveObjects.clear();
        }
        super.onDisable();
    }

    public void createFrameBuffers() {
        ensureShaders();
        framebuffer = RenderUtil.createFrameBuffer(framebuffer);
        outlineFrameBuffer = RenderUtil.createFrameBuffer(outlineFrameBuffer);
    }

    @Subscribe
    public final Listener<RenderChestEvent> onRenderChestEvent = new Listener<>(e-> {
        if(!chests.getValue()||framebuffer==null)return;
        boolean oldGlint=renderGlint;
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){

        if (chests.getValue() && framebuffer != null) {
            framebuffer.bindFramebuffer(false);
            chamsShader.init();
            chamsShader.setUniformi("textureIn", 0);
            Color color = getColor(e.getEntity());

            RenderUtil.resetColor();
            chamsShader.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1);
            e.drawChest();
            chamsShader.unload();

            this.mc.getFramebuffer().bindFramebuffer(false);
        }
        } finally { renderGlint=oldGlint; }
    });

    @Subscribe
    public final Listener<RenderModelEvent> onRenderModelEvent = new Listener<>(e -> {
        if(!e.isPost()||framebuffer==null||!entities.contains(e.getEntity()))return;
        boolean oldGlint=renderGlint;
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){

        if (e.isPost() && framebuffer != null) {
            if (!entities.contains(e.getEntity())) return;
            framebuffer.bindFramebuffer(false);
            chamsShader.init();
            chamsShader.setUniformi("textureIn", 0);
            Color color = getColor(e.getEntity());

            // TODO: Fix gradient
            chamsShader.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1);
            RenderUtil.resetColor();
            GlStateManager.enableCull(); // Ensure armor is visible
            renderGlint = false;

            e.drawModel();

            e.drawLayers();

            renderGlint = true;
            GlStateManager.disableCull();

            chamsShader.unload();


            mc.getFramebuffer().bindFramebuffer(false);
        }
        } finally { renderGlint=oldGlint; }
    });

    @Subscribe
    public final Listener<Event2D> on2D = new Listener<>(e -> {
        if(mc.theWorld==null||mc.thePlayer==null)return;
        boolean oldGlint=renderGlint;
        try(me.youngjibbit.core.diagnostics.RenderTimings.Scope timing=me.youngjibbit.core.diagnostics.RenderTimings.INSTANCE.scope("GlowESP / composite");
            me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){

        currentTheme = Theme.resolve(Theme.getDefaultTheme());
        outlineColors = getStaticOutlineGradientColors(currentTheme);
        createFrameBuffers();
        collectEntities();

        ScaledResolution sr = new ScaledResolution(mc);
        if (framebuffer != null && outlineFrameBuffer != null && (chests.getValue() || entities.size() > 0)) {
            RenderUtil.setAlphaLimit(0);
            GLUtils.startBlend();

           /*RenderUtil.bindTexture(framebuffer.framebufferTexture);
            ShaderUtil.drawQuads();
            framebuffer.framebufferClear();
            mc.getFramebuffer().bindFramebuffer(false);


            if(true) return;*/


            outlineFrameBuffer.framebufferClear();
            outlineFrameBuffer.bindFramebuffer(false);
            outlineShader.init();
            setupOutlineUniforms(0, 1);
            RenderUiUtils.bindTexture(framebuffer.framebufferTexture);
            ShaderUtil.drawQuads();
            outlineShader.init();
            setupOutlineUniforms(1, 0);
            RenderUiUtils.bindTexture(framebuffer.framebufferTexture);
            ShaderUtil.drawQuads();
            outlineShader.unload();
            outlineFrameBuffer.unbindFramebuffer();


            String gm = glowMode.getValue();
            if (gm.equals("Flame") || gm.equals("Neon") || gm.equals("Lines")) {
                ShaderUtil shader = getGlowShader(gm);
                if (shader != null) {
                    mc.getFramebuffer().bindFramebuffer(false);
                    shader.init();
                    try {
                        me.youngjibbit.utils.render.EspEffectStyle.upload(shader,currentTheme,gm,
                                "Sync".equals(colorMode.getValue()),exposure.getValue().floatValue()*fade());
                        shader.setUniformi("rectangleMask",0);
                        shader.setUniformi("textureIn",0);
                        GL13.glActiveTexture(GL13.GL_TEXTURE0);
                        RenderUtil.bindTexture(outlineFrameBuffer.framebufferTexture);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
                        ShaderUtil.drawQuads();
                    } finally { shader.unload(); }
                    framebuffer.framebufferClear();
                    mc.getFramebuffer().bindFramebuffer(false);
                    GlStateManager.bindTexture(0);
                }
            } else if (kawaseGlow.getValue()) {
                int offset = offsetSetting.getValue().intValue();
                int iterations = Math.max(2,Math.min(6,iterationsSetting.getValue().intValue()));

                if (framebufferList.isEmpty() || currentIterations != iterations || (framebufferList.get(0).framebufferWidth != mc.displayWidth || framebufferList.get(0).framebufferHeight != mc.displayHeight)) {
                    initFramebuffers(iterations);
                    currentIterations = iterations;
                }
                RenderUiUtils.setAlphaLimit(0);

                glBlendFunc(GL_ONE, GL_ONE);

                GL11.glClearColor(0, 0, 0, 0);
                KawasePass.draw(framebufferList.get(1), outlineFrameBuffer.framebufferTexture, kawaseGlowShader, offset);

                //Downsample
                for (int i = 1; i < iterations; i++) {
                    KawasePass.draw(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, kawaseGlowShader, offset);
                }

                //Upsample
                for (int i = iterations; i > 1; i--) {
                    KawasePass.draw(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseGlowShader2, offset);
                }

                Framebuffer lastBuffer = framebufferList.get(0);
                lastBuffer.framebufferClear();
                lastBuffer.bindFramebuffer(false);
                kawaseGlowShader2.init();
                kawaseGlowShader2.setUniformf("offset", offset, offset);
                kawaseGlowShader2.setUniformi("inTexture", 0);
                kawaseGlowShader2.setUniformi("check", 1);
                kawaseGlowShader2.setUniformf("lastPass", 1);
                kawaseGlowShader2.setUniformf("exposure", exposure.getValue().floatValue()*fade());
                kawaseGlowShader2.setUniformi("textureToCheck", 16);
                kawaseGlowShader2.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
                kawaseGlowShader2.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
                GL13.glActiveTexture(GL13.GL_TEXTURE16);
                RenderUtil.bindTexture(framebuffer.framebufferTexture);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                RenderUtil.bindTexture(framebufferList.get(1).framebufferTexture);

                ShaderUtil.drawQuads();
                kawaseGlowShader2.unload();

                GL11.glClearColor(0, 0, 0, 0);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                framebuffer.framebufferClear();
                RenderUtil.resetColor();
                mc.getFramebuffer().bindFramebuffer(true);
                RenderUtil.bindTexture(framebufferList.get(0).framebufferTexture);
                ShaderUtil.drawQuads();
                RenderUtil.setAlphaLimit(0);
                GlStateManager.bindTexture(0);
            } else {
                initGaussianFramebuffer();

                GL11.glClearColor(0, 0, 0, 0);
                glowFrameBuffer.framebufferClear();
                glowFrameBuffer.bindFramebuffer(false);
                glowShader.init();
                setupGlowUniforms(1f, 0);
                RenderUtil.bindTexture(outlineFrameBuffer.framebufferTexture);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                ShaderUtil.drawQuads();
                glowShader.unload();

                mc.getFramebuffer().bindFramebuffer(false);

                GL11.glClearColor(0, 0, 0, 0);
                glowShader.init();
                setupGlowUniforms(0, 1f);
                GL13.glActiveTexture(GL13.GL_TEXTURE16);
                RenderUtil.bindTexture(framebuffer.framebufferTexture);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                RenderUtil.bindTexture(glowFrameBuffer.framebufferTexture);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                ShaderUtil.drawQuads();
                glowShader.unload();

                framebuffer.framebufferClear();
                mc.getFramebuffer().bindFramebuffer(false);


            }
        }
        } finally { renderGlint=oldGlint; }
    });

    /** glowFrameBuffer is either Gaussian-owned or aliases Kawase element zero. */
    private void releaseGlowFramebuffers() {
        java.util.Set<Framebuffer> owned = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Framebuffer, Boolean>());
        owned.addAll(framebufferList);
        if (glowFrameBuffer != null) owned.add(glowFrameBuffer);
        for (Framebuffer buffer : owned) buffer.deleteFramebuffer();
        framebufferList.clear();
        glowFrameBuffer = null;
        currentIterations = 0;
    }

    private void initGaussianFramebuffer() {
        if (!framebufferList.isEmpty()) releaseGlowFramebuffers();
        glowFrameBuffer = RenderUtil.createFrameBuffer(glowFrameBuffer);
    }

    private void initFramebuffers(float iterations) {
        releaseGlowFramebuffers();

        //Have to make the framebuffer null so that it does not try to delete a framebuffer that has already been deleted
        framebufferList.add(glowFrameBuffer = RenderUtil.createFrameBuffer(null));


        for (int i = 1; i <= iterations; i++) {
            Framebuffer currentBuffer = new Framebuffer(Math.max(1,(int) (mc.displayWidth / Math.pow(2, i))), Math.max(1,(int) (mc.displayHeight / Math.pow(2, i))), true);
            currentBuffer.setFramebufferFilter(GL_LINEAR);

            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(0);

            framebufferList.add(currentBuffer);
        }
    }



    public void setupGlowUniforms(float dir1, float dir2) {
        glowShader.setUniformi("texture", 0);
        glowShader.setUniformi("textureToCheck", 16);
        glowShader.setUniformf("radius", radius.getValue().floatValue());
        glowShader.setUniformf("texelSize", 1.0f / mc.displayWidth, 1.0f / mc.displayHeight);
        glowShader.setUniformf("direction", dir1, dir2);
        glowShader.setUniformf("exposure", exposure.getValue().floatValue()*fade());
        glowShader.setUniformi("avoidTexture", 1);

        float currentRadius = radius.getValue().floatValue();
        if (Float.compare(cachedGaussianRadius, currentRadius) != 0) {
            gaussianWeights.clear();
            // The old freshly allocated direct buffer held zeroes after the active weights.
            for (int i = 0; i < gaussianWeights.capacity(); i++) gaussianWeights.put(0f);
            gaussianWeights.rewind();
            for (int i = 1; i <= currentRadius; i++) {
                gaussianWeights.put(MathUtils.calculateGaussianValue(i, currentRadius / 2));
            }
            cachedGaussianRadius = currentRadius;
        }
        gaussianWeights.rewind();
        glUniform1(glowShader.getUniform("weights"), gaussianWeights);
    }


    public void setupOutlineUniforms(float dir1, float dir2) {
        outlineShader.setUniformi("textureIn", 0);
        float iterations = kawaseGlow.getValue() ? (iterationsSetting.getValue().floatValue() * 2f) : radius.getValue().floatValue() / 1.5f;
        outlineShader.setUniformf("radius", iterations);
        outlineShader.setUniformf("texelSize", 1.0f / mc.displayWidth, 1.0f / mc.displayHeight);
        outlineShader.setUniformf("direction", dir1, dir2);
    }


    private ShaderUtil getGlowShader(String mode) {
        if (mode.equals("Flame")) {
            if (glowFlameShader == null) glowFlameShader = new ShaderUtil("pulsar/shader/esp/flame.frag");
            return glowFlameShader;
        } else if (mode.equals("Neon")) {
            if (glowNeonShader == null) glowNeonShader = new ShaderUtil("pulsar/shader/esp/neon.frag");
            return glowNeonShader;
        } else if (mode.equals("Lines")) {
            if (glowLinesShader == null) glowLinesShader = new ShaderUtil("pulsar/shader/esp/lines.frag");
            return glowLinesShader;
        }
        return null;
    }

    private Color getColor(Object entity) {
        Color color = Color.WHITE;
        Theme resolvedTheme = Theme.resolve(currentTheme);
        Color themeColor1 = resolvedTheme.getPrimaryGradientColor();
        Color themeColor2 = resolvedTheme.getSecondaryGradientColor();
        switch (colorMode.getValue()) {
            case "Custom":
                if (entity instanceof EntityPlayer) {
                    color = Color.red;
                }
                if (entity instanceof EntityMob) {
                    color = Color.white;
                }
                if (entity instanceof EntityAnimal) {
                    color = Color.yellow;
                }
                if (entity instanceof TileEntityChest) {
                    color = Color.orange;
                }
                break;
            case "Sync":

                if(entity instanceof EntityPlayer && mc.thePlayer.isOnSameTeam((EntityLivingBase) entity))
                {
                    color = Color.green;
                } else
                {
                    color = new Color(me.youngjibbit.utils.render.UiEffects.gradientColor(resolvedTheme,
                            me.youngjibbit.utils.render.UiEffects.Channel.GLOW,0,0),true);
                }

                break;
            case "Random":
                if (entityColorMap.containsKey(entity)) {
                    color = entityColorMap.get(entity);
                } else {
                    color = ColorUtil.getRandomColor();
                    entityColorMap.put(entity, color);
                }
                break;
        }

        if (entity instanceof EntityLivingBase) {
            EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
            if (entityLivingBase.hurtTime > 0) {
                //We use a the first part of the sine wave to make the color more red as the entity gets hurt and animate it back to normal
                color = ColorUtil.interpolateColorC(color, Color.red, (float) Math.sin(entityLivingBase.hurtTime * (18 * Math.PI / 180)));
            }
        }

        return color;
    }

    public void collectEntities() {
        entities.clear();
        // Dead/removed entities must not retain random-color entries for an entire session.
        if(!entityColorMap.isEmpty()&&mc.thePlayer.ticksExisted!=lastPrunedTick){
            lastPrunedTick=mc.thePlayer.ticksExisted;liveObjects.clear();
            liveObjects.addAll(mc.theWorld.getLoadedEntityList());liveObjects.addAll(mc.theWorld.loadedTileEntityList);
            entityColorMap.keySet().retainAll(liveObjects);liveObjects.clear();
        }
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if (!ESPUtil.isInView(entity)) continue;
            if (entity == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) continue;
            if (entity instanceof EntityAnimal && animals.getValue()) {
                entities.add(entity);
            }

            if (entity instanceof EntityPlayer && players.getValue()) {
                entities.add(entity);
            }

            if (entity instanceof EntityMob && mobs.getValue()) {
                entities.add(entity);
            }
        }
    }

    private int[] getStaticOutlineGradientColors(Theme theme)
    {
        Theme resolvedTheme = Theme.resolve(theme);
        return new int[]{resolvedTheme.getPrimaryGradientRGB(), resolvedTheme.getSecondaryGradientRGB()};
    }

}

