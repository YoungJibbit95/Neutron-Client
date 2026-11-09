package me.youngjibbit.core.world;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Restricted geometry adapter. All candidates come from Neutron's existing
 * ScaffoldPlacementService, not an AI model or a new placement implementation.
 * Actual authority remains in its placeWithAuthority PRE/C03/POST gates.
 */
public final class AdaptiveBridgeScaffoldAdapter {
    static final int MAX_RECOVERY_DESTINATIONS=12;
    private static final double FOOTPRINT_HALF_WIDTH=.28;
    private final AdaptiveBridgePlanner planner=new AdaptiveBridgePlanner();
    private final Thread owningThread=Thread.currentThread();

    public static final class Prepared {
        public final PlacementTarget target;
        public final float yaw,pitch;
        /** Inert suggestion, not permission to alter physical key state. */
        public final boolean suggestSneak;
        private Prepared(PlacementTarget target,float yaw,float pitch,boolean suggestSneak) {
            this.target=target;this.yaw=yaw;this.pitch=pitch;this.suggestSneak=suggestSneak;
        }
    }

    public void reset() {planner.reset();}

    /**
     * Inert acquisition for enable/PRE. Unlike findTarget, an occupied current
     * foot cell does not stop the short look-ahead. Every face still passes the
     * existing service's real current-world, stack, reach and raycast checks.
     * Normal bridging samples only three centerline cells (eighteen planFace calls).
     * An unsupported diagonal corner may try two adjacent connector faces, once.
     * A measured fall may add the actual short landing footprint and one lower
     * layer, capped at twelve cells. There is no arbitrary horizontal radius.
     * Subsequent PRE preparation adds at most five alternative faces.
     * No planner state, rotation request, inventory write or placement is made.
     */
    public PlacementTarget findEarlyTarget(ScaffoldPlacementService service,
            AdaptiveBridgePlanner.Frame frame,int blockY,PlacementTarget preferred) {
        if(!validFrame(service,frame,blockY))return null;
        double[] predicted=AdaptiveBridgePlanner.predict(frame);
        for(BlockPos destination:destinations(frame,blockY,predicted)) {
            // An actual gap beneath the player takes precedence over a future
            // kept point. Within one destination keep its exact immutable intent.
            if(preferred!=null && destination.equals(preferred.getDestination())
                    && validPreview(service,frame,preferred,predicted))return preferred;
            for(EnumFacing face:EnumFacing.values()) {
                PlacementTarget candidate=service.planFace(
                        destination.offset(face.getOpposite()),face,null);
                if(validPreview(service,frame,candidate,predicted))return candidate;
            }
        }
        return findDiagonalConnector(service,frame,blockY,predicted,preferred);
    }

    /**
     * A one-block diagonal step needs one orthogonal connector before its center
     * cell has a supporting face. Only the previous corner block may supply these
     * two proposals; this is not a search around neighboring rows.
     */
    private PlacementTarget findDiagonalConnector(ScaffoldPlacementService service,
            AdaptiveBridgePlanner.Frame frame,int blockY,double[] predicted,PlacementTarget preferred) {
        Corner corner=corner(frame,blockY);
        if(corner==null || !service.isReplaceableDestination(
                new BlockPos(frame.feetX,blockY,frame.feetZ)))return null;
        if(isConnector(frame,preferred,corner) && validPreview(service,frame,preferred,predicted))return preferred;
        for(EnumFacing face:new EnumFacing[]{corner.first,corner.second}) {
            BlockPos destination=corner.support.offset(face);
            if(!overlapsFootprint(frame.feetX,frame.feetZ,destination))continue;
            PlacementTarget candidate=service.planFace(corner.support,face,null);
            if(validPreview(service,frame,candidate,predicted))return candidate;
        }
        return null;
    }

    private static final class Corner {
        final BlockPos support;
        final EnumFacing first,second;
        Corner(BlockPos support,EnumFacing first,EnumFacing second) {
            this.support=support;this.first=first;this.second=second;
        }
    }

