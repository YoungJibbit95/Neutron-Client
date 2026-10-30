package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.impl.combat.Aura;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Actual service logic with numeric rotations, collision boxes and fake clocks/transports. No game/GL. */
public class CombatFlowTest {
    private EventMotion motion(float yaw, float pitch) { return new EventMotion(0,0,0,yaw,pitch,true); }

    private float[] frame(RotationManager manager, float goalYaw, float goalPitch, boolean accepted) {
        // The camera is deliberately unchanged: silent rotation must still make progress.
        manager.beginMotion(0,0);
        float[] next = manager.step("aura",0,0,goalYaw,goalPitch,12,8,.5f);
        manager.request("aura",RotationManager.AURA_PRIORITY,next[0],next[1]);
        EventMotion event = motion(0,0); manager.resolve(event);
        manager.finishMotion(event.getYaw(),event.getPitch(),accepted);
        return next;
    }

    @Test public void silentRotationConvergesAcrossTicksDespiteUnchangedCamera() {
        RotationManager manager = new RotationManager();
        float previous=0;
        for(int i=0;i<20;i++) {
            float[] next=frame(manager,93.17f,34.21f,true);
            assertTrue(next[0]>=previous); assertTrue(next[0]-previous<=12.001f); previous=next[0];
        }
        // Target convergence is bounded by half a mouse sensitivity step.
        assertEquals(93.17f,manager.getEffectiveYaw(),.0751f);
        assertEquals(34.21f,manager.getEffectivePitch(),.0751f);
        assertTrue(manager.isOwner("aura"));
    }

    @Test public void yawTakesShortPathAcrossWrapAndPitchStaysBounded() {
        RotationManager manager=new RotationManager();
        float[] step=manager.smooth(179,89,-179,130,5,5);
        assertEquals(181,step[0],.0001f); assertEquals(90,step[1],0);
        step=manager.smooth(-179,-89,179,-150,5,5);
        assertEquals(-181,step[0],.0001f); assertEquals(-90,step[1],0);
        step=manager.step("test",179,0,-179,0,5,5,.5f);
        for(int i=0;i<5;i++) step=manager.step("test",179,0,-179,0,5,5,.5f);
        assertEquals(0,MathHelper.wrapAngleTo180_float(step[0]+179),.0751f);
    }

    @Test public void finalSubSensitivityStepSnapsInsteadOfInventingFractionalMouseInput() {
        RotationManager manager=new RotationManager();
        float[] next=manager.step("aura",0,0,.03f,.02f,1,1,1);
        assertEquals(0,next[0],.000001f); assertEquals(0,next[1],.000001f);
        float gcd=(1f*.6f+.2f);gcd=gcd*gcd*gcd*1.2f;
        next=manager.step("aura",0,0,.37f,-.37f,1,1,1);
        assertEquals(gcd,next[0],.000001f);assertEquals(-gcd,next[1],.000001f);
    }

    @Test public void targetSwitchContinuesFromLastEffectiveStateWithoutTeleporting() {
        RotationManager manager=new RotationManager();
        for(int i=0;i<5;i++) frame(manager,100,20,true);
        float before=manager.getEffectiveYaw(); float[] changed=frame(manager,-80,-35,true);
        assertTrue(Math.abs(MathHelper.wrapAngleTo180_float(changed[0]-before))<=12.001f);
        assertTrue(changed[0]<before);
        for(int i=0;i<30;i++) frame(manager,-80,-35,true);
        assertEquals(-80,manager.getEffectiveYaw(),.0751f); assertEquals(-35,manager.getEffectivePitch(),.0751f);
    }

    @Test public void rejectedMovementDoesNotAdvanceEffectiveStateOrGrantAttackReadiness() {
        RotationManager manager=new RotationManager(); frame(manager,90,0,true);
        float before=manager.getEffectiveYaw(); float[] rejected=frame(manager,90,0,false);
        assertFalse(manager.isMotionAccepted()); assertEquals(before,manager.getEffectiveYaw(),0);
        float[] retry=frame(manager,90,0,true); assertArrayEquals(rejected,retry,0);
    }

