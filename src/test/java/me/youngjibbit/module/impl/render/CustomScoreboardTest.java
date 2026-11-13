package me.youngjibbit.module.impl.render;

import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.HudDrag;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Font;
import java.util.List;

import static org.junit.Assert.*;

public class CustomScoreboardTest {
    private TTFFontRenderer body, title;

    @Before public void fonts() {
        body = new TTFFontRenderer(new Font("Dialog", Font.PLAIN, 20));
        title = new TTFFontRenderer(new Font("Dialog", Font.BOLD, 20));
    }

    @After public void close() {
        body.close();
        title.close();
        HudDrag.context(false, false);
    }

    static ScoreObjective objective() {
        Scoreboard board = new Scoreboard();
        ScoreObjective objective = board.addScoreObjective("hud", IScoreObjectiveCriteria.DUMMY);
        objective.setDisplayName("\u00a7bA long scoreboard title with wide WWW glyphs");
        for (int i = 0; i < 20; i++) board.getValueFromObjective("WWW player " + i, objective).setScorePoints(i);
        for (int i = 0; i < 10; i++) board.getValueFromObjective("#hidden" + i, objective).setScorePoints(100 + i);
        return objective;
    }

    @Test public void filtersHiddenEntriesBeforeTakingTheLastFifteenScores() {
        List<Score> rows = CustomScoreboard.visibleScores(objective());
        assertEquals(15, rows.size());
        assertEquals(5, rows.get(0).getScorePoints());
        assertEquals(19, rows.get(14).getScorePoints());
        for (Score row : rows) assertFalse(row.getPlayerName().startsWith("#"));
    }

    @Test public void usesDrawingFontMetricsAndFitsRowsTitleAndBoundsAcrossAspectRatios() {
        CustomScoreboard custom = new CustomScoreboard();
        for (int[] size : new int[][]{{100, 80}, {320, 240}, {550, 320}, {960, 540}}) {
            CustomScoreboard.Frame frame = custom.layout(objective(), size[0], size[1], body, title);
            assertEquals(15, frame.rows.size());
            assertEquals("19", frame.rows.get(0).score);
            assertTrue(frame.x >= 0 && frame.y >= 0);
            assertTrue(frame.x + frame.screenWidth() <= size[0] + .001);
            assertTrue(frame.y + frame.screenHeight() <= size[1] + .001);
            assertTrue(title.getWidth(frame.title) <= frame.width - 16 + .001);
            for (CustomScoreboard.Row row : frame.rows) {
                assertTrue(body.getWidth(row.name) + body.getWidth(row.score) + 8 <= frame.width - 16 + .001);
            }
            assertTrue(frame.lineHeight >= body.getLineHeight());
            assertTrue(8 + frame.headerHeight + 15 * frame.lineHeight <= frame.height - 8 + .001);
        }
    }

    @Test public void hiddenNumbersDoNotReserveAColumnAndEmptyObjectivesKeepTheirHeading() {
        CustomScoreboard custom = new CustomScoreboard();
        ScoreObjective objective = objective();
        custom.getBooleanSetting("Show Scores").setValue(false);
        for (CustomScoreboard.Row row : custom.layout(objective, 320, 240, body, title).rows) assertEquals("", row.score);
        ScoreObjective empty = new Scoreboard().addScoreObjective("empty", IScoreObjectiveCriteria.DUMMY);
        empty.setDisplayName("Empty sidebar");
        CustomScoreboard.Frame frame = custom.layout(empty, 320, 240, body, title);
        assertEquals("Empty sidebar", frame.title);
        assertTrue(frame.rows.isEmpty());
        assertTrue(frame.height > title.getLineHeight());
    }

