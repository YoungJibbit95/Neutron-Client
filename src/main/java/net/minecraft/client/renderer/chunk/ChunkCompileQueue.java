package net.minecraft.client.renderer.chunk;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/** Bounded work queue. A finite urgency credit preserves progress for older normal work. */
final class ChunkCompileQueue extends AbstractQueue<ChunkCompileTaskGenerator>
        implements BlockingQueue<ChunkCompileTaskGenerator> {
    static final long URGENCY_CREDIT_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private final int capacity;
    private final LongSupplier clock;
    private final PriorityQueue<Entry> entries = new PriorityQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private long sequence;

    ChunkCompileQueue(int capacity) { this(capacity, System::nanoTime); }
    ChunkCompileQueue(int capacity, LongSupplier clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.clock = clock;
    }

    @Override public boolean offer(ChunkCompileTaskGenerator task) {
        if (task == null) throw new NullPointerException("task");
        lock.lock();
        try {
            if (entries.size() == capacity) return false;
            insert(task);
            return true;
        } finally { lock.unlock(); }
    }

    @Override public void put(ChunkCompileTaskGenerator task) throws InterruptedException {
        if (task == null) throw new NullPointerException("task");
        lock.lockInterruptibly();
        try {
            while (entries.size() == capacity) notFull.await();
            insert(task);
        } finally { lock.unlock(); }
    }

    @Override public boolean offer(ChunkCompileTaskGenerator task, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (task == null) throw new NullPointerException("task");
        long remaining = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (entries.size() == capacity) {
                if (remaining <= 0L) return false;
                remaining = notFull.awaitNanos(remaining);
            }
            insert(task);
            return true;
        } finally { lock.unlock(); }
    }

    @Override public ChunkCompileTaskGenerator take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (entries.isEmpty()) notEmpty.await();
            return removeFirst();
        } finally { lock.unlock(); }
    }

    @Override public ChunkCompileTaskGenerator poll(long timeout, TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (entries.isEmpty()) {
                if (remaining <= 0L) return null;
                remaining = notEmpty.awaitNanos(remaining);
            }
            return removeFirst();
        } finally { lock.unlock(); }
    }

    @Override public ChunkCompileTaskGenerator poll() {
        lock.lock();
        try { return entries.isEmpty() ? null : removeFirst(); }
        finally { lock.unlock(); }
    }

    @Override public ChunkCompileTaskGenerator peek() {
        lock.lock();
        try { return entries.isEmpty() ? null : entries.peek().task; }
        finally { lock.unlock(); }
    }

    @Override public boolean remove(Object task) {
        lock.lock();
        try {
            Iterator<Entry> iterator = entries.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().task == task) {
                    iterator.remove();
                    notFull.signal();
                    return true;
                }
            }
            return false;
        } finally { lock.unlock(); }
    }

    @Override public int size() {
        lock.lock();
        try { return entries.size(); }
        finally { lock.unlock(); }
    }

    @Override public int remainingCapacity() { return capacity - size(); }
    @Override public int drainTo(Collection<? super ChunkCompileTaskGenerator> target) {
        return drainTo(target, Integer.MAX_VALUE);
    }
    @Override public int drainTo(Collection<? super ChunkCompileTaskGenerator> target, int maxElements) {
        if (target == null) throw new NullPointerException("target");
        if (target == this) throw new IllegalArgumentException("cannot drain to this queue");
        lock.lock();
        try {
            int count = 0;
            while (count < maxElements && !entries.isEmpty()) {
                target.add(entries.peek().task);
                removeFirst();
                count++;
            }
            return count;
        } finally { lock.unlock(); }
    }

    @Override public Iterator<ChunkCompileTaskGenerator> iterator() {
        final List<ChunkCompileTaskGenerator> snapshot = new ArrayList<>();
        lock.lock();
        try { for (Entry entry : entries) snapshot.add(entry.task); }
        finally { lock.unlock(); }
        final Iterator<ChunkCompileTaskGenerator> iterator = snapshot.iterator();
        return new Iterator<ChunkCompileTaskGenerator>() {
            private ChunkCompileTaskGenerator current;
            public boolean hasNext() { return iterator.hasNext(); }
            public ChunkCompileTaskGenerator next() { return current = iterator.next(); }
            public void remove() {
                if (current == null) throw new IllegalStateException();
                ChunkCompileQueue.this.remove(current);
                current = null;
            }
        };
    }

    private void insert(ChunkCompileTaskGenerator task) {
        entries.add(new Entry(task, clock.getAsLong(), sequence++));
        notEmpty.signal();
    }

    private ChunkCompileTaskGenerator removeFirst() {
        ChunkCompileTaskGenerator task = entries.remove().task;
        notFull.signal();
        return task;
    }

    private static final class Entry implements Comparable<Entry> {
        final ChunkCompileTaskGenerator task;
        final long priorityNanos;
        final long sequence;
        Entry(ChunkCompileTaskGenerator task, long queuedNanos, long sequence) {
            this.task = task;
            this.priorityNanos = queuedNanos - (task.isUrgent() ? URGENCY_CREDIT_NANOS : 0L);
            this.sequence = sequence;
        }
        @Override public int compareTo(Entry other) {
            // nanoTime values are meaningful as differences, including near wraparound.
            long elapsed = this.priorityNanos - other.priorityNanos;
            return elapsed == 0L ? Long.compare(this.sequence, other.sequence) : elapsed < 0L ? -1 : 1;
        }
    }
}
