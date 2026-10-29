package me.youngjibbit.notifications;

import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.Theme;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Vis02NotificationTest {
    @Test public void realIconColorConsumerMapsExistingRolesAndErrorSurvivesOff() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            assertArrayEquals(new NotificationType[]{NotificationType.INFO,NotificationType.WARNING,NotificationType.ERROR,NotificationType.ENABLED,NotificationType.DISABLED,NotificationType.SUCCESS},NotificationType.values());
            assertEquals(0xffff667f,NotificationManager.iconColor(NotificationType.ERROR));
            assertEquals(0xffffc969,NotificationManager.iconColor(NotificationType.WARNING));
            assertEquals(0xff65e0ad,NotificationManager.iconColor(NotificationType.SUCCESS));
            assertEquals(NotificationManager.iconColor(NotificationType.SUCCESS),NotificationManager.iconColor(NotificationType.ENABLED));
            assertEquals(NotificationManager.iconColor(NotificationType.INFO),NotificationManager.iconColor(NotificationType.DISABLED));
            int info=NotificationManager.iconColor(NotificationType.INFO);f.settings.semanticColorPriority.setValue(false);
            assertEquals(0xffff667f,NotificationManager.iconColor(NotificationType.ERROR));assertNotEquals(info,NotificationManager.iconColor(NotificationType.INFO));
            f.settings.reducedMotion.setValue(true);assertEquals(0xffff667f,NotificationManager.iconColor(NotificationType.ERROR));
        }
    }
    @Test public void ambientOffDoesNotFreezeCoalescingExpiryExitOrPromotion() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.settings.ambientMotion.setValue(false);assertTrue(UiEffects.motion());
            NotificationManager.Timeline model=new NotificationManager.Timeline();
            NotificationManager.Message first=new NotificationManager.Message(new Notification(NotificationType.ERROR,"Error","Readable detail",1));
            NotificationManager.Message queued=new NotificationManager.Message(new Notification(NotificationType.INFO,"Info","Second",1));
            model.advance(Arrays.asList(first,queued),0,.1f,1,4,5,UiEffects.motion(),t->true);
            assertEquals(1,model.visible.size());assertEquals(1,model.pending.size());
            model.advance(Collections.singletonList(first),500000000L,.1f,1,4,5,UiEffects.motion(),t->true);
            assertEquals(2,model.visible.get(0).count);assertTrue(model.visible.get(0).alpha>0);
            for(int i=0;i<10;i++)model.advance(Collections.emptyList(),2_000_000_000L+i*100_000_000L,.1f,1,4,5,UiEffects.motion(),t->true);
            assertEquals(1,model.visible.size());assertEquals(NotificationType.INFO,model.visible.get(0).notification.type);assertTrue(model.pending.isEmpty());
            assertEquals("Second",model.visible.get(0).notification.message);
        }
    }
}
