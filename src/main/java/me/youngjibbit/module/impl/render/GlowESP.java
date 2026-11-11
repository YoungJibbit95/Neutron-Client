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
import me.youngjibbit.utils.render.EffectMotion;
import me.youngjibbit.utils.render.ContourMaskRenderer;
import me.youngjibbit.utils.render.shader.KawasePass;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ActiveRenderInfo;
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
import java.nio.IntBuffer;
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

    private final CheckBoxValue players = new CheckBoxValue("Players", this, true);
    private final CheckBoxValue animals = new CheckBoxValue("Animals", this, true);
    private final CheckBoxValue mobs = new CheckBoxValue("Mobs", this, true);
    private final CheckBoxValue chests = new CheckBoxValue("Chests", this, true);
    private final SliderValue radius = new SliderValue("Radius", this, 5.0, 2, 20, true);
    private final SliderValue iterationsSetting = new SliderValue("Iterations", this, 4.0, 2, 6, true);
    private final SliderValue offsetSetting = new SliderValue("Offset", this, 4.0, 2, 10, true);
    private final SliderValue exposure = new SliderValue("Intensity", this,  3.0, 0.5, 3.5, false);
    // Preserve the registration indices of the existing filters and controls.
    private final ComboValue glowMode = new ComboValue("Glow Mode", this, new String[]{"Standard", "Flame", "Neon", "Lines"}, 0);
    private ShaderUtil chamsShader,outlineShader,glowShader,kawaseGlowShader,kawaseGlowShader2;
    private final ContourMaskRenderer contourRenderer = new ContourMaskRenderer();
    private final EffectMotion silhouetteMotion = new EffectMotion();
    private final java.util.Set<Object> motionObjects = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
    private final java.util.Set<Object> previousMotionObjects = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
    private final java.util.Set<Object> projectedMotionObjects = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
    private final float[] projectedMotionPoint = new float[2];
    private Object motionWorld;
    private double sampledMotionTime = Double.NaN;
    private String compositeMode = "";
    private boolean clearMaskOnNextFrame = true;

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
        String gm=glowMode.getValue();
        if(!gm.equals(compositeMode)){
            clearMotionState();
            if("Standard".equals(gm))contourRenderer.close();
            else {
                releaseGlowFramebuffers();
                if(outlineFrameBuffer!=null){outlineFrameBuffer.deleteFramebuffer();outlineFrameBuffer=null;}
            }
            compositeMode=gm;
        }
        // The contour renderer consumes the filled silhouette directly.
        if(!"Standard".equals(gm))return;
        if(outlineShader==null)outlineShader=new ShaderUtil("Flauxy/Shaders/outline.frag");
        if(kawaseGlow.getValue()){
            if(kawaseGlowShader==null)kawaseGlowShader=new ShaderUtil("kawaseDownBloom");
            if(kawaseGlowShader2==null)kawaseGlowShader2=new ShaderUtil("kawaseUpGlow");
        }else if(glowShader==null)glowShader=new ShaderUtil("glow");
    }
    private float fade(){return me.youngjibbit.utils.render.UiEffects.motion()&&fadeIn!=null?fadeIn.getOutput().floatValue():1;}

    @Subscribe
    public final Listener<WorldEvent> onWorldEvent = new Listener<>(e ->{
        entityColorMap.clear();entities.clear();
        clearMotionState();clearMaskOnNextFrame=true;
    });

    @Override
    public void onEnable() {
        super.onEnable();
        entityColorMap.clear();
        clearMotionState();clearMaskOnNextFrame=true;
        fadeIn = new DecelerateAnimation(250, 1);
    }

    @Override public void onDisable(){
        clearMotionState();clearMaskOnNextFrame=true;compositeMode="";
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){
            contourRenderer.close();
            java.util.Set<Framebuffer> owned=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Framebuffer,Boolean>());
            releaseGlowFramebuffers();
            if(framebuffer!=null)owned.add(framebuffer);if(outlineFrameBuffer!=null)owned.add(outlineFrameBuffer);
            for(Framebuffer buffer:owned)buffer.deleteFramebuffer();framebufferList.clear();framebuffer=outlineFrameBuffer=glowFrameBuffer=null;entities.clear();
            for(ShaderUtil shader:new ShaderUtil[]{chamsShader,outlineShader,glowShader,kawaseGlowShader,kawaseGlowShader2})if(shader!=null)shader.close();
            chamsShader=outlineShader=glowShader=kawaseGlowShader=kawaseGlowShader2=null;entityColorMap.clear();liveObjects.clear();
        }
        super.onDisable();
    }

    public void createFrameBuffers() {
        ensureShaders();
        framebuffer = RenderUtil.createFrameBuffer(framebuffer);
        if("Standard".equals(glowMode.getValue()))outlineFrameBuffer = RenderUtil.createFrameBuffer(outlineFrameBuffer);
        if(clearMaskOnNextFrame){framebuffer.framebufferClear();clearMaskOnNextFrame=false;}
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
            if("Flame".equals(glowMode.getValue()))motionObjects.add(e.getEntity());
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
            if("Flame".equals(glowMode.getValue()))motionObjects.add(e.getEntity());

            renderGlint = true;
            GlStateManager.disableCull();

            chamsShader.unload();


            mc.getFramebuffer().bindFramebuffer(false);
        }
        } finally { renderGlint=oldGlint; }
    });

    @Subscribe
    public final Listener<Event2D> on2D = new Listener<>(e -> {
        if(mc.theWorld==null||mc.thePlayer==null){clearMotionState();clearMaskOnNextFrame=true;return;}
        boolean oldGlint=renderGlint;
        try(me.youngjibbit.core.diagnostics.RenderTimings.Scope timing=me.youngjibbit.core.diagnostics.RenderTimings.INSTANCE.scope("GlowESP / composite");
            me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){

        currentTheme = Theme.resolve(Theme.getDefaultTheme());
        outlineColors = getStaticOutlineGradientColors(currentTheme);
        createFrameBuffers();
        collectEntities();

        ScaledResolution sr = new ScaledResolution(mc);
        String gm=glowMode.getValue();
        if(!"Standard".equals(gm)){
            mc.getFramebuffer().bindFramebuffer(false);
            try {
                if("Flame".equals(gm))sampleSilhouetteMotion();
                if(framebuffer!=null&&(chests.getValue()||!entities.isEmpty()))
                    contourRenderer.draw(currentTheme,gm,framebuffer.framebufferTexture,
                            mc.displayWidth,mc.displayHeight,sr.getScaleFactor(),radius.getValue().floatValue(),
                            exposure.getValue().floatValue()*fade(),"Sync".equals(colorMode.getValue()),
                            silhouetteMotion.flowX(),silhouetteMotion.flowY(),silhouetteMotion.energy());
            } finally {
                if(framebuffer!=null)framebuffer.framebufferClear();
                mc.getFramebuffer().bindFramebuffer(false);
            }
            return;
        }
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


            if (kawaseGlow.getValue()) {
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
        } finally { renderGlint=oldGlint;motionObjects.clear();projectedMotionObjects.clear(); }
    });

    private void clearMotionState() {
        silhouetteMotion.reset();motionObjects.clear();previousMotionObjects.clear();projectedMotionObjects.clear();
        sampledMotionTime=Double.NaN;motionWorld=null;
    }

    /**
     * The shared silhouette has one coherent wind. The identity set is the actual
     * mask capture, so adding/removing a figure cannot masquerade as camera motion.
     * Vanilla already cached the camera matrices: this does not query GL or redraw models.
     */
    private void sampleSilhouetteMotion() {
        double seconds=UiEffects.time();
        if(motionWorld!=mc.theWorld){silhouetteMotion.reset();previousMotionObjects.clear();motionWorld=mc.theWorld;sampledMotionTime=Double.NaN;}
        if(!UiEffects.motion()||!UiEffects.options().ambient){
            silhouetteMotion.reset();previousMotionObjects.clear();sampledMotionTime=seconds;return;
        }
        if(seconds==sampledMotionTime)return;
        sampledMotionTime=seconds;
        FloatBuffer model=ActiveRenderInfo.modelview(),projection=ActiveRenderInfo.projection();
        IntBuffer viewport=ActiveRenderInfo.viewport();
        projectedMotionObjects.clear();
        double sumX=0,sumY=0;
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        double partial=mc.timer.renderPartialTicks;
        for(Object object:motionObjects){
            double worldX,worldY,worldZ;
            if(object instanceof Entity){
                Entity entity=(Entity)object;
                if(entity.worldObj!=mc.theWorld)continue;
                worldX=entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial;
                worldY=entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial+entity.height*.5;
                worldZ=entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial;
            }else if(object instanceof TileEntityChest){
                TileEntityChest chest=(TileEntityChest)object;
                if(chest.getWorld()!=mc.theWorld||chest.getPos()==null)continue;
                worldX=chest.getPos().getX()+.5;worldY=chest.getPos().getY()+.5;worldZ=chest.getPos().getZ()+.5;
            }else continue;
            if(!projectMotionPoint(worldX-mc.getRenderManager().viewerPosX,
                    worldY-mc.getRenderManager().viewerPosY,worldZ-mc.getRenderManager().viewerPosZ,
                    model,projection,viewport))continue;
            float x=projectedMotionPoint[0],y=projectedMotionPoint[1];
            sumX+=x;sumY+=y;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
            projectedMotionObjects.add(object);
        }
        int count=projectedMotionObjects.size();
        if(count==0){silhouetteMotion.reset();previousMotionObjects.clear();return;}
        if(!previousMotionObjects.equals(projectedMotionObjects))silhouetteMotion.reset();
        previousMotionObjects.clear();previousMotionObjects.addAll(projectedMotionObjects);
        silhouetteMotion.sample(seconds,(float)(sumX/count),(float)(sumY/count),
                Math.max(48,maxX-minX+48),Math.max(80,maxY-minY+80),mc.displayWidth,mc.displayHeight,true);
    }

    private boolean projectMotionPoint(double x,double y,double z,FloatBuffer model,FloatBuffer projection,IntBuffer viewport) {
        double ex=model.get(0)*x+model.get(4)*y+model.get(8)*z+model.get(12);
        double ey=model.get(1)*x+model.get(5)*y+model.get(9)*z+model.get(13);
        double ez=model.get(2)*x+model.get(6)*y+model.get(10)*z+model.get(14);
        double ew=model.get(3)*x+model.get(7)*y+model.get(11)*z+model.get(15);
        double cw=projection.get(3)*ex+projection.get(7)*ey+projection.get(11)*ez+projection.get(15)*ew;
        if(!(cw>.00001)||viewport.get(2)<=0||viewport.get(3)<=0)return false;
        double px=(projection.get(0)*ex+projection.get(4)*ey+projection.get(8)*ez+projection.get(12)*ew)/cw;
        double py=(projection.get(1)*ex+projection.get(5)*ey+projection.get(9)*ez+projection.get(13)*ew)/cw;
        double pz=(projection.get(2)*ex+projection.get(6)*ey+projection.get(10)*ez+projection.get(14)*ew)/cw;
        if(!Double.isFinite(px)||!Double.isFinite(py)||!Double.isFinite(pz)||pz< -1||pz>1||Math.abs(px)>2||Math.abs(py)>2)return false;
        projectedMotionPoint[0]=(float)(viewport.get(0)+(px*.5+.5)*viewport.get(2));
        projectedMotionPoint[1]=(float)(mc.displayHeight-viewport.get(1)-(py*.5+.5)*viewport.get(3));
        return true;
    }

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
