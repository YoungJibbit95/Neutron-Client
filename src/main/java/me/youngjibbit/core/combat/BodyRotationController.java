package me.youngjibbit.core.combat;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Presentation state only; entity angles and the accepted rotation are never mutated. */
public final class BodyRotationController {
    public enum Mode {
        VANILLA("Vanilla"), DIRECT("Direkt"), NATURAL("Natürlich");
        public final String label;
        Mode(String label) { this.label = label; }
        public static Mode fromSetting(String value) {
            for (Mode mode : values()) if (mode.label.equalsIgnoreCase(value)) return mode;
            throw new IllegalArgumentException("Unknown body rotation mode.");
        }
    }

    public static final float MAX_HEAD_BODY_ANGLE = 75;
    public static final double TIME_CONSTANT_SECONDS = .18;
    public static final class Pose {
        public final float bodyYaw, headYaw, headPitch;
        Pose(float bodyYaw, float headYaw, float headPitch) {
            this.bodyYaw = bodyYaw; this.headYaw = headYaw; this.headPitch = headPitch;
        }
    }

    private WeakReference<Object> player = new WeakReference<>(null), world = new WeakReference<>(null);
    private String owner;
    private long lastNanos;
    private float bodyYaw;

    private long sampledFrame=Long.MIN_VALUE, frameTime;
    private WeakReference<Object> framePlayer=new WeakReference<>(null), frameWorld=new WeakReference<>(null);
    private String frameOwner;
    private Mode frameMode;
    private float frameYaw,framePitch;
    private Pose framePose;

    /** Multiple render passes in one frame share the same smoothing time and pose. */
    public Pose sampleFrame(long frame, Object player, Object world, String owner, Mode mode,
                            float yaw, float pitch, float vanilla, long now) {
        boolean same=sampledFrame==frame && framePlayer.get()==player && frameWorld.get()==world
                && Objects.equals(frameOwner,owner) && frameMode==mode;
        if (same && frameYaw==yaw && framePitch==pitch) return framePose;
        long time=same ? frameTime : now;
        Pose pose=sample(player,world,owner,mode,yaw,pitch,vanilla,time);
        sampledFrame=frame;frameTime=time;framePlayer=new WeakReference<>(player);frameWorld=new WeakReference<>(world);
        frameOwner=owner;frameMode=mode;frameYaw=yaw;framePitch=pitch;framePose=pose;
        return pose;
    }

    public Pose sample(Object player, Object world, String owner, Mode mode, float targetYaw, float targetPitch,
                       float vanillaBodyYaw, long nowNanos) {
        if (player == null || world == null || owner == null || mode == Mode.VANILLA
                || !Float.isFinite(targetYaw) || !Float.isFinite(targetPitch) || !Float.isFinite(vanillaBodyYaw)) {
            reset();
            return null;
        }
        if (mode == Mode.DIRECT) {
            reset();
            return new Pose(targetYaw, targetYaw, clamp(targetPitch, -90, 90));
        }
        if (this.player.get() != player || this.world.get() != world || !Objects.equals(this.owner, owner)) {
            reset();
            this.player = new WeakReference<>(player);
            this.world = new WeakReference<>(world);
            this.owner = owner;
            bodyYaw = vanillaBodyYaw;
            lastNanos = nowNanos;
        }
        double seconds = Math.max(0, (nowNanos - lastNanos) * 1.0e-9);
        lastNanos = nowNanos;
        // Bound the initial error first; exponential decay then composes across frame subdivisions.
        float error = clamp(MoveFixController.wrap(bodyYaw - targetYaw), -MAX_HEAD_BODY_ANGLE, MAX_HEAD_BODY_ANGLE);
        bodyYaw = targetYaw + (float) (error * Math.exp(-seconds / TIME_CONSTANT_SECONDS));
        return new Pose(bodyYaw, targetYaw, clamp(targetPitch, -90, 90));
    }

    public void reset(String source) { if (Objects.equals(source, owner) || Objects.equals(source,frameOwner)) reset(); }
    public void reset() {
        owner = null;
        player.clear(); world.clear();
        bodyYaw = 0; lastNanos = 0;
        sampledFrame=Long.MIN_VALUE;framePose=null;frameOwner=null;framePlayer.clear();frameWorld.clear();
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
