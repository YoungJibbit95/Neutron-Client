package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.core.network.PacketPipeline;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import com.mojang.authlib.GameProfile;
import java.util.concurrent.atomic.AtomicBoolean;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MapColor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Real placement/rotation decisions, synthetic collision boxes, inventory and controller boundary. No game/GL. */
public class ScaffoldPlacementTest {
    private static final PlacementContext NORMAL = new PlacementContext(false, false, 63);
    private static final class Cell {
        final Object state = new Object();
        final AxisAlignedBB box;
        Cell(AxisAlignedBB box) { this.box = box; }
    }
    static class WorldAccess implements ScaffoldPlacementService.Access {
        Object world = new Object(), player = new Object();
        boolean ready = true, allowed = true, collision, success = true, sync = true, throwClick;
        int selected = 0, serverSlot = 0, clicks, swings;
        double reach = 4.5;
        long time = 1000;
        Vec3 feet = new Vec3(.5,64,.5), wireEyes;
        final Map<BlockPos,Cell> cells = new LinkedHashMap<>();
        final ItemStack[] inventory = new ItemStack[9];
        final List<Integer> slots = new ArrayList<>();
        Runnable onSync = () -> {}, onClick = () -> {};
        WorldAccess() {
            inventory[3] = new ItemStack(new ItemBlock(new Block(Material.rock, MapColor.stoneColor)), 64);
            block(new BlockPos(-1,63,0));
        }
        void block(BlockPos pos) { cells.put(pos, new Cell(new AxisAlignedBB(pos,pos.add(1,1,1)))); }
        public boolean ready() { return ready; }
        public Object world() { return world; }
        public Object player() { return player; }
        public Vec3 feet() { return feet; }
        public Vec3 eyes() { return feet.addVector(0,1.62,0); }
        public Vec3 wireEyes() { return wireEyes == null ? eyes() : wireEyes; }
        public double reach() { return reach; }
        public boolean jumpDown() { return false; }
        public int selectedSlot() { return selected; }
        public void selectSlot(int value) { selected = value; }
        public boolean syncSlot() { onSync.run(); if (sync) { serverSlot = selected; slots.add(selected); } return sync; }
        public ItemStack stack(int slot) { return inventory[slot]; }
        public boolean usable(ItemStack stack) { return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock; }
        public Object state(BlockPos pos) { return cells.containsKey(pos) ? cells.get(pos).state : null; }
        public boolean replaceable(BlockPos pos) { return pos.getY() >= 0 && pos.getY() < 256 && !cells.containsKey(pos); }
        public AxisAlignedBB supportBounds(BlockPos pos) { return cells.containsKey(pos) ? cells.get(pos).box : null; }
        public boolean canPlace(PlacementTarget target) { return allowed && !collision; }
        public MovingObjectPosition rayTrace(Vec3 start, Vec3 end) {
            MovingObjectPosition nearest = null;
            double distance = Double.MAX_VALUE;
            for (Map.Entry<BlockPos,Cell> entry : cells.entrySet()) {
                MovingObjectPosition hit = entry.getValue().box.calculateIntercept(start,end);
                if (hit != null && start.squareDistanceTo(hit.hitVec) < distance) {
                    distance = start.squareDistanceTo(hit.hitVec);
                    nearest = new MovingObjectPosition(hit.hitVec, hit.sideHit, entry.getKey());
                }
            }
            return nearest;
        }
        public boolean click(PlacementTarget target, Vec3 hit, BooleanSupplier valid) {
            onClick.run();
            if (!valid.getAsBoolean()) return false;
            assertEquals(target.getSlot(), selected);
            assertEquals(selected, serverSlot);
            assertSame(target.stack, inventory[selected]);
            assertTrue(hit.squareDistanceTo(target.getHitVec()) < .0025);
            clicks++;
            if (throwClick) throw new IllegalStateException("synthetic controller failure");
            if (success) { inventory[selected].stackSize--; block(target.getDestination()); }
            return success;
        }
        public void swing(boolean silent) { swings++; }
    }
    static final class Fixture {
        final WorldAccess world;
        final ScaffoldPlacementService service;
        final RotationManager rotations = new RotationManager();
        PlacementTarget plan;
        Fixture() { this(new WorldAccess()); }
        Fixture(WorldAccess world) { this.world=world; service=new ScaffoldPlacementService(world,()->world.time); plan=service.findTarget(NORMAL,"Back"); }
        void align() {
            service.beginTick();
            ScaffoldRotationPlan aim = ScaffoldRotationPlan.build("Back",world.wireEyes(),plan.getHitVec());
            rotations.beginMotion(0,0);
            rotations.request("scaffold",30,aim.getYaw(),aim.getPitch());
            EventMotion event = new EventMotion(0,0,0,0,0,true); rotations.resolve(event);
            rotations.finishMotion(event.getYaw(),event.getPitch(),true);
        }
        boolean place(boolean pre, long delay, boolean spoof) {
            return service.place(plan,rotations,pre,rotations.getMotionSequence(),delay,spoof,true,false);
        }
    }

