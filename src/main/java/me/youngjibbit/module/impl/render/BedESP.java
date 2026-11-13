package me.youngjibbit.module.impl.render;

import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.world.BedObservation;
import me.youngjibbit.core.world.BedWorldTracker;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.BedEspLayout;
import me.youngjibbit.utils.render.BedEspRenderer;
import me.youngjibbit.utils.render.BedHudRenderer;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL11.*;

/** Loaded-world observations only. The overlay never targets, rotates, mines or sends packets. */
@ModuleInfo(name="BedESP",category=Category.Render,
        description="Glowing beds with two observed defense layers, open sides and an uncertain team-color hint.")
public final class BedESP extends Module {
    public final ComboValue mode=new ComboValue("ESP Mode",this,new String[]{"Glow","Flame","Lines","Outline"},0);
    public final SliderValue range=new SliderValue("Range",this,48.0,8,128,true);
    public final ComboValue colors=new ComboValue("Color Source",this,new String[]{"Theme","Team hint"},0);
    public final SliderValue glowStrength=new SliderValue("Glow Strength",this,.8,0,2);
    public final SliderValue glowRadius=new SliderValue("Glow Radius",this,5.0,1,16);
    public final SliderValue boxOpacity=new SliderValue("Box Opacity",this,.13,0,.7);
    public final SliderValue lineWidth=new SliderValue("Line Width",this,1.25,.5,4);
    public final CheckBoxValue widgets=new CheckBoxValue("Widgets",this,true);
    public final SliderValue maxWidgets=new SliderValue("Max Widgets",this,8.0,1,16,true);
    public final SliderValue widgetScale=new SliderValue("Widget Scale",this,1.0,.65,1.5);
    public final SliderValue widgetHeight=new SliderValue("Widget Height",this,.85,.3,2);
    public final CheckBoxValue showLayers=new CheckBoxValue("Show Layers",this,true);
    public final CheckBoxValue showTeam=new CheckBoxValue("Show Team",this,true);
    public final CheckBoxValue showExposure=new CheckBoxValue("Show Exposure",this,true);

    private final BedWorldTracker tracker=new BedWorldTracker();
    private final BedEspRenderer boxes=new BedEspRenderer();
    private final BedHudRenderer labels=new BedHudRenderer();
    private final FloatBuffer model=BufferUtils.createFloatBuffer(16),projection=BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport=BufferUtils.createIntBuffer(16);
    private Object world,player;
    private PacketPipeline connection;
    private long connectionEpoch;
    private WeakReference<Object> unloadedWorld=new WeakReference<>(null);

    public BedESP(){
        glowStrength.setVisibility(module->!"Outline".equals(mode.getValue()));
        glowRadius.setVisibility(module->!"Outline".equals(mode.getValue()));
        for(me.youngjibbit.value.Value<?> value:new me.youngjibbit.value.Value<?>[]{maxWidgets,widgetScale,widgetHeight,showLayers,showTeam,showExposure})
            value.setVisibility(module->widgets.getValue());
    }
    @Override public void onEnable(){clear();super.onEnable();}
    @Override public void onDisable(){try{clear();}finally{super.onDisable();}}

