package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.player.JumpFixEvent;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.util.MovementInput;
import org.junit.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/** Reentrant Auto Jump callbacks end the old module lifetime before plan/timer continuation. */
public class ScaffoldLifecycleWaveTest {
    private static final class JumpHook implements Subscriber {
        Runnable callback;
        int calls;
        @Subscribe final Listener<JumpFixEvent> jumps = new Listener<>(event -> {
            calls++;
            callback.run();
        });
    }

    private void interruptedJump(Consumer<ScaffoldOwnerContinuityTest.Run> change) throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.f.scaffold.getBooleanSetting("Auto Jump").setValue(true);
            run.f.scaffold.getBooleanSetting("Sprint").setValue(true);
            run.f.scaffold.getNumberSetting("Timer").setValue(2D);
            run.f.player.sprint = true;
            run.f.player.movementInput = new MovementInput();
            run.f.player.movementInput.moveForward = 1;
            JumpHook hook = new JumpHook();
            hook.callback = () -> change.accept(run);
            Pulsar.BUS.subscribe(hook);
            try {
                List<Object> packets = run.walk();
                assertEquals("real Auto Jump callback reached", 1, hook.calls);
                assertEquals("old PRE continuation cannot reapply the timer", 1, run.f.mc.timer.timerSpeed, 0);
                assertNull("old PRE continuation cannot recreate a current plan",
                        ScaffoldOwnerContinuityTest.field(Scaffold.class, "currentTarget").get(run.f.scaffold));
                assertNull("old PRE continuation cannot recreate a frame plan",
                        ScaffoldOwnerContinuityTest.field(Scaffold.class, "frameTarget").get(run.f.scaffold));
                assertNull(run.rotation.getConfirmedOwner());
                ScaffoldOwnerContinuityTest.noActions(packets);
            } finally { Pulsar.BUS.unsubscribe(hook); }
        }
    }

    @Test public void disableInsideAutoJumpStopsTheOldPreBeforePlanAndTimerWrites() throws Exception {
        interruptedJump(run -> run.f.scaffold.setToggled(false));
    }

    @Test public void disableAndReenableInsideAutoJumpCannotResumeTheOldGeneration() throws Exception {
        interruptedJump(run -> {
            run.f.scaffold.setToggled(false);
            run.f.scaffold.setToggled(true);
        });
    }

    @Test public void epochChangeInsideAutoJumpStopsTheOldPreBeforePlanAndTimerWrites() throws Exception {
        interruptedJump(run -> run.f.network.getClientPackets().worldChanged());
    }
}