    @Test public void sourceReleaseReseedsFromManualViewAndKeepsOtherOwners() {
        RotationManager manager=new RotationManager(); frame(manager,90,0,true); manager.reset("aura");
        // Releasing control leaves the next unmodified movement free to send the manual view.
        manager.beginMotion(-30,10);manager.finishMotion(-30,10,true);
        manager.beginMotion(-30,10);
        float[] next=manager.step("aura",-30,10,-90,10,10,10,.5f);
        assertTrue(next[0]<-30&&next[0]>=-40.001f);
        manager.request("scaffold",30,80,75); EventMotion event=motion(0,0);manager.resolve(event);manager.finishMotion(80,75,true);
        manager.reset("aura"); assertTrue(manager.isOwner("scaffold"));
        manager.reset("scaffold"); assertFalse(manager.isOwner("scaffold"));
    }

    @Test public void releasedScaffoldHandsOverFromTheLastWireAngleNotTheCamera() {
        RotationManager manager=new RotationManager();manager.beginMotion(0,0);manager.request("scaffold",30,75,80);
        manager.finishMotion(75,80,true);manager.reset("scaffold");
        float[] next=frame(manager,-80,0,true);
        assertTrue(next[0]>=63&&next[0]<75);assertTrue(next[1]>=72&&next[1]<80);
    }

    @Test public void aNewWorldOrPlayerCannotReuseOldSceneRotation() {
        RotationManager manager=new RotationManager();Object player=new Object(),world=new Object(),nextWorld=new Object();
        manager.beginMotion(player,world,0,0);manager.request("scaffold",30,75,80);manager.finishMotion(75,80,true);
        manager.beginMotion(player,nextWorld,-40,0);
        float[] next=manager.step("aura",-40,0,-80,0,12,8,.5f);
        assertTrue(next[0]>=-52.001&&next[0]<-40);assertFalse(manager.isOwner("scaffold"));
        manager.request("aura",20,next[0],next[1]);manager.finishMotion(next[0],next[1],true);
        manager.beginMotion(new Object(),nextWorld,30,0);
        next=manager.step("aura",30,0,60,0,12,8,.5f);assertTrue(next[0]>30&&next[0]<=42);
    }

    @Test public void ownershipIsIndependentOfAuraScaffoldAndAssistListenerOrder() {
        String[][] orders={{"aura","scaffold","aimassist"},{"scaffold","aimassist","aura"},{"aimassist","aura","scaffold"}};
        for(String[]order:orders){
            RotationManager manager=new RotationManager();manager.beginMotion(0,0);
            for(String source:order) manager.request(source,source.equals("scaffold")?30:source.equals("aura")?20:10,
                    source.equals("scaffold")?75:source.equals("aura")?30:10,source.equals("scaffold")?80:0);
            EventMotion event=motion(0,0);manager.resolve(event);assertEquals(75,event.getYaw(),0);assertEquals(80,event.getPitch(),0);
            manager.finishMotion(event.getYaw(),event.getPitch(),true); assertTrue(manager.isOwner("scaffold"));assertFalse(manager.isOwner("aura"));
            float[] next=frame(manager,-80,0,true);assertTrue(next[0]<75&&next[0]>=63);
        }
    }

    @Test public void outgoingPacketReplacementCannotFalselyKeepAuraOwnership() {
        RotationManager manager=new RotationManager();manager.beginMotion(0,0);manager.request("aura",20,40,10);
        EventMotion event=motion(0,0);manager.resolve(event);manager.finishMotion(-30,0,true);
        assertTrue(manager.isMotionAccepted());assertFalse(manager.isOwner("aura"));assertEquals(-30,manager.getEffectiveYaw(),0);
        manager.beginMotion(10,20);manager.request("aura",20,45,15);manager.finishMotion(Float.NaN,15,true);
        assertFalse(manager.isMotionAccepted());
    }

