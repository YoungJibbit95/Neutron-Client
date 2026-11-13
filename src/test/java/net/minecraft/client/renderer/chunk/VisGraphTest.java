package net.minecraft.client.renderer.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import me.youngjibbit.core.diagnostics.PerformanceOptions;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisGraphTest
{
    @After public void resetOptions() { PerformanceOptions.defaults(); }

    @Test public void structuredSectionsMatchIndependentGeometryOracle()
    {
        for (String name : VisGraphTestSupport.SCENES) check(VisGraphTestSupport.scene(name));
    }

    @Test public void randomizedSectionsMatchAllThirtySixPairs()
    {
        Random random = new Random(19371113L);
        double[] densities = {.02, .065, .15, .35, .5, .8, .95};
        for (double density : densities)
            for (int sample = 0; sample < 20; ++sample)
            {
                boolean[] opaque = new boolean[4096];
                for (int i = 0; i < opaque.length; ++i) opaque[i] = random.nextDouble() < density;
                check(opaque);
            }
    }

    @Test public void conservativeLowOccupancyShortcutRemainsEnabled()
    {
        boolean[] opaque = new boolean[4096];
        for (int i = 0; i < 255; ++i) opaque[i] = true;
        for (boolean fast : new boolean[]{false,true})
        {
            SetVisibility visible = VisGraphTestSupport.graph(opaque).computeVisibility(fast);
            for (EnumFacing a : EnumFacing.VALUES)
                for (EnumFacing b : EnumFacing.VALUES) assertTrue(visible.isVisible(a,b));
        }
        opaque[255] = true;
        check(opaque);
    }

    @Test public void publicStrategyToggleAndNegativeCoordinatesPreserveFaces()
    {
        boolean[] opaque = VisGraphTestSupport.scene("corner_tunnel");
        for (boolean fast : new boolean[]{false,true})
        {
            PerformanceOptions.configure(false,fast,false,1);
            VisGraph shifted = new VisGraph();
            for (int i = 0; i < opaque.length; ++i)
                if (opaque[i])
                {
                    BlockPos pos = VisGraphTestSupport.POSITIONS[i];
                    shifted.func_178606_a(new BlockPos(pos.getX()-32,pos.getY()-48,pos.getZ()-16));
                }
            assertMatrix(VisGraphTestSupport.reference(opaque), shifted.computeVisibility());
            for (BlockPos start : new BlockPos[]{new BlockPos(0,8,8),new BlockPos(15,8,0),new BlockPos(8,8,8),new BlockPos(0,0,0)})
                assertEquals(VisGraphTestSupport.fromStart(opaque,start), VisGraphTestSupport.graph(opaque).func_178609_b(start));
        }
    }

    @Test public void consumingTraversalsRemainEquivalent()
    {
        for (String scene : VisGraphTestSupport.SCENES)
        {
            boolean[] opaque = VisGraphTestSupport.scene(scene);
            VisGraph legacy = VisGraphTestSupport.graph(opaque);
            VisGraph fast = VisGraphTestSupport.graph(opaque);
            assertEquals(VisGraphTestSupport.signature(legacy.computeVisibility(false)), VisGraphTestSupport.signature(fast.computeVisibility(true)));
            for (BlockPos start : new BlockPos[]{new BlockPos(0,0,0),new BlockPos(15,15,15),new BlockPos(8,8,8)})
            {
                PerformanceOptions.configure(false,false,false,1);
                Set<EnumFacing> expected = legacy.func_178609_b(start);
                PerformanceOptions.configure(false,true,false,1);
                assertEquals(expected,fast.func_178609_b(start));
            }
        }
    }

    @Test public void workerThreadsHaveIndependentBoundedScratch() throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try
        {
            List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
            for (int worker = 0; worker < 4; ++worker)
            {
                final int seed = worker;
                results.add(pool.submit(new Callable<Boolean>()
                {
                    @Override public Boolean call()
                    {
                        Random random = new Random(seed);
                        for (int sample = 0; sample < 20; ++sample)
                        {
                            boolean[] opaque = new boolean[4096];
                            for (int i = 0; i < opaque.length; ++i) opaque[i] = random.nextBoolean();
                            assertMatrix(VisGraphTestSupport.reference(opaque), VisGraphTestSupport.graph(opaque).computeVisibility(true));
                        }
                        return true;
                    }
                }));
            }
            for (Future<Boolean> result : results) assertTrue(result.get());
        }
        finally { pool.shutdownNow(); }
    }

    private static void check(boolean[] opaque)
    {
        boolean[][] expected = VisGraphTestSupport.reference(opaque);
        assertMatrix(expected, VisGraphTestSupport.graph(opaque).computeVisibility(false));
        assertMatrix(expected, VisGraphTestSupport.graph(opaque).computeVisibility(true));
    }

    private static void assertMatrix(boolean[][] expected, SetVisibility actual)
    {
        for (EnumFacing a : EnumFacing.VALUES)
            for (EnumFacing b : EnumFacing.VALUES)
                assertEquals(a+"/"+b, expected[a.ordinal()][b.ordinal()], actual.isVisible(a,b));
    }
}
