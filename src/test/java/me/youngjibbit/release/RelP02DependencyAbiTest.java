package me.youngjibbit.release;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.Test;
import javax.vecmath.*;
import java.lang.reflect.*;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test public void bootstrapDeferredCallbacksLinkWithSyntheticChannels() throws Exception {
        for (boolean deferred : new boolean[]{false, true}) {
            for (boolean success : new boolean[]{false, true}) {
                for (boolean local : new boolean[]{false, true}) callback(deferred, success, local);
            }
        }
    }

    private void callback(boolean deferred, boolean success, boolean local) throws Exception {
        AtomicInteger connects = new AtomicInteger();
        SocketAddress remoteAddress = new FixtureAddress(), localAddress = local ? new FixtureAddress() : null;
        EventLoop loop = (EventLoop) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{EventLoop.class}, (p, m, args) -> {
            if (m.getName().equals("execute")) { ((Runnable) args[0]).run(); return null; }
            if (m.getName().equals("inEventLoop")) return true;
            throw new AssertionError("unexpected event loop call: " + m.getName());
        });
        Channel channel = (Channel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Channel.class}, (p, m, args) -> {
            if (m.getName().equals("eventLoop")) return loop;
            if (m.getName().equals("connect")) {
                assertSame(remoteAddress, args[0]);
                assertEquals(local ? 3 : 2, args.length);
                if (local) assertSame(localAddress, args[1]);
                connects.incrementAndGet();
                return ((ChannelPromise) args[args.length - 1]).setSuccess();
            }
            if (m.getName().equals("toString")) return "synthetic-no-transport-channel";
            throw new AssertionError("unexpected channel call: " + m.getName());
        });
        Throwable failure = new IllegalStateException("synthetic-registration-failure");
        ChannelPromise registration = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        if (success) registration.setSuccess(); else registration.setFailure(failure);
        ChannelPromise result = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        if (deferred) {
            Class<?> listener = Class.forName("io.netty.bootstrap.Bootstrap$1");
            Constructor<?> ctor = listener.getDeclaredConstructor(Bootstrap.class, ChannelFuture.class, Channel.class,
                    SocketAddress.class, SocketAddress.class, ChannelPromise.class);
            ctor.setAccessible(true);
            ((ChannelFutureListener) ctor.newInstance(new Bootstrap(), registration, channel, remoteAddress, localAddress, result)).operationComplete(registration);
        } else {
            Method dispatch = Bootstrap.class.getDeclaredMethod("doConnect0", ChannelFuture.class, Channel.class,
                    SocketAddress.class, SocketAddress.class, ChannelPromise.class);
            dispatch.setAccessible(true); dispatch.invoke(null, registration, channel, remoteAddress, localAddress, result);
        }
        assertTrue(result.isDone()); assertEquals(success, result.isSuccess());
        assertEquals(success ? 1 : 0, connects.get());
        if (!success) assertSame(failure, result.cause());
    }

    private static final class FixtureAddress extends SocketAddress { private static final long serialVersionUID = 1L; }

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
