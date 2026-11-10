package me.youngjibbit.notifications;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.module.impl.client.WatermarkHUDModule;
import me.youngjibbit.utils.render.UiEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.multiplayer.WorldClient;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.Assert.*;

/** The real routing predicate and shared queue, without drawing or requiring OpenGL. */
public class IslandNotificationFallbackTest {
    @Test public void invisibleIslandKeepsAnExclusivePausedMessageUntilItsSurfaceReturns()throws Exception{
        Wave2IsolatedCase.run(Checks.class,"invisibleIslandKeepsAnExclusivePausedMessageUntilItsSurfaceReturns");
    }
    private static Field field(Class<?> owner,String name)throws Exception{
        Field f=owner.getDeclaredField(name);f.setAccessible(true);return f;
    }
    public static final class Checks {
        @Test @SuppressWarnings("unchecked")
        public void invisibleIslandKeepsAnExclusivePausedMessageUntilItsSurfaceReturns()throws Exception{
            try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()){
                f.mc.gameSettings=RotationBehaviorHookTest.bare(GameSettings.class);
                f.mc.gameSettings.guiScale=1;f.mc.displayWidth=854;f.mc.displayHeight=480;
                field(Minecraft.class,"mcThread").set(f.mc,Thread.currentThread());
                field(Minecraft.class,"mcLanguageManager").set(f.mc,new LanguageManager(null,"en_US"));
                Map<Class<?>,Module> modules=(Map<Class<?>,Module>)field(ModuleManager.class,"modulesByClass").get(Pulsar.INSTANCE.getMm());
                WatermarkHUDModule watermark=new WatermarkHUDModule();HUD hud=new HUD();ClientSettings settings=new ClientSettings();
                modules.put(WatermarkHUDModule.class,watermark);modules.put(HUD.class,hud);modules.put(ClientSettings.class,settings);
                watermark.getModeSetting("Mode").setValue("Modern");watermark.getBooleanSetting("Island Notifications").setValue(true);
                field(Module.class,"toggled").set(watermark,true);settings.notificationDuration.setValue(15D);UiEffects.invalidate();
                NotificationManager.releaseResources();
                try{
                    NotificationManager.show(new Notification(NotificationType.WARNING,"Saved message","Shared queue",0));
                    NotificationManager.IslandNotification first=NotificationManager.islandNotification();assertNotNull(first);
                    NotificationManager.Timeline timeline=(NotificationManager.Timeline)field(NotificationManager.class,"timeline").get(null);
                    WorldClient world=f.mc.theWorld;
                    f.mc.theWorld=null;paused(watermark,timeline,first);f.mc.theWorld=world;
                    f.mc.gameSettings.hideGUI=true;paused(watermark,timeline,first);f.mc.gameSettings.hideGUI=false;
                    f.mc.gameSettings.showDebugInfo=true;paused(watermark,timeline,first);f.mc.gameSettings.showDebugInfo=false;
                    f.mc.skipRenderWorld=true;paused(watermark,timeline,first);f.mc.skipRenderWorld=false;
                    f.mc.currentScreen=new GuiScreen(){};paused(watermark,timeline,first);
                    f.mc.currentScreen=new GuiChat();assertNotNull("chat leaves the Island visible",NotificationManager.islandNotification());
                    f.mc.currentScreen=null;hud.visible.setValue(false);paused(watermark,timeline,first);hud.visible.setValue(true);
                    watermark.getModeSetting("Mode").setValue("Simple");paused(watermark,timeline,first);
                    watermark.getModeSetting("Mode").setValue("Modern");
                    watermark.getBooleanSetting("Island Notifications").setValue(false);
                    assertFalse(watermark.routesNotificationsToIsland());assertNull(NotificationManager.islandNotification());
                    assertFalse("only an explicit option change permits ordinary cards",field(NotificationManager.class,"islandRoute").getBoolean(null));
                    assertEquals(first.id,timeline.visible.get(0).id);
                    watermark.getBooleanSetting("Island Notifications").setValue(true);
                    field(Module.class,"toggled").set(watermark,false);paused(watermark,timeline,first);
                    field(Module.class,"toggled").set(watermark,true);
                    NotificationManager.IslandNotification restored=NotificationManager.islandNotification();
                    assertNotNull(restored);assertEquals(first.id,restored.id);
                    assertTrue("hidden intervals are restored, never consumed",restored.expiresAtNanos>=first.expiresAtNanos);
                    settings.notifications.setValue(false);assertNull(NotificationManager.islandNotification());
                    assertTrue(timeline.visible.isEmpty());assertTrue(timeline.pending.isEmpty());
                }finally{NotificationManager.releaseResources();UiEffects.invalidate();}
            }
        }
        private static void paused(WatermarkHUDModule watermark,NotificationManager.Timeline timeline,
                                     NotificationManager.IslandNotification original)throws Exception{
            assertTrue(watermark.routesNotificationsToIsland());assertFalse(watermark.canPresentIslandNotifications());
            assertNull(NotificationManager.islandNotification());
            assertTrue("normal renderer stays suppressed",field(NotificationManager.class,"islandRoute").getBoolean(null));
            assertEquals("the single timeline retains the paused message",1,timeline.visible.size());
            assertEquals(original.id,timeline.visible.get(0).id);
        }
    }
}
