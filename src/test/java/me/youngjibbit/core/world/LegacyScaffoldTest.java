package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.*;
import org.junit.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class LegacyScaffoldTest {
    private Vec3 extended(Vec3 eyes,Vec3 point) {Vec3 direction=point.subtract(eyes).normalize();return point.addVector(direction.xCoord*.001,direction.yCoord*.001,direction.zCoord*.001);}
    @Test public void roundedPointsHitTheirActualFacesForBothAngleGrids() {
        AxisAlignedBB box=new AxisAlignedBB(0,0,0,1,1,1);
        for(EnumFacing face:EnumFacing.values())for(int step:new int[]{45,90}) {
            Vec3 eyes=new Vec3(.43,3,.57);
            switch(face){case WEST:eyes=new Vec3(-2,2,.57);break;case EAST:eyes=new Vec3(3,2,.57);break;case NORTH:eyes=new Vec3(.43,2,-2);break;case SOUTH:eyes=new Vec3(.43,2,3);break;case DOWN:eyes=new Vec3(.43,-2,.57);break;default:break;}
            Vec3 preferred=ScaffoldRotationPlan.hitPoint("Back",box,face,eyes);
            Vec3 point=ScaffoldRotationPlan.roundedFacePoint(box,face,eyes,preferred,step,face==EnumFacing.DOWN?-83.52:83.52);assertNotNull(face.toString(),point);
            ScaffoldRotationPlan plan=ScaffoldRotationPlan.build("Back",eyes,point);assertEquals(0,MathHelper.wrapAngleTo180_double(plan.getYaw()-Math.rint(plan.getYaw()/step)*step),.0001);
            MovingObjectPosition hit=box.calculateIntercept(eyes,extended(eyes,point));assertNotNull(hit);assertEquals(face,hit.sideHit);
        }
        try{ScaffoldRotationPlan.roundedFacePoint(box,EnumFacing.UP,new Vec3(0,3,0),new Vec3(.5,1,.5),0,80);fail("Zero step must not loop");}catch(IllegalArgumentException expected){}
    }
    @Test public void godbridgeHasADistinctReachableEdgePreference() {
        AxisAlignedBB box=new AxisAlignedBB(0,63,-1,1,64,0);Vec3 eyes=new Vec3(.48,65.62,.5);
        ScaffoldRotationPlan center=ScaffoldRotationPlan.build("Back",eyes,ScaffoldRotationPlan.hitPoint("Back",box,EnumFacing.SOUTH,eyes));
        Vec3 edge=ScaffoldRotationPlan.hitPoint("Godbridge",box,EnumFacing.SOUTH,eyes);ScaffoldRotationPlan god=ScaffoldRotationPlan.build("Godbridge",eyes,edge);
        assertTrue(god.getPitch()>center.getPitch());assertEquals(EnumFacing.SOUTH,box.calculateIntercept(eyes,extended(eyes,edge)).sideHit);
    }
    @Test public void nativeBindingsUseOneStableClientUnderTheNormalJit() throws Exception {
        Path output=Files.createTempFile(Paths.get(System.getProperty("user.dir")),"scaffold-bindings-",".log");
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Duser.home="+System.getProperty("user.home"),"-cp",System.getProperty("java.class.path"),
                "org.junit.runner.JUnitCore",BindingChecks.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!child.waitFor(90,TimeUnit.SECONDS)){child.destroy();throw new AssertionError("Scaffold checks timed out: "+output);}
        String result=new String(Files.readAllBytes(output),StandardCharsets.UTF_8);
        System.out.print(result);assertEquals(result,0,child.exitValue());assertTrue(result,result.contains("OK (2 tests)"));
    }

    /** A final client reference cannot safely be replaced repeatedly after HotSpot compiles its consumers. */
    public static class BindingChecks {
        private static RotationBehaviorHookTest.Fixture client;
        @BeforeClass public static void openClient() throws Exception {client=new RotationBehaviorHookTest.Fixture();}
        @AfterClass public static void closeClient() throws Exception {client.scaffold.onDisable();client.close();}
        @Before public void resetSettings() {
            client.scaffold.onDisable();client.scaffold.getBooleanSetting("Round Rots").setValue(false);
            client.scaffold.getModeSetting("Rotation Mode").setValue("Back");client.scaffold.getModeSetting("Place Timing").setValue("Pre");
        }
        private Object current(RotationBehaviorHookTest.Fixture f) throws Exception {java.lang.reflect.Field field=f.scaffold.getClass().getDeclaredField("currentTarget");field.setAccessible(true);return field.get(f.scaffold);}
        @Test public void roundedAndGodbridgeModesPlaceThroughNativePreAndPostWithoutRemappingInput() throws Exception {
            for(String mode:new String[]{"Back","Down","NCP","Godbridge"})for(String timing:new String[]{"Pre","Post"})for(boolean round:new boolean[]{false,true}) {
                if(!round && !mode.equals("Godbridge"))continue;
                {
                    RotationBehaviorHookTest.Fixture f=client;
                    ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.48);
                    f.mc.gameSettings.keyBindForward=new KeyBinding("fixture.forward",17,"fixture");f.mc.gameSettings.keyBindBack=new KeyBinding("fixture.back",31,"fixture");
                    f.mc.gameSettings.keyBindLeft=new KeyBinding("fixture.left",30,"fixture");f.mc.gameSettings.keyBindRight=new KeyBinding("fixture.right",32,"fixture");
                    f.mc.gameSettings.keyBindForward.pressed=true;f.mc.gameSettings.keyBindLeft.pressed=true;
                    f.scaffold.getModeSetting("Rotation Mode").setValue(mode);f.scaffold.getModeSetting("Place Timing").setValue(timing);f.scaffold.getBooleanSetting("Round Rots").setValue(round);
                    f.scaffold.onEnable();int placements=0;
                    try {
                        for(int tick=0;tick<18 && !scene.blocks.containsKey(new BlockPos(0,63,0));tick++) {
                            scene.motionTime.addAndGet(50_000_000L);f.runtime.getScaffoldPlacementService().beginTick();f.player.onUpdateWalkingPlayer();
                            Object sent;while((sent=f.channel.readOutbound())!=null)if(sent instanceof C08PacketPlayerBlockPlacement)placements++;
                        }
                        assertTrue(mode+"/"+timing+"/"+round,scene.blocks.containsKey(new BlockPos(0,63,0)));assertEquals(1,placements);assertEquals(63,f.player.inventory.mainInventory[3].stackSize);
                        double yaw=f.runtime.getRotationManager().getEffectiveYaw(),step=round?90:45;assertEquals(0,MathHelper.wrapAngleTo180_double(yaw-Math.rint(yaw/step)*step),.001);
                        assertEquals(0,f.player.rotationYaw,0);assertTrue(f.mc.gameSettings.keyBindForward.pressed);assertTrue(f.mc.gameSettings.keyBindLeft.pressed);assertFalse(f.mc.gameSettings.keyBindBack.pressed);assertFalse(f.mc.gameSettings.keyBindRight.pressed);
                    } finally {f.scaffold.onDisable();}
                }
            }
        }
        @Test public void settingChangesInvalidateAPendingPlanAndItsRotationOwnership() throws Exception {
            {
                    RotationBehaviorHookTest.Fixture f=client;
                ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.48);f.scaffold.onEnable();
                try {
                    f.runtime.getScaffoldPlacementService().beginTick();f.player.onUpdateWalkingPlayer();assertNotNull(current(f));
                    f.scaffold.getBooleanSetting("Round Rots").setValue(true);assertNull(current(f));assertNull(f.runtime.getRotationManager().getConfirmedOwner());
                    assertFalse(scene.blocks.containsKey(new BlockPos(0,63,0)));
                } finally {f.scaffold.onDisable();}
            }
        }
    }
}
