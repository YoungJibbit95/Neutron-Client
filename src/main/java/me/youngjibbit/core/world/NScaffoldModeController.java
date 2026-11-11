package me.youngjibbit.core.world;

/**
 * Observed building phases, without movement or key writes. Telly's separate
 * straight/diagonal flight windows are adapted from the authorized reference;
 * the player supplies the actual jump and lateral movement.
 */
public final class NScaffoldModeController {
    public enum Mode { NORMAL, BREEZILY, TELLY }

    public static final class State {
        public final boolean valid, canBuild;
        public final int airborneTicks, groundTicks, requiredTicks;

        private State(boolean valid,boolean canBuild,int airborneTicks,int groundTicks,int requiredTicks) {
            this.valid=valid;this.canBuild=canBuild;
            this.airborneTicks=airborneTicks;this.groundTicks=groundTicks;this.requiredTicks=requiredTicks;
        }
    }

    private static final State INVALID=new State(false,false,0,0,0);
    private long lastTick=Long.MIN_VALUE;
    private Mode lastMode;
    private boolean lastGround;
    private float lastForward,lastStrafe;
    private int lastStraight,lastDiagonal;
    private State state=INVALID;

    public void reset() {
        lastMode=null;state=INVALID;
        lastGround=false;lastForward=lastStrafe=0;lastStraight=lastDiagonal=0;
    }

    /** Only a different scene may begin a new outer-tick sequence. */
    public void resetForScene() { reset();lastTick=Long.MIN_VALUE; }

    /** The input listener can observe the last phase without advancing it. */
    public State getState() { return state; }

    /**
     * At most one observation per real outer tick. Re-entrant observations with
     * different facts are denied; skipped ticks cannot manufacture a flight.
     */
    public State advance(long tick,boolean onGround,float forward,float strafe,Mode mode,
                         int straightTicks,int diagonalTicks) {
        if(tick<0 || mode==null || !Float.isFinite(forward) || !Float.isFinite(strafe)
                || Math.abs(forward)>1 || Math.abs(strafe)>1
                || straightTicks<1 || straightTicks>5 || diagonalTicks<1 || diagonalTicks>5
                || lastTick!=Long.MIN_VALUE && tick<lastTick)return INVALID;
        if(tick==lastTick) {
            return lastMode==mode && lastGround==onGround && Float.compare(lastForward,forward)==0
                    && Float.compare(lastStrafe,strafe)==0 && lastStraight==straightTicks
                    && lastDiagonal==diagonalTicks ? state:INVALID;
        }
        boolean consecutive=lastTick!=Long.MIN_VALUE && tick-lastTick==1 && mode==lastMode
                && straightTicks==lastStraight && diagonalTicks==lastDiagonal;
        int airborne=onGround?0:consecutive?Math.min(1000000,state.airborneTicks+1):1;
        int ground=!onGround?0:consecutive?Math.min(1000000,state.groundTicks+1):1;
        int required=Math.abs(forward)>1.0e-5f && Math.abs(strafe)>1.0e-5f?diagonalTicks:straightTicks;
        state=new State(true,mode!=Mode.TELLY || airborne>=required,airborne,ground,required);
        lastTick=tick;lastMode=mode;lastGround=onGround;lastForward=forward;lastStrafe=strafe;
        lastStraight=straightTicks;lastDiagonal=diagonalTicks;
        return state;
    }
}
