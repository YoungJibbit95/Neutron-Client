package me.neutron.testserver;

/** The two bot roles supported by the local test server. */
public enum BotType {
    /** Static, immortal target for combat/rotation/reach/backtrack tests. */
    COMBAT,
    /** Static bot that sends swing, hurt and knockback packets to a nearby player. */
    VELOCITY;

    public static BotType parse(String value, BotType fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("ATTACK".equals(normalized) || "KB".equals(normalized) || "JUMPRESET".equals(normalized)) {
            return VELOCITY;
        }
        for (BotType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return fallback;
    }
}
