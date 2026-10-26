package me.youngjibbit.core;

import com.sun.jna.Native;
import org.junit.Test;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.util.Collections;

import static org.junit.Assert.*;

/** Exercise the native OSHI path that previously failed with AbstractMethodError. */
public class NativeDependencyTest {
    @Test
    public void oneJnaImplementationSupportsCpuDiscovery() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertEquals("duplicate embedded JNA Native", 1,
                Collections.list(loader.getResources("com/sun/jna/Native.class")).size());
        assertEquals("duplicate JNA platform", 1,
                Collections.list(loader.getResources("com/sun/jna/platform/win32/Kernel32.class")).size());
        java.lang.reflect.Field version = Native.class.getField("VERSION");
        version.setAccessible(true); // Declared on JNA's package-private Version interface.
        assertEquals("5.13.0", version.get(null));
        CentralProcessor processor = new SystemInfo().getHardware().getProcessor();
        assertTrue(processor.getLogicalProcessorCount() > 0);
        assertFalse(processor.getProcessorIdentifier().getName().trim().isEmpty());
    }
}
