package me.youngjibbit.module.impl.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.AutomatedActionAdmission;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.core.world.NScaffoldPlanner;
import me.youngjibbit.core.world.PlacementTarget;
import me.youngjibbit.core.world.ScaffoldPlacementService;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
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
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Uses visible normal slot selection; never spoofs slots, input, movement or packets. */
@ModuleInfo(name="NScaffold",description="Visible Standard/Godbridge building with normal slots, reach and world ray checks.",category=Category.World)
public final class NScaffold extends Module {
    public static final String ROTATION_SOURCE="nscaffold";
    private static final int ROTATION_PRIORITY=15;
    private final ComboValue mode=new ComboValue("Visible Rotation",this,new String[]{"Standard","Godbridge"},0);
    private final SliderValue turnSpeed=new SliderValue("Turn Speed",this,45D,1,180);
    private final SliderValue godbridgePitch=new SliderValue("Godbridge Pitch",this,75.7,65,89);
    private final SliderValue lookAhead=new SliderValue("Look Ahead",this,.65,0,1);
    private final CheckBoxValue keepHeight=new CheckBoxValue("Keep Height",this,false);
    private final CheckBoxValue autoSlot=new CheckBoxValue("Auto Select Blocks",this,true);
    private final SliderValue delay=new SliderValue("Placement Delay",this,0D,0,500);
    private final CheckBoxValue swing=new CheckBoxValue("Swing",this,true);

