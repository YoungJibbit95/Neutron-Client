package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class ClockReminderScheduleTest {
    private static long at(String value){return Instant.parse(value).toEpochMilli();}
    @Test public void framesAndPauseInOneMinuteEmitOnlyOnce() {
        ClockReminderSchedule schedule=new ClockReminderSchedule();ZoneId utc=ZoneId.of("UTC");
        long mark=at("2026-11-09T12:10:00Z");
        assertFalse(schedule.poll(mark-1,utc));assertTrue(schedule.poll(mark,utc));
        for(long offset:new long[]{1,16,100,1_000,30_000,59_999})assertFalse(schedule.poll(mark+offset,utc));
        assertFalse(schedule.poll(mark+60_000,utc));assertTrue(schedule.poll(mark+600_000,utc));
    }
    @Test public void sameClockTextOnFollowingDayIsANewMark() {
        ClockReminderSchedule schedule=new ClockReminderSchedule();ZoneId utc=ZoneId.of("UTC");
        assertTrue(schedule.poll(at("2026-11-09T12:10:00Z"),utc));
        assertTrue(schedule.poll(at("2026-11-10T12:10:00Z"),utc));
    }
    @Test public void localMinuteMarksWorkWithQuarterHourOffsets() {
        ClockReminderSchedule schedule=new ClockReminderSchedule();ZoneId offset=ZoneId.of("+05:45");
        assertFalse(schedule.poll(at("2026-11-09T06:20:00Z"),offset));
        assertTrue(schedule.poll(at("2026-11-09T06:25:00Z"),offset));
    }
    @Test public void backwardsClockCannotRepeatAnAlreadyEmittedMark() {
        ClockReminderSchedule schedule=new ClockReminderSchedule();ZoneId utc=ZoneId.of("UTC");
        long mark=at("2026-11-09T12:10:00Z");assertTrue(schedule.poll(mark,utc));
        assertFalse(schedule.poll(mark-600_000,utc));assertFalse(schedule.poll(mark,utc));
    }
}
