package me.youngjibbit.core.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import me.youngjibbit.event.motion.EventMotion;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.ref.WeakReference;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

public final class RotationManager {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final RaycastService rays=new RaycastService(mc);
    private final Map<String, RotationState> states = new HashMap<>();
    private final Map<String,Long> trajectoryEpochs = new HashMap<>();
    private final LongSupplier clock;
    private final long seed;
    public RotationManager() { this(System::nanoTime,ThreadLocalRandom.current().nextLong()); }
    public RotationManager(LongSupplier clock,long seed) { this.clock=clock;this.seed=seed; }
    public static final int AIM_ASSIST_PRIORITY = 10;
    public static final int AURA_PRIORITY = 20;
    public static final int SCAFFOLD_PRIORITY = 30;
    private boolean collecting, accepted, previousAccepted, hasEffective;
    private long sequence;
    private float baseYaw, basePitch, effectiveYaw, effectivePitch;
    private String owner;
    private WeakReference<Object> connection = new WeakReference<>(null);
    public void bindConnection(Object current) {
        if (connection.get()!=current) { reset(); connection=new WeakReference<>(current); }
    }
    public boolean isConnection(Object current) { return connection.get()==current; }
    public void invalidateConnection(Object current) { if (isConnection(current)) { reset(); connection.clear(); } }
    private Request winner;
    private WeakReference<Object> motionPlayer = new WeakReference<>(null);
    private WeakReference<Object> motionWorld = new WeakReference<>(null);

    public void beginMotion(Object player, Object world, float cameraYaw, float cameraPitch) {
        if (motionPlayer.get() != player || motionWorld.get() != world) {
            reset();
            motionPlayer = new WeakReference<>(player);
            motionWorld = new WeakReference<>(world);
        }
        beginMotion(cameraYaw, cameraPitch);
    }

    public void beginMotion(float cameraYaw, float cameraPitch) {
        // Keep the observed packet values for finishMotion's rejection, but never smooth from them.
        boolean usableBase = hasEffective && Float.isFinite(effectiveYaw) && Float.isFinite(effectivePitch)
                && effectivePitch >= -90 && effectivePitch <= 90;
        baseYaw = usableBase ? effectiveYaw : cameraYaw;
        basePitch = usableBase ? effectivePitch : cameraPitch;
        collecting = true;
        previousAccepted = accepted;
        accepted = false;
        winner = null;
        sequence++;
    }

    public void request(String source, int priority, float yaw, float pitch) {
        offer(new Request(source,priority,yaw,pitch,false,0,0,()->true));
    }

    public void requestVisible(String source,int priority,float yaw,float pitch,float cameraYaw,float cameraPitch,BooleanSupplier valid) {
        offer(new Request(source,priority,yaw,pitch,true,cameraYaw,cameraPitch,valid));
    }

    private void offer(Request request) {
        if (!collecting || request.source==null || !Float.isFinite(request.yaw) || !Float.isFinite(request.pitch)) return;
        request.pitch=clamp(request.pitch,-90,90);
        if (winner==null || request.priority>winner.priority
                || (request.priority==winner.priority && request.source.compareTo(winner.source)<0)) winner=request;
    }

    public void resolve(EventMotion event) {
        if (winner==null) return;
        if (winner.visible) {
            String confirmed=getConfirmedOwner();
            if (mc==null || mc.thePlayer==null || motionPlayer.get()!=mc.thePlayer || motionWorld.get()!=mc.theWorld
                    || mc.currentScreen!=null || !mc.inGameHasFocus || mc.getRenderViewEntity()!=mc.thePlayer
                    || "aura".equals(confirmed) || "scaffold".equals(confirmed) || !winner.valid.getAsBoolean()) {
                winner=null;return;
            }
            // Add assistance to the latest camera; a later manual mouse delta must survive arbitration.
            mc.thePlayer.rotationYaw+=MathHelper.wrapAngleTo180_float(winner.yaw-winner.cameraYaw);
            mc.thePlayer.rotationPitch=clamp(mc.thePlayer.rotationPitch+winner.pitch-winner.cameraPitch,-90,90);
            winner.yaw=mc.thePlayer.rotationYaw;winner.pitch=mc.thePlayer.rotationPitch;
        }
        event.setRotations(winner.yaw,winner.pitch);
    }

