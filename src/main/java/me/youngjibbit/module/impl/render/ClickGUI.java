package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.clickgui.NeutronTwoGui;
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
    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Panel", "Dropdown"}, 0);

    // A separate 2.0 screen; never instantiate it before the module manager is ready.
    private NeutronTwoGui modern;

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
        if (mc.currentScreen == Pulsar.INSTANCE.getPanel() || mc.currentScreen == Pulsar.INSTANCE.getDropdown()
                || mc.currentScreen == modern) {
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
        if (ThemeMode.APPROACH_TWO.equals(ThemeMode.selected())) {
            if (modern == null) modern = new NeutronTwoGui();
            return modern;
        }
        // A historical iOS configuration resolves to Panel, not a hidden GUI.
        return "Dropdown".equals(modeValue)
                ? Pulsar.INSTANCE.getDropdown() : Pulsar.INSTANCE.getPanel();
    }
}
