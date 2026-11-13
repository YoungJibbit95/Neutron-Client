package me.youngjibbit.utils.render;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.world.BedObservation;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/** Real production Bed world/HUD rendering in a separate Java 8 Pbuffer/FBO process. */
public final class BedVisualsNativeCheck {
    private static final File OUT = new File("bed-visuals-preview");
    private static final JsonObject REPORT = new JsonObject();
    private static Minecraft mc;
    private static ClientSettings settings;
    private static FontManager fonts;
    private static final BedEspRenderer ESP = new BedEspRenderer();
    private static final BedHudRenderer HUD = new BedHudRenderer();
    private static final AxisAlignedBB BED = new AxisAlignedBB(-.5, 0, -1, .5, .5625, 1);

    private BedVisualsNativeCheck() { }

    public static void main(String[] args) throws Exception {
        OUT.mkdirs();
        File data = Files.createTempDirectory(OUT.toPath(), "fixture-").toFile();
        System.setProperty("user.home", new File(data, "home").getAbsolutePath());
        try (PbufferTestContext context = new PbufferTestContext()) {
            initialize(context, data);
            try {
                worldGeometry();
                worldClippingAndState();
                progressPixels();
                widgetsAndPresentation();
                hudClippingAndState();
                colorMaskPixels();
                warmResources();
                context.check();
            } finally {
                ESP.close(); HUD.close(); releaseShared();
                if (fonts != null) fonts.close();
                mc.getFramebuffer().deleteFramebuffer();
            }
            require(RenderDiagnostics.framebufferCreates == RenderDiagnostics.framebufferDeletes, "Bed visual framebuffer leak");
            require(RenderDiagnostics.programCreates == RenderDiagnostics.programDeletes, "Bed visual program leak");
            require(RenderDiagnostics.shaderCompiles == RenderDiagnostics.shaderDeletes, "Bed visual shader leak");
            require(RenderDiagnostics.glyphTexturesCreated == RenderDiagnostics.glyphTexturesDeleted, "Bed font texture leak");
            require(glGetError() == GL_NO_ERROR, "Bed visual cleanup OpenGL error");
            REPORT.addProperty("framebuffer_creates", RenderDiagnostics.framebufferCreates);
            REPORT.addProperty("framebuffer_deletes", RenderDiagnostics.framebufferDeletes);
            REPORT.addProperty("program_creates", RenderDiagnostics.programCreates);
            REPORT.addProperty("program_deletes", RenderDiagnostics.programDeletes);
            REPORT.addProperty("status", "passed");
            report(); context.check();
        } catch (Throwable error) {
            REPORT.addProperty("status", "failed"); REPORT.addProperty("error", error.toString()); report(); throw error;
        }
        System.out.println("PASS: Bed ESP geometry/contours and real glass/gradient HUD pixels, clipping/state, warm resources and disposal.");
    }

    private static BedHudRenderer.Progress progress(float value, boolean estimated) {
        return new BedHudRenderer.Progress("Breaking", "End Stone / current tool", "~2.4 s", value, estimated);
    }

    private static BufferedImage paintProgress(float value, boolean estimated) {
        hud(); HUD.paintProgress(progress(value, estimated), 18, 22, 1, 1, Theme.SynthWave);
        return capture();
    }

    private static void progressPixels() throws Exception {
        resize(960, 640, 2);
        settings.glow.setValue(true); settings.reducedMotion.setValue(true); UiEffects.invalidate();
        paintProgress(0, true); // Warm real fonts and surfaces before comparing their pixels.
        BufferedImage zero = paintProgress(0, true);
        BufferedImage half = paintProgress(.5f, true);
        BufferedImage full = paintProgress(1, true);
        BufferedImage tiny = paintProgress(.008f, true);
        BufferedImage unknown = paintProgress(0, false);
        save("hud-progress-zero.png", zero); save("hud-progress-half.png", half);
        save("hud-progress-full.png", full); save("hud-progress-tiny.png", tiny); save("hud-eta-unknown.png", unknown);
        int scale = new ScaledResolution(mc).getScaleFactor();
        int left = 30 * scale, top = 66 * scale, right = 214 * scale, bottom = 71 * scale;
        int mid = (left + right) / 2;
        require(regionDifferences(zero, half, left + 3, top + 2, mid - 3, bottom - 2) > 200, "Half progress did not fill the first half of the real bar");
        require(regionDifferences(zero, half, mid + 8, top + 2, right - 3, bottom - 2) == 0, "Half progress filled beyond its remaining track");
        require(regionDifferences(zero, full, mid + 8, top + 2, right - 3, bottom - 2) > 200, "Full progress did not reach the second half of the real bar");
        int tinyPixels = differences(zero, tiny);
        require(tinyPixels > 0 && tinyPixels < 350, "Tiny rounded fill escaped its small footprint: " + tinyPixels);
        require(regionDifferences(zero, unknown, 144 * scale, 46 * scale, 218 * scale, 64 * scale) > 15,
                "Unknown estimate did not replace the numeric time in the production HUD");
        int a = full.getRGB(left + 20, top + 6), b = full.getRGB(right - 20, top + 6);
        require(rgbDistance(a, b) > 20, "The progress fill lost its horizontal color gradient");
        long blur = RenderDiagnostics.blurPasses;
        hud(); BufferedImage backdrop = capture();
        HUD.paintProgress(progress(.5f, true), 18, 22, 1, 1, Theme.SynthWave);
        require(RenderDiagnostics.blurPasses > blur, "Glass panel did not execute a real scene-blur pass");
        require(regionDifferences(backdrop, capture(), 40 * scale, 58 * scale, 180 * scale, 65 * scale) > 100,
                "Glass panel did not affect its structured source scene");
        REPORT.addProperty("progress_tiny_pixels", tinyPixels);
        require(glGetError() == GL_NO_ERROR, "Progress panel OpenGL error");
        System.out.println("PASS: real glass scene blur and gradient rail at zero/half/full/tiny progress, plus unknown estimate");
    }

