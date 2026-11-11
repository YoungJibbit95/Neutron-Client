package net.minecraft.client.gui;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.Command;
import me.youngjibbit.command.CommandManager;
import me.youngjibbit.command.impl.TrashCommand;
import me.youngjibbit.core.inventory.TrashItemStore;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.gui.inventory.TrashMenuScreen;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.player.InventoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.lwjgl.input.Keyboard;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Real Enter/send/command dispatch. Only text input, the window boundary and transport are synthetic. */
@RunWith(Parameterized.class)
public class GuiChatCommandNavigationTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"localTrashCommandKeepsItsEditorForBothEnterKeysAndAliases"},
                {"ordinaryMessageStillSendsExactlyOnceAndClosesChat"},
                {"emptyAndWhitespaceInputCloseWithoutSending"}
        });
    }
    private final String method;
    public GuiChatCommandNavigationTest(String method){this.method=method;}
    @Test public void realEnterHandlerInFreshJvm()throws Exception {Wave2IsolatedCase.run(Checks.class,method);}

    public static class Client extends Minecraft {
        int transitions;
        public Client(){super(null);}
        @Override public void displayGuiScreen(GuiScreen next){transitions++;currentScreen=next;}
    }
    public static class Outgoing extends NetHandlerPlayClient {
        List<Packet> sent;
        public Outgoing(){super(null,null,null,null);}
        @Override public void addToSendQueue(Packet packet){sent.add(packet);}
    }
    private static class Input extends GuiTextField {
        private final String value;
        Input(String value){super(0,null,0,0,100,10);this.value=value;}
        @Override public String getText(){return value;}
    }
    private static <T> T bare(Class<T> type)throws Exception {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)access.get(null)).allocateInstance(type));
    }
    private static void set(Class<?> owner,Object receiver,String name,Object value)throws Exception {
        Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(receiver,value);
    }
    private static final class Fixture {
        final Client client;
        final Outgoing transport;
        final InventoryManager inventory;
        Fixture()throws Exception {
            // Each outer case has its own JVM: legacy static Minecraft references bind to this fixture.
            client=bare(Client.class);set(Minecraft.class,null,"theMinecraft",client);
            client.ingameGUI=bare(GuiIngame.class);
            set(GuiIngame.class,client.ingameGUI,"persistantChatGUI",new GuiNewChat(client));
            client.thePlayer=bare(EntityPlayerSP.class);
            transport=bare(Outgoing.class);transport.sent=new ArrayList<>();
            set(EntityPlayerSP.class,client.thePlayer,"sendQueue",transport);
            inventory=new InventoryManager(TrashItemStore.memory());
            set(Pulsar.class,Pulsar.INSTANCE,"mm",new ModuleManager(Collections.singletonList(inventory)));
            // Supply an explicit registry; command parsing/aliases and the actual Trash command stay real.
            CommandManager commands=bare(CommandManager.class);
            Map<String,Command> registry=new LinkedHashMap<>();registry.put("trash",new TrashCommand());
            set(CommandManager.class,commands,"commands",registry);set(Pulsar.class,Pulsar.INSTANCE,"cm",commands);
        }
        GuiChat chat(String input) {
            client.transitions=0;transport.sent.clear();
            GuiChat chat=new GuiChat();chat.mc=client;chat.inputField=new Input(input);client.currentScreen=chat;
            return chat;
        }
    }
    public static class Checks {
        @Test public void localTrashCommandKeepsItsEditorForBothEnterKeysAndAliases()throws Exception {
            Fixture fixture=new Fixture();
            for(int enter:new int[]{Keyboard.KEY_RETURN,Keyboard.KEY_NUMPADENTER})for(String name:new String[]{"trash","trashmenu"}) {
                GuiChat chat=fixture.chat("  ."+name+"  ");chat.keyTyped('\n',enter);
                assertTrue("Enter closed the editor opened by the local command",fixture.client.currentScreen instanceof TrashMenuScreen);
                assertEquals("Only the command's destination change should run",1,fixture.client.transitions);
                assertTrue("Local editor commands never reach chat transport",fixture.transport.sent.isEmpty());
                assertFalse("Opening settings does not activate inventory actions",fixture.inventory.isToggled());
                Field parent=TrashMenuScreen.class.getDeclaredField("parent");parent.setAccessible(true);
                assertNull("Leaving the editor must not reopen the submitted chat",parent.get(fixture.client.currentScreen));
                List<String> history=fixture.client.ingameGUI.getChatGUI().getSentMessages();
                assertEquals("."+name,history.get(history.size()-1));
            }
        }
        @Test public void ordinaryMessageStillSendsExactlyOnceAndClosesChat()throws Exception {
            Fixture fixture=new Fixture();
            for(int enter:new int[]{Keyboard.KEY_RETURN,Keyboard.KEY_NUMPADENTER}) {
                GuiChat chat=fixture.chat("  ordinary message  ");chat.keyTyped('\n',enter);
                assertNull(fixture.client.currentScreen);assertEquals(1,fixture.client.transitions);
                assertEquals(1,fixture.transport.sent.size());
                assertTrue(fixture.transport.sent.get(0) instanceof C01PacketChatMessage);
                assertEquals("ordinary message",((C01PacketChatMessage)fixture.transport.sent.get(0)).getMessage());
                assertEquals(Collections.singletonList("ordinary message"),fixture.client.ingameGUI.getChatGUI().getSentMessages());
            }
        }
        @Test public void emptyAndWhitespaceInputCloseWithoutSending()throws Exception {
            Fixture fixture=new Fixture();
            for(int enter:new int[]{Keyboard.KEY_RETURN,Keyboard.KEY_NUMPADENTER})for(String value:new String[]{""," \t "}) {
                GuiChat chat=fixture.chat(value);chat.keyTyped('\n',enter);
                assertNull(fixture.client.currentScreen);assertEquals(1,fixture.client.transitions);
                assertTrue(fixture.transport.sent.isEmpty());
                assertTrue(fixture.client.ingameGUI.getChatGUI().getSentMessages().isEmpty());
            }
        }
    }
}
