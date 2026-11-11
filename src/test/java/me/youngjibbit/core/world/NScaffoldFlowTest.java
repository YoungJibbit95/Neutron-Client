package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.NScaffold;
import me.zero.alpine.event.EventPhase;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.Assert.*;

/** Real module, visible rotation arbitration and placement service; synthetic world/controller boundary. */
public class NScaffoldFlowTest {
    private static Field field(Class<?> type,String name)throws Exception {
        Field value=type.getDeclaredField(name);value.setAccessible(true);return value;
    }
    private static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
        int lateRayCountdown;
        Runnable afterLateRay=()->{};
        final ScaffoldPlacementTest.WorldAccess world=new ScaffoldPlacementTest.WorldAccess(){
            @Override public void selectSlot(int slot){super.selectSlot(slot);f.player.inventory.currentItem=slot;}
            @Override public MovingObjectPosition rayTrace(Vec3 from,Vec3 to) {
                MovingObjectPosition result=super.rayTrace(from,to);
                if(lateRayCountdown>0 && --lateRayCountdown==0)afterLateRay.run();
                return result;
            }
        };
        final ScaffoldPlacementService service=new ScaffoldPlacementService(world,()->world.time);
        final NScaffold module=new NScaffold();
        final Map<Class<? extends Module>,Module> modules;
        @SuppressWarnings("unchecked") Run()throws Exception {
            field(Module.class,"toggled").setBoolean(f.aura,false);field(Module.class,"toggled").setBoolean(f.scaffold,false);
            modules=(Map<Class<? extends Module>,Module>)field(ModuleManager.class,"modulesByClass").get(Pulsar.INSTANCE.getMm());
            modules.put(NScaffold.class,module);
            field(ClientRuntime.class,"scaffoldPlacementService").set(f.runtime,service);
            world.world=f.world;world.player=f.player;
            f.player.posX=.5;f.player.posY=64;f.player.posZ=.5;f.player.onGround=true;
            f.player.observeAcceptedPosition(.5,64,.5);f.player.movementInput=new MovementInput();
            f.player.inventory.mainInventory[3]=world.inventory[3];
            f.mc.gameSettings=RotationBehaviorHookTest.bare(GameSettings.class);
            f.mc.gameSettings.keyBindForward=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindBack=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindLeft=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindRight=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindJump=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindSneak=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindSprint=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindUseItem=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.keyBindAttack=RotationBehaviorHookTest.bare(KeyBinding.class);
            f.mc.gameSettings.mouseSensitivity=.5f;
            module.getNumberSetting("Turn Speed").setValue(180D);module.setToggled(true);
        }
        void tick(){tick(true,()->{});}
        void tick(boolean accepted,Runnable afterResolve) {
            f.runtime.getObservation().beginOuterTick();service.beginTick();world.time+=50;
            try {
                f.runtime.getObservation().observePlayerStep(f.player,()->{
                    RotationManager r=f.runtime.getRotationManager();r.bindConnection(f.network.getClientPackets());
                    r.beginMotion(f.player,f.mc.theWorld,f.player.rotationYaw,f.player.rotationPitch);
                    EventMotion event=new EventMotion(f.player.posX,f.player.posY,f.player.posZ,
                            f.player.rotationYaw,f.player.rotationPitch,true);
                    event.setEventPhase(EventPhase.PRE);
                    Pulsar.BUS.post(new EventRotation(event));r.resolve(event);afterResolve.run();
                    r.finishMotion(event.getYaw(),event.getPitch(),accepted);
                    event.setEventPhase(EventPhase.POST);event.setAccepted(accepted);Pulsar.BUS.post(event);
                    Pulsar.BUS.post(event); // A duplicate POST cannot consume a second placement.
                    return true;
                },step->step.getAsBoolean());
            } finally {f.runtime.getObservation().endOuterTick();}
        }
        @Override public void close()throws Exception {
            try{module.setToggled(false);}finally{f.close();}
        }
    }

    @Test public void acceptedVisibleTurnPlacesWithANormalVisibleSlotAndUnmodifiedMovementBasis()throws Exception {
        try(Run r=new Run()) {
            r.tick();assertEquals(1,r.world.clicks);assertEquals(3,r.world.selected);
            assertEquals(3,r.f.player.inventory.currentItem);assertEquals(1,r.world.swings);
            assertEquals(NScaffold.Status.BUILDING,r.module.getStatus());
            assertTrue(r.f.player.rotationPitch>60);assertTrue(Math.abs(r.f.player.rotationYaw)>40);
            assertEquals("nscaffold",r.f.runtime.getRotationManager().getConfirmedOwner());
            float yaw=r.f.player.rotationYaw;r.f.player.motionX=r.f.player.motionZ=0;
            r.f.player.moveFlying(0,1,.1f);
            assertEquals(-Math.sin(Math.toRadians(yaw))*.1,r.f.player.motionX,2e-5);
            assertEquals(Math.cos(Math.toRadians(yaw))*.1,r.f.player.motionZ,2e-5);
            assertEquals(0,r.f.player.movementInput.moveForward,0);assertEquals(0,r.f.player.movementInput.moveStrafe,0);
            assertFalse(r.f.mc.gameSettings.keyBindForward.isKeyDown());assertFalse(r.f.mc.gameSettings.keyBindUseItem.isKeyDown());
        }
    }
    @Test public void turnSpeedHasDirectImpactAndAnUnalignedOrRejectedFrameCannotPlace()throws Exception {
        try(Run r=new Run()) {
            r.module.getNumberSetting("Turn Speed").setValue(5D);r.tick();
            assertEquals(0,r.world.clicks);assertEquals(5,Math.hypot(r.f.player.rotationYaw,r.f.player.rotationPitch),.001);
            r.module.getNumberSetting("Turn Speed").setValue(180D);r.tick();assertEquals(1,r.world.clicks);
        }
        try(Run r=new Run()){r.tick(false,()->{});assertEquals(0,r.world.clicks);assertTrue(r.world.slots.isEmpty());}
    }
    @Test public void oldScaffoldAndActiveAuraYieldBeforeAnySlotOrControllerAction()throws Exception {
        try(Run r=new Run()) {
            field(Module.class,"toggled").setBoolean(r.f.scaffold,true);r.tick();
            assertEquals(NScaffold.Status.PAUSED_SCAFFOLD,r.module.getStatus());assertEquals(0,r.world.clicks);
            assertEquals(0,r.f.player.rotationYaw,0);assertTrue(r.world.slots.isEmpty());
        }
        try(Run r=new Run()) {
            TargetEspLifecycleTest.CallbackAura aura=new TargetEspLifecycleTest.CallbackAura();
            aura.observed=RotationBehaviorHookTest.bare(TargetEspLifecycleTest.Candidate.class);
            field(Module.class,"toggled").setBoolean(aura,true);r.modules.put(Aura.class,aura);r.tick();
            assertEquals(NScaffold.Status.PAUSED_AURA,r.module.getStatus());assertEquals(0,r.world.clicks);
            assertTrue(r.world.slots.isEmpty());
        }
    }
    @Test public void slotSelectionCanBeDisabledAndManualSelectionDuringCallbackWins()throws Exception {
        try(Run r=new Run()) {
            r.module.getBooleanSetting("Auto Select Blocks").setValue(false);r.tick();
            assertEquals(0,r.world.clicks);assertTrue(r.world.slots.isEmpty());assertEquals(0,r.world.selected);
        }
        try(Run r=new Run()) {
            r.world.onClick=()->{r.world.selected=7;r.f.player.inventory.currentItem=7;};r.tick();
            assertEquals(0,r.world.clicks);assertEquals(7,r.world.selected);assertEquals(7,r.f.player.inventory.currentItem);
        }
    }
    @Test public void aChangedFreshInputCancelsThePlanWithoutEditingThatInput()throws Exception {
        try(Run r=new Run()) {
            MovementInputEvent changed=new MovementInputEvent(.3f,.3f,true,true);
            r.tick(true,()->Pulsar.BUS.post(changed));
            assertEquals(0,r.world.clicks);assertTrue(r.world.slots.isEmpty());
            assertEquals(.3f,changed.getForward(),0);assertEquals(.3f,changed.getStrafe(),0);
            assertTrue(changed.isJump());assertTrue(changed.isSneak());assertFalse(changed.isCancelled());
            assertEquals(NScaffold.Status.PAUSED_INPUT,r.module.getStatus());
        }
    }
    @Test public void lateDisableReenableCannotClickOrPublishTheOldGeneration()throws Exception {
        try(Run r=new Run()) {
            r.world.onClick=()->{r.module.setToggled(false);r.module.setToggled(true);};r.tick();
            assertTrue(r.module.isToggled());assertEquals(0,r.world.clicks);assertEquals(0,r.world.swings);
            assertEquals(NScaffold.Status.WAITING,r.module.getStatus());
            r.world.onClick=()->{};r.tick();assertEquals(1,r.world.clicks);
        }
    }
    @Test public void epochAndPositionChangesInsideTheControllerPreventTheAction()throws Exception {
        try(Run r=new Run()) {
            r.world.onClick=()->r.f.network.getClientPackets().worldChanged();r.tick();
            assertEquals(0,r.world.clicks);assertEquals(0,r.world.swings);
            assertEquals("no cross-epoch slot restore",3,r.world.selected);
        }
        try(Run r=new Run()) {
            r.world.onClick=()->r.f.player.posX+=1;r.tick();
            assertEquals(0,r.world.clicks);assertEquals(0,r.world.swings);
        }
    }
    @Test public void noFocusOrItemUsePausesAndSmartDisableDefaultsStayOptIn()throws Exception {
        try(Run r=new Run()) {
            assertFalse(r.module.getBooleanSetting("Smart Disable on Death").getValue());
            assertFalse(r.module.getBooleanSetting("Smart Disable on World Change").getValue());
            r.f.mc.inGameHasFocus=false;r.tick();assertEquals(0,r.world.clicks);
            r.f.mc.inGameHasFocus=true;r.f.player.usingItem=true;r.tick();
            assertEquals(NScaffold.Status.PAUSED_INPUT,r.module.getStatus());assertEquals(0,r.world.clicks);
        }
    }

    @Test public void forwardAndSidewaysOnlyInputPauseWithAnExplicitUserStatus()throws Exception {
        for(boolean forward:new boolean[]{true,false})try(Run r=new Run()) {
            field(KeyBinding.class,"pressed").setBoolean(forward?r.f.mc.gameSettings.keyBindForward:r.f.mc.gameSettings.keyBindLeft,true);
            r.tick();assertEquals(0,r.world.clicks);assertEquals(0,r.f.player.rotationYaw,0);
            assertEquals(NScaffold.Status.HOLD_BACK_TO_BRIDGE,r.module.getStatus());
            assertEquals("Hold back to bridge",r.module.info);assertTrue(r.world.slots.isEmpty());
        }
    }

    @Test public void finalClickRayCannotReviveAResetOwnerOrAnOldModuleGeneration()throws Exception {
        for(boolean reenable:new boolean[]{false,true})try(Run r=new Run()) {
            final boolean[] fired={false};final float[] visible={0,0};
            r.afterLateRay=()->{
                fired[0]=true;visible[0]=r.f.player.rotationYaw;visible[1]=r.f.player.rotationPitch;
                if(reenable){r.module.setToggled(false);r.module.setToggled(true);}
                else r.f.runtime.getRotationManager().reset(NScaffold.ROTATION_SOURCE);
            };
            // Arm only once the real click boundary is entered. The first ray is
            // valid(target); the second is the final effectiveHit in that gate.
            r.world.onClick=()->r.lateRayCountdown=2;
            r.tick();
            assertTrue("Final effectiveHit callback was not reached",fired[0]);
            assertEquals("A callback-revoked plan must not click",0,r.world.clicks);
            assertEquals(0,r.world.swings);assertEquals(64,r.world.inventory[3].stackSize);
            assertEquals(visible[0],r.f.player.rotationYaw,0);assertEquals(visible[1],r.f.player.rotationPitch,0);
            if(reenable)assertTrue(r.module.isToggled());
        }
    }

    @Test public void manualCameraTurnInsideTheControllerWinsWithoutAPlacementOrCameraRollback()throws Exception {
        try(Run r=new Run()) {
            final boolean[] fired={false};final float[] manual={0,0};
            r.world.onClick=()->{
                fired[0]=true;manual[0]=r.f.player.rotationYaw+90;manual[1]=r.f.player.rotationPitch-17;
                r.f.player.rotationYaw=manual[0];r.f.player.rotationPitch=manual[1];
            };
            r.tick();assertTrue(fired[0]);
            assertEquals(0,r.world.clicks);assertEquals(0,r.world.swings);assertEquals(64,r.world.inventory[3].stackSize);
            assertEquals(manual[0],r.f.player.rotationYaw,0);assertEquals(manual[1],r.f.player.rotationPitch,0);
        }
    }

    @Test public void manualCameraTurnAfterResolveCannotReuseThePreviousAcceptedRay()throws Exception {
        try(Run r=new Run()) {
            final float[] manual={0,0};
            r.tick(true,()->{
                manual[0]=r.f.player.rotationYaw+90;manual[1]=r.f.player.rotationPitch-17;
                r.f.player.rotationYaw=manual[0];r.f.player.rotationPitch=manual[1];
            });
            assertEquals(0,r.world.clicks);assertEquals(0,r.world.swings);assertTrue(r.world.slots.isEmpty());
            assertEquals(manual[0],r.f.player.rotationYaw,0);assertEquals(manual[1],r.f.player.rotationPitch,0);
        }
    }

    @Test public void manualCameraTurnsReleaseTheHeldHeadingUntilTheViewSettles()throws Exception {
        try(Run r=new Run()) {
            field(KeyBinding.class,"pressed").setBoolean(r.f.mc.gameSettings.keyBindBack,true);
            r.f.player.rotationYaw=90;r.f.player.rotationPitch=75.7f;
            r.tick();
            assertEquals("Establish an accepted held backward view first",1,r.world.clicks);
            assertEquals(1,r.world.swings);assertEquals(90,r.f.player.rotationYaw,0);
            BlockPos destination=new BlockPos(0,63,0);
            assertTrue(r.world.cells.containsKey(destination));
            r.world.cells.remove(destination);
            int selections=r.world.slots.size();

            for(float yaw:new float[]{120,130}) {
                r.f.player.rotationYaw=yaw;
                r.tick();
                assertEquals("Manual movement must not be rotated back to the cached heading",yaw,r.f.player.rotationYaw,0);
                assertEquals(75.7f,r.f.player.rotationPitch,0);
                assertEquals(1,r.world.clicks);assertEquals(1,r.world.swings);
                assertEquals(selections,r.world.slots.size());
                assertEquals("MANUAL_VIEW",r.module.getStatus().name());
                assertEquals("Manual view",r.module.info);
                assertFalse(r.world.cells.containsKey(destination));
                assertTrue(r.f.mc.gameSettings.keyBindBack.isKeyDown());
            }

            // The visible 130-degree ray still reaches the original west support.
            // A settled camera can resume from that new direction, never the old 90 degrees.
            r.tick();
            assertEquals(130,r.f.player.rotationYaw,0);assertEquals(75.7f,r.f.player.rotationPitch,0);
            assertEquals(2,r.world.clicks);assertEquals(2,r.world.swings);
            assertTrue(r.world.cells.containsKey(destination));
            assertEquals(NScaffold.Status.BUILDING,r.module.getStatus());
        }
    }
}
