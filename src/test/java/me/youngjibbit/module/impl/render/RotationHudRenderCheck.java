package me.youngjibbit.module.impl.render;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.Vec3;

/** Local-player rendering with an embedded connection, never a login, world tick or external transport. */
final class RotationHudRenderCheck {
    static void run() throws Exception {
        Minecraft mc=IngameHudRenderCheck.mc;
        Object oldRuntime=Pulsar.INSTANCE.getRuntime();
        NetHandlerPlayClient oldHandler=mc.thePlayer.sendQueue;
        PlayerControllerMP oldController=mc.playerController;
        boolean oldFocus=mc.inGameHasFocus,oldFrustum=mc.thePlayer.ignoreFrustumCheck;
        int oldView=mc.gameSettings.thirdPersonView,oldTicks=mc.thePlayer.ticksExisted;
        float oldYaw=mc.thePlayer.rotationYaw,oldPitch=mc.thePlayer.rotationPitch;
        double oldX=mc.thePlayer.motionX,oldZ=mc.thePlayer.motionZ;
        NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        EmbeddedChannel transport=new EmbeddedChannel(network);
        Module aura=IngameHudRenderCheck.modules.getModule(Aura.class),scaffold=IngameHudRenderCheck.modules.getModule(Scaffold.class);
        ClientRuntime runtime=new ClientRuntime(IngameHudRenderCheck.modules,null);
        try {
            network.configureClientPackets(new PacketFlowTest.MainThread());
            NetHandlerPlayClient handler=new NetHandlerPlayClient(mc,null,network,mc.getSession().getProfile());network.setNetHandler(handler);
            SessionInfoRenderCheck.set(mc.thePlayer,"sendQueue",handler);mc.playerController=new PlayerControllerMP(mc,handler);
            SessionInfoRenderCheck.set(Pulsar.INSTANCE,"runtime",runtime);
            mc.inGameHasFocus=true;mc.gameSettings.thirdPersonView=1;mc.thePlayer.ticksExisted=20;mc.thePlayer.ignoreFrustumCheck=true;
            mc.thePlayer.rotationYaw=180;mc.thePlayer.rotationPitch=0;mc.theWorld.loadedEntityList.add(mc.thePlayer);
            IngameHudRenderCheck.includeLocalPlayer=true;
            aura.setToggled(true);scaffold.setToggled(true);
            for(Module source:new Module[]{aura,scaffold})for(String body:new String[]{"Direkt","Natürlich"})for(boolean kawase:new boolean[]{false,true}) {
                source.getModeSetting("Movefix").setValue("Rotationsgebunden");source.getModeSetting("Body Rotation").setValue(body);
                IngameHudRenderCheck.glow.getBooleanSetting("Kawase Glow").setValue(kawase);
                RotationManager rotations=runtime.getRotationManager();rotations.bindConnection(network.getClientPackets());
                rotations.beginMotion(mc.thePlayer,mc.theWorld,180,0);rotations.request(source.getId(),30,90,17);rotations.finishMotion(90,17,true);
                BodyRotationController.Pose before=RotationCorrections.renderPose(mc.thePlayer,0);
                SessionInfoRenderCheck.require(before!=null&&before.headYaw==90&&before.headPitch==17,"Local body correction was not selected");
                Vec3 position=mc.thePlayer.getLastReportedPosition();long sequence=rotations.getMotionSequence();
                IngameHudRenderCheck.frame(IngameHudRenderCheck.cow);
                SessionInfoRenderCheck.require(((java.util.List<?>)SessionInfoRenderCheck.get(IngameHudRenderCheck.glow,"entities")).contains(mc.thePlayer),"Glow did not capture the local third-person model");
                ModelBiped model=(ModelBiped)((RendererLivingEntity<?>)mc.getRenderManager().getEntityRenderObject(mc.thePlayer)).getMainModel();
                SessionInfoRenderCheck.require(Math.abs(model.bipedHead.rotateAngleX-Math.toRadians(17))<.0001,"Living renderer did not consume the accepted head pitch");
                SessionInfoRenderCheck.require(RotationCorrections.renderPose(mc.thePlayer,0)==before,"Repeated model/glow passes advanced body smoothing within one frame");
                SessionInfoRenderCheck.require(mc.thePlayer.rotationYaw==180&&mc.thePlayer.rotationPitch==0,"Body/glow/HUD rendering changed the camera");
                SessionInfoRenderCheck.require(rotations.getMotionSequence()==sequence&&rotations.getEffectiveYaw()==90&&rotations.getEffectivePitch()==17&&source.getId().equals(rotations.getConfirmedOwner()),"Rendering changed accepted rotation/ownership");
                Vec3 after=mc.thePlayer.getLastReportedPosition();
                SessionInfoRenderCheck.require(position.xCoord==after.xCoord&&position.yCoord==after.yCoord&&position.zCoord==after.zCoord,"Rendering changed accepted position");
                mc.thePlayer.motionX=mc.thePlayer.motionZ=0;mc.thePlayer.moveFlying(0,1,.1f);
                SessionInfoRenderCheck.require(Math.abs(mc.thePlayer.motionX+.1)<.00002&&Math.abs(mc.thePlayer.motionZ)<.00002,"North camera/west owner/W lost rotation-bound movement after rendering");
                SessionInfoRenderCheck.require(transport.readOutbound()==null,"Body/glow/HUD rendering emitted a packet");
            }
            System.out.println("PASS: local Aura/Scaffold body poses + both entity-glow paths + native HUD/bloom; one pose per frame; camera/accepted pose retained; north camera/west rotation/W moves west; no render packets");
        } finally {
            aura.setToggled(false);scaffold.setToggled(false);runtime.getRotationManager().reset();RotationCorrections.reset();
            IngameHudRenderCheck.includeLocalPlayer=false;mc.theWorld.loadedEntityList.remove(mc.thePlayer);
            mc.thePlayer.rotationYaw=oldYaw;mc.thePlayer.rotationPitch=oldPitch;mc.thePlayer.motionX=oldX;mc.thePlayer.motionZ=oldZ;
            mc.inGameHasFocus=oldFocus;mc.gameSettings.thirdPersonView=oldView;mc.thePlayer.ticksExisted=oldTicks;mc.thePlayer.ignoreFrustumCheck=oldFrustum;
            SessionInfoRenderCheck.set(mc.thePlayer,"sendQueue",oldHandler);mc.playerController=oldController;SessionInfoRenderCheck.set(Pulsar.INSTANCE,"runtime",oldRuntime);
            transport.finish();while(transport.readOutbound()!=null){}
        }
    }
}
