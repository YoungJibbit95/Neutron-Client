package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.core.inventory.*;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.gui.inventory.TrashMenuScreen;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.player.InventoryManager;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Real registry, models, shader material and input handlers; isolated explicit rule file. */
public final class TrashMenuCheck {
    private static final class Screen extends TrashMenuScreen {
        boolean control; GuiScreen destination;
        Screen(GuiScreen parent, InventoryManager manager) { super(parent, manager); }
        @Override protected boolean ctrl() { return control; }
        @Override protected void open(GuiScreen screen) { destination = screen; }
        void key(char c, int key) throws Exception { keyTyped(c, key); }
        void press(ClipRect r) throws Exception { mouseClicked((int)(r.x + r.width / 2), (int)(r.y + r.height / 2), 0); }
        void release(ClipRect r) { mouseReleased((int)(r.x + r.width / 2), (int)(r.y + r.height / 2), 0); }
        void click(ClipRect r) throws Exception { press(r); release(r); }
        void shortcut(int key) throws Exception { control = true; key(' ', key); control = false; }
        void search(String query) throws Exception { shortcut(Keyboard.KEY_F); key(' ', Keyboard.KEY_BACK); for (char c : query.toCharArray()) key(c, Keyboard.KEY_NONE); }
        @SuppressWarnings("unchecked") List<TrashItemCatalog.Entry> rows() throws Exception {
            return (List<TrashItemCatalog.Entry>) SessionInfoRenderCheck.get(this, "visible");
        }
    }
    private static final class NoInventoryActions extends PlayerControllerMP {
        int clicks;
        NoInventoryActions(Minecraft mc) { super(mc, null); }
        @Override public ItemStack windowClick(int window, int slot, int button, int mode, EntityPlayer player) {
            clicks++; throw new AssertionError("Trash editor performed an inventory action");
        }
    }
    private static void require(boolean condition, String message) { SessionInfoRenderCheck.require(condition, message); }
    private static void draw(Screen screen, int mx, int my) {
        SessionInfoRenderCheck.backdrop(); screen.drawScreen(mx, my, 0);
    }
    public static void run() throws Exception {
        Minecraft mc = SessionInfoRenderCheck.mc; Bootstrap.register();
        ModuleManager oldModules = Pulsar.INSTANCE.getMm(); PlayerControllerMP oldController = mc.playerController;
        GuiScreen oldScreen = mc.currentScreen; File output = new File("trash-preview"); output.mkdirs();
        Path policy = Files.createTempDirectory(output.toPath(), "fixture-").resolve("inventory-trash.json");
        SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", new ModuleManager());
        NoInventoryActions actions = new NoInventoryActions(mc); mc.playerController = actions;
        // Production item models and atlas are required: the fixture must not silently omit icons.
        SessionInfoRenderCheck.set(mc, "mcResourcePackRepository", new ResourcePackRepository(new File(output, "packs"),
                new File(output, "server-packs"), new DefaultResourcePack(Collections.emptyMap()), new IMetadataSerializer(), mc.gameSettings));
        TextureManager textures = new TextureManager(mc.getResourceManager()); SessionInfoRenderCheck.set(mc, "renderEngine", textures);
        mc.fontRendererObj = new FontRenderer(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), textures, false);
        mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
        TextureMap atlas = new TextureMap("textures"); atlas.setMipmapLevels(0); SessionInfoRenderCheck.set(mc, "textureMapBlocks", atlas);
        textures.loadTickableTexture(TextureMap.locationBlocksTexture, atlas);
        ModelManager models = new ModelManager(atlas); SessionInfoRenderCheck.set(mc, "modelManager", models); models.onResourceManagerReload(mc.getResourceManager());
        RenderItem renderer = new RenderItem(textures, models); SessionInfoRenderCheck.set(mc, "renderItem", renderer); renderer.onResourceManagerReload(mc.getResourceManager());
        TrashItemStore store = new TrashItemStore(policy); InventoryManager manager = new InventoryManager(store);
        GuiScreen parent = new GuiScreen() { }; Screen screen = new Screen(parent, manager); mc.currentScreen = screen;
        try {
            mc.displayWidth = 1200; mc.displayHeight = 800; mc.gameSettings.guiScale = 1;
            mc.getFramebuffer().createBindFramebuffer(1200, 800); screen.setWorldAndResolution(mc, 1200, 800);
            require(screen.rows().size() > 400, "Live registry catalog is incomplete");
            draw(screen, -1, -1); ImageIO.write(SessionInfoRenderCheck.capture(), "png", new File(output, "trash-catalog-wide.png"));
            screen.search("minecraft:wool"); require(screen.rows().size() == 16, "Exact item-ID search lost wool variants");
            ClipRect first = screen.entryBounds(0); String key = screen.rows().get(0).key;
            screen.click(first); require(store.contains(key), "Explicit variant toggle did not persist");
            require(new TrashItemStore(policy).contains(key), "Saved policy cannot be reopened");
            draw(screen, (int)first.x + 10, (int)first.y + 12);
            ImageIO.write(SessionInfoRenderCheck.capture(), "png", new File(output, "trash-search-variants.png"));
            int count = store.keys().size(); screen.press(first); screen.search("minecraft:diamond_sword"); screen.release(first);
            require(store.keys().size() == count, "A stale press toggled a newly filtered row");
            screen.press(screen.entryBounds(0)); screen.setWorldAndResolution(mc, 1200, 800); screen.release(screen.entryBounds(0));
            require(store.keys().size() == count, "Resize preserved a stale press");
            screen.search(""); screen.shortcut(Keyboard.KEY_L); require(screen.rows().size() == 1, "Listed-only filter");
            screen.key(' ', Keyboard.KEY_TAB); screen.key(' ', Keyboard.KEY_SPACE);
            require(store.keys().isEmpty() && screen.rows().isEmpty(), "Keyboard toggle/filter refresh");
            screen.shortcut(Keyboard.KEY_Z); require(store.contains(key) && screen.rows().size() == 1, "Undo did not restore exact rule");
            byte[] saved = Files.readAllBytes(policy); Files.write(policy, "{".getBytes(StandardCharsets.UTF_8));
            screen.key(' ', Keyboard.KEY_F5); require(!store.lastError().isEmpty(), "Read failure was not exposed");
            screen.click(screen.entryBounds(0)); require(store.contains(key), "Failed persistence changed active rule");
            draw(screen, -1, -1); ImageIO.write(SessionInfoRenderCheck.capture(), "png", new File(output, "trash-read-failure.png"));
            Files.write(policy, saved); screen.key(' ', Keyboard.KEY_F5); require(store.lastError().isEmpty(), "Reload did not recover");
            screen.shortcut(Keyboard.KEY_L); screen.search(""); screen.key(' ', Keyboard.KEY_TAB); screen.key(' ', Keyboard.KEY_END);
            require((float)SessionInfoRenderCheck.get(screen, "targetScroll") > 0, "Keyboard end cannot reach the catalog tail");
            screen.search("minecraft:potion");
            for (int[] size : new int[][]{{1100, 720, 2}, {640, 480, 1}, {1001, 701, 3}, {580, 342, 1}}) {
                mc.displayWidth = size[0]; mc.displayHeight = size[1]; mc.gameSettings.guiScale = size[2];
                mc.getFramebuffer().createBindFramebuffer(size[0], size[1]); ScaledResolution scaled = new ScaledResolution(mc);
                screen.setWorldAndResolution(mc, scaled.getScaledWidth(), scaled.getScaledHeight());
                for (ClipRect r : new ClipRect[]{screen.searchBounds(), screen.listBounds(), screen.actionBounds(0), screen.actionBounds(1)})
                    require(r.x >= 0 && r.y >= 0 && r.x + r.width <= scaled.getScaledWidth() && r.y + r.height <= scaled.getScaledHeight(), "Control escaped resized viewport");
                require(screen.listBounds().y + screen.listBounds().height < screen.actionBounds(0).y, "Catalog overlaps toolbar");
                draw(screen, -1, -1);
                if (size[2] == 3) ImageIO.write(SessionInfoRenderCheck.capture(), "png", new File(output, "trash-compact.png"));
            }
            renderer.zLevel = 17; GlStateManager.depthFunc(GL_GREATER); GlStateManager.depthMask(false);
            screen.drawScreen(-1, -1, 0);
            require(renderer.zLevel == 17 && glGetInteger(GL_DEPTH_FUNC) == GL_GREATER && !glGetBoolean(GL_DEPTH_WRITEMASK), "Item draw leaked render state");
            GlStateManager.depthFunc(GL_LEQUAL); GlStateManager.depthMask(true);
            for (int i = 0; i < 4; i++) draw(screen, -1, -1);
            RenderDiagnostics.enabled = true; RenderDiagnostics.reset();
            for (int i = 0; i < 6; i++) draw(screen, -1, -1);
            require(RenderDiagnostics.framebufferCreates == 0 && RenderDiagnostics.programCreates == 0, "Steady menu allocates render resources");
            RenderDiagnostics.enabled = false;
            manager.setToggled(true); Pulsar.BUS.post(new EventUpdate());
            require(actions.clicks == 0, "Menu caused an inventory click"); manager.setToggled(false);
            screen.key(' ', Keyboard.KEY_ESCAPE); screen.key(' ', Keyboard.KEY_ESCAPE); require(screen.destination == parent, "Escape cannot return to parent");
            require(glGetError() == GL_NO_ERROR, "Trash menu GL error");
            System.out.println("PASS: Trash registry/icons/variants, exact persistent rules/undo/reload errors, keyboard/search/filter, stale press/resize cancellation, compact layouts, GL restoration, cached resources, no inventory actions");
        } finally {
            RenderDiagnostics.enabled = false; manager.setToggled(false); screen.onGuiClosed();
            mc.currentScreen = oldScreen; mc.playerController = oldController; SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", oldModules); UiEffects.invalidate();
        }
    }
}
