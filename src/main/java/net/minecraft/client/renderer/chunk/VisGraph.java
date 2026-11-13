package net.minecraft.client.renderer.chunk;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;
import me.youngjibbit.core.diagnostics.PerformanceOptions;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IntegerCache;

public class VisGraph
{
    private static final int field_178616_a = (int)Math.pow(16.0D, 0.0D);
    private static final int field_178614_b = (int)Math.pow(16.0D, 1.0D);
    private static final int field_178615_c = (int)Math.pow(16.0D, 2.0D);
    private final BitSet field_178612_d = new BitSet(4096);
    private static final int[] field_178613_e = new int[1352];
    private int field_178611_f = 4096;
    private static final ThreadLocal<int[]> FAST_QUEUE = new ThreadLocal<int[]>()
    {
        @Override protected int[] initialValue() { return new int[4096]; }
    };
    private static final int FACE_DOWN = 1 << EnumFacing.DOWN.ordinal();
    private static final int FACE_UP = 1 << EnumFacing.UP.ordinal();
    private static final int FACE_NORTH = 1 << EnumFacing.NORTH.ordinal();
    private static final int FACE_SOUTH = 1 << EnumFacing.SOUTH.ordinal();
    private static final int FACE_WEST = 1 << EnumFacing.WEST.ordinal();
    private static final int FACE_EAST = 1 << EnumFacing.EAST.ordinal();

    public void func_178606_a(BlockPos pos)
    {
        this.field_178612_d.set(getIndex(pos), true);
        --this.field_178611_f;
    }

