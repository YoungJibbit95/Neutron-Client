package me.youngjibbit.module.impl.world;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
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
import me.youngjibbit.core.world.AdaptiveBridgeLaneController;
import me.youngjibbit.core.world.BridgePlacementCadence;
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
    private final AdaptiveBridgeLaneController bridgeLane=new AdaptiveBridgeLaneController();
    private AdaptiveBridgeMotion.Task bridgeTask=AdaptiveBridgeMotion.Task.TRACKING;
    private long bridgeGeneration,bridgeLastPreSequence=Long.MIN_VALUE,bridgeLastPostSequence=Long.MIN_VALUE;
    private long bridgeLastPreparationSequence=Long.MIN_VALUE;
    private long bridgeLastInputSequence=Long.MIN_VALUE,bridgeFreshInputSequence;
    private PlacementTarget bridgePreview;
    private PlacementTarget bridgeProfileAnchor;
    private AdaptiveBridgePlanner.Scope bridgeScope;
    private boolean bridgeHasDirection;
    private float bridgeForward,bridgeStrafe,bridgeYaw;
    private final BridgePlacementCadence bridgeCadence=new BridgePlacementCadence();
    private BridgePlacementCadence.Scope bridgeCadenceScope;
    private Object bridgeCadenceStack;
    private int bridgeCadenceSelectedSlot=-1,bridgeCadenceBlockSlot=-1;
    private final java.util.function.LongSupplier bridgePlacementClock=()->RuntimeServices.rotation().readMonotonicNanos();
    private int bridgeConfirmedPlacements,bridgeBlocksSinceJump;
    private boolean bridgeDebugInfo;
    private final ComboValue approach=new ComboValue("Scaffold Approach",this,new String[]{"Neutron","Arsenic","Adaptive Bridge"},0);
    private final ComboValue bridgeEdgeSafety=new ComboValue("Bridge Edge Safety",this,
            new String[]{"Off","Sneak","Stop","Smart"},3);
    private final SliderValue bridgePrediction=new SliderValue("Bridge Prediction",this,1D,0,3,false);
    private final ComboValue bridgeRotationProfile=new ComboValue("Bridge Rotation Profile",this,
            new String[]{"Adaptive","Godbridge"},0);
    /** Aim preset for the bridge route; "Custom" exposes the four per-phase sliders below. */
    private final ComboValue bridgeAimSpeed=new ComboValue("Aim Speed",this,
            new String[]{"Calm","Balanced","Swift","Custom"},1);
    private final SliderValue bridgeInitialYawSpeed=new SliderValue("Initial Yaw Speed",this,55D,1,90,true);
    private final SliderValue bridgeInitialPitchSpeed=new SliderValue("Initial Pitch Speed",this,55D,1,90,true);
    private final SliderValue bridgeKeepingYawSpeed=new SliderValue("Keeping Yaw Speed",this,30D,1,90,true);
    private final SliderValue bridgeKeepingPitchSpeed=new SliderValue("Keeping Pitch Speed",this,30D,1,90,true);
    /** Load-only alias for the retired W08 combo; never serialized and never shown in the UI. */
    private final ComboValue bridgeRotationSpeedAlias=new ComboValue("Bridge Rotation Speed",null,
            new String[]{"Calm","Balanced","Fast"},1){
        @Override public void setValue(String label) {
            // Explicit migration at load time: Fast keeps its pace as the Swift preset.
            if(label!=null)bridgeAimSpeed.setValue(label.equalsIgnoreCase("Fast")?"Swift":label);
        }
    };
    private final SliderValue bridgeRotationVariation=new SliderValue("Bridge Rotation Variation",this,.25D,0,1,false);
    private final SliderValue bridgePlaceMinCps=new SliderValue("Place Min CPS",this,12D,1,20,false);
    private final SliderValue bridgePlaceMaxCps=new SliderValue("Place Max CPS",this,16D,1,20,false);
    private final ComboValue bridgeClickProfile=new ComboValue("Click Profile",this,
            new String[]{"Basic","Smoothstep","Perlin"},1);
    private final ComboValue bridgeClickInterpolation=new ComboValue("Place Interpolation",this,
            new String[]{"Stable","Smooth","Dynamic"},1);
    private final SliderValue bridgeClickVariation=new SliderValue("Variation Strength",this,.35D,0,1,false);
    private final CheckBoxValue bridgePlaceDebug=new CheckBoxValue("Place Debug",this,false);
    private final CheckBoxValue bridgeAutoJump=new CheckBoxValue("Godbridge Auto Jump",this,false);
    private final SliderValue bridgeJumpAfter=new SliderValue("Godbridge Jump After Blocks",this,8D,1,20,true);
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
    /** Layman preset for the Movefix combo; "Custom" keeps every legacy value reachable. */
    private final ComboValue bridgeMovementCorrection=new ComboValue("Movement Correction",this,
            new String[]{"Off","Balanced","Precise","Custom"},0);
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
    private ScaffoldActivitySnapshot activitySnapshot;
    private ActivityInventoryContext activityContext;
    private final java.util.function.LongSupplier activityClock = System::nanoTime;
    private long activitySequence, activityRevision, activityPlacements;
    private long activityPlacedMotionSequence = -1;

    /** Bounded cached-fact validation only: never queries geometry or plans/places a block. */
    public ScaffoldActivitySnapshot getActivitySnapshot() {
        ScaffoldActivitySnapshot cached = activitySnapshot;
        ActivityInventoryContext context = activityContext;
        long now = activityClock.getAsLong();
        Minecraft client = Minecraft.getMinecraft();
        if (cached == null || cached != activitySnapshot || active != this || !isToggled()
                || client == null || client.currentScreen != null || !client.inGameHasFocus || client.isGamePaused()
                || !cached.isFresh(now) || context == null || !context.isCurrent(client)) {
            if (cached != null && cached == activitySnapshot) clearActivity();
            return null;
        }
        return cached == activitySnapshot ? cached : null;
    }

    private void clearActivity() {
        activityRevision++;
        activityPlacedMotionSequence = -1;
        activitySnapshot = null;
        activityContext = null;
    }

    /** Runs only after an existing motion/input producer path, using the original block policy. */
    private void publishActivity(boolean placed, long generation, long revision) {
        Minecraft client = Minecraft.getMinecraft();
        long now = activityClock.getAsLong();
        if (generation != lifecycleGeneration || revision != activityRevision || active != this || !isToggled()
                || client == null || client.currentScreen != null || !client.inGameHasFocus || client.isGamePaused()
                || client.thePlayer == null || client.thePlayer != contextPlayer || client.theWorld != contextWorld
                || client.thePlayer.openContainer != client.thePlayer.inventoryContainer) return;
        ActivityInventoryContext scene = ActivityInventoryContext.capture(client);
        if (scene == null) return;
        me.youngjibbit.core.world.ScaffoldPlacementService service = RuntimeServices.scaffoldPlacement();
        int count = service.getBlockCount(), slot = service.getBlockSlot();
        if (count < 0) count = -1; // An overflowing/malformed live total is unknown, never fabricated.
        net.minecraft.item.ItemStack stack = slot < 0 ? null : client.thePlayer.inventory.getStackInSlot(slot);
        int itemId = stack == null ? -1 : net.minecraft.item.Item.getIdFromItem(stack.getItem());
        int metadata = stack == null ? -1 : stack.getMetadata();
        if (metadata < 0) metadata = -1;
        ActivityInventoryContext captured = slot < 0 ? ActivityInventoryContext.capture(client)
                : ActivityInventoryContext.capture(client, 36 + slot);
        if (captured == null || generation != lifecycleGeneration || revision != activityRevision
                || !scene.sameScene(client) || active != this || !isToggled()) return;
        boolean observedPlacement = placed || activityPlacedMotionSequence >= 0
                && activityPlacedMotionSequence == contextMotionSequence;
        ScaffoldActivitySnapshot.Status status = adaptiveBridge() ? ScaffoldActivitySnapshot.Status.valueOf(bridgeSafety.getState().name())
                : currentTarget != null ? ScaffoldActivitySnapshot.Status.ALIGNING
                : ScaffoldActivitySnapshot.Status.SEARCHING;
        if (observedPlacement && status != ScaffoldActivitySnapshot.Status.RECOVERING
                && status != ScaffoldActivitySnapshot.Status.EDGE_GUARD) status = ScaffoldActivitySnapshot.Status.PLACING;
        String profile = adaptiveBridge() ? bridgeRotationProfile.getValue() : arsenic() ? "" : rotationMode.getValue();
        ScaffoldActivitySnapshot previous = activitySnapshot;
        if (placed || previous == null || !previous.hasSameFacts(itemId, metadata, count, approach.getValue(), profile, status))
            activitySequence++;
        activityContext = captured;
        activitySnapshot = new ScaffoldActivitySnapshot(activitySequence, now, itemId, metadata, count,
                approach.getValue(), profile, status);
    }

    @Subscribe private final Listener<me.youngjibbit.event.packet.EventPacket> activityPackets =
            new Listener<>(event -> {
                if (event.isPost() && event.isInbound()
                        && (event.getPacket() instanceof net.minecraft.network.play.server.S2FPacketSetSlot
                        || event.getPacket() instanceof net.minecraft.network.play.server.S30PacketWindowItems
                        || event.getPacket() instanceof net.minecraft.network.play.server.S2DPacketOpenWindow
                        || event.getPacket() instanceof net.minecraft.network.play.server.S2EPacketCloseWindow))
                    clearActivity();
            });

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
            if(value==bridgeAutoJump || value==bridgeJumpAfter) {
                value.setVisibility(m->adaptiveBridge() && "Godbridge".equals(bridgeRotationProfile.getValue())
                        && (value!=bridgeJumpAfter || bridgeAutoJump.getValue()));
                continue;
            }
            if(value==bridgePlaceMinCps || value==bridgePlaceMaxCps || value==bridgeClickProfile
                    || value==bridgeClickInterpolation || value==bridgeClickVariation || value==bridgePlaceDebug) {
                value.setVisibility(m->adaptiveBridge());continue;
            }
            if(value==bridgeEdgeSafety || value==bridgePrediction || value==bridgeRotationProfile
                    || value==bridgeAimSpeed || value==bridgeRotationVariation) {
                value.setVisibility(m->adaptiveBridge());continue;
            }
            if(value==bridgeInitialYawSpeed || value==bridgeInitialPitchSpeed
                    || value==bridgeKeepingYawSpeed || value==bridgeKeepingPitchSpeed) {
                value.setVisibility(m->adaptiveBridge() && "Custom".equals(bridgeAimSpeed.getValue()));continue;
            }
            if(value==movefix) {
                value.setVisibility(m->!arsenic() && "Custom".equals(bridgeMovementCorrection.getValue()));continue;
            }
            if(value==bridgeMovementCorrection) {
                value.setVisibility(m->!arsenic());continue;
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
        bridgeAimSpeed.reset();bridgeInitialYawSpeed.reset();bridgeInitialPitchSpeed.reset();
        bridgeKeepingYawSpeed.reset();bridgeKeepingPitchSpeed.reset();bridgeRotationVariation.reset();
        bridgePlaceMinCps.reset();bridgePlaceMaxCps.reset();bridgeClickProfile.reset();
        bridgeClickInterpolation.reset();bridgeClickVariation.reset();bridgePlaceDebug.reset();
        bridgeAutoJump.reset();bridgeJumpAfter.reset();
        // Missing friendly keys must not overwrite an existing legacy Movefix value.
        // endConfigUpdate mirrors the effective legacy value after explicit settings load.
    }
    @Override public void endConfigUpdate() {
        super.endConfigUpdate();
        // Legacy configs only carry the Movefix combo: mirror its truth as the preset display.
        syncMovementCorrection();
    }
    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) {
        if(name!=null) {
            String normalized=name.replace("_","").replace(" ","");
            if(normalized.equalsIgnoreCase("SameY"))return super.getValueByName("keep_y");
            // Saved W08 configs keep the retired combo id; migrate it into Aim Speed here.
            if(normalized.equalsIgnoreCase("BridgeRotationSpeed"))return (me.youngjibbit.value.Value<T>) bridgeRotationSpeedAlias;
        }
        return super.getValueByName(name);
    }
    @Override public void valueChanged(me.youngjibbit.value.Value<?> value) {
        super.valueChanged(value);
        // The layman preset drives the legacy combo; any legacy write mirrors into the preset.
        if(value==bridgeMovementCorrection)applyMovementCorrection(bridgeMovementCorrection.getValue());
        else if(value==movefix)syncMovementCorrection();
    }
    @Override public void onValueChange() {
        clearActivity();
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
        clearActivity();
        final long generation = ++lifecycleGeneration;
        // Detach normal action listeners before any scene supplier can reenter the lifecycle.
        super.onDisable();
        try {
            RotationManager rotation = contextRotation;
            if (usesRotations() && rotation != null && rotation == RuntimeServices.rotation())
                rotationReturn.begin(rotation, "scaffold", adaptiveBridge()?MoveFixController.Mode.OFF:moveFixMode(),
                        bodyRotationMode(), arsenic() && softArsenicMotion.getValue(), () -> lifecycleGeneration == generation);
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
        clearActivity();
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
        if (event.isPre() && RuntimeServices.rotation().hasPreparedMovement() && !adaptiveBridge()) return;
        handleMotion(event, null);
    });

    @Subscribe private final Listener<EventRotation> prepareRotation = new Listener<>(preparation -> handleMotion(preparation.motion(), preparation));

    private void handleMotion(EventMotion event, EventRotation preparation) {
        long generation = lifecycleGeneration, revision = activityRevision, placements = activityPlacements;
        boolean completed = false;
        try { handleMotionObserved(event, preparation); completed = true; }
        finally {
            if (completed && generation == lifecycleGeneration && revision == activityRevision)
                publishActivity(activityPlacements != placements, generation, revision);
        }
    }

    private void handleMotionObserved(EventMotion event, EventRotation preparation) {
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
        if(adaptiveBridge()) {
            if(preparation!=null)prepareBridgeMovement(event,motionSequence,generation);
            else bridgeMotion(event,motionSequence,generation);
            return;
        }
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
        clearActivity();
        if(bridgeCadence!=null && bridgeCadenceScope!=null)bridgeCadence.reset(bridgeCadenceScope);
        bridgeCadenceScope=null;
        bridgeCadenceStack=null;
        bridgeCadenceSelectedSlot=bridgeCadenceBlockSlot=-1;
        bridgeConfirmedPlacements=bridgeBlocksSinceJump=0;
        if(bridgeDebugInfo) {info="";bridgeDebugInfo=false;}
        if(contextRotation!=null && bridgeScope!=null)contextRotation.resetBridgeMotion(bridgeScope);
        bridgeGeneration++;
        bridgePreview=null;
        bridgeProfileAnchor=null;
        bridgeScope=null;
        bridgeHasDirection=false;
        bridgeLane.reset();
        bridgeTask=AdaptiveBridgeMotion.Task.TRACKING;
        bridgeAdapter.reset();
        bridgeSafety.reset();
        if(sequences) {
            bridgeLastPreSequence=bridgeLastPostSequence=bridgeLastInputSequence=Long.MIN_VALUE;
            bridgeLastPreparationSequence=Long.MIN_VALUE;
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
        frame.velocityY=client.thePlayer.motionY;frame.grounded=client.thePlayer.onGround;
        frame.allowLowerRecovery=!keepY.getValue();
        if(contextRotation!=null) {
            frame.hasRotation=true;
            frame.rotationYaw=contextRotation.getEffectiveYaw();
            frame.rotationPitch=contextRotation.getEffectivePitch();
        }
        AdaptiveBridgeLaneController.Lane lane=moveFixMode()==MoveFixController.Mode.OFF?null:bridgeLane.current();
        if(lane!=null) {
            frame.hasLane=true;
            frame.laneX=lane.projectX(frame.feetX,frame.feetZ);
            frame.laneZ=lane.projectZ(frame.feetX,frame.feetZ);
            frame.laneDirectionX=lane.directionX;frame.laneDirectionZ=lane.directionZ;
        }
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

    /** Locally observed target blocks, not packet attempts or remote acknowledgements. */
    public int bridgePlacementCount() { return bridgeConfirmedPlacements; }

    private BridgePlacementCadence.Settings bridgePlacementSettings() {
        BridgePlacementCadence.Profile profile="Perlin".equals(bridgeClickProfile.getValue())
                ?BridgePlacementCadence.Profile.PERLIN:"Smoothstep".equals(bridgeClickProfile.getValue())
                ?BridgePlacementCadence.Profile.SMOOTHSTEP:BridgePlacementCadence.Profile.BASIC;
        BridgePlacementCadence.Interpolation interpolation="Stable".equals(bridgeClickInterpolation.getValue())
                ?BridgePlacementCadence.Interpolation.STABLE:"Dynamic".equals(bridgeClickInterpolation.getValue())
                ?BridgePlacementCadence.Interpolation.DYNAMIC:BridgePlacementCadence.Interpolation.SMOOTH;
        return new BridgePlacementCadence.Settings(bridgePlaceMinCps.getValue(),bridgePlaceMaxCps.getValue(),
                profile,interpolation,bridgeClickVariation.getValue());
    }

    private boolean currentBridgeCadence(AdaptiveBridgePlanner.Scope scope) {
        BridgePlacementCadence.Scope click=bridgeCadenceScope;
        return scope!=null && click!=null && click.owner==scope.owner && click.world==scope.world
                && click.player==scope.player && click.connection==scope.connection
                && click.epoch==scope.connectionEpoch && click.generation==scope.acquisitionGeneration;
    }

    /** Stack amounts may decrease normally; replacing a stack or selecting another slot ends its credit. */
    private boolean bridgeInventoryUnchanged(long sequence,long generation,long acquisition,boolean capture) {
        if(!bridgeCurrent(sequence,generation,acquisition))return false;
        int blockSlot=RuntimeServices.scaffoldPlacement().getBlockSlot();
        if(!bridgeCurrent(sequence,generation,acquisition))return false;
        Minecraft client=Minecraft.getMinecraft();
        int selected=client.thePlayer.inventory.currentItem;
        // Read after the last contextual callback; do not validate a cached inventory snapshot.
        Object stack=blockSlot>=0 && blockSlot<9?client.thePlayer.inventory.mainInventory[blockSlot]:null;
        if(capture) {
            bridgeCadenceSelectedSlot=selected;bridgeCadenceBlockSlot=blockSlot;bridgeCadenceStack=stack;
            return true;
        }
        if(selected==bridgeCadenceSelectedSlot && blockSlot==bridgeCadenceBlockSlot
                && stack==bridgeCadenceStack)return true;
        revokeBridge(sequence,generation,acquisition);
        return false;
    }

    /** Begin a PRE clock session without consuming a click or granting placement. */
    private boolean acquireBridgeCadence(AdaptiveBridgePlanner.Scope scope,long sequence,long generation,long acquisition) {
        if(!bridgeCurrent(sequence,generation,acquisition))return false;
        if(currentBridgeCadence(scope)) {
            if(bridgeCadence.isActive())return bridgeInventoryUnchanged(sequence,generation,acquisition,false);
            // A rollback tombstones this session. Rearm on a later real PRE.
            revokeBridge(sequence,generation,acquisition);return false;
        }
        long now=bridgePlacementClock.getAsLong();
        if(!bridgeCurrent(sequence,generation,acquisition))return false;
        if(!bridgeInventoryUnchanged(sequence,generation,acquisition,true))return false;
        BridgePlacementCadence.Scope next=new BridgePlacementCadence.Scope(scope.owner,scope.world,scope.player,
                scope.connection,scope.connectionEpoch,scope.acquisitionGeneration);
        if(!bridgeCadence.start(next,bridgePlacementSettings(),now)) {
            revokeBridge(sequence,generation,acquisition);return false;
        }
        bridgeCadenceScope=next;
        return bridgeCurrent(sequence,generation,acquisition);
    }

    private void updateBridgePlacementInfo() {
        if(!bridgePlaceDebug.getValue())return;
        bridgeDebugInfo=true;
        info=String.format(java.util.Locale.ROOT,"Place %.1f CPS | Blocks %d",
                bridgeCadence.getPendingCps(),bridgeConfirmedPlacements);
        if(bridgeAutoJump.getValue() && "Godbridge".equals(bridgeRotationProfile.getValue()))
            info+=" | Jump "+bridgeBlocksSinceJump+"/"+bridgeJumpAfter.getValue().intValue();
    }

    private AdaptiveBridgeMotion.Settings bridgeRotationSettings() {
        AdaptiveBridgeMotion.Mode mode="Godbridge".equals(bridgeRotationProfile.getValue())
                ?AdaptiveBridgeMotion.Mode.GODBRIDGE:AdaptiveBridgeMotion.Mode.ADAPTIVE;
        String aim=bridgeAimSpeed.getValue();
        if("Custom".equals(aim))
            return new AdaptiveBridgeMotion.Settings(mode,AdaptiveBridgeMotion.Speed.CUSTOM,
                    bridgeRotationVariation.getValue(),bridgeInitialYawSpeed.getValue(),
                    bridgeInitialPitchSpeed.getValue(),bridgeKeepingYawSpeed.getValue(),
                    bridgeKeepingPitchSpeed.getValue());
        AdaptiveBridgeMotion.Speed speed="Calm".equals(aim)?AdaptiveBridgeMotion.Speed.CALM
                :"Swift".equals(aim)?AdaptiveBridgeMotion.Speed.SWIFT:AdaptiveBridgeMotion.Speed.BALANCED;
        return new AdaptiveBridgeMotion.Settings(mode,speed,bridgeRotationVariation.getValue(),
                yawSpeed.getValue(),pitchSpeed.getValue(),yawSpeed.getValue(),pitchSpeed.getValue());
    }

    /** Rotation urgency uses collision support, independently of the optional sneak guard. */
    private AdaptiveBridgeMotion.Task bridgeRotationTask(AdaptiveBridgePlanner.Frame frame,boolean keeping,boolean cornerConnector,
            long sequence,long generation,long acquisition) {
        if(!bridgeCurrent(sequence,generation,acquisition))return null;
        Minecraft client=Minecraft.getMinecraft();
        AxisAlignedBB body=client.thePlayer.getEntityBoundingBox();
        if(!boundedBridgeBox(body) || body.maxX-body.minX<=.002 || body.maxX-body.minX>2
                || body.maxZ-body.minZ<=.002 || body.maxZ-body.minZ>2)return null;
        final AxisAlignedBB feet=new AxisAlignedBB(body.minX+.001,body.minY-.05,body.minZ+.001,
                body.maxX-.001,body.minY,body.maxZ-.001);
        AdaptiveBridgeSafetyController.SupportProbe floor=new AdaptiveBridgeSafetyController.SupportProbe() {
            private boolean queried,supported;
            @Override public boolean supported(double x,double z) {
                if(x==0 && z==0) {
                    if(!queried) {queried=true;supported=bridgeGround(feet,sequence,generation,acquisition);}
                    return supported;
                }
                return bridgeGround(feet.offset(x,0,z),sequence,generation,acquisition);
            }
        };
        boolean supported=floor.supported(0,0);
        if(!bridgeCurrent(sequence,generation,acquisition))return null;
        if(!supported && !frame.grounded && frame.velocityY<-.03)
            return AdaptiveBridgeMotion.Task.CLUTCH;
        // A diagonal corner needs this connector and then the diagonal cell.
        // Acquire the connector now so both fit the normal cadence before support ends.
        if(cornerConnector)return AdaptiveBridgeMotion.Task.CLUTCH;
        if(!supported)return AdaptiveBridgeMotion.Task.EDGE;
        double scale=Math.max(1,Math.hypot(frame.forward,frame.strafe));
        double forward=frame.forward/scale,strafe=frame.strafe/scale,yaw=Math.toRadians(frame.yaw);
        double dx=frame.velocityX+(-Math.sin(yaw)*forward+Math.cos(yaw)*strafe)*.18;
        double dz=frame.velocityZ+(Math.cos(yaw)*forward+Math.sin(yaw)*strafe)*.18;
        // Probe momentum separately: opposite input is not proof that braking has happened.
        AdaptiveBridgeSafetyController.Ground residual=AdaptiveBridgeSafetyController.probe(
                floor,frame.velocityX,frame.velocityZ);
        if(!bridgeCurrent(sequence,generation,acquisition))return null;
        AdaptiveBridgeSafetyController.Ground projected=dx==frame.velocityX && dz==frame.velocityZ?residual
                :AdaptiveBridgeSafetyController.probe(floor,dx,dz);
        if(!bridgeCurrent(sequence,generation,acquisition))return null;
        // The next physical step may exhaust the remaining footprint overlap.
        // Rescue speed must engage before falling makes this level unusable.
        if(!residual.pathSupported || !projected.pathSupported)return AdaptiveBridgeMotion.Task.CLUTCH;
        return keeping?AdaptiveBridgeMotion.Task.TRACKING:AdaptiveBridgeMotion.Task.APPROACH;
    }

    /**
     * Input preparation owns only the route intention and movement basis.
     * Physics can expose a hidden face and move the ray origin: the single real
     * planning/turn budget therefore belongs to walking PRE, after movement.
     */
    private void prepareBridgeMovement(EventMotion event,long sequence,long generation) {
        if(bridgeLastPreparationSequence==sequence)return;
        bridgeLastPreparationSequence=sequence;
        long acquisition=bridgeGeneration;
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        Minecraft client=Minecraft.getMinecraft();
        BridgeInput physical=bridgeInput(client);
        float cameraYaw=client.thePlayer.rotationYaw;
        if(bridgeDirectionChanged(physical,cameraYaw)) {
            acquisition=revokeBridge(sequence,generation,acquisition);
            if(acquisition<0)return;
        }
        rememberBridgeDirection(physical,cameraYaw);
        if(client.thePlayer.onGround)keepYCoord=Math.floor(client.thePlayer.posY-1);
        PlacementTarget preview=bridgePreview;
        if(preview!=null && !RuntimeServices.scaffoldPlacement().isValid(preview))preview=null;
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        bridgePreview=preview;
        frameTarget=currentTarget=preview;
        frameSequence=sequence;framePre=false;downwardsState=false;
        bridgeScope=new AdaptiveBridgePlanner.Scope(this,contextWorld,contextPlayer,contextPackets,
                contextEpoch,acquisition);
        if(!acquireBridgeCadence(bridgeScope,sequence,generation,acquisition) || bridgeCadenceBlockSlot<0)return;
        if(!sprint.getValue()) {
            noSprintSequence=sequence;noSprintGeneration=generation;
            client.thePlayer.setSprinting(false);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
        }
        contextRotation.requestBridgeMovement(bridgeScope,
                hasHoldBasis?holdYaw:event.getYaw(),hasHoldBasis?holdPitch:event.getPitch());
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
            // Select the needed route cell before choosing a supporting face.
            // The legacy radial search can choose a parallel row and has no place here.
            PlacementTarget host=bridgeAdapter.findEarlyTarget(service,frame,bridgeBlockLevel(context),bridgePreview);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            bridgeScope=frame.scope;
            bridgePreview=host;
            rememberBridgeDirection(input,cameraYaw);
            if(!acquireBridgeCadence(bridgeScope,sequence,generation,acquisition))return;
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
            Vec3 eyes=client.thePlayer.getPlannedMovementPosition(event.getX(),event.getY(),event.getZ())
                    .addVector(0,client.thePlayer.getEyeHeight(),0);
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
            PlacementTarget retained=service.retainBridgeHit(anchor,
                    contextRotation.getEffectiveYaw(),contextRotation.getEffectivePitch());
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            boolean connector=bridgeAdapter.isDiagonalConnector(frame,host,bridgeBlockLevel(context));
            AdaptiveBridgeMotion.Task task=bridgeRotationTask(frame,kept,connector,sequence,generation,acquisition);
            if(task==null || !bridgeCurrent(sequence,generation,acquisition))return;
            bridgeTask=task;
            // A still-valid ray is already a useful aim: do not displace it with
            // variation or restart a curve merely because the player moved.
            PlacementTarget aimed=retained!=null?retained
                    :service.refineBridgeHit(anchor,contextRotation,bridgeScope,settings);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(aimed==null) {
                bridgeSafety.observePlan(bridgeScope,sequence,false);
                requestAcceptedHold();return;
            }
            bridgePreview=aimed;
            if(!sprint.getValue()) {
                noSprintSequence=sequence;noSprintGeneration=generation;
                client.thePlayer.setSprinting(false);
                if(!bridgeCurrent(sequence,generation,acquisition))return;
            }
            ScaffoldRotationPlan desired=ScaffoldRotationPlan.build("Back",eyes,aimed.getHitVec());
            float[] pose=desired==null?null:contextRotation.calculateBridgePlanRotations(bridgeScope,
                    service.rotationIdentity(aimed),desired.getYaw(),desired.getPitch(),settings,task);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(pose==null || !service.isValid(aimed)) {
                if(bridgeCurrent(sequence,generation,acquisition))bridgeSafety.observePlan(bridgeScope,sequence,false);
                return;
            }
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            boolean finalized=contextRotation.requestBridgeFinalRotation(bridgeScope,pose[0],pose[1]);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            bridgeSafety.observePlan(bridgeScope,sequence,finalized);
            // An early hold is not a substitute for this motion's checked task.
            // Rejected/stalled turns must leave no POST placement intent behind.
            if(finalized)frameTarget=currentTarget=aimed;
        } else if(event.isPost() && frameSequence==sequence && bridgeLastPreSequence==sequence) {
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
            AdaptiveBridgePlanner.Frame placementFrame=bridgeFrame(sequence,bridgeInput(client),true);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            boolean needed=bridgeAdapter.stillNeeded(service,original,placementFrame,bridgeBlockLevel(context));
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(!needed) {releasePlacement();return;}
            // The locally accepted pose can refine a hit vector, but never support,
            // face, destination or stack of the PRE intent.
            PlacementTarget accepted=service.alignAcceptedHit(original,contextRotation);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(accepted==null) {bridgeSafety.invalidate(scope);releasePlacement();return;}
            frameTarget=currentTarget=accepted;
            bridgeSafety.ready(scope,sequence);
            if(!currentBridgeCadence(scope))return;
            long now=bridgePlacementClock.getAsLong();
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            BridgePlacementCadence.Attempt attempt=bridgeCadence.tryConsume(bridgeCadenceScope,
                    bridgePlacementSettings(),sequence,now,true,
                    bridgeTask==AdaptiveBridgeMotion.Task.EDGE || bridgeTask==AdaptiveBridgeMotion.Task.CLUTCH);
            if(attempt==null) {updateBridgePlacementInfo();return;}
            bridgeSafety.placing(scope,sequence);
            boolean placed=tryPlace(false);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            // A successful non-silent switch deliberately keeps the chosen block slot.
            // Do not refresh stack identity: exhaustion/replacement must end pending credit.
            if(placed && !itemSpoof.getValue() && client.thePlayer.inventory.currentItem==accepted.getSlot())
                bridgeCadenceSelectedSlot=accepted.getSlot();
            boolean confirmed=placed && service.hasLocallyPlacedBlock(accepted);
            if(!bridgeCurrent(sequence,generation,acquisition))return;
            if(confirmed) {
                activityPlacements++;
                activityPlacedMotionSequence=sequence;
                if(bridgeConfirmedPlacements<Integer.MAX_VALUE)bridgeConfirmedPlacements++;
                if(bridgeBlocksSinceJump<bridgeJumpAfter.getValue().intValue())bridgeBlocksSinceJump++;
            }
            bridgeSafety.placementResult(scope,sequence,confirmed);
            updateBridgePlacementInfo();
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
        long generation = lifecycleGeneration, revision = activityRevision;
        boolean completed = false;
        try { applyBridgeSafetyObserved(input); completed = true; }
        finally {
            if (completed && generation == lifecycleGeneration && revision == activityRevision)
                publishActivity(false, generation, revision);
        }
    }

    private void applyBridgeSafetyObserved(MovementInput input) {
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
        BridgeInput physical=bridgeInput(client);
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

        AdaptiveBridgeLaneController.Input laneInput=bridgeLane.update(client.thePlayer.posX,client.thePlayer.posZ,
                client.thePlayer.motionX,client.thePlayer.motionZ,cameraYaw,input.moveForward,input.moveStrafe,
                moveFixMode()!=MoveFixController.Mode.OFF && "scaffold".equals(owner));
        input.moveForward=laneInput.forward;input.moveStrafe=laneInput.strafe;
        // Adaptive correction preserves the camera-frame world vector exactly.
        // Repeating the legacy discrete transform here would predict a different path.
        float forward=input.moveForward,strafe=input.moveStrafe;
        double length=Math.hypot(forward,strafe);
        if(length>1) {forward/=length;strafe/=length;}
        double yaw=Math.toRadians(cameraYaw);
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
        applyBridgeAutoJump(input,sample,decision,sequence,generation,acquisition);
    }

    /** A one-sample jump request; all velocity and actual jump execution remain vanilla. */
    private void applyBridgeAutoJump(MovementInput input,AdaptiveBridgeSafetyController.Snapshot sample,
            AdaptiveBridgeSafetyController.Decision decision,long sequence,long generation,long acquisition) {
        if(!bridgeAutoJump.getValue() || !"Godbridge".equals(bridgeRotationProfile.getValue())
                || !bridgeCurrent(sequence,generation,acquisition))return;
        Minecraft client=Minecraft.getMinecraft();
        // The user's jump takes precedence and cancels our pending automatic cycle.
        if(keyDown(client.gameSettings.keyBindJump)) {
            bridgeBlocksSinceJump=0;updateBridgePlacementInfo();return;
        }
        if(bridgeBlocksSinceJump<bridgeJumpAfter.getValue().intValue()
                || !sample.grounded || !sample.currentSupported || !sample.pathSupported
                || decision.requestSneak || input.sneak || keyDown(client.gameSettings.keyBindSneak)
                || !Float.isFinite(input.moveForward) || !Float.isFinite(input.moveStrafe)
                || Math.hypot(input.moveForward,input.moveStrafe)<1e-5
                || client.thePlayer.hurtTime>0 || client.thePlayer.capabilities.isFlying
                || client.thePlayer.capabilities.allowFlying
                || client.thePlayer.isUsingItem() || client.thePlayer.isRiding()
                || client.thePlayer.isOnLadder() || client.thePlayer.isInWater() || client.thePlayer.isInLava())return;
        if(!bridgeCurrent(sequence,generation,acquisition))return;
        boolean recovering=contextPackets.isRecoveringLocalImpulse(client.thePlayer);
        if(!bridgeCurrent(sequence,generation,acquisition) || recovering)return;
        if(!currentBridgeCadence(sample.scope)
                || !bridgeInventoryUnchanged(sequence,generation,acquisition,false)
                || bridgeCadenceBlockSlot<0)return;
        // No contextual/world callbacks after this final snapshot. Such callbacks may
        // have changed flight permission or physical input without changing the scene.
        if(client.gameSettings.keyBindJump.pressed) {
            bridgeBlocksSinceJump=0;updateBridgePlacementInfo();return;
        }
        if(!bridgeScopeCurrent(sequence,generation,acquisition)
                || client.thePlayer!=contextPlayer || client.theWorld!=contextWorld
                || !client.thePlayer.onGround || client.thePlayer.hurtTime>0
                || client.thePlayer.capabilities.allowFlying || client.thePlayer.capabilities.isFlying
                || client.currentScreen!=null || !client.inGameHasFocus
                || input.sneak || client.gameSettings.keyBindSneak.pressed
                || !Float.isFinite(input.moveForward) || !Float.isFinite(input.moveStrafe)
                || Math.hypot(input.moveForward,input.moveStrafe)<1e-5
                || !(bridgeCadenceStack instanceof net.minecraft.item.ItemStack)
                || ((net.minecraft.item.ItemStack)bridgeCadenceStack).stackSize<=0)return;
        input.jump=true;
        bridgeBlocksSinceJump=0;
        updateBridgePlacementInfo();
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
            if (scaffold.sneakInterval.input(++scaffold.freshInputSequence)) {
                if (!input.sneak) {
                    input.sneak = true;
                    input.moveForward *= .3f;
                    input.moveStrafe *= .3f;
                }
                // The earned final sneak owns automatic jump admission for this sample.
                // Preserve the player's physical jump, as the Adaptive edge guard does.
                if (!keyDown(mc.gameSettings.keyBindJump)) input.jump = false;
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
        long placementGeneration = lifecycleGeneration, placementActivity = activityRevision;
        Minecraft activityClient = Minecraft.getMinecraft();
        Object activityPlayer = activityClient == null ? null : activityClient.thePlayer;
        Object activityWorld = activityClient == null ? null : activityClient.theWorld;
        Object activityScreen = activityClient == null ? null : activityClient.currentScreen;
        boolean placed = RuntimeServices.scaffoldPlacement().placeWithAuthority(target, rotation, pre, sequence,
                placeDelay.getValue().longValue(), itemSpoof.getValue(), swing.getValue(), "Silent".equalsIgnoreCase(swingMode.getValue()),
                rotationRequired ? "scaffold" : null, actionValid, sceneValid);
        if (placed && !adaptiveBridge() && placementGeneration == lifecycleGeneration && placementActivity == activityRevision
                && active == this && isToggled() && Minecraft.getMinecraft() == activityClient
                && activityClient != null && activityClient.thePlayer == activityPlayer
                && activityClient.theWorld == activityWorld && activityClient.currentScreen == activityScreen) {
            activityPlacements++;
            activityPlacedMotionSequence = sequence;
        }
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
    /** Adaptive Bridge keeps movement intent independent of the placement aim for every enabled mode. */
    public boolean preservesBridgeHeading() { return adaptiveBridge(); }
    public MoveFixController.Mode moveFixMode() { return arsenic()?MoveFixController.Mode.SIMPLE:MoveFixController.Mode.fromSetting(movefix.getValue()); }

    /** Verified mapping: each layman preset binds exactly one reviewed Movefix value; Custom writes nothing. */
    private void applyMovementCorrection(String preset) {
        if(preset==null||"Custom".equals(preset))return;
        String mapped="Off".equals(preset)?"Aus":"Balanced".equals(preset)?"Einfach"
                :"Precise".equals(preset)?"Präzise":"Aus";
        if(!mapped.equals(movefix.getValue()))movefix.setValue(mapped);
    }

    /** Mirror a rewritten Movefix combo as the preset display; Custom stays chosen by its user. */
    private void syncMovementCorrection() {
        if("Custom".equals(bridgeMovementCorrection.getValue()))return;
        String current=movefix.getValue();
        String display="Aus".equals(current)?"Off":"Einfach".equals(current)?"Balanced"
                :"Präzise".equals(current)?"Precise":"Custom";
        if(!display.equals(bridgeMovementCorrection.getValue()))bridgeMovementCorrection.setValue(display);
    }
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
