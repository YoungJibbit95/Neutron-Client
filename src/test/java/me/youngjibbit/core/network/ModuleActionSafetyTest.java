package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.zero.alpine.event.EventPhase;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.util.FoodStats;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** Restrictive lifecycle tests. No packet queues, timing windows or predicted-hit behavior are created. */
public class ModuleActionSafetyTest {
    @Test public void defaultsAreOffAndValuesAreAppendedForTheThreeConsumers()throws Exception { run("defaultsAreOffAndValuesAreAppendedForTheThreeConsumers"); }
    @Test public void observedHurtStopsBeforeWorkAndCannotAutomaticallyRestart()throws Exception { run("observedHurtStopsBeforeWorkAndCannotAutomaticallyRestart"); }
    @Test public void realHealthPacketBeforeFirstPollUsesTheEnableBaseline()throws Exception { run("realHealthPacketBeforeFirstPollUsesTheEnableBaseline"); }
    @Test public void enablingDamageStopDuringOperationPrimesHealthBeforeTheFirstPacket()throws Exception { run("enablingDamageStopDuringOperationPrimesHealthBeforeTheFirstPacket"); }
    @Test public void healingUpdatesTheBaselineAndALaterDecreaseStops()throws Exception { run("healingUpdatesTheBaselineAndALaterDecreaseStops"); }
    @Test public void worldAndConnectionEpochChangesCannotReuseOldHealthEvidence()throws Exception { run("worldAndConnectionEpochChangesCannotReuseOldHealthEvidence"); }
    @Test public void packetEvidenceMustBeInboundPostOnTheCurrentConnection()throws Exception { run("packetEvidenceMustBeInboundPostOnTheCurrentConnection"); }
    @Test public void newerExclusiveModuleStopsAndLeavesOlderWorkAlone()throws Exception { run("newerExclusiveModuleStopsAndLeavesOlderWorkAlone"); }
    @Test public void enablingExclusiveOnTheOlderModuleStopsTheYoungerBeforeWork()throws Exception { run("enablingExclusiveOnTheOlderModuleStopsTheYoungerBeforeWork"); }
    @Test public void reactivationReceivesANewOrdinalAndCannotReclaimOldPriority()throws Exception { run("reactivationReceivesANewOrdinalAndCannotReclaimOldPriority"); }
    @Test public void exclusivityDoesNotCrossClientsAndWorkerCallsCannotAct()throws Exception { run("exclusivityDoesNotCrossClientsAndWorkerCallsCannotAct"); }
    @Test public void nestedCleanupEventsCannotRepeatTheStopOrExecuteWork()throws Exception { run("nestedCleanupEventsCannotRepeatTheStopOrExecuteWork"); }
    @Test public void actualConsumerPreflightsDisableOnDamageAndDiscardActivity()throws Exception { run("actualConsumerPreflightsDisableOnDamageAndDiscardActivity"); }
    @Test public void partialConfigResetRestoresBothOptInDefaults()throws Exception { run("partialConfigResetRestoresBothOptInDefaults"); }

    private static void run(String method)throws Exception { Wave2IsolatedCase.run(Checks.class,method); }

    @ModuleInfo(name="Action Safety Fixture",category=Category.Player)
    public static final class Guarded extends Module {
        final CheckBoxValue existing=new CheckBoxValue("Existing",this,true);
        int enables,disables,actions;
        Runnable duringDisable;
        Guarded(Minecraft client) { enableActionSafety(()->client); }
        boolean attempt() { if(!actionSafetyAllows())return false;actions++;return true; }
        @Override public void onEnable() { enables++;super.onEnable(); }
        @Override public void onDisable() {
            disables++;
            if(duringDisable!=null)duringDisable.run();
            super.onDisable();
        }
        @Subscribe private final Listener<EventUpdate> work=new Listener<>(event->attempt());
    }