    @Test public void allModesAimAtReachablePointsOnThePlannedFace() {
        for (String mode : Arrays.asList("Back","Down","NCP")) {
            Fixture f = new Fixture(); f.plan = f.service.findTarget(NORMAL,mode);
            assertNotNull(mode,f.plan); assertEquals(new BlockPos(0,63,0),f.plan.getDestination());
            assertEquals(EnumFacing.EAST,f.plan.getFacing()); f.align(); assertTrue(mode,f.place(false,0,true));
        }
    }

    @Test public void smoothingConvergesAndCannotPlaceBeforeEffectiveRotationMatches() {
        Fixture f = new Fixture();
        ScaffoldRotationPlan aim = ScaffoldRotationPlan.build("Back",f.world.eyes(),f.plan.getHitVec());
        int waiting = 0;
        for(int tick=0;tick<30;tick++) {
            f.rotations.beginMotion(0,0);
            float[] next = f.rotations.step("scaffold",0,0,aim.getYaw(),aim.getPitch(),8,7,.5f);
            f.rotations.request("scaffold",30,next[0],next[1]);
            assertFalse(f.place(false,0,true)); // pending PRE request is not an accepted motion
            EventMotion event = new EventMotion(0,0,0,0,0,true); f.rotations.resolve(event);
            f.rotations.finishMotion(event.getYaw(),event.getPitch(),true);
            if (f.place(false,0,true)) break;
            waiting++;
        }
        assertTrue(waiting>5); assertEquals(1,f.world.clicks); assertEquals(1,f.world.swings);
    }

    @Test public void preUsesPreviousAcceptedAnglesAndNeverTheNewRequest() {
        Fixture f = new Fixture();
        f.rotations.beginMotion(0,0); f.rotations.request("scaffold",30,90,80);
        assertFalse(f.place(true,0,true));
        f.rotations.finishMotion(0,0,false); f.align();
        f.rotations.beginMotion(0,0); f.rotations.request("aura",20,-90,0);
        assertTrue(f.place(true,0,true)); assertEquals(1,f.world.clicks);
    }

    @Test public void cancelledMotionAndUnsentFirstFrameCannotPlace() {
        Fixture f = new Fixture(); f.align();
        f.rotations.beginMotion(0,0); f.rotations.finishMotion(90,70,false);
        assertFalse(f.place(false,0,true));
        f.rotations.beginMotion(0,0); assertFalse(f.place(true,0,true));
    }

    @Test public void samePlanCannotPlaceTwiceAcrossPreAndPostEvenOnLocalFailure() {
        Fixture f = new Fixture(); f.align(); f.rotations.beginMotion(0,0); f.world.success=false;
        assertFalse(f.place(true,0,true)); assertEquals(1,f.world.clicks);
        ScaffoldRotationPlan aim = ScaffoldRotationPlan.build("Back",f.world.eyes(),f.plan.getHitVec());
        f.rotations.finishMotion(aim.getYaw(),aim.getPitch(),true);
        assertFalse(f.place(false,0,true)); assertEquals(1,f.world.clicks); assertEquals(0,f.world.swings);
    }

    @Test public void planIsNotReplacedInPostAfterItsSupportChanges() {
        Fixture f = new Fixture(); f.align(); PlacementTarget original=f.plan;
        f.world.cells.remove(original.getBlockPos()); f.world.block(new BlockPos(0,62,0));
        assertNotNull(f.service.findTarget(NORMAL));
        assertFalse(f.place(false,0,true)); assertSame(original,f.plan); assertEquals(0,f.world.clicks);
    }

    @Test public void replacedSupportStateFilledDestinationAndEntityCollisionInvalidatePlan() {
        Fixture f=new Fixture();f.align(); f.world.block(f.plan.getBlockPos()); assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.block(f.plan.getDestination()); assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.collision=true; assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.allowed=false; assertFalse(f.place(false,0,true));
    }

