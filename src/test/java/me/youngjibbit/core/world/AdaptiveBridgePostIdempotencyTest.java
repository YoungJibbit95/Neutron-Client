package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.event.EventPhase;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Real headless PRE -> accepted C03 -> POST controller. Explicit repeat POST
 * does not turn one accepted C03 into additional C08 attempts after rejection.
 */
public class AdaptiveBridgePostIdempotencyTest {
    private static final BlockPos DESTINATION=new BlockPos(0,63,0);

    private static final class DenyC08 implements Subscriber {
        final AtomicInteger attempted=new AtomicInteger();
        @Subscribe final Listener<EventPacket> listener=new Listener<>(event -> {
            if(event.isOutbound() && event.isPre()
                    && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                attempted.incrementAndGet();
                event.cancel();
            }
        });
    }

    private static List<Object> walk(RotationBehaviorHookTest.Fixture fixture,
                                     ScaffoldClientFlowTest.Scene scene) {
        scene.motionTime.addAndGet(50_000_000L);
        fixture.runtime.getScaffoldPlacementService().beginTick();
        ScaffoldClientFlowTest.ordinaryWalking(fixture);
        List<Object> sent=new ArrayList<>();
        Object outbound;
        while((outbound=fixture.channel.readOutbound())!=null)sent.add(outbound);
        return sent;
    }

    @Test public void deniedC08CanNeverBeRetriedByRepeatedPostForSameAcceptedC03() throws Exception {
        try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
            ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(fixture,.5,.48);
            fixture.player.movementInput=new MovementInput();
            fixture.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            fixture.scaffold.onEnable();
            DenyC08 rejection=new DenyC08();
            Pulsar.BUS.subscribe(rejection);
            try {
                boolean observed=false;
                for(int frame=0;frame<40 && !observed;frame++) {
                    List<Object> sent=walk(fixture,scene);
                    if(rejection.attempted.get()==0)continue;
                    assertEquals("a transport-accepted C03 is the only placement precursor",1,
                            sent.stream().filter(C03PacketPlayer.class::isInstance).count());
                    int before=rejection.attempted.get();
                    EventMotion duplicate=new EventMotion(fixture.player.posX,
                            fixture.player.posY,fixture.player.posZ,
                            fixture.player.rotationYaw,fixture.player.rotationPitch,fixture.player.onGround);
                    duplicate.setEventPhase(EventPhase.POST);
                    duplicate.setAccepted(true);
                    for(int repeat=0;repeat<6;repeat++)Pulsar.BUS.post(duplicate);
                    assertEquals("one rejected C08 cannot be resent using a duplicate POST",before,
                            rejection.attempted.get());
                    assertEquals(Blocks.air,scene.getBlockState(DESTINATION).getBlock());
                    assertEquals(64,fixture.player.inventory.mainInventory[3].stackSize);
                    observed=true;
                }
                assertTrue("real controller must attempt a C08 before denial",observed);
            } finally {
                Pulsar.BUS.unsubscribe(rejection);
                fixture.scaffold.onDisable();
            }
        }
    }

    @Test public void allPhysicalHeadingInputsUseFreshOriginalPreIntent() throws Exception {
        final float[][] directions={{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1}};
        for(float[] direction:directions) {
            try(RotationBehaviorHookTest.Fixture fixture=new RotationBehaviorHookTest.Fixture()) {
                ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(fixture,.5,.48);
                MovementInput input=new MovementInput();
                input.moveForward=direction[0];input.moveStrafe=direction[1];
                fixture.player.movementInput=input;
                fixture.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
                fixture.scaffold.onEnable();
                boolean placed=false;
                for(int tick=0;tick<35 && !placed;tick++) {
                    List<Object> sent=walk(fixture,scene);
                    int c03=-1,c08=-1;
                    for(int i=0;i<sent.size();i++) {
                        if(sent.get(i) instanceof C03PacketPlayer)c03=i;
                        if(sent.get(i) instanceof C08PacketPlayerBlockPlacement)c08=i;
                    }
                    if(c08>=0) {
                        assertTrue("POST must remain after exactly the original C03",c03>=0 && c08>c03);
                        placed=true;
                    }
                }
                assertTrue("input="+direction[0]+","+direction[1],placed);
                assertEquals(Blocks.stone,scene.getBlockState(DESTINATION).getBlock());
                fixture.scaffold.onDisable();
            }
        }
    }
}
