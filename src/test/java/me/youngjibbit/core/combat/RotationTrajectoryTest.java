package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class RotationTrajectoryTest {
    @Test public void restoredRandomPatternsUseControlledTimeAndKeepTheFinalSubstepAndBounds() {
        for(RotationProfile.Pattern pattern:RotationProfile.Pattern.values()) {
            AtomicLong a=new AtomicLong(),b=new AtomicLong();Object target=new Object();
            RotationTrajectory slow=new RotationTrajectory(a::get,68),fast=new RotationTrajectory(b::get,68);
            RotationProfile p=new RotationProfile(),q=new RotationProfile();p.pattern=q.pattern=pattern;p.yawSpeed=q.yawSpeed=180;p.pitchSpeed=q.pitchSpeed=1;q.frequency=2;
            slow.advance(target,0,0,30,80,1,p);fast.advance(target,0,0,30,80,1,q);
            for(int i=1;i<50;i++) {
                a.set(i*74_000_000L);b.set(i*37_000_000L);
                float[] expected=slow.advance(target,0,0,30,80,1,p),actual=fast.advance(target,0,0,30,80,1,q);
                assertEquals(pattern.toString(),expected[0],actual[0],0);assertTrue(Float.isFinite(actual[1]));
                for(int call=0;call<10;call++)assertArrayEquals(actual,fast.advance(target,actual[0],actual[1],30,80,1,q),0);
            }
        }
    }
    @Test public void heuristicPointsStayInsideThinGeometryAndHeuristicTurnsConvergeWithoutTheHistoricalZeroAngles() {
        AtomicLong clock=new AtomicLong();RotationTrajectory trajectory=new RotationTrajectory(clock::get,4);RotationProfile profile=new RotationProfile();profile.heuristics=true;
        AxisAlignedBB box=new AxisAlignedBB(1,2,3,1.0001,2.0002,3.0003);Vec3 base=new Vec3(1.00005,2.0001,3.00015);Object target=new Object();
        for(int i=0;i<80;i++){clock.set(i*50_000_000L);Vec3 point=trajectory.point(target,box,base,profile);assertTrue(box.isVecInside(point));}
        profile.randomization=false;float yaw=40,pitch=20;
        for(int i=0;i<40;i++){clock.addAndGet(50_000_000L);float[] turn=trajectory.advance(target,yaw,pitch,45,22,1,profile);assertTrue(turn[0]>=yaw&&turn[0]<=45);assertTrue(turn[1]>=pitch&&turn[1]<=22);yaw=turn[0];pitch=turn[1];}
        assertEquals(45,yaw,.001);assertEquals(22,pitch,.001);
    }
    private float[] series(RotationProfile p) {
        AtomicLong clock=new AtomicLong();RotationTrajectory path=new RotationTrajectory(clock::get,76421);Object target=new Object();
        path.sample(target,p,0);float[] values=new float[64];
        for(int i=0;i<values.length;i++) { clock.set(37_000_000L+i*137_000_000L);values[i]=path.sample(target,p,i%2);assertTrue(Float.isFinite(values[i])); }
        return values;
    }
    private void different(float[] a,float[] b) {
        double difference=0;for(int i=0;i<a.length;i++)difference+=Math.abs(a[i]-b[i]);
        assertTrue("parameter must affect its actual sampled path",difference>1e-4);
    }
    private void parameter(Consumer<RotationProfile> setup,Consumer<RotationProfile> change) {
        RotationProfile a=new RotationProfile(),b=new RotationProfile();setup.accept(a);setup.accept(b);change.accept(b);different(series(a),series(b));
    }
    @Test public void everyNoiseAndFractalChoiceHasADistinctExecutedPath() {
        List<float[]> noise=new ArrayList<>(),fractal=new ArrayList<>();
        for(FastNoiseLite.NoiseType type:FastNoiseLite.NoiseType.values()) { RotationProfile p=new RotationProfile();p.noise=type;noise.add(series(p)); }
        for(FastNoiseLite.FractalType type:FastNoiseLite.FractalType.values()) { RotationProfile p=new RotationProfile();p.fractal=type;fractal.add(series(p)); }
        for(List<float[]> paths:Arrays.asList(noise,fractal))for(int i=0;i<paths.size();i++)for(int j=0;j<i;j++)different(paths.get(i),paths.get(j));
    }
    @Test public void allCellularFractalAndDomainWarpParametersReachTheLibrary() {
        Consumer<RotationProfile> cellular=p->{p.noise=FastNoiseLite.NoiseType.Cellular;};
        for(FastNoiseLite.CellularDistanceFunction type:FastNoiseLite.CellularDistanceFunction.values())
            if(type!=FastNoiseLite.CellularDistanceFunction.EuclideanSq) parameter(cellular,p->p.cellularDistance=type);
        List<float[]> returns=new ArrayList<>();
        for(FastNoiseLite.CellularReturnType type:FastNoiseLite.CellularReturnType.values()) { RotationProfile p=new RotationProfile();cellular.accept(p);p.cellularReturn=type;returns.add(series(p)); }
        for(int i=0;i<returns.size();i++)for(int j=0;j<i;j++)different(returns.get(i),returns.get(j));
        parameter(cellular,p->p.cellularJitter=.2f);
        for(FastNoiseLite.FractalType type:new FastNoiseLite.FractalType[]{FastNoiseLite.FractalType.FBm,FastNoiseLite.FractalType.Ridged,FastNoiseLite.FractalType.PingPong}) {
            Consumer<RotationProfile> context=p->{p.fractal=type;};
            parameter(context,p->p.gain=.8f);parameter(context,p->p.lacunarity=3.1f);parameter(context,p->p.octaves=5);parameter(context,p->p.weighted=.8f);
        }
        parameter(p->p.fractal=FastNoiseLite.FractalType.PingPong,p->p.pingPong=3.5f);
        for(FastNoiseLite.FractalType type:new FastNoiseLite.FractalType[]{FastNoiseLite.FractalType.DomainWarpProgressive,FastNoiseLite.FractalType.DomainWarpIndependent}) {
            Consumer<RotationProfile> context=p->{p.fractal=type;};
            parameter(context,p->p.gain=.8f);parameter(context,p->p.lacunarity=3.1f);parameter(context,p->p.octaves=5);parameter(context,p->p.warpAmplitude=3);
            List<float[]> paths=new ArrayList<>();
            for(FastNoiseLite.DomainWarpType warp:FastNoiseLite.DomainWarpType.values()) { RotationProfile p=new RotationProfile();context.accept(p);p.warp=warp;paths.add(series(p)); }
            for(int i=0;i<paths.size();i++)for(int j=0;j<i;j++)different(paths.get(i),paths.get(j));
            RotationProfile zero=new RotationProfile(),base=new RotationProfile();context.accept(zero);zero.warpAmplitude=0;
            assertArrayEquals(series(base),series(zero),0);
        }
    }
    @Test public void frequencyScalesTimeRatherThanTheNumberOfCalls() {
        AtomicLong a=new AtomicLong(),b=new AtomicLong();Object target=new Object();
        RotationTrajectory slow=new RotationTrajectory(a::get,5),fast=new RotationTrajectory(b::get,5);
        RotationProfile p=new RotationProfile(),q=new RotationProfile();q.frequency=2;
        slow.sample(target,p,0);fast.sample(target,q,0);
        for(int i=1;i<100;i++) {
            a.set(i*46_000_000L);b.set(i*23_000_000L);
            float expected=slow.sample(target,p,0);assertEquals(expected,fast.sample(target,q,0),0);
            for(int frame=0;frame<50;frame++) assertEquals(expected,fast.sample(target,q,0),0);
        }
    }
    private float[] first(RotationProfile p) { return new RotationTrajectory(()->0,42).advance(this,0,0,80,40,1,p); }
    @Test public void modesSpeedCapsAndInterpolationHaveDifferentDefinedEffects() {
        RotationProfile p=new RotationProfile();p.randomization=false;
        float[] basic=first(p);assertEquals(28,basic[0],0);assertEquals(28,basic[1],0);
        p.yawSpeed=5;p.pitchSpeed=9;assertArrayEquals(new float[]{5,9},first(p),0);
        p.yawSpeed=p.pitchSpeed=180;p.mode=RotationProfile.Mode.SMOOTH;
        float[] smooth=first(p);p.interpolation=10;assertTrue(first(p)[0]<smooth[0]);
        p.mode=RotationProfile.Mode.ADVANCED;p.interpolation=5;float[] advanced=first(p);assertTrue(advanced[0]<smooth[0]);
        p.minSpeed=5;p.maxSpeed=20;float min=first(p)[0];p.minSpeed=10;assertTrue(first(p)[0]>min);
        float max=first(p)[0];p.maxSpeed=60;assertTrue(first(p)[0]>max);
        p.minSpeed=p.maxSpeed=180;p.interpolation=1;float one=first(p)[0];p.interpolation=20;assertTrue(first(p)[0]<one);
    }
    @Test public void strengthAndAxisOffsetsControlOnlyTheirIntendedAmplitudesAndZeroIsTheBasePath() {
        RotationProfile p=new RotationProfile();p.yawSpeed=p.pitchSpeed=180;p.jitter=0;p.yawOffset=3;p.pitchOffset=2;
        float[] full=first(p);p.strength=.5f;float[] half=first(p);
        assertEquals((full[0]-80)*.5,half[0]-80,1e-5);assertEquals((full[1]-40)*.5,half[1]-40,1e-5);
        p.strength=0;assertArrayEquals(new float[]{80,40},first(p),0);
        p.strength=1;p.randomization=false;assertArrayEquals(new float[]{80,40},first(p),0);
        p.randomization=true;p.noiseEnabled=false;assertArrayEquals(new float[]{80,40},first(p),0);
        p.noiseEnabled=true;p.yawOffset=0;assertEquals(80,first(p)[0],0);assertNotEquals(40,first(p)[1],0);
        p.yawOffset=3;p.pitchOffset=0;assertNotEquals(80,first(p)[0],0);assertEquals(40,first(p)[1],0);
        p.yawOffset=p.pitchOffset=0;p.jitter=0;assertArrayEquals(new float[]{80,40},first(p),0);
    }
    @Test public void randomPointsStayInsideActualTargetGeometryAndRadiusZeroKeepsTheBasePoint() {
        AtomicLong clock=new AtomicLong();RotationTrajectory path=new RotationTrajectory(clock::get,9);Object target=new Object();
        AxisAlignedBB box=new AxisAlignedBB(-.3,0,-.3,.3,1.8,.3);Vec3 base=new Vec3(0,1.4,0);
        RotationProfile p=new RotationProfile();p.randomHitVec=true;p.radius=5;
        for(FastNoiseLite.NoiseType type:FastNoiseLite.NoiseType.values())for(int i=0;i<60;i++) {
            p.noise=type;clock.set(i*121_000_000L);Vec3 point=path.point(target,box,base,p);
            assertTrue(point.xCoord>box.minX && point.xCoord<box.maxX);assertTrue(point.yCoord>box.minY && point.yCoord<box.maxY);assertTrue(point.zCoord>box.minZ && point.zCoord<box.maxZ);
        }
        p.radius=0;assertSame(base,path.point(target,box,base,p));p.radius=5;p.strength=0;assertSame(base,path.point(target,box,base,p));
        p.strength=1;p.randomHitVec=false;assertSame(base,path.point(target,box,base,p));
    }
    @Test public void targetChangesRestartOwnedTimeAndRepeatedCallsCannotAddRotationSteps() {
        AtomicLong time=new AtomicLong();RotationTrajectory a=new RotationTrajectory(time::get,42),b=new RotationTrajectory(time::get,42);
        RotationProfile p=new RotationProfile();p.randomization=false;Object target=new Object();
        float[] once=a.advance(target,0,0,100,50,1,p);float[] same=b.advance(target,0,0,100,50,1,p);
        for(int i=0;i<1000;i++) { a.sample(target,p,0);assertArrayEquals(once,a.advance(target,once[0],once[1],100,50,1,p),0); }
        time.set(50_000_000L);assertArrayEquals(b.advance(target,same[0],same[1],100,50,1,p),a.advance(target,once[0],once[1],100,50,1,p),0);
        p.randomization=true;Object replacement=new Object();float restarted=a.sample(replacement,p,0);
        time.set(8_000_000_000L);Object second=new Object(); // Same second acquisition, different absolute time.
        assertEquals(restarted,b.sample(second,p,0),0);
    }
}
