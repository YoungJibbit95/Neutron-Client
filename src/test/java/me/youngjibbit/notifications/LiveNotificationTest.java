package me.youngjibbit.notifications;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LiveNotificationTest {
    @Before public void clear(){NotificationManager.drain();}
    @After public void cleanup(){NotificationManager.drain();}
    private static Notification notice(String message){
        return Notification.latest("diagnostics.correction.test",NotificationType.WARNING,"Server correction",message,4);
    }
    private static void advance(NotificationManager.Timeline timeline,long now){
        timeline.advance(NotificationManager.drain(),now,.05f,1,8,5,false,type->true);
    }

    @Test public void inboxKeepsTheLatestFactsAndTheOriginalCreationRequest(){
        NotificationManager.show(notice("first"));
        NotificationManager.updateIfPresent(notice("latest"));
        List<NotificationManager.Message> inbox=NotificationManager.drain();
        assertEquals(1,inbox.size());assertEquals("latest",inbox.get(0).notification.message);
        assertEquals(2,inbox.get(0).count);assertFalse(inbox.get(0).updateOnly);
    }

    @Test public void unmatchedCooldownUpdateCannotEvictAQueuedNotification(){
        for(int i=0;i<64;i++)NotificationManager.show(new Notification(NotificationType.INFO,"Queued","message "+i,4));
        NotificationManager.updateIfPresent(notice("no matching card"));
        List<NotificationManager.Message> inbox=NotificationManager.drain();
        assertEquals(64,inbox.size());assertEquals("message 0",inbox.get(0).notification.message);
        assertEquals("message 63",inbox.get(63).notification.message);
    }

    @Test public void cooldownUpdateChangesFactsWithoutCreatingOrExtendingACard(){
        NotificationManager.Timeline timeline=new NotificationManager.Timeline();
        NotificationManager.updateIfPresent(notice("absent"));advance(timeline,0);
        assertTrue(timeline.visible.isEmpty());assertTrue(timeline.pending.isEmpty());
        NotificationManager.show(notice("first"));advance(timeline,1_000_000_000L);
        NotificationManager.Entry first=timeline.visible.get(0);
        long started=first.started,expires=first.expires,id=first.id;
        NotificationManager.updateIfPresent(notice("latest"));advance(timeline,2_000_000_000L);
        assertSame(first,timeline.visible.get(0));assertEquals("latest",first.notification.message);
        assertEquals(2,first.count);assertEquals(started,first.started);assertEquals(expires,first.expires);
        timeline.route(true,2_000_000_000L);
        assertEquals(id,timeline.islandSnapshot().id);assertEquals("latest",timeline.islandSnapshot().message);
        NotificationManager.updateIfPresent(notice("expired"));advance(timeline,6_000_000_000L);
        assertTrue(timeline.visible.isEmpty());assertTrue(timeline.pending.isEmpty());
    }

    @Test public void pendingCardsAlsoUseLatestFactsAndSaturatedCountDoesNotFreezeContent(){
        NotificationManager.Timeline timeline=new NotificationManager.Timeline();
        NotificationManager.show(new Notification(NotificationType.INFO,"Other","body",1));
        NotificationManager.show(notice("first"));advance(timeline,0);
        NotificationManager.Entry pending=timeline.pending.getFirst();pending.count=999;
        NotificationManager.updateIfPresent(notice("latest"));advance(timeline,500_000_000L);
        assertEquals("latest",pending.notification.message);assertEquals(999,pending.count);
        advance(timeline,2_000_000_000L);
        assertSame(pending,timeline.visible.get(0));assertEquals(6_000_000_000L,pending.expires);
    }

    @Test public void differentChannelsStaySeparateAndRepeatedPostsHaveBoundedLifetime(){
        NotificationManager.Timeline timeline=new NotificationManager.Timeline();
        NotificationManager.show(notice("first"));advance(timeline,0);
        for(int second=1;second<29;second++){
            NotificationManager.show(notice("sample "+second));advance(timeline,second*1_000_000_000L);
            assertEquals(1,timeline.visible.size());assertTrue(timeline.visible.get(0).expires<=30_000_000_000L);
        }
        NotificationManager.show(Notification.latest("another-run",NotificationType.WARNING,"Server correction","new context",4));
        advance(timeline,29_000_000_000L);assertEquals(1,timeline.pending.size());
        advance(timeline,31_000_000_000L);assertEquals("new context",timeline.visible.get(0).notification.message);
    }

    @Test public void iconRevealFinishesAndReducedMotionIsImmediate(){
        assertEquals(0,NotificationManager.iconReveal(10,20,true),0);
        float middle=NotificationManager.iconReveal(150_000_000L,0,true);
        assertTrue(middle>0&&middle<1);
        assertEquals(1,NotificationManager.iconReveal(1_000_000_000L,0,true),0);
        assertEquals(1,NotificationManager.iconReveal(0,0,false),0);
    }
}
