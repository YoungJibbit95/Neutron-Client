package me.youngjibbit.module.impl.combat;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "AntiBot",
        description = "Watchdog Antibot",
        category = Category.Combat
)

public class AntiBot extends Module
{
    public AntiBot()
    {
        setKey(Keyboard.KEY_NONE);
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
    });
}
