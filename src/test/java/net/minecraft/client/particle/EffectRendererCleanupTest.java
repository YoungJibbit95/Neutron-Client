package net.minecraft.client.particle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.youngjibbit.core.diagnostics.PerformanceOptions;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ReportedException;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectRendererCleanupTest
{
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }
    @After public void resetOptions() { PerformanceOptions.defaults(); }

    @Test public void actualLayerUpdatePreservesSurvivorsAndOffPath() throws Exception
    {
        for (boolean fast : new boolean[]{false,true})
        {
            PerformanceOptions.configure(fast,false,false,1);
            EffectRenderer renderer = new EffectRenderer(null,null);
            List<EntityFX> layer = new ArrayList<EntityFX>();
            List<EntityFX> expected = new ArrayList<EntityFX>();
            for (int i=0;i<128;++i)
            {
                Particle particle = new Particle(i,i%4==0);
                layer.add(particle);
                if (!particle.die) expected.add(particle);
            }
            update(renderer,layer);
            assertEquals(expected,layer);
            for (EntityFX survivor : layer) assertEquals(1,((Particle)survivor).ticks);
            Object scratch=ParticleRemovalBufferTest.field(renderer,"particleRemovalBuffer");
            if (fast) ParticleRemovalBufferTest.assertReleased(scratch);
            else assertNull(scratch);
        }
    }

    @Test public void entityIdChangesAfterDeathAreObservedAtCleanup() throws Exception
    {
        for (boolean fast : new boolean[]{false,true})
        {
            PerformanceOptions.configure(fast,false,false,1);
            EffectRenderer renderer = new EffectRenderer(null,null);
            List<EntityFX> layer = new ArrayList<EntityFX>();
            final Particle first = new Particle(0,true);
            layer.add(first);
            for (int i=1;i<128;++i) layer.add(new Particle(i,i<32));
            ((Particle)layer.get(1)).action=() -> first.setEntityId(100);
            update(renderer,layer);
            List<Integer> expected=new ArrayList<Integer>();
            for (int i=32;i<128;++i) if (i!=100) expected.add(i);
            assertEquals(expected,ids(layer));
        }
    }

    @Test public void particlesSpawnedDuringTickKeepExistingTickOrder() throws Exception
    {
        for (boolean fast : new boolean[]{false,true})
        {
            PerformanceOptions.configure(fast,false,false,1);
            final EffectRenderer renderer=new EffectRenderer(null,null);
            Particle spawner=new Particle(1,true), existing=new Particle(2,false), born=new Particle(3,false);
            spawner.action=() -> renderer.addEffect(born);
            renderer.addEffect(spawner);
            renderer.addEffect(existing);
            renderer.updateEffects();
            assertEquals(Arrays.asList(2,3),ids(layers(renderer)[0][1]));
            assertEquals(1,spawner.ticks);
            assertEquals(1,existing.ticks);
            assertEquals(1,born.ticks);
        }
    }

    @Test public void alphaMigrationStillReachesTheLaterLayer() throws Exception
    {
        for (boolean fast : new boolean[]{false,true})
        {
            PerformanceOptions.configure(fast,false,false,1);
            final EffectRenderer renderer=new EffectRenderer(null,null);
            final Particle migrating=new Particle(7,false);
            migrating.particleAlpha=.5F;
            migrating.action=() -> {
                renderer.moveToNoAlphaLayer(migrating);
                migrating.particleAlpha=1F;
            };
            renderer.addEffect(migrating);
            renderer.updateEffects();
            assertTrue(layers(renderer)[0][0].isEmpty());
            assertEquals(Arrays.asList(7),ids(layers(renderer)[0][1]));
            assertEquals("Existing iteration visits the later alpha layer",2,migrating.ticks);
        }
    }

    @Test public void nestedUpdateFallsBackWithoutLosingOuterDeaths() throws Exception
    {
        PerformanceOptions.configure(true,false,false,1);
        final EffectRenderer renderer=new EffectRenderer(null,null);
        final List<EntityFX> inner=new ArrayList<EntityFX>(Arrays.asList(new Particle(10,true),new Particle(11,false)));
        Particle dying=new Particle(1,true), nested=new Particle(2,false);
        nested.action=() -> updateUnchecked(renderer,inner);
        List<EntityFX> outer=new ArrayList<EntityFX>(Arrays.asList(dying,nested));
        update(renderer,outer);
        assertEquals(Arrays.asList(2),ids(outer));
        assertEquals(Arrays.asList(11),ids(inner));
        ParticleRemovalBufferTest.assertReleased(ParticleRemovalBufferTest.field(renderer,"particleRemovalBuffer"));
    }

    @Test public void failedTickReleasesScratchAndNextUpdateCanProceed() throws Exception
    {
        PerformanceOptions.configure(true,false,false,1);
        EffectRenderer renderer=new EffectRenderer(null,null);
        Particle broken=new Particle(2,false);
        broken.action=() -> { throw new IllegalStateException("expected tick fixture failure"); };
        try
        {
            update(renderer,new ArrayList<EntityFX>(Arrays.asList(new Particle(1,true),broken)));
            fail("Tick failure was swallowed");
        }
        catch (ReportedException expected) { assertNotNull(expected.getCrashReport()); }
        ParticleRemovalBufferTest.assertReleased(ParticleRemovalBufferTest.field(renderer,"particleRemovalBuffer"));
        List<EntityFX> next=new ArrayList<EntityFX>(Arrays.asList(new Particle(3,false)));
        update(renderer,next);
        assertEquals(Arrays.asList(3),ids(next));
        renderer.clearEffects(null);
        ParticleRemovalBufferTest.assertReleased(ParticleRemovalBufferTest.field(renderer,"particleRemovalBuffer"));
    }

    @Test public void existingTimeBudgetAndOldestEvictionArePreserved() throws Exception
    {
        for (boolean fast : new boolean[]{false,true})
        {
            PerformanceOptions.configure(fast,false,false,1);
            EffectRenderer renderer=new EffectRenderer(null,null);
            Particle slow=new Particle(1,false);
            slow.action=() -> {
                long until=System.currentTimeMillis()+25L;
                while (System.currentTimeMillis()<until)
                    try { Thread.sleep(1L); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new RuntimeException(failure); }
            };
            Particle middle=new Particle(2,false), last=new Particle(3,false);
            List<EntityFX> layer=new ArrayList<EntityFX>(Arrays.asList(slow,middle,last));
            update(renderer,layer);
            assertEquals(Arrays.asList(3),ids(layer));
            assertTrue(slow.isDead);
            assertTrue(middle.isDead);
            assertEquals(0,last.ticks);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EntityFX>[][] layers(EffectRenderer renderer) throws Exception
    {
        return (List<EntityFX>[][])ParticleRemovalBufferTest.field(renderer,"fxLayers");
    }

    private static List<Integer> ids(List<EntityFX> particles)
    {
        List<Integer> result=new ArrayList<Integer>();
        for (EntityFX particle : particles) result.add(particle.getEntityId());
        return result;
    }

    private static void update(EffectRenderer renderer,List<EntityFX> layer) throws Exception
    {
        Method method=EffectRenderer.class.getDeclaredMethod("updateEffectAlphaLayer",List.class);
        method.setAccessible(true);
        try { method.invoke(renderer,layer); }
        catch (InvocationTargetException failure)
        {
            Throwable cause=failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new AssertionError(cause);
        }
    }

    private static void updateUnchecked(EffectRenderer renderer,List<EntityFX> layer)
    {
        try { update(renderer,layer); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static final class Particle extends EntityFX
    {
        final boolean die;
        int ticks;
        Runnable action;
        Particle(int id,boolean die) { super(null,0,0,0); this.setEntityId(id); this.die=die; }
        @Override public void onUpdate()
        {
            ++this.ticks;
            if (this.action!=null) this.action.run();
            if (this.die) this.setDead();
        }
    }
}
