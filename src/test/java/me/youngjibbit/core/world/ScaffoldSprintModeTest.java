package me.youngjibbit.core.world;

import me.youngjibbit.module.impl.world.Scaffold;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real input, walking PRE and C0B consumers, isolated from other synthetic clients. */
public class ScaffoldSprintModeTest {
    @Test public void offOwnsSearchOnlyStepsInEveryApproach() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"offOwnsSearchOnlyStepsInEveryApproach");
    }
    @Test public void onForcesSprintWithoutKeysOrAPlacementPlan() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"onForcesSprintWithoutKeysOrAPlacementPlan");
    }
    @Test public void legitUsesForwardFoodCollisionSneakAndItemConditions() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"legitUsesForwardFoodCollisionSneakAndItemConditions");
    }
    @Test public void disabledScaffoldReturnsSprintToTheCaller() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"disabledScaffoldReturnsSprintToTheCaller");
    }
    @Test public void sceneSupplierDisableCannotApplyAnObsoleteSprintPolicy() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"sceneSupplierDisableCannotApplyAnObsoleteSprintPolicy");
    }
    @Test public void nativeSetterEnforcesThePolicyBeforeTheSprintFlagAndSpeedAttribute() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"nativeSetterEnforcesThePolicyBeforeTheSprintFlagAndSpeedAttribute");
    }

    /** Keep the real EntityPlayerSP setter, Entity flag and vanilla speed attribute consumer. */
    public static final class NativeSprintPlayer extends net.minecraft.client.entity.EntityPlayerSP {
        private net.minecraft.util.FoodStats food;
        public NativeSprintPlayer(){super(null,null,null,null);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isRiding(){return false;}
        @Override public net.minecraft.util.FoodStats getFoodStats(){return food;}
        @Override public boolean isUsingItem(){return false;}
        @Override public boolean isPotionActive(net.minecraft.potion.Potion potion){return false;}
    }

    public static class Checks {
        @Test public void nativeSetterEnforcesThePolicyBeforeTheSprintFlagAndSpeedAttribute() throws Exception {
            try(me.youngjibbit.core.combat.RotationBehaviorHookTest.Fixture f=
                        new me.youngjibbit.core.combat.RotationBehaviorHookTest.Fixture()){
                ScaffoldClientFlowTest.prepare(f,.05,.05);
                NativeSprintPlayer player=me.youngjibbit.core.combat.RotationBehaviorHookTest.bare(NativeSprintPlayer.class);
                player.worldObj=f.mc.theWorld;player.food=new net.minecraft.util.FoodStats();
                player.capabilities=new net.minecraft.entity.player.PlayerCapabilities();
                player.movementInput=new net.minecraft.util.MovementInput();
                player.movementInput.moveForward=1;player.movementInput.moveStrafe=0;
                player.inventory=new net.minecraft.entity.player.InventoryPlayer(player);
                net.minecraft.entity.DataWatcher watcher=new net.minecraft.entity.DataWatcher(player);watcher.addObject(0,(byte)0);
                ScaffoldClientFlowTest.set(net.minecraft.entity.Entity.class,player,"dataWatcher",watcher);
                ScaffoldClientFlowTest.set(net.minecraft.client.entity.EntityPlayerSP.class,player,"sendQueue",f.player.sendQueue);
                player.getAttributeMap().registerAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
                f.mc.thePlayer=player;f.scaffold.onEnable();
                try{
                    player.setSprinting(true);assertFalse(player.isSprinting());
                    assertEquals(.1,player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getAttributeValue(),1e-7);
                    f.scaffold.getModeSetting("Sprint Mode").setValue("On");
                    player.setSprinting(false);assertTrue(player.isSprinting());
                    assertEquals(.13,player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getAttributeValue(),1e-7);
                    f.scaffold.getModeSetting("Sprint Mode").setValue("Legit");
                    player.food.setFoodLevel(6);player.setSprinting(true);assertFalse(player.isSprinting());
                    player.food.setFoodLevel(20);player.setSprinting(false);assertTrue(player.isSprinting());
                    player.movementInput.moveForward=-1;player.setSprinting(true);assertFalse(player.isSprinting());
                    f.scaffold.setToggled(false);player.setSprinting(true);assertTrue(player.isSprinting());
                    player.setSprinting(false);assertFalse(player.isSprinting());
                }finally{if(f.scaffold.isToggled())f.scaffold.setToggled(false);}
            }
        }

        @Test public void offOwnsSearchOnlyStepsInEveryApproach() throws Exception {
            for(String approach:new String[]{"Neutron","Arsenic","Adaptive Bridge"}) {
                try(AdaptiveBridgeInputSafetyFlowTest.Run r=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                    r.f.scaffold.getModeSetting("Scaffold Approach").setValue(approach);
                    r.f.scaffold.getModeSetting("Bridge Edge Safety").setValue("Off");
                    r.player.inventory.mainInventory[3]=null;
                    r.settings.keyBindForward.pressed=r.settings.keyBindSprint.pressed=true;
                    r.f.scaffold.onEnable();
                    for(int step=0;step<3;step++) {
                        r.player.sprint=true;
                        assertFalse(approach,Scaffold.filterSprint(r.player,true));
                        r.step(true,()->assertFalse(approach,r.player.isSprinting()));
                        assertFalse(r.player.isServerSprintState());
                        assertFalse(r.drain().stream().filter(C0BPacketEntityAction.class::isInstance)
                                .map(C0BPacketEntityAction.class::cast)
                                .anyMatch(p->p.getAction()==C0BPacketEntityAction.Action.START_SPRINTING));
                    }
                }
            }
        }

        @Test public void onForcesSprintWithoutKeysOrAPlacementPlan() throws Exception {
            for(String approach:new String[]{"Neutron","Arsenic","Adaptive Bridge"}) {
                try(AdaptiveBridgeInputSafetyFlowTest.Run r=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                    r.f.scaffold.getModeSetting("Scaffold Approach").setValue(approach);
                    r.f.scaffold.getModeSetting("Sprint Mode").setValue("On");
                    r.player.inventory.mainInventory[3]=null;
                    r.player.food.setFoodLevel(0);
                    r.f.scaffold.onEnable();
                    for(int step=0;step<3;step++) {
                        r.player.sprint=false;
                        r.step(true,()->assertTrue(approach,r.player.isSprinting()));
                        assertTrue(r.player.isServerSprintState());
                    }
                    assertFalse(r.settings.keyBindSprint.isKeyDown());
                    assertTrue(r.drain().stream().filter(C0BPacketEntityAction.class::isInstance)
                            .map(C0BPacketEntityAction.class::cast)
                            .anyMatch(p->p.getAction()==C0BPacketEntityAction.Action.START_SPRINTING));
                }
            }
        }

        @Test public void legitUsesForwardFoodCollisionSneakAndItemConditions() throws Exception {
            try(AdaptiveBridgeInputSafetyFlowTest.Run r=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                r.adaptive("Off");r.f.scaffold.getModeSetting("Sprint Mode").setValue("Legit");
                r.player.inventory.mainInventory[3]=null;
                r.settings.keyBindForward.pressed=true;r.f.scaffold.onEnable();
                r.input();assertTrue("eligible forward input auto-sprints",r.player.isSprinting());
                r.player.food.setFoodLevel(6);r.input();assertFalse(r.player.isSprinting());
                r.player.food.setFoodLevel(20);r.player.isCollidedHorizontally=true;
                r.input();assertFalse(r.player.isSprinting());
                r.player.isCollidedHorizontally=false;r.settings.keyBindSneak.pressed=true;
                r.input();assertFalse(r.player.isSprinting());
                r.settings.keyBindSneak.pressed=false;r.player.usingItem=true;
                r.input();assertFalse("item use cannot borrow NoSlow permission",r.player.isSprinting());
                r.player.usingItem=false;r.settings.keyBindForward.pressed=false;r.settings.keyBindBack.pressed=true;
                r.input();assertFalse(r.player.isSprinting());
                r.settings.keyBindBack.pressed=false;r.settings.keyBindLeft.pressed=true;
                r.input();assertFalse(r.player.isSprinting());
                r.settings.keyBindForward.pressed=true;
                r.input();assertTrue("forward diagonal input remains eligible",r.player.isSprinting());
                assertFalse("policy does not simulate a held physical key",r.settings.keyBindSprint.isKeyDown());
            }
        }

        @Test public void disabledScaffoldReturnsSprintToTheCaller() throws Exception {
            try(AdaptiveBridgeInputSafetyFlowTest.Run r=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                r.f.scaffold.onEnable();assertFalse(Scaffold.filterSprint(r.player,true));
                r.f.scaffold.onDisable();
                assertTrue(Scaffold.filterSprint(r.player,true));
                assertFalse(Scaffold.filterSprint(r.player,false));
            }
        }

        @Test @SuppressWarnings("unchecked")
        public void sceneSupplierDisableCannotApplyAnObsoleteSprintPolicy() throws Exception {
            for(String mode:new String[]{"Off","On"})for(boolean apply:new boolean[]{false,true}) {
                try(AdaptiveBridgeInputSafetyFlowTest.Run r=new AdaptiveBridgeInputSafetyFlowTest.Run()) {
                    r.f.scaffold.getModeSetting("Sprint Mode").setValue(mode);r.f.scaffold.onEnable();
                    // A real observed step binds contextScene. Before the first outer tick,
                    // sameScene intentionally has no observer token and cannot call this supplier.
                    r.input();
                    boolean requested="Off".equals(mode);
                    assertEquals("positive control: the enabled policy owns this scene",!requested,
                            Scaffold.filterSprint(r.player,requested));
                    me.youngjibbit.core.observation.RuntimeObservation observation=r.f.runtime.getObservation();
                    java.lang.reflect.Field source=ScaffoldOwnerContinuityTest.field(observation.getClass(),"source");
                    java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample> original=
                            (java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample>)source.get(observation);
                    java.util.concurrent.atomic.AtomicInteger disables=new java.util.concurrent.atomic.AtomicInteger();
                    java.util.function.Supplier<me.youngjibbit.core.observation.RuntimeObservation.SceneSample> reentrant=()->{
                        if(disables.getAndIncrement()==0)r.f.scaffold.setToggled(false);
                        // Same world and player: scene equality alone cannot prove module ownership.
                        return original.get();
                    };
                    source.set(observation,reentrant);
                    try {
                        boolean result;
                        if(apply) {
                            r.player.sprint=requested;Scaffold.applySprintPolicy(r.player);
                            result=r.player.isSprinting();
                        } else result=Scaffold.filterSprint(r.player,requested);
                        assertTrue("the actual scene supplier boundary ran",disables.get()>0);
                        assertFalse(r.f.scaffold.isToggled());
                        assertEquals(mode+": disabled owner must preserve the caller's sprint request",requested,result);
                    } finally {source.set(observation,original);}
                }
            }
        }
    }
}
