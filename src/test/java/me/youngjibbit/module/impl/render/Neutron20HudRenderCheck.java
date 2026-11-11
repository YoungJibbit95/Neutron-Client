package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.render.Event2D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.EXTFramebufferObject;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Production Nametags/Keystrokes in a disconnected Pbuffer scene; never a live-game claim. */
final class Neutron20HudRenderCheck {
    private static final File OUT=new File("neutron20-preview");
    private static Minecraft mc;
    private static ClientSettings settings;
    private static Nametags names;
    private static Keystrokes keys;
    private static boolean bright;
    private static BufferedImage clean;

    static void run()throws Exception {
        OUT.mkdirs();mc=IngameHudRenderCheck.mc;settings=ClientSettings.current();
        names=(Nametags)IngameHudRenderCheck.modules.getModule(Nametags.class);
        keys=(Keystrokes)IngameHudRenderCheck.modules.getModule(Keystrokes.class);
        EntityRenderer oldRenderer=mc.entityRenderer;
        int[] oldSize={mc.displayWidth,mc.displayHeight,mc.gameSettings.guiScale};
        KeyBinding[] bindings=bindings();boolean[] oldPressed=new boolean[bindings.length];
        for(int i=0;i<bindings.length;i++)oldPressed[i]=bindings[i].pressed;
        Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
        mc.entityRenderer=(IngameHudRenderCheck.ProjectionRenderer)((sun.misc.Unsafe)unsafe.get(null))
                .allocateInstance(IngameHudRenderCheck.ProjectionRenderer.class);
        try(RenderState ignored=new RenderState()) {
            ThemeMode.select(ThemeMode.LEGACY);Theme.setDefaultTheme(Theme.PinkLemonade);
            settings.surface.setValue("Liquid Glass");settings.gradientMotion.setValue("Static");
            settings.animations.setValue(true);settings.reducedMotion.setValue(false);
            settings.motionIntensity.setValue("Balanced");settings.surfaceOpacity.setValue(.8);UiEffects.invalidate();
            // The disconnected local player starts at the world's spawn, not the origin.
            // Keep the real target beside the cached camera used by Nametags.projectLabel.
            IngameHudRenderCheck.player.setPosition(mc.getRenderManager().viewerPosX+1.5,
                    mc.getRenderManager().viewerPosY,mc.getRenderManager().viewerPosZ+1);
            IngameHudRenderCheck.player.lastTickPosX=IngameHudRenderCheck.player.posX;
            IngameHudRenderCheck.player.lastTickPosY=IngameHudRenderCheck.player.posY;
            IngameHudRenderCheck.player.lastTickPosZ=IngameHudRenderCheck.player.posZ;
            names.setToggled(true);keys.setToggled(true);
            checkNametags();checkKeystrokes();checkDeferredHudCleanup();checkLifecycle();
            require(glGetError()==GL_NO_ERROR,"Neutron 2.0 HUD OpenGL error");
            System.out.println("PASS: Neutron 2.0 independent Nametags/Keystrokes pixels, real health/input data, themes, scales, reduced motion, lifecycle and warm resources");
        }finally{
            for(int i=0;i<bindings.length;i++)bindings[i].pressed=oldPressed[i];
            names.setToggled(false);keys.setToggled(false);mc.entityRenderer=oldRenderer;resize(oldSize);
            RenderDiagnostics.enabled=false;
        }
    }

