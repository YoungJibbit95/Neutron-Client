package me.youngjibbit.core.combat;

import io.netty.channel.embedded.EmbeddedChannel;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.event.EventFlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Real acceptance observer and world raycast, with synthetic entities and an embedded transport. */
public class AcceptedPositionRaycastTest {
    static <T> T bare(Class<T> type) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(type));
    }
    public static class Viewer extends EntityPlayerSP {
        public Viewer(){super(null,null,null,null);}
        @Override public float getEyeHeight(){return 1.62f;}
    }
    public static class Target extends EntityOtherPlayerMP {
        public Target(){super(null,null);}
        @Override public boolean canBeCollidedWith(){return true;}
        @Override public float getCollisionBorderSize(){return 0;}
    }
    public static class World extends WorldClient {
        Entity target;double lastRayX;
        public World(){super(null,null,0,null,null);}
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from,Vec3 to,boolean a,boolean b,boolean c){lastRayX=from.xCoord;return null;}
        @Override public List<Entity> getEntitiesInAABBexcluding(Entity viewer,AxisAlignedBB area,com.google.common.base.Predicate<? super Entity> filter) {
            return target.getEntityBoundingBox().intersectsWith(area)?Collections.singletonList(target):Collections.emptyList();
        }
    }
    @Test public void movementReleasedImmediatelyBeforeAttackRevalidatesTheAcceptedEyePosition() throws Exception {
        Minecraft mc=bare(Minecraft.class);Viewer viewer=bare(Viewer.class);World world=bare(World.class);Target target=bare(Target.class);
        viewer.posY=64;viewer.worldObj=world;viewer.setEntityBoundingBox(new AxisAlignedBB(-.3,64,-.3,.3,65.8,.3));
        target.worldObj=world;target.setEntityId(9);target.setEntityBoundingBox(new AxisAlignedBB(-.3,65,2,.3,66.8,2.6));
        world.target=target;mc.thePlayer=viewer;mc.theWorld=world;
        NetworkManager network=new NetworkManager(EnumPacketDirection.CLIENTBOUND);EmbeddedChannel channel=new EmbeddedChannel(network);
        NetHandlerPlayClient play=new NetHandlerPlayClient(null,null,network,null);
        Field client=NetHandlerPlayClient.class.getDeclaredField("gameController");client.setAccessible(true);client.set(play,mc);
        Field queue=EntityPlayerSP.class.getDeclaredField("sendQueue");queue.setAccessible(true);queue.set(viewer,play);
        network.setNetHandler(play);network.configureClientPackets(new PacketFlowTest.MainThread());RaycastService rays=new RaycastService(mc);
        try {
            network.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(0,64,0,0,0,true));while(channel.readOutbound()!=null){}
            assertTrue(rays.canHit(target,0,0,4.5f));
            network.getClientPackets().delays().request("lagrange",200,(p,flow)->flow==EventFlow.OUTBOUND && p instanceof C03PacketPlayer);
            assertNull(network.sendPacketTracked(new C03PacketPlayer.C04PacketPlayerPosition(10,64,0,true),false));
            assertTrue(rays.canHit(target,0,0,4.5f));
            assertNull(network.sendPacketGuarded(new C02PacketUseEntity(target,C02PacketUseEntity.Action.ATTACK),()->rays.canHit(target,0,0,4.5f)));
            assertEquals(10,viewer.getLastReportedPosition().xCoord,0);assertEquals(0,viewer.posX,0);assertEquals(10,world.lastRayX,0);
            assertTrue(channel.readOutbound() instanceof C03PacketPlayer);assertNull(channel.readOutbound());assertFalse(rays.canHit(target,0,0,4.5f));
        } finally {channel.finish();}
    }
}
