package me.youngjibbit.clickgui;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.render.ClickGUI;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.utils.render.RenderDiagnostics;
import me.youngjibbit.value.impl.ComboValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.util.*;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Real production controls/shaders in a Pbuffer; no account, world or native Display is started. */
public final class DropdownGuiRenderCheck {
    private static Minecraft mc;
    private static final File OUTPUT = new File("dropdown-preview");
    private static final boolean PERFORMANCE_BASELINE = Boolean.getBoolean("dropdown.performance.baseline");
    private static final class Gui extends DropdownGui {
        boolean ctrl, shift;
        int themeOpens;
        @Override protected boolean ctrlDown() { return ctrl; }
        @Override protected boolean shiftDown() { return shift; }
        @Override protected void openThemeEditor() { themeOpens++; }
        void click(float x, float y, int button) throws Exception { mouseClicked((int)x, (int)y, button); }
        void release() { mouseReleased(0, 0, 0); }
        void key(char c, int key) throws Exception { keyTyped(c, key); }
        void type(String text) throws Exception { for (char c : text.toCharArray()) key(c, Keyboard.KEY_NONE); }
        void move(float x,float y){mouseClickMove((int)x,(int)y,0,10);}
        void scroll(float x, float y, int wheel) { scrollAt((int)x, (int)y, wheel); }
    }

    public static void main(String[] args) throws Exception {
        OUTPUT.mkdirs();
        System.setProperty("user.home", new File(OUTPUT, "home").getAbsolutePath());
        File data = java.nio.file.Files.createTempDirectory(OUTPUT.toPath(), "fixture-").toFile();
        try (PbufferTestContext context = new PbufferTestContext()) {
            mc = new Minecraft(new GameConfiguration(
                    new GameConfiguration.UserInformation(new Session("DropdownPreview", "00000000000000000000000000000000", "0", "legacy"),
                            new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(1280, 720, false, false),
                    new GameConfiguration.FolderInformation(data, new File(data, "packs"), new File(data, "assets"), null),
                    new GameConfiguration.GameInformation(false, "Dropdown render check"),
                    new GameConfiguration.ServerInformation(null, 0)));
            context.initialize(mc, data);
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc, "mcResourceManager", resources);
            set(mc, "mcLanguageManager", new LanguageManager(new IMetadataSerializer(), "en_US"));
            set(mc, "framebufferMc", new Framebuffer(mc.displayWidth, mc.displayHeight, true));
            Pulsar.INSTANCE.fontManager = ClickGuiRenderCheck.minimalFonts("Poppins-Regular 16","Poppins-Medium 16","icon1 16");
            set(Pulsar.INSTANCE, "mm", new ModuleManager());
            ClientSettings settings = ClientSettings.current();
            settings.surface.setValue("Blur");
            settings.reducedMotion.setValue(true);
            Theme.setDefaultTheme(Theme.Neutron);
            Gui gui = new Gui();
            set(Pulsar.INSTANCE, "dropdown", gui);
            resize(gui, 1280, 720, 1);
            frame(gui, -1, -1);
            checkBounds(gui, true);
            snapshot("01-columns.png");
            checkMode(gui);
            checkScissor(gui);
            checkHeadingPasses(gui);
            checkInputs(gui);
            checkSearch(gui);
            checkDragCollapse(gui);
            checkAnimatedControls(gui,settings);
            checkSizes(gui);
            checkGradientMaster(gui, settings);
            checkMaterials(gui, settings);
            checkCategoryRamp(gui);
            checkLifecycle(gui);
            require(glGetError() == GL_NO_ERROR, "Dropdown leaked a GL error");
            context.check();
            gui.onGuiClosed();
            ClickLibraryRenderCheck.run(mc);
            Pulsar.INSTANCE.fontManager.close();
            ArrayListSurface.releaseResources();
        }
        System.out.println(PERFORMANCE_BASELINE ? "BASELINE CAPTURE ONLY: dropdown performance assertion bypassed; NOT a test PASS"
                : "PASS [Pbuffer/FBO]: Dropdown mode, real values/input, numeric edit/cancel, keybind, search, scroll, topmost dispatch, drag/collapse, scales, materials and resource lifecycle");
    }

