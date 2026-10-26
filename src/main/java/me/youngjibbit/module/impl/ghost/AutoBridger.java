package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name =  "AutoBridger", category = Category.Ghost)
public class AutoBridger
        extends Module
{
    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e-> {
        this.mc.gameSettings.keyBindSneak.pressed = this.playerOverAir();
    });

    public AutoBridger() {
        super.setKey(Keyboard.KEY_G);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.mc.gameSettings.keyBindSneak.pressed = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.mc.gameSettings.keyBindSneak.pressed = false;
    }

    private boolean playerOverAir() {
        double x = this.mc.thePlayer.posX;
        double y = this.mc.thePlayer.posY - 1.0;
        double z = this.mc.thePlayer.posZ;
        BlockPos p = new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
        return this.mc.theWorld.isAirBlock(p);
    }
}
