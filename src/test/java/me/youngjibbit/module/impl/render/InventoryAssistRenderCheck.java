package me.youngjibbit.module.impl.render;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.inventory.InventoryAssistSession;
import me.youngjibbit.gui.inventory.InventoryAssistOverlay;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.util.ResourceLocation;
import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.lwjgl.opengl.GL11.*;

/** Real GuiContainer slot/overlay rendering in the existing Pbuffer. No controller or OS input action. */
public final class InventoryAssistRenderCheck {
    private static final class NoActions extends PlayerControllerMP {
        int clicks;
        NoActions(Minecraft mc) { super(mc, null); }
        @Override public ItemStack windowClick(int w, int s, int b, int mode, EntityPlayer player) {
            clicks++; throw new AssertionError("Rendering attempted an inventory action");
        }
        @Override public C0EPacketClickWindow windowClickGuarded(int w, int s, int b, int mode, EntityPlayer player,
                BooleanSupplier before, BooleanSupplier after) {
            clicks++; throw new AssertionError("Rendering attempted a guarded inventory action");
        }
    }
    /** Entity portrait needs a world renderer; this fixture keeps the native inventory surface and all slot drawing. */
    private static final class PlayerScreen extends GuiInventory {
        PlayerScreen(EntityPlayer player) { super(player); }
        @Override protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
            GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(inventoryBackground);
            drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        }
    }
    private static void require(boolean ok, String why) { SessionInfoRenderCheck.require(ok, why); }
    private static void resources(Minecraft mc, File out) throws Exception {
        SessionInfoRenderCheck.set(mc, "mcResourcePackRepository", new ResourcePackRepository(new File(out, "packs"),
                new File(out, "server-packs"), new DefaultResourcePack(Collections.emptyMap()), new IMetadataSerializer(), mc.gameSettings));
        TextureManager textures = new TextureManager(mc.getResourceManager()); SessionInfoRenderCheck.set(mc, "renderEngine", textures);
        mc.fontRendererObj = new FontRenderer(mc.gameSettings, new ResourceLocation("textures/font/ascii.png"), textures, false);
        mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
        TextureMap atlas = new TextureMap("textures"); atlas.setMipmapLevels(0); SessionInfoRenderCheck.set(mc, "textureMapBlocks", atlas);
        textures.loadTickableTexture(TextureMap.locationBlocksTexture, atlas);
        ModelManager models = new ModelManager(atlas); SessionInfoRenderCheck.set(mc, "modelManager", models); models.onResourceManagerReload(mc.getResourceManager());
        RenderItem renderer = new RenderItem(textures, models); SessionInfoRenderCheck.set(mc, "renderItem", renderer); renderer.onResourceManagerReload(mc.getResourceManager());
    }
    public static void run() throws Exception {
        Minecraft mc = SessionInfoRenderCheck.mc; Bootstrap.register();
        File out = new File("inventory-assist-preview"); out.mkdirs();
        JsonObject report = new JsonObject(); report.addProperty("format_version", 1); report.addProperty("status", "incomplete");
        report.addProperty("scope", "Real GuiContainer slots, item models, input revision and production assist overlay in Pbuffer/FBO; player portrait omitted. No world gameplay, OS input or server acceptance claim.");
        EntityPlayerSP oldPlayer = mc.thePlayer; WorldClient oldWorld = mc.theWorld;
        PlayerControllerMP oldController = mc.playerController; GuiScreen oldScreen = mc.currentScreen;
        RotationBehaviorHookTest.Player player = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
        player.inventory = new InventoryPlayer(player); player.capabilities = new PlayerCapabilities();
        player.worldObj = RotationBehaviorHookTest.bare(WorldClient.class);
        SessionInfoRenderCheck.set(player, "activePotionsMap", new HashMap<>());
        player.inventoryContainer = new ContainerPlayer(player.inventory, false, player); player.openContainer = player.inventoryContainer;
        NoActions actions = new NoActions(mc); InventoryAssistSession session = new InventoryAssistSession("Stealer");
        try {
            mc.thePlayer = player; mc.theWorld = (WorldClient)player.worldObj; mc.playerController = actions;
            resources(mc, out); Theme.setDefaultTheme(Theme.SynthWave);
            GuiContainer last = null;
            for (int kind = 0; kind < 4; kind++) {
                session.clear(); Arrays.fill(player.inventory.mainInventory, null);
                boolean playerInventory = kind == 2, compact = kind == 3;
                int size = kind == 0 ? 27 : 54;
                GuiContainer screen; int source, companion;
                if (playerInventory) {
                    player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
                    player.inventory.mainInventory[18] = new ItemStack(Items.golden_apple, 5);
                    player.inventory.mainInventory[20] = new ItemStack(Blocks.planks, 64);
                    screen = new PlayerScreen(player); source = 12; companion = 36;
                } else {
                    InventoryBasic storage = new InventoryBasic(size == 54 ? "Large Chest" : "Chest", false, size);
                    storage.setInventorySlotContents(0, new ItemStack(Items.diamond, 12));
                    storage.setInventorySlotContents(4, new ItemStack(Items.iron_ingot, 32));
                    storage.setInventorySlotContents(11, new ItemStack(Items.golden_apple, 4));
                    storage.setInventorySlotContents(size - 2, new ItemStack(Blocks.planks, 64));
                    screen = new GuiChest(player.inventory, storage); source = 4; companion = -1;
                }
                mc.displayWidth = compact ? 640 : 960; mc.displayHeight = compact ? 480 : 640; mc.gameSettings.guiScale = 2;
                mc.getFramebuffer().createBindFramebuffer(mc.displayWidth, mc.displayHeight);
                ScaledResolution scaled = new ScaledResolution(mc); mc.currentScreen = screen;
                screen.setWorldAndResolution(mc, scaled.getScaledWidth(), scaled.getScaledHeight());
                require(screen.inventoryAssistSlotVisible(source), "Source slot is not in the actual visible GUI");
                require(!session.ready(mc, playerInventory ? "Swap" : "Transfer", source, 0, playerInventory ? 2 : 1, companion, 100, 300), "Unrendered proposal was ready");
                InventoryAssistSession.Preview preview = screen.inventoryAssistPreview(); require(preview != null, "Preview was not installed");
                require(screen.inventoryAssistPresentedAt(preview.token) == Long.MIN_VALUE, "Update pretended to draw the preview");
                for (int frame = 0; frame < 3; frame++) { SessionInfoRenderCheck.backdrop(); screen.drawScreen(-1, -1, 0); }
                require(screen.inventoryAssistPresentedAt(preview.token) != Long.MIN_VALUE, "Real GuiContainer render omitted the overlay");
                String name = kind == 0 ? "assist-chest27.png" : kind == 1 ? "assist-chest54.png" : kind == 2 ? "assist-player.png" : "assist-compact54.png";
                ImageIO.write(SessionInfoRenderCheck.capture(), "png", new File(out, name));
                require(actions.clicks == 0 && player.inventory.getItemStack() == null, "Render or preview changed inventory/cursor");
                long token = preview.token; screen.setWorldAndResolution(mc, scaled.getScaledWidth(), scaled.getScaledHeight());
                require(screen.inventoryAssistPresentedAt(token) == Long.MIN_VALUE && screen.inventoryAssistPreview() == null, "Resize did not revoke preview");
                require(!session.ready(mc, playerInventory ? "Swap" : "Transfer", source, 0, playerInventory ? 2 : 1, companion, 100, 300), "Resize preserved old dwell time");
                last = screen;
            }
            InventoryAssistSession.Preview preview = last.inventoryAssistPreview();
            GlStateManager.depthFunc(GL_GREATER); GlStateManager.depthMask(false);
            InventoryAssistOverlay.draw(last, preview, mc.fontRendererObj);
            require(glGetInteger(GL_DEPTH_FUNC) == GL_GREATER && !glGetBoolean(GL_DEPTH_WRITEMASK), "Overlay leaked GL depth state");
            GlStateManager.depthFunc(GL_LEQUAL); GlStateManager.depthMask(true);
            for (int i = 0; i < 4; i++) InventoryAssistOverlay.draw(last, preview, mc.fontRendererObj);
            RenderDiagnostics.enabled = true; RenderDiagnostics.reset();
            for (int i = 0; i < 8; i++) InventoryAssistOverlay.draw(last, preview, mc.fontRendererObj);
            require(RenderDiagnostics.framebufferCreates == 0 && RenderDiagnostics.programCreates == 0, "Steady overlay allocates GPU resources");
            require(actions.clicks == 0 && glGetError() == GL_NO_ERROR, "Overlay action or GL error");
            report.addProperty("status", "passed"); report.addProperty("controller_actions", actions.clicks);
            report.addProperty("scenes", "27-slot chest, 54-slot chest, player inventory, compact 54-slot chest");
            report.addProperty("render_acknowledgement", "Only real GuiContainer drawing marks a preview presented");
            report.addProperty("input_checks", "Resize revokes the presented token; no OS input or controller operation is generated. Keyboard/touch/physical-input preemption is covered by InventoryAssistSessionTest.");
            report.addProperty("gl_state_restored", true); report.addProperty("resize_revokes_preview", true);
            report.addProperty("screenshots", 4); report.addProperty("steady_framebuffer_creates", RenderDiagnostics.framebufferCreates);
            report.addProperty("steady_program_creates", RenderDiagnostics.programCreates);
            System.out.println("PASS: visible inventory assistance, real 27/54/player slots, visible render acknowledgement, resize cancellation, compact layout, GL restoration, bounded resources, no input or inventory actions");
        } finally {
            RenderDiagnostics.enabled = false; session.clear(); mc.currentScreen = oldScreen; mc.thePlayer = oldPlayer;
            mc.theWorld = oldWorld; mc.playerController = oldController; UiEffects.invalidate();
            Files.write(new File(out, "inventory-assist-report.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        }
    }
}
