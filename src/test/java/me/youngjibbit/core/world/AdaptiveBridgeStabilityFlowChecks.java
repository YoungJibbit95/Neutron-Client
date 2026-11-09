package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Observable stability using real fresh-input/rotation/C03/POST hooks, native
 * ItemBlock placement and Entity.moveFlying/moveEntity. Physics runs between
 * early rotation preparation and the final movement packet, as in the client.
 * Storage, physical keys and transport are synthetic; there is no live server.
 */
public class AdaptiveBridgeStabilityFlowChecks {
    private static AdaptiveBridgeLaneController lane(AdaptiveBridgeInputSafetyFlowTest.Run r) throws Exception {
        return (AdaptiveBridgeLaneController) ScaffoldOwnerContinuityTest.field(
                Scaffold.class, "bridgeLane").get(r.f.scaffold);
    }

    private static long count(List<Object> packets, Class<?> type) {
        return packets.stream().filter(type::isInstance).count();
    }

    private static BlockPos destination(C08PacketPlayerBlockPlacement placement) {
        assertTrue("a scaffold placement must target a concrete block face",
                placement.getPlacedBlockDirection() >= 0 && placement.getPlacedBlockDirection() < 6);
        return placement.getPosition().offset(EnumFacing.getFront(placement.getPlacedBlockDirection()));
    }

    private static void assertAcceptedBeforePlacement(List<Object> packets) {
        int accepted = -1;
        for (int i = 0; i < packets.size(); i++) {
            if (packets.get(i) instanceof C03PacketPlayer) accepted = i;
            if (packets.get(i) instanceof C08PacketPlayerBlockPlacement)
                assertTrue("native placement follows accepted motion", accepted >= 0 && accepted < i);
        }
    }

    private static void route(AdaptiveBridgeInputSafetyFlowTest.Run r, MoveFixController.Mode mode) {
        r.adaptive("Off");
        r.f.scaffold.getModeSetting("movefix").setValue(mode.label);
        r.scene.blocks.clear();
        r.scene.blocks.put(new BlockPos(0, 63, 0), Blocks.stone.getDefaultState());
        r.position(.5, .5);
        r.player.rotationYaw = -90;
        r.player.rotationPitch = 0;
        r.player.motionX = r.player.motionY = r.player.motionZ = 0;
        r.settings.keyBindForward.pressed = true;
    }

    /** The physics omitted by Run's headless living boundary, in vanilla order. */
    private static void physics(AdaptiveBridgeInputSafetyFlowTest.Run r) {
        float drag = r.player.onGround ? .546f : .91f;
        float acceleration = r.player.onGround ? .1f : .02f;
        r.player.moveFlying(r.player.movementInput.moveStrafe * .98f,
                r.player.movementInput.moveForward * .98f, acceleration);
        r.player.moveEntity(r.player.motionX, r.player.motionY, r.player.motionZ);
        r.player.motionX *= drag;
        r.player.motionZ *= drag;
        r.player.motionY = (r.player.motionY - .08) * .98;
    }

