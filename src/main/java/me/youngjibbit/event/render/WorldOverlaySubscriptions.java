package me.youngjibbit.event.render;

import me.zero.alpine.event.dispatch.EventDispatcher;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.ListenerList;
import me.zero.alpine.listener.ListenerListFactory;

/** Tracks the actual Event3D list, including listeners registered outside ModuleManager. */
public final class WorldOverlaySubscriptions implements ListenerListFactory {
    private final ListenerListFactory factory = ListenerListFactory.defaultFactory();
    private volatile int listenerCount;

    public boolean hasListeners() {
        return listenerCount != 0;
    }

    @Override
    public <T> ListenerList<T> create(Class<T> type) {
        ListenerList<T> listeners = factory.create(type);
        if (type != Event3D.class) return listeners;

        return new ListenerList<T>() {
            @Override
            public synchronized boolean add(Listener<T> listener) {
                if (!listeners.add(listener)) return false;
                listenerCount++;
                return true;
            }

            @Override
            public synchronized boolean remove(Listener<T> listener) {
                if (!listeners.remove(listener)) return false;
                listenerCount--;
                return true;
            }

            @Override
            public void post(T event, EventDispatcher dispatcher) {
                listeners.post(event, dispatcher);
            }
        };
    }
}
