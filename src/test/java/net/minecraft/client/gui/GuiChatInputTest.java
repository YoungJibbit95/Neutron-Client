package net.minecraft.client.gui;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class GuiChatInputTest {
    @Test public void nativeInputAndDrawingWhenRequested() throws Exception {
        org.junit.Assume.assumeTrue(Boolean.getBoolean("pulsar.chat.native"));
        Path output = Files.createTempFile("pulsar-chat-input-", ".log");
        Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Duser.home=" + System.getProperty("user.home"), "-Dorg.lwjgl.librarypath=" + System.getProperty("org.lwjgl.librarypath"),
                "-cp", System.getProperty("java.class.path"), GuiChatInputNativeCheck.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) { child.destroy(); fail("Chat native check timed out: " + output); }
        String result = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        System.out.print(result); assertEquals(result, 0, child.exitValue());
    }
}
