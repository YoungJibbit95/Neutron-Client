package me.youngjibbit.module.impl.player;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "Fast Place",
        description = "increase block placing speed",
        category = Category.Player
)
public class FastPlace extends Module
{
    public FastPlace()
    {
        setKey(Keyboard.KEY_NONE);
    }



    @Override
    public void onEnable()
    {
        super.onEnable();
        if(this.isToggled()) {
            mc.rightClickDelayTimer = 0;
        }
    }

    @Override
    public void onDisable()
    {
        super.onDisable();
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e ->
    {
        if(this.isToggled()) {
            mc.rightClickDelayTimer = 0;
        }
    });
}
