package me.youngjibbit.core.world;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Bounded, current-world clutch geometry. A decision grants no rotation, input,
 * item or placement authority: the ordinary visible controller validates it
 * again after physics. Demise's clutch criteria, face preferences and short
 * retention periods inform these controls; no global input/packet subsystem is
 * copied. See docs/NSCAFFOLD-DEMISE-PARITY.md for the source and differences.
 */
public final class NScaffoldClutchPlanner {
    public static final int PREDICTION_TICKS=3;
    public static final int MAX_DESTINATIONS=7;
    public static final int MAX_NEW_FACE_CHECKS=MAX_DESTINATIONS*5;
    private static final int PREDICTION_RETENTION_TICKS=4;
    private static final int SNEAK_RETENTION_TICKS=2;
    private static final double MAX_HORIZONTAL_PROJECTION=2.0;
    private static final double MAX_VERTICAL_PROJECTION=3.0;
    private static final EnumFacing[] NEIGHBOURS={
            EnumFacing.WEST,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.NORTH,EnumFacing.DOWN
    };

    public enum Criteria { MOUSE_OVER, PREDICTION }
    public enum AimMode { NORMAL, CENTER }

    public static final class Options {
        public final Criteria criteria;
        public final AimMode aimMode;
        public final boolean urgentAlways,urgentOnMiss,urgentPredict,autoSneak;
        public final int groundTicksToSneak;
        public Options(Criteria criteria,AimMode aimMode,boolean urgentAlways,boolean urgentOnMiss,
                       boolean urgentPredict,boolean autoSneak,int groundTicksToSneak) {
            this.criteria=criteria;this.aimMode=aimMode;
            this.urgentAlways=urgentAlways;this.urgentOnMiss=urgentOnMiss;
            this.urgentPredict=urgentPredict;this.autoSneak=autoSneak;
            this.groundTicksToSneak=Math.max(0,Math.min(10,groundTicksToSneak));
        }
        private boolean valid(){return criteria!=null && aimMode!=null;}
        private boolean same(Options other) {
            return other!=null && criteria==other.criteria && aimMode==other.aimMode
                    && urgentAlways==other.urgentAlways && urgentOnMiss==other.urgentOnMiss
                    && urgentPredict==other.urgentPredict && autoSneak==other.autoSneak
                    && groundTicksToSneak==other.groundTicksToSneak;
        }
    }

    public static final class Sample {
        public final Vec3 feet,eyes,motion;
        public final float yaw,pitch;
        public final boolean moving,onGround;
        public final int groundTicks;
        public final long tick;
        public Sample(Vec3 feet,Vec3 eyes,Vec3 motion,float yaw,float pitch,boolean moving,
                      boolean onGround,int groundTicks,long tick) {
            this.feet=feet;this.eyes=eyes;this.motion=motion;this.yaw=yaw;this.pitch=pitch;
            this.moving=moving;this.onGround=onGround;this.groundTicks=groundTicks;this.tick=tick;
        }
        private boolean valid() {
            return finite(feet) && finite(eyes) && finite(motion) && Float.isFinite(yaw)
                    && Float.isFinite(pitch) && Math.abs(pitch)<=90 && groundTicks>=0 && tick>=0
                    && Math.abs(feet.xCoord)<=30000000 && Math.abs(feet.zCoord)<=30000000
                    && feet.yCoord>=1 && feet.yCoord<=256 && eyes.squareDistanceTo(feet)<=9
                    && Math.abs(motion.xCoord)<=10 && Math.abs(motion.yCoord)<=10
                    && Math.abs(motion.zCoord)<=10;
        }
    }

    public static final class Decision {
        public final PlacementTarget target;
        public final float yaw,pitch;
        public final boolean retained,urgent,predicted;
        private Decision(PlacementTarget target,float yaw,float pitch,boolean retained,
                         boolean urgent,boolean predicted) {
            this.target=target;this.yaw=yaw;this.pitch=pitch;this.retained=retained;
            this.urgent=urgent;this.predicted=predicted;
        }
    }

