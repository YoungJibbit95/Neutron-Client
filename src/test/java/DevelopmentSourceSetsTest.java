import me.youngjibbit.rendercheck.PbufferTestContext;
import net.minecraft.client.main.Main;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

/** Compile/link checks only: never opens a game, display, native context or connection. */
public class DevelopmentSourceSetsTest {
    private static URL outputOf(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }

    @Test public void clientEntrypointBelongsToProductionOutputNotTestOutput() {
        assertEquals("Start must run with the same production classpath as Minecraft",
                outputOf(Main.class), outputOf(Start.class));
        assertNotEquals("Do not put the client entrypoint back in src/test/java",
                outputOf(DevelopmentSourceSetsTest.class), outputOf(Start.class));
    }

    @Test public void nativeFixtureIsResolvableFromTestOutputWithoutOpeningAContext() throws Exception {
        assertEquals(outputOf(DevelopmentSourceSetsTest.class), outputOf(PbufferTestContext.class));
        assertTrue(AutoCloseable.class.isAssignableFrom(PbufferTestContext.class));
        assertNotNull(PbufferTestContext.class.getConstructor());
    }

    @Test public void movingEntrypointPreservesArgumentComposition() {
        String[] defaults = {"--version", "MavenMCP"};
        String[] arguments = {"--username", "NeutronTest"};
        assertArrayEquals(new String[]{"--version", "MavenMCP", "--username", "NeutronTest"},
                Start.concat(defaults, arguments));
        assertArrayEquals(new String[]{"--version", "MavenMCP"}, defaults);
        assertArrayEquals(new String[]{"--username", "NeutronTest"}, arguments);
    }
}
