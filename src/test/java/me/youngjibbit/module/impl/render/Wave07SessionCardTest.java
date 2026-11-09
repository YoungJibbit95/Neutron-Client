package me.youngjibbit.module.impl.render;

import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Pure session values / real mode selection, no native Minecraft framebuffer. */
public class Wave07SessionCardTest {
    @Test public void modernOverlayPreservesAllFourHistoricalModesAndDefault(){
        SessionInfo session=new SessionInfo();
        ComboValue mode=session.getModeSetting("Mode");
        String[] expected={"Basic","Outline","Tenacity","Rue (Rise)","Modern","Creative"};
        assertArrayEquals(expected,mode.getOptions());
        assertEquals("Outline",mode.getValue());
        assertEquals(1,mode.getSelectedIndex());
        assertEquals("Outline",SessionInfo.modernFor(mode.getValue(),false));
        assertEquals("Neutron 2.0",SessionInfo.modernFor(mode.getValue(),true));
        assertEquals("Outline",mode.getValue()); // no config change by presentation
        for(String legacy:new String[]{"Basic","Tenacity","Rue (Rise)"})
            assertEquals(legacy,SessionInfo.modernFor(legacy,true));
        assertEquals(5000d,session.getNumberSetting("Gradient Speed").getValue(),0);
        for(int i=0;i<expected.length;i++){
            mode.setValue(expected[i]);assertEquals(i,mode.getSelectedIndex());
        }
        assertEquals("Outline",mode.getDefaultValue());
    }

    @Test public void localVerifiedSessionStatsAreNeverSynthesized() {
        AtomicLong nano=new AtomicLong(0);
        SessionStatistics stats=new SessionStatistics(nano::get);
        assertEquals(0,stats.snapshot().kills);
        Object connection=new Object();
        stats.observe(connection,true);
        nano.set(74_000_000_000L);
        stats.chat("Bob was slain by Tester","Tester",stats.generation());
        stats.chat("Tester was shot by Alice","Tester",stats.generation());
        stats.chat("Fake untrusted flag detected","Tester",stats.generation());
        SessionStatistics.Snapshot snap=stats.snapshot();
        assertEquals(1,snap.kills);assertEquals(1,snap.deaths);
        assertEquals("1m 14s",snap.time());
        assertEquals("01:14",snap.clock());
        assertTrue(snap.active);
        stats.observe(null,false);
        assertFalse(stats.snapshot().active);
        assertEquals(1,stats.snapshot().kills);
        assertEquals(1,stats.snapshot().deaths);
    }

    @Test public void explicitOldModeAndPositionsSurviveOptInThemeChange(){
        SessionInfo session=new SessionInfo();
        String prior=ThemeMode.selected();
        try{
            session.getModeSetting("Mode").setValue("Rue (Rise)");
            session.getNumberSetting("Position X").setValue(78d);
            session.getNumberSetting("Position Y").setValue(95d);
            ThemeMode.select(ThemeMode.APPROACH_TWO);
            assertEquals("Rue (Rise)",session.getModeSetting("Mode").getValue());
            assertEquals("Rue (Rise)",SessionInfo.modernFor("Rue (Rise)",true));
            assertEquals(78d,session.getNumberSetting("Position X").getValue(),0);
            assertEquals(95d,session.getNumberSetting("Position Y").getValue(),0);
        }finally{ThemeMode.select(prior);}
    }
}
