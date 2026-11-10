package me.youngjibbit.core.activity;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only capture around the existing successful S08 application callback. */
public final class WidgetIncidentObservations {
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private WidgetIncidentObservations(){}

    public static CorrectionProbe captureCorrection(PacketPipeline connection,boolean replay){
        try{
            Minecraft mc=Minecraft.getMinecraft();
            if(replay||!current(mc,connection)||mc.thePlayer.ticksExisted<100
                    ||mc.thePlayer.isDead||!mc.thePlayer.isEntityAlive())return null;
            Object world=mc.theWorld;EntityPlayerSP player=mc.thePlayer;
            long epoch=connection.getContextVersion();
            WidgetIncidentJournal journal=WidgetIncidentJournal.global();
            journal.context(world,connection,epoch);
            ModuleManager modules=Pulsar.INSTANCE.getMm();
            if(modules!=null){
                Backtrack backtrack=(Backtrack)modules.getModule(Backtrack.class);
                LagRange lagRange=(LagRange)modules.getModule(LagRange.class);
                Tickbase tickbase=(Tickbase)modules.getModule(Tickbase.class);
                ExploitActivitySnapshot b=backtrack==null?null:backtrack.getActivitySnapshot();
                ExploitActivitySnapshot l=lagRange==null?null:lagRange.getActivitySnapshot();
                ExploitActivitySnapshot t=tickbase==null?null:tickbase.getActivitySnapshot();
                long now=System.nanoTime();
                journal.observeActivity(b,now);journal.observeActivity(l,now);journal.observeActivity(t,now);
            }
            if(!current(mc,connection)||mc.theWorld!=world||mc.thePlayer!=player
                    ||connection.getContextVersion()!=epoch)return null;
            return new CorrectionProbe(world,player,connection,epoch);
        }catch(RuntimeException ignored){return null;}
    }
    public static void appliedCorrection(CorrectionProbe probe){
        if(probe==null)return;
        try{
            Minecraft mc=Minecraft.getMinecraft();
            if(!current(mc,probe.connection)||mc.theWorld!=probe.world||mc.thePlayer!=probe.player)return;
            EntityPlayerSP player=mc.thePlayer;
            double dx=player.posX-probe.x,dy=player.posY-probe.y,dz=player.posZ-probe.z;
            double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            float angle=Math.max(Math.abs(MathHelper.wrapAngleTo180_float(player.rotationYaw-probe.yaw)),
                    Math.abs(player.rotationPitch-probe.pitch));
            if(!Double.isFinite(distance)||!Float.isFinite(angle))return;
            long now=System.nanoTime();
            LocalCorrectionSnapshot correction=new LocalCorrectionSnapshot(0,SEQUENCE.incrementAndGet(),now,distance,angle);
            WidgetIncidentJournal.global().appliedCorrection(probe.world,probe.connection,probe.epoch,
                    probe.connection.getContextVersion(),correction,now);
        }catch(RuntimeException ignored){/* HUD observation cannot change packet application. */}
    }
    private static boolean current(Minecraft mc,PacketPipeline connection){
        return mc!=null&&mc.theWorld!=null&&mc.thePlayer!=null&&connection!=null&&connection.isActive()
                &&mc.getNetHandler()!=null&&mc.getNetHandler().getNetworkManager().getClientPackets()==connection;
    }
    public static final class CorrectionProbe {
        final Object world;
        final EntityPlayerSP player;
        final PacketPipeline connection;
        final long epoch;
        final double x,y,z;
        final float yaw,pitch;
        CorrectionProbe(Object world,EntityPlayerSP player,PacketPipeline connection,long epoch){
            this.world=world;this.player=player;this.connection=connection;this.epoch=epoch;
            x=player.posX;y=player.posY;z=player.posZ;yaw=player.rotationYaw;pitch=player.rotationPitch;
        }
    }
}
