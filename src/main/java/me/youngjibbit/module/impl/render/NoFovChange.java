package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "NoFovChange",
        description = "disables FOV Change",
        category = Category.Render
)
public final class NoFovChange extends Module
{

    private boolean oldFovSetting;

    public NoFovChange()
    {
        setKey(Keyboard.KEY_NONE);
    }

    @Override
    public void onEnable()
    {
        super.onEnable();
        oldFovSetting = mc.gameSettings.ofDynamicFov;
    }

    @Override
    public void onDisable()
    {
        super.onDisable();
        mc.gameSettings.ofDynamicFov = oldFovSetting;
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e ->
    {
        mc.gameSettings.ofDynamicFov = false;
    });
}
