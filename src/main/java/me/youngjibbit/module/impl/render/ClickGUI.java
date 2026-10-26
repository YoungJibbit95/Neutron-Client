package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.CheckBoxValue;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

@ModuleInfo(
        name = "ClickGui",
        description = "simple clickgui",
        category = Category.Client
)
public class ClickGUI  extends Module
{
    private final CheckBoxValue headingGlow=new CheckBoxValue("Heading Glow",this,false);
    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Panel", "iOS"}, 0);

    public ClickGUI()
    {
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnable()
    {
        openSelectedGui();
        super.onEnable();
    }

    @Override
    public void onDisable()
    {
        if (mc.currentScreen == Pulsar.INSTANCE.getPanel() || mc.currentScreen == Pulsar.INSTANCE.getIOS()) {
            mc.displayGuiScreen(null);
        }
        super.onDisable();
    }

    public void setup()
    {

    }

    private void openSelectedGui() {
        GuiScreen target = resolveScreen(mode.getValue());
        if (target != null) {
            mc.displayGuiScreen(target);
        }
    }

    private GuiScreen resolveScreen(String modeValue) {
        switch (modeValue) {
            case "Panel":
                return Pulsar.INSTANCE.getPanel();
            case "iOS":
                return Pulsar.INSTANCE.getIOS();
            default:
                return Pulsar.INSTANCE.getPanel();
        }
    }
}
