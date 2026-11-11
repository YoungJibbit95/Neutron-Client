package me.youngjibbit.module.impl.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.NScaffoldPlanner;
import me.youngjibbit.core.world.NScaffoldClutchPlanner;
import me.youngjibbit.core.world.NScaffoldModeController;
import me.youngjibbit.core.world.NScaffoldRotationController;
import me.youngjibbit.core.world.PlacementTarget;
import me.youngjibbit.core.world.ScaffoldPlacementService;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.event.EventPhase;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Visible camera and ordinary controller building, with an optional normal sneak input. */
@ModuleInfo(name="NScaffold",description="Visible bridge modes, bounded clutch recovery and independent rotation controls.",category=Category.World)
public final class NScaffold extends Module {
    public static final String ROTATION_SOURCE="nscaffold";
    private static final int ROTATION_PRIORITY=15;
    private final ComboValue mode=new ComboValue("Visible Rotation",this,new String[]{"Standard","Godbridge","Center","Reverse"},0);
    private final SliderValue turnSpeed=new SliderValue("Turn Speed",this,45D,1,180);
    private final SliderValue godbridgePitch=new SliderValue("Godbridge Pitch",this,75.7,65,89);
    private final SliderValue lookAhead=new SliderValue("Look Ahead",this,.65,0,1);
    private final CheckBoxValue keepHeight=new CheckBoxValue("Keep Height",this,false);
    private final CheckBoxValue autoSlot=new CheckBoxValue("Auto Select Blocks",this,true);
    private final SliderValue delay=new SliderValue("Placement Delay",this,0D,0,500);
    private final CheckBoxValue swing=new CheckBoxValue("Swing",this,true);

    // Constructed after Smart Disable: legacy ordinal settings keep their original positions.
    private final ComboValue bridgeMode,clutchCriteria,clutchRotation,smoothMode;
    private final CheckBoxValue staticAngles,clutch,fastClutchAlways,fastClutchMiss,fastClutchPredict,
            autoClutchSneak,advancedRotations,accelerate,imperfectCorrelation,shortStop;
    private final SliderValue straightTellyTicks,diagonalTellyTicks,groundTicksToSneak,extraSmoothing,
            yawMinimum,yawMaximum,pitchMinimum,pitchMaximum,yawAcceleration,pitchAcceleration,
            stopDuration,stopThreshold,stopAttempts;

    public enum Status { OFF, WAITING, SEARCHING, ALIGNING, BUILDING, PAUSED_SCAFFOLD, PAUSED_AURA, PAUSED_INPUT, HOLD_BACK_TO_BRIDGE, MANUAL_VIEW, TELLY_WAIT, CLUTCHING }
    private Status status=Status.OFF;
    private long generation;
    private Frame context, prepared;
    private NScaffoldPlanner planner;
    private ScaffoldPlacementService planningService;
    private NScaffoldClutchPlanner clutchPlanner;
    private final NScaffoldModeController modeController=new NScaffoldModeController();
    private final NScaffoldRotationController rotationController=new NScaffoldRotationController();
    private NScaffoldModeController.State bridgeState;
    private SceneContext controllerScene;
    private RuntimeObservation controllerObservation;
    private boolean processing, heightKnown;
    private int height;
    private SceneContext observedViewScene;
    private float observedYaw,observedPitch;
    private final java.util.function.LongSupplier activityClock=System::nanoTime;
    private long activitySequence,activityRevision,activityPlacements;
    private ActivityView activityView;

    /** No retained entity/world/stack: the inventory guard uses weak identities and copied facts. */
    private static final class ActivityView {
        final ScaffoldActivitySnapshot snapshot;
        final ActivityInventoryContext inventory;
        final RuntimeObservation observation;
        final long generation,connectionGeneration,suspensionRevision;
        ActivityView(ScaffoldActivitySnapshot snapshot,ActivityInventoryContext inventory,
                     RuntimeObservation observation,long generation,long connectionGeneration,long suspensionRevision) {
            this.snapshot=snapshot;this.inventory=inventory;this.observation=observation;
            this.generation=generation;this.connectionGeneration=connectionGeneration;this.suspensionRevision=suspensionRevision;
        }
    }

    private static final class Frame {
        final Minecraft client;
        final EntityPlayerSP player;
        final WorldClient world;
        final ModuleManager modules;
        final PacketPipeline packets;
        final RuntimeObservation observation;
        final RotationManager rotations;
        final ScaffoldPlacementService placement;
        final SceneContext scene;
        final ExecutedStep step;
        final long generation,epoch,sequence,tick;
        final float forward,strafe,yaw,pitch;
        PlacementTarget target;
        boolean placing;
        boolean acceptedView;
        boolean clutching;
        int groundTicks;
        NScaffoldClutchPlanner.Options clutchOptions;
        double placementX,placementY,placementZ;
        Frame(Minecraft client,ModuleManager modules,PacketPipeline packets,RuntimeObservation observation,
              RotationManager rotations,ScaffoldPlacementService placement,SceneContext scene,long generation,
              float forward,float strafe) {
            this.client=client;this.player=client.thePlayer;this.world=client.theWorld;this.modules=modules;
            this.packets=packets;this.observation=observation;this.rotations=rotations;this.placement=placement;
            this.scene=scene;this.generation=generation;this.forward=forward;this.strafe=strafe;
            this.epoch=packets.getContextVersion();this.sequence=rotations.getMotionSequence();
            this.tick=observation.getActiveOuterTickId();this.step=observation.getCurrentStep();
            this.yaw=player.rotationYaw;this.pitch=player.rotationPitch;
        }
    }