    @Test public void ordinaryFallingWithoutSneakOrKnockbackPlacesAndLandsThroughRealPhysics() throws Exception {
        try (AdaptiveBridgeInputSafetyFlowTest.Run r = new AdaptiveBridgeInputSafetyFlowTest.Run()) {
            route(r, MoveFixController.Mode.PRECISE);
            r.position(1.31, .5);
            r.player.posY += .16;
            r.player.setEntityBoundingBox(r.player.getEntityBoundingBox().offset(0, .16, 0));
            r.player.onGround = false;
            r.player.motionY = -.078;
            r.player.motionX = .15;
            assertEquals(0, r.player.hurtTime);
            assertEquals("Balanced", r.f.scaffold.getModeSetting("Aim Speed").getValue());
            assertEquals(12, r.f.scaffold.getNumberSetting("Place Min CPS").getValue(), 0);
            assertEquals(16, r.f.scaffold.getNumberSetting("Place Max CPS").getValue(), 0);
            assertTrue("the body has lost the old floor",
                    r.scene.getCollidingBoundingBoxes(r.player,
                            r.player.getEntityBoundingBox().offset(0, -.2, 0)).isEmpty());
            assertNotNull("positive control: the ordinary fall has a reachable catch face",
                    r.f.runtime.getScaffoldPlacementService().planFace(new BlockPos(0, 63, 0), EnumFacing.EAST, null));
            r.f.scaffold.onEnable();
            r.step(true, () -> {
                assertFalse(r.player.isSneaking());
                assertFalse(r.settings.keyBindSneak.isKeyDown());
                assertEquals("the controller does not erase fall momentum", -.078, r.player.motionY, 0);
                physics(r);
                assertEquals(64.082, r.player.posY, 1e-9);
                assertTrue("the body really moves before C03", r.player.posX > 1.46);
            });
            List<Object> packets = r.drain();
            assertEquals(1, count(packets, C03PacketPlayer.class));
            assertAcceptedBeforePlacement(packets);
            assertEquals("clutch must acquire the accepted post-physics ray without startup delay",
                    1, count(packets, C08PacketPlayerBlockPlacement.class));
            assertEquals(Blocks.stone, r.scene.getBlockState(new BlockPos(1, 63, 0)).getBlock());
            assertEquals(63, r.player.inventory.mainInventory[3].stackSize);
            assertEquals(1, r.f.scaffold.bridgePlacementCount());

            r.step(true, () -> physics(r));
            assertEquals("native collision catches the fall at the new surface", 64, r.player.posY, 1e-9);
            assertTrue(r.player.onGround);
            assertEquals(.5, r.player.posZ, .001);
            assertFalse(r.player.isSneaking());
            assertFalse(r.settings.keyBindSneak.isKeyDown());
            assertAcceptedBeforePlacement(r.drain());
        }
    }

