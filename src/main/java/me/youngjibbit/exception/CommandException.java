package me.youngjibbit.exception;

import me.youngjibbit.utils.ChatUtil;
import net.minecraft.util.EnumChatFormatting;

public class CommandException extends IllegalArgumentException
{
    public CommandException(String message)
    {
        super(message);
        ChatUtil.addChatMessage(EnumChatFormatting.RED + message, true);
    }
}