    public NScaffold() {
        enableSmartDisable();
        bridgeMode=new ComboValue("Build Mode",this,new String[]{"Normal","Breezily","Telly"},0);
        straightTellyTicks=new SliderValue("Straight Telly Ticks",this,4D,1,5,true);
        diagonalTellyTicks=new SliderValue("Diagonal Telly Ticks",this,2D,1,5,true);
        staticAngles=new CheckBoxValue("Static-ify",this,false);
        clutch=new CheckBoxValue("Clutch",this,false);
        clutchCriteria=new ComboValue("Clutch Criteria",this,new String[]{"MouseOver","Prediction"},0);
        clutchRotation=new ComboValue("Clutch Rotation Mode",this,new String[]{"Normal","Center"},1);
        fastClutchAlways=new CheckBoxValue("Fast Clutch Always",this,false);
        fastClutchMiss=new CheckBoxValue("Fast Clutch on Miss",this,false);
        fastClutchPredict=new CheckBoxValue("Fast Clutch Prediction",this,false);
        autoClutchSneak=new CheckBoxValue("Auto Sneak on Clutch",this,false);
        groundTicksToSneak=new SliderValue("Ground Ticks to Sneak",this,5D,0,10,true);
        advancedRotations=new CheckBoxValue("Advanced Rotations",this,false);
        smoothMode=new ComboValue("Smooth Mode",this,new String[]{"Linear","Relative"},0);
        extraSmoothing=new SliderValue("Extra Smoothing Factor",this,.15,.01,1);
        yawMinimum=new SliderValue("Yaw Rotation Speed (min)",this,180D,.01,180);
        yawMaximum=new SliderValue("Yaw Rotation Speed (max)",this,180D,.01,180);
        pitchMinimum=new SliderValue("Pitch Rotation Speed (min)",this,180D,.01,180);
        pitchMaximum=new SliderValue("Pitch Rotation Speed (max)",this,180D,.01,180);
        accelerate=new CheckBoxValue("Accelerate",this,false);
        yawAcceleration=new SliderValue("Yaw Accel Factor",this,.25,.01,.9);
        pitchAcceleration=new SliderValue("Pitch Accel Factor",this,.25,.01,.9);
        imperfectCorrelation=new CheckBoxValue("Imperfect Correlation",this,false);
        shortStop=new CheckBoxValue("Short Stop",this,false);
        stopDuration=new SliderValue("Short Stop Duration (ms)",this,50D,25,1000,true);
        stopThreshold=new SliderValue("Rotation Diff Buildup to Stop",this,180D,50,720);
        stopAttempts=new SliderValue("Max Threshold Attempts to Stop",this,1D,0,5,true);
        straightTellyTicks.setVisibility(module->"Telly".equals(bridgeMode.getValue()));
        diagonalTellyTicks.setVisibility(module->"Telly".equals(bridgeMode.getValue()));
        mode.setVisibility(module->!"Breezily".equals(bridgeMode.getValue()));
        godbridgePitch.setVisibility(module->mode.isVisible()&&"Godbridge".equals(mode.getValue()));
        staticAngles.setVisibility(module->mode.isVisible()&&("Godbridge".equals(mode.getValue())||"Reverse".equals(mode.getValue())));
        for(me.youngjibbit.value.Value<?> value:new me.youngjibbit.value.Value<?>[]{clutchCriteria,clutchRotation,autoClutchSneak})
            value.setVisibility(module->clutch.getValue());
        for(me.youngjibbit.value.Value<?> value:new me.youngjibbit.value.Value<?>[]{fastClutchAlways,fastClutchMiss,fastClutchPredict})
            value.setVisibility(module->clutch.getValue()&&advancedRotations.getValue());
        groundTicksToSneak.setVisibility(module->clutch.getValue()&&autoClutchSneak.getValue());
        turnSpeed.setVisibility(module->!advancedRotations.getValue());
        for(me.youngjibbit.value.Value<?> value:new me.youngjibbit.value.Value<?>[]{smoothMode,extraSmoothing,yawMinimum,yawMaximum,pitchMinimum,pitchMaximum,accelerate,imperfectCorrelation,shortStop})
            value.setVisibility(module->advancedRotations.getValue());
        yawAcceleration.setVisibility(module->advancedRotations.getValue()&&accelerate.getValue());
        pitchAcceleration.setVisibility(module->advancedRotations.getValue()&&accelerate.getValue());
        for(me.youngjibbit.value.Value<?> value:new me.youngjibbit.value.Value<?>[]{stopDuration,stopThreshold,stopAttempts})
            value.setVisibility(module->advancedRotations.getValue()&&shortStop.getValue());
    }
    @Override public void onEnable(){activityPlacements=0;invalidate(Status.WAITING);super.onEnable();}
    @Override public void onDisable(){try{invalidate(Status.OFF);}finally{super.onDisable();}}
    @Override public void onValueChange(){invalidate(isToggled()?Status.WAITING:Status.OFF);}
    public Status getStatus(){return status;}

