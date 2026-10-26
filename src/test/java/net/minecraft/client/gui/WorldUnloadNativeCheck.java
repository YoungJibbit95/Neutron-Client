package net.minecraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.achievement.GuiAchievement;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.*;
import net.minecraft.world.storage.ISaveFormat;
import net.optifine.render.RenderEnv;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import java.lang.reflect.*;
import java.util.*;

/** Actual loadWorld/RenderGlobal/EffectRenderer cleanup with real frustum VBOs and synthetic world/audio. */
final class WorldUnloadNativeCheck {
    static sun.misc.Unsafe unsafe;
    static <T> T bare(Class<T> type) throws Exception { return type.cast(unsafe.allocateInstance(type)); }
    static void set(Class<?> type,Object target,String name,Object value)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    static Object get(Class<?> type,Object target,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static class ClientWorld extends WorldClient {
        int adds,removes,spawns;
        public ClientWorld(){super(null,null,0,null,null);}
        @Override public void addWorldAccess(IWorldAccess listener){adds++;}
        @Override public void removeWorldAccess(IWorldAccess listener){removes++;}
        @Override public boolean spawnEntityInWorld(Entity entity){spawns++;return true;}
    }
    public static class Sound extends SoundHandler {public Sound(){super(null,null);}@Override public void stopSounds(){}}
    public static class Packs extends ResourcePackRepository {public Packs(){super(null,null,null,null,null);}@Override public void clearResourcePack(){}}
    public static class Renderer extends EntityRenderer {
        MapItemRenderer maps;
        public Renderer(){super(null,null);}
        @Override public MapItemRenderer getMapItemRenderer(){return maps;}
    }
    public static class Global extends RenderGlobal {
        int loads;
        public Global(){super(null);}
        // Rejoin calls the actual world-attachment path; chunk rebuild scheduling is outside this fixture.
        @Override public void loadRenderers(){loads++;}
    }
    public static class Player extends EntityPlayerSP {
        public Player(){super(null,null,null,null);}
        @Override public void preparePlayerToSpawn(){}
    }
    public static class Controller extends PlayerControllerMP {
        public Controller(){super(null,null);}
        @Override public void setPlayerCapabilities(EntityPlayer player){}
    }
    static ClientWorld world()throws Exception {ClientWorld world=bare(ClientWorld.class);set(World.class,world,"provider",new WorldProviderSurface());return world;}

    @SuppressWarnings({"unchecked","rawtypes"})
    static void run(Minecraft mc)throws Exception {
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(sun.misc.Unsafe)uf.get(null);
        EntityPlayerSP oldPlayer=mc.thePlayer;EntityRenderer oldRenderer=mc.entityRenderer;
        RenderGlobal oldGlobal=mc.renderGlobal;EffectRenderer oldEffects=mc.effectRenderer;PlayerControllerMP oldController=mc.playerController;
        Object oldSound=get(Minecraft.class,mc,"mcSoundHandler"),oldPacks=get(Minecraft.class,mc,"mcResourcePackRepository"),oldSave=get(Minecraft.class,mc,"saveLoader");
        GuiAchievement oldAchievement=mc.guiAchievement;boolean oldVbo=mc.gameSettings.useVbo,oldRegions=mc.gameSettings.ofRenderRegions;
        ViewFrustum frustum=null;
        try {
            ClientWorld first=world(),next=world();mc.theWorld=first;
            Global global=bare(Global.class);mc.renderGlobal=global;
            set(RenderGlobal.class,global,"theWorld",first);set(RenderGlobal.class,global,"renderManager",bare(RenderManager.class));
            set(RenderGlobal.class,global,"renderEnv",new RenderEnv(Blocks.air.getDefaultState(),BlockPos.ORIGIN));
            for(String field:new String[]{"renderInfos","renderInfosEntities","renderInfosTileEntities"})set(RenderGlobal.class,global,field,new ArrayList<>());
            set(RenderGlobal.class,global,"chunksToUpdate",new LinkedHashSet<>());
            first.addWorldAccess(global);
            mc.gameSettings.useVbo=true;mc.gameSettings.ofRenderRegions=false;
            require(OpenGlHelper.useVbo(),"Real VBO support required for unload coverage");
            frustum=new ViewFrustum(first,0,global,RenderChunk::new);set(RenderGlobal.class,global,"viewFrustum",frustum);
            List<VertexBuffer> buffers=new ArrayList<>();List<Integer> ids=new ArrayList<>();
            for(RenderChunk chunk:frustum.renderChunks)for(int layer=0;layer<EnumWorldBlockLayer.values().length;layer++){
                VertexBuffer buffer=chunk.getVertexBufferByLayer(layer);buffer.func_181722_a(BufferUtils.createByteBuffer(32));
                int id=(Integer)get(VertexBuffer.class,buffer,"glBufferId");require(GL15.glIsBuffer(id),"VBO storage was not created");buffers.add(buffer);ids.add(id);
            }
            EffectRenderer effects=new EffectRenderer(first,mc.getTextureManager());mc.effectRenderer=effects;
            List[][] layers=(List[][])get(EffectRenderer.class,effects,"fxLayers");layers[0][0].add(null);
            ((List)get(EffectRenderer.class,effects,"particleEmitters")).add(null);
            Renderer renderer=bare(Renderer.class);renderer.maps=new MapItemRenderer(mc.getTextureManager());mc.entityRenderer=renderer;
            mc.thePlayer=null;mc.playerController=bare(Controller.class);mc.guiAchievement=new GuiAchievement(mc);
            set(Minecraft.class,mc,"mcSoundHandler",bare(Sound.class));set(Minecraft.class,mc,"mcResourcePackRepository",bare(Packs.class));
            set(Minecraft.class,mc,"saveLoader",java.lang.reflect.Proxy.newProxyInstance(ISaveFormat.class.getClassLoader(),new Class<?>[]{ISaveFormat.class},(p,m,a)->null));
            mc.loadWorld(null,"native unload check");
            require(mc.theWorld==null&&global.getWorld()==null,"World exit retained renderer world");
            require(first.removes==1,"World exit did not remove renderer world access exactly once");
            require(get(RenderGlobal.class,global,"viewFrustum")==null,"World exit retained frustum");
            for(int i=0;i<buffers.size();i++)require((Integer)get(VertexBuffer.class,buffers.get(i),"glBufferId")==-1&&!GL15.glIsBuffer(ids.get(i)),"World exit retained a native VBO");
            require(get(EffectRenderer.class,effects,"worldObj")==null&&effects.getStatistics().equals("0")&&((List)get(EffectRenderer.class,effects,"particleEmitters")).isEmpty(),"World exit retained particles or their world");
            mc.thePlayer=bare(Player.class);mc.loadWorld(next,"native rejoin check");
            require(mc.theWorld==next&&global.getWorld()==next&&next.adds==1&&global.loads==1&&next.spawns==1,"Next world did not attach through the real loadWorld path");
            require(get(EffectRenderer.class,effects,"worldObj")==next,"Particles did not attach to next world");
            mc.loadWorld(null,"native second exit check");require(next.removes==1&&global.getWorld()==null&&mc.thePlayer==null,"Second exit did not clean the next world");
            System.out.println("PASS: actual Minecraft null-world unload removes renderer world access, disposes 16 frustum chunks/64 real VBOs, clears particles, and rejoins/exits next synthetic world");
        } finally {
            if(frustum!=null)frustum.deleteGlResources();
            mc.theWorld=null;mc.thePlayer=oldPlayer;mc.entityRenderer=oldRenderer;mc.renderGlobal=oldGlobal;mc.effectRenderer=oldEffects;mc.playerController=oldController;mc.guiAchievement=oldAchievement;
            mc.gameSettings.useVbo=oldVbo;mc.gameSettings.ofRenderRegions=oldRegions;
            set(Minecraft.class,mc,"mcSoundHandler",oldSound);set(Minecraft.class,mc,"mcResourcePackRepository",oldPacks);set(Minecraft.class,mc,"saveLoader",oldSave);
        }
    }
}