    private static void checkMode(Gui gui) throws Exception {
        ClickGUI owner = (ClickGUI)Pulsar.INSTANCE.getMm().getModule(ClickGUI.class);
        ComboValue mode = owner.getModeSetting("mode");
        require(Arrays.equals(mode.getOptions(), new String[]{"Panel", "iOS", "Dropdown"}), "Existing config indices must remain stable");
        Method resolve = ClickGUI.class.getDeclaredMethod("resolveScreen", String.class);
        resolve.setAccessible(true);
        require(resolve.invoke(owner, "Dropdown") == gui, "Selected mode must resolve to production dropdown screen");
        require(!gui.doesGuiPauseGame(), "ClickGUI must not pause gameplay");
    }

    private static void checkScissor(Gui gui) {
        glEnable(GL_SCISSOR_TEST);
        glScissor(17, 29, 330, 210);
        gui.drawScreen(-1, -1, 0);
        java.nio.IntBuffer box = BufferUtils.createIntBuffer(16);
        glGetInteger(GL_SCISSOR_BOX, box);
        require(glIsEnabled(GL_SCISSOR_TEST) && box.get(0) == 17 && box.get(1) == 29
                && box.get(2) == 330 && box.get(3) == 210, "Dropdown must restore its caller's scissor");
        glDisable(GL_SCISSOR_TEST);
    }

    private static void checkHeadingPasses(Gui gui) throws Exception {
        ClickGUI owner = (ClickGUI)Pulsar.INSTANCE.getMm().getModule(ClickGUI.class);
        boolean wasEnabled = RenderDiagnostics.enabled;
        boolean previousGlow = owner.getBooleanSetting("Heading Glow").getValue();
        try {
            RenderDiagnostics.enabled = true;
            owner.getBooleanSetting("Heading Glow").setValue(false);
            RenderDiagnostics.reset();
            frame(gui, -1, -1);
            long surfaceMasks = panels(gui).size() + 1;
            System.out.println("HEADING GLOW OFF: " + RenderDiagnostics.snapshot());
            if (!PERFORMANCE_BASELINE) require(RenderDiagnostics.maskPasses == surfaceMasks
                    && RenderDiagnostics.composites == surfaceMasks, "Disabled heading glow must not add glyph mask/composite passes");
            owner.getBooleanSetting("Heading Glow").setValue(true);
            RenderDiagnostics.reset();
            frame(gui, -1, -1);
            System.out.println("HEADING GLOW ON: " + RenderDiagnostics.snapshot());
            require(RenderDiagnostics.maskPasses == surfaceMasks + panels(gui).size(), "Enabled heading glow must use the shared glyph path");
            snapshot("02-heading-glow.png");
        } finally {
            owner.getBooleanSetting("Heading Glow").setValue(previousGlow);
            RenderDiagnostics.enabled = wasEnabled;
        }
    }

