package me.youngjibbit.event.world;

import me.youngjibbit.event.Event;
import net.minecraft.world.World;

/** Main-thread lifecycle notifications from Minecraft.loadWorld. Cancellation never blocks unloading/loading. */
public class WorldEvent extends Event
{
    public final World world;

    public WorldEvent(World world) {
        this.world = world;
    }
/**
     * POST after the client world, player and movement input have been installed.
     **/
    public static class Load extends WorldEvent {
        public Load(World world) {
            super(world);
            setEventPhase(me.zero.alpine.event.EventPhase.POST);
        }
    }

    /**
     * PRE while the old client world is still available. Lifecycle state must be discarded, not replayed.
     **/
    public static class Unload extends WorldEvent {
        public Unload(World world) {
            super(world);
        }
    }

}


