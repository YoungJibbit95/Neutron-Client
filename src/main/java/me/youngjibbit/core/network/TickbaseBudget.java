package me.youngjibbit.core.network;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Credits count skipped local-player onUpdate bodies, not elapsed time or already executed ticks. */
public final class TickbaseBudget {
    private final LongSupplier clock;
    private Object player,world,connection;
    private int credit,max=5,min=2;
    private double releaseRate=.675, remainder;
    private boolean healthy,releasing,lastWithheld;
    private long oldest, frameAt;
    private int extraSteps;
    public TickbaseBudget() {this(System::nanoTime);}
    public TickbaseBudget(LongSupplier clock) {this.clock=clock;}
    public void configure(int max,int min,double speed) {
        this.max=Math.max(1,Math.min(20,max));this.min=Math.max(1,Math.min(this.max,min));
        // Preserved setting [1,2]: earned extra steps per two ordinary opportunities. At most one extra per opportunity.
        releaseRate=Math.max(1,Math.min(2,Double.isFinite(speed)?speed:1))/2;
        credit=Math.min(credit,this.max);
    }
    public void bind(Object player,Object world) {
        bind(player, world, null);
    }
    public void bind(Object player,Object world,Object connection) {
        if(this.player!=player || this.world!=world || this.connection!=connection) {
            suspend();this.player=player;this.world=world;this.connection=connection;
        }
    }
    public void frame(boolean eligible,int elapsedTicks,long frameMs,float timerSpeed) {
        frameAt=clock.getAsLong();
        healthy=eligible && elapsedTicks>=0 && elapsedTicks<=1 && frameMs>=0 && frameMs<=150
                && Float.isFinite(timerSpeed) && Math.abs(timerSpeed-1)<.001f;
        if(!healthy)reset();
        expire();
    }
    public boolean release() {
        checkFrame();
        expire();
        if(!healthy || credit<min || releasing)return false;
        releasing=true;remainder=0;lastWithheld=false;return true;
    }
    /** The caller has verified that a loaded player update is due. Input/network/world maintenance already ran. */
    public void step(boolean canCharge,BooleanSupplier stillValid,BooleanSupplier simulation) {
        extraSteps=0;
        checkFrame();
        expire();
        if(!stillValid.getAsBoolean()) {
            suspend();simulation.getAsBoolean();return;
        }
        if(healthy && !releasing && canCharge && credit<max && !lastWithheld) {
            if(credit==0)oldest=clock.getAsLong();
            credit++;lastWithheld=true;return;
        }
        lastWithheld=false;
        if(!simulation.getAsBoolean()) {suspend();return;}
        checkFrame();
        if(!stillValid.getAsBoolean()) {suspend();return;}
        if(!healthy || !releasing || credit==0)return;
        remainder+=releaseRate;
        if(remainder+1e-9<1)return;
        remainder-=1;
        if(simulation.getAsBoolean()) {credit--;extraSteps=1;} else {suspend();return;}
        checkFrame();
        if(!stillValid.getAsBoolean())suspend();else if(credit==0)reset();
    }
    private void expire() {if(credit>0 && clock.getAsLong()-oldest>5000000000L)reset();}
    private void checkFrame() {
        long age=clock.getAsLong()-frameAt;
        if(healthy && (age<0 || age>150000000L))suspend();
    }
    public void reset() {credit=0;releasing=false;lastWithheld=false;remainder=0;}
    /** Lifecycle/enable boundaries require a fresh frame sample before withholding can resume. */
    public void suspend() {reset();healthy=false;}
    public int getCredit() {return credit;}
    public boolean isReleasing() {return releasing;}
    public int getExtraSteps() {return extraSteps;}
    public boolean hasFreshFrame() {
        long age=clock.getAsLong()-frameAt;
        return healthy && age>=0 && age<=150000000L;
    }
}
