package me.youngjibbit.core.combat;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatPacketStateTest {
    @Test public void movementDoesNotEndServerItemUseAndActionsBlockOnlyTheirOwnMovementGroup() {
        Bootstrap.register();
        CombatPacketState state = new CombatPacketState();
        state.accepted(new C08PacketPlayerBlockPlacement(new ItemStack(Items.diamond_sword)), 0);
        state.accepted(new C03PacketPlayer(true), 50_000_000);
        assertFalse(state.mayAttack(50_000_000));
        state.accepted(new C09PacketHeldItemChange(1), 50_000_000);
        assertFalse(state.mayAttack(50_000_000));
        state.accepted(new C03PacketPlayer(true), 100_000_000);
        assertTrue(state.mayAttack(100_000_000));
        state.accepted(new C0APacketAnimation(), 100_000_000);
        assertFalse(state.mayAttack(100_000_000));
        state.reset(); assertTrue(state.mayAttack(100_000_000));
    }
}