    /** Pure bounded cache validation: no scene sampling, inventory scan, planner, rotation or cache writes. */
    public ScaffoldActivitySnapshot getActivitySnapshot() {
        ActivityView view=activityView;
        Minecraft client=Minecraft.getMinecraft();
        ModuleManager modules=Pulsar.INSTANCE.getMm();
        if(view==null || !isToggled() || generation!=view.generation || !basic(client)
                || modules==null || modules.getModule(NScaffold.class)!=this
                || RuntimeServices.observation()!=view.observation
                || view.observation.getObservedConnectionGeneration()!=view.connectionGeneration
                || view.observation.getObservedSuspensionRevision()!=view.suspensionRevision
                || !view.snapshot.isFresh(activityClock.getAsLong()) || !view.inventory.isCurrent(client))return null;
        return view==activityView && isToggled() && generation==view.generation
                && modules==Pulsar.INSTANCE.getMm() && modules.getModule(NScaffold.class)==this
                && Minecraft.getMinecraft()==client && RuntimeServices.observation()==view.observation
                && view.observation.getObservedConnectionGeneration()==view.connectionGeneration
                && view.observation.getObservedSuspensionRevision()==view.suspensionRevision ? view.snapshot:null;
    }

    private void clearActivity(){activityRevision++;activityView=null;}

    /** Called only by existing producer updates, after their current status/action is known. */
    private void publishActivity(Frame frame,long expectedGeneration) {
        if(!activityCurrent(frame,expectedGeneration))return;
        final long revision=activityRevision,placements=activityPlacements;
        final long suspension=frame.observation.getObservedSuspensionRevision();
        final Status observedStatus=status;
        final long now=activityClock.getAsLong();
        ActivityInventoryContext before=ActivityInventoryContext.capture(frame.client);
        if(before==null || !activityCurrent(frame,expectedGeneration) || revision!=activityRevision)return;
        int count=frame.placement.getBlockCount(),slot=frame.placement.getBlockSlot();
        if(slot<0 || slot>=9)slot=-1;
        ItemStack stack=slot<0?null:frame.player.inventory.getStackInSlot(slot);
        int item=stack==null?-1:Math.max(-1,Item.getIdFromItem(stack.getItem()));
        int metadata=stack==null?-1:Math.max(-1,stack.getMetadata());
        ActivityInventoryContext captured=slot<0?ActivityInventoryContext.capture(frame.client)
                :ActivityInventoryContext.capture(frame.client,36+slot);
        if(captured==null || !before.sameScene(frame.client) || !activityCurrent(frame,expectedGeneration)
                || revision!=activityRevision || placements!=activityPlacements || observedStatus!=status
                || frame.observation.getObservedSuspensionRevision()!=suspension)return;
        ScaffoldActivitySnapshot.Status visible;
        switch(observedStatus) {
            case SEARCHING:visible=ScaffoldActivitySnapshot.Status.SEARCHING;break;
            case ALIGNING:visible=ScaffoldActivitySnapshot.Status.ALIGNING;break;
            case CLUTCHING:visible=ScaffoldActivitySnapshot.Status.ALIGNING;break;
            case BUILDING:visible=ScaffoldActivitySnapshot.Status.PLACING;break;
            case WAITING:visible=ScaffoldActivitySnapshot.Status.WAITING;break;
            case TELLY_WAIT:visible=ScaffoldActivitySnapshot.Status.WAITING;break;
            case OFF:return;
            default:visible=ScaffoldActivitySnapshot.Status.PAUSED;break;
        }
        count=Math.max(-1,count);
        String profile=profile();
        ActivityView previous=activityView;
        if(previous==null || previous.snapshot.getPlacementCount()!=placements
                || !previous.snapshot.hasSameFacts(item,metadata,count,"NScaffold",profile,visible))activitySequence++;
        activityView=new ActivityView(new ScaffoldActivitySnapshot(activitySequence,now,item,metadata,count,
                "NScaffold",profile,visible,placements),captured,frame.observation,expectedGeneration,
                frame.scene.getConnectionGeneration(),suspension);
    }

