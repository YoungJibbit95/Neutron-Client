package me.youngjibbit.module.impl.client.island;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandPresentationTest {
    private static IslandPresentation idle(boolean showName, boolean showVersion, boolean showClock) {
        IslandStateMachine.Selection view = new IslandStateMachine().resolve(1, 0, null, null, true, false);
        return IslandPresentation.from(view, "Neutron", "Dev 2.2", "11:46",
                showName, showVersion, showClock);
    }

    @Test public void idleUsesTheLegacyNameVersionSwitchesWithoutForcingBrand() {
        assertEquals("Neutron Dev 2.2", idle(true, true, true).title);
        assertEquals("Neutron", idle(true, false, true).title);
        assertEquals("Dev 2.2", idle(false, true, true).title);
        assertEquals("", idle(false, false, true).title);
        assertEquals("11:46", idle(false, false, true).trailing);
        assertEquals("", idle(true, true, false).trailing);
    }

    @Test public void nullableAndLongValuesCannotInjectFormattingOrControls() {
        IslandPresentation none = IslandPresentation.from(null, null, null, null, true, true, true);
        assertEquals(IslandStateMachine.State.IDLE, none.state);
        assertEquals("", none.title);
        assertEquals("", none.trailing);
        IslandPresentation styled = IslandPresentation.from(null, "\u00a7cNeutron\n\u0007  😀",
                "\u00a7aDev", "12:30\t", true, true, true);
        assertFalse(styled.title.contains("\u00a7"));
        assertFalse(styled.title.contains("\n"));
        assertFalse(styled.title.contains("\u0007"));
        assertEquals("12:30", styled.trailing);
    }

    @Test public void targetHealthAndAbsorptionAreFrozenAndFinite() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Target target = new IslandStateMachine.Target(
                5, "Player", 12f, 20f, 4f);
        IslandStateMachine.Selection selection = model.resolve(2, 1000, target, null, true, false);
        IslandPresentation content = IslandPresentation.from(selection, "Neutron", "Dev 2.2", "12:00",
                true, true, true);
        assertEquals(IslandStateMachine.State.TARGET, content.state);
        assertEquals("Player", content.title);
        assertEquals("12.0 / 20.0 HP +4.0", content.detail);
        assertEquals(.6f, content.healthFraction, .00001f);
        assertEquals(.2f, content.absorptionFraction, .00001f);
        assertTrue(content.hasHealthBar);
        assertFalse(content.fixtureOnly);
        assertEquals("", content.trailing);
    }

    @Test public void staleTargetIsNotRetainedAfterTargetLoss() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Target target = new IslandStateMachine.Target(9, "Gone", 0, 20, 0);
        IslandPresentation first = IslandPresentation.from(
                model.resolve(1, 10, target, null, true, false), "Neutron", "Dev", "12:00",
                true, true, false);
        IslandPresentation next = IslandPresentation.from(
                model.resolve(1, 11, null, null, true, false), "Neutron", "Dev", "12:00",
                true, true, false);
        assertEquals("Gone", first.title);
        assertEquals(IslandStateMachine.State.IDLE, next.state);
        assertFalse(next.title.contains("Gone"));
        assertFalse(next.hasHealthBar);
    }

    @Test public void fixturesAreMarkedPendingAndNeverClaimServerVerification() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Scaffold scaffold = new IslandStateMachine.Scaffold(true, 16, "Normal");
        IslandPresentation fixture = IslandPresentation.from(
                model.resolve(1, 0, null, scaffold, false, true), "", "", "",
                true, true, true);
        assertTrue(fixture.fixtureOnly);
        assertEquals("Scaffold  16 blocks", fixture.title);
        assertEquals("Normal", fixture.detail);
        assertFalse(fixture.hasHealthBar);
        assertTrue(model.offer(IslandStateMachine.State.POSSIBLE_SETBACK,
                "synthetic-1", "Verified anticheat flag!", 1, 10));
        IslandPresentation warning = IslandPresentation.from(
                model.resolve(1, 12, null, null, false, false), "", "", "",
                true, true, true);
        assertTrue(warning.fixtureOnly);
        assertEquals("Possible setback (local)", warning.title);
        assertFalse(warning.title.toLowerCase().contains("verified"));
    }

    @Test public void toastTextIsBoundedAndDoesNotChangeGameplayState() {
        IslandStateMachine model = new IslandStateMachine();
        String name = "Very long module name 🛰️ with emojis and untrusted §c formatting";
        assertTrue(model.offer(IslandStateMachine.State.MODULE_TOAST, "toggle-1", name, 1, 0));
        IslandPresentation presentation = IslandPresentation.from(
                model.resolve(1, 1, null, null, false, false), "", "", "",
                true, true, true);
        assertTrue(presentation.fixtureOnly);
        assertTrue(presentation.title.codePointCount(0, presentation.title.length()) <= 64);
        assertFalse(presentation.title.contains("§"));
        assertEquals(1, model.pendingEventCount());
    }

    @Test public void nullAndInvalidSyntheticTargetNeverProduceAHealthBar() {
        IslandStateMachine model = new IslandStateMachine();
        IslandStateMachine.Target invalid = new IslandStateMachine.Target(-1, "No", Float.NaN,
                Float.NaN, Float.NaN);
        IslandPresentation result = IslandPresentation.from(
                model.resolve(2, 0, invalid, null, true, false), "Neutron", "Dev 2.2", "12:00",
                true, true, false);
        assertEquals(IslandStateMachine.State.IDLE, result.state);
        assertFalse(result.hasHealthBar);
        assertEquals(0, result.healthFraction, 0);
    }
}
