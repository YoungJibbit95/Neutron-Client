package net.minecraft.client.gui;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.render.ArrayListSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.*;
import org.lwjgl.input.Keyboard;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.entity.EntityPlayerSP;
import me.youngjibbit.Pulsar;
import me.youngjibbit.command.CommandManager;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Disposable offscreen GUI input/draw consumer; synthetic modifier keys, no game/network startup. */
public final class GuiChatInputNativeCheck {
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void verifyFormatting(FontRenderer font) {
        java.nio.FloatBuffer color = org.lwjgl.BufferUtils.createFloatBuffer(16);
        for (String text : new String[]{"\u00a7cX", "\u00a7CX", "\u0130\u00a7cX", "\u0130\u00a7CX"}) {
            font.drawString(text, 0, 0, -1);
            color.clear(); glGetFloat(GL_CURRENT_COLOR, color);
            require(Math.abs(color.get(0) - 1) < .001f && Math.abs(color.get(1) - 85 / 255f) < .001f
                    && Math.abs(color.get(2) - 85 / 255f) < .001f, "Formatting code lost its color after Unicode prefix: " + text);
        }
    }
    private static void reportFontAllocations(FontRenderer font) {
        java.lang.management.ThreadMXBean generic = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(generic instanceof com.sun.management.ThreadMXBean)) return;
        com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean) generic;
        if (!allocation.isThreadAllocatedMemorySupported()) return;
        allocation.setThreadAllocatedMemoryEnabled(true);
        for (int tokens : new int[]{32, 64, 128}) {
            String text = String.join("", Collections.nCopies(tokens, "\u00a7aABCD"));
            for (int i = 0; i < 100; i++) font.drawStringWithShadow(text, 0, 0, -1);
            long before = allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
            for (int i = 0; i < 100; i++) font.drawStringWithShadow(text, 0, 0, -1);
            long bytes = allocation.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
            System.out.println("AUDIT FontRenderer production drawStringWithShadow: chars=" + text.length() + " formatTokens=" + tokens + " allocatedBytesPerDraw=" + bytes / 100);
        }
    }
    private static void draw(Minecraft mc, GuiChat chat) {
        mc.getFramebuffer().bindFramebuffer(true);
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0, chat.width, chat.height, 0, -1000, 1000);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity(); glClearColor(.08f, .15f, .2f, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GlStateManager.pushMatrix(); GlStateManager.translate(0, chat.height - 48, 0);
        mc.ingameGUI.getChatGUI().drawChat(0); GlStateManager.popMatrix();
        chat.drawScreen(-1, -1, 0);
        require(glGetError() == GL_NO_ERROR, "Chat input drawing generated a GL error");
    }
    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("pulsar-chat-native-");
        try (PbufferTestContext context = new PbufferTestContext()) {
            Minecraft mc = new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(
                    new Session("ChatFixture", "00000000000000000000000000000000", "0", "legacy"),
                    new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(1280, 960, false, false),
                    new GameConfiguration.FolderInformation(data.toFile(), data.resolve("packs").toFile(), data.resolve("assets").toFile(), null),
                    new GameConfiguration.GameInformation(false, "chat input regression"), new GameConfiguration.ServerInformation(null, 0)));
            context.initialize(mc, data.toFile());
            IMetadataSerializer metadata = new IMetadataSerializer();
            metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
            metadata.registerMetadataSectionType(new AnimationMetadataSectionSerializer(), AnimationMetadataSection.class);
            metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(), PackMetadataSection.class);
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(metadata);
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(Minecraft.class, mc, "mcResourceManager", resources);
            set(Minecraft.class, mc, "mcLanguageManager", new LanguageManager(metadata, "en_US"));
            TextureManager textures = new TextureManager(resources); set(Minecraft.class, mc, "renderEngine", textures);
            set(Minecraft.class, mc, "framebufferMc", new Framebuffer(1280, 960, true));
            mc.fontRendererObj = new FontRenderer(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), textures, false);
            mc.fontRendererObj.onResourceManagerReload(resources); mc.ingameGUI = new GuiIngame(mc);
            // Native key events are not available in a Pbuffer; only modifier table state is synthetic.
            Field keyboardBuffer = Keyboard.class.getDeclaredField("keyDownBuffer"); keyboardBuffer.setAccessible(true);
            java.nio.ByteBuffer keys = (java.nio.ByteBuffer) keyboardBuffer.get(null);
            for (int key = 0; key < keys.capacity(); key++) keys.put(key, (byte) 0);
            set(Keyboard.class, null, "created", true);
            try {
                GuiChat chat = new GuiChat(); mc.currentScreen = chat; chat.setWorldAndResolution(mc, 640, 480);
                draw(mc, chat);
                for (char character : "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 /.,!? äöü".toCharArray()) {
                    chat.keyTyped(character, 0); chat.updateScreen(); draw(mc, chat);
                }
                require(chat.inputField.getText().contains("0123456789"), "Typed characters did not reach the real text field");
                chat.keyTyped('\0', Keyboard.KEY_LEFT); chat.keyTyped('\0', Keyboard.KEY_BACK); draw(mc, chat);
                chat.inputField.setSelectionPos(0); draw(mc, chat); chat.inputField.writeText("replacement"); draw(mc, chat);
                mc.ingameGUI.getChatGUI().addToSentMessages("synthetic history"); chat.getSentHistory(1); chat.getSentHistory(-1); draw(mc, chat);
                mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("synthetic received line")); draw(mc, chat);
                set(GuiChat.class, chat, "waitingOnAutocomplete", true); chat.inputField.setText("Al");
                chat.onAutocompleteResponse(new String[]{"Alpha", "Alpine"}); draw(mc, chat);
                keys.put(Keyboard.KEY_LCONTROL, (byte) 1);
                chat.keyTyped('\0', Keyboard.KEY_A); draw(mc, chat);
                require(chat.inputField.getSelectedText().equals(chat.inputField.getText()), "Ctrl+A did not select chat text");
                keys.put(Keyboard.KEY_LCONTROL, (byte) 0);
                chat.keyTyped('R', 0); require(chat.inputField.getText().equals("R"), "Typing did not replace selection");
                chat.inputField.setText("");
                for (char character : "\u0000\u0001\n\t\u00a7\u007f\u4e2d\u6587\u03a9\u0416\ud83d\ude00".toCharArray()) chat.keyTyped(character, 0);
                draw(mc, chat);
                require(!chat.inputField.getText().contains("\u00a7") && !chat.inputField.getText().contains("\n"), "Control/format characters entered chat");
                chat.inputField.setText("");
                for (int i = 0; i < 120; i++) chat.keyTyped('W', 0);
                require(chat.inputField.getText().length() == 100, "Chat limit not enforced");
                chat.width = 320; chat.initGui(); chat.inputField.setText(String.join("", Collections.nCopies(100, "W")));
                draw(mc, chat); chat.keyTyped('\0', Keyboard.KEY_HOME); draw(mc, chat);
                keys.put(Keyboard.KEY_LSHIFT, (byte) 1); chat.keyTyped('\0', Keyboard.KEY_END); draw(mc, chat);
                keys.put(Keyboard.KEY_LSHIFT, (byte) 0);
                for (int i = 0; i < 120; i++) mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("received " + i));
                mc.ingameGUI.getChatGUI().scroll(80); draw(mc, chat); mc.ingameGUI.getChatGUI().refreshChat(); draw(mc, chat);
                // Only the transport is synthetic: use the production GuiScreen -> EntityPlayerSP -> command/packet path.
                Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
                EntityPlayerSP player = (EntityPlayerSP) ((sun.misc.Unsafe) uf.get(null)).allocateInstance(EntityPlayerSP.class);
                NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
                EmbeddedChannel channel = new EmbeddedChannel(network);
                try {
                    NetHandlerPlayClient play = new NetHandlerPlayClient(mc, null, network, mc.getSession().getProfile()); network.setNetHandler(play);
                    set(EntityPlayerSP.class, player, "mc", mc); set(EntityPlayerSP.class, player, "sendQueue", play); mc.thePlayer = player;
                    set(Pulsar.class, Pulsar.INSTANCE, "cm", new CommandManager());
                    chat.sendChatMessage("ordinary message");
                    Object sent = channel.readOutbound(); require(sent instanceof C01PacketChatMessage, "Chat send did not reach actual packet path");
                    require(((C01PacketChatMessage) sent).getMessage().equals("ordinary message"), "Chat packet changed text");
                    for (String command : new String[]{".", ".help", ".help help", ".unknown", ".bind", ".config"}) {
                        chat.sendChatMessage(command); require(channel.readOutbound() == null, "Local command leaked to network: " + command); draw(mc, chat);
                    }
                } finally { channel.finish(); }
                reportFontAllocations(mc.fontRendererObj);
                verifyFormatting(mc.fontRendererObj);
                WorldUnloadNativeCheck.run(mc);
                chat.onGuiClosed(); ArrayListSurface.releaseResources(); context.check();
                System.out.println("PASS: real GuiChat typing/edit/history/completion/100-char scroll, modifiers, CJK/Cyrillic/emoji/control filtering, GuiNewChat scrolling, actual packet send and local-command dispatch in isolated Pbuffer");
            } finally { set(Keyboard.class, null, "created", false); }
        }
    }
}
