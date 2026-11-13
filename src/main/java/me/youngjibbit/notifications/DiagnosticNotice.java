package me.youngjibbit.notifications;

import me.youngjibbit.core.activity.ExploitActivitySnapshot;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Read-only status presentation. Sampling never advances a module or a packet queue. */
public final class DiagnosticNotice {
    private static final long COOLDOWN_NANOS = 2_000_000_000L;
    private final String channel, title;
    private final LongSupplier clock;
    private final Consumer<Notification> show, refresh;
    private String observed, lastText;
    private long publishedAt;
    private boolean published;

    public DiagnosticNotice(String channel, String title, LongSupplier clock) {
        this(channel, title, clock, NotificationManager::showState, NotificationManager::refreshStateIfPresent);
    }

    DiagnosticNotice(String channel, String title, LongSupplier clock,
                     Consumer<Notification> show, Consumer<Notification> refresh) {
        this.channel = Objects.requireNonNull(channel);
        this.title = Objects.requireNonNull(title);
        this.clock = Objects.requireNonNull(clock);
        this.show = Objects.requireNonNull(show);
        this.refresh = Objects.requireNonNull(refresh);
    }

    public void observe(boolean enabled, boolean diagnostics, String mode, String reason,
                        ExploitActivitySnapshot activity) {
        if (!enabled || !diagnostics) {
            finish(enabled ? "Diagnostics off" : "Off");
            return;
        }
        long now = clock.getAsLong();
        if (activity != null && !activity.isFresh(now)) activity = null;
        String label = activity == null ? idleLabel(reason) : activity.phase.name();
        String key = mode + ":" + label;
        boolean changed = !key.equals(observed);
        observed = key;
        String text = activity == null ? label : activityLabel(activity);
        if (mode != null && !mode.isEmpty()) text += " \u00b7 " + mode;
        if (!changed && text.equals(lastText)) return;
        lastText = text;
        Notification notification = notice(text);
        long elapsed = now - publishedAt;
        if (changed && (!published || elapsed < 0 || elapsed >= COOLDOWN_NANOS)) {
            published = true;
            publishedAt = now;
            show.accept(notification);
        } else {
            // Cooldown limits new interruptions, never the facts on an existing card.
            refresh.accept(notification);
        }
    }

    /** Disable/world changes must retire old holding text even when no more ticks arrive. */
    public void finish(String state) {
        if (observed != null) refresh.accept(notice(state));
        reset();
    }

    public void reset() {
        observed = null;
        lastText = null;
        publishedAt = 0;
        published = false;
    }

    private Notification notice(String text) {
        return Notification.latest(channel, NotificationType.INFO, title, text, 3);
    }

    private static String activityLabel(ExploitActivitySnapshot activity) {
        switch (activity.phase) {
            case HOLDING:
                return "Holding " + Math.max(0, activity.ageMillis) + " ms \u00b7 "
                        + activity.heldPackets + (activity.heldPackets == 1 ? " packet" : " packets");
            case CHARGED: return "Ready " + activity.credit + " ticks";
            case RELEASING:
                return activity.extraSteps > 0 ? "Step +" + activity.extraSteps : "Release " + activity.credit + " ticks";
            default: return "Idle";
        }
    }

    private static String idleLabel(String reason) {
        String value = reason == null ? "" : reason.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "no target": case "no eligible target": return "Waiting for target";
            case "waiting for aura target": return "Waiting for Aura target";
            case "distance filter": case "inside start distance": case "waiting for approach outside reach":
                return "Waiting for distance";
            case "hurt filter": return "Waiting for hurt state";
            case "activation filter": return "Waiting for input";
            case "local impulse recovery": case "local velocity": case "local knockback/hurt pause":
                return "Paused after hurt/knockback";
            case "stale position": case "receipt missing or stale": return "Waiting for position update";
            case "delaying target motion": case "delaying outgoing movement": return "Waiting for movement";
            case "settling": case "release cooldown": return "Cooldown";
            case "start distance exceeds target range": return "Check distance settings";
            case "zero delay configured": return "Delay off";
            case "other timing window": return "Waiting for timing window";
            case "healthy frame required": return "Waiting for frame";
            case "receipt queue budget": case "receipt window complete": case "pulse complete":
            case "frame window expired": case "attack released window": case "combat feedback":
                return "Window released";
            case "enabled": case "settings changed": case "world changed": case "context changed":
            case "waiting for context": case "invalid context": case "inactive context or input":
            case "inactive input": case "": return "Idle";
            default:
                String text = Character.toUpperCase(value.charAt(0)) + value.substring(1);
                return text.length() <= 48 ? text : text.substring(0, 47) + "\u2026";
        }
    }
}
