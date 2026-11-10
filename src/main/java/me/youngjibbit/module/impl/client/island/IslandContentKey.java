package me.youngjibbit.module.impl.client.island;

/** Only a different presentation restarts the content fade; live Scaffold measurements do not. */
public final class IslandContentKey {
    private IslandContentKey() { }
    public static String of(IslandStateMachine.Selection selection) {
        if (selection == null) return IslandStateMachine.State.IDLE.name();
        return selection.state.name()
                + (selection.target == null ? "" : "#" + selection.target.entityId + ":" + selection.target.name)
                + (selection.clock == null ? "" : "@" + selection.clock.time)
                + (selection.inventory == null ? "" : "#" + selection.inventory.producer + ":" + selection.inventory.action)
                + (selection.notice == null ? "" : "#" + selection.notice.eventId)
                + (selection.notification == null ? "" : "#" + selection.notification.id);
    }
}