    private static void checkNametags()throws Exception {
        require(names.replacementCandidate(IngameHudRenderCheck.player,mc.getRenderManager()),
                "Nametag fixture player does not satisfy the production visibility policy");
        try(RenderState projectionState=new RenderState()) {
            mc.entityRenderer.setupCameraTransform(0,0);
            javax.vecmath.Vector4d projected=names.projectLabel(IngameHudRenderCheck.player);
            ScaledResolution sr=new ScaledResolution(mc);
            require(projected!=null&&projected.x>=0&&projected.z<=sr.getScaledWidth()
                    &&projected.y>18&&projected.w<=sr.getScaledHeight(),
                    "Nametag fixture must project inside the visible framebuffer");
        }
        IngameHudRenderCheck.player.setHealth(20);
        BufferedImage full=render(names,"on2D","nametag-full.png");visible(full,"Independent Nametag did not paint");
        require(healthMotions().size()==1,"Expected one observed player health spring");
        HudSpring health=healthMotions().get(IngameHudRenderCheck.player);
        require(health!=null&&Math.abs(health.value()-1f)<.001f,"Initial health was not snapped");
        IngameHudRenderCheck.player.setHealth(8);
        SessionInfoRenderCheck.set(names,"lastHealthFrame",System.nanoTime()-16_666_667L);
        BufferedImage damage=render(names,"on2D","nametag-damage.png");
        require(health.value()>.4f&&health.value()<1f,"Damage did not interpolate toward actual health");
        require(!WatermarkCheck.equal(full,damage),"Actual health change did not update Nametag pixels");
        settings.reducedMotion.setValue(true);UiEffects.invalidate();
        render(names,"on2D","nametag-reduced.png");
        require(Math.abs(health.value()-.4f)<.001f,"Reduced motion did not snap Nametag health");
        settings.reducedMotion.setValue(false);UiEffects.invalidate();IngameHudRenderCheck.player.setHealth(16);
        SessionInfoRenderCheck.set(names,"lastHealthFrame",System.nanoTime()-16_666_667L);
        render(names,"on2D","nametag-regeneration.png");
        require(health.value()>.4f&&health.value()<.8f,"Regeneration did not interpolate toward actual health");
        settings.animations.setValue(false);UiEffects.invalidate();
        BufferedImage noDetails=render(names,"on2D",null);
        require(Math.abs(health.value()-.8f)<.001f,"Disabled motion did not snap Nametag health");
        names.getBooleanSetting("Player Head").setValue(true);names.getBooleanSetting("Distance").setValue(true);
        BufferedImage details=render(names,"on2D","nametag-head-distance.png");
        require(!WatermarkCheck.equal(noDetails,details),"Optional Nametag details have no visual effect");
        for(Theme theme:new Theme[]{Theme.PinkLemonade,Theme.SynthWave}) {
            Theme.setDefaultTheme(theme);
            for(boolean light:new boolean[]{false,true}) {
                bright=light;
                for(int[] size:new int[][]{{1100,640,2},{1001,701,3},{854,480,1}}) {
                    resize(size);
                    visible(render(names,"on2D","nametag-"+theme.name()+"-"+light+"-"+size[2]+".png"),"Missing Nametag after theme/scale/background change");
                }
            }
        }
        bright=false;resize(new int[]{1100,640,2});
        for(String mode:new String[]{"Flame","Neon","Lines","Static"}) {
            names.getModeSetting("Mode").setValue(mode);render(names,"on2D","nametag-mode-"+mode+".png");
        }
        warmResources(names,"on2D");
    }

