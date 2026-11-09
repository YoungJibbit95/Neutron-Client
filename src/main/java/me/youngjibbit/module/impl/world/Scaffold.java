package me.youngjibbit.module.impl.world;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.world.PlacementContext;
import me.youngjibbit.core.world.PlacementTarget;
import me.youngjibbit.core.world.ScaffoldRotationPlan;
import me.youngjibbit.core.world.AdaptiveBridgeScaffoldAdapter;
import me.youngjibbit.core.world.AdaptiveBridgePlanner;
import me.youngjibbit.core.world.AdaptiveBridgeMotion;
import me.youngjibbit.core.world.AdaptiveBridgeSafetyController;
import me.youngjibbit.core.world.TowerController;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.RotationSettings;
import me.youngjibbit.core.combat.RotationProfile;
import me.youngjibbit.core.combat.RotationReturnSettings;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.value.impl.MoveFixValue;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "Scaffold",
        description = "automated block placement",
        category = Category.World
)
public class Scaffold extends Module {

    private final TimerControl timerControl = new TimerControl();
    private final TowerController towerController = new TowerController();
    private final me.youngjibbit.core.world.ArsenicScaffoldController arsenicController=new me.youngjibbit.core.world.ArsenicScaffoldController();
    private final AdaptiveBridgeScaffoldAdapter bridgeAdapter=new AdaptiveBridgeScaffoldAdapter();
    private final AdaptiveBridgeSafetyController bridgeSafety=new AdaptiveBridgeSafetyController();
    private long bridgeGeneration,bridgeLastPreSequence=Long.MIN_VALUE,bridgeLastPostSequence=Long.MIN_VALUE;
    private long bridgeLastInputSequence=Long.MIN_VALUE,bridgeFreshInputSequence;
    private PlacementTarget bridgePreview;
    private PlacementTarget bridgeProfileAnchor;
    private AdaptiveBridgePlanner.Scope bridgeScope;
    private boolean bridgeHasDirection;
    private float bridgeForward,bridgeStrafe,bridgeYaw;
    private final ComboValue approach=new ComboValue("Scaffold Approach",this,new String[]{"Neutron","Arsenic","Adaptive Bridge"},0);
    private final ComboValue bridgeEdgeSafety=new ComboValue("Bridge Edge Safety",this,
            new String[]{"Off","Sneak","Stop","Smart"},3);
    private final SliderValue bridgePrediction=new SliderValue("Bridge Prediction",this,1D,0,3,false);
    private final ComboValue bridgeRotationProfile=new ComboValue("Bridge Rotation Profile",this,
            new String[]{"Adaptive","Godbridge"},0);
    private final ComboValue bridgeRotationSpeed=new ComboValue("Bridge Rotation Speed",this,
            new String[]{"Calm","Balanced","Fast"},1);
    private final SliderValue bridgeRotationVariation=new SliderValue("Bridge Rotation Variation",this,.25D,0,1,false);
    private final CheckBoxValue softArsenicMotion = new CheckBoxValue("Arsenic Soft Motion", this, false);
    private final me.youngjibbit.core.world.ScaffoldSneakInterval sneakInterval = new me.youngjibbit.core.world.ScaffoldSneakInterval();
    private long freshInputSequence;
    private final SliderValue sneakAfterBlocks = new SliderValue("Sneak After Blocks", this, 0D, 0, 20, true);
    private final CheckBoxValue eagle=new CheckBoxValue("Eagle",this,true);
    private final SliderValue safety=new SliderValue("Safety",this,2D,0,3,false);
    private final SliderValue arsenicMinSpeed=new SliderValue("Arsenic Min Speed",this,180D,1,360,true);
    private final SliderValue arsenicMaxSpeed=new SliderValue("Arsenic Max Speed",this,360D,1,360,true);

    private final ComboValue placeTiming = new ComboValue("Place Timing", this, new String[]{"Pre", "Post"}, 0);
    private final CheckBoxValue rotations = new CheckBoxValue("Rotations", this, true);
    private final MoveFixValue movefix = new MoveFixValue(this, false);
    private final ComboValue bodyRotation = new ComboValue("Body Rotation", this, new String[]{"Vanilla", "Direkt", "Natürlich"}, 0);
    private final RotationReturnSettings rotationReturn = new RotationReturnSettings(this);
    private final ComboValue rotationMode = new ComboValue("Rotation Mode", this, new String[]{"Back", "Down", "NCP", "Godbridge"}, 0);
    private final CheckBoxValue roundRots=new CheckBoxValue("Round Rots",this,false);
    private final SliderValue yawSpeed = new SliderValue("Yaw Speed", this, 35.0, 1.0, 90.0, true);
    private final SliderValue pitchSpeed = new SliderValue("Pitch Speed", this, 35.0, 1.0, 90.0, true);
    private final RotationSettings rotationSettings=new RotationSettings(this,"Rotation Smoothing",yawSpeed,pitchSpeed);
    private final SliderValue placeDelay = new SliderValue("Place Delay", this, 0.0, 0.0, 200.0, true);
    private final SliderValue timerSpeed = new SliderValue("Timer", this, 1.0, 0.2, 3.0, false);

    private final CheckBoxValue sprint = new CheckBoxValue("Sprint", this, false);
    private final CheckBoxValue keepY = new CheckBoxValue("Keep Y", this, false);
    private final CheckBoxValue downwards = new CheckBoxValue("Downwards", this, false);
    private final CheckBoxValue itemSpoof = new CheckBoxValue("Item Spoof", this, true);
    private final CheckBoxValue swing = new CheckBoxValue("Swing", this, true);
    private final ComboValue swingMode = new ComboValue("Swing Mode", this, new String[]{"Client", "Silent"}, 0);
    private final CheckBoxValue autoJump = new CheckBoxValue("Auto Jump", this, false);
    private final CheckBoxValue tower = new CheckBoxValue("Tower", this, false);
    private final ComboValue towerMode = new ComboValue("Tower Mode", this, new String[]{"Vanilla", "NCP", "Watchdog"}, 0);

    private final CheckBoxValue placementDiagnostics = new CheckBoxValue("Placement Diagnostics", this, false);
    private long nextDiagnosticNanos;
    private final java.util.function.LongSupplier diagnosticClock=System::nanoTime;
    private PlacementTarget currentTarget;
    public static double keepYCoord;
    private static boolean downwardsState;
    private PlacementTarget frameTarget;
    private long frameSequence = -1;
    private boolean framePre;
    private Object contextPlayer, contextWorld;
    private Object contextConnection;
    private Object contextTimer;
    private PacketPipeline contextPackets;
    private long contextEpoch = -1;
    private RotationManager contextRotation;
    private SceneContext contextScene;
    private long contextMotionSequence = -1;
    private long lifecycleGeneration;
    private long noSprintSequence = -1, noSprintGeneration = -1;
    private boolean hasHoldBasis;
    private float holdYaw, holdPitch;
    private static Scaffold active;

    /** Relinquish control if another writer changes the timer; never restore over that writer. */
    public static final class TimerControl {
        private boolean owned, blocked;
        private float before, written;
        public float apply(float current, float desired) {
            if (owned && Float.compare(current, written) != 0) { owned = false; blocked = true; }
            if (blocked) return current;
            if (!owned && Float.compare(current, desired) == 0) return current;
            if (!owned) { before = current; owned = true; }
            written = desired;
            return desired;
        }
        public float release(float current) {
            float result = owned && Float.compare(current, written) == 0 ? before : current;
            owned = false; blocked = false;
            return result;
        }
    }

