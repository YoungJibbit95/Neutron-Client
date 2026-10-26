package me.youngjibbit.utils.render.shader;

import me.youngjibbit.utils.RenderUtil;
import me.youngjibbit.utils.render.GLUtils;
import me.youngjibbit.utils.render.RenderUiUtils;
import me.youngjibbit.utils.render.Roundedutil;
import me.youngjibbit.utils.render.UIUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINEAR;

public class KawaseBloom {

    public static ShaderUtil kawaseDown = new ShaderUtil("kawaseDownBloom");
    public static ShaderUtil kawaseUp = new ShaderUtil("kawaseUpBloom");

    public static Framebuffer framebuffer;
    private static Framebuffer bloomFramebuffer = null;

    // Positive int-sized textures need at most 31 halvings. Keep the pool bounded.
    private static final int MAX_ITERATIONS = Integer.SIZE - 1;

    private static final List<Framebuffer> framebufferList = new ArrayList<>();

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static void ensureFramebuffers(int iterations) {
        if(kawaseDown==null)kawaseDown=new ShaderUtil("kawaseDownBloom");if(kawaseUp==null)kawaseUp=new ShaderUtil("kawaseUpBloom");
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Bloom iterations must be between 1 and " + MAX_ITERATIONS);
        }

        if (framebuffer == null || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            for (Framebuffer buffer : framebufferList) {
                buffer.deleteFramebuffer();
            }
            framebufferList.clear();
            framebuffer = null;
            framebufferList.add(framebuffer = RenderUtil.createFrameBuffer(null));
        }