    private static Corner corner(AdaptiveBridgePlanner.Frame frame,int blockY) {
        if(frame==null || AdaptiveBridgePlanner.falling(frame))return null;
        double yaw=Math.toRadians(frame.yaw);
        double dx=-Math.sin(yaw)*frame.forward+Math.cos(yaw)*frame.strafe;
        double dz=Math.cos(yaw)*frame.forward+Math.sin(yaw)*frame.strafe;
        if(frame.hasLane && Math.hypot(frame.feetX-frame.laneX,frame.feetZ-frame.laneZ)<=FOOTPRINT_HALF_WIDTH) {
            dx=frame.laneDirectionX;dz=frame.laneDirectionZ;
        }
        double length=Math.hypot(dx,dz);
        if(!Double.isFinite(length) || length<1e-5 || Math.min(Math.abs(dx),Math.abs(dz))<length*1e-5)return null;
        EnumFacing x=dx>0?EnumFacing.EAST:EnumFacing.WEST;
        EnumFacing z=dz>0?EnumFacing.SOUTH:EnumFacing.NORTH;
        double[] start=routeStart(frame);
        BlockPos support=new BlockPos(start[0],blockY,start[1]).offset(x.getOpposite()).offset(z.getOpposite());
        // Keep ties deterministic even when sin/cos differ by floating-point noise.
        return Math.abs(dx)+1e-6>=Math.abs(dz)?new Corner(support,x,z):new Corner(support,z,x);
    }

    private static boolean isConnector(AdaptiveBridgePlanner.Frame frame,PlacementTarget target,Corner corner) {
        return target!=null && corner!=null && corner.support.equals(target.getBlockPos())
                && (target.getFacing()==corner.first || target.getFacing()==corner.second)
                && corner.support.offset(target.getFacing()).equals(target.getDestination())
                && overlapsFootprint(frame.feetX,frame.feetZ,target.getDestination());
    }

    /**
     * Pure membership of an already checked PRE target. A connector and its
     * following diagonal cell share the short remaining support window, so the
     * host may raise aim urgency without acquiring another face or placement.
     */
    public boolean isDiagonalConnector(AdaptiveBridgePlanner.Frame frame,PlacementTarget target,int blockY) {
        return Thread.currentThread()==owningThread && frame!=null && frame.scope!=null
                && frame.scope.usable() && frame.authorized && AdaptiveBridgePlanner.validMotion(frame)
                && blockY>=0 && blockY<=255 && target!=null
                && target.world==frame.scope.world && target.player==frame.scope.player
                && isConnector(frame,target,corner(frame,blockY));
    }

    /**
     * POST may retain only a destination belonging to this immediate route.
     * A normal look-ahead remains an aim preview until it is below the feet.
     * Recovery may use the short landing footprint; it never inherits the legacy
     * scorer's two-block retention square. This is not placement authorization.
     */
    public boolean stillNeeded(ScaffoldPlacementService service,PlacementTarget target,
            AdaptiveBridgePlanner.Frame frame,int blockY) {
        if(!validFrame(service,frame,blockY) || target==null)return false;
        // The physical foot cell, not a nearby lane cell, decides if support is missing.
        if(!service.isReplaceableDestination(new BlockPos(frame.feetX,blockY,frame.feetZ)))return false;
        BlockPos destination=target.getDestination();
        double[] predicted=AdaptiveBridgePlanner.predict(frame),start=routeStart(frame);
        boolean relevant=AdaptiveBridgePlanner.falling(frame)
                ?destinations(frame,blockY,predicted).contains(destination)
                :(new BlockPos(start[0],blockY,start[1]).equals(destination)
                    && overlapsFootprint(frame.feetX,frame.feetZ,destination))
                    || isConnector(frame,target,corner(frame,blockY));
        return relevant && validPreview(service,frame,target,predicted);
    }

