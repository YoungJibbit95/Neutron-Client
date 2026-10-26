package me.youngjibbit.event.input;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.youngjibbit.event.Event;


@Getter
@AllArgsConstructor
public class EventKey extends Event
{
    private final int key;
}
