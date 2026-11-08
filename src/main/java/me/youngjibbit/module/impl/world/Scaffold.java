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
    private long bridgeGeneration,bridgeLastPreSequence=Long.MIN_VALUE;
    private final ComboValue approach=new ComboValue("Scaffold Approach",this,new String[]{"Neutron","Arsenic","Adaptive Bridge"},0);
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
    @Override public void beginConfigUpdate() { super.beginConfigUpdate();approach.reset();softArsenicMotion.reset();sneakAfterBlocks.reset(); }
    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) { return super.getValueByName(name!=null && name.replace("_","").replace(" ","").equalsIgnoreCase("SameY")?"keep_y":name); }
    @Override public void onValueChange() {
        long generation = ++lifecycleGeneration;
        sneakInterval.reset();
        releasePlacement();
        arsenicController.reset();
        bridgeAdapter.reset();
        bridgeGeneration++;
        bridgeLastPreSequence=Long.MIN_VALUE;
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
        bridgeAdapter.reset();
        bridgeGeneration++;
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
        bridgeAdapter.reset();
        bridgeGeneration++;
        bridgeLastPreSequence=Long.MIN_VALUE;
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

    /** Third opt-in approach: bounded PRE proposals, original C03 and POST-only placement. */
    private void bridgeMotion(EventMotion event,long sequence,long generation) {
        Minecraft client=Minecraft.getMinecraft();
        if(event.isPre()) {
            // No re-entry/second planner budget in one physical motion sequence.
            if(bridgeLastPreSequence==sequence)return;
            bridgeLastPreSequence=sequence;
            frameTarget=currentTarget=null;
            frameSequence=sequence;
            framePre=false;downwardsState=false;
            if(client.thePlayer.onGround)keepYCoord=Math.floor(client.thePlayer.posY-1);
            PlacementContext context=new PlacementContext(false,keepY.getValue(),keepYCoord);
            me.youngjibbit.core.world.ScaffoldPlacementService service=RuntimeServices.scaffoldPlacement();
            // The host picks an actually valid destination, slot, stack and first support.
            PlacementTarget host=service.findTarget(context,"Back",false,null);
            if(!continueMotion(sequence,generation))return;
            if(host==null){requestAcceptedHold();return;}

            AdaptiveBridgePlanner.Frame frame=new AdaptiveBridgePlanner.Frame();
            frame.scope=new AdaptiveBridgePlanner.Scope(this,contextWorld,contextPlayer,contextPackets,
                    contextEpoch,bridgeGeneration);
            frame.sequence=sequence;
            frame.feetX=client.thePlayer.posX;frame.feetY=client.thePlayer.posY;
            frame.feetZ=client.thePlayer.posZ;
            Vec3 eyes=client.thePlayer.getPositionEyes(1);
            frame.eyeX=eyes.xCoord;frame.eyeY=eyes.yCoord;frame.eyeZ=eyes.zCoord;
            frame.yaw=event.getYaw();
            frame.velocityX=client.thePlayer.motionX;frame.velocityZ=client.thePlayer.motionZ;
            MovementInput input=client.thePlayer.movementInput;
            frame.forward=input==null?0:input.moveForward;
            frame.strafe=input==null?0:input.moveStrafe;
            frame.reach=client.playerController.getBlockReachDistance();
            frame.authorized=validContext() && contextRotation.isConnection(contextPackets);
            // Eagle input / tower and timer mutation are explicitly out of this first bundle.
            frame.eagleEnabled=false;frame.physicalEdgeRisk=false;

            AdaptiveBridgeScaffoldAdapter.Prepared prepared=bridgeAdapter.prepare(service,host,frame);
            if(!continueMotion(sequence,generation))return;
            if(prepared==null || !service.isValid(prepared.target)) {
                requestAcceptedHold();return;
            }
            frameTarget=currentTarget=prepared.target;
            if(!sprint.getValue()) {
                noSprintSequence=sequence;noSprintGeneration=generation;
                client.thePlayer.setSprinting(false);
                if(!continueMotion(sequence,generation))return;
            }
            // Same source priority, old rotation trajectory and mouse-GCD snap.
            RotationProfile settings=rotationSettings.snapshot();
            float[] pose=contextRotation.calculatePlanRotations("scaffold",
                    event.getYaw(),event.getPitch(),service.rotationIdentity(prepared.target),
                    prepared.yaw,prepared.pitch,eyes.distanceTo(prepared.target.getHitVec()),settings);
            if(!continueMotion(sequence,generation) || !service.isValid(frameTarget))return;
            contextRotation.request("scaffold",RotationManager.SCAFFOLD_PRIORITY,pose[0],pose[1]);
        } else if(event.isPost() && frameSequence==sequence) {
            PlacementTarget original=frameTarget;
            if(original==null)return;
            me.youngjibbit.core.world.ScaffoldPlacementService service=RuntimeServices.scaffoldPlacement();
            PlacementContext context=new PlacementContext(false,keepY.getValue(),keepYCoord);
            if(!service.stillNeeded(original,context)) {releasePlacement();return;}
            // The server-accepted pose can refine a hit vector, but never support,
            // face, destination or stack of the PRE intent.
            PlacementTarget accepted=service.alignAcceptedHit(original,contextRotation);
            if(!continueMotion(sequence,generation))return;
            if(accepted==null) {releasePlacement();return;}
            frameTarget=currentTarget=accepted;
            boolean placed=tryPlace(false);
            if(!continueMotion(sequence,generation))return;
            if(placed || !service.isValid(frameTarget))releasePlacement();
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
        if (!liveContext() || !sameScene()) clearState();
        else if (!validContext()) suspendPlacement();
        else if (currentTarget != null && !RuntimeServices.scaffoldPlacement().isValid(currentTarget)) {
            // End the plan before movement; its accepted owner remains usable in this active scene.
            releasePlacement();
        }
    });

    /** Applied to freshly read movement input only; the physical/key-binding sneak state stays intact. */
    public static void applyDownwardsInput(MovementInput input) {
        applyDownwardsInput(input, true);
    }

    public static void applyDownwardsInput(MovementInput input, boolean allowInterval) {
        Minecraft mc = Minecraft.getMinecraft();
        Scaffold scaffold = active;
        if (scaffold != null && scaffold.isToggled() && !scaffold.arsenic() && scaffold.validContext()
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
