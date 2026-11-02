package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.module.impl.ghost.AutoClicker2;
import me.youngjibbit.module.impl.ghost.WTap2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;

/** Real inbound velocity, living input, native acceleration/friction/gravity and Aura transport.
 * Collision is a synthetic flat floor; no server acceptance or anti-cheat claim. */
@RunWith(Parameterized.class)
public class CombatFollowupTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"retainedImpulse"},{"packetReplacementAndAddition"},
                {"nativeAttackConditions"},{"cancelledAttackDoesNotTap"},{"acceptedAttackConsumesInputOnce"},
                {"clickModesRespectBounds"},{"nativeRejectedTarget"},{"nativeEnchantedHit"},{"targetLossRetainsImpulse"}});
    }
    private final String method;
    public CombatFollowupTest(String method) { this.method=method; }
    @Test public void freshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class,method); }
    public static class PhysicsPlayer extends AuraMovementConsistencyTest.Player {
        @Override public boolean isInWater() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean isOnLadder() { return false; }
        @Override public void playSound(String name,float volume,float pitch) { }
        @Override public float getAIMoveSpeed() { return isSprinting()?.13f:.1f; }
        @Override public void moveEntity(double x,double y,double z) {
            double nextY=posY+y;onGround=nextY<=64;
            setPosition(posX+x,Math.max(64,nextY),posZ+z);
            if(onGround && y<0)motionY=0;
        }
    }
    public static class RejectingRemote extends AuraMovementConsistencyTest.Remote {
        @Override public boolean attackEntityFrom(net.minecraft.util.DamageSource source,float amount) { return false; }
    }
    static void prepareNetwork(AuraMovementConsistencyTest.Run r) throws Exception {
        ScaffoldOwnerContinuityTest.field(Minecraft.class,"mcThread").set(r.f.run.fixture.mc,Thread.currentThread());
        ScaffoldOwnerContinuityTest.field(NetHandlerPlayClient.class,"clientWorldController").set(r.player.sendQueue,r.f.run.scene);
        r.f.run.scene.loadedEntityList.add(r.player);
        ScaffoldOwnerContinuityTest.field(World.class,"worldAccesses").set(r.f.run.scene,new ArrayList<>());
        ScaffoldOwnerContinuityTest.field(World.class,"rand").set(r.f.run.scene,new Random(17));
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)
            r.f.run.scene.blocks.put(new BlockPos(x,63,z),Blocks.stone.getDefaultState());
    }
    static void velocity(AuraMovementConsistencyTest.Run r,double x,double y,double z) {
        r.f.run.fixture.channel.writeInbound(new S12PacketEntityVelocity(r.player.getEntityId(),x,y,z));
        assertEquals(x,r.player.motionX,1.3e-4);assertEquals(y,r.player.motionY,1.3e-4);assertEquals(z,r.player.motionZ,1.3e-4);
    }
    static void impulse(Entity p,double x,double y,double z) {
        assertEquals(x,p.motionX,1e-6);assertEquals(y,p.motionY,1e-6);assertEquals(z,p.motionZ,1e-6);
    }
    static WTap2 tap(AuraMovementConsistencyTest.Run r) {
        r.f.run.fixture.mc.gameSettings.keyBindForward=new KeyBinding("synthetic.forward",17,"synthetic");
        r.f.run.fixture.mc.gameSettings.keyBindForward.pressed=true;
        r.f.run.fixture.mc.gameSettings.keyBindSprint.pressed=true;
        WTap2 tap=new WTap2();tap.getNumberSetting("Cooldown (ms)").setValue(428D);tap.setToggled(true);return tap;
    }
    static void detach(WTap2 tap) throws Exception {
        Pulsar.BUS.unsubscribe(tap);
        ScaffoldOwnerContinuityTest.field(me.youngjibbit.module.Module.class,"toggled").set(tap,false);
    }
    public static class Checks {
        @Test public void cancelledAttackDoesNotTap() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();WTap2 tap=tap(r);r.player.sprint=true;
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{if(e.isPre() && e.isOutbound() && e.getPacket() instanceof C02PacketUseEntity)e.cancel();};
                Pulsar.BUS.subscribe(hooks);
                try {
                    assertEquals(0,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                    assertTrue("cancelled C02 must not mutate the physical forward key",r.f.run.fixture.mc.gameSettings.keyBindForward.pressed);
                    assertTrue(r.f.run.fixture.mc.gameSettings.keyBindSprint.pressed);
                    assertTrue(r.player.sprint);
                } finally { Pulsar.BUS.unsubscribe(hooks);detach(tap); }
            }
        }
        @Test public void acceptedAttackConsumesInputOnce() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();WTap2 tap=tap(r);r.player.sprint=true;
                try {
                    assertEquals(1,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                    r.physical(false);
                    assertEquals("one next living input sample must release forward",0,MovementInput.moveForward,0);
                    assertFalse("sprint cannot re-enter the tapped physical step",r.physicsSprint);
                    r.physical(false);
                    assertEquals("raw forward resumes on the following sample",1,MovementInput.moveForward,0);
                } finally { detach(tap); }
            }
        }
        @Test public void clickModesRespectBounds() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                AutoClicker2 clicker=new AutoClicker2();
                clicker.getNumberSetting("Min CPS").setValue(9D);clicker.getNumberSetting("Max CPS").setValue(12D);
                Method sample=AutoClicker2.class.getDeclaredMethod("resolveCps");sample.setAccessible(true);
                for(String mode:new String[]{"Test","Smoothstep","Sin & Cos","Basic","Next","Secure","Pi","Perlin"}) {
                    clicker.getModeSetting("Randomization Mode").setValue(mode);
                    for(int i=0;i<200;i++) {
                        double value=(Double)sample.invoke(clicker);
                        assertTrue(mode+" produced out-of-range CPS: "+value,Double.isFinite(value)&&value>=9&&value<=12);
                    }
                }
            }
        }
        @Test public void nativeAttackConditions() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();prepareNetwork(r);
                for(boolean sprint:new boolean[]{false,true}) {
                    velocity(r,.4,.3,-.2);r.player.sprint=sprint;
                    assertEquals(1,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                    impulse(r.player,.4*(sprint?.6:1),.3,-.2*(sprint?.6:1));
                    velocity(r,-.2,.2,.4); // A later S12 replaces, it is not scaled by the earlier attack.
                    impulse(r.player,-.2,.2,.4);
                }
            }
        }
        @Test public void nativeRejectedTarget() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget(RejectingRemote.class);prepareNetwork(r);velocity(r,.4,.3,-.2);r.player.sprint=true;
                assertEquals(1,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                impulse(r.player,.4,.3,-.2);assertTrue("C02 acceptance alone cannot impose native slowdown",r.player.sprint);
            }
        }
        @Test public void nativeEnchantedHit() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();prepareNetwork(r);velocity(r,.4,.3,-.2);r.player.sprint=false;
                ScaffoldOwnerContinuityTest.field(Entity.class,"rand").set(r.player,new Random(7));
                net.minecraft.item.ItemStack sword=new net.minecraft.item.ItemStack(net.minecraft.init.Items.diamond_sword);
                sword.addEnchantment(net.minecraft.enchantment.Enchantment.knockback,1);
                r.player.inventory.mainInventory[r.player.inventory.currentItem]=sword;
                assertEquals(1,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                impulse(r.player,.24,.3,-.12);
            }
        }
        @Test public void targetLossRetainsImpulse() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();prepareNetwork(r);r.player.rotationYaw=90;
                r.f.run.fixture.aura.getBooleanSetting("Smooth Rotation Stop").setValue(true);
                assertEquals(1,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                velocity(r,.24,.3,-.16);r.f.run.scene.getEntityByID(19).isDead=true;
                Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
                impulse(r.player,.24,.3,-.16);
                assertEquals(0,r.f.walk().stream().filter(C02PacketUseEntity.class::isInstance).count());
                assertEquals(me.youngjibbit.core.combat.RotationManager.AURA_RELEASE_SOURCE,r.f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                impulse(r.player,.24,.3,-.16);
            }
        }
        @Test public void packetReplacementAndAddition() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                prepareNetwork(r);velocity(r,.4,.3,-.2);
                r.f.run.fixture.channel.writeInbound(new S27PacketExplosion(0,64,0,0,Collections.emptyList(),new Vec3(.1,.2,.3)));
                impulse(r.player,.5,.5,.1);
                velocity(r,-.2,.1,.4);
                r.f.run.fixture.aura.setToggled(false);impulse(r.player,-.2,.1,.4);
            }
        }
        @Test public void retainedImpulse() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict",PhysicsPlayer.class)) {
                prepareNetwork(r);r.player.jumpMovementFactor=.02f;
                for(String mode:new String[]{"Einfach","Präzise","Rotationsgebunden","Legacy Rounded","Legacy Strict"})
                    for(boolean air:new boolean[]{false,true})for(int[] keys:new int[][]{{0,1},{1,0},{1,1},{-1,-1}}) {
                        r.f.run.fixture.aura.getModeSetting("movefix").setValue(mode);
                        r.player.setPosition(.5,air?65:64,.5);r.player.onGround=!air;
                        r.input.strafe=keys[0];r.input.forward=keys[1];r.target(-.5,2);
                        velocity(r,.24,air?.15:0,-.16);
                        for(int step=0;step<3;step++) {
                            double oldX=r.player.motionX,oldY=r.player.motionY,oldZ=r.player.motionZ;
                            r.f.scheduler.beginTick();r.f.clock.addAndGet(50);r.f.run.scene.motionTime.addAndGet(50_000_000L);
                            me.youngjibbit.core.observation.RuntimeObservation observation=r.f.run.fixture.runtime.getObservation();
                            observation.beginOuterTick();
                            try { observation.observePlayerStep(r.player,()->{
                                try { r.player.onLivingUpdate();fail(); }catch(AuraMovementConsistencyTest.BeforePhysics expected) { }
                                impulse(r.player,oldX,oldY,oldZ);
                                boolean grounded=r.player.onGround;
                                r.player.moveEntityWithHeading(MovementInput.moveStrafe*.98f,MovementInput.moveForward*.98f);
                                assertNotNull(r.move);
                                float friction=grounded?.6f*.91f:.91f;
                                float scale=r.move.getFriction()/Math.max(1,net.minecraft.util.MathHelper.sqrt_float(
                                        r.move.getStrafe()*r.move.getStrafe()+r.move.getForward()*r.move.getForward()));
                                float sin=net.minecraft.util.MathHelper.sin(r.move.getYaw()*(float)Math.PI/180),cos=net.minecraft.util.MathHelper.cos(r.move.getYaw()*(float)Math.PI/180);
                                assertEquals((oldX+(r.move.getStrafe()*cos-r.move.getForward()*sin)*scale)*friction,r.player.motionX,1e-7);
                                assertEquals((oldZ+(r.move.getForward()*cos+r.move.getStrafe()*sin)*scale)*friction,r.player.motionZ,1e-7);
                                assertEquals(((r.player.onGround&&oldY<0?0:oldY)-.08)*.9800000190734863,r.player.motionY,1e-7);
                                r.player.onUpdateWalkingPlayer();
                                assertEquals(r.move.getYaw(),r.f.run.fixture.runtime.getRotationManager().getEffectiveYaw(),.001);
                                return true;
                            },s->s.getAsBoolean()); }finally { observation.endOuterTick(); }
                            r.f.run.drain();
                        }
                    }
            }
        }
    }
}
