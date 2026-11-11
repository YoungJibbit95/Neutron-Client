package me.youngjibbit.utils.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import org.lwjgl.BufferUtils;

/** CPU storage only. Each live RenderState owns a distinct snapshot, including nested scopes. */
final class RenderStateBuffers {
    private static final int MAX_IDLE = 16;
    private static final ThreadLocal<ArrayDeque<RenderStateBuffers>> IDLE =
            ThreadLocal.withInitial(ArrayDeque::new);

    // LWJGL's glGet overloads require capacity 16 even for four-component values.
    final IntBuffer scissor = BufferUtils.createIntBuffer(16);
    final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    final FloatBuffer color = BufferUtils.createFloatBuffer(16);
    final FloatBuffer clear = BufferUtils.createFloatBuffer(16);
    final ByteBuffer mask = BufferUtils.createByteBuffer(16);
    final boolean[] lights = new boolean[8];
    final int[] bindings = new int[5];
    final boolean[] textures = new boolean[5];

    private RenderStateBuffers() { }

    static RenderStateBuffers acquire() {
        RenderStateBuffers buffers = IDLE.get().pollFirst();
        if (buffers == null) return new RenderStateBuffers();
        buffers.scissor.clear();
        buffers.viewport.clear();
        buffers.projection.clear();
        buffers.model.clear();
        buffers.color.clear();
        buffers.clear.clear();
        buffers.mask.clear();
        return buffers;
    }

    static void release(RenderStateBuffers buffers) {
        ArrayDeque<RenderStateBuffers> idle = IDLE.get();
        if (idle.size() < MAX_IDLE) idle.addFirst(buffers);
    }
}
