package net.minecraft.client.particle;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParticleRemovalBufferTest
{
    @Test public void matchesListRemovalWithDuplicatesCollisionsAndStableOrder() throws Exception
    {
        Random random = new Random(20261113L);
        ParticleRemovalBuffer<Key> buffer = new ParticleRemovalBuffer<Key>();
        for (int run = 0; run < 100; ++run)
        {
            List<Key> values = new ArrayList<Key>();
            List<Key> removed = new ArrayList<Key>();
            for (int i = 0; i < 256; ++i) values.add(new Key(random.nextInt(160), run % 3 == 0));
            for (int i = 0; i < run; ++i) removed.add(new Key(random.nextInt(160), run % 3 == 0));
            List<Key> expected = new ArrayList<Key>(values);
            expected.removeAll(removed);
            assertTrue(buffer.tryAcquire());
            try
            {
                buffer.removed().addAll(removed);
                buffer.removeFrom(values);
                assertIdentityOrder(expected, values);
            }
            finally { buffer.release(); }
            assertReleased(buffer);
        }
    }

    @Test public void membershipUsesIdsAfterTickMutationsAndHandlesNull() throws Exception
    {
        ParticleRemovalBuffer<Key> buffer = new ParticleRemovalBuffer<Key>();
        List<Key> values = new ArrayList<Key>();
        for (int i = 0; i < 256; ++i) values.add(new Key(i,false));
        values.add(null);
        Key dead = values.get(0);
        assertTrue(buffer.tryAcquire());
        try
        {
            buffer.removed().add(dead);
            for (int i = 1; i < 32; ++i) buffer.removed().add(values.get(i));
            buffer.removed().add(null);
            dead.id = 200;
            List<Key> expected = new ArrayList<Key>(values);
            expected.removeAll(buffer.removed());
            buffer.removeFrom(values);
            assertIdentityOrder(expected, values);
            assertFalse(values.contains(new Key(200,false)));
            assertFalse(values.contains(null));
        }
        finally { buffer.release(); }
        assertReleased(buffer);
    }

    @Test public void reentrantAcquisitionDoesNotOverwriteActiveScratch() throws Exception
    {
        ParticleRemovalBuffer<Key> buffer = new ParticleRemovalBuffer<Key>();
        assertTrue(buffer.tryAcquire());
        Key original = new Key(7,false);
        buffer.removed().add(original);
        assertFalse(buffer.tryAcquire());
        assertSame(original,buffer.removed().get(0));
        buffer.release();
        assertReleased(buffer);
        assertTrue(buffer.tryAcquire());
        assertTrue(buffer.removed().isEmpty());
        buffer.release();
    }

    @Test public void exceptionalHashingStillReleasesAllReferences() throws Exception
    {
        ParticleRemovalBuffer<Key> buffer = new ParticleRemovalBuffer<Key>();
        List<Key> values = new ArrayList<Key>();
        for (int i = 0; i < 128; ++i) values.add(new Key(i,false));
        assertTrue(buffer.tryAcquire());
        try
        {
            for (int i = 0; i < 32; ++i) buffer.removed().add(values.get(i));
            buffer.removed().add(new Key(999,false) {
                @Override public int hashCode() { throw new IllegalStateException("fixture failure"); }
            });
            try { buffer.removeFrom(values); fail("Expected fixture failure"); }
            catch (IllegalStateException expected) { assertEquals("fixture failure",expected.getMessage()); }
        }
        finally { buffer.release(); }
        assertReleased(buffer);
        assertTrue(buffer.tryAcquire());
        buffer.release();
    }

    @Test public void unexpectedOversizedBatchFallsBackWithoutRetainingAnUnboundedTable() throws Exception
    {
        ParticleRemovalBuffer<Key> buffer = new ParticleRemovalBuffer<Key>();
        List<Key> values = new ArrayList<Key>();
        for (int i = 0; i < 5000; ++i) values.add(new Key(i,false));
        assertTrue(buffer.tryAcquire());
        try
        {
            buffer.removed().addAll(values);
            buffer.removeFrom(values);
            assertTrue(values.isEmpty());
        }
        finally { buffer.release(); }
        assertReleased(buffer);
    }

    @Test public void emptyAndSmallRemovalKeepIdentityOrder() throws Exception
    {
        ParticleRemovalBuffer<Key> buffer = new ParticleRemovalBuffer<Key>();
        Key a=new Key(1,false),b=new Key(2,false),c=new Key(3,false);
        List<Key> values = new ArrayList<Key>(Arrays.asList(a,b,c));
        assertTrue(buffer.tryAcquire());
        try { buffer.removeFrom(values); assertIdentityOrder(Arrays.asList(a,b,c),values); }
        finally { buffer.release(); }
        assertTrue(buffer.tryAcquire());
        try { buffer.removed().add(b); buffer.removeFrom(values); assertIdentityOrder(Arrays.asList(a,c),values); }
        finally { buffer.release(); }
        assertReleased(buffer);
    }

    static void assertReleased(Object buffer) throws Exception
    {
        assertFalse((Boolean)field(buffer,"inUse"));
        assertTrue(((List<?>)field(buffer,"removed")).isEmpty());
        Object membership = field(buffer,"membership");
        Object[] table = (Object[])field(membership,"table");
        assertTrue("bounded retained table",table.length <= 8192);
        for (Object item : table) assertNull("particle retained after release",item);
        assertEquals(0,field(membership,"size"));
        assertEquals(false,field(membership,"hasNull"));
    }

    static Object field(Object instance, String name) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void assertIdentityOrder(List<?> expected, List<?> actual)
    {
        assertEquals(expected.size(),actual.size());
        for (int i=0;i<expected.size();++i) assertSame(expected.get(i),actual.get(i));
    }

    static class Key
    {
        int id;
        final boolean collide;
        Key(int id, boolean collide) { this.id=id; this.collide=collide; }
        @Override public int hashCode() { return this.collide ? 0 : this.id; }
        @Override public boolean equals(Object other) { return other instanceof Key && ((Key)other).id==this.id; }
    }
}
