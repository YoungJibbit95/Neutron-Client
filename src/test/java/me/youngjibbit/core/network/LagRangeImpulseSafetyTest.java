package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Actual inbound S12 application and earned-step scheduler; no server or input window. */
public class LagRangeImpulseSafetyTest {
    private void velocity(ExploitHookFixture f) {
        f.receive(new S12PacketEntityVelocity(f.player.getEntityId(),.3,.2,-.1));
    }
    @Test public void appliedVelocityBeforeHurtStatusMustRunOrdinaryPhysics() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            f.world.entities.put(1,f.player);f.player.ticksExisted=120;f.enemy(7,3);
            AtomicLong now=new AtomicLong();AtomicInteger bodies=new AtomicInteger();
            LagRange lag=new LagRange(now::get,(min,max)->min);lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());LagRange.beginFrame(f.client,false,1,50);
                velocity(f);
                assertEquals(0,f.player.hurtTime);assertEquals(.3,f.player.motionX,1e-9);
                assertEquals(.2,f.player.motionY,1e-9);assertEquals(-.1,f.player.motionZ,1e-9);
                LagRange.runPlayerStep(f.player,()->{bodies.incrementAndGet();return true;});
                assertEquals("applied velocity must not wait for a separate hurt status",1,bodies.get());
                now.set(499_000_000L);LagRange.beginFrame(f.client,false,1,50);
                LagRange.runPlayerStep(f.player,()->{bodies.incrementAndGet();return true;});
                assertEquals(2,bodies.get());
                now.set(500_000_000L);LagRange.beginFrame(f.client,false,1,50);
                LagRange.runPlayerStep(f.player,()->{bodies.incrementAndGet();return true;});
                assertEquals("bounded pause ends when healthy and no hurt status remains",2,bodies.get());
            } finally {lag.setToggled(false);}
        }
    }
    @Test public void appliedVelocityReleasesOutgoingWindow() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            f.world.entities.put(1,f.player);f.enemy(7,6);
            AtomicLong now=new AtomicLong();BacktrackLagRangeControlTest.clock(f,now);
            LagRange lag=new LagRange(now::get,(min,max)->min);lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());
                C03PacketPlayer before=new C03PacketPlayer(true);f.connection.network.sendPacket(before);
                assertNull(f.connection.channel.readOutbound());assertTrue(lag.getInfo().endsWith("Q1"));
                velocity(f);
                assertSame("pre-impulse movement is released in order",before,f.connection.channel.readOutbound());
                assertEquals(0,f.pipe().delays().size());
                C03PacketPlayer after=new C03PacketPlayer(true);f.connection.network.sendPacket(after);
                assertSame("new movement is not held during impulse recovery",after,f.connection.channel.readOutbound());
                Pulsar.BUS.post(new TickEvent());assertTrue(lag.getStatus(),lag.getStatus().contains("knockback"));
            } finally {lag.setToggled(false);}
        }
    }
    @Test public void appliedExplosionPausesButCancelledOrZeroImpulseDoesNot() throws Exception {
        for(int scenario=0;scenario<3;scenario++)try(ExploitHookFixture f=new ExploitHookFixture()) {
            f.player.ticksExisted=120;f.enemy(7,3);
            java.lang.reflect.Field rand=net.minecraft.world.World.class.getDeclaredField("rand");rand.setAccessible(true);rand.set(f.world,new java.util.Random(7));
            java.lang.reflect.Field accesses=net.minecraft.world.World.class.getDeclaredField("worldAccesses");accesses.setAccessible(true);accesses.set(f.world,new java.util.ArrayList<>());
            final int selected=scenario;AtomicInteger bodies=new AtomicInteger();
            PacketFlowTest.Observer packets=new PacketFlowTest.Observer();
            packets.action=e->{if(selected==1&&e.isInbound()&&e.isPre()&&e.getPacket() instanceof S27PacketExplosion)e.cancel();};
            Pulsar.BUS.subscribe(packets);LagRange lag=new LagRange(()->0,(min,max)->min);lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());LagRange.beginFrame(f.client,false,1,50);
                f.receive(new S27PacketExplosion(0,0,0,0,java.util.Collections.emptyList(),
                        scenario==2?new net.minecraft.util.Vec3(0,0,0):new net.minecraft.util.Vec3(.1,.2,.3)));
                assertEquals(scenario==0?.2:0,f.player.motionY,1e-6);
                LagRange.runPlayerStep(f.player,()->{bodies.incrementAndGet();return true;});
                assertEquals("scenario="+scenario,scenario==0?1:0,bodies.get());
            } finally {lag.setToggled(false);Pulsar.BUS.unsubscribe(packets);}
        }
    }
    @Test public void impulseKeepsUnpublishedLookBarrierUntilMovementIsSent() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            f.world.entities.put(1,f.player);f.player.ticksExisted=120;f.enemy(7,3);
            LagRange lag=new LagRange(()->0,(min,max)->min);lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());LagRange.beginFrame(f.client,false,1,50);
                LagRange.runPlayerStep(f.player,()->{fail("earn withheld body");return true;});
                velocity(f);
                f.client.objectMouseOver=new net.minecraft.util.MovingObjectPosition(new net.minecraft.util.Vec3(1,1,0),
                        net.minecraft.util.EnumFacing.WEST,new net.minecraft.util.BlockPos(1,1,0));
                assertFalse("receiving velocity does not publish our new look",LagRange.allowBlockAttack());
                f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertTrue(LagRange.allowBlockAttack());
            } finally {lag.setToggled(false);}
        }
    }
    @Test public void contextChangeDuringWindowReleaseDoesNotCarryOldImpulsePause() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            f.world.entities.put(1,f.player);f.player.ticksExisted=120;ExploitHookFixture.Enemy enemy=f.enemy(7,6);
            AtomicLong now=new AtomicLong();BacktrackLagRangeControlTest.clock(f,now);
            LagRange lag=new LagRange(now::get,(min,max)->min);lag.setToggled(true);
            PacketFlowTest.Observer packets=new PacketFlowTest.Observer();Pulsar.BUS.subscribe(packets);
            try {
                Pulsar.BUS.post(new TickEvent());f.connection.network.sendPacket(new C03PacketPlayer(true));
                assertEquals(1,f.pipe().delays().size());
                packets.action=e->{if(e.isOutbound()&&e.isPost()&&e.getPacket() instanceof C03PacketPlayer)f.pipe().worldChanged();};
                velocity(f);packets.action=e->{};
                enemy.posX=3;enemy.setEntityBoundingBox(new AxisAlignedBB(2.7,0,-.3,3.3,1.8,.3));
                Pulsar.BUS.post(new TickEvent());LagRange.beginFrame(f.client,false,1,50);
                LagRange.runPlayerStep(f.player,()->{fail("new epoch cannot inherit old impulse pause");return true;});
            } finally {lag.setToggled(false);Pulsar.BUS.unsubscribe(packets);}
        }
    }
    @Test public void rejectedAndForeignVelocityDoNotInventAPause() throws Exception {
        for(int scenario=0;scenario<3;scenario++)try(ExploitHookFixture f=new ExploitHookFixture()) {
            f.world.entities.put(1,f.player);f.player.ticksExisted=120;f.enemy(7,3);
            final int selected=scenario;
            PacketFlowTest.Observer packets=new PacketFlowTest.Observer();
            packets.action=e->{if(selected==0&&e.isInbound()&&e.isPre()&&e.getPacket() instanceof S12PacketEntityVelocity)e.cancel();};
            Listener<KnockbackEvent> cancel=new Listener<>(KnockbackEvent.class,e->{if(selected==1&&e.isPre())e.cancel();});
            Pulsar.BUS.subscribe(packets);Pulsar.BUS.subscribe(cancel);
            LagRange lag=new LagRange(()->0,(min,max)->min);lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());LagRange.beginFrame(f.client,false,1,50);
                if(scenario==2)f.receive(new S12PacketEntityVelocity(7,.3,.2,-.1));else velocity(f);
                assertEquals(0,f.player.motionY,0);
                LagRange.runPlayerStep(f.player,()->{fail("unapplied local impulse must not pause, scenario="+selected);return true;});
            } finally {lag.setToggled(false);Pulsar.BUS.unsubscribe(packets);Pulsar.BUS.unsubscribe(cancel);}
        }
    }
    @Test public void impulseDiscardsEarnedCreditBeforeRecovery() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            f.world.entities.put(1,f.player);f.player.ticksExisted=120;f.player.movementInput=new MovementInput();
            f.player.movementInput.moveForward=1;f.player.rotationYaw=-90;
            ExploitHookFixture.Enemy enemy=f.enemy(7,3);AtomicLong now=new AtomicLong();AtomicInteger extras=new AtomicInteger();
            LagRange lag=new LagRange(now::get,(min,max)->min);lag.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent());
                for(int i=0;i<3;i++){now.set(i*50_000_000L);LagRange.beginFrame(f.client,false,1,50);
                    LagRange.runPlayerStep(f.player,()->{fail("earn real withheld credit first");return true;});}
                velocity(f);enemy.posX=3.5;enemy.hurtTime=0;
                enemy.setEntityBoundingBox(new AxisAlignedBB(3.2,0,-.3,3.8,1.8,.3));
                f.player.lastTickPosX=-.25;f.player.syntheticBody=()->extras.incrementAndGet();
                now.set(600_000_000L);LagRange.beginFrame(f.client,false,1,50);Pulsar.BUS.post(new TickEvent());
                assertEquals("recovery cannot spend pre-impulse credit",0,extras.get());
            } finally {lag.setToggled(false);}
        }
    }
    @Test public void containerAndFocusChangeInsideExtraStepSuppressDodge() throws Exception {
        for(int scenario=0;scenario<4;scenario++)try(ExploitHookFixture f=new ExploitHookFixture()) {
            AtomicLong now=new AtomicLong();AtomicInteger extras=new AtomicInteger();
            f.player.ticksExisted=120;f.player.movementInput=new MovementInput();f.player.movementInput.moveForward=1;
            f.player.rotationYaw=-90;ExploitHookFixture.Enemy enemy=f.enemy(7,3);
            LagRange lag=new LagRange(now::get,(min,max)->min);lag.setToggled(true);
            final int selected=scenario;
            final GuiScreen screen=scenario==0?AttackHookTest.bare(GuiInventory.class)
                    :scenario==1?AttackHookTest.bare(GuiChest.class):null;
            try {
                Pulsar.BUS.post(new TickEvent());
                for(int i=0;i<3;i++){now.set(i*50_000_000L);LagRange.beginFrame(f.client,false,1,50);
                    LagRange.runPlayerStep(f.player,()->{fail("earn credit");return true;});}
                enemy.posX=3.7;enemy.hurtTime=0;enemy.rotationYawHead=enemy.prevRotationYawHead=90;
                enemy.setEntityBoundingBox(new AxisAlignedBB(3.4,0,-.3,4,1.8,.3));f.player.lastTickPosX=-.25;
                f.player.syntheticBody=()->{
                    extras.incrementAndGet();f.client.currentScreen=screen;if(selected==2)f.client.inGameHasFocus=false;
                    MovementInputEvent input=new MovementInputEvent(0,0,false,false);Pulsar.BUS.post(input);
                    assertEquals("scenario="+selected,selected==3?1:0,Math.abs(input.getStrafe()),0);
                    f.player.posX+=.25;
                };
                now.set(150_000_000L);LagRange.beginFrame(f.client,false,1,50);Pulsar.BUS.post(new TickEvent());
                assertEquals("only focused gameplay may continue extra steps",scenario==3?2:1,extras.get());
            } finally {lag.setToggled(false);}
        }
    }
}
