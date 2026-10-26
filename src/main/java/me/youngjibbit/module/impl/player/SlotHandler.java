package me.youngjibbit.module.impl.player;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.Wrapper;
import me.youngjibbit.utils.CoolDown;
import me.youngjibbit.utils.player.PlayerUtil;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

@ModuleInfo(name = "SlotHandler", category = Category.Player)
public class SlotHandler extends Module
{
    private static final AtomicInteger currentSlot = new AtomicInteger(-1);
    private static final CoolDown coolDown = new CoolDown(-1);
    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Default", "Silent"}, 1);
    private final SliderValue switchBackDelay = new SliderValue("Switch back delay",this, 50.0, 10, 1000, true);

    public static int getCurrentSlot()
    {
        if (isSilentSlot())
            return currentSlot.get();
        return Wrapper.mc.thePlayer.inventory.currentItem;
    }

    public static void setCurrentSlot(int slot)
    {
        if (slot >= 0 && slot < 9)
        {
            currentSlot.set(slot);
            coolDown.start();
        }
    }

    public static @Nullable ItemStack getHeldItem()
    {
        final InventoryPlayer inventory = Wrapper.mc.thePlayer.inventory;
        if (isSilentSlot())
            return inventory.mainInventory[currentSlot.get()];
        return getRenderHeldItem();
    }

    public static @Nullable ItemStack getRenderHeldItem()
    {
        final InventoryPlayer inventory = Wrapper.mc.thePlayer.inventory;
        return inventory.currentItem < 9 && inventory.currentItem >= 0 ? inventory.mainInventory[inventory.currentItem] : null;
    }

    public static boolean isSilentSlot()
    {
        return currentSlot.get() != -1;
    }

    public static void resetSlot()
    {
        currentSlot.set(-1);
    }

    @Subscribe(priority = EventPriority.LOWEST)
    public void onPreUpdate(EventUpdate event)
    {
        if(event.isPre())
        {
            switch (mode.getValue())
            {
                case "Default":
                    Wrapper.mc.thePlayer.inventory.currentItem = getCurrentSlot();
                    resetSlot();
                    break;
                case "Silent":
                    coolDown.setCooldown((long) switchBackDelay.getValue().intValue());
                    if (isSilentSlot()
                            && !mc.playerController.func_181040_m()
                            && coolDown.hasFinished())
                        resetSlot();
                    break;
            }
        }
    }
}
