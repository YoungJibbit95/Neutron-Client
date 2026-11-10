package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.activity.InventoryActivitySnapshot;
import org.junit.Test;
import static org.junit.Assert.*;

public class IslandObservationsTest {
    @Test public void confirmedPlacementCounterIsIndependentOfStatusSequence(){
        IslandStateMachine.Scaffold a=IslandObservations.scaffold(new ScaffoldActivitySnapshot(50,100,1,0,24,
                "Adaptive Bridge","Adaptive",ScaffoldActivitySnapshot.Status.SEARCHING,7),100);
        IslandStateMachine.Scaffold b=IslandObservations.scaffold(new ScaffoldActivitySnapshot(51,200,1,0,24,
                "Adaptive Bridge","Adaptive",ScaffoldActivitySnapshot.Status.READY,7),200);
        assertEquals(7,a.placementCount);assertEquals(7,b.placementCount);assertEquals(200,b.observedAtNanos);
        IslandStateMachine.Scaffold c=IslandObservations.scaffold(new ScaffoldActivitySnapshot(52,300,1,0,23,
                "Adaptive Bridge","Adaptive",ScaffoldActivitySnapshot.Status.PLACING,8),300);
        assertEquals(8,c.placementCount);
    }
    private static InventoryActivitySnapshot inventory(InventoryActivitySnapshot.Producer producer,
            InventoryActivitySnapshot.Action action,int done,int total,long now){
        return new InventoryActivitySnapshot(producer,action,done,total,7,now);
    }
    private static IslandPresentation present(IslandStateMachine.Scaffold scaffold,IslandStateMachine.InventoryActivity inventory){
        return IslandPresentation.from(new IslandStateMachine().resolve(1,100,null,scaffold,null,inventory,
                false,true,false,true),"Neutron","Dev","12:10",true,true,true);
    }
    @Test public void scaffoldCopiesActualItemCountApproachProfileAndSafetyStatus() {
        ScaffoldActivitySnapshot snapshot=new ScaffoldActivitySnapshot(9,100,1,3,47,"Adaptive","Arsenic",ScaffoldActivitySnapshot.Status.EDGE_GUARD);
        IslandStateMachine.Scaffold view=IslandObservations.scaffold(snapshot,101);
        assertNotNull(view);assertTrue(view.observed);assertEquals(47,view.blockCount);
        assertEquals(1,view.itemId);assertEquals(3,view.metadata);assertEquals(9,view.sequence);
        IslandPresentation display=present(view,null);
        assertEquals("Scaffold  47 blocks",display.title);
        assertEquals("Adaptive / Arsenic  Edge guard",display.detail);assertFalse(display.fixtureOnly);
    }
    @Test public void unknownScaffoldCountDoesNotTurnIntoZeroOrAFakeItem() {
        IslandStateMachine.Scaffold view=IslandObservations.scaffold(new ScaffoldActivitySnapshot(1,100,-1,-1,-1,
                "Normal","Legacy",ScaffoldActivitySnapshot.Status.SEARCHING),100);
        assertNotNull(view);assertEquals(-1,view.itemId);assertEquals(-1,view.blockCount);
        assertEquals("Scaffold",present(view,null).title);
        assertTrue(present(view,null).detail.endsWith("Searching"));
        assertFalse(new IslandStateMachine.Scaffold(true,-1,"old fixture").valid());
    }
    @Test public void expiredAndFutureObservationsAreAbsent() {
        ScaffoldActivitySnapshot scaffold=new ScaffoldActivitySnapshot(1,100,1,0,8,"Normal","Legacy",ScaffoldActivitySnapshot.Status.SEARCHING);
        InventoryActivitySnapshot inventory=inventory(InventoryActivitySnapshot.Producer.STEALER,InventoryActivitySnapshot.Action.STEALING,-1,-1,100);
        assertNull(IslandObservations.scaffold(scaffold,99));
        assertNull(IslandObservations.scaffold(scaffold,100+ScaffoldActivitySnapshot.TTL_NANOS+1));
        assertNull(IslandObservations.inventory(inventory,null,99));
        assertNull(IslandObservations.inventory(inventory,null,100+InventoryActivitySnapshot.TTL_NANOS+1));
    }
    @Test public void unknownInventoryProgressHasNoInventedDenominatorOrPercentage() {
        IslandStateMachine.InventoryActivity view=IslandObservations.inventory(inventory(
                InventoryActivitySnapshot.Producer.INVENTORY_MANAGER,InventoryActivitySnapshot.Action.SCANNING,-1,-1,100),null,100);
        IslandPresentation display=present(null,view);
        assertEquals("Inventory Manager",display.title);assertEquals("Scanning",display.detail);
        assertFalse(display.fixtureOnly);assertFalse(display.detail.contains("%"));
        assertFalse(new IslandStateMachine.InventoryActivity("old fixture",-1,-1,false).valid());
        assertEquals(-1,view.processedCount);assertEquals(-1,view.totalCount);
    }
    @Test public void newestFreshProducerWinsAndCompletionKeepsUnknownCountsUnknown() {
        InventoryActivitySnapshot older=inventory(InventoryActivitySnapshot.Producer.INVENTORY_MANAGER,InventoryActivitySnapshot.Action.SORTING,1,3,100);
        InventoryActivitySnapshot newer=inventory(InventoryActivitySnapshot.Producer.STEALER,InventoryActivitySnapshot.Action.COMPLETE,-1,-1,101);
        IslandStateMachine.InventoryActivity view=IslandObservations.inventory(older,newer,102);
        assertEquals("Stealer",view.producer);assertTrue(view.complete);
        assertEquals("Complete",present(null,view).detail);
        assertEquals("Sorting  1 / 3",present(null,IslandObservations.inventory(older,null,102)).detail);
    }
    @Test public void repeatedSnapshotsDoNotEnqueueEventsAndVisibilityStillWins() {
        IslandStateMachine.InventoryActivity view=IslandObservations.inventory(inventory(
                InventoryActivitySnapshot.Producer.STEALER,InventoryActivitySnapshot.Action.STEALING,-1,-1,100),null,100);
        IslandStateMachine model=new IslandStateMachine();
        for(int i=0;i<100;i++)assertEquals(IslandStateMachine.State.INVENTORY_ACTIVITY,
                model.resolve(1,100+i,null,null,null,view,false,false,false,true).state);
        assertEquals(0,model.pendingEventCount());assertEquals(0,model.rememberedEventCount());
        assertEquals(IslandStateMachine.State.IDLE,model.resolve(1,201,null,null,null,view,false,false,false,false).state);
    }
}
