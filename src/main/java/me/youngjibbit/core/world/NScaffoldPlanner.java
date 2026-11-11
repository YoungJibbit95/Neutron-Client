package me.youngjibbit.core.world;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.function.BooleanSupplier;

/**
 * A small, directed building plan. Geometry only: a result cannot authorize a
 * rotation, select an item or place a block. The controller validates it again.
 * The five neighbour/face pairs and movement-relative Godbridge preference are
 * adapted from the authorized Demise reference; see docs/NSCAFFOLD.md.
 */
public final class NScaffoldPlanner {
    public static final int MAX_DESTINATIONS = 3;
    public static final int MAX_NEW_FACE_CHECKS = MAX_DESTINATIONS * 5;
    private static final float MAX_HEADING_CORRECTION=18;
    private static final EnumFacing[] NEIGHBOURS = {
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.DOWN
    };

    /** All production implementations retain the placement service's real world checks. */
    public interface Access {
        boolean replaceable(BlockPos destination);
        boolean valid(PlacementTarget target);
        PlacementTarget ray(float yaw, float pitch);
        PlacementTarget retain(PlacementTarget target, float yaw, float pitch);
        PlacementTarget face(BlockPos support, EnumFacing facing, Vec3 eyes, float yaw, float pitch);
    }

    public static final class Sample {
        public final Vec3 feet, eyes;
        public final float yaw, pitch, forward, strafe;
        public final int level;
        public final double lookAhead;
        public final boolean godbridge;
        public final float preferredPitch;
        public Sample(Vec3 feet, Vec3 eyes, float yaw, float pitch, float forward, float strafe,
                      int level, double lookAhead, boolean godbridge, float preferredPitch) {
            this.feet=feet;this.eyes=eyes;this.yaw=yaw;this.pitch=pitch;
            this.forward=forward;this.strafe=strafe;this.level=level;
            this.lookAhead=lookAhead;this.godbridge=godbridge;this.preferredPitch=preferredPitch;
        }
        private boolean finite() {
            return finiteVector(feet) && finiteVector(eyes) && Float.isFinite(yaw)
                    && Float.isFinite(pitch) && Math.abs(pitch)<=90 && Float.isFinite(forward)
                    && Float.isFinite(strafe) && Double.isFinite(lookAhead) && lookAhead>=0 && lookAhead<=1
                    && level>=0 && level<256 && Float.isFinite(preferredPitch)
                    && preferredPitch>0 && preferredPitch<90;
        }
    }

    public static final class Decision {
        public final PlacementTarget target;
        public final float yaw, pitch;
        public final boolean retained;
        private Decision(PlacementTarget target,float yaw,float pitch,boolean retained) {
            this.target=target;this.yaw=yaw;this.pitch=pitch;this.retained=retained;
        }
    }

    private final Access access;
    private PlacementTarget held;
    private BlockPos lastSupportedCell;
    private long revision;
    private boolean headingHeld, previousGodbridge;
    private float heading, previousForward, previousStrafe;
    private int lastFaceChecks;

    public NScaffoldPlanner(Access access) {
        if(access==null)throw new IllegalArgumentException("access");
        this.access=access;
    }
    public NScaffoldPlanner(final ScaffoldPlacementService service) {
        this(new Access() {
            public boolean replaceable(BlockPos p){return service.isReplaceableDestination(p);}
            public boolean valid(PlacementTarget p){return service.isValid(p);}
            public PlacementTarget ray(float y,float p){return service.planBridgeRay(y,p);}
            public PlacementTarget retain(PlacementTarget p,float y,float pitch){return service.retainBridgeHit(p,y,pitch);}
            public PlacementTarget face(BlockPos p,EnumFacing f,Vec3 e,float y,float pitch){
                return service.planBridgeFace(p,f,e,y,pitch);
            }
        });
    }

    public void reset() {
        revision++;held=null;lastSupportedCell=null;headingHeld=false;lastFaceChecks=0;
        previousForward=previousStrafe=0;previousGodbridge=false;
    }
    public void placed() { held=null; }
    public int getLastFaceChecks(){return lastFaceChecks;}

    /** Recheck the same small route after physics without acquiring a different target. */
    public boolean onRoute(PlacementTarget target,Sample sample,BooleanSupplier stillCurrent) {
        final long expected=revision;
        if(target==null || sample==null || !sample.finite() || stillCurrent==null
                || !current(expected,stillCurrent))return false;
        BlockPos base=new BlockPos(sample.feet.xCoord,sample.level,sample.feet.zCoord);
        boolean free=access.replaceable(base);
        if(!current(expected,stillCurrent))return false;
        BlockPos goal=free?base:projectedCell(sample);
        if(!free && goal.equals(base))return false;
        boolean needed=free || access.replaceable(goal);
        return current(expected,stillCurrent) && needed && member(route(routeOrigin(base,free),goal),target.getDestination());
    }

