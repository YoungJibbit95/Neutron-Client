package me.youngjibbit.module.impl.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.network.DemiseFakeLagMath;
import me.youngjibbit.core.network.DemiseFakeLagPolicy;
import me.youngjibbit.core.network.DemiseFakeLagQueue;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.LivingEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.utils.RenderUtils;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.DemiseSliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongSupplier;

/** Complete FakeLag settings/behavior from DemisePrivate e219a97 (AGPL-3.0), with guarded Neutron transport. */
@ModuleInfo(name = "FakeLag", description = "Demise Pulse and Spoof latency windows with smart range control and real-position rendering", category = Category.Combat)
public final class FakeLag extends Module {
    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Pulse", "Spoof"}, 0);
    private final CheckBoxValue smart = new CheckBoxValue("Smart", this, true);
    private final DemiseSliderValue hurtTimeToAttack = new DemiseSliderValue("HurtTime to register attack", this, 3D, 0, 10, 1);
    private final CheckBoxValue earlyRelease = new CheckBoxValue("Early release", this, false);
    private final DemiseSliderValue attackRange = new DemiseSliderValue("Attack range", this, 4D, 0, 15, .1);
    private final CheckBoxValue alwaysSpoof = new CheckBoxValue("Always spoof", this, false);
    private final DemiseSliderValue searchRange = new DemiseSliderValue("Search range", this, 6D, 1, 15, .1);
    private final DemiseSliderValue recoilTime = new DemiseSliderValue("Recoil time (ms)", this, 50D, 0, 1000, 1);
    private final DemiseSliderValue delayMin = new DemiseSliderValue("Delay (min ms)", this, 100D, 0, 1000, 1);
    private final DemiseSliderValue delayMax = new DemiseSliderValue("Delay (max ms)", this, 100D, 0, 1000, 1);
    private final DemiseSliderValue opponentDelay = new DemiseSliderValue("Delay on opponent attack", this, 200D, 0, 500, 1);
    private final ComboValue keepRange = new ComboValue("Keep range mode", this, new String[]{"None", "WTap", "Timer down"}, 0);
    private final DemiseSliderValue timer = new DemiseSliderValue("Timer", this, .75, .01, 1, .01);
    private final DemiseSliderValue timerTicks = new DemiseSliderValue("Timer ticks", this, 1D, 1, 10, 1);
    private final CheckBoxValue dynamicHurtTime = new CheckBoxValue("Experimental dynamic hurt time", this, false);
    private final DemiseSliderValue hurtTimeToStop = new DemiseSliderValue("HurtTime to stop (>)", this, 0D, 0, 10, 1);
    private final CheckBoxValue pauseOnBacktrack = new CheckBoxValue("Pause on backtrack", this, false);
    private final CheckBoxValue forceFirstHit = new CheckBoxValue("Force first hit", this, false);
    private final CheckBoxValue realPos = new CheckBoxValue("Display real pos", this, true);
    private final ComboValue renderMode = new ComboValue("Render mode", this, new String[]{"Box", "FakePlayer", "Line"}, 1);
    private final CheckBoxValue onlyOnGround = new CheckBoxValue("Only onGround", this, false);
    private final CheckBoxValue onlyAura = new CheckBoxValue("Only on killAura", this, false);
    private final CheckBoxValue teamCheck = new CheckBoxValue("Team check", this, false);
    private final Minecraft client;
    private final LongSupplier clock;
    private final DemiseFakeLagPolicy policy;
    private final TimerLease timerLease = new TimerLease();
    private final ArrayDeque<PathPoint> path = new ArrayDeque<>();
    private Scene scene;
    private EntityPlayer target;
    private Vec3 serverPosition = new Vec3(0, 0, 0), renderPosition = new Vec3(0, 0, 0);
    private long generation, lastUpdate = Long.MIN_VALUE;
    private WeakReference<Object> budgetPlayer = new WeakReference<>(null), budgetWorld = new WeakReference<>(null);
    private WeakReference<Object> unloadedWorld = new WeakReference<>(null);
    private boolean processing, inSourceUpdate, closing;

