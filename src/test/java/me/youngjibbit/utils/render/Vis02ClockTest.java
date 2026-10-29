package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import org.junit.Test;
import static org.junit.Assert.*;

public class Vis02ClockTest {
    @Test public void longPauseIsClampedAndNeverReplayed() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.tick(60);assertEquals(.1,UiEffects.time(),.000001);
            assertEquals(.025,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,.000001);
            UiEffects.beginFrame();assertEquals(.1,UiEffects.time(),.000001);
            f.tick(.016);assertEquals(.116,UiEffects.time(),.000001);
        }
    }
    @Test public void backwardReadingCannotBeCountedAgainOnRecovery() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.tick(.1);f.clock.set(50_000_000);UiEffects.beginFrame();
            f.clock.set(100_000_000);UiEffects.beginFrame();assertEquals(.1,UiEffects.time(),.000001);
        }
    }
    @Test public void duplicateReadingsAndPartitionedFramesHaveOneElapsedTime() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.advance(1);for(int i=0;i<100;i++)UiEffects.beginFrame();
            assertEquals(1,UiEffects.time(),.000001);
            assertEquals(.25,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).phase,.000001);
        }
    }
}