    public static class Checks {
        private static CheckBoxValue damage(Module module) { return module.getBooleanSetting("Stop on Damage"); }
        private static CheckBoxValue exclusive(Module module) { return module.getBooleanSetting("Exclusive Timing"); }

        /** Opt-in only: other ExploitHookFixture users retain their original bare-player contract. */
        private static void health(ExploitHookFixture f,float amount)throws Exception {
            // Each case has its own JVM; DataWatcher's biome dependency needs native registries first.
            Bootstrap.register();
            DataWatcher data=f.player.getDataWatcher();
            if(data==null) {
                data=new DataWatcher(f.player);data.addObject(6,amount);
                Field field=Entity.class.getDeclaredField("dataWatcher");field.setAccessible(true);field.set(f.player,data);
            } else data.updateObject(6,amount);
        }

        /** Initialize only the native S06 dependencies used by the two actual-application cases. */
        private static void nativeHealth(ExploitHookFixture f)throws Exception {
            health(f,20);
            f.player.getAttributeMap().registerAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20);
            Field food=EntityPlayer.class.getDeclaredField("foodStats");food.setAccessible(true);food.set(f.player,new FoodStats());
        }

        private static void packet(PacketPipeline pipe,EventFlow flow,EventPhase phase) {
            EventPacket event=new EventPacket(new S06PacketUpdateHealth(12,20,5),pipe);
            event.setEventFlow(flow);event.setEventPhase(phase);Pulsar.BUS.post(event);
        }

