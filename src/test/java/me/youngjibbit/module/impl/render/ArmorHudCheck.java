package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import java.io.File;
import java.lang.reflect.*;
import static org.lwjgl.opengl.GL11.*;
/** Actual vanilla model/atlas/item/glint path; no world or inventory is created. */
public final class ArmorHudCheck {
    public static void run()throws Exception{
        Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
        Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"Product-Sans-Bold 16","KhmerOureang 14"});
        SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"mcResourcePackRepository",new net.minecraft.client.resources.ResourcePackRepository(new File("armor-preview/packs"),new File("armor-preview/server-packs"),new net.minecraft.client.resources.DefaultResourcePack(java.util.Collections.emptyMap()),new net.minecraft.client.resources.data.IMetadataSerializer(),SessionInfoRenderCheck.mc.gameSettings));
        TextureManager textures=new TextureManager(SessionInfoRenderCheck.mc.getResourceManager());SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"renderEngine",textures);
        TextureMap atlas=new TextureMap("textures");atlas.setMipmapLevels(0);SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"textureMapBlocks",atlas);
        textures.loadTickableTexture(TextureMap.locationBlocksTexture,atlas);
        ModelManager models=new ModelManager(atlas);SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"modelManager",models);
        models.onResourceManagerReload(SessionInfoRenderCheck.mc.getResourceManager());
        RenderItem renderer=new RenderItem(textures,models);SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"renderItem",renderer);
        renderer.onResourceManagerReload(SessionInfoRenderCheck.mc.getResourceManager());
        ItemStack[] equipment={new ItemStack(Items.diamond_boots),new ItemStack(Items.diamond_leggings),null,new ItemStack(Items.diamond_helmet)};
        equipment[0].setItemDamage(300);equipment[3].addEnchantment(Enchantment.protection,3);
        ItemStack sword=new ItemStack(Items.diamond_sword);sword.addEnchantment(Enchantment.sharpness,5);sword.setItemDamage(800);
        if(Boolean.getBoolean("pulsar.armor.baseline")){
            Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)uf.get(null);
            net.minecraft.client.entity.EntityPlayerSP dummy=(net.minecraft.client.entity.EntityPlayerSP)unsafe.allocateInstance(net.minecraft.client.entity.EntityPlayerSP.class);
            dummy.inventory=new net.minecraft.entity.player.InventoryPlayer(dummy);dummy.inventory.armorInventory=equipment;dummy.inventory.mainInventory[0]=sword;SessionInfoRenderCheck.mc.thePlayer=dummy;
            ArmorHUD old=new ArmorHUD();SessionInfoRenderCheck.set(old,"x",30f);SessionInfoRenderCheck.set(old,"y",40f);SessionInfoRenderCheck.backdrop();old.renderArmorHUD();GuiShaderBloom.flushBatch(2,3);
            File out=new File("armor-preview");out.mkdirs();javax.imageio.ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(out,"before.png"));return;
        }
        String before=equipment[3].writeToNBT(new net.minecraft.nbt.NBTTagCompound()).toString();ArmorHUD hud=new ArmorHUD();SessionInfoRenderCheck.set(hud,"x",30f);SessionInfoRenderCheck.set(hud,"y",40f);
        File out=new File("armor-preview");out.mkdirs();
        for(String orientation:new String[]{"Vertical","Horizontal"})for(String mode:new String[]{"Percent","Value","Both","None"}){
            hud.getModeSetting("Layout").setValue(orientation);hud.getModeSetting("Durability").setValue(mode);
            ArmorHUD.Frame f=hud.layout(equipment,sword,550,320);
            SessionInfoRenderCheck.require(f.cells.size()==5&&f.cells.get(0).item.getItem()==Items.diamond_helmet&&f.cells.get(1).item==null&&f.cells.get(3).item.getItem()==Items.diamond_boots,"Slot mapping/empty slot");
            SessionInfoRenderCheck.require(f.x+f.width*f.scale<=546&&f.y+f.height*f.scale<=316,"HUD bounds");
            SessionInfoRenderCheck.backdrop();GlStateManager.enableLight(0);GlStateManager.enableColorMaterial();GlStateManager.enableRescaleNormal();GlStateManager.depthFunc(GL_GREATER);GlStateManager.depthMask(false);GlStateManager.alphaFunc(GL_LESS,.35f);renderer.zLevel=17;
            GuiShaderBloom.beginBatch();hud.paint(f);GuiShaderBloom.flushBatch(2,3);
            SessionInfoRenderCheck.require(glIsEnabled(GL_LIGHT0)&&glIsEnabled(GL_COLOR_MATERIAL)&&glIsEnabled(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL)&&glGetInteger(GL_DEPTH_FUNC)==GL_GREATER&&!glGetBoolean(GL_DEPTH_WRITEMASK)&&Math.abs(glGetFloat(GL_ALPHA_TEST_REF)-.35f)<.001&&renderer.zLevel==17,"Item render state leaked");
            // Force cache transitions to prove restored raw flags and GlStateManager agree.
            GlStateManager.disableLight(0);GlStateManager.disableColorMaterial();GlStateManager.disableRescaleNormal();GlStateManager.depthFunc(GL_LEQUAL);GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL_GREATER,.1f);
            if(mode.equals("Both"))javax.imageio.ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(out,orientation+".png"));
        }
        SessionInfoRenderCheck.require(before.equals(equipment[3].writeToNBT(new net.minecraft.nbt.NBTTagCompound()).toString())&&sword.getItemDamage()==800&&equipment[0].getItemDamage()==300,"Rendering mutated equipment");
        hud.getBooleanSetting("Empty Slots").setValue(false);hud.getBooleanSetting("Held Item").setValue(false);
        SessionInfoRenderCheck.require(hud.layout(new ItemStack[4],null,320,240).cells.isEmpty(),"Empty list");
        hud.getNumberSetting("Scale").setValue(1.6);ArmorHUD.Frame f=hud.layout(equipment,sword,100,80);SessionInfoRenderCheck.require(f.width*f.scale<=92&&f.height*f.scale<=72,"Scale-to-fit");
        float old=f.x;hud.interact(false,true,f.x+2,f.y+2,100,80);hud.interact(true,true,f.x+20,f.y+10,100,80);SessionInfoRenderCheck.require(hud.layout(equipment,sword,100,80).x==old,"Non-chat drag armed");
        hud.onDisable();SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Armor GL error");System.out.println("PASS: actual item models/glint; slot order, empty slots, damage modes, both layouts, fit/drag, immutable equipment, item GL/cache/z restoration");
    }
}
