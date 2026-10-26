package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.utils.math.FastNoiseLite;
import me.youngjibbit.value.Value;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static org.junit.Assert.*;

/** Public settings -> real module listener -> shared service -> real player/transport hook. */
public class RotationModuleBindingChecks {
    private static RotationBehaviorHookTest.Fixture shared;
    @org.junit.BeforeClass public static void createClient() throws Exception { shared=new RotationBehaviorHookTest.Fixture(); }
    @org.junit.AfterClass public static void closeClient() throws Exception { shared.close(); }
    static final class Setup implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f=shared;
        final AtomicLong clock=new AtomicLong();
        final RotationManager rotation=new RotationManager(clock::get,424242);
        final RotationBehaviorHookTest.Player target=RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
        final Module module;
        final boolean visible;
        Setup(boolean visible,Consumer<Module> settings) throws Exception {
            this.visible=visible;
            f.mc.inGameHasFocus=true;f.mc.currentScreen=null;f.player.isDead=false;
            f.player.rotationYaw=f.player.rotationPitch=0;
            RotationBehaviorHookTest.field(net.minecraft.client.entity.EntityPlayerSP.class,"lastReportedYaw").set(f.player,0F);
            RotationBehaviorHookTest.field(net.minecraft.client.entity.EntityPlayerSP.class,"lastReportedPitch").set(f.player,0F);
            RotationBehaviorHookTest.field(net.minecraft.client.entity.EntityPlayerSP.class,"positionUpdateTicks").set(f.player,0);
            ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.48);
            RotationBehaviorHookTest.field(ClientRuntime.class,"rotationManager").set(f.runtime,rotation);
            target.posX=-1;target.posY=64;target.posZ=2;target.worldObj=scene;
            target.setEntityBoundingBox(new AxisAlignedBB(-1.3,64,1.7,-.7,65.8,2.3));
            RotationBehaviorHookTest.field(World.class,"loadedEntityList").set(scene,new ArrayList<>(Collections.singletonList(target)));
            module=visible?new AimAssist():f.aura;
            for(Value<?> value:module.getValues()) value.reset();
            RotationBehaviorHookTest.field(Module.class,"toggled").set(module,true);
            module.getModeSetting(visible?"Rotations":"Rot Mode").setValue(visible?"Normal":"Basic");
            module.getNumberSetting("Yaw Speed").setValue(90D);module.getNumberSetting("Pitch Speed").setValue(90D);
            module.getNumberSetting("Jitter").setValue(3D);
            module.getBooleanSetting(visible?"Best Hit Vec":"Best Aim Vec").setValue(false);
            module.getModeSetting("Randomization Style").setValue("Benutzerdefiniert");
            settings.accept(module);
            // Exercise rotations without performing an unrelated combat action in this synthetic scene.
            f.player.usingItem=true;module.onEnable();
        }
        void tick(long now) {
            clock.set(now);f.player.onUpdateWalkingPlayer();
            assertTrue(rotation.isMotionAccepted());while(f.channel.readOutbound()!=null) { }
        }
        public void close() { module.onDisable(); }
    }
    private float[] path(boolean visible,Consumer<Module> settings) throws Exception {
        try(Setup s=new Setup(visible,settings)) {
            float[] out=new float[32];
            for(int i=0;i<16;i++) {
                s.f.player.rotationYaw=s.f.player.rotationPitch=0; // Explicit fresh manual input each step.
                s.tick(i*97_000_000L);
                out[i*2]=s.rotation.getEffectiveYaw();out[i*2+1]=s.rotation.getEffectivePitch();
                assertEquals("step "+i+" / "+s.module.getModeSetting(visible?"Rotations":"Rot Mode").getValue(),visible?"aimassist":"aura",s.rotation.getConfirmedOwner());
                if(visible) { assertEquals(out[i*2],s.f.player.rotationYaw,0);assertEquals(out[i*2+1],s.f.player.rotationPitch,0); }
                else { assertEquals(0,s.f.player.rotationYaw,0);assertEquals(0,s.f.player.rotationPitch,0); }
            }
            return out;
        }
    }
    private void different(float[] a,float[] b) { double sum=0;for(int i=0;i<a.length;i++)sum+=Math.abs(a[i]-b[i]);assertTrue("setting must change the executed module path",sum>1e-4); }
    private void number(Module m,String name,double value) { m.getNumberSetting(name).setValue(value); }
    private void mode(Module m,String name,String value) { m.getModeSetting(name).setValue(value); }
    @Test public void everyRotationAndNoiseModeReachesVisibleAndSilentConsumers() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            List<float[]> styles=new ArrayList<>(),noise=new ArrayList<>(),fractals=new ArrayList<>();
            for(String style:visible?new String[]{"Legit","Test","Normal"}:new String[]{"Basic","Smooth","Advanced"})
                styles.add(path(visible,m->mode(m,visible?"Rotations":"Rot Mode",style)));
            for(FastNoiseLite.NoiseType type:FastNoiseLite.NoiseType.values())noise.add(path(visible,m->mode(m,"Noise Mode",type.name())));
            for(FastNoiseLite.FractalType type:FastNoiseLite.FractalType.values())fractals.add(path(visible,m->mode(m,"Fractal Mode",type.name())));
            for(List<float[]> paths:Arrays.asList(styles,noise,fractals))for(int i=0;i<paths.size();i++)for(int j=0;j<i;j++)different(paths.get(i),paths.get(j));
        }
    }
    private void setting(boolean visible,Consumer<Module> context,String name,double a,double b) throws Exception {
        try { different(path(visible,m->{context.accept(m);number(m,name,a);}),path(visible,m->{context.accept(m);number(m,name,b);})); }
        catch(AssertionError error) { throw new AssertionError((visible?"visible ":"silent ")+name,error); }
    }
    @Test public void everyNumericNoiseSettingChangesItsProductiveModulePath() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            Consumer<Module> plain=m->{};
            for(String name:new String[]{"Yaw Speed","Pitch Speed","Jitter","Noise Strength","Yaw Offset","Pitch Offset","Frequency","Speed Variation"})
                setting(visible,plain,name,name.endsWith("Speed")?1:0.1,name.equals("Noise Strength")?1:name.equals("Speed Variation")?.8:4);
            setting(visible,m->m.getBooleanSetting("Random Hit Vec").setValue(true),"Hit Vec Radius",0,.4);
            Consumer<Module> advanced=m->mode(m,visible?"Rotations":"Rot Mode",visible?"Test":"Advanced");
            setting(visible,advanced,"Min Speed",3,20);setting(visible,advanced,"Max Speed",40,90);
            setting(visible,m->mode(m,visible?"Rotations":"Rot Mode",visible?"Legit":"Smooth"),"Interpolation",2,10);
            setting(visible,m->mode(m,"Noise Mode","Cellular"),"Cellular Jitter",.2,1.4);
            Consumer<Module> fractal=m->mode(m,"Fractal Mode","FBm");
            setting(visible,fractal,"Gain",.2,.8);setting(visible,fractal,"Lacunarity",1.2,3.1);setting(visible,fractal,"Octaves",2,5);setting(visible,fractal,"Weighted Strength",.1,.8);
            setting(visible,m->mode(m,"Fractal Mode","PingPong"),"Ping Pong Strength",1,3.5);
            setting(visible,m->mode(m,"Fractal Mode","DomainWarpProgressive"),"Domain Warp Amp",0,3);
            List<float[]> cellDistance=new ArrayList<>(),cellReturn=new ArrayList<>(),warps=new ArrayList<>();
            for(FastNoiseLite.CellularDistanceFunction type:FastNoiseLite.CellularDistanceFunction.values())cellDistance.add(path(visible,m->{mode(m,"Noise Mode","Cellular");mode(m,"Cellular Distance Mode",type.name());}));
            for(FastNoiseLite.CellularReturnType type:FastNoiseLite.CellularReturnType.values())cellReturn.add(path(visible,m->{mode(m,"Noise Mode","Cellular");mode(m,"Cellular Return Mode",type.name());}));
            for(FastNoiseLite.DomainWarpType type:FastNoiseLite.DomainWarpType.values())warps.add(path(visible,m->{mode(m,"Fractal Mode","DomainWarpIndependent");mode(m,"Domain Warp Mode",type.name());}));
            for(List<float[]> paths:Arrays.asList(cellDistance,cellReturn,warps))for(int i=0;i<paths.size();i++)for(int j=0;j<i;j++)different(paths.get(i),paths.get(j));
        }
    }
    @Test public void zeroStrengthOffAndDisabledRandomizationMatchTheDefinedBasePath() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            float[] base=path(visible,m->m.getBooleanSetting("Randomization").setValue(false));
            Consumer<Module> extreme=m->{m.getBooleanSetting("Random Hit Vec").setValue(true);number(m,"Hit Vec Radius",5);number(m,"Jitter",5);number(m,"Yaw Offset",5);number(m,"Pitch Offset",5);};
            assertArrayEquals(base,path(visible,m->{extreme.accept(m);number(m,"Noise Strength",0);}),0);
            assertArrayEquals(base,path(visible,m->{extreme.accept(m);mode(m,"Noise Mode","Aus");}),0);
            assertArrayEquals(base,path(visible,m->{extreme.accept(m);m.getBooleanSetting("Randomization").setValue(false);}),0);
            float[] yaw=path(visible,m->number(m,"Yaw Offset",3)),pitch=path(visible,m->number(m,"Pitch Offset",3)),normal=path(visible,m->{});
            for(int i=0;i<16;i++) { assertEquals(normal[i*2+1],yaw[i*2+1],0);assertEquals(normal[i*2],pitch[i*2],0); }
        }
    }
    @Test public void visibleAssistPreservesLaterMouseInputAndCannotCompeteWithOwnersOrPendingRequests() throws Exception {
        float baselineYaw=path(true,m->m.getBooleanSetting("Randomization").setValue(false))[0];
        try(Setup s=new Setup(true,m->m.getBooleanSetting("Randomization").setValue(false))) {
            s.rotation.bindConnection(s.f.network.getClientPackets());s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);
            EventMotion event=new EventMotion(.5,64,.5,0,0,true);Pulsar.BUS.post(event);
            s.f.player.rotationYaw=7;s.f.player.rotationPitch=2;s.rotation.resolve(event);
            assertEquals(baselineYaw+7,s.f.player.rotationYaw,1e-4);assertEquals(s.f.player.rotationYaw,event.getYaw(),0);
            assertTrue(s.f.player.rotationPitch>2);
            for(String owner:new String[]{"aura","scaffold"}) {
                s.f.player.rotationYaw=0;s.f.player.rotationPitch=0;s.f.accept(owner,90);s.tick(s.clock.addAndGet(50_000_000L));
                assertEquals(0,s.f.player.rotationYaw,0);assertEquals(0,s.f.player.rotationPitch,0);
            }
            s.rotation.reset();s.rotation.bindConnection(s.f.network.getClientPackets());
            s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);Pulsar.BUS.post(event);
            s.rotation.request("scaffold",30,90,70);s.rotation.resolve(event);
            assertEquals(0,s.f.player.rotationYaw,0);assertEquals(90,event.getYaw(),0);
            s.rotation.reset();s.rotation.bindConnection(s.f.network.getClientPackets());
            s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);Pulsar.BUS.post(new EventMotion(.5,64,.5,0,0,true));
            s.f.mc.theWorld.loadedEntityList.clear();
            s.rotation.resolve(new EventMotion(.5,64,.5,0,0,true));assertEquals(0,s.f.player.rotationYaw,0);
        }
    }
    @Test public void focusMenusAndInvalidTargetsNeverChangeTheCamera() throws Exception {
        try(Setup s=new Setup(true,m->{})) {
            s.f.mc.inGameHasFocus=false;s.tick(0);assertEquals(0,s.f.player.rotationYaw,0);
            s.f.mc.inGameHasFocus=true;s.f.mc.currentScreen=new GuiScreen(){};s.tick(50_000_000);assertEquals(0,s.f.player.rotationYaw,0);
            s.f.mc.currentScreen=null;s.target.isDead=true;s.tick(100_000_000);assertEquals(0,s.f.player.rotationYaw,0);
            s.target.isDead=false;s.tick(150_000_000);assertTrue(s.f.player.rotationYaw>0);
        }
    }
    @Test public void dependentValuesAreVisibleOnlyForTheirActualAlgorithms() throws Exception {
        try(Setup s=new Setup(true,m->{})) {
            assertFalse(s.module.getValueByName("Min Speed").isVisible());assertFalse(s.module.getValueByName("Interpolation").isVisible());
            mode(s.module,"Rotations","Test");assertTrue(s.module.getValueByName("Min Speed").isVisible());assertTrue(s.module.getValueByName("Interpolation").isVisible());
            assertFalse(s.module.getValueByName("Cellular Jitter").isVisible());mode(s.module,"Noise Mode","Cellular");assertTrue(s.module.getValueByName("Cellular Jitter").isVisible());
            mode(s.module,"Fractal Mode","DomainWarpIndependent");assertTrue(s.module.getValueByName("Domain Warp Amp").isVisible());assertFalse(s.module.getValueByName("Weighted Strength").isVisible());
            number(s.module,"Noise Strength",0);assertFalse(s.module.getValueByName("Frequency").isVisible());assertFalse(s.module.getValueByName("Domain Warp Amp").isVisible());
        }
    }
    @Test public void namedPresetsAreDistinctWithOneStrengthControlAndDeterministicOff() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            List<float[]> paths=new ArrayList<>();
            for(String style:new String[]{"Aus","Sanft","Organisch","Dynamisch"}) {
                paths.add(path(visible,m->mode(m,"Randomization Style",style)));
                assertArrayEquals(paths.get(0),path(visible,m->{mode(m,"Randomization Style",style);number(m,"Noise Strength",0);}),0);
            }
            for(int i=0;i<paths.size();i++)for(int j=0;j<i;j++)different(paths.get(i),paths.get(j));
        }
        try(Setup s=new Setup(true,m->mode(m,"Randomization Style","Organisch"))) {
            assertTrue(s.module.getValueByName("Noise Strength").isVisible());
            assertFalse(s.module.getValueByName("Frequency").isVisible());assertFalse(s.module.getValueByName("Cellular Jitter").isVisible());
        }
    }
    @Test public void presetTimingControlsTheActualPathWithoutHidingIndependentSpeedValues() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            Consumer<Module> smooth=m->{mode(m,visible?"Rotations":"Rot Mode",visible?"Legit":"Smooth");mode(m,"Randomization Style","Sanft");};
            different(path(visible,m->{smooth.accept(m);number(m,"Interpolation",2);}),path(visible,m->{smooth.accept(m);number(m,"Interpolation",15);}));
            assertArrayEquals(path(visible,m->{smooth.accept(m);m.getBooleanSetting("Preset Timing").setValue(true);number(m,"Interpolation",2);}),
                    path(visible,m->{smooth.accept(m);m.getBooleanSetting("Preset Timing").setValue(true);number(m,"Interpolation",15);}),0);
            try(Setup s=new Setup(visible,smooth)) {
                assertTrue(s.module.getValueByName("Interpolation").isVisible());s.module.getBooleanSetting("Preset Timing").setValue(true);
                assertFalse(s.module.getValueByName("Interpolation").isVisible());
            }
        }
    }
    @Test public void stopOnTargetFreezesOnlyARealHitFromTheVisibleOrPlannedPose() throws Exception {
        for(boolean visible:new boolean[]{true,false})try(Setup s=new Setup(visible,m->{m.getBooleanSetting("Stop on Target").setValue(true);number(m,"Jitter",5);})) {
            float[] aim=s.rotation.rotationsTo(s.target,new net.minecraft.util.Vec3(-1,65,2));
            if(visible){s.f.player.rotationYaw=aim[0];s.f.player.rotationPitch=aim[1];}
            else {s.rotation.bindConnection(s.f.network.getClientPackets());s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);s.rotation.request("aura",20,aim[0],aim[1]);s.rotation.finishMotion(aim[0],aim[1],true);}
            for(int i=0;i<4;i++){s.tick(i*50_000_000L);assertEquals(aim[0],s.rotation.getEffectiveYaw(),1e-5);assertEquals(aim[1],s.rotation.getEffectivePitch(),1e-5);assertEquals(visible?aim[0]:0,s.f.player.rotationYaw,1e-5);}
            s.module.getBooleanSetting("Stop on Target").setValue(false);s.tick(200_000_000L);
            assertTrue(Math.abs(s.rotation.getEffectiveYaw()-aim[0])+Math.abs(s.rotation.getEffectivePitch()-aim[1])>.01);
        }
    }
    @Test public void smartAimFindsAnUnobstructedPointAndRejectsAnEntirelyOccludedTarget() throws Exception {
        for(boolean visible:new boolean[]{true,false})try(Setup s=new Setup(visible,m->{mode(m,"Randomization Style","Aus");m.getBooleanSetting("Smart Aim").setValue(true);m.getBooleanSetting(visible?"Best Hit Vec":"Best Aim Vec").setValue(true);})) {
            ScaffoldClientFlowTest.Scene scene=(ScaffoldClientFlowTest.Scene)s.f.mc.theWorld;
            scene.setBlockState(new net.minecraft.util.BlockPos(-1,65,1),net.minecraft.init.Blocks.stone.getDefaultState(),3);
            float[] blocked=s.rotation.rotationsTo(s.target,new net.minecraft.util.Vec3(-.73,65.62,1.73));
            RaycastService rays=new RaycastService(s.f.mc);assertFalse(rays.canHitFrom(new net.minecraft.util.Vec3(.5,64,.5),s.target,blocked[0],blocked[1],4,false));
            s.tick(0);assertEquals(visible?"aimassist":"aura",s.rotation.getConfirmedOwner());
            assertTrue("Smart Aim must reach a real unblocked hit",rays.canHit(s.target,s.rotation.getEffectiveYaw(),s.rotation.getEffectivePitch(),4,false));
            for(int x=-3;x<=1;x++)for(int y=64;y<=67;y++)scene.setBlockState(new net.minecraft.util.BlockPos(x,y,1),net.minecraft.init.Blocks.stone.getDefaultState(),3);
            float yaw=s.f.player.rotationYaw,pitch=s.f.player.rotationPitch;s.tick(50_000_000L);
            assertNull(s.rotation.getConfirmedOwner());assertEquals(yaw,s.f.player.rotationYaw,0);assertEquals(pitch,s.f.player.rotationPitch,0);
            assertNull(visible?((AimAssist)s.module).getTarget():RotationBehaviorHookTest.field(Aura.class,"target").get(s.module));
        }
    }
    @Test public void fovSwitchLossAndOwnershipHandoffsUseTheRealVisibleModule() throws Exception {
        try(Setup s=new Setup(true,m->{number(m,"FOV",10);mode(m,"Randomization Style","Aus");})) {
            s.tick(0);assertNull(((AimAssist)s.module).getTarget());assertEquals(0,s.f.player.rotationYaw,0);
            number(s.module,"FOV",360);s.tick(50_000_000L);assertSame(s.target,((AimAssist)s.module).getTarget());assertTrue(s.f.player.rotationYaw>0);
            RotationBehaviorHookTest.Player other=RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);other.worldObj=s.f.mc.theWorld;other.posX=1;other.posY=64;other.posZ=1;
            other.setEntityBoundingBox(new AxisAlignedBB(.7,64,.7,1.3,65.8,1.3));s.f.mc.theWorld.loadedEntityList.add(other);
            s.tick(100_000_000L);assertSame(other,((AimAssist)s.module).getTarget());assertTrue(s.f.player.rotationYaw<0);
            s.f.mc.theWorld.loadedEntityList.clear();float yaw=s.f.player.rotationYaw;s.tick(150_000_000L);assertNull(((AimAssist)s.module).getTarget());assertEquals(yaw,s.f.player.rotationYaw,0);
            s.f.mc.theWorld.loadedEntityList.add(s.target);s.tick(200_000_000L);assertSame(s.target,((AimAssist)s.module).getTarget());
            for(String owner:new String[]{"aura","scaffold"}){s.f.accept(owner,90);yaw=s.f.player.rotationYaw;s.tick(s.clock.addAndGet(50_000_000));assertNull(((AimAssist)s.module).getTarget());assertEquals(yaw,s.f.player.rotationYaw,0);s.rotation.reset();}
            s.tick(400_000_000);assertSame(s.target,((AimAssist)s.module).getTarget());assertEquals("aimassist",s.rotation.getConfirmedOwner());
        }
    }
    @Test public void historicalAngularStrategiesReachBothConsumersWithIndependentAxisAndZeroControls() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            List<float[]> paths=new ArrayList<>();
            for(String pattern:new String[]{"Noise","Basic","OnlyRotation","Doubled"})paths.add(path(visible,m->mode(m,"Random Mode",pattern)));
            for(int i=0;i<paths.size();i++)for(int j=0;j<i;j++)different(paths.get(i),paths.get(j));
            for(String pattern:new String[]{"Basic","OnlyRotation","Doubled"}) {
                Consumer<Module> selected=m->{mode(m,"Random Mode",pattern);mode(m,"Noise Mode","Aus");number(m,"Jitter",0);};
                float[] base=path(visible,m->{selected.accept(m);number(m,"Noise Strength",0);});
                assertArrayEquals(base,path(visible,m->{selected.accept(m);number(m,"Yaw Offset",5);number(m,"Pitch Offset",5);number(m,"Noise Strength",0);}),0);
                if(!pattern.equals("OnlyRotation")) {
                    float[] yaw=path(visible,m->{selected.accept(m);number(m,"Yaw Offset",3);}),pitch=path(visible,m->{selected.accept(m);number(m,"Pitch Offset",3);});
                    different(base,yaw);different(base,pitch);
                    for(int i=0;i<16;i++){assertEquals(base[i*2+1],yaw[i*2+1],0);assertEquals(base[i*2],pitch[i*2],0);}
                    setting(visible,m->{mode(m,"Random Mode",pattern);mode(m,"Noise Mode","Aus");},"Frequency",.4,3);
                }
            }
        }
    }
    @Test public void onlyRotationSuppressesAngularNoiseNearTargetButMatchesBasicDuringAcquisition() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            Consumer<Module> far=m->{number(m,"Yaw Speed",2);number(m,"Pitch Speed",90);};
            float[] basic=path(visible,m->{far.accept(m);mode(m,"Random Mode","Basic");}),only=path(visible,m->{far.accept(m);mode(m,"Random Mode","OnlyRotation");});
            assertEquals(basic[0],only[0],0);assertEquals(basic[1],only[1],0);
            try(Setup s=new Setup(visible,m->{mode(m,"Random Mode","OnlyRotation");mode(m,"Noise Mode","Aus");number(m,"Jitter",5);})) {
                float[] aim=s.rotation.rotationsTo(s.target,new net.minecraft.util.Vec3(s.target.posX,s.target.posY+s.target.getEyeHeight()*.82,s.target.posZ));
                if(visible){s.f.player.rotationYaw=aim[0];s.f.player.rotationPitch=aim[1];}
                else {s.rotation.bindConnection(s.f.network.getClientPackets());s.rotation.beginMotion(s.f.player,s.f.mc.theWorld,0,0);s.rotation.request("aura",20,aim[0],aim[1]);s.rotation.finishMotion(aim[0],aim[1],true);}
                s.tick(0);assertEquals(aim[0],s.rotation.getEffectiveYaw(),.0001);assertEquals(aim[1],s.rotation.getEffectivePitch(),.0001);
                mode(s.module,"Random Mode","Basic");s.tick(50_000_000L);assertTrue(Math.abs(s.rotation.getEffectiveYaw()-aim[0])+Math.abs(s.rotation.getEffectivePitch()-aim[1])>.01);
            }
        }
    }
    @Test public void heuristicInterpolationAndAdvancedPitchAreAppliedRatherThanDiscarded() throws Exception {
        for(boolean visible:new boolean[]{true,false}) {
            Consumer<Module> plain=m->mode(m,"Randomization Style","Aus");float[] base=path(visible,plain);
            different(base,path(visible,m->{plain.accept(m);m.getBooleanSetting("Heuristics").setValue(true);}));
            different(base,path(visible,m->{plain.accept(m);m.getBooleanSetting("Interpolation Toggle").setValue(true);}));
            setting(visible,m->{plain.accept(m);m.getBooleanSetting("Interpolate Rotation").setValue(true);},"Interpolation",2,12);
            // The historical sine has a node at 45 degrees; use an oblique acquisition angle.
            Consumer<Module> oblique=m->{plain.accept(m);net.minecraft.entity.Entity entity=shared.mc.theWorld.loadedEntityList.get(0);entity.posX=-.5;entity.setEntityBoundingBox(entity.getEntityBoundingBox().offset(.5,0,0));};
            float[] uncoupled=path(visible,oblique),coupled=path(visible,m->{oblique.accept(m);m.getBooleanSetting("AdvancedRots").setValue(true);});different(uncoupled,coupled);
            for(int i=0;i<16;i++)assertEquals("Pitch coupling must leave yaw unchanged",uncoupled[i*2],coupled[i*2],0);
            try(Setup s=new Setup(visible,m->{plain.accept(m);m.getBooleanSetting("Heuristics").setValue(true);m.getBooleanSetting("Interpolate Rotation").setValue(true);m.getBooleanSetting("Advanced Pitch").setValue(true);})) {
                for(int i=0;i<5;i++){s.tick(i*50_000_000L);assertEquals(.5,s.f.mc.gameSettings.mouseSensitivity,0);assertEquals(visible?"aimassist":"aura",s.rotation.getConfirmedOwner());}
            }
        }
    }
    @Test public void stopOnTargetNeverTrustsAStaleMouseOverResultAndAllowsExplicitWallPolicy() throws Exception {
        for(boolean visible:new boolean[]{true,false})try(Setup s=new Setup(visible,m->{m.getBooleanSetting("Stop on Target").setValue(true);mode(m,"Randomization Style","Aus");})) {
            s.f.mc.objectMouseOver=new net.minecraft.util.MovingObjectPosition(s.target);s.tick(0);
            assertNotEquals("An old mouse-over result must not prevent aiming",0,s.rotation.getEffectiveYaw(),0);
            s.module.getBooleanSetting("Smart Aim").setValue(true);ScaffoldClientFlowTest.Scene scene=(ScaffoldClientFlowTest.Scene)s.f.mc.theWorld;
            for(int x=-3;x<=1;x++)for(int y=64;y<=67;y++)scene.setBlockState(new net.minecraft.util.BlockPos(x,y,1),net.minecraft.init.Blocks.stone.getDefaultState(),3);
            s.tick(50_000_000L);assertNull(s.rotation.getConfirmedOwner());
            s.module.getBooleanSetting("Through Walls").setValue(true);s.tick(100_000_000L);assertEquals(visible?"aimassist":"aura",s.rotation.getConfirmedOwner());
        }
    }
    @Test public void ordinaryTickMaintenanceInvalidatesTargetsWhileMotionStepsAreWithheld() throws Exception {
        try(Setup s=new Setup(true,m->mode(m,"Randomization Style","Aus"))) {
            s.tick(0);assertSame(s.target,((AimAssist)s.module).getTarget());
            s.f.mc.theWorld.loadedEntityList.clear();Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
            assertTrue("A removed target must clear without another player motion step",((AimAssist)s.module).getTarget()==null);assertNull(s.rotation.getConfirmedOwner());
            s.f.mc.theWorld.loadedEntityList.add(s.target);s.tick(50_000_000);s.target.isDead=true;Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
            assertNull(((AimAssist)s.module).getTarget());assertNull(CombatTargetTracker.getPrimaryTarget());
        }
    }
    @Test public void lostAuraTargetsReleaseMovementAndJumpBeforeAnotherMotionStep() throws Exception {
        for(int loss=0;loss<4;loss++)try(Setup s=new Setup(false,m->{
            mode(m,"Randomization Style","Aus");mode(m,"movefix","Rotationsgebunden");
        })) {
            s.tick(0);assertEquals("aura",s.rotation.getConfirmedOwner());
            if(loss==0)s.f.mc.theWorld.loadedEntityList.clear();
            if(loss==1)s.target.isDead=true;
            if(loss==2)s.target.setEntityBoundingBox(new AxisAlignedBB(20,64,20,21,66,21));
            if(loss==3)s.target.worldObj=null;
            // Minecraft posts TickEvent before local entity movement and its later PRE/POST pair.
            Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
            assertNull("Lost target must release its accepted rotation before movement",s.rotation.getConfirmedOwner());
            assertNull(CombatTargetTracker.getPrimaryTarget());
            s.f.player.rotationYaw=0;s.f.player.motionX=s.f.player.motionZ=0;
            s.f.player.moveFlying(0,1,.1f);
            assertEquals(0,s.f.player.motionX,3e-5);assertEquals(.1,s.f.player.motionZ,3e-5);
            s.f.player.sprint=true;s.f.player.motionX=s.f.player.motionZ=0;s.f.player.jump();
            assertEquals(0,s.f.player.motionX,3e-5);assertEquals(.2,s.f.player.motionZ,3e-5);
            s.f.player.sprint=false;
        }
    }
    @Test public void invalidScaffoldSupportReleasesMovementBeforeAnotherMotionStep() throws Exception {
        RotationBehaviorHookTest.Fixture f=shared;
        f.player.usingItem=false;f.mc.inGameHasFocus=true;f.mc.currentScreen=null;
        f.player.rotationYaw=f.player.rotationPitch=0;
        for(Value<?> value:f.scaffold.getValues())value.reset();
        ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.48);
        mode(f.scaffold,"movefix","Rotationsgebunden");mode(f.scaffold,"Randomization Style","Aus");
        f.scaffold.getNumberSetting("Yaw Speed").setValue(1D);
        f.scaffold.getNumberSetting("Pitch Speed").setValue(1D);
        f.scaffold.onEnable();
        try {
            f.player.onUpdateWalkingPlayer();
            assertEquals("scaffold",f.runtime.getRotationManager().getConfirmedOwner());
            scene.setBlockState(new net.minecraft.util.BlockPos(-1,63,0),net.minecraft.init.Blocks.air.getDefaultState(),3);
            Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
            assertNull("Lost support must release the accepted placement rotation before movement",f.runtime.getRotationManager().getConfirmedOwner());
            f.player.motionX=f.player.motionZ=0;f.player.moveFlying(0,1,.1f);
            assertEquals(0,f.player.motionX,3e-5);assertEquals(.1,f.player.motionZ,3e-5);
            assertEquals(64,f.player.inventory.mainInventory[3].stackSize);
        } finally { f.scaffold.onDisable(); }
    }
    @Test public void scaffoldPresetsKeepPointsOnTheFaceAndPlaceThroughTheNativeController() throws Exception {
        RotationBehaviorHookTest.Fixture f=shared;
        Map<String,List<float[]>> traces=new LinkedHashMap<>();
        traces.put("Pre",new ArrayList<>());traces.put("Post",new ArrayList<>());
        for(String style:new String[]{"Aus","Sanft","Organisch","Dynamisch"}) for(String timing:new String[]{"Pre","Post"}) {
            f.mc.inGameHasFocus=true;f.mc.currentScreen=null;f.player.usingItem=false;
            f.player.rotationYaw=f.player.rotationPitch=0;
            for(Value<?> value:f.scaffold.getValues()) value.reset();
            ScaffoldClientFlowTest.Scene scene=ScaffoldClientFlowTest.prepare(f,.5,.48);
            mode(f.scaffold,"Randomization Style",style);mode(f.scaffold,"Place Timing",timing);
            f.scaffold.onEnable();int placements=0;float firstYaw=0,firstPitch=0,hitY=0,hitZ=0;
            try {
                for(int tick=0;tick<24 && !scene.getBlockState(new net.minecraft.util.BlockPos(0,63,0)).getBlock().isFullBlock();tick++) {
                    scene.motionTime.addAndGet(50_000_000L);f.runtime.getScaffoldPlacementService().beginTick();f.player.onUpdateWalkingPlayer();
                    if(tick==0) { firstYaw=f.runtime.getRotationManager().getEffectiveYaw();firstPitch=f.runtime.getRotationManager().getEffectivePitch(); }
                    Object outgoing;while((outgoing=f.channel.readOutbound())!=null) if(outgoing instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement) {
                        net.minecraft.network.play.client.C08PacketPlayerBlockPlacement packet=(net.minecraft.network.play.client.C08PacketPlayerBlockPlacement)outgoing;
                        placements++;hitY=packet.getPlacedBlockOffsetY();hitZ=packet.getPlacedBlockOffsetZ();
                        assertEquals(net.minecraft.util.EnumFacing.EAST.getIndex(),packet.getPlacedBlockDirection());
                        assertEquals(1,packet.getPlacedBlockOffsetX(),1e-6);
                        assertTrue(packet.getPlacedBlockOffsetY()>0 && packet.getPlacedBlockOffsetY()<1);
                        assertTrue(packet.getPlacedBlockOffsetZ()>0 && packet.getPlacedBlockOffsetZ()<1);
                    }
                }
                assertTrue(style+"/"+timing,scene.getBlockState(new net.minecraft.util.BlockPos(0,63,0)).getBlock().isFullBlock());
                assertEquals(1,placements);assertEquals(0,f.player.rotationYaw,0);
                traces.get(timing).add(new float[]{firstYaw,firstPitch,hitY,hitZ});
                if(style.equals("Organisch") || style.equals("Dynamisch")) {
                    f.player.posX=1.5;f.player.setEntityBoundingBox(new AxisAlignedBB(1.2,64,.2,1.8,65.8,.8));
                    boolean second=false;
                    for(int tick=0;tick<24 && !second;tick++) {
                        scene.motionTime.addAndGet(50_000_000L);f.runtime.getScaffoldPlacementService().beginTick();f.player.onUpdateWalkingPlayer();
                        Object sent;while((sent=f.channel.readOutbound())!=null) if(sent instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement) {
                            net.minecraft.network.play.client.C08PacketPlayerBlockPlacement packet=(net.minecraft.network.play.client.C08PacketPlayerBlockPlacement)sent;
                            assertTrue("successive plan must acquire a fresh bounded point",Math.abs(hitY-packet.getPlacedBlockOffsetY())+Math.abs(hitZ-packet.getPlacedBlockOffsetZ())>1e-5);
                            second=true;
                        }
                    }
                    assertTrue("straight bridge must continue onto the next full block",second);
                    assertTrue(scene.getBlockState(new net.minecraft.util.BlockPos(1,63,0)).getBlock().isFullBlock());
                }
            } finally { f.scaffold.onDisable(); }
        }
    }

}