    public enum Status { OFF, WAITING, SEARCHING, ALIGNING, BUILDING, PAUSED_SCAFFOLD, PAUSED_AURA, PAUSED_INPUT, HOLD_BACK_TO_BRIDGE, MANUAL_VIEW }
    private Status status=Status.OFF;
    private long generation;
    private Frame context, prepared;
    private NScaffoldPlanner planner;
    private ScaffoldPlacementService planningService;
    private boolean processing, heightKnown;
    private int height;
    private SceneContext observedViewScene;
    private float observedYaw,observedPitch;

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
        godbridgePitch.setVisibility(module->"Godbridge".equals(mode.getValue()));
        enableSmartDisable();
    }
    @Override public void onEnable(){invalidate(Status.WAITING);super.onEnable();}
    @Override public void onDisable(){try{invalidate(Status.OFF);}finally{super.onDisable();}}
    @Override public void onValueChange(){invalidate(isToggled()?Status.WAITING:Status.OFF);}
    public Status getStatus(){return status;}

    private void invalidate(Status next) {
        Frame previous=context;
        generation++;prepared=null;context=null;heightKnown=false;observedViewScene=null;
        if(planner!=null)planner.reset();
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
                "Manual view":mode==null?"Visible":mode.getValue()+" / Visible";
    }
    private void stop(Frame frame,Status next) {
        if(frame!=null && frame.generation==generation)invalidate(next);
    }
    private void rememberView(Frame frame) {
        if(!identities(frame))return;
        observedViewScene=frame.scene;observedYaw=frame.player.rotationYaw;observedPitch=frame.player.rotationPitch;
    }
    private void pauseForManualView(Frame frame) {
        if(!identities(frame))return;
        SceneContext scene=frame.scene;float yaw=frame.player.rotationYaw,pitch=frame.player.rotationPitch;
        invalidate(Status.MANUAL_VIEW);
        observedViewScene=scene;observedYaw=yaw;observedPitch=pitch;
    }
    private static boolean differentView(float yaw,float pitch,float referenceYaw,float referencePitch) {
        return !Float.isFinite(yaw)||!Float.isFinite(pitch)
                ||Math.abs(MathHelper.wrapAngleTo180_float(yaw-referenceYaw))>.001f||Math.abs(pitch-referencePitch)>.001f;
    }

    @Subscribe private final Listener<WorldEvent> worldChanged=new Listener<>(event->invalidate(isToggled()?Status.WAITING:Status.OFF));
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
            planner=new NScaffoldPlanner(frame.placement);planningService=frame.placement;heightKnown=false;
        }
        int level=MathHelper.floor_double(frame.player.posY)-1;
        if(!heightKnown || !keepHeight.getValue() || level<height || level-height>3) {
            height=level;heightKnown=true;
        }
        NScaffoldPlanner.Sample sample=sample(frame,frame.yaw,frame.pitch);
        NScaffoldPlanner.Decision decision=planner.plan(sample,()->stepCurrent(frame)&&conflict(frame)==null);
        if(!stepCurrent(frame)){stop(frame,Status.WAITING);return;}
        conflict=conflict(frame);
        if(conflict!=null){stop(frame,conflict);return;}
        if(decision==null) {
            frame.rotations.reset(ROTATION_SOURCE);status=Status.SEARCHING;rememberView(frame);return;
        }
        if(!autoSlot.getValue() && decision.target.getSlot()!=frame.player.inventory.currentItem) {
            frame.rotations.reset(ROTATION_SOURCE);status=Status.WAITING;return;
        }
        frame.target=decision.target;prepared=frame;
        float[] pose=NScaffoldPlanner.turn(frame.yaw,frame.pitch,decision.yaw,decision.pitch,turnSpeed.getValue().floatValue());
        if(pose==null || !actionCurrent(frame)){stop(frame,Status.WAITING);return;}
        frame.rotations.requestVisible(ROTATION_SOURCE,ROTATION_PRIORITY,pose[0],pose[1],frame.yaw,frame.pitch,
                ()->actionCurrent(frame));
        if(stepCurrent(frame)) {status=Status.ALIGNING;info=mode.getValue()+" / Visible";}
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
        if(!planner.onRoute(frame.target,sample(frame,frame.player.rotationYaw,frame.player.rotationPitch),
                ()->actionCurrent(frame)) || !actionCurrent(frame)) {
            if(stepCurrent(frame)){prepared=null;frame.rotations.reset(ROTATION_SOURCE);status=Status.WAITING;}
            else stop(frame,Status.WAITING);
            return;
        }
        PlacementTarget aligned=frame.placement.alignAcceptedHit(frame.target,frame.rotations,ROTATION_SOURCE);
        if(!actionCurrent(frame))return;
        if(aligned==null){status=Status.ALIGNING;prepared=null;return;}
        frame.placing=true;frame.placementX=frame.player.posX;frame.placementY=frame.player.posY;frame.placementZ=frame.player.posZ;
        boolean placed=frame.placement.placeWithAuthority(aligned,frame.rotations,false,frame.sequence,
                delay.getValue().longValue(),false,swing.getValue(),false,ROTATION_SOURCE,
                ()->actionCurrent(frame),()->sceneCurrent(frame));
        if(frame.generation!=generation || prepared!=frame)return;
        if(differentView(frame.player.rotationYaw,frame.player.rotationPitch,
                frame.rotations.getEffectiveYaw(),frame.rotations.getEffectivePitch())){pauseForManualView(frame);return;}
        prepared=null;
        if(!sceneCurrent(frame)){stop(frame,Status.WAITING);return;}
        if(placed){planner.placed();status=Status.BUILDING;}
        else status=Status.ALIGNING;
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
                lookAhead.getValue(),"Godbridge".equals(mode.getValue()),godbridgePitch.getValue().floatValue());
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
        return frame!=null && isToggled() && generation==frame.generation && Minecraft.getMinecraft()==frame.client
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
        if(frame.acceptedView && differentView(frame.player.rotationYaw,frame.player.rotationPitch,
                frame.rotations.getEffectiveYaw(),frame.rotations.getEffectivePitch()))return false;
        if(!autoSlot.getValue() && frame.player.inventory.currentItem!=frame.target.getSlot())return false;
        if(frame.placing && (Double.compare(frame.player.posX,frame.placementX)!=0
                || Double.compare(frame.player.posY,frame.placementY)!=0 || Double.compare(frame.player.posZ,frame.placementZ)!=0))return false;
        return prepared==frame && stepCurrent(frame);
    }
    private Status conflict(Frame frame) {
        if(!identities(frame))return Status.WAITING;
        // A visible builder cannot secretly reinterpret forward/sideways keys as backward movement.
        if(frame.forward>0 || frame.forward==0 && frame.strafe!=0)return Status.HOLD_BACK_TO_BRIDGE;
        Scaffold old=(Scaffold)frame.modules.getModule(Scaffold.class);
        if(old!=null && old.isToggled())return Status.PAUSED_SCAFFOLD;
        Aura aura=(Aura)frame.modules.getModule(Aura.class);
        if(aura!=null && aura.isToggled() && aura.getActiveTarget()!=null)return Status.PAUSED_AURA;
        if(!identities(frame))return Status.WAITING;
        if(RuntimeServices.attackScheduler().hasAttackThisTick())return Status.PAUSED_AURA;
        if(frame.player.isUsingItem())return Status.PAUSED_INPUT;
        return null;
    }
    private static boolean sameAxes(float forward,float strafe,float actualForward,float actualStrafe) {
        if(!Float.isFinite(actualForward)||!Float.isFinite(actualStrafe))return false;
        double expected=Math.hypot(forward,strafe),actual=Math.hypot(actualForward,actualStrafe);
        if(expected<1e-5 || actual<1e-5)return expected<1e-5 && actual<1e-5;
        return Math.abs(forward/expected-actualForward/actual)<1e-4
                && Math.abs(strafe/expected-actualStrafe/actual)<1e-4;
    }
}
