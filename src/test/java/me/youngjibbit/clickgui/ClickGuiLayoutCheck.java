package me.youngjibbit.clickgui;

import static org.lwjgl.opengl.GL11.*;

import com.mojang.authlib.properties.PropertyMap;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.util.*;
import javax.imageio.ImageIO;
import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.component.impl.ClickModule;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.RenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;

/**
 * Real Pbuffer GUI path; no client bootstrap, world or account. Frame geometry is read from the
 * actual layout.
 */
public final class ClickGuiLayoutCheck {
  static class Panel extends ClickGui {
    int themes;

    @Override
    protected void openThemeEditor() {
      themes++;
    }

    void click(float x, float y, int b) throws Exception {
      mouseClicked((int) x, (int) y, b);
    }

    void release() {
      mouseReleased(0, 0, 0);
    }

    void key(char c, int k) throws Exception {
      keyTyped(c, k);
    }
  }

  public static void main(String[] args) throws Exception {
    File out = new File("frame-preview");
    out.mkdirs();
    System.setProperty("user.home", new File(out, "home").getAbsolutePath());
    File data = java.nio.file.Files.createTempDirectory(out.toPath(), "fixture-").toFile();
    try (PbufferTestContext context = new PbufferTestContext()) {
      Minecraft mc =
          new Minecraft(
              new GameConfiguration(
                  new GameConfiguration.UserInformation(
                      new Session(
                          "FramePreview", "00000000000000000000000000000000", "0", "legacy"),
                      new PropertyMap(),
                      new PropertyMap(),
                      Proxy.NO_PROXY),
                  new GameConfiguration.DisplayInformation(1280, 960, false, false),
                  new GameConfiguration.FolderInformation(
                      data, new File(data, "packs"), new File(data, "assets"), null),
                  new GameConfiguration.GameInformation(false, "Frame layout check"),
                  new GameConfiguration.ServerInformation(null, 0)));
      context.initialize(mc, data);
      SimpleReloadableResourceManager resources =
          new SimpleReloadableResourceManager(new IMetadataSerializer());
      resources.reloadResources(
          Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
      set(mc, "mcResourceManager", resources);
      set(mc, "mcLanguageManager", new LanguageManager(new IMetadataSerializer(), "en_US"));
      set(mc, "framebufferMc", new Framebuffer(mc.displayWidth, mc.displayHeight, true));
      Method fonts = ClickGuiRenderCheck.class.getDeclaredMethod("minimalFonts");
      fonts.setAccessible(true);
      Pulsar.INSTANCE.fontManager = (FontManager) fonts.invoke(null);
      set(Pulsar.INSTANCE, "mm", new ModuleManager());
      ClientSettings.current().surface.setValue("Standard");
      ClientSettings.current().reducedMotion.setValue(true);
      Theme.setDefaultTheme(Theme.SynthWave);
      Panel panel = new Panel();
      int[][] sizes = {{1280, 960, 2}, {640, 480, 2}, {320, 224, 1}, {1101, 733, 3}};
      for (int n = 0; n < sizes.length; n++) {
        mc.displayWidth = sizes[n][0];
        mc.displayHeight = sizes[n][1];
        mc.gameSettings.guiScale = sizes[n][2];
        mc.getFramebuffer().createBindFramebuffer(mc.displayWidth, mc.displayHeight);
        ScaledResolution sr = new ScaledResolution(mc);
        panel.setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
        frame(panel);
        ClipRect viewport = (ClipRect) get(panel, "viewport"),
            search = panel.searchBounds(),
            themes = panel.themeBounds();
        float x = number(panel, "x"),
            y = number(panel, "y"),
            w = number(panel, "width"),
            h = number(panel, "height");
        require(search.height == 24 && themes.height == 24, "Toolbar hit targets");
        require(
            search.x + search.width < themes.x && themes.x + themes.width < x + w - 26,
            "Toolbar overlap");
        require(
            viewport.y >= y + 64 && viewport.y + viewport.height <= y + h - 20,
            "Content overlaps heading/footer");
        for (ClipRect r : new ClipRect[] {viewport, search, themes})
          require(
              r.width > 0
                  && r.height > 0
                  && r.x >= 0
                  && r.y >= 0
                  && r.x + r.width <= sr.getScaledWidth()
                  && r.y + r.height <= sr.getScaledHeight(),
              "Unreachable frame region");
        if (n == 0) {
          for (String style :
              new String[] {"Standard", "Clear", "Gradient", "Blur", "Liquid Glass"}) {
            ClientSettings.current().surface.setValue(style);
            frame(panel);
            save(mc, new File(out, "material-" + style.replace(' ', '-') + ".png"));
          }
          ClientSettings.current().surface.setValue("Standard");
        }
        panel.click(themes.x + themes.width / 2, themes.y + themes.height / 2, 0);
        require(panel.themes == n + 1, "Theme target routing");
        panel.click(search.x + 6, search.y + 12, 0);
        panel.key('z', Keyboard.KEY_NONE);
        panel.key('z', Keyboard.KEY_NONE);
        frame(panel);
        require(((List<?>) get(panel, "modules")).isEmpty(), "Search empty state");
        save(mc, new File(out, "empty-" + n + ".png"));
        panel.key(' ', Keyboard.KEY_ESCAPE);
        frame(panel);
        require(!((List<?>) get(panel, "modules")).isEmpty(), "Escape search recovery");
        List<ClickModule> rows = (List<ClickModule>) get(panel, "modules");
        ClickModule row = rows.get(0);
        boolean before = row.getModule().isToggled();
        panel.click(row.getX() + 14, row.getY() + 12, 0);
        require(row.getModule().isToggled() != before, "Actual module header activation");
        panel.click(row.getX() + 14, row.getY() + 12, 1);
        frame(panel);
        save(mc, new File(out, "settings-" + n + ".png"));
        if (n == 0) {
          set(panel, "targetScrollOffset", -100000f);
          frame(panel);
          require(
              number(panel, "scrollOffset") >= -number(panel, "maxScroll"),
              "Scroll exceeded content");
          save(mc, new File(out, "scroll.png"));
        }
        float step = Math.min(24, (h - 36 - 20 - 8) / 8);
        panel.click(x + 40, y + 40 + step + Math.min(5, step / 2), 0);
        frame(panel);
        require(
            get(panel, "selectedCategory") == me.youngjibbit.module.Category.Movement,
            "Category hit target");
        save(mc, new File(out, "frame-" + n + ".png"));
        panel.key('/', Keyboard.KEY_NONE);
        require((Boolean) get(panel, "searchFocus"), "Search shortcut");
        panel.key(' ', Keyboard.KEY_TAB);
        require(get(panel, "focus") != null, "Keyboard focus traversal");
        panel.release();
        if (n == 0) {
          float originalX = number(panel, "x"), originalY = number(panel, "y");
          panel.click(originalX + 60, originalY + 18, 0);
          panel.drawScreen((int) originalX + 68, (int) originalY + 26, 0);
          panel.release();
          require(
              Math.abs(number(panel, "x") - originalX - 8) < 1
                  && Math.abs(number(panel, "y") - originalY - 8) < 1,
              "Toolbar drag stopped working");
          float rx = number(panel, "x"),
              ry = number(panel, "y"),
              rw = number(panel, "width"),
              rh = number(panel, "height");
          panel.click(rx + rw - 12, ry + 18, 0);
          panel.drawScreen((int) (rx + rw), (int) ry + 26, 0);
          panel.release();
          require(
              number(panel, "width") > rw && number(panel, "height") > rh,
              "Resize stopped working");
        }
      }
      float oldX = number(panel, "x"), oldY = number(panel, "y");
      panel.click(oldX + 60, oldY + 18, 0);
      panel.drawScreen((int) oldX + 64, (int) oldY + 22, 0);
      panel.release();
      require(!(Boolean) get(panel, "dragging"), "Drag release");
      float x = number(panel, "x"), y = number(panel, "y"), w = number(panel, "width");
      panel.click(x + w - 12, y + 18, 0);
      require((Boolean) get(panel, "resizing"), "Resize target");
      panel.release();
      panel.onGuiClosed();
      require(glGetError() == GL_NO_ERROR, "Frame GL error");
      context.check();
    }
    System.out.println(
        "PASS: frame/header/footer geometry, 24px search/theme targets, empty/recovery, module activation/settings, categories, search/tab, drag/resize; 320x224 minimum viewport and GUI scale 2/3");
  }

  static void frame(Panel panel) {
    try (RenderState state = new RenderState()) {
      state.screenSpace();
      glClearColor(.025f, .03f, .045f, 1);
      glClear(GL_COLOR_BUFFER_BIT);
      panel.drawScreen(-1, -1, 0);
    }
  }

  static void save(Minecraft mc, File file) throws Exception {
    glFinish();
    ByteBuffer data = BufferUtils.createByteBuffer(mc.displayWidth * mc.displayHeight * 4);
    glReadPixels(0, 0, mc.displayWidth, mc.displayHeight, GL_RGBA, GL_UNSIGNED_BYTE, data);
    BufferedImage image =
        new BufferedImage(mc.displayWidth, mc.displayHeight, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < mc.displayHeight; y++)
      for (int x = 0; x < mc.displayWidth; x++) {
        int i = (y * mc.displayWidth + x) * 4;
        image.setRGB(
            x,
            mc.displayHeight - y - 1,
            (data.get(i) & 255) << 16 | (data.get(i + 1) & 255) << 8 | (data.get(i + 2) & 255));
      }
    ImageIO.write(image, "png", file);
  }

  static Field field(Object o, String name) throws Exception {
    for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass())
      try {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) {
      }
    throw new NoSuchFieldException(name);
  }

  static Object get(Object o, String name) throws Exception {
    return field(o, name).get(o);
  }

  static void set(Object o, String name, Object value) throws Exception {
    field(o, name).set(o, value);
  }

  static float number(Object o, String name) throws Exception {
    return ((Number) get(o, name)).floatValue();
  }

  static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
