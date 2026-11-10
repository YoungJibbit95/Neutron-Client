package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Utils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/** Shared GLSL 1.20 compiler. Instances own their linked program; supplied streams are consumed and closed. */
public class ShaderUtil implements Utils, AutoCloseable {
    private final int programID;
    private final Map<String,Integer> uniforms=new HashMap<>();
    private boolean closed;
    public ShaderUtil(String fragment,String vertex){this(fragmentSource(fragment),resource(vertex),0);}
    public ShaderUtil(String fragment){this(fragment,"Flauxy/Shaders/vertex.vsh");}
    public ShaderUtil(String source,boolean raw){this(source,resource("Flauxy/Shaders/vertex.vsh"),0);}
    public ShaderUtil(InputStream fragment,InputStream vertex)throws IOException{this(readBoth(fragment,vertex));}
    private ShaderUtil(String[] source){this(source[0],source[1],0);}
    private static String[] readBoth(InputStream fragment,InputStream vertex)throws IOException{try(InputStream f=fragment;InputStream v=vertex){return new String[]{text(f),text(v)};}}
    private ShaderUtil(String fragment,String vertex,int sourceMarker){
        int program=glCreateProgram(),fs=0,vs=0;boolean attached=false,linked=false;
        if(RenderDiagnostics.enabled)RenderDiagnostics.programCreates++;
        try{
            if(fragment.contains("// UI_GRADIENT"))fragment=fragment.replace("// UI_GRADIENT",resource("Flauxy/Shaders/uiGradient.glsl"));if(fragment.contains("// UI_SURFACE"))fragment=fragment.replace("// UI_SURFACE",resource("Flauxy/Shaders/uiSurface.glsl"));
            if(fragment.contains("// UI_GLASS"))fragment=fragment.replace("// UI_GLASS",resource("Flauxy/Shaders/uiGlass.glsl"));
            fs=compile(fragment,GL_FRAGMENT_SHADER);vs=compile(vertex,GL_VERTEX_SHADER);
            glAttachShader(program,fs);glAttachShader(program,vs);attached=true;glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==0)throw new IllegalStateException("Shader link failed: "+glGetProgramInfoLog(program,4096));
            linked=true;programID=program;
        }finally{
            if(attached){glDetachShader(program,fs);glDetachShader(program,vs);}
            if(fs!=0)deleteShader(fs);if(vs!=0)deleteShader(vs);
            if(!linked){glDeleteProgram(program);if(RenderDiagnostics.enabled)RenderDiagnostics.programDeletes++;}
        }
    }
    private static int compile(String source,int type){
        int shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader);
        if(RenderDiagnostics.enabled)RenderDiagnostics.shaderCompiles++;
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==0){String info=glGetShaderInfoLog(shader,4096);deleteShader(shader);throw new IllegalStateException("Shader compile failed ("+type+"): "+info);}return shader;
    }
    private static void deleteShader(int id){glDeleteShader(id);if(RenderDiagnostics.enabled)RenderDiagnostics.shaderDeletes++;}
    private static String read(InputStream stream)throws IOException{try(InputStream owned=stream){return text(owned);}}
    private static String text(InputStream stream)throws IOException{
        if(stream==null)throw new FileNotFoundException("Shader resource not found");
        BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();String line;while((line=reader.readLine())!=null)out.append(line).append('\n');return out.toString();
    }
    private static String resource(String name){try{return read(mc.getResourceManager().getResource(new ResourceLocation(name)).getInputStream());}catch(IOException ex){throw new IllegalStateException("Shader resource: "+name,ex);}}
    private static String fragmentSource(String name) {
        String path;
        switch (name) {
            case "gradientMask":
            case "roundRectTexture":
                path = name + ".frag";
                break;
            case "kawaseUpGlow":
            case "glow":
            case "chams":
            case "roundRectOutline":
            case "kawaseUpBloom":
            case "kawaseDownBloom":
            case "kawaseUp":
            case "kawaseDown":
            case "mask":
            case "gradient":
            case "roundedRect":
            case "roundedRectGradient":
                path = "builtin/" + name + ".frag";
                break;
            default:
                return resource(name);
        }
        // These named programs were embedded in the client. Preserve that lookup independently
        // of resource-pack overrides for the public file-based constructor.
        try {
            return read(ShaderUtil.class.getResourceAsStream("/assets/minecraft/Flauxy/Shaders/" + path));
        } catch (IOException ex) {
            throw new IllegalStateException("Bundled shader: " + path, ex);
        }
    }
    public void init(){ensureOpen();glUseProgram(programID);}
    public void unload(){glUseProgram(0);}
    private void ensureOpen(){if(closed)throw new IllegalStateException("Shader is closed");}
    public int getProgramId(){ensureOpen();return programID;}
    public int getUniform(String name){ensureOpen();return uniforms.computeIfAbsent(name,key->glGetUniformLocation(programID,key));}
    public void setUniformf(String name,float...v){int loc=getUniform(name);switch(v.length){case 1:glUniform1f(loc,v[0]);break;case 2:glUniform2f(loc,v[0],v[1]);break;case 3:glUniform3f(loc,v[0],v[1],v[2]);break;case 4:glUniform4f(loc,v[0],v[1],v[2],v[3]);break;default:throw new IllegalArgumentException("Float uniform needs 1-4 values");}}
    public void setUniformi(String name,int...v){int loc=getUniform(name);if(v.length==2)glUniform2i(loc,v[0],v[1]);else if(v.length==1)glUniform1i(loc,v[0]);else throw new IllegalArgumentException("Integer uniform needs 1-2 values");}
    /** Rectangle dimensions, top-left texture UV origin (not endpoint coordinates). */
    public static void drawQuads(float x,float y,float width,float height){
        if(RenderDiagnostics.enabled)RenderDiagnostics.shaderDraws++;
        glBegin(GL_QUADS);glTexCoord2f(0,0);glVertex2f(x,y);glTexCoord2f(0,1);glVertex2f(x,y+height);glTexCoord2f(1,1);glVertex2f(x+width,y+height);glTexCoord2f(1,0);glVertex2f(x+width,y);glEnd();
    }
    /** Framebuffer UV origin: vertically flipped relative to the positioned rectangle overload. */
    public static void drawQuads(float width,float height){
        if(RenderDiagnostics.enabled)RenderDiagnostics.shaderDraws++;
        glBegin(GL_QUADS);glTexCoord2f(0,1);glVertex2f(0,0);glTexCoord2f(0,0);glVertex2f(0,height);glTexCoord2f(1,0);glVertex2f(width,height);glTexCoord2f(1,1);glVertex2f(width,0);glEnd();
    }
    public static void drawQuads(){ScaledResolution sr=GuiResolution.current(mc);drawQuads((float)sr.getScaledWidth_double(),(float)sr.getScaledHeight_double());}
    /** The legacy menu uses floor(width / scale), but a fractional height. Preserve that viewport contract. */
    public static void drawFixedQuads(){ScaledResolution sr=GuiResolution.current(mc);drawQuads((float)(mc.displayWidth/sr.getScaleFactor()),(float)mc.displayHeight/sr.getScaleFactor());}
    @Override public void close(){if(closed)return;if(glGetInteger(GL_CURRENT_PROGRAM)==programID)glUseProgram(0);glDeleteProgram(programID);if(RenderDiagnostics.enabled)RenderDiagnostics.programDeletes++;uniforms.clear();closed=true;}
}
