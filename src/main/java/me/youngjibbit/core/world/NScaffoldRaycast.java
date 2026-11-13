package me.youngjibbit.core.world;

import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded, real first-hit geometry for NScaffold. Planning never authorizes a click. */
public final class NScaffoldRaycast {
    static final int MAX_DEPTH = 6;
    static final int MAX_RAYS = 128;
    private static final double EPS = 1.0e-5;
    private static final EnumFacing[] NEIGHBORS = {
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.DOWN};
    private NScaffoldRaycast() { }

    /** All geometry is copied; the caller retains responsibility for its scene and inventory lifetime. */
    public static final class Plan {
        public final DemiseScaffoldGeometry.PlaceData data;
        public final BlockPos destination;
        public final float yaw, pitch;
        public final Vec3 hitVec;
        public final IBlockState supportState;
        public final AxisAlignedBB actualBounds;
        private final World world;
        private Plan(World world, BlockPos destination, Surface surface, float yaw, float pitch, Vec3 hit) {
            this.world = world;
            this.destination = copy(destination);
            this.data = new DemiseScaffoldGeometry.PlaceData(surface.face, copy(surface.pos));
            this.yaw = yaw; this.pitch = pitch;
            this.hitVec = new Vec3(hit.xCoord, hit.yCoord, hit.zCoord);
            this.supportState = surface.state;
            this.actualBounds = surface.bounds;
        }
        public float[] rotation() { return new float[]{yaw, pitch}; }
    }

    public static Plan find(EntityPlayerSP player, BlockPos destination, BlockPos previousSuccessfulBlock,
                            float preferredYaw, float preferredPitch, double reach, BooleanSupplier current) {
        return player == null ? null : find(player, player.getPositionEyes(1), destination,
                previousSuccessfulBlock, preferredYaw, preferredPitch, reach, current);
    }

    /** Forecast eyes may choose an early aim. trace must still receive the actual accepted wire eyes. */
    public static Plan find(EntityPlayerSP player, Vec3 planningEyes, BlockPos destination,
                            BlockPos previousSuccessfulBlock, float preferredYaw, float preferredPitch,
                            double reach, BooleanSupplier current) {
        return find(player,planningEyes,destination,previousSuccessfulBlock,preferredYaw,preferredPitch,reach,current,0);
    }

    public static Plan find(EntityPlayerSP player, Vec3 planningEyes, BlockPos destination,
                            BlockPos previousSuccessfulBlock, float preferredYaw, float preferredPitch,
                            double reach, BooleanSupplier current, double margin) {
        return find(player, planningEyes, destination, previousSuccessfulBlock,
                preferredYaw, preferredPitch, reach, current, margin, null, false);
    }

    /** Continue an intended walking path through an already empty foot cell. */
    public static Plan findMoving(EntityPlayerSP player, Vec3 planningEyes, BlockPos destination,
                                  BlockPos previousSuccessfulBlock, float preferredYaw, float preferredPitch,
                                  double reach, BooleanSupplier current, double margin) {
        return find(player, planningEyes, destination, previousSuccessfulBlock,
                preferredYaw, preferredPitch, reach, current, margin, null, true);
    }

    /** A diagonal step needs an orthogonal support first; stay inside this intended floor path. */
    public static Plan findAhead(EntityPlayerSP player, Vec3 planningEyes, BlockPos from, BlockPos destination,
                                 BlockPos previousSuccessfulBlock, float preferredYaw, float preferredPitch,
                                 double reach, BooleanSupplier current, double margin) {
        if (from == null || destination == null || from.getY() != destination.getY()) return null;
        return find(player, planningEyes, destination, previousSuccessfulBlock,
                preferredYaw, preferredPitch, reach, current, margin, copy(from), true);
    }

