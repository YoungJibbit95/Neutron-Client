package me.youngjibbit.module.impl.client.island;

import java.time.Instant;
import java.time.ZoneId;

/** Local ten-minute marks, identified by absolute minute rather than repeated HH:mm text. */
public final class ClockReminderSchedule {
    private long lastEmittedMinute=Long.MIN_VALUE;
    private boolean resuming;

    /** The first foreground frame establishes the current minute without replaying a missed mark. */
    public void suspend() { resuming = true; }

    public boolean poll(long wallMillis,ZoneId zone) {
        if(zone==null)return false;
        long minute=Math.floorDiv(wallMillis,60_000L);
        if (resuming) {
            resuming = false;
            lastEmittedMinute = Math.max(lastEmittedMinute, minute);
            return false;
        }
        if(minute<=lastEmittedMinute)return false;
        if(Instant.ofEpochMilli(wallMillis).atZone(zone).getMinute()%10!=0)return false;
        lastEmittedMinute=minute;
        return true;
    }
}