    private boolean validFrame(ScaffoldPlacementService service,AdaptiveBridgePlanner.Frame frame,int blockY) {
        return Thread.currentThread()==owningThread && service!=null && frame!=null
                && frame.scope!=null && frame.scope.usable() && frame.authorized
                && AdaptiveBridgePlanner.validMotion(frame) && blockY>=0 && blockY<=255;
    }

    private static double[] routeStart(AdaptiveBridgePlanner.Frame frame) {
        return frame.hasLane && !AdaptiveBridgePlanner.falling(frame)
                && Math.hypot(frame.feetX-frame.laneX,frame.feetZ-frame.laneZ)<=FOOTPRINT_HALF_WIDTH
                ?new double[]{frame.laneX,frame.laneZ}:new double[]{frame.feetX,frame.feetZ};
    }

    private static List<BlockPos> destinations(AdaptiveBridgePlanner.Frame frame,int blockY,double[] predicted) {
        List<BlockPos> cells=new ArrayList<>(MAX_RECOVERY_DESTINATIONS);
        double[] start=routeStart(frame);
        double endX=predicted[0],endZ=predicted[1];
        boolean falling=AdaptiveBridgePlanner.falling(frame);
        if(frame.hasLane && !falling && Math.hypot(frame.feetX-frame.laneX,frame.feetZ-frame.laneZ)<=FOOTPRINT_HALF_WIDTH) {
            double along=(predicted[0]-frame.laneX)*frame.laneDirectionX
                    +(predicted[1]-frame.laneZ)*frame.laneDirectionZ;
            endX=frame.laneX+along*frame.laneDirectionX;
            endZ=frame.laneZ+along*frame.laneDirectionZ;
        }
        add(cells,start[0],blockY,start[1]);
        if(falling)footprint(cells,frame.feetX,blockY,frame.feetZ);
        add(cells,(start[0]+endX)*.5,blockY,(start[1]+endZ)*.5);
        add(cells,endX,blockY,endZ);
        if(falling) {
            footprint(cells,predicted[0],blockY,predicted[1]);
            int lower=Math.max(blockY-1,net.minecraft.util.MathHelper.floor_double(
                    AdaptiveBridgePlanner.predictFeetY(frame)-1));
            if(frame.allowLowerRecovery && lower>=0 && lower<blockY) {
                add(cells,predicted[0],lower,predicted[1]);
                footprint(cells,predicted[0],lower,predicted[1]);
            }
        }
        return cells;
    }

    private static boolean overlapsFootprint(double x,double z,BlockPos cell) {
        return x+FOOTPRINT_HALF_WIDTH>cell.getX() && x-FOOTPRINT_HALF_WIDTH<cell.getX()+1
                && z+FOOTPRINT_HALF_WIDTH>cell.getZ() && z-FOOTPRINT_HALF_WIDTH<cell.getZ()+1;
    }

    private static void footprint(List<BlockPos> cells,double x,int y,double z) {
        // Only cells actually overlapped by the conservative player footprint.
        // A grounded route never calls this and therefore cannot grow a side row.
        for(int dx=-1;dx<=1;dx+=2)for(int dz=-1;dz<=1;dz+=2)
            add(cells,x+dx*FOOTPRINT_HALF_WIDTH,y,z+dz*FOOTPRINT_HALF_WIDTH);
    }

    private static void add(List<BlockPos> cells,double x,int y,double z) {
        if(cells.size()>=MAX_RECOVERY_DESTINATIONS || Math.abs(x)>30000000 || Math.abs(z)>30000000)return;
        BlockPos cell=new BlockPos(x,y,z);
        if(!cells.contains(cell))cells.add(cell);
    }

    private static boolean validPreview(ScaffoldPlacementService service,
            AdaptiveBridgePlanner.Frame frame,PlacementTarget target,double[] predicted) {
        if(target==null || target.world!=frame.scope.world || target.player!=frame.scope.player
                || !service.isValid(target))return false;
        Vec3 futureEyes=new Vec3(frame.eyeX+predicted[0]-frame.feetX,
                frame.eyeY+AdaptiveBridgePlanner.predictFeetY(frame)-frame.feetY,
                frame.eyeZ+predicted[1]-frame.feetZ);
        return futureEyes.squareDistanceTo(target.getHitVec())<=frame.reach*frame.reach;
    }

