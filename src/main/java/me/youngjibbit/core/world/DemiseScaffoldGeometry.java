package me.youngjibbit.core.world;

import net.minecraft.block.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ported from YoungJibbit95/DemisePrivate, commit
 * e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb:
 * src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java and
 * src/main/java/wtf/demise/utils/player/rotation/RotationUtils.java.
 * Client globals and source-level syntax are adapted to Neutron's Java 8 API.
 */
public final class DemiseScaffoldGeometry {
    private static final EnumFacing[] FACINGS = {
            EnumFacing.EAST, EnumFacing.WEST,
            EnumFacing.NORTH, EnumFacing.SOUTH,
            EnumFacing.UP
    };

    private static final BlockPos[] OFFSETS = {
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1),
            new BlockPos(0, -1, 0)
    };

    private DemiseScaffoldGeometry() { }

    public static PlaceData findBlock(World world, BlockPos pos, BlockPos playerPos, BlockPos previousBlock) {
        PlaceData immediateCheck = checkPositions(world, pos, previousBlock);
        if (immediateCheck != null) {
            return immediateCheck;
        }

        for (int depth = 1; depth <= 6; depth++) {
            List<BlockPos> positions = generateOffsets(pos, depth);
            if (positions.isEmpty()) continue;

            positions.sort(Comparator.comparingDouble(p -> p.distanceSq(playerPos)));

            for (BlockPos targetPos : positions) {
                PlaceData result = checkPositions(world, targetPos, previousBlock);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static List<BlockPos> generateOffsets(BlockPos origin, int depth) {
        List<BlockPos> result = new ArrayList<>((int) Math.pow(OFFSETS.length, depth));
        if (depth == 0) {
            result.add(origin);
            return result;
        }

        List<BlockPos> currentLevel = new ArrayList<>();
        List<BlockPos> nextLevel = new ArrayList<>();
        currentLevel.add(origin);

        for (int i = 0; i < depth; i++) {
            for (BlockPos pos : currentLevel) {
                for (BlockPos offset : OFFSETS) {
                    nextLevel.add(pos.add(offset));
                }
            }

            if (i < depth - 1) {
                currentLevel.clear();
                List<BlockPos> temp = currentLevel;
                currentLevel = nextLevel;
                nextLevel = temp;
            }
        }

        return nextLevel;
    }

    private static PlaceData checkPositions(World world, BlockPos basePos, BlockPos previousBlock) {
        for (int i = 0; i < OFFSETS.length; i++) {
            BlockPos newPos = basePos.add(OFFSETS[i]);
            Block block = world.getBlockState(newPos).getBlock();

            if (block != Blocks.air) {
                if (newPos.equals(previousBlock)) {
                    return new PlaceData(FACINGS[i], newPos);
                }

                if (!block.getMaterial().isReplaceable() && !isInteractable(block)) {
                    return new PlaceData(FACINGS[i], newPos);
                }
            }
        }
        return null;
    }

    private static boolean isInteractable(Block block) {
        return block instanceof BlockFurnace || block instanceof BlockFenceGate || block instanceof BlockChest || block instanceof BlockEnderChest || block instanceof BlockEnchantmentTable || block instanceof BlockBrewingStand || block instanceof BlockBed || block instanceof BlockDispenser || block instanceof BlockHopper || block instanceof BlockAnvil || block == Blocks.crafting_table;
    }

    public static Vec3 faceCenter(BlockPos pos, EnumFacing face) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        x += face.getFrontOffsetX() / 2.0D;
        z += face.getFrontOffsetZ() / 2.0D;
        y += face.getFrontOffsetY() / 2.0D;

        return new Vec3(x, y, z);
    }

    public static float[] rotations(EntityPlayerSP player, Vec3 target) {
        double x = target.xCoord - player.posX;
        double y = target.yCoord - (player.posY + (double) player.getEyeHeight());
        double z = target.zCoord - player.posZ;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static MovingObjectPosition rayTrace(EntityPlayerSP player, float[] rot, double reach, float partialTicks) {
        Vec3 from = player.getPositionEyes(partialTicks);
        Vec3 direction = Entity.getVectorForRotation(rot[1], rot[0]);
        Vec3 to = from.addVector(direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach);

        MovingObjectPosition result = player.worldObj.rayTraceBlocks(from, to, false, true, true);

        if (result == null) {
            return new MovingObjectPosition(MovingObjectPosition.MovingObjectType.MISS, to, EnumFacing.UP, new BlockPos(to));
        }

        return result;
    }

    public static float[] bestRotation(EntityPlayerSP player, PlaceData data, float[] serverRotation) {
        BlockPos blockPos = data.blockPos;
        EnumFacing face = data.facing;
        Vec3i faceVec = face.getDirectionVec();

        float[] xRange = getAxisMinMax(faceVec.getX());
        float[] yRange = getAxisMinMax(faceVec.getY());
        float[] zRange = getAxisMinMax(faceVec.getZ());

        float[] bestRot = rotations(player, faceCenter(data.blockPos, data.facing));
        double bestDist = rotationDifference(serverRotation, bestRot);
        boolean picked = false;

        for (float x = xRange[0]; x <= xRange[1]; x += 0.01f) {
            for (float y = yRange[0]; y <= yRange[1]; y += 0.01f) {
                for (float z = zRange[0]; z <= zRange[1]; z += 0.01f) {
                    Vec3 localPos = new Vec3(x, y, z);
                    Vec3 worldPos = localPos.add(new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()));

                    double diff = rotationDifference(serverRotation, rotations(player, worldPos));
                    // Upstream intentionally tests the previous bestRot, not this candidate.
                    MovingObjectPosition ray = rayTrace(player, bestRot, 4.5, 1);

                    if (diff < bestDist && ray.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && ray.getBlockPos().equals(blockPos) && ray.sideHit.equals(face)) {
                        bestDist = diff;
                        bestRot = rotations(player, worldPos);
                        picked = true;
                    }
                }
            }
        }

        return picked ? bestRot : rotations(player, faceCenter(data.blockPos, data.facing));
    }

    private static float[] getAxisMinMax(int axisValue) {
        if (axisValue == 1) return new float[]{1.0f, 1.0f};
        if (axisValue == -1) return new float[]{0.0f, 0.0f};
        return new float[]{0.1f, 0.9f};
    }

    private static float rotationDifference(final float[] a, final float[] b) {
        return (float) Math.hypot(Math.abs(MathHelper.wrapAngleTo180_float(a[0] - b[0])), Math.abs(a[1] - b[1]));
    }

    public static final class PlaceData {
        public final EnumFacing facing;
        public final BlockPos blockPos;

        public PlaceData(EnumFacing facing, BlockPos blockPos) {
            this.facing = facing;
            this.blockPos = blockPos;
        }
    }
}