    @Test public void lateMovementReplayInvalidatesPreviouslyValidatedAttackAngles() {
        RotationManager manager=new RotationManager();frame(manager,90,0,true);
        float yaw=manager.getEffectiveYaw(),pitch=manager.getEffectivePitch();
        assertTrue(manager.matchesEffective(yaw,pitch));assertTrue(manager.isOwner("aura"));
        manager.observeOutboundRotation(-45,20);
        assertFalse(manager.matchesEffective(yaw,pitch));assertFalse(manager.isOwner("aura"));
        assertEquals(-45,manager.getEffectiveYaw(),0);
    }

    @Test public void nestedPacketReplayBeforeMotionPostUsesLastAcceptedOutboundRotation() {
        RotationManager manager=new RotationManager();manager.beginMotion(0,0);manager.request("aura",20,30,10);
        manager.observeOutboundRotation(30,10); // current C05/C06 accepted
        manager.observeOutboundRotation(-10,5); // listener replay before the queued call returns
        manager.finishMotion(manager.getEffectiveYaw(),manager.getEffectivePitch(),true);
        assertTrue(manager.isMotionAccepted());assertFalse(manager.isOwner("aura"));assertFalse(manager.matchesEffective(30,10));
    }

    @Test public void attackListenerChangingWireRotationVetoesTheControllerAction() {
        RotationManager manager=new RotationManager();frame(manager,90,0,true);
        float yaw=manager.getEffectiveYaw(),pitch=manager.getEffectivePitch();
        AttackScheduler scheduler=new AttackScheduler(()->1000L,(min,max)->10);scheduler.beginTick();AtomicInteger actions=new AtomicInteger();
        assertFalse(scheduler.tryAttack(manager.getMotionSequence(),manager.matchesEffective(yaw,pitch),10,10,()->{
            manager.observeOutboundRotation(-20,15); // e.g. an AttackEvent listener dispatches queued movement
            if(!manager.matchesEffective(yaw,pitch))return false;
            actions.incrementAndGet();return true;
        }));
        assertEquals(0,actions.get());
    }

    private RaycastService.Collision<String> box(String name,int id,double x,double z){
        return new RaycastService.Collision<>(name,new AxisAlignedBB(x-.3,-.5,z-.3,x+.3,.5,z+.3),id);
    }
    private String hit(double reach,double obstruction,RaycastService.Collision<String>...boxes){
        return RaycastService.closestHit(new Vec3(0,0,0),new Vec3(0,0,1),reach,obstruction,Arrays.asList(boxes));
    }

    @Test public void raycastRejectsMisalignmentAndNeverHitsAnEntityBehindTheNearestOne() {
        RaycastService.Collision<String> selected=box("selected",2,2,2),front=box("front",1,0,1),back=box("back",3,0,2.5);
        assertNull(hit(3,Double.POSITIVE_INFINITY,selected));
        assertEquals("front",hit(3,Double.POSITIVE_INFINITY,back,front));
        assertEquals("front",hit(3,Double.POSITIVE_INFINITY,front,back));
    }

    @Test public void rangeAndWallsUseTheActualRayIntersection() {
        RaycastService.Collision<String> entity=box("target",1,0,3.2);
        assertEquals("target",hit(3,Double.POSITIVE_INFINITY,entity)); // near face at 2.9
        assertNull(hit(2.8,Double.POSITIVE_INFINITY,entity));
        assertNull(hit(3,2,entity));
        assertEquals("target",hit(3,Double.POSITIVE_INFINITY,entity)); // Through Walls ignores blocks, not geometry
        assertEquals("front",hit(3,Double.POSITIVE_INFINITY,entity,box("front",2,0,1)));
    }

    @Test public void containingBoxAtZeroDistanceCannotBeReplacedByAFartherEntity() {
        assertEquals("inside",hit(4,Double.POSITIVE_INFINITY,box("inside",1,0,0),box("farther",2,0,2)));
        assertEquals("inside",hit(4,Double.POSITIVE_INFINITY,box("farther",2,0,2),box("inside",1,0,0)));
    }