    @Test public void automaticSidebarKeepsHotbarAndNativeStatusRowsClearAtEveryModuleScale() {
        CustomScoreboard custom = new CustomScoreboard();
        for (int[] size : new int[][]{{320, 240}, {550, 320}, {334, 251}, {960, 540}}) {
            CustomHotbar.Layout hotbar = CustomHotbar.layout(size[0], size[1]);
            for (double scale : new double[]{.65, 1, 1.6}) {
                custom.getNumberSetting("Scale").setValue(scale);
                CustomScoreboard.Frame frame = custom.layout(objective(), size[0], size[1], body, title);
                assertEquals(15, frame.rows.size());
                assertTrue("Automatic sidebar covers the hotbar at " + size[0] + "x" + size[1] + "/" + scale,
                        frame.y + frame.screenHeight() <= hotbar.y - 6 + .001);
                assertTrue("Automatic sidebar covers the native armor/air row",
                        frame.y + frame.screenHeight() <= size[1] - 49 - 6 + .001);
                assertTrue(frame.x >= 0 && frame.y >= 0);
                assertTrue(frame.x + frame.screenWidth() <= size[0] + .001);
            }
        }
    }

    @Test public void manualSidebarStillUsesTheFullScreenAndResetRestoresAutomaticClearance() {
        CustomScoreboard custom = new CustomScoreboard();
        custom.getBooleanSetting("Custom Position").setValue(true);
        custom.getNumberSetting("Position X").setValue(20D);
        custom.getNumberSetting("Position Y").setValue(230D);
        custom.getNumberSetting("Scale").setValue(1.6D);
        CustomScoreboard.Frame frame = custom.layout(objective(), 320, 240, body, title);
        assertTrue("A manually positioned sidebar must retain access to the lower screen",
                frame.y + frame.screenHeight() > 240 - 49 - 6);
        assertTrue(frame.y >= 0 && frame.y + frame.screenHeight() <= 240.001);
        assertTrue(frame.x >= 0 && frame.x + frame.screenWidth() <= 320.001);
        custom.resetPosition();
        frame = custom.layout(objective(), 320, 240, body, title);
        assertTrue(frame.y + frame.screenHeight() <= CustomHotbar.layout(320, 240).y - 6 + .001);
    }

    @Test public void completeNumericScoresKeepTheirWidthBeforeLongNamesAreTruncated() {
        CustomScoreboard custom = new CustomScoreboard();
        Scoreboard board = new Scoreboard();
        ScoreObjective objective = board.addScoreObjective("numbers", IScoreObjectiveCriteria.DUMMY);
        board.getValueFromObjective("WWWW", objective).setScorePoints(Integer.MIN_VALUE);
        CustomScoreboard.Frame frame = custom.layout(objective, 320, 240, body, title);
        assertEquals(Integer.toString(Integer.MIN_VALUE), frame.rows.get(0).score);
        assertEquals("WWWW", frame.rows.get(0).name);
    }

    @Test public void dragUsesTheDisplayedBoundsPreservesGrabOffsetAndPersistsPosition() {
        CustomScoreboard custom = new CustomScoreboard();
        CustomScoreboard.Frame frame = custom.layout(null, 550, 320, body, title);
        float startX = frame.x, startY = frame.y;
        HudDrag.context(true, false);
        custom.interact(true, false, startX + 10, startY + 10, frame, 550, 320);
        HudDrag.context(true, true);
        custom.interact(true, true, startX + 10, startY + 10, frame, 550, 320);
        custom.interact(true, true, startX - 35, startY + 30, frame, 550, 320);
        assertEquals(startX - 45, frame.x, .001);
        assertEquals(startY + 20, frame.y, .001);
        assertTrue(custom.getBooleanSetting("Custom Position").getValue());
        assertEquals(frame.x, custom.getNumberSetting("Position X").getValue(), .001);
        assertEquals(frame.y, custom.getNumberSetting("Position Y").getValue(), .001);
        assertFalse(HudDrag.capture(new Object(), true, true, true, true));
        custom.interact(true, true, 10000, 10000, frame, 550, 320);
        assertTrue(frame.x + frame.screenWidth() <= 546.001);
        assertTrue(frame.y + frame.screenHeight() <= 316.001);
        custom.resetPosition();
        assertFalse(custom.getBooleanSetting("Custom Position").getValue());
    }

    @Test public void disabledDesignsLeaveTheirNativeCallerInCharge() {
        assertFalse(new CustomScoreboard().render(null, null));
        assertFalse(new CustomHotbar().render(null, 0, null));
    }
}
