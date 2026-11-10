package me.youngjibbit.module.impl.render;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Arrays;
import me.youngjibbit.utils.RenderUtil;
import me.youngjibbit.utils.render.RUtil;
import me.youngjibbit.utils.render.RenderState;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;

/** Verifies forwarding of legacy coordinate, overload, color and rounding-mode contracts. */
public final class LegacyRenderFacadeCheck {
    private LegacyRenderFacadeCheck() { }

    public static void run() {
        // Warm the canonical renderer's legacy frustum initialization before comparisons.
        RUtil.interpolate(1, 0, .5);
        compare("round size", () -> RUtil.drawRoundedRect(20d,30d,90d,45d,6d,0xaacc4488),
                () -> RenderUtil.drawRoundedRect(20,30,90,45,6,0xaacc4488));
        compare("round3", () -> RUtil.drawRoundedRect3(20,30,110,75,6,0xaacc4488),
                () -> RenderUtil.drawRoundedRect3(20,30,110,75,6,0xaacc4488));
        compare("rounded endpoints", () -> RUtil.drawRoundedRectangle(20,30,110,75,6,0xaacc4488),
                () -> RenderUtil.drawRoundedRectangle(20,30,110,75,6,0xaacc4488));
        compare("circle", () -> RUtil.drawCircle(70,65,20,0xaacc4488),
                () -> RenderUtil.drawCircle(70,65,20,0xaacc4488));
        compare("checkmark", () -> RUtil.drawCheckMark(40,40,15,0xaacc4488),
                () -> RenderUtil.drawCheckMark(40,40,15,0xaacc4488));
        compare("ellipse", () -> RUtil.glDrawFilledEllipse(70,65,20,0,32,32,true,0xaacc4488),
                () -> RenderUtil.glDrawFilledEllipse(70,65,20,0,32,32,true,0xaacc4488));
        for(RenderUtil.RoundingMode mode:RenderUtil.RoundingMode.values()) {
            compare("rounding "+mode,
                    () -> RUtil.glDrawRoundedRectEllipse(20,30,90,45,RUtil.RoundingMode.valueOf(mode.name()),16,6,0xaacc4488),
                    () -> RenderUtil.glDrawRoundedRectEllipse(20,30,90,45,mode,16,6,0xaacc4488));
        }
        compare("outline", () -> RUtil.drawUnfilledRectangle(20,30,110,75,6,0xaacc4488),
                () -> RenderUtil.drawUnfilledRectangle(20,30,110,75,6,0xaacc4488));
        compare("outline width", () -> RUtil.drawUnfilledRectangle(20,30,110,75,6,0xaacc4488,2),
                () -> RenderUtil.drawUnfilledRectangle(20,30,110,75,6,0xaacc4488,2));
        compare("gradient vertical", () -> RUtil.drawGradientRect(20,30,110,75,0xaacc4488,0x7022aaee),
                () -> RenderUtil.drawGradientRect(20,30,110,75,0xaacc4488,0x7022aaee));
        compare("gradient sideways", () -> RUtil.drawGradientSideways(20,30,110,75,0xaacc4488,0x7022aaee),
                () -> RenderUtil.drawGradientSideways(20,30,110,75,0xaacc4488,0x7022aaee));
        Color color=new Color(0xaacc4488,true);
        compare("filled circle integer", () -> RUtil.drawFilledCircle(70,65,20,color),
                () -> RenderUtil.drawFilledCircle(70,65,20,color));
        compare("unfilled circle integer", () -> RUtil.drawUnfilledCircle(70,65,20,color),
                () -> RenderUtil.drawUnfilledCircle(70,65,20,color));
        compare("quad size", () -> RUtil.glDrawFilledQuad(20,30,90,45,0xaacc4488),
                () -> RenderUtil.glDrawFilledQuad(20,30,90,45,0xaacc4488));
        compare("scissor endpoints", () -> {RUtil.prepareScissorBox(20,30,110,75);glEnable(GL_SCISSOR_TEST);RUtil.glDrawFilledQuad(0,0,250,180,0xaacc4488);},
                () -> {RenderUtil.prepareScissorBox(20,30,110,75);glEnable(GL_SCISSOR_TEST);RenderUtil.glDrawFilledQuad(0,0,250,180,0xaacc4488);});
        SessionInfoRenderCheck.require(RenderUtil.hover(5,8,15,18,10,10)
                && !RenderUtil.hover(5,8,16,18,10,10),"Legacy integer hover boundaries changed");
        SessionInfoRenderCheck.require(RenderUtil.interpolate(10,2,.25)==4
                && RenderUtil.interpolateFloat(10,2,.25f)==4,"Legacy interpolation argument order changed");
        SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Legacy render facade GL error");
        System.out.println("PASS: legacy render facade RGBA, dimensions/endpoints, integer overloads and all rounding modes");
    }

    private static void compare(String name,Runnable canonical,Runnable facade) {
        SessionInfoRenderCheck.require(Arrays.equals(draw(canonical),draw(facade)),"Legacy renderer differs: "+name);
    }

    private static byte[] draw(Runnable draw) {
        try(RenderState state=new RenderState()) {
            SessionInfoRenderCheck.backdrop();
            draw.run();
            int size=SessionInfoRenderCheck.mc.displayWidth*SessionInfoRenderCheck.mc.displayHeight*4;
            ByteBuffer pixels=BufferUtils.createByteBuffer(size);
            glReadPixels(0,0,SessionInfoRenderCheck.mc.displayWidth,SessionInfoRenderCheck.mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            byte[] result=new byte[size];pixels.get(result);return result;
        }
    }
}