    @Test public void successiveCellsUseOneRowAcrossAllCorrectionModesWhileActualAimChanges() throws Exception {
        for (MoveFixController.Mode mode : MoveFixController.Mode.values()) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                route(r, mode);
                r.f.scaffold.onEnable();
                Set<BlockPos> placed = new HashSet<>();
                Set<String> poses = new HashSet<>();
                for (int tick = 0; tick < 65; tick++) {
                    r.step(true, () -> {
                        assertFalse("Off edge policy must not hide missed placements with sneak", r.player.isSneaking());
                        physics(r);
                    });
                    List<Object> packets = r.drain();
                    assertEquals(mode + " has one physical motion per step", 1, count(packets, C03PacketPlayer.class));
                    assertAcceptedBeforePlacement(packets);
                    for (Object packet : packets) {
                        if (packet instanceof C03PacketPlayer) {
                            C03PacketPlayer motion = (C03PacketPlayer) packet;
                            if (motion.getRotating()) poses.add(motion.getYaw() + ":" + motion.getPitch());
                        }
                        if (packet instanceof C08PacketPlayerBlockPlacement) {
                            BlockPos cell = destination((C08PacketPlayerBlockPlacement) packet);
                            assertEquals(mode + " cannot start a parallel row", 0, cell.getZ());
                            assertEquals(mode + " keeps the bridge surface", 63, cell.getY());
                            assertTrue("each native placement advances the route", placed.add(cell));
                            assertEquals(Blocks.stone, r.scene.getBlockState(cell).getBlock());
                        }
                    }
                    assertEquals(mode + " cannot swerve with the placement aim", .5, r.player.posZ, .015);
                    assertEquals(mode + " must place before falling below the route", 64, r.player.posY, 1e-8);
                    assertFalse(r.settings.keyBindSneak.isKeyDown());
                }
                assertTrue(mode + " traverses and actually builds several cells", placed.size() >= 5);
                assertTrue(mode + " exercises changing accepted aim", poses.size() >= 3);
                assertEquals(64 - placed.size(), r.player.inventory.mainInventory[3].stackSize);
                assertEquals(placed.size(), r.f.scaffold.bridgePlacementCount());
                assertTrue(r.player.posX > 5);
                for (BlockPos cell : r.scene.blocks.keySet()) {
                    assertEquals(0, cell.getZ()); assertEquals(63, cell.getY());
                }
            }
        }
    }

    @Test public void diagonalCornersBuildAConnectedStaircaseAtPositiveAndNegativeCoordinates() throws Exception {
        for (double start : new double[]{.5, -8.5}) {
            try (AdaptiveBridgeInputSafetyFlowTest.Run r = new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                route(r, MoveFixController.Mode.PRECISE);
                r.scene.blocks.clear();
                r.scene.blocks.put(new BlockPos(start, 63, start), Blocks.stone.getDefaultState());
                r.position(start, start);
                r.player.rotationYaw = -45;
                r.f.scaffold.onEnable();
                Set<BlockPos> placed = new HashSet<>();
                for (int tick = 0; tick < 65; tick++) {
                    r.step(true, () -> physics(r));
                    List<Object> packets = r.drain();
                    assertAcceptedBeforePlacement(packets);
                    for (Object packet : packets) if (packet instanceof C08PacketPlayerBlockPlacement) {
                        BlockPos cell = destination((C08PacketPlayerBlockPlacement) packet);
                        assertEquals(63, cell.getY());
                        assertTrue("only the immediate diagonal staircase may be built",
                                Math.abs(cell.getX() - cell.getZ()) <= 1);
                        assertTrue(placed.add(cell));
                    }
                    assertEquals("diagonal corner at tick " + tick + ", x=" + r.player.posX,
                            64, r.player.posY, 1e-8);
                    assertEquals("placement aim cannot redirect the diagonal", r.player.posX, r.player.posZ, .015);
                    assertFalse(r.player.isSneaking());
                }
                assertTrue("several actual diagonal transitions are built", placed.size() >= 8);
                assertTrue(r.player.posX > start + 5);
                assertEquals(64 - placed.size(), r.player.inventory.mainInventory[3].stackSize);
                assertEquals(placed.size(), r.f.scaffold.bridgePlacementCount());
            }
        }
    }

    @Test public void offDoesNotSteerAndARealUserTurnReplacesTheAnchoredLane() throws Exception {
        try (AdaptiveBridgeInputSafetyFlowTest.Run r = new AdaptiveBridgeInputSafetyFlowTest.Run()) {
            route(r, MoveFixController.Mode.OFF);
            // Existing floor keeps this regression focused on input intention.
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                r.scene.blocks.put(new BlockPos(x, 63, z), Blocks.stone.getDefaultState());
            r.player.motionZ = .12;
            r.f.scaffold.onEnable();
            r.step(true, () -> { });
            assertEquals(1, r.player.movementInput.moveForward, 0);
            assertEquals(0, r.player.movementInput.moveStrafe, 0);
            assertEquals(.12, r.player.motionZ, 0);
            assertNull("Off has no hidden lane steering", lane(r).current());
            assertEquals(0, count(r.drain(), C08PacketPlayerBlockPlacement.class));

            r.f.scaffold.getModeSetting("movefix").setValue("Präzise");
            r.player.motionZ = 0;
            r.step(true, () -> { });
            AdaptiveBridgeLaneController.Lane straight = lane(r).current();
            assertNotNull(straight);
            assertEquals(1, straight.directionX, 1e-9);
            assertEquals(0, straight.directionZ, 1e-9);
            r.drain();
            r.settings.keyBindForward.pressed = false;
            r.settings.keyBindLeft.pressed = true;
            r.step(true, () -> { });
            AdaptiveBridgeLaneController.Lane turned = lane(r).current();
            assertNotNull(turned);
            assertNotSame("a deliberate turn must replace the old lane", straight, turned);
            assertEquals(0, turned.directionX, 1e-9);
            assertEquals(-1, turned.directionZ, 1e-9);
            assertEquals(0, r.player.movementInput.moveForward, 0);
            assertEquals(1, r.player.movementInput.moveStrafe, 0);
            assertEquals(0, count(r.drain(), C08PacketPlayerBlockPlacement.class));
        }
    }
}
