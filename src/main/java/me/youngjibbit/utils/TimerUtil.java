package me.youngjibbit.utils;

public class TimerUtil {

    public long lastMS = System.currentTimeMillis();
    private long lastTime = System.currentTimeMillis();


    public void reset() {
        long now = System.currentTimeMillis();
        lastMS = now;
        lastTime = now;
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.currentTimeMillis() - lastMS > time) {
            if (reset) reset();
            return true;
        }

        return false;
    }

    public boolean hasTimeElapsed(double time, boolean reset) {
        return hasTimeElapsed((long) time, reset);
    }

    public long getTimeElapsed() {
        return System.currentTimeMillis() - lastTime;
    }

    public void setTimeElapsed(long time) {
        this.lastTime = System.currentTimeMillis() - time;
    }

    public boolean hasTimeElapsed(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public boolean hasTimeElapsed(double time) {
        return hasTimeElapsed((long) time);
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public void setTime(long time) {
        lastMS = time;
    }

}
