package me.youngjibbit.module.impl.player;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class GuardedInventoryClickTest {
 static class Watch implements Subscriber {
  java.util.function.Consumer<EventPacket> action=e->{};
  @Subscribe final Listener<EventPacket> packet=new Listener<>(e->action.accept(e));
 }
 @Test public void realPredictionUsesModeTwoAndFinalGuardRejectsLatePreMutationsAndReplacement()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   f.player.inventoryContainer=new net.minecraft.inventory.ContainerPlayer(f.player.inventory,false,f.player);f.player.openContainer=f.player.inventoryContainer;
   Watch watch=new Watch();Pulsar.BUS.subscribe(watch);try {
    for(int mode=0;mode<5;mode++) {
     ItemStack source=new ItemStack(Items.mushroom_stew),dest=new ItemStack(Items.stick);f.player.inventory.mainInventory[12]=source;f.player.inventory.mainInventory[8]=dest;f.player.inventory.currentItem=0;
     final int scenario=mode;watch.action=e->{if(e.isPre()&&e.getPacket() instanceof C0EPacketClickWindow){if(scenario==1)f.player.inventory.currentItem=2;if(scenario==2)e.cancel();if(scenario==3)e.setPacket(new C0EPacketClickWindow(0,11,7,2,null,(short)99));if(scenario==4)f.player.inventory.mainInventory[8]=new ItemStack(Items.bread);}};
     C0EPacketClickWindow sent=f.mc.playerController.windowClickGuarded(0,12,8,2,f.player,()->true,()->f.player.inventory.currentItem==0&&ItemStack.areItemStacksEqual(source,f.player.inventory.mainInventory[8])&&f.player.inventory.mainInventory[12]==dest);
     if(mode==0){assertNotNull(sent);assertSame(sent,f.channel.readOutbound());assertTrue(ItemStack.areItemStacksEqual(source,f.player.inventory.mainInventory[8]));assertSame(dest,f.player.inventory.mainInventory[12]);}
     else{assertNull(sent);assertNull(f.channel.readOutbound());}
    }
   }finally{Pulsar.BUS.unsubscribe(watch);}
  }
 }
}
