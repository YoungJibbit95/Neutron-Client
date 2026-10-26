package me.youngjibbit.core.world;

public final class PlacementContext {

    private final boolean downwards;
    private final boolean keepY;
    private final double keepYCoord;

    public PlacementContext(boolean downwards, boolean keepY, double keepYCoord) {
        this.downwards = downwards;
        this.keepY = keepY;
        this.keepYCoord = keepYCoord;
    }

    public boolean isDownwards() {
        return downwards;
    }

    public boolean isKeepY() {
        return keepY;
    }

    public double getKeepYCoord() {
        return keepYCoord;
    }
}
