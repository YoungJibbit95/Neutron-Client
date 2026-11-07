package me.youngjibbit.release;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.Test;
import javax.vecmath.*;
import java.lang.reflect.*;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import static org.junit.Assert.*;

/** Java8 ABI probes, also run against Assembly alone. No socket, DNS, device or game. */
public class RelP02DependencyAbiTest {
    @Test public void vecmathProjectionFieldsAndMatrixContractExecute() throws Exception {
        // ESP/Nametags use these constructors and inherited public tuple fields.
        Vector3d corner = new Vector3d(2, 3, 4);
        Vector4d bounds = new Vector4d(corner.x, corner.y, corner.z, 0);
        bounds.x = Math.min(1, bounds.x); bounds.w = Math.max(5, bounds.w);
        assertArrayEquals(new double[]{1, 3, 4, 5}, new double[]{bounds.x, bounds.y, bounds.z, bounds.w}, 0);
        Matrix4f matrix = new Matrix4f(); matrix.setIdentity();
        Point3f point = new Point3f(2, 3, 4); matrix.transform(point);
        assertEquals(new Point3f(2, 3, 4), point);
        // OptiFine's optional Forge hook names Matrix4f; do not invoke the Forge dummy.
        Class<?> dummy = Class.forName("net.minecraftforge.client.model.TRSRTransformation", false, getClass().getClassLoader());
        assertNotNull(dummy.getConstructor(Matrix4f.class));
    }

    @Test public void bootstrapClientSignaturesAndCloneLink() throws Exception {
        Bootstrap b = new Bootstrap().handler(new ChannelInboundHandlerAdapter());
        assertNotSame(b, b.clone());
        assertEquals(io.netty.bootstrap.AbstractBootstrap.class, Bootstrap.class.getMethod("group", EventLoopGroup.class).getReturnType());
        assertEquals(ChannelFuture.class, Bootstrap.class.getMethod("connect", java.net.InetAddress.class, int.class).getReturnType());
        assertEquals(ChannelFuture.class, Bootstrap.class.getMethod("connect", SocketAddress.class).getReturnType());
        assertNotNull(Bootstrap.class.getMethod("channel", Class.class));
        try { b.validate(); fail("group is deliberately absent"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("group")); }
    }

