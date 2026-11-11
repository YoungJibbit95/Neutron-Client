package net.minecraft.client.renderer.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkCompileQueueTest {
    @Test public void urgencyIsFiniteAndNormalWorkAgesAheadOfNewUrgentWork() {
        AtomicLong clock = new AtomicLong();
        ChunkCompileQueue queue = new ChunkCompileQueue(3, clock::get);
        ChunkCompileTaskGenerator normal = task(false), earlyUrgent = task(true), lateUrgent = task(true);
        assertTrue(queue.offer(normal));
        clock.set(TimeUnit.MILLISECONDS.toNanos(10));
        assertTrue(queue.offer(earlyUrgent));
        clock.set(TimeUnit.MILLISECONDS.toNanos(60));
        assertTrue(queue.offer(lateUrgent));
        assertSame(earlyUrgent, queue.poll());
        assertSame("aged normal work precedes later urgent work", normal, queue.poll());
        assertSame(lateUrgent, queue.poll());
    }

    @Test public void capacityCancellationAndFifoTiesArePreserved() {
        ChunkCompileQueue queue = new ChunkCompileQueue(2, () -> 123L);
        ChunkCompileTaskGenerator first = task(false), second = task(false), third = task(true);
        assertTrue(queue.offer(first));
        assertTrue(queue.offer(second));
        assertFalse(queue.offer(third));
        assertEquals(0, queue.remainingCapacity());
        assertTrue(queue.remove(second));
        assertTrue(queue.offer(third));
        assertSame(third, queue.poll());
        assertSame(first, queue.poll());
        assertEquals(2, queue.remainingCapacity());
        queue.offer(first); queue.offer(second);
        assertSame(first, queue.poll());
    }

    @Test public void entryPriorityIsImmutableAfterEnqueue() {
        AtomicLong clock = new AtomicLong(TimeUnit.MILLISECONDS.toNanos(100));
        ChunkCompileQueue queue = new ChunkCompileQueue(2, clock::get);
        ChunkCompileTaskGenerator first = task(false), second = task(false);
        queue.offer(first);
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(1));
        queue.offer(second);
        second.setUrgent(true);
        assertSame(first, queue.poll());
        assertSame(second, queue.poll());
    }

    @Test public void waitingConsumerWakesAndDrainRestoresCapacity() throws Exception {
        ChunkCompileQueue queue = new ChunkCompileQueue(2);
        AtomicReference<ChunkCompileTaskGenerator> taken = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            try { taken.set(queue.take()); }
            catch (Throwable error) { failure.set(error); }
        });
        consumer.start();
        ChunkCompileTaskGenerator first = task(false);
        queue.put(first);
        consumer.join(10000);
        assertFalse(consumer.isAlive());
        assertNull(failure.get());
        assertSame(first, taken.get());
        assertNull(queue.poll(1, TimeUnit.MILLISECONDS));
        queue.offer(task(false)); queue.offer(task(true));
        assertFalse(queue.offer(task(false), 1, TimeUnit.MILLISECONDS));
        List<ChunkCompileTaskGenerator> drained = new ArrayList<>();
        assertEquals(2, queue.drainTo(drained));
        assertEquals(2, drained.size());
        assertEquals(2, queue.remainingCapacity());
    }

    private static ChunkCompileTaskGenerator task(boolean urgent) {
        ChunkCompileTaskGenerator task = new ChunkCompileTaskGenerator(null,
                ChunkCompileTaskGenerator.Type.REBUILD_CHUNK);
        task.setUrgent(urgent);
        return task;
    }
}
