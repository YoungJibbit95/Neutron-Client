package me.youngjibbit.core.network;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.Pulsar;
import me.youngjibbit.event.player.*;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.*;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.world.WorldSettings;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Executes the real controller and packet hooks with bare entities; never starts a game or renderer. */
public class AttackHookTest {
    static <T> T bare(Class<T> type) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(type));
    }
    private static Entity bareUnchecked() {
        try {return bare(EntityOtherPlayerMP.class);} catch(Exception error) {throw new AssertionError(error);}
    }
    public static class WorldStub extends WorldClient {
        Entity target;
        public WorldStub() {super(null,null,0,null,null);}
        @Override public Entity getEntityByID(int id) {return target!=null && target.getEntityId()==id?target:null;}
    }
    public static final class Attacks implements Subscriber {
        boolean cancel;int results;
        @Subscribe final Listener<AttackEvent> pre=new Listener<>(e->{if(cancel)e.cancel();});
        @Subscribe final Listener<AttackResultEvent> post=new Listener<>(e->results++);
    }
    @Test public void resultRequiresSuccessfulControllerGuardAndAcceptedAttackPacket() throws Exception {
        Minecraft mc=bare(Minecraft.class);WorldStub world=bare(WorldStub.class);
        EntityPlayerSP player=bare(EntityPlayerSP.class);player.worldObj=world;player.inventory=new InventoryPlayer(player);mc.thePlayer=player;mc.theWorld=world;
        world.target=bare(EntityOtherPlayerMP.class);world.target.setEntityId(9);world.target.worldObj=world;
        NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);EmbeddedChannel channel=new EmbeddedChannel(network);
        NetHandlerPlayClient play=new NetHandlerPlayClient(null,null,network,null);network.configureClientPackets(new PacketFlowTest.MainThread());
        PlayerControllerMP controller=new PlayerControllerMP(mc,play);
        Field mode=PlayerControllerMP.class.getDeclaredField("currentGameType");mode.setAccessible(true);mode.set(controller,WorldSettings.GameType.SPECTATOR);
        Attacks attacks=new Attacks();PacketFlowTest.Observer packets=new PacketFlowTest.Observer();Pulsar.BUS.subscribe(attacks);Pulsar.BUS.subscribe(packets);
        try {
            attacks.cancel=true;assertFalse(controller.tryAttackEntity(player,world.target,()->true));assertEquals(0,attacks.results);
            attacks.cancel=false;assertFalse(controller.tryAttackEntity(player,world.target,()->false));assertEquals(0,attacks.results);
            packets.action=e->{if(e.isPre() && e.getPacket() instanceof C02PacketUseEntity)e.cancel();};
            assertFalse(controller.tryAttackEntity(player,world.target,()->true));assertEquals(0,attacks.results);
            packets.action=e->{};assertTrue(controller.tryAttackEntity(player,world.target,()->true));assertEquals(1,attacks.results);
            java.util.concurrent.atomic.AtomicBoolean aligned=new java.util.concurrent.atomic.AtomicBoolean(true);
            packets.action=e->{if(e.isPre() && e.getPacket() instanceof C02PacketUseEntity)aligned.set(false);};
            assertFalse(controller.tryAttackEntity(player,world.target,aligned::get));assertEquals(1,attacks.results);
            while(channel.readOutbound()!=null) { }
            packets.action=e->{if(e.isPre() && e.getPacket() instanceof C02PacketUseEntity)
                e.setPacket(new C02PacketUseEntity(world.target,C02PacketUseEntity.Action.INTERACT));};
            assertFalse(controller.tryAttackEntity(player,world.target,()->true));assertEquals(1,attacks.results);
            assertNull("replacement must be rejected before transport",channel.readOutbound());
        } finally {Pulsar.BUS.unsubscribe(attacks);Pulsar.BUS.unsubscribe(packets);channel.finish();}
    }
    @Test public void replacementAndSceneOrDeathChangesCannotSendStaleAttacksOrReportSuccess() throws Exception {
        Minecraft mc=bare(Minecraft.class);WorldStub world=bare(WorldStub.class);
        EntityPlayerSP player=bare(EntityPlayerSP.class);player.worldObj=world;player.inventory=new InventoryPlayer(player);mc.thePlayer=player;mc.theWorld=world;
        world.target=bare(EntityOtherPlayerMP.class);world.target.worldObj=world;world.target.setEntityId(9);
        NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);EmbeddedChannel channel=new EmbeddedChannel(network);
        NetHandlerPlayClient play=new NetHandlerPlayClient(null,null,network,null);network.configureClientPackets(new PacketFlowTest.MainThread());
        PlayerControllerMP controller=new PlayerControllerMP(mc,play);Field mode=PlayerControllerMP.class.getDeclaredField("currentGameType");mode.setAccessible(true);mode.set(controller,WorldSettings.GameType.SPECTATOR);
        Attacks attacks=new Attacks();PacketFlowTest.Observer packets=new PacketFlowTest.Observer();Pulsar.BUS.subscribe(attacks);Pulsar.BUS.subscribe(packets);
        try {
            for(int i=0;i<6;i++) {
                final int scenario=i;
                packets.action=e->{
                    if(scenario==5 && e.isPost() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity)mc.theWorld=null;
                    if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity) {
                    if(scenario==0)e.setPacket(new C02PacketUseEntity(world.target,C02PacketUseEntity.Action.INTERACT));
                    if(scenario==4) {Entity other=bareUnchecked();other.setEntityId(10);e.setPacket(new C02PacketUseEntity(other,C02PacketUseEntity.Action.ATTACK));}
                    if(scenario==1)mc.theWorld=null;
                    if(scenario==2)world.target.isDead=true;
                    if(scenario==3)player.isDead=true;
                }};
                assertFalse(controller.tryAttackEntity(player,world.target,()->true));
                if(scenario==5)assertTrue(channel.readOutbound() instanceof C02PacketUseEntity);else assertNull(channel.readOutbound());
                assertEquals(0,attacks.results);
                mc.theWorld=world;world.target.isDead=false;player.isDead=false;
            }
            packets.action=e->{if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity)
                e.setPacket(new C02PacketUseEntity(world.target,C02PacketUseEntity.Action.ATTACK));};
            assertTrue(controller.tryAttackEntity(player,world.target,()->true));assertEquals(1,attacks.results);
            assertEquals(C02PacketUseEntity.Action.ATTACK,((C02PacketUseEntity)channel.readOutbound()).getAction());
        } finally {Pulsar.BUS.unsubscribe(attacks);Pulsar.BUS.unsubscribe(packets);channel.finish();}
    }

}
