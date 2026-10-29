package me.youngjibbit.core.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Small, bounded, in-memory client diagnostic history.
 * This is intentionally not an anticheat flag detector or a packet interceptor. */
public final class CombatDiagnosticTrace {
    private final int capacity;
    private final ArrayDeque<String> history = new ArrayDeque<>();

    public CombatDiagnosticTrace(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void append(String event) {
        if (event == null || event.isEmpty()) return;
        if (history.size() == capacity) history.removeFirst();
        history.addLast(event);
    }

    /** Newest first. Returning a copy prevents a renderer from mutating the history. */
    public synchronized List<String> recent(int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<String> items = new ArrayList<>(Math.min(limit, history.size()));
        Iterator<String> newest = history.descendingIterator();
        while (newest.hasNext() && items.size() < limit) items.add(newest.next());
        return items;
    }

    public synchronized int size() {
        return history.size();
    }

    public synchronized void clear() {
        history.clear();
    }
}
