package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class OnlyAuraConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void legacyProfilesEnableTheGateAndExplicitFalseRoundTrips() throws Exception {
        List<Module> modules = Arrays.asList(new Backtrack(), new LagRange(), new Tickbase());
        Path directory = temporary.newFolder().toPath();
        ConfigManager configs = new ConfigManager(directory, modules, () -> false, message -> { });
        for (Module module : modules) {
            assertTrue(module.getBooleanSetting("Only Aura").getValue());
            module.getBooleanSetting("Only Aura").setValue(false);
        }
        assertTrue(configs.saveConfig("Independent"));
        Files.write(directory.resolve("Legacy.json"),
                "{\"BackTrack\":{},\"LagRange\":{},\"Tickbase\":{}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(configs.getLastError(), configs.loadConfig("Legacy"));
        for (Module module : modules) assertTrue(module.getId(), module.getBooleanSetting("only_aura").getValue());
        assertTrue(configs.getLastError(), configs.loadConfig("Independent"));
        for (Module module : modules) assertFalse(module.getId(), module.getBooleanSetting("only_aura").getValue());
        assertTrue(configs.getLastError(), configs.loadConfig("Legacy"));
        for (Module module : modules) assertTrue(module.getId(), module.getBooleanSetting("only_aura").getValue());
    }
}
