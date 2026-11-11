package me.youngjibbit.core.diagnostics;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/** Bounded JVM GC notifications. Collector event duration is not always a stop-the-world pause. */
final class GcEventCapture implements NotificationListener {
    private static final int LIMIT = 2048;
    private final List<NotificationEmitter> emitters = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private boolean active;
    private long dropped, malformed;
    private long epoch, startUptimeMillis, endUptimeMillis;

    synchronized void start() {
        stop(); events.clear(); dropped = malformed = 0; active = true;
        ++epoch;
        startUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        endUptimeMillis = -1;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean instanceof NotificationEmitter) {
                NotificationEmitter emitter = (NotificationEmitter)bean;
                try { emitter.addNotificationListener(this, null, Long.valueOf(epoch)); emitters.add(emitter); }
                catch (RuntimeException unavailable) { malformed++; }
            }
        }
    }

    synchronized void stop() {
        if (active) endUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        active = false;
        for (NotificationEmitter emitter : emitters) {
            try { emitter.removeNotificationListener(this); }
            catch (Exception unavailable) { malformed++; }
        }
        emitters.clear();
    }

    @Override public synchronized void handleNotification(Notification notification, Object handback) {
        if (!active || !(handback instanceof Long) || ((Long)handback).longValue() != epoch
                || !GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
        if (events.size() == LIMIT) { dropped++; return; }
        try {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData)notification.getUserData());
            events.add(new Event(info.getGcName(), info.getGcAction(), info.getGcCause(),
                    info.getGcInfo().getStartTime(), info.getGcInfo().getEndTime(), info.getGcInfo().getDuration()));
        } catch (RuntimeException malformedNotification) { malformed++; }
    }

    synchronized Snapshot snapshot() { return new Snapshot(new ArrayList<>(events), dropped, malformed, startUptimeMillis, endUptimeMillis, epoch); }

    static final class Snapshot {
        final String meaning = "JVM collector event durations in milliseconds since JVM start; concurrent phases are not application stop-the-world pause times";
        final List<Event> events;
        final long dropped, malformed, startUptimeMillis, endUptimeMillis, epoch;
        Snapshot(List<Event> events, long dropped, long malformed, long start, long end, long epoch) {
            this.events = events; this.dropped = dropped; this.malformed = malformed;
            startUptimeMillis = start; endUptimeMillis = end; this.epoch = epoch;
        }
    }
    static final class Event {
        final String collector, action, cause;
        final long startUptimeMillis, endUptimeMillis, durationMillis;
        Event(String collector, String action, String cause, long start, long end, long duration) {
            this.collector = collector; this.action = action; this.cause = cause;
            startUptimeMillis = start; endUptimeMillis = end; durationMillis = duration;
        }
    }
}
