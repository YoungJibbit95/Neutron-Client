package me.youngjibbit.module.impl.render;

import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Client-thread session state. No rendering, wall clock, packet handlers or HUD-toggle side effects. */
final class SessionStatistics {
    // Deliberately narrow English vanilla-style evidence; player chat prefixes and challenges do not match.
    private static final Pattern DEATH = Pattern.compile("^([A-Za-z0-9_]{1,16}) was (?:slain|shot|killed) by ([A-Za-z0-9_]{1,16})(?: using \\[.{1,120}\\])?[.!]?$");
    private final LongSupplier clock;
    private Object connection;
    private long began, endedDuration, generation;
    private int kills, deaths;

    SessionStatistics(LongSupplier clock) { this.clock = clock; }

    void observe(Object connection, boolean playing) {
        if (connection == null) {
            if (this.connection != null) {
                endedDuration = Math.max(0, clock.getAsLong() - began);
                this.connection = null;
            }
        } else if (playing && this.connection != connection) {
            this.connection = connection;
            began = clock.getAsLong();
            endedDuration = 0;
            kills = deaths = 0;
            generation++;
        }
    }

    long generation() { return connection == null ? 0 : generation; }

    void chat(String text, String username, long generation) {
        if (this.connection == null || generation != this.generation || text == null || username == null) return;
        Matcher match = DEATH.matcher(text.replaceAll("(?i)§[0-9a-fk-or]", "").trim());
        if (!match.matches()) return;
        String victim = match.group(1), killer = match.group(2);
        if (victim.equalsIgnoreCase(username)) deaths = increment(deaths);
        else if (killer.equalsIgnoreCase(username)) kills = increment(kills);
    }

    Snapshot snapshot() {
        long duration = connection == null ? endedDuration : Math.max(0, clock.getAsLong() - began);
        return new Snapshot(duration, kills, deaths, connection != null);
    }

    private static int increment(int value) { return value == Integer.MAX_VALUE ? value : value + 1; }

    static final class Snapshot {
        final long elapsedNanos;
        final int kills, deaths;
        final boolean active;
        Snapshot(long nanos, int kills, int deaths, boolean active) {
            this.elapsedNanos = nanos; this.kills = kills; this.deaths = deaths; this.active = active;
        }
        long milliseconds() { return elapsedNanos / 1_000_000L; }
        String time() {
            long seconds = elapsedNanos / 1_000_000_000L;
            long hours = seconds / 3600, minutes = seconds / 60 % 60;
            return hours > 0 ? hours + "h " + minutes + "m " + seconds % 60 + "s" : minutes + "m " + seconds % 60 + "s";
        }
        String clock() {
            long seconds = elapsedNanos / 1_000_000_000L;
            return seconds >= 3600 ? String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
                    : String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
        }
    }
}
