package me.youngjibbit.notifications;

import me.youngjibbit.module.impl.client.island.IslandPresentation;
import me.youngjibbit.module.impl.client.island.IslandStateMachine;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class IslandNotificationRoutingTest {
    private static final long SECOND=1_000_000_000L;
    private static NotificationManager.Message message(String label){
        return new NotificationManager.Message(new Notification(NotificationType.INFO,label,"Detail "+label,0));
    }
    private static void advance(NotificationManager.Timeline t,long now,int count){
        t.advance(Collections.emptyList(),now,.05f,count,8,5,true,type->true);
    }
    @Test public void switchingToIslandParksOtherCardsAndFallbackPreservesIdentityAndRemainingTime(){
        NotificationManager.Timeline t=new NotificationManager.Timeline();
        t.advance(Arrays.asList(message("A"),message("B"),message("C")),0,0,3,8,5,false,type->true);
        long first=t.visible.get(0).id,second=t.visible.get(1).id,third=t.visible.get(2).id;
        t.route(true,SECOND);advance(t,SECOND,1);
        assertEquals(first,t.islandSnapshot().id);assertEquals(1,t.visible.size());assertEquals(2,t.pending.size());
        advance(t,5*SECOND,1);
        assertEquals(second,t.islandSnapshot().id);
        assertEquals("only its four remaining seconds are resumed",9*SECOND,t.visible.get(0).expires);
        t.route(false,6*SECOND);advance(t,6*SECOND,3);
        assertNull("only one presentation owns the timeline",t.islandSnapshot());
        assertEquals(2,t.visible.size());assertEquals(second,t.visible.get(0).id);assertEquals(third,t.visible.get(1).id);
        assertEquals(10*SECOND,t.visible.get(1).expires);assertTrue(t.pending.isEmpty());
    }
    @Test public void islandPromotesWithoutEmptyMorphFrameAndCoalescesTheSameEntry(){
        NotificationManager.Timeline t=new NotificationManager.Timeline();t.route(true,0);
        t.advance(Arrays.asList(message("A"),message("B")),0,0,1,8,1,true,type->true);
        long first=t.islandSnapshot().id;
        t.advance(Collections.singletonList(message("A")),SECOND/2,.05f,1,8,1,true,type->true);
        assertEquals(first,t.islandSnapshot().id);assertEquals(2,t.islandSnapshot().count);
        t.advance(Collections.emptyList(),2*SECOND,.05f,1,8,1,true,type->true);
        assertEquals("B",t.islandSnapshot().title);assertNotEquals(first,t.islandSnapshot().id);
        t.advance(Collections.emptyList(),2*SECOND+1,.05f,1,8,1,true,type->false);
        assertNull(t.islandSnapshot());assertTrue(t.pending.isEmpty());
    }
    @Test public void hiddenIslandFreezesLiveLifetimeAndDoesNotStartPendingMessages(){
        NotificationManager.Timeline t=new NotificationManager.Timeline();t.route(true,0);
        t.advance(Arrays.asList(message("A"),message("B")),0,0,1,8,5,false,type->true);
        long first=t.visible.get(0).id;
        t.pause(true,SECOND);
        t.advance(Collections.singletonList(message("C")),40*SECOND,.05f,1,8,5,true,type->true);
        assertNull(t.islandSnapshot());assertEquals(1,t.visible.size());assertEquals(2,t.pending.size());
        assertEquals("hidden messages retain four seconds",5*SECOND,t.visible.get(0).expires);
        t.pause(false,41*SECOND);advance(t,41*SECOND,1);
        assertEquals(first,t.islandSnapshot().id);assertEquals(45*SECOND,t.islandSnapshot().expiresAtNanos);
        advance(t,45*SECOND,1);assertEquals("B",t.islandSnapshot().title);
        assertEquals(50*SECOND,t.islandSnapshot().expiresAtNanos);
    }
    @Test public void hiddenIslandStillBoundsCoalescesAndFiltersItsQueue(){
        NotificationManager.Timeline t=new NotificationManager.Timeline();t.route(true,0);t.pause(true,0);
        t.advance(Arrays.asList(message("A"),message("B"),message("B"),message("C")),20*SECOND,0,1,2,5,false,type->true);
        assertTrue(t.visible.isEmpty());assertEquals(2,t.pending.size());assertEquals(2,t.pending.peekFirst().count);
        t.advance(Collections.emptyList(),25*SECOND,0,1,2,5,false,type->false);
        assertTrue(t.pending.isEmpty());assertNull(t.islandSnapshot());
        t.advance(Collections.singletonList(message("D")),30*SECOND,0,1,2,5,false,type->true);
        t.route(false,40*SECOND);t.pause(false,40*SECOND);advance(t,40*SECOND,3);
        assertNull(t.islandSnapshot());assertEquals("D",t.visible.get(0).notification.title);
        assertEquals(45*SECOND,t.visible.get(0).expires);
    }
    @Test public void producerExpiryIsNotRestartedByIslandEpochOrRepeatedSelection(){
        NotificationManager.IslandNotification n=new NotificationManager.IslandNotification(9,
                new Notification(NotificationType.WARNING,"Warning","Something happened",0),1,0,5*SECOND,SECOND);
        IslandStateMachine model=new IslandStateMachine();
        IslandStateMachine.Scaffold bridge=new IslandStateMachine.Scaffold(true,24,"Adaptive Bridge");
        model.offer(IslandStateMachine.State.MODULE_TOAST,"toggle","Toggle",1,SECOND);
        for(int epoch=1;epoch<4;epoch++){
            IslandStateMachine.Selection s=model.resolve(epoch,SECOND,null,bridge,null,null,false,true,false,false,n);
            assertEquals(IslandStateMachine.State.NOTIFICATION,s.state);assertSame(n,s.notification);
        }
        assertEquals(IslandStateMachine.State.SCAFFOLD,model.resolve(3,5*SECOND,null,bridge,null,null,
                false,true,false,false,n).state);
    }
    @Test public void islandPresentationSanitizesGlobalMessagesAndKeepsUsefulDetail(){
        String detail="\u00a7cA long global notification with enough information to exceed the old sixty-four character limit.\nA second line.";
        NotificationManager.IslandNotification n=new NotificationManager.IslandNotification(7,
                new Notification(NotificationType.ERROR,"\u00a7cTitle",detail,0),3,0,SECOND,0);
        IslandStateMachine.Selection s=new IslandStateMachine().resolve(1,0,null,null,null,null,false,false,false,false,n);
        IslandPresentation p=IslandPresentation.from(s,"Neutron","Dev","12:00",true,true,true);
        assertEquals("Title ×3",p.title);assertTrue(p.detail.length()>64);assertFalse(p.detail.contains("\u00a7"));
        assertFalse(p.detail.contains("\n"));assertSame(n,p.notification);
    }
}
