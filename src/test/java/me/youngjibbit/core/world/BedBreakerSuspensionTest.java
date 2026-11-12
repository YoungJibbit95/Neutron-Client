package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.contract.AutomationEligibility;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.player.BedBreaker;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.MathHelper;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Native owned mining must stop on suspension without turning a scheduler denial into a reset. */
public class BedBreakerSuspensionTest {
    @Test public void focusLossWithoutPlayerBodyAbortsOnceAndKeepsReturn() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"focusLossWithoutPlayerBodyAbortsOnceAndKeepsReturn");
    }
    @Test public void guiWithoutPlayerBodyAbortsOnceAndKeepsReturn() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"guiWithoutPlayerBodyAbortsOnceAndKeepsReturn");
    }
    @Test public void focusLossAfterOrdinaryBodyEntryStillReleasesOwnedMining() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"focusLossAfterOrdinaryBodyEntryStillReleasesOwnedMining");
    }
    @Test public void earnedExtraInValidViewLeavesProgressAndOrdinaryBudgetUntouched() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"earnedExtraInValidViewLeavesProgressAndOrdinaryBudgetUntouched");
    }

    public static final class Checks {
        @Test public void focusLossWithoutPlayerBodyAbortsOnceAndKeepsReturn() throws Exception { suspendWithoutBody(false); }
        @Test public void guiWithoutPlayerBodyAbortsOnceAndKeepsReturn() throws Exception { suspendWithoutBody(true); }

        private static void suspendWithoutBody(boolean gui) throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                startProgress(f);
                f.f.player.rotationYaw=80; f.f.player.rotationPitch=-10;
                float[] before=f.module.rotationEngine.rotation(); int calls=f.controller.automaticCalls;
                if(gui) f.f.mc.currentScreen=RotationBehaviorHookTest.bare(GuiIngameMenu.class);
                else f.f.mc.inGameHasFocus=false;
                tickWithoutBody(f);
                assertPaused(f,before,calls);
                tickWithoutBody(f); f.input(false);
                assertFalse(BedBreaker.handleBlockInput(f.f.mc,false));
                List<C07PacketPlayerDigging> packets=f.digs();
                assertEquals("repeated suspension/input cannot send duplicate aborts",1,packets.size());
                assertEquals(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK,packets.get(0).getStatus());
                f.frames(1);
                assertTrue("the retained rotation tail advances instead of clearing",f.module.rotationEngine.shouldRotate());
                double change=distance(before,f.module.rotationEngine.rotation());
                assertTrue(change>0); assertTrue(change<=12.0001);
                assertEquals(80,f.f.player.rotationYaw,0); assertEquals(-10,f.f.player.rotationPitch,0);
                assertTrue(f.digs().isEmpty());
            }
        }

        @Test public void focusLossAfterOrdinaryBodyEntryStillReleasesOwnedMining() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                startProgress(f);
                f.f.player.rotationYaw=80; f.f.player.rotationPitch=-10;
                float[] before=f.module.rotationEngine.rotation(); int calls=f.controller.automaticCalls;
                RuntimeObservation observation=f.f.runtime.getObservation();
                observation.beginOuterTick();
                try {
                    observation.observePlayerStep(f.f.player,() -> {
                        assertEquals(ExecutedStep.Kind.ORDINARY,observation.getCurrentStep().getKind());
                        f.f.mc.inGameHasFocus=false;
                        assertFalse(AutomatedActionAdmission.mayAttempt(AutomationEligibility.ModuleAction.ATTACK));
                        f.module.onDemiseUpdate();
                        return true;
                    },body -> body.getAsBoolean());
                } finally { observation.endOuterTick(); }
                assertPaused(f,before,calls);
                List<C07PacketPlayerDigging> packets=f.digs();
                assertEquals(1,packets.size());
                assertEquals(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK,packets.get(0).getStatus());
            }
        }

        @Test public void earnedExtraInValidViewLeavesProgressAndOrdinaryBudgetUntouched() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                startProgress(f); f.next();
                float progress=f.progress(); long revision=f.controller.getMiningRevision();
                int calls=f.controller.automaticCalls; float[] pose=f.module.rotationEngine.rotation();
                RuntimeObservation observation=f.f.runtime.getObservation();
                observation.beginOuterTick();
                try {
                    observation.observePlayerStep(f.f.player,() -> {
                        assertNotEquals(ExecutedStep.Kind.ORDINARY,observation.getCurrentStep().getKind());
                        assertFalse(AutomatedActionAdmission.mayAttempt(AutomationEligibility.ModuleAction.ATTACK));
                        f.module.onDemiseUpdate();
                        return true;
                    },body -> body.getAsBoolean(),true);
                } finally { observation.endOuterTick(); }
                assertEquals(calls,f.controller.automaticCalls); assertEquals(progress,f.progress(),0);
                assertEquals(revision,f.controller.getMiningRevision()); assertTrue(f.controller.isMiningBlock(f.pos));
                assertTrue(BedBreaker.handleBlockInput(f.f.mc,false));
                assertEquals(BedBreaker.Status.MINING,f.module.getStatus());
                assertArrayEquals(pose,f.module.rotationEngine.rotation(),0);
                assertNull("no extra dig or animation is sent",f.f.channel.readOutbound());
                f.update();
                assertEquals("a denied extra body cannot consume the ordinary attempt",calls+1,f.controller.automaticCalls);
                assertTrue(f.progress()>progress); assertTrue(f.controller.isMiningBlock(f.pos));
            }
        }

        private static void startProgress(BedBreakerTestSupport.Fixture f) throws Exception {
            f.bed.hardness=.1f; f.aim(); f.input(false); f.next(); f.input(false);
            assertTrue("fixture must own a real native START",f.controller.isMiningBlock(f.pos));
            assertTrue("fixture must have actual local mining progress",f.progress()>0);
            assertEquals(BedBreaker.Status.MINING,f.module.getStatus());
            f.digs();
        }
        private static void tickWithoutBody(BedBreakerTestSupport.Fixture f) {
            RuntimeObservation observation=f.f.runtime.getObservation(); observation.beginOuterTick();
            try { assertNull(observation.getCurrentStep()); Pulsar.BUS.post(new TickEvent()); }
            finally { observation.endOuterTick(); }
        }
        private static void assertPaused(BedBreakerTestSupport.Fixture f,float[] pose,int calls) throws Exception {
            assertFalse(f.controller.func_181040_m()); assertEquals(0,f.progress(),0);
            assertEquals(calls,f.controller.automaticCalls); assertEquals(0,f.world.removals);
            assertEquals(BedBreaker.Status.PAUSED_INPUT,f.module.getStatus());
            assertArrayEquals("same scene retains the current rotation",pose,f.module.rotationEngine.rotation(),0);
            assertTrue(f.module.rotationEngine.returning());
        }
        private static double distance(float[] a,float[] b) {
            return Math.hypot(MathHelper.wrapAngleTo180_float(a[0]-b[0]),a[1]-b[1]);
        }
    }
}
