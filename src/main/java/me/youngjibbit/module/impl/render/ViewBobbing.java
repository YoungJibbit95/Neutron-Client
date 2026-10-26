package me.youngjibbit.module.impl.render;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "VB",
        description = "disables ViewBobbing",
        category = Category.Render
)
public final class ViewBobbing extends Module
{

    private boolean oldVBSetting;

    public ViewBobbing()
    {
        setKey(Keyboard.KEY_NONE);
    }

    @Override
    public void onEnable()
    {
        super.onEnable();
        oldVBSetting = mc.gameSettings.viewBobbing;
    }

    @Override
    public void onDisable()
    {
        super.onDisable();
        mc.gameSettings.viewBobbing = oldVBSetting;
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e ->
    {
        mc.gameSettings.viewBobbing = false;
    });
}