    private boolean activityCurrent(Frame frame,long expectedGeneration) {
        if(generation!=expectedGeneration || !frameObjectsCurrent(frame) || !basic(frame.client))return false;
        return generation==expectedGeneration && frameObjectsCurrent(frame)
                && frame.player.openContainer!=null && frame.player.openContainer==frame.player.inventoryContainer
                && frame.observation.getObservedConnectionGeneration()==frame.scene.getConnectionGeneration();
    }

    private void invalidate(Status next) {
        Frame previous=context;
        generation++;prepared=null;context=null;heightKnown=false;observedViewScene=null;
        clearActivity();
        if(planner!=null)planner.reset();
        if(clutchPlanner!=null)clutchPlanner.reset();
        if(modeController!=null)modeController.reset();
        if(rotationController!=null)rotationController.reset();
        bridgeState=null;
        // An old callback never resets a replacement manager or another player/connection's owner.
        if(previous!=null && RuntimeServices.rotation()==previous.rotations
                && Minecraft.getMinecraft()==previous.client && previous.client.thePlayer==previous.player
                && previous.client.theWorld==previous.world && previous.rotations.isConnection(previous.packets)
                && previous.packets.getContextVersion()==previous.epoch && Pulsar.INSTANCE.getMm()==previous.modules
                && previous.modules.getModule(NScaffold.class)==this
                && previous.player.sendQueue!=null && previous.player.sendQueue.getNetworkManager()!=null
                && previous.player.sendQueue.getNetworkManager().getClientPackets()==previous.packets)
            previous.rotations.reset(ROTATION_SOURCE);
        status=next;info=next==Status.HOLD_BACK_TO_BRIDGE?"Hold back to bridge":next==Status.MANUAL_VIEW?
                "Manual view":mode==null?"Visible":profile()+" / Visible";
    }
    private void stop(Frame frame,Status next) {
        if(frame!=null && frame.generation==generation) {
            long nextGeneration=generation+1;
            invalidate(next);publishActivity(frame,nextGeneration);
        }
    }
    private void rememberView(Frame frame) {
        if(!identities(frame))return;
        observedViewScene=frame.scene;observedYaw=frame.player.rotationYaw;observedPitch=frame.player.rotationPitch;
    }
    private void pauseForManualView(Frame frame) {
        if(!identities(frame))return;
        SceneContext scene=frame.scene;float yaw=frame.player.rotationYaw,pitch=frame.player.rotationPitch;
        long nextGeneration=generation+1;
        invalidate(Status.MANUAL_VIEW);
        if(generation!=nextGeneration || !frameObjectsCurrent(frame))return;
        observedViewScene=scene;observedYaw=yaw;observedPitch=pitch;
        publishActivity(frame,nextGeneration);
    }
    private static boolean differentView(float yaw,float pitch,float referenceYaw,float referencePitch) {
        return !Float.isFinite(yaw)||!Float.isFinite(pitch)
                ||Math.abs(MathHelper.wrapAngleTo180_float(yaw-referenceYaw))>.001f||Math.abs(pitch-referencePitch)>.001f;
    }

    @Subscribe private final Listener<WorldEvent> worldChanged=new Listener<>(event->invalidate(isToggled()?Status.WAITING:Status.OFF));
    @Subscribe private final Listener<EventPacket> activityPackets=new Listener<>(event->{
        if(event.isPost() && event.isInbound() && (event.getPacket() instanceof S2FPacketSetSlot
                || event.getPacket() instanceof S30PacketWindowItems || event.getPacket() instanceof S2DPacketOpenWindow
                || event.getPacket() instanceof S2EPacketCloseWindow))clearActivity();
    });
    @Subscribe private final Listener<TickEvent> contextTick=new Listener<>(event->{
        Frame previous=context;
        if(previous!=null && !sceneCurrent(previous))stop(previous,Status.WAITING);
    });
    @Subscribe private final Listener<EventRotation> prepare=new Listener<>(event->{
        if(processing || !isToggled())return;
        processing=true;
        try{prepare();}finally{processing=false;}
    });
    @Subscribe private final Listener<MovementInputEvent> input=new Listener<>(event->{
        Frame frame=prepared;
        if(frame!=null && (!sameAxes(frame.forward,frame.strafe,event.getForward(),event.getStrafe())
                || event.isCancelled()))stop(frame,Status.PAUSED_INPUT);
        applyClutchSneak(event);
    },EventPriority.LOWEST);
    @Subscribe private final Listener<EventMotion> motion=new Listener<>(event->{
        if(event.getEventPhase()!=EventPhase.POST || processing || !isToggled())return;
        processing=true;
        try{place(event);}finally{processing=false;}
    });

