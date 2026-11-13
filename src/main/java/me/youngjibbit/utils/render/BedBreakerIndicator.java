package me.youngjibbit.utils.render;

import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.world.BedBreakEstimate;
import me.youngjibbit.gui.HudPlacement;
import me.youngjibbit.gui.HudReveal;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Mouse;

/** Read-only HUD owner. The countdown is recomputed work, never an independent wall clock. */
public final class BedBreakerIndicator implements AutoCloseable {
    public final CheckBoxValue enabled;
    public final SliderValue x,y,scale;
    private final BedHudRenderer renderer=new BedHudRenderer();
    private final HudReveal reveal=new HudReveal();
    private final HudSpring progress=new HudSpring();
    private Object world,player;
    private PacketPipeline packets;
    private long connectionEpoch,generation,lastNanos;
    private BedHudRenderer.Progress last;
    private BlockPos animatedBed;
    private boolean bedPhase,preview,dragging,previousButton;
    private float grabX,grabY;

    /** Appended after the original Demise and smart-disable values, preserving legacy indices. */
    public BedBreakerIndicator(BedBreaker owner){
        enabled=new CheckBoxValue("Break Indicator",owner,true);
        x=new SliderValue("Indicator X",owner,50.0,0,100);
        y=new SliderValue("Indicator Y",owner,68.0,0,100);
        scale=new SliderValue("Indicator Scale",owner,1.0,.65,1.5);
        x.setVisibility(module->enabled.getValue());y.setVisibility(module->enabled.getValue());scale.setVisibility(module->enabled.getValue());
    }
    public static boolean isSetting(Value<?> value){
        return value!=null&&(value.getId().equals("break_indicator")||value.getId().startsWith("indicator_"));
    }
    public void settingsChanged(){generation++;if(!enabled.getValue())close();}
    public void reset(){
        generation++;world=player=null;packets=null;connectionEpoch=lastNanos=0;last=null;animatedBed=null;
        bedPhase=preview=dragging=false;reveal.reset();progress.reset();HudDrag.cancel(this);
        previousButton=Mouse.isCreated()&&Mouse.isButtonDown(0);
    }
    @Override public void close(){reset();renderer.close();}

