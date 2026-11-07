package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.ghost.WTap2;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C02PacketUseEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.*;

/** Actual Aura/controller/S12 transport. No visible game or server acceptance claim. */
@RunWith(Parameterized.class)
public class STapAcceptanceTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"cancelledAura"},{"replacedAura"},{"acceptedAura"},
                {"cancelledManual"},{"acceptedManual"},{"cancelledAttemptDoesNotSpendCooldown"}});
    }
    private final String method;
    public STapAcceptanceTest(String method){this.method=method;}
    @Test public void freshJvm() throws Exception {Wave2IsolatedCase.run(Checks.class,method);}
    public static class Checks {
        private void verify(int scenario,boolean manual) throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();CombatFollowupTest.prepareNetwork(r);
                if(manual)r.f.run.fixture.aura.setToggled(false);
                WTap2 tap=CombatFollowupTest.tap(r);tap.getModeSetting("Mode").setValue("STap");
                tap.getNumberSetting("Duration (ms)").setValue(74D);
                net.minecraft.client.settings.GameSettings gs=r.f.run.fixture.mc.gameSettings;
                gs.keyBindBack=new KeyBinding("synthetic.back",31,"synthetic");
                r.player.sprint=true;CombatFollowupTest.velocity(r,.4,.3,-.2);
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{if(e.isPre()&&e.isOutbound()&&e.getPacket() instanceof C02PacketUseEntity){
                    if(scenario==0)e.cancel();
                    if(scenario==1)e.setPacket(new C02PacketUseEntity(r.f.run.scene.getEntityByID(19),C02PacketUseEntity.Action.INTERACT));
                }};
                Pulsar.BUS.subscribe(hooks);
                try {
                    long attacks;
                    if(manual){
                        boolean accepted=r.f.run.fixture.mc.playerController.tryAttackEntity(r.player,r.f.run.scene.getEntityByID(19),()->true);
                        assertEquals(scenario==2,accepted);
                        attacks=r.f.run.drain().stream().filter(p->p instanceof C02PacketUseEntity&&((C02PacketUseEntity)p).getAction()==C02PacketUseEntity.Action.ATTACK).count();
                    }else attacks=r.f.walk().stream().filter(p->p instanceof C02PacketUseEntity&&((C02PacketUseEntity)p).getAction()==C02PacketUseEntity.Action.ATTACK).count();
                    assertEquals(scenario==2?1:0,attacks);
                    CombatFollowupTest.impulse(r.player,scenario==2?.24:.4,.3,scenario==2?-.12:-.2);
                    assertEquals(scenario!=2,r.player.sprint);
                    assertEquals("Only an accepted C02 can own backwards input",scenario==2,
                            ScaffoldOwnerContinuityTest.field(WTap2.class,"stapOwned").getBoolean(tap));
                    if(scenario==2)ScaffoldOwnerContinuityTest.field(WTap2.class,"releaseAt").setLong(tap,Long.MAX_VALUE);
                    Pulsar.BUS.post(new TickEvent());
                    assertEquals(scenario!=2,gs.keyBindForward.pressed);assertEquals(scenario==2,gs.keyBindBack.pressed);
                    CombatFollowupTest.impulse(r.player,scenario==2?.24:.4,.3,scenario==2?-.12:-.2);
                }finally{Pulsar.BUS.unsubscribe(hooks);CombatFollowupTest.detach(tap);}
            }
        }
        @Test public void cancelledAura() throws Exception{verify(0,false);}
        @Test public void replacedAura() throws Exception{verify(1,false);}
        @Test public void acceptedAura() throws Exception{verify(2,false);}
        @Test public void cancelledManual() throws Exception{verify(0,true);}
        @Test public void acceptedManual() throws Exception{verify(2,true);}
        @Test public void cancelledAttemptDoesNotSpendCooldown() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();r.f.run.fixture.aura.setToggled(false);
                WTap2 tap=CombatFollowupTest.tap(r);tap.getModeSetting("Mode").setValue("STap");
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{if(e.isPre()&&e.isOutbound()&&e.getPacket() instanceof C02PacketUseEntity)e.cancel();};
                Pulsar.BUS.subscribe(hooks);
                try {
                    assertFalse(r.f.run.fixture.mc.playerController.tryAttackEntity(r.player,r.f.run.scene.getEntityByID(19),()->true));
                    assertEquals("Rejected attacks must not consume STap cooldown",0,ScaffoldOwnerContinuityTest.field(WTap2.class,"lastActivation").getLong(tap));
                    hooks.packet=e->{};r.f.scheduler.beginTick();
                    assertTrue(r.f.run.fixture.mc.playerController.tryAttackEntity(r.player,r.f.run.scene.getEntityByID(19),()->true));
                    assertTrue(ScaffoldOwnerContinuityTest.field(WTap2.class,"stapOwned").getBoolean(tap));
                }finally{Pulsar.BUS.unsubscribe(hooks);CombatFollowupTest.detach(tap);}
            }
        }
    }
}
