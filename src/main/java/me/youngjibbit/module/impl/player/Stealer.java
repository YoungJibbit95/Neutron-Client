package me.youngjibbit.module.impl.player;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.TimerUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ModuleInfo(
        name = "Stealer",
        description = "Steals items from chests with delays",
        category = Category.Player
)
public class Stealer extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil openDelayTimer = new TimerUtil();
    private final TimerUtil actionTimer = new TimerUtil();

    private final SliderValue delay = new SliderValue("Delay", this, 100.0, 0.0, 500.0);
    private final CheckBoxValue autoclose = new CheckBoxValue("Auto Close", this, true);
    private final SliderValue openDelay = new SliderValue("Open Delay", this, 500.0, 0.0, 1000.0, true);
    private final SliderValue autocloseDelay = new SliderValue("Close Delay", this, 300.0, 0.0, 500.0, true);
    private final CheckBoxValue randomItem = new CheckBoxValue("Randomness", this, true);

    public Stealer() {
        super.setKey(Keyboard.KEY_V);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.currentScreen instanceof GuiChest) {
            openDelayTimer.reset();
            actionTimer.reset();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetStealer();
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e -> {
        if (mc.thePlayer.openContainer != null && mc.thePlayer.openContainer instanceof ContainerChest) {
            ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;
            if (!openDelayTimer.hasTimeElapsed(openDelay.getValue().longValue(), false)) {
                return;
            }
            List<Integer> slots = getSlotsToSteal(chest);
            for (int slot : slots) {
                if (chest.getLowerChestInventory().getStackInSlot(slot) != null
                        && actionTimer.hasTimeElapsed(delay.getValue().longValue(), true)) {
                    mc.playerController.windowClick(chest.windowId, slot, 0, 1, mc.thePlayer);
                }
            }
            if (empty(chest) && autoclose.getValue()
                    && actionTimer.hasTimeElapsed(autocloseDelay.getValue().longValue(), true)) {
                mc.thePlayer.closeScreen();
            }
        } else {
            openDelayTimer.reset();
            actionTimer.reset();
        }
    });

    private boolean empty(Container c2) {
        int slot = c2.inventorySlots.size() == 90 ? 54 : 27;
        for (int i2 = 0; i2 < slot; ++i2) {
            if (!c2.getSlot(i2).getHasStack()) continue;
            return false;
        }
        return true;
    }
    private List<Integer> getSlotsToSteal(ContainerChest chest) {
        int size = chest.getLowerChestInventory().getSizeInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        if (randomItem.getValue()) {
            Collections.shuffle(slots);
        }

        return slots;
    }

    private void resetStealer() {
        openDelayTimer.reset();
        actionTimer.reset();
    }
}
