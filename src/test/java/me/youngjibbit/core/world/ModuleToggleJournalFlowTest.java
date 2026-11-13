package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.core.activity.ModuleToggleJournal;
import me.youngjibbit.core.activity.ModuleToggleSnapshot;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Real Module.setToggled, BUS and ConfigManager paths in the existing isolated client fixture. */
@RunWith(Parameterized.class)
public class ModuleToggleJournalFlowTest {
    @Parameterized.Parameters(name = "{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"completedLifecyclePreservesOrderAndPublishesAfterPersistence"},
                {"selfDisablingAndReentrantCallbacksPublishOnlyNetSuccess"},
                {"failedCallbacksNeverPublishAndKeepOriginalFailureState"},
                {"configLoadsAndDelayedActivationAreSilent"},
                {"configDuringCallbackInvalidatesOuterEvidenceAndFailureReleasesSuppression"},
                {"actualSceneBoundariesInvalidateAndRejectCrossWorldPublication"},
                {"latestOnlyReadPathDoesNotScanModulesOrPerformActions"},
                {"offThreadAndExpiredReadsCannotReplayEvidence"}
        });
    }
    private final String method;
    public ModuleToggleJournalFlowTest(String method) { this.method = method; }
    @Test public void checkInFreshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class, method); }

    public static final class ProbeEvent { }

    @ModuleInfo(id = "toast-probe", name = "Toast Probe", category = Category.Player)
    public static class ProbeModule extends Module {
        final List<String> calls = new ArrayList<>();
        Runnable toggleHook, enableHook, disableHook;
        int busEvents;
        @Subscribe private final Listener<ProbeEvent> probe = new Listener<>(event -> busEvents++);
        @Override public void onToggle() {
            calls.add("toggle:" + isToggled());
            if (toggleHook != null) toggleHook.run();
        }
        @Override public void onEnable() {
            calls.add("enable:" + isToggled());
            super.onEnable();
            if (enableHook != null) enableHook.run();
        }
        @Override public void onDisable() {
            calls.add("disable:" + isToggled());
            super.onDisable();
            if (disableHook != null) disableHook.run();
        }
    }

    @ModuleInfo(id = "toast-second", name = "Second Probe", category = Category.Player)
    public static class SecondModule extends ProbeModule { }

    public static class Checks {
        @Rule public TemporaryFolder temporary = new TemporaryFolder();

        private static RotationBehaviorHookTest.Fixture fixture() throws Exception {
            RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture();
            ScaffoldClientFlowTest.set(Minecraft.class, f.mc, "mcThread", Thread.currentThread());
            return f;
        }
        private static void clearObservation() {
            ModuleToggleJournal.beginConfigApplication();
            ModuleToggleJournal.endConfigApplication();
        }
        private static void cleanup(ProbeModule module) {
            module.toggleHook = module.enableHook = module.disableHook = null;
            module.setPersistenceListener(null);
            module.setToggled(false);
            Pulsar.BUS.unsubscribe(module);
            clearObservation();
        }
        private static void persistLog(ProbeModule module) {
            module.setPersistenceListener(changed -> module.calls.add("persist:" + changed + ":" + module.isToggled()));
        }
        private ConfigManager config(Path directory, ProbeModule module, AtomicBoolean ready) {
            ConfigManager manager = new ConfigManager(directory, Collections.singletonList(module), ready::get, ignored -> { });
            manager.initialize(Collections.emptyList());
            return manager;
        }
        private static void enabledConfig(Path directory, String id) throws Exception {
            Files.write(directory.resolve("On.json"), ("{\"version\":2,\"modules\":{\"" + id
                    + "\":{\"enabled\":true,\"settings\":{}}}}").getBytes(StandardCharsets.UTF_8));
        }

        @Test public void completedLifecyclePreservesOrderAndPublishesAfterPersistence() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                ProbeModule module = new ProbeModule();
                try {
                    long before = ModuleToggleJournal.currentSequence();
                    module.toggleHook = module.enableHook = () -> {
                        assertEquals(before, ModuleToggleJournal.currentSequence());
                        assertNull(ModuleToggleJournal.getSnapshot());
                    };
                    module.setPersistenceListener(changed -> {
                        module.calls.add("persist:" + changed + ":" + module.isToggled());
                        assertEquals(before, ModuleToggleJournal.currentSequence());
                        assertNull(ModuleToggleJournal.getSnapshot());
                    });
                    long start = System.nanoTime();
                    module.setToggled(true);
                    long end = System.nanoTime();
                    assertEquals(Arrays.asList("toggle:false", "enable:true", "persist:true:true"), module.calls);
                    ModuleToggleSnapshot enabled = ModuleToggleJournal.getSnapshot();
                    assertNotNull(enabled); assertTrue(enabled.isEnabled());
                    assertEquals("toast_probe", enabled.getModuleId()); assertEquals("Toast Probe", enabled.getModuleName());
                    assertEquals(before + 1, enabled.getSequence());
                    assertTrue(enabled.getObservedAtNanos() - start >= 0 && end - enabled.getObservedAtNanos() >= 0);
                    Pulsar.BUS.post(new ProbeEvent()); assertEquals(1, module.busEvents);
                    module.setToggled(true);
                    assertEquals(3, module.calls.size()); assertSame(enabled, ModuleToggleJournal.getSnapshot());

                    module.toggleHook = module.enableHook = null; persistLog(module);
                    module.setToggled(false);
                    assertEquals(Arrays.asList("toggle:false", "enable:true", "persist:true:true",
                            "toggle:true", "disable:false", "persist:true:false"), module.calls);
                    assertFalse(ModuleToggleJournal.getSnapshot().isEnabled());
                    assertEquals(before + 2, ModuleToggleJournal.currentSequence());
                    Pulsar.BUS.post(new ProbeEvent()); assertEquals(1, module.busEvents);
                    assertNull(f.channel.readOutbound());
                } finally { cleanup(module); }
            }
        }

        @Test public void selfDisablingAndReentrantCallbacksPublishOnlyNetSuccess() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                ProbeModule module = new ProbeModule();
                try {
                    persistLog(module);
                    long before = ModuleToggleJournal.currentSequence();
                    module.enableHook = () -> module.setToggled(false);
                    module.setToggled(true);
                    assertFalse(module.isToggled()); assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(before, ModuleToggleJournal.currentSequence());
                    assertEquals(Arrays.asList("toggle:false", "enable:true", "toggle:true", "disable:false",
                            "persist:true:false", "persist:true:false"), module.calls);
                    Pulsar.BUS.post(new ProbeEvent()); assertEquals(0, module.busEvents);

                    module.calls.clear(); AtomicBoolean once = new AtomicBoolean();
                    module.enableHook = () -> {
                        if (once.compareAndSet(false, true)) { module.setToggled(false); module.setToggled(true); }
                        assertNull(ModuleToggleJournal.getSnapshot());
                    };
                    module.setToggled(true);
                    assertTrue(module.isToggled()); assertTrue(ModuleToggleJournal.getSnapshot().isEnabled());
                    assertEquals(before + 1, ModuleToggleJournal.currentSequence());
                    assertEquals(Arrays.asList("toggle:false", "enable:true", "toggle:true", "disable:false", "persist:true:false",
                            "toggle:false", "enable:true", "persist:true:true", "persist:true:true"), module.calls);
                    module.enableHook = null;
                    module.disableHook = () -> module.setToggled(true);
                    module.setToggled(false);
                    assertTrue(module.isToggled()); assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(before + 1, ModuleToggleJournal.currentSequence());

                    module.disableHook = null; module.setToggled(false); clearObservation();
                    long persistenceBefore = ModuleToggleJournal.currentSequence();
                    module.setPersistenceListener(changed -> { if (module.isToggled()) module.setToggled(false); });
                    module.setToggled(true);
                    assertFalse(module.isToggled()); assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(persistenceBefore, ModuleToggleJournal.currentSequence());

                    persistLog(module); module.calls.clear(); AtomicBoolean toggleOnce = new AtomicBoolean();
                    module.toggleHook = () -> { if (toggleOnce.compareAndSet(false, true)) module.setToggled(true); };
                    module.setToggled(true);
                    assertTrue(module.isToggled()); assertTrue(ModuleToggleJournal.getSnapshot().isEnabled());
                    assertEquals(persistenceBefore + 1, ModuleToggleJournal.currentSequence());
                    assertEquals(Arrays.asList("toggle:false", "toggle:false", "enable:true", "persist:true:true",
                            "enable:true", "persist:true:true"), module.calls);
                } finally { cleanup(module); }
            }
        }

        @Test public void failedCallbacksNeverPublishAndKeepOriginalFailureState() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                for (String stage : Arrays.asList("toggle", "enable", "disable", "persistence", "caught-nested", "error")) {
                    ProbeModule module = new ProbeModule();
                    try {
                        if ("disable".equals(stage)) module.setToggled(true);
                        clearObservation(); long before = ModuleToggleJournal.currentSequence();
                        RuntimeException failure = new IllegalStateException("original " + stage);
                        if ("toggle".equals(stage)) module.toggleHook = () -> { throw failure; };
                        if ("enable".equals(stage)) module.enableHook = () -> { throw failure; };
                        if ("disable".equals(stage)) module.disableHook = () -> { throw failure; };
                        if ("persistence".equals(stage)) module.setPersistenceListener(changed -> { throw failure; });
                        if ("caught-nested".equals(stage)) {
                            module.disableHook = () -> { throw failure; };
                            module.enableHook = () -> {
                                module.enableHook = null;
                                assertSame(failure, assertThrows(RuntimeException.class, () -> module.setToggled(false)));
                                module.setToggled(true);
                            };
                            module.setToggled(true);
                        } else if ("error".equals(stage)) {
                            AssertionError error = new AssertionError("original error");
                            module.enableHook = () -> { throw error; };
                            assertSame(error, assertThrows(AssertionError.class, () -> module.setToggled(true)));
                        } else {
                            boolean target = !"disable".equals(stage);
                            assertSame(failure, assertThrows(RuntimeException.class, () -> module.setToggled(target)));
                        }
                        assertEquals(stage, !"toggle".equals(stage) && !"disable".equals(stage), module.isToggled());
                        assertNull(stage, ModuleToggleJournal.getSnapshot());
                        assertEquals(stage, before, ModuleToggleJournal.currentSequence());
                    } finally { cleanup(module); }
                }
                assertNull(f.channel.readOutbound());
            }
        }

        @Test public void configLoadsAndDelayedActivationAreSilent() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                ProbeModule module = new ProbeModule();
                try {
                    Path directory = temporary.newFolder().toPath(); AtomicBoolean ready = new AtomicBoolean(true);
                    ConfigManager manager = config(directory, module, ready); enabledConfig(directory, module.getId());
                    module.setToggled(true); assertNotNull(ModuleToggleJournal.getSnapshot());
                    long before = ModuleToggleJournal.currentSequence();
                    module.enableHook = module.disableHook = () -> assertNull(ModuleToggleJournal.getSnapshot());
                    assertTrue(manager.getLastError(), manager.loadConfig("On"));
                    assertTrue(module.isToggled()); assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(before, ModuleToggleJournal.currentSequence());

                    module.setToggled(false); ready.set(false); before = ModuleToggleJournal.currentSequence();
                    assertTrue(manager.loadConfig("On")); manager.tick(); assertFalse(module.isToggled());
                    ready.set(true); manager.tick();
                    assertTrue(module.isToggled()); assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(before, ModuleToggleJournal.currentSequence());
                    module.setToggled(false); assertFalse(ModuleToggleJournal.getSnapshot().isEnabled());

                    before = ModuleToggleJournal.currentSequence(); module.beginSettingsUpdate();
                    try { module.setToggled(true); } finally { module.endConfigUpdate(); }
                    assertNull(ModuleToggleJournal.getSnapshot()); assertEquals(before, ModuleToggleJournal.currentSequence());
                    assertNull(f.channel.readOutbound());
                } finally { cleanup(module); }
            }
        }

        @Test public void configDuringCallbackInvalidatesOuterEvidenceAndFailureReleasesSuppression() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                SecondModule outer = new SecondModule(); ProbeModule configured = new ProbeModule();
                try {
                    Path directory = temporary.newFolder().toPath();
                    ConfigManager manager = config(directory, configured, new AtomicBoolean(true));
                    enabledConfig(directory, configured.getId());
                    long before = ModuleToggleJournal.currentSequence();
                    outer.enableHook = () -> assertTrue(manager.loadConfig("On"));
                    outer.setToggled(true);
                    assertTrue(outer.isToggled()); assertTrue(configured.isToggled());
                    assertNull(ModuleToggleJournal.getSnapshot()); assertEquals(before, ModuleToggleJournal.currentSequence());
                    Pulsar.BUS.post(new ProbeEvent()); assertEquals(1, configured.busEvents);

                    configured.enableHook = () -> { throw new IllegalStateException("config activation"); };
                    assertFalse(manager.loadConfig("On"));
                    assertFalse(configured.isToggled()); assertTrue(outer.isToggled());
                    assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(before, ModuleToggleJournal.currentSequence());
                    Pulsar.BUS.post(new ProbeEvent()); assertEquals(1, configured.busEvents);

                    configured.enableHook = null; configured.setToggled(true);
                    assertTrue(configured.isToggled());
                    ModuleToggleSnapshot enabled = ModuleToggleJournal.getSnapshot();
                    assertNotNull(enabled); assertTrue(enabled.isEnabled());
                    assertEquals(configured.getId(), enabled.getModuleId());
                    assertEquals(before + 1, enabled.getSequence());
                    assertEquals(before + 1, ModuleToggleJournal.currentSequence());
                    Pulsar.BUS.post(new ProbeEvent()); assertEquals(2, configured.busEvents);

                    configured.setToggled(false);
                    assertFalse(configured.isToggled()); assertTrue(outer.isToggled());
                    ModuleToggleSnapshot disabled = ModuleToggleJournal.getSnapshot();
                    assertNotNull(disabled); assertFalse(disabled.isEnabled());
                    assertEquals(configured.getId(), disabled.getModuleId());
                    assertEquals(before + 2, disabled.getSequence());
                    assertEquals(before + 2, ModuleToggleJournal.currentSequence());
                    Pulsar.BUS.post(new ProbeEvent()); assertEquals(2, configured.busEvents);
                    assertNull(f.channel.readOutbound());
                } finally { cleanup(outer); cleanup(configured); }
            }
        }

        @Test public void actualSceneBoundariesInvalidateAndRejectCrossWorldPublication() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                ProbeModule module = new ProbeModule();
                try {
                    module.setToggled(true); assertNotNull(ModuleToggleJournal.getSnapshot());
                    long before = ModuleToggleJournal.currentSequence();
                    f.mc.theWorld = RotationBehaviorHookTest.bare(WorldClient.class);
                    assertNull(ModuleToggleJournal.getSnapshot()); f.mc.theWorld = f.world;
                    assertNull("Observed invalidation must not revive after ABA", ModuleToggleJournal.getSnapshot());
                    module.setToggled(false); assertNotNull(ModuleToggleJournal.getSnapshot());
                    f.network.getClientPackets().worldChanged(); assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(before + 1, ModuleToggleJournal.currentSequence());

                    module.enableHook = () -> f.mc.theWorld = null;
                    module.setToggled(true); f.mc.theWorld = f.world;
                    assertTrue(module.isToggled()); assertNull(ModuleToggleJournal.getSnapshot());
                    assertEquals(before + 1, ModuleToggleJournal.currentSequence());
                    module.enableHook = null; module.setToggled(false);
                    assertNotNull(ModuleToggleJournal.getSnapshot());
                    f.mc.thePlayer = null; assertNull(ModuleToggleJournal.getSnapshot()); f.mc.thePlayer = f.player;
                    assertNull(ModuleToggleJournal.getSnapshot());
                    module.setToggled(true); assertNotNull(ModuleToggleJournal.getSnapshot());
                    f.channel.close(); assertNull(ModuleToggleJournal.getSnapshot());
                } finally { f.mc.theWorld = f.world; f.mc.thePlayer = f.player; cleanup(module); }
            }
        }

        @Test public void latestOnlyReadPathDoesNotScanModulesOrPerformActions() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                ProbeModule outer = new ProbeModule(); SecondModule inner = new SecondModule();
                try {
                    long before = ModuleToggleJournal.currentSequence();
                    outer.enableHook = () -> {
                        inner.setToggled(true);
                        assertEquals("toast_second", ModuleToggleJournal.getSnapshot().getModuleId());
                    };
                    outer.setToggled(true);
                    ModuleToggleSnapshot latest = ModuleToggleJournal.getSnapshot();
                    assertEquals("toast_probe", latest.getModuleId());
                    assertEquals(before + 2, latest.getSequence());
                    int callbacks = outer.calls.size() + inner.calls.size();
                    ScaffoldClientFlowTest.set(Pulsar.class, Pulsar.INSTANCE, "mm", null);
                    for (int i = 0; i < 100; i++) {
                        assertSame(latest, ModuleToggleJournal.getSnapshot());
                        assertEquals(before + 2, ModuleToggleJournal.currentSequence());
                    }
                    assertEquals(callbacks, outer.calls.size() + inner.calls.size());
                    assertEquals(0, outer.busEvents + inner.busEvents);
                    assertNull(f.channel.readOutbound());
                } finally { cleanup(outer); cleanup(inner); }
            }
        }

        @Test public void offThreadAndExpiredReadsCannotReplayEvidence() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = fixture()) {
                ProbeModule module = new ProbeModule();
                try {
                    module.setToggled(true); ModuleToggleSnapshot snapshot = ModuleToggleJournal.getSnapshot();
                    assertNotNull(snapshot); long sequence = ModuleToggleJournal.currentSequence();
                    AtomicReference<ModuleToggleSnapshot> workerResult = new AtomicReference<>(snapshot);
                    Thread worker = new Thread(() -> workerResult.set(ModuleToggleJournal.getSnapshot()), "toast-read-test");
                    worker.start(); worker.join(5_000);
                    assertFalse(worker.isAlive()); assertNull(workerResult.get());
                    assertSame(snapshot, ModuleToggleJournal.getSnapshot());
                    Thread.sleep(2_000);
                    assertNull(ModuleToggleJournal.getSnapshot());
                    assertFalse(snapshot.isFresh(System.nanoTime()));
                    assertEquals(sequence, ModuleToggleJournal.currentSequence());
                    module.setToggled(false);
                    assertTrue(ModuleToggleJournal.getSnapshot().getSequence() > sequence);
                    assertNull(f.channel.readOutbound());
                } finally { cleanup(module); }
            }
        }
    }
}
