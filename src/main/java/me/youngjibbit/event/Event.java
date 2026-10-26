package me.youngjibbit.event;

import lombok.Getter;
import lombok.Setter;
import me.zero.alpine.event.EventPhase;

@Getter
public abstract class Event {

    private boolean cancelled;
    private EventPhase eventPhase = EventPhase.PRE;
    @Setter
    private EventFlow eventFlow;

    public boolean isCanceled() {
        return this.cancelled;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void setEventPhase(EventPhase eventPhase) {
        if (eventPhase != null) {
            this.eventPhase = eventPhase;
        }
    }

    public boolean isPre() {
        return eventPhase != null && eventPhase == EventPhase.PRE;
    }

    public boolean isON() {
        return eventPhase != null && eventPhase == EventPhase.ON;
    }

    public boolean isPost() {
        return eventPhase != null && eventPhase == EventPhase.POST;
    }

    public boolean isInbound() {
        return eventFlow != null && eventFlow == EventFlow.INBOUND;
    }

    public boolean isOutbound() {
        return eventFlow != null && eventFlow == EventFlow.OUTBOUND;
    }
}
