package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Real Aura Motion POST → scheduled click → local swing / confirmed controller C02. */
@RunWith(Parameterized.class)
public class ContinuousClickAuraIntegrationTest {
    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
            {"acceptedContinuousClicksUseRealControllerAndTransport"},
            {"outOfReachClicksSwingWithoutC02AndStillRespectCps"},
            {"cancelledC02DoesNotAdvanceMultiTarget"},
            {"disableStopsClicksAndRequiresFreshMotion"},
            {"cancelledC03CannotGrantClickOrAttack"}
        });
    }
    private final String method;
    public ContinuousClickAuraIntegrationTest(String method) {this.method=method;}
    @Test public void actualAuraPathInFreshJvm() throws Exception {
        Wave2IsolatedCase.run(Checks.class,method);
    }
    public static class Checks {
        private static long count(List<Object> packets,Class<?> type) {
            return packets.stream().filter(type::isInstance).count();
        }
        private static Wave05AuraSelectionChecks.SelectionRun fixture() throws Exception {
            Wave05AuraSelectionChecks.SelectionRun f=new Wave05AuraSelectionChecks.SelectionRun("Single");
            f.run.fixture.aura.getBooleanSetting("Click Randomization Enabled").setValue(false);
            return f;
        }
        @Test public void acceptedContinuousClicksUseRealControllerAndTransport() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=fixture()) {
                for(int n=0;n<5;n++) {
                    List<Object> packets=f.walk();
                    assertEquals(1,count(packets,C03PacketPlayer.class));
                    assertEquals(1,count(packets,C0APacketAnimation.class));
                    assertEquals(1,count(packets,C02PacketUseEntity.class));
                    assertEquals("aura",f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                }
            }
        }
        @Test public void outOfReachClicksSwingWithoutC02AndStillRespectCps() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=fixture()) {
                f.run.fixture.aura.getNumberSetting("Aim Range Margin").setValue(2D);
                f.first.posZ=f.first.lastTickPosZ=4.2;
                f.first.setEntityBoundingBox(new AxisAlignedBB(-.8,64,3.9,-.2,65.8,4.5));
                List<Object> first=f.walk();
                assertEquals("aura",f.run.fixture.runtime.getRotationManager().getConfirmedOwner());
                assertEquals(1,count(first,C0APacketAnimation.class));
                assertEquals(0,count(first,C02PacketUseEntity.class));
                f.clock.addAndGet(-100); // Walk advances its fake clock by 150 ms
                List<Object> tooEarly=f.walk();
                assertEquals(0,count(tooEarly,C0APacketAnimation.class));
                assertEquals(0,count(tooEarly,C02PacketUseEntity.class));
                f.clock.addAndGet(-100);
                List<Object> due=f.walk();
                assertEquals(1,count(due,C0APacketAnimation.class));
                assertEquals(0,count(due,C02PacketUseEntity.class));
                f.first.posZ=f.first.lastTickPosZ=2;
                f.first.setEntityBoundingBox(new AxisAlignedBB(-.8,64,1.7,-.2,65.8,2.3));
                assertEquals(1,count(f.walk(),C02PacketUseEntity.class));
            }
        }
        @Test public void cancelledC02DoesNotAdvanceMultiTarget() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=fixture()) {
                f.run.fixture.aura.getModeSetting("Mode").setValue("Multi");
                final boolean[] cancelled={false};
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{
                    if(!cancelled[0]&&e.isOutbound()&&e.isPre()
                            && e.getPacket() instanceof C02PacketUseEntity) {
                        cancelled[0]=true;e.cancel();
                    }
                };
                Pulsar.BUS.subscribe(hooks);
                try {
                    List<Object> rejected=f.walk();
                    assertTrue(cancelled[0]);
                    assertEquals(1,count(rejected,C0APacketAnimation.class));
                    assertEquals(0,count(rejected,C02PacketUseEntity.class));
                    List<Object> retried=f.walk();
                    C02PacketUseEntity first=(C02PacketUseEntity)retried.stream()
                            .filter(C02PacketUseEntity.class::isInstance).findFirst().get();
                    assertSame(f.first,first.getEntityFromWorld(f.run.scene));
                    List<Object> advanced=f.walk();
                    C02PacketUseEntity second=(C02PacketUseEntity)advanced.stream()
                            .filter(C02PacketUseEntity.class::isInstance).findFirst().get();
                    assertSame(f.second,second.getEntityFromWorld(f.run.scene));
                } finally {Pulsar.BUS.unsubscribe(hooks);}
            }
        }
        @Test public void disableStopsClicksAndRequiresFreshMotion() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=fixture()) {
                assertEquals(1,count(f.walk(),C02PacketUseEntity.class));
                f.run.fixture.aura.setToggled(false);
                List<Object> disabled=f.walk();
                assertEquals(0,count(disabled,C02PacketUseEntity.class));
                assertEquals(0,count(disabled,C0APacketAnimation.class));
                f.run.fixture.aura.setToggled(true);
                assertEquals(1,count(f.walk(),C02PacketUseEntity.class));
            }
        }
        @Test public void cancelledC03CannotGrantClickOrAttack() throws Exception {
            try(Wave05AuraSelectionChecks.SelectionRun f=fixture()) {
                ScaffoldOwnerContinuityTest.Hooks hooks=new ScaffoldOwnerContinuityTest.Hooks();
                hooks.packet=e->{
                    if(e.isOutbound()&&e.isPre()&&e.getPacket() instanceof C03PacketPlayer)
                        e.cancel();
                };
                Pulsar.BUS.subscribe(hooks);
                try {
                    List<Object> refused=f.walk();
                    assertEquals(0,count(refused,C02PacketUseEntity.class));
                    assertEquals(0,count(refused,C0APacketAnimation.class));
                } finally {Pulsar.BUS.unsubscribe(hooks);}
                assertEquals(1,count(f.walk(),C02PacketUseEntity.class));
            }
        }
    }
}