    public void render(BedBreaker owner,ScaledResolution screen){
        Minecraft mc=Minecraft.getMinecraft();
        PacketPipeline current=currentPackets(mc);
        if(!owner.isToggled()||!enabled.getValue()||!HUD.visible()||mc.gameSettings.hideGUI||mc.gameSettings.showDebugInfo
                ||mc.theWorld==null||mc.thePlayer==null||current==null){close();return;}
        if(world!=mc.theWorld||player!=mc.thePlayer||packets!=current||connectionEpoch!=current.getContextVersion()){
            close();world=mc.theWorld;player=mc.thePlayer;packets=current;connectionEpoch=current.getContextVersion();
        }
        long now=System.nanoTime();float dt=lastNanos==0||now<=lastNanos?0:Math.min(.05f,(now-lastNanos)/1_000_000_000f);lastNanos=now;
        ClientSettings settings=ClientSettings.current();
        boolean motion=settings==null||settings.animations.getValue(),reduced=settings!=null&&settings.reducedMotion.getValue();
        String intensity=settings==null?"Balanced":settings.motionIntensity.getValue();
        BedBreakEstimate estimate=owner.getBreakEstimate();
        if(estimate.state==BedBreakEstimate.State.INVALIDATED){
            generation++;last=null;animatedBed=null;reveal.reset();progress.reset();HudDrag.cancel(this);dragging=false;return;
        }
        BedHudRenderer.Progress live=frame(estimate);
        boolean chat=mc.currentScreen instanceof GuiChat;
        if(live!=null){
            if(animatedBed==null||!animatedBed.equals(estimate.bed)||bedPhase!=estimate.activeIsBed)progress.reset();
            animatedBed=estimate.bed;bedPhase=estimate.activeIsBed;
            float amount=progress.step(live.progress,dt,motion,reduced,intensity);
            live=new BedHudRenderer.Progress(live.phase,live.detail,live.time,amount,live.estimated);preview=false;
        }else if(chat){
            live=new BedHudRenderer.Progress("PREVIEW","2 blocks to bed","~2.4 s",.64f,true);preview=true;
        }else if(preview){last=null;preview=false;reveal.reset();progress.reset();}
        if(live!=null)last=live;
        else if(last!=null)last=new BedHudRenderer.Progress(last.phase,"Waiting to mine","--",last.progress,false);
        float drawScale=Math.min(scale.getValue().floatValue(),Math.min((screen.getScaledWidth()-8)/BedHudRenderer.PROGRESS_WIDTH,
                (screen.getScaledHeight()-8)/BedHudRenderer.PROGRESS_HEIGHT));
        if(drawScale<=0)return;
        float width=BedHudRenderer.PROGRESS_WIDTH*drawScale,height=BedHudRenderer.PROGRESS_HEIGHT*drawScale;
        HudPlacement.Point point=position(screen,width,height);
        boolean down=Mouse.isCreated()&&Mouse.isButtonDown(0);
        float mx=Mouse.isCreated()?Mouse.getX()/(float)screen.getScaleFactor():-1;
        float my=Mouse.isCreated()?(mc.displayHeight-Mouse.getY()-1)/(float)screen.getScaleFactor():-1;
        boolean wasDragging=dragging;
        dragging=HudDrag.capture(this,chat&&live!=null,down,!previousButton,
                HudDrag.roundedContains(mx,my,point.x,point.y,width,height,11*drawScale));
        if(dragging&&!wasDragging){grabX=mx-point.x;grabY=my-point.y;}
        if(dragging){
            HUD hud=HUD.current();float grid=hud==null?0:hud.snap.getValue().floatValue();
            point=HudPlacement.drag(mx,my,grabX,grabY,grid,screen.getScaledWidth(),screen.getScaledHeight(),width,height,HUD.bounded());
            x.setValue(100D*point.x/Math.max(1,screen.getScaledWidth()-width));
            y.setValue(100D*point.y/Math.max(1,screen.getScaledHeight()-height));
        }
        previousButton=down;
        HudReveal.Frame transition=reveal.step(live!=null,dt,motion,reduced,intensity);
        if(!transition.drawn()){if(live==null){last=null;animatedBed=null;progress.reset();}return;}
        final BedHudRenderer.Progress frozen=last;
        if(frozen==null)return;
        final long lease=generation,frame=mc.getRenderFrameSequence(),epoch=connectionEpoch,action=owner.getBreakEstimateRevision();
        final Object frameWorld=world,framePlayer=player;
        final PacketPipeline pipeline=packets;
        final float s=drawScale*transition.scale;
        final float px=point.x+(width-BedHudRenderer.PROGRESS_WIDTH*s)/2;
        final float py=point.y+(height-BedHudRenderer.PROGRESS_HEIGHT*s)/2+transition.slideY;
        final Theme theme=Theme.getDefaultTheme();
        GuiShaderBloom.afterBatch(()->{
            if(!owner.isToggled()||!enabled.getValue()||lease!=generation||frame!=mc.getRenderFrameSequence()
                    ||action!=owner.getBreakEstimateRevision()
                    ||frameWorld!=mc.theWorld||framePlayer!=mc.thePlayer||currentPackets(mc)!=pipeline
                    ||pipeline.getContextVersion()!=epoch||!HUD.visible()||mc.gameSettings.hideGUI||mc.gameSettings.showDebugInfo)return;
            if(estimate.hasEstimate()&&owner.getBreakEstimate()!=estimate)return;
            renderer.paintProgress(frozen,px,py,s,transition.opacity,theme);
        });
    }
    private HudPlacement.Point position(ScaledResolution screen,float width,float height){
        return HudPlacement.clamp((screen.getScaledWidth()-width)*x.getValue().floatValue()/100,
                (screen.getScaledHeight()-height)*y.getValue().floatValue()/100,
                screen.getScaledWidth(),screen.getScaledHeight(),width,height,HUD.bounded());
    }
    /** Pure labels: covers count toward reaching the bed; the bed then gets its own final phase. */
    public static BedHudRenderer.Progress frame(BedBreakEstimate estimate){
        if(estimate==null||estimate.bed==null||estimate.state==BedBreakEstimate.State.INVALIDATED)return null;
        if(estimate.hasEstimate()){
            boolean bed=estimate.activeIsBed;
            String detail=bed?"Breaking bed":estimate.remainingCoverBlocks+" block"+(estimate.remainingCoverBlocks==1?"":"s")+" to bed";
            return new BedHudRenderer.Progress(bed?"BED":"TO BED",detail,
                    BedEspLayout.time(bed?estimate.totalSeconds:estimate.accessSeconds),
                    bed?estimate.blockProgress:estimate.accessProgress,true);
        }
        String detail;
        switch(estimate.state){
            case ALIGNING:detail="Aligning to bed";break;
            case PAUSED:detail="Paused";break;
            case NO_BED_PATH:detail="No confirmed bed path";break;
            case OUT_OF_RANGE:detail="Out of range";break;
            case UNLOADED:detail="Waiting for blocks";break;
            case UNBREAKABLE:detail="Unbreakable defense";break;
            default:detail="Waiting to mine";
        }
        return new BedHudRenderer.Progress("TO BED",detail,"--",0,false);
    }
    private static PacketPipeline currentPackets(Minecraft mc){
        if(mc==null||mc.theWorld==null||mc.thePlayer==null||mc.thePlayer.worldObj!=mc.theWorld||mc.thePlayer.sendQueue==null)return null;
        NetworkManager network=mc.thePlayer.sendQueue.getNetworkManager();
        if(network==null||!network.isChannelOpen())return null;
        PacketPipeline pipeline=network.getClientPackets();
        return pipeline!=null&&pipeline.isActive()?pipeline:null;
    }
}
