package me.youngjibbit.core.network;

import me.youngjibbit.module.impl.exploit.Tickbase;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/**
 * Real player-body Tickbase consumer and existing budget, not a new timing
 * scheduler. An opt-in stable frame sample must precede every withheld step.
 */
public class TickbaseStableFrameFlowTest {
    private static Tickbase enable(ExploitHookFixture fixture,AtomicLong time) throws Exception {
        BacktrackLagRangeControlTest.clock(fixture,time);
        Tickbase tick=new Tickbase(time::get,fixture.client);
        tick.getBooleanSetting("Only Aura").setValue(false);
        tick.getBooleanSetting("Debug").setValue(true);
        tick.getModeSetting("Charge Policy").setValue("Stable Frame");
        tick.getNumberSetting("Charge Ticks").setValue(2D);
        tick.getNumberSetting("Min Charge").setValue(1D);
        tick.getNumberSetting("Release Speed").setValue(2D);
        tick.setToggled(true);
        ExploitHookFixture.Enemy enemy=fixture.enemy(7,3);
        fixture.cursor(enemy);
        return tick;
    }
    private static int step(ExploitHookFixture f) {
        AtomicInteger calls=new AtomicInteger();
        Tickbase.runPlayerStep(f.player,()->{calls.incrementAndGet();return true;});
        return calls.get();
    }
    private static void frame(ExploitHookFixture f,int ticks,long duration) {
        Tickbase.beginFrame(f.client,false,ticks,duration);
    }

    @Test public void optInRejectsMissingCatchupZeroAndJitteredFramesWithoutWithholdingPlayer() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            Tickbase tick=enable(f,time);
            try {
                assertEquals(1,step(f));assertEquals(0,tick.getDiagnostics().credit);
                assertEquals("stable_frame_required",tick.getDiagnostics().reason);
                int[][] bad={{0,50},{2,50},{1,19},{1,76},{3,250}};
                for(int[] sample:bad) {
                    frame(f,sample[0],sample[1]);
                    assertEquals("frame "+sample[0]+"/"+sample[1],1,step(f));
                    assertEquals(0,tick.getDiagnostics().credit);
                    assertFalse(tick.getDiagnostics().releasing);
                }
                frame(f,1,50);assertEquals(0,step(f));
                assertEquals(1,tick.getDiagnostics().credit);
                frame(f,1,50);assertEquals(1,step(f));
                frame(f,1,50);assertEquals(0,step(f));
                assertEquals(2,tick.getDiagnostics().credit);
            } finally {tick.setToggled(false);}
        }
    }

    @Test public void validStableFramesUseOnlyEarnedCreditsAndOriginalAcceptedAttackRelease() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            Tickbase tick=enable(f,time);
            try {
                frame(f,1,50);assertEquals(0,step(f));
                frame(f,1,50);assertEquals(1,step(f));
                frame(f,1,50);assertEquals(0,step(f));
                assertEquals(2,tick.getDiagnostics().credit);
                assertTrue(f.controller.tryAttackEntity(f.player,f.world.entities.get(7),()->true));
                assertTrue("the original accepted C02 alone releases earned steps",
                        tick.getDiagnostics().releasing);
                frame(f,1,50);assertEquals(2,step(f));
                frame(f,1,50);assertEquals(2,step(f));
                assertEquals(0,tick.getDiagnostics().credit);
                assertFalse(tick.getDiagnostics().releasing);
                frame(f,1,90);assertEquals(1,step(f));
                assertEquals(0,tick.getDiagnostics().credit);
            } finally {tick.setToggled(false);}
        }
    }

    @Test public void localKnockbackAbortsCreditAndRequiresFreshStableEvidence() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong time=new AtomicLong();
            Tickbase tick=enable(f,time);
            try {
                frame(f,1,50);assertEquals(0,step(f));
                assertEquals(1,tick.getDiagnostics().credit);
                f.receive(new S12PacketEntityVelocity(1,.3,.3,0));
                assertEquals(0,tick.getDiagnostics().credit);
                assertEquals(1,step(f));
                frame(f,1,50);
                assertEquals("local knockback recovery blocks borrowed credit",
                        1,step(f));
                assertEquals(0,tick.getDiagnostics().credit);
            } finally {tick.setToggled(false);}
        }
    }

    @Test public void legacyPolicyAndPersistentComboDefaultRemainUnchanged() {
        Tickbase tick=new Tickbase();
        assertEquals("Legacy",tick.getModeSetting("Charge Policy").getValue());
        assertEquals("Attack",tick.getModeSetting("Release On").getValue());
        assertEquals(5,tick.getNumberSetting("Charge Ticks").getValue(),0);
        assertEquals(2,tick.getNumberSetting("Min Charge").getValue(),0);
        tick.getModeSetting("Charge Policy").setValue("Stable Frame");
        assertEquals("Stable Frame",tick.getModeSetting("Charge Policy").getValue());
        tick.getModeSetting("Charge Policy").setValue("Legacy");
        assertEquals("Legacy",tick.getModeSetting("Charge Policy").getValue());
    }
}
