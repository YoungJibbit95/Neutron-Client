package me.youngjibbit.core.activity;

/** Immutable evidence of one successfully completed module lifecycle transition. */
public final class ModuleToggleSnapshot {
    public static final long MAX_AGE_NANOS = 1_900_000_000L;

    private final long sequence;
    private final long observedAtNanos;
    private final String moduleId;
    private final String moduleName;
    private final boolean enabled;

    ModuleToggleSnapshot(long sequence, long observedAtNanos, String moduleId, String moduleName, boolean enabled) {
        if (sequence <= 0 || moduleId == null || moduleName == null)
            throw new IllegalArgumentException("A completed transition needs a sequence and module identity.");
        this.sequence = sequence;
        this.observedAtNanos = observedAtNanos;
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.enabled = enabled;
    }

    public long getSequence() { return sequence; }
    public long getObservedAtNanos() { return observedAtNanos; }
    public String getModuleId() { return moduleId; }
    public String getModuleName() { return moduleName; }
    public boolean isEnabled() { return enabled; }

    public boolean isFresh(long now) {
        long age = now - observedAtNanos;
        return age >= 0 && age <= MAX_AGE_NANOS;
    }
}
