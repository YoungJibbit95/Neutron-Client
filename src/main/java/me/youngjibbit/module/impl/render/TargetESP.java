package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.module.impl.world.NScaffold;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.value.impl.*;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;
import java.awt.Color;
import java.util.List;

/** Displays the active selection and locally accepted rotation, without producing actions or rotations. */
@ModuleInfo(name="TargetESP", description="Smooth target halo and a read-only aim marker with optional visual smoothing.", category=Category.Render)
public final class TargetESP extends Module {
    private final CheckBoxValue halo = new CheckBoxValue("Target Halo",this,true);
    private final SliderValue haloRadius = new SliderValue("Halo Radius Padding",this,.14,0,.6);
    private final SliderValue haloWidth = new SliderValue("Halo Width",this,.16,.035,.4);
    private final SliderValue haloSpeed = new SliderValue("Halo Speed",this,.7,.1,2);
    private final SliderValue haloTrail = new SliderValue("Halo Trail",this,.65,0,1);
    private final SliderValue haloMotion = new SliderValue("Halo Motion",this,.55,0,1);
    private final CheckBoxValue hitpoint = new CheckBoxValue("Hitpoint",this,true);
    private final ComboValue hitpointSource = new ComboValue("Hitpoint Source",this,new String[]{"Active","Aura","Scaffold","NScaffold"},0);
    private final SliderValue hitpointSize = new SliderValue("Hitpoint Size",this,.10,.015,.3);
    private final SliderValue rayLength = new SliderValue("Hitpoint Ray Length",this,6D,1,16);
    private final CheckBoxValue misses = new CheckBoxValue("Show Ray Misses",this,true);
    private final SliderValue opacity = new SliderValue("Opacity",this,.9,.1,1);
    private final SliderValue glow = new SliderValue("Glow Strength",this,1D,0,2);
    private final CheckBoxValue throughWalls = new CheckBoxValue("Through Walls",this,false);
    private final ComboValue colorSource = new ComboValue("Color Source",this,new String[]{"Theme","Custom"},0);
    private final ColorValue color = new ColorValue("Custom Color",this,new Color(154,124,255));
    // Append to preserve legacy setting indices.
    private final SliderValue hitpointSmoothing = new SliderValue("Hitpoint Smoothing",this,.6,0,1);

    private final TargetEspMotion motion = new TargetEspMotion();
    private final TargetEspPositionMotion haloPosition = new TargetEspPositionMotion();
    private final TargetEspPositionMotion markerPosition = new TargetEspPositionMotion();
    private final TargetEspObservation observation = new TargetEspObservation();
    private final TargetEspRenderer renderer = new TargetEspRenderer();
    private final TargetEspRenderer.Frame frame = new TargetEspRenderer.Frame();
    private final TargetEspRenderer.Style style = new TargetEspRenderer.Style();
    private Object worldContext, playerContext, pipelineContext;
    private long pipelineEpoch = Long.MIN_VALUE;
    private long lifecycleGeneration;
    private boolean renderInProgress;
    private boolean previousMarkerHit;

    private static final class RenderScene {
        final Minecraft client;
        final EntityPlayerSP player;
        final WorldClient world;
        final PacketPipeline packets;
        final ModuleManager modules;
        final RotationManager rotation;
        final long epoch, generation;
        RenderScene(Minecraft client, PacketPipeline packets, long epoch, long generation, ModuleManager modules) {
            this.client=client;this.player=client.thePlayer;this.world=client.theWorld;
            this.packets=packets;this.epoch=epoch;this.generation=generation;this.modules=modules;
            this.rotation=RuntimeServices.rotation();
        }
    }

    public TargetESP() {
        for(me.youngjibbit.value.Value<?> value:new me.youngjibbit.value.Value<?>[]{haloRadius,haloWidth,haloSpeed,haloTrail,haloMotion})
            value.setVisibility(module->halo.getValue());
        for(me.youngjibbit.value.Value<?> value:new me.youngjibbit.value.Value<?>[]{hitpointSource,hitpointSize,rayLength,misses,hitpointSmoothing})
            value.setVisibility(module->hitpoint.getValue());
        color.setVisibility(module->"Custom".equals(colorSource.getValue()));
        halo.setOnChange(value->motion.reset());
        hitpoint.setOnChange(value->observation.reset());
        hitpointSource.setOnChange(value->observation.reset());
    }
    @Override public void onEnable(){reset();super.onEnable();}
    @Override public void onDisable(){try{reset();renderer.close();}finally{super.onDisable();}}
    @Override public void onValueChange(){reset();}
    @Subscribe private final Listener<WorldEvent> worldChange=new Listener<>(event->reset());
    @Subscribe private final Listener<Event3D> render=new Listener<>(event->{
        if(renderInProgress)return;
        renderInProgress=true;
        try{renderWorld(event.getPartialTicks());}finally{renderInProgress=false;}
    });

