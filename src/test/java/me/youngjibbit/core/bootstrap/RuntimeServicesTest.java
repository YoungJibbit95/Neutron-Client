package me.youngjibbit.core.bootstrap;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.*;

/** Service identity across bootstrap boundaries; no world, rendering or actions. */
public class RuntimeServicesTest {
    @Test public void canonicalRuntimeTakesPrecedenceWithoutReplacingOrResettingFallbacks() throws Exception {
        Field field=Pulsar.class.getDeclaredField("runtime");
        field.setAccessible(true);
        Object previous=field.get(Pulsar.INSTANCE);
        try {
            field.set(Pulsar.INSTANCE,null);
            assertNull(RuntimeServices.runtime());
            assertNull(RuntimeServices.observation());
            assertNull(RuntimeServices.personalAiLoader());
            Object[] fallbacks=services();
            for(Object service:fallbacks)assertNotNull(service);
            assertSameServices(fallbacks,services());

            ClientRuntime first=new ClientRuntime(new ModuleManager(Collections.emptyList()),null);
            field.set(Pulsar.INSTANCE,first);
            assertSame(first,RuntimeServices.runtime());
            assertSame(first.getObservation(),RuntimeServices.observation());
            assertSameServices(canonical(first),services());
            for(int i=0;i<fallbacks.length;i++)assertNotSame(fallbacks[i],services()[i]);

            ClientRuntime replacement=new ClientRuntime(new ModuleManager(Collections.emptyList()),null);
            field.set(Pulsar.INSTANCE,replacement);
            assertSame(replacement,RuntimeServices.runtime());
            assertSame(replacement.getObservation(),RuntimeServices.observation());
            assertSameServices(canonical(replacement),services());

            field.set(Pulsar.INSTANCE,null);
            assertNull(RuntimeServices.observation());
            assertSameServices(fallbacks,services());
        } finally {
            field.set(Pulsar.INSTANCE,previous);
        }
    }

    private static Object[] services() {
        return new Object[]{RuntimeServices.targeting(),RuntimeServices.rotation(),RuntimeServices.raycast(),
                RuntimeServices.attackScheduler(),RuntimeServices.scaffoldPlacement()};
    }

    private static Object[] canonical(ClientRuntime runtime) {
        return new Object[]{runtime.getTargetingService(),runtime.getRotationManager(),runtime.getRaycastService(),
                runtime.getAttackScheduler(),runtime.getScaffoldPlacementService()};
    }

    private static void assertSameServices(Object[] expected,Object[] actual) {
        assertEquals(expected.length,actual.length);
        for(int i=0;i<expected.length;i++)assertSame(expected[i],actual[i]);
    }
}
