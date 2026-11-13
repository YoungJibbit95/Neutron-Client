package net.minecraft.client.renderer.chunk;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.util.EnumFacing;
import org.junit.Test;
import static org.junit.Assert.*;

public class SetVisibilityTest
{
    @Test public void allThirtySixFacePairsHaveIndependentBits()
    {
        for (EnumFacing first : EnumFacing.VALUES)
            for (EnumFacing second : EnumFacing.VALUES)
            {
                SetVisibility visibility = new SetVisibility();
                visibility.setVisible(first, second, true);
                for (EnumFacing a : EnumFacing.VALUES)
                    for (EnumFacing b : EnumFacing.VALUES)
                        assertEquals(first + "/" + second + " leaked to " + a + "/" + b,
                                a == first && b == second || a == second && b == first,
                                visibility.isVisible(a, b));
                visibility.setVisible(first, second, false);
                assertAll(visibility, false);
            }
    }

    @Test public void clearingHighPairsDoesNotClearTheirLowBitAliases()
    {
        for (EnumFacing first : EnumFacing.VALUES)
            for (EnumFacing second : EnumFacing.VALUES)
            {
                SetVisibility visibility = new SetVisibility();
                visibility.setAllVisible(true);
                visibility.setVisible(first, second, false);
                for (EnumFacing a : EnumFacing.VALUES)
                    for (EnumFacing b : EnumFacing.VALUES)
                        assertEquals(!(a == first && b == second || a == second && b == first),
                                visibility.isVisible(a, b));
            }
    }

    @Test public void everyFaceMaskMatchesAnIndependentBooleanMatrix()
    {
        for (int mask = 0; mask < 64; ++mask)
        {
            Set<EnumFacing> faces = EnumSet.noneOf(EnumFacing.class);
            for (EnumFacing face : EnumFacing.VALUES)
                if ((mask & 1 << face.ordinal()) != 0) faces.add(face);
            SetVisibility normal = new SetVisibility();
            SetVisibility fast = new SetVisibility();
            normal.setManyVisible(faces);
            fast.setManyVisibleMask(mask);
            for (EnumFacing a : EnumFacing.VALUES)
                for (EnumFacing b : EnumFacing.VALUES)
                {
                    boolean expected = faces.contains(a) && faces.contains(b);
                    assertEquals(expected, normal.isVisible(a, b));
                    assertEquals(expected, fast.isVisible(a, b));
                }
        }
    }

    @Test public void disconnectedComponentsDoNotCreateCrossComponentPairs()
    {
        SetVisibility visibility = new SetVisibility();
        visibility.setManyVisibleMask((1 << EnumFacing.NORTH.ordinal()) | (1 << EnumFacing.EAST.ordinal()));
        visibility.setManyVisibleMask((1 << EnumFacing.DOWN.ordinal()) | (1 << EnumFacing.UP.ordinal()));
        assertTrue(visibility.isVisible(EnumFacing.NORTH, EnumFacing.EAST));
        assertTrue(visibility.isVisible(EnumFacing.DOWN, EnumFacing.UP));
        assertFalse(visibility.isVisible(EnumFacing.DOWN, EnumFacing.EAST));
        assertFalse(visibility.isVisible(EnumFacing.NORTH, EnumFacing.UP));
    }

    private static void assertAll(SetVisibility visibility, boolean expected)
    {
        for (EnumFacing a : EnumFacing.VALUES)
            for (EnumFacing b : EnumFacing.VALUES)
                assertEquals(expected, visibility.isVisible(a, b));
    }
}
