package me.youngjibbit.utils.render;

import java.io.*;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import static org.lwjgl.opengl.GL11.*;

/** Render-thread-only geometry alpha cache, never a cache of the scene or surface color. */
final class HaloCache {
    static final int MAX_ENTRIES = 32;
    static final long MAX_BYTES = 128L * 1024 * 1024, IDLE_NANOS = 1_000_000_000L;
    private static final String[] SOURCES = {"arraylistSurface.frag", "vertex.vsh", "glow.frag", "uiGradient.glsl", "uiGlass.glsl"};
    private final Retention<Key> retention = new Retention<>();
    private final List<Framebuffer> outputs = new ArrayList<>();
    private final IntBuffer polygonMode = BufferUtils.createIntBuffer(16);
    private final LongSupplier clock;
    private int width, height, clipPlanes;
    long hits, misses, bypasses, evictions, allocations, deletions;

    HaloCache() { this(System::nanoTime); }
    HaloCache(LongSupplier clock) { this.clock = clock; }
    interface Renderer { void draw(Framebuffer output); }

    Framebuffer get(int width, int height, int scale, int radius, float[] corners,
                    float left, float top, float right, float bottom, boolean originalSources, Renderer renderer) {
        resize(width, height);
        if (!originalSources || "false".equalsIgnoreCase(System.getProperty("pulsar.haloCache")) || !eligibleRaster()) {
            bypasses++;return null;
        }
        int capacity = capacity(width, height);
        if (capacity == 0) { bypasses++;return null; }
        Key key = new Key(width, height, scale, radius, glIsEnabled(GL_DITHER), corners, left, top, right, bottom);
        Selection selection = retention.select(key, clock.getAsLong(), capacity);
        if (selection == null) { bypasses++;return null; }
        if (selection.hit) { hits++;return outputs.get(selection.index); }
        misses++;
        if (selection.evicted) evictions++;
        Framebuffer output;
        if (selection.index == outputs.size()) {
            output = new Framebuffer(width, height, false);
            output.setFramebufferFilter(GL_LINEAR);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(output.framebufferTexture);glBindTexture(GL_TEXTURE_2D, output.framebufferTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            outputs.add(output);allocations++;
        } else output = outputs.get(selection.index);
        // Publish only after both fully cleared separable passes completed submission.
        renderer.draw(output);
        retention.complete(selection);
        return output;
    }

    private boolean eligibleRaster() {
        glGetInteger(GL_POLYGON_MODE, polygonMode);
        if (polygonMode.get(0) != GL_FILL || polygonMode.get(1) != GL_FILL
                || glIsEnabled(GL_COLOR_LOGIC_OP) || glIsEnabled(GL_POLYGON_SMOOTH) || glIsEnabled(GL_POLYGON_STIPPLE)) return false;
        if (clipPlanes == 0) clipPlanes = glGetInteger(GL_MAX_CLIP_PLANES);
        for (int plane = 0; plane < clipPlanes; plane++) if (glIsEnabled(GL_CLIP_PLANE0 + plane)) return false;
        return true;
    }

    static int capacity(int width, int height) {
        if (width <= 0 || height <= 0) return 0;
        long pixels = (long)width * height;
        return pixels > MAX_BYTES / 4 ? 0 : (int)Math.min(MAX_ENTRIES, MAX_BYTES / (pixels * 4));
    }
    void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        clear();this.width = width;this.height = height;
    }
    void clear() {
        for (Framebuffer output : outputs) { output.deleteFramebuffer();deletions++; }
        outputs.clear();retention.clear();width = height = clipPlanes = 0;
    }
    long bytes() { return (long)outputs.size() * width * height * 4; }
    int size() { return outputs.size(); }

    /** Match ShaderUtil's readLine normalization; compare actual resources, not pack names. */
    static boolean originalSources(IResourceManager manager) {
        try {
            for (String source : SOURCES) {
                String path = "Flauxy/Shaders/" + source;
                try (InputStream actual = manager.getResource(new ResourceLocation(path)).getInputStream();
                     InputStream bundled = HaloCache.class.getResourceAsStream("/assets/minecraft/" + path)) {
                    if (!text(actual).equals(text(bundled))) return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException failure) { return false; }
    }
    private static String text(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing halo shader source");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();String line;
        while ((line = reader.readLine()) != null) result.append(line).append('\n');
        return result.toString();
    }

    static final class Key {
        private final int[] bits;
        private final int hash;
        Key(int width, int height, int scale, int radius, boolean dither, float[] corners,
            float left, float top, float right, float bottom) {
            bits = new int[corners.length + 9];
            bits[0] = width;bits[1] = height;bits[2] = scale;bits[3] = radius;bits[4] = dither ? 1 : 0;
            bits[5] = Float.floatToRawIntBits(left);bits[6] = Float.floatToRawIntBits(top);
            bits[7] = Float.floatToRawIntBits(right);bits[8] = Float.floatToRawIntBits(bottom);
            for (int i = 0; i < corners.length; i++) bits[i + 9] = Float.floatToRawIntBits(corners[i]);
            hash = Arrays.hashCode(bits);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) { return other instanceof Key && Arrays.equals(bits, ((Key)other).bits); }
    }
    static final class Selection {
        final int index;final boolean hit, evicted;
        Selection(int index, boolean hit, boolean evicted) { this.index = index;this.hit = hit;this.evicted = evicted; }
    }
    /** A busy retained subset survives oversubscription; animation uses shared scratch. */
    static final class Retention<K> {
        private final List<Slot<K>> slots = new ArrayList<>();
        Selection select(K key, long now, int capacity) {
            int reusable = -1;long oldest = Long.MAX_VALUE;
            for (int i = 0; i < slots.size(); i++) {
                Slot<K> slot = slots.get(i);
                if (slot.ready && slot.key.equals(key)) { slot.lastUse = now;return new Selection(i, true, false); }
                if (!slot.ready || now - slot.lastUse > IDLE_NANOS) {
                    if (reusable < 0 || slot.lastUse < oldest) { reusable = i;oldest = slot.lastUse; }
                }
            }
            if (reusable < 0 && slots.size() < capacity) { reusable = slots.size();slots.add(new Slot<>()); }
            if (reusable < 0) return null;
            Slot<K> slot = slots.get(reusable);boolean evicted = slot.ready;
            slot.key = key;slot.lastUse = now;slot.ready = false;
            return new Selection(reusable, false, evicted);
        }
        void complete(Selection selection) { slots.get(selection.index).ready = true; }
        void clear() { slots.clear(); }
    }
    private static final class Slot<K> { K key;long lastUse;boolean ready; }
}
