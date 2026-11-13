package net.minecraft.client.renderer.chunk;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/** Independent coordinate/BFS oracle; no production masks or neighbor lookup. */
final class VisGraphTestSupport
{
    static final String[] SCENES = {"empty", "solid", "floor", "half_solid", "closed_room", "corner_tunnel", "checkerboard", "random_35_percent"};
    static final BlockPos[] POSITIONS = new BlockPos[4096];
    private static final int[][] DELTAS = {{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    static
    {
        for (int y = 0; y < 16; ++y)
            for (int z = 0; z < 16; ++z)
                for (int x = 0; x < 16; ++x)
                    POSITIONS[index(x,y,z)] = new BlockPos(x,y,z);
    }

    static boolean[] scene(String name)
    {
        boolean[] opaque = new boolean[4096];
        Random random = new Random(0x5eccfb7bL);
        for (int y = 0; y < 16; ++y)
            for (int z = 0; z < 16; ++z)
                for (int x = 0; x < 16; ++x)
                {
                    boolean solid;
                    switch (name)
                    {
                        case "empty": solid = false; break;
                        case "solid": solid = true; break;
                        case "floor": solid = y == 0; break;
                        case "half_solid": solid = y < 8; break;
                        case "closed_room": solid = boundary(x,y,z); break;
                        case "corner_tunnel": solid = !(y == 8 && (z == 8 || x == 15 && z <= 8)); break;
                        case "checkerboard": solid = ((x+y+z)&1) == 0; break;
                        case "random_35_percent": solid = random.nextDouble() < .35; break;
                        default: throw new IllegalArgumentException(name);
                    }
                    opaque[index(x,y,z)] = solid;
                }
        return opaque;
    }

    static VisGraph graph(boolean[] opaque)
    {
        VisGraph graph = new VisGraph();
        for (int i = 0; i < opaque.length; ++i)
            if (opaque[i]) graph.func_178606_a(POSITIONS[i]);
        return graph;
    }

    static boolean[][] reference(boolean[] opaque)
    {
        boolean[][] visible = new boolean[6][6];
        int count = 0;
        for (boolean value : opaque) if (value) ++count;
        if (count < 256)
        {
            for (boolean[] row : visible) Arrays.fill(row, true);
            return visible;
        }
        boolean[] visited = opaque.clone();
        for (int y = 0; y < 16; ++y)
            for (int z = 0; z < 16; ++z)
                for (int x = 0; x < 16; ++x)
                    if (boundary(x,y,z) && !visited[index(x,y,z)])
                    {
                        Set<EnumFacing> faces = fill(visited,x,y,z);
                        for (EnumFacing a : faces)
                            for (EnumFacing b : faces) visible[a.ordinal()][b.ordinal()] = true;
                    }
        return visible;
    }

    static Set<EnumFacing> fromStart(boolean[] opaque, BlockPos start)
    {
        return fill(opaque.clone(), start.getX() & 15, start.getY() & 15, start.getZ() & 15);
    }

    private static Set<EnumFacing> fill(boolean[] visited, int x, int y, int z)
    {
        Set<EnumFacing> faces = EnumSet.noneOf(EnumFacing.class);
        Queue<int[]> pending = new ArrayDeque<int[]>();
        pending.add(new int[]{x,y,z});
        visited[index(x,y,z)] = true;
        while (!pending.isEmpty())
        {
            int[] cell = pending.remove();
            if (cell[0] == 0) faces.add(EnumFacing.WEST);
            if (cell[0] == 15) faces.add(EnumFacing.EAST);
            if (cell[1] == 0) faces.add(EnumFacing.DOWN);
            if (cell[1] == 15) faces.add(EnumFacing.UP);
            if (cell[2] == 0) faces.add(EnumFacing.NORTH);
            if (cell[2] == 15) faces.add(EnumFacing.SOUTH);
            for (int[] delta : DELTAS)
            {
                int nx = cell[0] + delta[0], ny = cell[1] + delta[1], nz = cell[2] + delta[2];
                if (nx < 0 || nx >= 16 || ny < 0 || ny >= 16 || nz < 0 || nz >= 16) continue;
                int next = index(nx,ny,nz);
                if (!visited[next])
                {
                    visited[next] = true;
                    pending.add(new int[]{nx,ny,nz});
                }
            }
        }
        return faces;
    }

    static long signature(SetVisibility visibility)
    {
        long result = 0;
        for (EnumFacing a : EnumFacing.VALUES)
            for (EnumFacing b : EnumFacing.VALUES)
                if (visibility.isVisible(a,b)) result |= 1L << (a.ordinal()*6+b.ordinal());
        return result;
    }

    private static boolean boundary(int x, int y, int z) { return x==0 || x==15 || y==0 || y==15 || z==0 || z==15; }
    private static int index(int x, int y, int z) { return (y*16+z)*16+x; }
}