    private static void checkKeystrokes()throws Exception {
        settings.reducedMotion.setValue(true);settings.animations.setValue(true);UiEffects.invalidate();
        KeyBinding[] bindings=bindings();
        for(String mode:new String[]{"Basic","Modern","Creative"}) {
            keys.getModeSetting("Mode").setValue(mode);keys.getBooleanSetting("Mouse Buttons").setValue(true);
            for(KeyBinding binding:bindings)binding.pressed=false;
            render(keys,"render",null);
            BufferedImage up=render(keys,"render","keys-"+mode+"-released.png");visible(up,"Keystrokes did not paint "+mode);
            for(KeyBinding binding:bindings)binding.pressed=true;
            BufferedImage down=render(keys,"render","keys-"+mode+"-pressed.png");
            require(!WatermarkCheck.equal(up,down),"Key presses have no visible state in "+mode);
            for(float press:(float[])SessionInfoRenderCheck.get(keys,"presses"))require(press==1f,"Reduced motion left stale input feedback in "+mode);
            keys.getBooleanSetting("Mouse Buttons").setValue(false);
            BufferedImage hidden=render(keys,"render","keys-"+mode+"-movement-only.png");
            require(!WatermarkCheck.equal(down,hidden),"Mouse Buttons setting ignored");keys.getBooleanSetting("Mouse Buttons").setValue(true);
            settings.surfaceOpacity.setValue(0.0);UiEffects.invalidate();
            BufferedImage transparent=render(keys,"render","keys-"+mode+"-opacity-zero.png");
            settings.surfaceOpacity.setValue(1.0);UiEffects.invalidate();
            BufferedImage opaque=render(keys,"render","keys-"+mode+"-opacity-one.png");
            require(!WatermarkCheck.equal(transparent,opaque),"Keystrokes ignores material opacity in "+mode);
        }
        settings.animations.setValue(false);settings.reducedMotion.setValue(false);UiEffects.invalidate();
        keys.getModeSetting("Mode").setValue("Basic");for(KeyBinding binding:bindings)binding.pressed=false;
        render(keys,"render",null);
        for(float press:(float[])SessionInfoRenderCheck.get(keys,"presses"))require(press==0f,"Disabled animation left stale Basic input feedback");
        for(int[] size:new int[][]{{1001,701,3},{854,480,1},{1100,640,2}}) {
            resize(size);keys.getModeSetting("Mode").setValue("Modern");
            keys.getNumberSetting("Position X").setValue(16384.0);keys.getNumberSetting("Position Y").setValue(16384.0);
            visible(render(keys,"render","keys-resize-"+size[2]+".png"),"Resized edge Keystrokes disappeared");
        }
        warmResources(keys,"render");
    }