    private static BedObservation observedBed(boolean partial) {
        Map<BlockPos, IBlockState> blocks = new HashMap<>();
        BlockPos foot = new BlockPos(0, 0, 0), head = foot.north();
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 2; y++) for (int z = -3; z <= 2; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (y == -1) { blocks.put(pos, Blocks.stone.getDefaultState()); continue; }
            int distance = Math.min(Math.abs(x) + y + Math.abs(z), Math.abs(x) + y + Math.abs(z + 1));
            if (distance == 1) blocks.put(pos, y == 1 ? Blocks.end_stone.getDefaultState()
                    : Blocks.wool.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.RED));
            if (distance == 2) blocks.put(pos, Blocks.planks.getDefaultState());
        }
        IBlockState bed = Blocks.bed.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.NORTH);
        blocks.put(foot, bed.withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT));
        blocks.put(head, bed.withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD));
        blocks.put(head.north(), Blocks.air.getDefaultState());
        if (partial) blocks.put(head.west(), Blocks.wool.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.BLUE));
        BedObservation result = BedObservation.capture(new BedObservation.Access() {
            @Override public IBlockState stateIfLoaded(BlockPos pos) {
                return partial && pos.getX() == 2 && pos.getY() >= 0 ? null : blocks.getOrDefault(pos, Blocks.air.getDefaultState());
            }
            @Override public boolean current() { return true; }
        }, foot, 10);
        require(result != null && result.completeBed(), "Native fixture did not observe both real bed states");
        return result;
    }

    private static List<BedHudRenderer.PlacedWidget> widgets() {
        BedObservation known = observedBed(false), partial = observedBed(true);
        require(known.team.kind == BedObservation.TeamHint.Kind.COLOR_HEURISTIC, "Single-color source lost its explicitly uncertain team hint");
        require(partial.team.kind == BedObservation.TeamHint.Kind.UNKNOWN && partial.outer.unknown > 0,
                "Partly loaded/mixed-color fixture must remain unknown");
        BedHudRenderer.Widget first = BedHudRenderer.Widget.from(known, 7.5, true, true, true);
        BedHudRenderer.Widget second = BedHudRenderer.Widget.from(partial, 16, true, true, true);
        BedHudRenderer.Widget compact = BedHudRenderer.Widget.from(known, 12, false, false, false);
        require(first.height() == 62 && compact.height() == 25, "Compact widget height does not follow the visible rows");
        return Arrays.asList(new BedHudRenderer.PlacedWidget(first, 18, 105, 1, 1),
                new BedHudRenderer.PlacedWidget(second, 200, 105, 1, 1),
                new BedHudRenderer.PlacedWidget(compact, 18, 185, 1, 1));
    }

    private static void widgetsAndPresentation() throws Exception {
        List<BedHudRenderer.PlacedWidget> cards = widgets();
        hud(); BufferedImage backdrop = capture();
        HUD.paintProgress(progress(.5f, true), 18, 22, 1, 1, Theme.SynthWave); HUD.paintWidgets(cards, Theme.SynthWave);
        BufferedImage image = capture(); save("hud-widgets-layers-team-open.png", image);
        require(differences(backdrop, image) > 3000, "Observed bed layers/team/open cards are not visibly rendered");
        require(UiEffects.scene() == null, "Bed HUD left a live surface scope");
        // Disable per-card halos so this counts only the shared real scene blur.
        settings.glow.setValue(false); UiEffects.invalidate();
        hud(); long before = RenderDiagnostics.blurPasses;
        HUD.paintWidgets(Collections.singletonList(cards.get(0)), Theme.SynthWave);
        long one = RenderDiagnostics.blurPasses - before;
        hud(); before = RenderDiagnostics.blurPasses; HUD.paintWidgets(cards, Theme.SynthWave);
        require(one > 0 && RenderDiagnostics.blurPasses - before == one, "Multiple bed cards repeated the shared scene blur per widget");
        settings.glow.setValue(false); UiEffects.invalidate();
        hud(); HUD.paintProgress(progress(.5f, true), 18, 22, 1, 1, Theme.SynthWave); HUD.paintWidgets(cards, Theme.SynthWave);
        BufferedImage noGlow = capture(); save("hud-global-glow-off.png", noGlow);
        require(differences(image, noGlow) > 20, "Global Glow off had no visible effect on the bed HUD");
        settings.glow.setValue(true); UiEffects.invalidate(); Theme.setDefaultTheme(Theme.Neutron);
        hud(); HUD.paintProgress(progress(.5f, true), 18, 22, 1, 1, Theme.Neutron); HUD.paintWidgets(cards, Theme.Neutron);
        BufferedImage otherTheme = capture(); save("hud-neutron-theme.png", otherTheme);
        require(differences(image, otherTheme) > 100, "Bed glass/gradient widgets ignored the selected theme");
        Theme.setDefaultTheme(Theme.SynthWave); UiEffects.invalidate();
        for (int[] size : new int[][]{{800, 600, 1}, {960, 640, 2}, {1065, 795, 3}}) {
            resize(size[0], size[1], size[2]); hud(); BufferedImage base = capture();
            BedHudRenderer.Progress longText = new BedHudRenderer.Progress("Preparing a deliberately long phase label",
                    "Very long observed defense material description", "~10m+", .5f, true);
            HUD.paintProgress(longText, 12, 18, .82f, 1, Theme.SynthWave);
            BedHudRenderer.Widget longCard = new BedHudRenderer.Widget("A deliberately long unverified team name?",
                    "Open: 5 observed sides", "End Stone x12 / Red Wool x10 / Oak Planks x9",
                    "Obsidian x17 / Mixed unknown material labels ?", "12345m", 0xffee6060, true, true, true, true);
            HUD.paintWidgets(Collections.singletonList(new BedHudRenderer.PlacedWidget(longCard, 195, 18, .82f, 1)), Theme.SynthWave);
            require(differences(base, capture()) > 100, "Narrow/GUI-scale bed HUD is invisible");
            require(glGetError() == GL_NO_ERROR && UiEffects.scene() == null, "Bed HUD resize leaked GL/surface state");
            save("hud-scale-" + size[2] + "-narrow.png", capture());
        }
        resize(960, 640, 2); settings.reducedMotion.setValue(true); UiEffects.invalidate();
        BufferedImage reduced = paintProgress(.5f, true);
        require(differences(reduced, paintProgress(.5f, true)) == 0, "Reduced-motion/static bed presentation drifted between identical frames");
        save("hud-reduced-motion.png", reduced);
        settings.reducedMotion.setValue(false); UiEffects.invalidate(); paintProgress(.5f, true);
        require(glGetError() == GL_NO_ERROR && UiEffects.scene() == null, "Regular-motion bed presentation leaked state");
        settings.reducedMotion.setValue(true); UiEffects.invalidate();
        REPORT.addProperty("widget_scene_blur_passes_per_list", one); REPORT.addProperty("gui_scales", "1,2,3");
        System.out.println("PASS: observed two-layer/team/open/unknown compact cards, one scene blur per list, narrow text, scales, theme, reduced motion and global glow off");
    }

    private static void hudClippingAndState() throws Exception {
        hud(); BufferedImage background = capture();
        glEnable(GL_SCISSOR_TEST); glScissor(0, 0, mc.displayWidth / 2, mc.displayHeight);
        State clipped = new State();
        HUD.paintProgress(progress(.5f, true), 118, 22, 1, 1, Theme.SynthWave);
        HUD.paintWidgets(Collections.singletonList(new BedHudRenderer.PlacedWidget(
                BedHudRenderer.Widget.from(observedBed(false), 8, true, true, true), 118, 110, 1, 1)), Theme.SynthWave);
        clipped.requireSame("Bed HUD clip"); BufferedImage image = capture();
        require(differences(background, image) > 50, "Scissored bed HUD is invisible");
        for (int y = 0; y < mc.displayHeight; y++) for (int x = mc.displayWidth / 2; x < mc.displayWidth; x++)
            require(background.getRGB(x, y) == image.getRGB(x, y), "Bed HUD escaped inherited clipping at " + x + "," + y);
        save("hud-clipped.png", image);
        hud();
        try (ShaderUtil sentinel = new ShaderUtil("#version 120\nvoid main(){gl_FragColor=vec4(.2,.4,.7,1.0);}", true);
             RenderState restore = new RenderState()) {
            poisonState(); sentinel.init(); State before = new State();
            HUD.paintProgress(progress(.5f, true), 18, 22, 1, 1, Theme.SynthWave); before.requireSame("Bed progress HUD");
            HUD.paintWidgets(widgets(), Theme.SynthWave); before.requireSame("Bed widget HUD");
        }
        hud(); BufferedImage empty = capture();
        HUD.paintProgress(null, 18, 22, 1, 1, Theme.SynthWave);
        HUD.paintProgress(progress(.5f, true), Float.NaN, 22, 1, 1, Theme.SynthWave);
        HUD.paintProgress(progress(.5f, true), 18, 22, 1, 0, Theme.SynthWave);
        HUD.paintWidgets(Collections.emptyList(), Theme.SynthWave);
        require(differences(empty, capture()) == 0, "Cleared/invalid/faded bed HUD left stale pixels");
        require(glGetError() == GL_NO_ERROR, "Bed HUD state OpenGL error");
        System.out.println("PASS: Bed HUD inherited clip pixels, complete GL/FBO/matrix restore, empty/invalid/faded frames");
    }

    private static void warmFrame(List<BedHudRenderer.PlacedWidget> cards) {
        world(); ESP.render(entries(BED), 0, 0, 0, new BedEspRenderer.Style());
        hud(); HUD.paintProgress(progress(.5f, true), 18, 22, 1, 1, Theme.SynthWave); HUD.paintWidgets(cards, Theme.SynthWave);
    }

    private static void colorMaskPixels() throws Exception {
        resize(960, 640, 2);
        for (boolean red : new boolean[]{true, false}) {
            String eye = red ? "red" : "cyan";
            int blocked = red ? 0x00ffff : 0xff0000;
            world(); BufferedImage background = capture();
            GlStateManager.colorMask(red, !red, !red, true); glColorMask(red, !red, !red, true);
            State before = new State();
            ESP.render(entries(BED), 0, 0, 0, new BedEspRenderer.Style());
            before.requireSame("Bed ESP " + eye + " channel mask");
            BufferedImage image = capture(); requireChannels(background, image, blocked, "Bed ESP " + eye);
            save("world-" + eye + "-only.png", image);
            hud(); background = capture();
            GlStateManager.colorMask(red, !red, !red, true); glColorMask(red, !red, !red, true);
            before = new State();
            HUD.paintProgress(progress(.5f, true), 18, 22, 1, 1, Theme.SynthWave);
            HUD.paintWidgets(widgets(), Theme.SynthWave);
            before.requireSame("Bed HUD " + eye + " channel mask");
            image = capture(); requireChannels(background, image, blocked, "Bed HUD " + eye);
            save("hud-" + eye + "-only.png", image);
        }
        require(glGetError() == GL_NO_ERROR, "Bed eye/channel composition OpenGL error");
        REPORT.addProperty("channel_masks", "red-only,cyan-only");
        System.out.println("PASS: world contours and glass/widgets preserve every blocked red/cyan eye channel pixel");
    }

    private static void requireChannels(BufferedImage background, BufferedImage output, int blocked, String label) {
        int changed = 0;
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) {
            int delta = background.getRGB(x, y) ^ output.getRGB(x, y);
            require((delta & blocked) == 0, label + " overwrote a blocked eye channel at " + x + "," + y);
            if ((delta & 0xffffff) != 0) changed++;
        }
        require(changed > 100, label + " did not draw into its permitted eye channels");
    }

    private static void warmResources() {
        resize(960, 640, 2); List<BedHudRenderer.PlacedWidget> cards = widgets();
        warmFrame(cards); warmFrame(cards);
        long buffers = RenderDiagnostics.framebufferCreates, programs = RenderDiagnostics.programCreates;
        for (int i = 0; i < 32; i++) warmFrame(cards);
        require(RenderDiagnostics.framebufferCreates == buffers, "Warm bed rendering allocates new framebuffers");
        require(RenderDiagnostics.programCreates == programs, "Warm bed rendering recompiles shader programs");
        require(glGetError() == GL_NO_ERROR, "Warm bed rendering OpenGL error");
        REPORT.addProperty("warm_frames", 32);
        System.out.println("PASS: 32 warm world/HUD frames reuse existing framebuffers and shader programs");
    }

    private static int regionDifferences(BufferedImage first, BufferedImage second, int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++)
            if ((first.getRGB(x, y) & 0xffffff) != (second.getRGB(x, y) & 0xffffff)) count++;
        return count;
    }

    private static int rgbDistance(int first, int second) {
        return Math.abs((first >> 16 & 255) - (second >> 16 & 255))
                + Math.abs((first >> 8 & 255) - (second >> 8 & 255)) + Math.abs((first & 255) - (second & 255));
    }

    private static List<BedEspRenderer.Entry> entries(AxisAlignedBB bounds) {
        return Collections.singletonList(new BedEspRenderer.Entry(bounds,
                Theme.getDefaultTheme().getPrimaryGradientRGB(), Theme.getDefaultTheme().getSecondaryGradientRGB()));
    }

    private static void worldGeometry() throws Exception {
        resize(960, 640, 2);
        BedEspRenderer.Style style = new BedEspRenderer.Style();
        BufferedImage outline = null, glow = null;
        for (String mode : new String[]{"Outline", "Glow", "Flame", "Lines"}) {
            style.mode = mode;
            world(); BufferedImage background = capture();
            long passes = RenderDiagnostics.maskPasses, composites = RenderDiagnostics.composites;
            ESP.render(entries(BED), 0, 0, 0, style);
            BufferedImage image = capture();
            int pixels = differences(background, image);
            require(pixels > 150 && pixels < mc.displayWidth * mc.displayHeight / 2, mode + " did not draw bounded bed geometry");
            if (!"Outline".equals(mode)) {
                require(RenderDiagnostics.maskPasses > passes && RenderDiagnostics.composites > composites,
                        mode + " silently fell back instead of exercising the real contour shaders");
            }
            require(glGetError() == GL_NO_ERROR, mode + " OpenGL error");
            save("world-" + mode.toLowerCase(java.util.Locale.ROOT) + ".png", image);
            REPORT.addProperty("world_" + mode.toLowerCase(java.util.Locale.ROOT) + "_pixels", pixels);
            if ("Outline".equals(mode)) outline = image;
            if ("Glow".equals(mode)) glow = image;
        }
        require(differences(outline, glow) > 50, "Glow is only an alias of the geometric outline");
        style.mode = "Glow";
        settings.glow.setValue(false); UiEffects.invalidate();
        world(); ESP.render(entries(BED), 0, 0, 0, style);
        require(differences(outline, capture()) == 0, "Global Glow off does not retain exactly the ordinary bed geometry");
        settings.glow.setValue(true); UiEffects.invalidate();
        style.mode = "Outline";
        world(); ESP.render(entries(BED), 0, 0, 0, style); BufferedImage origin = capture();
        world(); ESP.render(entries(BED.offset(10000, 64, -9000)), 10000, 64, -9000, style);
        require(differences(origin, capture()) == 0, "Bed world-to-camera subtraction shifted the same geometry");
        world(); ESP.render(entries(new AxisAlignedBB(-1, 0, -.5, 1, .5625, .5)), 0, 0, 0, style);
        BufferedImage turned = capture(); save("world-east-west.png", turned);
        require(differences(origin, turned) > 150, "The two-block bed footprint ignores its actual orientation");
        world(); ESP.render(Arrays.asList(new BedEspRenderer.Entry(BED, 0xffff4545, 0xffffc060),
                new BedEspRenderer.Entry(BED.offset(1.4, .1, .8), 0xff45aaff, 0xff50ffd0)), 0, 0, 0, style);
        BufferedImage multiple = capture(); save("world-multiple-beds.png", multiple);
        require(differences(origin, multiple) > 150, "Multiple beds/colors did not reach the production world draw");
        System.out.println("PASS: exact two-block world bounds, four contour modes, global glow off, orientation and distant camera translation");
    }

    private static void worldClippingAndState() throws Exception {
        BedEspRenderer.Style style = new BedEspRenderer.Style();
        world(); BufferedImage background = capture();
        glEnable(GL_SCISSOR_TEST); glScissor(0, 0, mc.displayWidth / 2, mc.displayHeight);
        State clipped = new State(); ESP.render(entries(BED), 0, 0, 0, style); clipped.requireSame("Bed ESP clip");
        BufferedImage image = capture();
        require(differences(background, image) > 50, "Scissored world bed is invisible");
        for (int y = 0; y < mc.displayHeight; y++) for (int x = mc.displayWidth / 2; x < mc.displayWidth; x++)
            require(background.getRGB(x, y) == image.getRGB(x, y), "Bed contour escaped the inherited scissor at " + x + "," + y);
        save("world-clipped.png", image);
        // The reusable whole-scene mask must clear even outside the caller's current clip.
        AxisAlignedBB moved = BED.offset(1.15, 0, .2);
        world(); ESP.render(entries(moved), 0, 0, 0, style); BufferedImage freshMask = captureMask();
        world(); ESP.render(entries(BED), 0, 0, 0, style);
        world(); glEnable(GL_SCISSOR_TEST); glScissor(0, 0, mc.displayWidth / 4, mc.displayHeight);
        glColorMask(false, true, false, true);
        ESP.render(entries(moved), 0, 0, 0, style); BufferedImage movedMask = captureMask();
        require(Arrays.equals(freshMask.getRGB(0, 0, mc.displayWidth, mc.displayHeight, null, 0, mc.displayWidth),
                movedMask.getRGB(0, 0, mc.displayWidth, mc.displayHeight, null, 0, mc.displayWidth)),
                "Bed mask retained old geometry across a clipped/color-masked position change");
        world();
        try (ShaderUtil sentinel = new ShaderUtil("#version 120\nvoid main(){gl_FragColor=vec4(.2,.4,.7,1.0);}", true);
             RenderState restore = new RenderState()) {
            poisonState(); sentinel.init(); State before = new State();
            ESP.render(entries(BED), 0, 0, 0, style);
            before.requireSame("Bed ESP world pass");
        }
        world(); BufferedImage empty = capture();
        ESP.render(Collections.emptyList(), 0, 0, 0, style);
        ESP.render(Collections.singletonList(new BedEspRenderer.Entry(null, -1, -1)), 0, 0, 0, style);
        ESP.render(entries(BED), Double.NaN, 0, 0, style);
        require(differences(empty, capture()) == 0, "Invalid or empty bed observations left stale world pixels");
        require(glGetError() == GL_NO_ERROR, "Bed ESP state/empty-frame OpenGL error");
        System.out.println("PASS: Bed ESP inherited clip pixels, FBO/program/textures/matrices/depth/blend restoration and empty observations");
    }

    private static BufferedImage captureMask() throws Exception {
        Field field = BedEspRenderer.class.getDeclaredField("mask"); field.setAccessible(true);
        Framebuffer mask = (Framebuffer) field.get(ESP);
        require(mask != null, "Bed contour has no real framebuffer mask");
        try (RenderState restore = new RenderState()) {
            mask.bindFramebuffer(true); return capture();
        }
    }

    private static void poisonState() {
        glEnable(GL_SCISSOR_TEST); glScissor(70, 60, mc.displayWidth - 140, mc.displayHeight - 120);
        glEnable(GL_DEPTH_TEST); glDepthFunc(GL_GREATER); glDepthMask(true);
        glEnable(GL_FOG); glEnable(GL_CULL_FACE); glEnable(GL_LIGHTING); glEnable(GL_ALPHA_TEST);
        glEnable(GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
        GL14.glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_ZERO, GL_ONE);
        GL13.glActiveTexture(GL13.GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, mc.getFramebuffer().framebufferTexture);
        glEnable(GL_TEXTURE_2D); glViewport(3, 7, mc.displayWidth - 11, mc.displayHeight - 15);
        glColor4f(.2f, .3f, .4f, .5f); glLineWidth(2.25f); glMatrixMode(GL_PROJECTION);
    }

    private static void initialize(PbufferTestContext context, File data) throws Exception {
        mc = new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(
                new Session("BedVisualsFixture", "00000000000000000000000000000000", "0", "legacy"),
                new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(960, 640, false, false),
                new GameConfiguration.FolderInformation(data, new File(data, "packs"), new File(data, "assets"), null),
                new GameConfiguration.GameInformation(false, "Bed visuals Pbuffer check"),
                new GameConfiguration.ServerInformation(null, 0)));
        context.initialize(mc, data);
        IMetadataSerializer metadata = new IMetadataSerializer();
        metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
        metadata.registerMetadataSectionType(new AnimationMetadataSectionSerializer(), AnimationMetadataSection.class);
        metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(), PackMetadataSection.class);
        SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(metadata);
        resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
        set(mc, "mcResourceManager", resources);
        set(mc, "mcLanguageManager", new LanguageManager(new IMetadataSerializer(), "en_US"));
        set(mc, "renderEngine", new TextureManager(resources));
        mc.fontRendererObj = new FontRenderer(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), mc.getTextureManager(), false);
        mc.fontRendererObj.onResourceManagerReload(resources);
        settings = new ClientSettings();
        set(Pulsar.INSTANCE, "mm", new ModuleManager(Collections.singletonList(settings)));
        fonts = new FontManager();
        Pulsar.INSTANCE.fontManager = fonts;
        settings.gradients.setValue(true);
        settings.gradientMotion.setValue("Static");
        settings.animations.setValue(true);
        settings.reducedMotion.setValue(true);
        settings.surface.setValue("Liquid Glass");
        settings.blur.setValue(true);
        settings.blurMethod.setValue("Gaussian");
        settings.glow.setValue(true);
        Theme.setDefaultTheme(Theme.SynthWave);
        UiEffects.invalidate();
        RenderDiagnostics.reset();
        RenderDiagnostics.enabled = true;
        set(mc, "framebufferMc", new Framebuffer(mc.displayWidth, mc.displayHeight, true));
        REPORT.addProperty("format_version", 1);
        REPORT.addProperty("status", "incomplete");
        REPORT.addProperty("scope", "Production Bed ESP/HUD geometry, native shaders and FBO pixels; synthetic observations. No full game bootstrap, server acceptance or hardware FPS claim.");
        REPORT.addProperty("java", System.getProperty("java.runtime.version"));
        REPORT.addProperty("gl_vendor", glGetString(GL_VENDOR));
        REPORT.addProperty("gl_renderer", glGetString(GL_RENDERER));
        REPORT.addProperty("gl_version", glGetString(GL_VERSION));
    }

    private static void resize(int width, int height, int guiScale) {
        mc.displayWidth = width;
        mc.displayHeight = height;
        mc.gameSettings.guiScale = guiScale;
        if (mc.getFramebuffer().framebufferWidth != width || mc.getFramebuffer().framebufferHeight != height)
            mc.getFramebuffer().createBindFramebuffer(width, height);
        UiEffects.invalidate();
    }

    private static void begin() {
        mc.getFramebuffer().bindFramebuffer(true);
        glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL20.glUseProgram(0);
        glDisable(GL_SCISSOR_TEST); glDisable(GL_STENCIL_TEST); GlStateManager.disableFog(); glDisable(GL_FOG);
        GlStateManager.disableBlend(); glDisable(GL_BLEND);
        GlStateManager.disableAlpha(); glDisable(GL_ALPHA_TEST);
        GlStateManager.disableLighting(); glDisable(GL_LIGHTING);
        GlStateManager.disableCull(); glDisable(GL_CULL_FACE);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.disableTexture2D(); glDisable(GL_TEXTURE_2D);
        GlStateManager.depthMask(true); glDepthMask(true); GlStateManager.depthFunc(GL_LESS); glDepthFunc(GL_LESS);
        GlStateManager.enableDepth(); glEnable(GL_DEPTH_TEST);
        GlStateManager.colorMask(true, true, true, true); glColorMask(true, true, true, true);
        GlStateManager.clearColor(.04f, .055f, .08f, 1); glClearColor(.04f, .055f, .08f, 1);
        GlStateManager.clearDepth(1); glClearDepth(1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1, 1, 1, 1); glColor4f(1, 1, 1, 1);
    }

    private static void world() {
        begin();
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(-2.8, 2.8, -1.1, 2.1, -10, 10);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity(); glRotatef(22, 1, 0, 0); glRotatef(-28, 0, 1, 0);
        UiEffects.beginFrame();
    }

    private static void hud() {
        begin(); GlStateManager.disableDepth(); glDisable(GL_DEPTH_TEST);
        ScaledResolution sr = new ScaledResolution(mc);
        glMatrixMode(GL_PROJECTION); glLoadIdentity();
        glOrtho(0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0, -10, 10);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        // Real geometry creates a structured, colored source for glass and blur.
        GlStateManager.shadeModel(GL_SMOOTH); glShadeModel(GL_SMOOTH); glBegin(GL_QUADS);
        glColor3f(.05f, .14f, .23f); glVertex2d(0, 0);
        glColor3f(.23f, .08f, .20f); glVertex2d(sr.getScaledWidth_double(), 0);
        glColor3f(.30f, .23f, .10f); glVertex2d(sr.getScaledWidth_double(), sr.getScaledHeight_double());
        glColor3f(.05f, .20f, .17f); glVertex2d(0, sr.getScaledHeight_double()); glEnd();
        glBegin(GL_LINES);
        for (int x = 0; x < sr.getScaledWidth(); x += 9) {
            glColor3f(.31f, .40f, .43f); glVertex2f(x, 0); glVertex2f(x, sr.getScaledHeight());
        }
        for (int y = 0; y < sr.getScaledHeight(); y += 9) {
            glColor3f(.22f, .31f, .32f); glVertex2f(0, y); glVertex2f(sr.getScaledWidth(), y);
        }
        glEnd(); GlStateManager.shadeModel(GL_FLAT); glShadeModel(GL_FLAT);
        GlStateManager.color(1, 1, 1, 1); glColor4f(1, 1, 1, 1);
        UiEffects.beginFrame();
    }

    private static BufferedImage capture() {
        int width = mc.displayWidth, height = mc.displayHeight;
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int i = (y * width + x) * 4;
            image.setRGB(x, height - 1 - y, (pixels.get(i + 3) & 255) << 24
                    | (pixels.get(i) & 255) << 16 | (pixels.get(i + 1) & 255) << 8 | pixels.get(i + 2) & 255);
        }
        return image;
    }

    private static int differences(BufferedImage first, BufferedImage second) {
        require(first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight(), "Different capture sizes");
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++)
            if ((first.getRGB(x, y) & 0xffffff) != (second.getRGB(x, y) & 0xffffff)) count++;
        return count;
    }

    private static void save(String name, BufferedImage image) throws Exception {
        require(ImageIO.write(image, "png", new File(OUT, name)), "PNG writer unavailable");
    }

    private static void report() throws Exception {
        Files.write(new File(OUT, "bed-visuals.json").toPath(),
                new GsonBuilder().setPrettyPrinting().create().toJson(REPORT).getBytes(StandardCharsets.UTF_8));
    }

    private static void releaseShared() {
        ArrayListSurface.releaseResources(); TextEffects.releaseResources(); Blur.releaseResources();
        GuiShaderBloom.releaseResources(); UIUtils.releaseResources(); GaussianBlur.releaseResources();
        UiEffects.invalidate();
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class State {
        private final int[] values = {glGetInteger(GL20.GL_CURRENT_PROGRAM), glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),
                glGetInteger(GL13.GL_ACTIVE_TEXTURE), glGetInteger(GL_TEXTURE_BINDING_2D), glGetInteger(GL_MATRIX_MODE),
                glGetInteger(GL_DEPTH_FUNC), glGetInteger(GL14.GL_BLEND_EQUATION), glGetInteger(GL14.GL_BLEND_SRC_RGB),
                glGetInteger(GL14.GL_BLEND_DST_RGB), glGetInteger(GL14.GL_BLEND_SRC_ALPHA), glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                glGetInteger(GL_DRAW_BUFFER), glGetInteger(GL_READ_BUFFER), glGetInteger(GL_SHADE_MODEL)};
        private final boolean[] flags = {glIsEnabled(GL_DEPTH_TEST), glGetBoolean(GL_DEPTH_WRITEMASK), glIsEnabled(GL_BLEND),
                glIsEnabled(GL_ALPHA_TEST), glIsEnabled(GL_CULL_FACE), glIsEnabled(GL_LIGHTING), glIsEnabled(GL_FOG),
                glIsEnabled(GL_SCISSOR_TEST), glIsEnabled(GL_STENCIL_TEST), glIsEnabled(GL_TEXTURE_2D)};
        private final int[] viewport = integers(GL_VIEWPORT), clip = integers(GL_SCISSOR_BOX);
        private final byte[] colorMask = mask();
        private final float[] model = floats(GL_MODELVIEW_MATRIX), projection = floats(GL_PROJECTION_MATRIX), color = floats(GL_CURRENT_COLOR);
        private final float lineWidth = glGetFloat(GL_LINE_WIDTH);
        private final int[] textures = textures();

        private void requireSame(String label) {
            State other = new State();
            require(Arrays.equals(values, other.values) && Arrays.equals(flags, other.flags)
                    && Arrays.equals(viewport, other.viewport) && Arrays.equals(clip, other.clip)
                    && Arrays.equals(model, other.model) && Arrays.equals(projection, other.projection)
                    && Arrays.equals(color, other.color) && Arrays.equals(textures, other.textures)
                    && Arrays.equals(colorMask, other.colorMask)
                    && lineWidth == other.lineWidth, label + " leaked inherited OpenGL state");
        }
        private static int[] textures() {
            int active = glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int[] result = new int[8];
            for (int unit = 0; unit < 4; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                result[unit * 2] = glGetInteger(GL_TEXTURE_BINDING_2D);
                result[unit * 2 + 1] = glIsEnabled(GL_TEXTURE_2D) ? 1 : 0;
            }
            GL13.glActiveTexture(active); return result;
        }
        private static int[] integers(int parameter) {
            IntBuffer values = BufferUtils.createIntBuffer(16); glGetInteger(parameter, values);
            int[] result = new int[4]; for (int i = 0; i < result.length; i++) result[i] = values.get(i); return result;
        }
        private static byte[] mask() {
            ByteBuffer values = BufferUtils.createByteBuffer(16); glGetBoolean(GL_COLOR_WRITEMASK, values);
            byte[] result = new byte[4]; for (int i = 0; i < result.length; i++) result[i] = values.get(i); return result;
        }
        private static float[] floats(int parameter) {
            FloatBuffer values = BufferUtils.createFloatBuffer(16); glGetFloat(parameter, values);
            float[] result = new float[parameter == GL_CURRENT_COLOR ? 4 : 16];
            for (int i = 0; i < result.length; i++) result[i] = values.get(i); return result;
        }
    }
}