    @Test public void obstacleWrongFaceAndInsufficientReachRejectTheActualRay() {
        Fixture f=new Fixture();f.align(); f.world.reach=1; assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.cells.put(new BlockPos(0,65,0),new Cell(new AxisAlignedBB(.1,65,.1,.9,65.2,.9)));
        assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.wireEyes=new Vec3(-2,65.62,.5); assertFalse(f.place(false,0,true));
    }

    @Test public void preChecksReachFromAlreadyTransmittedPositionRatherThanUnsentMovement() {
        Fixture f=new Fixture();f.align();f.rotations.beginMotion(0,0);
        f.world.wireEyes=new Vec3(10,65.62,.5);
        assertTrue(f.service.isValid(f.plan));assertFalse(f.place(true,0,true));assertEquals(0,f.world.clicks);
    }

    @Test public void rotationsOffStillRequiresActualAcceptedView() {
        Fixture f=new Fixture();f.rotations.beginMotion(0,0);f.rotations.finishMotion(0,0,true);
        assertFalse(f.place(false,0,true));
        ScaffoldRotationPlan aim=ScaffoldRotationPlan.build("Back",f.world.eyes(),f.plan.getHitVec());
        f.rotations.beginMotion(aim.getYaw(),aim.getPitch());f.rotations.finishMotion(aim.getYaw(),aim.getPitch(),true);
        assertFalse(f.rotations.isOwner("scaffold"));assertTrue(f.place(false,0,true));
    }

    @Test public void emptyReplacedOrModifiedStackAndManualSlotChangeAbort() {
        Fixture f=new Fixture();f.align(); f.world.inventory[3].stackSize=0; assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.inventory[3]=f.world.inventory[3].copy(); assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.inventory[3].stackSize--; assertFalse(f.place(false,0,true));
        f=new Fixture();f.align(); f.world.selected=2; assertFalse(f.place(false,0,true));assertEquals(2,f.world.selected);
    }

    @Test public void spoofSelectsAndSynchronizesTheExactStackThenRestoresBothSlots() {
        Fixture f=new Fixture();f.align();assertTrue(f.place(false,0,true));
        assertEquals(0,f.world.selected);assertEquals(0,f.world.serverSlot);assertEquals(Arrays.asList(3,0),f.world.slots);
    }

    @Test public void nonSpoofSuccessKeepsBlockSlotButFailureRestoresOriginal() {
        Fixture f=new Fixture();f.align();assertTrue(f.place(false,0,false));assertEquals(3,f.world.selected);assertEquals(3,f.world.serverSlot);
        f=new Fixture();f.align();f.world.success=false;assertFalse(f.place(false,0,false));assertEquals(0,f.world.selected);assertEquals(0,f.world.serverSlot);
    }

    @Test public void exceptionAndCancelledSlotSyncRestoreOriginalWithoutSwing() {
        Fixture f=new Fixture();f.align();f.world.throwClick=true;
        try { f.place(false,0,true);fail(); } catch(IllegalStateException expected) { }
        assertEquals(0,f.world.selected);assertEquals(0,f.world.serverSlot);assertEquals(0,f.world.swings);
        f=new Fixture();f.align();f.world.sync=false;assertFalse(f.place(false,0,true));assertEquals(0,f.world.selected);assertEquals(0,f.world.clicks);
    }

    @Test public void manualSelectionDuringSyncOrClickWinsOverSpoofRestore() {
        Fixture f=new Fixture();f.align();WorldAccess a=f.world;a.onSync=()->a.selected=5;
        assertFalse(f.place(false,0,true));assertEquals(5,a.selected);assertEquals(0,a.clicks);
        f=new Fixture();f.align();WorldAccess b=f.world;b.onClick=()->b.selected=6;
        assertFalse(f.place(false,0,true));assertEquals(6,b.selected);assertEquals(6,b.serverSlot);assertEquals(0,b.clicks);
    }

    @Test public void deathOrGuiDuringClickRestoresSlotsInTheSameContext() {
        Fixture f=new Fixture();f.align();WorldAccess a=f.world;a.onClick=()->a.ready=false;
        assertFalse(f.place(false,0,true));assertEquals(0,a.selected);assertEquals(0,a.serverSlot);assertEquals(0,a.clicks);
    }

