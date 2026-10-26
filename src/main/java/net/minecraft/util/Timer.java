package net.minecraft.util;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.player.TimerManipulationEvent;
import net.minecraft.client.Minecraft;

public class Timer
{
    private final java.util.function.LongSupplier clock;
    private long lastRealSample;
    /** Raw frame duration before TimerManipulationEvent/timerSpeed; used to reject catch-up charge. */
    public long lastFrameDurationMs;
    /** The number of timer ticks per second of real time */
    public float ticksPerSecond;

    /**
     * The time reported by the high-resolution clock at the last call of updateTimer(), in seconds
     */
    private double lastHRTime;

    /**
     * How many full ticks have turned over since the last call to updateTimer(), capped at 10.
     */
    public int elapsedTicks;

    /**
     * How much time has elapsed since the last tick, in ticks, for use by display rendering routines (range: 0.0 -
     * 1.0).  This field is frozen if the display is paused to eliminate jitter.
     */
    public float renderPartialTicks;

    /**
     * A multiplier to make the timer (and therefore the game) go faster or slower.  0.5 makes the game run at half-
     * speed.
     */
    public float timerSpeed = 1.0F;

    /**
     * How much time has elapsed since the last tick, in ticks (range: 0.0 - 1.0).
     */
    public float elapsedPartialTicks;

    /**
     * The time reported by the system clock at the last sync, in milliseconds
     */
    private long lastSyncSysClock;

    /**
     * The time reported by the high-resolution clock at the last sync, in milliseconds
     */
    private long lastSyncHRClock;
    private long field_74285_i;

    /**
     * A ratio used to sync the high-resolution clock to the system clock, updated once per second
     */
    private double timeSyncAdjustment = 1.0D;

    /*public Timer(float p_i1018_1_)
    {
        this.ticksPerSecond = p_i1018_1_;
        this.lastSyncSysClock = Minecraft.getSystemTime();
        this.lastSyncHRClock = System.nanoTime() / 1000000L;
    }*/

    /**
     * Updates all fields of the Timer using the current time
     */

    public float partialTicks;
    public float field_194148_c;

    /**
     * The time reported by the system clock at the last sync, in milliseconds
     */
    private final float field_194149_e;

    public Timer(float tps)
    {
        this(tps, Minecraft::getSystemTime);
    }

    public Timer(float tps, java.util.function.LongSupplier clock)
    {
        if (!(tps > 0) || !Float.isFinite(tps)) throw new IllegalArgumentException("Tick rate must be finite and positive");
        this.clock = clock;
        this.ticksPerSecond = tps;
        this.field_194149_e = 1000.0F / tps;
        this.lastSyncSysClock = this.lastRealSample = clock.getAsLong();
        this.timerSpeed = 1.0F;
    }

    /**
     * Updates all fields of the Timer using the current time
     */
    public void updateTimer()
    {
        long realSample = clock.getAsLong();
        this.lastFrameDurationMs = Math.max(0, realSample - this.lastRealSample);
        this.lastRealSample = realSample;
        TimerManipulationEvent timerManipulationEvent = new TimerManipulationEvent(realSample);
        Pulsar.INSTANCE.BUS.post(timerManipulationEvent);
        long i = timerManipulationEvent.isCancelled() ? realSample : timerManipulationEvent.getTime();
        long delta = Math.max(0, Math.min(1000, i - this.lastSyncSysClock));
        float speed = Float.isFinite(this.timerSpeed) && this.timerSpeed >= 0 ? this.timerSpeed : 1;
        double elapsed = (double) delta / this.field_194149_e * speed;
        this.field_194148_c = (float) Math.min(10, elapsed);
        this.lastSyncSysClock = i;
        double total = this.partialTicks + elapsed;
        this.elapsedTicks = (int) Math.min(10, Math.floor(total));
        this.partialTicks = (float) (total - Math.floor(total));
        this.elapsedPartialTicks = this.partialTicks;
        this.renderPartialTicks = this.partialTicks;
    }

    /*public void updateTimer()
    {
        long i = Minecraft.getSystemTime();
        long j = i - this.lastSyncSysClock;
        long k = System.nanoTime() / 1000000L;
        double d0 = (double)k / 1000.0D;

        if (j <= 1000L && j >= 0L)
        {
            this.field_74285_i += j;

            if (this.field_74285_i > 1000L)
            {
                long l = k - this.lastSyncHRClock;
                double d1 = (double)this.field_74285_i / (double)l;
                this.timeSyncAdjustment += (d1 - this.timeSyncAdjustment) * 0.20000000298023224D;
                this.lastSyncHRClock = k;
                this.field_74285_i = 0L;
            }

            if (this.field_74285_i < 0L)
            {
                this.lastSyncHRClock = k;
            }
        }
        else
        {
            this.lastHRTime = d0;
        }

        this.lastSyncSysClock = i;
        double d2 = (d0 - this.lastHRTime) * this.timeSyncAdjustment;
        this.lastHRTime = d0;
        d2 = MathHelper.clamp_double(d2, 0.0D, 1.0D);
        this.elapsedPartialTicks = (float)((double)this.elapsedPartialTicks + d2 * (double)this.timerSpeed * (double)this.ticksPerSecond);
        this.elapsedTicks = (int)this.elapsedPartialTicks;
        this.elapsedPartialTicks -= (float)this.elapsedTicks;

        if (this.elapsedTicks > 10)
        {
            this.elapsedTicks = 10;
        }

        this.renderPartialTicks = this.elapsedPartialTicks;
    }*/
}
