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
     * Three sampled destinations and six faces bound this to eighteen planFace
     * calls; subsequent PRE preparation adds at most five alternative faces.
     * No planner state, rotation request, inventory write or placement is made.
     */
    public PlacementTarget findEarlyTarget(ScaffoldPlacementService service,
            AdaptiveBridgePlanner.Frame frame,int blockY,PlacementTarget preferred) {
        if(Thread.currentThread()!=owningThread || service==null || frame==null
                || frame.scope==null || !frame.scope.usable() || !frame.authorized
                || !AdaptiveBridgePlanner.validMotion(frame) || blockY<0 || blockY>255)return null;
        double[] predicted=AdaptiveBridgePlanner.predict(frame);
        BlockPos[] destinations={
                new BlockPos(frame.feetX,blockY,frame.feetZ),
                new BlockPos((frame.feetX+predicted[0])*.5,blockY,(frame.feetZ+predicted[1])*.5),
                new BlockPos(predicted[0],blockY,predicted[1])};
        for(int index=0;index<destinations.length;index++) {
            BlockPos destination=destinations[index];
            boolean duplicate=false;
            for(int earlier=0;earlier<index;earlier++)
                if(destination.equals(destinations[earlier]))duplicate=true;
            if(duplicate)continue;
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
        return null;
    }

    private static boolean validPreview(ScaffoldPlacementService service,
            AdaptiveBridgePlanner.Frame frame,PlacementTarget target,double[] predicted) {
        if(target==null || target.world!=frame.scope.world || target.player!=frame.scope.player
                || !service.isValid(target))return false;
        Vec3 futureEyes=new Vec3(frame.eyeX+predicted[0]-frame.feetX,frame.eyeY,
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
        // All alternatives must still be approved by the existing service
        // and use the SAME destination, slot and actual inventory stack.
        for(EnumFacing face:EnumFacing.values()) {
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
