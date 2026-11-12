package me.youngjibbit.config;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ModuleToggleJournal;
import me.youngjibbit.notifications.NotificationManager;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** Config IO and application run on the client thread; value changes only mark a batch dirty. */
public class ConfigManager {
    private static final Set<String> ROTATION_MANUAL_IDS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "jitter","noise_mode","frequency","rot_mode","rotation_smoothing","yaw_speed","pitch_speed","random_mode",
            "hit_vec_radius","random_hit_vec","randomization","noise_strength","speed_variation","yaw_offset","pitch_offset",
            "min_speed","max_speed","interpolation","cellular_distance_mode","cellular_return_mode","cellular_jitter",
            "fractal_mode","gain","lacunarity","octaves","ping_pong_strength","weighted_strength","domain_warp_mode","domain_warp_amp")));

    public static final List<LocalConfig> localConfigs = new ArrayList<>();
    public static boolean loadVisuals;
    public final File defaultConfig;
    private static final int CONFIG_VERSION = 2;
    private final Path directory;
    private final Collection<Module> modules;
    private final BooleanSupplier ready;
    private final Consumer<String> messages;
    private final LongSupplier clock;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<Module, Boolean> pendingActivation = new LinkedHashMap<>();
    private boolean initialized, applying, dirty, automaticReadOnly;
    private long saveAfter;
    private JsonObject automaticTemplate;
    private String lastError = "";

    public ConfigManager() {
        this(LocalConfig.configDirectory(Minecraft.getMinecraft().mcDataDir).toPath(),
                Pulsar.INSTANCE.getMm().getAllModules(),
                () -> Minecraft.getMinecraft().theWorld != null && Minecraft.getMinecraft().thePlayer != null,
                ConfigManager::report, System::nanoTime);
        installBundledPlaytestConfigs();
    }

    /** Install versioned examples once. An existing file always belongs to the user. */
    public boolean installBundledPlaytestConfigs() {
        for (String name : new String[]{"HypeMC-Final-0.2.1", "Mineblaze-Final-0.2.1", "PredictionAC"}) {
            Path destination = directory.resolve(name + ".json");
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) continue;
            Path temporary = null;
            try (InputStream resource = ConfigManager.class.getResourceAsStream("/neutron/configs/" + name + ".json")) {
                if (resource == null) throw new IOException("Bundled configuration is missing: " + name);
                Files.createDirectories(directory);
                temporary = Files.createTempFile(directory, ".bundled-", ".tmp");
                Files.copy(resource, temporary, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temporary, destination);
                } catch (FileAlreadyExistsException existing) {
                    // A concurrent installer/user won the filename. Keep their contents.
                }
            } catch (IOException failure) {
                lastError = "Could not install bundled config " + name + ": " + failure.getMessage();
                return false;
            } finally {
                if (temporary != null) {
                    try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                }
            }
        }
        return true;
    }

    // Explicit dependencies allow real module/value tests without starting Minecraft.
    public ConfigManager(Path directory, Collection<Module> modules, BooleanSupplier ready, Consumer<String> messages) {
        this(directory, modules, ready, messages, System::nanoTime);
    }

    ConfigManager(Path directory, Collection<Module> modules, BooleanSupplier ready, Consumer<String> messages, LongSupplier clock) {
        this.directory = directory.toAbsolutePath().normalize();
        this.defaultConfig = this.directory.resolve(".last-state.json").toFile();
        this.modules = new ArrayList<>(modules);
        this.ready = ready;
        this.messages = messages;
        this.clock = clock;
        for (Module module : this.modules) {
            module.setPersistenceListener(toggleChanged -> {
                if (!applying && initialized) {
                    if (toggleChanged) pendingActivation.remove(module);
                    markDirty();
                }
            });
        }
    }

    public String getLastError() { return lastError; }
    public void message(String message) { messages.accept(message); }

    public boolean collectConfigs() {
        localConfigs.clear();
        if (!Files.exists(directory)) return true;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.startsWith(".") || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    localConfigs.add(new LocalConfig(directory.toFile(), name.substring(0, name.length() - 5)));
                } catch (IllegalArgumentException ignored) {
                    // Unsupported filenames are never command targets.
                }
            }
            localConfigs.sort(Comparator.comparing(LocalConfig::getName, String.CASE_INSENSITIVE_ORDER));
            return true;
        } catch (IOException e) {
            fail("Cannot list configs", e);
            messages.accept(lastError);
            return false;
        }
    }

    public boolean saveConfig(String name) {
        try { return saveConfig(name, serialize()); }
        catch (RuntimeException e) { return fail("Cannot serialize config", e); }
    }

    /** A new library entry never replaces an independently created file. */
    public boolean createConfig(String name) {
        if (applying) return fail("Cannot save during config application", new IllegalStateException("Load is in progress."));
        try {
            Path path=namedPath(name);
            String content=serialize();
            parse(content);
            writeAtomically(path,content,false);
            collectConfigs();lastError="";
            return true;
        } catch(IOException | RuntimeException e) { return fail("Cannot create config",e); }
    }

    public boolean saveConfig(String name, String content) {
        if (applying) return fail("Cannot save during config application", new IllegalStateException("Load is in progress."));
        try {
            Path path = namedPath(name);
            parse(content);
            writeAtomically(path, content);
            collectConfigs();
            lastError = "";
            NotificationManager.success("Config saved","The configuration was written successfully.");
            return true;
        } catch (IOException | RuntimeException e) {
            return fail("Cannot save config", e);
        }
    }

    /** Rename the stored file itself; never serialize the currently running client. */
    public boolean renameConfig(String sourceName, String targetName) {
        if (applying) return fail("Cannot rename during config application", new IllegalStateException("Load is in progress."));
        try {
            ConfigRename.rename(namedPath(sourceName), namedPath(targetName));
            collectConfigs();
            lastError = "";
            return true;
        } catch (IOException | RuntimeException e) { return fail("Cannot rename config", e); }
    }

    public boolean delete(String name) {
        try {
            boolean removed = Files.deleteIfExists(namedPath(name));
            collectConfigs();
            return removed;
        } catch (IOException | RuntimeException e) { return fail("Cannot delete config", e); }
    }

    private Path namedPath(String name) throws IOException {
        Path path = new LocalConfig(directory.toFile(), name).getFile().toPath();
        if (Files.isSymbolicLink(path)) throw new IOException("Config links are not supported.");
        return path;
    }

    public void initialize(Collection<String> defaultEnabledIds) {
        if (initialized) throw new IllegalStateException("Config manager already initialized.");
        if (Files.exists(defaultConfig.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (Files.isSymbolicLink(defaultConfig.toPath())) throw new IOException("Automatic config is a link.");
                Plan plan = parse(readConfigData(defaultConfig.toPath()));
                apply(plan);
                if (plan.root.has("modules")) automaticTemplate = canonicalTemplate(plan.root);
                else if (plan.unknown > 0) {
                    automaticReadOnly = true;
                    messages.accept("Unknown legacy entries retained; automatic saving paused to preserve the original file.");
                }
                messages.accept(loadMessage("Last state", plan));
            } catch (IOException | RuntimeException e) {
                automaticReadOnly = true;
                fail("Last state was not restored; automatic saving is disabled to preserve its file", e);
                messages.accept(lastError);
            }
        } else {
            for (Module module : modules) {
                if (defaultEnabledIds.contains(module.getId())) pendingActivation.put(module, true);
            }
            markDirty();
        }
        initialized = true;
    }

    public boolean loadConfig(String name) {
        try {
            Plan plan = parse(readConfigData(namedPath(name)));
            apply(plan);
            if (initialized) markDirty();
            lastError = "";
            messages.accept(loadMessage("Config " + name, plan));
            NotificationManager.success("Config loaded",pendingActivation.isEmpty()?"The configuration was applied successfully.":"Values applied. Module activation waits for a world and player.");
            return true;
        } catch (IOException | RuntimeException e) {
            fail("Config load failed", e);
            messages.accept(lastError);
            return false;
        }
    }

    /** Apply only the bundled values. Bindings and module activation are user choices. */
    public boolean applyIntavePreset(String selection) {
        String id = selection == null ? "" : selection.toLowerCase(Locale.ROOT);
        if ("autoblock".equals(id)) id = "aura";
        if ("wtap".equals(id)) id = "wtap2";
        try (InputStream input = ConfigManager.class.getResourceAsStream("/neutron/configs/Intave.json")) {
            if (input == null) throw new IOException("Bundled Intave preset is missing.");
            JsonObject settings;
            try (JsonReader reader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                settings = object(readElement(reader, 0), "Intave settings");
            }
            if (!id.isEmpty() && !settings.has(id))
                throw new IllegalArgumentException("Choose one of " + String.join(", ", settings.keySet()));
            Plan plan = new Plan(settings);
            for (Map.Entry<String, JsonElement> entry : settings.entrySet()) {
                if (!id.isEmpty() && !id.equals(entry.getKey())) continue;
                Module module = findModule(entry.getKey());
                if (module == null) throw new IllegalArgumentException("Missing module: " + entry.getKey());
                Change change = new Change(module);
                parseSettings(plan, change, object(entry.getValue(), entry.getKey()));
                plan.changes.add(change);
            }
            if (plan.unknown != 0) throw new IOException("Unknown bundled Intave setting.");
            apply(plan, true);
            if (initialized) markDirty();
            lastError = "";
            messages.accept("Intave preset applied to " + (id.isEmpty() ? "all preset modules" : id) + ".");
            return true;
        } catch (IOException | RuntimeException e) {
            fail("Intave preset failed", e);
            messages.accept(lastError);
            return false;
        }
    }

    private String loadMessage(String label, Plan plan) {
        return label + " loaded" + (pendingActivation.isEmpty() ? "" : "; activation pending until world/player are ready")
                + (plan.migratedRotations==0?"":"; "+plan.migratedRotations+" rotation profiles migrated to Benutzerdefiniert")
                + (plan.migratedOctaves==0?"":"; legacy octave counts truncated with minimum 1")
                + (plan.migratedTiming==0?"":"; legacy preset timing preserved (Min/Max/Interpolation = 30/40/5); disable Preset Timing to use stored settings")
                + (plan.migratedModes==0?"":"; historical Smooth mapped to Legit (bounded exponential interpolation)")
                + (plan.migratedTargetHudModes==0?"":"; "+plan.migratedTargetHudModes+" TargetHUD design migrated to a current mode")
                + (plan.legacyAssist==0?"":"; historical AimAssist modes use the shared bounded camera path; broken negative-noise speeds and repeated jitter are not reproduced")
                + (plan.migratedDefaults==0?"":"; new optional rotation features defaulted off for legacy profiles")
                + (plan.legacyScaffold==0?"":"; historical Scaffold uses the shared Godbridge plan; face raycasts remain mandatory")
                + (plan.migratedNScaffold==0?"":"; NScaffold settings migrated to Demise; obsolete planner controls removed")
                + (plan.migratedBedBreaker==0?"":"; BedBreaker uses Demise defaults where omitted; Turn Speed has no frame-speed equivalent; animation packets follow Demise; no local swing setting")
                + (plan.legacyNetwork==0?"":"; historical network options migrated to bounded FIFO windows/earned-step budgets; ping, velocity and corrections remain immediate")
                + (plan.migratedNetworkPolicies==0?"":"; network policies normalized: prior Combat target selection retained, Min Range uses Min Server Distance, Delay uses Release Cooldown MS after successful release (no additional enable wait)")
                + (plan.clampedLegacyDelays==0?"":"; legacy delay requests above the shared 500 ms bound were capped explicitly")
                + (plan.unknown == 0 ? "." : "; " + plan.unknown + " unknown entries skipped (partial compatibility).");
    }

    public String readConfigData(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private void markDirty() {
        dirty = true;
        saveAfter = clock.getAsLong() + TimeUnit.SECONDS.toNanos(1);
    }

    public void tick() {
        if (!initialized || applying) return;
        if (ready.getAsBoolean() && !pendingActivation.isEmpty()) {
            applying = true;
            try {
                ModuleToggleJournal.beginConfigApplication();
                for (Map.Entry<Module, Boolean> entry : new ArrayList<>(pendingActivation.entrySet())) {
                    Module module = entry.getKey();
                    if (module.isToggled()) module.setToggled(false);
                    module.setToggled(entry.getValue());
                    pendingActivation.remove(module);
                }
                markDirty();
            } catch (RuntimeException e) {
                automaticReadOnly = true;
                fail("Module activation failed; automatic saving paused", e);
                messages.accept(lastError);
                pendingActivation.clear();
            } finally {
                applying = false;
                ModuleToggleJournal.endConfigApplication();
            }
        }
        if (dirty && !automaticReadOnly && clock.getAsLong() >= saveAfter) saveDefaultConfig();
    }

    public boolean saveDefaultConfig() {
        if (!initialized || automaticReadOnly || applying) return false;
        try {
            writeAtomically(defaultConfig.toPath(), serialize(automaticTemplate));
            dirty = false;
            lastError = "";
            return true;
        } catch (IOException | RuntimeException e) {
            saveAfter = clock.getAsLong() + TimeUnit.SECONDS.toNanos(5);
            fail("Cannot save last state", e);
            messages.accept(lastError);
            return false;
        }
    }

    public void shutdown() {
        if (initialized && dirty && !automaticReadOnly) saveDefaultConfig();
    }

    private void writeAtomically(Path target, String content) throws IOException {
        writeAtomically(target,content,true);
    }

    private void writeAtomically(Path target, String content, boolean replace) throws IOException {
        Files.createDirectories(directory);
        if(!replace) {
            try(DirectoryStream<Path> files=Files.newDirectoryStream(directory)) {
                for(Path file:files) if(file.getFileName().toString().equalsIgnoreCase(target.getFileName().toString()))
                    throw new FileAlreadyExistsException(file.toString());
            }
        }
        if (Files.isSymbolicLink(target)) throw new IOException("Config links are not supported.");
        Path temporary = Files.createTempFile(directory, ".config-", ".tmp");
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile())) {
                stream.write(content.getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            if(replace)replaceFile(temporary,target);else installNewFile(temporary,target);
        } finally { Files.deleteIfExists(temporary); }
    }

    protected void installNewFile(Path temporary, Path target) throws IOException {
        // ATOMIC_MOVE can replace an existing target on some providers even without
        // REPLACE_EXISTING. New library entries require the non-replacing move.
        Files.move(temporary,target);
    }

    protected void replaceFile(Path temporary, Path target) throws IOException {
        // No non-atomic/truncating fallback: keep the old file if replacement fails.
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public String serialize() { return serialize(null); }

    private String serialize(JsonObject template) {
        JsonObject root = template == null ? new JsonObject() : template.deepCopy();
        root.addProperty("version", CONFIG_VERSION);
        JsonObject entries = root.has("modules") ? object(root.get("modules"), "modules") : new JsonObject();
        Set<String> moduleIds = new HashSet<>();
        for (Module module : modules) {
            if (!moduleIds.add(module.getId())) throw new IllegalStateException("Duplicate module ID: " + module.getId());
            JsonObject entry = entries.has(module.getId()) ? object(entries.get(module.getId()), module.getId()) : new JsonObject();
            entry.addProperty("enabled", pendingActivation.getOrDefault(module, module.isToggled()));
            entry.addProperty("key", module.getKey());
            if(isCombatRotation(module))entry.addProperty("rotationVersion",2);
            if(isNetworkPolicy(module))entry.addProperty("networkVersion",1);
            JsonObject settings = entry.has("settings") ? object(entry.get("settings"), "settings") : new JsonObject();
            Set<String> valueIds = new HashSet<>();
            for (Value<?> value : module.getValues()) {
                if (!valueIds.add(value.getId())) throw new IllegalStateException("Duplicate setting ID: " + module.getId() + "." + value.getId());
                settings.add(value.getId(), writeSetting(value));
            }
            entry.add("settings", settings);
            entries.add(module.getId(), entry);
        }
        root.add("modules", entries);
        return gson.toJson(root);
    }

    private JsonElement writeSetting(Value<?> value) {
        if (value instanceof ColorValue) {
            ColorValue color = (ColorValue) value;
            JsonObject json = new JsonObject();
            json.addProperty("rgb", color.getValue().getRGB());
            json.addProperty("rainbow", color.isRainbow());
            if (color.isRainbow()) {
                json.addProperty("saturation", color.getRainbow().getSaturation());
                json.addProperty("speed", color.getRainbow().getSpeed());
            }
            return json;
        }
        return gson.toJsonTree(value.getValue());
    }

    private JsonObject canonicalTemplate(JsonObject original) {
        JsonObject template = original.deepCopy();
        JsonObject canonical = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : template.getAsJsonObject("modules").entrySet()) {
            Module module = findModule(entry.getKey());
            if (module == null) {
                canonical.add(entry.getKey(), entry.getValue());
                continue;
            }
            JsonObject data = entry.getValue().getAsJsonObject();
            if (data.has("settings")) {
                JsonObject settings = new JsonObject();
                for (Map.Entry<String, JsonElement> setting : data.getAsJsonObject("settings").entrySet()) {
                    // Migration has resolved the current values; aliases and
                    // retired fields must not survive automatic saves.
                    if ("nscaffold".equals(module.getId()) && (nScaffoldAlias(setting.getKey()) != null
                            || retiredNScaffoldSetting(setting.getKey()))) continue;
                    if ("bedbreaker".equals(module.getId()) && retiredBedBreakerSetting(setting.getKey())) continue;
                    Value<?> value = module.getValueByName(setting.getKey());
                    settings.add(value == null ? setting.getKey() : value.getId(), setting.getValue());
                }
                data.add("settings", settings);
            }
            canonical.add(module.getId(), data);
        }
        template.add("modules", canonical);
        return template;
    }

    private Plan parse(String content) throws IOException {
        JsonElement parsed;
        try (JsonReader reader = new JsonReader(new StringReader(content))) {
            reader.setLenient(false);
            parsed = readElement(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON data.");
        }
        JsonObject root = object(parsed, "root");
        boolean v2 = root.has("modules");
        if (root.has("version")) {
            int version = integer(root.get("version"), "version");
            if (version < 1 || version > CONFIG_VERSION) throw new IllegalArgumentException("Unsupported config version; file has been left unchanged.");
            if (version == CONFIG_VERSION && !v2) throw new IllegalArgumentException("Missing modules object.");
        }
        JsonObject entries = v2 ? object(root.get("modules"), "modules") : root;
        Plan plan = new Plan(root);
        Set<Module> seen = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            if (!v2 && "version".equals(entry.getKey())) continue;
            Module module = findModule(entry.getKey());
            if (module == null) { plan.unknown++; continue; }
            if (!seen.add(module)) throw new IllegalArgumentException("Duplicate module entry: " + module.getId());
            JsonObject json = object(entry.getValue(), module.getId());
            Change change = new Change(module);
            Set<String> names=settingNames(json);
            if(isNetworkPolicy(module)) {
                if(json.has("networkVersion") && integer(json.get("networkVersion"),"networkVersion")!=1)
                    throw new IllegalArgumentException("Unsupported network policy version; file has been left unchanged.");
                change.currentNetwork=json.has("networkVersion") || (module.getId().equals("backtrack")
                        ? names.contains("targetsource") || names.contains("targetholdms") || names.contains("minserverdistance") || names.contains("mindistancegain")
                        : names.contains("releasecooldownms") || names.contains("releaseon") || names.contains("chargerange") || names.contains("releaserange"));
            }
            change.legacyScaffold=module.getId().equals("scaffold") && (names.contains("samey") || names.contains("raytrace"));
            change.legacyBacktrack=module.getId().equals("backtrack") && (names.contains("delayping") || names.contains("delayvelocity") || names.contains("enemydistance") || (names.contains("delay") && !names.contains("delaymode") && !change.currentNetwork));
            change.legacyLag=module.getId().equals("lagrange") && (names.contains("ticksdelaybetweenpackets") || names.contains("distanceenemyblocks"));
            change.legacyTickbase=module.getId().equals("tickbase") && names.contains("ticks");
            int rotationVersion=isCombatRotation(module) && json.has("rotationVersion")?integer(json.get("rotationVersion"),"rotationVersion"):1;
            if(isCombatRotation(module) && (rotationVersion<1 || rotationVersion>2))throw new IllegalArgumentException("Unsupported rotation profile version; file has been left unchanged.");
            JsonObject explicitSettings=json.has("settings") && json.get("settings").isJsonObject()?json.getAsJsonObject("settings"):json;
            change.legacyRotation=module.getId().equals("aimassist") && !explicitSettings.has("randomization_style") && !explicitSettings.has("Randomization Style");
            String enabled = json.has("enabled") ? "enabled" : "toggled";
            if (json.has(enabled)) change.enabled = bool(json.get(enabled), module.getId() + ".enabled");
            if (json.has("key")) {
                change.key = integer(json.get("key"), module.getId() + ".key");
                if (change.key < 0 || change.key > 255) throw new IllegalArgumentException("Invalid keybind: " + module.getId());
            }
            if (v2) {
                if (json.has("settings")) parseSettings(plan, change, object(json.get("settings"), "settings"));
            } else if (json.has("settings") && json.get("settings").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("settings")) {
                    JsonObject setting = object(element, "legacy setting");
                    JsonElement name = setting.get("name");
                    if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Missing legacy setting name.");
                    parseSetting(plan, change, name.getAsString(), setting.get("value"));
                }
            } else {
                for (Map.Entry<String, JsonElement> value : json.entrySet()) {
                    if (Arrays.asList("key", "enabled", "toggled", "rotationVersion", "networkVersion").contains(value.getKey())) continue;
                    JsonElement raw = value.getValue();
                    if (raw.isJsonObject() && raw.getAsJsonObject().has("value")) raw = raw.getAsJsonObject().get("value");
                    parseSetting(plan, change, value.getKey(), raw);
                }
            }
            // Existing inventory files predate organized hotbar planning. New module
            // instances use Organized, but loading an old setup keeps its exact layout.
            if ("invmanager".equals(module.getId())) defaultSetting(change,"layout","Legacy");
            // Loading older inventory profiles also clears newer interaction/timing choices
            // left by a previously loaded profile. Explicit modern settings always win.
            if ("invmanager".equals(module.getId()) || "stealer".equals(module.getId())) {
                defaultSetting(change, "interaction_mode", "Legacy");
                defaultSetting(change, "speed_profile", "Legacy");
                if ("invmanager".equals(module.getId())) defaultSetting(change, "drop_order", "Priority");
                else defaultSetting(change, "pick_profile", "Shuffle");
            }
            // A present NScaffold profile owns its complete Demise setup. Current
            // keys win over aliases; omitted values must not inherit warm state.
            if ("nscaffold".equals(module.getId())) {
                for (Map.Entry<Value<?>,Object> alias : change.nScaffoldAliases.entrySet())
                    if (!change.values.containsKey(alias.getKey())) change.values.put(alias.getKey(), alias.getValue());
                for (Value<?> value : module.getValues())
                    if (!change.values.containsKey(value)) change.values.put(value, value.getDefaultValue());
                if (change.migratedNScaffold) plan.migratedNScaffold++;
            }
            // BedBreaker now has the complete Demise rotation profile. Its old
            // combined per-tick speed cannot represent the per-frame axis ranges.
            // Explicit current values and host smart-disable choices are retained.
            if ("bedbreaker".equals(module.getId())) {
                for (Value<?> value : module.getValues())
                    if (!change.values.containsKey(value)) change.values.put(value, value.getDefaultValue());
                if (change.migratedBedBreaker) plan.migratedBedBreaker++;
            }
            // Old geometric effects occupied the palette selector. An explicit new
            // UI mode wins regardless of JSON key order; Lines has no UI counterpart.
            if(change.legacyUiGlowMode!=null)defaultSetting(change,"ui_glow_mode",change.legacyUiGlowMode);
            // Old HUD files explicitly stored local effect choices. Preserve them instead of silently inheriting new defaults.
            Value<?> effectSource=module.getValueByName("effects_source");
            if(effectSource!=null&&!change.values.containsKey(effectSource)&&change.values.keySet().stream().anyMatch(value->Arrays.asList("background","blur","glow","bloom","blur_strength","gradient_speed","theme").contains(value.getId())))change.values.put(effectSource,"Local");
            Value<?> localTextExtent=module.getValueByName("local_text_glow_extent"),storedTextRadius=module.getValueByName("text_glow_radius");
            if(localTextExtent!=null&&storedTextRadius!=null&&!change.values.containsKey(localTextExtent)&&change.values.containsKey(storedTextRadius))change.values.put(localTextExtent,true);
            Value<?> rotationPreset=module.getValueByName("randomization_style");
            if(change.legacyRotation && change.values.containsKey(module.getValueByName("rotations")))plan.legacyAssist++;
            if(rotationPreset!=null && !change.values.containsKey(rotationPreset)) {
                boolean manual=change.values.keySet().stream().anyMatch(value -> ROTATION_MANUAL_IDS.contains(value.getId()) || ("rotations".equals(value.getId()) && value instanceof ComboValue));
                if(manual) { change.values.put(rotationPreset,"Benutzerdefiniert");plan.migratedRotations++; }
            }
            Value<?> timing=module.getValueByName("preset_timing");
            Object preset=change.values.get(rotationPreset);
            if(timing!=null && !change.values.containsKey(timing) && preset!=null) {
                boolean legacy=!"Benutzerdefiniert".equals(preset) && !"Aus".equals(preset);
                change.values.put(timing,legacy);
                if(legacy)plan.migratedTiming++;
            }
            if(rotationVersion==1 && isCombatRotation(module)) {
                boolean added=false;
                for(String id:Arrays.asList("smart_aim","stop_on_target","heuristics","interpolate_rotation","advanced_pitch","random_mode")) {
                    Value<?> optional=module.getValueByName(id);
                    if(optional!=null && !change.values.containsKey(optional)){change.values.put(optional,optional.getDefaultValue());added=true;}
                }
                if(added)plan.migratedDefaults++;
            }
            // Adaptive's new speed range may be absent in older files. Derive it
            // from incoming legacy axes or their defaults, never hidden warm state.
            // Other rotation modes keep their existing partial-import semantics.
            if("aura".equals(module.getId())
                    && "Adaptive".equals(change.values.get(module.getValueByName("rot_mode")))) {
                for(String axis:Arrays.asList("yaw_speed","pitch_speed")) {
                    Value<?> value=module.getValueByName(axis);
                    if(value!=null && !change.values.containsKey(value))change.values.put(value,value.getDefaultValue());
                }
            }
            if(change.legacyScaffold) {
                Value<?> mode=module.getValueByName("rotation_mode");if(mode!=null && !change.values.containsKey(mode))change.values.put(mode,"Godbridge");
                plan.legacyScaffold++;
            }
            prioritizeScaffoldSettings(change);
            migrateNetworkOptions(plan,change);
            plan.changes.add(change);
        }
        return plan;
    }
    /** Explicit modern controls win over a legacy preset regardless of JSON key order. */
    private static void prioritizeScaffoldSettings(Change change) {
        if(!"scaffold".equals(change.module.getId()))return;
        Map<Value<?>,Object> ordered=new LinkedHashMap<>();
        for(String alias:Arrays.asList("Sprint","Bridge Rotation Speed","Aim Speed")) {
            Value<?> value=change.module.getValueByName(alias);
            if(value!=null && change.values.containsKey(value))ordered.put(value,change.values.get(value));
        }
        ordered.putAll(change.values);
        change.values.clear();change.values.putAll(ordered);
    }
    private static boolean isNetworkPolicy(Module module) { return module.getId().equals("backtrack") || module.getId().equals("tickbase"); }
    private static boolean isCombatRotation(Module module) { return module.getId().equals("aura") || module.getId().equals("aimassist"); }
    private static boolean oldScaffoldName(String name) {String id=name.replace("_","").replace(" ","");return id.equalsIgnoreCase("SameY") || id.equalsIgnoreCase("Raytrace");}
    private static Set<String> settingNames(JsonObject entry) {
        Set<String> names=new HashSet<>();
        JsonElement settings=entry.has("settings")?entry.get("settings"):entry;
        if(settings.isJsonObject())for(String name:settings.getAsJsonObject().keySet())names.add(normalizedName(name));
        if(settings.isJsonArray())for(JsonElement element:settings.getAsJsonArray())if(element.isJsonObject()) {
            JsonElement name=element.getAsJsonObject().get("name");if(name!=null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString())names.add(normalizedName(name.getAsString()));
        }
        return names;
    }
    private static String normalizedName(String name) {return name.toLowerCase(Locale.ROOT).replace("_","").replace(" ","");}
    /** Only renames with a corresponding Demise setting belong in this table. */
    private static String nScaffoldAlias(String name) {
        switch (normalizedName(name)) {
            case "buildmode": return "mode";
            case "visiblerotation": return "rotations";
            case "keepheight": return "keepy";
            case "fastclutchalways": return "instant_rot_always";
            case "fastclutchonmiss": return "instant_rot_on_miss";
            case "fastclutchprediction": return "instant_rot_predict";
            case "shortstopduration(ms)": return "duration";
            default: return null;
        }
    }
    private static boolean retiredNScaffoldSetting(String name) {
        return Arrays.asList("turnspeed", "godbridgepitch", "lookahead", "autoselectblocks",
                "placementdelay", "advancedrotations", "smartdisableondeath", "smartdisableonworldchange")
                .contains(normalizedName(name));
    }
    private static void validateRetiredNScaffoldSetting(String name, JsonElement json) {
        String id = normalizedName(name), label = "nscaffold." + name;
        if (Arrays.asList("autoselectblocks", "advancedrotations", "smartdisableondeath", "smartdisableonworldchange").contains(id)) {
            bool(json, label);
            return;
        }
        double value = number(json, label);
        double min = "turnspeed".equals(id) ? 1 : "godbridgepitch".equals(id) ? 65 : 0;
        double max = "turnspeed".equals(id) ? 180 : "godbridgepitch".equals(id) ? 89 : "lookahead".equals(id) ? 1 : 500;
        if (value < min || value > max) throw new IllegalArgumentException("Number out of range: " + label);
    }
    private static boolean retiredBedBreakerSetting(String name) {
        return Arrays.asList("turnspeed", "swing").contains(normalizedName(name));
    }
    private static void validateRetiredBedBreakerSetting(String name, JsonElement json) {
        String label = "bedbreaker." + name;
        if ("swing".equals(normalizedName(name))) {
            bool(json, label);
            return;
        }
        double value = number(json, label);
        if (value < 1 || value > 180) throw new IllegalArgumentException("Number out of range: " + label);
    }
    private static void defaultSetting(Change change,String name,Object value) {Value<?> setting=change.module.getValueByName(name);if(setting!=null && !change.values.containsKey(setting))change.values.put(setting,value);}
    private static void migrateNetworkOptions(Plan plan,Change change) {
        String id=change.module.getId();
        if (id.equals("backtrack") || id.equals("lagrange") || id.equals("tickbase"))
            defaultSetting(change, "only_aura", true);
        if(id.equals("backtrack")) {
            defaultSetting(change,"delay_mode",change.legacyBacktrack?"Fixed":"Range");
            defaultSetting(change,"target_source",change.currentNetwork?"Attack / Cursor":change.legacyBacktrack?"Combat / Cursor":"Combat / Nearest");
            defaultSetting(change,"target_hold_ms",change.currentNetwork?350D:500D);
            defaultSetting(change,"min_distance_gain",.08D);defaultSetting(change,"diagnostics",false);
            if(!change.currentNetwork)plan.migratedNetworkPolicies++;
            defaultSetting(change,"delay",change.legacyBacktrack?500D:200D);defaultSetting(change,"min_range",2.2D);
            defaultSetting(change,"animals",change.legacyBacktrack);defaultSetting(change,"mobs",change.legacyBacktrack);
            if(change.legacyBacktrack){defaultSetting(change,"active_on_hit",true);defaultSetting(change,"active_on_no_hit",true);plan.legacyNetwork++;}
        } else if(id.equals("lagrange")) {
            defaultSetting(change,"delay_mode",change.legacyLag?"Ticks":"MS");defaultSetting(change,"window_ticks",2D);
            defaultSetting(change,"preactivation",change.legacyLag?2D:0D);defaultSetting(change,"only_swinging",change.legacyLag);defaultSetting(change,"animals",!change.legacyLag);
            if(change.legacyLag)plan.legacyNetwork++;
        } else if(id.equals("tickbase")) {
            // Full imports predate packet timing modes. Missing keys must have the
            // same meaning after a warm import as they have in a fresh client;
            // explicit modern values are retained by defaultSetting.
            defaultSetting(change,"mode","Player Steps");
            defaultSetting(change,"packet_hold_ms",200D);defaultSetting(change,"pulse_gap_ms",60D);
            defaultSetting(change,"release_cooldown_ms",change.currentNetwork || change.legacyTickbase?300D:0D);
            defaultSetting(change,"release_on","Attack");defaultSetting(change,"charge_range",6D);defaultSetting(change,"release_range",3.4D);
            if(!change.currentNetwork)plan.migratedNetworkPolicies++;
            if(change.legacyTickbase){Object count=change.values.get(change.module.getValueByName("charge_ticks"));if(count!=null)defaultSetting(change,"min_charge",count);plan.legacyNetwork++;}
        }
    }

    private JsonElement readElement(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IOException("Config nesting is too deep.");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IOException("Duplicate JSON member: " + name);
                    object.add(name, readElement(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readElement(reader, depth + 1));
                reader.endArray();
                return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IOException("Expected JSON value.");
        }
    }

    private void parseSettings(Plan plan, Change change, JsonObject settings) {
        for (Map.Entry<String, JsonElement> entry : settings.entrySet()) parseSetting(plan, change, entry.getKey(), entry.getValue());
    }

    private void parseSetting(Plan plan, Change change, String id, JsonElement json) {
        if ("bedbreaker".equals(change.module.getId()) && retiredBedBreakerSetting(id)) {
            JsonElement raw = json != null && json.isJsonObject() ? json.getAsJsonObject().get("value") : json;
            validateRetiredBedBreakerSetting(id, raw);
            if (!change.retiredBedBreaker.add(normalizedName(id)))
                throw new IllegalArgumentException("Duplicate setting: " + id);
            change.migratedBedBreaker = true;
            return;
        }
        boolean nScaffoldAlias = false;
        if ("nscaffold".equals(change.module.getId())) {
            JsonElement raw = json != null && json.isJsonObject() ? json.getAsJsonObject().get("value") : json;
            if (retiredNScaffoldSetting(id)) {
                validateRetiredNScaffoldSetting(id, raw);
                change.migratedNScaffold = true;
                return;
            }
            String replacement = nScaffoldAlias(id);
            if (replacement != null) {
                if ("rotations".equals(replacement) && raw != null && raw.isJsonPrimitive()
                        && raw.getAsJsonPrimitive().isString() && "Standard".equalsIgnoreCase(raw.getAsString()))
                    raw = new JsonPrimitive("Normal");
                id = replacement;
                json = raw;
                nScaffoldAlias = true;
                change.migratedNScaffold = true;
            }
        }
        if (change.module.getId().equals("velocity") && normalizedName(id).equals("mode")) {
            JsonElement raw = json != null && json.isJsonObject() ? json.getAsJsonObject().get("value") : json;
            if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()) {
                String old = raw.getAsString();
                for (String removed : new String[]{"Jump", "Packet", "Verus", "Ground Grim", "Minemen", "Intave", "Intave2"})
                    if (removed.equalsIgnoreCase(old)) { json = new JsonPrimitive("Legit"); break; }
            }
        }
        if(change.module.getId().equals("backtrack") && (normalizedName(id).equals("delayping") || normalizedName(id).equals("delayvelocity"))) {bool(json,"backtrack."+id);return;}
        if(change.module.getId().equals("scaffold") && oldScaffoldName(id)) {
            change.legacyScaffold=true;
            if(id.replace("_","").replace(" ","").equalsIgnoreCase("Raytrace")){bool(json,"scaffold.raytrace");return;}
        }
        if(change.module.getId().equals("backtrack") && normalizedName(id).equals("targetfallback")) {
            JsonElement raw=json!=null && json.isJsonObject()?json.getAsJsonObject().get("value"):json;
            if(raw==null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Expected choice: backtrack.target_fallback");
            String old=raw.getAsString();
            if(!old.equalsIgnoreCase("Nearest") && !old.equalsIgnoreCase("Cursor"))throw new IllegalArgumentException("Unknown choice: backtrack.target_fallback");
            json=new JsonPrimitive(old.equalsIgnoreCase("Nearest")?"Combat / Nearest":"Combat / Cursor");
        }
        Value<?> value = change.module.getValueByName(id);
        if (value == null) { plan.unknown++; return; }
        Map<Value<?>,Object> destination = nScaffoldAlias ? change.nScaffoldAliases : change.values;
        if (destination.containsKey(value)) throw new IllegalArgumentException("Duplicate setting: " + id);
        String label = change.module.getId() + "." + value.getId();
        Object decoded;
        if (value instanceof ColorValue) {
            ColorValue color = (ColorValue) value;
            Color base = color.getValue();
            boolean rainbow = false;
            float saturation = 1;
            int speed = 15;
            if (json != null && json.isJsonPrimitive()) {
                base = new Color(integer(json, label), true);
            } else {
                JsonObject data = object(json, label);
                if (data.has("rgb")) base = new Color(integer(data.get("rgb"), label + ".rgb"), true);
                else if (!data.has("speed") || !data.has("saturation")) throw new IllegalArgumentException("Missing color RGB: " + label);
                rainbow = data.has("rainbow") ? bool(data.get("rainbow"), label + ".rainbow") : !data.has("rgb");
                if (data.has("saturation")) saturation = (float) number(data.get("saturation"), label + ".saturation");
                if (data.has("speed")) speed = integer(data.get("speed"), label + ".speed");
                if (saturation < 0 || saturation > 1 || speed < 1) throw new IllegalArgumentException("Invalid rainbow parameters: " + label);
            }
            final Color loaded = base;
            final boolean animated = rainbow;
            final float sat = saturation;
            final int rate = speed;
            decoded = (Runnable) () -> color.setConfigState(loaded, animated, sat, rate);
        } else if (value instanceof CheckBoxValue || value.getDefaultValue() instanceof Boolean) {
            decoded = bool(json, label);
        } else if (value instanceof SliderValue) {
            SliderValue slider = (SliderValue) value;
            double number = number(json, label);
            double minimum = slider.getMin(), maximum = slider.getMax();
            if (slider instanceof DemiseSliderValue) {
                // The original stores floats. A saved 0.01F widens to a Double
                // slightly below 0.01D; validate in the original numeric domain
                // so a module's own legal boundary values remain loadable.
                number = (double) (float) number;
                minimum = (double) (float) minimum;
                maximum = (double) (float) maximum;
            }
            if(number==Math.rint(number) && ((change.legacyBacktrack && value.getId().equals("delay") && number>500 && number<=2000)
                    || (change.legacyLag && value.getId().equals("window_ticks") && number>10 && number<=20))) {
                number=slider.getMax();plan.clampedLegacyDelays++;
            }
            if(change.legacyRotation && value.getId().equals("octaves") && number>=.1 && number<=10 && number!=Math.rint(number)) {
                number=Math.max(1,(int)number);plan.migratedOctaves++;
            }
            if (number < minimum || number > maximum || (slider.isInteger() && number != Math.rint(number))) throw new IllegalArgumentException("Number out of range: " + label);
            decoded = number;
        } else if (value instanceof MoveFixValue) {
            JsonElement legacy=json!=null && json.isJsonObject()?json.getAsJsonObject().get("value"):json;
            if(change.legacyScaffold && legacy!=null && legacy.isJsonPrimitive() && legacy.getAsJsonPrimitive().isBoolean())decoded=legacy.getAsBoolean()?"Einfach":"Aus";
            else decoded = ((MoveFixValue) value).decodeConfig(json);
        } else if (value instanceof me.youngjibbit.theme.ThemeSelectionValue) {
            if (json != null && json.isJsonObject()) json=json.getAsJsonObject().get("value");
            if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected theme reference: " + label);
            decoded=me.youngjibbit.theme.ThemeCatalog.canonicalReference(json.getAsString());
        } else if (value instanceof ComboValue) {
            if (json != null && json.isJsonObject()) json = json.getAsJsonObject().get("value");
            if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected choice: " + label);
            String requested=json.getAsString();
            if("targethud".equals(change.module.getId()) && "mode".equals(value.getId())) {
                String migrated=me.youngjibbit.module.impl.render.TargetHUD.migrateMode(requested);
                if(!requested.equals(migrated))plan.migratedTargetHudModes++;
                requested=migrated;
            }
            if("client_settings".equals(change.module.getId()) && "gradient_method".equals(value.getId())) {
                if("Flame".equalsIgnoreCase(requested)||"Neon".equalsIgnoreCase(requested)||"Lines".equalsIgnoreCase(requested)) {
                    change.legacyUiGlowMode="Flame".equalsIgnoreCase(requested)?"Flame":"Neon".equalsIgnoreCase(requested)?"Neon":"Standard";
                    requested="Flow";
                    plan.migratedModes++;
                }
            }
            if(change.module.getId().equals("aimassist") && value.getId().equals("rotations") && requested.equalsIgnoreCase("Smooth")) {requested="Legit";plan.migratedModes++;}
            decoded = null;
            for (String option : ((ComboValue) value).getOptions()) if (option.equalsIgnoreCase(requested)) decoded = option;
            if (decoded == null) throw new IllegalArgumentException("Unknown choice: " + label);
        } else if (value.getDefaultValue() instanceof String) {
            if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected text: " + label);
            decoded = json.getAsString();
        } else if (value.getDefaultValue() instanceof Integer) {
            decoded = integer(json, label);
        } else if (value.getDefaultValue() instanceof Double) {
            decoded = number(json, label);
        } else if (value.getDefaultValue() instanceof Long) {
            number(json, label);
            decoded = new BigDecimal(json.getAsString()).longValueExact();
        } else if (value.getDefaultValue() instanceof Float) {
            float numeric = (float) number(json, label);
            if (!Float.isFinite(numeric)) throw new IllegalArgumentException("Number out of range: " + label);
            decoded = numeric;
        } else {
            throw new IllegalArgumentException("Unsupported setting type: " + label);
        }
        destination.put(value, decoded);
    }

    private void apply(Plan plan) {
        apply(plan, false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void apply(Plan plan, boolean settingsOnly) {
        if (applying) throw new IllegalStateException("Recursive config load is not allowed.");
        applying = true;
        try {
            ModuleToggleJournal.beginConfigApplication();
            boolean canActivate = ready.getAsBoolean();
            for (Change change : plan.changes) {
                change.desired = change.enabled != null ? change.enabled : pendingActivation.getOrDefault(change.module, change.module.isToggled());
                if (canActivate && change.module.isToggled()) change.module.setToggled(false);
                if (settingsOnly) change.module.beginSettingsUpdate();
                else change.module.beginConfigUpdate();
            }
            for (Change change : plan.changes) {
                if (change.key != null) change.module.setKey(change.key);
                for (Map.Entry<Value<?>, Object> entry : change.values.entrySet()) {
                    if (entry.getValue() instanceof Runnable) ((Runnable) entry.getValue()).run();
                    else ((Value) entry.getKey()).setValue(entry.getValue());
                }
            }
            // All modules' data is in place before callbacks or activation hooks.
            for (Change change : plan.changes) change.module.endConfigUpdate();
            for (Change change : plan.changes) {
                if (canActivate) {
                    pendingActivation.remove(change.module);
                    change.module.setToggled(change.desired);
                } else pendingActivation.put(change.module, change.desired);
            }
        } catch (RuntimeException e) {
            automaticReadOnly = true;
            throw new IllegalStateException("Application failed; state may be partially applied. Automatic saving paused: " + e.getMessage(), e);
        } finally {
            try {
                for (Change change : plan.changes) change.module.cancelConfigUpdate();
                applying = false;
            } finally {
                ModuleToggleJournal.endConfigApplication();
            }
        }
    }

    private Module findModule(String id) {
        String normalized = id.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        if (normalized.equals("aimassist2") || normalized.equals("testaimassist")) normalized="aimassist";
        for (Module module : modules) {
            if (module.getId().equalsIgnoreCase(id) || module.getName().replace(" ", "").equalsIgnoreCase(normalized)) return module;
        }
        return null;
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("Expected object: " + label);
        return element.getAsJsonObject();
    }
    private static boolean bool(JsonElement element, String label) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean: " + label);
        return element.getAsBoolean();
    }
    private static double number(JsonElement element, String label) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected number: " + label);
        double number = element.getAsDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Non-finite number: " + label);
        return number;
    }
    private static int integer(JsonElement element, String label) {
        number(element, label);
        try { return new BigDecimal(element.getAsString()).intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Expected 32-bit integer: " + label); }
    }
    private boolean fail(String message, Exception e) {
        lastError = message + ": " + e.getMessage();
        NotificationManager.error("Configuration failed","The action did not complete. See the existing config diagnostics.");
        return false;
    }
    private static void report(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[Config] " + message));
        else System.err.println("[Config] " + message);
    }
    private static final class Plan {
        final JsonObject root;
        final List<Change> changes = new ArrayList<>();
        int unknown, migratedRotations, migratedOctaves, migratedTiming, migratedModes, migratedTargetHudModes, legacyAssist, migratedDefaults, legacyScaffold, migratedNScaffold, migratedBedBreaker, legacyNetwork, clampedLegacyDelays, migratedNetworkPolicies;
        Plan(JsonObject root) { this.root = root; }
    }
    private static final class Change {
        final Module module;
        final Map<Value<?>, Object> values = new LinkedHashMap<>();
        final Map<Value<?>, Object> nScaffoldAliases = new LinkedHashMap<>();
        final Set<String> retiredBedBreaker = new HashSet<>();
        Boolean enabled;
        boolean legacyRotation,legacyScaffold,legacyBacktrack,legacyLag,legacyTickbase,currentNetwork,migratedNScaffold,migratedBedBreaker;
        String legacyUiGlowMode;
        Integer key;
        boolean desired;
        Change(Module module) { this.module = module; }
    }
}
