package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandClockFaceTest {
    @Test public void handsRepresentActualHourAndMinuteIncludingHalfHours() {
        IslandClockFace midnight=IslandClockFace.from("00:00");
        assertEquals(0,midnight.hourX,.00001);assertEquals(-1,midnight.hourY,.00001);
        assertEquals(0,midnight.minuteX,.00001);assertEquals(-1,midnight.minuteY,.00001);
        IslandClockFace three=IslandClockFace.from("15:00");
        assertEquals(1,three.hourX,.00001);assertEquals(0,three.hourY,.00001);
        IslandClockFace half=IslandClockFace.from("12:30");
        assertEquals(Math.sin(Math.PI/12),half.hourX,.00001);
        assertEquals(-Math.cos(Math.PI/12),half.hourY,.00001);
        assertEquals(0,half.minuteX,.00001);assertEquals(1,half.minuteY,.00001);
    }
    @Test public void invalidLabelsNeverBecomeAnInventedClock() {
        for(String input:new String[]{null,"","noon","24:00","12:60","-1:10","1:10","12.10","12:10 extra"})
            assertNull(input,IslandClockFace.from(input));
    }
    @Test public void actualPresentationUsesCapturedReminderNotLaterClockText() {
        IslandStateMachine state=new IslandStateMachine();
        assertTrue(state.offer(IslandStateMachine.State.CLOCK_REMINDER,"mark:1","12:10",1,0));
        IslandPresentation view=IslandPresentation.from(
                state.resolve(1,1,null,null,null,null,false,false,true,false),
                "Neutron","Dev","19:59",true,true,true);
        assertNotNull(view.clockFace);assertEquals("12:10",view.clockFace.time);
        assertEquals(12,view.clockFace.hour);assertEquals(10,view.clockFace.minute);
        assertFalse(view.fixtureOnly);assertEquals("",view.brand);
    }
    @Test public void idleBrandAndVersionRemainIndependentlySwitchable() {
        IslandPresentation branded=IslandPresentation.from(null,"Neutron","Dev","12:10",true,true,true);
        assertEquals("Neutron",branded.brand);assertEquals("Dev",branded.brandVersion);
        assertEquals("Neutron Dev",branded.title);assertNull(branded.clockFace);
        IslandPresentation hidden=IslandPresentation.from(null,"Neutron","Dev","12:10",false,true,false);
        assertEquals("",hidden.brand);assertEquals("Dev",hidden.title);assertEquals("",hidden.trailing);
    }
    @Test public void liveHandsIncludeFractionalSecondsAndTheActualHourShare(){
        long wall=java.time.Instant.parse("2026-11-09T03:15:30.500Z").toEpochMilli();
        IslandClockFace face=IslandClockFace.capture(wall,java.time.ZoneOffset.UTC,true);
        assertEquals(30.5,face.second,0);assertEquals("03:15",face.time);
        double actualMinute=15+30.5/60,actualHour=3+actualMinute/60;
        assertEquals(Math.cos(2*Math.PI*actualMinute/60-Math.PI/2),face.minuteX,.00001);
        assertEquals(Math.sin(2*Math.PI*actualHour/12-Math.PI/2),face.hourY,.00001);
        assertEquals(Math.cos(2*Math.PI*30.5/60-Math.PI/2),face.secondX,.00001);
        IslandClockFace later=IslandClockFace.capture(wall+250,java.time.ZoneOffset.UTC,true);
        assertNotEquals(face.secondX,later.secondX,.00001);
        assertEquals(30.5,face.second,0); // an already-deferred frame never reads a later wall clock
    }
    @Test public void actualZoneAndCurrentHandsDoNotRewriteReminderIdentity(){
        long wall=java.time.Instant.parse("2026-11-09T12:00:04.250Z").toEpochMilli();
        IslandClockFace face=IslandClockFace.capture(wall,java.time.ZoneOffset.ofHoursMinutes(5,45),true);
        assertEquals("17:45",face.time);assertEquals(4.25,face.second,0);
        IslandStateMachine state=new IslandStateMachine();
        state.offer(IslandStateMachine.State.CLOCK_REMINDER,"mark","17:45",1,0);
        IslandPresentation frame=IslandPresentation.from(state.resolve(1,1,null,null,null,null,false,false,true,false),
                "Neutron","Dev","unused",true,true,true).withClockFace(face);
        assertSame(face,frame.clockFace);assertEquals("17:45",frame.title);
        assertEquals(1,state.pendingEventCount());
    }
    @Test public void reducedMotionUsesRealWholeSecondsWithoutFractionalSweep(){
        long wall=java.time.Instant.parse("2026-11-09T12:10:30.100Z").toEpochMilli();
        IslandClockFace first=IslandClockFace.capture(wall,java.time.ZoneOffset.UTC,false);
        IslandClockFace sameSecond=IslandClockFace.capture(wall+700,java.time.ZoneOffset.UTC,false);
        assertEquals(30,first.second,0);assertEquals(first.hourX,sameSecond.hourX,0);
        assertEquals(first.minuteX,sameSecond.minuteX,0);assertEquals(first.secondX,sameSecond.secondX,0);
        IslandClockFace next=IslandClockFace.capture(wall+1000,java.time.ZoneOffset.UTC,false);
        assertEquals(31,next.second,0);assertNotEquals(first.secondX,next.secondX,.00001);
    }
}