    private void reset() {
        lifecycleGeneration++;
        frame.clear();motion.reset();haloPosition.reset();markerPosition.reset();observation.reset();
        worldContext=playerContext=pipelineContext=null;pipelineEpoch=Long.MIN_VALUE;
    }
    private void renderWorld(float partial) {
        Minecraft client=Minecraft.getMinecraft();frame.clear();
        if(!isToggled()||!halo.getValue()&&!hitpoint.getValue()
                ||client==null||client.thePlayer==null||client.theWorld==null||client.thePlayer.sendQueue==null
                ||client.thePlayer.sendQueue.getNetworkManager()==null){reset();return;}
        PacketPipeline packets=client.thePlayer.sendQueue.getNetworkManager().getClientPackets();
        if(packets==null||!packets.isActive()){reset();return;}
        long epoch=packets.getContextVersion();
        if(worldContext!=client.theWorld||playerContext!=client.thePlayer||pipelineContext!=packets||pipelineEpoch!=epoch){
            reset();worldContext=client.theWorld;playerContext=client.thePlayer;pipelineContext=packets;pipelineEpoch=epoch;
        }
        ModuleManager modules=Pulsar.INSTANCE.getMm();
        if(modules==null){reset();return;}
        RenderScene scene=new RenderScene(client,packets,epoch,lifecycleGeneration,modules);
        if(!current(scene))return;
        Aura aura=(Aura)modules.getModule(Aura.class);
        Scaffold scaffold=(Scaffold)modules.getModule(Scaffold.class);
        NScaffold nscaffold=(NScaffold)modules.getModule(NScaffold.class);
        EntityLivingBase target=aura==null?null:aura.getActiveTarget();
        // The target getter performs world/LOS reads which can synchronously change the scene.
        if(!current(scene))return;
        long now=System.nanoTime(),renderFrame=client.getRenderFrameSequence();float time=UiEffects.time();
        boolean animate=UiEffects.motion()&&UiEffects.options().ambient;
        partial=Float.isFinite(partial)?Math.max(0,Math.min(1,partial)):0;
        boolean liveTarget=target!=null&&target!=scene.player&&target.worldObj==scene.world
                &&!target.isDead&&target.isEntityAlive()&&scene.world.getEntityByID(target.getEntityId())==target;
        if(!current(scene))return;
        if(halo.getValue()&&liveTarget){
            AxisAlignedBB bounds=target.getEntityBoundingBox();
            if(!current(scene)||bounds==null)return;
            double x=target.lastTickPosX+(target.posX-target.lastTickPosX)*partial;
            double y=target.lastTickPosY+(target.posY-target.lastTickPosY)*partial;
            double z=target.lastTickPosZ+(target.posZ-target.lastTickPosZ)*partial;
            motion.sample(scene.world,target,now,renderFrame,x,z,animate,haloMotion.getValue().floatValue());
            frame.halo=haloPosition.sample(scene.world,target,now,renderFrame,x,y,z,animate,haloMotion.getValue().floatValue()*.35f);
            frame.x=haloPosition.x();frame.y=haloPosition.y()+bounds.minY-target.posY;frame.z=haloPosition.z();
            frame.radiusX=Math.max(.12,(bounds.maxX-bounds.minX)/2)+haloRadius.getValue();
            frame.radiusZ=Math.max(.12,(bounds.maxZ-bounds.minZ)/2)+haloRadius.getValue();
            frame.height=Math.max(.1,bounds.maxY-bounds.minY);
            frame.lift=TargetEspMotion.lift(time,haloSpeed.getValue().floatValue(),animate);
            frame.direction=TargetEspMotion.direction(time,haloSpeed.getValue().floatValue(),animate);
            frame.leanX=motion.leanX();frame.leanZ=motion.leanZ();
        }else {motion.reset();haloPosition.reset();}
        if(hitpoint.getValue()) {
            RotationManager.DiagnosticSnapshot observed=scene.rotation
                    .diagnosticSnapshot(scene.player,scene.world,packets);
            boolean current=observation.current(scene.world,scene.player,packets,epoch,observed.sequence,now,
                    observed.accepted,observed.collecting,observed.hasEffective,observed.owner,
                    aura!=null&&aura.isToggled()&&target!=null,scaffold!=null&&scaffold.isToggled(),
                    nscaffold!=null&&nscaffold.isToggled());
            String chosen=hitpointSource.getValue();
            if(current&&("Active".equals(chosen)||"Aura".equals(chosen)&&"aura".equals(observed.owner)
                    ||"Scaffold".equals(chosen)&&"scaffold".equals(observed.owner)
                    ||"NScaffold".equals(chosen)&&NScaffold.ROTATION_SOURCE.equals(observed.owner)))
                marker(scene,observed.wireYaw,observed.wirePitch,"aura".equals(observed.owner));
            if(frame.marker) {
                Object subject="aura".equals(observed.owner)?target:
                        "scaffold".equals(observed.owner)?scaffold:nscaffold;
                if(frame.markerHit!=previousMarkerHit)markerPosition.reset();
                previousMarkerHit=frame.markerHit;
                frame.markerInterpolated=markerPosition.sample(scene.world,subject,now,renderFrame,
                        frame.markerX,frame.markerY,frame.markerZ,animate,hitpointSmoothing.getValue().floatValue());
                frame.displayMarkerX=markerPosition.x();frame.displayMarkerY=markerPosition.y();frame.displayMarkerZ=markerPosition.z();
            }
        }else observation.reset();
        if(!frame.marker)markerPosition.reset();
        if(!current(scene))return;
        if(!frame.halo&&!frame.marker)return;
        RenderManager view=client.getRenderManager();if(view==null)return;
        frame.cameraX=view.viewerPosX;frame.cameraY=view.viewerPosY;frame.cameraZ=view.viewerPosZ;
        // UiEffects time freezes for animation/reduced-motion/ambient switches, without moving the marker.
        frame.time=animate?time*haloSpeed.getValue().floatValue():0;
        style.width=haloWidth.getValue().floatValue();style.trail=haloTrail.getValue().floatValue();
        style.markerSize=hitpointSize.getValue().floatValue();style.opacity=opacity.getValue().floatValue();
        style.glow=glow.getValue().floatValue();style.throughWalls=throughWalls.getValue();
        style.customColor="Custom".equals(colorSource.getValue());
        if(style.customColor)style.color=color.getColor().getRGB();
        // No callback from an old generation may draw or recreate a shader after disable.
        if(!current(scene))return;
        renderer.render(frame,style);
    }