    private static void checkDeferredHudCleanup()throws Exception {
        ScaledResolution sr=new ScaledResolution(mc);
        SessionInfo session=IngameHudRenderCheck.session;ArmorHUD armor=IngameHudRenderCheck.armor;
        for(String mode:new String[]{"Modern","Creative"}) {
            session.getModeSetting("Mode").setValue(mode);armor.getModeSetting("Mode").setValue(mode);
            SessionInfo.Frame sessionFrame=session.layout(SessionInfoRenderCheck.sample(),"Player93",sr.getScaledWidth(),sr.getScaledHeight());
            ArmorHUD.Frame armorFrame=armor.layout(mc.thePlayer.inventory.armorInventory,mc.thePlayer.getHeldItem(),sr.getScaledWidth(),sr.getScaledHeight());
            IngameHudRenderCheck.scene();BufferedImage before=SessionInfoRenderCheck.capture();
            GuiShaderBloom.beginBatch();session.paint(sessionFrame);armor.paint(armorFrame);
            invoke(session,"world",new WorldEvent.Unload(mc.theWorld));
            invoke(armor,"world",new WorldEvent.Unload(mc.theWorld));
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();GuiShaderBloom.flushBatch(2,2);
            require(WatermarkCheck.equal(before,SessionInfoRenderCheck.capture()),"A queued HUD frame repainted after world unload");
            require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"A stale callback recreated released HUD resources");
            RenderDiagnostics.enabled=false;
            GuiShaderBloom.beginBatch();
            session.paint(session.layout(SessionInfoRenderCheck.sample(),"Player93",sr.getScaledWidth(),sr.getScaledHeight()));
            armor.paint(armor.layout(mc.thePlayer.inventory.armorInventory,mc.thePlayer.getHeldItem(),sr.getScaledWidth(),sr.getScaledHeight()));
            GuiShaderBloom.flushBatch(2,2);
            require(!WatermarkCheck.equal(before,SessionInfoRenderCheck.capture()),"Fresh HUD frames stayed invalid after lifecycle reset");
        }
    }

    private static void checkLifecycle()throws Exception {
        mc.theWorld.loadedEntityList.remove(IngameHudRenderCheck.player);render(names,"on2D",null);
        require(healthMotions().isEmpty(),"Despawn retained health animation state");
        mc.theWorld.loadedEntityList.add(IngameHudRenderCheck.player);render(names,"on2D",null);
        require(healthMotions().size()==1,"Reappearing player was not reinitialized");
        invoke(names,"onWorldEvent",new WorldEvent.Unload(mc.theWorld));
        require(healthMotions().isEmpty(),"World unload retained Nametag entities");
        WorldClient oldWorld=mc.theWorld;
        try{mc.theWorld=null;render(names,"on2D",null);require(healthMotions().isEmpty(),"Absent world recreated stale animation state");}
        finally{mc.theWorld=oldWorld;}
        render(names,"on2D",null);names.setToggled(false);keys.setToggled(false);
        require(healthMotions().isEmpty(),"Disable retained Nametag animation state");
        for(float press:(float[])SessionInfoRenderCheck.get(keys,"presses"))require(press==0f,"Disable retained key presses");
    }

    private static BufferedImage render(Module module,String listener,String filename)throws Exception {
        IngameHudRenderCheck.scene();
        if(bright){glClearColor(.9f,.91f,.93f,1);glClear(GL_COLOR_BUFFER_BIT);}
        clean=SessionInfoRenderCheck.capture();
        GlStateManager.enableDepth();GlStateManager.depthMask(false);glEnable(GL_SCISSOR_TEST);glScissor(1,1,mc.displayWidth-2,mc.displayHeight-2);
        int framebuffer=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        invoke(module,listener,new Event2D(0,new ScaledResolution(mc)));
        require(glIsEnabled(GL_DEPTH_TEST)&&!glGetBoolean(GL_DEPTH_WRITEMASK)&&glIsEnabled(GL_SCISSOR_TEST)
                &&framebuffer==glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)
                &&UiEffects.scene()==null&&!GuiShaderBloom.isBatching(),"HUD render leaked depth/clip/FBO/scene state");
        glDisable(GL_SCISSOR_TEST);GlStateManager.disableDepth();GlStateManager.depthMask(true);
        BufferedImage image=SessionInfoRenderCheck.capture();
        if(filename!=null)ImageIO.write(image,"png",new File(OUT,filename));
        require(glGetError()==GL_NO_ERROR,"Native HUD paint error: "+filename);return image;
    }
    private static void visible(BufferedImage image,String message){require(!WatermarkCheck.equal(clean,image),message);}
    private static void resize(int[] size){mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);}
    private static void warmResources(Module module,String listener)throws Exception{
        for(int i=0;i<3;i++)render(module,listener,null);RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
        for(int i=0;i<4;i++)render(module,listener,null);
        require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Steady HUD draw allocated GPU resources");RenderDiagnostics.enabled=false;
    }
    private static void require(boolean condition,String message){SessionInfoRenderCheck.require(condition,message);}
    @SuppressWarnings("unchecked") private static Map<Entity,HudSpring> healthMotions()throws Exception{return (Map<Entity,HudSpring>)SessionInfoRenderCheck.get(names,"healthMotions");}
    @SuppressWarnings("unchecked") private static <T> void invoke(Module module,String field,T event)throws Exception{((Listener<T>)SessionInfoRenderCheck.get(module,field)).accept(event);}
    private static KeyBinding[] bindings(){return new KeyBinding[]{mc.gameSettings.keyBindForward,mc.gameSettings.keyBindLeft,mc.gameSettings.keyBindBack,mc.gameSettings.keyBindRight,mc.gameSettings.keyBindJump,mc.gameSettings.keyBindAttack,mc.gameSettings.keyBindUseItem};}
}