    @Test public void worldOrPlayerReplacementDoesNotRestoreIntoNewContext() {
        Fixture f=new Fixture();f.align();WorldAccess a=f.world;a.onSync=()->a.world=new Object();
        assertFalse(f.place(false,0,true));assertEquals(0,a.clicks);assertEquals(1,a.slots.size());
        f=new Fixture();f.align();f.world.player=new Object();assertFalse(f.place(false,0,true));
        f=new Fixture();f.align();f.world.ready=false;assertFalse(f.place(false,0,true));
    }

    @Test public void finalControllerGuardRevalidatesAfterListeners() {
        Fixture f=new Fixture();f.align();WorldAccess a=f.world;a.onClick=()->a.block(fixedSupport());
        assertFalse(f.place(false,0,true));assertEquals(0,a.clicks);assertEquals(0,a.swings);assertEquals(0,a.selected);
    }
    private static BlockPos fixedSupport() { return new BlockPos(-1,63,0); }

    @Test public void delayIsConsumedByAnActualAttemptIncludingFailureButNotByRotationWaiting() {
        Fixture f=new Fixture();f.world.time+=200;
        assertFalse(f.place(false,200,true));f.align();f.world.success=false;
        assertFalse(f.place(false,200,true));assertEquals(1,f.world.clicks);
        f.world.time+=199;f.align();assertFalse(f.place(false,200,true));assertEquals(1,f.world.clicks);
        f.world.time++;assertFalse(f.place(false,200,true));assertEquals(2,f.world.clicks);
    }

    @Test public void keepYDownwardsJumpAndHeightBoundsUseRealDestinationLogic() {
        assertEquals(63,ScaffoldPlacementService.yLevel(66,new PlacementContext(false,true,63),false),0);
        assertEquals(65,ScaffoldPlacementService.yLevel(66,new PlacementContext(false,true,63),true),0);
        assertEquals(67,ScaffoldPlacementService.yLevel(68,new PlacementContext(false,true,63),false),0);
        assertEquals(62,ScaffoldPlacementService.yLevel(64,new PlacementContext(true,true,63),false),0);
        Fixture f=new Fixture();f.world.cells.clear();f.world.block(new BlockPos(0,61,0));
        PlacementTarget down=f.service.findTarget(new PlacementContext(true,false,63));assertNotNull(down);assertEquals(62,down.getDestination().getY());
        f.world.feet=new Vec3(.5,0,.5);assertNull(f.service.findTarget(NORMAL));
        f.world.inventory[3].stackSize=0;assertEquals(-1,f.service.getBlockSlot());assertEquals(0,f.service.getBlockCount());
    }

    @Test public void retainedPlanMustStillServeTheCurrentKeepYAndDownwardsDestination() {
        Fixture f=new Fixture();assertTrue(f.service.stillNeeded(f.plan,NORMAL));
        f.world.feet=new Vec3(.5,65,.5);
        assertFalse(f.service.stillNeeded(f.plan,NORMAL));
        assertTrue(f.service.stillNeeded(f.plan,new PlacementContext(false,true,63)));
        assertFalse(f.service.stillNeeded(f.plan,new PlacementContext(true,true,63)));
    }

    @Test public void downwardsChangesOnlyFreshMovementInputAndIsIdempotent() {
        MovementInput input=new MovementInput();input.sneak=true;input.moveForward=.3f;input.moveStrafe=-.3f;
        Scaffold.suppressSneak(input);assertFalse(input.sneak);assertEquals(1,input.moveForward,.0001f);assertEquals(-1,input.moveStrafe,.0001f);
        Scaffold.suppressSneak(input);assertEquals(1,input.moveForward,.0001f);
    }

    @Test public void timerRestoreHonorsExternalOwnershipChanges() {
        Scaffold.TimerControl timer=new Scaffold.TimerControl();assertEquals(1.5f,timer.apply(1,1.5f),0);
        assertEquals(1,timer.release(1.5f),0);
        timer.apply(1,1.5f);assertEquals(2,timer.apply(2,1.5f),0);assertEquals(2,timer.release(2),0);
        timer.apply(1,1.5f);assertEquals(2,timer.release(2),0);
    }

