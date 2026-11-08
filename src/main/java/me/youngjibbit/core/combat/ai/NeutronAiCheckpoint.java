package me.youngjibbit.core.combat.ai;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Immutable, Java 8-only inference checkpoint. This class parses bytes supplied
 * by an explicitly authorized future loader; it never reads a file or a URL.
 *
 * Its SHA-256 trailer detects accidental changes, NOT who trained the model.
 * User consent, trusted origin and account-local path checks are separate gates.
 */
public final class NeutronAiCheckpoint {
    public static final int VERSION = 1;
    public static final int WIDTH = 40;
    public static final int HISTORY = 4;
    public static final int INPUTS = WIDTH * HISTORY;
    public static final int HIDDEN = 16;
    public static final int OUTPUTS = 2; // yaw and pitch velocity commands ONLY
    public static final int PARAMETERS = HIDDEN * (INPUTS + 1) + OUTPUTS * (HIDDEN + 1);
    public static final int MULTIPLY_ADDS = HIDDEN * INPUTS + OUTPUTS * HIDDEN;
    public static final int HEADER_BYTES = 52;
    public static final int SHA_BYTES = 32;
    public static final int SERIALIZED_BYTES = HEADER_BYTES + PARAMETERS * 4 + SHA_BYTES;
    public static final int MAX_CHECKPOINT_BYTES = 16 * 1024;
    private static final int MAGIC = 0x4e414931; // NAI1

    private final float[] parameters;
    private final int recordedFrames, manualClicks, reactions, epochs;
    private final float trainingLoss, validationLoss;

    private NeutronAiCheckpoint(float[] parameters, int recordedFrames, int manualClicks,
                                int reactions, int epochs, float trainingLoss, float validationLoss) {
        this.parameters = parameters;
        this.recordedFrames = recordedFrames;
        this.manualClicks = manualClicks;
        this.reactions = reactions;
        this.epochs = epochs;
        this.trainingLoss = trainingLoss;
        this.validationLoss = validationLoss;
    }

    /**
     * No file-system access, streaming, reflection or unbounded allocation.
     * Unknown versions, truncated/oversized payloads, mismatched dimensions,
     * non-finite weights/metrics and corrupt digests all return null (HOLD).
     */
    public static NeutronAiCheckpoint decode(byte[] encoded) {
        if (encoded == null || encoded.length != SERIALIZED_BYTES
                || encoded.length > MAX_CHECKPOINT_BYTES) return null;
        final int payload = encoded.length - SHA_BYTES;
        byte[] expected = new byte[SHA_BYTES];
        System.arraycopy(encoded, payload, expected, 0, SHA_BYTES);
        if (!MessageDigest.isEqual(expected, sha256(encoded, payload))) return null;

        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != MAGIC || buffer.getInt() != VERSION
                || buffer.getInt() != WIDTH || buffer.getInt() != HISTORY
                || buffer.getInt() != HIDDEN || buffer.getInt() != OUTPUTS
                || buffer.getInt() != PARAMETERS) return null;

        int frames = buffer.getInt();
        int clicks = buffer.getInt();
        int responseCount = buffer.getInt();
        int trainEpochs = buffer.getInt();
        float trainLoss = buffer.getFloat();
        float validationLoss = buffer.getFloat();

        if (frames < 600 || frames > 60_000 || clicks < 15 || clicks > frames
                || responseCount < 3 || responseCount > frames
                || trainEpochs < 1 || trainEpochs > 1_000
                || !goodLoss(trainLoss) || !goodLoss(validationLoss)) return null;

        float[] weights = new float[PARAMETERS];
        for (int i = 0; i < weights.length; i++) {
            float weight = buffer.getFloat();
            if (!Float.isFinite(weight) || Math.abs(weight) > 8) return null;
            weights[i] = weight;
        }
        if (buffer.position() != payload) return null;
        return new NeutronAiCheckpoint(weights, frames, clicks, responseCount,
                trainEpochs, trainLoss, validationLoss);
    }

    private static boolean goodLoss(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 20;
    }

    private static byte[] sha256(byte[] bytes, int count) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, count);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossibleInJava8) {
            throw new IllegalStateException("Java 8 SHA-256 provider unavailable", impossibleInJava8);
        }
    }

    /** Fixed-workspace forward pass; requires finite, normalized feature arrays. */
    boolean infer(float[] inputs, float[] hidden, float[] output) {
        if (inputs == null || inputs.length != INPUTS
                || hidden == null || hidden.length != HIDDEN
                || output == null || output.length != OUTPUTS) return false;
        for (float input : inputs)
            if (!Float.isFinite(input) || Math.abs(input) > 1.000001f) return false;

        for (int node = 0; node < HIDDEN; node++) {
            int at = node * (INPUTS + 1);
            double sum = parameters[at + INPUTS];
            for (int i = 0; i < INPUTS; i++) sum += parameters[at + i] * inputs[i];
            hidden[node] = (float) Math.tanh(sum);
        }
        int offset = HIDDEN * (INPUTS + 1);
        for (int node = 0; node < OUTPUTS; node++) {
            int at = offset + node * (HIDDEN + 1);
            double sum = parameters[at + HIDDEN];
            for (int i = 0; i < HIDDEN; i++) sum += parameters[at + i] * hidden[i];
            output[node] = (float) Math.tanh(sum);
            if (!Float.isFinite(output[node])) return false;
        }
        return true;
    }

    public int recordedFrames() { return recordedFrames; }
    public int manualClicks() { return manualClicks; }
    public int reactions() { return reactions; }
    public int epochs() { return epochs; }
    public float trainingLoss() { return trainingLoss; }
    public float validationLoss() { return validationLoss; }
}
