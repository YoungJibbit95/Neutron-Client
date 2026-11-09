package me.youngjibbit.module.impl.client.island;

import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.activity.InventoryActivitySnapshot;

/** Read-only adapter: never selects a slot, advances a planner, clicks, places or sends a packet. */
public final class IslandObservations {
    private IslandObservations(){}
    public static IslandStateMachine.Scaffold scaffold(ScaffoldActivitySnapshot snapshot,long now){
        if(snapshot==null||!snapshot.isFresh(now)||snapshot.getStatus()==null)return null;
        String status;
        switch(snapshot.getStatus()){
            case SEARCHING:status="Searching";break;
            case ALIGNING:status="Aligning";break;
            case READY:status="Ready";break;
            case PLACING:status="Placing";break;
            case EDGE_GUARD:status="Edge guard";break;
            case RECOVERING:status="Recovering";break;
            default:return null;
        }
        IslandStateMachine.Scaffold view=IslandStateMachine.Scaffold.observed(snapshot.getBlockCount(),
                snapshot.getApproach(),snapshot.getProfile(),status,snapshot.getItemId(),snapshot.getMetadata(),snapshot.getSequence());
        return view.valid()?view:null;
    }
    public static IslandStateMachine.InventoryActivity inventory(InventoryActivitySnapshot first,
                                                                 InventoryActivitySnapshot second,long now){
        if(first!=null&&!first.isFresh(now))first=null;
        if(second!=null&&!second.isFresh(now))second=null;
        InventoryActivitySnapshot snapshot=second!=null&&(first==null||second.getObservedAtNanos()>first.getObservedAtNanos())?second:first;
        if(snapshot==null||snapshot.getProducer()==null||snapshot.getAction()==null)return null;
        String action;
        switch(snapshot.getAction()){
            case SCANNING:action="Scanning";break;
            case DROPPING:action="Dropping";break;
            case SORTING:action="Sorting";break;
            case EQUIPPING:action="Equipping";break;
            case STEALING:action="Taking";break;
            case COMPLETE:action="Complete";break;
            default:return null;
        }
        String producer;
        switch(snapshot.getProducer()){
            case STEALER:producer="Stealer";break;
            case INVENTORY_MANAGER:producer="Inventory Manager";break;
            default:return null;
        }
        IslandStateMachine.InventoryActivity view=IslandStateMachine.InventoryActivity.observed(producer,action,
                snapshot.getProcessed(),snapshot.getTotal(),snapshot.isComplete(),snapshot.getSequence());
        return view.valid()?view:null;
    }
}
