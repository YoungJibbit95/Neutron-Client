package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.ghost.JumpReset;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class CombatRecodeConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void oldVelocityModesMigrateWithoutRejectingTheRemainingConfiguration() throws Exception {
        Velocity velocity = new Velocity(); JumpReset reset = new JumpReset();
        Path dir = temporary.newFolder().toPath();
        ConfigManager config = new ConfigManager(dir, Arrays.<Module>asList(velocity, reset), () -> false, message -> { });
        for (String mode : new String[]{"Jump", "Packet", "Verus", "Ground Grim", "Minemen", "Intave", "Intave2"}) {
            Files.write(dir.resolve("Legacy.json"), ("{\"Velocity\":{\"Mode\":\"" + mode
                    + "\",\"X\":0,\"Y\":100},\"JumpReset\":{\"Jump Chance\":60}}").getBytes(StandardCharsets.UTF_8));
            assertTrue(config.getLastError(), config.loadConfig("Legacy"));
            assertEquals("Legit", velocity.mode.getValue());
            assertEquals(60, reset.jumpChance.getValue(), 0);
        }
        velocity.reduceTicks.setValue(3D); velocity.jumpReset.setValue(false);
        assertTrue(config.saveConfig("Current"));
        velocity.reduceTicks.setValue(1D); velocity.jumpReset.setValue(true);
        assertTrue(config.loadConfig("Current"));
        assertEquals(3, velocity.reduceTicks.getValue(), 0); assertFalse(velocity.jumpReset.getValue());
    }
}
