package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.player.AttackEvent;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.impl.ghost.WTap2;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInputFromOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.*;

/** Actual movement-input and WTap event paths, with disposable client state and no window. */
@RunWith(Parameterized.class)
public class ContainerMovementInputTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"inventory"},{"chest"},{"chat"},{"resume"},
                {"stapInventory"},{"stapChest"},{"stapFocusLost"},
                {"activeStapChest"},{"activeStapFocusLost"},{"disableInInventory"},
                {"modeChange"},{"worldUnload"},{"expiredOwnership"},{"idleLeavesKeysAlone"}});
    }
    private final String method;
    public ContainerMovementInputTest(String method) { this.method=method; }
    @Test public void freshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class,method); }

    public static class Checks {
        private GameSettings settings() throws Exception {
            GameSettings s=RotationBehaviorHookTest.bare(GameSettings.class);
            s.keyBindForward=new KeyBinding("forward",17,"test");
            s.keyBindBack=new KeyBinding("back",31,"test");
            s.keyBindLeft=new KeyBinding("left",30,"test");
            s.keyBindRight=new KeyBinding("right",32,"test");
            s.keyBindJump=new KeyBinding("jump",57,"test");
            s.keyBindSneak=new KeyBinding("sneak",42,"test");
            return s;
        }
        private void blocked(Class<? extends GuiScreen> screen) throws Exception {
            try (RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
                GameSettings s=settings();f.mc.gameSettings=s;
                MovementInputFromOptions input=new MovementInputFromOptions(s);
                f.mc.currentScreen=RotationBehaviorHookTest.bare(screen);
                for(KeyBinding direction:new KeyBinding[]{s.keyBindForward,s.keyBindBack,s.keyBindLeft,s.keyBindRight}) {
                    direction.pressed=true;s.keyBindJump.pressed=true;s.keyBindSneak.pressed=true;
                    input.updatePlayerMoveState();
                    assertEquals("screen must suppress forward/back",0,input.moveForward,0);
                    assertEquals("screen must suppress strafe",0,input.moveStrafe,0);
                    assertFalse("screen must suppress jump",input.jump);
                    assertFalse("screen must suppress sneak",input.sneak);
                    direction.pressed=false;
                }
            }
        }
        @Test public void inventory() throws Exception { blocked(GuiInventory.class); }
        @Test public void chest() throws Exception { blocked(GuiChest.class); }
        @Test public void chat() throws Exception { blocked(GuiChat.class); }
        @Test public void resume() throws Exception {
            try (RotationBehaviorHookTest.Fixture f=new RotationBehaviorHookTest.Fixture()) {
                GameSettings s=settings();f.mc.gameSettings=s;
                MovementInputFromOptions input=new MovementInputFromOptions(s);
                s.keyBindForward.pressed=true;s.keyBindLeft.pressed=true;s.keyBindJump.pressed=true;
                f.mc.currentScreen=RotationBehaviorHookTest.bare(GuiChest.class);
                input.updatePlayerMoveState();
                f.mc.currentScreen=null;
                input.updatePlayerMoveState();
                assertEquals(1,input.moveForward,0);assertEquals(1,input.moveStrafe,0);assertTrue(input.jump);
                s.keyBindSneak.pressed=true;input.updatePlayerMoveState();
                assertEquals(.3f,input.moveForward,1e-6);assertEquals(.3f,input.moveStrafe,1e-6);assertTrue(input.sneak);
            }
        }
        private void stapBlocked(Class<? extends GuiScreen> screen) throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();WTap2 tap=CombatFollowupTest.tap(r);
                tap.getModeSetting("Mode").setValue("STap");
                r.f.run.fixture.mc.currentScreen=screen==null?null:RotationBehaviorHookTest.bare(screen);
                r.f.run.fixture.mc.inGameHasFocus=false;
                try {
                    Pulsar.BUS.post(new AttackEvent(r.f.first));
                    assertFalse("blocked input must not arm synthetic backwards movement",
                            ScaffoldOwnerContinuityTest.field(WTap2.class,"stapOwned").getBoolean(tap));
                } finally { CombatFollowupTest.detach(tap); }
            }
        }
        @Test public void stapInventory() throws Exception { stapBlocked(GuiInventory.class); }
        @Test public void stapChest() throws Exception { stapBlocked(GuiChest.class); }
        @Test public void stapFocusLost() throws Exception { stapBlocked(null); }

        private void lifecycle(String transition) throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                r.remoteTarget();WTap2 tap=CombatFollowupTest.tap(r);
                GameSettings s=r.f.run.fixture.mc.gameSettings;
                s.keyBindBack=new KeyBinding("back",31,"test");
                tap.getModeSetting("Mode").setValue("STap");
                try {
                    Pulsar.BUS.post(new AttackEvent(r.f.first));
                    assertTrue("control: focused game permits STap",ScaffoldOwnerContinuityTest.field(WTap2.class,"stapOwned").getBoolean(tap));
                    ScaffoldOwnerContinuityTest.field(WTap2.class,"releaseAt").setLong(tap,Long.MAX_VALUE);
                    Pulsar.BUS.post(new TickEvent());
                    assertFalse(s.keyBindForward.pressed);assertTrue("active tap drives backwards",s.keyBindBack.pressed);
                    switch(transition) {
                        case "chest":
                            r.f.run.fixture.mc.currentScreen=RotationBehaviorHookTest.bare(GuiChest.class);
                            Pulsar.BUS.post(new TickEvent());break;
                        case "focus":
                            r.f.run.fixture.mc.inGameHasFocus=false;Pulsar.BUS.post(new TickEvent());break;
                        case "disable":
                            r.f.run.fixture.mc.currentScreen=RotationBehaviorHookTest.bare(GuiInventory.class);
                            tap.setToggled(false);break;
                        case "mode": tap.getModeSetting("Mode").setValue("Legit");break;
                        case "world": Pulsar.BUS.post(new WorldEvent.Unload(r.f.run.scene));break;
                        case "expiry":
                            ScaffoldOwnerContinuityTest.field(WTap2.class,"releaseAt").setLong(tap,0);
                            Pulsar.BUS.post(new TickEvent());break;
                        default: fail(transition);
                    }
                    assertFalse(s.keyBindForward.pressed);assertFalse(s.keyBindBack.pressed);
                    assertFalse(ScaffoldOwnerContinuityTest.field(WTap2.class,"stapOwned").getBoolean(tap));
                    assertEquals(0,ScaffoldOwnerContinuityTest.field(WTap2.class,"releaseAt").getLong(tap));
                } finally { CombatFollowupTest.detach(tap); }
            }
        }
        @Test public void activeStapChest() throws Exception { lifecycle("chest"); }
        @Test public void activeStapFocusLost() throws Exception { lifecycle("focus"); }
        @Test public void disableInInventory() throws Exception { lifecycle("disable"); }
        @Test public void modeChange() throws Exception { lifecycle("mode"); }
        @Test public void worldUnload() throws Exception { lifecycle("world"); }
        @Test public void expiredOwnership() throws Exception { lifecycle("expiry"); }
        @Test public void idleLeavesKeysAlone() throws Exception {
            try(AuraMovementConsistencyTest.Run r=new AuraMovementConsistencyTest.Run("Legacy Strict")) {
                WTap2 tap=CombatFollowupTest.tap(r);tap.getModeSetting("Mode").setValue("STap");
                try {
                    Pulsar.BUS.post(new TickEvent());
                    assertTrue("idle STap must not overwrite vanilla W",r.f.run.fixture.mc.gameSettings.keyBindForward.pressed);
                } finally { CombatFollowupTest.detach(tap); }
            }
        }
    }
}
