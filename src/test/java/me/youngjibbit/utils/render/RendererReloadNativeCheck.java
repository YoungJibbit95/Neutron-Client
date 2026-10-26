package me.youngjibbit.utils.render;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.rendercheck.PbufferTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.*;
import net.minecraft.util.*;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Real renderer construction and resource reload, isolated from full startGame and personal profiles. */
public final class RendererReloadNativeCheck {
    public static void main(String[] args) throws Exception {
        File data=Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")),"pulsar-renderer-reload-").toFile();
        File pack=new File(data,"pack");
        Path shader=new File(pack,"assets/minecraft/shaders/post/notch.json").toPath();
        Files.createDirectories(shader.getParent());
        Files.write(shader,"{\"targets\":[\"fixture\"],\"passes\":[]}".getBytes(StandardCharsets.UTF_8));
        try(PbufferTestContext context=new PbufferTestContext()) {
            Minecraft mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(
                    new Session("RenderFixture","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(1280,960,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                    new GameConfiguration.GameInformation(false,"renderer reload regression"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());
            List<IResourcePack> packs=Collections.<IResourcePack>singletonList(new FolderResourcePack(pack));
            resources.reloadResources(packs);
            set(Minecraft.class,mc,"mcResourceManager",resources);
            TextureManager textures=new TextureManager(resources);set(Minecraft.class,mc,"renderEngine",textures);
            Framebuffer main=new Framebuffer(mc.displayWidth,mc.displayHeight,true);set(Minecraft.class,mc,"framebufferMc",main);
            CustomEntityRenderer renderer=new CustomEntityRenderer(mc);mc.entityRenderer=renderer;
            resources.registerReloadListener(renderer);
            List<?> listeners=(List<?>)get(SimpleReloadableResourceManager.class,resources,"reloadListeners");
            require(mc.entityRenderer==renderer && listeners.size()==1 && listeners.get(0)==mc.entityRenderer,"active instance must be sole registered renderer");
            Map<?,?> counts=(Map<?,?>)get(TextureManager.class,textures,"mapTextureCounters");
            require(Integer.valueOf(1).equals(counts.get("lightMap")),"constructor creates one lightmap");
            set(EntityRenderer.class,renderer,"shaderIndex",0);
            renderer.loadShader(new ResourceLocation("shaders/post/notch.json"));
            require(renderer.getShaderGroup()!=null,"real ShaderGroup created");
            try {
                for(int i=0;i<3;i++) {
                    ShaderGroup old=renderer.getShaderGroup();Framebuffer oldTarget=old.getFramebufferRaw("fixture");
                    require(oldTarget.framebufferObject>0 && glIsTexture(oldTarget.framebufferTexture),"old GPU target valid");
                    resources.reloadResources(packs);
                    ShaderGroup current=renderer.getShaderGroup();
                    require(current!=null && current!=old,"reload reached active renderer and rebuilt group");
                    require(oldTarget.framebufferObject==-1 && oldTarget.framebufferTexture==-1,"old target disposed by reload");
                    Framebuffer target=current.getFramebufferRaw("fixture");
                    require(target.framebufferObject>0 && glIsTexture(target.framebufferTexture),"replacement GPU target valid");
                    require(mc.entityRenderer==renderer && listeners.size()==1 && listeners.get(0)==mc.entityRenderer,"reload must not append renderer listeners");
                    require(Integer.valueOf(1).equals(counts.get("lightMap")),"reload must not construct duplicate lightmaps");
                }
                renderer.getShaderGroup().createBindFramebuffers(640,480);
                require(renderer.getShaderGroup().getFramebufferRaw("fixture").framebufferWidth==640,"active group resize");
                context.check();require(glGetError()==GL_NO_ERROR,"reload GL error");
            } finally {renderer.func_181022_b();main.deleteFramebuffer();textures.deleteTexture((ResourceLocation)get(EntityRenderer.class,renderer,"locationLightMap"));}
            System.out.println("PASS: actual CustomEntityRenderer constructor, sole reload-listener identity, three real ShaderGroup target replacements/deletions, one lightmap, group resize. No renderer/RenderGlobal resize, full startGame, shader programs, shaderpacks or world rendering.");
        }
    }
    static Object get(Class<?> owner,Object target,String name)throws Exception {Field f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void set(Class<?> owner,Object target,String name,Object value)throws Exception {Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
