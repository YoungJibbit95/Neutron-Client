package me.youngjibbit.event.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;
import net.minecraft.client.gui.ScaledResolution;

@Getter
@Setter
@AllArgsConstructor
public class Event2D extends Event
{
    private float partialTicks;
    private ScaledResolution sr;
}
