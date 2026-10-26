package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "JumpReset", description = "Jumps for you when you get hit", category = Category.Ghost)
public class JumpReset extends Module
{
    public SliderValue jumpChance = new SliderValue("Jump Chance", this, 60.0, 1, 100, true);

    public JumpReset()
    {
        super.setKey(Keyboard.KEY_0);
    }

    @Override
    public void onEnable()
    {
        super.onEnable();
    }

    @Override
    public void onDisable()
    {
        super.onDisable();
    }

    @Subscribe
    private final Listener<EventPacket> onPacket = new Listener<>(e->
    {
        if (!e.isPre() || !e.isInbound()) {
            return;
        }

        if (jumpChance.getValue() != 100.0D)
        {
            if (Math.random() >= jumpChance.getValue() / 100.0D)
            {
                return;
            }
        }
        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            if (mc.thePlayer == null) {
                return;
            }
            if (((S12PacketEntityVelocity) e.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
                if(mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                }
            }
        }
    });
}
