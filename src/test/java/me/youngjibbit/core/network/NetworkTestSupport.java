package me.youngjibbit.core.network;

import com.google.common.util.concurrent.*;
import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.*;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;
import net.minecraft.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Shared test-only fakes. Production sources must never depend on this test fixture. */
final class NetworkTestSupport {
    private NetworkTestSupport() { }

    static <T> T bare(Class<T> type) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(type));
    }
    public static class WorldStub extends WorldClient {
        Entity target;
        public WorldStub() {super(null,null,0,null,null);}
        @Override public Entity getEntityByID(int id) {return target!=null && target.getEntityId()==id?target:null;}
    }
    public static final class MainThread implements IThreadListener {
        final Thread owner = Thread.currentThread();
        final Queue<Runnable> tasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
        public ListenableFuture<Object> addScheduledTask(Runnable task) {
            tasks.add(task); return Futures.immediateFuture(null);
        }
        public boolean isCallingFromMinecraftThread() { return Thread.currentThread() == owner; }
        public void drain() { Runnable task; while ((task = tasks.poll()) != null) task.run(); }
    }
    public static final class Observer implements Subscriber {
        final List<String> calls = new ArrayList<>();
        java.util.function.Consumer<EventPacket> action = e -> { };
        @Subscribe public final Listener<EventPacket> listener = new Listener<>(e -> {
            calls.add((e.isInbound()?"in":"out")+(e.isPre()?"Pre":"Post")); action.accept(e);
        });
    }
    public static INetHandlerPlayClient handler(java.util.function.Consumer<Object[]> apply) {
        return (INetHandlerPlayClient) Proxy.newProxyInstance(NetworkTestSupport.class.getClassLoader(),
                new Class<?>[]{INetHandlerPlayClient.class}, (p,m,a) -> { apply.accept(a); return null; });
    }

    public static class Client extends Minecraft {
        MainThread main;
        public Client() {super(null);}
        @Override public boolean isCallingFromMinecraftThread() {return main.isCallingFromMinecraftThread();}
        @Override public ListenableFuture<Object> addScheduledTask(Runnable task) {return main.addScheduledTask(task);}
    }
    public static class Chat extends GuiNewChat {
        public List<String> displayed=new ArrayList<>();public boolean fail;public Runnable after=()->{};
        public Chat() {super(null);}
        @Override public void printChatMessage(IChatComponent text) {
            if(fail)throw new IllegalStateException("chat application failed");
            displayed.add(text.getUnformattedText());after.run();
        }
    }
    public static class Gui extends GuiIngame {
        Chat chat;
        public Gui() {super(null);}
        @Override public GuiNewChat getChatGUI() {return chat;}
        @Override public void setRecordPlaying(IChatComponent text,boolean playing) {chat.printChatMessage(text);}
    }
    public static class Play extends NetHandlerPlayClient {
        final Client client;int joins,respawns;boolean failJoin;
        Play(Client client,NetworkManager network) {super(client,null,network,null);this.client=client;}
        @Override public void handleJoinGame(S01PacketJoinGame packet) {
            PacketThreadUtil.checkThreadAndEnqueue(packet,this,client);
            if(failJoin)throw new IllegalStateException("join application failed");
            joins++;
        }
        @Override public void handleRespawn(S07PacketRespawn packet) {
            PacketThreadUtil.checkThreadAndEnqueue(packet,this,client);respawns++;getNetworkManager().getClientPackets().worldChanged();
        }
    }
    public static final class Fixture implements AutoCloseable {
        public final MainThread main=new MainThread();
        public final NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        public final EmbeddedChannel channel=new EmbeddedChannel(network);
        public final Client client;public final Chat chat=new Chat();public final Play play;
        public Fixture() throws Exception {
            client=bare(Client.class);client.main=main;
            Gui gui=bare(Gui.class);gui.chat=chat;client.ingameGUI=gui;
            play=new Play(client,network);network.setNetHandler(play);
        }
        public void join() {channel.writeInbound(new S01PacketJoinGame());}
        public void netty(Runnable action) throws Exception {Thread worker=new Thread(action);worker.start();worker.join();}
        public void close() {channel.finish();main.drain();}
    }
    static void clock(ExploitHookFixture f,AtomicLong now) throws Exception {
        Field delays=PacketPipeline.class.getDeclaredField("delays");delays.setAccessible(true);
        delays.set(f.pipe(),new PacketDelays(now::get));
    }
}