    /** Testable boundary; the production adapter always uses normal reach and first-face validation. */
    public interface Access {
        boolean replaceable(BlockPos destination);
        boolean valid(PlacementTarget target);
        PlacementTarget ray(float yaw,float pitch);
        PlacementTarget retain(PlacementTarget target,float yaw,float pitch);
        PlacementTarget face(BlockPos support,EnumFacing face,Vec3 eyes,float yaw,float pitch,boolean center);
    }

    private static final class Route {
        final List<BlockPos> cells=new ArrayList<>(MAX_DESTINATIONS);
        boolean currentFree,futureGap,fallingGap;
        void add(BlockPos cell){if(cells.size()<MAX_DESTINATIONS && !cells.contains(cell))cells.add(cell);}
    }

    private final Access access;
    private long revision,lastTick=Long.MIN_VALUE,lastTriggerTick=Long.MIN_VALUE,lastSneakTick=Long.MIN_VALUE;
    private PlacementTarget held;
    private Options activeOptions;
    private int lastFaceChecks,lastRayChecks,lastValidationChecks;

    public NScaffoldClutchPlanner(Access access) {
        if(access==null)throw new IllegalArgumentException("access");
        this.access=access;
    }
    public NScaffoldClutchPlanner(final ScaffoldPlacementService service) {
        this(new Access() {
            public boolean replaceable(BlockPos p){return service.isReplaceableDestination(p);}
            public boolean valid(PlacementTarget p){return service.isValid(p);}
            public PlacementTarget ray(float yaw,float pitch){return service.planBridgeRay(yaw,pitch);}
            public PlacementTarget retain(PlacementTarget p,float yaw,float pitch){return service.retainBridgeHit(p,yaw,pitch);}
            public PlacementTarget face(BlockPos p,EnumFacing f,Vec3 eyes,float yaw,float pitch,boolean center) {
                return center?service.planFace(p,f,null):service.planBridgeFace(p,f,eyes,yaw,pitch);
            }
        });
        if(service==null)throw new IllegalArgumentException("service");
    }

    public void reset() {
        revision++;held=null;activeOptions=null;lastFaceChecks=lastRayChecks=lastValidationChecks=0;
        lastTick=lastTriggerTick=lastSneakTick=Long.MIN_VALUE;
    }
    public void placed(){held=null;}
    public int getLastFaceChecks(){return lastFaceChecks;}
    /** Explicit current-ray and held-ray calls, separate from the normal service's per-face validation. */
    public int getLastRayChecks(){return lastRayChecks;}
    public int getLastValidationChecks(){return lastValidationChecks;}

