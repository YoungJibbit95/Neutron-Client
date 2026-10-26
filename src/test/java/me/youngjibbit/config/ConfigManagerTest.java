package me.youngjibbit.config;

import com.google.gson.*;
import me.youngjibbit.command.impl.LoadCommand;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.rules.TemporaryFolder;
import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import static org.junit.Assert.*;

/** Real config/module/value code, synthetic modules and temporary data only; no game or GL. */
public class ConfigManagerTest {
    private static String originalHome;
    private static Path syntheticHome;
    @BeforeClass public static void isolateUserHome() throws IOException {
        originalHome = System.getProperty("user.home");
        syntheticHome = Files.createTempDirectory("pulsar-config-test-home-");
        System.setProperty("user.home", syntheticHome.toString());
    }
    @AfterClass public static void restoreUserHome() throws IOException {
        System.setProperty("user.home", originalHome);
        assertTrue(syntheticHome.toRealPath().startsWith(Paths.get(System.getProperty("java.io.tmpdir")).toRealPath()));
        assertTrue(syntheticHome.getFileName().toString().startsWith("pulsar-config-test-home-"));
        try (Stream<Path> files = Files.walk(syntheticHome)) {
            for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) Files.delete(file);
        }
    }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final List<String> messages = new ArrayList<>();
    private final AtomicBoolean ready = new AtomicBoolean(true);
    private final AtomicLong clock = new AtomicLong();

    @ModuleInfo(id = "synthetic", name = "Synthetic Module", category = Category.Client)
    public static class Sample extends Module {
        final SliderValue decimal = new SliderValue("Decimal", this, 1.0, 0, 10);
        final SliderValue whole = new SliderValue("Whole", this, 1.0, 0, 10, true);
        final CheckBoxValue flag = new CheckBoxValue("Flag", this, false);
        final ComboValue choice = new ComboValue("Choice", this, new String[]{"First", "Second"}, 0);
        final ColorValue color = new ColorValue("Color", this, new Color(1, 2, 3, 44));
        final Value<String> text = new Value<>("Text", this, "initial");
        final Value<Integer> count = new Value<>("Count", this, 1);
        int enables, disables, callbacks;
        Runnable enabledCheck;
        @Override public void onEnable() { enables++; if (enabledCheck != null) enabledCheck.run(); }
        @Override public void onDisable() { disables++; }
        @Override public void onValueChange() { callbacks++; }
    }

    private ConfigManager manager(Path directory, Sample sample) {
        return new ConfigManager(directory, Collections.singletonList(sample), ready::get, messages::add, clock::get);
    }
    private Path directory() throws IOException { return temporary.newFolder().toPath(); }
    private void write(Path path, String text) throws IOException { Files.write(path, text.getBytes(StandardCharsets.UTF_8)); }
    private String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private JsonObject settings(JsonObject root) { return root.getAsJsonObject("modules").getAsJsonObject("synthetic").getAsJsonObject("settings"); }
    private JsonObject json(ConfigManager manager) { return JsonParser.parseString(manager.serialize()).getAsJsonObject(); }
    private void desired(Sample sample) {
        sample.decimal.setValue(7.25);
        sample.whole.setValue(6.0);
        sample.flag.setValue(true);
        sample.choice.setValue("Second");
        sample.color.setConfigState(new Color(13, 24, 35, 67), true, .4f, 23);
        sample.text.setValue("synthetic text");
        sample.count.setValue(7);
        sample.setKey(42);
        sample.setToggled(true);
    }
    private void assertDesired(Sample sample) {
        assertEquals(7.25, sample.decimal.getValue(), 0);
        assertEquals(6, sample.whole.getValue(), 0);
        assertTrue(sample.flag.getValue());
        assertEquals("Second", sample.choice.getValue());
        assertEquals(new Color(13, 24, 35, 67), sample.color.getValue());
        assertEquals(67, sample.color.getColor().getAlpha());
        assertTrue(sample.color.isRainbow());
        assertEquals(.4f, sample.color.getRainbow().getSaturation(), 0);
        assertEquals(23, sample.color.getRainbow().getSpeed());
        assertEquals("synthetic text", sample.text.getValue());
        assertEquals(Integer.valueOf(7), sample.count.getValue());
        assertEquals(42, sample.getKey());
    }

    @Test public void namedRoundTripRestoresEveryTypeAndRestartsActiveModuleAfterData() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        desired(sample); assertTrue(configs.saveConfig("Named"));
        sample.decimal.setValue(2.0); sample.whole.setValue(2.0); sample.flag.setValue(false);
        sample.choice.setValue("First"); sample.color.setConfigState(Color.RED, false, 1, 15);
        sample.text.setValue("changed"); sample.count.setValue(2); sample.setKey(0);
        sample.callbacks = 0; AtomicInteger valueCallbacks = new AtomicInteger();
        sample.decimal.setOnChange(value -> valueCallbacks.incrementAndGet());
        sample.color.setOnChange(value -> valueCallbacks.incrementAndGet());
        sample.enabledCheck = () -> assertDesired(sample);
        int enables = sample.enables;
        assertTrue(configs.loadConfig("Named"));
        assertDesired(sample); assertTrue(sample.isToggled());
        assertEquals(enables + 1, sample.enables); assertEquals(1, sample.disables);
        assertEquals(1, sample.callbacks); assertEquals(2, valueCallbacks.get());
    }

    @Test public void zeroKeyAndDisabledStateOverrideFirstUseDefaults() throws Exception {
        Path dir = directory(); Sample first = new Sample(); ConfigManager one = manager(dir, first);
        one.initialize(Collections.emptyList()); first.setKey(45); first.setKey(0); one.shutdown();
        Sample second = new Sample(); second.setKey(44); ConfigManager two = manager(dir, second);
        two.initialize(Collections.singletonList("synthetic")); two.tick();
        assertFalse(second.isToggled()); assertEquals(0, second.getKey()); assertEquals(0, second.enables);
    }

    @Test public void freshInstanceRestoresAutomaticallyAndDefersHooksUntilReady() throws Exception {
        Path dir = directory(); Sample first = new Sample(); ConfigManager one = manager(dir, first);
        one.initialize(Collections.emptyList()); desired(first); one.shutdown();
        byte[] saved = Files.readAllBytes(one.defaultConfig.toPath());
        ready.set(false); Sample second = new Sample(); ConfigManager two = manager(dir, second);
        second.enabledCheck = () -> { assertTrue(ready.get()); assertDesired(second); };
        two.initialize(Collections.emptyList()); assertDesired(second); assertFalse(second.isToggled());
        two.tick(); assertEquals(0, second.enables); two.shutdown();
        assertArrayEquals(saved, Files.readAllBytes(two.defaultConfig.toPath()));
        ready.set(true); two.tick(); assertTrue(second.isToggled()); assertEquals(1, second.enables);
    }

    @Test public void shutdownBeforeWorldStillPersistsPendingEnabledDecision() throws Exception {
        ready.set(false); Path dir = directory(); Sample first = new Sample(); ConfigManager one = manager(dir, first);
        one.initialize(Collections.singletonList("synthetic")); one.shutdown();
        Sample second = new Sample(); ConfigManager two = manager(dir, second); two.initialize(Collections.emptyList());
        assertFalse(second.isToggled()); ready.set(true); two.tick(); assertTrue(second.isToggled());
    }

    @Test public void debouncedEditsAndCallbacksDoNotWriteOrRecursivelySaveOnLoad() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        configs.initialize(Collections.emptyList()); configs.shutdown(); assertTrue(configs.saveConfig("Named"));
        byte[] initial = Files.readAllBytes(configs.defaultConfig.toPath());
        for (int i = 0; i < 100; i++) { sample.decimal.setValue(i / 10.0); configs.tick(); }
        assertArrayEquals(initial, Files.readAllBytes(configs.defaultConfig.toPath()));
        sample.decimal.setOnChange(value -> assertFalse(configs.saveDefaultConfig()));
        assertTrue(configs.loadConfig("Named"));
        assertArrayEquals(initial, Files.readAllBytes(configs.defaultConfig.toPath()));
        sample.decimal.setOnChange(null); sample.decimal.setValue(4.5);
        clock.addAndGet(2_000_000_000L); configs.tick();
        assertFalse(Arrays.equals(initial, Files.readAllBytes(configs.defaultConfig.toPath())));
    }

    @Test public void namedFilesAreIndependentAndHiddenAutoStateIsNotListed() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        configs.initialize(Collections.emptyList()); desired(sample); assertTrue(configs.saveConfig("Keep"));
        byte[] named = Files.readAllBytes(dir.resolve("Keep.json")); sample.decimal.setValue(2.0); configs.shutdown();
        assertArrayEquals(named, Files.readAllBytes(dir.resolve("Keep.json")));
        assertTrue(configs.collectConfigs()); assertEquals(1, ConfigManager.localConfigs.size());
        assertEquals("Keep", ConfigManager.localConfigs.get(0).getName());
    }

    @Test public void realCommandSaveLoadAndListUseSameManagerDirectory() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        LoadCommand command = new LoadCommand(configs); desired(sample); command.execute("save", "My", "Config");
        assertTrue(Files.exists(dir.resolve("My Config.json"))); sample.decimal.setValue(1.0);
        command.execute("load", "My", "Config"); assertEquals(7.25, sample.decimal.getValue(), 0);
        command.execute("list"); assertTrue(messages.get(messages.size()-1).contains("My Config"));
    }

    @Test public void legacyArrayAndFlatWrappersStillLoad() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        write(dir.resolve("Old.json"), "{\"Synthetic Module\":{\"toggled\":true,\"key\":0,\"settings\":[{\"name\":\"Decimal\",\"value\":4.5},{\"name\":\"Choice\",\"value\":\"second\"},{\"name\":\"Color\",\"value\":1122867}]}}");
        assertTrue(configs.loadConfig("Old")); assertEquals(4.5, sample.decimal.getValue(), 0);
        assertEquals("Second", sample.choice.getValue()); assertEquals(1122867, sample.color.getValue().getRGB());
        write(dir.resolve("Flat.json"), "{\"version\":1,\"SyntheticModule\":{\"toggled\":false,\"key\":34,\"Decimal\":{\"value\":3.25},\"Flag\":true,\"Color\":{\"speed\":20,\"saturation\":0.7}}}");
        assertTrue(configs.loadConfig("Flat")); assertEquals(3.25, sample.decimal.getValue(), 0);
        assertTrue(sample.flag.getValue()); assertFalse(sample.isToggled()); assertEquals(34, sample.getKey());
        assertTrue(sample.color.isRainbow()); assertEquals(20, sample.color.getRainbow().getSpeed());
    }

    @Test public void invalidKnownEntriesRejectEntireLoadBeforeAnyMutation() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        String baseline = configs.serialize();
        String[] invalid = {"number", "choice", "boolean", "key", "alpha", "rainbow", "whole", "null", "fractional-key"};
        for (String kind : invalid) {
            JsonObject root = json(configs); JsonObject module = root.getAsJsonObject("modules").getAsJsonObject("synthetic");
            module.addProperty("enabled", true); settings(root).addProperty("text", "must not apply");
            switch (kind) {
                case "number": settings(root).addProperty("decimal", 100); break;
                case "choice": settings(root).addProperty("choice", "Missing"); break;
                case "boolean": settings(root).addProperty("flag", "yes"); break;
                case "key": module.addProperty("key", -1); break;
                case "fractional-key": module.addProperty("key", 2.5); break;
                case "alpha": settings(root).getAsJsonObject("color").addProperty("rgb", 4294967296L); break;
                case "rainbow": settings(root).getAsJsonObject("color").addProperty("speed", 0); break;
                case "whole": settings(root).addProperty("whole", 1.5); break;
                case "null": settings(root).add("flag", JsonNull.INSTANCE); break;
                default: fail();
            }
            write(dir.resolve("Invalid.json"), root.toString()); assertFalse(kind, configs.loadConfig("Invalid"));
            assertEquals(kind, baseline, configs.serialize()); assertEquals(0, sample.enables);
            assertFalse(configs.getLastError().isEmpty());
        }
    }

    @Test public void unknownEntriesAreReportedAndPreservedInAutomaticFile() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        JsonObject root = json(configs); settings(root).addProperty("unknown_setting", 33);
        root.getAsJsonObject("modules").add("future_module", JsonParser.parseString("{\"opaque\":true}"));
        write(configs.defaultConfig.toPath(), root.toString()); configs.initialize(Collections.emptyList());
        assertTrue(messages.get(messages.size()-1).contains("2 unknown entries"));
        sample.decimal.setValue(2.5); configs.shutdown();
        JsonObject saved = JsonParser.parseString(read(configs.defaultConfig.toPath())).getAsJsonObject();
        assertEquals(33, settings(saved).get("unknown_setting").getAsInt());
        assertTrue(saved.getAsJsonObject("modules").has("future_module"));
    }

    @Test public void automaticAliasesAreCanonicalizedWithoutLosingUnknownEntries() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        JsonObject root = json(configs); JsonObject module = root.getAsJsonObject("modules").remove("synthetic").getAsJsonObject();
        JsonObject values = module.getAsJsonObject("settings"); values.add("Decimal", values.remove("decimal"));
        values.addProperty("unknown", 17); root.getAsJsonObject("modules").add("Synthetic Module", module);
        write(configs.defaultConfig.toPath(), root.toString()); configs.initialize(Collections.emptyList());
        sample.decimal.setValue(5.0); configs.shutdown();
        Sample next = new Sample(); ConfigManager restored = manager(dir, next); restored.initialize(Collections.emptyList());
        assertEquals(5.0, next.decimal.getValue(), 0);
        assertFalse(restored.getLastError(), restored.getLastError().contains("failed"));
        JsonObject saved = JsonParser.parseString(read(restored.defaultConfig.toPath())).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("modules").has("Synthetic Module"));
        assertEquals(17, settings(saved).get("unknown").getAsInt());
    }

    @Test public void unknownLegacyAutomaticEntriesAreNotLost() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        String legacy = "{\"SyntheticModule\":{\"Decimal\":3.0,\"unknown\":17}}";
        write(configs.defaultConfig.toPath(), legacy); configs.initialize(Collections.emptyList());
        assertEquals(3.0, sample.decimal.getValue(), 0); sample.decimal.setValue(4.0); configs.shutdown();
        assertEquals(legacy, read(configs.defaultConfig.toPath()));
    }

    @Test public void actualThemeCallbackRunsWithRestoredSelectionAndNoPersonalFiles() throws Exception {
        me.youngjibbit.module.impl.client.ThemeModule theme = new me.youngjibbit.module.impl.client.ThemeModule();
        Path dir = directory(); ConfigManager configs = new ConfigManager(dir, Collections.singletonList(theme), () -> false, messages::add);
        theme.selectedTheme.setValue("SynthWave"); assertTrue(configs.saveConfig("Theme"));
        theme.selectedTheme.setValue("BlackWhite"); AtomicInteger calls = new AtomicInteger();
        theme.selectedTheme.setOnChange(value -> {
            calls.incrementAndGet();
            assertEquals(me.youngjibbit.utils.Theme.SynthWave, me.youngjibbit.utils.Theme.getDefaultTheme());
        });
        assertTrue(configs.loadConfig("Theme")); assertEquals(1, calls.get());
        assertEquals("SynthWave", theme.selectedTheme.getValue());
        assertFalse("Selection must use config persistence, not legacy theme writes",Files.exists(syntheticHome.resolve(".minecraft/pulsar/theme/theme_config.txt")));
    }

    @Test public void activeModulesInMenuDoNotRunHooksUntilWorldIsReady() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        sample.decimal.setValue(5.0); sample.setKey(34); sample.setToggled(true);
        assertTrue(configs.saveConfig("On")); sample.decimal.setValue(2.0);
        configs.initialize(Collections.emptyList()); ready.set(false);
        int before = sample.enables; assertTrue(configs.loadConfig("On")); assertEquals(0, sample.disables);
        assertEquals(5.0, sample.decimal.getValue(), 0); configs.tick(); assertEquals(before, sample.enables);
        ready.set(true); sample.enabledCheck = () -> { assertEquals(5.0, sample.decimal.getValue(), 0); assertEquals(34, sample.getKey()); };
        configs.tick(); assertEquals(before+1, sample.enables); assertEquals(1, sample.disables);
    }

    @Test public void damagedAndNewerAutomaticFilesRemainByteForByteIntact() throws Exception {
        for (String content : new String[]{"{broken", "{\"version\":999,\"modules\":{}}", "{version:2,modules:{}}", "[]", "{\"version\":2}", "{\"version\":2,\"modules\":{},\"modules\":{}}"}) {
            Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
            write(configs.defaultConfig.toPath(), content); configs.initialize(Collections.singletonList("synthetic"));
            sample.decimal.setValue(3.0); sample.setKey(20); clock.addAndGet(5_000_000_000L); configs.tick(); configs.shutdown();
            assertEquals(content, read(configs.defaultConfig.toPath())); assertFalse(configs.saveDefaultConfig());
            assertFalse(sample.isToggled()); assertTrue(messages.stream().anyMatch(m -> m.contains("disabled to preserve")));
        }
    }

    @Test public void failedAtomicReplacementRetainsPreviousNamedAndAutomaticFiles() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); AtomicBoolean failWrites = new AtomicBoolean();
        ConfigManager configs = new ConfigManager(dir, Collections.singletonList(sample), ready::get, messages::add, clock::get) {
            @Override protected void replaceFile(Path temporary, Path target) throws IOException {
                if (failWrites.get()) throw new IOException("synthetic replacement failure");
                super.replaceFile(temporary, target);
            }
        };
        configs.initialize(Collections.emptyList()); configs.shutdown(); assertTrue(configs.saveConfig("Keep"));
        byte[] named = Files.readAllBytes(dir.resolve("Keep.json")), auto = Files.readAllBytes(configs.defaultConfig.toPath());
        failWrites.set(true); sample.decimal.setValue(2.5);
        assertFalse(configs.saveConfig("Keep")); assertFalse(configs.saveDefaultConfig()); configs.shutdown();
        assertArrayEquals(named, Files.readAllBytes(dir.resolve("Keep.json")));
        assertArrayEquals(auto, Files.readAllBytes(configs.defaultConfig.toPath()));
        try (Stream<Path> paths = Files.list(dir)) { assertFalse(paths.anyMatch(p -> p.toString().endsWith(".tmp"))); }
    }

    @Test public void namesCannotEscapeOrAddressAutomaticState() throws Exception {
        Path dir = directory(); ConfigManager configs = manager(dir, new Sample());
        for (String name : new String[]{"../outside", "..\\outside", "C:\\outside", "/outside", ".last-state", "CON", "NUL", "trailing ", ""}) {
            assertFalse(name, configs.saveConfig(name)); assertFalse(name, configs.loadConfig(name));
        }
        try (Stream<Path> paths = Files.list(dir)) { assertEquals(0, paths.count()); }
        assertTrue(configs.saveConfig("Änderung.v2"));
        assertTrue(configs.loadConfig("Änderung.v2"));
    }

    @Test public void callbackFailureIsReportedWithoutClaimingFullSuccess() throws Exception {
        Path dir = directory(); Sample sample = new Sample(); ConfigManager configs = manager(dir, sample);
        JsonObject root = json(configs); settings(root).addProperty("decimal", 2.5);
        write(dir.resolve("Throw.json"), root.toString());
        sample.decimal.setOnChange(value -> { throw new IllegalStateException("synthetic callback failure"); });
        assertFalse(configs.loadConfig("Throw"));
        assertTrue(configs.getLastError().contains("partially applied"));
        assertTrue(configs.getLastError().contains("synthetic callback failure"));
        assertFalse(messages.stream().anyMatch(m -> m.startsWith("Config Throw loaded")));
    }

    @Test public void colorEditorPathsKeepBaseValueAlphaAndNotifyOnce() {
        Sample sample = new Sample(); sample.callbacks = 0;
        sample.color.setColor(new Color(30, 40, 50, 63)); assertEquals(1, sample.callbacks);
        sample.color.setHue(.5f); sample.color.setSaturation(.7f); sample.color.setBrightness(.8f);
        assertEquals(sample.color.getValue(), sample.color.getColor()); assertEquals(63, sample.color.getValue().getAlpha());
        int before = sample.callbacks; sample.color.setRainbow(true); sample.color.getRainbow().setSpeed(19);
        assertEquals(before+2, sample.callbacks);
    }

    @Test public void colorSynchronizationKeepsEditorHueForGrayAndBlack() {
        Sample sample = new Sample();
        sample.color.setColor(.6f, 0, .5f);
        assertEquals(.6, sample.color.getHue(), .00001);
        sample.color.setSaturation(1);
        assertEquals(Color.HSBtoRGB(.6f, 1, .5f) & 0xFFFFFF, sample.color.getColor().getRGB() & 0xFFFFFF);
        sample.color.setColor(.3f, .7f, 0);
        assertEquals(.3, sample.color.getHue(), .00001);
        assertEquals(.7, sample.color.getSaturation(), .00001);
        sample.color.setBrightness(.8f);
        assertEquals(Color.HSBtoRGB(.3f, .7f, .8f) & 0xFFFFFF, sample.color.getColor().getRGB() & 0xFFFFFF);
    }

    @Test public void sharedDirectoryAndActualJavaFileResolutionAreVerified() throws Exception {
        File data = temporary.newFolder(); Path expected = data.toPath().resolve("pulsar").resolve("configs").toAbsolutePath();
        assertEquals(expected, LocalConfig.configDirectory(data).toPath().toAbsolutePath());
        LocalConfig config = new LocalConfig(LocalConfig.configDirectory(data), "Named");
        assertEquals(expected.resolve("Named.json"), config.getFile().toPath());
        System.out.println("Java File resolution check: old child /pulsar/configs resolves within parent = "
                + new File(data, "/pulsar/configs").getCanonicalFile().equals(expected.toFile().getCanonicalFile()));
        assertFalse(Files.exists(expected)); // Descriptors/listing do not create or migrate files.
    }
}
