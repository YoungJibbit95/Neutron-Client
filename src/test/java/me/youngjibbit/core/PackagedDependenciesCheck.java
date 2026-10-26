package me.youngjibbit.core;

import java.util.Collections;

/** Run with this test class plus ONE packaged client JAR; does not start Minecraft. */
public final class PackagedDependenciesCheck {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = PackagedDependenciesCheck.class.getClassLoader();
        require(Collections.list(loader.getResources("com/sun/jna/Native.class")).size() == 1, "one JNA Native");
        require(Collections.list(loader.getResources("com/sun/jna/platform/win32/Kernel32.class")).size() == 1, "one JNA platform");
        java.lang.reflect.Field version = com.sun.jna.Native.class.getField("VERSION");
        version.setAccessible(true); // Declared on JNA's package-private Version interface.
        require("5.13.0".equals(version.get(null)), "OSHI-compatible JNA");
        oshi.hardware.CentralProcessor processor = new oshi.SystemInfo().getHardware().getProcessor();
        require(processor.getLogicalProcessorCount() > 0, "native CPU discovery");
        require(!processor.getProcessorIdentifier().getName().trim().isEmpty(), "CPU identifier");
        require(Collections.list(loader.getResources("org/slf4j/impl/StaticLoggerBinder.class")).size() == 1, "one SLF4J binding");
        require(org.slf4j.LoggerFactory.getILoggerFactory().getClass().getName()
                .equals("org.apache.logging.slf4j.Log4jLoggerFactory"), "SLF4J connected to Log4j");
        require(org.apache.logging.log4j.LogManager.getContext(false)
                instanceof org.apache.logging.log4j.core.LoggerContext, "Log4j Core backend");
        org.apache.logging.log4j.core.layout.PatternLayout layout = org.apache.logging.log4j.core.layout.PatternLayout
                .newBuilder().withPattern("%level|%msg").build();
        org.apache.logging.log4j.core.LogEvent event = org.apache.logging.log4j.core.impl.Log4jLogEvent.newBuilder()
                .setLevel(org.apache.logging.log4j.Level.INFO)
                .setMessage(new org.apache.logging.log4j.message.SimpleMessage("packaged probe")).build();
        require("INFO|packaged probe".equals(layout.toSerializable(event)), "reflective Log4j pattern plugins");
        System.out.println("PASS: packaged OSHI/JNA and SLF4J/Log4j runtime; not a client launch.");
    }

    private static void require(boolean condition, String contract) {
        if (!condition) throw new AssertionError(contract);
    }
}