    private void prepare() {
        final long entering=generation;
        Frame frame=capture();
        if(frame==null) {
            if(generation==entering)invalidate(Status.WAITING);
            return;
        }
        if(context!=null && !context.scene.sameSceneAs(frame.scene)) {
            invalidate(Status.WAITING);
            return;
        }
        if(observedViewScene!=null && observedViewScene.sameSceneAs(frame.scene)
                && differentView(frame.yaw,frame.pitch,observedYaw,observedPitch)) {
            pauseForManualView(frame);return;
        }
        context=frame;prepared=null;
        Status conflict=conflict(frame);
        if(!stepCurrent(frame)){stop(frame,Status.WAITING);return;}
        if(conflict!=null){stop(frame,conflict);return;}
        if(planner==null || planningService!=frame.placement) {
            planner=new NScaffoldPlanner(frame.placement);clutchPlanner=new NScaffoldClutchPlanner(frame.placement);
            planningService=frame.placement;heightKnown=false;
        }
        if(controllerScene==null || controllerObservation!=frame.observation || !controllerScene.sameSceneAs(frame.scene)) {
            modeController.resetForScene();rotationController.resetForScene();clutchPlanner.reset();
            controllerScene=frame.scene;controllerObservation=frame.observation;
        }
        NScaffoldModeController.State phase=modeController.advance(frame.tick,frame.player.onGround,
                frame.forward,frame.strafe,buildingMode(),straightTellyTicks.getValue().intValue(),diagonalTellyTicks.getValue().intValue());
        if(!phase.valid){stop(frame,Status.WAITING);return;}
        bridgeState=phase;frame.groundTicks=phase.groundTicks;
        int level=MathHelper.floor_double(frame.player.posY)-1;
        if(!heightKnown || !keepHeight.getValue() || level<height || level-height>3) {
            height=level;heightKnown=true;
        }
        NScaffoldClutchPlanner.Decision recovery=null;
        if(clutch.getValue()) {
            frame.clutchOptions=clutchOptions();
            recovery=clutchPlanner.plan(clutchSample(frame,frame.groundTicks),frame.clutchOptions,
                    ()->stepCurrent(frame)&&conflict(frame)==null);
        }
        if(!stepCurrent(frame)){stop(frame,Status.WAITING);return;}
        conflict=conflict(frame);
        if(conflict!=null){stop(frame,conflict);return;}
        NScaffoldPlanner.Decision decision=null;
        if(recovery==null) {
            if(!backwardInput(frame.forward,frame.strafe)) {
                // A route pause must not erase real grounded/flight observations;
                // Clutch can become necessary while approaching an edge forwards.
                frame.rotations.reset(ROTATION_SOURCE);planner.reset();rotationController.reset();
                status=Status.HOLD_BACK_TO_BRIDGE;info="Hold back to bridge";rememberView(frame);
                publishActivity(frame,frame.generation);return;
            }
            if(!phase.canBuild) {
                frame.rotations.reset(ROTATION_SOURCE);status=Status.TELLY_WAIT;
                info="Telly / "+phase.airborneTicks+" of "+phase.requiredTicks;rememberView(frame);
                publishActivity(frame,frame.generation);return;
            }
            decision=planner.plan(sample(frame,frame.yaw,frame.pitch),()->stepCurrent(frame)&&conflict(frame)==null);
        }
        if(!stepCurrent(frame)){stop(frame,Status.WAITING);return;}
        conflict=conflict(frame);
        if(conflict!=null){stop(frame,conflict);return;}
        if(decision==null && recovery==null) {
            frame.rotations.reset(ROTATION_SOURCE);status=Status.SEARCHING;rememberView(frame);
            publishActivity(frame,frame.generation);return;
        }
        PlacementTarget target=recovery==null?decision.target:recovery.target;
        if(!autoSlot.getValue() && target.getSlot()!=frame.player.inventory.currentItem) {
            frame.rotations.reset(ROTATION_SOURCE);status=Status.WAITING;publishActivity(frame,frame.generation);return;
        }
        frame.target=target;frame.clutching=recovery!=null;prepared=frame;
        float targetYaw=recovery==null?decision.yaw:recovery.yaw,targetPitch=recovery==null?decision.pitch:recovery.pitch;
        float[] pose;
        if(advancedRotations.getValue()) {
            boolean held=Float.compare(frame.yaw,targetYaw)==0 && Float.compare(frame.pitch,targetPitch)==0;
            NScaffoldRotationController.Step turn=rotationController.update(frame.tick,frame.yaw,frame.pitch,targetYaw,targetPitch,
                    held,recovery!=null&&recovery.urgent,rotationSettings());
            pose=turn==null?null:new float[]{turn.yaw,turn.pitch};
        } else pose=NScaffoldPlanner.turn(frame.yaw,frame.pitch,targetYaw,targetPitch,turnSpeed.getValue().floatValue());
        if(pose==null || !actionCurrent(frame)){stop(frame,Status.WAITING);return;}
        frame.rotations.requestVisible(ROTATION_SOURCE,ROTATION_PRIORITY,pose[0],pose[1],frame.yaw,frame.pitch,
                ()->actionCurrent(frame));
        if(stepCurrent(frame)) {
            status=frame.clutching?Status.CLUTCHING:Status.ALIGNING;
            info=profile()+(frame.clutching?" / Clutch":" / Visible");publishActivity(frame,frame.generation);
        }
    }