    private boolean sameScene(RenderScene scene) {
        return lifecycleGeneration==scene.generation&&isToggled()&&Minecraft.getMinecraft()==scene.client
                &&scene.client.thePlayer==scene.player&&scene.client.theWorld==scene.world
                &&scene.player.worldObj==scene.world&&Pulsar.INSTANCE.getMm()==scene.modules
                &&RuntimeServices.rotation()==scene.rotation&&scene.player.sendQueue!=null
                &&scene.player.sendQueue.getNetworkManager()!=null
                &&scene.player.sendQueue.getNetworkManager().getClientPackets()==scene.packets
                &&scene.packets.isActive()&&scene.packets.getContextVersion()==scene.epoch;
    }
    private boolean current(RenderScene scene) {
        boolean valid=sameScene(scene)&&scene.client.currentScreen==null&&scene.client.inGameHasFocus
                &&!scene.client.isGamePaused()&&!scene.player.isDead&&scene.player.isEntityAlive()
                &&!scene.player.isSpectator()&&scene.client.getRenderViewEntity()==scene.player&&sameScene(scene);
        if(!valid&&lifecycleGeneration==scene.generation)reset();
        return valid;
    }

    private void marker(RenderScene scene,float yaw,float pitch,boolean entities) {
        Vec3 feet=scene.player.getLastReportedPosition();
        if(!current(scene)||!TargetEspRay.finite(feet))return;
        Vec3 eyes=feet.addVector(0,scene.player.getEyeHeight(),0);
        Vec3 direction=TargetEspRay.direction(yaw,pitch);
        Vec3 end=TargetEspRay.endpoint(eyes,direction,rayLength.getValue());
        if(!current(scene)||end==null)return;
        MovingObjectPosition block=scene.world.rayTraceBlocks(eyes,end,false,false,true);
        if(!current(scene))return;
        Vec3 nearest=block!=null&&block.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK?block.hitVec:null;
        if(entities){
            AxisAlignedBB area=new AxisAlignedBB(Math.min(eyes.xCoord,end.xCoord),Math.min(eyes.yCoord,end.yCoord),Math.min(eyes.zCoord,end.zCoord),
                    Math.max(eyes.xCoord,end.xCoord),Math.max(eyes.yCoord,end.yCoord),Math.max(eyes.zCoord,end.zCoord)).expand(1,1,1);
            List<Entity> candidates=scene.world.getEntitiesInAABBexcluding(scene.player,area,EntitySelectors.NOT_SPECTATING);
            if(!current(scene)||candidates==null)return;
            for(Entity entity:candidates){
                boolean collidable=entity!=null&&entity.canBeCollidedWith()&&!entity.isDead;
                if(!current(scene))return;
                if(!collidable)continue;
                float border=entity.getCollisionBorderSize();
                AxisAlignedBB bounds=entity.getEntityBoundingBox();
                if(!current(scene))return;
                if(bounds==null)continue;
                Vec3 hit=TargetEspRay.intercept(bounds.expand(border,border,border),eyes,end);
                if(TargetEspRay.nearer(eyes,hit,nearest))nearest=hit;
            }
        }
        if(!current(scene))return;
        frame.markerHit=TargetEspRay.finite(nearest);
        if(!frame.markerHit&&!misses.getValue())return;
        Vec3 point=frame.markerHit?nearest:end;
        frame.marker=true;frame.markerX=point.xCoord;frame.markerY=point.yCoord;frame.markerZ=point.zCoord;
    }
}
