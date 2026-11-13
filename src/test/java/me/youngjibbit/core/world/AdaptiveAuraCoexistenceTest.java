package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketDelays;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** Real Adaptive selection, walking tickets and controller actions with the timing/placement consumers.
 * Reuses the existing synthetic world/input fixture; no selected Aura target is injected. */
public class AdaptiveAuraCoexistenceTest {
    @Test public void tickbaseWindowsRequireFreshAcceptedAuraMotion() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "tickbaseWindowsRequireFreshAcceptedAuraMotion");
    }
    @Test public void lagRangeHeldWalkingCannotAuthorizeAnAuraAttack() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "lagRangeHeldWalkingCannotAuthorizeAnAuraAttack");
    }
    @Test public void backtrackFollowsRealAuraSelectionAndReleasesOnTargetLoss() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "backtrackFollowsRealAuraSelectionAndReleasesOnTargetLoss");
    }
    @Test public void scaffoldRevokesActiveAndPendingAuraSwordUse() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "scaffoldRevokesActiveAndPendingAuraSwordUse");
    }
    @Test public void manualSlotChangeDoesNotReuseOldAuraBlockOwnership() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "manualSlotChangeDoesNotReuseOldAuraBlockOwnership");
    }

    private static final class Run implements AutoCloseable {
        final AuraMovementConsistencyTest.Run body = new AuraMovementConsistencyTest.Run(
                "Einfach", AuraMovementConsistencyTest.SwordPlayer.class);
        final AtomicLong now = new AtomicLong(1_000_000_000L);
        final Aura aura = body.f.run.fixture.aura;
        final Scaffold scaffold = body.f.run.fixture.scaffold;
        final PacketPipeline pipe = body.f.run.fixture.network.getClientPackets();
        final RotationManager rotations = body.f.run.fixture.runtime.getRotationManager();
        final ItemStack sword = new ItemStack(Items.iron_sword);
        final EntityLivingBase target;

        Run() throws Exception {
            body.remoteTarget();
            target = (EntityLivingBase) body.f.run.scene.getEntityByID(19);
            body.input.forward = 0;
            body.player.inventory.mainInventory[0] = sword;
            // The movement fixture replaces InventoryPlayer when installing SwordPlayer;
            // seed its block slot as well as the original scaffold fixture's inventory.
            body.player.inventory.mainInventory[3] = new ItemStack(Blocks.stone, 64);
            body.player.inventory.currentItem = 0;
            aura.getModeSetting("Rot Mode").setValue("Adaptive");
            aura.getModeSetting("Adaptive Rotation Engine").setValue("Neutron");
            aura.getNumberSetting("Aim Range Margin").setValue(2D);
            aura.getBooleanSetting("Smooth Rotation Stop").setValue(false);
            ScaffoldOwnerContinuityTest.field(Minecraft.class, "mcThread")
                    .set(body.f.run.fixture.mc, Thread.currentThread());
            ScaffoldOwnerContinuityTest.field(PacketPipeline.class, "delays")
                    .set(pipe, new PacketDelays(now::get));
            moveTarget(4.5);
        }

        void moveTarget(double z) {
            target.posX = target.lastTickPosX = .5;
            target.posY = target.lastTickPosY = 64;
            target.posZ = target.lastTickPosZ = z;
            target.serverPosX = 16;
            target.serverPosY = 64 * 32;
            target.serverPosZ = (int) Math.round(z * 32);
            target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, z - .3, .8, 65.8, z + .3));
        }

        List<Object> walk() {
            now.addAndGet(50_000_000L);
            body.player.ticksExisted++;
            body.f.run.fixture.runtime.getScaffoldPlacementService().beginTick();
            return body.f.walk();
        }

        void acquireOutsideAttackRange() {
            List<Object> packets = walk();
            assertSame("normal Aura PRE selected the loaded target", target, aura.getActiveTarget());
            assertTrue(rotations.isMotionAccepted());
            assertEquals("aura", rotations.getConfirmedOwner());
            assertEquals(0, count(packets, C02PacketUseEntity.class));
        }

        List<Object> attackInRange() {
            moveTarget(2);
            for (int frame = 0; frame < 40; frame++) {
                List<Object> packets = walk();
                assertTrue("one shared attack budget per real walking step", count(packets, C02PacketUseEntity.class) <= 1);
                if (count(packets, C02PacketUseEntity.class) == 1) {
                    for (Object packet : packets) if (packet instanceof C02PacketUseEntity)
                        assertSame(target, ((C02PacketUseEntity) packet).getEntityFromWorld(body.f.run.scene));
                    assertTrue(rotations.isMotionAccepted());
                    assertEquals("aura", rotations.getConfirmedOwner());
                    return packets;
                }
            }
            throw new AssertionError("fresh accepted Adaptive motion must regain an actual controller attack");
        }

        public void close() throws Exception { body.close(); }
    }

    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    public static final class Checks {
        @Test public void tickbaseWindowsRequireFreshAcceptedAuraMotion() throws Exception {
            for (String mode : new String[]{"Demise Pulse", "Demise Approach"}) {
                try (Run r = new Run()) {
                    r.acquireOutsideAttackRange();
                    Tickbase module = new Tickbase(r.now::get, r.body.f.run.fixture.mc);
                    module.getModeSetting("Mode").setValue(mode);
                    module.setToggled(true);
                    try {
                        Tickbase.beginFrame(r.body.f.run.fixture.mc, false, 1, 50);
                        List<Object> held = r.walk();
                        assertTrue(mode + ": real Aura C03 must enter the existing window", r.pipe.delays().size() > 0);
                        assertEquals(0, count(held, C03PacketPlayer.class));
                        assertEquals(0, count(held, C02PacketUseEntity.class));
                        assertFalse("queued movement is not accepted movement", r.rotations.isMotionAccepted());
                        assertNull(r.rotations.getConfirmedOwner());

                        module.setToggled(false);
                        List<Object> released = r.body.f.run.drain();
                        assertEquals(0, r.pipe.delays().size());
                        assertTrue(count(released, C03PacketPlayer.class) > 0);
                        assertEquals("queue release alone cannot replay an Aura attack", 0, count(released, C02PacketUseEntity.class));
                        r.attackInRange();
                    } finally { module.setToggled(false); }
                }
            }
        }

        @Test public void lagRangeHeldWalkingCannotAuthorizeAnAuraAttack() throws Exception {
            try (Run r = new Run()) {
                r.acquireOutsideAttackRange();
                LagRange module = new LagRange(r.now::get, (min, max) -> min);
                module.getNumberSetting("Start Distance").setValue(3D);
                module.getNumberSetting("Min MS").setValue(100D);
                module.getNumberSetting("Max MS").setValue(100D);
                module.getBooleanSetting("Speed Up").setValue(false);
                module.getBooleanSetting("Slow Down").setValue(false);
                module.setToggled(true);
                try {
                    Pulsar.BUS.post(new TickEvent());
                    List<Object> held = r.walk();
                    assertTrue("real Aura C03 reaches LagRange's existing delay policy", r.pipe.delays().size() > 0);
                    assertFalse(r.rotations.isMotionAccepted());
                    assertNull(r.rotations.getConfirmedOwner());
                    assertEquals(0, count(held, C02PacketUseEntity.class));
                    module.setToggled(false);
                    assertEquals(0, r.pipe.delays().size());
                    assertEquals(0, count(r.body.f.run.drain(), C02PacketUseEntity.class));
                    r.attackInRange();
                } finally { module.setToggled(false); }
            }
        }

        @Test public void backtrackFollowsRealAuraSelectionAndReleasesOnTargetLoss() throws Exception {
            try (Run r = new Run()) {
                r.moveTarget(3.5);
                r.walk();
                assertSame(r.target, r.aura.getActiveTarget());
                Backtrack module = new Backtrack(r.now::get, r.body.f.run.fixture.mc, (min, max) -> min);
                module.getModeSetting("Backtrack Mode").setValue("Demise");
                module.setToggled(true);
                try {
                    Pulsar.BUS.post(new TickEvent());
                    int before = r.target.serverPosZ;
                    r.body.f.run.fixture.channel.writeInbound(new S14PacketEntity.S15PacketEntityRelMove(
                            19, (byte) 0, (byte) 0, (byte) 32, true));
                    assertEquals("movement for the actually selected Aura target is held", 1, r.pipe.delays().size());
                    assertEquals(before, r.target.serverPosZ);
                    assertNotNull(module.getActivitySnapshot());

                    r.body.f.run.scene.loadedEntityList.clear();
                    List<Object> lost = r.walk();
                    assertNull(r.aura.getActiveTarget());
                    assertEquals(0, count(lost, C02PacketUseEntity.class));
                    Pulsar.BUS.post(new TickEvent());
                    assertEquals("target loss releases this module's queue", 0, r.pipe.delays().size());
                    assertNull(module.getActivitySnapshot());
                } finally { module.setToggled(false); }
            }
        }

        @Test public void scaffoldRevokesActiveAndPendingAuraSwordUse() throws Exception {
            for (String mode : new String[]{"Force", "Release"}) {
                try (Run r = new Run()) {
                    r.aura.getModeSetting("Autoblock Mode").setValue(mode);
                    r.aura.getNumberSetting("Reblock Delay").setValue(3D);
                    r.aura.getBooleanSetting("Autoblock").setValue(true);
                    r.attackInRange();
                    if ("Force".equals(mode)) assertSame("positive control: actual owned use", r.sword, r.body.player.getItemInUse());
                    else assertNull("delayed reblock is pending", r.body.player.getItemInUse());

                    r.scaffold.getBooleanSetting("Smooth Rotation Stop").setValue(false);
                    r.scaffold.onEnable();
                    try {
                        boolean scaffoldOwned = false;
                        for (int frame = 0; frame < 12; frame++) {
                            List<Object> packets = r.walk();
                            if ("scaffold".equals(r.rotations.getConfirmedOwner())) {
                                scaffoldOwned = true;
                                assertEquals("Scaffold ownership excludes Aura attacks", 0, count(packets, C02PacketUseEntity.class));
                                assertNull("old Aura use cannot survive placement ownership", r.body.player.getItemInUse());
                            }
                        }
                        assertTrue("positive control: real Scaffold acquired rotation", scaffoldOwned);
                        assertTrue("real controller placement still completes", r.body.f.run.scene.blocks.containsKey(new BlockPos(0, 63, 0)));
                        assertEquals("temporary block selection returns to original slot", 0, r.body.player.inventory.currentItem);
                    } finally { r.scaffold.onDisable(); }
                    r.attackInRange();
                }
            }
        }

        @Test public void manualSlotChangeDoesNotReuseOldAuraBlockOwnership() throws Exception {
            try (Run r = new Run()) {
                r.aura.getModeSetting("Autoblock Mode").setValue("Force");
                r.aura.getBooleanSetting("Autoblock").setValue(true);
                r.attackInRange();
                assertSame(r.sword, r.body.player.getItemInUse());
                r.body.player.inventory.currentItem = 1;
                List<Object> packets = r.walk();
                assertEquals("manual slot choice is retained", 1, r.body.player.inventory.currentItem);
                assertNull("old stack use is revoked", r.body.player.getItemInUse());
                assertEquals("empty slot cannot be reblocked", 0, count(packets, C08PacketPlayerBlockPlacement.class));
            }
        }
    }
}
