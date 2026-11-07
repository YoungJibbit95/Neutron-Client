package me.youngjibbit.core.world;

import me.youngjibbit.core.arsenic.geometry.Box;
import me.youngjibbit.core.arsenic.scaffold.ScaffoldCore;
import me.youngjibbit.core.arsenic.scaffold.ScaffoldWorld;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.ArsenicMotionCurve;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.RotationSensitivity;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.Random;

/** Geometry/input adapter. It proposes plans; only Scaffold's existing authority can place. */
public final class ArsenicScaffoldController {
    private ScaffoldCore core=new ScaffoldCore(ScaffoldCore.Tuning.best(),new Random());
    private long preparedSequence=Long.MIN_VALUE,postSequence=Long.MIN_VALUE;
    private float yawMomentum,pitchMomentum;
    private ArsenicMotionCurve softCurve;

    public static final class Prepared {
        public final PlacementTarget target;
        public final float yaw,pitch;
        private Prepared(PlacementTarget target,float yaw,float pitch) { this.target=target;this.yaw=yaw;this.pitch=pitch; }
    }

    public void reset() { softCurve=null;core=new ScaffoldCore(ScaffoldCore.Tuning.best(),new Random());preparedSequence=postSequence=Long.MIN_VALUE;yawMomentum=pitchMomentum=0; }

    public Prepared prepare(RotationManager rotations, boolean eagle, double safety, double minSpeed, double maxSpeed) {
        return prepare(rotations, eagle, safety, minSpeed, maxSpeed, false);
    }

    public Prepared prepare(RotationManager rotations,boolean eagle,double safety,double minSpeed,double maxSpeed,boolean softMotion) {
        if(preparedSequence==rotations.getMotionSequence())return null;
        preparedSequence=rotations.getMotionSequence();
        Minecraft mc=Minecraft.getMinecraft();
        ScaffoldCore.Input in=input(mc,eagle,safety,null);
        in.speedMin=Math.min(minSpeed,maxSpeed);in.speedMax=Math.max(minSpeed,maxSpeed);
        WorldAdapter world=new WorldAdapter(mc);
        ScaffoldCore.Rotation aim=core.rotate(in,world);
        if(!rotations.canUseRotation("scaffold",true))yawMomentum=pitchMomentum=0;
        float yawError=MathHelper.wrapAngleTo180_float(aim.yaw-in.silentYaw),pitchError=aim.pitch-in.silentPitch;
        float yawStep, pitchStep;
        if (softMotion) {
            if (softCurve == null || !rotations.canUseRotation("scaffold", true))
                softCurve = new ArsenicMotionCurve(new Random());
            float[] delta = softCurve.advance(yawError, pitchError, aim.speed, aim.speed * .65f);
            yawStep = delta[0];
            pitchStep = delta[1];
        } else {
            softCurve = null;
            yawMomentum=momentum(yawMomentum,yawError,aim.speed,120);
            pitchMomentum=momentum(pitchMomentum,pitchError,aim.speed*.65f,60);
            yawStep=Math.signum(yawError)*Math.min(Math.abs(yawError),yawMomentum);
            pitchStep=Math.signum(pitchError)*Math.min(Math.abs(pitchError),pitchMomentum);
        }
        float[] next=rotations.applyGcd(in.silentYaw+yawStep,in.silentPitch+pitchStep,in.silentYaw,in.silentPitch);
        ScaffoldCore.Target target=core.target();
        if(target==null)return new Prepared(null,next[0],next[1]);
        BlockPos support=new BlockPos(target.x,target.y,target.z);
        EnumFacing face=EnumFacing.getFront(target.face);
        Vec3 eyes=new Vec3(in.posX,in.posY+in.eyeHeight,in.posZ);
        Vec3 direction=Entity.getVectorForRotation(aim.pitch,aim.yaw);
        MovingObjectPosition hit=mc.theWorld.rayTraceBlocks(eyes,eyes.addVector(direction.xCoord*4.5,direction.yCoord*4.5,direction.zCoord*4.5),false,false,false);
        Vec3 point=hit!=null && hit.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK && support.equals(hit.getBlockPos()) && face==hit.sideHit?hit.hitVec:null;
        return new Prepared(RuntimeServices.scaffoldPlacement().planFace(support,face,point),next[0],next[1]);
    }

    private static float momentum(float old,float error,float speed,float ease) {
        float t=Math.min(1,Math.abs(error)/ease);
        float wanted=speed*Math.max(ease==120?.12f:.15f,t*(2-t));
        return Math.max(0,old+(wanted-old)*.45f);
    }

    public PlacementTarget acceptedPlan(PlacementTarget prepared,RotationManager rotations,boolean eagle,double safety) {
        if(prepared==null || preparedSequence!=rotations.getMotionSequence() || !rotations.canUseRotation("scaffold",false))return null;
        Minecraft mc=Minecraft.getMinecraft();
        ScaffoldCore.Input in=input(mc,eagle,safety,null);
        Vec3 wire=mc.thePlayer.getLastReportedPosition();
        in.posX=wire.xCoord;in.posY=wire.yCoord;in.posZ=wire.zCoord;
        ScaffoldCore.Action action=core.post(in,new WorldAdapter(mc),rotations.getEffectiveYaw(),rotations.getEffectivePitch());
        if(!action.place || !prepared.getBlockPos().equals(new BlockPos(action.x,action.y,action.z))
                || prepared.getFacing()!=EnumFacing.getFront(action.face))return null;
        return RuntimeServices.scaffoldPlacement().alignAcceptedHit(prepared,rotations);
    }

