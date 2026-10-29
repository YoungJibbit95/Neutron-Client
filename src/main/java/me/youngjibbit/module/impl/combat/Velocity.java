package me.youngjibbit.module.impl.combat;

import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.MathUtils;
import me.youngjibbit.utils.player.PlayerUtil;
import me.youngjibbit.utils.player.MoveUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;

@ModuleInfo(name = "Velocity", category = Category.Combat)
public class Velocity extends Module
{
    public ComboValue mode = new ComboValue("Mode", this, new String[]{"Jump", "Packet", "Verus", "Ground Grim", "Minemen", "Intave", "Intave2"}, 1);
    public CheckBoxValue notWhileSpeed = new CheckBoxValue("Not while Speed", this, true);
    public CheckBoxValue notWhileJumpBoost = new CheckBoxValue("Not while Jump", this, true);
    public CheckBoxValue reduceUnnecessarySlowdown = new CheckBoxValue("Reduce Slowdown", this, true);
    public CheckBoxValue jump = new CheckBoxValue("Toggle Jump", this, true);
    public CheckBoxValue jumpInInv = new CheckBoxValue("Inventory Jump", this, false);
    public SliderValue xzOnHit = new SliderValue("XZ on Hit", this, 0.6, 0.0, 1.0, false);
    public SliderValue xzOnSprintHit = new SliderValue("XZ on Sprint Hit", this, 0.6, 0.0, 1.0, false);
    public SliderValue jumpChance = new SliderValue("Jump Chance", this, 60.0, 1, 100, true);
    public SliderValue horizontal = new SliderValue("X", this, 100.0, 0, 100, true);
    public SliderValue vertical = new SliderValue("Y", this, 100.0, 0, 100, true);
    private boolean reset;
    private int timerTicks = 0, intaveTick = 0, ticks = 0;
    private boolean reduced = false;

    public Velocity()
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
        mc.timer.timerSpeed = 1.0f;
        timerTicks = 0;
        intaveTick = 0;
        ticks = 0;
        reset = false;
        super.onDisable();
    }

    @Subscribe
    private final Listener<AttackEvent> onAttack = new Listener<>(e->
    {
        switch (mode.getValue())
        {
            case "Intave2":
                if (e.getTargetEntity() instanceof EntityLivingBase && mc.thePlayer.hurtTime > 0) {
                    if (noAction()) return;
                    if (Math.random() > jumpChance.getValue() / 100) return;
                    if (reduceUnnecessarySlowdown.getValue() && reduced) return;

                    if (mc.thePlayer.isSprinting()) {
                        mc.thePlayer.motionX *= xzOnSprintHit.getValue();
                        mc.thePlayer.motionZ *= xzOnSprintHit.getValue();
                    } else {
                        mc.thePlayer.motionX *= xzOnHit.getValue();
                        mc.thePlayer.motionZ *= xzOnHit.getValue();
                    }
                    reduced = true;
                        PlayerUtil.send(String.format("Reduced %.3f %.3f", mc.thePlayer.motionX, mc.thePlayer.motionZ));
                }
                break;
        }
    });

    @Subscribe
    private final Listener<KnockbackEvent> onKB = new Listener<>(e ->
    {
        if (!PlayerUtil.inGame()) {
            return;
        }

        if (jumpChance.getValue() != 100.0D && Math.random() >= jumpChance.getValue() / 100.0D) {
            return;
        }

        String mode = this.mode.getValue();
        switch (mode) {
            case "Jump":
                if (e.isPre() && mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                }
                break;

            case "Packet":
                e.setX(e.getX() * horizontal.getValue() / 100.0);
                e.setY(e.getY() * vertical.getValue() / 100.0);
                e.setZ(e.getZ() * horizontal.getValue() / 100.0);
                break;

            case "Ground Grim":
                if (MoveUtil.hasMovementInput(mc.thePlayer) && mc.thePlayer.onGround) {
                    e.setCancelled(true);
                    reset = true;
                }
                break;

            case "Intave":
                reset = true;
                break;

            case "Intave2":
                if(e.isPost())
                {
                    if (noAction()) return;

                    if (jump.getValue())
                    {
                        if (Math.random() > jumpChance.getValue() / 100) return;

                        if (mc.thePlayer.onGround && (jumpInInv.getValue() || mc.currentScreen == null))
                            mc.thePlayer.jump();
                    }
                    reduced = false;
                }
                break;

            case "Minemen":
                if (ticks > 14) {
                    adjustKnockBack(e, 1 / 42000.0, 1 / 8000.0);
                    e.setCancelled(true);
                    ticks = 0;
                }
                break;
            default:
                break;
        }
    });

    @Subscribe
    private final Listener<TickEvent> onTick = new Listener<>(e ->
    {
        switch (mode.getValue())
        {
            case "Ground Grim":
                if (timerTicks > 0 && mc.timer.timerSpeed <= 1) {
                    float timerSpeed = 0.8f + (0.2f * (20 - timerTicks) / 20);
                    mc.timer.timerSpeed = Math.min(timerSpeed, 1f);
                    timerTicks--;
                } else if (mc.timer.timerSpeed <= 1) {
                    mc.timer.timerSpeed = 1f;
                }

                if (reset) {
                    if (checkAir(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ))) {
                        reset = false;
                    }
                }
        }
    });

    @Subscribe
    private final Listener<EventMotion> onMotion = new Listener<>(e ->
    {
        if (!PlayerUtil.inGame() || !e.isPre()) {
            return;
        }

        String mode = this.mode.getValue();
        switch (mode) {
            case "Verus":
                if (mc.thePlayer.hurtTime == 10 - MathUtils.getRandomFloat(3, 4)) {
                    mc.thePlayer.motionX = 0.0D;
                    mc.thePlayer.motionY = 0.0D;
                    mc.thePlayer.motionZ = 0.0D;
                }
                break;
            case "Intave":
                intaveTick++;
                if (reset && mc.thePlayer.hurtTime == 2) {
                    if (mc.thePlayer.onGround && intaveTick % 2 == 0) {
                        mc.thePlayer.jump();
                        intaveTick = 0;
                    }
                    reset = false;
                }
                break;
        }
    });

    private boolean checkAir(BlockPos blockPos) {
        if (mc.theWorld == null)
            return false;

        if (!mc.theWorld.isAirBlock(blockPos))
            return false;

        timerTicks = 20;

        if (mc.thePlayer != null) {
            mc.getNetHandler().sendSilent(new C03PacketPlayer(true));
            mc.getNetHandler().sendSilent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockPos, EnumFacing.DOWN));
        }

        mc.theWorld.setBlockToAir(blockPos);

        return true;
    }

    private void adjustPlayerMovement(Consumer<EntityPlayerSP> adjuster) {
        adjuster.accept(mc.thePlayer);
    }

    private void adjustKnockBack(KnockbackEvent e, double horizontalFactor, double verticalFactor) {
        e.setX(e.getX() * horizontalFactor / 100.0);
        e.setY(e.getY() * verticalFactor / 100.0);
        e.setZ(e.getZ() * horizontalFactor / 100.0);
    }

    private boolean noAction() {
        for (net.minecraft.potion.PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
            if ((notWhileSpeed.getValue() && effect.getPotionID() == Potion.moveSpeed.getId())
                    || (notWhileJumpBoost.getValue() && effect.getPotionID() == Potion.jump.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidMotion(double motion, double min, double max) {
        return Math.abs(motion) > min && Math.abs(motion) < max;
    }
}
