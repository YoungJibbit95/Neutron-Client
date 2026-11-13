package me.youngjibbit.core.world;

import me.youngjibbit.utils.render.BedBreakerIndicator;
import me.youngjibbit.utils.render.BedEspLayout;
import me.youngjibbit.utils.render.BedHudRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class BedBreakEstimateTest {
    @Test public void directMineReflectsActualBedProgressEvenWithInterveningDefense() throws Exception {
        Access access = new Access(); BlockPos bed = new BlockPos(0,64,3); access.world.blocks.put(bed,Blocks.bed.getDefaultState());
        access.world.blocks.put(new BlockPos(0,65,1),Blocks.bedrock.getDefaultState());
        access.active=bed; access.progress=.5; access.delay=2;
        BedBreakEstimate value=BedBreakEstimate.calculate(access,bed,bed,new Vec3(.5,65.62,.5),4.5,true);
        assertTrue(value.hasEstimate()); assertEquals(BedBreakEstimate.Basis.DIRECT,value.basis); assertTrue(value.activeIsBed);
        assertEquals(0,value.accessSeconds,0); assertEquals(.2,value.totalSeconds,1e-9); assertEquals(.5,value.blockProgress,0);
        assertEquals(0,value.remainingCoverBlocks); assertEquals(0,access.world.rays);
        BedHudRenderer.Progress frame=BedBreakerIndicator.frame(value);
        assertEquals("BED",frame.phase);assertEquals(value.blockProgress,frame.progress,0);
        assertEquals(BedEspLayout.time(value.totalSeconds),frame.time);assertTrue(frame.estimated);
        for(int read=0;read<20;read++) assertEquals(value.totalSeconds,BedBreakEstimate.calculate(access,bed,bed,new Vec3(.5,65.62,.5),4.5,true).totalSeconds,0);
        access.progress=.75;
        assertEquals(.15,BedBreakEstimate.calculate(access,bed,bed,new Vec3(.5,65.62,.5),4.5,true).totalSeconds,1e-9);
        assertEquals(0,access.world.removals);
    }
    @Test public void conditionalCoverEstimateIncludesEveryBlockStartupAndNativeDelay() throws Exception {
        Route access=new Route();
        BedBreakEstimate value=access.estimate();
        assertTrue(value.state.toString(),value.hasEstimate()); assertEquals(BedBreakEstimate.Basis.CONDITIONAL_COVER,value.basis);
        assertEquals(Arrays.asList(access.first,access.second,access.bed),value.path); assertEquals(2,value.remainingCoverBlocks);
        assertEquals(.85,value.accessSeconds,1e-9); assertEquals(1.10,value.totalSeconds,1e-9);
        assertEquals(.25,value.accessProgress,1e-6); assertEquals(.5,value.blockProgress,0); assertFalse(value.activeIsBed);
        BedHudRenderer.Progress frame=BedBreakerIndicator.frame(value);
        assertEquals("TO BED",frame.phase);assertEquals("2 blocks to bed",frame.detail);assertTrue(frame.estimated);
        assertEquals(value.accessProgress,frame.progress,0);assertEquals(BedEspLayout.time(value.accessSeconds),frame.time);
        assertNotEquals(BedEspLayout.time(value.totalSeconds),frame.time);
        assertEquals(3,access.world.blocks.size()); assertEquals(0,access.world.removals);
    }
    @Test public void instantSurvivalCoverHasNoArtificialFiveTickDelay() throws Exception {
        Route access=new Route(); access.delay=0; access.progress=0; access.rates.put(access.first,Double.POSITIVE_INFINITY);access.rates.put(access.second,1D);
        BedBreakEstimate value=access.estimate();
        assertTrue(value.state.toString(),value.hasEstimate()); assertEquals(.10,value.accessSeconds,1e-9); assertEquals(.35,value.totalSeconds,1e-9);
    }
    @Test public void actualRemovedPrefixAdvancesProgressButAReplacedBlockResetsIt() throws Exception {
        Route access=new Route(); BedBreakEstimate.ProgressTracker tracker=new BedBreakEstimate.ProgressTracker();
        BedBreakEstimate initial=tracker.apply(access.estimate(),access); assertTrue(initial.state.toString(),initial.hasEstimate());assertEquals(.25,initial.accessProgress,1e-6);
        access.world.blocks.remove(access.first); access.active=access.second;access.progress=.25;
        BedBreakEstimate next=tracker.apply(access.estimate(),access);
        assertEquals(.625,next.accessProgress,1e-6); assertEquals(1.25/3,next.progress,1e-6);
        assertEquals(1,next.remainingCoverBlocks);
        access.world.blocks.put(access.first,Blocks.stone.getDefaultState());
        BedBreakEstimate changed=tracker.apply(access.estimate(),access);
        assertFalse(changed.hasEstimate()); // The replaced prefix now blocks the currently owned second block.
    }
    @Test public void unloadedUnbreakableAndOutOfReachNeverBecomeNumericSeconds() throws Exception {
        Route access=new Route(); access.unloaded.add(access.second);
        BedBreakEstimate unloaded=access.estimate(); assertFalse(unloaded.hasEstimate());assertEquals(BedBreakEstimate.State.UNLOADED,unloaded.state);
        assertTrue(Double.isNaN(unloaded.totalSeconds)); assertEquals(0,access.world.rays);
        access.unloaded.clear();access.rates.put(access.second,0D);
        BedBreakEstimate blocked=access.estimate();assertFalse(blocked.hasEstimate());assertEquals(BedBreakEstimate.State.UNBREAKABLE,blocked.state);
        BedBreakEstimate far=BedBreakEstimate.calculate(access,access.bed,access.first,new Vec3(50,64,.5),4.5,false);
        assertEquals(BedBreakEstimate.State.OUT_OF_RANGE,far.state);assertTrue(Double.isNaN(far.accessSeconds));
    }
    @Test public void unrelatedMouseOverBlockHasNoInventedBedPath() throws Exception {
        Route access=new Route(); access.active=new BlockPos(2,64,1);access.world.blocks.put(access.active,Blocks.stone.getDefaultState());
        BedBreakEstimate value=access.estimate();assertFalse(value.hasEstimate());assertEquals(BedBreakEstimate.State.NO_BED_PATH,value.state);
        assertTrue(Double.isNaN(value.totalSeconds));
    }
    @Test public void invalidationDuringHardnessOrRayCannotPublishAnEstimate() throws Exception {
        Route access=new Route();access.invalidateOnRate=true;
        assertEquals(BedBreakEstimate.State.INVALIDATED,access.estimate().state);
        access.valid=true;access.invalidateOnRate=false;access.world.afterRay=()->access.valid=false;
        assertEquals(BedBreakEstimate.State.INVALIDATED,access.estimate().state);
    }
    @Test public void invalidHardnessAndAdventureVetoRemainUnknown() throws Exception {
        Access access=new Access();BlockPos bed=new BlockPos(0,64,3);access.world.blocks.put(bed,Blocks.bed.getDefaultState());access.active=bed;
        for(double rate:new double[]{0,-1,Double.NaN}) {
            access.rates.put(bed,rate);BedBreakEstimate value=BedBreakEstimate.calculate(access,bed,bed,new Vec3(.5,65.62,.5),4.5,true);
            assertFalse(value.hasEstimate());assertEquals(BedBreakEstimate.State.UNBREAKABLE,value.state);
        }
        access.rates.put(bed,1D);access.allowed=false;
        assertFalse(BedBreakEstimate.calculate(access,bed,bed,new Vec3(.5,65.62,.5),4.5,true).hasEstimate());
    }
    @Test public void worldInvalidationStopsBeforeReadingAnotherPosition() throws Exception {
        Route access=new Route();access.world.stateReads=0;access.world.afterRead=pos->access.valid=false;
        assertEquals(BedBreakEstimate.State.INVALIDATED,access.estimate().state);assertEquals(1,access.world.stateReads);
    }
    @Test public void cachedRouteRequiresUnchangedFutureCoverLoadedCellsAndActualRemainingWork() throws Exception {
        Route access=new Route();Vec3 eyes=new Vec3(.5,64.3,.5);BedBreakEstimate value=access.estimate();
        assertTrue(value.state.toString(),value.isCurrent(access,eyes,4.5,false));int rays=access.world.rays;
        for(int frame=0;frame<10;frame++)assertTrue(value.isCurrent(access,eyes,4.5,false));
        assertEquals("frame validation does not repeat the route search",rays,access.world.rays);
        access.world.blocks.put(access.second,Blocks.bedrock.getDefaultState());
        assertFalse(value.isCurrent(access,eyes,4.5,false));
        access.world.blocks.put(access.second,Blocks.stone.getDefaultState());access.unloaded.add(access.second);
        assertFalse(value.isCurrent(access,eyes,4.5,false));access.unloaded.clear();
        access.progress=.75;assertFalse(value.isCurrent(access,eyes,4.5,false));access.progress=.5;
        access.delay=1;assertFalse(value.isCurrent(access,eyes,4.5,false));access.delay=2;
        access.rates.put(access.second,1D);assertFalse(value.isCurrent(access,eyes,4.5,false));access.rates.put(access.second,.5D);
        access.world.blocks.put(new BlockPos(0,64,0),Blocks.stone.getDefaultState());
        assertFalse("new obstruction in a previously empty line cell invalidates the number",value.isCurrent(access,eyes,4.5,false));
        access.world.blocks.remove(new BlockPos(0,64,0));assertTrue(value.isCurrent(access,eyes,4.5,false));
        assertFalse(value.isCurrent(access,new Vec3(.6,64.3,.5),4.5,false));assertFalse(value.isCurrent(access,eyes,4,false));
        assertEquals(0,access.world.removals);
    }
    @Test public void retainedBestRouteIsRecheckedAfterLaterCandidateCallbacksAndSearchIsBounded() throws Exception {
        Route access=new Route();final int[] count={0};final Runnable[] callback=new Runnable[1];
        callback[0]=()->{
            if(++count[0]==4){access.world.blocks.put(access.second,Blocks.bedrock.getDefaultState());access.rates.put(access.second,0D);}
            else access.world.afterRay=callback[0];
        };
        access.world.afterRay=callback[0];BedBreakEstimate value=access.estimate();
        assertEquals(4,count[0]);assertFalse("the earlier cheaper route cannot keep a stale cover material",value.hasEstimate());
        assertTrue(access.world.rays<=128);assertTrue(access.world.stateReads<10000);assertEquals(0,access.world.removals);
    }
    private static class Access implements BedBreakEstimate.Access {
        final BedBreakerTestSupport.BedWorld world=BedBreakerTestSupport.world();
        final Map<BlockPos,Double> rates=new HashMap<>();final Set<BlockPos> unloaded=new HashSet<>();
        BlockPos active;double progress;int delay;boolean valid=true,allowed=true,isCreative,invalidateOnRate;
        Access() throws Exception { }
        public boolean current(){return valid;}
        public IBlockState stateIfLoaded(BlockPos pos){return unloaded.contains(pos)?null:world.getBlockState(pos);}
        public MovingObjectPosition ray(Vec3 from,Vec3 to){return world.ray(from,to);}
        public boolean mayMine(BlockPos pos,IBlockState state){return allowed;}
        public double relativeHardness(BlockPos pos,IBlockState state){if(invalidateOnRate)valid=false;return rates.getOrDefault(pos,.25D);}
        public boolean creative(){return isCreative;}
        public double progress(BlockPos pos){return pos.equals(active)?progress:0;}
        public boolean mining(BlockPos pos){return pos.equals(active);}
        public int hitDelay(){return delay;}
    }
    private static final class Route extends Access {
        final BlockPos bed=new BlockPos(0,64,3),first=new BlockPos(0,64,1),second=new BlockPos(0,64,2);
        Route() throws Exception {
            world.blocks.put(bed,Blocks.bed.getDefaultState());world.blocks.put(first,Blocks.wool.getDefaultState());world.blocks.put(second,Blocks.stone.getDefaultState());
            active=first;progress=.5;delay=2;rates.put(second,.5D);
        }
        BedBreakEstimate estimate(){return BedBreakEstimate.calculate(this,bed,active,new Vec3(.5,64.3,.5),4.5,false);}
    }
}