    @Test(timeout = 20000)
    public void bootstrapImmediateAndDeferredRegistrationConnectThroughPublicApi() throws Exception {
        EventLoopGroup serverGroup = new DefaultEventLoopGroup(1);
        EventLoopGroup clientGroup = new DefaultEventLoopGroup(1);
        Channel server = null;
        try {
            server = new ServerBootstrap().group(serverGroup).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override protected void initChannel(Channel ch) { }
                    }).bind(LocalAddress.ANY).sync().channel();
            for (boolean deferred : new boolean[]{false, true}) {
                for (boolean success : new boolean[]{false, true}) {
                    for (boolean local : new boolean[]{false, true}) {
                        connect(clientGroup, server.localAddress(), deferred, success, local);
                    }
                }
            }
        } finally {
            if (server != null) server.close().syncUninterruptibly();
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
            serverGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private void connect(EventLoopGroup group, SocketAddress serverAddress,
                         boolean deferred, boolean success, boolean local) throws Exception {
        Bootstrap bootstrap = new Bootstrap().group(group).channel(LocalChannel.class)
                .handler(new ChannelInboundHandlerAdapter());
        SocketAddress remote = success ? serverAddress : new LocalAddress("missing-" + java.util.UUID.randomUUID());
        final LocalAddress localAddress = local ? new LocalAddress("client-" + java.util.UUID.randomUUID()) : null;
        Callable<ChannelFuture> call = () -> localAddress == null
                ? bootstrap.connect(remote) : bootstrap.connect(remote, localAddress);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        ChannelFuture result = null;
        try {
            if (deferred) {
                group.next().execute(() -> {
                    entered.countDown();
                    try {
                        if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("registration was not resumed");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                });
                assertTrue("event loop did not enter fixture", entered.await(5, TimeUnit.SECONDS));
                result = call.call();
                assertFalse("registration must still be pending", result.isDone());
                resume.countDown();
            } else {
                // Register from the event loop itself to exercise the immediate-registration path.
                result = group.next().submit(call).get(5, TimeUnit.SECONDS);
            }
            assertTrue("connect did not complete", result.await(5, TimeUnit.SECONDS));
            assertEquals(success, result.isSuccess());
            assertEquals(success, result.channel().isActive());
            if (success) {
                assertEquals(serverAddress, result.channel().remoteAddress());
                if (local) assertEquals(localAddress, result.channel().localAddress());
            } else {
                assertNotNull(result.cause());
            }
        } finally {
            resume.countDown();
            if (result != null) result.channel().close().syncUninterruptibly();
        }
    }

    @Test(timeout = 10000)
    public void bootstrapPropagatesRegistrationFailureWithoutConnecting() throws Exception {
        EventLoopGroup group = new DefaultEventLoopGroup(1);
        final IllegalStateException failure = new IllegalStateException("synthetic-registration-failure");
        ChannelFuture result = null;
        try {
            result = new Bootstrap().group(group).channelFactory(() -> new LocalChannel() {
                @Override protected void doRegister(ChannelPromise promise) { promise.setFailure(failure); }
            }).handler(new ChannelInboundHandlerAdapter()).connect(new LocalAddress("unused"));
            assertTrue(result.await(5, TimeUnit.SECONDS));
            assertFalse(result.isSuccess());
            assertSame(failure, result.cause());
            assertFalse(result.channel().isActive());
        } finally {
            if (result != null) result.channel().close().syncUninterruptibly();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    @Test public void soundCodecsConstructThroughTheRealReflectiveRegistry() throws Exception {
        // Unique fixture extensions avoid changing the application's ogg/wav registrations.
        paulscode.sound.SoundSystemConfig.setCodec("relp02ogg", paulscode.sound.codecs.CodecJOrbis.class);
        paulscode.sound.SoundSystemConfig.setCodec("relp02wav", paulscode.sound.codecs.CodecWav.class);
        paulscode.sound.ICodec ogg = paulscode.sound.SoundSystemConfig.getCodec("fixture.relp02ogg");
        paulscode.sound.ICodec wav = paulscode.sound.SoundSystemConfig.getCodec("fixture.relp02wav");
        assertTrue(ogg instanceof paulscode.sound.codecs.CodecJOrbis);
        assertTrue(wav instanceof paulscode.sound.codecs.CodecWav);
        ogg.cleanup(); wav.cleanup();
        assertNotNull(paulscode.sound.libraries.LibraryLWJGLOpenAL.class.getConstructor());
    }

    @Test public void lwjglUtilityCallsAndNativeEntrySignaturesRemainAvailable() throws Exception {
        java.nio.FloatBuffer floats = org.lwjgl.BufferUtils.createFloatBuffer(16);
        assertTrue(floats.isDirect()); assertEquals(16, floats.capacity());
        org.lwjgl.util.vector.Matrix4f identity = new org.lwjgl.util.vector.Matrix4f();
        org.lwjgl.util.vector.Vector4f input = new org.lwjgl.util.vector.Vector4f(2, 3, 4, 1);
        org.lwjgl.util.vector.Vector4f output = org.lwjgl.util.vector.Matrix4f.transform(identity, input, null);
        assertEquals(input.x, output.x, 0); assertEquals(input.w, output.w, 0);
        ClassLoader loader = getClass().getClassLoader();
        Class<?> display = Class.forName("org.lwjgl.opengl.Display", false, loader);
        assertNotNull(display.getMethod("create")); assertNotNull(display.getMethod("destroy"));
        assertNotNull(display.getMethod("setResizable", boolean.class));
        assertNotNull(Class.forName("org.lwjgl.opengl.Pbuffer", false, loader).getMethod("destroy"));
        assertNotNull(Class.forName("org.lwjgl.openal.AL", false, loader).getMethod("create"));
        // Native execution belongs to the separate unchanged WindowsNativeLinkCheck.
    }
}
