package me.youngjibbit.core.combat.ai;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Synthetic test weights only: NEVER a playable or bundled trained user model. */
public class NeutronAiCheckpointTest {
    static byte[] fixture(float yawBias, float pitchBias) throws Exception {
        byte[] bytes = new byte[NeutronAiCheckpoint.SERIALIZED_BYTES];
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        b.putInt(0x4e414931); b.putInt(NeutronAiCheckpoint.VERSION);
        b.putInt(NeutronAiCheckpoint.WIDTH); b.putInt(NeutronAiCheckpoint.HISTORY);
        b.putInt(NeutronAiCheckpoint.HIDDEN); b.putInt(NeutronAiCheckpoint.OUTPUTS);
        b.putInt(NeutronAiCheckpoint.PARAMETERS);
        b.putInt(900); b.putInt(45); b.putInt(12); b.putInt(10);
        b.putFloat(.25f); b.putFloat(.34f);
        float[] weights = new float[NeutronAiCheckpoint.PARAMETERS];
        int out = NeutronAiCheckpoint.HIDDEN * (NeutronAiCheckpoint.INPUTS + 1);
        weights[out + NeutronAiCheckpoint.HIDDEN] = yawBias;
        weights[out + (NeutronAiCheckpoint.HIDDEN + 1) + NeutronAiCheckpoint.HIDDEN] = pitchBias;
        for (float weight : weights) b.putFloat(weight);
        assertEquals(NeutronAiCheckpoint.SERIALIZED_BYTES -
                NeutronAiCheckpoint.SHA_BYTES, b.position());
        seal(bytes);
        return bytes;
    }

    static void seal(byte[] bytes) throws Exception {
        int end = bytes.length - NeutronAiCheckpoint.SHA_BYTES;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes, 0, end);
        byte[] hash = digest.digest();
        System.arraycopy(hash, 0, bytes, end, hash.length);
    }

    @Test public void finiteSyntheticModelDecodesAndUsesOnlyTwoRotationHeads() throws Exception {
        NeutronAiCheckpoint model = NeutronAiCheckpoint.decode(fixture(2, -1));
        assertNotNull(model);
        assertEquals(900, model.recordedFrames());
        assertEquals(45, model.manualClicks());
        assertEquals(12, model.reactions());
        assertEquals(10, model.epochs());
        assertEquals(.25f, model.trainingLoss(), 0);
        assertEquals(.34f, model.validationLoss(), 0);
        float[] x = new float[NeutronAiCheckpoint.INPUTS];
        float[] hidden = new float[NeutronAiCheckpoint.HIDDEN];
        float[] out = new float[NeutronAiCheckpoint.OUTPUTS];
        assertTrue(model.infer(x, hidden, out));
        assertEquals(Math.tanh(2), out[0], 1e-6);
        assertEquals(Math.tanh(-1), out[1], 1e-6);
        assertEquals(2610, NeutronAiCheckpoint.PARAMETERS);
        assertEquals(2592, NeutronAiCheckpoint.MULTIPLY_ADDS);
        assertTrue(NeutronAiCheckpoint.SERIALIZED_BYTES < 16 * 1024);
    }

    @Test public void absentOversizedAndTruncatedCheckpointsFailClosed() throws Exception {
        byte[] valid = fixture(1, 1);
        assertNull(NeutronAiCheckpoint.decode(null));
        assertNull(NeutronAiCheckpoint.decode(new byte[0]));
        assertNull(NeutronAiCheckpoint.decode(new byte[1024 * 1024]));
        assertNull(NeutronAiCheckpoint.decode(java.util.Arrays.copyOf(valid, valid.length - 1)));
        assertNull(NeutronAiCheckpoint.decode(java.util.Arrays.copyOf(valid, valid.length + 1)));
        byte[] corrupt = valid.clone();
        corrupt[85] ^= 1;
        assertNull(NeutronAiCheckpoint.decode(corrupt));
    }

    @Test public void wrongVersionDimensionsOrDataClaimsFailClosedEvenWithValidHash() throws Exception {
        int[][] invalid = {
                {0, 0x1234}, {4, 9}, {8, 39}, {12, 1}, {16, 128},
                {20, 10}, {24, 99}, {28, 599}, {28, 60001},
                {32, 14}, {32, 100000}, {36, 2}, {36, 9000},
                {40, 0}, {40, 1001}
        };
        for (int[] mutation : invalid) {
            byte[] bytes = fixture(.7f, .2f);
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(mutation[0], mutation[1]);
            seal(bytes);
            assertNull("invalid field at byte " + mutation[0], NeutronAiCheckpoint.decode(bytes));
        }
    }

    @Test public void nonfiniteOrOutOfRangeWeightsAndLossesReject() throws Exception {
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -Float.MAX_VALUE, 21}) {
            for (int byteOffset : new int[]{44, 48}) {
                byte[] bytes = fixture(1, 1);
                ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putFloat(byteOffset, invalid);
                seal(bytes);
                assertNull("nonfinite/invalid loss at " + byteOffset,
                        NeutronAiCheckpoint.decode(bytes));
            }
        }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -Float.MAX_VALUE, 9}) {
            for (int byteOffset : new int[]{NeutronAiCheckpoint.HEADER_BYTES,
                    NeutronAiCheckpoint.HEADER_BYTES + (NeutronAiCheckpoint.PARAMETERS - 1) * 4}) {
                byte[] bytes = fixture(1, 1);
                ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putFloat(byteOffset, invalid);
                seal(bytes);
                assertNull("nonfinite/invalid weight at " + byteOffset,
                        NeutronAiCheckpoint.decode(bytes));
            }
        }
    }

    @Test public void malformedFeatureArrayCannotReachInference() throws Exception {
        NeutronAiCheckpoint m = NeutronAiCheckpoint.decode(fixture(1, 1));
        assertNotNull(m);
        float[] x = new float[NeutronAiCheckpoint.INPUTS], h = new float[16], out = new float[2];
        assertFalse(m.infer(new float[16], h, out));
        assertFalse(m.infer(x, new float[15], out));
        assertFalse(m.infer(x, h, new float[1]));
        x[2] = Float.NaN;
        assertFalse(m.infer(x, h, out));
        x[2] = 2;
        assertFalse(m.infer(x, h, out));
    }

    @Test public void decodingDoesNotRetainMutableSourceArray() throws Exception {
        byte[] bytes = fixture(1.4f, .2f);
        NeutronAiCheckpoint model = NeutronAiCheckpoint.decode(bytes);
        assertNotNull(model);
        java.util.Arrays.fill(bytes, (byte) 0);
        float[] x = new float[NeutronAiCheckpoint.INPUTS];
        float[] h = new float[NeutronAiCheckpoint.HIDDEN];
        float[] y = new float[2];
        assertTrue(model.infer(x, h, y));
        assertEquals(Math.tanh(1.4), y[0], 1e-6);
    }
}
