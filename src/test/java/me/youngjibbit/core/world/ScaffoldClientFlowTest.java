package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.network.PacketFlowTest;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;
import net.minecraft.world.border.WorldBorder;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Actual Scaffold -> onUpdateWalkingPlayer -> accepted packet observer -> native controller/ItemBlock.
 * Only world storage, collision enumeration, sound and physical input are synthetic. No game session. */
public class ScaffoldClientFlowTest {
    public static class Scene extends WorldClient {
        Map<BlockPos,IBlockState> blocks;
        WorldBorder border;
        public java.util.concurrent.atomic.AtomicLong motionTime;
        public Scene() { super(null,null,0,null,null); }
        @Override public IBlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.air.getDefaultState()); }
        @Override public boolean isBlockLoaded(BlockPos pos) { return true; }
        @Override public Entity getEntityByID(int id) {if(loadedEntityList!=null)for(Entity entity:loadedEntityList)if(entity.getEntityId()==id)return entity;return null;}
        @Override public List<Entity> getEntitiesInAABBexcluding(Entity excluded,AxisAlignedBB area,com.google.common.base.Predicate<? super Entity> predicate) {
            List<Entity> found=new ArrayList<>();
            for(Entity entity:loadedEntityList)if(entity!=excluded && entity.getEntityBoundingBox().intersectsWith(area) && (predicate==null || predicate.apply(entity)))found.add(entity);
            return found;
        }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public boolean canBlockBePlaced(Block block,BlockPos pos,boolean ignore,EnumFacing side,Entity entity,ItemStack stack) { return getBlockState(pos).getBlock().getMaterial().isReplaceable(); }
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags) { blocks.put(pos,state);return true; }
        @Override public void playSoundEffect(double x,double y,double z,String sound,float volume,float pitch) { }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 start,Vec3 end,boolean liquid,boolean empty,boolean last) {
            MovingObjectPosition nearest=null;double distance=Double.MAX_VALUE;
            for(Map.Entry<BlockPos,IBlockState> cell:blocks.entrySet()) {
                BlockPos pos=cell.getKey();
                MovingObjectPosition hit=cell.getValue().getBlock().collisionRayTrace(this,pos,start,end);
                if(hit!=null && start.squareDistanceTo(hit.hitVec)<distance) { nearest=hit;distance=start.squareDistanceTo(hit.hitVec); }
            }
            return nearest;
        }
    }
    static void set(Class<?> type,Object object,String name,Object value) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value); }
    /** Match the production body boundary when a fixture calls the walking hook directly. */
    public static void ordinaryWalking(RotationBehaviorHookTest.Fixture f) {
        me.youngjibbit.core.observation.RuntimeObservation observation=f.runtime.getObservation();
        observation.beginOuterTick();
        try { observation.observePlayerStep(f.player,()->{f.player.onUpdateWalkingPlayer();return true;},step->step.getAsBoolean()); }
        finally { observation.endOuterTick(); }
    }
    public static Scene prepare(RotationBehaviorHookTest.Fixture f,double localX,double wireX) throws Exception {
        Bootstrap.register();
        Scene scene=RotationBehaviorHookTest.bare(Scene.class);scene.blocks=new LinkedHashMap<>();scene.border=new WorldBorder();scene.motionTime=new java.util.concurrent.atomic.AtomicLong();
        scene.blocks.put(new BlockPos(-1,63,0),Blocks.stone.getDefaultState());
        f.mc.theWorld=scene;f.player.worldObj=scene;
        set(EntityPlayerSP.class,f.player,"mc",f.mc);
        f.player.posX=localX;f.player.posY=64;f.player.posZ=.5;f.player.onGround=true;
        f.player.setEntityBoundingBox(new AxisAlignedBB(localX-.3,64,.2,localX+.3,65.8,.8));
        set(EntityPlayerSP.class,f.player,"lastReportedPosX",wireX);set(EntityPlayerSP.class,f.player,"lastReportedPosY",64D);set(EntityPlayerSP.class,f.player,"lastReportedPosZ",.5D);
        f.player.observeAcceptedPosition(wireX,64,.5);
        f.player.inventory.mainInventory[3]=new ItemStack(Blocks.stone,64);
        f.mc.gameSettings=RotationBehaviorHookTest.bare(GameSettings.class);
        f.mc.gameSettings.keyBindJump=new KeyBinding("synthetic.jump",0,"synthetic");
        f.mc.gameSettings.keyBindSneak=new KeyBinding("synthetic.sneak",0,"synthetic");f.mc.gameSettings.mouseSensitivity=.5f;
        f.mc.timer=new net.minecraft.util.Timer(20,()->0);
        set(NetHandlerPlayClient.class,f.player.sendQueue,"gameController",f.mc);
        f.network.configureClientPackets(new PacketFlowTest.MainThread(),f.player.sendQueue::observeAcceptedOutbound);
        set(me.youngjibbit.core.bootstrap.ClientRuntime.class,f.runtime,"rotationManager",new me.youngjibbit.core.combat.RotationManager(scene.motionTime::get,781));
        f.scaffold.getBooleanSetting("Swing").setValue(false);
        return scene;
    }
    @Test public void everyModeAndTimingPlacesThroughTheNativeFlowBeforePeriodicPositionRefresh() throws Exception {
        for(String mode:new String[]{"Back","Down","NCP"}) for(String timing:new String[]{"Pre","Post"}) for(double wireX:new double[]{.48,.46}) {
            try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
                Scene scene=prepare(f,.5,wireX);
                f.scaffold.getModeSetting("Rotation Mode").setValue(mode);f.scaffold.getModeSetting("Place Timing").setValue(timing);
                f.scaffold.onEnable();int placements=0;
                try {
                    for(int tick=0;tick<12 && !scene.blocks.containsKey(new BlockPos(0,63,0));tick++) {
                        scene.motionTime.addAndGet(50_000_000L);f.runtime.getScaffoldPlacementService().beginTick();
                        ordinaryWalking(f);
                        Object sent;while((sent=f.channel.readOutbound())!=null)if(sent instanceof C08PacketPlayerBlockPlacement)placements++;
                    }
                    assertTrue(mode+"/"+timing+": local displacement must not make the planned packet-pose raycast miss",scene.blocks.containsKey(new BlockPos(0,63,0)));
                    assertEquals(1,placements);assertEquals(63,f.player.inventory.mainInventory[3].stackSize);
                    assertEquals(0,f.player.inventory.currentItem);assertEquals(0,f.player.rotationYaw,0);
                    assertEquals(wireX==.48 ? .48 : .5,f.player.getLastReportedPosition().xCoord,1e-12);
                } finally { f.scaffold.onDisable(); }
            }
        }
    }
    @Test public void diagnosticsDefaultOffExplainNoBlocksAndAreRateLimitedWithControlledTime() throws Exception {
        try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
            prepare(f,.5,.48);f.player.inventory.mainInventory[3]=null;
            java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong();
            set(me.youngjibbit.module.impl.world.Scaffold.class,f.scaffold,"diagnosticClock",(java.util.function.LongSupplier)clock::get);
            assertFalse(f.scaffold.getBooleanSetting("Placement Diagnostics").getValue());
            f.scaffold.onEnable();
            try {
                for(int i=0;i<5;i++) f.player.onUpdateWalkingPlayer();
                assertEquals(0,f.player.diagnosticMessages);
                assertEquals(ScaffoldPlacementService.Rejection.NO_BLOCKS,f.runtime.getScaffoldPlacementService().getLastRejection());
                f.scaffold.getBooleanSetting("Placement Diagnostics").setValue(true);
                for(int i=0;i<10;i++) f.player.onUpdateWalkingPlayer();
                assertEquals(1,f.player.diagnosticMessages);
                clock.set(1_999_999_999L);f.player.onUpdateWalkingPlayer();assertEquals(1,f.player.diagnosticMessages);
                clock.set(2_000_000_000L);f.player.onUpdateWalkingPlayer();assertEquals(2,f.player.diagnosticMessages);
            } finally { f.scaffold.onDisable(); }
        }
    }

}