        @Test public void defaultsAreOffAndValuesAreAppendedForTheThreeConsumers()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                Guarded module=new Guarded(f.client);
                assertSame(module.existing,module.getValues().get(0));
                assertEquals("stop_on_damage",module.getValues().get(1).getId());
                assertEquals("exclusive_timing",module.getValues().get(2).getId());
                assertFalse(damage(module).getValue());assertFalse(exclusive(module).getValue());
                module.setToggled(true);
                try {
                    // No DataWatcher is initialized: OFF must not inspect or require health.
                    f.player.hurtTime=10;f.player.isDead=true;
                    assertTrue(module.attempt());assertTrue(module.isToggled());assertEquals(0,module.disables);
                } finally { module.setToggled(false); }
                for(Module consumer:Arrays.asList(new Backtrack(()->0L,f.client),new LagRange(()->0L,null),new Tickbase(()->0L,f.client))) {
                    int size=consumer.getValues().size();
                    assertEquals("stop_on_damage",consumer.getValues().get(size-2).getId());
                    assertEquals("exclusive_timing",consumer.getValues().get(size-1).getId());
                    assertFalse(damage(consumer).getValue());assertFalse(exclusive(consumer).getValue());
                }
            }
        }

        @Test public void observedHurtStopsBeforeWorkAndCannotAutomaticallyRestart()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                health(f,20);Guarded module=new Guarded(f.client);damage(module).setValue(true);module.setToggled(true);
                try {
                    assertTrue(module.attempt());f.player.hurtTime=1;
                    Pulsar.BUS.post(new EventUpdate());
                    assertFalse(module.isToggled());assertEquals(1,module.disables);assertEquals(1,module.actions);
                    f.player.hurtTime=0;Pulsar.BUS.post(new TickEvent());Pulsar.BUS.post(new EventUpdate());
                    assertFalse(module.attempt());assertEquals(1,module.enables);assertEquals(1,module.disables);
                } finally { module.setToggled(false); }
            }
        }

        @Test public void realHealthPacketBeforeFirstPollUsesTheEnableBaseline()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                nativeHealth(f);Guarded module=new Guarded(f.client);damage(module).setValue(true);module.setToggled(true);
                try {
                    f.receive(new S06PacketUpdateHealth(12,20,5));f.connection.main.drain();
                    assertEquals(12,f.player.getHealth(),0);
                    assertEquals("first native health assignment has no hurt animation",0,f.player.hurtTime);
                    assertFalse(module.isToggled());assertEquals(1,module.disables);assertEquals(0,module.actions);
                } finally { module.setToggled(false); }
            }
        }

        @Test public void enablingDamageStopDuringOperationPrimesHealthBeforeTheFirstPacket()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                nativeHealth(f);Guarded module=new Guarded(f.client);module.setToggled(true);
                try {
                    assertTrue(module.attempt());
                    damage(module).setValue(true);
                    f.receive(new S06PacketUpdateHealth(12,20,5));f.connection.main.drain();
                    assertEquals(12,f.player.getHealth(),0);assertEquals(0,f.player.hurtTime);
                    assertFalse("OFF -> ON must capture health before the next packet",module.isToggled());
                    assertEquals(1,module.disables);assertEquals(1,module.actions);
                } finally { module.setToggled(false); }
            }
        }

        @Test public void healingUpdatesTheBaselineAndALaterDecreaseStops()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                health(f,10);Guarded module=new Guarded(f.client);damage(module).setValue(true);module.setToggled(true);
                try {
                    health(f,15);assertTrue(module.attempt());assertTrue(module.isToggled());
                    health(f,14);assertFalse(module.attempt());assertEquals(1,module.disables);assertEquals(1,module.actions);
                } finally { module.setToggled(false); }
            }
        }

        @Test public void worldAndConnectionEpochChangesCannotReuseOldHealthEvidence()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                for(boolean connectionOnly:new boolean[]{false,true}) {
                    health(f,20);Guarded module=new Guarded(f.client);damage(module).setValue(true);module.setToggled(true);
                    try {
                        if(connectionOnly)f.pipe().worldChanged();
                        else {
                            ExploitHookFixture.World other=NetworkTestSupport.bare(ExploitHookFixture.World.class);
                            f.client.theWorld=other;f.player.worldObj=other;
                        }
                        health(f,12);assertTrue("new scene establishes its own health baseline",module.attempt());
                        health(f,11);assertFalse(module.attempt());assertEquals(1,module.disables);
                    } finally {
                        module.setToggled(false);f.client.theWorld=f.world;f.player.worldObj=f.world;
                    }
                }
            }
        }

        @Test public void packetEvidenceMustBeInboundPostOnTheCurrentConnection()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture();NetworkTestSupport.Fixture foreign=new NetworkTestSupport.Fixture()) {
                health(f,20);Guarded module=new Guarded(f.client);damage(module).setValue(true);module.setToggled(true);
                try {
                    health(f,12);
                    packet(f.pipe(),EventFlow.INBOUND,EventPhase.PRE);assertTrue(module.isToggled());
                    packet(f.pipe(),EventFlow.OUTBOUND,EventPhase.POST);assertTrue(module.isToggled());
                    packet(foreign.network.getClientPackets(),EventFlow.INBOUND,EventPhase.POST);assertTrue(module.isToggled());
                    packet(f.pipe(),EventFlow.INBOUND,EventPhase.POST);assertFalse(module.isToggled());
                    assertEquals(1,module.disables);assertEquals(0,module.actions);
                } finally { module.setToggled(false); }
            }
        }

        @Test public void newerExclusiveModuleStopsAndLeavesOlderWorkAlone()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                Guarded older=new Guarded(f.client),newer=new Guarded(f.client);
                exclusive(newer).setValue(true);older.setToggled(true);newer.setToggled(true);
                try {
                    assertFalse(newer.attempt());assertFalse(newer.isToggled());assertEquals(1,newer.disables);
                    assertTrue(older.attempt());assertTrue(older.isToggled());assertEquals(0,older.disables);
                } finally {newer.setToggled(false);older.setToggled(false);}
            }
        }

        @Test public void enablingExclusiveOnTheOlderModuleStopsTheYoungerBeforeWork()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                Guarded older=new Guarded(f.client),newer=new Guarded(f.client);
                older.setToggled(true);newer.setToggled(true);
                try {
                    assertTrue(older.attempt());assertTrue(newer.attempt());
                    exclusive(older).setValue(true);Pulsar.BUS.post(new EventUpdate());
                    assertTrue(older.isToggled());assertEquals(2,older.actions);
                    assertFalse(newer.isToggled());assertEquals(1,newer.actions);assertEquals(1,newer.disables);
                } finally {newer.setToggled(false);older.setToggled(false);}
            }
        }

        @Test public void reactivationReceivesANewOrdinalAndCannotReclaimOldPriority()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                Guarded first=new Guarded(f.client),second=new Guarded(f.client);
                first.setToggled(true);second.setToggled(true);
                try {
                    first.setToggled(false);exclusive(first).setValue(true);first.setToggled(true);
                    assertFalse(first.attempt());assertTrue(second.attempt());
                    assertEquals(2,first.enables);assertEquals(2,first.disables);assertEquals(0,second.disables);
                } finally {first.setToggled(false);second.setToggled(false);}
            }
        }

        @Test public void exclusivityDoesNotCrossClientsAndWorkerCallsCannotAct()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                NetworkTestSupport.Client other=NetworkTestSupport.bare(NetworkTestSupport.Client.class);other.main=f.client.main;
                Guarded first=new Guarded(f.client),second=new Guarded(other);
                exclusive(first).setValue(true);exclusive(second).setValue(true);
                first.setToggled(true);second.setToggled(true);
                try {
                    assertTrue(first.attempt());assertTrue(second.attempt());
                    AtomicBoolean workerAllowed=new AtomicBoolean(true);
                    f.connection.netty(()->workerAllowed.set(first.attempt()));
                    assertFalse(workerAllowed.get());assertTrue(first.isToggled());assertEquals(1,first.actions);
                } finally {second.setToggled(false);first.setToggled(false);}
            }
        }

        @Test public void nestedCleanupEventsCannotRepeatTheStopOrExecuteWork()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                health(f,20);Guarded module=new Guarded(f.client);damage(module).setValue(true);module.setToggled(true);
                module.duringDisable=()->{
                    Pulsar.BUS.post(new TickEvent());Pulsar.BUS.post(new EventUpdate());
                    assertFalse(module.attempt());
                };
                try {
                    f.player.hurtTime=1;assertFalse(module.attempt());
                    assertFalse(module.attempt());Pulsar.BUS.post(new EventUpdate());
                    assertEquals(1,module.disables);assertEquals(0,module.actions);assertFalse(module.isToggled());
                } finally {module.setToggled(false);}
            }
        }

        @Test public void actualConsumerPreflightsDisableOnDamageAndDiscardActivity()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                for(Module module:Arrays.asList(new Backtrack(()->0L,f.client),new LagRange(()->0L,null),new Tickbase(()->0L,f.client))) {
                    health(f,20);f.player.hurtTime=0;damage(module).setValue(true);module.setToggled(true);
                    try {
                        f.player.hurtTime=1;
                        String name=module instanceof Backtrack?"prepareContext":module instanceof LagRange?"context":"usable";
                        Method method=module.getClass().getDeclaredMethod(name);method.setAccessible(true);
                        assertEquals(Boolean.FALSE,method.invoke(module));assertFalse(module.getName(),module.isToggled());
                        Method snapshot=module.getClass().getMethod("getActivitySnapshot");
                        assertNull("stopped consumer must not retain activity",snapshot.invoke(module));
                        assertFalse("ordinary shutdown cannot retain timing ownership",f.pipe().combatTiming().observesOwner(module));
                    } finally {module.setToggled(false);f.player.hurtTime=0;}
                }
            }
        }

        @Test public void partialConfigResetRestoresBothOptInDefaults()throws Exception {
            try(ExploitHookFixture f=new ExploitHookFixture()) {
                Guarded module=new Guarded(f.client);damage(module).setValue(true);exclusive(module).setValue(true);
                module.beginConfigUpdate();module.endConfigUpdate();
                assertFalse(damage(module).getValue());assertFalse(exclusive(module).getValue());
            }
        }
    }
}
