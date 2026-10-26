package me.youngjibbit.module.impl.ghost;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.Wrapper;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "HitDelayFix", category = Category.Ghost)
public class HitDelayFix extends Module
{
        @Subscribe
        private final Listener<EventUpdate> onUpdate = new Listener<>(event -> {
            if (!event.isPre() || Wrapper.mc.thePlayer == null) {
                return;
            }

            if (Wrapper.mc.objectMouseOver == null || !(Wrapper.mc.objectMouseOver.entityHit instanceof EntityLivingBase)) {
                return;
            }

            if (!Wrapper.mc.gameSettings.keyBindAttack.isKeyDown()) {
                return;
            }

            // Use the same input/controller path instead of emitting a second raw attack.
            Wrapper.mc.clickMouse();
        });

        public HitDelayFix() {
            super.setKey(Keyboard.KEY_0); // Set a default key to enable the module
        }

        @Override
        public void onEnable() {
            super.onEnable();
        }

        @Override
        public void onDisable() {
            super.onDisable();
        }
}
