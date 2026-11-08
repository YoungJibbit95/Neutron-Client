package me.youngjibbit.module.impl.render;

import me.youngjibbit.config.ConfigManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.ThemeMode;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Real ConfigManager; prior display controls keep their saved names and positions. */
public class Wave07SessionDiagnosticsConfigTest {
    @Rule public TemporaryFolder home=new TemporaryFolder();
    @After public void restore(){ThemeMode.select(ThemeMode.LEGACY);}

    @Test public void sessionAndDiagnosticSettingsRoundTripWithoutPresentationState()throws Exception{
        Path folder=home.newFolder().toPath();
        SessionInfo session=new SessionInfo();
        Diagnostics diag=new Diagnostics();
        ConfigManager writer=new ConfigManager(folder,Arrays.asList(
                new ClientSettings(),session,diag),()->false,ignored->{});
        writer.initialize(Collections.emptyList());
        session.getModeSetting("Mode").setValue("Tenacity");
        session.getNumberSetting("Position X").setValue(153d);
        session.getNumberSetting("Position Y").setValue(79d);
        diag.getModeSetting("Display Mode").setValue("Simple");
        diag.getBooleanSetting("Performance Widget").setValue(false);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        assertTrue(writer.saveDefaultConfig());
        writer.shutdown();

        SessionInfo restoredSession=new SessionInfo();
        Diagnostics restoredDiag=new Diagnostics();
        ConfigManager reader=new ConfigManager(folder,Arrays.asList(
                new ClientSettings(),restoredSession,restoredDiag),()->false,ignored->{});
        reader.initialize(Collections.emptyList());
        assertEquals("Tenacity",restoredSession.getModeSetting("Mode").getValue());
        assertEquals(153d,restoredSession.getNumberSetting("Position X").getValue(),0);
        assertEquals(79d,restoredSession.getNumberSetting("Position Y").getValue(),0);
        assertEquals("Simple",restoredDiag.getModeSetting("Display Mode").getValue());
        assertFalse(restoredDiag.getBooleanSetting("Performance Widget").getValue());
        reader.shutdown();
    }
}
