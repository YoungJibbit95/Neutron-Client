package me.youngjibbit.clickgui;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.Component;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.clickgui.utils.*;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.shader.KawaseBloom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/**
 * Standalone OpenGL regression check. Uses a pbuffer, synthetic backdrop, and disposable data directory;
 * does not start Minecraft, log into an account, load a world, or call Pulsar.init().
 * Run with Temurin 8, the built dependency jar/classes on the classpath, and test_natives/windows.
 */
public final class ClickGuiRenderCheck {
    private static Minecraft mc;
    private static final File OUTPUT = new File("gui-preview");
    private static class Panel extends ClickGui {
        boolean shift,ctrl;int themeOpens;
        ClipRect searchBox(){return searchBounds();}ClipRect themesBox(){return themeBounds();}
        @Override protected void openThemeEditor(){ThemeEditorScreen editor=createThemeEditor();require(editor!=null,"Theme editor factory");editor.onGuiClosed();themeOpens++;}
        @Override protected boolean shiftDown(){return shift;}
        @Override protected boolean ctrlDown(){return ctrl;}
        void click(int x, int y, int button) throws Exception { mouseClicked(x, y, button); }
        void release() { mouseReleased(0, 0, 0); }
        void key(char c,int key)throws Exception{keyTyped(c,key);}
        void type(String text)throws Exception{for(char c:text.toCharArray())key(c,org.lwjgl.input.Keyboard.KEY_NONE);}
    }

    public static void main(String[] args) throws Exception {
        OUTPUT.mkdirs();
        System.setProperty("user.home", new File(OUTPUT, "home").getAbsolutePath());
        File data = java.nio.file.Files.createTempDirectory(OUTPUT.toPath(), "fixture-").toFile();
        try (PbufferTestContext context = new PbufferTestContext()) {
            mc = new Minecraft(new GameConfiguration(
                    new GameConfiguration.UserInformation(new Session("GuiPreview", "00000000000000000000000000000000", "0", "legacy"),
                            new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(1280, 960, false, false),
                    new GameConfiguration.FolderInformation(data, new File(data, "packs"), new File(data, "assets"), null),
                    new GameConfiguration.GameInformation(false, "GUI regression check"),
                    new GameConfiguration.ServerInformation(null, 0)));
            context.initialize(mc, data);
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc, "mcResourceManager", resources);
            set(mc, "mcLanguageManager", new LanguageManager(new IMetadataSerializer(), "en_US"));
            set(mc, "framebufferMc", new Framebuffer(mc.displayWidth, mc.displayHeight, true));
            Pulsar.INSTANCE.fontManager = minimalFonts();
            set(Pulsar.INSTANCE, "mm", new ModuleManager());
            // This fixture verifies the original dark reference design. The configurable
            // Clear/Gradient/Blur/Glass materials have a separate global-effects matrix.
            me.youngjibbit.module.impl.client.ClientSettings.current().surface.setValue("Standard");
            Theme.setDefaultTheme(Theme.SynthWave);
            checkClipMath();
            checkScissorRestore();
            checkNestedBatches();
            Panel panel = new Panel();
            set(Pulsar.INSTANCE, "panel", panel);
            resize(panel, 1280, 960, 2);
            frame(panel, -1, -1);
            for (ClickModule module : (List<ClickModule>) get(panel, "modules")) {
                require(module.getHeight() == 24, "New modules must start collapsed without a settings flash");
            }
            snapshot("01-default.png");
            checkDarkPanel(panel);
            ClickModule first = ((List<ClickModule>) get(panel, "modules")).get(0);
            int firstX = (int) first.getX() + 20;
            int firstY = (int) first.getY() + 12;
            panel.click(firstX, firstY, 0);
            settle(panel);
            snapshot("02-active.png");
            panel.click(firstX, firstY, 1);
            settle(panel);
            snapshot("03-settings.png");
            checkLabelFit();
            checkControls(panel);
            checkScroll(panel, "05");
            checkCollapse(panel);
            checkDragResize(panel);
            checkTheme(panel);
            resize(panel, 854, 480, 2);
            panel.click((int) number(panel, "x") + 40, categoryY(panel, 0), 0);
            settle(panel);
            snapshot("08-small-scale2.png");
            checkWindowBounds(panel);
            checkScroll(panel, "08");
            resize(panel, 1280, 960, 3);
            settle(panel);
            snapshot("09-scale3.png");
            checkWindowBounds(panel);
            resize(panel, 1101, 733, 1);
            settle(panel);
            snapshot("10-scale1-odd-resolution.png");
            checkWindowBounds(panel);
            resize(panel, 1101, 733, 3);
            settle(panel);
            snapshot("11-scale3-odd-resolution.png");
            checkWindowBounds(panel);
            if(!Boolean.getBoolean("pulsar.gui.baseline"))checkAdvancedInput(panel);
            checkBloomReuse();
            require(glGetError() == GL_NO_ERROR, "OpenGL error after rendering");
            context.check();
        }
        System.out.println("PASS [Pbuffer/FBO only]: clipping, input, sliders, checkbox, combo, scrolling, drag/resize, theme snapshots, simulated GUI scales, bloom reuse");
    }