    private void place(EventMotion event) {
        final Frame frame=prepared;
        if(frame==null)return;
        if(!event.isAccepted() || !actionCurrent(frame)
                || !frame.rotations.canUseRotation(ROTATION_SOURCE,false)) {
            stop(frame,Status.WAITING);return;
        }
        frame.acceptedView=true;
        if(!actionCurrent(frame)){
            if(identities(frame) && differentView(frame.player.rotationYaw,frame.player.rotationPitch,
                    frame.rotations.getEffectiveYaw(),frame.rotations.getEffectivePitch()))pauseForManualView(frame);
            else stop(frame,Status.WAITING);
            return;
        }
        rememberView(frame);
        boolean onRoute=frame.clutching?clutchPlanner.onRoute(frame.target,clutchSample(frame,frame.groundTicks),frame.clutchOptions,
                ()->actionCurrent(frame)):planner.onRoute(frame.target,sample(frame,frame.player.rotationYaw,frame.player.rotationPitch),
                ()->actionCurrent(frame));
        if(!onRoute || !actionCurrent(frame)) {
            if(stepCurrent(frame)){
                prepared=null;frame.rotations.reset(ROTATION_SOURCE);status=Status.WAITING;
                publishActivity(frame,frame.generation);
            }
            else stop(frame,Status.WAITING);
            return;
        }
        PlacementTarget aligned=frame.placement.alignAcceptedHit(frame.target,frame.rotations,ROTATION_SOURCE);
        if(!actionCurrent(frame))return;
        if(aligned==null){status=Status.ALIGNING;prepared=null;publishActivity(frame,frame.generation);return;}
        frame.placing=true;frame.placementX=frame.player.posX;frame.placementY=frame.player.posY;frame.placementZ=frame.player.posZ;
        boolean placed=frame.placement.placeWithAuthority(aligned,frame.rotations,false,frame.sequence,
                delay.getValue().longValue(),false,swing.getValue(),false,ROTATION_SOURCE,
                ()->actionCurrent(frame),()->sceneCurrent(frame));
        if(frame.generation!=generation || prepared!=frame)return;
        if(differentView(frame.player.rotationYaw,frame.player.rotationPitch,
                frame.rotations.getEffectiveYaw(),frame.rotations.getEffectivePitch())){pauseForManualView(frame);return;}
        prepared=null;
        if(!sceneCurrent(frame)){stop(frame,Status.WAITING);return;}
        if(placed){planner.placed();if(frame.clutching)clutchPlanner.placed();status=Status.BUILDING;activityPlacements++;}
        else status=Status.ALIGNING;
        publishActivity(frame,frame.generation);
    }

