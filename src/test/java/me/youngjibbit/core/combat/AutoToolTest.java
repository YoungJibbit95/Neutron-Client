package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.impl.player.AutoTool;
import me.zero.alpine.event.EventPhase;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.junit.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class AutoToolTest {
    @Test public void productiveMiningAndLifecycleChecks() throws Exception {
        Path output=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"autotool-",".log");
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Duser.home="+System.getProperty("user.home"),"-cp",System.getProperty("java.class.path"),
                "org.junit.runner.JUnitCore",Checks.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)){child.destroy();throw new AssertionError("AutoTool checks timed out: "+output);}
        String result=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);
        System.out.print(result);assertEquals(result,0,child.exitValue());assertTrue(result,result.contains("OK (4 tests)"));
    }

    /** Stable client reference under the normal JIT; native inventory/tool speeds and event listeners. */
    public static class Checks {
        private static RotationBehaviorHookTest.Fixture f;
        private AutoTool tool;
        private final BlockPos block=new BlockPos(-1,63,0);
        @BeforeClass public static void openClient() throws Exception {f=new RotationBehaviorHookTest.Fixture();}
        @AfterClass public static void closeClient() throws Exception {f.close();}
        @Before public void prepare() throws Exception {
            ScaffoldClientFlowTest.prepare(f,.5,.48);f.player.isDead=false;f.mc.inGameHasFocus=true;f.mc.currentScreen=null;
            f.mc.gameSettings.keyBindAttack=new KeyBinding("synthetic.mine",0,"synthetic");f.mc.gameSettings.keyBindAttack.pressed=true;
            Arrays.fill(f.player.inventory.mainInventory,null);f.player.inventory.currentItem=0;
            f.player.inventory.mainInventory[2]=new ItemStack(Items.iron_pickaxe);
            f.player.inventory.mainInventory[4]=new ItemStack(Items.diamond_pickaxe);
            f.player.inventory.mainInventory[6]=new ItemStack(Items.diamond_shovel);
            f.mc.objectMouseOver=new MovingObjectPosition(new Vec3(-.5,64,.5),EnumFacing.UP,block);
            tool=new AutoTool();tool.setToggled(true);
        }
        @After public void disable(){tool.setToggled(false);}
        private void motion(EventPhase phase){EventMotion event=new EventMotion(.5,64,.5,0,0,true);event.setEventPhase(phase);Pulsar.BUS.post(event);}
        @Test public void bestToolPreOnlyAndReleaseOrDisableRestoresTheRealInitialSlot() {
            motion(EventPhase.POST);assertEquals(0,f.player.inventory.currentItem);
            motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);
            motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);
            f.mc.gameSettings.keyBindAttack.pressed=false;Pulsar.BUS.post(new TickEvent());assertEquals(0,f.player.inventory.currentItem);
            f.mc.gameSettings.keyBindAttack.pressed=true;motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);
            tool.setToggled(false);assertEquals(0,f.player.inventory.currentItem);
            motion(EventPhase.PRE);assertEquals("Disabled listener must not select again",0,f.player.inventory.currentItem);
            tool.setToggled(true);motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);
            tool.setToggled(false);assertEquals(0,f.player.inventory.currentItem);
        }
        @Test public void manualSelectionWinsUntilReleaseAndFocusLossCleansUpWithoutAnotherMotion() {
            motion(EventPhase.PRE);f.player.inventory.currentItem=2;motion(EventPhase.PRE);Pulsar.BUS.post(new TickEvent());assertEquals(2,f.player.inventory.currentItem);
            tool.setToggled(false);assertEquals(2,f.player.inventory.currentItem);tool.setToggled(true);
            motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);f.mc.inGameHasFocus=false;Pulsar.BUS.post(new TickEvent());assertEquals(2,f.player.inventory.currentItem);
            motion(EventPhase.PRE);assertEquals(2,f.player.inventory.currentItem);f.mc.inGameHasFocus=true;
            f.mc.objectMouseOver=new MovingObjectPosition(f.player);motion(EventPhase.PRE);assertEquals(2,f.player.inventory.currentItem);
        }
        @Test public void blockChangesKeepOneOriginalSlotAndTiesDoNotReplaceTheHeldTool() {
            motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);
            f.mc.theWorld.setBlockState(block,Blocks.dirt.getDefaultState(),3);motion(EventPhase.PRE);assertEquals(6,f.player.inventory.currentItem);
            f.mc.theWorld.setBlockState(block,Blocks.wool.getDefaultState(),3);motion(EventPhase.PRE);assertEquals(0,f.player.inventory.currentItem);
            f.mc.theWorld.setBlockState(block,Blocks.stone.getDefaultState(),3);f.player.inventory.currentItem=2;f.player.inventory.mainInventory[4]=new ItemStack(Items.iron_pickaxe);
            motion(EventPhase.PRE);assertEquals(2,f.player.inventory.currentItem);f.mc.gameSettings.keyBindAttack.pressed=false;Pulsar.BUS.post(new TickEvent());assertEquals(2,f.player.inventory.currentItem);
        }
        @Test public void worldAndRespawnDiscardOldSlotOwnership() throws Exception {
            motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);
            Pulsar.BUS.post(new WorldEvent.Unload(f.mc.theWorld));f.player.inventory.currentItem=7;tool.setToggled(false);assertEquals(7,f.player.inventory.currentItem);
            tool.setToggled(true);f.player.inventory.currentItem=0;motion(EventPhase.PRE);assertEquals(4,f.player.inventory.currentItem);
            RotationBehaviorHookTest.Player replacement=RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
            replacement.worldObj=f.mc.theWorld;replacement.inventory=new InventoryPlayer(replacement);replacement.inventory.currentItem=5;
            f.mc.thePlayer=replacement;Pulsar.BUS.post(new TickEvent());assertEquals(5,replacement.inventory.currentItem);assertEquals(4,f.player.inventory.currentItem);
            tool.setToggled(false);assertEquals(5,replacement.inventory.currentItem);f.mc.thePlayer=f.player;
        }
    }
}