    @Subscribe private final Listener<WorldEvent> worldChanged=new Listener<>(event->{
        if(event instanceof WorldEvent.Unload)unloadedWorld=new WeakReference<>(event.world);
        else if(event instanceof WorldEvent.Load)unloadedWorld.clear();
        clear();
    });
    @Subscribe private final Listener<TickEvent> tick=new Listener<>(event->{
        if(!isToggled())return;
        PacketPipeline current=packets();
        if(current==null){clear();return;}
        if(world!=mc.theWorld||player!=mc.thePlayer||connection!=current||connectionEpoch!=current.getContextVersion()){
            clear();world=mc.theWorld;player=mc.thePlayer;connection=current;connectionEpoch=current.getContextVersion();
        }
        if(!mc.isGamePaused())tracker.update(mc,range.getValue());
    });
    @Subscribe private final Listener<Event3D> render=new Listener<>(event->{
        if(!isToggled()||!current()||net.optifine.shaders.Shaders.isShadowPass||mc.gameSettings.hideGUI||!HUD.visible())return;
        List<BedObservation> beds=tracker.snapshot(mc);
        if(beds.isEmpty()||!current())return;
        Theme theme=Theme.getDefaultTheme();
        ScaledResolution screen=new ScaledResolution(mc);
        double cx=mc.getRenderManager().viewerPosX,cy=mc.getRenderManager().viewerPosY,cz=mc.getRenderManager().viewerPosZ;
        boolean drawLabels=widgets.getValue()&&!mc.gameSettings.showDebugInfo;
        if(drawLabels){
            model.clear();projection.clear();viewport.clear();
            glGetFloat(GL_MODELVIEW_MATRIX,model);glGetFloat(GL_PROJECTION_MATRIX,projection);glGetInteger(GL_VIEWPORT,viewport);
        }
        List<BedEspRenderer.Entry> entries=new ArrayList<>(beds.size());
        List<BedHudRenderer.PlacedWidget> placed=new ArrayList<>();
        List<BedEspLayout.Rect> occupied=new ArrayList<>();
        boolean teamColor="Team hint".equals(colors.getValue());
        float scale=widgetScale.getValue().floatValue();
        for(BedObservation bed:beds){
            int primary=theme.getPrimaryGradientRGB(),secondary=theme.getSecondaryGradientRGB();
            if(teamColor&&bed.team.kind==BedObservation.TeamHint.Kind.COLOR_HEURISTIC){
                primary=0xff000000|bed.team.colorRGB;secondary=Theme.interpolateRGB(primary,0xffffffff,.28f);
            }
            entries.add(new BedEspRenderer.Entry(bed.bounds,primary,secondary));
            if(!drawLabels||placed.size()>=maxWidgets.getValue().intValue())continue;
            AxisAlignedBB b=bed.bounds;double x=(b.minX+b.maxX)/2,z=(b.minZ+b.maxZ)/2;
            BedEspLayout.Point anchor=BedEspLayout.project(x-cx,b.maxY+widgetHeight.getValue()-cy,z-cz,
                    model,projection,viewport,mc.displayHeight,screen.getScaleFactor());
            double dx=x-mc.thePlayer.posX,dy=b.minY-mc.thePlayer.posY,dz=z-mc.thePlayer.posZ;
            BedHudRenderer.Widget widget=BedHudRenderer.Widget.from(bed,Math.sqrt(dx*dx+dy*dy+dz*dz),
                    showTeam.getValue(),showLayers.getValue(),showExposure.getValue());
            BedEspLayout.Rect rect=BedEspLayout.place(anchor,BedHudRenderer.WIDGET_WIDTH*scale,widget.height()*scale,
                    screen.getScaledWidth(),screen.getScaledHeight(),occupied);
            if(rect!=null){occupied.add(rect);placed.add(new BedHudRenderer.PlacedWidget(widget,rect.x,rect.y,scale,1));}
        }
        if(!current())return;
        BedEspRenderer.Style style=new BedEspRenderer.Style();
        style.mode=mode.getValue();style.opacity=boxOpacity.getValue().floatValue();style.glow=glowStrength.getValue().floatValue();
        style.radius=glowRadius.getValue().floatValue();style.lineWidth=lineWidth.getValue().floatValue();style.useTheme=!teamColor;
        boxes.render(entries,cx,cy,cz,style);
        if(current())labels.paintWidgets(placed,theme);
    });

    private PacketPipeline packets(){
        if(mc==null||mc.theWorld==null||mc.theWorld==unloadedWorld.get()||mc.thePlayer==null
                ||mc.thePlayer.worldObj!=mc.theWorld||mc.thePlayer.sendQueue==null)return null;
        NetworkManager network=mc.thePlayer.sendQueue.getNetworkManager();
        if(network==null||!network.isChannelOpen())return null;
        PacketPipeline packets=network.getClientPackets();
        return packets!=null&&packets.isActive()?packets:null;
    }
    private boolean current(){return world!=null&&world==mc.theWorld&&player==mc.thePlayer
            &&connection!=null&&connection==packets()&&connection.getContextVersion()==connectionEpoch;}
    private void clear(){
        world=player=null;connection=null;connectionEpoch=0;
        tracker.invalidate();boxes.close();labels.close();
    }
}