    private static Plan find(EntityPlayerSP player, Vec3 planningEyes, BlockPos destination,
                             BlockPos previousSuccessfulBlock, float preferredYaw, float preferredPitch,
                             double reach, BooleanSupplier current, double margin, BlockPos from, boolean walking) {
        if (player == null || player.worldObj == null || destination == null || !finite(planningEyes)
                || !angles(preferredYaw, preferredPitch) || !Double.isFinite(reach) || reach <= 0 || current == null
                || !Double.isFinite(margin) || margin < 0) return null;
        final World world = player.worldObj;
        final Vec3 eyes = new Vec3(planningEyes.xCoord, planningEyes.yCoord, planningEyes.zCoord);
        final BlockPos requested = copy(destination);
        final Vec3 playerPos = player.getPositionVector();
        final BlockPos originalPlayerPos = player.getPosition();
        // Preserve Demise's stationary recovery order. Only an intended walking path chooses
        // between equivalent steps using the unrounded physical position.
        final java.util.function.ToDoubleFunction<BlockPos> distanceToPlayer = !walking
                ? p -> p.distanceSq(originalPlayerPos)
                : p -> p.distanceSqToCenter(playerPos.xCoord, playerPos.yCoord, playerPos.zCoord);
        final BooleanSupplier live = () -> current.getAsBoolean() && player.worldObj == world;
        // An occupied requested foot cell must not cause unsolicited building beside it.
        if (!replaceable(world, requested, live)) return null;
        Budget budget = new Budget(world, eyes, reach, live);
        MovingObjectPosition preferred = budget.ray(preferredYaw, preferredPitch);
        if (!live.getAsBoolean()) return null;
        List<BlockPos> level = new ArrayList<>(); level.add(requested);
        Set<BlockPos> visited = new HashSet<>(); visited.add(requested);
        int depthLimit = from == null ? MAX_DEPTH : (int) Math.min(MAX_DEPTH, Math.max(0L,
                Math.abs((long) requested.getX() - from.getX()) + Math.abs((long) requested.getZ() - from.getZ()) - 1));
        for (int depth = 0; depth <= depthLimit && !level.isEmpty(); depth++) {
            // Equal-distance diagonal steps are alternatives, not a reason to turn away
            // from an already useful ray just because WEST precedes NORTH in the BFS.
            level.sort(Comparator.comparingDouble(distanceToPlayer)
                    .thenComparingInt(p -> walking && blockHit(preferred)
                            && preferred.getBlockPos().offset(preferred.sideHit).equals(p) ? 0 : 1));
            Plan best = null;
            double bestDistance = Double.POSITIVE_INFINITY, bestTurn = Double.POSITIVE_INFINITY;
            for (BlockPos cell : level) {
                if (!live.getAsBoolean()) return null;
                double distance = distanceToPlayer.applyAsDouble(cell);
                if (budget.exhausted() || best != null && distance > bestDistance)
                    return currentPlan(world, best, live);
                if (from != null && !onForwardPath(from, requested, cell)) continue;
                if (!replaceable(world, cell, live)) continue;
                // A useful current ray wins without searching another angle or a fixed hit pixel.
                if (blockHit(preferred) && preferred.getBlockPos().offset(preferred.sideHit).equals(cell)) {
                    Surface surface = surface(world, preferred.getBlockPos(), preferred.sideHit, live);
                    Plan retained = makePlan(world, cell, surface, preferredYaw, preferredPitch, preferred, eyes, reach, live, margin);
                    if (retained != null) return retained;
                }
                // Preserve depth/distance order; a previously successful adjacent support is a continuity tie-break.
                List<EnumFacing> neighbors = new ArrayList<>(Arrays.asList(NEIGHBORS));
                if (previousSuccessfulBlock != null) neighbors.sort(Comparator.comparingInt(
                        side -> cell.offset(side).equals(previousSuccessfulBlock) ? 0 : 1));
                for (EnumFacing side : neighbors) {
                    Surface surface = surface(world, cell.offset(side), side.getOpposite(), live);
                    if (!live.getAsBoolean()) return null;
                    if (surface == null || !visibleSide(eyes, surface) || distanceToFaceSq(eyes, surface) > reach * reach) continue;
                    List<Vec3> points = points(surface, eyes, preferredYaw, preferredPitch, margin);
                    Vec3 center = point(surface,.5,.5);
                    // The first acquisition aims well inside the face so smoothing does not
                    // spend its final tiny steps just outside the first usable hit. Later
                    // placements keep the nearest valid angle and its already aligned axes.
                    points.sort(Comparator.comparingInt((Vec3 point) -> previousSuccessfulBlock == null
                            && point.squareDistanceTo(center) < 1.0e-12 ? 0 : 1)
                            .thenComparingDouble(point -> difference(rotation(eyes, point), preferredYaw, preferredPitch)));
                    for (Vec3 point : points) {
                        if (!live.getAsBoolean()) return null;
                        if (budget.exhausted()) return currentPlan(world, best, live);
                        float[] aim = rotation(eyes, point);
                        MovingObjectPosition hit = budget.ray(aim[0], aim[1], surface);
                        Plan plan = makePlan(world, cell, surface, aim[0], aim[1], hit, eyes, reach, live, margin);
                        if (plan != null) {
                            if (!walking) return plan;
                            double turn = difference(aim, preferredYaw, preferredPitch);
                            if (best == null || turn < bestTurn) {
                                best = plan; bestDistance = distance; bestTurn = turn;
                            }
                            break;
                        }
                    }
                }
            }
            if (best != null) return currentPlan(world, best, live);
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos cell : level) for (EnumFacing side : NEIGHBORS) {
                BlockPos neighbor = cell.offset(side);
                if (visited.add(neighbor)) next.add(neighbor);
            }
            level = next;
        }
        return null;
    }

    private static Plan currentPlan(World world, Plan plan, BooleanSupplier current) {
        return plan != null && valid(world, plan, current) ? plan : null;
    }

    private static boolean onForwardPath(BlockPos from, BlockPos destination, BlockPos cell) {
        return cell.getY() == destination.getY() && !cell.equals(from)
                && cell.getX() >= Math.min(from.getX(), destination.getX())
                && cell.getX() <= Math.max(from.getX(), destination.getX())
                && cell.getZ() >= Math.min(from.getZ(), destination.getZ())
                && cell.getZ() <= Math.max(from.getZ(), destination.getZ());
    }

    /** Rotation-only continuity: occupied terrain is useful even though it cannot authorize a click. */
    public static boolean hasRotationSupport(World world, Vec3 eyes, BlockPos feet, Plan previous,
                                              double reach, BooleanSupplier current) {
        if (world == null || feet == null || !finite(eyes) || !Double.isFinite(reach) || reach <= 0
                || current == null || !current.getAsBoolean() || previous != null && previous.world != world) return false;
        Surface underfoot = surface(world, feet, EnumFacing.UP, current);
        if (underfoot != null && distanceToFaceSq(eyes, underfoot) <= reach * reach && current.getAsBoolean()) return true;
        if (previous == null || !current.getAsBoolean()) return false;
        Surface support = surface(world, previous.data.blockPos, previous.data.facing, current);
        return support != null && Objects.equals(support.state, previous.supportState)
                && sameBounds(support.bounds, previous.actualBounds) && distanceToFaceSq(eyes, support) <= reach * reach
                && current.getAsBoolean();
    }

    /** Actual accepted pose only: BLOCK, support, face, destination, state, bounds, reach and visibility. */
    public static MovingObjectPosition trace(World world, Vec3 eyes, float yaw, float pitch, double reach,
                                             Plan plan, BooleanSupplier current) {
        if (plan == null || world != plan.world || !finite(eyes) || !angles(yaw, pitch)
                || !Double.isFinite(reach) || reach <= 0 || current == null || !valid(world, plan, current)) return null;
        Vec3 direction = Entity.getVectorForRotation(pitch, yaw);
        double castLength=castLength(eyes,direction,plan.actualBounds,plan.data.facing,reach);
        if (!Double.isFinite(castLength)) return null;
        Vec3 end=eyes.addVector(direction.xCoord*castLength,direction.yCoord*castLength,direction.zCoord*castLength);
        RayPath path=loadedPath(world,eyes,end,current);
        if (path==null) return null;
        MovingObjectPosition hit = world.rayTraceBlocks(eyes,end,false,false,false);
        if (!path.unchanged(current) || !matches(plan.data.blockPos, plan.data.facing, plan.destination,
                plan.actualBounds, hit, eyes, reach) || !valid(world, plan, current)) return null;
        return new MovingObjectPosition(hit.hitVec, hit.sideHit, copy(hit.getBlockPos()));
    }

    /** Strict mode adds only a small tangential inset, scaled down for narrow/slab/carpet faces. */
    public static boolean interiorHit(Plan plan, MovingObjectPosition hit, double margin) {
        if (plan == null || !blockHit(hit) || !Double.isFinite(margin) || margin < 0
                || !plan.data.blockPos.equals(hit.getBlockPos()) || plan.data.facing != hit.sideHit
                || !onFace(plan.actualBounds, hit.sideHit, hit.hitVec)) return false;
        AxisAlignedBB b = plan.actualBounds; Vec3 h = hit.hitVec;
        switch (hit.sideHit.getAxis()) {
            case X: return interior(h.yCoord,b.minY,b.maxY,margin) && interior(h.zCoord,b.minZ,b.maxZ,margin);
            case Y: return interior(h.xCoord,b.minX,b.maxX,margin) && interior(h.zCoord,b.minZ,b.maxZ,margin);
            default: return interior(h.xCoord,b.minX,b.maxX,margin) && interior(h.yCoord,b.minY,b.maxY,margin);
        }
    }

    private static boolean valid(World world, Plan plan, BooleanSupplier current) {
        if (!current.getAsBoolean() || !plan.data.blockPos.offset(plan.data.facing).equals(plan.destination)
                || !replaceable(world, plan.destination, current)) return false;
        Surface now = surface(world, plan.data.blockPos, plan.data.facing, current);
        return now != null && Objects.equals(now.state, plan.supportState) && sameBounds(now.bounds,plan.actualBounds)
                && current.getAsBoolean();
    }

    private static Plan makePlan(World world, BlockPos destination, Surface surface, float yaw, float pitch,
                                 MovingObjectPosition hit, Vec3 eyes, double reach, BooleanSupplier current, double margin) {
        if (surface == null || !current.getAsBoolean() || !matches(surface.pos,surface.face,destination,
                surface.bounds,hit,eyes,reach)) return null;
        Plan plan = new Plan(world,destination,surface,yaw,pitch,hit.hitVec);
        return interiorHit(plan,hit,margin) && valid(world,plan,current) ? plan : null;
    }

    private static boolean matches(BlockPos support, EnumFacing face, BlockPos destination, AxisAlignedBB bounds,
                                   MovingObjectPosition hit, Vec3 eyes, double reach) {
        return blockHit(hit) && support.equals(hit.getBlockPos()) && face == hit.sideHit
                && support.offset(face).equals(destination) && eyes.squareDistanceTo(hit.hitVec) <= reach*reach + EPS
                && onFace(bounds,face,hit.hitVec);
    }

    private static boolean replaceable(World world, BlockPos pos, BooleanSupplier current) {
        if (!loaded(world,pos,current)) return false;
        IBlockState state = world.getBlockState(pos);
        if (!current.getAsBoolean() || state == null) return false;
        boolean replaceable = state.getBlock().isReplaceable(world,pos);
        return current.getAsBoolean() && replaceable;
    }

    private static boolean loaded(World world, BlockPos pos, BooleanSupplier current) {
        if (!current.getAsBoolean() || pos.getY() < 0 || pos.getY() >= 256 || !world.isBlockLoaded(pos)
                || !current.getAsBoolean()) return false;
        net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
        return current.getAsBoolean() && border != null && border.contains(pos) && current.getAsBoolean();
    }

    private static Surface surface(World world, BlockPos pos, EnumFacing face, BooleanSupplier current) {
        if (!loaded(world,pos,current)) return null;
        IBlockState state = world.getBlockState(pos);
        if (!current.getAsBoolean() || state == null) return null;
        Block block = state.getBlock();
        if (block == Blocks.air || block.getMaterial().isLiquid() || block.isReplaceable(world,pos)
                || interactive(block) || !current.getAsBoolean() || !block.canCollideCheck(state,false)) return null;
        if (!current.getAsBoolean()) return null;
        block.setBlockBoundsBasedOnState(world,pos);
        if (!current.getAsBoolean()) return null;
        AxisAlignedBB bounds = block.getSelectedBoundingBox(world,pos);
        if (!current.getAsBoolean() || !bounds(bounds)) return null;
        IBlockState after = world.getBlockState(pos);
        return current.getAsBoolean() && Objects.equals(state,after) ? new Surface(copy(pos),face,state,bounds) : null;
    }

    private static boolean interactive(Block block) {
        return block instanceof BlockFurnace || block instanceof BlockFenceGate || block instanceof BlockChest
                || block instanceof BlockEnderChest || block instanceof BlockEnchantmentTable || block instanceof BlockBrewingStand
                || block instanceof BlockBed || block instanceof BlockDispenser || block instanceof BlockHopper
                || block instanceof BlockAnvil || block instanceof BlockDoor || block instanceof BlockTrapDoor
                || block instanceof BlockLever || block instanceof BlockButton || block instanceof BlockNote
                || block instanceof BlockJukebox || block instanceof BlockBeacon || block instanceof BlockCake
                || block == Blocks.crafting_table;
    }

    private static final class Surface {
        final BlockPos pos; final EnumFacing face; final IBlockState state; final AxisAlignedBB bounds;
        Surface(BlockPos pos, EnumFacing face, IBlockState state, AxisAlignedBB bounds) {
            this.pos=pos; this.face=face; this.state=state; this.bounds=bounds;
        }
    }
    private static final class Budget {
        final World world; final Vec3 eyes; final double reach; final BooleanSupplier current; int rays;
        Budget(World world, Vec3 eyes, double reach, BooleanSupplier current) {
            this.world=world; this.eyes=eyes; this.reach=reach; this.current=current;
        }
        boolean exhausted() { return rays >= MAX_RAYS; }
        MovingObjectPosition ray(float yaw,float pitch) {
            return ray(yaw,pitch,null);
        }
        MovingObjectPosition ray(float yaw,float pitch,Surface target) {
            if (exhausted() || !current.getAsBoolean()) return null;
            rays++; Vec3 direction=Entity.getVectorForRotation(pitch,yaw);
            double length=target==null?reach:castLength(eyes,direction,target.bounds,target.face,reach);
            if (!Double.isFinite(length)) return null;
            Vec3 end=eyes.addVector(direction.xCoord*length,direction.yCoord*length,direction.zCoord*length);
            RayPath path=loadedPath(world,eyes,end,current);
            if (path==null) return null;
            MovingObjectPosition hit=world.rayTraceBlocks(eyes,end,false,false,false);
            return path.unchanged(current) ? hit : null;
        }
    }

    /** Capture the same cells and tie order as World.rayTraceBlocks, including negative corner steps. */
    private static RayPath loadedPath(World world,Vec3 from,Vec3 end,BooleanSupplier current) {
        int x=MathHelper.floor_double(from.xCoord),y=MathHelper.floor_double(from.yCoord),z=MathHelper.floor_double(from.zCoord);
        int endX=MathHelper.floor_double(end.xCoord),endY=MathHelper.floor_double(end.yCoord),endZ=MathHelper.floor_double(end.zCoord);
        RayPath path=new RayPath(world);Vec3 cursor=from;
        for(int steps=0;steps<128;steps++) {
            if(!path.capture(new BlockPos(x,y,z),current))return null;
            if(x==endX&&y==endY&&z==endZ)return path;
            double bx=endX>x?x+1:x,by=endY>y?y+1:y,bz=endZ>z?z+1:z;
            double dx=end.xCoord-cursor.xCoord,dy=end.yCoord-cursor.yCoord,dz=end.zCoord-cursor.zCoord;
            double tx=endX==x?999:(bx-cursor.xCoord)/dx;
            double ty=endY==y?999:(by-cursor.yCoord)/dy;
            double tz=endZ==z?999:(bz-cursor.zCoord)/dz;
            if(tx==-0.0)tx=-1.0e-4;if(ty==-0.0)ty=-1.0e-4;if(tz==-0.0)tz=-1.0e-4;
            EnumFacing face;
            if(tx<ty&&tx<tz){face=endX>x?EnumFacing.WEST:EnumFacing.EAST;cursor=new Vec3(bx,cursor.yCoord+dy*tx,cursor.zCoord+dz*tx);}
            else if(ty<tz){face=endY>y?EnumFacing.DOWN:EnumFacing.UP;cursor=new Vec3(cursor.xCoord+dx*ty,by,cursor.zCoord+dz*ty);}
            else{face=endZ>z?EnumFacing.NORTH:EnumFacing.SOUTH;cursor=new Vec3(cursor.xCoord+dx*tz,cursor.yCoord+dy*tz,bz);}
            if(!finite(cursor))return null;
            x=MathHelper.floor_double(cursor.xCoord)-(face==EnumFacing.EAST?1:0);
            y=MathHelper.floor_double(cursor.yCoord)-(face==EnumFacing.UP?1:0);
            z=MathHelper.floor_double(cursor.zCoord)-(face==EnumFacing.SOUTH?1:0);
        }
        return null;
    }

    private static boolean pathCellLoaded(World world,BlockPos pos,BooleanSupplier current) {
        // Chunk availability is independent of height; a player's eyes may be above the build limit.
        BlockPos chunkPos=new BlockPos(pos.getX(),MathHelper.clamp_int(pos.getY(),0,255),pos.getZ());
        if(!current.getAsBoolean() || !world.isBlockLoaded(chunkPos) || !current.getAsBoolean())return false;
        net.minecraft.world.border.WorldBorder border=world.getWorldBorder();
        return current.getAsBoolean() && border!=null && border.contains(chunkPos) && current.getAsBoolean();
    }

    /** Block callbacks cannot validate a hit computed before an intervening cell changed. */
    private static final class RayPath {
        final World world;final Map<BlockPos,IBlockState> states=new LinkedHashMap<>();
        RayPath(World world){this.world=world;}
        boolean capture(BlockPos pos,BooleanSupplier current) {
            if(!pathCellLoaded(world,pos,current))return false;
            IBlockState state=world.getBlockState(pos);
            if(!current.getAsBoolean() || state==null)return false;
            IBlockState before=states.put(pos,state);
            return before==null || Objects.equals(before,state);
        }
        boolean unchanged(BooleanSupplier current) {
            for(Map.Entry<BlockPos,IBlockState> cell:states.entrySet()) {
                if(!pathCellLoaded(world,cell.getKey(),current))return false;
                IBlockState state=world.getBlockState(cell.getKey());
                if(!current.getAsBoolean() || !Objects.equals(cell.getValue(),state))return false;
            }
            return current.getAsBoolean();
        }
    }

    private static double castLength(Vec3 eyes,Vec3 direction,AxisAlignedBB bounds,EnumFacing face,double reach) {
        double distance=faceDistance(eyes,direction,bounds,face);
        if(!Double.isFinite(distance) || distance<=0 || distance>reach+EPS)return Double.NaN;
        double normal=face.getAxis()==EnumFacing.Axis.X?direction.xCoord:
                face.getAxis()==EnumFacing.Axis.Y?direction.yCoord:direction.zCoord;
        // World starts a block collision ray at its voxel edge. Vec3 rejects a remaining axis delta
        // below sqrt(1e-7), so the numerical overrun must be measured along the face normal.
        return Math.min(reach,distance+.001/Math.abs(normal));
    }

    private static double faceDistance(Vec3 eyes,Vec3 direction,AxisAlignedBB b,EnumFacing face) {
        switch(face){case EAST:return (b.maxX-eyes.xCoord)/direction.xCoord;case WEST:return (b.minX-eyes.xCoord)/direction.xCoord;
            case UP:return (b.maxY-eyes.yCoord)/direction.yCoord;case DOWN:return (b.minY-eyes.yCoord)/direction.yCoord;
            case SOUTH:return (b.maxZ-eyes.zCoord)/direction.zCoord;default:return (b.minZ-eyes.zCoord)/direction.zCoord;}
    }

    private static List<Vec3> points(Surface surface, Vec3 eyes, float yaw,float pitch,double margin) {
        List<Vec3> points=new ArrayList<>();
        Vec3 d=Entity.getVectorForRotation(pitch,yaw), center=point(surface,.5,.5);
        points.add(center);
        AxisAlignedBB b=surface.bounds;
        double nearestU,nearestV;
        switch(surface.face.getAxis()) {
            case X:nearestU=faceFraction(eyes.yCoord,b.minY,b.maxY,margin);nearestV=faceFraction(eyes.zCoord,b.minZ,b.maxZ,margin);break;
            case Y:nearestU=faceFraction(eyes.xCoord,b.minX,b.maxX,margin);nearestV=faceFraction(eyes.zCoord,b.minZ,b.maxZ,margin);break;
            default:nearestU=faceFraction(eyes.xCoord,b.minX,b.maxX,margin);nearestV=faceFraction(eyes.yCoord,b.minY,b.maxY,margin);
        }
        addPoint(points,point(surface,nearestU,nearestV));
        double denominator=d.xCoord*surface.face.getFrontOffsetX()+d.yCoord*surface.face.getFrontOffsetY()+d.zCoord*surface.face.getFrontOffsetZ();
        if (Math.abs(denominator)>1e-9) {
            double t=((center.xCoord-eyes.xCoord)*surface.face.getFrontOffsetX()
                    +(center.yCoord-eyes.yCoord)*surface.face.getFrontOffsetY()+(center.zCoord-eyes.zCoord)*surface.face.getFrontOffsetZ())/denominator;
            if (t>0 && Double.isFinite(t)) {
                Vec3 projected=eyes.addVector(d.xCoord*t,d.yCoord*t,d.zCoord*t);
                double u,v;
                switch(surface.face.getAxis()) {
                    case X:u=faceFraction(projected.yCoord,b.minY,b.maxY,margin);v=faceFraction(projected.zCoord,b.minZ,b.maxZ,margin);break;
                    case Y:u=faceFraction(projected.xCoord,b.minX,b.maxX,margin);v=faceFraction(projected.zCoord,b.minZ,b.maxZ,margin);break;
                    default:u=faceFraction(projected.xCoord,b.minX,b.maxX,margin);v=faceFraction(projected.yCoord,b.minY,b.maxY,margin);
                }
                addPoint(points,point(surface,u,v));
            }
        }
        for(double u:new double[]{.25,.5,.75}) for(double v:new double[]{.25,.5,.75})
            addPoint(points,point(surface,u,v));
        return points;
    }
    /** Keep each usable tangential coordinate; only a component outside the usable face moves inward. */
    private static double faceFraction(double coordinate,double min,double max,double margin) {
        double span=max-min;
        double inset=(Math.min(margin,span*.25)+Math.min(1.0e-4,span*.01))/span;
        double fraction=(coordinate-min)/span;
        if(fraction<inset)return Math.max(.2,inset);
        if(fraction>1-inset)return Math.min(.8,1-inset);
        return fraction;
    }
    private static void addPoint(List<Vec3> points,Vec3 point) {
        for(Vec3 present:points)if(present.xCoord==point.xCoord&&present.yCoord==point.yCoord&&present.zCoord==point.zCoord)return;
        points.add(point);
    }
    private static Vec3 point(Surface surface,double u,double v) {
        AxisAlignedBB b=surface.bounds;
        switch(surface.face.getAxis()) {
            case X:return new Vec3(surface.face==EnumFacing.EAST?b.maxX:b.minX,b.minY+(b.maxY-b.minY)*u,b.minZ+(b.maxZ-b.minZ)*v);
            case Y:return new Vec3(b.minX+(b.maxX-b.minX)*u,surface.face==EnumFacing.UP?b.maxY:b.minY,b.minZ+(b.maxZ-b.minZ)*v);
            default:return new Vec3(b.minX+(b.maxX-b.minX)*u,b.minY+(b.maxY-b.minY)*v,surface.face==EnumFacing.SOUTH?b.maxZ:b.minZ);
        }
    }
    private static boolean visibleSide(Vec3 eyes,Surface surface) {
        Vec3 center=point(surface,.5,.5);
        return (eyes.xCoord-center.xCoord)*surface.face.getFrontOffsetX()
                +(eyes.yCoord-center.yCoord)*surface.face.getFrontOffsetY()
                +(eyes.zCoord-center.zCoord)*surface.face.getFrontOffsetZ()>1e-7;
    }
    private static double distanceToFaceSq(Vec3 eyes,Surface surface) {
        AxisAlignedBB b=surface.bounds;Vec3 center=point(surface,.5,.5);
        double x=MathHelper.clamp_double(eyes.xCoord,b.minX,b.maxX),y=MathHelper.clamp_double(eyes.yCoord,b.minY,b.maxY),z=MathHelper.clamp_double(eyes.zCoord,b.minZ,b.maxZ);
        switch(surface.face.getAxis()){case X:x=center.xCoord;break;case Y:y=center.yCoord;break;default:z=center.zCoord;}
        return eyes.squareDistanceTo(new Vec3(x,y,z));
    }
    private static float[] rotation(Vec3 eyes,Vec3 point) {
        double x=point.xCoord-eyes.xCoord,y=point.yCoord-eyes.yCoord,z=point.zCoord-eyes.zCoord;
        return new float[]{(float)Math.toDegrees(Math.atan2(z,x))-90,(float)-Math.toDegrees(Math.atan2(y,Math.sqrt(x*x+z*z)))};
    }
    private static double difference(float[] aim,float yaw,float pitch) {
        return Math.hypot(MathHelper.wrapAngleTo180_float(aim[0]-yaw),aim[1]-pitch);
    }
    private static boolean onFace(AxisAlignedBB b,EnumFacing face,Vec3 hit) {
        if (!finite(hit) || !between(hit.xCoord,b.minX,b.maxX) || !between(hit.yCoord,b.minY,b.maxY)
                || !between(hit.zCoord,b.minZ,b.maxZ)) return false;
        switch(face){case EAST:return Math.abs(hit.xCoord-b.maxX)<=EPS;case WEST:return Math.abs(hit.xCoord-b.minX)<=EPS;
            case UP:return Math.abs(hit.yCoord-b.maxY)<=EPS;case DOWN:return Math.abs(hit.yCoord-b.minY)<=EPS;
            case SOUTH:return Math.abs(hit.zCoord-b.maxZ)<=EPS;default:return Math.abs(hit.zCoord-b.minZ)<=EPS;}
    }
    private static boolean interior(double value,double min,double max,double margin) {
        double inset=Math.min(margin,(max-min)*.25);return value>=min+inset-EPS && value<=max-inset+EPS;
    }
    private static boolean between(double v,double min,double max){return v>=min-EPS && v<=max+EPS;}
    private static boolean sameBounds(AxisAlignedBB a,AxisAlignedBB b){return a.minX==b.minX&&a.minY==b.minY&&a.minZ==b.minZ&&a.maxX==b.maxX&&a.maxY==b.maxY&&a.maxZ==b.maxZ;}
    private static boolean bounds(AxisAlignedBB b){return b!=null&&finite(new Vec3(b.minX,b.minY,b.minZ))&&finite(new Vec3(b.maxX,b.maxY,b.maxZ))&&b.maxX>b.minX&&b.maxY>b.minY&&b.maxZ>b.minZ;}
    private static boolean blockHit(MovingObjectPosition hit){return hit!=null&&hit.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK&&hit.getBlockPos()!=null&&hit.sideHit!=null&&finite(hit.hitVec);}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.xCoord)&&Double.isFinite(v.yCoord)&&Double.isFinite(v.zCoord);}
    private static boolean angles(float yaw,float pitch){return Float.isFinite(yaw)&&Float.isFinite(pitch)&&pitch>=-90&&pitch<=90;}
    private static BlockPos copy(BlockPos pos){return new BlockPos(pos.getX(),pos.getY(),pos.getZ());}
}