    private static void checkInputs(Gui gui) throws Exception {
        Object combat = panel(gui, Category.Combat);
        DropdownModule aim = row(combat, "AimAssist");
        boolean before = aim.getModule().isToggled();
        // The far right remains a toggle target, unlike the normal panel's disclosure button.
        gui.click(aim.getX() + aim.getWidth() - 3, aim.getY() + 8, 0);
        gui.release();
        require(aim.getModule().isToggled() != before && !aim.isOpen(), "Full compact row must toggle");
        frame(gui, -1, -1);
        snapshot("02-enabled.png");
        gui.click(aim.getX() + 10, aim.getY() + 8, 0);
        gui.release();
        require(aim.getModule().isToggled() == before, "Toggle did not round trip");
        gui.click(aim.getX() + 30, aim.getY() + 8, 1);
        frame(gui, -1, -1);
        require(aim.isOpen() && number(combat, "width") <= 108, "Settings must keep the compact column width");
        ClickSlider slider = null;
        for (Component.Sub<?> value : aim.focusableValues()) if (value instanceof ClickSlider) { slider = (ClickSlider)value; break; }
        require(slider != null, "AimAssist production slider missing");
        double original = slider.getValue().getValue();
        float valueX = methodNumber(slider, "valueX");
        gui.click(valueX + 5, slider.getY() + 10, 0);
        require(slider.isEditing(), "Real numeric field must enter edit mode");
        gui.type("3.25");
        gui.key(' ', Keyboard.KEY_RETURN);
        require(slider.getValue().getValue() == 3.25 && !slider.isEditing(), "Numeric input must reach the live module value");
        gui.key(' ', Keyboard.KEY_RETURN);
        gui.type("2.75");
        gui.key(' ', Keyboard.KEY_ESCAPE);
        require(slider.getValue().getValue() == 3.25 && !slider.isEditing(), "Esc must cancel exact-number input");
        slider.getValue().setValue(original);
        snapshot("03-settings.png");

        // Scroll only the hovered category. The other column's offset stays unchanged.
        resize(gui, 854, 480, 1);
        frame(gui, -1, -1);
        require(number(combat, "maxScroll") > 0, "Scroll fixture must overflow before testing wheel dispatch");
        Object movement = panel(gui, Category.Movement);
        float oldMovement = number(movement, "targetScroll");
        gui.scroll(aim.getX() + 20, aim.getY() + 8, -120);
        frame(gui, -1, -1);
        require(number(combat, "targetScroll") < 0 && number(movement, "targetScroll") == oldMovement, "Wheel leaked into another category");
        for (int i = 0; i < 60; i++) gui.scroll(number(combat, "x") + 10, number(combat, "y") + 25, -120);
        frame(gui, -1, -1);
        require(Math.abs(number(combat, "scroll") + number(combat, "maxScroll")) < .01f, "Bottom scroll clamp");
        for (int i = 0; i < 60; i++) gui.scroll(number(combat, "x") + 10, number(combat, "y") + 25, 120);
        frame(gui, -1, -1);
        require(number(combat, "scroll") == 0, "Top scroll clamp");

        int oldKey = aim.getModule().getKey();
        gui.click(aim.getX() + 12, aim.getY() + 8, 2);
        require(get(gui, "binding") == aim, "Middle click must capture module binding");
        gui.key(' ', Keyboard.KEY_F8);
        require(aim.getModule().getKey() == Keyboard.KEY_F8, "Keybind did not reach live module");
        gui.key('k', Keyboard.KEY_K);
        gui.key(' ', Keyboard.KEY_ESCAPE);
        require(aim.getModule().getKey() == Keyboard.KEY_F8, "Binding Esc changed key");
        gui.key('k', Keyboard.KEY_K);
        gui.key(' ', Keyboard.KEY_DELETE);
        require(aim.getModule().getKey() == 0, "Binding delete failed");
        aim.getModule().setKey(oldKey);
        gui.click(aim.getX() + 10, aim.getY() + 8, 1);
        frame(gui, -1, -1);
        require(!aim.isOpen() && aim.getHeight() == DropdownModule.ROW_HEIGHT, "Settings did not collapse");
    }

