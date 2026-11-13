package me.youngjibbit.utils.render.shader.blur;

import org.junit.Test;
import java.nio.FloatBuffer;
import java.util.Random;
import static org.junit.Assert.*;

public class PairedGaussianKernelCacheTest {
    @Test public void pairedInterpolationReconstructsTheOriginalClampedKernel() {
        GaussianKernelCache original=new GaussianKernelCache();
        PairedGaussianKernelCache paired=new PairedGaussianKernelCache();
        double[] signal=new double[37];Random random=new Random(78420L);
        for(int i=0;i<signal.length;i++)signal[i]=random.nextDouble()*255;
        for(int radius=1;radius<=127;radius++){
            FloatBuffer weights=original.get(radius);
            PairedGaussianKernelCache.Kernel kernel=paired.get(radius,weights);
            assertEquals((radius+1)/2,kernel.pairs());
            assertTrue(kernel.textureSamples()<=2*radius+1);
            if(radius>1)assertTrue(kernel.textureSamples()<2*radius+1);
            for(int center=0;center<signal.length;center++){
                double reference=sample(signal,center)*weights.get(0);
                for(int i=1;i<=radius;i++)reference+=(sample(signal,center+i)+sample(signal,center-i))*weights.get(i);
                double combined=sample(signal,center)*kernel.center;
                for(int i=0;i<kernel.pairs();i++)combined+=(linear(signal,center+kernel.offsets.get(i))+linear(signal,center-kernel.offsets.get(i)))*kernel.weights.get(i);
                assertEquals("radius="+radius+" pixel="+center,reference/kernel.total,combined/kernel.total,.0001);
            }
        }
    }

    @Test public void oddLastTapAndBorrowedBufferPositionsAreRetained() {
        GaussianKernelCache original=new GaussianKernelCache();PairedGaussianKernelCache paired=new PairedGaussianKernelCache();
        PairedGaussianKernelCache.Kernel first=paired.get(7,original.get(7));
        assertEquals(7,first.offsets.get(first.pairs()-1),0);
        assertEquals(original.get(7).get(7),first.weights.get(first.pairs()-1),0);
        first.weights.position(2);first.offsets.limit(2);
        PairedGaussianKernelCache.Kernel reused=paired.get(7,original.get(7));
        assertSame(first,reused);assertEquals(0,reused.weights.position());assertEquals(4,reused.offsets.limit());
        assertEquals(9,reused.textureSamples());assertEquals(129,paired.get(127,original.get(127)).textureSamples());
    }

    @Test public void malformedRadiusOrWeightsAreRejected() {
        PairedGaussianKernelCache cache=new PairedGaussianKernelCache();
        for(int radius:new int[]{0,128})try{cache.get(radius,FloatBuffer.allocate(129));fail("radius accepted");}catch(IllegalArgumentException expected){}
        try{cache.get(7,FloatBuffer.allocate(7));fail("short kernel accepted");}catch(IllegalArgumentException expected){}
    }

    private static double sample(double[] signal,int index){return signal[Math.max(0,Math.min(signal.length-1,index))];}
    private static double linear(double[] signal,double index){int a=(int)Math.floor(index);double fraction=index-a;return sample(signal,a)*(1-fraction)+sample(signal,a+1)*fraction;}
}
