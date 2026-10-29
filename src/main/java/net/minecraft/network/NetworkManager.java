package net.minecraft.network;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.SecretKey;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.PacketPipeline;
import me.zero.alpine.event.EventPhase;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.packet.EventPacket;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.CryptManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ITickable;
import net.minecraft.util.LazyLoadBase;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class NetworkManager extends SimpleChannelInboundHandler<Packet>
{
    private static final Logger logger = LogManager.getLogger();
    public static final Marker logMarkerNetwork = MarkerManager.getMarker("NETWORK");
    public static final Marker logMarkerPackets = MarkerManager.getMarker("NETWORK_PACKETS", logMarkerNetwork);
    public static final AttributeKey<EnumConnectionState> attrKeyConnectionState = AttributeKey.<EnumConnectionState>valueOf("protocol");
    public static final LazyLoadBase<NioEventLoopGroup> CLIENT_NIO_EVENTLOOP = new LazyLoadBase<NioEventLoopGroup>()
    {
        protected NioEventLoopGroup load()
        {
            return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
        }
    };
    public static final LazyLoadBase<EpollEventLoopGroup> field_181125_e = new LazyLoadBase<EpollEventLoopGroup>()
    {
        protected EpollEventLoopGroup load()
        {
            return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
        }
    };
    public static final LazyLoadBase<LocalEventLoopGroup> CLIENT_LOCAL_EVENTLOOP = new LazyLoadBase<LocalEventLoopGroup>()
    {
        protected LocalEventLoopGroup load()
        {
            return new LocalEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
        }
    };
    private final EnumPacketDirection direction;
    public final Queue<InboundHandlerTuplePacketListener> outboundPacketsQueue = Queues
            .<InboundHandlerTuplePacketListener>newConcurrentLinkedQueue();
    //private final Queue<NetworkManager.InboundHandlerTuplePacketListener> outboundPacketsQueue = Queues.<NetworkManager.InboundHandlerTuplePacketListener>newConcurrentLinkedQueue();
    private final ReentrantReadWriteLock field_181680_j = new ReentrantReadWriteLock();

    /** The active channel */
    private volatile Channel channel;

    /** A closed connection must never be mistaken for one that has not connected yet. */
    private volatile boolean transportClosed;

    /** The address of the remote party */
    private SocketAddress socketAddress;

    /** The INetHandler instance responsible for processing received packets */
    private volatile INetHandler packetListener;
    private volatile PacketPipeline clientPackets;
    private me.youngjibbit.core.network.PlaySessionLifecycle playSession;
    private java.util.function.Consumer<Packet<?>> acceptedClientPacket;

    /** A String indicating why the network has shutdown. */
    private IChatComponent terminationReason;
    private boolean isEncrypted;
    private boolean disconnected;

    public NetworkManager(EnumPacketDirection packetDirection)
    {
        this.direction = packetDirection;
    }

    public void channelActive(ChannelHandlerContext p_channelActive_1_) throws Exception
    {
        super.channelActive(p_channelActive_1_);
        this.channel = p_channelActive_1_.channel();
        this.socketAddress = this.channel.remoteAddress();

        if (transportClosed) {
            this.channel.close();
            return;
        }

        try
        {
            this.setConnectionState(EnumConnectionState.HANDSHAKING);
        }
        catch (Throwable throwable)
        {
            logger.fatal((Object)throwable);
        }
    }

    /**
     * Sets the new connection state and registers which packets this channel may send and receive
     */
    public void setConnectionState(EnumConnectionState newState)
    {
        this.channel.attr(attrKeyConnectionState).set(newState);
        this.channel.config().setAutoRead(true);
        logger.debug("Enabled auto read");
    }

    public void channelInactive(ChannelHandlerContext p_channelInactive_1_) throws Exception
    {
        this.closeChannel(new ChatComponentTranslation("disconnect.endOfStream", new Object[0]));
    }

    public void exceptionCaught(ChannelHandlerContext p_exceptionCaught_1_, Throwable p_exceptionCaught_2_) throws Exception
    {
        ChatComponentTranslation chatcomponenttranslation;

        if (p_exceptionCaught_2_ instanceof TimeoutException)
        {
            chatcomponenttranslation = new ChatComponentTranslation("disconnect.timeout", new Object[0]);
        }
        else
        {
            chatcomponenttranslation = new ChatComponentTranslation("disconnect.genericReason", new Object[] {"Internal Exception: " + p_exceptionCaught_2_});
        }

        this.closeChannel(chatcomponenttranslation);
    }

    /** Installs the play-only handoff. Login/status/server transports keep vanilla semantics. */
    public void configureClientPackets(net.minecraft.util.IThreadListener thread) {
        java.util.function.Consumer<Packet<?>> observer=acceptedClientPacket;
        if(observer==null)observer=packet -> {
            if (packetListener instanceof net.minecraft.client.network.NetHandlerPlayClient)
                ((net.minecraft.client.network.NetHandlerPlayClient)packetListener).observeAcceptedOutbound(packet);
            else if(packet instanceof net.minecraft.network.play.client.C03PacketPlayer) {
                net.minecraft.network.play.client.C03PacketPlayer movement=(net.minecraft.network.play.client.C03PacketPlayer)packet;
                if(movement.getRotating())me.youngjibbit.core.bootstrap.RuntimeServices.rotation().observeOutboundRotation(movement.getYaw(),movement.getPitch());
            }
        };
        configureClientPackets(thread,observer);
    }
    public void configureClientPackets(net.minecraft.util.IThreadListener thread,java.util.function.Consumer<Packet<?>> observer) {
        if (clientPackets != null) clientPackets.close();
        acceptedClientPacket=observer;
        if(playSession==null && thread!=null)playSession=new me.youngjibbit.core.network.PlaySessionLifecycle(this,thread);
        clientPackets = new PacketPipeline(thread, this::isChannelOpen, observer,
                error -> {
                    logger.error("Client packet processing failed", error);
                    closeChannel(new ChatComponentText("Client packet processing failed: "
                            + error.getClass().getSimpleName() + " (see logs/latest.log)"));
                },
                new me.youngjibbit.core.network.PacketDelays(), packet -> {
                    if(packet instanceof net.minecraft.network.play.server.S01PacketJoinGame && playSession!=null)playSession.confirmStarted();
                });
        final PacketPipeline installed = clientPackets;
        Runnable bind = () -> {
            if (clientPackets == installed && installed.isActive())
                me.youngjibbit.core.bootstrap.RuntimeServices.rotation().bindConnection(installed);
        };
        if (thread.isCallingFromMinecraftThread()) bind.run(); else thread.addScheduledTask(bind);
    }

    public PacketPipeline getClientPackets() { return clientPackets; }
    public me.youngjibbit.core.network.PlaySessionLifecycle getPlaySession() {return playSession;}

    public void deliverClientChat(net.minecraft.network.play.server.S02PacketChat packet,Runnable application) {
        if(playSession==null)application.run();else playSession.deliverChat(packet,application);
    }

    protected void channelRead0(ChannelHandlerContext handlerContext, Packet packet) throws Exception {
        receivePacket(packet, false);
    }

    public void receivePacket(Packet packet, boolean silent) {
        if (!isChannelOpen()) return;
        PacketPipeline pipeline = clientPackets;
        INetHandler handler = packetListener;
        if (pipeline != null) {
            pipeline.inbound(packet, handler, silent, () -> clientPackets == pipeline && packetListener == handler);
            return;
        }
        try { packet.processPacket(packetListener); }
        catch (ThreadQuickExitException scheduled) { }
    }

    /**
     * Sets the NetHandler for this NetworkManager, no checks are made if this handler is suitable for the particular
     * connection state (protocol)
     */
    public void setNetHandler(INetHandler handler)
    {
        Validate.notNull(handler, "packetListener", new Object[0]);
        logger.debug("Set listener of {} to {}", new Object[] {this, handler});
        this.packetListener = handler;
    }

    public void receivePacketSilent(final Packet packet) { receivePacket(packet, true); }

    public void sendPacket(Packet packetIn) { sendPacketTracked(packetIn, false, null); }
    public void sendPacketSilent(Packet packetIn) { sendPacketTracked(packetIn, true, null); }

    public Packet<?> sendPacketTracked(Packet packet, boolean silent) { return sendPacketTracked(packet, silent, null); }

    public Packet<?> sendPacketGuarded(Packet packet, java.util.function.BooleanSupplier validAction) {
        return sendPacketGuarded(packet, false, selected -> selected == packet && validAction.getAsBoolean());
    }

    public Packet<?> sendPacketGuarded(Packet packet, java.util.function.Predicate<Packet<?>> validAction) {
        return sendPacketGuarded(packet, false, validAction);
    }

    public Packet<?> sendPacketGuarded(Packet packet, boolean silent, java.util.function.Predicate<Packet<?>> validAction) {
        PacketPipeline pipeline = clientPackets;
        if (pipeline != null) return pipeline.outboundSelected(packet, silent, selected -> sendTransport((Packet) selected, null), validAction);
        if (!validAction.test(packet) || !isChannelOpen()) return null;
        return sendTransport(packet, null) ? packet : null;
    }

    private Packet<?> sendPacketTracked(Packet packet, boolean silent,
            GenericFutureListener<? extends Future<? super Void>>[] listeners) {
        if (clientPackets != null) return clientPackets.outbound(packet, silent, selected -> sendTransport((Packet) selected, listeners));
        return sendTransport(packet, listeners) ? packet : null;
    }

    public void sendPacket(Packet packetIn, GenericFutureListener<? extends Future<? super Void>> listener,
                           GenericFutureListener<? extends Future<? super Void>>... listeners) {
        sendPacketTracked(packetIn, false, (GenericFutureListener[]) ArrayUtils.add(listeners, 0, listener));
    }

    private boolean sendTransport(Packet packet, GenericFutureListener<? extends Future<? super Void>>[] listeners) {
        if (isChannelOpen()) {
            flushOutboundQueue();
            if (!isChannelOpen()) return rejectClosedTransport();
            dispatchPacket(packet, listeners);
            return true;
        } else {
            field_181680_j.writeLock().lock();
            try {
                if (clientPackets != null) return rejectClosedTransport();
                // Integrated-server player removal still sends chunk unloads after disconnect.
                // Only a genuinely pending first connection may retain packets here.
                if (transportClosed || (channel != null && !channel.isOpen())) return false;
                if (outboundPacketsQueue.size() >= 1024) throw new IllegalStateException("Connection send queue exceeded limit");
                InboundHandlerTuplePacketListener pending = new InboundHandlerTuplePacketListener(packet, listeners);
                outboundPacketsQueue.add(pending);
                // closeChannel may clear the concurrent queue between the check and the add.
                if (transportClosed) {
                    outboundPacketsQueue.remove(pending);
                    return false;
                }
                return true;
            }
            finally { field_181680_j.writeLock().unlock(); }
        }
    }

    private boolean rejectClosedTransport() {
        // The play pipeline must not publish POST/accepted state for a failed transport.
        if (clientPackets != null) throw new IllegalStateException("Play transport closed before acceptance");
        return false;
    }

    /**
     * Will commit the packet to the channel. If the current thread 'owns' the channel it will write and flush the
     * packet, otherwise it will add a task for the channel eventloop thread to do that.
     */
    private void dispatchPacket(final Packet inPacket, final GenericFutureListener <? extends Future <? super Void >> [] futureListeners)
    {
        final EnumConnectionState enumconnectionstate = EnumConnectionState.getFromPacket(inPacket);
        final EnumConnectionState enumconnectionstate1 = (EnumConnectionState)this.channel.attr(attrKeyConnectionState).get();

        if (enumconnectionstate1 != enumconnectionstate)
        {
            logger.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
        }

        if (this.channel.eventLoop().inEventLoop())
        {
            if (enumconnectionstate != enumconnectionstate1)
            {
                this.setConnectionState(enumconnectionstate);
            }

            ChannelFuture channelfuture = this.channel.writeAndFlush(inPacket);

            if (futureListeners != null)
            {
                channelfuture.addListeners(futureListeners);
            }

            channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
        else
        {
            this.channel.eventLoop().execute(new Runnable()
            {
                public void run()
                {
                    if (enumconnectionstate != enumconnectionstate1)
                    {
                        NetworkManager.this.setConnectionState(enumconnectionstate);
                    }

                    ChannelFuture channelfuture1 = NetworkManager.this.channel.writeAndFlush(inPacket);

                    if (futureListeners != null)
                    {
                        channelfuture1.addListeners(futureListeners);
                    }

                    channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
            });
        }
    }

    /**
     * Will iterate through the outboundPacketQueue and dispatch all Packets
     */
    private void flushOutboundQueue()
    {
        if (isChannelOpen())
        {
            this.field_181680_j.readLock().lock();

            try
            {
                while (isChannelOpen())
                {
                    NetworkManager.InboundHandlerTuplePacketListener networkmanager$inboundhandlertuplepacketlistener = (NetworkManager.InboundHandlerTuplePacketListener)this.outboundPacketsQueue.poll();
                    if (networkmanager$inboundhandlertuplepacketlistener == null) break;
                    this.dispatchPacket(networkmanager$inboundhandlertuplepacketlistener.packet, networkmanager$inboundhandlertuplepacketlistener.futureListeners);
                }
            }
            finally
            {
                this.field_181680_j.readLock().unlock();
            }
        }
    }

    /**
     * Checks timeouts and processes all packets received
     */
    public void processReceivedPackets()
    {
        this.flushOutboundQueue();

        if (this.packetListener instanceof ITickable)
        {
            ((ITickable)this.packetListener).update();
        }

        this.channel.flush();
    }

    /**
     * Returns the socket address of the remote side. Server-only.
     */
    public SocketAddress getRemoteAddress()
    {
        return this.socketAddress;
    }

    /**
     * Closes the channel, the parameter can be used for an exit message (not certain how it gets sent)
     */
    public void closeChannel(IChatComponent message)
    {
        transportClosed = true;
        if (this.terminationReason == null) this.terminationReason = message;
        if (clientPackets != null) clientPackets.close();
        this.outboundPacketsQueue.clear();
        if (this.channel != null && this.channel.isOpen())
        {
            this.channel.close().awaitUninterruptibly();
        }
        if(playSession!=null)playSession.requestEnd();
    }

    /**
     * True if this NetworkManager uses a memory connection (single player game). False may imply both an active TCP
     * connection or simply no active connection at all
     */
    public boolean isLocalChannel()
    {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public static NetworkManager func_181124_a(InetAddress p_181124_0_, int p_181124_1_, boolean p_181124_2_)
    {
        final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        Class <? extends SocketChannel > oclass;
        LazyLoadBase <? extends EventLoopGroup > lazyloadbase;

        if (Epoll.isAvailable() && p_181124_2_)
        {
            oclass = EpollSocketChannel.class;
            lazyloadbase = field_181125_e;
        }
        else
        {
            oclass = NioSocketChannel.class;
            lazyloadbase = CLIENT_NIO_EVENTLOOP;
        }

        ((Bootstrap)((Bootstrap)((Bootstrap)(new Bootstrap()).group((EventLoopGroup)lazyloadbase.getValue())).handler(new ChannelInitializer<Channel>()
        {
            protected void initChannel(Channel p_initChannel_1_) throws Exception
            {
                try
                {
                    p_initChannel_1_.config().setOption(ChannelOption.TCP_NODELAY, Boolean.valueOf(true));
                }
                catch (ChannelException var3)
                {
                    ;
                }

                p_initChannel_1_.pipeline().addLast((String)"timeout", (ChannelHandler)(new ReadTimeoutHandler(30))).addLast((String)"splitter", (ChannelHandler)(new MessageDeserializer2())).addLast((String)"decoder", (ChannelHandler)(new MessageDeserializer(EnumPacketDirection.CLIENTBOUND))).addLast((String)"prepender", (ChannelHandler)(new MessageSerializer2())).addLast((String)"encoder", (ChannelHandler)(new MessageSerializer(EnumPacketDirection.SERVERBOUND))).addLast((String)"packet_handler", (ChannelHandler)networkmanager);
            }
        })).channel(oclass)).connect(p_181124_0_, p_181124_1_).syncUninterruptibly();
        return networkmanager;
    }

    /**
     * Prepares a clientside NetworkManager: establishes a connection to the socket supplied and configures the channel
     * pipeline. Returns the newly created instance.
     */
    public static NetworkManager provideLocalClient(SocketAddress address)
    {
        final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        ((Bootstrap)((Bootstrap)((Bootstrap)(new Bootstrap()).group((EventLoopGroup)CLIENT_LOCAL_EVENTLOOP.getValue())).handler(new ChannelInitializer<Channel>()
        {
            protected void initChannel(Channel p_initChannel_1_) throws Exception
            {
                p_initChannel_1_.pipeline().addLast((String)"packet_handler", (ChannelHandler)networkmanager);
            }
        })).channel(LocalChannel.class)).connect(address).syncUninterruptibly();
        return networkmanager;
    }

    /**
     * Adds an encoder+decoder to the channel pipeline. The parameter is the secret key used for encrypted communication
     */
    public void enableEncryption(SecretKey key)
    {
        this.isEncrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new NettyEncryptingDecoder(CryptManager.createNetCipherInstance(2, key)));
        this.channel.pipeline().addBefore("prepender", "encrypt", new NettyEncryptingEncoder(CryptManager.createNetCipherInstance(1, key)));
    }

    public boolean getIsencrypted()
    {
        return this.isEncrypted;
    }

    /**
     * Returns true if this NetworkManager has an active channel, false otherwise
     */
    public boolean isChannelOpen()
    {
        return !transportClosed && this.channel != null && this.channel.isOpen();
    }

    public boolean hasNoChannel()
    {
        return this.channel == null;
    }

    /**
     * Gets the current handler for processing packets
     */
    public INetHandler getNetHandler()
    {
        return this.packetListener;
    }

    /**
     * If this channel is closed, returns the exit message, null otherwise.
     */
    public IChatComponent getExitMessage()
    {
        return this.terminationReason;
    }

    /**
     * Switches the channel to manual reading modus
     */
    public void disableAutoRead()
    {
        this.channel.config().setAutoRead(false);
    }

    public void setCompressionTreshold(int treshold)
    {
        if (treshold >= 0)
        {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder)
            {
                ((NettyCompressionDecoder)this.channel.pipeline().get("decompress")).setCompressionTreshold(treshold);
            }
            else
            {
                this.channel.pipeline().addBefore("decoder", "decompress", new NettyCompressionDecoder(treshold));
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder)
            {
                ((NettyCompressionEncoder)this.channel.pipeline().get("compress")).setCompressionTreshold(treshold);
            }
            else
            {
                this.channel.pipeline().addBefore("encoder", "compress", new NettyCompressionEncoder(treshold));
            }
        }
        else
        {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder)
            {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder)
            {
                this.channel.pipeline().remove("compress");
            }
        }
    }

    public void checkDisconnected()
    {
        if (this.channel != null && !this.channel.isOpen())
        {
            if(playSession!=null)playSession.requestEnd();
            if (!this.disconnected)
            {
                this.disconnected = true;

                if (this.getExitMessage() != null)
                {
                    this.getNetHandler().onDisconnect(this.getExitMessage());
                }
                else if (this.getNetHandler() != null)
                {
                    this.getNetHandler().onDisconnect(new ChatComponentText("Disconnected"));
                }
            }
            else
            {
                logger.warn("handleDisconnection() called twice");
            }
        }
    }

    public static class InboundHandlerTuplePacketListener {
        private final Packet packet;
        private final GenericFutureListener<? extends Future<? super Void>>[] futureListeners;

        public Packet getPacket() {
            return packet;
        }

        public GenericFutureListener<? extends Future<? super Void>>[] getFutureListeners() {
            return futureListeners;
        }

        public InboundHandlerTuplePacketListener(Packet inPacket,
                                                 GenericFutureListener<? extends Future<? super Void>>... inFutureListeners) {
            packet = inPacket;
            futureListeners = inFutureListeners;
        }
    }
}
