package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Velocity;
import me.zero.alpine.event.EventPhase;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VelocityCombatTest {
    @Test public void actualWalkingHookAttacksOnlyAfterAcceptedRayAndSharesTheTickBudget() throws Exception {
        check(false);
    }
    @Test public void itemUsePreventsReductionAttackEvenWithAnAcceptedRotation() throws Exception {
        check(true);
    }
    @SuppressWarnings("unchecked") private void check(boolean usingItem) throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.Scene scene = ScaffoldClientFlowTest.prepare(f, .5, .5);
            f.aura.setToggled(false); f.scaffold.setToggled(false);
            f.player.usingItem = usingItem;
            f.player.setEntityId(1);
            RotationBehaviorHookTest.Player target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
            target.worldObj = scene; target.setEntityId(7); target.posX = .5; target.posY = 64; target.posZ = 2.5;
            target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, 2.2, .8, 65.8, 2.8));
            RotationBehaviorHookTest.field(World.class, "loadedEntityList").set(scene, new ArrayList<Entity>(Arrays.asList(f.player, target)));
            RotationBehaviorHookTest.field(net.minecraft.client.multiplayer.PlayerControllerMP.class, "currentGameType")
                    .set(f.mc.playerController, WorldSettings.GameType.SPECTATOR);
            Velocity velocity = new Velocity();
            Map<Class<? extends Module>, Module> modules = (Map<Class<? extends Module>, Module>) RotationBehaviorHookTest
                    .field(ModuleManager.class, "modulesByClass").get(Pulsar.INSTANCE.getMm());
            modules.put(Velocity.class, velocity); velocity.setToggled(true);
            try {
                f.runtime.getAttackScheduler().beginTick();
                EventPacket applied = new EventPacket(new S12PacketEntityVelocity(1, .2, .2, 0), f.network.getClientPackets());
                applied.setEventFlow(EventFlow.INBOUND); applied.setEventPhase(EventPhase.POST); Pulsar.BUS.post(applied);
                ScaffoldClientFlowTest.ordinaryWalking(f);
                assertTrue(f.runtime.getRotationManager().isMotionAccepted());
                assertEquals("velocity", f.runtime.getRotationManager().getConfirmedOwner());
                int attacks = 0; Object packet;
                while ((packet = f.channel.readOutbound()) != null) if (packet instanceof C02PacketUseEntity) attacks++;
                assertEquals(usingItem ? 0 : 1, attacks);
                ScaffoldClientFlowTest.ordinaryWalking(f);
                while ((packet = f.channel.readOutbound()) != null) assertFalse(packet instanceof C02PacketUseEntity);
                assertEquals(0, f.player.rotationYaw, 0);
            } finally { velocity.setToggled(false); }
        }
    }
}
