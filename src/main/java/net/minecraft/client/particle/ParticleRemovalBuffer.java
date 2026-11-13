package net.minecraft.client.particle;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Owner-thread scratch for bounded particle layers. No particle survives a
 * release in either collection. Reentrant updates use the legacy path.
 */
final class ParticleRemovalBuffer<T>
{
    private static final int MAX_RETAINED_ENTRIES = 4096;
    private static final int HASH_THRESHOLD = 16;
    private final ArrayList<T> removed = new ArrayList<T>(32);
    private final Membership<T> membership = new Membership<T>();
    private boolean inUse;

    boolean tryAcquire()
    {
        if (this.inUse) return false;
        this.inUse = true;
        return true;
    }

    List<T> removed()
    {
        if (!this.inUse) throw new IllegalStateException("Particle removal scratch is not acquired");
        return this.removed;
    }

    void removeFrom(List<T> particles)
    {
        if (!this.inUse) throw new IllegalStateException("Particle removal scratch is not acquired");
        if (this.removed.isEmpty()) return;

        if (this.removed.size() < HASH_THRESHOLD || particles.size() < 128
                || this.removed.size() > MAX_RETAINED_ENTRIES)
        {
            particles.removeAll(this.removed);
            return;
        }

        // Entity IDs and hash codes can change during onUpdate. Build membership
        // only after all particle ticks and budget mutations have ended.
        this.membership.prepare(this.removed);
        particles.removeAll(this.membership);
    }

    void release()
    {
        this.membership.clear();
        boolean oversized = this.removed.size() > MAX_RETAINED_ENTRIES;
        this.removed.clear();
        if (oversized) this.removed.trimToSize();
        this.inUse = false;
    }

    /** Hash membership without per-entry nodes; at most 8192 retained slots. */
    private static final class Membership<T> extends AbstractCollection<T>
    {
        private Object[] table = new Object[0];
        private int size;
        private boolean hasNull;

        void prepare(List<T> values)
        {
            this.clear();
            int capacity = 32;
            while (capacity < values.size() * 2) capacity <<= 1;
            if (this.table.length < capacity) this.table = new Object[capacity];

            for (T value : values)
            {
                if (value == null)
                {
                    if (!this.hasNull) ++this.size;
                    this.hasNull = true;
                    continue;
                }

                int index = spread(value.hashCode()) & (this.table.length - 1);
                while (this.table[index] != null && !value.equals(this.table[index]))
                    index = (index + 1) & (this.table.length - 1);
                if (this.table[index] == null)
                {
                    this.table[index] = value;
                    ++this.size;
                }
            }
        }

        @Override public boolean contains(Object value)
        {
            if (value == null) return this.hasNull;
            if (this.table.length == 0) return false;
            int index = spread(value.hashCode()) & (this.table.length - 1);
            Object entry;
            while ((entry = this.table[index]) != null)
            {
                if (value.equals(entry)) return true;
                index = (index + 1) & (this.table.length - 1);
            }
            return false;
        }

        @Override public int size() { return this.size; }

        @Override public void clear()
        {
            if (this.size != 0) Arrays.fill(this.table, null);
            this.size = 0;
            this.hasNull = false;
        }

        @Override public Iterator<T> iterator()
        {
            return new Iterator<T>()
            {
                private int index;
                private boolean nullPending = Membership.this.hasNull;

                @Override public boolean hasNext()
                {
                    if (this.nullPending) return true;
                    while (this.index < Membership.this.table.length
                            && Membership.this.table[this.index] == null) ++this.index;
                    return this.index < Membership.this.table.length;
                }

                @SuppressWarnings("unchecked")
                @Override public T next()
                {
                    if (this.nullPending) { this.nullPending = false; return null; }
                    if (!this.hasNext()) throw new NoSuchElementException();
                    return (T)Membership.this.table[this.index++];
                }

                @Override public void remove() { throw new UnsupportedOperationException(); }
            };
        }

        private static int spread(int hash) { return hash ^ (hash >>> 16); }
    }
}
