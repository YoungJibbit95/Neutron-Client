package me.youngjibbit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.stream.DisabledStream;
import net.minecraft.client.stream.IStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class StartupServicesTest {
    @Test
    public void bootstrapUsesDisabledStreamingWithoutNativeSdk() throws Exception {
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Minecraft client = (Minecraft) ((Unsafe) singleton.get(null)).allocateInstance(Minecraft.class);
        Method init = Minecraft.class.getDeclaredMethod("initStream");
        init.setAccessible(true);
        init.invoke(client);
        IStream stream = client.getTwitchStream();
        assertTrue(stream instanceof DisabledStream);
        assertFalse(stream.isReadyToBroadcast());
        assertFalse(stream.isBroadcasting());
        stream.func_152935_j();
        stream.func_152922_k();
        stream.muteMicrophone(true);
        stream.shutdownStream();
    }

    @Test
    public void slf4jUsesOneBindingAndDeliversExceptionsToLog4j() throws Exception {
        assertEquals(1, Collections.list(getClass().getClassLoader()
                .getResources("org/slf4j/impl/StaticLoggerBinder.class")).size());
        assertEquals("org.apache.logging.slf4j.Log4jLoggerFactory", LoggerFactory.getILoggerFactory().getClass().getName());
        final List<LogEvent> captured = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("startup-probe", null, null, false) {
            @Override public void append(LogEvent event) { captured.add(event.toImmutable()); }
        };
        appender.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        String name = "pulsar.test.startup.binding";
        LoggerConfig config = new LoggerConfig(name, Level.ALL, false);
        config.addAppender(appender, Level.ALL, null);
        context.getConfiguration().addLogger(name, config);
        context.updateLoggers();
        try {
            RuntimeException failure = new RuntimeException("synthetic failure");
            LoggerFactory.getLogger(name).error("probe {}", "message", failure);
            assertEquals(1, captured.size());
            assertEquals("probe message", captured.get(0).getMessage().getFormattedMessage());
            assertSame(failure, captured.get(0).getThrown());
        } finally {
            context.getConfiguration().removeLogger(name);
            context.updateLoggers();
            appender.stop();
        }
    }
}