    /** Never scans a radius: the needed cell and at most two diagonal connectors. */
    public Decision plan(Sample sample, BooleanSupplier stillCurrent) {
        final long expected=revision;
        if(sample==null || !sample.finite() || stillCurrent==null || !current(expected,stillCurrent))return null;
        lastFaceChecks=0;
        BlockPos base=new BlockPos(sample.feet.xCoord,sample.level,sample.feet.zCoord);
        boolean baseFree=access.replaceable(base);
        if(!current(expected,stillCurrent))return null;
        if(!baseFree)lastSupportedCell=base;
        BlockPos goal=baseFree?base:projectedCell(sample);
        if(goal.equals(base) && !baseFree){held=null;return null;}
        boolean goalFree=baseFree || access.replaceable(goal);
        if(!current(expected,stillCurrent))return null;
        if(!goalFree){held=null;return null;}
        BlockPos[] route=route(routeOrigin(base,baseFree),goal);

        boolean directionChanged=Math.signum(previousForward)!=Math.signum(sample.forward)
                || Math.signum(previousStrafe)!=Math.signum(sample.strafe);
        float preferredYaw=headingHeld && previousGodbridge==sample.godbridge && !directionChanged?heading:sample.yaw;
        if(sample.godbridge) {
            double desired=headingHeld && previousGodbridge && !directionChanged?heading:sample.pitch>=60?
                    Math.round(sample.yaw/45)*45:
                    ScaffoldRotationPlan.godbridgeMovementYaw(sample.feet.xCoord,sample.feet.zCoord,
                            sample.yaw,sample.forward,sample.strafe);
            if(!Double.isFinite(desired))return null;
            preferredYaw=(float)desired;
        }
        // Following a previously steep edge angle while stepping away projects below the face.
        // Backward building reacquires the same face from a stable downward preference instead.
        float preferredPitch=sample.godbridge?sample.preferredPitch:sample.forward<0?75.7f:sample.pitch;
        PlacementTarget anchor=held;
        if(anchor!=null && member(route,anchor.getDestination()) && !directionChanged
                && sample.godbridge==previousGodbridge) {
            PlacementTarget retained=access.retain(anchor,sample.yaw,sample.pitch);
            if(!current(expected,stillCurrent))return null;
            if(retained!=null && acceptable(retained,sample,preferredYaw))
                return accept(retained,sample,true,true,preferredYaw,expected,stillCurrent);
            boolean valid=access.valid(anchor);
            if(!current(expected,stillCurrent))return null;
            // Standard keeps its concrete point while the visible turn is still approaching it.
            if(valid && !sample.godbridge && sample.forward>=0 && acceptable(anchor,sample,preferredYaw))
                return accept(anchor,sample,true,false,preferredYaw,expected,stillCurrent);
            PlacementTarget refined=access.face(anchor.getBlockPos(),anchor.getFacing(),sample.eyes,
                    preferredYaw,preferredPitch);
            if(!current(expected,stillCurrent))return null;
            if(refined!=null && member(route,refined.getDestination()) && acceptable(refined,sample,preferredYaw))
                return accept(refined,sample,true,false,preferredYaw,expected,stillCurrent);
        }
        // An already useful visible ray takes precedence over acquiring a different edge.
        if(!sample.godbridge || headingHeld && !directionChanged) {
            PlacementTarget ray=access.ray(sample.yaw,sample.pitch);
            if(!current(expected,stillCurrent))return null;
            if(ray!=null && member(route,ray.getDestination()) && acceptable(ray,sample,preferredYaw))
                return accept(ray,sample,false,true,preferredYaw,expected,stillCurrent);
        }
        for(BlockPos destination:route) {
            if(destination==null)continue;
            boolean free=access.replaceable(destination);
            if(!current(expected,stillCurrent))return null;
            if(!free)continue;
            PlacementTarget best=null;double bestCost=Double.POSITIVE_INFINITY;
            for(EnumFacing neighbour:NEIGHBOURS) {
                lastFaceChecks++;
                PlacementTarget candidate=access.face(destination.offset(neighbour),neighbour.getOpposite(),
                        sample.eyes,preferredYaw,preferredPitch);
                if(!current(expected,stillCurrent))return null;
                if(candidate==null || !destination.equals(candidate.getDestination()) || !acceptable(candidate,sample,preferredYaw))continue;
                ScaffoldRotationPlan pose=ScaffoldRotationPlan.build("Back",sample.eyes,candidate.getHitVec());
                if(pose==null)continue;
                double cost=Math.abs(MathHelper.wrapAngleTo180_float(pose.getYaw()-preferredYaw))
                        +Math.abs(pose.getPitch()-preferredPitch)*.75;
                if(cost<bestCost){bestCost=cost;best=candidate;}
            }
            if(best!=null)return accept(best,sample,false,false,preferredYaw,expected,stillCurrent);
        }
        if(current(expected,stillCurrent))held=null;
        return null;
    }