    public FakeLag() { this(() -> System.nanoTime() / 1000000L, Minecraft.getMinecraft(), FakeLag::randomDelay); }
    public FakeLag(LongSupplier clock, Minecraft client, DoubleBinaryOperator random) {
        this.clock = java.util.Objects.requireNonNull(clock); this.client = client;
        policy = new DemiseFakeLagPolicy(random);
        hurtTimeToAttack.setVisibility(m -> smart.getValue()); earlyRelease.setVisibility(m -> smart.getValue());
        attackRange.setVisibility(m -> !smart.getValue()); searchRange.setVisibility(m -> !alwaysSpoof.getValue());
        opponentDelay.setVisibility(m -> "Spoof".equals(mode.getValue())); keepRange.setVisibility(m -> smart.getValue());
        timer.setVisibility(m -> "Timer down".equals(keepRange.getValue())); timerTicks.setVisibility(m -> timer.isVisible());
        dynamicHurtTime.setVisibility(m -> smart.getValue()); renderMode.setVisibility(m -> realPos.getValue());
        enableActionSafety(() -> this.client);
    }
    private static double randomDelay(double min, double max) {
        if (min == max) return min;
        return min < 1 && max < 1 ? Math.random() * (max - min) + min : ThreadLocalRandom.current().nextDouble(min, max + 1);
    }
    @Override public void onEnable() { reset(false); policy.reset(clock.getAsLong()); super.onEnable(); }
    @Override public void onDisable() { super.onDisable(); reset(true); }
    @Override public void onValueChange() { if (policy != null) reset(true); }
    @Override public String getInfo() { return isToggled() ? policy.delayMs() + " ms" : ""; }
    public boolean isBlinking() { return isToggled() && policy.blinking() && scene != null && scene.objects(); }
    public Vec3 getServerPosition() { return serverPosition; }
    public EntityPlayer getTarget() { return target; }
    public int queuedPackets() { return scene != null && scene.objects() ? scene.pipeline.fakeLag().size() : 0; }

    @Subscribe private final Listener<WorldEvent> onWorld = new Listener<>(event -> {
        if (event instanceof WorldEvent.Unload) unloadedWorld = new WeakReference<>(event.world);
        else if (event instanceof WorldEvent.Load) unloadedWorld.clear();
        reset(false);
    });
    @Subscribe private final Listener<TickEvent> onTick = new Listener<>(event -> {
        if (scene != null && (!scene.objects() || !viewReady(scene))) reset(scene.objects());
    });
    @Subscribe private final Listener<LivingEvent> onUpdate = new Listener<>(event -> update());
    @Subscribe private final Listener<AttackEvent> onAttack = new Listener<>(this::attacked);
    private void attacked(AttackEvent event) {
        if (isToggled() && scene != null && scene.objects() && event.getTargetEntity() == target) policy.attack();
    }
    @Subscribe private final Listener<MovementInputEvent> onInput = new Listener<>(this::moveInput);
    private void moveInput(MovementInputEvent event) {
        if (isToggled() && scene != null && scene.objects() && viewReady(scene) && policy.consumeWTap()) event.setForward(0);
    }
    @Subscribe private final Listener<EventMotion> onMotion = new Listener<>(this::motion);
    private void motion(EventMotion event) {
        Scene frame = scene;
        if (frame == null || !frame.objects()) return;
        if (!policy.blinking()) { path.clear(); return; }
        long now = clock.getAsLong();
        if (realPos.getValue() && "Line".equals(renderMode.getValue())
                && (frame.player.lastTickPosX != frame.player.posX || frame.player.lastTickPosY != frame.player.posY
                || frame.player.lastTickPosZ != frame.player.posZ)) {
            if (path.size() >= DemiseFakeLagQueue.MAX_PACKETS) path.removeFirst();
            path.addLast(new PathPoint(frame.player.getPositionVector(), now));
        }
        while (!path.isEmpty() && now - path.peekFirst().time > policy.delayMs()) path.removeFirst();
    }
    @Subscribe private final Listener<Event3D> onRender = new Listener<>(event -> render(event));