    /**
     * Find a current, reachable face along this short physical trajectory. An
     * ascending jump is left to the normal building mode until its apex. Grounded
     * predictions keep the current height so gravity does not invent a hole in
     * an otherwise continuous floor. World collision/reach remains authoritative.
     */
    public Decision plan(Sample sample,Options options,BooleanSupplier stillCurrent) {
        if(sample==null || !sample.valid() || options==null || !options.valid() || stillCurrent==null)return null;
        // An already revoked caller cannot reset a newer valid owner's cache by
        // presenting an old clock or old settings.
        if(!current(revision,stillCurrent))return null;
        if(lastTick!=Long.MIN_VALUE && sample.tick<lastTick || activeOptions!=null && !activeOptions.same(options))reset();
        final long expected=revision;
        if(!current(expected,stillCurrent))return null;
        lastFaceChecks=lastRayChecks=lastValidationChecks=0;lastTick=sample.tick;activeOptions=options;
        if(!sample.onGround && sample.motion.yCoord>.08){held=null;lastTriggerTick=Long.MIN_VALUE;return null;}
        BlockPos belowTwo=new BlockPos(sample.feet.xCoord,sample.feet.yCoord-2,sample.feet.zCoord);
        boolean gap=access.replaceable(belowTwo);
        if(!current(expected,stillCurrent))return null;
        if(!gap){held=null;lastTriggerTick=Long.MIN_VALUE;return null;}
        Route route=route(sample,expected,stillCurrent);
        if(route==null || !current(expected,stillCurrent))return null;
        if(!route.currentFree && !route.futureGap){held=null;return null;}
        lastRayChecks++;
        PlacementTarget ray=access.ray(sample.yaw,sample.pitch);
        if(!current(expected,stillCurrent))return null;
        boolean usefulRay=ray!=null && route.cells.contains(ray.getDestination()) && valid(ray);
        if(!current(expected,stillCurrent))return null;
        boolean trigger=options.criteria==Criteria.MOUSE_OVER?(!usefulRay || !sample.moving)
                :(route.futureGap || route.fallingGap || !sample.moving);
        boolean retainedWindow=options.criteria==Criteria.PREDICTION && held!=null
                && within(sample.tick,lastTriggerTick,PREDICTION_RETENTION_TICKS);
        if(!trigger && !retainedWindow){held=null;return null;}

        PlacementTarget anchor=held;
        if(anchor!=null && route.cells.contains(anchor.getDestination())) {
            if(options.aimMode==AimMode.NORMAL) {
                lastRayChecks++;
                PlacementTarget retained=access.retain(anchor,sample.yaw,sample.pitch);
                if(!current(expected,stillCurrent))return null;
                if(retained!=null && sameFace(anchor,retained))
                    return accept(retained,sample,options,route,ray,true,true,trigger,expected,stillCurrent);
            }
            boolean valid=valid(anchor);
            if(!current(expected,stillCurrent))return null;
            if(valid)return accept(anchor,sample,options,route,ray,true,false,trigger,expected,stillCurrent);
        }
        if(usefulRay && options.aimMode==AimMode.NORMAL)
            return accept(ray,sample,options,route,ray,false,true,trigger,expected,stillCurrent);
        for(BlockPos destination:route.cells) {
            boolean free=access.replaceable(destination);
            if(!current(expected,stillCurrent))return null;
            if(!free)continue;
            PlacementTarget best=null;double cost=Double.POSITIVE_INFINITY;
            for(EnumFacing neighbour:NEIGHBOURS) {
                lastFaceChecks++;
                PlacementTarget candidate=access.face(destination.offset(neighbour),neighbour.getOpposite(),
                        sample.eyes,sample.yaw,sample.pitch,options.aimMode==AimMode.CENTER);
                if(!current(expected,stillCurrent))return null;
                if(candidate==null || !destination.equals(candidate.getDestination()))continue;
                boolean valid=valid(candidate);
                if(!current(expected,stillCurrent))return null;
                if(!valid)continue;
                ScaffoldRotationPlan aim=ScaffoldRotationPlan.build("Back",sample.eyes,candidate.getHitVec());
                if(aim==null)continue;
                double candidateCost=Math.hypot(MathHelper.wrapAngleTo180_float(aim.getYaw()-sample.yaw),
                        aim.getPitch()-sample.pitch);
                if(candidateCost<cost){best=candidate;cost=candidateCost;}
            }
            if(best!=null)return accept(best,sample,options,route,ray,false,false,trigger,expected,stillCurrent);
        }
        if(current(expected,stillCurrent))held=null;
        return null;
    }

    /** Revalidate this target after physics without a face search or acquiring another intent. */
    public boolean onRoute(PlacementTarget target,Sample sample,Options options,BooleanSupplier stillCurrent) {
        final long expected=revision;
        if(target==null || sample==null || !sample.valid() || options==null || !options.valid()
                || stillCurrent==null || held==null || target.trajectoryKey!=held.trajectoryKey
                || activeOptions==null || !activeOptions.same(options) || !within(sample.tick,lastTick,2)
                || !current(expected,stillCurrent))return false;
        Route route=route(sample,expected,stillCurrent);
        if(route==null || !route.cells.contains(target.getDestination()) || !current(expected,stillCurrent))return false;
        // Do not mutate the PRE accounting, held target or timing state in POST.
        boolean valid=access.valid(target);
        return current(expected,stillCurrent) && valid;
    }

    /** Pure request query. The module must still own a current scene/input stage before applying it. */
    public boolean wantsSneak(Sample sample,Options options) {
        return sample!=null && sample.valid() && options!=null && options.valid() && options.autoSneak
                && activeOptions!=null && activeOptions.same(options)
                && within(sample.tick,lastSneakTick,SNEAK_RETENTION_TICKS);
    }