    private Decision accept(PlacementTarget target,Sample sample,boolean retained,boolean keepView,float preferredYaw,
                            long expected,BooleanSupplier valid) {
        if(!current(expected,valid))return null;
        boolean usable=access.valid(target);
        if(!current(expected,valid) || !usable)return null;
        ScaffoldRotationPlan pose=ScaffoldRotationPlan.build("Back",sample.eyes,target.getHitVec());
        if(pose==null)return null;
        held=target;heading=preferredYaw;headingHeld=sample.godbridge || sample.forward<0;
        previousGodbridge=sample.godbridge;previousForward=sample.forward;previousStrafe=sample.strafe;
        // Lookup-table direction -> intercept -> atan2 is not an exact round trip.
        // A currently valid ray keeps its real angles bit-for-bit.
        return new Decision(target,keepView?sample.yaw:pose.getYaw(),keepView?sample.pitch:pose.getPitch(),retained);
    }
    private static boolean acceptable(PlacementTarget target,Sample sample,float preferredYaw) {
        if(!sample.godbridge && sample.forward>=0)return true;
        ScaffoldRotationPlan pose=ScaffoldRotationPlan.build("Back",sample.eyes,target.getHitVec());
        return pose!=null && Math.abs(MathHelper.wrapAngleTo180_float(pose.getYaw()-preferredYaw))<=MAX_HEADING_CORRECTION;
    }
    private BlockPos routeOrigin(BlockPos base,boolean baseFree) {
        return baseFree && lastSupportedCell!=null && lastSupportedCell.getY()==base.getY()
                && Math.abs(lastSupportedCell.getX()-base.getX())<=1 && Math.abs(lastSupportedCell.getZ()-base.getZ())<=1
                ?lastSupportedCell:base;
    }
    private boolean current(long expected,BooleanSupplier valid) {
        if(revision!=expected)return false;
        boolean result=valid.getAsBoolean();
        return result && revision==expected;
    }
    private static boolean member(BlockPos[] route,BlockPos destination) {
        for(BlockPos pos:route)if(pos!=null && pos.equals(destination))return true;
        return false;
    }
    private static BlockPos projectedCell(Sample sample) {
        double magnitude=Math.hypot(sample.forward,sample.strafe);
        if(magnitude<1e-5)return new BlockPos(sample.feet.xCoord,sample.level,sample.feet.zCoord);
        double yaw=Math.toRadians(sample.yaw),s=sample.strafe/magnitude,f=sample.forward/magnitude;
        return new BlockPos(sample.feet.xCoord+(s*Math.cos(yaw)-f*Math.sin(yaw))*sample.lookAhead,
                sample.level,sample.feet.zCoord+(f*Math.cos(yaw)+s*Math.sin(yaw))*sample.lookAhead);
    }
    private static BlockPos[] route(BlockPos base,BlockPos goal) {
        if(base.getX()!=goal.getX() && base.getZ()!=goal.getZ())
            return new BlockPos[]{goal,new BlockPos(goal.getX(),goal.getY(),base.getZ()),
                    new BlockPos(base.getX(),goal.getY(),goal.getZ())};
        return new BlockPos[]{goal};
    }
    private static boolean finiteVector(Vec3 p) {
        return p!=null && Double.isFinite(p.xCoord) && Double.isFinite(p.yCoord) && Double.isFinite(p.zCoord);
    }

    /** A visible camera turn with one angular budget; no input reconstruction or noise. */
    public static float[] turn(float yaw,float pitch,float targetYaw,float targetPitch,float maximum) {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(targetYaw)
                ||!Float.isFinite(targetPitch)||!Float.isFinite(maximum)||maximum<=0)return null;
        float dy=MathHelper.wrapAngleTo180_float(targetYaw-yaw),dp=targetPitch-pitch;
        double length=Math.hypot(dy,dp),scale=length>maximum?maximum/length:1;
        return new float[]{yaw+(float)(dy*scale),MathHelper.clamp_float(pitch+(float)(dp*scale),-90,90)};
    }
}
