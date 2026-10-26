package me.youngjibbit.event.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

@Getter
@Setter
@AllArgsConstructor
/** Main thread, once per timer sample. Time replacement affects the delta; cancellation uses the real sample. No POST. */
public class TimerManipulationEvent extends Event
{
    private long time;
}

