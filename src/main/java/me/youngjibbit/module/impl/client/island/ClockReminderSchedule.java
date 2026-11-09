package me.youngjibbit.module.impl.client.island;

import java.time.Instant;
import java.time.ZoneId;

/** Local ten-minute marks, identified by absolute minute rather than repeated HH:mm text. */
public final class ClockReminderSchedule {
    private long lastEmittedMinute=Long.MIN_VALUE;
    public boolean poll(long wallMillis,ZoneId zone) {
        if(zone==null)return false;
        long minute=Math.floorDiv(wallMillis,60_000L);
        if(minute<=lastEmittedMinute)return false;
        if(Instant.ofEpochMilli(wallMillis).atZone(zone).getMinute()%10!=0)return false;
        lastEmittedMinute=minute;
        return true;
    }
}
