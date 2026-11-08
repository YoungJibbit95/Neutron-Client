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
     * Called only from Scaffold PRE. The host service has already validated the
     * initial destination/support/slot/stack. Up to six valid original-destination
     * face proposals are considered. The method cannot mutate inventory or send.
     */
    public Prepared prepare(ScaffoldPlacementService service,PlacementTarget original,
                            AdaptiveBridgePlanner.Frame frame) {
        if(service==null || original==null || frame==null || frame.scope==null
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