        // Each pass clears its output, so smaller requests can reuse this pyramid's prefix.
        for (int i = framebufferList.size(); i <= iterations; i++) {
            Framebuffer currentBuffer = new Framebuffer(Math.max(1, mc.displayWidth >> i), Math.max(1, mc.displayHeight >> i), true);
            currentBuffer.setFramebufferFilter(GL_LINEAR);

            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(0);

            framebufferList.add(currentBuffer);
        }
    }

    public static Framebuffer drawArraylistFramebuffer(List<float[]> rects) {
        if(me.youngjibbit.utils.render.RenderDiagnostics.enabled)me.youngjibbit.utils.render.RenderDiagnostics.maskPasses++;
        ensureBloomFramebuffer();

        bloomFramebuffer.framebufferClear();
        bloomFramebuffer.bindFramebuffer(false);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        RenderUiUtils.prepare2D3();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        for (float[] rect : rects) {
            if(rect.length>6 && rect[6]>0){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor((int)rect[7],(int)rect[8],(int)rect[9],(int)rect[10]);}
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            float x = rect[0];
            float y = rect[1];
            float width = rect[2];
            float height = rect[3];
            int radius = (int) rect[4];
            int color = Float.floatToRawIntBits(rect[5]);

            Color c = new Color(color, true);
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

            if(rect.length>12&&rect[12]>0)UIUtils.drawWorldMaskRound(x,y,width,height,radius,c);else UIUtils.drawRound(x, y, width, height, radius, c);
        }

        RenderUiUtils.release2D3();
        GL11.glPopMatrix();
        GL11.glPopAttrib();

        bloomFramebuffer.unbindFramebuffer();
        return bloomFramebuffer;
    }

    public static void renderBlur(int framebufferTexture, int iterations, int offset) {
        renderBlur(framebufferTexture,iterations,offset,true);
    }
    public static void renderBlur(int framebufferTexture,int iterations,int offset,boolean themed){
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        ensureFramebuffers(iterations);

        RenderUtil.setAlphaLimit(0);
        // Filter passes replace their targets; never accumulate straight-alpha RGB.
        GlStateManager.disableBlend();GL11.glDisable(GL11.GL_BLEND);
        GL11.glClearColor(0, 0, 0, 0);
        renderFBO(framebufferList.get(1), framebufferTexture, kawaseDown, offset);

        //Downsample
        for (int i = 1; i < iterations; i++) {
            renderFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, kawaseDown, offset);
        }

        //Upsample
        for (int i = iterations; i > 1; i--) {
            renderFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseUp, offset);
        }

        Framebuffer lastBuffer = framebufferList.get(0);
        lastBuffer.framebufferClear();
        lastBuffer.bindFramebuffer(false);
        if(me.youngjibbit.utils.render.RenderDiagnostics.enabled)me.youngjibbit.utils.render.RenderDiagnostics.blurPasses++;
        kawaseUp.init();
        me.youngjibbit.utils.Theme theme=me.youngjibbit.utils.render.UiEffects.theme(me.youngjibbit.utils.Theme.getDefaultTheme());
        me.youngjibbit.utils.render.UiEffects.upload(kawaseUp,theme);if(!themed)kawaseUp.setUniformi("uiEnabled",0);
        java.awt.Color first=theme.getPrimaryGradientColor(),second=theme.getSecondaryGradientColor();
        kawaseUp.setUniformf("uiFirst",first.getRed()/255f,first.getGreen()/255f,first.getBlue()/255f,first.getAlpha()/255f);kawaseUp.setUniformf("uiSecond",second.getRed()/255f,second.getGreen()/255f,second.getBlue()/255f,second.getAlpha()/255f);kawaseUp.setUniformf("uiIntensity",me.youngjibbit.utils.render.UiEffects.glowAlpha(1));
        kawaseUp.setUniformf("offset", offset, offset);
        kawaseUp.setUniformi("inTexture", 0);
        kawaseUp.setUniformi("check", 1);
        kawaseUp.setUniformi("textureToCheck", 16);
        kawaseUp.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
        kawaseUp.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE16);
        RenderUtil.bindTexture(framebufferTexture);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(framebufferList.get(1).framebufferTexture);
        ShaderUtil.drawQuads();
        kawaseUp.unload();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE16);
        // RenderUtil binds directly: clear the actual binding as well as the state cache.
        RenderUtil.bindTexture(0);
        GlStateManager.bindTexture(0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);


        GlStateManager.clearColor(0, 0, 0, 0);
        mc.getFramebuffer().bindFramebuffer(true);
        if(me.youngjibbit.utils.render.RenderDiagnostics.enabled)me.youngjibbit.utils.render.RenderDiagnostics.composites++;
        RenderUtil.bindTexture(framebufferList.get(0).framebufferTexture);
        RenderUtil.setAlphaLimit(0);
        // Legacy mask capture mixes raw GL and cached state. Establish both here:
        // the final texture contains straight-alpha RGB, so disabled blending would
        // paint even its nearly transparent halo as a solid colored blob.
        GLUtils.startBlend();GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        ShaderUtil.drawQuads();
        RenderUtil.bindTexture(0);
        GlStateManager.bindTexture(0);
        RenderUtil.setAlphaLimit(0);
        GLUtils.startBlend();
    }

    private static void renderFBO(Framebuffer framebuffer, int framebufferTexture, ShaderUtil shader, float offset) {
        if(me.youngjibbit.utils.render.RenderDiagnostics.enabled)me.youngjibbit.utils.render.RenderDiagnostics.blurPasses++;
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(false);
        shader.init();shader.setUniformi("uiEnabled",0);
        RenderUtil.bindTexture(framebufferTexture);
        shader.setUniformf("offset", offset, offset);
        shader.setUniformi("inTexture", 0);
        shader.setUniformi("check", 0);
        shader.setUniformf("halfpixel", 1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight);
        shader.setUniformf("iResolution", framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        ShaderUtil.drawQuads();
        shader.unload();
    }

    public static Framebuffer drawRectToFramebuffer(float x, float y, float width, float height, int radius, int color) {
        if(me.youngjibbit.utils.render.RenderDiagnostics.enabled)me.youngjibbit.utils.render.RenderDiagnostics.maskPasses++;
        ensureBloomFramebuffer(); // Ensure it's created and matches screen size

        bloomFramebuffer.framebufferClear();
        bloomFramebuffer.bindFramebuffer(false);

        // Setup OpenGL state for 2D rendering
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        RenderUiUtils.prepare2D3();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        // Set draw color
        Color c = new Color(color, true);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

        // Draw the rounded rectangle into the framebuffer
        UIUtils.drawRound(x, y, width, height, radius, c);

        // Restore GL state
        RenderUiUtils.release2D3();
        GL11.glPopMatrix();
        GL11.glPopAttrib();

        bloomFramebuffer.unbindFramebuffer();
        return bloomFramebuffer;
    }

    public static Framebuffer drawSelectRoundedBloom(float x, float y, float width, float height, int rectRadiusTopLeft, int rectRadiusBottomLeft, int rectRadiusBottomRight, int rectRadiusTopRight, int radius, int color) {
        if(me.youngjibbit.utils.render.RenderDiagnostics.enabled)me.youngjibbit.utils.render.RenderDiagnostics.maskPasses++;
        ensureBloomFramebuffer(); // Ensure it's created and matches screen size

        bloomFramebuffer.framebufferClear();
        bloomFramebuffer.bindFramebuffer(false);

        // Setup OpenGL state for 2D rendering
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        RenderUiUtils.prepare2D3();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        // Set draw color
        Color c = new Color(color, true);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

        // Draw the rounded rectangle into the framebuffer
        UIUtils.drawRound(x, y, width, height, radius, c);
        Roundedutil.drawSelectRoundedRect(x, y, width, height, rectRadiusTopLeft, rectRadiusBottomLeft, rectRadiusBottomRight, rectRadiusTopRight, c.getRGB());

        // Restore GL state
        RenderUiUtils.release2D3();
        GL11.glPopMatrix();
        GL11.glPopAttrib();

        bloomFramebuffer.unbindFramebuffer();
        return bloomFramebuffer;
    }

    public static Framebuffer drawRectsToFramebuffer(List<float[]> rects) {
        if(me.youngjibbit.utils.render.RenderDiagnostics.enabled)me.youngjibbit.utils.render.RenderDiagnostics.maskPasses++;
        // rects list elements: {x, y, width, height, radius, color} packed as floats (color as int cast to float)

        ensureBloomFramebuffer(); // Make sure framebuffer exists and matches screen size

        bloomFramebuffer.framebufferClear();
        bloomFramebuffer.bindFramebuffer(false);

        // Setup OpenGL state for 2D rendering
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        RenderUiUtils.prepare2D3();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        for (float[] rect : rects) {
            if(rect.length>6 && rect[6]>0){GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor((int)rect[7],(int)rect[8],(int)rect[9],(int)rect[10]);}
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            float x = rect[0];
            float y = rect[1];
            float width = rect[2];
            float height = rect[3];
            int radius = (int) rect[4];
            int color = Float.floatToRawIntBits(rect[5]); // Recover int color stored as float

            Color c = new Color(color, true);
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

            if(rect.length>12&&rect[12]>0)UIUtils.drawWorldMaskRound(x,y,width,height,radius,c);else UIUtils.drawRound(x, y, width, height, radius, c);
        }

        // Restore GL state
        RenderUiUtils.release2D3();
        GL11.glPopMatrix();
        GL11.glPopAttrib();

        bloomFramebuffer.unbindFramebuffer();
        return bloomFramebuffer;
    }


    public static void releaseResources(){
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){
            for(Framebuffer target:framebufferList)target.deleteFramebuffer();framebufferList.clear();framebuffer=null;
            if(bloomFramebuffer!=null){bloomFramebuffer.deleteFramebuffer();bloomFramebuffer=null;}
            if(kawaseDown!=null){kawaseDown.close();kawaseDown=null;}if(kawaseUp!=null){kawaseUp.close();kawaseUp=null;}
        }
    }
    private static void ensureBloomFramebuffer() {
        if (bloomFramebuffer == null || bloomFramebuffer.framebufferWidth != mc.displayWidth || bloomFramebuffer.framebufferHeight != mc.displayHeight) {
            // Clean up the previous framebuffer if it exists
            if (bloomFramebuffer != null) {
                bloomFramebuffer.deleteFramebuffer();
            }

            // Create a new framebuffer with the current screen dimensions
            bloomFramebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
            bloomFramebuffer.setFramebufferFilter(GL_LINEAR);  // Set the filter for the framebuffer texture (you can adjust as needed)

            // Optionally, you can adjust texture wrap parameters here if needed
            GlStateManager.bindTexture(bloomFramebuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(0);
        }
    }
}