    private Decision accept(PlacementTarget target,Sample sample,Options options,Route route,PlacementTarget ray,
                            boolean retained,boolean keepView,boolean trigger,long expected,BooleanSupplier current) {
        if(target==null || !route.cells.contains(target.getDestination()) || !current(expected,current))return null;
        boolean usable=valid(target);
        if(!current(expected,current) || !usable)return null;
        ScaffoldRotationPlan aim=ScaffoldRotationPlan.build("Back",sample.eyes,target.getHitVec());
        if(aim==null)return null;
        held=target;
        if(trigger)lastTriggerTick=sample.tick;
        if(options.autoSneak && sample.moving && sample.onGround && sample.groundTicks>=options.groundTicksToSneak)
            lastSneakTick=sample.tick;
        boolean urgent=options.urgentAlways || options.urgentOnMiss && !sameFace(ray,target)
                || options.urgentPredict && (route.futureGap || route.fallingGap);
        return new Decision(target,keepView?sample.yaw:aim.getYaw(),keepView?sample.pitch:aim.getPitch(),
                retained,urgent,route.futureGap);
    }

    private Route route(Sample sample,long expected,BooleanSupplier current) {
        Route route=new Route();
        BlockPos previous=belowFeet(sample.feet);
        route.add(previous);route.currentFree=access.replaceable(previous);
        if(!current(expected,current))return null;
        // A fast fall may exceed the deliberately short spatial prediction
        // horizon. Its current unsupported cell remains an immediate concern;
        // the cap must not disable recovery from a presently reachable face.
        route.fallingGap=route.currentFree && !sample.onGround && sample.motion.yCoord<=0;
        Vec3 next=sample.feet;
        double vx=sample.motion.xCoord,vy=sample.motion.yCoord,vz=sample.motion.zCoord;
        for(int tick=0;tick<PREDICTION_TICKS;tick++) {
            next=next.addVector(vx,sample.onGround?0:vy,vz);
            if(Math.hypot(next.xCoord-sample.feet.xCoord,next.zCoord-sample.feet.zCoord)>MAX_HORIZONTAL_PROJECTION
                    || Math.abs(next.yCoord-sample.feet.yCoord)>MAX_VERTICAL_PROJECTION || next.yCoord<1 || next.yCoord>256)break;
            BlockPos projected=belowFeet(next);
            boolean free=projected.equals(previous) && route.cells.size()==1?route.currentFree:access.replaceable(projected);
            if(!current(expected,current))return null;
            route.futureGap|=free;route.add(projected);
            if(previous.getY()==projected.getY() && Math.abs(previous.getX()-projected.getX())==1
                    && Math.abs(previous.getZ()-projected.getZ())==1) {
                route.add(new BlockPos(projected.getX(),projected.getY(),previous.getZ()));
                route.add(new BlockPos(previous.getX(),projected.getY(),projected.getZ()));
            }
            previous=projected;vx*=.91;vz*=.91;vy=(vy-.08)*.98;
        }
        return route;
    }
    private boolean valid(PlacementTarget target){lastValidationChecks++;return access.valid(target);}
    private static BlockPos belowFeet(Vec3 feet){return new BlockPos(feet.xCoord,MathHelper.floor_double(feet.yCoord)-1,feet.zCoord);}
    private boolean current(long expected,BooleanSupplier valid) {
        if(revision!=expected)return false;
        boolean result=valid.getAsBoolean();
        return result && revision==expected;
    }
    private static boolean sameFace(PlacementTarget a,PlacementTarget b) {
        return a!=null && b!=null && a.getBlockPos().equals(b.getBlockPos()) && a.getFacing()==b.getFacing()
                && a.getDestination().equals(b.getDestination());
    }
    private static boolean within(long tick,long since,int duration){return since!=Long.MIN_VALUE && tick>=since && tick-since<duration;}
    private static boolean finite(Vec3 p){return p!=null && Double.isFinite(p.xCoord) && Double.isFinite(p.yCoord) && Double.isFinite(p.zCoord);}
}
