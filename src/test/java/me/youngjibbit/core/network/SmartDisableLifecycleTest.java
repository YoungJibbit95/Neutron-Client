package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.network.PlaySessionEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.player.InventoryManager;
import me.youngjibbit.module.impl.player.Stealer;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/** Client-thread lifecycle observation, using the normal Module toggle/cleanup path. */
public class SmartDisableLifecycleTest {
    @ModuleInfo(name = "Smart Disable Fixture", category = Category.Player)
    public static final class Guarded extends Module {
        final CheckBoxValue existing = new CheckBoxValue("Existing", this, true);
        int enables, disables, updates;
        Runnable duringDisable;
        Guarded() { enableSmartDisable(); }
        @Override public void onEnable() { enables++; super.onEnable(); }
        @Override public void onDisable() {
            disables++;
            if (duringDisable != null) duringDisable.run();
            super.onDisable();
        }
        @Subscribe final Listener<EventUpdate> action = new Listener<>(event -> {
            if (isToggled()) updates++;
        });
    }

    private static CheckBoxValue death(Module module) { return module.getBooleanSetting("Smart Disable on Death"); }
    private static CheckBoxValue world(Module module) { return module.getBooleanSetting("Smart Disable on World Change"); }

    @Test public void defaultsAndAppendOnlyRegistrationPreserveExistingBehavior() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded();
            assertSame(module.existing, module.getValues().get(0));
            assertEquals("smart_disable_on_death", module.getValues().get(1).getId());
            assertEquals("smart_disable_on_world_change", module.getValues().get(2).getId());
            assertFalse(death(module).getValue()); assertFalse(world(module).getValue());
            module.setToggled(true);
            try {
                f.player.isDead = true;
                Pulsar.BUS.post(new EventUpdate());
                Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                assertTrue(module.isToggled()); assertEquals(0, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void deathStopsBeforeUpdateAndNeverAutomaticallyReenablesAfterRespawn() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded(); death(module).setValue(true); module.setToggled(true);
            try {
                Pulsar.BUS.post(new EventUpdate()); assertEquals(1, module.updates);
                f.player.isDead = true;
                Pulsar.BUS.post(new EventUpdate());
                assertFalse(module.isToggled()); assertEquals(1, module.disables); assertEquals(1, module.updates);
                f.player.isDead = false;
                Pulsar.BUS.post(new WorldEvent.Load(f.world)); Pulsar.BUS.post(new EventUpdate());
                assertFalse(module.isToggled()); assertEquals(1, module.enables); assertEquals(1, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void deathAndWorldOptionsAreIndependent() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded(); death(module).setValue(true); module.setToggled(true);
            try {
                Pulsar.BUS.post(new WorldEvent.Unload(f.world)); assertTrue(module.isToggled());
                death(module).setValue(false); world(module).setValue(true);
                f.player.isDead = true; Pulsar.BUS.post(new TickEvent()); assertTrue(module.isToggled());
                Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                assertFalse(module.isToggled()); assertEquals(1, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void unloadStopsExactlyOnceEvenWhenCleanupPublishesNestedEvents() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded(); world(module).setValue(true); module.setToggled(true);
            module.duringDisable = () -> {
                Pulsar.BUS.post(new WorldEvent.Unload(f.world)); Pulsar.BUS.post(new TickEvent());
                Pulsar.BUS.post(new EventUpdate());
            };
            try {
                Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                assertFalse(module.isToggled()); assertEquals(1, module.disables); assertEquals(0, module.updates);
                Pulsar.BUS.post(new WorldEvent.Load(f.world)); Pulsar.BUS.post(new TickEvent());
                assertEquals(1, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void worldAndPlayerIdentityChangesStopBeforeDirectRotationOrMotionEvents() throws Exception {
        for (boolean playerOnly : new boolean[]{false, true}) try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded(); world(module).setValue(true); module.setToggled(true);
            try {
                if (playerOnly) f.client.thePlayer = NetworkTestSupport.bare(ExploitHookFixture.Player.class);
                else f.client.theWorld = NetworkTestSupport.bare(WorldClient.class);
                EventMotion motion = new EventMotion(0, 0, 0, 0, 0, true);
                if (playerOnly) Pulsar.BUS.post(motion); else Pulsar.BUS.post(new EventRotation(motion));
                assertFalse(module.isToggled()); assertEquals(1, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void enablingInMenusAllowsFirstJoinButStopsOnTheNextWorld() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            f.client.thePlayer = null; f.client.theWorld = null;
            Guarded module = new Guarded(); world(module).setValue(true); module.setToggled(true);
            try {
                Pulsar.BUS.post(new TickEvent()); assertTrue(module.isToggled());
                f.client.theWorld = f.world; f.client.thePlayer = f.player;
                Pulsar.BUS.post(new WorldEvent.Load(f.world)); assertTrue(module.isToggled());
                f.client.theWorld = NetworkTestSupport.bare(WorldClient.class);
                Pulsar.BUS.post(new TickEvent());
                assertFalse(module.isToggled()); assertEquals(1, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void onlyTheObservedConnectionEndStopsTheModule() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded(); world(module).setValue(true); module.setToggled(true);
            try {
                Pulsar.BUS.post(new PlaySessionEvent(new NetworkManager(EnumPacketDirection.CLIENTBOUND), PlaySessionEvent.State.ENDED));
                assertTrue(module.isToggled());
                Pulsar.BUS.post(new PlaySessionEvent(f.connection.network, PlaySessionEvent.State.ENDED));
                assertFalse(module.isToggled()); assertEquals(1, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void lateOptInStartsFromTheCurrentWorldAndLegacyConfigImportResetsMissingKeys() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded(); module.setToggled(true);
            try {
                f.client.theWorld = NetworkTestSupport.bare(WorldClient.class);
                world(module).setValue(true); Pulsar.BUS.post(new TickEvent()); assertTrue(module.isToggled());
                death(module).setValue(true);
                module.beginConfigUpdate(); module.endConfigUpdate();
                assertFalse(death(module).getValue()); assertFalse(world(module).getValue());
                module.beginConfigUpdate(); death(module).setValue(true); module.endConfigUpdate();
                assertTrue(death(module).getValue()); assertFalse(world(module).getValue());
            } finally { module.setToggled(false); }
        }
    }

    @Test public void nonClientThreadNotificationsDoNotMutateTheModule() throws Exception {
        try (ExploitHookFixture f = new ExploitHookFixture()) {
            Guarded module = new Guarded(); death(module).setValue(true); module.setToggled(true);
            try {
                f.connection.netty(() -> { f.player.isDead = true; Pulsar.BUS.post(new TickEvent()); });
                assertTrue(module.isToggled()); assertEquals(0, module.disables);
                Pulsar.BUS.post(new EventUpdate());
                assertFalse(module.isToggled()); assertEquals(1, module.disables);
            } finally { module.setToggled(false); }
        }
    }

    @Test public void allFourRequestedModulesExposeTheSameOptInSettings() {
        for (Module module : Arrays.asList(new Aura(), new Scaffold(), new Stealer(), new InventoryManager())) {
            assertNotNull(module.getName(), death(module)); assertNotNull(module.getName(), world(module));
            assertFalse(death(module).getValue()); assertFalse(world(module).getValue());
            assertTrue(death(module).isVisible()); assertTrue(world(module).isVisible());
            int deathIndex=module.getValues().indexOf(death(module));
            assertSame(world(module), module.getValues().get(deathIndex+1));
            String firstPhase2=module instanceof Aura?"adaptive_curve_strength"
                    :module instanceof Scaffold?null:"interaction_mode";
            if(firstPhase2==null)assertEquals(module.getValues().size()-2,deathIndex);
            else assertSame("Phase 2 must append after the existing lifecycle pair",module.getValueByName(firstPhase2),
                    module.getValues().get(deathIndex+2));
        }
        Scaffold scaffold = new Scaffold();
        for (String mode : new String[]{"Neutron", "Arsenic", "Adaptive Bridge"}) {
            scaffold.getModeSetting("Scaffold Approach").setValue(mode);
            assertTrue(death(scaffold).isVisible()); assertTrue(world(scaffold).isVisible());
        }
    }
}
