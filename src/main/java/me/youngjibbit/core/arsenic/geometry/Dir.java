/* Arsenic Client Development (c) 2022, MIT. Pinned to ee93d48a4b78b6383601c63b7269eb4c0e397e85.
 * See docs/third-party/ARSENIC.md and META-INF/licenses/arsenic.txt. */
package me.youngjibbit.core.arsenic.geometry;

public final class Dir {
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private Dir() {
    }

    public static int opposite(int d) {
        return d ^ 1;
    }

    public static int horizontal(int dx, int dz) {
        if (dx < 0) return WEST;
        if (dx > 0) return EAST;
        if (dz < 0) return NORTH;
        return SOUTH;
    }
}
