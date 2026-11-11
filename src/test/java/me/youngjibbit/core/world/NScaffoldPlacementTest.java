package me.youngjibbit.core.world;

import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class NScaffoldPlacementTest {
    private static void accept(ScaffoldPlacementTest.Fixture f,String source,boolean accepted) {
        ScaffoldRotationPlan pose=ScaffoldRotationPlan.build("Back",f.world.wireEyes(),f.plan.getHitVec());
        f.rotations.beginMotion(0,0);f.rotations.request(source,15,pose.getYaw(),pose.getPitch());
        f.rotations.finishMotion(pose.getYaw(),pose.getPitch(),accepted);
    }
    @Test public void alignmentRequiresItsOwnAcceptedOwnerAndPreservesLegacyDefault() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.align();
        assertNotNull(f.service.alignAcceptedHit(f.plan,f.rotations));
        assertNull(f.service.alignAcceptedHit(f.plan,f.rotations,"nscaffold"));
        accept(f,"nscaffold",false);assertNull(f.service.alignAcceptedHit(f.plan,f.rotations,"nscaffold"));
        accept(f,"nscaffold",true);assertNotNull(f.service.alignAcceptedHit(f.plan,f.rotations,"nscaffold"));
        assertNull(f.service.alignAcceptedHit(f.plan,f.rotations));
        assertNull(f.service.alignAcceptedHit(f.plan,f.rotations,null));
    }
    @Test public void normalSlotSelectionRemainsVisibleAndTheSharedTickBudgetStillApplies() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.service.beginTick();accept(f,"nscaffold",true);
        assertTrue(f.service.placeWithAuthority(f.plan,f.rotations,false,f.rotations.getMotionSequence(),0,
                false,true,false,"nscaffold",()->true,()->true));
        assertEquals(3,f.world.selected);assertEquals(3,f.world.serverSlot);assertEquals(1,f.world.swings);
        f.world.cells.remove(new BlockPos(0,63,0));
        f.plan=f.service.planFace(f.plan.getBlockPos(),f.plan.getFacing(),f.plan.getHitVec());
        assertNotNull(f.plan);accept(f,"nscaffold",true);
        assertFalse(f.service.placeWithAuthority(f.plan,f.rotations,false,f.rotations.getMotionSequence(),0,
                false,true,false,"nscaffold",()->true,()->true));
        assertEquals(ScaffoldPlacementService.Rejection.BUDGET,f.service.getLastRejection());
        assertEquals(1,f.world.clicks);
    }
    @Test public void foreignOwnerAfterSlotSynchronizationAbortsWithoutInventingSuccess() {
        ScaffoldPlacementTest.Fixture f=new ScaffoldPlacementTest.Fixture();f.service.beginTick();accept(f,"nscaffold",true);
        f.world.onSync=()->f.rotations.reset("nscaffold");
        assertFalse(f.service.placeWithAuthority(f.plan,f.rotations,false,f.rotations.getMotionSequence(),0,
                false,true,false,"nscaffold",()->true,()->true));
        assertEquals(0,f.world.clicks);assertEquals(0,f.world.selected);assertEquals(0,f.world.swings);
    }
}