    /**
     * Called only from Scaffold PRE. The host service has already validated the
     * initial destination/support/slot/stack. Up to six valid original-destination
     * face proposals are considered. The method cannot mutate inventory or send.
     */
    public Prepared prepare(ScaffoldPlacementService service,PlacementTarget original,
                            AdaptiveBridgePlanner.Frame frame) {
        return prepare(service,original,frame,false);
    }

    /** Keep a still-valid acquired face instead of scoring fresh replacement identities each PRE. */
    public Prepared prepareKeeping(ScaffoldPlacementService service,PlacementTarget original,
                                   AdaptiveBridgePlanner.Frame frame) {
        return prepare(service,original,frame,true);
    }

    private Prepared prepare(ScaffoldPlacementService service,PlacementTarget original,
                             AdaptiveBridgePlanner.Frame frame,boolean keeping) {
        if(Thread.currentThread()!=owningThread || service==null || original==null || frame==null || frame.scope==null
                || !service.isValid(original))return null;
        if(original.world!=frame.scope.world || original.player!=frame.scope.player)return null;
        final BlockPos destination=original.getDestination();
        frame.approvedStack=original.stack;
        frame.selectedSlot=original.getSlot();
        frame.destinationX=destination.getX();frame.destinationY=destination.getY();
        frame.destinationZ=destination.getZ();
        List<PlacementTarget> valid=new ArrayList<>();
        List<AdaptiveBridgePlanner.SupportFace> options=new ArrayList<>();

        addIfValid(service,original,original,valid,options);
        boolean held=(keeping || isConnector(frame,original,corner(frame,destination.getY())))
                && !valid.isEmpty() && AdaptiveBridgePlanner.validMotion(frame)
                && validPreview(service,frame,original,AdaptiveBridgePlanner.predict(frame));
        // All alternatives must still be approved by the existing service
        // and use the SAME destination, slot and actual inventory stack.
        if(!held)for(EnumFacing face:EnumFacing.values()) {
            if(face==original.getFacing())continue;
            PlacementTarget alternate=service.planFace(destination.offset(face.getOpposite()),face,null);
            if(alternate!=null)addIfValid(service,original,alternate,valid,options);
        }
        AdaptiveBridgePlanner.Plan candidate=planner.prepare(frame,options);
        if(!candidate.hasCandidate)return null;
        for(int i=0;i<options.size();i++)if(candidate.intent==options.get(i)) {
            PlacementTarget selected=valid.get(i);
            // Revalidate after host/world callbacks; PRE may never publish stale data.
            if(!service.isValid(selected))return null;
            return new Prepared(selected,(float)candidate.yaw,(float)candidate.pitch,candidate.suggestSneak);
        }
        return null;
    }

    private static void addIfValid(ScaffoldPlacementService service,PlacementTarget first,
                                   PlacementTarget item,List<PlacementTarget> valid,
                                   List<AdaptiveBridgePlanner.SupportFace> options) {
        if(item==null || item.getSlot()!=first.getSlot()
                || item.getSelectedSlot()!=first.getSelectedSlot()
                || !first.getDestination().equals(item.getDestination())
                || item.world!=first.world || item.player!=first.player
                || item.stack!=first.stack || !service.isValid(item))return;
        BlockPos support=item.getBlockPos();
        Vec3 hit=item.getHitVec();
        if(support==null || hit==null || item.supportState==null)return;
        AdaptiveBridgePlanner.Face face=AdaptiveBridgePlanner.Face.valueOf(item.getFacing().name());
        options.add(new AdaptiveBridgePlanner.SupportFace(support.getX(),support.getY(),support.getZ(),
                face,hit.xCoord,hit.yCoord,hit.zCoord,item.getSlot(),item.world,item.player,
                item.stack,item.supportState,true,true,true));
        valid.add(item);
    }
}