    @Test public void timerAloneCannotAttackAndOnlyOneOpportunityIsEvaluatedPerMotion() {
        AtomicLong now=new AtomicLong();AtomicInteger attacks=new AtomicInteger();
        AttackScheduler scheduler=new AttackScheduler(now::get,(min,max)->10);scheduler.beginTick();
        assertFalse(scheduler.tryAttack(1,false,10,10,()->{attacks.incrementAndGet();return true;}));
        now.set(1000);assertFalse(scheduler.tryAttack(1,true,10,10,()->{attacks.incrementAndGet();return true;}));
        assertEquals(0,attacks.get());scheduler.beginTick();
        assertTrue(scheduler.tryAttack(2,true,10,10,()->{attacks.incrementAndGet();return true;}));
        assertFalse(scheduler.tryAttack(2,true,10,10,()->{attacks.incrementAndGet();return true;}));
        assertFalse(scheduler.tryAttack(3,true,10,10,()->{attacks.incrementAndGet();return true;}));assertEquals(1,attacks.get());
    }

    @Test public void attackWaitsForActualAlignmentAndUsesTheVerifiedTarget() {
        RotationManager rotations=new RotationManager();AtomicLong now=new AtomicLong();AttackScheduler attacks=new AttackScheduler(now::get,(min,max)->10);
        List<String> struck=new ArrayList<>();boolean everAttacked=false;
        for(int i=0;i<20;i++){
            attacks.beginTick();frame(rotations,90,0,true);
            double radians=Math.toRadians(rotations.getEffectiveYaw());
            String hit=RaycastService.closestHit(new Vec3(0,0,0),new Vec3(-Math.sin(radians),0,Math.cos(radians)),3,Double.POSITIVE_INFINITY,
                    Arrays.asList(box("selected",1,-2.5,0),box("unrelated",2,0,2.5)));
            boolean correct="selected".equals(hit)&&rotations.isOwner("aura")&&rotations.isMotionAccepted();
            boolean fired=attacks.tryAttack(rotations.getMotionSequence(),correct,10,10,()->{struck.add(hit);return true;});
            if(fired){assertEquals("selected",hit);everAttacked=true;}
            else if(!everAttacked)assertTrue(struck.isEmpty());
            now.addAndGet(50);
        }
        assertTrue(everAttacked);assertTrue(struck.stream().allMatch("selected"::equals));
    }

    @Test public void manualAttackAndAutomaticAttackShareOneBudgetInBothOrders() {
        AtomicLong now=new AtomicLong();AttackScheduler scheduler=new AttackScheduler(now::get,(min,max)->10);
        scheduler.beginTick();assertTrue(scheduler.allowControllerAttack(true));scheduler.recordControllerAttack();
        assertFalse(scheduler.tryAttack(1,true,10,10,()->{fail("duplicate automatic attack");return true;}));
        now.set(100);scheduler.beginTick();
        assertTrue(scheduler.tryAttack(2,true,10,10,()->{
            assertTrue(scheduler.allowControllerAttack(true));assertFalse(scheduler.allowControllerAttack(true));return true;
        }));
        assertFalse(scheduler.allowControllerAttack(true));assertTrue(scheduler.allowControllerAttack(false));
    }

    @Test public void cpsWindowAndResetDoNotEraseAnotherControllersAttack() {
        AtomicLong now=new AtomicLong();AttackScheduler scheduler=new AttackScheduler(now::get,(min,max)->{
            assertEquals(5,min,0);assertEquals(10,max,0);return 10;
        });
        scheduler.beginTick();assertTrue(scheduler.tryAttack(1,true,10,5,()->true));
        now.set(99);scheduler.beginTick();assertFalse(scheduler.tryAttack(2,true,10,5,()->true));
        now.set(100);scheduler.beginTick();assertTrue(scheduler.tryAttack(3,true,10,5,()->true));
        scheduler.reset();assertFalse(scheduler.allowControllerAttack(true));
        now.set(150);scheduler.beginTick();assertFalse(scheduler.tryAttack(4,true,10,5,()->true));
    }

