/* Arsenic Client Development (c) 2022, MIT. Pinned to ee93d48a4b78b6383601c63b7269eb4c0e397e85.
 * See docs/third-party/ARSENIC.md and META-INF/licenses/arsenic.txt. */
package me.youngjibbit.core.arsenic.scaffold;

import me.youngjibbit.core.arsenic.geometry.Box;

public interface ScaffoldWorld {

    final class Hit {
        public int x, y, z, face;
        public double hx, hy, hz;
    }

    boolean isAir(int x, int y, int z);

    boolean isFullCube(int x, int y, int z);

    boolean canPlaceOnSide(int x, int y, int z, int face);

    boolean boxFree(Box b);

    boolean rayTrace(double sx, double sy, double sz, double ex, double ey, double ez, Hit out);
}
