package me.youngjibbit.utils;

import me.youngjibbit.Pulsar;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public final class ChatUtil {

    private ChatUtil() {
    }

    public static String fix(String string) {
        if (string == null) {
            return "";
        }

        return string.replace("&", "\u00A7")
                .replace(">>", "\u00BB")
                .replace("<<", "\u00AB")
                .replace("->", "\u2192")
                .replace("<-", "\u2190");
    }

    public static void sendMessage(String msg) {
        if (Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(fix(msg)));
        }
    }

    public static void addChatMessage(final String msg, final boolean prefix) {
        if (Minecraft.getMinecraft().thePlayer == null) {
            return;
        }

        String text = prefix ? fix(Pulsar.INSTANCE.getClientPrefix()) + fix(msg) : fix(msg);
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(text));
    }
}