    public void finishMotion(float yaw, float pitch, boolean packetAccepted) {
        collecting = false;
        accepted = packetAccepted && Float.isFinite(yaw) && Float.isFinite(pitch) && pitch >= -90 && pitch <= 90;
        if (!accepted) return;
        hasEffective = true;
        effectiveYaw = yaw;
        effectivePitch = clamp(pitch, -90, 90);
        owner = winner != null && Math.abs(MathHelper.wrapAngleTo180_float(winner.yaw - yaw)) < .001f
                && Math.abs(winner.pitch - pitch) < .001f ? winner.source : null;
        if (owner != null) {
            RotationState state = state(owner);
            state.yaw = effectiveYaw;
            state.pitch = effectivePitch;
            state.active = true;
        }
    }

    public boolean isOwner(String source) { return hasEffective && source.equals(owner); }
    /** During PRE, only the last accepted owner is usable; a pending winner grants no correction. */
    public String getConfirmedOwner() {
        return canUseRotation(collecting) ? owner : null;
    }
    public String getConfirmedOwner(Object player, Object world) {
        return motionPlayer.get() == player && motionWorld.get() == world ? getConfirmedOwner() : null;
    }
    public String getConfirmedOwner(Object player, Object world, Object playConnection) {
        return playConnection != null && isConnection(playConnection) ? getConfirmedOwner(player,world) : null;
    }
    /** PRE consumes the preceding accepted movement; POST consumes the current movement result. */
    public boolean canUseRotation(boolean pre) {
        return hasEffective && (pre ? previousAccepted : accepted)
                && Float.isFinite(effectiveYaw) && Float.isFinite(effectivePitch);
    }
    public boolean isMotionAccepted() { return accepted; }
    public long getMotionSequence() { return sequence; }
    public float getEffectiveYaw() { return effectiveYaw; }
    public float getEffectivePitch() { return effectivePitch; }
    public boolean hasEffectiveRotation() { return hasEffective; }

    public boolean matchesEffective(float yaw, float pitch) {
        return accepted && Math.abs(MathHelper.wrapAngleTo180_float(effectiveYaw-yaw)) < .001f
                && Math.abs(effectivePitch-pitch) < .001f;
    }

