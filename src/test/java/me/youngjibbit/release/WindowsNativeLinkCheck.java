package me.youngjibbit.release;

import org.junit.Test;
import java.nio.file.*;
import org.lwjgl.Sys;
import static org.junit.Assert.*;

/** Explicit isolated JDK8 process only; no Display, audio device or client is created. */
public class WindowsNativeLinkCheck {
    @Test public void lwjglJniAndWindows64LibrariesLink() throws Exception {
        assertEquals("Windows", System.getProperty("os.name").split(" ")[0]);
        assertEquals("64", System.getProperty("sun.arch.data.model"));
        assertTrue("LWJGL timer JNI", Sys.getTimerResolution() > 0);
        Path natives = Paths.get(System.getProperty("org.lwjgl.librarypath"));
        // TwitchStream preloads these four dependencies before initializing its SDK.
        for (String name : new String[]{"OpenAL64.dll", "jinput-dx8_64.dll", "jinput-raw_64.dll",
                "avutil-ttv-51.dll", "swresample-ttv-0.dll", "libmp3lame-ttv.dll", "libmfxsw64.dll", "twitchsdk.dll"}) {
            Path dll = natives.resolve(name);
            assertTrue(name, Files.isRegularFile(dll));
            System.load(dll.toAbsolutePath().toString());
        }
    }
}
