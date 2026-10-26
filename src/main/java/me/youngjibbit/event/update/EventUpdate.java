package me.youngjibbit.event.update;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.youngjibbit.event.Event;

@Getter
@AllArgsConstructor

/** Client thread, once before a loaded local player's onUpdate. PRE cancellation skips that update; no POST. */
public final class EventUpdate extends Event
{
}
