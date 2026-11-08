package me.youngjibbit.core.combat.ai;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class NeutronAiManualDatasetTest {
    private static NeutronAiManualDataset.Frame row(long elapsedMs) {
        float[] inputs=new float[NeutronAiCheckpoint.INPUTS];
        inputs[0]=.25f;inputs[40]=-.5f;
        return new NeutronAiManualDataset.Frame(elapsedMs,elapsedMs==0,elapsedMs==50,
                inputs,new float[]{.2f,-.1f});
    }

    @Test public void fixedVersionedFormatRoundTripsAndKeepsImmutableManualOnlyFrames() {
        float[] source=new float[NeutronAiCheckpoint.INPUTS],label={.4f,.2f};
        source[0]=.1f;
        NeutronAiManualDataset.Frame first=new NeutronAiManualDataset.Frame(0,true,false,source,label);
        source[0]=.9f;label[0]=.7f;
        assertEquals(.1f,first.input()[0],0);
        assertEquals(.4f,first.label()[0],0);
        float[] exposed=first.input();exposed[0]=1;
        assertEquals(.1f,first.input()[0],0);
        NeutronAiManualDataset dataset=NeutronAiManualDataset.of(Arrays.asList(first,row(50)));
        assertEquals(2,dataset.size());
        assertEquals(2,dataset.manualClicks());
        assertEquals(1,dataset.manualReactions());
        byte[] bytes=dataset.encode();
        assertEquals(32+2*(8+4+4*(NeutronAiCheckpoint.INPUTS+2))+32,bytes.length);
        assertEquals(0x4e4d4434,ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt());
        NeutronAiManualDataset recovered=NeutronAiManualDataset.decode(bytes);
        assertNotNull(recovered);
        assertEquals(dataset.size(),recovered.size());
        assertArrayEquals(first.input(),recovered.frames().get(0).input(),0);
        assertArrayEquals(first.label(),recovered.frames().get(0).label(),0);
        assertEquals(50,recovered.frames().get(1).elapsedMs);
    }

    @Test public void corruptDigestFormatProtocolAndTruncatedFilesRejectWithoutParse() {
        byte[] bytes=NeutronAiManualDataset.of(Arrays.asList(row(0),row(50))).encode();
        assertNull(NeutronAiManualDataset.decode(null));
        assertNull(NeutronAiManualDataset.decode(Arrays.copyOf(bytes,bytes.length-1)));
        assertNull(NeutronAiManualDataset.decode(new byte[NeutronAiManualDataset.MAX_FILE_BYTES+1]));
        for(int bytePosition:new int[]{0,4,8,12,16,20,24,28,32,45,bytes.length-1}) {
            byte[] corrupt=bytes.clone();corrupt[bytePosition]^=1;
            assertNull("byte "+bytePosition,NeutronAiManualDataset.decode(corrupt));
        }
    }

    @Test public void outOfRangeNonfiniteAndWrongDimensionsAreRejected() {
        float[] inputs=new float[NeutronAiCheckpoint.INPUTS];
        float[] labels={.2f,.4f};
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,1.5f}) {
            inputs[10]=invalid;
            try {new NeutronAiManualDataset.Frame(0,false,false,inputs,labels);fail("inputs");}
            catch(IllegalArgumentException expected) { }
            inputs[10]=0;labels[1]=invalid;
            try {new NeutronAiManualDataset.Frame(0,false,false,inputs,labels);fail("labels");}
            catch(IllegalArgumentException expected) { }
            labels[1]=0;
        }
        try {new NeutronAiManualDataset.Frame(0,true,false,new float[159],labels);fail();}
        catch(IllegalArgumentException expected){}
        try {new NeutronAiManualDataset.Frame(-1,true,false,inputs,labels);fail();}
        catch(IllegalArgumentException expected){}
    }

    @Test public void chronologyDurationAndGapNeverAdmitStitchedOrFutureSessions() {
        for(List<NeutronAiManualDataset.Frame> invalid:Arrays.asList(
                Arrays.asList(row(50)),
                Arrays.asList(row(0),row(0)),
                Arrays.asList(row(0),row(251)),
                Arrays.asList(row(0),row(80),row(75)))) {
            try {NeutronAiManualDataset.of(invalid);fail("invalid time sequence");}
            catch(IllegalArgumentException expected){}
        }
        try {NeutronAiManualDataset.of(new ArrayList<NeutronAiManualDataset.Frame>());fail();}
        catch(IllegalArgumentException expected){}
        try {row(NeutronAiManualDataset.MAX_DURATION_MS+1);fail();}
        catch(IllegalArgumentException expected){}
    }

    @Test public void hardFrameCountAndTotalByteCapsRejectOvercommit() {
        assertTrue(NeutronAiManualDataset.MAX_FILE_BYTES<6_000_000);
        List<NeutronAiManualDataset.Frame> rows=new ArrayList<>();
        for(int i=0;i<NeutronAiManualDataset.MAX_FRAMES;i++)rows.add(row(i*50L));
        NeutronAiManualDataset max=NeutronAiManualDataset.of(rows);
        assertEquals(NeutronAiManualDataset.MAX_FILE_BYTES,max.encode().length);
        rows.add(row(rows.size()*50L));
        try {NeutronAiManualDataset.of(rows);fail("more than bounded rows");}
        catch(IllegalArgumentException expected){}
    }
}
