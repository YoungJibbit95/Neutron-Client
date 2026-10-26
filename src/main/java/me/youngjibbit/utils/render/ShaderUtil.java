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
    private static String fragmentSource(String name){switch(name){
        case "kawaseUpGlow":return kawaseUpGlow;
        case "glow":return glowShader;
        case "chams":return chams;
        case "roundRectTexture":return roundRectTexture;
        case "roundRectOutline":return roundRectOutline;
        case "kawaseUpBloom":return kawaseUpBloom;
        case "kawaseDownBloom":return kawaseDownBloom;
        case "kawaseUp":return kawaseUp;
        case "kawaseDown":return kawaseDown;
        case "gradientMask":return gradientMask;
        case "mask":return mask;
        case "gradient":return gradient;
        case "roundedRect":return roundedRect;
        case "roundedRectGradient":return roundedRectGradient;
        default:return resource(name);
    }}
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
    public static void drawQuads(){ScaledResolution sr=new ScaledResolution(mc);drawQuads((float)sr.getScaledWidth_double(),(float)sr.getScaledHeight_double());}
    /** The legacy menu uses floor(width / scale), but a fractional height. Preserve that viewport contract. */
    public static void drawFixedQuads(){ScaledResolution sr=new ScaledResolution(mc);drawQuads((float)(mc.displayWidth/sr.getScaleFactor()),(float)mc.displayHeight/sr.getScaleFactor());}
    @Override public void close(){if(closed)return;if(glGetInteger(GL_CURRENT_PROGRAM)==programID)glUseProgram(0);glDeleteProgram(programID);if(RenderDiagnostics.enabled)RenderDiagnostics.programDeletes++;uniforms.clear();closed=true;}
    private static final String kawaseUpGlow = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform bool check;\n" +
            "uniform float lastPass;\n" +
            "uniform float exposure;\n" +
            "\n" +
            "void main() {\n" +
            "    if(check && texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "\n" +
            "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    vec4 smpl1 =  texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);\n" +
            "    smpl1.rgb *= smpl1.a;\n" +
            "    sum += smpl1 * 2.0;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3 * 2.0;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp5.rgb *= smp5.a;\n" +
            "    sum += smp5 * 2.0;\n" +
            "    vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    smp6.rgb *= smp6.a;\n" +
            "    sum += smp6;\n" +
            "    vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp7.rgb *= smp7.a;\n" +
            "    sum += smp7 * 2.0;\n" +
            "    vec4 result = sum / 12.0;\n" +
            "    gl_FragColor = vec4(result.rgb / max(result.a, 0.00001), mix(result.a, 1.0 - exp(-result.a * exposure), step(0.0, lastPass)));\n" +
            "}";

    private static final String glowShader = "#version 120\n" +
            "\n" +
            "uniform sampler2D textureIn, textureToCheck;\n" +
            "uniform vec2 texelSize, direction;\n" +
            "uniform vec3 color;\n" +
            "uniform bool avoidTexture;\n" +
            "uniform float exposure, radius;\n" +
            "uniform float weights[256];\n" +
            "\n" +
            "#define offset direction * texelSize\n" +
            "\n" +
            "void main() {\n" +
            "    if (direction.y == 1 && avoidTexture) {\n" +
            "        if (texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;\n" +
            "    }\n" +
            "    vec4 innerColor = texture2D(textureIn, gl_TexCoord[0].st);\n" +
            "    innerColor.rgb *= innerColor.a;\n" +
            "    innerColor *= weights[0];\n" +
            "    for (float r = 1.0; r <= radius; r++) {\n" +
            "        vec4 colorCurrent1 = texture2D(textureIn, gl_TexCoord[0].st + offset * r);\n" +
            "        vec4 colorCurrent2 = texture2D(textureIn, gl_TexCoord[0].st - offset * r);\n" +
            "\n" +
            "        colorCurrent1.rgb *= colorCurrent1.a;\n" +
            "        colorCurrent2.rgb *= colorCurrent2.a;\n" +
            "\n" +
            "        innerColor += (colorCurrent1 + colorCurrent2) * weights[int(r)];\n" +
            "    }\n" +
            "\n" +
            "    gl_FragColor = vec4(innerColor.rgb / innerColor.a, mix(innerColor.a, 1.0 - exp(-innerColor.a * exposure), step(0.0, direction.y)));\n" +
            "}\n";

    private static final String chams =
            "#version 120\n" +
                    "\n" +
                    "uniform sampler2D textureIn;\n" +
                    "uniform vec4 color;\n" +
                    "void main() {\n" +
                    "    float alpha = texture2D(textureIn, gl_TexCoord[0].st).a;\n" +

                    "    gl_FragColor = vec4(color.rgb, color.a * mix(0.0, alpha, step(0.0, alpha)));\n" +
                    "}\n";

    private static final String roundRectTexture = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform sampler2D textureIn;\n" +
            "uniform float radius, alpha;\n" +
            "\n" +
            "float roundedBoxSDF(vec2 centerPos, vec2 size, float radius) {\n" +
            "    return length(max(abs(centerPos) -size, 0.)) - radius;\n" +
            "}\n" +
            "\n" +
            "\n" +
            "void main() {\n" +
            "    float distance = roundedBoxSDF((rectSize * .5) - (gl_TexCoord[0].st * rectSize), (rectSize * .5) - radius - 1., radius);\n" +
            "    float smoothedAlpha =  (1.0-smoothstep(0.0, 2.0, distance)) * alpha;\n" +
            "    gl_FragColor = vec4(texture2D(textureIn, gl_TexCoord[0].st).rgb, smoothedAlpha);\n" +
            "}";

    private static final String roundRectOutline = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform vec4 color, outlineColor;\n// UI_GRADIENT\n// UI_SURFACE\n" +
            "uniform vec4 uiFirst, uiSecond;uniform float uiAccent;\n" +
            "uniform float radius, outlineThickness;\n" +
            "\n" +
            "float roundedSDF(vec2 centerPos, vec2 size, float radius) {\n" +
            "    vec2 q=abs(centerPos)-size+radius;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    float distance = roundedSDF(gl_FragCoord.xy - location - (rectSize * .5), (rectSize * .5) + (outlineThickness *.5) - 1.0, radius);\n" +
            "\n" +
            "    float blendAmount = smoothstep(0., 2., abs(distance) - (outlineThickness * .5));\n" +
            "\n" +
            "    vec4 surface=uiSurface(color,distance);vec4 insideColor = (distance < 0.) ? surface : vec4(outlineColor.rgb,  0.0);\n" +
            "    vec4 border=uiEnabled?vec4(mix(outlineColor.rgb,uiBorderPalette(uiFirst,uiSecond).rgb,uiAccent),outlineColor.a*mix(1.0,uiBorderPalette(uiFirst,uiSecond).a,uiAccent)):outlineColor;\n" +
            "    gl_FragColor = mix(border, insideColor, blendAmount);\n" +
            "\n" +
            "}";

    private static final String kawaseUpBloom = "#version 120\n// UI_GRADIENT\n" +
            "uniform vec4 uiFirst,uiSecond;uniform float uiIntensity;\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform int check;\n" +
            "\n" +
            "void main() {\n" +
            "  //  if(check && texture2D(textureToCheck, gl_TexCoord[0].st).a > 0.0) discard;\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "\n" +
            "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    vec4 smpl1 =  texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);\n" +
            "    smpl1.rgb *= smpl1.a;\n" +
            "    sum += smpl1 * 2.0;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3 * 2.0;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp5.rgb *= smp5.a;\n" +
            "    sum += smp5 * 2.0;\n" +
            "    vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    smp6.rgb *= smp6.a;\n" +
            "    sum += smp6;\n" +
            "    vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp7.rgb *= smp7.a;\n" +
            "    sum += smp7 * 2.0;\n" +
            "    vec4 result = sum / 12.0;\n" +
            "    if(check==1){if(uiEnabled){vec4 palette=mix(uiFirst,uiSecond,uiPosition(uiGlow));result.rgb=palette.rgb*result.a*palette.a;result.a*=palette.a;}result.rgb*=uiIntensity;result.a*=uiIntensity;}\n" +
            "    gl_FragColor = vec4(result.rgb / max(result.a, 0.00001), mix(result.a, result.a * (1.0 - step(0.00001, texture2D(textureToCheck, gl_TexCoord[0].st).a)),check));\n" +
            "}";

    private static final String kawaseDownBloom = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform vec2 offset, halfpixel, iResolution;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, gl_TexCoord[0].st);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    sum *= 4.0;\n" +
            "    vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);\n" +
            "    smp1.rgb *= smp1.a;\n" +
            "    sum += smp1;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 result = sum / 8.0;\n" +
            "    gl_FragColor = vec4(result.rgb / max(result.a, 0.00001), result.a);\n" +
            "}";

    private static final String kawaseUp = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform int check;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;\n" +
            "\n" +
            "    gl_FragColor = vec4(sum.rgb /12.0, mix(1.0, texture2D(textureToCheck, gl_TexCoord[0].st).a, check));\n" +
            "}\n";

    private static final String kawaseDown = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform vec2 offset, halfpixel, iResolution;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, gl_TexCoord[0].st) * 4.0;\n" +
            "    sum += texture2D(inTexture, uv - halfpixel.xy * offset);\n" +
            "    sum += texture2D(inTexture, uv + halfpixel.xy * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    gl_FragColor = vec4(sum.rgb * .125, 1.0);\n" +
            "}\n";

    private static final String gradientMask = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform sampler2D tex;\n" +
            "uniform vec3 color1, color2, color3, color4;\n" +
            "uniform float alpha;\n" +
            "\n" +
            "#define NOISE .5/255.0\n" +
            "\n" +
            "vec3 createGradient(vec2 coords, vec3 color1, vec3 color2, vec3 color3, vec3 color4){\n" +
            "    vec3 color = mix(mix(color1.rgb, color2.rgb, coords.y), mix(color3.rgb, color4.rgb, coords.y), coords.x);\n" +
            "    //Dithering the color from https://shader-tutorial.dev/advanced/color-banding-dithering/\n" +
            "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898,78.233))) * 43758.5453));\n" +
            "    return color;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n" +
            "    float texColorAlpha = texture2D(tex, gl_TexCoord[0].st).a;\n" +
            "    gl_FragColor = vec4(createGradient(coords, color1, color2, color3, color4), texColorAlpha * alpha);\n" +
            "}";

    private static final String mask = "#version 120\n"+
            "uniform sampler2D u_texture,u_texture2,snapshot;uniform bool glass;uniform vec2 resolution;uniform vec3 glassStyle,tintColor;\n"+
            "void main(){vec2 uv=gl_TexCoord[0].st;float a=texture2D(u_texture,uv).a;vec3 c=texture2D(u_texture2,uv).rgb;\n"+
            "if(glass){vec2 d=vec2(5.0)/resolution;vec2 n=vec2(texture2D(u_texture,uv+vec2(d.x,0.0)).a-texture2D(u_texture,uv-vec2(d.x,0.0)).a,texture2D(u_texture,uv+vec2(0.0,d.y)).a-texture2D(u_texture,uv-vec2(0.0,d.y)).a);float edge=length(n);vec2 warped=clamp(uv-n*glassStyle.x*18.0/resolution,vec2(.5)/resolution,vec2(1.0)-vec2(.5)/resolution);c=mix(texture2D(snapshot,warped).rgb,texture2D(u_texture2,warped).rgb,.82);c=mix(c,tintColor,glassStyle.y)+edge*glassStyle.z*.3;}gl_FragColor=vec4(c,a);}";

    private static final String gradient = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform sampler2D tex;\n" +
            "uniform vec4 color1, color2, color3, color4;\n" +
            "#define NOISE .5/255.0\n" +
            "\n" +
            "vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){\n" +
            "    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);\n" +
            "    //Dithering the color\n" +
            "    // from https://shader-tutorial.dev/advanced/color-banding-dithering/\n" +
            "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "    return color;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n" +
            "    gl_FragColor = createGradient(coords, color1, color2, color3, color4);\n" +
            "}";

    private static final String roundedRectGradient = "#version 120\n// UI_GRADIENT\n// UI_SURFACE\nuniform vec4 uiFirst,uiSecond;\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform vec4 color1, color2, color3, color4;\n" +
            "uniform float radius;\n" +
            "\n" +
            "#define NOISE .5/255.0\n" +
            "\n" +
            "float roundSDF(vec2 p, vec2 b, float r) {\n" +
            "    vec2 q=abs(p)-b;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;\n" +
            "}\n" +
            "\n" +
            "vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){\n" +
            "    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);\n" +
            "    //Dithering the color\n" +
            "    // from https://shader-tutorial.dev/advanced/color-banding-dithering/\n" +
            "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "    return color;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 st = gl_TexCoord[0].st;\n" +
            "    vec2 halfSize = rectSize * .5;\n" +
            "    \n" +
            "   // use the bottom leftColor as the alpha\n"+
            "    float smoothedAlpha =  (1.0-smoothstep(0.0, 2., roundSDF(halfSize - (gl_TexCoord[0].st * rectSize), halfSize - radius - 1., radius)));\n" +
            "    vec4 gradient = createGradient(st, color1, color2, color3, color4);" +
            "    if(uiEnabled)gradient=mix(uiFirst,uiSecond,uiPosition(uiBorder));\n" +
            "    gradient=uiSurface(gradient,roundSDF(halfSize-st*rectSize,halfSize-radius-1.0,radius));\n" +
            "    gl_FragColor = vec4(gradient.rgb, gradient.a * smoothedAlpha);\n" +
            "}";


    private static final String roundedRect = "#version 120\n// UI_GRADIENT\n// UI_SURFACE\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform vec4 color;\n" +
            "uniform float radius;\n" +
            "uniform bool blur;\n" +
            "\n" +
            "float roundSDF(vec2 p, vec2 b, float r) {\n" +
            "    vec2 q=abs(p)-b;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;\n" +
            "}\n" +
            "\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 rectHalf = rectSize * .5;\n" +
            "    // Smooth the result (free antialiasing).\n" +
            "    float smoothedAlpha =  (1.0-smoothstep(0.0, 1.0, roundSDF(rectHalf - (gl_TexCoord[0].st * rectSize), rectHalf - radius - 1., radius))) * color.a;\n" +
            "    vec4 surface=uiSurface(color,roundSDF(rectHalf-gl_TexCoord[0].st*rectSize,rectHalf-radius-1.0,radius));float coverage=color.a>0.0?smoothedAlpha/color.a:0.0;gl_FragColor=vec4(surface.rgb,surface.a*coverage);\n" +
            "\n" +
            "}";

}