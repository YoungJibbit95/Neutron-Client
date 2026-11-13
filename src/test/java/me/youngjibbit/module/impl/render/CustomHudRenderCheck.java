package me.youngjibbit.module.impl.render;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.utils.render.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScoreObjective;
import org.lwjgl.opengl.EXTFramebufferObject;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/** Actual GuiIngame routing, real fonts/items and both native fallback skins. */
final class CustomHudRenderCheck {
    static void run() throws Exception {
        Minecraft mc = IngameHudRenderCheck.mc;
        CustomHotbar hotbar = (CustomHotbar) IngameHudRenderCheck.modules.getModule(CustomHotbar.class);
        CustomScoreboard scoreboard = (CustomScoreboard) IngameHudRenderCheck.modules.getModule(CustomScoreboard.class);
        ClientSettings settings = ClientSettings.current();
        HUD hud = HUD.current();
        boolean oldHotbar = hotbar.isToggled(), oldScoreboard = scoreboard.isToggled();
        boolean oldPosition = scoreboard.getBooleanSetting("Custom Position").getValue();
        double oldScoreboardScale = scoreboard.getNumberSetting("Scale").getValue();
        boolean oldVisible = hud.visible.getValue(), oldReduced = settings.reducedMotion.getValue();
        boolean oldAnimations = settings.animations.getValue(), oldDiagnostics = RenderDiagnostics.enabled;
        int width = mc.displayWidth, height = mc.displayHeight, guiScale = mc.gameSettings.guiScale;
        int oldSlot = mc.thePlayer.inventory.currentItem;
        GuiScreen screen = mc.currentScreen;
        Method hotbarRoute = GuiIngame.class.getDeclaredMethod("renderTooltip", ScaledResolution.class, float.class);
        Method scoreboardRoute = GuiIngame.class.getDeclaredMethod("renderScoreboard", ScoreObjective.class, ScaledResolution.class);
        Method nativeScoreboard = GuiIngame.class.getDeclaredMethod("renderVanillaScoreboard", ScoreObjective.class, ScaledResolution.class);
        hotbarRoute.setAccessible(true);
        scoreboardRoute.setAccessible(true);
        nativeScoreboard.setAccessible(true);
        ScoreObjective objective = CustomScoreboardTest.objective();
        List<BufferedImage> pictures = new ArrayList<>();
        List<String> captions = new ArrayList<>();
        List<BufferedImage> clearances = new ArrayList<>();
        List<String> clearanceCaptions = new ArrayList<>();
        try {
            mc.currentScreen = null;
            hud.visible.setValue(true);
            settings.animations.setValue(true);
            settings.reducedMotion.setValue(true);
            hotbar.setToggled(true);
            scoreboard.setToggled(true);
            scoreboard.getBooleanSetting("Custom Position").setValue(false);
            scoreboard.getNumberSetting("Scale").setValue(1D);
            for (int[] viewport : new int[][]{{640, 480, 2}, {1100, 640, 2}, {960, 540, 1}, {1001, 751, 3}}) {
                mc.displayWidth = viewport[0];
                mc.displayHeight = viewport[1];
                mc.gameSettings.guiScale = viewport[2];
                mc.getFramebuffer().createBindFramebuffer(mc.displayWidth, mc.displayHeight);
                ScaledResolution resolution = new ScaledResolution(mc);
                mc.thePlayer.inventory.currentItem = (pictures.size() / 2) % 2 == 0 ? 0 : 8;
                SessionInfoRenderCheck.backdrop();
                BufferedImage empty = SessionInfoRenderCheck.capture();
                hotbarRoute.invoke(IngameHudRenderCheck.overlay, resolution, 0f);
                BufferedImage unobstructedHotbar = SessionInfoRenderCheck.capture();
                scoreboardRoute.invoke(IngameHudRenderCheck.overlay, objective, resolution);
                BufferedImage custom = SessionInfoRenderCheck.capture();
                require(!WatermarkCheck.equal(empty, custom), "Custom scoreboard/hotbar did not draw");
                checkClearance(scoreboard, objective, resolution, unobstructedHotbar, custom);
                pictures.add(custom);
                captions.add("Custom / " + resolution.getScaledWidth() + "x" + resolution.getScaledHeight()
                        + " / GUI " + resolution.getScaleFactor() + " / long formatted title + 15 rows");
                clearances.add(custom);
                clearanceCaptions.add("Default scale 1 / " + resolution.getScaledWidth() + "x" + resolution.getScaledHeight());
                for (double requestedScale : new double[]{.65, 1.6}) {
                    scoreboard.getNumberSetting("Scale").setValue(requestedScale);
                    SessionInfoRenderCheck.backdrop();
                    hotbarRoute.invoke(IngameHudRenderCheck.overlay, resolution, 0f);
                    BufferedImage hotbarOnly = SessionInfoRenderCheck.capture();
                    scoreboardRoute.invoke(IngameHudRenderCheck.overlay, objective, resolution);
                    BufferedImage combined = SessionInfoRenderCheck.capture();
                    checkClearance(scoreboard, objective, resolution, hotbarOnly, combined);
                    clearances.add(combined);
                    clearanceCaptions.add("Scoreboard scale " + requestedScale + " / "
                            + resolution.getScaledWidth() + "x" + resolution.getScaledHeight() + " / hotbar pixels unchanged");
                }
                scoreboard.getNumberSetting("Scale").setValue(1D);

                scoreboard.setToggled(false);
                SessionInfoRenderCheck.backdrop();
                scoreboardRoute.invoke(IngameHudRenderCheck.overlay, objective, resolution);
                BufferedImage routedNative = SessionInfoRenderCheck.capture();
                SessionInfoRenderCheck.backdrop();
                nativeScoreboard.invoke(IngameHudRenderCheck.overlay, objective, resolution);
                require(WatermarkCheck.equal(routedNative, SessionInfoRenderCheck.capture()),
                        "Disabled custom scoreboard did not use exactly the native renderer");
                hotbar.setToggled(false);
                SessionInfoRenderCheck.backdrop();
                hotbarRoute.invoke(IngameHudRenderCheck.overlay, resolution, 0f);
                scoreboardRoute.invoke(IngameHudRenderCheck.overlay, objective, resolution);
                BufferedImage vanilla = SessionInfoRenderCheck.capture();
                require(!WatermarkCheck.equal(empty, vanilla) && !WatermarkCheck.equal(custom, vanilla),
                        "Module-off skin must remain visible and switch to vanilla");
                pictures.add(vanilla);
                captions.add("Vanilla fallback / GUI " + resolution.getScaleFactor() + " / both design modules off");
                hotbar.setToggled(true);
                scoreboard.setToggled(true);
            }

            ScaledResolution resolution = new ScaledResolution(mc);
            settings.reducedMotion.setValue(false);
            mc.thePlayer.inventory.currentItem = 0;
            hotbar.setToggled(false);
            hotbar.setToggled(true);
            SessionInfoRenderCheck.backdrop();
            hotbarRoute.invoke(IngameHudRenderCheck.overlay, resolution, 0f);
            mc.thePlayer.inventory.currentItem = 8;
            SessionInfoRenderCheck.set(hotbar, "lastFrame", System.nanoTime() - 25_000_000L);
            SessionInfoRenderCheck.backdrop();
            hotbarRoute.invoke(IngameHudRenderCheck.overlay, resolution, 0f);
            HotbarMotion motion = (HotbarMotion) SessionInfoRenderCheck.get(hotbar, "motion");
            require(motion.position() > 0 && motion.position() < 8 && mc.thePlayer.inventory.currentItem == 8,
                    "Smooth cursor must interpolate while the actual selected slot changes immediately");
            pictures.add(SessionInfoRenderCheck.capture());
            captions.add("Render-time cursor travel / native selected slot already 8");

            mc.currentScreen = new GuiChat();
            SessionInfoRenderCheck.backdrop();
            require(scoreboard.render(null, resolution), "Missing sidebar should have an editor preview");
            pictures.add(SessionInfoRenderCheck.capture());
            captions.add("Chat editor preview / no active server scoreboard");
            mc.currentScreen = null;
            settings.reducedMotion.setValue(true);
            for (int i = 0; i < 5; i++) {
                SessionInfoRenderCheck.backdrop();
                hotbarRoute.invoke(IngameHudRenderCheck.overlay, resolution, 0f);
                scoreboardRoute.invoke(IngameHudRenderCheck.overlay, objective, resolution);
            }
            RenderDiagnostics.enabled = true;
            RenderDiagnostics.reset();
            SessionInfoRenderCheck.backdrop();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            int framebuffer = glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
            hotbarRoute.invoke(IngameHudRenderCheck.overlay, resolution, 0f);
            scoreboardRoute.invoke(IngameHudRenderCheck.overlay, objective, resolution);
            require(glIsEnabled(GL_DEPTH_TEST) && !glGetBoolean(GL_DEPTH_WRITEMASK)
                            && framebuffer == glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),
                    "Custom HUDs changed the caller's depth state or framebuffer");
            require(RenderDiagnostics.framebufferCreates == 0 && RenderDiagnostics.programCreates == 0,
                    "Warm custom HUD frames allocated GPU programs/framebuffers");
            SessionInfoRenderCheck.sheet("release-custom-hud-resize.png", captions, pictures, true);
            SessionInfoRenderCheck.sheet("release-custom-hud-clearance.png", clearanceCaptions, clearances, true);
            require(glGetError() == GL_NO_ERROR, "Custom HUD regression left an OpenGL error");
            System.out.println("PASS: font-measured scoreboard + 15 filtered rows; real native fallback routing; "
                    + "hotbar icons/counts and smooth selection; GUI scales 1/2/3; chat preview; reduced motion; "
                    + "automatic sidebar clears native status rows and preserves every hotbar pixel at scales .65/1/1.6; "
                    + "incoming GL/FBO restoration and warm GPU reuse");
        } finally {
            RenderDiagnostics.enabled = oldDiagnostics;
            scoreboard.getNumberSetting("Scale").setValue(oldScoreboardScale);
            scoreboard.getBooleanSetting("Custom Position").setValue(oldPosition);
            hotbar.setToggled(oldHotbar);
            scoreboard.setToggled(oldScoreboard);
            settings.reducedMotion.setValue(oldReduced);
            settings.animations.setValue(oldAnimations);
            hud.visible.setValue(oldVisible);
            mc.thePlayer.inventory.currentItem = oldSlot;
            mc.currentScreen = screen;
            mc.displayWidth = width;
            mc.displayHeight = height;
            mc.gameSettings.guiScale = guiScale;
            mc.getFramebuffer().createBindFramebuffer(width, height);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(true);
        }
    }

    private static void checkClearance(CustomScoreboard scoreboard, ScoreObjective objective,
                                       ScaledResolution resolution, BufferedImage hotbarOnly, BufferedImage combined) {
        Minecraft mc = IngameHudRenderCheck.mc;
        int width = resolution.getScaledWidth(), height = resolution.getScaledHeight();
        CustomScoreboard.Frame card = scoreboard.layout(objective, width, height,
                me.youngjibbit.Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 20"),
                me.youngjibbit.Pulsar.INSTANCE.getFontManager().getFont("Poppins-Medium 20"));
        CustomHotbar.Layout bar = CustomHotbar.layout(width, height);
        require(card.rows.size() == 15 && card.y + card.screenHeight() <= Math.min(bar.y, height - 49) - 6 + .001,
                "Automatic scoreboard overlaps the native status/hotbar area at " + width + "x" + height);
        int scale = resolution.getScaleFactor();
        int left = Math.max(0, (int) Math.ceil(bar.x * scale));
        int top = Math.max(0, (int) Math.ceil(bar.y * scale));
        int right = Math.min(mc.displayWidth, (int) Math.floor((bar.x + bar.width) * scale));
        int bottom = Math.min(mc.displayHeight, (int) Math.floor((bar.y + bar.height) * scale));
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++)
            require(hotbarOnly.getRGB(x, y) == combined.getRGB(x, y),
                    "Scoreboard altered a hotbar/item pixel at " + x + "," + y);
    }

    private static void require(boolean value, String message) {
        SessionInfoRenderCheck.require(value, message);
    }
}
