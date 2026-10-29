package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.input.EventKey;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.player.AttackResultEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.CombatDiagnostics;
import me.zero.alpine.event.EventPhase;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import org.junit.Test;
import org.lwjgl.input.Keyboard;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CombatDiagnosticsIntegrationTest {
    private CombatDiagnostics diagnostics() {
        CombatDiagnostics diagnostics = new CombatDiagnostics();
        diagnostics.getBooleanSetting("Console Attack Log").setValue(false);
        return diagnostics;
    }

    private CombatDiagnosticTrace trace(CombatDiagnostics diagnostics) throws Exception {
        Field field = CombatDiagnostics.class.getDeclaredField("history");
        field.setAccessible(true);
        return (CombatDiagnosticTrace) field.get(diagnostics);
    }

    @Test public void discoveryClientCategoryAndF9ToggleUseTheRealRegistry() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            CombatDiagnostics diagnostics = diagnostics();
            assertTrue(new Reflections("me.youngjibbit.module.impl").getSubTypesOf(Module.class)
                    .contains(CombatDiagnostics.class));
            ModuleManager modules = new ModuleManager(Collections.singletonList(diagnostics));
            assertSame(diagnostics, modules.getModule("CombatDiagnostics"));
            assertTrue(Arrays.asList(modules.getModules(Category.Client)).contains(diagnostics));
            modules.toggleByKey(Keyboard.KEY_F9);
            try {
                assertTrue(diagnostics.isToggled());
                for (int i = 0; i < 130; i++) Pulsar.BUS.post(new EventKey(Keyboard.KEY_F8));
                assertEquals(120, trace(diagnostics).size());
                Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                assertEquals(0, trace(diagnostics).size());
                Pulsar.BUS.post(new EventKey(Keyboard.KEY_F8));
            } finally { modules.toggleByKey(Keyboard.KEY_F9); }
            assertFalse(diagnostics.isToggled());
            assertEquals(0, trace(diagnostics).size());
            Pulsar.BUS.post(new EventKey(Keyboard.KEY_F8));
            assertEquals(0, trace(diagnostics).size());
            assertNull(f.channel.readOutbound());
        }
    }

    @Test public void unobservedWireAnglesMustNotBeInventedFromTheCamera() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            CombatDiagnostics diagnostics = diagnostics();
            f.player.rotationYaw = 37;
            diagnostics.setToggled(true);
            try {
                Pulsar.BUS.post(new EventKey(Keyboard.KEY_F8));
                String line = trace(diagnostics).recent(1).get(0);
                assertTrue(line, line.contains("wireKnown=false"));
                assertTrue(line, line.contains("localWire=NaN/NaN"));
                assertEquals(37, f.player.rotationYaw, 0);
                assertFalse(f.runtime.getRotationManager().hasEffectiveRotation());
                for (float invalidPitch : new float[]{Float.NaN, 91}) {
                    f.runtime.getRotationManager().observeOutboundRotation(5, invalidPitch);
                    Pulsar.BUS.post(new EventKey(Keyboard.KEY_F8));
                    assertTrue(trace(diagnostics).recent(1).get(0).contains("wireKnown=false"));
                }
                assertNull(f.channel.readOutbound());
            } finally { diagnostics.setToggled(false); }
        }
    }

    @Test public void movementObservationPrecedesNestedAttacksAndRetainsTheRealCamera() throws Exception {
        for (boolean diagnosticFirst : new boolean[]{false, true}) {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            f.player.rotationYaw = 180;
            f.accept("aura", 73);
            CombatDiagnostics diagnostics = diagnostics();
            Subscriber combat = new Subscriber() {
                @Subscribe final Listener<EventMotion> post = new Listener<>(event -> {
                    if (!event.isPost()) return;
                    // Reproduce Aura's actual POST -> temporary local pose -> controller events.
                    f.player.rotationYaw = 73;
                    try {
                        Pulsar.BUS.post(new AttackEvent(f.player));
                        Pulsar.BUS.post(new AttackResultEvent(f.player));
                    } finally { f.player.rotationYaw = 180; }
                });
            };
            // Listener registration order must not invert the apparent movement/attack timeline.
            if (diagnosticFirst) diagnostics.setToggled(true);
            Pulsar.BUS.subscribe(combat);
            if (!diagnosticFirst) diagnostics.setToggled(true);
            try {
                EventMotion motion = new EventMotion(0, 0, 0, 73, 0, true);
                motion.setEventPhase(EventPhase.POST);
                motion.setAccepted(true);
                Pulsar.BUS.post(motion);
                List<String> lines = trace(diagnostics).recent(3);
                assertTrue(lines.toString(), lines.get(2).startsWith("MOVE_LOCAL_POST"));
                assertTrue(lines.toString(), lines.get(1).startsWith("ATTACK_PRE_ATTEMPT"));
                assertTrue(lines.toString(), lines.get(0).startsWith("ATTACK_LOCAL_ACCEPTED"));
                assertTrue(lines.get(1), lines.get(1).contains("camera=180.00/0.00"));
                assertTrue(lines.get(1), lines.get(1).contains("playerPose=73.00/0.00"));
                f.player.rotationYaw = 37;
                Pulsar.BUS.post(new AttackEvent(f.player));
                assertTrue(trace(diagnostics).recent(1).get(0).contains("camera=37.00/0.00"));
                assertTrue(f.runtime.getRotationManager().isMotionAccepted());
                assertEquals("aura", f.runtime.getRotationManager().getConfirmedOwner());
                assertEquals(73, f.runtime.getRotationManager().getEffectiveYaw(), 0);
                assertNull(f.channel.readOutbound());
            } finally {
                diagnostics.setToggled(false);
                Pulsar.BUS.unsubscribe(combat);
            }
        }
        }
    }
}
