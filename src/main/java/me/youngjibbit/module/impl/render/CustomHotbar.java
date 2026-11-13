package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.module.impl.client.HUD;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.ArrayListSurface;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.awt.Color;

/** Optional custom skin for the native hotbar, with render-time selection and item feedback. */
@ModuleInfo(id = "custom_hotbar", name = "Custom Hotbar",
        description = "Animated hotbar design; disable for the vanilla hotbar", category = Category.Render)
public final class CustomHotbar extends Module {
    private final CheckBoxValue smoothSelection = new CheckBoxValue("Smooth Selection", this, true);
    private final CheckBoxValue itemAnimation = new CheckBoxValue("Item Animation", this, true);
    private final CheckBoxValue background = new CheckBoxValue("Background", this, true);
    private final CheckBoxValue blur = new CheckBoxValue("Blur", this, true);
    private final CheckBoxValue glow = new CheckBoxValue("Glow", this, true);
    private final ArrayListSurface surface = new ArrayListSurface(this);
    private final HotbarMotion motion = new HotbarMotion();
    private long lastFrame;
    private Object world;
    private boolean captured;

    public static CustomHotbar current() {
        return Pulsar.INSTANCE.getMm() == null ? null
                : (CustomHotbar) Pulsar.INSTANCE.getMm().getModule(CustomHotbar.class);
    }

    @Subscribe private final Listener<WorldEvent> worldChange = new Listener<>(event -> reset());

    @Override public void onDisable() {
        reset();
        super.onDisable();
    }

    private void reset() {
        motion.reset();
        lastFrame = 0;
        world = null;
        captured = false;
        surface.close();
    }

    /** Returns whether the caller should skip its vanilla skin. No inventory state is changed. */
    public boolean render(ScaledResolution resolution, float partialTicks, EntityPlayer player) {
        if (!isToggled() || !HUD.visible() || player == null) {
            reset();
            return false;
        }
        if (world != mc.theWorld) {
            reset();
            world = mc.theWorld;
        }
        long now = System.nanoTime();
        float seconds = lastFrame == 0 || now <= lastFrame ? 0
                : Math.min(.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        ClientSettings settings = ClientSettings.current();
        boolean animations = settings == null || settings.animations.getValue();
        boolean reduced = settings != null && settings.reducedMotion.getValue();
        String intensity = settings == null ? "Balanced" : settings.motionIntensity.getValue();
        int selected = me.youngjibbit.module.impl.world.NScaffold.displayedSlot(player);
        motion.step(selected, seconds, animations && smoothSelection.getValue(),
                animations && itemAnimation.getValue(), reduced, intensity);
        Layout layout = layout(resolution.getScaledWidth(), resolution.getScaledHeight());
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            surface.panel(layout.x, layout.y, layout.width, layout.height, 5 * layout.scale,
                    theme, background.getValue(), blur.getValue(), glow.getValue(), 8,
                    90 / 255f, .65f, () -> {
                        float selectedX = layout.x + (2 + motion.position() * 20) * layout.scale;
                        float selectedY = layout.y + 2 * layout.scale;
                        if (glow.getValue()) {
                            int color = UiEffects.gradientColor(theme, UiEffects.Channel.GLOW, selectedX, selectedY);
                            GuiShaderBloom.renderRect(selectedX, selectedY, 20 * layout.scale,
                                    20 * layout.scale, Math.round(4 * layout.scale), color, 1, 1);
                        }
                        UIUtils.drawDecorationRound(selectedX, selectedY, 20 * layout.scale,
                                20 * layout.scale, 4 * layout.scale, new Color(0, 0, 0, 122));
                        drawItems(player, partialTicks, layout, seconds,
                                animations && itemAnimation.getValue(), reduced, intensity);
                    });
        }
        captured = true;
        return true;
    }

    /** The Event2D owner can reuse the clean world capture made before the hotbar was painted. */
    public boolean withCapturedScene(Runnable overlays) {
        boolean available = captured;
        captured = false;
        return available && isToggled() && surface.withCapturedScene(overlays);
    }

    private void drawItems(EntityPlayer player, float partialTicks, Layout layout, float seconds,
                           boolean animations, boolean reduced, String intensity) {
        RenderItem renderer = mc.getRenderItem();
        float previousZ = renderer.zLevel;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(layout.x, layout.y, 0);
            GlStateManager.scale(layout.scale, layout.scale, 1);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderHelper.enableGUIStandardItemLighting();
            renderer.zLevel = 100;
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = player.inventory.mainInventory[slot];
                long observation = stack == null ? 0
                        : ((long) Item.getIdFromItem(stack.getItem()) << 32) ^ stack.getMetadata();
                float pulse = motion.itemPulse(slot, observation, seconds, animations, reduced, intensity);
                if (stack == null) continue;
                int x = 4 + slot * 20, y = 4;
                float weight = animations && !reduced ? motion.weight(slot) : 0;
                float scale = 1 + weight * .035f - pulse * .08f;
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(x + 8, y + 8 - weight * .65f, 0);
                    GlStateManager.scale(scale, scale, 1);
                    GlStateManager.translate(-8, -8, 0);
                    float pickup = animations && !reduced ? Math.max(0, stack.animationsToGo - partialTicks) : 0;
                    if (pickup > 0) {
                        float stretch = 1 + pickup / 5;
                        GlStateManager.translate(8, 12, 0);
                        GlStateManager.scale(1 / stretch, (stretch + 1) / 2, 1);
                        GlStateManager.translate(-8, -12, 0);
                    }
                    renderer.renderItemAndEffectIntoGUI(stack, 0, 0);
                } finally {
                    GlStateManager.popMatrix();
                }
                // Counts and durability stay fixed and readable while the icon moves.
                renderer.renderItemOverlays(mc.fontRendererObj, stack, x, y);
            }
        } finally {
            renderer.zLevel = previousZ;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    static Layout layout(int width, int height) {
        float scale = Math.min(1, Math.min(Math.max(1, width - 8) / 184f,
                Math.max(1, height - 6) / 24f));
        return new Layout((width - 184 * scale) / 2,
                Math.max(0, height - 24 * scale - 3), scale);
    }

    static final class Layout {
        final float x, y, scale, width, height;
        Layout(float x, float y, float scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.width = 184 * scale;
            this.height = 24 * scale;
        }
    }
}