    private static void checkAnimatedControls(Gui gui,ClientSettings settings)throws Exception{
        resize(gui,854,480,1);gui.key(' ',Keyboard.KEY_HOME);frame(gui,-1,-1);
        Object combat=panel(gui,Category.Combat);
        DropdownModule aim=row(combat,"AimAssist");
        settings.reducedMotion.setValue(false);
        float compact=number(combat,"width");
        aim.setOpen(true);gui.layout(0);
        me.youngjibbit.utils.animations.Animation opening=(me.youngjibbit.utils.animations.Animation)get(aim,"openAnimation");
        opening.timerUtil.setTime(System.currentTimeMillis()-80);gui.layout(0);
        float partial=aim.getHeight();require(partial>DropdownModule.ROW_HEIGHT,"Module opening must reveal content progressively");
        require(number(combat,"width")==compact,"Opening settings must not overlap the next column");
        frame(gui,-1,-1);snapshot("20-module-opening.png");
        settings.reducedMotion.setValue(true);gui.layout(0);float full=aim.getHeight();require(full>partial,"Reduced motion snaps to complete settings");
        ClickSlider slider=null;for(Component.Sub<?> value:aim.presentationValues())if(value instanceof ClickSlider){slider=(ClickSlider)value;break;}
        require(slider!=null,"Real compact slider");
        double old=slider.getValue().getValue();
        float tx=methodNumber(slider,"trackX"),tw=methodNumber(slider,"trackWidth"),ty=methodNumber(slider,"trackY");
        gui.click(tx+tw*.2f,ty,0);double clicked=slider.getValue().getValue();
        frame(gui,(int)(tx+tw*.8f),(int)ty);
        require(slider.getValue().getValue()==clicked,"Drawing a held slider must not write values");
        gui.move(tx+tw*.8f,ty);require(slider.getValue().getValue()!=clicked,"Mouse drag event must reach value");
        gui.release();slider.getValue().setValue(old);
        settings.reducedMotion.setValue(false);gui.layout(0);
        gui.scroll(number(combat,"x")+10,number(combat,"y")+30,-120);gui.layout(.016f);
        require(number(combat,"scroll")<0&&number(combat,"scroll")>number(combat,"targetScroll"),"Wheel must interpolate inside one column");
        float body=number(combat,"height");
        gui.click(number(combat,"x")+20,number(combat,"y")+8,1);
        me.youngjibbit.utils.animations.Animation reveal=(me.youngjibbit.utils.animations.Animation)get(combat,"reveal");
        reveal.timerUtil.setTime(System.currentTimeMillis()-80);gui.layout(0);
        require(number(combat,"height")>17&&number(combat,"height")<body,"Category must animate closed");
        frame(gui,-1,-1);snapshot("21-category-closing.png");
        settings.reducedMotion.setValue(true);gui.layout(0);
        require(number(combat,"height")==17,"Reduced motion collapses category immediately");
        gui.click(number(combat,"x")+20,number(combat,"y")+8,1);
        aim.setOpen(false);gui.key(' ',Keyboard.KEY_HOME);frame(gui,-1,-1);
    }

    private static void checkSearch(Gui gui) throws Exception {
        gui.key('/', Keyboard.KEY_NONE);
        gui.type("rotational");
        frame(gui, -1, -1);
        require(totalRows(gui) == 1, "Description search must span all categories");
        gui.key(' ', Keyboard.KEY_RETURN);
        require(get(gui, "focus") instanceof DropdownModule, "Enter from search must focus first result");
        Object owner = panel(gui, Category.Combat);
        require(panels(gui).get(panels(gui).size() - 1) == owner, "Keyboard reveal must bring focused column to front");
        gui.key(' ', Keyboard.KEY_RIGHT);
        frame(gui, -1, -1);
        gui.key(' ', Keyboard.KEY_TAB);
        require(get(gui, "focus") instanceof Component.Sub, "Tab must reach shared settings");
        Component focused = (Component)get(gui, "focus");
        require(focused.getClip().contains((int)focused.getX() + 20, (int)focused.getY() + 10), "Keyboard setting must be revealed in its viewport");
        snapshot("04-search-keyboard.png");
        gui.key('/', Keyboard.KEY_NONE);
        gui.ctrl = true;
        gui.key('a', Keyboard.KEY_A);
        gui.ctrl = false;
        gui.type("zzzz-no-module");
        frame(gui, -1, -1);
        require(totalRows(gui) == 0, "Search empty state");
        gui.key(' ', Keyboard.KEY_TAB);
        require(get(gui, "focus") == null, "Empty search Tab must not focus an invisible module");
        snapshot("05-no-results.png");
        gui.key(' ', Keyboard.KEY_ESCAPE);
        gui.key(' ', Keyboard.KEY_ESCAPE);
        require(((String)get(gui, "search")).isEmpty() && !(Boolean)get(gui, "searchFocus"), "Search Escape clear/unfocus sequence");
        DropdownModule aim = row(panel(gui, Category.Combat), "AimAssist");
        aim.setOpen(false);
        frame(gui, -1, -1);
        gui.ctrl = true;
        gui.key('t', Keyboard.KEY_T);
        gui.ctrl = false;
        require(gui.themeOpens == 1, "Shared theme editor keyboard entry");
    }

