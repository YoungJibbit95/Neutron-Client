package me.youngjibbit.utils.render;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;

/** One cached program, bounded ribbon geometry and one exact-position billboard. No FBOs. */
public final class TargetEspRenderer implements AutoCloseable {
    public static final class Frame {
        public boolean halo, marker, markerHit;
        public double x, y, z, radiusX, radiusZ, height;
        public double markerX, markerY, markerZ, cameraX, cameraY, cameraZ;
        public float time, lift = .5f, direction, leanX, leanZ;
        public void clear() { halo = marker = markerHit = false; }
    }
    public static final class Style {
        public float opacity = .9f, width = .13f, trail = .65f, markerSize = .075f, glow = 1;
        public boolean throughWalls, customColor;
        public int color = 0xff9a7cff;
    }

    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private ShaderUtil shader;
    private boolean failed;

    public void render(Frame frame, Style style) {
        if (frame == null || style == null || !frame.halo && !frame.marker || !validCamera(frame)) return;
        boolean halo = frame.halo && finite(frame.x,frame.y,frame.z) && Double.isFinite(frame.radiusX)
                && Double.isFinite(frame.radiusZ) && Double.isFinite(frame.height)
                && Float.isFinite(frame.lift) && Float.isFinite(frame.direction)
                && Float.isFinite(frame.leanX) && Float.isFinite(frame.leanZ)
                && frame.radiusX > 0 && frame.radiusZ > 0 && frame.height > 0;
        boolean marker = frame.marker && finite(frame.markerX,frame.markerY,frame.markerZ);
        if (!halo && !marker) return;
        try (RenderState state = WorldVisuals.begin()) {
            glDisable(GL_FOG); glDepthMask(false); glDepthFunc(GL_LEQUAL);
            if (style.throughWalls) glDisable(GL_DEPTH_TEST); else glEnable(GL_DEPTH_TEST);
            if (shader == null && !failed && OpenGlHelper.shadersSupported) {
                try { shader = new ShaderUtil("pulsar/shader/target/targetEsp.frag"); }
                catch (RuntimeException error) {
                    failed = true;
                    System.err.println("TargetESP shader unavailable; using the geometric fallback: " + error.getMessage());
                }
            }
            if (shader != null) {
                shader.init();
                shader.setUniformf("uGlow", clamp(style.glow,0,2));
                shader.setUniformf("uPhase", Float.isFinite(frame.time) ? frame.time * .19f : 0);
                if (halo) {
                    shader.setUniformi("uKind", 0);
                    ribbon(frame,style,0,1,1);
                    float tail = clamp(style.trail,0,1);
                    if (tail > .001f) {
                        shader.setUniformi("uKind", 2);
                        ribbon(frame,style,1,tail*.31f,1.55f);
                        ribbon(frame,style,2,tail*.14f,2.05f);
                    }
                }
                if (marker) {
                    shader.setUniformi("uKind", 1);
                    shader.setUniformf("uHit", frame.markerHit ? 1 : 0);
                    billboard(frame,style);
                }
            } else {
                GL20.glUseProgram(0);
                if (halo) ribbon(frame,style,0,.6f,.18f);
                if (marker) fallbackPoint(frame,style);
            }
        }
    }

    private void ribbon(Frame f, Style s, int layer, float alpha, float widthScale) {
        double extent = clamp(s.width,.035f,.4f) * widthScale;
        double y = f.y + Math.max(0,Math.min(1,f.lift))*f.height
                - f.direction*layer*.065*f.height*clamp(s.trail,0,1);
        double grow = 1 + layer*.035*clamp(s.trail,0,1);
        float opacity = clamp(s.opacity,0,1)*alpha;
        glBegin(GL_TRIANGLE_STRIP);
        for (int i=0;i<=WorldVisuals.SEGMENTS;i++) {
            float u=i/(float)WorldVisuals.SEGMENTS;
            double cosine=WorldVisuals.COS[i],sine=WorldVisuals.SIN[i];
            double tilt=cosine*f.leanX+sine*f.leanZ;
            double x=f.x+cosine*f.radiusX*grow-f.leanX*layer*.14;
            double z=f.z+sine*f.radiusZ*grow-f.leanZ*layer*.14;
            color(s,u,opacity);glTexCoord2f(u,0);vertex(f,x,y+tilt-extent,z);
            color(s,u,opacity);glTexCoord2f(u,1);vertex(f,x,y+tilt+extent,z);
        }
        glEnd();
        if(RenderDiagnostics.enabled)RenderDiagnostics.shaderDraws++;
    }

    private void billboard(Frame f, Style s) {
        model.clear();glGetFloat(GL_MODELVIEW_MATRIX,model);
        double size=clamp(s.markerSize,.015f,.3f);
        glBegin(GL_QUADS);
        corner(f,s,-1,-1,size,0,0);corner(f,s,1,-1,size,1,0);
        corner(f,s,1,1,size,1,1);corner(f,s,-1,1,size,0,1);
        glEnd();
        if(RenderDiagnostics.enabled)RenderDiagnostics.shaderDraws++;
    }
    private void corner(Frame f,Style s,double x,double y,double size,float u,float v) {
        color(s,(u+v)*.5f,clamp(s.opacity,0,1));glTexCoord2f(u,v);
        vertex(f,f.markerX+(model.get(0)*x+model.get(1)*y)*size,
                f.markerY+(model.get(4)*x+model.get(5)*y)*size,
                f.markerZ+(model.get(8)*x+model.get(9)*y)*size);
    }
    private void fallbackPoint(Frame f,Style s) {
        model.clear();glGetFloat(GL_MODELVIEW_MATRIX,model);
        double size=clamp(s.markerSize,.015f,.3f)*.3;
        glBegin(GL_TRIANGLE_FAN);
        color(s,.5f,clamp(s.opacity,0,1));vertex(f,f.markerX,f.markerY,f.markerZ);
        for(int i=0;i<=WorldVisuals.SEGMENTS;i++) {
            double x=WorldVisuals.COS[i],y=WorldVisuals.SIN[i];color(s,i/(float)WorldVisuals.SEGMENTS,0);
            vertex(f,f.markerX+(model.get(0)*x+model.get(1)*y)*size,
                    f.markerY+(model.get(4)*x+model.get(5)*y)*size,
                    f.markerZ+(model.get(8)*x+model.get(9)*y)*size);
        }
        glEnd();
    }
    private static void color(Style style,float position,float alpha) {
        if(!style.customColor)WorldVisuals.color(position,alpha);
        else {int c=style.color;glColor4f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,alpha*(c>>>24)/255f);}
    }
    private static void vertex(Frame f,double x,double y,double z){glVertex3d(x-f.cameraX,y-f.cameraY,z-f.cameraZ);}
    private static boolean finite(double x,double y,double z){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    private static boolean validCamera(Frame f){return finite(f.cameraX,f.cameraY,f.cameraZ);}
    private static float clamp(float value,float min,float max){return Float.isFinite(value)?Math.max(min,Math.min(max,value)):min;}
    @Override public void close() {
        if(shader!=null){try(RenderState ignored=new RenderState()){shader.close();}finally{shader=null;}}
        failed=false;
    }
}
