package me.youngjibbit.module.impl.player;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.Enchantment;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;
public class InventoryManagerSafetyTest {
 static Object call(Object o,String n) throws Exception {Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}
 static Object field(Object o,String n) throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void scan(InventoryManager m) throws Exception {call(m,"searchForItems");call(m,"searchForBestArmor");call(m,"searchForTrash");}
 @Test public void enchantedPickaxeSurvivesBothSlotOrders() throws Exception {
  Bootstrap.register();
  try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   InventoryManager m=new InventoryManager();
   for(int better:new int[]{0,1}) {Arrays.fill(f.player.inventory.mainInventory,null);f.player.inventory.mainInventory[0]=new ItemStack(Items.diamond_pickaxe);f.player.inventory.mainInventory[1]=new ItemStack(Items.diamond_pickaxe);f.player.inventory.mainInventory[better].addEnchantment(Enchantment.efficiency,5);scan(m);assertEquals(better,field(m,"bestPickaxeSlot"));assertFalse(((List)field(m,"trash")).contains(better));}
  }
 }

 public static class Drops extends net.minecraft.client.multiplayer.PlayerControllerMP {
  int calls, wireSlot; ItemStack observed;
  Drops(net.minecraft.client.Minecraft mc) {super(mc,null);}
  @Override public ItemStack windowClick(int w,int slot,int button,int mode,net.minecraft.entity.player.EntityPlayer p) {calls++;wireSlot=slot;observed=p.inventory.getStackInSlot(slot>=36?slot-36:slot);return null;}
 }
 static boolean drop(InventoryManager m,int slot) throws Exception {Method method=m.getClass().getDeclaredMethod("dropTrashSlot",int.class);method.setAccessible(true);return (Boolean)method.invoke(m,slot);}
 @Test public void dropRevalidatesStackAndCompetitorsAndUsesCurrentMappedSlot() throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   InventoryManager m=new InventoryManager();Drops d=new Drops(f.mc);f.mc.playerController=d;
   f.player.inventoryContainer=new net.minecraft.inventory.ContainerPlayer(f.player.inventory,false,f.player);
   for(boolean full:new boolean[]{false,true}) {
    Arrays.fill(f.player.inventory.mainInventory,null);
    if(full) for(int i=0;i<9;i++)f.player.inventory.mainInventory[i]=new ItemStack(Items.stick);
    ItemStack plain=new ItemStack(Items.diamond_pickaxe),best=new ItemStack(Items.diamond_pickaxe);best.addEnchantment(Enchantment.efficiency,5);
    f.player.inventory.mainInventory[8]=plain;f.player.inventory.mainInventory[12]=best;scan(m);
    f.player.inventory.mainInventory[8]=best;assertFalse(drop(m,8));assertEquals(0,d.calls);
    f.player.inventory.mainInventory[8]=plain;scan(m);f.player.inventory.mainInventory[12]=null;assertFalse(drop(m,8));assertEquals(0,d.calls);
    f.player.inventory.mainInventory[12]=best;scan(m);assertTrue(drop(m,8));assertSame(plain,d.observed);assertEquals(44,d.wireSlot);d.calls=0;
   }
  }
 }
 @Test public void equalEfficiencyKeepsMoreRemainingDurabilityThenLowestSlot() throws Exception {
  Bootstrap.register();try(RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
   InventoryManager m=new InventoryManager();f.player.inventory.mainInventory[0]=new ItemStack(Items.diamond_pickaxe,1,100);f.player.inventory.mainInventory[8]=new ItemStack(Items.diamond_pickaxe,1,10);scan(m);assertEquals(8,field(m,"bestPickaxeSlot"));f.player.inventory.mainInventory[0].setItemDamage(10);scan(m);assertEquals(0,field(m,"bestPickaxeSlot"));
  }
 }
}