    @Test public void scaffoldPriorityIsReleasedToAuraAfterTargetLossOrDisable() throws Exception {
        RotationManager r=RuntimeServices.rotation();r.reset();Scaffold scaffold=new Scaffold();scaffold.setToggled(true);
        try {
            r.beginMotion(0,0);r.request("aura",20,-90,0);r.request("scaffold",30,90,70);
            EventMotion event=new EventMotion(0,0,0,0,0,true);r.resolve(event);r.finishMotion(event.getYaw(),event.getPitch(),true);assertTrue(r.isOwner("scaffold"));
            Pulsar.BUS.post(new TickEvent());assertFalse(r.isOwner("scaffold"));
            r.beginMotion(0,0);r.request("aura",20,-90,0);r.resolve(event);r.finishMotion(event.getYaw(),event.getPitch(),true);assertTrue(r.isOwner("aura"));
            scaffold.setToggled(false);assertTrue(r.isOwner("aura"));
        } finally { scaffold.setToggled(false);r.reset(); }
    }

    @Test public void worldChangeAndDisableClearActualModulePlanAndTowerState() throws Exception {
        Scaffold scaffold=new Scaffold();scaffold.setToggled(true);
        try {
            Field target=Scaffold.class.getDeclaredField("currentTarget");target.setAccessible(true);target.set(scaffold,new Fixture().plan);
            Field frame=Scaffold.class.getDeclaredField("frameTarget");frame.setAccessible(true);frame.set(scaffold,new Fixture().plan);
            Field tower=Scaffold.class.getDeclaredField("towerController");tower.setAccessible(true);
            Field ticks=TowerController.class.getDeclaredField("ticks");ticks.setAccessible(true);ticks.setInt(tower.get(scaffold),5);
            Scaffold.keepYCoord=63;Pulsar.BUS.post(new WorldEvent.Unload(null));
            assertNull(target.get(scaffold));assertNull(frame.get(scaffold));assertEquals(0,ticks.getInt(tower.get(scaffold)));assertEquals(0,Scaffold.keepYCoord,0);assertFalse(Scaffold.isDownwards());
            target.set(scaffold,new Fixture().plan);scaffold.setToggled(false);assertNull(target.get(scaffold));
        } finally { scaffold.setToggled(false);RuntimeServices.rotation().reset(); }
    }

    private static final class Transport extends NetworkManager {
        final List<Packet> packets = new ArrayList<>();
        Transport() { super(EnumPacketDirection.CLIENTBOUND); }
        @Override public boolean isChannelOpen() { return true; }
        @Override public void sendPacket(Packet packet) { packets.add(packet); }
        @Override public Packet<?> sendPacketGuarded(Packet packet, boolean silent, java.util.function.Predicate<Packet<?>> guard) {
            return getClientPackets().outboundSelected(packet,silent,this::sendPacket,guard);
        }
    }
    private static final class PacketListener implements Subscriber {
        int pre, post;
        Runnable before = () -> {};
        Packet<?> replacement;
        @Subscribe final Listener<EventPacket> listener = new Listener<>(event -> {
            if (event.isPre()) { pre++; before.run(); if (replacement != null) event.setPacket(replacement); }
            if (event.isPost()) post++;
        });
    }
    private NetHandlerPlayClient handler(Transport transport) {
        transport.configureClientPackets(new me.youngjibbit.core.network.PacketFlowTest.MainThread());
        return new NetHandlerPlayClient(null,null,transport,new GameProfile(new UUID(0,1),"ScaffoldFixture"));
    }

    @Test public void nativeTransportGuardRunsAfterPreListenersAndBeforeSendingOrPost() {
        Transport transport=new Transport();NetHandlerPlayClient handler=handler(transport);
        PacketListener listener=new PacketListener();AtomicBoolean valid=new AtomicBoolean(true);
        listener.before=()->valid.set(false);Pulsar.BUS.subscribe(listener);
        try {
            C08PacketPlayerBlockPlacement packet=new C08PacketPlayerBlockPlacement();
            assertNull(handler.addToSendQueueTracked(packet,outgoing->valid.get()));
            assertTrue(transport.packets.isEmpty());assertEquals(1,listener.pre);assertEquals(0,listener.post);
            listener.before=()->{};valid.set(true);
            assertSame(packet,handler.addToSendQueueTracked(packet,outgoing->valid.get()));
            assertEquals(Collections.singletonList(packet),transport.packets);assertEquals(2,listener.pre);assertEquals(1,listener.post);
        } finally { Pulsar.BUS.unsubscribe(listener); }
    }

