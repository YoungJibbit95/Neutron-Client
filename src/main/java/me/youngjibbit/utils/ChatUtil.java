package me.youngjibbit.utils;

import me.youngjibbit.Pulsar;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;



public final class ChatUtil
{
    public static final String fix(String string)
    {
        return string.replace("&", "§")
                .replace(">>", "»")
                .replace("<<", "«")
                .replace("->", "→")
                .replace("<-", "←");
    }

    public static void sendMessage(String msg)
    {
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(fix(msg)));
    }

    public static void addChatMessage(final String msg, final boolean prefix)
    {
        if (prefix) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(fix(Pulsar.INSTANCE.getClientPrefix()) + fix(msg)));
        } else {
            addChatMessage(msg, false);
        }
    }
}
