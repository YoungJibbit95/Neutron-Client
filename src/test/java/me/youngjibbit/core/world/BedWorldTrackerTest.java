package me.youngjibbit.core.world;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class BedWorldTrackerTest {
    @BeforeClass public static void blocks() { Bootstrap.register(); }
    @Test public void discoversLoadedSectionsAndDeduplicatesAcrossChunkBoundaries() {
        Access access = new Access(); access.position = new Vec3(15.5,64,0.5); BlockPos foot = new BlockPos(15,64,0);
        access.bed(foot); BedWorldTracker tracker = new BedWorldTracker();
        for (int i=0;i<8;i++) { access.tick++; tracker.update(access,32); }
        List<BedObservation> values = tracker.snapshot(access);
        assertEquals(1, values.size()); assertEquals(foot, values.get(0).foot); assertTrue(values.get(0).completeBed());
        try { values.clear(); fail("mutable snapshot"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void chunkUnloadWorldChangeAndConnectionEpochInvalidateImmediately() {
        Access access = new Access(); access.bed(new BlockPos(0,64,0)); BedWorldTracker tracker = new BedWorldTracker(); tracker.update(access,16);
        assertEquals(1,tracker.snapshot(access).size()); access.sections.clear(); assertTrue(tracker.snapshot(access).isEmpty());
        access.bed(new BlockPos(0,64,0)); for(int i=0;i<8;i++){access.tick++;tracker.update(access,16);} assertEquals(1,tracker.snapshot(access).size());
        access.epoch++; assertTrue(tracker.snapshot(access).isEmpty());
        access.tick++; tracker.update(access,16); assertEquals(1,tracker.snapshot(access).size());
        access.world = new Object(); assertTrue(tracker.snapshot(access).isEmpty());
        access.tick++; tracker.update(access,16); assertFalse(tracker.snapshot(access).isEmpty());
        tracker.invalidate(); assertTrue(tracker.snapshot(access).isEmpty());
    }
    @Test public void repeatedFramesNeverRescanAndTickWorkIsBounded() {
        Access access = new Access(); ExtendedBlockStorage[] column = new ExtendedBlockStorage[16];
        for (int y=0;y<16;y++) {
            CountingSection section = new CountingSection(y*16); column[y]=section;
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int localY=0;localY<16;localY++) section.set(x,localY,z,Blocks.stone.getDefaultState());
            section.reads=0;
        }
        access.sections.put(key(0,0),column); BedWorldTracker tracker = new BedWorldTracker(); tracker.update(access,128);
        int visits=access.sectionVisits, cells=0; for(ExtendedBlockStorage section:column) cells+=((CountingSection)section).reads;
        assertTrue(visits<=BedWorldTracker.MAX_SECTION_VISITS_PER_TICK); assertTrue(cells<=BedWorldTracker.MAX_CELLS_PER_TICK);
        for(int frame=0;frame<20;frame++) { tracker.snapshot(access); tracker.update(access,128); }
        assertEquals(visits,access.sectionVisits);
    }
    @Test public void rangeCapAndLateInvalidationDoNotPublishOldBeds() {
        Access access = new Access(); access.bed(new BlockPos(0,64,0)); BedWorldTracker tracker = new BedWorldTracker();
        access.invalidateOnSection=true; tracker.update(access,100000); assertTrue(tracker.snapshot(access).isEmpty());
        access.valid=true; access.invalidateOnSection=false; access.tick++; tracker.update(access,16); assertEquals(1,tracker.snapshot(access).size());
        access.position=new Vec3(1000,64,1000); assertTrue(tracker.snapshot(access).isEmpty());
    }
    private static long key(int x,int z) { return ((long)x<<32) ^ (z&0xffffffffL); }
    private static final class CountingSection extends ExtendedBlockStorage {
        int reads; CountingSection(int y){super(y,false);}
        @Override public IBlockState get(int x,int y,int z){reads++;return super.get(x,y,z);}
    }
    private static final class Access implements BedWorldTracker.Access {
        Object world=new Object(),connection=new Object(); long epoch=1,tick; boolean valid=true,invalidateOnSection; int sectionVisits;
        Vec3 position=new Vec3(.5,64,.5); final Map<Long,ExtendedBlockStorage[]> sections=new HashMap<>();
        public Object worldIdentity(){return world;} public Object connectionIdentity(){return connection;} public long connectionEpoch(){return epoch;}
        public long tick(){return tick;} public Vec3 position(){return position;} public boolean current(){return valid;}
        public ExtendedBlockStorage sectionIfLoaded(int x,int y,int z){sectionVisits++;if(invalidateOnSection)valid=false;ExtendedBlockStorage[] column=sections.get(key(x,z));return column==null?null:column[y];}
        public IBlockState stateIfLoaded(BlockPos pos){
            if(pos.getY()<0||pos.getY()>=256)return Blocks.air.getDefaultState();
            ExtendedBlockStorage[] column=sections.get(key(pos.getX()>>4,pos.getZ()>>4)); if(column==null)return null;
            ExtendedBlockStorage section=column[pos.getY()>>4];return section==null?Blocks.air.getDefaultState():section.get(pos.getX()&15,pos.getY()&15,pos.getZ()&15);
        }
        void put(BlockPos pos,IBlockState state){ExtendedBlockStorage[] column=sections.computeIfAbsent(key(pos.getX()>>4,pos.getZ()>>4),k->new ExtendedBlockStorage[16]);
            int y=pos.getY()>>4;if(column[y]==null)column[y]=new ExtendedBlockStorage(y*16,false);column[y].set(pos.getX()&15,pos.getY()&15,pos.getZ()&15,state);}
        void bed(BlockPos foot){put(foot,Blocks.bed.getDefaultState().withProperty(BlockBed.FACING,EnumFacing.EAST).withProperty(BlockBed.PART,BlockBed.EnumPartType.FOOT));
            put(foot.east(),Blocks.bed.getDefaultState().withProperty(BlockBed.FACING,EnumFacing.EAST).withProperty(BlockBed.PART,BlockBed.EnumPartType.HEAD));}
    }
}
