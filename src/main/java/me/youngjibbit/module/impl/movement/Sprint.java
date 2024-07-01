package me.youngjibbit.module.impl.movement;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.entity.EntityPlayerSP;
import org.lwjgl.input.Keyboard;

import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;

@ModuleInfo(
        name = "Sprint",
        description = "Lets you sprint automatically",
        category = Category.Movement
)
public final class Sprint extends Module
{
    public Sprint()
    {
        setKey(Keyboard.KEY_L);
    }

    @Override
    public void onEnable()
    {
        super.onEnable();
    }

    public void onDisable()
    {
        super.onDisable();
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e -> {
        if (mc.thePlayer.isCollidedHorizontally) return;
        if (mc.thePlayer.moveForward < 0) return;
        if (mc.thePlayer.isUsingItem()) return;
        if (mc.thePlayer.isSneaking()) return;
        mc.thePlayer.setSprinting(true);
    });
}