    @Test public void nativeGuardRejectsPacketReplacementBeforeAnyTransmission() {
        Transport transport=new Transport();NetHandlerPlayClient handler=handler(transport);PacketListener listener=new PacketListener();
        listener.replacement=new C08PacketPlayerBlockPlacement();Pulsar.BUS.subscribe(listener);
        try {
            C08PacketPlayerBlockPlacement packet=new C08PacketPlayerBlockPlacement();
            assertNull(handler.addToSendQueueTracked(packet,outgoing->outgoing==packet));assertTrue(transport.packets.isEmpty());assertEquals(0,listener.post);
        } finally { Pulsar.BUS.unsubscribe(listener); }
    }

    @Test public void silentNativeTransportAlsoHonorsTheLastPlacementGuard() {
        Transport transport=new Transport();NetHandlerPlayClient handler=handler(transport);
        C08PacketPlayerBlockPlacement packet=new C08PacketPlayerBlockPlacement();
        assertNull(transport.getClientPackets().outbound(packet,true,transport::sendPacket,()->false));assertTrue(transport.packets.isEmpty());
    }

    @Test public void controllerSlotObservationAcceptsOnlyItsOwnTransport() throws Exception {
        NetHandlerPlayClient handler=handler(new Transport()),oldHandler=handler(new Transport());
        PlayerControllerMP controller=new PlayerControllerMP(null,handler);
        Field cached=PlayerControllerMP.class.getDeclaredField("currentPlayerItem");cached.setAccessible(true);
        controller.observeOutboundSlot(handler,3);assertEquals(3,cached.getInt(controller));
        controller.observeOutboundSlot(handler,5);assertEquals(5,cached.getInt(controller));
        controller.observeOutboundSlot(oldHandler,0);assertEquals(5,cached.getInt(controller));
        controller.observeOutboundSlot(handler,10);assertEquals(-1,cached.getInt(controller));
    }

    private static final class TowerAccess implements TowerController.Access {
        boolean ready=true, ground, allowMove=true;
        double velocity;
        Vec3 pos=new Vec3(.2,64.7,.3);
        public boolean ready(){return ready;} public boolean onGround(){return ground;}
        public double motionY(){return velocity;}public void motionY(double value){velocity=value;}
        public Vec3 position(){return pos;}public boolean move(Vec3 value){if(!allowMove)return false;pos=value;return true;}
    }
    @Test public void towerRequiresBlocksTargetJumpAndValidContextAndResetsItsCadence() {
        TowerAccess a=new TowerAccess();TowerController tower=new TowerController(a);EventMotion event=new EventMotion(.2,64.7,.3,0,0,false);
        tower.apply(event,true,"Watchdog",true,false,true);assertEquals(.32,a.velocity,0);
        tower.apply(event,true,"Watchdog",true,false,true);assertEquals(.32,a.velocity,0);
        tower.apply(event,true,"Watchdog",true,false,true);assertEquals(.24,a.velocity,0);
        tower.apply(event,true,"Watchdog",true,false,false);assertEquals(.24,a.velocity,0);
        tower.apply(event,true,"Watchdog",true,false,true);assertEquals(.32,a.velocity,0);
        a.velocity=0;tower.apply(event,true,"Vanilla",false,false,true);assertEquals(0,a.velocity,0);
        a.ready=false;tower.apply(event,true,"Vanilla",true,false,true);assertEquals(0,a.velocity,0);
    }

    @Test public void towerPositionChangesStayCollisionCheckedAndMatchTheMotionEvent() {
        TowerAccess a=new TowerAccess();TowerController tower=new TowerController(a);EventMotion event=new EventMotion(.2,64.7,.3,0,0,false);
        a.allowMove=false;tower.apply(event,true,"NCP",true,false,true);assertEquals(64.7,a.pos.yCoord,0);assertEquals(0,a.velocity,0);
        a.allowMove=true;tower.apply(event,true,"NCP",true,false,true);assertEquals(64,a.pos.yCoord,0);assertEquals(64,event.getY(),0);assertEquals(.42,a.velocity,0);
        tower.apply(event,true,"Watchdog",true,false,true);assertEquals(.5,a.pos.xCoord,0);assertEquals(.5,event.getX(),0);assertFalse(event.isOnGround());
        a.ground=true;tower.apply(event,true,"Vanilla",true,true,true);assertEquals(.42,a.velocity,0);assertTrue(event.isOnGround());
    }
}
