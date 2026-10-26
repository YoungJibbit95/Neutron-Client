package me.youngjibbit.core.network;

import com.google.common.util.concurrent.*;
import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.*;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.util.*;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class PacketFlowTest {
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
        return (INetHandlerPlayClient) Proxy.newProxyInstance(PacketFlowTest.class.getClassLoader(),
                new Class<?>[]{INetHandlerPlayClient.class}, (p,m,a) -> { apply.accept(a); return null; });
    }

    @Test public void actualNetworkManagerHandsOffBeforeEventsAndAppliesReplacementExactlyOnce() throws Exception {
        MainThread main = new MainThread(); Observer observer = new Observer();
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network); network.configureClientPackets(main);
        AtomicInteger applied = new AtomicInteger(); S00PacketKeepAlive replacement = new S00PacketKeepAlive(2);
        network.setNetHandler(handler(args -> { assertTrue(main.isCallingFromMinecraftThread());
            assertSame(replacement,args[0]); applied.incrementAndGet(); }));
        observer.action = e -> {
            assertTrue(main.isCallingFromMinecraftThread());
            if(e.isPre()) e.setPacket(replacement); else assertEquals(1,applied.get());
        };
        Pulsar.BUS.subscribe(observer);
        try {
            Thread netty = new Thread(() -> channel.writeInbound(new S00PacketKeepAlive(1)));
            netty.start();netty.join(); assertTrue(observer.calls.isEmpty());assertEquals(0,applied.get());
            main.drain();assertEquals(1,applied.get());assertEquals(Arrays.asList("inPre","inPost"),observer.calls);
            observer.calls.clear();observer.action = e -> e.cancel();
            channel.writeInbound(new S00PacketKeepAlive(3));assertEquals(1,applied.get());
            assertEquals(Collections.singletonList("inPre"),observer.calls);
        } finally { Pulsar.BUS.unsubscribe(observer);channel.finish(); }
    }

    @Test public void directNormalTrackedAndSilentSendsShareAcceptanceAndDoNotRetainIdentityFlags() {
        MainThread main = new MainThread(); Observer observer = new Observer();
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network);
        NetHandlerPlayClient play = new NetHandlerPlayClient(null,null,network,null);
        network.configureClientPackets(main); // real wrapper, fake thread, no Minecraft/GL
        Pulsar.BUS.subscribe(observer);
        try {
            C03PacketPlayer.C05PacketPlayerLook packet = new C03PacketPlayer.C05PacketPlayerLook(31,12,true);
            network.sendPacket(packet);assertSame(packet,channel.readOutbound());
            assertEquals(31,RuntimeServices.rotation().getEffectiveYaw(),0);
            assertSame(packet,play.addToSendQueueTracked(packet));assertSame(packet,channel.readOutbound());
            network.sendPacketSilent(packet);assertSame(packet,channel.readOutbound());
            network.sendPacket(packet);assertSame(packet,channel.readOutbound());
            assertEquals(Arrays.asList("outPre","outPost","outPre","outPost","outPre","outPost"),observer.calls);
            observer.action = e -> { if(e.isPre())e.cancel(); };
            assertNull(play.addToSendQueueTracked(packet));assertNull(channel.readOutbound());
            C03PacketPlayer replacement = new C03PacketPlayer.C05PacketPlayerLook(-20,3,false);
            observer.action = e -> { if(e.isPre()) e.setPacket(replacement); };
            assertSame(replacement,play.addToSendQueueTracked(packet));assertSame(replacement,channel.readOutbound());
            assertEquals(-20,RuntimeServices.rotation().getEffectiveYaw(),0);
        } finally { Pulsar.BUS.unsubscribe(observer);channel.finish();RuntimeServices.rotation().reset(); }
    }

    @Test public void quickExitTransfersCompletionAndNeverReportsSchedulingOrFailureAsSuccess() throws Exception {
        MainThread main = new MainThread(); AtomicInteger applied = new AtomicInteger(), completed = new AtomicInteger();
        INetHandler handler = reason -> { };
        Packet<INetHandler> packet = new Packet<INetHandler>() {
            public void readPacketData(PacketBuffer b) { } public void writePacketData(PacketBuffer b) { }
            public void processPacket(INetHandler h) { PacketThreadUtil.checkThreadAndEnqueue(this,h,main);applied.incrementAndGet(); }
        };
        Thread netty = new Thread(() -> PacketApplication.process(packet,handler,()->true,completed::incrementAndGet));
        netty.start();netty.join();assertEquals(0,completed.get());assertEquals(0,applied.get());
        main.drain();assertEquals(1,completed.get());assertEquals(1,applied.get());main.drain();assertEquals(1,completed.get());
        AtomicBoolean valid = new AtomicBoolean(true);
        netty = new Thread(() -> PacketApplication.process(packet,handler,valid::get,completed::incrementAndGet));
        netty.start();netty.join();valid.set(false);main.drain();assertEquals(1,applied.get());assertEquals(1,completed.get());
    }

    public static final class BothListeners implements Subscriber {
        int field, method;
        @Subscribe public final Listener<EventPacket> onField = new Listener<>(e -> field++);
        @Subscribe public void onMethod(EventPacket e) { method++; }
    }
    @Test public void alpineFieldAndMethodSubscriptionsAreIdempotentAndCanBeRemovedDuringDispatch() {
        BothListeners both = new BothListeners();
        Pulsar.BUS.subscribe(both);Pulsar.BUS.subscribe(both);
        Pulsar.BUS.post(new EventPacket(null)); assertEquals(1,both.field);assertEquals(1,both.method);
        Pulsar.BUS.unsubscribe(both);Pulsar.BUS.unsubscribe(both);
        Pulsar.BUS.post(new EventPacket(null)); assertEquals(1,both.field);assertEquals(1,both.method);
    }
    @Test public void connectionBindingFromLoginThreadWaitsForTheClientThread() throws Exception {
        MainThread main=new MainThread();NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);EmbeddedChannel channel=new EmbeddedChannel(network);
        me.youngjibbit.core.combat.RotationManager rotation=RuntimeServices.rotation();rotation.reset();rotation.beginMotion(0,0);rotation.finishMotion(90,20,true);
        try {
            Thread login=new Thread(()->network.configureClientPackets(main));login.start();login.join();
            assertEquals(90,rotation.getEffectiveYaw(),0);assertTrue(rotation.hasEffectiveRotation());
            main.drain();assertFalse(rotation.hasEffectiveRotation());assertTrue(rotation.isConnection(network.getClientPackets()));
        } finally {channel.finish();rotation.reset();}
    }

}