    private Frame capture() {
        Minecraft client=Minecraft.getMinecraft();
        if(!basic(client))return null;
        ModuleManager modules=Pulsar.INSTANCE.getMm();
        if(modules==null || modules.getModule(NScaffold.class)!=this)return null;
        PacketPipeline packets=client.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        RuntimeObservation observer=RuntimeServices.observation();
        RotationManager rotations=RuntimeServices.rotation();
        if(packets==null || !packets.isActive() || observer==null || !rotations.isConnection(packets))return null;
        long before=generation;
        SceneContext scene=observer.captureScene();
        if(before!=generation || !scene.isValid() || scene.isSuspended() || !basic(client))return null;
        GameSettings keys=client.gameSettings;
        float forward=(keys.keyBindForward.isKeyDown()?1:0)-(keys.keyBindBack.isKeyDown()?1:0);
        float strafe=(keys.keyBindLeft.isKeyDown()?1:0)-(keys.keyBindRight.isKeyDown()?1:0);
        Frame frame=new Frame(client,modules,packets,observer,rotations,RuntimeServices.scaffoldPlacement(),scene,
                before,forward,strafe);
        return stepCurrent(frame)?frame:null;
    }
    private NScaffoldPlanner.Sample sample(Frame frame,float yaw,float pitch) {
        return new NScaffoldPlanner.Sample(new Vec3(frame.player.posX,frame.player.posY,frame.player.posZ),
                frame.player.getPositionEyes(1),yaw,pitch,frame.forward,frame.strafe,height,
                lookAhead.getValue(),rotationStyle(),staticAngles.getValue(),godbridgePitch.getValue().floatValue());
    }
    private NScaffoldModeController.Mode buildingMode() {
        return "Telly".equals(bridgeMode.getValue())?NScaffoldModeController.Mode.TELLY:
                "Breezily".equals(bridgeMode.getValue())?NScaffoldModeController.Mode.BREEZILY:NScaffoldModeController.Mode.NORMAL;
    }
    private NScaffoldPlanner.RotationStyle rotationStyle() {
        if("Breezily".equals(bridgeMode.getValue()))return NScaffoldPlanner.RotationStyle.BREEZILY;
        if("Godbridge".equals(mode.getValue()))return NScaffoldPlanner.RotationStyle.GODBRIDGE;
        if("Center".equals(mode.getValue()))return NScaffoldPlanner.RotationStyle.CENTER;
        if("Reverse".equals(mode.getValue()))return NScaffoldPlanner.RotationStyle.REVERSE;
        return NScaffoldPlanner.RotationStyle.STANDARD;
    }
    private String profile() {
        return bridgeMode==null || "Normal".equals(bridgeMode.getValue())?mode.getValue():
                "Breezily".equals(bridgeMode.getValue())?"Breezily":"Telly / "+mode.getValue();
    }
    private NScaffoldClutchPlanner.Options clutchOptions() {
        return new NScaffoldClutchPlanner.Options("Prediction".equals(clutchCriteria.getValue())?
                NScaffoldClutchPlanner.Criteria.PREDICTION:NScaffoldClutchPlanner.Criteria.MOUSE_OVER,
                "Center".equals(clutchRotation.getValue())?NScaffoldClutchPlanner.AimMode.CENTER:NScaffoldClutchPlanner.AimMode.NORMAL,
                fastClutchAlways.getValue(),fastClutchMiss.getValue(),fastClutchPredict.getValue(),
                autoClutchSneak.getValue(),groundTicksToSneak.getValue().intValue());
    }
    private NScaffoldClutchPlanner.Sample clutchSample(Frame frame,int groundedTicks) {
        EntityPlayerSP p=frame.player;
        return new NScaffoldClutchPlanner.Sample(new Vec3(p.posX,p.posY,p.posZ),p.getPositionEyes(1),
                new Vec3(p.motionX,p.motionY,p.motionZ),p.rotationYaw,p.rotationPitch,
                frame.forward!=0 || frame.strafe!=0,p.onGround,groundedTicks,frame.observation.getActiveOuterTickId());
    }
    private NScaffoldRotationController.Settings rotationSettings() {
        return new NScaffoldRotationController.Settings("Relative".equals(smoothMode.getValue())?
                NScaffoldRotationController.Smoothing.RELATIVE:NScaffoldRotationController.Smoothing.LINEAR,
                extraSmoothing.getValue(),yawMinimum.getValue(),yawMaximum.getValue(),pitchMinimum.getValue(),pitchMaximum.getValue(),
                accelerate.getValue(),yawAcceleration.getValue(),pitchAcceleration.getValue(),imperfectCorrelation.getValue(),
                shortStop.getValue(),stopDuration.getValue().longValue(),stopThreshold.getValue(),stopAttempts.getValue().intValue());
    }
    /** Adds only vanilla sneak + its normal slowdown to the current input event; never holds a key globally. */
    private void applyClutchSneak(MovementInputEvent event) {
        Frame frame=context;
        if(event.isCancelled() || event.isSneak() || event.isJump() || frame==null || clutchPlanner==null || bridgeState==null
                || !clutch.getValue() || !autoClutchSneak.getValue() || !sceneCurrent(frame) || conflict(frame)!=null
                || !frame.player.onGround || !AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT))return;
        // Production resolves the visible camera before MovementInputEvent. That
        // owned prepared pose is expected here; a further/manual change still wins.
        RotationManager.MovementRotation movement=frame.rotations.getPreparedMovement();
        if(prepared==frame) {
            if(!stepCurrent(frame) || movement==null || !ROTATION_SOURCE.equals(movement.source)
                    || differentView(frame.player.rotationYaw,frame.player.rotationPitch,movement.yaw,movement.pitch))return;
        } else {
            if(movement!=null && movement.source!=null && !ROTATION_SOURCE.equals(movement.source))return;
            if(observedViewScene!=null && observedViewScene.sameSceneAs(frame.scene)
                    && differentView(frame.player.rotationYaw,frame.player.rotationPitch,observedYaw,observedPitch))return;
        }
        GameSettings keys=frame.client.gameSettings;
        float forward=(keys.keyBindForward.isKeyDown()?1:0)-(keys.keyBindBack.isKeyDown()?1:0);
        float strafe=(keys.keyBindLeft.isKeyDown()?1:0)-(keys.keyBindRight.isKeyDown()?1:0);
        if(!sameAxes(forward,strafe,event.getForward(),event.getStrafe()) || forward==0 && strafe==0)return;
        NScaffoldClutchPlanner.Sample sample=clutchSample(frame,bridgeState.groundTicks);
        if(!clutchPlanner.wantsSneak(sample,clutchOptions()) || !sceneCurrent(frame))return;
        event.setSneak(true);event.setForward(event.getForward()*.3f);event.setStrafe(event.getStrafe()*.3f);
    }
    private static boolean basic(Minecraft client) {
        return client!=null && client.thePlayer!=null && client.theWorld!=null && client.playerController!=null
                && client.gameSettings!=null && client.currentScreen==null && client.inGameHasFocus
                && !client.isGamePaused() && client.getRenderViewEntity()==client.thePlayer
                && client.thePlayer.worldObj==client.theWorld && client.thePlayer.inventory!=null
                && client.thePlayer.isEntityAlive() && !client.thePlayer.isDead && !client.thePlayer.isSpectator()
                && !client.thePlayer.isRiding() && client.thePlayer.sendQueue!=null
                && client.thePlayer.sendQueue.getNetworkManager()!=null
                && client.thePlayer.sendQueue.getNetworkManager().isChannelOpen();
    }
    private boolean identities(Frame frame) {
        return frame!=null && generation==frame.generation && frameObjectsCurrent(frame);
    }
    private boolean frameObjectsCurrent(Frame frame) {
        return frame!=null && isToggled() && Minecraft.getMinecraft()==frame.client
                && frame.client.thePlayer==frame.player && frame.client.theWorld==frame.world
                && Pulsar.INSTANCE.getMm()==frame.modules && frame.modules.getModule(NScaffold.class)==this
                && RuntimeServices.rotation()==frame.rotations && RuntimeServices.observation()==frame.observation
                && RuntimeServices.scaffoldPlacement()==frame.placement && frame.rotations.isConnection(frame.packets)
                && frame.player.sendQueue!=null && frame.player.sendQueue.getNetworkManager()!=null
                && frame.player.sendQueue.getNetworkManager().getClientPackets()==frame.packets
                && frame.packets.isActive() && frame.packets.getContextVersion()==frame.epoch;
    }
    private boolean sceneCurrent(Frame frame) {
        if(!identities(frame) || !basic(frame.client))return false;
        SceneContext fresh=frame.observation.captureScene();
        return identities(frame) && fresh.isValid() && !fresh.isSuspended() && frame.scene.sameSceneAs(fresh)
                && basic(frame.client);
    }
    private boolean stepCurrent(Frame frame) {
        if(!sceneCurrent(frame) || frame.rotations.getMotionSequence()!=frame.sequence
                || frame.observation.getCurrentStep()!=frame.step || frame.observation.getActiveOuterTickId()!=frame.tick)return false;
        boolean admitted=AutomatedActionAdmission.mayAttempt(ModuleAction.PLACEMENT);
        return admitted && identities(frame) && frame.rotations.getMotionSequence()==frame.sequence
                && frame.observation.getCurrentStep()==frame.step && frame.observation.getActiveOuterTickId()==frame.tick;
    }
    private boolean actionCurrent(Frame frame) {
        if(prepared!=frame || frame.target==null || !stepCurrent(frame) || conflict(frame)!=null)return false;
        if(!frame.clutching && !backwardInput(frame.forward,frame.strafe))return false;
        if(frame.acceptedView && differentView(frame.player.rotationYaw,frame.player.rotationPitch,
                frame.rotations.getEffectiveYaw(),frame.rotations.getEffectivePitch()))return false;
        if(!autoSlot.getValue() && frame.player.inventory.currentItem!=frame.target.getSlot())return false;
        if(frame.placing && (Double.compare(frame.player.posX,frame.placementX)!=0
                || Double.compare(frame.player.posY,frame.placementY)!=0 || Double.compare(frame.player.posZ,frame.placementZ)!=0))return false;
        return prepared==frame && stepCurrent(frame);
    }
    private Status conflict(Frame frame) {
        if(!identities(frame))return Status.WAITING;
        Scaffold old=(Scaffold)frame.modules.getModule(Scaffold.class);
        if(old!=null && old.isToggled())return Status.PAUSED_SCAFFOLD;
        Aura aura=(Aura)frame.modules.getModule(Aura.class);
        if(aura!=null && aura.isToggled() && aura.getActiveTarget()!=null)return Status.PAUSED_AURA;
        if(!identities(frame))return Status.WAITING;
        if(RuntimeServices.attackScheduler().hasAttackThisTick())return Status.PAUSED_AURA;
        if(frame.player.isUsingItem())return Status.PAUSED_INPUT;
        return null;
    }
    private static boolean backwardInput(float forward,float strafe) {
        return forward<=0 && (forward!=0 || strafe==0);
    }
    private static boolean sameAxes(float forward,float strafe,float actualForward,float actualStrafe) {
        if(!Float.isFinite(actualForward)||!Float.isFinite(actualStrafe))return false;
        double expected=Math.hypot(forward,strafe),actual=Math.hypot(actualForward,actualStrafe);
        if(expected<1e-5 || actual<1e-5)return expected<1e-5 && actual<1e-5;
        return Math.abs(forward/expected-actualForward/actual)<1e-4
                && Math.abs(strafe/expected-actualStrafe/actual)<1e-4;
    }
}
