package me.youngjibbit.utils.shader;
import me.youngjibbit.utils.render.ShaderUtil;
import java.io.IOException;

/** Classpath-only sandbox adapter; keeps the native menu's vertex shader and normalized mouse contract. */
public final class GLSLSandboxShaders implements AutoCloseable {
    private final ShaderUtil program;
    public GLSLSandboxShaders(String fragment)throws IOException{
        program=new ShaderUtil(GLSLSandboxShaders.class.getResourceAsStream(fragment),GLSLSandboxShaders.class.getResourceAsStream("/assets/minecraft/pulsar/shader/mainmenu/passthrough.vsh"));
    }
    public void useShader(int width,int height,float mouseX,float mouseY,float time){program.init();program.setUniformf("resolution",width,height);program.setUniformf("mouse",mouseX/width,1-mouseY/height);program.setUniformf("time",time);}
    @Override public void close(){program.close();}
}
