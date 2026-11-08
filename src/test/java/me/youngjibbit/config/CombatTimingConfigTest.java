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
import static org.junit.Assert.*;

public class CombatTimingConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void extendedCooldownsRoundTripWithoutChangingReferenceTimingValues() throws Exception {
        Backtrack back = new Backtrack(); LagRange lag = new LagRange(); Tickbase tick = new Tickbase();
        Path directory = temporary.newFolder().toPath();
        ConfigManager configs = new ConfigManager(directory, Arrays.<Module>asList(back,lag,tick), () -> false, message -> {});
        back.getNumberSetting("Min MS").setValue(21D);back.getNumberSetting("Max MS").setValue(91D);
        back.getNumberSetting("Cooldown Ticks").setValue(400D);
        lag.getModeSetting("Delay Mode").setValue("Ticks");lag.getNumberSetting("Window Ticks").setValue(2D);
        lag.getNumberSetting("Release Cooldown MS").setValue(20000D);
        tick.getNumberSetting("Charge Ticks").setValue(1D);tick.getNumberSetting("Release Speed").setValue(1.2D);
        tick.getNumberSetting("Release Cooldown MS").setValue(20000D);
        assertTrue(configs.saveConfig("LongCooldowns"));
        back.getNumberSetting("Cooldown Ticks").setValue(0D);
        lag.getNumberSetting("Release Cooldown MS").setValue(0D);
        tick.getNumberSetting("Release Cooldown MS").setValue(0D);
        assertTrue(configs.loadConfig("LongCooldowns"));
        assertEquals(400,back.getNumberSetting("Cooldown Ticks").getValue(),0);
        assertEquals(20000,lag.getNumberSetting("Release Cooldown MS").getValue(),0);
        assertEquals(20000,tick.getNumberSetting("Release Cooldown MS").getValue(),0);
        assertEquals(21,back.getNumberSetting("Min MS").getValue(),0);
        assertEquals(91,back.getNumberSetting("Max MS").getValue(),0);
        assertEquals(2,lag.getNumberSetting("Window Ticks").getValue(),0);
        assertEquals(1,tick.getNumberSetting("Charge Ticks").getValue(),0);
        assertEquals(1.2,tick.getNumberSetting("Release Speed").getValue(),0);
        Files.write(directory.resolve("OlderLag.json"),"{\"version\":2,\"modules\":{\"lagrange\":{\"settings\":{}}}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(configs.loadConfig("OlderLag"));
        assertEquals("old configs use the prior 500 ms default",500,lag.getNumberSetting("Release Cooldown MS").getValue(),0);
    }
}
