package me.youngjibbit.module.impl.player;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.core.network.PacketFlowTest;
import me.zero.alpine.listener.*;
import me.youngjibbit.event.packet.EventPacket;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;
public class AutoHealSafetyTest {
 static Object call(Object o,String n) throws Exception {Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}
 static void use(AutoHeal m,boolean potion) throws Exception {Method method=m.getClass().getDeclaredMethod("useHealingItem",boolean.class);method.setAccessible(true);method.invoke(m,potion);}
 static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static long deadline(AutoHeal m)throws Exception{Field f=AutoHeal.class.getDeclaredField("nextUseNanos");f.setAccessible(true);return f.getLong(m);}
 static ItemStack potion(int amplifier,boolean splash) {
  ItemStack s=new ItemStack(Items.potionitem,1,splash?16384:0);NBTTagCompound tag=new NBTTagCompound();NBTTagList effects=new NBTTagList();NBTTagCompound heal=new NBTTagCompound();heal.setByte("Id",(byte)net.minecraft.potion.Potion.heal.getId());heal.setByte("Amplifier",(byte)amplifier);heal.setInteger("Duration",1);effects.appendTag(heal);tag.setTag("CustomPotionEffects",effects);s.setTagCompound(tag);return s;
 }
 public static class Healthy extends RotationBehaviorHookTest.Player {
  public Healthy(){super();} public void initHealth(){this.dataWatcher=new net.minecraft.entity.DataWatcher(this);this.dataWatcher.addObject(6,4f);}
 }
 static void low(RotationBehaviorHookTest.Fixture f)throws Exception {
  Healthy p=RotationBehaviorHookTest.bare(Healthy.class);p.initHealth();p.inventory=new net.minecraft.entity.player.InventoryPlayer(p);Field q=net.minecraft.client.entity.EntityPlayerSP.class.getDeclaredField("sendQueue");q.setAccessible(true);q.set(p,f.player.sendQueue);f.mc.thePlayer=p;
 }
 @Test public void waterAndEmptyEffectsAreSafeAndOnlySupportedHotbarItemsQualify()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   low(f);AutoHeal m=new AutoHeal();f.mc.thePlayer.inventory.mainInventory[0]=new ItemStack(Items.potionitem,1,0);assertEquals(false,call(m,"shouldUsePotion"));
   ItemStack empty=new ItemStack(Items.potionitem,1,16384);NBTTagCompound tag=new NBTTagCompound();tag.setTag("CustomPotionEffects",new NBTTagList());empty.setTagCompound(tag);f.mc.thePlayer.inventory.mainInventory[0]=empty;assertEquals(-1,call(m,"getPotionSlot"));
   for(int slot:new int[]{0,8,9,12,39}) {
    Arrays.fill(f.mc.thePlayer.inventory.mainInventory,null);Arrays.fill(f.mc.thePlayer.inventory.armorInventory,null);
    f.mc.thePlayer.inventory.setInventorySlotContents(slot,potion(1,true));assertEquals(slot<9?slot:-1,call(m,"getPotionSlot"));
   }
   Arrays.fill(f.mc.thePlayer.inventory.mainInventory,null);f.mc.thePlayer.inventory.mainInventory[0]=potion(0,true);assertEquals(-1,call(m,"getPotionSlot"));f.mc.thePlayer.inventory.mainInventory[0]=potion(1,false);assertEquals(-1,call(m,"getPotionSlot"));
   f.mc.thePlayer.inventory.mainInventory[0]=new ItemStack(Items.bread);assertEquals(-1,call(m,"getSoupSlot"));f.mc.thePlayer.inventory.mainInventory[8]=new ItemStack(Items.mushroom_stew);assertEquals(8,call(m,"getSoupSlot"));
  }
 }
 public static class Watch implements Subscriber {
  java.util.function.Consumer<EventPacket> action=e->{};
  @Subscribe final Listener<EventPacket> listener=new Listener<>(e->action.accept(e));
 }
 static List<Object> drain(RotationBehaviorHookTest.Fixture f){List<Object> out=new ArrayList<>();Object p;while((p=f.channel.readOutbound())!=null)out.add(p);return out;}
 @Test public void actualSlotPacketsStackRestoreCooldownAndLifecycle()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   ScaffoldClientFlowTest.prepare(f,.5,.5);AutoHeal m=new AutoHeal();try {ItemStack stack=potion(1,true);f.player.inventory.mainInventory[8]=stack;f.player.inventory.currentItem=0;
   use(m,true);List<Object> out=drain(f);assertEquals(3,out.size());assertEquals(8,((C09PacketHeldItemChange)out.get(0)).getSlotId());assertEquals(stack.getMetadata(),((C08PacketPlayerBlockPlacement)out.get(1)).getStack().getMetadata());assertEquals(0,((C09PacketHeldItemChange)out.get(2)).getSlotId());assertEquals(0,f.player.inventory.currentItem);assertTrue(deadline(m)>System.nanoTime());use(m,true);assertTrue(drain(f).isEmpty());
   m.onDisable();assertEquals(0,deadline(m));m.onEnable();use(m,true);assertEquals(3,drain(f).size());f.mc.theWorld=RotationBehaviorHookTest.bare(net.minecraft.client.multiplayer.WorldClient.class);use(m,true);assertEquals(3,drain(f).size());}finally{m.onDisable();}
  }
 }
 @Test public void cancelledReplacedAndReentrantPacketsCannotUseWrongStackOrRestoreOverManualSelection()throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   ScaffoldClientFlowTest.prepare(f,.5,.5);Watch watch=new Watch();Pulsar.BUS.subscribe(watch);try {
    for(int scenario=0;scenario<8;scenario++) {
     final int mode=scenario;AutoHeal m=new AutoHeal();f.player.inventory.mainInventory[8]=potion(1,true);f.player.inventory.currentItem=0;f.mc.playerController.syncCurrentPlayItemChecked();drain(f);
     Pulsar.BUS.subscribe(m);
     watch.action=e->{if(!e.isPre())return;if(e.getPacket() instanceof C09PacketHeldItemChange && ((C09PacketHeldItemChange)e.getPacket()).getSlotId()==8) {if(mode==0)e.cancel();if(mode==1)f.player.inventory.currentItem=2;if(mode==2)f.player.inventory.mainInventory[8]=null;if(mode==6)m.onDisable();if(mode==7)Pulsar.BUS.post(new me.youngjibbit.event.world.WorldEvent.Unload(f.mc.theWorld));}
      if(e.getPacket() instanceof C08PacketPlayerBlockPlacement){if(mode==3)e.cancel();if(mode==4)e.setPacket(new C08PacketPlayerBlockPlacement(new ItemStack(Items.bread)));if(mode==5)f.player.inventory.currentItem=2;}};
     use(m,true);List<Object> out=drain(f);for(Object p:out)assertFalse(p instanceof C08PacketPlayerBlockPlacement);assertEquals(0,deadline(m));assertEquals(mode==1||mode==5?2:0,f.player.inventory.currentItem);watch.action=e->{};Pulsar.BUS.unsubscribe(m);
    }
   }finally{Pulsar.BUS.unsubscribe(watch);}
  }
 }
}
