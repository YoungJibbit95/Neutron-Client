package me.youngjibbit.core.diagnostics;

import com.sun.management.GarbageCollectionNotificationInfo;
import javax.management.Notification;
import org.junit.Test;
import static org.junit.Assert.*;

public class GcEventCaptureTest {
    @Test public void oldQueuedNotificationsCannotEnterANewCapture() {
        GcEventCapture capture = new GcEventCapture();
        try {
            capture.start(); long old = capture.snapshot().epoch;
            capture.stop(); capture.start(); long current = capture.snapshot().epoch;
            assertTrue(current > old);
            Notification malformed = new Notification(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION, this, 1);
            capture.handleNotification(malformed, Long.valueOf(old));
            assertEquals(0, capture.snapshot().malformed);
            capture.handleNotification(malformed, Long.valueOf(current));
            assertEquals(1, capture.snapshot().malformed);
            capture.stop();
            capture.handleNotification(malformed, Long.valueOf(current));
            assertEquals(1, capture.snapshot().malformed);
            assertTrue(capture.snapshot().endUptimeMillis >= capture.snapshot().startUptimeMillis);
        } finally { capture.stop(); }
    }
}
