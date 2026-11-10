package me.youngjibbit.core.activity;

/** Immutable, short-lived display evidence. An incident never identifies a server check or a cause. */
public final class WidgetIncident {
    public enum Kind { FPS, EXPLOIT }
    public final Kind kind;
    public final long id,observedAtNanos,expiresAtNanos;
    public final String headline,detail,value;

    WidgetIncident(Kind kind,long id,String headline,String detail,String value,long observed,long expires){
        this.kind=kind;this.id=id;this.headline=headline;this.detail=detail;this.value=value;
        observedAtNanos=observed;expiresAtNanos=expires;
    }
    public boolean isFresh(long now){return now>=observedAtNanos&&now<expiresAtNanos;}
}