    private static void checkAdvancedInput(Panel panel)throws Exception{
        Map<me.youngjibbit.module.Module,Boolean> toggled=new IdentityHashMap<>();
        for(me.youngjibbit.module.Module m:Pulsar.INSTANCE.getMm().getAllModules())toggled.put(m,m.isToggled());
        resize(panel,1280,960,2);mc.currentScreen=panel;
        ClipRect themeButton=panel.themesBox();panel.click((int)(themeButton.x+themeButton.width/2),(int)(themeButton.y+themeButton.height/2),0);require(panel.themeOpens==1,"Theme header entry");panel.ctrl=true;panel.key('t',org.lwjgl.input.Keyboard.KEY_T);panel.ctrl=false;require(panel.themeOpens==2,"Ctrl-T theme entry");
        panel.ctrl=true;panel.key('f',org.lwjgl.input.Keyboard.KEY_F);panel.ctrl=false;require((Boolean)get(panel,"searchFocus"),"Ctrl-F search");panel.type("rotational");settle(panel);
        List<ClickModule> rows=(List<ClickModule>)get(panel,"modules");
        require(rows.size()==1&&rows.get(0).getModule().getName().equals("AimAssist"),"Description search across categories");snapshot("12-search.png");
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);require(((String)get(panel,"search")).isEmpty()&&(Boolean)get(panel,"searchFocus"),"Escape must clear search first");
        panel.type("zzzz-no-module");settle(panel);require(((List<?>)get(panel,"modules")).isEmpty(),"Search empty state");snapshot("13-empty.png");
        // Clear through the real mouse handler, then find a module by its name.
        panel.click((int)(panel.searchBox().x+panel.searchBox().width-8),(int)panel.searchBox().y+6,0);panel.type("AimAssist");settle(panel);
        rows=(List<ClickModule>)get(panel,"modules");require(rows.size()==1,"Module-name search");ClickModule row=rows.get(0);
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);require(get(panel,"focus")==row,"Search Enter focus");
        me.youngjibbit.module.Module module=row.getModule(),other=Pulsar.INSTANCE.getMm().getAllModules().stream().filter(m->m!=module).findFirst().get();int oldKey=module.getKey(),otherKey=other.getKey();
        other.setKey(org.lwjgl.input.Keyboard.KEY_F8);
        panel.key('k',org.lwjgl.input.Keyboard.KEY_K);require(get(panel,"binding")==row,"Keyboard bind capture");panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);require(module.getKey()==oldKey,"Bind cancel changed value");
        panel.click((int)row.getX()+20,(int)row.getY()+12,2);panel.key(' ',org.lwjgl.input.Keyboard.KEY_F8);require(module.getKey()==org.lwjgl.input.Keyboard.KEY_F8&&other.getKey()==org.lwjgl.input.Keyboard.KEY_F8,"Duplicate key deleted another binding");
        panel.key('k',org.lwjgl.input.Keyboard.KEY_K);settle(panel);snapshot("14-keybind-conflict.png");panel.key(' ',org.lwjgl.input.Keyboard.KEY_DELETE);require(module.getKey()==0,"Remove keybind");module.setKey(oldKey);other.setKey(otherKey);
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_RIGHT);settle(panel);panel.key(' ',org.lwjgl.input.Keyboard.KEY_TAB);
        require(get(panel,"focus") instanceof ClickSlider,"Tab into settings");ClickSlider slider=(ClickSlider)get(panel,"focus");double original=slider.getValue().getValue();
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);panel.type("3.25");panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);require(slider.getValue().getValue()==3.25&&!slider.isEditing(),"Exact numeric commit");
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_LEFT);double adjusted=slider.getValue().getValue();require(adjusted<3.25,"Slider arrow input");
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);panel.type("-");panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);require(slider.isEditing()&&slider.getValue().getValue()==adjusted,"Invalid numeric input committed");panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);panel.type("2.75");settle(panel);snapshot("15-number-edit.png");panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);require(slider.getValue().getValue()==adjusted,"Numeric cancel changed value");
        // Keyboard activation of a checkbox and dropdown uses their visible GUI positions.
        List<Component.Sub<?>> subs=row.focusableValues();ClickCombo combo=control(subs,"sort_mode",ClickCombo.class);
        String previousMode=combo.getValue().getValue();
        int comboSteps=subs.indexOf(combo)-subs.indexOf(slider);
        require(comboSteps>0,"Combo follows the first numeric setting");
        for(int i=0;i<comboSteps;i++)panel.key(' ',org.lwjgl.input.Keyboard.KEY_TAB);
        require(get(panel,"focus")==combo,"Combo keyboard focus/reveal");panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);panel.key(' ',org.lwjgl.input.Keyboard.KEY_RIGHT);
        require(!combo.getValue().getValue().equals(previousMode),"Combo keyboard choice");panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);require(!combo.isOpen(),"Dropdown Escape");combo.getValue().setValue(previousMode);
        ClickCheckBox checkbox=control(subs,"players",ClickCheckBox.class);boolean oldBoolean=checkbox.getValue().getValue();
        int checkboxSteps=subs.indexOf(checkbox)-subs.indexOf(combo);require(checkboxSteps>0,"Checkbox follows sort setting");
        for(int i=0;i<checkboxSteps;i++)panel.key(' ',org.lwjgl.input.Keyboard.KEY_TAB);
        require(get(panel,"focus")==checkbox,"Checkbox keyboard focus/reveal");panel.key(' ',org.lwjgl.input.Keyboard.KEY_SPACE);require(checkbox.getValue().getValue()!=oldBoolean,"Checkbox keyboard activation");checkbox.getValue().setValue(oldBoolean);
        for(int i=0;i<subs.indexOf(checkbox)-subs.indexOf(slider);i++)panel.key(' ',org.lwjgl.input.Keyboard.KEY_UP);require(get(panel,"focus")==slider,"Reverse setting navigation");
        // Focus is attached to the control identity, and falls back to its row when collapsed.
        set(panel,"targetScrollOffset",-30f);set(panel,"scrollOffset",-30f);settle(panel);require(get(panel,"focus")==slider,"Scroll lost focus identity");
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_UP);require(get(panel,"focus")==row,"Up focus/reveal");panel.key(' ',org.lwjgl.input.Keyboard.KEY_LEFT);settle(panel);require(!row.isOpen(),"Keyboard collapse");panel.key(' ',org.lwjgl.input.Keyboard.KEY_RIGHT);settle(panel);
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_TAB);require(get(panel,"focus")==slider,"Reopen focus order");panel.shift=true;panel.key(' ',org.lwjgl.input.Keyboard.KEY_TAB);panel.shift=false;require(get(panel,"focus")==row,"Reverse Tab");panel.key(' ',org.lwjgl.input.Keyboard.KEY_TAB);
        // Tooltip uses the actual delayed hover entry, with no synthetic wall-clock sleep.
        frame(panel,(int)row.getX()+20,(int)row.getY()+12);require(get(panel,"hoverTarget")==row,"Hover target");set(panel,"hoverStarted",System.nanoTime()-600_000_000L);frame(panel,(int)row.getX()+20,(int)row.getY()+12);snapshot("16-tooltip.png");
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_RETURN);panel.type("4.0");panel.onGuiClosed();require(!slider.isEditing()&&get(panel,"binding")==null&&get(panel,"focus")==null&&!(Boolean)get(panel,"dragging"),"Close left input capture");slider.getValue().setValue(original);
        panel.setWorldAndResolution(mc,640,480);settle(panel);panel.key('/',org.lwjgl.input.Keyboard.KEY_SLASH);panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);panel.key(' ',org.lwjgl.input.Keyboard.KEY_NEXT);settle(panel);require(((String)get(panel,"search")).isEmpty()&&get(panel,"focus")==null,"Category change retained capture");
        // Minecraft.runTick routes keyboard events to a current screen before its EventKey branch.
        require(mc.currentScreen==panel,"GUI keyboard gate context");mc.currentScreen=null;
        for(Map.Entry<me.youngjibbit.module.Module,Boolean> entry:toggled.entrySet())require(entry.getKey().isToggled()==entry.getValue(),"GUI typing/search/binding/Ctrl-T toggled a module");
        System.out.println("PASS: real GUI search/clear/empty, Tab focus, bind cancel/remove/conflict, exact/invalid/cancelled numbers, arrows, scroll/collapse/reopen/close, delayed tooltips");
    }

    private static void checkClipMath() {
        ClipRect parent = new ClipRect(10, 20, 100, 80);
        ClipRect child = parent.intersect(new ClipRect(50, 70, 100, 100));
        require(child.width == 60 && child.height == 30 && child.contains(50, 70) && !child.contains(110, 100), "Clip intersection");
        require(parent.intersect(new ClipRect(200, 200, 1, 1)).isEmpty(), "Empty clip");
    }

    private static void checkScissorRestore() {
        glEnable(GL_SCISSOR_TEST);
        glScissor(10, 10, 100, 100);
        try (GuiClip parent = GuiClip.push(new ClipRect(0, 0, 640, 480))) {
            try (GuiClip child = GuiClip.push(new ClipRect(0, 450, 20, 30))) {
                IntBuffer box = BufferUtils.createIntBuffer(16);
                glGetInteger(GL_SCISSOR_BOX, box);
                require(box.get(0) == 10 && box.get(2) == 30 && box.get(3) == 50, "Nested GL intersection");
            }
            IntBuffer box = BufferUtils.createIntBuffer(16);
            glGetInteger(GL_SCISSOR_BOX, box);
            require(box.get(0) == 10 && box.get(2) == 100, "Parent GL restoration");
        }
        require(glIsEnabled(GL_SCISSOR_TEST), "External scissor remains enabled");
        glDisable(GL_SCISSOR_TEST);
        try (GuiClip ignored = GuiClip.push(new ClipRect(0, 0, 10, 10))) {
            require(glIsEnabled(GL_SCISSOR_TEST), "Root clip enabled");
        }
        require(!glIsEnabled(GL_SCISSOR_TEST), "Root clip restores disabled state");
    }

    private static void checkNestedBatches() throws Exception {
        GuiShaderBloom.beginBatch();
        GuiShaderBloom.queueRoundedRect(0, 0, 10, 10, 2, -1);
        GuiShaderBloom.beginBatch();
        GuiShaderBloom.renderRect(20, 0, 10, 10, 2, -1, 1, 1);
        GuiShaderBloom.flushBatch(1, 1);
        require(GuiShaderBloom.isBatching(), "Nested flush must preserve parent batch");
        require(((List<?>) getStatic(GuiShaderBloom.class, "queuedRects")).size() == 2, "Nested commands preserved");
        GuiShaderBloom.flushBatch(2, 2);
        require(!GuiShaderBloom.isBatching(), "Batch closed");
    }

    private static <T extends Component.Sub<?>> T control(List<Component.Sub<?>> values,String id,Class<T> type) {
        for(Component.Sub<?> value:values) {
            me.youngjibbit.value.Value<?> setting=(me.youngjibbit.value.Value<?>)value.getValue();
            if(setting.getId().equals(id)) {
                require(type.isInstance(value) && setting.isVisible(),"Expected visible control "+id+" of type "+type.getSimpleName());
                return type.cast(value);
            }
        }
        throw new AssertionError("Missing control: "+id);
    }

    private static ClickCheckBox lastVisibleCheckbox(List<Component.Sub<?>> values) {
        for(int i=values.size()-1;i>=0;i--) if(values.get(i) instanceof ClickCheckBox) {
            ClickCheckBox checkbox=(ClickCheckBox)values.get(i);
            if(checkbox.getValue().isVisible()) return checkbox;
        }
        throw new AssertionError("Missing visible checkbox for clipping/scroll regression");
    }

    private static void checkLabelFit() {
        TTFFontRenderer font=ClickControlStyle.font();
        require("Range".equals(ClickControlStyle.fit(font,"Range",font.getWidth("Range"))),"Short labels retain their text");
        String fitted=ClickControlStyle.fit(font,"A long setting label that cannot cover its value",60);
        require(fitted.endsWith("...") && font.getWidth(fitted)<=60,"Long labels fit the available space");
        require(ClickControlStyle.fit(font,"Range",0).isEmpty(),"No label space must stay empty");
    }

    private static void checkControls(Panel panel) throws Exception {
        ClickModule module = ((List<ClickModule>) get(panel, "modules")).get(0);
        List<Component.Sub<?>> values = (List<Component.Sub<?>>) get(module, "values");
        ClickSlider slider = control(values,"range",ClickSlider.class);
        me.youngjibbit.value.impl.SliderValue sliderValue = slider.getValue();
        int sliderX = (int) (methodNumber(slider, "trackX") + 2);
        int sliderY = (int) (slider.getY() + 11);
        double before = sliderValue.getValue();
        panel.click(sliderX, sliderY, 1);
        require(sliderValue.getValue() == before, "Right click must not drag slider");
        slider.cancelInput();
        panel.click((int)(methodNumber(slider,"valueX")+4),sliderY,0);
        require(slider.isEditing() && sliderValue.getValue()==before,"Left click numeric badge begins exact entry without changing value");
        panel.key(' ',org.lwjgl.input.Keyboard.KEY_ESCAPE);
        panel.click(sliderX, sliderY, 0);
        panel.release();
        require(sliderValue.getValue() < before, "Visible slider input");
        ClickCombo combo = control(values,"sort_mode",ClickCombo.class);
        panel.click((int) (methodNumber(combo,"fieldX") + 8), (int) combo.getY() + 10, 0);
        settle(panel);
        snapshot("04-combo.png");
        panel.click((int) combo.getX() + 25, (int) combo.getY() + 24 + 30, 0);
        require(combo.getValue().getValue().equals("Health"), "Visible combo option input");
        settle(panel);
        ClickCheckBox checkbox = control(values,"players",ClickCheckBox.class);
        boolean beforeCheck = checkbox.getValue().getValue();
        panel.click((int) (methodNumber(checkbox,"toggleX") + 12), (int) checkbox.getY() + 11, 0);
        require(checkbox.getValue().getValue() != beforeCheck, "Visible checkbox input");
        // Reduced Motion must put the knob in its new position on the first draw.
        me.youngjibbit.module.impl.client.ClientSettings settings=me.youngjibbit.module.impl.client.ClientSettings.current();
        boolean oldReduced=settings.reducedMotion.getValue(), checked=checkbox.getValue().getValue();
        try {
            settings.reducedMotion.setValue(true);
            checkbox.getValue().setValue(false);frame(panel,-1,-1);
            require(brightPixel(methodNumber(checkbox,"toggleX")+6,checkbox.getY()+12),"Reduced Motion off knob immediately at left");
            panel.click((int)(methodNumber(checkbox,"toggleX")+12),(int)checkbox.getY()+12,0);frame(panel,-1,-1);
            require(brightPixel(methodNumber(checkbox,"toggleX")+18,checkbox.getY()+12),"Reduced Motion on knob immediately at right");
        } finally {settings.reducedMotion.setValue(oldReduced);checkbox.getValue().setValue(checked);}
        // The bottom checkbox extends past the viewport; clicking that offscreen position must do nothing.
        float originalHeight = number(panel, "height");
        set(panel, "height", 280f);
        settle(panel);
        ClickCheckBox hidden = lastVisibleCheckbox(values);
        boolean hiddenBefore = hidden.getValue().getValue();
        require(!hidden.getClip().contains((int)(methodNumber(hidden,"toggleX")+12),(int)hidden.getY()+11),"Clipping regression must target a point outside the viewport");
        panel.click((int)(methodNumber(hidden,"toggleX")+12), (int) hidden.getY() + 11, 0);
        require(hidden.getValue().getValue() == hiddenBefore, "Clipped control must reject input");
        set(panel, "height", originalHeight);
        settle(panel);
    }

    private static boolean brightPixel(float x,float y) {
        int scale=new ScaledResolution(mc).getScaleFactor();
        ByteBuffer pixel=BufferUtils.createByteBuffer(4);
        glReadPixels((int)(x*scale),mc.displayHeight-(int)(y*scale),1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
        return (pixel.get(0)&255)>180&&(pixel.get(1)&255)>180&&(pixel.get(2)&255)>180;
    }

    private static void checkDarkPanel(Panel panel) throws Exception {
        int scale = new ScaledResolution(mc).getScaleFactor();
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        glReadPixels((int) ((number(panel, "x") + number(panel, "width") - 30) * scale),
                mc.displayHeight - (int) ((number(panel, "y") + number(panel, "height") - 30) * scale),
                1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        require((pixel.get(0) & 255) < 45 && (pixel.get(1) & 255) < 45 && (pixel.get(2) & 255) < 45,
                "Window interior must render dark after bloom/blur");
    }

    private static void checkScroll(Panel panel, String prefix) throws Exception {
        set(panel, "targetScrollOffset", -100000f);
        settle(panel);
        float max = number(panel, "maxScroll");
        require(Math.abs(number(panel, "scrollOffset") + max) < 1, "Bottom scroll clamp");
        snapshot(prefix + "-scroll-bottom.png");
        ClickModule first = ((List<ClickModule>) get(panel, "modules")).get(0);
        List<Component.Sub<?>> values = (List<Component.Sub<?>>) get(first, "values");
        ClickCheckBox checkbox = lastVisibleCheckbox(values);
        boolean before = checkbox.getValue().getValue();
        // New trailing controls can put this checkbox above a short viewport at the bottom clamp.
        // Exercise scrolling to its actual geometry, then retain the same real input assertion.
        if(!checkbox.getClip().contains((int)(methodNumber(checkbox,"toggleX")+12),(int)checkbox.getY()+11)) {
            ClipRect viewport=(ClipRect)get(panel,"viewport");
            float offset=viewport.y+viewport.height/2-checkbox.getY()-12;
            set(panel,"targetScrollOffset",Math.max(-max,Math.min(0,number(panel,"scrollOffset")+offset)));
            settle(panel);
        }
        require(checkbox.getClip().contains((int)(methodNumber(checkbox,"toggleX")+12),(int)checkbox.getY()+11),"Scroll regression must reveal the control before input");
        panel.click((int) (methodNumber(checkbox,"toggleX") + 12), (int) checkbox.getY() + 11, 0);
        require(checkbox.getValue().getValue() != before, "Checkbox input after scrolling");
        set(panel, "targetScrollOffset", 100000f);
        settle(panel);
        require(Math.abs(number(panel, "scrollOffset")) < 1, "Top scroll clamp");
        snapshot(prefix + "-scroll-top.png");
    }

    private static void checkCollapse(Panel panel) throws Exception {
        ClickModule module = ((List<ClickModule>) get(panel, "modules")).get(0);
        List<Component.Sub<?>> values = (List<Component.Sub<?>>) get(module, "values");
        ClickSlider slider = control(values,"range",ClickSlider.class);
        double before = slider.getValue().getValue();
        panel.click((int) module.getX() + 12, (int) module.getY() + 10, 1);
        panel.click((int) (methodNumber(slider,"trackX") + methodNumber(slider,"trackWidth") - 2), (int) slider.getY() + 11, 0);
        require(slider.getValue().getValue() == before, "Closing settings reject input during animation");
        settle(panel);
        require(module.getHeight() == 24, "Collapsed module height");
        snapshot("06-collapsed.png");
        panel.click((int) module.getX() + 12, (int) module.getY() + 10, 1);
        settle(panel);
    }

    private static void checkDragResize(Panel panel) throws Exception {
        float oldWidth = number(panel, "width");
        panel.click((int) (number(panel, "x") + oldWidth - 12), (int) number(panel, "y") + 10, 0);
        frame(panel, (int) (number(panel, "x") + oldWidth + 35), (int) number(panel, "y") + 35);
        panel.release();
        require(number(panel, "width") > oldWidth, "Resize grip");
        panel.click((int) number(panel, "x") + 20, (int) number(panel, "y") + 10, 0);
        frame(panel, -100, -100);
        panel.release();
        checkWindowBounds(panel);
        // Category hitbox must follow the moved window and selected row must settle at the same offset.
        panel.click((int) number(panel, "x") + 40, categoryY(panel, 2), 0);
        settle(panel);
        require(get(panel, "selectedCategory") == me.youngjibbit.module.Category.Player, "Category input after drag");
        require(Math.abs(number(panel, "selectionOffset") - 2 * methodNumber(panel,"categoryStep")) < 1, "Category selection alignment");
    }

    private static void checkTheme(Panel panel) throws Exception {
        Theme.setDefaultTheme(Theme.Neon);
        settle(panel);
        snapshot("07-theme-neon-resized.png");
        Theme.setDefaultTheme(Theme.SynthWave);
    }

    private static void checkWindowBounds(Panel panel) throws Exception {
        require(number(panel, "x") >= 8 && number(panel, "y") >= 8, "Window minimum bounds");
        require(number(panel, "x") + number(panel, "width") <= ((net.minecraft.client.gui.GuiScreen) panel).width - 8.0f, "Window right bound");
        require(number(panel, "y") + number(panel, "height") <= ((net.minecraft.client.gui.GuiScreen) panel).height - 8.0f, "Window bottom bound");
    }

    private static void checkBloomReuse() throws Exception {
        Object original = getStatic(KawaseBloom.class, "framebuffer");
        List<?> buffers = (List<?>) getStatic(KawaseBloom.class, "framebufferList");
        Object level = buffers.get(1);
        GuiShaderBloom.renderRect(10, 10, 20, 20, 4, -1, 1, 1);
        GuiShaderBloom.renderRect(10, 10, 20, 20, 4, -1, 2, 2);
        require(original == getStatic(KawaseBloom.class, "framebuffer") && level == buffers.get(1), "Hotbar bloom pool reuse");
    }

    private static void resize(Panel panel, int width, int height, int scale) throws Exception {
        mc.displayWidth = width;
        mc.displayHeight = height;
        mc.gameSettings.guiScale = scale;
        mc.getFramebuffer().createBindFramebuffer(width, height);
        ScaledResolution resolution = new ScaledResolution(mc);
        panel.setWorldAndResolution(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    private static void settle(Panel panel) throws Exception {
        for (int i = 0; i < 35; i++) { frame(panel, -1, -1); Thread.sleep(10); }
    }

    private static void frame(Panel panel, int mouseX, int mouseY) {
        mc.getFramebuffer().bindFramebuffer(true);
        glDisable(GL_SCISSOR_TEST);
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.color(1, 1, 1, 1);
        glClearColor(0.19f, 0.27f, 0.31f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        ScaledResolution resolution = new ScaledResolution(mc);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0, -1000, 1000);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        panel.drawScreen(mouseX, mouseY, 0);
        glFinish();
    }

    private static void snapshot(String name) throws Exception {
        mc.getFramebuffer().bindFramebuffer(false);
        ByteBuffer pixels = BufferUtils.createByteBuffer(mc.displayWidth * mc.displayHeight * 4);
        glReadPixels(0, 0, mc.displayWidth, mc.displayHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(mc.displayWidth, mc.displayHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < mc.displayHeight; y++) for (int x = 0; x < mc.displayWidth; x++) {
            int index = (y * mc.displayWidth + x) * 4;
            image.setRGB(x, mc.displayHeight - y - 1, 0xFF000000 | (pixels.get(index) & 255) << 16 |
                    (pixels.get(index + 1) & 255) << 8 | (pixels.get(index + 2) & 255));
        }
        ImageIO.write(image, "png", new File(OUTPUT, name));
    }

    // Load only the production font faces/sizes used by ClickGUI, avoiding the full client's font atlas.
    private static FontManager minimalFonts() throws Exception {
        return minimalFonts(new String[0]);
    }

    static FontManager minimalFonts(String... additionalKeys) throws Exception {
        FontManager manager = new FontManager();
        HashMap<String, TTFFontRenderer> fonts = new HashMap<>();
        ConcurrentLinkedQueue<TextureData> queue = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        String[] keys = {"Poppins-Regular 18", "Poppins-Regular 20", "Poppins-Medium 18", "Product-Sans-Regular 20",
                "Product-Sans-Bold 28", "icon1 21", "icon1 23", "icon 21"};
        java.util.List<String> requested = new ArrayList<>(Arrays.asList(keys));
        requested.addAll(Arrays.asList(additionalKeys));
        for (String key : requested) {
            int split = key.lastIndexOf(' ');
            String face = key.substring(0, split).replace("SFProText", "SFProRounded");
            Font font = Font.createFont(Font.TRUETYPE_FONT, ClickGuiRenderCheck.class.getResourceAsStream(
                    "/assets/minecraft/" + face + ".ttf")).deriveFont(Float.parseFloat(key.substring(split + 1)));
            fonts.put(key, new TTFFontRenderer(executor, queue, font));
        }
        executor.shutdown();
        require(executor.awaitTermination(60, TimeUnit.SECONDS), "Font generation timed out");
        require(queue.size() == requested.size() * 3 * 256, "Incomplete font generation must fail the render check");
        for (TextureData texture : queue) {
            GlStateManager.bindTexture(texture.getTextureId());
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, texture.getWidth(), texture.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, texture.getBuffer());
        }
        set(manager, "fonts", fonts);
        set(manager, "defaultFont", fonts.get("Poppins-Regular 18"));
        return manager;
    }

    private static float methodNumber(Object object,String name)throws Exception {
        Class<?> type=object.getClass();
        while(type!=null){try{java.lang.reflect.Method method=type.getDeclaredMethod(name);method.setAccessible(true);return ((Number)method.invoke(object)).floatValue();}catch(NoSuchMethodException ignored){type=type.getSuperclass();}}
        throw new NoSuchMethodException(name);
    }
    private static int categoryY(Panel panel,int index)throws Exception {
        return (int)(number(panel,"y")+((Number)getStatic(ClickGui.class,"HEADER")).floatValue()+4+index*methodNumber(panel,"categoryStep")+6);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object get(Object object, String name) throws Exception { return field(object.getClass(), name).get(object); }
    private static float number(Object object, String name) throws Exception { return ((Number) get(object, name)).floatValue(); }
    private static Object getStatic(Class<?> type, String name) throws Exception { return field(type, name).get(null); }
    private static void set(Object object, String name, Object value) throws Exception { field(object.getClass(), name).set(object, value); }
    private static void setStatic(Class<?> type, String name, Object value) throws Exception { field(type, name).set(null, value); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