    private static void checkDragCollapse(Gui gui) throws Exception {
        gui.key(' ', Keyboard.KEY_HOME);
        frame(gui, -1, -1);
        Object combat = panel(gui, Category.Combat), movement = panel(gui, Category.Movement);
        gui.click(number(combat, "x") + 20, number(combat, "y") + 8, 0);
        frame(gui, (int)number(movement, "x") + 20, (int)number(movement, "y") + 8);
        gui.release();
        frame(gui, -1, -1);
        DropdownModule under = rows(movement).get(0), over = rows(combat).get(0);
        boolean unchanged = under.getModule().isToggled();
        boolean wasOpen = over.isOpen();
        gui.click(over.getX() + 20, over.getY() + 8, 1);
        require(under.getModule().isToggled() == unchanged && over.isOpen() != wasOpen, "Overlapping columns must dispatch only to the top row");
        over.setOpen(false);
        frame(gui, -1, -1);
        gui.click(number(combat, "x") + 20, number(combat, "y") + 8, 1);
        frame(gui, -1, -1);
        require(!(Boolean)get(combat, "open") && number(combat, "height") == 17, "Right-click header must collapse category");
        gui.click(number(combat, "x") + 20, number(combat, "y") + 8, 1);
        gui.click(number(combat, "x") + 20, number(combat, "y") + 8, 0);
        frame(gui, -200, -200);
        gui.release();
        require(number(combat, "x") == 10 && number(combat, "y") == 10, "Drag must clamp to screen");
        gui.key(' ', Keyboard.KEY_HOME);
        frame(gui, -1, -1);
        checkBounds(gui, true);
    }

