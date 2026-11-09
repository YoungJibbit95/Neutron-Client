package me.neutron.testserver;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable-by-convention description of one bot.  Commands mutate the public
 * fields through the owning {@link ManagedBot}; the manager saves the current
 * values back to config.yml.
 */
public final class BotDefinition {

    public BotType type = BotType.COMBAT;
    public String name = "bot";
    public double x;
    public double y = 65.0D;
    public double z;
    public float yaw;
    public float pitch;

    /** NONE, SWORD, ARMOR, FULL, IRON_FULL. */
    public String equipment = "NONE";

    /** Percent of the vanilla 1.8 knockback impulse (0.4 blocks/tick). */
    public double horizontalPercent = 100.0D;
    public double verticalPercent = 100.0D;

    /** Attack interval in milliseconds.  Clamped to at least 50 ms (one tick). */
    public long intervalMs = 500L;

    /** Maximum distance to the target, in blocks. */
    public double range = 3.0D;

    public boolean swing = true;
    public boolean hurt = true;

    /** Optional exact player name.  Empty means nearest player in range. */
    public String target = "";

    public static BotDefinition fromMap(Map<?, ?> map, BotDefinition fallback) {
        BotDefinition out = new BotDefinition();
        if (fallback != null) {
            out.copyFrom(fallback);
        }
        if (map == null) {
            return out;
        }
        out.type = BotType.parse(string(map.get("type"), out.type.name()), out.type);
        out.name = sanitizeName(string(map.get("name"), out.name));
        out.x = number(map.get("x"), out.x);
        out.y = number(map.get("y"), out.y);
        out.z = number(map.get("z"), out.z);
        out.yaw = (float) number(map.get("yaw"), out.yaw);
        out.pitch = (float) number(map.get("pitch"), out.pitch);
        out.equipment = string(map.get("equipment"), out.equipment).toUpperCase(Locale.ROOT);
        out.horizontalPercent = clamp(number(map.get("horizontal-percent"), out.horizontalPercent), 0.0D, 1000.0D);
        out.verticalPercent = clamp(number(map.get("vertical-percent"), out.verticalPercent), 0.0D, 1000.0D);
        out.intervalMs = clamp((long) number(map.get("interval-ms"), out.intervalMs), 50L, 5000L);
        out.range = clamp(number(map.get("range"), out.range), 1.0D, 16.0D);
        out.swing = bool(map.get("swing"), out.swing);
        out.hurt = bool(map.get("hurt"), out.hurt);
        out.target = string(map.get("target"), out.target).trim();
        return out;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("type", type.name());
        map.put("name", name);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", yaw);
        map.put("pitch", pitch);
        map.put("equipment", equipment);
        if (type == BotType.VELOCITY) {
            map.put("horizontal-percent", horizontalPercent);
            map.put("vertical-percent", verticalPercent);
            map.put("interval-ms", intervalMs);
            map.put("range", range);
            map.put("swing", swing);
            map.put("hurt", hurt);
            map.put("target", target);
        }
        return map;
    }

    public void copyFrom(BotDefinition other) {
        this.type = other.type;
        this.name = other.name;
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        this.yaw = other.yaw;
        this.pitch = other.pitch;
        this.equipment = other.equipment;
        this.horizontalPercent = other.horizontalPercent;
        this.verticalPercent = other.verticalPercent;
        this.intervalMs = other.intervalMs;
        this.range = other.range;
        this.swing = other.swing;
        this.hurt = other.hurt;
        this.target = other.target;
    }

    public String displayName() {
        return "\u00A7b[BOT] \u00A7f" + name;
    }

    public String describe() {
        if (type == BotType.COMBAT) {
            return name + " [COMBAT, immortal, static] " + format(x) + "/" + format(y) + "/" + format(z)
                    + " equipment=" + equipment;
        }
        return name + " [VELOCITY, static] " + format(x) + "/" + format(y) + "/" + format(z)
                + " kb=" + format(horizontalPercent) + "%/" + format(verticalPercent) + "%"
                + " interval=" + intervalMs + "ms range=" + format(range)
                + " swing=" + swing + " hurt=" + hurt
                + (target.isEmpty() ? " target=nearest" : " target=" + target);
    }

    public static String sanitizeName(String input) {
        if (input == null) {
            return "bot";
        }
        String cleaned = input.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isEmpty()) {
            cleaned = "bot";
        }
        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 16);
        }
        return cleaned;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