    @Test public void failedOrCancelledActionCannotBeRepeatedInTheSameTick() {
        AttackScheduler scheduler=new AttackScheduler(()->0L,(min,max)->10);scheduler.beginTick();AtomicInteger calls=new AtomicInteger();
        assertFalse(scheduler.tryAttack(1,true,10,10,()->{calls.incrementAndGet();return false;}));
        assertFalse(scheduler.tryAttack(1,true,10,10,()->{calls.incrementAndGet();return true;}));
        assertFalse(scheduler.tryAttack(2,true,10,10,()->{calls.incrementAndGet();return true;}));assertEquals(1,calls.get());
    }

    @Test public void multiVisitsEachLimitedTargetOnlyAfterASuccessfulAttackAndHandlesLoss() {
        List<String> candidates=Arrays.asList("a","b","c","d");String selected=null;
        selected=TargetingService.selectTarget(candidates,selected,true,3,false);assertEquals("a",selected);
        selected=TargetingService.selectTarget(candidates,selected,true,3,false);assertEquals("a",selected);
        selected=TargetingService.selectTarget(candidates,selected,true,3,true);assertEquals("b",selected);
        selected=TargetingService.selectTarget(candidates,selected,true,3,true);assertEquals("c",selected);
        selected=TargetingService.selectTarget(candidates,selected,true,3,true);assertEquals("a",selected);
        assertEquals("b",TargetingService.selectTarget(Arrays.asList("b","c"),selected,true,3,false));
        assertNull(TargetingService.selectTarget(Collections.emptyList(),selected,true,3,true));
        assertEquals("a",TargetingService.selectTarget(candidates,"c",false,3,true));
    }

    @Test public void autoblockReleasesOnlyOwnedStateAndResumesAfterAttackForBothPacketModes() {
        for(AutoblockController.Mode mode:new AutoblockController.Mode[]{AutoblockController.Mode.WATCHDOG,AutoblockController.Mode.VERUS}){
            List<Boolean> packets=new ArrayList<>();AtomicBoolean manual=new AtomicBoolean();
            AutoblockController block=new AutoblockController(()->true,manual::get,()->1,packets::add);
            block.update(true,mode,true,false);assertTrue(packets.isEmpty());
            block.update(true,mode,false,true);assertEquals(Collections.singletonList(true),packets);
            block.beforeAttack();block.beforeAttack();assertEquals(Arrays.asList(true,false),packets);
            block.update(true,mode,false,true);assertEquals(Arrays.asList(true,false,true),packets);
            manual.set(true);block.release();assertEquals(3,packets.size());
            block.update(true,mode,false,true);assertEquals(3,packets.size());
        }
    }

    @Test public void worldResetDoesNotSendReleaseOnOldConnectionOrAffectManualUse() {
        List<Boolean> packets=new ArrayList<>();AutoblockController block=new AutoblockController(()->true,()->false,()->1,packets::add);
        block.update(true,AutoblockController.Mode.WATCHDOG,false,true);block.reset();block.release();
        assertEquals(Collections.singletonList(true),packets);
        block.update(true,AutoblockController.Mode.FAKE,true,true);assertEquals(1,packets.size());
    }

    @Test public void auraDisableWorldChangeAndUnavailableContextReleaseOnlyAuraOwnership() {
        RotationManager shared=RuntimeServices.rotation();shared.reset();Aura aura=new Aura();
        try {
            aura.setToggled(true);
            shared.beginMotion(0,0);shared.request("aura",20,40,10);shared.finishMotion(40,10,true);
            Pulsar.BUS.post(new WorldEvent.Unload(null));assertFalse(shared.isOwner("aura"));
            shared.beginMotion(0,0);shared.request("scaffold",30,70,80);shared.finishMotion(70,80,true);
            CombatTargetTracker.update(null,"scaffold");
            Pulsar.BUS.post(new TickEvent());assertTrue(shared.isOwner("scaffold"));assertEquals("scaffold",CombatTargetTracker.getSource());
            aura.setToggled(false);assertTrue(shared.isOwner("scaffold"));
        } finally { if(aura.isToggled())aura.setToggled(false);shared.reset();CombatTargetTracker.clear(); }
    }
}
