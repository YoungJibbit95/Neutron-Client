package me.youngjibbit.module.impl.render;

import me.youngjibbit.utils.render.HudDrag;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** Dedicated non-pausing editor. Widgets still render through their normal HUD/bloom owner. */
public final class DiagnosticsEditor extends GuiScreen {
    static final int CONTROL_STRIP=50;
    private final Diagnostics diagnostics;
    private GuiButton modeButton;
    public DiagnosticsEditor(Diagnostics diagnostics){this.diagnostics=diagnostics;}
    @Override public void initGui(){
        int buttonWidth = Math.min(100, (width - 16) / 3);
        int first = (width - 3 * buttonWidth - 8) / 2;
        buttonList.clear();
        modeButton = new GuiButton(2, first, height-25, buttonWidth, 20, modeLabel());
        buttonList.add(modeButton);
        buttonList.add(new GuiButton(0, first+buttonWidth+4, height-25, buttonWidth, 20, "Reset positions"));
        buttonList.add(new GuiButton(1, first+2*(buttonWidth+4), height-25, buttonWidth, 20, "Done"));
        HudDrag.context(false,Mouse.isCreated()&&Mouse.isButtonDown(0));
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        modeButton.displayString = modeLabel();
        String hint = "Simple".equals(diagnostics.displayMode.getValue()) ? "Drag widgets to reposition" : "Diagnostics layout - drag widgets; scroll details";
        fontRendererObj.drawStringWithShadow(hint,8,height-39,0xfff0f3fa);
        super.drawScreen(mouseX,mouseY,partialTicks);
    }
    @Override protected void actionPerformed(GuiButton button)throws IOException {
        if (button.id == 2) {
            diagnostics.displayMode.setSelectedIndex(1 - diagnostics.displayMode.getSelectedIndex());
            button.displayString = modeLabel();
        } else if (button.id == 0) diagnostics.resetPositions();
        else if (button.id == 1) mc.displayGuiScreen(null);
    }
    private String modeLabel() { return "Simple".equals(diagnostics.displayMode.getValue()) ? "Mode: Simple" : "Mode: Text"; }
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();
        Diagnostics.scrollAt(Mouse.getEventX()*width/(float)mc.displayWidth,height-Mouse.getEventY()*height/(float)mc.displayHeight-1,Mouse.getEventDWheel());
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void onGuiClosed(){diagnostics.restoreConfiguredPositions();HudDrag.context(false,Mouse.isCreated()&&Mouse.isButtonDown(0));}
}