    private void update() {
        if (processing || closing || !isToggled()) return;
        processing = true; inSourceUpdate = true;
        try {
            Scene frame = capture();
            if (frame == null) return;
            if (!viewReady(frame) || !actionSafetyAllows()) { reset(frame.objects()); return; }
            if (!AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK) || !claimUpdate(frame)) return;
            long action = generation;
            frame.pipeline.fakeLag().sourceUpdate(this);
            if (!current(frame, action)) return;
            target = selectTarget(frame);
            DemiseFakeLagPolicy.Settings settings = settings();
            DemiseFakeLagPolicy.Input input = input(frame);
            DemiseFakeLagPolicy.Result result = policy.beginUpdate(clock.getAsLong(), settings, input);
            if (!current(frame, action)) return;
            if (!input.eligible) {
                frame.pipeline.fakeLag().release(this, true);
                path.clear();
            }
            if (result.pickPosition) {
                serverPosition = frame.player.getPositionVector();
                // A fresh scene's visual starts at its first factual sample, never interpolates from world origin.
                if (path.isEmpty()) renderPosition = serverPosition;
            }
            if (input.eligible) switch (result.action) {
                case PULSE:
                    if (client.isSingleplayer() || !frame.handler.isDoneLoadingTerrain()) frame.pipeline.fakeLag().release(this, true);
                    else frame.pipeline.fakeLag().request(this, DemiseFakeLagQueue.Mode.PULSE, result.delayMs,
                            frame::objects, (ticket, before) -> released(frame, action, ticket, before));
                    break;
                case SPOOF:
                    frame.pipeline.fakeLag().request(this, DemiseFakeLagQueue.Mode.SPOOF, result.delayMs,
                            frame::objects, (ticket, before) -> released(frame, action, ticket, before));
                    break;
                case RELEASE: frame.pipeline.fakeLag().release(this, true); break;
                default: break;
            }
            if (!current(frame, action)) return;
            policy.finishUpdate(clock.getAsLong());
            if (result.resetPosition) serverPosition = renderPosition = frame.player.getPositionVector();
            float scale = policy.timer(clock.getAsLong(), settings);
            if (Float.isFinite(scale) && frame.timer != null) frame.timer.timerSpeed = timerLease.apply(frame.timer.timerSpeed, scale);
            else releaseTimer(frame);
            forceFirstHit(frame, action);
        } catch (RuntimeException | Error failure) {
            reset(false); throw failure;
        } finally { inSourceUpdate = false; processing = false; }
    }

    private void released(Scene frame, long action, DemiseFakeLagQueue.Ticket ticket, boolean before) {
        if (!current(frame, action) || !(ticket.packet instanceof C03PacketPlayer)) return;
        C03PacketPlayer movement = (C03PacketPlayer) ticket.packet;
        // Look-only C03 fields are zero placeholders, not a server position update.
        Vec3 sent = null;
        if (movement.isMoving()) {
            sent = new Vec3(movement.getPositionX(), movement.getPositionY(), movement.getPositionZ());
            if (!DemiseFakeLagMath.finite(sent)) return;
            serverPosition = sent;
        }
        DemiseFakeLagPolicy.Settings settings = settings();
        if (!viewReady(frame)) return;
        DemiseFakeLagPolicy.Input sample = input(frame, false);
        // Source shouldLag() runs before the Early-release option: its hurt
        // counter, WTap and timer effects also apply to ordinary C03 releases.
        boolean lag = policy.criteria(clock.getAsLong(), settings, sample);
        if (!lag || !policy.blinking() || sent == null || !settings.smart || !settings.earlyRelease || !validTarget(frame, target)) return;
        Vec3 eyes = frame.player.getPositionEyes(1);
        double distance = DemiseFakeLagMath.boxDistance(eyes, sent.addVector(0, frame.player.getEyeHeight(), 0), DemiseFakeLagMath.hitbox(target));
        if (distance > 3 || !current(frame, action)) return;
        frame.player.setPosition(sent.xCoord, sent.yCoord, sent.zCoord);
        if (inSourceUpdate) {
            frame.player.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a77[FakeLag] released early"));
            if (ticket.mode == DemiseFakeLagQueue.Mode.SPOOF) frame.pipeline.fakeLag().discardPending(this, ticket);
            policy.earlyRelease();
        }
    }

    private void forceFirstHit(Scene frame, long action) {
        if (!forceFirstHit.getValue() || !policy.firstHit() || !validTarget(frame, target) || frame.player.isUsingItem()
                || !AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK) || client.objectMouseOver == null
                || client.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) return;
        final Entity hit = client.objectMouseOver.entityHit;
        if (hit == null || hit == frame.player || hit.worldObj != frame.world || hit.isDead
                || !frame.controller.canNativeEntityClick(hit)) return;
        final int tick = frame.player.ticksExisted;
        final long motion = RuntimeServices.rotation().getMotionSequence();
        java.util.function.BooleanSupplier valid = () -> current(frame, action) && viewReady(frame)
                && frame.player.ticksExisted == tick && frame.controller.canNativeEntityClick(hit)
                && AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK);
        RuntimeServices.attackScheduler().tryClick(motion, true, () -> 1L,
                () -> frame.controller.tryAttackEntity(frame.player, hit, PlayerControllerMP.AttackExecution.NATIVE_CLICK, valid),
                () -> {
                    // An automatic native action cannot complete later from an obsolete queued C02.
                    frame.pipeline.fakeLag().release(this, true);
                    if (!valid.getAsBoolean()) return;
                    policy.consumeFirstHit(); frame.player.swingItem();
                    frame.player.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a77[FakeLag] forced first hit"));
                });
    }

    private DemiseFakeLagPolicy.Settings settings() {
        DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings();
        s.mode = mode.getValue(); s.smart = smart.getValue(); s.earlyRelease = earlyRelease.getValue();
        s.attackHurtTime = hurtTimeToAttack.getValue().floatValue(); s.attackRange = attackRange.getValue().floatValue();
        s.alwaysSpoof = alwaysSpoof.getValue(); s.searchRange = searchRange.getValue().floatValue();
        s.recoilMs = recoilTime.getValue().floatValue(); s.delayMin = delayMin.getValue().floatValue(); s.delayMax = delayMax.getValue().floatValue();
        s.opponentAttackMs = opponentDelay.getValue().floatValue(); s.keepRange = keepRange.getValue();
        s.timer = timer.getValue().floatValue(); s.timerTicks = timerTicks.getValue().floatValue();
        s.dynamicHurtTime = dynamicHurtTime.getValue(); s.hurtTimeToStop = hurtTimeToStop.getValue().floatValue();
        return s;
    }
    private DemiseFakeLagPolicy.Input input(Scene frame) {
        return input(frame, true);
    }
    private DemiseFakeLagPolicy.Input input(Scene frame, boolean updateConditions) {
        DemiseFakeLagPolicy.Input in = new DemiseFakeLagPolicy.Input();
        in.eligible = validTarget(frame, target);
        if (updateConditions) {
            Aura aura = module(Aura.class); Backtrack backtrack = module(Backtrack.class);
            in.eligible &= (!onlyOnGround.getValue() || frame.player.onGround)
                    && (!onlyAura.getValue() || aura != null && aura.isToggled())
                    && (!pauseOnBacktrack.getValue() || backtrack == null || !backtrack.isToggled() || backtrack.getActivitySnapshot() == null);
        }
        if (!in.eligible) return in;
        Vec3 eyes = frame.player.getPositionEyes(1); AxisAlignedBB box = DemiseFakeLagMath.hitbox(target);
        in.boxDistance = DemiseFakeLagMath.boxDistance(eyes, eyes, box);
        in.clientDistance = frame.player.getDistanceToEntity(target); in.serverDistance = DemiseFakeLagMath.entityDistance(serverPosition, target);
        in.selfHurtTime = frame.player.hurtTime; in.targetHurtTime = target.hurtTime;
        in.visible = smart.getValue() || target.canEntityBeSeen(frame.player); in.targetSwinging = target.isSwingInProgress;
        in.targetAimingAtServer = DemiseFakeLagMath.aimsAtServer(target, frame.player.getPositionVector(), serverPosition);
        in.mouseDistance = DemiseFakeLagMath.mouseDistance(eyes, frame.player.getLook(1), box, target == frame.player.ridingEntity);
        return in;
    }
    private EntityPlayer selectTarget(Scene frame) {
        EntityPlayer selected = null;
        double range = alwaysSpoof.getValue() ? Float.MAX_VALUE : searchRange.getValue().floatValue() + 1;
        for (EntityPlayer candidate : frame.world.playerEntities) {
            if (!validTarget(frame, candidate) || teamCheck.getValue() && DemiseFakeLagMath.sameTeam(frame.player, candidate)) continue;
            float distance = frame.player.getDistanceToEntity(candidate) - .5657f;
            if (distance <= range) { selected = candidate; range = distance; }
        }
        Aura aura = module(Aura.class);
        if (aura != null && aura.isToggled() && aura.getActiveTarget() instanceof EntityPlayer
                && validTarget(frame, (EntityPlayer) aura.getActiveTarget())) return (EntityPlayer) aura.getActiveTarget();
        return selected;
    }
    @SuppressWarnings("unchecked") private <T extends Module> T module(Class<T> type) {
        return Pulsar.INSTANCE.getMm() == null ? null : (T) Pulsar.INSTANCE.getMm().getModule(type);
    }
    private boolean validTarget(Scene frame, EntityPlayer entity) {
        return entity != null && entity != frame.player && entity.worldObj == frame.world && !entity.isDead && entity.isEntityAlive()
                && frame.world.getEntityByID(entity.getEntityId()) == entity;
    }
    private boolean claimUpdate(Scene frame) {
        if (budgetPlayer.get() != frame.player || budgetWorld.get() != frame.world) {
            budgetPlayer = new WeakReference<>(frame.player); budgetWorld = new WeakReference<>(frame.world); lastUpdate = Long.MIN_VALUE;
        }
        if (lastUpdate != Long.MIN_VALUE && frame.player.ticksExisted <= lastUpdate) return false;
        lastUpdate = frame.player.ticksExisted; return true;
    }
    private Scene capture() {
        if (scene != null && !scene.objects()) { reset(false); return null; }
        if (!isToggled() || closing || client == null || !client.isCallingFromMinecraftThread()
                || client.thePlayer == null || client.theWorld == null || client.playerController == null
                || unloadedWorld.get() == client.theWorld
                || client.thePlayer.sendQueue == null || client.thePlayer.sendQueue.getNetworkManager() == null) return null;
        if (scene == null) {
            PacketPipeline pipeline = client.thePlayer.sendQueue.getNetworkManager().getClientPackets();
            if (pipeline == null || !pipeline.isActive()) return null;
            scene = new Scene(pipeline);
        }
        return scene.objects() ? scene : null;
    }
    private boolean viewReady(Scene frame) {
        return client.gameSettings != null && client.currentScreen == null && client.inGameHasFocus && !client.isGamePaused()
                && !frame.player.isDead && frame.player.isEntityAlive() && !frame.player.isSpectator() && !frame.player.isRiding();
    }
    private boolean current(Scene frame, long action) { return !closing && isToggled() && scene == frame && generation == action && frame.objects(); }
    private void releaseTimer(Scene frame) {
        if (frame != null && frame.timer != null && client.timer == frame.timer)
            frame.timer.timerSpeed = timerLease.release(frame.timer.timerSpeed);
        else timerLease.release(1);
    }
    private void reset(boolean publish) {
        if (closing) return;
        closing = true;
        try {
            generation++; Scene old = scene; scene = null; target = null;
            releaseTimer(old); path.clear();
            serverPosition = renderPosition = new Vec3(0, 0, 0);
            if (policy != null) policy.reset(clock.getAsLong());
            if (old != null) old.pipeline.fakeLag().release(this, publish && old.objects());
        } finally { closing = false; }
    }
    private final class Scene {
        final EntityPlayerSP player = client.thePlayer;
        final WorldClient world = client.theWorld;
        final NetHandlerPlayClient handler = player.sendQueue;
        final PlayerControllerMP controller = client.playerController;
        final net.minecraft.util.Timer timer = client.timer;
        final PacketPipeline pipeline;
        final long epoch;
        Scene(PacketPipeline pipeline) { this.pipeline = pipeline; epoch = pipeline.getContextVersion(); }
        boolean objects() {
            return unloadedWorld.get() != world && client.thePlayer == player && client.theWorld == world && player.worldObj == world
                    && player.sendQueue == handler && client.playerController == controller && player.inventory != null
                    && handler.getNetworkManager() != null && handler.getNetworkManager().getClientPackets() == pipeline
                    && pipeline.isActive() && pipeline.getContextVersion() == epoch && module(FakeLag.class) == FakeLag.this;
        }
    }
    private static final class TimerLease {
        private boolean owned, blocked; private float before, written;
        float apply(float current, float desired) {
            if (owned && Float.compare(current, written) != 0) { owned = false; blocked = true; }
            if (blocked || !owned && Float.compare(current, desired) == 0) return current;
            if (!owned) { before = current; owned = true; }
            return written = desired;
        }
        float release(float current) {
            float result = owned && Float.compare(current, written) == 0 ? before : current;
            owned = blocked = false; return result;
        }
    }
    private static final class PathPoint {
        final Vec3 position; final long time;
        PathPoint(Vec3 position, long time) { this.position = position; this.time = time; }
    }
    private void render(Event3D event) {
        Scene frame = scene;
        if (!realPos.getValue() || !isToggled() || frame == null || !frame.objects() || client.gameSettings == null
                || client.gameSettings.thirdPersonView == 0 || !policy.blinking()) return;
        RenderManager manager = client.getRenderManager();
        if (manager == null) return;
        try (RenderState ignored = new RenderState()) {
            Theme theme = Theme.getDefaultTheme();
            if ("Line".equals(renderMode.getValue())) {
                GlStateManager.disableDepth(); GlStateManager.enableBlend(); GlStateManager.disableTexture2D();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_LINE_SMOOTH); GL11.glLineWidth(2);
                long now = clock.getAsLong(); int i = 0;
                GL11.glBegin(GL11.GL_LINE_STRIP);
                try {
                    for (PathPoint point : path) {
                        float alpha = policy.delayMs() <= 0 ? 0 : Math.max(0, Math.min(1, 1 - (float) (now - point.time) / policy.delayMs()));
                        int color = theme.getGradientRGB(i++ / 20F);
                        GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha);
                        GL11.glVertex3d(point.position.xCoord - manager.renderPosX, point.position.yCoord - manager.renderPosY, point.position.zCoord - manager.renderPosZ);
                    }
                } finally { GL11.glEnd(); }
                return;
            }
            float partial = event.getPartialTicks();
            renderPosition = new Vec3(interpolate(renderPosition.xCoord, serverPosition.xCoord, partial),
                    interpolate(renderPosition.yCoord, serverPosition.yCoord, partial), interpolate(renderPosition.zCoord, serverPosition.zCoord, partial));
            double x = renderPosition.xCoord - manager.viewerPosX, y = renderPosition.yCoord - manager.viewerPosY, z = renderPosition.zCoord - manager.viewerPosZ;
            if ("Box".equals(renderMode.getValue())) {
                AxisAlignedBB box = frame.player.getEntityBoundingBox().expand(.1, .1, .1).offset(
                        x - frame.player.posX, y - frame.player.posY, z - frame.player.posZ);
                RenderUtils.drawAxisAlignedBB(box, false, (theme.getGradientRGB(.05f) & 0xffffff) | 150 << 24);
            } else {
                float light = frame.world.getLight(new BlockPos(frame.player.getPositionVector()));
                GlStateManager.color(light, light, light);
                manager.doRenderEntity(frame.player, x, y, z, frame.player.rotationYawHead, partial, true);
            }
        }
    }
    private static double interpolate(double from, double to, float partial) {
        return (float) from + ((float) to - (float) from) * partial;
    }
}
