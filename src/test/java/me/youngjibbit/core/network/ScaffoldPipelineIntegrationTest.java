package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Common API contract from Scaffold 5cbaa54e: accepted geometry/slot, previous PRE, current POST, final-plan guard. */
public class ScaffoldPipelineIntegrationTest {
    private EntityPlayerSP player(SessionConsumerIntegrationTest.Fixture f) throws Exception {
        EntityPlayerSP player=AttackHookTest.bare(EntityPlayerSP.class);
        Field send=EntityPlayerSP.class.getDeclaredField("sendQueue");send.setAccessible(true);send.set(player,f.play);
        player.inventory=new InventoryPlayer(player);player.worldObj=AttackHookTest.bare(AttackHookTest.WorldStub.class);
        f.client.thePlayer=player;f.client.theWorld=(net.minecraft.client.multiplayer.WorldClient)player.worldObj;
        f.client.playerController=new PlayerControllerMP(f.client,f.play);return player;
    }
    private void pos(EntityPlayerSP player,double x) {assertEquals(x,player.getLastReportedPosition().xCoord,0);}
    @Test public void acceptedReplacementGeometryIsObservedBeforePacketPostAndMotionPreUsesThePreviousAcceptance() throws Exception {
        try(SessionConsumerIntegrationTest.Fixture f=new SessionConsumerIntegrationTest.Fixture()) {
            EntityPlayerSP player=player(f);RotationManager rotation=RuntimeServices.rotation();rotation.reset();
            rotation.beginMotion(player,player.worldObj,0,0);rotation.request("scaffold",30,20,70);
            C03PacketPlayer baseline=new C03PacketPlayer.C06PacketPlayerPosLook(1,64,0,20,70,true);
            assertSame(baseline,f.play.addToSendQueueTracked(baseline));rotation.finishMotion(20,70,true);assertTrue(rotation.isOwner("scaffold"));
            rotation.beginMotion(player,player.worldObj,0,0);assertTrue(rotation.canUseRotation(true));pos(player,1);
            rotation.request("scaffold",30,20,70);PacketFlowTest.Observer packets=new PacketFlowTest.Observer();
            C03PacketPlayer replacement=new C03PacketPlayer.C06PacketPlayerPosLook(3,64,0,-40,10,true);
            packets.action=e->{if(e.isPre())e.setPacket(replacement);else {pos(player,3);assertEquals(-40,rotation.getEffectiveYaw(),0);}};
            Pulsar.BUS.subscribe(packets);
            try {
                assertSame(replacement,f.play.addToSendQueueTracked(baseline));
                rotation.finishMotion(rotation.getEffectiveYaw(),rotation.getEffectivePitch(),true);
                assertTrue(rotation.canUseRotation(false));assertFalse(rotation.isOwner("scaffold"));
                rotation.beginMotion(player,player.worldObj,0,0);assertTrue(rotation.canUseRotation(true));packets.action=e->e.cancel();
                assertNull(f.play.addToSendQueueTracked(baseline));rotation.finishMotion(20,70,false);
                assertFalse(rotation.canUseRotation(false));pos(player,3);
            }finally {Pulsar.BUS.unsubscribe(packets);rotation.reset();}
        }
    }
    @Test public void delayedDirectAndSilentMovementShareTheSameAcceptedPositionAndRotationObservation() throws Exception {
        try(SessionConsumerIntegrationTest.Fixture f=new SessionConsumerIntegrationTest.Fixture()) {
            EntityPlayerSP player=player(f);RuntimeServices.rotation().reset();Object owner=new Object();
            try {
                f.network.getClientPackets().delays().request(owner,200,(p,flow)->true);
                C03PacketPlayer delayed=new C03PacketPlayer.C06PacketPlayerPosLook(2,64,0,25,60,true);
                assertNull(f.play.addToSendQueueTracked(delayed));pos(player,0);assertFalse(RuntimeServices.rotation().hasEffectiveRotation());
                f.network.getClientPackets().delays().release(owner);pos(player,2);assertEquals(25,RuntimeServices.rotation().getEffectiveYaw(),0);
                f.network.sendPacket(new C03PacketPlayer.C04PacketPlayerPosition(4,64,0,true));pos(player,4);
                f.network.sendPacketSilent(new C03PacketPlayer.C06PacketPlayerPosLook(5,64,0,45,50,true));
                pos(player,5);assertEquals(45,RuntimeServices.rotation().getEffectiveYaw(),0);
            }finally {RuntimeServices.rotation().reset();}
        }
    }
    private int slot(PlayerControllerMP controller) throws Exception {
        Field f=PlayerControllerMP.class.getDeclaredField("currentPlayerItem");f.setAccessible(true);return f.getInt(controller);
    }
    @Test public void acceptedSlotCacheSurvivesCancellationReplacementAndNestedPostSends() throws Exception {
        try(SessionConsumerIntegrationTest.Fixture f=new SessionConsumerIntegrationTest.Fixture()) {
            EntityPlayerSP player=player(f);player.inventory.currentItem=2;PlayerControllerMP controller=f.client.playerController;
            PacketFlowTest.Observer packets=new PacketFlowTest.Observer();Pulsar.BUS.subscribe(packets);
            try {
                int previousSlot=slot(controller);
                packets.action=e->{if(e.isPre())e.cancel();};assertFalse(controller.syncCurrentPlayItemChecked());assertEquals(previousSlot,slot(controller));
                packets.action=e->{if(e.isPre())e.setPacket(new C09PacketHeldItemChange(5));};assertFalse(controller.syncCurrentPlayItemChecked());assertEquals(5,slot(controller));
                packets.action=e->{};assertTrue(controller.syncCurrentPlayItemChecked());assertEquals(2,slot(controller));
                player.inventory.currentItem=3;packets.action=e->{if(e.isPost())f.network.sendPacketSilent(new C09PacketHeldItemChange(7));};
                assertFalse(controller.syncCurrentPlayItemChecked());assertEquals(7,slot(controller));
            }finally {Pulsar.BUS.unsubscribe(packets);}
        }
    }
    @Test public void nativePlacementGuardSeesFinalReplacementAndIsRecheckedAfterMovementRelease() throws Exception {
        try(SessionConsumerIntegrationTest.Fixture f=new SessionConsumerIntegrationTest.Fixture()) {
            player(f);PacketFlowTest.Observer packets=new PacketFlowTest.Observer();Pulsar.BUS.subscribe(packets);
            C08PacketPlayerBlockPlacement plan=new C08PacketPlayerBlockPlacement();
            try {
                packets.action=e->{if(e.isPre())e.setPacket(new C08PacketPlayerBlockPlacement());};
                assertNull(f.play.addToSendQueueTracked(plan,outgoing->outgoing==plan));assertNull(f.channel.readOutbound());
                packets.action=e->{};AtomicBoolean valid=new AtomicBoolean(true);Object owner=new Object();
                f.network.getClientPackets().delays().request(owner,200,(p,flow)->true);
                C03PacketPlayer move=new C03PacketPlayer.C06PacketPlayerPosLook(2,64,0,30,70,true);
                assertNull(f.play.addToSendQueueTracked(move));
                packets.action=e->{if(e.isPost() && e.getPacket()==move)valid.set(false);};
                assertNull(f.play.addToSendQueueTracked(plan,outgoing->outgoing==plan && valid.get()));
                assertSame(move,f.channel.readOutbound());assertNull(f.channel.readOutbound());
                assertNull(f.network.sendPacketGuarded(plan,true,outgoing->false));
            }finally {Pulsar.BUS.unsubscribe(packets);RuntimeServices.rotation().reset();}
        }
    }
}
