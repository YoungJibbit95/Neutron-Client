package me.youngjibbit.core.combat;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.stats.StatBase;
import net.minecraft.network.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import static org.junit.Assert.*;

/** Real Entity.moveFlying and EntityLivingBase.jump, with a disposable client and embedded transport. */
public class RotationBehaviorHookTest {
    public static class Player extends EntityPlayerSP {
        public boolean sprint,usingItem;
        @Override public boolean isUsingItem() { return usingItem; }
        @Override public boolean isInvisible() { return false; }
        @Override public net.minecraft.scoreboard.Team getTeam() { return null; }
        @Override public boolean canEntityBeSeen(net.minecraft.entity.Entity entity) { return true; }
        public int diagnosticMessages;
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message) { diagnosticMessages++; }
        public Player() { super(null,null,null,null); }
        @Override public boolean isEntityAlive() { return !isDead; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isRiding() { return false; }
        @Override public boolean isSprinting() { return sprint; }
        @Override public void setSprinting(boolean value) { sprint=value; }
        @Override public boolean isSneaking() { return false; }
        @Override public float getEyeHeight() { return 1.62f; }
        @Override public boolean canPlayerEdit(net.minecraft.util.BlockPos pos,net.minecraft.util.EnumFacing face,net.minecraft.item.ItemStack stack) { return true; }
        @Override public boolean isPotionActive(Potion potion) { return false; }
        @Override public void triggerAchievement(StatBase stat) { }
        @Override public void addExhaustion(float amount) { }
    }
    public static <T> T bare(Class<T> type) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(type));
    }
    static Field field(Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    public static final class Fixture implements AutoCloseable {
        final sun.misc.Unsafe unsafe;
        final Field singleton=field(Minecraft.class,"theMinecraft"),moduleClient=field(Module.class,"mc");
        final Field mm=field(Pulsar.class,"mm"),runtimeField=field(Pulsar.class,"runtime");
        final Object oldClient=singleton.get(null),oldMm=mm.get(Pulsar.INSTANCE),oldRuntime=runtimeField.get(Pulsar.INSTANCE);
        final Object oldModuleClient;
        public final Minecraft mc=bare(Minecraft.class);
        public final Player player=bare(Player.class);
        public final WorldClient world=bare(WorldClient.class);
        public final NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        public final EmbeddedChannel channel=new EmbeddedChannel(network);
        public final Aura aura;
        public final Scaffold scaffold;
        public final ClientRuntime runtime;
        public Fixture() throws Exception {
            unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
            oldModuleClient=unsafe.getObject(unsafe.staticFieldBase(moduleClient),unsafe.staticFieldOffset(moduleClient));
            singleton.set(null,mc);unsafe.putObject(unsafe.staticFieldBase(moduleClient),unsafe.staticFieldOffset(moduleClient),mc);
            mc.thePlayer=player;mc.theWorld=world;mc.inGameHasFocus=true;
            field(Minecraft.class,"renderViewEntity").set(mc,player);
            player.worldObj=world;player.ticksExisted=20;player.inventory=new InventoryPlayer(player);
            NetHandlerPlayClient play=new NetHandlerPlayClient(null,null,network,null);
            network.configureClientPackets(new PacketFlowTest.MainThread());network.setNetHandler(play);field(EntityPlayerSP.class,"sendQueue").set(player,play);
            mc.playerController=new PlayerControllerMP(mc,play);
            ModuleManager modules=bare(ModuleManager.class);
            LinkedHashMap<Class<? extends Module>,Module> byClass=new LinkedHashMap<>();
            field(ModuleManager.class,"modulesByClass").set(modules,byClass);mm.set(Pulsar.INSTANCE,modules);
            runtime=new ClientRuntime(modules,null);runtimeField.set(Pulsar.INSTANCE,runtime);
            aura=new Aura();scaffold=new Scaffold();byClass.put(Aura.class,aura);byClass.put(Scaffold.class,scaffold);
            field(Module.class,"toggled").set(aura,true);field(Module.class,"toggled").set(scaffold,true);
            aura.getModeSetting("movefix").setValue("Rotationsgebunden");
            scaffold.getModeSetting("movefix").setValue("Rotationsgebunden");
        }
        public void accept(String owner,float yaw) {
            RotationManager r=runtime.getRotationManager();r.bindConnection(network.getClientPackets());
            r.beginMotion(player,mc.theWorld,player.rotationYaw,0);r.request(owner,owner.equals("scaffold")?30:20,yaw,0);r.finishMotion(yaw,0,true);
        }
        public void close() throws Exception {
            runtime.getRotationManager().reset();channel.finish();
            while(channel.readOutbound()!=null) { }
            runtimeField.set(Pulsar.INSTANCE,oldRuntime);mm.set(Pulsar.INSTANCE,oldMm);singleton.set(null,oldClient);
            unsafe.putObject(unsafe.staticFieldBase(moduleClient),unsafe.staticFieldOffset(moduleClient),oldModuleClient);
        }
    }
    @Test public void northCameraWestAcceptedRotationAndWAccelerateWestAtActualConsumer() throws Exception {
        try(Fixture f=new Fixture()) {
            f.player.rotationYaw=180;f.accept("aura",90);
            f.player.moveFlying(0,1,.1f);
            assertEquals(-.1,f.player.motionX,2e-5);assertEquals(0,f.player.motionZ,2e-5);
            assertEquals(180,f.player.rotationYaw,0);
        }
    }
    @Test public void allKeysDiagonalsSneakAndAngleWrapUseTheAcceptedBasisWithOneVanillaCap() throws Exception {
        try(Fixture f=new Fixture()) {
            for(String owner:new String[]{"aura","scaffold"}) for(float yaw:new float[]{90,-90,179,-179,180,-180})
                for(float strength:new float[]{1,.3f}) for(float s:new float[]{-1,0,1}) for(float forward:new float[]{-1,0,1}) {
                    f.player.rotationYaw=180;f.accept(owner,yaw);f.player.motionX=f.player.motionZ=0;
                    f.player.moveFlying(s*strength,forward*strength,.1f);
                    double cap=Math.max(1,Math.hypot(s*strength,forward*strength)),r=Math.toRadians(yaw);
                    assertEquals(.1*(s*strength*Math.cos(r)-forward*strength*Math.sin(r))/cap,f.player.motionX,3e-5);
                    assertEquals(.1*(forward*strength*Math.cos(r)+s*strength*Math.sin(r))/cap,f.player.motionZ,3e-5);
                    assertEquals(180,f.player.rotationYaw,0);
                }
        }
    }
    @Test public void sprintJumpUsesAcceptedOwnerAndInvalidOrUnownedStateFallsBackToCamera() throws Exception {
        try(Fixture f=new Fixture()) {
            f.player.rotationYaw=180;f.player.sprint=true;f.accept("aura",90);f.player.jump();
            assertEquals(-.2,f.player.motionX,3e-5);assertEquals(0,f.player.motionZ,3e-5);assertTrue(f.player.motionY>.4);
            f.accept("scaffold",-90);f.player.motionX=f.player.motionZ=0;f.player.jump();
            assertEquals(.2,f.player.motionX,3e-5);assertEquals(0,f.player.motionZ,3e-5);
            f.runtime.getRotationManager().reset();f.player.motionX=f.player.motionZ=0;f.player.moveFlying(0,1,.1f);
            assertEquals(0,f.player.motionX,3e-5);assertEquals(-.1,f.player.motionZ,3e-5);
            f.accept("aura",90);f.mc.inGameHasFocus=false;f.player.motionX=f.player.motionZ=0;f.player.moveFlying(0,1,.1f);
            assertEquals(0,f.player.motionX,3e-5);assertEquals(-.1,f.player.motionZ,3e-5);
        }
    }
}
