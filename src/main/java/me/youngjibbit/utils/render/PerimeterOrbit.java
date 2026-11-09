package me.youngjibbit.utils.render;

/**
 * Perimeter Orbit: a gradient phase travels along the actual contour of a
 * rounded rectangle, either clockwise or counter-clockwise.
 * <p>
 * The contour is parameterised by arc length over the four straight edges and
 * four quarter-circle corners, yielding a continuous, seamless, frame-rate
 * independent motion. No angle jumps at corners; radius and aspect ratio are
 * handled correctly.
 */
public final class PerimeterOrbit {
    private PerimeterOrbit() {}

    public enum Direction { CLOCKWISE, COUNTERCLOCKWISE }

    /**
     * Total contour length of a rounded rectangle: straight edges + corner arcs.
     * Formula: 2*(w-2r) + 2*(h-2r) + 2*PI*r
     */
    public static float perimeter(float w, float h, float radius) {
        float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2f));
        return 2f * (w - 2f * r) + 2f * (h - 2f * r) + (float)(2 * Math.PI * r);
    }

    /**
     * Sample the normalised position {@code t in [0,1]} along the contour at a
     * given phase. {@code phase=0} starts at the top-left corner (x+r, y) and
     * travels clockwise along the top edge first.
     *
     * @param w     contour width
     * @param h     contour height
     * @param radius corner radius
     * @param phase phase in [0,1) along the contour
     * @param dir   travel direction
     * @return t in [0,1] for use in gradient interpolation
     */
    public static float position(float w, float h, float radius, float phase, Direction dir) {
        float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2f));
        float p = perimeter(w, h, r);
        if (p <= 0) return 0;
        float s = phase * p;
        if (dir == Direction.COUNTERCLOCKWISE) s = p - s;
        s = s % p;
        if (s < 0) s += p;
        return s / p;
    }
}
