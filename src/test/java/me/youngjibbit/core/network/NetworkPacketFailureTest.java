package me.youngjibbit.core.network;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class NetworkPacketFailureTest {
    @Test
    public void failedClientPacketLogsOriginalStackTraceAndStillAbortsTheConnection() throws Exception {
        List<LogEvent> events = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("packet-failure-probe", null, null, false) {
            @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        String loggerName = NetworkManager.class.getName();
        LoggerConfig previous = context.getConfiguration().getLoggers().get(loggerName);
        LoggerConfig capture = new LoggerConfig(loggerName, Level.ERROR, false);
        capture.addAppender(appender, Level.ERROR, null);
        context.getConfiguration().removeLogger(loggerName);
        context.getConfiguration().addLogger(loggerName, capture);
        context.updateLoggers();

        PacketFlowTest.MainThread main = new PacketFlowTest.MainThread();
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network);
        NullPointerException failure = new NullPointerException("synthetic join handler failure");
        AtomicInteger applications = new AtomicInteger();
        try {
            network.configureClientPackets(main);
            network.setNetHandler(PacketFlowTest.handler(args -> {
                applications.incrementAndGet();
                throw failure;
            }));
            Thread incoming = new Thread(() -> {
                channel.writeInbound(new S00PacketKeepAlive(1));
                channel.writeInbound(new S00PacketKeepAlive(2));
            });
            incoming.start();
            incoming.join();
            main.drain();

            assertEquals(1, applications.get());
            assertFalse(channel.isOpen());
            assertFalse(network.getClientPackets().isActive());
            assertTrue(network.getExitMessage().getUnformattedText().contains("NullPointerException"));
            LogEvent diagnostic = events.stream()
                    .filter(event -> event.getLevel() == Level.ERROR
                            && event.getMessage().getFormattedMessage().contains("Client packet processing failed"))
                    .findFirst().orElse(null);
            assertNotNull("The disconnect must retain the stack trace in the client log", diagnostic);
            assertSame(failure, diagnostic.getThrown());
            assertTrue(network.getExitMessage().getUnformattedText().contains("logs/latest.log"));
        } finally {
            channel.finish();
            context.getConfiguration().removeLogger(loggerName);
            if (previous != null) context.getConfiguration().addLogger(loggerName, previous);
            context.updateLoggers();
            appender.stop();
        }
    }
}
