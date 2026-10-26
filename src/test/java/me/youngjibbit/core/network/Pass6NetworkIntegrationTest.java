package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatTargetTracker;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.*;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class Pass6NetworkIntegrationTest {
    public static class Animal extends EntityCow {
        Animal(){super(null);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean isInvisible(){return false;}
    }
    @Test public void historicalCombatAndExplicitNearestRemainDifferentFromAcceptedAttackSelection() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            Backtrack back=new Backtrack(()->0,f.client);back.getBooleanSetting("Diagnostics").setValue(true);back.setToggled(true);
            try {
                ExploitHookFixture.Enemy far=f.enemy(7,4),near=f.enemy(8,2.8);f.cursor(near);CombatTargetTracker.update(far,"aura");
                back.getModeSetting("Target Source").setValue("Combat / Nearest");Pulsar.BUS.post(new TickEvent());assertEquals(7,back.getDiagnostics().targetId);assertEquals("combat",back.getDiagnostics().source);
                assertTrue(f.controller.tryAttackEntity(f.player,near,()->true));assertEquals(7,back.getDiagnostics().targetId);
                back.getModeSetting("Target Source").setValue("Nearest");Pulsar.BUS.post(new TickEvent());assertEquals(8,back.getDiagnostics().targetId);
                back.getModeSetting("Target Source").setValue("Attack / Cursor");assertTrue(f.controller.tryAttackEntity(f.player,far,()->true));assertEquals(7,back.getDiagnostics().targetId);assertEquals("attack",back.getDiagnostics().source);
            }finally{back.setToggled(false);}
        }
    }
    @Test public void fixedDelayUsesReceiptsFromBeforeAcquisitionWithoutASecondShadowOrRandomDraw() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            Backtrack back=new Backtrack(()->0,f.client,(min,max)->{throw new AssertionError("Fixed mode must not draw another delay");});
            back.getModeSetting("Delay Mode").setValue("Fixed");back.getNumberSetting("Delay").setValue(125D);back.getBooleanSetting("Diagnostics").setValue(true);back.setToggled(true);
            Object other=new Object();
            try {
                ExploitHookFixture.Enemy enemy=f.enemy(7,3);f.pipe().delays().request(other,500,(packet,flow)->true);
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)16,(byte)0,(byte)0,true));f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)16,(byte)0,(byte)0,true));
                f.cursor(enemy);Pulsar.BUS.post(new TickEvent());f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)16,(byte)0,(byte)0,true));
                assertEquals(96,enemy.serverPosX);assertEquals(4.5,f.pipe().receivedPosition(enemy).xCoord,0);assertEquals(1,back.getDiagnostics().queue.heldByOwner);
                back.setToggled(false);assertEquals(3,f.pipe().delays().size());f.pipe().delays().release(other);assertEquals(144,enemy.serverPosX);assertEquals(4.5,f.pipe().receivedPosition(enemy).xCoord,0);
            }finally{back.setToggled(false);}
        }
    }
    @Test public void optedInLivingTargetsUseAcceptedAttacksAndExactEntityIdentity() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong now=new AtomicLong();Backtrack back=new Backtrack(now::get,f.client);back.getBooleanSetting("Diagnostics").setValue(true);
            back.getModeSetting("Delay Mode").setValue("Fixed");back.getNumberSetting("Delay").setValue(500D);back.setToggled(true);
            try {
                Animal animal=AttackHookTest.bare(Animal.class);animal.worldObj=f.world;animal.setEntityId(9);animal.posX=3;animal.serverPosX=96;animal.hurtTime=4;animal.setEntityBoundingBox(new AxisAlignedBB(2.7,0,-.3,3.3,1.8,.3));f.world.entities.put(9,animal);f.world.loadedEntityList.add(animal);f.cursor(animal);
                Pulsar.BUS.post(new TickEvent());assertEquals(-1,back.getDiagnostics().targetId);
                back.getBooleanSetting("Animals").setValue(true);assertTrue(f.controller.tryAttackEntity(f.player,animal,()->true));assertEquals(9,back.getDiagnostics().targetId);assertEquals("attack",back.getDiagnostics().source);
                f.receive(new S14PacketEntity.S15PacketEntityRelMove(9,(byte)16,(byte)0,(byte)0,true));assertEquals(96,animal.serverPosX);assertEquals(3.5,f.pipe().receivedPosition(animal).xCoord,0);
                Animal replacement=AttackHookTest.bare(Animal.class);replacement.worldObj=f.world;replacement.setEntityId(9);replacement.serverPosX=32;replacement.setEntityBoundingBox(new AxisAlignedBB(0,0,0,1,1,1));f.world.entities.put(9,replacement);f.cursor(null);
                Pulsar.BUS.post(new TickEvent());assertEquals(-1,back.getDiagnostics().targetId);assertEquals(32,replacement.serverPosX);assertNull(f.pipe().receivedPosition(animal));
            }finally{back.setToggled(false);}
        }
    }
}
