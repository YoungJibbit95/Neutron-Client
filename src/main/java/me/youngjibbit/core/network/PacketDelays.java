package me.youngjibbit.core.network;

import me.youngjibbit.event.EventFlow;
import net.minecraft.network.Packet;
import java.util.*;
import java.util.function.*;

/** Bounded FIFO per direction. Owners share holds on each entry, never each other's switches. */
public final class PacketDelays {
    public static final int MAX_PACKETS = 1024;
    public static final long MAX_AGE_MS = 1000, MAX_DELAY_MS = 500, LEASE_MS = 250;
    private final LongSupplier clock;
    private final Map<Object, Request> requests = new LinkedHashMap<>();
    private final EnumMap<EventFlow, ArrayDeque<Entry>> queues = new EnumMap<>(EventFlow.class);
    private Object session = new Object();
    private boolean closed;

    public PacketDelays() { this(System::nanoTime); }
    public PacketDelays(LongSupplier clock) {
        this.clock = clock;
        queues.put(EventFlow.INBOUND, new ArrayDeque<>());
        queues.put(EventFlow.OUTBOUND, new ArrayDeque<>());
    }
    public synchronized void request(Object owner, int delayMs, BiPredicate<Packet<?>, EventFlow> matches) {
        Objects.requireNonNull(owner);Objects.requireNonNull(matches);
        if (closed) return;
        if (!requests.containsKey(owner) && requests.size() >= 32) throw new IllegalStateException("Too many packet delay owners");
        requests.put(owner, new Request(Math.max(0,Math.min(MAX_DELAY_MS,delayMs))*1000000L,
                clock.getAsLong()+LEASE_MS*1000000L, matches));
    }
    /** Evaluated after every PRE listener, against the final replacement packet. */
    public boolean defer(Packet<?> packet, EventFlow flow, Runnable apply) {
        pump();
        if (size() >= MAX_PACKETS) {
            // The bound is shared by both directions. Emptying only the incoming direction could exceed it.
            for (EventFlow direction : queues.keySet()) barrier(direction);
        }
        synchronized (this) {
            if (closed) return false;
            long now = clock.getAsLong();
            Map<Object,Long> holds = new LinkedHashMap<>();
            requests.forEach((owner,r) -> { if(r.delay>0 && r.matches.test(packet,flow)) holds.put(owner,now+r.delay); });
            ArrayDeque<Entry> queue = queues.get(flow);
            if (holds.isEmpty() && queue.isEmpty()) return false;
            if (size() >= MAX_PACKETS) throw new IllegalStateException("Packet delay queue refilled during capacity release");
            queue.add(new Entry(session,now,holds,apply));
        }
        return true;
    }
    public void release(Object owner) {
        synchronized(this) {
            requests.remove(owner);
            queues.values().forEach(q -> q.forEach(e -> e.holds.remove(owner)));
        }
        pump();
    }
    public void pump() {
        synchronized(this) {
            long now=clock.getAsLong();
            Set<Object> expired=new HashSet<>();
            requests.forEach((owner,r)-> { if(now-r.expires>=0)expired.add(owner); });
            expired.forEach(requests::remove);
            queues.values().forEach(q->q.forEach(e->expired.forEach(e.holds::remove)));
        }
        for(EventFlow flow:queues.keySet()) drain(flow,false);
    }
    /** Dependency barrier: earlier movement must apply before a teleport, action or correction. */
    public void barrier(EventFlow flow) {
        drain(flow,true);
    }
    private void drain(EventFlow flow,boolean force) {
        for(int i=0;i<MAX_PACKETS;i++) {
            Runnable next;
            synchronized(this) { next=nextLocked(flow,force,clock.getAsLong()); }
            if(next==null)return;
            // Keep later entries visible while callbacks run, so nested sends cannot overtake them.
            next.run();
        }
        synchronized(this) {
            if(force && !queues.get(flow).isEmpty())throw new IllegalStateException("Packet barrier exceeded release bound");
        }
    }
    private Runnable nextLocked(EventFlow flow,boolean force,long now) {
        ArrayDeque<Entry> queue=queues.get(flow);
        while(!queue.isEmpty()) {
            Entry entry=queue.peek();
            if(entry.session!=session) { queue.poll();continue; }
            boolean due=entry.holds.values().stream().allMatch(deadline->now-deadline>=0);
            if(!force && !due && now-entry.created<MAX_AGE_MS*1000000L)return null;
            return queue.poll().apply;
        }
        return null;
    }
    /** World/connection loss discards, never releases packets into the next scene. */
    public synchronized void discard() {
        session=new Object();requests.clear();queues.values().forEach(ArrayDeque::clear);
    }
    public synchronized void close() { closed=true;discard(); }
    public synchronized int size() { return queues.values().stream().mapToInt(ArrayDeque::size).sum(); }
    public synchronized boolean hasOwner(Object owner) { return requests.containsKey(owner); }
    /** Metadata only: no packet payloads, names or chat content. Does not pump or release queues. */
    public synchronized Snapshot snapshot(Object owner) {
        long now = clock.getAsLong(), oldest = 0;
        int held = 0;
        for (ArrayDeque<Entry> queue : queues.values()) for (Entry entry : queue) {
            oldest = Math.max(oldest, Math.max(0, now - entry.created));
            if (entry.holds.containsKey(owner)) held++;
        }
        return new Snapshot(size(), held, requests.size(), oldest / 1000000L);
    }
    public static final class Snapshot {
        public final int size, heldByOwner, activeOwners;
        public final long oldestAgeMs;
        Snapshot(int size, int heldByOwner, int activeOwners, long oldestAgeMs) {
            this.size = size; this.heldByOwner = heldByOwner; this.activeOwners = activeOwners; this.oldestAgeMs = oldestAgeMs;
        }
    }
    long now() { return clock.getAsLong(); }
    private static final class Request {
        final long delay,expires;final BiPredicate<Packet<?>,EventFlow> matches;
        Request(long delay,long expires,BiPredicate<Packet<?>,EventFlow> matches) {this.delay=delay;this.expires=expires;this.matches=matches;}
    }
    private static final class Entry {
        final Object session;final long created;final Map<Object,Long> holds;final Runnable apply;
        Entry(Object session,long created,Map<Object,Long> holds,Runnable apply) {this.session=session;this.created=created;this.holds=holds;this.apply=apply;}
    }
}
