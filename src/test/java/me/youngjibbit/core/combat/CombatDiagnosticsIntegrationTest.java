package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.input.EventKey;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.packet.PacketReceiveEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.player.AttackResultEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.CombatDiagnostics;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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
    @Test public void incomingS08PreviewAndOwnS12VelocityNeverMutateThePlayer() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            CombatDiagnostics diagnostics = diagnostics();
            f.player.posX = 100; f.player.posY = 64; f.player.posZ = -20;
            f.player.rotationYaw = 30; f.player.rotationPitch = -10;
            f.player.motionX = .123; f.player.motionY = .456; f.player.motionZ = -.789;
            diagnostics.setToggled(true);
            try {
                S08PacketPlayerPosLook correction = new S08PacketPlayerPosLook(.5, 70, 2, 10, 5,
                        EnumSet.of(S08PacketPlayerPosLook.EnumFlags.X,
                                S08PacketPlayerPosLook.EnumFlags.Y_ROT));
                Pulsar.BUS.post(new PacketReceiveEvent(correction, null));
                String snapshot = trace(diagnostics).recent(1).get(0);
                assertTrue(snapshot, snapshot.startsWith("S08_RECEIVED_NOT_A_FLAG"));
                assertTrue(snapshot, snapshot.contains("s08Phase=RECEIPT_NOT_APPLIED"));
                assertTrue(snapshot, snapshot.contains("flags=[X, Y_ROT]"));
                assertTrue(snapshot, snapshot.contains("s08Projected=100.5000/70.0000/2.0000"));
                assertTrue(snapshot, snapshot.contains("recvOffset=0.5000/6.0000/22.0000"));
                assertEquals(100, f.player.posX, 0);
                assertEquals(64, f.player.posY, 0);
                assertEquals(-20, f.player.posZ, 0);

                S12PacketEntityVelocity other = new S12PacketEntityVelocity(f.player.getEntityId() + 1, 1, .4, -.2);
                Pulsar.BUS.post(new PacketReceiveEvent(other, null));
                assertEquals("S12 for a different entity must be ignored", 1, trace(diagnostics).size());

                S12PacketEntityVelocity incoming = new S12PacketEntityVelocity(f.player.getEntityId(), .5, .25, -.125);
                Pulsar.BUS.post(new PacketReceiveEvent(incoming, null));
                String velocity = trace(diagnostics).recent(1).get(0);
                assertTrue(velocity, velocity.startsWith("S12_SELF_VELOCITY_RECEIVED"));
                assertTrue(velocity, velocity.contains("incomingVelocity=0.50000/0.25000/-0.12500"));
                assertEquals(.123, f.player.motionX, 0);
                assertEquals(.456, f.player.motionY, 0);
                assertEquals(-.789, f.player.motionZ, 0);

                KnockbackEvent post = new KnockbackEvent(.5, .25, -.125);
                post.setEventPhase(EventPhase.POST);
                Pulsar.BUS.post(post);
                assertTrue(trace(diagnostics).recent(1).get(0).startsWith("KB_SELF_APPLIED_POST"));
                assertTrue(trace(diagnostics).recent(1).get(0).contains("appliedKnockback=0.50000/0.25000/-0.12500"));
                assertNull(f.channel.readOutbound());
            } finally { diagnostics.setToggled(false); }
        }
    }

    @SuppressWarnings("unchecked")
    @Test public void actualMoveFlyingSnapshotShowsRawAndFinalInputsAtTheNextAttack() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            CombatDiagnostics diagnostics = diagnostics();
            f.aura.getModeSetting("movefix").setValue("Legacy Rounded");
            f.player.rotationYaw = 180;
            f.accept("aura", 73);

            Field registry = ModuleManager.class.getDeclaredField("modulesByClass");
            registry.setAccessible(true);
            Map<Class<? extends Module>, Module> modules =
                    (Map<Class<? extends Module>, Module>) registry.get(Pulsar.INSTANCE.getMm());
            Module prior = modules.put(CombatDiagnostics.class, diagnostics);
            diagnostics.setToggled(true);
            try {
                f.player.moveFlying(.2f, .2f, .1f);
                Pulsar.BUS.post(new AttackEvent(f.player));
                String line = trace(diagnostics).recent(1).get(0);
                assertTrue(line, line.startsWith("ATTACK_PRE_ATTEMPT"));
                assertTrue(line, line.contains("auraConfiguredMoveFix=Legacy Rounded"));
                assertTrue(line, line.contains("moveRaw=0.200/0.200@180.00"));
                assertTrue(line, line.contains("moveApplied="));
                assertTrue(line, line.contains("@73.00"));
                assertTrue(line, line.contains("moveAgeTicks=0"));
                assertEquals(180, f.player.rotationYaw, 0);
                assertNull(f.channel.readOutbound());
            } finally {
                diagnostics.setToggled(false);
                if (prior == null) modules.remove(CombatDiagnostics.class);
                else modules.put(CombatDiagnostics.class, prior);
            }
        }
    }

    @Test public void noPriorMoveSnapshotIsExplicitAndWorldChangeClearsIt() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            CombatDiagnostics diagnostics = diagnostics();
            diagnostics.setToggled(true);
            try {
                Pulsar.BUS.post(new AttackEvent(f.player));
                assertTrue(trace(diagnostics).recent(1).get(0).contains("moveSample=none"));
                Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                Pulsar.BUS.post(new EventKey(Keyboard.KEY_F8));
                assertTrue(trace(diagnostics).recent(1).get(0).contains("moveSample=none"));
                assertNull(f.channel.readOutbound());
            } finally { diagnostics.setToggled(false); }
        }
    }

    @SuppressWarnings("unchecked")
    @Test public void appliedS08ObservationMeasuresActualDisplacementNotReceiptPrediction() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            CombatDiagnostics diagnostics = diagnostics();
            // The actual NetHandler observer resolves the opt-in module through the client registry.
            Field registry = ModuleManager.class.getDeclaredField("modulesByClass");
            registry.setAccessible(true);
            Map<Class<? extends Module>, Module> modules =
                    (Map<Class<? extends Module>, Module>) registry.get(Pulsar.INSTANCE.getMm());
            Module prior = modules.put(CombatDiagnostics.class, diagnostics);
            diagnostics.setToggled(true);
            try {
                f.player.posX = 100.5;
                f.player.posY = 65;
                f.player.posZ = -18;
                f.player.rotationYaw = 45;
                f.player.rotationPitch = 5;
                CombatDiagnostics.observeAppliedS08(f.player, 100, 64, -20, 30, 0);
                String line = trace(diagnostics).recent(1).get(0);
                assertTrue(line, line.startsWith("S08_APPLIED_NOT_A_FLAG"));
                assertTrue(line, line.contains("s08Phase=APPLIED"));
                assertTrue(line, line.contains("appliedPre=100.0000/64.0000/-20.0000"));
                assertTrue(line, line.contains("appliedPost=100.5000/65.0000/-18.0000"));
                assertTrue(line, line.contains("appliedOffset=0.5000/1.0000/2.0000"));
                assertTrue(line, line.contains("appliedRotationDelta=15.00/5.00"));
                assertEquals(100.5, f.player.posX, 0);
                assertNull(f.channel.readOutbound());
            } finally {
                diagnostics.setToggled(false);
                if (prior == null) modules.remove(CombatDiagnostics.class);
                else modules.put(CombatDiagnostics.class, prior);
            }
        }
    }

}
