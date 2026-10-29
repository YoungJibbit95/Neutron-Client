package me.youngjibbit.notifications;

import me.youngjibbit.utils.render.Vis02TestContext;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Qa02NotificationTest {
    @Test public void allAppearancePairsPreserveSemanticResolverAndCriticalError()throws Exception{
        try(Vis02TestContext f=new Vis02TestContext()){
            Value<?> value=f.settings.getValueByName("notification_theme");ComboValue theme=(ComboValue)value;
            for(boolean priority:new boolean[]{true,false}){f.settings.semanticColorPriority.setValue(priority);Map<NotificationType,Integer> expected=new EnumMap<>(NotificationType.class);for(NotificationType type:NotificationType.values())expected.put(type,NotificationManager.iconColor(type));
                for(String selected:theme.getOptions())for(String design:f.settings.notificationDesign.getOptions()){theme.setValue(selected);f.settings.notificationDesign.setValue(design);for(NotificationType type:NotificationType.values())assertEquals((int)expected.get(type),NotificationManager.iconColor(type));assertEquals(0xffff667f,NotificationManager.iconColor(NotificationType.ERROR));}
            }
        }
    }
    @Test public void everyThemeLeavesCoalesceQueueExpiryAndPromotionIdentical()throws Exception{
        try(Vis02TestContext f=new Vis02TestContext()){
            for(String selected:f.settings.notificationTheme.getOptions()){
                f.settings.notificationTheme.setValue(selected);NotificationManager.Timeline t=new NotificationManager.Timeline();
                NotificationManager.Message a=new NotificationManager.Message(new Notification(NotificationType.WARNING,"Synthetic","Grüße ✓",1)),b=new NotificationManager.Message(new Notification(NotificationType.INFO,"Next","Unicode Ω",1));
                t.advance(Arrays.asList(a,b),0,0,1,4,1,false,type->true);assertEquals(1,t.visible.size());assertEquals(1,t.pending.size());
                t.advance(Collections.singletonList(a),500_000_000L,.1f,1,4,1,false,type->true);assertEquals(2,t.visible.get(0).count);assertEquals(1_500_000_000L,t.visible.get(0).expires);
                t.advance(Collections.emptyList(),2_000_000_000L,.1f,1,4,1,false,type->true);assertEquals("Next",t.visible.get(0).notification.title);assertEquals(3_000_000_000L,t.visible.get(0).expires);assertTrue(t.pending.isEmpty());
            }
        }
    }
}
