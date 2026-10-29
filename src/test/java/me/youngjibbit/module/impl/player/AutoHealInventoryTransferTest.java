package me.youngjibbit.module.impl.player;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.event.packet.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;
public class AutoHealInventoryTransferTest {
 static void prepare(RotationBehaviorHookTest.Fixture f)throws Exception {
  ScaffoldClientFlowTest.prepare(f,.5,.5);
  Field thread=net.minecraft.client.Minecraft.class.getDeclaredField("mcThread");thread.setAccessible(true);thread.set(f.mc,Thread.currentThread());
  f.player.inventoryContainer=new net.minecraft.inventory.ContainerPlayer(f.player.inventory,false,f.player);f.player.openContainer=f.player.inventoryContainer;
  Field watcher=net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");watcher.setAccessible(true);net.minecraft.entity.DataWatcher data=new net.minecraft.entity.DataWatcher(f.player);data.addObject(6,4f);watcher.set(f.player,data);
  Arrays.fill(f.player.inventory.mainInventory,null);
 }
 static List<Object> drain(RotationBehaviorHookTest.Fixture f){return AutoHealSafetyTest.drain(f);}
 static C0EPacketClickWindow click(List<Object> packets){for(Object p:packets)if(p instanceof C0EPacketClickWindow)return(C0EPacketClickWindow)p;return null;}
 static void tick(){
  me.youngjibbit.core.observation.RuntimeObservation observation=me.youngjibbit.core.bootstrap.RuntimeServices.observation();
  observation.beginOuterTick();
  try{Pulsar.BUS.post(new TickEvent());}finally{observation.endOuterTick();}
 }
 static void resync(RotationBehaviorHookTest.Fixture f){List<ItemStack> slots=new ArrayList<>();for(net.minecraft.inventory.Slot s:f.player.inventoryContainer.inventorySlots)slots.add(s.getStack());f.channel.writeInbound(new S30PacketWindowItems(0,slots));}
 @Test public void defaultOffHotbarFirstAndAckGatesEmptyOrFullHotbarTransferWithoutDrops()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHeal heal=new AutoHeal();heal.onEnable();try {
    f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);tick();assertTrue(drain(f).isEmpty());
    heal.getBooleanSetting("Inventory Transfers").setValue(true);
    for(boolean full:new boolean[]{false,true}) {
     if(full){heal.onDisable();heal.onEnable();for(int i=0;i<9;i++)f.player.inventory.mainInventory[i]=new ItemStack(Items.stick,i+1);f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);}
     f.player.inventory.currentItem=3;ItemStack displaced=f.player.inventory.mainInventory[0];tick();List<Object> out=drain(f);assertEquals(1,out.size());C0EPacketClickWindow packet=click(out);assertNotNull(packet);assertEquals(12,packet.getSlotId());assertEquals(0,packet.getUsedButton());assertEquals(2,packet.getMode());assertEquals(3,f.player.inventory.currentItem);assertSame(displaced,f.player.inventory.mainInventory[12]);
     tick();assertTrue(drain(f).isEmpty());f.channel.writeInbound(new S32PacketConfirmTransaction(0,(short)(packet.getActionNumber()+1),true));tick();assertTrue(drain(f).isEmpty());
     AutoHealSafetyTest.Watch cancelAck=new AutoHealSafetyTest.Watch();cancelAck.action=e->{if(e.isInbound()&&e.isPre()&&e.getPacket() instanceof S32PacketConfirmTransaction)e.cancel();};Pulsar.BUS.subscribe(cancelAck);try{f.channel.writeInbound(new S32PacketConfirmTransaction(0,packet.getActionNumber(),true));tick();assertTrue(drain(f).isEmpty());}finally{Pulsar.BUS.unsubscribe(cancelAck);}
     f.channel.writeInbound(new S32PacketConfirmTransaction(0,packet.getActionNumber(),true));assertTrue(drain(f).isEmpty());tick();out=drain(f);assertTrue(out.stream().anyMatch(p->p instanceof C08PacketPlayerBlockPlacement));assertEquals(3,f.player.inventory.currentItem);
    }
   }finally{heal.onDisable();}
  }
 }
 @Test public void rejectedMissingAckDisableAndManualChangesWaitForAppliedFullResync()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();try {
    f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);tick();C0EPacketClickWindow packet=click(drain(f));assertNotNull(packet);
    f.channel.writeInbound(new S32PacketConfirmTransaction(0,packet.getActionNumber(),false));drain(f);tick();assertTrue(drain(f).isEmpty());assertTrue(heal.info.contains("resync"));
    heal.onDisable();heal.onEnable();tick();assertTrue(drain(f).isEmpty());resync(f);tick();assertTrue(drain(f).stream().anyMatch(p->p instanceof C08PacketPlayerBlockPlacement));
    heal.onDisable();heal.onEnable();Arrays.fill(f.player.inventory.mainInventory,null);f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);tick();packet=click(drain(f));assertNotNull(packet);f.player.inventory.currentItem=4;f.channel.writeInbound(new S32PacketConfirmTransaction(0,packet.getActionNumber(),true));tick();assertTrue(drain(f).isEmpty());assertEquals(4,f.player.inventory.currentItem);
    heal.onDisable();heal.onEnable();tick();assertTrue(drain(f).isEmpty());resync(f);tick();assertFalse(drain(f).isEmpty());
   }finally{heal.onDisable();}
  }
 }
 @Test public void latePreCancelReplacementAndMutationRollbackOnlyOwnPrediction()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHealSafetyTest.Watch watch=new AutoHealSafetyTest.Watch();Pulsar.BUS.subscribe(watch);try {
    for(int scenario=0;scenario<4;scenario++) {
     final int mode=scenario;AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();try {
      Arrays.fill(f.player.inventory.mainInventory,null);f.player.inventory.currentItem=3;ItemStack source=new ItemStack(Items.mushroom_stew);f.player.inventory.mainInventory[12]=source;
      watch.action=e->{if(e.isPre()&&e.getPacket() instanceof C0EPacketClickWindow){if(mode==0)e.cancel();if(mode==1)e.setPacket(new C0EPacketClickWindow(0,13,0,2,null,(short)77));if(mode==2)f.player.inventory.currentItem=4;if(mode==3)f.player.inventory.mainInventory[0]=new ItemStack(Items.bread);}};
      tick();assertTrue(drain(f).isEmpty());assertEquals(mode==2?4:3,f.player.inventory.currentItem);
      if(mode<3){assertSame(source,f.player.inventory.mainInventory[12]);assertNull(f.player.inventory.mainInventory[0]);}else{assertEquals(Items.bread,f.player.inventory.mainInventory[0].getItem());tick();assertTrue(drain(f).isEmpty());assertTrue(heal.info.contains("resync"));}
     }finally{heal.onDisable();watch.action=e->{};}
    }
   }finally{Pulsar.BUS.unsubscribe(watch);}
  }
 }

 @Test public void timeoutAndContextChangesCannotReleaseUnacknowledgedPrediction()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();try {
    f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);tick();C0EPacketClickWindow sent=click(drain(f));assertNotNull(sent);
    Field transferField=AutoHeal.class.getDeclaredField("transfer");transferField.setAccessible(true);AutoHealInventoryTransfer transfer=(AutoHealInventoryTransfer)transferField.get(heal);
    Field pendingField=AutoHealInventoryTransfer.class.getDeclaredField("pending");pendingField.setAccessible(true);Object pending=pendingField.get(transfer);Field deadline=pending.getClass().getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(pending,System.nanoTime()-1);
    tick();assertTrue(drain(f).isEmpty());f.channel.writeInbound(new S32PacketConfirmTransaction(0,sent.getActionNumber(),true));tick();assertTrue(drain(f).isEmpty());assertTrue(heal.info.contains("resync"));
    f.mc.theWorld=RotationBehaviorHookTest.bare(net.minecraft.client.multiplayer.WorldClient.class);f.player.worldObj=f.mc.theWorld;tick();assertTrue(drain(f).stream().anyMatch(p->p instanceof C08PacketPlayerBlockPlacement));assertFalse(heal.info.contains("resync"));
   }finally{heal.onDisable();}
  }
 }

 @Test public void fullSyncBeforeMatchingAckDoesNotAuthorizeUseAndWorldUnloadDuringPreDoesNotThrow()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();try {
    f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);List<ItemStack> before=new ArrayList<>();for(net.minecraft.inventory.Slot slot:f.player.inventoryContainer.inventorySlots)before.add(slot.getStack());
    tick();C0EPacketClickWindow sent=click(drain(f));assertNotNull(sent);f.channel.writeInbound(new S30PacketWindowItems(0,before));tick();assertTrue(drain(f).isEmpty());f.channel.writeInbound(new S32PacketConfirmTransaction(0,sent.getActionNumber(),true));tick();assertTrue(drain(f).isEmpty());
    List<ItemStack> after=new ArrayList<>(before);after.set(36,before.get(12));after.set(12,null);f.channel.writeInbound(new S30PacketWindowItems(0,after));tick();assertTrue(drain(f).stream().anyMatch(p->p instanceof C08PacketPlayerBlockPlacement));
   }finally{heal.onDisable();}
   Arrays.fill(f.player.inventory.mainInventory,null);f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);AutoHeal next=new AutoHeal();next.getBooleanSetting("Inventory Transfers").setValue(true);next.onEnable();AutoHealSafetyTest.Watch watch=new AutoHealSafetyTest.Watch();Pulsar.BUS.subscribe(watch);try {
    watch.action=e->{if(e.isPre()&&e.getPacket() instanceof C0EPacketClickWindow){Pulsar.BUS.post(new me.youngjibbit.event.world.WorldEvent.Unload(f.mc.theWorld));f.mc.thePlayer=null;f.mc.theWorld=null;}};tick();assertTrue(drain(f).isEmpty());
   }finally{Pulsar.BUS.unsubscribe(watch);next.onDisable();}
  }
 }

 static AutoHealInventoryTransfer transfer(AutoHeal heal)throws Exception {Field f=AutoHeal.class.getDeclaredField("transfer");f.setAccessible(true);return(AutoHealInventoryTransfer)f.get(heal);}
 static boolean observing(AutoHeal heal)throws Exception {Field f=AutoHealInventoryTransfer.class.getDeclaredField("observing");f.setAccessible(true);return f.getBoolean(transfer(heal));}
 @Test public void pendingObserverSettlesWhileDisabledAndReleasesOnContextCleanup()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);
   for(boolean reject:new boolean[]{false,true}) {
    Arrays.fill(f.player.inventory.mainInventory,null);f.player.inventory.currentItem=3;f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();tick();C0EPacketClickWindow sent=click(drain(f));assertNotNull(sent);assertTrue(observing(heal));heal.onDisable();assertTrue(observing(heal));
    f.channel.writeInbound(new S32PacketConfirmTransaction(0,sent.getActionNumber(),!reject));drain(f);if(reject){assertTrue(observing(heal));resync(f);}assertFalse(observing(heal));tick();assertTrue(drain(f).isEmpty());heal.onEnable();try{tick();assertTrue(drain(f).stream().anyMatch(p->p instanceof C08PacketPlayerBlockPlacement));}finally{heal.onDisable();}
   }
   Arrays.fill(f.player.inventory.mainInventory,null);f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();tick();assertNotNull(click(drain(f)));heal.onDisable();assertTrue(observing(heal));Pulsar.BUS.post(new me.youngjibbit.event.world.WorldEvent.Unload(f.mc.theWorld));assertFalse(observing(heal));
  }
 }

 @Test public void closedSameConnectionClearsPendingObserverOnTick()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);tick();assertNotNull(click(drain(f)));heal.onDisable();assertTrue(observing(heal));f.channel.close();tick();assertFalse(observing(heal));assertFalse(transfer(heal).blocked());
  }
 }

 @Test public void deniedBeforePredictionLeavesNoObserverOrMutation()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHeal heal=new AutoHeal();ItemStack source=new ItemStack(Items.mushroom_stew);f.player.inventory.mainInventory[12]=source;f.mc.inGameHasFocus=false;
   assertTrue(transfer(heal).begin(stack->stack!=null&&stack.getItem()==Items.mushroom_stew));assertFalse(observing(heal));assertFalse(transfer(heal).blocked());assertSame(source,f.player.inventory.mainInventory[12]);assertNull(f.player.inventory.mainInventory[0]);assertTrue(drain(f).isEmpty());
  }
 }

 @Test public void reentrantAuthoritativeSyncDuringClickPreReleasesOwnerAndAbortsTransport()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   prepare(f);AutoHeal heal=new AutoHeal();heal.getBooleanSetting("Inventory Transfers").setValue(true);heal.onEnable();AutoHealSafetyTest.Watch watch=new AutoHealSafetyTest.Watch();Pulsar.BUS.subscribe(watch);try {
    f.player.inventory.mainInventory[12]=new ItemStack(Items.mushroom_stew);watch.action=e->{if(e.isPre()&&e.getPacket() instanceof C0EPacketClickWindow)resync(f);};tick();assertTrue(drain(f).isEmpty());assertFalse(observing(heal));
   }finally{Pulsar.BUS.unsubscribe(watch);heal.onDisable();}
  }
 }
}
