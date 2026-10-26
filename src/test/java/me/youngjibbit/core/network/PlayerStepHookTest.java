package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.TickbaseBudget;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Timer;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class PlayerStepHookTest {
    public static class WorldStub extends AttackHookTest.WorldStub {
        boolean loaded;
        @Override public boolean isBlockLoaded(BlockPos pos) {return loaded;}
    }
    public static class PlayerStub extends EntityPlayerSP {
        public PlayerStub() {super(null,null,null,null);}
        @Override public boolean isEntityAlive() {return true;}
    }
    public static final class CancelUpdate implements Subscriber {
        int updates;
        @Subscribe final Listener<EventUpdate> listener=new Listener<>(e->{updates++;e.cancel();});
    }
    @Test public void actualPlayerHookWithholdsBeforeUpdateAndCancellationAbortsCreditWithoutFreezingMaintenance() throws Exception {
        try(SessionConsumerIntegrationTest.Fixture connection=new SessionConsumerIntegrationTest.Fixture()) {
        Minecraft mc=connection.client;PlayerStub player=AttackHookTest.bare(PlayerStub.class);
        WorldStub world=AttackHookTest.bare(WorldStub.class);world.loaded=true;player.worldObj=world;player.onGround=true;
        mc.thePlayer=player;mc.theWorld=world;mc.inGameHasFocus=true;mc.timer=new Timer(20,()->0);
        Field send=EntityPlayerSP.class.getDeclaredField("sendQueue");send.setAccessible(true);send.set(player,connection.play);
        connection.play.clientWorldController=world;
        Tickbase module=new Tickbase(System::nanoTime,mc);CancelUpdate cancel=new CancelUpdate();
        module.getBooleanSetting("Require Target").setValue(false);
        Field budgetField=Tickbase.class.getDeclaredField("budget");budgetField.setAccessible(true);TickbaseBudget budget=(TickbaseBudget)budgetField.get(module);
        Pulsar.BUS.subscribe(cancel);module.setToggled(true);
        try {
            Tickbase.beginFrame(mc,false,1,50);player.onUpdate();assertEquals(1,budget.getCredit());assertEquals(0,cancel.updates);
            // runTick and its input/network work remain independent. The next real player update reaches PRE and aborts.
            Tickbase.beginFrame(mc,false,1,50);player.onUpdate();assertEquals(0,budget.getCredit());assertEquals(1,cancel.updates);
            world.loaded=false;Tickbase.beginFrame(mc,false,1,50);player.onUpdate();assertEquals(0,budget.getCredit());assertEquals(1,cancel.updates);
            world.loaded=true;Tickbase.beginFrame(mc,true,1,50);player.onUpdate();assertEquals(0,budget.getCredit());assertEquals(2,cancel.updates);
            mc.timer.timerSpeed=1.6f;module.setToggled(false);assertEquals(1.6f,mc.timer.timerSpeed,0);
        } finally {module.setToggled(false);Pulsar.BUS.unsubscribe(cancel);}
        }
    }
}
