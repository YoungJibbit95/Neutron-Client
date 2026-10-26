package me.youngjibbit.module.impl.world;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.world.PlacementContext;
import me.youngjibbit.core.world.PlacementTarget;
import me.youngjibbit.core.world.ScaffoldRotationPlan;
import me.youngjibbit.core.world.TowerController;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.RotationSettings;
import me.youngjibbit.core.combat.RotationProfile;
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
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "Scaffold",
        description = "automated block placement",
        category = Category.World
)
public class Scaffold extends Module {

    private final TimerControl timerControl = new TimerControl();
    private final TowerController towerController = new TowerController();

    private final ComboValue placeTiming = new ComboValue("Place Timing", this, new String[]{"Pre", "Post"}, 0);
    private final CheckBoxValue rotations = new CheckBoxValue("Rotations", this, true);
    private final MoveFixValue movefix = new MoveFixValue(this, false);
    private final ComboValue bodyRotation = new ComboValue("Body Rotation", this, new String[]{"Vanilla", "Direkt", "Natürlich"}, 0);
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
    }
    @Override public <T> me.youngjibbit.value.Value<T> getValueByName(String name) { return super.getValueByName(name!=null && name.replace("_","").replace(" ","").equalsIgnoreCase("SameY")?"keep_y":name); }
    @Override public void onValueChange() { releasePlacement(); }

    @Override
    public void onEnable() {
        clearState();
        active = this;
        captureContext();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        clearState();
        if (active == this) active = null;
        super.onDisable();
    }

    private boolean validContext() {
        return mc != null && mc.thePlayer != null && mc.theWorld != null && mc.playerController != null
                && mc.thePlayer.isEntityAlive() && !mc.thePlayer.isDead && !mc.thePlayer.isSpectator()
                && !mc.thePlayer.isRiding() && mc.currentScreen == null && mc.inGameHasFocus;
    }

    private void captureContext() {
        contextPlayer = mc == null ? null : mc.thePlayer;
        contextWorld = mc == null ? null : mc.theWorld;
        if (mc != null && mc.thePlayer != null) keepYCoord = Math.floor(mc.thePlayer.posY - 1);
    }

    private void releasePlacement() {
        RuntimeServices.rotation().reset("scaffold");
        RotationCorrections.reset("scaffold");
        currentTarget = frameTarget = null;
        frameSequence = -1;
        downwardsState = false;
        towerController.reset();
        if (mc != null && mc.timer != null) mc.timer.timerSpeed = timerControl.release(mc.timer.timerSpeed);
    }

    private void clearState() {
        releasePlacement();
        RuntimeServices.scaffoldPlacement().reset();
        contextPlayer = contextWorld = null;
        keepYCoord = 0;
    }

    @Subscribe
    public final Listener<EventMotion> onMotionEvent = new Listener<>(event -> {
        if (!validContext()) { diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.CONTEXT);clearState(); return; }
        if (contextPlayer != mc.thePlayer || contextWorld != mc.theWorld) { clearState(); captureContext(); }
        if (event.isPre()) {
            downwardsState = downwards.getValue() && mc.gameSettings.keyBindSneak.isKeyDown();
            if (mc.thePlayer.onGround) keepYCoord = Math.floor(mc.thePlayer.posY - 1);
            PlacementContext context = new PlacementContext(downwardsState, keepY.getValue(), keepYCoord);
            if (!RuntimeServices.scaffoldPlacement().stillNeeded(currentTarget, context))
                currentTarget = findTarget(context,event);
            if (currentTarget == null) { diagnose(RuntimeServices.scaffoldPlacement().getLastRejection());releasePlacement(); return; }

            // A valid placement plan owns this decision before Auto Jump consumes sprint's impulse.
            if (!sprint.getValue()) mc.thePlayer.setSprinting(false);
            if (autoJump.getValue() && mc.thePlayer.onGround && isMoving() && !mc.gameSettings.keyBindJump.isKeyDown()) mc.thePlayer.jump();
            towerController.apply(event, tower.getValue(), towerMode.getValue(), mc.gameSettings.keyBindJump.isKeyDown(), isMoving(), true);
            // Tower may move the local player. Finalize the plan before issuing a rotation or attempting placement.
            if (!RuntimeServices.scaffoldPlacement().stillNeeded(currentTarget, context))
                currentTarget = findTarget(context,event);
            if (currentTarget == null) { diagnose(RuntimeServices.scaffoldPlacement().getLastRejection());releasePlacement(); return; }
            if (mc.timer != null) mc.timer.timerSpeed = timerControl.apply(mc.timer.timerSpeed,
                    Math.max(.2f, Math.min(3, timerSpeed.getValue().floatValue())));

            RotationProfile profile=rotationSettings.snapshot();
            currentTarget=RuntimeServices.scaffoldPlacement().randomize(currentTarget,RuntimeServices.rotation(),profile);
            Vec3 plannedEyes=mc.thePlayer.getPlannedMovementPosition(event.getX(),event.getY(),event.getZ()).addVector(0,mc.thePlayer.getEyeHeight(),0);
            currentTarget=RuntimeServices.scaffoldPlacement().align(currentTarget,rotationMode.getValue(),roundRots.getValue(),plannedEyes);
            if(currentTarget==null){diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.NO_FACE);releasePlacement();return;}
            frameTarget = currentTarget;
            frameSequence = RuntimeServices.rotation().getMotionSequence();
            framePre = "Pre".equalsIgnoreCase(placeTiming.getValue());
            if (framePre && tryPlace(true)) { releasePlacement(); return; }
            if (!RuntimeServices.scaffoldPlacement().isValid(frameTarget)) { releasePlacement(); return; }
            applyRotations(event,profile);
        } else if (event.isPost()) {
            // Never search for a replacement in POST: this is exactly the PRE plan.
            if (frameTarget == null || frameSequence != RuntimeServices.rotation().getMotionSequence()) return;
            PlacementContext context = new PlacementContext(downwards.getValue() && mc.gameSettings.keyBindSneak.isKeyDown(), keepY.getValue(), keepYCoord);
            if (!RuntimeServices.scaffoldPlacement().stillNeeded(frameTarget, context)) { releasePlacement(); return; }
            if (!framePre && tryPlace(false)) releasePlacement();
            else if (!RuntimeServices.scaffoldPlacement().isValid(frameTarget)) releasePlacement();
        }
    });

    @Subscribe
    private final Listener<WorldEvent> worldReset = new Listener<>(event -> clearState());

    @Subscribe
    public final Listener<TickEvent> onTickEvent = new Listener<>(event -> {
        if (!validContext() || contextPlayer != mc.thePlayer || contextWorld != mc.theWorld) clearState();
        else if (currentTarget != null && !RuntimeServices.scaffoldPlacement().isValid(currentTarget)) {
            // Release the accepted movement basis before entity movement, even without another PRE.
            releasePlacement();
        }
    });

    /** Applied to freshly read movement input only; the physical/key-binding sneak state stays intact. */
    public static void applyDownwardsInput(MovementInput input) {
        Scaffold scaffold = active;
        if (scaffold != null && scaffold.isToggled() && scaffold.validContext()
                && scaffold.downwards.getValue() && mc.gameSettings.keyBindSneak.isKeyDown()
                && scaffold.currentTarget != null && RuntimeServices.scaffoldPlacement().isValid(scaffold.currentTarget)) suppressSneak(input);
    }

    public static void suppressSneak(MovementInput input) {
        if (input != null && input.sneak) {
            input.sneak = false;
            input.moveForward = Math.max(-1, Math.min(1, input.moveForward / .3f));
            input.moveStrafe = Math.max(-1, Math.min(1, input.moveStrafe / .3f));
        }
    }

    private void applyRotations(EventMotion event,RotationProfile profile) {
        if (!rotations.getValue() || currentTarget == null) { RuntimeServices.rotation().reset("scaffold"); return; }
        Vec3 eyes = mc.thePlayer.getPlannedMovementPosition(event.getX(), event.getY(), event.getZ())
                .addVector(0, mc.thePlayer.getEyeHeight(), 0);
        ScaffoldRotationPlan plan = ScaffoldRotationPlan.build(rotationMode.getValue(), eyes, currentTarget.getHitVec());
        if (plan == null) { RuntimeServices.rotation().reset("scaffold"); return; }
        float[] smoothed = RuntimeServices.rotation().calculatePlanRotations("scaffold", event.getYaw(), event.getPitch(),
                RuntimeServices.scaffoldPlacement().rotationIdentity(currentTarget),plan.getYaw(), plan.getPitch(),eyes.distanceTo(currentTarget.getHitVec()),profile);
        RuntimeServices.rotation().request("scaffold", RotationManager.SCAFFOLD_PRIORITY, smoothed[0], smoothed[1]);
    }
    private PlacementTarget findTarget(PlacementContext context,EventMotion event) {
        boolean rounded=roundRots.getValue() || "Godbridge".equals(rotationMode.getValue());
        Vec3 eyes=rounded?mc.thePlayer.getPlannedMovementPosition(event.getX(),event.getY(),event.getZ()).addVector(0,mc.thePlayer.getEyeHeight(),0):null;
        return RuntimeServices.scaffoldPlacement().findTarget(context,rotationMode.getValue(),roundRots.getValue(),eyes);
    }

    private boolean tryPlace(boolean pre) {
        if(roundRots.getValue() || "Godbridge".equals(rotationMode.getValue())) {
            double step=roundRots.getValue()?90:45,yaw=RuntimeServices.rotation().getEffectiveYaw();
            if(Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_double(yaw-Math.rint(yaw/step)*step))>.001) {
                diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.ROTATION);return false;
            }
        }
        boolean placed = RuntimeServices.scaffoldPlacement().place(frameTarget, RuntimeServices.rotation(), pre, frameSequence,
                placeDelay.getValue().longValue(), itemSpoof.getValue(), swing.getValue(), "Silent".equalsIgnoreCase(swingMode.getValue()));
        if (!placed) diagnose(RuntimeServices.scaffoldPlacement().getLastRejection());
        return placed;
    }

    private void diagnose(me.youngjibbit.core.world.ScaffoldPlacementService.Rejection reason) {
        if (!placementDiagnostics.getValue() || reason==me.youngjibbit.core.world.ScaffoldPlacementService.Rejection.NONE
                || mc==null || mc.thePlayer==null) return;
        long now=diagnosticClock.getAsLong();
        if (now<nextDiagnosticNanos) return;
        nextDiagnosticNanos=now+2_000_000_000L;
        mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[Scaffold] "+reason.message));
    }

    public boolean correctionEnabled() { return isToggled() && validContext() && rotations.getValue(); }
    public MoveFixController.Mode moveFixMode() { return MoveFixController.Mode.fromSetting(movefix.getValue()); }
    public BodyRotationController.Mode bodyRotationMode() { return BodyRotationController.Mode.fromSetting(bodyRotation.getValue()); }

    private boolean isMoving() {
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
