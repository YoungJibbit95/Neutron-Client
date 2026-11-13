package me.youngjibbit.module.impl.render;

import me.youngjibbit.utils.render.RenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.EXTFramebufferObject;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/** Actual player/armor/held-item layers, on the scene and GlowESP's capture target. */
final class LivingLayersRenderCheck {
    @SuppressWarnings({"rawtypes","unchecked"}) static void run() throws Exception {
        Minecraft mc=IngameHudRenderCheck.mc;
        GlowESP glow=IngameHudRenderCheck.glow;
        IngameHudRenderCheck.Player player=(IngameHudRenderCheck.Player)IngameHudRenderCheck.player;
        ItemStack[] savedArmor=player.inventory.armorInventory.clone();
        ItemStack savedHeld=player.inventory.mainInventory[player.inventory.currentItem];
        boolean enabled=glow.isToggled();String mode=glow.getModeSetting("Glow Mode").getValue();
        int width=mc.displayWidth,height=mc.displayHeight,scale=mc.gameSettings.guiScale;
        RendererLivingEntity renderer=(RendererLivingEntity)mc.getRenderManager().getEntityRenderObject(player);
        List<Integer> targets=new ArrayList<>();
        LayerRenderer<EntityLivingBase> observer=new LayerRenderer<EntityLivingBase>() {
            @Override public void doRenderLayer(EntityLivingBase entity,float limb,float amount,float partial,
                                               float age,float yaw,float pitch,float modelScale) {
                targets.add(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT));
            }
            @Override public boolean shouldCombineTextures(){return false;}
        };
        renderer.getLayerRenderers().add(observer);
        List<BufferedImage> pictures=new ArrayList<>();List<String> captions=new ArrayList<>();
        try {
            for(int[] viewport:new int[][]{{854,480,1},{1101,733,3}}) {
                mc.displayWidth=viewport[0];mc.displayHeight=viewport[1];mc.gameSettings.guiScale=viewport[2];
                mc.getFramebuffer().createBindFramebuffer(mc.displayWidth,mc.displayHeight);
                Arrays.fill(player.inventory.armorInventory,null);player.inventory.mainInventory[player.inventory.currentItem]=null;
                glow.setToggled(false);targets.clear();BufferedImage naked=draw(false);
                require(targets.size()==1&&targets.get(0)==mc.getFramebuffer().framebufferObject,"Normal living layers must run exactly once with GlowESP disabled");
                player.inventory.armorInventory[0]=new ItemStack(Items.diamond_boots);
                player.inventory.armorInventory[1]=new ItemStack(Items.diamond_leggings);
                player.inventory.armorInventory[2]=new ItemStack(Items.diamond_chestplate);
                player.inventory.armorInventory[3]=new ItemStack(Items.diamond_helmet);
                player.inventory.mainInventory[player.inventory.currentItem]=new ItemStack(Items.diamond_sword);
                targets.clear();BufferedImage armored=draw(false);
                require(differences(naked,armored)>120,"Actual armor/held-item layers did not change the visible player");
                require(targets.size()==1,"Normal layers were duplicated");
                pictures.add(crop(naked));captions.add("GUI scale "+viewport[2]+" / player without equipment");
                pictures.add(crop(armored));captions.add("GUI scale "+viewport[2]+" / diamond armor + held item");
                player.inventory.armorInventory[3].addEnchantment(Enchantment.protection,3);
                BufferedImage enchanted=draw(false);
                require(differences(armored,enchanted)>8,"Visible armor glint disappeared");
                for(String selected:new String[]{"Standard","Flame","Neon","Lines"}) {
                    glow.getModeSetting("Glow Mode").setValue(selected);glow.setToggled(true);targets.clear();
                    BufferedImage captured=draw(true);
                    require(WatermarkCheck.equal(enchanted,captured),"GlowESP "+selected+" changed the visible armor/glint/held-item pass");
                    require(targets.size()==2&&targets.get(0)==glow.framebuffer.framebufferObject
                                    &&targets.get(1)==mc.getFramebuffer().framebufferObject,
                            "Expected one offscreen layer capture followed by exactly one scene layer pass");
                    if("Standard".equals(selected)){pictures.add(crop(captured));captions.add("GUI scale "+viewport[2]+" / GlowESP capture, armor and glint retained");}
                }
                glow.setToggled(false);targets.clear();BufferedImage disabled=draw(false);
                require(WatermarkCheck.equal(enchanted,disabled)&&targets.size()==1,"Disabling GlowESP left the scene layers altered");
            }
            SessionInfoRenderCheck.sheet("release-living-layers-resize.png",captions,pictures,true);
            require(glGetError()==GL_NO_ERROR,"Living layer regression left an OpenGL error");
            System.out.println("PASS: actual scene armor, enchantment glint and held item; one scene layer pass plus one GlowESP capture in all four modes, GUI scales 1/3 and toggle restoration");
        } finally {
            renderer.getLayerRenderers().remove(observer);
            System.arraycopy(savedArmor,0,player.inventory.armorInventory,0,savedArmor.length);
            player.inventory.mainInventory[player.inventory.currentItem]=savedHeld;
            glow.getModeSetting("Glow Mode").setValue(mode);glow.setToggled(enabled);
            mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;
            mc.getFramebuffer().createBindFramebuffer(width,height);
        }
    }
    private static BufferedImage draw(boolean capture) {
        Minecraft mc=IngameHudRenderCheck.mc;
        IngameHudRenderCheck.scene();
        try(RenderState state=new RenderState()) {
            if(capture){IngameHudRenderCheck.glow.createFrameBuffers();IngameHudRenderCheck.glow.collectEntities();}
            mc.getFramebuffer().bindFramebuffer(true);
            IngameHudRenderCheck.drawModel(IngameHudRenderCheck.player,150,50,-10);
        }
        return SessionInfoRenderCheck.capture();
    }
    private static BufferedImage crop(BufferedImage image) {
        int scale=new ScaledResolution(IngameHudRenderCheck.mc).getScaleFactor();
        return image.getSubimage(105*scale,50*scale,90*scale,115*scale);
    }
    private static int differences(BufferedImage a,BufferedImage b) {
        int changed=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;
        return changed;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