    public void finish(RotationManager rotations,boolean eagle,double safety,boolean placed) {
        if(postSequence==rotations.getMotionSequence())return;
        postSequence=rotations.getMotionSequence();
        Minecraft mc=Minecraft.getMinecraft();
        core.sneak(input(mc,eagle,safety,null),new WorldAdapter(mc),placed);
        if(!rotations.canUseRotation("scaffold",false))yawMomentum=pitchMomentum=0;
    }

    public void applyInput(MovementInputEvent event,boolean eagle,double safety) {
        Minecraft mc=Minecraft.getMinecraft();
        ScaffoldCore.Input in=input(mc,eagle,safety,event);
        float[] nudge=core.inputNudge(in);
        if(nudge!=null && (event.getForward()!=0 || event.getStrafe()!=0)) {
            float scale=Math.max(Math.abs(event.getForward()),Math.abs(event.getStrafe()));
            event.setForward(nudge[0]*scale);event.setStrafe(nudge[1]*scale);
            in=input(mc,eagle,safety,event);
        }
        if(!event.isSneak() && core.previewSneak(in,new WorldAdapter(mc))) {
            event.setSneak(true);event.setForward(event.getForward()*.3f);event.setStrafe(event.getStrafe()*.3f);
        }
    }

    private static ScaffoldCore.Input input(Minecraft mc,boolean eagle,double safety,MovementInputEvent event) {
        EntityPlayerSP player=mc.thePlayer;
        ScaffoldCore.Input in=new ScaffoldCore.Input();
        in.posX=player.posX;in.posY=player.posY;in.posZ=player.posZ;
        in.motionX=player.motionX;in.motionY=player.motionY;in.motionZ=player.motionZ;in.onGround=player.onGround;
        in.eyeHeight=player.getEyeHeight();in.cameraYaw=player.rotationYaw;
        RotationManager rotation=RuntimeServices.rotation();
        in.silentYaw=rotation.hasEffectiveRotation()?rotation.getEffectiveYaw():player.rotationYaw;
        in.silentPitch=rotation.hasEffectiveRotation()?rotation.getEffectivePitch():player.rotationPitch;
        in.keyForward=down(mc.gameSettings.keyBindForward);in.keyBack=down(mc.gameSettings.keyBindBack);
        in.keyLeft=down(mc.gameSettings.keyBindLeft);in.keyRight=down(mc.gameSettings.keyBindRight);in.keyJump=down(mc.gameSettings.keyBindJump);
        float forward=event==null?player.movementInput.moveForward:event.getForward();
        float strafe=event==null?player.movementInput.moveStrafe:event.getStrafe();
        boolean sneak=event==null?player.movementInput.sneak:event.isSneak();
        in.moveScale=sneak?.3f:1f;
        // Fresh input uses the conservative full-speed bound before item slowdown;
        // PRE already contains the actual slowdown chosen by the input hooks.
        PlayerMoveUpdateEvent movement=new PlayerMoveUpdateEvent(strafe,forward,0,player.rotationYaw,player.rotationPitch);
        RotationCorrections.movement(movement);
        in.moveForward=movement.getForward();in.moveStrafe=movement.getStrafe();in.movementYaw=movement.getYaw();
        in.aiMoveSpeed=player.getAIMoveSpeed();
        in.slipperiness=mc.theWorld.getBlockState(new BlockPos(player.posX,player.getEntityBoundingBox().minY-1,player.posZ)).getBlock().slipperiness;
        in.gcd=RotationSensitivity.mouseStep(mc.gameSettings.mouseSensitivity);
        in.hasBlock=RuntimeServices.scaffoldPlacement().getBlockSlot()>=0;in.eagle=eagle;in.safety=Math.max(0,Math.min(3,safety));
        return in;
    }
    private static boolean down(KeyBinding key) { return key!=null && key.isKeyDown(); }

    private static final class WorldAdapter implements ScaffoldWorld {
        private final World world;
        private final EntityPlayerSP player;
        WorldAdapter(Minecraft mc) { world=mc.theWorld;player=mc.thePlayer; }
        private boolean loaded(BlockPos pos) { return pos.getY()>=0 && pos.getY()<256 && world.isBlockLoaded(pos) && world.getWorldBorder().contains(pos); }
        public boolean isAir(int x,int y,int z) { BlockPos p=new BlockPos(x,y,z);return loaded(p) && world.isAirBlock(p); }
        public boolean isFullCube(int x,int y,int z) { BlockPos p=new BlockPos(x,y,z);return loaded(p) && world.getBlockState(p).getBlock().isFullCube(); }
        public boolean canPlaceOnSide(int x,int y,int z,int face) { return RuntimeServices.scaffoldPlacement().canPlanFace(new BlockPos(x,y,z),EnumFacing.getFront(face)); }
        public boolean boxFree(Box box) {
            return world.getCollidingBoundingBoxes(player,new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ)).isEmpty();
        }
        public boolean rayTrace(double sx,double sy,double sz,double ex,double ey,double ez,Hit out) {
            MovingObjectPosition hit=world.rayTraceBlocks(new Vec3(sx,sy,sz),new Vec3(ex,ey,ez),false,false,false);
            if(hit==null || hit.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK || hit.hitVec==null)return false;
            out.x=hit.getBlockPos().getX();out.y=hit.getBlockPos().getY();out.z=hit.getBlockPos().getZ();out.face=hit.sideHit.getIndex();
            out.hx=hit.hitVec.xCoord;out.hy=hit.hitVec.yCoord;out.hz=hit.hitVec.zCoord;return true;
        }
    }
}
