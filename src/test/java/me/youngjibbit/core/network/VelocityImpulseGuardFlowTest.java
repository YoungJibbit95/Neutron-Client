package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.motion.KnockbackEvent;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.impl.combat.Velocity;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.junit.Test;

import static org.junit.Assert.*;

/** Original NetHandlerPlayClient.handleEntityVelocity --> applied KnockbackEvent
 * POST --> one actual MovementInputEvent consumer; no artificial combat packets.
 */
public class VelocityImpulseGuardFlowTest {
    private static final class DenyPre implements Subscriber {
        @Subscribe final Listener<KnockbackEvent> listener=new Listener<>(event -> {
            if(event.isPre())event.cancel();
        });
    }

    private static Velocity enable(ExploitHookFixture fixture) {
        fixture.supportPlayerOnFloor();
        fixture.world.entities.put(1,fixture.player);
        fixture.player.sprint=true;
        fixture.player.onGround=true;
        Velocity v=new Velocity();
        v.getModeSetting("Mode").setValue("Impulse Guard");
        v.setToggled(true);
        return v;
    }
    private static MovementInputEvent input(float forward,boolean sneak) {
        MovementInputEvent sample=new MovementInputEvent(0,forward,false,sneak);
        Pulsar.BUS.post(sample);
        return sample;
    }
    private static void impulse(ExploitHookFixture f,int id) {
        f.receive(new S12PacketEntityVelocity(id,.3,.2,0));
    }

    @Test public void actualAppliedLocalS12AllowsExactlyOneForwardGroundedJumpWithoutC02() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            Velocity v=enable(f);
            try {
                assertFalse(input(1,false).isJump());
                impulse(f,1);
                assertEquals(.3,f.player.motionX,1e-4);
                assertEquals(.2,f.player.motionY,1e-4);
                assertTrue("one input after an actually applied local knockback",input(1,false).isJump());
                assertFalse("no repeated automatic jump",input(1,false).isJump());
                Object packet;
                while((packet=f.connection.channel.readOutbound())!=null)
                    assertFalse("no automatic reduction attack in this mode",packet instanceof C02PacketUseEntity);
            } finally {v.setToggled(false);}
        }
    }

    @Test public void noForwardSneakOrNonlocalImpulseCannotCreateManualIntent() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            Velocity v=enable(f);
            try {
                impulse(f,7);
                assertFalse("nonlocal S12 is not a player demonstration",input(1,false).isJump());
                impulse(f,1);
                assertFalse("stationary input is not consent to autohop",input(0,false).isJump());
                assertFalse("one pending request cannot wait for a later key press",input(1,false).isJump());
                impulse(f,1);
                assertFalse("sneaking never auto-jumps",input(1,true).isJump());
                assertFalse(input(1,false).isJump());
                impulse(f,1);
                f.player.sprint=false;
                assertFalse(input(1,false).isJump());
                f.player.sprint=true;
                assertFalse(input(1,false).isJump());
            } finally {v.setToggled(false);}
        }
    }

    @Test public void preCancelledS12FocusLossAndSessionEpochInvalidateAllQueuedInput() throws Exception {
        try(ExploitHookFixture f=new ExploitHookFixture()) {
            Velocity v=enable(f);
            try {
                DenyPre deny=new DenyPre();
                Pulsar.BUS.subscribe(deny);
                try {
                    impulse(f,1);
                    assertFalse("PRE-cancelled S12 never publishes POST or jump",input(1,false).isJump());
                } finally {Pulsar.BUS.unsubscribe(deny);}
                impulse(f,1);
                f.client.inGameHasFocus=false;
                assertFalse(input(1,false).isJump());
                f.client.inGameHasFocus=true;
                assertFalse(input(1,false).isJump());
                impulse(f,1);
                f.pipe().worldChanged();
                assertFalse("old connection epoch cannot authorize a jump",input(1,false).isJump());
            } finally {v.setToggled(false);}
        }
    }

    @Test public void legacyModeOrdinalsAndOriginalOptOutDefaultRemainStable() {
        Velocity v=new Velocity();
        assertEquals("Legit",v.getModeSetting("Mode").getValue());
        for(String prior:new String[]{"Legit","Legit Jumpreset","Intave Reduce"}) {
            v.getModeSetting("Mode").setValue(prior);
            assertEquals(prior,v.getModeSetting("Mode").getValue());
        }
        v.getModeSetting("Mode").setValue("Impulse Guard");
        assertEquals("Impulse Guard",v.getModeSetting("Mode").getValue());
        assertEquals(2D,v.getNumberSetting("Reduce Ticks").getValue(),0);
    }
}