    private static int getIndex(BlockPos pos)
    {
        return getIndex(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    private static int getIndex(int x, int y, int z)
    {
        return x << 0 | y << 8 | z << 4;
    }

    public SetVisibility computeVisibility()
    {
        return this.computeVisibility(PerformanceOptions.isFastVisibilityGraphEnabled());
    }

    SetVisibility computeVisibility(boolean fast)
    {
        SetVisibility setvisibility = new SetVisibility();

        if (4096 - this.field_178611_f < 256)
        {
            setvisibility.setAllVisible(true);
        }
        else if (this.field_178611_f == 0)
        {
            setvisibility.setAllVisible(false);
        }
        else
        {
            for (int i : field_178613_e)
            {
                if (!this.field_178612_d.get(i))
                {
                    if (fast) setvisibility.setManyVisibleMask(this.collectVisibleFaceMask(i));
                    else setvisibility.setManyVisible(this.func_178604_a(i));
                }
            }
        }

        return setvisibility;
    }

    public Set<EnumFacing> func_178609_b(BlockPos pos)
    {
        if (PerformanceOptions.isFastVisibilityGraphEnabled())
        {
            int faces = this.collectVisibleFaceMask(getIndex(pos));
            Set<EnumFacing> result = EnumSet.noneOf(EnumFacing.class);
            for (EnumFacing face : EnumFacing.VALUES)
                if ((faces & 1 << face.ordinal()) != 0) result.add(face);
            return result;
        }
        return this.func_178604_a(getIndex(pos));
    }

    /**
     * Each cell is marked before enqueue, so this owner-thread queue cannot
     * exceed the section's 4096 cells. No callbacks or nested traversal occur.
     */
    private int collectVisibleFaceMask(int start)
    {
        int[] queue = FAST_QUEUE.get();
        int head = 0;
        int tail = 1;
        int faces = 0;
        queue[0] = start;
        this.field_178612_d.set(start);

        while (head < tail)
        {
            int index = queue[head++];
            int x = index & 15;
            int y = index >>> 8 & 15;
            int z = index >>> 4 & 15;

            if (y == 0) faces |= FACE_DOWN;
            else if (!this.field_178612_d.get(index - 256))
            {
                this.field_178612_d.set(index - 256);
                queue[tail++] = index - 256;
            }
            if (y == 15) faces |= FACE_UP;
            else if (!this.field_178612_d.get(index + 256))
            {
                this.field_178612_d.set(index + 256);
                queue[tail++] = index + 256;
            }
            if (z == 0) faces |= FACE_NORTH;
            else if (!this.field_178612_d.get(index - 16))
            {
                this.field_178612_d.set(index - 16);
                queue[tail++] = index - 16;
            }
            if (z == 15) faces |= FACE_SOUTH;
            else if (!this.field_178612_d.get(index + 16))
            {
                this.field_178612_d.set(index + 16);
                queue[tail++] = index + 16;
            }
            if (x == 0) faces |= FACE_WEST;
            else if (!this.field_178612_d.get(index - 1))
            {
                this.field_178612_d.set(index - 1);
                queue[tail++] = index - 1;
            }
            if (x == 15) faces |= FACE_EAST;
            else if (!this.field_178612_d.get(index + 1))
            {
                this.field_178612_d.set(index + 1);
                queue[tail++] = index + 1;
            }
        }
        return faces;
    }

    private Set<EnumFacing> func_178604_a(int p_178604_1_)
    {
        Set<EnumFacing> set = EnumSet.<EnumFacing>noneOf(EnumFacing.class);
        Queue<Integer> queue = new ArrayDeque(384);
        queue.add(IntegerCache.func_181756_a(p_178604_1_));
        this.field_178612_d.set(p_178604_1_, true);

        while (!((Queue)queue).isEmpty())
        {
            int i = ((Integer)queue.poll()).intValue();
            this.func_178610_a(i, set);

            for (EnumFacing enumfacing : EnumFacing.VALUES)
            {
                int j = this.func_178603_a(i, enumfacing);

                if (j >= 0 && !this.field_178612_d.get(j))
                {
                    this.field_178612_d.set(j, true);
                    queue.add(IntegerCache.func_181756_a(j));
                }
            }
        }

        return set;
    }

    private void func_178610_a(int p_178610_1_, Set<EnumFacing> p_178610_2_)
    {
        int i = p_178610_1_ >> 0 & 15;

        if (i == 0)
        {
            p_178610_2_.add(EnumFacing.WEST);
        }
        else if (i == 15)
        {
            p_178610_2_.add(EnumFacing.EAST);
        }

        int j = p_178610_1_ >> 8 & 15;

        if (j == 0)
        {
            p_178610_2_.add(EnumFacing.DOWN);
        }
        else if (j == 15)
        {
            p_178610_2_.add(EnumFacing.UP);
        }

        int k = p_178610_1_ >> 4 & 15;

        if (k == 0)
        {
            p_178610_2_.add(EnumFacing.NORTH);
        }
        else if (k == 15)
        {
            p_178610_2_.add(EnumFacing.SOUTH);
        }
    }

    private int func_178603_a(int p_178603_1_, EnumFacing p_178603_2_)
    {
        switch (p_178603_2_)
        {
            case DOWN:
                if ((p_178603_1_ >> 8 & 15) == 0)
                {
                    return -1;
                }

                return p_178603_1_ - field_178615_c;

            case UP:
                if ((p_178603_1_ >> 8 & 15) == 15)
                {
                    return -1;
                }

                return p_178603_1_ + field_178615_c;

            case NORTH:
                if ((p_178603_1_ >> 4 & 15) == 0)
                {
                    return -1;
                }

                return p_178603_1_ - field_178614_b;

            case SOUTH:
                if ((p_178603_1_ >> 4 & 15) == 15)
                {
                    return -1;
                }

                return p_178603_1_ + field_178614_b;

            case WEST:
                if ((p_178603_1_ >> 0 & 15) == 0)
                {
                    return -1;
                }

                return p_178603_1_ - field_178616_a;

            case EAST:
                if ((p_178603_1_ >> 0 & 15) == 15)
                {
                    return -1;
                }

                return p_178603_1_ + field_178616_a;

            default:
                return -1;
        }
    }

    static
    {
        int i = 0;
        int j = 15;
        int k = 0;

        for (int l = 0; l < 16; ++l)
        {
            for (int i1 = 0; i1 < 16; ++i1)
            {
                for (int j1 = 0; j1 < 16; ++j1)
                {
                    if (l == 0 || l == 15 || i1 == 0 || i1 == 15 || j1 == 0 || j1 == 15)
                    {
                        field_178613_e[k++] = getIndex(l, i1, j1);
                    }
                }
            }
        }
    }
}
