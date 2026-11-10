package me.youngjibbit.utils.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class RenderStateBuffersTest {
    @Test public void nestedSnapshotsDoNotOverwriteTheOuterSnapshot() {
        RenderStateBuffers outer = RenderStateBuffers.acquire();
        RenderStateBuffers inner = RenderStateBuffers.acquire();
        try {
            assertNotSame(outer, inner);
            outer.model.put(0, 13f);
            outer.bindings[0] = 42;
            inner.model.put(0, 27f);
            inner.bindings[0] = 81;
            assertEquals(13f, outer.model.get(0), 0f);
            assertEquals(42, outer.bindings[0]);
        } finally {
            RenderStateBuffers.release(inner);
            RenderStateBuffers.release(outer);
        }
    }

    @Test public void completedSnapshotsReuseDirectStorageAndResetPositions() {
        RenderStateBuffers first = RenderStateBuffers.acquire();
        first.projection.position(3);
        first.color.limit(4);
        first.mask.position(2);
        RenderStateBuffers.release(first);
        RenderStateBuffers next = RenderStateBuffers.acquire();
        try {
            assertSame(first, next);
            assertTrue(next.projection.isDirect());
            assertTrue(next.scissor.isDirect());
            assertTrue(next.mask.isDirect());
            assertEquals(0, next.projection.position());
            assertEquals(16, next.color.limit());
            assertEquals(0, next.mask.position());
        } finally {
            RenderStateBuffers.release(next);
        }
    }

    @Test public void idleStorageIsBoundedAfterDeepNesting() {
        List<RenderStateBuffers> live = new ArrayList<>();
        IdentityHashMap<RenderStateBuffers, Boolean> original = new IdentityHashMap<>();
        for (int i = 0; i < 40; i++) {
            RenderStateBuffers buffers = RenderStateBuffers.acquire();
            live.add(buffers);
            assertNull(original.put(buffers, Boolean.TRUE));
        }
        for (RenderStateBuffers buffers : live) RenderStateBuffers.release(buffers);
        live.clear();
        int reused = 0;
        for (int i = 0; i < 40; i++) {
            RenderStateBuffers buffers = RenderStateBuffers.acquire();
            live.add(buffers);
            if (original.containsKey(buffers)) reused++;
        }
        for (RenderStateBuffers buffers : live) RenderStateBuffers.release(buffers);
        assertEquals(16, reused);
    }

    @Test public void independentThreadsCannotBorrowEachOthersStorage() throws Exception {
        RenderStateBuffers caller = RenderStateBuffers.acquire();
        RenderStateBuffers.release(caller);
        AtomicReference<RenderStateBuffers> other = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            RenderStateBuffers buffers = RenderStateBuffers.acquire();
            other.set(buffers);
            RenderStateBuffers.release(buffers);
        });
        worker.start();
        worker.join();
        assertNotSame(caller, other.get());
    }
}
