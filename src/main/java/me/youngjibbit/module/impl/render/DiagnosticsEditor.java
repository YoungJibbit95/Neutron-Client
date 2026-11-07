package me.youngjibbit.module.impl.render;

import me.youngjibbit.utils.render.HudDrag;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Mouse;
import java.io.IOException;

/** Dedicated non-pausing editor. Widgets still render through their normal HUD/bloom owner. */
public final class DiagnosticsEditor extends GuiScreen {
    static final int CONTROL_STRIP=50;
    private final Diagnostics diagnostics;
    public DiagnosticsEditor(Diagnostics diagnostics){this.diagnostics=diagnostics;}
    @Override public void initGui(){
        buttonList.clear();buttonList.add(new GuiButton(0,width/2-104,height-25,100,20,"Reset positions"));
        buttonList.add(new GuiButton(1,width/2+4,height-25,100,20,"Done"));
        HudDrag.context(false,Mouse.isCreated()&&Mouse.isButtonDown(0));
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        fontRendererObj.drawStringWithShadow("Diagnostics layout - drag widgets; scroll details",8,height-39,0xfff0f3fa);
        super.drawScreen(mouseX,mouseY,partialTicks);
    }
    @Override protected void actionPerformed(GuiButton button)throws IOException {
        if(button.id==0)diagnostics.resetPositions();else mc.displayGuiScreen(null);
    }
    @Override public void handleMouseInput()throws IOException {
        super.handleMouseInput();
        Diagnostics.scrollAt(Mouse.getEventX()*width/(float)mc.displayWidth,height-Mouse.getEventY()*height/(float)mc.displayHeight-1,Mouse.getEventDWheel());
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void onGuiClosed(){diagnostics.restoreConfiguredPositions();HudDrag.context(false,Mouse.isCreated()&&Mouse.isButtonDown(0));}
}