    /** Also observe replays/other movement emitted by attack listeners, not just the motion event. */
    public void observeOutboundRotation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) {
            accepted = false;
            previousAccepted = false;
            owner = null;
        } else if (!hasEffective || Math.abs(MathHelper.wrapAngleTo180_float(effectiveYaw-yaw)) >= .001f
                || Math.abs(effectivePitch-pitch) >= .001f) {
            owner = null;
        }
        hasEffective = true;
        effectiveYaw = yaw;
        effectivePitch = pitch;
    }

    /** The numeric step is shared by combat/placement and can be tested without Minecraft. */
    public float[] step(String source, float yaw, float pitch, float targetYaw, float targetPitch,
                        float yawStep, float pitchStep, float sensitivity) {
        RotationState state = state(source);
        float fromYaw = collecting ? baseYaw : state.active ? state.yaw : yaw;
        float fromPitch = collecting ? basePitch : state.active ? state.pitch : pitch;
        float[] next = smooth(fromYaw, fromPitch, targetYaw, targetPitch, yawStep, pitchStep);
        next = quantize(next[0], next[1], fromYaw, fromPitch, sensitivity);
        if (!collecting) {
            state.yaw = next[0]; state.pitch = next[1]; state.active = true;
        }
        return next;
    }

    public float[] step(String source, float yaw, float pitch, float targetYaw, float targetPitch, float yawStep, float pitchStep) {
        return step(source, yaw, pitch, targetYaw, targetPitch, yawStep, pitchStep, mc.gameSettings.mouseSensitivity);
    }

    private Vec3 baseAimPoint(EntityLivingBase target,boolean best,AxisAlignedBB box) {
        Vec3 eyes=mc.thePlayer.getPositionEyes(1);
        if(best) return new Vec3(clampInside(eyes.xCoord,box.minX,box.maxX,.03),
                clampInside(eyes.yCoord,box.minY,box.maxY,.03),clampInside(eyes.zCoord,box.minZ,box.maxZ,.03));
        return new Vec3(clampInside(target.posX,box.minX,box.maxX,.001),
                clampInside(target.posY+target.getEyeHeight()*.82,box.minY,box.maxY,.001),
                clampInside(target.posZ,box.minZ,box.maxZ,.001));
    }

    public float[] rotationsTo(Entity target, Vec3 aimPoint) {
        if (target == null || mc.thePlayer == null) {
            return null;
        }

        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 hit = aimPoint != null ? aimPoint : new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        return angles(eyes,hit);
    }
    private float[] angles(Vec3 eyes,Vec3 hit) {
        double diffX = hit.xCoord - eyes.xCoord;
        double diffY = hit.yCoord - eyes.yCoord;
        double diffZ = hit.zCoord - eyes.zCoord;
        double distance = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, distance));
        return new float[]{yaw, pitch};
    }

    public float[] smooth(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float maxYawStep, float maxPitchStep) {
        float smoothedYaw = me.youngjibbit.utils.rotations.RUtil.smoothRotation(currentYaw,targetYaw,maxYawStep);
        float smoothedPitch = currentPitch + clamp(clamp(targetPitch, -90, 90) - currentPitch, -maxPitchStep, maxPitchStep);
        smoothedPitch = clamp(smoothedPitch, -90.0f, 90.0f);
        return new float[]{smoothedYaw, smoothedPitch};
    }

    public float[] applyGcd(float yaw, float pitch, float previousYaw, float previousPitch) {
        return quantize(yaw, pitch, previousYaw, previousPitch, mc.gameSettings.mouseSensitivity);
    }

    private float[] quantize(float yaw, float pitch, float previousYaw, float previousPitch, float mouseSensitivity) {
        float sensitivity = (mouseSensitivity * 0.6F + 0.2F);
        float gcd = sensitivity * sensitivity * sensitivity * 1.2F;
        float yawDelta = yaw - previousYaw;
        float pitchDelta = pitch - previousPitch;

        // Preserve a final sub-step instead of truncating it to zero forever.
        yaw = previousYaw + (Math.abs(yawDelta) < gcd ? yawDelta : yawDelta - yawDelta % gcd);
        pitch = previousPitch + (Math.abs(pitchDelta) < gcd ? pitchDelta : pitchDelta - pitchDelta % gcd);
        return new float[]{yaw, clamp(pitch, -90.0f, 90.0f)};
    }

    public float[] calculateRotations(String source,float cameraYaw,float cameraPitch,EntityLivingBase target,
                                      boolean bestHitVec,RotationProfile profile,boolean visible) {
        if(target==null || mc==null || mc.thePlayer==null) return null;
        RotationState state=state(source);
        AxisAlignedBB box=target.getEntityBoundingBox();
        Vec3 feet=new Vec3(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        if(!visible && (profile.smartAim || profile.stopOnTarget || profile.heuristics))feet=mc.thePlayer.getPlannedMovementPosition(feet.xCoord,feet.yCoord,feet.zCoord);
        Vec3 eyes=feet.addVector(0,mc.thePlayer.getEyeHeight(),0);
        float fromYaw=visible?cameraYaw:collecting?baseYaw:state.active?state.yaw:cameraYaw;
        float fromPitch=visible?cameraPitch:collecting?basePitch:state.active?state.pitch:cameraPitch;
        if(profile.stopOnTarget && rays.canHitFrom(feet,target,fromYaw,fromPitch,profile.reach,profile.throughWalls)) {
            state.trajectory.pause(target);return new float[]{fromYaw,fromPitch};
        }
        Vec3 base=baseAimPoint(target,bestHitVec,box);
        if(profile.smartAim || profile.heuristics)base=visibleAimPoint(target,box,base,feet,fromYaw,fromPitch,profile,rays);
        if(base==null)return null;
        Vec3 aim=state.trajectory.point(target,box,base,profile);
        if((profile.smartAim || profile.heuristics) && !hitsPoint(rays,feet,target,aim,profile))aim=base;
        float[] desired=angles(eyes,aim);
        return profileStep(source,cameraYaw,cameraPitch,target,desired[0],desired[1],eyes.distanceTo(aim),profile,visible);
    }

    private boolean hitsPoint(RaycastService rays,Vec3 feet,EntityLivingBase target,Vec3 point,RotationProfile profile) {
        float[] direction=angles(feet.addVector(0,mc.thePlayer.getEyeHeight(),0),point);
        return rays.canHitFrom(feet,target,direction[0],direction[1],profile.reach,profile.throughWalls);
    }
    private Vec3 visibleAimPoint(EntityLivingBase target,AxisAlignedBB box,Vec3 base,Vec3 feet,float yaw,float pitch,RotationProfile profile,RaycastService rays) {
        Vec3 eyes=feet.addVector(0,mc.thePlayer.getEyeHeight(),0);
        List<Vec3> candidates=new ArrayList<>();candidates.add(base);
        for(double x:axisSamples(box.minX,box.maxX))
            for(double y:axisSamples(box.minY,box.maxY))
                for(double z:axisSamples(box.minZ,box.maxZ))
                    candidates.add(new Vec3(x,y,z));
        candidates.sort(Comparator.comparingDouble(point->{float[] a=angles(eyes,point);double dy=MathHelper.wrapAngleTo180_float(a[0]-yaw),dp=a[1]-pitch;return dy*dy+dp*dp;}));
        for(Vec3 candidate:candidates)if(hitsPoint(rays,feet,target,candidate,profile))return candidate;
        return null;
    }
    private double[] axisSamples(double min,double max) {
        double inset=Math.min(.001,(max-min)/4);
        return new double[]{min+inset,(min+max)/2,max-inset};
    }

    public float[] calculatePlanRotations(String source,float cameraYaw,float cameraPitch,Object target,float targetYaw,float targetPitch,double distance,RotationProfile profile) {
        return profileStep(source,cameraYaw,cameraPitch,target,targetYaw,targetPitch,distance,profile,false);
    }

    public Vec3 faceAimPoint(String source,Object target,AxisAlignedBB box,net.minecraft.util.EnumFacing face,Vec3 base,RotationProfile profile) {
        Vec3 point=state(source).trajectory.point(target,box,base,profile);
        switch(face) {
            case WEST:return new Vec3(box.minX,point.yCoord,point.zCoord);
            case EAST:return new Vec3(box.maxX,point.yCoord,point.zCoord);
            case DOWN:return new Vec3(point.xCoord,box.minY,point.zCoord);
            case UP:return new Vec3(point.xCoord,box.maxY,point.zCoord);
            case NORTH:return new Vec3(point.xCoord,point.yCoord,box.minZ);
            default:return new Vec3(point.xCoord,point.yCoord,box.maxZ);
        }
    }

    private float[] profileStep(String source,float cameraYaw,float cameraPitch,Object target,float targetYaw,float targetPitch,double distance,RotationProfile profile,boolean visible) {
        RotationState state=state(source);
        float fromYaw=visible?cameraYaw:collecting?baseYaw:state.active?state.yaw:cameraYaw;
        float fromPitch=visible?cameraPitch:collecting?basePitch:state.active?state.pitch:cameraPitch;
        float[] next=state.trajectory.advance(target,fromYaw,fromPitch,targetYaw,targetPitch,distance,profile);
        return quantize(next[0],next[1],fromYaw,fromPitch,mc.gameSettings.mouseSensitivity);
    }

    public void reset(String source) {
        if (source == null) {
            return;
        }
        states.remove(source);
        if (source.equals(owner)) owner = null;
        if (winner != null && source.equals(winner.source)) winner = null;
    }

    public void reset() {
        RotationCorrections.reset();
        states.clear();
        owner = null;
        winner = null;
        accepted = false;
        hasEffective = false;
        previousAccepted = false;
        motionPlayer.clear();
        motionWorld.clear();
    }

    private double clampInside(double value, double min, double max, double inset) {
        double innerMin = min + inset;
        double innerMax = max - inset;
        if (innerMin > innerMax) {
            innerMin = min;
            innerMax = max;
        }
        return MathHelper.clamp_double(value, innerMin, innerMax);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private RotationState state(String source) {
        String key = source == null ? "shared" : source;
        RotationState state = states.get(key);
        if (state == null) {
            long epoch=trajectoryEpochs.merge(key,1L,Long::sum);
            state = new RotationState(new RotationTrajectory(clock,(int)(seed ^ sourceHash(key) ^ epoch*0x54ab183dL)));
            states.put(key, state);
        }
        return state;
    }

    private static long sourceHash(String key) { return key.hashCode()*0x9e3779b97f4a7c15L; }

    private static final class RotationState {
        final RotationTrajectory trajectory;
        RotationState(RotationTrajectory trajectory) { this.trajectory=trajectory; }
        private float yaw;
        private float pitch;
        private boolean active;

    }

    private static final class Request {
        final String source;
        final int priority;
        float yaw,pitch;
        final boolean visible;
        final float cameraYaw,cameraPitch;
        final BooleanSupplier valid;
        Request(String source,int priority,float yaw,float pitch,boolean visible,float cameraYaw,float cameraPitch,BooleanSupplier valid) {
            this.source=source;this.priority=priority;this.yaw=yaw;this.pitch=pitch;
            this.visible=visible;this.cameraYaw=cameraYaw;this.cameraPitch=cameraPitch;this.valid=valid;
        }
    }
}
