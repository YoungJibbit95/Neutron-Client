package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.player.MovementInputEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.NScaffold;
import me.zero.alpine.event.EventPhase;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import static org.junit.Assert.*;

/** Real module, visible rotation arbitration and placement service; synthetic world/controller boundary. */
public class NScaffoldFlowTest {
    private static Field field(Class<?> type,String name)throws Exception {
        Field value=type.getDeclaredField(name);value.setAccessible(true);return value;
    }
    private static final class Run implements AutoCloseable {
        final float originalForward=MovementInput.moveForward,originalStrafe=MovementInput.moveStrafe;
        final RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture();
        int lateRayCountdown,rayReads,stackReads;
        Runnable afterLateRay=()->{};
        final ScaffoldPlacementTest.WorldAccess world=new ScaffoldPlacementTest.WorldAccess(){
            @Override public void selectSlot(int slot){super.selectSlot(slot);f.player.inventory.currentItem=slot;}
            @Override public ItemStack stack(int slot){stackReads++;return super.stack(slot);}
            @Override public MovingObjectPosition rayTrace(Vec3 from,Vec3 to) {
                rayReads++;
                MovingObjectPosition result=super.rayTrace(from,to);
                if(lateRayCountdown>0 && --lateRayCountdown==0)afterLateRay.run();
                return result;
            }
        };
        final ScaffoldPlacementService service=new ScaffoldPlacementService(world,()->world.time);
        final NScaffold module=new NScaffold();
        final Map<Class<? extends Module>,Module> modules;
        @SuppressWarnings("unchecked") Run()throws Exception {
            // These legacy axes are static, so a new MovementInput does not isolate a run.
            MovementInput.moveForward=0;MovementInput.moveStrafe=0;
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
        AtomicLong activity()throws Exception {
            Bootstrap.register();
            f.player.capabilities=new PlayerCapabilities();
            world.inventory[3]=new ItemStack(Blocks.planks,64,2);
            f.player.inventory.mainInventory[3]=world.inventory[3];
            f.player.inventoryContainer=new ContainerPlayer(f.player.inventory,false,f.player);
            f.player.openContainer=f.player.inventoryContainer;
            AtomicLong clock=new AtomicLong(1_000_000_000L);
            field(NScaffold.class,"activityClock").set(module,(LongSupplier)clock::get);
            return clock;
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
        void position(double x,double y,double z,boolean grounded) {
            f.player.posX=x;f.player.posY=y;f.player.posZ=z;f.player.onGround=grounded;
            world.feet=new Vec3(x,y,z);f.player.observeAcceptedPosition(x,y,z);
            f.player.setEntityBoundingBox(new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+1.8,z+.3));
        }
        void press(KeyBinding key,boolean pressed)throws Exception {field(KeyBinding.class,"pressed").setBoolean(key,pressed);}
        void clutch(boolean sneak) {
            module.getBooleanSetting("Clutch").setValue(true);
            module.getModeSetting("Clutch Criteria").setValue("Prediction");
            module.getBooleanSetting("Auto Sneak on Clutch").setValue(sneak);
        }
        void preparedTick(MovementInputEvent input){preparedTick(input,()->{},()->{},()->{});}
        void preparedTick(MovementInputEvent input,Runnable afterPrepare,Runnable afterInput,Runnable afterPost) {
            position(f.player.posX,f.player.posY,f.player.posZ,f.player.onGround);
            f.runtime.getObservation().beginOuterTick();service.beginTick();world.time+=50;
            try {
                f.runtime.getObservation().observePlayerStep(f.player,()->{
                    preparedStep(input,afterPrepare,afterInput);afterPost.run();return true;
                },step->step.getAsBoolean());
            } finally {f.runtime.getObservation().endOuterTick();}
        }
        /** Real prepareMovement/resolve/input ordering; synthetic physics and controller boundary. */
        void preparedStep(MovementInputEvent input,Runnable afterPrepare,Runnable afterInput) {
            RotationManager rotations=f.runtime.getRotationManager();
            rotations.prepareMovement(f.player);afterPrepare.run();
            if(input!=null) {
                Pulsar.BUS.post(input);
                if(!input.isCancelled()) {
                    f.player.movementInput.moveForward=input.getForward();f.player.movementInput.moveStrafe=input.getStrafe();
                    f.player.movementInput.jump=input.isJump();f.player.movementInput.sneak=input.isSneak();
                }
            }
            afterInput.run();rotations.beforeMovementPhysics();
            rotations.bindConnection(f.network.getClientPackets());
            if(!rotations.resumePreparedMovement(f.player,f.world))
                rotations.beginMotion(f.player,f.world,f.player.rotationYaw,f.player.rotationPitch);
            EventMotion event=new EventMotion(f.player.posX,f.player.posY,f.player.posZ,
                    f.player.rotationYaw,f.player.rotationPitch,f.player.onGround);
            event.setEventPhase(EventPhase.PRE);Pulsar.BUS.post(event);rotations.resolve(event);
            rotations.finishMotion(event.getYaw(),event.getPitch(),true);
            event.setEventPhase(EventPhase.POST);event.setAccepted(true);Pulsar.BUS.post(event);Pulsar.BUS.post(event);
        }
        @Override public void close()throws Exception {
            try{module.setToggled(false);}finally{
                try{f.close();}finally{
                    MovementInput.moveForward=originalForward;MovementInput.moveStrafe=originalStrafe;
                }
            }
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

    @Test public void activityCountsSuccessfulPlacementsAndReadingItNeverRunsTheBuilder()throws Exception {
        try(Run r=new Run()) {
            r.activity();r.world.success=false;r.tick();
            ScaffoldActivitySnapshot failed=r.module.getActivitySnapshot();
            assertNotNull(failed);assertEquals(0,failed.getPlacementCount());assertEquals(64,failed.getBlockCount());
            assertEquals(ScaffoldActivitySnapshot.Status.ALIGNING,failed.getStatus());
            assertEquals(1,r.world.clicks);assertEquals(0,r.world.swings);

            r.world.success=true;r.tick();
            ScaffoldActivitySnapshot placed=r.module.getActivitySnapshot();assertNotNull(placed);
            assertEquals(1,placed.getPlacementCount());assertEquals(63,placed.getBlockCount());
            assertEquals(Item.getIdFromItem(Item.getItemFromBlock(Blocks.planks)),placed.getItemId());
            assertEquals(2,placed.getMetadata());assertEquals("NScaffold",placed.getApproach());
            assertEquals("Standard",placed.getProfile());assertEquals(ScaffoldActivitySnapshot.Status.PLACING,placed.getStatus());
            int reads=r.stackReads,rays=r.rayReads,slots=r.world.slots.size();
            long motion=r.f.runtime.getRotationManager().getMotionSequence();
            float yaw=r.f.player.rotationYaw,pitch=r.f.player.rotationPitch;
            Object cache=field(NScaffold.class,"activityView").get(r.module);
            for(int i=0;i<100;i++)assertSame(placed,r.module.getActivitySnapshot());
            assertSame(cache,field(NScaffold.class,"activityView").get(r.module));
            assertEquals(reads,r.stackReads);assertEquals(rays,r.rayReads);assertEquals(slots,r.world.slots.size());
            assertEquals(2,r.world.clicks);assertEquals(1,r.world.swings);assertEquals(63,r.world.inventory[3].stackSize);
            assertEquals(yaw,r.f.player.rotationYaw,0);assertEquals(pitch,r.f.player.rotationPitch,0);
            assertEquals(motion,r.f.runtime.getRotationManager().getMotionSequence());
            assertEquals("nscaffold",r.f.runtime.getRotationManager().getConfirmedOwner());
        }
    }

    @Test public void activityPausesAndWaitingDoNotPretendToSearchOrPlace()throws Exception {
        try(Run r=new Run()) {
            r.activity();
            field(KeyBinding.class,"pressed").setBoolean(r.f.mc.gameSettings.keyBindForward,true);
            r.tick();ScaffoldActivitySnapshot paused=r.module.getActivitySnapshot();assertNotNull(paused);
            assertEquals(ScaffoldActivitySnapshot.Status.PAUSED,paused.getStatus());assertEquals(0,paused.getPlacementCount());
            assertEquals(64,paused.getBlockCount());assertEquals(0,r.world.clicks);
            field(KeyBinding.class,"pressed").setBoolean(r.f.mc.gameSettings.keyBindForward,false);
            r.module.getBooleanSetting("Auto Select Blocks").setValue(false);r.tick();
            ScaffoldActivitySnapshot waiting=r.module.getActivitySnapshot();assertNotNull(waiting);
            assertEquals(ScaffoldActivitySnapshot.Status.WAITING,waiting.getStatus());assertEquals(0,waiting.getPlacementCount());
            assertEquals(0,r.world.clicks);assertTrue(r.world.slots.isEmpty());
        }
    }

    @Test public void activityHeartbeatsRefreshTimeWithoutInventingProgressOrChangingFactSequence()throws Exception {
        try(Run r=new Run()) {
            AtomicLong clock=r.activity();r.tick();r.tick();
            ScaffoldActivitySnapshot first=r.module.getActivitySnapshot();assertNotNull(first);
            assertEquals(ScaffoldActivitySnapshot.Status.SEARCHING,first.getStatus());assertEquals(1,first.getPlacementCount());
            clock.addAndGet(50_000_000L);r.tick();
            ScaffoldActivitySnapshot second=r.module.getActivitySnapshot();assertNotNull(second);
            assertNotSame(first,second);assertEquals(first.getSequence(),second.getSequence());
            assertEquals(first.getPlacementCount(),second.getPlacementCount());assertEquals(1,r.world.clicks);
            assertEquals(clock.get(),second.getObservedAtNanos());
            Object cache=field(NScaffold.class,"activityView").get(r.module);
            clock.addAndGet(ScaffoldActivitySnapshot.TTL_NANOS+1);
            assertNull(r.module.getActivitySnapshot());assertSame(cache,field(NScaffold.class,"activityView").get(r.module));
            r.tick();assertNotNull(r.module.getActivitySnapshot());assertEquals(1,r.world.clicks);
        }
    }

    @Test public void activityRejectsMenuRoundTripsAndChangedConnectionOrWorldWithoutReaderMutation()throws Exception {
        try(Run r=new Run()) {
            r.activity();r.tick();assertNotNull(r.module.getActivitySnapshot());
            Object cache=field(NScaffold.class,"activityView").get(r.module);
            r.f.mc.currentScreen=RotationBehaviorHookTest.bare(GuiChat.class);
            assertNull(r.module.getActivitySnapshot());assertSame(cache,field(NScaffold.class,"activityView").get(r.module));
            r.f.runtime.getObservation().captureScene(); // The normal observer records entry into suspension.
            r.f.mc.currentScreen=null;
            assertNull("Closing a menu cannot revive a pre-suspension snapshot",r.module.getActivitySnapshot());
            r.tick();assertNotNull(r.module.getActivitySnapshot());
            r.f.mc.theWorld=RotationBehaviorHookTest.bare(net.minecraft.client.multiplayer.WorldClient.class);
            assertNull(r.module.getActivitySnapshot());
            r.f.runtime.getObservation().invalidateScene();r.f.mc.theWorld=r.f.world;
            assertNull("A -> B -> A cannot revive the old connection generation",r.module.getActivitySnapshot());
        }
        try(Run r=new Run()) {
            r.activity();r.tick();assertNotNull(r.module.getActivitySnapshot());
            r.f.network.getClientPackets().worldChanged();assertNull(r.module.getActivitySnapshot());
        }
    }

    @Test public void inventoryCorrectionsAndReentrantEnableCannotRepublishOldActivity()throws Exception {
        try(Run r=new Run()) {
            r.activity();r.tick();assertEquals(1,r.module.getActivitySnapshot().getPlacementCount());
            EventPacket packet=new EventPacket(new S2FPacketSetSlot(0,39,r.world.inventory[3]),r.f.network.getClientPackets());
            packet.setEventFlow(EventFlow.INBOUND);packet.setEventPhase(EventPhase.POST);Pulsar.BUS.post(packet);
            assertNull(r.module.getActivitySnapshot());r.tick();
            assertEquals(1,r.module.getActivitySnapshot().getPlacementCount());assertEquals(1,r.world.clicks);
            r.world.cells.remove(new BlockPos(0,63,0));
            r.world.onClick=()->{r.module.setToggled(false);r.module.setToggled(true);};r.tick();
            assertEquals(1,r.world.clicks);assertNull(r.module.getActivitySnapshot());
            r.world.onClick=()->{};r.tick();
            assertEquals(2,r.world.clicks);assertEquals(1,r.module.getActivitySnapshot().getPlacementCount());
        }
    }

    @Test public void reenableInsideThePublicationStackReadCannotPublishOrExecuteTheOldFrame()throws Exception {
        try(Run r=new Run()) {
            r.activity();final boolean[] armed={true},fired={false};
            InventoryPlayer observed=new InventoryPlayer(r.f.player){
                @Override public ItemStack getStackInSlot(int slot) {
                    if(slot==3 && armed[0]) {
                        armed[0]=false;fired[0]=true;
                        r.module.setToggled(false);r.module.setToggled(true);
                    }
                    return super.getStackInSlot(slot);
                }
            };
            observed.mainInventory[3]=r.world.inventory[3];r.f.player.inventory=observed;
            r.f.player.inventoryContainer=new ContainerPlayer(observed,false,r.f.player);
            r.f.player.openContainer=r.f.player.inventoryContainer;
            r.tick();
            assertTrue("The snapshot's original stack read must be exercised",fired[0]);
            assertTrue(r.module.isToggled());assertNull(r.module.getActivitySnapshot());
            assertEquals(NScaffold.Status.WAITING,r.module.getStatus());
            assertEquals(0,r.world.clicks);assertEquals(0,r.world.swings);assertTrue(r.world.slots.isEmpty());
            assertEquals(0,r.f.player.rotationYaw,0);assertEquals(0,r.f.player.rotationPitch,0);
            r.tick();assertEquals(1,r.world.clicks);assertEquals(1,r.module.getActivitySnapshot().getPlacementCount());
        }
    }

    @Test public void aGroundedForwardApproachCanSneakOnItsFirstClutchDespiteItsOwnVisibleTurn()throws Exception {
        try(Run r=new Run()) {
            r.clutch(true);r.press(r.f.mc.gameSettings.keyBindForward,true);
            r.world.cells.clear();r.world.block(new BlockPos(0,63,0));r.f.player.motionX=.1;
            assertEquals(5,r.module.getNumberSetting("Ground Ticks to Sneak").getValue().intValue());
            for(int tick=0;tick<4;tick++) {
                MovementInputEvent ordinary=new MovementInputEvent(0,1,false,false);
                r.preparedTick(ordinary);
                assertEquals(NScaffold.Status.HOLD_BACK_TO_BRIDGE,r.module.getStatus());
                assertFalse(ordinary.isSneak());assertEquals(0,r.world.clicks);
            }
            r.position(1.05,64,.5,true);
            MovementInputEvent edge=new MovementInputEvent(0,1,false,false);
            r.preparedTick(edge,()->{
                RotationManager.MovementRotation movement=r.f.runtime.getRotationManager().getPreparedMovement();
                assertNotNull("actual production preparation must exist",movement);
                assertEquals("nscaffold",movement.source);assertEquals(NScaffold.Status.CLUTCHING,r.module.getStatus());
                assertTrue("new clutch visibly turns away from the previously observed zero yaw",Math.abs(r.f.player.rotationYaw)>40);
                assertEquals(movement.yaw,r.f.player.rotationYaw,0);assertEquals(movement.pitch,r.f.player.rotationPitch,0);
            },()->{
                assertTrue("the fifth observed grounded tick meets the threshold",edge.isSneak());
                assertEquals(.3f,edge.getForward(),0);assertEquals(0,edge.getStrafe(),0);
                Pulsar.BUS.post(edge);
                assertEquals("reposting a sneaking sample must not apply 0.3 twice",.3f,edge.getForward(),0);
                assertTrue(r.f.mc.gameSettings.keyBindForward.isKeyDown());assertFalse(r.f.mc.gameSettings.keyBindSneak.isKeyDown());
                assertFalse(r.f.mc.gameSettings.keyBindJump.isKeyDown());assertFalse(r.f.mc.gameSettings.keyBindUseItem.isKeyDown());
            },()->{});
            assertEquals(1,r.world.clicks);assertEquals(1,r.world.swings);
            assertTrue(r.world.cells.containsKey(new BlockPos(1,63,0)));
        }
    }

    @Test public void clutchSneakYieldsToManualCameraJumpCancellationGuiAirAndChangedInput()throws Exception {
        for(int veto=0;veto<6;veto++)try(Run r=new Run()) {
            final int kind=veto;
            r.clutch(true);r.module.getNumberSetting("Ground Ticks to Sneak").setValue(0D);
            r.press(r.f.mc.gameSettings.keyBindForward,true);
            if(kind==1)r.press(r.f.mc.gameSettings.keyBindJump,true);
            r.position(.05,64,.5,true);r.f.player.motionX=.1;
            MovementInputEvent input=new MovementInputEvent(0,1,kind==1,false);
            r.preparedTick(input,()->{
                RotationManager.MovementRotation movement=r.f.runtime.getRotationManager().getPreparedMovement();
                assertNotNull(movement);assertEquals("nscaffold",movement.source);
                if(kind==0)r.f.player.rotationYaw+=17;
                if(kind==2)input.cancel();
                if(kind==3)r.f.mc.currentScreen=new GuiChat();
                if(kind==4)r.f.player.onGround=false;
                if(kind==5){input.setForward(0);input.setStrafe(1);}
            },()->{
                assertFalse("veto="+kind,input.isSneak());
                assertEquals(kind==5?0:1,input.getForward(),0);assertEquals(kind==5?1:0,input.getStrafe(),0);
                assertEquals(kind==1,input.isJump());assertEquals(kind==2,input.isCancelled());
                assertFalse(r.f.mc.gameSettings.keyBindSneak.isKeyDown());
                assertEquals(kind==1,r.f.mc.gameSettings.keyBindJump.isKeyDown());
                assertTrue(r.f.mc.gameSettings.keyBindForward.isKeyDown());
            },()->{});
            if(kind==0 || kind==2 || kind==3 || kind==5)assertEquals("revoked action, veto="+kind,0,r.world.clicks);
        }
    }

    @Test public void existingManualSneakKeepsItsSingleVanillaSlowdown()throws Exception {
        try(Run r=new Run()) {
            r.clutch(true);r.module.getNumberSetting("Ground Ticks to Sneak").setValue(0D);
            r.press(r.f.mc.gameSettings.keyBindForward,true);r.press(r.f.mc.gameSettings.keyBindSneak,true);
            r.position(.05,64,.5,true);r.f.player.motionX=.1;
            MovementInputEvent manual=new MovementInputEvent(0,.3f,false,true);
            r.preparedTick(manual);
            assertTrue(manual.isSneak());assertEquals(.3f,manual.getForward(),0);
            assertTrue(r.f.mc.gameSettings.keyBindSneak.isKeyDown());assertTrue(r.f.mc.gameSettings.keyBindForward.isKeyDown());
            assertEquals(1,r.world.clicks);
        }
    }

    @Test public void tellyUsesSeparateRealStraightAndDiagonalFlightWindows()throws Exception {
        for(boolean diagonal:new boolean[]{false,true})try(Run r=new Run()) {
            r.module.getModeSetting("Build Mode").setValue("Telly");
            r.press(r.f.mc.gameSettings.keyBindBack,true);
            if(diagonal)r.press(r.f.mc.gameSettings.keyBindLeft,true);
            r.f.player.rotationYaw=90;r.f.player.rotationPitch=75.7f;
            r.preparedTick(new MovementInputEvent(diagonal?1:0,-1,false,false));
            assertEquals(NScaffold.Status.TELLY_WAIT,r.module.getStatus());assertEquals(0,r.world.clicks);
            r.f.player.onGround=false;r.f.player.motionY=-.1;
            int required=diagonal?2:4;
            for(int airborne=1;airborne<required;airborne++) {
                r.preparedTick(new MovementInputEvent(diagonal?1:0,-1,false,false));
                assertEquals("airborne="+airborne,NScaffold.Status.TELLY_WAIT,r.module.getStatus());
                assertEquals(0,r.world.clicks);assertTrue(r.module.info.contains(airborne+" of "+required));
            }
            r.preparedTick(new MovementInputEvent(diagonal?1:0,-1,false,false));
            assertEquals(1,r.world.clicks);assertEquals(NScaffold.Status.BUILDING,r.module.getStatus());
            assertFalse(r.f.mc.gameSettings.keyBindJump.isKeyDown());
        }
    }

    @Test public void tellyKeepsTheObservedFlightWhenThePlayerChangesFromForwardToBack()throws Exception {
        try(Run r=new Run()) {
            r.module.getModeSetting("Build Mode").setValue("Telly");
            r.press(r.f.mc.gameSettings.keyBindForward,true);r.f.player.rotationYaw=90;r.f.player.rotationPitch=75.7f;
            r.preparedTick(new MovementInputEvent(0,1,false,false));
            r.f.player.onGround=false;r.f.player.motionY=-.1;
            for(int airborne=1;airborne<=2;airborne++) {
                r.preparedTick(new MovementInputEvent(0,1,false,false));
                assertEquals(NScaffold.Status.HOLD_BACK_TO_BRIDGE,r.module.getStatus());assertEquals(0,r.world.clicks);
            }
            r.press(r.f.mc.gameSettings.keyBindForward,false);r.press(r.f.mc.gameSettings.keyBindBack,true);
            r.preparedTick(new MovementInputEvent(0,-1,false,false));
            assertEquals(NScaffold.Status.TELLY_WAIT,r.module.getStatus());assertTrue(r.module.info.contains("3 of 4"));
            r.preparedTick(new MovementInputEvent(0,-1,false,false));
            assertEquals(1,r.world.clicks);assertEquals(NScaffold.Status.BUILDING,r.module.getStatus());
        }
    }

    @Test public void clutchOverridesTheTellyWaitOnlyForAnActuallyReachableRecoveryFace()throws Exception {
        for(boolean supported:new boolean[]{false,true})try(Run r=new Run()) {
            r.module.getModeSetting("Build Mode").setValue("Telly");r.clutch(false);
            r.press(r.f.mc.gameSettings.keyBindBack,true);r.f.player.onGround=false;r.f.player.motionY=-.1;
            if(!supported)r.world.cells.clear();
            r.preparedTick(new MovementInputEvent(0,-1,false,false),()->{
                assertEquals(supported?NScaffold.Status.CLUTCHING:NScaffold.Status.TELLY_WAIT,r.module.getStatus());
                RotationManager.MovementRotation movement=r.f.runtime.getRotationManager().getPreparedMovement();
                assertNotNull(movement);
                if(supported)assertEquals("nscaffold",movement.source);else assertNull(movement.source);
            },()->{},()->{});
            assertEquals(supported?1:0,r.world.clicks);
            if(!supported)assertTrue(r.world.slots.isEmpty());
        }
    }

    private static void advanced(Run r,double yaw,double pitch,double smoothing) {
        r.module.getBooleanSetting("Advanced Rotations").setValue(true);
        r.module.getModeSetting("Smooth Mode").setValue("Linear");
        r.module.getNumberSetting("Extra Smoothing Factor").setValue(smoothing);
        r.module.getNumberSetting("Yaw Rotation Speed (min)").setValue(yaw);
        r.module.getNumberSetting("Yaw Rotation Speed (max)").setValue(yaw);
        r.module.getNumberSetting("Pitch Rotation Speed (min)").setValue(pitch);
        r.module.getNumberSetting("Pitch Rotation Speed (max)").setValue(pitch);
        r.module.getBooleanSetting("Accelerate").setValue(false);
        r.module.getBooleanSetting("Imperfect Correlation").setValue(false);
        r.module.getBooleanSetting("Short Stop").setValue(false);
    }

    @Test public void advancedAxisSpeedSettingsChangeTheActualPreparedVisibleCamera()throws Exception {
        double[] yaw=new double[2],pitch=new double[2];
        for(int profile=0;profile<2;profile++)try(Run r=new Run()) {
            final int index=profile;double yawCap=profile==0?4:80,pitchCap=profile==0?2:40;
            advanced(r,yawCap,pitchCap,1);
            r.preparedTick(new MovementInputEvent(0,0,false,false),()->{
                RotationManager.MovementRotation movement=r.f.runtime.getRotationManager().getPreparedMovement();
                assertNotNull(movement);assertEquals("nscaffold",movement.source);
                yaw[index]=Math.abs(r.f.player.rotationYaw);pitch[index]=Math.abs(r.f.player.rotationPitch);
                assertEquals(movement.yaw,r.f.player.rotationYaw,0);assertEquals(movement.pitch,r.f.player.rotationPitch,0);
            },()->{},()->{});
            assertTrue(yaw[index]>0 && yaw[index]<=yawCap);assertTrue(pitch[index]>0 && pitch[index]<=pitchCap);
            assertEquals("partial turn cannot place",0,r.world.clicks);
        }
        assertTrue("yaw control has visible runtime impact",yaw[1]>yaw[0]*10);
        assertTrue("pitch control has independent visible runtime impact",pitch[1]>pitch[0]*10);
    }

    @Test public void fastClutchBypassesOptionalEasingButKeepsBothConfiguredAxisCaps()throws Exception {
        double[] travel=new double[2];
        for(int fast=0;fast<2;fast++)try(Run r=new Run()) {
            r.clutch(false);advanced(r,12,8,.05);
            r.module.getBooleanSetting("Fast Clutch Always").setValue(fast==1);
            r.f.player.onGround=false;r.f.player.motionY=-.1;
            r.preparedTick(new MovementInputEvent(0,0,false,false));
            double yaw=Math.abs(r.f.player.rotationYaw),pitch=Math.abs(r.f.player.rotationPitch);
            assertTrue(yaw>0 && yaw<=12);assertTrue(pitch>0 && pitch<=8);
            travel[fast]=Math.hypot(yaw,pitch);assertEquals(0,r.world.clicks);
        }
        assertTrue("fast acquisition must have a real effect",travel[1]>travel[0]*5);
    }

    @Test public void modeAndLifecycleChangesCannotBuyASecondActionInsideTheSameOuterTick()throws Exception {
        for(int change=0;change<3;change++)try(Run r=new Run()) {
            final int kind=change;
            r.preparedTick(new MovementInputEvent(0,0,false,false),()->{},()->{},()->{
                assertEquals(1,r.world.clicks);r.world.cells.remove(new BlockPos(0,63,0));
                float yaw=r.f.player.rotationYaw,pitch=r.f.player.rotationPitch;
                if(kind==0){r.module.setToggled(false);r.module.setToggled(true);}
                if(kind==1){r.module.getModeSetting("Build Mode").setValue("Telly");r.module.getModeSetting("Build Mode").setValue("Normal");}
                if(kind==2){r.module.getBooleanSetting("Advanced Rotations").setValue(true);r.module.getBooleanSetting("Advanced Rotations").setValue(false);}
                r.preparedStep(new MovementInputEvent(0,0,false,false),()->{},()->{});
                assertEquals("same tick must keep its consumed budget",1,r.world.clicks);
                assertEquals(yaw,r.f.player.rotationYaw,0);assertEquals(pitch,r.f.player.rotationPitch,0);
                assertEquals(63,r.world.inventory[3].stackSize);
            });
            r.preparedTick(new MovementInputEvent(0,0,false,false));
            assertEquals("a later real tick may resume normally",2,r.world.clicks);
        }
    }

    @Test public void aPreparedClutchCannotPlaceAfterItsSupportEpochOrPhysicalRouteChanges()throws Exception {
        for(int change=0;change<3;change++)try(Run r=new Run()) {
            final int kind=change;
            r.clutch(true);r.module.getNumberSetting("Ground Ticks to Sneak").setValue(0D);
            r.press(r.f.mc.gameSettings.keyBindForward,true);r.position(.05,64,.5,true);r.f.player.motionX=.1;
            MovementInputEvent input=new MovementInputEvent(0,1,false,false);
            r.preparedTick(input,()->{
                assertEquals(NScaffold.Status.CLUTCHING,r.module.getStatus());
                if(kind==0)r.world.cells.clear();
                if(kind==1)r.f.network.getClientPackets().worldChanged();
            },()->{
                if(kind==1)assertFalse("an old scene cannot request sneak",input.isSneak());
                if(kind==2)r.position(4.05,64,.5,true);
            },()->{});
            assertEquals("stale recovery, change="+kind,0,r.world.clicks);assertEquals(0,r.world.swings);
            assertEquals(64,r.world.inventory[3].stackSize);assertTrue(r.world.slots.isEmpty());
            assertFalse(r.f.mc.gameSettings.keyBindSneak.isKeyDown());
        }
    }
}
