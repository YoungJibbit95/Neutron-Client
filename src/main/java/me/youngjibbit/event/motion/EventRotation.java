package me.youngjibbit.event.motion;

import me.youngjibbit.event.Event;
import java.util.ArrayList;
import java.util.List;

/** One rotation preparation before local input/physics; never an accepted packet. */
public final class EventRotation extends Event {
    private final EventMotion motion;
    private final List<Runnable> beforePhysics = new ArrayList<>();

    public EventRotation(EventMotion motion) { this.motion = motion; }
    public EventMotion motion() { return motion; }

    /** Automatic jumps need both the resolved yaw and the final input/sprint decision. */
    public void beforePhysics(Runnable action) { beforePhysics.add(action); }
    public void runBeforePhysics() {
        try { for (Runnable action : beforePhysics) action.run(); }
        finally { beforePhysics.clear(); }
    }
}
