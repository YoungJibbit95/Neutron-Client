package me.youngjibbit.notifications;

import me.youngjibbit.core.activity.ExploitActivitySnapshot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class DiagnosticNoticeTest {
    private final AtomicLong now = new AtomicLong();

    @Before public void clear() { NotificationManager.drain(); }
    @After public void cleanup() { NotificationManager.drain(); }

    private ExploitActivitySnapshot holding(int packets, long age) {
        return ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,
                now.get(), 1, packets, age);
    }

    private void advance(NotificationManager.Timeline timeline, int visible) {
        timeline.advance(NotificationManager.drain(), now.get(), .05f, visible, 8, 5, false, type -> true);
    }

    @Test public void liveFactsRefreshOneCardWithoutCountingSamplesOrExtendingItsLifetime() {
        DiagnosticNotice notice = new DiagnosticNotice("module.test", "Backtrack", now::get);
        NotificationManager.Timeline timeline = new NotificationManager.Timeline();
        notice.observe(true, true, "Timed", "delaying_target_motion", holding(1, 10));
        advance(timeline, 1);
        NotificationManager.Entry entry = timeline.visible.get(0);
        long started = entry.started, expires = entry.expires, id = entry.id;
        for (int sample = 1; sample <= 50; sample++) {
            now.set(sample * 50_000_000L);
            notice.observe(true, true, "Timed", "delaying_target_motion", holding(2, sample * 10));
            advance(timeline, 1);
            assertSame(entry, timeline.visible.get(0));
            assertEquals(1, entry.count);
            assertEquals(started, entry.started);
            assertEquals(expires, entry.expires);
        }
        assertEquals("Holding 500 ms \u00b7 2 packets \u00b7 Timed", entry.notification.message);
        timeline.route(true, now.get());
        assertEquals(id, timeline.islandSnapshot().id);
        assertEquals(1, timeline.islandSnapshot().count);
        now.set(4_000_000_000L);
        notice.observe(true, true, "Timed", "delaying_target_motion", holding(1, 20));
        advance(timeline, 1);
        assertTrue(timeline.visible.isEmpty());
        assertTrue(timeline.pending.isEmpty());
    }

    @Test public void changesInsideCooldownRefreshImmediatelyAndAreNotAnnouncedAgainLater() {
        List<Notification> shown = new ArrayList<>(), refreshed = new ArrayList<>();
        DiagnosticNotice notice = new DiagnosticNotice("module.test", "Backtrack", now::get,
                shown::add, refreshed::add);
        notice.observe(true, true, "Distance", "no_target", null);
        now.set(500_000_000L);
        notice.observe(true, true, "Distance", "delaying_target_motion", holding(2, 40));
        assertEquals(1, shown.size());
        assertEquals("Holding 40 ms \u00b7 2 packets \u00b7 Distance", refreshed.get(0).message);
        now.set(2_500_000_000L);
        notice.observe(true, true, "Distance", "delaying_target_motion", holding(2, 50));
        assertEquals(1, shown.size());
        notice.observe(true, true, "Distance", "no_target", null);
        assertEquals(2, shown.size());
        assertEquals("Waiting for target \u00b7 Distance", shown.get(1).message);
    }

    @Test public void unchangedIdleStateDoesNotKeepAllocatingInboxUpdates() {
        List<Notification> shown = new ArrayList<>(), refreshed = new ArrayList<>();
        DiagnosticNotice notice = new DiagnosticNotice("module.test", "Backtrack", now::get,
                shown::add, refreshed::add);
        notice.observe(true, true, "Distance", "no_target", null);
        for (int sample = 0; sample < 1000; sample++) {
            now.addAndGet(50_000_000L);
            notice.observe(true, true, "Distance", "no_target", null);
        }
        assertEquals(1, shown.size());
        assertTrue(refreshed.isEmpty());
    }

    @Test public void disabledAndWorldTransitionsReplaceHoldingWithoutCreatingNewCards() {
        DiagnosticNotice notice = new DiagnosticNotice("module.test", "Backtrack", now::get);
        NotificationManager.Timeline timeline = new NotificationManager.Timeline();
        notice.observe(true, true, "Timed", "delaying_target_motion", holding(2, 40));
        advance(timeline, 1);
        NotificationManager.Entry entry = timeline.visible.get(0);
        long expires = entry.expires;
        now.set(200_000_000L);
        notice.observe(false, true, "Timed", "disabled", null);
        advance(timeline, 1);
        assertSame(entry, timeline.visible.get(0));
        assertEquals("Off", entry.notification.message);
        assertEquals(1, entry.count);
        assertEquals(expires, entry.expires);
        notice.reset();
        notice.observe(true, true, "Timed", "delaying_target_motion", holding(1, 10));
        advance(timeline, 1);
        expires = entry.expires;
        now.set(300_000_000L);
        notice.finish("Idle");
        advance(timeline, 1);
        assertEquals("Idle", entry.notification.message);
        assertEquals(expires, entry.expires);
        now.set(5_000_000_000L);
        advance(timeline, 1);
        notice.finish("Off");
        advance(timeline, 1);
        assertTrue(timeline.visible.isEmpty());
        assertTrue(timeline.pending.isEmpty());
    }

    @Test public void turningDiagnosticsOffRetiresTheCurrentTextWithoutANewNotice() {
        DiagnosticNotice notice = new DiagnosticNotice("module.test", "Backtrack", now::get);
        notice.observe(true, true, "Timed", "delaying_target_motion", holding(1, 20));
        notice.observe(true, false, "Timed", "settings_changed", null);
        List<NotificationManager.Message> messages = NotificationManager.drain();
        assertEquals(1, messages.size());
        assertEquals("Diagnostics off", messages.get(0).notification.message);
        assertFalse(messages.get(0).updateOnly);
        notice.observe(true, false, "Timed", "settings_changed", null);
        assertTrue(NotificationManager.drain().isEmpty());
    }

    @Test public void staleAndFutureActivityCannotBeDisplayedAsCurrentHolding() {
        for (long observed : new long[]{0, 1_000_000_001L}) {
            now.set(1_000_000_000L);
            List<Notification> shown = new ArrayList<>();
            DiagnosticNotice notice = new DiagnosticNotice("module.test", "Backtrack", now::get,
                    shown::add, update -> fail("First state must publish"));
            notice.observe(true, true, "Timed", "delaying_target_motion",
                    ExploitActivitySnapshot.holding(ExploitActivitySnapshot.Producer.BACKTRACK,
                            observed, 1, 2, 100));
            assertEquals("Waiting for movement \u00b7 Timed", shown.get(0).message);
        }
    }

    @Test public void pendingStateKeepsCurrentFactsAndCountsWhileEventCountsRemainIntact() {
        DiagnosticNotice notice = new DiagnosticNotice("module.test", "Backtrack", now::get);
        NotificationManager.Timeline timeline = new NotificationManager.Timeline();
        NotificationManager.show(new Notification(NotificationType.INFO, "Other", "message", 1));
        notice.observe(true, true, "Timed", "delaying_target_motion", holding(1, 20));
        advance(timeline, 1);
        NotificationManager.Entry pending = timeline.pending.getFirst();
        now.set(500_000_000L);
        notice.observe(true, true, "Timed", "delaying_target_motion", holding(2, 50));
        advance(timeline, 1);
        assertEquals(1, pending.count);
        assertEquals("Holding 50 ms \u00b7 2 packets \u00b7 Timed", pending.notification.message);
        Notification event = Notification.latest("correction.test", NotificationType.WARNING,
                "Server correction", "Cause unknown", 3);
        NotificationManager.show(event);
        NotificationManager.updateIfPresent(event);
        now.set(2_000_000_000L);
        advance(timeline, 3);
        assertSame(pending, timeline.visible.get(0));
        assertEquals(1, pending.count);
        assertEquals(2, timeline.visible.get(1).count);
    }
}
