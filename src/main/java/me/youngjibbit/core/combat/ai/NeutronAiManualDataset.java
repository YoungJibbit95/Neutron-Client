package me.youngjibbit.core.combat.ai;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local-only, fixed-size manual demonstration schema. This is NOT a model and
 * contains no identity, target ID, IP, server, chat, token, username or text.
 * Data supplied by a trusted explicit-consent recorder only; SHA is an
 * integrity check, not proof that mouse input was actually human-generated.
 */
public final class NeutronAiManualDataset {
    public static final int VERSION = 4;
    public static final int MINECRAFT_PROTOCOL = 47; // 1.8.9
    public static final int MAX_FRAMES = 8_000;
    public static final long MAX_DURATION_MS = 30L * 60_000;
    public static final long MAX_GAP_MS = 250;
    public static final int MAX_FILES = 8;
    private static final int MAGIC = 0x4e4d4434; // NMD4
    private static final int HEADER_BYTES = 8 * 4;
    private static final int TRAILER_BYTES = 32;
    private static final int ROW_BYTES = 8 + 4 + 4 * (NeutronAiCheckpoint.INPUTS + NeutronAiCheckpoint.OUTPUTS);
    public static final int MAX_FILE_BYTES = HEADER_BYTES + MAX_FRAMES * ROW_BYTES + TRAILER_BYTES;
    private static final int CLICK = 1, REACTION = 2;
    private final List<Frame> frames;

    public static final class Frame {
        public final long elapsedMs;
        public final boolean manualClick, manualReaction;
        private final float[] input, label;

        public Frame(long elapsedMs, boolean manualClick, boolean manualReaction,
                     float[] input, float[] label) {
            if (elapsedMs < 0 || elapsedMs > MAX_DURATION_MS
                    || input == null || input.length != NeutronAiCheckpoint.INPUTS
                    || label == null || label.length != NeutronAiCheckpoint.OUTPUTS
                    || !normalized(input) || !normalized(label))
                throw new IllegalArgumentException("invalid bounded manual frame");
            this.elapsedMs = elapsedMs;
            this.manualClick = manualClick;
            this.manualReaction = manualReaction;
            this.input = input.clone();
            this.label = label.clone();
        }
        public float[] input() { return input.clone(); }
        public float[] label() { return label.clone(); }
    }

    private NeutronAiManualDataset(List<Frame> validated) {
        this.frames = Collections.unmodifiableList(new ArrayList<>(validated));
    }

    public static NeutronAiManualDataset of(List<Frame> samples) {
        if (samples == null || samples.isEmpty() || samples.size() > MAX_FRAMES)
            throw new IllegalArgumentException("dataset frame count");
        long previous = -1;
        for (Frame row : samples) {
            if (row == null || (previous == -1 ? row.elapsedMs != 0 :
                    row.elapsedMs <= previous || row.elapsedMs - previous > MAX_GAP_MS))
                throw new IllegalArgumentException("broken chronological manual session");
            previous = row.elapsedMs;
        }
        return new NeutronAiManualDataset(samples);
    }

    public int size() { return frames.size(); }
    public List<Frame> frames() { return frames; }
    public int manualClicks() {
        int count = 0;
        for (Frame frame : frames) if (frame.manualClick) count++;
        return count;
    }
    public int manualReactions() {
        int count = 0;
        for (Frame frame : frames) if (frame.manualReaction) count++;
        return count;
    }

    public byte[] encode() {
        int bytes = HEADER_BYTES + frames.size() * ROW_BYTES + TRAILER_BYTES;
        ByteBuffer out = ByteBuffer.allocate(bytes).order(ByteOrder.BIG_ENDIAN);
        out.putInt(MAGIC);
        out.putInt(VERSION);
        out.putInt(MINECRAFT_PROTOCOL);
        out.putInt(NeutronAiCheckpoint.VERSION);
        out.putInt(NeutronAiCheckpoint.INPUTS);
        out.putInt(NeutronAiCheckpoint.OUTPUTS);
        out.putInt(frames.size());
        out.putInt(0); // reserved; all unknown flags/versions fail closed
        for (Frame frame : frames) {
            out.putLong(frame.elapsedMs);
            out.putInt((frame.manualClick ? CLICK : 0) | (frame.manualReaction ? REACTION : 0));
            for (float value : frame.input) out.putFloat(value);
            for (float value : frame.label) out.putFloat(value);
        }
        if (out.position() != bytes - TRAILER_BYTES)
            throw new IllegalStateException("manual format length mismatch");
        out.put(digest(out.array(), bytes - TRAILER_BYTES));
        return out.array();
    }

    /** Hard bound checked before allocation or parsing; returns null on invalid input. */
    public static NeutronAiManualDataset decode(byte[] encoded) {
        if (encoded == null || encoded.length < HEADER_BYTES + ROW_BYTES + TRAILER_BYTES
                || encoded.length > MAX_FILE_BYTES) return null;
        try {
            ByteBuffer in = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
            if (in.getInt() != MAGIC || in.getInt() != VERSION
                    || in.getInt() != MINECRAFT_PROTOCOL
                    || in.getInt() != NeutronAiCheckpoint.VERSION
                    || in.getInt() != NeutronAiCheckpoint.INPUTS
                    || in.getInt() != NeutronAiCheckpoint.OUTPUTS) return null;
            int count = in.getInt();
            if (count < 1 || count > MAX_FRAMES || in.getInt() != 0
                    || encoded.length != HEADER_BYTES + count * ROW_BYTES + TRAILER_BYTES)
                return null;
            byte[] signature = new byte[TRAILER_BYTES];
            System.arraycopy(encoded, encoded.length - TRAILER_BYTES, signature, 0, TRAILER_BYTES);
            if (!MessageDigest.isEqual(signature, digest(encoded, encoded.length - TRAILER_BYTES)))
                return null;
            List<Frame> samples = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long elapsedMs = in.getLong();
                int flags = in.getInt();
                if ((flags & ~(CLICK | REACTION)) != 0) return null;
                float[] input = new float[NeutronAiCheckpoint.INPUTS];
                float[] label = new float[NeutronAiCheckpoint.OUTPUTS];
                for (int j = 0; j < input.length; j++) input[j] = in.getFloat();
                for (int j = 0; j < label.length; j++) label[j] = in.getFloat();
                samples.add(new Frame(elapsedMs, (flags & CLICK) != 0,
                        (flags & REACTION) != 0, input, label));
            }
            return in.position() == encoded.length - TRAILER_BYTES ? of(samples) : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static boolean normalized(float[] values) {
        for (float value : values) if (!Float.isFinite(value) || Math.abs(value) > 1.000001f)
            return false;
        return true;
    }

    private static byte[] digest(byte[] data, int length) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(data, 0, length);
            return sha.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java 8 SHA-256 unavailable", impossible);
        }
    }
}
