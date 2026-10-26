package me.youngjibbit.module.impl.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.PacketUtils;
import me.youngjibbit.utils.player.CombatUtil;
import me.youngjibbit.utils.player.MoveUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;

@ModuleInfo(name = "MoreKB", category = Category.Combat)
public class MoreKB extends Module
{
    private final ComboValue mode = new ComboValue("Mode",this, new String[]{"Legit Fast", "Packet"}, 1);
    private final CheckBoxValue onlyGround = new CheckBoxValue("Only Ground", this, true);
    public int ticks;

    private EntityLivingBase target = null;

    @Subscribe
    public final Listener<AttackEvent> onAttack = new Listener<>(event -> {
        if (event.getTargetEntity() != null && event.getTargetEntity() instanceof EntityLivingBase) {
            EntityLivingBase t = event.getTargetEntity();
            target = t;
            ticks = 2;
        }
    });

    @Subscribe
    public final Listener<EventUpdate> onUpdate = new Listener<>(event -> {
        if (target != null && MoveUtil.hasMovementInput(mc.thePlayer)) {
            if ((onlyGround.getValue() && mc.thePlayer.onGround || !onlyGround.getValue())) {
                switch (mode.getValue()) {
                    case "Legit Fast":
                        mc.thePlayer.sprintingTicksLeft = 0;
                        Pulsar.INSTANCE.debugMessage("sprint ticks: " + mc.thePlayer.sprintingTicksLeft);
                        break;
                    case "Packet":
                        PacketUtils.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                        Pulsar.INSTANCE.debugMessage("stopped sprinting");
                        PacketUtils.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                        break;
                }
            }
            target = null;
        }
    });
}
