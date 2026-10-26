package me.youngjibbit.utils.render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import java.nio.*;
/** Captures a deferred GUI rectangle in screen space, including scale/translation and billboard projection. */
public final class ScreenRect {
    private static final FloatBuffer model=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16),point=BufferUtils.createFloatBuffer(3);
    private static final IntBuffer viewport=BufferUtils.createIntBuffer(16);
    private ScreenRect() { }
    public static float[] project(float x,float y,float width,float height) {
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,model);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<4;i++){GLU.gluProject(x+((i&1)==0?0:width),y+((i&2)==0?0:height),0,model,projection,viewport,point);minX=Math.min(minX,point.get(0));maxX=Math.max(maxX,point.get(0));minY=Math.min(minY,point.get(1));maxY=Math.max(maxY,point.get(1));}
        Minecraft mc=Minecraft.getMinecraft();float scale=new ScaledResolution(mc).getScaleFactor();
        return new float[]{minX/scale,(mc.displayHeight-maxY)/scale,(maxX-minX)/scale,(maxY-minY)/scale};
    }
}
