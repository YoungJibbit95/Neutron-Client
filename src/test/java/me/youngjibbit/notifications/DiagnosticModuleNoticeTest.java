package me.youngjibbit.notifications;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** Exercises the actual presentation callbacks with no world, connection or packet work. */
public class DiagnosticModuleNoticeTest {
    @Before public void clear() { NotificationManager.drain(); }
    @After public void cleanup() { NotificationManager.drain(); }

    @Test public void backtrackUpdatesItsVisibleStatusDuringCooldownAndOnDisable() throws Exception {
        AtomicLong now = new AtomicLong();
        check(new Backtrack(now::get, (net.minecraft.client.Minecraft) null), now,
                "no_target", "distance_filter", "Backtrack");
    }

    @Test public void lagRangeUpdatesItsVisibleStatusDuringCooldownAndOnDisable() throws Exception {
        AtomicLong now = new AtomicLong();
        check(new LagRange(now::get, null), now,
                "no eligible target", "waiting for approach outside reach", "LagRange");
    }

    private static void check(Module module, AtomicLong now, String initial, String next, String title) throws Exception {
        module.getBooleanSetting("Diagnostics").setValue(true);
        Field toggled = Module.class.getDeclaredField("toggled");
        toggled.setAccessible(true);
        Field reason = module.getClass().getDeclaredField("reason");
        reason.setAccessible(true);
        Method report = module.getClass().getDeclaredMethod("reportStatus");
        report.setAccessible(true);
        NotificationManager.Timeline timeline = new NotificationManager.Timeline();
        toggled.setBoolean(module, true);
        try {
            reason.set(module, initial);
            report.invoke(module);
            advance(timeline, now.get());
            assertEquals(1, timeline.visible.size());
            NotificationManager.Entry entry = timeline.visible.get(0);
            assertEquals(title, entry.notification.title);
            assertTrue(entry.notification.message.startsWith("Waiting for target"));
            long started = entry.started, expires = entry.expires;
            now.set(500_000_000L);
            reason.set(module, next);
            report.invoke(module);
            advance(timeline, now.get());
            assertSame(entry, timeline.visible.get(0));
            assertTrue(entry.notification.message.startsWith("Waiting for distance"));
            assertEquals(1, entry.count);
            assertEquals(started, entry.started);
            assertEquals(expires, entry.expires);
            toggled.setBoolean(module, false);
            module.onDisable();
            advance(timeline, now.get());
            assertSame(entry, timeline.visible.get(0));
            assertEquals("Off", entry.notification.message);
            assertEquals(expires, entry.expires);
        } finally {
            toggled.setBoolean(module, false);
            module.onDisable();
        }
    }

    private static void advance(NotificationManager.Timeline timeline, long now) {
        timeline.advance(NotificationManager.drain(), now, .05f, 1, 8, 5, false, type -> true);
    }
}