    private static void checkSizes(Gui gui) throws Exception {
        int[][] sizes = {{854, 480, 1}, {854, 480, 2}, {1280, 960, 3}, {1101, 733, 3}, {320, 224, 1}};
        for (int i = 0; i < sizes.length; i++) {
            resize(gui, sizes[i][0], sizes[i][1], sizes[i][2]);
            frame(gui, -1, -1);
            checkBounds(gui, true);
            snapshot("06-size-" + i + ".png");
        }
    }

// Same production row and physical pixels, changing only the category ramp position.
private static void checkGradientMaster(Gui gui, ClientSettings settings) throws Exception {
    DropdownModule real = row(panel(gui, Category.Combat), "AimAssist");
    boolean toggled = real.getModule().isToggled();
    String motion = settings.gradientMotion.getValue();
    boolean gradients = settings.gradients.getValue();
    try {
        real.getModule().setToggled(true);
        DropdownModule probe = new DropdownModule(real.getModule());
        probe.updateAnimation();
        probe.layout(20, 20, 108, new ClipRect(20, 20, 108, 16));
        settings.gradients.setValue(true);
        settings.gradientMotion.setValue("Static");
        int first = sampleRamp(probe, .1f), second = sampleRamp(probe, .9f);
        require(first != second, "Enabled category ramp must vary between positions");
        settings.gradients.setValue(false);
        first = sampleRamp(probe, .1f); second = sampleRamp(probe, .9f);
        require(first == second, "Shared Gradients OFF must remove the category ramp");
        settings.gradients.setValue(true);
        settings.gradientMotion.setValue("Solid");
        first = sampleRamp(probe, .1f); second = sampleRamp(probe, .9f);
        require(first == second, "Solid gradient motion must remove the category ramp");
    } finally {
        settings.gradients.setValue(gradients);
        settings.gradientMotion.setValue(motion);
        real.getModule().setToggled(toggled);
    }
}

private static int sampleRamp(DropdownModule row, float position) {
    mc.getFramebuffer().bindFramebuffer(true);
    glDisable(GL_SCISSOR_TEST);
    glClearColor(.12f, .18f, .24f, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    try(me.youngjibbit.utils.render.UiEffects.GradientSpace gradient=me.youngjibbit.utils.render.UiEffects.gradientSpace(20,24-position*200,108,200)){
        row.drawScreen(-1,-1);
    }
    glFinish();
    int scale = new ScaledResolution(mc).getScaleFactor();
    ByteBuffer pixel = BufferUtils.createByteBuffer(4);
    // Left inset avoids the enabled-state dot, centered name and focus rails.
    glReadPixels(28 * scale, mc.displayHeight - 24 * scale, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
    return (pixel.get(0) & 255) << 16 | (pixel.get(1) & 255) << 8 | (pixel.get(2) & 255);
}

    private static void checkMaterials(Gui gui, ClientSettings settings) throws Exception {
        resize(gui, 854, 480, 1);
        for (String material : new String[]{"Clear", "Gradient", "Blur", "Liquid Glass"}) {
            settings.surface.setValue(material);
            frame(gui, -1, -1);
            snapshot("07-material-" + material.replace(' ', '-') + ".png");
            require(glGetError() == GL_NO_ERROR, "Material GL failure: " + material);
            for (Object panel : panels(gui)) {
                Framebuffer[] buffers = (Framebuffer[])get(get(panel, "surface"), "buffers");
                require(buffers[3] == null && buffers[4] == null, "Category must borrow shared clean/blurred scene");
            }
        }
        settings.surface.setValue("Blur");
        for (String method : new String[]{"Gaussian", "Kawase"}) {
            settings.blurMethod.setValue(method);
            frame(gui, -1, -1);
            require(glGetError() == GL_NO_ERROR, "Shared blur-method failure: " + method);
            snapshot("07-blur-" + method + ".png");
        }
        settings.blurMethod.setValue("Gaussian");
        settings.blur.setValue(false);
        settings.glow.setValue(false);
        settings.border.setValue(false);
        settings.gradients.setValue(false);
        frame(gui, -1, -1);
        snapshot("08-effects-off.png");
        settings.surface.setValue("Blur");
        settings.blur.setValue(true);
        settings.glow.setValue(true);
        settings.border.setValue(true);
        settings.gradients.setValue(true);
        Theme.setDefaultTheme(Theme.Neon);
        frame(gui, -1, -1);
        snapshot("09-theme-neon.png");
        Theme.setDefaultTheme(Theme.Neutron);
    }

    private static void checkLifecycle(Gui gui) throws Exception {
        Object combat = panel(gui, Category.Combat);
        DropdownModule aim = row(combat, "AimAssist");
        gui.click(aim.getX() + 20, aim.getY() + 8, 2);
        gui.onGuiClosed();
        require(get(gui, "binding") == null && get(gui, "dragging") == null && get(gui, "focus") == null, "Close must cancel transient input");
        for (Framebuffer buffer : (Framebuffer[])get(get(gui, "scene"), "buffers")) require(buffer == null, "Close must release scene references");
        gui.initGui();
        frame(gui, -1, -1);
        require(glGetError() == GL_NO_ERROR, "Close/reopen must recreate resources");
        snapshot("10-reopened.png");
    }

    private static void checkCategoryRamp(Gui gui) throws Exception {
        Object combat = panel(gui, Category.Combat);
        List<DropdownModule> enabled = Arrays.asList(row(combat, "AimAssist"), row(combat, "AntiBot"), row(combat, "MoreKB"));
        List<Boolean> original = new ArrayList<>();
        for (DropdownModule module : enabled) original.add(module.getModule().isToggled());
        try {
            // Real input handlers and live modules. No world/update/attack event is dispatched.
            for (DropdownModule module : enabled) if (!module.getModule().isToggled()) {
                gui.click(module.getX() + 20, module.getY() + 8, 0);
                gui.release();
            }
            for (DropdownModule module : enabled) require(module.getModule().isToggled(), "Ramp capture must show several enabled rows");
            frame(gui, -1, -1);
            snapshot("11-category-ramp.png");
        } finally {
            for (int i = 0; i < enabled.size(); i++) enabled.get(i).getModule().setToggled(original.get(i));
        }
    }

    private static void checkBounds(Gui gui, boolean disjoint) throws Exception {
        List<Object> panels = panels(gui);
        for (int i = 0; i < panels.size(); i++) {
            Object panel = panels.get(i);
            ClipRect box = new ClipRect(number(panel, "x"), number(panel, "y"), number(panel, "width"), number(panel, "height"));
            require(box.x >= 10 && box.y >= 10 && box.x + box.width <= gui.width - 9.9f
                    && box.y + box.height <= gui.height - 47.9f, "Category outside available screen: " + get(panel, "category"));
            if (disjoint) for (int j = i + 1; j < panels.size(); j++) {
                Object other = panels.get(j);
                require(box.intersect(new ClipRect(number(other, "x"), number(other, "y"), number(other, "width"), number(other, "height"))).isEmpty(), "Default category panels overlap");
            }
        }
    }

    private static void resize(Gui gui, int width, int height, int scale) {
        mc.displayWidth = width;
        mc.displayHeight = height;
        mc.gameSettings.guiScale = scale;
        mc.getFramebuffer().createBindFramebuffer(width, height);
        ScaledResolution sr = new ScaledResolution(mc);
        gui.setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
    }

    private static void frame(Gui gui, int mx, int my) {
        mc.getFramebuffer().bindFramebuffer(true);
        glDisable(GL_SCISSOR_TEST);
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.color(1, 1, 1, 1);
        glClearColor(.12f, .18f, .24f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        ScaledResolution sr = new ScaledResolution(mc);
        glMatrixMode(GL_PROJECTION); glLoadIdentity();
        glOrtho(0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0, -1000, 1000);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        // Synthetic scene, deliberately labelled as such in the test coverage.
        GlStateManager.disableTexture2D();
        glBegin(GL_QUADS);
        glColor4f(.16f, .24f, .32f, 1); glVertex2f(0, 0); glVertex2f(gui.width, 0);
        glColor4f(.35f, .29f, .24f, 1); glVertex2f(gui.width, gui.height); glVertex2f(0, gui.height);
        glEnd();
        GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
        gui.drawScreen(mx, my, 0);
        glFinish();
    }

    private static void snapshot(String name) throws Exception {
        mc.getFramebuffer().bindFramebuffer(false);
        ByteBuffer pixels = BufferUtils.createByteBuffer(mc.displayWidth * mc.displayHeight * 4);
        glReadPixels(0, 0, mc.displayWidth, mc.displayHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(mc.displayWidth, mc.displayHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < mc.displayHeight; y++) for (int x = 0; x < mc.displayWidth; x++) {
            int at = (y * mc.displayWidth + x) * 4;
            image.setRGB(x, mc.displayHeight - y - 1, 0xff000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | (pixels.get(at + 2) & 255));
        }
        ImageIO.write(image, "png", new File(OUTPUT, name));
    }

    @SuppressWarnings("unchecked") private static List<Object> panels(Gui gui) throws Exception { return (List<Object>)get(gui, "panels"); }
    @SuppressWarnings("unchecked") private static List<DropdownModule> rows(Object panel) throws Exception { return (List<DropdownModule>)get(panel, "rows"); }
    private static Object panel(Gui gui, Category category) throws Exception { for (Object panel : panels(gui)) if (get(panel, "category") == category) return panel; throw new AssertionError(category); }
    private static DropdownModule row(Object panel, String name) throws Exception { for (DropdownModule row : rows(panel)) if (row.getModule().getName().equals(name)) return row; throw new AssertionError(name); }
    private static int totalRows(Gui gui) throws Exception { int result = 0; for (Object panel : panels(gui)) result += rows(panel).size(); return result; }
    private static Field field(Class<?> type, String name) throws Exception { while (type != null) { try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ex) { type = type.getSuperclass(); } } throw new NoSuchFieldException(name); }
    private static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { field(target.getClass(), name).set(target, value); }
    private static float number(Object target, String name) throws Exception { return ((Number)get(target, name)).floatValue(); }
    private static float methodNumber(Object target, String name) throws Exception { Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); return ((Number)m.invoke(target)).floatValue(); }
    private static void require(boolean pass, String reason) { if (!pass) throw new AssertionError(reason); }
}
