package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ArrayListHUDModule;
import me.youngjibbit.module.impl.client.WatermarkHUDModule;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.*;
import net.minecraft.world.*;
import com.mojang.authlib.GameProfile;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Real living entities, vanilla model/item renderers, native hotbar and native Event2D/bloom owner. No game bootstrap/network/world tick. */
public final class IngameHudRenderCheck {
    static Minecraft mc;static ModuleManager modules;static GuiIngame overlay;
    static TargetHUD target;static ArrayListHUDModule array;static ArmorHUD armor;static SessionInfo session;static GlowESP glow;
    static EntityCow cow;static EntityZombie zombie;static EntityOtherPlayerMP player;
    static final File OUT=new File("hud-preview");
    static boolean baseline=Boolean.getBoolean("pulsar.hud.baseline");
    static boolean includeLocalPlayer;
    static class Player extends EntityOtherPlayerMP {
        boolean ready;
        Player(WorldClient world){super(world,new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000123"),"Player93"));ready=true;}
        @Override public String getNameClear(){return ready?super.getNameClear():null;} // Suppress constructor-time cosmetic HTTP.
        @Override protected NetworkPlayerInfo getPlayerInfo(){return null;}
        @Override public boolean isSpectator(){return false;}
    }
    static class LocalPlayer extends EntityPlayerSP {
        boolean ready;
        LocalPlayer(Minecraft mc,WorldClient world,net.minecraft.client.network.NetHandlerPlayClient profile){super(mc,world,profile,new StatFileWriter());ready=true;}
        @Override public String getNameClear(){return ready?super.getNameClear():null;}
    }
    public static void run()throws Exception {
        mc=SessionInfoRenderCheck.mc;OUT.mkdirs();
        ArmorHudCheck.run(); // Initializes the real vanilla item atlas, models and glint renderer.
        ItemRenderer oldItemRenderer=mc.getItemRenderer();
        FontManager oldFonts=Pulsar.INSTANCE.fontManager;ModuleManager oldModules=Pulsar.INSTANCE.getMm();
        FontManager fonts=new FontManager();Pulsar.INSTANCE.fontManager=fonts;
        try {
            mc.displayWidth=1100;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1100,640);
            WorldClient world=new WorldClient(null,new WorldSettings(0,WorldSettings.GameType.CREATIVE,false,false,WorldType.DEFAULT),0,EnumDifficulty.NORMAL,new net.minecraft.profiler.Profiler());
            net.optifine.player.PlayerConfigurations.setPlayerConfiguration(mc.getSession().getUsername(),new net.optifine.player.PlayerConfiguration());
            net.optifine.player.PlayerConfigurations.setPlayerConfiguration("Player93",new net.optifine.player.PlayerConfiguration());
            // Profile-only disconnected handler: no NetworkManager/channel, join or packet application.
            net.minecraft.client.network.NetHandlerPlayClient profile=new net.minecraft.client.network.NetHandlerPlayClient(null,null,null,mc.getSession().getProfile());
            mc.theWorld=world;mc.thePlayer=new LocalPlayer(mc,world,profile);SessionInfoRenderCheck.set(mc,"renderViewEntity",mc.thePlayer);
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
            RenderManager renderer=new RenderManager(mc.getTextureManager(),mc.getRenderItem());SessionInfoRenderCheck.set(mc,"renderManager",renderer);
            SessionInfoRenderCheck.set(mc,"itemRenderer",new ItemRenderer(mc));
            renderer.cacheActiveRenderInfo(world,mc.fontRendererObj,mc.thePlayer,null,mc.gameSettings,0);
            cow=new EntityCow(world);cow.setHealth(7);cow.setCustomNameTag("Cow");
            zombie=new EntityZombie(world);zombie.setHealth(6);zombie.setAbsorptionAmount(3);zombie.setCustomNameTag("Zombie");player=new Player(world);player.setHealth(18);
            for(EntityLivingBase entity:Arrays.asList(cow,zombie,player)){entity.ignoreFrustumCheck=true;entity.setPosition(0,0,0);world.loadedEntityList.add(entity);}
            modules=new ModuleManager();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",modules);
            target=(TargetHUD)modules.getModule(TargetHUD.class);array=(ArrayListHUDModule)modules.getModule(ArrayListHUDModule.class);armor=(ArmorHUD)modules.getModule(ArmorHUD.class);session=(SessionInfo)modules.getModule(SessionInfo.class);glow=(GlowESP)modules.getModule(GlowESP.class);
            for(me.youngjibbit.module.Module module:Arrays.asList(target,array,armor,session,glow,modules.getModule(WatermarkHUDModule.class)))module.setToggled(true);
            array.getBooleanSetting("Show Render").setValue(true);glow.getBooleanSetting("Chests").setValue(false);
            session.getModeSetting("Mode").setValue("Tenacity");SessionInfoRenderCheck.set(session,"x",350f);SessionInfoRenderCheck.set(session,"y",25f);
            SessionInfoRenderCheck.set(armor,"x",370f);SessionInfoRenderCheck.set(armor,"y",200f);
            mc.thePlayer.inventory.mainInventory[0]=new ItemStack(Items.spawn_egg);mc.thePlayer.inventory.mainInventory[1]=new ItemStack(net.minecraft.init.Blocks.sand,4);mc.thePlayer.inventory.mainInventory[2]=new ItemStack(Items.beef,7);
            overlay=new GuiIngame(mc);mc.ingameGUI=overlay;mc.currentScreen=null;
            if(baseline){for(int i=0;i<4;i++)frame(cow);save("before-cow.png");return;}
            checkExtraTextureUnits();checkModelState();RotationHudRenderCheck.run();checkTargets();checkArmor();checkDragging();checkIconsAndMasks();
            armor.getBooleanSetting("Empty Slots").setValue(true);armor.getBooleanSetting("Held Item").setValue(true);armor.getModeSetting("Layout").setValue("Vertical");
            for(int i=0;i<5;i++)frame(cow);save("after-cow.png");
            mc.thePlayer.inventory.armorInventory[0]=new ItemStack(Items.diamond_boots);mc.thePlayer.inventory.armorInventory[0].setItemDamage(300);
            mc.thePlayer.inventory.armorInventory[3]=new ItemStack(Items.diamond_helmet);mc.thePlayer.inventory.armorInventory[3].addEnchantment(net.minecraft.enchantment.Enchantment.protection,3);
            armor.getModeSetting("Layout").setValue("Horizontal");frame(player);save("after-player-equipment.png");
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();for(int i=0;i<5;i++)frame(zombie);
            SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Combined warm frames allocate resources");
            for(boolean kawase:new boolean[]{true,false}){
                glow.getBooleanSetting("Kawase Glow").setValue(kawase);
                for(int[] size:new int[][]{{1001,701,3},{780,520,1},{1100,640,2}}){mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);frame(zombie);frame(zombie);if(kawase){java.util.List<net.minecraft.client.shader.Framebuffer> pool=(java.util.List<net.minecraft.client.shader.Framebuffer>)SessionInfoRenderCheck.get(glow,"framebufferList");SessionInfoRenderCheck.require(pool.get(0).framebufferWidth==size[0]&&pool.get(0).framebufferHeight==size[1],"Entity glow pyramid kept old dimensions");}}
            }
            checkSubstances();checkProjectedSurfaces();
            save("after-zombie-glow.png");writeComparison();RenderDiagnostics.enabled=false;
            int texture=glow.framebuffer.framebufferTexture;glow.setToggled(false);SessionInfoRenderCheck.require(!glIsTexture(texture),"Entity glow framebuffer survived disable");
            SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Combined HUD GL error");
            System.out.println("PASS: native hotbar + Event2D/bloom owner, actual player/cow/zombie models/data + entity glow (both paths), seven target designs/switch/death/long names, compact equipment, exclusive silhouette drag, vector icons, GL state, steady reuse and odd resize/scales");
        }finally{
            if(modules!=null)for(me.youngjibbit.module.Module module:modules.getAllModules())if(module.isToggled())module.setToggled(false);
            CombatTargetTracker.clear();mc.currentScreen=null;mc.thePlayer=null;mc.theWorld=null;SessionInfoRenderCheck.set(mc,"itemRenderer",oldItemRenderer);fonts.close();Pulsar.INSTANCE.fontManager=oldFonts;SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",oldModules);
        }
    }
    static class ProjectionRenderer extends EntityRenderer{
        ProjectionRenderer(){super(mc,mc.getResourceManager());}
        @Override public void setupCameraTransform(float partial,int pass){glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(-5,5,-3,3,-10,10);glMatrixMode(GL_MODELVIEW);glLoadIdentity();glTranslatef(0,-.5f,-1);}
        @Override public void setupOverlayRendering(){ScaledResolution sr=new ScaledResolution(mc);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);glMatrixMode(GL_MODELVIEW);glLoadIdentity();}
    }
    static void checkProjectedSurfaces()throws Exception{
        EntityRenderer before=mc.entityRenderer;java.lang.reflect.Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);mc.entityRenderer=(ProjectionRenderer)((sun.misc.Unsafe)uf.get(null)).allocateInstance(ProjectionRenderer.class);
        Nametags names=(Nametags)modules.getModule(Nametags.class);ESP esp=(ESP)modules.getModule(ESP.class);ArrayListSurface group=new ArrayListSurface();
        me.youngjibbit.module.impl.client.ClientSettings c=me.youngjibbit.module.impl.client.ClientSettings.current();
        try{
            java.util.List<BufferedImage> images=new java.util.ArrayList<>();java.util.List<String> labels=new java.util.ArrayList<>();
            for(String mode:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){c.surface.setValue(mode);esp.getModeSetting("Mode").setValue("Static");scene();BufferedImage clean=SessionInfoRenderCheck.capture();group.withScene(Theme.getDefaultTheme(),()->{try{((me.zero.alpine.listener.Listener)SessionInfoRenderCheck.get(names,"on2D")).accept(new me.youngjibbit.event.render.Event2D(0,new ScaledResolution(mc)));((me.zero.alpine.listener.Listener)SessionInfoRenderCheck.get(esp,"on3D")).accept(new me.youngjibbit.event.render.Event3D(0));}catch(Exception ex){throw new RuntimeException(ex);}});BufferedImage rendered=SessionInfoRenderCheck.capture();SessionInfoRenderCheck.require(!WatermarkCheck.equal(clean,rendered),"Projected surface missing "+mode);images.add(rendered);labels.add(mode+" / actual Nametags + Static ESP; synthetic projection, no world render");}
            esp.getModeSetting("Mode").setValue("Morden");scene();((me.zero.alpine.listener.Listener)SessionInfoRenderCheck.get(esp,"on3D")).accept(new me.youngjibbit.event.render.Event3D(0));SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR&&UiEffects.scene()==null&&!GuiShaderBloom.isBatching(),"Projected surfaces leaked GL/scene/batch");SessionInfoRenderCheck.sheet("18-projected-surfaces-resize.png",labels,images);System.out.println("PASS: actual Nametags, Static panel and Morden world-accent consumers; four substances with deterministic projection, shared clean scene and retained geometry");
        }finally{group.close();names.onDisable();esp.onDisable();mc.entityRenderer=before;}
    }
    static class Hover extends GuiScreen{void tooltip(){drawHoveringText(Arrays.asList("Synthetic item tooltip","Sharp native text"),240,210);}}
    static void checkSubstances()throws Exception{
        me.youngjibbit.module.impl.client.ClientSettings c=me.youngjibbit.module.impl.client.ClientSettings.current();c.reducedMotion.setValue(true);c.gradientMotion.setValue("Static");
        java.util.List<BufferedImage> pictures=new java.util.ArrayList<>();java.util.List<String> captions=new java.util.ArrayList<>();
        Hover hover=new Hover();ScaledResolution sr=new ScaledResolution(mc);hover.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());GuiChat chat=new GuiChat("Synthetic draft, no message sent");chat.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
        BufferedImage previous=null;for(String mode:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){c.surface.setValue(mode);for(int i=0;i<3;i++)frame(cow);hover.tooltip();chat.drawScreen(-1,-1,0);BufferedImage image=SessionInfoRenderCheck.capture();if(previous!=null)SessionInfoRenderCheck.require(!WatermarkCheck.equal(previous,image),"HUDs ignore global substance "+mode);previous=image;pictures.add(image);captions.add(mode+" / HUDs + native hotbar, chat input and tooltip + entity glow");}
        SessionInfoRenderCheck.sheet("17-hud-substances-resize.png",captions,pictures);chat.onGuiClosed();
    }
    static void checkExtraTextureUnits(){
        try(RenderState original=new RenderState()){
            for(int unit:new int[]{org.lwjgl.opengl.GL13.GL_TEXTURE2,org.lwjgl.opengl.GL13.GL_TEXTURE3}){
                int texture=glGenTextures();GlStateManager.setActiveTexture(unit);org.lwjgl.opengl.GL13.glActiveTexture(unit);GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.bindTexture(texture);glBindTexture(GL_TEXTURE_2D,texture);
                try(RenderState guard=new RenderState()){GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);GlStateManager.bindTexture(0);glBindTexture(GL_TEXTURE_2D,0);}
                SessionInfoRenderCheck.require(glIsEnabled(GL_TEXTURE_2D)&&glGetInteger(GL_TEXTURE_BINDING_2D)==texture,"Extra texture unit not restored");
                GlStateManager.disableTexture2D();SessionInfoRenderCheck.require(!glIsEnabled(GL_TEXTURE_2D),"Extra texture unit cache disagrees with GL");GlStateManager.deleteTexture(texture);
            }
        }
    }
    static void drawModels(){
        drawModel(cow,90,60,15);drawModel(zombie,180,45,0);drawModel(player,265,45,-10);
        if(includeLocalPlayer)drawModel(mc.thePlayer,350,45,0);
    }
    static void drawModel(EntityLivingBase entity,int x,int size,float mouse){
        try(RenderState state=new RenderState()){
            for(int unit:new int[]{org.lwjgl.opengl.GL13.GL_TEXTURE1,org.lwjgl.opengl.GL13.GL_TEXTURE2,org.lwjgl.opengl.GL13.GL_TEXTURE3}){GlStateManager.setActiveTexture(unit);org.lwjgl.opengl.GL13.glActiveTexture(unit);GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);}
            GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
            GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);
            GlStateManager.enableDepth();glEnable(GL_DEPTH_TEST);GlStateManager.depthFunc(GL_LEQUAL);glDepthFunc(GL_LEQUAL);GlStateManager.depthMask(true);glDepthMask(true);
            GlStateManager.enableAlpha();glEnable(GL_ALPHA_TEST);GlStateManager.alphaFunc(GL_GREATER,.1f);glAlphaFunc(GL_GREATER,.1f);
            GuiInventory.drawEntityOnScreen(x,160,size,mouse,0,entity);
        }
    }
    static void checkModelState()throws Exception{
        glow.setToggled(false);scene();drawModels();BufferedImage plain=SessionInfoRenderCheck.capture();
        glow.setToggled(true);scene();try(RenderState state=new RenderState()){glow.createFrameBuffers();glow.collectEntities();mc.getFramebuffer().bindFramebuffer(true);drawModels();}BufferedImage masked=SessionInfoRenderCheck.capture();
        SessionInfoRenderCheck.require(WatermarkCheck.equal(plain,masked),"Glow model capture changed visible entity colors/texture/state");
    }
    static void frame(EntityLivingBase selected)throws Exception {
        CombatTargetTracker.update(selected,"offscreen-hud-fixture");
        scene();BufferedImage beforeModels=SessionInfoRenderCheck.capture();
        try(RenderState state=new RenderState()){
            glow.createFrameBuffers();glow.collectEntities();mc.getFramebuffer().bindFramebuffer(true);
            drawModels();
        }
        {BufferedImage after=SessionInfoRenderCheck.capture();float scale=new ScaledResolution(mc).getScaleFactor();for(int cx:new int[]{90,180,265}){int changed=0;for(int y=(int)(65*scale);y<160*scale;y++)for(int x=(int)((cx-25)*scale);x<(cx+25)*scale;x++)if(after.getRGB(x,y)!=beforeModels.getRGB(x,y))changed++;if(changed<=100){ImageIO.write(after,"png",new File(OUT,"missing-model-"+cx+".png"));throw new AssertionError("Vanilla entity model missing at "+cx+", changed="+changed);}}}
        try(RenderState state=new RenderState()){
            Method hotbar=GuiIngame.class.getDeclaredMethod("renderTooltip",ScaledResolution.class,float.class);hotbar.setAccessible(true);hotbar.invoke(overlay,new ScaledResolution(mc),0f);
        }
        GlStateManager.enableDepth();GlStateManager.depthMask(false);GlStateManager.enableBlend();glEnable(GL_SCISSOR_TEST);glScissor(3,5,mc.displayWidth-6,mc.displayHeight-10);int fbo=glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        Method owner=GuiIngame.class.getDeclaredMethod("renderClientHud",float.class,ScaledResolution.class);owner.setAccessible(true);owner.invoke(overlay,0f,new ScaledResolution(mc));
        SessionInfoRenderCheck.require(glIsEnabled(GL_DEPTH_TEST)&&!glGetBoolean(GL_DEPTH_WRITEMASK)&&glIsEnabled(GL_SCISSOR_TEST)&&fbo==glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)&&!GuiShaderBloom.isBatching(),"Native HUD batch leaked state/target");
        glDisable(GL_SCISSOR_TEST);GlStateManager.disableDepth();GlStateManager.depthMask(true);
    }
    static void scene(){
        SessionInfoRenderCheck.backdrop();ScaledResolution sr=new ScaledResolution(mc);
        try(RenderState state=new RenderState()){
            state.screenSpace();GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);
            mc.getTextureManager().bindTexture(new ResourceLocation("textures/blocks/dirt.png"));quad(0,0,sr.getScaledWidth(),sr.getScaledHeight()/2,32);
            mc.getTextureManager().bindTexture(new ResourceLocation("textures/blocks/sand.png"));quad(0,sr.getScaledHeight()/2,sr.getScaledWidth(),sr.getScaledHeight()/2,16);
            GlStateManager.depthMask(true);glDepthMask(true);glClearDepth(1);glClear(GL_DEPTH_BUFFER_BIT);
        }
    }
    static void quad(float x,float y,float w,float h,float tile){glBegin(GL_QUADS);glTexCoord2f(0,0);glVertex2f(x,y);glTexCoord2f(0,h/tile);glVertex2f(x,y+h);glTexCoord2f(w/tile,h/tile);glVertex2f(x+w,y+h);glTexCoord2f(w/tile,0);glVertex2f(x+w,y);glEnd();}
    static void writeComparison()throws Exception{
        File baselineImage=new File(OUT,"before-cow.png");if(!baselineImage.isFile())return;
        BufferedImage before=ImageIO.read(baselineImage),after=ImageIO.read(new File(OUT,"after-cow.png"));
        BufferedImage comparison=new BufferedImage(1100,1340,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=comparison.createGraphics();g.setColor(new java.awt.Color(18,20,25));g.fillRect(0,0,1100,1340);g.setColor(java.awt.Color.WHITE);g.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,18));
        g.drawString("Vorher: HUD-Klassen aus 72a73e03b / gleicher Offscreen-Aufbau",12,23);g.drawImage(before,0,30,null);
        g.drawString("Nachher: native HUD-Reihenfolge, Entity-/Item-Pfade, Entity-Glow",12,693);g.drawImage(after,0,700,null);g.dispose();ImageIO.write(comparison,"png",new File(OUT,"hud-before-after.png"));
    }
    static void save(String name)throws Exception{ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(OUT,name));}
    static void checkTargets()throws Exception{
        java.util.List<BufferedImage> images=new ArrayList<>();java.util.List<String> captions=new ArrayList<>();
        for(String mode:new String[]{"Neutron Default","Neutron White","Neutron Outline","Rise","Tenacity","Basic","Test"}){
            target.getModeSetting("Mode").setValue(mode);
            for(EntityLivingBase entity:Arrays.asList(player,cow,zombie)){
                TargetHUD.Frame f=target.layout(entity,550,320);SessionInfoRenderCheck.require(f!=null&&!f.name.isEmpty()&&!f.healthText.isEmpty()&&f.x+f.width<=546,"Incomplete target layout");
                SessionInfoRenderCheck.require(entity instanceof AbstractClientPlayer||f.skin==null,"Mob uses fake player skin");frame(entity);
                // The native listener must execute, not just the layout method.
                BufferedImage image=SessionInfoRenderCheck.capture();int scale=new ScaledResolution(mc).getScaleFactor();images.add(image.getSubimage((int)((f.x-20)*scale),(int)((f.y-20)*scale),(int)((f.width+40)*scale),(int)((f.height+40)*scale)));captions.add(mode+" / "+f.kind);
            }
        }
        java.awt.image.BufferedImage matrix=new java.awt.image.BufferedImage(1440,1540,java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics=matrix.createGraphics();graphics.setColor(new java.awt.Color(18,20,25));graphics.fillRect(0,0,1440,1540);graphics.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,17));
        for(int i=0;i<images.size();i++){int x=i%3*480,y=i/3*220;graphics.setColor(java.awt.Color.WHITE);graphics.drawString(captions.get(i),x+12,y+22);graphics.drawImage(images.get(i),x+12,y+32,null);}graphics.dispose();ImageIO.write(matrix,"png",new File(OUT,"target-living-matrix.png"));
        cow.setHealth(1);target.layout(cow,550,320);TargetHUD.Frame fresh=target.layout(player,550,320);SessionInfoRenderCheck.require(Math.abs(fresh.health-.9f)<.001,"Health animation carried over from old target");
        cow.setCustomNameTag("A very long animal name that must stay inside the target panel");TargetHUD.Frame longName=target.layout(cow,550,320);SessionInfoRenderCheck.require(longName.nameFont.getWidth(longName.name)<=longName.width-longName.textX-8,"Long target name escaped");
        cow.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(1024);cow.setHealth(900);target.layout(cow,550,320);cow.setHealth(0);SessionInfoRenderCheck.require(target.layout(cow,550,320)==null&&target.layout(null,550,320)==null,"Dead/missing target retains panel");cow.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(10);cow.setHealth(7);cow.setCustomNameTag("Cow");target.getModeSetting("Mode").setValue("Neutron Default");
    }
    static void checkArmor()throws Exception{
        for(String orientation:new String[]{"Horizontal","Vertical"})for(String durability:new String[]{"Percent","Value","Both","None"})for(int count:new int[]{0,2,4}){
            ItemStack[] equipment=new ItemStack[4];for(int i=0;i<count;i++)equipment[i]=new ItemStack(new net.minecraft.item.Item[]{Items.diamond_boots,Items.diamond_leggings,Items.diamond_chestplate,Items.diamond_helmet}[i]);
            armor.getModeSetting("Layout").setValue(orientation);armor.getModeSetting("Durability").setValue(durability);armor.getBooleanSetting("Empty Slots").setValue(true);armor.getBooleanSetting("Held Item").setValue(true);
            ArmorHUD.Frame f=armor.layout(equipment,mc.thePlayer.getHeldItem(),550,320);if(count==0&&orientation.equals("Vertical"))SessionInfoRenderCheck.require(f.height<75,"Empty armor creates a tall block");armor.paint(f);
        }
        armor.getBooleanSetting("Empty Slots").setValue(false);armor.getBooleanSetting("Held Item").setValue(false);SessionInfoRenderCheck.require(armor.layout(new ItemStack[4],null,550,320).cells.isEmpty(),"Hidden empty equipment still paints");
    }
    static void checkDragging()throws Exception{
        Method render=ArrayListHUDModule.class.getDeclaredMethod("renderArrayList");render.setAccessible(true);Method interact=ArrayListHUDModule.class.getDeclaredMethod("interact",boolean.class,boolean.class,float.class,float.class,ArrayListSurface.Shape.class,int.class,int.class);interact.setAccessible(true);
        render.invoke(array);ArrayListSurface.Shape shape=(ArrayListSurface.Shape)SessionInfoRenderCheck.get(array,"frameShape");float x=shape.left,y=shape.top;
        interact.invoke(array,false,true,x+8,y+8,shape,550,320);interact.invoke(array,true,true,x+30,y+30,shape,550,320);SessionInfoRenderCheck.require(array.getNumberSetting("Position X").getValue()==10,"Gameplay held click armed drag");
        interact.invoke(array,true,false,x+8,y+8,shape,550,320);interact.invoke(array,true,true,x+8,y+8,shape,550,320);
        SessionInfo.Frame overlap=session.layout(SessionInfoRenderCheck.sample(),"Overlap",550,320);SessionInfoRenderCheck.set(session,"x",x);SessionInfoRenderCheck.set(session,"y",y);overlap=session.layout(SessionInfoRenderCheck.sample(),"Overlap",550,320);session.interact(true,false,x+8,y+8,overlap,550,320);
        armor.getBooleanSetting("Empty Slots").setValue(true);armor.getBooleanSetting("Held Item").setValue(true);SessionInfoRenderCheck.set(armor,"x",x);SessionInfoRenderCheck.set(armor,"y",y);armor.layout(new ItemStack[4],mc.thePlayer.getHeldItem(),550,320);armor.interact(true,false,x+8,y+8,550,320);
        // Release all three before one shared press; the first hit captures it.
        interact.invoke(array,true,false,x+8,y+8,shape,550,320);interact.invoke(array,true,true,x+8,y+8,shape,550,320);session.interact(true,true,x+8,y+8,overlap,550,320);armor.interact(true,true,x+8,y+8,550,320);
        SessionInfoRenderCheck.require(!(boolean)SessionInfoRenderCheck.get(armor,"dragging"),"Overlapping ArmorHUD captured the Arraylist click");
        SessionInfoRenderCheck.require(!(boolean)SessionInfoRenderCheck.get(session,"dragging"),"Overlapping HUDs captured one click");
        interact.invoke(array,true,true,x+38,y+28,shape,550,320);SessionInfoRenderCheck.require(Math.abs(array.getNumberSetting("Position X").getValue()-(x+30))<.01,"Grab offset jump");
        double saved=array.getNumberSetting("Position X").getValue();interact.invoke(array,true,false,0f,0f,shape,550,320);render.invoke(array);SessionInfoRenderCheck.require(array.getNumberSetting("Position X").getValue()==saved,"Animation moved stored anchor");
        array.getNumberSetting("Position X").setValue(9999.0);array.getNumberSetting("Position Y").setValue(9999.0);render.invoke(array);shape=(ArrayListSurface.Shape)SessionInfoRenderCheck.get(array,"frameShape");SessionInfoRenderCheck.require(shape.left<546&&shape.top<316,"Resized array anchor unreachable");
        array.getNumberSetting("Position X").setValue(10.0);array.getNumberSetting("Position Y").setValue(26.0);SessionInfoRenderCheck.set(session,"x",350f);SessionInfoRenderCheck.set(session,"y",25f);SessionInfoRenderCheck.set(armor,"x",370f);SessionInfoRenderCheck.set(armor,"y",200f);
    }
    static void checkIconsAndMasks()throws Exception{
        for(String mode:new String[]{"Basic","Outline","Tenacity","Rue (Rise)"}){
            session.getModeSetting("Mode").setValue(mode);SessionInfo.Frame f=session.layout(SessionInfoRenderCheck.sample(),"Player93",550,320);
            int icons=0;for(SessionInfo.TextRun run:f.text)if(run instanceof SessionInfo.IconRun){icons++;SessionInfoRenderCheck.require(run.text.isEmpty(),"Raw icon character survives layout");}SessionInfoRenderCheck.require(icons>=2,"Missing semantic icons");
            SessionInfo.Frame blank=new SessionInfo.Frame(f.x,f.y,f.width,f.height,f.radius,f.design,f.snapshot,f.theme,false,false,false,4,0);
            SessionInfoRenderCheck.backdrop();session.paint(blank);BufferedImage off=SessionInfoRenderCheck.capture();SessionInfo.Frame lit=new SessionInfo.Frame(f.x,f.y,f.width,f.height,f.radius,f.design,f.snapshot,f.theme,false,false,true,4,0);
            SessionInfoRenderCheck.backdrop();session.paint(lit);BufferedImage on=SessionInfoRenderCheck.capture();int scale=new ScaledResolution(mc).getScaleFactor();
            for(int iy=(int)((f.y+8)*scale);iy<(f.y+f.height-8)*scale;iy++)for(int ix=(int)((f.x+8)*scale);ix<(f.x+f.width-8)*scale;ix++)SessionInfoRenderCheck.require(off.getRGB(ix,iy)==on.getRGB(ix,iy),"Session glow contributes inside transparent panel");
        }
        session.getModeSetting("Mode").setValue("Tenacity");
    }
}
