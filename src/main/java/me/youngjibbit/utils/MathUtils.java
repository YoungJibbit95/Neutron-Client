package me.youngjibbit.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Arrays;

public class MathUtils {

    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static int getRandomInRange(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    public static float getRandomInRange(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }

    public static double getRandomInRange(double min, double max) {
        SecureRandom random = new SecureRandom();
        return random.nextDouble() * (max - min) + min;
    }

    public static double round(double num, double increment) {
        BigDecimal bd = new BigDecimal(num);
        bd = (bd.setScale((int) increment, RoundingMode.HALF_UP));
        return bd.doubleValue();
    }

    public static boolean inBetween(double min, double max, double value) {
        return value >= min && value <= max;
    }

    public static double smoothstep(double a, double b, double t) {
        t = t * t * (3 - 2 * t); // Smoother curve
        return a + (b - a) * t;
    }

    public static float getAdvancedRandom(float min, float max) {
        SecureRandom random = new SecureRandom();

        long finalSeed = System.nanoTime();

        for (int i = 0; i < 3; ++i) {
            long seed = (long) (Math.random() * 1_000_000_000);

            seed ^= (seed << 13);
            seed ^= (seed >>> 17);
            seed ^= (seed << 15);

            finalSeed += seed;
        }

        random.setSeed(finalSeed);

        return random.nextFloat() * (max - min) + min;
    }

    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }


    public static double[] yawPos(final double value) {
        return yawPos(Minecraft.getMinecraft().thePlayer.rotationYaw * net.minecraft.util.MathHelper.deg2Rad, value);
    }

    public static double[] yawPos(final float yaw, final double value) {
        return new double[]{-net.minecraft.util.MathHelper.sin(yaw) * value, MathHelper.cos(yaw) * value};
    }

    public static double lerp(final double a, final double b, final double c) {
        return a + c * (b - a);
    }

    public float lerp(final float a, final float b, final float c) {
        return a + c * (b - a);
    }

    public static Double interpolate(final double oldValue, final double newValue, final double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static float interpolateFloat(final float oldValue, final float newValue, final double interpolationValue){
        return interpolate(oldValue, newValue, (float) interpolationValue).floatValue();
    }

    public static int interpolateInt(final int oldValue, final int newValue, final double interpolationValue){
        return interpolate(oldValue, newValue, (float) interpolationValue).intValue();
    }

    public static float calculateGaussianValue(final float x, final float sigma) {
        final double output = 1.0 / Math.sqrt(2.0 * Math.PI * (sigma * sigma));
        return (float) (output * Math.exp(-(x * x) / (2.0 * (sigma * sigma))));
    }

    public static float getRandomFloat(final float max, final float min) {
        return SECURE_RANDOM.nextFloat() * (max - min) + min;
    }

    public static int getNumberOfDecimalPlace(final double value) {
        final BigDecimal bigDecimal = new BigDecimal(value);
        return Math.max(0, bigDecimal.stripTrailingZeros().scale());
    }

    public static double clamp(double value, final double min, final double max) {
        value = Math.max(min, value);
        value = Math.min(max, value);
        return value;
    }

    public static double roundToHalf(double d) {
        return Math.round(d * 2) / 2.0;
    }

    public static int nextInt(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    public static <T> void reverse(T[] array) {
        int left = 0;
        int right = array.length - 1;

        while (left < right) {
            T temp = array[left];
            array[left] = array[right];
            array[right] = temp;
            left++;
            right--;
        }
    }

    public static double easeInQuad(double delta, double start, double end) {
        return start + (end - start) * Math.pow(delta, 2);
    }
    public static double easeOutQuad(double delta, double start, double end) {
        return start + (end - start) * (1 - Math.pow(1 - delta, 2));
    }
    public static double easeInOutQuad(double delta, double start, double end) {
        if (delta < 0.5) {
            return start + (end - start) * 2 * Math.pow(delta, 2);
        } else {
            return start + (end - start) * (1 - 2 * Math.pow(1 - delta, 2));
        }
    }
    public static double easeInCubic(double delta, double start, double end) {
        return start + (end - start) * Math.pow(delta, 3);
    }
    public static double easeOutCubic(double delta, double start, double end) {
        return start + (end - start) * (1 - Math.pow(1 - delta, 3));
    }
    public static double easeInOutCubic(double delta, double start, double end) {
        if (delta < 0.5) {
            return start + (end - start) * 4 * Math.pow(delta, 3);
        } else {
            return start + (end - start) * (1 - 4 * Math.pow(1 - delta, 3));
        }
    }
    public static double easeInBounce(double delta, double start, double end) {
        return start + (end - start) * (1 - easeOutBounce(1 - delta, 0, 1));
    }

    private static double easeOutBounce(double delta, double start, double end) {
        if (delta < (1 / 2.75)) {
            return end * (7.5625 * delta * delta) + start;
        } else if (delta < (2 / 2.75)) {
            return end * (7.5625 * (delta -= (1.5 / 2.75)) * delta + 0.75) + start;
        } else if (delta < (2.5 / 2.75)) {
            return end * (7.5625 * (delta -= (2.25 / 2.75)) * delta + 0.9375) + start;
        } else {
            return end * (7.5625 * (delta -= (2.625 / 2.75)) * delta + 0.984375) + start;
        }
    }
    public static double easeInOutBounce(double delta, double start, double end) {
        if (delta < 0.5) {
            return easeInBounce(delta * 2, 0, end - start) * 0.5 + start;
        } else {
            return easeOutBounce(delta * 2 - 1, 0, end - start) * 0.5 + (end - start) * 0.5 + start;
        }
    }
}
