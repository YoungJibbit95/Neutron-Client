package me.youngjibbit.command.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandInfo;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.player.InventoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;

/** Opens the local item filter editor; it never sends a chat message or inventory action. */
@CommandInfo(name="trash",description="Search items and edit inventory trash filters",usage="trash",aliases={"trashmenu"})
public final class TrashCommand extends Command {
    @Override public void execute(String... args) throws CommandException {
        if(args.length!=0)throw new CommandException("Usage: .trash");
        ModuleManager modules=Pulsar.INSTANCE.getMm();
        InventoryManager inventory=modules==null?null:(InventoryManager)modules.getModule(InventoryManager.class);
        if(inventory==null)throw new CommandException("Inventory settings are not ready yet.");
        Minecraft client=Minecraft.getMinecraft();
        inventory.openTrashMenu(client.currentScreen instanceof GuiChat?null:client.currentScreen);
    }
}
