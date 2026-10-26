package me.youngjibbit.utils.render;

import me.youngjibbit.Pulsar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

public class PlayerHeadDrawer {
    private static final Minecraft mc = Pulsar.INSTANCE.getMc();

    public static void drawPlayerHead(int x, int y, int width, EntityLivingBase player) {
        NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (playerInfo != null) {
            mc.getTextureManager().bindTexture(playerInfo.getLocationSkin());
            GL11.glColor4f(1F, 1F, 1F, 1F);
            Gui.drawScaledCustomSizeModalRect(x, y, 8F, 8F, 8, 8, width, width, 64F, 64F);
        }
    }

    public static void drawPlayerHead(int x, int y, int width) {
        if (mc.thePlayer != null) {
            GlStateManager.pushMatrix();
            float scale = width / 32.0f;
            GlStateManager.scale(scale, scale, scale);
            mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
            GL11.glEnable(GL11.GL_BLEND);
            Gui.drawScaledCustomSizeModalRect((int) (x / scale), (int) (y / scale), 8F, 8F, 8, 8, 32, 32, 64F, 64F);
            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.popMatrix();
        }
    }
}