    public Scaffold() {
        this.setKey(Keyboard.KEY_G);
        roundRots.setVisibility(module->rotations.getValue());
        for(me.youngjibbit.value.Value<?> value:getValues()) {
            if(value==bridgeEdgeSafety || value==bridgePrediction || value==bridgeRotationProfile
                    || value==bridgeRotationSpeed || value==bridgeRotationVariation) {
                value.setVisibility(m->adaptiveBridge());continue;
            }
            if(value==yawSpeed || value==pitchSpeed) {
                java.util.function.Predicate<Module> previous=value.getVisibility();
                value.setVisibility(m->adaptiveBridge() || !arsenic() && (previous==null || previous.test(m)));
                continue;
            }
            if(value==sneakAfterBlocks || value==approach || value==bodyRotation || value==itemSpoof || value==swing || value==swingMode
                    || value==placeDelay || value==placementDiagnostics || value==rotationReturn.enabled || value==rotationReturn.ticks)continue;
            if(value==softArsenicMotion || value==eagle || value==safety || value==arsenicMinSpeed || value==arsenicMaxSpeed) {
                value.setVisibility(m->arsenic());continue;
            }
            java.util.function.Predicate<Module> visible=value.getVisibility();
            value.setVisibility(m->!arsenic() && !adaptiveBridge() && (visible==null || visible.test(m)));
        }
    }
    private boolean arsenic() { return "Arsenic".equals(approach.getValue()); }
    private boolean adaptiveBridge() { return "Adaptive Bridge".equals(approach.getValue()); }
    private boolean usesRotations() { return arsenic() || adaptiveBridge() || rotations.getValue(); }
    @Override public void beginConfigUpdate() {
        super.beginConfigUpdate();approach.reset();softArsenicMotion.reset();sneakAfterBlocks.reset();
        bridgeEdgeSafety.reset();bridgePrediction.reset();bridgeRotationProfile.reset();
        bridgeRotationSpeed.reset();bridgeRotationVariation.reset();
    }
    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) { return super.getValueByName(name!=null && name.replace("_","").replace(" ","").equalsIgnoreCase("SameY")?"keep_y":name); }
    @Override public void onValueChange() {
        long generation = ++lifecycleGeneration;
        sneakInterval.reset();
        releasePlacement();
        arsenicController.reset();
        resetBridgeAcquisition(true);
        // Module's callback has no changed-value argument; revalidate the actual owner conditions.
        boolean end = !isToggled() || !usesRotations() || !sameScene();
        if (generation != lifecycleGeneration) return;
        if (!usesRotations() && isToggled() && contextRotation != null) {
            rotationReturn.begin(contextRotation, "scaffold", moveFixMode(), bodyRotationMode(),
                    () -> lifecycleGeneration == generation && contextRotation == RuntimeServices.rotation());
            if (generation != lifecycleGeneration) return;
        }
        if (!rotationReturn.enabled.getValue() && !(arsenic() && softArsenicMotion.getValue())) RuntimeServices.rotation().cancelScaffoldRelease();
        if (end) endRotation();
    }

    @Override
    public void onEnable() {
        long generation = ++lifecycleGeneration;
        RuntimeServices.rotation().cancelScaffoldRelease();
        clearState();
        if (generation != lifecycleGeneration) return;
        active = this;
        captureContext();
        if (generation != lifecycleGeneration) return;
        super.onEnable();
        if(generation==lifecycleGeneration && adaptiveBridge()) previewBridgeOnEnable(generation);
    }

    @Override
    public void onDisable() {
        final long generation = ++lifecycleGeneration;
        // Detach normal action listeners before any scene supplier can reenter the lifecycle.
        super.onDisable();
        try {
            RotationManager rotation = contextRotation;
            if (usesRotations() && rotation != null && rotation == RuntimeServices.rotation())
                rotationReturn.begin(rotation, "scaffold", moveFixMode(), bodyRotationMode(), arsenic() && softArsenicMotion.getValue(), () -> lifecycleGeneration == generation);
        } finally {
            if (generation == lifecycleGeneration) {
                try { clearState(); }
                finally { if (generation == lifecycleGeneration && active == this) active = null; }
            }
        }
    }

    private boolean liveContext() {
        Minecraft client = Minecraft.getMinecraft();
        return client != null && client.thePlayer != null && client.theWorld != null && client.playerController != null
                && client.thePlayer.worldObj == client.theWorld && client.thePlayer.sendQueue != null
                && client.thePlayer.sendQueue.getNetworkManager() != null
                && client.thePlayer.sendQueue.getNetworkManager().getClientPackets() != null
                && client.thePlayer.sendQueue.getNetworkManager().getClientPackets().isActive()
                && client.thePlayer.isEntityAlive() && !client.thePlayer.isDead && !client.thePlayer.isSpectator()
                && !client.thePlayer.isRiding();
    }

    private boolean validContext() {
        Minecraft client = Minecraft.getMinecraft();
        return liveContext() && client.currentScreen == null && client.inGameHasFocus && !client.isGamePaused();
    }

    private boolean sameObjects() {
        Minecraft client = Minecraft.getMinecraft();
        return client != null && contextPlayer != null && client.thePlayer == contextPlayer && client.theWorld == contextWorld
                && client.thePlayer.sendQueue != null && client.thePlayer.sendQueue.getNetworkManager() == contextConnection
                && client.thePlayer.sendQueue.getNetworkManager().getClientPackets() == contextPackets
                && contextPackets != null
                && RuntimeServices.rotation() == contextRotation;
    }

    private boolean sameIdentities() {
        return sameObjects() && contextPackets.getContextVersion() == contextEpoch;
    }

    private boolean sameScene() {
        if (!sameIdentities()) return false;
        RuntimeObservation observation = RuntimeServices.observation();
        return contextScene == null || observation != null && observation.isCurrent(contextScene);
    }

    private void captureContext() {
        long generation = lifecycleGeneration;
        Minecraft mc = Minecraft.getMinecraft();
        contextPlayer = mc == null ? null : mc.thePlayer;
        contextWorld = mc == null ? null : mc.theWorld;
        contextConnection = mc == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null
                ? null : mc.thePlayer.sendQueue.getNetworkManager();
        contextPackets = contextConnection == null ? null : mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        contextEpoch = contextPackets == null ? -1 : contextPackets.getContextVersion();
        contextRotation = RuntimeServices.rotation();
        contextTimer = mc == null ? null : mc.timer;
        contextMotionSequence = contextRotation.getMotionSequence();
        RuntimeObservation observation = RuntimeServices.observation();
        SceneContext scene = observation == null ? null : observation.captureScene();
        if (generation != lifecycleGeneration) return;
        // Pre-bootstrap rotation planning has no fallback observer and grants no placement admission.
        contextScene = scene != null && scene.isValid() ? scene : null;
        if (mc != null && mc.thePlayer != null) keepYCoord = Math.floor(mc.thePlayer.posY - 1);
    }

    private void releaseRotation() {
        long generation = lifecycleGeneration;
        RotationManager rotation = contextRotation;
        long sequence = rotation == null ? -1 : rotation.getMotionSequence();
        // Never release a replacement manager or a newly bound foreign scene during a callback.
        if (rotation == null) rotation = RuntimeServices.rotation();
        else try {
            if (contextPackets != null && (!rotation.isConnection(contextPackets)
                    || !sameScene() && !("scaffold".equals(rotation.getConfirmedOwner(contextPlayer, contextWorld, contextPackets))
                    && (rotation.getMotionSequence() == contextMotionSequence || rotation.canUseRotation("scaffold", true))))) return;
        } catch (RuntimeException | Error error) {
            if (generation == lifecycleGeneration && rotation == contextRotation
                    && rotation.getMotionSequence() == sequence && rotation.isConnection(contextPackets)) {
                rotation.reset("scaffold");
                RotationCorrections.reset("scaffold");
            }
            throw error;
        }
        if (generation != lifecycleGeneration || sequence >= 0 && rotation.getMotionSequence() != sequence) return;
        rotation.reset("scaffold");
        if (!rotation.hasRotationRelease("scaffold")) RotationCorrections.reset("scaffold");
    }

    private void endRotation() {
        hasHoldBasis = false;
        releaseRotation();
    }

    private void rememberAcceptedRotation() {
        if (usesRotations() && sameScene() && contextRotation.isConnection(contextPackets)
                && "scaffold".equals(contextRotation.getConfirmedOwner(contextPlayer, contextWorld, contextPackets))) {
            holdYaw = contextRotation.getEffectiveYaw();
            holdPitch = contextRotation.getEffectivePitch();
            hasHoldBasis = true;
        }
    }

    private void suspendPlacement() {
        sneakInterval.reset();
        arsenicController.reset();
        resetBridgeAcquisition(false);
        rememberAcceptedRotation();
        releasePlacement();
        releaseRotation(); // Keep only the scene-bound wish; resume needs a new C03 acceptance.
    }

    private void releasePlacement() {
        currentTarget = frameTarget = null;
        frameSequence = -1;
        downwardsState = false;
        towerController.reset();
        Minecraft client = Minecraft.getMinecraft();
        // Epoch invalidation precedes WorldEvent.Unload while these old objects still exist.
        // Release our value on that exact timer; a replacement scene/object or foreign value wins.
        if (client != null && client.timer != null && client.timer == contextTimer && sameObjects())
            client.timer.timerSpeed = timerControl.release(client.timer.timerSpeed);
        else timerControl.release(0); // Discard our bookkeeping without writing into another scene.
    }

    private void clearState() {
        sneakInterval.reset();
        arsenicController.reset();
        resetBridgeAcquisition(true);
        long generation = lifecycleGeneration;
        try { endRotation(); }
        finally {
            if (generation == lifecycleGeneration) {
                releasePlacement();
                RuntimeServices.scaffoldPlacement().reset();
                contextPlayer = contextWorld = null;
                contextConnection = contextPackets = null;
                contextRotation = null;
                contextTimer = null;
                contextScene = null;
                contextEpoch = -1;
                contextMotionSequence = -1;
                keepYCoord = 0;
            }
        }
    }

    @Subscribe
    public final Listener<EventMotion> onMotionEvent = new Listener<>(event -> {
        if (event.isPre() && RuntimeServices.rotation().hasPreparedMovement()) return;
        handleMotion(event, null);
    });

    @Subscribe private final Listener<EventRotation> prepareRotation = new Listener<>(preparation -> handleMotion(preparation.motion(), preparation));

    private void handleMotion(EventMotion event, EventRotation preparation) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isToggled()) return;
        if (!liveContext()) { diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.CONTEXT);clearState(); return; }
        if (contextPlayer == null || !sameScene()) {
            clearState(); captureContext();
            if (!event.isPre()) return;
        }
        if (contextScene == null && RuntimeServices.observation() != null) {
            SceneContext scene = RuntimeServices.observation().captureScene();
            if (scene.isValid()) contextScene = scene;
        }
        contextMotionSequence = contextRotation.getMotionSequence();
        rememberAcceptedRotation();
        if (!validContext()) { suspendPlacement(); return; }
        final long motionSequence = contextMotionSequence;
        final long generation = lifecycleGeneration;
        if (!usesRotations() && contextRotation.hasRotationRelease("scaffold")) {
            if (currentMotion(motionSequence, generation)) releasePlacement();
            return;
        }
        if(arsenic()) { arsenicMotion(event,motionSequence,generation);return; }
        if(adaptiveBridge()) { bridgeMotion(event,motionSequence,generation);return; }
        if (event.isPre()) {
            downwardsState = downwards.getValue() && mc.gameSettings.keyBindSneak.isKeyDown();
            if (mc.thePlayer.onGround) keepYCoord = Math.floor(mc.thePlayer.posY - 1);
            PlacementContext context = new PlacementContext(downwardsState, keepY.getValue(), keepYCoord);
            if (!RuntimeServices.scaffoldPlacement().stillNeeded(currentTarget, context))
                currentTarget = findTarget(context,event);
            if (currentTarget == null) { diagnose(RuntimeServices.scaffoldPlacement().getLastRejection());releasePlacement(); requestAcceptedHold(); return; }

            // A valid placement plan owns this decision before Auto Jump consumes sprint's impulse.
            if (!sprint.getValue()) {
                noSprintSequence = motionSequence; noSprintGeneration = generation;
                mc.thePlayer.setSprinting(false);
            }
            if (autoJump.getValue() && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
                if (preparation != null) {
                    preparation.beforePhysics(() -> {
                        if (continueMotion(motionSequence, generation) && mc.thePlayer.onGround && isMoving()
                                && !mc.gameSettings.keyBindJump.isKeyDown()) mc.thePlayer.jump();
                    });
                } else if (isMoving()) {
                    mc.thePlayer.jump();
                    if (!continueMotion(motionSequence, generation)) return;
                }
            }
            boolean towerEnabled = tower.getValue();
            towerController.apply(event, towerEnabled, towerMode.getValue(), mc.gameSettings.keyBindJump.isKeyDown(), isMoving(), true);
            if (towerEnabled && !continueMotion(motionSequence, generation)) return;
            // Tower may move the local player. Finalize the plan before issuing a rotation or attempting placement.
            if (!RuntimeServices.scaffoldPlacement().stillNeeded(currentTarget, context))
                currentTarget = findTarget(context,event);
            if (currentTarget == null) { diagnose(RuntimeServices.scaffoldPlacement().getLastRejection());releasePlacement(); requestAcceptedHold(); return; }
            if (mc.timer != null) mc.timer.timerSpeed = timerControl.apply(mc.timer.timerSpeed,
                    Math.max(.2f, Math.min(3, timerSpeed.getValue().floatValue())));

            RotationProfile profile=rotationSettings.snapshot();
            currentTarget=RuntimeServices.scaffoldPlacement().randomize(currentTarget,RuntimeServices.rotation(),profile);
            Vec3 plannedEyes=mc.thePlayer.getPlannedMovementPosition(event.getX(),event.getY(),event.getZ()).addVector(0,mc.thePlayer.getEyeHeight(),0);
            currentTarget=RuntimeServices.scaffoldPlacement().align(currentTarget,rotationMode.getValue(),roundRots.getValue(),plannedEyes);
            if(currentTarget==null){diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.NO_FACE);releasePlacement();requestAcceptedHold();return;}
            frameTarget = currentTarget;
            frameSequence = RuntimeServices.rotation().getMotionSequence();
            framePre = "Pre".equalsIgnoreCase(placeTiming.getValue());
            if (framePre) {
                boolean placed = tryPlace(true);
                if (!continueMotion(motionSequence, generation)) return;
                if (placed) { releasePlacement(); requestAcceptedHold(); return; }
            }
            if (!RuntimeServices.scaffoldPlacement().isValid(frameTarget)) { releasePlacement(); requestAcceptedHold(); return; }
            applyRotations(event,profile);
        } else if (event.isPost()) {
            // Never search for a replacement in POST: this is exactly the PRE plan.
            if (frameTarget == null || frameSequence != RuntimeServices.rotation().getMotionSequence()) return;
            PlacementContext context = new PlacementContext(downwards.getValue() && mc.gameSettings.keyBindSneak.isKeyDown(), keepY.getValue(), keepYCoord);
            if (!RuntimeServices.scaffoldPlacement().stillNeeded(frameTarget, context)) { releasePlacement(); return; }
            boolean placed = !framePre && tryPlace(false);
            if (!continueMotion(motionSequence, generation)) return;
            if (placed || !RuntimeServices.scaffoldPlacement().isValid(frameTarget)) releasePlacement();
        }
    }

    /** Release only this approach's short-lived acquisition; never a physical key. */
    private void resetBridgeAcquisition(boolean sequences) {
        if(contextRotation!=null && bridgeScope!=null)contextRotation.resetBridgeMotion(bridgeScope);
        bridgeGeneration++;
        bridgePreview=null;
        bridgeProfileAnchor=null;
        bridgeScope=null;
        bridgeHasDirection=false;
        bridgeAdapter.reset();
        bridgeSafety.reset();
        if(sequences) {
            bridgeLastPreSequence=bridgeLastPostSequence=bridgeLastInputSequence=Long.MIN_VALUE;
        }
    }

    private boolean bridgeCurrent(long sequence,long generation,long acquisition) {
        if(!bridgeScopeCurrent(sequence,generation,acquisition))return false;
        boolean live=liveContext();
        if(!bridgeScopeCurrent(sequence,generation,acquisition))return false;
        if(!live) {clearState();return false;}
        boolean same=sameScene();
        // A callback may replace only the acquisition, without a module toggle.
        // The old query must not clear or suspend that replacement acquisition.
        if(!bridgeScopeCurrent(sequence,generation,acquisition))return false;
        if(!same) {clearState();return false;}
        boolean valid=validContext();
        if(!bridgeScopeCurrent(sequence,generation,acquisition))return false;
        if(!valid) {suspendPlacement();return false;}
        return true;
    }

    private boolean bridgeScopeCurrent(long sequence,long generation,long acquisition) {
        return active==this && adaptiveBridge() && bridgeGeneration==acquisition
                && currentMotion(sequence,generation);
    }

    private long revokeBridge(long sequence,long generation,long acquisition) {
        if(!bridgeCurrent(sequence,generation,acquisition))return -1;
        resetBridgeAcquisition(false);
        long replacement=bridgeGeneration;
        releasePlacement();
        releaseRotation();
        return bridgeCurrent(sequence,generation,replacement)?replacement:-1;
    }

    private static final class BridgeInput {
        final float forward,strafe;
        BridgeInput(float forward,float strafe) {this.forward=forward;this.strafe=strafe;}
    }

    private static boolean keyDown(KeyBinding binding) { return binding!=null && binding.isKeyDown(); }

    /** The early rotation hook runs before updatePlayerMoveState: read real keys here. */
    private BridgeInput bridgeInput(Minecraft client) {
        MovementInput input=client.thePlayer.movementInput;
        if(input instanceof MovementInputFromOptions && client.gameSettings!=null) {
            float forward=(keyDown(client.gameSettings.keyBindForward)?1:0)
                    -(keyDown(client.gameSettings.keyBindBack)?1:0);
            float strafe=(keyDown(client.gameSettings.keyBindLeft)?1:0)
                    -(keyDown(client.gameSettings.keyBindRight)?1:0);
            if(keyDown(client.gameSettings.keyBindSneak)) {forward*=.3f;strafe*=.3f;}
            return new BridgeInput(forward,strafe);
        }
        // Explicit custom/headless input providers retain their own fresh sampling contract.
        return new BridgeInput(input==null?0:input.moveForward,input==null?0:input.moveStrafe);
    }

    private void rememberBridgeDirection(BridgeInput input,float yaw) {
        bridgeForward=input.forward;bridgeStrafe=input.strafe;bridgeYaw=yaw;
        bridgeHasDirection=true;
    }

    private boolean bridgeDirectionChanged(BridgeInput input,float yaw) {
        if(!bridgeHasDirection)return false;
        if(!Float.isFinite(input.forward) || !Float.isFinite(input.strafe) || !Float.isFinite(yaw))return true;
        double previous=Math.hypot(bridgeForward,bridgeStrafe),current=Math.hypot(input.forward,input.strafe);
        if(previous<1e-5 || current<1e-5)return (previous<1e-5)!=(current<1e-5);
        double oldDirection=bridgeYaw-Math.toDegrees(Math.atan2(bridgeStrafe,bridgeForward));
        double direction=yaw-Math.toDegrees(Math.atan2(input.strafe,input.forward));
        return Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_double(direction-oldDirection))>1;
    }

    private AdaptiveBridgePlanner.Frame bridgeFrame(long sequence,BridgeInput input,boolean requireBinding) {
        Minecraft client=Minecraft.getMinecraft();
        AdaptiveBridgePlanner.Frame frame=new AdaptiveBridgePlanner.Frame();
        frame.scope=new AdaptiveBridgePlanner.Scope(this,contextWorld,contextPlayer,contextPackets,
                contextEpoch,bridgeGeneration);
        frame.sequence=sequence;
        frame.feetX=client.thePlayer.posX;frame.feetY=client.thePlayer.posY;frame.feetZ=client.thePlayer.posZ;
        Vec3 eyes=client.thePlayer.getPositionEyes(1);
        frame.eyeX=eyes.xCoord;frame.eyeY=eyes.yCoord;frame.eyeZ=eyes.zCoord;
        frame.yaw=client.thePlayer.rotationYaw;
        frame.forward=input.forward;frame.strafe=input.strafe;
        frame.velocityX=client.thePlayer.motionX;frame.velocityZ=client.thePlayer.motionZ;
        frame.predictionTicks=bridgePrediction.getValue();
        frame.reach=client.playerController.getBlockReachDistance();
        frame.authorized=validContext() && sameScene()
                && (!requireBinding || contextRotation!=null && contextRotation.isConnection(contextPackets));
        frame.eagleEnabled=false;frame.physicalEdgeRisk=false;
        return frame;
    }

    private int bridgeBlockLevel(PlacementContext context) {
        Minecraft client=Minecraft.getMinecraft();
        return net.minecraft.util.MathHelper.floor_double(
                me.youngjibbit.core.world.ScaffoldPlacementService.yLevel(client.thePlayer.posY,
                        context,keyDown(client.gameSettings.keyBindJump)));
    }

    /** Enable may capture checked geometry; only a real PRE may spend planner/rotation authority. */
    private void previewBridgeOnEnable(long generation) {
        long sequence=contextRotation==null?-1:contextRotation.getMotionSequence(),acquisition=bridgeGeneration;
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        Minecraft client=Minecraft.getMinecraft();
        BridgeInput input=bridgeInput(client);
        float yaw=client.thePlayer.rotationYaw;
        AdaptiveBridgePlanner.Frame frame=bridgeFrame(sequence,input,false);
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        PlacementContext context=new PlacementContext(false,keepY.getValue(),keepYCoord);
        PlacementTarget preview=bridgeAdapter.findEarlyTarget(RuntimeServices.scaffoldPlacement(),
                frame,bridgeBlockLevel(context),null);
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        bridgePreview=preview;
        bridgeScope=frame.scope;
        rememberBridgeDirection(input,yaw);
    }

    public AdaptiveBridgeSafetyController.State bridgeSafetyState() { return bridgeSafety.getState(); }

    /** Pure identity check for the manager's reentrant PRE queries. */
    public boolean isCurrentBridgeScope(AdaptiveBridgePlanner.Scope scope) {
        return scope!=null && active==this && isToggled() && adaptiveBridge() && scope.owner==this
                && scope.acquisitionGeneration==bridgeGeneration && scope.world==contextWorld
                && scope.player==contextPlayer && scope.connection==contextPackets
                && scope.connectionEpoch==contextEpoch;
    }

    private AdaptiveBridgeMotion.Settings bridgeRotationSettings() {
        AdaptiveBridgeMotion.Mode mode="Godbridge".equals(bridgeRotationProfile.getValue())
                ?AdaptiveBridgeMotion.Mode.GODBRIDGE:AdaptiveBridgeMotion.Mode.ADAPTIVE;
        AdaptiveBridgeMotion.Speed speed="Calm".equals(bridgeRotationSpeed.getValue())
                ?AdaptiveBridgeMotion.Speed.CALM:"Fast".equals(bridgeRotationSpeed.getValue())
                ?AdaptiveBridgeMotion.Speed.FAST:AdaptiveBridgeMotion.Speed.BALANCED;
        return new AdaptiveBridgeMotion.Settings(mode,speed,bridgeRotationVariation.getValue(),
                yawSpeed.getValue(),pitchSpeed.getValue());
    }

    /** Third opt-in approach: bounded PRE proposals, original C03 and POST-only placement. */
    private void bridgeMotion(EventMotion event,long sequence,long generation) {
        Minecraft client=Minecraft.getMinecraft();
        if(event.isPre()) {
            // No re-entry/second planner budget in one physical motion sequence.
            if(bridgeLastPreSequence==sequence)return;
            bridgeLastPreSequence=sequence;
            long acquisition=bridgeGeneration;
            BridgeInput input=bridgeInput(client);
            float cameraYaw=client.thePlayer.rotationYaw;
            if(bridgeDirectionChanged(input,cameraYaw)) {
                acquisition=revokeBridge(sequence,generation,acquisition);
                if(acquisition<0)return;
            }
            frameTarget=currentTarget=null;
            frameSequence=sequence;
            framePre=false;downwardsState=false;
            if(client.thePlayer.onGround)keepYCoord=Math.floor(client.thePlayer.posY-1);
            PlacementContext context=new PlacementContext(false,keepY.getValue(),keepYCoord);
            me.youngjibbit.core.world.ScaffoldPlacementService service=RuntimeServices.scaffoldPlacement();
            AdaptiveBridgePlanner.Frame frame=bridgeFrame(sequence,input,true);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            // Keep a still-needed original hitpoint; otherwise use the old checked
            // gap search before looking ahead from an occupied foot cell.
            PlacementTarget host=service.stillNeeded(bridgePreview,context)?bridgePreview:null;
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(host==null)host=service.findTarget(context,"Back",false,null);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(host==null)host=bridgeAdapter.findEarlyTarget(service,frame,bridgeBlockLevel(context),bridgePreview);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            bridgeScope=frame.scope;
            bridgePreview=host;
            rememberBridgeDirection(input,cameraYaw);
            if(host==null) {
                bridgeSafety.observePlan(bridgeScope,sequence,false);
                requestAcceptedHold();return;
            }
            boolean keepFace=bridgeProfileAnchor!=null
                    && service.rotationIdentity(bridgeProfileAnchor)==service.rotationIdentity(host);
            AdaptiveBridgeScaffoldAdapter.Prepared prepared=keepFace
                    ?bridgeAdapter.prepareKeeping(service,host,frame):bridgeAdapter.prepare(service,host,frame);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(prepared==null || !service.isValid(prepared.target)) {
                if(!bridgeCurrent(sequence,generation,acquisition))return;
                bridgeSafety.observePlan(bridgeScope,sequence,false);
                requestAcceptedHold();return;
            }
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            AdaptiveBridgeMotion.Settings settings=bridgeRotationSettings();
            Vec3 eyes=client.thePlayer.getPositionEyes(1);
            PlacementTarget anchor=bridgeProfileAnchor;
            boolean kept=anchor!=null && service.rotationIdentity(anchor)==service.rotationIdentity(prepared.target)
                    && service.isValid(anchor);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(!kept) {
                anchor=prepared.target;
                if("Godbridge".equals(bridgeRotationProfile.getValue())) {
                    // Classic 45-degree yaw / 83.52-degree pitch are checked geometric preferences.
                    // Acquire it once; later frames keep this exact face anchor.
                    PlacementTarget preferred=service.preferBridgeGodbridge(anchor,eyes);
                    if(!bridgeCurrent(sequence,generation,acquisition))return;
                    if(preferred!=null)anchor=preferred;
                }
                bridgeProfileAnchor=anchor;
            }
            PlacementTarget aimed=service.refineBridgeHit(anchor,contextRotation,bridgeScope,settings);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(aimed==null) {
                bridgeSafety.observePlan(bridgeScope,sequence,false);
                requestAcceptedHold();return;
            }
            frameTarget=currentTarget=aimed;
            bridgePreview=aimed;
            bridgeSafety.observePlan(bridgeScope,sequence,true);
            if(!sprint.getValue()) {
                noSprintSequence=sequence;noSprintGeneration=generation;
                client.thePlayer.setSprinting(false);
                if(!bridgeCurrent(sequence,generation,acquisition))return;
            }
            ScaffoldRotationPlan desired=ScaffoldRotationPlan.build("Back",eyes,aimed.getHitVec());
            if(desired==null)return;
            float[] pose=contextRotation.calculateBridgePlanRotations(bridgeScope,
                    service.rotationIdentity(aimed),desired.getYaw(),desired.getPitch(),settings);
            if(pose==null)return;
            if(!bridgeCurrent(sequence,generation,acquisition) || !service.isValid(frameTarget))return;
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            contextRotation.request("scaffold",RotationManager.SCAFFOLD_PRIORITY,pose[0],pose[1]);
        } else if(event.isPost() && frameSequence==sequence) {
            // Exactly one POST placement attempt per accepted physical C03 frame,
            // even when the C08 transport rejects the first action. A duplicate
            // motion callback must not turn the same C03 into multiple C08s.
            if(bridgeLastPostSequence==sequence)return;
            bridgeLastPostSequence=sequence;
            PlacementTarget original=frameTarget;
            if(original==null)return;
            long acquisition=bridgeGeneration;
            AdaptiveBridgePlanner.Scope scope=bridgeScope;
            if(scope==null || scope.acquisitionGeneration!=acquisition)return;
            me.youngjibbit.core.world.ScaffoldPlacementService service=RuntimeServices.scaffoldPlacement();
            PlacementContext context=new PlacementContext(false,keepY.getValue(),keepYCoord);
            boolean needed=service.stillNeeded(original,context);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(!needed) {releasePlacement();return;}
            // The locally accepted pose can refine a hit vector, but never support,
            // face, destination or stack of the PRE intent.
            PlacementTarget accepted=service.alignAcceptedHit(original,contextRotation);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(accepted==null) {bridgeSafety.invalidate(scope);releasePlacement();return;}
            frameTarget=currentTarget=accepted;
            bridgeSafety.ready(scope,sequence);
            bridgeSafety.placing(scope,sequence);
            boolean placed=tryPlace(false);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            bridgeSafety.placementResult(scope,sequence,placed);
            if(placed) {bridgePreview=null;bridgeProfileAnchor=null;}
            boolean valid=placed || service.isValid(frameTarget);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(placed || !valid)releasePlacement();
        }
    }

    private void arsenicMotion(EventMotion event,long sequence,long generation) {
        if(event.isPre()) {
            if(frameSequence==sequence)return;
            frameTarget=currentTarget=null;frameSequence=sequence;framePre=false;downwardsState=false;
            me.youngjibbit.core.world.ArsenicScaffoldController.Prepared prepared=arsenicController.prepare(contextRotation,eagle.getValue(),safety.getValue(),arsenicMinSpeed.getValue(),arsenicMaxSpeed.getValue(),softArsenicMotion.getValue());
            if(!continueMotion(sequence,generation))return;
            if(prepared==null)return;
            if(prepared.target==null) { requestAcceptedHold(); return; }
            frameTarget=currentTarget=prepared.target;
            // World/geometry callbacks may disable or reconfigure this module during prepare.
            // Publish only after revalidating that exact lifecycle and scene.
            contextRotation.request("scaffold",RotationManager.SCAFFOLD_PRIORITY,prepared.yaw,prepared.pitch);
        } else if(event.isPost() && frameSequence==sequence) {
            PlacementTarget aligned=arsenicController.acceptedPlan(frameTarget,contextRotation,eagle.getValue(),safety.getValue());
            if(!continueMotion(sequence,generation))return;
            frameTarget=currentTarget=aligned;
            boolean placed=aligned!=null && tryPlace(false);
            if(!continueMotion(sequence,generation))return;
            arsenicController.finish(contextRotation,eagle.getValue(),safety.getValue(),placed);
            if(!continueMotion(sequence,generation))return;
            if(placed || aligned==null)releasePlacement();
        }
    }

    @Subscribe private final Listener<me.youngjibbit.event.player.MovementInputEvent> arsenicInput=new Listener<>(event->{
        if(event.isCancelled() || !isToggled() || !arsenic())return;
        long generation=lifecycleGeneration;
        long sequence=contextRotation==null?-1:contextRotation.getMotionSequence();
        if(!continueMotion(sequence,generation))return;
        me.youngjibbit.event.player.MovementInputEvent candidate=new me.youngjibbit.event.player.MovementInputEvent(
                event.getStrafe(),event.getForward(),event.isJump(),event.isSneak());
        arsenicController.applyInput(candidate,eagle.getValue(),safety.getValue());
        if(!continueMotion(sequence,generation))return;
        event.setStrafe(candidate.getStrafe());event.setForward(candidate.getForward());event.setSneak(candidate.isSneak());
    });

    /** Keep sprint admission and jump acceleration consistent in the ordinary physics path. */
    public static boolean allowsSprint() {
        Scaffold scaffold=active;
        if (scaffold==null || !scaffold.isToggled() || !scaffold.validContext() || !scaffold.sameScene()) return true;
        if (scaffold.arsenic()) return false;
        // Keep a valid plan's decision for this step even if PRE placement consumed the plan.
        return scaffold.sprint.getValue() || scaffold.noSprintGeneration != scaffold.lifecycleGeneration
                || scaffold.noSprintSequence != scaffold.contextRotation.getMotionSequence();
    }

    @Subscribe
    private final Listener<WorldEvent> worldReset = new Listener<>(event -> clearState());

    @Subscribe
    public final Listener<TickEvent> onTickEvent = new Listener<>(event -> {
        if(adaptiveBridge()) {
            if(contextRotation==null)return;
            long sequence=contextRotation.getMotionSequence(),generation=lifecycleGeneration,
                    acquisition=bridgeGeneration;
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            PlacementTarget preview=bridgePreview;
            if(preview!=null) {
                boolean valid=RuntimeServices.scaffoldPlacement().isValid(preview);
                if(!bridgeCurrent(sequence,generation,acquisition))return;
                if(!valid)revokeBridge(sequence,generation,acquisition);
            }
            return;
        }
        if (!liveContext() || !sameScene()) clearState();
        else if (!validContext()) suspendPlacement();
        else if (currentTarget != null && !RuntimeServices.scaffoldPlacement().isValid(currentTarget)) {
            // End the plan before movement; its accepted owner remains usable in this active scene.
            releasePlacement();
        }
    });

    /** Applied after every MovementInputEvent consumer, on the existing fresh-input hook. */
    private void applyBridgeSafety(MovementInput input) {
        if(input==null || active!=this || !isToggled() || !adaptiveBridge() || contextRotation==null)return;
        long sequence=contextRotation.getMotionSequence(),generation=lifecycleGeneration,
                acquisition=bridgeGeneration;
        if(!bridgeCurrent(sequence,generation,acquisition) || bridgeLastInputSequence==sequence)return;
        bridgeLastInputSequence=sequence;
        final long fresh=++bridgeFreshInputSequence;
        Minecraft client=Minecraft.getMinecraft();
        RotationManager.MovementRotation movement=contextRotation.getPreparedMovement();
        String owner=movement==null?contextRotation.getConfirmedOwner(contextPlayer,contextWorld,contextPackets)
                :movement.source;
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        if(owner!=null && !"scaffold".equals(owner)) {
            revokeBridge(sequence,generation,acquisition);
            return;
        }
        BridgeInput physical=new BridgeInput(input.moveForward,input.moveStrafe);
        float cameraYaw=client.thePlayer.rotationYaw;
        if(bridgeDirectionChanged(physical,cameraYaw)) {
            acquisition=revokeBridge(sequence,generation,acquisition);
            if(acquisition<0)return;
            movement=contextRotation.getPreparedMovement();
            if(!bridgeCurrent(sequence,generation,acquisition))return;
        }
        rememberBridgeDirection(physical,cameraYaw);
        if(bridgeScope==null)bridgeScope=new AdaptiveBridgePlanner.Scope(this,contextWorld,contextPlayer,
                contextPackets,contextEpoch,acquisition);
        AdaptiveBridgePlanner.Scope scope=bridgeScope;

        float forward=input.moveForward,strafe=input.moveStrafe,physicsYaw=cameraYaw;
        MoveFixController.Mode correction=MoveFixController.Mode.OFF;
        if(movement!=null) {
            physicsYaw=movement.yaw;correction=movement.mode;
        } else if("scaffold".equals(contextRotation.getConfirmedOwner(contextPlayer,contextWorld,contextPackets))) {
            physicsYaw=contextRotation.getEffectiveYaw();correction=moveFixMode();
        }
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        if(correction!=MoveFixController.Mode.OFF) {
            // Prediction only: moveFlying retains its sole correction claim.
            MoveFixController.Input corrected=MoveFixController.correct(strafe,forward,cameraYaw,physicsYaw,correction);
            strafe=corrected.strafe;forward=corrected.forward;
        } else physicsYaw=cameraYaw;
        double length=Math.hypot(forward,strafe);
        if(length>1) {forward/=length;strafe/=length;}
        double yaw=Math.toRadians(physicsYaw);
        double velocityX=client.thePlayer.motionX,velocityZ=client.thePlayer.motionZ;
        // The input lead is an estimate. Probe residual velocity independently
        // so opposite keys cannot hide outward momentum by overestimating braking.
        double dx=velocityX+(-Math.sin(yaw)*forward+Math.cos(yaw)*strafe)*.18;
        double dz=velocityZ+(Math.cos(yaw)*forward+Math.sin(yaw)*strafe)*.18;
        AxisAlignedBB body=client.thePlayer.getEntityBoundingBox();
        if(!boundedBridgeBox(body)
                || body.maxX-body.minX<=.002 || body.maxX-body.minX>2
                || body.maxZ-body.minZ<=.002 || body.maxZ-body.minZ>2)return;
        final AxisAlignedBB feet=new AxisAlignedBB(body.minX+.001,body.minY-.05,body.minZ+.001,
                body.maxX-.001,body.minY,body.maxZ-.001);
        final long scopedAcquisition=acquisition;
        // One shared current footprint plus two three-point sweeps: at most seven
        // real collision queries per fresh input, each no farther than .6 blocks.
        AdaptiveBridgeSafetyController.SupportProbe floor=new AdaptiveBridgeSafetyController.SupportProbe() {
            private boolean currentQueried,currentSupported;
            @Override public boolean supported(double x,double z) {
                if(x==0 && z==0) {
                    if(!currentQueried) {
                        currentQueried=true;
                        currentSupported=bridgeGround(feet,sequence,generation,scopedAcquisition);
                    }
                    return currentSupported;
                }
                return bridgeGround(feet.offset(x,0,z),sequence,generation,scopedAcquisition);
            }
        };
        AdaptiveBridgeSafetyController.Ground residual=AdaptiveBridgeSafetyController.probe(floor,velocityX,velocityZ);
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        AdaptiveBridgeSafetyController.Ground projected=dx==velocityX && dz==velocityZ?residual
                :AdaptiveBridgeSafetyController.probe(floor,dx,dz);
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        PlacementTarget target=frameTarget;
        boolean candidate=target!=null && RuntimeServices.scaffoldPlacement().isValid(target);
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        if(target!=null && !candidate) {
            acquisition=revokeBridge(sequence,generation,acquisition);
            if(acquisition<0)return;
            scope=new AdaptiveBridgePlanner.Scope(this,contextWorld,contextPlayer,contextPackets,contextEpoch,acquisition);
            bridgeScope=scope;
        }
        AdaptiveBridgeSafetyController.Snapshot sample=new AdaptiveBridgeSafetyController.Snapshot();
        sample.scope=scope;sample.inputSequence=fresh;
        sample.mode=AdaptiveBridgeSafetyController.Mode.fromSetting(bridgeEdgeSafety.getValue());
        sample.enabled=sample.contextValid=sample.ownerAllowed=true;
        sample.grounded=client.thePlayer.onGround;
        sample.currentSupported=residual.currentSupported && projected.currentSupported;
        sample.pathSupported=residual.pathSupported && projected.pathSupported;
        sample.hasCandidate=candidate;
        // PreparedMovement is not a current C03 acceptance. POST reports READY separately.
        sample.acceptedIntent=false;
        sample.recovering=bridgeSafety.getState()==AdaptiveBridgeSafetyController.State.RECOVERING;
        AdaptiveBridgeSafetyController.Decision decision=bridgeSafety.update(sample);
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        AdaptiveBridgeSafetyController.Input guarded=decision.apply(input.moveForward,input.moveStrafe,input.sneak);
        input.moveForward=guarded.forward;input.moveStrafe=guarded.strafe;input.sneak=guarded.sneak;
        // Physical jumps remain the user's choice. Earlier automatic jumps must
        // not silently turn off the normal grounded sneak collision protection.
        if(decision.requestSneak && !keyDown(client.gameSettings.keyBindJump))input.jump=false;
    }

    /** Keep floor-to-int conversion and the small cell loops within the world coordinate budget. */
    private static boolean boundedBridgeBox(AxisAlignedBB box) {
        return box!=null && Double.isFinite(box.minY)
                && Double.isFinite(box.minX) && Double.isFinite(box.maxX)
                && Double.isFinite(box.minZ) && Double.isFinite(box.maxZ)
                && box.minX>=-30000000D && box.maxX<=30000000D
                && box.minZ>=-30000000D && box.maxZ<=30000000D
                && box.maxX>box.minX && box.maxZ>box.minZ;
    }

    /** Thin real foot collision, with bounded loaded-cell checks at each probe. */
    private boolean bridgeGround(AxisAlignedBB feet,long sequence,long generation,long acquisition) {
        if(!bridgeCurrent(sequence,generation,acquisition))return false;
        if(!boundedBridgeBox(feet) || feet.minY<0 || feet.minY>=256)return false;
        Minecraft client=Minecraft.getMinecraft();
        int y=net.minecraft.util.MathHelper.floor_double(feet.minY);
        int minX=net.minecraft.util.MathHelper.floor_double(feet.minX),
                maxX=net.minecraft.util.MathHelper.floor_double(feet.maxX-1e-7),
                minZ=net.minecraft.util.MathHelper.floor_double(feet.minZ),
                maxZ=net.minecraft.util.MathHelper.floor_double(feet.maxZ-1e-7);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            BlockPos cell=new BlockPos(x,y,z);
            boolean loaded=client.theWorld.isBlockLoaded(cell);
            if(!bridgeCurrent(sequence,generation,acquisition) || !loaded)return false;
            boolean inside=client.theWorld.getWorldBorder().contains(cell);
            if(!bridgeCurrent(sequence,generation,acquisition) || !inside)return false;
        }
        boolean supported=!client.theWorld.getCollidingBoundingBoxes(client.thePlayer,feet).isEmpty();
        return bridgeCurrent(sequence,generation,acquisition) && supported;
    }

    /** Applied to freshly read movement input only; the physical/key-binding sneak state stays intact. */
    public static void applyDownwardsInput(MovementInput input) {
        applyDownwardsInput(input, true);
    }

    public static void applyDownwardsInput(MovementInput input, boolean allowInterval) {
        Minecraft mc = Minecraft.getMinecraft();
        Scaffold scaffold = active;
        if (scaffold != null && scaffold.isToggled() && !scaffold.arsenic() && !scaffold.adaptiveBridge() && scaffold.validContext()
                && scaffold.downwards.getValue() && mc.gameSettings.keyBindSneak.isKeyDown()
                && scaffold.currentTarget != null && RuntimeServices.scaffoldPlacement().isValid(scaffold.currentTarget)) suppressSneak(input);
        // This hook runs once after each fresh input read, after downwards suppression.
        // Other owners (including physical input and Eagle) keep their sneak and slowdown.
        if (allowInterval && input != null && scaffold != null && scaffold.isToggled()
                && scaffold.sneakAfterBlocks.getValue() > 0) {
            long generation = scaffold.lifecycleGeneration;
            long sequence = scaffold.contextRotation == null ? -1 : scaffold.contextRotation.getMotionSequence();
            if (!scaffold.continueMotion(sequence, generation)) return;
            if (scaffold.sneakInterval.input(++scaffold.freshInputSequence) && !input.sneak) {
                input.sneak = true;
                input.moveForward *= .3f;
                input.moveStrafe *= .3f;
            }
        }
        if(allowInterval && scaffold!=null && scaffold.adaptiveBridge())scaffold.applyBridgeSafety(input);
    }

    public static void suppressSneak(MovementInput input) {
        if (input != null && input.sneak) {
            input.sneak = false;
            input.moveForward = Math.max(-1, Math.min(1, input.moveForward / .3f));
            input.moveStrafe = Math.max(-1, Math.min(1, input.moveStrafe / .3f));
        }
    }

    private void applyRotations(EventMotion event,RotationProfile profile) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!rotations.getValue()) { endRotation(); return; }
        if (currentTarget == null) { requestAcceptedHold(); return; }
        if (!sameScene() || !contextRotation.isConnection(contextPackets)) return;
        Vec3 eyes = mc.thePlayer.getPlannedMovementPosition(event.getX(), event.getY(), event.getZ())
                .addVector(0, mc.thePlayer.getEyeHeight(), 0);
        ScaffoldRotationPlan plan = ScaffoldRotationPlan.build(rotationMode.getValue(), eyes, currentTarget.getHitVec());
        if (plan == null) { requestAcceptedHold(); return; }
        float[] smoothed = RuntimeServices.rotation().calculatePlanRotations("scaffold", event.getYaw(), event.getPitch(),
                RuntimeServices.scaffoldPlacement().rotationIdentity(currentTarget),plan.getYaw(), plan.getPitch(),eyes.distanceTo(currentTarget.getHitVec()),profile);
        RuntimeServices.rotation().request("scaffold", RotationManager.SCAFFOLD_PRIORITY, smoothed[0], smoothed[1]);
    }

    private void requestAcceptedHold() {
        rememberAcceptedRotation();
        if (!isToggled() || !usesRotations() || !validContext() || !sameScene()
                || !contextRotation.isConnection(contextPackets) || !hasHoldBasis) return;
        // A previously accepted wish may be requested again after rejection; only finishMotion grants rights.
        contextRotation.request("scaffold", RotationManager.SCAFFOLD_PRIORITY,
                holdYaw, holdPitch);
    }

    private boolean continueMotion(long sequence, long generation) {
        if(!currentMotion(sequence,generation))return false;
        boolean live=liveContext();
        if(!currentMotion(sequence,generation))return false;
        if(!live){clearState();return false;}
        boolean same=sameScene();
        // Scene suppliers may disable/re-enable us even while returning the same objects.
        // An old callback must neither publish work nor clear the replacement generation.
        if(!currentMotion(sequence,generation))return false;
        if(!same){clearState();return false;}
        boolean valid=validContext();
        if(!currentMotion(sequence,generation))return false;
        if(!valid){suspendPlacement();return false;}
        return currentMotion(sequence,generation);
    }
    /** Callback-free identity check, repeated after every context/supplier query. */
    private boolean currentMotion(long sequence,long generation) {
        return generation==lifecycleGeneration && isToggled() && contextRotation!=null
                && contextRotation==RuntimeServices.rotation() && contextRotation.getMotionSequence()==sequence;
    }
    private PlacementTarget findTarget(PlacementContext context,EventMotion event) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean rounded=roundRots.getValue() || "Godbridge".equals(rotationMode.getValue());
        Vec3 eyes=rounded?mc.thePlayer.getPlannedMovementPosition(event.getX(),event.getY(),event.getZ()).addVector(0,mc.thePlayer.getEyeHeight(),0):null;
        return RuntimeServices.scaffoldPlacement().findTarget(context,rotationMode.getValue(),roundRots.getValue(),eyes);
    }

    private boolean tryPlace(boolean pre) {
        if (!AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT)) return false;
        RuntimeObservation observation = RuntimeServices.observation();
        SceneContext scene = observation.captureScene();
        RotationManager rotation = RuntimeServices.rotation();
        PlacementTarget target = frameTarget;
        long sequence = frameSequence;
        boolean rotationRequired = usesRotations();
        java.util.function.BooleanSupplier sceneValid = () -> observation == RuntimeServices.observation()
                && observation.isCurrent(scene) && rotation == RuntimeServices.rotation();
        java.util.function.BooleanSupplier actionValid = () -> sceneValid.getAsBoolean() && isToggled()
                && validContext() && usesRotations() == rotationRequired
                && frameTarget == target && frameSequence == sequence
                && AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT);
        if(!arsenic() && !adaptiveBridge() && (roundRots.getValue() || "Godbridge".equals(rotationMode.getValue()))) {
            double step=roundRots.getValue()?90:45,yaw=RuntimeServices.rotation().getEffectiveYaw();
            if(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_double(yaw-Math.rint(yaw/step)*step))>.001) {
                diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.ROTATION);return false;
            }
        }
        long placementGeneration = lifecycleGeneration;
        boolean placed = RuntimeServices.scaffoldPlacement().placeWithAuthority(target, rotation, pre, sequence,
                placeDelay.getValue().longValue(), itemSpoof.getValue(), swing.getValue(), "Silent".equalsIgnoreCase(swingMode.getValue()),
                rotationRequired ? "scaffold" : null, actionValid, sceneValid);
        if (placed && sneakAfterBlocks.getValue() > 0 && continueMotion(sequence, placementGeneration))
            sneakInterval.placed(sneakAfterBlocks.getValue().intValue());
        if (!placed) diagnose(RuntimeServices.scaffoldPlacement().getLastRejection());
        return placed;
    }

    private void diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection reason) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!placementDiagnostics.getValue() || reason==me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.NONE
                || mc==null || mc.thePlayer==null) return;
        long now=diagnosticClock.getAsLong();
        if (now<nextDiagnosticNanos) return;
        nextDiagnosticNanos=now+2_000_000_000L;
        mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[Scaffold] "+reason.message));
    }

    public boolean correctionEnabled() { return isToggled() && validContext() && usesRotations()
            && (contextPlayer == null || sameScene()); }
    public MoveFixController.Mode moveFixMode() { return arsenic()?MoveFixController.Mode.SIMPLE:MoveFixController.Mode.fromSetting(movefix.getValue()); }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.fromSetting(bodyRotation.getValue()); }

    private boolean isMoving() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null
                && (mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F);
    }

    public static boolean isDownwards() {
        return downwardsState;
    }

    public double getNegativeExpand() {
        return 0.0D;
    }
}
